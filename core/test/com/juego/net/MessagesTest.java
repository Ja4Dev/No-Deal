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
	void movementRoundTrip() {
		Messages.Movement m = new Messages.Movement();
		m.settings.gravity = 400;
		m.settings.airAccelerate = 100;
		m.settings.autoBunnyhop = true;
		assertEquals(m.settings, Messages.Movement.read(roundTrip(m)).settings);
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
		m.magazine = 5;
		m.reserve = -1;
		m.crouched = true;
		Messages.PlayerState r = Messages.PlayerState.read(roundTrip(m));
		assertTrue(r.crouched);
		assertEquals(5, r.magazine);
		assertEquals(-1, r.reserve, "infinite reserve survives the trip");
		assertEquals(m.sequence, r.sequence);
		assertEquals(m.teleportId, r.teleportId);
		assertEquals(m.clientTimeMs, r.clientTimeMs);
		assertEquals(m.position, r.position);
		assertEquals(m.yaw, r.yaw);
		assertEquals(m.pitch, r.pitch);
		assertEquals(1, r.slot);
	}

	@Test
	void weaponMessagesRoundTrip() {
		Messages.WeaponSpawn spawn = new Messages.WeaponSpawn();
		spawn.weaponId = 70_000;
		spawn.gun = "ak47";
		spawn.from.set(1, 2, 3);
		spawn.position.set(4, 0.5f, -6);
		spawn.yaw = 123.5f;
		spawn.magazine = 17;
		spawn.reserve = 40;
		spawn.droppedBy = 3;
		Messages.WeaponSpawn s = Messages.WeaponSpawn.read(roundTrip(spawn));
		assertEquals(70_000, s.weaponId);
		assertEquals("ak47", s.gun);
		assertEquals(spawn.from, s.from);
		assertEquals(spawn.position, s.position);
		assertEquals(123.5f, s.yaw);
		assertEquals(17, s.magazine);
		assertEquals(40, s.reserve);
		assertEquals(3, s.droppedBy);

		Messages.WeaponRemove remove = new Messages.WeaponRemove();
		remove.weaponId = 70_000;
		remove.pickerId = 4;
		Messages.WeaponRemove r = Messages.WeaponRemove.read(roundTrip(remove));
		assertEquals(70_000, r.weaponId);
		assertEquals(4, r.pickerId);

		Messages.DropWeapon drop = new Messages.DropWeapon();
		drop.slot = 1;
		drop.magazine = 3;
		drop.reserve = -1;
		Messages.DropWeapon d = Messages.DropWeapon.read(roundTrip(drop));
		assertEquals(1, d.slot);
		assertEquals(3, d.magazine);
		assertEquals(-1, d.reserve);

		Messages.PickupWeapon pickup = new Messages.PickupWeapon();
		pickup.weaponId = 9;
		pickup.magazine = 30;
		pickup.reserve = 70;
		Messages.PickupWeapon p = Messages.PickupWeapon.read(roundTrip(pickup));
		assertEquals(9, p.weaponId);
		assertEquals(30, p.magazine);
		assertEquals(70, p.reserve);
	}

	@Test
	void shotAndDamageRoundTrip() {
		Messages.Shot shot = new Messages.Shot();
		shot.shooterId = 2;
		shot.gun = "magnum";
		shot.origin.set(1, 4, 2);
		shot.end.set(-20, 3, 40);
		Messages.Shot s = Messages.Shot.read(roundTrip(shot));
		assertEquals(2, s.shooterId);
		assertEquals("magnum", s.gun);
		assertEquals(shot.origin, s.origin);
		assertEquals(shot.end, s.end);

		Messages.Damage damage = new Messages.Damage();
		damage.amount = 120; // a headshot can do more than 100
		damage.source.set(7, 8, 9);
		Messages.Damage d = Messages.Damage.read(roundTrip(damage));
		assertEquals(120, d.amount);
		assertEquals(damage.source, d.source);
	}

	@Test
	void snapshotRoundTrip() {
		Messages.Snapshot m = new Messages.Snapshot();
		m.tick = 42;
		m.phase = 1;
		m.remainingMs = 599_999;
		for (int id = 1; id <= Protocol.MAX_PLAYERS; id++) {
			Messages.Snapshot.Entry e = new Messages.Snapshot.Entry();
			e.playerId = id;
			e.alive = id % 2 == 0;
			e.crouched = id % 3 == 0; // every combination of alive and crouched
			e.health = 100 - id;
			e.kills = 1000 + id;
			e.deaths = id;
			e.assists = 2 * id;
			e.slot = id % 2;
			e.position.set(id, id * 2, id * 3);
			e.yaw = id * 10;
			e.pitch = -id;
			m.players.add(e);
		}
		Messages.Snapshot r = Messages.Snapshot.read(roundTrip(m));
		assertEquals(42, r.tick);
		assertEquals(1, r.phase);
		assertEquals(599_999, r.remainingMs);
		assertEquals(Protocol.MAX_PLAYERS, r.players.size);
		for (int i = 0; i < r.players.size; i++) {
			Messages.Snapshot.Entry a = m.players.get(i), b = r.players.get(i);
			assertEquals(a.playerId, b.playerId);
			assertEquals(a.alive, b.alive);
			assertEquals(a.crouched, b.crouched);
			assertEquals(a.health, b.health);
			assertEquals(a.kills, b.kills);
			assertEquals(a.deaths, b.deaths);
			assertEquals(a.assists, b.assists);
			assertEquals(a.slot, b.slot);
			assertEquals(a.position, b.position);
			assertEquals(a.yaw, b.yaw);
			assertEquals(a.pitch, b.pitch);
		}
	}

	@Test
	void namesAreSanitized() {
		com.juego.utils.Text.setLanguage(com.juego.utils.Text.Language.ES);
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

	@Test
	void hitRoundTrip() {
		Messages.Hit hit = new Messages.Hit();
		hit.victimId = 200;
		hit.headshot = true;
		hit.position.set(1, 2, 3);
		hit.direction.set(0, 0.6f, 0.8f);
		Messages.Hit r = Messages.Hit.read(roundTrip(hit));
		assertEquals(200, r.victimId);
		assertEquals(true, r.headshot);
		assertEquals(hit.position, r.position);
		assertEquals(hit.direction, r.direction);
	}

	@Test
	void aFullServerWithBotsFitsInOneSnapshot() {
		Messages.Snapshot m = new Messages.Snapshot();
		for (int id = 1; id <= Protocol.MAX_MATCH_PLAYERS; id++) {
			Messages.Snapshot.Entry e = new Messages.Snapshot.Entry();
			e.playerId = id;
			m.players.add(e);
		}
		assertTrue(roundTrip(m).limit() < Protocol.MAX_DATAGRAM - 20,
				"the biggest match must fit a datagram");
	}
}
