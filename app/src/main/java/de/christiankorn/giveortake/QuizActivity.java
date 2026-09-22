package de.christiankorn.giveortake;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import de.christiankorn.giveortake.core.Guess;
import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.IntervalScore;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.LogRelativeScore;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.core.QuizSession;
import de.christiankorn.giveortake.core.QuizSessionSnapshot;
import de.christiankorn.giveortake.core.QuizSubmission;
import de.christiankorn.giveortake.core.ScoringPolicy;
import de.christiankorn.giveortake.core.SessionResult;
import de.christiankorn.giveortake.data.AssetQuestionBankLoader;
import de.christiankorn.giveortake.data.AnswerDraft;
import de.christiankorn.giveortake.data.HighScorePreferences;
import de.christiankorn.giveortake.data.QuizHistorySession;
import de.christiankorn.giveortake.data.QuizHistoryStore;
import de.christiankorn.giveortake.ui.UncertaintyDial;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Random;
import java.util.UUID;

/**
 * Runs a numerical-estimation quiz and owns its point or interval answer controls.
 *
 * <p>Both answer modes produce a core {@code Guess} and delegate scoring, calibration, and
 * scheduling to the current session. Visibility swaps keep the two XML input groups explicit
 * while sharing the question, progress, and submission controls.</p>
 */
public class QuizActivity extends AppCompatActivity {
    private static final int SESSION_LENGTH = 10;
    private static final String EXTRA_LEVEL = "quiz.level";
    private static final String STATE_RANGE_ENTRY_MODE = "quiz.rangeEntryMode";
    private static final String STATE_DIRECT_BOUNDS_INITIALISED =
            "quiz.directBoundsInitialised";
    private static final String STATE_HISTORY_SESSION_ID = "quiz.historySessionId";
    private static final String STATE_HISTORY_SESSION_TOKEN = "quiz.historySessionToken";
    private static final String STATE_HISTORY_STARTED_AT = "quiz.historyStartedAt";

    private enum RangeEntryMode {
        FACTOR_DIAL,
        DIRECT_BOUNDS
    }

