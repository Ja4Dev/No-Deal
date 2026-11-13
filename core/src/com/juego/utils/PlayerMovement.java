package com.juego.utils;

import com.badlogic.gdx.math.Vector3;

/**
 * How the player's horizontal velocity responds to input, in the style of
 * Quake and Counter-Strike: on the ground there is friction and quick (but
 * not instant) acceleration, so starting and stopping take a fraction of a
 * second; in the air you keep your momentum and can only steer a little.
 * <p>
 * Steering in the air uses Quake's acceleration, so turning while strafing in
 * the air gains speed, and jumping again the moment you land keeps it
 * (bunnyhopping): how much is up to the server's sv_airaccelerate (see
 * {@link MovementSettings}).
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
	/** Normal gravity (sv_gravity 800). */
	public static final float GRAVITY = 48f;
	/** Normally nothing goes faster than this horizontally, however good the bunnyhop: three times running. */
	public static final float MAX_SPEED = RUN_SPEED * 3f;

	/** Ground acceleration: full speed in about 0.2 s. */
	static final float ACCELERATE = 10f;
	/** Ground friction: from running to standing in about 0.25 s. */
	static final float FRICTION = 8f;
	/** Below this speed friction acts as if moving this fast, so you come to a clean stop. */
	static final float STOP_SPEED = 4f;
	/** In the air you can only add this much speed in the direction you press (CS: 30 of 250). */
	static final float AIR_WISH_SPEED = RUN_SPEED * 0.12f;
	/**
	 * Quake's air acceleration is worked out once per frame, and CS players
	 * play at 100+ frames per second, which is when sv_airaccelerate makes a
	 * difference. The air part of each physics step is split into steps this
	 * long, so the air control feels like CS's (and still doesn't depend on
	 * the frame rate).
	 */
	static final float AIR_SUBSTEP = 1f / 120f;

	/** Scratch for the air steps (players and bots all move on the game or server thread). */
	private static final Vector3 airWish = new Vector3();

	private PlayerMovement() {
	}

	/** With the normal air acceleration (sv_airaccelerate 10). */
	public static void step(Vector3 velocity, Vector3 wishDir, float wishSpeed, boolean onGround, float dt) {
		step(velocity, wishDir, wishSpeed, onGround, dt, MovementSettings.DEFAULT_AIR_ACCELERATE);
	}

	/** With the normal top speed (sv_maxvelocity 750). */
	public static void step(Vector3 velocity, Vector3 wishDir, float wishSpeed, boolean onGround, float dt,
			float airAccelerate) {
		step(velocity, wishDir, wishSpeed, onGround, dt, airAccelerate, MAX_SPEED);
	}

	/**
	 * Advances the horizontal velocity by one step.
	 *
	 * @param velocity      horizontal velocity, updated in place (y is ignored and left at 0)
	 * @param wishDir       direction the player presses, unit length or zero
	 * @param wishSpeed     running, walking or crouching speed (see {@link MovementSettings#runSpeed()})
	 * @param onGround      false also for the step in which the player jumps: no friction then, so
	 *                      jumping again the moment you land keeps your speed
	 * @param airAccelerate sv_airaccelerate
	 * @param topSpeed      nothing goes faster (sv_maxvelocity, see {@link MovementSettings#topSpeed()})
	 */
	public static void step(Vector3 velocity, Vector3 wishDir, float wishSpeed, boolean onGround, float dt,
			float airAccelerate, float topSpeed) {
		velocity.y = 0;
		if (onGround) {
			applyFriction(velocity, dt);
			accelerate(velocity, wishDir, wishSpeed, wishSpeed, ACCELERATE, dt);
		} else {
			int substeps = Math.max(1, Math.round(dt / AIR_SUBSTEP));
			// Between frames the player keeps turning the mouse with the strafe, so the wish direction
			// keeps its angle to the velocity rather than staying put (with a fixed view this barely matters:
			// it is a fraction of a step).
			boolean turning = !wishDir.isZero() && velocity.len2() > 0.0001f;
			float relative = turning ? (float) Math.atan2(wishDir.x, wishDir.z) - (float) Math.atan2(velocity.x, velocity.z) : 0;
			airWish.set(wishDir);
			for (int i = 0; i < substeps; i++) {
				if (turning && i > 0) {
					float heading = (float) Math.atan2(velocity.x, velocity.z) + relative;
					airWish.set((float) Math.sin(heading), 0, (float) Math.cos(heading));
				}
				accelerate(velocity, airWish, Math.min(wishSpeed, AIR_WISH_SPEED), wishSpeed, airAccelerate, dt / substeps);
			}
		}
		velocity.limit(topSpeed);
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
