// Compat check for generated widgets (corpus/generated/**, see
// scripts/generate-corpus.ts) and, with CORPUS_GROUP=htmlbubbles-ported, for the
// ported Telegram demos (pnpm test:htmlbubbles). No per-widget script: each widget is loaded in
// sandbox and reference mode, every visible button is clicked once (up to 4),
// and the screenshots are diffed. Writes docs/compat-report-generated.md.
// TW_RENDERER=blitz|litehtml runs the sandbox side on a native wasm renderer instead of
// the iframe (pnpm build:blitz-web first): a canvas, so no buttons are clicked,
// and the report goes to docs/compat-report-<group>-blitz.md — the pixel
// threshold is not a pass criterion there (another rasterizer, see
// docs/blitz-experiment.md), the gallery is. TW_RENDERER=shadow runs it on the
// Shadow DOM renderer (no iframe, docs/shadow-dom-renderer.md): the same engine,
// so clicks and the pixel threshold apply as for the iframe; report
// docs/compat-report-<group>-shadow.md. CORPUS_GROUP=safe-area (pnpm test:safe-area-corpus) draws both sides as a client
// without a caption: the dev app's time control (?time=corner: the reference band and the time over both columns) and,
// for the reference frame, Chromium's own env(safe-area-inset-*) through its safe-area override.

