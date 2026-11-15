import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

TOOLS = pathlib.Path(__file__).parent
sys.path.insert(0, str(TOOLS))
SPEC = importlib.util.spec_from_file_location("daily_signals_backup_roundtrip", TOOLS / "backup-roundtrip.py")
roundtrip = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(roundtrip)


class BackupRoundTripTest(unittest.TestCase):
    def test_orchestrates_seed_backup_volume_wipe_restore_and_verification(self):
        completed = subprocess.CompletedProcess([], 0, b"", b"")
        with tempfile.TemporaryDirectory() as folder, \
                mock.patch.object(roundtrip, "command", return_value=completed) as command, \
                mock.patch.object(roundtrip, "wait_for_api"), \
                mock.patch.object(roundtrip, "request_json", side_effect=[
                    roundtrip.SETTINGS, {"schemaVersion": 1, "configured": False}, roundtrip.SETTINGS,
                ]), \
                mock.patch.object(roundtrip, "write_marker"), \
                mock.patch.object(roundtrip, "marker_exists", return_value=False), \
                mock.patch.object(roundtrip, "read_marker", return_value=roundtrip.MARKER):
            report = roundtrip.round_trip(pathlib.Path(folder) / "backup", pathlib.Path(folder) / "report.json")

        self.assertEqual("PASS", report["result"])
        self.assertEqual(["seeded", "backed-up", "wiped", "restored"], report["stages"])
        calls = [call.args[0] for call in command.call_args_list]
        self.assertIn(roundtrip.COMPOSE + ["down", "--volumes", "--remove-orphans"], calls)
        self.assertTrue(any(arguments[-2].endswith("restore.py") for arguments in calls))

    def test_requires_both_database_and_object_data_to_be_gone_and_then_restored(self):
        with self.assertRaisesRegex(AssertionError, "wipe"):
            roundtrip.validate_wipe({"configured": True}, False)
        with self.assertRaisesRegex(AssertionError, "settings"):
            roundtrip.validate_restore({"configured": False}, roundtrip.MARKER)
        with self.assertRaisesRegex(AssertionError, "object"):
            roundtrip.validate_restore(roundtrip.SETTINGS, b"wrong")


if __name__ == "__main__":
    unittest.main()
