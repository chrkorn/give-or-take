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

## 2026-09-11 — Core milestone changelog
- Added the first changelog release entry for `v0.1-core`, summarising the complete
  Android-free, JVM-tested domain layer.

## 2026-09-11 — Question-data provenance decision
- Chose Wikidata under CC0 1.0 as the sole source for the bundled initial question bank.
- Limited the first bank to mountain elevations, completed-building heights, and explicitly dated
  national populations, with referenced statements and manual review required before release.
- Defined snapshot dating, disputed-value exclusions, and in-app and README attribution practices.

## 2026-09-11 — Reproducible question-bank generation
- Extended the pre-release version-1 metadata with a UTC generation timestamp and generator
  version, retaining strict validation in the plain-Java loader and its JUnit tests.
- Added a standard-library Python generator for referenced Wikidata statements with an identified,
  rate-limited client, retries, deterministic selection, revision-pinned source links, sanity and
  conflict filters, dry-run statistics, and a strictly validated manual override layer.
- Generated an 80-question candidate bank across mountain elevations, completed-building heights,
  and dated national populations, balanced across available orders of magnitude.
- Added offline generator tests and run/curation documentation.

## 2026-09-12 — Question-data source-admission amendment
- Corrected ADR 0011 after external review exposed six factual errors that revision-pinning and
  source-reproduction validation could not detect.
- Established Wikidata as a discovery index rather than final authority, with six admission rules,
  tiered primary-source requirements, automated conflict checks, and documented manual sampling.
- Dropped the unsupported per-question difficulty field rather than treating sitelink counts as a
  measure of estimation difficulty.

## 2026-09-12 — Explicit measurement context

- Chose version-2 question banks with fully composed, reviewable prompts plus structured
  `measurementBasis` and conditional `asOf` fields.
- Declared time-varying categories at bank level so the strict loader can require dates for those
  questions and forbid dates on time-independent quantities.
- Added generator templates that state the basis, unit, and full reference date, with checks that
  prevent manual prompt overrides from dropping that context.
- Kept the bundled-asset loader intentionally version-2-only because reader and data ship together
  and version 1 cannot provide a trustworthy measurement basis for migration.

## 2026-09-12 — Admission rules in the generator
- Four rules replace what would have been an eighty-record hand review: the exclusion list
  is honoured, subjects whose sources disagree are rejected, a familiarity floor removes
  recognition trivia, and the place name is resolved into the prompt.
- The most interesting finding was in our own code. `difficulty_from_sitelinks()` mapped
  Wikimedia sitelink count onto a 1–5 scale, so "difficulty" was literally a measure of
  obscurity — difficulty 5 meant fewer than five Wikipedia language editions. The reviewer
  inferred that defect from the outside, without seeing the source. Same signal, inverted:
  what was labelled hard is now rejected as unestimable.
- The quota was also why the bank drifted obscure. Filling sixteen slots at each of five
  difficulty levels forced the generator down its sitelink-ordered list into the tail.
- Competing-value detection reads claim documents rather than query results, because the
  SPARQL only returns referenced statements and Monte Titano's competing 739 m value
  carries no reference. That is precisely why it was invisible the first time.
- 21 offline tests. The SPARQL is unchanged, so none of this has met live Wikidata yet.

## 2026-09-12 — Remove unsupported question difficulty

- Removed the sitelink-derived `difficulty` value from the Java question model, strict bank
  loader, generator candidates and output, override schema, tests, and current format documentation.
- Advanced the strict question-bank schema and generator to version 3 because version-2 readers and
  version-3 documents are not mutually compatible.
- Deleted relative-difficulty quintile assignment without changing selection: sitelink counts still
  enforce the familiarity floor and prefer familiar subjects within each magnitude bucket.
- Verified all 120 Java unit tests with `./gradlew test` and all 32 offline generator tests with
  `python3 -m unittest tools/test_build_questions.py`.

## 2026-09-12 — Magnitude-coverage design and feasibility

- Recorded why a wide overall value range does not prevent category-to-exponent leakage and chose
  broad measurement families with overlapping base-10 magnitude bands.
- Defined an integer-based release check: substantive overlap in every well-populated band, no
  category above a two-thirds share, at least four substantive bands per large category, and at
  least 80 percent of questions covered by well-populated bands.
- Added the validator and five offline tests, and made the generator test suite part of CI. All 37
  generator tests and all 120 Java unit tests pass.
