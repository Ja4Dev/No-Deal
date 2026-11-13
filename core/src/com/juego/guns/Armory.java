package com.juego.guns;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** Registry of every gun definition in the game, loaded from JSON. */
public class Armory {
	public static final String GUNS_FILE = "data/guns.json";

	private final ObjectMap<String, GunDefinition> definitions = new ObjectMap<>();

	@SuppressWarnings("unchecked")
	public static Armory load(FileHandle file) {
		Array<GunDefinition> list = new Json().fromJson(Array.class, GunDefinition.class, file);
		Armory armory = new Armory();
		for (GunDefinition def : list) {
			validate(def, file);
			if (armory.definitions.containsKey(def.id))
				throw new GdxRuntimeException(file + ": duplicate gun id '" + def.id + "'");
			armory.definitions.put(def.id, def);
		}
		return armory;
	}

	private static void validate(GunDefinition def, FileHandle file) {
		String where = file + ": gun '" + def.id + "' ";
		if (def.id == null || def.id.isEmpty())
			throw new GdxRuntimeException(file + ": a gun is missing its id");
		if (def.slot == null)
			throw new GdxRuntimeException(where + "has no slot (PRIMARY, SECONDARY or MELEE)");
		if (def.isMelee()) {
			if (def.altDamage <= 0 || def.backDamage <= 0 || def.altBackDamage <= 0 || def.altRange <= 0
					|| def.altFireDelayMs <= 0)
				throw new GdxRuntimeException(where + "is melee: it needs altDamage, backDamage, altBackDamage, "
						+ "altRange and altFireDelayMs");
		} else if (def.magazineSize <= 0)
			throw new GdxRuntimeException(where + "needs a positive magazineSize");
		if (def.damage <= 0 || def.range <= 0 || def.fireDelayMs < 0)
			throw new GdxRuntimeException(where + "has invalid damage, range or fireDelayMs");
		if (def.rangeModifier <= 0 || def.rangeModifier > 1)
			throw new GdxRuntimeException(where + "needs a rangeModifier between 0 (excluded) and 1");
		if (def.model == null || def.sound == null)
			throw new GdxRuntimeException(where + "needs a model and a sound");
		if (def.viewOffset == null || def.viewOffset.length != 3)
			throw new GdxRuntimeException(where + "needs a 3-component viewOffset");
		if (def.spray != null)
			for (float[] step : def.spray)
				if (step == null || step.length != 2)
					throw new GdxRuntimeException(where + "has a spray step that isn't {up, right}");
	}

	public GunDefinition get(String id) {
		GunDefinition def = definitions.get(id);
		if (def == null)
			throw new GdxRuntimeException("Unknown gun id '" + id + "'");
		return def;
	}

	/** The name players read for a gun id ("Cuchillo" for "knife"), or the text itself if it isn't a gun id. */
	public String displayName(String id) {
		GunDefinition def = definitions.get(id);
		return def == null ? id : def.displayName();
	}

	/** Creates a new gun with a full magazine. */
	public Gun create(String id) {
		return new Gun(get(id));
	}

	public Iterable<GunDefinition> all() {
		return definitions.values();
	}
}
