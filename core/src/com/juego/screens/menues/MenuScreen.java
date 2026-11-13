package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.UI.Menu;
import com.juego.core.Core;

/** A full-screen menu: takes the input, and acts and draws its stage every frame. */
public abstract class MenuScreen extends Menu implements Screen {
	protected final Core game;
	private Button backButton;

	protected MenuScreen(Core game) {
		this.game = game;
		Gdx.input.setInputProcessor(stage);
	}

	@Override
	public void render(float delta) {
		stage.act(delta);
		stage.draw();
	}

	/**
	 * Android's back button: does what the screen's "Volver" button does, if
	 * it has one (see {@link #setBackButton}).
	 */
	public void back() {
		if (backButton == null)
			return;
		for (EventListener listener : backButton.getListeners())
			if (listener instanceof ClickListener)
				((ClickListener) listener).clicked(null, 0, 0);
	}

	/** The button the back key presses. */
	protected void setBackButton(Button button) {
		backButton = button;
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
