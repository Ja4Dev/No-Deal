package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btAxisSweep3;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btGhostPairCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.utils.Array;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;

/**
 * The physics world. It advances in fixed steps of {@link #fixedTimeStep}, so
 * movement is the same at any frame rate; {@link #interpolation()} says how
 * far the current frame is between the last step and the next one, so the
 * camera can move smoothly on monitors faster than the physics.
 */
public class BulletSystem extends EntitySystem implements EntityListener {

	/** Runs code right before every physics step (e.g. the player's movement). */
	public interface StepListener {
		void beforeStep(float timeStep);
	}

	public final btCollisionConfiguration collisionConfiguration;
	public final btCollisionDispatcher dispatcher;
	public final btBroadphaseInterface broadphase;
	public final btConstraintSolver solver;
	public final btDiscreteDynamicsWorld collisionWorld;
	private btGhostPairCallback ghostPairCallback;
	private final MyContactListener contactListener;
	public int maxSubSteps = 5;
	public float fixedTimeStep = 1f / 60f;
	private float accumulator;
	private final Array<StepListener> stepListeners = new Array<>();

	@Override
	public void addedToEngine(Engine engine) {
		engine.addEntityListener(Family.all(BulletComponent.class).get(), this);
	}

	public BulletSystem() {
		contactListener = new MyContactListener();
		contactListener.enable();
		collisionConfiguration = new btDefaultCollisionConfiguration();
		dispatcher = new btCollisionDispatcher(collisionConfiguration);
		broadphase = new btAxisSweep3(new Vector3(-1000, -1000, -1000), new Vector3(1000, 1000, 1000));
		solver = new btSequentialImpulseConstraintSolver();
		collisionWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
		ghostPairCallback = new btGhostPairCallback();
		broadphase.getOverlappingPairCache().setInternalGhostPairCallback(ghostPairCallback);
		collisionWorld.setGravity(new Vector3(0, -20f, 0));
	}

	@Override
	public void update(float deltaTime) {
		// After a long hitch, drop the time that doesn't fit instead of trying to catch up.
		accumulator = Math.min(accumulator + deltaTime, maxSubSteps * fixedTimeStep);
		while (accumulator >= fixedTimeStep) {
			for (int i = 0; i < stepListeners.size; i++)
				stepListeners.get(i).beforeStep(fixedTimeStep);
			collisionWorld.stepSimulation(fixedTimeStep, 0); // exactly one step
			accumulator -= fixedTimeStep;
		}
	}

	/** From 0 (the last step just happened) to 1 (the next one is due). */
	public float interpolation() {
		return accumulator / fixedTimeStep;
	}

	public void addStepListener(StepListener listener) {
		stepListeners.add(listener);
	}

	public void removeStepListener(StepListener listener) {
		stepListeners.removeValue(listener, true);
	}

	public void dispose() {
		collisionWorld.dispose();
		if (solver != null)
			solver.dispose();
		if (broadphase != null)
			broadphase.dispose();
		if (dispatcher != null)
			dispatcher.dispose();
		if (collisionConfiguration != null)
			collisionConfiguration.dispose();
		ghostPairCallback.dispose();
		contactListener.dispose();
	}

	@Override
	public void entityAdded(Entity entity) {
		BulletComponent bulletComponent = entity.getComponent(BulletComponent.class);
		if (bulletComponent.body != null) {
			collisionWorld.addRigidBody((btRigidBody) bulletComponent.body);
		}
	}

	/** Removes the entity's physics objects from the world and frees their native memory. */
	public void destroyBody(Entity entity) {
		BulletComponent comp = entity.getComponent(BulletComponent.class);
		if (comp != null && comp.body != null) {
			collisionWorld.removeCollisionObject(comp.body);
			comp.body.dispose();
			comp.bodyInfo.getCollisionShape().dispose();
			comp.bodyInfo.dispose();
			comp.motionState.dispose();
			comp.body = null;
		}
		CharacterComponent character = entity.getComponent(CharacterComponent.class);
		if (character != null && character.characterController != null) {
			collisionWorld.removeAction(character.characterController);
			collisionWorld.removeCollisionObject(character.ghostObject);
			character.characterController.dispose();
			character.ghostObject.dispose();
			character.ghostShape.dispose();
			character.characterController = null;
		}
	}

	@Override
	public void entityRemoved(Entity entity) {

	}
}