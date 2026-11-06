package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;

class PlayerMovementTest {
	private static final float DT = 1f / 60f;
	private static final Vector3 NONE = new Vector3();
	private static final Vector3 FORWARD = new Vector3(0, 0, 1);
	private static final Vector3 RIGHT = new Vector3(-1, 0, 0);

	private static float timeToReach(Vector3 velocity, Vector3 wish, float speed, float target) {
		for (int step = 1; step < 600; step++) {
			PlayerMovement.step(velocity, wish, speed, true, DT);
			if (velocity.len() >= target)
				return step * DT;
		}
		return Float.MAX_VALUE;
	}

	@Test
	void reachesRunningSpeedQuicklyButNotInstantly() {
		Vector3 velocity = new Vector3();
		PlayerMovement.step(velocity, FORWARD, PlayerMovement.RUN_SPEED, true, DT);
		assertTrue(velocity.len() < PlayerMovement.RUN_SPEED * 0.5f, "not instant");
		float time = timeToReach(velocity, FORWARD, PlayerMovement.RUN_SPEED, PlayerMovement.RUN_SPEED * 0.99f);
		assertTrue(time < 0.35f, "about 0.2 s, took " + time);
		for (int i = 0; i < 120; i++)
			PlayerMovement.step(velocity, FORWARD, PlayerMovement.RUN_SPEED, true, DT);
		assertEquals(PlayerMovement.RUN_SPEED, velocity.len(), 0.01f, "and never faster");
	}

	@Test
	void stopsShortlyAfterReleasingTheKeys() {
		Vector3 velocity = new Vector3(0, 0, PlayerMovement.RUN_SPEED);
		float time = 0;
		while (!velocity.isZero() && time < 2) {
			PlayerMovement.step(velocity, NONE, PlayerMovement.RUN_SPEED, true, DT);
			time += DT;
		}
		assertTrue(velocity.isZero(), "comes to a full stop");
		assertTrue(time > 0.1f && time < 0.4f, "in about a quarter of a second, took " + time);
	}

	@Test
	void walkingIsHalfSpeed() {
		Vector3 velocity = new Vector3();
		for (int i = 0; i < 120; i++)
			PlayerMovement.step(velocity, FORWARD, PlayerMovement.WALK_SPEED, true, DT);
		assertEquals(PlayerMovement.WALK_SPEED, velocity.len(), 0.01f);
	}

	@Test
	void inTheAirYouKeepYourMomentum() {
		Vector3 velocity = new Vector3(0, 0, PlayerMovement.RUN_SPEED);
		for (int i = 0; i < 30; i++)
			PlayerMovement.step(velocity, NONE, PlayerMovement.RUN_SPEED, false, DT);
		assertEquals(PlayerMovement.RUN_SPEED, velocity.len(), 0.001f, "no friction in the air");

		Vector3 standing = new Vector3();
		for (int i = 0; i < 30; i++)
			PlayerMovement.step(standing, FORWARD, PlayerMovement.RUN_SPEED, false, DT);
		assertTrue(standing.len() < PlayerMovement.RUN_SPEED * 0.2f, "can barely speed up mid-jump: " + standing.len());
	}

	@Test
	void strafingInTheAirTurnsButIsCapped() {
		Vector3 velocity = new Vector3(0, 0, PlayerMovement.RUN_SPEED);
		for (int i = 0; i < 300; i++)
			PlayerMovement.step(velocity, RIGHT, PlayerMovement.RUN_SPEED, false, DT);
		assertTrue(velocity.x < -1f, "the velocity turned towards the strafe");
		assertTrue(velocity.len() <= PlayerMovement.MAX_AIR_SPEED + 1e-4f, "never faster than the cap");
	}

	@Test
	void aJumpIsAboutAsHighAsCs() {
		// CS: about 45 of 72 units of player height, 0.62 of it; here the player is 6 units tall.
		float height = PlayerMovement.JUMP_SPEED * PlayerMovement.JUMP_SPEED / (2 * PlayerMovement.GRAVITY);
		assertEquals(0.62f * 6, height, 0.25f);
	}
}
