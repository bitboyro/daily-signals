#!/usr/bin/env python3
"""Regression tests for the disposable Keycloak fixture provisioner."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("keycloak-fixture.py")
SPEC = importlib.util.spec_from_file_location("keycloak_fixture", MODULE_PATH)
assert SPEC and SPEC.loader
KEYCLOAK_FIXTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(KEYCLOAK_FIXTURE)


class WaitForAdminTokenTest(unittest.TestCase):
    def test_retries_connection_reset_during_keycloak_startup(self) -> None:
        with patch.object(KEYCLOAK_FIXTURE, "token",
                          side_effect=[ConnectionResetError("starting"), "admin-token"]) as token_call, \
             patch.object(KEYCLOAK_FIXTURE.time, "sleep") as sleep:
            result = KEYCLOAK_FIXTURE.wait_for_admin_token("http://issuer/token", "secret")

        self.assertEqual("admin-token", result)
        self.assertEqual(2, token_call.call_count)
        sleep.assert_called_once_with(1)

    def test_fixture_profile_satisfies_keycloak_default_user_profile(self) -> None:
        calls = []

        def fake_request(url: str, **kwargs):
            calls.append((url, kwargs))
            if url.endswith("/users"):
                return 201, b"", {"Location": url + "/fixture-id"}
            if url.endswith("/roles/EDITOR"):
                return 200, b'{"id":"role-id","name":"EDITOR"}', {}
            return 204, b"", {}

        with patch.object(KEYCLOAK_FIXTURE, "wait_for_admin_token", return_value="admin-token"), \
             patch.object(KEYCLOAK_FIXTURE, "request", side_effect=fake_request), \
             patch.object(KEYCLOAK_FIXTURE, "token", return_value="fixture-token"), \
             patch.object(KEYCLOAK_FIXTURE.Path, "chmod"):
            with self.subTest("complete fixture identity"):
                from tempfile import TemporaryDirectory
                with TemporaryDirectory() as directory:
                    KEYCLOAK_FIXTURE.provision("http://issuer", "secret", Path(directory) / "token")

        profile = next(kwargs["payload"] for url, kwargs in calls if url.endswith("/users"))
        self.assertEqual("editor-fixture@example.invalid", profile["email"])
        self.assertTrue(profile["emailVerified"])
        self.assertEqual("Editor", profile["firstName"])
        self.assertEqual("Fixture", profile["lastName"])
        self.assertEqual([], profile["requiredActions"])


if __name__ == "__main__":
    unittest.main()
