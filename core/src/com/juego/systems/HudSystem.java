package com.juego.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.juego.UI.HUD;
import com.juego.UI.PauseMenu;
import com.juego.UI.Scoreboard;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.match.Match;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;

/**
 * The local player's 2D overlay: HUD (with the match clock), pause menu and
 * scoreboard. Added last so it draws on top of the 3D scene.
 */
public class HudSystem extends EntitySystem implements EntityListener, Disposable {
	private final GameWorld gameWorld;
	private Entity player;
	private PlayerComponent playerComponent;
	private HUD hud;
	private PauseMenu pauseMenu;
	private Scoreboard scoreboard;
	private final Array<Scoreboard.Row> rows = new Array<>();

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
		scoreboard = new Scoreboard();
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
	}

	/** Null until the local player exists. */
	public HUD getHud() {
		return hud;
	}

	public boolean isPaused() {
		return playerComponent != null && playerComponent.pause;
	}

	@Override
	public void update(float delta) {
		if (player == null)
			return;
		if (Controls.isJustPressed(Action.PAUSE)) {
			playerComponent.pause = !playerComponent.pause;
			playerComponent.scoreboard = false;
		}
		Gdx.input.setCursorCatched(!playerComponent.pause);
		if (!playerComponent.pause) // while paused the pause menu's button controls it
			playerComponent.scoreboard = Controls.isPressed(Action.SCOREBOARD);

		boolean intermission = gameWorld.getMatchPhase() == Match.Phase.INTERMISSION;
		long remaining = gameWorld.getMatchRemainingMs();
		hud.setClock(intermission ? "Fin" : Match.formatClock(remaining));
		showDamage();
		hud.update(playerComponent.getHealth(), playerComponent.selectedGun());

		// Opened from the pause menu, the scoreboard replaces it until the next click.
		boolean scoreboardOverPause = playerComponent.pause && playerComponent.scoreboard;
		pauseMenu.setInteractive(!scoreboardOverPause);
		if (!scoreboardOverPause)
			pauseMenu.update(delta);
		if (playerComponent.scoreboard || intermission) {
			rows.clear();
			gameWorld.getScoreRows(rows);
			String footer = scoreboardOverPause ? "Clic para volver"
					: intermission ? "Nueva partida en " + (remaining + 999) / 1000 + " s"
							: "Quedan " + Match.formatClock(remaining);
			scoreboard.update(gameWorld.getScoreboardTitle(), rows, footer);
			scoreboard.draw();
		}
		if (scoreboardOverPause && Gdx.input.justTouched())
			playerComponent.scoreboard = false;
	}

	/** Turns the hits taken since last frame into the HUD's red flash, direction arc and a small view kick. */
	private void showDamage() {
		int damage = playerComponent.pendingDamage;
		if (damage <= 0)
			return;
		playerComponent.pendingDamage = 0;
		float direction = Float.NaN;
		Vector3 source = playerComponent.lastDamageSource;
		if (source != null) {
			PerspectiveCamera camera = gameWorld.renderSystem.perspectiveCamera;
			forward.set(camera.direction.x, 0, camera.direction.z).nor();
			right.set(forward).crs(Vector3.Y);
			toSource.set(source).sub(camera.position);
			toSource.y = 0;
			if (!toSource.isZero(0.01f))
				direction = MathUtils.atan2(toSource.dot(right), toSource.dot(forward)) * MathUtils.radiansToDegrees;
		}
		hud.showDamage(damage, direction);
		if (!playerComponent.isDead())
			playerComponent.pendingRecoil += Math.min(2.5f, 0.6f + damage / 40f); // aim punch
	}

	private final Vector3 forward = new Vector3(), right = new Vector3(), toSource = new Vector3();

	public void resize(int width, int height) {
		if (hud != null)
			hud.resize(width, height);
		if (pauseMenu != null)
			pauseMenu.resize(width, height);
		if (scoreboard != null)
			scoreboard.resize(width, height);
	}

	@Override
	public void dispose() {
		if (hud != null)
			hud.dispose();
		if (pauseMenu != null)
			pauseMenu.dispose();
		if (scoreboard != null)
			scoreboard.dispose();
	}
}
