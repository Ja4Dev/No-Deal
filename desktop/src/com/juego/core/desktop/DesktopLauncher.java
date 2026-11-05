package com.juego.core.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.juego.core.Core;
import com.juego.utils.Config;

public class DesktopLauncher {
	public static void main(String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("No Deal");
		config.useVsync(true);
		config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
		if (Config.fullscreen) {
			config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
		} else {
			config.setWindowedMode(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		}
		new Lwjgl3Application(new Core(), config);
	}
}
