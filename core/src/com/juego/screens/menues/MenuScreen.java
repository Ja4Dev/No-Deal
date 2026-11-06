package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.juego.UI.Menu;
import com.juego.core.Core;

/** A full-screen menu: takes the input, and acts and draws its stage every frame. */
public abstract class MenuScreen extends Menu implements Screen {
	protected final Core game;

	protected MenuScreen(Core game) {
		this.game = game;
		Gdx.input.setInputProcessor(stage);
	}

	@Override
	public void render(float delta) {
		stage.act(delta);
		stage.draw();
	}

	/** True while the menu wants every key for itself (e.g. waiting for a key to bind). */
	public boolean capturesKeys() {
		return false;
	}

	@Override
	public void show() {
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void hide() {
	}
}
