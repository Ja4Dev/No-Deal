package com.juego.core;

import com.badlogic.gdx.utils.Disposable;
import com.juego.net.GameClient;

/**
 * How a match is played. Every match goes through a server and a
 * {@link GameClient}: singleplayer runs a private server with bots inside the
 * game, "Crear partida" runs one others can join, and "Unirse" connects to
 * someone else's.
 */
public class GameSession implements Disposable {
	public enum Kind {
		/** A private server inside the game, with bots; the pause menu stops it. */
		SINGLEPLAYER,
		/** A server inside the game that others can join. */
		HOST,
		/** Someone else's server. */
		JOIN
	}

	public final GameClient client;
	public final GameClock clock;
	public final Kind kind;

	public GameSession(GameClient client, GameClock clock, Kind kind) {
		this.client = client;
		this.clock = clock;
		this.kind = kind;
	}

	/** Whether the server runs inside this game (and stops when the match ends). */
	public boolean isHosting() {
		return kind != Kind.JOIN;
	}

	public boolean isSingleplayer() {
		return kind == Kind.SINGLEPLAYER;
	}

	@Override
	public void dispose() {
		client.dispose();
	}
}
