"""Check tracked/staged paths and common credential signatures without printing values.

This is a narrow repository hygiene check, not proof that no secret exists.
"""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = [
    re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})"),
    re.compile(rb"AKIA[A-Z0-9]{16}"),
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(rb"(?:sk-[A-Za-z0-9_-]{32,}|AIza[0-9A-Za-z_-]{35})"),
    re.compile(rb'(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret|storePassword|keyPassword)\s*[=:]\s*[\"\x27]([A-Za-z0-9_+/=-]{24,})[\"\x27]'),
]
FORBIDDEN = re.compile(r"(?i)(?:^|/)(?:\.env(?:\..*)?|local\.properties|key\.properties|signing\.properties|secrets\.properties)$|\.(?:jks|keystore|p12|pfx|clixml|pem|key|apk|aab)$|^(?:output|graphify-out|\.gradle|\.idea)/|(?:^|/)build/")

def check():
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    errors = []
    for name in filter(None, paths):
        if FORBIDDEN.search(name) and not name.endswith('.env.example'):
            errors.append(name + ': forbidden local/generated path')
            continue
        data = subprocess.check_output(['git', 'show', ':' + name], cwd=ROOT)
        if b'\0' not in data and any(pattern.search(data) for pattern in PATTERNS):
            errors.append(name + ': possible credential signature')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Repository hygiene passed: {len(list(filter(None, paths)))} indexed files; no values printed.')
    return 0

if __name__ == '__main__':
    sys.exit(check())
