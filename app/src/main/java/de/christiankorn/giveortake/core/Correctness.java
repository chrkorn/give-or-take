package de.christiankorn.giveortake.core;

/**
 * Describes the user-facing accuracy band of a point estimate.
 */
public enum Correctness {
    /** The estimate is within a factor of 1.5 of the true value. */
    CORRECT,

    /** The estimate is outside the correct band but within a factor of 3 of the true value. */
    CLOSE,

    /** The estimate is more than a factor of 3 from the true value. */
    WRONG
}
