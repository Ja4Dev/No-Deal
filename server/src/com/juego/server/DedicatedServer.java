package com.juego.server;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.juego.bots.BotDifficulty;
import com.juego.match.Match;
import com.juego.net.GameServer;
import com.juego.net.GameServer.EventType;
import com.juego.net.Protocol;

/**
 * Dedicated server: the same {@link GameServer} the game uses when hosting,
 * without a GPU or local player. It opens a console-style window
 * ({@link ServerWindow}); with {@code --nogui}, or on a machine without a
 * display, it runs in the terminal instead.
 *
 * <pre>
 * java -jar NoDeal-server.jar [--port 25559] [--max-players 4] [--time-limit 10] [--frag-limit 20]
 *                             [--bots 0] [--bot-difficulty medio] [--nogui]
 * </pre>
 *
 * Terminal commands: {@code status}, {@code help}, {@code quit}, and the
 * match commands of {@link GameServer#command} (bots).
 */
public class DedicatedServer {
	private static final int TICK_RATE = 60;
	static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

	public static void main(String[] args) {
		GameServer.Config config = new GameServer.Config();
		boolean noGui = false;
		boolean optionsGiven = false;
		try {
			for (int i = 0; i < args.length; i++) {
				switch (args[i]) {
				case "--port":
					config.port = Integer.parseInt(args[++i]);
					break;
				case "--max-players":
					config.maxPlayers = Integer.parseInt(args[++i]);
					break;
				case "--time-limit":
					config.timeLimitMinutes = Integer.parseInt(args[++i]);
					break;
				case "--frag-limit":
					config.fragLimit = Integer.parseInt(args[++i]);
					break;
				case "--bots":
					config.bots = Integer.parseInt(args[++i]);
					break;
				case "--bot-difficulty": {
					String name = args[++i];
					config.botDifficulty = BotDifficulty.parse(name);
					if (config.botDifficulty == null)
						throw new IllegalArgumentException(
								"Unknown bot difficulty " + name + " (" + BotDifficulty.choices() + ")");
					break;
				}
				case "--nogui":
					noGui = true;
					continue;
				case "--help":
				case "-h":
					printUsage();
					return;
				default:
					throw new IllegalArgumentException("Unknown option " + args[i]);
				}
				optionsGiven = true;
			}
			String error = validate(config);
			if (error != null)
				throw new IllegalArgumentException(error);
		} catch (RuntimeException e) {
			System.err.println("Error: " + e.getMessage());
			printUsage();
			System.exit(2);
		}

		if (noGui || GraphicsEnvironment.isHeadless())
			runInTerminal(config);
		else
			// Double-clicked jar: wait for "Iniciar". Started with options: start right away.
			ServerWindow.open(config, optionsGiven);
	}

	/** Null if the settings are valid, otherwise what is wrong with them. */
	static String validate(GameServer.Config config) {
		if (config.port < 1 || config.port > 65535)
			return "--port must be between 1 and 65535";
		if (config.maxPlayers < 1 || config.maxPlayers > 32)
			return "--max-players must be between 1 and 32";
		if (config.timeLimitMinutes < 1 || config.timeLimitMinutes > 120)
			return "--time-limit must be between 1 and 120 minutes";
		if (config.fragLimit < 0 || config.fragLimit > 999)
			return "--frag-limit must be between 0 (none) and 999";
		if (config.bots < 0 || config.bots > GameServer.MAX_BOTS)
			return "--bots must be between 0 and " + GameServer.MAX_BOTS;
		return null;
	}

	private static void printUsage() {
		System.out.println("No Deal dedicated server (protocol v" + Protocol.VERSION + ")");
		System.out.println("Usage: java -jar NoDeal-server.jar [--port " + Protocol.DEFAULT_PORT + "] [--max-players "
				+ Protocol.MAX_PLAYERS + "] [--time-limit " + Protocol.DEFAULT_TIME_LIMIT_MINUTES + "] [--frag-limit "
				+ Protocol.DEFAULT_FRAG_LIMIT + "] [--bots 0] [--bot-difficulty medio] [--nogui]");
		System.out.println("  --time-limit  match length in minutes");
		System.out.println("  --frag-limit  kills that win the match early (0 = play the full time)");
		System.out.println("  --bots        bots in the match (0 to " + GameServer.MAX_BOTS + "), besides the players");
		System.out.println("  --bot-difficulty  " + BotDifficulty.choices());
		System.out.println("  --nogui       run in the terminal instead of opening the server window");
	}

