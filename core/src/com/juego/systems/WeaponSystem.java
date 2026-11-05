package com.juego.systems;

import java.util.Random;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.juego.components.CharacterComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.StatusComponent;
import com.juego.core.Assets;
import com.juego.core.GameWorld;
import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;
import com.juego.utils.CharacterShape;
import com.juego.utils.Config;
import com.juego.utils.SpatialAudio;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;

/** The local player's guns: hitscan shooting, reloading, switching and aiming down sights. */
public class WeaponSystem extends EntitySystem implements EntityListener, Disposable {

	private static final String RELOAD_SOUND = "sounds/Reload.mp3";
	private static final long RELOAD_DELAY_MS = 2000;
	private static final float DEFAULT_FOV = 67f;
	private static final float ZOOM_FOV = 30f;
	/** Spread values are tuned in pixels for a 1280 px wide window and scaled from there. */
	private static final float SPREAD_REFERENCE_WIDTH = 1280f;

	private final GameWorld gameWorld;
	private final PerspectiveCamera camera;
	private Entity player;
	private PlayerComponent playerComponent;
	private CharacterComponent characterComponent;

	/** First-person model and shot sound of every gun in the game, by gun id: any gun can be picked up. */
	private final ObjectMap<String, Entity> viewModels = new ObjectMap<>();
	private final ObjectMap<String, Sound> shotSounds = new ObjectMap<>();
	/** The gun in hand last frame, to notice when it changes (switch, drop, pickup, respawn). */
	private Gun heldGun;
	private final Array<String> acquired = new Array<>();
	private Sound reloadSound;
	private long lastShotTime;
	private long lastReloadTime;
	private boolean zoomed;
	private float spread;
	private final Random random = new Random();

	// Reused math objects (avoid per-frame garbage).
	private final ClosestRayResultCallback rayTestCB = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
	private final Vector3 rayFrom = new Vector3();
	private final Vector3 rayTo = new Vector3();
	private final Vector3 hitPoint = new Vector3();
	private final Vector3 hitNormal = new Vector3();
	private final Vector3 targetCenter = new Vector3();
	private final Matrix4 targetTransform = new Matrix4();
	private final Vector3 muzzle = new Vector3();
	private final Vector3 tmp = new Vector3();

	public WeaponSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.gameWorld = gameWorld;
		this.camera = camera;
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
		characterComponent = entity.getComponent(CharacterComponent.class);
		reloadSound = acquire(RELOAD_SOUND);
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
		if (!playerComponent.pause && !playerComponent.isDead() && gameWorld.canFight())
			handleInput();
		syncViewModel(); // the selected gun can also change on respawn
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
			if (zoomed)
				toggleZoom(); // a different gun never starts aimed
		}
		gameWorld.renderSystem.gun = gun == null ? null : viewModels.get(gun.def.id);
	}

	private void toggleZoom() {
		zoomed = !zoomed;
		camera.fieldOfView = zoomed ? ZOOM_FOV : DEFAULT_FOV;
	}

	/** Bullet spread in pixels: none when standing still, more when running or airborne. */
	private float calculateSpread() {
		boolean onGround = characterComponent.characterController.onGround();
		float pixels;
		if (!onGround || (playerComponent.moving && playerComponent.running))
			pixels = random.nextInt(51) + 50;
		else if (playerComponent.moving)
			pixels = random.nextInt(26) + 25;
		else
			pixels = 0;
		if (random.nextBoolean())
			pixels = -pixels;
		return pixels * Gdx.graphics.getWidth() / SPREAD_REFERENCE_WIDTH;
	}

	private void fire(Gun gun) {
		lastShotTime = System.currentTimeMillis();
		if (!gun.consumeRound()) {
			reload(gun);
			return;
		}
		shotSounds.get(gun.def.id).play(Config.fxVolume / 100);
		playerComponent.pendingRecoil += gun.def.recoil;

		spread = calculateSpread();
		Ray ray = camera.getPickRay(Gdx.graphics.getWidth() / 2f + spread, Gdx.graphics.getHeight() / 2f);
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
		if (gameWorld.network != null) {
			// Online the server re-traces the shot and decides the damage; the local
			// ray above is only for the impact mark.
			gameWorld.network.sendFire(gun, rayFrom, ray.direction);
		} else if (target != null) {
			applyHit(target, gun.def.damage);
		}
	}

	/** The local ray hit level geometry: leave a bullet hole there. */
	private void leaveImpactMark(Ray ray) {
		rayTestCB.getHitPointWorld(hitPoint);
		// Online, other players aren't in the local physics world: don't mark the wall
		// behind a player the shot actually hit.
		if (gameWorld.network != null && gameWorld.network.isRemotePlayerCloserThan(ray, rayFrom.dst(hitPoint)))
			return;
		rayTestCB.getHitNormalWorld(hitNormal);
		gameWorld.renderSystem.impactMarks.add(hitPoint, hitNormal);
	}

	private void applyHit(Entity target, int damage) {
		CharacterComponent targetCharacter = target.getComponent(CharacterComponent.class);
		if (targetCharacter == null || targetCharacter.characterController == null)
			return; // map geometry, props, or an enemy that is already being removed
		rayTestCB.getHitPointWorld(hitPoint);
		targetCharacter.ghostObject.getWorldTransform(targetTransform);
		targetTransform.getTranslation(targetCenter);
		boolean headshot = hitPoint.y >= targetCenter.y + CharacterShape.HEAD_OFFSET;

		StatusComponent status = target.getComponent(StatusComponent.class);
		if (status != null) {
			boolean killed = headshot ? status.getDamageHead(damage) : status.getDamage(damage);
			if (killed)
				playerComponent.kills++;
		}
		PlayerComponent otherPlayer = target.getComponent(PlayerComponent.class);
		if (otherPlayer != null && otherPlayer != playerComponent) {
			if (headshot)
				otherPlayer.getDamageHead(damage);
			else
				otherPlayer.getDamage(damage);
		}
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

	private void reload(Gun gun) {
		if (!gun.reload())
			return;
		lastReloadTime = System.currentTimeMillis();
		reloadSound.play(Config.fxVolume / 100);
	}

	@Override
	public void dispose() {
		rayTestCB.dispose();
		for (String path : acquired)
			Assets.release(path);
		acquired.clear();
	}
}
