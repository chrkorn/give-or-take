package de.christiankorn.giveortake.core;

/**
 * Identifies a curriculum stage in numerical-estimation training.
 *
 * <p>Levels describe which skill the player practises rather than ranking question content, as
 * decided in ADR 0009
 * ({@code docs/adr/0009-use-curriculum-levels-and-mean-session-scores.md}).</p>
 */
public enum Level {
    /** Point estimates scored for multiplicative accuracy. */
    POINT_ESTIMATES,

    /** Ninety-percent confidence intervals scored for calibration and sharpness. */
    CONFIDENCE_INTERVALS
}
