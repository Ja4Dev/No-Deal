package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.juego.components.AnimationComponent;
import com.juego.components.GunComponent;
import com.juego.components.ModelComponent;
import com.juego.core.Core;
import com.juego.graphics.ImpactMarks;
import com.juego.graphics.SkyDome;
import com.juego.graphics.Tracers;

public class RenderSystem extends EntitySystem {
	/**
	 * Drawn after every other system has updated (movement, mouse look, shots),
	 * so what's on screen is this frame's state, not last frame's.
	 */
	public static final int PRIORITY = 100;

	private ImmutableArray<Entity> entities;
	private ModelBatch batch;
	private Environment environment;
	private static final float FOV = 67F;
	public PerspectiveCamera perspectiveCamera, gunCamera;
	public Entity gun;
	public final ImpactMarks impactMarks;
	public final Tracers tracers;
	private final SkyDome sky = new SkyDome();

	public RenderSystem() {
		super(PRIORITY);
		perspectiveCamera = new PerspectiveCamera(FOV, Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		perspectiveCamera.far = 10000f;
		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f));
		batch = new ModelBatch();
		gunCamera = new PerspectiveCamera(FOV, Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		gunCamera.far = 100f;
		impactMarks = new ImpactMarks(perspectiveCamera);
		tracers = new Tracers(perspectiveCamera);
	}


	public void addedToEngine(Engine e) {
		entities = e.getEntitiesFor(Family.all(ModelComponent.class).get());
	}

	public void update(float delta) {
		drawModels(delta);
	}

	private void drawModels(float delta) {
		// Sky in its own pass: it never writes depth, so everything drawn after covers it.
		batch.begin(perspectiveCamera);
		sky.render(batch, perspectiveCamera);
		batch.end();
		batch.begin(perspectiveCamera);
		for (int i = 0; i < entities.size(); i++) {
			if (entities.get(i).getComponent(GunComponent.class) == null) {
				ModelComponent mod = entities.get(i).getComponent(ModelComponent.class);
				if (mod.visible)
					batch.render(mod.instance, environment);
			}
		}
		batch.end();
		impactMarks.render();
		tracers.render(delta);
		drawGun(delta);
	}

	private void drawGun(float delta) {
		if (gun == null || !gun.getComponent(ModelComponent.class).visible)
			return;
		Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
		batch.begin(gunCamera);
		batch.render(gun.getComponent(ModelComponent.class).instance);
		gun.getComponent(AnimationComponent.class).update(delta);
		batch.end();
	}
	
	public void resize(int width, int height) {
		perspectiveCamera.viewportHeight = height;
		perspectiveCamera.viewportWidth = width;
		gunCamera.viewportHeight = height;
		gunCamera.viewportWidth = width;
		}
	public void dispose() {
		impactMarks.dispose();
		tracers.dispose();
		sky.dispose();
		batch.dispose();
		batch = null;
		}



}
