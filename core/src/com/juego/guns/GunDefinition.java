package com.juego.guns;

/** Static, data-driven description of a gun. Loaded from {@code data/guns.json} by {@link Armory}. */
public class GunDefinition {
	public enum Slot {
		PRIMARY, SECONDARY
	}

	/** Longest shot: well beyond the biggest level (the Bank is about 160 units across). */
	public static final float MAX_RANGE = 1000;
	/** Distance over which a bullet keeps {@link #rangeModifier} of its damage. */
	public static final float FALLOFF_DISTANCE = 50;

	/**
	 * Damage of a body shot that travelled {@code distance} units: a little less
	 * the farther it went, but never nothing.
	 */
	public int damageAt(float distance) {
		double kept = Math.pow(rangeModifier, Math.max(0, distance) / FALLOFF_DISTANCE);
		return Math.max(1, (int) Math.round(damage * kept));
	}

	public String id;
	public String name;
	public Slot slot;
	public int damage;
	public int magazineSize;
	/** Spare rounds carried outside the magazine; a negative value means infinite. */
	public int reserveAmmo;
	public boolean automatic;
	/**
	 * How far a bullet flies, in world units. Farther than any level, so a shot
	 * always reaches what it is aimed at; distance only lowers its damage
	 * ({@link #rangeModifier}).
	 */
	public float range = MAX_RANGE;
	/**
	 * Damage kept every {@link #FALLOFF_DISTANCE} units the bullet travels, as in
	 * CS: 0.85 means 85% after 50 units, 72% after 100, 52% after 200. 1 = no
	 * falloff.
	 */
	public float rangeModifier = 1;
	public long fireDelayMs;
	/**
	 * Spray pattern (see {@link Recoil}): for each bullet of a burst, its offset
	 * from the aim in degrees, as {up, right}. The first entry is the first shot,
	 * normally {0, 0}. Null means no pattern.
	 */
	public float[][] spray;
	/** Quick jolt of the view on every shot, in degrees; it springs back on its own. */
	public float punch;
	/** Random inaccuracy, in degrees: standing still, extra per shot of the burst, running, and in the air. */
	public float spreadBase, spreadPerShot, spreadMove, spreadAir;
	/** Whether the gun can aim down sights (zoom). */
	public boolean hasSight;
	/** Internal path of the view model (.g3dj). */
	public String model;
	/** Internal path of the shot sound. */
	public String sound;
	/** Length of the gun lying on the ground, in world units (a character is 6 units tall). */
	public float worldLength = 2.5f;
	/** Position of the view model relative to the gun camera. */
	public float[] viewOffset = new float[3];
}
