package com.juego.core;

import java.util.function.LongSupplier;

/**
 * Milliseconds for a match, that can stand still. Online it is the wall
 * clock. In singleplayer the server inside the game and its client share one,
 * and the pause menu stops it: the match clock, the bots, respawns and the
 * network timeouts all freeze together, and carry on where they were.
 */
public class GameClock implements LongSupplier {
	private long offset;
	private long pausedAt = -1;

	@Override
	public long getAsLong() {
		return pausedAt >= 0 ? pausedAt : System.currentTimeMillis() - offset;
	}

	public void setPaused(boolean paused) {
		if (paused && pausedAt < 0) {
			pausedAt = getAsLong();
		} else if (!paused && pausedAt >= 0) {
			offset = System.currentTimeMillis() - pausedAt;
			pausedAt = -1;
		}
	}

	public boolean isPaused() {
		return pausedAt >= 0;
	}
}
