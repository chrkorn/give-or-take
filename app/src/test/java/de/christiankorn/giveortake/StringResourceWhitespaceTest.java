package de.christiankorn.giveortake;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/** Verifies string resources whose surrounding whitespace is part of their meaning. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class StringResourceWhitespaceTest {

    /**
     * Regression: aapt strips leading whitespace from unquoted resources, so the suffix appended
     * to an abandoned session's band counts rendered as "0 wrong· ended early".
     */
    @Test
    public void abandonedSuffix_keepsItsLeadingSpace() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(" · ended early", context.getString(R.string.stats_abandoned_suffix));
    }
}
