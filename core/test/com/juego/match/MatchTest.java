package com.juego.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatchTest {
	private final Match match = new Match(new Match.Settings(60_000, 5, 10_000));

	@Test
	void endsWhenTimeRunsOutThenRestartsAfterTheIntermission() {
		match.start(1000);
		assertEquals(Match.Transition.NONE, match.update(60_999, 4));
		assertEquals(1, match.remainingMs(60_999));
		assertEquals(Match.Transition.ENDED, match.update(61_000, 4));
		assertEquals(Match.Phase.INTERMISSION, match.getPhase());
		assertEquals(Match.EndReason.TIME, match.getEndReason());
		assertEquals(10_000, match.remainingMs(61_000));
		assertEquals(Match.Transition.NONE, match.update(70_999, 99), "frag limit doesn't apply during intermission");
		assertEquals(Match.Transition.RESTARTED, match.update(71_000, 0));
		assertTrue(match.isPlaying());
		assertEquals(60_000, match.remainingMs(71_000));
	}

	@Test
	void endsEarlyOnFragLimit() {
		match.start(0);
		assertEquals(Match.Transition.ENDED, match.update(5_000, 5));
		assertEquals(Match.EndReason.FRAG_LIMIT, match.getEndReason());
	}

	@Test
	void noFragLimitMeansOnlyTimeMatters() {
		Match timed = new Match(new Match.Settings(1000, 0, 500));
		timed.start(0);
		assertEquals(Match.Transition.NONE, timed.update(999, 1_000_000));
		assertEquals(Match.Transition.ENDED, timed.update(1000, 0));
	}

	@Test
	void clockFormatting() {
		assertEquals("10:00", Match.formatClock(600_000));
		assertEquals("0:01", Match.formatClock(1));
		assertEquals("0:00", Match.formatClock(0));
		assertEquals("1:05", Match.formatClock(64_001));
	}
}
