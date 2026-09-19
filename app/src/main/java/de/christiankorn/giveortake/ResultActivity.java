package de.christiankorn.giveortake;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.OptionalDouble;

import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.SessionResult;

/**
 * Summarises one completed quiz and provides routes out of the finished session.
 *
 * <p>The Intent contract contains only primitives and a level name. This keeps Android out of the
 * core model, avoids Java object-serialisation overhead, and gives each field an explicit key that
 * can be evolved independently in a later contract version.</p>
 */
public class ResultActivity extends AppCompatActivity {
    private static final int EXTRA_FORMAT_VERSION = 1;
    private static final String EXTRA_VERSION = "result.version";
    private static final String EXTRA_LEVEL = "result.level";
    private static final String EXTRA_ANSWER_COUNT = "result.answerCount";
    private static final String EXTRA_MEAN_RAW_ERROR = "result.meanRawError";
    private static final String EXTRA_MEAN_POINTS = "result.meanPoints";
    private static final String EXTRA_CORRECT_COUNT = "result.correctCount";
    private static final String EXTRA_CLOSE_COUNT = "result.closeCount";
    private static final String EXTRA_WRONG_COUNT = "result.wrongCount";
    private static final String EXTRA_CALIBRATION_HIT_COUNT = "result.calibrationHitCount";
    private static final String EXTRA_CALIBRATION_SAMPLE_SIZE = "result.calibrationSampleSize";
    private static final String EXTRA_CALIBRATION_MEANINGFUL = "result.calibrationMeaningful";
    private static final String EXTRA_NEW_HIGH_SCORE = "result.newHighScore";
    private static final String EXTRA_PREVIOUS_HIGH_SCORE_PRESENT =
            "result.previousHighScorePresent";
    private static final String EXTRA_PREVIOUS_HIGH_SCORE = "result.previousHighScore";
    private static final String EXTRA_CURRENT_HIGH_SCORE = "result.currentHighScore";

    /**
     * Creates the explicit Intent for a completed result.
     *
     * <p>The factory is the single mapping boundary from compound domain values to primitive
     * extras. The previous record is passed separately so this screen can distinguish a newly set
     * record from an existing personal best without reading or mutating persistence.</p>
     *
     * @param context context used to identify the destination Activity
     * @param sessionResult non-empty completed session to display
     * @param previousHighScore record loaded before this session was considered
     * @return an explicit Intent containing validated, primitive display data
     * @throws IllegalArgumentException if an argument is invalid or belongs to another level
     */
    public static Intent createIntent(
            Context context,
            SessionResult sessionResult,
            HighScore previousHighScore
    ) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (sessionResult == null) {
            throw new IllegalArgumentException("sessionResult must not be null");
        }
        if (sessionResult.getAnsweredQuestionCount() <= 0) {
            throw new IllegalArgumentException("sessionResult must contain at least one answer");
        }
        if (previousHighScore == null) {
            throw new IllegalArgumentException("previousHighScore must not be null");
        }
        if (previousHighScore.getLevel() != sessionResult.getLevel()) {
            throw new IllegalArgumentException(
                    "previousHighScore must belong to the session level"
            );
        }

        OptionalDouble meanRawError = sessionResult.getMeanRawError();
        if (!meanRawError.isPresent()) {
            throw new IllegalArgumentException("sessionResult must contain a mean raw value");
        }
        HighScore currentHighScore = previousHighScore.afterSession(sessionResult);
        boolean newHighScore = !currentHighScore.equals(previousHighScore);
        boolean previousHighScorePresent = previousHighScore.getBestValue().isPresent();

        Intent intent = new Intent(context, ResultActivity.class)
                .putExtra(EXTRA_VERSION, EXTRA_FORMAT_VERSION)
                .putExtra(EXTRA_LEVEL, sessionResult.getLevel().name())
                .putExtra(EXTRA_ANSWER_COUNT, sessionResult.getAnsweredQuestionCount())
                .putExtra(EXTRA_MEAN_RAW_ERROR, meanRawError.getAsDouble())
                .putExtra(EXTRA_CORRECT_COUNT, sessionResult.getCorrectCount())
                .putExtra(EXTRA_CLOSE_COUNT, sessionResult.getCloseCount())
                .putExtra(EXTRA_WRONG_COUNT, sessionResult.getWrongCount())
                .putExtra(
                        EXTRA_CALIBRATION_HIT_COUNT,
                        sessionResult.getCalibrationHitCount()
                )
                .putExtra(
                        EXTRA_CALIBRATION_SAMPLE_SIZE,
                        sessionResult.getCalibrationSampleSize()
                )
                .putExtra(
                        EXTRA_CALIBRATION_MEANINGFUL,
                        sessionResult.hasMeaningfulCalibrationSampleSize()
                )
                .putExtra(EXTRA_NEW_HIGH_SCORE, newHighScore)
                .putExtra(EXTRA_PREVIOUS_HIGH_SCORE_PRESENT, previousHighScorePresent)
                .putExtra(
                        EXTRA_CURRENT_HIGH_SCORE,
                        currentHighScore.getBestValue().getAsDouble()
                );

