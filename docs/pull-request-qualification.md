# Pull request qualification

Every pull request records a small, retained qualification context containing its head SHA, base SHA, labels and selected qualification mode. This makes the requested policy visible to the optimizer and to reviewers.

The default mode is `standard`. Applying the `run-all-checks` label records `full` mode with the reason `label:run-all-checks`. The label is an explicit human policy request. It does not silently alter historical runs or replace the scheduled and release full qualifications.

The retained artifact is named `pr-qualification-context-<attempt>`. Its fields are data from the GitHub pull request event; no credentials or source contents are written into it.

The artifact also lists every check Decision Lab selected and skipped. `qualification/required-status-policy.json` is compared with that list; qualification fails if a skipped job is required, or if a required job is outside the small plan/audit allowlist. The default branch remains unprotected while selection is in shadow. If selective enforcement is enabled later, protect only the Decision Lab plan and audit checks—ordinary jobs may be intentionally skipped and therefore cannot all be required checks.
