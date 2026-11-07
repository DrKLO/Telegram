// Builds packages/conformance (the conformance set shipped with the framework) from its widget files:
//   1. catalogue.json / catalogue.md from scripts/conformance/catalogue.ts;
//   2. the harness region of every widget: the harness CSS of its set, the
//      harness JS bundled with only the exports the widget uses and minified
//      (esbuild), the stage geometry; every widget must fit a v1 mini-widget
//      with a margin (SIZE_LIMIT), or the build fails;
//   3. reference images: every stage rendered natively by headless Chromium
//      (and WebKit when Playwright has it installed) at DPR 2, in reference mode
//      (a stage with <template data-ref> gets the template's markup), with the
//      renderer's UA stylesheet from profile.json (the unstyled <button>) →
//      packages/conformance/refs/<set>/<widget>/<stage>.<engine>[.dark].png;
//   4. the reference pictures posted next to the visual set and the client-SDK
//      group in the test channel: the whole widget rendered natively at the
//      reference width (refs/visual/<widget>[.dark].png); the height gate
//      (MAX_HEIGHT): a visual or client-SDK widget taller than the bubble's
//      cap allows fails the build; so does an automatic widget, measured
//      through the sandbox (heights.ts: the dev app on a dev server the build
//      starts, or --origin; --no-heights skips it); and so does a picture of
//      the visual set that differs from the widget drawn through the sandbox
//      (picture-check.ts, on the same dev server): a picture shows what widget
//      profile 1 draws, not what Chromium does with an out-of-profile feature;
//   5. the native run of every widget (no template swap): what a plain
//      browser engine says to each check, recorded in the manifest as a sanity
//      reference for the tests themselves;
//   6. manifest.json: test → catalogue IDs, kind, stages and references,
//      actions, host checks, themes and widths, coverage per set;
//   7. channel.json: the posting order for the test channel.
//
//   corepack pnpm tsx scripts/conformance/build.ts [--only 04-layout] [--no-refs] [--engines chromium,webkit] [--origin http://127.0.0.1:5175 | --no-heights]
//   corepack pnpm tsx scripts/conformance/build.ts --sizes [--only …]   # sizes and the size gate only: no browser, nothing written

import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import { createRequire } from 'node:module';
import { chromium, webkit, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { PNG } from 'pngjs';
import { CATALOGUE } from './catalogue';
import { emitCatalogue } from './emit-catalogue';
import { AUTO_GRID, CONF, MAX_HEIGHT, ROOT, STAGE, autoStagePos, listWidgets, parseWidget, perform, picturePath, posixRelative, readJson, refPath, type BotAction, type MessageSpec, type Published, type Widget } from './lib';
import { diffStats } from '../../packages/conformance/tools/diff.mjs';
import { measureHeights } from './heights';
import { checkPictures } from './picture-check';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import { REFERENCE_STYLESHEET, REFERENCE_TIME_LABEL, referenceSafeAreaCss, timeCornerStyle } from '../../packages/core/src/shared/reference.ts';
import { DEFAULT_THEME_PARAMS, checkContentSafeArea, type ContentSafeArea } from '../../packages/core/src/shared/sdk-policy.ts';

const args = process.argv.slice(2);
const opt = (name: string) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : undefined);
const ONLY = opt('only');
const SIZES = args.includes('--sizes');
const NO_REFS = args.includes('--no-refs') || SIZES;
const ENGINES = (opt('engines') ?? 'chromium,webkit').split(',');
const WIDTH = 390;
const DPR = 2;

const harnessDir = path.join(CONF, 'harness');
// esbuild comes with the core package (its dev dependency); the conformance set needs nothing of its own.
const esbuild = createRequire(path.join(ROOT, 'packages', 'core', 'package.json'))('esbuild');
const minCss = (file: string) => esbuild.transformSync(fs.readFileSync(path.join(harnessDir, file), 'utf8'), { loader: 'css', minify: true }).code.trim();
const HARNESS_CSS: Record<string, string> = { auto: minCss('harness.css') + minCss('auto.css'), visual: minCss('harness.css') + minCss('visual.css') };
const BEGIN = '<!-- conformance:harness -->';
const END = '<!-- /conformance:harness -->';
// The client-SDK group (visual/sdk) uses the mini harness, so a widget fits a v1 mini-widget.
const MINI_JS = fs.readFileSync(path.join(harnessDir, 'mini.js'), 'utf8').replace(/^\/\/.*\n/gm, '').trim();
const MINI_CSS = fs.readFileSync(path.join(harnessDir, 'mini.css'), 'utf8').trim();
const MINI_BEGIN = '<!-- conformance:mini -->';
const MINI_END = '<!-- /conformance:mini -->';
const SDK_MOCK = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'sdk-mock.js'), 'utf8');
/**
 * v1 mini-widgets (docs/tgw-format.md): the server refuses more than 4 KB after zlib at level 9
 * (PHP gzcompress) or 32 KB of HTML (INPUT_MEDIA_WIDGET_TOO_LARGE). Every widget stays within
 * 3800 bytes compressed, the margin the harness and the dev app warn at (src/shared/widget-size.ts).
 */
const SIZE_LIMIT = { zlib: 3800, raw: 32768 };
const sizeOf = (html: string) => { const b = Buffer.from(html); return { raw: b.length, zlib: zlib.deflateSync(b, { level: 9 }).length }; };
const isSdk = (w: Widget) => w.id.startsWith('visual/sdk/');
/**
 * The renderer's UA layer (profile.json tables.uaStylesheet: the root and text defaults, the unstyled <button>) with
 * the reference theme (default palettes, reference text tokens): every renderer adds it to a widget, so the native
 * runs do too (packages/core/src/shared/reference.ts).
 */
