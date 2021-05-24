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
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Timer;
import com.juego.UI.HUD;
import com.juego.UI.PauseMenu;
import com.juego.components.AnimationComponent;
import com.juego.components.CharacterComponent;
import com.juego.components.EnemyComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.StatusComponent;
import com.juego.core.GameWorld;
import com.juego.managers.EntityFactory;
import com.juego.utils.Config;

public class PlayerSystem extends EntitySystem implements EntityListener {

	private Entity player;
	private PlayerComponent playerComponent;
	private CharacterComponent characterComponent;
	private ModelComponent modelComponent;
	Vector3 position = new Vector3();
	Quaternion quat = new Quaternion();
	private final PerspectiveCamera camera;
	private GameWorld gameWorld;
	private boolean thirdPerson = false;
	ClosestRayResultCallback rayTestCB;
	Vector3 rayFrom = new Vector3();
	Vector3 rayTo = new Vector3();
	HUD hud;
	private long lastShotTime;
	private long lastRechargeTime;
	private Sound reloadSound = Gdx.audio.newSound(Gdx.files.internal("sounds/Reload.mp3"));
	public Entity gun;
	private int actualGun = 1;
	float recoil;
	public PauseMenu pauseMenu;
	private boolean zoomed = false;
	float deltaX;
	float deltaY;
	float walkSpeed = 10f;
	private final long rechargeDelay = 2000;
	Matrix4 ghost = new Matrix4();
	Vector3 translation = new Vector3();
	float desviation;
	private Random r = new Random();
	boolean walking;
	Vector3 pos2 = new Vector3();
	
