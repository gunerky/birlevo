"""Independently verify the near-limit PDF page resources, rasters and worker samples."""

import argparse
import csv
import hashlib
import json
from pathlib import Path
import re

import numpy as np
from PIL import Image
from pypdf import PdfReader
import pypdfium2 as pdfium


def verify_document(path, expected, manifest, original_renderers):
    reader = PdfReader(path)
    rendered = pdfium.PdfDocument(path)
    assert len(reader.pages) == len(expected)
    try:
        for index, (family, source_index, rotation) in enumerate(expected):
            page = reader.pages[index]
            source = manifest[family]['pages'][source_index]
            assert tuple(map(int, page.mediabox)) == (0, 0, 960, 640)
            assert page.rotation == rotation
            assert source['text'] in page.extract_text()
            resources = page['/Resources']
            assert resources['/Font'] and len(resources['/XObject']) == 1
            image = resources['/XObject']['/Photo'].get_object()
            assert (int(image['/Width']), int(image['/Height'])) == (source['width'], source['height'])
            assert image['/Filter'] == '/FlateDecode'
            assert hashlib.sha256(image._data).hexdigest() == source['encoded_sha256']
            assert hashlib.sha256(image.get_data()).hexdigest() == source['pixels_sha256']
            actual_page, source_page = rendered[index], original_renderers[family][source_index]
            actual = reference = None
            try:
                actual, reference = actual_page.render(scale=1), source_page.render(scale=1, rotation=rotation)
                assert np.array_equal(np.asarray(actual.to_pil()), np.asarray(reference.to_pil())), (path.name, index)
            finally:
                if actual: actual.close()
                if reference: reference.close()
                actual_page.close(); source_page.close()
    finally: rendered.close()
    assert path.stat().st_size <= 128*1024**2
    return {'pages': len(expected), 'bytes': path.stat().st_size, 'encoded_and_decoded_pixels_equal': True,
            'vector_text_preserved': True, 'exact_independent_render_comparison': True}


def memory_samples(path):
    text = path.read_text(encoding='utf-8-sig')
    pss = [int(value) for groups in re.findall(r'^\s*TOTAL\s+(\d+)\b|^\s*TOTAL PSS:\s*(\d+)\b', text, re.M)
           for value in groups if value]
    return {'captured_process_samples': len(pss), 'highest_sampled_pss_kib': max(pss) if pss else None}


def verify_vectors(path):
    reader = PdfReader(path)
    assert len(reader.pages) == 2000
    for index, page in enumerate(reader.pages):
        assert page.rotation == 90 and not page['/Resources'].get('/XObject')
        assert ''.join(page.extract_text().split()) == f'Vectorpage{2000-index}'
    return {'pages':2000, 'all_text_and_rotation_verified':True, 'no_page_rasterization':True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--fixtures', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--vector', type=Path)
    args = parser.parse_args()
    args.report.unlink(missing_ok=True)
    manifest = json.loads((args.fixtures/'manifest.json').read_text(encoding='utf-8'))
    inputs = [pdfium.PdfDocument(args.fixtures/item['file']) for item in manifest]
    evidence = args.folder/'external-pdf'
    merged = [(0, i, 0) for i in range(28)]
    merged.insert(14, (1, 0, 180))
    try:
        result = {
            'input_bytes': sum(item['bytes'] for item in manifest),
            'reordered': verify_document(evidence/'reordered.pdf', [(0, i, 90 if i%2 == 0 else 0) for i in range(27,-1,-1)], manifest, inputs),
            'merged': verify_document(evidence/'merged.pdf', merged, manifest, inputs),
            'extracted': verify_document(evidence/'extracted.pdf', [(0,i,270) for i in (27,13,0)], manifest, inputs),
            'raster_formats': [],
            'worker_samples': {name: memory_samples(args.folder/f'{name}-memory.log') for name in ('pdf_pages','pdf_renderer')},
        }
        expected = [(255,0,0), (0,0,255), (0,255,0), (255,255,0)]
        for page, extension, kind in [(0,'png','PNG'), (13,'jpg','JPEG'), (26,'webp','WEBP')]:
            path = evidence/f'page-{page}.{extension}'
            with Image.open(path) as image:
                assert image.format == kind and image.size == (4096,2731)
                rgb = image.convert('RGB')
                for point, color in zip([(352,290),(3744,290),(352,2441),(3744,2441)], expected):
                    tolerance = 6 if kind == 'PNG' else 15
                    assert max(abs(a-b) for a,b in zip(rgb.getpixel(point),color)) <= tolerance, (kind, point, rgb.getpixel(point))
            result['raster_formats'].append({'format': kind, 'width':4096, 'height':2731, 'bytes':path.stat().st_size})
        with (evidence/'parent-final.csv').open(encoding='utf-8') as stream:
            rows = list(csv.DictReader(stream))
        result['parent_samples'] = {key:max(int(row[key]) for row in rows) for key in ('javaBytes','nativeBytes','pssKiB')}
        result['worker_timing'] = (evidence/'worker-final.txt').read_text(encoding='utf-8')
        if args.vector: result['vector_regression'] = verify_vectors(args.vector)
        args.report.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
        print('60 output PDF pages: exact encoded streams, pixels, vector text and renders. Three 4096 rasters verified.')
    finally:
        for document in inputs: document.close()


if __name__ == '__main__': main()
