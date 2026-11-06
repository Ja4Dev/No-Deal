package com.juego.utils;

/**
 * When a character's footsteps sound, as in CS: running makes noise, one
 * step every stride, while walking (holding Shift) and being in the air are
 * silent. Landing from a jump or a fall makes a louder step. Pure logic, so
 * the same rules serve the local player and everyone else.
 */
public class Footsteps {
	/** Distance between two steps when running (a character is 6 units tall). */
	public static final float STRIDE = 4.2f;
	/** Slower than this is walking: no sound. Running is 12, walking 6. */
	public static final float MIN_SPEED = PlayerMovement.WALK_SPEED * 1.15f;
	/** A fall shorter than this lands silently (stairs, small bumps). */
	static final float MIN_LANDING_AIR_TIME = 0.3f;

	public enum Step {
		NONE, STEP, LANDING
	}

	private float travelled = STRIDE / 2;
	private float airTime;
	private boolean wasOnGround = true;

	/**
	 * @param speed horizontal speed, units per second
	 * @return what sounds this frame
	 */
	public Step update(float speed, boolean onGround, float delta) {
		if (!onGround) {
			airTime += delta;
			wasOnGround = false;
			return Step.NONE;
		}
		boolean landed = !wasOnGround && airTime >= MIN_LANDING_AIR_TIME;
		wasOnGround = true;
		airTime = 0;
		if (landed) {
			travelled = 0;
			return Step.LANDING;
		}
		if (speed < MIN_SPEED) {
			// Standing or walking: the next run starts with a step soon, not a full stride later.
			travelled = Math.min(travelled, STRIDE / 2);
			return Step.NONE;
		}
		travelled += speed * delta;
		if (travelled < STRIDE)
			return Step.NONE;
		travelled = Math.min(travelled - STRIDE, STRIDE);
		return Step.STEP;
	}
}
