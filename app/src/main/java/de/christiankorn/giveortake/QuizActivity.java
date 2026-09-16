package de.christiankorn.giveortake;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;

/**
 * Displays one point-estimation question and validates the player's numeric answer.
 *
 * <p>This first quiz screen deliberately stops at a logging placeholder. A later increment will
 * pass the validated value to the scoring engine and advance the session.</p>
 */
public class QuizActivity extends AppCompatActivity {

    static final String EXTRA_CURRENT_QUESTION = "currentQuestion";
    static final String EXTRA_QUESTION_COUNT = "questionCount";
    static final String EXTRA_QUESTION_PROMPT = "questionPrompt";
    static final String EXTRA_QUESTION_UNIT = "questionUnit";

    private static final String TAG = "QuizActivity";

    private TextInputLayout answerInputLayout;
    private TextInputEditText answerInput;
    private MaterialButton submitButton;
    private char decimalSeparator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quiz);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.quiz_scroll), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView questionCounter = findViewById(R.id.question_counter);
        TextView questionPrompt = findViewById(R.id.question_prompt);
        answerInputLayout = findViewById(R.id.answer_input_layout);
        answerInput = findViewById(R.id.answer_input);
        submitButton = findViewById(R.id.submit_button);
        decimalSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();

        int currentQuestion = getIntent().getIntExtra(EXTRA_CURRENT_QUESTION, 3);
        int questionCount = getIntent().getIntExtra(EXTRA_QUESTION_COUNT, 10);
        String prompt = getIntent().getStringExtra(EXTRA_QUESTION_PROMPT);
        String unit = getIntent().getStringExtra(EXTRA_QUESTION_UNIT);

        questionCounter.setText(getString(R.string.quiz_question_counter, currentQuestion,
                questionCount));
        questionPrompt.setText(prompt == null ? getString(R.string.quiz_default_prompt) : prompt);
        answerInputLayout.setSuffixText(unit == null ? getString(R.string.quiz_default_unit) : unit);

        submitButton.setEnabled(false);
        answerInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // No work is needed until the edit has completed.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                // No work is needed until the edit has completed.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                updateValidation(true);
            }
        });

        submitButton.setOnClickListener(view -> submitAnswer());
        answerInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && submitButton.isEnabled()) {
                submitButton.performClick();
                return true;
            }
            return false;
        });
    }

    private boolean updateValidation(boolean showError) {
        GuessInputValidator.Error error = GuessInputValidator.validate(
                answerInput.getText() == null ? "" : answerInput.getText().toString(),
                decimalSeparator
        );
        boolean isValid = error == GuessInputValidator.Error.NONE;
        submitButton.setEnabled(isValid);
        answerInputLayout.setError(showError && !isValid ? getErrorMessage(error) : null);
        return isValid;
    }

    private String getErrorMessage(GuessInputValidator.Error error) {
        switch (error) {
            case EMPTY:
                return getString(R.string.quiz_error_empty);
            case UNPARSEABLE:
                return getString(R.string.quiz_error_unparseable);
            case ZERO:
                return getString(R.string.quiz_error_zero);
            case NEGATIVE:
                return getString(R.string.quiz_error_negative);
            case TOO_LARGE:
                return getString(R.string.quiz_error_too_large);
            case NONE:
            default:
                return null;
        }
    }

    private void submitAnswer() {
        if (!updateValidation(true)) {
            return;
        }

        String normalisedInput = GuessInputValidator.normaliseDecimalSeparator(
                answerInput.getText().toString().trim(), decimalSeparator
        );
        BigDecimal estimate = new BigDecimal(normalisedInput);
        Log.i(TAG, "Validated estimate submitted: " + estimate.toPlainString());
    }
}
