# Material design review

Date: 2026-09-28

## Scope and test configuration

This pass covers every XML screen and reusable row in the app. It keeps the existing Java,
`AppCompatActivity`, XML-layout and Material Components architecture. No dependency was added.

Visual verification used the Pixel_9 AVD on API 35 in portrait with both light and dark mode. The
largest tested accessibility configuration combined `font_scale=2.0` with a 560 dpi display
density. Both point-estimate and 90% interval sessions were exercised through quiz, feedback and
result screens. This is evidence for that phone configuration only; landscape, tablets, foldables,
physical devices and API 37 remain outside this pass.

## Colour system

`Theme.GiveOrTake` now inherits from `Theme.Material3.DayNight.NoActionBar` and supplies the full
set of primary, secondary, tertiary, error, background, surface, outline, inverse-surface and
surface-container roles. `values-night` contains an explicit dark palette instead of relying on
light colours being transformed at runtime.

Representative WCAG contrast checks for the theme roles were:

| Pair | Light | Dark |
| --- | ---: | ---: |
| primary / on-primary | 6.98:1 | 7.66:1 |
| background / on-background | 16.35:1 | 14.03:1 |
| surface-variant / on-surface-variant | 7.20:1 | 5.49:1 |
| background / primary | 6.68:1 | 10.59:1 |
| background / error | 6.18:1 | 10.62:1 |
| background / outline | 4.29:1 | 5.70:1 |

These checks cover ordinary text, actions, errors and outlines. They meet 4.5:1 for text except the
light outline, which is not used as small text and exceeds the 3:1 non-text boundary requirement.

The correctness-band colours remain exactly as specified:

| Band | Light background / foreground | Dark background / foreground | Non-colour cue |
| --- | --- | --- | --- |
| CORRECT / CONTAINED | `#DCEFE3` / `#174D30` | `#173D29` / `#B8E8C8` | `●` plus label |
| CLOSE | `#F8E8C4` / `#584000` | `#443508` / `#F5D98A` | `▲` plus label |
| WRONG / MISSED | `#F9DEDC` / `#6D1B1B` | `#4D2525` / `#F4C7C4` | `×` plus label |

The supplied measurements were accepted rather than recalculated: band text contrast is
8.07–9.06:1 in light mode and 8.60–8.88:1 in dark mode. The supplied simulations also show why
colour alone is insufficient: CORRECT and WRONG backgrounds are only dE76 3.6 apart under
deuteranopia and 3.2 under protanopia, while all three backgrounds are within a 1.06 luminance
ratio in greyscale. The tint is therefore decorative. Feedback uses the same circle / triangle /
cross vocabulary already present on the result screen, and always retains the textual band name.
The symbol is excluded from accessibility focus because the adjacent label already conveys the
meaning without making a screen reader announce punctuation.

## Type scale

XML layouts no longer set ad-hoc `textSize` values. They use Material theme attributes:

| Role | Material attribute | Use |
| --- | --- | --- |
| Display | `textAppearanceDisplaySmall` | session score and other isolated key results |
| Screen/question heading | `textAppearanceHeadlineMedium` | screen titles and question prompts |
| Secondary result | `textAppearanceHeadlineSmall` | calculated interval readout |
| Card/section heading | `textAppearanceTitleLarge` | prominent card titles and summaries |
| Body | `textAppearanceBodyLarge` / `BodyMedium` | explanations and supporting copy |
| Labels/actions | `textAppearanceLabelLarge` / `LabelMedium` | buttons, counters and metadata |

The hand-drawn chart and uncertainty dial retain resource-backed `sp` sizes because canvas text
cannot consume a `TextAppearance` directly. The dial now omits interior labels when they would
overlap at large font scales while retaining both endpoints, tick marks and the full accessible
value. Long units are full-width labels rather than suffixes inside numeric fields, so the unit no
longer takes the input area away from the number.

## Spacing and shape system

All screen spacing comes from one 4 dp-based scale:

| Token | Value | Intended use |
| --- | ---: | --- |
| `space_1` | 4 dp | tightly related text |
| `space_2` | 8 dp | control internals and compact separation |
| `space_3` | 12 dp | related controls |
| `space_4` | 16 dp | card padding |
| `space_5` | 24 dp | screen gutters and section separation |
| `space_6` | 32 dp | major separation |
| `space_7` | 40 dp | exceptional large separation |

Shared geometry uses a 12 dp medium corner, 1 dp ordinary stroke, 0 dp card elevation, 48 dp
minimum touch target, 56 dp ordinary button minimum and 64 dp prominent-button minimum. Custom
view drawing dimensions are separately named because they are geometry rather than layout gaps.

## Component policy

- Filled primary buttons are reserved for the next or main action: Start session, Submit, Next and
  Play again.
- Outlined secondary buttons are navigation or alternatives: Stats, Settings, Home and Share.
- Text buttons are low-emphasis inline mode changes and source links.
- The destructive reset action is an outlined error-colour button, never a filled primary action.
- Cards group a genuine unit of information. They use a surface-container colour, 12 dp corners,
  1 dp outline and zero elevation; visual hierarchy comes from spacing and surface roles rather
  than inconsistent shadows.
- Material toolbars are used for secondary screens with a standard up action and accessible
  navigation description.

## Layout review

| Layout | 48 dp targets | Largest font/display and truncation | Dark mode | Result |
| --- | --- | --- | --- | --- |
| `activity_main` | 56 dp buttons | Converted from fixed placement to a scrolling vertical flow; all cards and actions remain reachable | Checked | Pass |
| `activity_quiz` | 56 dp Submit, 48 dp text action and dial hit area | Long prompts scroll; point and interval inputs remain reachable; units moved outside fields; dial labels adapt | Checked in both modes | Pass |
| `activity_feedback` | 56 dp Next, 48 dp source action | Prompt, band copy and score scroll independently of the fixed action; no fixed-height text | Checked for point and interval feedback | Pass |
| `activity_result` | 56 dp actions | Score, high-score, bands and calibration content scroll; actions stack vertically and stay reachable | Checked | Pass |
| `activity_stats` + `item_stats_header` | toolbar action at least 48 dp | The two-column metric grid broke words at maximum font size, so metrics now stack in one column; remaining content scrolls | Checked with populated history | Pass |
| `item_stats_session` | row actions meet Material minimums | All row text wraps; no fixed-height text containers | Checked through populated statistics | Pass |
| `activity_settings` | 56/64 dp buttons, 48+ dp radio and checkbox rows | Toolbar title, toggle labels, modes, categories and reset flow remain readable through scrolling | Checked | Pass |
| `view_uncertainty_dial_preview` | custom control exposes at least a 48 dp hit area | Tick labels are collision-aware and accessibility exposes the precise selected value | Checked through interval quiz | Pass |

No XML text element uses a fixed height. `wrap_content`, minimum heights and scrolling are used so
font growth expands content instead of clipping it. The maximum-size pass found two structural
layout failures—the fixed home composition and the result action arrangement—and corrected both.
It also found and corrected the statistics metric wrapping, long-unit suffix and dial-label
collisions.

## Verification limits

The pass does **not** establish tablet, foldable, landscape, split-screen, TalkBack/Accessibility
Scanner or physical-device quality. Those are recorded as gaps in the core quality audit rather
than being implied by the phone screenshots.
