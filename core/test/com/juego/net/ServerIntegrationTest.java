package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import com.juego.utils.CharacterShape;

/**
 * End to end over real UDP sockets on localhost: a real GameServer (with the
 * real Bank level for hit detection) and real GameClients.
 */
class ServerIntegrationTest {
	private static final File ASSETS = new File("assets");
	// Two spawn points with a clear line of sight between them (found by raycasting the level).
	private static final Vector3 SPAWN_A = new Vector3(3, 4, 2);
	private static final Vector3 SPAWN_B = new Vector3(-6, 4, -4);

	@TempDir
	static Path tmp;
	private GameServer server;
	private final List<GameClient> clients = new ArrayList<>();
	private final List<GameClient.Event> eventsA = new ArrayList<>(), eventsB = new ArrayList<>();

	@BeforeAll
	static void initBullet() throws IOException {
		Bullet.init();
		Files.writeString(tmp.resolve("Test.json"), "{model: maps/Bank.g3dj, spawnPoints: [[" + SPAWN_A.x + ","
				+ SPAWN_A.y + "," + SPAWN_A.z + "],[" + SPAWN_B.x + "," + SPAWN_B.y + "," + SPAWN_B.z + "]]}");
	}

	@BeforeEach
	void startServer() throws IOException {
		startServer(new GameServer.Config());
	}

	private void startServer(GameServer.Config config) throws IOException {
		config.port = 0; // any free port
		config.maxPlayers = 2;
		config.map = "Test";
		Armory armory = Armory.load(new FileHandle(new File(ASSETS, Armory.GUNS_FILE)));
		server = new GameServer(config, path -> path.equals("data/maps/Test.json")
				? new FileHandle(tmp.resolve("Test.json").toFile())
				: new FileHandle(new File(ASSETS, path)), armory, System::currentTimeMillis);
	}

	@AfterEach
	void stop() {
		for (GameClient c : clients)
			c.dispose();
		server.dispose();
	}

	private GameClient connect(String name) throws IOException {
		GameClient client = new GameClient(System::currentTimeMillis);
		client.connect("127.0.0.1", server.getPort(), name);
		clients.add(client);
		return client;
	}

	/** Runs server and clients like a game loop until the condition holds. */
	private void pumpUntil(String what, long timeoutMs, BooleanSupplier condition) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			server.update();
			for (int i = 0; i < clients.size(); i++) {
				GameClient c = clients.get(i);
				c.update();
				GameClient.Event e;
				while ((e = c.pollEvent()) != null)
					(i == 0 ? eventsA : eventsB).add(e);
			}
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

	private void pumpFor(long ms) {
		long end = System.currentTimeMillis() + ms;
		pumpUntil("pumping", ms + 1000, () -> System.currentTimeMillis() >= end);
	}

	private GameClient.Event find(List<GameClient.Event> events, GameClient.Event.Type type) {
		for (GameClient.Event e : events)
			if (e.type == type)
				return e;
		return null;
	}

