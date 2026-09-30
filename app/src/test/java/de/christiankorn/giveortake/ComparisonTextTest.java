package de.christiankorn.giveortake;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the wording thresholds for near-exact estimates on the feedback screen. */
public class ComparisonTextTest {

    /**
     * Regression: 420 against a true 420.5 was reported as "about 1× too low" because a factor of
     * 1.0012 rounds to 1 at two fraction digits.
     */
    @Test
    public void nearExactEstimate_isWordedAsPercentage() {
        double factor = 420.5 / 420.0;
        assertTrue(ComparisonText.prefersPercent(factor));
        assertEquals("0.12", ComparisonText.formatPercentDeviation(factor, Locale.US));
    }

    /** Verifies that ordinary misses keep the multiplicative wording the tests already pin. */
    @Test
    public void factorAtThreshold_keepsMultiplicativeWording() {
        assertFalse(ComparisonText.prefersPercent(ComparisonText.PERCENT_BELOW_FACTOR));
        assertFalse(ComparisonText.prefersPercent(2.0));
    }

    /** Verifies that a deviation too small to display is reported as negligible, not as 0%. */
    @Test
    public void tinyDeviation_isNegligible() {
        assertTrue(ComparisonText.isNegligible(1.00001));
        assertFalse(ComparisonText.isNegligible(1.0001));
    }

    /** Verifies locale-aware digits for the percentage. */
    @Test
    public void percentage_usesLocaleDecimalSeparator() {
        assertEquals("0,48", ComparisonText.formatPercentDeviation(4.22 / 4.2, Locale.GERMANY));
    }
}
