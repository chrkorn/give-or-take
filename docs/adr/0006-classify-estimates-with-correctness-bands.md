# ADR 0006 — Classify estimates with correctness bands

- **Status:** Accepted
- **Date:** 2026-09-07

## Context

ADR 0004 defines estimation performance continuously as the log-relative error
`|log10(guess / trueValue)|`, and ADR 0005 maps that error continuously to points. This model
preserves meaningful differences between estimates and avoids arbitrary discontinuities in the
score itself. However, the project specification requires an assessment and training strategy
that includes behaviour such as assigning points to correct answers and repeating wrong answers.
A repeat queue cannot act on a continuous value without a rule that classifies an answer as wrong.

The resulting tension is imposed by the acceptance criteria rather than arising from an
independent preference for categorical scoring. The continuous score should remain the primary
measure of estimation quality, while a separate classification supplies the binary signal needed
by the training strategy. This prevents the operational requirement from reducing the precision
of the underlying score.

## Decision

Every valid point estimate is assigned one of three correctness bands from its raw log-relative
error. The bands are evaluated in the order shown, so an answer classified as `CORRECT` is not
subsequently classified as `CLOSE`:

| Band | Log-relative error | User-facing meaning |
| --- | ---: | --- |
| `CORRECT` | `error <= log10(1.5)`, approximately `0.176` | The estimate is within a factor of 1.5 of the truth. |
| `CLOSE` | `error <= log10(3)`, approximately `0.477` | The estimate is more than a factor of 1.5 away, but within a factor of 3. |
| `WRONG` | `error > log10(3)` | The estimate is more than a factor of 3 from the truth. |

“Within a factor of 1.5” means that the estimate lies between the true value divided by 1.5 and
the true value multiplied by 1.5. The equivalent explanation applies to a factor of 3. Factor
language will be used in user-facing feedback because it is more readily interpreted than a
logarithmic value.

Two complementary arguments can justify the initial boundaries. First, a colloquial account of
a good estimate can regard an answer no more than one-and-a-half times from the true value as
sufficiently accurate to count as correct, while an error beyond threefold is no longer reasonably
described as close.
This argument is comprehensible and supports the labels, but it remains a judgement about ordinary
language. Second, the boundaries can be calibrated against a sample of real guesses, aiming for
approximately one third of answers in each band. Such calibration would make all three outcomes
useful in practice and would avoid a repeat queue that is either nearly empty or overwhelming.
The empirical argument is academically more defensible because its assumptions and observed
distribution can be documented and evaluated. The colloquial argument remains useful for
explaining and initially selecting thresholds before sufficient observations exist.

The bands will initially be global across all question categories. Category-specific boundaries
could be fairer if, for example, population questions systematically produce larger errors than
length questions. They would, however, require enough representative observations in every
category, which the application does not possess on its first release. Global boundaries are
therefore simpler, consistent, and testable at launch. Category-specific calibration may be
reconsidered when adequate data exist.

Only `WRONG` answers enter the repeat queue. All three bands determine the feedback screen colour
and contribute to level-progression rules. Points and raw error remain unchanged by the
classification.

## Consequences

The repeat queue, feedback presentation, and level progression depend on a shared classification
policy rather than implementing their own thresholds. Boundary tests must cover exact values and
values immediately on either side. Because raw error is retained, later threshold changes do not
require answers to be rescored; stored results can be reclassified. Such tuning may nevertheless
change historical band summaries and progression outcomes, so a threshold version or migration
policy will be required if historical classifications become persistent user-visible records.

The thresholds are tunable product parameters, not discovered truths about estimation ability.
The project report will present them as explicit, provisional design choices and will distinguish
their rationale from empirical validation. Any later adjustment must be recorded as a new decision
or amendment and must update the shared policy and its tests together.
