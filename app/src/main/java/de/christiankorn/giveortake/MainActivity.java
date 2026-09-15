package de.christiankorn.giveortake;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
            Intent quizIntent = new Intent(MainActivity.this, QuizActivity.class);
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
}
