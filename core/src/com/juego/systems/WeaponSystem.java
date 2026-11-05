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
import com.juego.utils.CharacterShape;
import com.juego.utils.Config;

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

	private final ObjectMap<Gun, Entity> viewModels = new ObjectMap<>();
	private final ObjectMap<Gun, Sound> shotSounds = new ObjectMap<>();
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
	private final Vector3 targetCenter = new Vector3();
	private final Matrix4 targetTransform = new Matrix4();

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
		for (Gun gun : new Gun[] { playerComponent.primaryGun, playerComponent.secondaryGun }) {
			viewModels.put(gun, gameWorld.entityFactory.createGunView(gun));
			shotSounds.put(gun, acquire(gun.def.sound));
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
		if (!playerComponent.pause && !playerComponent.isDead())
			handleInput();
		syncViewModel(); // the selected gun can also change on respawn
	}

	private void handleInput() {
		Gun gun = playerComponent.selectedGun;
		if (Gdx.input.isKeyJustPressed(Config.reloadKey))
			reload(gun);
		if (Gdx.input.isKeyJustPressed(Config.changeGunKey)) {
			if (zoomed)
				toggleZoom();
			playerComponent.selectedGun = gun == playerComponent.primaryGun ? playerComponent.secondaryGun
					: playerComponent.primaryGun;
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastShotTime >= gun.def.fireDelayMs && now - lastReloadTime >= RELOAD_DELAY_MS) {
			boolean trigger = gun.def.automatic ? Gdx.input.isButtonPressed(Config.fireButton)
					: Gdx.input.isButtonJustPressed(Config.fireButton);
			if (trigger)
				fire(gun);
		}
		if (Gdx.input.isButtonJustPressed(Config.zoomButton) && gun.def.hasSight)
			toggleZoom();
	}

	private void syncViewModel() {
		gameWorld.renderSystem.gun = viewModels.get(playerComponent.selectedGun);
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
		shotSounds.get(gun).play(Config.fxVolume / 100);
		playerComponent.pendingRecoil += gun.def.recoil;

		spread = calculateSpread();
		Ray ray = camera.getPickRay(Gdx.graphics.getWidth() / 2f + spread, Gdx.graphics.getHeight() / 2f);
		rayFrom.set(ray.origin);
		if (gameWorld.network != null) {
			// Online the server re-traces the shot and decides the damage.
			gameWorld.network.sendFire(gun, rayFrom, ray.direction);
			return;
		}
		rayTo.set(ray.direction).scl(gun.def.range).add(rayFrom);
		// The callback is reused, so its previous result has to be cleared.
		rayTestCB.setCollisionObject(null);
		rayTestCB.setClosestHitFraction(1f);
		rayTestCB.setRayFromWorld(rayFrom);
		rayTestCB.setRayToWorld(rayTo);
		gameWorld.bulletSystem.collisionWorld.rayTest(rayFrom, rayTo, rayTestCB);
		if (rayTestCB.hasHit() && rayTestCB.getCollisionObject().userData instanceof Entity)
			applyHit((Entity) rayTestCB.getCollisionObject().userData, gun.def.damage);
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