    private final ActivityResultLauncher<Intent> feedbackLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> continueAfterFeedback()
            );

    private TextView questionCounter;
    private TextView questionPrompt;
    private TextInputLayout answerInputLayout;
    private TextInputEditText answerInput;
    private MaterialButton submitButton;
    private View pointAnswerGroup;
    private View rangeAnswerGroup;
    private View dialEntryGroup;
    private View directBoundsGroup;
    private TextInputLayout bestGuessInputLayout;
    private TextInputEditText bestGuessInput;
    private TextInputLayout lowerBoundInputLayout;
    private TextInputEditText lowerBoundInput;
    private TextInputLayout upperBoundInputLayout;
    private TextInputEditText upperBoundInput;
    private UncertaintyDial uncertaintyDial;
    private TextView rangeReadout;
    private TextView rangeExplanation;
    private MaterialButton rangeEntryModeButton;
    private char decimalSeparator;
    private QuizSession quizSession;
    private QuizHistorySession historySession;
    private String historySessionToken;
    private long historyStartedAtEpochMillis;
    private boolean historySessionEnded;
    private Level level;
    private RangeEntryMode rangeEntryMode = RangeEntryMode.FACTOR_DIAL;
    private boolean directBoundsInitialised;
    private boolean updatingRangeInputs;

    /**
     * Creates an explicit quiz Intent for a selected answer mode.
     *
     * <p>The point-estimate flow remains the default for callers that construct an Intent
     * directly. Normal callers should use this factory so the selected level remains an explicit
     * navigation input rather than hidden mutable Activity state.</p>
     *
     * @param context context used to identify this Activity
     * @param level answer mode to display
     * @return an explicit Intent carrying the selected level
     */
    public static Intent createIntent(Context context, Level level) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        return new Intent(context, QuizActivity.class).putExtra(EXTRA_LEVEL, level.name());
    }

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
        pointAnswerGroup = findViewById(R.id.point_answer_group);
        rangeAnswerGroup = findViewById(R.id.range_answer_group);
        dialEntryGroup = findViewById(R.id.dial_entry_group);
        directBoundsGroup = findViewById(R.id.direct_bounds_group);
        bestGuessInputLayout = findViewById(R.id.best_guess_input_layout);
        bestGuessInput = findViewById(R.id.best_guess_input);
        lowerBoundInputLayout = findViewById(R.id.lower_bound_input_layout);
        lowerBoundInput = findViewById(R.id.lower_bound_input);
        upperBoundInputLayout = findViewById(R.id.upper_bound_input_layout);
        upperBoundInput = findViewById(R.id.upper_bound_input);
        uncertaintyDial = findViewById(R.id.uncertainty_dial);
        rangeReadout = findViewById(R.id.range_readout);
        rangeExplanation = findViewById(R.id.range_explanation);
        rangeEntryModeButton = findViewById(R.id.range_entry_mode_button);
        decimalSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        level = readLevel();
        restoreRangeEntryState(savedInstanceState);

        QuestionBank questionBank = new AssetQuestionBankLoader(getAssets()).load();
        quizSession = createOrRestoreSession(questionBank, savedInstanceState);
        restoreOrStartHistorySession(savedInstanceState);
        if (quizSession.isComplete()) {
            showCompletedResult();
            return;
        }
        renderCurrentQuestion();

        configureAnswerInput();
    }

    private Level readLevel() {
        String levelName = getIntent().getStringExtra(EXTRA_LEVEL);
        if (levelName == null) {
            return Level.POINT_ESTIMATES;
        }
        try {
            return Level.valueOf(levelName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported quiz level: " + levelName, exception);
        }
    }

    private void restoreRangeEntryState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String modeName = savedInstanceState.getString(STATE_RANGE_ENTRY_MODE);
        if (modeName != null) {
            try {
                rangeEntryMode = RangeEntryMode.valueOf(modeName);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unsupported saved range entry mode", exception);
            }
        }
        directBoundsInitialised = savedInstanceState.getBoolean(
                STATE_DIRECT_BOUNDS_INITIALISED,
                false
        );
    }

    private void configureAnswerInput() {
        submitButton.setEnabled(false);
        if (level == Level.CONFIDENCE_INTERVALS) {
            pointAnswerGroup.setVisibility(View.GONE);
            rangeAnswerGroup.setVisibility(View.VISIBLE);
            submitButton.setText(R.string.range_submit);
            configureRangeInput();
            return;
        }

        pointAnswerGroup.setVisibility(View.VISIBLE);
        rangeAnswerGroup.setVisibility(View.GONE);
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

    private void configureRangeInput() {
        TextWatcher rangeTextWatcher = new TextWatcher() {
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
                if (!updatingRangeInputs) {
                    updateRangePreview(true);
                }
            }
        };
        bestGuessInput.addTextChangedListener(rangeTextWatcher);
        lowerBoundInput.addTextChangedListener(rangeTextWatcher);
        upperBoundInput.addTextChangedListener(rangeTextWatcher);
        uncertaintyDial.setOnFactorChangeListener(
                (dial, factor, fromUser) -> updateRangePreview(false)
        );
        rangeEntryModeButton.setOnClickListener(view -> toggleRangeEntryMode());
        submitButton.setOnClickListener(view -> submitIntervalAnswer());
        upperBoundInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && submitButton.isEnabled()) {
                submitButton.performClick();
                return true;
            }
            return false;
        });
        renderRangeEntryMode();
        updateRangePreview(false);
    }

    private void toggleRangeEntryMode() {
        if (rangeEntryMode == RangeEntryMode.FACTOR_DIAL) {
            initialiseDirectBoundsFromDial();
            rangeEntryMode = RangeEntryMode.DIRECT_BOUNDS;
        } else {
            rangeEntryMode = RangeEntryMode.FACTOR_DIAL;
        }
        renderRangeEntryMode();
        updateRangePreview(false);
    }

    private void initialiseDirectBoundsFromDial() {
        if (directBoundsInitialised) {
            return;
        }
        IntervalGuess interval = buildDialInterval(false);
        if (interval == null) {
            return;
        }

        runWithoutRangeCallbacks(() -> {
            lowerBoundInput.setText(formatEditableValue(interval.getLowerBound()));
            upperBoundInput.setText(formatEditableValue(interval.getUpperBound()));
        });
        directBoundsInitialised = true;
    }

    private void runWithoutRangeCallbacks(Runnable update) {
        if (updatingRangeInputs) {
            return;
        }
        updatingRangeInputs = true;
        try {
            update.run();
        } finally {
            updatingRangeInputs = false;
        }
    }

    private void renderRangeEntryMode() {
        boolean usesDial = rangeEntryMode == RangeEntryMode.FACTOR_DIAL;
        dialEntryGroup.setVisibility(usesDial ? View.VISIBLE : View.GONE);
        directBoundsGroup.setVisibility(usesDial ? View.GONE : View.VISIBLE);
        rangeEntryModeButton.setText(
                usesDial ? R.string.range_enter_bounds_directly : R.string.range_use_factor_dial
        );
    }

    private void updateRangePreview(boolean showErrors) {
        IntervalGuess interval = rangeEntryMode == RangeEntryMode.FACTOR_DIAL
                ? buildDialInterval(showErrors)
                : buildDirectInterval(showErrors);
        boolean isValid = interval != null;
        submitButton.setEnabled(isValid);
        if (!isValid) {
            rangeReadout.setText(R.string.range_readout_unavailable);
            rangeExplanation.setText(R.string.range_explanation_unavailable);
            uncertaintyDial.setDerivedRangeText(null);
            return;
        }

        Question question = quizSession.getCurrentQuestion();
        String formattedRange = getString(
                R.string.range_readout,
                formatRangeValue(interval.getLowerBound()),
                formatRangeValue(interval.getUpperBound()),
                question.getUnit()
        );
        rangeReadout.setText(formattedRange);
        uncertaintyDial.setDerivedRangeText(formattedRange);
        if (rangeEntryMode == RangeEntryMode.FACTOR_DIAL) {
            double bestGuess = parseInput(bestGuessInput);
            rangeExplanation.setText(getString(
                    R.string.range_factor_explanation,
                    formatFactor(uncertaintyDial.getFactor()),
                    formatRangeValue(bestGuess),
                    question.getUnit()
            ));
        } else {
            rangeExplanation.setText(R.string.range_direct_explanation);
        }
    }

    private IntervalGuess buildDialInterval(boolean showErrors) {
        GuessInputValidator.Error error = validateInput(bestGuessInput);
        bestGuessInputLayout.setError(
                showErrors && error != GuessInputValidator.Error.NONE
                        ? getErrorMessage(error)
                        : null
        );
        if (error != GuessInputValidator.Error.NONE) {
            return null;
        }
        return IntervalGuess.fromBestGuessAndFactor(
                parseInput(bestGuessInput),
                uncertaintyDial.getFactor()
        );
    }

    private IntervalGuess buildDirectInterval(boolean showErrors) {
        GuessInputValidator.Error lowerError = validateInput(lowerBoundInput);
        GuessInputValidator.Error upperError = validateInput(upperBoundInput);
        lowerBoundInputLayout.setError(
                showErrors && lowerError != GuessInputValidator.Error.NONE
                        ? getErrorMessage(lowerError)
                        : null
        );
        upperBoundInputLayout.setError(
                showErrors && upperError != GuessInputValidator.Error.NONE
                        ? getErrorMessage(upperError)
                        : null
        );
        if (lowerError != GuessInputValidator.Error.NONE
                || upperError != GuessInputValidator.Error.NONE) {
            return null;
        }

        double lowerBound = parseInput(lowerBoundInput);
        double upperBound = parseInput(upperBoundInput);
        if (lowerBound > upperBound) {
            lowerBoundInputLayout.setError(
                    showErrors ? getString(R.string.range_error_inverted) : null
            );
            return null;
        }
        return new IntervalGuess(lowerBound, upperBound);
    }

    private GuessInputValidator.Error validateInput(TextInputEditText input) {
        return GuessInputValidator.validate(
                input.getText() == null ? "" : input.getText().toString(),
                decimalSeparator
        );
    }

    private double parseInput(TextInputEditText input) {
        String text = input.getText() == null ? "" : input.getText().toString();
        return new BigDecimal(GuessInputValidator.normaliseDecimalSeparator(
                text.trim(),
                decimalSeparator
        )).doubleValue();
    }

    private String formatRangeValue(double value) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setGroupingUsed(true);
        numberFormat.setMaximumFractionDigits(significantFractionDigits(value));
        return numberFormat.format(value);
    }

    private int significantFractionDigits(double value) {
        int integerDigits = value >= 1.0
                ? (int) Math.floor(Math.log10(value)) + 1
                : 0;
        if (integerDigits > 0) {
            return Math.max(0, 3 - integerDigits);
        }
        return Math.min(340, 2 - (int) Math.floor(Math.log10(value)));
    }

    private String formatFactor(double factor) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(factor);
    }

    private String formatEditableValue(double value) {
        String plainValue = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return decimalSeparator == '.' ? plainValue : plainValue.replace('.', decimalSeparator);
    }

    private void submitIntervalAnswer() {
        IntervalGuess interval = getCurrentIntervalGuess(true);
        if (interval == null) {
            updateRangePreview(true);
            return;
        }
        QuizSubmission submission = quizSession.submit(interval);
        persistAcceptedAnswer(submission, interval);

        runWithoutRangeCallbacks(() -> {
            bestGuessInput.setText("");
            lowerBoundInput.setText("");
            upperBoundInput.setText("");
        });
        directBoundsInitialised = false;
        bestGuessInputLayout.setError(null);
        lowerBoundInputLayout.setError(null);
        upperBoundInputLayout.setError(null);
        updateRangePreview(false);
        feedbackLauncher.launch(FeedbackActivity.createIntent(
                this,
                submission,
                interval,
                quizSession.getAnsweredQuestionCount()
        ));
    }

    IntervalGuess getCurrentIntervalGuess(boolean showErrors) {
        return rangeEntryMode == RangeEntryMode.FACTOR_DIAL
                ? buildDialInterval(showErrors)
                : buildDirectInterval(showErrors);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        QuizSessionState.write(outState, quizSession.snapshot());
        outState.putString(STATE_RANGE_ENTRY_MODE, rangeEntryMode.name());
        outState.putBoolean(STATE_DIRECT_BOUNDS_INITIALISED, directBoundsInitialised);
        outState.putString(STATE_HISTORY_SESSION_TOKEN, historySessionToken);
        outState.putLong(STATE_HISTORY_STARTED_AT, historyStartedAtEpochMillis);
        outState.putLong(STATE_HISTORY_SESSION_ID, historySession.getSessionId());
    }

    private QuizSession createOrRestoreSession(
            QuestionBank questionBank,
            Bundle savedInstanceState
    ) {
        ScoringPolicy scoringPolicy = level == Level.POINT_ESTIMATES
                ? new LogRelativeScore()
                : new IntervalScore();
        QuizSessionSnapshot snapshot = QuizSessionState.read(savedInstanceState);
        if (snapshot == null) {
            return new QuizSession(
                    questionBank.getQuestions(),
                    SESSION_LENGTH,
                    new Random(),
                    level,
                    scoringPolicy
            );
        }

        if (snapshot.getLevel() != level) {
            throw new IllegalStateException("Saved quiz level does not match the Intent level");
        }

        try {
            return QuizSession.restore(
                    questionBank.getQuestions(),
                    snapshot,
                    scoringPolicy
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
        bestGuessInputLayout.setSuffixText(question.getUnit());
        lowerBoundInputLayout.setSuffixText(question.getUnit());
        upperBoundInputLayout.setSuffixText(question.getUnit());
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
        persistAcceptedAnswer(submission, guess);

        answerInput.setText("");
        answerInputLayout.setError(null);
        submitButton.setEnabled(false);
        feedbackLauncher.launch(FeedbackActivity.createIntent(
                this,
                submission,
                guess,
                quizSession.getAnsweredQuestionCount()
        ));
    }

    private void continueAfterFeedback() {
        if (quizSession.isComplete()) {
            showCompletedResult();
            return;
        }
        renderCurrentQuestion();
    }

    private void showCompletedResult() {
        SessionResult sessionResult = quizSession.getResult();
        HighScorePreferences highScorePreferences = new HighScorePreferences(this);
        HighScore previousHighScore = highScorePreferences.load(sessionResult.getLevel());
        HighScore updatedHighScore = previousHighScore.afterSession(sessionResult);
        if (!updatedHighScore.equals(previousHighScore)) {
            highScorePreferences.save(updatedHighScore);
        }

        startActivity(ResultActivity.createIntent(
                this,
                sessionResult,
                previousHighScore
        ));
        // Removing the completed quiz means Back from Result returns to Home, never stale input.
        finish();
    }

    private void restoreOrStartHistorySession(Bundle savedInstanceState) {
        QuizHistoryStore historyStore = ((GiveOrTakeApplication) getApplication())
                .getQuizHistoryStore();
        if (savedInstanceState == null) {
            historySessionToken = UUID.randomUUID().toString();
            historyStartedAtEpochMillis = System.currentTimeMillis();
            historySession = historyStore.startSession(
                    historySessionToken,
                    level,
                    quizSession.getInitialQuestionCount(),
                    historyStartedAtEpochMillis
            );
            historySessionEnded = false;
            return;
        }

        historySessionToken = savedInstanceState.getString(STATE_HISTORY_SESSION_TOKEN);
        if (historySessionToken == null || historySessionToken.trim().isEmpty()) {
            throw new IllegalStateException("Saved quiz is missing its history session token");
        }
        if (!savedInstanceState.containsKey(STATE_HISTORY_STARTED_AT)) {
            throw new IllegalStateException("Saved quiz is missing its history start time");
        }
        historyStartedAtEpochMillis = savedInstanceState.getLong(STATE_HISTORY_STARTED_AT);
        long savedSessionId = savedInstanceState.getLong(STATE_HISTORY_SESSION_ID, 0L);
        historySessionEnded = quizSession.isComplete();
        historySession = historyStore.restoreSession(
                historySessionToken,
                level,
                quizSession.getInitialQuestionCount(),
                historyStartedAtEpochMillis,
                savedSessionId,
                historySessionEnded
        );
    }

    private void persistAcceptedAnswer(QuizSubmission submission, Guess guess) {
        long answeredAtEpochMillis = System.currentTimeMillis();
        AnswerDraft answer = new AnswerDraft(
                quizSession.getAnsweredQuestionCount(),
                submission.getAnsweredQuestion(),
                guess,
                answeredAtEpochMillis
        );
        if (submission.isSessionComplete()) {
            historySession.recordFinalAnswerAndCompleteSession(
                    answer,
                    answeredAtEpochMillis
            );
            historySessionEnded = true;
            return;
        }
        historySession.recordAnswer(answer);
    }

    /** {@inheritDoc} */
    @Override
    public void finish() {
        if (historySession != null && !historySessionEnded) {
            historySession.abandon(System.currentTimeMillis());
            historySessionEnded = true;
        }
        super.finish();
    }
}
