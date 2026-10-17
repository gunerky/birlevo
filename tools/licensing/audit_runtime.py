"""Exact runtime license inventory. Fetch only official Maven POMs with explicit --fetch.

POM declarations/packaged notices are evidence, not patent clearance. Tests/build tools
are excluded from the commercial runtime manifest. New dependencies require review.
"""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
from xml.etree import ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'output/development/mp3-expansion'
MANIFEST = Path(__file__).with_name('runtime-licenses.json')
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'

def pom_url(coordinate):
    group, artifact, version = coordinate.split(':')
    google = group.startswith(('androidx.', 'com.google.android.gms', 'com.google.android.ump',
                               'com.google.android.play', 'com.google.android.libraries.ads.'))
    base = 'https://dl.google.com/dl/android/maven2/' if google else 'https://repo.maven.apache.org/maven2/'
    return base + f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.pom'

def get_pom(coordinate, fetch):
    group, artifact, version = coordinate.split(':')
    files = list((CACHE / group / artifact / version).glob('*/*.pom'))
    local = REPORT / 'poms' / f'{coordinate.replace(":", "_")}.pom'
    if files: return files[0]
    if local.exists(): return local
    if not fetch: raise ValueError(f'Missing POM for {coordinate}; run explicit --fetch review')
    local.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(pom_url(coordinate), headers={'User-Agent': 'Birlevo-license-review/1'})
    with urllib.request.urlopen(request, timeout=25) as response:
        if not response.geturl().startswith(('https://dl.google.com/', 'https://repo.maven.apache.org/')):
            raise ValueError('Unexpected license source redirect')
        data = response.read(1_048_577)
        if len(data) > 1_048_576: raise ValueError('Oversized POM')
    ET.fromstring(data); local.write_bytes(data)
    return local

def declarations(coordinate, fetch, depth=0):
    if depth > 5: raise ValueError('Excessive parent depth')
    p = get_pom(coordinate, fetch); root = ET.parse(p).getroot()
    licenses = [{'name': x.findtext('{*}name', ''), 'url': x.findtext('{*}url', '')}
                for x in root.findall('./{*}licenses/{*}license')]
    if licenses: return licenses, [pom_url(coordinate)]
    parent = root.find('{*}parent')
    if parent is None: raise ValueError('No declared license: ' + coordinate)
    parent_coordinate = ':'.join(parent.findtext('{*}'+key, '') for key in ['groupId', 'artifactId', 'version'])
    licenses, sources = declarations(parent_coordinate, fetch, depth + 1)
    return licenses, [pom_url(coordinate), *sources]

SUPPLEMENTAL = Path(__file__).with_name('advertising-licenses.json')

def supplemental(coordinate):
    if not SUPPLEMENTAL.exists(): return None
    return json.loads(SUPPLEMENTAL.read_text(encoding='utf-8'))['artifacts'].get(coordinate)

def identified(licenses, coordinate=None):
    result = []
    for item in licenses:
        name = item['name'].lower()
        if 'apache' in name and ('2.0' in name or 'version 2' in name): result.append('Apache-2.0')
        elif name == 'bouncy castle licence': result.append('Bouncy-Castle')
        elif name == 'the mit license': result.append('MIT')
        else:
            extra = supplemental(coordinate)
            if extra is None or item != extra['declaration']:
                raise ValueError('Unreviewed license declaration: ' + item['name'])
            result.extend(extra['spdx'])
    return sorted(set(result))

