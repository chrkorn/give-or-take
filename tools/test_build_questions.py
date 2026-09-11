"""Unit tests for the deterministic parts of the question-bank generator."""

import decimal
import unittest

from tools import build_questions


def binding(
    entity_id="Q42",
    label="Example Mountain",
    amount="1234",
    unit="Q11573",
    rank="NormalRank",
    sitelinks="80",
    year=None,
):
    """Create the small SPARQL JSON shape used by generator tests."""
    result = {
        "item": {"value": f"http://www.wikidata.org/entity/{entity_id}"},
        "itemLabel": {"value": label},
        "amount": {"value": amount},
        "unit": {"value": f"http://www.wikidata.org/entity/{unit}"},
        "rank": {"value": f"http://wikiba.se/ontology#{rank}"},
        "sitelinks": {"value": sitelinks},
    }
    if year is not None:
        result["pointInTime"] = {"value": f"{year:04d}-01-01T00:00:00Z"}
    return result


class BuildQuestionsTest(unittest.TestCase):
    """Exercise filtering, curation, selection, and JSON construction offline."""

    def test_candidates_filter_invalid_values_and_keep_valid_value(self):
        spec = build_questions.CATEGORY_SPECS[0]
        rows = [
            binding(entity_id="Q1", amount="0"),
            binding(entity_id="Q2", amount="-12"),
            binding(entity_id="Q3", amount="9500"),
            binding(entity_id="Q4", amount="1200"),
            binding(entity_id="Q5", amount="1234"),
        ]

        with self.assertLogs(level="INFO") as logs:
            candidates = build_questions.candidates_from_bindings(spec, rows, 2026)

        self.assertEqual(["wikidata-q5-mountains"], [item.identifier for item in candidates])
        messages = "\n".join(logs.output)
        self.assertIn("zero or negative value", messages)
        self.assertIn("outside [100, 9000]", messages)
        self.assertIn("suspiciously round value 1200", messages)

    def test_candidates_drop_conflicting_best_ranked_values(self):
        spec = build_questions.CATEGORY_SPECS[1]
        rows = [
            binding(entity_id="Q10", label="Example Tower", amount="321"),
            binding(entity_id="Q10", label="Example Tower", amount="322"),
        ]

        with self.assertLogs(level="INFO") as logs:
            candidates = build_questions.candidates_from_bindings(spec, rows, 2026)

        self.assertEqual([], candidates)
        self.assertIn("conflicting best-ranked values 321, 322", "\n".join(logs.output))

    def test_population_uses_latest_best_ranked_year(self):
        spec = build_questions.CATEGORY_SPECS[2]
        rows = [
            binding(
                entity_id="Q20", label="Exampleland", amount="1234567",
                unit="Q199", year=2022
            ),
            binding(
                entity_id="Q20", label="Exampleland", amount="1239876",
                unit="Q199", year=2024
            ),
        ]

        candidates = build_questions.candidates_from_bindings(spec, rows, 2026)

        self.assertEqual(1, len(candidates))
        self.assertEqual(2024, candidates[0].year)
        self.assertEqual(decimal.Decimal("1239876"), candidates[0].value)

    def test_apply_overrides_changes_only_curatorial_fields(self):
        candidate = candidate_for("Q30", decimal.Decimal("456"), "Mountain elevations")
        overrides = {
            candidate.identifier: {
                "prompt": "What is Example's surveyed elevation?",
                "difficulty": 5,
            }
        }

        curated, prompts = build_questions.apply_overrides([candidate], overrides)

        self.assertEqual(decimal.Decimal("456"), curated[0].value)
        self.assertEqual(5, curated[0].difficulty)
        self.assertEqual("What is Example's surveyed elevation?", prompts[candidate.identifier])

    def test_select_balanced_round_robins_across_magnitudes(self):
        candidates = [
            candidate_for("Q1", decimal.Decimal("11"), "Building heights"),
            candidate_for("Q2", decimal.Decimal("12"), "Building heights"),
            candidate_for("Q3", decimal.Decimal("101"), "Building heights"),
            candidate_for("Q4", decimal.Decimal("102"), "Building heights"),
        ]

        with self.assertLogs(level="INFO"):
            selected = build_questions.select_balanced(candidates, 2)

        self.assertEqual({"Q1", "Q3"}, {item.entity_id for item in selected})

    def test_assign_relative_difficulties_spreads_category_over_five_levels(self):
        candidates = [
            build_questions.Candidate(
                **{
                    **candidate_for(
                        f"Q{index}", decimal.Decimal(str(100 + index)), "Building heights"
                    ).__dict__,
                    "sitelinks": 100 - index,
                }
            )
            for index in range(1, 11)
        ]

        assigned = build_questions.assign_relative_difficulties(candidates, {})

        counts = {level: 0 for level in range(1, 6)}
        for candidate in assigned:
            counts[candidate.difficulty] += 1
        self.assertEqual({1: 2, 2: 2, 3: 2, 4: 2, 5: 2}, counts)

    def test_population_prompt_adds_article_only_when_name_requires_it(self):
        spec = build_questions.CATEGORY_SPECS[2]

        self.assertEqual(
            "What was the population of the United States in 2024?",
            build_questions.prompt_for(spec, "United States", 2024),
        )
        self.assertEqual(
            "What was the population of Canada in 2024?",
            build_questions.prompt_for(spec, "Canada", 2024),
        )

    def test_question_document_matches_extended_version_one_metadata(self):
        candidate = candidate_for("Q42", decimal.Decimal("123.5"), "Mountain elevations")
        document = build_questions.question_document(
            [candidate],
            {candidate.identifier: "What is the elevation of Example?"},
            {"Q42": 987654321},
            "2026-09-11T12:00:00Z",
        )

        self.assertEqual(1, document["version"])
        self.assertEqual("2026-09-11", document["metadata"]["generationDate"])
        self.assertEqual("1.0.0", document["metadata"]["scriptVersion"])
        self.assertEqual(123.5, document["questions"][0]["trueValue"])
        self.assertEqual(
            "https://www.wikidata.org/w/index.php?title=Q42&oldid=987654321#P2044",
            document["questions"][0]["sourceUrl"],
        )

    def test_queries_require_references_and_expected_qualifier(self):
        for spec in build_questions.CATEGORY_SPECS:
            self.assertIn("prov:wasDerivedFrom", spec.query)
            self.assertIn("wikibase:DeprecatedRank", spec.query)
        self.assertIn("pq:P585", build_questions.POPULATION_QUERY)


def candidate_for(entity_id, value, category):
    """Create a candidate with the source property matching its category."""
    properties = {
        "Mountain elevations": ("mountains", "P2044", "metres above sea level"),
        "Building heights": ("buildings", "P2048", "metres"),
        "National populations": ("populations", "P1082", "people"),
    }
    key, property_id, unit = properties[category]
    return build_questions.Candidate(
        identifier=f"wikidata-{entity_id.lower()}-{key}",
        entity_id=entity_id,
        label="Example",
        value=value,
        year=2024 if key == "populations" else None,
        category=category,
        property_id=property_id,
        unit=unit,
        sitelinks=80,
        difficulty=2,
    )


if __name__ == "__main__":
    unittest.main()
