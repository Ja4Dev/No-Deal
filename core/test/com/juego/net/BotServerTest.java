package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.juego.bots.BotDifficulty;
import com.juego.guns.Armory;
import com.juego.utils.Text;

/**
 * Bots in a real server, seen by a real client over UDP. The match runs on a
 * fake clock that advances 16 ms per tick, so a minute of play takes a few
 * seconds.
 */
class BotServerTest {
	private static final File ASSETS = new File("assets");
	private final AtomicLong clock = new AtomicLong(1_000_000);
	private GameServer server;
	private GameClient client;
	private final List<GameClient.Event> events = new ArrayList<>();

	@BeforeAll
	static void initBullet() {
		Bullet.init();
		Text.setLanguage(Text.Language.ES); // the replies checked here are the Spanish ones
	}

	@BeforeEach
	void start() throws IOException {
		GameServer.Config config = new GameServer.Config();
		config.port = 0;
		config.localOnly = true;
		Armory armory = Armory.load(new FileHandle(new File(ASSETS, Armory.GUNS_FILE)));
		server = new GameServer(config, path -> new FileHandle(new File(ASSETS, path)), armory, clock::get);
		client = new GameClient(clock::get);
		client.connect("127.0.0.1", server.getPort(), "Vos");
		pumpUntil("connected", 200, () -> client.getStatus() == GameClient.Status.CONNECTED);
	}

	@AfterEach
	void stop() {
		client.dispose();
		server.dispose();
	}

	/** Ticks server and client (16 ms of match time per tick) until the condition holds. */
	private void pumpUntil(String what, int maxTicks, BooleanSupplier condition) {
		for (int tick = 0; tick < maxTicks; tick++) {
			clock.addAndGet(16);
			server.update();
			sleepBriefly(); // let the datagrams cross the loopback
			client.update();
			if (client.getStatus() == GameClient.Status.CONNECTED) // standing at the spawn, like a player afk
				client.sendState(client.getSpawn(), 0, 0, 0, 30, 70);
			GameClient.Event e;
			while ((e = client.pollEvent()) != null)
				events.add(e);
			if (condition.getAsBoolean())
				return;
		}
		fail("timed out waiting for: " + what);
	}

	private static void sleepBriefly() {
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private long count(GameClient.Event.Type type) {
		return events.stream().filter(e -> e.type == type).count();
	}

	@Test
	void botsAddedByCommandHuntAndKillAPlayer() {
		String reply = server.command("bot agregar 2 muy dificil");
		assertTrue(reply.startsWith("Entraron BOT "), reply);
		assertEquals(2, server.getBotCount());
		assertEquals(1, server.getPlayerCount(), "bots don't take people's places");
		pumpUntil("the client sees both bots", 200, () -> client.getPlayers().size == 3);
		int botId = -1;
		for (GameClient.PlayerInfo p : client.getPlayers())
			if (p.id != client.getLocalId()) {
				assertTrue(p.name.startsWith("BOT "), p.name);
				botId = p.id;
			}

		// The bots move (snapshots show them somewhere else a few seconds later)...
		Vector3 before = new Vector3(), after = new Vector3();
		float[] yaw = new float[1];
		int id = botId;
		pumpUntil("a snapshot", 100, () -> client.getPlayer(id).interpolate(clock.get(), before, yaw));
		pumpUntil("the bot walked", 600, () -> client.getPlayer(id).interpolate(clock.get(), after, yaw)
				&& after.dst(before) > 5);

		// ...and find and kill us: we stand still at the spawn, never shooting back.
		pumpUntil("killed by a bot", 60 * 60, () -> events.stream().anyMatch(e -> e.type == GameClient.Event.Type.KILL
				&& e.playerId == client.getLocalId() && e.killerId != client.getLocalId()));
		assertTrue(count(GameClient.Event.Type.SHOT) > 0, "we heard their shots");
		assertTrue(count(GameClient.Event.Type.DAMAGE) > 0, "and felt them");
		assertTrue(events.stream().anyMatch(e -> e.type == GameClient.Event.Type.HIT
				&& e.playerId == client.getLocalId() && e.from.isUnit(0.01f)), "every hit is announced, with its direction");
	}

	@Test
	void commandsChangeAndRemoveBots() {
		assertTrue(server.command("bot_add facil").startsWith("Entro BOT "));
		assertTrue(server.command("bot agregar 3").contains("(Medio)"), "new bots use the server's difficulty");
		assertEquals(4, server.getBotCount());
		assertTrue(server.command("bot agregar imposible").startsWith("No conozco la dificultad"));
		assertEquals(4, server.getBotCount());

		assertTrue(server.command("bot dificultad muy dificil").contains("Muy dificil"));
		assertEquals(BotDifficulty.MUY_DIFICIL, server.getBotDifficulty());
		String list = server.command("bots");
		assertTrue(list.startsWith("4 bots"), list);
		assertEquals(4, list.split("bot \\(Muy dificil\\)", -1).length - 1, "every bot changed: " + list);

		String name = null;
		for (GameServer.PlayerInfo p : server.getPlayers())
			if (p.bot)
				name = p.name;
		String shortName = name.substring(4).toLowerCase(); // "BOT Pedro" -> "pedro"
		assertEquals("Salio " + name + ".", server.command("bot quitar " + shortName));
		assertEquals(3, server.getBotCount());
		assertTrue(server.command("bot quitar todos").startsWith("Salio "));
		assertEquals(0, server.getBotCount());
		assertEquals("No hay bots.", server.command("bot_kick"));
		pumpUntil("the client saw them come and go", 200,
				() -> count(GameClient.Event.Type.LEFT) == 4 && client.getPlayers().size == 1);

		assertTrue(server.command("estado").startsWith("1/"));
		assertNull(server.command("salir"), "not a match command: the console handles it");
		assertTrue(server.command("bot bailar").contains("bot agregar"), "unknown sub-commands show the help");
	}

	@Test
	void aFullServerStillTakesBotsButNotMoreThanTheLimit() {
		String reply = server.command("bot agregar 20");
		assertEquals(GameServer.MAX_BOTS, server.getBotCount());
		assertTrue(reply.endsWith("No entran mas bots (maximo " + GameServer.MAX_BOTS + ", y "
				+ Protocol.MAX_MATCH_PLAYERS + " en total)."), reply);
	}
}
