package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests the approved point-estimate mastery threshold and permanent unlocking rule.
 */
public class LevelStateTest {

    /** Verifies both inclusive advancement boundaries unlock confidence intervals. */
    @Test
    public void afterSession_atAnswerAndPointThreshold_advancesLevel() {
        LevelState initial = LevelState.initial();

        LevelState advanced = initial.afterSession(pointSession(10, 70));

        assertEquals(Level.CONFIDENCE_INTERVALS, advanced.getHighestUnlockedLevel());
        assertTrue(advanced.isUnlocked(Level.POINT_ESTIMATES));
        assertTrue(advanced.isUnlocked(Level.CONFIDENCE_INTERVALS));
    }

    /** Verifies a session below the score threshold cannot advance the level. */
    @Test
    public void afterSession_belowPointThreshold_doesNotAdvanceLevel() {
        LevelState initial = LevelState.initial();

        LevelState unchanged = initial.afterSession(pointSession(10, 69));

        assertSame(initial, unchanged);
        assertEquals(Level.POINT_ESTIMATES, unchanged.getHighestUnlockedLevel());
        assertFalse(unchanged.isUnlocked(Level.CONFIDENCE_INTERVALS));
    }

    /** Verifies a high average based on too few answers cannot advance the level. */
    @Test
    public void afterSession_belowAnswerThreshold_doesNotAdvanceLevel() {
        LevelState initial = LevelState.initial();

        LevelState unchanged = initial.afterSession(pointSession(9, 100));

        assertSame(initial, unchanged);
    }

    /** Verifies later weak performance never revokes a permanent unlock. */
    @Test
    public void afterSession_afterUnlock_neverDropsBack() {
        LevelState advanced = LevelState.initial().afterSession(pointSession(10, 70));

        LevelState afterWeakSession = advanced.afterSession(pointSession(10, 0));

        assertSame(advanced, afterWeakSession);
        assertEquals(Level.CONFIDENCE_INTERVALS, afterWeakSession.getHighestUnlockedLevel());
    }

    private static SessionResult pointSession(int answerCount, int pointsPerAnswer) {
        List<Score> scores = new ArrayList<>();
        for (int index = 0; index < answerCount; index++) {
            scores.add(new Score(0.1, pointsPerAnswer));
        }
        return new SessionResult(Level.POINT_ESTIMATES, scores);
    }
}
