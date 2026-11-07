// The feed's measurement layer (dev tooling, `?feed=1` → the **bench** button /
// `?bench=1`; the web twin of the iOS feed-report.json, docs/chat-feed-ios.md).
// It runs the same feed — corpus/common, the same bubble count, the same
// scripted `step` scroll — N passes per renderer × pool setting and records
// what happened: every boot (feed.ts's `BootRecord`: creation → content,
// visible-without-content time, renderer creation → ready, host constructor
// time), the main thread during the scroll (PerformanceObserver long tasks and
// a requestAnimationFrame loop for frame intervals), and memory samples
// (`performance.memory` every second; `measureUserAgentSpecificMemory` at the
// baseline and the end of each pass where the browser exposes it). Nothing is
// typed in: the two tables, the overlay text and the JSON report are rendered
// from the same `FeedReport` object, median / p90 in every cell.
//
// Method (what makes the numbers comparable):
//   - one browser tab, one feed, every combination in the same session, back to back;
//   - a pass = fresh pool (prewarmed before the feed opens, like a messenger at
//     launch) → `reset` → the first screen settles → the scripted scroll down
//     and back → stragglers settle → the pass is read out;
//   - cold = the first boot of a pass; warm = every other boot of the pass;
//   - per-bubble rows: median / p90 over the pass's bubbles, then the median of
//     those over the N passes; per-pass rows: median / p90 over the N passes;
//   - the demo's own periodic work (overlay refresh, memory and canary polls)
//     is paused while a pass runs.
// Trusted glue only; nothing here touches the sandbox, the validator or a renderer.

import type { BootRecord, FeedApi, FeedCorpus, FeedRenderer } from './feed';

export interface MemorySample {
  /** performance.now() */
  t: number;
  /** Bytes, or null when no source is available. */
  total: number | null;
  workers: number | null;
  frames: number | null;
  source: 'measureUserAgentSpecificMemory' | 'performance.memory' | 'performance.memory+gc' | 'none';
  /** Why the precise API was not used (its error), when it exists but refuses. */
  note?: string;
  /** Resident memory of every browser process (bytes), read by the Playwright driver through `__twProcessMemory`; null in a plain tab. */
  process: number | null;
}

/**
 * Reads what the browser offers: the precise API (workers and frames in the
 * breakdown) or the page's JS heap. `gc` runs `window.gc()` first where it is
 * exposed (`--js-flags=--expose-gc`, the Playwright driver): performance.memory
 * counts garbage otherwise.
 */
export async function measureMemory(opts: { precise?: boolean; timeoutMs?: number; gc?: boolean } = {}): Promise<MemorySample> {
  const p = performance as any;
  const t = performance.now();
  const gc = (globalThis as any).gc;
  if (opts.gc && typeof gc === 'function') gc();
  // scripts/feed-report.ts exposes it: the RSS of every Chromium process (workers, frames, GPU, the lot), the number a device feels.
  const procFn = (globalThis as any).__twProcessMemory;
  const process: number | null = typeof procFn === 'function' ? await procFn().catch(() => null) : null;
  let note: string | undefined;
  if (opts.precise !== false && typeof p.measureUserAgentSpecificMemory === 'function') {
    try {
      // The page is cross-origin isolated (Vite sends COOP/COEP): workers and the srcdoc frames are in the breakdown.
      // The browser delays the answer by a random interval; the caller bounds the wait.
      const r = await Promise.race([
        p.measureUserAgentSpecificMemory() as Promise<{ bytes: number; breakdown: { bytes: number; attribution: { scope?: string; container?: unknown }[] }[] }>,
        new Promise<null>((res) => setTimeout(() => res(null), opts.timeoutMs ?? 20_000)),
      ]);
      if (r) {
        let workers = 0, frames = 0;
        for (const e of r.breakdown) {
          if (e.attribution.some((a) => a.scope?.includes('Worker'))) workers += e.bytes;
          else if (e.attribution.some((a) => a.container)) frames += e.bytes;
        }
        return { t, total: r.bytes, workers, frames, source: 'measureUserAgentSpecificMemory', process };
      }
      note = 'measureUserAgentSpecificMemory: no answer within the wait';
    } catch (e) { note = 'measureUserAgentSpecificMemory: ' + String(e).split('\n')[0]; }
  }
  if (p.memory) return { t, total: p.memory.usedJSHeapSize as number, workers: null, frames: null, source: opts.gc && typeof gc === 'function' ? 'performance.memory+gc' : 'performance.memory', note, process };
  return { t, total: null, workers: null, frames: null, source: 'none', note, process };
}

export interface Combo { renderer: FeedRenderer; pool: boolean }
export interface BenchOptions {
  passes: number;
  count: number;
  corpus: FeedCorpus;
  combos: Combo[];
  /** Fill the pool before the feed opens (a messenger prewarms at launch); off = the pool fills while the first screen boots. */
  prewarm: boolean;
  /** Seconds the first screen gets before the scroll starts, and the settle time after it. */
  settleMs: number;
}

