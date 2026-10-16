"""Synthetic host-only OOXML direction probe. Never imported by Android."""
import argparse
import copy
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import xml.etree.ElementTree as ET

A='http://schemas.openxmlformats.org/drawingml/2006/main'
P='http://schemas.openxmlformats.org/presentationml/2006/main'
R='http://schemas.openxmlformats.org/officeDocument/2006/relationships'
for prefix, uri in [('a',A),('p',P),('r',R)]: ET.register_namespace(prefix,uri)
q=lambda name:f'{{{A}}}{name}'

def generate(source:Path, output:Path):
    output.mkdir(parents=True,exist_ok=True)
    cases=[]
    for mode in ('paragraph','run-flags','mixed-runs'):
        with ZipFile(source) as archive:
            root=ET.fromstring(archive.read('ppt/slides/slide2.xml'))
            for shape in root.findall(f'.//{{{P}}}sp'):
                name=shape.find(f'{{{P}}}nvSpPr/{{{P}}}cNvPr').get('name')
                if name not in ('Heading','Body'):continue
                body=shape.find(f'{{{P}}}txBody')
                paragraphs=body.findall(q('p'))
                for index,paragraph in enumerate(paragraphs):
                    props=paragraph.find(q('pPr'))
                    props.set('rtl','1');props.set('algn','r')
                    original=paragraph.find(q('r'))
                    chunks=[(''.join(t.text or '' for t in paragraph.iter(q('t'))),True,'ar' if index==0 else 'fa')]
                    if mode=='mixed-runs' and name=='Body':
                        chunks=([('مرحبا ',True,'ar'),('report_2026.pdf ',False,'en-US'),('بالعالم ',True,'ar'),('١٢٣',False,'ar')]
                            if index==0 else [('آزمایش ',True,'fa'),('file.pdf ',False,'en-US'),('برای همه ',True,'fa'),('۱۲۳',False,'fa')])
                    for node in list(paragraph):
                        if node.tag in (q('r'),q('br')):paragraph.remove(node)
                    for text,rtl,language in chunks:
                        run=copy.deepcopy(original)
                        run.find(q('t')).text=text
                        rp=run.find(q('rPr'))
                        rp.set('lang',language)
                        cs=rp.find(q('cs'))
                        if cs is None:cs=ET.SubElement(rp,q('cs'))
                        cs.set('typeface','Arial')
                        if mode!='paragraph':ET.SubElement(rp,q('rtl'),{'val':'1' if rtl else '0'})
                        paragraph.append(run)
            target=output/f'{mode}.pptx'
            assert source.resolve()!=target.resolve()
            with ZipFile(target,'w',ZIP_DEFLATED) as new:
                for item in archive.infolist():
                    new.writestr(item,ET.tostring(root,encoding='utf-8',xml_declaration=True) if item.filename=='ppt/slides/slide2.xml' else archive.read(item))
            cases.append({'file':target.name,'body':[''.join(t.text or '' for t in p.iter(q('t'))) for p in root.findall(f'.//{{{P}}}sp') if p.find(f'{{{P}}}nvSpPr/{{{P}}}cNvPr').get('name')=='Body'], 'mode':mode})
    (output/'cases.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();generate(args.source,args.output)
