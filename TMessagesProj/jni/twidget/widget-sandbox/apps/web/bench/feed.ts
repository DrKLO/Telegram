// Feed bench (dev tooling, docs/shadow-dom-renderer.md): N chat bubbles with the
// sandboxed iframe renderer vs the Shadow DOM renderer on the same validated
// ops. Two modes:
//   ?mode=ops  — renderer only: replays pre-validated `ops` batches
//                (scripts/feed-bench.ts writes them to bench/ops/ with the Node
//                harness); measures build time, time to all rendered, main-thread
//                long tasks, memory (measureUserAgentSpecificMemory: the page is
//                cross-origin isolated) and scroll frame times.
//   ?mode=host — the whole WidgetHost per bubble (QuickJS worker + validator +
//                renderer), `parallel` at a time; the sandbox boot dominates.
// ?renderer=iframe|shadow &count=N &cv=1 (content-visibility: auto on bubbles)
// &widgets=a,b (ops files / corpus files) &auto=1 (start on load; otherwise
// the driver calls window.__feed.start() after taking its CDP baseline).
// Results in window.__feed and the HUD. Bubble heights are clamped to the
// WidgetHost range (24–900 px): a `min-height: 100vh` body is a feedback loop
// on both renderers, the clamp is where it stops in the product too.

import { IframeRenderer } from '@telegram/widget-sandbox/host/iframe-renderer';
import type { Renderer } from '@telegram/widget-sandbox/host/renderer';
import { ShadowDomRenderer } from '@telegram/widget-sandbox/host/shadow-renderer';
import { WidgetHost } from '@telegram/widget-sandbox/host/widget-host';
import { WorkerPool } from '@telegram/widget-sandbox/host/worker-pool';
import type { HostToRenderer, RendererToHost } from '@telegram/widget-sandbox/shared/protocol';

const params = new URLSearchParams(location.search);
const RENDERER = params.get('renderer') === 'shadow' ? 'shadow' : 'iframe';
const MODE = params.get('mode') === 'host' ? 'host' : 'ops';
const COUNT = Math.max(1, Number(params.get('count') ?? 100));
const CV = params.get('cv') === '1';
const PARALLEL = Math.max(1, Number(params.get('parallel') ?? 4));
/** host mode: take sandbox workers from a pool of this many warmed ones (prewarmed before the clock starts, refilled as taken); 0 = boot each. */
const POOL = Math.max(0, Number(params.get('pool') ?? 0));
const MIN_HEIGHT = 24, MAX_HEIGHT = 900; // WidgetHost's clamp
const WIDGETS = (params.get('widgets') ?? 'generated/with-profile/01-show-me-a-mortgage-calculator-loan-amount,generated/with-profile/06-show-a-comparison-table-of-three-laptops,generated/with-profile/11-make-a-to-do-list-widget-where-i-can-add,generated/with-profile/19-make-a-step-by-step-recipe-card-for,generated/without-profile/02-make-an-interactive-quiz-about-the-solar').split(',');

const feed = document.getElementById('feed')!;
const hud = document.getElementById('hud')!;
const factory = RENDERER === 'shadow' ? ShadowDomRenderer.factory : IframeRenderer.factory;

interface Result {
  renderer: string; mode: string; count: number; cv: boolean; pool: number;
  /** host mode with a pool: ms to prewarm it (before the clock), pool counters at the end. */
  prewarmMs?: number; poolStats?: { created: number; hits: number; misses: number };
  /** ms: creating N renderers (+ sending ops) — synchronous work on the main thread. */
  build: number;
  /** ms: until every bubble has applied its ops and reported a size (ops mode) / every host reported loaded (host mode). */
  ready: number;
  /** ms: until the frame after `ready` was painted. */
  painted: number;
  /** Long tasks (> 50 ms) on the main thread between start and painted: count and total ms. */
  longTasks: { count: number; total: number };
  /** Bytes from measureUserAgentSpecificMemory, after - before, or null when unavailable. */
  memory: number | null;
  memoryBefore: number | null;
  /** Scroll through the feed: average and p95 frame interval (ms), frames over 32 ms. */
  scroll: { frames: number; avg: number; p95: number; slow: number };
  /** Total height of the feed content (px): a sanity check that both renderers laid out the same widgets. */
  feedHeight: number;
  /** Main-thread CPU per bubble is not observable; wall-clock per bubble = ready / count. */
  perBubble: number;
}

