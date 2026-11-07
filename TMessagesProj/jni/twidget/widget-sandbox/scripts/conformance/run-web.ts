// Web runner of the conformance set: every widget of packages/conformance on the
// web dev app (apps/web) through the real pipeline (QuickJS worker → validator
// → renderer), on each renderer: iframe and shadow (Shadow DOM, the web product
// renderer); the frozen blitz and litehtml prototypes (wasm) only when named in
// --renderers and built (docs/decisions.md, "The renderer prototypes are frozen"). For each widget it
//   - reads the result from the validated ops: it taps the renderer's `send`
//     and keeps the last `data-conformance` attribute set on the element the
//     `el` op adopted as <html> (what any host can do with its own ops);
//   - performs the widget's scripted actions (taps, drags, hover, theme) at the
//     target rects the widget published, then waits for the result again;
//   - screenshots the bubble at DPR 2, crops each stage of the auto set and
//     diffs it against the Chromium/WebKit references;
//   - checks the host-side expectations (bridge calls, height).
// Output: reports/conformance/<renderer>/<widget>.json (+ stage crops and diffs)
// and the per-renderer report reports/conformance/<renderer>.json; report.ts
// writes the run files, packages/conformance/tools/report.mjs judges them (the
// same tool every client runs on its own run files).
//
// Needs the dev server of this checkout:  corepack pnpm dev --port 5175
//   corepack pnpm tsx scripts/conformance/run-web.ts --origin http://127.0.0.1:5175 \
//     [--renderers iframe,shadow] [--set auto,visual] [--only 03-layout-a] \
//     [--themes light,dark] [--widths 390] [--features webAnimations] [--policy strict]

import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { chromium, type Page } from '@playwright/test';
import { PNG } from 'pngjs';
import { ROOT, autoStagePos, crop, judgeStage, listWidgets, perform, refPath, type Published, type Widget } from './lib';

const args = process.argv.slice(2);
const opt = (name: string, def?: string) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : def);
const ORIGIN = opt('origin', `http://127.0.0.1:${process.env.TW_PORT ?? 5175}`)!;
const RENDERERS = opt('renderers', 'iframe,shadow')!.split(',');
const SETS = opt('set', 'auto,visual')!.split(',');
const THEMES = opt('themes');
const WIDTHS = opt('widths', '390')!.split(',').map(Number);
const FEATURES = opt('features', 'webAnimations')!;
const POLICY = opt('policy', 'strict')!;
const ONLY = opt('only');
const DPR = 2;
const OUT = path.join(ROOT, 'reports', 'conformance');

/** One run in the run-file format of packages/conformance/run.schema.json (plus a few web-only extras). */
export interface WidgetRun {
  id: string; set: string; renderer: string; theme: string; width: number;
  loaded: boolean; dead: string | null; status: string; ms: number;
  checks: [string, string, string, string][];
  /** Stage crops (paths relative to the run file); report.mjs judges them against the references. */
  stages: { name: string; crop: string }[];
  /** What the host saw, for the manifest's host checks. */
  observed: { bridge: { method: string; args: unknown[] }[]; height: number; chatFocused: boolean; focusHolder: string; syntheticInput: boolean };
  /** Indices into the manifest's actions that this renderer could not deliver. */
  skippedActions: number[];
  errors: string[]; diags: Record<string, number>;
  /** Stage rects as the widget measured them in this renderer (CSS px). */
  stageRects?: Record<string, number[]>;
}

const commit = () => { try { return execSync('git rev-parse --short HEAD', { cwd: ROOT }).toString().trim(); } catch { return ''; } };
const canvasRenderer = (r: string) => r === 'blitz' || r === 'litehtml';

async function setup(page: Page, renderer: string, theme: string) {
  const q = new URLSearchParams({ theme, features: FEATURES, policy: POLICY });
  if (renderer !== 'iframe') q.set(renderer, '1');
  await page.goto(`${ORIGIN}/?${q}`);
  await page.waitForFunction(() => !!(window as any).__tw, null, { timeout: 30_000 });
}

