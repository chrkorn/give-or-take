# ADR 0009 — Use curriculum levels and mean session scores

- **Status:** Accepted
- **Date:** 2026-09-10

## Context

The app needs level progression and high scores, but a level can represent several different
ideas: harder authored questions, tighter correctness thresholds, different answer modes, or
longer sessions. Only the answer-mode interpretation follows the app's pedagogical purpose. Point
estimation teaches numerical accuracy; confidence intervals then ask the player to express and
calibrate uncertainty.

Session length is not constant. The training strategy limits initial distinct questions but may
add remedial repeats after wrong answers. A total score would therefore increase merely because a
session contained more answers. In addition, point-estimate scores contain display points whereas
the proper interval rule deliberately retains an unbounded raw loss with no points mapping.

## Decision

There are two curriculum levels. Level 1 uses point estimates and `LogRelativeScore`. Level 2 uses
90 percent confidence intervals and the log interval score from ADR 0007. The authored difficulty
from 1 to 5 remains an independent property of a question and can vary within both levels.

The 2026-09-12 amendment to ADR 0011 supersedes that question-difficulty decision after external
review showed that its sitelink-derived values measured obscurity rather than estimation difficulty.
The two skill-based curriculum levels remain unchanged.

Confidence intervals unlock after a point-estimate session with at least ten answered questions
and at least 70 arithmetic-mean points. Both thresholds are inclusive. This is a deliberately
simple first mastery rule; it is suitable for deterministic testing but does not claim that one
session proves stable ability. An unlocked level is permanent. Later weak performance may inform
feedback or question selection but never removes access.

A session aggregates individual `Score` values with the arithmetic mean and retains its answer
count. Empty sessions have no mean. Point-estimate sessions expose mean raw error and mean points;
interval sessions expose mean raw loss only. Scores whose points representation does not match the
session level are rejected so unlike policies cannot be combined accidentally.

High scores are level-specific immutable values. Point-estimate records compare mean points and
higher is better. Interval records compare mean raw loss and lower is better. Only a strictly
better result replaces a record; an exact tie preserves the earlier record and its sample size.
The value object contains no storage code. A later data-layer component may persist and restore its
primitive values.

CRPS is not a third level. ADR 0007 deferred it because deriving a full lognormal belief from two
bounds imposes an additional modelling assumption. It requires a separate accepted decision and
scoring implementation before it can become another curriculum stage.

## Alternatives considered

**Question difficulty as level** was rejected because it confuses content selection with the skill
being learned. **Tighter correctness bands** were rejected as an arbitrary difficulty control and
would change the wrong-answer repeat signal. **Longer sessions** increase duration rather than
conceptual mastery. **Summed session points** were rejected because remedial repeats and user-selected
lengths would make records incomparable. **Dropping a level after weak performance** was rejected
because revoking an already learned mode is punitive and prevents users from choosing earlier
practice. **Rolling or consecutive-session advancement** would be less noisy, but needs more
historical state and is deferred until usage evidence justifies that complexity.

## Consequences

The first advancement criterion can be tuned later only through an explicit decision because it
changes progression semantics. Session means make different lengths comparable, while the visible
answer count communicates how much evidence supports a result. Point and interval records remain
separate and have opposite comparison directions. The UI and data layers must store level identity
beside any high-score value and must not label raw interval loss as points.
