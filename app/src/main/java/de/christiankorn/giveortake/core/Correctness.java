package de.christiankorn.giveortake.core;

/**
 * Describes the user-facing accuracy band assigned to a point estimate by
 * {@link CorrectnessClassifier}.
 *
 * <p>The three bands implement ADR 0006
 * ({@code docs/adr/0006-classify-estimates-with-correctness-bands.md}). They supplement rather
 * than replace the continuous error retained in {@link Score}.</p>
 */
public enum Correctness {
    /** The estimate is within a factor of 1.5 of the true value. */
    CORRECT,

    /** The estimate is outside the correct band but within a factor of 3 of the true value. */
    CLOSE,

    /** The estimate is more than a factor of 3 from the true value. */
    WRONG
}
