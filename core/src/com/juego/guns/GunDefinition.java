package com.juego.guns;

import com.juego.utils.Text;

/**
 * Static, data-driven description of a gun (or the knife). Loaded from
 * {@code data/guns.json} by {@link Armory}.
 */
public class GunDefinition {
	/** Where a player carries it: one gun of each, and always the knife. */
	public enum Slot {
		PRIMARY, SECONDARY, MELEE
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
	/**
	 * Its weight: how fast you run with it in hand, in CS's units, as in CS.
	 * 250 (the knife) is the fastest; pistols 240; rifles 190 to 235.
	 */
	public int maxSpeed = FASTEST;
	/** Running with the lightest weapon (the knife): sv_maxspeed is this fast. */
	public static final int FASTEST = 250;

	/** Share of the full running speed (sv_maxspeed) with this in hand: 235 / 250 = 0.94. */
	public float speedFactor() {
		return maxSpeed / (float) FASTEST;
	}

	// ---- the knife ----
	// A melee weapon has two attacks: the slash (left click: damage, range, fireDelayMs) and the
	// stab (right click: the alt* fields). From behind, both hurt more (a backstab).

	/** Stab damage, and damage from behind of the slash and of the stab. */
	public int altDamage, backDamage, altBackDamage;
	/** How far the stab reaches (the slash reaches {@link #range}). */
	public float altRange;
	/** Time between a stab and the next attack. */
	public long altFireDelayMs;
	/** Internal path of the sound when it hits a wall. */
	public String hitSound;

	public boolean isMelee() {
		return slot == Slot.MELEE;
	}

	/** How far an attack reaches: the slash, or the stab ({@code alt}). */
	public float reach(boolean alt) {
		return alt ? altRange : range;
	}

	/** Time after an attack before the next one. */
	public long attackDelayMs(boolean alt) {
		return alt ? altFireDelayMs : fireDelayMs;
	}

	/** Damage of a knife attack: slash or stab, from the front or from behind. */
	public int meleeDamage(boolean alt, boolean fromBehind) {
		if (alt)
			return fromBehind ? altBackDamage : altDamage;
		return fromBehind ? backDamage : damage;
	}

	/** The name players read: translated if Text has "gun.<id>" ("Cuchillo"), otherwise {@link #name}. */
	public String displayName() {
		return Text.has("gun." + id) ? Text.get("gun." + id) : name;
	}
}
