package de.christiankorn.giveortake.core;

/**
 * Marks an immutable answer to a numerical estimation question.
 *
 * <p>The concrete type distinguishes a single point estimate from a confidence interval.
 * Keeping the alternatives as separate types prevents point guesses from carrying meaningless
 * interval bounds.</p>
 */
public interface Guess {
}
