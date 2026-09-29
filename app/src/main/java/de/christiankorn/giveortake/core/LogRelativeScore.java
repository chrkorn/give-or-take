package de.christiankorn.giveortake.core;

/**
 * Scores {@link PointGuess} answers by their multiplicative distance from the true value.
 *
 * <p>The raw error is {@code abs(log10(guess) - log10(trueValue))}. Points decay exponentially at
 * {@code 100 * exp(-ln(10) * error)}, which is equivalent to dividing 100 by the factor by
 * which the estimate is wrong. The result is rounded to the nearest whole point.</p>
 *
 * <p>The raw metric implements ADR 0004
 * ({@code docs/adr/0004-use-log-relative-error-for-point-estimates.md}); its points mapping
 * implements ADR 0005 ({@code docs/adr/0005-map-log-relative-error-to-points.md}). This policy
 * remains in the pure-Java core layer so its numerical behavior can be covered by JVM tests.</p>
 */
public final class LogRelativeScore implements ScoringPolicy {
    private static final double POINT_DECAY_RATE = Math.log(10.0);

    /**
     * Creates the stateless log-relative scoring policy.
     */
    public LogRelativeScore() {
    }

    /**
     * Scores a positive point estimate against a question's positive true value.
     *
     * @param question the question containing the authoritative value
     * @param guess a {@link PointGuess} to evaluate
     * @return the log-relative raw error and a points value from 0 through 100
     * @throws IllegalArgumentException if either argument is {@code null} or {@code guess} is not
     *         a {@link PointGuess}
     */
    @Override
    public Score score(Question question, Guess guess) {
        if (question == null) {
            throw new IllegalArgumentException("question must not be null");
        }
        if (guess == null) {
            throw new IllegalArgumentException("guess must not be null");
        }
        if (!(guess instanceof PointGuess)) {
            throw new IllegalArgumentException("LogRelativeScore requires a PointGuess");
        }

        return score(question.getTrueValue(), (PointGuess) guess);
    }

    /**
     * Scores a stored point estimate against its scoring-time truth value.
     *
     * <p>This overload lets history be rescored without reconstructing question content that was
     * never part of the numerical policy. It applies exactly the same arithmetic as
     * {@link #score(Question, Guess)}.</p>
     *
     * @param trueValue finite scoring-time truth value greater than zero
     * @param guess stored point estimate
     * @return the log-relative raw error and a points value from 0 through 100
     * @throws IllegalArgumentException if an argument is invalid
     */
    public Score score(double trueValue, PointGuess guess) {
        if (!Double.isFinite(trueValue) || trueValue <= 0.0) {
            throw new IllegalArgumentException("trueValue must be finite and greater than zero");
        }
        if (guess == null) {
            throw new IllegalArgumentException("guess must not be null");
        }

        // Subtracting logarithms is equivalent to log10(guess / trueValue), but avoids overflow
        // or underflow when valid finite inputs are many orders of magnitude apart (ADR 0004).
        double rawError = Math.abs(Math.log10(guess.getValue()) - Math.log10(trueValue));
        double unroundedPoints = 100.0 * Math.exp(-POINT_DECAY_RATE * rawError);

        // Whole points keep the displayed result and accumulated session total easy to explain.
        int points = (int) Math.round(unroundedPoints);
        return new Score(rawError, points);
    }
}
