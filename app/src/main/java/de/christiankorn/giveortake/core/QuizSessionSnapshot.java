package de.christiankorn.giveortake.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes the minimal framework-independent state needed to restore a point-estimate session.
 *
 * <p>The snapshot stores stable question identifiers instead of serialising question content or
 * the mutable strategy implementation. The Android layer can therefore save its primitive values
 * in an instance-state {@code Bundle} without introducing Android types into the core package.</p>
 */
public final class QuizSessionSnapshot {
    private final int initialQuestionCount;
    private final List<String> pendingQuestionIds;
    private final String currentQuestionId;
    private final List<Score> scores;

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
            if (!score.hasPoints()) {
                throw new IllegalArgumentException(
                        "point-estimate snapshot scores must include points"
                );
            }
            copiedScores.add(score);
        }
        this.scores = Collections.unmodifiableList(copiedScores);
        this.initialQuestionCount = initialQuestionCount;
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
     * @return an unmodifiable list of point-estimate scores
     */
    public List<Score> getScores() {
        return scores;
    }

    private static String requireQuestionId(String questionId, String fieldName) {
        if (questionId == null || questionId.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must contain non-blank identifiers");
        }
        return questionId;
    }
}
