package de.christiankorn.giveortake.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

import de.christiankorn.giveortake.core.CalibrationTracker;
import de.christiankorn.giveortake.core.CorrectnessClassifier;
import de.christiankorn.giveortake.core.Guess;
import de.christiankorn.giveortake.core.HighScore;
import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.IntervalScore;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.LogRelativeScore;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Score;
import de.christiankorn.giveortake.core.SessionResult;

/**
 * Reads quiz history and derives typed statistics without exposing cursors to callers.
 *
 * <p>SQLite owns selection, ordering, and paging. Scoring, interval containment, logarithmic
 * width, calibration, and personal-best comparisons remain in the framework-independent core
 * package. This prevents a second SQL implementation of the app's numerical policies.</p>
 */
public final class QuizStatisticsDao implements AutoCloseable {
    /** Number of consecutive interval answers represented by each coverage trend point. */
    public static final int COVERAGE_WINDOW_SIZE = 10;

    /** Label used when schema-version-1 history has no snapshotted category. */
    public static final String LEGACY_CATEGORY = "Uncategorised";

    private static final String SESSION_ID = "statistics_session_id";
    private static final String SESSION_LEVEL = "statistics_session_level";
    private static final String SESSION_STATE = "statistics_session_state";
    private static final String SESSION_INITIAL_COUNT = "statistics_session_initial_count";
    private static final String SESSION_STARTED = "statistics_session_started";
    private static final String SESSION_ENDED = "statistics_session_ended";
    private static final String ANSWER_ID = "statistics_answer_id";
    private static final String ANSWER_SEQUENCE = "statistics_answer_sequence";
    private static final String ANSWER_QUESTION_ID = "statistics_answer_question_id";
    private static final String ANSWER_CATEGORY = "statistics_answer_category";
    private static final String ANSWER_TRUTH = "statistics_answer_truth";
    private static final String ANSWER_POINT = "statistics_answer_point";
    private static final String ANSWER_LOWER = "statistics_answer_lower";
    private static final String ANSWER_UPPER = "statistics_answer_upper";
    private static final String ANSWER_TIME = "statistics_answer_time";

    private static final String SELECT_JOINED_COLUMNS =
            "s." + QuizHistoryContract.Sessions._ID + " AS " + SESSION_ID + ", "
                    + "s." + QuizHistoryContract.Sessions.COLUMN_LEVEL + " AS "
                    + SESSION_LEVEL + ", "
                    + "s." + QuizHistoryContract.Sessions.COLUMN_STATE + " AS "
                    + SESSION_STATE + ", "
                    + "s." + QuizHistoryContract.Sessions.COLUMN_INITIAL_QUESTION_COUNT + " AS "
                    + SESSION_INITIAL_COUNT + ", "
                    + "s." + QuizHistoryContract.Sessions.COLUMN_STARTED_AT_EPOCH_MS + " AS "
                    + SESSION_STARTED + ", "
                    + "s." + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " AS "
                    + SESSION_ENDED + ", "
                    + "a." + QuizHistoryContract.Answers._ID + " AS " + ANSWER_ID + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER + " AS "
                    + ANSWER_SEQUENCE + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_QUESTION_ID + " AS "
                    + ANSWER_QUESTION_ID + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_CATEGORY_AT_ANSWER + " AS "
                    + ANSWER_CATEGORY + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_TRUE_VALUE_AT_ANSWER + " AS "
                    + ANSWER_TRUTH + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_POINT_GUESS + " AS "
                    + ANSWER_POINT + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_LOWER_BOUND + " AS "
                    + ANSWER_LOWER + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_UPPER_BOUND + " AS "
                    + ANSWER_UPPER + ", "
                    + "a." + QuizHistoryContract.Answers.COLUMN_ANSWERED_AT_EPOCH_MS + " AS "
                    + ANSWER_TIME;

