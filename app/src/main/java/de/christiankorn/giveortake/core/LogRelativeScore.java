package de.christiankorn.giveortake.core;

/**
 * Scores point estimates using their multiplicative distance from the true value.
 *
 * <p>The raw error is {@code abs(log10(guess) - log10(trueValue))}. Points decay exponentially at
 * {@code 100 * exp(-ln(10) * error)}, which is equivalent to dividing 100 by the factor by
 * which the estimate is wrong. The result is rounded to the nearest whole point.</p>
 */
public final class LogRelativeScore implements ScoringPolicy {
    private static final double POINT_DECAY_RATE = Math.log(10.0);

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

        PointGuess pointGuess = (PointGuess) guess;

        // Subtracting logarithms is algebraically identical to log10(guess / trueValue) but is
        // numerically robust. Forming the ratio first overflows to infinity, or underflows to
        // zero, whenever the two finite positive values are far enough apart -- for example a
        // guess of Double.MAX_VALUE against a true value of Double.MIN_VALUE. The logarithm of
        // such a ratio is infinite, which the Score constructor then rejects with a message that
        // says nothing about the real cause. Working in the exponent domain cannot overflow for
        // any finite positive inputs, which the domain invariants already guarantee.
        double rawError = Math.abs(Math.log10(pointGuess.getValue()) - Math.log10(question.getTrueValue()));
        double unroundedPoints = 100.0 * Math.exp(-POINT_DECAY_RATE * rawError);

        // Whole points keep the displayed result and accumulated session total easy to explain.
        int points = (int) Math.round(unroundedPoints);
        return new Score(rawError, points);
    }
}
