package com.juego.core;

import com.badlogic.gdx.utils.Disposable;
import com.juego.net.GameClient;

/** How a match is played: alone against bots, or online through a GameClient. */
public class GameSession implements Disposable {
	/** Null in singleplayer. */
	public final GameClient client;
	private final boolean hosting;

	private GameSession(GameClient client, boolean hosting) {
		this.client = client;
		this.hosting = hosting;
	}

	public static GameSession singleplayer() {
		return new GameSession(null, false);
	}

	/** @param hosting whether this machine also runs the server ("Crear partida"). */
	public static GameSession multiplayer(GameClient client, boolean hosting) {
		return new GameSession(client, hosting);
	}

	public boolean isMultiplayer() {
		return client != null;
	}

	public boolean isHosting() {
		return hosting;
	}

	@Override
	public void dispose() {
		if (client != null)
			client.dispose();
	}
}
