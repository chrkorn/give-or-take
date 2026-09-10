package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests length-independent aggregation of individual policy scores.
 */
public class SessionResultTest {
    private static final double PRECISE_COMPARISON = 1.0e-12;

    /** Verifies arithmetic means across answers with different errors and point awards. */
    @Test
    public void constructor_withMixedPointScores_aggregatesArithmeticMeans() {
        SessionResult result = new SessionResult(
                Level.POINT_ESTIMATES,
                Arrays.asList(
                        new Score(0.1, 100),
                        new Score(0.3, 70),
                        new Score(0.5, 40)
                )
        );

        assertEquals(3, result.getAnsweredQuestionCount());
        assertEquals(0.3, result.getMeanRawError().getAsDouble(), PRECISE_COMPARISON);
        assertEquals(70.0, result.getMeanPoints().getAsDouble(), PRECISE_COMPARISON);
    }

    /** Verifies interval losses are averaged without manufacturing a deferred points mapping. */
    @Test
    public void constructor_withMixedIntervalLosses_aggregatesRawMeanOnly() {
        SessionResult result = new SessionResult(
                Level.CONFIDENCE_INTERVALS,
                Arrays.asList(new Score(0.2), new Score(0.8), new Score(0.5))
        );

        assertEquals(3, result.getAnsweredQuestionCount());
        assertEquals(0.5, result.getMeanRawError().getAsDouble(), PRECISE_COMPARISON);
        assertFalse(result.getMeanPoints().isPresent());
    }

    /** Verifies a session without answers has no aggregate instead of a fabricated zero. */
    @Test
    public void constructor_withNoScores_exposesEmptyAverages() {
        SessionResult result = new SessionResult(
                Level.POINT_ESTIMATES,
                Collections.emptyList()
        );

        assertEquals(0, result.getAnsweredQuestionCount());
        assertFalse(result.getMeanRawError().isPresent());
        assertFalse(result.getMeanPoints().isPresent());
    }

    /** Verifies unlike scoring representations cannot accidentally be mixed within one level. */
    @Test
    public void constructor_withScoreFromAnotherLevel_rejectsMismatchedRepresentation() {
        IllegalArgumentException pointException = assertThrows(
                IllegalArgumentException.class,
                () -> new SessionResult(
                        Level.POINT_ESTIMATES,
                        Collections.singletonList(new Score(0.2))
                )
        );
        IllegalArgumentException intervalException = assertThrows(
                IllegalArgumentException.class,
                () -> new SessionResult(
                        Level.CONFIDENCE_INTERVALS,
                        Collections.singletonList(new Score(0.2, 80))
                )
        );

        assertEquals("point-estimate scores must include points", pointException.getMessage());
        assertEquals(
                "confidence-interval scores must not include points",
                intervalException.getMessage()
        );
        assertTrue(pointException.getMessage().contains("points"));
    }
}
