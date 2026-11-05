package com.juego.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

class ArmoryTest {
	private static final File ASSETS = new File("assets");

	@TempDir
	Path tmp;

	private static Armory shipped() {
		return Armory.load(new FileHandle(new File(ASSETS, Armory.GUNS_FILE)));
	}

	@Test
	void shippedGunDataIsValidAndPointsToExistingFiles() {
		int count = 0;
		for (GunDefinition def : shipped().all()) {
			count++;
			assertTrue(new File(ASSETS, def.model).isFile(), def.id + ": missing model " + def.model);
			assertTrue(new File(ASSETS, def.sound).isFile(), def.id + ": missing sound " + def.sound);
		}
		assertTrue(count >= 2, "expected at least a primary and a secondary gun");
	}

	@Test
	void loadoutUsedByTheGameExists() {
		Armory armory = shipped();
		assertEquals(GunDefinition.Slot.PRIMARY, armory.get("m4a1").slot);
		assertEquals(GunDefinition.Slot.SECONDARY, armory.get("magnum").slot);
		assertTrue(armory.create("magnum").hasInfiniteReserve());
	}

	@Test
	void unknownIdFails() {
		Armory armory = shipped();
		assertThrows(GdxRuntimeException.class, () -> armory.get("bazooka"));
	}

	@Test
	void rejectsInvalidDefinitions() {
		String gun = "{id:x, slot:PRIMARY, damage:10, magazineSize:%d, range:10, fireDelayMs:100,"
				+ " model:a, sound:b, viewOffset:[0,0,0]}";
		assertThrows(GdxRuntimeException.class, () -> load("[" + String.format(gun, 0) + "]"), "zero magazine");
		assertThrows(GdxRuntimeException.class,
				() -> load("[" + String.format(gun, 5) + "," + String.format(gun, 5) + "]"), "duplicate id");
		assertThrows(GdxRuntimeException.class, () -> load("[{id:y, damage:1, magazineSize:1, range:1}]"),
				"missing slot");
	}

	private Armory load(String json) throws IOException {
		Path file = tmp.resolve("guns.json");
		Files.write(file, json.getBytes(StandardCharsets.UTF_8));
		return Armory.load(new FileHandle(file.toFile()));
	}
}
