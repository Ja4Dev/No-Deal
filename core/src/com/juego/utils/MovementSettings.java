package com.juego.utils;

/**
 * The movement rules the server sets for everyone, changed from its console
 * like CS's server variables:
 * <ul>
 * <li>{@code sv_gravity}: in CS's units, 800 is normal (this world's 48
 * units/s²). Jumps are as strong at any gravity, so they go higher when it is
 * lower.</li>
 * <li>{@code sv_airaccelerate}: how fast you can change direction in the air
 * (the same formula as Quake and CS). 10 is CS's normal; around 100 air
 * strafing and bunnyhopping gain a lot of speed.</li>
 * <li>{@code sv_autobunnyhopping}: holding jump jumps again the moment you land
 * (as in CS:GO), instead of having to press it again on time.</li>
 * </ul>
 * The server sends them to every player when they join and whenever they
 * change; bots follow them too.
 */
public final class MovementSettings {
	public static final String GRAVITY = "sv_gravity", AIR_ACCELERATE = "sv_airaccelerate",
			AUTO_BUNNYHOP = "sv_autobunnyhopping";
	public static final int DEFAULT_GRAVITY = 800, MIN_GRAVITY = 100, MAX_GRAVITY = 4000;
	public static final float DEFAULT_AIR_ACCELERATE = 10, MAX_AIR_ACCELERATE = 1000;

	public int gravity = DEFAULT_GRAVITY;
	public float airAccelerate = DEFAULT_AIR_ACCELERATE;
	public boolean autoBunnyhop;

	/** The gravity in this world's units/s², for the physics. */
	public float gravityAcceleration() {
		return gravity * PlayerMovement.GRAVITY / DEFAULT_GRAVITY;
	}

	public MovementSettings set(MovementSettings other) {
		gravity = other.gravity;
		airAccelerate = other.airAccelerate;
		autoBunnyhop = other.autoBunnyhop;
		return this;
	}

	/** The value of a variable as typed in the console ("800", "10", "0"), or null if it isn't one of these. */
	public String get(String name) {
		switch (name) {
		case GRAVITY:
			return Integer.toString(gravity);
		case AIR_ACCELERATE:
			return format(airAccelerate);
		case AUTO_BUNNYHOP:
			return autoBunnyhop ? "1" : "0";
		default:
			return null;
		}
	}

	/**
	 * Sets a variable from the console.
	 *
	 * @return null if it was set, or what is wrong with the value (for the player)
	 */
	public String set(String name, String value) {
		try {
			switch (name) {
			case GRAVITY: {
				int g = Math.round(Float.parseFloat(value));
				if (g < MIN_GRAVITY || g > MAX_GRAVITY)
					return Text.get("server.cvarRange", name, MIN_GRAVITY, MAX_GRAVITY);
				gravity = g;
				return null;
			}
			case AIR_ACCELERATE: {
				float a = Float.parseFloat(value);
				if (a < 0 || a > MAX_AIR_ACCELERATE || Float.isNaN(a))
					return Text.get("server.cvarRange", name, 0, format(MAX_AIR_ACCELERATE));
				airAccelerate = a;
				return null;
			}
			case AUTO_BUNNYHOP:
				if (!value.equals("0") && !value.equals("1"))
					return Text.get("server.cvarRange", name, 0, 1);
				autoBunnyhop = value.equals("1");
				return null;
			default:
				return Text.get("server.cvarUnknown", name);
			}
		} catch (NumberFormatException e) {
			return Text.get("server.cvarNotNumber", name, value);
		}
	}

	/** "10", "12.5": no ".0" for whole numbers. */
	public static String format(float value) {
		return value == (int) value ? Integer.toString((int) value) : Float.toString(value);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MovementSettings))
			return false;
		MovementSettings m = (MovementSettings) o;
		return m.gravity == gravity && m.airAccelerate == airAccelerate && m.autoBunnyhop == autoBunnyhop;
	}

	@Override
	public int hashCode() {
		return gravity * 31 + Float.floatToIntBits(airAccelerate) * 7 + (autoBunnyhop ? 1 : 0);
	}

	@Override
	public String toString() {
		return GRAVITY + " " + get(GRAVITY) + ", " + AIR_ACCELERATE + " " + get(AIR_ACCELERATE) + ", " + AUTO_BUNNYHOP
				+ " " + get(AUTO_BUNNYHOP);
	}
}
