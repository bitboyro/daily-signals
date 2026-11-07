import json
import tempfile
import unittest
from pathlib import Path

from enrich_nightly_report import enrich


class EnrichNightlyReportTest(unittest.TestCase):
    def test_marks_small_zero_miss_sample_as_insufficient(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            report_json = root / "report.json"
            report_markdown = root / "report.md"
            report_json.write_text(json.dumps({"allTime": {"failingChanges": 2, "changesWithAMiss": 0}}))
            report_markdown.write_text("# Nightly qualification\n\n**Everything passed.**\n")

            section = enrich(report_json, report_markdown, 8)

            self.assertIn("Insufficient evidence: 2 of 8", section)
            self.assertIn("enforcement remains blocked", section)
            self.assertIn("Reference and slow-tier checks passed", report_markdown.read_text())

    def test_reports_sample_gate_without_claiming_full_rollout_readiness(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            report_json = root / "report.json"
            report_markdown = root / "report.md"
            report_json.write_text(json.dumps({"allTime": {"failingChanges": 8, "changesWithAMiss": 0}}))
            report_markdown.write_text("# Nightly qualification\n")

            section = enrich(report_json, report_markdown, 8)

            self.assertIn("Sample-size gate met", section)
            self.assertIn("replay and escape checks still apply", section)


if __name__ == "__main__":
    unittest.main()
