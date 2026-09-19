package de.christiankorn.giveortake;

import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.text.DecimalFormatSymbols;

import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.data.AssetQuestionBankLoader;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

/**
 * Verifies the quiz screen's Material validation feedback and long-prompt layout behaviour.
 */
@RunWith(AndroidJUnit4.class)
public class QuizActivityTest {

    /** Verifies that only a valid value enables submission and errors appear beside the input. */
    @Test
    public void validInputEnablesSubmitAndEmptyInputShowsLayoutError() {
        try (ActivityScenario<QuizActivity> ignored = ActivityScenario.launch(QuizActivity.class)) {
            onView(withId(R.id.submit_button)).check(matches(not(isEnabled())));
            onView(withText("0 answered · 10 remaining")).check(matches(isDisplayed()));

            onView(withId(R.id.answer_input)).perform(replaceText("346.5"));
            onView(withId(R.id.submit_button)).check(matches(isEnabled()));

            onView(withId(R.id.answer_input)).perform(clearText());
            onView(withId(R.id.submit_button)).check(matches(not(isEnabled())));
            onView(withText(R.string.quiz_error_empty)).check(matches(isDisplayed()));
        }
    }

    /** Verifies that a 200-character prompt wraps without making the action unreachable. */
    @Test
    public void twoHundredCharacterPromptWrapsAndLeavesSubmitReachable() {
        String longPrompt = "Estimate the carefully defined numerical quantity described by this "
                + "deliberately long question prompt, including all measurement context, scope, "
                + "qualifiers and the applicable reference date shown now?";
        assertEquals(200, longPrompt.length());

        try (ActivityScenario<QuizActivity> scenario = ActivityScenario.launch(QuizActivity.class)) {
            scenario.onActivity(activity -> {
                TextView questionPrompt = activity.findViewById(R.id.question_prompt);
                questionPrompt.setText(longPrompt);
            });
            onView(withText(longPrompt)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.submit_button)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    /** Verifies that Back from feedback resumes the already-advanced core session. */
    @Test
    public void submitValidGuess_thenBackFromFeedback_advancesQuestionAndProgress() {
        AtomicReference<String> firstPrompt = new AtomicReference<>();
        try (ActivityScenario<QuizActivity> scenario = ActivityScenario.launch(QuizActivity.class)) {
            scenario.onActivity(activity -> firstPrompt.set(
                    ((TextView) activity.findViewById(R.id.question_prompt)).getText().toString()
            ));

            onView(withId(R.id.answer_input)).perform(replaceText("1"));
            onView(withId(R.id.submit_button)).perform(click());

            onView(withId(R.id.feedback_next_button)).check(matches(isDisplayed()));
            onView(withText(firstPrompt.get())).check(matches(isDisplayed()));
            pressBack();

            onView(withId(R.id.question_counter)).check(matches(withText(startsWith("1 answered · "))));
            onView(withId(R.id.question_prompt)).check(matches(not(withText(firstPrompt.get()))));
            onView(withId(R.id.answer_input)).check(matches(withText("")));
        }
    }

    /** Verifies that Activity recreation restores the exact current core-session state. */
    @Test
    public void recreate_afterSubmission_restoresCurrentQuestionAndProgress() {
        AtomicReference<String> expectedPrompt = new AtomicReference<>();
        AtomicReference<String> expectedProgress = new AtomicReference<>();
        try (ActivityScenario<QuizActivity> scenario = ActivityScenario.launch(QuizActivity.class)) {
            onView(withId(R.id.answer_input)).perform(replaceText("1"));
            onView(withId(R.id.submit_button)).perform(click());
            pressBack();
            scenario.onActivity(activity -> {
                expectedPrompt.set(
                        ((TextView) activity.findViewById(R.id.question_prompt)).getText().toString()
                );
                expectedProgress.set(
                        ((TextView) activity.findViewById(R.id.question_counter)).getText().toString()
                );
            });

            scenario.recreate();

            onView(withId(R.id.question_prompt)).check(matches(withText(expectedPrompt.get())));
            onView(withId(R.id.question_counter)).check(matches(withText(expectedProgress.get())));
        }
    }

    /** Verifies Back from a completed result returns Home rather than the finished quiz. */
    @Test
    public void completeSession_thenBackFromResult_returnsHome() {
        QuestionBank questionBank = new AssetQuestionBankLoader(
                androidx.test.core.app.ApplicationProvider
                        .getApplicationContext()
                        .getAssets()
        ).load();
        char decimalSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.start_session_button)).perform(click());

            for (int index = 0; index < 10; index++) {
                AtomicReference<String> prompt = new AtomicReference<>();
                onView(withId(R.id.question_prompt)).check((view, noViewFoundException) -> {
                    if (noViewFoundException != null) {
                        throw noViewFoundException;
                    }
                    prompt.set(((TextView) view).getText().toString());
                });
                Question question = findByPrompt(questionBank, prompt.get());
                String answer = Double.toString(question.getTrueValue());
                if (decimalSeparator != '.') {
                    answer = answer.replace('.', decimalSeparator);
                }

                onView(withId(R.id.answer_input)).perform(replaceText(answer));
                onView(withId(R.id.submit_button)).perform(click());
                onView(withId(R.id.feedback_next_button)).perform(click());
            }

            onView(withText(R.string.result_title)).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.start_session_button)).check(matches(isDisplayed()));
        }
    }

    private static Question findByPrompt(QuestionBank questionBank, String prompt) {
        for (Question question : questionBank.getQuestions()) {
            if (question.getPrompt().equals(prompt)) {
                return question;
            }
        }
        throw new AssertionError("Displayed question was not found in the bundled bank");
    }
}
