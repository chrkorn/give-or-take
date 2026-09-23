package de.christiankorn.giveortake.data;

import de.christiankorn.giveortake.core.Guess;

/** Represents one raw answer row read from persistent quiz history. */
public final class StoredAnswer {
    private final long id;
    private final long sessionId;
    private final int sequenceNumber;
    private final String questionId;
    private final String categoryAtAnswer;
    private final double trueValueAtAnswer;
    private final Guess guess;
    private final long answeredAtEpochMillis;

    StoredAnswer(
            long id,
            long sessionId,
            int sequenceNumber,
            String questionId,
            String categoryAtAnswer,
            double trueValueAtAnswer,
            Guess guess,
            long answeredAtEpochMillis
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.sequenceNumber = sequenceNumber;
        this.questionId = questionId;
        this.categoryAtAnswer = categoryAtAnswer;
        this.trueValueAtAnswer = trueValueAtAnswer;
        this.guess = guess;
        this.answeredAtEpochMillis = answeredAtEpochMillis;
    }

    /** Returns the SQLite row identifier. */
    public long getId() {
        return id;
    }

    /** Returns the parent session identifier. */
    public long getSessionId() {
        return sessionId;
    }

    /** Returns the one-based submission position. */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /** Returns the stable identifier of the answered question. */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the subject grouping captured when the answer was submitted.
     *
     * @return the category, or {@code null} for history written before schema version 2
     */
    public String getCategoryAtAnswer() {
        return categoryAtAnswer;
    }

    /** Returns the authoritative value used when the answer was scored. */
    public double getTrueValueAtAnswer() {
        return trueValueAtAnswer;
    }

    /** Returns the reconstructed point or interval guess. */
    public Guess getGuess() {
        return guess;
    }

    /** Returns the UTC Unix epoch millisecond at submission. */
    public long getAnsweredAtEpochMillis() {
        return answeredAtEpochMillis;
    }
}
