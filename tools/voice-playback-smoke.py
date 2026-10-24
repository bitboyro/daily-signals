#!/usr/bin/env python3
"""Render fixture audio in Java, publish manifest, and play it through Rust."""
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]

with tempfile.TemporaryDirectory() as temp:
    folder = Path(temp)
    subprocess.run(['java', '-cp', ':'.join(str(ROOT / part) for part in (
        'radio-api/target/classes', 'radio-edition/target/classes')),
        'ro.bitboy.dailysignals.FixturePublication', str(folder)], check=True)
    manifest = json.loads((folder / 'manifest.json').read_text())
    wav = (folder / manifest['audioPath']).read_bytes()
    assert hashlib.sha256(wav).hexdigest() == manifest['checksumSha256']
    port = free_port()
    rust = subprocess.Popen([str(ROOT / 'streamer/target/debug/daily-signals-streamer'), str(port),
                             str(folder / 'manifest.json'), str(folder)], stdout=subprocess.DEVNULL)
    try:
        for _ in range(50):
            if rust.poll() is not None:
                raise RuntimeError('streamer exited')
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/health', timeout=.2):
                    break
            except Exception:
                time.sleep(.1)
        else:
            raise RuntimeError('streamer did not start')
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/editions/{manifest["editionId"]}/audio') as response:
            assert response.read() == wav
            assert response.headers['ETag'] == '"' + manifest['checksumSha256'] + '"'
        print('Fixture voice, publish checksum and Rust playback matched')
    finally:
        rust.terminate()
        try: rust.wait(timeout=3)
        except subprocess.TimeoutExpired: rust.kill()
