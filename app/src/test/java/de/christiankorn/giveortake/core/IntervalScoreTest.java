package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests the log interval score selected in ADR 0007.
 */
public class IntervalScoreTest {
    private static final double PRECISE_COMPARISON = 1.0e-12;

    private final ScoringPolicy scoringPolicy = new IntervalScore();

    /**
     * Verifies that charging for logarithmic width prevents the widest-interval strategy.
     */
    @Test
    public void score_gameabilityTest_narrowHitStrictlyBeatsAbsurdlyWideHit() {
        Question question = questionWithTrueValue(346.0);

        Score narrowHit = scoringPolicy.score(question, new IntervalGuess(300.0, 400.0));
        Score absurdlyWideHit = scoringPolicy.score(question, new IntervalGuess(1.0, 1.0e12));

        assertTrue(narrowHit.getRawError() < absurdlyWideHit.getRawError());
        assertEquals(Math.log10(400.0) - Math.log10(300.0),
                narrowHit.getRawError(), PRECISE_COMPARISON);
        assertEquals(12.0, absurdlyWideHit.getRawError(), PRECISE_COMPARISON);
    }

    /**
     * Verifies that the miss penalty prevents width reduction from dominating accuracy.
     */
    @Test
    public void score_forNarrowMiss_isWorseThanWiderHit() {
        Question question = questionWithTrueValue(346.0);

        Score narrowMiss = scoringPolicy.score(question, new IntervalGuess(300.0, 330.0));
        Score widerHit = scoringPolicy.score(question, new IntervalGuess(300.0, 400.0));

        assertTrue(narrowMiss.getRawError() > widerHit.getRawError());
    }

    /**
     * Verifies that both interval bounds are inclusive and therefore add no miss penalty.
     */
    @Test
    public void score_forTruthExactlyOnEitherBound_chargesOnlyWidth() {
        IntervalGuess interval = new IntervalGuess(300.0, 400.0);
        double expectedLogarithmicWidth = Math.log10(400.0) - Math.log10(300.0);

        Score truthOnLowerBound = scoringPolicy.score(
                questionWithTrueValue(300.0),
                interval
        );
        Score truthOnUpperBound = scoringPolicy.score(
                questionWithTrueValue(400.0),
                interval
        );

        assertEquals(expectedLogarithmicWidth,
                truthOnLowerBound.getRawError(), PRECISE_COMPARISON);
        assertEquals(expectedLogarithmicWidth,
                truthOnUpperBound.getRawError(), PRECISE_COMPARISON);
    }

    /**
     * Verifies multiplicative symmetry for equally distant misses below and above an interval.
     */
    @Test
    public void score_forFactorOfTwoMissBelowAndAbove_isSymmetricOnLogScale() {
        Question question = questionWithTrueValue(100.0);
        double expectedLogarithmicWidth = Math.log10(2.0);
        double expectedMissPenalty = 20.0 * Math.log10(2.0);
        double expectedIntervalLoss = expectedLogarithmicWidth + expectedMissPenalty;

        Score belowInterval = scoringPolicy.score(question, new IntervalGuess(200.0, 400.0));
        Score aboveInterval = scoringPolicy.score(question, new IntervalGuess(25.0, 50.0));

        assertEquals(expectedIntervalLoss, belowInterval.getRawError(), PRECISE_COMPARISON);
        assertEquals(expectedIntervalLoss, aboveInterval.getRawError(), PRECISE_COMPARISON);
        assertEquals(belowInterval.getRawError(), aboveInterval.getRawError(), PRECISE_COMPARISON);
    }

    /**
     * Verifies that an exact degenerate interval has neither width nor miss loss.
     */
    @Test
    public void score_forDegenerateIntervalContainingTruth_hasZeroLoss() {
        Score score = scoringPolicy.score(
                questionWithTrueValue(346.0),
                new IntervalGuess(346.0, 346.0)
        );

        assertEquals(0.0, score.getRawError(), 0.0);
    }

