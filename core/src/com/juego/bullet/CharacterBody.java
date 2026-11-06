package com.juego.bullet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShapeZ;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btPairCachingGhostObject;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.badlogic.gdx.utils.Disposable;
import com.juego.utils.CharacterShape;
import com.juego.utils.PlayerMovement;

/**
 * A walking character's physics: a capsule ghost object moved by Bullet's
 * kinematic character controller. The local player (on the client) and the
 * bots (on the server) use exactly the same body, so they climb the same
 * steps and fit through the same doors.
 */
public class CharacterBody implements Disposable {
	/** Highest step climbed without jumping. */
	public static final float STEP_HEIGHT = .35f;

	// btKinematicCharacterController treats +Z as the default up axis and rotates the
	// ghost object to match the requested up vector (+Y), so the capsule has to be
	// built along Z to end up standing. A Y capsule ends up lying on its side.
	public final btCapsuleShapeZ shape = new btCapsuleShapeZ(CharacterShape.RADIUS, CharacterShape.CYLINDER);
	public final btPairCachingGhostObject ghost = new btPairCachingGhostObject();
	public final btKinematicCharacterController controller;
	private btDiscreteDynamicsWorld world;
	private final Matrix4 transform = new Matrix4();

	/** @param transform where it starts (the capsule's center) */
	public CharacterBody(Matrix4 transform) {
		ghost.setWorldTransform(transform);
		ghost.setCollisionShape(shape);
		ghost.setCollisionFlags(btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);
		controller = new btKinematicCharacterController(ghost, shape, STEP_HEIGHT, new Vector3(0, 1f, 0));
		// Snappier than Bullet's default (29.4): jumps as high as before but shorter, like CS.
		controller.setGravity(new Vector3(0, -PlayerMovement.GRAVITY, 0));
	}

	/** Adds the body to a world (it needs a ghost pair callback on its broadphase). */
	public void addTo(btDiscreteDynamicsWorld world) {
		if (this.world != null)
			return;
		this.world = world;
		world.addCollisionObject(ghost, (short) btBroadphaseProxy.CollisionFilterGroups.CharacterFilter,
				(short) btBroadphaseProxy.CollisionFilterGroups.AllFilter);
		world.addAction(controller);
	}

	public void removeFromWorld() {
		if (world == null)
			return;
		world.removeAction(controller);
		world.removeCollisionObject(ghost);
		world = null;
	}

	public boolean isInWorld() {
		return world != null;
	}

	/** The capsule's center. */
	public Vector3 getPosition(Vector3 out) {
		ghost.getWorldTransform(transform);
		return transform.getTranslation(out);
	}

	/** Moves the body without sweeping (spawns and respawns). */
	public void warp(Vector3 position) {
		controller.warp(position);
		controller.setWalkDirection(Vector3.Zero);
		if (world != null)
			controller.reset(world);
	}

	@Override
	public void dispose() {
		removeFromWorld();
		controller.dispose();
		ghost.dispose();
		shape.dispose();
	}
}
