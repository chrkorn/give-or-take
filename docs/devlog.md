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

## 2026-09-23 — Query and derive history statistics

- Recorded ADR 0021 after comparing SQLite aggregation with reuse of the pure-Java policies.
  SQLite now owns filtering, stable ordering, and bound-parameter paging, while the data layer
  applies the already-tested scoring, calibration, session-result, and personal-best logic.
- Added immutable result types for recent session pages, overall calibration, rolling ten-answer
  coverage, per-session mean log-relative error, category performance, and mode-specific records.
  Empty samples use optional values and no cursor leaves the data layer.
- Migrated history to schema version 2 with a nullable answer-time category snapshot. New answers
  preserve their category; unrecoverable version-1 rows are reported under `Uncategorised` instead
  of being silently reclassified through the current question bank.
- Included completed and explicitly abandoned answers in learning statistics, excluded in-progress
  sessions, and limited record-setting to completed non-empty sessions.
- Added Robolectric coverage for empty results, newest-first paging and tie-breaking, in-progress
  exclusion, mixed-mode aggregates, category separation, personal bests, and overlapping rolling
  windows. No dependency was added.
- Verified all 176 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.

## 2026-09-24 — Statistics screen

- Implemented the XML-and-Java `StatsActivity` with explicit loading, useful empty, error, and
  populated states. A single `RecyclerView` holds a summary header and reusable newest-first
  session rows so headline figures, calibration, the chart placeholder, and history scroll
  together.
- Kept SQLite reads on a named background executor and converted the stored mean logarithmic
  values into locale-formatted multiplicative factors only at the presentation boundary. Added
  the answer-weighted point-estimate mean needed by the headline without moving scoring policy
  into the Activity.
- Added the fixed “Confidently Wrong” panel with neutral wording for under-, well-, and
  overconfidence. Empirical coverage is completely withheld below the core tracker’s 50-answer
  threshold; the UI explains the sampling reason and shows finite progress, while mean interval
  width remains available.
- Added locale-aware dates, scores, factors, percentages, pluralised counts, interval contained
  and missed counts, an actionable first-use explanation, and the calibration-chart placeholder.
- Added JUnit coverage for factor conversion and verdict boundaries, extended DAO coverage for the
  headline aggregate, and added Espresso cases for empty, insufficient, and 55 percent coverage
  states. No dependency was added.
- Verified all 179 JVM tests, Android lint, and Android-test APK compilation.

## 2026-09-25 — Calibration chart

- Replaced the statistics placeholder with a hand-drawn Java `View` that plots rolling interval
  coverage on a fixed zero-to-one scale against a visually prominent dashed 90 percent target.
- Kept `Paint` and `Path` allocation out of drawing, respected view padding and measure contracts,
  and resolved line, text, and grid colours from the active Material theme for dark-mode support.
- Added session labels, grid and axis labels, a legend, point markers, a useful insufficient-data
  state, and a TalkBack description that states the direction and latest relationship to target.
- Connected the view to the existing immutable coverage trend without adding a chart dependency.
- Verified all 185 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.

## 2026-09-25 — Session settings and statistics reset

- Recorded ADR 0022 and implemented the settings screen as a real XML-and-Java Activity with the
  already-present Material controls rather than adding AndroidX Preference.
- Added one `SharedPreferences` wrapper that owns keys, validation, and first-run defaults: ten
  questions, follow the current level, and every dynamically loaded question-bank category.
- Populated category checkboxes from the validated bank, persisted multi-select changes, and
  refused deselection of the final category so a quiz always has an eligible question pool.
- Copied a complete settings snapshot into each new quiz Intent. Session length, resolved answer
  mode, and category filter therefore stay fixed through later edits and Activity recreation.
- Added a two-step statistics reset that reports exact session and answer counts, serializes the
  SQLite deletion behind pending history writes, and clears personal-best preferences only after
  the database transaction succeeds.
- Added JUnit/Robolectric coverage for defaults, round-trips, stale-category recovery, and ordered
  reset, plus Espresso coverage for dynamic defaults, persistence, last-category protection,
  confirmation, and configured quiz filtering. No dependency was added.
- Verified all 190 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.

## 2026-09-27 — Share results and open question sources

