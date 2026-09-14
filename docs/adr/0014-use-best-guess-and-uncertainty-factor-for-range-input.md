# ADR 0014 — Use a best guess and uncertainty factor for range input

## Status

Accepted

## Date

2026-09-14

## Context

Interval mode asks the user for bounds intended to contain the true value with 90% probability.
The input control is therefore part of the measurement instrument: it affects not only usability,
but also the calibration data reported after fifteen or more sessions. Three variants were
wireframed before implementation: a two-thumb slider on a fixed logarithmic answer axis, two typed
bounds with a read-only scale strip, and a best guess combined with an uncertainty-factor dial.

The interface must avoid disclosing the answer's likely magnitude, work across many orders of
magnitude, and make confidence-range width intelligible. It must also correspond honestly to the
scoring model. ADR 0007 interprets the lower and upper bounds as
the 5th and 95th percentiles of a central 90% interval and scores them in logarithmic space. Its
lognormal belief model represents that central interval symmetrically around the central estimate
in log space. Accepting a nominally different primary representation and then silently fitting or
reinterpreting it would make the interface and scoring model disagree.

## Decision

The primary control will ask for one best guess, `g`, and one multiplicative uncertainty factor,
`f`. It will derive the stored bounds as `l = g / f` and `u = g * f` and display them live before
submission. Thus, “500 m, within a factor of 3” is shown as “167 m – 1 500 m”, subject to display
rounding. The user sees the exact claim that will be scored rather than having to infer it from the
control.

Log-space symmetry is intentional, not a defect of convenience. For a lognormal belief, the 5th
and 95th percentiles of the central 90% interval are equally distant from the central estimate
after logarithmic transformation. The equations above expose precisely that relationship. The
primary control therefore does not reduce what the scoring representation can express; it makes
that representation visible and teachable. The user
manipulates uncertainty itself, expressed as “within a factor of N”, rather than obtaining
uncertainty accidentally from two independently selected endpoints. This directly exercises the
skill the application claims to train.

The dial will be continuous from factor ×1.2 to ×100. Labels at selected factors are reference
marks, not detents; the thumb may rest between them and its live label will show the selected
factor. Coarse steps such as ×1.5, ×2, ×3, and ×10 were rejected. They would force a user whose
honest uncertainty is ×4 to round, quantising the recorded intervals and making measured coverage
partly an artefact of the instrument. Since calibration across repeated sessions is itself a
reported project result, that distortion is unacceptable. Continuous input still has finite
screen and numeric precision, but it does not impose categorical rounding.

A secondary action, “Set the two bounds myself”, will open direct lower- and upper-bound entry for
genuinely asymmetric beliefs, including beliefs constrained by a natural boundary. It produces the
same interval-answer representation and stored bounds as the primary path; scoring and analytics
will not distinguish how the answer was entered.

## Consequences

The step from point mode to interval mode adds one control while retaining the familiar best-guess
field. Implementation is smaller than for a two-thumb control: one thumb is
mapped to one factor, with no thumb-crossing rules, minimum-gap handling, or bidirectional
synchronisation between two handles and two fields. Live derivation and rounding nevertheless
require tests, especially at positive-value limits and when displayed bounds round to the same
number.

The factor dial is not an answer axis. Its scale is identical for every question and therefore
carries no information about the true answer's order of magnitude. Derived bounds may extend to
any magnitude permitted by the value domain; the factor control has no answer-space end stop.

The primary path privileges the lognormal central-interval model. The secondary path must therefore
remain discoverable and must not be presented as an error or advanced feature. Because displayed
bounds remain one derivation away from the entered values, the live preview is essential before
submission.

## Alternatives considered

### Two-thumb slider on a fixed 1–10⁹ logarithmic axis

This variant made the whole available scale visible and allowed one gesture to set both bounds.
Its strongest reusable idea was an axis identical for every question, whose bounds revealed nothing
about the current answer. The factor dial inherits this property without using an answer axis.

The variant was rejected because nine decades compressed into 360 dp make precise selection
difficult: a small finger movement materially changes a bound, requiring typed correction. Two
thumbs also need crossing and minimum-gap rules, and typed fields must remain synchronised with
both. Fixed answer-space end stops can clip valid ranges; overflow behaviour only mitigates that
conceptual mismatch.

### Two typed bound fields with a read-only scale strip

This variant was exact at any magnitude, required no gesture precision, and reused the numeric
keyboard interaction from point mode. The passive strip could show position and width without
inviting answer-fishing.

It was rejected as the primary path because entering two numbers costs more taps and treats range
width as a by-product. A passive strip makes uncertainty less tangible, weakening the intended
lesson that an overconfident range is too narrow. Its fields are retained as the asymmetric-belief
escape hatch.
