"""Small path-style object-store fixture shared by qualification tools."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading


def start_object_store(manifest, audio):
    """Serve one publication manifest and its audio through the Rust S3 boundary."""
    bucket = "radio"
    manifest_key = f"editions/{manifest['editionId']}.json"
    objects = {
        f"/{bucket}/{manifest_key}": ("application/json", json.dumps(manifest).encode()),
        f"/{bucket}/{manifest['audioPath']}": (manifest["contentType"], audio),
    }

    class ObjectStoreHandler(BaseHTTPRequestHandler):
        def do_GET(self):
            item = objects.get(self.path)
            if item is None:
                self.send_error(404)
                return
            content_type, body = item
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), ObjectStoreHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    endpoint = f"http://127.0.0.1:{server.server_port}"
    return server, endpoint, bucket, manifest_key
