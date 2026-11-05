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
	/** Hits on the top ~1.2 units of the capsule (measured from its center) count as headshots. */
	public static final float HEAD_OFFSET = HALF_HEIGHT - 1.2f;

	private CharacterShape() {
	}
}
