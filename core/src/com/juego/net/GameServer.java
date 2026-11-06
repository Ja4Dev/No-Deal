package com.juego.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
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
import com.juego.bots.Bot;
import com.juego.bots.BotDifficulty;
import com.juego.bots.BotWorld;
import com.juego.bots.NavGrid;
import com.juego.guns.Armory;
import com.juego.guns.GroundWeapons;
import com.juego.guns.GunDefinition;
import com.juego.match.Match;
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
 * Bots ({@link Bot}) are players simulated here: they have no connection, but
 * everyone else sees them like any other player.
 * <p>
 * Not thread-safe: call {@link #update()} from a single thread, often (every
 * frame, or ~60 times per second).
 */
public class GameServer implements Disposable {
	private static final String TAG = "Server";
	/** Shots start at the eyes (2.2 above the capsule center), plus room for movement since the last state. */
	private static final float MAX_FIRE_ORIGIN_DISTANCE = 4.5f;
	/** Fire rate is checked with some tolerance: shots can arrive bunched by network jitter. */
	private static final float FIRE_RATE_TOLERANCE = 0.75f;
	private static final long CORRECTION_COOLDOWN_MS = 500;
	private static final float KILL_PLANE_Y = -30f;
	/** Extra reach allowed for pickups: the server sees players a little late. */
	private static final float PICKUP_SLACK = 1.5f;
	public static final int MAX_BOTS = 16;
	private static final String[] BOT_NAMES = { "Pedro", "Lucia", "Tano", "Marta", "Rulo", "Vicky", "Nacho", "Sole",
			"Tincho", "Flor", "Chino", "Male", "Pipo", "Juli", "Beto", "Caro", "Quique", "Ana" };

	public static class Config {
		public int port = Protocol.DEFAULT_PORT;
		public int maxPlayers = Protocol.MAX_PLAYERS;
		public String map = "Bank";
		public int timeLimitMinutes = Protocol.DEFAULT_TIME_LIMIT_MINUTES;
		/** 0 = no frag limit. */
		public int fragLimit = Protocol.DEFAULT_FRAG_LIMIT;
		public long intermissionMs = Protocol.INTERMISSION_MS;
		/** Bots in the match from the start (they can be added or removed later with commands). */
		public int bots;
		public BotDifficulty botDifficulty = BotDifficulty.MEDIO;
		/** Only this computer can join: the private server behind "Un jugador". */
		public boolean localOnly;
	}

	/** What a server event is about, so a console can color or filter it. */
	public enum EventType {
		INFO, JOIN, LEAVE, KILL, MATCH, WARNING
	}

	/** Receives the server's events (on the server thread), e.g. for the server window. */
	public interface Listener {
		void onServerEvent(EventType type, String message);
	}

	/** A copy of one player's public state, safe to hand to another thread. */
	public static final class PlayerInfo {
		public final int id, kills, deaths, assists, health;
		/** For bots the address is "bot (Medio)". */
		public final String name, address;
		public final boolean alive, bot;

		PlayerInfo(int id, String name, String address, int kills, int deaths, int assists, int health,
				boolean alive, boolean bot) {
			this.bot = bot;
			this.assists = assists;
			this.id = id;
			this.name = name;
			this.address = address;
			this.kills = kills;
			this.deaths = deaths;
			this.health = health;
			this.alive = alive;
		}
	}

	private static class ServerPlayer {
		final int id;
		final String name;
		/** Null for bots. */
		final Connection connection;
		/** Null for people. */
		Bot bot;
		final MovementValidator validator = new MovementValidator();
		final Vector3 position = new Vector3();
		final long[] lastFireMs = { Long.MIN_VALUE / 2, Long.MIN_VALUE / 2 };
		final btCollisionObject hitbox = new btCollisionObject();
		/** Whether the hitbox is in the collision world (dead players are taken out). */
		boolean hitboxInWorld;
		float yaw, pitch;
		int slot, health = 100, kills, deaths, assists, teleportId, lastSequence = -1;
		/** Damage taken from each other player since the last spawn, for assists. */
		final com.badlogic.gdx.utils.IntIntMap damageFrom = new com.badlogic.gdx.utils.IntIntMap();
		/** Guns carried, by slot (null = empty), and the ammo of the one in hand as the client reports it. */
		final GunDefinition[] guns = new GunDefinition[GunDefinition.Slot.values().length];
		int magazine, reserve;
		boolean alive = true;
		long respawnAtMs, lastCorrectionMs = Long.MIN_VALUE / 2;

		ServerPlayer(int id, String name, Connection connection) {
			this.id = id;
			this.name = name;
			this.connection = connection;
		}

