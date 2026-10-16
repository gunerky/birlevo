"""Independent end-to-end check: converted hash -> exported ZIP -> actual SAF extraction.

Use the bundled Python (Pillow/numpy/pypdf) and pass the local host FFmpeg executable.
This file is a development test, never an Android dependency.
"""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
from xml.etree import ElementTree as ET
from zipfile import ZipFile
from PIL import Image
from pypdf import PdfReader

parser=argparse.ArgumentParser()
parser.add_argument('folder',type=Path)
parser.add_argument('--ffmpeg',type=Path,required=True)
args=parser.parse_args()
root=args.folder.resolve()
assert args.ffmpeg.is_file()
codec={'MP4_AVC':'h264','MP4_HEVC':'hevc','WEBM_VP8':'vp8','WEBM_VP9':'vp9',
       'WAV':'pcm_s16le','M4A':'aac','OGG':'opus','FLAC':'flac','MP3':'mp3'}
word={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
sheet={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
reports=[]
targets={kind:set() for kind in ['image','video','audio','document']}
archive_count=0
for manifest_name in ['image','video','audio','document','selection','process','empty']:
    manifest=json.loads((root/f'{manifest_name}-manifest.json').read_text(encoding='utf-8'))
    for record in manifest['outputs']:
        label=record['label'];assert re.fullmatch(r'[a-z0-9_-]+',label)
        if manifest_name in targets:
            key={'image':'imageFormat','video':'videoFormat','audio':'audioTarget','document':'documentTarget'}[manifest_name]
            targets[manifest_name].add(record[key])
        archive=root/f'{label}.zip'
        assert hashlib.sha256(archive.read_bytes()).hexdigest()==record['zipSha256']
        archive_count+=1
        with ZipFile(archive) as zipped:
            assert zipped.testzip() is None
            names=[entry['name'] for entry in record['entries']]
            assert zipped.namelist()==names
            assert len(names)==len(set(name.casefold() for name in names))
            for ordinal,entry in enumerate(record['entries'],1):
                name=entry['name']
                assert re.fullmatch(r'\d{2}_[^/\\\x00]+\.[a-z0-9]+',name)
                assert name.startswith(f'{ordinal:02d}_')
                packed=zipped.read(name)
                assert len(packed)==entry['bytes']
                assert hashlib.sha256(packed).hexdigest()==entry['sha256']
            extracted=record['extracted']
            assert len(extracted)==(1 if manifest_name=='selection' else len(names))
            for entry in extracted:
                name=entry['name'];file=root/entry['extracted']
                assert file.parent==root and file.name==f'{label}--{name}'
                data=file.read_bytes()
                assert data==zipped.read(name)
                assert len(data)==entry['bytes'] and hashlib.sha256(data).hexdigest()==entry['sha256']
                kind=record['kind']
                if kind=='IMAGE':
                    with Image.open(io.BytesIO(data)) as image:
                        image.load();assert image.format==record['imageFormat']
                        scale=min(1,record['edge']/max(entry['sourceWidth'],entry['sourceHeight']))
                        size=tuple(max(1,int(entry[key]*scale)) for key in ['sourceWidth','sourceHeight'])
                        assert image.size==size,(name,image.size,size)
                        if entry['source']=='tiny.png':
                            pixel=image.convert('RGBA').getpixel((0,0))
                            if image.format in ('PNG','WEBP'): assert pixel[3]==0
                            else: assert min(pixel[:3])>=240 and pixel[3]==255
                elif kind in ('VIDEO','AUDIO'):
                    decoded=subprocess.run([str(args.ffmpeg),'-v','error','-i',str(file),'-f','null','-'],capture_output=True,check=True)
                    assert not decoded.stderr,(name,decoded.stderr.decode(errors='replace'))
                    info=subprocess.run([str(args.ffmpeg),'-hide_banner','-i',str(file)],capture_output=True).stderr.decode(errors='replace')
                    timing=re.search(r'Duration: (\d+):(\d+):([\d.]+)',info);assert timing,info
                    seconds=int(timing[1])*3600+int(timing[2])*60+float(timing[3])
                    assert abs(seconds-entry['durationMs']/1000)<.25,(name,seconds,entry)
                    if kind=='VIDEO':
                        video=re.search(r'Video: ([^ ,]+)',info);assert video and video[1]==codec[record['videoFormat']],info
                        scale=min(1,record['edge']/max(entry['sourceWidth'],entry['sourceHeight']))
                        size=tuple(max(2,int(entry[key]*scale)//2*2) for key in ['sourceWidth','sourceHeight'])
                        # Coded width/height can be reversed by a valid display rotation matrix.
                        frame=subprocess.check_output([str(args.ffmpeg),'-v','error','-i',str(file),'-frames:v','1',
                            '-f','image2pipe','-c:v','png','-'])
                        with Image.open(io.BytesIO(frame)) as image:
                            image.load();assert image.size==size,(name,image.size,size)
                        assert 'Audio:' in info
                        assert ('Audio: opus' in info) if record['videoFormat'].startswith('WEBM') else ('Audio: aac' in info)
                    else:
                        assert 'Video:' not in info
                        audio=re.search(r'Audio: ([^ ,]+).*?, (\d+) Hz, ([^,\r\n]+)',info);assert audio,info
                        assert audio[1]==codec[record['audioTarget']] and int(audio[2])==entry['sampleRate']
                        assert audio[3].strip()==('mono' if entry['channels']==1 else 'stereo')
                else:
                    target=record['documentTarget']
                    if target in ('CSV','TSV') or (target=='TXT' and entry['documentKind']!='TEXT'):
                        rows=list(csv.reader(io.StringIO(data.decode('utf-8'),newline=''),delimiter=',' if target=='CSV' else '\t',strict=True))
                        assert rows==entry['rows']
                    elif target=='TXT': assert data.decode('utf-8')==entry['text']
                    elif target=='DOCX':
                        with ZipFile(io.BytesIO(data)) as doc:
                            assert doc.testzip() is None
                            xml=ET.fromstring(doc.read('word/document.xml'))
                            text='\n'.join(''.join(n.text or '' if n.tag==f"{{{word['w']}}}t" else '\t' if n.tag==f"{{{word['w']}}}tab" else '\n' if n.tag in (f"{{{word['w']}}}br",f"{{{word['w']}}}cr") else '' for n in p.iter()) for p in xml.findall('.//w:p',word))
                            assert re.sub(r'\s+',' ',text).strip()==re.sub(r'\s+',' ',entry['text']).strip()
                    elif target=='XLSX':
                        with ZipFile(io.BytesIO(data)) as book:
                            assert book.testzip() is None
                            xml=ET.fromstring(book.read('xl/worksheets/sheet1.xml'));assert not xml.findall('.//s:f',sheet)
                            rows=[]
                            for row in xml.findall('s:sheetData/s:row',sheet):
                                values=[]
                                for cell in row.findall('s:c',sheet):
                                    assert cell.get('t')=='inlineStr'
                                    values.append(''.join(n.text or '' for n in cell.findall('.//s:t',sheet)))
                                rows.append(values)
                            assert rows==entry['rows']
                    elif target=='PDF':
                        pdf=PdfReader(io.BytesIO(data),strict=True);assert 1<=len(pdf.pages)<=300
                        text='\n'.join(page.extract_text() or '' for page in pdf.pages)
                        if entry['source']=='records.csv': assert '00123' in text and 'Name' in re.sub(r'\s+','',text)
                        if 'Second line' in entry['text']: assert 'Second line' in text
                    else: raise AssertionError(target)
                reports.append({'zip':archive.name,'extracted':file.name,'bytes':len(data),'sha256':entry['sha256'],'kind':kind})
assert targets=={'image':{'JPEG','PNG','WEBP','BMP'},'video':{'MP4_AVC','MP4_HEVC','WEBM_VP8','WEBM_VP9'},
                 'audio':{'WAV','M4A','OGG','FLAC','MP3'},'document':{'TXT','CSV','TSV','PDF','DOCX','XLSX'}},targets
assert archive_count==22 and len(reports)==50,(archive_count,len(reports))
(root/'independent-check.json').write_text(json.dumps({'archives':archive_count,'extractedFiles':reports},indent=2)+'\n',encoding='utf-8')
print(f'{archive_count} exported ZIPs / {len(reports)} actual SAF extractions: exact bytes, hashes and independent format decode passed.')
