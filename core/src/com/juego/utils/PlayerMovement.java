package com.juego.utils;

import com.badlogic.gdx.math.Vector3;

/**
 * How the player's horizontal velocity responds to input, in the style of
 * Quake and Counter-Strike: on the ground there is friction and quick (but
 * not instant) acceleration, so starting and stopping take a fraction of a
 * second; in the air you keep your momentum and can only steer a little.
 * <p>
 * Pure math, run once per physics step, so it behaves the same at any frame rate.
 * Speeds are in world units per second (a character is 6 units tall).
 */
public final class PlayerMovement {
	public static final float RUN_SPEED = 12f;
	/** Holding the walk key: half speed, quiet and accurate. */
	public static final float WALK_SPEED = 6f;
	/** Crouching: slower than walking, and silent too. */
	public static final float CROUCH_SPEED = 4f;
	/** Upwards speed of a jump: with {@link #GRAVITY} it reaches ~3.8 units (like CS) in under 0.4 s. */
	public static final float JUMP_SPEED = 19f;
	public static final float GRAVITY = 48f;
	/** Air strafing can't push the horizontal speed past this. */
	public static final float MAX_AIR_SPEED = RUN_SPEED * 1.15f;

	/** Ground acceleration: full speed in about 0.2 s. */
	static final float ACCELERATE = 10f;
	/** Ground friction: from running to standing in about 0.25 s. */
	static final float FRICTION = 8f;
	/** Below this speed friction acts as if moving this fast, so you come to a clean stop. */
	static final float STOP_SPEED = 4f;
	/** In the air you can only add this much speed in the direction you press (CS: 30 of 250). */
	static final float AIR_WISH_SPEED = RUN_SPEED * 0.12f;
	static final float AIR_ACCELERATE = 10f;

	private PlayerMovement() {
	}

	/**
	 * Advances the horizontal velocity by one step.
	 *
	 * @param velocity  horizontal velocity, updated in place (y is ignored and left at 0)
	 * @param wishDir   direction the player presses, unit length or zero
	 * @param wishSpeed {@link #RUN_SPEED} or {@link #WALK_SPEED}
	 */
	public static void step(Vector3 velocity, Vector3 wishDir, float wishSpeed, boolean onGround, float dt) {
		velocity.y = 0;
		if (onGround) {
			applyFriction(velocity, dt);
			accelerate(velocity, wishDir, wishSpeed, wishSpeed, ACCELERATE, dt);
		} else {
			accelerate(velocity, wishDir, Math.min(wishSpeed, AIR_WISH_SPEED), wishSpeed, AIR_ACCELERATE, dt);
			velocity.limit(MAX_AIR_SPEED);
		}
	}

	private static void applyFriction(Vector3 velocity, float dt) {
		float speed = velocity.len();
		if (speed < 0.01f) {
			velocity.setZero();
			return;
		}
		float drop = Math.max(speed, STOP_SPEED) * FRICTION * dt;
		velocity.scl(Math.max(0, speed - drop) / speed);
	}

	/**
	 * Quake's acceleration: adds speed along {@code wishDir} until the speed in
	 * that direction reaches {@code wishSpeed}. In the air the cap is tiny but
	 * the rate isn't, which is what makes strafing in the air turn you.
	 */
	private static void accelerate(Vector3 velocity, Vector3 wishDir, float wishSpeed, float rateSpeed, float accel,
			float dt) {
		if (wishDir.isZero())
			return;
		float current = velocity.dot(wishDir);
		float add = wishSpeed - current;
		if (add <= 0)
			return;
		float accelSpeed = Math.min(accel * rateSpeed * dt, add);
		velocity.mulAdd(wishDir, accelSpeed);
	}
}
