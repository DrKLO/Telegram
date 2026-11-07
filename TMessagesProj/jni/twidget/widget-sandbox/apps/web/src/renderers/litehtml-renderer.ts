// litehtml renderer prototype for the web: the Renderer contract of WidgetHost
// on the C++ core in renderer-litehtml/ compiled to wasm by Emscripten (the
// same `twl_*` C ABI the Swift / Kotlin / desktop glue calls, with the fonts,
// text widths and images answered from here through `Module.twlBackends`). The
// validated DOM is kept as a shadow tree in the wasm and re-parsed by litehtml
// on every batch; the paint comes back as a display list (JSON, CSS px) that
// this file draws on a <canvas>. No iframe, no script, no network: the wasm
// only ever sees validated RenderOps and `data:` images. Dev tooling for the
// three-way comparison (docs/litehtml-renderer.md), not a product path; the
// web twin of LitehtmlRenderer.swift / LitehtmlRenderer.kt and the structure
// of blitz-renderer.ts.
//
// Build the module once with `pnpm build:litehtml-web` (emscripten →
// renderer-litehtml/pkg-web/, gitignored, served at /litehtml/ by the Vite
// plugin); without it the toggle reports a `renderer` diag and shows nothing.

import type { HostToRenderer, RendererToHost } from '@telegram/widget-sandbox/shared/protocol';
import type { Renderer, RendererFactory } from '@telegram/widget-sandbox/host/renderer';

/** The Emscripten module: the C ABI as `_twl_*` exports plus the runtime helpers the build exports. */
interface LitehtmlModule {
  HEAPU8: Uint8Array;
  HEAPU32: Uint32Array;
  UTF8ToString(ptr: number): string;
  stringToUTF8(s: string, ptr: number, max: number): void;
  lengthBytesUTF8(s: string): number;
  twlBackends?: Map<number, CanvasBackend>;
  _twl_create(width: number, dpr: number, theme: number): number;
  _twl_backend_id(handle: number): number;
  _twl_destroy(handle: number): void;
  _twl_receive(handle: number, json: number): number;
  _twl_set_width(handle: number, width: number): number;
  _twl_set_theme(handle: number, theme: number): number;
  _twl_touch(handle: number, action: number, x: number, y: number, pointer: number, primary: number): number;
  _twl_image_ready(handle: number, image: number, width: number, height: number): number;
  _twl_size(handle: number, widthOut: number, heightOut: number): void;
  _twl_dirty(handle: number): number;
  _twl_display_list(handle: number): number;
  _twl_stats(handle: number): number;
  _twl_string_free(s: number): void;
  _twl_alloc(len: number): number;
  _twl_free(p: number): void;
}

const GLUE_URL = '/litehtml/twidget_litehtml.js';

let modulePromise: Promise<LitehtmlModule> | null = null;
/** Fetched and compiled once per page; every renderer shares the instance (one wasm heap). */
function loadModule(): Promise<LitehtmlModule> {
  modulePromise ??= (async () => {
    const url = GLUE_URL; // a variable: Vite's import analysis leaves the URL alone (served outside the module graph)
    const glue = await import(/* @vite-ignore */ url);
    const mod = (await glue.default({ locateFile: (f: string) => '/litehtml/' + f })) as LitehtmlModule;
    mod.twlBackends = new Map();
    return mod;
  })();
  return modulePromise;
}

/** One display-list command as `to_json` in display_list.h writes it. */
interface Cmd {
  k: number;
  r?: number[];
  q?: number[];
  l?: number[];
  c?: number;
  w?: number;
  f?: number;
  t?: string;
  x?: number;
  y?: number;
  i?: number;
  a?: number[];
  s?: number[];
}

/** Fonts, text widths and images for the wasm container, on a 2D context; and the painter of its display list. */
export class CanvasBackend {
  private readonly ctx: CanvasRenderingContext2D;
  private fonts = new Map<number, string>();
  private nextFont = 0;
  readonly images = new Map<number, HTMLImageElement>();

  constructor(private onImage: (id: number, width: number, height: number) => void) {
    this.ctx = document.createElement('canvas').getContext('2d')!;
  }

  private fontCss(families: string, size: number, weight: number, italic: boolean): string {
    const css = `${italic ? 'italic ' : ''}${weight} ${size}px ${families}`;
    this.ctx.font = `${size}px sans-serif`;
    this.ctx.font = css;  // ignored by the canvas when the family list is not valid CSS → the fallback above stays
    return this.ctx.font;
  }

