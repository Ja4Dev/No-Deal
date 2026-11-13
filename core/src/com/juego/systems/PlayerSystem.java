package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.juego.UI.TouchControls;
import com.juego.components.CharacterComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.utils.CharacterShape;
import com.juego.utils.Config;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;
import com.juego.utils.PlayerMovement;
import com.juego.utils.MovementSettings;

/**
 * Local player movement: mouse look, walking with acceleration and friction,
 * jumping and respawning.
 * <p>
 * Input is read every frame, but movement runs in the physics steps (see
 * {@link BulletSystem}), so it doesn't depend on the frame rate. The camera
 * is placed between the last two physics positions, so it moves smoothly at
 * any refresh rate, and sits at eye height.
 */
public class PlayerSystem extends EntitySystem implements EntityListener, BulletSystem.StepListener {

	/** Just short of straight up/down: at exactly 90 degrees the view would flip. */
	private static final float MAX_PITCH = 89f;
	/** Degrees per mouse count at sensitivity 1, the same as CS's m_yaw/m_pitch, so CS players can use their value. */
	private static final float MOUSE_SCALE = 0.022f;
	/** How fast a punch (shot or hit) springs back, per second. */
	private static final float PUNCH_RECOVERY = 14f;
	/**
	 * How much of the spray pattern the view follows, as in CS: the bullets go
	 * the whole way, so the rest is pulled down by the player (the crosshair
	 * can show it, see CrosshairSettings.followRecoil).
	 */
	public static final float VIEW_RECOIL = 0.5f;
	/** How fast the view goes down when crouching (and back up), per second. */
	private static final float CROUCH_RATE = 12f;
	/** Degrees the view turns for a drag across the whole screen height, at touch sensitivity 1. */
	private static final float TOUCH_DEGREES = 160f;

	private final PerspectiveCamera camera;
	/** The field of view when not zoomed in, to slow the touch look down while aiming. */
	private final float normalFieldOfView;
	private final GameWorld gameWorld;
	private Entity player;
	private PlayerComponent playerComponent;
	private CharacterComponent characterComponent;
	private ModelComponent modelComponent;

	/** What the keys ask for this frame, applied in the next physics step(s). */
	private final Vector3 wishDirection = new Vector3();
	private float wishSpeed;
	private boolean jumpRequested;
	/** The gravity last given to the character controller, to set it again only when sv_gravity changes. */
	private float appliedGravity = Float.NaN;

	/** Positions at the start and end of the last physics step, to interpolate the camera between them. */
	private final Vector3 stepFrom = new Vector3(), stepTo = new Vector3();
	/** Where the step before last started and how far it was told to move, to notice walls. */
	private final Vector3 previousStepFrom = new Vector3(), commanded = new Vector3();
	private boolean hasStep;

	// Reused math objects (avoid per-frame garbage).
	private final Vector3 forward = new Vector3();
	private final Vector3 right = new Vector3();
	private final Vector3 translation = new Vector3();
	private final Vector3 actual = new Vector3();
	private final Vector3 spawnPoint = new Vector3();
	private final Quaternion cameraRotation = new Quaternion();
	private final Quaternion bodyRotation = new Quaternion();
	private final Matrix4 ghost = new Matrix4();

