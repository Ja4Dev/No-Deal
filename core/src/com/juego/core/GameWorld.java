package com.juego.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.guns.Gun;
import com.juego.guns.M4A1;
import com.juego.guns.PrimaryGun;
import com.juego.managers.EntityFactory;
import com.juego.systems.BulletSystem;
import com.juego.systems.EnemySystem;
import com.juego.systems.PlayerSystem;
import com.juego.systems.RenderSystem;
import com.juego.systems.StatusSystem;

public class GameWorld {

	private Engine engine;
	private Entity p1, map;
	public BulletSystem bulletSystem;
	public PlayerSystem playerSystem;
	public RenderSystem renderSystem;
	private EnemySystem enemySystem;
	private StatusSystem statusSystem;
	private DebugDrawer debugDrawer;
	public boolean debug = false;
	public Core game;
	public int jugador = 1;

	public GameWorld(Core game) {
		this.game = game;
		Bullet.init();
		setDebug();
		addSystems();
		addEntities();
	}

	private void setDebug() {
		if (debug) {
			debugDrawer = new DebugDrawer();
			debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
		}
	}

	private void addEntities() {
		loadLevel("Bank");
		createPlayers(3, 5, 3, "ct", new M4A1());
	}

	private void loadLevel(String mapName) {
		map = EntityFactory.loadScene(0, 0, 0, mapName);
		engine.addEntity(map);
	}

	private void createPlayers(float x, float y, float z, String team, PrimaryGun primaryGun) {
		p1 = EntityFactory.createPlayer(bulletSystem, x, y, z, team, primaryGun, 1);
		engine.addEntity(p1);

		// Gun view models are drawn by the RenderSystem with their own camera, so they
		// are not added to the engine. Both are built once and swapped when switching.
		PlayerComponent player = p1.getComponent(PlayerComponent.class);
		playerSystem.setGuns(createGunEntity(player.primaryGun), createGunEntity(player.secondaryGun));
	}

	private Entity createGunEntity(Gun gun) {
		return EntityFactory.loadGun(gun.modelPos[0], gun.modelPos[1], gun.modelPos[2], gun);
	}

	private void addSystems() {
		engine = new Engine();
		engine.addSystem(renderSystem = new RenderSystem());
		engine.addSystem(bulletSystem = new BulletSystem());
		engine.addSystem(playerSystem = new PlayerSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(enemySystem = new EnemySystem(this));
		engine.addSystem(statusSystem = new StatusSystem(this));
		if (debug)
			bulletSystem.collisionWorld.setDebugDrawer(this.debugDrawer);
	}

	public void render(float delta) {
		// While the pause menu is open the world freezes: no physics, no enemies.
		boolean running = !playerSystem.isPaused();
		bulletSystem.setProcessing(running);
		enemySystem.setProcessing(running);
		statusSystem.setProcessing(running);
		engine.update(delta);
		if (debug) {
			debugDrawer.begin(renderSystem.perspectiveCamera);
			bulletSystem.collisionWorld.debugDrawWorld();
			debugDrawer.end();
		}
	}

	public void resize(int width, int height) {
		renderSystem.resize(width, height);
		playerSystem.pauseMenu.resize(width, height);
	}

	public void dispose() {
		for (Entity entity : engine.getEntitiesFor(Family.one(BulletComponent.class, CharacterComponent.class).get()))
			bulletSystem.destroyBody(entity);
		engine.removeAllEntities();
		map.getComponent(ModelComponent.class).model.dispose();
		PlayerComponent player = p1.getComponent(PlayerComponent.class);
		player.primaryGun.dispose();
		player.secondaryGun.dispose();
		playerSystem.dispose();
		renderSystem.dispose();
		bulletSystem.dispose();
		if (debugDrawer != null)
			debugDrawer.dispose();
	}

	public void remove(Entity entity) {
		bulletSystem.destroyBody(entity);
		engine.removeEntity(entity);
	}

}
