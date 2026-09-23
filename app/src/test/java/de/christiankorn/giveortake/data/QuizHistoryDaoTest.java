package de.christiankorn.giveortake.data;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Exercises the SQLite schema and DAO together on the local JVM. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class QuizHistoryDaoTest {
    private Context context;
    private QuizHistoryDao dao;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
        dao = new QuizHistoryDao(context);
    }

    @After
    public void tearDown() {
        dao.close();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void pointAnswer_roundTripsRawInputAndTruthSnapshot() {
        long sessionId = dao.startSession(Level.POINT_ESTIMATES, 10, 1_000L);
        Question question = question("river", 346.0);

        dao.recordAnswer(
                sessionId,
                new AnswerDraft(1, question, new PointGuess(300.0), 1_100L)
        );

        StoredSession session = dao.findSession(sessionId).orElseThrow(AssertionError::new);
        assertEquals(Level.POINT_ESTIMATES, session.getLevel());
        assertEquals(StoredSession.State.IN_PROGRESS, session.getState());
        assertEquals(10, session.getInitialQuestionCount());
        assertEquals(1_000L, session.getStartedAtEpochMillis());
        assertNull(session.getEndedAtEpochMillis());

        List<StoredAnswer> answers = dao.getAnswersForSession(sessionId);
        assertEquals(1, answers.size());
        StoredAnswer answer = answers.get(0);
        assertEquals(sessionId, answer.getSessionId());
        assertEquals(1, answer.getSequenceNumber());
        assertEquals("river", answer.getQuestionId());
        assertEquals("Test category", answer.getCategoryAtAnswer());
        assertEquals(346.0, answer.getTrueValueAtAnswer(), 0.0);
        assertEquals(300.0, ((PointGuess) answer.getGuess()).getValue(), 0.0);
        assertEquals(1_100L, answer.getAnsweredAtEpochMillis());
    }

    @Test
    public void finalIntervalAnswer_completesSessionAtomically() {
        long sessionId = dao.startSession(Level.CONFIDENCE_INTERVALS, 1, 2_000L);
        AnswerDraft answer = new AnswerDraft(
                1,
                question("population", 84_000_000.0),
                new IntervalGuess(75_000_000.0, 90_000_000.0),
                2_100L
        );

        dao.recordFinalAnswerAndCompleteSession(sessionId, answer, 2_100L);

        StoredSession session = dao.findSession(sessionId).orElseThrow(AssertionError::new);
        assertEquals(StoredSession.State.COMPLETED, session.getState());
        assertEquals(Long.valueOf(2_100L), session.getEndedAtEpochMillis());
        IntervalGuess storedGuess = (IntervalGuess) dao.getAnswersForSession(sessionId)
                .get(0)
                .getGuess();
        assertEquals(75_000_000.0, storedGuess.getLowerBound(), 0.0);
        assertEquals(90_000_000.0, storedGuess.getUpperBound(), 0.0);
        assertEquals(
                sessionId,
                dao.getCompletedSessions(Level.CONFIDENCE_INTERVALS).get(0).getId()
        );
        assertTrue(dao.getCompletedSessions(Level.POINT_ESTIMATES).isEmpty());
    }

    @Test
    public void recordAnswers_whenOneSequenceIsDuplicated_rollsBackWholeBatch() {
        long sessionId = dao.startSession(Level.POINT_ESTIMATES, 2, 3_000L);
        List<AnswerDraft> answers = Arrays.asList(
                new AnswerDraft(1, question("first", 10.0), new PointGuess(9.0), 3_100L),
                new AnswerDraft(1, question("second", 20.0), new PointGuess(19.0), 3_200L)
        );

        assertThrows(
                SQLiteConstraintException.class,
                () -> dao.recordAnswers(sessionId, answers)
        );

        assertTrue(dao.getAnswersForSession(sessionId).isEmpty());
    }

    @Test
    public void finalAnswer_whenSessionIsAlreadyAbandoned_rollsBackAnswer() {
        long sessionId = dao.startSession(Level.POINT_ESTIMATES, 1, 4_000L);
        dao.abandonSession(sessionId, 4_100L);
        AnswerDraft answer = new AnswerDraft(
                1,
                question("late", 100.0),
                new PointGuess(90.0),
                4_200L
        );

        assertThrows(
                IllegalStateException.class,
                () -> dao.recordFinalAnswerAndCompleteSession(sessionId, answer, 4_200L)
        );

        assertTrue(dao.getAnswersForSession(sessionId).isEmpty());
        assertEquals(
                StoredSession.State.ABANDONED,
                dao.findSession(sessionId).orElseThrow(AssertionError::new).getState()
        );
    }

    @Test
    public void foreignKey_rejectsAnswerForMissingSession() {
        ContentValues values = new ContentValues();
        values.put(QuizHistoryContract.Answers.COLUMN_SESSION_ID, 999L);
        values.put(QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER, 1);
        values.put(QuizHistoryContract.Answers.COLUMN_QUESTION_ID, "orphan");
        values.put(QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER, 100.0);
        values.put(QuizHistoryContract.Answers.COLUMN_POINT_GUESS, 90.0);
        values.put(QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS, 5_000L);
        QuizDatabaseHelper helper = new QuizDatabaseHelper(context);

        try {
            assertThrows(
                    SQLiteConstraintException.class,
                    () -> helper.getWritableDatabase().insertOrThrow(
                            QuizHistoryContract.Answers.TABLE_NAME,
                            null,
                            values
                    )
            );
        } finally {
            helper.close();
        }
    }

    @Test
    public void findSession_whenIdentifierIsUnknown_returnsEmptyValue() {
        Optional<StoredSession> session = dao.findSession(123L);

        assertFalse(session.isPresent());
    }

    @Test
    public void recordAnswer_whenGuessTypeDoesNotMatchSessionLevel_rejectsAnswer() {
        long sessionId = dao.startSession(Level.CONFIDENCE_INTERVALS, 1, 6_000L);
        AnswerDraft pointAnswer = new AnswerDraft(
                1,
                question("wrong-mode", 100.0),
                new PointGuess(90.0),
                6_100L
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> dao.recordAnswer(sessionId, pointAnswer)
        );

        assertEquals("guess type does not match the session level", exception.getMessage());
        assertTrue(dao.getAnswersForSession(sessionId).isEmpty());
    }

    @Test
    public void migrationFromVersionOne_addsNullableAnswerCategoryWithoutDroppingRows() {
        SQLiteDatabase database = SQLiteDatabase.create(null);
        QuizDatabaseHelper helper = new QuizDatabaseHelper(context);
        database.execSQL("CREATE TABLE answers (_id INTEGER PRIMARY KEY, question_id TEXT)");
        database.execSQL("INSERT INTO answers (_id, question_id) VALUES (1, 'legacy')");

        try {
            helper.onUpgrade(database, 1, 2);

            try (Cursor cursor = database.rawQuery(
                    "SELECT question_id, category_at_answer FROM answers WHERE _id = ?",
                    new String[]{"1"}
            )) {
                assertTrue(cursor.moveToFirst());
                assertEquals("legacy", cursor.getString(0));
                assertTrue(cursor.isNull(1));
            }
        } finally {
            helper.close();
            database.close();
        }
    }

    private static Question question(String id, double trueValue) {
        return Question.builder()
                .id(id)
                .prompt("Estimate " + id)
                .trueValue(trueValue)
                .unit("units")
                .measurementBasis("test measurement")
                .timeVarying(false)
                .category("Test category")
                .build();
    }
}
