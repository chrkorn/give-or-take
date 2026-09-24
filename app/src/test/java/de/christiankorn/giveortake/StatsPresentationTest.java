package de.christiankorn.giveortake;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Verifies the statistics screen's pure interpretation and factor conversions. */
public class StatsPresentationTest {

    /** Verifies the dry verdict distinguishes low, near-target, and high coverage. */
    @Test
    public void assessCalibration_usesFivePointTargetBand() {
        assertEquals(
                StatsPresentation.CalibrationVerdict.OVERCONFIDENT,
                StatsPresentation.assessCalibration(0.35)
        );
        assertEquals(
                StatsPresentation.CalibrationVerdict.WELL_CALIBRATED,
                StatsPresentation.assessCalibration(0.02)
        );
        assertEquals(
                StatsPresentation.CalibrationVerdict.WELL_CALIBRATED,
                StatsPresentation.assessCalibration(-0.05)
        );
        assertEquals(
                StatsPresentation.CalibrationVerdict.UNDERCONFIDENT,
                StatsPresentation.assessCalibration(-0.07)
        );
    }

    /** Verifies log-scale means become the scale-free factors shown to the user. */
    @Test
    public void factorFromLogScale_returnsMultiplicativeFactor() {
        assertEquals(1.0, StatsPresentation.factorFromLogScale(0.0), 0.0);
        assertEquals(10.0, StatsPresentation.factorFromLogScale(1.0), 1.0e-12);
        assertEquals(2.0, StatsPresentation.factorFromLogScale(Math.log10(2.0)), 1.0e-12);
    }

    /** Verifies non-finite and negative policy inputs cannot reach number formatting. */
    @Test
    public void presentationCalculations_rejectInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StatsPresentation.assessCalibration(Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> StatsPresentation.factorFromLogScale(-0.01)
        );
    }
}
