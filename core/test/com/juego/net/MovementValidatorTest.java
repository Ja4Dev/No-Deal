package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;
import com.juego.utils.PlayerMovement;

class MovementValidatorTest {
	private MovementValidator validator;

	@BeforeEach
	void setUp() {
		validator = new MovementValidator();
		validator.reset(new Vector3(0, 4, 0), 1000);
		assertTrue(validator.accept(new Vector3(0, 4, 0), 50_000, 1000), "first state at the spawn");
	}

	@Test
	void acceptsRunningSpeed() {
		long client = 50_000, server = 1000;
		for (int i = 1; i <= 30; i++) {
			client += 33;
			server += 33;
			assertTrue(validator.accept(new Vector3(i * PlayerMovement.RUN_SPEED * 0.033f, 4, 0), client, server), "step " + i);
		}
	}

	@Test
	void acceptsTopSpeedInTheAir() {
		long client = 50_000, server = 1000;
		for (int i = 1; i <= 30; i++) {
			client += 16;
			server += 16;
			assertTrue(validator.accept(new Vector3(i * PlayerMovement.MAX_SPEED * 0.016f, 4, 0), client, server),
					"step " + i);
		}
	}

	@Test
	void acceptsPacketsArrivingBunchedTogether() {
		// Sent 33 ms apart, but all three arrive at the same server instant.
		assertTrue(validator.accept(new Vector3(0.33f, 4, 0), 50_033, 1100));
		assertTrue(validator.accept(new Vector3(0.66f, 4, 0), 50_066, 1100));
		assertTrue(validator.accept(new Vector3(0.99f, 4, 0), 50_099, 1100));
	}

	@Test
	void rejectsTeleporting() {
		assertFalse(validator.accept(new Vector3(30, 4, 0), 50_033, 1033));
	}

	@Test
	void rejectsClientClockRunningFast() {
		// Claims 2 s passed when the server only saw 100 ms: a speed hack.
		assertFalse(validator.accept(new Vector3(20, 4, 0), 52_000, 1100));
	}

	@Test
	void allowsFalling() {
		assertTrue(validator.accept(new Vector3(0, 4 - 50 * 0.5f, 0), 50_500, 1500));
	}

	@Test
	void rejectedStateDoesNotMoveTheBaseline() {
		assertFalse(validator.accept(new Vector3(30, 4, 0), 50_033, 1033));
		assertTrue(validator.accept(new Vector3(0.6f, 4, 0), 50_066, 1066), "measured from the last accepted position");
	}
}
