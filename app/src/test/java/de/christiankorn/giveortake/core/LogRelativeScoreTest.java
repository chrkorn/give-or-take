package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Tests log-relative error calculation and its exponential points mapping.
 */
public class LogRelativeScoreTest {
    private static final double THREE_DECIMAL_PLACES = 0.0005;
    private static final double PRECISE_COMPARISON = 1.0e-12;

    private final ScoringPolicy scoringPolicy = new LogRelativeScore();

    /**
     * Verifies the reference errors used to choose the metric in ADR 0004.
     */
    @Test
    public void score_forReferenceGuesses_matchesExpectedErrorsToThreeDecimalPlaces() {
        double[][] examples = {
                {35.0, 0.995},
                {173.0, 0.301},
                {300.0, 0.062},
                {346.0, 0.000},
                {400.0, 0.063},
                {692.0, 0.301},
                {3460.0, 1.000}
        };

        for (double[] example : examples) {
            Score score = scoringPolicy.score(questionWithTrueValue(346.0), new PointGuess(example[0]));

            assertEquals(example[1], score.getRawError(), THREE_DECIMAL_PLACES);
        }
    }

    /**
     * Verifies the lower error boundary and upper points boundary.
     */
    @Test
    public void score_forExactAnswer_hasZeroErrorAndMaximumPoints() {
        Score score = scoringPolicy.score(questionWithTrueValue(346.0), new PointGuess(346.0));

        assertEquals(0.0, score.getRawError(), 0.0);
        assertEquals(100, score.getPoints());
    }

    /**
     * Verifies that equal multiplicative errors receive equal results in both directions.
     */
    @Test
    public void score_forDoubleAndHalfAnswers_isSymmetric() {
        Question question = questionWithTrueValue(346.0);
        Score halfScore = scoringPolicy.score(question, new PointGuess(173.0));
        Score doubleScore = scoringPolicy.score(question, new PointGuess(692.0));

        assertEquals(halfScore.getRawError(), doubleScore.getRawError(), PRECISE_COMPARISON);
        assertEquals(halfScore.getPoints(), doubleScore.getPoints());
        assertEquals(50, halfScore.getPoints());
    }

    /**
     * Verifies the factor-of-ten landmark for both raw error and points.
     */
    @Test
    public void score_forFactorOfTenError_hasExactlyOneRawErrorAndTenPoints() {
        Score score = scoringPolicy.score(questionWithTrueValue(346.0), new PointGuess(3460.0));

        assertEquals(1.0, score.getRawError(), 0.0);
        assertEquals(10, score.getPoints());
    }

    /**
     * Verifies that extreme but valid magnitudes stay finite.
     *
     * <p>These inputs satisfy every domain invariant: both values are finite and strictly
     * positive. Computing {@code guess / trueValue} first would nevertheless overflow to
     * infinity or underflow to zero, producing an infinite error that {@link Score} rejects.
     * The difference-of-logarithms form used by {@link LogRelativeScore} is algebraically
     * identical and cannot overflow. See ADR 0004.</p>
     */
    @Test
    public void score_forExtremeMagnitudes_staysFiniteAndAwardsNoPoints() {
        // guess / trueValue would overflow to positive infinity.
        Score overflowing = scoringPolicy.score(
                questionWithTrueValue(Double.MIN_VALUE),
                new PointGuess(Double.MAX_VALUE)
        );

        // guess / trueValue would underflow to zero.
        Score underflowing = scoringPolicy.score(
                questionWithTrueValue(Double.MAX_VALUE),
                new PointGuess(Double.MIN_VALUE)
        );

        // A subnormal guess against an ordinary true value would also underflow.
        Score subnormalGuess = scoringPolicy.score(
                questionWithTrueValue(346.0),
                new PointGuess(Double.MIN_VALUE)
        );

        assertEquals(631.561, overflowing.getRawError(), THREE_DECIMAL_PLACES);
        assertEquals(631.561, underflowing.getRawError(), THREE_DECIMAL_PLACES);
        assertEquals(325.845, subnormalGuess.getRawError(), THREE_DECIMAL_PLACES);
        assertEquals(0, overflowing.getPoints());
        assertEquals(0, underflowing.getPoints());
        assertEquals(0, subnormalGuess.getPoints());
    }

    /**
     * Verifies that the two algebraically equivalent forms agree where both are defined.
     *
     * <p>Guards against the difference-of-logarithms form drifting from the ratio form for
     * ordinary inputs while fixing its behaviour at the extremes.</p>
     */
    @Test
    public void score_forOrdinaryMagnitudes_matchesTheRatioForm() {
        double trueValue = 346.0;
        double[] guesses = {35.0, 173.0, 300.0, 346.0, 400.0, 692.0, 3460.0, 1.0e-6, 1.0e12};

        for (double guess : guesses) {
            double ratioForm = Math.abs(Math.log10(guess / trueValue));
            double actual = scoringPolicy
                    .score(questionWithTrueValue(trueValue), new PointGuess(guess))
                    .getRawError();

            assertEquals("guess " + guess, ratioForm, actual, PRECISE_COMPARISON);
        }
    }

    /**
     * Verifies that this point-estimate policy clearly rejects interval answers.
     */
    @Test
    public void score_forIntervalGuess_throwsUsefulException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scoringPolicy.score(
                        questionWithTrueValue(346.0),
                        new IntervalGuess(300.0, 400.0)
                )
        );

        assertEquals("LogRelativeScore requires a PointGuess", exception.getMessage());
    }

    /**
     * Verifies that missing inputs fail at the policy boundary with useful messages.
     */
    @Test
    public void score_withNullArguments_throwsUsefulExceptions() {
        IllegalArgumentException nullQuestionException = assertThrows(
                IllegalArgumentException.class,
                () -> scoringPolicy.score(null, new PointGuess(346.0))
        );
        IllegalArgumentException nullGuessException = assertThrows(
                IllegalArgumentException.class,
                () -> scoringPolicy.score(questionWithTrueValue(346.0), null)
        );

        assertEquals("question must not be null", nullQuestionException.getMessage());
        assertEquals("guess must not be null", nullGuessException.getMessage());
    }

    private static Question questionWithTrueValue(double trueValue) {
        return Question.builder()
                .id("test-question")
                .prompt("What is the value?")
                .trueValue(trueValue)
                .unit("units")
                .measurementBasis("test value")
                .build();
    }
}
