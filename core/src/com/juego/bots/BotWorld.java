package com.juego.bots;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.AllHitsRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btGhostPairCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.juego.bullet.CharacterBody;

/**
 * The server's physics for bots: the level plus one {@link CharacterBody} per
 * living bot, stepped at a fixed rate like the client's world. Also answers
 * the questions bots ask about the level (can I see that point? where is the
 * floor here?), ignoring characters.
 * <p>
 * Not thread-safe: use it on the server thread only.
 */
public class BotWorld implements Disposable {
	public static final float STEP = 1f / 60f;

	private final btDefaultCollisionConfiguration collisionConfig = new btDefaultCollisionConfiguration();
	private final btCollisionDispatcher dispatcher = new btCollisionDispatcher(collisionConfig);
	private final btDbvtBroadphase broadphase = new btDbvtBroadphase();
	private final btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
	private final btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver,
			collisionConfig);
	private final btGhostPairCallback ghostPairCallback = new btGhostPairCallback();
	private final btCollisionObject level = new btCollisionObject();
	private final ClosestRayResultCallback ray = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
	private final Vector3 rayFrom = new Vector3(), rayTo = new Vector3();
	private final Matrix4 transform = new Matrix4();

	/** @param levelShape the level's static collision shape (not owned: the caller disposes it) */
	public BotWorld(btCollisionShape levelShape) {
		broadphase.getOverlappingPairCache().setInternalGhostPairCallback(ghostPairCallback);
		level.setCollisionShape(levelShape);
		world.addCollisionObject(level, (short) btBroadphaseProxy.CollisionFilterGroups.StaticFilter,
				(short) btBroadphaseProxy.CollisionFilterGroups.AllFilter);
		ray.setCollisionFilterMask((short) btBroadphaseProxy.CollisionFilterGroups.StaticFilter);
	}

	/** A new body, already in the world, with its center at {@code position}. */
	public CharacterBody createBody(Vector3 position) {
		CharacterBody body = new CharacterBody(transform.setToTranslation(position));
		body.addTo(world);
		return body;
	}

	public void add(CharacterBody body) {
		body.addTo(world);
	}

	/** One fixed physics step. */
	public void step() {
		world.stepSimulation(STEP, 0);
	}

	/**
	 * Fraction of the segment that is free of level geometry: 1 if nothing is
	 * in the way.
	 */
	public float clearFraction(Vector3 from, Vector3 to) {
		rayFrom.set(from);
		rayTo.set(to);
		ray.setCollisionObject(null);
		ray.setClosestHitFraction(1f);
		ray.setRayFromWorld(rayFrom);
		ray.setRayToWorld(rayTo);
		world.rayTest(rayFrom, rayTo, ray);
		return ray.hasHit() ? ray.getClosestHitFraction() : 1f;
	}

	/** Whether the level doesn't block the segment. */
	public boolean isClear(Vector3 from, Vector3 to) {
		return clearFraction(from, to) >= 1f;
	}

	/**
	 * Heights of every walkable surface (facing up) under the vertical segment
	 * from {@code top} down to {@code bottom}, highest first.
	 */
	public void floorsBelow(float x, float z, float top, float bottom, FloatArray out) {
		out.clear();
		rayFrom.set(x, top, z);
		rayTo.set(x, bottom, z);
		AllHitsRayResultCallback hits = new AllHitsRayResultCallback(rayFrom, rayTo);
		hits.setCollisionFilterMask((short) btBroadphaseProxy.CollisionFilterGroups.StaticFilter);
		try {
			world.rayTest(rayFrom, rayTo, hits);
			for (int i = 0; i < hits.getHitFractions().size(); i++) {
				if (hits.getHitNormalWorld().at(i).y > 0.7f)
					out.add(top + (bottom - top) * hits.getHitFractions().atConst(i));
			}
		} finally {
			hits.dispose();
		}
		out.sort();
		out.reverse();
		// A ray along a shared edge hits both triangles: keep one height per surface.
		for (int i = out.size - 1; i > 0; i--)
			if (out.get(i - 1) - out.get(i) < 0.05f)
				out.removeIndex(i);
	}

	/** Bounds of the level, as {min, max}. */
	public Vector3[] levelBounds() {
		Vector3 min = new Vector3(), max = new Vector3();
		level.getCollisionShape().getAabb(transform.idt(), min, max);
		return new Vector3[] { min, max };
	}

	@Override
	public void dispose() {
		world.removeCollisionObject(level);
		level.dispose();
		ray.dispose();
		world.dispose();
		ghostPairCallback.dispose();
		solver.dispose();
		broadphase.dispose();
		dispatcher.dispose();
		collisionConfig.dispose();
	}
}
