package com.juego.bots;

import java.text.Normalizer;
import com.juego.utils.Text;

/**
 * How well a bot plays. Every level is a set of human-like limits: how long it
 * takes to react, how fast and how precisely it turns, how well it controls
 * the spray, whether it stops to shoot or strafes, and how far it sees.
 */
public enum BotDifficulty {
	// reaction ms, turn deg/s, aim error deg, spray control, headshot chance, burst, fov deg, sight range, moves while shooting
	FACIL(750, 140, 7f, 0.0f, 0.05f, 3, 100, 45, Combat.RUN_AND_GUN),
	MEDIO(480, 260, 4f, 0.45f, 0.2f, 7, 120, 55, Combat.STOP_AND_SHOOT),
	DIFICIL(290, 420, 2.6f, 0.75f, 0.45f, 12, 150, 70, Combat.STRAFE),
	MUY_DIFICIL(170, 650, 1.2f, 0.95f, 0.75f, 30, 180, 90, Combat.STRAFE);

	/** What the bot does with its legs while it fights. */
	public enum Combat {
		/** Keeps running at you while shooting: very inaccurate. */
		RUN_AND_GUN,
		/** Stops before shooting, so its shots are accurate. */
		STOP_AND_SHOOT,
		/** Walks left and right while shooting (walking doesn't hurt accuracy). */
		STRAFE
	}

	/** Time from first seeing an enemy to the first shot. */
	public final long reactionMs;
	/** Fastest the aim can turn. */
	public final float turnSpeed;
	/** How far off the aim wanders around the target, in degrees. */
	public final float aimError;
	/** 0 = lets the spray climb, 1 = pulls it down perfectly. */
	public final float sprayControl;
	/** Chance of aiming at the head instead of the chest for an engagement. */
	public final float headshotChance;
	/** Longest burst before letting the spray recover. */
	public final int maxBurst;
	/** Field of view: enemies outside it go unnoticed unless they make noise. */
	public final float fieldOfView;
	/** Farthest an enemy is noticed. */
	public final float sightRange;
	public final Combat combat;

	BotDifficulty(long reactionMs, float turnSpeed, float aimError, float sprayControl,
			float headshotChance, int maxBurst, float fieldOfView, float sightRange, Combat combat) {
		this.reactionMs = reactionMs;
		this.turnSpeed = turnSpeed;
		this.aimError = aimError;
		this.sprayControl = sprayControl;
		this.headshotChance = headshotChance;
		this.maxBurst = maxBurst;
		this.fieldOfView = fieldOfView;
		this.sightRange = sightRange;
		this.combat = combat;
	}

	/** "Facil" / "Easy", in the current language. */
	public String label() {
		return Text.get("difficulty." + name());
	}

	/** The name used in commands: facil, medio, dificil, muy_dificil. */
	public String id() {
		return name().toLowerCase();
	}

	/**
	 * Reads a difficulty as typed in a command: "facil", "Fácil", "muy dificil",
	 * "muy_dificil", "muydificil", or the English names
	 * (easy, normal, hard, expert).
	 *
	 * @return null if it isn't one
	 */
	public static BotDifficulty parse(String text) {
		if (text == null)
			return null;
		String s = Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.replaceAll("[\\s_-]+", "");
		switch (s) {
		case "facil":
		case "easy":
			return FACIL;
		case "medio":
		case "normal":
		case "medium":
			return MEDIO;
		case "dificil":
		case "hard":
			return DIFICIL;
		case "muydificil":
		case "experto":
		case "expert":
			return MUY_DIFICIL;
		default:
			return null;
		}
	}

	/** "facil, medio, dificil, muy_dificil" (or the English names), for help texts. All are understood. */
	public static String choices() {
		return Text.get("difficulty.choices");
	}
}