const UA_CSS = REFERENCE_STYLESHEET;

// ───────────── 2. harness region ─────────────

/** Auto set: stages sit on a fixed grid (auto.css places the first one; these rules the others, by position). */
function stageCss(w: Widget): string {
  const rules: string[] = [];
  const n = w.stages.length;
  if (w.set === 'auto' && n) {
    for (let c = 1; c < Math.min(n, AUTO_GRID.perRow); c++) rules.push(`.conf-stages>:nth-child(${AUTO_GRID.perRow}n+${c + 1}){left:${AUTO_GRID.left + c * AUTO_GRID.dx}px}`);
    for (let r = 1; r * AUTO_GRID.perRow < n; r++) rules.push(`.conf-stages>:nth-child(n+${r * AUTO_GRID.perRow + 1}){top:${AUTO_GRID.top + r * AUTO_GRID.dy}px}`);
    rules.push(`.conf-stages{height:${AUTO_GRID.top + Math.ceil(n / AUTO_GRID.perRow) * AUTO_GRID.dy}px}`);
  }
  // A stage of another size than its set's default.
  for (const s of w.stages) if (s.w !== STAGE[w.set].w || s.h !== STAGE[w.set].h) rules.push(`[data-stage="${s.name}"]{width:${s.w}px;height:${s.h}px}`);
  return rules.join('');
}

/** The harness JS for one widget: only the exports it uses (T.ok, T.run, …), bundled and minified, as `var T`. */
function harnessJs(body: string): string {
  const names = [...new Set([...body.matchAll(/\bT\.([a-zA-Z]+)/g)].map((m) => m[1]).concat('run'))].sort();
  const list = /id="conf-list"/.test(body) ? "import { list } from './harness.js'; list();\n" : '';
  const out = esbuild.buildSync({
    stdin: { contents: `${list}export { ${names.join(', ')} } from './harness.js';`, resolveDir: harnessDir, loader: 'js' },
    bundle: true, format: 'esm', minify: true, write: false, target: 'es2017', charset: 'utf8', legalComments: 'none',
  }).outputFiles[0].text.trim();
  const m = /export\s*\{([^}]*)\};?$/.exec(out);
  if (!m) throw new Error('harness bundle has no export list');
  const members = m[1].split(',').map((p: string) => { const [local, as] = p.trim().split(/\s+as\s+/); return `${as ?? local}:${local}`; });
  return `var T=function(){${out.slice(0, m.index)}return{${members.join(',')}}}();`;
}

function withHarness(w: Widget): string {
  if (w.html.includes(MINI_BEGIN)) {
    const i = w.html.indexOf(MINI_BEGIN), j = w.html.indexOf(MINI_END);
    if (j < 0) throw new Error(`${w.id}: no ${MINI_END}`);
    const css = w.meta.bare ? '' : `<style>${MINI_CSS.replace(/\n/g, '')}</style>`;
    return w.html.slice(0, i) + `${MINI_BEGIN}${css}<script>${MINI_JS}</script>${MINI_END}` + w.html.slice(j + MINI_END.length);
  }
  const i = w.html.indexOf(BEGIN), j = w.html.indexOf(END);
  if (i < 0 || j < 0) throw new Error(`${w.id}: no ${BEGIN} … ${END} region`);
  const body = w.html.slice(0, i) + w.html.slice(j + END.length);
  // A bare widget tests the renderer's own defaults: no harness CSS over them.
  const css = (w.meta.bare ? '' : HARNESS_CSS[w.set]) + stageCss(w);
  const region = `${BEGIN}${css ? `<style>${css}</style>` : ''}<script>${harnessJs(body)}</script>${END}`;
  return w.html.slice(0, i) + region + w.html.slice(j + END.length);
}

// ───────────── native runs ─────────────

async function newContext(browser: Browser, theme: 'light' | 'dark', width = WIDTH): Promise<BrowserContext> {
  const ctx = await browser.newContext({ viewport: { width, height: 2000 }, deviceScaleFactor: DPR, colorScheme: theme });
  // A native page has the network: nothing may leave it (the widgets never need it; links must not navigate).
  await ctx.route('**/*', (r) => (r.request().url().startsWith('data:') ? r.continue() : r.abort()));
  return ctx;
}

async function published(page: Page): Promise<Published | null> {
  return page.evaluate(() => { const v = document.documentElement.getAttribute('data-conformance'); return v ? JSON.parse(v) : null; });
}

async function waitStatus(page: Page, want: string[], timeout: number): Promise<Published | null> {
  const t0 = Date.now();
  let last: Published | null = null;
  while (Date.now() - t0 < timeout) {
    last = await published(page).catch(() => null);
    if (last && want.includes(last.status)) return last;
    await page.waitForTimeout(100);
  }
  return last;
}

/**
 * Reference pictures: a page whose root says `color-scheme: dark` gets Chromium's own dark canvas, not the default background
 * set through CDP, where a client shows its bubble through the transparent renderer. When neither the root nor the body paints
 * a background (so nothing propagates), the root takes the bubble's colour.
 */
const BUBBLE = `addEventListener('load',function(){var d=document.documentElement,b=document.body,t=function(e){var s=getComputedStyle(e);return s.backgroundImage==='none'&&/^(transparent|rgba\\(0, 0, 0, 0\\))$/.test(s.backgroundColor)};if(b&&t(d)&&t(b))d.style.backgroundColor=matchMedia('(prefers-color-scheme: dark)').matches?${JSON.stringify(DEFAULT_THEME_PARAMS.dark.bg_color)}:${JSON.stringify(DEFAULT_THEME_PARAMS.light.bg_color)}});`;

