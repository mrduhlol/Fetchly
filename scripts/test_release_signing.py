"""Release boundary regressions, including real keytool/restore integration."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('verify_release', ROOT / 'scripts/verify-production-apk.py')
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class ProductionApkTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / 'app.apk'
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('lib/arm64-v8a/example.so', b'fixture')
        self.pin = 'a' * 64
        self.signer = f'Signer #1 certificate SHA-256 digest: {self.pin}\n'
        self.badging = "package: name='com.fetchly.app.lite' versionCode='30' versionName='2.4.6-lite'\nminSdkVersion:'29'\n"

    def verify(self, signer=None, badging=None):
        with patch.object(verifier.subprocess, 'check_output', side_effect=[signer or self.signer, badging or self.badging]):
            return verifier.verify(self.apk, 'com.fetchly.app.lite', '2.4.6-lite', Path('/tools'), self.pin)

    def test_pinned_non_debuggable_stable_package_passes(self):
        report = self.verify()
        self.assertEqual('stable', report['channel'])
        self.assertEqual(29, report['minSdk'])
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), report['sha256'])

    def test_development_or_rotated_certificate_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'certificate'):
            self.verify(signer='Signer #1 certificate SHA-256 digest: ' + 'b' * 64)

    def test_test_package_cannot_be_promoted(self):
        with self.assertRaisesRegex(ValueError, 'package/version'):
            self.verify(badging=self.badging.replace('com.fetchly.app.lite', 'com.fetchly.app.lite.test'))

    def test_test_version_cannot_be_promoted(self):
        with self.assertRaisesRegex(ValueError, 'package/version'):
            self.verify(badging=self.badging.replace('2.4.6-lite', '2.4.6-lite-test'))

    def test_debuggable_apk_is_rejected_even_with_correct_signer(self):
        with self.assertRaisesRegex(ValueError, 'debuggable'):
            self.verify(badging=self.badging + 'application-debuggable\n')

    def test_lite_rejects_unexpected_native_architecture(self):
        with zipfile.ZipFile(self.apk, 'a') as archive:
            archive.writestr('lib/x86_64/example.so', b'fixture')
        with self.assertRaisesRegex(ValueError, 'arm64'):
            self.verify()


class ProductionRestoreTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = tempfile.TemporaryDirectory()
        store = Path(cls.fixture.name) / 'fixture.jks'
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(store), '-storepass', 'android',
                        '-keypass', 'android', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048',
                        '-validity', '1', '-dname', 'CN=Release Test Fixture', '-noprompt'],
                       check=True, capture_output=True)
        cert = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(store),
                                        '-storepass', 'android', '-alias', 'fixture'])
        cls.pin = hashlib.sha256(cert).hexdigest()
        cls.blob = base64.b64encode(store.read_bytes()).decode()

    @classmethod
    def tearDownClass(cls):
        cls.fixture.cleanup()

    def run_restore(self, pin, missing=None):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        (root / 'docs').mkdir()
        (root / 'docs/SIGNING_CERT_SHA256').write_text(pin)
        env = dict(os.environ, FETCHLY_KEYSTORE_BASE64=self.blob,
                   FETCHLY_SIGNING_STORE_PASSWORD='android', FETCHLY_SIGNING_KEY_ALIAS='fixture',
                   FETCHLY_SIGNING_KEY_PASSWORD='android', RUNNER_TEMP=str(root), GITHUB_ENV=str(root / 'env'))
        if missing:
            env.pop(missing)
        result = subprocess.run(['bash', str(ROOT / 'scripts/prepare-production-signing.sh')],
                                cwd=root, env=env, capture_output=True, text=True)
        return root, result

    def test_missing_secret_fails_without_generating_a_key(self):
        root, result = self.run_restore(self.pin, 'FETCHLY_KEYSTORE_BASE64')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((root / 'fetchly-production-signing.jks').exists())

    def test_wrong_certificate_fails_closed_and_removes_restored_key(self):
        root, result = self.run_restore('f' * 64)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((root / 'fetchly-production-signing.jks').exists())

    def test_pinned_persistent_key_exports_only_path_and_build_mode(self):
        root, result = self.run_restore(self.pin)
        self.assertEqual(0, result.returncode, result.stderr)
        exported = (root / 'env').read_text()
        self.assertIn('FETCHLY_PRODUCTION_BUILD=true', exported)
        self.assertNotIn('PASSWORD', exported)
        self.assertTrue((root / 'fetchly-production-signing.jks').exists())