import { expect, test, type FrameLocator, type Locator, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';
import { REFERENCE_TIME_CORNER } from '../packages/core/src/shared/reference.ts';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const GROUP = process.env.CORPUS_GROUP ?? 'generated';
/** 'blitz' | 'litehtml' | 'shadow': the renderer the sandbox side runs on instead of the iframe (wasm: pnpm build:<name>-web first), '' for the iframe. */
const NATIVE = ['blitz', 'litehtml', 'shadow'].includes(process.env.TW_RENDERER ?? '') ? process.env.TW_RENDERER! : '';
/** A canvas renderer: no clicks, pixel diff informational. The shadow renderer is DOM like the iframe. */
const BLITZ = NATIVE === 'blitz' || NATIVE === 'litehtml';
const SHADOW = NATIVE === 'shadow';
const SUFFIX = NATIVE ? `-${NATIVE}` : '';
/** corpus/safe-area: the client's time over the corner (docs/sdk-v1.md §2.17), on both sides. */
const SAFE_AREA = GROUP === 'safe-area';
const genDir = path.join(root, 'corpus', GROUP);
const outDir = path.join(root, 'reports', GROUP + SUFFIX); // outside test-results/, see compat.spec.ts
fs.mkdirSync(outDir, { recursive: true });

const files: string[] = [];
if (fs.existsSync(genDir)) {
  for (const batch of fs.readdirSync(genDir).sort()) {
    const dir = path.join(genDir, batch);
    if (!fs.statSync(dir).isDirectory()) continue;
    for (const f of fs.readdirSync(dir).sort()) if (f.endsWith('.html')) files.push(`${batch}/${f}`);
  }
}

const TOLERANCE = 0.03;

interface Result { file: string; batch: string; loaded: boolean; errors: string[]; diags: string[]; diff: number; clicks: number; pass: boolean; unsupported: string[] }
const resultFile = (file: string) => path.join(outDir, file.replace(/[^\w.-]+/g, '_') + '.result.json');

async function waitReady(page: Page) {
  await page.waitForFunction(() => { const s = (window as any).__tw?.state; return s && (s.loaded || s.dead); }, null, { timeout: 60_000 });
  await page.waitForTimeout(600);
}

async function poke(frame: FrameLocator | Locator, page: Page): Promise<number> {
  const buttons = frame.locator('button:visible, [role=button]:visible, input[type=button]:visible, input[type=submit]:visible');
  const n = Math.min(await buttons.count().catch(() => 0), 4);
  for (let i = 0; i < n; i++) {
    try { await buttons.nth(i).click({ timeout: 2000, force: true }); } catch {}
    await page.waitForTimeout(150);
  }
  return n;
}

async function run(page: Page, file: string, mode: 'sandbox' | 'reference') {
  const blitz = BLITZ && mode === 'sandbox';
  const shadow = SHADOW && mode === 'sandbox';
  if (SAFE_AREA) {
    // The reference frame is the browser's own: its env() gets the band from Chromium (the sandbox's is rewritten to the host's variables).
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Emulation.setSafeAreaInsetsOverride' as any, { insets: { top: 0, right: 0, bottom: REFERENCE_TIME_CORNER.bottom, left: 0 } });
  }
  await page.goto(`/?file=${GROUP}/${file}${mode === 'reference' ? '&reference=1' : ''}${blitz || shadow ? `&${NATIVE}=1` : ''}${SAFE_AREA ? '&time=corner' : ''}`);
  await waitReady(page);
  if (mode === 'reference') await page.waitForTimeout(800);
  if (blitz) {
    // The wasm renderer paints on its own rAF clock; its first raster follows `loaded` closely, the Playwright click cannot see into a canvas.
    await page.waitForFunction((name) => ((window as any).__tw[name]?.paints ?? 0) > 0, NATIVE, { timeout: 30_000 }).catch(() => {});
    await page.waitForTimeout(300);
  }
  const sel = blitz ? '#sandbox-wrap canvas' : shadow ? '#sandbox-wrap .widget-shadow' : mode === 'sandbox' ? '#sandbox-wrap iframe' : '#reference-wrap iframe';
  // Playwright's CSS engine pierces open shadow roots: the host element locator reaches the widget's buttons.
  const clicks = blitz ? 0 : await poke(shadow ? page.locator(sel) : page.frameLocator(sel), page);
  await page.waitForTimeout(500);
  const buf = await page.locator(sel).screenshot({ animations: 'disabled', caret: 'hide' });
  fs.writeFileSync(path.join(outDir, `${file.replace('/', '__')}.${mode}.png`), buf);
  const state = await page.evaluate(() => { const st = (window as any).__tw.state; return { loaded: st.loaded, dead: st.dead, diags: st.diags, logs: st.logs }; });
  return { png: PNG.sync.read(buf), clicks, state };
}

function diffImages(a: PNG, b: PNG, file: string): number {
  const w = Math.max(a.width, b.width), h = Math.max(a.height, b.height);
  const pad = (img: PNG) => { if (img.width === w && img.height === h) return img; const o = new PNG({ width: w, height: h }); o.data.fill(255); PNG.bitblt(img, o, 0, 0, img.width, img.height, 0, 0); return o; };
  const out = new PNG({ width: w, height: h });
  const n = pixelmatch(pad(a).data, pad(b).data, out.data, w, h, { threshold: 0.15, includeAA: true });
  fs.writeFileSync(path.join(outDir, `${file.replace('/', '__')}.diff.png`), PNG.sync.write(out));
  return n / (w * h);
}

test.skip(files.length === 0, `no corpus/${GROUP} — run pnpm generate-corpus (or extract + port the Telegram demos) first`);

for (const file of files) {
  test(`${GROUP} ${file}`, async ({ page }) => {
    test.setTimeout(120_000);
    const sb = await run(page, file, 'sandbox');
    const ref = await run(page, file, 'reference');
    const diff = diffImages(sb.png, ref.png, file);
    const errors = (sb.state.logs as any[]).filter((l) => l.level === 'error').map((l) => String(l.text).split('\n')[0].slice(0, 160));
    const diagEntries = Object.entries(sb.state.diags as Record<string, { count: number; details: string[] }>);
    const unsupported = diagEntries.filter(([k]) => ['unsupported', 'script-skipped', 'network-blocked', 'url-blocked', 'tag-dropped'].includes(k)).map(([k, v]) => `${k}: ${v.details.slice(0, 2).join('; ')}`);
    const pass = !!sb.state.loaded && !sb.state.dead && errors.length === 0 && (BLITZ || diff <= TOLERANCE);
    const result: Result = { file, batch: file.split('/')[0], loaded: !!sb.state.loaded && !sb.state.dead, errors, diags: diagEntries.filter(([k]) => k !== 'boot' && k !== 'library').map(([k, v]) => `${k}×${v.count}`), diff, clicks: sb.clicks, pass, unsupported };
    fs.writeFileSync(resultFile(file), JSON.stringify(result));
    expect.soft(sb.state.dead, 'sandbox died').toBeNull();
    expect.soft(errors, 'sandbox JS errors').toHaveLength(0);
    if (!BLITZ) expect.soft(diff, 'pixel diff').toBeLessThanOrEqual(TOLERANCE);
    else expect.soft(sb.state.diags['renderer'], `${NATIVE} renderer diag`).toBeUndefined();
  });
}

test.afterAll(() => {
  const results: Result[] = fs.readdirSync(outDir).filter((f) => f.endsWith('.result.json')).map((f) => JSON.parse(fs.readFileSync(path.join(outDir, f), 'utf8')) as Result).sort((a, b) => a.file.localeCompare(b.file));
  if (!results.length) return;
  const batches = [...new Set(results.map((r) => r.batch))];
  const cmd = (NATIVE ? `TW_RENDERER=${NATIVE} ` : '') + (GROUP === 'generated' ? 'pnpm test:generated' : `CORPUS_GROUP=${GROUP} pnpm test:generated`);
  const lines = [`# Compat report — ${GROUP === 'generated' ? 'generated widgets' : GROUP}${BLITZ ? ` on the ${NATIVE} wasm renderer` : SHADOW ? ' on the Shadow DOM renderer (no iframe)' : ''}`, '',
    BLITZ ? `Generated ${new Date().toISOString()} by \`${cmd}\`. Pass = loaded without JS errors on the ${NATIVE} renderer (no clicks: a canvas). The pixel diff against the native reference is informational — another rasterizer, see docs/blitz-experiment.md and docs/litehtml-renderer.md; judge by reports/${GROUP}${SUFFIX}/.`
          : `Generated ${new Date().toISOString()} by \`${cmd}\`. Pass = loaded without JS errors and screenshot within ${TOLERANCE * 100}% of the native reference after clicking up to 4 buttons.`, '',
    ...(SAFE_AREA ? [`Both sides are drawn as a client without a caption draws them: a ${REFERENCE_TIME_CORNER.bottom} px band with the time over a ${REFERENCE_TIME_CORNER.corner} px right corner (the dev app's \`?time=corner\`; Chromium's env() override for the reference frame).`, ''] : [])];
  for (const b of batches) {
    const rs = results.filter((r) => r.batch === b);
    const passed = rs.filter((r) => r.pass).length;
    lines.push(`## ${b}: ${passed} / ${rs.length} pass (${Math.round((passed / rs.length) * 100)}%)`, '', '| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |', '|---|---|---|---|---|---|---|');
    for (const r of rs) lines.push(`| ${r.file.split('/')[1]} | ${r.pass ? '✅' : '❌'} | ${(r.diff * 100).toFixed(2)}% | ${r.clicks} | ${r.errors.join('<br>').replace(/\|/g, '\\|')} | ${r.unsupported.join('<br>').replace(/\|/g, '\\|')} | ${r.diags.join(', ')} |`);
    lines.push('');
  }
  fs.writeFileSync(path.join(root, 'docs', `compat-report-${GROUP}${SUFFIX}.md`), lines.join('\n'));
});
