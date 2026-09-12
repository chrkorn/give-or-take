#!/usr/bin/env python3
"""Build the Give or Take question bank from referenced Wikidata statements."""

from __future__ import annotations

import argparse
import collections
import dataclasses
import datetime as dt
import decimal
import json
import logging
import math
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


SCRIPT_VERSION = "3.0.0"
DEFAULT_ENDPOINT = "https://query.wikidata.org/sparql"
WIKIDATA_API = "https://www.wikidata.org/w/api.php"
DEFAULT_USER_AGENT = (
    "GiveOrTakeQuestionBuilder/1.0 "
    "(https://github.com/chrkorn/give-or-take; educational project)"
)
SOURCE_DATASETS = [
    "Wikidata main namespace (Q-item and revision identifiers recorded per question)"
]
LICENCE = (
    "Wikidata structured data — CC0 1.0 Universal "
    "(https://creativecommons.org/publicdomain/zero/1.0/)"
)
ENTITY_ID_PATTERN = re.compile(r"Q[1-9][0-9]*$")
DATE_PATTERN = re.compile(r"^[+]?([0-9]{4})-([0-9]{2})-([0-9]{2})T")
QUESTION_SOURCE_LABEL = "Wikidata"

# Admission rules added after an external review of the first generated bank.
# See ADR 0011 (amendments) and docs/question-bank-review-2026-09-12.md.

# Two values for the same property that differ by more than this relative amount are
# treated as an unresolved source disagreement, and the subject is rejected. Calibrated
# so Monte Titano (739 m against 756 m, 2.3% apart) fails while Mount Everest and
# Mont Blanc, whose published values differ by centimetres, survive.
COMPETING_VALUE_TOLERANCE = decimal.Decimal("0.005")

# Minimum number of Wikimedia sitelinks. This is a familiarity floor, not a difficulty
# rating: the review found that subjects described in only a handful of language editions
# produce recognition trivia, because the player cannot reason toward an answer from the
# prompt and can only recall the fact.
DEFAULT_MINIMUM_SITELINKS = 20

# Deprecated statements are excluded from conflict detection: a value the community has
# already marked as superseded is not evidence of a live disagreement.
DEPRECATED_RANK = "deprecated"

LOCATION_PROPERTIES = ("P131", "P17")
ENGLISH_MONTH_NAMES = (
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)
COUNTRY_NAMES_REQUIRING_ARTICLE = {
    "Bahamas",
    "Democratic Republic of the Congo",
    "Federated States of Micronesia",
    "Gambia",
    "Maldives",
    "Netherlands",
    "Philippines",
    "Republic of the Congo",
    "United Arab Emirates",
    "United Kingdom",
    "United States",
}
BUILDING_NAMES_REQUIRING_ARTICLE = {
    "Empire State Building",
}

