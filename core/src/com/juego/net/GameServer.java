package com.juego.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.LongSupplier;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.AllHitsRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.juego.guns.Armory;
import com.juego.guns.GunDefinition;
import com.juego.utils.CharacterShape;

/**
 * Authoritative deathmatch server. The same class runs inside the host's game
 * ("Crear partida") and in the headless dedicated server.
 * <p>
 * Clients own their movement (validated by {@link MovementValidator}); the
 * server owns everything that decides the match: shots are re-traced here
 * against the level and the other players' hitboxes, and health, kills,
 * deaths and respawns are only changed here.
 * <p>
 * Not thread-safe: call {@link #update()} from a single thread, often (every
 * frame, or ~60 times per second).
 */
public class GameServer implements Disposable {
	private static final String TAG = "Server";
	private static final float MAX_FIRE_ORIGIN_DISTANCE = 3f;
	/** Fire rate is checked with some tolerance: shots can arrive bunched by network jitter. */
	private static final float FIRE_RATE_TOLERANCE = 0.75f;
	private static final long CORRECTION_COOLDOWN_MS = 500;
	private static final float KILL_PLANE_Y = -30f;

	public static class Config {
		public int port = Protocol.DEFAULT_PORT;
		public int maxPlayers = Protocol.MAX_PLAYERS;
		public String map = "Bank";
	}

	private static class ServerPlayer {
		final int id;
		final String name;
		final Connection connection;
		final MovementValidator validator = new MovementValidator();
		final Vector3 position = new Vector3();
		final long[] lastFireMs = { Long.MIN_VALUE / 2, Long.MIN_VALUE / 2 };
		final btCollisionObject hitbox = new btCollisionObject();
		/** Whether the hitbox is in the collision world (dead players are taken out). */
		boolean hitboxInWorld;
		float yaw, pitch;
		int slot, health = 100, kills, deaths, teleportId, lastSequence = -1;
		boolean alive = true;
		long respawnAtMs, lastCorrectionMs = Long.MIN_VALUE / 2;

		ServerPlayer(int id, String name, Connection connection) {
			this.id = id;
			this.name = name;
			this.connection = connection;
		}
	}

	private final Config config;
	private final Armory armory;
	private final LongSupplier clock;
	private final NetSocket socket;
	private final MapCollision map;
	private final Array<Vector3> spawnPoints = new Array<>();

	private final btDefaultCollisionConfiguration collisionConfig = new btDefaultCollisionConfiguration();
	private final btCollisionDispatcher dispatcher = new btCollisionDispatcher(collisionConfig);
	private final btDbvtBroadphase broadphase = new btDbvtBroadphase();
	private final btCollisionWorld world = new btCollisionWorld(dispatcher, broadphase, collisionConfig);
	private final btCollisionObject mapObject = new btCollisionObject();
	private final btCapsuleShape hitboxShape = new btCapsuleShape(CharacterShape.RADIUS, CharacterShape.CYLINDER);

	private final ObjectMap<InetSocketAddress, ServerPlayer> byAddress = new ObjectMap<>();
	private final IntMap<ServerPlayer> byId = new IntMap<>();
	/** Connections that were rejected: flushed once so the REJECT reaches them, then dropped. */
	private final Array<Connection> closing = new Array<>();
	private final Array<Connection.Message> received = new Array<>();
	private final Matrix4 tmpTransform = new Matrix4();
	private final Vector3 tmp = new Vector3();
	private long nextSnapshotMs;
	private int tick;

	public GameServer(Config config, FileHandleResolver files, Armory armory, LongSupplier clock) throws IOException {
		this.config = config;
		this.armory = armory;
		this.clock = clock;
		JsonValue mapData = new JsonReader().parse(files.resolve("data/maps/" + config.map + ".json"));
		for (JsonValue point : mapData.get("spawnPoints"))
			spawnPoints.add(new Vector3(point.getFloat(0), point.getFloat(1), point.getFloat(2)));
		map = new MapCollision(files.resolve(mapData.getString("model")));
		mapObject.setCollisionShape(map.shape);
		mapObject.setUserValue(Protocol.WORLD);
		world.addCollisionObject(mapObject);
		socket = new NetSocket(config.port);
		NetLog.info(TAG, "Listening on UDP port " + socket.getLocalPort() + " (map " + config.map + ", max "
				+ config.maxPlayers + " players)");
	}

