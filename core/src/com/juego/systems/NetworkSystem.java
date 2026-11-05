package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.juego.UI.HUD;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.guns.Gun;
import com.juego.net.GameClient;
import com.juego.net.NetUtils;
import com.juego.net.Protocol;

/**
 * Bridges the online match ({@link GameClient}) and the ECS world: sends the
 * local player's state and shots, applies the server's verdicts (health,
 * score, deaths, respawns, corrections) and moves the other players' models,
 * drawn {@link Protocol#INTERPOLATION_DELAY_MS} in the past so they glide
 * smoothly between snapshots. Runs first each frame.
 */
public class NetworkSystem extends EntitySystem implements EntityListener {
	private final GameWorld gameWorld;
	private final GameClient client;
	private Engine engine;
	private Entity player;
	private PlayerComponent playerComponent;
	private final IntMap<Entity> remotes = new IntMap<>();
	private final IntMap<String> names = new IntMap<>();
	private boolean greeted;

	private final Vector3 position = new Vector3();
	private final float[] yaw = new float[1];
	private final Quaternion rotation = new Quaternion();

	public NetworkSystem(GameWorld gameWorld, GameClient client) {
		this.gameWorld = gameWorld;
		this.client = client;
	}

	@Override
	public void addedToEngine(Engine engine) {
		this.engine = engine;
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
	}

	@Override
	public void entityAdded(Entity entity) {
		if (gameWorld.isLocalPlayer(entity)) {
			player = entity;
			playerComponent = entity.getComponent(PlayerComponent.class);
		}
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
	}

	@Override
	public void update(float delta) {
		client.update();
		if (client.getStatus() != GameClient.Status.CONNECTED) {
			gameWorld.connectionLost(client.getFailureReason());
			return;
		}
		if (player == null)
			return;
		greetOnce();
		syncRoster();
		GameClient.Event event;
		while ((event = client.pollEvent()) != null)
			handle(event);
		applyLocalStats();
		moveRemotePlayers();
		sendLocalState();
	}

	private HUD hud() {
		return gameWorld.getHud();
	}

	private void greetOnce() {
		if (greeted || hud() == null)
			return;
		greeted = true;
		if (gameWorld.isHosting()) {
			Array<String> ips = NetUtils.lanAddresses();
			hud().addFeedMessage(ips.isEmpty() ? "Partida creada (puerto " + gameWorld.getHostedPort() + ")"
					: "Partida creada: " + ips.first() + ":" + gameWorld.getHostedPort());
		} else {
			hud().addFeedMessage("Conectado a la partida");
		}
	}

	/** Creates a model for every player the client knows about, removes the ones that left. */
	private void syncRoster() {
		for (GameClient.PlayerInfo info : client.getPlayers()) {
			names.put(info.id, info.name);
			if (info.id != client.getLocalId() && !remotes.containsKey(info.id)) {
				Entity remote = gameWorld.entityFactory.createRemotePlayer(info.id);
				remotes.put(info.id, remote);
				engine.addEntity(remote);
			}
		}
		IntArray ids = remotes.keys().toArray(); // a copy: we remove while iterating
		for (int i = 0; i < ids.size; i++) {
			int id = ids.get(i);
			if (client.getPlayer(id) == null)
				engine.removeEntity(remotes.remove(id));
		}
	}

	private void handle(GameClient.Event event) {
		boolean me = event.playerId == client.getLocalId();
		switch (event.type) {
		case JOINED:
			feed(name(event.playerId) + " se unió");
			break;
		case LEFT:
			feed(name(event.playerId) + " salió");
			break;
		case KILL: {
			String victim = name(event.playerId);
			String line = event.killerId == Protocol.WORLD ? victim + " se cayó del mapa"
					: name(event.killerId) + " [" + event.gun + (event.headshot ? ", a la cabeza" : "") + "] " + victim;
			feed(line);
			if (me) {
				playerComponent.killedByServer();
				if (hud() != null)
					hud().setCenterMessage(event.killerId == Protocol.WORLD || event.killerId == client.getLocalId()
							? "Moriste" : "Te eliminó " + name(event.killerId));
			}
			break;
		}
		case RESPAWN:
			if (me) {
				playerComponent.respawn();
				gameWorld.playerSystem().teleport(event.position);
				if (hud() != null)
					hud().setCenterMessage(null);
			}
			break;
		case SET_POSITION:
			if (me)
				gameWorld.playerSystem().teleport(event.position);
			break;
		default:
			break;
		}
	}

	private String name(int id) {
		if (id == client.getLocalId())
			return "Vos";
		String name = names.get(id);
		return name != null ? name : "#" + id;
	}

	private void feed(String message) {
		if (hud() != null)
			hud().addFeedMessage(message);
	}

	private void applyLocalStats() {
		GameClient.PlayerInfo me = client.getPlayer(client.getLocalId());
		if (me == null)
			return;
		playerComponent.setFromServer(me.health, me.kills, me.deaths);
		if (!me.alive && !playerComponent.isDead())
			playerComponent.killedByServer();
	}

	private void moveRemotePlayers() {
		long renderTime = System.currentTimeMillis() - Protocol.INTERPOLATION_DELAY_MS;
		for (IntMap.Entry<Entity> entry : remotes.entries()) {
			GameClient.PlayerInfo info = client.getPlayer(entry.key);
			ModelComponent model = entry.value.getComponent(ModelComponent.class);
			boolean known = info != null && info.interpolate(renderTime, position, yaw);
			model.visible = known && info.alive;
			if (known) {
				rotation.setFromAxis(Vector3.Y, yaw[0]);
				model.instance.transform.set(position, rotation);
			}
		}
	}

	private void sendLocalState() {
		if (playerComponent.isDead())
			return;
		gameWorld.playerSystem().getPosition(position);
		int slot = playerComponent.selectedGun == playerComponent.primaryGun ? 0 : 1;
		client.sendState(position, playerComponent.yaw, playerComponent.pitch, slot);
	}

	public void sendFire(Gun gun, Vector3 origin, Vector3 direction) {
		int slot = gun == playerComponent.primaryGun ? 0 : 1;
		client.sendFire(slot, origin, direction);
	}
}
