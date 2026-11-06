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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.juego.UI.Console;
import com.juego.UI.HUD;
import com.juego.UI.PauseMenu;
import com.juego.UI.Scoreboard;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.match.Match;
import com.juego.net.GameServer;
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
	private Console console;
	private Scoreboard scoreboard;
	private final Array<Scoreboard.Row> rows = new Array<>();

	public HudSystem(GameWorld gameWorld) {
		super(RenderSystem.PRIORITY + 1); // right after the 3D scene is drawn
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
		console = new Console(this::runCommand);
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
		boolean consoleKey = Controls.isJustPressed(Action.CONSOLE);
		if (console.isOpen()) {
			// Escape or the console key close it; nothing else reaches the game meanwhile.
			if (consoleKey || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
				console.close();
				pauseMenu.takeInput();
			}
		} else if (consoleKey && !playerComponent.pause) {
			console.open();
		} else if (Controls.isJustPressed(Action.PAUSE) && !pauseMenu.consumePauseKey()) {
			// Inside the settings the pause key goes back to the pause buttons instead.
			playerComponent.pause = !playerComponent.pause;
			playerComponent.scoreboard = false;
		}
		playerComponent.console = console.isOpen();
		Gdx.input.setCursorCatched(playerComponent.controlsPlayer());
		if (playerComponent.controlsPlayer()) // while paused the pause menu's button controls it
			playerComponent.scoreboard = Controls.isPressed(Action.SCOREBOARD);
		else if (playerComponent.console)
			playerComponent.scoreboard = false;

		boolean intermission = gameWorld.getMatchPhase() == Match.Phase.INTERMISSION;
		long remaining = gameWorld.getMatchRemainingMs();
		hud.setClock(intermission ? "Fin" : Match.formatClock(remaining));
		showDamage();
		hud.setCrosshair(playerComponent.selectedGun() != null && !playerComponent.isDead() && !pauseMenu.isShowingSettings(),
				spreadPixels());
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
		console.draw(delta);
	}

	/**
	 * A console line. Match commands run on the server inside this game; when
	 * playing on someone else's server there is no server here to run them.
	 */
	private String runCommand(String line) {
		String lower = line.trim().toLowerCase();
		if (lower.equals("ayuda") || lower.equals("help") || lower.equals("?"))
			return "Comandos:\n" + GameServer.COMMANDS_HELP + "\nlimpiar  borra la consola";
		GameServer server = gameWorld.hostedServer();
		if (server == null)
			return "Los comandos los usa quien creo la partida (o la consola del servidor dedicado).";
		String reply = server.command(line);
		return reply != null ? reply : "No conozco '" + line + "'. Escribi 'ayuda'.";
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
		if (!playerComponent.isDead()) // the view jolts up (it springs back on its own)
			playerComponent.punchUp += Math.min(2.5f, 0.6f + damage / 40f);
	}

	/** How far from the screen center the gun's inaccuracy cone reaches, in window pixels. */
	private float spreadPixels() {
		PerspectiveCamera camera = gameWorld.renderSystem.perspectiveCamera;
		float degrees = gameWorld.weaponSystem().spreadDegrees();
		return (float) (Math.tan(Math.toRadians(degrees)) / Math.tan(Math.toRadians(camera.fieldOfView / 2))
				* Gdx.graphics.getHeight() / 2);
	}

	private final Vector3 forward = new Vector3(), right = new Vector3(), toSource = new Vector3();

	public void resize(int width, int height) {
		if (hud != null)
			hud.resize(width, height);
		if (pauseMenu != null)
			pauseMenu.resize(width, height);
		if (console != null)
			console.resize(width, height);
		if (scoreboard != null)
			scoreboard.resize(width, height);
	}

	@Override
	public void dispose() {
		if (hud != null)
			hud.dispose();
		if (pauseMenu != null)
			pauseMenu.dispose();
		if (console != null)
			console.dispose();
		if (scoreboard != null)
			scoreboard.dispose();
	}
}
