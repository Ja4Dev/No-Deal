package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Array;

class ConnectionTest {
	private long now = 0;
	private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 1);

	private static Messages.PlayerLeft message(int value) {
		Messages.PlayerLeft m = new Messages.PlayerLeft();
		m.playerId = value;
		return m;
	}

	/**
	 * Sends 500 reliable messages over a link that drops 30% of datagrams,
	 * duplicates 10% and delivers them in random order. Every message must
	 * arrive exactly once and in order.
	 */
	@Test
	void reliableChannelSurvivesLossDuplicationAndReordering() {
		Random random = new Random(42);
		Connection sender = new Connection(address, () -> now);
		Connection receiver = new Connection(address, () -> now);
		List<Integer> delivered = new ArrayList<>();
		int total = 500, queued = 0;

		for (int step = 0; step < 10_000 && delivered.size() < total; step++) {
			now += 20;
			for (int i = 0; i < 3 && queued < total; i++)
				sender.sendReliable(message(queued++ % 200));
			transfer(sender.flush(), receiver, delivered, random);
			transfer(receiver.flush(), sender, null, random); // acks back, same bad link
		}

		assertEquals(total, delivered.size());
		for (int i = 0; i < total; i++)
			assertEquals(i % 200, delivered.get(i), "message " + i + " out of order");
		// One more clean round trip lets the last acks through.
		now += Protocol.RESEND_MS;
		transfer(sender.flush(), receiver, delivered, null);
		transfer(receiver.flush(), sender, null, null);
		assertEquals(0, sender.getPendingCount(), "everything acknowledged");
	}

	private void transfer(Array<byte[]> datagrams, Connection to, List<Integer> delivered, Random random) {
		List<byte[]> link = new ArrayList<>();
		for (byte[] d : datagrams) {
			if (random != null && random.nextFloat() < 0.3f)
				continue; // lost
			link.add(d);
			if (random != null && random.nextFloat() < 0.1f)
				link.add(d); // duplicated
		}
		if (random != null)
			Collections.shuffle(link, random);
		for (byte[] d : link) {
			Array<Connection.Message> out = new Array<>();
			to.receive(ByteBuffer.wrap(d), out);
			if (delivered != null)
				for (Connection.Message m : out)
					delivered.add(Messages.PlayerLeft.read(m.payload).playerId);
		}
	}

	@Test
	void unreliableMessagesAreSentOnce() {
		Connection sender = new Connection(address, () -> now);
		sender.sendUnreliable(message(7));
		assertEquals(1, sender.flush().size);
		now += 1000;
		assertEquals(0, sender.flush().size, "not re-sent");
	}

	@Test
	void reliableMessagesAreResentUntilAcked() {
		Connection sender = new Connection(address, () -> now);
		Connection receiver = new Connection(address, () -> now);
		sender.sendReliable(message(1));
		assertEquals(1, sender.flush().size);
		assertEquals(0, sender.flush().size, "not before the resend interval");
		now += Protocol.RESEND_MS;
		Array<byte[]> resent = sender.flush();
		assertEquals(1, resent.size);
		receiver.receive(ByteBuffer.wrap(resent.first()), new Array<>());
		for (byte[] ack : receiver.flush())
			sender.receive(ByteBuffer.wrap(ack), new Array<>());
		assertEquals(0, sender.getPendingCount());
	}

	@Test
	void ignoresGarbage() {
		Connection c = new Connection(address, () -> now);
		Array<Connection.Message> out = new Array<>();
		assertFalse(c.receive(ByteBuffer.wrap(new byte[] { 1, 2, 3 }), out));
		assertFalse(c.receive(ByteBuffer.wrap(new byte[] { 0x4E, 0x44, 0, 0, 0, 0, 5, 1 }), out), "truncated");
		assertTrue(out.isEmpty());
	}

	@Test
	void timesOutWithoutTraffic() {
		Connection c = new Connection(address, () -> now);
		assertFalse(c.isTimedOut(Protocol.TIMEOUT_MS));
		now += Protocol.TIMEOUT_MS + 1;
		assertTrue(c.isTimedOut(Protocol.TIMEOUT_MS));
	}
}
