package de.christiankorn.giveortake;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.data.QuizSettings;

import static org.junit.Assert.assertTrue;

/** Keeps fixture installation and failure-safe cleanup out of the report's readable UI journey. */
final class SessionFixtureRule implements TestRule {
    /** Wraps ActivityScenarioRule so even a launch/assertion failure restores the previous inputs. */
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            /** Installs the fixture before evaluating the nested Activity rule. */
            @Override
            public void evaluate() throws Throwable {
                GiveOrTakeApplication app = ApplicationProvider.getApplicationContext();
                QuestionBank bank;
                // androidTest assets belong to the test APK, not the target application's assets.
                try (Reader reader = new InputStreamReader(
                        InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                                .open("session-questions.json"), StandardCharsets.UTF_8)) {
                    bank = QuestionBank.fromJson(reader);
                }
                Map<SharedPreferences, Map<String, ?>> savedPreferences = new LinkedHashMap<>();
                for (String name : new String[]{"quiz_settings", "high_scores"}) {
                    SharedPreferences preferences = app.getSharedPreferences(name, Context.MODE_PRIVATE);
                    savedPreferences.put(preferences, preferences.getAll());
                }
                QuizDependencies[] previous = new QuizDependencies[1];
                Locale previousLocale = Locale.getDefault();
                try {
                    Locale.setDefault(Locale.US);
                    for (SharedPreferences preferences : savedPreferences.keySet()) {
                        assertTrue("Could not clear fixture preferences", preferences.edit().clear().commit());
                    }
                    QuizSettings settings = new QuizSettings(app);
                    settings.setSessionLength(5);
                    settings.setAnswerMode(QuizSettings.AnswerMode.POINT_ESTIMATE);
                    settings.setSelectedCategories(Collections.singleton("Session test"));
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                            previous[0] = app.replaceQuizDependencies(new QuizDependencies() {
                                /** Supplies the same immutable bank to settings resolution and quiz creation. */
                                @Override
                                public QuestionBank loadQuestionBank() {
                                    return bank;
                                }

                                /** Prevents a previous session from consuming this session's random sequence. */
                                @Override
                                public Random newSessionRandom() {
                                    return new Random(42L);
                                }
                            }));
                    base.evaluate();
                } finally {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                        // The scenario owns Home; a failure can leave Feedback or Result above it.
                        for (Stage stage : Stage.values()) {
                            if (stage == Stage.DESTROYED) {
                                continue;
                            }
                            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry
                                    .getInstance().getActivitiesInStage(stage))) {
                                if (activity.getPackageName().equals(app.getPackageName())) {
                                    activity.finish();
                                }
                            }
                        }
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                        if (previous[0] != null) {
                            app.replaceQuizDependencies(previous[0]);
                        }
                    });
                    Locale.setDefault(previousLocale);
                    for (Map.Entry<SharedPreferences, Map<String, ?>> entry : savedPreferences.entrySet()) {
                        restorePreferences(entry.getKey(), entry.getValue());
                    }
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static void restorePreferences(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Set) {
                editor.putStringSet(key, (Set<String>) value);
            } else {
                throw new AssertionError("Unsupported preference type for " + key);
            }
        }
        assertTrue("Could not restore pre-test preferences", editor.commit());
    }
}
