package de.christiankorn.giveortake;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.data.AssetQuestionBankLoader;
import de.christiankorn.giveortake.data.HighScorePreferences;
import de.christiankorn.giveortake.data.QuizHistoryStore;
import de.christiankorn.giveortake.data.QuizSettings;

/**
 * Lets the player configure quiz-session preferences.
 *
 * <p>Each ordinary control writes immediately to {@link android.content.SharedPreferences}. A
 * running quiz is unaffected because {@link QuizActivity} copies a complete settings snapshot
 * into its launch Intent instead of reading these preferences while the session is active. This
 * screen implements ADR 0022
 * ({@code docs/adr/0022-build-settings-with-ordinary-activity-controls.md}).</p>
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String STATE_RESET_PHASE = "reset_phase";
    private static final String STATE_RESET_SESSION_COUNT = "reset_session_count";
    private static final String STATE_RESET_ANSWER_COUNT = "reset_answer_count";

    private final Map<String, MaterialCheckBox> categoryBoxes = new LinkedHashMap<>();
    private QuizSettings quizSettings;
    private MaterialButton resetButton;
    private ResetPhase resetPhase = ResetPhase.IDLE;
    private int pendingSessionCount;
    private int pendingAnswerCount;
    private AlertDialog resetDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        QuestionBank questionBank = new AssetQuestionBankLoader(getAssets()).load();
        Set<String> availableCategories = new LinkedHashSet<>(questionBank.getCategories());
        quizSettings = new QuizSettings(this);
        QuizSettings.Snapshot snapshot = quizSettings.load(availableCategories);

        configureSessionLength(snapshot);
        configureAnswerMode(snapshot);
        configureCategories(questionBank.getCategories(), snapshot.getSelectedCategories());
        resetButton = findViewById(R.id.settings_reset_button);
        resetButton.setOnClickListener(view -> beginReset());
        restoreResetPhase(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_RESET_PHASE, resetPhase.name());
        outState.putInt(STATE_RESET_SESSION_COUNT, pendingSessionCount);
        outState.putInt(STATE_RESET_ANSWER_COUNT, pendingAnswerCount);
    }

    @Override
    protected void onDestroy() {
        // Detach the listener before dismissing: this dismissal is the Activity going away, not
        // the user answering the dialog, and it must not be recorded as the reset having ended.
        // Dismissing at all is what stops the window leaking when rotation destroys the host.
        if (resetDialog != null) {
            resetDialog.setOnDismissListener(null);
            resetDialog.dismiss();
            resetDialog = null;
        }
        super.onDestroy();
    }

    private void restoreResetPhase(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        pendingSessionCount = savedInstanceState.getInt(STATE_RESET_SESSION_COUNT);
        pendingAnswerCount = savedInstanceState.getInt(STATE_RESET_ANSWER_COUNT);
        String savedPhase = savedInstanceState.getString(STATE_RESET_PHASE);
        ResetPhase phase = savedPhase == null ? ResetPhase.IDLE : ResetPhase.valueOf(savedPhase);
        switch (phase) {
            case LOADING_COUNTS:
                // The count query belonged to the destroyed Activity and its callback was
                // dropped. Counting rows reads two tables and changes nothing, so the cheapest
                // correct recovery is to ask again.
                beginReset();
                break;
            case CONFIRMING:
                showResetConfirmation(pendingSessionCount, pendingAnswerCount);
                break;
            case CLEARING:
                // The delete that was running will finish on its own, because the store holds
                // only the application context, but its completion callback cannot reach this
                // new Activity. Re-issuing it is what restores the feedback, and it is safe:
                // clearHistory deletes unconditionally inside one transaction, so a second pass
                // over an already empty table is a no-op.
                showResetConfirmation(pendingSessionCount, pendingAnswerCount);
                confirmClear();
                break;
            default:
                break;
        }
    }

    private void configureSessionLength(QuizSettings.Snapshot snapshot) {
        MaterialButtonToggleGroup group = findViewById(R.id.settings_session_length_group);
        group.check(buttonIdForLength(snapshot.getSessionLength()));
        group.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (isChecked) {
                quizSettings.setSessionLength(lengthForButtonId(checkedId));
            }
        });
    }

    private void configureAnswerMode(QuizSettings.Snapshot snapshot) {
        RadioGroup group = findViewById(R.id.settings_answer_mode_group);
        group.check(radioIdForMode(snapshot.getAnswerMode()));
        group.setOnCheckedChangeListener((radioGroup, checkedId) ->
                quizSettings.setAnswerMode(modeForRadioId(checkedId))
        );
    }

    private void configureCategories(List<String> categories, Set<String> selectedCategories) {
        LinearLayout container = findViewById(R.id.settings_categories_container);
        int horizontalPadding = getResources().getDimensionPixelSize(
                R.dimen.space_3
        );
        int minimumHeight = getResources().getDimensionPixelSize(R.dimen.size_button_prominent);
        for (String category : categories) {
            MaterialCheckBox checkBox = new MaterialCheckBox(this);
            checkBox.setId(View.generateViewId());
            checkBox.setText(category);
            checkBox.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            );
            checkBox.setMinHeight(minimumHeight);
            checkBox.setPadding(horizontalPadding, 0, horizontalPadding, 0);
            checkBox.setChecked(selectedCategories.contains(category));
            checkBox.setOnCheckedChangeListener((button, isChecked) ->
                    onCategoryChanged(checkBox, isChecked)
            );
            categoryBoxes.put(category, checkBox);
            container.addView(checkBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private void onCategoryChanged(
            MaterialCheckBox changedBox,
            boolean isChecked
    ) {
        Set<String> selected = selectedCategories();
        if (!isChecked && selected.isEmpty()) {
            changedBox.setChecked(true);
            Toast.makeText(
                    this,
                    R.string.settings_last_category_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        quizSettings.setSelectedCategories(selected);
    }

    private Set<String> selectedCategories() {
        Set<String> selected = new LinkedHashSet<>();
        for (Map.Entry<String, MaterialCheckBox> entry : categoryBoxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    private void beginReset() {
        resetPhase = ResetPhase.LOADING_COUNTS;
        setResetLoading(true);
        historyStore().loadHistoryCounts(
                counts -> runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        setResetLoading(false);
                        showResetConfirmation(counts.getSessionCount(), counts.getAnswerCount());
                    }
                }),
                exception -> runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        resetPhase = ResetPhase.IDLE;
                        setResetLoading(false);
                        showResetFailure();
                    }
                })
        );
    }

    /**
     * Shows the confirmation dialog for a reset that would delete the given row counts.
     *
     * <p>Takes the two counts rather than a {@link QuizHistoryStore.HistoryCounts} so that the
     * dialog can be rebuilt after a configuration change from the two integers in the saved
     * instance state, without querying the database a second time.</p>
     */
    private void showResetConfirmation(int sessionCount, int answerCount) {
        resetPhase = ResetPhase.CONFIRMING;
        pendingSessionCount = sessionCount;
        pendingAnswerCount = answerCount;
        String sessions = getResources().getQuantityString(
                R.plurals.settings_reset_sessions,
                sessionCount,
                sessionCount
        );
        String answers = getResources().getQuantityString(
                R.plurals.settings_reset_answers,
                answerCount,
                answerCount
        );
        resetDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_title)
                .setMessage(getString(R.string.settings_reset_message, sessions, answers))
                .setNegativeButton(R.string.settings_reset_cancel, null)
                .setPositiveButton(R.string.settings_reset_confirm, null)
                .create();
        resetDialog.setOnShowListener(unused -> {
            resetDialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
            resetDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(view -> confirmClear());
        });
        // Reached by cancelling, by the delete succeeding and by the delete failing. All three
        // end the reset. The Activity being destroyed detaches this listener first, so a
        // rotation mid-dialog is not mistaken for the user having answered.
        resetDialog.setOnDismissListener(unused -> {
            resetPhase = ResetPhase.IDLE;
            resetDialog = null;
        });
        resetDialog.show();
    }

    private void confirmClear() {
        resetPhase = ResetPhase.CLEARING;
        // Once queued the delete cannot be called back, so the dialog stops offering a choice
        // rather than pretending the user still has one.
        resetDialog.setCancelable(false);
        resetDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        resetDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
        clearStatistics();
    }

    private void clearStatistics() {
        historyStore().clearHistory(
                () -> {
                    // Persistence must finish even if rotation destroys this Activity mid-reset.
                    new HighScorePreferences(getApplicationContext()).clear();
                    runOnUiThread(() -> {
                        if (!isDestroyed()) {
                            dismissResetDialog();
                            Toast.makeText(
                                    this,
                                    R.string.settings_reset_complete,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                },
                exception -> runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        dismissResetDialog();
                        showResetFailure();
                    }
                })
        );
    }

    private void dismissResetDialog() {
        // Read the field instead of capturing the dialog in the database callback. A captured
        // dialog holds the destroyed Activity's window alive until the worker thread gets round
        // to the callback, and after a configuration change it is the wrong dialog in any case.
        if (resetDialog != null) {
            resetDialog.dismiss();
        }
    }

    private void showResetFailure() {
        Toast.makeText(this, R.string.settings_reset_error, Toast.LENGTH_LONG).show();
    }

    private void setResetLoading(boolean loading) {
        resetButton.setEnabled(!loading);
        resetButton.setText(
                loading ? R.string.settings_reset_loading : R.string.settings_reset_action
        );
    }

    private QuizHistoryStore historyStore() {
        return ((GiveOrTakeApplication) getApplication()).getQuizHistoryStore();
    }

    private static int buttonIdForLength(int sessionLength) {
        if (sessionLength == 5) {
            return R.id.settings_length_5;
        }
        if (sessionLength == 20) {
            return R.id.settings_length_20;
        }
        return R.id.settings_length_10;
    }

    private static int lengthForButtonId(int buttonId) {
        if (buttonId == R.id.settings_length_5) {
            return 5;
        }
        if (buttonId == R.id.settings_length_20) {
            return 20;
        }
        return 10;
    }

    private static int radioIdForMode(QuizSettings.AnswerMode mode) {
        if (mode == QuizSettings.AnswerMode.POINT_ESTIMATE) {
            return R.id.settings_mode_point;
        }
        if (mode == QuizSettings.AnswerMode.CONFIDENCE_INTERVAL) {
            return R.id.settings_mode_interval;
        }
        return R.id.settings_mode_follow_level;
    }

    private static QuizSettings.AnswerMode modeForRadioId(int radioId) {
        if (radioId == R.id.settings_mode_point) {
            return QuizSettings.AnswerMode.POINT_ESTIMATE;
        }
        if (radioId == R.id.settings_mode_interval) {
            return QuizSettings.AnswerMode.CONFIDENCE_INTERVAL;
        }
        return QuizSettings.AnswerMode.FOLLOW_LEVEL;
    }

    /**
     * How far the multi-step statistics reset has got.
     *
     * <p>A reset spans two asynchronous database calls with a dialog between them, so it
     * regularly outlives the Activity that started it. This project deliberately carries no
     * retained scope — no {@code ViewModel} — which leaves the saved instance state as the only
     * place the progress of the flow can be kept.</p>
     */
    private enum ResetPhase {
        /** No reset in progress. */
        IDLE,
        /** Counting the rows a reset would delete, before anything is shown or deleted. */
        LOADING_COUNTS,
        /** The confirmation dialog is on screen; nothing has been deleted. */
        CONFIRMING,
        /** The user confirmed and the delete is running. */
        CLEARING
    }
}
