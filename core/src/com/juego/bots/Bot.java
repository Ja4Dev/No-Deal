package com.juego.bots;

import java.util.Random;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.juego.bullet.CharacterBody;
import com.juego.guns.GunDefinition;
import com.juego.guns.Recoil;
import com.juego.utils.CharacterShape;
import com.juego.utils.PlayerMovement;

/**
 * A computer-controlled player on the server. It plays by the same rules as
 * people: it walks with the same physics and speeds, its gun has the same
 * fire rate, magazine, spray pattern and inaccuracy (running makes it miss),
 * and it can only shoot what it can see. Its {@link BotDifficulty} sets the
 * human limits on top: reaction time, how fast and precisely it aims, spray
 * control, and how it moves in a fight.
 * <p>
 * Each tick it looks for enemies, aims, shoots, and otherwise hunts: it walks
 * to where it last saw or heard someone, or to the nearest enemy.
 */
public class Bot {
	/** What the bot can do in the match; implemented by the server. */
	public interface Host {
		/** The bot pulls the trigger: a shot from {@code origin} along {@code direction} (normalized). */
		void fire(Bot bot, Vector3 origin, Vector3 direction);
	}

	/** Another player, as the bot sees it. */
	public static final class Target {
		public int id;
		public boolean alive;
		/** Capsule center. */
		public final Vector3 position = new Vector3();
	}

	/** How long the bot remembers where it saw an enemy, or heard a shot. */
	static final long MEMORY_MS = 6000;
	/** How far a shot is heard. */
	static final float HEARING_RANGE = 70f;
	/** Waypoints count as reached this close (horizontally). */
	private static final float WAYPOINT_RADIUS = 2.5f;
	private static final long REPATH_MS = 1500;
	private static final long STUCK_CHECK_MS = 900;
	/** Hard bots walk towards enemies farther than this while they fight. */
	private static final float CLOSE_IN_DISTANCE = 40f;
	/** Head and chest, above the capsule center. */
	private static final float HEAD_AIM = CharacterShape.HEAD_OFFSET + 0.5f, CHEST_AIM = 0.4f;

	public final int id;
	public final String name;
	private BotDifficulty difficulty;
	private final BotWorld world;
	private final NavGrid nav;
	private final Random random;
	public final CharacterBody body;

	/** Capsule center, after the last physics step. */
	public final Vector3 position = new Vector3();
	private final Vector3 velocity = new Vector3();
	private final Vector3 wishDirection = new Vector3();
	private float wishSpeed;
	public float yaw, pitch;
	private boolean onGround = true;

	// The gun in hand and its ammo.
	private GunDefinition gun;
	private int magazine, reserve;
	private long reloadDoneMs, lastShotMs = Long.MIN_VALUE / 2;
	private final Recoil recoil = new Recoil();

	// What the bot knows about enemies.
	private int targetId = -1;
	private boolean targetVisible;
	private final Vector3 targetPosition = new Vector3();
	private long noticedMs, lastSeenMs = Long.MIN_VALUE / 2;
	private final Vector3 lastSeen = new Vector3();
	private boolean aimAtHead;
	private final Vector3 heard = new Vector3();
	private long heardMs = Long.MIN_VALUE / 2;
	private int attackerId = -1;
	private long attackedMs = Long.MIN_VALUE / 2;

	// Aim: the error wanders smoothly around the target, more right after spotting it.
	private float errorYaw, errorPitch, errorGoalYaw, errorGoalPitch;
	private long nextErrorMs;
	private int burstLeft;
	private long burstPauseUntilMs;

	// Moving around.
	private final Array<Vector3> path = new Array<>();
	private int pathIndex;
	private final Vector3 goal = new Vector3();
	private boolean hasGoal;
	private long repathMs, goalUntilMs;
	private float strafeSign = 1;
	private long strafeSwitchMs;
	private final Vector3 stuckFrom = new Vector3();
	private long stuckCheckMs;
	private int stuckCount;

	private final Vector3 eye = new Vector3(), aimPoint = new Vector3(), tmp = new Vector3(), tmp2 = new Vector3();
	private final Vector3 stepFrom = new Vector3(), commanded = new Vector3();
	private final Quaternion aimRotation = new Quaternion();

	public Bot(int id, String name, BotDifficulty difficulty, BotWorld world, NavGrid nav, Vector3 spawn,
			Random random) {
		this.id = id;
		this.name = name;
		this.difficulty = difficulty;
		this.world = world;
		this.nav = nav;
		this.random = random;
		body = world.createBody(spawn);
		position.set(spawn);
	}