  /** Called from the wasm: metrics of a font, or null. */
  createFont(families: string, size: number, weight: number, italic: boolean) {
    const css = this.fontCss(families, size, weight, italic);
    const id = ++this.nextFont;
    this.fonts.set(id, css);
    const x = this.ctx.measureText('x');
    const ascent = x.fontBoundingBoxAscent ?? size * 0.9;
    const descent = x.fontBoundingBoxDescent ?? size * 0.25;
    return { id, ascent, descent, height: ascent + descent, xHeight: x.actualBoundingBoxAscent || size * 0.5, zeroWidth: this.ctx.measureText('0').width };
  }

  deleteFont(id: number) {
    this.fonts.delete(id);
  }

  textWidth(id: number, text: string): number {
    const css = this.fonts.get(id);
    if (!css) return 0;
    this.ctx.font = css;
    return this.ctx.measureText(text).width;
  }

  /** `data:` images decode asynchronously: the size is reported through `onImage` → twl_image_ready when it is known. */
  loadImage(id: number, url: string) {
    if (!url.startsWith('data:') || url.includes('svg')) return null;  // litehtml has no SVG; neither does this path
    const img = new Image();
    img.decoding = 'sync';
    img.onload = () => this.onImage(id, img.naturalWidth, img.naturalHeight);
    img.src = url;
    this.images.set(id, img);
    return img.complete && img.naturalWidth ? { width: img.naturalWidth, height: img.naturalHeight } : { width: 0, height: 0 };
  }

  dispose() {
    this.images.clear();
    this.fonts.clear();
  }

  // ───────────── the painter ─────────────

  private static color(argb: number): string {
    const a = (argb >>> 24) & 255, r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
    return `rgba(${r},${g},${b},${a / 255})`;
  }

  private static rounded(ctx: CanvasRenderingContext2D, r: number[], q?: number[]) {
    ctx.beginPath();
    if (q) ctx.roundRect(r[0], r[1], r[2], r[3], [{ x: q[0], y: q[1] }, { x: q[2], y: q[3] }, { x: q[4], y: q[5] }, { x: q[6], y: q[7] }]);
    else ctx.rect(r[0], r[1], r[2], r[3]);
  }

  /** Executes a display list on `ctx`, which is already scaled to CSS px. */
  paint(ctx: CanvasRenderingContext2D, list: Cmd[]) {
    let clipDepth = 0;
    const clipRect = (c: Cmd) => { if (c.l) { ctx.beginPath(); ctx.rect(c.l[0], c.l[1], c.l[2], c.l[3]); ctx.clip(); } };
    for (const c of list) {
      switch (c.k) {
        case 0: // FillRect
          ctx.save();
          clipRect(c);
          CanvasBackend.rounded(ctx, c.r!, c.q);
          ctx.fillStyle = CanvasBackend.color(c.c!);
          ctx.fill();
          ctx.restore();
          break;
        case 1: // StrokeRect
          CanvasBackend.rounded(ctx, c.r!, c.q);
          ctx.strokeStyle = CanvasBackend.color(c.c!);
          ctx.lineWidth = c.w ?? 1;
          ctx.stroke();
          break;
        case 2: // FillEllipse
        case 3: { // StrokeEllipse
          const [x, y, w, h] = c.r!;
          const lw = c.w ?? 1;
          const inset = c.k === 3 ? lw / 2 : 0;
          ctx.beginPath();
          ctx.ellipse(x + w / 2, y + h / 2, Math.max(0, w / 2 - inset), Math.max(0, h / 2 - inset), 0, 0, Math.PI * 2);
          if (c.k === 2) { ctx.fillStyle = CanvasBackend.color(c.c!); ctx.fill(); } else { ctx.strokeStyle = CanvasBackend.color(c.c!); ctx.lineWidth = lw; ctx.stroke(); }
          break;
        }
        case 4: { // Text
          const css = this.fonts.get(c.f!);
          if (!css) break;
          ctx.font = css;
          ctx.fillStyle = CanvasBackend.color(c.c!);
          ctx.textBaseline = 'alphabetic';
          ctx.fillText(c.t!, c.x!, c.y!);
          break;
        }
        case 5: { // Image
          const img = this.images.get(c.i!);
          if (!img || !img.complete || !img.naturalWidth) break;
          ctx.save();
          clipRect(c);
          if (c.q) { CanvasBackend.rounded(ctx, c.l ?? c.r!, c.q); ctx.clip(); }
          ctx.drawImage(img, c.r![0], c.r![1], c.r![2], c.r![3]);
          ctx.restore();
          break;
        }
        case 6: case 7: case 8: { // Linear / Radial / Conic
          const a = c.a!, s = c.s ?? [];
          if (s.length < 2) break;
          ctx.save();
          clipRect(c);
          CanvasBackend.rounded(ctx, c.r!, c.q);
          ctx.clip();
          let g: CanvasGradient;
          if (c.k === 6) g = ctx.createLinearGradient(a[0], a[1], a[2], a[3]);
          else if (c.k === 7) {
            const rx = Math.max(a[2], 0.01), ry = Math.max(a[3], 0.01);
            g = ctx.createRadialGradient(a[0], a[1], 0, a[0], a[1], rx);
            ctx.translate(a[0], a[1]); ctx.scale(1, ry / rx); ctx.translate(-a[0], -a[1]);
          } else g = ctx.createConicGradient(((a[2] - 90) * Math.PI) / 180, a[0], a[1]);
          for (let i = 0; i + 1 < s.length; i += 2) g.addColorStop(Math.min(1, Math.max(0, s[i])), CanvasBackend.color(s[i + 1]));
          ctx.fillStyle = g;
          const r = c.r!;
          ctx.fillRect(r[0] - 4000, r[1] - 4000, r[2] + 8000, r[3] + 8000);
          ctx.restore();
          break;
        }
        case 9: // ClipPush
          ctx.save();
          clipDepth++;
          CanvasBackend.rounded(ctx, c.r!, c.q);
          ctx.clip();
          break;
        case 10: // ClipPop
          if (clipDepth > 0) { ctx.restore(); clipDepth--; }
          break;
      }
    }
    while (clipDepth-- > 0) ctx.restore();
  }
}

