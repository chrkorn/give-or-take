# Question-bank generator

`build_questions.py` creates the version-3 `questions.json` consumed by Give or Take. It queries
referenced, non-deprecated Wikidata statements selected under ADR 0011. It uses only the Python 3
standard library; `requirements.txt` exists to make that absence of third-party dependencies
explicit.

## Recommended bank shape

The defaults generate 80 questions:

| Category | Default | Typical magnitude | Why it is included |
|---|---:|---:|---|
| Mountain elevations | 25 | `10^2`–`10^3` metres | Familiar natural quantities measured as elevation above sea level. |
| Completed-building heights | 25 | `10^1`–`10^2` metres | Human-made quantities that overlap partly with mountains without duplicating their scale. |
| Dated national populations | 30 | `10^3`–`10^9` people | Most of the bank's order-of-magnitude range; prompts state the full reference date. |

This 25/25/30 split keeps all three ADR-approved subjects visible while populations provide the
wide scale range needed to exercise log-relative scoring. Within each category, selection cycles
through available base-10 magnitude bins before taking a second item from a bin. More familiar
subjects are preferred within a bin. The counts can be changed on the command line, but a released
bank should remain in the requested 60–100 range and should be manually reviewed.

## Generated prompt templates

The generator stores complete English prompts so reviewers see exactly what the app will display:

- mountain elevation: `What is the elevation of {subject} above sea level, in metres?`
- building height: `What is the architectural height of {subject}, in metres?`
- population: `According to {source}, what was the resident population of {subject} on {date}? Give your answer as a number of people.`

The population template uses a second sentence because ending the question with “in people” is
grammatical but mechanical. Country and selected building names receive a leading article where
English requires one. Dates use a fixed English day-month-year form rather than the development
machine's locale.

## Run it

From the repository root, use Python 3.10 or newer:

```shell
python3 -m pip install -r tools/requirements.txt
python3 tools/build_questions.py --dry-run
python3 tools/build_questions.py
```

The dry run performs the Wikidata queries and prints counts and magnitude distributions without
writing a file. A normal run writes `questions.json` in the repository root. Use `--output` to
choose another location. Candidate drops and their reasons are logged to standard error.

The generator identifies itself with a project-specific User-Agent, waits one second between
requests, honours numeric `Retry-After` responses, and retries temporary endpoint failures with
backoff. The delay can be increased with `--request-delay`; setting it to zero is mainly useful for
offline tests or a private endpoint.

The default category sizes are configurable:

```shell
python3 tools/build_questions.py --mountains 20 --buildings 20 --populations 30
```

`--generation-timestamp 2026-09-11T12:00:00Z` makes metadata reproducible for a controlled rebuild.
Ordinary runs record the current UTC timestamp. Questions are always ordered by category and
numeric Wikidata Q-ID, so source changes produce a focused diff rather than an arbitrary reshuffle.

## Manual prompt curation

`overrides.json` is the reviewed layer over generated source data. Keys are stable generated IDs.
An override may replace `prompt` or set `exclude` to `true`:

```json
{
  "wikidata-q513-mountains": {
    "prompt": "What is Mount Everest's elevation above sea level, in metres?"
  },
  "wikidata-q999999-buildings": {
    "exclude": true
  }
}
```

Unknown IDs and fields stop generation. A prompt override must retain the generated measurement
basis, unit, and applicable reference date. This catches stale IDs, spelling mistakes, and wording
that drifts away from the structured fields instead of silently ignoring intended edits. Overrides
cannot change values, units, categories, or source
links; changing those would disguise a data correction as copy editing.

## Selection and validation rules

The three queries deliberately select a conservative candidate pool:

- mountains must be direct instances of mountain with referenced elevation statements in metres;
- buildings must be completed, non-demolished skyscrapers with referenced height statements in
  metres;
- populations must describe sovereign states, use the latest non-future dated truthy statement
  from 2020 onward, carry a reference, and use people as the unit.

Preferred statements outrank normal statements. If equally ranked current statements conflict,
the item is dropped instead of choosing an apparent truth. Missing, non-finite, zero, negative,
unexpected-unit, out-of-range, future, and suspiciously round values are also dropped and logged.
The current broad plausibility ranges are 100–9,000 metres for mountains, 10–1,000 metres for
buildings, and 1,000–2,000,000,000 people for national populations. Integer mountain/building
values divisible by 100 and population values divisible by 100,000 are treated as suspiciously
coarse placeholders.

Wikimedia sitelink counts serve only as a familiarity signal. The global threshold rejects subjects
that are too obscure for a player to estimate meaningfully, and the selector prefers more familiar
subjects within each order-of-magnitude bucket. Sitelink counts are not emitted as question data or
interpreted as estimation difficulty.

Each output source link contains the Wikidata Q-ID, immutable revision number, and relevant
property anchor. This makes the value inspected for a released bank recoverable even after the
live item changes. The generated bank records Wikidata's dataset identity, its CC0 1.0 licence,
the exact generation timestamp, and the generator version.

## Test the generator

The offline unit tests use recorded SPARQL-result shapes and do not contact Wikidata:

```shell
python3 -m unittest tools/test_build_questions.py
```

These tests accompany the filtering and selection logic. A successful test does not replace the
manual source and wording review required by ADR 0011.

## Admission rules

Added after an external review of the first generated bank found six factually wrong
values among eighty, none of which the existing validation could detect: it verified that
each value matched its cited Wikidata statement, not that the statement was right.

Four rules now run before selection. Each logs its rejections with a reason, so
`--dry-run` shows what every rule costs.

| Rule | Flag | Purpose |
|---|---|---|
| Exclusion list | `--exclusions` | Honours `tools/exclusions.json`. Records cut by hand after review would otherwise return on the next run, silently breaking the reproducibility the pinned revisions provide. A listed id that no longer appears produces a warning, not an error — upstream data legitimately changes. |
| Familiarity floor | `--min-sitelinks` | Rejects subjects described in fewer than N Wikimedia language editions. This is a floor, not a difficulty rating: the review found such subjects yield recall questions, because the player cannot reason toward an answer from the prompt. |
| Competing values | `--competing-value-tolerance` | Rejects a subject whose own sources disagree about the value by more than a relative tolerance. Deprecated statements are ignored: a superseded value is not a live disagreement. |
| Location in the prompt | — | Resolves `P131`, falling back to `P17`, and names the place in the prompt. Removes a class of identity ambiguity: "Sugarloaf Mountain", "Corcovado" and "Freedom Tower" each denote more than one subject. |

### Why the competing-value rule reads claim documents rather than the query

The SPARQL queries return only referenced, non-deprecated statements. A competing value
that carries no reference is therefore invisible to them — which is exactly how the Monte
Titano disagreement (739 m against 756 m) reached the first bank. The rule fetches each
subject's full claim document from the MediaWiki API instead, so unreferenced
disagreements are visible.

### Calibration

The tolerance is set so that Monte Titano fails and Mount Everest and Mont Blanc pass.
Everest's published values differ by a few metres between surveys, and Mont Blanc's
snow-and-ice summit varies between measurements; neither is a source disagreement. Monte
Titano's two values differ by 2.3%, which is. The unit tests encode all three cases, so
changing the tolerance without re-reading them will fail the suite.
