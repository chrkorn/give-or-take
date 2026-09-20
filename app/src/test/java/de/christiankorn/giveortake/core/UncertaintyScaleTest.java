package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Tests the framework-independent logarithmic mapping used by the uncertainty dial.
 */
public class UncertaintyScaleTest {
    private static final double PRECISE_COMPARISON = 1.0e-12;

    /**
     * Verifies that both configured factor endpoints map to the corresponding position endpoints.
     */
    @Test
    public void factorToPositionFraction_forRangeEndpoints_returnsPositionEndpoints() {
        assertEquals(
                0.0,
                UncertaintyScale.factorToPositionFraction(1.2, 1.2, 100.0),
                0.0
        );
        assertEquals(
                1.0,
                UncertaintyScale.factorToPositionFraction(100.0, 1.2, 100.0),
                0.0
        );
    }

    /**
     * Verifies the defining property of the logarithmic scale: equal ratios have equal spacing.
     */
    @Test
    public void factorToPositionFraction_forEqualRatios_returnsEqualDistances() {
        double two = UncertaintyScale.factorToPositionFraction(2.0, 1.0, 16.0);
        double four = UncertaintyScale.factorToPositionFraction(4.0, 1.0, 16.0);
        double eight = UncertaintyScale.factorToPositionFraction(8.0, 1.0, 16.0);

        assertEquals(four - two, eight - four, PRECISE_COMPARISON);
    }

    /**
     * Verifies that converting in both directions recovers ordinary and endpoint factors.
     */
    @Test
    public void positionFractionToFactor_afterFactorMapping_recoversOriginalFactor() {
        double[] factors = {1.2, 2.0, 4.0, 10.0, 20.0, 50.0, 100.0};

        for (double factor : factors) {
            double position = UncertaintyScale.factorToPositionFraction(
                    factor,
                    1.2,
                    100.0
            );

            assertEquals(
                    factor,
                    UncertaintyScale.positionFractionToFactor(position, 1.2, 100.0),
                    PRECISE_COMPARISON
            );
        }
    }

    /**
     * Verifies that dragging past either physical end remains at that endpoint's exact factor.
     */
    @Test
    public void positionFractionToFactor_pastTrackEnds_clampsToFactorEndpoints() {
        assertEquals(
                1.2,
                UncertaintyScale.positionFractionToFactor(0.0, 1.2, 100.0),
                0.0
        );
        assertEquals(
                100.0,
                UncertaintyScale.positionFractionToFactor(1.0, 1.2, 100.0),
                0.0
        );
        assertEquals(
                1.2,
                UncertaintyScale.positionFractionToFactor(-0.25, 1.2, 100.0),
                0.0
        );
        assertEquals(
                100.0,
                UncertaintyScale.positionFractionToFactor(1.25, 1.2, 100.0),
                0.0
        );
    }

    /**
     * Verifies that invalid ranges and values fail at the pure mapping boundary.
     */
    @Test
    public void mapping_withInvalidArguments_throwsUsefulExceptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UncertaintyScale.factorToPositionFraction(2.0, 0.0, 100.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> UncertaintyScale.factorToPositionFraction(101.0, 1.2, 100.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> UncertaintyScale.positionFractionToFactor(Double.NaN, 1.2, 100.0)
        );
    }
}
