package com.juego.utils;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;

/**
 * What the game runs on, for the few things that differ: phones and tablets
 * play with on-screen controls, have no window to make fullscreen, and on iOS
 * an app must not quit itself.
 * <p>
 * On desktop, {@code -Dnodeal.touch=true} turns on the touch controls (driven
 * with the mouse), to try them without a phone.
 */
public final class Platform {
	/** On-screen controls instead of mouse look and mouse buttons. */
	public static boolean touch;
	/** A desktop window (fullscreen option, mouse and keyboard settings). */
	public static boolean desktop = true;
	/** Whether the main menu shows "Salir" (Apple doesn't allow iOS apps to close themselves). */
	public static boolean canQuit = true;

	private Platform() {
	}

	public static void detect() {
		ApplicationType type = Gdx.app.getType();
		boolean mobile = type == ApplicationType.Android || type == ApplicationType.iOS;
		desktop = type == ApplicationType.Desktop;
		touch = mobile || Boolean.getBoolean("nodeal.touch");
		canQuit = type != ApplicationType.iOS;
	}
}