/** In reference mode every stage with a <template data-ref="name"> gets the template's markup (after the widget's own scripts ran). */
const SWAP = "document.addEventListener('DOMContentLoaded',function(){[].forEach.call(document.querySelectorAll('template[data-ref]'),function(t){var s=document.querySelector('[data-stage=\"'+t.getAttribute('data-ref')+'\"]');if(s)s.innerHTML=t.innerHTML})})";

/**
 * sdk: 'mock' = the SDK stand-in (client-SDK group pictures); 'palette' = only the reference palette (visual set pictures).
 * The page's canvas is the reference palette's bubble colour, set outside the document (Chromium), as the client's bubble is
 * behind a transparent renderer: a widget's root background or body background still paints over it (propagation).
 */
async function openNative(ctx: BrowserContext, html: string, mode: 'ref' | 'native', sdk: 'mock' | 'palette' | false = false, theme: 'light' | 'dark' = 'light', message?: MessageSpec, safeArea?: Partial<ContentSafeArea>): Promise<Page> {
  const page = await ctx.newPage();
  // A widget drawn with the client's time over its corner (the header's `safeArea`, reference mode only): the engine's
  // own env(safe-area-inset-*) and the root variables a renderer sets both hold the band (Chromium only).
  const area = mode === 'ref' && safeArea && ctx.browser()?.browserType().name() === 'chromium' ? checkContentSafeArea(safeArea) : null;
  if (ctx.browser()?.browserType().name() === 'chromium') {
    const hex = DEFAULT_THEME_PARAMS[theme].bg_color;
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
    const cdp = await ctx.newCDPSession(page);
    await cdp.send('Emulation.setDefaultBackgroundColorOverride', { color: { r, g, b, a: 1 } });
    if (area) await cdp.send('Emulation.setSafeAreaInsetsOverride' as any, { insets: { top: area.top, right: area.right, bottom: area.bottom, left: area.left } });
  }
  // The mode goes into the HTML, before the harness. Links never navigate in a native reference run.
  const boot = (UA_CSS ? `<style>${UA_CSS}${area ? referenceSafeAreaCss(area) : ''}</style>` : '') + `<script>window.__conformanceMode=${JSON.stringify(mode)};document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a');if(a)e.preventDefault()},true);${mode === 'ref' ? SWAP + ';' + BUBBLE : ''}</script>` + (sdk ? `<script>${sdk === 'palette' ? 'window.__conformanceNoSdk=1;' : ''}${message ? `window.__conformanceMessage=${JSON.stringify(message)};` : ''}${SDK_MOCK}</script>` : '');
  // Served from a real (fake) origin, not about:blank, so storage works as in a WebView.
  const url = 'https://conformance.test/widget.html';
  await page.route(url, (r) => r.fulfill({ contentType: 'text/html; charset=utf-8', body: html.replace(/<head>/i, '<head>' + boot) }));
  // The SDK group's pictures show clock times (11-crash-restart): a fixed clock keeps them the same on every build.
  if (sdk) await page.clock.setFixedTime(new Date('2026-10-17T12:00:00'));
  await page.goto(url, { waitUntil: 'load' });
  return page;
}

interface NativeResult { status: string; checks: [string, string, string, string][]; skipped: string[] }

async function runNative(ctx: BrowserContext, w: Widget, html: string): Promise<NativeResult> {
  const page = await openNative(ctx, html, 'native', isSdk(w) && 'mock', 'light', w.meta.message?.[0]);
  let pub = await waitStatus(page, ['done', 'awaiting'], 15_000);
  let skipped: string[] = [];
  let skippedIds: string[] = [];
  if (w.meta.actions?.length) {
    ({ skipped, ids: skippedIds } = await perform(page, w.meta.actions, {
      origin: async () => ({ x: 0, y: 0 }),
      setTheme: async (t) => { await page.emulateMedia({ colorScheme: t }); },
    }, () => published(page)));
    pub = await waitStatus(page, ['done'], 8_000);
  }
  await page.close();
  const checks = (pub?.checks ?? []).map((c) => (skippedIds.includes(c[0]) ? [c[0], c[1], 'skip', 'input not available natively'] : c) as [string, string, string, string]);
  return { status: pub?.status ?? 'none', checks, skipped };
}

async function renderRefs(browsers: Record<string, Browser>, w: Widget, html: string): Promise<Record<string, Record<string, string>>> {
  const out: Record<string, Record<string, string>> = {};
  if (!w.stages.length) return out;
  for (const [engine, browser] of Object.entries(browsers)) {
    for (const theme of ['light', 'dark'] as const) {
      const stages = w.stages.filter((s) => s.themes.includes(theme));
      if (!stages.length) continue;
      const ctx = await newContext(browser, theme);
      const page = await openNative(ctx, html, 'ref', false, theme);
      await waitStatus(page, ['done', 'awaiting'], 15_000);
      await page.waitForTimeout(150);
      for (const s of stages) {
        const file = refPath(w, s.name, engine, theme);
        fs.mkdirSync(path.dirname(file), { recursive: true });
        const loc = page.locator(`[data-stage="${s.name}"]`);
        const buf = await loc.screenshot({ animations: 'allow', caret: 'hide' });
        writeIfChanged(file, buf);
        ((out[s.name] ??= {})[`${engine}${theme === 'dark' ? '.dark' : ''}`] = posixRelative(CONF, file));
      }
      await ctx.close();
    }
  }
  return out;
}

