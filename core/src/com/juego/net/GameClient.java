package com.juego.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.juego.match.Match;

/**
 * Client side of a multiplayer match. Holds a model of the match (who is
 * connected, their latest state, interpolation samples) plus a queue of
 * events, and the game reads both every frame. Pulling state instead of
 * pushing callbacks means nothing is lost while the game switches from the
 * connect screen to the match.
 * <p>
 * Not thread-safe: call {@link #update()} from the game thread.
 */
public class GameClient implements Disposable {
	public enum Status {
		CONNECTING, CONNECTED, REJECTED, DISCONNECTED
	}

	/** What the client knows about a player, including the local one (stats come from the server). */
	public static class PlayerInfo {
		public final int id;
		/** Can change mid-match (see {@link GameClient#rename}). */
		public String name;
		public boolean alive = true;
		public int health = 100, kills, deaths, assists, slot;
		public boolean crouched;
		private final Array<float[]> samples = new Array<>(); // {timeMs, x, y, z, yaw, pitch}

		PlayerInfo(int id, String name) {
			this.id = id;
			this.name = name;
		}

		void addSample(long timeMs, Vector3 position, float yaw, float pitch) {
			samples.add(new float[] { timeMs, position.x, position.y, position.z, yaw, pitch });
			if (samples.size > 32)
				samples.removeIndex(0);
		}

		void clearSamples() {
			samples.clear();
		}

		/**
		 * Position and yaw (and pitch, if {@code outYaw} has room for it) at
		 * {@code renderTimeMs}, blended between the two snapshots around it (or
		 * the newest one if there is nothing newer).
		 *
		 * @return false if there is no data yet
		 */
		public boolean interpolate(long renderTimeMs, Vector3 outPosition, float[] outYaw) {
			if (samples.size == 0)
				return false;
			float[] older = samples.first(), newer = samples.peek();
			for (int i = samples.size - 1; i >= 0; i--) {
				if (samples.get(i)[0] <= renderTimeMs) {
					older = samples.get(i);
					newer = i + 1 < samples.size ? samples.get(i + 1) : older;
					break;
				}
			}
			float span = newer[0] - older[0];
			float alpha = span <= 0 ? 1 : MathUtils.clamp((renderTimeMs - older[0]) / span, 0, 1);
			outPosition.set(older[1], older[2], older[3]).lerp(tmp.set(newer[1], newer[2], newer[3]), alpha);
			outYaw[0] = lerpAngle(older[4], newer[4], alpha);
			if (outYaw.length > 1)
				outYaw[1] = older[5] + (newer[5] - older[5]) * alpha; // pitch
			return true;
		}

		private final Vector3 tmp = new Vector3();

		private static float lerpAngle(float from, float to, float alpha) {
			float delta = ((to - from) % 360 + 540) % 360 - 180; // shortest way around
			return from + delta * alpha;
		}
	}

	/** Something that happened in the match, for the game to react to once. */
	public static class Event {
		public enum Type {
			JOINED, LEFT, KILL, RESPAWN, SET_POSITION, MATCH_END, MATCH_START, SHOT, WEAPON_SPAWN, WEAPON_REMOVE,
			DAMAGE, HIT, RENAMED
		}

		public final Type type;
		/** The player it is about. WEAPON_SPAWN: who dropped it. WEAPON_REMOVE: who took it. */
		public int playerId, killerId;
		public boolean headshot;
		/** MATCH_END: ordinal of Match.EndReason. DAMAGE: amount. */
		public int detail;
		/** KILL, SHOT, WEAPON_SPAWN: gun id or name. RENAMED: the old name. */
		public String gun = "";
		/** Where it happens. SHOT: where the bullet stopped. DAMAGE: where the shot came from. HIT: where it hit. */
		public final Vector3 position = new Vector3();
		/** SHOT: where the bullet started. WEAPON_SPAWN: where the gun was thrown from. HIT: the bullet's direction. */
		public final Vector3 from = new Vector3();
		public int weaponId, magazine, reserve;
		public float yaw;

		Event(Type type) {
			this.type = type;
		}
	}

