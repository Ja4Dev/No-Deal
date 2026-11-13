package com.juego.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import java.io.IOException;
import java.util.function.LongSupplier;

import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.juego.net.Bluetooth;
import com.juego.net.GameServer;
import com.juego.screens.menues.MainMenuScreen;
import com.juego.screens.menues.MenuScreen;
import com.juego.utils.Config;
import com.juego.utils.MusicPlayer;
import com.juego.utils.Platform;

public class Core extends ApplicationAdapter {
	public static int VIRTUAL_WIDTH = 1280;
	public static int VIRTUAL_HEIGHT = 720;
	private Screen screen;
	private Screen pendingScreen;
	public MusicPlayer musicPlayer;
	/** Server running inside this game while hosting a match ("Crear partida"); null otherwise. */
	private GameServer hostedServer;
	/** Multiplayer over Bluetooth, on platforms that have it (Android); null elsewhere. */
	public final Bluetooth bluetooth;

	public Core() {
		this(null);
	}

	public Core(Bluetooth bluetooth) {
		this.bluetooth = bluetooth;
	}

	/** Whether the menus offer playing over Bluetooth. */
	public boolean hasBluetooth() {
		return bluetooth != null && bluetooth.isSupported();
	}

	@Override
	public void create() {
		Bullet.init();
		Platform.detect();
		Config.load();
		if (Config.fullscreen && Platform.desktop)
			Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
		Assets.init();
		Gdx.input.setCatchKey(Input.Keys.BACK, true);
		musicPlayer = new MusicPlayer();
		setScreen(new MainMenuScreen(this));
	}

	@Override
	public void render() {
		// The hosted server runs on the game thread: Bullet's JNI wrappers are not
		// thread-safe, and the server and the match each have a Bullet world.
		if (hostedServer != null)
			hostedServer.update();
		applyPendingScreen();
		if (screen instanceof MenuScreen && Gdx.input.isKeyJustPressed(Input.Keys.BACK))
			((MenuScreen) screen).back(); // in a match, back opens the pause menu instead (Controls)
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		if (screen != null)
			screen.render(Gdx.graphics.getDeltaTime());
		musicPlayer.update();
	}

	/** The app went to the background (Android, iOS) or the window was minimized. */
	@Override
	public void pause() {
		if (screen != null)
			screen.pause();
	}

	@Override
	public void resume() {
		if (screen != null)
			screen.resume();
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

	/**
	 * Starts a server inside this game: a private one for singleplayer, or
	 * one others can join ("Crear partida"). Throws if the port is in use.
	 */
	public void startHostedServer(GameServer.Config config, LongSupplier clock) throws IOException {
		stopHostedServer();
		hostedServer = new GameServer(config, new InternalFileHandleResolver(), Assets.armory, clock);
	}

	/** The server running inside this game, or null. Only use it on the game thread. */
	public GameServer getHostedServer() {
		return hostedServer;
	}

	public void stopHostedServer() {
		if (hostedServer != null) {
			hostedServer.dispose();
			hostedServer = null;
		}
	}

	public int getHostedPort() {
		return hostedServer != null ? hostedServer.getPort() : 0;
	}

	/** Ends the current match and returns to the main menu, optionally explaining why. */
	public void leaveMatch(String message) {
		musicPlayer.paused = false;
		setScreen(new MainMenuScreen(this, message));
	}

	@Override
	public void dispose() {
		if (screen != null)
			screen.dispose();
		if (pendingScreen != null)
			pendingScreen.dispose();
		stopHostedServer();
		musicPlayer.dispose();
		Assets.dispose();
	}

}
