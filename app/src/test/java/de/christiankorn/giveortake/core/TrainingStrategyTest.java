package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests deterministic session scheduling and delayed repetition of wrong answers.
 */
public class TrainingStrategyTest {
    private static final long FIXED_RANDOM_SEED = 24680L;

    /** Verifies that a correct-answer session stops at its requested length. */
    @Test
    public void session_whenEveryAnswerIsCorrect_yieldsExactlyRequestedQuestionCount() {
        TrainingStrategy strategy = strategyWithQuestions(6, 4);

        int presentedQuestions = answerEveryQuestion(strategy, Correctness.CORRECT);

        assertEquals(4, presentedQuestions);
        assertTrue(strategy.isComplete());
    }

    /** Verifies that a wrong question returns after exactly two other questions. */
    @Test
    public void recordAnswer_whenAnswerIsWrong_repeatsAfterTwoInterveningQuestions() {
        TrainingStrategy strategy = strategyWithQuestions(5, 5);

        Question wrongQuestion = strategy.nextQuestion();
        strategy.recordAnswer(Correctness.WRONG);

        Question firstInterveningQuestion = strategy.nextQuestion();
        strategy.recordAnswer(Correctness.CORRECT);
        Question secondInterveningQuestion = strategy.nextQuestion();
        strategy.recordAnswer(Correctness.CORRECT);
        Question repeatedQuestion = strategy.nextQuestion();

        assertNotEquals(wrongQuestion, firstInterveningQuestion);
        assertNotEquals(wrongQuestion, secondInterveningQuestion);
        assertEquals(wrongQuestion, repeatedQuestion);
    }

    /** Verifies that the schedule never presents the same question twice in succession. */
    @Test
    public void session_withWrongAnswers_neverPresentsSameQuestionTwiceInARow() {
        TrainingStrategy strategy = strategyWithQuestions(5, 5);
        List<Question> presentedQuestions = new ArrayList<>();

        Question firstQuestion = strategy.nextQuestion();
        presentedQuestions.add(firstQuestion);
        strategy.recordAnswer(Correctness.WRONG);

        while (!strategy.isComplete()) {
            Question nextQuestion = strategy.nextQuestion();
            presentedQuestions.add(nextQuestion);
            strategy.recordAnswer(Correctness.CORRECT);
        }

        for (int i = 1; i < presentedQuestions.size(); i++) {
            assertNotEquals(presentedQuestions.get(i - 1), presentedQuestions.get(i));
        }
    }

    /** Verifies that a close answer does not enter the repeat queue defined by ADR 0006. */
    @Test
    public void recordAnswer_whenAnswerIsClose_doesNotRepeatQuestion() {
        TrainingStrategy strategy = strategyWithQuestions(4, 4);

        int presentedQuestions = answerEveryQuestion(strategy, Correctness.CLOSE);

        assertEquals(4, presentedQuestions);
    }

    /** Verifies that a small pool ends after all of its distinct questions have been asked. */
    @Test
    public void session_whenPoolIsSmallerThanRequestedLength_asksEachQuestionOnce() {
        TrainingStrategy strategy = strategyWithQuestions(2, 5);
        List<Question> presentedQuestions = new ArrayList<>();

        while (!strategy.isComplete()) {
            presentedQuestions.add(strategy.nextQuestion());
            strategy.recordAnswer(Correctness.CORRECT);
        }

        assertEquals(2, presentedQuestions.size());
        assertNotEquals(presentedQuestions.get(0), presentedQuestions.get(1));
    }

    /** Verifies that a short tail uses its longest possible non-immediate repeat delay. */
    @Test
    public void recordAnswer_whenOnlyOneQuestionRemains_repeatsAfterThatQuestion() {
        TrainingStrategy strategy = strategyWithQuestions(2, 2);

        Question wrongQuestion = strategy.nextQuestion();
        strategy.recordAnswer(Correctness.WRONG);
        Question interveningQuestion = strategy.nextQuestion();
        strategy.recordAnswer(Correctness.CORRECT);
        Question repeatedQuestion = strategy.nextQuestion();

        assertNotEquals(wrongQuestion, interveningQuestion);
        assertEquals(wrongQuestion, repeatedQuestion);
    }

