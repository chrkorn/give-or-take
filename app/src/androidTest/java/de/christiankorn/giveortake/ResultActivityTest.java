package de.christiankorn.giveortake;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.christiankorn.giveortake.core.CalibrationTracker;
import de.christiankorn.giveortake.core.CorrectnessClassifier;
import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.Score;
import de.christiankorn.giveortake.core.SessionResult;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

/**
 * Verifies result rendering, honest calibration messaging, and replacement navigation.
 */
@RunWith(AndroidJUnit4.class)
public class ResultActivityTest {

    /** Verifies score, closeness, band plurals, record emphasis, and recreation. */
    @Test
    public void pointResult_displaysAggregateAndNewPersonalBest() {
        Context context = ApplicationProvider.getApplicationContext();
        SessionResult result = SessionResult.forPointEstimates(
                Arrays.asList(
                        new Score(0.0, 100),
                        new Score(0.3, 50),
                        new Score(0.6, 25)
                ),
                new CorrectnessClassifier()
        );

        try (ActivityScenario<ResultActivity> scenario = ActivityScenario.launch(
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.recorded(Level.POINT_ESTIMATES, 50.0, 3)
                )
        )) {
            assertPointResultIsVisible();
            scenario.recreate();
            assertPointResultIsVisible();
            onView(withId(R.id.result_share_button)).check(matches(not(isEnabled())));
        }
    }

    /** Verifies a small interval sample is described without a misleading percentage. */
    @Test
    public void intervalResult_withSmallSample_explainsInsufficiency() {
        Context context = ApplicationProvider.getApplicationContext();
        SessionResult result = intervalResult(6, 4);

        try (ActivityScenario<ResultActivity> ignored = ActivityScenario.launch(
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.empty(Level.CONFIDENCE_INTERVALS)
                )
        )) {
            onView(withText("This session has only 10 interval answers."))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.result_calibration_insufficient_explanation))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.result_bands_card)).check(matches(not(isDisplayed())));
        }
    }

    /** Verifies a sufficient interval sample reports observed and expected coverage. */
    @Test
    public void intervalResult_withMeaningfulSample_displaysCalibration() {
        Context context = ApplicationProvider.getApplicationContext();
        SessionResult result = intervalResult(45, 5);

        try (ActivityScenario<ResultActivity> ignored = ActivityScenario.launch(
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.empty(Level.CONFIDENCE_INTERVALS)
                )
        )) {
            onView(withText("45 of 50 ranges contained the true value (90%)."))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.result_calibration_target))
                    .check(matches(isDisplayed()));
        }
    }

    /** Verifies Play again replaces the result with a fresh quiz Activity. */
    @Test
    public void playAgain_finishesResultAndStartsFreshQuiz() {
        Context context = ApplicationProvider.getApplicationContext();
        SessionResult result = SessionResult.forPointEstimates(
                Arrays.asList(new Score(0.0, 100)),
                new CorrectnessClassifier()
        );

        try (ActivityScenario<ResultActivity> scenario = ActivityScenario.launch(
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.empty(Level.POINT_ESTIMATES)
                )
        )) {
            onView(withId(R.id.result_play_again_button)).perform(click());

            onView(withId(R.id.submit_button)).check(matches(isDisplayed()));
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    /** Verifies Play again retains the completed session's explicitly selected level. */
    @Test
    public void playAgain_afterIntervalResult_startsIntervalQuiz() {
        Context context = ApplicationProvider.getApplicationContext();
        SessionResult result = intervalResult(1, 0);

        try (ActivityScenario<ResultActivity> scenario = ActivityScenario.launch(
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.empty(Level.CONFIDENCE_INTERVALS)
                )
        )) {
            onView(withId(R.id.result_play_again_button)).perform(click());

            onView(withId(R.id.range_answer_group)).check(matches(isDisplayed()));
            onView(withId(R.id.point_answer_group)).check(matches(not(isDisplayed())));
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    /** Verifies Home removes the result and opens the application's existing root destination. */
    @Test
    public void home_finishesResultAndDisplaysHome() {
        Context context = ApplicationProvider.getApplicationContext();
        SessionResult result = SessionResult.forPointEstimates(
                Arrays.asList(new Score(0.0, 100)),
                new CorrectnessClassifier()
        );

        try (ActivityScenario<ResultActivity> scenario = ActivityScenario.launch(
                ResultActivity.createIntent(
                        context,
                        result,
                        HighScore.empty(Level.POINT_ESTIMATES)
                )
        )) {
            onView(withId(R.id.result_home_button)).perform(click());

            onView(withId(R.id.start_session_button)).check(matches(isDisplayed()));
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    private static void assertPointResultIsVisible() {
        onView(withText(R.string.result_title)).check(matches(isDisplayed()));
        onView(withText("58.3 / 100")).check(matches(isDisplayed()));
        onView(withText("On average, your estimates were within a factor of 2 of the truth."))
                .check(matches(isDisplayed()));
        onView(withText("1 correct")).check(matches(isDisplayed()));
        onView(withText("1 close")).check(matches(isDisplayed()));
        onView(withText("1 wrong")).check(matches(isDisplayed()));
        onView(withText(R.string.result_new_personal_best)).check(matches(isDisplayed()));
        onView(withText("Previous: 50 / 100")).check(matches(isDisplayed()));
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
}
