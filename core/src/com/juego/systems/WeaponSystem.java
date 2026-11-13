package com.juego.systems;

import java.util.Random;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.juego.components.CharacterComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.Assets;
import com.juego.core.GameWorld;
import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;
import com.juego.guns.Recoil;
import com.juego.utils.CharacterShape;
import com.juego.utils.Config;
import com.juego.utils.SpatialAudio;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;
import com.juego.utils.PlayerMovement;

/**
 * The local player's guns: hitscan shooting with spray recoil (see
 * {@link Recoil}), reloading, switching, aiming down sights, and the
 * first-person gun moving with your steps and kicking back on each shot.
 */
public class WeaponSystem extends EntitySystem implements EntityListener, Disposable {

	private static final String RELOAD_SOUND = "sounds/Reload.mp3";
	private static final long RELOAD_DELAY_MS = 2000;
	private static final float DEFAULT_FOV = 67f;
	private static final float ZOOM_FOV = 30f;
	/** Aiming down sights steadies the gun: inaccuracy is multiplied by this. */
	private static final float ZOOM_SPREAD = 0.6f;
	/** How fast the first-person gun settles after a shot, per second. */
	private static final float GUN_KICK_RECOVERY = 16f;
	/** Steps per second at full running speed, for the gun's bob. */
	private static final float STEP_RATE = 1.9f;

	private final GameWorld gameWorld;
	private final PerspectiveCamera camera;
	private Entity player;
	private PlayerComponent playerComponent;

	/** First-person model and shot sound of every gun in the game, by gun id: any gun can be picked up. */
	private final ObjectMap<String, Entity> viewModels = new ObjectMap<>();
	private final ObjectMap<String, Sound> shotSounds = new ObjectMap<>();
	/** The gun in hand last frame, to notice when it changes (switch, drop, pickup, respawn). */
	private Gun heldGun;
	private final Array<String> acquired = new Array<>();
	private Sound reloadSound;
	private final Array<Sound> bodyHitSounds = new Array<>(), headHitSounds = new Array<>();
	/** Hits are heard only nearby: as loud as a gunshot this many times farther away. */
	private static final float HIT_SOUND_RANGE_SCALE = 4f;
	/** Blood stains a wall at most this far behind whoever got hit. */
	private static final float SPLATTER_DISTANCE = 7f;
	private long lastShotTime;
	private long lastReloadTime;
	private boolean zoomed;
	private final Recoil recoil = new Recoil();
	/** Radius of the inaccuracy cone right now, in degrees (for the dynamic crosshair). */
	private float spreadDegrees, moveSpreadDegrees, fireSpreadDegrees;
	private float gunKick, bobPhase, bobAmount;
	private final Random random = new Random();

	// Reused math objects (avoid per-frame garbage).
	private final ClosestRayResultCallback rayTestCB = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
	/** Only the level (no characters): where blood lands behind someone who got hit. */
	private final ClosestRayResultCallback levelRay = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
	private final Vector3 rayFrom = new Vector3();
	private final Vector3 rayTo = new Vector3();
	private final Vector3 hitPoint = new Vector3();
	private final Vector3 hitNormal = new Vector3();
	private final Vector3 targetCenter = new Vector3();
	private final Matrix4 targetTransform = new Matrix4();
	private final Vector3 muzzle = new Vector3();
	private final Vector3 tmp = new Vector3();
	private final Vector3 shotDirection = new Vector3();
	private final Ray shotRay = new Ray();
	private final Quaternion aim = new Quaternion();
	private final Quaternion gunTilt = new Quaternion();

