package com.juego.UI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.juego.UI.TouchControls.Button;
import com.juego.utils.Controls.Action;

class TouchControlsTest {
	/** A 20:9 phone in landscape. */
	private static final int W = 2400, H = 1080;
	private static final float UNIT = H / TouchControls.UNITS;

	/** Fingers on a fake screen: positions in pixels, y down, like Gdx.input. */
	private static class Fingers implements TouchControls.Pointers {
		final boolean[] down = new boolean[TouchControls.MAX_POINTERS];
		final float[] x = new float[TouchControls.MAX_POINTERS], y = new float[TouchControls.MAX_POINTERS];

		Fingers touch(int pointer, float px, float py) {
			down[pointer] = true;
			x[pointer] = px;
			y[pointer] = py;
			return this;
		}

		Fingers lift(int pointer) {
			down[pointer] = false;
			return this;
		}

		@Override
		public boolean isDown(int pointer) {
			return down[pointer];
		}

		@Override
		public float x(int pointer) {
			return x[pointer];
		}

		@Override
		public float y(int pointer) {
			return y[pointer];
		}
	}

	private final TouchControls controls = new TouchControls();
	private final Fingers fingers = new Fingers();

	private void frame() {
		controls.update(W, H, fingers);
	}

	/** Where a button's center is, in Gdx.input coordinates (y down). */
	private float[] at(Button button) {
		controls.update(W, H, new Fingers()); // sets the screen size
		return new float[] { controls.centerX(button), H - controls.centerY(button) };
	}

	@Test
	void buttonsFitOnScreenWithoutOverlapping() {
		for (int[] size : new int[][] { { 2400, 1080 }, { 1280, 720 }, { 2048, 1536 } }) { // phone, small, 4:3 tablet
			controls.update(size[0], size[1], new Fingers());
			float unit = size[1] / TouchControls.UNITS;
			for (Button a : Button.values()) {
				float r = a.radius * unit;
				assertTrue(controls.centerX(a) - r >= 0 && controls.centerX(a) + r <= size[0], a + " inside horizontally");
				assertTrue(controls.centerY(a) - r >= 0 && controls.centerY(a) + r <= size[1], a + " inside vertically");
				for (Button b : Button.values()) {
					if (a.ordinal() >= b.ordinal())
						continue;
					float dx = controls.centerX(a) - controls.centerX(b), dy = controls.centerY(a) - controls.centerY(b);
					float gap = (float) Math.sqrt(dx * dx + dy * dy) - (a.radius + b.radius) * unit;
					assertTrue(gap > 10 * unit, a + " and " + b + " must not touch (gap " + gap + ")");
				}
			}
		}
	}

	@Test
	void everyButtonHasAnAction() {
		for (Button button : Button.values())
			assertEquals(button.label(), TouchControls.label(button.action));
		assertEquals(null, TouchControls.label(Action.FORWARD)); // the joystick, no button
	}

	@Test
	void joystickStartsUnderTheThumbAndWalksOrRuns() {
		fingers.touch(0, 400, H - 300);
		frame();
		assertEquals(0, controls.stickAmount(), "touching alone doesn't move");

		fingers.touch(0, 400, H - 300 - 0.4f * TouchControls.STICK_RADIUS * UNIT); // 40% forward
		frame();
		assertEquals(0.4f, controls.moveY(), 0.01f);
		assertEquals(0, controls.moveX(), 0.01f);
		assertTrue(controls.stickAmount() < TouchControls.RUN_THRESHOLD, "walks");

		fingers.touch(0, 400 + 3 * TouchControls.STICK_RADIUS * UNIT, H - 300); // far to the right
		frame();
		assertEquals(1, controls.moveX(), 0.01f, "clamped to the edge");
		assertTrue(controls.stickAmount() >= TouchControls.RUN_THRESHOLD, "runs");

		fingers.lift(0);
		frame();
		assertEquals(0, controls.moveX());
		assertEquals(0, controls.moveY());
	}

	@Test
	void smallMovesStayInTheDeadZone() {
		fingers.touch(0, 400, H - 300);
		frame();
		fingers.touch(0, 400 + 0.1f * TouchControls.STICK_RADIUS * UNIT, H - 300);
		frame();
		assertEquals(0, controls.moveX());
		assertEquals(0, controls.stickAmount());
	}

	@Test
	void touchingTheCornerDoesNotWalk() {
		fingers.touch(0, 5, H - 5); // the very corner
		frame();
		assertEquals(0, controls.stickAmount(), "the stick is centered under the thumb");
	}

	@Test
	void draggingOnTheRightTurnsTheView() {
		fingers.touch(0, 1400, 500);
		frame();
		assertEquals(0, controls.lookDeltaX(), "the first frame only puts the finger down");
		fingers.touch(0, 1450, 480);
		frame();
		assertEquals(50, controls.lookDeltaX(), 0.001f);
		assertEquals(-20, controls.lookDeltaY(), 0.001f, "up is negative, like the mouse");
		frame();
		assertEquals(0, controls.lookDeltaX(), "no movement, no turn");
		assertEquals(0, controls.stickAmount(), "the right side never moves the player");
	}

