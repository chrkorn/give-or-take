## 2026-09-01 — Repository created
- Created public GitHub repo `give-or-take` as the primary remote for the app.
- Empty repo for now.

## 2026-09-02 — Project skeleton
- Android Studio Empty Views Activity, Java, minSdk 26
- verified the Gradle wrapper is tracked (examination criterion: the repo must build standalone).

## 2026-09-02 — Architecture decision recorded
- Added ADR 0001, documenting the decision to use Java with XML layouts, using AI-polished wording.
- Recorded the curriculum alignment, acceptance-criterion fit, trade-offs, and rejected alternatives.

## 2026-09-03 — CI
- GitHub Actions running ./gradlew test on every push; verified it actually fails when a test fails.
- Instrumented tests deliberately excluded from CI (needs an emulator, too slow and flaky); they run locally before tags.
