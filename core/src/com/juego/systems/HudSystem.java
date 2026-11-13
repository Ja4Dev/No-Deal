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
import com.juego.UI.DeathScreen;
import com.juego.UI.HUD;
import com.juego.UI.PauseMenu;
import com.juego.UI.Scoreboard;
import com.juego.UI.TouchControls;
import com.juego.components.PlayerComponent;
import com.juego.core.GameWorld;
import com.juego.match.Match;
import com.juego.net.GameClient;
import com.juego.net.GameServer;
import com.juego.net.Protocol;
import com.juego.utils.Config;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;
import com.juego.utils.CrosshairSettings;
import com.juego.utils.Platform;
import com.juego.utils.Text;

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
	private DeathScreen deathScreen;
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
		deathScreen = new DeathScreen();
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

	/** Whether the player is playing (not in the pause menu or the console), so the touch controls are on. */
	public boolean isPlayerInControl() {
		return playerComponent != null && playerComponent.controlsPlayer();
	}

	/** Opens the pause menu (the app went to the background). */
	public void pauseGame() {
		if (playerComponent != null && !console.isOpen())
			playerComponent.pause = true;
	}

	@Override
	public void update(float delta) {
		if (player == null)
			return;
		boolean consoleKey = Controls.isJustPressed(Action.CONSOLE);
		if (console.isOpen()) {
			// Escape, back, the console key or its Cerrar button close it; nothing else reaches the game meanwhile.
			if (consoleKey || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
					|| Gdx.input.isKeyJustPressed(Input.Keys.BACK) || console.consumeCloseRequest()) {
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
		Gdx.input.setCursorCatched(playerComponent.controlsPlayer() && !Platform.touch);
		if (playerComponent.controlsPlayer()) // while paused the pause menu's button controls it
			playerComponent.scoreboard = Controls.isPressed(Action.SCOREBOARD);
		else if (playerComponent.console)
			playerComponent.scoreboard = false;

		boolean intermission = gameWorld.getMatchPhase() == Match.Phase.INTERMISSION;
		long remaining = gameWorld.getMatchRemainingMs();
		hud.setClock(intermission ? Text.get("hud.end") : Match.formatClock(remaining));
		showDamage();
		// The view climbs only part of the spray; following the recoil, the crosshair climbs the rest.
		float recoilLeft = 1 - PlayerSystem.VIEW_RECOIL;
		boolean follow = Config.crosshair.followRecoil;
		hud.setCrosshair(playerComponent.selectedGun() != null && !playerComponent.isDead() && !pauseMenu.isShowingSettings(),
				spreadPixels(), follow ? pixels(playerComponent.recoilRight * recoilLeft) : 0,
				follow ? pixels(playerComponent.recoilUp * recoilLeft) : 0);
		boolean spectating = gameWorld.deathCam().isWatching() && !intermission;
		hud.setPlayerWidgetsVisible(!spectating);
		hud.update(playerComponent.getHealth(), playerComponent.selectedGun());
		if (spectating)
			showDeathScreen();
		TouchControls touch = gameWorld.touchControls();
		if (touch != null && playerComponent.controlsPlayer())
			touch.draw();

		// Opened from the pause menu, the scoreboard replaces it until the next click. While playing the
		// (hidden) pause buttons must not take taps either.
		boolean scoreboardOverPause = playerComponent.pause && playerComponent.scoreboard;
		pauseMenu.setInteractive(playerComponent.pause && !scoreboardOverPause);
		if (!scoreboardOverPause)
			pauseMenu.update(delta);
		if (playerComponent.scoreboard || intermission) {
			rows.clear();
			gameWorld.getScoreRows(rows);
			String footer = scoreboardOverPause ? Text.get(Platform.touch ? "hud.tapBack" : "hud.clickBack")
					: intermission ? Text.get("hud.nextMatchIn", (remaining + 999) / 1000)
							: Text.get("hud.timeLeft", Match.formatClock(remaining));
			scoreboard.update(gameWorld.getScoreboardTitle(), rows, footer, gameWorld.session.client.getRenames());
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
			return Text.get("console.help", GameServer.commandsHelp());
		if (lower.startsWith("nombre") || lower.startsWith("name")) {
			String name = line.trim().replaceFirst("(?i)^(nombre|name)\\s*", "");
			if (name.isEmpty())
				return Text.get("console.renameUsage", Protocol.MAX_NAME_LENGTH);
			return Text.get("console.renamed", gameWorld.network.rename(name));
		}
		GameServer server = gameWorld.hostedServer();
		if (server == null)
			return Text.get("console.notHost");
		String reply = server.command(line);
		return reply != null ? reply : Text.get("console.unknown", line);
	}

	/** Who killed us, with their stats, and the countdown to respawning. */
	private void showDeathScreen() {
		DeathCamSystem cam = gameWorld.deathCam();
		GameClient.PlayerInfo killer = cam.watchedId() == Protocol.WORLD ? null
				: gameWorld.session.client.getPlayer(cam.watchedId());
		if (killer != null && killer.id == gameWorld.session.client.getLocalId())
			killer = null;
		deathScreen.update(killer == null ? null : killer.name, cam.fell(), cam.getGun(), cam.wasHeadshot(),
				killer == null ? 0 : killer.kills, killer == null ? 0 : killer.deaths,
				killer == null ? 0 : killer.assists, cam.respawnInMs());
		deathScreen.draw();
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

	/** How far the dynamic crosshair opens: the inaccuracy of moving and/or of spraying, as chosen. */
	private float spreadPixels() {
		WeaponSystem weapons = gameWorld.weaponSystem();
		CrosshairSettings settings = Config.crosshair;
		return pixels((settings.dynamicMove ? weapons.moveSpreadDegrees() : 0)
				+ (settings.dynamicFire ? weapons.fireSpreadDegrees() : 0));
	}

	/** An angle away from the screen center, in window pixels. */
	private float pixels(float degrees) {
		PerspectiveCamera camera = gameWorld.renderSystem.perspectiveCamera;
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
		if (deathScreen != null)
			deathScreen.resize(width, height);
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
		if (deathScreen != null)
			deathScreen.dispose();
		if (scoreboard != null)
			scoreboard.dispose();
	}
}