    /** Verifies that a one-question pool completes without an unavoidable immediate duplicate. */
    @Test
    public void session_whenOnlyOneQuestionExists_completesAfterThatQuestion() {
        TrainingStrategy strategy = strategyWithQuestions(1, 5);

        strategy.nextQuestion();
        strategy.recordAnswer(Correctness.WRONG);

        assertTrue(strategy.isComplete());
    }

    /** Verifies that an empty pool fails immediately rather than entering a scheduling loop. */
    @Test
    public void constructor_whenPoolIsEmpty_throwsClearException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TrainingStrategy(
                        Collections.emptyList(),
                        5,
                        new Random(FIXED_RANDOM_SEED)
                )
        );

        assertEquals("questionPool must not be empty", exception.getMessage());
    }

    /** Verifies that equal seeds and pools create equal question orders. */
    @Test
    public void session_withEqualRandomSeeds_isDeterministic() {
        List<Question> pool = questions(6);
        TrainingStrategy first = new TrainingStrategy(pool, 6, new Random(FIXED_RANDOM_SEED));
        TrainingStrategy second = new TrainingStrategy(pool, 6, new Random(FIXED_RANDOM_SEED));

        List<String> firstOrder = answerAndCollectIds(first);
        List<String> secondOrder = answerAndCollectIds(second);

        assertEquals(firstOrder, secondOrder);
    }

    /** Verifies that completion is not reported while a final question awaits its answer. */
    @Test
    public void isComplete_whileLastQuestionAwaitsAnswer_returnsFalse() {
        TrainingStrategy strategy = strategyWithQuestions(1, 1);

        strategy.nextQuestion();

        assertFalse(strategy.isComplete());
    }

    /** Verifies that callers cannot request another question before answering the current one. */
    @Test
    public void nextQuestion_whenCurrentQuestionIsUnanswered_throwsClearException() {
        TrainingStrategy strategy = strategyWithQuestions(2, 2);
        strategy.nextQuestion();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                strategy::nextQuestion
        );

        assertEquals(
                "the current question must be answered before requesting the next question",
                exception.getMessage()
        );
    }

    /** Verifies that requesting a question after completion gives a clear signal. */
    @Test
    public void nextQuestion_whenSessionIsComplete_throwsClearException() {
        TrainingStrategy strategy = strategyWithQuestions(1, 1);
        strategy.nextQuestion();
        strategy.recordAnswer(Correctness.CORRECT);

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                strategy::nextQuestion
        );

        assertEquals("the training session is complete", exception.getMessage());
    }

    private static TrainingStrategy strategyWithQuestions(int poolSize, int sessionLength) {
        return new TrainingStrategy(
                questions(poolSize),
                sessionLength,
                new Random(FIXED_RANDOM_SEED)
        );
    }

    private static int answerEveryQuestion(
            TrainingStrategy strategy,
            Correctness correctness
    ) {
        int questionCount = 0;
        while (!strategy.isComplete()) {
            strategy.nextQuestion();
            questionCount++;
            strategy.recordAnswer(correctness);
        }
        return questionCount;
    }

    private static List<String> answerAndCollectIds(TrainingStrategy strategy) {
        List<String> questionIds = new ArrayList<>();
        while (!strategy.isComplete()) {
            questionIds.add(strategy.nextQuestion().getId());
            strategy.recordAnswer(Correctness.CORRECT);
        }
        return questionIds;
    }

    private static List<Question> questions(int count) {
        List<Question> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add(question("question-" + i));
        }
        return questions;
    }

    private static Question question(String id) {
        return Question.builder()
                .id(id)
                .prompt("Estimate for " + id)
                .trueValue(100.0)
                .unit("units")
                .difficulty(1)
                .build();
    }
}
