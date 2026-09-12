# ADR 0012 — Store fully composed question prompts

- **Status:** Accepted
- **Date:** 2026-09-12

## Context

A numerical answer is reproducible only when the question distinguishes its unit from the criterion
that produced the value. A building value might mean architectural height, roof height, or height to
the antenna; an elevation needs a datum; and a population needs both a definition and a reference
date. Version 1 stored a prompt and unit but could not represent these distinctions structurally.

Adding structured context creates a representation choice. The bank could store only prompt inputs
and let the app assemble sentences, or it could retain the complete reviewed prompt alongside those
inputs. Runtime assembly would prevent the rendered sentence from contradicting the fields and could
eventually support localised templates. It would also require subject names, template identifiers,
article rules, date formatting, and category-specific grammar in the application. Localised
templates alone would be insufficient because subject names and measurement-basis phrases would
also need translations.

The question bank is generated, manually reviewed, and bundled with the application. It is not a
user-owned interchange format. Reviewers need to see the exact text a player will read when a bank
is regenerated.

## Decision

Version 2 stores the fully composed `prompt` and adds the required `measurementBasis` field. It also
adds an optional `asOf` ISO date and a required root-level `timeVaryingCategories` declaration. A
question in a declared time-varying category must contain `asOf`; every other question must omit it.

The development-time generator remains responsible for language composition. Its category
specifications define the unit, measurement basis, time-varying status, and prompt template together.
Generated prompts explicitly include the measurement context. A manual prompt override is rejected
when it drops the basis or unit words, or the reference date or source where applicable. The Java
loader validates the structured contract but does not attempt to understand arbitrary English prose.

The loader accepts only version 2. Because the bank and reader ship in the same APK, a version-1
asset is a build or packaging defect. Runtime migration could not supply a trustworthy required
measurement basis without category-specific assumptions, so accepting the previous version would
weaken validation while maintaining an otherwise unnecessary parser path.

## Consequences

Regeneration diffs can show both a structured-field change and its corresponding prompt change.
That duplication is intentional: it exposes the exact player-facing consequence during review.
Prompt overrides retain editorial flexibility but receive deterministic drift checks during
generation.

The English prompt remains part of the bundled data. Full localisation would require localised
question content or a later schema designed around translated subject names and measurement bases;
switching only the sentence template to Android string resources would not solve that problem.

The root declaration keeps category volatility out of `Question` itself. `Question` stores an
immutable `LocalDate` when supplied, while `QuestionBank` applies the cross-record rule that decides
whether the date is required or forbidden.
