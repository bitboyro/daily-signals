#!/usr/bin/env python3
"""Contract test for the durable object store used by the Compose install."""
import importlib.util
import pathlib
import tempfile
import threading
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("daily_signals_object_store", ROOT / "deploy/object_store.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


with tempfile.TemporaryDirectory() as directory:
    root = pathlib.Path(directory)
    module.seed(
        root,
        ROOT / "contracts/publication-manifest-v2.json",
        ROOT / "radio-api/src/main/resources/fixtures/demo-edition.wav",
    )
    assert (root / "daily-signals/editions/demo.json").is_file()
    assert (root / "daily-signals/fixtures/demo-edition.wav").is_file()
    server = module.ThreadingHTTPServer(("127.0.0.1", 0), module.handler(root))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_port}"
    try:
        assert urllib.request.urlopen(f"{base}/health").read() == b"ok\n"
        body = b'{"editionId":"durable"}'
        request = urllib.request.Request(
            f"{base}/daily-signals/editions/durable.json", data=body, method="PUT",
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request) as response:
            assert response.status == 201 and response.headers["ETag"]
        with urllib.request.urlopen(f"{base}/daily-signals/editions/durable.json") as response:
            assert response.read() == body
            assert response.headers["Content-Type"] == "application/json"
        head = urllib.request.Request(f"{base}/daily-signals/editions/durable.json", method="HEAD")
        with urllib.request.urlopen(head) as response:
            assert response.status == 200 and int(response.headers["Content-Length"]) == len(body)
        try:
            urllib.request.urlopen(f"{base}/daily-signals/../secret")
            raise AssertionError("unsafe object key was accepted")
        except urllib.error.HTTPError as error:
            assert error.code in (400, 404)
        assert (root / "daily-signals/editions/durable.json").read_bytes() == body
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=3)

print("durable object-store contract passed")
