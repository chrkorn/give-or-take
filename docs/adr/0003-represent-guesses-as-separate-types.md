# ADR 0003 — Represent point and interval guesses as separate types

- **Status:** Accepted
- **Date:** 2026-09-04

## Context

An answer is either a point estimate or a 90 percent confidence interval. The model must remain
plain Java, support later SQLite persistence and Intent navigation, and give scoring code a safe
way to distinguish the two forms.

One option is a single `Guess` class containing a point value and optional lower and upper bounds.
It needs fewer source files and maps directly to one SQLite row or one set of Intent extras.
However, nullable or sentinel-valued bounds allow impossible combinations: one bound without the
other, a point carrying interval data, or an interval also carrying a meaningless point value.
Every consumer must interpret and validate those combinations. Scoring would switch on a mode flag
and then trust that the corresponding fields are present.

The other option is a common `Guess` interface implemented by `PointGuess` and `IntervalGuess`.
Each concrete type can contain only the fields that are meaningful for that answer form, so those
impossible states are not representable. Scoring can dispatch with `instanceof` and a cast in Java
11. A visitor could avoid type checks, but would add generic methods and a visitor implementation
before the scoring result types are known.

The hierarchy costs two additional small classes and requires persistence adapters to store a type
discriminator. SQLite can still use one table with a `guess_type`, a point column, and two bound
columns, or separate tables if later querying needs justify them. Intent navigation can pass the
type discriminator and primitive doubles as extras; this keeps Android APIs out of the core model
and avoids the overhead and coupling of `Serializable` or `Parcelable`.

Interval width also needs a definition. Absolute width, `upper - lower`, is scale-dependent and is
therefore inconsistent with log-relative point scoring. Logarithmic width,
`log10(upper) - log10(lower)`, measures the number of orders of magnitude covered and gives every
tenfold interval the same width. It requires positive bounds, which matches the positive value
domain accepted in ADR 0002.

## Decision

Use the `Guess` interface with immutable `PointGuess` and `IntervalGuess` implementations. Keep the
interface free of type-specific accessors. Scoring will initially use explicit `instanceof`
dispatch; a visitor is deferred unless several independent consumers make the repeated dispatch
worth its boilerplate.

Both guess types accept only finite values greater than zero. An interval additionally requires its
lower bound to be less than or equal to its upper bound. `IntervalGuess.width()` returns logarithmic
width and `containsTruth` treats both bounds as inclusive.

## Consequences

- Point guesses cannot carry bounds, and interval guesses cannot be missing either bound.
- The compiler exposes type-specific operations only after the concrete guess type is known.
- SQLite and Intent adapters need a discriminator and a small mapping step.
- Adding another guess form requires a new implementation and an update to scoring dispatch.
- Java 11 cannot close the interface to unknown implementations, so scoring must reject unsupported
  implementations explicitly rather than assuming only these two can ever exist.
- Zero and signed guesses remain outside the initial domain; supporting them requires a scoring and
  width policy designed for those values.
