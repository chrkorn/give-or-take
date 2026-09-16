package de.christiankorn.giveortake;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the numeric boundaries enforced before a point estimate can be submitted.
 */
public class GuessInputValidatorTest {

    /** Verifies that whitespace alone is not an answer. */
    @Test
    public void rejectsEmptyInput() {
        assertEquals(GuessInputValidator.Error.EMPTY,
                GuessInputValidator.validate("   ", '.'));
    }

    /** Verifies that malformed decimal text is rejected. */
    @Test
    public void rejectsUnparseableInput() {
        assertEquals(GuessInputValidator.Error.UNPARSEABLE,
                GuessInputValidator.validate("12.3.4", '.'));
    }

    /** Verifies that scientific notation stays out of this plain-decimal fallback. */
    @Test
    public void rejectsScientificNotation() {
        assertEquals(GuessInputValidator.Error.UNPARSEABLE,
                GuessInputValidator.validate("3.46e2", '.'));
    }

    /** Verifies the strict positive-value boundary at zero. */
    @Test
    public void rejectsZero() {
        assertEquals(GuessInputValidator.Error.ZERO,
                GuessInputValidator.validate("0.00", '.'));
    }

    /** Verifies that values below the logarithmic scoring domain are rejected. */
    @Test
    public void rejectsNegativeInput() {
        assertEquals(GuessInputValidator.Error.NEGATIVE,
                GuessInputValidator.validate("-1", '.'));
    }

    /** Verifies the upper product boundary. */
    @Test
    public void rejectsValueBeyondMaximum() {
        assertEquals(GuessInputValidator.Error.TOO_LARGE,
                GuessInputValidator.validate("1000000000000000.01", '.'));
    }

    /** Verifies that the documented upper product boundary is inclusive. */
    @Test
    public void acceptsMaximumValue() {
        assertEquals(GuessInputValidator.Error.NONE,
                GuessInputValidator.validate("1000000000000000", '.'));
    }

    /** Verifies that an ordinary positive decimal is accepted. */
    @Test
    public void acceptsPositiveDecimal() {
        assertEquals(GuessInputValidator.Error.NONE,
                GuessInputValidator.validate("346.5", '.'));
    }

    /** Verifies that numeric keyboards using a locale decimal comma remain usable. */
    @Test
    public void acceptsLocaleDecimalSeparator() {
        assertEquals(GuessInputValidator.Error.NONE,
                GuessInputValidator.validate("346,5", ','));
    }
}
