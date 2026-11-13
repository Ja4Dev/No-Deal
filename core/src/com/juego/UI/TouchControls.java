package com.juego.UI;

import java.nio.IntBuffer;
import java.util.EnumSet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.juego.utils.Controls.Action;

/**
 * On-screen controls for phones and tablets, the usual mobile shooter layout:
 * <ul>
 * <li>a joystick that appears where the left thumb touches (lower left part of
 * the screen); pushed a little it walks, pushed to the edge it runs;</li>
 * <li>dragging anywhere else turns the view;</li>
 * <li>buttons on the right for the actions, and pause, scoreboard and console
 * at the top. The fire button also turns the view while dragged, so you can
 * aim and shoot with one thumb.</li>
 * </ul>
 * The game asks for actions through {@link com.juego.utils.Controls}, which
 * checks these buttons besides the keyboard, so the systems don't care where
 * an action came from.
 * <p>
 * The fingers are read every frame by polling (not through an input
 * processor), so the controls work whatever stage has the input. Positions
 * are measured in units of 1/720 of the screen height, so the layout looks
 * the same on every screen; horizontal positions are relative to the left or
 * right edge, so wide phones get more room in the middle, not stretched
 * buttons.
 */
public class TouchControls implements Disposable {

	/** Where the fingers are: {@link #GDX} on a device, a fake in tests. Coordinates in pixels, y down. */
	public interface Pointers {
		boolean isDown(int pointer);

		float x(int pointer);

		float y(int pointer);
	}

	public static final Pointers GDX = new Pointers() {
		@Override
		public boolean isDown(int pointer) {
			return Gdx.input.isTouched(pointer);
		}

		@Override
		public float x(int pointer) {
			return Gdx.input.getX(pointer);
		}

		@Override
		public float y(int pointer) {
			return Gdx.input.getY(pointer);
		}
	};

	/** Fingers tracked at once (Android reports up to 20, nobody uses more than a few). */
	static final int MAX_POINTERS = 10;
	/** Design height of the layout, in units. */
	static final float UNITS = 720;
	static final float STICK_RADIUS = 95;
	/** Where the joystick rests when no finger holds it. */
	static final float STICK_REST_X = 200, STICK_REST_Y = 200;
	/** The joystick starts in the left part of the screen, below the top buttons. */
	static final float STICK_AREA_WIDTH = 0.45f, STICK_AREA_TOP = 0.78f;
	/** Pushed less than this, the joystick does nothing (a resting thumb mustn't walk). */
	public static final float DEADZONE = 0.15f;
	/** Pushed at least this far the player runs; less, walks (silently). */
	public static final float RUN_THRESHOLD = 0.65f;
	/** Buttons react a little outside their circle: fingers are not precise. */
	private static final float HIT_SLOP = 1.2f;

	/**
	 * The buttons. x and y are the center in units: negative x is measured from
	 * the right edge, negative y from the top.
	 */
	public enum Button {
		FIRE(Action.FIRE, "FUEGO", -215, 235, 80),
		AIM(Action.AIM, "MIRA", -360, 330, 50),
		JUMP(Action.JUMP, "SALTO", -85, 360, 50),
		SWITCH_GUN(Action.SWITCH_GUN, "ARMA", -215, 440, 44),
		RELOAD(Action.RELOAD, "RECARGA", -90, 495, 44),
		DROP(Action.DROP, "AGARRAR", -360, 475, 44),
		PAUSE(Action.PAUSE, "II", -60, -60, 36),
		SCOREBOARD(Action.SCOREBOARD, "PTS", -150, -60, 36),
		CONSOLE(Action.CONSOLE, ">_", -240, -60, 36);

		public final Action action;
		public final String label;
		final float x, y, radius;

		Button(Action action, String label, float x, float y, float radius) {
			this.action = action;
			this.label = label;
			this.x = x;
			this.y = y;
			this.radius = radius;
		}

		static Button forAction(Action action) {
			for (Button button : values())
				if (button.action == action)
					return button;
			return null;
		}
	}

	private enum Role {
		/** Not touching. */
		NONE,
		/** Was already down when the controls came back (e.g. the tap on "Reanudar"): ignored until lifted. */
		IGNORED,
		STICK,
		LOOK,
		BUTTON
	}

