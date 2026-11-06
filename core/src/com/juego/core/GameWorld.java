package com.juego.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.badlogic.gdx.utils.Array;
import com.juego.UI.HUD;
import com.juego.UI.Scoreboard;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.PlayerComponent;
import com.juego.managers.EntityFactory;
import com.juego.guns.Gun;
import com.juego.match.Match;
import com.juego.net.GameClient;
import com.juego.net.GameServer;
import com.juego.net.Protocol;
import com.juego.systems.BulletSystem;
import com.juego.systems.DeathCamSystem;
import com.juego.systems.FootstepSystem;
import com.juego.systems.GroundWeaponSystem;
import com.juego.systems.HudSystem;
import com.juego.systems.NetworkSystem;
import com.juego.systems.PlayerSystem;
import com.juego.systems.RenderSystem;
import com.juego.systems.WeaponSystem;

/**
 * One match as this computer sees it: the ECS engine, its systems, the
 * physics world and every entity in it. The match itself is always run by a
 * server (see {@link GameSession}); a {@link NetworkSystem} keeps this world
 * in sync with it.
 */
public class GameWorld {
	private static final String LEVEL = "Bank";
	private static final int LOCAL_PLAYER = 1;

	public final Core game;
	public final GameSession session;
	public final boolean debug = false;
	private final Engine engine = new Engine();
	public BulletSystem bulletSystem;
	public RenderSystem renderSystem;
	public EntityFactory entityFactory;
	public NetworkSystem network;
	private PlayerSystem playerSystem;
	private WeaponSystem weaponSystem;
	private GroundWeaponSystem groundWeaponSystem;
	private FootstepSystem footstepSystem;
	private DeathCamSystem deathCamSystem;
	private HudSystem hudSystem;
	private DebugDrawer debugDrawer;
	private boolean leaving;

	public GameWorld(Core game, GameSession session) {
		this.game = game;
		this.session = session;
		addSystems();
		entityFactory = new EntityFactory(bulletSystem);
		engine.addEntity(entityFactory.createLevel(LEVEL));
		Vector3 spawn = session.client.getSpawn();
		Entity player = entityFactory.createPlayer(spawn.x, spawn.y, spawn.z, "ct", GameWorld::spawnLoadout,
				LOCAL_PLAYER);
		engine.addEntity(player);
	}

	/** The guns everyone spawns with. */
	private static Gun[] spawnLoadout() {
		Gun[] guns = new Gun[Protocol.LOADOUT.length];
		for (int i = 0; i < guns.length; i++)
			guns[i] = Assets.armory.create(Protocol.LOADOUT[i]);
		return guns;
	}

	private void addSystems() {
		// Update order matters: network first (it applies server teleports and moves
		// remote players), physics before the systems that read positions, and the
		// HUD last so it draws on top of the 3D scene.
		engine.addSystem(network = new NetworkSystem(this, session.client));
		engine.addSystem(renderSystem = new RenderSystem());
		engine.addSystem(bulletSystem = new BulletSystem());
		engine.addSystem(playerSystem = new PlayerSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(weaponSystem = new WeaponSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(groundWeaponSystem = new GroundWeaponSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(footstepSystem = new FootstepSystem(this, renderSystem.perspectiveCamera));
		// After the player and weapon systems: while dead it has the last word on the camera.
		engine.addSystem(deathCamSystem = new DeathCamSystem(this, renderSystem.perspectiveCamera));
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

	public boolean isHosting() {
		return session.isHosting();
	}

	public boolean isSingleplayer() {
		return session.isSingleplayer();
	}

	public int getHostedPort() {
		return game.getHostedPort();
	}

	/** The server running inside this game (singleplayer or "Crear partida"), or null when joining someone else's. */
	public GameServer hostedServer() {
		return game.getHostedServer();
	}

	public WeaponSystem weaponSystem() {
		return weaponSystem;
	}

	public PlayerSystem playerSystem() {
		return playerSystem;
	}

	public GroundWeaponSystem groundWeapons() {
		return groundWeaponSystem;
	}

	public DeathCamSystem deathCam() {
		return deathCamSystem;
	}

	/** Match time: the client's clock, which stands still while a singleplayer match is paused. */
	public long timeMs() {
		return session.clock.getAsLong();
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
		// In singleplayer the pause menu freezes the whole match, server included.
		if (session.isSingleplayer()) {
			boolean paused = hudSystem.isPaused();
			session.clock.setPaused(paused);
			bulletSystem.setProcessing(!paused);
		}
		engine.update(delta);
		if (debug) {
			debugDrawer.begin(renderSystem.perspectiveCamera);
			bulletSystem.collisionWorld.debugDrawWorld();
			debugDrawer.end();
		}
	}

	// ---- Match information for the HUD ----

	public Match.Phase getMatchPhase() {
		return session.client.getMatchPhase();
	}

	public long getMatchRemainingMs() {
		return session.client.getMatchRemainingMs();
	}

	/** Shooting is only allowed while a match is being played (not between matches). */
	public boolean canFight() {
		return getMatchPhase() == Match.Phase.PLAYING;
	}

	public String getScoreboardTitle() {
		int fragLimit = session.client.getFragLimit();
		String mode = session.isSingleplayer() ? "Un jugador contra bots" : "Deathmatch";
		return fragLimit > 0 ? mode + ": gana el primero en llegar a " + fragLimit + " bajas" : mode;
	}

	public void getScoreRows(Array<Scoreboard.Row> out) {
		for (GameClient.PlayerInfo p : session.client.getPlayers())
			out.add(new Scoreboard.Row().set(p.name, p.kills, p.deaths, p.assists, p.id == session.client.getLocalId()));
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
		groundWeaponSystem.dispose();
		footstepSystem.dispose();
		deathCamSystem.dispose();
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
