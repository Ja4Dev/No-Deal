package com.juego.components;

import com.badlogic.ashley.core.Component;
import com.juego.guns.Gun;
import com.juego.guns.Magnum;
import com.juego.guns.PrimaryGun;
import com.juego.guns.SecondaryGun;

public class PlayerComponent implements Component {

	public static final int MAX_HEALTH = 100;
	public static final int START_PRIMARY_AMMO = 70;
	public static final int HEADSHOT_MULTIPLIER = 3;

	private int health = MAX_HEALTH;
	private boolean dead = false;
	public boolean pause = false;
	private int deaths = 0;
	public int kills = 0;
	public String team;
	public boolean scoreboard = false;
	public PrimaryGun primaryGun;
	public SecondaryGun secondaryGun;
	public Gun selectedGun;
	public int primaryAmmo = START_PRIMARY_AMMO;
	public int numJugador;

	public PlayerComponent(String team, PrimaryGun primaryGun, int numJugador) {
		this.numJugador = numJugador;
		this.primaryGun = primaryGun;
		this.secondaryGun = new Magnum();
		this.selectedGun = this.primaryGun;
		this.team = team;
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

	/** Restores health and ammo after a death. Repositioning is handled by the PlayerSystem. */
	public void respawn() {
		health = MAX_HEALTH;
		dead = false;
		primaryAmmo = START_PRIMARY_AMMO;
		primaryGun.magazine = primaryGun.magazineSize;
		secondaryGun.magazine = secondaryGun.magazineSize;
	}
}
