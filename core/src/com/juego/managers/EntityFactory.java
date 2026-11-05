package com.juego.managers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShapeZ;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btPairCachingGhostObject;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.juego.bullet.MotionState;
import com.juego.components.AnimationComponent;
import com.juego.components.BulletComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.EnemyComponent;
import com.juego.components.GunComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.RemotePlayerComponent;
import com.juego.components.StatusComponent;
import com.juego.core.Assets;
import com.juego.guns.Gun;
import com.juego.systems.BulletSystem;
import com.juego.utils.CharacterShape;

/**
 * Builds the game's entities. Owned by a GameWorld: models it loads are
 * released, and the placeholder character model destroyed, on {@link #dispose()}.
 */
public class EntityFactory implements Disposable {
	/** Character capsule: radius 2, cylinder 2 -> 6 units tall, centered on its origin. */
	public static final float CHARACTER_RADIUS = CharacterShape.RADIUS;
	public static final float CHARACTER_CYLINDER = CharacterShape.CYLINDER;
	private static final String CHARACTER_TEXTURE = "badlogic.jpg";
	private static final float STEP_HEIGHT = .35f;

	private final BulletSystem bulletSystem;
	private final Model characterModel;
	private final Array<String> acquired = new Array<>();

	public EntityFactory(BulletSystem bulletSystem) {
		this.bulletSystem = bulletSystem;
		Texture texture = acquire(CHARACTER_TEXTURE, Texture.class);
		Material material = new Material(TextureAttribute.createDiffuse(texture),
				ColorAttribute.createSpecular(1, 1, 1, 1), FloatAttribute.createShininess(8f));
		float height = CHARACTER_CYLINDER + 2 * CHARACTER_RADIUS;
		characterModel = new ModelBuilder().createCapsule(CHARACTER_RADIUS, height, 16, material,
				VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
						| VertexAttributes.Usage.TextureCoordinates);
	}

	private <T> T acquire(String path, Class<T> type) {
		acquired.add(path);
		return Assets.acquire(path, type);
	}

	private Model acquireModel(String path) {
		acquired.add(path);
		return Assets.acquireModel(path);
	}

	private Entity createCharacter(float x, float y, float z) {
		Entity entity = new Entity();
		ModelComponent modelComponent = new ModelComponent(characterModel, x, y, z);
		entity.add(modelComponent);
		CharacterComponent characterComponent = new CharacterComponent();
		characterComponent.ghostObject = new btPairCachingGhostObject();
		characterComponent.ghostObject.setWorldTransform(modelComponent.instance.transform);
		// btKinematicCharacterController treats +Z as the default up axis and rotates the
		// ghost object to match the requested up vector (+Y), so the capsule has to be
		// built along Z to end up standing. A Y capsule ends up lying on its side.
		characterComponent.ghostShape = new btCapsuleShapeZ(CHARACTER_RADIUS, CHARACTER_CYLINDER);
		characterComponent.ghostObject.setCollisionShape(characterComponent.ghostShape);
		characterComponent.ghostObject.setCollisionFlags(btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);
		characterComponent.characterController = new btKinematicCharacterController(characterComponent.ghostObject,
				characterComponent.ghostShape, STEP_HEIGHT, new Vector3(0, 1f, 0));
		characterComponent.ghostObject.userData = entity;
		entity.add(characterComponent);
		bulletSystem.collisionWorld.addCollisionObject(characterComponent.ghostObject,
				(short) btBroadphaseProxy.CollisionFilterGroups.CharacterFilter,
				(short) btBroadphaseProxy.CollisionFilterGroups.AllFilter);
		bulletSystem.collisionWorld.addAction(characterComponent.characterController);
		return entity;
	}

	public Entity createPlayer(float x, float y, float z, String team, Gun primaryGun, Gun secondaryGun,
			int playerNumber) {
		Entity entity = createCharacter(x, y, z);
		entity.add(new PlayerComponent(team, primaryGun, secondaryGun, playerNumber));
		return entity;
	}

	public Entity createEnemy(float x, float y, float z) {
		Entity entity = createCharacter(x, y, z);
		entity.add(new EnemyComponent(EnemyComponent.STATE.HUNTING));
		entity.add(new StatusComponent());
		return entity;
	}

	/** Another online player: just a model, moved by the NetworkSystem from server snapshots. */
	public Entity createRemotePlayer(int playerId) {
		Entity entity = new Entity();
		ModelComponent model = new ModelComponent(characterModel, 0, -1000, 0);
		model.visible = false; // until its first snapshot arrives
		entity.add(model);
		entity.add(new RemotePlayerComponent(playerId));
		return entity;
	}

	/** First-person view model of a gun. Drawn by the RenderSystem with its own camera. */
	public Entity createGunView(Gun gun) {
		float[] offset = gun.def.viewOffset;
		ModelComponent modelComponent = new ModelComponent(acquireModel(gun.def.model), offset[0], offset[1],
				offset[2]);
		Entity gunEntity = new Entity();
		gunEntity.add(modelComponent);
		gunEntity.add(new GunComponent());
		gunEntity.add(new AnimationComponent(modelComponent.instance));
		return gunEntity;
	}

	/** A level: its model plus a static triangle-mesh collider built from the model's nodes. */
	public Entity createLevel(String mapName) {
		Model model = acquireModel("maps/" + mapName + ".g3dj");
		Entity entity = new Entity();
		ModelComponent modelComponent = new ModelComponent(model, 0, 0, 0);
		entity.add(modelComponent);
		BulletComponent bulletComponent = new BulletComponent();
		btCollisionShape shape = Bullet.obtainStaticNodeShape(model.nodes);
		bulletComponent.bodyInfo = new btRigidBody.btRigidBodyConstructionInfo(0, null, shape, Vector3.Zero);
		bulletComponent.body = new btRigidBody(bulletComponent.bodyInfo);
		bulletComponent.body.userData = entity;
		bulletComponent.motionState = new MotionState(modelComponent.instance.transform);
		((btRigidBody) bulletComponent.body).setMotionState(bulletComponent.motionState);
		entity.add(bulletComponent);
		return entity;
	}

	@Override
	public void dispose() {
		characterModel.dispose();
		for (String path : acquired)
			Assets.release(path);
		acquired.clear();
	}
}
