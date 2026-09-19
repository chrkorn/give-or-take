# ADR 0017 — Pass completed results as primitive Intent extras

- **Status:** Accepted
- **Date:** 2026-09-19

## Context

`ResultActivity` needs a compound summary containing the session level, score means, point-answer
bands, interval calibration, and the previous personal best. Android may recreate the Activity
after configuration change or process death, so a static in-memory holder is insufficient. The
`core` package must remain ordinary Java without `android.*` imports.

The result contract also needs to tolerate later additions. A result Intent may be retained by the
system while the process is absent, including across an application update, even though it is not
durable application storage.

## Decision

`ResultActivity.createIntent` accepts the compound `SessionResult` and `HighScore` domain values but
flattens them into named primitive extras. The contract carries an explicit format version and the
Activity checks every required field and cross-field count invariant before rendering.

`Parcelable` is not added to a core type because doing so would violate the framework-independent
package boundary. A separate Android-layer parcelable DTO would preserve that boundary and would
be efficient, but manual Java parceling adds a second representation whose ordered field schema is
easy to evolve incorrectly. Parcels are an IPC and saved-state format, not durable versioned
storage.

Java `Serializable` would keep Android imports out of core and require little initial code. It is
rejected because reflective object serialization is slower and more allocation-heavy than direct
primitive Bundle values, every reachable value becomes part of the serialization graph, and class
evolution depends on `serialVersionUID` compatibility. Those costs buy no useful simplicity for a
small, fixed screen contract.

The completed `QuizActivity` starts `ResultActivity` and immediately calls `finish()`. Therefore
system Back from the result reveals the existing Home Activity rather than the finished quiz.
“Play again” starts a fresh quiz and finishes the result. “Home” uses
`FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`: `CLEAR_TOP` removes any screens above the
existing Home instance, while `SINGLE_TOP` reuses that revealed instance rather than creating a
duplicate. The flags do not clear unrelated tasks or application data.

## Consequences

- Core result and high-score types remain unit-testable on the JVM without Android dependencies.
- Primitive extras avoid Java serialization overhead and Binder-unfriendly object graphs.
- Named keys allow optional fields to be added without changing an ordered binary layout; an
  incompatible semantic change must increment the format version.
- Mapping code is more verbose and must be kept in one factory to prevent key drift.
- The finished quiz cannot be reached through Back from the result, and both visible exit actions
  leave a deliberate stack.

