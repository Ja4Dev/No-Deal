package com.juego.utils;

/**
 * How much a fall hurts, as in CS: landing slower than a safe speed does
 * nothing, and from there the damage grows with the speed until it kills.
 * <p>
 * It goes by the speed of the impact, not the height, so gravity matters: at
 * normal gravity (sv_gravity 800) a drop of about three times a player's
 * height starts to hurt and one of about nine kills, like CS's; with lower
 * gravity you land slower and the same drop hurts less (or not at all), with
 * higher gravity more. A jump on flat ground never hurts: it lands at the
 * speed it took off with.
 */
public final class FallDamage {
	/** Drop that starts to hurt at normal gravity: ~3 player heights (CS: 210 of 72 units). */
	public static final float SAFE_HEIGHT = 17.5f;
	/** Drop that kills at normal gravity: ~9 player heights (CS: 655 of 72 units). */
	public static final float FATAL_HEIGHT = 54.6f;
	/** Landing speeds (units/s) where falls start to hurt and where they kill. */
	public static final float SAFE_SPEED = speedAfter(SAFE_HEIGHT, PlayerMovement.GRAVITY),
			FATAL_SPEED = speedAfter(FATAL_HEIGHT, PlayerMovement.GRAVITY);
	/** Nothing falls faster (the character controller's limit), so even huge falls can be measured. */
	public static final float MAX_FALL_SPEED = 150f;

	private FallDamage() {
	}

	/** Speed after falling {@code height} from rest with {@code gravity} (units/s²). */
	public static float speedAfter(float height, float gravity) {
		return (float) Math.sqrt(2 * gravity * Math.max(0, height));
	}

	/** Health lost landing at {@code speed} units/s (downwards): 0 below {@link #SAFE_SPEED}, 100 or more at {@link #FATAL_SPEED}. */
	public static int damage(float speed) {
		if (speed <= SAFE_SPEED)
			return 0;
		return Math.max(1, Math.round((speed - SAFE_SPEED) * 100 / (FATAL_SPEED - SAFE_SPEED)));
	}

	/**
	 * The fastest someone can land after being at most {@code drop} units above
	 * where they land, jumping included: a check on what clients report.
	 */
	public static float fastestLanding(float drop, float gravity) {
		float jumpHeight = PlayerMovement.JUMP_SPEED * PlayerMovement.JUMP_SPEED / (2 * gravity);
		return Math.min(MAX_FALL_SPEED, speedAfter(Math.max(0, drop) + jumpHeight, gravity));
	}
}