        if (sessionResult.getMeanPoints().isPresent()) {
            intent.putExtra(EXTRA_MEAN_POINTS, sessionResult.getMeanPoints().getAsDouble());
        }
        if (previousHighScorePresent) {
            intent.putExtra(
                    EXTRA_PREVIOUS_HIGH_SCORE,
                    previousHighScore.getBestValue().getAsDouble()
            );
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);

        View root = findViewById(R.id.result_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        requireFormatVersion(intent);
        Level level = requireLevel(intent);
        int answerCount = requirePositiveInt(intent, EXTRA_ANSWER_COUNT);
        double meanRawError = requireNonNegativeDouble(intent, EXTRA_MEAN_RAW_ERROR);
        double currentHighScore = requireScoreValue(intent, EXTRA_CURRENT_HIGH_SCORE, level);
        boolean newHighScore = requireBoolean(intent, EXTRA_NEW_HIGH_SCORE);
        boolean previousHighScorePresent = requireBoolean(
                intent,
                EXTRA_PREVIOUS_HIGH_SCORE_PRESENT
        );

        renderScore(intent, level, meanRawError);
        renderHighScore(
                intent,
                level,
                currentHighScore,
                newHighScore,
                previousHighScorePresent
        );
        renderModeDetails(intent, level, answerCount);
        configureNavigation();
    }

    private void renderScore(Intent intent, Level level, double meanRawError) {
        TextView scoreView = findViewById(R.id.result_score_value);
        TextView detailView = findViewById(R.id.result_score_detail);
        if (level == Level.POINT_ESTIMATES) {
            double meanPoints = requireScoreValue(intent, EXTRA_MEAN_POINTS, level);
            scoreView.setText(getString(
                    R.string.result_point_score,
                    formatDecimal(meanPoints, 1)
            ));
            double meanFactor = Math.pow(10.0, meanRawError);
            detailView.setText(getString(
                    R.string.result_average_closeness,
                    formatDecimal(meanFactor, 2)
            ));
        } else {
            scoreView.setText(getString(
                    R.string.result_interval_score,
                    formatDecimal(meanRawError, 2)
            ));
            detailView.setText(R.string.result_interval_score_explanation);
        }
    }

    private void renderHighScore(
            Intent intent,
            Level level,
            double currentHighScore,
            boolean newHighScore,
            boolean previousHighScorePresent
    ) {
        MaterialCardView card = findViewById(R.id.result_high_score_card);
        TextView icon = findViewById(R.id.result_high_score_icon);
        TextView label = findViewById(R.id.result_high_score_label);
        TextView detail = findViewById(R.id.result_high_score_detail);

        if (newHighScore) {
            card.setStrokeWidth(getResources().getDimensionPixelSize(
                    R.dimen.result_high_score_new_stroke_width
            ));
            card.setStrokeColor(ContextCompat.getColor(this, R.color.home_primary_button));
            icon.setVisibility(View.VISIBLE);
            label.setText(R.string.result_new_personal_best);
            if (previousHighScorePresent) {
                double previous = requireScoreValue(
                        intent,
                        EXTRA_PREVIOUS_HIGH_SCORE,
                        level
                );
                detail.setText(getString(
                        R.string.result_previous_personal_best,
                        formatHighScore(previous, level)
                ));
            } else {
                detail.setText(R.string.result_first_personal_best);
            }
        } else {
            card.setStrokeWidth(getResources().getDimensionPixelSize(
                    R.dimen.home_card_stroke_width
            ));
            icon.setVisibility(View.GONE);
            label.setText(R.string.result_personal_best);
            detail.setText(formatHighScore(currentHighScore, level));
        }
    }

