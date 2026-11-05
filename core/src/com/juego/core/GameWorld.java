package com.juego.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.PlayerComponent;
import com.juego.managers.EntityFactory;
import com.juego.systems.BulletSystem;
import com.juego.systems.EnemySystem;
import com.juego.systems.HudSystem;
import com.juego.systems.PlayerSystem;
import com.juego.systems.RenderSystem;
import com.juego.systems.StatusSystem;
import com.juego.systems.WeaponSystem;

/** One match: the ECS engine, its systems, the physics world and every entity in it. */
public class GameWorld {
	private static final String LEVEL = "Bank";
	private static final int LOCAL_PLAYER = 1;

	public final Core game;
	public final boolean debug = false;
	private final Engine engine = new Engine();
	public BulletSystem bulletSystem;
	public RenderSystem renderSystem;
	public EntityFactory entityFactory;
	private PlayerSystem playerSystem;
	private WeaponSystem weaponSystem;
	private EnemySystem enemySystem;
	private StatusSystem statusSystem;
	private HudSystem hudSystem;
	private DebugDrawer debugDrawer;

	public GameWorld(Core game) {
		this.game = game;
		Bullet.init();
		addSystems();
		entityFactory = new EntityFactory(bulletSystem);
		engine.addEntity(entityFactory.createLevel(LEVEL));
		engine.addEntity(entityFactory.createPlayer(3, 5, 3, "ct", Assets.armory.create("m4a1"),
				Assets.armory.create("magnum"), LOCAL_PLAYER));
	}

	private void addSystems() {
		// Update order matters: physics before the systems that read positions,
		// and the HUD last so it draws on top of the 3D scene.
		engine.addSystem(renderSystem = new RenderSystem());
		engine.addSystem(bulletSystem = new BulletSystem());
		engine.addSystem(playerSystem = new PlayerSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(weaponSystem = new WeaponSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(enemySystem = new EnemySystem(this));
		engine.addSystem(statusSystem = new StatusSystem(this));
		engine.addSystem(hudSystem = new HudSystem(this));
		if (debug) {
			debugDrawer = new DebugDrawer();
			debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
			bulletSystem.collisionWorld.setDebugDrawer(debugDrawer);
		}
	}

	/** Whether the entity is the player controlled from this machine (relevant once there is multiplayer). */
	public boolean isLocalPlayer(Entity entity) {
		PlayerComponent player = entity.getComponent(PlayerComponent.class);
		return player != null && player.playerNumber == LOCAL_PLAYER;
	}

	public void render(float delta) {
		// While the pause menu is open the world freezes: no physics, no enemies.
		boolean running = !hudSystem.isPaused();
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
		hudSystem.resize(width, height);
	}

	public void remove(Entity entity) {
		bulletSystem.destroyBody(entity);
		engine.removeEntity(entity);
	}

	public void dispose() {
		for (Entity entity : engine.getEntitiesFor(Family.one(BulletComponent.class, CharacterComponent.class).get()))
			bulletSystem.destroyBody(entity);
		engine.removeAllEntities();
		hudSystem.dispose();
		weaponSystem.dispose();
		entityFactory.dispose();
		renderSystem.dispose();
		bulletSystem.dispose();
		if (debugDrawer != null)
			debugDrawer.dispose();
	}
}
