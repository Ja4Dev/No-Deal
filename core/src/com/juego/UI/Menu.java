package com.juego.UI;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Controls;
import com.juego.utils.Display;

/**
 * A Scene2D stage at the game's virtual resolution, plus the textures it uses.
 * Menus keep their 16:9 shape (with bars on other screens); what is drawn over
 * a match (HUD, console, scoreboard) can stretch its world instead to reach
 * every edge of the screen, whatever its shape.
 */
public abstract class Menu implements Disposable {
	protected Stage stage;
	private final Array<String> texturePaths = new Array<>();

	public Menu() {
		this(false);
	}

	/**
	 * @param fillScreen the stage's world grows to the screen's shape (at least
	 *                   1280x720), so things placed by its edges stay by the
	 *                   screen's edges: lay them out from stage.getWidth() and
	 *                   getHeight() in {@link #layout()}
	 */
	public Menu(boolean fillScreen) {
		Viewport viewport = fillScreen ? new ExtendViewport(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT)
				: new FitViewport(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		stage = new Stage(viewport) {
			@Override
			public boolean scrolled(float amountX, float amountY) {
				// Whatever menu has the input, the wheel can still change weapons (see Controls).
				Controls.scrolled(amountY);
				return super.scrolled(amountX, amountY);
			}

			// The mouse comes in window coordinates; the game may be drawn scaled (see Display).
			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button) {
				return super.touchDown(Display.toRenderX(screenX), Display.toRenderY(screenY), pointer, button);
			}

			@Override
			public boolean touchUp(int screenX, int screenY, int pointer, int button) {
				return super.touchUp(Display.toRenderX(screenX), Display.toRenderY(screenY), pointer, button);
			}

			@Override
			public boolean touchDragged(int screenX, int screenY, int pointer) {
				return super.touchDragged(Display.toRenderX(screenX), Display.toRenderY(screenY), pointer);
			}

			@Override
			public boolean mouseMoved(int screenX, int screenY) {
				return super.mouseMoved(Display.toRenderX(screenX), Display.toRenderY(screenY));
			}
		};
	}

	/** Places what depends on the stage's size; called after every resize. */
	protected void layout() {
	}

	/** Loads a texture that is released automatically when the menu is disposed. */
	protected Texture loadTexture(String path) {
		texturePaths.add(path);
		return Assets.acquire(path, Texture.class);
	}

	public void resize(int width, int height) {
		stage.getViewport().update(width, height, true);
		layout();
	}

	@Override
	public void dispose() {
		stage.dispose();
		for (String path : texturePaths)
			Assets.release(path);
		texturePaths.clear();
	}
}