/** The visual set's and the client-SDK group's themes: a picture per theme. */
const pictureThemes = (w: Widget) => (w.meta.themes ?? (w.stages.some((s) => s.themes.includes('dark')) ? ['light', 'dark'] : ['light'])) as ('light' | 'dark')[];

/**
 * The reference picture posted next to a visual or client-SDK widget in the test channel: the whole widget
 * rendered natively in reference mode at the reference width, DPR 2, on the reference palette's background
 * (the SDK group with the SDK stand-in), at most 2000 CSS px tall (1200 for the SDK group) → refs/visual/<widget>[.dark].png.
 * Animations as they are: Playwright's `animations: 'disabled'` finishes a finite animation, a paused one too (it drew
 * 06-css-animations' frozen box back at its start); a row that moves has a template.
 */
async function renderPictures(browser: Browser, w: Widget, html: string): Promise<Record<string, string>> {
  const out: Record<string, string> = {};
  for (const theme of pictureThemes(w)) {
    const ctx = await newContext(browser, theme);
    const page = await openNative(ctx, html, 'ref', isSdk(w) ? 'mock' : 'palette', theme, w.meta.message?.[0], w.meta.safeArea);
    await waitStatus(page, ['done', 'awaiting'], 15_000);
    await page.waitForTimeout(300);
    const h = Math.min(isSdk(w) ? 1200 : 2000, await page.evaluate(() => Math.ceil(document.documentElement.getBoundingClientRect().height)));
    if (w.meta.safeArea) await drawTime(page, w.meta.safeArea, h);
    const file = picturePath(w, theme);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    writeIfChanged(file, await page.screenshot({ clip: { x: 0, y: 0, width: WIDTH, height: Math.max(h, 40) }, animations: 'allow', caret: 'hide' }));
    out[theme] = posixRelative(CONF, file);
    await ctx.close();
  }
  return out;
}

/**
 * The client's time over the bottom corner of a picture `height` CSS px tall, as the dev app draws it over a bubble
 * (reference.ts `timeCornerStyle`): outside the widget's document flow, on top of everything.
 */
async function drawTime(page: Page, area: Partial<ContentSafeArea>, height: number) {
  const style = timeCornerStyle(area);
  if (!style) return;
  await page.evaluate(({ style, height, label }) => {
    const box = document.createElement('div');
    box.style.cssText = `position:absolute;left:0;top:0;width:100%;height:${height}px;pointer-events:none;z-index:2147483647`;
    const time = document.createElement('div');
    time.setAttribute('style', style);
    time.textContent = label;
    box.appendChild(time);
    document.documentElement.appendChild(box);
  }, { style, height, label: REFERENCE_TIME_LABEL });
}

/** The height gate: the widget's height at MAX_HEIGHT.width, drawn natively in reference mode. */
async function measureHeight(browser: Browser, w: Widget, html: string): Promise<number> {
  const ctx = await newContext(browser, 'light', MAX_HEIGHT.width);
  const page = await openNative(ctx, html, 'ref', isSdk(w) ? 'mock' : 'palette', 'light', w.meta.message?.[0], w.meta.safeArea);
  await waitStatus(page, ['done', 'awaiting'], 15_000);
  await page.waitForTimeout(300);
  const h = await page.evaluate(() => Math.ceil(document.documentElement.getBoundingClientRect().height));
  await ctx.close();
  return h;
}

/** Sanity check of a reftest: the stage drawn natively by the feature vs its template. */
async function selfVsRef(browser: Browser, w: Widget, html: string): Promise<Record<string, number>> {
  const res: Record<string, number> = {};
  const reftests = w.stages.filter((s) => s.reftest);
  if (!reftests.length) return res;
  const ctx = await newContext(browser, 'light');
  const page = await openNative(ctx, html, 'native');
  await waitStatus(page, ['done', 'awaiting'], 15_000);
  await page.waitForTimeout(150);
  for (const s of reftests) {
    if (!s.themes.includes('light')) continue;
    const buf = await page.locator(`[data-stage="${s.name}"]`).screenshot({ animations: 'allow', caret: 'hide' });
    const ref = refPath(w, s.name, 'chromium', 'light');
    if (fs.existsSync(ref)) res[s.name] = Math.round(diffStats(PNG.sync.read(buf), PNG.sync.read(fs.readFileSync(ref))).ink * 10000) / 10000;
  }
  await ctx.close();
  return res;
}

function writeIfChanged(file: string, data: string | Buffer) {
  if (fs.existsSync(file) && Buffer.compare(fs.readFileSync(file), Buffer.from(data)) === 0) return;
  fs.writeFileSync(file, data);
}

// ───────────── main ─────────────

/** The size gate: every widget is a v1 mini-widget, with a margin. */
function checkSize(w: Widget, html: string): string | null {
  const z = sizeOf(html);
  return z.raw > SIZE_LIMIT.raw || z.zlib > SIZE_LIMIT.zlib
    ? `${w.id}: ${z.zlib} B compressed, ${z.raw} B of HTML; the limit is ${SIZE_LIMIT.zlib} / ${SIZE_LIMIT.raw}`
    : null;
}

