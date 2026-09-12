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
5. Every category containing at least twelve questions contributes at least two questions to each
   of at least four different magnitude bands.

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
