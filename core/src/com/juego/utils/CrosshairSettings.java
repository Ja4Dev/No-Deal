package com.juego.utils;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * How the player wants the crosshair, like CS's crosshair settings: shape,
 * size, thickness, gap, color, opacity, a black outline, whether it opens
 * up while moving and while shooting (dynamic, each on its own), and whether
 * it follows the recoil to where the next bullet goes. Lengths are in screen
 * pixels at 1080p and scale with the window height.
 */
public class CrosshairSettings {
	public enum Style {
		/** Four lines. */
		CROSS("Clasica"),
		/** Four lines and a dot in the middle. */
		CROSS_DOT("Clasica con punto"),
		/** Just a dot. */
		DOT("Solo punto"),
		/** No top line. */
		T("Forma de T");

		public final String label;

		Style(String label) {
			this.label = label;
		}

		public boolean hasLines() {
			return this != DOT;
		}

		public boolean hasDot() {
			return this == CROSS_DOT || this == DOT;
		}
	}

	public static final String[] COLOR_NAMES = { "Verde", "Amarillo", "Cian", "Blanco", "Rojo", "Magenta" };
	private static final Color[] COLORS = { new Color(0.2f, 1f, 0.2f, 1), new Color(1f, 0.92f, 0.2f, 1),
			new Color(0.2f, 0.95f, 1f, 1), new Color(1f, 1f, 1f, 1), new Color(1f, 0.25f, 0.25f, 1),
			new Color(1f, 0.3f, 1f, 1) };

	public static final int MIN_SIZE = 1, MAX_SIZE = 20;
	public static final int MIN_THICKNESS = 1, MAX_THICKNESS = 6;
	public static final int MIN_GAP = 0, MAX_GAP = 16;
	public static final float MIN_OPACITY = 0.2f;

	public Style style = Style.CROSS;
	/** Length of each line. */
	public int size = 7;
	public int thickness = 2;
	/** Distance from the center to where each line starts. */
	public int gap = 4;
	/** Index into {@link #COLOR_NAMES}. */
	public int color = 0;
	public float opacity = 0.9f;
	public boolean outline = true;
	/** Opens up with the inaccuracy of moving and jumping. */
	public boolean dynamicMove = true;
	/** Opens up with the inaccuracy of spraying (the shots in a row). */
	public boolean dynamicFire = true;
	/**
	 * The view only climbs part of the recoil (as in CS): with this on, the
	 * crosshair climbs the rest, so it stays where the next bullet goes; off,
	 * it stays in the middle and the spray has to be pulled down by feel.
	 */
	public boolean followRecoil = true;

	public Color color(Color out) {
		return out.set(COLORS[MathUtils.clamp(color, 0, COLORS.length - 1)]).mul(1, 1, 1, opacity);
	}

	public void resetDefaults() {
		set(new CrosshairSettings());
	}

	public void set(CrosshairSettings other) {
		style = other.style;
		size = other.size;
		thickness = other.thickness;
		gap = other.gap;
		color = other.color;
		opacity = other.opacity;
		outline = other.outline;
		dynamicMove = other.dynamicMove;
		dynamicFire = other.dynamicFire;
		followRecoil = other.followRecoil;
	}

	public void load(Preferences prefs) {
		CrosshairSettings d = new CrosshairSettings();
		try {
			style = Style.valueOf(prefs.getString("crosshair.style", d.style.name()));
		} catch (IllegalArgumentException unknown) {
			style = d.style;
		}
		size = MathUtils.clamp(prefs.getInteger("crosshair.size", d.size), MIN_SIZE, MAX_SIZE);
		thickness = MathUtils.clamp(prefs.getInteger("crosshair.thickness", d.thickness), MIN_THICKNESS, MAX_THICKNESS);
		gap = MathUtils.clamp(prefs.getInteger("crosshair.gap", d.gap), MIN_GAP, MAX_GAP);
		color = MathUtils.clamp(prefs.getInteger("crosshair.color", d.color), 0, COLORS.length - 1);
		opacity = MathUtils.clamp(prefs.getFloat("crosshair.opacity", d.opacity), MIN_OPACITY, 1f);
		outline = prefs.getBoolean("crosshair.outline", d.outline);
		// Before there were two switches there was one, "crosshair.dynamic": it decides both if present.
		boolean oldDynamic = prefs.getBoolean("crosshair.dynamic", true);
		dynamicMove = prefs.getBoolean("crosshair.dynamicMove", oldDynamic && d.dynamicMove);
		dynamicFire = prefs.getBoolean("crosshair.dynamicFire", oldDynamic && d.dynamicFire);
		followRecoil = prefs.getBoolean("crosshair.followRecoil", d.followRecoil);
	}

	public void save(Preferences prefs) {
		prefs.putString("crosshair.style", style.name());
		prefs.putInteger("crosshair.size", size);
		prefs.putInteger("crosshair.thickness", thickness);
		prefs.putInteger("crosshair.gap", gap);
		prefs.putInteger("crosshair.color", color);
		prefs.putFloat("crosshair.opacity", opacity);
		prefs.putBoolean("crosshair.outline", outline);
		prefs.remove("crosshair.dynamic");
		prefs.putBoolean("crosshair.dynamicMove", dynamicMove);
		prefs.putBoolean("crosshair.dynamicFire", dynamicFire);
		prefs.putBoolean("crosshair.followRecoil", followRecoil);
	}
}
