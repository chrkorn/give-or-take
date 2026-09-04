package de.christiankorn.giveortake.core;

/**
 * Contains the numerical result produced by a {@link ScoringPolicy}.
 *
 * <p>The raw error preserves the precision needed for analysis, while points provide a
 * whole-number value suitable for display and session totals. Direction and correctness are
 * deliberately absent because they require separate product decisions.</p>
 */
public final class Score {
    private final double rawError;
    private final int points;

    /**
     * Creates an immutable scoring result.
     *
     * @param rawError the finite, non-negative error calculated by the scoring policy
     * @param points the user-facing points value from 0 through 100
     * @throws IllegalArgumentException if the error or points are outside their valid ranges
     */
    public Score(double rawError, int points) {
        if (!Double.isFinite(rawError) || rawError < 0.0) {
            throw new IllegalArgumentException("rawError must be finite and non-negative");
        }
        if (points < 0 || points > 100) {
            throw new IllegalArgumentException("points must be between 0 and 100");
        }
        this.rawError = rawError;
        this.points = points;
    }

    /**
     * Returns the unrounded error produced by the scoring policy.
     *
     * @return the finite, non-negative raw error
     */
    public double getRawError() {
        return rawError;
    }

    /**
     * Returns the whole-number points awarded for display and session totals.
     *
     * @return a value from 0 through 100
     */
    public int getPoints() {
        return points;
    }
}
