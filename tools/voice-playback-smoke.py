#!/usr/bin/env python3
"""Render fixture audio in Java, publish through the event consumer, and play it through Rust."""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
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
    events = [json.loads(line) for line in (folder / 'events.jsonl').read_text().splitlines()]
    assert [event['type'] for event in events] == ['edition.published'], events
    assert events[0]['manifest']['checksumSha256'] == manifest['checksumSha256']
    assert manifest['schemaVersion'] == 2 and manifest['airDate'] == '2026-01-15'
    stream_port, object_port = free_port(), free_port()

    class ObjectStoreFixture(BaseHTTPRequestHandler):
        objects = {
            f'/radio/editions/{manifest["editionId"]}.json': ('application/json', json.dumps(manifest).encode()),
            f'/radio/{manifest["audioPath"]}': ('audio/wav', wav),
        }

        def do_GET(self):
            item = self.objects.get(self.path)
            if item is None:
                self.send_error(404)
                return
            content_type, body = item
            self.send_response(200)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):
            pass

    object_store = ThreadingHTTPServer(('127.0.0.1', object_port), ObjectStoreFixture)
    object_thread = threading.Thread(target=object_store.serve_forever, daemon=True)
    object_thread.start()
    rust = subprocess.Popen([
        str(ROOT / 'streamer/target/debug/daily-signals-streamer'),
        str(stream_port),
        f'http://127.0.0.1:{object_port}',
        'radio',
        f'editions/{manifest["editionId"]}.json',
    ], stdout=subprocess.DEVNULL)
    try:
        for _ in range(50):
            if rust.poll() is not None:
                raise RuntimeError('streamer exited')
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{stream_port}/health', timeout=.2):
                    break
            except Exception:
                time.sleep(.1)
        else:
            raise RuntimeError('streamer did not start')
        with urllib.request.urlopen(f'http://127.0.0.1:{stream_port}/editions/{manifest["editionId"]}/audio') as response:
            assert response.read() == wav
            assert response.headers['ETag'] == '"' + manifest['checksumSha256'] + '"'
        print('Fixture voice, publish checksum and Rust playback matched')
    finally:
        rust.terminate()
        try: rust.wait(timeout=3)
        except subprocess.TimeoutExpired: rust.kill()
        object_store.shutdown()
        object_store.server_close()
        object_thread.join(timeout=3)
