// Chat feed demo (dev tooling for the web dev app, `?feed=1`; nothing here ships
// in a native build). The web port of the idea behind ios/DemoApp/Sources/ChatFeed
// (docs/chat-feed-ios.md): a vertical feed of 60–80 bubbles, each a WidgetHost
// on corpus/showcase (the interactive, animated demos; `corpus=common` for the
// renderer-comparison corpus, `corpus=gestures` for the drag claims, `corpus=safe-area` for the client's time over the
// corner), driven by the list's own lifecycle — a host is created when
// a bubble comes near the viewport, hidden (`setVisible(false)`) when it leaves
// it, and dropped when it is screens away (the height is remembered so the
// layout does not jump; a host from the pool replaces it when it comes back).
// Four things to see, not click:
//   1. the warmed worker pool on/off while scrolling: with the pool a bubble
//      shows content as it arrives, without it the bubble sits blank through
//      the QuickJS + sandbox-bundle boot (the striped placeholder counts the ms);
//   2. the renderer swapped live on the same feed — iframe → shadow DOM →
//      canvas (litehtml / Blitz): same HTML, same sandbox, same validator, the
//      renderer seam (src/host/renderer.ts) is the only thing that changes;
//   3. one hostile bubble (corpus/attacks) among the normal ones: it is
//      stopped or throttled by the runtime limits, the canary stays at 0, the
//      feed keeps scrolling;
//   4. a metrics overlay: booted, time to content (median / p90) per pool
//      setting, memory, canary;
//   5. the bench panel (feed-bench.ts, the **bench** button / `?bench=1`): the
//      same feed run N passes per renderer × pool setting with the scripted
//      scroll, every boot recorded (`BootRecord`), two live tables and a JSON
//      report — the web twin of the iOS feed-report.json.
// Trusted glue only: the sandbox, the validator and the renderers are untouched,
// every bubble is an ordinary WidgetHost.

import { startBench, measureMemory, type BenchApi, type MemorySample } from './feed-bench';
import { BlitzRenderer } from './renderers/blitz-renderer';
import { IframeRenderer } from '@telegram/widget-sandbox/host/iframe-renderer';
import { LitehtmlRenderer } from './renderers/litehtml-renderer';
import type { Renderer, RendererFactory } from '@telegram/widget-sandbox/host/renderer';
import { ShadowDomRenderer } from '@telegram/widget-sandbox/host/shadow-renderer';
import { WidgetHost } from '@telegram/widget-sandbox/host/widget-host';
import { WorkerPool } from '@telegram/widget-sandbox/host/worker-pool';
import { WIDGET_FEATURES, WIDGET_POLICY } from './policy';
import { bubbleGesturesOption, showBubbleGesture, type GestureMode } from './bubble-gestures';
import { DEFAULT_RESIZE_ANCHOR, type ResizeAnchor } from '@telegram/widget-sandbox/shared/sdk-policy';
import { REFERENCE_TIME_LABEL, referenceSafeArea, timeCornerStyle } from '@telegram/widget-sandbox/shared/reference';

export type FeedRenderer = 'iframe' | 'shadow' | 'blitz' | 'litehtml';
/** Which corpus fills the widget bubbles; `BATCH_ORDER` is the round-robin over its batches (light first). */
export type FeedCorpus = 'showcase' | 'common' | 'gestures' | 'resize' | 'safe-area';
const CORPORA: { kind: FeedCorpus; title: string }[] = [
  { kind: 'showcase', title: 'corpus/showcase: 21 interactive, animated demos on the full web profile (boarding pass, ride tracking, smart home, kanban, live poll …)' },
  { kind: 'common', title: 'corpus/common: the 32 widgets on the profile every renderer shares, for like-for-like renderer timings' },
  { kind: 'gestures', title: 'corpus/gestures: what the horizontal-only rule removed (v0.1.3: games on touch-action none and the same games on pan-y, joystick and 2048, now alike; a wheel picker that no longer turns) and what is fine with horizontal only (paddle, card carousel, range slider)' },
  { kind: 'resize', title: 'corpus/resize: one widget per resize anchor (setResizeAnchor, v0.1.3): an FAQ that grows downward (the default, top), a support thread that grows upward (bottom), results that grow downward (top)' },
  { kind: 'safe-area', title: 'corpus/safe-area: the client\'s time over the bottom corner of a widget with no caption below it (docs/sdk-v1.md §2.17): every bubble gets the reference band and the time drawn over it; a diagnostic, full-bleed cards (one right-to-left), an unstyled note, CSS as models write it, a canvas chart, the caption pair' },
];
const BATCH_ORDER: Record<FeedCorpus, string[]> = {
  showcase: ['travel', 'commerce', 'life', 'work'], common: ['cards', 'interactive', 'live', 'heavy'], gestures: ['1-pairs', '2-horizontal', '3-vertical'],
  resize: ['1-default', '2-grows-up', '3-grows-down'],
  'safe-area': ['1-diagnostic', '2-cards', '3-css', '4-script', '5-caption'],
};
const RENDERERS: { kind: FeedRenderer; label: string; title: string }[] = [
  { kind: 'iframe', label: 'iframe', title: 'sandboxed srcdoc iframe per bubble (product renderer)' },
  { kind: 'shadow', label: 'shadow', title: 'the same renderer core on a shadow root in this page, no iframe (prototype)' },
  { kind: 'litehtml', label: 'litehtml', title: 'litehtml (wasm) painting validated ops on a canvas — needs pnpm build:litehtml-web' },
  { kind: 'blitz', label: 'blitz', title: 'Blitz (wasm) painting validated ops on a canvas — needs pnpm build:blitz-web' },
];
const FACTORIES: Record<FeedRenderer, RendererFactory> = {
  iframe: IframeRenderer.factory, shadow: ShadowDomRenderer.factory, blitz: BlitzRenderer.factory, litehtml: LitehtmlRenderer.factory,
};
/** Hostile bubbles on offer (corpus/attacks); each is stopped or contained by a different runtime limit. */
const HOSTILE: { name: string; title: string }[] = [
  { name: 'none', title: 'no hostile bubble' },
  { name: 'fallback-at-boot', title: 'infinite loop in the first script: killed by the boot CPU slice (500 ms), the static markup stays as the frozen fallback' },
  { name: 'cpu-raf-burn', title: 'a 30 ms rAF loop: throttled by the 300 ms/s CPU quota, not killed' },
  { name: 'cpu-greedy-timer', title: 'greedy timers: deferred by the CPU quota' },
  { name: 'timer-flood', title: '100 000 setTimeout calls: refused past 256 timers' },
  { name: 'api-fetch', title: 'fetch() to the canary: NetworkDisabledError in the sandbox, nothing leaves' },
  { name: 'markup-img-src', title: 'an <img src=http://…> to the canary: the validator drops the URL' },
  { name: 'mem-steady-growth', title: 'keeps 8 MB of typed arrays per tick; watch the QuickJS heap line (the web QuickJS build does not cap typed-array growth today: a sandbox gap, not a feed one)' },
  { name: 'mem-burst-alloc', title: 'one huge allocation' },
];
/** The hostile bubble's position among the widget bubbles: the third one, on the first screen. */
const HOSTILE_INDEX = 2;
const MIN_HEIGHT = 24; // WidgetHost's clamp: an empty document reports exactly this
const TEXTS = [
  'Here, have a look at this one', 'can you make it a card?', 'ok that works', 'and the live one?', 'nice', 'try scrolling faster',
  'this is the heavy batch', 'same file, other renderer', 'what does the dashboard look like now', 'ship it', 'one more', 'let me check the poll',
];

