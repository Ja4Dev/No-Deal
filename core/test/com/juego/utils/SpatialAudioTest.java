package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;

class SpatialAudioTest {
	private static final Vector3 ORIGIN = new Vector3(0, 0, 0);
	// Same convention as the game camera: looking down +Z, up +Y, so +X is to the left.
	private static final Vector3 FORWARD = new Vector3(0, 0, 1);
	private static final Vector3 UP = new Vector3(0, 1, 0);

	@Test
	void soundsToTheRightPanRightAndToTheLeftPanLeft() {
		assertEquals(1f, SpatialAudio.pan(ORIGIN, FORWARD, UP, new Vector3(-10, 0, 0)), 1e-4f);
		assertEquals(-1f, SpatialAudio.pan(ORIGIN, FORWARD, UP, new Vector3(10, 0, 0)), 1e-4f);
	}

	@Test
	void soundsInFrontOrBehindAreCentered() {
		assertEquals(0f, SpatialAudio.pan(ORIGIN, FORWARD, UP, new Vector3(0, 0, 30)), 1e-4f);
		assertEquals(0f, SpatialAudio.pan(ORIGIN, FORWARD, UP, new Vector3(0, 0, -30)), 1e-4f);
	}

	@Test
	void veryCloseSoundsAreMostlyCentered() {
		float near = Math.abs(SpatialAudio.pan(ORIGIN, FORWARD, UP, new Vector3(-0.5f, 0, 0)));
		assertTrue(near < 0.3f, "pan " + near);
	}

	@Test
	void volumeFallsOffWithDistance() {
		assertEquals(1f, SpatialAudio.volume(ORIGIN, ORIGIN), 1e-4f);
		float near = SpatialAudio.volume(ORIGIN, new Vector3(10, 0, 0));
		float far = SpatialAudio.volume(ORIGIN, new Vector3(60, 0, 0));
		assertTrue(near > far && far > 0, near + " > " + far);
		assertEquals(0f, SpatialAudio.volume(ORIGIN, new Vector3(SpatialAudio.MAX_DISTANCE + 1, 0, 0)), 1e-4f);
	}
}
