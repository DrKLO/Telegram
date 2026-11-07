// Feed bench driver (dev tooling, docs/shadow-dom-renderer.md): writes the
// validated ops of a few generated widgets to bench/ops/ (Node harness), then
// opens bench/feed.html in Chromium for every renderer × count and prints a
// table; with --out it also writes the Markdown report.
//   corepack pnpm tsx scripts/feed-bench.ts [--counts 50,200] [--mode ops|host] [--cv] [--pool 8[,16]] [--out docs/feed-bench-web.md]
// --pool N: host mode also with a pool of N warmed sandbox workers (prewarmed before the clock, refilled as taken).
// Needs the dev server (corepack pnpm dev) on 127.0.0.1:5173.

import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runWidget } from '@telegram/widget-sandbox/node';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const argv = process.argv.slice(2);
const opt = (name: string, def: string) => { const i = argv.indexOf(name); return i >= 0 && argv[i + 1] ? argv[i + 1] : def; };
const counts = opt('--counts', '50,200').split(',').map(Number);
const modes = opt('--mode', 'ops').split(',');
const cvs = argv.includes('--cv') ? [false, true] : [false];
const pools = [0, ...opt('--pool', '').split(',').filter(Boolean).map(Number)];
const out = opt('--out', '');
const origin = opt('--origin', 'http://127.0.0.1:5173');
const WIDGETS = [
  'generated/with-profile/01-show-me-a-mortgage-calculator-loan-amount',
  'generated/with-profile/06-show-a-comparison-table-of-three-laptops',
  'generated/with-profile/11-make-a-to-do-list-widget-where-i-can-add',
  'generated/with-profile/19-make-a-step-by-step-recipe-card-for',
  'generated/without-profile/02-make-an-interactive-quiz-about-the-solar',
];

