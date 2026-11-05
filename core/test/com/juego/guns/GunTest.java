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
}
