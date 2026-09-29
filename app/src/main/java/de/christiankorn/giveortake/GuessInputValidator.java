package de.christiankorn.giveortake;

import java.math.BigDecimal;

/**
 * Validates locale-formatted numeric text before the UI constructs a core guess.
 *
 * <p>The validator distinguishes user-correctable error states for field feedback while enforcing
 * the finite positive domain required by the core scoring policies. It stays free of Android APIs
 * so the same boundary cases can be exercised in local JVM tests.</p>
 */
final class GuessInputValidator {

    static final BigDecimal MAXIMUM_VALUE = new BigDecimal("1000000000000000");

    private GuessInputValidator() {
    }

    /**
     * Classifies one numeric input without throwing for user-entered text.
     *
     * @param input text entered by the user; must not be {@code null}
     * @param decimalSeparator locale-specific decimal separator
     * @return the first validation error, or {@link Error#NONE}
     */
    static Error validate(String input, char decimalSeparator) {
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
            return Error.EMPTY;
        }
        if (trimmedInput.indexOf('e') >= 0 || trimmedInput.indexOf('E') >= 0) {
            return Error.UNPARSEABLE;
        }

        BigDecimal value;
        try {
            value = new BigDecimal(normaliseDecimalSeparator(trimmedInput, decimalSeparator));
        } catch (NumberFormatException exception) {
            return Error.UNPARSEABLE;
        }

        int sign = value.signum();
        if (sign == 0) {
            return Error.ZERO;
        }
        if (sign < 0) {
            return Error.NEGATIVE;
        }
        if (value.compareTo(MAXIMUM_VALUE) > 0) {
            return Error.TOO_LARGE;
        }
        return Error.NONE;
    }

    /**
     * Replaces a locale-specific decimal separator with the form accepted by {@link BigDecimal}.
     *
     * @param input numeric text; must not be {@code null}
     * @param decimalSeparator locale-specific decimal separator
     * @return normalised text, or the original text when the separator is a period
     */
    static String normaliseDecimalSeparator(String input, char decimalSeparator) {
        if (decimalSeparator == '.') {
            return input;
        }
        return input.replace(decimalSeparator, '.');
    }

    /** Identifies a user-correctable validation result for one numeric field. */
    enum Error {
        NONE,
        EMPTY,
        UNPARSEABLE,
        ZERO,
        NEGATIVE,
        TOO_LARGE
    }
}
