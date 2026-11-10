# Nightly qualification

The nightly tier finds failures that are too expensive or repetitive for every ordinary change. GitHub Actions runs it at 02:00 UTC and on manual dispatch. It does not run on pull requests.

The run has two parts. `Nightly qualification` executes the full reference and slow tier. After that workflow completes, `Nightly qualification observer` reruns failed jobs once, pools Decision Lab audits and publishes the morning report. The observer is separate because GitHub cannot rerun a failed job until its original workflow has completed.

## Morning read

The pinned [Nightly qualification issue](https://github.com/bitboyro/daily-signals/issues/24) receives one report per first attempt. The same `nightly-report.md` and machine-readable `nightly-report.json` are retained as the `nightly-report-*` artifact and the report is written to the observer's job summary.

Read it in this order:

1. **Failing tonight** is a repeatable failure after the one automatic failed-job rerun.
2. **Flaky tonight** failed on attempt one and passed on attempt two. It is evidence, not a green check to ignore.
3. **Escapes** names a merged pull request that actually skipped a check which now fails on `main`.
4. **Shadow escapes** names a check that still ran, but that Decision Lab's shadow plan would have skipped. Any shadow escape blocks moving to enforcement.
5. **Plan misses** and its 95% upper bound are the cumulative safety ledger over observable failing changes.
6. **Rollout evidence** applies the pre-registered sample-size gate. A green reference run and zero misses do not mean enforcement is safe while fewer than eight failing pull-request runs have been audited.

One real or shadow escape keeps `rollout.mode` in `shadow` until the cause is linked from the report and the corrected selector replays the full v2 and v3 history without a miss.

## Day-3 rollout decision

DS-69 applied the gate exactly as registered before day 1. Decision Lab remains in shadow mode because all four conditions had to pass and two were still unproven.

| Condition | Evidence at the decision | Result |
|---|---|---|
| Every audited shadow run misses zero failing jobs | 47 audited changes; 0 of 10 failing pull-request runs missed | Pass |
| At least eight failing pull-request runs audited | 10 observed; 8 required | Pass |
| Frozen configuration misses no failing change across the combined v2 and v3 replay | Combined replay not complete | Not met |
| No nightly shadow escape through the end of day 2 | Zero reported so far, but no published nightly covers final day-2 merge `fcd5e24` | Not met |

The source reports are [the 10-run sample-size report](https://github.com/bitboyro/daily-signals/issues/24#issuecomment-6719381531) and [the latest report available at the decision](https://github.com/bitboyro/daily-signals/issues/24#issuecomment-6736342210). The machine-readable decision is `qualification/rollout-decision.json`; the applied morning record is `qualification/nightly-follow-up.json`. No selective workflow conditions or holdback were enabled.

## Full reference

The `reference-*` jobs call the five normal verification workflows as reusable workflows with the Decision Lab mode fixed to `shadow`. This produces the day's ground truth without maintaining a second copy of the build and test commands. The final `reference` job fails if any verification family fails.

## Slow tier

`nightly-load` starts the Java API and Rust streamer, applies the fixed profile in `qualification/nightly-load.json`, and records request errors and p95 latency for both boundaries. Before the parallel reads it exercises the producer sequence against the running API: load the queue, approve a pending story, approve the edition, then publish it. Each step has its own reviewed latency ceiling and appears in the JSON evidence without storing the generated reviewer token.

`nightly-browser-matrix` runs a real listener and producer journey in Chromium, Firefox and WebKit at mobile and desktop viewports. Their JSON, JUnit and trace evidence is uploaded even on failure.

## Nightly soak

`nightly-soak` repeats three deterministic paths five times: Java editorial scheduling, Rust manifest handling and TypeScript listener unit tests. It stops at the first failing command or when the 30-minute profile budget is exceeded. `nightly-soak-report.json` records every task duration and iteration.

Reproduce the complete profile with:

```sh
npm --prefix web ci
python3 tools/nightly-soak.py
```

For a quick configuration check, run `python3 tools/nightly-soak.py --validate-only --iterations 2`.

## Editorial mutation

`editorial-mutation` mutates `CandidateReview` and requires at least a 70% mutation score. A surviving mutant means existing tests executed the code but did not distinguish the changed behavior; inspect the PIT HTML report and add a behavior-level assertion. Do not lower the threshold merely to clear a run.

Reproduce it with:

```sh
mvn -B -ntp -pl radio-editorial -am install -DskipTests
mvn -B -ntp -f radio-editorial/pom.xml -Pmutation org.pitest:pitest-maven:mutationCoverage
```

## Triage and reruns

Download the failing job artifact and both `nightly-attempts-*` records. Classify setup or runner exhaustion as infrastructure; classify a repeatable task, test, threshold or mutant failure as code evidence. The observer has already rerun only failed jobs once—do not erase evidence with repeated reruns. The weekly release qualification remains the authoritative full release gate; a green nightly run does not replace it.

Environment-specific results and artifact naming are described in [compatibility matrix evidence](matrix-evidence.md).
