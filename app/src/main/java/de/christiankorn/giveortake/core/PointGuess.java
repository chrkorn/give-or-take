package de.christiankorn.giveortake.core;

/**
 * Represents an immutable point-estimate answer in the pure-Java core layer.
 *
 * <p>The strictly positive domain makes the guess valid input to {@link LogRelativeScore}; zero
 * and signed estimates need a different scoring policy. The separate point and interval answer
 * types implement ADR 0003 ({@code docs/adr/0003-represent-guesses-as-separate-types.md}).</p>
 */
public final class PointGuess implements Guess {
    private final double value;

    /**
     * Creates a point estimate in the positive value domain used by the question model.
     *
     * @param value the finite estimate, greater than zero
     * @throws IllegalArgumentException if {@code value} is non-finite or not greater than zero
     */
    public PointGuess(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException("value must be greater than zero");
        }
        this.value = value;
    }

    /**
     * Returns the player's numerical estimate.
     *
     * @return the finite, strictly positive estimate
     */
    public double getValue() {
        return value;
    }
}
