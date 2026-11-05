package com.juego.systems;

import java.util.Random;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.juego.components.CharacterComponent;
import com.juego.components.EnemyComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.managers.EntityFactory;

public class EnemySystem extends EntitySystem implements EntityListener {
	private static final float ENEMY_SPEED = 3f;

	private ImmutableArray<Entity> entities;
	private Entity player;
	private Engine engine;
	private final GameWorld gameWorld;
	private final Random random = new Random();
	private final ComponentMapper<CharacterComponent> cm = ComponentMapper.getFor(CharacterComponent.class);
	private final ComponentMapper<ModelComponent> mm = ComponentMapper.getFor(ModelComponent.class);
	// Reused every frame to avoid allocating garbage per enemy.
	private final Quaternion rotation = new Quaternion();
	private final Matrix4 ghostTransform = new Matrix4();
	private final Vector3 playerPosition = new Vector3();
	private final Vector3 enemyPosition = new Vector3();

	public EnemySystem(GameWorld gameWorld) {
		this.gameWorld = gameWorld;
	}

	@Override
	public void addedToEngine(Engine e) {
		entities = e.getEntitiesFor(Family.all(EnemyComponent.class, CharacterComponent.class).get());
		e.addEntityListener(Family.one(PlayerComponent.class).get(), this);
		this.engine = e;
	}

	@Override
	public void update(float delta) {
		if (player == null)
			return;
		if (entities.size() < 1) {
			engine.addEntity(EntityFactory.createEnemy(gameWorld.bulletSystem, random.nextInt(40) - 20, 10,
					random.nextInt(40) - 20));
		}
		mm.get(player).instance.transform.getTranslation(playerPosition);
		float stepSpeed = ENEMY_SPEED * gameWorld.bulletSystem.fixedTimeStep;
		for (Entity e : entities) {
			CharacterComponent character = cm.get(e);
			if (character.characterController == null)
				continue; // already destroyed, waiting to be removed from the engine
			ModelComponent mod = mm.get(e);
			character.ghostObject.getWorldTransform(ghostTransform);
			ghostTransform.getTranslation(enemyPosition);

			// Face the player (yaw only)...
			float theta = MathUtils.atan2(playerPosition.x - enemyPosition.x, playerPosition.z - enemyPosition.z);
			rotation.setFromAxis(0, 1, 0, theta * MathUtils.radiansToDegrees + 90);
			mod.instance.transform.set(enemyPosition, rotation);

			// ...and walk straight at them. walkDirection is a per-physics-step displacement.
			character.characterDirection.set(-1, 0, 0).rot(mod.instance.transform);
			character.walkDirection.set(character.characterDirection).scl(stepSpeed);
			character.characterController.setWalkDirection(character.walkDirection);
		}
	}

	@Override
	public void entityAdded(Entity entity) {
		player = entity;
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
	}
}
