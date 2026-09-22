package de.christiankorn.giveortake.data;

import de.christiankorn.giveortake.core.Guess;
import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;

/** Collects one validated raw answer before it is inserted into quiz history. */
public final class AnswerDraft {
    private final int sequenceNumber;
    private final Question question;
    private final Guess guess;
    private final long answeredAtEpochMillis;

    /**
     * Creates an answer ready for persistence.
     *
     * @param sequenceNumber one-based submission position within the session
     * @param question question whose truth value was used for scoring
     * @param guess supported point or interval guess supplied by the player
     * @param answeredAtEpochMillis non-negative UTC Unix epoch millisecond
     * @throws IllegalArgumentException if an argument is invalid or the guess type is unsupported
     */
    public AnswerDraft(
            int sequenceNumber,
            Question question,
            Guess guess,
            long answeredAtEpochMillis
    ) {
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be greater than zero");
        }
        if (question == null) {
            throw new IllegalArgumentException("question must not be null");
        }
        if (!(guess instanceof PointGuess) && !(guess instanceof IntervalGuess)) {
            throw new IllegalArgumentException("guess must be a point or interval guess");
        }
        if (answeredAtEpochMillis < 0L) {
            throw new IllegalArgumentException("answeredAtEpochMillis must not be negative");
        }
        this.sequenceNumber = sequenceNumber;
        this.question = question;
        this.guess = guess;
        this.answeredAtEpochMillis = answeredAtEpochMillis;
    }

    /** Returns the one-based submission position. */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /** Returns the answered question, including its scoring-time truth value. */
    public Question getQuestion() {
        return question;
    }

    /** Returns the player's immutable point or interval guess. */
    public Guess getGuess() {
        return guess;
    }

    /** Returns the UTC Unix epoch millisecond at submission. */
    public long getAnsweredAtEpochMillis() {
        return answeredAtEpochMillis;
    }
}
