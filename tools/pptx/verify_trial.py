"""Verify synthetic Android outputs against an independent PPTX reader / renderer.

Pillow is host QA only, not an application dependency. PowerPoint execution is not implied.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageChops, ImageDraw, ImageStat
from inspect_pptx import inspect

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
args = parser.parse_args()
folder = args.directory
project = json.loads((folder / 'native/expected-project.json').read_text(encoding='utf-8'))['projects'][0]
slides = project['slides']
source_hash = hashlib.sha256((folder / 'native/source.png').read_bytes()).hexdigest()
checks = []
for file in sorted((folder / 'native').glob('*.pptx')):
    result = inspect(file)
    assert result['slide_count'] == len(slides)
    assert result['size_emu'] == [9144000, 5143500]
    assert not result['external_relationships']
    assert not result['general_import_ready']
    rows = [json.loads(line) for line in (folder / f'rendered/{file.stem}-inspect.ndjson').read_text(encoding='utf-8').splitlines() if line]
    editable = file.stem.endswith('-editable')
    differences = []
    with ZipFile(file) as archive:
        assert not any('/fonts/' in name for name in archive.namelist()), 'No fonts may be embedded in this trial'
        for i, (expected, found) in enumerate(zip(slides, result['slides'])):
            assert found['notes'] == expected['notes']
            assert 'PRIVATE NOTES' not in found['text']
            reader_notes = [row['text'] for row in rows if row['kind'] == 'notes' and row['slideIndex'] == i]
            assert reader_notes == [expected['notes']]
            if editable:
                assert expected['heading'] in found['text'] and expected['body'] in found['text']
                assert found['pictures'][0]['sha256'] == source_hash
                reader_text = {row.get('name'): row.get('text') for row in rows if row['kind'] == 'textbox' and row['slideIndex'] == i}
                assert reader_text['Heading'] == expected['heading']
                assert reader_text['Body'] == expected['body']
            else:
                assert not found['text']
                assert not [row for row in rows if row['kind'] == 'textbox' and row['slideIndex'] == i]
                embedded = Image.open(io.BytesIO(archive.read(f'ppt/media/image{i + 1}.png'))).convert('RGB')
                assert embedded.size == (2048, 1152)
                actual = Image.open(folder / f'rendered/{file.stem}-{i + 1}.png').convert('RGB')
                assert actual.size == (960, 540)
                reference = embedded.resize(actual.size, Image.Resampling.LANCZOS)
                difference = sum(ImageStat.Stat(ImageChops.difference(reference, actual)).rms) / 3
                assert difference < 5, f'Unexpected raster slide difference: {difference}'
                differences.append(round(difference, 4))
    checks.append({'file': file.name, 'sha256': result['sha256'], 'slides': len(slides), 'editable_text': editable, 'unicode_notes_image_checks': 'pass', 'raster_rgb_rmse_0_255': differences, 'powerpoint_tested': False})
assert len(checks) == 6
roundtrip = inspect(folder / 'rendered/classic-edited-roundtrip.pptx')
assert 'Türkçe & English + edited' in roundtrip['slides'][0]['text']
assert roundtrip['slides'][0]['notes'] == slides[0]['notes']
external = inspect(folder / 'fixtures/external-reference.pptx')
assert {'table', 'chart'}.issubset(external['slides'][3]['issues'])
assert external['slides'][0]['pictures'][0]['sha256'] == hashlib.sha256((folder / 'fixtures/orientation.png').read_bytes()).hexdigest()
report = {'status': 'pass', 'native_exports': checks, 'independent_heading_edit_roundtrip': 'pass', 'complex_import_gate': 'blocked with explicit feature inventory', 'measurements': json.loads((folder / 'native/measurements.json').read_text(encoding='utf-8')), 'limitations': ['Independent Artifact Tool reader/renderer only; no PowerPoint or Google Slides run', 'Editable fonts, paragraph wrapping and RTL still require compatibility acceptance', 'Four synthetic slides, three themes; no general Office fidelity claim', 'Emulator timing is not a physical low-end device benchmark']}
(folder / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
for theme in ('classic', 'ocean', 'warm'):
    sheet = Image.new('RGB', (1920, 2240), '#e8e8e8')
    draw = ImageDraw.Draw(sheet)
    for i in range(4):
        for column, mode in enumerate(('editable', 'slide_image')):
            name = f'{theme}-{mode}-{i + 1}.png'
            sheet.paste(Image.open(folder / 'rendered' / name), (column * 960, i * 560 + 20))
            draw.text((column * 960 + 8, i * 560 + 4), name, fill='black')
    sheet.save(folder / f'{theme}-review.png')
print('6 native PPTX / 24 slides verified; independent heading edit passed; complex import stays gated.')
