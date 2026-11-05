package com.juego.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.bullet.collision.ContactListener;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.juego.components.EnemyComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.StatusComponent;

/** Enemies are kamikazes: touching a player hurts the player and destroys the enemy. */
public class MyContactListener extends ContactListener {
	public static final int ENEMY_CONTACT_DAMAGE = 10;

	@Override
	public void onContactStarted(btCollisionObject colObj0, btCollisionObject colObj1) {
		if (!(colObj0.userData instanceof Entity) || !(colObj1.userData instanceof Entity))
			return;
		Entity entity0 = (Entity) colObj0.userData;
		Entity entity1 = (Entity) colObj1.userData;
		if (!handleEnemyHitsPlayer(entity0, entity1))
			handleEnemyHitsPlayer(entity1, entity0);
	}

	private boolean handleEnemyHitsPlayer(Entity enemy, Entity player) {
		StatusComponent enemyStatus = enemy.getComponent(StatusComponent.class);
		PlayerComponent playerComponent = player.getComponent(PlayerComponent.class);
		if (enemy.getComponent(EnemyComponent.class) == null || enemyStatus == null || playerComponent == null)
			return false;
		if (enemyStatus.alive) {
			playerComponent.getDamage(ENEMY_CONTACT_DAMAGE);
			enemyStatus.alive = false;
		}
		return true;
	}
}