		boolean isBot() {
			return bot != null;
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
	private final Match match;
	private final GroundWeapons weapons;
	private long nextSnapshotMs;
	private int tick;
	private Listener listener;

	// Bots: their physics and navigation are built when the first bot joins.
	private BotWorld botWorld;
	private NavGrid nav;
	private BotDifficulty botDifficulty;
	private final Random random = new Random();
	private final Array<Bot.Target> targets = new Array<>();
	private float botAccumulator;
	private long lastBotUpdateMs = Long.MIN_VALUE;
	private long botNowMs;
	private final Bot.Host botHost = (bot, origin, direction) -> {
		ServerPlayer p = byId.get(bot.id);
		if (p != null)
			fireShot(p, p.slot, origin, direction, botNowMs);
	};

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
		socket = new NetSocket(config.port, config.localOnly);
		match = new Match(new Match.Settings(config.timeLimitMinutes * 60_000L, config.fragLimit, config.intermissionMs));
		match.start(clock.getAsLong());
		weapons = new GroundWeapons(this::castLevel, new GroundWeapons.Listener() {
			@Override
			public void spawned(GroundWeapons.Weapon weapon) {
				broadcastReliable(weaponSpawn(weapon, true), null);
			}

			@Override
			public void removed(GroundWeapons.Weapon weapon, int pickerId) {
				Messages.WeaponRemove m = new Messages.WeaponRemove();
				m.weaponId = weapon.id;
				m.pickerId = pickerId;
				broadcastReliable(m, null);
			}
		});
		JsonValue spawners = mapData.get("weapons");
		if (spawners != null)
			for (JsonValue s : spawners)
				weapons.addSpawner(armory.get(s.getString("gun")), new Vector3(s.get("position").getFloat(0),
						s.get("position").getFloat(1), s.get("position").getFloat(2)));
		weapons.update(clock.getAsLong());
		botDifficulty = config.botDifficulty;
		for (int i = 0; i < Math.min(config.bots, MAX_BOTS); i++)
			addBot(botDifficulty);
		NetLog.info(TAG, "Listening on UDP port " + socket.getLocalPort() + " (map " + config.map + ", max "
				+ config.maxPlayers + " players, " + config.timeLimitMinutes + " min"
				+ (config.fragLimit > 0 ? " or " + config.fragLimit + " kills" : "") + ")");
	}

	/** Called on the server thread for every join, leave, kill and match change. */
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	private void event(EventType type, String message) {
		NetLog.info(TAG, message);
		if (listener != null)
			listener.onServerEvent(type, message);
	}

	public int getPort() {
		return socket.getLocalPort();
	}

	/** People connected (bots not counted). */
	public int getPlayerCount() {
		int count = 0;
		for (ServerPlayer p : byId.values())
			if (!p.isBot())
				count++;
		return count;
	}

	public int getBotCount() {
		return byId.size - getPlayerCount();
	}

	/** Difficulty of the next bots added. */
	public BotDifficulty getBotDifficulty() {
		return botDifficulty;
	}

	/** E.g. "jugando, quedan 7:42", for the consoles. */
	public String describeMatch() {
		long remaining = match.remainingMs(clock.getAsLong());
		return match.isPlaying() ? "jugando, quedan " + Match.formatClock(remaining)
				: "entre partidas, la proxima en " + Match.formatClock(remaining);
	}

	/** One line per player, for the dedicated server console. */
	public String describePlayers() {
		StringBuilder sb = new StringBuilder();
		for (PlayerInfo p : getPlayers())
			sb.append(String.format("  #%d %-16s %s  bajas %d / muertes %d / asistencias %d  %s%n", p.id, p.name,
					p.alive ? p.health + " hp" : "muerto", p.kills, p.deaths, p.assists, p.address));
		return sb.length() == 0 ? "  (nadie)\n" : sb.toString();
	}

	/** The players right now, sorted by id. */
	public Array<PlayerInfo> getPlayers() {
		Array<PlayerInfo> result = new Array<>(byId.size);
		for (ServerPlayer p : byId.values())
			result.add(new PlayerInfo(p.id, p.name,
					p.isBot() ? "bot (" + p.bot.getDifficulty().label + ")" : NetUtils.format(p.connection.address),
					p.kills, p.deaths, p.assists, p.health, p.alive, p.isBot()));
		result.sort((a, b) -> Integer.compare(a.id, b.id));
		return result;
	}

	public boolean isMatchPlaying() {
		return match.isPlaying();
	}

	public long getMatchRemainingMs() {
		return match.remainingMs(clock.getAsLong());
	}

	public int getMaxPlayers() {
		return config.maxPlayers;
	}

