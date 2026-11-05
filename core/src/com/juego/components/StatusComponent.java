package com.juego.components;

import com.badlogic.ashley.core.Component;

public class StatusComponent implements Component {
	public static final int HEADSHOT_MULTIPLIER = 3;

	public boolean alive = true;
	private int health = 50;

	/** @return true if this hit killed the entity. */
	public boolean getDamage(int damage) {
		return applyDamage(damage);
	}

	/** @return true if this hit killed the entity. */
	public boolean getDamageHead(int damage) {
		return applyDamage(damage * HEADSHOT_MULTIPLIER);
	}

	private boolean applyDamage(int damage) {
		if (!alive)
			return false;
		health -= damage;
		if (health <= 0) {
			health = 0;
			alive = false;
			return true;
		}
		return false;
	}
}
