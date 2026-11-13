package com.juego.components;

import java.util.function.Supplier;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;

/**
 * State of a player. It is also how the player systems talk to each other:
 * PlayerSystem writes the movement state, WeaponSystem reads it for bullet
 * spread and writes back how recoil moves the view, and HudSystem owns the
 * pause/scoreboard flags.
 */
public class PlayerComponent implements Component {

	public static final int MAX_HEALTH = 100;
	public static final int HEADSHOT_MULTIPLIER = 3;

	private int health = MAX_HEALTH;
	private boolean dead = false;
	private int deaths = 0;
	public int kills = 0;
	public final String team;
	public final int playerNumber;

	/** Guns carried, indexed by {@link GunDefinition.Slot#ordinal()}. A slot is null after dropping its gun. */
	public final Gun[] guns = new Gun[GunDefinition.Slot.values().length];
	public int selectedSlot;
	/** Makes the guns a player spawns with (primary, secondary). */
	private final Supplier<Gun[]> loadout;

	/** Damage taken since the HUD last showed it, and where the last hit came from (null if unknown). */
	public int pendingDamage;
	public Vector3 lastDamageSource;

	// UI state (HudSystem)
	public boolean pause = false;
	public boolean scoreboard = false;
	/** Typing in the console: the keys are text, not movement. */
	public boolean console = false;

	// Look and movement state (PlayerSystem)
	/** Where the player aims, in degrees (yaw 0 = +Z; positive pitch looks down). Moved only by the mouse. */
	public float yaw, pitch;
	public boolean moving, running, onGround = true;
	/** Crouching (holding the key), and how far down the view has gone, 0 to 1 (eased). */
	public boolean crouched;
	public float crouchAmount;
	/** Horizontal velocity, units per second. */
	public final Vector3 velocity = new Vector3();

	// How the view is pushed away from the aim, in degrees up and to the right.
	/** By the spray pattern while shooting (WeaponSystem); comes back down when the trigger is released. */
	public float recoilUp, recoilRight;
	/** Quick jolt from a shot or a hit; springs back on its own (PlayerSystem). */
	public float punchUp, punchRight;

	public PlayerComponent(String team, Supplier<Gun[]> loadout, int playerNumber) {
		this.team = team;
		this.loadout = loadout;
		this.playerNumber = playerNumber;
		resetLoadout();
	}

	// ---- guns ----

	/** The gun in hand. Null only while dead after the gun was dropped. */
	public Gun selectedGun() {
		return guns[selectedSlot];
	}

	public int gunCount() {
		int count = 0;
		for (Gun gun : guns)
			if (gun != null)
				count++;
		return count;
	}

	/** Puts a gun in its slot and takes it in hand. @return the gun that was in that slot, or null */
	public Gun equip(Gun gun) {
		int slot = gun.def.slot.ordinal();
		Gun previous = guns[slot];
		guns[slot] = gun;
		selectedSlot = slot;
		return previous;
	}

	/** Empties a slot; if it was the gun in hand, takes the other one. @return the removed gun */
	public Gun removeGun(int slot) {
		Gun removed = guns[slot];
		guns[slot] = null;
		if (selectedSlot == slot)
			switchGun();
		return removed;
	}

	/** Takes the next gun carried. @return false if there is no other gun */
	public boolean switchGun() {
		for (int i = 1; i < guns.length; i++) {
			int slot = (selectedSlot + i) % guns.length;
			if (guns[slot] != null) {
				selectedSlot = slot;
				return true;
			}
		}
		return false;
	}

	private void resetLoadout() {
		Gun[] spawnGuns = loadout.get();
		for (int i = 0; i < guns.length; i++)
			guns[i] = null;
		for (Gun gun : spawnGuns)
			guns[gun.def.slot.ordinal()] = gun;
		selectedSlot = GunDefinition.Slot.PRIMARY.ordinal();
		if (guns[selectedSlot] == null)
			switchGun();
	}

	/** Notes a hit for the HUD's damage effect. {@code source} may be null. */
	public void hurt(int damage, Vector3 source) {
		pendingDamage += damage;
		if (source != null) {
			if (lastDamageSource == null)
				lastDamageSource = new Vector3();
			lastDamageSource.set(source);
		} else {
			lastDamageSource = null;
		}
	}

	public int getHealth() {
		return health;
	}

	public boolean isDead() {
		return dead;
	}

	public int getDeaths() {
		return deaths;
	}

	public void getDamage(int damage) {
		applyDamage(damage);
	}

	public void getDamageHead(int damage) {
		applyDamage(damage * HEADSHOT_MULTIPLIER);
	}

	private void applyDamage(int damage) {
		if (dead)
			return;
		health -= damage;
		if (health <= 0) {
			health = 0;
			dead = true;
			deaths++;
		}
	}

	// ---- Online matches: the server is the authority on health and score ----

	public void setFromServer(int health, int kills, int deaths) {
		if (!dead)
			this.health = health;
		this.kills = kills;
		this.deaths = deaths;
	}

	/** The server says this player died. Deaths are counted by the server, not here. */
	public void killedByServer() {
		health = 0;
		dead = true;
	}

	/** New match: scores back to zero. */
	public void resetScore() {
		kills = 0;
		deaths = 0;
	}

	/** Restores health and the spawn loadout after a death. Repositioning is handled by the PlayerSystem. */
	public void respawn() {
		health = MAX_HEALTH;
		dead = false;
		recoilUp = recoilRight = punchUp = punchRight = 0;
		velocity.setZero();
		pendingDamage = 0;
		resetLoadout();
	}

	/** Whether keys and mouse control the player now (not paused, not typing in the console). */
	public boolean controlsPlayer() {
		return !pause && !console;
	}
}