	public BotDifficulty getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(BotDifficulty difficulty) {
		this.difficulty = difficulty;
	}

	/** Back in the match at {@code spawn}, with a fresh gun and no memories of the last life. */
	public void respawn(Vector3 spawn, GunDefinition gun, float yaw) {
		if (!body.isInWorld())
			world.add(body);
		body.warp(spawn);
		position.set(spawn);
		velocity.setZero();
		this.yaw = yaw;
		pitch = 0;
		equip(gun);
		targetId = -1;
		targetVisible = false;
		lastSeenMs = heardMs = attackedMs = Long.MIN_VALUE / 2;
		attackerId = -1;
		hasGoal = false;
		path.clear();
		stuckCount = 0;
	}

	/** Dead: out of the physics world until it respawns. */
	public void die() {
		body.removeFromWorld();
		velocity.setZero();
		path.clear();
	}

	public void equip(GunDefinition gun) {
		this.gun = gun;
		magazine = gun.magazineSize;
		reserve = gun.reserveAmmo;
		reloadDoneMs = 0;
		recoil.reset();
		burstLeft = 0;
	}

	public int getMagazine() {
		return magazine;
	}

	public int getReserve() {
		return reserve;
	}

	/** Someone shot nearby: worth a look. */
	public void hearShot(int shooterId, Vector3 origin, long nowMs) {
		if (shooterId == id || origin.dst(position) > HEARING_RANGE)
			return;
		heard.set(origin);
		heardMs = nowMs;
	}

	/** Got hit: the attacker is noticed even from behind. */
	public void hurtBy(int attackerId, Vector3 origin, long nowMs) {
		this.attackerId = attackerId;
		attackedMs = nowMs;
		heard.set(origin);
		heardMs = nowMs;
	}

	// ---- thinking ----

	/**
	 * One tick of decisions: who to fight, where to look, whether to shoot and
	 * where to go. Call before {@link #move()} and the physics step.
	 *
	 * @param fighting false between matches: the bot stands still
	 */
	public void think(long nowMs, float dt, Array<Target> targets, boolean fighting, Host host) {
		eye.set(position).add(0, CharacterShape.EYE_OFFSET, 0);
		finishReload(nowMs);
		recoil.update(gun, dt, nowMs);
		wishDirection.setZero();
		wishSpeed = PlayerMovement.RUN_SPEED;
		if (!fighting) {
			targetId = -1;
			return;
		}
		Target target = perceive(nowMs, targets);
		if (target != null) {
			fight(target, nowMs, dt, host);
		} else {
			if (magazine < gun.magazineSize / 2)
				startReload(nowMs);
			hunt(nowMs, targets);
			lookAround(nowMs, dt);
		}
	}

	/** Picks the enemy to fight: the one already being fought if still in sight, else the nearest visible one. */
	private Target perceive(long nowMs, Array<Target> targets) {
		Target current = null, nearest = null;
		float nearestDistance = Float.MAX_VALUE;
		for (Target t : targets) {
			if (t.id == id || !t.alive || !canSee(t, nowMs))
				continue;
			if (t.id == targetId)
				current = t;
			float d = t.position.dst(position);
			if (d < nearestDistance) {
				nearestDistance = d;
				nearest = t;
			}
		}
		Target chosen = current != null ? current : nearest;
		if (chosen == null) {
			targetVisible = false;
			return null;
		}
		boolean fresh = chosen.id != targetId || nowMs - lastSeenMs > 1000;
		if (fresh) {
			targetId = chosen.id;
			noticedMs = nowMs;
			burstLeft = 0;
			aimAtHead = random.nextFloat() < difficulty.headshotChance;
			// Right after spotting someone the aim is off by more; it settles while tracking.
			errorYaw = errorGoalYaw = randomError() * 2.5f;
			errorPitch = errorGoalPitch = randomError() * 1.5f;
			nextErrorMs = nowMs + 300;
		}
		targetVisible = true;
		targetPosition.set(chosen.position);
		lastSeen.set(chosen.position);
		lastSeenMs = nowMs;
		return chosen;
	}

