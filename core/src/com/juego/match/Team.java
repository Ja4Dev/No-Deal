package com.juego.match;

import com.badlogic.gdx.graphics.Color;
import com.juego.utils.Text;

/** A side in a team match. NONE: no team (deathmatch), or "whichever needs players" when asking for one. */
public enum Team {
	NONE(Color.WHITE, "ffffff"), RED(new Color(1f, 0.38f, 0.32f, 1), "ff6152"),
	BLUE(new Color(0.4f, 0.62f, 1f, 1), "669eff");

	/** For the HUD and the scoreboard: light enough to read on dark backgrounds. */
	public final Color color;
	/** The same, as a libGDX font markup color: [#ff6152]. */
	public final String hex;

	Team(Color color, String hex) {
		this.color = color;
		this.hex = hex;
	}

	public boolean isTeam() {
		return this != NONE;
	}

	public Team other() {
		return this == RED ? BLUE : this == BLUE ? RED : NONE;
	}

	/** "Rojo", "Azul". */
	public String label() {
		return Text.get("team." + name());
	}

	/** "rojo", "red", "azul", "blue", "auto"... or null. NONE means "auto" (whichever needs players). */
	public static Team parse(String text) {
		switch (text.trim().toLowerCase()) {
		case "rojo":
		case "red":
		case "r":
			return RED;
		case "azul":
		case "blue":
		case "b":
		case "a":
			return BLUE;
		case "auto":
		case "":
			return NONE;
		default:
			return null;
		}
	}

	/** The team with this ordinal (from the network), NONE if unknown. */
	public static Team of(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
	}
}
