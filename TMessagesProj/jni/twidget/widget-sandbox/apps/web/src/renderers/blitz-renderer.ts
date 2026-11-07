// Blitz renderer prototype for the web: the Renderer contract of WidgetHost on
// the Rust crate in renderer-blitz/ compiled to wasm32 (the same C ABI the
// Swift / C++ glue calls, see include/twidget_blitz.h; wasm-bindgen's glue is
// only there because web-time needs it). The document is laid out by
// blitz-dom and painted on the CPU by vello_cpu into a <canvas>; no iframe, no
// script, no network: the wasm only ever sees validated RenderOps and `data:`
// images. Dev tooling for docs/blitz-experiment.md, not a product path. The
// web twin of BlitzRenderer.swift / BlitzRenderer.kt / desktop's
// blitz_renderer.cpp: touches in CSS px go in, rasters come out on a
// requestAnimationFrame clock that also delivers the widget's frames.
//
// Build the module once with `pnpm build:blitz-web` (rustup cargo +
// wasm-bindgen-cli → renderer-blitz/pkg-web/, gitignored, served at /blitz/ by
// the Vite plugin); without it the toggle reports a `renderer` diag and shows
// nothing.

import type { HostToRenderer, RendererToHost } from '@telegram/widget-sandbox/shared/protocol';
import type { Renderer, RendererFactory } from '@telegram/widget-sandbox/host/renderer';

/** The crate's C ABI as wasm exports (pointers are byte offsets into `memory`). */
interface BlitzExports {
  memory: WebAssembly.Memory;
  twb_create(width: number, dpr: number, theme: number): number;
  twb_destroy(handle: number): void;
  twb_receive(handle: number, json: number): number;
  twb_set_width(handle: number, width: number): number;
  twb_set_theme(handle: number, theme: number): number;
  twb_touch(handle: number, action: number, x: number, y: number, pointer: number, primary: number): number;
  twb_size(handle: number, widthOut: number, heightOut: number): void;
  twb_dirty(handle: number): number;
  twb_render(handle: number, buffer: number, capacity: number): number;
  twb_font_report(): number;
  twb_string_free(s: number): void;
  twb_alloc(len: number): number;
  twb_free(p: number, len: number): void;
}

const GLUE_URL = '/blitz/twidget_blitz.js';
const WASM_URL = '/blitz/twidget_blitz_bg.wasm';
const encoder = new TextEncoder();
const decoder = new TextDecoder();

let modulePromise: Promise<BlitzExports> | null = null;
/** Fetched and compiled once per page; every renderer shares the instance (one wasm heap, one font collection). */
function loadModule(): Promise<BlitzExports> {
  modulePromise ??= (async () => {
    const url = GLUE_URL; // a variable: Vite's import analysis leaves the URL alone (it is served outside the module graph)
    const glue = await import(/* @vite-ignore */ url);
    // wasm-bindgen --target web: `init` resolves to the instance's exports.
    return (await glue.default({ module_or_path: WASM_URL })) as BlitzExports;
  })();
  return modulePromise;
}

export class BlitzRenderer implements Renderer {
  /** Plug into WidgetHost: `new WidgetHost(container, html, { ..., rendererFactory: BlitzRenderer.factory })`. */
  static readonly factory: RendererFactory = (container, theme) => new BlitzRenderer(container, theme);
  /** The renderer created last (the dev UI and the runners talk to it). */
  static current: BlitzRenderer | null = null;

  readonly element: HTMLCanvasElement;
  onMessage: ((msg: RendererToHost) => void) | null = null;
  onDiag: ((kind: string, detail: string) => void) | null = null;
  /** Paint statistics (`blitz-paint` in the native demos). */
  readonly stats = { paints: 0, paintMs: 0, initMs: 0, width: 0, height: 0 };

  private wasm: BlitzExports | null = null;
  private handle = 0;
  private queue: HostToRenderer[] = [];
  private destroyed = false;
  private frozen = false;
  private frameRequested = false;
  private paintScheduled = false;
  private raf = 0;
  private pixels = 0;
  private pixelsCap = 0;
  private scratch = 0;
  private readonly dpr = window.devicePixelRatio || 1;
  private width: number;
  private readonly ctx: CanvasRenderingContext2D;
  private readonly resizeObserver: ResizeObserver;
  private pointerIds = new Map<number, number>();
  private nextPointer = 0;

