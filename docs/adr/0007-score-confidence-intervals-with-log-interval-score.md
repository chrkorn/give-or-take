# ADR 0007 — Score confidence intervals with a log interval score

- **Status:** Accepted
- **Date:** 2026-09-08

## Context

Interval mode asks for a range that the user believes has a 90% probability of containing the
true value. A binary score—one point when the interval contains the truth and zero otherwise—
measures coverage but ignores informativeness. For a true value of 346, both `[300, 400]` and
`[1, 10^12]` score one point. The first limits the answer to a factor of 1.33, whereas the second
spans a factor of one trillion. A user could therefore maximise the hit rate by always reporting
the widest permitted interval while communicating almost no knowledge. This is precisely the
behaviour that confidence-calibration training must not reward.

The score must balance *calibration*—agreement between stated probabilities and observed
frequencies—with *sharpness*—concentration of the ranges. Forecasting research addresses this
with proper scoring rules. A rule is proper when expected performance is optimised by reporting
the belief actually held; honesty is the winning strategy. For an interval rule, this concerns
the requested quantiles, not an unreported complete distribution.

## Decision

The first interval-scoring policy will use the interval score, also called the Winkler score. Let
`[l, u]` be a central `(1 - alpha)` interval and `y` the true value. The loss is

```text
IS_alpha(l, u; y) = (u - l)
                    + (2 / alpha)(l - y) * I(y < l)
                    + (2 / alpha)(y - u) * I(y > u)
```

where `I(condition)` is one when true and zero otherwise. The first term charges for width; the
others charge proportionally for misses below and above the bounds. For 90% coverage,
`alpha = 0.10`, so a miss has multiplier 20. Minimising expected loss elicits the user's 5th and
95th percentiles. Smaller losses are better. The literature calls this *negatively oriented*;
the values need not be negative.

The natural-scale formula is unsuitable across questions spanning orders of magnitude: a width of
100 may be enormous for one quantity and negligible for another. In accordance with ADR 0004,
the policy will first transform all three values with `z(x) = log10(x)`:

```text
L(l, u; y) = log10(u / l)
             + 20 * log10(l / y) * I(y < l)
             + 20 * log10(y / u) * I(y > u)
```

Differences of logarithms should replace the displayed ratios in code to avoid overflow. Unit
changes then cancel. For `y = 346`, `[300, 400]` has loss `log10(400 / 300) = 0.125`, whereas
`[1, 10^12]` has loss `12`. The miss `[100, 300]` has width `0.477` plus penalty
`20 log10(346 / 300) = 1.239`, totalling `1.716`. Width matters on a hit, and distance on a miss.

Per-answer loss and calibration across answers will be retained separately. Calibration is
`hits / intervalAnswers`, compared with 90%. Broad ranges can achieve or exceed the target while
conveying little; sharp ranges can score well individually yet miss too often. Loss therefore
discourages width, while accumulated hit rate exposes overconfidence below 90% and possible
underconfidence above it. The app must show the sample size because one answer cannot establish a
long-run frequency.

All values must be finite and strictly positive. Zero and negative bounds are invalid input, as
`IntervalGuess` already enforces, because their logarithms are undefined. Signed or zero-valued
questions require another policy.

Raw loss is unbounded and informative for analysis but awkward as a game score. A later decision
may map it to 0–100 with, for example, `100 exp(-kL)`. This is familiar but introduces `k`, hides
the decomposition, and is not guaranteed to remain proper if users optimise it; generally only
positive affine transformations preserve propriety. Raw losses must therefore be stored and
aggregated. A tested user-facing mapping is deferred.

## Resolved objections

### An unbounded loss can dominate a session

A hard cap is rejected. Beyond it, additional error is free. A user can shorten an honestly held
interval, gain reduced width on ordinary outcomes, and pay no extra cost for extreme misses. The
cap therefore incentivises narrower, overconfident reports.

Instead, fixed positive limits on truths and bounds make the worst log-distance finite without
altering the rule, although they must cover the bank and avoid revealing answers. Alternatively, a
strictly monotone asymptotic display transform never equates finite losses, but extreme raw losses
still dominate analytics and transformed averages can change incentives. Input-domain bounds are
recommended for session scoring because they preserve propriety; their exact values require a
separate domain decision. A display transform may supplement, but not replace, raw analysis.