/** --sizes: the built size of every widget and the gate, without a browser and without writing anything. */
function sizesOnly(widgets: Widget[]) {
  let over = 0;
  for (const w of widgets) {
    const html = withHarness(w), z = sizeOf(html), bad = checkSize(w, html);
    if (bad) over++;
    console.log(`${w.id.padEnd(40)} ${String(z.raw).padStart(6)} B  zlib ${String(z.zlib).padStart(5)}${bad ? `  OVER by ${z.zlib - SIZE_LIMIT.zlib}` : ''}`);
  }
  if (over) { console.log(`\n${over} widget(s) over the limit (${SIZE_LIMIT.zlib} B compressed, ${SIZE_LIMIT.raw} B of HTML)`); process.exitCode = 1; }
}

async function main() {
  const widgets = listWidgets(ONLY);
  if (SIZES) return sizesOnly(widgets);
  emitCatalogue();
  const ids = new Set(CATALOGUE.map((f) => f.id));
  const browsers: Record<string, Browser> = {};
  for (const e of ENGINES) {
    try { browsers[e] = await (e === 'webkit' ? webkit : chromium).launch(); }
    catch (err) { console.warn(`${e}: not available (${String((err as Error).message).split('\n')[0]}); skipping its references`); }
  }
  if (!browsers.chromium) throw new Error('chromium is required');

  const manifestFile = path.join(CONF, 'manifest.json');
  const prev = readJson<{ tests: any[] }>(manifestFile, { tests: [] });
  const entries = new Map<string, any>(prev.tests.map((t) => [t.id, t]));
  const problems: string[] = [];
  const tooLarge: string[] = [];
  const tooTall: string[] = [];

  for (const w0 of widgets) {
    let html = withHarness(w0);
    const w = parseWidget(w0.set, w0.stem, html);
    process.stdout.write(`${w.id}: `);
    const refs = NO_REFS ? (entries.get(w.id)?.stages ?? []).reduce((m: any, s: any) => ({ ...m, [s.name]: s.refs }), {}) : await renderRefs(browsers, w, html);
    const refImages = w.set === 'visual' ? (NO_REFS ? entries.get(w.id)?.refImages ?? {} : await renderPictures(browsers.chromium, w, html)) : undefined;
    const height = w.set === 'visual' ? await measureHeight(browsers.chromium, w, html) : undefined;
    if (height !== undefined && height > MAX_HEIGHT.height && !w.meta.tall) tooTall.push(`${w.id}: ${height} px at ${MAX_HEIGHT.width} px wide`);
    const size = sizeOf(html);
    const bad = checkSize(w, html);
    if (bad) tooLarge.push(bad);
    writeIfChanged(w.file, html);

    const native: Record<string, NativeResult> = {};
    for (const [engine, browser] of Object.entries(browsers)) {
      const ctx = await newContext(browser, 'light');
      native[engine] = await runNative(ctx, w, html);
      await ctx.close();
    }
    const sanity = NO_REFS ? {} : await selfVsRef(browsers.chromium, w, html);

    const checkKeys = new Map<string, { id: string; name: string; native: Record<string, string> }>();
    for (const [engine, r] of Object.entries(native)) {
      for (const [id, name, st] of r.checks) {
        const k = `${id}|${name}`;
        const e = checkKeys.get(k) ?? { id, name, native: {} };
        e.native[engine] = st;
        checkKeys.set(k, e);
      }
    }
    const checks = [...checkKeys.values()];
    const covers = new Set<string>([...checks.map((c) => c.id), ...w.stages.flatMap((s) => s.covers), ...(w.meta.covers ?? []), ...(w.meta.host ?? []).map((h) => h.id)]);
    covers.delete('harness');
    for (const c of covers) if (!ids.has(c)) problems.push(`${w.id}: unknown catalogue id ${c}`);
    for (const c of checks) if (c.id === 'harness') problems.push(`${w.id}: harness error ${c.name}`);
    for (const c of checks) {
      const f = CATALOGUE.find((x) => x.id === c.id);
      if (!isSdk(w) && f?.status === 'in' && c.native.chromium === 'fail') problems.push(`${w.id}: in-profile check fails in native Chromium: ${c.id} — ${c.name}`);
    }
    // Visual-set templates are illustrations (end states, motion diagrams), not reftests: only the auto set is checked.
    // The template of an out feature's stage draws it neutralised, so a native engine differs from it by design.
    const outOnly = (st?: { covers: string[] }) => !!st?.covers.length && st.covers.every((id) => CATALOGUE.find((x) => x.id === id)?.status === 'out');
    if (w.set === 'auto') for (const [s, d] of Object.entries(sanity)) { const st = w.stages.find((x) => x.name === s); if (d > 0.02 && !st?.xfail && !outOnly(st)) problems.push(`${w.id}: stage ${s} drawn natively differs from its template by ${(d * 100).toFixed(1)} %`); }
    const stagePos = (i: number) => (w.set === 'auto' ? [...autoStagePos(i), w.stages[i].w, w.stages[i].h] : null);
    entries.set(w.id, {
      id: w.id,
      set: w.set,
      file: posixRelative(CONF, w.file),
      title: w.meta.title,
      hint: w.meta.hint,
      kind: [checks.length ? 'self' : null, w.stages.length ? 'pixel' : null, w.meta.actions?.length ? 'interactive' : null].filter(Boolean),
      covers: [...covers].sort(),
      themes: w.meta.themes ?? (w.stages.some((s) => s.themes.includes('dark')) ? ['light', 'dark'] : ['light']),
      widths: [360, 390, 430],
      actions: w.meta.actions ?? [],
      host: w.meta.host ?? [],
      checks: checks.map((c) => ({ id: c.id, name: c.name, native: c.native })),
      stages: w.stages.map((s, i) => ({ name: s.name, rect: stagePos(i), size: [s.w, s.h], covers: s.covers, themes: s.themes, reftest: s.reftest, maxDiff: s.maxDiff, colors: s.colors, xfail: s.xfail, refs: refs[s.name] ?? {}, nativeVsTemplate: sanity[s.name] })),
      bytes: size.raw,
      zlib: size.zlib,
      ...(refImages ? { refImages } : {}),
      ...(height !== undefined ? { height } : {}),
      ...(isSdk(w) ? { group: 'sdk', expect: w.meta.expect, contract: w.meta.contract, pending: w.meta.pending } : {}),
      ...(w.meta.stops ? { stops: w.meta.stops } : {}),
      ...(w.meta.safeArea ? { safeArea: w.meta.safeArea } : {}),
      ...(w.meta.message ? { message: w.meta.message } : {}),
      ...(w.meta.bot ? { bot: w.meta.bot } : {}),
    });
    const n = native.chromium.checks.reduce((m: Record<string, number>, c) => ({ ...m, [c[2]]: (m[c[2]] ?? 0) + 1 }), {});
    console.log(`${checks.length} checks (chromium: ${Object.entries(n).map(([k, v]) => `${k} ${v}`).join(', ')}), ${w.stages.length} stages, ${(size.raw / 1024).toFixed(1)} KB (${size.zlib} B compressed)${bad ? ' TOO LARGE' : ''}${height !== undefined ? `, ${height} px tall${tooTall.at(-1)?.startsWith(w.id + ':') ? ' TOO TALL' : ''}` : ''}`);
  }
  for (const b of Object.values(browsers)) await b.close();

  // Drop entries of widgets that no longer exist.
  const all = new Set(listWidgets().map((w) => w.id));
  for (const k of [...entries.keys()]) if (!all.has(k)) entries.delete(k);
  const tests = [...entries.values()].sort((a, b) => a.id.localeCompare(b.id));
  const coverage = (set: string) => new Set(tests.filter((t) => t.set === set).flatMap((t) => t.covers));
  const auto = coverage('auto'), visual = coverage('visual');
  // Covered by other suites of this repository (they kill or flood the widget, which a conformance widget cannot report on itself).
  const external: Record<string, string> = {
    'limit.nodes': 'corpus/attacks (pnpm test:attacks): node flood',
    'limit.cpu': 'corpus/attacks and tests/lifecycle.spec.ts: CPU quota, deferral, kill',
    'limit.memory': 'corpus/attacks (pnpm test:attacks): memory cap',
    'limit.html-size': 'server-side (D20): not a client check',
    // Client behaviour around the widget: judged by people with the client-SDK group (visual/sdk).
    'behaviour.restart': 'visual/sdk/11-crash-restart (client behaviour)',
    'behaviour.gesture-claim': 'visual/sdk/12-drag-gestures (client behaviour)',
    'behaviour.storage-persist': 'visual/sdk/07, 08, 09 (client behaviour)',
    'behaviour.storage-forward': 'visual/sdk/15-storage-forward (client behaviour)',
    'behaviour.message-edit': 'visual/sdk/16-state-edit (client behaviour)',
    'behaviour.data-edit': 'visual/sdk/16-state-edit, 24-watch-public-data, 25-group-chat-state (client behaviour)',
    'behaviour.forward-copy': 'visual/sdk/20-forward-requests and auto/26-context-copy, -live-copy viewed as forwards (client behaviour)',
    'behaviour.preview': 'visual/sdk/23-preview and auto/26-context-preview in the inline results (client behaviour)',
    'behaviour.network-marker': 'visual/sdk/21-network-marker (client behaviour)',
    'behaviour.resize-anchor': 'visual/sdk/18-resize-anchor (client behaviour)',
    'behaviour.size-hint': 'visual/sdk/17-size-hint (client behaviour)',
    'behaviour.time-corner': 'visual/17-time-corner, sent without a caption (client behaviour)',
  };
  const needAuto = CATALOGUE.filter((f) => f.status !== 'out' && !auto.has(f.id) && !external[f.id]).map((f) => f.id);
  const outUncovered = CATALOGUE.filter((f) => f.status === 'out' && !auto.has(f.id)).map((f) => f.id);
  const manifest = {
    $comment: 'Generated by scripts/conformance/build.ts. One entry per widget of packages/conformance/{auto,visual}; checks[].native = what plain browser engines answer (a sanity reference for the test, not the expectation: out-of-profile and sandbox-only checks differ by design).',
    version: 1,
    catalogue: 'catalogue.json',
    render: { widths: [360, 390, 430], themes: ['light', 'dark'], dpr: DPR, referenceWidth: WIDTH, stageGrid: AUTO_GRID },
    coverage: { auto: auto.size, visual: visual.size, catalogue: CATALOGUE.length, external, notInAuto: needAuto, outNotInAuto: outUncovered },
    tests,
  };
  fs.writeFileSync(manifestFile, JSON.stringify(manifest, null, 1) + '\n');
  // The client-SDK group's post list for the test channel, from the manifest.
  const sdk = tests.filter((t) => t.group === 'sdk');
  if (sdk.length) {
    const lines = [
      '# Client SDK group: what each client implements on its side of SDK v1',
      '',
      'Generated by `scripts/conformance/build.ts` from the widget headers. Contract: `docs/sdk-v1.md`. Each widget is a v1 mini-widget (at most 4 KB compressed, 32 KB of HTML); post it as a widget message with its picture as a separate image next to it (two pictures: light and dark). A tester does what the instruction says and checks the client against the expected behaviour. Rows marked *waits* need server support first; they are here so the client side can be built against them. *Sent as* says how the test bot sends the widget (the `inputMediaWidget` fields, the chat, a forward, the inline results) and *The bot* what it does on the widget\'s requests (`scripts/conformance/test-bot.ts`, `CLIENTS.md`).',
      '',
      '| # | Widget | Picture | The tester | The client must | Sent as · the bot | Contract | zlib |',
      '|---|---|---|---|---|---|---|---|',
      ...sdk.map((t) => `| ${t.id.split('/').pop().slice(0, 2)} | [${t.title}](${path.basename(t.file)})${t.pending ? `<br>*waits: ${t.pending}*` : ''} | ${Object.entries(t.refImages ?? {}).map(([k, v]) => `[${k}](../../${v})`).join(' · ')} | ${t.hint ?? ''} | ${t.expect ?? ''} | ${sentAs(t.message, t.bot) || '—'} | ${t.contract ?? ''} | ${(t.zlib / 1024).toFixed(1)} KB |`.replace(/\n/g, ' ')),
      '',
    ];
    fs.writeFileSync(path.join(CONF, 'visual', 'sdk', 'README.md'), lines.join('\n'));
  }
  writeChannel(tests);
  emitCatalogue();
  console.log(`\nmanifest: ${tests.length} widgets; auto covers ${auto.size}, visual ${visual.size} of ${CATALOGUE.length}; in/candidate not in auto: ${needAuto.length}; out not in auto: ${outUncovered.length}`);
  if (needAuto.length) console.log('  not covered by auto: ' + needAuto.join(', '));
  if (problems.length) { console.log('\nproblems:'); for (const p of problems) console.log('  ' + p); }
  if (tooLarge.length) {
    console.log(`\ntoo large for a mini-widget (${SIZE_LIMIT.zlib} B compressed, ${SIZE_LIMIT.raw} B of HTML):`);
    for (const p of tooLarge) console.log('  ' + p);
    process.exitCode = 1;
  }
  // Through the sandbox, on the dev app: the automatic set's height gate (drawn natively an automatic widget also
  // lists the out-of-profile checks Chromium fails, so the native height is not the client's), and the pictures just
  // drawn against the pipeline (the widget files as written above).
  const measured = args.includes('--no-heights') ? [] : widgets.filter((w) => w.set === 'auto');
  const pictured = NO_REFS ? [] : listWidgets(ONLY).filter((w) => w.set === 'visual');
  let differ: string[] = [];
  if (measured.length || pictured.length) await withDevServer(async (origin) => {
    if (measured.length) {
      console.log(`\nheights of the automatic set through the sandbox (${origin}):`);
      tooTall.push(...(await measureHeights(measured, origin, 'shadow', [MAX_HEIGHT.width], (line) => console.log('  ' + line))));
    }
    if (pictured.length) {
      console.log(`\nthe visual set's pictures against the widget drawn through the sandbox (${origin}):`);
      differ = await checkPictures(pictured, origin, 'shadow', (line) => console.log('  ' + line));
    }
  });
  if (tooTall.length) {
    console.log(`\ntaller than ${MAX_HEIGHT.height} px at a ${MAX_HEIGHT.width} px bubble (the cap is 600; split the widget, or "tall": true for a test of the cap):`);
    for (const p of tooTall) console.log('  ' + p);
    process.exitCode = 1;
  }
  if (differ.length) {
    console.log('\npictures that differ from the widget drawn through the sandbox (an out-of-profile feature drawn by Chromium: neutralise it in reference mode; or a pipeline bug; diffs in reports/conformance/pictures):');
    for (const p of differ) console.log('  ' + p);
    process.exitCode = 1;
  }
}

