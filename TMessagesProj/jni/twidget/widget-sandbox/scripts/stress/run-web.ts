// Reference runner of the stress kit (packages/stress) on the web renderers: every widget of the manifest
// through the web dev app (apps/web), the real pipeline (QuickJS worker → validator → renderer), in headless
// Chromium; iframe and shadow (Shadow DOM, the web product renderer); the frozen blitz and litehtml prototypes
// only when named in --renderers and their wasm is built. Per widget: load it, warm up, then measure for `measureMs`:
//   - client frames: requestAnimationFrame timestamps in the frame that hosts the renderer (the page for
//     shadow, the sandboxed iframe for iframe), plus long tasks there: what the user sees;
//   - renderer work per frame: the wasm renderers' paint time; null for iframe and shadow (a WebView-like
//     renderer cannot time its style and paint from script, CLIENTS.md);
//   - the host: JS CPU, layout queries, QuickJS heap from `onStats`, nodes, validated ops per second and the
//     largest validated batch from the renderer's `send`;
//   - the widget's own data-stress (its rAF rate as the sandbox sees it).
// It writes one run file per renderer (packages/stress/run.schema.json) to reports/stress/<pass>-<renderer>.json;
// packages/stress/tools/report.mjs turns run files (any client's) into the summary.
//
// Needs the dev server of this checkout:  corepack pnpm dev --port 5178
//   corepack pnpm tsx scripts/stress/run-web.ts --origin http://127.0.0.1:5178 [--renderers iframe,shadow]
//     [--only shadow-moving] [--warmup 2000] [--measure 10000] [--pass measure|production] [--width 390]
//     [--theme light] [--device "MacBook Pro (M5 Max)"]
// `--pass measure` (default) is CLIENTS.md's measuring configuration (compat, the quotas out of the way);
// `--pass production` is the product's (strict, the default limits) and records where each ladder stops.

import { execSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { chromium, type Frame, type Page } from '@playwright/test';
import { DEFAULT_RUN, MEASURE_PARAMS, ROOT, STRESS, loadWidget, openDevApp, published, readManifest, type Published } from './lib';

const args = process.argv.slice(2);
const opt = (name: string, def?: string) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : def);
const ORIGIN = opt('origin', `http://127.0.0.1:${process.env.TW_PORT ?? 5178}`)!;
const RENDERERS = opt('renderers', 'iframe,shadow')!.split(',');
const ONLY = opt('only');
const WARMUP = Number(opt('warmup', String(DEFAULT_RUN.warmupMs)));
const MEASURE = Number(opt('measure', String(DEFAULT_RUN.measureMs)));
const PASS = opt('pass', 'measure') as 'measure' | 'production';
const WIDTH = Number(opt('width', String(DEFAULT_RUN.width)));
const THEME = opt('theme', 'light')!;
const DEVICE = opt('device', `${os.cpus()[0]?.model ?? 'unknown CPU'} (${os.cpus().length} cores)`)!;
const DPR = 2;
const OUT = path.join(ROOT, 'reports', 'stress');

const commit = () => { try { return execSync('git rev-parse --short HEAD', { cwd: ROOT }).toString().trim(); } catch { return ''; } };
const ownRenderer = (r: string) => r === 'blitz' || r === 'litehtml';
const pct = (a: number[], p: number) => (a.length ? [...a].sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor(p * a.length))] : null);
const r1 = (x: number | null) => (x === null || !Number.isFinite(x) ? null : Math.round(x * 10) / 10);

/** rAF timestamps and long tasks in a frame; each call starts a new window; the renderer's paint times ride along. A string: tsx would wrap named functions in a helper the page does not have. */
const SAMPLER = `(() => {
  const w = window;
  if (!w.__sf) {
    const s = (w.__sf = { t: [], long: 0, paints: [], lastPaints: -1 });
    const f = (t) => {
      s.t.push(t);
      const r = w.__tw && (w.__tw.blitz || w.__tw.litehtml);
      if (r && r.paints !== s.lastPaints) { if (s.lastPaints >= 0) s.paints.push(r.paintMs); s.lastPaints = r.paints; }
      requestAnimationFrame(f);
    };
    requestAnimationFrame(f);
    try { new PerformanceObserver((l) => { for (const e of l.getEntries()) s.long += e.duration; }).observe({ type: 'longtask' }); } catch (e) {}
  }
  w.__sf.t = []; w.__sf.long = 0; w.__sf.paints = [];
})()`;
const SAMPLES = '({ t: window.__sf.t, long: window.__sf.long, paints: window.__sf.paints })';

