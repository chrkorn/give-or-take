package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/**
 * Tests immutable, level-specific personal-best comparisons.
 */
public class HighScoreTest {
    private static final double PRECISE_COMPARISON = 1.0e-12;

    /** Verifies point records update only for a strictly higher mean. */
    @Test
    public void afterSession_withPointScores_updatesOnlyWhenStrictlyBeaten() {
        HighScore firstRecord = HighScore.empty(Level.POINT_ESTIMATES)
                .afterSession(pointSession(70, 80));

        HighScore beatenRecord = firstRecord.afterSession(pointSession(90, 90));
        HighScore afterWorseSession = beatenRecord.afterSession(pointSession(60, 70));

        assertEquals(90.0, beatenRecord.getBestValue().getAsDouble(), PRECISE_COMPARISON);
        assertEquals(2, beatenRecord.getAnsweredQuestionCount().getAsInt());
        assertSame(beatenRecord, afterWorseSession);
    }

    /** Verifies an exact tie retains the earlier record, including its sample size. */
    @Test
    public void afterSession_withExactTie_doesNotReplaceExistingRecord() {
        HighScore existing = HighScore.empty(Level.POINT_ESTIMATES)
                .afterSession(pointSession(80));

        HighScore afterTie = existing.afterSession(pointSession(70, 90));

        assertSame(existing, afterTie);
        assertEquals(1, afterTie.getAnsweredQuestionCount().getAsInt());
    }

    /** Verifies interval records recognise that a lower mean raw loss is better. */
    @Test
    public void afterSession_withIntervalLosses_updatesOnlyForLowerMean() {
        HighScore firstRecord = HighScore.empty(Level.CONFIDENCE_INTERVALS)
                .afterSession(intervalSession(0.4, 0.6));

        HighScore beatenRecord = firstRecord.afterSession(intervalSession(0.2, 0.4));
        HighScore afterWorseSession = beatenRecord.afterSession(intervalSession(0.8));

        assertEquals(0.3, beatenRecord.getBestValue().getAsDouble(), PRECISE_COMPARISON);
        assertSame(beatenRecord, afterWorseSession);
    }

    /** Verifies an empty session cannot establish an artificial zero-valued record. */
    @Test
    public void afterSession_withNoAnswers_doesNotSetRecord() {
        HighScore empty = HighScore.empty(Level.POINT_ESTIMATES);

        HighScore unchanged = empty.afterSession(
                new SessionResult(Level.POINT_ESTIMATES, Collections.emptyList())
        );

        assertSame(empty, unchanged);
        assertFalse(unchanged.getBestValue().isPresent());
        assertFalse(unchanged.getAnsweredQuestionCount().isPresent());
    }

    /** Verifies a record cannot compare sessions governed by another scoring policy. */
    @Test
    public void afterSession_fromDifferentLevel_rejectsComparison() {
        HighScore pointHighScore = HighScore.empty(Level.POINT_ESTIMATES);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointHighScore.afterSession(intervalSession(0.2))
        );

        assertEquals("sessionResult must belong to the same level", exception.getMessage());
    }

    private static SessionResult pointSession(int... points) {
        Score[] scores = new Score[points.length];
        for (int index = 0; index < points.length; index++) {
            scores[index] = new Score(0.1, points[index]);
        }
        return new SessionResult(Level.POINT_ESTIMATES, Arrays.asList(scores));
    }

    private static SessionResult intervalSession(double... losses) {
        Score[] scores = new Score[losses.length];
        for (int index = 0; index < losses.length; index++) {
            scores[index] = new Score(losses[index]);
        }
        return new SessionResult(Level.CONFIDENCE_INTERVALS, Arrays.asList(scores));
    }
}
