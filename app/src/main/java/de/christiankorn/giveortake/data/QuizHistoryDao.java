package de.christiankorn.giveortake.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.christiankorn.giveortake.core.Guess;
import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.PointGuess;

/**
 * Stores and retrieves quiz history without exposing SQL or cursor handling to Activities.
 *
 * <p>The database owns raw scoring inputs, not derived scores. Callers can therefore apply the
 * current framework-independent scoring policies when presenting historical statistics.</p>
 */
public final class QuizHistoryDao implements AutoCloseable {
    private static final String[] SESSION_PROJECTION = {
            QuizHistoryContract.Sessions._ID,
            QuizHistoryContract.Sessions.COLUMN_LEVEL,
            QuizHistoryContract.Sessions.COLUMN_STATE,
            QuizHistoryContract.Sessions.COLUMN_INITIAL_QUESTION_COUNT,
            QuizHistoryContract.Sessions.COLUMN_STARTED_AT_EPOCH_MS,
            QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS
    };

    private static final String[] ANSWER_PROJECTION = {
            QuizHistoryContract.Answers._ID,
            QuizHistoryContract.Answers.COLUMN_SESSION_ID,
            QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER,
            QuizHistoryContract.Answers.COLUMN_QUESTION_ID,
            QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER,
            QuizHistoryContract.Answers.COLUMN_POINT_GUESS,
            QuizHistoryContract.Answers.COLUMN_LOWER_BOUND,
            QuizHistoryContract.Answers.COLUMN_UPPER_BOUND,
            QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS
    };

    private final QuizDatabaseHelper databaseHelper;

    /**
     * Creates a history DAO backed by the application-private database.
     *
     * @param context context used to locate the database
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public QuizHistoryDao(Context context) {
        this(new QuizDatabaseHelper(context));
    }

    QuizHistoryDao(QuizDatabaseHelper databaseHelper) {
        if (databaseHelper == null) {
            throw new IllegalArgumentException("databaseHelper must not be null");
        }
        this.databaseHelper = databaseHelper;
    }

    /**
     * Starts a new persisted session.
     *
     * @param level curriculum level and answer mode
     * @param initialQuestionCount planned distinct-question count before repeats
     * @param startedAtEpochMillis non-negative UTC Unix epoch millisecond
     * @return positive identifier of the inserted session
     * @throws IllegalArgumentException if an argument is invalid
     * @throws IllegalStateException if SQLite does not insert the row
     */
    public long startSession(
            Level level,
            int initialQuestionCount,
            long startedAtEpochMillis
    ) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (initialQuestionCount <= 0) {
            throw new IllegalArgumentException("initialQuestionCount must be greater than zero");
        }
        requireNonNegativeTimestamp(startedAtEpochMillis, "startedAtEpochMillis");

