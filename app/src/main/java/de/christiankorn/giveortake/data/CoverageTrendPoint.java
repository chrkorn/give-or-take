package de.christiankorn.giveortake.data;

/** Represents one rolling window in the interval-coverage trend. */
public final class CoverageTrendPoint {
    private final long endingAnswerEpochMillis;
    private final long endingSessionId;
    private final int sampleSize;
    private final int hitCount;

    CoverageTrendPoint(
            long endingAnswerEpochMillis,
            long endingSessionId,
            int sampleSize,
            int hitCount
    ) {
        this.endingAnswerEpochMillis = endingAnswerEpochMillis;
        this.endingSessionId = endingSessionId;
        this.sampleSize = sampleSize;
        this.hitCount = hitCount;
    }

    /** Returns the UTC timestamp of the newest answer in this rolling window. */
    public long getEndingAnswerEpochMillis() {
        return endingAnswerEpochMillis;
    }

    /** Returns the session containing the newest answer in this rolling window. */
    public long getEndingSessionId() {
        return endingSessionId;
    }

    /** Returns the number of interval answers in this window. */
    public int getSampleSize() {
        return sampleSize;
    }

    /** Returns how many intervals in this window contained the truth. */
    public int getHitCount() {
        return hitCount;
    }

    /** Returns the observed coverage within this rolling window. */
    public double getCoverage() {
        return (double) hitCount / sampleSize;
    }
}
