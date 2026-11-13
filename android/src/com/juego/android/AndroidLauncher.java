package com.juego.android;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.juego.core.Core;

/**
 * Starts the game on Android: full screen, landscape, and the screen stays on
 * while playing. Also gives the game Bluetooth multiplayer.
 */
public class AndroidLauncher extends AndroidApplication {
	private AndroidBluetooth bluetooth;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useImmersiveMode = true; // hides the status and navigation bars
		config.useWakelock = true;
		config.useAccelerometer = false;
		config.useCompass = false;
		config.useGyroscope = false;
		config.depth = 24; // a 3D game: the default 16 bits shows z-fighting on the level's walls
		bluetooth = new AndroidBluetooth(this);
		addAndroidEventListener(bluetooth); // the answers to "turn Bluetooth on" and "make it visible"
		initialize(new Core(bluetooth), config);
		// Draw under the camera cutout too (in landscape it would otherwise leave a black bar at one side).
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams attributes = getWindow().getAttributes();
			attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			getWindow().setAttributes(attributes);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		bluetooth.onPermissionsResult(requestCode, grantResults);
	}
}