const opsDir = path.join(root, 'bench', 'ops');
fs.mkdirSync(opsDir, { recursive: true });
// One ops file per widget and renderer: the validator scopes CSS differently for the shadow renderer.
for (const w of WIDGETS) for (const scope of ['document', 'shadow'] as const) {
  const file = path.join(opsDir, w.replace(/\//g, '__') + (scope === 'shadow' ? '.shadow' : '') + '.json');
  if (fs.existsSync(file)) continue;
  const r = await runWidget(fs.readFileSync(path.join(root, 'corpus', w + '.html'), 'utf8'), { verbose: false, probes: [], cssScope: scope });
  fs.writeFileSync(file, JSON.stringify([{ type: 'ops', ops: r.renderOps }]));
  console.error(`ops: ${w} (${scope}) → ${r.renderOps.length} ops`);
}

const browser = await chromium.launch();
const rows: any[] = [];
for (const mode of modes) for (const count of counts) for (const cv of cvs) for (const pool of mode === 'host' ? pools : [0]) for (const renderer of ['iframe', 'shadow']) {
  const page = await browser.newPage({ viewport: { width: 900, height: 900 } });
  const url = `${origin}/bench/feed.html?renderer=${renderer}&mode=${mode}&count=${count}${cv ? '&cv=1' : ''}${pool ? `&pool=${pool}` : ''}`;
  await page.goto(url);
  await page.waitForFunction(() => typeof (window as any).__feed?.start === 'function');
  // Chromium's own counters for the whole page (frames included): JS heap, documents, DOM nodes, layout / style time.
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Performance.enable');
  const metrics = async () => Object.fromEntries(((await cdp.send('Performance.getMetrics')).metrics as { name: string; value: number }[]).map((m) => [m.name, m.value]));
  const before = await metrics();
  await page.evaluate(() => (window as any).__feed.start());
  await page.waitForFunction(() => (window as any).__feed?.done, null, { timeout: 600_000 });
  const after = await metrics();
  const r = await page.evaluate(() => (window as any).__feed);
  if (r.error) { console.error(`${renderer} ${mode} ${count}: ${r.error}`); await page.close(); continue; }
  const d = (k: string) => (after[k] ?? 0) - (before[k] ?? 0);
  const x = { ...r.result, jsHeap: d('JSHeapUsedSize'), documents: d('Documents'), frames: d('Frames'), nodes: d('Nodes'), layoutMs: d('LayoutDuration') * 1000, styleMs: d('RecalcStyleDuration') * 1000, taskMs: d('TaskDuration') * 1000 };
  rows.push(x);
  console.log(`${renderer.padEnd(7)} ${mode.padEnd(5)} ${String(count).padStart(4)}${cv ? ' cv' : '   '}${pool ? ` pool ${String(pool).padStart(2)} (prewarm ${x.prewarmMs.toFixed(0)} ms, hits ${x.poolStats.hits}/${count})` : ''.padEnd(0)}  build ${x.build.toFixed(0).padStart(5)} ms  ready ${x.ready.toFixed(0).padStart(6)} ms  painted ${x.painted.toFixed(0).padStart(6)} ms  ${x.perBubble.toFixed(1).padStart(6)} ms/bubble  long ${String(x.longTasks.count).padStart(3)} (${x.longTasks.total.toFixed(0).padStart(5)} ms)  heap +${(x.jsHeap / 1048576).toFixed(1)} MB  docs +${x.documents} frames +${x.frames} nodes +${x.nodes}  layout ${x.layoutMs.toFixed(0)} ms style ${x.styleMs.toFixed(0)} ms task ${x.taskMs.toFixed(0)} ms  scroll avg ${x.scroll.avg.toFixed(1)} p95 ${x.scroll.p95.toFixed(1)} slow ${x.scroll.slow}/${x.scroll.frames}  height ${x.feedHeight}`);
  await page.close();
}
await browser.close();

if (out && rows.length) {
  const mb = (b: number | null) => (b === null ? 'n/a' : (b / 1048576).toFixed(1));
  const lines = [
    '# Feed bench — iframe vs Shadow DOM renderer on the web', '',
    `Generated ${new Date().toISOString()} by \`corepack pnpm tsx scripts/feed-bench.ts --counts ${counts.join(',')} --mode ${modes.join(',')}${cvs.length > 1 ? ' --cv' : ''}\` (headless Chromium ${browser.version()}, dev server, 900×900). ` +
    'ops = renderer only, replaying the validated ops of five generated widgets (bench/ops/, Node harness) round-robin; host = a WidgetHost per bubble (QuickJS worker + validator + renderer), four booting at a time. ' +
    'build = creating the renderers and sending the ops (main thread, synchronous); ready = every bubble applied its ops; painted = the frame after that; ' +
    'long tasks = main-thread tasks over 50 ms until painted; heap / documents / frames / nodes / layout / style / task = Chromium `Performance.getMetrics` deltas for the whole page, iframes included (JS heap of the renderer process, documents and frames created, DOM nodes, time in layout, style recalc and all tasks); ' +
    'scroll = 60 scroll steps through the feed, frame intervals. Bubble heights are clamped to 24–900 px like in WidgetHost. ' +
    'Pool = host mode with a WorkerPool of that many warmed sandbox workers (QuickJS + bundle evaluated), prewarmed before the clock starts and refilled as taken; hits = bubbles that got a warm worker.', '',
    '| Renderer | Mode | Bubbles | CV | Pool (prewarm ms, hits) | Build ms | Ready ms | Painted ms | ms / bubble | Long tasks | JS heap MB | Documents | Frames | Nodes | Layout ms | Style ms | Task ms | Scroll avg / p95 ms | Slow frames | Feed height |',
    '|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|',
  ];
  for (const x of rows) lines.push(`| ${x.renderer} | ${x.mode} | ${x.count} | ${x.cv ? 'yes' : ''} | ${x.pool ? `${x.pool} (${x.prewarmMs.toFixed(0)} ms, ${x.poolStats.hits}/${x.count})` : ''} | ${x.build.toFixed(0)} | ${x.ready.toFixed(0)} | ${x.painted.toFixed(0)} | ${x.perBubble.toFixed(1)} | ${x.longTasks.count} (${x.longTasks.total.toFixed(0)} ms) | ${mb(x.jsHeap)} | ${x.documents} | ${x.frames} | ${x.nodes} | ${x.layoutMs.toFixed(0)} | ${x.styleMs.toFixed(0)} | ${x.taskMs.toFixed(0)} | ${x.scroll.avg.toFixed(1)} / ${x.scroll.p95.toFixed(1)} | ${x.scroll.slow} / ${x.scroll.frames} | ${x.feedHeight} |`);
  fs.writeFileSync(path.join(root, out), lines.join('\n') + '\n');
  console.error(`→ ${out}`);
}