	@Test
	void twoPlayersJoinShootAndRespawn() throws IOException {
		GameClient a = connect("Ana");
		pumpUntil("A connected", 3000, () -> a.getStatus() == GameClient.Status.CONNECTED);
		assertEquals(SPAWN_A, a.getSpawn(), "first player gets the first spawn");

		GameClient b = connect("Beto");
		pumpUntil("both see each other", 3000, () -> b.getStatus() == GameClient.Status.CONNECTED
				&& a.getPlayer(b.getLocalId()) != null && b.getPlayer(a.getLocalId()) != null);
		assertEquals(SPAWN_B, b.getSpawn(), "second player spawns away from the first");
		assertEquals("Beto", a.getPlayer(b.getLocalId()).name);
		assertEquals("Ana", b.getPlayer(a.getLocalId()).name);

		// Body shot: normal damage, seen by both clients through snapshots.
		Vector3 bodyDirection = new Vector3(SPAWN_B).add(0, -1, 0).sub(SPAWN_A);
		a.sendFire(0, SPAWN_A, bodyDirection);
		pumpUntil("B damaged", 2000, () -> b.getPlayer(b.getLocalId()).health == 100 - 34
				&& a.getPlayer(b.getLocalId()).health == 100 - 34);

		// Shooting faster than the gun's fire rate is ignored by the server.
		a.sendFire(0, SPAWN_A, bodyDirection);
		pumpFor(300);
		assertEquals(100 - 34, a.getPlayer(b.getLocalId()).health, "second shot came too fast");

		// Headshot: x3 damage kills.
		sleep(200);
		a.sendFire(0, SPAWN_A, new Vector3(SPAWN_B).add(0, 2.5f, 0).sub(SPAWN_A));
		pumpUntil("kill event", 2000, () -> find(eventsB, GameClient.Event.Type.KILL) != null);
		GameClient.Event kill = find(eventsB, GameClient.Event.Type.KILL);
		assertEquals(a.getLocalId(), kill.killerId);
		assertEquals(b.getLocalId(), kill.playerId);
		assertTrue(kill.headshot);
		assertEquals("M4A1", kill.gun);
		pumpUntil("scores", 2000, () -> a.getPlayer(a.getLocalId()).kills == 1 && a.getPlayer(b.getLocalId()).deaths == 1);
		assertTrue(!b.getPlayer(b.getLocalId()).alive, "victim is dead until respawn");

		// Respawn after the delay, at a spawn point.
		long killTime = System.currentTimeMillis();
		pumpUntil("respawn", Protocol.RESPAWN_DELAY_MS + 2000, () -> find(eventsB, GameClient.Event.Type.RESPAWN) != null);
		assertTrue(System.currentTimeMillis() - killTime >= Protocol.RESPAWN_DELAY_MS - 100);
		GameClient.Event respawn = find(eventsB, GameClient.Event.Type.RESPAWN);
		assertEquals(b.getLocalId(), respawn.playerId);
		assertTrue(respawn.position.epsilonEquals(SPAWN_A) || respawn.position.epsilonEquals(SPAWN_B));
		assertTrue(b.getPlayer(b.getLocalId()).alive);
		assertEquals(100, b.getPlayer(b.getLocalId()).health);
	}

	@Test
	void matchEndsAtFragLimitAndRestartsWithFreshScores() throws IOException {
		server.dispose();
		GameServer.Config config = new GameServer.Config();
		config.fragLimit = 1;
		config.intermissionMs = 800;
		startServer(config);

		GameClient a = connect("Ana");
		pumpUntil("A in", 3000, () -> a.getStatus() == GameClient.Status.CONNECTED);
		GameClient b = connect("Beto");
		pumpUntil("B in", 3000, () -> b.getStatus() == GameClient.Status.CONNECTED
				&& b.getPlayer(a.getLocalId()) != null);
		assertEquals(com.juego.match.Match.Phase.PLAYING, a.getMatchPhase());
		pumpUntil("first snapshot", 1000, () -> a.getMatchRemainingMs() > 0);
		long remaining = a.getMatchRemainingMs();
		assertTrue(remaining > 9 * 60_000 && remaining <= 10 * 60_000, "10 minute clock, got " + remaining);

		a.sendFire(0, SPAWN_A, new Vector3(SPAWN_B).add(0, 2.5f, 0).sub(SPAWN_A));
		pumpUntil("match end", 2000, () -> find(eventsA, GameClient.Event.Type.MATCH_END) != null
				&& find(eventsB, GameClient.Event.Type.MATCH_END) != null);
		GameClient.Event end = find(eventsB, GameClient.Event.Type.MATCH_END);
		assertEquals(a.getLocalId(), end.playerId, "A wins");
		assertEquals(com.juego.match.Match.EndReason.FRAG_LIMIT.ordinal(), end.detail);
		GameClient.Event shot = find(eventsB, GameClient.Event.Type.SHOT);
		assertNotNull(shot, "the victim heard the shot");
		assertEquals(a.getLocalId(), shot.playerId);
		assertTrue(find(eventsA, GameClient.Event.Type.SHOT) == null, "shooters don't get their own SHOT");
		pumpUntil("intermission seen", 1000, () -> b.getMatchPhase() == com.juego.match.Match.Phase.INTERMISSION);

		// Shots during the intermission don't count.
		sleep(200);
		eventsB.clear();
		a.sendFire(0, SPAWN_A, new Vector3(SPAWN_B).add(0, -1, 0).sub(SPAWN_A));
		pumpFor(150);
		assertTrue(find(eventsB, GameClient.Event.Type.SHOT) == null, "fire ignored during intermission");

		pumpUntil("new match", 3000, () -> find(eventsB, GameClient.Event.Type.MATCH_START) != null
				&& b.getPlayer(a.getLocalId()).kills == 0 && b.getPlayer(b.getLocalId()).deaths == 0);
		assertTrue(b.getPlayer(b.getLocalId()).alive);
		assertNotNull(find(eventsB, GameClient.Event.Type.RESPAWN), "everyone respawns for the new match");
		pumpUntil("A sees the new match too", 1000, () -> a.getMatchPhase() == com.juego.match.Match.Phase.PLAYING);
	}

