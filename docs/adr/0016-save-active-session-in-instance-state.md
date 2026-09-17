# ADR 0016 — Save the active session in Activity instance state

- **Status:** Accepted
- **Date:** 2026-09-17

## Context

`QuizActivity` needs to retain the exact current question, delayed wrong-answer repeats, and
recorded scores when Android recreates it. Recreation normally occurs after a configuration change
such as rotation and may also occur after Android kills a background application process. The
project deliberately does not use `ViewModel` or the Jetpack lifecycle library, and its `core`
package must remain free of Android imports.

The state is transient: completing or explicitly leaving a quiz ends that attempt. Long-term
statistics and completed results have different persistence requirements and will use the data
layer rather than the Activity-state mechanism.

## Decision

Store a minimal snapshot of the active session with `onSaveInstanceState` and reconstruct the core
session in `onCreate`. The snapshot is a plain Java value in `core`. It records stable question
identifiers in their scheduled order, the current identifier, prior scores, and initial session
size. It does not serialise `QuestionBank`, `TrainingStrategy`, `Random`, or Android objects.

The Android layer writes the snapshot as primitive arrays, strings, and string lists in a `Bundle`.
After recreation it reloads the bundled question bank, resolves the saved identifiers, and restores
the core session. A missing identifier is treated as incompatible application state and fails
clearly instead of silently substituting a different question.

`Parcelable` is rejected for the core snapshot because it would introduce an `android.*` import.
Java object serialisation is also avoided: an explicit Bundle representation is smaller and does
not turn private implementation fields into a persistence contract.

## Alternatives considered

### Application-scoped holder or singleton

An in-memory holder survives Activity recreation while the application process remains alive, but
loses the session after process death. It also introduces global lifecycle and cleanup questions,
especially if more than one quiz Activity instance exists. This does not satisfy the required
background-process restoration behaviour.

### Suppress recreation with `android:configChanges`

Handling selected changes in the Activity would retain its fields for those changes only. It does
not handle process death or every possible recreation, and it makes the Activity responsible for
refreshing configuration-dependent resources. It would avoid rather than demonstrate the normal
Activity lifecycle taught by the course.

### Persist every answer to SQLite

SQLite could restore a quiz after process death, task dismissal, force-stop, or reboot, subject to
the transaction having committed. It would require a session schema, DAO, transaction ordering,
cleanup policy, and compatibility handling for changed question banks. That durability and
complexity are unnecessary for an attempt that the user has explicitly dismissed.

## Consequences

- Rotation and other Activity recreation preserve the exact repeat schedule and accumulated scores.
- System-initiated process death can be restored from the state retained outside the application
  process.
- The orchestration and snapshot round-trip remain testable with ordinary JUnit tests.
- Bundle contents stay small because question content is reloaded from the asset by stable ID.
- The session does not survive Back or `finish()`, removal from Recents, force-stop, reboot, or any
  other complete user dismissal. Supporting those cases would require durable persistence and a
  separate product decision.
- Saved instance state is a restoration mechanism, not storage for completed results or statistics.
