// Compat harness: every widget in corpus/compat/ is run with a scripted
// interaction in SANDBOX mode and in REFERENCE mode (native, dev tooling), and
// the two screenshots are diffed. Results go to docs/compat-report.md. The
// widgets that need what profile 1 leaves out (libraries, form controls) are
// in corpus/out-of-profile/, outside this suite; their in-profile reworks are
// here (charts drawn by hand, buttons instead of inputs, taps only).

import { expect, test, type FrameLocator, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
// Outside Playwright's test-results/ (which it wipes at every run start); cleared by tests/global-setup.ts.
const outDir = path.join(root, 'reports', 'compat');
fs.mkdirSync(outDir, { recursive: true });

interface Scenario {
  file: string;
  /** Interaction script, run identically in both modes. */
  steps?: (f: FrameLocator, page: Page) => Promise<void>;
  /** Behavioural assertions after the steps (both modes). */
  check?: (f: FrameLocator) => Promise<void>;
  /** Max fraction of differing pixels (default 0.02). Use 1 for animated / time-dependent widgets. */
  tolerance?: number;
  /** Widgets that are expected not to work; they must degrade gracefully instead. */
  unsupported?: boolean;
  /** Keep the mouse where the steps left it (tooltips / hover state are part of the screenshot). */
  hover?: boolean;
  /** Run with the dark theme: host theme for the sandbox, emulated prefers-color-scheme for the reference. */
  theme?: 'dark';
  /** Report name when a file appears in more than one scenario. */
  id?: string;
  note?: string;
}

const scenarios: Scenario[] = [
  { file: 'tabs.html', steps: async (f) => { await f.locator('#tab-2').click(); await f.locator('#panel-2 button').click(); await f.locator('#panel-2 button').click(); await f.locator('#tab-3').click(); await f.locator('#acc-1 .acc-h').click(); },
    check: async (f) => { await expect(f.locator('#panel-3')).toBeVisible(); await expect(f.locator('#acc-1')).toHaveClass(/open/); await expect(f.locator('#count')).toHaveText('2'); } },
  { file: 'fragment.html', steps: async (f) => { for (let i = 0; i < 3; i++) await f.locator('#btn').click(); }, check: async (f) => { await expect(f.locator('#n')).toHaveText('3'); await expect(f.locator('#status')).toHaveText('load fired'); } },
  { file: 'calculator.html', steps: async (f) => { await f.locator('#k7').click(); await f.locator('#kplus').click(); await f.locator('#k3').click(); await f.locator('#keq').click(); }, check: async (f) => expect(f.locator('#display')).toHaveText('10') },
  { file: 'table-sort.html', steps: async (f) => { await f.locator('#h-score').click(); await f.locator('#h-score').click(); await f.locator('#f-berlin').click(); },
    check: async (f) => { await expect(f.locator('#rows tr')).toHaveCount(2); await expect(f.locator('#rows tr').first()).toContainText('Alice'); await expect(f.locator('#count')).toHaveText('2 of 6 rows'); await expect(f.locator('#h-score')).toHaveClass('desc'); } },
  { file: 'quiz-buttons.html', steps: async (f) => { await f.locator('#q1-right').click(); await f.locator('#q2-wrong').click(); await f.locator('#q3-a').click(); await f.locator('#q3-b').click(); await f.locator('#submit').click(); },
    check: async (f) => { await expect(f.locator('#score')).toHaveText('Score: 2 / 3'); await expect(f.locator('#q2')).toHaveClass(/\bwrong\b/); await expect(f.locator('#q3')).toHaveClass(/\bcorrect\b/); await expect(f.locator('#q2-wrong')).toHaveAttribute('aria-pressed', 'true'); } },
  { file: 'svg-chart.html', steps: async (f) => { await f.locator('#bar-3').click(); }, check: async (f) => { await expect(f.locator('#tip')).toContainText('Apr: $71k'); await expect(f.locator('#bar-3')).toHaveClass(/active/); } },
  { file: 'svg-bar-chart.html', steps: async (f) => { await f.locator('#bar-F').click(); }, check: async (f) => { await expect(f.locator('#info')).toHaveText('F = 97'); await expect(f.locator('#bar-F')).toHaveAttribute('fill', '#f59e0b'); } },
  // A tap pins the crosshair (the hover pointer only moves it), so it survives the mouse parking before the screenshot.
  { file: 'svg-line-chart.html', steps: async (f) => { const svg = f.locator('#svg'); const b = await svg.boundingBox(); if (b) await svg.click({ position: { x: b.width * 0.5, y: b.height * 0.5 } }); },
    check: async (f) => { await expect(f.locator('#readout')).toHaveText(/^\d+:00 — [\d.]+°C$/); await expect(f.locator('#svg circle')).toBeVisible(); } },
  { file: 'canvas-line-chart.html', steps: async (f, page) => { await f.locator('#add').click(); const c = f.locator('#c'); const b = await c.boundingBox(); if (b) { await c.click({ position: { x: b.width * 0.45, y: b.height * 0.5 } }); await page.waitForTimeout(300); } },
    check: async (f) => { await expect(f.locator('#n')).toHaveText('8 points'); await expect(f.locator('#readout')).toHaveText(/^\w+ — Visits \d+ · Signups \d+$/); } },
  // The middle of the third category, then the left legend entry (2024) on the canvas: Transport with 2024 hidden.
  { file: 'canvas-bar-chart.html', steps: async (f, page) => { const c = f.locator('#c'); const b = await c.boundingBox(); if (b) { await c.click({ position: { x: b.width * 0.55, y: b.height * 0.6 } }); await c.click({ position: { x: b.width * 0.4, y: 14 } }); await page.waitForTimeout(300); } },
    check: async (f) => expect(f.locator('#readout')).toHaveText('Transport — 2025: $150') },
  // A frame between the toggles: two class changes the sandbox delivers in one frame start no transition (no user taps that fast).
  { file: 'css-animation.html', steps: async (f, page) => { await f.locator('#toggle').click(); await page.waitForTimeout(100); await f.locator('#toggle').click(); }, check: async (f) => expect(f.locator('#events')).toHaveText(/[1-9]\d* transitionend/), tolerance: 1, note: 'spinner frame differs by timing' },
  { file: 'clock.html', steps: async (f, page) => { await page.waitForTimeout(1100); }, check: async (f) => { await expect(f.locator('#time')).toHaveText(/\d\d:\d\d:\d\d/); await expect(f.locator('#ticks')).toHaveText(/[2-9]\d* ticks/); }, tolerance: 1, note: 'time of day differs' },
  { file: 'raf-animation.html', steps: async (f, page) => { await page.waitForTimeout(600); await f.locator('#stop').click(); }, check: async (f) => expect(f.locator('#frames')).toHaveText(/^[1-9]\d+$/), tolerance: 1, note: 'ball position depends on frame timing' },
  { file: 'drag.html', steps: async (f, page) => { const b = await f.locator('#box').boundingBox(); if (!b) return; await page.mouse.move(b.x + 30, b.y + 30); await page.mouse.down(); for (let i = 1; i <= 8; i++) await page.mouse.move(b.x + 30 + i * 15, b.y + 30 + i * 8); await page.mouse.up(); },
    check: async (f) => expect(f.locator('#pos')).toHaveText('x: 140, y: 84') },
  { file: 'notes-storage.html', steps: async (f) => { await f.locator('#add-tests').click(); await f.locator('li .del').first().click(); },
    check: async (f) => { await expect(f.locator('#list li')).toHaveCount(2); await expect(f.locator('#list li').last()).toContainText('Write tests'); await expect(f.locator('#meta')).toContainText('2 notes'); } },
  { file: 'dark-mode.html', steps: async (f) => { await f.locator('#check').click(); }, check: async (f) => { await expect(f.locator('#mode')).toHaveText('light ☀️'); await expect(f.locator('#js')).toContainText('matches = false'); }, tolerance: 0.06, note: 'bridge line differs by design (reference has no TelegramWidget)' },
  { file: 'dark-mode.html', id: 'dark-mode.html (dark theme)', theme: 'dark', steps: async (f) => { await f.locator('#check').click(); }, check: async (f) => { await expect(f.locator('#mode')).toHaveText('dark 🌙'); await expect(f.locator('#js')).toContainText('matches = true'); await expect(f.locator('body')).toHaveCSS('background-color', 'rgb(15, 23, 42)'); }, tolerance: 0.06, note: 'host-owned colour scheme; bridge line differs by design' },
  { file: 'stepper.html', steps: async (f) => { await f.locator('#next').click(); await f.locator('#next').click(); await f.locator('#back').click(); }, check: async (f) => { await expect(f.locator('#panel h3')).toHaveText('Profile'); await expect(f.locator('#fill')).toHaveCSS('width', /px/); } },
  { file: 'three-js.html', unsupported: true, check: async (f) => expect(f.locator('#fallback')).toBeVisible(), tolerance: 1, note: 'expected unsupported: Three.js/WebGL → fallback text' },
  { file: 'webgl.html', unsupported: true, check: async (f) => expect(f.locator('#fallback')).toBeVisible(), tolerance: 1, note: 'expected unsupported: WebGL → 2D fallback' },
];

interface Result { file: string; order: number; sandboxLoaded: boolean; behaviorSandbox: string; behaviorReference: string; diff: number | null; tolerance: number; pass: boolean; unsupported: boolean; diags: string[]; errors: string[]; stats: any; note?: string }
// Results are persisted per test: Playwright restarts the worker after a failure, which would lose in-memory state.
const resultFile = (id: string) => path.join(outDir, id.replace(/[^\w.-]+/g, '_') + '.result.json');

async function waitReady(page: Page) {
  await page.waitForFunction(() => { const s = (window as any).__tw?.state; return s && (s.loaded || s.dead); }, null, { timeout: 60_000 });
  await page.waitForTimeout(400);
}

async function snapshot(page: Page, sel: string, file: string): Promise<PNG> {
  const buf = await page.locator(sel).screenshot({ animations: 'disabled', caret: 'hide' });
  fs.writeFileSync(path.join(outDir, file), buf);
  return PNG.sync.read(buf);
}

function diffImages(a: PNG, b: PNG, file: string): number {
  const w = Math.max(a.width, b.width), h = Math.max(a.height, b.height);
  const pad = (img: PNG) => {
    if (img.width === w && img.height === h) return img;
    const out = new PNG({ width: w, height: h });
    out.data.fill(255);
    PNG.bitblt(img, out, 0, 0, img.width, img.height, 0, 0);
    return out;
  };
  const pa = pad(a), pb = pad(b);
  const out = new PNG({ width: w, height: h });
  const n = pixelmatch(pa.data, pb.data, out.data, w, h, { threshold: 0.15, includeAA: true });
  fs.writeFileSync(path.join(outDir, file), PNG.sync.write(out));
  return n / (w * h);
}

async function runMode(page: Page, s: Scenario, mode: 'sandbox' | 'reference') {
  // Park the mouse so hover state from the previous layout cannot leak into this run.
  await page.mouse.move(2, 2);
  await page.emulateMedia({ colorScheme: s.theme === 'dark' ? 'dark' : 'light' });
  // TW_POOL=N: the sandbox worker comes from a pool of N warmed ones (worker-pool.ts), the `warm` + `start` path instead of `init`.
  const pool = process.env.TW_POOL && mode === 'sandbox' ? `&pool=${process.env.TW_POOL}` : '';
  await page.goto(`/?file=compat/${s.file}${mode === 'reference' ? '&reference=1' : ''}${s.theme === 'dark' ? '&theme=dark' : ''}${pool}`);
  await waitReady(page);
  const sel = mode === 'sandbox' ? '#sandbox-wrap iframe' : '#reference-wrap iframe';
  const frame = page.frameLocator(sel);
  if (mode === 'reference') {
    // The native frame reports its height after load; wait until it is sized and its scripts ran.
    await page.waitForFunction((s) => (document.querySelector(s) as HTMLElement | null)?.getBoundingClientRect().height! > 24, sel, { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(600);
  }
  let behavior = 'ok';
  try {
    await s.steps?.(frame, page);
    await page.waitForTimeout(500);
    // Known-unsupported widgets: the native reference may well support the feature (headless Chromium has WebGL).
    if (mode === 'sandbox' || !s.unsupported) await s.check?.(frame);
  } catch (e) {
    behavior = String((e as Error).message).split('\n')[0].slice(0, 200);
  }
  // Element screenshots can shift the layout under the cursor and fire hover
  // events; park the mouse first unless the scenario is about hover state.
  if (!s.hover) {
    await page.mouse.move(2, 2);
    await page.waitForTimeout(150);
  }
  const png = await snapshot(page, sel, `${(s.id ?? s.file).replace(/[^\w.-]+/g, '_')}.${mode}.png`);
  const state = await page.evaluate(() => { const st = (window as any).__tw.state; return { loaded: st.loaded, dead: st.dead, diags: st.diags, logs: st.logs, stats: st.stats }; });
  return { behavior, png, state };
}

for (const s of scenarios) {
  test(`compat ${s.id ?? s.file}`, async ({ page }) => {
    test.setTimeout(120_000);
    const sb = await runMode(page, s, 'sandbox');
    const ref = await runMode(page, s, 'reference');
    const tolerance = s.tolerance ?? 0.02;
    const diff = diffImages(sb.png, ref.png, `${(s.id ?? s.file).replace(/[^\w.-]+/g, '_')}.diff.png`);
    const errors = sb.state.logs.filter((l: any) => l.level === 'error').map((l: any) => l.text.split('\n')[0]);
    const diags = Object.entries(sb.state.diags as Record<string, { count: number; details: string[] }>)
      .filter(([k]) => k !== 'boot' && k !== 'library')
      .map(([k, v]) => `${k}×${v.count}: ${v.details[0]}`);
    const pass = sb.state.loaded && !sb.state.dead && sb.behavior === 'ok' && diff <= tolerance && (s.unsupported || errors.length === 0);
    const result: Result = { file: s.id ?? s.file, order: scenarios.indexOf(s), sandboxLoaded: !!sb.state.loaded && !sb.state.dead, behaviorSandbox: sb.behavior, behaviorReference: ref.behavior, diff, tolerance, pass, unsupported: !!s.unsupported, diags, errors, stats: sb.state.stats, note: s.note };
    fs.writeFileSync(resultFile(result.file), JSON.stringify(result));
    expect.soft(sb.state.dead, 'sandbox died').toBeNull();
    expect.soft(sb.behavior, 'sandbox behaviour').toBe('ok');
    expect.soft(ref.behavior, 'reference behaviour (harness sanity)').toBe('ok');
    expect.soft(diff, 'pixel diff').toBeLessThanOrEqual(tolerance);
  });
}

test.afterAll(() => {
  const results: Result[] = fs.readdirSync(outDir).filter((f) => f.endsWith('.result.json')).map((f) => JSON.parse(fs.readFileSync(path.join(outDir, f), 'utf8')) as Result).sort((a, b) => a.order - b.order);
  if (!results.length) return;
  const supported = results.filter((r) => !r.unsupported);
  const passed = supported.filter((r) => r.pass).length;
  const pct = Math.round((passed / supported.length) * 100);
  const lines = [
    '# Compat report',
    '',
    `Generated ${new Date().toISOString()} by \`pnpm test:compat\`.`,
    '',
    `**${passed} / ${supported.length} supported widgets pass (${pct}%).** ${results.filter((r) => r.unsupported).length} known-unsupported widgets checked for graceful fallback: ${results.filter((r) => r.unsupported && r.pass).length} ok.`,
    '',
    'Pass = sandbox loaded without JS errors, the scripted interaction produced the expected result, and the screenshot differs from the native reference by at most the tolerance.',
    '',
    '| Widget | Pass | Behaviour (sandbox) | Pixel diff | Tolerance | JS ms | Ops | Sync queries (cache hits) | Diagnostics | Note |',
    '|---|---|---|---|---|---|---|---|---|---|',
    ...results.map((r) => `| ${r.file} | ${r.pass ? '✅' : '❌'} | ${r.behaviorSandbox.replace(/\|/g, '\\|')} | ${(r.diff! * 100).toFixed(2)}% | ${r.tolerance * 100}% | ${r.stats?.jsMs?.toFixed(0) ?? ''} | ${r.stats?.ops ?? ''} | ${r.stats?.queries ?? ''} (${r.stats?.queryCacheHits ?? ''}) | ${[...r.errors.map((e) => 'error: ' + e), ...r.diags].join('<br>').replace(/\|/g, '\\|')} | ${r.note ?? ''} |`),
    '',
    '## Failures and top causes',
    '',
    ...(results.filter((r) => !r.pass).length ? results.filter((r) => !r.pass).map((r) => `- **${r.file}**: ${r.behaviorSandbox !== 'ok' ? 'behaviour — ' + r.behaviorSandbox : ''} ${r.diff! > r.tolerance ? `pixel diff ${(r.diff! * 100).toFixed(2)}% > ${r.tolerance * 100}%` : ''} ${r.errors.length ? 'errors: ' + r.errors.join('; ') : ''}`) : ['- none']),
    '',
    `Screenshots and diffs: \`reports/compat/<widget>.{sandbox,reference,diff}.png\`.`,
    '',
  ];
  // Hand-written analysis lives in docs/compat-notes.md so regeneration keeps it.
  const notes = path.join(root, 'docs', 'compat-notes.md');
  if (fs.existsSync(notes)) lines.push(fs.readFileSync(notes, 'utf8'));
  fs.writeFileSync(path.join(root, 'docs', 'compat-report.md'), lines.join('\n'));
});
