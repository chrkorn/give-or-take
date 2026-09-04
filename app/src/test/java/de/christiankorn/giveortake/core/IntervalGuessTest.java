package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests construction, validation, and interval behaviour of {@link IntervalGuess}.
 */
public class IntervalGuessTest {
    @Test
    public void constructor_withValidBounds_exposesBoundsThroughGuessType() {
        Guess guess = new IntervalGuess(300.0, 400.0);

        assertTrue(guess instanceof IntervalGuess);
        IntervalGuess intervalGuess = (IntervalGuess) guess;
        assertEquals(300.0, intervalGuess.getLowerBound(), 0.0);
        assertEquals(400.0, intervalGuess.getUpperBound(), 0.0);
    }

    @Test
    public void constructor_withEqualBounds_acceptsZeroWidthInterval() {
        IntervalGuess guess = new IntervalGuess(346.0, 346.0);

        assertEquals(0.0, guess.width(), 0.0);
    }

    @Test
    public void constructor_withInvertedBounds_throwsUsefulException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new IntervalGuess(400.0, 300.0)
        );

        assertEquals(
                "lowerBound must be less than or equal to upperBound",
                exception.getMessage()
        );
    }

    @Test
    public void constructor_withNonFiniteLowerBound_throwsUsefulException() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new IntervalGuess(invalidValue, 400.0)
            );
            assertEquals("lowerBound must be finite", exception.getMessage());
        }
    }

    @Test
    public void constructor_withNonFiniteUpperBound_throwsUsefulException() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new IntervalGuess(300.0, invalidValue)
            );
            assertEquals("upperBound must be finite", exception.getMessage());
        }
    }

    @Test
    public void constructor_withNonPositiveBound_throwsUsefulException() {
        IllegalArgumentException zeroException = assertThrows(
                IllegalArgumentException.class,
                () -> new IntervalGuess(0.0, 400.0)
        );
        IllegalArgumentException negativeException = assertThrows(
                IllegalArgumentException.class,
                () -> new IntervalGuess(-1.0, 400.0)
        );
        IllegalArgumentException upperException = assertThrows(
                IllegalArgumentException.class,
                () -> new IntervalGuess(300.0, 0.0)
        );

        assertEquals("lowerBound must be greater than zero", zeroException.getMessage());
        assertEquals("lowerBound must be greater than zero", negativeException.getMessage());
        assertEquals("upperBound must be greater than zero", upperException.getMessage());
    }

    @Test
    public void containsTruth_atEitherExactBoundary_returnsTrue() {
        IntervalGuess guess = new IntervalGuess(300.0, 400.0);

        assertTrue(guess.containsTruth(300.0));
        assertTrue(guess.containsTruth(400.0));
    }

    @Test
    public void containsTruth_insideAndOutsideInterval_returnsExpectedResult() {
        IntervalGuess guess = new IntervalGuess(300.0, 400.0);

        assertTrue(guess.containsTruth(346.0));
        assertFalse(guess.containsTruth(299.9));
        assertFalse(guess.containsTruth(400.1));
    }

    @Test
    public void width_forSameMultiplicativeSpan_isIndependentOfScale() {
        IntervalGuess small = new IntervalGuess(10.0, 100.0);
        IntervalGuess large = new IntervalGuess(1_000.0, 10_000.0);

        assertEquals(1.0, small.width(), 0.0);
        assertEquals(small.width(), large.width(), 0.0);
    }

    @Test
    public void width_withExtremeFiniteBounds_doesNotOverflow() {
        IntervalGuess guess = new IntervalGuess(Double.MIN_VALUE, Double.MAX_VALUE);

        assertTrue(Double.isFinite(guess.width()));
    }
}
