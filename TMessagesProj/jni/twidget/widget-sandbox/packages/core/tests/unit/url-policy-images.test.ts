// data: images: their size read from their bytes as a decoder would (url-policy.ts `dataImageSize`), past
// MAX_IMAGE_PIXELS refused, bytes of none of the profile's formats broken; the body read as fetch decodes it.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { BROKEN_IMAGE, MAX_IMAGE_PIXELS, checkUrl, dataImageSize } from '../../src/host/url-policy.ts';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const htmlFiles = (dir: string): string[] => fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
  const file = path.join(dir, entry.name);
  return entry.isDirectory() ? htmlFiles(file) : entry.name.endsWith('.html') ? [file] : [];
});

const base64 = (bytes: number[]) => Buffer.from(bytes).toString('base64');
const u32be = (n: number) => [n >>> 24, (n >>> 16) & 255, (n >>> 8) & 255, n & 255];
/** The signature and IHDR of a PNG claiming `width` × `height`: all a decoder reads before it allocates. */
const pngHeader = (width: number, height: number) => 'data:image/png;base64,' + base64([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52,
  ...u32be(width), ...u32be(height), 8, 6, 0, 0, 0,
]);
const u16le = (n: number) => [n & 255, n >> 8];
/** A GIF of a `screen` logical screen whose first frame is `frame` ([left, top, width, height]), after an extension. */
const gif = (screen: [number, number], frame?: [number, number, number, number]) => 'data:image/gif;base64,' + base64([
  ...Buffer.from('GIF89a', 'latin1'), ...u16le(screen[0]), ...u16le(screen[1]), 0, 0, 0,
  0x21, 0xf9, 4, 0, 0, 0, 0, 0,
  ...(frame ? [0x2c, ...frame.flatMap(u16le), 0, 2, 2, 0x4c, 0x01, 0] : []),
  0x3b,
]);
/** A baseline JPEG's frame header of 16 × 16, percent-encoded. */
const SOF_16 = '%FF%C0%00%11%08%00%10%00%10%03';
/** A JPEG whose frame header (16 × 16) comes after APP1 segments of these lengths. */
const jpegWithSegments = (lengths: number[]) => [
  0xff, 0xd8,
  ...lengths.flatMap((length) => [0xff, 0xe1, (length + 2) >> 8, (length + 2) & 255, ...new Array(length).fill(0x41)]),
  0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03,
];
/** A JPEG whose frame header (16 × 16) comes after `fill` fill bytes, a step each. */
const jpegWithFill = (fill: number) => [0xff, 0xd8, ...new Array(fill).fill(0xff), 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03];
/** A 1 × 1 GIF whose frame comes after a comment of `count` sub-blocks of `size` bytes. */
const gifWithBlocks = (count: number, size: number) => 'data:image/gif;base64,' + base64([
  ...Buffer.from('GIF89a', 'latin1'), 1, 0, 1, 0, 0, 0, 0,
  0x21, 0xfe, ...Array.from({ length: count }, () => [size, ...new Array(size).fill(0x41)]).flat(), 0,
  0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4c, 0x01, 0, 0x3b,
]);
/** A WebP whose first chunk is `chunk` with these bytes from offset 16 on. */
const webpChunk = (chunk: string, bytes: number[]) => [...Buffer.from('RIFF\0\0\0\0WEBP' + chunk, 'latin1'), ...bytes];

/** How many characters `run` has atob decode. */
const decodedBy = (run: () => unknown): number => {
  const atob = globalThis.atob;
  let characters = 0;
  globalThis.atob = (text: string) => { characters += text.length; return atob(text); };
  try { run(); } finally { globalThis.atob = atob; }
  return characters;
};
/**
 * How many times as long a scan of `url` takes as parsing the message that brings it (an `attr` op), which the host pays
 * for every op anyway: the least of rounds that take turns, so that a busy machine slows both alike.
 */
const scanCost = (url: string): number => {
  const message = JSON.stringify([{ op: 'attr', id: 1, name: 'src', value: url }]);
  let scan = Infinity, parse = Infinity, sink = 0;
  for (let round = 0; round < 9; round++) {
    let start = performance.now();
    for (let i = 0; i < 20; i++) sink += dataImageSize(url)?.[0] ?? 0;
    scan = Math.min(scan, performance.now() - start);
    start = performance.now();
    for (let i = 0; i < 20; i++) sink += JSON.parse(message).length;
    parse = Math.min(parse, performance.now() - start);
  }
  assert.ok(sink >= 0);
  return scan / parse;
};

