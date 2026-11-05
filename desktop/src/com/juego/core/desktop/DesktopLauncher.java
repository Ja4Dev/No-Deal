package com.juego.core.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.juego.core.Core;

public class DesktopLauncher {
	public static void main(String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("No Deal");
		config.useVsync(true);
		config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
		// Saved settings (including fullscreen) can only be read once the app exists: Core applies them.
		config.setWindowedMode(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		new Lwjgl3Application(new Core(), config);
	}
}
