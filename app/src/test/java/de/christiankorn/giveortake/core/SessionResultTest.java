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
        SessionResult result = SessionResult.forPointEstimates(
                Arrays.asList(
                        new Score(0.1, 100),
                        new Score(0.3, 70),
                        new Score(0.5, 40)
                ),
                new CorrectnessClassifier()
        );

        assertEquals(3, result.getAnsweredQuestionCount());
        assertEquals(0.3, result.getMeanRawError().getAsDouble(), PRECISE_COMPARISON);
        assertEquals(70.0, result.getMeanPoints().getAsDouble(), PRECISE_COMPARISON);
        assertEquals(1, result.getCorrectCount());
        assertEquals(1, result.getCloseCount());
        assertEquals(1, result.getWrongCount());
    }

    /** Verifies interval losses are averaged without manufacturing a deferred points mapping. */
    @Test
    public void constructor_withMixedIntervalLosses_aggregatesRawMeanOnly() {
        SessionResult result = SessionResult.forConfidenceIntervals(
                Arrays.asList(new Score(0.2), new Score(0.8), new Score(0.5)),
                trackerWithOutcomes(true, false, true)
        );

        assertEquals(3, result.getAnsweredQuestionCount());
        assertEquals(0.5, result.getMeanRawError().getAsDouble(), PRECISE_COMPARISON);
        assertFalse(result.getMeanPoints().isPresent());
        assertEquals(2L, result.getCalibrationHitCount());
        assertEquals(3L, result.getCalibrationSampleSize());
        assertFalse(result.hasMeaningfulCalibrationSampleSize());
    }

    /** Verifies a session without answers has no aggregate instead of a fabricated zero. */
    @Test
    public void constructor_withNoScores_exposesEmptyAverages() {
        SessionResult result = SessionResult.forPointEstimates(
                Collections.emptyList(),
                new CorrectnessClassifier()
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
                () -> SessionResult.forPointEstimates(
                        Collections.singletonList(new Score(0.2)),
                        new CorrectnessClassifier()
                )
        );
        IllegalArgumentException intervalException = assertThrows(
                IllegalArgumentException.class,
                () -> SessionResult.forConfidenceIntervals(
                        Collections.singletonList(new Score(0.2, 80)),
                        trackerWithOutcomes(true)
                )
        );

        assertEquals("point-estimate scores must include points", pointException.getMessage());
        assertEquals(
                "confidence-interval scores must not include points",
                intervalException.getMessage()
        );
        assertTrue(pointException.getMessage().contains("points"));
    }

    /** Verifies that calibration cannot silently describe a different set of answers. */
    @Test
    public void intervalFactory_withMismatchedCalibrationSample_rejectsResult() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SessionResult.forConfidenceIntervals(
                        Arrays.asList(new Score(0.2), new Score(0.3)),
                        trackerWithOutcomes(true)
                )
        );

        assertEquals(
                "calibrationTracker must contain one outcome for every score",
                exception.getMessage()
        );
    }

    private static CalibrationTracker trackerWithOutcomes(boolean... hits) {
        CalibrationTracker tracker = new CalibrationTracker();
        for (boolean hit : hits) {
            tracker.recordOutcome(hit, 1.0);
        }
        return tracker;
    }
}
