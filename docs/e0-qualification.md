# E0 qualification

E0 is a deterministic Java proof of concept. It registers configured HTTPS sources, parses pinned synthetic snapshots, preserves attribution, reviews candidates, assembles a dated edition, and references playable fixture audio. The Java API currently serves health and fixture audio only; live ingestion and playback in Rust arrive later.

Reference checks at this checkpoint: `mvn -B -ntp -o verify`, `python3 tools/check-fixtures.py`, and `python3 tools/check-source-example.py`. The DS-05 candidate merge regression is intentionally visible in the commit history and corrected in DS-06. The docs example similarly fails at DS-03.c01 and is fixed at DS-03.c02. These are observed local checks; hosted provider results must be attached separately.