describe('url policy: data: images', () => {
  it('takes every image of the corpora and the conformance set, whatever its format, at its size', () => {
    const urls = new Set<string>();
    for (const dir of ['corpus', 'packages/conformance']) {
      for (const file of htmlFiles(path.join(repo, dir))) {
        // the attacks' images are past the cap on purpose (mem-huge-bitmaps)
        if (file.startsWith(path.join(repo, 'corpus', 'attacks') + path.sep)) continue;
        for (const [url] of fs.readFileSync(file, 'utf8').matchAll(/data:image\/(?:png|jpe?g|gif|webp);base64,[A-Za-z0-9+/=]+/g)) urls.add(url);
      }
    }
    // the conformance set's broken image (auto/02-attributes-images-b), which has to fire error
    urls.delete('data:image/png;base64,AAAA');
    // and its image one pixel past the cap (auto/19-limits), which has to be the placeholder
    const limits = fs.readFileSync(path.join(repo, 'packages/conformance/auto/19-limits.html'), 'utf8');
    urls.delete(/id="img-big"[^>]*src="([^"]+)"/.exec(limits)![1]);
    assert.ok(urls.size > 3, `${urls.size} images`);
    for (const url of urls) {
      const size = dataImageSize(url);
      assert.ok(size && size[0] * size[1] <= MAX_IMAGE_PIXELS, `${url.slice(0, 60)}: ${size}`);
      assert.equal(checkUrl(url, 'image'), url);
    }
  });

  it('refuses an image whose header claims more pixels than a bitmap may hold', () => {
    assert.deepEqual(dataImageSize(pngHeader(4096, 4096)), [4096, 4096]);
    assert.notEqual(checkUrl(pngHeader(1024, 1024), 'image'), null);
    assert.notEqual(checkUrl(pngHeader(4096, 256), 'image'), null);
    assert.equal(checkUrl(pngHeader(1025, 1024), 'image'), null);
    assert.equal(checkUrl(pngHeader(4096, 4096), 'image'), null);
    assert.equal(checkUrl(pngHeader(65535, 65535), 'image'), null);
    // GIF's logical screen, which every frame is drawn in
    assert.equal(checkUrl(gif([65535, 65535], [0, 0, 1, 1]), 'image'), null);
  });

  it("bounds a GIF by its first frame too, where that frame reaches past the logical screen, as decoders grow it", () => {
    assert.deepEqual(dataImageSize(gif([100, 100], [10, 10, 5, 5])), [100, 100]);
    assert.deepEqual(dataImageSize(gif([100, 100], [90, 0, 20, 10])), [110, 100]);
    assert.deepEqual(dataImageSize(gif([1, 1], [0, 0, 8192, 8192])), [8192, 8192]);
    assert.equal(checkUrl(gif([1, 1], [0, 0, 8192, 8192]), 'image'), null);
    // no frame at all: nothing a decoder draws
    assert.equal(checkUrl(gif([1, 1]), 'image'), BROKEN_IMAGE);
    // thousands of tiny extensions before the frame (a script's): no image has them, and reading them is the host's time
    const crowded = 'data:image/gif;base64,' + base64([
      ...Buffer.from('GIF89a', 'latin1'), 1, 0, 1, 0, 0, 0, 0, ...Array.from({ length: 20000 }, () => [0x21, 0xfe, 0]).flat(),
      0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4c, 0x01, 0, 0x3b,
    ]);
    assert.equal(checkUrl(crowded, 'image'), BROKEN_IMAGE);
  });

  it('takes a whole lossless WebP shorter than the other chunks need (28 bytes, 1 × 1)', () => {
    const webp = 'data:image/webp;base64,UklGRhQAAABXRUJQVlA4TAgAAAAvAAAAAIiICA==';
    assert.deepEqual(dataImageSize(webp), [1, 1]);
    assert.equal(checkUrl(webp, 'image'), webp);
  });

  it("leaves the URL's fragment out of the body, as fetch does", () => {
    const png = pngHeader(2, 3);
    const body = png.slice(png.indexOf(',') + 1);
    assert.deepEqual(dataImageSize(png.replace(body, body.replace(/(.{4})/g, '$1 ')) + '#part'), [2, 3]);
    const small = gif([1, 1], [0, 0, 1, 1]) + '#x';
    assert.deepEqual(dataImageSize(small), [1, 1]);
    assert.equal(checkUrl(small, 'image'), small);
  });

  it('reads the body as fetch does: percent-encoded, base64 with spaces', () => {
    const png = pngHeader(2, 3);
    const body = png.slice(png.indexOf(',') + 1);
    assert.deepEqual(dataImageSize(png.replace(body, body.replace(/(.{4})/g, '$1 '))), [2, 3]);
    assert.deepEqual(dataImageSize('data:image/png;base64,' + encodeURIComponent(body)), [2, 3]);
    const raw = Buffer.from(body, 'base64');
    assert.deepEqual(dataImageSize('data:image/png,' + [...raw].map((b) => '%' + b.toString(16).padStart(2, '0')).join('')), [2, 3]);
  });

  it('breaks bytes of another format, or none, behind an image type: the browser picks the decoder by the bytes', () => {
    // BMP, outside the profile
    assert.equal(checkUrl('data:image/png;base64,' + base64([0x42, 0x4d, ...new Array(52).fill(0)]), 'image'), BROKEN_IMAGE);
    assert.equal(checkUrl('data:image/png;base64,AAAA', 'image'), BROKEN_IMAGE);
    assert.equal(checkUrl('data:image/png;base64,!!', 'image'), BROKEN_IMAGE);
    assert.equal(checkUrl(BROKEN_IMAGE, 'image'), BROKEN_IMAGE);
  });

  it("finds a JPEG's size past the segments before it, in every encoding of the body, and no further than 64 KB", () => {
    const jpeg = jpegWithSegments([30000, 32000]);
    const body = base64(jpeg);
    for (const url of [
      'data:image/jpeg;base64,' + body,
      'data:image/jpeg;base64,' + body.replace(/(.{76})/g, '$1 '),
      'data:image/jpeg;base64,' + body.replace(/\//g, '%2F').replace(/\+/g, '%2b'),
      'data:image/jpeg,' + jpeg.map((b) => '%' + b.toString(16).padStart(2, '0')).join(''),
    ]) {
      assert.deepEqual(dataImageSize(url), [16, 16], url.slice(0, 40));
    }
    assert.equal(checkUrl('data:image/jpeg;base64,' + base64(jpegWithSegments([65533])), 'image'), BROKEN_IMAGE);
  });

  it('keeps the bytes where the browser has them: a %u is no escape, a pair of surrogates is one character', () => {
    // the browser's bytes: the body as UTF-8, percent-decoded (%u is left alone)
    const app = (payload: string) => {
      const length = Buffer.byteLength(payload) + 2;
      return '%FF%D8%FF%E1' + [length >> 8, length & 255].map((b) => '%' + b.toString(16).padStart(2, '0')).join('') + payload + SOF_16;
    };
    assert.deepEqual(dataImageSize('data:image/jpeg,' + app('x%u0041y')), [16, 16]);
    // the high surrogate on the last character the decoder reads at first
    const prefix = app('').indexOf('%FF%C0');
    assert.deepEqual(dataImageSize('data:image/jpeg,' + app('a'.repeat(4095 - prefix) + '😀' + 'b'.repeat(100))), [16, 16]);
  });
});

