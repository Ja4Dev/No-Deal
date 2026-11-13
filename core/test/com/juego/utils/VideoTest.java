package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Array;
import com.juego.utils.Video.Aspect;
import com.juego.utils.Video.Mode;
import com.juego.utils.Video.Resolution;

class VideoTest {
	private static final Resolution NATIVE = new Resolution(1920, 1080);
	private static final Array<Resolution> SCREEN = new Array<>(new Resolution[] { NATIVE, new Resolution(1680, 1050),
			new Resolution(1280, 1024), new Resolution(1024, 768), new Resolution(1280, 720), new Resolution(640, 480) });

	@Test
	void theDefaultFovIsTheViewTheGameAlwaysHad() {
		assertEquals(67, Video.verticalFov(Video.DEFAULT_FOV), 0.2f);
		assertEquals(73.74f, Video.verticalFov(90), 0.05f, "CS's 90 on 4:3");
		assertTrue(Video.verticalFov(Video.MAX_FOV) > Video.verticalFov(Video.MIN_FOV));
	}

	@Test
	void aspectRatiosMatchTheirResolutions() {
		assertTrue(Aspect.FOUR_THREE.matches(1024, 768));
		assertTrue(Aspect.FOUR_THREE.matches(1280, 1024), "5:4 goes with 4:3, as in CS");
		assertTrue(Aspect.SIXTEEN_NINE.matches(1366, 768), "it isn't exactly 16:9, but it is sold as one");
		assertTrue(Aspect.SIXTEEN_TEN.matches(1680, 1050));
		assertTrue(Aspect.WIDE.matches(2560, 1080) && Aspect.WIDE.matches(3440, 1440));
		assertFalse(Aspect.SIXTEEN_NINE.matches(1680, 1050));
		assertEquals(Aspect.SIXTEEN_NINE, Aspect.of(1920, 1080));
		assertEquals(Aspect.ALL, Aspect.of(1000, 100));
	}

	@Test
	void fullscreenOffersAnySizeUpToTheMonitors() {
		// It is drawn off screen and scaled, so it needn't be a mode of the monitor.
		Array<Resolution> list = Video.resolutions(Mode.FULLSCREEN, Aspect.ALL, SCREEN, NATIVE);
		assertEquals(NATIVE, list.first(), "biggest first");
		assertTrue(list.contains(new Resolution(1600, 900), false));
		assertTrue(list.contains(new Resolution(1280, 960), false), "4:3 on a 16:9 monitor, as in CS");
		assertFalse(list.contains(new Resolution(640, 480), false), "too small for the menus");
		assertFalse(list.contains(new Resolution(2560, 1440), false), "bigger than the monitor");
	}

	@Test
	void windowsCanBeAnySizeThatFits() {
		Array<Resolution> list = Video.resolutions(Mode.WINDOWED, Aspect.SIXTEEN_NINE, SCREEN, NATIVE);
		assertTrue(list.contains(new Resolution(1600, 900), false), "a common size, even if not a mode");
		assertFalse(list.contains(new Resolution(2560, 1440), false), "bigger than the screen");
		for (Resolution r : list)
			assertTrue(Aspect.SIXTEEN_NINE.matches(r.width, r.height), r.toString());
		Array<Resolution> borderless = Video.resolutions(Mode.BORDERLESS, Aspect.FOUR_THREE, SCREEN, NATIVE);
		assertTrue(borderless.contains(new Resolution(1024, 768), false), "borderless scales too");
	}
}
