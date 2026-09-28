# Deterministic complete-session Espresso test

`CompleteSessionTest` launches the real home screen with `ActivityScenarioRule`, presses Start,
answers five questions, checks feedback after every answer and checks the result summary. The
fixture supplies a small bank and a fresh `Random(42L)`; see ADR 0025 for the decision.

The readable journey is in
`app/src/androidTest/java/de/christiankorn/giveortake/CompleteSessionTest.java`. Fixture setup and
cleanup are in `SessionFixtureRule.java`; the bank is in
`app/src/androidTest/assets/session-questions.json`.

## What the test establishes

Each turn checks the question prompt, unit and answered/remaining counter before typing. After
submission it checks the same prompt, answer number, submitted estimate, independent true value,
unit, CLOSE label, 50-point score and factor-of-two explanation. The five distinct values and
units make stale feedback or incorrect Intent contents observable. It finally checks 50 / 100,
zero correct, five close and zero wrong, then returns Home.

The estimates are twice the truths, so confusing the guess and truth cannot pass unnoticed.
CLOSE answers do not extend the schedule; a fixed number of arbitrary wrong answers would not
be a valid way to guarantee session completion. Tests in `TrainingStrategyTest` cover repeats.
This test covers point mode, not interval calibration, persistence durability or production bank
content. Those claims belong to their respective tests.

`RuleChain.outerRule(fixture).around(home)` matters: ActivityScenarioRule launches before JUnit
`@Before` methods. The outer rule controls preferences and number-formatting locale, installs
the provider before launch and restores state afterward, including when the test fails. A
fresh generator is necessary: reusing one seeded Random would let earlier sessions consume
its sequence.

## What Espresso synchronises

The JUnit test runs on the instrumentation thread. Espresso schedules view lookup, actions and
assertions on the UI thread and coordinates with its Looper. Before a view operation it waits
for the main MessageQueue to be idle with respect to messages needing immediate processing,
the framework's tracked AsyncTask work, and registered IdlingResources. It lets the main loop
process callbacks while waiting; it does not freeze the UI thread with a sleep. This is the
contract described in the [official Espresso documentation](https://developer.android.com/training/testing/espresso).

"Idle" does not mean the queue is permanently empty or every application thread has finished.
Future delayed messages may remain. A plain ExecutorService job can still be running while
the main queue is idle, even if it will post a UI update later. Espresso cannot infer that
future dependency just by observing the main queue.

Here, loading the bank, scoring, navigation and rendering the feedback/result happen on the
main thread. The database executor is separate, but its completion does not control the views
asserted by this test. No custom IdlingResource is needed for these assertions. The fixture's
`waitForIdleSync()` is only lifecycle cleanup; it is not a database completion barrier.

If the test is extended to assert asynchronously loaded statistics or saved history, register
an IdlingResource before triggering that work. Mark it busy before enqueueing the job, keep it
busy until the relevant result has been applied to the UI, and signal idle on completion,
including error paths. Keep `isIdleNow()` non-blocking and notify via `onTransitionToIdle()`
outside that method. Unregister in cleanup. This closes the gap between an idle main queue
and work that has not yet posted its UI callback. See Android's
[IdlingResource guidance](https://developer.android.com/training/testing/espresso/idling-resource).

`closeSoftKeyboard()` explicitly dismisses the IME before Submit; `scrollTo()` brings actions
into reach on smaller screens. No `Thread.sleep` or assertion-retry loop is used.

## Why execution stays out of this project's CI

The current workflow provisions no emulator or physical device. Instrumented execution requires
one; compiling its APK is not equivalent to running its assertions. Provisioning and booting an
emulator would add runtime and device-environment failure modes to this project's quick CI loop.
Under the existing policy, JVM tests remain in CI and this real-device UI journey runs locally.

This is a project-specific infrastructure decision, not a general claim that Espresso must
never run in CI or that UI flakiness is acceptable. Controlled devices and proper synchronisation
can support reliable CI execution; Android documents the relevant
[test-stability practices](https://developer.android.com/training/testing/instrumented-tests/stability).

## Run and retain evidence locally

Use a dedicated test AVD. These are real sessions and the history writer can persist synthetic
answers; the full existing suite also resets application data. Do not use personal quiz data.
Use the existing verified Pixel_9 API 35 configuration: the pinned Espresso 3.5.1 has a recorded
compatibility failure on API 37. No dependency upgrade was made for this change.

Run the existing helper from the repository root:

```sh
tools/run_instrumented_tests.sh Pixel_9
```

It selects the bundled JDK/SDK, boots the AVD if necessary, checks the connected device API,
disables animations, unlocks the device, runs the runner canary and then the instrumented suite.
It leaves an emulator it started running. Emulator-boot polling in that shell helper is separate
from synchronisation inside the Espresso test.

To run only the new test once the device is ready:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=de.christiankorn.giveortake.CompleteSessionTest \
  --console=plain
```

Alternatively, select that class in Android Studio and run it on the API 35 device. Do not use
`testDebugUnitTest` as evidence that this instrumented test executed.

Before another run overwrites outputs, copy both `app/build/reports/androidTests/connected/debug/`
and `app/build/outputs/androidTest-results/connected/debug/` to a dated evidence directory.
Retain the console output/exit status, HTML report, JUnit XML and failure logcat. Record the date,
commit hash (and any uncommitted diff), AVD/model, actual API level, relevant locale/animation
settings, test selection, counts, failures and duration in `docs/devlog.md`. Preserve a failed
run before fixing it; record the subsequent run separately. Screenshots or a screen recording
can illustrate the report, but the runner's actual assertions establish pass/fail.

Repeat after relevant navigation, scoring, fixture or UI changes and before a release tag.
Neither a written test nor a successful APK build counts as an executed UI test.
