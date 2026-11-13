package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.juego.guns.Armory;

/**
 * A player who just joined is silent while the game loads the level; on a
 * slow phone that takes longer than the normal timeout, and the server used
 * to drop them before they could play (found joining over Bluetooth on an
 * emulator, but it happens over Wi-Fi too). Time is a fake clock here.
 */
class LoadingTimeoutTest {
	private static final File ASSETS = new File("assets");

	@TempDir
	static Path tmp;
	private final AtomicLong time = new AtomicLong(1_000_000);
	private GameServer server;
	private GameClient client;

	@BeforeAll
	static void init() throws IOException {
		Bullet.init();
		Files.writeString(tmp.resolve("Test.json"), "{model: maps/Bank.g3dj, spawnPoints: [[3,4,2],[-6,4,-4]]}");
	}

	@BeforeEach
	void start() throws IOException {
		GameServer.Config config = new GameServer.Config();
		config.port = 0;
		config.map = "Test";
		Armory armory = Armory.load(new FileHandle(new File(ASSETS, Armory.GUNS_FILE)));
		server = new GameServer(config,
				path -> path.startsWith("data/maps/") ? new FileHandle(tmp.resolve(path.substring(10)).toFile())
						: new FileHandle(new File(ASSETS, path)),
				armory, time::get);
		client = new GameClient(time::get);
		client.connect("127.0.0.1", server.getPort(), "Lento");
		pump("connected", () -> client.getStatus() == GameClient.Status.CONNECTED && server.getPlayerCount() == 1);
	}

	@AfterEach
	void stop() {
		client.dispose();
		server.dispose();
	}

	/** Server and client run, with the clock moving a little each round (real sockets in between). */
	private void pump(String what, BooleanSupplier condition) {
		long deadline = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < deadline) {
			time.addAndGet(16);
			server.update();
			client.update();
			if (condition.getAsBoolean())
				return;
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		fail("timed out waiting for: " + what);
	}

	/** Only the server runs: the client is frozen (loading the level, or gone). */
	private void serverAlone(long ms) {
		// First take in what the client already sent, so it isn't read as fresh after the jump.
		try {
			Thread.sleep(30);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		server.update();
		time.addAndGet(ms);
		server.update();
	}

	@Test
	void aPlayerLoadingTheLevelIsNotDropped() {
		serverAlone(Protocol.TIMEOUT_MS * 2); // a slow phone loading for 10 s
		assertEquals(1, server.getPlayerCount(), "still in the match while loading");

		client.sendState(new Vector3(3, 4, 2), 0, 0, 0); // loaded: playing now
		pump("the server hears the first state", () -> {
			serverAlone(0);
			return true;
		});
		pump("still connected", () -> client.getStatus() == GameClient.Status.CONNECTED);
		assertEquals(1, server.getPlayerCount());
	}

	@Test
	void aPlayerWhoNeverFinishesLoadingIsDroppedEventually() {
		serverAlone(Protocol.LOADING_TIMEOUT_MS + 1000);
		assertEquals(0, server.getPlayerCount());
	}

	@Test
	void oncePlayingTheNormalTimeoutApplies() {
		client.sendState(new Vector3(3, 4, 2), 0, 0, 0);
		pump("state sent", () -> true);
		for (int i = 0; i < 20; i++) // let the state arrive
			pump("round", () -> true);
		serverAlone(Protocol.TIMEOUT_MS + 1000);
		assertEquals(0, server.getPlayerCount(), "a player who stops answering mid-match is dropped as before");
	}
}
