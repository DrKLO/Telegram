#!/usr/bin/env node
// Stress kit report: run files (run.schema.json) from any number of clients and devices → summary.md and
// summary.json. No dependencies, Node ≥ 18.
//
//   node packages/stress/tools/report.mjs --out stress-results path/to/run.json [more run files…]
//
// For each load, a table of its levels against every (client, renderer, device) of a measure pass: the
// client's frame time p95, the share of dropped frames, the widget's own frame rate, and the renderer's own
// work per frame where the renderer measures it. Then, per client, the fitted cost per changed box of the
// moving loads (what calibrates the WebView cost model's A and B, the renderer cost in the CPU quota) and the
// highest level each load holds at the full frame rate. A production pass adds where each ladder stops.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(fs.readFileSync(path.join(here, '..', 'manifest.json'), 'utf8'));
const byId = new Map(manifest.widgets.map((w) => [w.id, w]));

const args = process.argv.slice(2);
let out = 'stress-results';
const files = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--out') out = args[++i];
  else files.push(args[i]);
}
if (!files.length) {
  console.error('usage: node report.mjs --out <dir> <run.json> [run.json …]');
  process.exit(2);
}

/** The few checks that keep a broken file from turning into a wrong table (the schema says the rest). */
function check(f, name) {
  const missing = ['platform', 'client', 'renderer', 'rendererKind', 'device', 'pass', 'config', 'runs'].filter((k) => !(k in f));
  if (missing.length) throw new Error(`${name}: missing ${missing.join(', ')}`);
  if (!Array.isArray(f.runs)) throw new Error(`${name}: runs is not an array`);
  for (const r of f.runs) {
    const m = ['id', 'loaded', 'dead', 'frames', 'host', 'widget'].filter((k) => !(k in r));
    if (m.length) throw new Error(`${name}: run ${r.id ?? '?'} misses ${m.join(', ')}`);
    if (!byId.has(r.id)) console.warn(`${name}: ${r.id} is not in this kit's manifest (another kit version?)`);
  }
}

const runsets = files.map((file) => {
  const f = JSON.parse(fs.readFileSync(file, 'utf8'));
  check(f, file);
  const label = `${f.client} · ${f.renderer} · ${f.device.model}`;
  const vsync = f.device.refreshHz ? 1000 / f.device.refreshHz : 1000 / 60;
  return { file, f, label, vsync, runs: new Map(f.runs.map((r) => [r.id, r])) };
});
const measure = runsets.filter((s) => s.f.pass === 'measure');
const production = runsets.filter((s) => s.f.pass === 'production');

const fmt = (x, d = 1) => (x === null || x === undefined || !Number.isFinite(x) ? '–' : Number(x).toFixed(d).replace(/\.0+$/, ''));
/** The measuring configuration of CLIENTS.md; a measure pass that applied something else is flagged. */
const MEASURE = { policy: 'compat', cpuQuotaMs: 10000, cpuQuotaWindowMs: 1000, mainThreadQuotaMs: 10000, maxTimers: 256, memoryLimitBytes: 64 * 1024 * 1024 };
function configNotes(s) {
  const applied = s.f.config?.applied ?? {}, requested = s.f.config?.requested ?? {};
  const notes = [];
  for (const [k, v] of Object.entries(MEASURE)) {
    const a = applied[k] ?? null, r = requested[k] ?? null;
    if (a === null && r !== v) notes.push(`${k} not set`);
    else if (a !== null && a !== v) notes.push(`${k} ${a}`);
    else if (a === null && Object.keys(applied).length) notes.push(`${k}: host default`);
  }
  return notes;
}

/** A cell: client frame p95 / dropped % / widget fps [/ renderer ms]; ✖ when the widget stopped. */
function cell(s, r) {
  if (!r) return '';
  if (r.dead && r.diedAtMs !== null && (r.widget?.frames ?? 0) === 0) return `✖ ${String(r.dead).split(':')[0].slice(0, 28)}`;
  const parts = [fmt(r.frames.p95), r.frames.dropped === null ? '–' : fmt(r.frames.dropped * 100, 0) + '%', fmt(r.widget.fps, 0)];
  if (s.f.rendererKind === 'own' || r.renderer) parts.push(fmt(r.renderer?.msPerFrame, 2));
  return parts.join(' / ') + (r.dead ? ' ✖' : '');
}

