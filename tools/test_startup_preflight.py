#!/usr/bin/env python3
import importlib.util
import pathlib
import tempfile
import unittest

PATH = pathlib.Path(__file__).with_name("startup_preflight.py")
SPEC = importlib.util.spec_from_file_location("startup_preflight", PATH)
preflight = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(preflight)


class StartupPreflightTest(unittest.TestCase):
    def test_repository_base_configuration_is_ready(self):
        self.assertEqual([], preflight.configuration_errors(preflight.ROOT, "base"))

    def test_kokoro_profile_explains_missing_assets(self):
        errors = preflight.configuration_errors(preflight.ROOT, "kokoro", {})
        self.assertTrue(any("model is missing" in error for error in errors), errors)
        self.assertTrue(any("voices are missing" in error for error in errors), errors)

    def test_malformed_runtime_environment_is_actionable(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            (root / "deploy").mkdir()
            (root / "deploy/runtime.env").write_text("NOT_AN_ASSIGNMENT\n")
            errors = preflight.configuration_errors(root, "base")
            self.assertIn("must use NAME=value", errors[0])


if __name__ == "__main__":
    unittest.main()