	/**
	 * Starts the headless libGDX loop that runs the server. Info logs are
	 * silenced because the view already shows every event; errors are passed
	 * to the view as warnings.
	 */
	static HeadlessApplication launch(ServerHost host, ServerHost.View view) {
		HeadlessApplicationConfiguration appConfig = new HeadlessApplicationConfiguration();
		appConfig.updatesPerSecond = TICK_RATE;
		HeadlessApplication app = new HeadlessApplication(host, appConfig);
		app.setLogLevel(Application.LOG_ERROR);
		app.setApplicationLogger(new ApplicationLogger() {
			@Override
			public void log(String tag, String message) {
			}

			@Override
			public void log(String tag, String message, Throwable exception) {
			}

			@Override
			public void error(String tag, String message) {
				view.onEvent(EventType.WARNING, message);
			}

			@Override
			public void error(String tag, String message, Throwable exception) {
				view.onEvent(EventType.WARNING, message + ": " + exception);
			}

			@Override
			public void debug(String tag, String message) {
			}

			@Override
			public void debug(String tag, String message, Throwable exception) {
			}
		});
		return app;
	}

	/** Prints events with a timestamp, for the terminal and as a copy of the window's log. */
	static void print(EventType type, String message) {
		String line = "[" + LocalTime.now().format(CLOCK) + "] " + message;
		if (type == EventType.WARNING)
			System.err.println(line);
		else
			System.out.println(line);
	}

	// ---- terminal mode ----

	private static void runInTerminal(GameServer.Config config) {
		ServerHost.View view = new ServerHost.View() {
			@Override
			public void onEvent(EventType type, String message) {
				print(type, message);
			}

			@Override
			public void onStatus(ServerHost.Status status) {
			}
		};
		ServerHost host = new ServerHost(view);
		launch(host, view);
		host.start(config);
		host.post(() -> {
			if (host.server() == null)
				host.quit(); // the port was taken: nothing else to do in the terminal
			else
				System.out.println("Type 'help' for commands.");
		});
		startConsole(host);
	}

	/** Reads commands on a background thread; they run on the server thread. */
	private static void startConsole(ServerHost host) {
		Thread console = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					String command = line.trim();
					host.post(() -> execute(host, command));
				}
			} catch (IOException e) {
				// stdin closed (e.g. running as a service): no console
			}
		}, "Console");
		console.setDaemon(true);
		console.start();
	}

	private static void execute(ServerHost host, String line) {
		GameServer server = host.server();
		switch (line.toLowerCase()) {
		case "":
			break;
		case "status":
		case "players":
			if (server != null)
				System.out.print(server.getPlayerCount() + "/" + server.getMaxPlayers() + " jugadores y "
						+ server.getBotCount() + " bots en el puerto " + server.getPort() + ", " + server.describeMatch()
						+ "\n" + server.describePlayers());
			break;
		case "quit":
		case "exit":
		case "stop":
			host.quit();
			break;
		case "help":
		case "ayuda":
			System.out.println("Commands: status (list players), quit (notify players and stop)");
			System.out.println(GameServer.COMMANDS_HELP);
			break;
		default:
			String reply = server == null ? null : server.command(line);
			System.out.println(reply != null ? reply : "Unknown command '" + line + "'. Type 'help'.");
		}
	}

	/** "Jugando, quedan 7:42" / "Entre partidas, la proxima en 0:08". */
	static String describeMatch(ServerHost.Status status) {
		String clock = Match.formatClock(status.matchRemainingMs);
		return status.matchPlaying ? "Jugando, quedan " + clock : "Entre partidas, la proxima en " + clock;
	}
}
