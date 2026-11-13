package com.juego.UI;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Controls;

/** A Scene2D stage at the game's virtual resolution, plus the textures it uses. */
public abstract class Menu implements Disposable {
	protected Stage stage;
	private final Array<String> texturePaths = new Array<>();

	public Menu() {
		stage = new Stage(new FitViewport(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT)) {
			@Override
			public boolean scrolled(float amountX, float amountY) {
				// Whatever menu has the input, the wheel can still change weapons (see Controls).
				Controls.scrolled(amountY);
				return super.scrolled(amountX, amountY);
			}
		};
	}

	/** Loads a texture that is released automatically when the menu is disposed. */
	protected Texture loadTexture(String path) {
		texturePaths.add(path);
		return Assets.acquire(path, Texture.class);
	}

	public void resize(int width, int height) {
		stage.getViewport().update(width, height, true);
	}

	@Override
	public void dispose() {
		stage.dispose();
		for (String path : texturePaths)
			Assets.release(path);
		texturePaths.clear();
	}
}
