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
    private static final String EXTRA_QUESTION = "feedback.question";
    private static final String EXTRA_GUESS = "feedback.guess";
    private static final String EXTRA_TRUE_VALUE = "feedback.trueValue";
    private static final String EXTRA_UNIT = "feedback.unit";
    private static final String EXTRA_POINTS = "feedback.points";
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
     * @param guess point estimate entered by the user
     * @param answerNumber one-based number of the recorded answer
     * @return an explicit Intent containing all feedback display data
     * @throws IllegalArgumentException if an argument is invalid
     */
    public static Intent createIntent(
            Context context,
            QuizSubmission submission,
            PointGuess guess,
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
        return new Intent(context, FeedbackActivity.class)
                .putExtra(EXTRA_ANSWER_NUMBER, answerNumber)
                .putExtra(EXTRA_QUESTION, question.getPrompt())
                .putExtra(EXTRA_GUESS, guess.getValue())
                .putExtra(EXTRA_TRUE_VALUE, question.getTrueValue())
                .putExtra(EXTRA_UNIT, question.getUnit())
                .putExtra(EXTRA_POINTS, submission.getScore().getPoints())
                .putExtra(EXTRA_CORRECTNESS, submission.getCorrectness().name())
                .putExtra(EXTRA_SOURCE_URL, question.getSourceUrl())
                .putExtra(EXTRA_SOURCE_LABEL, question.getSourceLabel());
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
        String question = requireNonBlankString(intent, EXTRA_QUESTION);
        double guess = requirePositiveDouble(intent, EXTRA_GUESS);
        double trueValue = requirePositiveDouble(intent, EXTRA_TRUE_VALUE);
        String unit = requireNonBlankString(intent, EXTRA_UNIT);
        int points = requireNonNegativeInt(intent, EXTRA_POINTS);
        Correctness correctness = requireCorrectness(intent);
        String sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL);
        String sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL);

        ((TextView) findViewById(R.id.feedback_answer_number)).setText(
                getString(R.string.feedback_answer_number, answerNumber)
        );
        ((TextView) findViewById(R.id.feedback_question)).setText(question);
        ((TextView) findViewById(R.id.feedback_guess)).setText(formatValue(guess, unit));
        ((TextView) findViewById(R.id.feedback_true_value)).setText(
                formatValue(trueValue, unit)
        );
        ((TextView) findViewById(R.id.feedback_score)).setText(
                getResources().getQuantityString(R.plurals.feedback_score, points, points)
        );

        renderBand(correctness);
        renderComparison(guess, trueValue);
        configureSourceLink(sourceUrl, sourceLabel);

        findViewById(R.id.feedback_next_button).setOnClickListener(view -> finish());
    }

    private void renderBand(Correctness correctness) {
        int labelResource;
        int descriptionResource;
        int backgroundResource;
        int contentResource;
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
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(6);
        return getString(R.string.feedback_value_with_unit, numberFormat.format(value), unit);
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
