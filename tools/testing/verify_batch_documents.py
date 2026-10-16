"""Independent ZIP/OOXML/CSV/PDF verification; run with the bundled Python (pypdf)."""
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import sys
from xml.etree import ElementTree as ET
from zipfile import ZipFile
from pypdf import PdfReader

root=Path(sys.argv[1])
manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
records=[]
word={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
sheet={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
def plain(value): return re.sub(r'\s+',' ',value).strip()
for output in manifest['outputs']:
    target=output['target']
    with ZipFile(root/f'{target.lower()}.zip') as archive:
        assert archive.testzip() is None
        assert archive.namelist()==[e['name'] for e in output['entries']]
        for entry in output['entries']:
            data=archive.read(entry['name']); assert hashlib.sha256(data).hexdigest()==entry['sha256']
            if target in ('CSV','TSV') or (target=='TXT' and entry['kind']!='TEXT'):
                rows=list(csv.reader(io.StringIO(data.decode('utf-8'),newline=''),delimiter=',' if target=='CSV' else '\t',strict=True))
                assert rows==entry['rows'],entry['name']
            elif target=='TXT': assert data.decode('utf-8')==entry['text']
            elif target=='DOCX':
                with ZipFile(io.BytesIO(data)) as doc:
                    assert doc.testzip() is None
                    xml=ET.fromstring(doc.read('word/document.xml'))
                    # Word tabs/breaks are elements, not XML text nodes.
                    text='\n'.join(''.join(n.text or '' if n.tag==f"{{{word['w']}}}t" else
                        '\t' if n.tag==f"{{{word['w']}}}tab" else '\n' if n.tag in (f"{{{word['w']}}}br",f"{{{word['w']}}}cr") else ''
                        for n in p.iter()) for p in xml.findall('.//w:p',word))
                    assert plain(text)==plain(entry['text']), (entry['name'],text,entry['text'])
            elif target=='XLSX':
                with ZipFile(io.BytesIO(data)) as book:
                    assert book.testzip() is None
                    xml=ET.fromstring(book.read('xl/worksheets/sheet1.xml'))
                    assert not xml.findall('.//s:f',sheet)
                    rows=[]
                    for row in xml.findall('s:sheetData/s:row',sheet):
                        values=[]
                        for cell in row.findall('s:c',sheet):
                            assert cell.get('t')=='inlineStr'
                            values.append(''.join(n.text or '' for n in cell.findall('.//s:t',sheet)))
                        rows.append(values)
                    assert rows==entry['rows'],entry['name']
            elif target=='PDF':
                pdf=PdfReader(io.BytesIO(data),strict=True); assert 1<=len(pdf.pages)<=300
                text='\n'.join(p.extract_text() or '' for p in pdf.pages)
                expected=entry['text']+' '+str(entry['rows'])
                if 'Second line' in expected: assert 'Second line' in text
                # PDF extractors can infer spaces from glyph positions (e.g. 'N ame').
                if entry['source']=='records.csv': assert '00123' in text and 'Name' in re.sub(r'\s+','',text)
            else: raise AssertionError(target)
            records.append({'target':target,'entry':entry['name'],'bytes':len(data),'sha256':entry['sha256']})
assert len(records)==16,len(records)
with ZipFile(root/'formulas.zip') as archive:
    name=next(n for n in archive.namelist() if 'shared-no-cache' in n)
    rows=list(csv.reader(io.StringIO(archive.read(name).decode('utf-8'),newline='')))
    assert rows[0][0]=='00123' and rows[0][2]=='=SUM(B1:B2)' and rows[0][3]=='46309'
with ZipFile(root/'empty.zip') as archive:
    assert archive.testzip() is None and len(archive.infolist())==1
    assert archive.read(archive.namelist()[0])==b''
(root/'independent-check.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Independent checks passed: 16 documents, formula text/raw values, empty text ZIP, CRC and hashes.')
