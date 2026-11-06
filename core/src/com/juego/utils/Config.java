package com.juego.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.juego.bots.BotDifficulty;
import com.juego.net.GameServer;
import com.juego.net.Messages;

/**
 * Player settings. Saved with libGDX Preferences (on desktop, an XML file in
 * ~/.prefs/NoDeal) and loaded on startup. Key bindings live in {@link Controls}.
 */
public final class Config {
	private static final String PREFERENCES = "NoDeal";

	public static boolean fullscreen = false;
	public static String playerName = "Jugador";
	public static float fxVolume = 100;
	public static float musicVolume = 50;
	/** Same scale as CS: degrees per mouse count = sensitivity * 0.022. */
	public static float sensitivity = 2.5f;
	public static final CrosshairSettings crosshair = new CrosshairSettings();
	public static final float MIN_SENSITIVITY = 0.5f, MAX_SENSITIVITY = 10f;
	/** Bots chosen last time for "Un jugador" and for "Crear partida", and their difficulty. */
	public static int singleplayerBots = 5, hostBots = 0;
	public static BotDifficulty botDifficulty = BotDifficulty.MEDIO;

	private Config() {
	}

	public static void load() {
		Preferences prefs = Gdx.app.getPreferences(PREFERENCES);
		fullscreen = prefs.getBoolean("fullscreen", fullscreen);
		playerName = Messages.sanitizeName(prefs.getString("playerName", playerName));
		fxVolume = clamp(prefs.getFloat("fxVolume", fxVolume), 0, 100);
		musicVolume = clamp(prefs.getFloat("musicVolume", musicVolume), 0, 100);
		sensitivity = clamp(prefs.getFloat("sensitivity", sensitivity), MIN_SENSITIVITY, MAX_SENSITIVITY);
		singleplayerBots = Math.max(1, Math.min(GameServer.MAX_BOTS, prefs.getInteger("singleplayerBots", singleplayerBots)));
		hostBots = Math.max(0, Math.min(GameServer.MAX_BOTS, prefs.getInteger("hostBots", hostBots)));
		BotDifficulty difficulty = BotDifficulty.parse(prefs.getString("botDifficulty", ""));
		if (difficulty != null)
			botDifficulty = difficulty;
		crosshair.load(prefs);
		Controls.deserialize(prefs.getString("controls", ""));
	}

	public static void save() {
		Preferences prefs = Gdx.app.getPreferences(PREFERENCES);
		prefs.putBoolean("fullscreen", fullscreen);
		prefs.putString("playerName", playerName);
		prefs.putFloat("fxVolume", fxVolume);
		prefs.putFloat("musicVolume", musicVolume);
		prefs.putFloat("sensitivity", sensitivity);
		prefs.putInteger("singleplayerBots", singleplayerBots);
		prefs.putInteger("hostBots", hostBots);
		prefs.putString("botDifficulty", botDifficulty.id());
		crosshair.save(prefs);
		prefs.putString("controls", Controls.serialize());
		prefs.flush();
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
