"""Extract actual selector methods for an isolated native regression executable.

No live Telegram state or auth keys are read. This does not test sockets, crypto,
Android UI, or the complete APK. The real header and selector bodies are compiled.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
source_path = Path(sys.argv[2]) if len(sys.argv) > 2 else root / 'TMessagesProj/jni/tgnet/Datacenter.cpp'
source = source_path.read_text(encoding='utf-8-sig')
names = ['getCurrentAddress', 'getCurrentPort', 'addAddressAndPort',
         'nextAddressOrPort', 'isCustomPort', 'replaceAddresses',
         'shouldUseTemporaryAddresses']
patterns = [r'Datacenter::Datacenter\(int32_t instance, uint32_t id\)']
patterns += [rf'[^\n]*Datacenter::{name}\([^\n]*\)' for name in names]
bodies = []
for pattern in patterns:
    match = re.search(pattern, source)
    if not match:
        if 'shouldUseTemporaryAddresses' in pattern:
            continue  # Baseline has the condition duplicated inline.
        raise RuntimeError(f'Missing production method: {pattern}')
    opening = source.index('{', match.end())
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    bodies.append(source[match.start():end])
template = (root / 'tests/temp_address_recovery.cpp').read_text()
Path(sys.argv[1]).write_text(template.replace('// PRODUCTION_METHODS', '\n\n'.join(bodies)))
