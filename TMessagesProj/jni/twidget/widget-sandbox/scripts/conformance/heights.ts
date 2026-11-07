// Content height of every widget of packages/conformance through the sandbox: the web dev app (apps/web)
// loads it through the real pipeline (QuickJS worker → validator → renderer) at a narrow bubble width, and
// the renderer's own size report (before the host clamps it to the cap) is the height. Fails when a widget
// is taller than MAX_HEIGHT (lib.ts) allows, except one marked "tall" (a test of the cap). build.ts checks
// the visual set drawn natively, and the automatic set with measureHeights() here (it starts a dev server
// for it): drawn natively an automatic widget also lists the out-of-profile checks Chromium fails. The
// height is taken before any input, so an interactive widget's pending checks are in it.
//
// Needs the dev server of this checkout:  corepack pnpm dev --port 5175
//   corepack pnpm tsx scripts/conformance/heights.ts --origin http://127.0.0.1:5175 \
//     [--renderer shadow|iframe] [--widths 320,390] [--only auto/]

import { pathToFileURL } from 'node:url';
import { chromium, type Page } from '@playwright/test';
import { MAX_HEIGHT, listWidgets, type Widget } from './lib';

/** Loads the widget at `width` and returns its unclamped content height once it has run its checks (or waits for input). */
async function measure(page: Page, w: Widget, width: number): Promise<{ height: number; status: string }> {
  await page.evaluate(({ html, name, width, safeArea }) => {
    const wrap = document.querySelector('#sandbox-wrap') as HTMLElement;
    (wrap.closest('.chat') as HTMLElement).style.width = width + 'px';
    (wrap.closest('.bubble') as HTMLElement).style.padding = '0';
    const tw = (window as any).__tw;
    const m: any = ((window as any).__heights = { height: 0, attr: null, html: null });
    // A widget drawn with the client's time over its corner is that much taller (the UA layer's body padding, its own env()).
    tw.setSafeArea(safeArea);
    tw.load(html, name);
    const host = tw.host;
    const onRenderer = host.onRenderer;
    host.onRenderer = function (msg: any) {
      if (msg?.type === 'size' && Number.isFinite(msg.height)) m.height = msg.height;
      return onRenderer.call(this, msg);
    };
    const r = host.renderer;
    const send = r.send.bind(r);
    r.send = (msg: any) => {
      if (msg?.type === 'ops') {
        for (const op of msg.ops) {
          if (op.op === 'el' && op.adopt === 'html') {
            m.html = op.id;
            for (const [n, v] of op.attrs) if (n === 'data-conformance') m.attr = v;
          } else if (op.op === 'attr' && op.id === m.html && op.name === 'data-conformance') m.attr = op.value;
        }
      }
      return send(msg);
    };
  }, { html: w.html, name: `conformance/${w.id}.html`, width, safeArea: w.meta.safeArea ?? null });
  let status = 'none';
  const t0 = Date.now();
  while (Date.now() - t0 < 10_000) {
    status = await page.evaluate(() => {
      if ((window as any).__tw.state.dead) return 'dead';
      try { return JSON.parse((window as any).__heights.attr).status; } catch { return 'none'; }
    });
    if (status === 'done' || status === 'awaiting' || status === 'dead') break;
    await page.waitForTimeout(100);
  }
  await page.waitForTimeout(400);
  return { height: Math.ceil(await page.evaluate(() => (window as any).__heights.height)), status };
}

/**
 * Measures `widgets` through the dev app at `origin` and returns the ones taller than MAX_HEIGHT allows at a
 * width up to MAX_HEIGHT.width (a "tall" widget excepted), as "<id>: <h> px at <w> px wide".
 */
export async function measureHeights(widgets: Widget[], origin: string, renderer = 'shadow', widths = [MAX_HEIGHT.width], log = console.log): Promise<string[]> {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 1100 }, deviceScaleFactor: 2 })).newPage();
  const q = new URLSearchParams({ theme: 'light', features: 'webAnimations', policy: 'strict' });
  if (renderer !== 'iframe') q.set(renderer, '1');
  await page.goto(`${origin}/?${q}`);
  await page.waitForFunction(() => !!(window as any).__tw, null, { timeout: 60_000 });

  const over: string[] = [];
  log(`${renderer}, widths ${widths.join(', ')}; at most ${MAX_HEIGHT.height} px at ${MAX_HEIGHT.width} px wide`);
  for (const w of widgets) {
    const cells: string[] = [];
    let status = '';
    for (const width of widths) {
      const r = await measure(page, w, width);
      status = r.status;
      const tooTall = width <= MAX_HEIGHT.width && r.height > MAX_HEIGHT.height;
      if (tooTall && !w.meta.tall) over.push(`${w.id}: ${r.height} px at ${width} px wide`);
      cells.push(`${String(r.height).padStart(5)}${tooTall ? (w.meta.tall ? '~' : '!') : ' '}`);
    }
    log(`${w.id.padEnd(40)} ${cells.join(' ')}  ${status}`);
  }
  await browser.close();
  return over;
}

async function main() {
  const args = process.argv.slice(2);
  const opt = (name: string) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : undefined);
  const over = await measureHeights(listWidgets(opt('only')), opt('origin') ?? 'http://127.0.0.1:5175', opt('renderer') ?? 'shadow', (opt('widths') ?? String(MAX_HEIGHT.width)).split(',').map(Number));
  if (over.length) {
    console.log(`\ntaller than ${MAX_HEIGHT.height} px (split the widget, or "tall": true for a test of the cap):`);
    for (const o of over) console.log('  ' + o);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main().catch((e) => { console.error(e); process.exit(1); });
