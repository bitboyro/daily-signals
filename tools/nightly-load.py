#!/usr/bin/env python3
"""Exercise the API and Rust stream boundary with a fixed, reviewable load profile."""
import argparse
import concurrent.futures
import json
import math
import pathlib
import socket
import subprocess
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--profile", type=pathlib.Path, default=ROOT / "qualification/nightly-load.json")
parser.add_argument("--report", type=pathlib.Path, default=pathlib.Path("nightly-load-report.json"))
parser.add_argument("--validate-only", action="store_true")
args = parser.parse_args()

profile = json.loads(args.profile.read_text())
assert profile["schemaVersion"] == 1
assert 1 <= profile["requestsPerTarget"] <= 10_000
assert 1 <= profile["concurrency"] <= 128
assert 1 <= profile["startupTimeoutSeconds"] <= 120
assert 1 <= profile["maximumP95Milliseconds"] <= 60_000
assert 0 <= profile["maximumErrorRate"] <= 1
assert {target["service"] for target in profile["targets"]} == {"api", "stream"}

if args.validate_only:
    print(f"nightly load profile valid: {len(profile['targets'])} targets x {profile['requestsPerTarget']} requests")
    raise SystemExit(0)

manifest_path = ROOT / "contracts/publication-manifest-v2.json"
manifest = json.loads(manifest_path.read_text())


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def fetch(url):
    started = time.monotonic()
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            response.read()
            ok = response.status == 200
    except Exception:
        ok = False
    return round((time.monotonic() - started) * 1000, 3), ok


api_port, stream_port = free_port(), free_port()
java_classpath = ":".join(str(ROOT / module / "target/classes") for module in (
    "radio-api", "radio-edition", "radio-editorial", "radio-ingestion", "radio-persistence"))
api = subprocess.Popen([
    "java", "-cp", java_classpath,
    "ro.bitboy.dailysignals.DailySignals", str(api_port)
], stdout=subprocess.DEVNULL)
stream = subprocess.Popen([
    str(ROOT / "streamer/target/debug/daily-signals-streamer"), str(stream_port),
    str(manifest_path), str(ROOT / "radio-api/src/main/resources")
], stdout=subprocess.DEVNULL)

try:
    deadline = time.monotonic() + profile["startupTimeoutSeconds"]
    while time.monotonic() < deadline:
        if api.poll() is not None or stream.poll() is not None:
            raise RuntimeError("a load-profile service exited during startup")
        if fetch(f"http://127.0.0.1:{api_port}/health")[1] and fetch(f"http://127.0.0.1:{stream_port}/health")[1]:
            break
        time.sleep(.1)
    else:
        raise RuntimeError("load-profile services did not become healthy")

    bases = {"api": f"http://127.0.0.1:{api_port}", "stream": f"http://127.0.0.1:{stream_port}"}
    results = []
    for target in profile["targets"]:
        path = target["path"].format(editionId=manifest["editionId"])
        url = bases[target["service"]] + path
        with concurrent.futures.ThreadPoolExecutor(max_workers=profile["concurrency"]) as pool:
            samples = list(pool.map(lambda _: fetch(url), range(profile["requestsPerTarget"])))
        durations = sorted(sample[0] for sample in samples)
        failures = sum(not sample[1] for sample in samples)
        p95 = durations[max(0, math.ceil(.95 * len(durations)) - 1)]
        results.append({
            "name": target["name"], "requests": len(samples), "failures": failures,
            "errorRate": failures / len(samples), "p95Milliseconds": p95,
        })

    passed = all(result["errorRate"] <= profile["maximumErrorRate"]
                 and result["p95Milliseconds"] <= profile["maximumP95Milliseconds"] for result in results)
    report = {"schemaVersion": 1, "result": "PASS" if passed else "FAIL", "profile": profile, "targets": results}
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    assert passed, "nightly load thresholds exceeded"
    print("nightly load passed: " + ", ".join(
        f"{result['name']} p95={result['p95Milliseconds']}ms errors={result['failures']}" for result in results))
finally:
    for process in (api, stream):
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
