package com.juego.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.juego.UI.HUD;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.PlayerComponent;
import com.juego.managers.EntityFactory;
import com.juego.net.Protocol;
import com.juego.systems.BulletSystem;
import com.juego.systems.EnemySystem;
import com.juego.systems.HudSystem;
import com.juego.systems.NetworkSystem;
import com.juego.systems.PlayerSystem;
import com.juego.systems.RenderSystem;
import com.juego.systems.StatusSystem;
import com.juego.systems.WeaponSystem;

/**
 * One match: the ECS engine, its systems, the physics world and every entity
 * in it. Offline the player fights bots; online (see {@link GameSession})
 * there are no bots and a {@link NetworkSystem} syncs the match with a server.
 */
public class GameWorld {
	private static final String LEVEL = "Bank";
	private static final Vector3 OFFLINE_SPAWN = new Vector3(3, 5, 3);
	private static final int LOCAL_PLAYER = 1;

	public final Core game;
	public final GameSession session;
	public final boolean debug = false;
	private final Engine engine = new Engine();
	public BulletSystem bulletSystem;
	public RenderSystem renderSystem;
	public EntityFactory entityFactory;
	/** Null offline. */
	public NetworkSystem network;
	private PlayerSystem playerSystem;
	private WeaponSystem weaponSystem;
	private EnemySystem enemySystem;
	private StatusSystem statusSystem;
	private HudSystem hudSystem;
	private DebugDrawer debugDrawer;
	private boolean leaving;

	public GameWorld(Core game, GameSession session) {
		this.game = game;
		this.session = session;
		addSystems();
		entityFactory = new EntityFactory(bulletSystem);
		engine.addEntity(entityFactory.createLevel(LEVEL));
		Vector3 spawn = session.isMultiplayer() ? session.client.getSpawn() : OFFLINE_SPAWN;
		engine.addEntity(entityFactory.createPlayer(spawn.x, spawn.y, spawn.z, "ct",
				Assets.armory.create(Protocol.LOADOUT[0]), Assets.armory.create(Protocol.LOADOUT[1]), LOCAL_PLAYER));
	}

	private void addSystems() {
		// Update order matters: network first (it applies server teleports and moves
		// remote players), physics before the systems that read positions, and the
		// HUD last so it draws on top of the 3D scene.
		if (session.isMultiplayer())
			engine.addSystem(network = new NetworkSystem(this, session.client));
		engine.addSystem(renderSystem = new RenderSystem());
		engine.addSystem(bulletSystem = new BulletSystem());
		engine.addSystem(playerSystem = new PlayerSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(weaponSystem = new WeaponSystem(this, renderSystem.perspectiveCamera));
		if (!session.isMultiplayer()) {
			engine.addSystem(enemySystem = new EnemySystem(this));
			engine.addSystem(statusSystem = new StatusSystem(this));
		}
		engine.addSystem(hudSystem = new HudSystem(this));
		if (debug) {
			debugDrawer = new DebugDrawer();
			debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
			bulletSystem.collisionWorld.setDebugDrawer(debugDrawer);
		}
	}

	/** Whether the entity is the player controlled from this machine. */
	public boolean isLocalPlayer(Entity entity) {
		PlayerComponent player = entity.getComponent(PlayerComponent.class);
		return player != null && player.playerNumber == LOCAL_PLAYER;
	}

	public boolean isMultiplayer() {
		return session.isMultiplayer();
	}

	public boolean isHosting() {
		return session.isHosting();
	}

	public int getHostedPort() {
		return game.getHostedPort();
	}

	public PlayerSystem playerSystem() {
		return playerSystem;
	}

	/** Null until the local player exists. */
	public HUD getHud() {
		return hudSystem.getHud();
	}

	/** The connection dropped or the server closed: go back to the menu with the reason. */
	public void connectionLost(String reason) {
		if (leaving)
			return;
		leaving = true;
		game.leaveMatch(reason);
	}

	public void render(float delta) {
		// Offline, the pause menu freezes the world. Online the match goes on.
		if (!session.isMultiplayer()) {
			boolean running = !hudSystem.isPaused();
			bulletSystem.setProcessing(running);
			enemySystem.setProcessing(running);
			statusSystem.setProcessing(running);
		}
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
		session.dispose(); // disconnects from the server
		if (session.isHosting())
			game.stopHostedServer();
	}
}
