package de.christiankorn.giveortake.core;

/**
 * Represents an immutable 90 percent confidence interval supplied as an answer.
 *
 * <p>Both bounds belong to the positive value domain used by the question model. This permits
 * interval width to be measured as a multiplicative span rather than an absolute difference.</p>
 */
public final class IntervalGuess implements Guess {
    private final double lowerBound;
    private final double upperBound;

    /**
     * Creates an interval with inclusive lower and upper bounds.
     *
     * @param lowerBound the finite lower bound, greater than zero
     * @param upperBound the finite upper bound, greater than zero
     * @throws IllegalArgumentException if either bound is non-finite or not greater than zero,
     *                                  or if the lower bound exceeds the upper bound
     */
    public IntervalGuess(double lowerBound, double upperBound) {
        if (!Double.isFinite(lowerBound)) {
            throw new IllegalArgumentException("lowerBound must be finite");
        }
        if (!Double.isFinite(upperBound)) {
            throw new IllegalArgumentException("upperBound must be finite");
        }
        if (lowerBound <= 0.0) {
            throw new IllegalArgumentException("lowerBound must be greater than zero");
        }
        if (upperBound <= 0.0) {
            throw new IllegalArgumentException("upperBound must be greater than zero");
        }
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(
                    "lowerBound must be less than or equal to upperBound"
            );
        }

        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    /**
     * Returns the inclusive lower end of the interval.
     *
     * @return the finite, strictly positive lower bound
     */
    public double getLowerBound() {
        return lowerBound;
    }

    /**
     * Returns the inclusive upper end of the interval.
     *
     * @return the finite, strictly positive upper bound
     */
    public double getUpperBound() {
        return upperBound;
    }

    /**
     * Reports whether a true value lies within this interval, including either boundary.
     *
     * @param trueValue the value to compare with the bounds
     * @return {@code true} when {@code trueValue} is at least the lower bound and at most the
     *         upper bound
     */
    public boolean containsTruth(double trueValue) {
        return trueValue >= lowerBound && trueValue <= upperBound;
    }

    /**
     * Returns the interval's base-10 logarithmic width.
     *
     * <p>The result is the difference between the bounds' base-10 logarithms. It therefore
     * measures multiplicative span: every tenfold interval has width {@code 1.0}, regardless
     * of its absolute scale.</p>
     *
     * @return {@code log10(upperBound) - log10(lowerBound)}
     */
    public double width() {
        return Math.log10(upperBound) - Math.log10(lowerBound);
    }
}