/**
 * Size anchors (inputMediaWidget width / height, CSS px, 0 = auto) for widgets the test bot sends more than
 * once or with a size: a header's "anchors" ([[width, height], …]) wins. 17-size-hint is sent four times, one per anchor case of its hint (0×0, 0×300, 300×0, 300×300).
 */
const ANCHORS: Record<string, [number, number][]> = { 'visual/sdk/17-size-hint': [[0, 0], [0, 300], [300, 0], [300, 300]] };

/** Runs `fn` on the dev app: --origin's, or a dev server the build starts for it on a free port. */
async function withDevServer(fn: (origin: string) => Promise<void>): Promise<void> {
  let origin = opt('origin');
  let server: ReturnType<typeof spawn> | undefined;
  if (!origin) {
    const port = await new Promise<number>((resolve) => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = (s.address() as net.AddressInfo).port; s.close(() => resolve(p)); }); });
    origin = `http://127.0.0.1:${port}`;
    server = spawn('corepack', ['pnpm', 'dev', '--port', String(port)], { cwd: ROOT, stdio: 'ignore', detached: true });
    const t0 = Date.now();
    for (;;) {
      try { if ((await fetch(origin)).ok) break; } catch {}
      if (Date.now() - t0 > 90_000) { process.kill(-server.pid!); throw new Error(`the dev server for the checks through the sandbox did not start on ${origin}`); }
      await new Promise((r) => setTimeout(r, 500));
    }
  }
  try { await fn(origin); }
  finally { if (server) process.kill(-server.pid!); }
}