/** Least squares y = a + b·x; null below two distinct x. */
function fit(points) {
  if (points.length < 2 || new Set(points.map((p) => p[0])).size < 2) return null;
  const n = points.length, sx = points.reduce((s, p) => s + p[0], 0), sy = points.reduce((s, p) => s + p[1], 0);
  const sxx = points.reduce((s, p) => s + p[0] * p[0], 0), sxy = points.reduce((s, p) => s + p[0] * p[1], 0);
  const b = (n * sxy - sx * sy) / (n * sxx - sx * sx);
  return { a: (sy - b * sx) / n, b, n };
}


/** Per client and moving load: µs per changed box, from the renderer's own time or from saturated client frames; the highest level at the full rate. */
function calibrate(s) {
  const res = {};
  for (const load of manifest.loads) {
    const levels = load.widgets.map((id) => ({ w: byId.get(id), r: s.runs.get(id) })).filter((x) => x.w && x.r);
    if (!levels.length) continue;
    const full = (r) => !r.dead && r.frames.p95 !== null && r.frames.p95 <= 1.5 * s.vsync && (r.frames.dropped ?? 0) <= 0.05;
    const widgetFull = (r) => !r.dead && r.widget.fps !== null && r.widget.fps >= 0.9 * (1000 / s.vsync);
    const upTo = (pred) => { let v = null; for (const x of levels) { if (pred(x.r)) v = x.w.value; else break; } return v; };
    const entry = { fit: load.fit, fullRateUpTo: upTo(full), widgetFullRateUpTo: upTo(widgetFull), metric: null, usPerBox: null, points: 0 };
    if (load.fit) {
      const own = levels.filter((x) => x.r.renderer?.msPerFrame != null);
      let pts, metric;
      if (own.length >= 2) { pts = own.map((x) => [x.w.changedBoxes, x.r.renderer.msPerFrame]); metric = 'renderer ms per frame'; }
      else { pts = levels.filter((x) => !x.r.dead && x.r.frames.p50 !== null && x.r.frames.p50 > 1.2 * s.vsync).map((x) => [x.w.changedBoxes, x.r.frames.p50]); metric = 'client frame p50 (saturated levels)'; }
      const f = fit(pts);
      Object.assign(entry, { metric: f ? metric : `${metric}: under two levels`, usPerBox: f ? Math.round(f.b * 1000) : null, points: pts.length });
    }
    res[load.id] = entry;
  }
  return res;
}

const lines = ['# Stress kit results', '', `Kit version ${manifest.version}. Cells: client frame p95 (ms) / dropped frames / the widget's own fps, then the renderer's own ms per frame for own renderers. ✖ = the widget stopped (the reason follows when it stopped before the window). "over" lists the strict limits a level crosses.`, ''];
lines.push('## Runs', '', '| | client · renderer · device | class | Hz | pass | framework | date | config |', '|---|---|---|---|---|---|---|---|');
runsets.forEach((s, i) => lines.push(`| ${i + 1} | ${s.label} | ${s.f.device.class} | ${s.f.device.refreshHz ?? '–'} | ${s.f.pass} | ${s.f.framework ?? '–'} | ${(s.f.date ?? '').slice(0, 10)} | ${s.f.pass === 'measure' ? configNotes(s).join(', ') || 'as CLIENTS.md' : (s.f.config?.applied?.policy ?? s.f.config?.requested?.policy ?? '')} |`));
lines.push('');

const summary = { kit: manifest.version, runs: runsets.map((s) => ({ label: s.label, file: path.basename(s.file), platform: s.f.platform, client: s.f.client, renderer: s.f.renderer, rendererKind: s.f.rendererKind, device: s.f.device, pass: s.f.pass, framework: s.f.framework })), loads: {}, calibration: {}, production: {} };

