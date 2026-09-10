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

    /**
     * Aggregates a session from its level and individual scores.
     *
     * @param level the curriculum stage played in the session
     * @param scores the scores produced for each answered question, in any order
     * @throws IllegalArgumentException if either argument is {@code null}, the list contains
     *                                  {@code null}, or a score's points availability does not
     *                                  match the level's scoring policy
     */
    public SessionResult(Level level, List<Score> scores) {
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
        }

        meanRawError = runningRawMean;
        meanPoints = runningPointsMean;
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
