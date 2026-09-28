# ADR 0025 — Inject session inputs for deterministic UI tests

- **Status:** Accepted by the project owner
- **Date:** 2026-09-28

## Context

`TrainingStrategy` and `QuizSession` already accept a `Random`. However, `QuizActivity`
constructed `new Random()` and opened the production asset internally. Launching Home and
pressing Start could not supply reproducible inputs. This was a missing construction boundary
in the Android layer, not a problem to solve with sleeps or repeated test attempts.

## Decision

Let `GiveOrTakeApplication` own a package-private `QuizDependencies` provider. Its production
implementation uses the existing asset loader and creates a fresh `Random` per new session.
Both configured-Intent creation and quiz creation/restoration obtain the bank from this provider.
Restoration still restores the saved queue; it does not shuffle it again.

An outer JUnit4 rule installs a five-question bank from the test APK and supplies a fresh
`Random(42L)` for each session. `ActivityScenarioRule<MainActivity>` is the inner rule, so
injection and preference setup happen before Home launches, not in a late `@Before` method.
The test controls session length, category, answer mode, personal best and number-formatting
locale. Cleanup closes leftover Activities and restores the provider, locale and preferences.

The test follows real explicit Intents through Home, Quiz, Feedback and Result. It uses the
real scheduler and scoring policy. Expected order, true values and scores are explicit test
data; they are not calculated by calling the production scheduler/scorer a second time.

## Alternatives considered

| Approach | Benefit | Cost |
|---|---|---|
| Fixed seed with production bank | Small input seam; exercises real bundled data | Ordering and expectations change with bank contents, ordering and filters |
| Small injected bank and fixed seed | Stable, inspectable examples; same real navigation and scoring | Small application provider and fixture lifecycle rule |
| Dedicated test build variant | Can isolate fixtures and application storage | Extra build/source-set configuration and another configuration to maintain |

The owner selected the second option. There is no new dependency, build variant, seed Intent
extra, Android import in `core`, or change to the production random distribution.

## Consequences

Five guesses deliberately equal twice the truth: 50 points and CLOSE each, yielding 50 / 100,
zero correct, five close and zero wrong. Different guesses and truths detect a feedback screen
that mistakenly copies the guess into the true-value field. CLOSE does not trigger repeats, so
the run has a known length. Wrong-answer scheduling remains covered by the core tests.

The fixture lives only in `androidTest` assets. The application provider is mutable within the
app package; replacement must happen on the main thread before launching Activities and must
be restored afterward. This is scoped to this local, sequential instrumented-test process.

The UI result does not depend on completion of asynchronous history writes. This test therefore
needs no custom IdlingResource and makes no database-durability claim. The real history writer
still runs and may leave synthetic history on a device; use a dedicated test AVD. Production
asset validation remains covered by the existing bank tests.

Instrumented execution remains local under the existing CI policy. See
[the execution and synchronisation notes](../complete-session-test.md).
