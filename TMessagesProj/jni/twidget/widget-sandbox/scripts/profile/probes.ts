// Widget profile, step 1: which CSS/HTML features each renderer actually
// supports, measured rather than assumed. Every probe is a tiny widget whose
// feature, when supported, puts a known colour at a known pixel; it goes
// through the real pipeline (QuickJS sandbox → validator → renderer) in the
// desktop harness (`twidget-run --blitz | --litehtml --png`, built with
// -DTW_BLITZ=ON -DTW_LITEHTML=ON), and through the validator alone under Node
// to see whether the validator itself keeps the feature (what the product
// iframe/WebView would get). Output: reports/profile/probes.json and a
// Markdown table on stdout, pasted into docs/widget-profile-draft.md (historical; the profile is docs/profile-v1.md).
//
//   corepack pnpm tsx scripts/profile/probes.ts [--only grid,flex-gap]

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { runWidget } from '@telegram/widget-sandbox/node';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const run = path.join(root, 'packages', 'desktop', 'build', 'twidget-run');
const out = path.join(root, 'reports', 'profile');
const probeDir = path.join(out, 'probes');
fs.mkdirSync(probeDir, { recursive: true });

type Color = 'red' | 'blue' | 'white' | 'pink' | 'gray' | 'notwhite';
interface Check { x: number; y: number; is: Color }
/** A region where some pixel must be (or none may be) red / non-white. */
interface Region { x0: number; y0: number; x1: number; y1: number; has: 'red' | 'notwhite'; expect: boolean }
interface Probe {
  key: string;
  /** What the product (iframe / WebView) does with it; the probe's reason to exist. */
  feature: string;
  html: string;
  checks?: Check[];
  regions?: Region[];
  /** A token that must survive in the validated CSS/ops for the validator to count as "keeps" (default: feature used in a style). */
  keep?: RegExp;
}

const BASE = '<style>html,body{margin:0;padding:0;background:#fff}</style>';
const box = (style: string, inner = '') => `<div style="width:100px;height:50px;background:#f00;${style}">${inner}</div>`;
const redPng = (() => {
  const png = new PNG({ width: 2, height: 2 });
  for (let i = 0; i < 16; i += 4) { png.data[i] = 255; png.data[i + 1] = 0; png.data[i + 2] = 0; png.data[i + 3] = 255; }
  return 'data:image/png;base64,' + PNG.sync.write(png).toString('base64');
})();

