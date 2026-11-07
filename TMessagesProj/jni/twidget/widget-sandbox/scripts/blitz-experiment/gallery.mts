import fs from 'node:fs';
import path from 'node:path';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';
// Blitz experiment (docs/blitz-experiment.md), step 4: three-way gallery.
// usage: corepack pnpm tsx scripts/blitz-experiment/gallery.mts <dumpDir> <blitzOutDir> <litehtmlOutDir> <gallery.html>
// <litehtmlOutDir> holds <name>.litehtml.png plus timings.txt ("<name> <json>" per line).
const [dump, out, lh, galleryPath] = process.argv.slice(2);
if (!galleryPath) throw new Error('usage: gallery.mts <dumpDir> <blitzOutDir> <litehtmlOutDir> <gallery.html>');
const lhTimes = new Map<string, any>();
for (const line of fs.readFileSync(path.join(lh, 'timings.txt'), 'utf8').split('\n')) { const i = line.indexOf(' {'); if (i > 0) try { lhTimes.set(line.slice(0, i), JSON.parse(line.slice(i + 1))); } catch {} }
const blitzRows = new Map<string, any>(JSON.parse(fs.readFileSync(path.join(out, 'rows.json'), 'utf8')).map((r: any) => [r.file, r]));
const pad = (img: PNG, w: number, h: number) => { if (img.width === w && img.height === h) return img; const o = new PNG({ width: w, height: h }); o.data.fill(255); PNG.bitblt(img, o, 0, 0, img.width, img.height, 0, 0); return o; };
const shift = (img: PNG, dy: number) => { const o = new PNG({ width: img.width, height: img.height }); o.data.fill(255); PNG.bitblt(img, o, 0, Math.max(0, -dy), img.width, img.height - Math.abs(dy), 0, Math.max(0, dy)); return o; };
function metrics(a: PNG, b: PNG, diffPath: string) {
  const w = Math.max(a.width, b.width), h = Math.max(a.height, b.height); const A = pad(a, w, h), B = pad(b, w, h);
  const d = new PNG({ width: w, height: h });
  const strict = pixelmatch(A.data, B.data, d.data, w, h, { threshold: 0.15, includeAA: true }) / (w * h);
  fs.writeFileSync(diffPath, PNG.sync.write(d));
  const soft = Math.min(...[-2, -1, 0, 1, 2].map((dy) => pixelmatch(shift(A, dy).data, B.data, undefined as any, w, h, { threshold: 0.3, includeAA: false }) / (w * h)));
  return { strict, soft };
}
const b64 = (p: string) => 'data:image/png;base64,' + fs.readFileSync(p).toString('base64');
const rows: string[] = []; const stats: any[] = [];
for (const m of fs.readdirSync(dump).filter((f) => f.endsWith('.meta.json')).sort()) {
  const name = m.replace('.meta.json', ''); const meta = JSON.parse(fs.readFileSync(path.join(dump, m), 'utf8'));
  const ref = PNG.sync.read(fs.readFileSync(path.join(dump, `${name}.reference.png`)));
  const bl = PNG.sync.read(fs.readFileSync(path.join(out, `${name}.blitz.png`)));
  const lhPng = path.join(lh, `${name}.litehtml.png`);
  const li = fs.existsSync(lhPng) ? PNG.sync.read(fs.readFileSync(lhPng)) : null;
  const mb = metrics(bl, ref, path.join(out, `${name}.diff.png`));
  const ml = li ? metrics(li, ref, path.join(lh, `${name}.diff.png`)) : { strict: 1, soft: 1 };
  const bt = blitzRows.get(meta.file), lt = lhTimes.get(name) ?? {};
  stats.push({ file: meta.file, blitz: mb, litehtml: ml, blitzMs: bt ? bt.parse + bt.layout + bt.paint : null, litehtmlMs: lt.parse_ms != null ? lt.parse_ms + lt.layout_ms + lt.paint_ms : null, litehtmlHeight: li?.height ?? null, refHeight: ref.height });
  const cap = (t: string, m: { strict: number; soft: number }) => `${t} · strict ${(m.strict * 100).toFixed(1)}% · soft+shift ${(m.soft * 100).toFixed(1)}%`;
  rows.push(`<section><h2>${meta.file}</h2><div class="row"><figure><img src="${b64(path.join(dump, `${name}.reference.png`))}"><figcaption>Chromium (reference)</figcaption></figure><figure><img src="${b64(path.join(out, `${name}.blitz.png`))}"><figcaption>${cap('Blitz', mb)}</figcaption></figure><figure><img src="${li ? b64(lhPng) : ''}"><figcaption>${li ? cap('litehtml', ml) : 'litehtml: did not render'}</figcaption></figure></div><details><summary>diff maps</summary><div class="row"><figure><img src="${b64(path.join(out, `${name}.diff.png`))}"><figcaption>Blitz vs reference</figcaption></figure>${li ? `<figure><img src="${b64(path.join(lh, `${name}.diff.png`))}"><figcaption>litehtml vs reference</figcaption></figure>` : ''}</div></details></section>`);
}
const med = (xs: number[]) => { const s = xs.filter((x) => x != null).sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; };
const pct = (x: number) => (x * 100).toFixed(1) + '%';
const summary = `Medians over 40: Blitz strict ${pct(med(stats.map((s) => s.blitz.strict)))}, soft+shift ${pct(med(stats.map((s) => s.blitz.soft)))}, time ${med(stats.map((s) => s.blitzMs)).toFixed(1)} ms; litehtml strict ${pct(med(stats.map((s) => s.litehtml.strict)))}, soft+shift ${pct(med(stats.map((s) => s.litehtml.soft)))}, time ${med(stats.map((s) => s.litehtmlMs)).toFixed(1)} ms.`;
const html = `<!doctype html><meta charset="utf-8"><title>Blitz, litehtml vs Chromium</title><style>body{font:14px -apple-system,system-ui,sans-serif;margin:16px;background:#f3f4f6;color:#111}h1{font-size:18px}h2{font-size:13px;margin:24px 0 6px}.row{display:flex;gap:12px;align-items:flex-start}figure{margin:0;background:#fff;padding:6px;border:1px solid #ddd}img{display:block;width:404px;height:auto}figcaption{font-size:11px;color:#555;margin-top:4px}p{max-width:1000px}details{margin-top:6px}summary{font-size:12px;color:#555;cursor:pointer}</style><h1>Blitz and litehtml vs Chromium, corpus/generated (40)</h1><p>strict is the test metric (pixelmatch, threshold 0.15, anti-aliasing counted); soft+shift is threshold 0.3 without AA, best vertical shift of ±2 px. Time is parsing + layout + rasterisation of one document (for litehtml, layout is part of parsing). ${summary}</p>${rows.join('\n')}`;
fs.writeFileSync(galleryPath, html);
fs.writeFileSync(path.join(path.dirname(galleryPath), 'metrics.json'), JSON.stringify(stats, null, 2));
for (const s of stats) console.log(`${s.file.padEnd(62)} blitz ${pct(s.blitz.strict).padStart(6)}/${pct(s.blitz.soft).padStart(6)}  litehtml ${pct(s.litehtml.strict).padStart(6)}/${pct(s.litehtml.soft).padStart(6)}  h ${s.litehtmlHeight}/${s.refHeight}  ms ${s.blitzMs?.toFixed(1)} / ${s.litehtmlMs?.toFixed(1)}`);
console.log(summary);