if (measure.length) {
  lines.push('## Loads (measure pass)', '');
  const head = measure.map((s) => `${runsets.indexOf(s) + 1}`);
  for (const load of manifest.loads) {
    if (!load.widgets.some((id) => measure.some((s) => s.runs.has(id)))) continue;
    lines.push(`### ${load.title} (\`${load.id}\`)`, '', load.stresses + '.', '', `| ${load.param} | over | ${head.map((h) => `run ${h}`).join(' | ')} |`, `|---|---|${head.map(() => '---').join('|')}|`);
    const rows = [];
    for (const id of load.widgets) {
      const w = byId.get(id);
      lines.push(`| ${w.value} | ${w.crosses.map((c) => c.split(' ')[0]).join(', ')} | ${measure.map((s) => cell(s, s.runs.get(id))).join(' | ')} |`);
      rows.push({ id, value: w.value, crosses: w.crosses, cells: Object.fromEntries(measure.map((s) => [s.label, s.runs.get(id) ? { p95: s.runs.get(id).frames.p95, dropped: s.runs.get(id).frames.dropped, widgetFps: s.runs.get(id).widget.fps, rendererMs: s.runs.get(id).renderer?.msPerFrame ?? null, jsMsPerSec: s.runs.get(id).host.jsMsPerSec, opsPerSec: s.runs.get(id).host.opsPerSec, dead: s.runs.get(id).dead } : null])) });
    }
    summary.loads[load.id] = rows;
    lines.push('');
  }

  lines.push('## Calibration', '', 'Per client: the cost of one changed box on the moving loads (µs per box; the slope of the renderer\'s own time per frame, or of the client\'s frame time over the levels where frames take longer than 1.2 frame periods), and the highest level each load holds at the full rate on the client\'s frames (p95 within 1.5 frame periods, at most 5 % dropped) and on the widget\'s own frames (at least 90 % of the refresh rate). The heavy loads (fit A: `shadow-moving`, `ars-cell`, `opacity-moving`, `filter-moving`) give the cost model\'s A, the plain ones (fit B: `style-churn`, `gradient-moving`, `clip-moving`) its B; `--` = the device never got slow enough to tell.', '');
  for (const s of measure) {
    const c = calibrate(s);
    summary.calibration[s.label] = c;
    lines.push(`### ${runsets.indexOf(s) + 1}. ${s.label}`, '', '| load | fit | µs per changed box | from | full rate up to (client frames) | full rate up to (widget) |', '|---|---|---|---|---|---|');
    for (const load of manifest.loads) {
      const e = c[load.id];
      if (!e) continue;
      lines.push(`| ${load.id} | ${e.fit ? (e.fit === 'heavy' ? 'A' : 'B') : ''} | ${e.usPerBox ?? '--'} | ${e.metric ?? ''} | ${e.fullRateUpTo ?? 'none'} | ${e.widgetFullRateUpTo ?? 'none'} |`);
    }
    lines.push('');
  }
}

if (production.length) {
  lines.push('## Where the product configuration stops (production pass)', '', 'Per load: the first level whose widget stopped, and why; next to it the first level the manifest says is over a strict limit.', '');
  lines.push(`| load | first level over a limit | ${production.map((s) => `run ${runsets.indexOf(s) + 1}`).join(' | ')} |`, `|---|---|${production.map(() => '---').join('|')}|`);
  for (const load of manifest.loads) {
    const over = load.widgets.map((id) => byId.get(id)).find((w) => w.crosses.length);
    const cells = production.map((s) => {
      const dead = load.widgets.map((id) => s.runs.get(id)).find((r) => r && r.dead);
      return dead ? `${dead.value}: ${String(dead.dead).slice(0, 60)}` : load.widgets.some((id) => s.runs.has(id)) ? 'none' : '';
    });
    summary.production[load.id] = { firstOver: over ? { value: over.value, crosses: over.crosses } : null, stops: Object.fromEntries(production.map((s, i) => [s.label, cells[i]])) };
    lines.push(`| ${load.id} | ${over ? `${over.value} (${over.crosses.map((c) => c.split(' ')[0]).join(', ')})` : 'none'} | ${cells.join(' | ')} |`);
  }
  lines.push('');
}

fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'summary.md'), lines.join('\n'));
fs.writeFileSync(path.join(out, 'summary.json'), JSON.stringify(summary, null, 1));
console.log(`${runsets.length} run file(s) → ${path.join(out, 'summary.md')}, summary.json`);
