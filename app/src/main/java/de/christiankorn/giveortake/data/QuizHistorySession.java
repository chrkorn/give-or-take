package de.christiankorn.giveortake.data;

import android.util.Log;

import de.christiankorn.giveortake.core.Level;

/**
 * Accepts ordered persistence commands for one quiz without owning an Activity reference.
 *
 * <p>Methods enqueue work and return immediately. The parent {@link QuizHistoryStore} executes the
 * commands serially, so callers may submit an answer even while the session insert is still
 * pending.</p>
 */
public final class QuizHistorySession {
    private static final String LOG_TAG = "QuizHistory";

    private final QuizHistoryStore store;
    private final QuizHistoryDao dao;
    private final String token;
    private final Level level;
    private final int initialQuestionCount;
    private final long startedAtEpochMillis;
    private volatile long sessionId;
    private volatile boolean ended;
    private volatile RuntimeException failure;

    QuizHistorySession(
            QuizHistoryStore store,
            QuizHistoryDao dao,
            String token,
            Level level,
            int initialQuestionCount,
            long startedAtEpochMillis,
            boolean ended
    ) {
        this.store = store;
        this.dao = dao;
        this.token = token;
        this.level = level;
        this.initialQuestionCount = initialQuestionCount;
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.ended = ended;
    }

    /**
     * Returns the SQLite identifier once the queued start or restore operation has finished.
     *
     * @return positive session identifier, or zero while initialisation is pending
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * Queues one accepted non-final answer for immediate serial persistence.
     *
     * @param answer validated raw answer to store
     * @throws IllegalArgumentException if {@code answer} is {@code null}
     * @throws IllegalStateException if this session has already ended or initialisation failed
     */
    public void recordAnswer(AnswerDraft answer) {
        if (answer == null) {
            throw new IllegalArgumentException("answer must not be null");
        }
        requireActive();
        enqueue("record answer", () -> dao.recordAnswer(requireSessionId(), answer));
    }

    /**
     * Queues the final answer and completion metadata in the DAO's single transaction.
     *
     * @param answer validated final raw answer
     * @param endedAtEpochMillis non-negative UTC Unix epoch millisecond
     * @throws IllegalArgumentException if an argument is invalid
     * @throws IllegalStateException if this session has already ended or initialisation failed
     */
    public synchronized void recordFinalAnswerAndCompleteSession(
            AnswerDraft answer,
            long endedAtEpochMillis
    ) {
        if (answer == null) {
            throw new IllegalArgumentException("answer must not be null");
        }
        if (endedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("endedAtEpochMillis must not be negative");
        }
        requireActive();
        ended = true;
        enqueue("complete session", () -> {
            dao.recordFinalAnswerAndCompleteSession(
                    requireSessionId(),
                    answer,
                    endedAtEpochMillis
            );
            store.forget(token, this);
        });
    }

    /**
     * Queues an explicit abandoned-session marker if this session has not already ended.
     *
     * @param endedAtEpochMillis non-negative UTC Unix epoch millisecond
     * @throws IllegalArgumentException if the timestamp is negative
     * @throws IllegalStateException if asynchronous initialisation has already failed
     */
    public synchronized void abandon(long endedAtEpochMillis) {
        if (endedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("endedAtEpochMillis must not be negative");
        }
        requireHealthy();
        if (ended) {
            return;
        }
        ended = true;
        enqueue("abandon session", () -> {
            dao.abandonSession(requireSessionId(), endedAtEpochMillis);
            store.forget(token, this);
        });
    }

    void start() {
        runGuarded("start session", () -> sessionId = dao.startSession(
                level,
                initialQuestionCount,
                startedAtEpochMillis
        ));
    }

    void restore(long savedSessionId) {
        runGuarded("restore session", () -> {
            StoredSession storedSession = savedSessionId > 0L
                    ? dao.findSession(savedSessionId).orElseThrow(() ->
                            new IllegalStateException("Saved quiz history session no longer exists")
                    )
                    : dao.findOrStartSession(
                            level,
                            initialQuestionCount,
                            startedAtEpochMillis
                    );
            requireMatching(storedSession);
            sessionId = storedSession.getId();
        });
    }

    void requireDescriptor(
            Level expectedLevel,
            int expectedQuestionCount,
            long expectedStartedAt
    ) {
        if (level != expectedLevel
                || initialQuestionCount != expectedQuestionCount
                || startedAtEpochMillis != expectedStartedAt) {
            throw new IllegalArgumentException("token is already used by another history session");
        }
    }

    RuntimeException getFailure() {
        return failure;
    }

    private void requireMatching(StoredSession storedSession) {
        if (storedSession.getLevel() != level
                || storedSession.getInitialQuestionCount() != initialQuestionCount
                || storedSession.getStartedAtEpochMillis() != startedAtEpochMillis) {
            throw new IllegalStateException("Saved quiz and persisted history identity disagree");
        }
        StoredSession.State expectedState = ended
                ? StoredSession.State.COMPLETED
                : StoredSession.State.IN_PROGRESS;
        if (storedSession.getState() != expectedState) {
            throw new IllegalStateException(
                    "Saved quiz and persisted history completion states disagree"
            );
        }
    }

    private synchronized void requireActive() {
        requireHealthy();
        if (ended) {
            throw new IllegalStateException("history session has already ended");
        }
    }

    private void requireHealthy() {
        RuntimeException currentFailure = failure;
        if (currentFailure != null) {
            throw new IllegalStateException("history session initialisation failed", currentFailure);
        }
    }

    private long requireSessionId() {
        if (sessionId <= 0L) {
            throw new IllegalStateException("history session has not been initialised");
        }
        return sessionId;
    }

    private void enqueue(String operationName, Runnable operation) {
        store.execute(() -> runGuarded(operationName, operation));
    }

    private void runGuarded(String operationName, Runnable operation) {
        if (failure != null) {
            return;
        }
        try {
            operation.run();
        } catch (RuntimeException exception) {
            failure = exception;
            Log.e(LOG_TAG, "Could not " + operationName, exception);
        }
    }
}
