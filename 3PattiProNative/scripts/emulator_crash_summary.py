#!/usr/bin/env python3
"""Print only fault codes/module basenames from CI emulator minidumps.

Raw memory, stack bytes, annotations, environment and dump files are never emitted
or uploaded; they can contain credentials. This is best-effort host diagnostics.
"""
import json
import struct
from pathlib import Path


def summarize(data):
    if data[:4] != b'MDMP':
        raise ValueError('not minidump')
    count, directory = struct.unpack_from('<II', data, 8)
    if count > 4096:
        raise ValueError('stream count')
    streams = {}
    for i in range(count):
        kind, size, offset = struct.unpack_from('<III', data, directory + i * 12)
        if offset + size > len(data):
            raise ValueError('stream bounds')
        streams[kind] = offset
    if 6 not in streams:
        return {'exceptionPresent': False}
    exception = streams[6]
    code = struct.unpack_from('<I', data, exception + 8)[0]
    address = struct.unpack_from('<Q', data, exception + 24)[0]
    result = {'exceptionPresent': True, 'exceptionCode': hex(code), 'faultModule': None}
    if 4 in streams:
        modules = streams[4]
        module_count = struct.unpack_from('<I', data, modules)[0]
        if module_count > 65536:
            raise ValueError('module count')
        for i in range(module_count):
            entry = modules + 4 + i * 108
            base, size = struct.unpack_from('<QI', data, entry)
            if not base <= address < base + size:
                continue
            name_offset = struct.unpack_from('<I', data, entry + 20)[0]
            length = struct.unpack_from('<I', data, name_offset)[0]
            if length > 8192 or name_offset + 4 + length > len(data):
                raise ValueError('module name bounds')
            name = data[name_offset + 4:name_offset + 4 + length].decode('utf-16-le', 'replace')
            result['faultModule'] = name.replace('\\', '/').rsplit('/', 1)[-1]
            result['moduleOffset'] = hex(address - base)
            break
    return result


def main():
    reports = []
    skipped = 0
    for path in Path('/tmp').glob('android-*/emu-crash-*.db/**/*.dmp'):
        try:
            if path.stat().st_size > 32 * 1024 * 1024:
                skipped += 1
                continue
            reports.append(summarize(path.read_bytes()))
        except (OSError, ValueError, struct.error):
            skipped += 1
    print(json.dumps({'reports': reports, 'unreadableOrLargeReports': skipped}, indent=2))


if __name__ == '__main__':
    main()
