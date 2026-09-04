package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Tests the invariants and exposed values of {@link Score}.
 */
public class ScoreTest {
    /**
     * Verifies that valid constructor arguments remain available without transformation.
     */
    @Test
    public void constructor_withValidValues_exposesValues() {
        Score score = new Score(0.301, 50);

        assertEquals(0.301, score.getRawError(), 0.0);
        assertEquals(50, score.getPoints());
    }

    /**
     * Verifies that a score cannot contain an invalid raw error.
     */
    @Test
    public void constructor_withInvalidRawError_throwsUsefulException() {
        double[] invalidErrors = {
                -0.001,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidError : invalidErrors) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Score(invalidError, 50)
            );
            assertEquals("rawError must be finite and non-negative", exception.getMessage());
        }
    }

    /**
     * Verifies that user-facing points remain within their documented range.
     */
    @Test
    public void constructor_withPointsOutsideRange_throwsUsefulException() {
        int[] invalidPoints = {-1, 101};

        for (int invalidPointValue : invalidPoints) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Score(0.301, invalidPointValue)
            );
            assertEquals("points must be between 0 and 100", exception.getMessage());
        }
    }
}