    private static final String ALL_ENDED_HISTORY_QUERY =
            "SELECT " + SELECT_JOINED_COLUMNS + " FROM "
                    + QuizHistoryContract.Sessions.TABLE_NAME + " s LEFT JOIN "
                    + QuizHistoryContract.Answers.TABLE_NAME + " a ON a."
                    + QuizHistoryContract.Answers.COLUMN_SESSION_ID + " = s."
                    + QuizHistoryContract.Sessions._ID + " WHERE s."
                    + QuizHistoryContract.Sessions.COLUMN_STATE + " <> ? AND s."
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS
                    + " IS NOT NULL ORDER BY s."
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " ASC, s."
                    + QuizHistoryContract.Sessions._ID + " ASC, a."
                    + QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER + " ASC";

    private static final String RECENT_HISTORY_QUERY =
            "WITH recent_sessions AS (SELECT * FROM "
                    + QuizHistoryContract.Sessions.TABLE_NAME + " WHERE "
                    + QuizHistoryContract.Sessions.COLUMN_STATE + " <> ? AND "
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS
                    + " IS NOT NULL ORDER BY "
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " DESC, "
                    + QuizHistoryContract.Sessions._ID + " DESC LIMIT ? OFFSET ?) SELECT "
                    + SELECT_JOINED_COLUMNS + " FROM recent_sessions s LEFT JOIN "
                    + QuizHistoryContract.Answers.TABLE_NAME + " a ON a."
                    + QuizHistoryContract.Answers.COLUMN_SESSION_ID + " = s."
                    + QuizHistoryContract.Sessions._ID + " ORDER BY s."
                    + QuizHistoryContract.Sessions.COLUMN_ENDED_AT_EPOCH_MS + " DESC, s."
                    + QuizHistoryContract.Sessions._ID + " DESC, a."
                    + QuizHistoryContract.Answers.COLUMN_SEQUENCE_NUMBER + " ASC";

    private final QuizDatabaseHelper databaseHelper;
    private final LogRelativeScore pointPolicy = new LogRelativeScore();
    private final IntervalScore intervalPolicy = new IntervalScore();
    private final CorrectnessClassifier correctnessClassifier = new CorrectnessClassifier();

