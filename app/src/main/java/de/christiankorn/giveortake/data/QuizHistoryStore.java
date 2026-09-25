package de.christiankorn.giveortake.data;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import de.christiankorn.giveortake.core.Level;

/**
 * Owns the process-scoped serial queue used for quiz-history database operations.
 *
 * <p>A single worker preserves the order in which a session is started, answered, and completed.
 * The store owns only an application-context DAO and immutable session inputs, so queued work can
 * finish after an Activity is destroyed without retaining that Activity.</p>
 */
public final class QuizHistoryStore {
    private static final long TEST_TIMEOUT_SECONDS = 5L;

    private final QuizHistoryDao dao;
    private final ExecutorService executor;
    private final Map<String, QuizHistorySession> sessions = new HashMap<>();

    /**
     * Creates a process-scoped store backed by one named database worker.
     *
     * @param context context used to open the application-private database
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public QuizHistoryStore(Context context) {
        this(
                new QuizHistoryDao(requireApplicationContext(context)),
                Executors.newSingleThreadExecutor(runnable ->
                        new Thread(runnable, "quiz-history-database")
                )
        );
    }

    QuizHistoryStore(QuizHistoryDao dao, ExecutorService executor) {
        if (dao == null) {
            throw new IllegalArgumentException("dao must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        this.dao = dao;
        this.executor = executor;
    }

    /**
     * Begins a new persisted quiz session on the serial database queue.
     *
     * @param token process-unique token saved with the Activity state
     * @param level curriculum level and answer mode
     * @param initialQuestionCount planned distinct-question count before repeats
     * @param startedAtEpochMillis non-negative UTC Unix epoch millisecond
     * @return handle that accepts ordered answer and completion writes immediately
     * @throws IllegalArgumentException if an argument is invalid or the token is already reused
     */
    public synchronized QuizHistorySession startSession(
            String token,
            Level level,
            int initialQuestionCount,
            long startedAtEpochMillis
    ) {
        requireDescriptor(token, level, initialQuestionCount, startedAtEpochMillis);
        QuizHistorySession attached = sessions.get(token);
        if (attached != null) {
            attached.requireDescriptor(level, initialQuestionCount, startedAtEpochMillis);
            return attached;
        }

        QuizHistorySession session = new QuizHistorySession(
                this,
                dao,
                token,
                level,
                initialQuestionCount,
                startedAtEpochMillis,
                false
        );
        sessions.put(token, session);
        execute(session::start);
        return session;
    }

    /**
     * Reattaches to an existing in-memory session or restores its database identity asynchronously.
     *
     * <p>A zero {@code savedSessionId} means state was saved while the original asynchronous insert
     * was still in flight. In that case the DAO recovers the row from the stable session inputs.</p>
     *
     * @param token token restored from the Activity state
     * @param level expected curriculum level and answer mode
     * @param initialQuestionCount expected planned distinct-question count
     * @param startedAtEpochMillis original session start timestamp
     * @param savedSessionId positive stored identifier, or zero while creation was pending
     * @param expectedComplete whether the restored core session is already complete
     * @return lifecycle-independent handle for the restored session
     * @throws IllegalArgumentException if an argument is invalid or the token is reused
     */
    public synchronized QuizHistorySession restoreSession(
            String token,
            Level level,
            int initialQuestionCount,
            long startedAtEpochMillis,
            long savedSessionId,
            boolean expectedComplete
    ) {
        requireDescriptor(token, level, initialQuestionCount, startedAtEpochMillis);
        if (savedSessionId < 0L) {
            throw new IllegalArgumentException("savedSessionId must not be negative");
        }
        QuizHistorySession attached = sessions.get(token);
        if (attached != null) {
            attached.requireDescriptor(level, initialQuestionCount, startedAtEpochMillis);
            return attached;
        }

        QuizHistorySession session = new QuizHistorySession(
                this,
                dao,
                token,
                level,
                initialQuestionCount,
                startedAtEpochMillis,
                expectedComplete
        );
        sessions.put(token, session);
        execute(() -> session.restore(savedSessionId));
        return session;
    }

    /**
     * Loads exact history counts after all earlier queued writes have finished.
     *
     * <p>Callbacks run on the history worker. An Activity must post UI work to the main thread.</p>
     *
     * @param onSuccess receives immutable counts when the query succeeds
     * @param onFailure receives the database failure
     * @throws IllegalArgumentException if either callback is {@code null}
     */
    public void loadHistoryCounts(
            Consumer<HistoryCounts> onSuccess,
            Consumer<RuntimeException> onFailure
    ) {
        requireCallbacks(onSuccess, onFailure);
        execute(() -> {
            try {
                onSuccess.accept(dao.getHistoryCounts());
            } catch (RuntimeException exception) {
                onFailure.accept(exception);
            }
        });
    }

    /**
     * Deletes all history after earlier writes and before later writes on the serial queue.
     *
     * <p>Callbacks run on the history worker. Personal-best preferences are deliberately cleared
     * by the caller only after this database operation succeeds.</p>
     *
     * @param onSuccess invoked after the transaction commits
     * @param onFailure receives the database failure
     * @throws IllegalArgumentException if either callback is {@code null}
     */
    public void clearHistory(Runnable onSuccess, Consumer<RuntimeException> onFailure) {
        if (onSuccess == null || onFailure == null) {
            throw new IllegalArgumentException("callbacks must not be null");
        }
        execute(() -> {
            try {
                dao.clearHistory();
                onSuccess.run();
            } catch (RuntimeException exception) {
                onFailure.accept(exception);
            }
        });
    }

    void execute(Runnable operation) {
        executor.execute(operation);
    }

    synchronized void forget(String token, QuizHistorySession session) {
        sessions.remove(token, session);
    }

    void awaitIdle() {
        Future<?> marker = executor.submit(() -> {
            // Reaching this marker proves that every earlier serial operation has finished.
        });
        try {
            marker.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for history operations", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("History operations did not finish", exception);
        }
    }

    void closeForTest() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("History executor did not terminate");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while closing history store", exception);
        } finally {
            dao.close();
        }
    }

    private static Context requireApplicationContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return context.getApplicationContext();
    }

    private static <T> void requireCallbacks(
            Consumer<T> onSuccess,
            Consumer<RuntimeException> onFailure
    ) {
        if (onSuccess == null || onFailure == null) {
            throw new IllegalArgumentException("callbacks must not be null");
        }
    }

    private static void requireDescriptor(
            String token,
            Level level,
            int initialQuestionCount,
            long startedAtEpochMillis
    ) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (initialQuestionCount <= 0) {
            throw new IllegalArgumentException("initialQuestionCount must be greater than zero");
        }
        if (startedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("startedAtEpochMillis must not be negative");
        }
    }

    /** Exact row counts shown before the player confirms a statistics reset. */
    public static final class HistoryCounts {
        private final int sessionCount;
        private final int answerCount;

        HistoryCounts(int sessionCount, int answerCount) {
            this.sessionCount = sessionCount;
            this.answerCount = answerCount;
        }

        /** Returns the number of session rows that will be deleted. */
        public int getSessionCount() {
            return sessionCount;
        }

        /** Returns the number of answer rows that will be deleted. */
        public int getAnswerCount() {
            return answerCount;
        }
    }
}