	/** Regression: moved hitboxes must be found by ray tests (their broadphase AABB has to follow them). */
	@Test
	void shotsHitPlayersWhereTheyMovedTo() throws IOException {
		GameClient a = connect("A");
		pumpUntil("A in", 3000, () -> a.getStatus() == GameClient.Status.CONNECTED);
		GameClient b = connect("B");
		pumpUntil("B in", 3000, () -> b.getStatus() == GameClient.Status.CONNECTED);

		// B walks 4 units sideways at 8 units/s, sending its state like the game does.
		Vector3 position = new Vector3(SPAWN_B);
		Vector3 destination = new Vector3(SPAWN_B).add(0, 0, 4);
		long last = System.currentTimeMillis();
		while (!position.epsilonEquals(destination, 0.01f)) {
			pumpFor(35);
			long now = System.currentTimeMillis();
			Vector3 step = new Vector3(destination).sub(position).limit(8f * (now - last) / 1000f);
			last = now;
			position.add(step);
			b.sendState(position, 0, 0, 0);
		}
		pumpFor(150);
		assertTrue(find(eventsB, GameClient.Event.Type.SET_POSITION) == null, "legit movement was not corrected");

		a.sendFire(0, SPAWN_A, new Vector3(destination).add(0, 2.5f, 0).sub(SPAWN_A));
		pumpUntil("B killed at its new position", 2000, () -> find(eventsB, GameClient.Event.Type.KILL) != null);
	}

	@Test
	void serverRejectsWhenFullAndAnnouncesLeavers() throws IOException {
		GameClient a = connect("A");
		GameClient b = connect("B");
		pumpUntil("both in", 3000, () -> a.getStatus() == GameClient.Status.CONNECTED
				&& b.getStatus() == GameClient.Status.CONNECTED);
		GameClient c = connect("C");
		pumpUntil("C rejected", 3000, () -> c.getStatus() == GameClient.Status.REJECTED);
		assertTrue(c.getFailureReason().contains("lleno"), c.getFailureReason());

		int bId = b.getLocalId();
		b.disconnect();
		pumpUntil("A sees B leave", 3000, () -> find(eventsA, GameClient.Event.Type.LEFT) != null);
		assertEquals(bId, find(eventsA, GameClient.Event.Type.LEFT).playerId);
		assertEquals(1, server.getPlayerCount());
		assertNotNull(a.getPlayer(a.getLocalId()));
	}

	@Test
	void serverCorrectsImpossibleMovement() throws IOException {
		GameClient a = connect("A");
		pumpUntil("connected", 3000, () -> a.getStatus() == GameClient.Status.CONNECTED);
		sleep(50);
		a.sendState(new Vector3(SPAWN_A).add(60, 0, 0), 0, 0, 0); // 60 units in a few ms
		pumpUntil("correction", 2000, () -> find(eventsA, GameClient.Event.Type.SET_POSITION) != null);
		assertEquals(SPAWN_A, find(eventsA, GameClient.Event.Type.SET_POSITION).position);
	}