	public void update() {
		long now = clock.getAsLong();
		NetSocket.Datagram datagram;
		while ((datagram = socket.poll()) != null)
			handleDatagram(datagram, now);

		for (ServerPlayer p : byId.values().toArray()) {
			if (!p.isBot() && p.connection.isTimedOut(Protocol.TIMEOUT_MS)) {
				event(EventType.LEAVE, p.name + " se desconectó (sin respuesta)");
				removePlayer(p);
			} else if (!p.alive && now >= p.respawnAtMs) {
				respawn(p, now);
			}
		}
		updateBots(now);
		updateMatch(now);
		weapons.update(now);
		if (now >= nextSnapshotMs) {
			nextSnapshotMs = now + Protocol.SNAPSHOT_INTERVAL_MS;
			broadcastSnapshot();
		}
		for (ServerPlayer p : byId.values())
			if (!p.isBot())
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
			case Protocol.DROP_WEAPON:
				handleDrop(player, Messages.DropWeapon.read(message.payload), now);
				break;
			case Protocol.PICKUP_WEAPON:
				handlePickup(player, Messages.PickupWeapon.read(message.payload), now);
				break;
			case Protocol.DISCONNECT:
				event(EventType.LEAVE, player.name + " salió de la partida");
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
		else if (getPlayerCount() >= config.maxPlayers)
			reason = "Servidor lleno (" + config.maxPlayers + " jugadores)";
		if (reason != null) {
			Messages.Reject reject = new Messages.Reject();
			reject.reason = reason;
			connection.sendReliable(reject);
			closing.add(connection);
			event(EventType.WARNING, "Rechazado " + NetUtils.format(connection.address) + ": " + reason);
			return null;
		}
		if (byId.size >= Protocol.MAX_MATCH_PLAYERS)
			removeBot(null); // people come first: a bot leaves to make room
		if (getPlayerCount() == 0) {
			// The first person into an empty server gets a full match (bots waiting there start over too).
			match.start(now);
			for (ServerPlayer bot : byId.values()) {
				bot.kills = 0;
				bot.deaths = 0;
				bot.assists = 0;
			}
		}
		int id = 1;
		while (byId.containsKey(id))
			id++;
		ServerPlayer player = new ServerPlayer(id, Messages.sanitizeName(hello.name), connection);
		player.hitbox.setCollisionShape(hitboxShape);
		player.hitbox.setUserValue(id);
		resetLoadout(player);
		byId.put(id, player);
		byAddress.put(connection.address, player);
		placeAtSpawn(player, now);

		Messages.Welcome welcome = new Messages.Welcome();
		welcome.playerId = id;
		welcome.teleportId = player.teleportId;
		welcome.map = config.map;
		welcome.fragLimit = config.fragLimit;
		welcome.spawn.set(player.position);
		connection.sendReliable(welcome);
		for (ServerPlayer other : byId.values()) {
			if (other != player)
				connection.sendReliable(joined(other));
		}
		for (GroundWeapons.Weapon w : weapons.all())
			connection.sendReliable(weaponSpawn(w, false)); // already lying there: no throw animation
		broadcastReliable(joined(player), player);
		event(EventType.JOIN,
				player.name + " entró a la partida (#" + id + ", " + NetUtils.format(connection.address) + ")");
		return player;
	}

	private void handleState(ServerPlayer p, Messages.PlayerState state, long now) {
		if (!p.alive || state.teleportId != p.teleportId || state.sequence <= p.lastSequence)
			return; // dead, sent before the last teleport, or older than one we already have
		p.lastSequence = state.sequence;
		p.yaw = state.yaw;
		p.pitch = state.pitch;
		if (state.slot >= 0 && state.slot < p.guns.length) {
			p.slot = state.slot;
			p.magazine = state.magazine;
			p.reserve = state.reserve;
		}
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
		if (!match.isPlaying() || !shooter.alive || fire.slot < 0 || fire.slot >= shooter.guns.length)
			return;
		GunDefinition gun = shooter.guns[fire.slot];
		if (gun == null)
			return; // that slot is empty: the gun was dropped
		if (now - shooter.lastFireMs[fire.slot] < gun.fireDelayMs * FIRE_RATE_TOLERANCE)
			return; // faster than the gun can shoot
		if (fire.origin.dst(shooter.position) > MAX_FIRE_ORIGIN_DISTANCE || fire.direction.isZero())
			return;
		fireShot(shooter, fire.slot, fire.origin, fire.direction, now);
	}

