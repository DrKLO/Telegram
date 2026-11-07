// Runs the web feed's bench (src/feed-bench.ts) in headless Chromium and writes
// the report: the JSON the tables are rendered from and the same tables as
// Markdown — the web twin of the iOS `--autorun feed` + feed-report.json.
//   corepack pnpm tsx scripts/feed-report.ts [--passes 5] [--count 60] [--combos iframe:on,shadow:on,iframe:off,shadow:off]
//                                            [--no-prewarm] [--theme dark] [--origin http://127.0.0.1:5174] [--out docs/feed-report-web]
// Needs the dev server (corepack pnpm dev [--port 5174]). The frozen canvas prototypes (blitz, litehtml) run only when named
// in --combos and built (pnpm build:blitz-web / build:litehtml-web; docs/decisions.md, "The renderer prototypes are frozen").
// Headless Chromium exposes measureUserAgentSpecificMemory (the page is cross-origin isolated), so the memory row counts
// workers and frames there; the window is 1280×900 so the scroller is a desktop screen.

import { chromium } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const argv = process.argv.slice(2);
const opt = (name: string, def: string) => { const i = argv.indexOf(name); return i >= 0 && argv[i + 1] ? argv[i + 1] : def; };
const passes = Number(opt('--passes', '5'));
const count = Number(opt('--count', '60'));
const combos = opt('--combos', 'iframe:on,shadow:on,iframe:off,shadow:off');
const prewarm = !argv.includes('--no-prewarm');
const theme = opt('--theme', 'light');
const origin = opt('--origin', 'http://127.0.0.1:5174');
const out = opt('--out', 'docs/feed-report-web');

// --expose-gc: the bench calls window.gc() before its performance.memory readings (no garbage in the memory row).
const browser = await chromium.launch({ args: ['--js-flags=--expose-gc'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
page.on('console', (m) => { if (m.type() === 'error') console.error('[page]', m.text()); });
// Resident memory of the whole browser (every process CDP lists: browser, renderers, isolated frames, GPU, utility), in bytes.
// The bench reads it at the baseline and the end of each pass through `__twProcessMemory` (src/feed-bench.ts, measureMemory).
const cdp = await browser.newBrowserCDPSession();
async function processMemory(): Promise<number | null> {
  const { processInfo } = (await cdp.send('SystemInfo.getProcessInfo')) as { processInfo: { type: string; id: number }[] };
  if (!processInfo.length) return null;
  const out = execFileSync('ps', ['-o', 'rss=', '-p', processInfo.map((p) => p.id).join(',')], { encoding: 'utf8' });
  return out.trim().split('\n').reduce((a, l) => a + (Number(l.trim()) || 0) * 1024, 0);
}
await page.exposeFunction('__twProcessMemory', () => processMemory());
const url = `${origin}/?feed=1&corpus=common&hostile=none&count=${count}&bench=open&passes=${passes}&benchCount=${count}&combos=${combos}${prewarm ? '' : '&prewarm=0'}${theme === 'dark' ? '&theme=dark' : ''}`;
await page.goto(url);
await page.waitForFunction(() => typeof (window as any).__tw?.feed?.bench?.run === 'function');
console.error(`running ${combos} × ${passes} passes, ${count} bubbles …`);
const started = Date.now();
const progress = setInterval(async () => {
  const text = await page.evaluate(() => document.querySelector('.bench-progress')?.textContent ?? '').catch(() => '');
  console.error(`  ${Math.round((Date.now() - started) / 1000)} s: ${text}`);
}, 30_000);
await page.evaluate(() => (window as any).__tw.feed.bench.run());
clearInterval(progress);
const json: string = await page.evaluate(() => (window as any).__tw.feed.bench.json());
const md: string = await page.evaluate(() => (window as any).__tw.feed.bench.markdown());
await browser.close();

fs.writeFileSync(path.resolve(root, out + '.json'), json);
fs.writeFileSync(path.resolve(root, out + '.md'), md);
console.log(md);
console.error(`→ ${out}.json, ${out}.md`);