- Enabled the result screen's system share action with an `ACTION_SEND` `text/plain` Intent wrapped
  in `Intent.createChooser`. The concise text contains the session score and answer count; interval
  sessions also include containment counts and, once statistically meaningful, observed coverage.
- Kept the feedback screen's source URL as an `ACTION_VIEW` Intent and verified that the exact
  authored URL is delegated to Android rather than tied to a particular browser.
- Both implicit launches now use direct launch plus `ActivityNotFoundException` handling. This
  remains reliable from minSdk 26 onward without broad `<queries>` declarations, unlike treating
  `resolveActivity()` as an authoritative availability check on Android 11 and later.
- Added Robolectric coverage for both Intent contracts, chooser wrapping, share copy, honest small-
  sample calibration, and the no-handler share path. The existing Espresso no-browser test covers
  the corresponding feedback fallback.
- Verified all 195 JVM tests, Android lint, the debug APK build, and Android-test APK compilation.
  On an API 35 emulator, all feedback tests and the changed share-button assertion passed.
- **Correction (2026-09-27).** The entry above originally stopped there, which reads as though the
  instrumented suite were green. It was not: the full run was **32 of 34**, with two failures that
  this entry claimed were "documented in the devlog" when they were not documented anywhere. Both
  are recorded now, before either is fixed:
  - `ResultActivityTest.playAgain_afterIntervalResult_startsIntervalQuiz` — `range_answer_group`
    was `GONE`. A product defect: `ResultActivity.configureNavigation()` passed a hard-coded
    `Level.POINT_ESTIMATES` to `createConfiguredIntent`, so replaying after an interval session
    dropped back to point estimates whenever the answer mode is "follow my level". This directly
    contradicts the 2026-09-21 claim that the level is preserved when replaying.
  - `StatsActivityTest.smallIntervalSample_suppressesCoverageAndExplainsThreshold` —
    `NoMatchingViewException` on `stats_session_date`. A test defect: the assertion reaches into a
    `RecyclerView` row while `awaitStatistics` waits only for the aggregates, not for the adapter
    to bind. The same asynchronous attachment was flagged during the lifecycle review.
- Recording this before fixing it, because a green-looking entry that was never green is worse than
  a red one. Instrumented tests are excluded from CI by choice; the cost of that choice is that the
  written record is the only place a failure is visible, so the written record has to be accurate.
  

## 2026-09-27 — Replay keeps the answer mode

- `ResultActivity` passed a hard-coded `Level.POINT_ESTIMATES` into
  `QuizActivity.createConfiguredIntent()`. That argument is the level `QuizSettings.resolveLevel()`
  resolves against, so with the answer mode set to "follow my level" — the default — replaying
  after a confidence-interval session silently started a point-estimate session.
- It now passes the level of the session just completed, which `onCreate` already reads from the
  result Intent for the score, high-score and share rendering.
- Caught by `ResultActivityTest.playAgain_afterIntervalResult_startsIntervalQuiz`, one of the two
  failures in the 32-of-34 run. The test was written weeks ago and had never executed, because the
  instrumented harness could not start; it was correct all along. Worth remembering that a test
  which has never run is not evidence, and that this one earned its place the first time it did.
- The 2026-09-21 entry claimed the level was preserved when replaying. It was not. Corrected here
  rather than by editing that entry, so the sequence stays visible.

## 2026-09-27 — Stop the statistics test racing its own list

- `StatsActivityTest.smallIntervalSample_suppressesCoverageAndExplainsThreshold` asserted on
  `stats_session_date`, a view inside a `RecyclerView` row, and failed with
  `NoMatchingViewException`. The three assertions before it passed, so the screen had loaded; the
  list simply had not bound a row yet.
- `awaitLoadForTest()` returns when the query finishes, but the adapter is attached on the main
  thread and children are bound on a later layout pass. `waitForIdleSync()` does not reliably
  span that gap. The lifecycle review had already flagged that the adapter attaches
  asynchronously and that a test would need to account for it.
- **First attempt was wrong twice over, and instructively so.** Waiting for a non-zero child count
  inside `awaitStatistics` broke `noHistory_displaysHelpfulEmptyState`, because the empty-state
  screen hides the list entirely — `showOnly(R.id.stats_empty_state)` leaves the `RecyclerView`
  `GONE` with no children, so the wait could never be satisfied. It also failed to fix the
  original test, because the overview header is adapter item 0 and satisfies "at least one child"
  before any session row has been bound.
