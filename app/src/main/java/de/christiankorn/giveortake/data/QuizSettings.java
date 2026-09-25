package de.christiankorn.giveortake.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import de.christiankorn.giveortake.core.Level;

/** Persists the choices that are copied into each newly started quiz session. */
public final class QuizSettings {
    /** Default number of distinct questions requested for a session. */
    public static final int DEFAULT_SESSION_LENGTH = 10;

    private static final String PREFERENCES_NAME = "quiz_settings";
    private static final String KEY_SESSION_LENGTH = "sessionLength";
    private static final String KEY_ANSWER_MODE = "answerMode";
    private static final String KEY_SELECTED_CATEGORIES = "selectedCategories";

    private final SharedPreferences preferences;

    /** Identifies how a new session chooses its answer UI. */
    public enum AnswerMode {
        /** Always use point estimates, without changing curriculum progress. */
        POINT_ESTIMATE,
        /** Always use 90 percent intervals, without changing curriculum progress. */
        CONFIDENCE_INTERVAL,
        /** Use the answer mode belonging to the player's current curriculum level. */
        FOLLOW_LEVEL
    }

    /**
     * Opens the application-private settings preferences.
     *
     * @param context context used to access application-private storage
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public QuizSettings(Context context) {
        this(preferencesFor(context));
    }

    QuizSettings(SharedPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences must not be null");
        }
        this.preferences = preferences;
    }

    /**
     * Loads a validated snapshot for the categories in the current question bank.
     *
     * <p>Before the first save, every available category is selected. If an application update
     * removes all previously selected categories, falling back to all current categories keeps a
     * usable question pool instead of failing to start a session.</p>
     *
     * @param availableCategories non-empty categories exposed by the loaded question bank
     * @return immutable choices ready to copy into a new session
     * @throws IllegalArgumentException if the category set is null, empty, or contains blanks
     */
    public Snapshot load(Set<String> availableCategories) {
        LinkedHashSet<String> available = requireCategories(availableCategories);
        int sessionLength = preferences.getInt(
                KEY_SESSION_LENGTH,
                DEFAULT_SESSION_LENGTH
        );
        if (!isSupportedSessionLength(sessionLength)) {
            sessionLength = DEFAULT_SESSION_LENGTH;
        }

        AnswerMode answerMode = readAnswerMode();
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (!preferences.contains(KEY_SELECTED_CATEGORIES)) {
            selected.addAll(available);
        } else {
            Set<String> stored = preferences.getStringSet(
                    KEY_SELECTED_CATEGORIES,
                    Collections.emptySet()
            );
            for (String category : available) {
                if (stored.contains(category)) {
                    selected.add(category);
                }
            }
            if (selected.isEmpty()) {
                selected.addAll(available);
            }
        }
        return new Snapshot(sessionLength, answerMode, selected);
    }

    /**
     * Saves one of the supported session lengths.
     *
     * @param sessionLength 5, 10, or 20 questions
     * @throws IllegalArgumentException if the value is unsupported
     */
    public void setSessionLength(int sessionLength) {
        if (!isSupportedSessionLength(sessionLength)) {
            throw new IllegalArgumentException("sessionLength must be 5, 10, or 20");
        }
        preferences.edit().putInt(KEY_SESSION_LENGTH, sessionLength).apply();
    }

    /**
     * Saves the answer-mode choice used by subsequently started sessions.
     *
     * @param answerMode non-null answer-mode choice
     * @throws IllegalArgumentException if {@code answerMode} is {@code null}
     */
    public void setAnswerMode(AnswerMode answerMode) {
        if (answerMode == null) {
            throw new IllegalArgumentException("answerMode must not be null");
        }
        preferences.edit().putString(KEY_ANSWER_MODE, answerMode.name()).apply();
    }

    /**
     * Saves the non-empty category filter used by subsequently started sessions.
     *
     * @param selectedCategories selected non-blank question-bank categories
     * @throws IllegalArgumentException if the set is null, empty, or contains blanks
     */
    public void setSelectedCategories(Set<String> selectedCategories) {
        preferences.edit()
                .putStringSet(
                        KEY_SELECTED_CATEGORIES,
                        requireCategories(selectedCategories)
                )
                .apply();
    }

    /** Reports whether a value is one of the three session-length choices. */
    public static boolean isSupportedSessionLength(int sessionLength) {
        return sessionLength == 5 || sessionLength == 10 || sessionLength == 20;
    }

    private AnswerMode readAnswerMode() {
        String stored = preferences.getString(
                KEY_ANSWER_MODE,
                AnswerMode.FOLLOW_LEVEL.name()
        );
        if (stored == null) {
            return AnswerMode.FOLLOW_LEVEL;
        }
        try {
            return AnswerMode.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return AnswerMode.FOLLOW_LEVEL;
        }
    }

    private static LinkedHashSet<String> requireCategories(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("categories must not be null or empty");
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String category : categories) {
            if (category == null || category.trim().isEmpty()) {
                throw new IllegalArgumentException("categories must not contain blanks");
            }
            copy.add(category);
        }
        return copy;
    }

    private static SharedPreferences preferencesFor(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }

    /** Immutable settings copied into a newly created quiz Intent. */
    public static final class Snapshot {
        private final int sessionLength;
        private final AnswerMode answerMode;
        private final Set<String> selectedCategories;

        private Snapshot(
                int sessionLength,
                AnswerMode answerMode,
                Set<String> selectedCategories
        ) {
            this.sessionLength = sessionLength;
            this.answerMode = answerMode;
            this.selectedCategories = Collections.unmodifiableSet(
                    new LinkedHashSet<>(selectedCategories)
            );
        }

        /** Returns the requested number of initial questions. */
        public int getSessionLength() {
            return sessionLength;
        }

        /** Returns the stored answer-mode choice. */
        public AnswerMode getAnswerMode() {
            return answerMode;
        }

        /** Returns the immutable, non-empty category filter. */
        public Set<String> getSelectedCategories() {
            return selectedCategories;
        }

        /**
         * Resolves an explicit answer-mode override or follows the supplied curriculum level.
         *
         * @param currentLevel player's current curriculum level
         * @return level whose answer UI the new session should use
         * @throws IllegalArgumentException if {@code currentLevel} is {@code null}
         */
        public Level resolveLevel(Level currentLevel) {
            if (currentLevel == null) {
                throw new IllegalArgumentException("currentLevel must not be null");
            }
            if (answerMode == AnswerMode.POINT_ESTIMATE) {
                return Level.POINT_ESTIMATES;
            }
            if (answerMode == AnswerMode.CONFIDENCE_INTERVAL) {
                return Level.CONFIDENCE_INTERVALS;
            }
            return currentLevel;
        }
    }
}
