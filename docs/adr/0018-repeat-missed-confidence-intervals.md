# ADR 0018 — Repeat confidence intervals that miss the truth

- **Status:** Accepted
- **Date:** 2026-09-21

## Context

The training strategy needs a discrete signal because the examination task requires incorrectly
answered questions to return later. Point estimates obtain that signal by applying the accepted
correctness bands to log-relative error. Confidence intervals instead use the continuous,
unbounded log interval loss from ADR 0007. Applying the point-estimate thresholds to that loss
would mix unlike quantities, while inventing interval-loss thresholds would introduce a new
arbitrary scoring decision.

Coverage and sharpness already have separate meanings in interval mode. Containment says whether
the stated 90 percent range covered this outcome. The proper interval loss additionally charges
for the range's logarithmic width, preventing broad ranges from becoming a free way to obtain
hits.

## Decision

A confidence-interval answer whose inclusive bounds contain the true value supplies
`Correctness.CORRECT` to the training strategy. An interval that misses the true value supplies
`Correctness.WRONG` and is scheduled for a delayed repeat under the same rule used by point
estimates. Interval mode never produces `Correctness.CLOSE`.

This classification is a practice-scheduling signal, not the interval score. `IntervalScore`
continues to calculate and retain the proper log interval loss, and `CalibrationTracker` records
containment and logarithmic width separately.

## Consequences

Users practise a question again after expressing too-narrow or misplaced uncertainty. A very
broad interval that contains the truth does not trigger a repeat, but its width still worsens the
session loss and remains retained by the calibration tracker. The `QuizSession` domain boundary
owns both updates atomically; the Android Activity only constructs a guess and submits it.

Feedback uses “contained” and “missed” rather than applying the point-mode labels to interval
answers. Result aggregation continues to report mean loss and calibration instead of correctness
band counts.

## Alternatives considered

**Never repeat interval questions** was rejected because it would omit the mandated remedial
behaviour from one curriculum level. **Classify an interval by a loss threshold** was rejected
because no accepted threshold exists, and the same loss can arise from different combinations of
width and miss distance. **Repeat excessively wide hits as well as misses** was rejected for the
same threshold problem; proper loss remains the principled signal for comparing sharpness.
