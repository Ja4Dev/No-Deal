package com.juego.utils;

/**
 * Dimensions of every character capsule (players and enemies), shared by the
 * client and the server so that hit detection agrees on both sides.
 */
public final class CharacterShape {
	public static final float RADIUS = 2f;
	/** Length of the cylindrical part; total height is CYLINDER + 2 * RADIUS = 6. */
	public static final float CYLINDER = 2f;
	public static final float HALF_HEIGHT = CYLINDER / 2 + RADIUS;
	/**
	 * Where bullets hit: a slimmer capsule of the same height that matches the
	 * soldier model (shoulders and arms about 2.8 across), so shots that look
	 * like they miss do miss. Movement still uses {@link #RADIUS}.
	 */
	public static final float HITBOX_RADIUS = 1.4f;
	public static final float HITBOX_CYLINDER = 2 * HALF_HEIGHT - 2 * HITBOX_RADIUS;
	/** Hits on the top ~1.2 units of the capsule (measured from its center) count as headshots. */
	public static final float HEAD_OFFSET = HALF_HEIGHT - 1.2f;
	/**
	 * Height of the eyes above the capsule center: 5.2 units above the feet, near
	 * the top of the head, like CS (eyes at about 89% of the player's height).
	 */
	public static final float EYE_OFFSET = 2.2f;

	private CharacterShape() {
	}
}
