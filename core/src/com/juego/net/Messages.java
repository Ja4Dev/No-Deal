package com.juego.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Payloads of every message, with their binary encoding. Each message knows
 * how to write itself to and read itself from a {@link ByteBuffer}.
 */
public final class Messages {
	private Messages() {
	}

	public interface Message {
		byte type();

		void write(ByteBuffer out);
	}

	public static byte[] encode(Message message) {
		ByteBuffer buffer = ByteBuffer.allocate(Protocol.MAX_DATAGRAM);
		message.write(buffer);
		byte[] bytes = new byte[buffer.position()];
		buffer.flip();
		buffer.get(bytes);
		return bytes;
	}

	// ---- helpers ----

	static void writeString(ByteBuffer out, String value, int maxLength) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		int length = Math.min(bytes.length, maxLength * 4);
		out.put((byte) length);
		out.put(bytes, 0, length);
	}

	static String readString(ByteBuffer in) {
		int length = in.get() & 0xFF;
		byte[] bytes = new byte[length];
		in.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	static void writeVector(ByteBuffer out, Vector3 v) {
		out.putFloat(v.x).putFloat(v.y).putFloat(v.z);
	}

	static Vector3 readVector(ByteBuffer in, Vector3 out) {
		return out.set(in.getFloat(), in.getFloat(), in.getFloat());
	}

	/** Trims and limits a player name; never returns an empty name. */
	public static String sanitizeName(String name) {
		String clean = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
		if (clean.length() > Protocol.MAX_NAME_LENGTH)
			clean = clean.substring(0, Protocol.MAX_NAME_LENGTH);
		return clean.isEmpty() ? "Jugador" : clean;
	}

	// ---- client -> server ----

	public static class Hello implements Message {
		public int version = Protocol.VERSION;
		public String name = "";

		public byte type() {
			return Protocol.HELLO;
		}

		public void write(ByteBuffer out) {
			out.putInt(version);
			writeString(out, name, Protocol.MAX_NAME_LENGTH);
		}

		public static Hello read(ByteBuffer in) {
			Hello m = new Hello();
			m.version = in.getInt();
			m.name = readString(in);
			return m;
		}
	}

	/** Sent ~30 times per second. Only the newest one matters, so it travels unreliably. */
	public static class PlayerState implements Message {
		/** Increases with every state; older ones arriving late are dropped. */
		public int sequence;
		/** Matches the server's teleport counter: states sent before a respawn/correction are ignored. */
		public int teleportId;
		public long clientTimeMs;
		public final Vector3 position = new Vector3();
		public float yaw, pitch;
		public int slot;
		/** Ammo of the gun in hand, so the server can drop it with the right ammo if the player dies. */
		public int magazine, reserve;
		public boolean crouched;

		public byte type() {
			return Protocol.PLAYER_STATE;
		}

		public void write(ByteBuffer out) {
			out.putInt(sequence).putInt(teleportId).putLong(clientTimeMs);
			writeVector(out, position);
			out.putFloat(yaw).putFloat(pitch).put((byte) slot);
			out.putShort((short) magazine).putShort((short) reserve);
			out.put((byte) (crouched ? 1 : 0));
		}

		public static PlayerState read(ByteBuffer in) {
			PlayerState m = new PlayerState();
			m.sequence = in.getInt();
			m.teleportId = in.getInt();
			m.clientTimeMs = in.getLong();
			readVector(in, m.position);
			m.yaw = in.getFloat();
			m.pitch = in.getFloat();
			m.slot = in.get();
			m.magazine = in.getShort();
			m.reserve = in.getShort();
			m.crouched = (in.get() & 1) != 0;
			return m;
		}
	}

	/** Throw the gun in a slot, with the ammo it has left. */
	public static class DropWeapon implements Message {
		public int slot, magazine, reserve;

		public byte type() {
			return Protocol.DROP_WEAPON;
		}

		public void write(ByteBuffer out) {
			out.put((byte) slot).putShort((short) magazine).putShort((short) reserve);
		}

		public static DropWeapon read(ByteBuffer in) {
			DropWeapon m = new DropWeapon();
			m.slot = in.get();
			m.magazine = in.getShort();
			m.reserve = in.getShort();
			return m;
		}
	}

	/** Take a gun from the ground. The ammo is the one of the gun it replaces (thrown in its place), if any. */
	public static class PickupWeapon implements Message {
		public int weaponId, magazine, reserve;

		public byte type() {
			return Protocol.PICKUP_WEAPON;
		}

		public void write(ByteBuffer out) {
			out.putInt(weaponId).putShort((short) magazine).putShort((short) reserve);
		}

		public static PickupWeapon read(ByteBuffer in) {
			PickupWeapon m = new PickupWeapon();
			m.weaponId = in.getInt();
			m.magazine = in.getShort();
			m.reserve = in.getShort();
			return m;
		}
	}

	public static class Fire implements Message {
		public int slot;
		public final Vector3 origin = new Vector3();
		public final Vector3 direction = new Vector3();

		public byte type() {
			return Protocol.FIRE;
		}

		public void write(ByteBuffer out) {
			out.put((byte) slot);
			writeVector(out, origin);
			writeVector(out, direction);
		}

		public static Fire read(ByteBuffer in) {
			Fire m = new Fire();
			m.slot = in.get();
			readVector(in, m.origin);
			readVector(in, m.direction);
			return m;
		}
	}

	/** No payload: the client is leaving (sent unreliably a few times), or the server is shutting down. */
	public static class Disconnect implements Message {
		public byte type() {
			return Protocol.DISCONNECT;
		}

		public void write(ByteBuffer out) {
		}
	}

	// ---- server -> client ----

	public static class Welcome implements Message {
		public int playerId;
		public int teleportId;
		/** 0 = no frag limit. */
		public int fragLimit;
		public String map = "";
		public final Vector3 spawn = new Vector3();

		public byte type() {
			return Protocol.WELCOME;
		}

		public void write(ByteBuffer out) {
			out.put((byte) playerId).putInt(teleportId).putShort((short) fragLimit);
			writeString(out, map, 32);
			writeVector(out, spawn);
		}

		public static Welcome read(ByteBuffer in) {
			Welcome m = new Welcome();
			m.playerId = in.get() & 0xFF;
			m.teleportId = in.getInt();
			m.fragLimit = in.getShort() & 0xFFFF;
			m.map = readString(in);
			readVector(in, m.spawn);
			return m;
		}
	}

	public static class Reject implements Message {
		public String reason = "";

		public byte type() {
			return Protocol.REJECT;
		}

		public void write(ByteBuffer out) {
			writeString(out, reason, 64);
		}

		public static Reject read(ByteBuffer in) {
			Reject m = new Reject();
			m.reason = readString(in);
			return m;
		}
	}

	public static class PlayerJoined implements Message {
		public int playerId;
		public String name = "";

		public byte type() {
			return Protocol.PLAYER_JOINED;
		}

		public void write(ByteBuffer out) {
			out.put((byte) playerId);
			writeString(out, name, Protocol.MAX_NAME_LENGTH);
		}

		public static PlayerJoined read(ByteBuffer in) {
			PlayerJoined m = new PlayerJoined();
			m.playerId = in.get() & 0xFF;
			m.name = readString(in);
			return m;
		}
	}

	/** Client to server: "call me this from now on". */
	public static class Rename implements Message {
		public String name = "";

		public byte type() {
			return Protocol.RENAME;
		}

		public void write(ByteBuffer out) {
			writeString(out, name, Protocol.MAX_NAME_LENGTH);
		}

		public static Rename read(ByteBuffer in) {
			Rename m = new Rename();
			m.name = readString(in);
			return m;
		}
	}

	/** Server to everyone: a player changed their name. */
	public static class PlayerRenamed implements Message {
		public int playerId;
		public String name = "";

		public byte type() {
			return Protocol.PLAYER_RENAMED;
		}

		public void write(ByteBuffer out) {
			out.put((byte) playerId);
			writeString(out, name, Protocol.MAX_NAME_LENGTH);
		}

		public static PlayerRenamed read(ByteBuffer in) {
			PlayerRenamed m = new PlayerRenamed();
			m.playerId = in.get() & 0xFF;
			m.name = readString(in);
			return m;
		}
	}

	public static class PlayerLeft implements Message {
		public int playerId;

		public byte type() {
			return Protocol.PLAYER_LEFT;
		}

		public void write(ByteBuffer out) {
			out.put((byte) playerId);
		}

		public static PlayerLeft read(ByteBuffer in) {
			PlayerLeft m = new PlayerLeft();
			m.playerId = in.get() & 0xFF;
			return m;
		}
	}

	public static class Kill implements Message {
		public int killerId, victimId;
		public boolean headshot;
		public String gun = "";

		public byte type() {
			return Protocol.KILL;
		}

		public void write(ByteBuffer out) {
			out.put((byte) killerId).put((byte) victimId).put((byte) (headshot ? 1 : 0));
			writeString(out, gun, 16);
		}

		public static Kill read(ByteBuffer in) {
			Kill m = new Kill();
			m.killerId = in.get() & 0xFF;
			m.victimId = in.get() & 0xFF;
			m.headshot = in.get() != 0;
			m.gun = readString(in);
			return m;
		}
	}

	public static class MatchEnd implements Message {
		/** {@link Protocol#WORLD} if nobody scored. */
		public int winnerId;
		/** Ordinal of {@link com.juego.match.Match.EndReason}. */
		public int reason;

		public byte type() {
			return Protocol.MATCH_END;
		}

		public void write(ByteBuffer out) {
			out.put((byte) winnerId).put((byte) reason);
		}

		public static MatchEnd read(ByteBuffer in) {
			MatchEnd m = new MatchEnd();
			m.winnerId = in.get() & 0xFF;
			m.reason = in.get();
			return m;
		}
	}

	/** Someone fired: which gun (for the sound) and where the bullet went (for the tracer). */
	public static class Shot implements Message {
		public int shooterId;
		public String gun = "";
		public final Vector3 origin = new Vector3();
		/** Where the bullet stopped: what it hit, or the end of the gun's range. */
		public final Vector3 end = new Vector3();

		public byte type() {
			return Protocol.SHOT;
		}

		public void write(ByteBuffer out) {
			out.put((byte) shooterId);
			writeString(out, gun, 16);
			writeVector(out, origin);
			writeVector(out, end);
		}

		public static Shot read(ByteBuffer in) {
			Shot m = new Shot();
			m.shooterId = in.get() & 0xFF;
			m.gun = readString(in);
			readVector(in, m.origin);
			readVector(in, m.end);
			return m;
		}
	}

	/** A shot hit a player, for everyone near enough to see the blood and hear it. */
	public static class Hit implements Message {
		public int victimId;
		public boolean headshot;
		/** Where the bullet hit the body. */
		public final Vector3 position = new Vector3();
		/** Which way the bullet was going (normalized): the blood sprays that way. */
		public final Vector3 direction = new Vector3();

		public byte type() {
			return Protocol.HIT;
		}

		public void write(ByteBuffer out) {
			out.put((byte) victimId).put((byte) (headshot ? 1 : 0));
			writeVector(out, position);
			writeVector(out, direction);
		}

		public static Hit read(ByteBuffer in) {
			Hit m = new Hit();
			m.victimId = in.get() & 0xFF;
			m.headshot = in.get() != 0;
			readVector(in, m.position);
			readVector(in, m.direction);
			return m;
		}
	}

	public static class WeaponSpawn implements Message {
		public int weaponId;
		public String gun = "";
		public final Vector3 from = new Vector3();
		public final Vector3 position = new Vector3();
		public float yaw;
		public int magazine, reserve;
		/** {@link Protocol#WORLD} for guns placed by the level. */
		public int droppedBy;

		public byte type() {
			return Protocol.WEAPON_SPAWN;
		}

		public void write(ByteBuffer out) {
			out.putInt(weaponId);
			writeString(out, gun, 16);
			writeVector(out, from);
			writeVector(out, position);
			out.putFloat(yaw).putShort((short) magazine).putShort((short) reserve).put((byte) droppedBy);
		}

		public static WeaponSpawn read(ByteBuffer in) {
			WeaponSpawn m = new WeaponSpawn();
			m.weaponId = in.getInt();
			m.gun = readString(in);
			readVector(in, m.from);
			readVector(in, m.position);
			m.yaw = in.getFloat();
			m.magazine = in.getShort();
			m.reserve = in.getShort();
			m.droppedBy = in.get() & 0xFF;
			return m;
		}
	}

	public static class WeaponRemove implements Message {
		public int weaponId;
		/** Who took it, or {@link Protocol#WORLD} if it despawned. */
		public int pickerId;

		public byte type() {
			return Protocol.WEAPON_REMOVE;
		}

		public void write(ByteBuffer out) {
			out.putInt(weaponId).put((byte) pickerId);
		}

		public static WeaponRemove read(ByteBuffer in) {
			WeaponRemove m = new WeaponRemove();
			m.weaponId = in.getInt();
			m.pickerId = in.get() & 0xFF;
			return m;
		}
	}

	/** You got hit for {@code amount}, by a shot fired from {@code source}. */
	public static class Damage implements Message {
		public int amount;
		public final Vector3 source = new Vector3();

		public byte type() {
			return Protocol.DAMAGE;
		}

		public void write(ByteBuffer out) {
			out.put((byte) Math.min(amount, 255));
			writeVector(out, source);
		}

		public static Damage read(ByteBuffer in) {
			Damage m = new Damage();
			m.amount = in.get() & 0xFF;
			readVector(in, m.source);
			return m;
		}
	}

	/** A player (re)appears at a position. Also used to correct a rejected movement (SET_POSITION). */
	public static class Teleport implements Message {
		private final byte type;
		public int playerId;
		public int teleportId;
		public final Vector3 position = new Vector3();

		public Teleport(byte type) {
			this.type = type;
		}

		public byte type() {
			return type;
		}

		public void write(ByteBuffer out) {
			out.put((byte) playerId).putInt(teleportId);
			writeVector(out, position);
		}

		public static Teleport read(byte type, ByteBuffer in) {
			Teleport m = new Teleport(type);
			m.playerId = in.get() & 0xFF;
			m.teleportId = in.getInt();
			readVector(in, m.position);
			return m;
		}
	}

	public static class Snapshot implements Message {
		public static class Entry {
			public int playerId;
			public boolean alive, crouched;
			public int health, kills, deaths, assists, slot;
			public final Vector3 position = new Vector3();
			public float yaw, pitch;
		}

		public int tick;
		/** Ordinal of {@link com.juego.match.Match.Phase}. */
		public int phase;
		/** Time left in the current phase. */
		public int remainingMs;
		public final Array<Entry> players = new Array<>();

		public byte type() {
			return Protocol.SNAPSHOT;
		}

		public void write(ByteBuffer out) {
			out.putInt(tick).put((byte) phase).putInt(remainingMs).put((byte) players.size);
			for (Entry e : players) {
				out.put((byte) e.playerId).put((byte) ((e.alive ? 1 : 0) | (e.crouched ? 2 : 0))).put((byte) e.health);
				out.putShort((short) e.kills).putShort((short) e.deaths).putShort((short) e.assists).put((byte) e.slot);
				writeVector(out, e.position);
				out.putFloat(e.yaw).putFloat(e.pitch);
			}
		}

		public static Snapshot read(ByteBuffer in) {
			Snapshot m = new Snapshot();
			m.tick = in.getInt();
			m.phase = in.get();
			m.remainingMs = in.getInt();
			int count = in.get() & 0xFF;
			for (int i = 0; i < count; i++) {
				Entry e = new Entry();
				e.playerId = in.get() & 0xFF;
				int flags = in.get();
				e.alive = (flags & 1) != 0;
				e.crouched = (flags & 2) != 0;
				e.health = in.get() & 0xFF;
				e.kills = in.getShort() & 0xFFFF;
				e.deaths = in.getShort() & 0xFFFF;
				e.assists = in.getShort() & 0xFFFF;
				e.slot = in.get();
				readVector(in, e.position);
				e.yaw = in.getFloat();
				e.pitch = in.getFloat();
				m.players.add(e);
			}
			return m;
		}
	}
}