/** One message spec in words (the README's Sent as column, a post's caption). */
export function describeMessage(m: MessageSpec): string {
  const where = m.inline ? 'an inline result for the query conf-preview' : m.chat === 'group' ? 'the test group' : 'the private chat';
  const flags = (['allow_queries', 'fwd_allow_queries', 'shared_widget'] as const).filter((f) => m[f]);
  const data = (['chat_data', 'public_data'] as const).filter((f) => m[f] !== undefined).map((f) => `${f} ${JSON.stringify(m[f])}`);
  return [`${m.kind}: ${where}`, flags.length ? flags.join(', ') : 'no flags', ...data, m.forward ? 'then forwarded' : ''].filter(Boolean).join(', ');
}
/** What the test bot does for one request data. */
export function describeBot(data: string, a: BotAction): string {
  const parts = [
    a.edit ? `editMessageWidgetData ${Object.entries(a.edit).map(([k, v]) => `${k} ${JSON.stringify(v)}`).join(', ')}` : '',
    a.editShared ? `editWidgetData public_data ${JSON.stringify(a.editShared.public_data)}${a.editShared.cache_time !== undefined ? `, cache_time ${a.editShared.cache_time}` : ''}` : '',
    a.repeat ? `every ${a.repeat.every} s, ${a.repeat.times} times` : '',
    a.result !== undefined ? `result ${JSON.stringify(a.result)}` : 'an empty answer',
  ].filter(Boolean);
  return `\`${data}\` → ${parts.join('; ')}`;
}
function sentAs(message?: MessageSpec[], bot?: Record<string, BotAction>): string {
  return [...(message ?? []).map(describeMessage), ...Object.entries(bot ?? {}).map(([d, a]) => describeBot(d, a))].join('<br>');
}