- Waiting for the view itself rather than a proxy fixed the empty-state regression but not the
  original failure, which then failed as an explicit timeout instead of `NoMatchingViewException`
  — the same fact, better reported.
- **It was never a timing problem.** A `RecyclerView` only lays out the children needed to fill the
  viewport. The overview header is item 0 and is taller than the screen, so the first session row
  is never created at all until the list is scrolled to it. No amount of waiting produces a view
  that the layout has decided not to build.
- The helper now scrolls to position 1 and then waits. It scrolls through the Activity rather than
  `RecyclerViewActions`, which lives in espresso-contrib and would mean a new dependency for one
  assertion. Its failure message now reports the adapter item count and the laid-out child count,
  so the next failure of this kind arrives with evidence instead of requiring another hypothesis.
- Three wrong attempts, each wrong in the same way: asserting on a stand-in for the condition
  rather than the condition. Child count is not "the row exists"; "the row exists" is not "the row
  has been laid out".
- Considered an `IdlingResource` and rejected it: it reports work the application knows it is
  doing, and the application does not know it is waiting for a layout pass. There would be
  nothing for it to mark busy. Polling the condition that actually matters is the more honest
  instrument here, even though it is the less idiomatic one.

## 2026-09-27 — Record the Robolectric decision

- Robolectric was approved and added on 2026-09-22 with the SQLite history work, and is now used
  in seven test classes. It had no ADR, while smaller dependency decisions did — ADR 0022 records
  *rejecting* AndroidX Preference, yet the library that carries most of the Android-dependent
  coverage was undocumented.
- Written up as ADR 0023, including the part that matters for the report: Robolectric runs inside
  the existing CI job, so it is the only reason six weeks of a broken instrumented harness did not
  leave every Android-dependent class unverified.
- Also recorded the hazard. Robolectric is a simulation, and because its tests are fast and always
  run there is a standing temptation to migrate assertions there to keep CI green. The division is
  drawn by what is being claimed — anything asserting what a user can see or touch stays on a
  device — not by which is more convenient.

## 2026-09-27 — The instrumented suite runs, and passes

- 34 of 34 instrumented tests green on a Pixel_9 AVD at API 35. This is the first complete run of
  the instrumented suite in the project's history; before today the harness had never started.
- What it took, in the order the failures surfaced: `adb` was not on `PATH`, so the runner learned
  to locate the SDK from `ANDROID_HOME`, then `sdk.dir` in `local.properties`, then the default
  install location. The Gradle invocation then failed with `Unable to locate a Java Runtime`,
  because no JDK is on the shell `PATH` on this machine — the runner now prefers Android Studio's
  bundled JBR and falls back to `JAVA_HOME` and `/usr/libexec/java_home`. Finally the only
  emulator image installed was API 37, on which the Espresso version in the version catalogue
  does not initialise; creating a Pixel_9 AVD at API 35 was the fix, and the runner now refuses to
  proceed above API 35 unless `ALLOW_UNSUPPORTED_API=1` is set, checking the *connected* device's
  API level rather than trusting the AVD name.
- The three failures the suite then reported were all real: the replay level bug, and the two
  statistics assertions racing the `RecyclerView`. Both are written up above. It is worth stating
  plainly that a harness which cannot start hides defects rather than preventing them — these
  tests had been in the repository for weeks, were correct, and found genuine bugs the first
  minute they were allowed to execute.
- The instrumented suite stays out of CI, as decided earlier: there is no emulator on the runner
  and the tests are slow enough to discourage the small commits this project is trying to
  demonstrate. The cost is that it runs when someone runs it, and the devlog is the only record
  that it did. `tools/run_instrumented_tests.sh` exists so that "someone runs it" is one command
  rather than an afternoon.

## 2026-09-27 — Closing out the lifecycle review

- The lifecycle review left five findings. Three are fixed and two are accepted; the accepted
  pair is written up as ADR 0024 rather than left in a review note nobody reads again.
- The visible defect was the statistics reset. The confirmation dialog was a local variable, so
  rotation lost it and leaked its window, and a count query in flight had its callback dropped on
  the destroyed Activity — tap Reset, rotate, and the screen sat there with nothing ever
  happening. A `ResetPhase` enum and the two row counts now go into the saved instance state and
  the flow is rebuilt from them.
