# Pull request qualification

Every pull request records a small, retained qualification context containing its head SHA, base SHA, labels and selected qualification mode. This makes the requested policy visible to the optimizer and to reviewers.

The default mode is `standard`. Applying the `run-all-checks` label records `full` mode with the reason `label:run-all-checks`. The label is an explicit human policy request. It does not silently alter historical runs or replace the scheduled and release full qualifications.

The retained artifact is named `pr-qualification-context-<attempt>`. Its fields are data from the GitHub pull request event; no credentials or source contents are written into it.
