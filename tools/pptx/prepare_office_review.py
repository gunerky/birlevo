"""Prepare offline, synthetic production PPTX samples for manual Office review.

This tool does not open Office, upload files, or mark compatibility as passed.
It accepts only the six outputs already checked by verify_export.py and creates
a new directory so that an earlier human review is never overwritten.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
from zipfile import ZipFile

from inspect_pptx import inspect


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def prepare(source, output, revision):
    source, output = source.resolve(), output.resolve()
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('Use the full tested source commit hash.')
    if output == source or output in source.parents or source in output.parents:
        raise ValueError('The review folder must be separate from the source evidence.')
    verification = json.loads((source / 'verification.json').read_text(encoding='utf-8'))
    if verification['status'] != 'pass':
        raise ValueError('Production verification must pass first.')
    names = {f'edge-{edge}-notes-{str(notes).lower()}.pptx'
             for edge in (1024, 2048, 4096) for notes in (False, True)}
    records = verification['exports']
    if len(records) != 6 or {record['file'] for record in records} != names:
        raise ValueError('Expected all six production QA outputs.')

    # Validate every input before writing. Only known image parts are copied;
    # never extract a ZIP archive or derive output paths from its entry names.
    inventories = {}
    for record in records:
        file = source / 'native' / record['file']
        inventory = inspect(file)
        if (inventory['sha256'] != record['sha256'] or file.stat().st_size != record['bytes']
                or inventory['slide_count'] != record['pages']
                or inventory['external_relationships']):
            raise ValueError(f'Stale or unsupported evidence: {file.name}')
        expected_notes = file.stem.endswith('-true')
        if expected_notes != record['notes'] or int(file.stem.split('-')[1]) != record['edge']:
            raise ValueError(f'Sample options do not match: {file.name}')
        with ZipFile(file) as archive:
            for number, slide in enumerate(inventory['slides'], 1):
                part = f'ppt/media/image{number}.png'
                if (slide['text'] or len(slide['pictures']) != 1
                        or slide['pictures'][0]['part'] != part
                        or (not expected_notes and slide['notes'])):
                    raise ValueError(f'Expected a raster production slide: {file.name}/{number}')
                if hashlib.sha256(archive.read(part)).hexdigest() != slide['pictures'][0]['sha256']:
                    raise ValueError('Image hash mismatch.')
        inventories[file.name] = inventory

    output.mkdir(parents=True, exist_ok=False)
    (output / 'decks').mkdir()
    (output / 'reference').mkdir()
    manifest = {'prepared_at_utc': datetime.now(timezone.utc).isoformat(),
                'tested_source_revision': revision, 'office_compatibility': 'pending',
                'scope': 'Synthetic raster PPTX export only; not editable Office import.',
                'exports': []}
    results = {'application': None, 'application_version': None, 'platform': None,
               'reviewer': None, 'checked_at': None, 'exports': []}
    articles = []
    for record in sorted(records, key=lambda row: row['file']):
        file = source / 'native' / record['file']
        inventory = inventories[file.name]
        shutil.copyfile(file, output / 'decks' / file.name)
        references, slides_html = [], []
        with ZipFile(file) as archive:
            for number, slide in enumerate(inventory['slides'], 1):
                relative = f'reference/{file.stem}-slide-{number}.png'
                (output / relative).write_bytes(archive.read(f'ppt/media/image{number}.png'))
                references.append({'slide': number, 'image': relative,
                                   'image_sha256': slide['pictures'][0]['sha256'],
                                   'expected_notes': slide['notes']})
                notes = html.escape(slide['notes']) if record['notes'] else 'Not alanı boş olmalı.'
                slides_html.append(f'<figure><img src="{relative}" alt="Beklenen slayt {number}">'
                                   f'<figcaption>Slayt {number}</figcaption>'
                                   f'<p>Beklenen konuşmacı notu:</p><pre dir="auto">{notes}</pre></figure>')
        manifest['exports'].append({**record, 'deck': f'decks/{file.name}', 'slides': references})
        results['exports'].append({'file': file.name, 'sha256': record['sha256'],
                                   'opens_without_repair': None, 'four_slides_in_order': None,
                                   'visual_match_all_slides': None, 'notes_match_all_slides': None,
                                   'slideshow_matches_editor': None, 'issues': ''})
        articles.append(f'<article><h2>{file.name}</h2><a href="decks/{file.name}">PPTX dosyası</a>'
                        f'<p>{record["edge"]} piksel, notlar '
                        f'{"dahil" if record["notes"] else "hariç"}, {record["bytes"]:,} bayt.</p>'
                        + ''.join(slides_html) + '</article>')
    write_json(output / 'manifest.json', manifest)
    write_json(output / 'review-results-template.json', results)
    (output / 'index.html').write_text('''<!doctype html><html lang="tr"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Birlevo PPTX Office kontrolü</title><style>
body{font:17px/1.6 system-ui;background:#f4f6fb;color:#11264b;max-width:1000px;margin:32px auto;padding:20px}
article{background:white;border:1px solid #dbe1ec;border-radius:20px;padding:24px;margin:28px 0}
h1{color:#b83e3b}a{color:#123d8e}figure{margin:28px 0}img{width:100%;height:auto;border:1px solid #dbe1ec}
pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f4f6fb;padding:16px}code{overflow-wrap:anywhere}
</style><h1>Birlevo PPTX Office kontrolü</h1>
<p><strong>Durum: gerçek Office kontrolü bekliyor.</strong> Bu sayfa bir başarı raporu değildir.
Dosyalar uygulamanın sentetik QA çıktılarıdır. Yalnız yerel dosyalar kullanılır.</p>
<ol><li>Her PPTX'i PowerPoint'te açın. Kullanılan uygulama, sürüm ve platformu kaydedin.
Google Slides veya LibreOffice denemesi için ayrı bir sonuç dosyası tutun.</li>
<li>Dosya onarım uyarısı olmadan açılmalı. Korumalı Görünüm güvenlik bildirimi varsa ayrıca kaydedin;
bu bildirim dosya onarım uyarısıyla aynı değildir.</li>
<li>Dört slaytın sırası ve 16:9 oranını kontrol edin. Düzenleyicide ve slayt gösterisinde
her slaytı aşağıdaki referansla karşılaştırın. Kırpılma, kayma ve eksik görsel olmamalı.
Renkli köşe işaretleri ve farklı alfabeler aynı görünmeli.</li>
<li>Konuşmacı notlarını kontrol edin. Notlar hariç dosyada tüm alanlar boş, dahil dosyada
aşağıdaki metinlerle aynı olmalı. Slaytlar tek görseldir; ayrı düzenlenebilir metin beklenmez.</li>
<li><code>review-results-template.json</code> dosyasını ayrı bir adla kopyalayıp sonuçları doldurun.
Denetlenmemiş alanları <code>null</code> bırakın. <code>true</code> yalnız gerçekten görülen
başarılı kontrolü, <code>false</code> hatayı belirtir. Her sonuç özgün dosya hash'ine bağlıdır.</li></ol>
<p>Manifest: <a href="manifest.json">dosya kimlikleri ve beklenen notlar</a>.
Sonuç şablonu: <a href="review-results-template.json">kontrol kaydı</a>.</p>
''' + ''.join(articles) + '</html>\n', encoding='utf-8')
    print(f'Prepared {len(records)} PPTX / 24 slide references. Actual Office review remains pending.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='Production pptx-export evidence directory')
    parser.add_argument('output', type=Path, help='New, separate output directory')
    parser.add_argument('--source-revision', required=True)
    args = parser.parse_args()
    prepare(args.source, args.output, args.source_revision)
