package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class MessagesTest {

	private static ByteBuffer roundTrip(Messages.Message message) {
		return ByteBuffer.wrap(Messages.encode(message));
	}

	@Test
	void playerStateRoundTrip() {
		Messages.PlayerState m = new Messages.PlayerState();
		m.sequence = 123456;
		m.teleportId = 7;
		m.clientTimeMs = 9_876_543_210L;
		m.position.set(1.5f, -2.25f, 300f);
		m.yaw = -170.5f;
		m.pitch = 45f;
		m.slot = 1;
		Messages.PlayerState r = Messages.PlayerState.read(roundTrip(m));
		assertEquals(m.sequence, r.sequence);
		assertEquals(m.teleportId, r.teleportId);
		assertEquals(m.clientTimeMs, r.clientTimeMs);
		assertEquals(m.position, r.position);
		assertEquals(m.yaw, r.yaw);
		assertEquals(m.pitch, r.pitch);
		assertEquals(1, r.slot);
	}

	@Test
	void snapshotRoundTrip() {
		Messages.Snapshot m = new Messages.Snapshot();
		m.tick = 42;
		for (int id = 1; id <= Protocol.MAX_PLAYERS; id++) {
			Messages.Snapshot.Entry e = new Messages.Snapshot.Entry();
			e.playerId = id;
			e.alive = id % 2 == 0;
			e.health = 100 - id;
			e.kills = 1000 + id;
			e.deaths = id;
			e.slot = id % 2;
			e.position.set(id, id * 2, id * 3);
			e.yaw = id * 10;
			e.pitch = -id;
			m.players.add(e);
		}
		Messages.Snapshot r = Messages.Snapshot.read(roundTrip(m));
		assertEquals(42, r.tick);
		assertEquals(Protocol.MAX_PLAYERS, r.players.size);
		for (int i = 0; i < r.players.size; i++) {
			Messages.Snapshot.Entry a = m.players.get(i), b = r.players.get(i);
			assertEquals(a.playerId, b.playerId);
			assertEquals(a.alive, b.alive);
			assertEquals(a.health, b.health);
			assertEquals(a.kills, b.kills);
			assertEquals(a.deaths, b.deaths);
			assertEquals(a.slot, b.slot);
			assertEquals(a.position, b.position);
			assertEquals(a.yaw, b.yaw);
			assertEquals(a.pitch, b.pitch);
		}
	}

	@Test
	void namesAreSanitized() {
		assertEquals("Jugador", Messages.sanitizeName("   "));
		assertEquals("Jugador", Messages.sanitizeName(null));
		assertEquals("abc", Messages.sanitizeName(" a\u0000b\nc "));
		assertTrue(Messages.sanitizeName("x".repeat(100)).length() <= Protocol.MAX_NAME_LENGTH);
		Messages.Hello hello = new Messages.Hello();
		hello.name = "Ñandú ⚡";
		assertEquals("Ñandú ⚡", Messages.Hello.read(roundTrip(hello)).name);
	}

	@Test
	void killAndTeleportRoundTrip() {
		Messages.Kill k = new Messages.Kill();
		k.killerId = 3;
		k.victimId = 200;
		k.headshot = true;
		k.gun = "M4A1";
		Messages.Kill rk = Messages.Kill.read(roundTrip(k));
		assertEquals(3, rk.killerId);
		assertEquals(200, rk.victimId);
		assertTrue(rk.headshot);
		assertEquals("M4A1", rk.gun);

		Messages.Teleport t = new Messages.Teleport(Protocol.RESPAWN);
		t.playerId = 2;
		t.teleportId = 9;
		t.position.set(-78, 4, 68);
		Messages.Teleport rt = Messages.Teleport.read(Protocol.RESPAWN, roundTrip(t));
		assertEquals(2, rt.playerId);
		assertEquals(9, rt.teleportId);
		assertEquals(t.position, rt.position);
	}
}
