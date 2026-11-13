package com.juego.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;

class DamageTest {

	private static final GunDefinition RIFLE = def(GunDefinition.Slot.PRIMARY, 30, 70);
	private static final GunDefinition PISTOL = def(GunDefinition.Slot.SECONDARY, 6, -1);
	private static final GunDefinition OTHER_RIFLE = def(GunDefinition.Slot.PRIMARY, 30, 70);

	private static GunDefinition def(GunDefinition.Slot slot, int magazineSize, int reserve) {
		GunDefinition def = new GunDefinition();
		def.slot = slot;
		def.magazineSize = magazineSize;
		def.reserveAmmo = reserve;
		return def;
	}

	private static PlayerComponent player() {
		return new PlayerComponent("ct", () -> new Gun[] { new Gun(RIFLE), new Gun(PISTOL) }, 1);
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
		p.guns[0].consumeRound();
		p.guns[0].reload();
		p.equip(new Gun(OTHER_RIFLE, 3, 0)); // picked up another rifle...
		p.removeGun(1); // ...and dropped the pistol
		p.getDamage(100);
		p.respawn();
		assertFalse(p.isDead());
		assertEquals(PlayerComponent.MAX_HEALTH, p.getHealth());
		assertEquals(RIFLE, p.guns[0].def, "back to the spawn loadout");
		assertEquals(PISTOL, p.guns[1].def);
		assertEquals(30, p.guns[0].getMagazine());
		assertEquals(70, p.guns[0].getReserve());
		assertEquals(p.guns[0], p.selectedGun());
		assertEquals(1, p.getDeaths(), "deaths are kept across respawns");
	}

	@Test
	void numbersWheelAndLastWeapon() {
		GunDefinition knife = def(GunDefinition.Slot.MELEE, 0, -1);
		PlayerComponent p = new PlayerComponent("ct",
				() -> new Gun[] { new Gun(RIFLE), new Gun(PISTOL), new Gun(knife) }, 1);
		assertEquals(RIFLE, p.selectedGun().def, "starts with the primary");
		assertTrue(p.lastWeapon());
		assertEquals(PISTOL, p.selectedGun().def, "Q right after spawning: the pistol");
		assertTrue(p.select(2));
		assertEquals(knife, p.selectedGun().def);
		assertFalse(p.select(2), "already in hand");
		assertTrue(p.lastWeapon());
		assertEquals(PISTOL, p.selectedGun().def, "Q: back to the one before");
		assertTrue(p.lastWeapon());
		assertEquals(knife, p.selectedGun().def, "and Q again goes back and forth");

		assertTrue(p.switchGun());
		assertEquals(RIFLE, p.selectedGun().def, "next after the knife: round to the primary");
		assertTrue(p.previousGun());
		assertEquals(knife, p.selectedGun().def, "previous before the primary: the knife");
		assertTrue(p.previousGun());
		assertEquals(PISTOL, p.selectedGun().def);

		p.removeGun(0); // dropped the rifle
		assertFalse(p.select(0), "nothing in that slot");
		assertTrue(p.switchGun());
		assertEquals(knife, p.selectedGun().def, "the wheel skips the empty slot");
		assertTrue(p.switchGun());
		assertEquals(PISTOL, p.selectedGun().def);
	}

	@Test
	void droppingAndPickingUpGuns() {
		PlayerComponent p = player();
		assertEquals(2, p.gunCount());
		Gun rifle = p.removeGun(0);
		assertEquals(RIFLE, rifle.def);
		assertEquals(1, p.selectedSlot, "dropping the gun in hand takes the other one");
		assertFalse(p.switchGun(), "nothing to switch to");

		Gun picked = new Gun(OTHER_RIFLE, 12, 40);
		assertNull(p.equip(picked), "the primary slot was empty");
		assertEquals(picked, p.selectedGun(), "a picked-up gun goes to the hand");
		Gun swapped = p.equip(new Gun(RIFLE));
		assertEquals(picked, swapped, "a gun for a full slot replaces the one there");
		assertTrue(p.switchGun());
		assertEquals(PISTOL, p.selectedGun().def);
	}

	@Test
	void pickedUpGunsKeepTheirAmmoWithinLimits() {
		Gun gun = new Gun(RIFLE, 12, 40);
		assertEquals(12, gun.getMagazine());
		assertEquals(40, gun.getReserve());
		Gun cheated = new Gun(RIFLE, 500, 9999);
		assertEquals(30, cheated.getMagazine());
		assertEquals(70, cheated.getReserve());
		assertTrue(new Gun(PISTOL, 2, 50).hasInfiniteReserve());
	}

	@Test
	void hitsAreQueuedForTheHud() {
		PlayerComponent p = player();
		p.hurt(10, new com.badlogic.gdx.math.Vector3(1, 2, 3));
		p.hurt(5, new com.badlogic.gdx.math.Vector3(4, 5, 6));
		assertEquals(15, p.pendingDamage);
		assertEquals(new com.badlogic.gdx.math.Vector3(4, 5, 6), p.lastDamageSource, "the latest hit's direction");
		p.hurt(1, null);
		assertNull(p.lastDamageSource, "unknown direction");
	}
}
