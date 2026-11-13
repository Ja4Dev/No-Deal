package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input;
import com.juego.utils.Controls.Action;
import com.juego.utils.Controls.Binding;

class ControlsTest {

	@AfterEach
	void restore() {
		Controls.resetDefaults();
	}

	@Test
	void defaultsAreTheClassicLayout() {
		assertEquals(Binding.key(Input.Keys.W), Controls.get(Action.FORWARD));
		assertEquals(Binding.mouse(Input.Buttons.LEFT), Controls.get(Action.FIRE));
		assertEquals(Binding.key(Input.Keys.ESCAPE), Controls.get(Action.PAUSE));
	}

	@Test
	void takingABindingInUseSwapsTheTwoActions() {
		Controls.set(Action.JUMP, Binding.key(Input.Keys.R)); // R was RELOAD
		assertEquals(Binding.key(Input.Keys.R), Controls.get(Action.JUMP));
		assertEquals(Binding.key(Input.Keys.SPACE), Controls.get(Action.RELOAD), "reload got jump's old key");
		assertNoDuplicates();
	}

	@Test
	void weaponsGoOnNumbersQAndTheWheel() {
		assertEquals(Binding.key(Input.Keys.NUM_1), Controls.get(Action.PRIMARY));
		assertEquals(Binding.key(Input.Keys.NUM_2), Controls.get(Action.SECONDARY));
		assertEquals(Binding.key(Input.Keys.NUM_3), Controls.get(Action.KNIFE));
		assertEquals(Binding.key(Input.Keys.Q), Controls.get(Action.LAST_WEAPON));
		assertEquals(Binding.wheel(1), Controls.get(Action.NEXT_WEAPON), "wheel down: next, as CS");
		assertEquals(Binding.wheel(-1), Controls.get(Action.PREVIOUS_WEAPON));
		assertNoDuplicates();
	}

	@Test
	void theWheelCanBeBoundAndCountsForOneFrame() {
		Controls.set(Action.JUMP, Binding.wheel(1)); // bunnyhop on the wheel, as in CS; next weapon gets space
		assertEquals(Binding.key(Input.Keys.SPACE), Controls.get(Action.NEXT_WEAPON));
		assertEquals("W:1", Binding.wheel(5).serialize());
		assertEquals(Binding.wheel(-1), Binding.parse("W:-1"));
		assertNull(Binding.parse("W:2"));
		String saved = Controls.serialize();
		Controls.resetDefaults();
		Controls.deserialize(saved);
		assertEquals(Binding.wheel(1), Controls.get(Action.JUMP));

		Controls.newFrame();
		assertFalse(Controls.isJustPressed(Action.JUMP));
		Controls.scrolled(1);
		assertFalse(Controls.isJustPressed(Action.JUMP), "counts from the next frame on");
		Controls.newFrame();
		assertTrue(Controls.isJustPressed(Action.JUMP));
		assertTrue(Controls.isPressed(Action.JUMP));
		assertFalse(Controls.isJustPressed(Action.PREVIOUS_WEAPON), "up is another binding");
		Controls.newFrame();
		assertFalse(Controls.isJustPressed(Action.JUMP), "only that frame");
	}

	@Test
	void actionsCanUseMouseButtons() {
		Controls.set(Action.RELOAD, Binding.mouse(Input.Buttons.MIDDLE));
		assertEquals("Clic medio", Controls.get(Action.RELOAD).displayName());
	}

	@Test
	void serializationRoundTrip() {
		Controls.set(Action.FORWARD, Binding.key(Input.Keys.UP));
		Controls.set(Action.AIM, Binding.mouse(Input.Buttons.MIDDLE));
		String saved = Controls.serialize();
		Controls.resetDefaults();
		Controls.deserialize(saved);
		assertEquals(Binding.key(Input.Keys.UP), Controls.get(Action.FORWARD));
		assertEquals(Binding.mouse(Input.Buttons.MIDDLE), Controls.get(Action.AIM));
		assertNoDuplicates();
	}

	@Test
	void corruptSettingsFallBackToDefaults() {
		Controls.deserialize("FORWARD=K:9999,NOT_AN_ACTION=K:51,JUMP=garbage,,=,BACKWARD");
		assertEquals(Binding.key(Input.Keys.W), Controls.get(Action.FORWARD));
		assertEquals(Binding.key(Input.Keys.SPACE), Controls.get(Action.JUMP));
		assertNull(Binding.parse("M:99"));
		assertNull(Binding.parse(""));
		assertNoDuplicates();
	}

	private static void assertNoDuplicates() {
		Set<Binding> seen = new HashSet<>();
		for (Action action : Action.values())
			if (!seen.add(Controls.get(action)))
				throw new AssertionError("binding used twice: " + Controls.get(action));
	}
}
