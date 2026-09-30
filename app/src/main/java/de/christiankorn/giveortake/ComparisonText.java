package de.christiankorn.giveortake;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Chooses how a near-miss is worded on the feedback screen, independent of Android so the
 * thresholds can be covered by local JVM tests.
 *
 * <p>Multiplicative factors are shown with two fraction digits, which cannot resolve a factor
 * below 1.005: those printed as "about 1× too low", a sentence that contradicts itself. Below
 * {@link #PERCENT_BELOW_FACTOR} the deviation is therefore expressed as a percentage, which keeps
 * the direction honest and the size readable.</p>
 */
final class ComparisonText {

    /** Factors below this are worded as a percentage rather than as a multiple. */
    static final double PERCENT_BELOW_FACTOR = 1.01;

    /** Percent deviations below this are reported as negligible rather than rounded to zero. */
    static final double NEGLIGIBLE_PERCENT = 0.005;

    private ComparisonText() {
    }

    /**
     * Returns whether a factor is too close to one for the multiplicative wording.
     *
     * @param factor multiplicative distance between estimate and truth, at least {@code 1.0}
     * @return {@code true} when the percentage wording should be used
     */
    static boolean prefersPercent(double factor) {
        return factor < PERCENT_BELOW_FACTOR;
    }

    /**
     * Converts a multiplicative factor to the percentage by which the estimate missed.
     *
     * @param factor multiplicative distance, at least {@code 1.0}
     * @return {@code (factor - 1) * 100}
     */
    static double percentDeviation(double factor) {
        return (factor - 1.0) * 100.0;
    }

    /**
     * Returns whether a factor's percentage deviation would round to zero when displayed.
     *
     * @param factor multiplicative distance, at least {@code 1.0}
     * @return {@code true} when the miss should be reported as negligible
     */
    static boolean isNegligible(double factor) {
        return percentDeviation(factor) < NEGLIGIBLE_PERCENT;
    }

    /**
     * Formats a percentage deviation with at most two fraction digits.
     *
     * @param factor multiplicative distance, at least {@code 1.0}
     * @param locale locale used for digit and separator formatting
     * @return the number part of the percentage, without the percent sign
     */
    static String formatPercentDeviation(double factor, Locale locale) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(percentDeviation(factor));
    }
}
