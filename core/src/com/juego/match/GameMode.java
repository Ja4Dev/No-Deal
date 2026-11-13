package com.juego.match;

import com.juego.utils.Text;

/** What kind of match the server runs. */
public enum GameMode {
	/** Everyone against everyone: the player with the most kills wins. */
	DEATHMATCH("dm"),
	/**
	 * Two teams, Red and Blue: every kill of an enemy scores for the team, and
	 * the team that gets to the kill limit (or has more when time runs out)
	 * wins. Teammates can't hurt each other unless mp_friendlyfire is on.
	 */
	TEAM_DEATHMATCH("tdm");

	/** As typed on the dedicated server's command line: --mode tdm. */
	public final String id;

	GameMode(String id) {
		this.id = id;
	}

	public boolean hasTeams() {
		return this == TEAM_DEATHMATCH;
	}

	/** "Todos contra todos", "Por equipos". */
	public String label() {
		return Text.get("mode." + name());
	}

	/** "dm", "tdm", "deathmatch", "team"... or null. */
	public static GameMode parse(String text) {
		if (text == null)
			return null;
		String t = text.trim().toLowerCase();
		for (GameMode mode : values())
			if (mode.id.equals(t) || mode.name().toLowerCase().equals(t) || mode.name().toLowerCase().replace("_", "").equals(t))
				return mode;
		if (t.equals("team") || t.equals("teams") || t.equals("equipos"))
			return TEAM_DEATHMATCH;
		if (t.equals("ffa") || t.equals("todos"))
			return DEATHMATCH;
		return null;
	}

	/** The mode with this ordinal (from the network), deathmatch if unknown. */
	public static GameMode of(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : DEATHMATCH;
	}
}
