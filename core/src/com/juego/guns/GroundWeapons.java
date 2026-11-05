package com.juego.guns;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.juego.utils.CharacterShape;

/**
 * Guns lying on the ground: the ones players drop (by hand or when they die)
 * and the ones placed on the level by weapon spawners. Pure logic that
 * doesn't know about rendering or networking, so the same rules run offline,
 * on the server, and as the client's mirror of the server.
 * <p>
 * Positions are on the floor. Player positions are the center of the
 * character capsule, like everywhere else in the game.
 */
public class GroundWeapons {
	/** How close (horizontally, from the capsule center) a player has to be to pick a gun up. */
	public static final float PICKUP_RADIUS = 3.5f;
	/** Vertical reach: the gun lies about one capsule half-height below the player's center. */
	public static final float PICKUP_HEIGHT = CharacterShape.HALF_HEIGHT + 2f;
	/**
	 * For this long, whoever dropped a gun doesn't pick it up again just by
	 * standing on it. Pressing the pickup key works right away.
	 */
	public static final long DROPPER_BLOCK_MS = 1500;
	/** Dropped guns disappear after this long, so the level doesn't fill up. */
	public static final long DESPAWN_MS = 60_000;
	/** At most this many dropped guns at once; the oldest goes first. */
	public static final int MAX_DROPPED = 16;
	/** A spawner puts a new gun this long after its gun was taken. */
	public static final long SPAWNER_RESPAWN_MS = 30_000;
	/** How far in front of the player a thrown gun lands. */
	public static final float THROW_DISTANCE = 3.5f;
	/**
	 * Looking at a gun means the angle to it is under ~25 degrees. A wider cone
	 * would also catch guns lying ahead while looking straight forward, and G
	 * would pick up instead of drop.
	 */
	private static final float AIM_COS = 0.9f;

	/** The level geometry, to find walls and the floor. */
	public interface Raycaster {
		/**
		 * @return the distance to the first level surface between the points, or a
		 *         negative value if there is none; the hit point goes in {@code outHit}
		 */
		float cast(Vector3 from, Vector3 to, Vector3 outHit);
	}

	public interface Listener {
		void spawned(Weapon weapon);

		/** @param pickerId who took it, or {@link #NOBODY} if it despawned or the round was reset */
		void removed(Weapon weapon, int pickerId);
	}

	public static final int NOBODY = 0;

	public static final class Weapon {
		public final int id;
		public final GunDefinition def;
		public final int magazine, reserve;
		/** Where it was thrown from (equal to {@link #position} if it was placed, not thrown). */
		public final Vector3 from = new Vector3();
		/** Resting point on the floor. */
		public final Vector3 position = new Vector3();
		/** Which way it points on the floor, in degrees. */
		public final float yaw;
		/** Player that dropped it, or {@link #NOBODY} for spawner guns. */
		public final int droppedBy;
		final long blockedUntilMs, despawnAtMs;
		final Spawner spawner;

		Weapon(int id, GunDefinition def, int magazine, int reserve, float yaw, int droppedBy, long blockedUntilMs,
				long despawnAtMs, Spawner spawner) {
			this.id = id;
			this.def = def;
			this.magazine = magazine;
			this.reserve = reserve;
			this.yaw = yaw;
			this.droppedBy = droppedBy;
			this.blockedUntilMs = blockedUntilMs;
			this.despawnAtMs = despawnAtMs;
			this.spawner = spawner;
		}

		/** Whether walking over it gives it to {@code playerId} now (the dropper has to wait a moment). */
		public boolean canBeTakenBy(int playerId, long nowMs) {
			return playerId != droppedBy || nowMs >= blockedUntilMs;
		}
	}

	private static final class Spawner {
		final GunDefinition def;
		final Vector3 position = new Vector3();
		/** The gun it placed, while nobody has taken it. */
		Weapon current;
		long respawnAtMs;

		Spawner(GunDefinition def) {
			this.def = def;
		}
	}

