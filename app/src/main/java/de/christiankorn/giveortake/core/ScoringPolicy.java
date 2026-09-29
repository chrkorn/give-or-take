package de.christiankorn.giveortake.core;

/**
 * Defines a pure-Java core policy for comparing a {@link Guess} with a {@link Question}'s
 * authoritative value.
 *
 * <p>Implementations return a {@link Score} so raw policy values remain independent of Android
 * presentation and persistence concerns.</p>
 */
public interface ScoringPolicy {
    /**
     * Calculates the policy's raw value and any policy-defined user-facing points for a guess.
     *
     * @param question the non-null question containing the authoritative value
     * @param guess the non-null answer form supported by this policy
     * @return an immutable scoring result
     * @throws IllegalArgumentException if either argument is {@code null} or the guess type is
     *         unsupported by this policy
     */
    Score score(Question question, Guess guess);
}
