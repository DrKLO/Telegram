// Blitz experiment (docs/blitz-experiment.md), step 1.
// Dump the post-JS DOM of every generated widget from the dev UI's reference
// frame (native Chromium rendering of the corpus file), at the same moment the
// reference screenshot is taken: load, wait, click up to 4 buttons, wait.
// Output per widget: <name>.reference.png, <name>.html (static DOM: canvases
// replaced by their bitmap, form state baked into attributes, scripts/links
// removed), <name>.meta.json (frame width/height).
import { chromium, type FrameLocator, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const genDir = path.join(root, 'corpus', 'generated');
const outDir = process.argv[2];
if (!outDir) throw new Error('usage: corepack pnpm tsx scripts/blitz-experiment/dump-dom.mts <outDir> [filter]  (dev server on 127.0.0.1:5173 must be running)');
const filter = process.argv[3] ?? '';
fs.mkdirSync(outDir, { recursive: true });

const files: string[] = [];
for (const batch of fs.readdirSync(genDir).sort()) {
  const dir = path.join(genDir, batch);
  if (!fs.statSync(dir).isDirectory()) continue;
  for (const f of fs.readdirSync(dir).sort()) if (f.endsWith('.html') && f.includes(filter)) files.push(`${batch}/${f}`);
}

async function waitReady(page: Page) {
  await page.waitForFunction('(() => { const s = window.__tw && window.__tw.state; return !!(s && (s.loaded || s.dead)); })()', null, { timeout: 60_000 });
  await page.waitForTimeout(600);
}

async function poke(frame: FrameLocator, page: Page): Promise<number> {
  const buttons = frame.locator('button:visible, [role=button]:visible, input[type=button]:visible, input[type=submit]:visible');
  const n = Math.min(await buttons.count().catch(() => 0), 4);
  for (let i = 0; i < n; i++) {
    try { await buttons.nth(i).click({ timeout: 2000, force: true }); } catch {}
    await page.waitForTimeout(150);
  }
  return n;
}

// Browser-side code as plain JS strings (tsx's keepNames would inject __name
// helpers into functions passed to page.evaluate).
const BLUR = `(() => { try { const d = document.querySelector('#reference-wrap iframe').contentDocument; if (d && d.activeElement && d.activeElement.blur) d.activeElement.blur(); } catch (e) {} })()`;
const DUMP = `(() => {
  const f = document.querySelector('#reference-wrap iframe');
  const d = f.contentDocument;
  const clone = d.documentElement.cloneNode(true);
  const copyAttrs = (from, to) => { for (const a of Array.from(from.attributes)) if (a.name !== 'width' && a.name !== 'height') to.setAttribute(a.name, a.value); };
  // Canvas -> its bitmap. If Blitz were the renderer, canvas would be our own
  // command-stream backend anyway; the bitmap stands in for it.
  const origC = d.querySelectorAll('canvas'), cloneC = clone.querySelectorAll('canvas');
  cloneC.forEach((c, i) => {
    const o = origC[i];
    const img = d.createElement('img');
    copyAttrs(o, img);
    try { img.setAttribute('src', o.toDataURL('image/png')); } catch (e) {}
    img.style.width = o.clientWidth + 'px';
    img.style.height = o.clientHeight + 'px';
    c.replaceWith(img);
  });
  // Form state -> attributes.
  const origF = d.querySelectorAll('input,textarea,select'), cloneF = clone.querySelectorAll('input,textarea,select');
  cloneF.forEach((el, i) => {
    const o = origF[i];
    if (o.tagName === 'INPUT') {
      if (o.type === 'checkbox' || o.type === 'radio') { if (o.checked) el.setAttribute('checked', ''); else el.removeAttribute('checked'); }
      else el.setAttribute('value', o.value);
    } else if (o.tagName === 'TEXTAREA') el.textContent = o.value;
    else if (o.tagName === 'SELECT') {
      const oo = o.options, co = el.options;
      for (let k = 0; k < co.length; k++) { if (oo[k] && oo[k].selected) co[k].setAttribute('selected', ''); else co[k].removeAttribute('selected'); }
    }
  });
  clone.querySelectorAll('script,link,noscript').forEach((s) => s.remove());
  // Only data: images reach either renderer: Chromium's CSP blocks the rest,
  // so take the src away rather than let a static renderer fetch it.
  clone.querySelectorAll('img,source,video,audio').forEach((el) => { const v = el.getAttribute('src') || ''; if (v && !v.startsWith('data:')) el.removeAttribute('src'); el.removeAttribute('srcset'); });
  return {
    html: '<!DOCTYPE html>\\n' + clone.outerHTML,
    width: f.clientWidth,
    height: f.clientHeight,
    docHeight: Math.ceil(d.documentElement.getBoundingClientRect().height),
    bodyBg: getComputedStyle(d.body).backgroundColor,
    canvases: origC.length,
    svgs: d.querySelectorAll('svg').length,
    inputs: origF.length,
  };
})()`;

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
const page = await context.newPage();
for (const file of files) {
  const name = file.replace('/', '__');
  await page.goto(`http://127.0.0.1:5173/?file=generated/${file}&reference=1`);
  await waitReady(page);
  await page.waitForTimeout(800);
  const sel = '#reference-wrap iframe';
  const clicks = await poke(page.frameLocator(sel), page);
  // No :hover / :focus in the snapshot: a static renderer has neither.
  await page.mouse.move(0, 0);
  await page.evaluate(BLUR);
  await page.waitForTimeout(500);
  const buf = await page.locator(sel).screenshot({ animations: 'disabled', caret: 'hide' });
  fs.writeFileSync(path.join(outDir, `${name}.reference.png`), buf);
  const dump: any = await page.evaluate(DUMP);
  fs.writeFileSync(path.join(outDir, `${name}.html`), dump.html);
  fs.writeFileSync(path.join(outDir, `${name}.meta.json`), JSON.stringify({ ...dump, html: undefined, clicks, file }, null, 2));
  console.log(`${file}: ${dump.width}x${dump.height} (doc ${dump.docHeight}) clicks=${clicks} canvas=${dump.canvases} svg=${dump.svgs} inputs=${dump.inputs}`);
}
await browser.close();
