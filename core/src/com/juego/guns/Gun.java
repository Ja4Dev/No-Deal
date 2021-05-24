package com.juego.guns;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.JsonReader;

public abstract class Gun {
	public int damage;
	public int magazine;
	public boolean auto;
	public float range;
	public int delay;
	public int magazineSize;
	public Sound sound;
	public boolean sight;
	private ModelLoader modelLoader;
	protected ModelData modelData;
	private Model model;
	public float[] modelPos = new float[3];
	public float recoil;
	
	public Gun(int damage, int magazine, int magazineSize, boolean auto, float range, int delay, Sound sound, boolean sight, String modelName, float recoil) {
		modelLoader = new G3dModelLoader(new JsonReader());
		this.damage = damage;
		this.magazine = magazine;
		this.magazineSize = magazineSize;
		this.auto = auto;
		this.range = range;
		this.delay = delay;
		this.sound = sound;
		this.sight = sight;
		modelData = modelLoader.loadModelData(Gdx.files.internal("models/" + modelName));
		this.recoil = recoil;
	}

	public Model getModel() {
		Model model = new Model(modelData, new TextureProvider.FileTextureProvider());
		return model;
	}
	
}
