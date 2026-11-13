package com.juego.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.juego.core.Core;
import com.juego.core.GameSession;
import com.juego.core.GameWorld;
import com.juego.utils.Platform;

public class GameScreen implements Screen {
	private final GameWorld gameWorld;

	public GameScreen(Core game, GameSession session) {
		gameWorld = new GameWorld(game, session);
		Gdx.input.setCursorCatched(!Platform.touch);
	}

	@Override
	public void render(float delta) {
		gameWorld.render(delta);
	}

	@Override
	public void resize(int width, int height) {
		gameWorld.resize(width, height);
	}

	@Override
	public void dispose() {
		gameWorld.dispose();
	}

	@Override
	public void show() {
	}

	/** Leaving the app mid-match (a call, the home button) opens the pause menu. */
	@Override
	public void pause() {
		gameWorld.appPaused();
	}

	@Override
	public void resume() {
	}

	@Override
	public void hide() {
	}
}
