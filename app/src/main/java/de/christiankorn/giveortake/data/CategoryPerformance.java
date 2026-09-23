package de.christiankorn.giveortake.data;

import java.util.OptionalDouble;

/** Represents mode-specific historical performance within one snapshotted question category. */
public final class CategoryPerformance {
    private final String category;
    private final int pointSampleSize;
    private final OptionalDouble meanLogRelativeError;
    private final int intervalSampleSize;
    private final int intervalHitCount;
    private final OptionalDouble empiricalCoverage;
    private final OptionalDouble meanIntervalLoss;

    CategoryPerformance(
            String category,
            int pointSampleSize,
            OptionalDouble meanLogRelativeError,
            int intervalSampleSize,
            int intervalHitCount,
            OptionalDouble empiricalCoverage,
            OptionalDouble meanIntervalLoss
    ) {
        this.category = category;
        this.pointSampleSize = pointSampleSize;
        this.meanLogRelativeError = meanLogRelativeError;
        this.intervalSampleSize = intervalSampleSize;
        this.intervalHitCount = intervalHitCount;
        this.empiricalCoverage = empiricalCoverage;
        this.meanIntervalLoss = meanIntervalLoss;
    }

    /** Returns the category captured at answer time, or the legacy-data label. */
    public String getCategory() {
        return category;
    }

    /** Returns the number of point estimates in this category. */
    public int getPointSampleSize() {
        return pointSampleSize;
    }

    /** Returns mean log-relative error, or an empty value without point estimates. */
    public OptionalDouble getMeanLogRelativeError() {
        return meanLogRelativeError;
    }

    /** Returns the number of confidence intervals in this category. */
    public int getIntervalSampleSize() {
        return intervalSampleSize;
    }

    /** Returns how many category intervals contained the truth. */
    public int getIntervalHitCount() {
        return intervalHitCount;
    }

    /** Returns category coverage, or an empty value without interval answers. */
    public OptionalDouble getEmpiricalCoverage() {
        return empiricalCoverage;
    }

    /** Returns mean proper interval loss, or an empty value without interval answers. */
    public OptionalDouble getMeanIntervalLoss() {
        return meanIntervalLoss;
    }
}
