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

## 2026-09-03 — Public README
- Added setup, test, architecture, attribution, and licence information for repository visitors.

## 2026-09-03 — Question domain model
- Added an immutable `Question` built through named fields, with validation for required text,
  positive finite true values, and difficulty levels from 1 to 5.
- Defined equality by stable question ID so corrected content remains linked to earlier data.
- Added JVM tests for construction, every validation boundary, and equality semantics.

## 2026-09-04 — Guess domain model
- Chose separate `PointGuess` and `IntervalGuess` types behind a common `Guess` interface so
  fields that do not belong to an answer form cannot coexist.
- Used multiplicative interval width to keep confidence ranges consistent with log-relative
  point scoring and the positive question-value domain.
- Added JVM tests for valid construction, invalid values and bounds, inclusive containment, and
  scale-independent width.

## 2026-09-04 — Point-estimate scoring decision
- Recorded the choice of log-relative error as the scale-free, multiplicatively symmetric
  metric for point estimates.
- Added independently calculated comparison values for absolute, percentage, log-relative,
  and ratio error to supply expected values for the scoring tests.

## 2026-09-04 — Point-estimate scoring implementation
- Added a scoring-policy interface and immutable result type that preserve precise raw error while
  exposing whole-number points for the user interface.
- Implemented log-relative scoring and an exponential points mapping where points equal 100 divided
  by the multiplicative error factor, rounded to the nearest whole point.
- Added JVM tests for the reference examples, exact answers, factor symmetry, factor-of-ten error,
  points mapping, result invariants, and unsupported guess types.
- Recorded the points-mapping decision and its trade-offs in ADR 0005.
