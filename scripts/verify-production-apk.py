#!/usr/bin/env python3
"""Verify the production signer, stable package/version and Android manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def verify(apk, package, version, tools, pin):
    signer = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', signer, re.M)
    if [value.lower() for value in certificates] != [pin.lower()]:
        raise ValueError('APK signer is not the single pinned production certificate')
    badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
    identity = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not identity or identity[1] != package or identity[3] != version or '-test' in identity[3]:
        raise ValueError('APK package/version does not match the intended stable release')
    if re.search(r'^application-debuggable', badging, re.M):
        raise ValueError('Production APK must not be debuggable')
    minimum = re.search(r"^sdkVersion:'(\d+)'|^minSdkVersion:'(\d+)'", badging, re.M)
    if not minimum:
        raise ValueError('APK minimum Android version could not be determined')
    abis = sorted({name.split('/')[1] for name in zipfile.ZipFile(apk).namelist() if name.startswith('lib/')})
    if package == 'com.fetchly.app.lite' and abis != ['arm64-v8a']:
        raise ValueError('Lite production APK must contain only arm64-v8a libraries')
    return {'package': identity[1], 'versionCode': int(identity[2]), 'versionName': identity[3],
            'minSdk': int(minimum[1] or minimum[2]), 'abis': abis, 'sizeBytes': apk.stat().st_size,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'certificateSha256': pin.lower(),
            'sourceCommit': os.environ.get('GITHUB_SHA'), 'channel': 'stable'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--package', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--tools', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    pin = Path('docs/SIGNING_CERT_SHA256').read_text().strip()
    if not re.fullmatch('[0-9a-fA-F]{64}', pin):
        raise ValueError('Invalid pinned production certificate fingerprint')
    report = verify(args.apk, args.package, args.version, args.tools, pin)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