	/** A shot that passed the checks (or a bot's): traced against the level and the players. */
	private void fireShot(ServerPlayer shooter, int slot, Vector3 origin, Vector3 direction, long now) {
		GunDefinition gun = shooter.guns[slot];
		if (gun == null || !match.isPlaying() || !shooter.alive)
			return;
		shooter.lastFireMs[slot] = now;
		direction.nor();
		Vector3 to = tmp.set(direction).scl(gun.range).add(origin);

		AllHitsRayResultCallback callback = new AllHitsRayResultCallback(origin, to);
		try {
			world.rayTest(origin, to, callback);
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
			Vector3 end = closest < 0 ? to : callback.getHitPointWorld().at(closest);
			broadcastShot(shooter, gun, origin, end);
			if (closest < 0)
				return;
			int hitId = callback.getCollisionObjects().atConst(closest).getUserValue();
			ServerPlayer victim = byId.get(hitId);
			if (victim == null || !victim.alive)
				return; // hit the level
			boolean headshot = end.y >= victim.position.y + CharacterShape.HEAD_OFFSET;
			int damage = gun.damage * (headshot ? 3 : 1);
			victim.damageFrom.getAndIncrement(shooter.id, 0, Math.min(damage, Math.max(0, victim.health)));
			victim.health -= damage;
			broadcastHit(victim, headshot, end, direction);
			if (victim.isBot()) {
				victim.bot.hurtBy(shooter.id, origin, now);
			} else {
				Messages.Damage hurt = new Messages.Damage();
				hurt.amount = damage;
				hurt.source.set(origin);
				victim.connection.sendUnreliable(hurt);
			}
			if (victim.health <= 0)
				kill(victim, shooter, headshot, gun.name, now);
		} finally {
			callback.dispose();
		}
	}

	/** Tells the other players about a shot, for its sound and tracer. */
	private void broadcastShot(ServerPlayer shooter, GunDefinition gun, Vector3 origin, Vector3 end) {
		Messages.Shot shot = new Messages.Shot();
		shot.shooterId = shooter.id;
		shot.gun = gun.id;
		shot.origin.set(origin);
		shot.end.set(end);
		for (ServerPlayer p : byId.values()) {
			if (p.isBot())
				p.bot.hearShot(shooter.id, origin, botNowMs);
			else if (p != shooter)
				p.connection.sendUnreliable(shot);
		}
	}

	// ---- bots ----

	/** Builds the bots' physics and navigation the first time they're needed (a fraction of a second). */
	private void ensureBotWorld() {
		if (botWorld != null)
			return;
		long start = System.currentTimeMillis();
		botWorld = new BotWorld(map.shape);
		nav = new NavGrid(botWorld, spawnPoints);
		NetLog.info(TAG, "Bot navigation: " + nav.usableCount() + " walkable cells (of " + nav.nodeCount()
				+ ") in " + (System.currentTimeMillis() - start) + " ms");
	}

	/**
	 * Adds a bot, which spawns right away.
	 *
	 * @return its name, or null if there is no room: {@link #MAX_BOTS} bots, or a
	 *         full match ({@link Protocol#MAX_MATCH_PLAYERS})
	 */
	public String addBot(BotDifficulty difficulty) {
		if (getBotCount() >= MAX_BOTS || byId.size >= Protocol.MAX_MATCH_PLAYERS)
			return null;
		long now = clock.getAsLong();
		ensureBotWorld();
		int id = 1;
		while (byId.containsKey(id))
			id++;
		String name = botName();
		ServerPlayer p = new ServerPlayer(id, name, null);
		p.hitbox.setCollisionShape(hitboxShape);
		p.hitbox.setUserValue(id);
		resetLoadout(p);
		byId.put(id, p);
		placeAtSpawn(p, now);
		p.bot = new Bot(id, name, difficulty, botWorld, nav, p.position, random);
		respawnBot(p);
		broadcastReliable(joined(p), p);
		event(EventType.JOIN, name + " entró a la partida (bot " + difficulty.label + ")");
		return name;
	}

	/** "BOT Pedro": the first name not in use, in a random order. */
	private String botName() {
		int first = random.nextInt(BOT_NAMES.length);
		for (int round = 1;; round++) {
			for (int i = 0; i < BOT_NAMES.length; i++) {
				String name = "BOT " + BOT_NAMES[(first + i) % BOT_NAMES.length] + (round > 1 ? " " + round : "");
				if (findBot(name) == null)
					return name;
			}
		}
	}

	/** A bot by name, with or without the "BOT " prefix, ignoring case; null if there is none. */
	private ServerPlayer findBot(String name) {
		String wanted = name.toLowerCase().startsWith("bot ") ? name : "BOT " + name;
		for (ServerPlayer p : byId.values())
			if (p.isBot() && p.name.equalsIgnoreCase(wanted))
				return p;
		return null;
	}

