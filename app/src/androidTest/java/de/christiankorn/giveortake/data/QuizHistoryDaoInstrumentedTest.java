package de.christiankorn.giveortake.data;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;

import static org.junit.Assert.assertEquals;

/** Smoke-tests the history DAO against the SQLite implementation on an Android device. */
@RunWith(AndroidJUnit4.class)
public class QuizHistoryDaoInstrumentedTest {
    private Context context;
    private QuizHistoryDao dao;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
        dao = new QuizHistoryDao(context);
    }

    @After
    public void tearDown() {
        dao.close();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void completedSession_roundTripsOnAndroidSQLite() {
        long sessionId = dao.startSession(Level.POINT_ESTIMATES, 1, 1_000L);
        Question question = Question.builder()
                .id("device-smoke")
                .prompt("Estimate the test value")
                .trueValue(100.0)
                .unit("units")
                .measurementBasis("test measurement")
                .timeVarying(false)
                .build();

        dao.recordFinalAnswerAndCompleteSession(
                sessionId,
                new AnswerDraft(1, question, new PointGuess(90.0), 1_100L),
                1_100L
        );

        List<StoredAnswer> answers = dao.getAnswersForSession(sessionId);
        assertEquals(1, answers.size());
        assertEquals(90.0, ((PointGuess) answers.get(0).getGuess()).getValue(), 0.0);
        assertEquals(
                StoredSession.State.COMPLETED,
                dao.findSession(sessionId).orElseThrow(AssertionError::new).getState()
        );
    }
}