	public int getPort() {
		return socket.getLocalPort();
	}

	public int getPlayerCount() {
		return byId.size;
	}

	/** One line per player, for the dedicated server console. */
	public String describePlayers() {
		StringBuilder sb = new StringBuilder();
		for (ServerPlayer p : byId.values())
			sb.append(String.format("  #%d %-16s %s  K %d / D %d  %s%n", p.id, p.name,
					p.alive ? p.health + " hp" : "dead", p.kills, p.deaths, p.connection.address));
		return sb.length() == 0 ? "  (no players)\n" : sb.toString();
	}

	public void update() {
		long now = clock.getAsLong();
		NetSocket.Datagram datagram;
		while ((datagram = socket.poll()) != null)
			handleDatagram(datagram, now);

		for (ServerPlayer p : byId.values().toArray()) {
			if (p.connection.isTimedOut(Protocol.TIMEOUT_MS)) {
				NetLog.info(TAG, p.name + " timed out");
				removePlayer(p);
			} else if (!p.alive && now >= p.respawnAtMs) {
				respawn(p, now);
			}
		}
		if (now >= nextSnapshotMs) {
			nextSnapshotMs = now + Protocol.SNAPSHOT_INTERVAL_MS;
			broadcastSnapshot();
		}
		for (ServerPlayer p : byId.values())
			send(p.connection);
		for (Connection c : closing)
			send(c);
		closing.clear();
	}

	private void send(Connection connection) {
		for (byte[] data : connection.flush())
			socket.send(connection.address, data);
	}

	// ---- incoming ----

	private void handleDatagram(NetSocket.Datagram datagram, long now) {
		ServerPlayer player = byAddress.get(datagram.from);
		Connection connection = player != null ? player.connection : new Connection(datagram.from, clock);
		received.clear();
		if (!connection.receive(java.nio.ByteBuffer.wrap(datagram.data), received))
			return;
		for (Connection.Message message : received) {
			if (player == null) {
				// Unknown address: the only thing it may say is HELLO.
				if (message.type == Protocol.HELLO)
					player = handleHello(connection, Messages.Hello.read(message.payload), now);
				if (player == null)
					return;
				continue;
			}
			switch (message.type) {
			case Protocol.PLAYER_STATE:
				handleState(player, Messages.PlayerState.read(message.payload), now);
				break;
			case Protocol.FIRE:
				handleFire(player, Messages.Fire.read(message.payload), now);
				break;
			case Protocol.DISCONNECT:
				NetLog.info(TAG, player.name + " left");
				removePlayer(player);
				return;
			default:
				break; // HELLO resent while the WELCOME was in flight, or unknown: ignore
			}
		}
	}

	private ServerPlayer handleHello(Connection connection, Messages.Hello hello, long now) {
		String reason = null;
		if (hello.version != Protocol.VERSION)
			reason = "Version incompatible (servidor v" + Protocol.VERSION + ", cliente v" + hello.version + ")";
		else if (byId.size >= config.maxPlayers)
			reason = "Servidor lleno (" + config.maxPlayers + " jugadores)";
		if (reason != null) {
			Messages.Reject reject = new Messages.Reject();
			reject.reason = reason;
			connection.sendReliable(reject);
			closing.add(connection);
			NetLog.info(TAG, "Rejected " + connection.address + ": " + reason);
			return null;
		}
		int id = 1;
		while (byId.containsKey(id))
			id++;
		ServerPlayer player = new ServerPlayer(id, Messages.sanitizeName(hello.name), connection);
		player.hitbox.setCollisionShape(hitboxShape);
		player.hitbox.setUserValue(id);
		byId.put(id, player);
		byAddress.put(connection.address, player);
		placeAtSpawn(player, now);

		Messages.Welcome welcome = new Messages.Welcome();
		welcome.playerId = id;
		welcome.teleportId = player.teleportId;
		welcome.map = config.map;
		welcome.spawn.set(player.position);
		connection.sendReliable(welcome);
		for (ServerPlayer other : byId.values()) {
			if (other != player)
				connection.sendReliable(joined(other));
		}
		broadcastReliable(joined(player), player);
		NetLog.info(TAG, player.name + " joined as #" + id + " from " + connection.address);
		return player;
	}

