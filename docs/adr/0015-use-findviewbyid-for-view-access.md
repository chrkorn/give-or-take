# ADR 0015 — Use `findViewById` for view access

## Status

Decided

## Date

2026-09-15

## Context

Give or Take uses Java, XML layouts, and `AppCompatActivity`. Each activity must obtain references
to the views declared in its layout. The course material teaches `findViewById` exclusively, and
part of the assessment concerns “Transfer”: demonstrating how concepts taught in the course were
applied. Choosing another mechanism is therefore possible, but its benefit and relationship to the
taught activity-and-layout model would need to be explained in the report.

ViewBinding generates a binding class for each XML layout. Its fields have the types declared by
the layout, so an incorrect view type is normally caught during compilation rather than through a
failed cast or later use. After a binding has been inflated successfully, views required by that
layout are available without repeated lookup calls. This reduces boilerplate and the opportunity
to use the wrong identifier. Java itself still does not provide Kotlin-style null safety, and
views that exist only in some layout configurations may be represented as nullable, so
ViewBinding reduces rather than eliminates null-related mistakes.

`findViewById` needs no generated binding class and no additional build setting. It requires one
lookup per view and permits a wrong identifier to produce `null` or a type mismatch at runtime.
Those risks can be kept visible through small activities, immediate assignments after
`setContentView`, and Espresso coverage, but they remain costs of the approach.

## Decision

This app will use `findViewById` consistently in activities. View references will be assigned immediately after `setContentView`, use the narrowest
practical scope, and be covered by UI tests for the relevant screen.

This is a context-specific decision,
not a claim that `findViewById` is generally preferable for Java Android applications.

## Consequences

Activity code will map directly onto the sequence shown in the course: inflate an XML layout,
look up its views, attach listeners, and navigate with explicit intents. That makes the
implementation easier to explain with the taught concepts and requires no ViewBinding build
configuration or generated sources.

The activities will contain more repetitive assignments. Layout and Java code may drift without
a compile-time error, and missing or incorrect identifiers can fail only when the screen runs.
Consistent naming, limited activity size, and Espresso tests reduce these risks but do not provide
ViewBinding's compile-time guarantees.

## Alternatives considered

### ViewBinding

ViewBinding would be enabled with a small Gradle `buildFeatures` setting and would generate typed
references from each XML layout. It would remove most lookup boilerplate, improve type safety, and
make required views effectively non-null after successful inflation. It is a first-party Android
feature, not a third-party dependency, so adopting it would not conflict with the project's goal
of minimising external libraries.

Its cost is modest but real: another build feature, generated classes, slightly more build work,
and binding-specific lifecycle code. More importantly here, it is absent from the course material.
The report could justify it as a safer implementation of the same XML-and-activity concepts, but
that explanation would be less direct than showing the exact `findViewById` approach that the
course teaches.
