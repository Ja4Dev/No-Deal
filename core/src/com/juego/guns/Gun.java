package com.juego.guns;

/**
 * Runtime state of a gun the player is carrying: what's in the magazine and in
 * reserve. Everything static lives in its {@link GunDefinition}.
 */
public class Gun {
	public final GunDefinition def;
	private int magazine;
	private int reserve;

	public Gun(GunDefinition def) {
		this.def = def;
		refill();
	}

	public int getMagazine() {
		return magazine;
	}

	public int getReserve() {
		return reserve;
	}

	public boolean hasInfiniteReserve() {
		return def.reserveAmmo < 0;
	}

	public boolean isEmpty() {
		return magazine <= 0;
	}

	/** Spends one round. @return false if the magazine was empty. */
	public boolean consumeRound() {
		if (isEmpty())
			return false;
		magazine--;
		return true;
	}

	public boolean canReload() {
		return magazine < def.magazineSize && (hasInfiniteReserve() || reserve > 0);
	}

	/** Moves rounds from the reserve into the magazine. @return false if nothing was reloaded. */
	public boolean reload() {
		if (!canReload())
			return false;
		int needed = def.magazineSize - magazine;
		int loaded = hasInfiniteReserve() ? needed : Math.min(needed, reserve);
		magazine += loaded;
		if (!hasInfiniteReserve())
			reserve -= loaded;
		return true;
	}

	/** Full magazine and starting reserve, as when the player spawns. */
	public void refill() {
		magazine = def.magazineSize;
		reserve = def.reserveAmmo;
	}
}
