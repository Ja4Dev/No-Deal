package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.juego.utils.CrosshairSettings;

/**
 * Draws the crosshair from {@link CrosshairSettings}, in real screen pixels
 * (not the HUD's scaled virtual resolution) so its lines stay sharp.
 */
public class Crosshair implements Disposable {
	/** Settings sizes are pixels at this window height. */
	private static final float REFERENCE_HEIGHT = 1080f;

	private final ShapeRenderer shapes = new ShapeRenderer();
	private final FloatArray rects = new FloatArray();
	private final Matrix4 projection = new Matrix4();
	private final Color color = new Color();

	/**
	 * The crosshair's rectangles, as (x, y, width, height) quadruples, around
	 * the pixel (cx, cy). Whole pixels only, so nothing is drawn blurry.
	 *
	 * @param extraGap pixels the lines move out (dynamic crosshair)
	 * @param scale    window height / 1080
	 */
	public static void layout(CrosshairSettings s, int cx, int cy, int extraGap, float scale, FloatArray out) {
		out.clear();
		int length = Math.max(1, Math.round(s.size * scale));
		int thickness = Math.max(1, Math.round(s.thickness * scale));
		int gap = Math.round(s.gap * scale) + Math.max(0, extraGap);
		int half = thickness / 2;
		if (s.style.hasLines()) {
			out.addAll(cx + gap, cy - half, length, thickness); // right
			out.addAll(cx - gap - length, cy - half, length, thickness); // left
			out.addAll(cx - half, cy - gap - length, thickness, length); // bottom
			if (s.style != CrosshairSettings.Style.T)
				out.addAll(cx - half, cy + gap, thickness, length); // top
		}
		if (s.style.hasDot())
			out.addAll(cx - half, cy - half, thickness, thickness);
	}

	/** Pixel scale for the current window: bigger above 1080p, but never smaller, so it stays readable. */
	public static float scale() {
		return Math.max(1f, Gdx.graphics.getHeight() / REFERENCE_HEIGHT);
	}

	/**
	 * Draws over the whole window.
	 *
	 * @param cx, cy center, in window pixels from the bottom left
	 */
	public void draw(CrosshairSettings s, float cx, float cy, float extraGap) {
		int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
		layout(s, Math.round(cx), Math.round(cy), Math.round(extraGap), scale(), rects);
		HdpiUtils.glViewport(0, 0, width, height);
		shapes.setProjectionMatrix(projection.setToOrtho2D(0, 0, width, height));
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		s.color(color);
		if (s.outline) {
			shapes.setColor(0, 0, 0, color.a * 0.85f);
			for (int i = 0; i < rects.size; i += 4)
				shapes.rect(rects.get(i) - 1, rects.get(i + 1) - 1, rects.get(i + 2) + 2, rects.get(i + 3) + 2);
		}
		shapes.setColor(color);
		for (int i = 0; i < rects.size; i += 4)
			shapes.rect(rects.get(i), rects.get(i + 1), rects.get(i + 2), rects.get(i + 3));
		shapes.end();
	}

	@Override
	public void dispose() {
		shapes.dispose();
	}
}