    private void renderModeDetails(Intent intent, Level level, int answerCount) {
        View bands = findViewById(R.id.result_bands_card);
        View calibration = findViewById(R.id.result_calibration_card);
        if (level == Level.POINT_ESTIMATES) {
            int correctCount = requireNonNegativeInt(intent, EXTRA_CORRECT_COUNT);
            int closeCount = requireNonNegativeInt(intent, EXTRA_CLOSE_COUNT);
            int wrongCount = requireNonNegativeInt(intent, EXTRA_WRONG_COUNT);
            if (correctCount + closeCount + wrongCount != answerCount) {
                throw new IllegalStateException("Result correctness counts do not match answers");
            }
            ((TextView) findViewById(R.id.result_correct_count)).setText(
                    getResources().getQuantityString(
                            R.plurals.result_correct_count,
                            correctCount,
                            correctCount
                    )
            );
            ((TextView) findViewById(R.id.result_close_count)).setText(
                    getResources().getQuantityString(
                            R.plurals.result_close_count,
                            closeCount,
                            closeCount
                    )
            );
            ((TextView) findViewById(R.id.result_wrong_count)).setText(
                    getResources().getQuantityString(
                            R.plurals.result_wrong_count,
                            wrongCount,
                            wrongCount
                    )
            );
            bands.setVisibility(View.VISIBLE);
            calibration.setVisibility(View.GONE);
            return;
        }

        bands.setVisibility(View.GONE);
        calibration.setVisibility(View.VISIBLE);
        long hitCount = requireNonNegativeLong(intent, EXTRA_CALIBRATION_HIT_COUNT);
        long sampleSize = requireNonNegativeLong(intent, EXTRA_CALIBRATION_SAMPLE_SIZE);
        boolean meaningful = requireBoolean(intent, EXTRA_CALIBRATION_MEANINGFUL);
        if (sampleSize != answerCount || hitCount > sampleSize) {
            throw new IllegalStateException("Result calibration counts do not match answers");
        }

        TextView summary = findViewById(R.id.result_calibration_summary);
        TextView explanation = findViewById(R.id.result_calibration_explanation);
        if (meaningful) {
            NumberFormat percentFormat = NumberFormat.getPercentInstance();
            percentFormat.setMaximumFractionDigits(0);
            summary.setText(getResources().getQuantityString(
                    R.plurals.result_calibration_summary,
                    answerCount,
                    hitCount,
                    sampleSize,
                    percentFormat.format((double) hitCount / sampleSize)
            ));
            explanation.setText(R.string.result_calibration_target);
        } else {
            summary.setText(getResources().getQuantityString(
                    R.plurals.result_calibration_insufficient,
                    answerCount,
                    answerCount
            ));
            explanation.setText(R.string.result_calibration_insufficient_explanation);
        }
    }

    private void configureNavigation() {
        findViewById(R.id.result_play_again_button).setOnClickListener(view -> {
            startActivity(new Intent(ResultActivity.this, QuizActivity.class));
            // A fresh quiz replaces this result, so Back from it returns directly to Home.
            finish();
        });
        findViewById(R.id.result_home_button).setOnClickListener(view -> {
            Intent homeIntent = new Intent(ResultActivity.this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(homeIntent);
            finish();
        });
        // Share remains visibly disabled until the later system-share implementation step.
        findViewById(R.id.result_share_button).setEnabled(false);
    }

    private String formatHighScore(double value, Level level) {
        if (level == Level.POINT_ESTIMATES) {
            return getString(R.string.result_point_score, formatDecimal(value, 1));
        }
        return getString(R.string.result_interval_score, formatDecimal(value, 2));
    }

    private String formatDecimal(double value, int maximumFractionDigits) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(maximumFractionDigits);
        return numberFormat.format(value);
    }

    private static void requireFormatVersion(Intent intent) {
        int version = requireInt(intent, EXTRA_VERSION);
        if (version != EXTRA_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported result format version: " + version);
        }
    }

    private static Level requireLevel(Intent intent) {
        String value = intent.getStringExtra(EXTRA_LEVEL);
        if (value == null) {
            throw new IllegalStateException("Missing result extra: " + EXTRA_LEVEL);
        }
        try {
            return Level.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid result level: " + value, exception);
        }
    }

    private static int requirePositiveInt(Intent intent, String key) {
        int value = requireInt(intent, key);
        if (value <= 0) {
            throw new IllegalStateException("Invalid positive result extra: " + key);
        }
        return value;
    }

    private static int requireNonNegativeInt(Intent intent, String key) {
        int value = requireInt(intent, key);
        if (value < 0) {
            throw new IllegalStateException("Invalid non-negative result extra: " + key);
        }
        return value;
    }

    private static int requireInt(Intent intent, String key) {
        if (!intent.hasExtra(key)) {
            throw new IllegalStateException("Missing result extra: " + key);
        }
        return intent.getIntExtra(key, Integer.MIN_VALUE);
    }

    private static long requireNonNegativeLong(Intent intent, String key) {
        if (!intent.hasExtra(key)) {
            throw new IllegalStateException("Missing result extra: " + key);
        }
        long value = intent.getLongExtra(key, -1L);
        if (value < 0L) {
            throw new IllegalStateException("Invalid non-negative result extra: " + key);
        }
        return value;
    }

    private static boolean requireBoolean(Intent intent, String key) {
        if (!intent.hasExtra(key)) {
            throw new IllegalStateException("Missing result extra: " + key);
        }
        return intent.getBooleanExtra(key, false);
    }

    private static double requireScoreValue(Intent intent, String key, Level level) {
        double value = requireNonNegativeDouble(intent, key);
        if (level == Level.POINT_ESTIMATES && value > 100.0) {
            throw new IllegalStateException("Point result extra exceeds 100: " + key);
        }
        return value;
    }

    private static double requireNonNegativeDouble(Intent intent, String key) {
        if (!intent.hasExtra(key)) {
            throw new IllegalStateException("Missing result extra: " + key);
        }
        double value = intent.getDoubleExtra(key, Double.NaN);
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalStateException("Invalid non-negative result extra: " + key);
        }
        return value;
    }
}
