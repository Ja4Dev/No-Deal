package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.juego.UI.HUD;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.graphics.SoldierModel;
import com.juego.guns.Gun;
import com.juego.net.GameClient;
import com.juego.net.Messages;
import com.juego.net.NetUtils;
import com.juego.net.Protocol;
import com.juego.utils.CharacterShape;
import com.juego.utils.Config;
import com.juego.utils.Controls;
import com.juego.utils.Platform;
import com.juego.utils.Text;

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
	private final float[] yaw = new float[2]; // yaw, pitch
	/** Walk cycle and aim of each other player's model. */
	private final IntMap<Animation> animations = new IntMap<>();

	private static class Animation {
		final SoldierModel.Pose pose;
		final Vector3 last = new Vector3();
		boolean known;
		float speed;

		Animation(ModelComponent model) {
			pose = new SoldierModel.Pose(model.instance);
		}
	}
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
		moveRemotePlayers(delta);
		sendLocalState();
	}

	private HUD hud() {
		return gameWorld.getHud();
	}

	private void greetOnce() {
		if (greeted || hud() == null)
			return;
		greeted = true;
		if (gameWorld.isSingleplayer()) {
			String console = Text.get(Platform.touch ? "feed.consoleButton" : "feed.consoleKey",
					Controls.promptName(Controls.Action.CONSOLE));
			hud().addFeedMessage(Text.get("feed.consoleHint", console));
		} else if (gameWorld.isHosting()) {
			Array<String> ips = NetUtils.lanAddresses();
			hud().addFeedMessage(ips.isEmpty() ? Text.get("feed.hosted", gameWorld.getHostedPort())
					: Text.get("feed.hostedAt", ips.first() + ":" + gameWorld.getHostedPort()));
		} else {
			hud().addFeedMessage(Text.get("feed.connected"));
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
			if (client.getPlayer(id) == null) {
				engine.removeEntity(remotes.remove(id));
				animations.remove(id);
			}
		}
	}

	private void handle(GameClient.Event event) {
		boolean me = event.playerId == client.getLocalId();
		switch (event.type) {
		case JOINED:
			feed(Text.get("feed.joined", name(event.playerId)));
			break;
		case LEFT:
			feed(Text.get("feed.left", name(event.playerId)));
			break;
		case KILL: {
			String victim = name(event.playerId);
			String line = event.killerId == Protocol.WORLD ? Text.get("feed.fell", victim)
					: name(event.killerId) + " [" + event.gun + (event.headshot ? Text.get("feed.headshot") : "") + "] " + victim;
			feed(line);
			if (me) {
				playerComponent.killedByServer();
				gameWorld.deathCam().onKilled(event.killerId, event.gun, event.headshot);
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
		case MATCH_END: {
			String message = event.playerId == Protocol.WORLD ? Text.get("center.matchOver")
					: event.playerId == client.getLocalId() ? Text.get("center.youWon")
							: Text.get("center.winner", name(event.playerId));
			feed(message);
			if (hud() != null)
				hud().setCenterMessage(message);
			break;
		}
		case MATCH_START:
			feed(Text.get("feed.newMatch"));
			if (hud() != null && !playerComponent.isDead())
				hud().setCenterMessage(null);
			break;
		case SHOT: {
			GameClient.PlayerInfo shooter = client.getPlayer(event.playerId);
			if (shooter != null && shooter.interpolate(client.now() - Protocol.INTERPOLATION_DELAY_MS,
					position, yaw))
				gameWorld.weaponSystem().playRemoteShot(event.gun, position, event.from, event.position);
			break;
		}
		case WEAPON_SPAWN:
			gameWorld.groundWeapons().onServerSpawn(event);
			break;
		case WEAPON_REMOVE:
			gameWorld.groundWeapons().onServerRemove(event);
			break;
		case DAMAGE:
			playerComponent.hurt(event.detail, event.position);
			break;
		case RENAMED:
			feed(me ? Text.get("feed.renamedMe", client.getPlayer(event.playerId).name)
					: Text.get("feed.renamed", event.gun, name(event.playerId)));
			break;
		case MOVEMENT:
			feed(Text.get("feed.movement", event.gun, event.value));
			break;
		case HIT:
			gameWorld.weaponSystem().showHit(event.position, event.from, event.headshot, me);
			break;
		default:
			break;
		}
	}

	private String name(int id) {
		if (id == client.getLocalId())
			return Text.get("you");
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

	private void moveRemotePlayers(float delta) {
		long renderTime = client.now() - Protocol.INTERPOLATION_DELAY_MS;
		for (IntMap.Entry<Entity> entry : remotes.entries()) {
			GameClient.PlayerInfo info = client.getPlayer(entry.key);
			ModelComponent model = entry.value.getComponent(ModelComponent.class);
			boolean known = info != null && info.interpolate(renderTime, position, yaw);
			model.visible = known && info.alive;
			Animation animation = animations.get(entry.key);
			if (animation == null)
				animations.put(entry.key, animation = new Animation(model));
			if (!model.visible) {
				animation.known = false;
				continue;
			}
			rotation.setFromAxis(Vector3.Y, yaw[0]);
			model.instance.transform.set(position, rotation);
			// How fast they move, from where they are drawn (smoothed: snapshots come 20 times a second).
			float dx = position.x - animation.last.x, dz = position.z - animation.last.z;
			float speed = animation.known && delta > 0 && dx * dx + dz * dz < 100
					? (float) Math.sqrt(dx * dx + dz * dz) / delta : 0;
			animation.speed += (speed - animation.speed) * Math.min(1, delta * 10);
			animation.last.set(position);
			animation.known = true;
			animation.pose.update(animation.speed, yaw[1], info.crouched, delta);
		}
	}

	/**
	 * Changes our name for everyone (the server tells the others) and keeps it
	 * for next time.
	 *
	 * @return the name, as it will be shown (cleaned up and cut to the maximum length)
	 */
	public String rename(String name) {
		String clean = Messages.sanitizeName(name);
		client.rename(clean);
		Config.playerName = clean;
		Config.save();
		return clean;
	}

	/**
	 * Where another player is drawn now (capsule center) and where they look.
	 *
	 * @return false if we know nothing about them
	 */
	public boolean remotePosition(int playerId, Vector3 outPosition, float[] outYaw) {
		GameClient.PlayerInfo info = client.getPlayer(playerId);
		return info != null && info.interpolate(client.now() - Protocol.INTERPOLATION_DELAY_MS, outPosition, outYaw);
	}

	private void sendLocalState() {
		if (playerComponent.isDead())
			return;
		gameWorld.playerSystem().getPosition(position);
		Gun gun = playerComponent.selectedGun();
		client.setCrouched(playerComponent.crouched);
		client.sendState(position, playerComponent.yaw, playerComponent.pitch, playerComponent.selectedSlot,
				gun == null ? 0 : gun.getMagazine(), gun == null ? 0 : gun.getReserve());
	}

	/**
	 * Whether the ray passes through another player's body before {@code distance}.
	 * Remote players have no local physics, so their capsule is approximated by
	 * two spheres (head and legs).
	 */
	public boolean isRemotePlayerCloserThan(Ray ray, float distance) {
		for (IntMap.Entry<Entity> entry : remotes.entries()) {
			ModelComponent model = entry.value.getComponent(ModelComponent.class);
			if (!model.visible)
				continue;
			model.instance.transform.getTranslation(sphereCenter);
			for (float offset : SPHERE_OFFSETS) {
				sphereCenter2.set(sphereCenter).add(0, offset, 0);
				if (Intersector.intersectRaySphere(ray, sphereCenter2, CharacterShape.HITBOX_RADIUS, intersection)
						&& intersection.dst(ray.origin) < distance)
					return true;
			}
		}
		return false;
	}

	private static final float[] SPHERE_OFFSETS = { -CharacterShape.HITBOX_CYLINDER / 2, 0,
			CharacterShape.HITBOX_CYLINDER / 2 };
	private final Vector3 sphereCenter = new Vector3(), sphereCenter2 = new Vector3(), intersection = new Vector3();

	public void sendFire(Gun gun, Vector3 origin, Vector3 direction) {
		client.sendFire(gun.def.slot.ordinal(), origin, direction);
	}

	/** Asks the server to throw the gun in {@code slot}; it leaves our hands when the server confirms. */
	public void requestDrop(int slot, Gun gun) {
		client.sendDrop(slot, gun.getMagazine(), gun.getReserve());
	}

	/** Asks the server for a gun on the ground; {@code replaced} (may be null) is thrown in its place. */
	public void requestPickup(int weaponId, Gun replaced) {
		client.sendPickup(weaponId, replaced == null ? 0 : replaced.getMagazine(),
				replaced == null ? 0 : replaced.getReserve());
	}
}
