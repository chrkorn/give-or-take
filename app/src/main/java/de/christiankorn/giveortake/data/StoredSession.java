package de.christiankorn.giveortake.data;

import de.christiankorn.giveortake.core.Level;

/** Represents one session row read from persistent quiz history. */
public final class StoredSession {
    /** Describes whether a persisted session is active or how it ended. */
    public enum State {
        /** The session can still receive answers. */
        IN_PROGRESS,
        /** The session reached its scheduled end. */
        COMPLETED,
        /** The player explicitly left before completion. */
        ABANDONED
    }

    private final long id;
    private final Level level;
    private final State state;
    private final int initialQuestionCount;
    private final long startedAtEpochMillis;
    private final Long endedAtEpochMillis;

    StoredSession(
            long id,
            Level level,
            State state,
            int initialQuestionCount,
            long startedAtEpochMillis,
            Long endedAtEpochMillis
    ) {
        this.id = id;
        this.level = level;
        this.state = state;
        this.initialQuestionCount = initialQuestionCount;
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.endedAtEpochMillis = endedAtEpochMillis;
    }

    /** Returns the SQLite row identifier. */
    public long getId() {
        return id;
    }

    /** Returns the curriculum level and answer mode. */
    public Level getLevel() {
        return level;
    }

    /** Returns the persisted lifecycle state. */
    public State getState() {
        return state;
    }

    /** Returns the planned distinct-question count before repeats. */
    public int getInitialQuestionCount() {
        return initialQuestionCount;
    }

    /** Returns the UTC Unix epoch millisecond at which the session began. */
    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    /**
     * Returns the UTC Unix epoch millisecond at which the session ended.
     *
     * @return the end time, or {@code null} while the session is in progress
     */
    public Long getEndedAtEpochMillis() {
        return endedAtEpochMillis;
    }
}