    /**
     * Creates a statistics reader for the application-private history database.
     *
     * @param context context used to locate the database
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public QuizStatisticsDao(Context context) {
        this(new QuizDatabaseHelper(context));
    }

    QuizStatisticsDao(QuizDatabaseHelper databaseHelper) {
        if (databaseHelper == null) {
            throw new IllegalArgumentException("databaseHelper must not be null");
        }
        this.databaseHelper = databaseHelper;
    }

    /**
     * Loads one newest-first page of completed or abandoned sessions.
     *
     * <p>The limit and offset are bound parameters. Empty sessions remain present and expose
     * absent means rather than fabricated zero scores.</p>
     *
     * @param limit positive maximum number of sessions to return
     * @param offset non-negative number of newer sessions to skip
     * @return immutable page of session statistics
     * @throws IllegalArgumentException if pagination arguments are invalid
     */
    public List<RecentSessionStatistics> getRecentSessions(int limit, int offset) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }

        List<SessionHistory> sessions = querySessions(
                RECENT_HISTORY_QUERY,
                new String[]{
                        QuizHistoryContract.Sessions.STATE_IN_PROGRESS,
                        Integer.toString(limit),
                        Integer.toString(offset)
                }
        );
        List<RecentSessionStatistics> results = new ArrayList<>(sessions.size());
        for (SessionHistory session : sessions) {
            results.add(new RecentSessionStatistics(session.session, scoreSession(session)));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Calculates all non-paged statistics-screen aggregates from one database snapshot.
     *
     * <p>In-progress sessions are excluded because their asynchronous history may still change.
     * Accepted answers from explicitly abandoned sessions remain part of learning and calibration;
     * only completed sessions are eligible to establish personal bests.</p>
     *
     * @return immutable overview with explicit empty values when history is insufficient
     */
    public StatisticsOverview getOverview() {
        List<SessionHistory> sessions = querySessions(
                ALL_ENDED_HISTORY_QUERY,
                new String[]{QuizHistoryContract.Sessions.STATE_IN_PROGRESS}
        );
        List<HistoricalAnswer> answers = answersInChronologicalOrder(sessions);

        CalibrationTracker overallCalibration = new CalibrationTracker();
        for (HistoricalAnswer answer : answers) {
            if (answer.guess instanceof IntervalGuess) {
                recordInterval(overallCalibration, answer);
            }
        }

        return new StatisticsOverview(
                sessions.size(),
                answers.size(),
                new CalibrationStatistics(overallCalibration),
                immutable(coverageTrend(answers)),
                immutable(meanErrorTrend(sessions)),
                immutable(categoryPerformance(answers)),
                immutable(personalBests(sessions))
        );
    }

    /** Closes the cached database connection held by the helper. */
    @Override
    public void close() {
        databaseHelper.close();
    }

    private List<SessionHistory> querySessions(String sql, String[] arguments) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Map<Long, SessionHistory> sessions = new LinkedHashMap<>();
        try (Cursor cursor = database.rawQuery(sql, arguments)) {
            while (cursor.moveToNext()) {
                long sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(SESSION_ID));
                SessionHistory history = sessions.get(sessionId);
                if (history == null) {
                    history = new SessionHistory(readSession(cursor));
                    sessions.put(sessionId, history);
                }
                int answerIdIndex = cursor.getColumnIndexOrThrow(ANSWER_ID);
                if (!cursor.isNull(answerIdIndex)) {
                    history.answers.add(readAnswer(cursor));
                }
            }
        }
        return new ArrayList<>(sessions.values());
    }

    private SessionResult scoreSession(SessionHistory history) {
        List<Score> scores = new ArrayList<>(history.answers.size());
        if (history.session.getLevel() == Level.POINT_ESTIMATES) {
            for (HistoricalAnswer answer : history.answers) {
                scores.add(pointPolicy.score(
                        answer.trueValue,
                        requirePointGuess(answer.guess)
                ));
            }
            return SessionResult.forPointEstimates(scores, correctnessClassifier);
        }

        CalibrationTracker tracker = new CalibrationTracker();
        for (HistoricalAnswer answer : history.answers) {
            IntervalGuess guess = requireIntervalGuess(answer.guess);
            scores.add(intervalPolicy.score(answer.trueValue, guess));
            tracker.recordOutcome(guess.containsTruth(answer.trueValue), guess.width());
        }
        return SessionResult.forConfidenceIntervals(scores, tracker);
    }

    private List<CoverageTrendPoint> coverageTrend(List<HistoricalAnswer> answers) {
        ArrayDeque<HistoricalAnswer> window = new ArrayDeque<>(COVERAGE_WINDOW_SIZE);
        List<CoverageTrendPoint> trend = new ArrayList<>();
        for (HistoricalAnswer answer : answers) {
            if (!(answer.guess instanceof IntervalGuess)) {
                continue;
            }
            window.addLast(answer);
            if (window.size() > COVERAGE_WINDOW_SIZE) {
                window.removeFirst();
            }
            if (window.size() == COVERAGE_WINDOW_SIZE) {
                CalibrationTracker tracker = new CalibrationTracker();
                for (HistoricalAnswer member : window) {
                    recordInterval(tracker, member);
                }
                trend.add(new CoverageTrendPoint(
                        answer.answeredAtEpochMillis,
                        answer.sessionId,
                        COVERAGE_WINDOW_SIZE,
                        (int) tracker.getHitCount()
                ));
            }
        }
        return trend;
    }

    private List<MeanErrorTrendPoint> meanErrorTrend(List<SessionHistory> sessions) {
        List<MeanErrorTrendPoint> trend = new ArrayList<>();
        for (SessionHistory session : sessions) {
            if (session.session.getLevel() != Level.POINT_ESTIMATES) {
                continue;
            }
            SessionResult result = scoreSession(session);
            OptionalDouble mean = result.getMeanRawError();
            if (mean.isPresent()) {
                trend.add(new MeanErrorTrendPoint(
                        session.session.getId(),
                        session.session.getEndedAtEpochMillis(),
                        result.getAnsweredQuestionCount(),
                        mean.getAsDouble()
                ));
            }
        }
        return trend;
    }

    private List<CategoryPerformance> categoryPerformance(List<HistoricalAnswer> answers) {
        Map<String, CategoryAccumulator> categories = new TreeMap<>();
        for (HistoricalAnswer answer : answers) {
            String category = answer.category == null ? LEGACY_CATEGORY : answer.category;
            CategoryAccumulator accumulator = categories.get(category);
            if (accumulator == null) {
                accumulator = new CategoryAccumulator(category);
                categories.put(category, accumulator);
            }
            if (answer.guess instanceof PointGuess) {
                accumulator.recordPoint(pointPolicy.score(
                        answer.trueValue,
                        requirePointGuess(answer.guess)
                ).getRawError());
            } else {
                IntervalGuess guess = requireIntervalGuess(answer.guess);
                accumulator.recordInterval(
                        guess.containsTruth(answer.trueValue),
                        guess.width(),
                        intervalPolicy.score(answer.trueValue, guess).getRawError()
                );
            }
        }

        List<CategoryPerformance> results = new ArrayList<>(categories.size());
        for (CategoryAccumulator accumulator : categories.values()) {
            results.add(accumulator.toResult());
        }
        return results;
    }

    private List<PersonalBestStatistics> personalBests(List<SessionHistory> sessions) {
        Map<Level, HighScore> records = new EnumMap<>(Level.class);
        records.put(Level.POINT_ESTIMATES, HighScore.empty(Level.POINT_ESTIMATES));
        records.put(
                Level.CONFIDENCE_INTERVALS,
                HighScore.empty(Level.CONFIDENCE_INTERVALS)
        );
        Map<Level, PersonalBestStatistics> results = new EnumMap<>(Level.class);

        for (SessionHistory session : sessions) {
            if (session.session.getState() != StoredSession.State.COMPLETED) {
                continue;
            }
            Level level = session.session.getLevel();
            HighScore previous = records.get(level);
            SessionResult result = scoreSession(session);
            HighScore updated = previous.afterSession(result);
            if (!updated.equals(previous)) {
                double value = updated.getBestValue().orElseThrow(IllegalStateException::new);
                results.put(level, new PersonalBestStatistics(
                        level,
                        session.session.getId(),
                        session.session.getEndedAtEpochMillis(),
                        value,
                        result.getAnsweredQuestionCount()
                ));
                records.put(level, updated);
            }
        }

        List<PersonalBestStatistics> ordered = new ArrayList<>(2);
        if (results.containsKey(Level.POINT_ESTIMATES)) {
            ordered.add(results.get(Level.POINT_ESTIMATES));
        }
        if (results.containsKey(Level.CONFIDENCE_INTERVALS)) {
            ordered.add(results.get(Level.CONFIDENCE_INTERVALS));
        }
        return ordered;
    }

    private static List<HistoricalAnswer> answersInChronologicalOrder(
            List<SessionHistory> sessions
    ) {
        List<HistoricalAnswer> answers = new ArrayList<>();
        for (SessionHistory session : sessions) {
            answers.addAll(session.answers);
        }
        answers.sort(Comparator
                .comparingLong((HistoricalAnswer answer) -> answer.answeredAtEpochMillis)
                .thenComparingLong(answer -> answer.id));
        return answers;
    }

    private static void recordInterval(
            CalibrationTracker tracker,
            HistoricalAnswer answer
    ) {
        IntervalGuess guess = requireIntervalGuess(answer.guess);
        tracker.recordOutcome(guess.containsTruth(answer.trueValue), guess.width());
    }

    private static PointGuess requirePointGuess(Guess guess) {
        if (!(guess instanceof PointGuess)) {
            throw new IllegalStateException("Stored answer mode does not match its session");
        }
        return (PointGuess) guess;
    }

    private static IntervalGuess requireIntervalGuess(Guess guess) {
        if (!(guess instanceof IntervalGuess)) {
            throw new IllegalStateException("Stored answer mode does not match its session");
        }
        return (IntervalGuess) guess;
    }

    private static StoredSession readSession(Cursor cursor) {
        return new StoredSession(
                cursor.getLong(cursor.getColumnIndexOrThrow(SESSION_ID)),
                readLevel(cursor.getString(cursor.getColumnIndexOrThrow(SESSION_LEVEL))),
                readState(cursor.getString(cursor.getColumnIndexOrThrow(SESSION_STATE))),
                cursor.getInt(cursor.getColumnIndexOrThrow(SESSION_INITIAL_COUNT)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SESSION_STARTED)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SESSION_ENDED))
        );
    }

    private static HistoricalAnswer readAnswer(Cursor cursor) {
        int pointIndex = cursor.getColumnIndexOrThrow(ANSWER_POINT);
        Guess guess;
        if (!cursor.isNull(pointIndex)) {
            guess = new PointGuess(cursor.getDouble(pointIndex));
        } else {
            guess = new IntervalGuess(
                    cursor.getDouble(cursor.getColumnIndexOrThrow(ANSWER_LOWER)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(ANSWER_UPPER))
            );
        }

        int categoryIndex = cursor.getColumnIndexOrThrow(ANSWER_CATEGORY);
        return new HistoricalAnswer(
                cursor.getLong(cursor.getColumnIndexOrThrow(ANSWER_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SESSION_ID)),
                cursor.isNull(categoryIndex) ? null : cursor.getString(categoryIndex),
                cursor.getDouble(cursor.getColumnIndexOrThrow(ANSWER_TRUTH)),
                guess,
                cursor.getLong(cursor.getColumnIndexOrThrow(ANSWER_TIME))
        );
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
        if (QuizHistoryContract.Sessions.STATE_COMPLETED.equals(value)) {
            return StoredSession.State.COMPLETED;
        }
        if (QuizHistoryContract.Sessions.STATE_ABANDONED.equals(value)) {
            return StoredSession.State.ABANDONED;
        }
        throw new IllegalStateException("Statistics query returned non-terminal state: " + value);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static final class SessionHistory {
        private final StoredSession session;
        private final List<HistoricalAnswer> answers = new ArrayList<>();

        private SessionHistory(StoredSession session) {
            this.session = session;
        }
    }

    private static final class HistoricalAnswer {
        private final long id;
        private final long sessionId;
        private final String category;
        private final double trueValue;
        private final Guess guess;
        private final long answeredAtEpochMillis;

        private HistoricalAnswer(
                long id,
                long sessionId,
                String category,
                double trueValue,
                Guess guess,
                long answeredAtEpochMillis
        ) {
            this.id = id;
            this.sessionId = sessionId;
            this.category = category;
            this.trueValue = trueValue;
            this.guess = guess;
            this.answeredAtEpochMillis = answeredAtEpochMillis;
        }
    }

    private static final class CategoryAccumulator {
        private final String category;
        private int pointCount;
        private double meanPointError;
        private final CalibrationTracker intervalCalibration = new CalibrationTracker();
        private double meanIntervalLoss;

        private CategoryAccumulator(String category) {
            this.category = category;
        }

        private void recordPoint(double error) {
            pointCount++;
            meanPointError += (error - meanPointError) / pointCount;
        }

        private void recordInterval(boolean hit, double width, double loss) {
            intervalCalibration.recordOutcome(hit, width);
            meanIntervalLoss += (loss - meanIntervalLoss)
                    / intervalCalibration.getSampleSize();
        }

        private CategoryPerformance toResult() {
            int intervalCount = (int) intervalCalibration.getSampleSize();
            return new CategoryPerformance(
                    category,
                    pointCount,
                    pointCount == 0
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(meanPointError),
                    intervalCount,
                    (int) intervalCalibration.getHitCount(),
                    intervalCalibration.getEmpiricalCoverage(),
                    intervalCount == 0
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(meanIntervalLoss)
            );
        }
    }
}