/** Load the widget into the dev app and tap the renderer's ops for the result attribute. */
async function load(page: Page, w: Widget, width: number) {
  await page.evaluate(({ html, name, width, message, safeArea }) => {
    const wrap = document.querySelector('#sandbox-wrap') as HTMLElement;
    const chat = wrap.closest('.chat') as HTMLElement;
    const bubble = wrap.closest('.bubble') as HTMLElement;
    chat.style.width = width + 'px';
    bubble.style.padding = '0';
    bubble.style.borderRadius = '0';
    bubble.style.boxShadow = 'none';
    // A stand-in for the chat's input field, focused while the widget boots (host check `focus: 'chat'`).
    let chatInput = document.getElementById('conf-chat-input') as HTMLInputElement | null;
    if (!chatInput) {
      chatInput = document.createElement('input');
      chatInput.id = 'conf-chat-input';
      chatInput.placeholder = 'chat input';
      chatInput.style.cssText = 'position:fixed;left:8px;bottom:8px;width:200px;z-index:10';
      document.body.appendChild(chatInput);
    }
    chatInput.focus();
    const tw = (window as any).__tw;
    const conf: any = ((window as any).__conf = { html: null as number | null, attr: null as string | null, ops: 0 });
    // The message context of the header's first message spec (lib.ts MessageSpec) as the mock bot's: the kind (and so
    // the server's flags) and the state the test bot sends with; the private chat with no state otherwise.
    Object.assign(tw.bot.context, { message: message?.kind ?? 'private', chatType: null, viewer: 1001 });
    tw.bot.edit(name, { chatData: message?.chat_data ?? null, publicData: message?.public_data ?? null });
    // The header's content safe area (lib.ts WidgetMeta.safeArea): the time over the corner, as a client without a caption draws it.
    tw.setSafeArea(safeArea);
    tw.load(html, name);
    tw.host.element.setAttribute('data-conf-el', '');
    const r = tw.host.renderer;
    const send = r.send.bind(r);
    r.send = (m: any) => {
      if (m && m.type === 'ops') {
        for (const op of m.ops) {
          conf.ops++;
          if (op.op === 'el' && op.adopt === 'html') {
            conf.html = op.id;
            for (const [n, v] of op.attrs) if (n === 'data-conformance') conf.attr = v;
          } else if (op.op === 'attr' && op.id === conf.html && op.name === 'data-conformance') conf.attr = op.value;
        }
      }
      return send(m);
    };
  }, { html: w.html, name: `conformance/${w.id}.html`, width, message: w.meta.message?.[0] ?? null, safeArea: w.meta.safeArea ?? null });
}

async function published(page: Page): Promise<Published | null> {
  return page.evaluate(() => { const a = (window as any).__conf?.attr; try { return a ? JSON.parse(a) : null; } catch { return null; } });
}

async function waitStatus(page: Page, want: string[], timeout: number): Promise<Published | null> {
  const t0 = Date.now();
  let last: Published | null = null;
  while (Date.now() - t0 < timeout) {
    last = await published(page);
    if (last && want.includes(last.status)) return last;
    const dead = await page.evaluate(() => (window as any).__tw.state.dead);
    if (dead) return last;
    await page.waitForTimeout(100);
  }
  return last;
}