/** channel.json: the test channel's posting order (the visual set, the client-SDK group, then the automatic set), one entry per message. */
function writeChannel(tests: any[]) {
  const order = (t: any) => (t.set === 'visual' ? (t.group === 'sdk' ? 1 : 0) : 2);
  const posts: any[] = [];
  const bot: Record<string, { case: string; action: BotAction }> = {};
  for (const t of [...tests].sort((a, b) => order(a) - order(b) || a.id.localeCompare(b.id))) {
    const header = parseWidget(t.set, t.id.slice(t.set.length + 1), fs.readFileSync(path.join(CONF, t.file), 'utf8')).meta as any;
    const check = t.set === 'auto'
      ? 'the widget checks itself: its result line at the bottom must say "done" and show no "fail" (each failure is listed under it)'
      : t.group === 'sdk' ? `${t.hint ?? ''} The client must: ${t.expect ?? ''}` : t.hint ?? 'compare with the picture';
    const messages: (MessageSpec | undefined)[] = header.message ?? [undefined];
    // The client draws its time over a widget with no caption below it (docs/sdk-v1.md §2.17): a case of it goes out
    // without a caption, then with its caption above the widget (invert_media), which must look the same.
    const captions: ('none' | 'above' | undefined)[] = header.safeArea ? ['none', 'above'] : [undefined];
    for (const message of messages) for (const captionAt of captions) for (const [width, height] of header.anchors ?? ANCHORS[t.id] ?? [[0, 0]]) {
      const view = message ? ` Sent as ${describeMessage(message)}${message.view ? `; look at ${message.view}` : ''}.` : '';
      const placed = captionAt === 'none' ? ' Sent without a caption: your time over the widget\'s bottom corner.'
        : captionAt === 'above' ? ' Sent with its caption above the widget (invert_media): your time over the widget\'s bottom corner, as without a caption.' : '';
      posts.push({
        widget: t.file,
        width, height,
        ...(message ? { message, ...(messages.length > 1 ? { variant: message.kind } : {}) } : {}),
        ...(captionAt === 'none' ? { withoutCaption: true } : captionAt === 'above' ? { captionAbove: true, variant: 'caption-above' } : {}),
        caption: `${t.id} · ${t.title}. ${check}${view}${placed && !/\.\s*$/.test(check + view) ? '.' : ''}${placed}`.replace(/\s+/g, ' ').trim(),
        photos: Object.values(t.refImages ?? {}),
        zlib: t.zlib,
        ...(t.pending ? { pending: t.pending } : {}),
      });
    }
    for (const [data, action] of Object.entries((header.bot ?? {}) as Record<string, BotAction>)) {
      if (bot[data] && JSON.stringify(bot[data].action) !== JSON.stringify(action)) throw new Error(`${t.id}: bot data ${data} is also used by ${bot[data].case} with another action`);
      bot[data] = { case: t.id, action };
    }
  }
  const channel = {
    $comment: 'Generated by scripts/conformance/build.ts: the test channel, in posting order. A test bot sends each widget as a layer-233 message (inputMediaWidget: html = the file, width / height = the anchors, 0 = auto) with its caption (none with `withoutCaption`, shown above the widget with `captionAbove`, Bot API show_caption_above_media / layer-233 invert_media: the client then draws the message time over the bottom corner of the widget, docs/sdk-v1.md §2.17), then the photos (the reference pictures, light then dark) as a separate message next to it. A post with `message` is sent with those fields (allow_queries, fwd_allow_queries, shared_widget, chat_data, public_data), into the test group when chat is group, forwarded by the tester when forward, or as the inline result for the query conf-preview when inline; its kind is the message context the client must report where the tester looks. `bot`: what the test bot does on a widget_query with that data (scripts/conformance/test-bot.ts; templates {n}, {from}, {time}).',
    version: 1,
    posts,
    bot,
  };
  fs.writeFileSync(path.join(CONF, 'channel.json'), JSON.stringify(channel, null, 1) + '\n');
}

main().catch((e) => { console.error(e); process.exit(1); });