export interface PassResult {
  pass: number;
  seconds: number;
  records: BootRecord[];
  /** Boots that got no content in this pass and were not killed (scrolled past, dropped while booting). */
  incomplete: number;
  longTasks: { count: number; totalMs: number; maxMs: number; scrollSeconds: number };
  /** Milliseconds the document was hidden during the scroll (a background tab: frames and timers throttled, the pass is not comparable). */
  hiddenMs: number;
  frames: { count: number; expectedMs: number; dropped: number; maxMs: number; p95Ms: number; over32: number };
  memory: {
    baseline: MemorySample;
    end: MemorySample;
    liveAtEnd: number;
    /**
     * The ramp: after the scroll, the drop distance is lifted and the feed is scrolled through once more so every bubble
     * boots and stays live; `pre` is read before it (the keep window live), `all` with every bubble live. Per bubble =
     * (all − pre) ÷ (liveAll − livePre): a large signal against the browser's drift. `heapsAll` = Σ live QuickJS heaps then.
     */
    ramp: { pre: MemorySample; all: MemorySample; livePre: number; liveAll: number; heapsAll: number; seconds: number } | null;
    /** Once a second while the pass runs: `performance.memory`, the browser's process memory (driver only), the live host count and the sum of the live QuickJS heaps. */
    samples: { t: number; total: number | null; process: number | null; live: number; heaps: number }[];
  };
  pool: { size: number; created: number; hits: number; misses: number } | null;
}

/** A cell: median / p90, how many samples, and the unit. `null` = not measured in this run. */
export interface Cell { median: number; p90: number; n: number }

export interface ComboResult extends Combo {
  key: string;
  passes: PassResult[];
  cells: Record<string, Cell | null>;
  notes: string[];
}

export interface FeedReport {
  kind: 'twidget-feed-report-web';
  date: string;
  userAgent: string;
  platform: { cores: number; dpr: number; window: { width: number; height: number }; scroller: { width: number; height: number }; theme: string };
  config: {
    corpus: FeedCorpus; count: number; passes: number; prewarm: boolean; settleMs: number;
    scroll: { kind: 'step'; screenFraction: number; pauseMs: number };
    lanes: number; ahead: number; keep: number; poolSize: number; hostile: string;
  };
  rows: RowDef[];
  combos: ComboResult[];
  notMeasured: string[];
  /** Set when the run was interrupted. */
  aborted?: string;
  totalSeconds: number;
}

/** What one table row is: which claim it belongs to, how it is read from a pass, how it is shown. */
export interface RowDef {
  key: string;
  group: 'start' | 'scale' | 'native';
  label: string;
  unit: 'ms' | 'MB' | 'n' | 'ms/s';
  /** 'bubble': per-bubble values within a pass (median / p90 over bubbles, the median of those across passes); 'pass': one value per pass (median / p90 across passes). */
  kind: 'bubble' | 'pass';
  title: string;
}

export const GROUPS: Record<RowDef['group'], { label: string; claim: string }> = {
  start: { label: 'starts instantly', claim: 'the pool: a bubble shows content as it scrolls in, not after its QuickJS boots' },
  scale: { label: 'cheap at scale', claim: 'the renderer: what one more live bubble costs the page' },
  native: { label: 'feels native under the finger', claim: 'the main thread during the scripted scroll' },
};

export const ROWS: RowDef[] = [
  { key: 'ttfpCold', group: 'start', label: 'time to first content, cold (first bubble of a pass)', unit: 'ms', kind: 'pass', title: 'host creation → the first size report with content, for the first boot of each pass (one sample per pass: median / p90 across passes)' },
  { key: 'ttfpWarm', group: 'start', label: 'time to first content, warm', unit: 'ms', kind: 'bubble', title: 'host creation → first content for every boot after the first one of the pass' },
  { key: 'blank', group: 'start', label: 'blank while visible', unit: 'ms', kind: 'bubble', title: 'how long a bubble sat inside the viewport without content, per boot (0 = content was there before the reader got to it); boots that never got content count their whole visible time' },
  { key: 'blankCount', group: 'start', label: 'bubbles blank > 100 ms', unit: 'n', kind: 'pass', title: 'boots whose visible-without-content time exceeded 100 ms, per pass' },
  { key: 'rendererReady', group: 'start', label: 'renderer create → first report', unit: 'ms', kind: 'bubble', title: 'the renderer alone: its construction → its first message to the host (iframe: the srcdoc document load + renderer bundle, then the first size report; shadow: the first size report, synchronous; canvas: `ready` after the wasm instance, cached after the first)' },
  { key: 'hostCreate', group: 'start', label: 'host constructor (main thread)', unit: 'ms', kind: 'bubble', title: 'the synchronous cost of `new WidgetHost` on the main thread: renderer element, validator, worker handoff' },
  { key: 'memProcess', group: 'scale', label: 'memory per live bubble, browser processes', unit: 'MB', kind: 'pass', title: 'the memory ramp at the end of each pass: the drop distance is lifted and the feed is scrolled through once more so every bubble boots and stays live; (resident memory of every Chromium process with all bubbles live − with the keep window live) ÷ the bubbles added. Read by the Playwright driver (scripts/feed-report.ts); n/a in a plain tab' },
  { key: 'memPage', group: 'scale', label: 'memory per live bubble, page JS heap', unit: 'MB', kind: 'pass', title: 'the same ramp on performance.memory (the main thread\'s V8 heap: not the workers, not an isolated frame\'s heap), after gc() where the browser exposes it; n/a without performance.memory' },
  { key: 'qjsPerBubble', group: 'scale', label: 'QuickJS heap per live bubble', unit: 'MB', kind: 'pass', title: 'the engine\'s own counter (memory_used_size) summed over the live widgets ÷ live widgets, at the ramp\'s reading with every bubble live' },
  { key: 'rampLive', group: 'scale', label: 'live bubbles at the memory reading', unit: 'n', kind: 'pass', title: 'hosts alive at the ramp\'s reading (every bubble of the feed when the ramp completed)' },
  { key: 'longTotal', group: 'native', label: 'long tasks, total', unit: 'ms', kind: 'pass', title: 'PerformanceObserver longtask (≥ 50 ms) on the main thread during the scripted scroll, summed per pass' },
  { key: 'longRate', group: 'native', label: 'long-task time per second of scroll', unit: 'ms/s', kind: 'pass', title: 'the total above ÷ the seconds the scripted scroll took (comparable to the iOS hitch rate in spirit, not in definition)' },
  { key: 'longMax', group: 'native', label: 'longest task', unit: 'ms', kind: 'pass', title: 'the longest single long task during the scroll' },
  { key: 'frameMax', group: 'native', label: 'longest frame', unit: 'ms', kind: 'pass', title: 'the longest interval between two requestAnimationFrame callbacks during the scroll' },
  { key: 'dropped', group: 'native', label: 'dropped frames (estimate)', unit: 'n', kind: 'pass', title: 'Σ round(interval ÷ expected) − 1 over the frames of the scroll; expected = the 20th-percentile interval of the pass (the display\'s refresh)' },
];