	private final LongSupplier clock;
	private Transport socket;
	private Connection connection;
	private Status status = Status.DISCONNECTED;
	private String failureReason = "";
	private long connectStartMs, nextStateMs;
	private int localId, teleportId, sequence;
	private boolean crouched;
	private String mapName = "";
	private String localName = "";
	private int fragLimit;
	private final Vector3 spawn = new Vector3();
	private final IntMap<PlayerInfo> players = new IntMap<>();
	private final ArrayDeque<Event> events = new ArrayDeque<>();
	/** "Old -> New" for every rename since the match started, newest last. */
	private final Array<String> renames = new Array<>();
	private static final int MAX_RENAMES_SHOWN = 4;
	private final Array<Connection.Message> received = new Array<>();
	private final Messages.PlayerState state = new Messages.PlayerState();
	private Match.Phase matchPhase = Match.Phase.PLAYING;
	private long matchRemainingMs, matchSampleMs;

	public GameClient(LongSupplier clock) {
		this.clock = clock;
	}

	/** The client's clock: snapshots are timed with it, so interpolate with it too. */
	public long now() {
		return clock.getAsLong();
	}

	public void connect(String host, int port, String name) throws IOException {
		InetSocketAddress address = new InetSocketAddress(host, port);
		if (address.isUnresolved())
			throw new IOException("No se encontro el host '" + host + "'");
		connect(new NetSocket(0), address, name);
	}

	/**
	 * Joins a server through an already open transport, such as a Bluetooth
	 * link to the host's phone. The client closes it when it disconnects.
	 *
	 * @param server the server's address on that transport
	 */
	public void connect(Transport transport, InetSocketAddress server, String name) {
		socket = transport;
		connection = new Connection(server, clock);
		Messages.Hello hello = new Messages.Hello();
		hello.name = localName = Messages.sanitizeName(name);
		connection.sendReliable(hello);
		status = Status.CONNECTING;
		connectStartMs = clock.getAsLong();
	}

	public void update() {
		if (socket == null || status == Status.DISCONNECTED || status == Status.REJECTED)
			return;
		NetSocket.Datagram datagram;
		while ((datagram = socket.poll()) != null) {
			if (!datagram.from.equals(connection.address))
				continue; // only listen to our server
			received.clear();
			if (!connection.receive(ByteBuffer.wrap(datagram.data), received))
				continue;
			for (Connection.Message message : received)
				handle(message);
		}
		long now = clock.getAsLong();
		if (status == Status.CONNECTING && now - connectStartMs > Protocol.CONNECT_TIMEOUT_MS) {
			fail(Status.DISCONNECTED, "No se pudo conectar: el servidor no responde");
		} else if (status == Status.CONNECTED && connection.isTimedOut(Protocol.TIMEOUT_MS)) {
			fail(Status.DISCONNECTED, "Se perdio la conexion con el servidor");
		}
		if (connection != null)
			flush();
	}

	private void flush() {
		for (byte[] data : connection.flush())
			socket.send(connection.address, data);
	}

