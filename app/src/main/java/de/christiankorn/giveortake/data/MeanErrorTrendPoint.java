package de.christiankorn.giveortake.data;

/** Represents one point-estimate session in the mean log-relative-error trend. */
public final class MeanErrorTrendPoint {
    private final long sessionId;
    private final long endedAtEpochMillis;
    private final int sampleSize;
    private final double meanLogRelativeError;

    MeanErrorTrendPoint(
            long sessionId,
            long endedAtEpochMillis,
            int sampleSize,
            double meanLogRelativeError
    ) {
        this.sessionId = sessionId;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.sampleSize = sampleSize;
        this.meanLogRelativeError = meanLogRelativeError;
    }

    /** Returns the persisted session identifier. */
    public long getSessionId() {
        return sessionId;
    }

    /** Returns the UTC time at which the session ended. */
    public long getEndedAtEpochMillis() {
        return endedAtEpochMillis;
    }

    /** Returns how many point answers contributed to the mean. */
    public int getSampleSize() {
        return sampleSize;
    }

    /** Returns the session's arithmetic mean log-relative error. */
    public double getMeanLogRelativeError() {
        return meanLogRelativeError;
    }
}