    /**
     * A very narrow interval containing the truth must retain its small positive width: it is
     * more informative than a wider hit, but it is not the zero-width perfect forecast.
     */
    @Test
    public void score_forExtremelyNarrowHit_preservesSmallPositiveWidth() {
        double lowerBound = 1.0;
        double upperBound = lowerBound + 1.0e-9;
        Question question = questionWithTrueValue(lowerBound + 0.5e-9);

        Score score = scoringPolicy.score(
                question,
                new IntervalGuess(lowerBound, upperBound)
        );
        double expectedWidth = Math.log10(upperBound) - Math.log10(lowerBound);

        assertTrue(score.getRawError() > 0.0);
        assertTrue(Double.isFinite(score.getRawError()));
        assertEquals(expectedWidth, score.getRawError(), 0.0);
    }

    /**
     * A zero-width interval away from the truth must still be penalised: zero width removes only
     * the sharpness cost and cannot excuse a forecast that misses the true value.
     */
    @Test
    public void score_forDegenerateIntervalMissingTruth_chargesMissPenalty() {
        Score score = scoringPolicy.score(
                questionWithTrueValue(100.0),
                new IntervalGuess(10.0, 10.0)
        );

        assertEquals(20.0, score.getRawError(), PRECISE_COMPARISON);
        assertTrue(Double.isFinite(score.getRawError()));
    }

    /**
     * Verifies ADR 0007's decision to reject bounds outside the positive logarithmic domain.
     */
    @Test
    public void score_forLowerBoundAtOrBelowZero_isRejectedByIntervalGuess() {
        IllegalArgumentException zeroException = assertThrows(
                IllegalArgumentException.class,
                () -> new IntervalGuess(0.0, 400.0)
        );
        IllegalArgumentException negativeException = assertThrows(
                IllegalArgumentException.class,
                () -> new IntervalGuess(-1.0, 400.0)
        );

        assertEquals("lowerBound must be greater than zero", zeroException.getMessage());
        assertEquals("lowerBound must be greater than zero", negativeException.getMessage());
    }