COMMON_PREFIXES = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX p: <http://www.wikidata.org/prop/>
PREFIX ps: <http://www.wikidata.org/prop/statement/>
PREFIX psv: <http://www.wikidata.org/prop/statement/value/>
PREFIX pq: <http://www.wikidata.org/prop/qualifier/>
PREFIX wikibase: <http://wikiba.se/ontology#>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
"""

MOUNTAIN_QUERY = COMMON_PREFIXES + """
SELECT DISTINCT ?item ?itemLabel ?amount ?unit ?rank ?sitelinks WHERE {
  ?item wdt:P31 wd:Q8502;
        p:P2044 ?statement;
        wikibase:sitelinks ?sitelinks.
  ?statement ps:P2044 ?amount;
             psv:P2044 ?valueNode;
             wikibase:rank ?rank;
             prov:wasDerivedFrom ?reference.
  ?valueNode wikibase:quantityUnit ?unit.
  FILTER(?rank != wikibase:DeprecatedRank)
  FILTER(?unit = wd:Q11573)
  ?item rdfs:label ?itemLabel.
  FILTER(LANG(?itemLabel) = "en")
}
ORDER BY DESC(?sitelinks) ?item ?amount
LIMIT 400
"""

BUILDING_QUERY = COMMON_PREFIXES + """
SELECT DISTINCT ?item ?itemLabel ?amount ?unit ?rank ?sitelinks WHERE {
  ?item wdt:P31 wd:Q11303;
        wdt:P571 ?completionDate;
        p:P2048 ?statement;
        wikibase:sitelinks ?sitelinks.
  ?statement ps:P2048 ?amount;
             psv:P2048 ?valueNode;
             wikibase:rank ?rank;
             prov:wasDerivedFrom ?reference.
  ?valueNode wikibase:quantityUnit ?unit.
  FILTER(?rank != wikibase:DeprecatedRank)
  FILTER(?unit = wd:Q11573)
  FILTER NOT EXISTS { ?item wdt:P576 ?demolitionDate. }
  ?item rdfs:label ?itemLabel.
  FILTER(LANG(?itemLabel) = "en")
}
ORDER BY DESC(?sitelinks) ?item ?amount
LIMIT 400
"""

POPULATION_QUERY = COMMON_PREFIXES + """
SELECT DISTINCT ?item ?itemLabel ?amount ?unit ?rank ?pointInTime ?sitelinks WHERE {
  ?item wdt:P31 wd:Q3624078;
        wdt:P1082 ?amount;
        p:P1082 ?statement;
        wikibase:sitelinks ?sitelinks.
  ?statement ps:P1082 ?amount;
             psv:P1082 ?valueNode;
             pq:P585 ?pointInTime;
             wikibase:rank ?rank;
             prov:wasDerivedFrom ?reference.
  ?valueNode wikibase:quantityUnit ?unit.
  FILTER(?rank != wikibase:DeprecatedRank)
  FILTER(?pointInTime <= NOW())
  FILTER(YEAR(?pointInTime) >= 2020)
  FILTER(?unit = wd:Q199)
  ?item rdfs:label ?itemLabel.
  FILTER(LANG(?itemLabel) = "en")
}
ORDER BY DESC(?sitelinks) ?item ?amount
LIMIT 1200
"""


@dataclasses.dataclass(frozen=True)
class CategorySpec:
    """Configuration for one deliberately bounded question category."""

    key: str
    label: str
    property_id: str
    unit_qid: str
    output_unit: str
    measurement_basis: str
    time_varying: bool
    minimum: decimal.Decimal
    maximum: decimal.Decimal
    quota: int
    query: str


@dataclasses.dataclass(frozen=True)
class Candidate:
    """A validated candidate before the final Wikidata revision URL is attached."""

    identifier: str
    entity_id: str
    label: str
    value: decimal.Decimal
    as_of: dt.date | None
    category: str
    property_id: str
    unit: str
    measurement_basis: str
    source_label: str
    sitelinks: int
    location: str | None = None


CATEGORY_SPECS = (
    CategorySpec(
        key="mountains",
        label="Mountain elevations",
        property_id="P2044",
        unit_qid="Q11573",
        output_unit="metres",
        measurement_basis="elevation above sea level",
        time_varying=False,
        minimum=decimal.Decimal("100"),
        maximum=decimal.Decimal("9000"),
        quota=25,
        query=MOUNTAIN_QUERY,
    ),
    CategorySpec(
        key="buildings",
        label="Building heights",
        property_id="P2048",
        unit_qid="Q11573",
        output_unit="metres",
        measurement_basis="architectural height",
        time_varying=False,
        minimum=decimal.Decimal("10"),
        maximum=decimal.Decimal("1000"),
        quota=25,
        query=BUILDING_QUERY,
    ),
    CategorySpec(
        key="populations",
        label="National populations",
        property_id="P1082",
        unit_qid="Q199",
        output_unit="people",
        measurement_basis="resident population",
        time_varying=True,
        minimum=decimal.Decimal("1000"),
        maximum=decimal.Decimal("2000000000"),
        quota=30,
        query=POPULATION_QUERY,
    ),
)


class WikidataClient:
    """Small standard-library HTTP client with identification, pacing, and retries."""

    def __init__(self, user_agent: str, delay_seconds: float) -> None:
        self.user_agent = user_agent
        self.delay_seconds = delay_seconds
        self._last_request_at: float | None = None

    def query(self, endpoint: str, sparql: str) -> list[dict[str, Any]]:
        """Run one SPARQL query and return its result bindings."""
        body = urllib.parse.urlencode({"query": sparql, "format": "json"}).encode("utf-8")
        request = urllib.request.Request(
            endpoint,
            data=body,
            headers={
                "Accept": "application/sparql-results+json",
                "Content-Type": "application/x-www-form-urlencoded; charset=utf-8",
                "User-Agent": self.user_agent,
            },
            method="POST",
        )
        payload = self._request_json(request)
        try:
            return payload["results"]["bindings"]
        except (KeyError, TypeError) as exception:
            raise RuntimeError("WDQS returned an unexpected JSON document") from exception

    def revisions(self, entity_ids: Sequence[str]) -> dict[str, int]:
        """Fetch immutable revision numbers in small MediaWiki API batches."""
        revisions: dict[str, int] = {}
        for start in range(0, len(entity_ids), 50):
            batch = entity_ids[start : start + 50]
            query = urllib.parse.urlencode(
                {
                    "action": "query",
                    "prop": "info",
                    "titles": "|".join(batch),
                    "format": "json",
                    "formatversion": "2",
                }
            )
            request = urllib.request.Request(
                f"{WIKIDATA_API}?{query}",
                headers={"Accept": "application/json", "User-Agent": self.user_agent},
            )
            payload = self._request_json(request)
            for page in payload.get("query", {}).get("pages", []):
                title = page.get("title")
                revision = page.get("lastrevid")
                if isinstance(title, str) and isinstance(revision, int):
                    revisions[title] = revision
        missing = sorted(set(entity_ids) - revisions.keys(), key=entity_sort_key)
        if missing:
            raise RuntimeError("No Wikidata revision returned for: " + ", ".join(missing))
        return revisions

    def entity_claims(self, entity_ids: Sequence[str]) -> dict[str, dict[str, Any]]:
        """Fetch full claim lists so conflicts invisible to the query can be detected.

        The SPARQL query only returns referenced, non-deprecated statements. An item can
        still carry a competing value that is unreferenced, which is precisely how the
        Monte Titano disagreement reached the first bank unnoticed.
        """
        return self._entities(entity_ids, "claims")

    def entity_labels(self, entity_ids: Sequence[str]) -> dict[str, str]:
        """Resolve entity identifiers to English labels, for place names in prompts."""
        result: dict[str, str] = {}
        for entity_id, entity in self._entities(entity_ids, "labels").items():
            label = entity.get("labels", {}).get("en", {}).get("value")
            if isinstance(label, str) and label:
                result[entity_id] = label
        return result

    def _entities(self, entity_ids: Sequence[str], props: str) -> dict[str, dict[str, Any]]:
        entities: dict[str, dict[str, Any]] = {}
        unique = sorted(set(entity_ids), key=entity_sort_key)
        for start in range(0, len(unique), 50):
            batch = unique[start : start + 50]
            query = urllib.parse.urlencode(
                {
                    "action": "wbgetentities",
                    "props": props,
                    "ids": "|".join(batch),
                    "languages": "en",
                    "format": "json",
                    "formatversion": "2",
                }
            )
            request = urllib.request.Request(
                f"{WIKIDATA_API}?{query}",
                headers={"Accept": "application/json", "User-Agent": self.user_agent},
            )
            payload = self._request_json(request)
            for entity_id, entity in payload.get("entities", {}).items():
                if isinstance(entity, dict):
                    entities[entity_id] = entity
        return entities

    def _request_json(self, request: urllib.request.Request) -> dict[str, Any]:
        for attempt in range(4):
            self._pace()
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    return json.load(response)
            except urllib.error.HTTPError as exception:
                retryable = exception.code == 429 or 500 <= exception.code < 600
                if not retryable or attempt == 3:
                    raise RuntimeError(
                        f"Wikidata request failed with HTTP {exception.code}"
                    ) from exception
                retry_after = exception.headers.get("Retry-After")
                wait = parse_retry_after(retry_after, default=2**attempt)
                logging.warning("Wikidata asked us to retry; waiting %.1f seconds", wait)
                time.sleep(wait)
            except (urllib.error.URLError, TimeoutError, OSError) as exception:
                if attempt == 3:
                    raise RuntimeError("Could not reach Wikidata") from exception
                wait = float(2**attempt)
                logging.warning("Temporary network error; waiting %.1f seconds", wait)
                time.sleep(wait)
        raise AssertionError("retry loop must return or raise")

    def _pace(self) -> None:
        now = time.monotonic()
        if self._last_request_at is not None:
            remaining = self.delay_seconds - (now - self._last_request_at)
            if remaining > 0:
                time.sleep(remaining)
        self._last_request_at = time.monotonic()


def parse_retry_after(value: str | None, default: int) -> float:
    """Return a bounded delay for a numeric Retry-After header."""
    try:
        return min(60.0, max(0.0, float(value)))
    except (TypeError, ValueError):
        return min(60.0, float(default))


def entity_sort_key(entity_id: str) -> int:
    """Sort Q-identifiers numerically rather than lexicographically."""
    return int(entity_id[1:])


def binding_value(binding: Mapping[str, Any], name: str) -> str | None:
    """Read a SPARQL binding without assuming optional values are present."""
    value = binding.get(name, {}).get("value")
    return value if isinstance(value, str) and value.strip() else None


def qid_from_uri(uri: str | None) -> str | None:
    """Extract a Q-identifier from an entity URI."""
    if uri is None:
        return None
    candidate = uri.rstrip("/").rsplit("/", 1)[-1]
    return candidate if ENTITY_ID_PATTERN.fullmatch(candidate) else None


def rank_priority(rank_uri: str | None) -> int:
    """Map Wikidata rank URIs to a deterministic preference order."""
    if rank_uri and rank_uri.endswith("PreferredRank"):
        return 2
    if rank_uri and rank_uri.endswith("NormalRank"):
        return 1
    return 0


def date_from_wikidata_time(value: str | None) -> dt.date | None:
    """Extract a supported calendar date from a Wikidata date-time literal."""
    if value is None:
        return None
    match = DATE_PATTERN.match(value)
    if match is None:
        return None
    try:
        return dt.date(*(int(part) for part in match.groups()))
    except ValueError:
        return None


def statement_values(entity: Mapping[str, Any], property_id: str) -> list[decimal.Decimal]:
    """Collect every non-deprecated numeric value an entity records for one property."""
    values: list[decimal.Decimal] = []
    for statement in entity.get("claims", {}).get(property_id, []):
        if not isinstance(statement, Mapping):
            continue
        if statement.get("rank") == DEPRECATED_RANK:
            continue
        snak = statement.get("mainsnak")
        if not isinstance(snak, Mapping) or snak.get("snaktype") != "value":
            continue
        amount = (snak.get("datavalue") or {}).get("value", {})
        if not isinstance(amount, Mapping):
            continue
        text = amount.get("amount")
        if not isinstance(text, str):
            continue
        try:
            parsed = decimal.Decimal(text)
        except decimal.InvalidOperation:
            continue
        if parsed.is_finite():
            values.append(parsed)
    return values


def competing_values(
    entity: Mapping[str, Any],
    property_id: str,
    tolerance: decimal.Decimal = COMPETING_VALUE_TOLERANCE,
) -> tuple[decimal.Decimal, decimal.Decimal] | None:
    """Return the widest disagreeing pair of values, or None when the sources agree.

    A single-answer quiz cannot use a quantity its own sources dispute: two different
    answers would both be defensible, and the player is penalised for the disagreement.
    """
    values = statement_values(entity, property_id)
    if len(values) < 2:
        return None
    lowest, highest = min(values), max(values)
    scale = max(abs(lowest), abs(highest))
    if scale == 0:
        return None
    if (highest - lowest) / scale > tolerance:
        return (lowest, highest)
    return None


def location_entity_id(entity: Mapping[str, Any]) -> str | None:
    """Find the place an item belongs to, preferring the narrower administrative unit."""
    claims = entity.get("claims", {})
    for property_id in LOCATION_PROPERTIES:
        for statement in claims.get(property_id, []):
            if not isinstance(statement, Mapping):
                continue
            if statement.get("rank") == DEPRECATED_RANK:
                continue
            snak = statement.get("mainsnak")
            if not isinstance(snak, Mapping) or snak.get("snaktype") != "value":
                continue
            value = (snak.get("datavalue") or {}).get("value", {})
            if isinstance(value, Mapping) and isinstance(value.get("id"), str):
                return value["id"]
    return None


def is_suspiciously_round(value: decimal.Decimal, category_key: str) -> bool:
    """Flag coarse values likely to be placeholders rather than measurements."""
    if value != value.to_integral_value():
        return False
    digits = str(abs(int(value)))
    trailing_zeroes = len(digits) - len(digits.rstrip("0"))
    if category_key == "populations":
        return trailing_zeroes >= 5
    return int(value) >= 100 and trailing_zeroes >= 2


def prompt_for(
    spec: CategorySpec,
    label: str,
    as_of: dt.date | None,
    source_label: str,
    location: str | None = None,
) -> str:
    """Render category-specific wording that also states necessary qualifiers.

    The location is part of the identification, not decoration. Several names in the
    first bank denoted more than one subject: "Sugarloaf Mountain", "Corcovado", and
    "Freedom Tower", which is widely used for One World Trade Center but described the
    Miami building. Naming the place removes that ambiguity mechanically.
    """
    if spec.key == "mountains":
        place = f" in {location}" if location else ""
        return f"What is the elevation of {label}{place} above sea level, in metres?"
    if spec.key == "buildings":
        display_name = (
            f"the {label}" if label in BUILDING_NAMES_REQUIRING_ARTICLE else label
        )
        place = f" in {location}" if location else ""
        return f"What is the architectural height of {display_name}{place}, in metres?"
    if as_of is None:
        raise ValueError("population prompt requires a reference date")
    display_name = f"the {label}" if label in COUNTRY_NAMES_REQUIRING_ARTICLE else label
    display_date = display_date_in_english(as_of)
    return (
        f"According to {source_label}, what was the resident population of "
        f"{display_name} on {display_date}? Give your answer as a number of people."
    )


def display_date_in_english(value: dt.date) -> str:
    """Format a stable English date without depending on the machine locale."""
    return f"{value.day} {ENGLISH_MONTH_NAMES[value.month - 1]} {value.year}"


def candidate_id(spec: CategorySpec, entity_id: str) -> str:
    """Build identity from source identity, not mutable wording or values."""
    return f"wikidata-{entity_id.lower()}-{spec.key}"


def candidates_from_bindings(
    spec: CategorySpec,
    bindings: Iterable[Mapping[str, Any]],
    current_year: int,
) -> list[Candidate]:
    """Validate, resolve, and deduplicate one category's raw query rows."""
    valid_rows: list[dict[str, Any]] = []
    for index, binding in enumerate(bindings):
        entity_id = qid_from_uri(binding_value(binding, "item"))
        subject = entity_id or f"row {index + 1}"
        label = binding_value(binding, "itemLabel")
        amount_text = binding_value(binding, "amount")
        unit_qid = qid_from_uri(binding_value(binding, "unit"))
        rank = rank_priority(binding_value(binding, "rank"))
        sitelinks_text = binding_value(binding, "sitelinks")
        as_of = date_from_wikidata_time(binding_value(binding, "pointInTime"))

        reason: str | None = None
        amount: decimal.Decimal | None = None
        sitelinks = 0
        if entity_id is None:
            reason = "missing or malformed item identifier"
        elif label is None:
            reason = "missing English label"
        elif amount_text is None:
            reason = "missing value"
        else:
            try:
                amount = decimal.Decimal(amount_text)
            except decimal.InvalidOperation:
                reason = f"non-numeric value {amount_text!r}"
        if reason is None and (amount is None or not amount.is_finite()):
            reason = "non-finite value"
        elif reason is None and amount <= 0:
            reason = "zero or negative value"
        elif reason is None and unit_qid != spec.unit_qid:
            reason = f"unexpected unit {unit_qid or 'missing'}"
        elif reason is None and not spec.minimum <= amount <= spec.maximum:
            reason = f"value {amount} outside [{spec.minimum}, {spec.maximum}]"
        elif reason is None and is_suspiciously_round(amount, spec.key):
            reason = f"suspiciously round value {amount}"
        elif reason is None and rank == 0:
            reason = "missing supported statement rank"
        elif reason is None and spec.time_varying and as_of is None:
            reason = "missing point-in-time date"
        elif reason is None and as_of is not None and as_of.year > current_year:
            reason = f"future population date {as_of.isoformat()}"

        if reason is not None:
            logging.info("DROP %s %s: %s", spec.key, subject, reason)
            continue

        try:
            sitelinks = max(0, int(sitelinks_text or "0"))
        except ValueError:
            logging.info("DROP %s %s: malformed sitelink count", spec.key, subject)
            continue
        valid_rows.append(
            {
                "entity_id": entity_id,
                "label": label,
                "amount": amount,
                "as_of": as_of,
                "rank": rank,
                "sitelinks": sitelinks,
            }
        )

    grouped: dict[str, list[dict[str, Any]]] = collections.defaultdict(list)
    for row in valid_rows:
        grouped[row["entity_id"]].append(row)

    candidates: list[Candidate] = []
    for entity_id in sorted(grouped, key=entity_sort_key):
        rows = grouped[entity_id]
        best_rank = max(row["rank"] for row in rows)
        rows = [row for row in rows if row["rank"] == best_rank]
        if spec.time_varying:
            latest_date = max(row["as_of"] for row in rows)
            rows = [row for row in rows if row["as_of"] == latest_date]
        values = {row["amount"] for row in rows}
        if len(values) != 1:
            logging.info(
                "DROP %s %s: conflicting best-ranked values %s",
                spec.key,
                entity_id,
                ", ".join(str(value) for value in sorted(values)),
            )
            continue
        row = max(rows, key=lambda item: (item["sitelinks"], item["label"]))
        candidates.append(
            Candidate(
                identifier=candidate_id(spec, entity_id),
                entity_id=entity_id,
                label=row["label"],
                value=row["amount"],
                as_of=row["as_of"],
                category=spec.label,
                property_id=spec.property_id,
                unit=spec.output_unit,
                measurement_basis=spec.measurement_basis,
                source_label=QUESTION_SOURCE_LABEL,
                sitelinks=row["sitelinks"],
            )
        )
    return candidates


