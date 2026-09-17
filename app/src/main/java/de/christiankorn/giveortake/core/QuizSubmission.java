package de.christiankorn.giveortake.core;

/**
 * Reports the domain result of submitting one point estimate to a quiz session.
 */
public final class QuizSubmission {
    private final Question answeredQuestion;
    private final Score score;
    private final Correctness correctness;
    private final Question nextQuestion;

    QuizSubmission(
            Question answeredQuestion,
            Score score,
            Correctness correctness,
            Question nextQuestion
    ) {
        this.answeredQuestion = answeredQuestion;
        this.score = score;
        this.correctness = correctness;
        this.nextQuestion = nextQuestion;
    }

    /**
     * Returns the question against which the submitted guess was scored.
     *
     * @return the answered question
     */
    public Question getAnsweredQuestion() {
        return answeredQuestion;
    }

    /**
     * Returns the continuous score produced for the guess.
     *
     * @return the immutable score
     */
    public Score getScore() {
        return score;
    }

    /**
     * Returns the correctness band used by the training strategy.
     *
     * @return the classified correctness
     */
    public Correctness getCorrectness() {
        return correctness;
    }

    /**
     * Returns the next question selected after recording this answer.
     *
     * @return the next question, or {@code null} when the session is complete
     */
    public Question getNextQuestion() {
        return nextQuestion;
    }

    /**
     * Reports whether this submission completed the session.
     *
     * @return {@code true} when no next question remains
     */
    public boolean isSessionComplete() {
        return nextQuestion == null;
    }
}
