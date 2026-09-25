package de.christiankorn.giveortake.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

/**
 * Verifies primitive SharedPreferences round-trips for level-specific personal bests.
 */
@RunWith(AndroidJUnit4.class)
public class HighScorePreferencesTest {
    private static final String TEST_PREFERENCES_NAME = "high_score_preferences_test";

    private SharedPreferences sharedPreferences;
    private HighScorePreferences highScorePreferences;

    /** Creates isolated empty preferences before each test. */
    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        sharedPreferences = context.getSharedPreferences(
                TEST_PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        sharedPreferences.edit().clear().commit();
        highScorePreferences = new HighScorePreferences(sharedPreferences);
    }

    /** Removes test values so instrumentation runs remain independent. */
    @After
    public void tearDown() {
        sharedPreferences.edit().clear().commit();
    }

    /** Verifies an unseen level reconstructs an empty domain record. */
    @Test
    public void load_beforeAnySave_returnsEmptyRecord() {
        HighScore highScore = highScorePreferences.load(Level.POINT_ESTIMATES);

        assertEquals(Level.POINT_ESTIMATES, highScore.getLevel());
        assertFalse(highScore.getBestValue().isPresent());
    }

    /** Verifies each primitive field and level identity survive a storage round-trip. */
    @Test
    public void save_thenLoad_reconstructsRecordedHighScore() {
        HighScore expected = HighScore.recorded(Level.CONFIDENCE_INTERVALS, 0.375, 50);

        highScorePreferences.save(expected);

        assertEquals(expected, highScorePreferences.load(Level.CONFIDENCE_INTERVALS));
        assertFalse(highScorePreferences.load(Level.POINT_ESTIMATES)
                .getBestValue().isPresent());
    }

    /** Verifies an absent value cannot erase or manufacture a persisted record. */
    @Test
    public void save_withEmptyRecord_rejectsArgument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> highScorePreferences.save(HighScore.empty(Level.POINT_ESTIMATES))
        );

        assertEquals("highScore must contain a recorded value", exception.getMessage());
    }

    /** Verifies a statistics reset removes records for every answer mode. */
    @Test
    public void clear_afterSeveralSaves_removesEveryHighScore() {
        highScorePreferences.save(HighScore.recorded(Level.POINT_ESTIMATES, 80.0, 10));
        highScorePreferences.save(HighScore.recorded(Level.CONFIDENCE_INTERVALS, 0.4, 10));

        highScorePreferences.clear();

        assertFalse(highScorePreferences.load(Level.POINT_ESTIMATES).getBestValue().isPresent());
        assertFalse(highScorePreferences.load(Level.CONFIDENCE_INTERVALS)
                .getBestValue().isPresent());
    }
}
