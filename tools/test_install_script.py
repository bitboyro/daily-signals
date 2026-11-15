#!/usr/bin/env python3
import pathlib
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class InstallScriptTest(unittest.TestCase):
    def test_script_is_valid_and_dry_run_exposes_the_install_contract(self):
        subprocess.run(["bash", "-n", "install.sh"], cwd=ROOT, check=True)
        result = subprocess.run(["bash", "install.sh", "--dry-run"], cwd=ROOT,
                                check=True, capture_output=True, text=True)
        self.assertIn("startup preflight passed", result.stdout)
        self.assertIn("docker compose", result.stdout)
        self.assertIn("up -d --build", result.stdout)
        self.assertIn("post-deploy-health.py", result.stdout)

    def test_unknown_profile_is_rejected(self):
        result = subprocess.run(["bash", "install.sh", "--profile", "cloud", "--dry-run"],
                                cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn("profile must be base or kokoro", result.stderr)


if __name__ == "__main__":
    unittest.main()
