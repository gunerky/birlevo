"""Generate deterministic, image-heavy external PDFs for host/emulator QA only."""

import argparse
import hashlib
import json
from pathlib import Path
import zlib

import numpy as np
from pypdf import PdfWriter
from pypdf.generic import (ArrayObject, DecodedStreamObject, DictionaryObject,
                          EncodedStreamObject, NameObject, NumberObject)


def make(path, count, small=False):
    writer = PdfWriter()
    manifest = []
    font = writer._add_object(DictionaryObject({NameObject('/Type'): NameObject('/Font'),
        NameObject('/Subtype'): NameObject('/Type1'), NameObject('/BaseFont'): NameObject('/Helvetica')}))
    for index in range(count):
        width, height = (96, 64) if small else (1600, 1024)
        pixels = np.random.default_rng(20261016 + index + (100 if small else 0)).integers(
            0, 256, (height, width, 3), dtype=np.uint8)
        marker = 12 if small else 128
        pixels[:marker, :marker] = [255, 0, 0]
        pixels[:marker, -marker:] = [0, 0, 255]
        pixels[-marker:, :marker] = [0, 255, 0]
        pixels[-marker:, -marker:] = [255, 255, 0]
        encoded = zlib.compress(pixels.tobytes(), level=1)
        image = EncodedStreamObject()
        image._data = encoded
        image.update({NameObject('/Type'): NameObject('/XObject'), NameObject('/Subtype'): NameObject('/Image'),
            NameObject('/Width'): NumberObject(width), NameObject('/Height'): NumberObject(height),
            NameObject('/ColorSpace'): NameObject('/DeviceRGB'), NameObject('/BitsPerComponent'): NumberObject(8),
            NameObject('/Filter'): NameObject('/FlateDecode')})
        label = f'External {"second" if small else "heavy"} page {index+1}'
        content = DecodedStreamObject()
        content.set_data((f'q 864 0 0 576 48 32 cm /Photo Do Q\n'
                          f'BT /F1 12 Tf 48 620 Td ({label}) Tj ET\n').encode('ascii'))
        page = writer.add_blank_page(width=960, height=640)
        page[NameObject('/Resources')] = DictionaryObject({
            NameObject('/XObject'): DictionaryObject({NameObject('/Photo'): writer._add_object(image)}),
            NameObject('/Font'): DictionaryObject({NameObject('/F1'): font})})
        page[NameObject('/Contents')] = writer._add_object(content)
        manifest.append({'text': label, 'width': width, 'height': height,
            'encoded_sha256': hashlib.sha256(encoded).hexdigest(),
            'pixels_sha256': hashlib.sha256(pixels.tobytes()).hexdigest()})
    writer.write(path)
    return {'file': path.name, 'bytes': path.stat().st_size, 'pages': manifest}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    args = parser.parse_args()
    args.folder.mkdir(parents=True, exist_ok=True)
    fixtures = [make(args.folder/'heavy.pdf', 28), make(args.folder/'second.pdf', 1, small=True)]
    total = sum(item['bytes'] for item in fixtures)
    assert 120*1024**2 < total < 128*1024**2, total
    (args.folder/'manifest.json').write_text(json.dumps(fixtures, indent=2)+'\n', encoding='utf-8')
    print(f'Created two PDFs: {total:,} bytes, 29 pages, distinct encoded lossless images and vector text.')


if __name__ == '__main__': main()