	private final Raycaster raycaster;
	private final Listener listener;
	private final IntMap<Weapon> weapons = new IntMap<>();
	/** Dropped (not spawner) guns, oldest first. */
	private final Array<Weapon> dropped = new Array<>();
	private final Array<Spawner> spawners = new Array<>();
	private int nextId = 1;
	private final Vector3 tmp = new Vector3(), tmp2 = new Vector3(), hit = new Vector3();

	/** @param raycaster null for a client mirror, which never places guns itself */
	public GroundWeapons(Raycaster raycaster, Listener listener) {
		this.raycaster = raycaster;
		this.listener = listener;
	}

	// ---- authority (offline game and server) ----

	/** A spot on the level where a gun appears and comes back a while after someone takes it. */
	public void addSpawner(GunDefinition def, Vector3 approximatePosition) {
		Spawner spawner = new Spawner(def);
		spawner.position.set(floorBelow(approximatePosition, tmp));
		spawners.add(spawner);
	}

	/**
	 * Throws a gun from a player's hands: it lands on the floor in front of them,
	 * or closer if there is a wall.
	 *
	 * @param center player's capsule center
	 * @param yaw    which way the player looks, in degrees (0 = +Z)
	 */
	public Weapon toss(GunDefinition def, int magazine, int reserve, Vector3 center, float yaw, int playerId,
			long nowMs) {
		float sin = MathUtils.sinDeg(yaw), cos = MathUtils.cosDeg(yaw);
		Vector3 target = tmp.set(center).add(sin * THROW_DISTANCE, 0, cos * THROW_DISTANCE);
		float wall = raycaster.cast(center, target, hit);
		if (wall >= 0)
			target.set(center).add(sin * Math.max(0, wall - 1f), 0, cos * Math.max(0, wall - 1f));
		Vector3 landing = floorBelow(target, tmp2);
		Vector3 from = tmp.set(center).add(sin * 1.2f, -0.8f, cos * 1.2f);
		return addDropped(def, magazine, reserve, from, landing, yaw + 70, playerId, nowMs);
	}

	/** Drops a gun where a player stands (when they die). */
	public Weapon dropAtFeet(GunDefinition def, int magazine, int reserve, Vector3 center, float yaw, int playerId,
			long nowMs) {
		Vector3 landing = floorBelow(center, tmp2);
		Vector3 from = tmp.set(center).add(0, -1f, 0);
		return addDropped(def, magazine, reserve, from, landing, yaw + 90, playerId, nowMs);
	}

	private Weapon addDropped(GunDefinition def, int magazine, int reserve, Vector3 from, Vector3 landing,
			float yaw, int playerId, long nowMs) {
		if (dropped.size >= MAX_DROPPED)
			remove(dropped.first(), NOBODY, nowMs);
		Weapon w = new Weapon(nextId++, def, magazine, reserve, yaw, playerId, nowMs + DROPPER_BLOCK_MS,
				nowMs + DESPAWN_MS, null);
		w.from.set(from);
		w.position.set(landing);
		dropped.add(w);
		add(w);
		return w;
	}

	/** The floor under a point, or the point itself lowered to foot level if there is none. */
	private Vector3 floorBelow(Vector3 point, Vector3 out) {
		if (raycaster.cast(point, tmp2.set(point).add(0, -40, 0), hit) >= 0)
			return out.set(hit);
		return out.set(point).add(0, -CharacterShape.HALF_HEIGHT, 0);
	}

	/** Takes a gun off the ground. */
	public void remove(Weapon weapon, int pickerId, long nowMs) {
		if (weapons.remove(weapon.id) == null)
			return;
		dropped.removeValue(weapon, true);
		if (weapon.spawner != null) {
			weapon.spawner.current = null;
			weapon.spawner.respawnAtMs = nowMs + SPAWNER_RESPAWN_MS;
		}
		listener.removed(weapon, pickerId);
	}

