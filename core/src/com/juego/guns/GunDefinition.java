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
	/** Camera kick (degrees of pitch) applied per shot. */
	public float recoil;
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
