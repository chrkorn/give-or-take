# ADR 0019 — Persist raw answer history in explicit SQLite sessions

- **Status:** Accepted
- **Date:** 2026-09-22

## Context

Long-term statistics require every accepted point estimate and confidence interval to survive
application restarts. The history must remain useful if correctness thresholds, point mappings, or
the interval width penalty are later retuned. A stable question identifier alone is insufficient
for rescoring because question content, including its authoritative value, may be corrected without
changing the identifier.

A session could be inferred from gaps between answer timestamps, but any inactivity threshold would
be arbitrary. Interruptions, clock changes, and remedial repeats would make those inferred groups
unreliable. The existing active-session Bundle from ADR 0016 serves restoration rather than
long-term history and remains a separate concern.

The app already stores its level-specific high scores in `SharedPreferences`. A high score is a
small derived value, while sessions and answers are relational history.

## Decision

Use an application-private `SQLiteOpenHelper` database with separate `sessions` and `answers`
tables. A session records its curriculum level, initial distinct-question count, start and optional
end time, and one of three states: in progress, completed, or abandoned. Each answer references its
session and records its one-based submission order, stable question identifier, the authoritative
value used at submission, the raw point estimate or interval bounds, and its timestamp.

The answer table stores mutually exclusive nullable columns for point and interval inputs. A table
check accepts exactly one valid shape. The session level supplies the answer mode without repeating
it in every answer row. Foreign keys are enabled for every database connection and cascade answer
deletion when a session is deleted.

Do not store raw error, points, correctness, interval loss, containment, or session aggregates.
Those values remain products of the pure-Java scoring policies. Snapshotting the truth value is not
denormalised scoring output; it is an input needed to reproduce the original comparison after a
question-bank correction.

Store timestamps as non-negative UTC Unix epoch milliseconds in `INTEGER` columns. Store numerical
answers as `REAL`, identifiers and controlled vocabulary as `TEXT`, and use `NOT NULL`, `CHECK`,
`UNIQUE`, and foreign-key constraints to compensate for SQLite's dynamic typing.

Create indices for sessions by level and descending end time, and answers by question and answer
time. The unique session-and-sequence constraint also supplies the index used to load answers in
submission order. Do not index low-cardinality state values alone.

Insert a session when play starts and each answer immediately after the core session accepts it.
Insert the final answer and mark the session completed in one transaction. Explicitly leaving marks
an unfinished session abandoned. A process terminated without a lifecycle callback may leave a row
in progress; statistics exclude such rows, and SQLite does not become an alternative resume path.

Keep `HighScorePreferences` as a derived convenience cache. If scoring policy changes, rebuild that
cache from raw SQLite history. This preserves the small key-value use case for
`SharedPreferences`; it accepts that SQLite completion and preference refresh cannot share one
atomic transaction.

Use immutable, sequential migrations. `onUpgrade` applies one fixed old-version-to-next-version
step at a time and fails if a required step is missing. It never drops the history tables to
recreate them.

Test the helper and DAO with JUnit 4 and Robolectric so normal CI remains emulator-free. Retain a
small instrumented smoke test for optional execution against an Android device's SQLite build.

## Alternatives considered

**Persist computed scores beside inputs** would make current queries cheaper but would leave stale
history after policy tuning and create two possible authorities. A versioned derived cache can be
added later if measured data volume warrants it.

**Infer sessions from timestamps** avoids one table but cannot reliably preserve level, planned
size, completion, abandonment, or repeat membership.

**Move high scores into SQLite** permits an atomic completion-and-record transaction but duplicates
a derived value and replaces an existing tested preference implementation. It still requires a
rebuild after policy changes.

**Instrumented tests only** provide the highest Android fidelity but require an emulator or device
in CI. **SQLite JDBC** exercises a different integration boundary, and mocks do not verify schema
constraints, cursor mapping, or transaction rollback.

## Consequences

- Historical scores can be recalculated with new policies without modifying stored rows.
- Corrected or time-varying question values do not silently rewrite past comparisons.
- Abandoned attempts and remedial repeats remain distinguishable from completed sessions.
- Activities use typed DAO operations and contain no SQL or cursor management.
- Writes carry the small cost of maintaining two explicit secondary indices.
- Statistics must reconstruct guesses and apply core policies instead of reading precomputed score
  columns.
- Robolectric becomes a test-only dependency; an occasional device smoke test still guards the
  remaining fidelity gap.