	private void handle(Connection.Message message) {
		ByteBuffer in = message.payload;
		switch (message.type) {
		case Protocol.WELCOME: {
			Messages.Welcome welcome = Messages.Welcome.read(in);
			localId = welcome.playerId;
			teleportId = welcome.teleportId;
			mapName = welcome.map;
			fragLimit = welcome.fragLimit;
			spawn.set(welcome.spawn);
			players.put(localId, new PlayerInfo(localId, localName));
			status = Status.CONNECTED;
			break;
		}
		case Protocol.REJECT:
			fail(Status.REJECTED, Messages.Reject.read(in).reason);
			break;
		case Protocol.PLAYER_JOINED: {
			Messages.PlayerJoined m = Messages.PlayerJoined.read(in);
			players.put(m.playerId, new PlayerInfo(m.playerId, m.name));
			Event e = new Event(Event.Type.JOINED);
			e.playerId = m.playerId;
			events.add(e);
			break;
		}
		case Protocol.PLAYER_LEFT: {
			Messages.PlayerLeft m = Messages.PlayerLeft.read(in);
			players.remove(m.playerId);
			Event e = new Event(Event.Type.LEFT);
			e.playerId = m.playerId;
			events.add(e);
			break;
		}
		case Protocol.SNAPSHOT: {
			Messages.Snapshot snapshot = Messages.Snapshot.read(in);
			long now = clock.getAsLong();
			Match.Phase phase = Match.Phase.values()[MathUtils.clamp(snapshot.phase, 0, Match.Phase.values().length - 1)];
			if (phase == Match.Phase.PLAYING && matchPhase == Match.Phase.INTERMISSION) {
				events.add(new Event(Event.Type.MATCH_START));
				renames.clear();
			}
			matchPhase = phase;
			matchRemainingMs = snapshot.remainingMs;
			matchSampleMs = now;
			for (Messages.Snapshot.Entry entry : snapshot.players) {
				PlayerInfo p = players.get(entry.playerId);
				if (p == null)
					continue; // its PLAYER_JOINED hasn't arrived yet
				p.alive = entry.alive;
				p.crouched = entry.crouched;
				p.health = entry.health;
				p.kills = entry.kills;
				p.deaths = entry.deaths;
				p.assists = entry.assists;
				p.slot = entry.slot;
				p.addSample(now, entry.position, entry.yaw, entry.pitch);
			}
			break;
		}
		case Protocol.KILL: {
			Messages.Kill m = Messages.Kill.read(in);
			PlayerInfo victim = players.get(m.victimId);
			if (victim != null) {
				victim.alive = false;
				victim.health = 0;
			}
			Event e = new Event(Event.Type.KILL);
			e.playerId = m.victimId;
			e.killerId = m.killerId;
			e.headshot = m.headshot;
			e.gun = m.gun;
			events.add(e);
			break;
		}
		case Protocol.RESPAWN:
		case Protocol.SET_POSITION: {
			Messages.Teleport m = Messages.Teleport.read(message.type, in);
			PlayerInfo p = players.get(m.playerId);
			if (p != null) {
				p.clearSamples(); // snap instead of sliding across the map
				if (message.type == Protocol.RESPAWN) {
					p.alive = true;
					p.health = 100;
				}
			}
			if (m.playerId == localId)
				teleportId = m.teleportId;
			Event e = new Event(message.type == Protocol.RESPAWN ? Event.Type.RESPAWN : Event.Type.SET_POSITION);
			e.playerId = m.playerId;
			e.position.set(m.position);
			events.add(e);
			break;
		}
		case Protocol.MATCH_END: {
			Messages.MatchEnd m = Messages.MatchEnd.read(in);
			matchPhase = Match.Phase.INTERMISSION;
			Event e = new Event(Event.Type.MATCH_END);
			e.playerId = m.winnerId;
			e.detail = m.reason;
			events.add(e);
			break;
		}
		case Protocol.SHOT: {
			Messages.Shot m = Messages.Shot.read(in);
			Event e = new Event(Event.Type.SHOT);
			e.playerId = m.shooterId;
			e.gun = m.gun;
			e.from.set(m.origin);
			e.position.set(m.end);
			events.add(e);
			break;
		}
		case Protocol.WEAPON_SPAWN: {
			Messages.WeaponSpawn m = Messages.WeaponSpawn.read(in);
			Event e = new Event(Event.Type.WEAPON_SPAWN);
			e.weaponId = m.weaponId;
			e.gun = m.gun;
			e.from.set(m.from);
			e.position.set(m.position);
			e.yaw = m.yaw;
			e.magazine = m.magazine;
			e.reserve = m.reserve;
			e.playerId = m.droppedBy;
			events.add(e);
			break;
		}
		case Protocol.WEAPON_REMOVE: {
			Messages.WeaponRemove m = Messages.WeaponRemove.read(in);
			Event e = new Event(Event.Type.WEAPON_REMOVE);
			e.weaponId = m.weaponId;
			e.playerId = m.pickerId;
			events.add(e);
			break;
		}
		case Protocol.DAMAGE: {
			Messages.Damage m = Messages.Damage.read(in);
			Event e = new Event(Event.Type.DAMAGE);
			e.playerId = localId;
			e.detail = m.amount;
			e.position.set(m.source);
			events.add(e);
			break;
		}
		case Protocol.PLAYER_RENAMED: {
			Messages.PlayerRenamed m = Messages.PlayerRenamed.read(in);
			PlayerInfo p = players.get(m.playerId);
			if (p == null || p.name.equals(m.name))
				break;
			Event e = new Event(Event.Type.RENAMED);
			e.playerId = m.playerId;
			e.gun = p.name;
			renames.add(p.name + " -> " + m.name);
			if (renames.size > MAX_RENAMES_SHOWN)
				renames.removeIndex(0);
			p.name = m.name;
			if (m.playerId == localId)
				localName = m.name;
			events.add(e);
			break;
		}
		case Protocol.HIT: {
			Messages.Hit m = Messages.Hit.read(in);
			Event e = new Event(Event.Type.HIT);
			e.playerId = m.victimId;
			e.headshot = m.headshot;
			e.position.set(m.position);
			e.from.set(m.direction);
			events.add(e);
			break;
		}
		case Protocol.DISCONNECT:
			fail(Status.DISCONNECTED, "El servidor cerro la partida");
			break;
		default:
			break;
		}
	}

