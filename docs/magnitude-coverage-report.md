# Magnitude coverage before and after Areas

This section records the structural question-bank limitation using the release metric from ADR
0013. Values are grouped by `floor(log10(trueValue))` in each category's fixed display unit. A band
is testable when it contains at least six questions.

## Before: original 64-question bank

| Band | Building heights | Mountain elevations | National populations | Total |
|---:|---:|---:|---:|---:|
| `10^1` | 2 | 0 | 0 | 2 |
| `10^2` | 16 | 6 | 0 | 22 |
| `10^3` | 0 | 12 | 0 | 12 |
| `10^4` | 0 | 0 | 6 | 6 |
| `10^5` | 0 | 0 | 5 | 5 |
| `10^6` | 0 | 0 | 5 | 5 |
| `10^7` | 0 | 0 | 6 | 6 |
| `10^8` | 0 | 0 | 5 | 5 |
| `10^9` | 0 | 0 | 1 | 1 |

Only 46 of 64 questions (71.9%) were in testable bands. None of the four testable bands fully met
the overlap and dominance rules: `10^3`, `10^4`, and `10^7` contained one category, while buildings
supplied 16 of 22 questions at `10^2`. The overall range was wide, but category remained a strong
predictor of the exponent.

## After: 80-question bank with Areas

| Band | Areas | Building heights | Mountain elevations | National populations | Total |
|---:|---:|---:|---:|---:|---:|
| `10^0` | 7 | 0 | 0 | 0 | 7 |
| `10^1` | 7 | 0 | 0 | 0 | 7 |
| `10^2` | 7 | 10 | 5 | 0 | 22 |
| `10^3` | 7 | 0 | 5 | 0 | 12 |
| `10^4` | 2 | 0 | 0 | 6 | 8 |
| `10^5` | 0 | 0 | 0 | 6 | 6 |
| `10^6` | 0 | 0 | 0 | 6 | 6 |
| `10^7` | 0 | 0 | 0 | 6 | 6 |
| `10^8` | 0 | 0 | 0 | 5 | 5 |
| `10^9` | 0 | 0 | 0 | 1 | 1 |

The change raises testable coverage to 74 of 80 questions (92.5%) and creates two fully compliant
bands, `10^2` and `10^3`. It does not make category independent of magnitude. Areas alone occupy
`10^0` and `10^1`; populations alone occupy the testable `10^5`–`10^7` bands; and populations
contribute six of eight questions at `10^4`, above the two-thirds cap.

The result is therefore a measured partial repair. One broad internally varied family substantially
improves the middle of the matrix, but the selected scope deliberately excludes another broad family
that could bridge its extremes. CI preserves the achieved level as a ratchet and prints the matrix
on every run. The five ADR rules remain the target definition, so the residual defect is visible
rather than reclassified as success.