    /**
     * Zero truth must be rejected by the question model because a logarithmic score has no
     * defined value at zero; silently manufacturing a score would hide a domain error.
     */
    @Test
    public void score_forZeroTruth_isRejectedByQuestion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> questionWithTrueValue(0.0)
        );

        assertEquals("trueValue must be greater than zero", exception.getMessage());
    }

    /**
     * Every non-finite bound must be rejected at construction because logarithms of NaN or
     * infinities cannot produce the finite loss promised by the scoring API.
     */
    @Test
    public void score_forNonFiniteBounds_isRejectedByIntervalGuess() {
        double[] nonFiniteValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double nonFiniteValue : nonFiniteValues) {
            IllegalArgumentException lowerException = assertThrows(
                    IllegalArgumentException.class,
                    () -> new IntervalGuess(nonFiniteValue, 400.0)
            );
            IllegalArgumentException upperException = assertThrows(
                    IllegalArgumentException.class,
                    () -> new IntervalGuess(300.0, nonFiniteValue)
            );

            assertEquals("lowerBound must be finite", lowerException.getMessage());
            assertEquals("upperBound must be finite", upperException.getMessage());
        }
    }

    /**
     * Every non-finite truth must be rejected by the question model because otherwise NaN or an
     * infinite logarithm could leak into the interval loss.
     */
    @Test
    public void score_forNonFiniteTruth_isRejectedByQuestion() {
        double[] nonFiniteValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double nonFiniteValue : nonFiniteValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> questionWithTrueValue(nonFiniteValue)
            );

            assertEquals("trueValue must be finite", exception.getMessage());
        }
    }

    /**
     * Verifies both the default 90 percent confidence and constructor-injected confidence.
     */
    @Test
    public void score_withDefaultAndInjectedConfidence_usesCorrespondingMissMultiplier() {
        Question question = questionWithTrueValue(200.0);
        IntervalGuess interval = new IntervalGuess(100.0, 100.0);

        Score defaultConfidence = new IntervalScore().score(question, interval);
        Score eightyPercentConfidence = new IntervalScore(0.80).score(question, interval);

        assertEquals(20.0 * Math.log10(2.0),
                defaultConfidence.getRawError(), PRECISE_COMPARISON);
        assertEquals(10.0 * Math.log10(2.0),
                eightyPercentConfidence.getRawError(), PRECISE_COMPARISON);
    }

    /**
     * Verifies that interval losses remain raw until a separate display mapping is decided.
     */
    @Test
    public void score_forIntervalLoss_hasNoUserFacingPointsMapping() {
        Score score = scoringPolicy.score(
                questionWithTrueValue(346.0),
                new IntervalGuess(300.0, 400.0)
        );

        assertFalse(score.hasPoints());
        assertThrows(IllegalStateException.class, score::getPoints);
    }

    /**
     * Verifies that invalid confidence levels cannot produce invalid alpha values.
     */
    @Test
    public void constructor_withInvalidNominalConfidenceLevel_throwsUsefulException() {
        double[] invalidConfidenceLevels = {
                0.0,
                1.0,
                -0.1,
                1.1,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidConfidenceLevel : invalidConfidenceLevels) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new IntervalScore(invalidConfidenceLevel)
            );

            assertEquals("nominalConfidenceLevel must be finite and between 0 and 1 exclusive",
                    exception.getMessage());
        }
    }

    /**
     * Verifies that this interval policy clearly rejects point answers.
     */
    @Test
    public void score_forPointGuess_throwsUsefulException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scoringPolicy.score(
                        questionWithTrueValue(346.0),
                        new PointGuess(346.0)
                )
        );

        assertEquals("IntervalScore requires an IntervalGuess", exception.getMessage());
    }

    /**
     * Verifies that missing inputs fail at the policy boundary with useful messages.
     */
    @Test
    public void score_withNullArguments_throwsUsefulExceptions() {
        IllegalArgumentException nullQuestionException = assertThrows(
                IllegalArgumentException.class,
                () -> scoringPolicy.score(null, new IntervalGuess(300.0, 400.0))
        );
        IllegalArgumentException nullGuessException = assertThrows(
                IllegalArgumentException.class,
                () -> scoringPolicy.score(questionWithTrueValue(346.0), null)
        );

        assertEquals("question must not be null", nullQuestionException.getMessage());
        assertEquals("guess must not be null", nullGuessException.getMessage());
    }

    /**
     * Verifies that logarithm subtraction keeps valid extreme inputs finite.
     */
    @Test
    public void score_forExtremeFiniteBounds_staysFinite() {
        Score score = scoringPolicy.score(
                questionWithTrueValue(1.0),
                new IntervalGuess(Double.MIN_VALUE, Double.MAX_VALUE)
        );

        assertTrue(Double.isFinite(score.getRawError()));
        assertFalse(Double.isNaN(score.getRawError()));
    }

    /**
     * A miss near Double.MAX_VALUE must remain finite because subtracting finite logarithms avoids
     * overflowing the ratios or distances that a direct calculation could create.
     */
    @Test
    public void score_forTruthNearDoubleMaximum_avoidsIntermediateOverflow() {
        double truth = Double.MAX_VALUE;
        double lowerBound = truth / 4.0;
        double upperBound = truth / 2.0;

        Score score = scoringPolicy.score(
                questionWithTrueValue(truth),
                new IntervalGuess(lowerBound, upperBound)
        );
        double expectedLoss = Math.log10(upperBound) - Math.log10(lowerBound)
                + 20.0 * (Math.log10(truth) - Math.log10(upperBound));

        assertTrue(Double.isFinite(score.getRawError()));
        assertFalse(Double.isNaN(score.getRawError()));
        assertEquals(expectedLoss, score.getRawError(), PRECISE_COMPARISON);
    }

    /**
     * Equal extreme inputs must produce exactly zero rather than NaN: using finite logarithms and
     * subtraction avoids undefined forms such as zero divided by zero or infinity minus infinity.
     */
    @Test
    public void score_forDegenerateIntervalAtDoubleMaximum_hasZeroFiniteLoss() {
        Score score = scoringPolicy.score(
                questionWithTrueValue(Double.MAX_VALUE),
                new IntervalGuess(Double.MAX_VALUE, Double.MAX_VALUE)
        );

        assertEquals(0.0, score.getRawError(), 0.0);
        assertTrue(Double.isFinite(score.getRawError()));
        assertFalse(Double.isNaN(score.getRawError()));
    }

    private static Question questionWithTrueValue(double trueValue) {
        return Question.builder()
                .id("test-question")
                .prompt("What is the value?")
                .trueValue(trueValue)
                .unit("units")
                .difficulty(1)
                .build();
    }
}
