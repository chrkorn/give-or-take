# Changelog

All notable changes to Give or Take are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release
groups user-visible and development-facing changes under clear categories so the project's
evolution can be followed without reading individual commits.

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
