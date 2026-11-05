package com.juego.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;

class GroundWeaponsTest {
	private static final float FLOOR = 0.5f;
	private static final float WALL_X = 10f;
	/** A player standing on the floor: capsule center 3 units above it. */
	private static final Vector3 STANDING = new Vector3(0, FLOOR + 3, 0);

	private final List<GroundWeapons.Weapon> spawned = new ArrayList<>();
	private final List<Integer> removedBy = new ArrayList<>();
	private GroundWeapons weapons;
	private GunDefinition rifle, pistol;

	/** A flat floor at y = FLOOR and a wall at x = WALL_X. */
	private static float cast(Vector3 from, Vector3 to, Vector3 out) {
		float best = Float.MAX_VALUE;
		if (from.y > FLOOR && to.y <= FLOOR) {
			float t = (from.y - FLOOR) / (from.y - to.y);
			best = t;
		}
		if (from.x < WALL_X && to.x >= WALL_X) {
			float t = (WALL_X - from.x) / (to.x - from.x);
			best = Math.min(best, t);
		}
		if (best == Float.MAX_VALUE)
			return -1;
		out.set(to).sub(from).scl(best).add(from);
		return best * from.dst(to);
	}

	@BeforeEach
	void setUp() {
		rifle = def("rifle", GunDefinition.Slot.PRIMARY);
		pistol = def("pistol", GunDefinition.Slot.SECONDARY);
		weapons = new GroundWeapons(GroundWeaponsTest::cast, new GroundWeapons.Listener() {
			@Override
			public void spawned(GroundWeapons.Weapon weapon) {
				spawned.add(weapon);
			}

			@Override
			public void removed(GroundWeapons.Weapon weapon, int pickerId) {
				removedBy.add(pickerId);
			}
		});
	}

	private static GunDefinition def(String id, GunDefinition.Slot slot) {
		GunDefinition def = new GunDefinition();
		def.id = id;
		def.slot = slot;
		def.magazineSize = 30;
		def.reserveAmmo = 70;
		return def;
	}

	@Test
	void thrownGunsLandOnTheFloorInFront() {
		GroundWeapons.Weapon w = weapons.toss(rifle, 12, 40, STANDING, 0, 1, 0); // yaw 0 = looking down +Z
		assertEquals(FLOOR, w.position.y, 0.001f, "on the floor");
		assertEquals(GroundWeapons.THROW_DISTANCE, w.position.z, 0.001f, "in front of the player");
		assertTrue(w.from.y > w.position.y, "thrown from the hands");
		assertEquals(12, w.magazine);
		assertEquals(40, w.reserve);
		assertSame(w, spawned.get(0));
	}

	@Test
	void thrownGunsStopBeforeWalls() {
		Vector3 nearWall = new Vector3(WALL_X - 2, FLOOR + 3, 0);
		GroundWeapons.Weapon w = weapons.toss(rifle, 30, 70, nearWall, 90, 1, 0); // yaw 90 = looking down +X
		assertTrue(w.position.x < WALL_X - 0.5f, "stays on this side of the wall, got x=" + w.position.x);
		assertEquals(FLOOR, w.position.y, 0.001f);
	}

	@Test
	void whoeverDroppedAGunCantPickItUpRightAway() {
		GroundWeapons.Weapon w = weapons.toss(rifle, 30, 70, STANDING, 0, 1, 1000);
		boolean[] primaryFree = { true, false };
		Vector3 standingOnIt = new Vector3(w.position).add(0, 3, 0);
		assertNull(weapons.findAutoPickup(standingOnIt, primaryFree, 1, 1000 + 100), "the dropper waits");
		assertSame(w, weapons.findAutoPickup(standingOnIt, primaryFree, 2, 1000 + 100), "anyone else can take it");
		assertSame(w, weapons.findAutoPickup(standingOnIt, primaryFree, 1, 1000 + GroundWeapons.DROPPER_BLOCK_MS));
	}

