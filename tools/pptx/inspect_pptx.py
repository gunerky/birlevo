"""Bounded, offline PPTX content inventory. Not a renderer or a lossless importer.

No extraction to disk, external relationship fetching, macros, or user media upload.
Accepts UTF-8 Transitional PresentationML only. Reports losses before model mapping.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import posixpath
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET
from zipfile import ZipFile

P = 'http://schemas.openxmlformats.org/presentationml/2006/main'
A = 'http://schemas.openxmlformats.org/drawingml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
REL = 'http://schemas.openxmlformats.org/package/2006/relationships'
MAX_XML = 2 * 1024 * 1024
MAX_PART = 32 * 1024 * 1024
MAX_TOTAL = 128 * 1024 * 1024


class Rejected(ValueError):
    pass


def checked_xml(data):
    if len(data) > MAX_XML:
        raise Rejected('XML part too large')
    try:
        text = data.decode('utf-8-sig')
        if '\x00' in text or '<!DOCTYPE' in text.upper() or '<!ENTITY' in text.upper():
            raise Rejected('DTD/entities or non-UTF-8 XML rejected')
        root = ET.fromstring(text)
    except (UnicodeError, ET.ParseError) as exc:
        raise Rejected('Invalid or unsupported XML') from exc
    pending, count = [(root, 1)], 0
    while pending:
        node, depth = pending.pop()
        count += 1
        if count > 50000 or depth > 100:
            raise Rejected('XML complexity limit')
        pending.extend((child, depth + 1) for child in node)
    return root


def target_part(owner, target):
    decoded = unquote(target)
    parsed = urlsplit(decoded)
    if parsed.scheme or parsed.netloc or parsed.query or parsed.fragment or '\\' in decoded:
        raise Rejected('Invalid internal relationship URI')
    candidate = posixpath.normpath(decoded.lstrip('/') if decoded.startswith('/') else posixpath.join(posixpath.dirname(owner), decoded))
    if candidate in ('', '.', '..') or candidate.startswith('../') or ':' in candidate:
        raise Rejected('Relationship escapes package')
    return candidate


def paragraphs(root):
    result = []
    for paragraph in root.iter(f'{{{A}}}p'):
        result.append(''.join('\n' if node.tag == f'{{{A}}}br' else (node.text or '') if node.tag == f'{{{A}}}t' else '' for node in paragraph.iter()))
    return '\n'.join(result)


def inspect(file):
    file = Path(file)
    if file.stat().st_size > 64 * 1024 * 1024:
        raise Rejected('Compressed package limit')
    with ZipFile(file) as archive:
        entries = archive.infolist()
        if len(entries) > 2000:
            raise Rejected('Package entry limit')
        names, total, parts = set(), 0, {}
        for entry in entries:
            name = entry.filename
            if name in names or name.startswith('/') or '\\' in name or ':' in name or '..' in PurePosixPath(name).parts or posixpath.normpath(name).rstrip('/') != name.rstrip('/'):
                raise Rejected('Duplicate or unsafe part name')
            names.add(name)
            total += entry.file_size
            if entry.flag_bits & 1 or entry.file_size > MAX_PART or total > MAX_TOTAL:
                raise Rejected('Encrypted or oversized package')
            if name.lower().endswith('.bin') or 'vbaproject' in name.lower():
                raise Rejected('Binary embedded content/macros outside trial scope')
            if not entry.is_dir():
                # Bound actual bytes too, independent of declared ZIP sizes.
                with archive.open(entry) as stream:
                    data = stream.read(MAX_PART + 1)
                    if len(data) > MAX_PART:
                        raise Rejected('Inflated part limit')
                    parts[name] = data
        xml = {name: checked_xml(data) for name, data in parts.items() if name.endswith(('.xml', '.rels'))}
        if '[Content_Types].xml' not in xml:
            raise Rejected('Missing content types')
        relationships, external = {}, []
        for name, root in xml.items():
            if not name.endswith('.rels'):
                continue
            if root.tag != f'{{{REL}}}Relationships':
                raise Rejected('Unsupported relationship namespace')
            owner = '' if name == '_rels/.rels' else posixpath.join(posixpath.dirname(posixpath.dirname(name)), posixpath.basename(name)[:-5])
            links = {}
            for rel in root:
                rid, target, kind = rel.get('Id'), rel.get('Target'), rel.get('Type', '')
                if not rid or not target or rid in links:
                    raise Rejected('Invalid/duplicate relationship')
                mode = rel.get('TargetMode', 'Internal')
                if mode == 'External':
                    external.append({'owner': owner, 'type': kind.rsplit('/', 1)[-1]})
                    links[rid] = (None, kind)
                elif mode == 'Internal':
                    part = target_part(owner, target)
                    if part not in parts:
                        raise Rejected('Missing relationship target')
                    links[rid] = (part, kind)
                else:
                    raise Rejected('Unknown relationship mode')
            relationships[owner] = links
        office = [part for part, kind in relationships.get('', {}).values() if kind == R + '/officeDocument']
        if len(office) != 1 or office[0] not in xml:
            raise Rejected('Missing/ambiguous presentation root')
        presentation = xml[office[0]]
        if presentation.tag != f'{{{P}}}presentation':
            raise Rejected('Strict/encrypted/non-PPTX format outside trial scope')
        size = presentation.find(f'{{{P}}}sldSz')
        if size is None:
            raise Rejected('Missing slide size')
        dimensions = [int(size.get(key, '0')) for key in ('cx', 'cy')]
        if min(dimensions) <= 0:
            raise Rejected('Invalid slide size')
        ids = presentation.findall(f'{{{P}}}sldIdLst/{{{P}}}sldId')
        if not 1 <= len(ids) <= 100 or len({item.get('id') for item in ids}) != len(ids):
            raise Rejected('Slide count/identity limit')
        slides = []
        for item in ids:
            link = relationships.get(office[0], {}).get(item.get(f'{{{R}}}id'))
            if not link or link[1] != R + '/slide' or link[0] not in xml:
                raise Rejected('Broken slide reference')
            part = link[0]
            root = xml[part]
            if root.tag != f'{{{P}}}sld':
                raise Rejected('Invalid slide root')
            issues = {'layout-not-imported', 'font-substitution-possible'}
            mappings = {'tbl': 'table', 'chart': 'chart', 'timing': 'animation', 'transition': 'transition', 'grpSp': 'group', 'videoFile': 'video', 'audioFile': 'audio', 'oleObj': 'embedded-object', 'AlternateContent': 'alternate-content', 'srcRect': 'image-crop', 'custGeom': 'custom-geometry'}
            for node in root.iter():
                local = node.tag.rsplit('}', 1)[-1]
                if local in mappings:
                    issues.add(mappings[local])
                if local == 'xfrm' and (node.get('rot', '0') != '0' or node.get('flipH', '0') not in ('0', 'false')):
                    issues.add('transform')
            pictures = root.findall(f'.//{{{P}}}pic')
            if len(pictures) > 1:
                issues.add('multiple-images')
            shapes = root.findall(f'.//{{{P}}}sp')
            if len(shapes) > 2:
                issues.add('extra-shapes-or-textboxes')
            image_parts = []
            for node in root.iter():
                for key in (f'{{{R}}}embed', f'{{{R}}}link', f'{{{R}}}id'):
                    if key not in node.attrib:
                        continue
                    related = relationships.get(part, {}).get(node.get(key))
                    if related is None:
                        raise Rejected('Broken slide object relationship')
                    if related[0] is None:
                        issues.add('external-content-not-loaded')
                    elif related[1] == R + '/image':
                        image_parts.append({'part': related[0], 'sha256': hashlib.sha256(parts[related[0]]).hexdigest()})
            notes = []
            for note_part, kind in relationships.get(part, {}).values():
                if kind == R + '/notesSlide' and note_part in xml:
                    for shape in xml[note_part].iter(f'{{{P}}}sp'):
                        placeholder = shape.find(f'.//{{{P}}}ph')
                        if placeholder is not None and placeholder.get('type') == 'body':
                            notes.append(paragraphs(shape))
            slides.append({'part': part, 'text': paragraphs(root), 'notes': '\n'.join(notes), 'pictures': image_parts, 'issues': sorted(issues)})
        return {'file': file.name, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'size_emu': dimensions, 'slide_count': len(slides), 'slides': slides, 'external_relationships': external, 'general_import_ready': False, 'scope': 'content inventory only; no layout/model conversion'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('file', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = inspect(args.file)
    encoded = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding='utf-8')
        print(f'Inventoried {result["slide_count"]} slides; no layout fidelity claimed.')
    else:
        print(encoded)
