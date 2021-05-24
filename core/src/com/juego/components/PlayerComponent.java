package com.juego.components;

import com.badlogic.ashley.core.Component;
import com.juego.guns.AK47;
import com.juego.guns.Gun;
import com.juego.guns.M4A1;
import com.juego.guns.Magnum;
import com.juego.guns.PrimaryGun;
import com.juego.guns.SecondaryGun;

public class PlayerComponent implements Component {
	
	private int health;
	public boolean pause;
	private int deaths = 0;
	public int kills = 0;
	public String team;
	public boolean scoreboard;
	public PrimaryGun primaryGun;
	public SecondaryGun secondaryGun;
	public Gun selectedGun;
	public int primaryAmmo = 70;
	public int numJugador;

	public PlayerComponent(String team, PrimaryGun primaryGun, int numJugador) {
	this.numJugador = numJugador;
	health = 100;
	pause = false;
	scoreboard = false;
	this.primaryGun = primaryGun;
	secondaryGun = new Magnum();
	selectedGun = this.primaryGun;
	this.team = team;
	}
	public int getHealth() {
		return health;
	}
	public void getDamage(int damage) {
		health -= damage;
		if (health <= 0) {
			health = 0;
			die(false);
		}
	}
	private void die(boolean headshot) {
		deaths++;
	}
	
	public int getDeaths() {
		return deaths;
	}
	
	public void getDamageHead(int damage) {
		health -= damage * 3;
		if (health <= 0) {
			health = 0;
			die(true);
		}
	}
}