	/** In range, in the field of view (or the one shooting at us, or what we heard) and not behind a wall. */
	private boolean canSee(Target t, long nowMs) {
		float distance = t.position.dst(position);
		boolean alerted = t.id == attackerId && nowMs - attackedMs < 3000
				|| nowMs - heardMs < 2000 && t.position.dst(heard) < 8;
		if (distance > difficulty.sightRange * (alerted ? 1.5f : 1f))
			return false;
		if (!alerted && t.id != targetId) {
			float toTarget = MathUtils.atan2(t.position.x - position.x, t.position.z - position.z)
					* MathUtils.radiansToDegrees;
			if (Math.abs(angleDelta(yaw, toTarget)) > difficulty.fieldOfView / 2)
				return false;
		}
		// Head or chest visible.
		return world.isClear(eye, tmp.set(t.position).add(0, HEAD_AIM, 0))
				|| world.isClear(eye, tmp.set(t.position).add(0, CHEST_AIM, 0));
	}

	private void fight(Target target, long nowMs, float dt, Host host) {
		float distance = target.position.dst(position);
		aimPoint.set(target.position).add(0, aimAtHead ? HEAD_AIM : CHEST_AIM, 0);
		float idealYaw = yawTo(aimPoint), idealPitch = pitchTo(aimPoint);

		// Aim error: a target that moves around every few hundred ms, smaller the longer we track.
		if (nowMs >= nextErrorMs) {
			float settle = 1 + 1.5f * (float) Math.exp(-(nowMs - noticedMs) / 700.0);
			errorGoalYaw = randomError() * settle;
			errorGoalPitch = randomError() * settle * 0.6f;
			nextErrorMs = nowMs + 250 + random.nextInt(350);
		}
		float blend = 1 - (float) Math.exp(-6 * dt);
		errorYaw += (errorGoalYaw - errorYaw) * blend;
		errorPitch += (errorGoalPitch - errorPitch) * blend;
		// Spray control: aiming against the pattern (bullets go to aim - pattern).
		float wantYaw = idealYaw + errorYaw + recoil.right() * difficulty.sprayControl;
		float wantPitch = idealPitch + errorPitch + recoil.up() * difficulty.sprayControl;
		turnTowards(wantYaw, wantPitch, dt);

		moveInFight(target, distance, nowMs);

		boolean reacted = nowMs - noticedMs >= difficulty.reactionMs;
		if (!reacted || reloadDoneMs != 0)
			return;
		if (magazine <= 0) {
			startReload(nowMs);
			return;
		}
		if (nowMs - lastShotMs < gun.fireDelayMs || nowMs < burstPauseUntilMs)
			return;
		// Only shoot when the crosshair is roughly on the body.
		float tolerance = Math.max(1.2f, (float) Math.toDegrees(Math.atan2(CharacterShape.HITBOX_RADIUS * 0.8f, distance)));
		float off = Math.max(Math.abs(angleDelta(yaw, idealYaw + recoil.right() * difficulty.sprayControl)),
				Math.abs(pitch - (idealPitch + recoil.up() * difficulty.sprayControl)));
		if (off > tolerance + Math.abs(errorYaw))
			return;
		if (difficulty.combat == BotDifficulty.Combat.STOP_AND_SHOOT && speedFraction() > 0.55f)
			return; // still stopping
		if (burstLeft <= 0) {
			burstLeft = distance < 18 ? difficulty.maxBurst : 1 + random.nextInt(Math.max(1, difficulty.maxBurst / 2));
			aimAtHead = random.nextFloat() < difficulty.headshotChance; // head or chest, every burst
		}
		shoot(nowMs, host);
		if (--burstLeft <= 0)
			burstPauseUntilMs = nowMs + 220 + random.nextInt(260) + (long) (distance * 6)
					+ (difficulty == BotDifficulty.FACIL ? 350 : 0);
	}

	private void shoot(long nowMs, Host host) {
		float cone = recoil.inaccuracy(gun, speedFraction(), !onGround);
		recoil.shoot(gun, nowMs);
		lastShotMs = nowMs;
		magazine--;
		float radius = cone * (float) Math.sqrt(random.nextFloat());
		float angle = random.nextFloat() * MathUtils.PI2;
		float up = recoil.up() + radius * MathUtils.sin(angle);
		float right = recoil.right() + radius * MathUtils.cos(angle);
		aimRotation.setEulerAngles(yaw - right, pitch - up, 0);
		aimRotation.transform(tmp2.set(0, 0, 1));
		host.fire(this, eye, tmp2);
		if (magazine <= 0)
			startReload(nowMs);
	}

