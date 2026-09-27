# ADR 0023 — Test Android components on the JVM with Robolectric

- **Status:** Accepted
- **Date:** 2026-09-22 (approved), recorded 2026-09-27

## Context

The project deliberately excludes instrumented tests from continuous integration: an emulator in
CI is slow, and flaky enough that a red run stops carrying information. The consequence is that
every class touching an Android type — `SQLiteOpenHelper` and its DAOs, `SharedPreferences`,
Intent construction, custom `View` measurement — had no automated coverage at all, because the
`core` package's no-`android.*` rule is precisely what makes the rest of the code untestable on a
plain JVM.

That gap was not theoretical. The instrumented suite could not start for six weeks: the Android
Studio template pinned Espresso 3.5.1, and `./gradlew` could not locate a JDK outside the IDE.
Five Espresso classes accumulated without executing a single assertion. During that period the
only automated evidence for any Android-dependent code was whatever ran on the JVM.

Robolectric provides Android framework implementations that run in an ordinary JVM test, so
`./gradlew test` can exercise Android-dependent classes with no device and no emulator.

## Decision

Add Robolectric as a **test-only** dependency (`testImplementation`), used for Android-dependent
logic that does not require a real screen: SQLite round trips and transaction behaviour,
`SharedPreferences` defaults and migration, Intent contracts and chooser wrapping, and custom
`View` measurement and drawing. It is in use in seven test classes.

Espresso is retained for what Robolectric cannot honestly assert: real layout, real touch
dispatch, real Activity lifecycle on a real platform image. The division is by what is being
claimed, not by convenience — a test that asserts a view is visible to a user belongs on a device.

Robolectric runs inside the existing CI job. No change to the workflow was required, and
instrumented tests remain excluded from it.

## Consequences

Android-dependent code is covered automatically on every push, which is the only reason the six
weeks of a broken instrumented harness did not leave that code entirely unverified. The JVM suite
grew from roughly 160 to 195 tests largely on this basis.

Robolectric is a simulation. A test passing under it is evidence that the logic is right, not that
the behaviour is right on a device — shadow implementations can diverge from the platform, and a
Robolectric test cannot detect a layout that renders off-screen or a touch target too small to hit.
Claims in the project report must therefore distinguish what was verified on the JVM from what was
verified on an emulator, and name the API level for the latter.

There is a real hazard in the division being drawn by convenience rather than by claim. Because
Robolectric tests are fast and always run, the temptation is to migrate assertions there simply to
keep CI green. The rule above exists to resist that.

Adding it also grew the test-time dependency surface substantially; Robolectric downloads platform
jars on first run. This is accepted for a test-only dependency that never ships in the APK.

## Alternatives considered

**Instrumented tests in CI.** Rejected before this decision and not revisited: emulator startup
dominates the run and flakiness destroys the signal. That exclusion is what creates the gap this
ADR fills.

**No coverage of Android-dependent code beyond Espresso.** This was the de facto position, and it
failed badly — the harness was broken for six weeks and nothing noticed, because nothing was
running.

**Moving more logic into the Android-free `core` package so it needs no framework at all.** Done
wherever it is honest, and preferred where possible. It does not help for code whose entire purpose
is to talk to `SQLiteOpenHelper`, `SharedPreferences` or `Intent`: relocating that would mean
writing an abstraction layer whose only client is the test suite.