	private void handleState(ServerPlayer p, Messages.PlayerState state, long now) {
		if (!p.alive || state.teleportId != p.teleportId || state.sequence <= p.lastSequence)
			return; // dead, sent before the last teleport, or older than one we already have
		p.lastSequence = state.sequence;
		p.yaw = state.yaw;
		p.pitch = state.pitch;
		if (state.slot == 0 || state.slot == 1)
			p.slot = state.slot;
		if (state.position.y < KILL_PLANE_Y) {
			kill(p, null, false, "", now); // fell out of the map
			return;
		}
		if (p.validator.accept(state.position, state.clientTimeMs, now)) {
			setPosition(p, state.position);
		} else if (now - p.lastCorrectionMs >= CORRECTION_COOLDOWN_MS) {
			p.lastCorrectionMs = now;
			teleport(p, p.validator.getLastPosition(), Protocol.SET_POSITION, false, now);
		}
	}

	private void handleFire(ServerPlayer shooter, Messages.Fire fire, long now) {
		if (!shooter.alive || fire.slot < 0 || fire.slot >= Protocol.LOADOUT.length)
			return;
		GunDefinition gun = armory.get(Protocol.LOADOUT[fire.slot]);
		if (now - shooter.lastFireMs[fire.slot] < gun.fireDelayMs * FIRE_RATE_TOLERANCE)
			return; // faster than the gun can shoot
		if (fire.origin.dst(shooter.position) > MAX_FIRE_ORIGIN_DISTANCE || fire.direction.isZero())
			return;
		shooter.lastFireMs[fire.slot] = now;
		fire.direction.nor();
		Vector3 to = tmp.set(fire.direction).scl(gun.range).add(fire.origin);

		AllHitsRayResultCallback callback = new AllHitsRayResultCallback(fire.origin, to);
		try {
			world.rayTest(fire.origin, to, callback);
			int closest = -1;
			float closestFraction = 2f;
			for (int i = 0; i < callback.getCollisionObjects().size(); i++) {
				btCollisionObject object = callback.getCollisionObjects().atConst(i);
				if (object.getUserValue() == shooter.id)
					continue; // the ray starts inside the shooter's own capsule
				float fraction = callback.getHitFractions().atConst(i);
				if (fraction < closestFraction) {
					closestFraction = fraction;
					closest = i;
				}
			}
			if (closest < 0)
				return;
			int hitId = callback.getCollisionObjects().atConst(closest).getUserValue();
			ServerPlayer victim = byId.get(hitId);
			if (victim == null || !victim.alive)
				return; // hit the level
			Vector3 point = callback.getHitPointWorld().at(closest);
			boolean headshot = point.y >= victim.position.y + CharacterShape.HEAD_OFFSET;
			int damage = gun.damage * (headshot ? 3 : 1);
			victim.health -= damage;
			if (victim.health <= 0)
				kill(victim, shooter, headshot, gun.name, now);
		} finally {
			callback.dispose();
		}
	}

	// ---- game state ----

	private void kill(ServerPlayer victim, ServerPlayer killer, boolean headshot, String gun, long now) {
		victim.health = 0;
		victim.alive = false;
		victim.deaths++;
		victim.respawnAtMs = now + Protocol.RESPAWN_DELAY_MS;
		removeHitbox(victim);
		if (killer != null && killer != victim)
			killer.kills++;
		Messages.Kill kill = new Messages.Kill();
		kill.killerId = killer == null ? Protocol.WORLD : killer.id;
		kill.victimId = victim.id;
		kill.headshot = headshot;
		kill.gun = gun;
		broadcastReliable(kill, null);
		NetLog.info(TAG, (killer == null ? "The world" : killer.name) + " killed " + victim.name
				+ (headshot ? " (headshot)" : ""));
	}

