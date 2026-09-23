package de.christiankorn.giveortake.data;

import java.util.OptionalDouble;

import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.SessionResult;

/** Represents one ended session in the paged recent-history list. */
public final class RecentSessionStatistics {
    private final long sessionId;
    private final Level level;
    private final StoredSession.State state;
    private final long startedAtEpochMillis;
    private final long endedAtEpochMillis;
    private final int answerCount;
    private final OptionalDouble meanRawValue;
    private final OptionalDouble meanPoints;
    private final int correctCount;
    private final int closeCount;
    private final int wrongCount;
    private final long calibrationHitCount;

    RecentSessionStatistics(StoredSession session, SessionResult result) {
        sessionId = session.getId();
        level = session.getLevel();
        state = session.getState();
        startedAtEpochMillis = session.getStartedAtEpochMillis();
        endedAtEpochMillis = session.getEndedAtEpochMillis();
        answerCount = result.getAnsweredQuestionCount();
        meanRawValue = result.getMeanRawError();
        meanPoints = result.getMeanPoints();
        correctCount = result.getCorrectCount();
        closeCount = result.getCloseCount();
        wrongCount = result.getWrongCount();
        calibrationHitCount = result.getCalibrationHitCount();
    }

    /** Returns the persisted session identifier. */
    public long getSessionId() {
        return sessionId;
    }

    /** Returns the quiz mode. */
    public Level getLevel() {
        return level;
    }

    /** Returns whether this ended session was completed or abandoned. */
    public StoredSession.State getState() {
        return state;
    }

    /** Returns the session start time as UTC Unix epoch milliseconds. */
    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    /** Returns the session end time as UTC Unix epoch milliseconds. */
    public long getEndedAtEpochMillis() {
        return endedAtEpochMillis;
    }

    /** Returns the number of accepted answers, including repeats. */
    public int getAnswerCount() {
        return answerCount;
    }

    /** Returns mean log-relative error or mean interval loss, absent for an empty session. */
    public OptionalDouble getMeanRawValue() {
        return meanRawValue;
    }

    /** Returns mean points for point mode, absent otherwise. */
    public OptionalDouble getMeanPoints() {
        return meanPoints;
    }

    /** Returns the count in the point mode's correct band. */
    public int getCorrectCount() {
        return correctCount;
    }

    /** Returns the count in the point mode's close band. */
    public int getCloseCount() {
        return closeCount;
    }

    /** Returns the count in the point mode's wrong band. */
    public int getWrongCount() {
        return wrongCount;
    }

    /** Returns how many interval answers contained the truth. */
    public long getCalibrationHitCount() {
        return calibrationHitCount;
    }
}