// A check runs for every element and style that names an image, on the host's thread, and a script can name thousands
// of URLs that differ in their fragment alone: each scan reads the bytes in place, takes a bounded number of steps and
// decodes no more than it reads, so that no bytes cost more than a few parses of the message that brings them.
describe('url policy: data: image scans at their worst', () => {
  it("walks a JPEG to its frame header in at most 1024 steps, each a read in place", () => {
    assert.deepEqual(dataImageSize('data:image/jpeg;base64,' + base64(jpegWithFill(1024))), [16, 16]);
    assert.equal(dataImageSize('data:image/jpeg;base64,' + base64(jpegWithFill(1025))), null);
    const worst = {
      'fill bytes': [0xff, 0xd8, ...new Array(65000).fill(0xff)],
      'restart markers': [0xff, 0xd8, ...Array.from({ length: 32000 }, () => [0xff, 0xd0]).flat()],
      'empty segments': [0xff, 0xd8, ...Array.from({ length: 16000 }, () => [0xff, 0xe0, 0, 2]).flat()],
    };
    for (const [name, bytes] of Object.entries(worst)) {
      const url = 'data:image/jpeg;base64,' + base64(bytes);
      assert.equal(checkUrl(url, 'image'), BROKEN_IMAGE, name);
      const decoded = decodedBy(() => dataImageSize(url));
      assert.ok(decoded <= 2 * 4096, `${name}: ${decoded} characters decoded`);
      const cost = scanCost(url);
      assert.ok(cost < 4, `${name}: ${cost.toFixed(1)} parses of its message`);
    }
    // percent-encoded, which is decoded from its start: as far as the steps go
    const percent = 'data:image/jpeg,' + worst['fill bytes'].map((b) => '%' + b.toString(16)).join('');
    assert.equal(checkUrl(percent, 'image'), BROKEN_IMAGE);
    const cost = scanCost(percent);
    assert.ok(cost < 4, `percent-encoded fill bytes: ${cost.toFixed(1)} parses of its message`);
    // a frame header past 62 KB of segments: the windows the steps land in
    const far = 'data:image/jpeg;base64,' + base64(jpegWithSegments([30000, 32000]));
    assert.deepEqual(dataImageSize(far), [16, 16]);
    const decoded = decodedBy(() => dataImageSize(far));
    assert.ok(decoded <= 3 * 4096, `${decoded} characters decoded`);
  });

  it('walks a GIF to its first frame in at most 4096 sub-blocks, each a read in place', () => {
    assert.deepEqual(dataImageSize(gifWithBlocks(4095, 1)), [1, 1]);
    assert.equal(dataImageSize(gifWithBlocks(4096, 1)), null);
    // the farthest the steps reach: 4095 sub-blocks to 61 KB in, decoded once
    const far = gifWithBlocks(4095, 14);
    assert.deepEqual(dataImageSize(far), [1, 1]);
    let decoded = decodedBy(() => dataImageSize(far));
    assert.ok(decoded <= far.length - far.indexOf(',') + 4096, `${decoded} characters decoded`);
    let cost = scanCost(far);
    assert.ok(cost < 4, `${cost.toFixed(1)} parses of its message`);
    // one-byte sub-blocks to 64 KB
    const tiny = gifWithBlocks(32000, 1);
    assert.equal(checkUrl(tiny, 'image'), BROKEN_IMAGE);
    decoded = decodedBy(() => dataImageSize(tiny));
    assert.ok(decoded <= 3 * 4096, `${decoded} characters decoded`);
    cost = scanCost(tiny);
    assert.ok(cost < 4, `${cost.toFixed(1)} parses of its message`);
  });

  it("reads a PNG's or a WebP's size from the body's first window, whatever follows", () => {
    const tail = new Array(65000).fill(0);
    const images: [string, number[], [number, number]][] = [
      ['png', [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, ...u32be(2), ...u32be(3), 8, 6, 0, 0, 0], [2, 3]],
      ['webp', webpChunk('VP8X', [10, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 2, 0, 0]), [2, 3]],
      ['webp', webpChunk('VP8L', [13, 0, 0, 0, 0x2f, 0x01, 0x80, 0, 0]), [2, 3]],
      ['webp', webpChunk('VP8 ', [10, 0, 0, 0, 0, 0, 0, 0x9d, 0x01, 0x2a, 2, 0, 3, 0]), [2, 3]],
    ];
    for (const [type, header, size] of images) {
      const url = `data:image/${type};base64,` + base64([...header, ...tail]);
      assert.deepEqual(dataImageSize(url), size, url.slice(0, 50));
      const decoded = decodedBy(() => dataImageSize(url));
      assert.ok(decoded <= 4096, `${url.slice(0, 50)}: ${decoded} characters decoded`);
      const cost = scanCost(url);
      assert.ok(cost < 4, `${url.slice(0, 50)}: ${cost.toFixed(1)} parses of its message`);
    }
  });

  it('tries a body that is no base64 once, however many signatures are looked for after', () => {
    const valid = base64(new Array(48000).fill(0x41));
    const broken = {
      'a character no base64 has': 'data:image/png;base64,!' + valid.slice(1),
      'a padding in the middle': 'data:image/png;base64,QQ==' + valid,
      'with white space': 'data:image/png;base64,! ' + valid.replace(/(.{76})/g, '$1 '),
    };
    for (const [name, url] of Object.entries(broken)) {
      assert.equal(checkUrl(url, 'image'), BROKEN_IMAGE, name);
      const decoded = decodedBy(() => dataImageSize(url));
      assert.ok(decoded <= 4096, `${name}: ${decoded} characters decoded`);
      const cost = scanCost(url);
      assert.ok(cost < 4, `${name}: ${cost.toFixed(1)} parses of its message`);
    }
    // a short one, whose message costs next to nothing to parse: one try
    assert.equal(decodedBy(() => dataImageSize('data:image/png;base64,!!')), 2);
  });
});
