# ADR 0001 — Use Java with XML layouts
- **Status:** Accepted
- **Date:** 2026-09-02

## Context

Give or Take is the practical deliverable for IU International University course DLBCSEMSE02, Mobile Software Engineering II. The module has no separate MSE II course book and instead refers to DLBCSEMSE01-01. That book teaches Android development entirely through Java and XML layouts. Its examples use the pattern `public class MainActivity extends AppCompatActivity`, followed by `setContentView(R.layout.…)`, `findViewById`, and explicit navigation with `new Intent(…)`.

Kotlin is mentioned nine times in passing but is not used in any code example. Jetpack Compose, `ViewModel`, and Room are absent. The testing material covers JUnit and Espresso. The assessment also assigns 15% of the grade to “Transfer”: the report must identify concepts, patterns, and libraries from the course that were applied. Using the taught stack therefore makes the implementation choices directly traceable to the course material.

One acceptance criterion requires several Android activities. A design based on one activity and a Compose navigation graph would meet that wording only indirectly and would introduce avoidable examination risk.

The app’s substantial analytical work—numerical scoring, interval scoring, training decisions, and confidence calibration—resides in a plain-Java core with no Android dependency. The UI toolkit therefore does not constrain the logic that carries most of the project’s technical value.

## Decision

The project will use Java for application code and XML resources for layouts. Screens will be implemented as multiple real `AppCompatActivity` classes connected by explicit `Intent` objects. Activities will inflate layouts with `setContentView` and access views through `findViewById` or ViewBinding. Business logic in the core package will remain plain Java and must not import `android.*`. Logic tests will use JUnit 4, and UI tests will use Espresso.

## Consequences

The chosen stack aligns closely with the referenced course material and makes the Transfer argument specific and citable. It also satisfies the multiple-activity criterion directly and keeps the testable core independent of Android.

The approach requires more boilerplate than Kotlin or Compose. UI code relies on `findViewById` or ViewBinding rather than a declarative model, and the developer must handle screen state and configuration changes manually. XML and activity lifecycle code create more opportunities for duplicated wiring. The resulting codebase is intentionally aligned with the curriculum and is not representative of current Android practice.

## Alternatives considered

**Kotlin with XML layouts** would preserve conventional Android activities and XML resources while reducing boilerplate and providing stronger null-safety. It was rejected because the course provides no Kotlin implementation examples, weakening the evidence that taught concepts were transferred into the project and increasing the amount of language knowledge that would need separate justification.

**Kotlin with Jetpack Compose** would provide a modern declarative UI and concise state-driven rendering. It was rejected because neither Kotlin nor Compose is taught in the referenced book, Compose-related architecture is absent from the curriculum, and the common single-activity navigation model conflicts with the plain reading of the requirement for several Android activities. It would add examination risk without improving the project’s central scoring and calibration logic.
