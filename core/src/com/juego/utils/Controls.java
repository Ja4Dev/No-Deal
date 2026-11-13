package com.juego.utils;

import java.util.EnumMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.juego.UI.TouchControls;

/**
 * Rebindable controls. Every action is bound to a key, a mouse button or a
 * turn of the mouse wheel, so the game code asks
 * {@code Controls.isPressed(Action.JUMP)} and never cares which one the
 * player chose.
 */
public final class Controls {

	public enum Action {
		FORWARD(Binding.key(Input.Keys.W)),
		BACKWARD(Binding.key(Input.Keys.S)),
		LEFT(Binding.key(Input.Keys.A)),
		RIGHT(Binding.key(Input.Keys.D)),
		JUMP(Binding.key(Input.Keys.SPACE)),
		WALK(Binding.key(Input.Keys.SHIFT_LEFT)),
		/** Held: slower than walking, lower, and a little more accurate. */
		CROUCH(Binding.key(Input.Keys.CONTROL_LEFT)),
		FIRE(Binding.mouse(Input.Buttons.LEFT)),
		AIM(Binding.mouse(Input.Buttons.RIGHT)),
		RELOAD(Binding.key(Input.Keys.R)),
		/** Back to the weapon held before (as CS's Q: lastinv). */
		LAST_WEAPON(Binding.key(Input.Keys.Q)),
		PRIMARY(Binding.key(Input.Keys.NUM_1)),
		SECONDARY(Binding.key(Input.Keys.NUM_2)),
		KNIFE(Binding.key(Input.Keys.NUM_3)),
		/** Primary, secondary, knife, primary... (wheel down, as CS's invnext). */
		NEXT_WEAPON(Binding.wheel(1)),
		PREVIOUS_WEAPON(Binding.wheel(-1)),
		DROP(Binding.key(Input.Keys.G)),
		SCOREBOARD(Binding.key(Input.Keys.TAB)),
		PAUSE(Binding.key(Input.Keys.ESCAPE)),
		/** Opens the console, for commands like "bot agregar" (only whoever runs the server). */
		CONSOLE(Binding.key(Input.Keys.GRAVE));

		public final Binding defaultBinding;

		/** Shown in the controls menu (the menu font has no accents). */
		public String label() {
			return Text.get("action." + name());
		}

		Action(Binding defaultBinding) {
			this.defaultBinding = defaultBinding;
		}
	}

	/** A key, a mouse button, or the mouse wheel turned one way. Immutable. */
	public static final class Binding {
		public final boolean mouse;
		/** The mouse wheel: {@link #code} is 1 for down (towards you), -1 for up. */
		public final boolean wheel;
		public final int code;

		private Binding(boolean mouse, boolean wheel, int code) {
			this.mouse = mouse;
			this.wheel = wheel;
			this.code = code;
		}

		public static Binding key(int keycode) {
			return new Binding(false, false, keycode);
		}

		public static Binding mouse(int button) {
			return new Binding(true, false, button);
		}

		/** @param direction 1 for down (towards you), -1 for up */
		public static Binding wheel(int direction) {
			return new Binding(false, true, direction > 0 ? 1 : -1);
		}

		/** "K:51" for a key, "M:0" for a mouse button, "W:1" or "W:-1" for the wheel. */
		public String serialize() {
			return (wheel ? "W:" : mouse ? "M:" : "K:") + code;
		}

		/** @return null if the text is not a valid binding */
		public static Binding parse(String text) {
			if (text == null || text.length() < 3 || text.charAt(1) != ':')
				return null;
			try {
				int code = Integer.parseInt(text.substring(2));
				if (text.charAt(0) == 'K' && code > 0 && code <= Input.Keys.MAX_KEYCODE)
					return key(code);
				if (text.charAt(0) == 'M' && code >= 0 && code <= Input.Buttons.FORWARD)
					return mouse(code);
				if (text.charAt(0) == 'W' && (code == 1 || code == -1))
					return wheel(code);
			} catch (NumberFormatException e) {
				// fall through
			}
			return null;
		}

		public String displayName() {
			if (wheel)
				return Text.get(code > 0 ? "mouse.wheelDown" : "mouse.wheelUp");
			if (!mouse) {
				if (code == Input.Keys.GRAVE)
					return Text.get("key.grave"); // ` on US keyboards, º on Spanish ones
				String name = Input.Keys.toString(code);
				return name != null ? name : Text.get("key.other", code);
			}
			switch (code) {
			case Input.Buttons.LEFT:
				return Text.get("mouse.left");
			case Input.Buttons.RIGHT:
				return Text.get("mouse.right");
			case Input.Buttons.MIDDLE:
				return Text.get("mouse.middle");
			default:
				return Text.get("mouse.other", code + 1);
			}
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Binding && ((Binding) o).mouse == mouse && ((Binding) o).wheel == wheel
					&& ((Binding) o).code == code;
		}

