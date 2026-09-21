package de.christiankorn.giveortake;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;

import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.data.HighScorePreferences;

/**
 * Displays the application's home screen and starts its top-level destinations.
 *
 * <p>The home screen is the launcher entry point. It summarises the current curriculum level and
 * personal best, then uses explicit intents to open a quiz, statistics, or settings screen.</p>
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.start_session_button).setOnClickListener(view -> {
            Intent quizIntent = QuizActivity.createIntent(
                    MainActivity.this,
                    Level.POINT_ESTIMATES
            );
            startActivity(quizIntent);
        });

        findViewById(R.id.stats_button).setOnClickListener(view -> {
            Intent statsIntent = new Intent(MainActivity.this, StatsActivity.class);
            startActivity(statsIntent);
        });

        findViewById(R.id.settings_button).setOnClickListener(view -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHighScore();
    }

    private void renderHighScore() {
        HighScore highScore = new HighScorePreferences(this).load(Level.POINT_ESTIMATES);
        TextView valueView = findViewById(R.id.high_score_value);
        TextView explanationView = findViewById(R.id.high_score_explanation);
        if (!highScore.getBestValue().isPresent()) {
            valueView.setText(R.string.high_score_empty_value);
            explanationView.setText(R.string.high_score_empty_explanation);
            return;
        }

        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(1);
        valueView.setText(getString(
                R.string.high_score_point_value,
                numberFormat.format(highScore.getBestValue().getAsDouble())
        ));
        explanationView.setText(getResources().getQuantityString(
                R.plurals.high_score_answer_count,
                highScore.getAnsweredQuestionCount().getAsInt(),
                highScore.getAnsweredQuestionCount().getAsInt()
        ));
    }
}