	@Test
	void walkingOverAGunOnlyTakesItIfThatSlotIsFree() {
		GroundWeapons.Weapon w = weapons.toss(rifle, 30, 70, STANDING, 0, 2, 0);
		Vector3 standingOnIt = new Vector3(w.position).add(0, 3, 0);
		assertNull(weapons.findAutoPickup(standingOnIt, new boolean[] { false, true }, 1, 0), "primary slot is full");
		assertSame(w, weapons.findAutoPickup(standingOnIt, new boolean[] { true, false }, 1, 0));
		Vector3 farAway = new Vector3(w.position).add(GroundWeapons.PICKUP_RADIUS + 1, 3, 0);
		assertNull(weapons.findAutoPickup(farAway, new boolean[] { true, true }, 1, 0), "out of reach");
	}

	@Test
	void aimingPicksTheGunInFront() {
		GroundWeapons.Weapon ahead = weapons.toss(rifle, 30, 70, STANDING, 0, 2, 0);
		weapons.toss(pistol, 6, -1, STANDING, 180, 2, 0); // behind the player
		Vector3 lookingDownAhead = new Vector3(0, -1, 1).nor();
		assertSame(ahead, weapons.findAimed(STANDING, lookingDownAhead), "even right after dropping it");
		assertNull(weapons.findAimed(STANDING, new Vector3(0, 1, 0)), "looking at the sky");
	}

	@Test
	void droppedGunsDespawnAndAreCapped() {
		weapons.toss(rifle, 30, 70, STANDING, 0, 1, 0);
		weapons.update(GroundWeapons.DESPAWN_MS - 1);
		assertEquals(1, weapons.size());
		weapons.update(GroundWeapons.DESPAWN_MS);
		assertEquals(0, weapons.size());
		assertEquals(GroundWeapons.NOBODY, (int) removedBy.get(0));

		for (int i = 0; i < GroundWeapons.MAX_DROPPED + 3; i++)
			weapons.toss(rifle, 30, 70, STANDING, 0, 1, 100_000);
		assertEquals(GroundWeapons.MAX_DROPPED, weapons.size(), "the oldest ones make room");
	}

	@Test
	void spawnersRefillAfterTheirGunIsTaken() {
		weapons.addSpawner(rifle, new Vector3(5, 4, 5));
		weapons.update(0);
		assertEquals(1, weapons.size());
		GroundWeapons.Weapon w = spawned.get(0);
		assertEquals(FLOOR, w.position.y, 0.001f, "placed on the floor below the given point");
		assertEquals(30, w.magazine, "full ammo");
		assertTrue(w.canBeTakenBy(GroundWeapons.NOBODY, 0) && w.canBeTakenBy(1, 0));

		weapons.update(GroundWeapons.DESPAWN_MS * 10);
		assertEquals(1, weapons.size(), "spawner guns don't despawn");

		long takenAt = 1_000_000;
		weapons.remove(w, 1, takenAt);
		assertEquals(0, weapons.size());
		weapons.update(takenAt + GroundWeapons.SPAWNER_RESPAWN_MS - 1);
		assertEquals(0, weapons.size());
		weapons.update(takenAt + GroundWeapons.SPAWNER_RESPAWN_MS);
		assertEquals(1, weapons.size(), "back after a while");
	}

	@Test
	void resetClearsDroppedGunsAndRefillsSpawners() {
		weapons.addSpawner(rifle, new Vector3(5, 4, 5));
		weapons.update(0);
		weapons.remove(spawned.get(0), 1, 0);
		weapons.toss(pistol, 6, -1, STANDING, 0, 1, 0);
		weapons.reset(10);
		assertEquals(1, weapons.size());
		assertEquals(rifle, weapons.all().first().def);
	}

	@Test
	void clientMirrorFollowsTheServer() {
		GroundWeapons mirror = new GroundWeapons(null, new GroundWeapons.Listener() {
			@Override
			public void spawned(GroundWeapons.Weapon weapon) {
			}

			@Override
			public void removed(GroundWeapons.Weapon weapon, int pickerId) {
				removedBy.add(pickerId);
			}
		});
		mirror.addRemote(42, rifle, 5, 6, new Vector3(0, 3, 0), new Vector3(0, FLOOR, 3), 10, 7, 0);
		assertNotNull(mirror.get(42));
		assertFalse(mirror.get(42).canBeTakenBy(7, 0), "the dropper waits here too");
		mirror.removeRemote(42, 3);
		assertNull(mirror.get(42));
		assertEquals(3, (int) removedBy.get(0));
		mirror.removeRemote(42, 3); // duplicate: ignored
		assertEquals(1, removedBy.size());
	}
}