def load_exclusions(path: Path) -> dict[str, str]:
    """Load the editorial exclusion list so regeneration reproduces the shipped bank.

    Records removed by hand after review would otherwise return on the next run, which
    silently breaks the reproducibility the pinned revisions are meant to provide.
    """
    if not path.exists():
        return {}
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"{path} must contain a JSON object")
    entries = document.get("excluded", [])
    if not isinstance(entries, list):
        raise ValueError(f"{path} 'excluded' must be an array")
    exclusions: dict[str, str] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError(f"{path} entries must be objects")
        identifier = entry.get("id")
        reason = entry.get("reason")
        if not isinstance(identifier, str) or not identifier:
            raise ValueError(f"{path} entry is missing a string id")
        if not isinstance(reason, str) or not reason:
            raise ValueError(f"{path} entry {identifier!r} is missing a reason")
        if identifier in exclusions:
            raise ValueError(f"{path} lists {identifier!r} twice")
        exclusions[identifier] = reason
    return exclusions


def apply_exclusions(
    candidates: Sequence[Candidate], exclusions: Mapping[str, str]
) -> list[Candidate]:
    """Drop excluded records and warn about entries the source no longer produces.

    A stale entry is a warning rather than an error: upstream data legitimately changes,
    and failing the build because a rejected subject disappeared would be perverse. It is
    still worth surfacing, because it may equally mean an id was mistyped.
    """
    kept: list[Candidate] = []
    seen: set[str] = set()
    for candidate in candidates:
        reason = exclusions.get(candidate.identifier)
        if reason is None:
            kept.append(candidate)
            continue
        seen.add(candidate.identifier)
        logging.info("DROP %s: excluded by review — %s", candidate.identifier, reason)
    for identifier in sorted(set(exclusions) - seen):
        logging.warning(
            "exclusion %s matched no candidate; the source may have changed or the id "
            "may be wrong",
            identifier,
        )
    return kept


