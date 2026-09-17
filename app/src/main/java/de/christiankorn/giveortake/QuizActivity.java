package de.christiankorn.giveortake;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import de.christiankorn.giveortake.core.CorrectnessClassifier;
import de.christiankorn.giveortake.core.LogRelativeScore;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.core.QuizSession;
import de.christiankorn.giveortake.core.QuizSessionSnapshot;
import de.christiankorn.giveortake.core.QuizSubmission;
import de.christiankorn.giveortake.data.AssetQuestionBankLoader;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Random;

/**
 * Runs a point-estimation quiz while delegating scoring and scheduling to the core session.
 */
public class QuizActivity extends AppCompatActivity {
    private static final int SESSION_LENGTH = 10;

    private TextView questionCounter;
    private TextView questionPrompt;
    private TextInputLayout answerInputLayout;
    private TextInputEditText answerInput;
    private MaterialButton submitButton;
    private char decimalSeparator;
    private QuizSession quizSession;

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

        questionCounter = findViewById(R.id.question_counter);
        questionPrompt = findViewById(R.id.question_prompt);
        answerInputLayout = findViewById(R.id.answer_input_layout);
        answerInput = findViewById(R.id.answer_input);
        submitButton = findViewById(R.id.submit_button);
        decimalSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();

        QuestionBank questionBank = new AssetQuestionBankLoader(getAssets()).load();
        quizSession = createOrRestoreSession(questionBank, savedInstanceState);
        if (quizSession.isComplete()) {
            finish();
            return;
        }
        renderCurrentQuestion();

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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        QuizSessionState.write(outState, quizSession.snapshot());
    }

    private QuizSession createOrRestoreSession(
            QuestionBank questionBank,
            Bundle savedInstanceState
    ) {
        QuizSessionSnapshot snapshot = QuizSessionState.read(savedInstanceState);
        if (snapshot == null) {
            return new QuizSession(
                    questionBank.getQuestions(),
                    SESSION_LENGTH,
                    new Random(),
                    new LogRelativeScore(),
                    new CorrectnessClassifier()
            );
        }

        try {
            return QuizSession.restore(
                    questionBank.getQuestions(),
                    snapshot,
                    new LogRelativeScore(),
                    new CorrectnessClassifier()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Saved quiz session is incompatible with the bundled question bank",
                    exception
            );
        }
    }

    private void renderCurrentQuestion() {
        Question question = quizSession.getCurrentQuestion();
        questionCounter.setText(getString(
                R.string.quiz_question_progress,
                quizSession.getAnsweredQuestionCount(),
                quizSession.getRemainingQuestionCount()
        ));
        questionPrompt.setText(question.getPrompt());
        answerInputLayout.setSuffixText(question.getUnit());
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
        PointGuess guess = new PointGuess(estimate.doubleValue());
        QuizSubmission submission = quizSession.submit(guess);

        if (submission.isSessionComplete()) {
            finish();
            return;
        }

        answerInput.setText("");
        answerInputLayout.setError(null);
        submitButton.setEnabled(false);
        renderCurrentQuestion();
    }
}
