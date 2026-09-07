package de.christiankorn.giveortake.core;

/**
 * Maps a non-negative log-relative error to a user-facing {@link Correctness} band.
 *
 * <p>The default thresholds implement ADR 0006. They can be supplied through the constructor so
 * later empirical tuning does not require changes to the classification algorithm. Injection also
 * lets unit tests use deliberately simple boundaries and test the comparison rules in isolation
 * from the calculation of the default logarithmic values.</p>
 */
public final class CorrectnessClassifier {
    /** Default inclusive upper boundary for estimates within a factor of 1.5. */
    public static final double DEFAULT_CORRECT_THRESHOLD = Math.log10(1.5);

    /** Default inclusive upper boundary for estimates within a factor of 3. */
    public static final double DEFAULT_CLOSE_THRESHOLD = Math.log10(3.0);

    private final double correctThreshold;
    private final double closeThreshold;

    /**
     * Creates a classifier using the factor-of-1.5 and factor-of-3 boundaries from ADR 0006.
     */
    public CorrectnessClassifier() {
        this(DEFAULT_CORRECT_THRESHOLD, DEFAULT_CLOSE_THRESHOLD);
    }

    /**
     * Creates a classifier with configurable inclusive upper boundaries.
     *
     * @param correctThreshold inclusive upper error boundary for {@link Correctness#CORRECT}
     * @param closeThreshold inclusive upper error boundary for {@link Correctness#CLOSE}
     * @throws IllegalArgumentException if either threshold is not finite and non-negative, or if
     *         {@code closeThreshold} is not greater than {@code correctThreshold}
     */
    public CorrectnessClassifier(double correctThreshold, double closeThreshold) {
        if (!Double.isFinite(correctThreshold) || correctThreshold < 0.0) {
            throw new IllegalArgumentException("correctThreshold must be finite and non-negative");
        }
        if (!Double.isFinite(closeThreshold) || closeThreshold <= correctThreshold) {
            throw new IllegalArgumentException(
                    "closeThreshold must be finite and greater than correctThreshold"
            );
        }
        this.correctThreshold = correctThreshold;
        this.closeThreshold = closeThreshold;
    }

    /**
     * Classifies a raw log-relative error using this classifier's thresholds.
     *
     * @param rawError the finite, non-negative log-relative error to classify
     * @return the matching correctness band
     * @throws IllegalArgumentException if {@code rawError} is not finite and non-negative
     */
    public Correctness classify(double rawError) {
        if (!Double.isFinite(rawError) || rawError < 0.0) {
            throw new IllegalArgumentException("rawError must be finite and non-negative");
        }

        // ADR 0006 makes both upper boundaries inclusive and requires evaluation in this order.
        if (rawError <= correctThreshold) {
            return Correctness.CORRECT;
        }
        if (rawError <= closeThreshold) {
            return Correctness.CLOSE;
        }
        return Correctness.WRONG;
    }
}
