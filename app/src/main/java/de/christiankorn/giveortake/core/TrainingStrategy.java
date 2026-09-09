package de.christiankorn.giveortake.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

/**
 * Schedules distinct questions for one training session and delays questions answered incorrectly.
 *
 * <p>The requested session length limits the initial, distinct questions. A {@link Correctness#WRONG}
 * answer is placed behind the next two scheduled questions, so remedial questions can extend the
 * session. Near the end of a session, fewer than two questions may remain; the repeat is then placed
 * after every remaining question. If none remains, the repeat is omitted because presenting it next
 * would violate the no-immediate-repeat rule.</p>
 *
 * <p>The caller supplies the source of randomness. This keeps ordering reproducible in tests and
 * prevents the domain layer from hiding a second random-number generator.</p>
 */
public final class TrainingStrategy {
    /** Number of other questions placed before a question answered incorrectly. */
    public static final int REQUEUE_DELAY = 2;

    private final List<Question> pendingQuestions;
    private Question currentQuestion;

    /**
     * Creates a session from a defensive copy of the supplied question pool.
     *
     * <p>At most {@code sessionLength} distinct questions are selected. When the pool is smaller,
     * each available question is scheduled once rather than repeating correctly answered questions
     * merely to reach the requested length.</p>
     *
     * @param questionPool questions available for this session
     * @param sessionLength maximum number of distinct questions in the initial schedule
     * @param random caller-owned source of randomness used to shuffle the initial schedule
     * @throws IllegalArgumentException if the pool is null, empty, contains null or duplicate
     *                                  questions, the session length is not positive, or the random
     *                                  source is null
     */
    public TrainingStrategy(List<Question> questionPool, int sessionLength, Random random) {
        validateArguments(questionPool, sessionLength, random);

        List<Question> shuffledQuestions = new ArrayList<>(questionPool);
        Collections.shuffle(shuffledQuestions, random);
        int initialQuestionCount = Math.min(sessionLength, shuffledQuestions.size());
        pendingQuestions = new ArrayList<>(
                shuffledQuestions.subList(0, initialQuestionCount)
        );
    }

    /**
     * Returns and marks the next scheduled question as awaiting an answer.
     *
     * @return the next question in the session
     * @throws IllegalStateException if the previous question has not been answered
     * @throws NoSuchElementException if the session is complete
     */
    public Question nextQuestion() {
        if (currentQuestion != null) {
            throw new IllegalStateException(
                    "the current question must be answered before requesting the next question"
            );
        }
        if (pendingQuestions.isEmpty()) {
            throw new NoSuchElementException("the training session is complete");
        }

        currentQuestion = pendingQuestions.remove(0);
        return currentQuestion;
    }

    /**
     * Records the correctness band for the current question and updates the repeat schedule.
     *
     * <p>Only {@link Correctness#WRONG} schedules a repeat, as required by ADR 0006. A repeat is
     * never added to an otherwise empty schedule because it would be the same question twice in
     * immediate succession.</p>
     *
     * @param correctness classification of the answer to the current question
     * @throws IllegalArgumentException if correctness is null
     * @throws IllegalStateException if no question is awaiting an answer
     */
    public void recordAnswer(Correctness correctness) {
        if (correctness == null) {
            throw new IllegalArgumentException("correctness must not be null");
        }
        if (currentQuestion == null) {
            throw new IllegalStateException("no question is awaiting an answer");
        }

        if (correctness == Correctness.WRONG && !pendingQuestions.isEmpty()) {
            int insertionIndex = Math.min(REQUEUE_DELAY, pendingQuestions.size());
            pendingQuestions.add(insertionIndex, currentQuestion);
        }
        currentQuestion = null;
    }

    /**
     * Reports whether all scheduled questions have been answered.
     *
     * @return {@code true} when no question is awaiting an answer and none remains scheduled
     */
    public boolean isComplete() {
        return currentQuestion == null && pendingQuestions.isEmpty();
    }

    private static void validateArguments(
            List<Question> questionPool,
            int sessionLength,
            Random random
    ) {
        if (questionPool == null) {
            throw new IllegalArgumentException("questionPool must not be null");
        }
        if (questionPool.isEmpty()) {
            throw new IllegalArgumentException("questionPool must not be empty");
        }
        if (sessionLength <= 0) {
            throw new IllegalArgumentException("sessionLength must be greater than zero");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }

        Set<Question> uniqueQuestions = new HashSet<>();
        for (Question question : questionPool) {
            if (question == null) {
                throw new IllegalArgumentException("questionPool must not contain null questions");
            }
            if (!uniqueQuestions.add(question)) {
                throw new IllegalArgumentException(
                        "questionPool must not contain duplicate question identifiers"
                );
            }
        }
    }
}
