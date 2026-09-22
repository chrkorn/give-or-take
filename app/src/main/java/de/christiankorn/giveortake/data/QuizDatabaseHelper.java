package de.christiankorn.giveortake.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Creates, configures, and incrementally upgrades the private quiz-history database. */
public final class QuizDatabaseHelper extends SQLiteOpenHelper {
    /** Current on-device schema version. */
    public static final int DATABASE_VERSION = 1;
    /** Name of the application-private database file. */
    public static final String DATABASE_NAME = "quiz_history.db";

    private static final String CREATE_SESSIONS =
            "CREATE TABLE " + QuizHistoryContract.Sessions.TABLE_NAME + " ("
                    + QuizHistoryContract.Sessions._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + QuizHistoryContract.Sessions.COLUMN_LEVEL + " TEXT NOT NULL CHECK ("
                    + QuizHistoryContract.Sessions.COLUMN_LEVEL + " IN ('"
                    + QuizHistoryContract.Sessions.LEVEL_POINT_ESTIMATES + "', '"
                    + QuizHistoryContract.Sessions.LEVEL_CONFIDENCE_INTERVALS + "')), "
                    + QuizHistoryContract.Sessions.COLUMN_STATE + " TEXT NOT NULL CHECK ("
                    + QuizHistoryContract.Sessions.COLUMN_STATE + " IN ('"
                    + QuizHistoryContract.Sessions.STATE_IN_PROGRESS + "', '"
                    + QuizHistoryContract.Sessions.STATE_COMPLETED + "', '"
                    + QuizHistoryContract.Sessions.STATE_ABANDONED + "')), "
                    + QuizHistoryContract.Sessions.COLUMN_INITIAL_QUESTION_COUNT
                    + " INTEGER NOT NULL CHECK ("
                    + QuizHistoryContract.Sessions.COLUMN_INITIAL_QUESTION_COUNT + " > 0), "
                    + QuizHistoryContract.Sessions.COLUMN_STARTED_AT_EPOCH_MS
                    + " INTEGER NOT NULL CHECK ("
                    + QuizHistoryContract.Sessions.COLUMN_STARTED_AT_EPOCH_MS + " >= 0), "
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS
                    + " INTEGER, CHECK (("
                    + QuizHistoryContract.Sessions.COLUMN_STATE + " = '"
                    + QuizHistoryContract.Sessions.STATE_IN_PROGRESS + "' AND "
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " IS NULL) OR ("
                    + QuizHistoryContract.Sessions.COLUMN_STATE + " IN ('"
                    + QuizHistoryContract.Sessions.STATE_COMPLETED + "', '"
                    + QuizHistoryContract.Sessions.STATE_ABANDONED + "') AND "
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS
                    + " IS NOT NULL)))";

    private static final String CREATE_ANSWERS =
            "CREATE TABLE " + QuizHistoryContract.Answers.TABLE_NAME + " ("
                    + QuizHistoryContract.Answers._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + QuizHistoryContract.Answers.COLUMN_SESSION_ID + " INTEGER NOT NULL, "
                    + QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER
                    + " INTEGER NOT NULL CHECK ("
                    + QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER + " > 0), "
                    + QuizHistoryContract.Answers.COLUMN_QUESTION_ID
                    + " TEXT NOT NULL CHECK (length(trim("
                    + QuizHistoryContract.Answers.COLUMN_QUESTION_ID + ")) > 0), "
                    + QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER
                    + " REAL NOT NULL CHECK ("
                    + QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER + " > 0), "
                    + QuizHistoryContract.Answers.COLUMN_POINT_GUESS + " REAL, "
                    + QuizHistoryContract.Answers.COLUMN_LOWER_BOUND + " REAL, "
                    + QuizHistoryContract.Answers.COLUMN_UPPER_BOUND + " REAL, "
                    + QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS
                    + " INTEGER NOT NULL CHECK ("
                    + QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS + " >= 0), "
                    + "FOREIGN KEY (" + QuizHistoryContract.Answers.COLUMN_SESSION_ID + ") "
                    + "REFERENCES " + QuizHistoryContract.Sessions.TABLE_NAME + "("
                    + QuizHistoryContract.Sessions._ID + ") ON DELETE CASCADE, "
                    + "UNIQUE (" + QuizHistoryContract.Answers.COLUMN_SESSION_ID + ", "
                    + QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER + "), "
                    + "CHECK ((" + QuizHistoryContract.Answers.COLUMN_POINT_GUESS
                    + " IS NOT NULL AND " + QuizHistoryContract.Answers.COLUMN_POINT_GUESS
                    + " > 0 AND " + QuizHistoryContract.Answers.COLUMN_LOWER_BOUND
                    + " IS NULL AND " + QuizHistoryContract.Answers.COLUMN_UPPER_BOUND
                    + " IS NULL) OR (" + QuizHistoryContract.Answers.COLUMN_POINT_GUESS
                    + " IS NULL AND " + QuizHistoryContract.Answers.COLUMN_LOWER_BOUND
                    + " IS NOT NULL AND " + QuizHistoryContract.Answers.COLUMN_LOWER_BOUND
                    + " > 0 AND " + QuizHistoryContract.Answers.COLUMN_UPPER_BOUND
                    + " IS NOT NULL AND " + QuizHistoryContract.Answers.COLUMN_UPPER_BOUND
                    + " >= " + QuizHistoryContract.Answers.COLUMN_LOWER_BOUND + ")))";

    private static final String CREATE_SESSIONS_LEVEL_END_INDEX =
            "CREATE INDEX sessions_level_end_idx ON "
                    + QuizHistoryContract.Sessions.TABLE_NAME + "("
                    + QuizHistoryContract.Sessions.COLUMN_LEVEL + ", "
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " DESC)";

    private static final String CREATE_ANSWERS_QUESTION_TIME_INDEX =
            "CREATE INDEX answers_question_time_idx ON "
                    + QuizHistoryContract.Answers.TABLE_NAME + "("
                    + QuizHistoryContract.Answers.COLUMN_QUESTION_ID + ", "
                    + QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS + ")";

    /**
     * Creates a helper for the application-private history database.
     *
     * @param context context used to locate the database file
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public QuizDatabaseHelper(Context context) {
        super(applicationContext(context), DATABASE_NAME, null, DATABASE_VERSION);
    }

    /** {@inheritDoc} */
    @Override
    public void onConfigure(SQLiteDatabase database) {
        super.onConfigure(database);
        database.setForeignKeyConstraintsEnabled(true);
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(CREATE_SESSIONS);
        database.execSQL(CREATE_ANSWERS);
        database.execSQL(CREATE_SESSIONS_LEVEL_END_INDEX);
        database.execSQL(CREATE_ANSWERS_QUESTION_TIME_INDEX);
    }

    /** {@inheritDoc} */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        int currentVersion = oldVersion;
        while (currentVersion < newVersion) {
            /*
             * Each future case performs exactly one immutable migration, such as ALTER TABLE or
             * create-copy-rename, and then advances currentVersion. Chaining fixed steps upgrades
             * every installed version without discarding user history. Version 1 has no earlier
             * schema to migrate, so no case exists yet; a missing future step fails visibly.
             */
            switch (currentVersion) {
                default:
                    throw new IllegalStateException(
                            "Missing database migration from version " + currentVersion
                    );
            }
        }
    }

    private static Context applicationContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return context.getApplicationContext();
    }
}