	/** Despawns old dropped guns and refills spawners. */
	public void update(long nowMs) {
		while (dropped.size > 0 && nowMs >= dropped.first().despawnAtMs)
			remove(dropped.first(), NOBODY, nowMs);
		for (Spawner s : spawners) {
			if (s.current == null && nowMs >= s.respawnAtMs) {
				Weapon w = new Weapon(nextId++, s.def, s.def.magazineSize, s.def.reserveAmmo, spawnerYaw(s),
						NOBODY, 0, Long.MAX_VALUE, s);
				w.from.set(s.position);
				w.position.set(s.position);
				s.current = w;
				add(w);
			}
		}
	}

	private static float spawnerYaw(Spawner s) {
		return (s.position.x * 13 + s.position.z * 7) % 360; // fixed per spot, but not all aligned
	}

	/** New round: nothing on the ground, every spawner full again. */
	public void reset(long nowMs) {
		for (Weapon w : weapons.values().toArray())
			remove(w, NOBODY, nowMs);
		for (Spawner s : spawners)
			s.respawnAtMs = 0;
		update(nowMs);
	}

	// ---- client mirror (online) ----

	/** A gun the server says is on the ground. */
	public Weapon addRemote(int id, GunDefinition def, int magazine, int reserve, Vector3 from, Vector3 position,
			float yaw, int droppedBy, long nowMs) {
		Weapon w = new Weapon(id, def, magazine, reserve, yaw, droppedBy, nowMs + DROPPER_BLOCK_MS, Long.MAX_VALUE,
				null);
		w.from.set(from);
		w.position.set(position);
		add(w);
		return w;
	}

	/** The server says this gun is gone. */
	public void removeRemote(int id, int pickerId) {
		Weapon w = weapons.remove(id);
		if (w != null)
			listener.removed(w, pickerId);
	}

	// ---- queries ----

	private void add(Weapon w) {
		weapons.put(w.id, w);
		listener.spawned(w);
	}

	public Weapon get(int id) {
		return weapons.get(id);
	}

	/** A copy, safe to iterate while guns are added or removed. */
	public Array<Weapon> all() {
		return weapons.values().toArray();
	}

	public int size() {
		return weapons.size;
	}

	/** Whether a player standing at {@code center} can reach the gun. {@code slack} widens it (server). */
	public static boolean inReach(Weapon w, Vector3 center, float slack) {
		float dx = w.position.x - center.x, dz = w.position.z - center.z;
		float radius = PICKUP_RADIUS + slack;
		return dx * dx + dz * dz <= radius * radius && Math.abs(w.position.y - center.y) <= PICKUP_HEIGHT + slack;
	}

	/**
	 * The reachable gun the player is looking at most directly, or null.
	 *
	 * @param center capsule center (for the reach)
	 * @param eye    where the player looks from
	 */
	public Weapon findAimed(Vector3 center, Vector3 eye, Vector3 viewDirection) {
		Weapon best = null;
		float bestDot = AIM_COS;
		for (Weapon w : weapons.values()) {
			if (!inReach(w, center, 0))
				continue;
			float dot = tmp.set(w.position).sub(eye).nor().dot(viewDirection);
			if (dot > bestDot) {
				bestDot = dot;
				best = w;
			}
		}
		return best;
	}

	/**
	 * A gun the player walks over and can take without pressing anything:
	 * the closest reachable one for a slot they have free.
	 *
	 * @param freeSlots indexed by {@link GunDefinition.Slot#ordinal()}
	 */
	public Weapon findAutoPickup(Vector3 center, boolean[] freeSlots, int playerId, long nowMs) {
		Weapon best = null;
		float bestDistance = Float.MAX_VALUE;
		for (Weapon w : weapons.values()) {
			if (!freeSlots[w.def.slot.ordinal()] || !inReach(w, center, 0) || !w.canBeTakenBy(playerId, nowMs))
				continue;
			float distance = w.position.dst2(center);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = w;
			}
		}
		return best;
	}
}
