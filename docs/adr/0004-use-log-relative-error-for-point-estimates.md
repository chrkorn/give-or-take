# ADR 0004 — Use log-relative error for point estimates

- **Status:** Accepted
- **Date:** 2026-09-04

## Context

The question bank spans many orders of magnitude: river lengths around 350 km, building
heights around 300 m, and national populations around 2 × 10^8. Absolute error cannot
compare performance meaningfully across those scales. Percentage error is scale-free but
asymmetric: estimates twice the true value and half the true value are equally distant by
ratio, yet have different percentage errors.

The following values use a true value of 346. All calculated metrics are rounded to three
decimal places.

| Guess | Absolute error | Percentage error | Log-relative error | Ratio error |
| ---: | ---: | ---: | ---: | ---: |
| 35 | 311.000 | 0.899 | 0.995 | 9.886 |
| 173 | 173.000 | 0.500 | 0.301 | 2.000 |
| 300 | 46.000 | 0.133 | 0.062 | 1.153 |
| 346 | 0.000 | 0.000 | 0.000 | 1.000 |
| 400 | 54.000 | 0.156 | 0.063 | 1.156 |
| 692 | 346.000 | 1.000 | 0.301 | 2.000 |
| 3460 | 3114.000 | 9.000 | 1.000 | 10.000 |

For a guess of 173, the calculations are `|173 - 346| = 173`, `173 / 346 =
0.500`, `|log10(173 / 346)| = |log10(0.5)| = 0.301`, and `346 / 173 = 2.000`.
For a guess of 692, they are `|692 - 346| = 346`, `346 / 346 = 1.000`,
`|log10(692 / 346)| = log10(2) = 0.301`, and `692 / 346 = 2.000`.

## Decision

Point estimates are scored with log-relative error:

`error = |log10(guess / trueValue)|`

An error of 0 is exact, approximately 0.301 is wrong by a factor of 2, and 1.0 is
wrong by a factor of 10. The metric is scale-free and symmetric under multiplication:
doubling and halving the true value produce the same error.

This metric depends on the domain invariant `trueValue > 0`, enforced in the
`Question` constructor. Signed and zero-valued true quantities require a different scoring
policy and are outside the project scope.

## Consequences

Errors from differently scaled questions can be compared directly. Log-relative error and
ratio error are monotonic transformations of each other: log-relative error is the base-10
logarithm of ratio error. The log form is preferred because multiplicative errors become
additive, so errors average sensibly across a session.

Taking the absolute value discards direction: the score does not say whether an estimate is
too high or too low. The feedback screen must recover that information separately by
comparing the guess with the true value.

The metric is computed as `|log10(guess) - log10(trueValue)|` rather than by forming the
ratio first. The two forms are algebraically identical, but `guess / trueValue` overflows to
infinity, or underflows to zero, when two finite positive values are far enough apart. The
logarithm of such a ratio is infinite, which the `Score` constructor rejects with a message
that describes the symptom rather than the cause. Subtracting logarithms works in the
exponent domain and cannot overflow for any inputs the domain invariants permit.

## Alternatives considered

**Absolute error** is rejected because its meaning changes with the scale and unit of the
question.

**Percentage error** is rejected because equally large multiplicative underestimates and
overestimates receive different errors.

**Ratio error** is rejected as the stored metric because, although symmetric and scale-free,
its multiplicative form does not average across a session as naturally as the additive log
form.

## Amendments

**2026-09-07 — deferred questions resolved.** As originally accepted, this ADR left two
questions open. Both are now settled.

The mapping from raw error to a user-facing 0–100 points value is decided in
[ADR 0005](0005-map-log-relative-error-to-points.md).

Guesses of zero or negative values are rejected at the domain boundary rather than handled
by the scoring policy: the `PointGuess` constructor requires a finite value greater than
zero, mirroring the invariant `Question` places on the true value. The scoring policy
therefore performs no value validation of its own and relies on the invariant.

This means a non-positive estimate is treated as malformed input rather than as a very poor
answer, and the user interface must prevent its submission rather than scoring it. That is a
deliberate trade-off. For some questions zero is a coherent, merely very wrong answer, and
rejecting it removes an answer the user might sincerely wish to give. It is accepted here
because the alternative — defining a finite maximum error for an input the metric cannot
represent — introduces a discontinuity into an otherwise continuous scale, and because the
question bank is curated to quantities for which zero is not a plausible estimate.

**2026-09-07 — numerical form corrected.** The implementation originally formed the ratio
before taking the logarithm. Extreme-magnitude testing showed this produces an infinite raw
error for valid inputs. The Consequences section above now records the difference-of-
logarithms form, and `LogRelativeScoreTest` covers the failing cases.
