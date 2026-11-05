package com.juego.net;

import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

/**
 * One end of a conversation with a peer over UDP, with two channels:
 * <ul>
 * <li><b>unreliable</b>: sent once; may be lost, duplicated or reordered.
 * Used for data that is replaced constantly (player state, snapshots).</li>
 * <li><b>reliable, ordered</b>: every message gets an increasing id and is
 * re-sent every {@link Protocol#RESEND_MS} until the peer acknowledges it.
 * The receiver buffers messages that arrive early and delivers them strictly
 * in id order, exactly once. Acknowledgements are cumulative ("I have
 * everything up to N") and piggyback on every datagram.</li>
 * </ul>
 * This class does no I/O: it turns datagrams into messages ({@link #receive})
 * and pending messages into datagrams ({@link #flush}), so it can be tested
 * over a simulated lossy link.
 */
public class Connection {
	private static final int HEADER_SIZE = 2 + 4 + 1;
	private static final int MESSAGE_OVERHEAD = 1 + 4 + 1 + 2;
	/** A peer that leaves this many reliable messages unacknowledged is considered broken. */
	public static final int MAX_PENDING = 512;
	private static final int MAX_OUT_OF_ORDER = 512;

	/** A message delivered to the application. */
	public static class Message {
		public final byte type;
		public final ByteBuffer payload;

		Message(byte type, byte[] payload) {
			this.type = type;
			this.payload = ByteBuffer.wrap(payload);
		}
	}

	private static class Pending {
		final int id;
		final byte type;
		final byte[] payload;
		long lastSentMs = -1;

		Pending(int id, byte type, byte[] payload) {
			this.id = id;
			this.type = type;
			this.payload = payload;
		}
	}

	public final InetSocketAddress address;
	private final LongSupplier clock;
	private final ArrayDeque<Pending> pending = new ArrayDeque<>();
	private final Array<Pending> unreliable = new Array<>();
	private final IntMap<Message> outOfOrder = new IntMap<>();
	private int nextOutgoingId = 1;
	private int nextExpectedId = 1;
	private boolean ackDirty;
	private long lastReceiveMs;
	private boolean overflowed;

	public Connection(InetSocketAddress address, LongSupplier clock) {
		this.address = address;
		this.clock = clock;
		this.lastReceiveMs = clock.getAsLong();
	}

	public void sendReliable(Messages.Message message) {
		if (pending.size() >= MAX_PENDING) {
			overflowed = true;
			return;
		}
		pending.add(new Pending(nextOutgoingId++, message.type(), Messages.encode(message)));
	}

	public void sendUnreliable(Messages.Message message) {
		unreliable.add(new Pending(0, message.type(), Messages.encode(message)));
	}

	/**
	 * Parses one datagram from the peer and appends the messages that are now
	 * deliverable to {@code out}. Malformed datagrams are ignored.
	 *
	 * @return false if the datagram was not a valid No Deal packet
	 */
	public boolean receive(ByteBuffer datagram, Array<Message> out) {
		try {
			if (datagram.getShort() != Protocol.MAGIC)
				return false;
			int ack = datagram.getInt();
			int count = datagram.get() & 0xFF;
			Array<Message> parsedUnreliable = new Array<>();
			// Validate the whole datagram before acting on any of it.
			int[] ids = new int[count];
			Message[] messages = new Message[count];
			for (int i = 0; i < count; i++) {
				boolean reliable = (datagram.get() & 1) != 0;
				ids[i] = reliable ? datagram.getInt() : 0;
				byte type = datagram.get();
				int length = datagram.getShort() & 0xFFFF;
				byte[] payload = new byte[length];
				datagram.get(payload);
				messages[i] = new Message(type, payload);
				if (reliable && ids[i] <= 0)
					return false;
			}
			lastReceiveMs = clock.getAsLong();
			acknowledge(ack);
			for (int i = 0; i < count; i++) {
				if (ids[i] == 0) {
					parsedUnreliable.add(messages[i]);
				} else {
					acceptReliable(ids[i], messages[i], out);
				}
			}
			out.addAll(parsedUnreliable);
			return true;
		} catch (BufferUnderflowException | IllegalArgumentException e) {
			return false;
		}
	}

	private void acknowledge(int ack) {
		while (!pending.isEmpty() && pending.peek().id <= ack)
			pending.poll();
	}

	private void acceptReliable(int id, Message message, Array<Message> out) {
		ackDirty = true; // even duplicates must be acked again: our previous ack may have been lost
		if (id < nextExpectedId)
			return; // duplicate
		if (id > nextExpectedId) {
			if (id - nextExpectedId < MAX_OUT_OF_ORDER)
				outOfOrder.put(id, message);
			return;
		}
		out.add(message);
		nextExpectedId++;
		Message next;
		while ((next = outOfOrder.remove(nextExpectedId)) != null) {
			out.add(next);
			nextExpectedId++;
		}
	}

	/** Builds the datagrams to send now: new and timed-out reliable messages, unreliable ones and acks. */
	public Array<byte[]> flush() {
		long now = clock.getAsLong();
		Array<Pending> toSend = new Array<>();
		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (p.lastSentMs < 0 || now - p.lastSentMs >= Protocol.RESEND_MS) {
				p.lastSentMs = now;
				toSend.add(p);
			}
		}
		toSend.addAll(unreliable);
		unreliable.clear();

		Array<byte[]> datagrams = new Array<>();
		if (toSend.size == 0) {
			if (ackDirty)
				datagrams.add(build(new Array<Pending>()));
			ackDirty = false;
			return datagrams;
		}
		Array<Pending> batch = new Array<>();
		int size = HEADER_SIZE;
		for (Pending p : toSend) {
			int messageSize = MESSAGE_OVERHEAD + p.payload.length;
			if (batch.size > 0 && (size + messageSize > Protocol.MAX_DATAGRAM || batch.size == 255)) {
				datagrams.add(build(batch));
				batch.clear();
				size = HEADER_SIZE;
			}
			batch.add(p);
			size += messageSize;
		}
		datagrams.add(build(batch));
		ackDirty = false;
		return datagrams;
	}

	private byte[] build(Array<Pending> messages) {
		int size = HEADER_SIZE;
		for (Pending p : messages)
			size += MESSAGE_OVERHEAD + p.payload.length;
		ByteBuffer out = ByteBuffer.allocate(size);
		out.putShort(Protocol.MAGIC).putInt(nextExpectedId - 1).put((byte) messages.size);
		for (Pending p : messages) {
			boolean reliable = p.id > 0;
			out.put((byte) (reliable ? 1 : 0));
			if (reliable)
				out.putInt(p.id);
			out.put(p.type).putShort((short) p.payload.length).put(p.payload);
		}
		return out.array();
	}

	public boolean isTimedOut(long timeoutMs) {
		return overflowed || clock.getAsLong() - lastReceiveMs > timeoutMs;
	}

	/** Reliable messages sent but not acknowledged yet. */
	public int getPendingCount() {
		return pending.size();
	}
}
