package de.christiankorn.giveortake.core;

/**
 * Describes the directed multiplicative difference between an estimate and a true value.
 *
 * <p>The scoring metric deliberately discards direction by taking an absolute logarithm. Feedback
 * needs the original values as well so that an equal error can be explained as either too high or
 * too low without losing the metric's multiplicative symmetry.</p>
 */
public final class EstimateComparison {
    private final Direction direction;
    private final double factor;

    private EstimateComparison(Direction direction, double factor) {
        this.direction = direction;
        this.factor = factor;
    }

    /**
     * Compares two finite, strictly positive values.
     *
     * @param estimate the submitted estimate
     * @param trueValue the authoritative value
     * @return the direction and factor separating the values
     * @throws IllegalArgumentException if either value is not finite and strictly positive
     */
    public static EstimateComparison between(double estimate, double trueValue) {
        requirePositiveFinite(estimate, "estimate");
        requirePositiveFinite(trueValue, "trueValue");

        if (Double.compare(estimate, trueValue) == 0) {
            return new EstimateComparison(Direction.EXACT, 1.0);
        }

        Direction direction = estimate > trueValue ? Direction.HIGH : Direction.LOW;
        double factor = Math.pow(
                10.0,
                Math.abs(Math.log10(estimate) - Math.log10(trueValue))
        );
        return new EstimateComparison(direction, factor);
    }

    /**
     * Returns whether the estimate was exact, too high, or too low.
     *
     * @return the comparison direction
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Returns the multiplicative distance between the values.
     *
     * @return one for an exact estimate, otherwise a value greater than one
     */
    public double getFactor() {
        return factor;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }

    /**
     * Identifies the estimate's direction relative to the true value.
     */
    public enum Direction {
        /** The estimate equals the true value. */
        EXACT,

        /** The estimate is greater than the true value. */
        HIGH,

        /** The estimate is less than the true value. */
        LOW
    }
}
