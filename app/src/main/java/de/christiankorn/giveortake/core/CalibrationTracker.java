package de.christiankorn.giveortake.core;

import java.util.OptionalDouble;

/**
 * Accumulates the empirical calibration of 90 percent confidence intervals.
 *
 * <p>This tracker deliberately keeps two ideas separate. {@link IntervalScore} produces a
 * per-answer loss using the proper scoring rule selected in ADR 0007. That loss rewards both
 * coverage and narrow, informative intervals. This class instead calculates an across-answers
 * calibration statistic: the fraction of intervals that contained the truth, compared with the
 * nominal 90 percent coverage. Coverage alone is not a per-answer score and must not be used as
 * one, because a user could improve it merely by making every interval extremely wide. The mean
 * logarithmic width is therefore retained alongside coverage to make that behaviour visible.</p>
 *
 * <p>Statistics are absent until at least one outcome has been recorded. {@link OptionalDouble}
 * makes that absence explicit instead of manufacturing a value or dividing by zero.</p>
 */
public final class CalibrationTracker {
    /** The requested long-run coverage of every interval recorded by this tracker. */
    public static final double NOMINAL_COVERAGE = 0.90;

    /**
     * The rule-of-thumb sample size at which calibration figures may begin to be interpreted.
     *
     * <p>With 90 percent nominal coverage, 50 answers imply five expected misses. Five expected
     * observations in the less frequent outcome is a conventional minimum for treating a
     * binomial proportion as roughly informative. This is not a derived guarantee or a claim of
     * high precision: near 90 percent, 50 independent answers still have a standard error of
     * about 4.2 percentage points. A confidence interval should accompany stronger claims.</p>
     */
    public static final long MINIMUM_MEANINGFUL_SAMPLE_SIZE = 50L;

    private long sampleSize;
    private long hitCount;
    private double meanLogScaleWidth;

    /**
     * Creates an empty tracker for 90 percent confidence-interval outcomes.
     */
    public CalibrationTracker() {
        // Explicit constructor keeps the public API and its initial state visible in Javadoc.
    }

    /**
     * Records one confidence-interval outcome.
     *
     * @param intervalContainedTruth {@code true} when the interval included the true value,
     *                               including either boundary
     * @param logScaleWidth the interval width on the base-10 logarithmic scale, as returned by
     *                      {@link IntervalGuess#width()}; must be finite and non-negative
     * @throws IllegalArgumentException if {@code logScaleWidth} is non-finite or negative
     */
    public void recordOutcome(boolean intervalContainedTruth, double logScaleWidth) {
        if (!Double.isFinite(logScaleWidth) || logScaleWidth < 0.0) {
            throw new IllegalArgumentException(
                    "logScaleWidth must be finite and non-negative"
            );
        }

        sampleSize++;
        if (intervalContainedTruth) {
            hitCount++;
        }

        // An incremental mean avoids allowing a long-running width total to overflow even when
        // every individual outcome satisfies the finite-width invariant.
        meanLogScaleWidth += (logScaleWidth - meanLogScaleWidth) / sampleSize;
    }

    /**
     * Returns the requested long-run interval coverage.
     *
     * @return {@value #NOMINAL_COVERAGE}
     */
    public double getNominalCoverage() {
        return NOMINAL_COVERAGE;
    }

    /**
     * Returns the observed fraction of intervals that contained the truth.
     *
     * @return the hit count divided by the sample size, or an empty value before any outcome has
     *         been recorded
     */
    public OptionalDouble getEmpiricalCoverage() {
        if (sampleSize == 0L) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) hitCount / sampleSize);
    }

    /**
     * Returns the signed overconfidence gap: nominal coverage minus empirical coverage.
     *
     * <p>A positive value means observed coverage is below 90 percent and indicates
     * overconfidence. Zero means observed coverage matches the nominal target. A negative value
     * means observed coverage is above the target and may indicate underconfidence, especially
     * when accompanied by large mean interval width. Sampling variation remains substantial for
     * small samples, so direction should not be interpreted as a stable trait too early.</p>
     *
     * @return {@code nominal coverage - empirical coverage}, or an empty value before any outcome
     *         has been recorded
     */
    public OptionalDouble getNominalMinusEmpiricalCoverageGap() {
        OptionalDouble empiricalCoverage = getEmpiricalCoverage();
        if (!empiricalCoverage.isPresent()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(NOMINAL_COVERAGE - empiricalCoverage.getAsDouble());
    }

    /**
     * Returns the number of interval outcomes accumulated by this tracker.
     *
     * @return the non-negative sample size
     */
    public long getSampleSize() {
        return sampleSize;
    }

    /**
     * Reports whether the calibration sample has reached the documented rule-of-thumb minimum.
     *
     * <p>This method only describes sample size. It does not prove that answers are independent,
     * representative, or precise enough for a particular decision.</p>
     *
     * @return {@code true} after at least {@value #MINIMUM_MEANINGFUL_SAMPLE_SIZE} outcomes
     */
    public boolean hasMeaningfulSampleSize() {
        return sampleSize >= MINIMUM_MEANINGFUL_SAMPLE_SIZE;
    }

    /**
     * Returns the arithmetic mean of recorded base-10 logarithmic interval widths.
     *
     * <p>A width of {@code 1.0} represents a tenfold span and {@code 2.0} a hundredfold span,
     * independent of the question's unit or scale. Reporting this value beside coverage exposes
     * coverage achieved by consistently broad, uninformative ranges.</p>
     *
     * @return the mean log-scale width, or an empty value before any outcome has been recorded
     */
    public OptionalDouble getMeanLogScaleWidth() {
        if (sampleSize == 0L) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(meanLogScaleWidth);
    }
}