def apply_familiarity_floor(
    candidates: Sequence[Candidate], minimum_sitelinks: int
) -> list[Candidate]:
    """Reject subjects too obscure to estimate rather than merely recall."""
    kept: list[Candidate] = []
    for candidate in candidates:
        if candidate.sitelinks < minimum_sitelinks:
            logging.info(
                "DROP %s: %d sitelinks is below the familiarity floor of %d",
                candidate.identifier,
                candidate.sitelinks,
                minimum_sitelinks,
            )
            continue
        kept.append(candidate)
    return kept


def apply_competing_value_rule(
    candidates: Sequence[Candidate],
    entities: Mapping[str, Mapping[str, Any]],
    tolerance: decimal.Decimal = COMPETING_VALUE_TOLERANCE,
) -> list[Candidate]:
    """Reject subjects whose own sources disagree about the value."""
    kept: list[Candidate] = []
    for candidate in candidates:
        entity = entities.get(candidate.entity_id)
        if entity is None:
            logging.warning(
                "no claim document for %s; competing-value rule not applied",
                candidate.identifier,
            )
            kept.append(candidate)
            continue
        conflict = competing_values(entity, candidate.property_id, tolerance)
        if conflict is not None:
            lowest, highest = conflict
            logging.info(
                "DROP %s: competing values %s and %s (%.2f%% apart)",
                candidate.identifier,
                lowest,
                highest,
                float((highest - lowest) / max(abs(lowest), abs(highest)) * 100),
            )
            continue
        kept.append(candidate)
    return kept