export class LitehtmlRenderer implements Renderer {
  /** Plug into WidgetHost: `new WidgetHost(container, html, { ..., rendererFactory: LitehtmlRenderer.factory })`. */
  static readonly factory: RendererFactory = (container, theme) => new LitehtmlRenderer(container, theme);
  /** The renderer created last (the dev UI and the runners talk to it). */
  static current: LitehtmlRenderer | null = null;

  readonly element: HTMLCanvasElement;
  onMessage: ((msg: RendererToHost) => void) | null = null;
  onDiag: ((kind: string, detail: string) => void) | null = null;
  /** Paint statistics (`litehtml-paint` in the native demos) and the last build's numbers. */
  readonly stats = { paints: 0, paintMs: 0, initMs: 0, width: 0, height: 0, displayOps: 0, builds: 0, parseLayoutMs: 0 };

  private wasm: LitehtmlModule | null = null;
  private handle = 0;
  private backend: CanvasBackend | null = null;
  private queue: HostToRenderer[] = [];
  private destroyed = false;
  private frozen = false;
  private frameRequested = false;
  private paintScheduled = false;
  private raf = 0;
  private scratch = 0;
  private lastReport = 0;
  private readonly dpr = window.devicePixelRatio || 1;
  private width: number;
  private readonly ctx: CanvasRenderingContext2D;
  private readonly resizeObserver: ResizeObserver;
  private pointerIds = new Map<number, number>();
  private nextPointer = 0;

  constructor(container: HTMLElement, private theme: 'light' | 'dark') {
    const canvas = document.createElement('canvas');
    canvas.className = 'widget-frame litehtml-frame';
    canvas.width = 0;
    canvas.height = 0;
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
    LitehtmlRenderer.current = this;
  }

