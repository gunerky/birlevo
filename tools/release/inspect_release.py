"""Verify the packaged release manifest, icon, ELF alignment, and bundle hash."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile


def load_alignments(data):
    if data[:4] != b'\x7fELF' or data[5] != 1:
        raise ValueError('Expected little-endian ELF library')
    is_64 = data[4] == 2
    number = '<Q' if is_64 else '<I'
    offset = struct.unpack_from(number, data, 32 if is_64 else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if is_64 else 42)
    return [
        struct.unpack_from(number, data, offset + i * size + (48 if is_64 else 28))[0]
        for i in range(count)
        if struct.unpack_from('<I', data, offset + i * size)[0] == 1
    ]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    android = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(args.manifest).getroot()
    application = manifest.find('application')
    sdk = manifest.find('uses-sdk')
    assert manifest.get('package') == 'studio.birlevo.android'
    assert application.get(android + 'debuggable', 'false') == 'false'
    assert application.get(android + 'testOnly', 'false') == 'false'
    assert application.get(android + 'allowBackup') == 'false'
    permissions = sorted(
        node.get(android + 'name') for node in manifest.findall('uses-permission')
    )
    # Permissions are versioned: the gated SDK enters the signed package at code 13.
    expected_permissions = {
        'android.permission.ACCESS_NETWORK_STATE',
        'studio.birlevo.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
    }
    if int(manifest.get(android + 'versionCode')) >= 10:
        expected_permissions |= {
            'android.permission.FOREGROUND_SERVICE',
            'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
            'android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING',
            'android.permission.POST_NOTIFICATIONS',
            'android.permission.WAKE_LOCK',
        }
        services = application.findall('service')
        # bundletool prints compiled flag attributes as hex; merged XML uses names.
        def processing_type(service):
            value = service.get(android + 'foregroundServiceType', '')
            return (int(value, 16) == 0x2001 if value.startswith('0x') else
                    set(value.split('|')) == {'dataSync', 'mediaProcessing'})
        processing = [s for s in services if processing_type(s)]
        assert len(processing) == 1 and processing[0].get(android + 'exported') == 'false'
        for process in (':pdf_pages', ':pdf_renderer'):
            workers = [s for s in services if s.get(android + 'process') == process]
            assert len(workers) == 1
            assert workers[0].get(android + 'exported') == 'false'
            assert workers[0].get(android + 'isolatedProcess') == 'true'
    if int(manifest.get(android + 'versionCode')) >= 13:
        expected_permissions |= {
            'android.permission.INTERNET',
            'android.permission.READ_BASIC_PHONE_STATE',
            'com.google.android.gms.permission.AD_ID',
        }
        metadata = {node.get(android + 'name'): node.get(android + 'value')
                    for node in application.findall('meta-data')}
        assert metadata['com.google.android.gms.ads.APPLICATION_ID'] == 'ca-app-pub-5967660342681325~8485054661'
        startup = [node for node in application.findall('provider')
                   if node.get(android + 'authorities') == 'studio.birlevo.android.androidx-startup']
        assert len(startup) == 1 and startup[0].get(android + 'exported') == 'false'
        assert not any(node.get(android + 'name') == 'androidx.work.WorkManagerInitializer'
                       for node in startup[0].findall('meta-data'))
    assert set(permissions) == expected_permissions, permissions
    assert not any('Fixture' in node.get(android + 'name', '') for node in application)
    assert not any('OutputReceiver' in node.get(android + 'name', '') for node in application)
    output_providers = [node for node in application.findall('provider')
                        if node.get(android + 'authorities') == 'studio.birlevo.android.outputs']
    if tuple(map(int, manifest.get(android + 'versionName').split('.'))) >= (0, 1, 6):
        assert len(output_providers) == 1
        assert output_providers[0].get(android + 'exported') == 'false'
        assert output_providers[0].get(android + 'grantUriPermissions') == 'true'
    with zipfile.ZipFile(args.bundle) as archive:
        assert archive.testzip() is None
        names = archive.namelist()
        icon = [name for name in names if name.endswith('/birlevo_logo_safe.png')]
        assert len(icon) == 1, 'Final safe-area logo missing from bundle'
        libraries = {}
        for name in names:
            if name.endswith('.so'):
                alignments = load_alignments(archive.read(name))
                libraries[name] = alignments
                if '/arm64-v8a/' in name or '/x86_64/' in name:
                    assert alignments and all(value >= 16384 for value in alignments), (
                        f'64-bit library lacks 16 KB ELF alignment: {name} {alignments}'
                    )
        assert not any('/fixtures/' in name or 'androidTest' in name for name in names)
    report = {
        'applicationId': manifest.get('package'),
        'versionCode': int(manifest.get(android + 'versionCode')),
        'versionName': manifest.get(android + 'versionName'),
        'minSdk': int(sdk.get(android + 'minSdkVersion')),
        'targetSdk': int(sdk.get(android + 'targetSdkVersion')),
        'debuggable': False,
        'permissions': permissions,
        'logoResource': icon[0],
        'nativeLoadSegmentAlignments': libraries,
        'bytes': args.bundle.stat().st_size,
        'sha256': hashlib.sha256(args.bundle.read_bytes()).hexdigest(),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
