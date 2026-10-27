# Dependency and supply-chain policy

Daily Signals keeps dependency decisions reviewable across Maven, Cargo, npm and container builds. `security/dependency-policy.json` is the machine-readable baseline and `python3 tools/check-dependency-policy.py` enforces it.

## Accepted changes

- Maven libraries use exact versions in root properties, for example `<jackson.version>2.22.4</jackson.version>`.
- Rust and npm dependency changes update their committed lockfiles in the same commit.
- Runtime images use a dated or numbered tag, never `latest`.
- A dependency update explains the affected modules and runs their focused tests before full qualification.

## Rejected changes

Floating versions such as `LATEST`, `RELEASE` or `+`, a missing lockfile, an unpinned runtime image, a root final container stage, embedded credentials and privileged Compose services fail policy checks. Test tokens must be generated values or obvious non-secret fixtures; real credentials never enter source, examples, logs or artifacts.

## Local review

Run:

```sh
python3 tools/check-dependency-policy.py
python3 tools/scan-secrets.py
python3 tools/scan-container-policy.py
```

The hosted `dependency-policy`, `secret-scan` and `container-scan` jobs provide independent results. These deterministic gates complement provider vulnerability feeds; they do not claim that an ecosystem has no newly disclosed vulnerability.
