package de.christiankorn.giveortake;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Rounds confidence-interval bounds to the precision a player can reason about, independent of
 * Android so the rule can be covered by local JVM tests.
 *
 * <p>A dial-derived bound is a best guess divided or multiplied by a continuous factor, so its
 * exact value carries meaningless digits ({@code 2224.2888379530405}). The live readout already
 * rounded them away; the pre-filled direct-entry fields and the feedback screen did not. All three
 * now share this one rule: three significant digits, but never fewer than the whole number
 * (2,224 rather than 2,220).</p>
 */
final class RangeFormatting {

    private static final int MAXIMUM_FRACTION_DIGITS = 340;

    private RangeFormatting() {
    }

    /**
     * Returns the number of fraction digits shown for a positive bound.
     *
     * @param value positive, finite bound
     * @return fraction digits to display
     */
    static int significantFractionDigits(double value) {
        int integerDigits = value >= 1.0
                ? (int) Math.floor(Math.log10(value)) + 1
                : 0;
        if (integerDigits > 0) {
            return Math.max(0, 3 - integerDigits);
        }
        return Math.min(MAXIMUM_FRACTION_DIGITS, 2 - (int) Math.floor(Math.log10(value)));
    }

    /**
     * Formats a bound for display with grouping separators.
     *
     * @param value positive, finite bound
     * @param locale locale used for digits and separators
     * @return the rounded, grouped value
     */
    static String formatForDisplay(double value, Locale locale) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setGroupingUsed(true);
        numberFormat.setRoundingMode(RoundingMode.HALF_EVEN);
        numberFormat.setMaximumFractionDigits(significantFractionDigits(value));
        return numberFormat.format(value);
    }

    /**
     * Formats a bound for an editable field: the display rounding without grouping separators,
     * so the text parses back through {@link GuessInputValidator} unchanged.
     *
     * @param value positive, finite bound
     * @param decimalSeparator locale-specific decimal separator
     * @return plain decimal text
     */
    static String formatForEditing(double value, char decimalSeparator) {
        String plainValue = BigDecimal.valueOf(value)
                .setScale(significantFractionDigits(value), RoundingMode.HALF_EVEN)
                .stripTrailingZeros()
                .toPlainString();
        return decimalSeparator == '.' ? plainValue : plainValue.replace('.', decimalSeparator);
    }
}
