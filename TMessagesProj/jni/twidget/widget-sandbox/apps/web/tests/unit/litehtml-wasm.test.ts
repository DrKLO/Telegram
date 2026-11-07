// The litehtml renderer's wasm build (pnpm build:litehtml-web → renderer-litehtml/pkg-web/)
// driven from Node through the same C ABI apps/web/src/renderers/litehtml-renderer.ts uses: validated
// ops in, real layout answers and a display list out, nothing fetched. Fonts come from a
// stub backend (every glyph 8 px wide), since Node has no canvas. The prototype is frozen
// (docs/decisions.md, "The renderer prototypes are frozen"): skipped unless asked for with
// TW_NATIVE_RENDERERS=1 and the module is built (it is gitignored dev tooling).
// Run with: TW_NATIVE_RENDERERS=1 pnpm test:unit

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { Validator } from '@telegram/widget-sandbox/host/validator';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const glueFile = path.join(root, 'apps', 'renderer-litehtml', 'pkg-web', 'twidget_litehtml.js');
const wasmFile = path.join(root, 'apps', 'renderer-litehtml', 'pkg-web', 'twidget_litehtml.wasm');
const built = fs.existsSync(glueFile) && fs.existsSync(wasmFile);

interface Module {
  HEAPU32: Uint32Array;
  UTF8ToString(p: number): string;
  stringToUTF8(s: string, p: number, max: number): void;
  lengthBytesUTF8(s: string): number;
  twlBackends?: Map<number, unknown>;
  _twl_create(w: number, dpr: number, theme: number): number;
  _twl_backend_id(h: number): number;
  _twl_destroy(h: number): void;
  _twl_receive(h: number, json: number): number;
  _twl_touch(h: number, action: number, x: number, y: number, pointer: number, primary: number): number;
  _twl_size(h: number, wOut: number, hOut: number): void;
  _twl_display_list(h: number): number;
  _twl_html(h: number): number;
  _twl_string_free(p: number): void;
  _twl_alloc(n: number): number;
  _twl_free(p: number): void;
}

/** A monospace stand-in for the canvas backend: 8 px advance per UTF-16 unit, metrics from the size. */
class StubBackend {
  fonts = new Map<number, number>();
  next = 0;
  createFont(_families: string, size: number) {
    const id = ++this.next;
    this.fonts.set(id, size);
    return { id, ascent: size * 0.8, descent: size * 0.2, height: size, xHeight: size * 0.5, zeroWidth: 8 };
  }
  deleteFont(id: number) { this.fonts.delete(id); }
  textWidth(_id: number, text: string) { return text.length * 8; }
  loadImage() { return null; }
}

/** One renderer on a shared module, with the string helpers of litehtml-renderer.ts. */
class Doc {
  static mod: Promise<Module> | null = null;
  private w!: Module;
  private h = 0;
  static async create(width = 400, dpr = 1, theme = 'light') {
    Doc.mod ??= (async () => {
      const m = (await (await import(glueFile)).default({ locateFile: () => wasmFile })) as Module;
      m.twlBackends = new Map();
      return m;
    })();
    const d = new Doc();
    d.w = await Doc.mod;
    d.h = d.cstr(theme, (p) => d.w._twl_create(width, dpr, p));
    assert.ok(d.h, 'twl_create');
    d.w.twlBackends!.set(d.w._twl_backend_id(d.h), new StubBackend());
    return d;
  }
  cstr<T>(s: string, fn: (p: number) => T): T {
    const len = this.w.lengthBytesUTF8(s) + 1;
    const p = this.w._twl_alloc(len);
    this.w.stringToUTF8(s, p, len);
    try { return fn(p); } finally { this.w._twl_free(p); }
  }
  read(p: number): string {
    const s = this.w.UTF8ToString(p);
    this.w._twl_string_free(p);
    return s;
  }
  send(msg: unknown): any[] { return JSON.parse(this.read(this.cstr(JSON.stringify(msg), (p) => this.w._twl_receive(this.h, p)))); }
  touch(action: number, x: number, y: number): any[] { return JSON.parse(this.read(this.w._twl_touch(this.h, action, x, y, 0, 1))); }
  html(): string { return this.read(this.w._twl_html(this.h)); }
  size(): [number, number] {
    const s = this.w._twl_alloc(8);
    this.w._twl_size(this.h, s, s + 4);
    const r: [number, number] = [this.w.HEAPU32[s >> 2], this.w.HEAPU32[(s >> 2) + 1]];
    this.w._twl_free(s);
    return r;
  }
  displayList(): any[] { return JSON.parse(this.read(this.w._twl_display_list(this.h))); }
  destroy() { this.w.twlBackends!.delete(this.w._twl_backend_id(this.h)); this.w._twl_destroy(this.h); }
}

