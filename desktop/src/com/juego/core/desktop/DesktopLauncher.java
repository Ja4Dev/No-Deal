package com.juego.core.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.juego.core.Core;
import com.juego.utils.Config;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new
				LwjglApplicationConfiguration();
				config.width = (int) Core.VIRTUAL_WIDTH;
				config.height = (int) Core.VIRTUAL_HEIGHT;
				config.title = "No Deal";
				config.fullscreen = Config.fullscreen;
				new LwjglApplication(new Core(), config);

	}
	
}
