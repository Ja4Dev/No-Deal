package com.juego.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.utils.Array;

/**
 * Video settings: how the window is shown (window, fullscreen or borderless),
 * its resolution, picked from those of an aspect ratio as in CS, how a
 * resolution of another shape than the monitor's fills it, the field of view,
 * the shadows and antialiasing. They are kept in {@link Config}.
 * <p>
 * Fullscreen and borderless always use the monitor's own resolution; another
 * one is drawn off screen and scaled (see {@link Display}), so it looks the
 * same whatever the graphics driver would have done with it.
 */
public final class Video {
	/** How the game is shown on a computer. */
	public enum Mode {
		WINDOWED, FULLSCREEN,
		/** A window without borders the size of the screen: fullscreen, but switching to other windows is instant. */
		BORDERLESS;

		public String label() {
			return Text.get("video.mode." + name());
		}
	}

	/** How a resolution of another shape than the monitor's (4:3 on a 16:9 one) fills it. */
	public enum Scaling {
		/** Keeps its shape, with black bars at the sides (or above and below). */
		BLACK_BARS,
		/** Fills the whole monitor, stretched (as many CS players play 4:3). */
		STRETCH;

		public String label() {
			return Text.get("video.scaling." + name());
		}
	}

	/** The aspect ratios the resolution list can be narrowed to. */
	public enum Aspect {
		ALL(0), FOUR_THREE(4f / 3), SIXTEEN_NINE(16f / 9), SIXTEEN_TEN(16f / 10), WIDE(64f / 27);

		final float ratio;

		Aspect(float ratio) {
			this.ratio = ratio;
		}

		public String label() {
			switch (this) {
			case FOUR_THREE:
				return "4:3";
			case SIXTEEN_NINE:
				return "16:9";
			case SIXTEEN_TEN:
				return "16:10";
			case WIDE:
				return "21:9";
			default:
				return Text.get("video.aspect.all");
			}
		}

		/** Whether a resolution has this shape (21:9 takes the whole family of ultrawides: 2560x1080, 3440x1440...). */
		public boolean matches(int width, int height) {
			if (this == ALL)
				return true;
			float r = width / (float) height;
			if (this == WIDE)
				return r > 2.25f && r < 2.45f;
			if (this == FOUR_THREE)
				return Math.abs(r - ratio) < 0.02f || (width == 1280 && height == 1024); // 5:4, CS lists it with 4:3
			return Math.abs(r - ratio) < 0.02f || (this == SIXTEEN_NINE && width == 1366 && height == 768);
		}

		/** The ratio a resolution belongs to (ALL if none of these). */
		public static Aspect of(int width, int height) {
			for (Aspect a : values())
				if (a != ALL && a.matches(width, height))
					return a;
			return ALL;
		}
	}

	/** Shadow quality: how big the shadow map is and how far around the player it reaches. */
	public enum Shadows {
		OFF(0, 0), LOW(1024, 60), MEDIUM(2048, 80), HIGH(4096, 110);

		/** Shadow map size in pixels, and the side of the square around the player it covers, in world units. */
		public final int mapSize;
		public final float area;

		Shadows(int mapSize, float area) {
			this.mapSize = mapSize;
			this.area = area;
		}

		public String label() {
			return Text.get("video.shadows." + name());
		}
	}

	/** A width and height. */
	public static final class Resolution {
		public final int width, height;

		public Resolution(int width, int height) {
			this.width = width;
			this.height = height;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Resolution && ((Resolution) o).width == width && ((Resolution) o).height == height;
		}

		@Override
		public int hashCode() {
			return width * 31 + height;
		}

		@Override
		public String toString() {
			return width + " x " + height;
		}
	}

	/** Field of view, in degrees across a 4:3 screen, as CS counts it; wider screens see more at the sides. */
	public static final int MIN_FOV = 70, MAX_FOV = 110;
	/** The view the game always had (67 degrees from top to bottom). */
	public static final int DEFAULT_FOV = 83;
	/** Antialiasing samples the menu offers (0 = off). Applied when the game starts. */
	public static final int[] MSAA = { 0, 2, 4, 8 };
	/** Window sizes offered besides the screen's own modes. */
	private static final int[][] WINDOW_SIZES = { { 800, 600 }, { 1024, 768 }, { 1152, 864 }, { 1280, 720 },
			{ 1280, 800 }, { 1280, 960 }, { 1280, 1024 }, { 1366, 768 }, { 1440, 900 }, { 1600, 900 }, { 1600, 1200 },
			{ 1680, 1050 }, { 1920, 1080 }, { 1920, 1200 }, { 2560, 1080 }, { 2560, 1440 }, { 2560, 1600 },
			{ 3440, 1440 }, { 3840, 2160 } };

	private Video() {
	}

