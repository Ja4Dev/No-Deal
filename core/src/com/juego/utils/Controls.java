package com.juego.utils;

import java.util.EnumMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Rebindable controls. Every action is bound to either a key or a mouse
 * button, so the game code asks {@code Controls.isPressed(Action.JUMP)} and
 * never cares which one the player chose.
 */
public final class Controls {

	public enum Action {
		FORWARD("Adelante", Binding.key(Input.Keys.W)),
		BACKWARD("Atras", Binding.key(Input.Keys.S)),
		LEFT("Izquierda", Binding.key(Input.Keys.A)),
		RIGHT("Derecha", Binding.key(Input.Keys.D)),
		JUMP("Saltar", Binding.key(Input.Keys.SPACE)),
		WALK("Caminar", Binding.key(Input.Keys.SHIFT_LEFT)),
		FIRE("Disparar", Binding.mouse(Input.Buttons.LEFT)),
		AIM("Apuntar", Binding.mouse(Input.Buttons.RIGHT)),
		RELOAD("Recargar", Binding.key(Input.Keys.R)),
		SWITCH_GUN("Cambiar arma", Binding.key(Input.Keys.Q)),
		DROP("Soltar / agarrar arma", Binding.key(Input.Keys.G)),
		SCOREBOARD("Puntuacion", Binding.key(Input.Keys.TAB)),
		PAUSE("Pausa", Binding.key(Input.Keys.ESCAPE)),
		/** Opens the console, for commands like "bot agregar" (only whoever runs the server). */
		CONSOLE("Consola", Binding.key(Input.Keys.GRAVE));

		/** Shown in the controls menu (the menu font has no accents). */
		public final String label;
		public final Binding defaultBinding;

		Action(String label, Binding defaultBinding) {
			this.label = label;
			this.defaultBinding = defaultBinding;
		}
	}

	/** A key or a mouse button. Immutable. */
	public static final class Binding {
		public final boolean mouse;
		public final int code;

		private Binding(boolean mouse, int code) {
			this.mouse = mouse;
			this.code = code;
		}

		public static Binding key(int keycode) {
			return new Binding(false, keycode);
		}

		public static Binding mouse(int button) {
			return new Binding(true, button);
		}

		/** "K:51" for a key, "M:0" for a mouse button. */
		public String serialize() {
			return (mouse ? "M:" : "K:") + code;
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
			} catch (NumberFormatException e) {
				// fall through
			}
			return null;
		}

		public String displayName() {
			if (!mouse) {
				if (code == Input.Keys.GRAVE)
					return "Izq. del 1"; // ` on US keyboards, º on Spanish ones
				String name = Input.Keys.toString(code);
				return name != null ? name : "Tecla " + code;
			}
			switch (code) {
			case Input.Buttons.LEFT:
				return "Clic izquierdo";
			case Input.Buttons.RIGHT:
				return "Clic derecho";
			case Input.Buttons.MIDDLE:
				return "Clic medio";
			default:
				return "Boton " + (code + 1);
			}
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Binding && ((Binding) o).mouse == mouse && ((Binding) o).code == code;
		}

		@Override
		public int hashCode() {
			return (mouse ? 1000 : 0) + code;
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

	public static boolean isPressed(Action action) {
		Binding b = bindings.get(action);
		return b.mouse ? Gdx.input.isButtonPressed(b.code) : Gdx.input.isKeyPressed(b.code);
	}

	public static boolean isJustPressed(Action action) {
		Binding b = bindings.get(action);
		return b.mouse ? Gdx.input.isButtonJustPressed(b.code) : Gdx.input.isKeyJustPressed(b.code);
	}
}
