package de.christiankorn.giveortake.ui;

import android.content.Context;
import android.view.ContextThemeWrapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import de.christiankorn.giveortake.R;
import de.christiankorn.giveortake.core.IntervalGuess;

import static org.junit.Assert.assertEquals;

/** Verifies that the dial's configured factors keep their decimal values. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class UncertaintyDialFactorTest {

    /**
     * Regression: the default minimum factor {@code 1.2f} was widened to 1.2000000476837158, so a
     * best guess of 27,000,000 showed a range of "22,499,999 – 32,400,001".
     */
    @Test
    public void defaultMinimumFactor_isExactlyOnePointTwo() {
        Context context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                R.style.Theme_GiveOrTake
        );
        UncertaintyDial dial = new UncertaintyDial(context);

        assertEquals(1.2, dial.getFactor(), 0.0);
        IntervalGuess interval = IntervalGuess.fromBestGuessAndFactor(27_000_000.0, dial.getFactor());
        assertEquals(22_500_000.0, interval.getLowerBound(), 1e-6);
        assertEquals(32_400_000.0, interval.getUpperBound(), 1e-6);
    }

    /** Verifies float attributes widen to their written decimal, not their binary neighbour. */
    @Test
    public void decimalValueOf_keepsTheWrittenDecimal() {
        assertEquals(1.2, UncertaintyDial.decimalValueOf(1.2f), 0.0);
        assertEquals(100.0, UncertaintyDial.decimalValueOf(100.0f), 0.0);
        assertEquals(1.5, UncertaintyDial.decimalValueOf(1.5f), 0.0);
    }
}
