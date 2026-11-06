package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy;
import com.badlogic.gdx.utils.Disposable;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.net.Protocol;
import com.juego.utils.CharacterShape;

/**
 * While the local player is dead, the camera leaves their eyes and follows
 * whoever killed them from behind and above (a spectator view), gliding there
 * from where they fell and never going through walls. Killed by the map or by
 * themselves, it looks at where they died. The {@link HudSystem} shows the
 * killer's name and stats and the respawn countdown on top.
 * <p>
 * Runs after the player and weapon systems, so it has the last word on the
 * camera, and before the render system.
 */
public class DeathCamSystem extends EntitySystem implements EntityListener, Disposable {
	/** Where the camera sits: this far behind the one watched, this far above their eyes. */
	private static final float DISTANCE = 13f, HEIGHT = 4.5f;
	/** How fast the camera catches up (per second). */
	private static final float FOLLOW = 5f;

	private final GameWorld gameWorld;
	private final PerspectiveCamera camera;
	private PlayerComponent playerComponent;
	private ModelComponent playerModel;

	private boolean watching;
	private int killerId = Protocol.WORLD;
	private String gun = "";
	private boolean headshot, fell;
	private long deathMs;
	private boolean cameraPlaced;
	private final Vector3 deathPosition = new Vector3(), target = new Vector3(), desired = new Vector3(),
			cameraPosition = new Vector3(), tmp = new Vector3();
	private final float[] yaw = new float[1];
	private final ClosestRayResultCallback levelRay = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);

	public DeathCamSystem(GameWorld gameWorld, PerspectiveCamera camera) {
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
		if (gameWorld.isLocalPlayer(entity)) {
			playerComponent = entity.getComponent(PlayerComponent.class);
			playerModel = entity.getComponent(ModelComponent.class);
		}
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity.getComponent(PlayerComponent.class) == playerComponent)
			playerComponent = null;
	}

	/**
	 * The server says we died.
	 *
	 * @param killerId {@link Protocol#WORLD} if nobody killed us (we fell)
	 */
	public void onKilled(int killerId, String gun, boolean headshot) {
		watching = true;
		this.killerId = killerId;
		this.gun = gun == null ? "" : gun;
		this.headshot = headshot;
		fell = killerId == Protocol.WORLD;
		deathMs = gameWorld.timeMs();
		cameraPlaced = false;
		gameWorld.playerSystem().getPosition(deathPosition);
		gameWorld.weaponSystem().unzoom();
	}

	/** Whether the spectator view is on (we're dead). */
	public boolean isWatching() {
		return watching;
	}

	/** Who we are watching: the killer, or {@link Protocol#WORLD} if nobody (we fell, or killed ourselves). */
	public int watchedId() {
		return killerId;
	}

	public String getGun() {
		return gun;
	}

	public boolean wasHeadshot() {
		return headshot;
	}

	/** Killed by falling out of the map. */
	public boolean fell() {
		return fell;
	}

	/** Time until the server brings us back. */
	public long respawnInMs() {
		return Math.max(0, Protocol.RESPAWN_DELAY_MS - (gameWorld.timeMs() - deathMs));
	}

	@Override
	public void update(float delta) {
		if (playerComponent == null)
			return;
		boolean dead = playerComponent.isDead();
		playerModel.visible = !dead; // no standing body where we died
		if (!dead) {
			watching = false;
			return;
		}
		if (!watching) { // dead without a KILL message (e.g. joined while dead): just look at where we are
			onKilled(Protocol.WORLD, "", false);
			fell = false;
		}
		gameWorld.renderSystem.gun = null; // no gun in our hands while watching

		boolean followKiller = killerId != Protocol.WORLD && killerId != gameWorld.session.client.getLocalId()
				&& gameWorld.network.remotePosition(killerId, target, yaw);
		if (followKiller) {
			target.add(0, CharacterShape.EYE_OFFSET - 0.5f, 0);
		} else {
			target.set(deathPosition);
			yaw[0] = playerComponent.yaw;
		}
		desired.set(-MathUtils.sinDeg(yaw[0]) * DISTANCE, HEIGHT, -MathUtils.cosDeg(yaw[0]) * DISTANCE).add(target);
		keepOutOfWalls();

		if (!cameraPlaced) { // start from where our eyes were, then glide
			cameraPosition.set(camera.position);
			cameraPlaced = true;
		}
		cameraPosition.lerp(desired, 1 - (float) Math.exp(-FOLLOW * delta));
		camera.position.set(cameraPosition);
		camera.up.set(Vector3.Y);
		tmp.set(target).sub(cameraPosition);
		if (!tmp.isZero(0.01f))
			camera.direction.set(tmp.nor());
		camera.update();
	}

	/** If a wall is between the one watched and the camera, the camera comes in front of it. */
	private void keepOutOfWalls() {
		levelRay.setCollisionObject(null);
		levelRay.setClosestHitFraction(1f);
		levelRay.setRayFromWorld(target);
		levelRay.setRayToWorld(desired);
		gameWorld.bulletSystem.collisionWorld.rayTest(target, desired, levelRay);
		if (!levelRay.hasHit())
			return;
		levelRay.getHitPointWorld(tmp);
		// Back off the wall a little, towards the one watched.
		desired.set(target).sub(tmp).nor().scl(0.8f).add(tmp);
	}

	@Override
	public void dispose() {
		levelRay.dispose();
	}
}
