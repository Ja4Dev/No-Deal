package com.juego.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;

class DamageTest {

	private static Gun gun(int magazineSize, int reserve) {
		GunDefinition def = new GunDefinition();
		def.magazineSize = magazineSize;
		def.reserveAmmo = reserve;
		return new Gun(def);
	}

	private static PlayerComponent player() {
		return new PlayerComponent("ct", gun(30, 70), gun(6, -1), 1);
	}

	@Test
	void headshotsDealTripleDamage() {
		PlayerComponent body = player(), head = player();
		body.getDamage(20);
		head.getDamageHead(20);
		assertEquals(80, body.getHealth());
		assertEquals(40, head.getHealth());
	}

	@Test
	void playerDiesOnceEvenIfHitAgain() {
		PlayerComponent p = player();
		p.getDamage(150);
		p.getDamage(10);
		assertTrue(p.isDead());
		assertEquals(0, p.getHealth());
		assertEquals(1, p.getDeaths());
	}

	@Test
	void respawnRestoresHealthAmmoAndLoadout() {
		PlayerComponent p = player();
		p.selectedGun = p.secondaryGun;
		p.primaryGun.consumeRound();
		p.primaryGun.reload();
		p.getDamage(100);
		p.respawn();
		assertFalse(p.isDead());
		assertEquals(PlayerComponent.MAX_HEALTH, p.getHealth());
		assertEquals(30, p.primaryGun.getMagazine());
		assertEquals(70, p.primaryGun.getReserve());
		assertEquals(p.primaryGun, p.selectedGun);
		assertEquals(1, p.getDeaths(), "deaths are kept across respawns");
	}

	@Test
	void enemyReportsTheKillingHitOnly() {
		StatusComponent enemy = new StatusComponent();
		assertFalse(enemy.getDamage(34), "50 hp enemy survives a body shot");
		assertTrue(enemy.getDamage(34), "second hit kills");
		assertFalse(enemy.getDamage(34), "already dead: no second kill");
		assertFalse(enemy.alive);
	}
}
