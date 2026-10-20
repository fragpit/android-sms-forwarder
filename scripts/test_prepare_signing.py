import hashlib
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import unittest


PROJECT = Path(__file__).resolve().parent.parent
KEY_NAME = "sms-forwarder-debug.jks"


class PrepareSigningTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="sms-forwarder-signing-")
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.project = root / "project"
        (self.project / "scripts").mkdir(parents=True)
        (self.project / "signing").mkdir()
        shutil.copy2(
            PROJECT / "scripts/prepare-signing.sh",
            self.project / "scripts/prepare-signing.sh",
        )
        self.checksum = self.project / "signing/keystore.sha256"
        shutil.copy2(PROJECT / "signing/keystore.sha256", self.checksum)
        self.expected = self.checksum.read_text().strip()
        self.keys = [
            root / "primary" / KEY_NAME,
            root / "backup" / KEY_NAME,
            self.project / "signing" / KEY_NAME,
        ]
        self.environment = {
            **os.environ,
            "SMS_FORWARDER_SIGNING_DIR": str(self.keys[0].parent),
            "SMS_FORWARDER_SIGNING_BACKUP_DIR": str(self.keys[1].parent),
        }

    def seed(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(PROJECT / "signing" / KEY_NAME, path)
        self.assertEqual(self.expected, self.digest(path))

    def prepare(self):
        return subprocess.run(
            ["bash", str(self.project / "scripts/prepare-signing.sh")],
            env=self.environment,
            capture_output=True,
            text=True,
            check=False,
        )

    def digest(self, path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def test_restores_from_each_copy_and_reuses_the_key(self):
        for survivor in self.keys:
            with self.subTest(survivor=survivor):
                for key in self.keys:
                    key.unlink(missing_ok=True)
                self.seed(survivor)
                result = self.prepare()
                self.assertEqual(0, result.returncode, result.stderr)
                for key in self.keys:
                    self.assertEqual(self.expected, self.digest(key))
                    self.assertEqual(0o600, stat.S_IMODE(key.stat().st_mode))
                for key in self.keys[:2]:
                    self.assertEqual(0o700, stat.S_IMODE(key.parent.stat().st_mode))
                modified_times = [key.stat().st_mtime_ns for key in self.keys]
                result = self.prepare()
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(modified_times, [key.stat().st_mtime_ns for key in self.keys])

    def test_missing_all_copies_fails_without_generating_a_key(self):
        result = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("A new key will not be generated", result.stderr)
        self.assertTrue(all(not key.exists() for key in self.keys))

    def test_wrong_key_never_overwrites_existing_copies(self):
        for wrong_key in self.keys:
            with self.subTest(wrong_key=wrong_key):
                for key in self.keys:
                    self.seed(key)
                wrong_key.write_bytes(b"different signing key")
                before = [self.digest(key) for key in self.keys]
                result = self.prepare()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Signing key mismatch", result.stderr)
                self.assertEqual(before, [self.digest(key) for key in self.keys])

    def test_invalid_checksum_fails_without_reseeding_identity(self):
        self.seed(self.keys[2])
        self.checksum.write_text("invalid checksum\n")
        result = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid signing/keystore.sha256", result.stderr)
        self.assertEqual("invalid checksum\n", self.checksum.read_text())
        self.assertFalse(self.keys[0].exists())
        self.assertFalse(self.keys[1].exists())


if __name__ == "__main__":
    unittest.main()
