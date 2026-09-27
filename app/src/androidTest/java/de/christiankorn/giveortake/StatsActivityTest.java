package de.christiankorn.giveortake;

import android.content.Context;
import android.os.SystemClock;

import androidx.recyclerview.widget.RecyclerView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.data.AnswerDraft;
import de.christiankorn.giveortake.data.QuizDatabaseHelper;
import de.christiankorn.giveortake.data.QuizHistoryDao;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

/** Verifies the statistics screen's asynchronous states and calibration disclosure rules. */
@RunWith(AndroidJUnit4.class)
public class StatsActivityTest {
    private static final long ROW_BIND_TIMEOUT_MILLIS = 5_000L;

    private Context context;

    /** Starts each test with no persisted quiz history. */
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    /** Removes history created by a test. */
    @After
    public void tearDown() {
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    /** Verifies first-time players receive an explanation and an action instead of zeroes. */
    @Test
    public void noHistory_displaysHelpfulEmptyState() {
        try (ActivityScenario<StatsActivity> scenario = ActivityScenario.launch(
                StatsActivity.class
        )) {
            awaitStatistics(scenario);

            onView(withText(R.string.stats_empty_title)).check(matches(isDisplayed()));
            onView(withId(R.id.stats_start_session_button)).check(matches(isDisplayed()));
            onView(withId(R.id.stats_recycler)).check(matches(not(isDisplayed())));
        }
    }

    /** Verifies a small interval sample never exposes a noisy coverage percentage. */
    @Test
    public void smallIntervalSample_suppressesCoverageAndExplainsThreshold() {
        storeIntervalSession(1, 1);

        try (ActivityScenario<StatsActivity> scenario = ActivityScenario.launch(
                StatsActivity.class
        )) {
            awaitStatistics(scenario);

            onView(withId(R.id.stats_insufficient_group)).check(matches(isDisplayed()));
            onView(withId(R.id.stats_coverage_group)).check(matches(not(isDisplayed())));
            onView(withText("49 more range answers until coverage is shown."))
                    .check(matches(isDisplayed()));

            awaitSessionRow(scenario);
            onView(withId(R.id.stats_session_date)).check(matches(isDisplayed()));
        }
    }

    /** Verifies sufficient 55 percent coverage receives honest overconfidence wording. */
    @Test
    public void sufficientLowCoverage_displaysDryOverconfidenceVerdict() {
        storeIntervalSession(60, 33);

        try (ActivityScenario<StatsActivity> scenario = ActivityScenario.launch(
                StatsActivity.class
        )) {
            awaitStatistics(scenario);

            onView(withText("Your 90% ranges contained the true value 55% of the time."))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.stats_verdict_overconfident))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.stats_insufficient_group)).check(matches(not(isDisplayed())));
        }
    }

    private static void awaitStatistics(ActivityScenario<StatsActivity> scenario) {
        AtomicReference<StatsActivity> activityReference = new AtomicReference<>();
        scenario.onActivity(activityReference::set);
        activityReference.get().awaitLoadForTest();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Waits until a session row has been bound into the history list.
     *
     * <p>{@code awaitLoadForTest()} returns when the database query completes, but the adapter is
     * attached on the main thread and the {@link RecyclerView} binds its children on a later
     * layout pass. {@code waitForIdleSync()} does not reliably span that gap, so an assertion on a
     * view inside a row could run against a list that had not yet bound one.</p>
     *
     * <p>Polls for the view the assertion actually needs rather than a proxy. Two earlier attempts
     * were wrong in instructive ways: waiting for a non-zero child count never returns on the
     * empty-state screen, where the list is {@code GONE} and has no children at all; and it
     * returns too early on a populated screen, where the overview header is item 0 and satisfies
     * the condition before any session row exists.</p>
     *
     * <p>Call it only from tests that assert on a row. An {@code IdlingResource} would be more
     * idiomatic but reports work the application knows it is performing, and the application does
     * not know it is waiting for a layout pass.</p>
     */
    private static void awaitSessionRow(ActivityScenario<StatsActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + ROW_BIND_TIMEOUT_MILLIS;
        AtomicBoolean bound = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity ->
                    bound.set(activity.findViewById(R.id.stats_session_date) != null)
            );
            if (bound.get()) {
                return;
            }
            // One frame, so the loop neither spins nor hides a real failure behind a long wait.
            SystemClock.sleep(16L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        throw new AssertionError(
                "no session row was bound within " + ROW_BIND_TIMEOUT_MILLIS + " ms"
        );
    }

    private void storeIntervalSession(int sampleSize, int hitCount) {
        try (QuizHistoryDao historyDao = new QuizHistoryDao(context)) {
            long sessionId = historyDao.startSession(
                    Level.CONFIDENCE_INTERVALS,
                    sampleSize,
                    1_000L
            );
            List<AnswerDraft> leadingAnswers = new ArrayList<>();
            for (int index = 1; index < sampleSize; index++) {
                leadingAnswers.add(intervalDraft(index, index <= hitCount));
            }
            historyDao.recordAnswers(sessionId, leadingAnswers);
            historyDao.recordFinalAnswerAndCompleteSession(
                    sessionId,
                    intervalDraft(sampleSize, sampleSize <= hitCount),
                    2_000L
            );
        }
    }

    private static AnswerDraft intervalDraft(int sequence, boolean containsTruth) {
        Question question = Question.builder()
                .id("stats-" + sequence)
                .prompt("Estimate test value")
                .trueValue(100.0)
                .unit("units")
                .measurementBasis("test measurement")
                .timeVarying(false)
                .category("Test")
                .build();
        return new AnswerDraft(
                sequence,
                question,
                containsTruth
                        ? new IntervalGuess(50.0, 200.0)
                        : new IntervalGuess(1.0, 10.0),
                1_000L + sequence
        );
    }
}
