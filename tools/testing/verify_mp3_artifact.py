"""Check new MP3 native ABI/alignment, offline corresponding source and debug size.

This checks an existing artifact; it never builds/releases anything. ELF alignment
does not prove runtime behavior on a physical 16 KiB device.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]

def elf(data):
    assert data[:4] == b'\x7fELF' and data[5] == 1
    wide = data[4] == 2
    fmt = '<Q' if wide else '<I'
    phoff = struct.unpack_from(fmt,data,32 if wide else 28)[0]
    ents,num = struct.unpack_from('<HH',data,54 if wide else 42)
    loads=[]; relro=None; dynamic=None
    for i in range(num):
        p=phoff+i*ents;kind=struct.unpack_from('<I',data,p)[0]
        off=struct.unpack_from(fmt,data,p+(8 if wide else 4))[0]
        addr=struct.unpack_from(fmt,data,p+(16 if wide else 8))[0]
        size=struct.unpack_from(fmt,data,p+(32 if wide else 16))[0]
        memory=struct.unpack_from(fmt,data,p+(40 if wide else 20))[0]
        align=struct.unpack_from(fmt,data,p+(48 if wide else 28))[0]
        if kind==1: loads.append((off,addr,size,align))
        if kind==2: dynamic=(off,size)
        if kind==0x6474e552: relro=(addr+memory)%16384
    assert loads and min(x[3] for x in loads)>=16384 and relro==0
    assert dynamic is not None
    step=16 if wide else 8; values=[]
    for p in range(dynamic[0],sum(dynamic),step):
        tag,value=struct.unpack_from('<QQ' if wide else '<II',data,p)
        if tag==0:break
        values.append((tag,value))
    table=next(value for tag,value in values if tag==5)
    base=next(off+table-addr for off,addr,size,_ in loads if addr<=table<addr+size)
    needed=[]
    for tag,value in values:
        if tag==1:
            p=base+value;needed.append(data[p:data.index(b'\0',p)].decode('ascii'))
    return {'load_alignments':[x[3] for x in loads],'relro_end_mod_16k':relro,'needed':needed}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--before',type=Path)
    parser.add_argument('--report',type=Path,default=ROOT/'output/development/mp3-expansion/artifact-check.json')
    args=parser.parse_args()
    report={'apk_bytes':args.apk.stat().st_size,'native':[]}
    expected={f'lib/{abi}/lib{name}.so' for abi in ['arm64-v8a','armeabi-v7a','x86','x86_64']
              for name in ['mp3lame','birlevo_mp3']}
    with zipfile.ZipFile(args.apk) as apk:
        actual={n for n in apk.namelist() if n.endswith(('/libmp3lame.so','/libbirlevo_mp3.so'))}
        assert actual==expected,'Missing ABI'
        for name in sorted(actual):
            data=apk.read(name); checked=elf(data)
            if name.endswith('libbirlevo_mp3.so'): assert 'libmp3lame.so' in checked['needed']
            else: assert b'hip_decode' not in data and b'lame_decode' not in data,'Unexpected decoder symbols'
            report['native'].append({'name':name,'bytes':len(data),**checked})
        source=apk.read('assets/licenses/birlevo-mp3-sources.zip')
        import io
        with zipfile.ZipFile(io.BytesIO(source)) as bundle:
            for name in bundle.namelist():
                if name.endswith('/'):continue
                current=ROOT/('app/src/main/java/com/example/contentstudio/audio/LameEncoder.java' if name=='interface/LameEncoder.java' else name)
                assert bundle.read(name)==current.read_bytes(),'Stale source: '+name
        notices=apk.read('assets/licenses/NOTICES.txt').decode('utf-8')
        assert 'LAME 4.0' in notices and 'Bouncy Castle 1.72' in notices and 'Version 2.1, February 1999' in notices
        report['source_zip_bytes']=len(source)
        report['source_zip_sha256']=hashlib.sha256(source).hexdigest()
    if args.before:
        report['before_apk_bytes']=args.before.stat().st_size
        report['apk_increase_bytes']=report['apk_bytes']-report['before_apk_bytes']
    report['passed']=True
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(f'8 native libraries across 4 ABIs: 16 KiB LOAD/RELRO, dynamic linkage, no decoder, exact source and notices passed.')

if __name__=='__main__':main()
