package com.juego.systems;

import java.util.Random;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.utils.Disposable;
import com.juego.UI.HUD;
import com.juego.UI.PauseMenu;
import com.juego.components.CharacterComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.StatusComponent;
import com.juego.core.GameWorld;
import com.juego.guns.Gun;
import com.juego.utils.Config;

public class PlayerSystem extends EntitySystem implements EntityListener, Disposable {

	private static final float RUN_SPEED = 10f;
	private static final float WALK_SPEED = 5f;
	private static final Vector3 JUMP_VELOCITY = new Vector3(0, 15, 0);
	private static final long RELOAD_DELAY_MS = 2000;
	private static final float DEFAULT_FOV = 67f;
	private static final float ZOOM_FOV = 30f;
	/**
	 * Character capsules are 6 units tall and centered on their origin, so the
	 * top ~1.2 units (the "head") start 1.8 units above the center.
	 */
	private static final float HEAD_OFFSET = 1.8f;
	/** Spread values below are tuned in pixels for a 1280 px wide window. */
	private static final float SPREAD_REFERENCE_WIDTH = 1280f;

	private Entity player;
	private PlayerComponent playerComponent;
	private CharacterComponent characterComponent;
	private ModelComponent modelComponent;
	private final PerspectiveCamera camera;
	private final GameWorld gameWorld;
	private HUD hud;
	public PauseMenu pauseMenu;

	public Entity gun;
	private Entity primaryGunEntity, secondaryGunEntity;
	private final Sound reloadSound = Gdx.audio.newSound(Gdx.files.internal("sounds/Reload.mp3"));
	private long lastShotTime;
	private long lastReloadTime;
	private boolean zoomed = false;

	private float yaw, pitch, recoil;
	private float spread;
	private boolean walking, running;
	private final Random random = new Random();

	// Reused math objects (avoid per-frame garbage).
	private final ClosestRayResultCallback rayTestCB = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
	private final Vector3 rayFrom = new Vector3();
	private final Vector3 rayTo = new Vector3();
	private final Vector3 hitPoint = new Vector3();
	private final Vector3 targetCenter = new Vector3();
	private final Vector3 forward = new Vector3();
	private final Vector3 right = new Vector3();
	private final Vector3 translation = new Vector3();
	private final Vector3 spawnPoint = new Vector3();
	private final Quaternion cameraRotation = new Quaternion();
	private final Quaternion bodyRotation = new Quaternion();
	private final Matrix4 ghost = new Matrix4();
	private final Matrix4 targetTransform = new Matrix4();

