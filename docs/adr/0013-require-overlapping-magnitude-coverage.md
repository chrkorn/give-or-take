# ADR 0013 — Require overlapping magnitude coverage

- **Status:** Accepted
- **Date:** 2026-09-12

## Context

Log-relative scoring lets one question bank compare estimates across quantities whose answers span
many orders of magnitude. A wide minimum-to-maximum range is not sufficient, however. The first
generated bank covers values from tens to billions, but its categories nearly partition that range:
buildings occupy tens and hundreds, mountains occupy hundreds and thousands, and national
populations occupy ten-thousands through billions. A player can therefore infer much of the
answer's exponent from the category before reasoning about the subject.

The desired property is low predictive power of category alone. After choosing and fixing a
human-readable answer unit, each category should cover several base-10 magnitude bands and should
share its well-populated bands with other categories. Complete statistical independence is neither
required nor desirable: the type of thing being estimated is legitimate evidence, and forcing
identical distributions would replace one artificial quota with another.

Orders of magnitude are also unit-dependent. Expressing the same areas in square metres instead of
square kilometres shifts every exponent by six without improving the questions. Coverage can only
be interpreted after each category's answer unit has been selected for player comprehension and
then kept fixed.

## Decision

Question categories will describe broad measurement families rather than narrow subject classes.
The next family will be **Areas**, sourced through Wikidata area property P2046 and expressed in
square kilometres. Countries, islands, lakes, and protected areas are candidate subject types
within that one category rather than separate categories.

Every area question will be treated as time-varying and will state a reference date. Its prompt
will also state the applicable measurement basis, such as total jurisdictional area including
inland water, land area excluding inland water, lake surface area under a stated condition, or an
officially designated protected area. Country-area candidates are admitted only when the boundary
and inclusion basis are undisputed and explicit. This retains ADR 0011's exclusion of disputed
country areas.

The generator will validate a magnitude-coverage matrix. For a positive answer value `v` in its
fixed display unit, its band is `floor(log10(v))`. Let a matrix cell contain the number of questions
from one category in one band. A release bank must satisfy all of these rules:

1. A band is testable when it contains at least six questions.
2. At least 80 percent of all questions occur in testable bands.
3. Every testable band contains at least two categories that contribute at least two questions
   each.
4. No category contributes more than two thirds of a testable band.
5. Every category containing at least twelve questions **and whose configured admissible value
   range can occupy at least four magnitude bands** contributes at least two questions to each of
   at least four different bands.

The thresholds are deliberately integer-friendly. In the smallest testable band, a `4 + 2` split
passes while `5 + 1` fails. Requiring two questions makes overlap substantive rather than allowing
one token record to satisfy the rule. Four internally represented bands require useful breadth
without demanding that every category span the complete bank.

The coverage validator is development-time tooling. It does not belong to the Android-free Java
domain model because magnitude balance is a property of the curated bank, not a gameplay rule.

## Consequences

The check directly rejects the structural defect in the first bank and produces failures in terms
of concrete categories and bands. It permits naturally uneven source availability and avoids exact
per-band quotas.

Adding an Areas category increases editorial work. P2046 identifies the quantity but does not by
itself resolve land-versus-total area, boundary disputes, water-level conventions, or dated park
boundaries. The source-admission rules in ADR 0011 remain authoritative; passing magnitude coverage
does not establish factual reliability or estimability.

The check is intentionally coarse. Values immediately below and above a power of ten fall into
different bands, sparse edge bands are not individually constrained, and a misleading category
taxonomy could game the matrix. The 80-percent rule limits sparse-band avoidance, while category
semantics and prompt quality remain manual review concerns.

## Alternatives considered

**Require only a wide overall minimum-to-maximum range.** Rejected because disjoint category ranges
can produce an excellent overall span while making category a strong exponent predictor.

**Require only two categories in every occupied band.** Rejected because one token question could
hide overwhelming dominance, and very sparse edge bands would make regeneration fragile.

