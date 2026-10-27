# Container runtime

`deploy/compose.yaml` builds two images from the repository root: `api` (Java API plus the built listener UI) and `streamer` (Rust playback). Both read `deploy/runtime.env`. The streamer mounts the shared v2 publication manifest and the synthetic fixture audio read-only under `/srv/published`; a deployment replaces those mounts with its published object store.

Run `python3 tools/container-smoke.py` after building Java, Rust and the web client. It validates pinned base images, non-root runtime users and port declarations, then starts both entrypoints on the host with container paths mapped to local build outputs and loopback bind addresses. `python3 tools/container-smoke.py --docker` builds and starts the compose project and runs the same startup and playback probes.

Hosted deployment qualification uses the separate `post-deploy-health` check and retains its JSON evidence. See [listener release operations](operations/listener-release.md) for packaging, promotion, probing and rollback.

Producer review writes stay closed unless `DAILY_SIGNALS_REVIEW_TOKENS` (`token=ROLE` pairs) is supplied to the `api` container from a secret store. `runtime.env` deliberately omits it, and tokens are never committed.
