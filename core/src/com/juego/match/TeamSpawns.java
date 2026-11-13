package com.juego.match;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Where each team spawns in a team match. A map can say it ("teamSpawns":
 * {"red": [...], "blue": [...]}); otherwise its spawn points are split in two
 * halves across its longest side, so each team starts at its own end.
 */
public final class TeamSpawns {
	public final Array<Vector3> red = new Array<>(), blue = new Array<>();

	public Array<Vector3> of(Team team) {
		return team == Team.BLUE ? blue : red;
	}

	/**
	 * @param map    the map's data (data/maps/*.json)
	 * @param spawns its spawn points, for the split
	 */
	public static TeamSpawns load(JsonValue map, Array<Vector3> spawns) {
		TeamSpawns out = new TeamSpawns();
		JsonValue teams = map.get("teamSpawns");
		if (teams != null && teams.get("red") != null && teams.get("blue") != null) {
			read(teams.get("red"), out.red);
			read(teams.get("blue"), out.blue);
			if (out.red.size > 0 && out.blue.size > 0)
				return out;
			out.red.clear();
			out.blue.clear();
		}
		return split(spawns);
	}

	private static void read(JsonValue points, Array<Vector3> out) {
		for (JsonValue p : points)
			out.add(new Vector3(p.getFloat(0), p.getFloat(1), p.getFloat(2)));
	}

	/** Half the points (the lower ones along the map's longest side) for Red, the rest for Blue. */
	public static TeamSpawns split(Array<Vector3> spawns) {
		TeamSpawns out = new TeamSpawns();
		if (spawns.size == 1) {
			out.red.add(spawns.first());
			out.blue.add(spawns.first());
			return out;
		}
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (Vector3 p : spawns) {
			minX = Math.min(minX, p.x);
			maxX = Math.max(maxX, p.x);
			minZ = Math.min(minZ, p.z);
			maxZ = Math.max(maxZ, p.z);
		}
		final boolean alongX = maxX - minX >= maxZ - minZ;
		Array<Vector3> sorted = new Array<>(spawns);
		sorted.sort((a, b) -> Float.compare(alongX ? a.x : a.z, alongX ? b.x : b.z));
		int half = sorted.size / 2;
		for (int i = 0; i < sorted.size; i++)
			(i < half ? out.red : out.blue).add(sorted.get(i));
		return out;
	}
}
