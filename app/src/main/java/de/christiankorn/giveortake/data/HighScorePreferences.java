package de.christiankorn.giveortake.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.Level;

/**
 * Persists level-specific personal bests as explicit primitive preference values.
 *
 * <p>The domain object remains independent of Android. Keeping value and answer count beside the
 * level preserves the comparison meaning documented by {@link HighScore} without serialising its
 * private implementation.</p>
 */
public final class HighScorePreferences {
    private static final String PREFERENCES_NAME = "high_scores";
    private static final String KEY_PREFIX = "highScore.";
    private static final String VALUE_SUFFIX = ".value";
    private static final String ANSWER_COUNT_SUFFIX = ".answerCount";

    private final SharedPreferences preferences;

    /**
     * Opens the application-private high-score preferences.
     *
     * @param context context used to access application-private storage
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public HighScorePreferences(Context context) {
        this(preferencesFor(context));
    }

    HighScorePreferences(SharedPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences must not be null");
        }
        this.preferences = preferences;
    }

    /**
     * Loads the personal best for one curriculum level.
     *
     * @param level level whose record should be loaded
     * @return a reconstructed record, or an empty record before the first completed session
     * @throws IllegalArgumentException if {@code level} is {@code null}
     * @throws IllegalStateException if stored fields are incomplete or invalid
     */
    public HighScore load(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }

        String valueKey = key(level, VALUE_SUFFIX);
        String countKey = key(level, ANSWER_COUNT_SUFFIX);
        boolean hasValue = preferences.contains(valueKey);
        boolean hasCount = preferences.contains(countKey);
        if (!hasValue && !hasCount) {
            return HighScore.empty(level);
        }
        if (hasValue != hasCount) {
            throw new IllegalStateException("Stored high score is incomplete for " + level);
        }

        try {
            double value = Double.longBitsToDouble(preferences.getLong(valueKey, 0L));
            int answerCount = preferences.getInt(countKey, 0);
            return HighScore.recorded(level, value, answerCount);
        } catch (ClassCastException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored high score is invalid for " + level, exception);
        }
    }

    /**
     * Saves a recorded personal best.
     *
     * @param highScore non-empty record to persist
     * @throws IllegalArgumentException if the argument is {@code null} or empty
     */
    public void save(HighScore highScore) {
        if (highScore == null) {
            throw new IllegalArgumentException("highScore must not be null");
        }
        OptionalDouble bestValue = highScore.getBestValue();
        OptionalInt answerCount = highScore.getAnsweredQuestionCount();
        if (!bestValue.isPresent() || !answerCount.isPresent()) {
            throw new IllegalArgumentException("highScore must contain a recorded value");
        }

        preferences.edit()
                .putLong(
                        key(highScore.getLevel(), VALUE_SUFFIX),
                        Double.doubleToRawLongBits(bestValue.getAsDouble())
                )
                .putInt(
                        key(highScore.getLevel(), ANSWER_COUNT_SUFFIX),
                        answerCount.getAsInt()
                )
                .apply();
    }

    /** Removes every level-specific personal best as part of a statistics reset. */
    public void clear() {
        preferences.edit().clear().apply();
    }

    private static String key(Level level, String suffix) {
        return KEY_PREFIX + level.name() + suffix;
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
}
