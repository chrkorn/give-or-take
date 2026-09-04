package de.christiankorn.giveortake.core;

/**
 * Defines how a numerical guess is compared with a question's authoritative value.
 */
public interface ScoringPolicy {
    /**
     * Calculates the raw error and user-facing points for a guess.
     *
     * @param question the question containing the authoritative value
     * @param guess the answer to evaluate
     * @return an immutable scoring result
     * @throws IllegalArgumentException if either argument is {@code null} or the guess type is
     *         unsupported by this policy
     */
    Score score(Question question, Guess guess);
}
