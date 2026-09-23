package de.christiankorn.giveortake.data;

import android.provider.BaseColumns;

/**
 * Defines the stable SQLite names used to persist quiz sessions and their raw answers.
 *
 * <p>Keeping these identifiers in one contract prevents callers and migrations from silently
 * disagreeing about the on-device schema.</p>
 */
public final class QuizHistoryContract {
    private QuizHistoryContract() {
    }

    /** Defines the session table and its persisted vocabulary. */
    public static final class Sessions implements BaseColumns {
        /** Name of the session table. */
        public static final String TABLE_NAME = "sessions";
        /** Curriculum level played in the session. */
        public static final String COLUMN_LEVEL = "level";
        /** Current lifecycle state of the session. */
        public static final String COLUMN_STATE = "state";
        /** Number of distinct questions selected before remedial repeats. */
        public static final String COLUMN_INITIAL_QUESTION_COUNT = "initial_question_count";
        /** UTC Unix epoch millisecond at which the session began. */
        public static final String COLUMN_STARTED_AT_EPOCH_MS = "started_at_epoch_ms";
        /** UTC Unix epoch millisecond at which the session ended, or {@code NULL} while active. */
        public static final String COLUMN_ENDED_AT_EPOCH_MS = "ended_at_epoch_ms";

        /** Persisted value for point-estimate sessions. */
        public static final String LEVEL_POINT_ESTIMATES = "point_estimates";
        /** Persisted value for confidence-interval sessions. */
        public static final String LEVEL_CONFIDENCE_INTERVALS = "confidence_intervals";
        /** Persisted value for a session that can still receive answers. */
        public static final String STATE_IN_PROGRESS = "in_progress";
        /** Persisted value for a normally completed session. */
        public static final String STATE_COMPLETED = "completed";
        /** Persisted value for a session explicitly left before completion. */
        public static final String STATE_ABANDONED = "abandoned";

        private Sessions() {
        }
    }

    /** Defines the raw-answer table. */
    public static final class Answers implements BaseColumns {
        /** Name of the answer table. */
        public static final String TABLE_NAME = "answers";
        /** Parent session identifier. */
        public static final String COLUMN_SESSION_ID = "session_id";
        /** One-based submission order within the parent session. */
        public static final String COLUMN_SEQUENCE_NUMBER = "sequence_number";
        /** Stable identifier of the question that was answered. */
        public static final String COLUMN_QUESTION_ID = "question_id";
        /**
         * Subject grouping captured when the answer was submitted, or {@code NULL} for legacy
         * data.
         */
        public static final String COLUMN_CATEGORY_AT_ANSWER = "category_at_answer";
        /** Authoritative value used when the answer was originally scored. */
        public static final String COLUMN_TRUE_VALUE_AT_ANSWER = "true_value_at_answer";
        /** Raw point estimate, or {@code NULL} for an interval answer. */
        public static final String COLUMN_POINT_GUESS = "point_guess";
        /** Raw lower interval bound, or {@code NULL} for a point answer. */
        public static final String COLUMN_LOWER_BOUND = "lower_bound";
        /** Raw upper interval bound, or {@code NULL} for a point answer. */
        public static final String COLUMN_UPPER_BOUND = "upper_bound";
        /** UTC Unix epoch millisecond at which the answer was submitted. */
        public static final String COLUMN_ANSWERED_AT_EPOCH_MS = "answered_at_epoch_ms";

        private Answers() {
        }
    }
}
