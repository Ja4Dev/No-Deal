package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.juego.components.CharacterComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.utils.Config;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;

/** Local player movement: mouse look, WASD walking, jumping and respawning. */
public class PlayerSystem extends EntitySystem implements EntityListener {

	private static final float RUN_SPEED = 10f;
	private static final float WALK_SPEED = 5f;
	private static final Vector3 JUMP_VELOCITY = new Vector3(0, 15, 0);
	private static final float MAX_PITCH = 90f;
	private static final float MOUSE_SCALE = 0.01f;

	private final PerspectiveCamera camera;
	private final GameWorld gameWorld;
	private Entity player;
	private PlayerComponent playerComponent;
	private CharacterComponent characterComponent;
	private ModelComponent modelComponent;

	// Reused math objects (avoid per-frame garbage).
	private final Vector3 forward = new Vector3();
	private final Vector3 right = new Vector3();
	private final Vector3 translation = new Vector3();
	private final Vector3 spawnPoint = new Vector3();
	private final Quaternion cameraRotation = new Quaternion();
	private final Quaternion bodyRotation = new Quaternion();
	private final Matrix4 ghost = new Matrix4();

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
		if (!gameWorld.isLocalPlayer(entity))
			return;
		player = entity;
		playerComponent = entity.getComponent(PlayerComponent.class);
		characterComponent = entity.getComponent(CharacterComponent.class);
		modelComponent = entity.getComponent(ModelComponent.class);
		modelComponent.instance.transform.getTranslation(spawnPoint);
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
	}

	@Override
	public void update(float delta) {
		if (player == null)
			return;
		// Offline we respawn right away; online the server decides when and where.
		if (playerComponent.isDead() && !gameWorld.isMultiplayer())
			respawn();

		characterComponent.walkDirection.setZero();
		playerComponent.moving = false;
		if (!playerComponent.pause && !playerComponent.isDead()) {
			updateCameraRotation();
			updateWalkDirection();
			if (Controls.isJustPressed(Action.JUMP) && characterComponent.characterController.onGround()) {
				characterComponent.characterController.setJumpSpeed(1f);
				characterComponent.characterController.jump(JUMP_VELOCITY);
			}
			if (Gdx.input.isKeyJustPressed(Input.Keys.B))
				teleportToSpawn(); // debug: get unstuck
		}
		characterComponent.characterController.setWalkDirection(characterComponent.walkDirection);
		syncModelAndCamera();
	}

	private void updateCameraRotation() {
		PlayerComponent p = playerComponent;
		p.yaw += -Gdx.input.getDeltaX() * Config.sensitivity * MOUSE_SCALE;
		p.pitch += Gdx.input.getDeltaY() * Config.sensitivity * MOUSE_SCALE - p.pendingRecoil;
		p.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, p.pitch));
		p.pendingRecoil = 0;
		cameraRotation.setEulerAngles(p.yaw, p.pitch, 0);
		camera.direction.set(0, 0, 1);
		camera.up.set(0, 1, 0);
		camera.rotate(cameraRotation);
	}

	private void updateWalkDirection() {
		// Move on the horizontal plane only: looking up or down must not change the speed.
		forward.set(camera.direction.x, 0, camera.direction.z).nor();
		right.set(forward).crs(Vector3.Y);
		Vector3 walkDirection = characterComponent.walkDirection;
		if (Controls.isPressed(Action.FORWARD))
			walkDirection.add(forward);
		if (Controls.isPressed(Action.BACKWARD))
			walkDirection.sub(forward);
		if (Controls.isPressed(Action.LEFT))
			walkDirection.sub(right);
		if (Controls.isPressed(Action.RIGHT))
			walkDirection.add(right);
		playerComponent.moving = !walkDirection.isZero();
		playerComponent.running = !Controls.isPressed(Action.WALK);

		// Normalized so diagonals aren't faster. The character controller expects a
		// displacement per physics step, not per rendered frame.
		float speed = playerComponent.running ? RUN_SPEED : WALK_SPEED;
		walkDirection.nor().scl(speed * gameWorld.bulletSystem.fixedTimeStep);
	}

	private void syncModelAndCamera() {
		characterComponent.ghostObject.getWorldTransform(ghost);
		ghost.getTranslation(translation);
		bodyRotation.setFromAxis(Vector3.Y, playerComponent.yaw);
		modelComponent.instance.transform.set(translation, bodyRotation);
		camera.position.set(translation);
		camera.update(true);
	}

	private void respawn() {
		playerComponent.respawn();
		teleportToSpawn();
	}

	/** Back to where the player first spawned (a new offline round). */
	public void respawnAtSpawn() {
		teleportToSpawn();
	}

	private void teleportToSpawn() {
		teleport(spawnPoint);
	}

	/** Moves the player instantly (spawns, server corrections). */
	public void teleport(Vector3 position) {
		characterComponent.characterController.warp(position);
		characterComponent.walkDirection.setZero();
		syncModelAndCamera();
	}

	/** Center of the player's capsule (where the camera is). */
	public Vector3 getPosition(Vector3 out) {
		characterComponent.ghostObject.getWorldTransform(ghost);
		return ghost.getTranslation(out);
	}
}