/** The frame the renderer draws in: the sandboxed iframe for the iframe renderer, the page otherwise. */
async function rendererFrame(page: Page): Promise<Frame> {
  const el = await page.$('iframe[data-stress-el], [data-stress-el] iframe');
  return (await el?.contentFrame()) ?? page.mainFrame();
}

function frameStats(t: number[], vsync: number) {
  const dts: number[] = [];
  for (let i = 1; i < t.length; i++) dts.push(t[i] - t[i - 1]);
  let slots = 0, missed = 0;
  for (const d of dts) { const n = Math.max(1, Math.round(d / vsync)); slots += n; missed += n - 1; }
  return { count: dts.length, p50: r1(pct(dts, 0.5)), p95: r1(pct(dts, 0.95)), max: r1(dts.length ? Math.max(...dts) : null), dropped: slots ? Math.round((missed / slots) * 1000) / 1000 : null };
}

async function hostSnapshot(page: Page) {
  return page.evaluate(() => {
    const st = (window as any).__tw.state, x = (window as any).__stress;
    return { stats: st.stats ? { ...st.stats } : null, ops: x.ops as number, maxBatch: x.maxBatch as number, dead: st.dead as string | null, loaded: !!st.loaded };
  });
}

async function runWidget(page: Page, w: { id: string; file: string; load: string; value: number }, renderer: string, vsync: number) {
  const html = fs.readFileSync(path.join(STRESS, w.file), 'utf8');
  const t0 = Date.now();
  await loadWidget(page, html, `stress/${w.id}.html`, WIDTH);
  await page.waitForFunction(() => { const s = (window as any).__tw.state; return s.loaded || s.dead; }, null, { timeout: 60_000, polling: 'raf' }).catch(() => {});
  const bootMs = Date.now() - t0;
  const frame = await rendererFrame(page);
  await frame.evaluate(SAMPLER).catch(() => {});
  await page.waitForTimeout(WARMUP);
  const a = await hostSnapshot(page), pa = await published(page);
  await frame.evaluate(SAMPLER).catch(() => {});
  const m0 = Date.now();
  let maxGap = 0;
  let diedAtMs: number | null = a.dead ? 0 : null;
  while (Date.now() - m0 < MEASURE) {
    await page.waitForTimeout(Math.min(500, MEASURE - (Date.now() - m0)));
    const p = await published(page);
    if (p && p.maxGap > maxGap) maxGap = p.maxGap;
    if (diedAtMs === null && (await page.evaluate(() => !!(window as any).__tw.state.dead))) diedAtMs = Date.now() - t0;
  }
  const secs = (Date.now() - m0) / 1000;
  const samples = (await frame.evaluate(SAMPLES).catch(() => ({ t: [], long: 0, paints: [] }))) as { t: number[]; long: number; paints: number[] };
  const b = await hostSnapshot(page), pb = await published(page);
  const state = await page.evaluate(() => { const st = (window as any).__tw.state; return { logs: st.logs as { level: string; text: string }[], diags: Object.fromEntries(Object.entries(st.diags as Record<string, { count: number }>).map(([k, v]) => [k, v.count])) }; });
  const rate = (k: string) => (a.stats && b.stats ? r1((b.stats[k] - a.stats[k]) / secs) : null);
  const widgetFps = (pa: Published | null, pb: Published | null) => (pa && pb && pb.t > pa.t ? r1(((pb.frames - pa.frames) * 1000) / (pb.t - pa.t)) : null);
  return {
    id: w.id, load: w.load, value: w.value,
    loaded: a.loaded, bootMs, dead: b.dead, diedAtMs: b.dead ? (diedAtMs ?? bootMs) : null,
    frames: { ...frameStats(samples.t, vsync), longTaskMs: r1(samples.long) },
    renderer: ownRenderer(renderer) ? { msPerFrame: r1(samples.paints.length ? samples.paints.reduce((s, x) => s + x, 0) / samples.paints.length : null), p95Ms: r1(pct(samples.paints, 0.95)) } : null,
    host: {
      jsMsPerSec: rate('jsMs'), opsPerSec: r1((b.ops - a.ops) / secs), queriesPerSec: rate('queries'), queryMsPerSec: rate('queryMs'),
      nodes: b.stats?.nodes ?? null, memoryBytes: b.stats?.memoryBytes ?? null, throttledTasks: a.stats && b.stats ? b.stats.throttledTasks - a.stats.throttledTasks : null, maxBatchOps: b.maxBatch,
    },
    widget: { fps: widgetFps(pa, pb), frames: pb && pa ? pb.frames - pa.frames : null, maxGap, jank: pa && pb ? pb.jank - pa.jank : null, last: pb },
    thermal: null,
    errors: state.logs.filter((l) => l.level === 'error').map((l) => String(l.text).split('\n')[0].slice(0, 200)),
    diags: state.diags,
  };
}

