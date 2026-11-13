package com.juego.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;

class TeamSpawnsTest {
	private static Array<Vector3> points(float[][] xyz) {
		Array<Vector3> out = new Array<>();
		for (float[] p : xyz)
			out.add(new Vector3(p[0], p[1], p[2]));
		return out;
	}

	@Test
	void eachTeamStartsAtItsEndOfTheMap() {
		// The Bank's spawn points: a long map from west to east.
		Array<Vector3> bank = points(new float[][] { { 3, 4, 2 }, { -78, 4, -67 }, { -78, 4, 68 }, { 75, 4, -67 },
				{ 75, 4, 68 }, { -6, 4, -67 }, { -66, 4, -1 }, { -9, 4, 68 } });
		TeamSpawns spawns = TeamSpawns.split(bank);
		assertEquals(4, spawns.red.size);
		assertEquals(4, spawns.blue.size);
		float redMax = -Float.MAX_VALUE, blueMin = Float.MAX_VALUE;
		for (Vector3 p : spawns.red)
			redMax = Math.max(redMax, p.x);
		for (Vector3 p : spawns.blue)
			blueMin = Math.min(blueMin, p.x);
		assertTrue(redMax <= blueMin, "Red west, Blue east");
	}

	@Test
	void splitsAlongTheLongestSide() {
		TeamSpawns spawns = TeamSpawns.split(points(new float[][] { { 0, 0, 50 }, { 1, 0, -50 }, { 2, 0, 40 }, { 3, 0, -40 } }));
		for (Vector3 p : spawns.red)
			assertTrue(p.z < 0, "split north-south: " + p);
	}

	@Test
	void aMapCanSayWhereEachTeamStarts() {
		Array<Vector3> all = points(new float[][] { { 0, 0, 0 }, { 10, 0, 0 } });
		TeamSpawns spawns = TeamSpawns.load(new JsonReader().parse(
				"{teamSpawns: {red: [[1,2,3]], blue: [[4,5,6],[7,8,9]]}}"), all);
		assertEquals(new Vector3(1, 2, 3), spawns.of(Team.RED).first());
		assertEquals(2, spawns.of(Team.BLUE).size);
		assertEquals(1, TeamSpawns.load(new JsonReader().parse("{}"), all).red.size, "otherwise split");
		TeamSpawns one = TeamSpawns.split(points(new float[][] { { 5, 0, 5 } }));
		assertEquals(one.red.first(), one.blue.first(), "a single point serves both");
	}

	@Test
	void namesAndModesAreUnderstood() {
		assertEquals(Team.RED, Team.parse("Rojo"));
		assertEquals(Team.BLUE, Team.parse("blue"));
		assertEquals(Team.NONE, Team.parse("auto"));
		assertEquals(null, Team.parse("verde"));
		assertEquals(Team.BLUE, Team.RED.other());
		assertEquals(GameMode.TEAM_DEATHMATCH, GameMode.parse("tdm"));
		assertEquals(GameMode.TEAM_DEATHMATCH, GameMode.parse("TeamDeathmatch"));
		assertEquals(GameMode.DEATHMATCH, GameMode.parse("dm"));
		assertEquals(null, GameMode.parse("capture"));
	}
}