	private void moveInFight(Target target, float distance, long nowMs) {
		tmp.set(target.position).sub(position);
		tmp.y = 0;
		tmp.nor();
		boolean reacted = nowMs - noticedMs >= difficulty.reactionMs;
		switch (difficulty.combat) {
		case RUN_AND_GUN:
			if (distance > 25)
				wishDirection.set(tmp);
			else
				strafe(tmp, nowMs, 600, 1400); // running sideways: easy to see, hard to hit with
			break;
		case STOP_AND_SHOOT:
			if (!reacted || reloadDoneMs != 0 || nowMs < burstPauseUntilMs) {
				strafe(tmp, nowMs, 500, 900);
				wishSpeed = PlayerMovement.WALK_SPEED;
			} // else: stand still and shoot
			break;
		case STRAFE:
			strafe(tmp, nowMs, 350, 800);
			wishSpeed = PlayerMovement.WALK_SPEED; // walking doesn't spoil the aim
			if (distance > CLOSE_IN_DISTANCE) // far shots hurt less: get closer while strafing
				wishDirection.add(tmp).nor();
			break;
		}
	}

	/** Sideways to {@code toTarget}, switching side every so often or when a wall is in the way. */
	private void strafe(Vector3 toTarget, long nowMs, int minMs, int maxMs) {
		if (nowMs >= strafeSwitchMs) {
			strafeSign = random.nextBoolean() ? 1 : -1;
			strafeSwitchMs = nowMs + minMs + random.nextInt(maxMs - minMs);
		}
		wishDirection.set(-toTarget.z * strafeSign, 0, toTarget.x * strafeSign);
		tmp2.set(position).mulAdd(wishDirection, CharacterShape.RADIUS + 2f);
		if (!world.isClear(position, tmp2)) {
			strafeSign = -strafeSign;
			wishDirection.scl(-1);
		}
	}

	/** No enemy in sight: go where one was seen or heard, or to the nearest one. */
	private void hunt(long nowMs, Array<Target> targets) {
		boolean newGoal = false;
		if (nowMs - lastSeenMs < MEMORY_MS) {
			newGoal = setGoal(lastSeen);
		} else if (nowMs - heardMs < MEMORY_MS) {
			newGoal = setGoal(heard);
		} else if (!hasGoal || nowMs >= goalUntilMs || reachedGoal()) {
			// Hunting: easy bots often just roam, harder ones head for the nearest enemy.
			Target nearest = null;
			float best = Float.MAX_VALUE;
			for (Target t : targets) {
				if (t.id == id || !t.alive)
					continue;
				float d = t.position.dst2(position);
				if (d < best) {
					best = d;
					nearest = t;
				}
			}
			float huntChance = 0.35f + 0.2f * difficulty.ordinal();
			if (nearest != null && random.nextFloat() < huntChance) {
				newGoal = setGoal(nearest.position);
			} else {
				int node = nav.randomNode(random);
				if (node >= 0)
					newGoal = setGoal(nav.nodePosition(node, tmp));
			}
			goalUntilMs = nowMs + 3000 + random.nextInt(3000);
		}
		if (!hasGoal)
			return;
		if (newGoal || nowMs >= repathMs || pathIndex >= path.size && !reachedGoal()) {
			repathMs = nowMs + REPATH_MS;
			if (!nav.findPath(position, goal, path))
				hasGoal = false;
			pathIndex = 0;
		}
		followPath();
		checkStuck(nowMs);
	}

	/** @return true if the goal moved enough to need a new path */
	private boolean setGoal(Vector3 where) {
		boolean changed = !hasGoal || goal.dst(where) > 6;
		goal.set(where);
		hasGoal = true;
		return changed;
	}

	private boolean reachedGoal() {
		return horizontalDistance(position, goal) < WAYPOINT_RADIUS * 1.5f;
	}

	private void followPath() {
		while (pathIndex < path.size && horizontalDistance(position, path.get(pathIndex)) < WAYPOINT_RADIUS)
			pathIndex++;
		if (pathIndex >= path.size)
			return;
		wishDirection.set(path.get(pathIndex)).sub(position);
		wishDirection.y = 0;
		wishDirection.nor();
	}

	/** Walking into something for a while: find another way, or somewhere else to go. */
	private void checkStuck(long nowMs) {
		if (nowMs < stuckCheckMs)
			return;
		boolean wanted = !wishDirection.isZero();
		if (wanted && horizontalDistance(position, stuckFrom) < 1f) {
			stuckCount++;
			repathMs = 0;
			if (stuckCount >= 3) {
				hasGoal = false;
				stuckCount = 0;
			}
		} else {
			stuckCount = 0;
		}
		stuckFrom.set(position);
		stuckCheckMs = nowMs + STUCK_CHECK_MS;
	}

