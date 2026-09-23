package de.christiankorn.giveortake.data;

import de.christiankorn.giveortake.core.Level;

/** Identifies the completed session that established one mode-specific personal best. */
public final class PersonalBestStatistics {
    private final Level level;
    private final long sessionId;
    private final long endedAtEpochMillis;
    private final double value;
    private final int answerCount;

    PersonalBestStatistics(
            Level level,
            long sessionId,
            long endedAtEpochMillis,
            double value,
            int answerCount
    ) {
        this.level = level;
        this.sessionId = sessionId;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.value = value;
        this.answerCount = answerCount;
    }

    /** Returns the mode whose sessions were compared. */
    public Level getLevel() {
        return level;
    }

    /** Returns the record-setting session identifier. */
    public long getSessionId() {
        return sessionId;
    }

    /** Returns the UTC time at which the record-setting session ended. */
    public long getEndedAtEpochMillis() {
        return endedAtEpochMillis;
    }

    /** Returns mean points for point mode or mean interval loss for interval mode. */
    public double getValue() {
        return value;
    }

    /** Returns how many answers contributed to the record. */
    public int getAnswerCount() {
        return answerCount;
    }
}
