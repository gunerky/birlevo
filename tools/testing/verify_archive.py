"""Independent stdlib verification of emulator ZIP extraction evidence."""
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile
root = Path(sys.argv[1])
outputs = {'folder/hello.txt': 'hello.txt', '日本.txt': 'unicode.txt', 'empty.bin': 'empty.bin'}
report = []
with ZipFile(root / 'source.zip') as archive:
    assert archive.testzip() is None
    assert len(archive.infolist()) == 4
    for name, output in outputs.items():
        original = archive.read(name)
        actual = (root / output).read_bytes()
        assert actual == original, name
        report.append({'entry': name, 'output': output, 'bytes': len(actual), 'sha256': hashlib.sha256(actual).hexdigest()})
(root / 'independent-check.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('Independent ZIP CRC and 3 exact extracted file hashes passed.')
