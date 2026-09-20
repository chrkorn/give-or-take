package de.christiankorn.giveortake.core;

/**
 * Converts between multiplicative uncertainty factors and positions on a logarithmic dial.
 *
 * <p>The returned position is a fraction rather than a pixel coordinate, so this class remains
 * independent of Android and screen geometry. A logarithmic scale gives equal visual distances
 * to equal multiplicative changes: for example, the intervals from ×2 to ×4 and from ×4 to
 * ×8 occupy the same width.</p>
 */
public final class UncertaintyScale {

    private UncertaintyScale() {
        // This class contains only pure conversion functions and must not be instantiated.
    }

    /**
     * Maps a factor within the configured range to a position fraction from {@code 0} to
     * {@code 1}.
     *
     * @param factor the uncertainty factor to map
     * @param minimumFactor the factor represented by position {@code 0}
     * @param maximumFactor the factor represented by position {@code 1}
     * @return the factor's logarithmic position fraction
     * @throws IllegalArgumentException if any argument is non-finite, a factor is not positive,
     *         the range does not increase, or {@code factor} lies outside the range
     */
    public static double factorToPositionFraction(
            double factor,
            double minimumFactor,
            double maximumFactor
    ) {
        validateRange(minimumFactor, maximumFactor);
        if (!Double.isFinite(factor) || factor < minimumFactor || factor > maximumFactor) {
            throw new IllegalArgumentException("factor must be finite and within the factor range");
        }

        return (Math.log(factor) - Math.log(minimumFactor))
                / (Math.log(maximumFactor) - Math.log(minimumFactor));
    }

    /**
     * Maps a position fraction from {@code 0} to {@code 1} back to an uncertainty factor.
     *
     * @param positionFraction the normalised position to map
     * @param minimumFactor the factor represented by position {@code 0}
     * @param maximumFactor the factor represented by position {@code 1}
     * @return the uncertainty factor at the logarithmic position
     * @throws IllegalArgumentException if any argument is non-finite, a factor is not positive,
     *         the range does not increase, or {@code positionFraction} lies outside
     *         {@code [0, 1]}
     */
    public static double positionFractionToFactor(
            double positionFraction,
            double minimumFactor,
            double maximumFactor
    ) {
        validateRange(minimumFactor, maximumFactor);
        if (!Double.isFinite(positionFraction)
                || positionFraction < 0.0
                || positionFraction > 1.0) {
            throw new IllegalArgumentException("position fraction must be finite and within [0, 1]");
        }

        double logarithmicMinimum = Math.log(minimumFactor);
        double logarithmicRange = Math.log(maximumFactor) - logarithmicMinimum;
        return Math.exp(logarithmicMinimum + positionFraction * logarithmicRange);
    }

    private static void validateRange(double minimumFactor, double maximumFactor) {
        if (!Double.isFinite(minimumFactor)
                || !Double.isFinite(maximumFactor)
                || minimumFactor <= 0.0
                || maximumFactor <= minimumFactor) {
            throw new IllegalArgumentException(
                    "factor range must contain finite positive values with minimum below maximum"
            );
        }
    }
}
