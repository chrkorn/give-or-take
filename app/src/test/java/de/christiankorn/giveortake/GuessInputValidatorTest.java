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

    /**
     * Regression: a positive value too small for a {@code double} used to pass validation and
     * crash the quiz when {@code doubleValue()} returned zero.
     */
    @Test
    public void rejectsPositiveValueThatUnderflowsToZero() {
        String underflowing = "0." + "0".repeat(400) + "1";
        assertEquals(0.0, new java.math.BigDecimal(underflowing).doubleValue(), 0.0);
        assertEquals(GuessInputValidator.Error.TOO_SMALL,
                GuessInputValidator.validate(underflowing, '.'));
    }

    /** Verifies the lower product boundary. */
    @Test
    public void rejectsValueBelowMinimum() {
        assertEquals(GuessInputValidator.Error.TOO_SMALL,
                GuessInputValidator.validate("0.0000000000000009", '.'));
    }

    /** Verifies that the lower product boundary is inclusive and survives double conversion. */
    @Test
    public void acceptsMinimumValue() {
        assertEquals(GuessInputValidator.Error.NONE,
                GuessInputValidator.validate("0.000000000000001", '.'));
        assertEquals(1e-15, GuessInputValidator.MINIMUM_VALUE.doubleValue(), 0.0);
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
