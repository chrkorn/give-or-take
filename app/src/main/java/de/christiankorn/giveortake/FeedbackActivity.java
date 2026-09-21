package de.christiankorn.giveortake;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;

import de.christiankorn.giveortake.core.Correctness;
import de.christiankorn.giveortake.core.EstimateComparison;
import de.christiankorn.giveortake.core.Guess;
import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.core.QuizSubmission;

/**
 * Explains the result of one submitted estimate in human-readable terms.
 *
 * <p>The Activity receives immutable display values through Intent extras. Android can retain
 * those values when recreating the Activity, whereas a static field could lose data after process
 * death and could accidentally retain stale screen state.</p>
 */
public class FeedbackActivity extends AppCompatActivity {
    private static final String EXTRA_ANSWER_NUMBER = "feedback.answerNumber";
    private static final String EXTRA_LEVEL = "feedback.level";
    private static final String EXTRA_QUESTION = "feedback.question";
    private static final String EXTRA_GUESS = "feedback.guess";
    private static final String EXTRA_LOWER_BOUND = "feedback.lowerBound";
    private static final String EXTRA_UPPER_BOUND = "feedback.upperBound";
    private static final String EXTRA_TRUE_VALUE = "feedback.trueValue";
    private static final String EXTRA_UNIT = "feedback.unit";
    private static final String EXTRA_POINTS = "feedback.points";
    private static final String EXTRA_RAW_SCORE = "feedback.rawScore";
    private static final String EXTRA_CORRECTNESS = "feedback.correctness";
    private static final String EXTRA_SOURCE_URL = "feedback.sourceUrl";
    private static final String EXTRA_SOURCE_LABEL = "feedback.sourceLabel";

    /**
     * Creates the explicit Intent that displays one completed submission.
     *
     * <p>Only primitives and strings cross the Activity boundary. This keeps the navigation
     * contract small and avoids making framework-independent domain objects Parcelable.</p>
     *
     * @param context context used to identify the destination Activity
     * @param submission scored submission to display
     * @param guess point estimate or confidence interval entered by the user
     * @param answerNumber one-based number of the recorded answer
     * @return an explicit Intent containing all feedback display data
     * @throws IllegalArgumentException if an argument is invalid
     */
    public static Intent createIntent(
            Context context,
            QuizSubmission submission,
            Guess guess,
            int answerNumber
    ) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (submission == null) {
            throw new IllegalArgumentException("submission must not be null");
        }
        if (guess == null) {
            throw new IllegalArgumentException("guess must not be null");
        }
        if (answerNumber <= 0) {
            throw new IllegalArgumentException("answerNumber must be greater than zero");
        }

