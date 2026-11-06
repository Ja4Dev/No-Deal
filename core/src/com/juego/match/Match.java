package com.juego.match;

/**
 * Timing and win conditions of a match: play until the time runs out or
 * someone reaches the frag limit, show the results during an intermission,
 * then start over. Pure logic driven by whatever clock the caller uses (the
 * wall clock, or a singleplayer clock that stops while paused), so it is easy
 * to test.
 */
public class Match {
	public enum Phase {
		PLAYING, INTERMISSION
	}

	public enum Transition {
		NONE, ENDED, RESTARTED
	}

	public enum EndReason {
		TIME, FRAG_LIMIT
	}

	public static class Settings {
		public long durationMs;
		/** 0 = no frag limit. */
		public int fragLimit;
		public long intermissionMs;

		public Settings(long durationMs, int fragLimit, long intermissionMs) {
			this.durationMs = durationMs;
			this.fragLimit = fragLimit;
			this.intermissionMs = intermissionMs;
		}
	}

	public final Settings settings;
	private Phase phase = Phase.PLAYING;
	private EndReason endReason = EndReason.TIME;
	private long phaseEndsAtMs;

	public Match(Settings settings) {
		this.settings = settings;
	}

	public void start(long nowMs) {
		phase = Phase.PLAYING;
		phaseEndsAtMs = nowMs + settings.durationMs;
	}

	/**
	 * Advances the match.
	 *
	 * @param topKills the best score among the players right now
	 */
	public Transition update(long nowMs, int topKills) {
		if (phase == Phase.PLAYING) {
			boolean fragLimit = settings.fragLimit > 0 && topKills >= settings.fragLimit;
			if (fragLimit || nowMs >= phaseEndsAtMs) {
				phase = Phase.INTERMISSION;
				endReason = fragLimit ? EndReason.FRAG_LIMIT : EndReason.TIME;
				phaseEndsAtMs = nowMs + settings.intermissionMs;
				return Transition.ENDED;
			}
		} else if (nowMs >= phaseEndsAtMs) {
			start(nowMs);
			return Transition.RESTARTED;
		}
		return Transition.NONE;
	}

	public Phase getPhase() {
		return phase;
	}

	public boolean isPlaying() {
		return phase == Phase.PLAYING;
	}

	public EndReason getEndReason() {
		return endReason;
	}

	/** Time left in the current phase (match time, or time until the next match). */
	public long remainingMs(long nowMs) {
		return Math.max(0, phaseEndsAtMs - nowMs);
	}

	/** "mm:ss", rounding up so the clock reads 0:00 only when time is really up. */
	public static String formatClock(long ms) {
		long seconds = (ms + 999) / 1000;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
