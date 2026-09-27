package de.christiankorn.giveortake;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.christiankorn.giveortake.core.CalibrationTracker;
import de.christiankorn.giveortake.core.CorrectnessClassifier;
import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.Score;
import de.christiankorn.giveortake.core.SessionResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/** Verifies the result screen's implicit share Intent and graceful failure path. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class ResultActivityShareTest {

    /** Verifies point results are sent as short plain text through the system chooser. */
    @Test
    public void sharePointResult_createsChooserWithPlainTextPayload() {
        ResultActivity activity = buildActivity(SessionResult.forPointEstimates(
                Arrays.asList(
                        new Score(0.0, 100),
                        new Score(0.3, 50),
                        new Score(0.6, 25)
                ),
                new CorrectnessClassifier()
        ));

        activity.findViewById(R.id.result_share_button).performClick();

        Intent chooser = shadowOf(activity).getNextStartedActivity();
        Intent sendIntent = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertEquals("Share session result", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE));
        assertNotNull(sendIntent);
        assertEquals(Intent.ACTION_SEND, sendIntent.getAction());
        assertEquals("text/plain", sendIntent.getType());
        assertEquals(
                "Give or Take — score 58.3 / 100 across 3 questions.",
                sendIntent.getStringExtra(Intent.EXTRA_TEXT)
        );
    }

    /** Verifies interval sharing includes both the session score and meaningful calibration. */
    @Test
    public void shareIntervalResult_includesCalibrationFigure() {
        ResultActivity activity = buildActivity(intervalResult(45, 5));

        activity.findViewById(R.id.result_share_button).performClick();

        Intent chooser = shadowOf(activity).getNextStartedActivity();
        Intent sendIntent = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(sendIntent);
        String shareText = sendIntent.getStringExtra(Intent.EXTRA_TEXT);
        assertNotNull(shareText);
        assertTrue(shareText.contains("0.4 average loss across 50 questions"));
        assertTrue(shareText.contains("Calibration: 45/50 ranges contained the truth (90%)"));
    }

    /** Verifies a small interval sample shares honest counts without a noisy percentage. */
    @Test
    public void shareIntervalResult_withSmallSample_omitsCalibrationPercentage() {
        ResultActivity activity = buildActivity(intervalResult(6, 4));

        activity.findViewById(R.id.result_share_button).performClick();

        Intent chooser = shadowOf(activity).getNextStartedActivity();
        Intent sendIntent = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(sendIntent);
        assertEquals(
                "Give or Take — interval score 0.4 average loss across 10 questions. "
                        + "Calibration: 6/10 ranges contained the truth.",
                sendIntent.getStringExtra(Intent.EXTRA_TEXT)
        );
    }

    /** Verifies a missing handler is caught instead of escaping from the button click. */
    @Test
    public void shareResult_withoutHandler_doesNotCrash() {
        Context context = RuntimeEnvironment.getApplication();
        SessionResult result = SessionResult.forPointEstimates(
                Arrays.asList(new Score(0.0, 100)),
                new CorrectnessClassifier()
        );
        Intent intent = ResultActivity.createIntent(
                context,
                result,
                HighScore.empty(Level.POINT_ESTIMATES)
        );
        NoHandlerResultActivity activity = Robolectric.buildActivity(
                NoHandlerResultActivity.class,
                intent
        ).setup().get();

        activity.findViewById(R.id.result_share_button).performClick();
    }

    private static ResultActivity buildActivity(SessionResult result) {
        Context context = RuntimeEnvironment.getApplication();
        return Robolectric.buildActivity(
                ResultActivity.class,
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.empty(result.getLevel())
                )
        ).setup().get();
    }

    private static SessionResult intervalResult(int hitCount, int missCount) {
        int answerCount = hitCount + missCount;
        List<Score> scores = new ArrayList<>(answerCount);
        CalibrationTracker tracker = new CalibrationTracker();
        for (int index = 0; index < hitCount; index++) {
            scores.add(new Score(0.4));
            tracker.recordOutcome(true, 0.4);
        }
        for (int index = 0; index < missCount; index++) {
            scores.add(new Score(0.4));
            tracker.recordOutcome(false, 0.4);
        }
        return SessionResult.forConfidenceIntervals(scores, tracker);
    }

    private static final class NoHandlerResultActivity extends ResultActivity {
        @Override
        public void startActivity(Intent intent) {
            throw new ActivityNotFoundException("No test handler");
        }
    }
}
