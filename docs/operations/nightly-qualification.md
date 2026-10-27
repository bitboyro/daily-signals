# Nightly qualification

The nightly tier finds failures that are too expensive or repetitive for every ordinary change. GitHub Actions runs it at 02:00 UTC, on manual dispatch, and in a bounded two-iteration form while this workflow itself is reviewed.

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

Download the failing job artifact before rerunning. Classify setup or runner exhaustion as infrastructure; classify a repeatable task, test, threshold or mutant failure as code evidence. Rerun only the failed job after the cause is understood. The weekly release qualification remains the authoritative full release gate; a green nightly run does not replace it.
