package com.juego.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GunTest {

	static GunDefinition definition(int magazineSize, int reserveAmmo) {
		GunDefinition def = new GunDefinition();
		def.id = "test";
		def.slot = GunDefinition.Slot.PRIMARY;
		def.magazineSize = magazineSize;
		def.reserveAmmo = reserveAmmo;
		return def;
	}

	@Test
	void startsWithFullMagazineAndReserve() {
		Gun gun = new Gun(definition(30, 70));
		assertEquals(30, gun.getMagazine());
		assertEquals(70, gun.getReserve());
	}

	@Test
	void cannotFireWhenEmpty() {
		Gun gun = new Gun(definition(2, 0));
		assertTrue(gun.consumeRound());
		assertTrue(gun.consumeRound());
		assertTrue(gun.isEmpty());
		assertFalse(gun.consumeRound());
		assertEquals(0, gun.getMagazine());
	}

	@Test
	void reloadTakesOnlyWhatTheMagazineNeeds() {
		Gun gun = new Gun(definition(30, 70));
		for (int i = 0; i < 12; i++)
			gun.consumeRound();
		assertTrue(gun.reload());
		assertEquals(30, gun.getMagazine());
		assertEquals(58, gun.getReserve());
	}

	@Test
	void reloadWithShortReserveLoadsWhatIsLeft() {
		Gun gun = new Gun(definition(30, 5));
		for (int i = 0; i < 30; i++)
			gun.consumeRound();
		assertTrue(gun.reload());
		assertEquals(5, gun.getMagazine());
		assertEquals(0, gun.getReserve());
		assertFalse(gun.canReload(), "no reserve left");
	}

	@Test
	void cannotReloadAFullMagazine() {
		Gun gun = new Gun(definition(30, 70));
		assertFalse(gun.reload());
		assertEquals(70, gun.getReserve());
	}

	@Test
	void infiniteReserveNeverRunsOut() {
		Gun gun = new Gun(definition(6, -1));
		assertTrue(gun.hasInfiniteReserve());
		for (int round = 0; round < 100; round++) {
			while (gun.consumeRound()) {
				// empty the magazine
			}
			assertTrue(gun.reload());
		}
		assertEquals(6, gun.getMagazine());
	}

	@Test
	void refillRestoresStartingAmmo() {
		Gun gun = new Gun(definition(30, 70));
		gun.consumeRound();
		gun.reload();
		gun.refill();
		assertEquals(30, gun.getMagazine());
		assertEquals(70, gun.getReserve());
	}

	@Test
	void damageDropsWithDistanceButNeverToNothing() {
		GunDefinition rifle = definition(30, 70);
		rifle.damage = 34;
		rifle.rangeModifier = 0.85f;
		assertEquals(34, rifle.damageAt(0), "point blank: full damage");
		assertEquals(Math.round(34 * 0.85f), rifle.damageAt(GunDefinition.FALLOFF_DISTANCE));
		assertEquals(Math.round(34 * 0.85f * 0.85f), rifle.damageAt(2 * GunDefinition.FALLOFF_DISTANCE));
		int previous = Integer.MAX_VALUE;
		for (float d = 0; d <= GunDefinition.MAX_RANGE; d += 25) {
			int damage = rifle.damageAt(d);
			assertTrue(damage <= previous, "never more damage farther away");
			assertTrue(damage >= 1, "always hurts, even at " + d);
			previous = damage;
		}
		rifle.rangeModifier = 1;
		assertEquals(34, rifle.damageAt(500), "1 = no falloff");
	}

	@Test
	void everyGunReachesAcrossTheLevel() {
		for (GunDefinition def : Armory.load(new com.badlogic.gdx.files.FileHandle(new java.io.File("assets", Armory.GUNS_FILE))).all()) {
			assertTrue(def.range >= 300, def.id + " must reach across the level");
			assertTrue(def.damageAt(150) > 0 && def.damageAt(150) < def.damage, def.id + " loses some damage at 150 units");
		}
	}
}
