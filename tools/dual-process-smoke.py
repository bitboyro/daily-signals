#!/usr/bin/env python3
"""Run Java API and Rust streamer together against the same pinned fixture."""
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, socket, subprocess, threading, time, urllib.request
ROOT = Path(__file__).resolve().parents[1]
manifest_bytes = (ROOT / 'contracts/publication-manifest-v2.json').read_bytes()
manifest = json.loads(manifest_bytes)
audio_bytes = (ROOT / 'radio-api/src/main/resources' / manifest['audioPath']).read_bytes()

class ObjectStoreFixture(BaseHTTPRequestHandler):
    objects = {
        '/radio/editions/demo.json': ('application/json', manifest_bytes),
        '/radio/' + manifest['audioPath']: ('audio/wav', audio_bytes),
    }
    def do_GET(self):
        item = self.objects.get(self.path)
        if item is None:
            self.send_error(404); return
        content_type, body = item
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers(); self.wfile.write(body)
    def log_message(self, *_): pass

def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); return sock.getsockname()[1]

java_port, rust_port, object_port = port(), port(), port()
object_store = ThreadingHTTPServer(('127.0.0.1', object_port), ObjectStoreFixture)
object_thread = threading.Thread(target=object_store.serve_forever, daemon=True)
object_thread.start()
java_classpath = ':'.join(str(ROOT / module / 'target/classes') for module in (
    'radio-api', 'radio-edition', 'radio-editorial', 'radio-ingestion', 'radio-persistence'))
java = subprocess.Popen(['java', '-cp', java_classpath, 'ro.bitboy.dailysignals.DailySignals', str(java_port)], stdout=subprocess.DEVNULL)
rust = subprocess.Popen([str(ROOT / 'streamer/target/debug/daily-signals-streamer'), str(rust_port), f'http://127.0.0.1:{object_port}', 'radio', 'editions/demo.json'], stdout=subprocess.DEVNULL)
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
    print('Java fixture and Rust object-store playback matched; range and ETag validated')
finally:
    for process in (java, rust):
        process.terminate()
        try: process.wait(timeout=3)
        except subprocess.TimeoutExpired: process.kill()
    object_store.shutdown(); object_store.server_close(); object_thread.join(timeout=3)
