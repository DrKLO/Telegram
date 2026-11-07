// Blitz experiment (docs/blitz-experiment.md), step 3.
// Render every dumped widget through Blitz, diff against the Chromium
// reference screenshot with the same pixelmatch settings as
// tests/generated.spec.ts, and write a markdown table + per-widget diff PNGs.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const [dumpDir, bin, outDir] = process.argv.slice(2);
if (!dumpDir || !bin || !outDir) throw new Error('usage: TW_SHARED_FONTS=1 corepack pnpm tsx scripts/blitz-experiment/compare.mts <dumpDir> <twidget_render binary> <outDir>');
const reportMd = fs.readFileSync(path.join(here, '..', '..', 'docs', 'compat-report-generated.md'), 'utf8');
// Sandbox-vs-reference diffs from the committed report (batch heading, then rows).
const sandboxDiffs = new Map<string, number>();
{ let batch = ''; for (const line of reportMd.split('\n')) { const h = /^## ([\w-]+):/.exec(line); if (h) batch = h[1]; const r = /^\| (\S+\.html) \| . \| ([\d.]+)% \|/.exec(line); if (r) sandboxDiffs.set(`${batch}/${r[1]}`, Number(r[2]) / 100); } }
fs.mkdirSync(outDir, { recursive: true });

function diffImages(a: PNG, b: PNG, name: string): number {
  const w = Math.max(a.width, b.width), h = Math.max(a.height, b.height);
  const pad = (img: PNG) => { if (img.width === w && img.height === h) return img; const o = new PNG({ width: w, height: h }); o.data.fill(255); PNG.bitblt(img, o, 0, 0, img.width, img.height, 0, 0); return o; };
  const out = new PNG({ width: w, height: h });
  const n = pixelmatch(pad(a).data, pad(b).data, out.data, w, h, { threshold: 0.15, includeAA: true });
  fs.writeFileSync(path.join(outDir, `${name}.diff.png`), PNG.sync.write(out));
  return n / (w * h);
}

interface Row { file: string; batch: string; diff: number; sandboxDiff: number | null; parse: number; layout: number; paint: number; warm: number; passes: number; total: number; height: number; refHeight: number; clicks: number; canvas: number; svg: number; inputs: number; error: string }
const rows: Row[] = [];
const metas = fs.readdirSync(dumpDir).filter((f) => f.endsWith('.meta.json')).sort();
for (const m of metas) {
  const meta = JSON.parse(fs.readFileSync(path.join(dumpDir, m), 'utf8'));
  const name = m.replace('.meta.json', '');
  const html = path.join(dumpDir, `${name}.html`);
  const png = path.join(outDir, `${name}.blitz.png`);
  // Second run is the one timed: the first run of the process pays the font
  // database build (~300 ms on this machine), which a host would pay once.
  let res = spawnSync(bin, [html, String(meta.width), png], { encoding: 'utf8', timeout: 60_000 });
  const t0 = process.hrtime.bigint();
  res = spawnSync(bin, [html, String(meta.width), png], { encoding: 'utf8', timeout: 60_000 });
  const total = Number(process.hrtime.bigint() - t0) / 1e6;
  let diff = 1, parse = 0, layout = 0, paint = 0, height = 0, warm = 0, passes = 0, error = '';
  if (res.status !== 0 || !fs.existsSync(png)) {
    error = (res.stderr || res.error?.message || `exit ${res.status}`).split('\n').find((l) => l.trim()) ?? 'failed';
  } else {
    try { const t = JSON.parse(res.stdout.trim().split('\n').pop()!); parse = t.parse_ms; layout = t.layout_ms; paint = t.paint_ms; height = t.height; warm = t.warm_ms; passes = t.passes; } catch {}
    const ref = PNG.sync.read(fs.readFileSync(path.join(dumpDir, `${name}.reference.png`)));
    const bl = PNG.sync.read(fs.readFileSync(png));
    diff = diffImages(bl, ref, name);
  }
  const sandboxDiff: number | null = sandboxDiffs.get(meta.file) ?? null;
  rows.push({ file: meta.file, batch: meta.file.split('/')[0], diff, sandboxDiff, parse, layout, paint, warm, passes, total, height, refHeight: meta.height, clicks: meta.clicks, canvas: meta.canvases, svg: meta.svgs, inputs: meta.inputs, error });
  console.log(`${meta.file}: blitz diff ${(diff * 100).toFixed(2)}% (sandbox ${sandboxDiff == null ? '?' : (sandboxDiff * 100).toFixed(2) + '%'}) parse ${parse} layout ${layout} paint ${paint} ms; h ${height}/${meta.height} ${error}`);
}
const TOL = 0.03;
const med = (xs: number[]) => { const s = [...xs].sort((a, b) => a - b); return s.length ? s[Math.floor(s.length / 2)] : 0; };
const lines: string[] = [];
for (const b of [...new Set(rows.map((r) => r.batch))]) {
  const rs = rows.filter((r) => r.batch === b);
  const pass = rs.filter((r) => r.diff <= TOL).length;
  const sbPass = rs.filter((r) => r.sandboxDiff != null && r.sandboxDiff <= TOL).length;
  lines.push(`## ${b}: Blitz ${pass} / ${rs.length} within ${TOL * 100}% (sandbox WebKit/Chromium path: ${sbPass} / ${rs.length})`, '', '| Widget | Blitz diff | Sandbox diff | Blitz parse / layout / paint (ms) | Height Blitz / ref | Clicks | canvas / svg / inputs | Error |', '|---|---|---|---|---|---|---|---|');
  for (const r of rs) lines.push(`| ${r.file.split('/')[1]} | ${(r.diff * 100).toFixed(2)}% ${r.diff <= TOL ? '✅' : '❌'} | ${r.sandboxDiff == null ? '—' : (r.sandboxDiff * 100).toFixed(2) + '%'} | ${r.parse.toFixed(0)} / ${r.layout.toFixed(0)} / ${r.paint.toFixed(0)} | ${r.height} / ${r.refHeight} | ${r.clicks} | ${r.canvas} / ${r.svg} / ${r.inputs} | ${r.error.replace(/\|/g, '\\|')} |`);
  lines.push('');
}
const ok = rows.filter((r) => !r.error);
lines.push(`Medians over ${ok.length} rendered widgets: Blitz diff ${(med(ok.map((r) => r.diff)) * 100).toFixed(2)}%, sandbox diff ${(med(ok.filter((r) => r.sandboxDiff != null).map((r) => r.sandboxDiff!)) * 100).toFixed(2)}%; Blitz parse ${med(ok.map((r) => r.parse)).toFixed(1)} ms, layout ${med(ok.map((r) => r.layout)).toFixed(1)} ms, paint ${med(ok.map((r) => r.paint)).toFixed(1)} ms, first document in the process (one-time init included) ${med(ok.map((r) => r.warm)).toFixed(0)} ms, whole process ${med(ok.map((r) => r.total)).toFixed(0)} ms.`);
fs.writeFileSync(path.join(outDir, 'table.md'), lines.join('\n') + '\n');
fs.writeFileSync(path.join(outDir, 'rows.json'), JSON.stringify(rows, null, 2));
