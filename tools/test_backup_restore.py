#!/usr/bin/env python3
import importlib.util
import json
import pathlib
import tempfile
import unittest

PATH = pathlib.Path(__file__).with_name("backup.py")
SPEC = importlib.util.spec_from_file_location("daily_signals_backup", PATH)
backup = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(backup)


class BackupCommandTest(unittest.TestCase):
    def test_writes_atomic_checksummed_component_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            output = pathlib.Path(folder) / "backup-1"
            manifest = backup.write_backup(output, b"PGDMP database", b"objects archive",
                                           "2026-11-15T12:00:00+00:00")
            recorded = json.loads((output / "manifest.json").read_text())
            self.assertEqual(manifest, recorded)
            for name, metadata in recorded["components"].items():
                payload = (output / name).read_bytes()
                self.assertEqual(backup.digest(payload), metadata["sha256"])
                self.assertEqual(len(payload), metadata["sizeBytes"])

    def test_refuses_empty_or_existing_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            output = pathlib.Path(folder) / "backup-1"
            with self.assertRaises(ValueError):
                backup.write_backup(output, b"", b"objects")
            backup.write_backup(output, b"database", b"objects")
            with self.assertRaises(FileExistsError):
                backup.write_backup(output, b"database", b"objects")


if __name__ == "__main__":
    unittest.main()
