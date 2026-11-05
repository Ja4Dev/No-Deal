package com.juego.guns;

/** Static, data-driven description of a gun. Loaded from {@code data/guns.json} by {@link Armory}. */
public class GunDefinition {
	public enum Slot {
		PRIMARY, SECONDARY
	}

	public String id;
	public String name;
	public Slot slot;
	public int damage;
	public int magazineSize;
	/** Spare rounds carried outside the magazine; a negative value means infinite. */
	public int reserveAmmo;
	public boolean automatic;
	public float range;
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
