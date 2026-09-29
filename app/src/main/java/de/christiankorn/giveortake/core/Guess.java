package de.christiankorn.giveortake.core;

/**
 * Marks an immutable answer to a numerical estimation question in the pure-Java core layer.
 *
 * <p>The concrete type distinguishes a {@link PointGuess} from an {@link IntervalGuess}.
 * Keeping the alternatives as separate types prevents either answer form from carrying fields
 * that only make sense for the other. This hierarchy implements ADR 0003
 * ({@code docs/adr/0003-represent-guesses-as-separate-types.md}).</p>
 */
public interface Guess {
}