	@Test
	void moveLookAndShootAtTheSameTime() {
		float[] fire = at(Button.FIRE);
		fingers.touch(0, 400, H - 300).touch(1, fire[0], fire[1]);
		frame();
		assertTrue(controls.isJustPressed(Action.FIRE));
		assertTrue(controls.isPressed(Action.FIRE));

		fingers.touch(0, 400, H - 300 - TouchControls.STICK_RADIUS * UNIT).touch(1, fire[0] - 30, fire[1] + 10);
		frame();
		assertFalse(controls.isJustPressed(Action.FIRE), "only the first frame");
		assertTrue(controls.isPressed(Action.FIRE), "held");
		assertEquals(1, controls.moveY(), 0.01f);
		assertEquals(-30, controls.lookDeltaX(), 0.001f, "the fire button also aims");
		assertEquals(10, controls.lookDeltaY(), 0.001f);

		fingers.lift(1);
		frame();
		assertFalse(controls.isPressed(Action.FIRE));
	}

	@Test
	void otherButtonsDontTurnTheView() {
		float[] jump = at(Button.JUMP);
		fingers.touch(0, jump[0], jump[1]);
		frame();
		assertTrue(controls.isJustPressed(Action.JUMP));
		fingers.touch(0, jump[0] + 20, jump[1]);
		frame();
		assertEquals(0, controls.lookDeltaX());
	}

	@Test
	void slidingOntoAButtonDoesNotPressIt() {
		float[] reload = at(Button.RELOAD);
		fingers.touch(0, 1400, 500);
		frame();
		fingers.touch(0, reload[0], reload[1]);
		frame();
		assertFalse(controls.isPressed(Action.RELOAD), "a look drag that crosses a button stays a look drag");
	}

	@Test
	void theTapThatClosesThePauseMenuIsIgnored() {
		float[] pause = at(Button.PAUSE);
		fingers.touch(0, pause[0], pause[1]);
		frame();
		assertTrue(controls.isJustPressed(Action.PAUSE));

		// The pause menu opens: controls off while the finger is still down...
		controls.disable(fingers);
		assertFalse(controls.isJustPressed(Action.PAUSE));
		assertFalse(controls.isPressed(Action.PAUSE));
		// ...and back on (tapped "Reanudar") with the same finger still down: no new press.
		frame();
		assertFalse(controls.isJustPressed(Action.PAUSE), "doesn't pause again right away");
		fingers.lift(0);
		frame();
		fingers.touch(0, pause[0], pause[1]);
		frame();
		assertTrue(controls.isJustPressed(Action.PAUSE), "a new tap works again");
	}

	@Test
	void aTapBetweenTwoFramesStillPressesTheButton() {
		float[] fire = at(Button.FIRE);
		// Down and up before the next frame: polling never sees the finger.
		controls.recordTouchDown(0, fire[0], fire[1]);
		frame();
		assertTrue(controls.isJustPressed(Action.FIRE), "the recorded tap counts");
		assertTrue(controls.isPressed(Action.FIRE), "for one frame");
		frame();
		assertFalse(controls.isPressed(Action.FIRE), "and is over in the next");
	}

	@Test
	void aRecordedTapIsNotCountedTwice() {
		float[] jump = at(Button.JUMP);
		fingers.touch(0, jump[0], jump[1]);
		controls.recordTouchDown(0, jump[0], jump[1]);
		frame();
		assertTrue(controls.isJustPressed(Action.JUMP));
		frame();
		assertFalse(controls.isJustPressed(Action.JUMP), "still held, not pressed again");
	}

	@Test
	void tapsOnTheLookAreaDoNothingWhenMissed() {
		controls.recordTouchDown(0, 1400, 500);
		frame();
		for (Action action : Action.values())
			assertFalse(controls.isPressed(action));
	}

	@Test
	void tapsOnThePauseMenuAreForgotten() {
		float[] fire = at(Button.FIRE);
		controls.recordTouchDown(0, fire[0], fire[1]);
		controls.disable(fingers); // the tap was on the menu
		frame();
		assertFalse(controls.isPressed(Action.FIRE));
	}

	@Test
	void crouchIsASwitch() {
		float[] crouch = at(Button.CROUCH);
		fingers.touch(0, crouch[0], crouch[1]);
		frame();
		fingers.lift(0);
		frame();
		assertTrue(controls.isPressed(Action.CROUCH), "still crouching after lifting the finger");
		assertTrue(controls.isCrouchOn());
		fingers.touch(0, crouch[0], crouch[1]);
		frame();
		fingers.lift(0);
		frame();
		assertFalse(controls.isPressed(Action.CROUCH), "a second tap stands up");
	}

	@Test
	void onlyOneJoystickAtATime() {
		fingers.touch(0, 400, H - 300).touch(1, 600, H - 250);
		frame();
		fingers.touch(1, 700, H - 250);
		frame();
		assertEquals(0, controls.stickAmount(), "the second thumb on the left turns the view");
		assertEquals(100, controls.lookDeltaX(), 0.001f);
		assertNotNull(TouchControls.label(Action.FIRE));
	}
}