  load() {
    const t0 = performance.now();
    loadModule().then((wasm) => {
      if (this.destroyed) return;
      this.wasm = wasm;
      this.scratch = wasm._twl_alloc(8);
      this.handle = this.withCString(this.theme, (p) => wasm._twl_create(this.width, this.dpr, p));
      this.stats.initMs = performance.now() - t0;
      if (!this.handle) {
        this.onDiag?.('renderer', 'litehtml renderer failed to initialise');
        return;
      }
      this.backend = new CanvasBackend((id, w, h) => {
        if (!this.handle) return;
        this.dispatch(this.guard(() => wasm._twl_image_ready(this.handle, id, w, h)));
      });
      wasm.twlBackends!.set(wasm._twl_backend_id(this.handle), this.backend);
      this.onDiag?.('litehtml-init', `${this.stats.initMs.toFixed(1)} ms (fetch + compile + document)`);
      this.onMessage?.({ type: 'ready' });
      const queued = this.queue;
      this.queue = [];
      for (const msg of queued) this.deliver(msg);
      this.schedulePaint();
    }, (err) => {
      this.onDiag?.('renderer', `litehtml wasm not available (${String(err).split('\n')[0]}) — run pnpm build:litehtml-web`);
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
    this.dispatch(this.withCString(theme, (p) => this.wasm!._twl_set_theme(this.handle, p)));
  }

  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    this.resizeObserver.disconnect();
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
    if (this.wasm && this.handle) {
      const wasm = this.wasm, handle = this.handle;
      this.guard(() => {
        wasm.twlBackends?.delete(wasm._twl_backend_id(handle));
        wasm._twl_destroy(handle);
        if (this.scratch) wasm._twl_free(this.scratch);
      });
    }
    this.backend?.dispose();
    this.handle = 0;
    this.element.remove();
    if (LitehtmlRenderer.current === this) LitehtmlRenderer.current = null;
  }

  /** A pointer in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. */
  touch(action: number, x: number, y: number, pointer = 0, primary = true) {
    if (!this.handle) return;
    this.dispatch(this.guard(() => this.wasm!._twl_touch(this.handle, action, x, y, pointer, primary ? 1 : 0)));
  }

  /** Paints the document now: the display list from the wasm, drawn on the canvas at the device pixel ratio. */
  paint() {
    if (!this.handle || !this.wasm || !this.backend) return;
    const wasm = this.wasm;
    const t0 = performance.now();
    this.guard(() => {
      wasm._twl_size(this.handle, this.scratch, this.scratch + 4);
      const w = wasm.HEAPU32[this.scratch >> 2], h = wasm.HEAPU32[(this.scratch >> 2) + 1];
      if (!w || !h) return;
      const list = JSON.parse(this.readCString(wasm._twl_display_list(this.handle))) as Cmd[];
      const pw = Math.ceil(w * this.dpr), ph = Math.ceil(h * this.dpr);
      if (this.element.width !== pw || this.element.height !== ph) {
        this.element.width = pw;
        this.element.height = ph;
        this.element.style.width = `${w}px`;
        this.element.style.height = `${h}px`;
      }
      this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
      this.ctx.clearRect(0, 0, w, h);
      this.backend!.paint(this.ctx, list);
      this.stats.width = pw;
      this.stats.height = ph;
      this.stats.displayOps = list.length;
    });
    this.stats.paints++;
    this.stats.paintMs = performance.now() - t0;
    const now = performance.now();
    if (now - this.lastReport > 1000) {
      this.lastReport = now;
      try {
        const s = JSON.parse(this.readCString(wasm._twl_stats(this.handle)));
        this.stats.builds = s.builds ?? 0;
        this.stats.parseLayoutMs = s.parseLayoutMs ?? 0;
        this.onDiag?.('litehtml-build', `serialise ${(s.serializeMs ?? 0).toFixed(2)} ms, parse+layout ${(s.parseLayoutMs ?? 0).toFixed(1)} ms in ${s.passes} pass(es), ${s.htmlBytes} B html, build #${s.builds}`);
      } catch { /* diagnostics only */ }
      this.onDiag?.('litehtml-paint', `${this.stats.paintMs.toFixed(1)} ms, ${this.stats.displayOps} draw ops, ${this.stats.width}×${this.stats.height} px, paint #${this.stats.paints}`);
    }
  }

  // ───────────── host → wasm ─────────────

  private deliver(msg: HostToRenderer) {
    if (msg.type === 'requestFrame') { this.requestFrame(); return; }
    if (msg.type === 'frozen') this.frozen = true;
    this.dispatch(this.withCString(JSON.stringify(msg), (p) => this.wasm!._twl_receive(this.handle, p)));
  }

  private setWidth(width: number) {
    if (!this.handle) return;
    this.dispatch(this.guard(() => this.wasm!._twl_set_width(this.handle, width)));
  }

  /** A JSON array of renderer → host messages from the wasm; each one goes to the host separately. */
  private dispatch(ptr: number | undefined) {
    if (!ptr || !this.wasm) return;
    const text = this.readCString(ptr);
    if (this.handle && this.wasm._twl_dirty(this.handle)) this.schedulePaint();
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
    const len = wasm.lengthBytesUTF8(s) + 1;
    const ptr = wasm._twl_alloc(len);
    wasm.stringToUTF8(s, ptr, len);
    try {
      return this.guard(() => fn(ptr)) as T;
    } finally {
      wasm._twl_free(ptr);
    }
  }

  /** Reads and frees a string returned by the wasm. */
  private readCString(ptr: number): string {
    const wasm = this.wasm!;
    if (!ptr) return '';
    const text = wasm.UTF8ToString(ptr);
    wasm._twl_string_free(ptr);
    return text;
  }

  /** A trap or an uncaught C++ exception ends the renderer: the canvas keeps its last paint, the host gets a diag. */
  private guard<T>(fn: () => T): T | undefined {
    try {
      return fn();
    } catch (e) {
      this.onDiag?.('renderer', `litehtml wasm failed: ${String(e).split('\n')[0]}`);
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
      if (frame) this.onMessage?.({ type: 'frame', time: performance.now() });
      if (paint) this.paint();
    });
  }
}
