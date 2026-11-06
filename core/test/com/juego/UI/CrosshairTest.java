package com.juego.UI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.FloatArray;
import com.juego.utils.CrosshairSettings;

class CrosshairTest {
	private final FloatArray rects = new FloatArray();

	private static CrosshairSettings settings(CrosshairSettings.Style style) {
		CrosshairSettings s = new CrosshairSettings();
		s.style = style;
		s.size = 8;
		s.thickness = 2;
		s.gap = 4;
		return s;
	}

	@Test
	void classicCrossHasFourLinesAroundTheCenter() {
		Crosshair.layout(settings(CrosshairSettings.Style.CROSS), 100, 100, 0, 1, rects);
		assertEquals(4 * 4, rects.size);
		// right line: starts gap pixels right of the center, centered vertically
		assertEquals(104, rects.get(0));
		assertEquals(99, rects.get(1));
		assertEquals(8, rects.get(2));
		assertEquals(2, rects.get(3));
		// left line ends gap pixels left of the center
		assertEquals(100 - 4 - 8, rects.get(4));
	}

	@Test
	void stylesAddOrRemoveParts() {
		Crosshair.layout(settings(CrosshairSettings.Style.CROSS_DOT), 0, 0, 0, 1, rects);
		assertEquals(5 * 4, rects.size, "four lines and a dot");
		Crosshair.layout(settings(CrosshairSettings.Style.DOT), 0, 0, 0, 1, rects);
		assertEquals(4, rects.size, "just the dot");
		Crosshair.layout(settings(CrosshairSettings.Style.T), 0, 0, 0, 1, rects);
		assertEquals(3 * 4, rects.size, "no top line");
		for (int i = 0; i < rects.size; i += 4)
			assertTrue(rects.get(i + 1) < 4, "nothing above the center");
	}

	@Test
	void theDynamicGapPushesTheLinesOut() {
		Crosshair.layout(settings(CrosshairSettings.Style.CROSS), 0, 0, 10, 1, rects);
		assertEquals(4 + 10, rects.get(0));
	}

	@Test
	void scalesWithTheWindowButNeverVanishes() {
		CrosshairSettings s = settings(CrosshairSettings.Style.CROSS);
		Crosshair.layout(s, 0, 0, 0, 2, rects);
		assertEquals(16, rects.get(2), "twice as long at 4K");
		s.thickness = 1;
		s.size = 1;
		Crosshair.layout(s, 0, 0, 0, 0.5f, rects);
		assertEquals(1, rects.get(2));
		assertEquals(1, rects.get(3));
	}
}
