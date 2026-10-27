# Release qualification

The current producer/listener candidate scope and required evidence are listed in [E6 release candidate notes](release-candidate-e6.md).

The `release/first` branch is an independent maintenance line. Its first backport is the inclusive Rust range boundary fix from DS-11.c02, applied to the older DS-11.c01 source. Main and release SHAs must be tracked separately; a release-branch commit is not an ancestor of main.

The release workflow accepts a `v*` tag or an explicit manual dispatch. It runs Java unit and PostgreSQL reference tests, Rust tests, deterministic fixture/audio and documentation checks, the web unit and listener journey tests, Java-to-Rust playback checks, and container startup both host-run and through compose. It retains Java and web JUnit XML and the Rust log for 30 days. A tag run also writes a release manifest containing the exact source SHA, the tag, digests of every shared contract, the published fixture audio checksum (verified against its manifest) and Java, web and Rust report digests. Manual branch dispatch qualifies without claiming a release tag.

A tag is considered qualified only after the corresponding GitHub Actions run succeeds and its retained manifest matches the tag target. Creating a local tag or a green local replay alone does not establish a hosted release outcome.

The same workflow runs every Monday at 03:00 UTC on the default branch as the weekly full qualification. A scheduled run qualifies the exact `main` SHA it checked out; it never writes a release manifest. GitHub can delay or drop scheduled runs, and it disables them in inactive repositories. A week without a successful scheduled run is therefore recorded as missed full-qualification debt, which the next full run must clear; it is not inferred to have passed.

## Station-local edition schedule

Daily Signals schedules an edition in the station's IANA timezone, then stores the resulting instant. For example, 08:30 in `Europe/Bucharest` on 2026-01-15 is 06:30 UTC; 08:30 in `Asia/Tokyo` on that same local date is 23:30 UTC on the prior day. DST gaps move forward under Java's zone rules, while overlaps use the earlier offset. CI schedules remain UTC operational triggers; they do not redefine the station-local air time.

Listener visual baselines keep ISO dates in semantic `datetime` attributes and render labels with an explicitly selected locale. CI qualifies the reviewed listener markup on Node 22 and 24 so host locale and timezone defaults cannot silently alter the daily-edition view.

## Performance profiles

Performance checks separate deterministic work budgets from observed timing. Edition assembly records fixed candidate and selected-segment counts, the web build enforces total and per-file byte ceilings, and API startup records elapsed time as evidence without making host speed a pass condition. Provider job duration remains observational evidence and is never substituted for these deterministic budgets.
