package de.christiankorn.giveortake;

/** Keeps small, testable interpretation rules out of {@link StatsActivity}. */
final class StatsPresentation {
    static final double WELL_CALIBRATED_TOLERANCE = 0.05;

    enum CalibrationVerdict {
        OVERCONFIDENT,
        WELL_CALIBRATED,
        UNDERCONFIDENT
    }

    private StatsPresentation() {
        // This class groups pure presentation calculations and is not stateful.
    }

    static CalibrationVerdict assessCalibration(double nominalMinusEmpiricalGap) {
        if (!Double.isFinite(nominalMinusEmpiricalGap)) {
            throw new IllegalArgumentException("coverage gap must be finite");
        }
        if (nominalMinusEmpiricalGap > WELL_CALIBRATED_TOLERANCE) {
            return CalibrationVerdict.OVERCONFIDENT;
        }
        if (nominalMinusEmpiricalGap < -WELL_CALIBRATED_TOLERANCE) {
            return CalibrationVerdict.UNDERCONFIDENT;
        }
        return CalibrationVerdict.WELL_CALIBRATED;
    }

    static double factorFromLogScale(double logScaleValue) {
        if (!Double.isFinite(logScaleValue) || logScaleValue < 0.0) {
            throw new IllegalArgumentException("log-scale value must be finite and non-negative");
        }
        return Math.pow(10.0, logScaleValue);
    }
}
