import fs from 'node:fs';
import path from 'node:path';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';
// Blitz experiment (docs/blitz-experiment.md), "PaintScene on CoreGraphics": the native renderers side by side
// from the desktop harness' --png-dir output (same corpus, same width and dpr, no browser involved).
//
// usage: corepack pnpm tsx scripts/blitz-experiment/gallery-native.mts <outDir> <label=pngDir[:tableTxt]>... [--strips N] [--crop X,Y,W,H]
//   e.g. … reports/blitz-experiment/native vello=/tmp/gen-vello:/tmp/gen-vello.txt cg=/tmp/gen-cg:/tmp/gen-cg.txt litehtml=/tmp/gen-litehtml:/tmp/gen-litehtml.txt
// The first label is the baseline the pixel diffs are taken against. tableTxt is twidget-run's stdout (paint / loaded / height
// per widget). --strips N also writes strip-<widget>.png for the first N widgets: the same crop from every renderer stacked,
// 1:1 pixels, for looking at glyph rendering up close.
const argv = process.argv.slice(2);
const outDir = argv.shift();
if (!outDir) throw new Error('usage: gallery-native.mts <outDir> <label=pngDir[:tableTxt]>... [--strips N] [--crop X,Y,W,H]');
let strips = 0;
let crop = { x: 0, y: 0, w: 640, h: 220 };
const sources: { label: string; dir: string; table?: string }[] = [];
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === '--strips') strips = Number(argv[++i]);
  else if (a === '--crop') { const [x, y, w, h] = argv[++i].split(',').map(Number); crop = { x, y, w, h }; }
  else { const eq = a.indexOf('='); const label = a.slice(0, eq); const [dir, table] = a.slice(eq + 1).split(':'); sources.push({ label, dir, table }); }
}
if (sources.length < 2) throw new Error('need at least two label=dir sources');

type Row = { status: string; height: number; paintMs: number; loadedMs: number };
function parseTable(file?: string): Map<string, Row> {
  const m = new Map<string, Row>();
  if (!file || !fs.existsSync(file)) return m;
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    const r = /^(\S+\.html)\s+(\S+)\s+\d+\s+\d+\s+\d+\s+\d+\s+(\d+)\s+([\d.]+)ms\s+(\d+)ms/.exec(line);
    if (r) m.set(r[1].replace(/\.html$/, ''), { status: r[2], height: Number(r[3]), paintMs: Number(r[4]), loadedMs: Number(r[5]) });
  }
  return m;
}
const tables = sources.map((s) => parseTable(s.table));
const pad = (img: PNG, w: number, h: number) => { if (img.width === w && img.height === h) return img; const o = new PNG({ width: w, height: h }); o.data.fill(0); PNG.bitblt(img, o, 0, 0, Math.min(img.width, w), Math.min(img.height, h), 0, 0); return o; };
const readPng = (p: string) => (fs.existsSync(p) ? PNG.sync.read(fs.readFileSync(p)) : null);
const med = (xs: number[]) => { const s = xs.filter((x) => Number.isFinite(x)).sort((a, b) => a - b); return s.length ? s[Math.floor(s.length / 2)] : NaN; };
const pct = (x: number) => (x * 100).toFixed(1) + '%';