	private void respawn(ServerPlayer p, long now) {
		p.alive = true;
		p.health = 100;
		placeAtSpawn(p, now);
		teleport(p, p.position, Protocol.RESPAWN, true, now);
	}

	private void placeAtSpawn(ServerPlayer p, long now) {
		// Pick the spawn point farthest from every other living player.
		Vector3 best = spawnPoints.first();
		float bestDistance = -1;
		for (Vector3 candidate : spawnPoints) {
			float nearest = Float.MAX_VALUE;
			for (ServerPlayer other : byId.values()) {
				if (other != p && other.alive)
					nearest = Math.min(nearest, other.position.dst(candidate));
			}
			if (nearest > bestDistance) {
				bestDistance = nearest;
				best = candidate;
			}
		}
		p.teleportId++;
		p.validator.reset(best, now);
		setPosition(p, best);
		if (p.alive && !p.hitboxInWorld) {
			world.addCollisionObject(p.hitbox);
			p.hitboxInWorld = true;
		}
	}

	private void removeHitbox(ServerPlayer p) {
		if (p.hitboxInWorld) {
			world.removeCollisionObject(p.hitbox);
			p.hitboxInWorld = false;
		}
	}

	private void setPosition(ServerPlayer p, Vector3 position) {
		p.position.set(position);
		p.hitbox.setWorldTransform(tmpTransform.setToTranslation(position));
		// The server never steps a simulation, which is what normally refreshes the
		// broadphase: without this, ray tests would still see the hitbox where it was.
		if (p.hitboxInWorld)
			world.updateSingleAabb(p.hitbox);
	}

	private void teleport(ServerPlayer p, Vector3 position, byte type, boolean broadcast, long now) {
		if (type == Protocol.SET_POSITION) {
			p.teleportId++;
			p.validator.reset(position, now);
		}
		Messages.Teleport message = new Messages.Teleport(type);
		message.playerId = p.id;
		message.teleportId = p.teleportId;
		message.position.set(position);
		if (broadcast)
			broadcastReliable(message, null);
		else
			p.connection.sendReliable(message);
	}

	private void removePlayer(ServerPlayer p) {
		byId.remove(p.id);
		byAddress.remove(p.connection.address);
		removeHitbox(p);
		p.hitbox.dispose();
		Messages.PlayerLeft left = new Messages.PlayerLeft();
		left.playerId = p.id;
		broadcastReliable(left, null);
	}

	private static Messages.PlayerJoined joined(ServerPlayer p) {
		Messages.PlayerJoined m = new Messages.PlayerJoined();
		m.playerId = p.id;
		m.name = p.name;
		return m;
	}

	private void broadcastReliable(Messages.Message message, ServerPlayer except) {
		for (ServerPlayer p : byId.values()) {
			if (p != except)
				p.connection.sendReliable(message);
		}
	}

	private void broadcastSnapshot() {
		if (byId.size == 0)
			return;
		Messages.Snapshot snapshot = new Messages.Snapshot();
		snapshot.tick = tick++;
		for (ServerPlayer p : byId.values()) {
			Messages.Snapshot.Entry e = new Messages.Snapshot.Entry();
			e.playerId = p.id;
			e.alive = p.alive;
			e.health = Math.max(0, p.health);
			e.kills = p.kills;
			e.deaths = p.deaths;
			e.slot = p.slot;
			e.position.set(p.position);
			e.yaw = p.yaw;
			e.pitch = p.pitch;
			snapshot.players.add(e);
		}
		for (ServerPlayer p : byId.values())
			p.connection.sendUnreliable(snapshot);
	}

	@Override
	public void dispose() {
		for (ServerPlayer p : byId.values()) {
			p.connection.sendUnreliable(new Messages.Disconnect());
			send(p.connection);
			removeHitbox(p);
			p.hitbox.dispose();
		}
		byId.clear();
		byAddress.clear();
		socket.close();
		world.removeCollisionObject(mapObject);
		mapObject.dispose();
		hitboxShape.dispose();
		world.dispose();
		broadphase.dispose();
		dispatcher.dispose();
		collisionConfig.dispose();
		map.dispose();
		NetLog.info(TAG, "Stopped");
	}
}