/** Raw sandbox ops through the real Validator, as WidgetHost would send them (the UA body margin of 8px stays). */
function validated(ops: unknown[], diags: string[] = []) {
  const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
  return v.processOps(ops as any);
}

const CARD = [
  { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
  { op: 'el', id: 3, tag: 'head' }, { op: 'ins', parent: 2, id: 3, before: null },
  { op: 'el', id: 4, tag: 'body' }, { op: 'ins', parent: 2, id: 4, before: null },
  { op: 'el', id: 5, tag: 'style' }, { op: 'text', id: 6, data: 'body{margin:0}#d{height:50px;background:#f00}' }, { op: 'ins', parent: 5, id: 6, before: null }, { op: 'ins', parent: 3, id: 5, before: null },
  { op: 'el', id: 7, tag: 'div', attrs: { id: 'd' } }, { op: 'text', id: 8, data: 'x' }, { op: 'ins', parent: 7, id: 8, before: null }, { op: 'ins', parent: 4, id: 7, before: null },
  { op: 'el', id: 9, tag: 'button', attrs: { id: 'b', style: 'display:block;width:200px;height:60px;margin:0' } }, { op: 'text', id: 10, data: 'go' }, { op: 'ins', parent: 9, id: 10, before: null }, { op: 'ins', parent: 4, id: 9, before: null },
];

const skip = process.env.TW_NATIVE_RENDERERS !== '1' ? 'frozen prototype (TW_NATIVE_RENDERERS=1 runs it)' : built ? false : 'renderer-litehtml/pkg-web not built (pnpm build:litehtml-web)';
describe('litehtml wasm renderer', { skip }, () => {
  it('lays out validated ops, reports the size and answers rect/box queries from real geometry', async () => {
    const d = await Doc.create();
    try {
      const out = d.send({ type: 'ops', ops: validated(CARD) });
      const size = out.find((m) => m.type === 'size');
      assert.ok(size, 'size report');
      assert.equal(size.width, 400);
      assert.ok(size.height >= 110 && size.height < 200, `height ${size.height}`);
      assert.match(d.html(), /data-twid="7"/);
      const [w, h] = d.size();
      assert.equal(w, 400);
      assert.ok(h >= 110);
      const rect = d.send({ type: 'query', qid: 1, query: { q: 'rect', id: 7 } })[0].result;
      assert.equal(rect.width, 400);
      assert.equal(rect.height, 50);
      const box = d.send({ type: 'query', qid: 2, query: { q: 'box', id: 9 } })[0].result;
      assert.equal(box.offsetWidth, 200);
      assert.equal(box.offsetHeight, 60);
      assert.equal(box.offsetTop, 50);
      const list = d.displayList();
      assert.ok(list.some((c) => c.k === 0 && (c.c >>> 0) === 0xffff0000), 'the red box is in the display list');
      assert.ok(list.some((c) => c.k === 4 && c.t === 'go'), 'the button text is in the display list');
    } finally {
      d.destroy();
    }
  });

  it('hit-tests a tap into a click on the element under it, and nothing outside', async () => {
    const d = await Doc.create();
    try {
      d.send({ type: 'ops', ops: validated(CARD) });
      d.send({ type: 'listen', types: ['pointerdown'] });
      const down = d.touch(0, 20, 70);
      assert.ok(down.some((m) => m.type === 'gesture' && m.wants === false), 'gesture decision');
      assert.ok(down.some((m) => m.type === 'event' && m.ev.type === 'pointerdown' && m.ev.targetId === 9), 'pointerdown on the button');
      const up = d.touch(2, 21, 71);
      assert.ok(up.some((m) => m.type === 'event' && m.ev.type === 'click' && m.ev.targetId === 9), 'click on the button');
      d.touch(0, 390, 300);
      const miss = d.touch(2, 390, 300);
      assert.ok(!miss.some((m) => m.type === 'event' && m.ev.type === 'click' && m.ev.targetId === 9), 'no click outside');
    } finally {
      d.destroy();
    }
  });

  it('refuses non-data images and on* attributes; frozen keeps the document and drops touches', async () => {
    const d = await Doc.create();
    const diags: string[] = [];
    try {
      const ops = validated([...CARD, { op: 'el', id: 11, tag: 'img', attrs: { src: 'https://example.com/x.png', onload: 'x()' } }, { op: 'ins', parent: 4, id: 11, before: null }], diags);
      d.send({ type: 'ops', ops });
      assert.ok(!d.html().includes('example.com'), 'the validator dropped the URL before the renderer');
      assert.ok(!d.html().includes('onload'));
      const frozen = d.send({ type: 'frozen', reason: 'CPU limit', fatal: true });
      assert.ok(Array.isArray(frozen));
      assert.match(d.html(), /data-tw-frozen="fatal"/);
      assert.deepEqual(d.touch(0, 20, 70), []);
    } finally {
      d.destroy();
    }
  });
});