  constructor(container: HTMLElement, private theme: 'light' | 'dark') {
    const canvas = document.createElement('canvas');
    canvas.className = 'widget-frame blitz-frame';
    canvas.width = 0;
    canvas.height = 0;
    // Pointers go to the document (it hit-tests them); the page keeps scrolling with the wheel.
    canvas.style.touchAction = 'pan-y';
    this.element = canvas;
    this.ctx = canvas.getContext('2d')!;
    container.appendChild(canvas);
    this.width = canvas.clientWidth || 400;
    for (const [type, action] of [['pointerdown', 0], ['pointermove', 1], ['pointerup', 2], ['pointercancel', 3]] as const) {
      canvas.addEventListener(type, (e: PointerEvent) => {
        if (action === 0) { canvas.setPointerCapture(e.pointerId); } else if (!this.pointerIds.has(e.pointerId) && action !== 1) return;
        this.touch(action, e.offsetX, e.offsetY, this.pointer(e, action), e.isPrimary);
      });
    }
    this.resizeObserver = new ResizeObserver(() => {
      const w = canvas.clientWidth;
      if (w > 0 && w !== this.width) { this.width = w; this.setWidth(w); }
    });
    this.resizeObserver.observe(canvas);
    BlitzRenderer.current = this;
  }

  load() {
    const t0 = performance.now();
    loadModule().then((wasm) => {
      if (this.destroyed) return;
      this.wasm = wasm;
      this.scratch = wasm.twb_alloc(8);
      this.handle = this.withCString(this.theme, (p) => wasm.twb_create(this.width, this.dpr, p));
      this.stats.initMs = performance.now() - t0;
      if (!this.handle) {
        this.onDiag?.('renderer', 'Blitz renderer failed to initialise');
        return;
      }
      this.onDiag?.('blitz-init', `${this.stats.initMs.toFixed(1)} ms (fetch + compile + document)`);
      this.onDiag?.('blitz-fonts', this.readCString(wasm.twb_font_report()));
      this.onMessage?.({ type: 'ready' });
      const queued = this.queue;
      this.queue = [];
      for (const msg of queued) this.deliver(msg);
      this.schedulePaint();
    }, (err) => {
      this.onDiag?.('renderer', `Blitz wasm not available (${String(err).split('\n')[0]}) — run pnpm build:blitz-web`);
    });
  }

  send(msg: HostToRenderer) {
    if (this.destroyed) return;
    if (!this.handle) { this.queue.push(msg); return; }
    this.deliver(msg);
  }

