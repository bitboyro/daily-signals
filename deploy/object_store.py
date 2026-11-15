#!/usr/bin/env python3
"""Small durable path-style object store for a self-contained installation."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import shutil
from urllib.parse import unquote, urlsplit

BUCKET = re.compile(r"[a-z0-9][a-z0-9.-]{2,62}")
KEY = re.compile(r"[A-Za-z0-9][A-Za-z0-9/_.-]*")
MAX_OBJECT_BYTES = 100 * 1024 * 1024


def object_path(root: Path, request_path: str) -> Path:
    parts = unquote(urlsplit(request_path).path).lstrip("/").split("/", 1)
    if len(parts) != 2 or not BUCKET.fullmatch(parts[0]) or not KEY.fullmatch(parts[1]):
        raise ValueError("expected /bucket/object-key")
    if ".." in parts[1].split("/") or "//" in parts[1]:
        raise ValueError("unsafe object key")
    target = (root / parts[0] / parts[1]).resolve()
    if root.resolve() not in target.parents:
        raise ValueError("object escaped store root")
    return target


def seed(root: Path, manifest_path: Path, audio_path: Path) -> None:
    manifest = json.loads(manifest_path.read_text())
    objects = {
        root / "daily-signals" / "editions" / f"{manifest['editionId']}.json": manifest_path,
        root / "daily-signals" / manifest["audioPath"]: audio_path,
    }
    for target, source in objects.items():
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)


def handler(root: Path):
    class ObjectHandler(BaseHTTPRequestHandler):
        server_version = "DailySignalsObjectStore/1"

        def target(self) -> Path | None:
            try:
                return object_path(root, self.path)
            except ValueError as error:
                self.send_error(400, str(error))
                return None

        def do_PUT(self) -> None:
            target = self.target()
            if target is None:
                return
            try:
                length = int(self.headers.get("Content-Length", ""))
            except ValueError:
                length = -1
            if not 0 < length <= MAX_OBJECT_BYTES:
                self.send_error(413, "object must be between 1 byte and 100 MiB")
                return
            body = self.rfile.read(length)
            if len(body) != length:
                self.send_error(400, "incomplete object")
                return
            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_name(f".{target.name}.{os.getpid()}.tmp")
            temporary.write_bytes(body)
            temporary.replace(target)
            self.send_response(201)
            self.send_header("ETag", f'"{hashlib.sha256(body).hexdigest()}"')
            self.send_header("Content-Length", "0")
            self.end_headers()

        def send_object(self, include_body: bool) -> None:
            if self.path == "/health":
                body = b"ok\n"
                self.send_response(200)
                self.send_header("Content-Type", "text/plain")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                if include_body:
                    self.wfile.write(body)
                return
            target = self.target()
            if target is None:
                return
            if not target.is_file():
                self.send_error(404, "object not found")
                return
            body = target.read_bytes()
            content_type = "application/json" if target.suffix == ".json" else "audio/wav"
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("ETag", f'"{hashlib.sha256(body).hexdigest()}"')
            self.end_headers()
            if include_body:
                self.wfile.write(body)

        def do_GET(self) -> None:
            self.send_object(True)

        def do_HEAD(self) -> None:
            self.send_object(False)

        def log_message(self, format: str, *args: object) -> None:
            print(f"object-store {self.address_string()} {format % args}", flush=True)

    return ObjectHandler


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("/data"))
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9000)
    parser.add_argument("--seed-manifest", type=Path)
    parser.add_argument("--seed-audio", type=Path)
    args = parser.parse_args()
    args.root.mkdir(parents=True, exist_ok=True)
    if bool(args.seed_manifest) != bool(args.seed_audio):
        parser.error("--seed-manifest and --seed-audio must be supplied together")
    if args.seed_manifest:
        seed(args.root, args.seed_manifest, args.seed_audio)
    ThreadingHTTPServer((args.host, args.port), handler(args.root)).serve_forever()


if __name__ == "__main__":
    main()
