"""Tests for loading and checking the shipped magnitude-coverage evidence."""

import json
import pathlib
import tempfile
import unittest

from tools import check_magnitude_coverage


class CheckMagnitudeCoverageTest(unittest.TestCase):
    """Exercise the strict file boundary around the shared coverage logic."""

    def test_load_records_reads_minimal_question_data(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "questions.json"
            path.write_text(json.dumps({"questions": [{
                "id": "area-1",
                "category": "Areas",
                "trueValue": 12.5,
            }]}), encoding="utf-8")

            records = check_magnitude_coverage.load_records(path)

        self.assertEqual("area-1", records[0].identifier)
        self.assertEqual("12.5", str(records[0].value))

    def test_load_records_rejects_boolean_as_a_number(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "questions.json"
            path.write_text(json.dumps({"questions": [{
                "id": "area-1",
                "category": "Areas",
                "trueValue": True,
            }]}), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "numeric trueValue"):
                check_magnitude_coverage.load_records(path)

    def test_load_ratchet_rejects_unknown_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "ratchet.json"
            path.write_text(json.dumps({"surprise": 1}), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "unknown fields"):
                check_magnitude_coverage.load_ratchet(path)


if __name__ == "__main__":
    unittest.main()
