package de.christiankorn.giveortake;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/** Exercises the real Activity/Intent journey with a fixed bank and reproducible question order. */
@RunWith(AndroidJUnit4.class)
public class CompleteSessionTest {
    private final ActivityScenarioRule<MainActivity> home =
            new ActivityScenarioRule<>(MainActivity.class);

    /** Installs session inputs before Activity launch and restores them after Activity shutdown. */
    @Rule
    public final RuleChain rules = RuleChain.outerRule(new SessionFixtureRule()).around(home);

    /** Checks question-specific feedback for five close answers and the resulting score summary. */
    @Test
    public void homeToResult_showsFeedbackForEveryQuestion() {
        onView(withId(R.id.current_level_value))
                .check(matches(withText("Level 1 — Point estimates")));
        onView(withId(R.id.start_session_button))
                .check(matches(withText("Start session")))
                .perform(scrollTo(), click());

        // Explicit expectations are independent of the app's scheduler and scoring implementation.
        // Fixture order a,b,c,d,e shuffled with a fresh Random(42) is b,c,d,e,a.
        // A factor of two earns 50 points and CLOSE; only WRONG answers are requeued.
        answerClose(1, "What is the recorded value for the test walk?", "40", "20", "minutes");
        answerClose(2, "What is the recorded value for the test route?", "60", "30", "kilometres");
        answerClose(3, "What is the recorded value for the test parcel?", "80", "40", "grams");
        answerClose(4, "What is the recorded value for the test tank?", "100", "50", "litres");
        answerClose(5, "What is the recorded value for the test rope?", "20", "10", "metres");

        onView(withText("Session complete")).check(matches(isDisplayed()));
        onView(withId(R.id.result_score_value)).check(matches(withText("50 / 100")));
        onView(withId(R.id.result_correct_count)).check(matches(withText("0 correct")));
        onView(withId(R.id.result_close_count)).check(matches(withText("5 close")));
        onView(withId(R.id.result_wrong_count)).check(matches(withText("0 wrong")));

        onView(withId(R.id.result_home_button)).perform(click());
        onView(withId(R.id.start_session_button)).check(matches(isDisplayed()));
    }

    /** Checks that the real UI journey applies the delayed remedial-repeat policy. */
    @Test
    public void wrongAnswer_isPresentedAgainLaterInSession() {
        // Covers the examination acceptance criterion that wrong answers are presented again.
        onView(withId(R.id.start_session_button)).perform(scrollTo(), click());

        String repeatedPrompt = "What is the recorded value for the test walk?";
        onView(withId(R.id.question_prompt)).check(matches(withText(repeatedPrompt)));
        onView(withId(R.id.answer_input))
                .perform(scrollTo(), click(), typeText("20000"), closeSoftKeyboard());
        onView(withId(R.id.submit_button)).perform(scrollTo(), click());
        onView(withId(R.id.feedback_band_label)).check(matches(withText("WRONG")));
        onView(withId(R.id.feedback_next_button)).perform(click());

        answerCorrectlyAndContinue(
                "What is the recorded value for the test route?",
                "30"
        );
        answerCorrectlyAndContinue(
                "What is the recorded value for the test parcel?",
                "40"
        );

        onView(withId(R.id.question_prompt)).check(matches(withText(repeatedPrompt)));
    }

    private void answerClose(
            int answerNumber, String prompt, String estimate, String truth, String unit
    ) {
        int answered = answerNumber - 1;
        onView(withId(R.id.question_counter)).check(matches(withText(
                answered + " answered · " + (5 - answered) + " remaining"
        )));
        onView(withId(R.id.question_prompt)).check(matches(withText(prompt)));
        onView(withId(R.id.answer_unit)).check(matches(withText("Unit: " + unit)));
        onView(withId(R.id.answer_input))
                .perform(scrollTo(), click(), typeText(estimate), closeSoftKeyboard());
        onView(withId(R.id.submit_button)).perform(scrollTo(), click());

        onView(withId(R.id.feedback_answer_number))
                .check(matches(withText("Answer " + answerNumber)));
        onView(withId(R.id.feedback_question)).check(matches(withText(prompt)));
        onView(withId(R.id.feedback_guess)).check(matches(withText(estimate + " " + unit)));
        onView(withId(R.id.feedback_true_value)).check(matches(withText(truth + " " + unit)));
        onView(withId(R.id.feedback_band_label)).check(matches(withText("CLOSE")));
        onView(withId(R.id.feedback_score)).check(matches(withText("50 points out of 100")));
        onView(withId(R.id.feedback_comparison))
                .check(matches(withText("Your estimate was about 2× too high.")));
        onView(withId(R.id.feedback_next_button)).perform(click());
    }

    private void answerCorrectlyAndContinue(String prompt, String truth) {
        onView(withId(R.id.question_prompt)).check(matches(withText(prompt)));
        onView(withId(R.id.answer_input))
                .perform(scrollTo(), click(), typeText(truth), closeSoftKeyboard());
        onView(withId(R.id.submit_button)).perform(scrollTo(), click());
        onView(withId(R.id.feedback_band_label)).check(matches(withText("CORRECT")));
        onView(withId(R.id.feedback_next_button)).perform(click());
    }
}