		@Override
		public int hashCode() {
			return (mouse ? 1000 : 0) + (wheel ? 2000 : 0) + code;
		}

		@Override
		public String toString() {
			return displayName();
		}
	}

	private static final EnumMap<Action, Binding> bindings = new EnumMap<>(Action.class);

	static {
		resetDefaults();
	}

	private Controls() {
	}

	public static Binding get(Action action) {
		return bindings.get(action);
	}

	/**
	 * Binds an action. If another action already used that key or button, the
	 * two swap bindings, so no action is ever left unbound or doubly bound.
	 */
	public static void set(Action action, Binding binding) {
		Binding previous = bindings.get(action);
		for (Action other : Action.values()) {
			if (other != action && binding.equals(bindings.get(other)))
				bindings.put(other, previous);
		}
		bindings.put(action, binding);
	}

	public static void resetDefaults() {
		for (Action action : Action.values())
			bindings.put(action, action.defaultBinding);
	}

	/** "FORWARD=K:51,JUMP=K:62,..." */
	public static String serialize() {
		StringBuilder sb = new StringBuilder();
		for (Action action : Action.values()) {
			if (sb.length() > 0)
				sb.append(',');
			sb.append(action.name()).append('=').append(bindings.get(action).serialize());
		}
		return sb.toString();
	}

	/** Loads saved bindings; unknown or invalid entries keep their default. */
	public static void deserialize(String text) {
		resetDefaults();
		if (text == null || text.isEmpty())
			return;
		for (String entry : text.split(",")) {
			String[] parts = entry.split("=", 2);
			if (parts.length != 2)
				continue;
			Binding binding = Binding.parse(parts[1].trim());
			if (binding == null)
				continue;
			try {
				set(Action.valueOf(parts[0].trim()), binding);
			} catch (IllegalArgumentException e) {
				// an action that no longer exists
			}
		}
	}

	/**
	 * The on-screen controls while playing on a phone or tablet, or null. Their
	 * buttons count as pressing the action, besides its key (a phone can have a
	 * keyboard plugged in too).
	 */
	public static TouchControls touch;

	// ---- the mouse wheel: libGDX only reports it as events ----

	/** Wheel turns that arrived since the last frame started, and the ones that count this frame. */
	private static int pendingDown, pendingUp, wheelDown, wheelUp;

	/** A turn of the wheel (positive: down). The game's input processors pass it on (see Menu). */
	public static void scrolled(float amountY) {
		if (amountY > 0)
			pendingDown++;
		else if (amountY < 0)
			pendingUp++;
	}

	/** Called once at the start of every frame: the wheel turned since the last one counts in this one. */
	public static void newFrame() {
		wheelDown = pendingDown;
		wheelUp = pendingUp;
		pendingDown = pendingUp = 0;
	}

	private static boolean wheelTurned(Binding b) {
		return b.code > 0 ? wheelDown > 0 : wheelUp > 0;
	}

	public static boolean isPressed(Action action) {
		if (touch != null && touch.isPressed(action))
			return true;
		Binding b = bindings.get(action);
		if (b.wheel)
			return !Platform.touch && wheelTurned(b);
		if (b.mouse)
			// On a touch screen every finger counts as the left mouse button: only the on-screen buttons fire.
			return !Platform.touch && Gdx.input.isButtonPressed(b.code);
		return Gdx.input.isKeyPressed(b.code);
	}

	public static boolean isJustPressed(Action action) {
		if (touch != null && touch.isJustPressed(action))
			return true;
		if (action == Action.PAUSE && Gdx.input.isKeyJustPressed(Input.Keys.BACK))
			return true; // Android's back button
		Binding b = bindings.get(action);
		if (b.wheel)
			return !Platform.touch && wheelTurned(b);
		if (b.mouse)
			return !Platform.touch && Gdx.input.isButtonJustPressed(b.code);
		return Gdx.input.isKeyJustPressed(b.code);
	}

	/** How to tell the player to use an action: its key ("G"), or on a touch screen the button's label ("AGARRAR"). */
	public static String promptName(Action action) {
		String label = Platform.touch ? TouchControls.label(action) : null;
		return label != null ? label.replace('\n', '/') : get(action).displayName(); // "SOLTAR/AGARRAR"
	}
}