	public WeaponSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.gameWorld = gameWorld;
		this.camera = camera;
		levelRay.setCollisionFilterMask(btBroadphaseProxy.CollisionFilterGroups.AllFilter
				& ~btBroadphaseProxy.CollisionFilterGroups.CharacterFilter);
	}

	@Override
	public void addedToEngine(Engine engine) {
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
	}

	@Override
	public void entityAdded(Entity entity) {
		if (!gameWorld.isLocalPlayer(entity))
			return;
		player = entity;
		playerComponent = entity.getComponent(PlayerComponent.class);
		reloadSound = acquire(RELOAD_SOUND);
		for (int i = 0; i < 3; i++) {
			bodyHitSounds.add(acquire("sounds/hits/body-" + i + ".wav"));
			headHitSounds.add(acquire("sounds/hits/head-" + i + ".wav"));
		}
		for (GunDefinition def : Assets.armory.all()) {
			viewModels.put(def.id, gameWorld.entityFactory.createGunView(def));
			shotSounds.put(def.id, acquire(def.sound));
		}
		syncViewModel();
	}

	private Sound acquire(String path) {
		acquired.add(path);
		return Assets.acquire(path, Sound.class);
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
	}

	@Override
	public void update(float deltaTime) {
		if (player == null)
			return;
		syncViewModel(); // the selected gun can also change on respawn, a drop or a pickup
		Gun gun = playerComponent.selectedGun();
		long now = System.currentTimeMillis();
		if (gun != null)
			recoil.update(gun.def, deltaTime, now);
		if (playerComponent.controlsPlayer() && !playerComponent.isDead() && gameWorld.canFight())
			handleInput();
		syncViewModel();
		gun = playerComponent.selectedGun();
		if (gun != null) {
			spreadDegrees = inaccuracy(gun.def);
			float scale = zoomed ? ZOOM_SPREAD : 1;
			moveSpreadDegrees = recoil.movementInaccuracy(gun.def, speedFraction(), !playerComponent.onGround) * scale;
			fireSpreadDegrees = recoil.firingInaccuracy(gun.def) * scale;
		}
		// The spray pattern: the view climbs part of it (PlayerSystem.VIEW_RECOIL), the bullets all of it.
		playerComponent.recoilUp = recoil.up();
		playerComponent.recoilRight = recoil.right();
		gameWorld.playerSystem().updateCameraDirection();
		animateViewModel(deltaTime);
	}

	/** Radius of the random inaccuracy cone right now, in degrees. */
	public float spreadDegrees() {
		return spreadDegrees;
	}

	/** The part of {@link #spreadDegrees} from moving and jumping, for the dynamic crosshair. */
	public float moveSpreadDegrees() {
		return moveSpreadDegrees;
	}

	/** The part of {@link #spreadDegrees} from spraying, for the dynamic crosshair. */
	public float fireSpreadDegrees() {
		return fireSpreadDegrees;
	}

	private float speedFraction() {
		return playerComponent.velocity.len() / PlayerMovement.RUN_SPEED;
	}

	private float inaccuracy(GunDefinition def) {
		float spread = recoil.inaccuracy(def, speedFraction(), !playerComponent.onGround);
		return zoomed ? spread * ZOOM_SPREAD : spread;
	}

	/** The first-person gun bobs with your steps and kicks back on each shot. */
	private void animateViewModel(float delta) {
		Gun gun = playerComponent.selectedGun();
		if (gun == null)
			return;
		float speedFraction = Math.min(1f, playerComponent.velocity.len() / PlayerMovement.RUN_SPEED);
		float targetBob = playerComponent.onGround ? speedFraction : 0;
		bobAmount += (targetBob - bobAmount) * Math.min(1f, delta * 8f);
		bobPhase = (bobPhase + delta * STEP_RATE * MathUtils.PI2 * Math.max(speedFraction, 0.3f)) % MathUtils.PI2;
		gunKick *= (float) Math.exp(-GUN_KICK_RECOVERY * delta);

		float[] offset = gun.def.viewOffset;
		float x = offset[0] + MathUtils.sin(bobPhase) * 0.09f * bobAmount;
		float y = offset[1] - Math.abs(MathUtils.cos(bobPhase)) * 0.07f * bobAmount + gunKick * 0.06f;
		float z = offset[2] + gunKick * 0.45f; // towards the eye
		gunTilt.setEulerAngles(0, gunKick * 5f, 0); // muzzle up
		ModelComponent model = viewModels.get(gun.def.id).getComponent(ModelComponent.class);
		model.instance.transform.set(x, y, z, gunTilt.x, gunTilt.y, gunTilt.z, gunTilt.w);
	}

	private void handleInput() {
		Gun gun = playerComponent.selectedGun();
		if (gun == null)
			return;
		if (Controls.isJustPressed(Action.RELOAD))
			reload(gun);
		if (Controls.isJustPressed(Action.SWITCH_GUN)) {
			playerComponent.switchGun();
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastShotTime >= gun.def.fireDelayMs && now - lastReloadTime >= RELOAD_DELAY_MS) {
			boolean trigger = gun.def.automatic ? Controls.isPressed(Action.FIRE)
					: Controls.isJustPressed(Action.FIRE);
			if (trigger)
				fire(gun);
		}
		if (Controls.isJustPressed(Action.AIM) && gun.def.hasSight)
			toggleZoom();
	}

	private void syncViewModel() {
		Gun gun = playerComponent.selectedGun();
		if (gun != heldGun) {
			heldGun = gun;
			recoil.reset();
			gunKick = 0;
			if (zoomed)
				toggleZoom(); // a different gun never starts aimed
		}
		gameWorld.renderSystem.gun = gun == null ? null : viewModels.get(gun.def.id);
	}

	/** Back to the normal view, if aiming down sights. */
	public void unzoom() {
		if (zoomed)
			toggleZoom();
	}

	private void toggleZoom() {
		zoomed = !zoomed;
		camera.fieldOfView = zoomed ? ZOOM_FOV : DEFAULT_FOV;
	}

	/**
	 * Where a bullet goes: where the crosshair is, which is where the player
	 * aims plus the spray pattern's offset for this bullet of the burst and the
	 * view's jolt from the last shots and hits, plus a random deviation inside
	 * the inaccuracy cone.
	 */
	private Vector3 aimShot(GunDefinition def, float coneDegrees, Vector3 out) {
		float radius = coneDegrees * (float) Math.sqrt(random.nextFloat()); // uniform over the disc
		float angle = random.nextFloat() * MathUtils.PI2;
		float up = recoil.up() + playerComponent.punchUp + radius * MathUtils.sin(angle);
		float right = recoil.right() + playerComponent.punchRight + radius * MathUtils.cos(angle);
		aim.setEulerAngles(playerComponent.yaw - right, playerComponent.pitch - up, 0);
		return aim.transform(out.set(0, 0, 1));
	}

	private void fire(Gun gun) {
		long now = System.currentTimeMillis();
		lastShotTime = now;
		if (!gun.consumeRound()) {
			reload(gun);
			return;
		}
		shotSounds.get(gun.def.id).play(Config.fxVolume / 100);

		float cone = inaccuracy(gun.def); // before the shot: the first bullet only pays for movement
		recoil.shoot(gun.def, now);
		aimShot(gun.def, cone, shotDirection);
		// A quick jolt of the view, mostly up and a little to a random side, and the gun kicks back.
		float punch = gun.def.punch * (0.85f + random.nextFloat() * 0.3f);
		playerComponent.punchUp += punch;
		playerComponent.punchRight += punch * (random.nextFloat() - 0.5f) * 0.4f;
		gunKick = Math.min(gunKick + 1f, 1.6f);

		Ray ray = shotRay.set(camera.position, shotDirection);
		rayFrom.set(ray.origin);
		rayTo.set(ray.direction).scl(gun.def.range).add(rayFrom);
		// The callback is reused, so its previous result has to be cleared.
		rayTestCB.setCollisionObject(null);
		rayTestCB.setClosestHitFraction(1f);
		rayTestCB.setRayFromWorld(rayFrom);
		rayTestCB.setRayToWorld(rayTo);
		gameWorld.bulletSystem.collisionWorld.rayTest(rayFrom, rayTo, rayTestCB);
		Entity target = rayTestCB.hasHit() && rayTestCB.getCollisionObject().userData instanceof Entity
				? (Entity) rayTestCB.getCollisionObject().userData
				: null;

		if (target != null && target.getComponent(CharacterComponent.class) == null)
			leaveImpactMark(ray);
		if (rayTestCB.hasHit())
			rayTestCB.getHitPointWorld(hitPoint);
		gameWorld.renderSystem.tracers.add(muzzlePosition(muzzle), rayTestCB.hasHit() ? hitPoint : rayTo);
		// The server re-traces the shot and decides the damage; the local ray above
		// is only for the impact mark and the tracer.
		gameWorld.network.sendFire(gun, rayFrom, ray.direction);
	}

	/** The local ray hit level geometry: leave a bullet hole there. */
	private void leaveImpactMark(Ray ray) {
		rayTestCB.getHitPointWorld(hitPoint);
		// Other players aren't in the local physics world: don't mark the wall behind
		// a player the shot actually hit.
		if (gameWorld.network.isRemotePlayerCloserThan(ray, rayFrom.dst(hitPoint)))
			return;
		rayTestCB.getHitNormalWorld(hitNormal);
		gameWorld.renderSystem.impactMarks.add(hitPoint, hitNormal);
	}

	/**
	 * Roughly where the first-person gun's barrel ends, in world space, so our
	 * own tracers seem to leave the gun. The view model is drawn with its own
	 * camera, but with the same field of view, so a point at the same offset
	 * from the eye lands on the same spot of the screen.
	 */
	private Vector3 muzzlePosition(Vector3 out) {
		tmp.set(camera.direction).crs(camera.up).nor(); // right
		return out.set(camera.position).mulAdd(camera.direction, 3.2f).mulAdd(tmp, 0.9f).mulAdd(camera.up, -0.75f);
	}

	/**
	 * Another player's shot (online): its sound, panned and attenuated by
	 * where it came from, and its tracer.
	 *
	 * @param shooter where the shooter is drawn (for the sound)
	 * @param origin  where the server says the bullet started (the shooter's eye)
	 */
	public void playRemoteShot(String gunId, Vector3 shooter, Vector3 origin, Vector3 end) {
		if (playerComponent == null)
			return;
		// Start the tracer a little in front of the eye, at about gun height.
		tmp.set(end).sub(origin).nor();
		muzzle.set(origin).mulAdd(tmp, 2f).add(0, -0.7f, 0);
		gameWorld.renderSystem.tracers.add(muzzle, end);
		Sound sound = shotSounds.get(gunId);
		float volume = Config.fxVolume / 100 * SpatialAudio.volume(camera.position, shooter);
		if (sound == null || volume < 0.01f)
			return;
		sound.play(volume, 1f, SpatialAudio.pan(camera.position, camera.direction, camera.up, shooter));
	}

	/**
	 * Someone was hit (the server says so): blood flies from the wound and stains
	 * the wall behind, and a thud sounds from the body, heard only nearby.
	 *
	 * @param onMe the local player was hit: no blood in our own eyes, the thud right here
	 */
	public void showHit(Vector3 at, Vector3 direction, boolean headshot, boolean onMe) {
		if (playerComponent == null)
			return;
		Array<Sound> sounds = headshot ? headHitSounds : bodyHitSounds;
		Sound sound = sounds.get(random.nextInt(sounds.size));
		if (onMe) {
			sound.play(Config.fxVolume / 100 * 0.8f);
			return;
		}
		gameWorld.renderSystem.blood.spray(at, direction, headshot);
		splatter(at, direction);
		tmp.set(at).sub(camera.position).scl(HIT_SOUND_RANGE_SCALE).add(camera.position);
		float volume = Config.fxVolume / 100 * SpatialAudio.volume(camera.position, tmp);
		if (volume > 0.01f)
			sound.play(Math.min(1, volume * 1.2f), MathUtils.random(0.93f, 1.07f),
					SpatialAudio.pan(camera.position, camera.direction, camera.up, at));
	}

	/** Blood on the wall or floor behind the wound, if there is one close enough. */
	private void splatter(Vector3 at, Vector3 direction) {
		rayFrom.set(direction).scl(0.5f).add(at);
		rayTo.set(direction).scl(SPLATTER_DISTANCE).add(at).add(0, -1.5f, 0); // drops fall a little
		levelRay.setCollisionObject(null);
		levelRay.setClosestHitFraction(1f);
		levelRay.setRayFromWorld(rayFrom);
		levelRay.setRayToWorld(rayTo);
		gameWorld.bulletSystem.collisionWorld.rayTest(rayFrom, rayTo, levelRay);
		if (!levelRay.hasHit())
			return;
		levelRay.getHitPointWorld(hitPoint);
		levelRay.getHitNormalWorld(hitNormal);
		gameWorld.renderSystem.impactMarks.addBlood(hitPoint, hitNormal);
	}

	private void reload(Gun gun) {
		if (!gun.reload())
			return;
		lastReloadTime = System.currentTimeMillis();
		reloadSound.play(Config.fxVolume / 100);
	}

	@Override
	public void dispose() {
		rayTestCB.dispose();
		levelRay.dispose();
		for (String path : acquired)
			Assets.release(path);
		acquired.clear();
	}
}