	public PlayerSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.camera = camera;
		this.normalFieldOfView = camera.fieldOfView;
		this.gameWorld = gameWorld;
	}

	@Override
	public void addedToEngine(Engine engine) {
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
		gameWorld.bulletSystem.addStepListener(this);
	}

	@Override
	public void removedFromEngine(Engine engine) {
		gameWorld.bulletSystem.removeStepListener(this);
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
		hasStep = false;
		appliedGravity = Float.NaN; // a new body falls with the default gravity until told otherwise
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
		wishDirection.setZero();
		playerComponent.moving = false;
		boolean playing = playerComponent.controlsPlayer() && !playerComponent.isDead();
		playerComponent.crouched = playing && Controls.isPressed(Action.CROUCH);
		// The view goes down and up quickly but not instantly, like CS.
		float crouchTarget = playerComponent.crouched ? 1 : 0;
		playerComponent.crouchAmount += (crouchTarget - playerComponent.crouchAmount) * Math.min(1, delta * CROUCH_RATE);
		if (playing) {
			look();
			readMovementKeys();
			// Kept until a physics step can use it. With sv_autobunnyhopping, holding the key is enough:
			// the player jumps again the moment they land.
			if (Controls.isJustPressed(Action.JUMP) || (movement().autoBunnyhop && Controls.isPressed(Action.JUMP)))
				jumpRequested = true;
			if (Gdx.input.isKeyJustPressed(Input.Keys.B))
				teleportToSpawn(); // debug: get unstuck
		}
		float decay = (float) Math.exp(-PUNCH_RECOVERY * delta);
		playerComponent.punchUp *= decay;
		playerComponent.punchRight *= decay;
		syncModelAndCamera();
	}

	private void look() {
		PlayerComponent p = playerComponent;
		TouchControls touch = Controls.touch;
		if (touch != null) {
			// A drag across the whole screen height turns TOUCH_DEGREES; slower while zoomed in, like the
			// scope narrows the view.
			float scale = TOUCH_DEGREES * Config.touchSensitivity / touch.screenHeight()
					* camera.fieldOfView / normalFieldOfView;
			p.yaw -= touch.lookDeltaX() * scale;
			p.pitch += touch.lookDeltaY() * scale;
		} else {
			float scale = Config.sensitivity * MOUSE_SCALE;
			p.yaw -= Gdx.input.getDeltaX() * scale;
			p.pitch += Gdx.input.getDeltaY() * scale;
		}
		p.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, p.pitch));
		p.yaw %= 360f;
	}

	private void readMovementKeys() {
		// Move on the horizontal plane only: looking up or down must not change the speed.
		forward.set(MathUtils.sinDeg(playerComponent.yaw), 0, MathUtils.cosDeg(playerComponent.yaw));
		right.set(forward).crs(Vector3.Y);
		if (Controls.isPressed(Action.FORWARD))
			wishDirection.add(forward);
		if (Controls.isPressed(Action.BACKWARD))
			wishDirection.sub(forward);
		if (Controls.isPressed(Action.LEFT))
			wishDirection.sub(right);
		if (Controls.isPressed(Action.RIGHT))
			wishDirection.add(right);
		// The joystick is analog: pushed a little the player walks, pushed to the edge runs.
		TouchControls touch = Controls.touch;
		float stick = touch == null ? 0 : touch.stickAmount();
		if (stick > 0)
			wishDirection.mulAdd(forward, touch.moveY()).mulAdd(right, touch.moveX());
		wishDirection.nor(); // diagonals aren't faster
		playerComponent.moving = !wishDirection.isZero();
		// Crouching is the slowest, and silent like walking.
		playerComponent.running = !playerComponent.crouched && !Controls.isPressed(Action.WALK)
				&& (stick == 0 || stick >= TouchControls.RUN_THRESHOLD);
		wishSpeed = playerComponent.crouched ? PlayerMovement.CROUCH_SPEED
				: playerComponent.running ? PlayerMovement.RUN_SPEED : PlayerMovement.WALK_SPEED;
	}

	/** Physics step: turns the keys into a velocity and tells the character controller how far to move. */
	@Override
	public void beforeStep(float timeStep) {
		if (player == null || characterComponent.characterController == null)
			return;
		getPosition(stepFrom);
		boolean onGround = characterComponent.characterController.onGround();
		if (hasStep) {
			// Bumped into something: the velocity becomes what the last step really did
			// (sliding along the wall), instead of pushing into it at full speed.
			actual.set(stepFrom).sub(previousStepFrom);
			actual.y = 0;
			float wanted = commanded.len();
			if (wanted > 0.0001f && actual.len() < wanted * 0.95f)
				playerComponent.velocity.set(actual).scl(1f / timeStep);
		}
		if (playerComponent.isDead())
			playerComponent.velocity.setZero();
		MovementSettings movement = movement();
		float gravity = movement.gravityAcceleration();
		if (gravity != appliedGravity) { // sv_gravity changed (or a new body)
			characterComponent.characterController.setGravity(new Vector3(0, -gravity, 0));
			appliedGravity = gravity;
		}
		// Jumping the step you land skips that step's friction, as in Quake and CS: that is what
		// keeps the speed when bunnyhopping.
		boolean jumping = jumpRequested && onGround && !playerComponent.isDead();
		PlayerMovement.step(playerComponent.velocity, wishDirection, wishSpeed, onGround && !jumping, timeStep,
				movement.airAccelerate);
		if (jumping) {
			characterComponent.characterController.jump(new Vector3(0, PlayerMovement.JUMP_SPEED, 0));
			onGround = false;
		}
		jumpRequested = false;
		playerComponent.onGround = onGround;

		commanded.set(playerComponent.velocity).scl(timeStep);
		characterComponent.walkDirection.set(commanded);
		characterComponent.characterController.setWalkDirection(commanded);
		previousStepFrom.set(stepFrom);
		hasStep = true;
	}

	private void syncModelAndCamera() {
		getPosition(stepTo);
		// The physics position only changes 60 times a second: draw the player between
		// where the last step started and where it ended.
		if (hasStep && stepFrom.dst2(stepTo) < 25f)
			translation.set(stepFrom).lerp(stepTo, gameWorld.bulletSystem.interpolation());
		else
			translation.set(stepTo);
		bodyRotation.setFromAxis(Vector3.Y, playerComponent.yaw);
		modelComponent.instance.transform.set(translation, bodyRotation);
		camera.position.set(translation).add(0,
				CharacterShape.EYE_OFFSET - CharacterShape.CROUCH_DROP * playerComponent.crouchAmount, 0);
		updateCameraDirection();
	}

	/**
	 * Points the camera where the player aims, pushed by recoil and punches.
	 * Also called by the WeaponSystem right after a shot, so the kick shows the same frame.
	 */
	public void updateCameraDirection() {
		PlayerComponent p = playerComponent;
		if (p == null)
			return;
		float up = p.recoilUp * VIEW_RECOIL + p.punchUp, rightward = p.recoilRight * VIEW_RECOIL + p.punchRight;
		float pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, p.pitch - up));
		cameraRotation.setEulerAngles(p.yaw - rightward, pitch, 0);
		camera.direction.set(0, 0, 1);
		camera.up.set(0, 1, 0);
		camera.rotate(cameraRotation);
		camera.update(true);
	}

	private void teleportToSpawn() {
		teleport(spawnPoint);
	}

	/** The server's movement rules (sv_gravity, sv_airaccelerate, sv_autobunnyhopping). */
	private MovementSettings movement() {
		return gameWorld.session.client.getMovement();
	}

	/** Moves the player instantly (spawns, server corrections). */
	public void teleport(Vector3 position) {
		characterComponent.characterController.warp(position);
		characterComponent.walkDirection.setZero();
		characterComponent.characterController.setWalkDirection(characterComponent.walkDirection);
		playerComponent.velocity.setZero();
		hasStep = false; // nothing to interpolate from
		syncModelAndCamera();
	}

	/** Center of the player's capsule, at the last physics step. The eyes are {@link CharacterShape#EYE_OFFSET} above. */
	public Vector3 getPosition(Vector3 out) {
		characterComponent.ghostObject.getWorldTransform(ghost);
		return ghost.getTranslation(out);
	}
}
