# Release qualification

The `release/first` branch is an independent maintenance line. Its first backport is the inclusive Rust range boundary fix from DS-11.c02, applied to the older DS-11.c01 source. Main and release SHAs must be tracked separately; a release-branch commit is not an ancestor of main.

The release workflow accepts a `v*` tag or an explicit manual dispatch. It runs Java unit and PostgreSQL reference tests, Rust tests, deterministic fixture/audio checks, and Java-to-Rust playback checks. It retains JUnit XML and the Rust log for 30 days. A tag run also writes a release manifest containing the exact source SHA, the tag, publication contract digest and report digests. Manual branch dispatch qualifies without claiming a release tag.

A tag is considered qualified only after the corresponding GitHub Actions run succeeds and its retained manifest matches the tag target. Creating a local tag or a green local replay alone does not establish a hosted release outcome.
