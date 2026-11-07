// The Blitz renderer's wasm build (pnpm build:blitz-web → renderer-blitz/pkg-web/) driven
// from Node through the same C ABI apps/web/src/renderers/blitz-renderer.ts uses: validated
// ops in, real layout answers and a raster out, nothing fetched. The prototype is frozen
// (docs/decisions.md, "The renderer prototypes are frozen"): skipped unless asked for with
// TW_NATIVE_RENDERERS=1 and the module is built (it is gitignored dev tooling).
// Run with: TW_NATIVE_RENDERERS=1 pnpm test:unit

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { PLACEHOLDER_IMAGE } from '@telegram/widget-sandbox/host/url-policy';
import { Validator } from '@telegram/widget-sandbox/host/validator';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const glueFile = path.join(root, 'apps', 'renderer-blitz', 'pkg-web', 'twidget_blitz.js');
const wasmFile = path.join(root, 'apps', 'renderer-blitz', 'pkg-web', 'twidget_blitz_bg.wasm');
const built = fs.existsSync(glueFile) && fs.existsSync(wasmFile);

/** A `w`×`h` PNG of one colour, as a data: URL (PNG is in profile 1; SVG images are not). */
function pngDataUrl(w: number, h: number, [r, g, b]: number[]): string {
  const chunk = (type: string, data: Buffer) => {
    const body = Buffer.concat([Buffer.from(type, 'latin1'), data]);
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(zlib.crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w, 0);
  header.writeUInt32BE(h, 4);
  header.set([8, 2, 0, 0, 0], 8);
  const row = Buffer.concat([Buffer.from([0]), Buffer.from(Array.from({ length: w }, () => [r, g, b]).flat())]);
  const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(Buffer.concat(Array(h).fill(row)))), chunk('IEND', Buffer.alloc(0))]);
  return 'data:image/png;base64,' + png.toString('base64');
}

interface Exports {
  memory: WebAssembly.Memory;
  twb_create(w: number, dpr: number, theme: number): number;
  twb_destroy(h: number): void;
  twb_receive(h: number, json: number): number;
  twb_touch(h: number, action: number, x: number, y: number, pointer: number, primary: number): number;
  twb_size(h: number, wOut: number, hOut: number): void;
  twb_render(h: number, buf: number, cap: number): number;
  twb_font_report(): number;
  twb_string_free(p: number): void;
  twb_alloc(n: number): number;
  twb_free(p: number, n: number): void;
}

/** One renderer on a shared module, with the string helpers of blitz-renderer.ts. */
class Doc {
  static exports: Promise<Exports> | null = null;
  private w!: Exports;
  private h = 0;
  static async create(width = 400, dpr = 1, theme = 'light') {
    Doc.exports ??= (async () => (await import(glueFile)).default({ module_or_path: fs.readFileSync(wasmFile) }))();
    const d = new Doc();
    d.w = await Doc.exports;
    d.h = d.cstr(theme, (p) => d.w.twb_create(width, dpr, p));
    assert.ok(d.h, 'twb_create');
    return d;
  }
  cstr<T>(s: string, fn: (p: number) => T): T {
    const b = new TextEncoder().encode(s);
    const p = this.w.twb_alloc(b.length + 1);
    new Uint8Array(this.w.memory.buffer, p, b.length + 1).set(b);
    new Uint8Array(this.w.memory.buffer)[p + b.length] = 0;
    try { return fn(p); } finally { this.w.twb_free(p, b.length + 1); }
  }
  read(p: number): string {
    const heap = new Uint8Array(this.w.memory.buffer);
    let e = p;
    while (heap[e]) e++;
    const s = new TextDecoder().decode(heap.subarray(p, e));
    this.w.twb_string_free(p);
    return s;
  }
  send(msg: unknown): any[] { return JSON.parse(this.read(this.cstr(JSON.stringify(msg), (p) => this.w.twb_receive(this.h, p)))); }
  touch(action: number, x: number, y: number): any[] { return JSON.parse(this.read(this.w.twb_touch(this.h, action, x, y, 0, 1))); }
  fonts(): string { return this.read(this.w.twb_font_report()); }
  render(): { w: number; h: number; px: Uint8Array } {
    const s = this.w.twb_alloc(8);
    this.w.twb_size(this.h, s, s + 4);
    const [w, h] = new Uint32Array(this.w.memory.buffer, s, 2);
    this.w.twb_free(s, 8);
    const buf = this.w.twb_alloc(w * h * 4);
    assert.ok(this.w.twb_render(this.h, buf, w * h * 4) >= 0, 'twb_render');
    const px = new Uint8Array(this.w.memory.buffer, buf, w * h * 4).slice();
    this.w.twb_free(buf, w * h * 4);
    return { w, h, px };
  }
  pixel(r: { w: number; px: Uint8Array }, x: number, y: number) { const i = (y * r.w + x) * 4; return [...r.px.subarray(i, i + 4)]; }
  destroy() { this.w.twb_destroy(this.h); }
}