	/**
	 * Takes a bot out of the match.
	 *
	 * @param name which one; null for the last one added
	 * @return its name, or null if there is no such bot
	 */
	public String removeBot(String name) {
		ServerPlayer bot = null;
		if (name != null) {
			bot = findBot(name);
		} else {
			for (ServerPlayer p : byId.values())
				if (p.isBot() && (bot == null || p.id > bot.id))
					bot = p;
		}
		if (bot == null)
			return null;
		removePlayer(bot);
		event(EventType.LEAVE, bot.name + " salió de la partida (bot)");
		return bot.name;
	}

	/** Changes the difficulty of every bot, and of the ones added from now on. */
	public void setBotDifficulty(BotDifficulty difficulty) {
		botDifficulty = difficulty;
		for (ServerPlayer p : byId.values())
			if (p.isBot())
				p.bot.setDifficulty(difficulty);
		event(EventType.INFO, "Dificultad de los bots: " + difficulty.label);
	}

	private final Array<ServerPlayer> botsThisStep = new Array<>();

	/**
	 * Runs the bots in fixed steps of {@link BotWorld#STEP}: they decide, then
	 * their bodies move. Without people in the server they wait where they are.
	 */
	private void updateBots(long now) {
		if (botWorld == null || getBotCount() == 0) {
			lastBotUpdateMs = now;
			return;
		}
		if (lastBotUpdateMs == Long.MIN_VALUE)
			lastBotUpdateMs = now;
		botAccumulator = Math.min(botAccumulator + (now - lastBotUpdateMs) / 1000f, 5 * BotWorld.STEP);
		lastBotUpdateMs = now;
		boolean fighting = match.isPlaying() && getPlayerCount() > 0;
		botNowMs = now;
		while (botAccumulator >= BotWorld.STEP) {
			botAccumulator -= BotWorld.STEP;
			// A copy: shots and kills iterate the players too, and map iterators can't be nested.
			botsThisStep.clear();
			for (ServerPlayer p : byId.values())
				if (p.isBot() && p.alive)
					botsThisStep.add(p);
			refreshTargets();
			for (ServerPlayer p : botsThisStep) {
				if (p.alive)
					p.bot.think(now, BotWorld.STEP, targets, fighting, botHost);
				if (p.alive)
					p.bot.move();
			}
			botWorld.step();
			for (ServerPlayer p : botsThisStep) {
				if (!p.alive)
					continue;
				p.bot.afterStep();
				setPosition(p, p.bot.position);
				p.yaw = p.bot.yaw;
				p.pitch = p.bot.pitch;
				p.magazine = p.bot.getMagazine();
				p.reserve = p.bot.getReserve();
				if (p.position.y < KILL_PLANE_Y)
					kill(p, null, false, "", now);
			}
		}
	}

	/** What bots know about everyone: who is alive and where. */
	private void refreshTargets() {
		while (targets.size < byId.size)
			targets.add(new Bot.Target());
		targets.truncate(byId.size);
		int i = 0;
		for (ServerPlayer p : byId.values()) {
			Bot.Target t = targets.get(i++);
			t.id = p.id;
			t.alive = p.alive;
			t.position.set(p.position);
		}
	}

	// ---- console commands ----

	/** The help for {@link #command}, one command per line. */
	public static final String COMMANDS_HELP = "bot agregar [cantidad] [dificultad]  agrega bots\n"
			+ "bot quitar [nombre | cantidad | todos]  saca bots\n"
			+ "bot dificultad <dificultad>  cambia la dificultad de todos los bots\n"
			+ "bot lista  muestra los bots\n"
			+ "  dificultades: " + BotDifficulty.choices() + "\n"
			+ "  (tambien bot_add, bot_kick y bot_difficulty, como en CS)\n"
			+ "estado  jugadores y tiempo de la partida";

