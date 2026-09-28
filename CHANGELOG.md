# Changelog

All notable changes to Give or Take are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release
groups user-visible and development-facing changes under clear categories so the project's
evolution can be followed without reading individual commits.

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
