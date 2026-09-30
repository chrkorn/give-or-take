# Changelog

All notable changes to Give or Take are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release
groups user-visible and development-facing changes under clear categories so the project's
evolution can be followed without reading individual commits.

## [v1.0-submission] - 2026-09-30

The version submitted for assessment. Give or Take is an Android quiz for practising numerical
estimation: players answer quantity questions either with a single estimate, scored by how many
times too high or too low it was, or with a 90% confidence range, scored for width and for whether
it contains the truth. Across sessions the app compares the stated 90% confidence with how often
the ranges actually contained the answer.

### Release summary

- Seven screens (home, point-estimate quiz, 90% interval quiz, feedback, result, statistics,
  settings) implemented by six `AppCompatActivity` classes connected with explicit Intents; the
  two quiz variants share one mode-aware `QuizActivity`.
- An Android-free `core` package holding the scoring, correctness bands, interval scoring,
  calibration tracking, training strategy with delayed repeats of wrong answers, and level
  progression, covered by plain JUnit tests.
- An 80-question bank in four categories, generated from Wikidata and curated by hand, with
  revision-pinned or primary-source links on every question.
- A custom uncertainty dial for building ranges from a best guess and a multiplicative factor, and
  a custom calibration chart.
- SQLite session and answer history with statistics derived through the core policies, settings and
  personal bests in `SharedPreferences`, and sharing and source links through implicit Intents.
- Material 3 light and dark themes, large-text-safe layouts and non-colour outcome cues.
- Design decisions recorded in 25 ADRs under `docs/adr/`, with a dated development log.

### Fixed

Found in a structured self-testing pass after v0.9; each has its own commit and regression test.

- A positive estimate too small for a `double` passed validation and crashed the quiz.
- The statistics screen showed stale data after a session started from it.
- A configuration change on the feedback screen made the next question open with a validation
  error on an untouched field.
- Near-exact answers were described as "about 1× too low"; they are now worded as a percentage,
  and the statistics show mean closeness with the result screen's precision.
- Interval bounds appeared as raw doubles in direct entry and on the feedback screen, and the
  dial's starting factor produced bounds such as 22,499,999.
- A missing space before "· ended early" in the session history.
- Sessions left unfinished by a crashed or killed process stayed "in progress" for ever, so the
  statistics and the reset dialog disagreed about how much history existed.

### Changed

- README completed with screenshots, the actual project structure, an index of all ADRs, question
  data provenance and licensing, and build instructions verified from a fresh clone.

### Verified

- 213 JVM tests using JUnit 4 and Robolectric, and 40 Espresso instrumented tests on a Pixel 9
  API 35 emulator, both from a fresh clone of this commit.
- Android lint, debug APK assembly and release App Bundle assembly from the same fresh clone. The
  bundle is unsigned: no `signingConfigs` block exists, and signing credentials do not belong in
  source control.

## [v0.9-feature-complete] - 2026-09-28

### Added

- Complete seven-screen experience covering home, point-estimate quiz, 90% confidence-interval
  quiz, answer feedback, session results, statistics, and settings. The two quiz screens share one
  mode-aware `QuizActivity`; the flow is backed by six real `AppCompatActivity` classes connected
  with explicit Intents.
- Point-estimate answers scored by scale-free log-relative error, with human-readable closeness
  feedback, correctness bands, points, delayed repeats for wrong answers, and personal bests.
- 90% confidence-interval answers scored by a width-sensitive interval rule, with contained or
  missed feedback and calibration tracking that compares observed coverage with the 90% target.
- Custom hand-drawn uncertainty dial that converts a best estimate and multiplicative uncertainty
  factor into a visible lower and upper range; direct bound entry remains available as an
  alternative.
- App-private SQLite quiz history containing sessions and raw answers, plus `SharedPreferences`
  for settings and mode-specific personal bests.
- Statistics for completed and abandoned sessions, category performance, mean estimation error,
  personal bests, interval width, and a hand-drawn rolling calibration chart.
- Settings for session length, answer mode, question categories, and a confirmed statistics reset.
- Result sharing through Android's system chooser and question-source links delegated to an
  installed browser through implicit Intents.
- Material 3 day/night theme with explicit light and dark palettes, consistent typography and
  spacing, 48 dp or larger touch targets, scroll-safe large-text layouts, and non-colour outcome
  cues.

### Verified

- 195 JVM tests using JUnit 4 and Robolectric, plus 35 Espresso instrumented tests on a Pixel 9
  API 35 emulator.
- Android lint, debug APK assembly, Android-test APK assembly, and release App Bundle assembly.
  The bundle is unsigned: no `signingConfigs` block exists, and signing credentials do not belong
  in source control.

## [v0.1-core] - 2026-09-11

### Added

- Complete Android-free domain layer in the `core` package, with no `android.*` dependencies.
- Immutable question and point- and interval-guess models with validation for numerical edge
  cases.
- Scale-free log-relative point scoring and proper interval scoring that penalises unnecessarily
  wide confidence ranges.
- Correctness banding that turns continuous estimation error into `CORRECT`, `CLOSE`, and `WRONG`
  outcomes.
- Calibration tracking for empirical interval coverage, coverage gap, sample size, and mean
  interval width.
- Training strategy with deterministic session scheduling and delayed requeueing of wrong
  answers.
- Level progression, session-result aggregation, and level-specific high-score tracking.
- Strict, versioned JSON question-bank loading with provenance metadata and validation errors.
- JUnit 4 tests covering the domain rules and edge cases entirely on the JVM.