- The re-issue strategy is worth stating because it is not obviously safe. A dropped count query
  is re-issued because counting rows changes nothing. A delete that was running is also
  re-issued, because `clearHistory` deletes unconditionally inside one transaction and a second
  pass over an empty table is a no-op — re-issuing it is the only way the rebuilt dialog gets a
  completion callback, the original having gone to an Activity that no longer exists. If the
  delete had been conditional or incremental this would have been the wrong move.
- The database callbacks now read the dialog from a field instead of capturing it. A captured
  dialog keeps the destroyed Activity's window alive until the worker thread gets round to the
  callback, and after a configuration change it is the wrong dialog anyway. The `NestedScrollView`
  also finally has an id, without which the framework cannot save its scroll position.
- `SettingsActivityTest.resetConfirmation_survivesRecreation` covers it. **This test has not been
  executed yet** — it needs the emulator, and the instrumented suite runs by hand. Recording that
  here because a test written today and run tomorrow is not evidence today, and this project has
  already been caught once believing otherwise.

## 2026-09-27 — Both suites green after the reset fix

- 195 JVM tests and 35 instrumented tests, no failures, no errors, no skips. Instrumented run on
  the Pixel_9 AVD at API 35.
- `SettingsActivityTest.resetConfirmation_survivesRecreation` passed on its first execution, in
  1.03 s. The entry above said the test had been written but not run; it has now run, and the
  claim that the confirmation survives rotation is evidence rather than an intention.
- The instrumented count went from 34 to 35 — the whole of the increase is that one test. Worth
  noting that the suite took two runs of this project to become useful: the first, three days
  ago, existed only to discover that it could not start.

## 2026-09-28 — Material pass
- Consistent theme, type scale and spacing system. Largest-font-size setting broke two layouts; fixed.
- Checked against the Android app quality guidelines; remaining gaps recorded as known limitations rather than quietly ignored.

## 2026-09-28 — Prepare the feature-complete release evidence

- Added the `v0.9-feature-complete` changelog entry covering the seven user-facing screens, both
  answer modes, uncertainty dial, persistence, statistics, settings, implicit-Intent actions, and
  Material day/night presentation.
- Wrote a verification procedure translating every acceptance criterion into a reproducible
  action, an observable pass condition and evidence to retain — then kept it out of this
  repository. Only its executed results will be recorded here and in the report.
- The reason is that it is a plan, not a record. Every item is unperformed. Committing an
  empty checklist would publish an inventory of what has not been done, which is the opposite of
  what the rest of `docs/` is for. It gets executed during self-testing, and the outcome —
  failures included, recorded before they are fixed — is what earns a place here.
- Kept subjective usability separate from implementation evidence: the procedure asks for an
  uncoached first-time-user task trial and preserves the known Android quality-audit gaps rather
  than turning a Material review into an unsupported claim of complete usability or compliance.
  One uncoached tester is n = 1, and the record says so.


## 2026-09-28 — Make the complete UI journey deterministic

- Recorded ADR 0025 for test dataset decisions.
  The core already accepted Random; the missing seam was in QuizActivity, which constructed it
  and loaded the bank itself. GiveOrTakeApplication now supplies those two inputs through a
  package-private provider. Production still loads the bundled bank and uses unseeded Random.
- Added CompleteSessionTest with ActivityScenarioRule<MainActivity>, wrapped by a fixture rule
  that installs inputs before launch and restores the provider, preferences and numeric locale
  after shutdown, including assertion failures. The five synthetic questions live in androidTest
  assets. No new dependencies or changes to the Android-free core were needed.
- The journey presses Start, types five estimates with closeSoftKeyboard, verifies each prompt,
  progress count, estimate, independent true value and feedback, then checks the final summary
  and returns Home. Every estimate is twice the truth: CLOSE and 50 points, without requeues.
  This detects confusing the submitted estimate with the truth and ends at 50 / 100, zero
  correct, five close and zero wrong. Expectations are explicit, not derived with the scorer.
