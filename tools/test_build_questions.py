"""Unit tests for the deterministic parts of the question-bank generator."""

import dataclasses
import datetime as dt
import decimal
import tempfile
import pathlib
import json
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


def claim(amount, rank="normal"):
    """Create the wbgetentities claim shape used by the admission-rule tests."""
    return {
        "rank": rank,
        "mainsnak": {
            "snaktype": "value",
            "datavalue": {"value": {"amount": amount}, "type": "quantity"},
        },
    }


def entity(property_id="P2044", amounts=("+756",), ranks=None, location=None,
           location_property="P131"):
    """Create a minimal entity document with claims for one property."""
    ranks = ranks or ["normal"] * len(amounts)
    claims = {property_id: [claim(a, r) for a, r in zip(amounts, ranks)]}
    if location is not None:
        claims[location_property] = [{
            "rank": "normal",
            "mainsnak": {
                "snaktype": "value",
                "datavalue": {"value": {"id": location}, "type": "wikibase-entityid"},
            },
        }]
    return {"claims": claims}


def candidate(identifier="wikidata-q1-mountains", entity_id="Q1", sitelinks=80,
              property_id="P2044", category="Mountain elevations", label="Test Peak"):
    """Create a Candidate with the fields the admission rules read."""
    return build_questions.Candidate(
        identifier=identifier,
        entity_id=entity_id,
        label=label,
        value=decimal.Decimal("1234"),
        as_of=None,
        category=category,
        property_id=property_id,
        unit="metres",
        measurement_basis="elevation above sea level",
        source_label="Wikidata",
        sitelinks=sitelinks,
    )


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

    def test_population_uses_latest_best_ranked_date(self):
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
        self.assertEqual(dt.date(2024, 1, 1), candidates[0].as_of)
        self.assertEqual(decimal.Decimal("1239876"), candidates[0].value)

    def test_apply_overrides_changes_only_prompt(self):
        candidate = candidate_for("Q30", decimal.Decimal("456"), "Mountain elevations")
        overrides = {
            candidate.identifier: {
                "prompt": "What is Example's surveyed elevation above sea level, in metres?",
            }
        }

        curated, prompts = build_questions.apply_overrides([candidate], overrides)

        self.assertEqual(decimal.Decimal("456"), curated[0].value)
        self.assertEqual(
            "What is Example's surveyed elevation above sea level, in metres?",
            prompts[candidate.identifier],
        )

    def test_apply_overrides_rejects_prompt_missing_measurement_context(self):
        candidate = candidate_for("Q31", decimal.Decimal("456"), "Building heights")

        with self.assertRaisesRegex(ValueError, "omits measurement context"):
            build_questions.apply_overrides(
                [candidate],
                {candidate.identifier: {"prompt": "How tall is Example?"}},
            )

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

    def test_select_balanced_prefers_more_familiar_subject_within_magnitude(self):
        less_familiar = candidate_for(
            "Q1", decimal.Decimal("11"), "Building heights"
        )
        more_familiar = dataclasses.replace(
            less_familiar,
            identifier="wikidata-q2-buildings",
            entity_id="Q2",
            sitelinks=100,
        )

        with self.assertLogs(level="INFO"):
            selected = build_questions.select_balanced(
                [less_familiar, more_familiar], 1
            )

        self.assertEqual(["Q2"], [item.entity_id for item in selected])

    def test_prompt_templates_include_unit_basis_and_date(self):
        mountain, building, population = build_questions.CATEGORY_SPECS

        self.assertEqual(
            "What is the elevation of Mount Everest above sea level, in metres?",
            build_questions.prompt_for(mountain, "Mount Everest", None, "Wikidata"),
        )
        self.assertEqual(
            "What is the architectural height of the Empire State Building, in metres?",
            build_questions.prompt_for(
                building, "Empire State Building", None, "Wikidata"
            ),
        )
        self.assertEqual(
            "According to Destatis, what was the resident population of the United States "
            "on 31 December 2024? Give your answer as a number of people.",
            build_questions.prompt_for(
                population, "United States", dt.date(2024, 12, 31), "Destatis"
            ),
        )
        self.assertEqual(
            "According to Wikidata, what was the resident population of Canada on "
            "31 December 2024? Give your answer as a number of people.",
            build_questions.prompt_for(
                population, "Canada", dt.date(2024, 12, 31), "Wikidata"
            ),
        )

    def test_question_document_matches_version_three_schema(self):
        candidate = candidate_for("Q42", decimal.Decimal("123.5"), "Mountain elevations")
        document = build_questions.question_document(
            [candidate],
            {
                candidate.identifier: (
                    "What is the elevation of Example above sea level, in metres?"
                )
            },
            {"Q42": 987654321},
            "2026-09-11T12:00:00Z",
        )

        self.assertEqual(3, document["version"])
        self.assertEqual("2026-09-11", document["metadata"]["generationDate"])
        self.assertEqual("3.0.0", document["metadata"]["scriptVersion"])
        self.assertEqual(
            ["National populations"], document["timeVaryingCategories"]
        )
        self.assertEqual(123.5, document["questions"][0]["trueValue"])
        self.assertEqual("metres", document["questions"][0]["unit"])
        self.assertEqual(
            "elevation above sea level",
            document["questions"][0]["measurementBasis"],
        )
        self.assertNotIn("asOf", document["questions"][0])
        self.assertNotIn("difficulty", document["questions"][0])
        self.assertEqual(
            "https://www.wikidata.org/w/index.php?title=Q42&oldid=987654321#P2044",
            document["questions"][0]["sourceUrl"],
        )

    def test_queries_require_references_and_expected_qualifier(self):
        for spec in build_questions.CATEGORY_SPECS:
            self.assertIn("prov:wasDerivedFrom", spec.query)
            self.assertIn("wikibase:DeprecatedRank", spec.query)
        self.assertIn("pq:P585", build_questions.POPULATION_QUERY)

    # ------------------------------------------------------------------
    # Admission rules added after the external review of the first bank.
    # ------------------------------------------------------------------

    def test_competing_values_rejects_monte_titano_disagreement(self):
        """739 m against 756 m is an unresolved disagreement, not a rounding difference."""
        conflict = build_questions.competing_values(
            entity(amounts=["+756", "+739"]), "P2044"
        )
        self.assertIsNotNone(conflict)
        self.assertEqual(
            (decimal.Decimal("739"), decimal.Decimal("756")), conflict
        )

    def test_competing_values_keeps_everest_measurement_variation(self):
        """Published Everest values differ by metres at most; that is not a dispute."""
        self.assertIsNone(
            build_questions.competing_values(
                entity(amounts=["+8848.86", "+8848", "+8844.43"]), "P2044"
            )
        )

    def test_competing_values_keeps_mont_blanc_snow_variation(self):
        """Snow-and-ice summit values vary by a few metres between surveys."""
        self.assertIsNone(
            build_questions.competing_values(
                entity(amounts=["+4805.59", "+4808.72", "+4810.02"]), "P2044"
            )
        )

    def test_competing_values_ignores_deprecated_statements(self):
        """A value the community has already superseded is not a live disagreement."""
        self.assertIsNone(
            build_questions.competing_values(
                entity(amounts=["+8848.86", "+8840"], ranks=["preferred", "deprecated"]),
                "P2044",
            )
        )

    def test_competing_values_returns_none_for_a_single_value(self):
        self.assertIsNone(build_questions.competing_values(entity(), "P2044"))

    def test_competing_values_returns_none_when_property_absent(self):
        self.assertIsNone(build_questions.competing_values(entity(), "P2048"))

    def test_apply_competing_value_rule_drops_only_the_disputed_subject(self):
        disputed = candidate(identifier="disputed", entity_id="Q158526")
        agreed = candidate(identifier="agreed", entity_id="Q513")
        kept = build_questions.apply_competing_value_rule(
            [disputed, agreed],
            {
                "Q158526": entity(amounts=["+756", "+739"]),
                "Q513": entity(amounts=["+8848.86"]),
            },
        )
        self.assertEqual(["agreed"], [item.identifier for item in kept])

    def test_apply_competing_value_rule_keeps_subjects_without_claim_documents(self):
        """A missing claim document is a gap in evidence, not evidence of a conflict."""
        kept = build_questions.apply_competing_value_rule([candidate()], {})
        self.assertEqual(1, len(kept))

    def test_familiarity_floor_rejects_obscure_subjects(self):
        """Sitelink count stands in for whether a subject can be reasoned about."""
        kept = build_questions.apply_familiarity_floor(
            [
                candidate(identifier="landmark", sitelinks=140),
                candidate(identifier="borderline", sitelinks=20),
                candidate(identifier="obscure", sitelinks=3),
            ],
            20,
        )
        self.assertEqual(["landmark", "borderline"], [i.identifier for i in kept])

    def test_load_overrides_rejects_removed_difficulty_field(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "overrides.json"
            path.write_text(
                json.dumps({"wikidata-q1-mountains": {"difficulty": 2}}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "unknown fields: difficulty"):
                build_questions.load_overrides(path)

    def test_load_exclusions_reads_ids_and_reasons(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "exclusions.json"
            path.write_text(json.dumps({
                "excluded": [{"id": "a", "subject": "A", "reason": "wrong value"}]
            }), encoding="utf-8")
            self.assertEqual({"a": "wrong value"}, build_questions.load_exclusions(path))

    def test_load_exclusions_returns_empty_when_absent(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = pathlib.Path(directory) / "nothing.json"
            self.assertEqual({}, build_questions.load_exclusions(missing))

    def test_load_exclusions_rejects_an_entry_without_a_reason(self):
        """A cut without a recorded reason is not auditable, so it is not accepted."""
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "exclusions.json"
            path.write_text(json.dumps({"excluded": [{"id": "a"}]}), encoding="utf-8")
            with self.assertRaises(ValueError):
                build_questions.load_exclusions(path)

    def test_apply_exclusions_drops_listed_records(self):
        kept = build_questions.apply_exclusions(
            [candidate(identifier="keep"), candidate(identifier="cut")],
            {"cut": "factually wrong"},
        )
        self.assertEqual(["keep"], [item.identifier for item in kept])

    def test_apply_exclusions_warns_but_does_not_fail_on_a_stale_entry(self):
        """Upstream data legitimately changes; a vanished subject must not break the build."""
        with self.assertLogs(level="WARNING") as captured:
            kept = build_questions.apply_exclusions([candidate(identifier="keep")],
                                                    {"gone": "no longer present"})
        self.assertEqual(["keep"], [item.identifier for item in kept])
        self.assertTrue(any("gone" in line for line in captured.output))

    def test_location_entity_id_prefers_the_narrower_administrative_unit(self):
        document = {"claims": {
            "P131": entity(location="Q8678", location_property="P131")["claims"]["P131"],
            "P17": entity(location="Q155", location_property="P17")["claims"]["P17"],
        }}
        self.assertEqual("Q8678", build_questions.location_entity_id(document))

    def test_location_entity_id_falls_back_to_country(self):
        document = entity(location="Q155", location_property="P17")
        self.assertEqual("Q155", build_questions.location_entity_id(document))

    def test_location_entity_id_returns_none_when_unresolved(self):
        self.assertIsNone(build_questions.location_entity_id(entity()))

    def test_prompt_names_the_place_for_an_ambiguous_mountain(self):
        """"Sugarloaf Mountain" alone denotes many summits."""
        spec = next(s for s in build_questions.CATEGORY_SPECS if s.key == "mountains")
        prompt = build_questions.prompt_for(
            spec, "Sugarloaf Mountain", None, "Wikidata", "Rio de Janeiro"
        )
        self.assertIn("Sugarloaf Mountain in Rio de Janeiro", prompt)
        self.assertIn("in metres", prompt)

    def test_prompt_names_the_place_for_an_ambiguous_building(self):
        """"Freedom Tower" is widely used for One World Trade Center."""
        spec = next(s for s in build_questions.CATEGORY_SPECS if s.key == "buildings")
        prompt = build_questions.prompt_for(
            spec, "Freedom Tower", None, "Wikidata", "Miami"
        )
        self.assertIn("Freedom Tower in Miami", prompt)

    def test_prompt_omits_the_place_when_none_resolves(self):
        spec = next(s for s in build_questions.CATEGORY_SPECS if s.key == "mountains")
        prompt = build_questions.prompt_for(spec, "Mount Everest", None, "Wikidata", None)
        self.assertEqual(
            "What is the elevation of Mount Everest above sea level, in metres?", prompt
        )

    def test_attach_locations_leaves_unresolved_candidates_unchanged(self):
        result = build_questions.attach_locations([candidate()], {}, {})
        self.assertIsNone(result[0].location)




def candidate_for(entity_id, value, category):
    """Create a candidate with the source property matching its category."""
    properties = {
        "Mountain elevations": (
            "mountains", "P2044", "metres", "elevation above sea level"
        ),
        "Building heights": (
            "buildings", "P2048", "metres", "architectural height"
        ),
        "National populations": (
            "populations", "P1082", "people", "resident population"
        ),
    }
    key, property_id, unit, measurement_basis = properties[category]
    return build_questions.Candidate(
        identifier=f"wikidata-{entity_id.lower()}-{key}",
        entity_id=entity_id,
        label="Example",
        value=value,
        as_of=dt.date(2024, 1, 1) if key == "populations" else None,
        category=category,
        property_id=property_id,
        unit=unit,
        measurement_basis=measurement_basis,
        source_label="Wikidata",
        sitelinks=80,
    )

if __name__ == "__main__":
    unittest.main()
