// The posted pictures against the pipeline: every widget of the visual set drawn through the sandbox on the web
// dev app (apps/web: QuickJS worker → validator → the shadow renderer, the web product renderer) at the reference
// width and DPR, before any input, and compared with its reference picture (refs/visual/<widget>[.dark].png, drawn
// natively by Chromium in reference mode). A picture must show what widget profile 1 draws: a feature that Chromium
// draws and the profile removes (a canvas pattern, a popover's top layer) is neutralised in reference mode, by the
// stage's <template data-ref> or by the widget itself (T.mode == 'ref'), or the two differ here. A difference is
// either such a reference or a bug of the pipeline; the pixel diff points at the rows. Stages marked
// data-illustration (motion, a state after input) are left out of both. The client-SDK group is not compared: its
// pictures show the SDK stand-in's state (a fixed clock, the chat type, the cap), not the dev app's.
// build.ts runs it after drawing the pictures, on the dev server it starts for the height gate.
//
// Needs the dev server of this checkout:  corepack pnpm dev --port 5175
//   corepack pnpm tsx scripts/conformance/picture-check.ts --origin http://127.0.0.1:5175 [--only 13-out] [--renderer shadow|iframe]

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium, type Page } from '@playwright/test';
import { PNG } from 'pngjs';
import { ROOT, listWidgets, picturePath, type Widget } from './lib';
import { diffStats } from '../../packages/conformance/tools/diff.mjs';
import { checkContentSafeArea } from '../../packages/core/src/shared/sdk-policy.ts';

/** At most this share of the ink may differ (the pipeline and the reference agree to under 0.1 % today), and the heights by this many CSS px. */
export const PICTURE_CHECK = { maxDiff: 0.005, height: 2 };
const WIDTH = 390;
const DPR = 2;
const OUT = path.join(ROOT, 'reports', 'conformance', 'pictures');

const isChecked = (w: Widget) => w.set === 'visual' && !w.id.startsWith('visual/sdk/');

/** Loads the widget at the reference width and returns its picture and the stage rects it published, once it ran its checks (or waits for input). */
async function draw(page: Page, w: Widget): Promise<{ png: PNG; stages: Record<string, number[]> }> {
  await page.evaluate(({ html, name, width, safeArea }) => {
    const wrap = document.querySelector('#sandbox-wrap') as HTMLElement;
    (wrap.closest('.chat') as HTMLElement).style.width = width + 'px';
    const bubble = wrap.closest('.bubble') as HTMLElement;
    bubble.style.padding = '0';
    bubble.style.borderRadius = '0';
    bubble.style.boxShadow = 'none';
    const tw = (window as any).__tw;
    const m: any = ((window as any).__pictures = { attr: null, html: null });
    // The client's time over the corner (the header's safeArea): the dev app passes the band and draws the time over the bubble.
    tw.setSafeArea(safeArea);
    tw.load(html, name);
    tw.host.element.setAttribute('data-conf-el', '');
    const r = tw.host.renderer;
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
  }, { html: w.html, name: `conformance/${w.id}.html`, width: WIDTH, safeArea: w.meta.safeArea ?? null });
  let pub: any = null;
  const t0 = Date.now();
  while (Date.now() - t0 < 15_000) {
    pub = await page.evaluate(() => { try { return JSON.parse((window as any).__pictures.attr); } catch { return null; } });
    if (pub && (pub.status === 'done' || pub.status === 'awaiting')) break;
    if (await page.evaluate(() => (window as any).__tw.state.dead)) break;
    await page.waitForTimeout(100);
  }
  // As long as build.ts waits before its picture.
  await page.waitForTimeout(300);
  // The renderer element can sit at a fractional CSS position: clip at the snapped origin, as run-web.ts crops.
  const box = (await page.locator('[data-conf-el]').boundingBox())!;
  const shot = await page.screenshot({ clip: { x: Math.round(box.x), y: Math.round(box.y), width: WIDTH, height: Math.max(Math.ceil(box.height), 40) }, animations: 'allow', caret: 'hide' });
  return { png: PNG.sync.read(shot), stages: pub?.stages ?? {} };
}

/** The corner box the client's time takes over the bottom of a picture `height` CSS px tall (the header's safeArea), as [x, y, w, h]. */
function timeCorner(w: Widget, height: number): number[] | null {
  const a = checkContentSafeArea(w.meta.safeArea ?? null);
  const corner = a.cornerRight || a.cornerLeft;
  if (!a.bottom || !corner) return null;
  return [a.cornerRight ? WIDTH - corner : 0, height - a.bottom, corner, a.bottom];
}

/** The top `h` device rows of `png`, with the rects (CSS px) painted white. */
function prepare(png: PNG, h: number, masks: number[][]): PNG {
  const out = new PNG({ width: png.width, height: h });
  PNG.bitblt(png, out, 0, 0, png.width, h, 0, 0);
  for (const [x, y, w, hh] of masks) {
    const x0 = Math.max(0, Math.floor(x * DPR) - 2), y0 = Math.max(0, Math.floor(y * DPR) - 2);
    const x1 = Math.min(out.width, Math.ceil((x + w) * DPR) + 2), y1 = Math.min(h, Math.ceil((y + hh) * DPR) + 2);
    for (let yy = y0; yy < y1; yy++) out.data.fill(255, (yy * out.width + x0) * 4, (yy * out.width + x1) * 4);
  }
  return out;
}

