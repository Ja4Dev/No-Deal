package com.juego.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.juego.guns.Armory;
import com.juego.net.GameServer;
import com.juego.net.NetUtils;
import com.juego.net.Protocol;

/**
 * Headless dedicated server: the same {@link GameServer} the game uses when
 * hosting, without a window, GPU or local player.
 *
 * <pre>
 * java -jar NoDeal-server.jar [--port 25559] [--max-players 4]
 * </pre>
 *
 * Console commands: {@code status}, {@code help}, {@code quit}.
 */
public class DedicatedServer extends ApplicationAdapter {
	private static final String TAG = "Server";
	private static final int TICK_RATE = 60;

	private final GameServer.Config config;
	private final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
	private GameServer server;

	public DedicatedServer(GameServer.Config config) {
		this.config = config;
	}

	public static void main(String[] args) {
		GameServer.Config config = new GameServer.Config();
		try {
			for (int i = 0; i < args.length; i++) {
				switch (args[i]) {
				case "--port":
					config.port = Integer.parseInt(args[++i]);
					break;
				case "--max-players":
					config.maxPlayers = Integer.parseInt(args[++i]);
					break;
				case "--help":
				case "-h":
					printUsage();
					return;
				default:
					throw new IllegalArgumentException("Unknown option " + args[i]);
				}
			}
			if (config.port < 1 || config.port > 65535)
				throw new IllegalArgumentException("--port must be between 1 and 65535");
			if (config.maxPlayers < 1 || config.maxPlayers > 32)
				throw new IllegalArgumentException("--max-players must be between 1 and 32");
		} catch (RuntimeException e) {
			System.err.println("Error: " + e.getMessage());
			printUsage();
			System.exit(2);
		}
		HeadlessApplicationConfiguration appConfig = new HeadlessApplicationConfiguration();
		appConfig.updatesPerSecond = TICK_RATE;
		new HeadlessApplication(new DedicatedServer(config), appConfig);
	}

	private static void printUsage() {
		System.out.println("No Deal dedicated server (protocol v" + Protocol.VERSION + ")");
		System.out.println("Usage: java -jar NoDeal-server.jar [--port " + Protocol.DEFAULT_PORT + "] [--max-players "
				+ Protocol.MAX_PLAYERS + "]");
	}

	@Override
	public void create() {
		Bullet.init();
		try {
			Armory armory = Armory.load(Gdx.files.internal(Armory.GUNS_FILE));
			server = new GameServer(config, new InternalFileHandleResolver(), armory, System::currentTimeMillis);
		} catch (IOException e) {
			Gdx.app.error(TAG, "Could not start on port " + config.port + ": " + e.getMessage());
			Gdx.app.exit();
			return;
		}
		for (String ip : NetUtils.lanAddresses())
			Gdx.app.log(TAG, "Players on your network can join at " + ip + ":" + server.getPort());
		Gdx.app.log(TAG, "Type 'help' for commands.");
		startConsole();
	}

	/** Reads commands on a background thread; they are executed on the server thread. */
	private void startConsole() {
		Thread console = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null)
					commands.add(line.trim().toLowerCase());
			} catch (IOException e) {
				// stdin closed (e.g. running as a service): no console
			}
		}, "Console");
		console.setDaemon(true);
		console.start();
	}

	@Override
	public void render() {
		if (server == null)
			return;
		String command;
		while ((command = commands.poll()) != null)
			execute(command);
		server.update();
	}

	private void execute(String command) {
		switch (command) {
		case "":
			break;
		case "status":
		case "players":
			System.out.print(server.getPlayerCount() + "/" + config.maxPlayers + " players on port "
					+ server.getPort() + "\n" + server.describePlayers());
			break;
		case "quit":
		case "exit":
		case "stop":
			Gdx.app.log(TAG, "Shutting down...");
			Gdx.app.exit();
			break;
		case "help":
			System.out.println("Commands: status (list players), quit (notify players and stop)");
			break;
		default:
			System.out.println("Unknown command '" + command + "'. Type 'help'.");
		}
	}

	@Override
	public void dispose() {
		if (server != null)
			server.dispose(); // tells every player the server closed
	}
}
