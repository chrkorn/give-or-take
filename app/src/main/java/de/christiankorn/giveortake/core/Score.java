package de.christiankorn.giveortake.core;

/**
 * Contains the numerical result produced by a {@link ScoringPolicy}.
 *
 * <p>The raw value preserves the precision and orientation defined by its policy. A policy may
 * additionally provide whole-number points suitable for display and session totals. Keeping
 * that mapping optional allows proper losses to remain raw until a separate display mapping is
 * explicitly selected. Direction and correctness are deliberately absent because they require
 * separate product decisions.</p>
 */
public final class Score {
    private static final int NO_POINTS = -1;

    private final double rawError;
    private final int points;

    /**
     * Creates an immutable scoring result without a user-facing points mapping.
     *
     * @param rawError the finite, non-negative raw value calculated by the scoring policy
     * @throws IllegalArgumentException if the raw value is non-finite or negative
     */
    public Score(double rawError) {
        validateRawError(rawError);
        this.rawError = rawError;
        this.points = NO_POINTS;
    }

    /**
     * Creates an immutable scoring result with user-facing points.
     *
     * @param rawError the finite, non-negative raw value calculated by the scoring policy
     * @param points the user-facing points value from 0 through 100
     * @throws IllegalArgumentException if the raw value or points are outside their valid ranges
     */
    public Score(double rawError, int points) {
        validateRawError(rawError);
        if (points < 0 || points > 100) {
            throw new IllegalArgumentException("points must be between 0 and 100");
        }
        this.rawError = rawError;
        this.points = points;
    }

    /**
     * Returns the unrounded raw value produced by the scoring policy.
     *
     * <p>The method retains its error-oriented name for compatibility with point-estimate
     * scoring. For a negatively oriented policy such as an interval score, this value is its raw
     * loss and smaller values are better.</p>
     *
     * @return the finite, non-negative raw policy value
     */
    public double getRawError() {
        return rawError;
    }

    /**
     * Reports whether this result includes a user-facing points mapping.
     *
     * @return {@code true} when {@link #getPoints()} can return a points value
     */
    public boolean hasPoints() {
        return points != NO_POINTS;
    }

    /**
     * Returns the whole-number points awarded for display and session totals.
     *
     * @return a value from 0 through 100
     * @throws IllegalStateException if this policy result has no points mapping
     */
    public int getPoints() {
        if (!hasPoints()) {
            throw new IllegalStateException("this score has no user-facing points mapping");
        }
        return points;
    }

    private static void validateRawError(double rawError) {
        if (!Double.isFinite(rawError) || rawError < 0.0) {
            throw new IllegalArgumentException("rawError must be finite and non-negative");
        }
    }
}