/** The CSS-px rows that hold differing pixels (red in the diff image), a few per device row at least: single ones are glyph noise. */
function diffRows(img: { width: number; height: number; data: Uint8Array }): [number, number] | null {
  let top = -1, bottom = -1;
  for (let y = 0; y < img.height; y++) {
    let n = 0;
    for (let x = 0; x < img.width; x++) {
      const p = (y * img.width + x) * 4;
      if (img.data[p] === 255 && img.data[p + 1] === 0 && img.data[p + 2] === 0) n++;
    }
    if (n >= 4) { if (top < 0) top = y; bottom = y; }
  }
  return top < 0 ? null : [Math.floor(top / DPR), Math.ceil(bottom / DPR)];
}

/**
 * Draws `widgets` (the visual set among them) through the dev app at `origin` and returns the ones whose reference
 * picture differs from the pipeline's, as "<id>[ (dark)]: what differs". The pipeline's pictures and the diffs go to
 * reports/conformance/pictures.
 */
export async function checkPictures(widgets: Widget[], origin: string, renderer = 'shadow', log = console.log): Promise<string[]> {
  const list = widgets.filter(isChecked);
  if (!list.length) return [];
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 2400 }, deviceScaleFactor: DPR })).newPage();
  const bad: string[] = [];
  fs.mkdirSync(OUT, { recursive: true });
  log(`${renderer}, ${WIDTH} px; at most ${PICTURE_CHECK.maxDiff * 100} % of the ink and ${PICTURE_CHECK.height} px of height may differ`);
  try {
    for (const theme of ['light', 'dark'] as const) {
      const todo = list.filter((w) => fs.existsSync(picturePath(w, theme)));
      if (!todo.length) continue;
      const q = new URLSearchParams({ theme, features: 'webAnimations', policy: 'strict' });
      if (renderer !== 'iframe') q.set(renderer, '1');
      await page.goto(`${origin}/?${q}`);
      await page.waitForFunction(() => !!(window as any).__tw, null, { timeout: 60_000 });
      for (const w of todo) {
        const label = `${w.id}${theme === 'dark' ? ' (dark)' : ''}`;
        const ref = PNG.sync.read(fs.readFileSync(picturePath(w, theme)));
        const got = await draw(page, w);
        const masks = w.stages.filter((s) => s.illustration && got.stages[s.name]).map((s) => got.stages[s.name]);
        const h = Math.min(ref.height, got.png.height);
        // The client's time over the corner is the client's drawing (the dev app's and the build's agree, but it is no part of the widget).
        const corner = timeCorner(w, h / DPR);
        if (corner) masks.push(corner);
        const d = diffStats(prepare(got.png, h, masks), prepare(ref, h, masks));
        const tall = Math.abs(ref.height - got.png.height) / DPR;
        const rows = diffRows(d.image);
        const problems = [
          tall > PICTURE_CHECK.height ? `the picture is ${ref.height / DPR} px tall, the pipeline draws ${got.png.height / DPR} px` : '',
          d.ink > PICTURE_CHECK.maxDiff ? `${(d.ink * 100).toFixed(1)} % of the ink differs${rows ? `, rows ${rows[0]}–${rows[1]} px` : ''}` : '',
        ].filter(Boolean);
        if (problems.length) bad.push(`${label}: ${problems.join('; ')}`);
        const file = path.join(OUT, `${w.stem}${theme === 'dark' ? '.dark' : ''}`);
        fs.writeFileSync(`${file}.png`, PNG.sync.write(got.png));
        fs.writeFileSync(`${file}.diff.png`, PNG.sync.write(Object.assign(new PNG({ width: d.image.width, height: d.image.height }), { data: d.image.data })));
        log(`${label.padEnd(40)} ${(d.ink * 100).toFixed(2).padStart(6)} %  ${String(got.png.height / DPR).padStart(4)} / ${String(ref.height / DPR).padStart(4)} px${masks.length ? `  (${masks.length} illustrated)` : ''}${problems.length ? '  DIFFERS' : ''}`);
      }
    }
  } finally {
    await browser.close();
  }
  return bad;
}

async function main() {
  const args = process.argv.slice(2);
  const opt = (name: string) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : undefined);
  const bad = await checkPictures(listWidgets(opt('only')), opt('origin') ?? 'http://127.0.0.1:5175', opt('renderer') ?? 'shadow');
  if (bad.length) {
    console.log(`\nreference pictures that differ from the widget drawn through the sandbox (diffs in ${path.relative(process.cwd(), OUT)}):`);
    for (const b of bad) console.log('  ' + b);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main().catch((e) => { console.error(e); process.exit(1); });
