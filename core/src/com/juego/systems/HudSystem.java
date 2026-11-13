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
import com.juego.utils.Display;
import com.juego.utils.MovementSettings;
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
		showNumbers();
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

	/** How long the last jump's speed stays on the speedometer after landing for good. */
	private static final long JUMP_SHOWN_MS = 2500;
	private long fpsUpdatedMs;
	private String fpsText;

	/** cl_showfps and cl_showspeed. */
	private void showNumbers() {
		if (Config.showFps) {
			long now = System.currentTimeMillis();
			if (fpsText == null || now - fpsUpdatedMs >= 250) { // readable: not a new number every frame
				fpsUpdatedMs = now;
				int ping = gameWorld.network.pingMs();
				fpsText = Text.get("hud.fps", Gdx.graphics.getFramesPerSecond())
						+ (ping < 0 ? "" : "   " + Text.get("hud.ping", ping));
			}
			hud.setStats(fpsText);
		} else {
			hud.setStats(null);
			fpsText = null;
		}
		if (!Config.showSpeed || playerComponent.isDead()) {
			hud.setSpeed(null, null, 0);
			return;
		}
		PlayerComponent p = playerComponent;
		String speed = Integer.toString(Math.round(p.velocity.len() * MovementSettings.SPEED_SCALE));
		boolean recent = !p.onGround || System.currentTimeMillis() - p.jumpMs < JUMP_SHOWN_MS;
		String jump = null;
		if (recent && p.jumpMs != 0) {
			int gain = Math.round(p.jumpGain);
			jump = Text.get("hud.jump", Math.round(p.jumpSpeed))
					+ (p.jumpChained ? " (" + (gain >= 0 ? "+" : "") + gain + ")" : "");
		}
		hud.setSpeed(speed, jump, p.jumpChained ? p.jumpGain : 0);
	}

	/**
	 * A console line. The player's own settings (cl_showfps, cl_showspeed,
	 * fps_max) work everywhere; match commands run on the server inside this
	 * game, so when playing on someone else's server they only show the values.
	 */
	private String runCommand(String line) {
		String lower = line.trim().toLowerCase().replaceAll("\\s+", " ");
		String[] words = lower.split(" ");
		if (lower.equals("ayuda") || lower.equals("help") || lower.equals("?"))
			return Text.get("console.help", GameServer.commandsHelp());
		String own = clientVariable(words);
		if (own != null)
			return own;
		if (lower.startsWith("nombre") || lower.startsWith("name")) {
			String name = line.trim().replaceFirst("(?i)^(nombre|name)\\s*", "");
			if (name.isEmpty())
				return Text.get("console.renameUsage", Protocol.MAX_NAME_LENGTH);
			return Text.get("console.renamed", gameWorld.network.rename(name));
		}
		GameServer server = gameWorld.hostedServer();
		if (server == null && words[0].startsWith("sv_")) {
			// Someone else's server: its rules can be seen, not changed.
			String value = gameWorld.session.client.getMovement().get(words[0]);
			if (value == null)
				return Text.get("server.cvarUnknown", words[0]);
			return words.length < 2 ? Text.get("server.cvar", words[0], value)
					: Text.get("console.hostOnly", words[0], value);
		}
		if (server == null)
			return Text.get("console.notHost");
		String reply = server.command(line);
		return reply != null ? reply : Text.get("console.unknown", line);
	}

	/**
	 * cl_showfps, cl_showspeed and fps_max: shows the value, or sets it (and
	 * keeps it for next time).
	 *
	 * @return the answer, or null if it isn't one of these
	 */
	private String clientVariable(String[] words) {
		String name = words[0];
		boolean toggle = name.equals("cl_showfps") || name.equals("cl_showspeed");
		if (!toggle && !name.equals("fps_max"))
			return null;
		String current = name.equals("cl_showfps") ? (Config.showFps ? "1" : "0")
				: name.equals("cl_showspeed") ? (Config.showSpeed ? "1" : "0") : Integer.toString(Config.fpsMax);
		if (words.length < 2)
			return Text.get("server.cvar", name, current);
		String value = words[1];
		if (toggle) {
			if (!value.equals("0") && !value.equals("1"))
				return Text.get("server.cvarRange", name, 0, 1);
			if (name.equals("cl_showfps"))
				Config.showFps = value.equals("1");
			else
				Config.showSpeed = value.equals("1");
		} else {
			int fps;
			try {
				fps = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return Text.get("server.cvarNotNumber", name, value);
			}
			if (fps < 0 || fps > Config.MAX_FPS_MAX)
				return Text.get("server.cvarRange", name, 0, Config.MAX_FPS_MAX);
			Config.fpsMax = fps;
			Config.applyFpsMax();
		}
		Config.save();
		return Text.get("server.cvarChanged", name, value);
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
				* Display.height() / 2);
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
