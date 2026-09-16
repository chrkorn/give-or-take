package de.christiankorn.giveortake;

import java.math.BigDecimal;

final class GuessInputValidator {

    static final BigDecimal MAXIMUM_VALUE = new BigDecimal("1000000000000000");

    private GuessInputValidator() {
    }

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

    static String normaliseDecimalSeparator(String input, char decimalSeparator) {
        if (decimalSeparator == '.') {
            return input;
        }
        return input.replace(decimalSeparator, '.');
    }

    enum Error {
        NONE,
        EMPTY,
        UNPARSEABLE,
        ZERO,
        NEGATIVE,
        TOO_LARGE
    }
}
