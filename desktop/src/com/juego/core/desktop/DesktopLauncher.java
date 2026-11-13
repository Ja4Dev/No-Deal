package com.juego.core.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.juego.core.Core;

public class DesktopLauncher {
	public static void main(String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("No Deal");
		config.useVsync(true);
		config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
		// Antialiasing has to be chosen before the window exists.
		int samples = savedMsaa();
		if (samples > 0)
			config.setBackBufferConfig(8, 8, 8, 8, 16, 0, samples);
		// The other saved settings (window mode, resolution) can only be applied once the app exists: Core does.
		config.setWindowedMode(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		new Lwjgl3Application(new Core(), config);
	}

	/**
	 * The antialiasing samples chosen in Opciones > Video. libGDX keeps the
	 * settings in ~/.prefs/NoDeal, which can't be read through it yet.
	 */
	private static int savedMsaa() {
		File prefs = new File(System.getProperty("user.home"), ".prefs/NoDeal");
		if (!prefs.isFile())
			return 0;
		Properties saved = new Properties();
		try (InputStream in = new FileInputStream(prefs)) {
			saved.loadFromXML(in);
			int samples = Integer.parseInt(saved.getProperty("msaa", "0").trim());
			return samples == 2 || samples == 4 || samples == 8 ? samples : 0;
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}
}
