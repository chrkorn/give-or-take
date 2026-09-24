package de.christiankorn.giveortake.data;

import java.util.List;
import java.util.OptionalDouble;

/** Collects the statistics-screen aggregates calculated from one consistent history snapshot. */
public final class StatisticsOverview {
    private final int endedSessionCount;
    private final int answerCount;
    private final OptionalDouble meanPointLogRelativeError;
    private final CalibrationStatistics calibration;
    private final List<CoverageTrendPoint> coverageTrend;
    private final List<MeanErrorTrendPoint> meanErrorTrend;
    private final List<CategoryPerformance> categoryPerformance;
    private final List<PersonalBestStatistics> personalBests;

    StatisticsOverview(
            int endedSessionCount,
            int answerCount,
            OptionalDouble meanPointLogRelativeError,
            CalibrationStatistics calibration,
            List<CoverageTrendPoint> coverageTrend,
            List<MeanErrorTrendPoint> meanErrorTrend,
            List<CategoryPerformance> categoryPerformance,
            List<PersonalBestStatistics> personalBests
    ) {
        this.endedSessionCount = endedSessionCount;
        this.answerCount = answerCount;
        this.meanPointLogRelativeError = meanPointLogRelativeError;
        this.calibration = calibration;
        this.coverageTrend = coverageTrend;
        this.meanErrorTrend = meanErrorTrend;
        this.categoryPerformance = categoryPerformance;
        this.personalBests = personalBests;
    }

    /** Returns the number of completed or abandoned sessions represented. */
    public int getEndedSessionCount() {
        return endedSessionCount;
    }

    /** Returns the number of accepted answers represented. */
    public int getAnswerCount() {
        return answerCount;
    }

    /**
     * Returns the mean log-relative error across every stored point estimate.
     *
     * <p>The aggregate is weighted per answer rather than per session, so a short abandoned
     * session does not count as much as a longer session merely because both have one mean.</p>
     *
     * @return mean point-estimate error, or an empty value when no point answer exists
     */
    public OptionalDouble getMeanPointLogRelativeError() {
        return meanPointLogRelativeError;
    }

    /** Returns overall confidence-interval calibration. */
    public CalibrationStatistics getCalibration() {
        return calibration;
    }

    /** Returns immutable rolling ten-answer coverage points in chronological order. */
    public List<CoverageTrendPoint> getCoverageTrend() {
        return coverageTrend;
    }

    /** Returns immutable per-session mean-error points in chronological order. */
    public List<MeanErrorTrendPoint> getMeanErrorTrend() {
        return meanErrorTrend;
    }

    /** Returns immutable category aggregates ordered by category name. */
    public List<CategoryPerformance> getCategoryPerformance() {
        return categoryPerformance;
    }

    /** Returns immutable mode-specific records for modes with a completed non-empty session. */
    public List<PersonalBestStatistics> getPersonalBests() {
        return personalBests;
    }
}
