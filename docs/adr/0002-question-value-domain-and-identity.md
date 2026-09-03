# ADR 0002 — Define question value domain and identity
- **Status:** Accepted
- **Date:** 2026-09-03

## Context

Each numerical question needs an authoritative true value. Standard-mode scoring will use
the log-relative error `|log10(guess / truth)|`, which compares estimates by multiplicative
distance. This expression is undefined when the true value is zero and does not provide a
useful interpretation for signed quantities.

Questions also need equality semantics. Their wording, attribution, difficulty, or corrected
true value may change while stored session data must continue to refer to the same question.

## Decision

`Question` accepts only finite true values greater than zero. Questions involving zero or
signed values are outside the initial question domain. They may be added later with a scoring
policy designed explicitly for them rather than a fallback hidden inside log-relative scoring.

Two questions are equal when their stable IDs are equal. Question collections and the data
layer must therefore prevent different questions from reusing an ID.

`Question` is immutable and constructed through a builder. The builder labels the eight
arguments at the call site, avoiding mistakes between the five string-valued fields while
keeping validation in the `Question` constructor.

## Consequences

- Log-relative scoring can assume a positive true value, although it must still validate the
  player's guess.
- The initial question bank cannot include quantities such as Celsius temperatures, values
  below a reference point, or quantities whose true value is zero.
- Corrections to question content do not break references from earlier sessions.
- Duplicate IDs with conflicting content are not detectable through equality alone and must
  be rejected when questions are loaded or stored.