        Question question = submission.getAnsweredQuestion();
        Intent intent = new Intent(context, FeedbackActivity.class)
                .putExtra(EXTRA_ANSWER_NUMBER, answerNumber)
                .putExtra(EXTRA_QUESTION, question.getPrompt())
                .putExtra(EXTRA_TRUE_VALUE, question.getTrueValue())
                .putExtra(EXTRA_UNIT, question.getUnit())
                .putExtra(EXTRA_RAW_SCORE, submission.getScore().getRawError())
                .putExtra(EXTRA_CORRECTNESS, submission.getCorrectness().name())
                .putExtra(EXTRA_SOURCE_URL, question.getSourceUrl())
                .putExtra(EXTRA_SOURCE_LABEL, question.getSourceLabel());
        if (guess instanceof PointGuess) {
            if (!submission.getScore().hasPoints()) {
                throw new IllegalArgumentException("point feedback requires a points score");
            }
            return intent
                    .putExtra(EXTRA_LEVEL, Level.POINT_ESTIMATES.name())
                    .putExtra(EXTRA_GUESS, ((PointGuess) guess).getValue())
                    .putExtra(EXTRA_POINTS, submission.getScore().getPoints());
        }
        if (guess instanceof IntervalGuess) {
            if (submission.getScore().hasPoints()) {
                throw new IllegalArgumentException(
                        "confidence-interval feedback requires a raw loss"
                );
            }
            IntervalGuess intervalGuess = (IntervalGuess) guess;
            return intent
                    .putExtra(EXTRA_LEVEL, Level.CONFIDENCE_INTERVALS.name())
                    .putExtra(EXTRA_LOWER_BOUND, intervalGuess.getLowerBound())
                    .putExtra(EXTRA_UPPER_BOUND, intervalGuess.getUpperBound());
        }
        throw new IllegalArgumentException("unsupported guess type");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_feedback);

        View root = findViewById(R.id.feedback_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        int answerNumber = requirePositiveInt(intent, EXTRA_ANSWER_NUMBER);
        Level level = requireLevel(intent);
        String question = requireNonBlankString(intent, EXTRA_QUESTION);
        double trueValue = requirePositiveDouble(intent, EXTRA_TRUE_VALUE);
        String unit = requireNonBlankString(intent, EXTRA_UNIT);
        Correctness correctness = requireCorrectness(intent);
        String sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL);
        String sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL);

        ((TextView) findViewById(R.id.feedback_answer_number)).setText(
                getString(R.string.feedback_answer_number, answerNumber)
        );
        ((TextView) findViewById(R.id.feedback_question)).setText(question);
        ((TextView) findViewById(R.id.feedback_true_value)).setText(
                formatValue(trueValue, unit)
        );

        if (level == Level.POINT_ESTIMATES) {
            renderPointFeedback(intent, trueValue, unit, correctness);
        } else {
            renderIntervalFeedback(intent, trueValue, unit, correctness);
        }
        configureSourceLink(sourceUrl, sourceLabel);

        findViewById(R.id.feedback_next_button).setOnClickListener(view -> finish());
    }

    private void renderPointFeedback(
            Intent intent,
            double trueValue,
            String unit,
            Correctness correctness
    ) {
        double guess = requirePositiveDouble(intent, EXTRA_GUESS);
        int points = requireNonNegativeInt(intent, EXTRA_POINTS);
        ((TextView) findViewById(R.id.feedback_guess)).setText(formatValue(guess, unit));
        ((TextView) findViewById(R.id.feedback_score)).setText(
                getResources().getQuantityString(R.plurals.feedback_score, points, points)
        );
        renderBand(correctness, Level.POINT_ESTIMATES);
        renderComparison(guess, trueValue);
    }

    private void renderIntervalFeedback(
            Intent intent,
            double trueValue,
            String unit,
            Correctness correctness
    ) {
        double lowerBound = requirePositiveDouble(intent, EXTRA_LOWER_BOUND);
        double upperBound = requirePositiveDouble(intent, EXTRA_UPPER_BOUND);
        if (lowerBound > upperBound) {
            throw new IllegalStateException("Invalid confidence-interval feedback bounds");
        }
        double rawScore = requireNonNegativeDouble(intent, EXTRA_RAW_SCORE);

        ((TextView) findViewById(R.id.feedback_guess_label)).setText(
                R.string.feedback_interval_label
        );
        ((TextView) findViewById(R.id.feedback_guess)).setText(
                getString(
                        R.string.feedback_interval_value,
                        formatNumber(lowerBound),
                        formatNumber(upperBound),
                        unit
                )
        );
        ((TextView) findViewById(R.id.feedback_score)).setText(getString(
                R.string.feedback_interval_loss,
                formatNumber(rawScore)
        ));
        renderBand(correctness, Level.CONFIDENCE_INTERVALS);

        TextView comparisonView = findViewById(R.id.feedback_comparison);
        if (correctness == Correctness.CORRECT) {
            comparisonView.setText(R.string.feedback_interval_contained_comparison);
        } else if (trueValue < lowerBound) {
            comparisonView.setText(R.string.feedback_interval_above_truth_comparison);
        } else {
            comparisonView.setText(R.string.feedback_interval_below_truth_comparison);
        }
    }

    private void renderBand(Correctness correctness, Level level) {
        int labelResource;
        int descriptionResource;
        int backgroundResource;
        int contentResource;
        if (level == Level.CONFIDENCE_INTERVALS) {
            if (correctness == Correctness.CLOSE) {
                throw new IllegalStateException("Interval feedback cannot have a close outcome");
            }
            boolean contained = correctness == Correctness.CORRECT;
            labelResource = contained
                    ? R.string.feedback_interval_contained
                    : R.string.feedback_interval_missed;
            descriptionResource = contained
                    ? R.string.feedback_interval_contained_description
                    : R.string.feedback_interval_missed_description;
            backgroundResource = contained
                    ? R.color.feedback_correct_background
                    : R.color.feedback_wrong_background;
            contentResource = contained
                    ? R.color.feedback_correct_content
                    : R.color.feedback_wrong_content;
            applyBandStyle(
                    labelResource,
                    descriptionResource,
                    backgroundResource,
                    contentResource
            );
            return;
        }
        switch (correctness) {
            case CORRECT:
                labelResource = R.string.feedback_band_correct;
                descriptionResource = R.string.feedback_band_correct_description;
                backgroundResource = R.color.feedback_correct_background;
                contentResource = R.color.feedback_correct_content;
                break;
            case CLOSE:
                labelResource = R.string.feedback_band_close;
                descriptionResource = R.string.feedback_band_close_description;
                backgroundResource = R.color.feedback_close_background;
                contentResource = R.color.feedback_close_content;
                break;
            case WRONG:
                labelResource = R.string.feedback_band_wrong;
                descriptionResource = R.string.feedback_band_wrong_description;
                backgroundResource = R.color.feedback_wrong_background;
                contentResource = R.color.feedback_wrong_content;
                break;
            default:
                throw new IllegalStateException("Unsupported correctness band: " + correctness);
        }

        applyBandStyle(labelResource, descriptionResource, backgroundResource, contentResource);
    }

    private void applyBandStyle(
            int labelResource,
            int descriptionResource,
            int backgroundResource,
            int contentResource
    ) {
        int contentColor = ContextCompat.getColor(this, contentResource);
        MaterialCardView bandCard = findViewById(R.id.feedback_band_card);
        bandCard.setCardBackgroundColor(ContextCompat.getColor(this, backgroundResource));
        bandCard.setStrokeColor(contentColor);

        TextView label = findViewById(R.id.feedback_band_label);
        label.setText(labelResource);
        label.setTextColor(contentColor);
        TextView description = findViewById(R.id.feedback_band_description);
        description.setText(descriptionResource);
        description.setTextColor(contentColor);
    }

    private void renderComparison(double guess, double trueValue) {
        EstimateComparison comparison = EstimateComparison.between(guess, trueValue);
        TextView comparisonView = findViewById(R.id.feedback_comparison);
        switch (comparison.getDirection()) {
            case EXACT:
                comparisonView.setText(R.string.feedback_comparison_exact);
                break;
            case HIGH:
                comparisonView.setText(getString(
                        R.string.feedback_comparison_high,
                        formatFactor(comparison.getFactor())
                ));
                break;
            case LOW:
                comparisonView.setText(getString(
                        R.string.feedback_comparison_low,
                        formatFactor(comparison.getFactor())
                ));
                break;
            default:
                throw new IllegalStateException(
                        "Unsupported comparison direction: " + comparison.getDirection()
                );
        }
    }

    private void configureSourceLink(String sourceUrl, String sourceLabel) {
        MaterialButton sourceButton = findViewById(R.id.feedback_source_button);
        if (isBlank(sourceUrl)) {
            sourceButton.setVisibility(View.GONE);
            return;
        }

        String displayLabel = isBlank(sourceLabel)
                ? Uri.parse(sourceUrl).getHost()
                : sourceLabel.trim();
        if (isBlank(displayLabel)) {
            displayLabel = sourceUrl;
        }
        sourceButton.setText(getString(R.string.feedback_source, displayLabel));
        sourceButton.setContentDescription(
                getString(R.string.feedback_source_content_description, displayLabel)
        );
        sourceButton.setOnClickListener(view -> openSource(sourceUrl));
    }

    private void openSource(String sourceUrl) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl));
        try {
            startActivity(browserIntent);
        } catch (ActivityNotFoundException exception) {
            Snackbar.make(
                    findViewById(R.id.feedback_root),
                    R.string.feedback_source_unavailable,
                    Snackbar.LENGTH_LONG
            ).show();
        }
    }

    private String formatValue(double value, String unit) {
        return getString(R.string.feedback_value_with_unit, formatNumber(value), unit);
    }

    private String formatNumber(double value) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(6);
        return numberFormat.format(value);
    }

    private String formatFactor(double factor) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(factor);
    }

    private static int requirePositiveInt(Intent intent, String key) {
        int value = intent.getIntExtra(key, -1);
        if (value <= 0) {
            throw new IllegalStateException("Missing or invalid feedback extra: " + key);
        }
        return value;
    }

    private static int requireNonNegativeInt(Intent intent, String key) {
        int value = intent.getIntExtra(key, -1);
        if (value < 0) {
            throw new IllegalStateException("Missing or invalid feedback extra: " + key);
        }
        return value;
    }

    private static double requirePositiveDouble(Intent intent, String key) {
        double value = intent.getDoubleExtra(key, Double.NaN);
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException("Missing or invalid feedback extra: " + key);
        }
        return value;
    }

    private static double requireNonNegativeDouble(Intent intent, String key) {
        double value = intent.getDoubleExtra(key, Double.NaN);
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalStateException("Missing or invalid feedback extra: " + key);
        }
        return value;
    }

    private static String requireNonBlankString(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        if (isBlank(value)) {
            throw new IllegalStateException("Missing or invalid feedback extra: " + key);
        }
        return value;
    }

    private static Correctness requireCorrectness(Intent intent) {
        String value = requireNonBlankString(intent, EXTRA_CORRECTNESS);
        try {
            return Correctness.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Missing or invalid feedback extra: " + EXTRA_CORRECTNESS,
                    exception
            );
        }
    }

    private static Level requireLevel(Intent intent) {
        String value = requireNonBlankString(intent, EXTRA_LEVEL);
        try {
            return Level.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Missing or invalid feedback extra: " + EXTRA_LEVEL,
                    exception
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
