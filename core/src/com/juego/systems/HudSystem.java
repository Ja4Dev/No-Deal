package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import com.juego.UI.HUD;
import com.juego.UI.PauseMenu;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.utils.Config;

/**
 * The local player's 2D overlay: HUD, pause menu and scoreboard toggle. Added
 * last so it draws on top of the 3D scene.
 */
public class HudSystem extends EntitySystem implements EntityListener, Disposable {
	private final GameWorld gameWorld;
	private Entity player;
	private PlayerComponent playerComponent;
	private HUD hud;
	private PauseMenu pauseMenu;

	public HudSystem(GameWorld gameWorld) {
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
		hud = new HUD();
		pauseMenu = new PauseMenu(playerComponent, gameWorld);
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
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
		if (!playerComponent.pause) // while paused the pause menu's button controls it
			playerComponent.scoreboard = Gdx.input.isKeyPressed(Config.scoreboardsKey);
		hud.update(playerComponent.getHealth(), playerComponent.selectedGun);
		pauseMenu.update(delta);
	}

	public void resize(int width, int height) {
		if (hud != null)
			hud.resize(width, height);
		if (pauseMenu != null)
			pauseMenu.resize(width, height);
	}

	@Override
	public void dispose() {
		if (hud != null)
			hud.dispose();
		if (pauseMenu != null)
			pauseMenu.dispose();
	}
}
