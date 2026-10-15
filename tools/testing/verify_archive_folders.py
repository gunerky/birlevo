"""Host check, independent from Android extraction implementation."""
import hashlib
import json
import sys
from pathlib import Path
root = Path(sys.argv[1])
expected = {'hello.txt': 'Exact original: folder/hello.txt'.encode(),
            'unicode.txt': 'Exact original: 日本.txt'.encode(), 'empty.bin': b''}
report = []
for name, data in expected.items():
    actual = (root / name).read_bytes()
    assert actual == data, name
    report.append({'file': name, 'bytes': len(actual), 'sha256': hashlib.sha256(actual).hexdigest()})
(root / 'independent-check.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print('3 selected files: exact bytes, Unicode, nested path payload and empty file verified.')
