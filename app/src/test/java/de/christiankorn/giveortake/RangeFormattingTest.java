package de.christiankorn.giveortake;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/** Verifies the shared rounding of confidence-interval bounds. */
public class RangeFormattingTest {

    /**
     * Regression: switching to direct entry pre-filled the raw dial bounds, for example
     * {@code 2224.2888379530405}, while the readout above showed 2,224.
     */
    @Test
    public void formatForEditing_matchesTheDisplayedRounding() {
        assertEquals("2224", RangeFormatting.formatForEditing(2224.2888379530405, '.'));
        assertEquals("34816", RangeFormatting.formatForEditing(34815.622269303014, '.'));
        assertEquals("2,224", RangeFormatting.formatForDisplay(2224.2888379530405, Locale.US));
    }

    /** Verifies bounds keep three significant digits and the locale separator. */
    @Test
    public void smallBounds_keepTwoSignificantDigits() {
        assertEquals("0.00123", RangeFormatting.formatForEditing(0.00123456, '.'));
        assertEquals("12,3", RangeFormatting.formatForEditing(12.3456, ','));
        assertEquals("0.001", RangeFormatting.formatForEditing(0.001, '.'));
    }

    /** Verifies the edit text parses back as a valid guess. */
    @Test
    public void editText_passesValidation() {
        assertEquals(GuessInputValidator.Error.NONE, GuessInputValidator.validate(
                RangeFormatting.formatForEditing(101.104038, '.'),
                '.'
        ));
    }

    /** Regression: feedback showed dial bounds with six decimals, 101.104038–1,582.528285. */
    @Test
    public void formatForDisplay_roundsFeedbackBounds() {
        assertEquals("101", RangeFormatting.formatForDisplay(101.104038, Locale.US));
        assertEquals("1,583", RangeFormatting.formatForDisplay(1582.528285, Locale.US));
    }
}
