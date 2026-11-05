package com.juego.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Directional sound without a 3D audio engine: a sound is attenuated with
 * distance and panned left/right according to where it is relative to the
 * listener's view. libGDX can pan mono sounds, which is why the gunshots ship
 * as mono WAV files.
 */
public final class SpatialAudio {
	/** Beyond this distance a sound is inaudible. */
	public static final float MAX_DISTANCE = 120f;
	/** Sounds very close to the listener are centered instead of fully panned. */
	private static final float CENTER_RADIUS = 2f;

	private SpatialAudio() {
	}

	/** Volume factor in [0, 1]: quadratic falloff from 1 at the listener to 0 at {@link #MAX_DISTANCE}. */
	public static float volume(Vector3 listener, Vector3 source) {
		float t = MathUtils.clamp(1 - listener.dst(source) / MAX_DISTANCE, 0, 1);
		return t * t;
	}

	/**
	 * Pan in [-1, 1] (left to right) for a listener at {@code listener} looking
	 * along {@code direction} with {@code up} as its up vector.
	 */
	public static float pan(Vector3 listener, Vector3 direction, Vector3 up, Vector3 source) {
		float dx = source.x - listener.x, dy = source.y - listener.y, dz = source.z - listener.z;
		float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (distance < 1e-4f)
			return 0;
		// right = direction x up
		float rx = direction.y * up.z - direction.z * up.y;
		float ry = direction.z * up.x - direction.x * up.z;
		float rz = direction.x * up.y - direction.y * up.x;
		float rightLength = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
		if (rightLength < 1e-4f)
			return 0;
		float side = (rx * dx + ry * dy + rz * dz) / (rightLength * distance);
		return MathUtils.clamp(side * Math.min(1, distance / CENTER_RADIUS), -1, 1);
	}
}
