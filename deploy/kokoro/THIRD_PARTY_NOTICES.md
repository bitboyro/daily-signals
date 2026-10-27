# Kokoro third-party notices

The optional profile uses these independently distributed components. Daily Signals does not redistribute their source or model weights.

- **Kokoro FastAPI v0.8.0**, `ghcr.io/remsky/kokoro-fastapi-cpu`, Apache License 2.0. The compose file pins OCI index digest `sha256:d32322c61254a871e0bc9c38d4e60cd18539cf9b1a2fc8f3ae04409061d0793b`.
- **Kokoro-82M v1.0**, repository `hexgrad/Kokoro-82M`, revision `9e1746465742d780a70ac81d01a334c9f8e0bbb3`, Apache License 2.0. `kokoro-v1_0.pth` must match SHA-256 `496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4`.
- The wrapper reports inference code adapted from StyleTTS2 under the MIT License. Operators distributing an image or model bundle must retain all notices shipped by those upstream artifacts and verify the packaged dependency inventory themselves.

The lock file is an integrity and provenance record, not permission to redistribute third-party artifacts.
