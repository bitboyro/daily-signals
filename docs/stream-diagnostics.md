# Stream diagnostics

The Rust streamer owns published audio byte delivery. `GET /health` confirms process availability. A published edition audio request returns `200` with an ETag; byte ranges return `206` and `Content-Range`. A matching `If-None-Match` yields `304`. Invalid byte ranges yield `416`.

Use `cargo test --manifest-path streamer/Cargo.toml` to diagnose range and cache behavior, and `python3 tools/dual-process-smoke.py` after building Java and Rust to verify the process boundary. A failed Rust unit test is a source-code failure, not evidence of an unavailable runner.
