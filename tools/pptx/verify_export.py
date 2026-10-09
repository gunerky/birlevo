"""Offline checks for production raster PPTX outputs against the independent host renderer."""
import argparse
import io
import json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageChops, ImageStat
from inspect_pptx import inspect

def verify(folder):
    project = json.loads((folder / 'native/expected-project.json').read_text(encoding='utf-8'))['projects'][0]
    expected_files = {f'edge-{edge}-notes-{str(notes).lower()}.pptx' for edge in (1024, 2048, 4096) for notes in (False, True)}
    assert {file.name for file in (folder / 'native').glob('*.pptx')} == expected_files
    checks = []
    for file in sorted((folder / 'native').glob('*.pptx')):
        edge = int(file.stem.split('-')[1])
        notes = file.stem.endswith('-true')
        inventory = inspect(file)
        assert inventory['slide_count'] == len(project['slides']) == 4
        assert inventory['size_emu'] == [9144000, 5143500]
        assert not inventory['external_relationships']
        rows = [json.loads(row) for row in (folder / f'rendered/{file.stem}-inspect.ndjson').read_text(encoding='utf-8').splitlines() if row]
        differences = []
        with ZipFile(file) as archive:
            assert not any('/fonts/' in name or name.endswith('.bin') for name in archive.namelist())
            assert any('notesSlide' in name for name in archive.namelist()) == notes
            for i, (expected, actual) in enumerate(zip(project['slides'], inventory['slides'])):
                assert not actual['text'], 'Raster output must not imply editable text'
                assert actual['notes'] == (expected['notes'] if notes else '')
                independent_notes = [row['text'] for row in rows if row['kind'] == 'notes' and row['slideIndex'] == i]
                # The independent reader materializes an empty notes model even without a notes part.
                assert independent_notes == ([expected['notes']] if notes else [''])
                embedded = Image.open(io.BytesIO(archive.read(f'ppt/media/image{i+1}.png'))).convert('RGB')
                assert embedded.size == (edge, edge*9//16)
                rendered = Image.open(folder / f'rendered/{file.stem}-{i+1}.png').convert('RGB')
                assert rendered.size == (960, 540)
                reference = embedded.resize(rendered.size, Image.Resampling.LANCZOS)
                difference = sum(ImageStat.Stat(ImageChops.difference(reference, rendered)).rms) / 3
                assert difference < 5, f'Independent rendering difference: {file.name}/{i+1}: {difference}'
                differences.append(round(difference, 4))
        checks.append({'file': file.name, 'sha256': inventory['sha256'], 'bytes': file.stat().st_size,
                       'edge': edge, 'notes': notes, 'pages': 4, 'raster_rgb_rmse_0_255': differences})
    report = {'status': 'pass', 'exports': checks, 'independent_reader': 'Artifact Tool 2.8.89',
              'real_office_tested': False, 'full_ooxml_schema_validation': False, 'editable_or_input_support': False}
    (folder / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('6 production PPTX / 24 slides: resolutions, notes exclusion/opt-in, independent raster rendering passed.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    verify(parser.parse_args().directory)