	public PlayerSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.camera = camera;
		this.gameWorld = gameWorld;
	}

	@Override
	public void addedToEngine(Engine engine) {
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
	}

	@Override
	public void entityAdded(Entity entity) {
		if (entity.getComponent(PlayerComponent.class).numJugador != gameWorld.jugador)
			return;
		player = entity;
		playerComponent = entity.getComponent(PlayerComponent.class);
		characterComponent = entity.getComponent(CharacterComponent.class);
		modelComponent = entity.getComponent(ModelComponent.class);
		modelComponent.instance.transform.getTranslation(spawnPoint);
		hud = new HUD(playerComponent.getHealth(), playerComponent.primaryAmmo);
		pauseMenu = new PauseMenu(playerComponent, gameWorld);
	}

	public void setGuns(Entity primary, Entity secondary) {
		primaryGunEntity = primary;
		secondaryGunEntity = secondary;
		gun = primary;
		gameWorld.renderSystem.gun = gun;
	}

	public boolean isPaused() {
		return playerComponent != null && playerComponent.pause;
	}

	@Override
	public void update(float delta) {
		if (player == null)
			return;
		if (Gdx.input.isKeyJustPressed(Config.pauseKey))
			playerComponent.pause = !playerComponent.pause;
		Gdx.input.setCursorCatched(!playerComponent.pause);
		if (playerComponent.isDead())
			respawn();
		updateMovement();
		if (playerComponent.selectedGun == playerComponent.primaryGun) {
			hud.update(playerComponent.getHealth(), playerComponent.primaryAmmo, playerComponent.selectedGun);
		} else {
			hud.update(playerComponent.getHealth(), playerComponent.selectedGun);
		}
		pauseMenu.update(delta);
	}

	private void updateMovement() {
		characterComponent.walkDirection.setZero();
		walking = false;
		if (!playerComponent.pause) {
			updateCameraRotation();

			// Move on the horizontal plane only: looking up or down must not change the speed.
			forward.set(camera.direction.x, 0, camera.direction.z).nor();
			right.set(forward).crs(Vector3.Y);
			Vector3 walkDirection = characterComponent.walkDirection;
			if (Gdx.input.isKeyPressed(Config.forwardKey))
				walkDirection.add(forward);
			if (Gdx.input.isKeyPressed(Config.backwardKey))
				walkDirection.sub(forward);
			if (Gdx.input.isKeyPressed(Config.leftKey))
				walkDirection.sub(right);
			if (Gdx.input.isKeyPressed(Config.rightKey))
				walkDirection.add(right);
			walking = !walkDirection.isZero();
			running = !Gdx.input.isKeyPressed(Config.walkKey);

			// Normalized so diagonals aren't faster. The character controller expects a
			// displacement per physics step, not per rendered frame.
			float speed = running ? RUN_SPEED : WALK_SPEED;
			walkDirection.nor().scl(speed * gameWorld.bulletSystem.fixedTimeStep);

			if (Gdx.input.isKeyJustPressed(Config.jumpKey) && characterComponent.characterController.onGround()) {
				characterComponent.characterController.setJumpSpeed(1f);
				characterComponent.characterController.jump(JUMP_VELOCITY);
			}
			updatePlayerActions();
		}
		characterComponent.characterController.setWalkDirection(characterComponent.walkDirection);

		characterComponent.ghostObject.getWorldTransform(ghost);
		ghost.getTranslation(translation);
		bodyRotation.setFromAxis(Vector3.Y, yaw);
		modelComponent.instance.transform.set(translation, bodyRotation);
		camera.position.set(translation);
		camera.update(true);
	}

	private void updatePlayerActions() {
		calculateSpread();
		if (Gdx.input.isKeyJustPressed(Config.reloadKey))
			reloadGun();
		if (Gdx.input.isKeyJustPressed(Config.changeGunKey)) {
			if (zoomed)
				toggleZoom();
			if (playerComponent.selectedGun == playerComponent.primaryGun) {
				selectGun(playerComponent.secondaryGun, secondaryGunEntity);
			} else {
				selectGun(playerComponent.primaryGun, primaryGunEntity);
			}
		}
		playerComponent.scoreboard = Gdx.input.isKeyPressed(Config.scoreboardsKey);

		long now = System.currentTimeMillis();
		if (now - lastShotTime >= playerComponent.selectedGun.delay && now - lastReloadTime >= RELOAD_DELAY_MS) {
			boolean trigger = playerComponent.selectedGun.auto ? Gdx.input.isButtonPressed(Config.fireButton)
					: Gdx.input.isButtonJustPressed(Config.fireButton);
			if (trigger)
				fire();
		}
		if (Gdx.input.isButtonJustPressed(Config.zoomButton) && playerComponent.selectedGun.sight)
			toggleZoom();

		if (Gdx.input.isKeyJustPressed(Input.Keys.B))
			teleportToSpawn(); // debug: get unstuck
	}

	/** Bullet spread in pixels: none when standing still, more when running or airborne. */
	private void calculateSpread() {
		boolean onGround = characterComponent.characterController.onGround();
		if (!onGround || (walking && running)) {
			spread = random.nextInt(51) + 50;
		} else if (walking) {
			spread = random.nextInt(26) + 25;
		} else {
			spread = 0;
		}
		if (random.nextBoolean())
			spread = -spread;
		spread *= Gdx.graphics.getWidth() / SPREAD_REFERENCE_WIDTH;
	}

	private void updateCameraRotation() {
		yaw += -Gdx.input.getDeltaX() * Config.sensivity * 0.01f;
		pitch -= -Gdx.input.getDeltaY() * Config.sensivity * 0.01f + recoil;
		if (pitch > 90)
			pitch = 90;
		if (pitch < -90)
			pitch = -90;
		recoil = 0;
		cameraRotation.setEulerAngles(yaw, pitch, 0);
		camera.direction.set(0, 0, 1);
		camera.up.set(0, 1, 0);
		camera.rotate(cameraRotation);
	}

	private void toggleZoom() {
		zoomed = !zoomed;
		camera.fieldOfView = zoomed ? ZOOM_FOV : DEFAULT_FOV;
	}

	private void selectGun(Gun selected, Entity entity) {
		playerComponent.selectedGun = selected;
		gun = entity;
		gameWorld.renderSystem.gun = entity;
	}

	private void fire() {
		lastShotTime = System.currentTimeMillis();
		if (playerComponent.selectedGun.magazine <= 0) {
			reloadGun();
			return;
		}
		playerComponent.selectedGun.magazine--;
		playerComponent.selectedGun.sound.play(Config.fxVolume / 100);
		recoil = playerComponent.selectedGun.recoil;

		Ray ray = camera.getPickRay(Gdx.graphics.getWidth() / 2f + spread, Gdx.graphics.getHeight() / 2f);
		rayFrom.set(ray.origin);
		rayTo.set(ray.direction).scl(playerComponent.selectedGun.range).add(rayFrom);
		// The callback is reused, so its previous result has to be cleared.
		rayTestCB.setCollisionObject(null);
		rayTestCB.setClosestHitFraction(1f);
		rayTestCB.setRayFromWorld(rayFrom);
		rayTestCB.setRayToWorld(rayTo);
		gameWorld.bulletSystem.collisionWorld.rayTest(rayFrom, rayTo, rayTestCB);
		if (rayTestCB.hasHit() && rayTestCB.getCollisionObject().userData instanceof Entity)
			applyHit((Entity) rayTestCB.getCollisionObject().userData);
	}

	private void applyHit(Entity target) {
		CharacterComponent targetCharacter = target.getComponent(CharacterComponent.class);
		if (targetCharacter == null || targetCharacter.characterController == null)
			return; // map geometry, props, or an enemy that is already being removed
		rayTestCB.getHitPointWorld(hitPoint);
		targetCharacter.ghostObject.getWorldTransform(targetTransform);
		targetTransform.getTranslation(targetCenter);
		boolean headshot = hitPoint.y >= targetCenter.y + HEAD_OFFSET;
		int damage = playerComponent.selectedGun.damage;

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

	private void reloadGun() {
		Gun selected = playerComponent.selectedGun;
		boolean isPrimary = selected == playerComponent.primaryGun;
		if (selected.magazine >= selected.magazineSize || (isPrimary && playerComponent.primaryAmmo <= 0))
			return;
		lastReloadTime = System.currentTimeMillis();
		int neededAmmo = selected.magazineSize - selected.magazine;
		if (isPrimary) {
			int loaded = Math.min(neededAmmo, playerComponent.primaryAmmo);
			playerComponent.primaryAmmo -= loaded;
			selected.magazine += loaded;
		} else {
			selected.magazine += neededAmmo; // the secondary gun has infinite reserve ammo
		}
		reloadSound.play(Config.fxVolume / 100);
	}

	private void respawn() {
		playerComponent.respawn();
		teleportToSpawn();
	}

	private void teleportToSpawn() {
		characterComponent.characterController.warp(spawnPoint);
	}

	@Override
	public void entityRemoved(Entity entity) {
	}

	@Override
	public void dispose() {
		reloadSound.dispose();
		rayTestCB.dispose();
		if (hud != null)
			hud.dispose();
		if (pauseMenu != null)
			pauseMenu.dispose();
	}
}