	private final Role[] roles = new Role[MAX_POINTERS];
	private final Button[] pointerButtons = new Button[MAX_POINTERS];
	private final float[] lastX = new float[MAX_POINTERS], lastY = new float[MAX_POINTERS];

	private final EnumSet<Action> pressed = EnumSet.noneOf(Action.class);
	private final EnumSet<Action> justPressed = EnumSet.noneOf(Action.class);
	private int stickPointer = -1;
	private float stickOriginX, stickOriginY, stickX, stickY;
	private float lookX, lookY;
	private int width = 1, height = 1;
	private float unit = 1;

	// Drawing, created on first use (tests never draw).
	private ShapeRenderer shapes;
	private SpriteBatch batch;
	private BitmapFont font;
	private OrthographicCamera camera;
	private final GlyphLayout layout = new GlyphLayout();
	private final Color color = new Color();
	private final IntBuffer savedViewport = BufferUtils.newIntBuffer(16);

	public TouchControls() {
		for (int i = 0; i < MAX_POINTERS; i++)
			roles[i] = Role.NONE;
	}

	/** The on-screen label of the button for an action ("AGARRAR"), or null if it has none. */
	public static String label(Action action) {
		Button button = Button.forAction(action);
		return button == null ? null : button.label;
	}

	/** Reads the fingers. Call once per frame, before the game asks for actions. */
	public void update(int screenWidth, int screenHeight, Pointers pointers) {
		if (pointers == GDX)
			taps.install();
		setScreen(screenWidth, screenHeight);
		justPressed.clear();
		lookX = lookY = 0;
		for (int p = 0; p < MAX_POINTERS; p++) {
			if (!pointers.isDown(p)) {
				release(p);
				continue;
			}
			float x = pointers.x(p), y = height - pointers.y(p); // y up from here on
			if (roles[p] == Role.NONE)
				press(p, x, y);
			else
				drag(p, x, y);
			lastX[p] = x;
			lastY[p] = y;
		}
		pressed.clear();
		for (int p = 0; p < MAX_POINTERS; p++)
			if (roles[p] == Role.BUTTON)
				pressed.add(pointerButtons[p].action);
		pressMissedTaps();
	}

	/**
	 * A quick tap can start and end between two frames (a slow phone, or a
	 * fast finger), and then polling never sees it down. The touch-down events
	 * are recorded too: a tap on a button whose finger is already up counts as
	 * pressing it for this frame.
	 */
	private void pressMissedTaps() {
		for (int i = 0; i < taps.count; i++) {
			int p = taps.pointers[i];
			if (p < 0 || p >= MAX_POINTERS || roles[p] != Role.NONE)
				continue; // still down: the polling above handled it
			Button button = buttonAt(taps.xs[i], height - taps.ys[i]);
			if (button != null) {
				justPressed.add(button.action);
				pressed.add(button.action);
			}
		}
		taps.count = 0;
	}

	/** Records a finger touching down (from the input events), for {@link #pressMissedTaps}. */
	void recordTouchDown(int pointer, float x, float y) {
		taps.add(pointer, x, y);
	}

	/**
	 * Sees the touch-down events by wrapping whatever input processor the game
	 * has (the pause menu's stage, the console...) and passing everything on.
	 * Screens replace the processor now and then, so it is wrapped again
	 * whenever it changed.
	 */
	private final class TapRecorder implements InputProcessor {
		static final int MAX = 32;
		final int[] pointers = new int[MAX];
		final float[] xs = new float[MAX], ys = new float[MAX];
		int count;
		private InputProcessor wrapped;

		void install() {
			InputProcessor current = Gdx.input.getInputProcessor();
			if (current == this)
				return;
			wrapped = current;
			Gdx.input.setInputProcessor(this);
		}

		void uninstall() {
			if (Gdx.input != null && Gdx.input.getInputProcessor() == this)
				Gdx.input.setInputProcessor(wrapped);
			wrapped = null;
		}

		void add(int pointer, float x, float y) {
			if (count == MAX)
				return;
			pointers[count] = pointer;
			xs[count] = x;
			ys[count] = y;
			count++;
		}

		@Override
		public boolean touchDown(int screenX, int screenY, int pointer, int button) {
			add(pointer, screenX, screenY);
			return wrapped != null && wrapped.touchDown(screenX, screenY, pointer, button);
		}

		@Override
		public boolean touchUp(int screenX, int screenY, int pointer, int button) {
			return wrapped != null && wrapped.touchUp(screenX, screenY, pointer, button);
		}

