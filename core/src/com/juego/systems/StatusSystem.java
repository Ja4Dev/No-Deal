package com.juego.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.juego.components.StatusComponent;
import com.juego.core.GameWorld;

/** Removes dead entities from the engine and the physics world. */
public class StatusSystem extends EntitySystem {
	private ImmutableArray<Entity> entities;
	private final GameWorld gameWorld;
	private final ComponentMapper<StatusComponent> sm = ComponentMapper.getFor(StatusComponent.class);

	public StatusSystem(GameWorld gameWorld) {
		this.gameWorld = gameWorld;
	}

	@Override
	public void addedToEngine(Engine engine) {
		entities = engine.getEntitiesFor(Family.all(StatusComponent.class).get());
	}

	@Override
	public void update(float deltaTime) {
		for (Entity entity : entities) {
			if (!sm.get(entity).alive)
				gameWorld.remove(entity);
		}
	}
}
