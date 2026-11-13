package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.badlogic.gdx.physics.bullet.Bullet;
import com.juego.guns.Armory;
import com.juego.match.GameMode;
import com.juego.match.Team;
import com.juego.utils.Text;

/** Bots in a team match on the Bank, on a fake clock (16 ms per tick), seen by a real client. */
class TeamBotTest {
	private static final File ASSETS = new File("assets");
	private final AtomicLong clock = new AtomicLong(1_000_000);
	private GameServer server;
	private GameClient client;
	private final List<GameClient.Event> events = new ArrayList<>();

	@BeforeAll
	static void initBullet() {
		Bullet.init();
		Text.setLanguage(Text.Language.ES);
	}

	@BeforeEach
	void start() throws IOException {
		GameServer.Config config = new GameServer.Config();
		config.port = 0;
		config.localOnly = true;
		config.mode = GameMode.TEAM_DEATHMATCH;
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

	private void pumpUntil(String what, int maxTicks, BooleanSupplier condition) {
		for (int tick = 0; tick < maxTicks; tick++) {
			clock.addAndGet(16);
			server.update();
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			client.update();
			if (client.getStatus() == GameClient.Status.CONNECTED)
				client.sendState(client.getSpawn(), 0, 0, 0, 30, 70);
			GameClient.Event e;
			while ((e = client.pollEvent()) != null)
				events.add(e);
			if (condition.getAsBoolean())
				return;
		}
		fail("timed out waiting for: " + what);
	}

	private int count(Team team) {
		int n = 0;
		for (GameClient.PlayerInfo p : client.getPlayers())
			if (p.team == team)
				n++;
		return n;
	}

	@Test
	void botsEvenTheTeamsOutAndScoreForThem() {
		pumpUntil("our team", 100, () -> client.getLocalTeam().isTeam());
		assertEquals(Team.RED, client.getLocalTeam());
		assertTrue(server.command("bot agregar 4 azul").startsWith("Entraron"));
		// Four for Blue against us alone: one of them changes sides, 2 against 3.
		pumpUntil("even", 300, () -> client.getPlayers().size == 5 && count(Team.RED) == 2 && count(Team.BLUE) == 3);
	}

	@Test
	void teamScoresAreTheTeamsKills() {
		server.command("bot agregar 3 muy dificil");
		pumpUntil("some kills", 6000, () -> client.getTeamScore(Team.RED) + client.getTeamScore(Team.BLUE) >= 3);
		pumpUntil("snapshot", 10, () -> true);
		for (Team team : new Team[] { Team.RED, Team.BLUE }) {
			int kills = 0;
			for (GameClient.PlayerInfo p : client.getPlayers())
				if (p.team == team)
					kills += p.kills;
			assertEquals(kills, client.getTeamScore(team), team + ": its players' kills");
		}
		for (GameClient.Event e : events)
			if (e.type == GameClient.Event.Type.KILL && e.killerId != Protocol.WORLD && e.killerId != e.playerId) {
				GameClient.PlayerInfo killer = client.getPlayer(e.killerId), victim = client.getPlayer(e.playerId);
				if (killer != null && victim != null)
					assertTrue(killer.team != victim.team, "only enemies die: " + killer.name + " / " + victim.name);
			}
	}
}
