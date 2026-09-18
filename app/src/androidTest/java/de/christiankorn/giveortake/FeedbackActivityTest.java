package de.christiankorn.giveortake;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Random;

import de.christiankorn.giveortake.core.CorrectnessClassifier;
import de.christiankorn.giveortake.core.LogRelativeScore;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.core.QuizSession;
import de.christiankorn.giveortake.core.QuizSubmission;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Verifies that feedback translates a scored submission into accessible display text.
 */
@RunWith(AndroidJUnit4.class)
public class FeedbackActivityTest {

    /** Verifies the directed factor, band text, values, score, and source survive recreation. */
    @Test
    public void sixfoldOverestimate_displaysHumanReadableWrongFeedback() {
        Context context = ApplicationProvider.getApplicationContext();
        Question question = Question.builder()
                .id("test-question")
                .prompt("Estimate the test quantity")
                .trueValue(100.0)
                .unit("units")
                .measurementBasis("test basis")
                .sourceUrl("https://example.org/value")
                .sourceLabel("example.org")
                .build();
        QuizSession session = new QuizSession(
                Collections.singletonList(question),
                1,
                new Random(123L),
                new LogRelativeScore(),
                new CorrectnessClassifier()
        );
        PointGuess guess = new PointGuess(600.0);
        QuizSubmission submission = session.submit(guess);

        try (ActivityScenario<FeedbackActivity> scenario = ActivityScenario.launch(
                FeedbackActivity.createIntent(context, submission, guess, 1)
        )) {
            assertFeedbackTextIsVisible();

            scenario.recreate();

            assertFeedbackTextIsVisible();
        }
    }

    /** Verifies that an unhandled source address produces visible in-app feedback. */
    @Test
    public void sourceWithoutAvailableHandler_displaysUnavailableMessage() {
        Context context = ApplicationProvider.getApplicationContext();
        Question question = Question.builder()
                .id("unhandled-source")
                .prompt("Estimate another test quantity")
                .trueValue(100.0)
                .unit("units")
                .measurementBasis("test basis")
                .sourceUrl("give-or-take-unhandled://source")
                .sourceLabel("Unavailable test source")
                .build();
        QuizSession session = new QuizSession(
                Collections.singletonList(question),
                1,
                new Random(456L),
                new LogRelativeScore(),
                new CorrectnessClassifier()
        );
        PointGuess guess = new PointGuess(100.0);
        QuizSubmission submission = session.submit(guess);

        try (ActivityScenario<FeedbackActivity> ignored = ActivityScenario.launch(
                FeedbackActivity.createIntent(context, submission, guess, 1)
        )) {
            onView(withId(R.id.feedback_source_button)).perform(click());

            onView(withText(R.string.feedback_source_unavailable))
                    .check(matches(isDisplayed()));
        }
    }

    private static void assertFeedbackTextIsVisible() {
        onView(withText("Answer 1")).check(matches(isDisplayed()));
        onView(withText("Estimate the test quantity")).check(matches(isDisplayed()));
        onView(withId(R.id.feedback_guess)).check(matches(withText("600 units")));
        onView(withId(R.id.feedback_true_value)).check(matches(withText("100 units")));
        onView(withText(R.string.feedback_band_wrong)).check(matches(isDisplayed()));
        onView(withText(R.string.feedback_band_wrong_description)).check(matches(isDisplayed()));
        onView(withText("Your estimate was about 6× too high."))
                .check(matches(isDisplayed()));
        onView(withText("17 points out of 100")).check(matches(isDisplayed()));
        onView(withText("Source: example.org ↗")).check(matches(isDisplayed()));
    }
}
