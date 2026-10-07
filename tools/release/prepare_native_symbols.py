"""Extract only genuine, matching native symbols from an existing APK/AAB. No build or upload.

Match GNU build IDs AND code hashes; a dynamic symbol table alone is insufficient.
Pre-stripped third-party libraries are reported explicitly, never replaced with a fake symbol file.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

def sections(data):
    if data[:4] != b'\x7fELF' or data[5] != 1: raise ValueError('Expected little-endian ELF')
    wide = data[4] == 2
    offset = struct.unpack_from('<Q' if wide else '<I', data, 40 if wide else 32)[0]
    size, count, string_index = struct.unpack_from('<HHH', data, 58 if wide else 46)
    entries = [struct.unpack_from('<IIQQQQIIQQ' if wide else '<IIIIIIIIII', data, offset+i*size) for i in range(count)]
    string_entry = entries[string_index]; names = data[string_entry[4]:string_entry[4]+string_entry[5]]
    return {names[e[0]:names.find(b'\0',e[0])].decode('ascii'): data[e[4]:e[4]+e[5]]
            for e in entries if e[0] and e[1] != 8}

def identity(data):
    table = sections(data); note = table.get('.note.gnu.build-id', b''); build_id = None
    position = 0
    while position+12 <= len(note):
        namesize, descsize, kind = struct.unpack_from('<III',note,position); position += 12
        name = note[position:position+namesize]; position += (namesize+3)//4*4
        desc = note[position:position+descsize]; position += (descsize+3)//4*4
        if kind == 3 and name.rstrip(b'\0') == b'GNU': build_id = desc.hex()
    return {'build_id': build_id, 'text_sha256': hashlib.sha256(table['.text']).hexdigest() if table.get('.text') else None,
            'symbol_table': bool(table.get('.symtab')), 'dwarf': any(n.startswith('.debug_') for n in table)}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--symbol-dir', type=Path, help='Optional unstripped libraries; only matching identities are accepted.')
    parser.add_argument('--require-owned', action='store_true', help='Fail if an MP3 library has no matching symbols.')
    args = parser.parse_args()
    report = {'artifact': args.artifact.name, 'artifact_sha256': hashlib.sha256(args.artifact.read_bytes()).hexdigest(),
              'libraries': [], 'uploadable_libraries': 0, 'missing_symbols': [], 'rejected_candidates': []}
    candidates = {}
    with zipfile.ZipFile(args.artifact) as archive:
        libraries = [n for n in archive.namelist() if re.match(r'^(?:base/)?lib/[^/]+/[^/]+\.so$',n)]
        for name in archive.namelist():
            if name.startswith('BUNDLE-METADATA/com.android.tools.build.debugsymbols/'):
                match = re.search(r'/([^/]+)/(lib[^/]+\.so)(?:\.sym|\.dbg)?$',name)
                if match: candidates.setdefault('/'.join(match.groups()), []).append(archive.read(name))
        if args.symbol_dir:
            for path in args.symbol_dir.rglob('lib*.so*'):
                if path.is_file() and path.parent.name in ['arm64-v8a','armeabi-v7a','x86','x86_64']:
                    name = re.sub(r'\.(sym|dbg)$','',path.name)
                    candidates.setdefault(path.parent.name+'/'+name,[]).append(path.read_bytes())
        verified = {}
        for name in sorted(libraries):
            key = '/'.join(name.split('/')[-2:]); packaged = identity(archive.read(name)); found = None
            for data in candidates.get(key,[]):
                symbol = identity(data)
                # FULL metadata may intentionally omit code (.text becomes NOBITS).
                # GNU build ID still must match; compare code as well whenever present.
                if symbol['symbol_table'] and symbol['build_id'] and symbol['build_id'] == packaged['build_id'] and (symbol['text_sha256'] is None or symbol['text_sha256'] == packaged['text_sha256']):
                    found = symbol; verified[key] = data; break
                report['rejected_candidates'].append({'library':key,'build_id':symbol['build_id']})
            report['libraries'].append({'library':key, **packaged, 'matching_symbols': found})
            if not found: report['missing_symbols'].append(key)
        report['uploadable_libraries'] = len(verified)
        args.output_dir.mkdir(parents=True,exist_ok=True)
        if verified:
            symbol_path = args.output_dir/(args.artifact.stem+'-'+report['artifact_sha256'][:12]+'-native-debug-symbols.zip')
            with zipfile.ZipFile(symbol_path,'w',zipfile.ZIP_DEFLATED) as output:
                for name,data in sorted(verified.items()): output.writestr(name,data)
            report['symbol_zip'] = str(symbol_path)
            report['symbol_zip_sha256'] = hashlib.sha256(symbol_path.read_bytes()).hexdigest()
        report_path = args.output_dir/(args.artifact.stem+'-symbols.json')
        report_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        print(f"{len(verified)}/{len(libraries)} libraries have genuine matching symbols; report: {report_path}")
        if args.require_owned and any(n.endswith(('/libmp3lame.so','/libbirlevo_mp3.so')) for n in report['missing_symbols']):
            raise SystemExit('Owned native symbols missing: release symbol verification failed.')

if __name__ == '__main__': main()
