package de.christiankorn.giveortake.core;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Aggregates the scores from one completed or abandoned quiz session.
 *
 * <p>Arithmetic means make sessions comparable when their answer counts differ. This matters
 * because remedial repeats can extend a session. A sum would reward merely answering more
 * questions. The answer count remains visible so callers can distinguish equally valued means
 * based on very different sample sizes.</p>
 *
 * <p>Point-estimate sessions expose both mean raw error and mean points. Confidence-interval
 * sessions expose mean raw loss only because ADR 0007 deliberately deferred a points mapping.
 * Empty sessions expose neither mean, avoiding a fabricated score and division by zero.</p>
 */
public final class SessionResult {
    private final Level level;
    private final int answeredQuestionCount;
    private final double meanRawError;
    private final double meanPoints;
    private final int correctCount;
    private final int closeCount;
    private final int wrongCount;
    private final long calibrationHitCount;
    private final long calibrationSampleSize;
    private final boolean meaningfulCalibrationSampleSize;

    private SessionResult(
            Level level,
            List<Score> scores,
            CorrectnessClassifier correctnessClassifier,
            CalibrationTracker calibrationTracker
    ) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (scores == null) {
            throw new IllegalArgumentException("scores must not be null");
        }

        this.level = level;
        answeredQuestionCount = scores.size();

        double runningRawMean = 0.0;
        double runningPointsMean = 0.0;
        int runningCorrectCount = 0;
        int runningCloseCount = 0;
        int runningWrongCount = 0;
        for (int index = 0; index < scores.size(); index++) {
            Score score = scores.get(index);
            if (score == null) {
                throw new IllegalArgumentException("scores must not contain null");
            }
            validatePointsAvailability(level, score);

            int sampleSize = index + 1;
            runningRawMean += (score.getRawError() - runningRawMean) / sampleSize;
            if (score.hasPoints()) {
                runningPointsMean += (score.getPoints() - runningPointsMean) / sampleSize;
            }

            if (correctnessClassifier != null) {
                Correctness correctness = correctnessClassifier.classify(score.getRawError());
                switch (correctness) {
                    case CORRECT:
                        runningCorrectCount++;
                        break;
                    case CLOSE:
                        runningCloseCount++;
                        break;
                    case WRONG:
                        runningWrongCount++;
                        break;
                    default:
                        throw new IllegalStateException(
                                "Unsupported correctness band: " + correctness
                        );
                }
            }
        }