export const NOT_MEASURED = [
  'memory: performance.memory is the main thread\'s JS heap only (no workers, no isolated frame); measureUserAgentSpecificMemory is refused by this Chromium (the note under the table says so when it is); the process row is resident memory (shared pages counted once per process) over a ramp of ~1 minute: the browser\'s own drift over that minute is in it, the pool\'s warm workers are not',
  'the pool\'s prewarm itself (N worker boots before the feed opens) is outside every clock',
  'cold cache: wasm modules and the dev server\'s files are fetched once per page load, so only the first pass of a canvas renderer pays the fetch + compile',
  'a background tab: Chromium throttles timers and frames there; run with the tab in front',
  'CPU frequency, thermal state, other tabs and processes; the dev build (Vite, unminified)',
  'the iOS report\'s hitch rate: long tasks start at 50 ms, so shorter main-thread stalls show only in the frame rows',
];

const quantile = (xs: number[], q: number) => {
  if (!xs.length) return NaN;
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.floor(s.length * q))];
};
const median = (xs: number[]) => quantile(xs, 0.5);
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
const MB = 1048576;

// ───────────── one pass → cell values ─────────────

/** Per-bubble values of a pass for a 'bubble' row; one value for a 'pass' row (NaN = not available in this pass). */
function passValues(row: RowDef, p: PassResult): number[] | number {
  const ok = p.records.filter((r) => r.contentMs !== null && !r.dead);
  switch (row.key) {
    case 'ttfpCold': { const c = ok.find((r) => r.cold); return c ? c.contentMs! : NaN; }
    case 'ttfpWarm': return ok.filter((r) => !r.cold).map((r) => r.contentMs!);
    case 'blank': return p.records.filter((r) => r.shownAt && !r.dead).map((r) => r.blankMs);
    case 'blankCount': return p.records.filter((r) => r.shownAt && !r.dead && r.blankMs > 100).length;
    case 'rendererReady': return p.records.filter((r) => r.rendererReadyMs !== null).map((r) => r.rendererReadyMs!);
    case 'hostCreate': return p.records.map((r) => r.hostCreateMs);
    case 'memProcess': {
      const r = p.memory.ramp;
      if (!r || r.pre.process === null || r.all.process === null || r.liveAll - r.livePre < 5) return NaN;
      return (r.all.process - r.pre.process) / (r.liveAll - r.livePre) / MB;
    }
    case 'memPage': {
      const r = p.memory.ramp;
      if (!r || r.pre.total === null || r.all.total === null || r.pre.source !== r.all.source || r.liveAll - r.livePre < 5) return NaN;
      return (r.all.total - r.pre.total) / (r.liveAll - r.livePre) / MB;
    }
    case 'qjsPerBubble': {
      const r = p.memory.ramp;
      return r && r.liveAll > 0 && r.heapsAll > 0 ? r.heapsAll / r.liveAll / MB : NaN;
    }
    case 'rampLive': return p.memory.ramp ? p.memory.ramp.liveAll : NaN;
    case 'longTotal': return p.longTasks.totalMs;
    case 'longRate': return p.longTasks.scrollSeconds > 0 ? p.longTasks.totalMs / p.longTasks.scrollSeconds : NaN;
    case 'longMax': return p.longTasks.count ? p.longTasks.maxMs : 0;
    case 'frameMax': return p.frames.count ? p.frames.maxMs : NaN;
    case 'dropped': return p.frames.count ? p.frames.dropped : NaN;
  }
  return NaN;
}

/** The cell for a row over a combination's passes (null when no pass had the value). */
export function cell(row: RowDef, passes: PassResult[]): Cell | null {
  if (row.kind === 'bubble') {
    const meds: number[] = [], p90s: number[] = [];
    let n = 0;
    for (const p of passes) {
      const xs = passValues(row, p) as number[];
      if (!xs.length) continue;
      meds.push(median(xs)); p90s.push(quantile(xs, 0.9)); n += xs.length;
    }
    return meds.length ? { median: median(meds), p90: median(p90s), n } : null;
  }
  const xs = passes.map((p) => passValues(row, p) as number).filter((v) => Number.isFinite(v));
  return xs.length ? { median: median(xs), p90: quantile(xs, 0.9), n: xs.length } : null;
}

