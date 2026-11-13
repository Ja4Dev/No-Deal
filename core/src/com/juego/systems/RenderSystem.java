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
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.math.Vector3;
import com.juego.components.BulletComponent;
import com.juego.components.AnimationComponent;
import com.juego.components.GunComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.Core;
import com.juego.graphics.BloodEffects;
import com.juego.graphics.ImpactMarks;
import com.juego.graphics.SkyDome;
import com.juego.graphics.Tracers;
import com.juego.utils.Config;
import com.juego.utils.Display;
import com.juego.utils.Video;

public class RenderSystem extends EntitySystem {
	/**
	 * Drawn after every other system has updated (movement, mouse look, shots),
	 * so what's on screen is this frame's state, not last frame's.
	 */
	public static final int PRIORITY = 100;

	private ImmutableArray<Entity> entities;
	private ModelBatch batch;
	private Environment environment;
	/** The guns in hand keep their own field of view whatever the player's, like CS's viewmodel. */
	private static final float GUN_FOV = 67F;
	/**
	 * Shadows (see Video.Shadows): a light from above, a little slanted, whose
	 * shadow map holds the players and the guns on the floor. The level itself
	 * casts none: it has ceilings, and a sun would leave every room in shadow.
	 */
	@SuppressWarnings("deprecation")
	private DirectionalShadowLight shadowLight;
	private ModelBatch shadowBatch;
	private Video.Shadows shadows = Video.Shadows.OFF;
	private static final Vector3 LIGHT_DIRECTION = new Vector3(-0.35f, -1f, -0.25f).nor();
	/**
	 * With shadows the light is split between the light from above and the
	 * ambient light, so the floor is about as bright as without them (ambient
	 * only) and a shadow is a little darker.
	 */
	private static final float LIGHT_STRENGTH = 0.25f, AMBIENT = 0.5f, AMBIENT_WITH_SHADOWS = 0.4f;
	private final ColorAttribute ambient = new ColorAttribute(ColorAttribute.AmbientLight, AMBIENT, AMBIENT, AMBIENT, 1f);
	public PerspectiveCamera perspectiveCamera, gunCamera;
	public Entity gun;
	public final ImpactMarks impactMarks;
	public final Tracers tracers;
	public final BloodEffects blood;
	private final SkyDome sky = new SkyDome();

	public RenderSystem() {
		super(PRIORITY);
		perspectiveCamera = new PerspectiveCamera(Video.verticalFov(), Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		perspectiveCamera.far = 10000f;
		environment = new Environment();
		environment.set(ambient);
		batch = new ModelBatch();
		gunCamera = new PerspectiveCamera(GUN_FOV, Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		gunCamera.far = 100f;
		impactMarks = new ImpactMarks(perspectiveCamera);
		tracers = new Tracers(perspectiveCamera);
		blood = new BloodEffects(perspectiveCamera);
	}


	public void addedToEngine(Engine e) {
		entities = e.getEntitiesFor(Family.all(ModelComponent.class).get());
	}

	public void update(float delta) {
		drawModels(delta);
	}

	/** Follows the setting: makes, remakes or removes the shadow light. */
	@SuppressWarnings("deprecation")
	private void syncShadows() {
		if (Config.shadows == shadows)
			return;
		disposeShadows();
		shadows = Config.shadows;
		if (shadows == Video.Shadows.OFF)
			return;
		shadowLight = new DirectionalShadowLight(shadows.mapSize, shadows.mapSize, shadows.area, shadows.area, 1f, 200f);
		shadowLight.set(LIGHT_STRENGTH, LIGHT_STRENGTH, LIGHT_STRENGTH, LIGHT_DIRECTION);
		environment.add(shadowLight);
		environment.shadowMap = shadowLight;
		ambient.color.set(AMBIENT_WITH_SHADOWS, AMBIENT_WITH_SHADOWS, AMBIENT_WITH_SHADOWS, 1f);
		shadowBatch = new ModelBatch(new DepthShaderProvider());
	}

	@SuppressWarnings("deprecation")
	private void disposeShadows() {
		if (shadowLight == null)
			return;
		environment.remove(shadowLight);
		environment.shadowMap = null;
		ambient.color.set(AMBIENT, AMBIENT, AMBIENT, 1f);
		shadowLight.dispose();
		shadowBatch.dispose();
		shadowLight = null;
		shadowBatch = null;
	}

	/** Draws what casts shadows (players, our own body included, and guns on the floor) into the shadow map. */
	@SuppressWarnings("deprecation")
	private void drawShadowMap() {
		shadowLight.begin(perspectiveCamera.position, LIGHT_DIRECTION);
		shadowBatch.begin(shadowLight.getCamera());
		for (int i = 0; i < entities.size(); i++) {
			Entity entity = entities.get(i);
			if (entity.getComponent(GunComponent.class) != null || entity.getComponent(BulletComponent.class) != null)
				continue;
			ModelComponent mod = entity.getComponent(ModelComponent.class);
			// Our own body isn't drawn (the camera is in its head), but its shadow is, while alive.
			PlayerComponent player = entity.getComponent(PlayerComponent.class);
			if (mod.visible || (player != null && !player.isDead()))
				shadowBatch.render(mod.instance);
		}
		shadowBatch.end();
		shadowLight.end();
		Display.resume(); // back to the scaled image, if the game is drawn scaled
	}

	private void drawModels(float delta) {
		syncShadows();
		if (shadowLight != null)
			drawShadowMap();
		// The whole window: the HUD's stages leave a letterboxed 16:9 viewport behind, which on a
		// wider screen (phones, a resized window) would draw the scene with black bars at the sides.
		Gdx.gl.glViewport(0, 0, Display.backBufferWidth(), Display.backBufferHeight());
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
		blood.render(delta);
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
		blood.dispose();
		sky.dispose();
		disposeShadows();
		batch.dispose();
		batch = null;
		}



}