interface Bubble {
  /** Index among the widget bubbles (the head line shows it). */
  idx: number;
  file: string;
  hostile: boolean;
  el: HTMLElement;
  head: HTMLElement;
  wrap: HTMLElement;
  foot: HTMLElement;
  host: WidgetHost | null;
  state: 'idle' | 'queued' | 'booting' | 'live' | 'dead' | 'parked' | 'stopped';
  near: boolean;
  visible: boolean;
  /** Remembered bubble height (px): the placeholder while parked or booting, so the layout holds still. */
  height: number;
  /** The edge that stays put when the height changes (the widget's `setResizeAnchor`, from the last size report; kept with the height). */
  anchor: ResizeAnchor;
  /** The bubble's laid-out height (head and foot included) at the last anchoring pass. */
  laidOut: number;
  /** When the current blank stretch started (visible, no content yet); 0 when not blank. */
  blankSince: number;
  createdAt: number;
  loadedAt: number;
  contentAt: number;
  /** When the bubble first came into view since this boot (0 until then; reset when the host is dropped). */
  shownAt: number;
  shownSampled: boolean;
  /** A size report arrived in this boot (the remembered height from an earlier boot does not count as content). */
  sized: boolean;
  poolOn: boolean;
  renderer: FeedRenderer;
  boots: number;
  blocked: number;
  throttled: number;
  timerLimit: boolean;
  heap: number;
  deadReason: string | null;
  /** Killed once: never re-run (a product keeps the frozen fallback, not a second attempt). */
  killed: boolean;
  lastError: string | null;
  /** The record of the current boot (feed-bench.ts reads them per pass). */
  rec: BootRecord | null;
}

/**
 * One boot of one bubble, every clock in `performance.now()` ms. What the
 * bench aggregates; nothing here is typed in by hand. `blankMs` is the time
 * the bubble was inside the viewport without content (it accumulates while
 * the bubble is visible and stops at content or when the host is dropped).
 */
export interface BootRecord {
  idx: number;
  file: string;
  renderer: FeedRenderer;
  poolOn: boolean;
  /** The pool had a warm worker when this host took one (false = booted a fresh one, pool or not). */
  poolHit: boolean;
  /** The first boot of the feed since `reset` (the cold path: the first bubble the reader sees). */
  cold: boolean;
  createdAt: number;
  /** `new WidgetHost` on the main thread: renderer construction + validator + worker handoff, synchronous. */
  hostCreateMs: number;
  /** The renderer factory + `load()`, synchronous part. */
  rendererCreateMs: number;
  /** Renderer construction → its first message (the canvas renderers' `ready` after the wasm init; the iframe's and the shadow root's first size report, after the document load for the iframe); null until it arrives. */
  rendererReadyMs: number | null;
  /** Creation → first content (the time-to-first-paint of the bubble). */
  contentMs: number | null;
  loadedMs: number | null;
  /** First time the bubble was inside the viewport in this boot (0 = never). */
  shownAt: number;
  blankMs: number;
  dead: string | null;
}

interface Samples { boot: number[]; shown: number[]; loaded: number[]; misses: number }
const newSamples = (): Samples => ({ boot: [], shown: [], loaded: [], misses: 0 });

export interface FeedOptions {
  theme: 'light' | 'dark';
  params: URLSearchParams;
  /** The bubbles' own presses (bubble-gestures.ts); `phone` when absent. */
  gestures?: GestureMode;
}

export interface FeedApi {
  setTheme(theme: 'light' | 'dark'): void;
  /** Who gets a press: the bubble of a phone or a desktop client unless touch-action claims it, or (`off`) the widget. */
  setGestures(mode: GestureMode): void;
  setRenderer(kind: FeedRenderer): void;
  setPool(on: boolean): void;
  /** `rebuild` false: only the setting changes, the next `reset` applies it (the bench sets several at once). */
  setHostile(name: string, rebuild?: boolean): void;
  setCorpus(kind: FeedCorpus, rebuild?: boolean): void;
  setCount(n: number, rebuild?: boolean): void;
  reset(): Promise<void>;
  /** Drop every host and empty the feed (a `reset` without the rebuild): the bench measures its memory baseline here. */
  clear(): void;
  /** Scripted scroll like the iOS `step` run: one screen, pause, repeat, down and back up. Resolves when done. */
  autoScroll(): Promise<void>;
  stop(): void;
  /** Pause the demo's own periodic work (overlay refresh, memory and canary polls) while the bench measures. */
  quiet(on: boolean): void;
  /** A snapshot of the metrics the overlay shows. */
  readonly summary: FeedSummary;
  readonly bubbles: readonly Bubble[];
  readonly pool: WorkerPool | null;
  /** Every boot since the last `reset`, in creation order. */
  readonly records: readonly BootRecord[];
  readonly settings: { corpus: FeedCorpus; renderer: FeedRenderer; poolOn: boolean; poolSize: number; hostile: string; count: number; lanes: number; ahead: number; keep: number; theme: 'light' | 'dark' };
  /** The scroll container's size (the bench records it: the step scroll is one screen of it). */
  readonly viewport: { width: number; height: number };
  /** The scroll container itself (the bench's memory ramp scrolls it). */
  readonly scroller: HTMLElement;
  /** Drop distance in screens (`keep`), changeable live; the bench lifts it for the memory ramp and puts it back. */
  setKeep(screens: number): void;
  bench: BenchApi;
}

