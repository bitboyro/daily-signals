#!/usr/bin/env python3
import importlib.util
import io
import json
import pathlib
import sys
import tarfile
import tempfile
import unittest

PATH = pathlib.Path(__file__).with_name("backup.py")
sys.path.insert(0, str(PATH.parent))
SPEC = importlib.util.spec_from_file_location("backup", PATH)
backup = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
sys.modules["backup"] = backup
SPEC.loader.exec_module(backup)
RESTORE_SPEC = importlib.util.spec_from_file_location("daily_signals_restore", PATH.with_name("restore.py"))
restore = importlib.util.module_from_spec(RESTORE_SPEC)
assert RESTORE_SPEC.loader
RESTORE_SPEC.loader.exec_module(restore)


def object_archive(objects: dict[str, bytes]) -> bytes:
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w") as archive:
        for name, payload in sorted(objects.items()):
            info = tarfile.TarInfo(name)
            info.size = len(payload)
            info.mtime = 0
            archive.addfile(info, io.BytesIO(payload))
    return output.getvalue()


class BackupCommandTest(unittest.TestCase):
    def test_writes_atomic_checksummed_component_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            output = pathlib.Path(folder) / "backup-1"
            objects = object_archive({"daily-signals/editions/demo.json": b"{}",
                                      "daily-signals/fixtures/demo.wav": b"RIFFaudio"})
            manifest = backup.write_backup(output, b"PGDMP database", objects,
                                           "2026-11-15T12:00:00+00:00")
            recorded = json.loads((output / "manifest.json").read_text())
            self.assertEqual(manifest, recorded)
            for name, metadata in recorded["components"].items():
                payload = (output / name).read_bytes()
                self.assertEqual(backup.digest(payload), metadata["sha256"])
                self.assertEqual(len(payload), metadata["sizeBytes"])
            verified, _, _ = restore.verify_backup(output)
            self.assertEqual(2, len(verified["objects"]))

    def test_refuses_empty_or_existing_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            output = pathlib.Path(folder) / "backup-1"
            with self.assertRaises(ValueError):
                backup.write_backup(output, b"", object_archive({"object": b"value"}))
            backup.write_backup(output, b"database", object_archive({"object": b"value"}))
            with self.assertRaises(FileExistsError):
                backup.write_backup(output, b"database", object_archive({"object": b"value"}))

    def test_restore_rejects_a_tampered_recording_even_with_updated_archive_digest(self):
        with tempfile.TemporaryDirectory() as folder:
            output = pathlib.Path(folder) / "backup-1"
            backup.write_backup(output, b"database", object_archive({"radio/recording.wav": b"RIFForiginal"}))
            changed = object_archive({"radio/recording.wav": b"RIFFchanged"})
            (output / "objects.tar").write_bytes(changed)
            manifest = json.loads((output / "manifest.json").read_text())
            manifest["components"]["objects.tar"] = {
                "sha256": backup.digest(changed), "sizeBytes": len(changed),
            }
            (output / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "recording/object checksum mismatch"):
                restore.verify_backup(output)


if __name__ == "__main__":
    unittest.main()
