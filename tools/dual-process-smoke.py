#!/usr/bin/env python3
"""Run Java API and Rust streamer together against the same pinned fixture."""
from pathlib import Path
import json, socket, subprocess, time, urllib.request
ROOT = Path(__file__).resolve().parents[1]
manifest = json.loads((ROOT / 'contracts/publication-manifest-v2.json').read_text())

def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); return sock.getsockname()[1]

java_port, rust_port = port(), port()
java = subprocess.Popen(['java', '-cp', str(ROOT / 'radio-api/target/classes'), 'ro.bitboy.dailysignals.DailySignals', str(java_port)], stdout=subprocess.DEVNULL)
rust = subprocess.Popen([str(ROOT / 'streamer/target/debug/daily-signals-streamer'), str(rust_port), str(ROOT / 'contracts/publication-manifest-v2.json'), str(ROOT / 'radio-api/src/main/resources')], stdout=subprocess.DEVNULL)
try:
    for _ in range(50):
        if java.poll() is not None or rust.poll() is not None: raise RuntimeError('one service exited')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{java_port}/health', timeout=.2) as j, urllib.request.urlopen(f'http://127.0.0.1:{rust_port}/health', timeout=.2) as r:
                assert j.status == r.status == 200
            break
        except Exception:
            time.sleep(.1)
    else: raise RuntimeError('services did not become healthy')
    with urllib.request.urlopen(f'http://127.0.0.1:{java_port}/editions/demo/audio') as j, urllib.request.urlopen(f'http://127.0.0.1:{rust_port}/editions/{manifest["editionId"]}/audio') as r:
        assert j.read() == r.read()
        assert r.headers['ETag'] == '"' + manifest['checksumSha256'] + '"'
    request = urllib.request.Request(f'http://127.0.0.1:{rust_port}/editions/{manifest["editionId"]}/audio', headers={'Range': 'bytes=0-3'})
    with urllib.request.urlopen(request) as response:
        assert response.status == 206 and response.read() == b'RIFF'
    print('Java and Rust fixture playback matched; range and ETag validated')
finally:
    for process in (java, rust):
        process.terminate()
        try: process.wait(timeout=3)
        except subprocess.TimeoutExpired: process.kill()
