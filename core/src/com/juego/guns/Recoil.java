package com.juego.guns;

import com.badlogic.gdx.math.MathUtils;

/**
 * Spray recoil in the style of CS 1.6. Every gun has a fixed pattern: the
 * offset (up, right) of each bullet of a burst from where the player aims.
 * The first bullet goes where you aim; holding the trigger, the next ones
 * climb and then wander sideways, always the same way, so a player can learn
 * to pull the mouse against it. The view climbs with the pattern, so the
 * crosshair always shows where the next bullet goes, and when the trigger is
 * released it comes back down within a fraction of a second.
 * <p>
 * On top of the pattern there is a random inaccuracy: tiny when standing
 * still, larger as the burst goes on, and large when running or in the air.
 * <p>
 * Pure logic (angles in degrees), so it is tested without a window.
 */
public class Recoil {
	/** After the last shot, recovery waits the gun's fire delay plus this. */
	static final long RECOVERY_DELAY_MS = 40;
	/** Recovery speed, in pattern steps per second: a constant part plus a part proportional to the burst. */
	static final float RECOVERY_BASE = 6f, RECOVERY_RATE = 4f;
	/** The burst stops adding inaccuracy after this many shots. */
	static final int MAX_SPREAD_SHOTS = 10;

	/** Position in the pattern: 0 = next bullet is a first shot. Fractional while recovering. */
	private float shots;
	private long lastShotMs = Long.MIN_VALUE / 2;
	/** Current offset of the pattern, in degrees. */
	private float up, right;

	/** Forgets the burst (new gun in hand, respawn). */
	public void reset() {
		shots = 0;
		up = right = 0;
		lastShotMs = Long.MIN_VALUE / 2;
	}

	/** Lets the pattern come back down while not shooting. Call every frame. */
	public void update(GunDefinition def, float delta, long nowMs) {
		if (shots > 0 && nowMs - lastShotMs >= def.fireDelayMs + RECOVERY_DELAY_MS)
			shots = Math.max(0, shots - (RECOVERY_BASE + shots * RECOVERY_RATE) * delta);
		sample(def, shots);
	}

	/**
	 * A shot: where it goes relative to the aim (call {@link #up()} and
	 * {@link #right()} right after) and how inaccurate it is.
	 */
	public void shoot(GunDefinition def, long nowMs) {
		sample(def, shots);
		lastShotMs = nowMs;
		shots = Math.min(shots + 1, patternLength(def) - 1);
	}

	/** Offset of the pattern now (or of the bullet just shot), degrees up. */
	public float up() {
		return up;
	}

	/** Offset of the pattern now (or of the bullet just shot), degrees to the right. */
	public float right() {
		return right;
	}

	public float shots() {
		return shots;
	}

	/**
	 * Radius of the random inaccuracy cone, in degrees.
	 *
	 * @param speedFraction horizontal speed divided by the run speed
	 */
	public float inaccuracy(GunDefinition def, float speedFraction, boolean airborne) {
		return def.spreadBase + firingInaccuracy(def) + movementInaccuracy(def, speedFraction, airborne);
	}

	/** The part of {@link #inaccuracy} that comes from the shots in a row (degrees). */
	public float firingInaccuracy(GunDefinition def) {
		return def.spreadPerShot * Math.min(shots, MAX_SPREAD_SHOTS);
	}

	/** The part of {@link #inaccuracy} that comes from moving and jumping (degrees). */
	public float movementInaccuracy(GunDefinition def, float speedFraction, boolean airborne) {
		// Walking (half speed) is as accurate as standing still; it gets worse from there to running.
		float moving = MathUtils.clamp((speedFraction - 0.55f) / 0.45f, 0, 1);
		return def.spreadMove * moving + (airborne ? def.spreadAir : 0);
	}

	private static int patternLength(GunDefinition def) {
		return def.spray == null ? 1 : def.spray.length;
	}

	/** Interpolates the pattern at a fractional step, so recovery is smooth. */
	private void sample(GunDefinition def, float at) {
		if (def.spray == null || def.spray.length == 0) {
			up = right = 0;
			return;
		}
		int i = Math.min((int) at, def.spray.length - 1);
		int j = Math.min(i + 1, def.spray.length - 1);
		float t = at - i;
		up = MathUtils.lerp(def.spray[i][0], def.spray[j][0], t);
		right = MathUtils.lerp(def.spray[i][1], def.spray[j][1], t);
	}
}
