import json
import pathlib
import sys
import unittest
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from object_store_fixture import start_object_store


class ObjectStoreFixtureTest(unittest.TestCase):
    def test_serves_manifest_and_audio_through_path_style_keys(self):
        manifest = {
            "editionId": "edition-1",
            "audioPath": "audio/edition-1.wav",
            "contentType": "audio/wav",
        }
        server, endpoint, bucket, key = start_object_store(manifest, b"RIFFfixture")
        try:
            with urllib.request.urlopen(f"{endpoint}/{bucket}/{key}") as response:
                self.assertEqual(manifest, json.loads(response.read()))
            with urllib.request.urlopen(f"{endpoint}/{bucket}/{manifest['audioPath']}") as response:
                self.assertEqual(b"RIFFfixture", response.read())
                self.assertEqual("audio/wav", response.headers["Content-Type"])
            with self.assertRaises(urllib.error.HTTPError) as missing:
                urllib.request.urlopen(f"{endpoint}/{bucket}/missing")
            self.assertEqual(404, missing.exception.code)
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