	/**
	 * The camera's vertical field of view for a {@link Config#fov} (horizontal
	 * on 4:3): wider screens then see more at the sides and the same up and down.
	 */
	public static float verticalFov(float fov43) {
		double half = Math.toRadians(fov43) / 2;
		return (float) Math.toDegrees(2 * Math.atan(Math.tan(half) * 3 / 4));
	}

	/** The camera's vertical field of view now. */
	public static float verticalFov() {
		return verticalFov(Config.fov);
	}

	/**
	 * The resolutions to offer, biggest first.
	 *
	 * @param screen the screen's modes (fullscreen offers only these)
	 * @param native_ the screen's own resolution: windows can't be bigger
	 */
	public static Array<Resolution> resolutions(Mode mode, Aspect aspect, Array<Resolution> screen,
			Resolution native_) {
		// Fullscreen draws any resolution scaled to the monitor, so it offers the same sizes as a window,
		// plus the monitor's own modes.
		Array<Resolution> out = new Array<>();
		Array<Resolution> candidates = new Array<>(screen);
		candidates.add(native_);
		for (int[] size : WINDOW_SIZES)
			candidates.add(new Resolution(size[0], size[1]));
		for (Resolution r : candidates) {
			if (out.contains(r, false) || !aspect.matches(r.width, r.height) || r.width < 800 || r.height < 600)
				continue;
			if (r.width > native_.width || r.height > native_.height)
				continue;
			out.add(r);
		}
		out.sort((a, b) -> a.width != b.width ? b.width - a.width : b.height - a.height);
		return out;
	}

	// ---- the real screen (desktop) ----

	/** The screen's resolutions, each once (its best refresh rate). */
	public static Array<Resolution> screenResolutions() {
		Array<Resolution> out = new Array<>();
		for (Graphics.DisplayMode m : Gdx.graphics.getDisplayModes()) {
			Resolution r = new Resolution(m.width, m.height);
			if (!out.contains(r, false))
				out.add(r);
		}
		return out;
	}

	/** The screen's own resolution: its biggest mode. */
	public static Resolution nativeResolution() {
		Graphics.DisplayMode best = Gdx.graphics.getDisplayMode();
		for (Graphics.DisplayMode m : Gdx.graphics.getDisplayModes())
			if ((long) m.width * m.height > (long) best.width * best.height)
				best = m;
		return new Resolution(best.width, best.height);
	}

	/** The resolution in the settings, or the default for the mode (a 1280x720 window, or the screen's). */
	public static Resolution chosenResolution() {
		if (Config.screenWidth <= 0 || Config.screenHeight <= 0)
			return Config.videoMode == Mode.WINDOWED ? new Resolution(1280, 720) : nativeResolution();
		return new Resolution(Config.screenWidth, Config.screenHeight);
	}

	/** Shows the game as the settings say (on startup and when the player applies them). Desktop only. */
	public static void apply() {
		if (!Platform.desktop || Gdx.graphics == null)
			return;
		Resolution r = chosenResolution();
		Resolution screen = nativeResolution();
		switch (Config.videoMode) {
		case FULLSCREEN: {
			// The monitor's own resolution, at its best refresh rate; the chosen one is drawn scaled.
			Graphics.DisplayMode best = null;
			for (Graphics.DisplayMode m : Gdx.graphics.getDisplayModes())
				if (m.width == screen.width && m.height == screen.height
						&& (best == null || m.refreshRate > best.refreshRate))
					best = m;
			Gdx.graphics.setFullscreenMode(best != null ? best : Gdx.graphics.getDisplayMode());
			Display.setRenderSize(r.width, r.height, Config.scaling == Scaling.STRETCH);
			break;
		}
		case BORDERLESS:
			if (Gdx.graphics.isFullscreen())
				Gdx.graphics.setWindowedMode(1280, 720); // out of fullscreen first, so the window is placed anew
			Gdx.graphics.setUndecorated(true);
			Gdx.graphics.setWindowedMode(screen.width, screen.height); // libGDX centers it: at 0,0
			Display.setRenderSize(r.width, r.height, Config.scaling == Scaling.STRETCH);
			break;
		default:
			Gdx.graphics.setUndecorated(false);
			Gdx.graphics.setWindowedMode(r.width, r.height);
			Display.setRenderSize(0, 0, false);
			break;
		}
		Config.applyFpsMax();
		resizeGame();
	}

	/** Stretched or with black bars, at once. */
	public static void applyScaling() {
		if (Config.videoMode != Mode.WINDOWED) {
			Resolution r = chosenResolution();
			Display.setRenderSize(r.width, r.height, Config.scaling == Scaling.STRETCH);
		}
	}

	/**
	 * Tells the game the size it draws on changed: a new resolution in
	 * fullscreen keeps the window's size, so no resize would come otherwise.
	 */
	private static void resizeGame() {
		if (Gdx.app != null && Gdx.app.getApplicationListener() != null)
			Gdx.app.getApplicationListener().resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
	}
}
