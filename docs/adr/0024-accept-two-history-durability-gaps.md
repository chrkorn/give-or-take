# ADR 0024 — Accept two history durability gaps as known limitations

- **Status:** Accepted
- **Date:** 2026-09-27

## Context

The lifecycle review went through every screen for configuration-change and
process-death safety. Most of what it found was fixed: the statistics reset now survives rotation,
the active quiz session was already saved in instance state (ADR 0016), and results travel as
primitive Intent extras (ADR 0017). Two findings were not defects in a screen but properties of
how history is persisted, and neither has a cheap fix.

**Queued writes can be lost when the process is killed.** Every history write is posted to the
single-thread executor in `QuizHistoryStore` (ADR 0020) and committed by the worker some time
later. Between the post and the commit there is a window in which the system reclaiming the
process, or the user swiping the app away, loses the write. The exposure is an answer submitted
immediately before the app is backgrounded and killed: it can be missing from the statistics that
are shown when the app is next opened.

**A reset is not atomic across the two stores.** Clearing statistics deletes the SQLite session
rows in one transaction, and then, in that operation's success callback, clears the personal bests
held in `SharedPreferences`. No transaction spans both, and `SharedPreferences.apply()` is itself
asynchronous, so the gap is a little wider than the two statements suggest. A process kill inside
the gap leaves an empty history alongside a surviving personal best — a number the user can see
with no session behind it.

## Decision

Accept both as known limitations, document them here and in the project report, and do not
implement a fix in this project.

For the first, rely on the properties the current design does guarantee. The executor is serial,
so a loss is always a suffix of the queue: answer five is never persisted without answer four.
Answer rows are written under their session row and removed with it, so a partial loss produces a
short session, never an inconsistent one. Nothing the user sees is wrong; there is simply less of
it than there should be.

For the second, accept the inconsistency and note that it is not silent. A stale personal best is
visible and is corrected the next time a session beats it or the user resets again, and today's
fix makes rotation during a reset recover on its own — only an outright process kill inside the
gap leaves the split.

## Consequences

The statistics used for the report's evaluation section are collected on a device that is not
being killed mid-session, so the first gap does not threaten the data run. It would matter for an
app that users actually depend on, and the report says so rather than claiming a durability the
implementation does not have.

The second gap means the personal-best figure and the session history can disagree. Any claim in
the report about reset behaviour has to be stated as "clears both stores in sequence", not
"atomically".

Recording these rather than fixing them is itself the decision worth defending. Both fixes are
known and neither is large; what they are not is free, and spending the remaining effort on a
durability property that a single-user quiz app does not need would be the wrong trade at this
point in the project.

## Alternatives considered

**Write synchronously on the caller's thread.** Removes the queue and therefore the window, and
puts SQLite I/O on the main thread, which is exactly what ADR 0020 exists to avoid. Rejected: it
trades a rare loss of one answer for a frame drop on every answer.

**Flush the queue in `onPause`.** Narrows the first gap substantially, because `onPause` runs
before the process becomes a candidate for reclaim. Rejected for this project because doing it
honestly means blocking `onPause` on the worker, which is main-thread I/O again under a different
name; doing it dishonestly — posting a flush and not waiting — changes nothing.

**Move personal bests into SQLite.** Makes the reset a single transaction and closes the second
gap completely. This is the right fix and it is not difficult. Rejected on scope: `SharedPreferences`
is a course-book topic the project is meant to demonstrate, and the high score is the only place it
is genuinely the natural store.

**A "reset in progress" flag in `SharedPreferences`, checked at startup.** Closes the second gap
without moving any data, by finishing an interrupted reset on the next launch. Rejected as more
mechanism than the failure deserves: it adds a startup code path that would run on every launch to
guard against a kill inside a window of a few milliseconds.