	/**
	 * Runs a match command typed in the dedicated server's console, the server
	 * window, or the in-game console of whoever hosts. Call it on the server
	 * thread.
	 *
	 * @return what to show (one or more lines), or null if it isn't a match
	 *         command (so the caller can try its own)
	 */
	public String command(String line) {
		String text = line.trim().replaceAll("\\s+", " ");
		String lower = text.toLowerCase();
		if (lower.startsWith("bot_"))
			lower = "bot " + lower.substring(4);
		else if (lower.equals("bots"))
			lower = "bot lista";
		String[] words = lower.split(" ");
		if (words[0].equals("estado") || words[0].equals("status") || words[0].equals("jugadores")) {
			return getPlayerCount() + "/" + config.maxPlayers + " jugadores y " + getBotCount() + " bots, "
					+ describeMatch() + "\n" + describePlayers().replaceAll("\\s+$", "");
		}
		if (!words[0].equals("bot"))
			return null;
		String action = words.length > 1 ? words[1] : "lista";
		String rest = words.length > 2 ? lower.substring(lower.indexOf(words[1]) + words[1].length()).trim() : "";
		switch (action) {
		case "agregar":
		case "add":
		case "sumar":
			return addBotsCommand(rest);
		case "quitar":
		case "sacar":
		case "kick":
		case "remove":
			return removeBotsCommand(rest, text);
		case "dificultad":
		case "difficulty": {
			BotDifficulty d = BotDifficulty.parse(rest);
			if (d == null)
				return rest.isEmpty() ? "Dificultad de los bots: " + botDifficulty.label + ". Opciones: "
						+ BotDifficulty.choices() : "No conozco la dificultad '" + rest + "'. Opciones: " + BotDifficulty.choices();
			setBotDifficulty(d);
			return "Ahora todos los bots juegan en " + d.label + ".";
		}
		case "lista":
		case "list": {
			StringBuilder sb = new StringBuilder(getBotCount() + " bots (los nuevos entran en " + botDifficulty.label + ")");
			for (PlayerInfo p : getPlayers())
				if (p.bot)
					sb.append(String.format("%n  %-12s %s  bajas %d / muertes %d", p.name, p.address, p.kills, p.deaths));
			return sb.toString();
		}
		default:
			return "No conozco 'bot " + action + "'. Comandos:\n" + COMMANDS_HELP;
		}
	}

	/** "bot agregar", "bot agregar 3", "bot agregar dificil", "bot agregar 2 muy dificil"... */
	private String addBotsCommand(String args) {
		int count = 1;
		StringBuilder difficultyText = new StringBuilder();
		for (String word : args.split(" ")) {
			if (word.matches("\\d{1,3}"))
				count = Integer.parseInt(word);
			else if (!word.isEmpty())
				difficultyText.append(difficultyText.length() == 0 ? "" : " ").append(word);
		}
		BotDifficulty difficulty = botDifficulty;
		if (difficultyText.length() > 0) {
			difficulty = BotDifficulty.parse(difficultyText.toString());
			if (difficulty == null)
				return "No conozco la dificultad '" + difficultyText + "'. Opciones: " + BotDifficulty.choices();
		}
		Array<String> added = new Array<>();
		for (int i = 0; i < count; i++) {
			String name = addBot(difficulty);
			if (name == null)
				break;
			added.add(name);
		}
		String full = "No entran mas bots (maximo " + MAX_BOTS + ", y " + Protocol.MAX_MATCH_PLAYERS
				+ " en total).";
		if (added.size == 0)
			return full;
		return (added.size == 1 ? "Entro " : "Entraron ") + added.toString(", ") + " (" + difficulty.label + ")."
				+ (added.size < count ? " " + full : "");
	}

	/** "bot quitar", "bot quitar 2", "bot quitar todos", "bot quitar Pedro". */
	private String removeBotsCommand(String args, String original) {
		if (getBotCount() == 0)
			return "No hay bots.";
		int count = 1;
		String name = null;
		if (args.equals("todos") || args.equals("all"))
			count = MAX_BOTS;
		else if (args.matches("\\d+"))
			count = Integer.parseInt(args);
		else if (!args.isEmpty())
			name = original.substring(original.toLowerCase().lastIndexOf(args)).trim();
		Array<String> removed = new Array<>();
		for (int i = 0; i < count; i++) {
			String gone = removeBot(name);
			if (gone == null)
				break;
			removed.add(gone);
		}
		if (removed.size == 0)
			return "No hay ningun bot llamado '" + name + "'. Escribi 'bot lista' para verlos.";
		return "Salio " + removed.toString(", ") + ".";
	}

	/** Tells everyone a player was hit, for the blood and the hit sound. */
	private void broadcastHit(ServerPlayer victim, boolean headshot, Vector3 where, Vector3 direction) {
		Messages.Hit hit = new Messages.Hit();
		hit.victimId = victim.id;
		hit.headshot = headshot;
		hit.position.set(where);
		hit.direction.set(direction).nor();
		for (ServerPlayer p : byId.values())
			if (!p.isBot())
				p.connection.sendUnreliable(hit);
	}

	// ---- guns on the ground ----

	private void handleDrop(ServerPlayer p, Messages.DropWeapon m, long now) {
		if (!p.alive || !match.isPlaying() || m.slot < 0 || m.slot >= p.guns.length || p.guns[m.slot] == null)
			return;
		if (gunCount(p) < 2)
			return; // the last gun can't be dropped
		GunDefinition def = p.guns[m.slot];
		p.guns[m.slot] = null;
		weapons.toss(def, clampMagazine(def, m.magazine), clampReserve(def, m.reserve), p.position, p.yaw, p.id,
				now);
	}