fs.mkdirSync(outDir, { recursive: true });
for (const s of sources) fs.mkdirSync(path.join(outDir, s.label), { recursive: true });
const stems = fs.readdirSync(sources[0].dir).filter((f) => f.endsWith('.png')).map((f) => f.replace(/\.png$/, '')).sort();
const rows: string[] = [];
const stats: { stem: string; diff: number[]; paint: number[] }[] = [];
for (const [idx, stem] of stems.entries()) {
  const imgs = sources.map((s) => readPng(path.join(s.dir, `${stem}.png`)));
  for (const [i, s] of sources.entries()) if (imgs[i]) fs.copyFileSync(path.join(s.dir, `${stem}.png`), path.join(outDir, s.label, `${stem}.png`));
  const base = imgs[0];
  const diffs: number[] = [];
  for (let i = 1; i < sources.length; i++) {
    const other = imgs[i];
    if (!base || !other) { diffs.push(NaN); continue; }
    const w = Math.max(base.width, other.width), h = Math.max(base.height, other.height);
    const A = pad(base, w, h), B = pad(other, w, h);
    const d = new PNG({ width: w, height: h });
    const n = pixelmatch(A.data, B.data, d.data, w, h, { threshold: 0.15, includeAA: true });
    fs.writeFileSync(path.join(outDir, sources[i].label, `${stem}.diff.png`), PNG.sync.write(d));
    diffs.push(n / (w * h));
  }
  const paint = sources.map((_, i) => tables[i].get(stem)?.paintMs ?? NaN);
  stats.push({ stem, diff: diffs, paint });
  if (idx < strips && imgs.every(Boolean)) {
    const gap = 6;
    const strip = new PNG({ width: crop.w, height: (crop.h + gap) * imgs.length - gap });
    strip.data.fill(255);
    imgs.forEach((img, i) => { const w = Math.min(crop.w, img!.width - crop.x), h = Math.min(crop.h, img!.height - crop.y); if (w > 0 && h > 0) PNG.bitblt(img!, strip, crop.x, crop.y, w, h, 0, i * (crop.h + gap)); });
    fs.writeFileSync(path.join(outDir, `strip-${stem}.png`), PNG.sync.write(strip));
  }
  const figs = sources.map((s, i) => {
    const t = tables[i].get(stem);
    const cap = [s.label, t ? `paint ${t.paintMs.toFixed(1)} ms` : '', t ? `${t.height} px` : '', i > 0 && Number.isFinite(diffs[i - 1]) ? `diff vs ${sources[0].label} ${pct(diffs[i - 1])}` : ''].filter(Boolean).join(' · ');
    return imgs[i] ? `<figure><div class="shot"><img src="${s.label}/${stem}.png"></div><figcaption>${cap}</figcaption></figure>` : `<figure><figcaption>${s.label}: no frame</figcaption></figure>`;
  });
  const zooms = sources.map((s, i) => (imgs[i] ? `<figure><div class="zoom"><img src="${s.label}/${stem}.png"></div><figcaption>${s.label} · 2×</figcaption></figure>` : ''));
  const diffFigs = sources.slice(1).map((s) => `<figure><div class="shot"><img src="${s.label}/${stem}.diff.png"></div><figcaption>${s.label} vs ${sources[0].label}</figcaption></figure>`);
  rows.push(`<section id="${stem}"><h2>${stem}</h2><div class="row">${figs.join('')}</div><details open><summary>text up close (2×, top-left corner)</summary><div class="row">${zooms.join('')}</div></details><details><summary>diff maps</summary><div class="row">${diffFigs.join('')}</div></details></section>`);
}
const summary = sources
  .map((s, i) => `${s.label}: paint median ${med(stats.map((x) => x.paint[i])).toFixed(1)} ms${i > 0 ? `, diff vs ${sources[0].label} median ${pct(med(stats.map((x) => x.diff[i - 1])))}` : ''}`)
  .join('; ');
const html = `<!doctype html><meta charset="utf-8"><title>twidget: native renderers side by side</title>
<style>
body{font:14px/1.4 -apple-system,system-ui,sans-serif;margin:16px;color:#111;background:#fff}
h1{font-size:20px}h2{font-size:15px;margin:28px 0 6px;font-weight:600}
.row{display:flex;gap:12px;align-items:flex-start}
figure{margin:0;flex:1;min-width:0}figcaption{font-size:12px;color:#555;margin-top:4px}
.shot img{width:100%;height:auto;display:block;border:1px solid #ddd;background:#fafafa}
.zoom{width:100%;aspect-ratio:${crop.w}/${crop.h};overflow:hidden;border:1px solid #ddd;position:relative;background:#fafafa}
.zoom img{position:absolute;left:0;top:0;transform:scale(2);transform-origin:0 0;image-rendering:pixelated;width:auto;height:auto;max-width:none}
details{margin-top:8px}summary{cursor:pointer;color:#555;font-size:12px}
nav{font-size:12px;color:#555;max-width:100%;overflow-wrap:anywhere}
</style>
<h1>Native renderers side by side: ${sources.map((s) => s.label).join(' | ')}</h1>
<p>${stems.length} widgets from one run <code>twidget-run --corpus … --png-dir</code> (same width and dpr). Diff is strict pixelmatch (threshold 0.15, AA on) against the first column.<br>${summary}</p>
<nav>${stems.map((s) => `<a href="#${s}">${s.slice(0, 24)}</a>`).join(' · ')}</nav>
${rows.join('\n')}
<script>
// The zoom boxes show 1 image px per 2 CSS px of the top-left corner: scale the img to the natural pixel size first.
for (const img of document.querySelectorAll('.zoom img')) { const fit = () => { img.style.width = img.naturalWidth / 2 + 'px'; }; img.complete ? fit() : img.addEventListener('load', fit); }
</script>`;
fs.writeFileSync(path.join(outDir, 'gallery.html'), html);
fs.writeFileSync(path.join(outDir, 'stats.json'), JSON.stringify({ sources: sources.map((s) => s.label), stats }, null, 1));
console.log(summary);
console.log(`gallery: ${path.join(outDir, 'gallery.html')} (${stems.length} widgets${strips ? `, ${Math.min(strips, stems.length)} strips` : ''})`);
