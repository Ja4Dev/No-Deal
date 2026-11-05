package com.juego.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecoilTest {
	private GunDefinition rifle;
	private Recoil recoil;

	@BeforeEach
	void setUp() {
		rifle = new GunDefinition();
		rifle.fireDelayMs = 100;
		rifle.spray = new float[][] { { 0, 0 }, { 1, 0 }, { 2, -0.5f }, { 3, 1 } };
		rifle.spreadBase = 0.1f;
		rifle.spreadPerShot = 0.2f;
		rifle.spreadMove = 3f;
		rifle.spreadAir = 6f;
		recoil = new Recoil();
	}

	/** Holds the trigger: one shot every fire delay. Returns the {up, right} of each bullet. */
	private float[][] spray(int bullets, long startMs) {
		float[][] offsets = new float[bullets][];
		long now = startMs;
		for (int i = 0; i < bullets; i++) {
			recoil.update(rifle, rifle.fireDelayMs / 1000f, now);
			recoil.shoot(rifle, now);
			offsets[i] = new float[] { recoil.up(), recoil.right() };
			now += rifle.fireDelayMs;
		}
		return offsets;
	}

	@Test
	void theFirstBulletGoesWhereYouAim() {
		float[][] bullets = spray(1, 0);
		assertEquals(0, bullets[0][0]);
		assertEquals(0, bullets[0][1]);
	}

	@Test
	void holdingTheTriggerFollowsThePatternEveryTime() {
		for (int attempt = 0; attempt < 2; attempt++) {
			recoil.reset();
			float[][] bullets = spray(4, attempt * 10_000L);
			for (int i = 0; i < 4; i++) {
				assertEquals(rifle.spray[i][0], bullets[i][0], 1e-5f, "bullet " + i + " up");
				assertEquals(rifle.spray[i][1], bullets[i][1], 1e-5f, "bullet " + i + " right");
			}
		}
	}

	@Test
	void aLongBurstStaysAtTheEndOfThePattern() {
		float[][] bullets = spray(10, 0);
		assertEquals(3, bullets[9][0], 1e-5f);
		assertEquals(1, bullets[9][1], 1e-5f);
	}

	@Test
	void releasingTheTriggerBringsTheViewBackDown() {
		spray(4, 0);
		long last = 3 * rifle.fireDelayMs;
		recoil.update(rifle, 0.016f, last + 50);
		assertTrue(recoil.up() > 2.5f, "doesn't recover between shots of a burst");
		float before = recoil.up();
		long now = last + rifle.fireDelayMs + 100;
		recoil.update(rifle, 0.05f, now);
		assertTrue(recoil.up() < before, "starts coming down after the fire delay");
		for (int i = 0; i < 60; i++)
			recoil.update(rifle, 0.016f, now += 16);
		assertEquals(0, recoil.shots(), 1e-5f, "back to a first shot within a second");
		assertEquals(0, recoil.up(), 1e-5f);
	}

	@Test
	void tappingIsAccurateButSprayingIsNot() {
		float firstShot = recoil.inaccuracy(rifle, 0, false);
		assertEquals(rifle.spreadBase, firstShot, 1e-5f);
		spray(5, 0);
		assertTrue(recoil.inaccuracy(rifle, 0, false) > firstShot + 0.5f, "a burst gets less accurate");
	}

	@Test
	void walkingIsAccurateRunningAndJumpingAreNot() {
		float still = recoil.inaccuracy(rifle, 0, false);
		assertEquals(still, recoil.inaccuracy(rifle, 0.5f, false), 1e-5f, "walking (half speed)");
		assertEquals(still + rifle.spreadMove, recoil.inaccuracy(rifle, 1f, false), 1e-5f, "running");
		assertEquals(still + rifle.spreadAir, recoil.inaccuracy(rifle, 0, true), 1e-5f, "in the air");
	}

	@Test
	void switchingGunsForgetsTheBurst() {
		spray(3, 0);
		recoil.reset();
		assertEquals(0, recoil.shots());
		assertEquals(0, recoil.up());
	}
}