def packaged_notices(path):
    result = {}
    def inspect(data, prefix):
        with zipfile.ZipFile(data) as archive:
            for name in archive.namelist():
                base = name.rsplit('/', 1)[-1].lower()
                if base.startswith(('license', 'notice', 'copying')) and archive.getinfo(name).file_size < 1_048_576:
                    content = archive.read(name)
                    if b'\0' not in content:
                        result[prefix+name] = content.decode('utf-8', errors='replace')
                elif name == 'classes.jar':
                    import io
                    inspect(io.BytesIO(archive.read(name)), prefix+name+'!/')
    inspect(path, '')
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fetch', action='store_true', help='Explicit primary-source metadata download')
    parser.add_argument('--check', action='store_true', help='Check exact reviewed dependency/hash coverage offline')
    args = parser.parse_args()
    artifacts = [(coord, Path(filename)) for coord, filename in
                 (line.split('\t') for line in (REPORT/'runtime-artifacts.tsv').read_text().splitlines())]
    if args.check:
        saved = json.loads(MANIFEST.read_text())
        actual = {(coord, path.name, hashlib.sha256(path.read_bytes()).hexdigest()) for coord,path in artifacts}
        reviewed = {(x['coordinate'],x['artifact'],x['sha256']) for x in saved['artifacts']}
        assert actual == reviewed, 'Runtime changed: review licenses and regenerate inventory before distribution'
        assert saved['native']['upstream_sha256'] == hashlib.sha256((ROOT/'third_party/lame/lame-4.0.tar.gz').read_bytes()).hexdigest()
        notices = (ROOT/'app/src/main/assets/licenses/NOTICES.txt').read_text(encoding='utf-8')
        for row in saved['artifacts']:
            extra = supplemental(row['coordinate'])
            if extra:
                notice = Path(__file__).parent / extra['notice']
                assert hashlib.sha256(notice.read_bytes()).hexdigest() == extra['notice_sha256']
                assert notice.read_text(encoding='utf-8') in notices, 'Missing supplemental attribution'
        print(f'Exact reviewed runtime coverage passed: {len(artifacts)} artifacts plus native encoder. No network.')
        return
    def review(item):
        coord, path = item
        licenses, sources = declarations(coord, args.fetch)
        notices = packaged_notices(path)
        return {'coordinate':coord,'artifact':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
                'licenses':licenses,'spdx':identified(licenses,coord),'evidence':sources,
                'embedded_notices':notices}
    with ThreadPoolExecutor(max_workers=6) as pool: rows=list(pool.map(review,artifacts))
    rows.sort(key=lambda x:(x['coordinate'],x['artifact']))
    # Preserve attribution/permission text alongside the readable app licenses.
    notices_path = ROOT/'app/src/main/assets/licenses/NOTICES.txt'
    original = notices_path.read_text(encoding='utf-8').split('\n\nRuntime dependency inventory (generated)\n')[0]
    notice_sections = ['Runtime dependency inventory (generated)',
        '\n'.join(f'{x["coordinate"]} ({", ".join(x["spdx"])})' for x in rows)]
    notice_sections.append((Path(__file__).parent/'notices/BouncyCastle-1.72.txt').read_text(encoding='utf-8'))
    seen = set()
    for row in rows:
        extra = supplemental(row['coordinate'])
        if extra:
            notice = Path(__file__).parent / extra['notice']
            assert hashlib.sha256(notice.read_bytes()).hexdigest() == extra['notice_sha256']
            if extra['notice'] not in seen:
                seen.add(extra['notice'])
                notice_sections.append(notice.read_text(encoding='utf-8'))
    for row in rows:
        for name, content in row['embedded_notices'].items():
            digest=hashlib.sha256(content.encode()).hexdigest()
            if digest not in seen:
                seen.add(digest); notice_sections.append(row['coordinate']+' — '+name+'\n'+content)
    notices_path.write_text(original+'\n\n'+'\n\n'.join(notice_sections)+'\n',encoding='utf-8')
    native = {'name':'LAME','version':'4.0','spdx':'LGPL-2.0-or-later','elected_distribution':'LGPL-2.1',
        'upstream':'https://downloads.sourceforge.net/project/lame/lame/4.0/lame-4.0.tar.gz',
        'upstream_sha256':hashlib.sha256((ROOT/'third_party/lame/lame-4.0.tar.gz').read_bytes()).hexdigest(),
        'linkage':'Separate dynamically linked encoder and LGPL bridge; GPL mpglib decoder excluded',
        'corresponding_source':'Offline assets/licenses/birlevo-mp3-sources.zip, exportable in licenses dialog'}
    MANIFEST.write_text(json.dumps({'scope':'Exact releaseRuntimeClasspath artifacts; conservative before R8, no release built',
        'artifacts':rows,'native':native},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Review inventory generated: {len(rows)} artifacts, {len({x["coordinate"] for x in rows})} modules.')

if __name__ == '__main__': main()
