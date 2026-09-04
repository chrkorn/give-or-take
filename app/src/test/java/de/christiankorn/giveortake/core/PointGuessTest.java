package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests construction and validation of {@link PointGuess}.
 */
public class PointGuessTest {
    @Test
    public void constructor_withValidValue_exposesValueThroughGuessType() {
        Guess guess = new PointGuess(346.0);

        assertTrue(guess instanceof PointGuess);
        assertEquals(346.0, ((PointGuess) guess).getValue(), 0.0);
    }

    @Test
    public void constructor_withNonFiniteValue_throwsUsefulException() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PointGuess(invalidValue)
            );
            assertEquals("value must be finite", exception.getMessage());
        }
    }

    @Test
    public void constructor_withNonPositiveValue_throwsUsefulException() {
        double[] invalidValues = {0.0, -1.0};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PointGuess(invalidValue)
            );
            assertEquals("value must be greater than zero", exception.getMessage());
        }
    }
}
