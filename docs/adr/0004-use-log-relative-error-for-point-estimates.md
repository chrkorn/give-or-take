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

This ADR does not decide the mapping from raw error to a user-facing 0–100 points value;
that remains open for the next step. It also does not decide how scoring handles guesses of
zero or negative values. The positive-value invariant constrains the true value, not user
input, so non-positive guesses require a separate explicit decision.

## Alternatives considered

**Absolute error** is rejected because its meaning changes with the scale and unit of the
question.

**Percentage error** is rejected because equally large multiplicative underestimates and
overestimates receive different errors.

**Ratio error** is rejected as the stored metric because, although symmetric and scale-free,
its multiplicative form does not average across a session as naturally as the additive log
form.