	public PlayerSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.camera = camera;
		this.gameWorld = gameWorld;
		rayTestCB = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
	}

	@Override
	public void addedToEngine(Engine engine) {
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
	}

	@Override
	public void entityAdded(Entity entity) {
			if (entity.getComponent(PlayerComponent.class).numJugador == gameWorld.jugador) {
			player = entity;
			playerComponent = entity.getComponent(PlayerComponent.class);
			characterComponent = entity.getComponent(CharacterComponent.class);
			modelComponent = entity.getComponent(ModelComponent.class);
			hud = new HUD(playerComponent.getHealth(), playerComponent.primaryAmmo);
			pauseMenu = new PauseMenu(playerComponent, gameWorld);
		}
		
	}

	@Override
	public void update(float delta) {
		if (player == null)
			return;
		Gdx.input.setCursorCatched(!playerComponent.pause);
		updateMovement(delta);
		if (actualGun == 1) {
			hud.update(playerComponent.getHealth(), playerComponent.primaryAmmo, playerComponent.selectedGun, true);
		} else {
			hud.update(playerComponent.getHealth(), playerComponent.selectedGun, true);
		}
		pauseMenu.update(delta);
	}

	private void updateMovement(float delta) {
		if (Gdx.input.isKeyJustPressed(Config.pauseKey))
			playerComponent.pause = !playerComponent.pause;
		walkSpeed = 10f;
		if (!playerComponent.pause) {
			updateCameraRotation();
			
			characterComponent.characterDirection.set(-1, 0, 0).rot(modelComponent.instance.transform).nor();
			characterComponent.walkDirection.set(0, 0, 0);
			if (Gdx.input.isKeyPressed(Config.forwardKey)) {
				characterComponent.walkDirection.add(camera.direction);
				walking = true;
			} 
			if (Gdx.input.isKeyPressed(Config.backwardKey)) {
				characterComponent.walkDirection.sub(camera.direction);
				walking = true;
			} 
			if (Gdx.input.isKeyPressed(Config.leftKey)) {
				position.set(camera.direction).crs(camera.up).scl(-1);
				walking = true;
			} 
			if (Gdx.input.isKeyPressed(Config.rightKey)) {
				position.set(camera.direction).crs(camera.up);
				walking = true;
			} 
			if (!Gdx.input.isKeyPressed(Config.forwardKey) && !Gdx.input.isKeyPressed(Config.backwardKey) && !Gdx.input.isKeyPressed(Config.leftKey) && !Gdx.input.isKeyPressed(Config.rightKey)) walking = false;
			
			if (Gdx.input.isKeyPressed(Config.walkKey)) {
				walkSpeed = 5f;
			} else
				walkSpeed = 10f;
			if (Gdx.input.isKeyJustPressed(Config.jumpKey)) {
				if (characterComponent.characterController.onGround()) {
					characterComponent.characterController.setJumpSpeed(1f);
					characterComponent.characterController.jump(new Vector3(0, 15, 0));
				}
			}
			updatePlayerActions();
		}
		characterComponent.walkDirection.add(position);
		characterComponent.walkDirection.scl(walkSpeed * delta);
		characterComponent.characterController.setWalkDirection(characterComponent.walkDirection);
		characterComponent.ghostObject.getWorldTransform(ghost);
		ghost.getTranslation(translation);
		modelComponent.instance.transform.set(translation.x, translation.y, translation.z, camera.direction.x,
				camera.direction.y, camera.direction.z, 0);
//		updateDebugThirdPerson();
		camera.position.set(translation.x, translation.y, translation.z);
		camera.update(true);

	}

	public void updatePlayerActions() {
		calculateDesviation();
		if (Gdx.input.isKeyJustPressed(Config.reloadKey))
			reloadGun();
		if (Gdx.input.isKeyJustPressed(Config.changeGunKey)) {
			if(zoomed) zoom();
			if (actualGun == 1) {
				changeToSecondary();
			} else changeToPrimary();
		}
		if (Gdx.input.isKeyPressed(Config.scoreboardsKey)) {
			playerComponent.scoreboard = true;
		} else
			playerComponent.scoreboard = false;

		if (System.currentTimeMillis() - lastShotTime >= playerComponent.selectedGun.delay
				&& System.currentTimeMillis() - lastRechargeTime >= rechargeDelay) {
			if (playerComponent.selectedGun.auto) {
				if (Gdx.input.isButtonPressed(Config.fireButton)) {
					fire();
				}
			} else {
				if (Gdx.input.isButtonJustPressed(Config.fireButton)) {
					fire();
				}
			}
		}
		if (Gdx.input.isButtonJustPressed(Config.zoomButton)) {
			if(playerComponent.selectedGun.sight) zoom();
		}
		
		if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
			//debug respawn
			camera.position.set(0, 6, 0);
			camera.update(true);
			modelComponent.instance.transform.set(0, 6, 0, camera.direction.x,
					camera.direction.y, camera.direction.z, 0);
		}
	}

	public void calculateDesviation() {
		if (walking){
			if (walkSpeed == 10){
				desviation = r.nextInt(51) + 50;
			} else {
				desviation = r.nextInt(26) + 25;
			}
		}
		if (!characterComponent.characterController.onGround()) desviation = r.nextInt(51) + 50;
		if (characterComponent.characterController.onGround() && !walking) desviation = 0;
		if (r.nextInt(2) == 1) desviation *= -1;
	}

	public void updateDebugThirdPerson() {
		if (gameWorld.debug) {
		if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
			thirdPerson = !thirdPerson;
		}
		if (thirdPerson) {
			camera.position.set(translation.x - 5f, translation.y + 2f, translation.z);
		}
		} else if (!gameWorld.debug || !thirdPerson) 
			camera.position.set(translation.x, translation.y, translation.z);
		
			
	}

	public void updateCameraRotation() {
		deltaX += -Gdx.input.getDeltaX() * Config.sensivity * 0.01f;
		deltaY -= -Gdx.input.getDeltaY() * Config.sensivity * 0.01f + recoil;
		if (deltaY >= 90) deltaY = 90;
		if (deltaY <= -90) deltaY = -90;
		recoil = 0;
		position.set(0, 0, 0);
		quat.idt();

		quat.setEulerAngles(deltaX, deltaY, 0);

		camera.direction.set(0, 0, 1);
		camera.up.set(0,1,0);
		camera.rotate(quat);
	}

	public void zoom() {
		if(!zoomed) {
		camera.fieldOfView = 30f;
		} else {
		camera.fieldOfView = 67f;
		}
		zoomed  = !zoomed;
	}

	public void changeToSecondary() {
		playerComponent.selectedGun = playerComponent.secondaryGun;
		gun.removeAll();
		gun = EntityFactory.loadGun(playerComponent.selectedGun.modelPos[0], playerComponent.selectedGun.modelPos[1],
				playerComponent.selectedGun.modelPos[2], playerComponent.selectedGun);
		gameWorld.renderSystem.gun = gun;
		actualGun = 2;
	}

	public void changeToPrimary() {
		playerComponent.selectedGun = playerComponent.primaryGun;
		gun.removeAll();
		gun = EntityFactory.loadGun(playerComponent.selectedGun.modelPos[0], playerComponent.selectedGun.modelPos[1],
				playerComponent.selectedGun.modelPos[2], playerComponent.selectedGun);
		gameWorld.renderSystem.gun = gun;
		actualGun = 1;
	}

	private void fire() {
		lastShotTime = System.currentTimeMillis();
		if (playerComponent.selectedGun.magazine > 0) {
			playerComponent.selectedGun.magazine--;
			Ray ray = camera.getPickRay(Gdx.graphics.getWidth() / 2 + desviation, Gdx.graphics.getHeight() / 2);
			rayFrom.set(ray.origin);
			rayTo.set(ray.direction).scl(playerComponent.selectedGun.range).add(rayFrom); /* distancia del rayo */
			playerComponent.selectedGun.sound.play(Config.fxVolume / 100);
			/*
			 * Because we reuse the ClosestRayResultCallback, we need reset it's values
			 */
			rayTestCB.setCollisionObject(null);
			rayTestCB.setClosestHitFraction(1f);
			rayTestCB.setRayFromWorld(rayFrom);
			rayTestCB.setRayToWorld(rayTo);
			gameWorld.bulletSystem.collisionWorld.rayTest(rayFrom, rayTo, rayTestCB);
			if (rayTestCB.hasHit()) {
				final btCollisionObject obj = rayTestCB.getCollisionObject();
				if (((Entity) obj.userData).getComponent(EnemyComponent.class) != null) {
					((Entity) obj.userData).getComponent(ModelComponent.class).instance.transform.getTranslation(pos2);
					if(rayTo.y >= position.y + 4) {
						((Entity) obj.userData).getComponent(StatusComponent.class).getDamageHead(playerComponent.selectedGun.damage);
					} else {
						((Entity) obj.userData).getComponent(StatusComponent.class).getDamage(playerComponent.selectedGun.damage);
					}
				}
				
				if (((Entity) obj.userData).getComponent(PlayerComponent.class) != null) {
					((Entity) obj.userData).getComponent(ModelComponent.class).instance.transform.getTranslation(pos2);
					if(rayTo.y >= position.y + 4) {
						((Entity) obj.userData).getComponent(PlayerComponent.class).getDamageHead(playerComponent.selectedGun.damage);
					} else {
					((Entity) obj.userData).getComponent(PlayerComponent.class).getDamage(playerComponent.selectedGun.damage);
					}
				}
			}
//		gun.getComponent(AnimationComponent.class).animate("Armature|shoot" , 1, 3);
			recoil = playerComponent.selectedGun.recoil;
		} else {
			reloadGun();
		}
	}

	private void reloadGun() {
		int neededAmmo = 0;
		if (playerComponent.primaryAmmo > 0 || playerComponent.selectedGun.equals(playerComponent.secondaryGun)) {
			if (playerComponent.selectedGun.magazine < playerComponent.selectedGun.magazineSize) {
				lastRechargeTime = System.currentTimeMillis();
				neededAmmo = playerComponent.selectedGun.magazineSize - playerComponent.selectedGun.magazine;
				if (playerComponent.selectedGun.equals(playerComponent.primaryGun)) {
					if (playerComponent.primaryAmmo > neededAmmo) {
						playerComponent.primaryAmmo -= neededAmmo;
						playerComponent.selectedGun.magazine += neededAmmo;
					} else {
						playerComponent.selectedGun.magazine += playerComponent.primaryAmmo;
						playerComponent.primaryAmmo = 0;
					}
				} else {
					playerComponent.selectedGun.magazine += neededAmmo;
				}
				reloadSound.play(Config.fxVolume / 100);
			}
		}
	}

	@Override
	public void entityRemoved(Entity entity) {
	}
}
