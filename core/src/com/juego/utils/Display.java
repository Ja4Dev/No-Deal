package com.juego.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;

/**
 * Where the game draws. Normally straight to the window. In fullscreen (and
 * borderless) the window always has the monitor's own resolution, and if the
 * player chose another one the game draws at that resolution into an
 * off-screen image, which is then put on the monitor either stretched to fill
 * it or with black bars that keep its shape (see Video.Scaling). That way the
 * game decides, not the graphics driver, and a 4:3 resolution looks the same
 * on every computer.
 * <p>
 * Code that needs the size of what it draws on asks here ({@link #width()},
 * {@link #height()}), not the window. Mouse positions arrive in window
 * coordinates; {@link #toRenderX} and {@link #toRenderY} turn them into the
 * drawing's.
 */
public final class Display {
	/** The resolution to draw at, or 0 to draw straight to the window. */
	private static int renderWidth, renderHeight;
	private static boolean stretch;
	private static FrameBuffer frameBuffer;
	private static SpriteBatch batch;
	private static final Matrix4 projection = new Matrix4();
	/** Where the image goes in the window (pixels from the bottom left). */
	private static float rectX, rectY, rectWidth, rectHeight;

	private Display() {
	}

	/**
	 * Draws at {@code width} x {@code height} and scales it to the window;
	 * 0 x 0 (or the window's own size) draws straight to the window.
	 */
	public static void setRenderSize(int width, int height, boolean stretch) {
		Display.stretch = stretch;
		if (width == renderWidth && height == renderHeight)
			return;
		renderWidth = width;
		renderHeight = height;
		disposeFrameBuffer();
	}

	/** Whether the game draws into the off-screen image now. */
	public static boolean isScaled() {
		return renderWidth > 0 && renderHeight > 0 && Gdx.graphics != null
				&& (renderWidth != Gdx.graphics.getBackBufferWidth() || renderHeight != Gdx.graphics.getBackBufferHeight());
	}

	/** Width of what the game draws on, in pixels (the window's logical size if it isn't scaled). */
	public static int width() {
		return isScaled() ? renderWidth : Gdx.graphics.getWidth();
	}

	public static int height() {
		return isScaled() ? renderHeight : Gdx.graphics.getHeight();
	}

	/** Same, in the framebuffer's pixels (more than {@link #width()} on high-density screens). */
	public static int backBufferWidth() {
		return isScaled() ? renderWidth : Gdx.graphics.getBackBufferWidth();
	}

	public static int backBufferHeight() {
		return isScaled() ? renderHeight : Gdx.graphics.getBackBufferHeight();
	}

	/** Starts a frame: from here on everything is drawn into the off-screen image, if scaled. */
	public static void begin() {
		if (!isScaled())
			return;
		if (frameBuffer == null) {
			frameBuffer = new FrameBuffer(Pixmap.Format.RGB888, renderWidth, renderHeight, true);
			frameBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		}
		frameBuffer.begin();
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
	}

	/**
	 * Draws into the off-screen image again after something else drew into its
	 * own (the shadow map): ending a libGDX frame buffer goes back to the window.
	 */
	public static void resume() {
		if (frameBuffer != null && isScaled())
			frameBuffer.bind();
	}

	/** Ends a frame: puts the off-screen image on the window, stretched or with black bars. */
	public static void end() {
		if (frameBuffer == null || !isScaled())
			return;
		frameBuffer.end();
		int windowWidth = Gdx.graphics.getBackBufferWidth(), windowHeight = Gdx.graphics.getBackBufferHeight();
		updateRect(windowWidth, windowHeight);
		Gdx.gl.glViewport(0, 0, windowWidth, windowHeight);
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		if (batch == null)
			batch = new SpriteBatch();
		batch.setProjectionMatrix(projection.setToOrtho2D(0, 0, windowWidth, windowHeight));
		batch.begin();
		// Framebuffer images are upside down.
		batch.draw(frameBuffer.getColorBufferTexture(), rectX, rectY, rectWidth, rectHeight, 0, 0, renderWidth,
				renderHeight, false, true);
		batch.end();
	}

	/** Stretched, the whole window; with bars, as big as fits keeping the shape, centered. */
	private static void updateRect(float windowWidth, float windowHeight) {
		if (stretch) {
			rectX = rectY = 0;
			rectWidth = windowWidth;
			rectHeight = windowHeight;
			return;
		}
		float scale = Math.min(windowWidth / renderWidth, windowHeight / renderHeight);
		rectWidth = renderWidth * scale;
		rectHeight = renderHeight * scale;
		rectX = (windowWidth - rectWidth) / 2;
		rectY = (windowHeight - rectHeight) / 2;
	}

	/** A mouse x in the window to the drawing's. */
	public static int toRenderX(int windowX) {
		if (!isScaled())
			return windowX;
		updateRect(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		return Math.round((windowX - rectX) * renderWidth / rectWidth);
	}

	/**
	 * A mouse y in the window (from the top) to the drawing's, offset so that
	 * libGDX's viewports, which flip it with the window's height, get the
	 * drawing's.
	 */
	public static int toRenderY(int windowY) {
		if (!isScaled())
			return windowY;
		int windowHeight = Gdx.graphics.getHeight();
		updateRect(Gdx.graphics.getWidth(), windowHeight);
		float top = windowHeight - rectY - rectHeight; // the bar above, in window pixels from the top
		int y = Math.round((windowY - top) * renderHeight / rectHeight);
		return y + (windowHeight - renderHeight);
	}

	private static void disposeFrameBuffer() {
		if (frameBuffer != null) {
			frameBuffer.dispose();
			frameBuffer = null;
		}
	}

	public static void dispose() {
		disposeFrameBuffer();
		if (batch != null) {
			batch.dispose();
			batch = null;
		}
	}
}
