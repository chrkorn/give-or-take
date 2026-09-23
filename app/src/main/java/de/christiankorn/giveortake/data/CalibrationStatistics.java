package de.christiankorn.giveortake.data;

import java.util.OptionalDouble;

import de.christiankorn.giveortake.core.CalibrationTracker;

/** Represents overall empirical coverage and interval width across stored answers. */
public final class CalibrationStatistics {
    private final long sampleSize;
    private final long hitCount;
    private final OptionalDouble empiricalCoverage;
    private final OptionalDouble nominalCoverageGap;
    private final OptionalDouble meanLogScaleWidth;
    private final boolean meaningfulSampleSize;

    CalibrationStatistics(CalibrationTracker tracker) {
        sampleSize = tracker.getSampleSize();
        hitCount = tracker.getHitCount();
        empiricalCoverage = tracker.getEmpiricalCoverage();
        nominalCoverageGap = tracker.getNominalMinusEmpiricalCoverageGap();
        meanLogScaleWidth = tracker.getMeanLogScaleWidth();
        meaningfulSampleSize = tracker.hasMeaningfulSampleSize();
    }

    /** Returns the fixed target coverage for the app's confidence intervals. */
    public double getNominalCoverage() {
        return CalibrationTracker.NOMINAL_COVERAGE;
    }

    /** Returns the number of stored confidence intervals. */
    public long getSampleSize() {
        return sampleSize;
    }

    /** Returns how many stored intervals contained the scoring-time truth value. */
    public long getHitCount() {
        return hitCount;
    }

    /** Returns observed coverage, or an empty value when no interval answer exists. */
    public OptionalDouble getEmpiricalCoverage() {
        return empiricalCoverage;
    }

    /** Returns nominal minus observed coverage, or an empty value without interval answers. */
    public OptionalDouble getNominalCoverageGap() {
        return nominalCoverageGap;
    }

    /** Returns mean base-10 logarithmic interval width, or an empty value without intervals. */
    public OptionalDouble getMeanLogScaleWidth() {
        return meanLogScaleWidth;
    }

    /** Reports whether the sample meets the core tracker's interpretation threshold. */
    public boolean hasMeaningfulSampleSize() {
        return meaningfulSampleSize;
    }
}
