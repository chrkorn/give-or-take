package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests session orchestration and framework-independent snapshot restoration.
 */
public class QuizSessionTest {
    private static final long RANDOM_SEED = 13579L;

    /** Verifies that submission keeps all scoring and scheduling steps inside the session. */
    @Test
    public void submit_whenGuessIsCorrect_scoresClassifiesAndAdvances() {
        QuizSession session = newSession(questions(3), 3);
        Question answeredQuestion = session.getCurrentQuestion();
        assertEquals(0, session.getAnsweredQuestionCount());
        assertEquals(3, session.getRemainingQuestionCount());

        QuizSubmission submission = session.submit(
                new PointGuess(answeredQuestion.getTrueValue())
        );

        assertEquals(answeredQuestion, submission.getAnsweredQuestion());
        assertEquals(100, submission.getScore().getPoints());
        assertEquals(Correctness.CORRECT, submission.getCorrectness());
        assertFalse(submission.isSessionComplete());
        assertEquals(submission.getNextQuestion(), session.getCurrentQuestion());
        assertEquals(2, session.getCurrentQuestionNumber());
        assertEquals(1, session.getAnsweredQuestionCount());
        assertEquals(2, session.getRemainingQuestionCount());
    }

    /** Verifies that a wrong answer is repeated after two intervening questions. */
    @Test
    public void submit_whenGuessIsWrong_appliesTrainingStrategyRepeat() {
        QuizSession session = newSession(questions(5), 5);
        Question wrongQuestion = session.getCurrentQuestion();

        session.submit(new PointGuess(wrongQuestion.getTrueValue() * 4.0));
        assertEquals(1, session.getAnsweredQuestionCount());
        assertEquals(5, session.getRemainingQuestionCount());
        answerCurrentCorrectly(session);
        answerCurrentCorrectly(session);

        assertEquals(wrongQuestion, session.getCurrentQuestion());
    }

    /** Verifies that snapshot restoration preserves the exact future schedule and score history. */
    @Test
    public void restore_afterWrongAnswer_preservesScheduleAndResult() {
        List<Question> pool = questions(5);
        QuizSession original = newSession(pool, 5);
        Question firstQuestion = original.getCurrentQuestion();
        original.submit(new PointGuess(firstQuestion.getTrueValue() * 4.0));
        answerCurrentCorrectly(original);

        QuizSessionSnapshot snapshot = original.snapshot();
        QuizSession restored = QuizSession.restore(
                pool,
                snapshot,
                new LogRelativeScore(),
                new CorrectnessClassifier()
        );

        assertSnapshotsEqual(snapshot, restored.snapshot());
        List<String> originalRemainder = answerRemainderAndCollectIds(original);
        List<String> restoredRemainder = answerRemainderAndCollectIds(restored);
        assertEquals(originalRemainder, restoredRemainder);
        assertEquals(
                original.getResult().getMeanPoints().getAsDouble(),
                restored.getResult().getMeanPoints().getAsDouble(),
                0.0
        );
    }

    /** Verifies that a completed session can also be reconstructed after process recreation. */
    @Test
    public void restore_whenSessionIsComplete_preservesCompletedResult() {
        List<Question> pool = questions(1);
        QuizSession original = newSession(pool, 1);
        answerCurrentCorrectly(original);

        QuizSession restored = QuizSession.restore(
                pool,
                original.snapshot(),
                new LogRelativeScore(),
                new CorrectnessClassifier()
        );

        assertTrue(restored.isComplete());
        assertEquals(100.0, restored.getResult().getMeanPoints().getAsDouble(), 0.0);
    }

    /** Verifies that an app update cannot silently restore a snapshot against different content. */
    @Test
    public void restore_whenQuestionIdIsMissing_failsClearly() {
        QuizSessionSnapshot snapshot = new QuizSessionSnapshot(
                1,
                new ArrayList<>(),
                "removed-question",
                new ArrayList<>()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> QuizSession.restore(
                        questions(1),
                        snapshot,
                        new LogRelativeScore(),
                        new CorrectnessClassifier()
                )
        );

        assertEquals(
                "snapshot question identifier is absent from the question pool: removed-question",
                exception.getMessage()
        );
    }

    /** Verifies that a result cannot be requested while a question is still active. */
    @Test
    public void getResult_beforeCompletion_failsClearly() {
        QuizSession session = newSession(questions(1), 1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                session::getResult
        );

        assertEquals("the quiz session is not complete", exception.getMessage());
    }

    private static QuizSession newSession(List<Question> pool, int sessionLength) {
        return new QuizSession(
                pool,
                sessionLength,
                new Random(RANDOM_SEED),
                new LogRelativeScore(),
                new CorrectnessClassifier()
        );
    }

    private static void answerCurrentCorrectly(QuizSession session) {
        Question question = session.getCurrentQuestion();
        session.submit(new PointGuess(question.getTrueValue()));
    }

    private static List<String> answerRemainderAndCollectIds(QuizSession session) {
        List<String> identifiers = new ArrayList<>();
        while (!session.isComplete()) {
            identifiers.add(session.getCurrentQuestion().getId());
            answerCurrentCorrectly(session);
        }
        return identifiers;
    }

    private static void assertSnapshotsEqual(
            QuizSessionSnapshot expected,
            QuizSessionSnapshot actual
    ) {
        assertEquals(expected.getInitialQuestionCount(), actual.getInitialQuestionCount());
        assertEquals(expected.getCurrentQuestionId(), actual.getCurrentQuestionId());
        assertEquals(expected.getPendingQuestionIds(), actual.getPendingQuestionIds());
        assertEquals(expected.getScores().size(), actual.getScores().size());
        for (int index = 0; index < expected.getScores().size(); index++) {
            Score expectedScore = expected.getScores().get(index);
            Score actualScore = actual.getScores().get(index);
            assertEquals(expectedScore.getRawError(), actualScore.getRawError(), 0.0);
            assertEquals(expectedScore.getPoints(), actualScore.getPoints());
        }
    }

    private static List<Question> questions(int count) {
        List<Question> questions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            questions.add(question("question-" + index, index * 100.0));
        }
        return questions;
    }

    private static Question question(String id, double trueValue) {
        return Question.builder()
                .id(id)
                .prompt("Estimate " + id)
                .trueValue(trueValue)
                .unit("units")
                .measurementBasis("test value")
                .build();
    }
}
