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

## 2026-09-07 - Numerical robustness improvement
- Changed score calculation to be more robust, e.g. for tiny true values.
- Amended ADR accordingly.

## 2026-09-07 — Correctness-band decision
- Added ADR 0006 to reconcile continuous estimation scores with the specification's requirement
  to repeat wrong answers.
- Chose provisional global factor thresholds for `CORRECT`, `CLOSE`, and `WRONG`, while recording
  the case for later empirical calibration.

## 2026-09-07 — Correctness-band implementation
- Added a plain-Java classifier for the three correctness bands defined in ADR 0006, with named
  default thresholds and constructor injection for later tuning.
- Added JVM tests for each band, both inclusive boundaries, exact and very large errors, injected
  thresholds, and invalid error values.

## 2026-09-08 — Interval-scoring decision
- Added ADR 0007 describing a proper interval score on log-transformed values for 90% confidence
  ranges, while retaining empirical coverage as a separate calibration statistic.
- Recorded the unbounded-loss and centrality objections, the later CRPS extension, and the primary
  references.

## 2026-09-08 — Interval-scoring implementation
- Added the log interval scoring policy with a default or constructor-injected nominal confidence
  level, logarithmic width cost, and proportional penalties for misses on either side.
- Kept interval loss raw, as required by ADR 0007, by allowing scoring results to omit a deferred
  user-facing points mapping without changing existing point-score callers.
- Added JVM tests for gameability, balanced width and miss costs, inclusive bounds, multiplicative
  symmetry, degenerate and invalid intervals, confidence injection, extreme values, and policy
  boundary errors.

## 2026-09-09 — Interval-scoring edge cases
- Added JVM tests for extremely narrow and zero-width intervals, rejected zero and non-finite
  inputs, and arithmetic near `Double.MAX_VALUE`.
- Verified that the model rejects values outside the logarithmic domain and that valid extreme
  inputs cannot leak `NaN` or infinity into a score.

## 2026-09-09 — Calibration tracking
- Added a pure-Java accumulator that keeps empirical coverage, the explicitly signed
  nominal-minus-empirical coverage gap, sample size, and mean logarithmic interval width separate
  from the proper per-answer interval score.
- Used 50 interval answers as a documented interpretation threshold: at 90% nominal coverage this
  gives five expected misses, while remaining an honest rule of thumb rather than a precision
  guarantee.
- Added JVM tests for obvious hit sequences, wide but uninformative intervals, empty and one-answer
  samples, the interpretation threshold, and malformed widths.

## 2026-09-09 — Training strategy
- Chose an in-session repeat delay of two intervening questions for answers classified as `WRONG`,
  retaining the no-immediate-repeat rule at short-session boundaries.
- Added a deterministic pure-Java scheduler that receives its question pool and `Random` source,
  limits the initial schedule to available distinct questions, and exposes explicit completion.
- Added JVM tests for session length, repeat position, adjacency, undersized and empty pools,
  correctness-band behaviour, deterministic ordering, and lifecycle misuse.

## 2026-09-10 — Curriculum levels, session results, and high scores
- Defined point estimation and 90 percent confidence intervals as two permanent curriculum stages,
  keeping authored question difficulty independent and deferring CRPS until a separate decision.
- Added the approved advancement rule: at least ten point-estimate answers averaging at least 70
  points unlock confidence intervals, with no later level loss.
- Aggregated variable-length sessions with arithmetic means and an explicit answer count; empty
  sessions have absent averages rather than an artificial zero.
- Added immutable level-specific high scores that compare higher point means or lower interval
  losses, preserve an existing record on exact ties, and contain no persistence logic.
- Added JVM tests for advancement boundaries, failed advancement, permanent unlocking, mixed-score
  aggregation, zero-answer sessions, strict record updates, ties, and score direction.

## 2026-09-10 — Versioned question-bank loading
- Defined a documented version-1 JSON format with provenance metadata and an exact generated-data
  example.
- Added Gson as the single JSON dependency and kept Android asset access outside the core boundary.
- Implemented strict, whole-file validation with path-specific errors, duplicate-ID detection, and
  immutable question results.
- Added JVM tests for valid and empty banks, malformed data, schema errors, duplicate and unknown
  fields, and a 1,000-question performance sanity check.
