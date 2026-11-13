package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fall damage goes by the speed of the landing, so it follows sv_gravity. */
class FallDamageTest {
	private static float gravity(int svGravity) {
		MovementSettings m = new MovementSettings();
		m.gravity = svGravity;
		return m.gravityAcceleration();
	}

	@Test
	void shortDropsAreSafeLongOnesKill() {
		float g = PlayerMovement.GRAVITY;
		assertEquals(0, FallDamage.damage(FallDamage.speedAfter(FallDamage.SAFE_HEIGHT * 0.95f, g)));
		int middle = FallDamage.damage(FallDamage.speedAfter((FallDamage.SAFE_HEIGHT + FallDamage.FATAL_HEIGHT) / 2, g));
		assertTrue(middle > 20 && middle < 80, "halfway hurts, without killing: " + middle);
		assertTrue(FallDamage.damage(FallDamage.speedAfter(FallDamage.FATAL_HEIGHT, g)) >= 100);
	}

	@Test
	void aJumpNeverHurtsAtAnyGravity() {
		// On flat ground a jump lands as fast as it took off, whatever the gravity.
		assertEquals(0, FallDamage.damage(PlayerMovement.JUMP_SPEED));
		assertEquals(0, FallDamage.damage(FallDamage.fastestLanding(0, gravity(MovementSettings.MAX_GRAVITY))));
	}

	@Test
	void theSameDropHurtsLessWithLessGravity() {
		float drop = 35;
		int normal = FallDamage.damage(FallDamage.speedAfter(drop, gravity(800)));
		int low = FallDamage.damage(FallDamage.speedAfter(drop, gravity(400)));
		int high = FallDamage.damage(FallDamage.speedAfter(drop, gravity(1600)));
		assertTrue(normal > 0, "35 units hurt at normal gravity");
		assertEquals(0, low, "at half gravity it doesn't");
		assertTrue(high > normal, "at double gravity it hurts more: " + high + " vs " + normal);
	}

	@Test
	void aClientCantClaimAFallFromHigherThanItWas() {
		float g = PlayerMovement.GRAVITY;
		float fromTheFloor = FallDamage.fastestLanding(0, g);
		assertEquals(PlayerMovement.JUMP_SPEED, fromTheFloor, 0.01f, "only a jump");
		assertEquals(0, FallDamage.damage(fromTheFloor));
		assertTrue(FallDamage.fastestLanding(30, g) > FallDamage.speedAfter(30, g), "a jump off the ledge counts too");
		assertEquals(FallDamage.MAX_FALL_SPEED, FallDamage.fastestLanding(10_000, g), 0.01f, "never past the top speed");
	}
}
