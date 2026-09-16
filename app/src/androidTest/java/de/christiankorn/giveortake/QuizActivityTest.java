package de.christiankorn.giveortake;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
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
            onView(withText(R.string.quiz_default_unit)).check(matches(isDisplayed()));

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

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(QuizActivity.EXTRA_QUESTION_PROMPT, longPrompt);

        try (ActivityScenario<QuizActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withText(longPrompt)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.submit_button)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
}
