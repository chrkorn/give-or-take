package de.christiankorn.giveortake.data;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Exercises ordered asynchronous history persistence on the local JVM. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class QuizHistoryStoreTest {
    private Context context;
    private QuizHistoryDao dao;
    private QuizHistoryStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
        dao = new QuizHistoryDao(context);
        store = new QuizHistoryStore(dao, Executors.newSingleThreadExecutor());
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.closeForTest();
        }
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    /** Verifies queued creation, answers, and completion execute in submission order. */
    @Test
    public void queuedWrites_persistAnswersAndCompletionInOrder() {
        QuizHistorySession session = store.startSession(
                "ordered-session",
                Level.POINT_ESTIMATES,
                2,
                1_000L
        );
        session.recordAnswer(new AnswerDraft(
                1,
                question("first", 10.0),
                new PointGuess(9.0),
                1_100L
        ));
        session.recordFinalAnswerAndCompleteSession(
                new AnswerDraft(
                        2,
                        question("second", 20.0),
                        new PointGuess(21.0),
                        1_200L
                ),
                1_200L
        );

        store.awaitIdle();

        assertNull(session.getFailure());
        assertTrue(session.getSessionId() > 0L);
        StoredSession storedSession = dao.findSession(session.getSessionId())
                .orElseThrow(AssertionError::new);
        assertEquals(StoredSession.State.COMPLETED, storedSession.getState());
        assertEquals(Long.valueOf(1_200L), storedSession.getEndedAtEpochMillis());
        List<StoredAnswer> answers = dao.getAnswersForSession(session.getSessionId());
        assertEquals(2, answers.size());
        assertEquals(1, answers.get(0).getSequenceNumber());
        assertEquals(2, answers.get(1).getSequenceNumber());
    }

    /** Verifies recreation in one process reattaches instead of creating a second writer. */
    @Test
    public void restoreSession_whileStartIsQueued_reattachesByToken() {
        QuizHistorySession started = store.startSession(
                "rotation-session",
                Level.POINT_ESTIMATES,
                10,
                2_000L
        );

        QuizHistorySession restored = store.restoreSession(
                "rotation-session",
                Level.POINT_ESTIMATES,
                10,
                2_000L,
                0L,
                false
        );

        assertSame(started, restored);
        store.awaitIdle();
        assertNull(restored.getFailure());
    }

    /** Verifies process-style restoration can recover an insert whose generated ID was not saved. */
    @Test
    public void restoreSession_withoutSavedId_findsPreviouslyInsertedRow() {
        QuizHistorySession original = store.startSession(
                "original-process-token",
                Level.POINT_ESTIMATES,
                10,
                3_000L
        );
        store.awaitIdle();
        long originalId = original.getSessionId();
        store.closeForTest();

        dao = new QuizHistoryDao(context);
        store = new QuizHistoryStore(dao, Executors.newSingleThreadExecutor());
        QuizHistorySession restored = store.restoreSession(
                "new-process-token",
                Level.POINT_ESTIMATES,
                10,
                3_000L,
                0L,
                false
        );
        store.awaitIdle();

        assertNull(restored.getFailure());
        assertEquals(originalId, restored.getSessionId());
    }

    /** Verifies reset is serialised after pending writes and removes sessions with their answers. */
    @Test
    public void clearHistory_afterQueuedWrites_deletesCompleteHistory() {
        QuizHistorySession session = store.startSession(
                "reset-session",
                Level.POINT_ESTIMATES,
                1,
                4_000L
        );
        session.recordFinalAnswerAndCompleteSession(
                new AnswerDraft(
                        1,
                        question("reset-answer", 25.0),
                        new PointGuess(24.0),
                        4_100L
                ),
                4_100L
        );
        AtomicBoolean resetCompleted = new AtomicBoolean();

        store.clearHistory(
                () -> resetCompleted.set(true),
                exception -> {
                    throw exception;
                }
        );
        store.awaitIdle();

        assertTrue(resetCompleted.get());
        QuizHistoryStore.HistoryCounts counts = dao.getHistoryCounts();
        assertEquals(0, counts.getSessionCount());
        assertEquals(0, counts.getAnswerCount());
    }

    private static Question question(String id, double trueValue) {
        return Question.builder()
                .id(id)
                .prompt("Estimate " + id)
                .trueValue(trueValue)
                .unit("units")
                .sourceUrl("https://example.com/" + id)
                .sourceLabel("Example source")
                .measurementBasis("Test basis")
                .timeVarying(false)
                .build();
    }
}
