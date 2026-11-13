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
import com.juego.utils.Text;

/**
 * Dedicated server: the same {@link GameServer} the game uses when hosting,
 * without a GPU or local player. It opens a console-style window
 * ({@link ServerWindow}); with {@code --nogui}, or on a machine without a
 * display, it runs in the terminal instead.
 *
 * <pre>
 * java -jar NoDeal-server.jar [--port 25559] [--max-players 4] [--time-limit 10] [--frag-limit 20]
 *                             [--bots 0] [--bot-difficulty medio] [--lang es|en] [--nogui]
 * </pre>
 *
 * Terminal commands: {@code status}, {@code help}, {@code quit}, and the
 * match commands of {@link GameServer#command} (bots).
 */
public class DedicatedServer {
	private static final int TICK_RATE = 60;
	static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

	public static void main(String[] args) {
		Text.setLanguage(language(args));
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
								Text.get("ds.unknownDifficulty", name, BotDifficulty.choices()));
					break;
				}
				case "--nogui":
					noGui = true;
					continue;
				case "--lang":
					i++; // already read by language()
					continue;
				case "--help":
				case "-h":
					printUsage();
					return;
				default:
					throw new IllegalArgumentException(Text.get("ds.unknownOption", args[i]));
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
			return Text.get("ds.badPort");
		if (config.maxPlayers < 1 || config.maxPlayers > 32)
			return Text.get("ds.badPlayers");
		if (config.timeLimitMinutes < 1 || config.timeLimitMinutes > 120)
			return Text.get("ds.badTime");
		if (config.fragLimit < 0 || config.fragLimit > 999)
			return Text.get("ds.badFrags");
		if (config.bots < 0 || config.bots > GameServer.MAX_BOTS)
			return Text.get("ds.badBots", GameServer.MAX_BOTS);
		return null;
	}

	private static void printUsage() {
		System.out.println(Text.get("ds.title.usage", Protocol.VERSION));
		System.out.println(Text.get("ds.usage", Protocol.DEFAULT_PORT, Protocol.MAX_PLAYERS,
				Protocol.DEFAULT_TIME_LIMIT_MINUTES, Protocol.DEFAULT_FRAG_LIMIT, GameServer.MAX_BOTS, BotDifficulty.choices()));
	}

	/**
	 * The language of the messages: --lang if given; otherwise the one chosen in
	 * the game on this computer (libGDX keeps it in ~/.prefs/NoDeal), so the
	 * game, its launcher and the server agree; otherwise the system's.
	 */
	static Text.Language language(String[] args) {
		for (int i = 0; i + 1 < args.length; i++) {
			if (args[i].equals("--lang")) {
				Text.Language chosen = Text.Language.parse(args[i + 1]);
				if (chosen != null)
					return chosen;
				System.err.println(Text.get("ds.unknownLanguage", args[i + 1]));
			}
		}
		java.io.File prefs = new java.io.File(System.getProperty("user.home"), ".prefs/NoDeal");
		if (prefs.isFile()) {
			java.util.Properties saved = new java.util.Properties();
			try (java.io.InputStream in = new java.io.FileInputStream(prefs)) {
				saved.loadFromXML(in);
				Text.Language chosen = Text.Language.parse(saved.getProperty("language", ""));
				if (chosen != null)
					return chosen;
			} catch (IOException | RuntimeException e) {
				// unreadable: use the system's
			}
		}
		return Text.system();
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
				System.out.println(Text.get("ds.terminalHelp"));
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
		case "estado":
		case "jugadores":
			if (server != null)
				System.out.print(Text.get("ds.terminalStatus", server.getPlayerCount(), server.getMaxPlayers(),
						server.getBotCount(), server.getPort(), server.describeMatch()) + "\n" + server.describePlayers());
			break;
		case "quit":
		case "exit":
		case "stop":
		case "salir":
			host.quit();
			break;
		case "help":
		case "ayuda":
			System.out.println(Text.get("ds.terminalCommands"));
			System.out.println(GameServer.commandsHelp());
			break;
		default:
			String reply = server == null ? null : server.command(line);
			System.out.println(reply != null ? reply : Text.get("ds.terminalUnknown", line));
		}
	}

	/** "Jugando, quedan 7:42" / "Entre partidas, la proxima en 0:08". */
	static String describeMatch(ServerHost.Status status) {
		String clock = Match.formatClock(status.matchRemainingMs);
		return Text.get(status.matchPlaying ? "ds.playing" : "ds.between", clock);
	}
}
