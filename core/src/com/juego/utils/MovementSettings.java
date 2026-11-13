package com.juego.utils;

/**
 * The movement rules the server sets for everyone, changed from its console
 * like CS's server variables:
 * <ul>
 * <li>{@code sv_gravity}: in CS's units, 800 is normal (this world's 48
 * units/s²). Jumps are as strong at any gravity, so they go higher when it is
 * lower, and falls hurt less.</li>
 * <li>{@code sv_airaccelerate}: how fast you can change direction in the air
 * (the same formula as Quake and CS). 10 is CS's normal; around 100 air
 * strafing and bunnyhopping gain a lot of speed.</li>
 * <li>{@code sv_autobunnyhopping}: holding jump jumps again the moment you land
 * (as in CS:GO), instead of having to press it again on time.</li>
 * <li>{@code sv_maxspeed}: running speed, in CS's units (250 is normal, like CS
 * with the knife). Walking and crouching keep their share of it.</li>
 * <li>{@code sv_maxvelocity}: nobody moves faster than this horizontally,
 * however good the bunnyhop (750 is normal: three times running).</li>
 * <li>{@code sv_falldamage}: whether long falls hurt.</li>
 * </ul>
 * Speeds are shown and set in CS's units ({@link #SPEED_SCALE} per world unit),
 * the numbers CS players know: 250 running, 125 walking.
 * <p>
 * The server sends them to every player when they join and whenever they
 * change; bots follow them too.
 */
public final class MovementSettings {
	public static final String GRAVITY = "sv_gravity", AIR_ACCELERATE = "sv_airaccelerate",
			AUTO_BUNNYHOP = "sv_autobunnyhopping", MAX_SPEED = "sv_maxspeed", MAX_VELOCITY = "sv_maxvelocity",
			FALL_DAMAGE = "sv_falldamage";
	/** Every variable, in the order they are listed. */
	public static final String[] NAMES = { GRAVITY, AIR_ACCELERATE, AUTO_BUNNYHOP, MAX_SPEED, MAX_VELOCITY,
			FALL_DAMAGE };
	public static final int DEFAULT_GRAVITY = 800, MIN_GRAVITY = 100, MAX_GRAVITY = 4000;
	public static final float DEFAULT_AIR_ACCELERATE = 10, MAX_AIR_ACCELERATE = 1000;
	/** CS's speed units per world unit per second: running (12 here) is CS's 250. */
	public static final float SPEED_SCALE = 250f / PlayerMovement.RUN_SPEED;
	public static final int DEFAULT_MAX_SPEED = 250, MIN_MAX_SPEED = 50, MAX_MAX_SPEED = 2000;
	public static final int DEFAULT_MAX_VELOCITY = Math.round(PlayerMovement.MAX_SPEED * SPEED_SCALE),
			MIN_MAX_VELOCITY = 100, MAX_MAX_VELOCITY = 5000;

	public int gravity = DEFAULT_GRAVITY;
	public float airAccelerate = DEFAULT_AIR_ACCELERATE;
	public boolean autoBunnyhop;
	/** In CS's units: see {@link #runSpeed()} and {@link #topSpeed()} for this world's. */
	public int maxSpeed = DEFAULT_MAX_SPEED, maxVelocity = DEFAULT_MAX_VELOCITY;
	public boolean fallDamage = true;

	/** The gravity in this world's units/s², for the physics. */
	public float gravityAcceleration() {
		return gravity * PlayerMovement.GRAVITY / DEFAULT_GRAVITY;
	}

	/** Running speed, in world units per second. */
	public float runSpeed() {
		return maxSpeed / SPEED_SCALE;
	}

	/** Walking: the same share of running as normally (half). */
	public float walkSpeed() {
		return runSpeed() * PlayerMovement.WALK_SPEED / PlayerMovement.RUN_SPEED;
	}

	public float crouchSpeed() {
		return runSpeed() * PlayerMovement.CROUCH_SPEED / PlayerMovement.RUN_SPEED;
	}

	/** Nothing goes faster horizontally, in world units per second. */
	public float topSpeed() {
		return maxVelocity / SPEED_SCALE;
	}

	/** The fastest anyone can legitimately move horizontally: running or bunnyhopping, whichever is more. */
	public float fastestSpeed() {
		return Math.max(runSpeed(), topSpeed());
	}

	public MovementSettings set(MovementSettings other) {
		gravity = other.gravity;
		airAccelerate = other.airAccelerate;
		autoBunnyhop = other.autoBunnyhop;
		maxSpeed = other.maxSpeed;
		maxVelocity = other.maxVelocity;
		fallDamage = other.fallDamage;
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
		case MAX_SPEED:
			return Integer.toString(maxSpeed);
		case MAX_VELOCITY:
			return Integer.toString(maxVelocity);
		case FALL_DAMAGE:
			return fallDamage ? "1" : "0";
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
			case MAX_SPEED: {
				int s = Math.round(Float.parseFloat(value));
				if (s < MIN_MAX_SPEED || s > MAX_MAX_SPEED)
					return Text.get("server.cvarRange", name, MIN_MAX_SPEED, MAX_MAX_SPEED);
				maxSpeed = s;
				return null;
			}
			case MAX_VELOCITY: {
				int v = Math.round(Float.parseFloat(value));
				if (v < MIN_MAX_VELOCITY || v > MAX_MAX_VELOCITY)
					return Text.get("server.cvarRange", name, MIN_MAX_VELOCITY, MAX_MAX_VELOCITY);
				maxVelocity = v;
				return null;
			}
			case AUTO_BUNNYHOP:
			case FALL_DAMAGE: {
				if (!value.equals("0") && !value.equals("1"))
					return Text.get("server.cvarRange", name, 0, 1);
				if (name.equals(AUTO_BUNNYHOP))
					autoBunnyhop = value.equals("1");
				else
					fallDamage = value.equals("1");
				return null;
			}
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
		return m.gravity == gravity && m.airAccelerate == airAccelerate && m.autoBunnyhop == autoBunnyhop
				&& m.maxSpeed == maxSpeed && m.maxVelocity == maxVelocity && m.fallDamage == fallDamage;
	}

	@Override
	public int hashCode() {
		return ((gravity * 31 + Float.floatToIntBits(airAccelerate)) * 31 + maxSpeed) * 31 + maxVelocity
				+ (autoBunnyhop ? 1 : 0) + (fallDamage ? 2 : 0);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String name : NAMES) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(name).append(' ').append(get(name));
		}
		return sb.toString();
	}
}
