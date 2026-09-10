package de.christiankorn.giveortake.core;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Holds an immutable, level-specific personal best without performing persistence.
 *
 * <p>Point-estimate sessions compete on mean points, where higher is better. Confidence-interval
 * sessions compete on mean raw interval loss, where lower is better. Keeping the level in the
 * value object prevents unlike scoring policies from being compared. Storage and retrieval belong
 * to the data layer, which can reconstruct this object from its exposed values.</p>
 */
public final class HighScore {
    private final Level level;
    private final boolean recorded;
    private final double bestValue;
    private final int answeredQuestionCount;

    private HighScore(
            Level level,
            boolean recorded,
            double bestValue,
            int answeredQuestionCount
    ) {
        this.level = level;
        this.recorded = recorded;
        this.bestValue = bestValue;
        this.answeredQuestionCount = answeredQuestionCount;
    }

    /**
     * Creates an empty personal best for one curriculum level.
     *
     * @param level the level whose sessions will be compared
     * @return an empty immutable high score
     * @throws IllegalArgumentException if {@code level} is {@code null}
     */
    public static HighScore empty(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        return new HighScore(level, false, 0.0, 0);
    }

    /**
     * Reconstructs a recorded personal best, for example after the data layer loads it.
     *
     * @param level the level whose sessions are compared
     * @param bestValue mean points for point estimates or mean raw loss for intervals
     * @param answeredQuestionCount number of answers in the session that set the record
     * @return the reconstructed immutable high score
     * @throws IllegalArgumentException if an argument is invalid for the selected level
     */
    public static HighScore recorded(
            Level level,
            double bestValue,
            int answeredQuestionCount
    ) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (!Double.isFinite(bestValue) || bestValue < 0.0
                || (level == Level.POINT_ESTIMATES && bestValue > 100.0)) {
            throw new IllegalArgumentException("bestValue is invalid for the selected level");
        }
        if (answeredQuestionCount <= 0) {
            throw new IllegalArgumentException("answeredQuestionCount must be greater than zero");
        }
        return new HighScore(level, true, bestValue, answeredQuestionCount);
    }

    /**
     * Returns the curriculum level to which this record belongs.
     *
     * @return the non-null level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Returns the best level-specific mean recorded so far.
     *
     * @return mean points for point estimates or mean raw loss for intervals, or an empty value
     *         before a non-empty session is recorded
     */
    public OptionalDouble getBestValue() {
        return recorded ? OptionalDouble.of(bestValue) : OptionalDouble.empty();
    }

    /**
     * Returns the size of the session that established the personal best.
     *
     * @return the positive answer count, or an empty value before a session is recorded
     */
    public OptionalInt getAnsweredQuestionCount() {
        return recorded
                ? OptionalInt.of(answeredQuestionCount)
                : OptionalInt.empty();
    }

    /**
     * Returns the record after considering a session as a candidate.
     *
     * <p>An empty session cannot establish a record. A strictly better mean creates a new value;
     * a worse result or exact tie returns this object unchanged.</p>
     *
     * @param sessionResult candidate session from the same level
     * @return an updated immutable high score only when the candidate strictly beats the record
     * @throws IllegalArgumentException if the session is {@code null} or belongs to another level
     */
    public HighScore afterSession(SessionResult sessionResult) {
        if (sessionResult == null) {
            throw new IllegalArgumentException("sessionResult must not be null");
        }
        if (sessionResult.getLevel() != level) {
            throw new IllegalArgumentException("sessionResult must belong to the same level");
        }

        OptionalDouble candidateValue = valueFor(sessionResult);
        if (!candidateValue.isPresent()) {
            return this;
        }

        double candidate = candidateValue.getAsDouble();
        if (!recorded || isStrictlyBetter(candidate)) {
            return new HighScore(
                    level,
                    true,
                    candidate,
                    sessionResult.getAnsweredQuestionCount()
            );
        }
        return this;
    }

    /**
     * Compares high scores by their level, presence, best value, and record-setting sample size.
     *
     * @param other object to compare with this high score
     * @return {@code true} when both value objects represent the same record
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HighScore)) {
            return false;
        }
        HighScore highScore = (HighScore) other;
        return recorded == highScore.recorded
                && Double.compare(bestValue, highScore.bestValue) == 0
                && answeredQuestionCount == highScore.answeredQuestionCount
                && level == highScore.level;
    }

    /**
     * Returns a hash code derived from every value represented by this object.
     *
     * @return a hash code consistent with {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        return Objects.hash(level, recorded, bestValue, answeredQuestionCount);
    }

    private OptionalDouble valueFor(SessionResult sessionResult) {
        if (level == Level.POINT_ESTIMATES) {
            return sessionResult.getMeanPoints();
        }
        return sessionResult.getMeanRawError();
    }

    private boolean isStrictlyBetter(double candidate) {
        if (level == Level.POINT_ESTIMATES) {
            return candidate > bestValue;
        }
        return candidate < bestValue;
    }
}
