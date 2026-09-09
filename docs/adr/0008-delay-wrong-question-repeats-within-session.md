# ADR 0008 — Delay wrong-question repeats within a session

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

ADR 0006 supplies the discrete signal needed by a training strategy: only answers classified as
`WRONG` enter a repeat queue. The remaining decision is when a wrong question should return.
Repeating it immediately makes the revealed value easy to echo from working memory and conflicts
with the requirement not to present the same question twice in succession. Moving all repeats to
the end gives an unpredictable delay and can create a frustrating block of difficult questions.
Persisting Leitner-style review boxes would support spacing across sessions, but would require
per-question scheduling data and risks shifting this estimation trainer toward memorising fixed
trivia answers.

Research on distributed practice supports spacing learning events rather than massing them, while
research on retrieval practice supports asking learners to retrieve information repeatedly. These
results provide a rationale for a delayed repeat, but they do not establish one optimal delay for
short numerical-estimation sessions. The chosen delay is therefore a provisional interaction
design parameter rather than an empirically validated optimum for this app.

## Decision

A question answered `WRONG` will be requeued after two intervening questions. `CORRECT` and
`CLOSE` answers will not be requeued. The queue exists only for the current session and requires
no persistence or database-schema change.

The requested session length is the maximum number of distinct questions in the initial schedule.
When the pool contains fewer questions, the strategy asks every distinct question once rather than
repeating correct answers to fill the requested length. Remedial repeats can extend that initial
schedule.

At the tail of a session, fewer than two questions may remain. A wrong question is placed after all
remaining questions, giving the longest available delay. If no question remains, it is not
requeued: repeating it immediately would violate the stronger no-consecutive-question invariant,
and adding already completed questions merely as spacers would make session length unpredictable.
This also makes a one-question pool terminate sensibly.

The strategy receives a `Random` instance from its caller and never creates its own. A fixed seed
therefore makes the initial shuffle reproducible in JVM tests.

## Alternatives considered

**Immediate repeat** was rejected because it is massed practice, is likely to feel punitive, and
cannot coexist with the no-consecutive-question rule. **Requeue at the end** was rejected because
its delay depends on the original question position and it clusters difficult material at the
end. **Leitner-style boxes** were deferred because cross-session scheduling needs persistent state
such as a box number and next-due value, with corresponding schema and migration work.

## Consequences

The scheduler needs only an in-memory ordered queue and the currently unanswered question. The
two-question delay is easy to explain and test deterministically. Sessions with wrong answers may
contain more presentations than the requested initial length, while sessions with undersized pools
may contain fewer. A future empirical study may tune the delay or justify cross-session spacing;
such a change must amend this decision and add persistence deliberately if needed.

## References

- Cepeda, N. J., Pashler, H., Vul, E., Wixted, J. T., & Rohrer, D. (2006). Distributed practice in
  verbal recall tasks: A review and quantitative synthesis. *Psychological Bulletin, 132*(3),
  354–380. https://doi.org/10.1037/0033-2909.132.3.354
- Karpicke, J. D., & Roediger, H. L. (2008). The critical importance of retrieval for learning.
  *Science, 319*(5865), 966–968. https://doi.org/10.1126/science.1152408