def attach_locations(
    candidates: Sequence[Candidate],
    entities: Mapping[str, Mapping[str, Any]],
    place_labels: Mapping[str, str],
) -> list[Candidate]:
    """Attach a place name where one resolves, leaving the prompt unchanged otherwise."""
    result: list[Candidate] = []
    for candidate in candidates:
        entity = entities.get(candidate.entity_id) or {}
        place_id = location_entity_id(entity)
        location = place_labels.get(place_id) if place_id else None
        result.append(dataclasses.replace(candidate, location=location))
    return result


def load_overrides(path: Path) -> dict[str, dict[str, Any]]:
    """Load and strictly validate the small human-curation layer."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exception:
        raise ValueError(f"override file does not exist: {path}") from exception
    except json.JSONDecodeError as exception:
        raise ValueError(f"invalid JSON in {path}: {exception}") from exception
    if not isinstance(document, dict):
        raise ValueError("override file must contain a JSON object")
    allowed = {"prompt", "exclude"}
    result: dict[str, dict[str, Any]] = {}
    for identifier, override in document.items():
        if not isinstance(identifier, str) or not identifier.strip():
            raise ValueError("override IDs must be non-blank strings")
        if not isinstance(override, dict):
            raise ValueError(f"override {identifier!r} must be an object")
        unknown = set(override) - allowed
        if unknown:
            raise ValueError(
                f"override {identifier!r} has unknown fields: {', '.join(sorted(unknown))}"
            )
        if "prompt" in override and (
            not isinstance(override["prompt"], str) or not override["prompt"].strip()
        ):
            raise ValueError(f"override {identifier!r} prompt must be non-blank")
        if "exclude" in override and type(override["exclude"]) is not bool:
            raise ValueError(f"override {identifier!r} exclude must be a boolean")
        result[identifier] = override
    return result


def apply_overrides(
    candidates: Sequence[Candidate], overrides: Mapping[str, Mapping[str, Any]]
) -> tuple[list[Candidate], dict[str, str]]:
    """Apply curation without permitting silent edits to sourced values."""
    known_ids = {candidate.identifier for candidate in candidates}
    unused = sorted(set(overrides) - known_ids)
    if unused:
        raise ValueError("override IDs did not match a candidate: " + ", ".join(unused))

    retained: list[Candidate] = []
    prompts: dict[str, str] = {}
    for candidate in candidates:
        override = overrides.get(candidate.identifier, {})
        if override.get("exclude", False):
            logging.info("DROP %s: excluded by manual override", candidate.identifier)
            continue
        retained.append(candidate)
        prompt = override.get("prompt", prompt_for_spec_candidate(candidate))
        validate_prompt_context(candidate, prompt)
        prompts[candidate.identifier] = prompt
    return retained, prompts


def prompt_for_spec_candidate(candidate: Candidate) -> str:
    """Render a prompt after resolving a candidate back to its category."""
    spec = next(spec for spec in CATEGORY_SPECS if spec.label == candidate.category)
    return prompt_for(
        spec,
        candidate.label,
        candidate.as_of,
        candidate.source_label,
        candidate.location,
    )


def validate_prompt_context(candidate: Candidate, prompt: str) -> None:
    """Reject curated wording that drops structured measurement context."""
    prompt_words = set(re.findall(r"[a-z]+", prompt.casefold()))
    required_words = set(re.findall(
        r"[a-z]+",
        f"{candidate.measurement_basis} {candidate.unit}".casefold(),
    ))
    missing_words = sorted(required_words - prompt_words)
    if missing_words:
        raise ValueError(
            f"prompt override for {candidate.identifier!r} omits measurement context: "
            + ", ".join(missing_words)
        )
    if candidate.as_of is not None:
        display_date = display_date_in_english(candidate.as_of)
        if display_date.casefold() not in prompt.casefold():
            raise ValueError(
                f"prompt override for {candidate.identifier!r} omits date {display_date}"
            )
        if candidate.source_label.casefold() not in prompt.casefold():
            raise ValueError(
                f"prompt override for {candidate.identifier!r} omits source "
                f"{candidate.source_label}"
            )


def select_balanced(candidates: Sequence[Candidate], quota: int) -> list[Candidate]:
    """Select familiar subjects round-robin across available orders of magnitude."""
    buckets: dict[int, list[Candidate]] = collections.defaultdict(list)
    for candidate in candidates:
        magnitude = math.floor(math.log10(float(candidate.value)))
        buckets[magnitude].append(candidate)
    for bucket in buckets.values():
        bucket.sort(key=lambda item: (-item.sitelinks, entity_sort_key(item.entity_id)))

    selected: list[Candidate] = []
    magnitudes = sorted(buckets)
    while len(selected) < quota and magnitudes:
        next_magnitudes: list[int] = []
        for magnitude in magnitudes:
            if len(selected) >= quota:
                next_magnitudes.append(magnitude)
                continue
            bucket = buckets[magnitude]
            if bucket:
                selected.append(bucket.pop(0))
            if bucket:
                next_magnitudes.append(magnitude)
        magnitudes = next_magnitudes

    selected_ids = {candidate.identifier for candidate in selected}
    for candidate in candidates:
        if candidate.identifier not in selected_ids:
            logging.info("DROP %s: category quota of %d reached", candidate.identifier, quota)
    return selected


def json_number(value: decimal.Decimal) -> int | float:
    """Convert an exact source decimal to an unquoted JSON number."""
    if value == value.to_integral_value():
        return int(value)
    converted = float(value)
    if not math.isfinite(converted):
        raise ValueError(f"value cannot be represented as a finite JSON number: {value}")
    return converted


def question_document(
    candidates: Sequence[Candidate],
    prompts: Mapping[str, str],
    revisions: Mapping[str, int],
    generation_timestamp: str,
) -> dict[str, Any]:
    """Create the exact strict version-3 JSON shape consumed by the app."""
    questions = []
    category_order = {spec.label: index for index, spec in enumerate(CATEGORY_SPECS)}
    ordered = sorted(
        candidates,
        key=lambda item: (category_order[item.category], entity_sort_key(item.entity_id)),
    )
    for candidate in ordered:
        revision = revisions[candidate.entity_id]
        source_url = (
            "https://www.wikidata.org/w/index.php?"
            + urllib.parse.urlencode(
                {"title": candidate.entity_id, "oldid": str(revision)}
            )
            + f"#{candidate.property_id}"
        )
        question = {
            "id": candidate.identifier,
            "prompt": prompts[candidate.identifier],
            "trueValue": json_number(candidate.value),
            "unit": candidate.unit,
            "measurementBasis": candidate.measurement_basis,
            "category": candidate.category,
            "sourceUrl": source_url,
            "sourceLabel": candidate.source_label,
        }
        if candidate.as_of is not None:
            question["asOf"] = candidate.as_of.isoformat()
        questions.append(question)
    return {
        "version": 3,
        "metadata": {
            "generationDate": generation_timestamp[:10],
            "generationTimestamp": generation_timestamp,
            "sourceDatasets": SOURCE_DATASETS,
            "licence": LICENCE,
            "scriptVersion": SCRIPT_VERSION,
        },
        "timeVaryingCategories": [
            spec.label for spec in CATEGORY_SPECS if spec.time_varying
        ],
        "questions": questions,
    }


def print_statistics(candidates: Sequence[Candidate]) -> None:
    """Print category counts and logarithmic magnitude bins."""
    print(f"Questions: {len(candidates)}")
    by_category: dict[str, list[Candidate]] = collections.defaultdict(list)
    for candidate in candidates:
        by_category[candidate.category].append(candidate)
    for spec in CATEGORY_SPECS:
        category_candidates = by_category[spec.label]
        print(f"  {spec.label}: {len(category_candidates)}")
        magnitudes = collections.Counter(
            math.floor(math.log10(float(candidate.value)))
            for candidate in category_candidates
        )
        for exponent in sorted(magnitudes):
            print(f"    10^{exponent} to <10^{exponent + 1}: {magnitudes[exponent]}")


def utc_timestamp(value: str | None) -> str:
    """Validate an optional reproducibility timestamp or return the current UTC time."""
    if value is None:
        return (
            dt.datetime.now(dt.timezone.utc)
            .replace(microsecond=0)
            .isoformat()
            .replace("+00:00", "Z")
        )
    if not value.endswith("Z"):
        raise ValueError("generation timestamp must be UTC and end in Z")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exception:
        raise ValueError("generation timestamp must be ISO 8601") from exception
    if parsed.tzinfo != dt.timezone.utc:
        raise ValueError("generation timestamp must be UTC")
    return parsed.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    """Parse command-line arguments while keeping repository-relative defaults stable."""
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="print statistics without writing")
    parser.add_argument("--output", type=Path, default=repository / "questions.json")
    parser.add_argument(
        "--exclusions",
        type=Path,
        default=repository / "tools" / "exclusions.json",
        help="editorial exclusion list; honoured so regeneration reproduces the bank",
    )
    parser.add_argument(
        "--min-sitelinks",
        type=int,
        default=DEFAULT_MINIMUM_SITELINKS,
        help=(
            "familiarity floor: reject subjects described in fewer Wikimedia language "
            "editions, because they yield recall questions rather than estimation ones"
        ),
    )
    parser.add_argument(
        "--competing-value-tolerance",
        type=decimal.Decimal,
        default=COMPETING_VALUE_TOLERANCE,
        help="relative difference above which two source values count as disagreeing",
    )
    parser.add_argument("--overrides", type=Path, default=repository / "tools" / "overrides.json")
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    parser.add_argument("--request-delay", type=float, default=1.0)
    parser.add_argument("--mountains", type=int, default=25, metavar="COUNT")
    parser.add_argument("--buildings", type=int, default=25, metavar="COUNT")
    parser.add_argument("--populations", type=int, default=30, metavar="COUNT")
    parser.add_argument(
        "--generation-timestamp",
        help="fixed UTC timestamp for reproducible output (for example 2026-09-11T12:00:00Z)",
    )
    args = parser.parse_args(argv)
    if args.request_delay < 0:
        parser.error("--request-delay must not be negative")
    for name in ("mountains", "buildings", "populations"):
        if not 0 <= getattr(args, name) <= 100:
            parser.error(f"--{name} must be between 0 and 100")
    return args


def run(argv: Sequence[str]) -> int:
    """Build, report, and optionally write a curated question bank."""
    args = parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        generated_at = utc_timestamp(args.generation_timestamp)
        overrides = load_overrides(args.overrides)
        exclusions = load_exclusions(args.exclusions)
        client = WikidataClient(args.user_agent, args.request_delay)
        requested = {
            "mountains": args.mountains,
            "buildings": args.buildings,
            "populations": args.populations,
        }
        all_candidates: list[Candidate] = []
        for spec in CATEGORY_SPECS:
            logging.info("Querying Wikidata for %s", spec.label.lower())
            bindings = client.query(args.endpoint, spec.query)
            category_candidates = candidates_from_bindings(
                spec, bindings, int(generated_at[:4])
            )
            all_candidates.extend(category_candidates)

        # Admission rules, cheapest first so the network is spared what it can be.
        before_rules = len(all_candidates)
        all_candidates = apply_exclusions(all_candidates, exclusions)
        after_exclusions = len(all_candidates)
        all_candidates = apply_familiarity_floor(all_candidates, args.min_sitelinks)
        after_floor = len(all_candidates)

        entity_ids = sorted({item.entity_id for item in all_candidates}, key=entity_sort_key)
        logging.info("Fetching claim documents for %d subjects", len(entity_ids))
        entities = client.entity_claims(entity_ids)
        all_candidates = apply_competing_value_rule(
            all_candidates, entities, args.competing_value_tolerance
        )
        after_competing = len(all_candidates)

        place_ids = sorted(
            {
                place_id
                for item in all_candidates
                if (place_id := location_entity_id(entities.get(item.entity_id) or {}))
            },
            key=entity_sort_key,
        )
        place_labels = client.entity_labels(place_ids) if place_ids else {}
        all_candidates = attach_locations(all_candidates, entities, place_labels)

        logging.info(
            "Admission rules: %d candidates -> %d after exclusions -> %d after the "
            "familiarity floor -> %d after the competing-value rule",
            before_rules,
            after_exclusions,
            after_floor,
            after_competing,
        )

        curated, prompts = apply_overrides(all_candidates, overrides)
        selected: list[Candidate] = []
        for spec in CATEGORY_SPECS:
            category_candidates = [item for item in curated if item.category == spec.label]
            selected.extend(select_balanced(category_candidates, requested[spec.key]))
        print_statistics(selected)

        if args.dry_run:
            logging.info("Dry run: %s was not written", args.output)
            return 0
        selected_entity_ids = sorted(
            {item.entity_id for item in selected}, key=entity_sort_key
        )
        revisions = client.revisions(selected_entity_ids)
        document = question_document(selected, prompts, revisions, generated_at)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(document, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        logging.info("Wrote %d questions to %s", len(selected), args.output)
        return 0
    except (OSError, RuntimeError, ValueError) as exception:
        logging.error("%s", exception)
        return 1


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
