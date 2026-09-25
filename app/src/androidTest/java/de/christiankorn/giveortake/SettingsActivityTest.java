package de.christiankorn.giveortake;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.data.AssetQuestionBankLoader;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/** Verifies the hand-built settings controls and destructive confirmation boundary. */
@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {
    private static final String SETTINGS_PREFERENCES = "quiz_settings";

    private Context context;
    private List<String> categories;

    /** Clears saved choices and reads the categories supplied by the bundled bank. */
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        QuestionBank bank = new AssetQuestionBankLoader(context.getAssets()).load();
        categories = bank.getCategories();
    }

    /** Removes settings written by a test. */
    @After
    public void tearDown() {
        context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    /** Verifies first-run defaults and that category controls come from the loaded bank. */
    @Test
    public void firstRun_displaysDefaultsAndEveryBankCategory() {
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(
                SettingsActivity.class
        )) {
            onView(withId(R.id.settings_length_10)).check(matches(isChecked()));
            onView(withId(R.id.settings_mode_follow_level)).check(matches(isChecked()));
            for (String category : categories) {
                onView(withText(category)).check(matches(isChecked()));
            }
        }
    }

    /** Verifies ordinary controls persist and the last category cannot be deselected. */
    @Test
    public void edits_persistWhileLastCategoryRemainsSelected() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(
                SettingsActivity.class
        )) {
            onView(withId(R.id.settings_length_5)).perform(click());
            onView(withId(R.id.settings_mode_interval)).perform(click());
            for (int index = 0; index < categories.size() - 1; index++) {
                onView(withText(categories.get(index))).perform(scrollTo(), click());
            }
            String lastCategory = categories.get(categories.size() - 1);
            onView(withText(lastCategory)).perform(scrollTo(), click());
            onView(withText(lastCategory)).check(matches(isChecked()));

            scenario.recreate();

            onView(withId(R.id.settings_length_5)).check(matches(isChecked()));
            onView(withId(R.id.settings_mode_interval)).check(matches(isChecked()));
            onView(withText(lastCategory)).check(matches(isChecked()));
        }
    }

    /** Verifies the destructive action cannot run before a separately confirmed dialog. */
    @Test
    public void resetButton_requiresExplicitConfirmation() {
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(
                SettingsActivity.class
        )) {
            onView(withId(R.id.settings_reset_button)).perform(scrollTo(), click());
            waitForDialog();

            onView(withText(R.string.settings_reset_title)).check(matches(
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
            ));
            onView(withText(R.string.settings_reset_cancel)).perform(click());
        }
    }

    private static void waitForDialog() {
        long deadline = android.os.SystemClock.uptimeMillis() + 2_000L;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                onView(withText(R.string.settings_reset_title)).check(matches(
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
                ));
                return;
            } catch (RuntimeException | AssertionError exception) {
                android.os.SystemClock.sleep(25L);
            }
        }
        throw new AssertionError("Reset confirmation dialog did not appear");
    }
}
