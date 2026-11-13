package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;

/**
 * Air strafing and bunnyhopping with PlayerMovement, and the server's movement
 * variables (sv_gravity, sv_airaccelerate, sv_autobunnyhopping).
 */
class BunnyhopTest {
	private static final float DT = 1f / 60f;
	/** Steps in the air per hop: a jump at normal gravity lasts about 0.8 s. */
	private static final int AIR_STEPS = 48;
	private final Text.Language language = Text.language();

	@AfterEach
	void restore() {
		Text.setLanguage(language);
	}

	/**
	 * The classic strafe jump: in the air, press sideways while turning the view
	 * with the movement, so the wish direction stays a little past 90 degrees
	 * from the velocity. Then land and jump again right away, for {@code hops}
	 * hops. Returns the speed at the end.
	 */
	private static float strafeJump(float airAccelerate, int hops, boolean jumpOnLanding) {
		Vector3 velocity = new Vector3(0, 0, PlayerMovement.RUN_SPEED);
		Vector3 wish = new Vector3();
		for (int hop = 0; hop < hops; hop++) {
			for (int i = 0; i < AIR_STEPS; i++) {
				// Perpendicular to where we are going, alternating sides each hop like a real strafe.
				wish.set(velocity.z, 0, -velocity.x).nor().scl(hop % 2 == 0 ? 1 : -1);
				PlayerMovement.step(velocity, wish, PlayerMovement.RUN_SPEED, false, DT, airAccelerate);
			}
			// The landing step: jumping right away skips its friction; waiting a step doesn't.
			PlayerMovement.step(velocity, new Vector3(), PlayerMovement.RUN_SPEED, !jumpOnLanding, DT, airAccelerate);
		}
		return velocity.len();
	}

	@Test
	void airStrafingGainsSpeedAndMoreWithHigherAirAccelerate() {
		float normal = strafeJump(MovementSettings.DEFAULT_AIR_ACCELERATE, 4, true);
		float bhopServer = strafeJump(100, 4, true);
		assertTrue(normal > PlayerMovement.RUN_SPEED * 1.2f, "even at 10, strafe jumps gain speed: " + normal);
		assertTrue(bhopServer > normal, "100 gains more: " + bhopServer + " vs " + normal);
	}

	@Test
	void jumpingTheMomentYouLandKeepsTheSpeed() {
		float onTime = strafeJump(100, 6, true);
		float late = strafeJump(100, 6, false);
		assertTrue(onTime > late, "a step of friction at every landing costs speed: " + onTime + " vs " + late);
	}

	@Test
	void nothingGoesPastTheTopSpeed() {
		float speed = strafeJump(MovementSettings.MAX_AIR_ACCELERATE, 40, true);
		assertTrue(speed <= PlayerMovement.MAX_SPEED + 1e-3f, "capped: " + speed);
		assertEquals(PlayerMovement.MAX_SPEED, speed, 0.5f, "and a long bunnyhop at the top air control gets there");
	}

	@Test
	void withoutAirControlTheSpeedStays() {
		assertEquals(PlayerMovement.RUN_SPEED, strafeJump(0, 3, true), 1e-3f);
	}

	@Test
	void gravityIsInCsUnits() {
		MovementSettings m = new MovementSettings();
		assertEquals(PlayerMovement.GRAVITY, m.gravityAcceleration(), 1e-4f, "800 is the normal gravity");
		assertNull(m.set(MovementSettings.GRAVITY, "400"));
		assertEquals(PlayerMovement.GRAVITY / 2, m.gravityAcceleration(), 1e-4f);
		// The jump is as strong: at half gravity it goes twice as high.
		float height = PlayerMovement.JUMP_SPEED * PlayerMovement.JUMP_SPEED / (2 * m.gravityAcceleration());
		assertEquals(2 * 0.62f * 6, height, 0.5f);
	}

	@Test
	void consoleValuesAreChecked() {
		Text.setLanguage(Text.Language.EN);
		MovementSettings m = new MovementSettings();
		assertEquals("800", m.get("sv_gravity"));
		assertEquals("10", m.get("sv_airaccelerate"));
		assertEquals("0", m.get("sv_autobunnyhopping"));
		assertNull(m.get("sv_cheats"));

		assertNull(m.set("sv_airaccelerate", "100"));
		assertEquals("100", m.get("sv_airaccelerate"));
		assertNull(m.set("sv_airaccelerate", "12.5"));
		assertEquals("12.5", m.get("sv_airaccelerate"));
		assertNull(m.set("sv_autobunnyhopping", "1"));
		assertTrue(m.autoBunnyhop);

		assertEquals("sv_gravity must be between 100 and 4000", m.set("sv_gravity", "5"));
		assertNotNull(m.set("sv_airaccelerate", "-1"));
		assertNotNull(m.set("sv_airaccelerate", "mucho"));
		assertNotNull(m.set("sv_autobunnyhopping", "2"));
		assertEquals("800", m.get("sv_gravity"), "a rejected value changes nothing");
		assertFalse(m.equals(new MovementSettings()));
		assertEquals(m, new MovementSettings().set(m));
	}
}
