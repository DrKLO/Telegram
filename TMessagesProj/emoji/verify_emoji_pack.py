#!/usr/bin/env python3
"""Independent reader/checker for EPK3 versions 2 and 3. Includes complete emoji composition."""
import argparse, collections, hashlib, io, json, mmap, struct, time
from pathlib import Path
import numpy as np
from PIL import Image
NONE = 65535


def wrap_webp(b):
    return b'RIFF' + struct.pack('<I', 12 + len(b) + (len(b) & 1)) + b'WEBPVP8L' + struct.pack('<I', len(b)) + b + (b'\0' if len(b) & 1 else b'')


def transform(a, t):
    if t & 4: a = a.swapaxes(0, 1)
    if t & 2: a = a[::-1]
    if t & 1: a = a[:, ::-1]
    return a


class Pack:
    def __init__(self, path):
        import zlib
        self.zlib = zlib
        self.file = open(path, 'rb')
        self.data = mmap.mmap(self.file.fileno(), 0, access=mmap.ACCESS_READ)
        magic, version, size, width, height, self.n, self.ne, length, flags = struct.unpack_from('<4sHHIIIIII', self.data)
        assert version in (2, 3)
        self.version = version
        assert (magic, size, width, height, length, flags) == (b'EPK3', 20, 64, 64, len(self.data), 0)
        self.entries = [struct.unpack_from('<HHIHHHHBBBB', self.data, 32 + i * 20) for i in range(self.n)]
        self.cache = collections.OrderedDict()
        self.native_calls = 0
        self.palette_calls = 0
        self.input_bytes = 0
        for i, (key, mask, offset, length, a, b, colors, codec, pred, ch, flags) in enumerate(self.entries):
            assert offset >= 32 + 20 * self.n and length and offset + length <= len(self.data)
            assert codec in range(5) and pred in range(7) and flags in range(256)
            assert all(p == NONE or 0 <= p < self.n for p in (a, b))
            assert pred == 0 or a != NONE
            assert (pred in (3, 5, 6)) == (b != NONE)
            if pred:
                for p in (a, b):
                    if p != NONE:
                        e = self.entries[p]
                        assert p != i and e[7] <= 1 and e[8] == 0 and e[4] == NONE and e[5] == NONE
            if codec in (2, 3): assert colors == ch == pred == (flags & 127) == 0
            else: assert 1 <= colors <= 256 and (ch & 127) in (0, 1, 3, 4)

    def palette_length(self, e):
        if e[9] & 128:
            return 2 + struct.unpack_from('<H', self.data, e[2])[0]
        n = e[6] * (e[9] & 127)
        return (n * 7 + 7) // 8 if e[10] & 1 else n

    def blob(self, i):
        e = self.entries[i]
        self.input_bytes += e[3]
        return self.data[e[2]:e[2] + e[3]]

    def root(self, i):
        if i not in self.cache:
            a = self.indices(i)
            self.cache[i] = a
            if len(self.cache) > 8: self.cache.popitem(last=False)
        self.cache.move_to_end(i)
        return self.cache[i]

    def indices(self, i):
        e = self.entries[i]
        parents = [p for p in e[4:6] if p != NONE]
        maps = [transform(self.root(p), (e[10] >> (1 + j * 3)) & 7)
                for j, p in enumerate(parents)]
        if e[7] == 4:
            assert e[8] == 4 and len(parents) == 1 and self.entries[parents[0]][6] == e[6]
            assert self.palette_length(e) == e[3]
            return maps[0].copy()
        sizes = [self.entries[p][6] for p in parents]
        ln = sum(sizes)
        b = self.blob(i)[self.palette_length(e):]
        cut = 32
        if e[8] in (5, 6):
            cut = b[0]; b = b[1:]
            assert 1 <= cut < 64
        self.native_calls += 1
        if e[7] == 0:
            dz = self.zlib.decompressobj(-15)
            raw = dz.decompress(b)
            assert dz.eof and not dz.unused_data and len(raw) == ln + 4096
            lut = np.frombuffer(raw[:ln], np.uint8)
            arr = np.frombuffer(raw[ln:], np.uint8).reshape(64, 64).copy()
        else:
            lut = np.frombuffer(b[:ln], np.uint8)
            arr = np.array(Image.open(io.BytesIO(wrap_webp(b[ln:]))).convert('L'))
        if parents:
            if e[8] in (3, 5, 6):
                axis = 0 if e[8] == 6 else 1
                region = np.indices((64, 64))[axis] < cut
                predicted = np.where(region, lut[:sizes[0]][maps[0]], lut[sizes[0]:][maps[1]])
            else: predicted = lut[maps[0]]
            arr = arr ^ predicted if e[8] == 1 else arr + predicted
        assert arr.shape == (64, 64) and int(arr.max()) < e[6]
        return arr

    def decode(self, i):
        e = self.entries[i]
        if e[7] in (2, 3):
            self.native_calls += 1
            b = self.blob(i)
            if e[7] == 2: b = wrap_webp(b)
            return np.array(Image.open(io.BytesIO(b)).convert('RGBA'))
        idx = self.indices(i)
        n, ch = e[6], e[9] & 127
        if ch == 0:
            palette = np.arange(256, dtype=np.uint8)[:, None].repeat(4, axis=1)
            palette[:, 3] = 255
        else:
            b = self.data[e[2]:e[2] + self.palette_length(e)]
            if e[9] & 128:
                dz = self.zlib.decompressobj(-15); b = dz.decompress(b[2:])
                expected = (n * ch * 7 + 7) // 8 if e[10] & 1 else n * ch
                assert dz.eof and not dz.unused_data and len(b) == expected
                self.palette_calls += 1
            if e[10] & 1:
                values = []
                for j in range(n * ch):
                    bit = j * 7; pos = bit >> 3; shift = bit & 7
                    v = b[pos] >> shift
                    if shift > 1: v |= b[pos + 1] << (8 - shift)
                    v &= 127
                    values.append((v << 1) | (v >> 6))
                a = np.array(values, np.uint8).reshape(n, ch)
            else: a = np.frombuffer(b, np.uint8).reshape(n, ch)
            palette = np.full((n, 4), 255, np.uint8)
            if ch == 1: palette[:, :3] = a
            else: palette[:, :ch] = a
        return palette[idx]

    def emoji(self, i):
        assert 0 <= i < self.ne
        out = self.decode(i)
        mask = self.entries[i][1]
        if mask != NONE:
            j = next(j for j in range(self.ne, self.n) if self.entries[j][0] == mask)
            alpha = self.decode(j)[:, :, 0]
            out[:, :, 3] = ((out[:, :, 3].astype(np.uint16) * alpha + 127) // 255).astype(np.uint8)
        return out

    def close(self):
        self.data.close()
        self.file.close()


def reference(path):
    b = Path(path).read_bytes()
    ne = struct.unpack_from('<I', b)[0] // 12
    emojis = [struct.unpack_from('<HHII', b, 4 + 12 * i) for i in range(ne)]
    p = 4 + 12 * ne
    nm = struct.unpack_from('<I', b, p)[0] // 10
    masks = [struct.unpack_from('<HII', b, p + 4 + 10 * i) for i in range(nm)]
    return b, sorted(emojis), sorted(masks)


def validate(pack_path, reference_path, textures_path, compare_pack_path=None):
    import zipfile
    import optimizer_visual as quality
    from scipy.ndimage import uniform_filter
    pack = Pack(pack_path)
    previous = Pack(compare_pack_path) if compare_pack_path else None
    compared_changes = []
    raw, emojis, masks = reference(reference_path)
    assert pack.ne == len(emojis) and pack.n == len(emojis) + len(masks)
    mask_images = {key: np.array(Image.open(io.BytesIO(raw[off:off+length])).convert('L'))
                   for key, off, length in masks}
    changed = []; before = []; after = []; max_streams = max_input = 0; total_streams = 0; total_palettes = max_palettes = 0
    with zipfile.ZipFile(textures_path) as archive:
        for i, (key, mask, off, length) in enumerate(emojis):
            assert pack.entries[i][:2] == (key, mask)
            baseline = np.array(Image.open(io.BytesIO(raw[off:off+length])).convert('RGBA'))
            if mask != NONE:
                baseline[:, :, 3] = ((baseline[:, :, 3].astype(np.uint16) * mask_images[mask] + 127) // 255).astype(np.uint8)
            pack.cache.clear(); pack.native_calls = pack.input_bytes = pack.palette_calls = 0
            actual = pack.emoji(i)
            max_streams = max(max_streams, pack.native_calls); max_input = max(max_input, pack.input_bytes)
            total_streams += pack.native_calls
            total_palettes += pack.palette_calls; max_palettes = max(max_palettes, pack.palette_calls)
            assert np.array_equal(actual[:, :, 3], baseline[:, :, 3]), f'Alpha changed: {key}'
            a = actual.copy(); b = baseline.copy()
            a[a[:, :, 3] == 0, :3] = 0; b[b[:, :, 3] == 0, :3] = 0
            if not np.array_equal(a, b):
                assert pack.entries[i][10] & 128, f'Unmarked visual change: {key}'
                original = np.array(Image.open(archive.open(f'{key//4096}_{key%4096}.png')).convert('RGBA'))
                m0 = quality.metrics(baseline, original); m1 = quality.metrics(actual, original)
                assert m1[0] <= m0[0]+1e-10 and m1[1] >= m0[1]-1e-7 and m1[2] <= m0[2]+1e-10, f'Global quality gate: {key}'
                e0 = uniform_filter(quality.error(baseline, original), size=5)
                s0 = quality.structure(baseline, original)
                assert quality.passes_local(actual, original, e0, s0), f'Local quality gate: {key}'
                changed.append(key); before.append(m0); after.append(m1)
            if previous is not None:
                assert previous.ne == pack.ne and previous.entries[i][0] == key
                prev = previous.emoji(i)
                assert np.array_equal(actual[:, :, 3], prev[:, :, 3])
                visible = actual[:, :, 3] > 0
                if not np.array_equal(actual[visible, :3], prev[visible, :3]):
                    original = np.array(Image.open(archive.open(f'{key//4096}_{key%4096}.png')).convert('RGBA'))
                    m0 = quality.metrics(prev, original); m1 = quality.metrics(actual, original)
                    assert m1[0] <= m0[0]+1e-10 and m1[1] >= m0[1]-1e-7 and m1[2] <= m0[2]+1e-10, f'Previous-pack global gate: {key}'
                    assert quality.passes_local(actual, original, uniform_filter(quality.error(prev, original), size=5), quality.structure(prev, original)), f'Previous-pack local gate: {key}'
                    entry = pack.entries[i]
                    if entry[8] in (3, 5, 6) and entry[10] & 128:
                        from quality_seams import passes_seam
                        cut = 32 if entry[8] == 3 else pack.data[entry[2] + pack.palette_length(entry)]
                        axis = 1 if entry[8] == 6 else 0
                        assert passes_seam(actual, prev, original, axis, cut), f'Seam quality gate: {key}'
                    compared_changes.append(key)
    stats = dict(pack_bytes=len(pack.data), verified_emojis=pack.ne,
                 changed_emojis=len(changed), alpha_changed_pixels=0,
                 unmarked_visible_changes=0, quality_gate_failures=0,
                 max_cold_streams=max_streams, max_cold_compressed_bytes=max_input,
                 average_cold_streams=total_streams/pack.ne,
                 max_cold_palette_inflations=max_palettes, average_cold_palette_inflations=total_palettes/pack.ne,
                 sha256=hashlib.sha256(pack.data).hexdigest(), changed_ids=changed)
    if changed:
        stats['changed_metrics_mean'] = dict(reference=np.mean(before,axis=0).tolist(), result=np.mean(after,axis=0).tolist())
    if previous is not None:
        stats['compared_pack_sha256'] = hashlib.sha256(previous.data).hexdigest()
        stats['changed_vs_compared_pack'] = len(compared_changes)
        stats['compared_pack_quality_failures'] = 0
        previous.close()
    pack.close()
    return stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pack')
    parser.add_argument('--reference', required=True)
    parser.add_argument('--textures', required=True)
    parser.add_argument('--compare-pack', help='Also require no quality regression against this prior EPK3 pack')
    parser.add_argument('--json', help='Write the full machine-readable report')
    args = parser.parse_args()
    result = validate(args.pack, args.reference, args.textures, args.compare_pack)
    if args.json: Path(args.json).write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k != 'changed_ids'},indent=2))


if __name__ == '__main__': main()
