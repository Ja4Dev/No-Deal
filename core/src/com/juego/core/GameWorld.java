package com.juego.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.juego.components.CharacterComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.guns.AK47;
import com.juego.guns.M4A1;
import com.juego.guns.PrimaryGun;
import com.juego.managers.EntityFactory;
import com.juego.networking.ClientThread;
import com.juego.systems.BulletSystem;
import com.juego.systems.EnemySystem;
import com.juego.systems.PlayerSystem;
import com.juego.systems.RenderSystem;
import com.juego.systems.StatusSystem;

public class GameWorld {

	private Environment environment;
	private Engine engine;
	private Entity p1, p2, gun;
	public BulletSystem bulletSystem;
	public PlayerSystem playerSystem;
	public RenderSystem renderSystem;
	private DebugDrawer debugDrawer;
	public boolean debug = false;
	public Core game;
//	private ClientThread hc;
	public int jugador = 1;

	public GameWorld(Core game) {
		this.game = game;
//		hc = new ClientThread(this);
		Bullet.init();
		setDebug();
		initEnvironment();
		addSystems();
		addEntities();
	}

	private void setDebug() {
		if (debug) {
			debugDrawer = new DebugDrawer();
			debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
		}
	}

	private void initEnvironment() {
		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1, 0, 0, 1.f));
	}

	
	private void addEntities() {
		loadLevel("Bank");
		createPlayers(3, 5, 3, "ct", new M4A1(), new AK47());
		}
	
		private void loadLevel(String mapName) {
		engine.addEntity(EntityFactory.loadScene(0, 0, 0, mapName));
		}

	private void createPlayers(float x, float y, float z, String team, PrimaryGun primaryGunP1, PrimaryGun primaryGunP2) {
		p1 = EntityFactory.createPlayer(bulletSystem, x, y, z, team, primaryGunP1, 1);
		engine.addEntity(p1);
		
	

			engine.addEntity(gun = EntityFactory.loadGun(p1.getComponent(PlayerComponent.class).selectedGun.modelPos[0], p1.getComponent(PlayerComponent.class).selectedGun.modelPos[1], p1.getComponent(PlayerComponent.class).selectedGun.modelPos[2], p1.getComponent(PlayerComponent.class).selectedGun));
		
		playerSystem.gun = gun;
		renderSystem.gun = gun;
	}

	private void addSystems() {
		engine = new Engine();
		engine.addSystem(renderSystem = new RenderSystem());
		engine.addSystem(bulletSystem = new BulletSystem());
		engine.addSystem(playerSystem = new PlayerSystem(this, renderSystem.perspectiveCamera));
		engine.addSystem(new EnemySystem(this));
		engine.addSystem(new StatusSystem(this));
		if (debug)
			bulletSystem.collisionWorld.setDebugDrawer(this.debugDrawer);
	}

	public void render(float delta) {
		renderWorld(delta);
//		Vector3 position1 = new Vector3();
//		Vector3 position2 = new Vector3();
//		p1.getComponent(ModelComponent.class).instance.transform.getTranslation(position1);
//		p2.getComponent(ModelComponent.class).instance.transform.getTranslation(position2);
		//System.out.println("x = " + position.x + " y= " + position.y + " z= " + position.z);
//		System.out.println("FPS: " + Gdx.graphics.getFramesPerSecond());
	}

	protected void renderWorld(float delta) {
		engine.update(delta);
		if (debug) {
			debugDrawer.begin(renderSystem.perspectiveCamera);
			bulletSystem.collisionWorld.debugDrawWorld();
			debugDrawer.end();
		}
	}

	public void resize(int width, int height) {
		renderSystem.resize(width, height);
		playerSystem.pauseMenu.resize(width, height);
	}


	public void dispose() {
		bulletSystem.collisionWorld.removeAction(p1.getComponent(CharacterComponent.class).characterController);
		bulletSystem.collisionWorld.removeCollisionObject(p1.getComponent(CharacterComponent.class).ghostObject);
		bulletSystem.dispose();
		bulletSystem = null;
		renderSystem.dispose();
		p1.getComponent(CharacterComponent.class).characterController.dispose();
		p1.getComponent(CharacterComponent.class).ghostObject.dispose();
		p1.getComponent(CharacterComponent.class).ghostShape.dispose();
	}

	public void remove(Entity entity) {
		engine.removeEntity(entity);
		bulletSystem.removeBody(entity);
	}

}
