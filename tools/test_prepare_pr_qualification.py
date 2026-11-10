import unittest
import importlib.util
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "prepare_pr_qualification", Path(__file__).with_name("prepare-pr-qualification.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
qualification_context = MODULE.qualification_context


EVENT = {
    "action": "synchronize",
    "pull_request": {
        "number": 33,
        "head": {"sha": "head"},
        "base": {"sha": "base"},
        "labels": [],
    },
}


class PreparePullRequestQualificationTest(unittest.TestCase):
    def test_reports_selected_and_skipped_checks(self):
        context = qualification_context(
            EVENT,
            {"mode": "shadow", "fullRun": False, "selected": ["java.yml:java-unit"],
             "skipped": ["web.yml:web-e2e"]},
            {"protection": "unprotected", "requiredChecks": [],
             "allowedWhenSelective": ["*:decision-lab*"]},
        )

        self.assertEqual(["java.yml:java-unit"], context["decisionLab"]["selectedChecks"])
        self.assertEqual(["web.yml:web-e2e"], context["decisionLab"]["skippedChecks"])
        self.assertTrue(context["decisionLab"]["requiredStatus"]["compatible"])

    def test_rejects_a_skipped_required_check(self):
        context = qualification_context(
            EVENT,
            {"mode": "enforce", "fullRun": False, "selected": [],
             "skipped": ["java.yml:java-unit"]},
            {"protection": "protected", "requiredChecks": ["java-unit"],
             "allowedWhenSelective": ["decision-lab / plan"]},
        )

        status = context["decisionLab"]["requiredStatus"]
        self.assertEqual(["java.yml:java-unit"], status["skippedRequiredChecks"])
        self.assertFalse(status["compatible"])

    def test_full_run_has_no_skipped_checks(self):
        context = qualification_context(
            EVENT,
            {"mode": "shadow", "fullRun": True, "selected": ["java.yml:java-unit"],
             "skipped": ["web.yml:web-e2e"]},
            {"protection": "unprotected", "requiredChecks": [], "allowedWhenSelective": []},
        )

        self.assertEqual([], context["decisionLab"]["skippedChecks"])


if __name__ == "__main__":
    unittest.main()