        meanRawError = runningRawMean;
        meanPoints = runningPointsMean;
        correctCount = runningCorrectCount;
        closeCount = runningCloseCount;
        wrongCount = runningWrongCount;
        if (calibrationTracker == null) {
            calibrationHitCount = 0L;
            calibrationSampleSize = 0L;
            meaningfulCalibrationSampleSize = false;
        } else {
            calibrationHitCount = calibrationTracker.getHitCount();
            calibrationSampleSize = calibrationTracker.getSampleSize();
            meaningfulCalibrationSampleSize = calibrationTracker.hasMeaningfulSampleSize();
        }
    }

    /**
     * Aggregates a completed or abandoned point-estimate session.
     *
     * <p>The shared classifier supplies the same correctness-band policy used for feedback and
     * remedial scheduling. Reclassifying the retained raw errors here prevents the Activity from
     * duplicating threshold arithmetic.</p>
     *
     * @param scores the point scores produced for each answered question
     * @param correctnessClassifier classifier used to count the three result bands
     * @return an immutable point-estimate result
     * @throws IllegalArgumentException if an argument is {@code null}, a list item is
     *                                  {@code null}, or a score has no points mapping
     */
    public static SessionResult forPointEstimates(
            List<Score> scores,
            CorrectnessClassifier correctnessClassifier
    ) {
        if (correctnessClassifier == null) {
            throw new IllegalArgumentException("correctnessClassifier must not be null");
        }
        return new SessionResult(
                Level.POINT_ESTIMATES,
                scores,
                correctnessClassifier,
                null
        );
    }

    /**
     * Aggregates a completed or abandoned confidence-interval session.
     *
     * @param scores the interval losses produced for each answered question
     * @param calibrationTracker tracker containing one outcome for every supplied score
     * @return an immutable confidence-interval result
     * @throws IllegalArgumentException if an argument is {@code null}, a list item is
     *                                  {@code null}, a score has a points mapping, or the tracker
     *                                  and score sample sizes differ
     */
    public static SessionResult forConfidenceIntervals(
            List<Score> scores,
            CalibrationTracker calibrationTracker
    ) {
        if (calibrationTracker == null) {
            throw new IllegalArgumentException("calibrationTracker must not be null");
        }
        if (scores == null) {
            throw new IllegalArgumentException("scores must not be null");
        }
        if (calibrationTracker.getSampleSize() != scores.size()) {
            throw new IllegalArgumentException(
                    "calibrationTracker must contain one outcome for every score"
            );
        }
        return new SessionResult(
                Level.CONFIDENCE_INTERVALS,
                scores,
                null,
                calibrationTracker
        );
    }

    /**
     * Returns the curriculum stage played in this session.
     *
     * @return the non-null session level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Returns how many questions received an answer.
     *
     * @return the non-negative answer count, including remedial repeats
     */
    public int getAnsweredQuestionCount() {
        return answeredQuestionCount;
    }

    /**
     * Returns the arithmetic mean of the raw policy values.
     *
     * <p>For point estimates this is mean log-relative error; for confidence intervals it is
     * mean log interval loss. Smaller values are better in both cases, but values from different
     * levels must not be compared because the policies measure different forecasts.</p>
     *
     * @return the mean raw value, or an empty value when no question was answered
     */
    public OptionalDouble getMeanRawError() {
        if (answeredQuestionCount == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(meanRawError);
    }

    /**
     * Returns the arithmetic mean of user-facing points in a point-estimate session.
     *
     * @return the mean points, or an empty value for an empty or confidence-interval session
     */
    public OptionalDouble getMeanPoints() {
        if (answeredQuestionCount == 0 || level != Level.POINT_ESTIMATES) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(meanPoints);
    }

    /**
     * Returns how many point estimates fell in the correct band.
     *
     * @return the non-negative count, or zero for an interval session
     */
    public int getCorrectCount() {
        return correctCount;
    }

    /**
     * Returns how many point estimates fell in the close band.
     *
     * @return the non-negative count, or zero for an interval session
     */
    public int getCloseCount() {
        return closeCount;
    }

    /**
     * Returns how many point estimates fell in the wrong band.
     *
     * @return the non-negative count, or zero for an interval session
     */
    public int getWrongCount() {
        return wrongCount;
    }

    /**
     * Returns how many confidence intervals contained the true value.
     *
     * @return the non-negative hit count, or zero for a point-estimate session
     */
    public long getCalibrationHitCount() {
        return calibrationHitCount;
    }

    /**
     * Returns how many confidence intervals contributed to this session's calibration.
     *
     * @return the non-negative sample size, or zero for a point-estimate session
     */
    public long getCalibrationSampleSize() {
        return calibrationSampleSize;
    }

    /**
     * Reports whether the interval sample reached the tracker's meaningful-size rule.
     *
     * @return {@code true} only for a sufficiently large confidence-interval result
     */
    public boolean hasMeaningfulCalibrationSampleSize() {
        return meaningfulCalibrationSampleSize;
    }

    private static void validatePointsAvailability(Level level, Score score) {
        if (level == Level.POINT_ESTIMATES && !score.hasPoints()) {
            throw new IllegalArgumentException("point-estimate scores must include points");
        }
        if (level == Level.CONFIDENCE_INTERVALS && score.hasPoints()) {
            throw new IllegalArgumentException(
                    "confidence-interval scores must not include points"
            );
        }
    }
}