	/** Without an enemy: look where we go, or where the noise came from. */
	private void lookAround(long nowMs, float dt) {
		float wantYaw = yaw, wantPitch = 0;
		if (nowMs - heardMs < 2500) {
			wantYaw = yawTo(heard);
		} else if (!wishDirection.isZero()) {
			wantYaw = MathUtils.atan2(wishDirection.x, wishDirection.z) * MathUtils.radiansToDegrees;
		}
		turnTowards(wantYaw, wantPitch, dt * 0.6f);
	}

	/** Turns like a hand on a mouse: fast when far off, slowing down near the goal, never faster than the limit. */
	private void turnTowards(float wantYaw, float wantPitch, float dt) {
		float maxStep = difficulty.turnSpeed * dt;
		float ease = 1 - (float) Math.exp(-14 * dt);
		float dy = angleDelta(yaw, wantYaw), dp = wantPitch - pitch;
		yaw += MathUtils.clamp(dy * ease + Math.signum(dy) * Math.min(Math.abs(dy), 0.05f), -maxStep, maxStep);
		pitch += MathUtils.clamp(dp * ease + Math.signum(dp) * Math.min(Math.abs(dp), 0.05f), -maxStep, maxStep);
		pitch = MathUtils.clamp(pitch, -89, 89);
		yaw = ((yaw % 360) + 360) % 360;
	}

	private void startReload(long nowMs) {
		if (reloadDoneMs != 0 || magazine >= gun.magazineSize || reserve == 0)
			return;
		reloadDoneMs = nowMs + RELOAD_MS;
	}

	/** As long as a person's reload. */
	static final long RELOAD_MS = 2000;

	private void finishReload(long nowMs) {
		if (reloadDoneMs == 0 || nowMs < reloadDoneMs)
			return;
		reloadDoneMs = 0;
		int needed = gun.magazineSize - magazine;
		int loaded = reserve < 0 ? needed : Math.min(needed, reserve);
		magazine += loaded;
		if (reserve >= 0)
			reserve -= loaded;
		recoil.reset();
	}

	// ---- moving ----

	/** Turns the decision into the body's movement for the next physics step (same rules as a player). */
	public void move() {
		if (!body.isInWorld())
			return;
		body.getPosition(stepFrom);
		onGround = body.controller.onGround();
		PlayerMovement.step(velocity, wishDirection, wishSpeed, onGround, BotWorld.STEP);
		commanded.set(velocity).scl(BotWorld.STEP);
		body.controller.setWalkDirection(commanded);
	}

	/** After the physics step: where the body ended up, and the velocity it really had (walls stop it). */
	public void afterStep() {
		if (!body.isInWorld())
			return;
		body.getPosition(position);
		tmp.set(position).sub(stepFrom);
		tmp.y = 0;
		float wanted = commanded.len();
		if (wanted > 0.0001f && tmp.len() < wanted * 0.95f)
			velocity.set(tmp).scl(1f / BotWorld.STEP);
	}

	private float speedFraction() {
		return (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) / PlayerMovement.RUN_SPEED;
	}

	// ---- helpers ----

	private float randomError() {
		return (random.nextFloat() * 2 - 1) * difficulty.aimError;
	}

	/** Yaw that looks from the eyes at {@code point} (the player's convention: forward = (sin, 0, cos)). */
	private float yawTo(Vector3 point) {
		return MathUtils.atan2(point.x - eye.x, point.z - eye.z) * MathUtils.radiansToDegrees;
	}

	/** Pitch that looks from the eyes at {@code point}; positive looks down. */
	private float pitchTo(Vector3 point) {
		float dx = point.x - eye.x, dz = point.z - eye.z;
		return -MathUtils.atan2(point.y - eye.y, (float) Math.sqrt(dx * dx + dz * dz)) * MathUtils.radiansToDegrees;
	}

	/** Signed shortest turn from {@code from} to {@code to}, in degrees. */
	static float angleDelta(float from, float to) {
		return ((to - from) % 360 + 540) % 360 - 180;
	}

	private static float horizontalDistance(Vector3 a, Vector3 b) {
		float dx = a.x - b.x, dz = a.z - b.z;
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	public void dispose() {
		body.dispose();
	}
}