async function runWidget(page: Page, w: Widget, renderer: string, theme: 'light' | 'dark', width: number): Promise<WidgetRun> {
  const t0 = Date.now();
  await load(page, w, width);
  await page.waitForFunction(() => { const s = (window as any).__tw.state; return s.loaded || s.dead; }, null, { timeout: 60_000 }).catch(() => {});
  let pub = await waitStatus(page, ['done', 'awaiting'], 20_000);
  // Before any input: does the chat input still have the focus?
  const chatFocused = await page.evaluate(() => document.activeElement?.id === 'conf-chat-input');
  const focusHolder = await page.evaluate(() => { const a = document.activeElement; return a ? a.tagName.toLowerCase() + (a.id ? '#' + a.id : '') + (a.className && typeof a.className === 'string' ? '.' + a.className.split(' ')[0] : '') : 'none'; });
  let skippedNames: string[] = [];
  let skippedActions: number[] = [];
  if (w.meta.actions?.length && pub) {
    const wrap = page.locator('[data-conf-el]');
    ({ skipped: skippedNames, indices: skippedActions } = await perform(page, w.meta.actions, {
      origin: async () => { const b = await wrap.boundingBox(); return { x: b?.x ?? 0, y: b?.y ?? 0 }; },
      setTheme: async (t) => { await page.evaluate((t) => (window as any).__tw.setTheme(t), t); },
      setVisible: async (v) => { await page.evaluate((v) => (window as any).__tw.setVisible(v), v); },
      tapOnly: canvasRenderer(renderer) ? async (x, y) => { await page.evaluate(([x, y]) => (window as any).__tw.tap(x, y), [x, y]); } : undefined,
    }, () => published(page)));
    pub = await waitStatus(page, ['done'], skippedNames.length ? 3_000 : 10_000);
  }
  // Raw checks: report.mjs skips the ones fed by skippedActions (input this renderer cannot receive).
  const checks = pub?.checks ?? [];
  if (canvasRenderer(renderer)) await page.waitForFunction((r) => ((window as any).__tw[r]?.paints ?? 0) > 0, renderer, { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(250);
  const state = await page.evaluate(() => { const s = (window as any).__tw.state; return { loaded: s.loaded, dead: s.dead, diags: s.diags, logs: s.logs, bridge: s.bridge, height: s.height }; });

  const dir = path.join(OUT, renderer, w.set);
  fs.mkdirSync(dir, { recursive: true });
  const tag = `${w.stem}.${theme}.${width}`;
  fs.mkdirSync(path.dirname(path.join(dir, tag)), { recursive: true }); // grouped widgets (visual/sdk/…)
  const stages: { name: string; crop: string }[] = [];
  /** For the console only: the run file carries the crops, report.mjs judges them. */
  const preview: string[] = [];
  if (w.set === 'auto' && w.stages.length) {
    // The renderer element can sit at a fractional CSS position; its content is snapped to device pixels,
    // an element screenshot is clipped at whole CSS pixels: crop from the viewport at the snapped origin.
    const box = await page.locator('[data-conf-el]').boundingBox();
    const shot = PNG.sync.read(await page.screenshot({ animations: 'allow', caret: 'hide' }));
    const ox = Math.round((box?.x ?? 0) * DPR), oy = Math.round((box?.y ?? 0) * DPR);
    w.stages.forEach((s, i) => {
      if (!s.themes.includes(theme)) return;
      const pos = autoStagePos(i);
      const got = crop(shot, pos[0], pos[1], s.w, s.h, DPR, ox, oy);
      const file = path.join(dir, `${tag}.${s.name}.png`);
      fs.writeFileSync(file, PNG.sync.write(got));
      stages.push({ name: s.name, crop: path.relative(OUT, file) });
      const refs = ['chromium', 'webkit'].map((engine) => ({ engine, file: refPath(w, s.name, engine, theme) })).filter((r) => fs.existsSync(r.file)).map((r) => ({ engine: r.engine, png: PNG.sync.read(fs.readFileSync(r.file)) }));
      const j = judgeStage(got, refs, s);
      preview.push(`${s.name} ${j.diff === null ? '?' : (j.diff * 100).toFixed(1) + '%'}${j.status === 'pass' ? '' : ' ' + j.status}`);
    });
  }

  const run: WidgetRun = {
    id: w.id, set: w.set, renderer, theme, width,
    loaded: !!state.loaded && !state.dead, dead: state.dead, status: pub?.status ?? 'no result', ms: Date.now() - t0,
    checks, stages, skippedActions,
    observed: { bridge: state.bridge, height: state.height, chatFocused, focusHolder, syntheticInput: canvasRenderer(renderer) },
    errors: (state.logs as { level: string; text: string }[]).filter((l) => l.level === 'error').map((l) => String(l.text).split('\n')[0].slice(0, 200)),
    diags: Object.fromEntries(Object.entries(state.diags as Record<string, { count: number }>).map(([k, v]) => [k, v.count])),
    stageRects: pub?.stages,
  };
  fs.writeFileSync(path.join(dir, `${tag}.json`), JSON.stringify(run, null, 1));
  return Object.assign(run, { preview });
}

async function main() {
  const widgets = listWidgets(ONLY).filter((w) => SETS.includes(w.set));
  const browser = await chromium.launch();
  for (const renderer of RENDERERS) {
    const ctx = await browser.newContext({ viewport: { width: 1400, height: 1100 }, deviceScaleFactor: DPR });
    const page = await ctx.newPage();
    const runs: WidgetRun[] = [];
    // --themes widens the default; a widget that declares its themes (a scenario that starts in light) keeps to them.
    const themes = (w: Widget) => (w.meta.themes ?? (THEMES ? THEMES.split(',') : w.stages.some((s) => s.themes.includes('dark')) ? ['light', 'dark'] : ['light'])) as ('light' | 'dark')[];
    let current = '';
    let available = true;
    for (const w of widgets) {
      for (const theme of themes(w)) {
        for (const width of WIDTHS) {
          if (current !== theme) { await setup(page, renderer, theme); current = theme; }
          try {
            const r = await runWidget(page, w, renderer, theme, width);
            if (canvasRenderer(renderer) && r.diags[`${renderer}-init`] && !r.loaded) available = false;
            const { preview: _p, ...entry } = r as WidgetRun & { preview: string[] };
            runs.push(entry);
            const n = r.checks.reduce((m: Record<string, number>, c) => ({ ...m, [c[2]]: (m[c[2]] ?? 0) + 1 }), {});
            const st = (r as WidgetRun & { preview: string[] }).preview.join(', ');
            console.log(`${renderer.padEnd(8)} ${w.id.padEnd(28)} ${theme.padEnd(5)} ${width} ${r.status.padEnd(8)} ${Object.entries(n).map(([k, v]) => `${k} ${v}`).join(' ')}${st ? ' | ' + st : ''}${r.dead ? ' DEAD ' + r.dead.slice(0, 80) : ''}${r.errors.length ? ` | ${r.errors.length} errors` : ''}`);
          } catch (e) {
            console.log(`${renderer} ${w.id} ${theme} ${width}: runner error ${(e as Error).message.split('\n')[0]}`);
            current = '';
          }
        }
      }
      if (!available) { console.log(`${renderer}: not available (wasm not built?), skipping the rest`); break; }
    }
    fs.mkdirSync(OUT, { recursive: true });
    // A partial run (--only, --set) updates its entries and keeps the rest of the renderer's last run.
    const prevFile = path.join(OUT, `${renderer}.json`);
    if (fs.existsSync(prevFile)) {
      const key = (r: WidgetRun) => `${r.id}|${r.theme}|${r.width}`;
      const fresh = new Set(runs.map(key));
      const prev = (JSON.parse(fs.readFileSync(prevFile, 'utf8')).runs ?? []) as WidgetRun[];
      runs.unshift(...prev.filter((r) => !fresh.has(key(r))));
      runs.sort((a, b) => key(a).localeCompare(key(b)));
    }
    fs.writeFileSync(path.join(OUT, `${renderer}.json`), JSON.stringify({ renderer, platform: 'web', engine: `Chromium ${browser.version()} (headless, Playwright)`, framework: commit(), commit: commit(), origin: ORIGIN, features: FEATURES.split(',').filter(Boolean), policy: POLICY, date: new Date().toISOString(), runs }, null, 1));
    await ctx.close();
  }
  await browser.close();
}

main().catch((e) => { console.error(e); process.exit(1); });