**Gate on mutual information or category-only prediction accuracy.** These are useful report
diagnostics but produce less intelligible failures and thresholds that are sensitive to the chosen
category mix.

**Force equal counts in every matrix cell.** Rejected because this would manufacture a uniform
distribution, promote weak source candidates merely to fill quotas, and repeat the quota-driven
obscurity problem removed under ADR 0011.

## Amendments

**2026-09-12 — volatility moved from category to question.** A live feasibility query showed that
requiring a point-in-time qualifier on every P2046 statement removes most islands, lakes, and
national parks and leaves too few defensible candidates to span the intended magnitudes. Directly
referenced, familiar P2046 candidates without that qualifier do span `10^0`–`10^7` square
kilometres. Treating their retrieval date as the measurement date was rejected because it would
make the prompt more precise-looking without adding evidence.

The earlier decision that every Areas question is time-varying is superseded. Question-bank version
4 records volatility per question. Stable, source-reviewed physical measurements may omit `asOf`;
changing jurisdictional boundaries and protected areas still require a genuine reference date.
This preserves one broad Areas category without weakening date validation for the records that need
it.

**2026-09-12 — conservative Areas source contract.** The implemented pool uses direct instances of
island, lake, national park, and sovereign state. Every selected P2046 statement must use square
kilometres, omit an applies-to-part qualifier, and contain a direct HTTP reference URL. Island
prompts say land area; lake prompts say surface area. National parks use officially designated area
and require a point in time. Countries require both a point in time and an explicit P1011 qualifier
excluding maritime waters, so an unqualified or differently scoped country figure is not silently
presented as comparable.

A live dry run at the familiarity floor of twenty left 93 area candidates after all automatic
admission rules. Round-robin selection produced thirty questions across five bands (`10^0` through
`10^4` square kilometres, distributed 7/7/7/7/2). This is the measured strict-pool span; the earlier
`10^0`–`10^7` result described the looser feasibility query before the date and scope rules were
applied.

**2026-09-12 — rule 5 limited to physically eligible categories.** The original wording applied
the four-band requirement to every category with at least twelve questions. That made the rule
unsatisfiable for configured mountain elevations (100–9,000 metres) and building heights
(10–1,000 metres): adding questions cannot make a bounded physical range occupy four bands. A quota
of fewer than twelve merely hid the contradiction and would make an otherwise harmless quota
increase fail.

Rule 5 now applies only when the category's configured minimum and maximum can occupy at least four
base-10 bands. It therefore continues to constrain broad families such as Areas and national
populations, while the intentionally retained narrow height categories are judged through the
bank-level overlap and dominance rules. Dropping rule 5 entirely was rejected because a nominally
broad category could then collapse into one or two populated bands. Adding a second bank-level
breadth rule was rejected because the existing 80-percent testable-share rule already measures bank
breadth and would not replace the need to check whether broad categories actually use their range.

**2026-09-12 — partial repair accepted and protected by a ratchet.** Adding Lengths or Masses was
explicitly rejected for the current scope. The five rules remain the definition of a fully repaired
bank, but the shipped bank is accepted with a measured limitation. CI prints the complete matrix and
checks monotonic outcomes against the accepted baseline: at least 74/80 questions in testable bands,
at least two fully compliant bands, at most five insufficient-overlap bands, at most one other
dominated band, and no broad-category span failures. A future improvement passes without changing
the baseline; a regression fails.

The known gaps are `10^0` and `10^1`, which contain only Areas; `10^5`, `10^6`, and `10^7`, which
contain only national populations; and `10^4`, where populations contribute six of eight questions
and exceed the two-thirds cap. Areas overlap the two retained height categories at `10^2` and
`10^3`, so those bands meet the full-repair rules. The gap remains because the approved subject
families do not naturally bridge small areas to large populations, and manufacturing that bridge
would require adding another broad measurement family. This limitation is accepted and documented
rather than concealed by weakening the thresholds.
