package de.christiankorn.giveortake.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes the minimal framework-independent state needed to restore a quiz session.
 *
 * <p>The snapshot stores stable question identifiers instead of serialising question content or
 * the mutable strategy implementation. The Android layer can therefore save its primitive values
 * in an instance-state {@code Bundle} without introducing Android types into the core package.</p>
 */
public final class QuizSessionSnapshot {
    private final Level level;
    private final int initialQuestionCount;
    private final List<String> pendingQuestionIds;
    private final String currentQuestionId;
    private final List<Score> scores;
    private final long calibrationHitCount;
    private final long calibrationSampleSize;
    private final double meanLogScaleWidth;

    /**
     * Creates an immutable session snapshot.
     *
     * @param initialQuestionCount number of distinct questions selected when the session began
     * @param pendingQuestionIds identifiers in their exact future presentation order
     * @param currentQuestionId identifier awaiting an answer, or {@code null} after completion
     * @param scores scores already recorded in answer order
     * @throws IllegalArgumentException if a value violates the snapshot invariants
     */
    public QuizSessionSnapshot(
            int initialQuestionCount,
            List<String> pendingQuestionIds,
            String currentQuestionId,
            List<Score> scores
    ) {
        this(
                Level.POINT_ESTIMATES,
                initialQuestionCount,
                pendingQuestionIds,
                currentQuestionId,
                scores,
                0L,
                0L,
                0.0
        );
    }

    /**
     * Creates an immutable point or confidence-interval session snapshot.
     *
     * @param level curriculum level whose mode produced the scores
     * @param initialQuestionCount number of distinct questions selected when the session began
     * @param pendingQuestionIds identifiers in their exact future presentation order
     * @param currentQuestionId identifier awaiting an answer, or {@code null} after completion
     * @param scores scores already recorded in answer order
     * @param calibrationHitCount interval answers that contained the truth
     * @param calibrationSampleSize interval answers recorded by the calibration tracker
     * @param meanLogScaleWidth mean logarithmic interval width, or zero for point mode
     * @throws IllegalArgumentException if a value violates the snapshot invariants
     */
    public QuizSessionSnapshot(
            Level level,
            int initialQuestionCount,
            List<String> pendingQuestionIds,
            String currentQuestionId,
            List<Score> scores,
            long calibrationHitCount,
            long calibrationSampleSize,
            double meanLogScaleWidth
    ) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (initialQuestionCount <= 0) {
            throw new IllegalArgumentException("initialQuestionCount must be greater than zero");
        }
        if (pendingQuestionIds == null) {
            throw new IllegalArgumentException("pendingQuestionIds must not be null");
        }
        if (scores == null) {
            throw new IllegalArgumentException("scores must not be null");
        }
        if (currentQuestionId == null && !pendingQuestionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "an incomplete snapshot must have a current question"
            );
        }

        List<String> copiedQuestionIds = new ArrayList<>(pendingQuestionIds.size());
        for (String questionId : pendingQuestionIds) {
            copiedQuestionIds.add(requireQuestionId(questionId, "pendingQuestionIds"));
        }
        this.pendingQuestionIds = Collections.unmodifiableList(copiedQuestionIds);
        this.currentQuestionId = currentQuestionId == null
                ? null
                : requireQuestionId(currentQuestionId, "currentQuestionId");

        List<Score> copiedScores = new ArrayList<>(scores.size());
        for (Score score : scores) {
            if (score == null) {
                throw new IllegalArgumentException("scores must not contain null");
            }
            validateScore(level, score);
            copiedScores.add(score);
        }
        if (level == Level.POINT_ESTIMATES) {
            if (calibrationHitCount != 0L || calibrationSampleSize != 0L
                    || meanLogScaleWidth != 0.0) {
                throw new IllegalArgumentException(
                        "point-estimate snapshots must not contain calibration state"
                );
            }
        } else {
            if (calibrationSampleSize != scores.size()) {
                throw new IllegalArgumentException(
                        "interval calibration sample size must match the score count"
                );
            }
            CalibrationTracker.restore(
                    calibrationSampleSize,
                    calibrationHitCount,
                    meanLogScaleWidth
            );
        }
        this.level = level;
        this.scores = Collections.unmodifiableList(copiedScores);
        this.initialQuestionCount = initialQuestionCount;
        this.calibrationHitCount = calibrationHitCount;
        this.calibrationSampleSize = calibrationSampleSize;
        this.meanLogScaleWidth = meanLogScaleWidth;
    }

    /**
     * Returns the curriculum level and therefore answer mode of the captured session.
     *
     * @return the non-null level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Returns the number of distinct questions selected before remedial repeats were added.
     *
     * @return the positive initial question count
     */
    public int getInitialQuestionCount() {
        return initialQuestionCount;
    }

    /**
     * Returns future question identifiers in presentation order.
     *
     * @return an unmodifiable list of stable identifiers
     */
    public List<String> getPendingQuestionIds() {
        return pendingQuestionIds;
    }

    /**
     * Returns the identifier of the question currently awaiting an answer.
     *
     * @return the current identifier, or {@code null} after session completion
     */
    public String getCurrentQuestionId() {
        return currentQuestionId;
    }

    /**
     * Returns scores already recorded in answer order.
     *
     * @return an unmodifiable list of scores produced by the captured mode
     */
    public List<Score> getScores() {
        return scores;
    }

    /**
     * Returns how many captured confidence intervals contained the truth.
     *
     * @return the non-negative hit count, or zero for point mode
     */
    public long getCalibrationHitCount() {
        return calibrationHitCount;
    }

    /**
     * Returns how many captured confidence intervals were recorded.
     *
     * @return the non-negative sample size, or zero for point mode
     */
    public long getCalibrationSampleSize() {
        return calibrationSampleSize;
    }

    /**
     * Returns the captured mean logarithmic interval width.
     *
     * @return the finite non-negative mean, or zero for point mode
     */
    public double getMeanLogScaleWidth() {
        return meanLogScaleWidth;
    }

    private static void validateScore(Level level, Score score) {
        if (level == Level.POINT_ESTIMATES && !score.hasPoints()) {
            throw new IllegalArgumentException(
                    "point-estimate snapshot scores must include points"
            );
        }
        if (level == Level.CONFIDENCE_INTERVALS && score.hasPoints()) {
            throw new IllegalArgumentException(
                    "confidence-interval snapshot scores must not include points"
            );
        }
    }

    private static String requireQuestionId(String questionId, String fieldName) {
        if (questionId == null || questionId.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must contain non-blank identifiers");
        }
        return questionId;
    }
}
