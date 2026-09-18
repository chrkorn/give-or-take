package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Tests directed, multiplicative comparisons used by answer feedback.
 */
public class EstimateComparisonTest {

    /** Verifies that equal values have no direction and a factor of one. */
    @Test
    public void between_equalValues_reportsExact() {
        EstimateComparison comparison = EstimateComparison.between(632.0, 632.0);

        assertEquals(EstimateComparison.Direction.EXACT, comparison.getDirection());
        assertEquals(1.0, comparison.getFactor(), 0.0);
    }

    /** Verifies that an overestimate retains its direction and multiplicative distance. */
    @Test
    public void between_sixfoldOverestimate_reportsHigh() {
        EstimateComparison comparison = EstimateComparison.between(600.0, 100.0);

        assertEquals(EstimateComparison.Direction.HIGH, comparison.getDirection());
        assertEquals(6.0, comparison.getFactor(), 0.0000001);
    }

    /** Verifies that the inverse underestimate has the same factor in the other direction. */
    @Test
    public void between_sixfoldUnderestimate_reportsLow() {
        EstimateComparison comparison = EstimateComparison.between(100.0, 600.0);

        assertEquals(EstimateComparison.Direction.LOW, comparison.getDirection());
        assertEquals(6.0, comparison.getFactor(), 0.0000001);
    }

    /** Verifies that invalid values fail at this domain boundary. */
    @Test
    public void between_nonPositiveEstimate_failsClearly() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EstimateComparison.between(0.0, 100.0)
        );

        assertEquals("estimate must be finite and greater than zero", exception.getMessage());
    }
}
