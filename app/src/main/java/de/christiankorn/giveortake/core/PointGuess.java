package de.christiankorn.giveortake.core;

/**
 * Represents an immutable answer containing one numerical estimate.
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