- Queried live Wikidata before implementing Areas. With a familiarity floor of 20, a genuine point
  in time, and a direct reference URL, P2046 provides islands only in `10^0`–`10^4` square
  kilometres, lakes only in `10^0`–`10^2`, no direct-instance national parks, and a handful of
  country statements with conflicting inclusion scopes. This is an upper bound before the existing
  competing-value and manual source-admission rules. The approved `10^0`–`10^7` dated category is
  therefore not implementable from the proposed pool without either changing date semantics or
  allowing per-question volatility.
- Repeating the same live query without requiring a point-in-time qualifier produced directly
  referenced, familiar candidates across `10^0`–`10^7`, including all four proposed subject types.
  This supports per-question volatility as a viable correction while confirming that a retrieval
  date must not be presented as the date on which an undated measurement was true.

## 2026-09-12 — Referenced Areas generator

- Added Areas as a broad P2046 measurement family covering islands, lakes, national parks, and
  strictly scoped sovereign states. Prompts carry the fixed square-kilometre unit, the applicable
  area basis, the direct reference hostname, and a genuine date where the scope can change.
- Split the four subject types into separate Wikidata queries after the combined query exceeded the
  public endpoint's planning limits. No dependency was added.
- A live area-only run left 93 candidates after the familiarity and competing-value gates. The
  balanced 30-question selection spans five orders of magnitude (`10^0`–`10^4`, distributed
  7/7/7/7/2).
- Corrected the competing-value gate to compare like with like. Historical population figures no
  longer compete with the selected date, and area claims must match the selected date and scope;
  same-context disagreements still fail. The corrected live population run supplies all 30
  requested questions across `10^4`–`10^9`.
- Combining the live distributions shows that Areas alone does not make the release bank pass ADR
  0013. The edge bands remain single-category and populations dominate `10^4`; enabling the bank
  gate therefore awaits a content decision about another broad measurement family or removal of
  legacy categories, rather than weakening the accepted thresholds.
- The remaining gap was accepted without adding Lengths or Masses. Regenerated the shipped bank as
  version 4 with 80 questions: 10 mountains, 10 buildings, 30 populations, and 30 areas.
- Corrected ADR 0013 rule 5 so its four-band requirement applies only to categories whose configured
  admissible range can occupy four bands. The old question-count-only condition became impossible
  for mountains and buildings as soon as either quota reached twelve.
- Added a CI ratchet that always prints the full matrix and fails only if the achieved outcomes
  regress. The report-ready before/after tables record the improvement from 46/64 testable questions
  and zero compliant bands to 74/80 and two compliant bands, together with every unclosed gap.
- All 53 offline tooling tests and all 120 Java unit tests pass.

## 2026-09-15 — View-access proposal

- Wrote ADR 0015 comparing `findViewById` with ViewBinding for Java activities and XML layouts.
- Recommended the course-taught `findViewById` approach because direct evidence for the Transfer
  criterion outweighs ViewBinding's compile-time safety and reduced boilerplate in this project.

## 2026-09-15 — Home screen and explicit Activity navigation

- Replaced the generated placeholder with the wireframed home screen using ConstraintLayout,
  Material cards and buttons, resource-backed text, dimensions, and light/dark colour palettes.
- Kept the initial level and personal-best display honest for the pre-persistence state: level 1
  point estimates and an em dash until a completed session supplies a score.
- Added empty quiz, feedback, result, statistics, and settings Activity stubs and registered the
  internal destinations as non-exported in the manifest.
- Wired the home actions with `findViewById` listeners and explicit Intents so the destination
  component is visible in the Java code used as evidence for the course's Transfer criterion.
- Verified the debug APK build, all JVM unit tests, and Android lint with the bundled Android
  Studio Java runtime.

## 2026-09-16 — Point-estimate quiz fallback screen

- Implemented the first Java/XML quiz screen with a question counter, wrapping prompt, unit-labelled
  Material input, and Submit button, retaining `findViewById` as decided in ADR 0015.
- Added immediate validation for empty, malformed, zero, negative, and implausibly large values;
  validation errors use the input layout and Submit remains disabled until the value is valid.
- Used a resizing window and scroll container so long prompts and the Submit button remain reachable
  above the soft keyboard on small screens.
- Kept submission deliberately limited to a log statement until the scoring/session increment.
- Added JVM boundary tests for input validation and Espresso coverage for control state, the input
  error, and a 200-character wrapping prompt.

## 2026-09-17 — Core-backed quiz sessions and Activity restoration

- Recorded ADR 0016: active quiz attempts use an explicit plain-Java snapshot saved as primitive
  Activity instance state, preserving rotation and system-initiated process recreation without
  introducing Android types into `core`.
