package de.christiankorn.giveortake;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Keeps small, framework-independent interpretation rules out of {@link StatsActivity} so they
 * can be covered by local JVM tests.
 */
final class StatsPresentation {
    static final double WELL_CALIBRATED_TOLERANCE = 0.05;

    enum CalibrationVerdict {
        OVERCONFIDENT,
        WELL_CALIBRATED,
        UNDERCONFIDENT
    }

    private StatsPresentation() {
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

    /**
     * Formats a mean-closeness factor with the two fraction digits the result screen uses.
     *
     * <p>The statistics list used its shared one-digit format here, so a session the result
     * screen summarised as "within a factor of 1.02" appeared as "×1" in the statistics.</p>
     *
     * @param factor multiplicative factor, at least {@code 1.0}
     * @param locale locale used for digit and separator formatting
     * @return the formatted number without the multiplication sign
     */
    static String formatClosenessFactor(double factor, Locale locale) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(factor);
    }

    static double factorFromLogScale(double logScaleValue) {
        if (!Double.isFinite(logScaleValue) || logScaleValue < 0.0) {
            throw new IllegalArgumentException("log-scale value must be finite and non-negative");
        }
        return Math.pow(10.0, logScaleValue);
    }
}
