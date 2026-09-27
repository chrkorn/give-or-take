package de.christiankorn.giveortake;

import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Random;

import de.christiankorn.giveortake.core.CorrectnessClassifier;
import de.christiankorn.giveortake.core.LogRelativeScore;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.core.QuizSession;
import de.christiankorn.giveortake.core.QuizSubmission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

/** Verifies that the feedback source link delegates URL handling to Android. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class FeedbackActivitySourceIntentTest {

    /** Verifies the source button sends the exact question URL in an ACTION_VIEW Intent. */
    @Test
    public void openSource_createsViewIntentWithQuestionUrl() {
        Context context = RuntimeEnvironment.getApplication();
        Question question = Question.builder()
                .id("source-intent")
                .prompt("Estimate the test quantity")
                .trueValue(100.0)
                .unit("units")
                .measurementBasis("test basis")
                .sourceUrl("https://example.org/value")
                .sourceLabel("Example source")
                .build();
        QuizSession session = new QuizSession(
                Collections.singletonList(question),
                1,
                new Random(123L),
                new LogRelativeScore(),
                new CorrectnessClassifier()
        );
        PointGuess guess = new PointGuess(100.0);
        QuizSubmission submission = session.submit(guess);
        FeedbackActivity activity = Robolectric.buildActivity(
                FeedbackActivity.class,
                FeedbackActivity.createIntent(context, submission, guess, 1)
        ).setup().get();

        activity.findViewById(R.id.feedback_source_button).performClick();

        Intent sourceIntent = shadowOf(activity).getNextStartedActivity();
        assertNotNull(sourceIntent);
        assertEquals(Intent.ACTION_VIEW, sourceIntent.getAction());
        assertEquals("https://example.org/value", sourceIntent.getDataString());
    }
}