const memory = async (): Promise<number | null> => {
  const p = performance as any;
  if (!p.measureUserAgentSpecificMemory) return null;
  try { return (await p.measureUserAgentSpecificMemory()).bytes as number; } catch { return null; }
};
const nextFrame = () => new Promise<number>((r) => requestAnimationFrame((t) => r(t)));
const settle = async () => { await nextFrame(); await nextFrame(); };

function bubble(): HTMLElement {
  const b = document.createElement('div');
  b.className = 'bubble' + (CV ? ' cv' : '');
  const wrap = document.createElement('div');
  wrap.className = 'frame-wrap';
  b.appendChild(wrap);
  feed.appendChild(b);
  return wrap;
}

async function scrollTest(): Promise<Result['scroll']> {
  feed.scrollTop = 0;
  await settle();
  const total = feed.scrollHeight - feed.clientHeight;
  const steps = 60;
  const deltas: number[] = [];
  let last = await nextFrame();
  for (let i = 1; i <= steps; i++) {
    feed.scrollTop = (total * i) / steps;
    const t = await nextFrame();
    deltas.push(t - last);
    last = t;
  }
  const sorted = [...deltas].sort((a, b) => a - b);
  return {
    frames: deltas.length,
    avg: deltas.reduce((a, b) => a + b, 0) / deltas.length,
    p95: sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))],
    slow: deltas.filter((d) => d > 32).length,
  };
}

async function runOps(): Promise<Result> {
  const opsFiles = await Promise.all(WIDGETS.map(async (w) => (await (await fetch(`/bench/ops/${w.replace(/\//g, '__')}${RENDERER === 'shadow' ? '.shadow' : ''}.json`)).json()) as HostToRenderer[]));
  const memoryBefore = await memory();
  const longTasks: PerformanceEntry[] = [];
  const po = new PerformanceObserver((l) => longTasks.push(...l.getEntries()));
  try { po.observe({ type: 'longtask', buffered: false }); } catch {}

  const t0 = performance.now();
  let readyCount = 0;
  let resolveReady!: () => void;
  const allReady = new Promise<void>((r) => (resolveReady = r));
  const renderers: Renderer[] = [];
  for (let i = 0; i < COUNT; i++) {
    const r = factory(bubble(), 'light');
    r.onMessage = (msg: RendererToHost) => {
      if (msg.type === 'size') r.element.style.height = Math.min(Math.max(msg.height, MIN_HEIGHT), MAX_HEIGHT) + 'px';
      // The answer to the sentinel query arrives after the ops were applied (messages are ordered).
      if (msg.type === 'result' && msg.qid === 1 && ++readyCount === COUNT) resolveReady();
    };
    r.load();
    for (const m of opsFiles[i % opsFiles.length]) r.send(m);
    r.send({ type: 'query', qid: 1, query: { q: 'viewport' } });
    renderers.push(r);
  }
  const build = performance.now() - t0;
  await allReady;
  const ready = performance.now() - t0;
  await nextFrame();
  const painted = performance.now() - t0;
  po.disconnect();
  await settle();
  const memoryAfter = await memory();
  const scroll = await scrollTest();
  return {
    renderer: RENDERER, mode: MODE, count: COUNT, cv: CV, pool: 0, build, ready, painted,
    longTasks: { count: longTasks.length, total: longTasks.reduce((a, e) => a + e.duration, 0) },
    memory: memoryBefore !== null && memoryAfter !== null ? memoryAfter - memoryBefore : null, memoryBefore,
    scroll, feedHeight: feed.scrollHeight, perBubble: ready / COUNT,
  };
}