	@Test
	void playersDropAndPickUpGuns() throws IOException {
		GameClient a = connect("Ana");
		pumpUntil("A in", 3000, () -> a.getStatus() == GameClient.Status.CONNECTED);
		GameClient b = connect("Beto");
		pumpUntil("B in", 3000, () -> b.getStatus() == GameClient.Status.CONNECTED
				&& b.getPlayer(a.getLocalId()) != null);

		// A throws its rifle, with some ammo spent: everyone sees it land in front of A.
		a.sendDrop(0, 12, 40);
		pumpUntil("rifle thrown", 2000, () -> find(eventsA, GameClient.Event.Type.WEAPON_SPAWN) != null
				&& find(eventsB, GameClient.Event.Type.WEAPON_SPAWN) != null);
		GameClient.Event rifle = find(eventsB, GameClient.Event.Type.WEAPON_SPAWN);
		assertEquals("m4a1", rifle.gun);
		assertEquals(a.getLocalId(), rifle.playerId);
		assertEquals(12, rifle.magazine);
		assertEquals(40, rifle.reserve);
		assertTrue(rifle.position.y < SPAWN_A.y - 2, "on the floor, got " + rifle.position);
		assertTrue(Vector3.dst(rifle.position.x, 0, rifle.position.z, SPAWN_A.x, 0, SPAWN_A.z)
				<= com.juego.guns.GroundWeapons.THROW_DISTANCE + 0.1f, "in front of A, got " + rifle.position);
		assertTrue(!rifle.from.epsilonEquals(rifle.position, 0.01f), "thrown, so clients animate it");

		// The last gun can't be dropped, and an empty slot can't shoot.
		eventsA.clear();
		eventsB.clear();
		Vector3 bodyDirection = new Vector3(SPAWN_B).add(0, -1, 0).sub(SPAWN_A);
		a.sendDrop(1, 6, -1);
		a.sendFire(0, SPAWN_A, bodyDirection);
		pumpFor(300);
		assertNull(find(eventsB, GameClient.Event.Type.WEAPON_SPAWN), "kept the last gun");
		assertNull(find(eventsB, GameClient.Event.Type.SHOT), "no rifle anymore");

		// The magnum works: B is hurt, told where the shot came from, and sees where the bullet went.
		a.sendFire(1, SPAWN_A, bodyDirection);
		pumpUntil("B hurt", 2000, () -> find(eventsB, GameClient.Event.Type.DAMAGE) != null
				&& find(eventsB, GameClient.Event.Type.SHOT) != null);
		GameClient.Event damage = find(eventsB, GameClient.Event.Type.DAMAGE);
		assertEquals(40, damage.detail, "magnum damage");
		assertEquals(SPAWN_A, damage.position);
		GameClient.Event shot = find(eventsB, GameClient.Event.Type.SHOT);
		assertEquals("magnum", shot.gun);
		assertEquals(SPAWN_A, shot.from);
		assertTrue(shot.position.dst(SPAWN_B) < CharacterShape.HALF_HEIGHT + 1, "bullet stopped at B: " + shot.position);

		// Too far away to take it.
		b.sendPickup(rifle.weaponId, 30, 70);
		pumpFor(300);
		assertNull(find(eventsA, GameClient.Event.Type.WEAPON_REMOVE), "B is across the room");

		// B walks over and swaps its own rifle for A's.
		walk(b, SPAWN_B, new Vector3(rifle.position.x, SPAWN_B.y, rifle.position.z));
		eventsA.clear();
		eventsB.clear();
		b.sendPickup(rifle.weaponId, 30, 70);
		pumpUntil("swap", 2000, () -> find(eventsA, GameClient.Event.Type.WEAPON_REMOVE) != null);
		GameClient.Event taken = find(eventsA, GameClient.Event.Type.WEAPON_REMOVE);
		assertEquals(rifle.weaponId, taken.weaponId);
		assertEquals(b.getLocalId(), taken.playerId);
		GameClient.Event bRifle = find(eventsA, GameClient.Event.Type.WEAPON_SPAWN);
		assertNotNull(bRifle, "B's own rifle took its place on the ground");
		assertEquals(b.getLocalId(), bRifle.playerId);
		assertEquals(30, bRifle.magazine);

		// Someone joining later finds it lying there, without a throw animation.
		b.disconnect();
		pumpUntil("B gone", 3000, () -> server.getPlayerCount() == 1);
		eventsB.clear();
		GameClient c = connect("Carla");
		pumpUntil("C sees the ground", 3000, () -> find(eventsB, GameClient.Event.Type.WEAPON_SPAWN) != null);
		GameClient.Event lying = find(eventsB, GameClient.Event.Type.WEAPON_SPAWN);
		assertEquals(bRifle.weaponId, lying.weaponId);
		assertTrue(lying.from.epsilonEquals(lying.position, 0.01f));
		assertEquals(GameClient.Status.CONNECTED, c.getStatus());
	}

	/** Moves a client at a legal speed, sending its state like the game does. */
	private void walk(GameClient client, Vector3 from, Vector3 to) {
		Vector3 position = new Vector3(from);
		long last = System.currentTimeMillis();
		while (!position.epsilonEquals(to, 0.01f)) {
			pumpFor(35);
			long now = System.currentTimeMillis();
			position.add(new Vector3(to).sub(position).limit(8f * (now - last) / 1000f));
			last = now;
			client.sendState(position, 0, 0, 0);
		}
		pumpFor(100);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
