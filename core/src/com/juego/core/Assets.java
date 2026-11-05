package com.juego.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class Assets {
	public static Skin skin;

	public Assets() {
		skin = new Skin();
		FileHandle fileHandle = Gdx.files.internal("skin/uiskin.json");
		FileHandle atlasFile = fileHandle.sibling("uiskin.atlas");
		if (atlasFile.exists()) {
			skin.addRegions(new TextureAtlas(atlasFile));
		}
		skin.load(fileHandle);
	}

	/**
	 * Texture provider for 3D models: mipmapped with trilinear + anisotropic
	 * filtering. Without mipmaps distant walls shimmer when the camera moves;
	 * without anisotropic filtering, floors and ceilings seen at a grazing
	 * angle turn blurry.
	 */
	public static TextureProvider modelTextureProvider() {
		return new TextureProvider.FileTextureProvider(TextureFilter.MipMapLinearLinear, TextureFilter.Linear,
				TextureWrap.Repeat, TextureWrap.Repeat, true) {
			@Override
			public Texture load(String fileName) {
				Texture texture = super.load(fileName);
				texture.setAnisotropicFilter(8f);
				return texture;
			}
		};
	}

	public static void dispose() {
		skin.dispose();
	}
}