async function runHost(): Promise<Result> {
  const htmls = await Promise.all(WIDGETS.map(async (w) => (await fetch(`/corpus/${w}.html`)).text()));
  let pool: WorkerPool | undefined;
  let prewarmMs: number | undefined;
  if (POOL > 0) {
    pool = new WorkerPool(POOL);
    const tp = performance.now();
    await pool.prewarm();
    prewarmMs = performance.now() - tp;
  }
  const memoryBefore = await memory();
  const longTasks: PerformanceEntry[] = [];
  const po = new PerformanceObserver((l) => longTasks.push(...l.getEntries()));
  try { po.observe({ type: 'longtask', buffered: false }); } catch {}
  const t0 = performance.now();
  const hosts: WidgetHost[] = [];
  let i = 0;
  const one = () => new Promise<void>((resolve) => {
    const idx = i++;
    const h = new WidgetHost(bubble(), htmls[idx % htmls.length], {
      theme: 'light', rendererFactory: factory, workerPool: pool,
      onLoaded: resolve, onDead: () => resolve(),
    });
    hosts.push(h);
  });
  // `parallel` sandboxes boot at a time (more than a few do not fit the boot CPU slice together).
  const lanes: Promise<void>[] = [];
  for (let lane = 0; lane < PARALLEL; lane++) lanes.push((async () => { while (i < COUNT) await one(); })());
  await Promise.all(lanes);
  const ready = performance.now() - t0;
  await nextFrame();
  const painted = performance.now() - t0;
  po.disconnect();
  await settle();
  const memoryAfter = await memory();
  const scroll = await scrollTest();
  return {
    renderer: RENDERER, mode: MODE, count: COUNT, cv: CV, pool: POOL, prewarmMs, poolStats: pool && { ...pool.stats }, build: 0, ready, painted,
    longTasks: { count: longTasks.length, total: longTasks.reduce((a, e) => a + e.duration, 0) },
    memory: memoryBefore !== null && memoryAfter !== null ? memoryAfter - memoryBefore : null, memoryBefore,
    scroll, feedHeight: feed.scrollHeight, perBubble: ready / COUNT,
  };
}

hud.textContent = `feed bench: ${RENDERER}, ${MODE}, ${COUNT} bubbles${CV ? ', content-visibility' : ''}…`;
let started = false;
function start() {
  if (started) return;
  started = true;
  (MODE === 'host' ? runHost() : runOps()).then((result) => {
  (window as any).__feed = { done: true, result, error: null };
  const mb = (b: number | null) => (b === null ? 'n/a' : (b / 1048576).toFixed(1) + ' MB');
  hud.textContent = [
    `${result.renderer} · ${result.mode} · ${result.count} bubbles${result.cv ? ' · content-visibility' : ''}${result.pool ? ` · pool ${result.pool} (prewarm ${result.prewarmMs?.toFixed(0)} ms, hits ${result.poolStats?.hits}, misses ${result.poolStats?.misses})` : ''}`,
    `build ${result.build.toFixed(0)} ms · ready ${result.ready.toFixed(0)} ms · painted ${result.painted.toFixed(0)} ms`,
    `${result.perBubble.toFixed(1)} ms/bubble · long tasks ${result.longTasks.count} (${result.longTasks.total.toFixed(0)} ms)`,
    `memory +${mb(result.memory)} (before ${mb(result.memoryBefore)})`,
    `scroll: avg ${result.scroll.avg.toFixed(1)} ms, p95 ${result.scroll.p95.toFixed(1)} ms, ${result.scroll.slow}/${result.scroll.frames} frames > 32 ms`,
    `feed height ${result.feedHeight} px`,
  ].join('\n');
  }, (e) => {
    (window as any).__feed = { ...(window as any).__feed, done: true, result: null, error: String(e) };
    hud.textContent = 'error: ' + String(e);
  });
}
(window as any).__feed = { done: false, result: null as Result | null, error: null as string | null, start };
if (params.get('auto') === '1') start();
