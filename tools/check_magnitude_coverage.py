#!/usr/bin/env python3
"""Print the shipped bank's magnitude matrix and enforce its non-regression ratchet."""

from __future__ import annotations

import argparse
import decimal
import json
import sys
from pathlib import Path
from typing import Any, Mapping, Sequence

from tools import build_questions


def load_records(path: Path) -> list[build_questions.MagnitudeRecord]:
    """Load only the fields needed for the magnitude-coverage assessment."""
    document = json.loads(path.read_text(encoding="utf-8"))
    questions = document.get("questions") if isinstance(document, Mapping) else None
    if not isinstance(questions, list) or not questions:
        raise ValueError(f"{path} must contain a non-empty questions array")
    records: list[build_questions.MagnitudeRecord] = []
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, Mapping):
            raise ValueError(f"question {index} must be an object")
        identifier = question.get("id")
        category = question.get("category")
        value = question.get("trueValue")
        if not isinstance(identifier, str) or not identifier:
            raise ValueError(f"question {index} has no string id")
        if not isinstance(category, str) or not category:
            raise ValueError(f"question {identifier!r} has no string category")
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError(f"question {identifier!r} has no numeric trueValue")
        records.append(build_questions.MagnitudeRecord(
            identifier=identifier,
            category=category,
            value=decimal.Decimal(str(value)),
        ))
    return records


def positive_integer(document: Mapping[str, Any], name: str, allow_zero: bool) -> int:
    """Read one bounded integer from the deliberately small ratchet document."""
    value = document.get(name)
    minimum = 0 if allow_zero else 1
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ValueError(f"ratchet field {name!r} must be an integer >= {minimum}")
    return value


def load_ratchet(path: Path) -> build_questions.MagnitudeCoverageRatchet:
    """Load and strictly validate the accepted non-regression thresholds."""
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, Mapping):
        raise ValueError(f"{path} must contain an object")
    expected = {
        "minimumTestableShareNumerator",
        "minimumTestableShareDenominator",
        "minimumCompliantBands",
        "maximumInsufficientOverlapBands",
        "maximumDominatedBands",
        "maximumBroadCategoryFailures",
    }
    unknown = sorted(set(document) - expected)
    missing = sorted(expected - set(document))
    if unknown or missing:
        details = []
        if unknown:
            details.append("unknown fields: " + ", ".join(unknown))
        if missing:
            details.append("missing fields: " + ", ".join(missing))
        raise ValueError(f"invalid ratchet document ({'; '.join(details)})")
    return build_questions.MagnitudeCoverageRatchet(
        minimum_testable_share_numerator=positive_integer(
            document, "minimumTestableShareNumerator", False
        ),
        minimum_testable_share_denominator=positive_integer(
            document, "minimumTestableShareDenominator", False
        ),
        minimum_compliant_bands=positive_integer(
            document, "minimumCompliantBands", True
        ),
        maximum_insufficient_overlap_bands=positive_integer(
            document, "maximumInsufficientOverlapBands", True
        ),
        maximum_dominated_bands=positive_integer(
            document, "maximumDominatedBands", True
        ),
        maximum_broad_category_failures=positive_integer(
            document, "maximumBroadCategoryFailures", True
        ),
    )


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    """Parse paths while keeping repository defaults independent of the current directory."""
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bank", nargs="?", type=Path, default=repository / "questions.json")
    parser.add_argument(
        "--ratchet",
        type=Path,
        default=repository / "tools" / "magnitude-coverage-ratchet.json",
    )
    return parser.parse_args(argv)


def run(argv: Sequence[str]) -> int:
    """Print all evidence, then return failure only when coverage regresses."""
    args = parse_args(argv)
    try:
        assessment = build_questions.assess_magnitude_coverage(load_records(args.bank))
        print(build_questions.format_magnitude_coverage(assessment))
        if assessment.violations:
            print("Full-repair status: known limitation")
            for violation in assessment.violations:
                print(f"  - {violation}")
        else:
            print("Full-repair status: achieved")
        build_questions.validate_magnitude_coverage_ratchet(
            assessment, load_ratchet(args.ratchet)
        )
        print("Ratchet status: no regression")
        return 0
    except (OSError, json.JSONDecodeError, ValueError) as exception:
        print(f"ERROR {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