        ContentValues values = new ContentValues();
        values.put(QuizHistoryContract.Sessions.COLUMN_LEVEL, levelValue(level));
        values.put(
                QuizHistoryContract.Sessions.COLUMN_STATE,
                QuizHistoryContract.Sessions.STATE_IN_PROGRESS
        );
        values.put(
                QuizHistoryContract.Sessions.COLUMN_INITIAL_QUESTION_COUNT,
                initialQuestionCount
        );
        values.put(
                QuizHistoryContract.Sessions.COLUMN_STARTED_AT_EPOCH_MS,
                startedAtEpochMillis
        );
        long id = databaseHelper.getWritableDatabase().insertOrThrow(
                QuizHistoryContract.Sessions.TABLE_NAME,
                null,
                values
        );
        if (id <= 0L) {
            throw new IllegalStateException("SQLite returned an invalid session identifier");
        }
        return id;
    }

    /**
     * Records one accepted answer in an active session.
     *
     * @param sessionId positive parent-session identifier
     * @param answer validated raw answer
     * @throws IllegalArgumentException if an argument is invalid
     */
    public void recordAnswer(long sessionId, AnswerDraft answer) {
        requireSessionId(sessionId);
        if (answer == null) {
            throw new IllegalArgumentException("answer must not be null");
        }
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            Level level = requireInProgressSession(database, sessionId);
            requireGuessMatchesLevel(level, answer.getGuess());
            insertAnswer(database, sessionId, answer);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Records several answers atomically.
     *
     * <p>A failed item rolls back every earlier item in the batch, preventing a partially imported
     * session.</p>
     *
     * @param sessionId positive parent-session identifier
     * @param answers non-null answers to insert in their supplied order
     * @throws IllegalArgumentException if an argument or list item is invalid
     */
    public void recordAnswers(long sessionId, List<AnswerDraft> answers) {
        requireSessionId(sessionId);
        if (answers == null) {
            throw new IllegalArgumentException("answers must not be null");
        }

        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            Level level = requireInProgressSession(database, sessionId);
            for (AnswerDraft answer : answers) {
                if (answer == null) {
                    throw new IllegalArgumentException("answers must not contain null");
                }
                requireGuessMatchesLevel(level, answer.getGuess());
                insertAnswer(database, sessionId, answer);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Records the final answer and completes its session in one transaction.
     *
     * @param sessionId positive parent-session identifier
     * @param answer final validated raw answer
     * @param endedAtEpochMillis non-negative UTC Unix epoch millisecond
     * @throws IllegalArgumentException if an argument is invalid
     * @throws IllegalStateException if the session is absent or no longer in progress
     */
    public void recordFinalAnswerAndCompleteSession(
            long sessionId,
            AnswerDraft answer,
            long endedAtEpochMillis
    ) {
        requireSessionId(sessionId);
        if (answer == null) {
            throw new IllegalArgumentException("answer must not be null");
        }
        requireNonNegativeTimestamp(endedAtEpochMillis, "endedAtEpochMillis");

        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            Level level = requireInProgressSession(database, sessionId);
            requireGuessMatchesLevel(level, answer.getGuess());
            insertAnswer(database, sessionId, answer);
            endSession(
                    database,
                    sessionId,
                    QuizHistoryContract.Sessions.STATE_COMPLETED,
                    endedAtEpochMillis
            );
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Marks an unfinished session as explicitly abandoned.
     *
     * @param sessionId positive session identifier
     * @param endedAtEpochMillis non-negative UTC Unix epoch millisecond
     * @throws IllegalArgumentException if an argument is invalid
     * @throws IllegalStateException if the session is absent or no longer in progress
     */
    public void abandonSession(long sessionId, long endedAtEpochMillis) {
        requireSessionId(sessionId);
        requireNonNegativeTimestamp(endedAtEpochMillis, "endedAtEpochMillis");
        endSession(
                databaseHelper.getWritableDatabase(),
                sessionId,
                QuizHistoryContract.Sessions.STATE_ABANDONED,
                endedAtEpochMillis
        );
    }

    /**
     * Finds one session by its identifier.
     *
     * @param sessionId positive session identifier
     * @return stored session, or an empty value when no row matches
     * @throws IllegalArgumentException if {@code sessionId} is not positive
     */
    public Optional<StoredSession> findSession(long sessionId) {
        requireSessionId(sessionId);
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        try (Cursor cursor = database.query(
                QuizHistoryContract.Sessions.TABLE_NAME,
                SESSION_PROJECTION,
                QuizHistoryContract.Sessions._ID + " = ?",
                new String[]{Long.toString(sessionId)},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return Optional.empty();
            }
            return Optional.of(readSession(cursor));
        }
    }

    /**
     * Loads completed sessions for one level, newest first.
     *
     * @param level curriculum level to select
     * @return immutable snapshot of matching sessions
     * @throws IllegalArgumentException if {@code level} is {@code null}
     */
    public List<StoredSession> getCompletedSessions(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        List<StoredSession> sessions = new ArrayList<>();
        try (Cursor cursor = database.query(
                QuizHistoryContract.Sessions.TABLE_NAME,
                SESSION_PROJECTION,
                QuizHistoryContract.Sessions.COLUMN_LEVEL + " = ? AND "
                        + QuizHistoryContract.Sessions.COLUMN_STATE + " = ?",
                new String[]{
                        levelValue(level),
                        QuizHistoryContract.Sessions.STATE_COMPLETED
                },
                null,
                null,
                QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " DESC"
        )) {
            while (cursor.moveToNext()) {
                sessions.add(readSession(cursor));
            }
        }
        return Collections.unmodifiableList(sessions);
    }

    /**
     * Loads every answer from one session in submission order.
     *
     * @param sessionId positive parent-session identifier
     * @return immutable snapshot of matching answers
     * @throws IllegalArgumentException if {@code sessionId} is not positive
     */
    public List<StoredAnswer> getAnswersForSession(long sessionId) {
        requireSessionId(sessionId);
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        List<StoredAnswer> answers = new ArrayList<>();
        try (Cursor cursor = database.query(
                QuizHistoryContract.Answers.TABLE_NAME,
                ANSWER_PROJECTION,
                QuizHistoryContract.Answers.COLUMN_SESSION_ID + " = ?",
                new String[]{Long.toString(sessionId)},
                null,
                null,
                QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER + " ASC"
        )) {
            while (cursor.moveToNext()) {
                answers.add(readAnswer(cursor));
            }
        }
        return Collections.unmodifiableList(answers);
    }

    /** Closes the cached database connection held by the helper. */
    @Override
    public void close() {
        databaseHelper.close();
    }

    private static void insertAnswer(
            SQLiteDatabase database,
            long sessionId,
            AnswerDraft answer
    ) {
        ContentValues values = new ContentValues();
        values.put(QuizHistoryContract.Answers.COLUMN_SESSION_ID, sessionId);
        values.put(
                QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER,
                answer.getSequenceNumber()
        );
        values.put(
                QuizHistoryContract.Answers.COLUMN_QUESTION_ID,
                answer.getQuestion().getId()
        );
        values.put(
                QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER,
                answer.getQuestion().getTrueValue()
        );
        putGuess(values, answer.getGuess());
        values.put(
                QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS,
                answer.getAnsweredAtEpochMillis()
        );
        database.insertOrThrow(QuizHistoryContract.Answers.TABLE_NAME, null, values);
    }

    private static void putGuess(ContentValues values, Guess guess) {
        if (guess instanceof PointGuess) {
            values.put(
                    QuizHistoryContract.Answers.COLUMN_POINT_GUESS,
                    ((PointGuess) guess).getValue()
            );
            return;
        }
        IntervalGuess intervalGuess = (IntervalGuess) guess;
        values.put(
                QuizHistoryContract.Answers.COLUMN_LOWER_BOUND,
                intervalGuess.getLowerBound()
        );
        values.put(
                QuizHistoryContract.Answers.COLUMN_UPPER_BOUND,
                intervalGuess.getUpperBound()
        );
    }

    private static void endSession(
            SQLiteDatabase database,
            long sessionId,
            String state,
            long endedAtEpochMillis
    ) {
        ContentValues values = new ContentValues();
        values.put(QuizHistoryContract.Sessions.COLUMN_STATE, state);
        values.put(
                QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS,
                endedAtEpochMillis
        );
        int updatedRows = database.update(
                QuizHistoryContract.Sessions.TABLE_NAME,
                values,
                QuizHistoryContract.Sessions._ID + " = ? AND "
                        + QuizHistoryContract.Sessions.COLUMN_STATE + " = ?",
                new String[]{
                        Long.toString(sessionId),
                        QuizHistoryContract.Sessions.STATE_IN_PROGRESS
                }
        );
        if (updatedRows != 1) {
            throw new IllegalStateException("Session is absent or is no longer in progress");
        }
    }

    private static Level requireInProgressSession(SQLiteDatabase database, long sessionId) {
        try (Cursor cursor = database.query(
                QuizHistoryContract.Sessions.TABLE_NAME,
                new String[]{QuizHistoryContract.Sessions.COLUMN_LEVEL},
                QuizHistoryContract.Sessions._ID + " = ? AND "
                        + QuizHistoryContract.Sessions.COLUMN_STATE + " = ?",
                new String[]{
                        Long.toString(sessionId),
                        QuizHistoryContract.Sessions.STATE_IN_PROGRESS
                },
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("Session is absent or is no longer in progress");
            }
            return readLevel(cursor.getString(cursor.getColumnIndexOrThrow(
                    QuizHistoryContract.Sessions.COLUMN_LEVEL
            )));
        }
    }

    private static void requireGuessMatchesLevel(Level level, Guess guess) {
        boolean matches = (level == Level.POINT_ESTIMATES && guess instanceof PointGuess)
                || (level == Level.CONFIDENCE_INTERVALS && guess instanceof IntervalGuess);
        if (!matches) {
            throw new IllegalArgumentException("guess type does not match the session level");
        }
    }

    private static StoredSession readSession(Cursor cursor) {
        int endedAtIndex = cursor.getColumnIndexOrThrow(
                QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS
        );
        Long endedAt = cursor.isNull(endedAtIndex) ? null : cursor.getLong(endedAtIndex);
        return new StoredSession(
                cursor.getLong(cursor.getColumnIndexOrThrow(QuizHistoryContract.Sessions._ID)),
                readLevel(cursor.getString(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Sessions.COLUMN_LEVEL
                ))),
                readState(cursor.getString(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Sessions.COLUMN_STATE
                ))),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Sessions.COLUMN_INITIAL_QUESTION_COUNT
                )),
                cursor.getLong(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Sessions.COLUMN_STARTED_AT_EPOCH_MS
                )),
                endedAt
        );
    }

    private static StoredAnswer readAnswer(Cursor cursor) {
        int pointGuessIndex = cursor.getColumnIndexOrThrow(
                QuizHistoryContract.Answers.COLUMN_POINT_GUESS
        );
        Guess guess;
        if (!cursor.isNull(pointGuessIndex)) {
            guess = new PointGuess(cursor.getDouble(pointGuessIndex));
        } else {
            guess = new IntervalGuess(
                    cursor.getDouble(cursor.getColumnIndexOrThrow(
                            QuizHistoryContract.Answers.COLUMN_LOWER_BOUND
                    )),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(
                            QuizHistoryContract.Answers.COLUMN_UPPER_BOUND
                    ))
            );
        }
        return new StoredAnswer(
                cursor.getLong(cursor.getColumnIndexOrThrow(QuizHistoryContract.Answers._ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Answers.COLUMN_SESSION_ID
                )),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER
                )),
                cursor.getString(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Answers.COLUMN_QUESTION_ID
                )),
                cursor.getDouble(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER
                )),
                guess,
                cursor.getLong(cursor.getColumnIndexOrThrow(
                        QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS
                ))
        );
    }

    private static String levelValue(Level level) {
        switch (level) {
            case POINT_ESTIMATES:
                return QuizHistoryContract.Sessions.LEVEL_POINT_ESTIMATES;
            case CONFIDENCE_INTERVALS:
                return QuizHistoryContract.Sessions.LEVEL_CONFIDENCE_INTERVALS;
            default:
                throw new IllegalArgumentException("Unsupported level: " + level);
        }
    }

    private static Level readLevel(String value) {
        if (QuizHistoryContract.Sessions.LEVEL_POINT_ESTIMATES.equals(value)) {
            return Level.POINT_ESTIMATES;
        }
        if (QuizHistoryContract.Sessions.LEVEL_CONFIDENCE_INTERVALS.equals(value)) {
            return Level.CONFIDENCE_INTERVALS;
        }
        throw new IllegalStateException("Unsupported stored level: " + value);
    }

    private static StoredSession.State readState(String value) {
        if (QuizHistoryContract.Sessions.STATE_IN_PROGRESS.equals(value)) {
            return StoredSession.State.IN_PROGRESS;
        }
        if (QuizHistoryContract.Sessions.STATE_COMPLETED.equals(value)) {
            return StoredSession.State.COMPLETED;
        }
        if (QuizHistoryContract.Sessions.STATE_ABANDONED.equals(value)) {
            return StoredSession.State.ABANDONED;
        }
        throw new IllegalStateException("Unsupported stored session state: " + value);
    }

    private static void requireSessionId(long sessionId) {
        if (sessionId <= 0L) {
            throw new IllegalArgumentException("sessionId must be greater than zero");
        }
    }

    private static void requireNonNegativeTimestamp(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
