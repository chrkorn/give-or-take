package de.christiankorn.giveortake;

import android.os.Bundle;

import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.QuizSessionSnapshot;
import de.christiankorn.giveortake.core.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates a framework-independent quiz snapshot to small values supported by {@link Bundle}.
 */
final class QuizSessionState {
    private static final int FORMAT_VERSION = 2;
    private static final String KEY_VERSION = "quizSession.version";
    private static final String KEY_INITIAL_QUESTION_COUNT = "quizSession.initialQuestionCount";
    private static final String KEY_PENDING_QUESTION_IDS = "quizSession.pendingQuestionIds";
    private static final String KEY_CURRENT_QUESTION_ID = "quizSession.currentQuestionId";
    private static final String KEY_RAW_ERRORS = "quizSession.rawErrors";
    private static final String KEY_POINTS = "quizSession.points";
    private static final String KEY_LEVEL = "quizSession.level";
    private static final String KEY_CALIBRATION_HIT_COUNT =
            "quizSession.calibrationHitCount";
    private static final String KEY_CALIBRATION_SAMPLE_SIZE =
            "quizSession.calibrationSampleSize";
    private static final String KEY_MEAN_LOG_SCALE_WIDTH =
            "quizSession.meanLogScaleWidth";

    private QuizSessionState() {
    }

    static void write(Bundle bundle, QuizSessionSnapshot snapshot) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        List<Score> scores = snapshot.getScores();
        double[] rawErrors = new double[scores.size()];
        int[] points = new int[scores.size()];
        for (int index = 0; index < scores.size(); index++) {
            Score score = scores.get(index);
            rawErrors[index] = score.getRawError();
            points[index] = score.hasPoints() ? score.getPoints() : -1;
        }

        bundle.putInt(KEY_VERSION, FORMAT_VERSION);
        bundle.putString(KEY_LEVEL, snapshot.getLevel().name());
        bundle.putInt(KEY_INITIAL_QUESTION_COUNT, snapshot.getInitialQuestionCount());
        bundle.putStringArrayList(
                KEY_PENDING_QUESTION_IDS,
                new ArrayList<>(snapshot.getPendingQuestionIds())
        );
        bundle.putString(KEY_CURRENT_QUESTION_ID, snapshot.getCurrentQuestionId());
        bundle.putDoubleArray(KEY_RAW_ERRORS, rawErrors);
        bundle.putIntArray(KEY_POINTS, points);
        bundle.putLong(KEY_CALIBRATION_HIT_COUNT, snapshot.getCalibrationHitCount());
        bundle.putLong(KEY_CALIBRATION_SAMPLE_SIZE, snapshot.getCalibrationSampleSize());
        bundle.putDouble(KEY_MEAN_LOG_SCALE_WIDTH, snapshot.getMeanLogScaleWidth());
    }

    static QuizSessionSnapshot read(Bundle bundle) {
        if (bundle == null || !bundle.containsKey(KEY_VERSION)) {
            return null;
        }
        int version = bundle.getInt(KEY_VERSION);
        if (version != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported saved quiz-session version: " + version);
        }

        String levelName = bundle.getString(KEY_LEVEL);
        ArrayList<String> pendingQuestionIds = bundle.getStringArrayList(
                KEY_PENDING_QUESTION_IDS
        );
        double[] rawErrors = bundle.getDoubleArray(KEY_RAW_ERRORS);
        int[] points = bundle.getIntArray(KEY_POINTS);
        if (levelName == null || pendingQuestionIds == null || rawErrors == null || points == null
                || rawErrors.length != points.length
                || !bundle.containsKey(KEY_CALIBRATION_HIT_COUNT)
                || !bundle.containsKey(KEY_CALIBRATION_SAMPLE_SIZE)
                || !bundle.containsKey(KEY_MEAN_LOG_SCALE_WIDTH)) {
            throw new IllegalStateException("Saved quiz-session state is incomplete");
        }

        Level level;
        try {
            level = Level.valueOf(levelName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported saved quiz level: " + levelName, exception);
        }

        List<Score> scores = new ArrayList<>(rawErrors.length);
        for (int index = 0; index < rawErrors.length; index++) {
            scores.add(points[index] == -1
                    ? new Score(rawErrors[index])
                    : new Score(rawErrors[index], points[index]));
        }
        return new QuizSessionSnapshot(
                level,
                bundle.getInt(KEY_INITIAL_QUESTION_COUNT),
                pendingQuestionIds,
                bundle.getString(KEY_CURRENT_QUESTION_ID),
                scores,
                bundle.getLong(KEY_CALIBRATION_HIT_COUNT),
                bundle.getLong(KEY_CALIBRATION_SAMPLE_SIZE),
                bundle.getDouble(KEY_MEAN_LOG_SCALE_WIDTH)
        );
    }
}