export interface FeedSummary {
  corpus: FeedCorpus;
  renderer: FeedRenderer;
  pool: { size: number; warm: number; created: number; hits: number; misses: number } | null;
  count: number;
  live: number;
  booted: number;
  booting: number;
  parked: number;
  dead: number;
  samples: { on: Samples; off: Samples };
  memory: { total: number | null; workers: number | null; frames: number | null; heaps: number; source: string };
  canary: { state: 'unknown' | 'ok' | 'off'; hits: number };
  blocked: number;
  scroll: { p95: number; slow: number; frames: number };
}

const now = () => performance.now();
const quantile = (xs: number[], q: number) => {
  if (!xs.length) return NaN;
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.floor(s.length * q))];
};
const fmtMs = (v: number) => (Number.isNaN(v) ? '—' : `${v < 0 ? '−' : ''}${Math.abs(v).toFixed(0)} ms`);
const fmtMb = (b: number | null) => (b === null ? 'n/a' : `${(b / 1048576).toFixed(0)} MB`);
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
/** Seeded shuffle (an LCG, like the corpus files use) so two runs show the same feed. */
function shuffled<T>(xs: T[], seed: number): T[] {
  const out = [...xs];
  let s = seed >>> 0;
  for (let i = out.length - 1; i > 0; i--) {
    s = (s * 1664525 + 1013904223) >>> 0;
    const j = s % (i + 1);
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

export function startFeed(view: HTMLElement, opts: FeedOptions): FeedApi {
  const params = opts.params;
  let theme = opts.theme;
  let gestures: GestureMode = opts.gestures ?? 'phone';
  let renderer: FeedRenderer = (RENDERERS.find((r) => r.kind === params.get('renderer'))?.kind) ?? 'iframe';
  let corpus: FeedCorpus = (CORPORA.find((c) => c.kind === params.get('corpus'))?.kind) ?? 'showcase';
  const poolSize = Math.max(1, Number(params.get('poolSize') ?? 6));
  let poolOn = params.get('pool') !== '0';
  let hostile = HOSTILE.find((h) => h.name === params.get('hostile'))?.name ?? 'fallback-at-boot';
  let count = Math.min(200, Math.max(1, Number(params.get('count') ?? 72)));
  /** How many sandboxes boot at once (a QuickJS worker each); more than a few contend for the boot CPU slice. */
  const lanes = Math.max(1, Number(params.get('lanes') ?? 4));
  /** Prefetch distance and the drop distance, in screens. */
  const ahead = Math.max(0, Number(params.get('ahead') ?? 0.5));
  let keep = Math.max(ahead, Number(params.get('keep') ?? 2));

  let pool: WorkerPool | null = null;
  let bubbles: Bubble[] = [];
  const htmls = new Map<string, string>();
  const queue: Bubble[] = [];
  let booting = 0;
  let booted = 0;
  let samples = { on: newSamples(), off: newSamples() };
  let generation = 0;
  let records: BootRecord[] = [];
  let quietMode = false;

  // ───────────── DOM ─────────────

  view.replaceChildren();
  const bar = document.createElement('div');
  bar.className = 'feed-bar';
  const corpusLabel = document.createElement('label');
  corpusLabel.className = 'feed-group';
  corpusLabel.append('corpus ');
  const corpusSelect = document.createElement('select');
  for (const c of CORPORA) {
    const o = document.createElement('option');
    o.value = c.kind;
    o.textContent = c.kind;
    o.title = c.title;
    corpusSelect.appendChild(o);
  }
  corpusSelect.value = corpus;
  corpusSelect.title = CORPORA.find((c) => c.kind === corpus)!.title;
  corpusSelect.addEventListener('change', () => setCorpus(corpusSelect.value as FeedCorpus));
  corpusLabel.appendChild(corpusSelect);
  const rendererGroup = document.createElement('span');
  rendererGroup.className = 'feed-group';
  rendererGroup.append('renderer ');
  const rendererInputs: HTMLInputElement[] = [];
  for (const r of RENDERERS) {
    const label = document.createElement('label');
    label.title = r.title;
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'feed-renderer';
    input.value = r.kind;
    input.checked = r.kind === renderer;
    input.addEventListener('change', () => { if (input.checked) setRenderer(r.kind); });
    label.append(input, r.label);
    rendererGroup.appendChild(label);
    rendererInputs.push(input);
  }
  const poolLabel = document.createElement('label');
  poolLabel.className = 'feed-group';
  poolLabel.title = `a pool of ${poolSize} warmed sandbox workers (QuickJS + sandbox bundle evaluated, waiting for a widget); off = every bubble boots its own`;
  const poolInput = document.createElement('input');
  poolInput.type = 'checkbox';
  poolInput.checked = poolOn;
  poolInput.addEventListener('change', () => setPool(poolInput.checked));
  poolLabel.append(poolInput, ` pool ×${poolSize}`);
  const hostileLabel = document.createElement('label');
  hostileLabel.className = 'feed-group';
  hostileLabel.append('hostile ');
  const hostileSelect = document.createElement('select');
  for (const h of HOSTILE) {
    const o = document.createElement('option');
    o.value = h.name;
    o.textContent = h.name;
    o.title = h.title;
    hostileSelect.appendChild(o);
  }
  hostileSelect.value = hostile;
  hostileSelect.addEventListener('change', () => setHostile(hostileSelect.value));
  hostileLabel.appendChild(hostileSelect);
  const countLabel = document.createElement('label');
  countLabel.className = 'feed-group';
  countLabel.append('bubbles ');
  const countInput = document.createElement('input');
  countInput.type = 'number';
  countInput.min = '1';
  countInput.max = '200';
  countInput.value = String(count);
  countInput.addEventListener('change', () => setCount(Number(countInput.value) || 72));
  countLabel.appendChild(countInput);
  const resetBtn = document.createElement('button');
  resetBtn.type = 'button';
  resetBtn.textContent = 'reset';
  resetBtn.title = 'drop every host, clear the metrics, scroll to the top (the pool setting stays): run the same scroll with the pool on and off';
  resetBtn.addEventListener('click', () => void reset());
  const scrollBtn = document.createElement('button');
  scrollBtn.type = 'button';
  scrollBtn.textContent = '▶ auto-scroll';
  scrollBtn.title = 'scripted scroll like the iOS feed run: one screen, pause 0.9 s, repeat to the bottom and back';
  scrollBtn.addEventListener('click', () => { if (autoRunning) stop(); else void autoScroll(); });
  const benchBtn = document.createElement('button');
  benchBtn.type = 'button';
  benchBtn.textContent = '📊 bench';
  benchBtn.title = 'the measurement panel: N passes of the scripted scroll per renderer × pool setting, two live tables, a JSON report (feed-bench.ts)';
  bar.append(corpusLabel, rendererGroup, poolLabel, hostileLabel, countLabel, resetBtn, scrollBtn, benchBtn);

  const main = document.createElement('div');
  main.className = 'feed-main';
  const benchPanel = document.createElement('div');
  benchPanel.className = 'feed-bench hidden';
  const scroller = document.createElement('div');
  scroller.className = 'feed-scroll';
  const column = document.createElement('div');
  column.className = 'feed-col';
  scroller.appendChild(column);
  main.append(benchPanel, scroller);
  const overlay = document.createElement('div');
  overlay.className = 'feed-overlay';
  view.append(bar, main, overlay);
  benchBtn.addEventListener('click', () => { benchPanel.classList.toggle('hidden'); benchBtn.classList.toggle('on', !benchPanel.classList.contains('hidden')); });

  // ───────────── lifecycle: three observers on the scroller ─────────────

  const byEl = new Map<Element, Bubble>();
  const ioNear = new IntersectionObserver((entries) => {
    for (const e of entries) {
      const b = byEl.get(e.target);
      if (!b) continue;
      b.near = e.isIntersecting;
      if (b.near && !b.host && b.state !== 'queued' && !b.killed) enqueue(b);
      else if (!b.near && b.state === 'queued') { b.state = 'idle'; const i = queue.indexOf(b); if (i >= 0) queue.splice(i, 1); }
    }
    pump();
  }, { root: scroller, rootMargin: `${Math.round(ahead * 100)}% 0px` });
  const ioVisible = new IntersectionObserver((entries) => {
    for (const e of entries) {
      const b = byEl.get(e.target);
      if (!b || b.visible === e.isIntersecting) continue;
      b.visible = e.isIntersecting;
      // `shownAt` is the first time the bubble was seen since its host was created (or dropped): one sample per boot.
      if (b.visible && !b.shownAt) { b.shownAt = now(); trySampleShown(b); }
      // The record: first sight of the bubble in this boot, and the blank time (visible without content: starts when a
      // content-less bubble comes in, stops when it leaves or at content).
      if (b.rec && b.visible && !b.rec.shownAt) b.rec.shownAt = now();
      if (b.rec && !b.rec.contentMs) {
        if (b.visible) b.blankSince = now();
        else stopBlank(b);
      }
      b.host?.setVisible(b.visible);
    }
  }, { root: scroller, rootMargin: '0px' });
  const farObserver = () => new IntersectionObserver((entries) => {
    for (const e of entries) {
      const b = byEl.get(e.target);
      if (b && !e.isIntersecting && b.host && b.state !== 'dead' && b.state !== 'booting') park(b);
    }
  }, { root: scroller, rootMargin: `${Math.round(keep * 100)}% 0px` });
  let ioFar = farObserver();
  /** The drop distance, live: the bench lifts it for its memory ramp (every bubble boots and stays). */
  function setKeep(screens: number) {
    keep = Math.max(ahead, screens);
    ioFar.disconnect();
    ioFar = farObserver();
    for (const b of bubbles) ioFar.observe(b.el);
  }

  // ───────────── the resize anchor (docs/sdk-v1.md §5.6): the anchored edge stays put on screen ─────────────

  // The list compensates its scroll itself (the browser's own scroll anchoring is off: .feed-scroll in style.css): a
  // bubble on screen anchored at the bottom keeps its bottom edge, the content above it moves; anchored at the top it
  // keeps its top edge, the content below moves; a bubble above the viewport never moves what is on screen. The same
  // for every height change: a size report, the boot placeholder (the cached height) giving way, a re-created bubble.
  const anchorObserver = new ResizeObserver((entries) => {
    const view = scroller.getBoundingClientRect();
    let shift = 0;
    for (const e of entries) {
      const b = byEl.get(e.target);
      if (!b) continue;
      const h = b.el.offsetHeight, delta = h - b.laidOut;
      if (!b.laidOut || !delta) { b.laidOut = h; continue; }
      b.laidOut = h;
      const r = b.el.getBoundingClientRect();
      const top = r.top, oldBottom = top + h - delta;
      if (oldBottom <= view.top) shift += delta;
      else if (top < view.bottom && b.anchor === 'bottom') shift += delta;
    }
    if (shift) scroller.scrollTop += shift;
  });

  function enqueue(b: Bubble) {
    b.state = 'queued';
    queue.push(b);
    renderHead(b);
  }

  /** Starts boots up to `lanes` at a time, nearest to the viewport first. */
  function pump() {
    if (queue.length > 1) {
      const mid = scroller.scrollTop + scroller.clientHeight / 2;
      const dist = (b: Bubble) => Math.abs(b.el.offsetTop + b.el.offsetHeight / 2 - mid);
      queue.sort((a, b) => dist(a) - dist(b));
    }
    while (booting < lanes && queue.length) {
      const b = queue.shift()!;
      if (b.near && !b.host && !b.killed) boot(b);
      else b.state = b.killed ? 'stopped' : 'idle';
    }
  }

  function boot(b: Bubble) {
    const html = htmls.get(b.file);
    if (html === undefined) { b.state = 'idle'; return; }
    const gen = generation;
    b.state = 'booting';
    b.boots++;
    b.createdAt = now();
    b.loadedAt = b.contentAt = 0;
    b.shownSampled = false;
    b.sized = false;
    // On screen already (the first screen, a renderer switch): the reader has been looking at it since now; otherwise the visible observer sets it.
    b.shownAt = b.visible ? b.createdAt : 0;
    b.poolOn = !!pool;
    b.renderer = renderer;
    b.blocked = 0; b.throttled = 0; b.timerLimit = false; b.heap = 0; b.deadReason = null; b.lastError = null;
    b.wrap.replaceChildren();
    b.wrap.classList.add('booting');
    b.wrap.dataset.ms = '0 ms';
    b.wrap.style.minHeight = (b.height || 72) + 'px';
    const col = b.poolOn ? samples.on : samples.off;
    if (pool && pool.warm === 0) col.misses++;
    const rec: BootRecord = {
      idx: b.idx, file: b.file, renderer, poolOn: b.poolOn, poolHit: !!pool && pool.warm > 0, cold: records.length === 0,
      createdAt: b.createdAt, hostCreateMs: 0, rendererCreateMs: 0, rendererReadyMs: null, contentMs: null, loadedMs: null,
      shownAt: b.shownAt, blankMs: 0, dead: null,
    };
    b.rec = rec;
    b.blankSince = b.visible ? b.createdAt : 0;
    records.push(rec);
    booting++;
    startBootTicker();
    let released = false;
    const release = () => { if (!released) { released = true; booting--; pump(); } };
    const timeout = setTimeout(release, 20_000);
    const content = () => {
      if (b.contentAt || gen !== generation) return;
      b.contentAt = now();
      b.state = 'live';
      booted++;
      b.wrap.classList.remove('booting');
      b.wrap.style.minHeight = '';
      col.boot.push(b.contentAt - b.createdAt);
      rec.contentMs = b.contentAt - b.createdAt;
      stopBlank(b);
      trySampleShown(b);
      renderHead(b);
    };
    const t0 = now();
    b.host = new WidgetHost(b.wrap, html, {
      policy: WIDGET_POLICY,
      features: WIDGET_FEATURES,
      theme,
      rendererFactory: timedFactory(FACTORIES[renderer], rec),
      workerPool: pool ?? undefined,
      visible: b.visible,
      // corpus/safe-area: messages with no caption below the widget, the client's time over their corner.
      ...(corpus === 'safe-area' ? { contentSafeArea: referenceSafeArea() } : {}),
      bubbleGestures: bubbleGesturesOption(gestures),
      onBubbleGesture: (g) => { if (b.wrap.parentElement) showBubbleGesture(b.wrap.parentElement, g); },
      onLoaded: () => {
        if (gen !== generation) return;
        b.loadedAt = now();
        col.loaded.push(b.loadedAt - b.createdAt);
        rec.loadedMs = b.loadedAt - b.createdAt;
        clearTimeout(timeout);
        release();
        // Content that arrived before `loaded` (the first size report) was already counted; a widget with no size report yet gets it on the next one.
        if (b.sized && b.height > MIN_HEIGHT) content();
        renderHead(b);
      },
      onResize: (h, anchor) => {
        if (gen !== generation) return;
        b.height = h;
        b.anchor = anchor;
        b.sized = true;
        // The first size report of an empty document is exactly MIN_HEIGHT; content is anything taller, or any report after the scripts ran.
        if (!b.contentAt && (b.loadedAt || h > MIN_HEIGHT)) content();
      },
      onDead: (reason) => {
        if (gen !== generation) return;
        b.state = 'dead';
        b.killed = true;
        b.deadReason = reason;
        rec.dead = reason;
        stopBlank(b);
        b.wrap.classList.remove('booting');
        b.wrap.style.minHeight = '';
        clearTimeout(timeout);
        release();
        renderHead(b);
        renderFoot(b);
      },
      onError: (text) => { b.lastError = text; renderFoot(b); },
      onDiag: (kind) => {
        if (kind.endsWith('-blocked')) b.blocked++;
        else if (kind === 'cpu-throttled') b.throttled++;
        else if (kind.startsWith('timer-limit')) b.timerLimit = true;
        else return;
        renderHead(b);
      },
      onStats: (s) => { b.heap = s.memoryBytes; },
    });
    rec.hostCreateMs = now() - t0;
    renderHead(b);
  }

  /** Closes the bubble's current blank stretch (content arrived, it left the viewport, the host is dropped). */
  function stopBlank(b: Bubble) {
    if (b.rec && b.blankSince) b.rec.blankMs += now() - b.blankSince;
    b.blankSince = 0;
  }

  /** Wraps a renderer factory so the record gets the renderer's synchronous creation cost and its creation → first message time. */
  function timedFactory(factory: RendererFactory, rec: BootRecord): RendererFactory {
    return (container, t) => {
      const t0 = now();
      const inner = factory(container, t);
      rec.rendererCreateMs = now() - t0;
      const wrapper: Renderer = {
        element: inner.element,
        cssScope: inner.cssScope,
        onMessage: null,
        onDiag: null,
        load() { const l0 = now(); inner.load(); rec.rendererCreateMs += now() - l0; },
        send: (m) => inner.send(m),
        setTheme: (th) => inner.setTheme(th),
        destroy: () => inner.destroy(),
      };
      inner.onMessage = (m) => {
        if (rec.rendererReadyMs === null) rec.rendererReadyMs = now() - t0;
        wrapper.onMessage?.(m);
      };
      inner.onDiag = (k, d) => wrapper.onDiag?.(k, d);
      return wrapper;
    };
  }

  function trySampleShown(b: Bubble) {
    if (b.shownSampled || !b.contentAt || !b.shownAt || b.state === 'parked') return;
    b.shownSampled = true;
    (b.poolOn ? samples.on : samples.off).shown.push(b.contentAt - b.shownAt);
  }

  /** The bubble is screens away: drop the host, keep the height (a product would keep a snapshot too). */
  function park(b: Bubble) {
    stopBlank(b);
    b.rec = null;
    b.host?.destroy();
    b.host = null;
    b.state = 'parked';
    b.shownSampled = false;
    b.shownAt = 0;
    const ph = document.createElement('div');
    ph.className = 'feed-parked';
    ph.style.height = (b.height || 72) + 'px';
    ph.textContent = 'parked — host dropped, height remembered';
    b.wrap.replaceChildren(ph);
    b.wrap.classList.remove('booting');
    renderHead(b);
  }

  /** A killed widget after its host is gone (renderer switch / reset): a product shows the frozen fallback, we show the stop line. */
  function stopped(b: Bubble) {
    stopBlank(b);
    b.rec = null;
    b.host?.destroy();
    b.host = null;
    b.state = 'stopped';
    const ph = document.createElement('div');
    ph.className = 'feed-parked feed-stopped';
    ph.style.height = (b.height || 72) + 'px';
    ph.textContent = 'Widget stopped';
    b.wrap.replaceChildren(ph);
    b.wrap.classList.remove('booting');
    renderHead(b);
    renderFoot(b);
  }

  // ───────────── bubble chrome ─────────────

  function renderHead(b: Bubble) {
    const parts = [`#${b.idx + 1}`, b.file];
    if (b.hostile) parts.push('⚠ hostile');
    if (b.state === 'queued') parts.push('queued');
    else if (b.state === 'booting') parts.push(`booting (${b.renderer}${b.poolOn ? ', pooled' : ', cold'})`);
    else if (b.state === 'parked') parts.push(`parked · booted ${b.boots}×`);
    else if (b.state === 'stopped') parts.push('stopped');
    else if (b.contentAt) parts.push(`${b.renderer}${b.poolOn ? ' · pooled' : ' · cold'} · content in ${(b.contentAt - b.createdAt).toFixed(0)} ms${b.boots > 1 ? ` · boot ${b.boots}` : ''}`);
    if (b.blocked) parts.push(`blocked ×${b.blocked}`);
    if (b.throttled) parts.push(`CPU deferred ×${b.throttled}`);
    if (b.timerLimit) parts.push('timers capped');
    if (b.state === 'dead') parts.push('✖ stopped');
    b.head.textContent = parts.join(' · ');
    b.el.classList.toggle('dead', b.state === 'dead' || b.state === 'stopped');
  }
  function renderFoot(b: Bubble) {
    const line = b.deadReason ? `Widget stopped: ${b.deadReason.split('\n')[0]}` : b.lastError ? b.lastError.split('\n')[0] : '';
    b.foot.textContent = line;
    b.foot.classList.toggle('hidden', !line);
  }

  function makeBubble(idx: number, file: string, isHostile: boolean): Bubble {
    const el = document.createElement('div');
    el.className = 'feed-bubble widget';
    const head = document.createElement('div');
    head.className = 'bubble-head';
    const bubble = document.createElement('div');
    bubble.className = 'bubble'; // the widget's canvas: no padding, the radius clips (style.css)
    const wrap = document.createElement('div');
    wrap.className = 'frame-wrap feed-wrap';
    bubble.appendChild(wrap);
    if (corpus === 'safe-area') {
      // The client's time over a widget with no caption below it: a view over the widget, its taps the client's.
      const time = document.createElement('div');
      time.setAttribute('style', timeCornerStyle(referenceSafeArea()));
      time.textContent = REFERENCE_TIME_LABEL;
      bubble.appendChild(time);
    }
    const foot = document.createElement('div');
    foot.className = 'feed-foot hidden';
    el.append(head, bubble, foot);
    const b: Bubble = {
      idx, file, hostile: isHostile, el, head, wrap, foot, host: null, state: 'idle', near: false, visible: false, height: 0, anchor: DEFAULT_RESIZE_ANCHOR, laidOut: 0,
      createdAt: 0, loadedAt: 0, contentAt: 0, shownAt: 0, shownSampled: false, sized: false, poolOn: false, renderer, boots: 0,
      blocked: 0, throttled: 0, timerLimit: false, heap: 0, deadReason: null, killed: false, lastError: null, rec: null, blankSince: 0,
    };
    renderHead(b);
    return b;
  }
  function makeText(i: number): HTMLElement {
    const el = document.createElement('div');
    el.className = 'feed-bubble text' + (i % 3 === 1 ? ' out' : '');
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = TEXTS[i % TEXTS.length];
    el.appendChild(bubble);
    return el;
  }

  // ───────────── building the feed ─────────────

  let files: string[] = [];
  async function fetchHtml(file: string) {
    if (htmls.has(file)) return;
    const res = await fetch('/corpus/' + file);
    if (!res.ok) throw new Error('no such corpus file: ' + file);
    htmls.set(file, await res.text());
  }

  async function build() {
    const gen = ++generation;
    if (!files.length) {
      const all: string[] = await (await fetch('/api/corpus')).json();
      files = all.filter((f) => f.startsWith(`${corpus}/`));
    }
    // One of each batch in turn (travel, commerce, life, work, travel, … — or cards, interactive, live, heavy for `common`), each batch in a seeded order: a mixed feed that starts light.
    const batches = BATCH_ORDER[corpus].map((g, i) => shuffled(files.filter((f) => f.startsWith(`${corpus}/${g}/`)), 7 + i));
    const order: string[] = [];
    for (let i = 0; order.length < files.length; i++) for (const b of batches) if (i < b.length) order.push(b[i]);
    const plan: { file: string; hostile: boolean }[] = [];
    for (let i = 0; i < count; i++) plan.push({ file: order[i % order.length], hostile: false });
    if (hostile !== 'none' && count > HOSTILE_INDEX) plan[HOSTILE_INDEX] = { file: `attacks/${hostile}.html`, hostile: true };
    await Promise.all([...new Set(plan.map((p) => p.file))].map(fetchHtml));
    if (gen !== generation) return;
    bubbles = plan.map((p, i) => makeBubble(i, p.file, p.hostile));
    const frag = document.createDocumentFragment();
    bubbles.forEach((b, i) => {
      if (i % 2 === 0) frag.appendChild(makeText(i));
      frag.appendChild(b.el);
      byEl.set(b.el, b);
    });
    column.replaceChildren(frag);
    for (const b of bubbles) { ioNear.observe(b.el); ioVisible.observe(b.el); ioFar.observe(b.el); anchorObserver.observe(b.el); }
  }

  function teardown() {
    generation++;
    ioNear.disconnect(); ioVisible.disconnect(); ioFar.disconnect(); anchorObserver.disconnect();
    for (const b of bubbles) { stopBlank(b); b.rec = null; b.host?.destroy(); }
    bubbles = [];
    byEl.clear();
    queue.length = 0;
    booting = 0;
    booted = 0;
    column.replaceChildren();
  }

  function clear() {
    stop();
    teardown();
    samples = { on: newSamples(), off: newSamples() };
    records = [];
    scrollDeltas.length = 0;
    scroller.scrollTop = 0;
    renderOverlay();
  }

  async function reset() {
    clear();
    await build();
    syncUrl();
    renderOverlay();
  }

  // ───────────── the switches ─────────────

  function setRenderer(kind: FeedRenderer) {
    if (kind === renderer) return;
    renderer = kind;
    for (const i of rendererInputs) i.checked = i.value === kind;
    // Every live bubble swaps its renderer in place: the same HTML boots again behind the new seam; the height is remembered so nothing jumps.
    for (const b of bubbles) {
      if (b.state === 'queued') { b.state = 'idle'; }
      if (!b.host) continue;
      if (b.killed) { stopped(b); continue; }
      stopBlank(b);
      b.rec = null;
      b.host.destroy();
      b.host = null;
      b.state = 'idle';
      b.shownSampled = false;
      b.shownAt = 0;
    }
    queue.length = 0;
    booting = 0;
    for (const b of bubbles) if (b.near && !b.killed) enqueue(b);
    pump();
    syncUrl();
    renderOverlay();
  }

  function setPool(on: boolean) {
    poolOn = on;
    poolInput.checked = on;
    if (on && !pool) {
      pool = new WorkerPool(poolSize);
      void pool.prewarm().then(renderOverlay);
    } else if (!on && pool) {
      pool.destroy();
      pool = null;
    }
    syncUrl();
    renderOverlay();
  }

  function setHostile(name: string, rebuild = true) {
    hostile = HOSTILE.find((h) => h.name === name)?.name ?? 'none';
    hostileSelect.value = hostile;
    if (rebuild) void reset();
  }

  function setCorpus(kind: FeedCorpus, rebuild = true) {
    const next = CORPORA.find((c) => c.kind === kind)?.kind ?? 'showcase';
    if (next !== corpus) files = [];
    corpus = next;
    corpusSelect.value = corpus;
    corpusSelect.title = CORPORA.find((c) => c.kind === corpus)!.title;
    if (rebuild) void reset();
  }

  function setCount(n: number, rebuild = true) {
    count = Math.min(200, Math.max(1, Math.round(n) || 72));
    countInput.value = String(count);
    if (rebuild) void reset();
  }

  function quiet(on: boolean) {
    quietMode = on;
    if (!on) { renderOverlay(); void measureMemoryInto(); }
  }

  function setTheme(t: 'light' | 'dark') {
    theme = t;
    for (const b of bubbles) b.host?.setTheme(t);
    syncUrl();
  }
  function setGestures(mode: GestureMode) {
    gestures = mode;
    for (const b of bubbles) b.host?.setBubbleGestures(bubbleGesturesOption(mode));
    syncUrl();
  }

  function syncUrl() {
    const p = new URLSearchParams();
    p.set('feed', '1');
    if (theme === 'dark') p.set('theme', 'dark');
    if (gestures !== 'phone') p.set('gestures', gestures);
    if (corpus !== 'showcase') p.set('corpus', corpus);
    if (renderer !== 'iframe') p.set('renderer', renderer);
    if (!poolOn) p.set('pool', '0');
    if (poolSize !== 6) p.set('poolSize', String(poolSize));
    if (hostile !== 'fallback-at-boot') p.set('hostile', hostile);
    if (count !== 72) p.set('count', String(count));
    for (const k of ['lanes', 'ahead', 'keep', 'canary'] as const) if (params.has(k)) p.set(k, params.get(k)!);
    history.replaceState(null, '', '?' + p.toString());
  }

  // ───────────── scripted scroll ─────────────

  let autoRunning = false;
  let autoGen = 0;
  async function autoScroll() {
    if (autoRunning) return;
    autoRunning = true;
    const gen = ++autoGen;
    scrollBtn.textContent = '■ stop';
    let dir = 1;
    while (autoRunning && gen === autoGen) {
      scroller.scrollBy({ top: dir * scroller.clientHeight * 0.9, behavior: 'smooth' });
      await sleep(900);
      const atBottom = scroller.scrollTop + scroller.clientHeight >= scroller.scrollHeight - 2;
      if (dir > 0 && atBottom) dir = -1;
      else if (dir < 0 && scroller.scrollTop <= 1) break;
    }
    if (gen === autoGen) stop();
  }
  function stop() {
    autoRunning = false;
    autoGen++;
    scrollBtn.textContent = '▶ auto-scroll';
  }

  // ───────────── metrics ─────────────

  /** Frame intervals while the feed scrolls (the last ~3 s of scrolling). */
  const scrollDeltas: number[] = [];
  let measuring = false;
  let scrollUntil = 0;
  scroller.addEventListener('scroll', () => {
    scrollUntil = now() + 400;
    if (measuring) return;
    measuring = true;
    let last = now();
    const tick = (t: number) => {
      scrollDeltas.push(t - last);
      last = t;
      if (scrollDeltas.length > 180) scrollDeltas.splice(0, scrollDeltas.length - 180);
      if (now() < scrollUntil) requestAnimationFrame(tick); else measuring = false;
    };
    requestAnimationFrame(tick);
  }, { passive: true });

  // The booting placeholders count the milliseconds the reader waits (a frame loop only while something boots).
  let bootTicker = false;
  function startBootTicker() {
    if (bootTicker) return;
    bootTicker = true;
    const tick = () => {
      let any = false;
      for (const b of bubbles) if (b.state === 'booting') { any = true; b.wrap.dataset.ms = `${(now() - b.createdAt).toFixed(0)} ms`; }
      if (any) requestAnimationFrame(tick); else bootTicker = false;
    };
    requestAnimationFrame(tick);
  }

  const memory: FeedSummary['memory'] = { total: null, workers: null, frames: null, heaps: 0, source: '' };
  let measuringMemory = false;
  /** The overlay's memory line (feed-bench.ts's `measureMemory` does the reading; the bench calls it itself, on its own clock). */
  async function measureMemoryInto() {
    if (measuringMemory) return;
    measuringMemory = true;
    const m: MemorySample = await measureMemory();
    Object.assign(memory, { total: m.total, workers: m.workers, frames: m.frames, source: m.source });
    memory.heaps = bubbles.reduce((a, b) => a + (b.host ? b.heap : 0), 0);
    measuringMemory = false;
  }
  setInterval(() => { if (!quietMode) void measureMemoryInto(); }, 4000);
  void measureMemoryInto();

  // The canary of the attack suite (tests/canary-server.ts, `corepack pnpm canary`): every hostile widget points at it.
  const canary: FeedSummary['canary'] = { state: 'unknown', hits: 0 };
  async function pollCanary() {
    try {
      const hits = (await (await fetch('http://127.0.0.1:9999/__hits', { cache: 'no-store' })).json()) as unknown[];
      canary.state = 'ok';
      canary.hits = hits.length;
    } catch {
      canary.state = 'off';
    }
  }
  const canaryTimer = setInterval(() => { if (canary.state !== 'off' && !quietMode) void pollCanary(); }, 3000);
  void pollCanary().then(() => { if (canary.state === 'off') clearInterval(canaryTimer); });

  function summary(): FeedSummary {
    return {
      corpus,
      renderer,
      pool: pool ? { size: pool.size, warm: pool.warm, ...pool.stats } : null,
      count: bubbles.length,
      live: bubbles.filter((b) => b.host).length,
      booted,
      booting,
      parked: bubbles.filter((b) => b.state === 'parked').length,
      dead: bubbles.filter((b) => b.state === 'dead' || b.state === 'stopped').length,
      samples,
      memory: { ...memory },
      canary: { ...canary },
      blocked: bubbles.reduce((a, b) => a + b.blocked, 0),
      scroll: { p95: quantile(scrollDeltas, 0.95), slow: scrollDeltas.filter((d) => d > 32).length, frames: scrollDeltas.length },
    };
  }

  function renderOverlay() {
    const s = summary();
    const line = (text: string, cls = '') => { const d = document.createElement('div'); d.textContent = text; if (cls) d.className = cls; return d; };
    const table = document.createElement('table');
    const row = (cells: string[], head = false) => {
      const tr = document.createElement('tr');
      for (const c of cells) { const td = document.createElement(head ? 'th' : 'td'); td.textContent = c; tr.appendChild(td); }
      table.appendChild(tr);
    };
    const on = s.samples.on, off = s.samples.off;
    row(['', 'pool on', 'pool off'], true);
    row(['bubbles booted', String(on.boot.length), String(off.boot.length)]);
    row(['create → content, median', fmtMs(quantile(on.boot, 0.5)), fmtMs(quantile(off.boot, 0.5))]);
    row(['create → content, p90', fmtMs(quantile(on.boot, 0.9)), fmtMs(quantile(off.boot, 0.9))]);
    row(['shown → content, median', fmtMs(quantile(on.shown, 0.5)), fmtMs(quantile(off.shown, 0.5))]);
    row(['shown → content, p90', fmtMs(quantile(on.shown, 0.9)), fmtMs(quantile(off.shown, 0.9))]);
    row(['pool empty when taken', String(on.misses), '—']);
    const poolText = s.pool ? `pool ${s.pool.size}: ${s.pool.warm} warm · hits ${s.pool.hits} · misses ${s.pool.misses}` : 'pool off: every bubble boots QuickJS + the sandbox bundle itself';
    const mem = s.memory.total === null
      ? `memory ${s.memory.source || 'n/a'}`
      : `memory ${fmtMb(s.memory.total)}${s.memory.workers !== null ? ` (workers ${fmtMb(s.memory.workers)}, frames ${fmtMb(s.memory.frames)})` : ' page heap'} · QuickJS heaps of live widgets ${fmtMb(s.memory.heaps)}`;
    const canaryText = s.canary.state === 'ok' ? `canary 127.0.0.1:9999: ${s.canary.hits} hits` : s.canary.state === 'off' ? 'canary not running (corepack pnpm canary)' : 'canary …';
    overlay.replaceChildren(
      line(`corpus/${s.corpus} · ${s.renderer} renderer · ${s.count} bubbles · ${s.live} live · ${s.booting} booting · ${s.parked} parked · ${s.dead} stopped`),
      line(poolText, s.pool ? 'ok' : 'warn'),
      table,
      line('negative shown → content: the widget was ready before the bubble scrolled in', 'muted'),
      line(mem),
      line(`${canaryText} · blocked by validator/shim ×${s.blocked}`, s.canary.hits ? 'bad' : s.blocked || s.dead ? 'ok' : ''),
      line(s.scroll.frames ? `scroll frames: p95 ${s.scroll.p95.toFixed(0)} ms, ${s.scroll.slow}/${s.scroll.frames} over 32 ms` : 'scroll frames: scroll to measure', 'muted'),
    );
  }
  setInterval(() => { if (!quietMode) renderOverlay(); }, 250);

  // ───────────── go ─────────────

  if (poolOn) { pool = new WorkerPool(poolSize); void pool.prewarm().then(renderOverlay); }
  void build().then(() => { syncUrl(); renderOverlay(); });

  const api: FeedApi = {
    setTheme, setGestures, setRenderer, setPool, setHostile, setCorpus, setCount, reset, clear, autoScroll, stop, quiet, setKeep, scroller,
    get summary() { return summary(); },
    get bubbles() { return bubbles; },
    get pool() { return pool; },
    get records() { return records; },
    get settings() { return { corpus, renderer, poolOn, poolSize, hostile, count, lanes, ahead, keep, theme }; },
    get viewport() { return { width: scroller.clientWidth, height: scroller.clientHeight }; },
    bench: null as unknown as BenchApi,
  };
  api.bench = startBench(benchPanel, api, {
    renderers: RENDERERS.map((r) => r.kind),
    params,
    onOpen: () => { benchPanel.classList.remove('hidden'); benchBtn.classList.add('on'); },
  });
  return api;
}
