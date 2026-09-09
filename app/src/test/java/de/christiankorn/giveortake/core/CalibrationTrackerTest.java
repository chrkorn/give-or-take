package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.util.OptionalDouble;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests empirical interval calibration and width tracking with hand-constructed outcomes.
 */
public class CalibrationTrackerTest {
    private static final double PRECISE_COMPARISON = 1.0e-12;
    private static final double ORDINARY_LOG_SCALE_WIDTH = 0.25;

    /** Verifies that ten hits produce complete coverage and a negative signed gap. */
    @Test
    public void statistics_forTenHits_reportCompleteCoverage() {
        CalibrationTracker tracker = trackerWithOutcomes(10, 0, ORDINARY_LOG_SCALE_WIDTH);

        assertEquals(10L, tracker.getSampleSize());
        assertEquals(1.0, valueOf(tracker.getEmpiricalCoverage()), PRECISE_COMPARISON);
        assertEquals(-0.10,
                valueOf(tracker.getNominalMinusEmpiricalCoverageGap()), PRECISE_COMPARISON);
        assertFalse(tracker.hasMeaningfulSampleSize());
    }

    /** Verifies that nine hits in ten exactly match the nominal 90 percent coverage. */
    @Test
    public void statistics_forNineHitsInTen_reportZeroCoverageGap() {
        CalibrationTracker tracker = trackerWithOutcomes(9, 1, ORDINARY_LOG_SCALE_WIDTH);

        assertEquals(0.90, valueOf(tracker.getEmpiricalCoverage()), PRECISE_COMPARISON);
        assertEquals(0.0,
                valueOf(tracker.getNominalMinusEmpiricalCoverageGap()), PRECISE_COMPARISON);
    }

    /** Verifies that five hits in ten produce an unambiguously positive overconfidence gap. */
    @Test
    public void statistics_forFiveHitsInTen_reportLargePositiveCoverageGap() {
        CalibrationTracker tracker = trackerWithOutcomes(5, 5, ORDINARY_LOG_SCALE_WIDTH);

        assertEquals(0.50, valueOf(tracker.getEmpiricalCoverage()), PRECISE_COMPARISON);
        assertEquals(0.40,
                valueOf(tracker.getNominalMinusEmpiricalCoverageGap()), PRECISE_COMPARISON);
    }

    /**
     * Verifies that perfect coverage from enormous intervals remains visibly uninformative.
     */
    @Test
    public void statistics_forTenEnormousHits_exposeEnormousMeanWidth() {
        double enormousLogScaleWidth = 12.0;
        CalibrationTracker tracker = trackerWithOutcomes(10, 0, enormousLogScaleWidth);

        assertEquals(1.0, valueOf(tracker.getEmpiricalCoverage()), PRECISE_COMPARISON);
        assertEquals(enormousLogScaleWidth,
                valueOf(tracker.getMeanLogScaleWidth()), PRECISE_COMPARISON);
    }

    /** Verifies that an empty tracker exposes absence and never divides by zero. */
    @Test
    public void statistics_forEmptyTracker_areAbsentAndNotMeaningful() {
        CalibrationTracker tracker = new CalibrationTracker();

        assertEquals(0L, tracker.getSampleSize());
        assertEquals(CalibrationTracker.NOMINAL_COVERAGE,
                tracker.getNominalCoverage(), PRECISE_COMPARISON);
        assertFalse(tracker.getEmpiricalCoverage().isPresent());
        assertFalse(tracker.getNominalMinusEmpiricalCoverageGap().isPresent());
        assertFalse(tracker.getMeanLogScaleWidth().isPresent());
        assertFalse(tracker.hasMeaningfulSampleSize());
    }

    /** Verifies that one answer is calculated correctly but not presented as meaningful. */
    @Test
    public void statistics_forOneAnswer_areAvailableButNotMeaningful() {
        CalibrationTracker tracker = new CalibrationTracker();

        tracker.recordOutcome(false, 0.5);

        assertEquals(1L, tracker.getSampleSize());
        assertEquals(0.0, valueOf(tracker.getEmpiricalCoverage()), PRECISE_COMPARISON);
        assertEquals(0.90,
                valueOf(tracker.getNominalMinusEmpiricalCoverageGap()), PRECISE_COMPARISON);
        assertEquals(0.5, valueOf(tracker.getMeanLogScaleWidth()), PRECISE_COMPARISON);
        assertFalse(tracker.hasMeaningfulSampleSize());
    }

    /** Verifies that the meaningful-sample rule changes state exactly at 50 outcomes. */
    @Test
    public void meaningfulSampleSize_atDocumentedThreshold_becomesTrue() {
        CalibrationTracker tracker = trackerWithOutcomes(49, 0, ORDINARY_LOG_SCALE_WIDTH);

        assertFalse(tracker.hasMeaningfulSampleSize());
        tracker.recordOutcome(true, ORDINARY_LOG_SCALE_WIDTH);
        assertTrue(tracker.hasMeaningfulSampleSize());
    }

    /** Verifies that malformed widths cannot corrupt the accumulated width statistic. */
    @Test
    public void recordOutcome_withNegativeOrNonFiniteWidth_throwsUsefulException() {
        double[] invalidWidths = {
                -0.1,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidWidth : invalidWidths) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new CalibrationTracker().recordOutcome(true, invalidWidth)
            );

            assertEquals("logScaleWidth must be finite and non-negative", exception.getMessage());
        }
    }

    private static CalibrationTracker trackerWithOutcomes(
            int hits,
            int misses,
            double logScaleWidth
    ) {
        CalibrationTracker tracker = new CalibrationTracker();
        for (int i = 0; i < hits; i++) {
            tracker.recordOutcome(true, logScaleWidth);
        }
        for (int i = 0; i < misses; i++) {
            tracker.recordOutcome(false, logScaleWidth);
        }
        return tracker;
    }

    private static double valueOf(OptionalDouble value) {
        assertTrue(value.isPresent());
        return value.getAsDouble();
    }
}
