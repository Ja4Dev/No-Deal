package com.juego.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.juego.guns.Armory;

/**
 * Single entry point for loading assets. Everything except streamed music goes
 * through one {@link AssetManager}, which reference-counts each file:
 * {@link #acquire} loads it (or reuses it if something else already holds it)
 * and every acquire must be paired with a {@link #release}.
 */
public final class Assets {
	public static final String SKIN = "skin/uiskin.json";
	private static final float ANISOTROPY = 8f;

	private static AssetManager manager;
	public static Skin skin;
	public static Armory armory;

	private Assets() {
	}

	public static void init() {
		manager = new AssetManager();
		skin = acquire(SKIN, Skin.class);
		armory = Armory.load(Gdx.files.internal(Armory.GUNS_FILE));
	}

	/** Loads an asset synchronously (or bumps its reference count) and returns it. */
	public static <T> T acquire(String path, Class<T> type) {
		manager.load(path, type);
		manager.finishLoadingAsset(path);
		return manager.get(path, type);
	}

	/**
	 * Loads a 3D model with mipmapped, trilinear and anisotropic texture
	 * filtering. Without mipmaps distant walls shimmer when the camera moves;
	 * without anisotropic filtering floors and ceilings seen at a grazing
	 * angle turn blurry.
	 */
	public static Model acquireModel(String path) {
		ModelLoader.ModelParameters params = new ModelLoader.ModelParameters();
		params.textureParameter.genMipMaps = true;
		params.textureParameter.minFilter = TextureFilter.MipMapLinearLinear;
		params.textureParameter.magFilter = TextureFilter.Linear;
		params.textureParameter.wrapU = TextureWrap.Repeat;
		params.textureParameter.wrapV = TextureWrap.Repeat;
		manager.load(path, Model.class, params);
		manager.finishLoadingAsset(path);
		Model model = manager.get(path, Model.class);
		for (Material material : model.materials) {
			for (Attribute attribute : material) {
				if (attribute instanceof TextureAttribute) {
					Texture texture = ((TextureAttribute) attribute).textureDescription.texture;
					texture.setAnisotropicFilter(ANISOTROPY);
				}
			}
		}
		return model;
	}

	public static void release(String path) {
		if (manager.isLoaded(path))
			manager.unload(path);
	}

	public static void dispose() {
		manager.dispose();
		manager = null;
		skin = null;
	}
}
