package com.juego.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.juego.bots.BotDifficulty;
import com.juego.net.GameServer;
import com.juego.net.Messages;
import com.juego.net.Protocol;

/**
 * Player settings. Saved with libGDX Preferences (on desktop, an XML file in
 * ~/.prefs/NoDeal) and loaded on startup. Key bindings live in {@link Controls}.
 */
public final class Config {
	private static final String PREFERENCES = "NoDeal";

	public static boolean fullscreen = false;
	public static String playerName = "";
	/** The language the player chose in Opciones; null until then (the system's is used, see Text). */
	public static Text.Language language;
	public static float fxVolume = 100;
	public static float musicVolume = 50;
	/** Same scale as CS: degrees per mouse count = sensitivity * 0.022. */
	public static float sensitivity = 2.5f;
	public static final CrosshairSettings crosshair = new CrosshairSettings();
	public static final float MIN_SENSITIVITY = 0.5f, MAX_SENSITIVITY = 10f;
	/** How fast dragging a finger turns the view on a touch screen (1 = a drag across the screen turns 160 degrees). */
	public static float touchSensitivity = 1f;
	public static final float MIN_TOUCH_SENSITIVITY = 0.3f, MAX_TOUCH_SENSITIVITY = 3f;
	/** Bots chosen last time for "Un jugador" and for "Crear partida", and their difficulty. */
	public static int singleplayerBots = 5, hostBots = 0;
	public static BotDifficulty botDifficulty = BotDifficulty.MEDIO;
	/** Match rules chosen last time for "Un jugador" and "Crear partida": minutes, and kills to win (0 = none). */
	public static int timeLimitMinutes = Protocol.DEFAULT_TIME_LIMIT_MINUTES, fragLimit = Protocol.DEFAULT_FRAG_LIMIT;
	public static final int MAX_TIME_LIMIT_MINUTES = 30, MAX_FRAG_LIMIT = 50;
	/** Console: cl_showfps (FPS and ping on screen) and cl_showspeed (speedometer, for bunnyhopping). */
	public static boolean showFps, showSpeed;
	/** Console: fps_max. 0 follows the screen (vertical sync); otherwise the most frames per second. */
	public static int fpsMax;
	public static final int MAX_FPS_MAX = 1000;

	private Config() {
	}

	public static void load() {
		Preferences prefs = Gdx.app.getPreferences(PREFERENCES);
		// First, so every text below (and the default name) is in the right language.
		language = Text.Language.parse(prefs.getString("language", ""));
		Text.setLanguage(language != null ? language : Text.system());
		fullscreen = prefs.getBoolean("fullscreen", fullscreen);
		playerName = Messages.sanitizeName(prefs.getString("playerName", playerName));
		fxVolume = clamp(prefs.getFloat("fxVolume", fxVolume), 0, 100);
		musicVolume = clamp(prefs.getFloat("musicVolume", musicVolume), 0, 100);
		sensitivity = clamp(prefs.getFloat("sensitivity", sensitivity), MIN_SENSITIVITY, MAX_SENSITIVITY);
		touchSensitivity = clamp(prefs.getFloat("touchSensitivity", touchSensitivity), MIN_TOUCH_SENSITIVITY,
				MAX_TOUCH_SENSITIVITY);
		singleplayerBots = Math.max(1, Math.min(GameServer.MAX_BOTS, prefs.getInteger("singleplayerBots", singleplayerBots)));
		hostBots = Math.max(0, Math.min(GameServer.MAX_BOTS, prefs.getInteger("hostBots", hostBots)));
		BotDifficulty difficulty = BotDifficulty.parse(prefs.getString("botDifficulty", ""));
		if (difficulty != null)
			botDifficulty = difficulty;
		timeLimitMinutes = Math.max(1, Math.min(MAX_TIME_LIMIT_MINUTES, prefs.getInteger("timeLimit", timeLimitMinutes)));
		fragLimit = Math.max(0, Math.min(MAX_FRAG_LIMIT, prefs.getInteger("fragLimit", fragLimit)));
		crosshair.load(prefs);
		Controls.deserialize(prefs.getString("controls", ""));
		showFps = prefs.getBoolean("showFps", showFps);
		showSpeed = prefs.getBoolean("showSpeed", showSpeed);
		fpsMax = Math.max(0, Math.min(MAX_FPS_MAX, prefs.getInteger("fpsMax", fpsMax)));
		applyFpsMax();
	}

	/**
	 * fps_max: 0 draws once per screen refresh (vertical sync, the default);
	 * a number caps the frames per second at it without vertical sync, which
	 * is what CS players use to play at 100+ FPS.
	 */
	public static void applyFpsMax() {
		if (Gdx.graphics == null)
			return;
		if (fpsMax > 0) {
			Gdx.graphics.setVSync(false);
			Gdx.graphics.setForegroundFPS(fpsMax);
		} else {
			Gdx.graphics.setVSync(true);
			Gdx.graphics.setForegroundFPS(Gdx.graphics.getDisplayMode().refreshRate + 1);
		}
	}

	public static void save() {
		Preferences prefs = Gdx.app.getPreferences(PREFERENCES);
		prefs.putBoolean("fullscreen", fullscreen);
		prefs.putString("playerName", playerName);
		if (language != null)
			prefs.putString("language", language.id);
		prefs.putFloat("fxVolume", fxVolume);
		prefs.putFloat("musicVolume", musicVolume);
		prefs.putFloat("sensitivity", sensitivity);
		prefs.putFloat("touchSensitivity", touchSensitivity);
		prefs.putInteger("singleplayerBots", singleplayerBots);
		prefs.putInteger("hostBots", hostBots);
		prefs.putString("botDifficulty", botDifficulty.id());
		prefs.putInteger("timeLimit", timeLimitMinutes);
		prefs.putInteger("fragLimit", fragLimit);
		crosshair.save(prefs);
		prefs.putString("controls", Controls.serialize());
		prefs.putBoolean("showFps", showFps);
		prefs.putBoolean("showSpeed", showSpeed);
		prefs.putInteger("fpsMax", fpsMax);
		prefs.flush();
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