export function formatValue(v: number, unit: RowDef['unit']): string {
  if (!Number.isFinite(v)) return 'n/a';
  switch (unit) {
    case 'ms': return Math.abs(v) < 10 && v !== 0 ? v.toFixed(1) : v.toFixed(0);
    case 'ms/s': return v.toFixed(1);
    case 'MB': return v.toFixed(1);
    case 'n': return v.toFixed(0);
  }
}
export function formatCell(c: Cell | null, unit: RowDef['unit']): string {
  if (!c) return 'n/a';
  const u = unit === 'n' ? '' : ` ${unit}`;
  return `${formatValue(c.median, unit)} / ${formatValue(c.p90, unit)}${u}`;
}

// ───────────── tables (DOM and Markdown from the same report) ─────────────

export interface TableSpec { title: string; columns: { label: string; combo: ComboResult }[] }

/** The two tables: (1) every renderer with the pool on; (2) pool off vs on for each renderer that ran both. */
export function tables(report: FeedReport): TableSpec[] {
  const out: TableSpec[] = [];
  const poolOn = report.combos.filter((c) => c.pool);
  if (poolOn.length) out.push({ title: 'Renderers, pool on', columns: poolOn.map((c) => ({ label: c.renderer, combo: c })) });
  const both: TableSpec['columns'] = [];
  for (const r of [...new Set(report.combos.map((c) => c.renderer))]) {
    const off = report.combos.find((c) => c.renderer === r && !c.pool);
    const on = report.combos.find((c) => c.renderer === r && c.pool);
    if (off && on) both.push({ label: `${r}, pool off`, combo: off }, { label: `${r}, pool on`, combo: on });
  }
  if (both.length) out.push({ title: 'Pool off vs on', columns: both });
  return out;
}

export function toMarkdown(report: FeedReport): string {
  const lines: string[] = [];
  const c = report.config, p = report.platform;
  lines.push(`# Web feed report (${report.date.slice(0, 10)})`, '');
  lines.push(`corpus/${c.corpus}, ${c.count} bubbles, ${c.passes} passes per cell, \`step\` scroll (${Math.round(c.scroll.screenFraction * 100)} % of a screen, pause ${c.scroll.pauseMs} ms, down and back), pool ×${c.poolSize}${c.prewarm ? ' prewarmed before the feed opens' : ''}, lanes ${c.lanes}, prefetch ${c.ahead} screens, keep ${c.keep} screens, hostile bubble: ${c.hostile}. Scroller ${p.scroller.width}×${p.scroller.height} css px, dpr ${p.dpr}, ${p.cores} cores, ${p.theme} theme. ${report.userAgent}. Total ${Math.round(report.totalSeconds)} s.`, '');
  lines.push('Cells are median / p90. Per-bubble rows (warm time to content, blank, renderer ready, host constructor): over the bubbles of a pass, then the median across passes. Per-pass rows: across passes.', '');
  for (const t of tables(report)) {
    lines.push(`## ${t.title}`, '');
    lines.push(`| | ${t.columns.map((col) => col.label).join(' | ')} |`);
    lines.push(`|---|${t.columns.map(() => '---:').join('|')}|`);
    let group: RowDef['group'] | null = null;
    for (const row of report.rows) {
      if (row.group !== group) { group = row.group; lines.push(`| **${GROUPS[group].label}** — ${GROUPS[group].claim} |${t.columns.map(() => ' ').join('|')}|`); }
      lines.push(`| ${row.label} | ${t.columns.map((col) => formatCell(col.combo.cells[row.key], row.unit)).join(' | ')} |`);
    }
    lines.push('');
    const notes = [...new Set(t.columns.flatMap((col) => col.combo.notes))];
    if (notes.length) lines.push(...notes.map((n) => `- ${n}`), '');
  }
  lines.push('Not measured: ' + report.notMeasured.join('; ') + '.', '');
  if (report.aborted) lines.push(`Run interrupted: ${report.aborted}`, '');
  return lines.join('\n');
}

// ───────────── the run ─────────────

export interface BenchApi {
  run(opts?: Partial<BenchOptions>): Promise<FeedReport>;
  stop(): void;
  readonly running: boolean;
  /** The last (or current, while running) report. */
  readonly report: FeedReport | null;
  markdown(): string;
  json(): string;
  download(): void;
  readonly options: BenchOptions;
}

export interface BenchHostOptions {
  renderers: FeedRenderer[];
  params: URLSearchParams;
  /** Show the panel (the `?bench=1` autorun opens it). */
  onOpen: () => void;
}

// The product renderer (shadow) and the reference (iframe); the frozen canvas prototypes only through `combos`.
const DEFAULT_COMBOS: Combo[] = [
  { renderer: 'iframe', pool: true }, { renderer: 'shadow', pool: true },
  { renderer: 'iframe', pool: false }, { renderer: 'shadow', pool: false },
];

function parseCombos(text: string | null, renderers: FeedRenderer[]): Combo[] | null {
  if (!text) return null;
  const out: Combo[] = [];
  for (const part of text.split(',')) {
    const [r, p] = part.split(':');
    if (!renderers.includes(r as FeedRenderer)) continue;
    out.push({ renderer: r as FeedRenderer, pool: p !== 'off' && p !== '0' });
  }
  return out.length ? out : null;
}

