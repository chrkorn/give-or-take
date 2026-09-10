package de.christiankorn.giveortake.core;

import java.util.OptionalDouble;

/**
 * Represents the highest curriculum level a player has permanently unlocked.
 *
 * <p>A point-estimate session unlocks confidence intervals when it contains at least ten answers
 * and awards at least 70 mean points. Both boundaries are inclusive. Unlocking is permanent:
 * evaluating later weak or empty sessions can never reduce the highest unlocked level.</p>
 */
public final class LevelState {
    /** Minimum number of answers needed for an advancement decision. */
    public static final int MINIMUM_ANSWERS_FOR_ADVANCEMENT = 10;

    /** Inclusive minimum mean point score needed to unlock confidence intervals. */
    public static final double MINIMUM_MEAN_POINTS_FOR_ADVANCEMENT = 70.0;

    private final Level highestUnlockedLevel;

    /**
     * Creates level state suitable for either a new player or a restored player.
     *
     * @param highestUnlockedLevel the highest curriculum stage already unlocked
     * @throws IllegalArgumentException if {@code highestUnlockedLevel} is {@code null}
     */
    public LevelState(Level highestUnlockedLevel) {
        if (highestUnlockedLevel == null) {
            throw new IllegalArgumentException("highestUnlockedLevel must not be null");
        }
        this.highestUnlockedLevel = highestUnlockedLevel;
    }

    /**
     * Creates the initial state in which only point estimates are unlocked.
     *
     * @return a new initial level state
     */
    public static LevelState initial() {
        return new LevelState(Level.POINT_ESTIMATES);
    }

    /**
     * Returns the most advanced curriculum stage available to the player.
     *
     * @return the highest permanently unlocked level
     */
    public Level getHighestUnlockedLevel() {
        return highestUnlockedLevel;
    }

    /**
     * Reports whether a curriculum stage is available to the player.
     *
     * @param level the stage whose availability should be checked
     * @return {@code true} for point estimates and for confidence intervals once unlocked
     * @throws IllegalArgumentException if {@code level} is {@code null}
     */
    public boolean isUnlocked(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        return level == Level.POINT_ESTIMATES
                || highestUnlockedLevel == Level.CONFIDENCE_INTERVALS;
    }

    /**
     * Returns the permanent progression state after considering one session.
     *
     * <p>Only point-estimate points can unlock the second level. Confidence-interval raw losses
     * use a different scale and therefore cannot satisfy the point threshold. Once the second
     * level is unlocked this method returns the same state, regardless of later performance.</p>
     *
     * @param sessionResult the session to consider for advancement
     * @return a state advanced to confidence intervals when the criterion is met, otherwise this
     *         unchanged state
     * @throws IllegalArgumentException if {@code sessionResult} is {@code null}
     */
    public LevelState afterSession(SessionResult sessionResult) {
        if (sessionResult == null) {
            throw new IllegalArgumentException("sessionResult must not be null");
        }
        if (highestUnlockedLevel == Level.CONFIDENCE_INTERVALS
                || sessionResult.getLevel() != Level.POINT_ESTIMATES
                || sessionResult.getAnsweredQuestionCount()
                        < MINIMUM_ANSWERS_FOR_ADVANCEMENT) {
            return this;
        }

        OptionalDouble meanPoints = sessionResult.getMeanPoints();
        if (meanPoints.isPresent()
                && meanPoints.getAsDouble() >= MINIMUM_MEAN_POINTS_FOR_ADVANCEMENT) {
            return new LevelState(Level.CONFIDENCE_INTERVALS);
        }
        return this;
    }
}
