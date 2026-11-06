package com.juego.bots;

import java.util.PriorityQueue;
import java.util.Random;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.juego.bullet.CharacterBody;
import com.juego.utils.CharacterShape;

/**
 * Where bots can walk, built automatically from the level's geometry when the
 * server starts (no hand-placed waypoints). The level is sampled on a grid:
 * every upward-facing surface with room for a standing character becomes a
 * node (several per column on levels with floors on top of each other), and
 * nodes next to each other are linked when a character can walk between them.
 * Bots find their way with A* over these nodes, then cut corners where the
 * straight line is clear.
 */
public class NavGrid {
	public static final float CELL = 2f;
	/** Heights above the floor where a character's body must be free of walls. */
	private static final float[] BODY_HEIGHTS = { 0.6f, 3f, 5.4f };
	private static final float HEIGHT = CharacterShape.HALF_HEIGHT * 2;
	/** A little less than the capsule's radius: corridors exactly as wide as a character still count. */
	private static final float CLEARANCE = CharacterShape.RADIUS * 0.95f;
	/** Steepest height change between neighbouring cells (stairs, ramps). */
	private static final float MAX_CLIMB = 1.0f;
	private static final int[][] DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 },
			{ -1, 1 }, { -1, -1 } };

	private final BotWorld world;
	private final float minX, minZ;
	private final int cols, rows;
	/** Node indices in each column, by column index (ix * rows + iz). */
	private final IntArray[] columns;
	private final FloatArray nodeX = new FloatArray(), nodeFloor = new FloatArray(), nodeZ = new FloatArray();
	private final IntArray nodeIx = new IntArray(), nodeIz = new IntArray();
	private final Array<IntArray> links = new Array<>();
	/** Whether a node is reachable from the spawn points (roofs and closed rooms are not). */
	private boolean[] usable;
	private int usableCount;

	// A* scratch, reused between searches.
	private float[] cost;
	private int[] cameFrom, visited;
	private int search;

	private final Vector3 a = new Vector3(), b = new Vector3(), tmp = new Vector3();

	/**
	 * Samples the level. Takes a fraction of a second on a typical level.
	 *
	 * @param anchors points that must be reachable (the spawn points): only the
	 *                parts of the level connected to them are kept
	 */
	public NavGrid(BotWorld world, Array<Vector3> anchors) {
		this.world = world;
		Vector3[] bounds = world.levelBounds();
		minX = bounds[0].x;
		minZ = bounds[0].z;
		cols = Math.max(1, (int) Math.ceil((bounds[1].x - minX) / CELL));
		rows = Math.max(1, (int) Math.ceil((bounds[1].z - minZ) / CELL));
		columns = new IntArray[cols * rows];
		float top = bounds[1].y + 1, bottom = bounds[0].y - 1;
		FloatArray floors = new FloatArray();
		for (int ix = 0; ix < cols; ix++) {
			for (int iz = 0; iz < rows; iz++) {
				float x = minX + (ix + 0.5f) * CELL, z = minZ + (iz + 0.5f) * CELL;
				world.floorsBelow(x, z, top, bottom, floors);
				for (int i = 0; i < floors.size; i++) {
					if (fits(x, floors.get(i), z))
						addNode(ix, iz, x, floors.get(i), z);
				}
			}
		}
		for (int n = 0; n < nodeX.size; n++)
			linkNeighbours(n);
		keepReachable(anchors);
		cost = new float[nodeX.size];
		cameFrom = new int[nodeX.size];
		visited = new int[nodeX.size];
	}

	private void addNode(int ix, int iz, float x, float floor, float z) {
		int column = ix * rows + iz;
		if (columns[column] == null)
			columns[column] = new IntArray(2);
		columns[column].add(nodeX.size);
		nodeX.add(x);
		nodeFloor.add(floor);
		nodeZ.add(z);
		nodeIx.add(ix);
		nodeIz.add(iz);
		links.add(new IntArray(8));
	}

	/** Whether a standing character fits on the floor at (x, floor, z). */
	private boolean fits(float x, float floor, float z) {
		if (!world.isClear(a.set(x, floor + 0.1f, z), b.set(x, floor + HEIGHT + 0.2f, z)))
			return false;
		for (float h : BODY_HEIGHTS) {
			for (int[] d : DIRECTIONS) {
				float length = d[0] != 0 && d[1] != 0 ? CLEARANCE * 0.7071f : CLEARANCE;
				a.set(x, floor + h, z);
				b.set(x + d[0] * length, floor + h, z + d[1] * length);
				if (!world.isClear(a, b))
					return false;
			}
		}
		return true;
	}

	private void linkNeighbours(int n) {
		int ix = nodeIx.get(n), iz = nodeIz.get(n);
		for (int[] d : DIRECTIONS) {
			int m = nodeAt(ix + d[0], iz + d[1], nodeFloor.get(n));
			if (m < 0 || m < n && links.get(m).contains(n))
				continue;
			if (d[0] != 0 && d[1] != 0) {
				// Diagonal: only if both straight neighbours exist too (no cutting corners).
				if (nodeAt(ix + d[0], iz, nodeFloor.get(n)) < 0 || nodeAt(ix, iz + d[1], nodeFloor.get(n)) < 0)
					continue;
			}
			if (canWalk(n, m)) {
				links.get(n).add(m);
				if (!links.get(m).contains(n))
					links.get(m).add(n);
			}
		}
	}

	/** The node in column (ix, iz) whose floor is closest to {@code floor}, within a climb; -1 if none. */
	private int nodeAt(int ix, int iz, float floor) {
		if (ix < 0 || iz < 0 || ix >= cols || iz >= rows)
			return -1;
		IntArray column = columns[ix * rows + iz];
		if (column == null)
			return -1;
		int best = -1;
		float bestDy = MAX_CLIMB;
		for (int i = 0; i < column.size; i++) {
			float dy = Math.abs(nodeFloor.get(column.get(i)) - floor);
			if (dy <= bestDy) {
				bestDy = dy;
				best = column.get(i);
			}
		}
		return best;
	}

	private boolean canWalk(int n, int m) {
		return canWalk(nodeX.get(n), nodeFloor.get(n), nodeZ.get(n), nodeX.get(m), nodeFloor.get(m), nodeZ.get(m));
	}

	/** Whether a character can walk in a straight line between two floor points. */
	private boolean canWalk(float x1, float f1, float z1, float x2, float f2, float z2) {
		for (float h : BODY_HEIGHTS) {
			if (!world.isClear(a.set(x1, f1 + h, z1), b.set(x2, f2 + h, z2)))
				return false;
		}
		// Wide enough: the same at both sides of the body.
		float dx = x2 - x1, dz = z2 - z1, length = (float) Math.sqrt(dx * dx + dz * dz);
		if (length > 0.01f) {
			float sx = -dz / length * CLEARANCE, sz = dx / length * CLEARANCE;
			for (int side = -1; side <= 1; side += 2) {
				if (!world.isClear(a.set(x1 + sx * side, f1 + 3f, z1 + sz * side),
						b.set(x2 + sx * side, f2 + 3f, z2 + sz * side)))
					return false;
			}
		}
		// No hole along the way: there must be floor every cell or so.
		int samples = Math.max(1, (int) (length / CELL));
		for (int i = 1; i <= samples; i++) {
			float t = i / (samples + 1f);
			float x = x1 + dx * t, z = z1 + dz * t, f = f1 + (f2 - f1) * t;
			if (world.isClear(a.set(x, f + MAX_CLIMB, z), b.set(x, f - MAX_CLIMB - CharacterBody.STEP_HEIGHT, z)))
				return false;
		}
		return true;
	}

	/** Keeps the nodes connected to an anchor; the rest are islands bots must not try to reach. */
	private void keepReachable(Array<Vector3> anchors) {
		usable = new boolean[nodeX.size];
		IntArray queue = new IntArray();
		for (Vector3 anchor : anchors) {
			int start = nearestNode(anchor, false);
			if (start < 0 || usable[start])
				continue;
			usable[start] = true;
			queue.add(start);
			while (queue.size > 0) {
				int n = queue.pop();
				usableCount++;
				IntArray l = links.get(n);
				for (int i = 0; i < l.size; i++) {
					if (!usable[l.get(i)]) {
						usable[l.get(i)] = true;
						queue.add(l.get(i));
					}
				}
			}
		}
	}

	public int nodeCount() {
		return nodeX.size;
	}

	/** Nodes bots can reach. */
	public int usableCount() {
		return usableCount;
	}

	/** Where a character standing on node n has its center. */
	public Vector3 nodePosition(int n, Vector3 out) {
		return out.set(nodeX.get(n), nodeFloor.get(n) + CharacterShape.HALF_HEIGHT, nodeZ.get(n));
	}

	/** The reachable node nearest to a character centered at {@code position}; -1 if none is close. */
	public int nearestNode(Vector3 position) {
		return nearestNode(position, true);
	}

	private int nearestNode(Vector3 position, boolean onlyUsable) {
		int cx = (int) Math.floor((position.x - minX) / CELL), cz = (int) Math.floor((position.z - minZ) / CELL);
		int best = -1;
		float bestDistance = Float.MAX_VALUE;
		for (int radius = 0; radius <= 4 && best < 0; radius++) {
			for (int ix = cx - radius; ix <= cx + radius; ix++) {
				for (int iz = cz - radius; iz <= cz + radius; iz++) {
					if (ix < 0 || iz < 0 || ix >= cols || iz >= rows || columns[ix * rows + iz] == null)
						continue;
					IntArray column = columns[ix * rows + iz];
					for (int i = 0; i < column.size; i++) {
						int n = column.get(i);
						if (onlyUsable && !usable[n])
							continue;
						float d = nodePosition(n, tmp).dst2(position);
						if (d < bestDistance) {
							bestDistance = d;
							best = n;
						}
					}
				}
			}
		}
		return best;
	}

	/** A random reachable node, e.g. somewhere to roam to; -1 if the grid is empty. */
	public int randomNode(Random random) {
		if (usableCount == 0)
			return -1;
		int skip = random.nextInt(usableCount);
		for (int n = 0; n < usable.length; n++) {
			if (usable[n] && skip-- == 0)
				return n;
		}
		return -1;
	}

	/**
	 * A path for a character centered at {@code from} to reach {@code to}, as
	 * character-center waypoints (the first one is the next place to go, the
	 * last one is near {@code to}). Corners are cut where the straight line is
	 * clear.
	 *
	 * @return false if there is no way there
	 */
	public boolean findPath(Vector3 from, Vector3 to, Array<Vector3> out) {
		out.clear();
		int start = nearestNode(from), goal = nearestNode(to);
		if (start < 0 || goal < 0)
			return false;
		if (!aStar(start, goal))
			return false;
		IntArray nodes = new IntArray();
		for (int n = goal; n != start; n = cameFrom[n])
			nodes.add(n);
		nodes.add(start);
		nodes.reverse();
		// String pulling: from where we are, skip every node we can walk to in a straight line.
		float fx = from.x, ff = from.y - CharacterShape.HALF_HEIGHT, fz = from.z;
		int i = 0;
		while (i < nodes.size) {
			int furthest = i;
			for (int j = Math.min(nodes.size - 1, i + 12); j > i; j--) {
				int n = nodes.get(j);
				if (canWalk(fx, ff, fz, nodeX.get(n), nodeFloor.get(n), nodeZ.get(n))) {
					furthest = j;
					break;
				}
			}
			int n = nodes.get(furthest);
			out.add(nodePosition(n, new Vector3()));
			fx = nodeX.get(n);
			ff = nodeFloor.get(n);
			fz = nodeZ.get(n);
			i = furthest + 1;
		}
		return true;
	}

	private boolean aStar(int start, int goal) {
		search++;
		final float[] f = new float[1];
		PriorityQueue<long[]> open = new PriorityQueue<>((p, q) -> Float.compare(Float.intBitsToFloat((int) p[0]),
				Float.intBitsToFloat((int) q[0])));
		cost[start] = 0;
		visited[start] = search;
		cameFrom[start] = start;
		open.add(new long[] { Float.floatToIntBits(heuristic(start, goal)), start });
		while (!open.isEmpty()) {
			long[] entry = open.poll();
			int n = (int) entry[1];
			f[0] = Float.intBitsToFloat((int) entry[0]);
			if (n == goal)
				return true;
			if (f[0] > cost[n] + heuristic(n, goal) + 1e-3f)
				continue; // a stale entry: n was reached more cheaply since
			IntArray l = links.get(n);
			for (int i = 0; i < l.size; i++) {
				int m = l.get(i);
				float c = cost[n] + distance(n, m);
				if (visited[m] != search || c < cost[m]) {
					visited[m] = search;
					cost[m] = c;
					cameFrom[m] = n;
					open.add(new long[] { Float.floatToIntBits(c + heuristic(m, goal)), m });
				}
			}
		}
		return false;
	}

	private float distance(int n, int m) {
		float dx = nodeX.get(n) - nodeX.get(m), dy = nodeFloor.get(n) - nodeFloor.get(m), dz = nodeZ.get(n) - nodeZ.get(m);
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private float heuristic(int n, int goal) {
		return distance(n, goal);
	}
}
