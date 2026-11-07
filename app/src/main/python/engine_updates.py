"""Verified official yt-dlp wheel updates, activated only on the next process start."""
import ast
import hashlib
import importlib
import json
import os
from pathlib import Path, PurePosixPath
from importlib.metadata import version as distribution_version, PackageNotFoundError
import re
import shutil
import sys
import tempfile
import threading
from urllib.parse import urlsplit
from urllib.request import urlopen
import zipfile
from packaging.specifiers import SpecifierSet
from packaging.version import Version

_LOCK = threading.Lock()
_MAX_WHEEL = 32 * 1024 * 1024
_MAX_EXPANDED = 128 * 1024 * 1024


def _pointer(root):
    path = Path(root) / 'active.json'
    try:
        value = json.loads(path.read_text())['version']
        return value if re.fullmatch(r'[0-9A-Za-z._-]{1,80}', value) else None
    except (OSError, ValueError, KeyError, TypeError):
        return None


def _bundled_version():
    try:
        return distribution_version('yt-dlp')
    except PackageNotFoundError:
        return '0'


def activate(root):
    """No hot replacement: active downloads keep their imported engine modules."""
    if 'yt_dlp' in sys.modules:
        return
    version = _pointer(root)
    if not version:
        return
    if Version(version) <= Version(_bundled_version()):
        (Path(root) / 'active.json').unlink(missing_ok=True)
        return
    package_root = Path(root) / version
    if not (package_root / 'yt_dlp' / '__init__.py').is_file():
        (Path(root) / 'active.json').unlink(missing_ok=True)
        return
    sys.path.insert(0, str(package_root))
    try:
        importlib.import_module('yt_dlp')
    except Exception:
        sys.path.remove(str(package_root))
        for name in list(sys.modules):
            if name == 'yt_dlp' or name.startswith('yt_dlp.'):
                del sys.modules[name]
        (Path(root) / 'active.json').unlink(missing_ok=True)
        importlib.import_module('yt_dlp')  # Bundled fallback stays available.


def _metadata():
    with urlopen('https://pypi.org/pypi/yt-dlp/json', timeout=20) as response:
        raw = response.read(8 * 1024 * 1024 + 1)
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError('The update catalogue was too large.')
    data = json.loads(raw)
    version = data['info']['version']
    if not re.fullmatch(r'[0-9A-Za-z._-]{1,80}', version):
        raise ValueError('The update version was invalid.')
    runtime = '.'.join(map(str, sys.version_info[:3]))
    if runtime not in SpecifierSet(data['info'].get('requires_python') or ''):
        raise ValueError('This engine release needs a newer Fetchly build. Update Fetchly to continue.')
    for wheel in data['urls']:
        if wheel['filename'].endswith('-py3-none-any.whl') and not wheel.get('yanked'):
            parsed = urlsplit(wheel['url'])
            if parsed.scheme != 'https' or parsed.hostname != 'files.pythonhosted.org':
                raise ValueError('The update was not hosted by the official package service.')
            if runtime not in SpecifierSet(wheel.get('requires_python') or ''):
                continue
            return version, wheel
    raise ValueError('No compatible engine update was available.')


def _report(root, current, latest=None, message=None):
    ready = _pointer(root)
    imported = getattr(sys.modules.get('yt_dlp'), '__file__', '') or ''
    reset_pending = ready is None and bool(imported) and Path(imported).is_relative_to(Path(root))
    restart = (ready is not None and ready != current) or reset_pending
    return {'ok': True, 'current': current, 'latest': latest,
            'available': latest is not None and Version(latest) > Version(current) and ready != latest,
            'restart': restart, 'override': ready is not None,
            'message': message or ('Bundled engine selected. Finish current downloads, force stop Fetchly in Android app settings, then reopen to activate it.' if reset_pending
                                   else f'Engine {ready} is ready. Finish current downloads, force stop Fetchly in Android app settings, then reopen to activate it.' if restart
                                   else f'Engine {current} is up to date.' if latest is not None and Version(latest) <= Version(current)
                                   else f'Engine {latest} is available.' if latest else f'Using engine {current}.')}


def status(root, current):
    return json.dumps(_report(root, current))


def check(root, current):
    try:
        version, _ = _metadata()
        return json.dumps(_report(root, current, version))
    except Exception as exc:
        return json.dumps({'ok': False, 'message': f"Couldn't check for an engine update. {str(exc)[:180]}"})


def _unpack(wheel_path, target, expected_version):
    with zipfile.ZipFile(wheel_path) as archive:
        files = archive.infolist()
        if len(files) > 20000 or sum(item.file_size for item in files) > _MAX_EXPANDED:
            raise ValueError('The engine archive exceeded the size limit.')
        for item in files:
            path = PurePosixPath(item.filename)
            if path.is_absolute() or '..' in path.parts or '\\' in item.filename:
                raise ValueError('The engine archive contained an unsafe path.')
            if (item.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('The engine archive contained a symbolic link.')
            if path.parts and path.parts[0] == 'yt_dlp':
                archive.extract(item, target)
    version_file = target / 'yt_dlp' / 'version.py'
    module = ast.parse(version_file.read_text())
    versions = [ast.literal_eval(node.value) for node in module.body
                if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == '__version__' for t in node.targets)]
    if versions != [expected_version] or not (target / 'yt_dlp' / '__init__.py').is_file():
        raise ValueError('The engine package did not match its advertised version.')


def install(root, current):
    with _LOCK:
        stage = None
        try:
            version, wheel = _metadata()
            if Version(version) <= Version(current):
                return json.dumps(_report(root, current, version))
            root = Path(root)
            root.mkdir(parents=True, exist_ok=True)
            stage = Path(tempfile.mkdtemp(prefix='stage-', dir=root))
            archive = stage / 'engine.whl'
            digest = hashlib.sha256()
            size = 0
            with urlopen(wheel['url'], timeout=30) as response, archive.open('wb') as output:
                if urlsplit(response.geturl()).scheme != 'https' or urlsplit(response.geturl()).hostname != 'files.pythonhosted.org':
                    raise ValueError('The engine update redirected to an unexpected host.')
                while True:
                    chunk = response.read(64 * 1024)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > _MAX_WHEEL:
                        raise ValueError('The engine update exceeded the size limit.')
                    digest.update(chunk)
                    output.write(chunk)
            if digest.hexdigest() != wheel['digests']['sha256']:
                raise ValueError('The engine update failed its integrity check. Try again.')
            _unpack(archive, stage, version)
            archive.unlink()
            final = root / version
            # An installed version is immutable while another thread uses it.
            if final.exists():
                shutil.rmtree(final)
            os.replace(stage, final)
            stage = None
            pending = root / 'active.json.tmp'
            with pending.open('w') as output:
                json.dump({'version': version}, output)
                output.flush()
                os.fsync(output.fileno())
            os.replace(pending, root / 'active.json')
            return json.dumps(_report(root, current, version))
        except Exception as exc:
            return json.dumps({'ok': False, 'message': f"Couldn't install the engine update. {str(exc)[:180]}"})
        finally:
            if stage is not None:
                shutil.rmtree(stage, ignore_errors=True)


def reset(root, current):
    with _LOCK:
        (Path(root) / 'active.json').unlink(missing_ok=True)
    return json.dumps({'ok': True, 'current': current, 'available': False, 'restart': True,
                       'override': False, 'message': 'Bundled engine selected. Finish current downloads, force stop Fetchly in Android app settings, then reopen to activate it.'})
