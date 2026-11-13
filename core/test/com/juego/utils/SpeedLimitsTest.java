package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;
import com.juego.net.MovementValidator;

/** sv_maxspeed, sv_maxvelocity and sv_falldamage, in CS's units. */
class SpeedLimitsTest {

	@Test
	void speedsAreInCsUnits() {
		MovementSettings m = new MovementSettings();
		assertEquals(PlayerMovement.RUN_SPEED, m.runSpeed(), 1e-4f, "250 is running");
		assertEquals(PlayerMovement.WALK_SPEED, m.walkSpeed(), 1e-4f);
		assertEquals(PlayerMovement.CROUCH_SPEED, m.crouchSpeed(), 1e-4f);
		assertEquals(PlayerMovement.MAX_SPEED, m.topSpeed(), 1e-3f, "750 is three times running");
		assertEquals("750", m.get(MovementSettings.MAX_VELOCITY));

		assertNull(m.set(MovementSettings.MAX_SPEED, "500"));
		assertEquals(2 * PlayerMovement.RUN_SPEED, m.runSpeed(), 1e-4f);
		assertEquals(2 * PlayerMovement.WALK_SPEED, m.walkSpeed(), 1e-4f, "walking keeps its share");
		assertNotNull(m.set(MovementSettings.MAX_SPEED, "10"));
		assertNotNull(m.set(MovementSettings.MAX_VELOCITY, "99999"));
		assertNull(m.set(MovementSettings.FALL_DAMAGE, "0"));
		assertFalse(m.fallDamage);
		assertNotNull(m.set(MovementSettings.FALL_DAMAGE, "si"));
	}

	@Test
	void theTopSpeedCapsEverything() {
		Vector3 velocity = new Vector3(0, 0, 100);
		PlayerMovement.step(velocity, new Vector3(), PlayerMovement.RUN_SPEED, false, 1 / 60f, 10, 20);
		assertEquals(20, velocity.len(), 1e-3f);
		velocity.set(0, 0, 100);
		PlayerMovement.step(velocity, new Vector3(), PlayerMovement.RUN_SPEED, false, 1 / 60f, 10, 200);
		assertEquals(100, velocity.len(), 1e-3f, "a higher top lets it be");
	}

	@Test
	void theServerAllowsWhatItsLimitsAllow() {
		MovementValidator v = new MovementValidator();
		v.reset(new Vector3(), 0);
		assertTrue(v.accept(new Vector3(), 10_000, 0));
		float fast = PlayerMovement.MAX_SPEED * 2;
		assertFalse(v.accept(new Vector3(fast * 0.1f, 0, 0), 10_100, 100), "twice the normal top speed");

		MovementSettings m = new MovementSettings();
		m.maxVelocity = m.maxVelocity * 3;
		v.setLimits(m.fastestSpeed(), m.runSpeed(), 100);
		assertTrue(v.accept(new Vector3(fast * 0.1f, 0, 0), 10_100, 100), "allowed right away after raising it");

		// Lowered again: the old limit still holds for a moment (the players don't know yet), then not.
		v.setLimits(PlayerMovement.MAX_SPEED, PlayerMovement.RUN_SPEED, 200);
		assertTrue(v.accept(new Vector3(fast * 0.2f, 0, 0), 10_200, 200), "still moving by the old rules");
		assertFalse(v.accept(new Vector3(fast * 0.3f, 0, 0), 10_300, 200 + MovementValidator.LIMIT_GRACE_MS),
				"the lower limit applies after the grace time");
	}

	@Test
	void fallingFastIsAllowedFlyingUpIsNot() {
		MovementValidator v = new MovementValidator();
		v.reset(new Vector3(0, 100, 0), 0);
		assertTrue(v.accept(new Vector3(0, 100, 0), 10_000, 0));
		assertTrue(v.accept(new Vector3(0, 90, 0), 10_100, 100), "100 units/s down");
		assertFalse(v.accept(new Vector3(0, 100, 0), 10_200, 200), "100 units/s up");
	}
}
