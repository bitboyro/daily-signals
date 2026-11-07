#!/usr/bin/env python3
"""Provision a disposable editor in the imported test realm and save its access token without printing it."""
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def request(url: str, *, form: dict | None = None, payload: object | None = None,
            bearer: str | None = None, method: str | None = None) -> tuple[int, bytes, dict]:
    data = None
    headers = {"Accept": "application/json"}
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"
    call = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(call, timeout=5) as response:
            return response.status, response.read(), dict(response.headers)
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read(), dict(failure.headers)


def token(endpoint: str, values: dict) -> str:
    status, body, _ = request(endpoint, form=values)
    if status != 200:
        raise RuntimeError(f"token endpoint returned {status}")
    return json.loads(body)["access_token"]


def wait_for_admin_token(endpoint: str, admin_password: str) -> str:
    """Wait until Keycloak accepts requests, including while its socket is restarting."""
    for _ in range(60):
        try:
            return token(endpoint, {"client_id": "admin-cli", "username": "admin",
                                    "password": admin_password, "grant_type": "password"})
        except (RuntimeError, OSError):
            time.sleep(1)
    raise RuntimeError("Keycloak did not become ready within 60 seconds")


def provision(base: str, admin_password: str, token_file: Path) -> None:
    master = f"{base}/realms/master/protocol/openid-connect/token"
    admin_token = wait_for_admin_token(master, admin_password)

    realm_admin = f"{base}/admin/realms/bitboy-internal"
    status, _, headers = request(f"{realm_admin}/users", bearer=admin_token, method="POST",
                                 payload={"username": "editor-fixture", "enabled": True,
                                          "email": "editor-fixture@example.invalid",
                                          "emailVerified": True, "firstName": "Editor",
                                          "lastName": "Fixture", "requiredActions": []})
    if status != 201:
        raise RuntimeError(f"create fixture user returned {status}")
    user_id = headers["Location"].rstrip("/").split("/")[-1]
    fixture_credential = f"editor-{admin_password}"
    status, _, _ = request(f"{realm_admin}/users/{user_id}/reset-password", bearer=admin_token, method="PUT",
                           payload={"type": "password", "value": fixture_credential, "temporary": False})
    if status != 204:
        raise RuntimeError(f"set fixture password returned {status}")
    status, role_body, _ = request(f"{realm_admin}/roles/EDITOR", bearer=admin_token)
    if status != 200:
        raise RuntimeError(f"read editor role returned {status}")
    status, _, _ = request(f"{realm_admin}/users/{user_id}/role-mappings/realm", bearer=admin_token,
                           method="POST", payload=[json.loads(role_body)])
    if status != 204:
        raise RuntimeError(f"assign editor role returned {status}")

    access_token = token(f"{base}/realms/bitboy-internal/protocol/openid-connect/token", {
        "client_id": "daily-signals-integration", "username": "editor-fixture",
        "password": fixture_credential, "grant_type": "password",
    })
    token_file.write_text(access_token)
    token_file.chmod(0o600)
    print("Keycloak fixture user provisioned and token saved")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8180")
    parser.add_argument("--admin-password", required=True)
    parser.add_argument("--token-file", type=Path, required=True)
    args = parser.parse_args()
    provision(args.base.rstrip("/"), args.admin_password, args.token_file)


if __name__ == "__main__":
    main()
