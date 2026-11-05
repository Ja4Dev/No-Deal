package com.juego.components;

import com.badlogic.ashley.core.Component;
import com.juego.guns.Gun;

/**
 * State of a player. It is also how the player systems talk to each other:
 * PlayerSystem writes the movement state, WeaponSystem reads it for bullet
 * spread and writes back recoil, and HudSystem owns the pause/scoreboard flags.
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

	public final Gun primaryGun;
	public final Gun secondaryGun;
	public Gun selectedGun;

	// UI state (HudSystem)
	public boolean pause = false;
	public boolean scoreboard = false;

	// Look and movement state (PlayerSystem)
	public float yaw, pitch;
	public boolean moving, running;
	/** Pitch kick (degrees) to apply on the next camera update. Written by the WeaponSystem. */
	public float pendingRecoil;

	public PlayerComponent(String team, Gun primaryGun, Gun secondaryGun, int playerNumber) {
		this.team = team;
		this.primaryGun = primaryGun;
		this.secondaryGun = secondaryGun;
		this.selectedGun = primaryGun;
		this.playerNumber = playerNumber;
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

	/** Restores health and ammo after a death. Repositioning is handled by the PlayerSystem. */
	public void respawn() {
		health = MAX_HEALTH;
		dead = false;
		pendingRecoil = 0;
		primaryGun.refill();
		secondaryGun.refill();
		selectedGun = primaryGun;
	}
}