async function main() {
  const manifest = readManifest();
  const widgets = manifest.widgets.filter((w) => !ONLY || ONLY.split(',').some((o) => w.id.startsWith(o)));
  const params: Record<string, string> = PASS === 'measure' ? MEASURE_PARAMS : { policy: 'strict' };
  const browser = await chromium.launch();
  fs.mkdirSync(OUT, { recursive: true });
  for (const renderer of RENDERERS) {
    const ctx = await browser.newContext({ viewport: { width: 1200, height: 1000 }, deviceScaleFactor: DPR });
    const page = await ctx.newPage();
    await openDevApp(page, ORIGIN, renderer, { ...params, theme: THEME });
    // The display's frame period, from the idle page.
    await page.evaluate(SAMPLER);
    await page.waitForTimeout(1500);
    const idle = ((await page.evaluate(SAMPLES)) as { t: number[] }).t;
    const dts = idle.slice(1).map((t, i) => t - idle[i]);
    const vsync = pct(dts, 0.5) ?? 1000 / 60;
    const refreshHz = Math.round(1000 / vsync);
    console.log(`${renderer}: ${widgets.length} widgets, ${PASS} pass, ${refreshHz} Hz, warm-up ${WARMUP} ms, measure ${MEASURE} ms`);
    const runs = [];
    let applied: Record<string, unknown> = {};
    for (const w of widgets) {
      try {
        const run = await runWidget(page, w, renderer, vsync);
        if (!Object.keys(applied).length) applied = await page.evaluate(() => { const o = (window as any).__tw.host?.opts ?? {}; const k = ['policy', 'cpuLimitMs', 'cpuQuotaMs', 'cpuQuotaWindowMs', 'mainThreadQuotaMs', 'maxTimers', 'memoryLimitBytes']; return Object.fromEntries(k.map((n) => [n, o[n] ?? null])); });
        runs.push(run);
        const f = run.frames;
        console.log(`  ${w.id.padEnd(28)} p50 ${String(f.p50).padStart(5)} p95 ${String(f.p95).padStart(5)} drop ${String(f.dropped).padStart(5)} | widget ${String(run.widget.fps).padStart(5)} fps | js ${String(run.host.jsMsPerSec).padStart(6)} ms/s ops ${String(run.host.opsPerSec).padStart(7)}/s${run.renderer ? ` | render ${run.renderer.msPerFrame} ms` : ''}${run.dead ? ' | DEAD ' + run.dead.slice(0, 90) : ''}`);
      } catch (e) {
        console.log(`  ${w.id}: runner error ${(e as Error).message.split('\n')[0]}`);
        await openDevApp(page, ORIGIN, renderer, { ...params, theme: THEME });
      }
    }
    const file = {
      kit: { version: manifest.version, commit: commit() },
      platform: 'web', client: 'widget-sandbox dev app (apps/web)', clientBuild: commit(), framework: commit(),
      renderer, rendererKind: ownRenderer(renderer) ? 'own' : 'webview',
      engine: `Chromium ${browser.version()} (headless, Playwright)`,
      device: { model: DEVICE, os: `${os.type()} ${os.release()}`, class: 'desktop', refreshHz, dpr: DPR },
      pass: PASS,
      config: { requested: { ...Object.fromEntries(Object.entries(params).map(([k, v]) => [k, k === 'policy' ? v : Number(v)])), width: WIDTH, theme: THEME, warmupMs: WARMUP, measureMs: MEASURE, features: ['webAnimations'] }, applied },
      date: new Date().toISOString(),
      runs,
    };
    fs.writeFileSync(path.join(OUT, `${PASS}-${renderer}.json`), JSON.stringify(file, null, 1));
    console.log(`  → ${path.relative(ROOT, path.join(OUT, `${PASS}-${renderer}.json`))}`);
    await ctx.close();
  }
  await browser.close();
}

main().catch((e) => { console.error(e); process.exit(1); });