/** Raw sandbox ops through the real Validator, as WidgetHost would send them (the UA body margin of 8px stays). */
function validated(ops: unknown[], diags: string[] = []) {
  const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
  const out = v.processOps([
    { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
    { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null },
    ...ops,
  ]);
  assert.deepEqual(diags.filter((d) => d.startsWith('protocol-violation')), [], 'the skeleton and the ops are valid protocol');
  return out;
}

const skip = process.env.TW_NATIVE_RENDERERS !== '1' ? 'frozen prototype (TW_NATIVE_RENDERERS=1 runs it)' : built ? false : 'apps/renderer-blitz/pkg-web not built — pnpm build:blitz-web';
describe('Blitz renderer (wasm)', { skip }, () => {
  it('bundles a font: the browser exposes none to wasm', async () => {
    const d = await Doc.create();
    assert.match(d.fonts(), /^1 families/);
    d.destroy();
  });

  it('lays out validated ops, reports the size and answers queries from real geometry', async () => {
    const d = await Doc.create(400, 1);
    const out = d.send({ type: 'ops', ops: validated([
      { op: 'el', id: 4, tag: 'div', attrs: { style: 'height:50px;background:#f00' } }, { op: 'ins', parent: 3, id: 4, before: null },
      { op: 'text', id: 5, data: 'hello' }, { op: 'ins', parent: 3, id: 5, before: null },
    ]) });
    const size = out.find((m) => m.type === 'size');
    assert.ok(size, 'size report');
    assert.ok(size.height >= 66 && size.height < 140, `document height ${size.height}`);
    const [res] = d.send({ type: 'query', qid: 7, query: { q: 'rect', id: 4 } });
    assert.equal(res.type, 'result');
    assert.equal(res.qid, 7);
    assert.deepEqual([res.result.width, res.result.height, res.result.top, res.result.left], [384, 50, 8, 8]);
    const r = d.render();
    assert.equal(r.w, 400);
    assert.ok(r.h >= 66);
    assert.deepEqual(d.pixel(r, 20, 20), [255, 0, 0, 255], 'a red pixel inside the div');
    assert.equal(d.pixel(r, 2, 2)[3], 0, 'the body margin is transparent');
    d.destroy();
  });

  it('paints at the device pixel ratio', async () => {
    const d = await Doc.create(300, 2);
    d.send({ type: 'ops', ops: validated([{ op: 'el', id: 4, tag: 'div', attrs: { style: 'height:10px' } }, { op: 'ins', parent: 3, id: 4, before: null }]) });
    const r = d.render();
    assert.equal(r.w, 600);
    assert.ok(r.h >= 52);
    d.destroy();
  });

  it('hit-tests a tap and reports it as a click the sandbox listens for', async () => {
    const d = await Doc.create();
    d.send({ type: 'ops', ops: validated([{ op: 'el', id: 4, tag: 'button', attrs: { style: 'display:block;width:200px;height:60px;margin:0' } }, { op: 'ins', parent: 3, id: 4, before: null }]) });
    d.send({ type: 'listen', types: ['click'] });
    d.touch(0, 20, 20);
    const clicks = d.touch(2, 20, 20).filter((m) => m.type === 'event' && m.ev.type === 'click');
    assert.equal(clicks.length, 1);
    assert.equal(clicks[0].ev.targetId, 4);
    d.touch(0, 300, 200);
    assert.equal(d.touch(2, 300, 200).filter((m) => m.type === 'event' && m.ev.type === 'click' && m.ev.targetId === 4).length, 0, 'outside the button');
    d.destroy();
  });

  it('never tries to fetch: external URLs are gone before the ops arrive, data: images are decoded in place', async () => {
    const d = await Doc.create();
    const diags: string[] = [];
    const ops = validated([
      { op: 'el', id: 4, tag: 'img', attrs: { src: 'http://127.0.0.1:9999/hit/blitz-wasm', style: 'background:url(http://127.0.0.1:9999/hit/css)' } }, { op: 'ins', parent: 3, id: 4, before: null },
      { op: 'el', id: 5, tag: 'img', attrs: { src: pngDataUrl(20, 20, [0, 0, 255]) } }, { op: 'ins', parent: 3, id: 5, before: null },
      { op: 'el', id: 6, tag: 'img', attrs: { src: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><rect width="20" height="20" fill="blue"/></svg>' } }, { op: 'ins', parent: 3, id: 6, before: null },
    ], diags);
    assert.ok(!JSON.stringify(ops).includes('127.0.0.1'), 'the validator stripped the URLs');
    const svg = (ops as any[]).find((o) => o.op === 'el' && o.id === 6);
    assert.equal(new Map(svg.attrs).get('src'), PLACEHOLDER_IMAGE, 'a data: SVG image is out of profile 1: the validator puts the placeholder in');
    assert.ok(diags.some((m) => m.startsWith('url-blocked: img[src]=data:image/svg+xml')), diags.join('; '));
    const out = d.send({ type: 'ops', ops });
    assert.deepEqual(out.filter((m) => m.type === 'violation'), [], 'no fetch refused by the renderer');
    const [res] = d.send({ type: 'query', qid: 1, query: { q: 'rect', id: 5 } });
    assert.deepEqual([res.result.width, res.result.height], [20, 20], 'the data: PNG has its intrinsic size');
    const r = d.render();
    assert.deepEqual(d.pixel(r, Math.round(res.result.left + 10), Math.round(res.result.top + 10)).slice(0, 3), [0, 0, 255], 'the data: PNG painted');
    const [placeholder] = d.send({ type: 'query', qid: 2, query: { q: 'rect', id: 6 } });
    assert.deepEqual([placeholder.result.width, placeholder.result.height], [48, 48], 'the placeholder at its own size');
    d.destroy();
  });

  it('frozen: keeps the last render, ignores touches, shows the badge', async () => {
    const d = await Doc.create();
    d.send({ type: 'ops', ops: validated([{ op: 'el', id: 4, tag: 'div', attrs: { style: 'height:40px;background:#00f' } }, { op: 'ins', parent: 3, id: 4, before: null }]) });
    d.send({ type: 'listen', types: ['click'] });
    d.send({ type: 'frozen', reason: 'Widget stopped', fatal: true });
    assert.deepEqual(d.touch(0, 20, 20), []);
    const r = d.render();
    const [red, , blue, alpha] = d.pixel(r, 20, 20);
    assert.ok(blue > 150 && red < 120 && alpha === 255, 'the blue div is still there (30 % grayscale)');
    d.destroy();
  });
});
