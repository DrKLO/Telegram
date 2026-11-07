#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const runPath = args.find((a) => !a.startsWith('--'));
if (!runPath) {
  console.error('usage: node tools/conformance/visual-report.mjs <macos-twidget-litehtml.visual.run.json> [--out <dir>]');
  process.exit(2);
}
const outIndex = args.indexOf('--out');
const runDir = path.dirname(path.resolve(runPath));
const outDir = path.resolve(outIndex >= 0 ? args[outIndex + 1] : path.join(runDir, 'report'));
const sandbox = process.env.TWIDGET_WIDGET_SANDBOX ?? path.resolve(here, '../../../widget-sandbox');
const conf = path.join(sandbox, 'packages/conformance');
const { judgeStage, diffStats } = await import(pathToFileURL(path.join(conf, 'tools/diff.mjs')).href);
const { decodePng, encodePng } = await import(pathToFileURL(path.join(conf, 'tools/png.mjs')).href);

const manifest = JSON.parse(fs.readFileSync(path.join(conf, 'manifest.json'), 'utf8'));
const tests = new Map(manifest.tests.map((t) => [t.id, t]));
const file = JSON.parse(fs.readFileSync(runPath, 'utf8'));
const templates = new Map();
for (const t of manifest.tests) {
  if (t.set !== 'visual') continue;
  const html = fs.readFileSync(path.join(conf, t.file), 'utf8');
  templates.set(t.id, new Set([...html.matchAll(/<template data-ref="([^"]+)"/g)].map((m) => m[1])));
}

const inked = (d, i) => d[i + 3] > 16 && (d[i] < 240 || d[i + 1] < 240 || d[i + 2] < 240);
function inkCount(img) {
  let n = 0;
  for (let i = 0; i < img.data.length; i += 4) if (inked(img.data, i)) n++;
  return n;
}
function redBox(img) {
  let n = 0, x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
    const p = (y * img.width + x) * 4;
    if (img.data[p] === 255 && img.data[p + 1] === 0 && img.data[p + 2] === 0) { n++; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  }
  return n ? { n, box: [x0 / 2, y0 / 2, (x1 - x0 + 1) / 2, (y1 - y0 + 1) / 2] } : { n: 0, box: null };
}
function sideBySide(images, gap = 8) {
  const w = images.reduce((s, i) => s + i.width, 0) + gap * (images.length - 1);
  const h = Math.max(...images.map((i) => i.height));
  const data = Buffer.alloc(w * h * 4);
  for (let i = 0; i < data.length; i += 4) { data[i] = 255; data[i + 1] = 0; data[i + 2] = 255; data[i + 3] = 255; }
  let ox = 0;
  for (const img of images) {
    for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
      const s = (y * img.width + x) * 4, d = (y * w + ox + x) * 4;
      const a = img.data[s + 3] / 255;
      data[d] = Math.round(img.data[s] * a + 255 * (1 - a));
      data[d + 1] = Math.round(img.data[s + 1] * a + 255 * (1 - a));
      data[d + 2] = Math.round(img.data[s + 2] * a + 255 * (1 - a));
      data[d + 3] = 255;
    }
    ox += img.width + gap;
  }
  return { width: w, height: h, data };
}
const safe = (id) => id.replace(/^visual\//, '').replace(/\//g, '_');

fs.mkdirSync(path.join(outDir, 'diffs'), { recursive: true });
fs.mkdirSync(path.join(outDir, 'side'), { recursive: true });
const stageRows = [];
const wholeRows = [];
for (const r of file.runs) {
  const test = tests.get(r.id);
  if (!test) continue;
  for (const given of r.stages ?? []) {
    const s = test.stages.find((x) => x.name === given.name);
    if (!s) continue;
    const row = { id: r.id, theme: r.theme, width: r.width, stage: s.name, template: templates.get(r.id)?.has(s.name) ?? false, reftest: !!s.reftest, rect: given.rect, beyondCap: !!given.beyondCap };
    if (!given.crop) { stageRows.push({ ...row, status: 'skip', diff: null, detail: given.detail }); continue; }
    const got = decodePng(fs.readFileSync(path.resolve(runDir, given.crop)));
    const refs = Object.entries(s.refs ?? {}).filter(([k]) => (r.theme === 'dark' ? k.endsWith('.dark') : !k.endsWith('.dark'))).map(([k, rel]) => ({ engine: k.replace('.dark', ''), png: decodePng(fs.readFileSync(path.join(conf, rel))) }));
    const j = judgeStage(got, refs, s);
    const name = `${safe(r.id)}.${r.theme}.${r.width}.${s.name}.png`;
    let red = { n: 0, box: null };
    if (j.image) {
      fs.writeFileSync(path.join(outDir, 'diffs', name), encodePng(j.image));
      red = redBox(j.image);
      fs.writeFileSync(path.join(outDir, 'side', name), encodePng(sideBySide([got, refs[0].png, j.image])));
    }
    stageRows.push({ ...row, status: j.status, diff: j.diff, maxDiff: j.maxDiff, detail: j.detail, inkGot: inkCount(got), inkRef: refs[0] ? inkCount(refs[0].png) : null, red: red.n, redBox: red.box, crop: given.crop, side: `side/${name}`, diffImage: `diffs/${name}`, ref: refs[0] ? path.join(conf, Object.entries(s.refs).find(([k]) => k.replace('.dark', '') === refs[0].engine && (r.theme === 'dark') === k.endsWith('.dark'))[1]) : null });
  }
  const refRel = test.refImages?.[r.theme];
  if (r.full && refRel && r.width === 390) {
    const gotPath = path.resolve(runDir, r.full);
    if (fs.existsSync(gotPath)) {
      const got = decodePng(fs.readFileSync(gotPath));
      const ref = decodePng(fs.readFileSync(path.join(conf, refRel)));
      const d = diffStats(got, ref);
      const name = `${safe(r.id)}.${r.theme}.${r.width}.whole.png`;
      fs.writeFileSync(path.join(outDir, 'side', name), encodePng(sideBySide([got, ref, d.image])));
      wholeRows.push({ id: r.id, theme: r.theme, width: r.width, diff: Math.round(d.ink * 10000) / 10000, gotSize: [got.width / 2, got.height / 2], refSize: [ref.width / 2, ref.height / 2], side: `side/${name}` });
    }
  }
}

const pct = (v) => (v == null ? '–' : `${(v * 100).toFixed(1)} %`);
const mark = (s) => ({ pass: '✅', fail: '❌', skip: '–', xfail: '⏳', xpass: '❗' }[s] ?? s);
const totals = stageRows.reduce((m, r) => ({ ...m, [r.status]: (m[r.status] ?? 0) + 1 }), {});
const md = [];
md.push(`# Visual set on ${file.platform}-${file.renderer}`, '');
md.push(`${file.engine}; framework ${file.framework}; twidget ${file.commit}; features ${file.features.join(', ')}; palette ${file.palette}; locale ${file.locale}; ${file.date}`, '');
md.push(`Stage verdicts (diff.mjs judgeStage, max ${pct(0.05)} of ink, threshold 0.12, anti-aliasing ignored): ${Object.entries(totals).map(([k, v]) => `${k} ${v}`).join(', ')} of ${stageRows.length} stage crops (${file.runs.length} runs).`, '');
md.push('Ref kind: **live** = the stage drawn by Chromium; **template** = a `<template data-ref>` illustration swapped in by build.ts (end state or motion diagram), not a frame of the live feature.', '');
md.push('| widget | stage | ref | 360 | 390 | 430 | ink got/ref (390) | red px (390) | side by side (390) |', '|---|---|---|---|---|---|---|---|---|');
const keys = [...new Set(stageRows.map((r) => `${r.id}|${r.theme}|${r.stage}`))];
for (const k of keys) {
  const [id, theme, stage] = k.split('|');
  const cells = [360, 390, 430].map((w) => stageRows.find((r) => r.id === id && r.theme === theme && r.stage === stage && r.width === w));
  const mid = cells[1] ?? cells.find(Boolean);
  md.push(`| ${id}${theme === 'dark' ? ' (dark)' : ''} | ${stage}${mid.beyondCap ? ' ⚠︎ below 600' : ''} | ${mid.template ? 'template' : 'live'} | ${cells.map((c) => (c ? `${mark(c.status)} ${pct(c.diff)}` : '·')).join(' | ')} | ${mid.inkGot ?? '–'}/${mid.inkRef ?? '–'} | ${mid.red ?? '–'} | ${mid.side ? `[png](${mid.side})` : ''} |`);
}
md.push('', '## Whole widget vs the posted reference picture (390, informational)', '', 'The posted pictures are rendered in reference mode (templates swapped in), on the SDK stand-in palette with a fixed clock; a difference here is not a verdict.', '');
md.push('| widget | theme | diff of ink | size got | size ref | side by side |', '|---|---|---|---|---|---|');
for (const w of wholeRows) md.push(`| ${w.id} | ${w.theme} | ${pct(w.diff)} | ${w.gotSize.join('×')} | ${w.refSize.join('×')} | [png](${w.side}) |`);
md.push('', '## SDK widgets (390)', '');
for (const r of file.runs.filter((x) => x.sdk && x.width === 390)) {
  const s = r.sdk;
  md.push(`### ${r.id} (${r.theme})`, '');
  md.push(`- status ${r.status}, height ${r.observed.height} (view ${r.observed.viewHeight}, cap ${r.observed.cap}), dead ${r.dead ?? 'no'}, errors ${r.errors.length}`);
  const pub = s.afterProbe;
  md.push(`- data-conformance after input: status ${pub.status}; ${Object.entries(pub.counts).map(([k, v]) => `${k} ${v}`).join(', ') || 'no checks'}`);
  for (const c of pub.checks) md.push(`  - ${c[2]} · ${c[0]} · ${c[1]}${c[3] ? ` (${c[3]})` : ''}`);
  if (r.skippedActions?.length) md.push(`- skipped manifest actions: ${r.skippedActions.join(', ')}`);
  for (const t of s.probeTaps ?? []) md.push(`- probe tap ${t.target}: ${t.skipped ?? ((t.actions ?? []).map((a) => [a.action, a.kind, a.value, a.url].filter(Boolean).join(' ')).join('; ') || 'no action')}${t.dead ? ` → dead: ${t.dead}` : ''}`);
  md.push(`- onAction total: ${s.actions.length ? s.actions.map((a) => [a.action, a.kind, a.value, a.url].filter(Boolean).join(' ')).join('; ') : 'none'}`);
  md.push(`- gesture claims: ${s.gestureClaims} (${s.gestureClaimsWithX} with x); storage commits: ${s.storageCommits.length ? s.storageCommits.map((c) => `${c.area} ${c.keys} keys`).join(', ') : 'none'}; storage now: local ${s.storage.local.keys} keys / ${s.storage.local.units} units, session ${s.storage.session.keys} keys / ${s.storage.session.units} units`);
  if (Object.keys(s.diags ?? {}).length) md.push(`- host diags: ${Object.entries(s.diags).map(([k, v]) => `${k} ×${v.count}${v.details?.length ? ` (${v.details.join(' | ').slice(0, 160)})` : ''}`).join('; ')}`);
  if (s.recreated) md.push(`- re-created with the stored copy: status ${s.recreated.published.status}, dead ${s.recreated.dead ?? 'no'}, ${s.recreated.published.checks.length} checks, actions ${s.recreated.actions.map((a) => a.action).join(', ') || 'none'}`);
  md.push(`- pictures: full/${safe(r.id)}.${r.theme}.390.png, .probed.png, .recreated.png`, '');
}
fs.writeFileSync(path.join(outDir, 'visual-summary.md'), md.join('\n') + '\n');
fs.writeFileSync(path.join(outDir, 'visual-stages.json'), JSON.stringify({ totals, stages: stageRows, whole: wholeRows }, null, 1));
console.log(`${stageRows.length} stage crops: ${Object.entries(totals).map(([k, v]) => `${k} ${v}`).join(', ')} → ${path.join(outDir, 'visual-summary.md')}`);
