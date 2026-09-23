package de.christiankorn.giveortake.core;

/**
 * Scores confidence intervals with the negatively oriented log interval score from ADR 0007.
 *
 * <p>For lower bound {@code l}, upper bound {@code u}, true value {@code y}, and
 * {@code alpha = 1 - nominalConfidenceLevel}, the raw interval loss is:</p>
 *
 * <pre>
 * L(l, u; y) = log10(u) - log10(l)
 *              + (2 / alpha) * (log10(l) - log10(y)) * I(y &lt; l)
 *              + (2 / alpha) * (log10(y) - log10(u)) * I(y &gt; u)
 * </pre>
 *
 * <p>The first term is the logarithmic width penalty. The remaining terms penalise a miss in
 * proportion to the truth's logarithmic distance outside the interval. Smaller losses are
 * better. The result intentionally has no user-facing points mapping because ADR 0007 defers
 * that separate decision. All values are finite and strictly positive through the invariants of
 * {@link Question} and {@link IntervalGuess}.</p>
 */
public final class IntervalScore implements ScoringPolicy {
    /** The nominal confidence level used by the no-argument constructor. */
    public static final double DEFAULT_NOMINAL_CONFIDENCE_LEVEL = 0.90;

    private static final double TWO_SIDED_MISS_WEIGHT = 2.0;

    private final double nominalConfidenceLevel;
    private final double alpha;

    /**
     * Creates an interval policy for the default 90 percent nominal confidence level.
     */
    public IntervalScore() {
        this(DEFAULT_NOMINAL_CONFIDENCE_LEVEL);
    }

    /**
     * Creates an interval policy for a central interval at the supplied confidence level.
     *
     * @param nominalConfidenceLevel the requested coverage probability, strictly between zero
     *                               and one
     * @throws IllegalArgumentException if the confidence level is non-finite or is not strictly
     *                                  between zero and one
     */
    public IntervalScore(double nominalConfidenceLevel) {
        if (!Double.isFinite(nominalConfidenceLevel)
                || nominalConfidenceLevel <= 0.0
                || nominalConfidenceLevel >= 1.0) {
            throw new IllegalArgumentException(
                    "nominalConfidenceLevel must be finite and between 0 and 1 exclusive"
            );
        }

        this.nominalConfidenceLevel = nominalConfidenceLevel;
        this.alpha = 1.0 - nominalConfidenceLevel;
    }

    /**
     * Returns the central interval's requested coverage probability.
     *
     * @return the finite nominal confidence level, strictly between zero and one
     */
    public double getNominalConfidenceLevel() {
        return nominalConfidenceLevel;
    }

    /**
     * Calculates the raw log interval loss for a confidence-interval answer.
     *
     * @param question the question containing the authoritative positive value
     * @param guess an {@link IntervalGuess} to evaluate
     * @return the finite, non-negative raw interval loss without a points mapping
     * @throws IllegalArgumentException if either argument is {@code null} or {@code guess} is not
     *                                  an {@link IntervalGuess}
     */
    @Override
    public Score score(Question question, Guess guess) {
        if (question == null) {
            throw new IllegalArgumentException("question must not be null");
        }
        if (guess == null) {
            throw new IllegalArgumentException("guess must not be null");
        }
        if (!(guess instanceof IntervalGuess)) {
            throw new IllegalArgumentException("IntervalScore requires an IntervalGuess");
        }

        return score(question.getTrueValue(), (IntervalGuess) guess);
    }

    /**
     * Scores a stored confidence interval against its scoring-time truth value.
     *
     * <p>This overload keeps historical rescoring on the same proper scoring-rule implementation
     * as live quiz play without manufacturing question text in the data layer.</p>
     *
     * @param trueValue finite scoring-time truth value greater than zero
     * @param guess stored confidence interval
     * @return the negatively oriented log interval loss
     * @throws IllegalArgumentException if an argument is invalid
     */
    public Score score(double trueValue, IntervalGuess guess) {
        if (!Double.isFinite(trueValue) || trueValue <= 0.0) {
            throw new IllegalArgumentException("trueValue must be finite and greater than zero");
        }
        if (guess == null) {
            throw new IllegalArgumentException("guess must not be null");
        }

        double logarithmicLowerBound = Math.log10(guess.getLowerBound());
        double logarithmicUpperBound = Math.log10(guess.getUpperBound());
        double logarithmicTruth = Math.log10(trueValue);

        double logarithmicWidth = logarithmicUpperBound - logarithmicLowerBound;
        double missPenaltyMultiplier = TWO_SIDED_MISS_WEIGHT / alpha;
        double missBelowPenalty = 0.0;
        double missAbovePenalty = 0.0;

        if (trueValue < guess.getLowerBound()) {
            missBelowPenalty = missPenaltyMultiplier
                    * (logarithmicLowerBound - logarithmicTruth);
        } else if (trueValue > guess.getUpperBound()) {
            missAbovePenalty = missPenaltyMultiplier
                    * (logarithmicTruth - logarithmicUpperBound);
        }

        // Differences of logarithms preserve the displayed formula without forming ratios that
        // could overflow or underflow for valid extreme values.
        double intervalLoss = logarithmicWidth + missBelowPenalty + missAbovePenalty;
        return new Score(intervalLoss);
    }
}
