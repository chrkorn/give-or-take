package de.christiankorn.giveortake.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import de.christiankorn.giveortake.core.Level;

import static org.junit.Assert.assertEquals;

/** Exercises defaults, validation, and round-trips for quiz-session preferences. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class QuizSettingsTest {
    private static final String TEST_PREFERENCES_NAME = "quiz_settings_test";

    private SharedPreferences preferences;
    private QuizSettings settings;
    private Set<String> availableCategories;

    /** Creates isolated preferences and a stable test question-bank vocabulary. */
    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
                TEST_PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        preferences.edit().clear().commit();
        settings = new QuizSettings(preferences);
        availableCategories = new LinkedHashSet<>(Arrays.asList("Areas", "Populations"));
    }

    /** Clears test values after every test. */
    @After
    public void tearDown() {
        preferences.edit().clear().commit();
    }

    /** Verifies a first run uses ten questions, follows the level, and includes every category. */
    @Test
    public void load_beforeAnySave_returnsSensibleDefaults() {
        QuizSettings.Snapshot snapshot = settings.load(availableCategories);

        assertEquals(10, snapshot.getSessionLength());
        assertEquals(QuizSettings.AnswerMode.FOLLOW_LEVEL, snapshot.getAnswerMode());
        assertEquals(availableCategories, snapshot.getSelectedCategories());
        assertEquals(
                Level.CONFIDENCE_INTERVALS,
                snapshot.resolveLevel(Level.CONFIDENCE_INTERVALS)
        );
    }

    /** Verifies all three choices survive a preference round-trip. */
    @Test
    public void save_thenLoad_reconstructsCompleteSnapshot() {
        Set<String> selected = new LinkedHashSet<>(Arrays.asList("Areas"));
        settings.setSessionLength(20);
        settings.setAnswerMode(QuizSettings.AnswerMode.CONFIDENCE_INTERVAL);
        settings.setSelectedCategories(selected);

        QuizSettings.Snapshot snapshot = settings.load(availableCategories);

        assertEquals(20, snapshot.getSessionLength());
        assertEquals(
                QuizSettings.AnswerMode.CONFIDENCE_INTERVAL,
                snapshot.getAnswerMode()
        );
        assertEquals(selected, snapshot.getSelectedCategories());
        assertEquals(Level.CONFIDENCE_INTERVALS, snapshot.resolveLevel(Level.POINT_ESTIMATES));
    }

    /** Verifies stale categories from an updated bank cannot leave the question pool empty. */
    @Test
    public void load_whenEveryStoredCategoryWasRemoved_fallsBackToAllAvailable() {
        settings.setSelectedCategories(new LinkedHashSet<>(Arrays.asList("Removed category")));

        QuizSettings.Snapshot snapshot = settings.load(availableCategories);

        assertEquals(availableCategories, snapshot.getSelectedCategories());
    }
}
