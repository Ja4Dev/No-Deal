package com.juego.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.juego.screens.menues.MainMenuScreen;
import com.juego.utils.MusicPlayer;

public class Core extends ApplicationAdapter {
	public static int VIRTUAL_WIDTH = 1280;
	public static int VIRTUAL_HEIGHT = 720;
	private Screen screen;
	private Screen pendingScreen;
	public MusicPlayer musicPlayer;

	@Override
	public void create() {
		new Assets();
		Gdx.input.setCatchKey(Input.Keys.BACK, true);
		musicPlayer = new MusicPlayer();
		setScreen(new MainMenuScreen(this));
	}

	@Override
	public void render() {
		applyPendingScreen();
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		if (screen != null)
			screen.render(Gdx.graphics.getDeltaTime());
		musicPlayer.update();
	}

	@Override
	public void resize(int width, int height) {
		if (screen != null)
			screen.resize(width, height);
	}

	/**
	 * Schedules a screen change for the start of the next frame.
	 * <p>
	 * Screens are usually changed from a button listener, which runs while the
	 * current screen (and, in game, the whole ECS engine and physics world) is
	 * still being updated. Disposing it right there would free native Bullet
	 * objects that are still in use, so the swap is deferred.
	 */
	public void setScreen(Screen screen) {
		if (pendingScreen != null && pendingScreen != screen)
			pendingScreen.dispose();
		pendingScreen = screen;
	}

	private void applyPendingScreen() {
		if (pendingScreen == null)
			return;
		if (screen != null) {
			screen.hide();
			screen.dispose();
		}
		screen = pendingScreen;
		pendingScreen = null;
		screen.show();
		screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
	}

	@Override
	public void dispose() {
		if (screen != null)
			screen.dispose();
		if (pendingScreen != null)
			pendingScreen.dispose();
		musicPlayer.dispose();
		Assets.dispose();
	}

}
