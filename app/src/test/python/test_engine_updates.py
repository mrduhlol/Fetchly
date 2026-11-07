import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

MODULE = Path(__file__).parents[2] / 'main/python/engine_updates.py'
spec = importlib.util.spec_from_file_location('engine_updates', MODULE)
updates = importlib.util.module_from_spec(spec)
spec.loader.exec_module(updates)


class Response(io.BytesIO):
    def geturl(self):
        return 'https://files.pythonhosted.org/packages/test.whl'


class EngineUpdatesTests(unittest.TestCase):
    def wheel(self, unsafe=False):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as archive:
            archive.writestr('yt_dlp/__init__.py', 'from .version import __version__\n')
            archive.writestr('yt_dlp/version.py', '__version__ = "2026.10.26"\n')
            if unsafe:
                archive.writestr('../outside.py', 'bad')
        return data.getvalue()

    def metadata(self, body, digest=None):
        return ('2026.10.26', {'url': 'https://files.pythonhosted.org/packages/test.whl',
                               'digests': {'sha256': digest or hashlib.sha256(body).hexdigest()}})

    def test_installs_verified_wheel_and_activates_only_in_new_process(self):
        body = self.wheel()
        with tempfile.TemporaryDirectory() as root:
            with patch.object(updates, '_metadata', return_value=self.metadata(body)), \
                    patch.object(updates, 'urlopen', return_value=Response(body)):
                result = json.loads(updates.install(root, '2026.8.19'))
            self.assertTrue(result['ok'])
            self.assertTrue(result['restart'])
            self.assertFalse(result['available'])
            self.assertEqual('2026.10.26', updates._pointer(root))
            code = ('import sys; sys.path.insert(0, sys.argv[1]); import engine_updates; '
                    'engine_updates._bundled_version = lambda: \"2026.8.19\"; engine_updates.activate(sys.argv[2]); import yt_dlp; print(yt_dlp.__version__)')
            result = subprocess.check_output([sys.executable, '-c', code, str(MODULE.parent), root], text=True)
            self.assertEqual('2026.10.26', result.strip())

    def test_bad_digest_does_not_replace_the_working_selection(self):
        body = self.wheel()
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / 'active.json').write_text('{"version":"2026.8.19"}')
            with patch.object(updates, '_metadata', return_value=self.metadata(body, '0' * 64)), \
                    patch.object(updates, 'urlopen', return_value=Response(body)):
                result = json.loads(updates.install(root, '2026.8.19'))
            self.assertFalse(result['ok'])
            self.assertEqual('2026.8.19', updates._pointer(root))
            self.assertFalse((Path(root) / '2026.10.26').exists())
            self.assertFalse(list(Path(root).glob('stage-*')))

    def test_redirect_cannot_downgrade_the_update_to_plain_http(self):
        body = self.wheel()
        response = Response(body)
        response.geturl = lambda: 'http://files.pythonhosted.org/packages/test.whl'
        with tempfile.TemporaryDirectory() as root:
            with patch.object(updates, '_metadata', return_value=self.metadata(body)), \
                    patch.object(updates, 'urlopen', return_value=response):
                result = json.loads(updates.install(root, '2026.8.19'))
            self.assertFalse(result['ok'])
            self.assertIsNone(updates._pointer(root))

    def test_archive_traversal_is_rejected(self):
        body = self.wheel(unsafe=True)
        with tempfile.TemporaryDirectory() as root:
            with patch.object(updates, '_metadata', return_value=self.metadata(body)), \
                    patch.object(updates, 'urlopen', return_value=Response(body)):
                result = json.loads(updates.install(root, '2026.8.19'))
            self.assertFalse(result['ok'])
            self.assertIsNone(updates._pointer(root))
            self.assertFalse((Path(root) / 'outside.py').exists())

    def test_incompatible_python_release_is_not_offered(self):
        metadata = {'info': {'version': '2026.10.26', 'requires_python': '>=99'}, 'urls': []}
        with patch.object(updates, 'urlopen', return_value=Response(json.dumps(metadata).encode())):
            result = json.loads(updates.check('/unused', '2026.8.19'))
        self.assertFalse(result['ok'])
        self.assertIn('newer Fetchly build', result['message'])

    def test_bad_pointer_cannot_load_code_outside_the_update_directory(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / 'active.json').write_text('{"version":"../../elsewhere"}')
            self.assertIsNone(updates._pointer(root))

    def test_reset_keeps_package_files_and_requires_restart(self):
        with tempfile.TemporaryDirectory() as root:
            directory = Path(root) / '2026.10.26'
            directory.mkdir()
            (Path(root) / 'active.json').write_text('{"version":"2026.10.26"}')
            result = json.loads(updates.reset(root, '2026.10.26'))
            self.assertTrue(result['restart'])
            self.assertTrue(directory.is_dir())
            self.assertIsNone(updates._pointer(root))

    def test_newer_app_bundle_supersedes_an_old_override(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / 'active.json').write_text('{"version":"2026.8.19"}')
            code = ('import sys; sys.path.insert(0, sys.argv[1]); import engine_updates; '
                    'engine_updates._bundled_version = lambda: "2026.10.26"; '
                    'engine_updates.activate(sys.argv[2])')
            subprocess.check_call([sys.executable, '-c', code, str(MODULE.parent), root])
            self.assertIsNone(updates._pointer(root))

    def test_broken_override_falls_back_to_bundled_engine(self):
        with tempfile.TemporaryDirectory() as root:
            package = Path(root) / '2026.10.26' / 'yt_dlp'
            package.mkdir(parents=True)
            (package / '__init__.py').write_text('raise RuntimeError("broken update")')
            (Path(root) / 'active.json').write_text('{"version":"2026.10.26"}')
            code = ('import sys; sys.path.insert(0, sys.argv[1]); import engine_updates; '
                    'engine_updates._bundled_version = lambda: \"2026.8.19\"; engine_updates.activate(sys.argv[2]); import yt_dlp; '
                    'assert sys.argv[2] not in yt_dlp.__file__')
            subprocess.check_call([sys.executable, '-c', code, str(MODULE.parent), root])
            self.assertIsNone(updates._pointer(root))


if __name__ == '__main__':
    unittest.main()