- Added a core `QuizSession` boundary that atomically scores a `Guess`, classifies correctness,
  updates delayed-repeat scheduling, accumulates scores, and selects the next question. The
  Activity performs no scoring or scheduling arithmetic.
- Moved the generated version-4 bank into `app/src/main/assets/questions.json`, updated generator
  defaults and documentation, and added a build-time test of the exact packaged data.
- Added an Android asset loader with caller-owned stream closure and instrumented fail-fast tests
  for a missing or malformed asset; an empty bundled bank also fails as a programming error.
- Wired `QuizActivity` to load or restore a ten-question point-estimate session, submit
  `PointGuess` values, and display the chosen dynamic progress form: answered guesses and currently
  remaining scheduled questions.
- Added JUnit coverage for orchestration, wrong-answer repeats, snapshot round-trips, incompatible
  question IDs, and progress counts. Expanded Espresso coverage for submission and Activity
  recreation; the Android-test APK compiles, but no emulator or device was connected to execute it.
- Verified all 140 JVM tests, all 53 offline tooling tests, Android lint, the debug APK, and Android
  test compilation.

## 2026-09-18 — Directed point-estimate feedback

- Compared factor, percentage, raw-error, and visual-scale explanations before selecting directed
  factor wording: feedback now says that an estimate was approximately a given factor too high or
  too low, preserving information intentionally discarded by the absolute scoring metric.
- Implemented the XML-based `FeedbackActivity` with the question, estimate, true value, score,
  correctness label and threshold explanation, source link, and Next action. Band colours have
  light and dark variants, while visible labels and descriptions keep the result understandable
  without colour perception.
- Passed immutable primitives and strings through an explicit Intent factory. The quiz advances
  before opening feedback and waits for that Activity to finish, so both Next and system Back
  reveal the next scheduled question rather than the answered one.
- Opened sources with an `ACTION_VIEW` implicit Intent and show an in-app message when no handler is
  installed. No dependency was added.
- Added pure-Java tests for directed multiplicative comparison and Espresso coverage for feedback
  rendering, recreation, and Back navigation. Verified the JVM suite, debug APK, Android-test
  compilation, and Android lint.

## 2026-09-19 — Completed-session result flow

- Recorded ADR 0017 and implemented the selected primitive-extra result contract with explicit
  format versioning and fail-fast validation; no dependency was added.
- Extended the framework-independent session aggregate with correctness-band counts and immutable
  calibration summary values sourced from `CalibrationTracker`'s meaningful-sample rule.
- Implemented the XML/Material result screen with the ADR 0009 mean session score, multiplicative
  average closeness, plural-aware band counts, level-specific personal-best treatment, an honest
  small-sample calibration message, and a disabled Share placeholder.
- Added a `SharedPreferences` data wrapper for level-specific high scores and refreshed the Home
  personal-best card when returning from a session.
- Finished the completed quiz below the result, made Play again replace the result with a fresh
  quiz, and used `CLEAR_TOP | SINGLE_TOP` for Home so completed session screens cannot be re-entered.
- Added JUnit coverage for result aggregation and calibration counts plus Espresso coverage for
  result recreation, interval sufficiency, disabled sharing, Play again, the complete-session Back
  path, and SharedPreferences round-trips.
- Verified all 145 JVM tests, the debug APK, Android-test compilation, and Android lint. 

## 2026-09-20 — Uncertainty-factor dial skeleton

- Added a Java/XML custom `View` skeleton for the single-thumb uncertainty-factor dial, with
  theme-aware custom attributes, explicit measurement behaviour, reusable drawing objects, labelled
  reference ticks, right-to-left rendering, and a dedicated Android Studio preview layout.
- Kept the factor-to-position conversion in the framework-independent `core` package. Its
  logarithmic mapping gives equal screen distance to equal ratios, such as ×2–×4 and ×4–×8.
- Added JUnit coverage for endpoints, multiplicative spacing, round trips, and invalid inputs. No
  dependency was added.
- Verified all 149 JVM tests, the debug APK build, and Android lint.

## 2026-09-20 — Uncertainty-factor dial interaction and accessibility

- Added continuous track taps and thumb dragging with a 48 dp minimum hit band, endpoint clamping,
  parent-scroll interception protection, and redraw-only updates during movement. Pointer release
  remains unsnapped, preserving ADR 0014's decision that labelled factors are references rather
  than detents.
- Added factor-change callbacks for the Activity's live derived-range preview. The Activity can
  return that exact formatted range to the dial so its dynamic content description uses the same
  units and rounding as the visible answer.
- Made the dial one adjustable accessibility range with D-pad, keyboard, and standard
  accessibility forward/backward actions between labelled reference factors. Completed touch
  gestures delegate to `performClick()` so click listeners and accessibility services receive the
  semantic action.
