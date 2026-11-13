package com.juego.ios;

import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.glkit.GLKViewDrawableDepthFormat;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIRectEdge;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import com.juego.core.Core;

/** Starts the game on iPhone and iPad: landscape, and the screen stays on while playing. */
public class IOSLauncher extends IOSApplication.Delegate {
	@Override
	protected IOSApplication createApplication() {
		IOSApplicationConfiguration config = new IOSApplicationConfiguration();
		config.orientationLandscape = true;
		config.orientationPortrait = false;
		config.preventScreenDimming = true;
		config.depthFormat = GLKViewDrawableDepthFormat._24; // a 3D game: 16 bits shows z-fighting
		config.hideHomeIndicator = true;
		// A swipe from the bottom edge (aiming, moving) must reach the game before iOS goes home.
		config.screenEdgesDeferringSystemGestures = UIRectEdge.All;
		config.useAccelerometer = false;
		config.useCompass = false;
		return new IOSApplication(new Core(), config);
	}

	public static void main(String[] argv) {
		NSAutoreleasePool pool = new NSAutoreleasePool();
		UIApplication.main(argv, null, IOSLauncher.class);
		pool.close();
	}
}