export function startBench(panel: HTMLElement, feed: FeedApi, host: BenchHostOptions): BenchApi {
  const params = host.params;
  const options: BenchOptions = {
    passes: Math.max(1, Number(params.get('passes') ?? 5)),
    count: Math.min(200, Math.max(1, Number(params.get('benchCount') ?? 60))),
    corpus: (params.get('benchCorpus') === 'showcase' ? 'showcase' : 'common'),
    combos: parseCombos(params.get('combos'), host.renderers) ?? DEFAULT_COMBOS,
    prewarm: params.get('prewarm') !== '0',
    settleMs: Math.max(0, Number(params.get('settle') ?? 1500)),
  };
  let report: FeedReport | null = null;
  let running = false;
  let abort: string | null = null;

  // ───────────── panel ─────────────

  const el = (tag: string, cls = '', text = '') => { const e = document.createElement(tag); if (cls) e.className = cls; if (text) e.textContent = text; return e; };
  const form = el('div', 'bench-form');
  const passesInput = document.createElement('input');
  passesInput.type = 'number'; passesInput.min = '1'; passesInput.max = '20'; passesInput.value = String(options.passes);
  passesInput.title = 'passes per renderer × pool cell; every cell is median / p90 over them';
  const countInput = document.createElement('input');
  countInput.type = 'number'; countInput.min = '4'; countInput.max = '200'; countInput.value = String(options.count);
  countInput.title = 'widget bubbles per pass (a text bubble before every other one), the same for every cell';
  const comboBoxes: { combo: Combo; input: HTMLInputElement }[] = [];
  const comboGrid = el('div', 'bench-combos');
  comboGrid.append(el('span', 'muted', ''), el('span', 'muted', 'pool on'), el('span', 'muted', 'pool off'));
  for (const r of host.renderers) {
    comboGrid.append(el('span', '', r));
    for (const pool of [true, false]) {
      const label = el('label');
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.checked = options.combos.some((c) => c.renderer === r && c.pool === pool);
      label.appendChild(input);
      comboGrid.appendChild(label);
      comboBoxes.push({ combo: { renderer: r, pool }, input });
    }
  }
  const prewarmLabel = el('label');
  const prewarmInput = document.createElement('input');
  prewarmInput.type = 'checkbox'; prewarmInput.checked = options.prewarm;
  prewarmLabel.title = 'fill the pool before the feed opens, as a messenger does at launch; off: the pool fills while the first screen boots';
  prewarmLabel.append(prewarmInput, ' prewarm pool before the feed opens');
  const runBtn = el('button', '', '▶ run') as HTMLButtonElement;
  runBtn.type = 'button';
  const dlBtn = el('button', '', '⬇ JSON') as HTMLButtonElement;
  dlBtn.type = 'button'; dlBtn.disabled = true;
  dlBtn.title = 'feed-report-web.json: the same object the tables are rendered from (every boot, every long task total, every memory sample)';
  const mdBtn = el('button', '', '⎘ Markdown') as HTMLButtonElement;
  mdBtn.type = 'button'; mdBtn.disabled = true;
  mdBtn.title = 'copy the two tables as Markdown';
  const row1 = el('div', 'bench-row');
  row1.append(el('span', '', 'passes '), passesInput, el('span', '', ' bubbles '), countInput, el('span', 'muted', ` corpus/${options.corpus}, step scroll`));
  const row2 = el('div', 'bench-row');
  row2.append(runBtn, dlBtn, mdBtn);
  form.append(row1, comboGrid, prewarmLabel, row2);
  const progress = el('div', 'bench-progress muted', 'A pass: fresh pool (prewarmed) → reset → first screen settles → scripted scroll down and back → read out. Cells: median / p90.');
  const results = el('div', 'bench-results');
  panel.replaceChildren(el('div', 'bench-title', 'Measurements'), form, progress, results);

  runBtn.addEventListener('click', () => { if (running) stop(); else void run(); });
  dlBtn.addEventListener('click', download);
  mdBtn.addEventListener('click', () => { void navigator.clipboard?.writeText(markdown()).then(() => setProgress('Markdown copied.')); });

  function readForm(): Partial<BenchOptions> {
    return {
      passes: Math.max(1, Number(passesInput.value) || 5),
      count: Math.min(200, Math.max(4, Number(countInput.value) || 60)),
      combos: comboBoxes.filter((c) => c.input.checked).map((c) => c.combo),
      prewarm: prewarmInput.checked,
    };
  }
  function setProgress(text: string, cls = 'muted') { progress.textContent = text; progress.className = 'bench-progress ' + cls; }

  // ───────────── collectors ─────────────

  /** Long tasks on the main thread and rAF intervals for one phase (the scripted scroll). */
  function phaseCollector() {
    let longCount = 0, longTotal = 0, longMax = 0;
    let observer: PerformanceObserver | null = null;
    try {
      observer = new PerformanceObserver((list) => {
        for (const e of list.getEntries()) { longCount++; longTotal += e.duration; longMax = Math.max(longMax, e.duration); }
      });
      observer.observe({ type: 'longtask', buffered: false });
    } catch { observer = null; }
    const deltas: number[] = [];
    let hiddenMs = 0, hiddenSince = document.hidden ? performance.now() : 0;
    const onVis = () => { if (document.hidden) hiddenSince = performance.now(); else if (hiddenSince) { hiddenMs += performance.now() - hiddenSince; hiddenSince = 0; } };
    document.addEventListener('visibilitychange', onVis);
    let last = 0, rafOn = true;
    const tick = (t: number) => {
      if (!rafOn) return;
      if (last) deltas.push(t - last);
      last = t;
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
    const started = performance.now();
    return {
      stop(): { longTasks: PassResult['longTasks']; frames: PassResult['frames']; hiddenMs: number } {
        rafOn = false;
        document.removeEventListener('visibilitychange', onVis);
        if (hiddenSince) hiddenMs += performance.now() - hiddenSince;
        const seconds = (performance.now() - started) / 1000;
        if (observer) { for (const e of observer.takeRecords()) { longCount++; longTotal += e.duration; longMax = Math.max(longMax, e.duration); } observer.disconnect(); }
        // The display's refresh interval: the 20th percentile of the intervals (the quiet frames), snapped to 60 / 90 / 120 Hz.
        const q20 = quantile(deltas, 0.2);
        const hz = [60, 90, 120, 144].reduce((best, h) => (Math.abs(1000 / h - q20) < Math.abs(1000 / best - q20) ? h : best), 60);
        const expected = 1000 / hz;
        let dropped = 0;
        for (const d of deltas) dropped += Math.max(0, Math.round(d / expected) - 1);
        return {
          longTasks: { count: observer ? longCount : NaN, totalMs: observer ? longTotal : NaN, maxMs: observer ? longMax : NaN, scrollSeconds: seconds },
          frames: { count: deltas.length, expectedMs: expected, dropped, maxMs: deltas.length ? Math.max(...deltas) : NaN, p95Ms: quantile(deltas, 0.95), over32: deltas.filter((d) => d > 32).length },
          hiddenMs,
        };
      },
    };
  }

  const liveCount = () => feed.bubbles.filter((b) => b.host).length;
  const liveHeaps = () => feed.bubbles.reduce((a, b) => a + (b.host ? b.heap : 0), 0);

  // ───────────── the run ─────────────

  async function run(partial: Partial<BenchOptions> = {}): Promise<FeedReport> {
    if (running) return report!;
    Object.assign(options, readForm(), partial);
    if (!options.combos.length) { setProgress('pick at least one renderer × pool cell', 'bad'); return report!; }
    running = true;
    abort = null;
    runBtn.textContent = '■ stop';
    dlBtn.disabled = mdBtn.disabled = true;
    const before = feed.settings;
    const runStart = performance.now();
    const s = feed.settings;
    report = {
      kind: 'twidget-feed-report-web',
      date: new Date().toISOString(),
      userAgent: navigator.userAgent,
      platform: { cores: navigator.hardwareConcurrency, dpr: devicePixelRatio, window: { width: innerWidth, height: innerHeight }, scroller: feed.viewport, theme: s.theme },
      config: {
        corpus: options.corpus, count: options.count, passes: options.passes, prewarm: options.prewarm, settleMs: options.settleMs,
        scroll: { kind: 'step', screenFraction: 0.9, pauseMs: 900 },
        lanes: s.lanes, ahead: s.ahead, keep: s.keep, poolSize: s.poolSize, hostile: 'none',
      },
      rows: ROWS,
      combos: [],
      notMeasured: NOT_MEASURED,
      totalSeconds: 0,
    };
    feed.quiet(true);
    try {
      feed.clear();
      feed.setCorpus(options.corpus, false);
      feed.setHostile('none', false);
      feed.setCount(options.count, false);
      for (const combo of options.combos) {
        if (abort) break;
        const result: ComboResult = { ...combo, key: `${combo.renderer}:${combo.pool ? 'on' : 'off'}`, passes: [], cells: {}, notes: [] };
        report.combos.push(result);
        feed.setRenderer(combo.renderer);
        for (let pass = 1; pass <= options.passes && !abort; pass++) {
          setProgress(`${combo.renderer}, pool ${combo.pool ? 'on' : 'off'}: pass ${pass} / ${options.passes} …`, '');
          const p = await runPass(combo, pass);
          if (!p) break;
          result.passes.push(p);
          result.cells = Object.fromEntries(ROWS.map((r) => [r.key, cell(r, result.passes)]));
          result.notes = notesFor(result);
          report.totalSeconds = (performance.now() - runStart) / 1000;
          renderResults();
        }
      }
    } catch (e) {
      abort = 'error: ' + String(e).split('\n')[0];
    } finally {
      report.totalSeconds = (performance.now() - runStart) / 1000;
      if (abort) report.aborted = abort;
      running = false;
      runBtn.textContent = '▶ run';
      dlBtn.disabled = mdBtn.disabled = !report.combos.some((c) => c.passes.length);
      feed.quiet(false);
      // Back to what the reader had before the run (one rebuild).
      feed.setCorpus(before.corpus, false);
      feed.setHostile(before.hostile, false);
      feed.setCount(before.count, false);
      feed.setRenderer(before.renderer);
      feed.setPool(before.poolOn);
      void feed.reset();
      renderResults();
      setProgress(abort ? `stopped: ${abort}` : `done in ${Math.round(report.totalSeconds)} s · ${report.combos.length} cells × ${options.passes} passes · ⬇ JSON for the report`, abort ? 'bad' : 'ok');
    }
    return report;
  }

  /** One pass: fresh pool → reset → settle → scripted scroll → settle → read out. Null when stopped midway. */
  async function runPass(combo: Combo, pass: number): Promise<PassResult | null> {
    const t0 = performance.now();
    feed.clear();
    feed.setPool(false);
    if (combo.pool) {
      feed.setPool(true);
      if (options.prewarm) await feed.pool!.prewarm();
    }
    if (abort) return null;
    // Baseline: the empty feed, the pool warm — what a chat costs before any bubble (a second for the previous pass's processes and workers to go).
    await sleep(1000);
    const baseline = await measureMemory({ timeoutMs: 15_000, gc: true });
    const samples: PassResult['memory']['samples'] = [];
    const procFn = (globalThis as any).__twProcessMemory;
    let sampling = false;
    const sampler = setInterval(async () => {
      if (sampling) return;
      sampling = true;
      const m = (performance as any).memory;
      const live = liveCount(), heaps = liveHeaps(), t = performance.now();
      // The process reading is the driver's (CDP + ps, off this thread); the live count is taken with it, before the await.
      const process: number | null = typeof procFn === 'function' ? await procFn().catch(() => null) : null;
      samples.push({ t, total: m ? (m.usedJSHeapSize as number) : null, process, live, heaps });
      sampling = false;
    }, 1000);
    try {
      await feed.reset();
      await sleep(options.settleMs);
      if (abort) return null;
      const collector = phaseCollector();
      await feed.autoScroll();
      const phase = collector.stop();
      if (abort) return null;
      await sleep(options.settleMs);
      // The end reading: the live hosts of the top of the feed (the keep window) against the baseline, the same source as the baseline.
      const end = await measureMemory({ precise: baseline.source === 'measureUserAgentSpecificMemory', timeoutMs: 15_000, gc: true });
      const liveAtEnd = liveCount();
      const records = feed.records.map((r) => ({ ...r }));
      const ramp = await memoryRamp();
      return {
        pass,
        seconds: (performance.now() - t0) / 1000,
        records,
        incomplete: records.filter((r) => r.contentMs === null && !r.dead).length,
        longTasks: phase.longTasks,
        frames: phase.frames,
        hiddenMs: phase.hiddenMs,
        memory: { baseline, end, liveAtEnd, samples, ramp },
        pool: feed.pool ? { size: feed.pool.size, ...feed.pool.stats } : null,
      };
    } finally {
      clearInterval(sampler);
    }
  }

  /**
   * The memory ramp: lift the drop distance, scroll through so every bubble boots and stays, read memory with all of them
   * live against the reading just before (the keep window). The clocks and the boot records of the pass are closed by now.
   */
  async function memoryRamp(): Promise<PassResult['memory']['ramp']> {
    const t0 = performance.now();
    const pre = await measureMemory({ precise: false, gc: true });
    const livePre = liveCount();
    const keep = feed.settings.keep;
    feed.setKeep(1000);
    try {
      const sc = feed.scroller;
      const total = feed.bubbles.length;
      for (let i = 0; i < 400 && !abort; i++) {
        const atBottom = sc.scrollTop + sc.clientHeight >= sc.scrollHeight - 2;
        if (atBottom && liveCount() >= total) break;
        if (!atBottom) sc.scrollBy({ top: sc.clientHeight * 0.9, behavior: 'instant' as ScrollBehavior });
        await sleep(atBottom ? 250 : 150);
      }
      if (abort) return null;
      await sleep(options.settleMs);
      const all = await measureMemory({ precise: false, gc: true });
      return { pre, all, livePre, liveAll: liveCount(), heapsAll: liveHeaps(), seconds: (performance.now() - t0) / 1000 };
    } finally {
      feed.setKeep(keep);
    }
  }

  function notesFor(c: ComboResult): string[] {
    const notes: string[] = [];
    const sources = new Set(c.passes.map((p) => p.memory.end.source));
    const procs = c.passes.some((p) => p.memory.end.process !== null);
    notes.push(procs
      ? 'memory per live bubble, browser processes: the ramp\'s delta in the RSS of every Chromium process (renderer, the isolated frames, GPU, utility; read by the driver) ÷ the bubbles that went live in it; the QuickJS workers and the renderer are both in it'
      : 'memory per live bubble, browser processes: n/a — only the Playwright driver (scripts/feed-report.ts) can read process memory');
    const ramps = c.passes.map((p) => p.memory.ramp).filter((r): r is NonNullable<typeof r> => !!r);
    if (ramps.length && ramps.some((r) => r.liveAll < options.count)) notes.push(`${c.renderer}, pool ${c.pool ? 'on' : 'off'}: the ramp ended with ${Math.min(...ramps.map((r) => r.liveAll))}–${Math.max(...ramps.map((r) => r.liveAll))} of ${options.count} bubbles live`);
    if (sources.has('none')) notes.push('page JS heap: n/a — no performance.memory in this browser');
    else if (sources.has('performance.memory')) notes.push('page JS heap: performance.memory with garbage included (no gc() in this browser); the QuickJS workers\' wasm memory and an isolated iframe\'s heap are not in it');
    else if (sources.has('performance.memory+gc')) notes.push('page JS heap: performance.memory after a forced gc(); the QuickJS workers\' wasm memory and an isolated iframe\'s heap are not in it');
    else notes.push('page JS heap row from measureUserAgentSpecificMemory: workers, frames and the page');
    const precise = c.passes.map((p) => p.memory.baseline.note).find(Boolean);
    if (precise) notes.push(precise);
    const incomplete = c.passes.reduce((a, p) => a + p.incomplete, 0);
    if (incomplete) notes.push(`${c.renderer}, pool ${c.pool ? 'on' : 'off'}: ${incomplete} boots over ${c.passes.length} passes got no content (scrolled past or dropped while booting); they count in the blank row, not in time to content`);
    const dead = c.passes.reduce((a, p) => a + p.records.filter((r) => r.dead).length, 0);
    if (dead) notes.push(`${c.renderer}, pool ${c.pool ? 'on' : 'off'}: ${dead} widgets stopped by the runtime limits`);
    if (c.passes.some((p) => Number.isNaN(p.longTasks.totalMs))) notes.push('long tasks: PerformanceObserver longtask is not available in this browser');
    const hidden = c.passes.filter((p) => p.hiddenMs > 0);
    if (hidden.length) notes.push(`${c.renderer}, pool ${c.pool ? 'on' : 'off'}: the tab was hidden during ${hidden.length} of ${c.passes.length} scrolls (${Math.round(Math.max(...hidden.map((p) => p.hiddenMs)) / 1000)} s at most): frames and timers were throttled there, the frame and blank rows of this cell are not comparable`);
    const misses = c.passes.reduce((a, p) => a + (p.pool?.misses ?? 0), 0);
    if (c.pool && misses) notes.push(`${c.renderer}, pool on: the pool was empty ${misses} times over ${c.passes.length} passes (a fresh worker booted instead)`);
    return notes;
  }

  function stop() { if (running) abort = 'stopped by the reader'; feed.stop(); }

  // ───────────── output ─────────────

  function renderResults() {
    results.replaceChildren();
    if (!report) return;
    for (const t of tables(report)) {
      results.appendChild(el('h3', '', t.title));
      const table = document.createElement('table');
      table.className = 'bench-table';
      const thead = document.createElement('tr');
      thead.appendChild(el('th', '', ''));
      for (const col of t.columns) {
        const th = el('th', '', col.label);
        th.title = `${col.combo.passes.length} / ${options.passes} passes`;
        if (col.combo.passes.length < options.passes) th.textContent += ` (${col.combo.passes.length}/${options.passes})`;
        thead.appendChild(th);
      }
      table.appendChild(thead);
      let group: RowDef['group'] | null = null;
      for (const row of report.rows) {
        if (row.group !== group) {
          group = row.group;
          const tr = document.createElement('tr');
          tr.className = 'bench-group';
          const td = el('td', '', GROUPS[group].label) as HTMLTableCellElement;
          td.colSpan = t.columns.length + 1;
          td.title = GROUPS[group].claim;
          const claim = el('span', 'muted', ` — ${GROUPS[group].claim}`);
          td.appendChild(claim);
          tr.appendChild(td);
          table.appendChild(tr);
        }
        const tr = document.createElement('tr');
        const label = el('td', '', row.label);
        label.title = row.title;
        tr.appendChild(label);
        for (const col of t.columns) {
          const c = col.combo.cells[row.key];
          const td = el('td', c ? '' : 'muted', formatCell(c, row.unit));
          if (c) td.title = `${c.n} samples · ${row.kind === 'bubble' ? 'median / p90 over bubbles, the median across passes' : 'median / p90 across passes'}`;
          tr.appendChild(td);
        }
        table.appendChild(tr);
      }
      results.appendChild(table);
      const notes = [...new Set(t.columns.flatMap((col) => col.combo.notes))];
      for (const n of notes) results.appendChild(el('div', 'bench-note muted', n));
    }
    if (report.combos.some((c) => c.passes.length)) {
      const c = report.config, p = report.platform;
      results.appendChild(el('div', 'bench-note', `Method: corpus/${c.corpus}, ${c.count} bubbles, ${c.passes} passes per cell, step scroll (90 % of a screen, pause 900 ms, down and back), pool ×${c.poolSize}${c.prewarm ? ' prewarmed before the feed opens' : ''}, lanes ${c.lanes}, prefetch ${c.ahead} screens, keep ${c.keep}; scroller ${p.scroller.width}×${p.scroller.height}, dpr ${p.dpr}, ${p.cores} cores; ${report.date.slice(0, 16).replace('T', ' ')}.`));
      results.appendChild(el('div', 'bench-note muted', 'Not measured: ' + report.notMeasured.join('; ') + '.'));
    }
  }

  function json() { return JSON.stringify(report, (_k, v) => (typeof v === 'number' ? (Number.isFinite(v) ? Math.round(v * 100) / 100 : null) : v), 2); }
  function markdown() { return report ? toMarkdown(report) : ''; }
  function download() {
    if (!report) return;
    const blob = new Blob([json()], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `feed-report-web-${report.date.slice(0, 19).replace(/[:T]/g, '-')}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  // `?bench=1`: open the panel and run with the URL's options (the Playwright driver scripts/feed-report.ts uses __tw.feed.bench.run instead).
  if (params.get('bench') === '1') { host.onOpen(); setTimeout(() => void run(), 500); }
  else if (params.get('bench') === 'open') host.onOpen();

  return {
    run, stop, markdown, json, download,
    get running() { return running; },
    get report() { return report; },
    get options() { return options; },
  };
}