- Verification was performed on the working tree based on
  `bc508deef00804053ed3f3a2eadc783101c18ed5`, with this change uncommitted. Host launcher was
  Android Studio's bundled OpenJDK 25.0.3. The existing JVM suite passed all 195 tests, with no
  failures, errors or skips. An initial instrumentation compilation error used `fromReader`
  instead of the existing `QuestionBank.fromJson(Reader)` overload; corrected before device runs.
- First device suite: 36 tests, one failure in the new test. It expected `minutes` where the UI
  displays `Unit: minutes`. The other 35 tests passed. Corrected that assertion and strengthened
  the initial exact guesses into different estimate/truth pairs.
- Second device suite: 36 tests, one failure in the new test. All first-question feedback content
  checks passed, but scrollTo on the fixed Next footer violated Espresso's scroll-container
  constraint. Removed scrolling for fixed footer actions (Next and Result's Home).
- Final device suite: **36 instrumented tests passed**, no failures, errors or skips, using
  `tools/run_instrumented_tests.sh Pixel_9`. The runner canary passed separately. Actual device:
  Pixel_9 AVD, Android 15/API 35, animations disabled by the helper; the new fixture uses Locale.US
  for number formatting and restores the prior locale afterward. Final suite Gradle invocation
  completed in 33 seconds; the new test took **9.262 seconds**. JUnit XML timestamp:
  `2026-09-28T07:14:44` (UTC; 09:14:44 Europe/Berlin).
- Added `docs/complete-session-test.md` with local execution/evidence instructions and the
  synchronisation explanation. Main-queue idleness does not imply the history executor is done;
  these feedback/result assertions do not depend on it, so no IdlingResource or Thread.sleep
  is used. This test makes no persistence-durability claim. Use a dedicated AVD because real
  history writes may retain synthetic sessions. Instrumented execution remains outside CI under
  the existing no-emulator policy; that is a project constraint, not a universal Espresso rule.

## 2026-09-28 — Exercise wrong-answer repetition through the real UI

- Added `wrongAnswer_isPresentedAgainLaterInSession` to the deterministic Espresso journey. It
  enters 20,000 for a true value of 20, verifies that the real scoring path labels the answer
  `WRONG`, answers the next two seeded questions correctly, and asserts that the original prompt
  is then presented again. A test comment identifies the examination criterion it covers.
- Kept the scenario on the five-question fixture and `Random(42L)` construction boundary from
  ADR 0025. The test therefore exercises the real `Activity`/explicit-`Intent` flow, scorer and
  training strategy without adding a test-only scheduling implementation or dependency.
- Updated the complete-session test notes to distinguish the fixed-length all-`CLOSE` journey
  from this deliberately extended remedial-repeat journey. The JVM scheduling tests remain the
  cheaper coverage for queue boundary cases.
- Verification used Android Studio's bundled OpenJDK 25.0.3. A forced
  fresh run passed all **195 JVM tests**. `tools/run_instrumented_tests.sh Pixel_9` passed its
  runner canary and all **37 instrumented tests** on the Pixel_9 AVD, Android 15/API 35, with no
  failures, errors or skips. The new test took **5.648 seconds**.

## 2026-09-29 — Audit production Javadoc for examination submission

- Reviewed every production Java file across the core, data, Activity, and custom-view layers.
  Added concise architectural roles, cross-links between related types, and source paths for the
  ADRs each implementation realises. Public API documentation remains intentionally brief for
  self-explanatory getters and inherited Android lifecycle methods.
- Corrected stale contracts: `IntervalGuess` is not inherently tied to a 90 percent confidence
  level, `QuizSession.submit` accepts both supported guess forms, `QuizSession.getResult` returns
  either level's result, and a matching history-session token is reused rather than rejected.
- Retained explanatory comments where they record numerical stability, scoring-policy separation,
  transaction ordering, lifecycle recovery, accessibility, or drawing-performance reasons.
  Removed comments that merely narrated empty text-watcher callbacks or a private utility-class
  constructor.
- Added the missing documentation for the locale-aware numeric-input validator. Left two
  presentation tolerances explicitly un-rationalised rather than inventing evidence: the five
  percentage-point calibration verdict band and the two-point chart trend-stability band need a
  product or report justification if they are to be defended as more than provisional display
  rules.
- Strict Javadoc validation passed for the complete Android-free `core` package with no warnings.
  The full `testDebugUnitTest` task also passed after recompiling the production sources.

## 2026-09-30 — Self-testing

- Adversarial pass on the Medium_Phone emulator: rotation on every screen, backgrounding
  mid-answer, boundary input, dark mode, 200 % font, back-stack abuse. Seven defects found; each
  is fixed in its own commit and logged below with its root cause.
- **Crash on a tiny positive estimate.** `0.` followed by a few hundred zeros and a `1` passed
  `GuessInputValidator`, which checks the exact `BigDecimal`, but `doubleValue()` underflowed to
  `0.0` and the `PointGuess` constructor threw; the quiz Activity died and the session was lost.
  Cause: validation and construction ran on different number types. Fix: a `MINIMUM_VALUE` of
  1e-15, the log-space mirror of the existing 1e15 ceiling, with its own error message; every
  accepted value is now a normal positive `double`. Regression test builds the underflowing input
  and asserts it is rejected.
- **Statistics screen stale after playing from it.** Starting a session from the Stats screen's
  empty state and pressing Back after the result returned to "Nothing to measure yet", while Home
  already showed the new personal best. Cause: `StatsActivity` read history only in `onCreate`,
  and it stays in the back stack under the quiz. Fix: reload in `onRestart`, keeping the current
  content visible until the new read lands (no loading flash). New instrumented test stops the
  Activity, stores a session, resumes it and asserts the list replaces the empty state.
- **Fresh question opened with "Enter an estimate."** After rotating (or switching to dark mode)
  on the feedback screen, the next question's empty field was already red; in direct-bounds mode
  both fields were. A control run without a configuration change showed no error. Cause: the
  `QuizActivity` under the feedback screen is recreated, and the framework restores `EditText`
  text in `onRestoreInstanceState` — after `onCreate` attached the text watchers, which validate
  with errors shown. Fix: suppress the watchers during the restore, then re-validate once, showing
  errors only for fields that actually contain text. Two instrumented tests: untouched fields stay
  clean across `recreate()`, and a typed invalid `0` keeps its error.
- **"Your estimate was about 1× too low."** Entering 420 for a true 420.5 (and 4.2 for 4.22,
  59,000,000 for 58,850,717) produced a sentence that contradicts itself. `EstimateComparison` was
  right; the display was not: `formatFactor` keeps two fraction digits, so every factor below
  1.005 printed as `1`. Fix: below a factor of 1.01 the miss is worded as a percentage ("about
  0.12% too low"), and below 0.005 % as "within 0.01% of the true value"; ordinary misses keep the
  multiplicative wording the Espresso tests already pin ("about 2× too high"). The thresholds live
  in a small Android-free `ComparisonText` class with JVM tests, following `StatsPresentation`.
- **Statistics said "Mean closeness ×1" for a session the result screen summarised as 1.02.**
  Same class of defect as the feedback wording, on a different screen: `StatsActivity` formatted
  the factor with its shared one-fraction-digit format. Fix: format it with the result screen's
  two digits via `StatsPresentation.formatClosenessFactor`, with a JVM test pinning both screens to
  the same precision. Kept as a separate commit because it is a separate code path.
- **Raw doubles in the interval UI.** Switching from the dial to "Set the two bounds myself"
  pre-filled `2224.2888379530405` and `34815.622269303014` while the readout above said
  2,224 – 34,816, and the feedback screen showed a dial answer as `101.104038–1,582.528285`.
  Cause: `formatEditableValue` wrote the full `double`, and feedback used the six-digit format it
  applies to authored true values. Fix: one Android-free `RangeFormatting` rule (three significant
  digits, never fewer than the whole number) now drives the readout, the pre-filled fields and
  the feedback bounds. Consequence worth recording: direct entry now starts from the interval the
  player was *shown* (500 / 3 becomes 167), not the dial's exact double, so the instrumented test
  that asserted exact equality was rewritten to assert the displayed values and an unchanged
  readout. Switching modes therefore preserves the interval to display precision, not exactly.
- **"0 wrong· ended early".** The Past sessions row for an abandoned session lost the space before
  its separator. Cause: `stats_abandoned_suffix` was written as `" · ended early"`, and aapt
  strips leading whitespace from unquoted string resources. Fix: a `\u0020` escape; a Robolectric
  test reads the resource back. A grep found no other resource with meaningful edge whitespace.
