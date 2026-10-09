#!/usr/bin/env python3
"""Reject different executable examples in English and Chinese user documentation."""
from pathlib import Path
import re

FENCE = re.compile(r'^```[^\n]*\n(.*?)^```[ \t]*$', re.M | re.S)

def check():
    errors = []
    count = 0
    for english in sorted(Path('docs').rglob('*.md')):
        relative = english.relative_to('docs')
        if 'zh' in relative.parts or 'assets' in relative.parts:
            continue
        chinese = Path('docs/zh') / relative
        if not chinese.exists():
            errors.append(f'{relative}: missing Chinese page')
            continue
        count += 1
        # Keep API calls, configuration, dependency versions and compiled-source
        # references identical; translate explanations outside the fences.
        en, zh = english.read_text(), chinese.read_text()
        if FENCE.findall(en) != FENCE.findall(zh):
            errors.append(f'{relative}: English/Chinese code examples differ')
        # Project policies/changelog are shared root documents; the translated
        # project pages explicitly link to those canonical maintenance records.
        if relative.parts[0] != 'project':
            if re.findall(r'^#{1,6} ', en, re.M) != re.findall(r'^#{1,6} ', zh, re.M):
                errors.append(f'{relative}: translated section structure differs')
            if sum(line.startswith('|') for line in en.splitlines()) != sum(line.startswith('|') for line in zh.splitlines()):
                errors.append(f'{relative}: translated table row coverage differs')
            rows = lambda text: [set(re.findall(r'`([^`]+)`', line)) for line in text.splitlines() if line.startswith('|')]
            if rows(en) != rows(zh):
                errors.append(f'{relative}: technical names in translated tables differ')
            if relative.as_posix() == 'getting-started/installation.md':
                cells = lambda text: [line.rsplit('|', 2)[-2].strip() for line in text.splitlines() if line.startswith('|')][2:]
                # Both artifact tables list their runtime Android requirements.
                numeric = lambda text: [cell for cell in cells(text) if re.fullmatch(r'\d+|—', cell)]
                if numeric(en) != numeric(zh):
                    errors.append(f'{relative}: minSdk values differ')
    if FENCE.findall(Path('README.md').read_text()) != FENCE.findall(Path('README.zh-CN.md').read_text()):
        errors.append('README: English/Chinese examples differ')
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'Checked examples, user-guide sections and table coverage in {count} English/Chinese page pairs')

if __name__ == '__main__':
    check()
