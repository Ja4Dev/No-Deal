package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FootstepsTest {
	private static final float DT = 1f / 60f;

	/** Steps heard while moving at {@code speed} for {@code seconds}. */
	private static int steps(Footsteps footsteps, float speed, boolean onGround, float seconds) {
		int count = 0;
		for (int i = 0; i < seconds / DT; i++)
			if (footsteps.update(speed, onGround, DT) != Footsteps.Step.NONE)
				count++;
		return count;
	}

	@Test
	void runningSoundsAboutThreeStepsASecond() {
		int steps = steps(new Footsteps(), PlayerMovement.RUN_SPEED, true, 2);
		assertEquals(Math.round(2 * PlayerMovement.RUN_SPEED / Footsteps.STRIDE), steps, 1);
	}

	@Test
	void walkingStandingAndJumpingAreSilent() {
		assertEquals(0, steps(new Footsteps(), PlayerMovement.WALK_SPEED, true, 3), "Shift walks silently");
		assertEquals(0, steps(new Footsteps(), 0, true, 3));
		assertEquals(0, steps(new Footsteps(), PlayerMovement.RUN_SPEED, false, 3), "no steps in the air");
	}

	@Test
	void startingToRunStepsSoon() {
		Footsteps footsteps = new Footsteps();
		steps(footsteps, 0, true, 1);
		float time = 0;
		while (footsteps.update(PlayerMovement.RUN_SPEED, true, DT) == Footsteps.Step.NONE)
			time += DT;
		assertTrue(time < 0.25f, "first step after " + time + " s");
	}

	@Test
	void landingFromAJumpIsHeardButNotFromAStep() {
		Footsteps footsteps = new Footsteps();
		steps(footsteps, 0, false, 0.6f); // a jump
		assertEquals(Footsteps.Step.LANDING, footsteps.update(0, true, DT));
		steps(footsteps, 0, false, 0.1f); // stepping down a stair
		assertEquals(Footsteps.Step.NONE, footsteps.update(0, true, DT));
	}
}
