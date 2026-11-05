package com.juego.net;

import com.badlogic.gdx.Gdx;

/** Logs through Gdx.app when there is one, or to stdout (unit tests, server startup). */
public final class NetLog {
	private NetLog() {
	}

	public static void info(String tag, String message) {
		if (Gdx.app != null)
			Gdx.app.log(tag, message);
		else
			System.out.println("[" + tag + "] " + message);
	}

	public static void error(String tag, String message) {
		if (Gdx.app != null)
			Gdx.app.error(tag, message);
		else
			System.err.println("[" + tag + "] " + message);
	}
}