	private void fail(Status newStatus, String reason) {
		status = newStatus;
		failureReason = reason;
		NetLog.info("Client", reason);
	}

	/** Whether the local player is crouching, sent with the next states. */
	public void setCrouched(boolean crouched) {
		this.crouched = crouched;
	}

	/** Sends the local player's state, at most {@link Protocol#STATE_INTERVAL_MS} apart. */
	public void sendState(Vector3 position, float yaw, float pitch, int slot) {
		sendState(position, yaw, pitch, slot, 0, 0);
	}

	/** Also reports the ammo of the gun in hand (see {@link Messages.PlayerState#magazine}). */
	public void sendState(Vector3 position, float yaw, float pitch, int slot, int magazine, int reserve) {
		long now = clock.getAsLong();
		if (status != Status.CONNECTED || now < nextStateMs)
			return;
		nextStateMs = now + Protocol.STATE_INTERVAL_MS;
		state.sequence = ++sequence;
		state.teleportId = teleportId;
		state.clientTimeMs = now;
		state.position.set(position);
		state.yaw = yaw;
		state.pitch = pitch;
		state.slot = slot;
		state.magazine = magazine;
		state.reserve = reserve;
		state.crouched = crouched;
		connection.sendUnreliable(state);
	}

	/** Asks the server to call us {@code name} from now on (it tells everyone). */
	public void rename(String name) {
		if (status != Status.CONNECTED)
			return;
		Messages.Rename m = new Messages.Rename();
		m.name = Messages.sanitizeName(name);
		connection.sendReliable(m);
	}

	/** Renames in this match, "Old -> New", oldest first (the last few). */
	public Array<String> getRenames() {
		return renames;
	}

	/** Asks the server to throw the gun in {@code slot}. */
	public void sendDrop(int slot, int magazine, int reserve) {
		if (status != Status.CONNECTED)
			return;
		Messages.DropWeapon m = new Messages.DropWeapon();
		m.slot = slot;
		m.magazine = magazine;
		m.reserve = reserve;
		connection.sendReliable(m);
	}

	/**
	 * Asks the server for a gun on the ground. The ammo is the one of the gun
	 * it replaces (0 if that slot is empty).
	 */
	public void sendPickup(int weaponId, int magazine, int reserve) {
		if (status != Status.CONNECTED)
			return;
		Messages.PickupWeapon m = new Messages.PickupWeapon();
		m.weaponId = weaponId;
		m.magazine = magazine;
		m.reserve = reserve;
		connection.sendReliable(m);
	}

	public void sendFire(int slot, Vector3 origin, Vector3 direction) {
		if (status != Status.CONNECTED)
			return;
		Messages.Fire fire = new Messages.Fire();
		fire.slot = slot;
		fire.origin.set(origin);
		fire.direction.set(direction);
		connection.sendReliable(fire);
	}

	/** Tells the server we're leaving. Sent a few times because it is unreliable. */
	public void disconnect() {
		if (connection != null && (status == Status.CONNECTED || status == Status.CONNECTING)) {
			for (int i = 0; i < 3; i++) {
				connection.sendUnreliable(new Messages.Disconnect());
				flush();
			}
		}
		status = Status.DISCONNECTED;
	}

	/** 0 = no frag limit. */
	public int getFragLimit() {
		return fragLimit;
	}

	public Match.Phase getMatchPhase() {
		return matchPhase;
	}

	/** Time left in the current phase, counted down locally between snapshots. */
	public long getMatchRemainingMs() {
		return Math.max(0, matchRemainingMs - (clock.getAsLong() - matchSampleMs));
	}

	public Event pollEvent() {
		return events.poll();
	}

	public Status getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public int getLocalId() {
		return localId;
	}

	public String getMapName() {
		return mapName;
	}

	public Vector3 getSpawn() {
		return spawn;
	}

	public PlayerInfo getPlayer(int id) {
		return players.get(id);
	}

	/** A copy: libGDX map iterators are shared, so nested loops over them would break. */
	public Array<PlayerInfo> getPlayers() {
		return players.values().toArray();
	}

	@Override
	public void dispose() {
		disconnect();
		if (socket != null)
			socket.close();
		socket = null;
	}
}
