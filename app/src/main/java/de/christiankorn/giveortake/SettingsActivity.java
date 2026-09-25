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
 * into its launch Intent instead of reading these preferences while the session is active.</p>
 */
public class SettingsActivity extends AppCompatActivity {
    private final Map<String, MaterialCheckBox> categoryBoxes = new LinkedHashMap<>();
    private QuizSettings quizSettings;
    private MaterialButton resetButton;

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
        resetButton.setOnClickListener(view -> loadResetCounts());
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
                R.dimen.settings_row_padding
        );
        int minimumHeight = getResources().getDimensionPixelSize(R.dimen.settings_row_height);
        for (String category : categories) {
            MaterialCheckBox checkBox = new MaterialCheckBox(this);
            checkBox.setId(View.generateViewId());
            checkBox.setText(category);
            checkBox.setTextSize(18.0f);
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

    private void loadResetCounts() {
        setResetLoading(true);
        historyStore().loadHistoryCounts(
                counts -> runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        setResetLoading(false);
                        showResetConfirmation(counts);
                    }
                }),
                exception -> runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        setResetLoading(false);
                        showResetFailure();
                    }
                })
        );
    }

    private void showResetConfirmation(QuizHistoryStore.HistoryCounts counts) {
        String sessions = getResources().getQuantityString(
                R.plurals.settings_reset_sessions,
                counts.getSessionCount(),
                counts.getSessionCount()
        );
        String answers = getResources().getQuantityString(
                R.plurals.settings_reset_answers,
                counts.getAnswerCount(),
                counts.getAnswerCount()
        );
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_title)
                .setMessage(getString(R.string.settings_reset_message, sessions, answers))
                .setNegativeButton(R.string.settings_reset_cancel, null)
                .setPositiveButton(R.string.settings_reset_confirm, null)
                .create();
        dialog.setOnShowListener(unused -> {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
                clearStatistics(dialog);
            });
        });
        dialog.show();
    }

    private void clearStatistics(AlertDialog dialog) {
        historyStore().clearHistory(
                () -> {
                    // Persistence must finish even if rotation destroys this Activity mid-reset.
                    new HighScorePreferences(getApplicationContext()).clear();
                    runOnUiThread(() -> {
                        if (!isDestroyed()) {
                            dialog.dismiss();
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
                        dialog.dismiss();
                        showResetFailure();
                    }
                })
        );
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
}