- Changed the pure-Java position-to-factor mapping to clamp positions outside the track and added
  JVM coverage for both exact endpoints and movement past each end. No dependency was added.
- Verified all 150 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.

## 2026-09-21 — Confidence-range input and state restoration

- Added a pure-Java `IntervalGuess` factory for deriving log-symmetric bounds from a best guess and
  uncertainty factor, including explicit overflow and underflow rejection and matching JUnit tests.
- Added the live range read-out and factor explanation to the confidence-interval answer mode. The
  Activity owns locale-aware display formatting, while the core model owns interval arithmetic.
- Implemented the asymmetric-belief escape hatch as an inline swap to two bound fields. Both paths
  produce the same `IntervalGuess` type, and the dial path pre-fills the direct fields without
  recursive listener updates.
- Implemented the custom dial's `BaseSavedState` parcel for its selected factor and saved the
  Activity's dial-versus-direct entry mode separately. Every stateful View has a stable XML ID.
- Added Espresso coverage for live derivation, representation equivalence, dial restoration, and
  direct-mode restoration. 
- Verified all JVM tests, Android lint, the debug APK build, and Android-test APK compilation. No
  dependency was added.

## 2026-09-21 — Wire point and interval quiz modes end to end

- Recorded ADR 0018: interval containment is the discrete remedial-repeat signal, while the proper
  log interval loss remains the separate performance measure.
- Made the selected curriculum `Level` the explicit quiz-mode input. Policy selection remains one
  polymorphic choice between `LogRelativeScore` and `IntervalScore`; no scoring arithmetic moved
  into an Activity.
- Extended the pure-Java session boundary to score interval guesses, record calibration outcomes,
  schedule missed ranges for delayed repetition, aggregate the correct mode-specific result, and
  preserve interval scores and calibration aggregates through Activity recreation.
- Kept point and interval controls as explicit XML groups swapped by visibility. Interval
  submissions now use the same feedback-and-resume flow as point estimates, with range-specific
  containment, direction, and raw-loss wording.
- Preserved the selected level when replaying from the result screen and added JUnit4 and Espresso
  coverage for interval scheduling, restoration, feedback, navigation, and mode retention.
- Verified the JVM suite, debug APK, Android-test APK compilation, and Android lint. No dependency
  was added.

## 2026-09-22 — Persist raw quiz history with SQLiteOpenHelper

- Recorded ADR 0019: explicit session rows own level and lifecycle state, while answer rows retain
  raw point or interval inputs plus the scoring-time truth value so future policies can rescore
  history without stale derived columns.
- Added an Android-style schema contract and version-1 `SQLiteOpenHelper` with enabled foreign
  keys, shape and lifecycle constraints, focused indices, and a sequential fail-fast migration
  pattern that never drops user history.
- Added a typed DAO that keeps SQL and cursor ownership out of Activities, returns immutable stored
  records, closes every cursor with try-with-resources, and uses transactions for batches and the
  final-answer-plus-completion boundary.
- Kept the existing `SharedPreferences` high score as a derived cache and wired `QuizActivity` to
  start or restore a history session, persist each accepted answer, complete the final answer
  atomically, and mark an explicitly finished incomplete quiz as abandoned.
- Added the approved Robolectric test-only dependency and JVM coverage for point and interval round
  trips, mode validation, foreign keys, completed-session queries, and transaction rollback. Added
  a small instrumented Android SQLite smoke test without requiring it in emulator-free CI.
- Verified all 166 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.

## 2026-09-22 — Move quiz-history I/O off the main thread

- Recorded ADR 0020 after comparing deprecated `AsyncTask`, `HandlerThread`, raw threads,
  `ExecutorService`, WorkManager, and services. Chose one application-scoped single-thread executor
  because it gives Java-native FIFO ordering without another dependency.
- Added lifecycle-independent session handles that queue creation, answers, atomic final completion,
  and abandonment without retaining an Activity or View. Activity recreation reattaches by a saved
  process token, and process-style restoration can recover the row when state was saved before the
  asynchronous insert returned its ID.
- Moved DAO ownership from `QuizActivity` to the application process, so `onDestroy()` cannot close
  the database beneath an in-flight write. Kept ADR 0019's raw-input schema and final-answer
  transaction unchanged.
- Enabled debug-only `StrictMode` detection for main-thread disk reads and writes with logged
  violations; release builds do not install the policy.
- Added Robolectric coverage for serial write order, final session state, in-process reattachment,
  and restoration without a previously saved database ID. No dependency was added.
- Verified all 169 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.
