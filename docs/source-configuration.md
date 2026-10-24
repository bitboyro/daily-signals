# Source configuration

The example in `examples/sources.json` is the pinned synthetic source used by the pilot. A source has a stable lowercase ID, a display name, an HTTPS URL and a nonnegative priority. The source URL must match the snapshot provenance before candidates enter review. The fetcher must retain the snapshot ID and capture time so a revision can be replayed offline. Production sources require permission and a separately configured fetch policy; the pilot runs only on licensed synthetic content.

Run `python3 tools/check-source-example.py` before copying the example into a deployment configuration.