	private void handlePickup(ServerPlayer p, Messages.PickupWeapon m, long now) {
		GroundWeapons.Weapon w = weapons.get(m.weaponId);
		if (w == null || !p.alive || !match.isPlaying() || !GroundWeapons.inReach(w, p.position, PICKUP_SLACK))
			return; // already taken by someone else, or too far
		int slot = w.def.slot.ordinal();
		GunDefinition replaced = p.guns[slot];
		// The replaced gun is thrown first, so the client sees it leave the slot
		// before the new one arrives.
		if (replaced != null)
			weapons.toss(replaced, clampMagazine(replaced, m.magazine), clampReserve(replaced, m.reserve),
					p.position, p.yaw, p.id, now);
		weapons.remove(w, p.id, now);
		p.guns[slot] = w.def;
	}

	/** Drops the gun a dying player had in hand, where they stood. */
	private void dropOnDeath(ServerPlayer p, long now) {
		GunDefinition def = p.guns[p.slot];
		if (def == null)
			return;
		p.guns[p.slot] = null;
		weapons.dropAtFeet(def, clampMagazine(def, p.magazine), clampReserve(def, p.reserve), p.position, p.yaw,
				p.id, now);
	}

	private void resetLoadout(ServerPlayer p) {
		for (int i = 0; i < p.guns.length; i++)
			p.guns[i] = null;
		for (String id : Protocol.LOADOUT) {
			GunDefinition def = armory.get(id);
			p.guns[def.slot.ordinal()] = def;
		}
		p.slot = 0;
		p.magazine = p.guns[0].magazineSize;
		p.reserve = p.guns[0].reserveAmmo;
	}

	private static int gunCount(ServerPlayer p) {
		int count = 0;
		for (GunDefinition def : p.guns)
			if (def != null)
				count++;
		return count;
	}

	private static int clampMagazine(GunDefinition def, int magazine) {
		return Math.max(0, Math.min(magazine, def.magazineSize));
	}

	private static int clampReserve(GunDefinition def, int reserve) {
		return def.reserveAmmo < 0 ? def.reserveAmmo : Math.max(0, Math.min(reserve, def.reserveAmmo));
	}

	private Messages.WeaponSpawn weaponSpawn(GroundWeapons.Weapon w, boolean thrown) {
		Messages.WeaponSpawn m = new Messages.WeaponSpawn();
		m.weaponId = w.id;
		m.gun = w.def.id;
		m.from.set(thrown ? w.from : w.position);
		m.position.set(w.position);
		m.yaw = w.yaw;
		m.magazine = w.magazine;
		m.reserve = w.reserve;
		m.droppedBy = w.droppedBy;
		return m;
	}

	private final Vector3 castTo = new Vector3();

	/** Distance to the level (ignoring players) along a segment, for {@link GroundWeapons}. */
	private float castLevel(Vector3 from, Vector3 to, Vector3 outHit) {
		castTo.set(to); // 'to' may be one of GroundWeapons' temporaries: keep a copy
		AllHitsRayResultCallback callback = new AllHitsRayResultCallback(from, castTo);
		try {
			world.rayTest(from, castTo, callback);
			float best = 2f;
			int closest = -1;
			for (int i = 0; i < callback.getCollisionObjects().size(); i++) {
				if (callback.getCollisionObjects().atConst(i).getUserValue() != Protocol.WORLD)
					continue;
				float fraction = callback.getHitFractions().atConst(i);
				if (fraction < best) {
					best = fraction;
					closest = i;
				}
			}
			if (closest < 0)
				return -1;
			outHit.set(callback.getHitPointWorld().at(closest));
			return best * from.dst(castTo);
		} finally {
			callback.dispose();
		}
	}

	// ---- game state ----

	private void updateMatch(long now) {
		int topKills = 0;
		for (ServerPlayer p : byId.values())
			topKills = Math.max(topKills, p.kills);
		switch (match.update(now, topKills)) {
		case ENDED: {
			ServerPlayer winner = leader();
			Messages.MatchEnd end = new Messages.MatchEnd();
			end.winnerId = winner == null ? Protocol.WORLD : winner.id;
			end.reason = match.getEndReason().ordinal();
			broadcastReliable(end, null);
			event(EventType.MATCH, "Fin de la partida ("
					+ (match.getEndReason() == Match.EndReason.FRAG_LIMIT ? "límite de bajas" : "tiempo") + "). "
					+ (winner == null ? "Nadie hizo bajas." : "Ganó " + winner.name + " con " + winner.kills + " bajas."));
			break;
		}
		case RESTARTED:
			// A copy: placeAtSpawn and teleport iterate the players too, and libGDX map
			// iterators can't be nested.
			for (ServerPlayer p : byId.values().toArray()) {
				p.kills = 0;
				p.deaths = 0;
				p.assists = 0;
				p.damageFrom.clear();
				p.alive = true;
				p.health = 100;
				resetLoadout(p);
				placeAtSpawn(p, now);
				respawnBot(p);
				teleport(p, p.position, Protocol.RESPAWN, true, now);
			}
			weapons.reset(now);
			event(EventType.MATCH, "Empieza una nueva partida");
			break;
		default:
			break;
		}
	}

