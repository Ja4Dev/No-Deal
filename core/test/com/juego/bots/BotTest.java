package com.juego.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.juego.guns.Armory;
import com.juego.guns.GunDefinition;
import com.juego.net.MapCollision;
import com.juego.utils.CharacterShape;

/** Bots on the real Bank level: navigation, chasing and shooting, without a server. */
class BotTest {
	private static final File ASSETS = new File("assets");
	private static MapCollision map;
	private static BotWorld world;
	private static NavGrid nav;
	private static GunDefinition rifle;
	private static final Array<Vector3> spawns = new Array<>();

	@BeforeAll
	static void buildLevel() {
		Bullet.init();
		JsonValue data = new JsonReader().parse(new FileHandle(new File(ASSETS, "data/maps/Bank.json")));
		for (JsonValue p : data.get("spawnPoints"))
			spawns.add(new Vector3(p.getFloat(0), p.getFloat(1), p.getFloat(2)));
		map = new MapCollision(new FileHandle(new File(ASSETS, data.getString("model"))));
		world = new BotWorld(map.shape);
		long start = System.currentTimeMillis();
		nav = new NavGrid(world, spawns);
		System.out.println("NavGrid: " + nav.usableCount() + " usable of " + nav.nodeCount() + " nodes in "
				+ (System.currentTimeMillis() - start) + " ms");
		rifle = Armory.load(new FileHandle(new File(ASSETS, Armory.GUNS_FILE))).get("m4a1");
	}

	@AfterAll
	static void dispose() {
		world.dispose();
		map.dispose();
	}

	@Test
	void theWholeLevelIsWalkableFromEverySpawn() {
		assertTrue(nav.usableCount() > 1000, "found " + nav.usableCount() + " walkable cells");
		Array<Vector3> path = new Array<>();
		for (int i = 0; i < spawns.size; i++)
			for (int j = 0; j < spawns.size; j++)
				assertTrue(nav.findPath(spawns.get(i), spawns.get(j), path),
						"no path from " + spawns.get(i) + " to " + spawns.get(j));
		nav.findPath(spawns.get(1), spawns.get(4), path);
		assertTrue(path.peek().dst(spawns.get(4)) < 3, "the path ends at the goal: " + path.peek());
	}

	/** Records the bot's shots and whether they would hit a still target. */
	private static class Range implements Bot.Host {
		final Vector3 target;
		int shots, hits, headshots;
		long firstShotMs = -1;
		long now;

		Range(Vector3 target) {
			this.target = target;
		}

		@Override
		public void fire(Bot bot, Vector3 origin, Vector3 direction) {
			shots++;
			if (firstShotMs < 0)
				firstShotMs = now;
			Ray ray = new Ray(origin, direction);
			Vector3 hit = new Vector3();
			for (float y = -CharacterShape.CYLINDER / 2; y <= CharacterShape.CYLINDER / 2; y += 0.25f) {
				if (Intersector.intersectRaySphere(ray, new Vector3(target).add(0, y, 0), CharacterShape.RADIUS, hit)) {
					if (hit.dst(origin) > rifle.range || !world.isClear(origin, hit))
						return; // out of range, or a wall first
					hits++;
					if (hit.y >= target.y + CharacterShape.HEAD_OFFSET)
						headshots++;
					return;
				}
			}
		}
	}

	/** Runs a bot against a target that stands still, for up to {@code seconds}. */
	private static Range fight(BotDifficulty difficulty, Vector3 from, Vector3 target, float seconds, int maxShots,
			long seed) {
		Range range = new Range(target);
		Bot bot = new Bot(1, "BOT Test", difficulty, world, nav, from, new Random(seed));
		// Facing the target (roughly), so the time to the first shot is the reaction, not the search.
		float yaw = (float) Math.toDegrees(Math.atan2(target.x - from.x, target.z - from.z)) + 20;
		bot.respawn(from, rifle, yaw);
		Array<Bot.Target> targets = new Array<>();
		Bot.Target t = new Bot.Target();
		t.id = 2;
		t.alive = true;
		t.position.set(target);
		targets.add(t);
		try {
			for (int step = 0; step < seconds / BotWorld.STEP && range.shots < maxShots; step++) {
				range.now = (long) (step * BotWorld.STEP * 1000);
				bot.think(range.now, BotWorld.STEP, targets, true, range);
				bot.move();
				world.step();
				bot.afterStep();
			}
		} finally {
			bot.dispose();
		}
		return range;
	}