  setTheme(theme: 'light' | 'dark') {
    this.theme = theme;
    if (!this.handle) return;
    this.dispatch(this.withCString(theme, (p) => this.wasm!.twb_set_theme(this.handle, p)));
  }

  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    this.resizeObserver.disconnect();
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
    if (this.wasm && this.handle) {
      this.guard(() => {
        this.wasm!.twb_destroy(this.handle);
        if (this.pixels) this.wasm!.twb_free(this.pixels, this.pixelsCap);
        if (this.scratch) this.wasm!.twb_free(this.scratch, 8);
      });
    }
    this.handle = 0;
    this.pixels = 0;
    this.element.remove();
    if (BlitzRenderer.current === this) BlitzRenderer.current = null;
  }

  /** A pointer in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. */
  touch(action: number, x: number, y: number, pointer = 0, primary = true) {
    if (!this.handle) return;
    this.dispatch(this.guard(() => this.wasm!.twb_touch(this.handle, action, x, y, pointer, primary ? 1 : 0)));
  }

  /** Paints the document now; the canvas shows it. */
  paint() {
    if (!this.handle || !this.wasm) return;
    const wasm = this.wasm;
    const t0 = performance.now();
    this.guard(() => {
      wasm.twb_size(this.handle, this.scratch, this.scratch + 4);
      const [w, h] = new Uint32Array(wasm.memory.buffer, this.scratch, 2);
      if (!w || !h) return;
      const need = w * h * 4;
      if (need > this.pixelsCap) {
        if (this.pixels) wasm.twb_free(this.pixels, this.pixelsCap);
        this.pixelsCap = need;
        this.pixels = wasm.twb_alloc(need);
      }
      if (wasm.twb_render(this.handle, this.pixels, this.pixelsCap) < 0) {
        this.onDiag?.('renderer', 'Blitz paint failed');
        return;
      }
      // Premultiplied RGBA from vello → straight alpha for putImageData.
      const src = new Uint8Array(wasm.memory.buffer, this.pixels, need);
      const image = new ImageData(w, h);
      const dst = image.data;
      for (let i = 0; i < need; i += 4) {
        const a = src[i + 3];
        if (a === 255) { dst[i] = src[i]; dst[i + 1] = src[i + 1]; dst[i + 2] = src[i + 2]; dst[i + 3] = 255; }
        else if (a) { dst[i] = (src[i] * 255) / a; dst[i + 1] = (src[i + 1] * 255) / a; dst[i + 2] = (src[i + 2] * 255) / a; dst[i + 3] = a; }
      }
      if (this.element.width !== w || this.element.height !== h) { this.element.width = w; this.element.height = h; }
      this.ctx.putImageData(image, 0, 0);
      this.stats.width = w;
      this.stats.height = h;
    });
    this.stats.paints++;
    this.stats.paintMs = performance.now() - t0;
  }

  // ───────────── host → wasm ─────────────

  private deliver(msg: HostToRenderer) {
    if (msg.type === 'requestFrame') { this.requestFrame(); return; }
    if (msg.type === 'frozen') this.frozen = true;
    this.dispatch(this.withCString(JSON.stringify(msg), (p) => this.wasm!.twb_receive(this.handle, p)));
  }

  private setWidth(width: number) {
    if (!this.handle) return;
    this.dispatch(this.guard(() => this.wasm!.twb_set_width(this.handle, width)));
  }

  /** A JSON array of renderer → host messages from the wasm; each one goes to the host separately. */
  private dispatch(ptr: number | undefined) {
    if (!ptr || !this.wasm) return;
    const text = this.readCString(ptr);
    if (this.wasm.twb_dirty(this.handle)) this.schedulePaint();
    let messages: RendererToHost[];
    try { messages = JSON.parse(text); } catch { return; }
    if (!Array.isArray(messages)) return;
    for (const msg of messages) {
      if (msg.type === 'violation') this.onDiag?.('renderer-violation', msg.detail);
      this.onMessage?.(msg);
    }
  }

  /** `fn` with a NUL-terminated UTF-8 copy of `s` in the wasm heap. */
  private withCString<T>(s: string, fn: (ptr: number) => T): T {
    const wasm = this.wasm!;
    const bytes = encoder.encode(s);
    const ptr = wasm.twb_alloc(bytes.length + 1);
    new Uint8Array(wasm.memory.buffer, ptr, bytes.length + 1).set(bytes);
    new Uint8Array(wasm.memory.buffer)[ptr + bytes.length] = 0;
    try {
      return this.guard(() => fn(ptr)) as T;
    } finally {
      wasm.twb_free(ptr, bytes.length + 1);
    }
  }

  /** Reads and frees a string returned by the wasm. */
  private readCString(ptr: number): string {
    const wasm = this.wasm!;
    if (!ptr) return '';
    const heap = new Uint8Array(wasm.memory.buffer);
    let end = ptr;
    while (heap[end] !== 0) end++;
    const text = decoder.decode(heap.subarray(ptr, end));
    wasm.twb_string_free(ptr);
    return text;
  }

  /** A trap (a Rust panic with panic=abort) ends the renderer: the canvas keeps its last raster, the host gets a diag. */
  private guard<T>(fn: () => T): T | undefined {
    try {
      return fn();
    } catch (e) {
      this.onDiag?.('renderer', `Blitz wasm trapped: ${String(e).split('\n')[0]}`);
      this.handle = 0;
      return undefined;
    }
  }

  private pointer(e: PointerEvent, action: number): number {
    let id = this.pointerIds.get(e.pointerId);
    if (id === undefined) { id = this.nextPointer++; this.pointerIds.set(e.pointerId, id); }
    if (action >= 2) this.pointerIds.delete(e.pointerId);
    if (this.pointerIds.size === 0) this.nextPointer = 0;
    return id;
  }

  // ───────────── frames and paints, coalesced into display refreshes ─────────────

  private requestFrame() {
    if (this.frozen || this.frameRequested) return;
    this.frameRequested = true;
    this.schedule();
  }

  private schedulePaint() {
    if (this.paintScheduled || this.destroyed) return;
    this.paintScheduled = true;
    this.schedule();
  }

  private schedule() {
    if (this.raf) return;
    this.raf = requestAnimationFrame(() => {
      this.raf = 0;
      if (this.destroyed) return;
      const frame = this.frameRequested, paint = this.paintScheduled;
      this.frameRequested = false;
      this.paintScheduled = false;
      // Like rAF before the browser's paint: the callback first, the raster after.
      if (frame) this.onMessage?.({ type: 'frame', time: performance.now() });
      if (paint) this.paint();
    });
  }
}
