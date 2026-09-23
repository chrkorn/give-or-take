# ADR 0021 — Derive history statistics with core policies

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

The statistics screen needs paged recent sessions, interval coverage and width, a coverage trend,
point-estimate error over time, per-category performance, and mode-specific personal bests. The
history is intentionally raw under ADR 0019, so these values must be derived when read.

SQLite can calculate interval hits with `CASE` and `SUM`, but that would duplicate the inclusive
containment rule in `IntervalGuess`. Calculating logarithmic width, log-relative error, interval
loss, and personal-best comparisons in SQL would duplicate more of the tested core policies.
Android's SQLite build also does not guarantee a base-10 logarithm function. The expected history
contains at most a few thousand answers and ADR 0020 already keeps database work off the main
thread, so transferring the selected primitive rows is not a practical performance concern.

The version-1 answer schema stores question identity and the scoring-time truth value but not the
question's category. Resolving categories against the current bundled bank would silently
reclassify history after a bank correction and could not classify a removed question. Historical
per-category statistics therefore need the answer-time category as another raw input snapshot.

The statistics wireframe specifies coverage over rolling windows of ten interval answers. A fixed
window gives every plotted point the same denominator; session buckets can vary because remedial
repeats extend sessions, while weekly buckets may be empty for an occasional user.

## Decision

SQLite selects, joins, orders, and pages history with bound parameters. The data layer closes every
cursor and converts rows into private Java values. It then calls `LogRelativeScore`,
`IntervalScore`, `IntervalGuess`, `CalibrationTracker`, `SessionResult`, and `HighScore` to derive
statistics. Public methods return immutable typed result objects and never expose a cursor.

Add scoring-policy overloads that accept a stored positive truth value and the policy's concrete
guess type. Live scoring continues to use the `Question`-based interface and delegates to the same
arithmetic. This avoids manufacturing incomplete historical `Question` objects in the data layer.

Increment the database to version 2 and add nullable `answers.category_at_answer`. New answers
store the category from their `Question`. The column remains nullable because a schema-only
migration cannot recover the original category of existing rows and the core question model
allows an optional category. Statistics group those legacy rows under `Uncategorised` rather than
guessing from the current question bank.

Coverage trend points use the most recent ten interval answers and advance by one interval answer.
They are absent until ten interval answers exist. Mean log-relative error is reported per ended
point-estimate session. Completed and explicitly abandoned answers contribute to learning and
calibration statistics, but only completed non-empty sessions may establish a personal best.
Point-mode records compare mean points with higher values better; interval-mode records compare
mean proper interval loss with lower values better, preserving the existing `HighScore` contract.

## Alternatives considered

**Aggregate in SQL** would transfer fewer rows, but the database must still scan them and the small
data volume does not justify duplicate numerical rules and SQLite integration tests for each rule.

**Return SQL hit and sample counts to `CalibrationTracker.restore`** retains the presentation API
but still duplicates containment in SQL and cannot portably calculate the required logarithmic
width.

**Resolve categories from the current bundled question bank** avoids a migration but makes old
statistics depend on current mutable content and loses removed identifiers.

**Bucket coverage per session or calendar week** gives non-overlapping points, but denominators
vary by session and sparse weeks create gaps plus a time-zone policy. Both can be added later from
the same raw rows if the visual design changes.

## Consequences

- One tested set of core policies defines live and historical scoring.
- Statistics require a bounded in-memory pass over the selected answer history.
- Paged session selection remains a database operation and does not load every session first.
- Version-1 answers remain readable but appear in the explicit legacy category.
- Overlapping trend points are not statistically independent and must be presented as a visual
  trend, not as separate evidence.
- A future measured performance problem can add a versioned derived cache without changing the
  raw-history authority.
