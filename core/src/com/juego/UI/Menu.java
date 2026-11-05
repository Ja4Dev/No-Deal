package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.juego.core.Core;

public abstract class Menu implements Disposable {
	protected Stage stage;
	private final Array<Texture> textures = new Array<>();

	public Menu() {
		stage = new Stage(new FitViewport(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT));
	}

	/** Loads a texture that will be released automatically when the menu is disposed. */
	protected Texture loadTexture(String path) {
		Texture texture = new Texture(Gdx.files.internal(path));
		textures.add(texture);
		return texture;
	}

	public void resize(int width, int height) {
		stage.getViewport().update(width, height, true);
	}

	@Override
	public void dispose() {
		stage.dispose();
		for (Texture texture : textures)
			texture.dispose();
		textures.clear();
	}

}