### A contained truth should score better when it is closer and more central

Closeness is already addressed. Inside `[l, u]`, total distance to the endpoints is
`(y - l) + (u - y) = u - l`; another term would double-count it. Equal-width successful intervals
deliberately tie.

Centrality is different. Endpoints are percentiles, not equal raw distances around a midpoint.
Suppose a right-skewed belief has 5th percentile 10, median 100, and 95th percentile 1,000. A truth
of 100 is central in probability but only 9% across the raw interval. A bonus for arithmetic
centrality encourages `[10, 190]`, making 100 the midpoint while deleting a sincerely held upper
tail and rewarding overconfidence.

The underlying wish—to reward an observation more when it lies in a region assigned greater
probability—is nevertheless sound. The proper rule for that richer forecast is the continuous
ranked probability score (CRPS):

```text
CRPS(F, y) = integral from -infinity to infinity of
             (F(x) - I(x >= y))^2 dx
```

CRPS scores the whole CDF `F`. A later policy could treat slider bounds as the 5th and 95th
percentiles of a lognormal belief. With `a = Phi^-1(0.95)`, its normal parameters are uniquely
`mu = (ln(l) + ln(u)) / 2` and `sigma = (ln(u) - ln(l)) / (2a)`. Lognormal beliefs are positive,
right-skewed, and multiplicative, but impose an unreported shape and increase explanation and
implementation costs. Natural-scale lognormal CRPS is not scale-free; normal CRPS on log-values
would be comparable across questions but constitutes another design decision.

A closed form for lognormal CRPS is reported by Baran and Lerch (2015). CRPS may later
become a second `ScoringPolicy`, possibly unlocked through level progression, after interval
scoring and calibration tracking are validated.

## Alternatives considered

**Naive hit rate** remains a calibration statistic but width is free. **Hit rate plus a fixed-rate
width penalty** requires an arbitrary trade-off and still equates near and extreme misses.
**Pinball loss** on the 5th and 95th quantiles is proper and equivalent up to positive scaling;
interval form better exposes width and misses. **Brier score** evaluates a probability for a binary
event. Here probability is fixed at 0.90 and the event changes with the bounds, so it omits width.
**CRPS** is deferred because it requires a full distribution or a modelling assumption.

## Consequences

The pure-Java policy requires tests at boundaries, for both misses, scale changes, and numeric
extremes. Raw loss, hit status, and sample count must be stored separately. The policy resists the
widest-interval strategy and leaves an extension point for full-distribution scoring.

## References

- Baran, S., & Lerch, S. (2015). Log-normal distribution based Ensemble Model Output Statistics
  models for probabilistic wind-speed forecasting. *Quarterly Journal of the Royal Meteorological
  Society, 141*(691), 2289–2299. https://doi.org/10.1002/qj.2521
- Bracher, J., Ray, E. L., Gneiting, T., & Reich, N. G. (2021). Evaluating epidemic forecasts in an
  interval format. *PLOS Computational Biology, 17*(2), e1008618.
  https://doi.org/10.1371/journal.pcbi.1008618
- Gneiting, T., & Raftery, A. E. (2007). Strictly proper scoring rules, prediction, and estimation.
  *Journal of the American Statistical Association, 102*(477), 359–378.
  https://doi.org/10.1198/016214506000001437
- Lichtenstein, S., & Fischhoff, B. (1980). Training for calibration. *Organizational Behavior and
  Human Performance, 26*(2), 149–171. https://doi.org/10.1016/0030-5073(80)90052-5
- Matheson, J. E., & Winkler, R. L. (1976). Scoring rules for continuous probability distributions.
  *Management Science, 22*(10), 1087–1096. https://doi.org/10.1287/mnsc.22.10.1087
- Winkler, R. L. (1972). A decision-theoretic approach to interval estimation. *Journal of the
  American Statistical Association, 67*(337), 187–191.
  https://doi.org/10.1080/01621459.1972.10481224
