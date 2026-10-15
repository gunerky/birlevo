"""Independent host ZIP/CRC/hash/Pillow full-decode checks. No app or runtime dependency."""
import argparse
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('folder', type=Path)
args = parser.parse_args()
manifest = json.loads((args.folder / 'manifest.json').read_text(encoding='utf-8'))
assert {x['format'] for x in manifest['outputs']} == {'JPEG', 'PNG', 'WEBP', 'BMP'}
reports = []
for item in manifest['outputs']:
    file = args.folder / (item['format'].lower() + '.zip')
    with zipfile.ZipFile(file) as archive:
        assert archive.testzip() is None
        assert len(archive.infolist()) == 3
        assert len({x.filename.lower() for x in archive.infolist()}) == 3
        assert archive.namelist() == [x['name'] for x in item['entries']]
        for i, entry in enumerate(item['entries']):
            assert re.fullmatch(r'\d{2}_[^/\\\x00]+\.(jpg|png|webp|bmp)', entry['name'])
            data = archive.read(entry['name'])
            assert hashlib.sha256(data).hexdigest() == entry['sha256']
            with Image.open(io.BytesIO(data)) as decoded:
                decoded.load()  # Full independent decode, not just a header probe.
                assert decoded.format == item['format'], (file, decoded.format)
                assert decoded.size == (entry['width'], entry['height'])
                if i:
                    assert decoded.size == (16, 16)
                    rgba = decoded.convert('RGBA').getpixel((0, 0))
                    if item['format'] in ('PNG', 'WEBP'): assert rgba[3] == 0, (file, rgba)
                    else: assert min(rgba[:3]) >= 240 and rgba[3] == 255, (file, rgba)
                reports.append({'zip': file.name, 'entry': entry['name'], 'bytes': len(data),
                                'width': decoded.width, 'height': decoded.height, 'sha256': entry['sha256']})
report = {'independent_reader': 'Python zipfile + Pillow', 'archives': 4, 'images': reports}
(args.folder / 'independent-images.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print('4 ZIP archives, 12 exact entries: CRC/hash/full image decode/dimensions/alpha passed.')
