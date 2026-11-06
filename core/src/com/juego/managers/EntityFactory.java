package com.juego.managers;

import java.util.function.Supplier;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.juego.bullet.CharacterBody;
import com.juego.bullet.MotionState;
import com.juego.components.AnimationComponent;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.GroundWeaponComponent;
import com.juego.components.GunComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.RemotePlayerComponent;
import com.juego.core.Assets;
import com.juego.graphics.SoldierModel;
import com.juego.guns.GroundWeapons;
import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;
import com.juego.systems.BulletSystem;
import com.juego.utils.CharacterShape;

/**
 * Builds the game's entities. Owned by a GameWorld: models it loads are
 * released, and the placeholder character model destroyed, on {@link #dispose()}.
 */
public class EntityFactory implements Disposable {
	/** Character capsule: radius 2, cylinder 2 -> 6 units tall, centered on its origin. */
	public static final float CHARACTER_RADIUS = CharacterShape.RADIUS;
	public static final float CHARACTER_CYLINDER = CharacterShape.CYLINDER;

	private final BulletSystem bulletSystem;
	private final Model characterModel;
	private final Array<String> acquired = new Array<>();
	/** Gun models already loaded, by path: every gun on the ground shares its model. */
	private final ObjectMap<String, Model> gunModels = new ObjectMap<>();
	/** Bounds of each gun model, by path: {center, dimensions}. */
	private final ObjectMap<String, Vector3[]> gunBounds = new ObjectMap<>();

	public EntityFactory(BulletSystem bulletSystem) {
		this.bulletSystem = bulletSystem;
		characterModel = SoldierModel.build();
	}

	private <T> T acquire(String path, Class<T> type) {
		acquired.add(path);
		return Assets.acquire(path, type);
	}

	private Model acquireModel(String path) {
		acquired.add(path);
		return Assets.acquireModel(path);
	}

	private Entity createCharacter(float x, float y, float z) {
		Entity entity = new Entity();
		ModelComponent modelComponent = new ModelComponent(characterModel, x, y, z);
		entity.add(modelComponent);
		CharacterComponent characterComponent = new CharacterComponent();
		CharacterBody body = new CharacterBody(modelComponent.instance.transform);
		characterComponent.ghostShape = body.shape;
		characterComponent.ghostObject = body.ghost;
		characterComponent.characterController = body.controller;
		characterComponent.ghostObject.userData = entity;
		entity.add(characterComponent);
		body.addTo(bulletSystem.collisionWorld);
		return entity;
	}

	/** @param loadout makes the guns the player spawns (and respawns) with */
	public Entity createPlayer(float x, float y, float z, String team, Supplier<Gun[]> loadout, int playerNumber) {
		Entity entity = createCharacter(x, y, z);
		entity.add(new PlayerComponent(team, loadout, playerNumber));
		return entity;
	}

	/** Another online player: just a model, moved by the NetworkSystem from server snapshots. */
	public Entity createRemotePlayer(int playerId) {
		Entity entity = new Entity();
		ModelComponent model = new ModelComponent(characterModel, 0, -1000, 0);
		model.visible = false; // until its first snapshot arrives
		SoldierModel.paint(model.instance, playerId);
		entity.add(model);
		entity.add(new RemotePlayerComponent(playerId));
		return entity;
	}

	/** First-person view model of a gun. Drawn by the RenderSystem with its own camera. */
	public Entity createGunView(GunDefinition def) {
		float[] offset = def.viewOffset;
		ModelComponent modelComponent = new ModelComponent(gunModel(def), offset[0], offset[1], offset[2]);
		Entity gunEntity = new Entity();
		gunEntity.add(modelComponent);
		gunEntity.add(new GunComponent());
		gunEntity.add(new AnimationComponent(modelComponent.instance));
		return gunEntity;
	}

	/** A gun lying on the ground (or being thrown there). */
	public Entity createGroundWeapon(GroundWeapons.Weapon weapon, long nowMs) {
		Model model = gunModel(weapon.def);
		Vector3[] bounds = gunBounds.get(weapon.def.model);
		if (bounds == null) {
			BoundingBox box = new ModelInstance(model).calculateBoundingBox(new BoundingBox());
			bounds = new Vector3[] { box.getCenter(new Vector3()), box.getDimensions(new Vector3()) };
			gunBounds.put(weapon.def.model, bounds);
		}
		Entity entity = new Entity();
		ModelComponent modelComponent = new ModelComponent(model, weapon.position.x, weapon.position.y,
				weapon.position.z);
		entity.add(modelComponent);
		GroundWeaponComponent ground = new GroundWeaponComponent(weapon, nowMs, bounds[0], bounds[1].z,
				bounds[1].x);
		ground.update(modelComponent.instance.transform, nowMs);
		entity.add(ground);
		return entity;
	}

	private Model gunModel(GunDefinition def) {
		Model model = gunModels.get(def.model);
		if (model == null) {
			model = acquireModel(def.model);
			gunModels.put(def.model, model);
		}
		return model;
	}

	/** A level: its model plus a static triangle-mesh collider built from the model's nodes. */
	public Entity createLevel(String mapName) {
		Model model = acquireModel("maps/" + mapName + ".g3dj");
		Entity entity = new Entity();
		ModelComponent modelComponent = new ModelComponent(model, 0, 0, 0);
		entity.add(modelComponent);
		BulletComponent bulletComponent = new BulletComponent();
		btCollisionShape shape = Bullet.obtainStaticNodeShape(model.nodes);
		bulletComponent.bodyInfo = new btRigidBody.btRigidBodyConstructionInfo(0, null, shape, Vector3.Zero);
		bulletComponent.body = new btRigidBody(bulletComponent.bodyInfo);
		bulletComponent.body.userData = entity;
		bulletComponent.motionState = new MotionState(modelComponent.instance.transform);
		((btRigidBody) bulletComponent.body).setMotionState(bulletComponent.motionState);
		entity.add(bulletComponent);
		return entity;
	}

	@Override
	public void dispose() {
		characterModel.dispose();
		for (String path : acquired)
			Assets.release(path);
		acquired.clear();
	}
}
