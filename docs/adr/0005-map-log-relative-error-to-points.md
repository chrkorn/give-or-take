# ADR 0005 — Map log-relative error to points with exponential decay

- **Status:** Accepted
- **Date:** 2026-09-04

## Context

ADR 0004 defines the raw error for a point estimate as
`|log10(guess / trueValue)|`. The app also needs a user-facing score from 0 through 100.
That mapping should be understandable, should reward every improvement, and should produce useful
session totals without discarding the more precise raw error.

A piecewise band table would be easy to label, but boundaries give noticeably different points to
nearly identical guesses. Values within each band also become indistinguishable when averaged over
a session.

## Decision

Map raw error to points with exponential decay:

`points = round(100 * exp(-ln(10) * error))`

The constant `ln(10)` makes this equivalent to `100 / factor`, where `factor` is the
multiplicative distance between the guess and truth. An exact answer therefore receives 100
points, an answer wrong by a factor of 2 receives 50, and an answer wrong by a factor of 10
receives 10. Round each result to the nearest whole point for display and session totals.

Retain the unrounded raw error alongside the points so later analysis does not lose precision.

## Consequences

- Every improvement in multiplicative accuracy improves the unrounded score; there are no band
  boundaries.
- Points have a short user-facing explanation: 100 divided by the factor by which the answer is
  wrong.
- Averaging points across a session is stable, but it is not the same as transforming the average
  raw error because the points mapping is non-linear.
- Rounding individual results to whole points loses a small amount of detail, while the stored raw
  error remains available for precise statistics.
- Direction and the threshold that classifies an answer as wrong remain separate decisions.

## Alternative considered

A piecewise table using factor thresholds of 1.10, 1.25, 1.5, 2, 5, and 10 was rejected. Its
labels are intuitive, but its discontinuities can make small changes feel unfair and make session
averages less informative.
