package com.juego.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game packets over stream connections, such as Bluetooth RFCOMM sockets: a
 * host has one link per phone connected to it, a client one link to the host.
 * <p>
 * A stream has no packets, so each one goes as its length (2 bytes) followed
 * by its bytes. Every link has a thread that reads and one that writes, so
 * the game thread never waits for the radio: it polls received packets like
 * from a UDP socket, and sending only queues. When the radio can't keep up,
 * packets beyond a queue's capacity are dropped, as UDP would; the game's
 * reliable channel resends what matters.
 * <p>
 * Each link gets an unresolved address naming the device ("Bluetooth: Pixel
 * 6", plus a number to tell links apart), which the server uses like an IP.
 */
public class LinkTransport implements Transport {
	private static final int MAX_QUEUED = 4096;
	/** Packets waiting to be written to one link. */
	static final int SEND_QUEUE = 256;
	private static final byte[] CLOSE = new byte[0];

	private final ConcurrentLinkedQueue<NetSocket.Datagram> inbox = new ConcurrentLinkedQueue<>();
	private final AtomicInteger queued = new AtomicInteger();
	private final ConcurrentHashMap<InetSocketAddress, Link> links = new ConcurrentHashMap<>();
	private final AtomicInteger nextId = new AtomicInteger(1);
	private final String prefix;
	private volatile boolean closed;

	/** @param prefix shown before the device names in addresses, e.g. "Bluetooth" */
	public LinkTransport(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Starts exchanging packets over a new connection.
	 *
	 * @param name       the other device, for addresses and logs
	 * @param connection closed together with the link (the socket)
	 * @return the address the other side's packets come from
	 */
	public InetSocketAddress addLink(String name, InputStream in, OutputStream out, Closeable connection) {
		InetSocketAddress address = InetSocketAddress.createUnresolved(prefix + ": " + name, nextId.getAndIncrement());
		Link link = new Link(address, in, out, connection);
		if (closed) {
			link.close();
			return address;
		}
		links.put(address, link);
		link.start();
		return address;
	}

	/** Links still open. */
	public int linkCount() {
		return links.size();
	}

	@Override
	public NetSocket.Datagram poll() {
		NetSocket.Datagram d = inbox.poll();
		if (d != null)
			queued.decrementAndGet();
		return d;
	}

	@Override
	public void send(InetSocketAddress to, byte[] data) {
		Link link = links.get(to);
		if (link != null)
			link.send(data);
	}

	@Override
	public boolean reaches(InetSocketAddress address) {
		return links.containsKey(address);
	}

	@Override
	public void close() {
		closed = true;
		for (Link link : links.values())
			link.close();
		links.clear();
	}

	private final class Link {
		final InetSocketAddress address;
		final DataInputStream in;
		final OutputStream out;
		final Closeable connection;
		final LinkedBlockingQueue<byte[]> outbox = new LinkedBlockingQueue<>(SEND_QUEUE);
		volatile boolean open = true;

		Link(InetSocketAddress address, InputStream in, OutputStream out, Closeable connection) {
			this.address = address;
			this.in = new DataInputStream(in);
			this.out = out;
			this.connection = connection;
		}

		void start() {
			Thread reader = new Thread(this::readLoop, "Link-in " + address.getHostString());
			Thread writer = new Thread(this::writeLoop, "Link-out " + address.getHostString());
			reader.setDaemon(true);
			writer.setDaemon(true);
			reader.start();
			writer.start();
		}

		void send(byte[] data) {
			if (open && data.length > 0 && data.length <= 0xFFFF)
				outbox.offer(data); // full: dropped, like a UDP packet
		}

		private void readLoop() {
			try {
				while (open) {
					int length = in.readUnsignedShort();
					if (length == 0 || length > Protocol.MAX_DATAGRAM * 2)
						throw new IOException("bad packet length " + length);
					byte[] data = new byte[length];
					in.readFully(data);
					if (queued.get() >= MAX_QUEUED)
						continue; // the game thread is not keeping up
					inbox.add(new NetSocket.Datagram(address, data));
					queued.incrementAndGet();
				}
			} catch (IOException e) {
				if (open)
					NetLog.info("Net", address.getHostString() + " desconectado (" + e.getMessage() + ")");
			}
			close();
		}

		private void writeLoop() {
			try {
				byte[] frame = new byte[2 + 0xFFFF];
				while (true) {
					byte[] data = outbox.take();
					if (data == CLOSE)
						break;
					// Length and bytes in one write: on Android every write() of a Bluetooth socket goes out
					// as its own RFCOMM frame, and frames in flight are limited.
					frame[0] = (byte) (data.length >>> 8);
					frame[1] = (byte) data.length;
					System.arraycopy(data, 0, frame, 2, data.length);
					out.write(frame, 0, 2 + data.length);
					out.flush();
				}
			} catch (IOException | InterruptedException e) {
				// the connection dropped: the reader notices too
			}
			close();
		}

		void close() {
			if (!open)
				return;
			open = false;
			links.remove(address);
			outbox.clear();
			outbox.offer(CLOSE);
			try {
				connection.close(); // unblocks the reader
			} catch (IOException e) {
				// already closed
			}
		}
	}
}
