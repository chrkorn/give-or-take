# ADR 0022 — Build settings with ordinary Activity controls

- **Status:** Accepted
- **Date:** 2026-09-25

## Context

The app needs settings for session length, answer mode, and a multi-select category filter, plus a
destructive statistics reset. AndroidX Preference would provide ready-made preference rows and
automatic `SharedPreferences` integration, but it adds another dependency and an API family that
the course book does not cover. This project is assessed partly on visible transfer of the book's
Java, XML layout, `findViewById`, Activity, listener, dialog, and `SharedPreferences` concepts.

A setting changed while a quiz is running must not alter the active question schedule or swap its
answer controls. Reading mutable preferences every time `QuizActivity` resumes would make rotation
or process recreation capable of changing the session halfway through.

Category names belong to the versioned question bank. Duplicating them in `strings.xml` would let
the filter drift away from the data. An empty category selection cannot start a valid core session.
Resetting statistics affects both raw SQLite history and the level-specific personal-best cache in
`SharedPreferences`, and must not be possible through one accidental tap.

## Decision

Use a real `SettingsActivity` with an XML layout and ordinary Material controls already supplied by
the existing Material dependency. Do not add AndroidX Preference. Store the three choices through
one `QuizSettings` wrapper, which owns all preference names, keys, validation, and first-run
defaults: ten questions, follow the current level, and all categories selected.

Derive the category list from the loaded `QuestionBank` in stable alphabetical order. Create the
checkboxes at runtime and refuse an attempt to uncheck the final selected category. If an updated
bank no longer contains any stored selection, restore all current categories so the player is not
left with an unusable pool.

At each production quiz entry point, load one validated settings snapshot and copy its resolved
level, requested length, and category names into primitive Intent extras. `QuizActivity` reads those
extras only during creation and saves the evolving core session separately. The launch Intent
survives Activity recreation, so later preference changes apply only to the next new session.

Place reset behind a confirmation dialog whose message names the exact session and answer row
counts and warns that every personal best is also removed. The negative action receives focus when
the dialog opens. Serialize counting and deletion on the existing history worker so reset cannot
overtake pending writes; clear personal-best preferences only after the SQLite transaction commits.

## Alternatives considered

**`PreferenceFragmentCompat`** would reduce binding code and is idiomatic for conventional Android
settings, but it adds AndroidX Preference, hides persistence work that is useful examination
evidence, and introduces a library not taught by the course material.

**Read preferences inside a running Activity** is shorter, but a configuration change could apply
a new length, mode, or category filter to restored state. It would violate the promise that changes
take effect only on the next session.

**Hard-code category rows in XML** gives simple view binding but requires a code release whenever
the data vocabulary changes and can silently diverge from the bank.

**Allow an empty filter to mean all categories** avoids validation but makes “none selected” and
“all selected” visually contradictory. Keeping one explicit selection is clearer.

## Consequences

- The screen contains more Java and XML than a Preference fragment, but every mechanism maps to
  concepts required by the course and no dependency is added.
- New question-bank categories appear automatically in settings.
- Active quizzes remain deterministic across settings edits and Activity recreation.
- Reset is asynchronous, ordered with history writes, explicit, and all-or-nothing for SQLite.
- SQLite history and personal-best preferences are separate stores; the preference cache is cleared
  only after database deletion succeeds, avoiding a reported success after a failed history reset.