	@Test
	void huntsDownAnEnemyAcrossTheMapAndShootsIt() {
		// Opposite corners of the bank: the bot has to find its way there.
		Range range = fight(BotDifficulty.DIFICIL, spawns.get(1), spawns.get(4), 60, 10, 1);
		assertTrue(range.shots > 0, "it found the target and fired");
		assertTrue(range.hits > 0, "and hit it: " + range.hits + " of " + range.shots);
	}

	@Test
	void harderBotsReactFasterAndHitMore() {
		Vector3[] pair = visiblePair(35, 45);
		Vector3 a = pair[0], b = pair[1];
		int[] hits = new int[BotDifficulty.values().length], headshots = new int[hits.length];
		long[] reaction = new long[hits.length];
		for (BotDifficulty d : BotDifficulty.values()) {
			for (long seed = 0; seed < 8; seed++) {
				Range range = fight(d, a, b, 20, 30, seed);
				hits[d.ordinal()] += range.hits;
				headshots[d.ordinal()] += range.headshots;
				reaction[d.ordinal()] += range.firstShotMs;
			}
			System.out.println(d + ": " + hits[d.ordinal()] + " hits of 240 (" + headshots[d.ordinal()]
					+ " to the head), first shot after "
					+ reaction[d.ordinal()] / 8 + " ms");
		}
		for (int i = 1; i < hits.length; i++) {
			assertTrue(hits[i] + 2 * headshots[i] > hits[i - 1] + 2 * headshots[i - 1],
					"damage must grow with difficulty");
			assertTrue(reaction[i] < reaction[i - 1], "reaction must get faster with difficulty");
		}
		assertTrue(hits[0] < 240 * 0.6f, "easy bots miss a lot at this distance");
		assertTrue(hits[3] > 240 * 0.85f, "very hard ones hardly miss");
		assertTrue(reaction[0] / 8 >= BotDifficulty.FACIL.reactionMs, "easy bots wait their reaction time");
	}

	/** Two walkable places that see each other, between min and max apart. */
	private static Vector3[] visiblePair(float min, float max) {
		Random random = new Random(7);
		Vector3 eyeA = new Vector3(), eyeB = new Vector3();
		for (int attempt = 0; attempt < 100_000; attempt++) {
			Vector3 a = nav.nodePosition(nav.randomNode(random), new Vector3());
			Vector3 b = nav.nodePosition(nav.randomNode(random), new Vector3());
			float d = a.dst(b);
			if (d < min || d > max)
				continue;
			eyeA.set(a).add(0, CharacterShape.EYE_OFFSET, 0);
			eyeB.set(b).add(0, CharacterShape.EYE_OFFSET, 0);
			if (world.isClear(eyeA, eyeB) && world.isClear(eyeA, new Vector3(b).add(0, -1, 0)))
				return new Vector3[] { a, b };
		}
		throw new IllegalStateException("no two places see each other");
	}

	@Test
	void difficultiesAreReadFromCommands() {
		assertEquals(BotDifficulty.FACIL, BotDifficulty.parse("Fácil"));
		assertEquals(BotDifficulty.MEDIO, BotDifficulty.parse("medio"));
		assertEquals(BotDifficulty.DIFICIL, BotDifficulty.parse("DIFICIL"));
		assertEquals(BotDifficulty.MUY_DIFICIL, BotDifficulty.parse("muy difícil"));
		assertEquals(BotDifficulty.MUY_DIFICIL, BotDifficulty.parse("muy_dificil"));
		assertEquals(BotDifficulty.MUY_DIFICIL, BotDifficulty.parse("expert"));
		assertNull(BotDifficulty.parse("imposible"));
		assertNotNull(BotDifficulty.parse(BotDifficulty.MUY_DIFICIL.id()));
	}
}
