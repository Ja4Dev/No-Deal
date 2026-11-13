package com.juego.server;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.utils.Array;
import com.juego.guns.Armory;
import com.juego.net.GameServer;
import com.juego.net.GameServer.EventType;
import com.juego.net.NetUtils;
import com.juego.utils.Text;

/**
 * Runs a {@link GameServer} on the libGDX (headless) thread and lets another
 * thread start and stop it. Bullet is not thread-safe, so the console thread or
 * the Swing window never touch the server directly: they post tasks that run
 * here, and get events and status back through a {@link View}.
 */
public class ServerHost extends ApplicationAdapter {
	/** How often the view gets a fresh {@link Status}. */
	private static final long STATUS_INTERVAL_MS = 250;

	/** Where the host reports to. Called on the server thread. */
	public interface View {
		void onEvent(EventType type, String message);

		void onStatus(Status status);
	}

	/** A copy of the server state, safe to hand to another thread. */
	public static final class Status {
		public final boolean running;
		public final int port, maxPlayers;
		public final boolean matchPlaying;
		public final long matchRemainingMs;
		/** Everyone, bots included. */
		public final Array<GameServer.PlayerInfo> players;
		public final int people, bots;
		public final Array<String> addresses;
		/** How many start/stop requests the host has handled, so a view knows when its request is done. */
		public final long handledRequests;

		Status(GameServer server, Array<String> addresses, long handledRequests) {
			this.handledRequests = handledRequests;
			running = server != null;
			port = running ? server.getPort() : 0;
			maxPlayers = running ? server.getMaxPlayers() : 0;
			matchPlaying = running && server.isMatchPlaying();
			matchRemainingMs = running ? server.getMatchRemainingMs() : 0;
			players = running ? server.getPlayers() : new Array<>();
			int botCount = 0;
			for (GameServer.PlayerInfo p : players)
				if (p.bot)
					botCount++;
			bots = botCount;
			people = players.size - botCount;
			this.addresses = addresses;
		}
	}

	private final View view;
	private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
	private Armory armory;
	private GameServer server;
	private Array<String> addresses = new Array<>();
	private long nextStatusMs;
	private long handledRequests;
	private volatile Runnable onDisposed;

	public ServerHost(View view) {
		this.view = view;
	}

	/** Runs {@code task} on the server thread at the start of the next tick. */
	public void post(Runnable task) {
		tasks.add(task);
	}

	/** Starts a server; the request counts once it succeeded or failed (see {@link Status#handledRequests}). */
	public void start(GameServer.Config config) {
		post(() -> {
			startNow(config);
			handledRequests++;
			publishStatus();
		});
	}

	public void stop() {
		post(() -> {
			stopNow();
			handledRequests++;
			publishStatus();
		});
	}

	/** Stops the server, then the whole application. */
	public void quit() {
		post(() -> Gdx.app.exit());
	}

	/** Runs on the server thread after the server stopped and the application is closing. */
	public void setOnDisposed(Runnable onDisposed) {
		this.onDisposed = onDisposed;
	}

	/** Only valid on the server thread (inside a posted task). Null while stopped. */
	public GameServer server() {
		return server;
	}

	@Override
	public void create() {
		Bullet.init();
		armory = Armory.load(Gdx.files.internal(Armory.GUNS_FILE));
		publishStatus();
	}

	private void startNow(GameServer.Config config) {
		if (server != null)
			return;
		try {
			server = new GameServer(config, new InternalFileHandleResolver(), armory, System::currentTimeMillis);
		} catch (IOException e) {
			view.onEvent(EventType.WARNING, Text.get("ds.portFailed", config.port, e.getMessage()));
			publishStatus();
			return;
		}
		server.setListener(view::onEvent);
		addresses = NetUtils.lanAddresses();
		view.onEvent(EventType.INFO, Text.get("ds.started", server.getPort(), config.maxPlayers, config.timeLimitMinutes,
				config.fragLimit > 0 ? Text.get("ds.startedFrags", config.fragLimit) : "",
				server.getBotCount() > 0 ? Text.get("ds.startedBots", server.getBotCount(), config.botDifficulty.label()) : ""));
		for (String ip : addresses)
			view.onEvent(EventType.INFO, Text.get("ds.lanAddress", ip + ":" + server.getPort()));
		if (addresses.size == 0)
			view.onEvent(EventType.WARNING, Text.get("ds.noLan", "127.0.0.1:" + server.getPort()));
		publishStatus();
	}

	private void stopNow() {
		if (server == null)
			return;
		server.dispose(); // tells every player the server closed
		server = null;
		view.onEvent(EventType.INFO, Text.get("ds.stopped"));
		publishStatus();
	}

	private void publishStatus() {
		nextStatusMs = System.currentTimeMillis() + STATUS_INTERVAL_MS;
		view.onStatus(new Status(server, addresses, handledRequests));
	}

	@Override
	public void render() {
		Runnable task;
		while ((task = tasks.poll()) != null)
			task.run();
		if (server != null)
			server.update();
		if (System.currentTimeMillis() >= nextStatusMs)
			publishStatus();
	}

	@Override
	public void dispose() {
		stopNow();
		Runnable callback = onDisposed;
		if (callback != null)
			callback.run();
	}
}
