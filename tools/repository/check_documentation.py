"""Validate tracked local Markdown/HTML link paths without network access.

Heading fragments are outside this check. Ignored QA/release outputs are counted
separately: historical reports may reference evidence that is only available locally.
"""

from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
INLINE_LINK = re.compile(r'\]\((?:<([^>\n]+)>|([^\s)]+))(?:\s+"[^"]*")?\)')
HTML_LINK = re.compile(r'''(?:href|src)=["']([^"']+)["']''', re.IGNORECASE)
REFERENCE_LINK = re.compile(r'^ {0,3}\[[^\]^]+\]:\s*(?:<([^>]+)>|(\S+))')
FENCE = re.compile(r'^ {0,3}(`{3,}|~{3,})(.*)$')
INLINE_CODE = re.compile(r'(`+).*?\1')
SCHEME = re.compile(r'^[A-Za-z][A-Za-z0-9+.-]*:')
LOCAL_OUTPUTS = ('output/', 'graphify-out/', 'build/', 'app/build/', 'test-artifacts/')


def references(text):
    fence = None
    for number, line in enumerate(text.splitlines(), 1):
        marker = FENCE.match(line)
        if marker:
            run, rest = marker.groups()
            if fence is None:
                fence = (run[0], len(run))
            elif run[0] == fence[0] and len(run) >= fence[1] and not rest.strip():
                fence = None
            continue
        if fence is not None:
            continue
        line = INLINE_CODE.sub('', line)
        for match in INLINE_LINK.finditer(line):
            yield number, match[1] or match[2]
        for match in HTML_LINK.finditer(line):
            yield number, match[1]
        reference = REFERENCE_LINK.match(line)
        if reference:
            yield number, reference[1] or reference[2]


def check():
    names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    tracked = {name for name in names if name}
    directories = {str(parent).replace('\\', '/') for name in tracked for parent in Path(name).parents}
    markdown = sorted(name for name in tracked if name.endswith('.md'))
    errors = []
    checked = generated = 0
    for name in markdown:
        source = ROOT / name
        if not source.is_file():
            errors.append(f'{name}: tracked document is missing; stage intended moves before checking')
            continue
        for number, url in references(source.read_text(encoding='utf-8')):
            if SCHEME.match(url) or url.startswith(('/', '#')):
                continue
            path = unquote(urlsplit(url).path)
            if not path:
                continue
            target = (source.parent / path).resolve()
            if not target.is_relative_to(ROOT):
                errors.append(f'{name}:{number}: local link escapes the repository: {url}')
                continue
            relative = target.relative_to(ROOT).as_posix()
            if relative.startswith(LOCAL_OUTPUTS):
                generated += 1
                continue
            checked += 1
            if relative not in tracked and relative not in directories:
                errors.append(f'{name}:{number}: local target is not tracked: {url}')
            elif not target.exists():
                errors.append(f'{name}:{number}: local target is missing: {url}')
    if errors:
        print('\n'.join(errors))
        return 1
    print(f'Documentation links passed: {len(markdown)} Markdown files, {checked} tracked local targets.')
    print(f'{generated} generated-artifact references require local outputs; external URLs and fragments were not checked.')
    return 0


if __name__ == '__main__':
    raise SystemExit(check())
