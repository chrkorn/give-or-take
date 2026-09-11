# Question-bank generator

`build_questions.py` creates the version-1 `questions.json` consumed by Give or Take. It queries
referenced, non-deprecated Wikidata statements selected under ADR 0011. It uses only the Python 3
standard library; `requirements.txt` exists to make that absence of third-party dependencies
explicit.

## Recommended bank shape

The defaults generate 80 questions:

| Category | Default | Typical magnitude | Why it is included |
|---|---:|---:|---|
| Mountain elevations | 25 | `10^2`–`10^3` metres | Familiar natural quantities, including regional and famous peaks. |
| Completed-building heights | 25 | `10^1`–`10^2` metres | Human-made quantities that overlap partly with mountains without duplicating their scale. |
| Dated national populations | 30 | `10^3`–`10^9` people | Most of the bank's order-of-magnitude range; prompts state the source year. |

This 25/25/30 split keeps all three ADR-approved subjects visible while populations provide the
wide scale range needed to exercise log-relative scoring. Within each category, selection cycles
through available base-10 magnitude bins before taking a second item from a bin. More familiar
subjects are preferred within a bin. The counts can be changed on the command line, but a released
bank should remain in the requested 60–100 range and should be manually reviewed.

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
An override may replace `prompt`, set `difficulty` from 1 through 5, or set `exclude` to `true`:

```json
{
  "wikidata-q513-mountains": {
    "prompt": "How high is Mount Everest above sea level?",
    "difficulty": 1
  },
  "wikidata-q999999-buildings": {
    "exclude": true
  }
}
```

Unknown IDs and fields stop generation. This catches stale IDs and spelling mistakes instead of
silently ignoring intended edits. Overrides cannot change values, units, categories, or source
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

Difficulty is a reproducible estimate of likely familiarity, not an empirical measurement of
estimation error. Within each selected category, Wikimedia sitelink counts place subjects into five
relative familiarity bands: widely documented subjects receive level 1 and less documented ones
receive level 5. This gives each category a useful spread without pretending that an arbitrary
global sitelink threshold is universal. Manual review can correct the proxy through
`overrides.json`.

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