	/** Most kills; ties go to fewer deaths, then to whoever joined first. Null if nobody scored. */
	private ServerPlayer leader() {
		ServerPlayer best = null;
		for (ServerPlayer p : byId.values()) {
			if (p.kills == 0)
				continue;
			if (best == null || p.kills > best.kills || (p.kills == best.kills
					&& (p.deaths < best.deaths || (p.deaths == best.deaths && p.id < best.id))))
				best = p;
		}
		return best;
	}

	private void kill(ServerPlayer victim, ServerPlayer killer, boolean headshot, String gun, long now) {
		victim.health = 0;
		victim.alive = false;
		victim.deaths++;
		victim.respawnAtMs = now + Protocol.RESPAWN_DELAY_MS;
		removeHitbox(victim);
		if (victim.isBot())
			victim.bot.die();
		if (killer != null && killer != victim)
			killer.kills++;
		// Whoever else did enough damage to this life gets an assist.
		for (com.badlogic.gdx.utils.IntIntMap.Entry e : victim.damageFrom) {
			ServerPlayer helper = byId.get(e.key);
			if (helper != null && helper != killer && helper != victim && e.value >= Protocol.ASSIST_DAMAGE)
				helper.assists++;
		}
		victim.damageFrom.clear();
		Messages.Kill kill = new Messages.Kill();
		kill.killerId = killer == null ? Protocol.WORLD : killer.id;
		kill.victimId = victim.id;
		kill.headshot = headshot;
		kill.gun = gun;
		broadcastReliable(kill, null);
		// After the KILL, so the victim's client is already dead when its gun hits the floor.
		if (killer != null)
			dropOnDeath(victim, now); // falling out of the map takes the gun with you
		if (killer == null)
			event(EventType.KILL, victim.name + " se cayó del mapa");
		else
			event(EventType.KILL,
					killer.name + " mató a " + victim.name + " con " + gun + (headshot ? " (headshot)" : ""));
	}

	private void respawn(ServerPlayer p, long now) {
		p.alive = true;
		p.health = 100;
		resetLoadout(p);
		placeAtSpawn(p, now);
		respawnBot(p);
		teleport(p, p.position, Protocol.RESPAWN, true, now);
	}

	/** Puts a bot's body where the server placed it, with a fresh gun. */
	private void respawnBot(ServerPlayer p) {
		if (p.isBot())
			p.bot.respawn(p.position, p.guns[p.slot], random.nextFloat() * 360);
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
		else if (!p.isBot())
			p.connection.sendReliable(message);
	}

	private void removePlayer(ServerPlayer p) {
		byId.remove(p.id);
		if (p.isBot())
			p.bot.dispose();
		else
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
			if (p != except && !p.isBot())
				p.connection.sendReliable(message);
		}
	}

	private void broadcastSnapshot() {
		if (byId.size == 0)
			return;
		Messages.Snapshot snapshot = new Messages.Snapshot();
		snapshot.tick = tick++;
		snapshot.phase = match.getPhase().ordinal();
		snapshot.remainingMs = (int) Math.min(Integer.MAX_VALUE, match.remainingMs(clock.getAsLong()));
		for (ServerPlayer p : byId.values()) {
			Messages.Snapshot.Entry e = new Messages.Snapshot.Entry();
			e.playerId = p.id;
			e.alive = p.alive;
			e.health = Math.max(0, p.health);
			e.kills = p.kills;
			e.deaths = p.deaths;
			e.assists = p.assists;
			e.slot = p.slot;
			e.position.set(p.position);
			e.yaw = p.yaw;
			e.pitch = p.pitch;
			snapshot.players.add(e);
		}
		for (ServerPlayer p : byId.values())
			if (!p.isBot())
				p.connection.sendUnreliable(snapshot);
	}

	@Override
	public void dispose() {
		for (ServerPlayer p : byId.values()) {
			if (p.isBot()) {
				p.bot.dispose();
			} else {
				p.connection.sendUnreliable(new Messages.Disconnect());
				send(p.connection);
			}
			removeHitbox(p);
			p.hitbox.dispose();
		}
		if (botWorld != null)
			botWorld.dispose();
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
