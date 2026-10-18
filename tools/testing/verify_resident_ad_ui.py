"""Validate named emulator logs and same-process SDK evidence, not application assertions alone."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('folder', type=Path)
parser.add_argument('--app', type=Path, required=True)
parser.add_argument('--instrumentation', type=Path, nargs='+', required=True)
parser.add_argument('--report', type=Path, required=True)
parser.add_argument('--cases', nargs='+', help='Explicit selected matrix for a focused follow-up profile')
args = parser.parse_args()
cases = ['picker','routes','inspector','image-save','save-failure','late-save','larger-copy',
         'new-project','append-project','share-open','grant','photo-pdf','audio','video','presentation','presenter']
if args.cases:
    cases = args.cases
assert len(cases)==len(set(cases)) and all(re.fullmatch(r'[a-z-]+',c) for c in cases)
app_hash = hashlib.sha256(args.app.read_bytes()).hexdigest()
test_hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in args.instrumentation]
artifacts = {'application':app_hash, 'instrumentation':test_hashes}
records = []
for case in cases:
    folder = args.folder/case
    stamp = json.loads((folder/'artifacts.json').read_text(encoding='utf-8-sig'))
    assert stamp['application']==app_hash and stamp['instrumentation'] in test_hashes, (case,'Artifact mismatch')
    text = (folder/'tests.log').read_text(encoding='utf-8-sig')
    assert re.search(r'OK \(1 test\)',text), case
    assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[34]',text), case
    proof = json.loads((folder/'proof.json').read_text())
    assert proof['passed'] is True, case
    assert proof['case'].split('-',1)[1] in text, (case,'Mismatched test identity')
    rows = list(csv.DictReader((folder/'memory.csv').read_text().splitlines()))
    before = [r for r in rows if r['stage']=='before-consent']
    assert len(before)==1 and before[0]['sdkInitialized']=='false', case
    resident = [r for r in rows if r['stage']!='before-consent']
    assert {'resident-sdk','workload','completed'} <= {r['stage'] for r in resident}, case
    assert all(int(r['pid'])==proof['pid'] for r in rows), (case,'Process restart')
    assert all(r['sdkInitialized']=='true' for r in resident), case
    records.append({'case':case,'test':proof['case'],'age':proof['ageTreatment'],'artifacts':stamp,
                    'elapsedMs':proof['elapsedMs'],'samples':len(rows),
                    'sampledParentPssKiB':max(int(r['pssKiB']) for r in rows)})
args.report.parent.mkdir(parents=True,exist_ok=True)
args.report.write_text(json.dumps({'artifacts':artifacts,'cases':records,'passed':len(records)},indent=2)+'\n')
print(f'{len(records)} UI cases: exact artifacts, no skips, same PID and initialized SDK during work verified.')