const PROBES: Probe[] = [
  { key: 'grid', feature: 'display: grid (two columns)', html: '<div style="display:grid;grid-template-columns:1fr 1fr"><div style="height:50px;background:#00f"></div><div style="height:50px;background:#f00"></div></div>', checks: [{ x: 225, y: 25, is: 'red' }, { x: 75, y: 25, is: 'blue' }], keep: /grid-template-columns/ },
  { key: 'flex-gap', feature: 'flex + gap', html: '<div style="display:flex;gap:20px"><div style="width:40px;height:40px;background:#f00"></div><div style="width:40px;height:40px;background:#f00"></div></div>', checks: [{ x: 50, y: 20, is: 'white' }, { x: 70, y: 20, is: 'red' }], keep: /gap/ },
  { key: 'box-shadow', feature: 'box-shadow', html: '<div style="width:50px;height:50px;background:#00f;box-shadow:100px 0 0 0 #f00"></div>', checks: [{ x: 125, y: 25, is: 'red' }], keep: /box-shadow/ },
  { key: 'opacity', feature: 'opacity', html: box('opacity:.5'), checks: [{ x: 50, y: 25, is: 'pink' }], keep: /opacity/ },
  { key: 'transform-2d', feature: 'transform: translateX', html: '<div style="width:50px;height:50px;background:#f00;transform:translateX(100px)"></div>', checks: [{ x: 125, y: 25, is: 'red' }, { x: 25, y: 25, is: 'white' }], keep: /translateX/ },
  { key: 'transform-3d', feature: 'transform: rotateY(180deg) + backface-visibility: hidden (card flip)', html: box('transform:rotateY(180deg);backface-visibility:hidden'), checks: [{ x: 50, y: 25, is: 'white' }], keep: /rotateY/ },
  { key: 'overflow-clip', feature: 'overflow: hidden clips', html: '<div style="width:100px;height:50px;overflow:hidden"><div style="width:100px;height:200px;background:#f00"></div></div>', checks: [{ x: 50, y: 100, is: 'white' }, { x: 50, y: 25, is: 'red' }], keep: /overflow/ },
  { key: 'overflow-auto-clip', feature: 'overflow: auto clips (scrolling itself is interactive, not probed)', html: '<div style="width:100px;height:50px;overflow:auto"><div style="width:80px;height:200px;background:#f00"></div></div>', checks: [{ x: 40, y: 100, is: 'white' }, { x: 40, y: 25, is: 'red' }], keep: /overflow/ },
  { key: 'border-radius-clip', feature: 'border-radius + overflow: hidden rounds children', html: '<div style="width:100px;height:100px;border-radius:50px;overflow:hidden"><div style="width:100px;height:100px;background:#f00"></div></div>', checks: [{ x: 3, y: 3, is: 'white' }, { x: 50, y: 50, is: 'red' }], keep: /border-radius/ },
  { key: 'svg-inline', feature: 'inline <svg> with presentation attributes', html: '<svg width="100" height="50"><rect width="100" height="50" fill="#f00"/></svg>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /"svg"/ },
  { key: 'svg-css', feature: 'inline <svg> styled from CSS (fill in a rule)', html: '<style>.r{fill:#f00}</style><svg width="100" height="50"><rect class="r" width="100" height="50"/></svg>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /fill/ },
  { key: 'img-png', feature: '<img src="data:image/png">', html: `<img src="${redPng}" style="width:100px;height:50px;display:block">`, checks: [{ x: 50, y: 25, is: 'red' }], keep: /data:image\/png/ },
  { key: 'img-svg', feature: '<img src="data:image/svg+xml">', html: `<img src="data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="#f00"/></svg>')}" style="width:100px;height:50px;display:block">`, checks: [{ x: 50, y: 25, is: 'red' }], keep: /svg\+xml/ },
  { key: 'bg-image', feature: 'background-image: url(data:png)', html: box(`background:url(${redPng}) 0 0/100px 50px no-repeat`), checks: [{ x: 50, y: 25, is: 'red' }], keep: /data:image\/png/ },
  { key: 'canvas', feature: '<canvas> 2D (fillRect through the command stream)', html: '<canvas id="c" width="100" height="50" style="display:block"></canvas><script>var x=document.getElementById("c").getContext("2d");x.fillStyle="#f00";x.fillRect(0,0,100,50)</script>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /"canvas"/ },
  { key: 'input-value', feature: '<input> draws its value', html: '<input value="MMMMMMMMMM" style="width:200px;height:30px;font-size:20px;color:#000;border:0;padding:0;background:#fff;display:block">', regions: [{ x0: 2, y0: 2, x1: 120, y1: 28, has: 'notwhite', expect: true }], keep: /value/ },
  { key: 'select', feature: '<select> shows the selected option', html: '<select style="width:200px;height:30px;font-size:20px;color:#000;border:0;padding:0;background:#fff;display:block"><option>MMMMMMMM</option></select>', regions: [{ x0: 2, y0: 2, x1: 120, y1: 28, has: 'notwhite', expect: true }], keep: /"select"/ },
  { key: 'range', feature: '<input type=range> draws a track/thumb', html: '<input type="range" style="width:200px;display:block">', regions: [{ x0: 0, y0: 0, x1: 200, y1: 30, has: 'notwhite', expect: true }], keep: /range/ },
  { key: 'checkbox', feature: '<input type=checkbox checked> draws a mark', html: '<input type="checkbox" checked style="display:block;margin:0">', regions: [{ x0: 0, y0: 0, x1: 20, y1: 20, has: 'notwhite', expect: true }], keep: /checkbox/ },
  { key: 'button', feature: '<button> has a visible box', html: '<button style="width:100px;height:40px;display:block;background:#f00;border:0">b</button>', checks: [{ x: 50, y: 20, is: 'red' }], keep: /"button"/ },
  { key: 'aspect-ratio', feature: 'aspect-ratio', html: '<div style="width:100px;aspect-ratio:1;background:#f00"></div>', checks: [{ x: 50, y: 90, is: 'red' }], keep: /aspect-ratio/ },
  { key: 'clamp', feature: 'clamp() in width', html: '<div style="width:clamp(50px,200px,100px);height:50px;background:#f00"></div>', checks: [{ x: 75, y: 25, is: 'red' }, { x: 150, y: 25, is: 'white' }], keep: /clamp/ },
  { key: 'calc', feature: 'calc() in width', html: '<div style="width:calc(50px + 50px);height:50px;background:#f00"></div>', checks: [{ x: 75, y: 25, is: 'red' }, { x: 150, y: 25, is: 'white' }], keep: /calc/ },
  { key: 'var', feature: 'var(--x)', html: '<style>:root{--w:100px}</style><div style="width:var(--w);height:50px;background:#f00"></div>', checks: [{ x: 75, y: 25, is: 'red' }, { x: 150, y: 25, is: 'white' }], keep: /--w/ },
  { key: 'vw', feature: 'vw unit (viewport = bubble width)', html: '<div style="width:50vw;height:50px;background:#f00"></div>', checks: [{ x: 100, y: 25, is: 'red' }, { x: 200, y: 25, is: 'white' }], keep: /vw/ },
  { key: 'light-dark', feature: 'light-dark() colour (light theme; the validator resolves it to the theme colour, so "drops" here means "resolved")', html: '<style>:root{color-scheme:light dark}</style><div style="width:100px;height:50px;background:light-dark(#f00,#00f)"></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /light-dark/ },
  { key: 'color-mix', feature: 'color-mix()', html: '<div style="width:100px;height:50px;background:color-mix(in srgb,#f00,#f00)"></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /color-mix/ },
  { key: 'filter', feature: 'filter: grayscale(1)', html: box('filter:grayscale(1)'), checks: [{ x: 50, y: 25, is: 'gray' }], keep: /grayscale/ },
  { key: 'pseudo-before', feature: '::before with content', html: '<style>.b::before{content:"";display:block;width:100px;height:50px;background:#f00}</style><div class="b"></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /before/ },
  { key: 'nth-child', feature: ':nth-child(2)', html: '<style>.l div{height:50px}.l div:nth-child(2){background:#f00}</style><div class="l"><div></div><div></div></div>', checks: [{ x: 50, y: 75, is: 'red' }, { x: 50, y: 25, is: 'white' }], keep: /nth-child/ },
  { key: 'is-selector', feature: ':is()', html: '<style>:is(.a) .b{width:100px;height:50px;background:#f00}</style><div class="a"><div class="b"></div></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /:is\(/ },
  { key: 'has-selector', feature: ':has()', html: '<style>.a:has(.b){width:100px;height:50px;background:#f00}</style><div class="a"><span class="b"></span></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /:has\(/ },
  { key: 'nesting', feature: 'CSS nesting (.a { .b { } })', html: '<style>.a{.b{width:100px;height:50px;background:#f00}}</style><div class="a"><div class="b"></div></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /\.b\{/ },
  { key: 'supports', feature: '@supports (display: grid)', html: '<style>@supports (display:grid){.a{width:100px;height:50px;background:#f00}}</style><div class="a"></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /@supports/ },
  { key: 'media-width', feature: '@media (min-width: 200px) (bubble is 300 wide)', html: '<style>@media (min-width:200px){.a{width:100px;height:50px;background:#f00}}</style><div class="a"></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /min-width/ },
  { key: 'keyframes-forwards', feature: '@keyframes + animation 0s forwards (end state applied)', html: '<style>@keyframes a{to{background:#f00}}</style><div style="width:100px;height:50px;background:#00f;animation:a 0s forwards"></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /@keyframes/ },
  { key: 'position-absolute', feature: 'position: absolute inside relative', html: '<div style="position:relative;height:50px"><div style="position:absolute;left:150px;top:0;width:50px;height:50px;background:#f00"></div></div>', checks: [{ x: 175, y: 25, is: 'red' }], keep: /absolute/ },
  { key: 'z-index', feature: 'z-index stacking (earlier box on top)', html: '<div style="position:relative;height:50px"><div style="position:absolute;z-index:2;width:50px;height:50px;background:#f00"></div><div style="position:absolute;width:50px;height:50px;background:#00f"></div></div>', checks: [{ x: 25, y: 25, is: 'red' }], keep: /z-index/ },
  { key: 'visibility-hidden', feature: 'visibility: hidden', html: box('visibility:hidden'), checks: [{ x: 50, y: 25, is: 'white' }], keep: /visibility/ },
  { key: 'logical-margin', feature: 'margin-inline-start (logical property)', html: box('margin-inline-start:100px'), checks: [{ x: 50, y: 25, is: 'white' }, { x: 150, y: 25, is: 'red' }], keep: /margin-inline-start/ },
  { key: 'clip-path', feature: 'clip-path: inset()', html: box('clip-path:inset(0 50% 0 0)'), checks: [{ x: 25, y: 25, is: 'red' }, { x: 75, y: 25, is: 'white' }], keep: /clip-path/ },
  { key: 'linear-gradient', feature: 'linear-gradient', html: '<div style="width:200px;height:50px;background:linear-gradient(90deg,#f00 0,#f00 50%,#00f 50%,#00f 100%)"></div>', checks: [{ x: 50, y: 25, is: 'red' }, { x: 150, y: 25, is: 'blue' }], keep: /linear-gradient/ },
  { key: 'radial-gradient', feature: 'radial-gradient', html: '<div style="width:100px;height:100px;background:radial-gradient(circle at center,#f00 0 20%,#00f 21%)"></div>', checks: [{ x: 50, y: 50, is: 'red' }, { x: 5, y: 5, is: 'blue' }], keep: /radial-gradient/ },
  { key: 'conic-gradient', feature: 'conic-gradient', html: '<div style="width:100px;height:100px;background:conic-gradient(#f00 0 25%,#00f 25% 100%)"></div>', checks: [{ x: 75, y: 15, is: 'red' }, { x: 25, y: 75, is: 'blue' }], keep: /conic-gradient/ },
  { key: 'text-shadow', feature: 'text-shadow', html: '<div style="font:40px sans-serif;color:#fff;text-shadow:60px 0 0 #f00;width:200px;height:50px">MMMM</div>', regions: [{ x0: 60, y0: 0, x1: 200, y1: 50, has: 'red', expect: true }], keep: /text-shadow/ },
  { key: 'emoji', feature: 'colour emoji glyph (host font; measured on macOS)', html: '<div style="font:40px sans-serif;height:50px">🎨</div>', regions: [{ x0: 0, y0: 0, x1: 50, y1: 50, has: 'notwhite', expect: true }] },
  { key: 'details-open', feature: '<details open> shows its content', html: '<details open><summary style="height:20px">s</summary><div style="width:100px;height:50px;background:#f00"></div></details>', regions: [{ x0: 0, y0: 20, x1: 100, y1: 90, has: 'red', expect: true }], keep: /details/ },
  { key: 'details-closed', feature: '<details> (closed) hides its content', html: '<details><summary style="height:20px">s</summary><div style="width:100px;height:50px;background:#f00"></div></details>', regions: [{ x0: 0, y0: 0, x1: 100, y1: 100, has: 'red', expect: false }], keep: /details/ },
  { key: 'table', feature: '<table>: cells sized by CSS width/height (litehtml honours the width, ignores the height)', html: '<p style="margin:0;height:20px">t</p><table style="border-collapse:collapse;color:transparent"><tr><td style="width:100px;height:50px;background:#00f;padding:0">a</td><td style="width:100px;height:50px;background:#f00;padding:0">b</td></tr></table>', checks: [{ x: 150, y: 45, is: 'red' }], keep: /"table"/ },
  { key: 'table-first', feature: '<table> as the first block in <body> (same, without a preceding paragraph)', html: '<table style="border-collapse:collapse;color:transparent"><tr><td style="width:100px;height:50px;background:#00f;padding:0">a</td><td style="width:100px;height:50px;background:#f00;padding:0">b</td></tr></table>', checks: [{ x: 150, y: 25, is: 'red' }], keep: /"table"/ },
  { key: 'sticky', feature: 'position: sticky (lays out in flow; stickiness is interactive, not probed)', html: '<div style="height:50px"><div style="position:sticky;top:0;width:100px;height:50px;background:#f00"></div></div>', checks: [{ x: 50, y: 25, is: 'red' }], keep: /sticky/ },
  { key: 'pre-wrap', feature: 'white-space: pre (line break kept → second line)', html: '<div style="white-space:pre;font:20px/50px sans-serif;color:#fff"><span style="background:#00f">A</span>\n<span style="background:#f00">B</span></div>', checks: [{ x: 5, y: 75, is: 'red' }], keep: /pre/ },
  { key: 'word-wrap', feature: 'overflow-wrap: anywhere breaks a long word', html: '<div style="width:60px;font:20px/25px monospace;overflow-wrap:anywhere;color:#fff"><span style="background:#f00">MMMMMMMMMMMMMMMMMMMM</span></div>', regions: [{ x0: 0, y0: 30, x1: 60, y1: 50, has: 'red', expect: true }], keep: /overflow-wrap/ },
];

function classify(r: number, g: number, b: number, a: number): Color[] {
  if (a < 20) return ['white'];
  const c: Color[] = [];
  const isRed = r > 200 && g < 60 && b < 60;
  const isBlue = b > 200 && r < 60 && g < 60;
  const isWhite = r > 235 && g > 235 && b > 235;
  if (isRed) c.push('red');
  if (isBlue) c.push('blue');
  if (isWhite) c.push('white');
  if (r > 200 && g > 90 && g < 170 && b > 90 && b < 170) c.push('pink');
  if (Math.abs(r - g) < 25 && Math.abs(g - b) < 25 && r < 200) c.push('gray');
  if (!isWhite) c.push('notwhite');
  return c;
}

interface EngineResult { pass: boolean | null; detail: string }

function judge(png: PNG | null, p: Probe): EngineResult {
  if (!png) return { pass: null, detail: 'no raster' };
  const px = (x: number, y: number) => {
    if (x >= png.width || y >= png.height) return [255, 255, 255, 0];
    const i = (y * png.width + x) * 4;
    return [png.data[i], png.data[i + 1], png.data[i + 2], png.data[i + 3]];
  };
  const fails: string[] = [];
  for (const c of p.checks ?? []) {
    const [r, g, b, a] = px(c.x, c.y);
    if (!classify(r, g, b, a).includes(c.is)) fails.push(`(${c.x},${c.y}) = rgba(${r},${g},${b},${a}), wanted ${c.is}`);
  }
  for (const rg of p.regions ?? []) {
    let found = false;
    for (let y = rg.y0; y < Math.min(rg.y1, png.height) && !found; y++)
      for (let x = rg.x0; x < Math.min(rg.x1, png.width); x++) {
        const [r, g, b, a] = px(x, y);
        if (classify(r, g, b, a).includes(rg.has)) { found = true; break; }
      }
    if (found !== rg.expect) fails.push(`${rg.has} ${rg.expect ? 'missing' : 'present'} in [${rg.x0},${rg.y0}..${rg.x1},${rg.y1}]`);
  }
  return { pass: fails.length === 0, detail: fails.join('; ') };
}

function render(engine: 'blitz' | 'litehtml', file: string, png: string): PNG | null {
  const r = spawnSync(run, [file, `--${engine}`, '--dpr', '1', '--width', '300', '--wait-ms', '400', '--png', png], { encoding: 'utf8' });
  if (!fs.existsSync(png)) { console.error(`${engine} ${path.basename(file)}: no png\n${(r.stdout + r.stderr).slice(-400)}`); return null; }
  try { return PNG.sync.read(fs.readFileSync(png)); } catch { return null; }
}

async function main() {
  if (!fs.existsSync(run)) { console.error(`${run} not built: cd desktop && cmake -S . -B build -G Ninja -DTW_BLITZ=ON -DTW_LITEHTML=ON && cmake --build build`); process.exit(2); }
  const only = process.argv.includes('--only') ? process.argv[process.argv.indexOf('--only') + 1].split(',') : null;
  const results: { key: string; feature: string; validator: boolean; blitz: EngineResult; litehtml: EngineResult }[] = [];
  for (const p of PROBES) {
    if (only && !only.includes(p.key)) continue;
    const html = BASE + p.html;
    const file = path.join(probeDir, `${p.key}.html`);
    fs.writeFileSync(file, html);
    // the validator alone: does the feature survive into the render ops?
    const res = await runWidget(html, { timerRounds: 0 });
    const ops = JSON.stringify(res.renderOps);
    const validator = p.keep ? p.keep.test(ops) : true;
    const blitz = judge(render('blitz', file, path.join(probeDir, `${p.key}.blitz.png`)), p);
    const litehtml = judge(render('litehtml', file, path.join(probeDir, `${p.key}.litehtml.png`)), p);
    results.push({ key: p.key, feature: p.feature, validator, blitz, litehtml });
    const mark = (e: EngineResult) => (e.pass === null ? '?' : e.pass ? '✅' : '❌');
    console.log(`${p.key.padEnd(20)} validator ${validator ? 'keeps' : 'DROPS'}  blitz ${mark(blitz)}  litehtml ${mark(litehtml)}${blitz.detail || litehtml.detail ? `   (${[blitz.detail && 'blitz: ' + blitz.detail, litehtml.detail && 'litehtml: ' + litehtml.detail].filter(Boolean).join(' | ')})` : ''}`);
  }
  // --only: refresh those rows, keep the rest of an earlier full run
  const file = path.join(out, 'probes.json');
  let merged = results;
  if (only && fs.existsSync(file)) {
    const prev = JSON.parse(fs.readFileSync(file, 'utf8')) as typeof results;
    const byKey = new Map(prev.map((r) => [r.key, r]));
    for (const r of results) byKey.set(r.key, r);
    merged = PROBES.map((p) => byKey.get(p.key)).filter((r): r is typeof results[number] => !!r);
  }
  results.length = 0;
  results.push(...merged);
  fs.writeFileSync(file, JSON.stringify(results, null, 2));
  const md = ['| Feature | Validator keeps | Blitz | litehtml |', '|---|---|---|---|'];
  for (const r of results) {
    const mark = (e: EngineResult) => (e.pass === null ? '?' : e.pass ? '✅' : '❌');
    md.push(`| ${r.feature} (\`${r.key}\`) | ${r.validator ? '✅' : '❌'} | ${mark(r.blitz)} | ${mark(r.litehtml)} |`);
  }
  fs.writeFileSync(path.join(out, 'probes.md'), md.join('\n') + '\n');
  console.log(`\n${results.length} probes → ${path.relative(root, out)}/probes.{json,md}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