		@Override
		public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
			return wrapped != null && wrapped.touchCancelled(screenX, screenY, pointer, button);
		}

		@Override
		public boolean touchDragged(int screenX, int screenY, int pointer) {
			return wrapped != null && wrapped.touchDragged(screenX, screenY, pointer);
		}

		@Override
		public boolean mouseMoved(int screenX, int screenY) {
			return wrapped != null && wrapped.mouseMoved(screenX, screenY);
		}

		@Override
		public boolean scrolled(float amountX, float amountY) {
			return wrapped != null && wrapped.scrolled(amountX, amountY);
		}

		@Override
		public boolean keyDown(int keycode) {
			return wrapped != null && wrapped.keyDown(keycode);
		}

		@Override
		public boolean keyUp(int keycode) {
			return wrapped != null && wrapped.keyUp(keycode);
		}

		@Override
		public boolean keyTyped(char character) {
			return wrapped != null && wrapped.keyTyped(character);
		}
	}

	private final TapRecorder taps = new TapRecorder();

	/**
	 * While the controls are hidden (pause menu, console): nothing is pressed,
	 * and fingers already down are ignored until lifted, so the tap that closes
	 * the menu doesn't also press a button or turn the view.
	 */
	public void disable(Pointers pointers) {
		for (int p = 0; p < MAX_POINTERS; p++)
			roles[p] = pointers.isDown(p) ? Role.IGNORED : Role.NONE;
		taps.count = 0; // taps on the menu, not on the controls
		pressed.clear();
		justPressed.clear();
		stickPointer = -1;
		stickX = stickY = lookX = lookY = 0;
	}

	private void setScreen(int screenWidth, int screenHeight) {
		width = Math.max(1, screenWidth);
		height = Math.max(1, screenHeight);
		unit = height / UNITS;
	}

	private void press(int p, float x, float y) {
		Button button = buttonAt(x, y);
		if (button != null) {
			roles[p] = Role.BUTTON;
			pointerButtons[p] = button;
			justPressed.add(button.action);
		} else if (stickPointer < 0 && x < width * STICK_AREA_WIDTH && y < height * STICK_AREA_TOP) {
			roles[p] = Role.STICK;
			stickPointer = p;
			// Centered on the thumb, even near the edge (partly off screen): moving it into the screen
			// would count as pushing it, and a touch in the corner would walk.
			stickOriginX = x;
			stickOriginY = y;
			moveStick(x, y);
		} else {
			roles[p] = Role.LOOK;
		}
	}

	private void drag(int p, float x, float y) {
		switch (roles[p]) {
		case STICK:
			moveStick(x, y);
			break;
		case LOOK:
			look(p, x, y);
			break;
		case BUTTON:
			if (pointerButtons[p] == Button.FIRE)
				look(p, x, y); // aim while shooting
			break;
		default:
			break;
		}
	}

	private void look(int p, float x, float y) {
		lookX += x - lastX[p];
		lookY -= y - lastY[p]; // back to y down, like the mouse
	}

	private void moveStick(float x, float y) {
		float dx = (x - stickOriginX) / (STICK_RADIUS * unit), dy = (y - stickOriginY) / (STICK_RADIUS * unit);
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length > 1) {
			dx /= length;
			dy /= length;
		}
		stickX = dx;
		stickY = dy;
	}

	private void release(int p) {
		if (p == stickPointer) {
			stickPointer = -1;
			stickX = stickY = 0;
		}
		roles[p] = Role.NONE;
		pointerButtons[p] = null;
	}

	private Button buttonAt(float x, float y) {
		for (Button button : Button.values()) {
			float r = button.radius * unit * HIT_SLOP;
			float dx = x - centerX(button), dy = y - centerY(button);
			if (dx * dx + dy * dy <= r * r)
				return button;
		}
		return null;
	}

	float centerX(Button button) {
		return button.x < 0 ? width + button.x * unit : button.x * unit;
	}

	float centerY(Button button) {
		return button.y < 0 ? height + button.y * unit : button.y * unit;
	}

	public boolean isPressed(Action action) {
		return pressed.contains(action);
	}

	public boolean isJustPressed(Action action) {
		return justPressed.contains(action);
	}

	/** How far the joystick is pushed sideways, -1 (left) to 1 (right); 0 inside the dead zone. */
	public float moveX() {
		return stickAmount() > 0 ? stickX : 0;
	}

	/** How far the joystick is pushed forward, -1 (back) to 1 (forward); 0 inside the dead zone. */
	public float moveY() {
		return stickAmount() > 0 ? stickY : 0;
	}

	/** How far the joystick is pushed, 0 to 1 (0 inside the dead zone). */
	public float stickAmount() {
		float amount = (float) Math.sqrt(stickX * stickX + stickY * stickY);
		return amount < DEADZONE ? 0 : amount;
	}

	/** How far the view-turning fingers moved this frame, in pixels (right positive). */
	public float lookDeltaX() {
		return lookX;
	}

	/** How far the view-turning fingers moved this frame, in pixels (down positive, like the mouse). */
	public float lookDeltaY() {
		return lookY;
	}

	public int screenHeight() {
		return height;
	}

	// ---- Drawing ----

	/** Draws the joystick and the buttons over the game. */
	public void draw() {
		if (shapes == null) {
			shapes = new ShapeRenderer();
			batch = new SpriteBatch();
			font = new BitmapFont();
			font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			camera = new OrthographicCamera();
		}
		setScreen(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		// The whole screen, not the HUD's letterboxed 16:9 area; put back afterwards because the stages
		// drawn later (pause menu, scoreboard) don't set it again.
		savedViewport.clear();
		Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, savedViewport);
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		camera.setToOrtho(false, width, height);
		shapes.setProjectionMatrix(camera.combined);
		batch.setProjectionMatrix(camera.combined);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

		boolean stickHeld = stickPointer >= 0;
		float baseX = stickHeld ? stickOriginX : STICK_REST_X * unit;
		float baseY = stickHeld ? stickOriginY : STICK_REST_Y * unit;
		float radius = STICK_RADIUS * unit;
		float knobX = baseX + stickX * radius, knobY = baseY + stickY * radius;

		shapes.begin(ShapeRenderer.ShapeType.Filled);
		shapes.setColor(color.set(0, 0, 0, stickHeld ? 0.3f : 0.18f));
		shapes.circle(baseX, baseY, radius, 48);
		shapes.setColor(color.set(1, 1, 1, stickHeld ? 0.5f : 0.25f));
		shapes.circle(knobX, knobY, radius * 0.45f, 32);
		for (Button button : Button.values()) {
			boolean down = pressed.contains(button.action);
			if (button == Button.FIRE)
				shapes.setColor(color.set(0.85f, 0.1f, 0.1f, down ? 0.6f : 0.35f));
			else
				shapes.setColor(color.set(0, 0, 0, down ? 0.5f : 0.3f));
			shapes.circle(centerX(button), centerY(button), button.radius * unit, 40);
		}
		shapes.end();

		Gdx.gl.glLineWidth(Math.max(1, 2 * unit));
		shapes.begin(ShapeRenderer.ShapeType.Line);
		shapes.setColor(color.set(1, 1, 1, stickHeld ? 0.6f : 0.35f));
		shapes.circle(baseX, baseY, radius, 48);
		for (Button button : Button.values()) {
			boolean down = pressed.contains(button.action);
			shapes.setColor(color.set(1, 1, 1, down ? 0.9f : 0.55f));
			shapes.circle(centerX(button), centerY(button), button.radius * unit, 40);
		}
		shapes.end();
		Gdx.gl.glLineWidth(1);

		batch.begin();
		for (Button button : Button.values()) {
			// As big as fits in the circle, but the same size on every small button.
			float scale = unit * (button.radius >= 70 ? 1.9f : button.radius >= 50 ? 1.5f : 1.3f);
			font.getData().setScale(scale);
			layout.setText(font, button.label);
			float maxWidth = button.radius * unit * 1.7f;
			if (layout.width > maxWidth) {
				font.getData().setScale(scale * maxWidth / layout.width);
				layout.setText(font, button.label);
			}
			font.setColor(1, 1, 1, pressed.contains(button.action) ? 1f : 0.85f);
			font.draw(batch, layout, centerX(button) - layout.width / 2, centerY(button) + layout.height / 2);
		}
		batch.end();
		Gdx.gl.glViewport(savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3));
	}

	@Override
	public void dispose() {
		taps.uninstall();
		if (shapes != null) {
			shapes.dispose();
			batch.dispose();
			font.dispose();
		}
	}
}
