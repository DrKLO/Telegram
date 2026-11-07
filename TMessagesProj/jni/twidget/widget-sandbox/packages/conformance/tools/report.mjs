#!/usr/bin/env node
// Conformance report: turns run files (what a client's runner recorded, see
// ../CLIENTS.md and ../run.schema.json) into the common report, one JSON per
// client renderer plus a Markdown summary of all of them side by side, with a
// verdict for every catalogue ID. Stage crops without a diff are judged here
// against ../refs with the rule of diff.mjs. No dependencies, Node ≥ 18.
//
//   node packages/conformance/tools/report.mjs [--out DIR] [--fail-on in] RUN.json [RUN.json …]
//
// --out      where to write <platform>-<renderer>.json, summary.md and diffs/ (default ./conformance-results)
// --fail-on  exit 1 when an ID of that catalogue status (in | candidate) fails on any renderer (for CI)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from './png.mjs';
import { judgeStage } from './diff.mjs';

const PKG = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const readJson = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));
const MARK = { pass: '✅', partial: '◐', fail: '❌', xfail: '⏳', xpass: '❗', skip: '–', untested: '·' };
const norm = (s) => (s === 'pending' ? 'fail' : s);

export function verdictOf(ev) {
  if (!ev.length) return 'untested';
  const n = (s) => ev.filter((e) => e.status === s).length;
  const pass = n('pass'), fail = n('fail'), xfail = n('xfail'), xpass = n('xpass');
  if (fail && (pass || xpass)) return 'partial';
  if (fail) return 'fail';
  if (xpass && !xfail && !pass) return 'xpass';
  // What is implemented passes, the rest is known to be pending: not a failure.
  if (xfail) return 'xfail';
  if (pass || xpass) return 'pass';
  return 'skip';
}

/** Host checks of the manifest from what the runner observed (bridge calls, height, focus). */
function hostChecks(test, observed) {
  return (test.host ?? []).map((h) => {
    if (h.gesture && observed.syntheticInput) return { id: h.id, name: h.name, status: 'skip', detail: 'synthetic input grants no user gesture' };
    let ok = true;
    const detail = [];
    if (h.bridge) {
      const calls = (observed.bridge ?? []).filter((b) => b.method === h.bridge.method && (h.bridge.arg === undefined || (b.args ?? [])[0] === h.bridge.arg));
      ok &&= calls.length === h.bridge.count;
      detail.push(`${h.bridge.method} ×${calls.length} (want ${h.bridge.count})`);
    }
    if (h.height) {
      const v = observed.height;
      if (typeof v !== 'number') return { id: h.id, name: h.name, status: 'skip', detail: 'height not observed' };
      ok &&= (h.height.min === undefined || v >= h.height.min) && (h.height.max === undefined || v <= h.height.max);
      detail.push(`height ${v}`);
    }
    if (h.focus === 'chat') {
      if (typeof observed.chatFocused !== 'boolean') return { id: h.id, name: h.name, status: 'skip', detail: 'chat focus not observed' };
      ok &&= observed.chatFocused;
      detail.push(observed.chatFocused ? 'chat input kept the focus' : `focus moved to ${observed.focusHolder ?? 'the widget'}`);
    }
    return { id: h.id, name: h.name, status: h.xfail ? (ok ? 'xpass' : 'xfail') : ok ? 'pass' : 'fail', detail: detail.join('; ') };
  });
}

export function buildReport(file, fileDir, manifest, catalogue, diffDir) {
  const tests = new Map(manifest.tests.map((t) => [t.id, t]));
  const evidence = new Map();
  const add = (id, e) => { if (!evidence.has(id)) evidence.set(id, []); evidence.get(id).push(e); };
  const out = [];
  for (const r of file.runs) {
    const test = tests.get(r.id);
    if (!test) { console.warn(`${r.id}: not in the manifest, ignored`); continue; }
    // A test with `stops` must be stopped by the host with a reason that starts with it (a counted limit under strict):
    // it passes on that stop and fails on any other end, alive or dead.
    const stopped = !!test.stops && typeof r.dead === 'string' && r.dead.startsWith(test.stops);
    const failed = test.stops ? !stopped : !r.loaded || !!r.dead;
    // Input the runner could not deliver: the checks and stages it feeds are not judged.
    const skippedIds = new Set();
    for (const s of r.skippedActions ?? []) {
      const acts = typeof s === 'number' ? [test.actions[s]] : test.actions.filter((a) => Object.keys(a).includes(String(s)));
      for (const a of acts) for (const id of a?.ids ?? []) skippedIds.add(id);
    }
    const checks = (r.checks ?? []).map(([id, name, st, detail]) => (skippedIds.has(id) ? [id, name, 'skip', 'input not deliverable on this renderer'] : [id, name, norm(st), detail ?? '']));
    const stages = [];
    for (const s of test.set === 'auto' ? test.stages : []) {
      if (!s.themes.includes(r.theme)) continue;
      const given = (r.stages ?? []).find((x) => x.name === s.name);
      let res;
      if (given && given.status && given.diff !== undefined) res = { status: given.status, diff: given.diff, maxDiff: given.maxDiff ?? s.maxDiff, detail: given.detail };
      else if (given?.crop) {
        const got = decodePng(fs.readFileSync(path.resolve(fileDir, given.crop)));
        const refs = Object.entries(s.refs ?? {}).filter(([k]) => (r.theme === 'dark' ? k.endsWith('.dark') : !k.endsWith('.dark'))).map(([k, rel]) => ({ engine: k.replace('.dark', ''), png: decodePng(fs.readFileSync(path.join(PKG, rel))) }));
        const j = judgeStage(got, refs, s);
        if (j.image && diffDir) { fs.mkdirSync(diffDir, { recursive: true }); fs.writeFileSync(path.join(diffDir, `${file.platform}-${file.renderer}.${r.id.replace('/', '_')}.${r.theme}.${r.width}.${s.name}.png`), encodePng(j.image)); }
        res = { status: j.status, diff: j.diff, maxDiff: j.maxDiff, detail: j.detail };
      } else res = { status: 'skip', diff: null, maxDiff: s.maxDiff, detail: 'no crop' };
      if (s.covers.some((c) => skippedIds.has(c))) res = { ...res, status: 'skip', detail: 'input not deliverable on this renderer' };
      stages.push({ name: s.name, covers: s.covers, ...res });
    }
    const host = r.host ?? hostChecks(test, r.observed ?? {});
    if (test.set === 'auto') {
      if (test.stops) for (const id of test.covers) add(id, { test: r.id, kind: 'stop', name: `stopped with "${test.stops}…"`, status: failed ? 'fail' : 'pass', detail: r.dead ?? 'not stopped', theme: r.theme, width: r.width });
      else for (const [id, name, st, detail] of checks) if (id !== 'harness') add(id, { test: r.id, kind: 'check', name, status: failed ? 'fail' : st, detail, theme: r.theme, width: r.width });
      for (const s of stages) for (const id of s.covers) add(id, { test: r.id, kind: 'stage', name: s.name, status: failed ? 'fail' : s.status, detail: s.diff === null ? s.detail ?? '' : `diff ${(s.diff * 100).toFixed(1)} % of ink (max ${((s.maxDiff ?? 0.05) * 100).toFixed(0)} %)${s.detail ? '; ' + s.detail : ''}`, theme: r.theme, width: r.width });
      for (const h of host) add(h.id, { test: r.id, kind: 'host', name: h.name, status: norm(h.status), detail: h.detail, theme: r.theme, width: r.width });
    }
    out.push({
      id: r.id, set: test.set, theme: r.theme, width: r.width, loaded: !!r.loaded, dead: r.dead ?? null, status: r.status, ...(test.stops ? { stops: test.stops, stopped } : {}),
      errors: r.errors ?? [], harnessErrors: checks.filter((c) => c[0] === 'harness').map((c) => c[1] + ': ' + c[3]), skippedActions: r.skippedActions ?? [],
      counts: checks.reduce((m, c) => ({ ...m, [c[2]]: (m[c[2]] ?? 0) + 1 }), {}),
      stages: stages.map((s) => ({ name: s.name, diff: s.diff, status: s.status })), host,
    });
  }
  const features = {};
  for (const f of catalogue.features) {
    const ev = evidence.get(f.id) ?? [];
    features[f.id] = { status: f.status, verdict: verdictOf(ev), counts: ev.reduce((m, e) => ({ ...m, [e.status]: (m[e.status] ?? 0) + 1 }), {}), evidence: ev };
  }
  const summary = {};
  for (const st of ['in', 'candidate', 'out']) {
    const row = { pass: 0, partial: 0, fail: 0, xfail: 0, xpass: 0, skip: 0, untested: 0 };
    for (const f of catalogue.features.filter((x) => x.status === st)) row[features[f.id].verdict]++;
    summary[st] = row;
  }
  return {
    schema: 1,
    profile: catalogue.version,
    client: { platform: file.platform ?? 'unknown', renderer: file.renderer ?? 'unknown', engine: file.engine ?? '', framework: file.framework ?? '', policy: file.policy ?? 'strict', features: Array.isArray(file.features) ? file.features : [], date: file.date ?? new Date().toISOString(), commit: file.commit ?? '' },
    summary, features, tests: out,
  };
}

/** What plain browser engines answer (recorded by the maintainers' build), as reference columns. */
function nativeColumn(manifest, catalogue, engine) {
  const ev = new Map();
  for (const t of manifest.tests) if (t.set === 'auto') for (const c of t.checks) {
    const st = c.native?.[engine];
    if (!st || c.id === 'harness') continue;
    if (!ev.has(c.id)) ev.set(c.id, []);
    ev.get(c.id).push({ status: norm(st) });
  }
  return Object.fromEntries(catalogue.features.map((f) => [f.id, verdictOf(ev.get(f.id) ?? [])]));
}

export function summaryMarkdown(reports, manifest, catalogue) {
  const natives = ['chromium', 'webkit'].map((e) => ({ name: `native ${e}`, verdicts: nativeColumn(manifest, catalogue, e) })).filter((n) => Object.values(n.verdicts).some((v) => v !== 'untested'));
  const cols = [...reports.map((r) => ({ name: `${r.client.platform} ${r.client.renderer}`, verdicts: Object.fromEntries(Object.entries(r.features).map(([k, v]) => [k, v.verdict])) })), ...natives];
  const count = (st) => catalogue.features.filter((f) => f.status === st).length;
  const hasCandidates = count('candidate') > 0;
  const first = reports[0]?.client ?? {};
  const md = [
    '# Conformance results',
    '',
    `Generated ${new Date().toISOString().slice(0, 16).replace('T', ' ')} UTC by \`packages/conformance/tools/report.mjs\` (profile ${catalogue.version}; ${reports.map((r) => `${r.client.platform} ${r.client.renderer}${r.client.engine ? ` — ${r.client.engine}` : ''}${r.client.commit ? ` @ ${r.client.commit}` : ''}`).join('; ')}; policy ${first.policy ?? ''}, features ${(first.features ?? []).join(', ') || 'none'}).`,
    '',
    `Verdict per catalogue ID over the automatic set: ${MARK.pass} pass, ${MARK.partial} partly, ${MARK.fail} fail, ${MARK.xfail} expected to fail (not implemented yet: D23, the profile in the validator, …), ${MARK.xpass} passes although expected to fail, ${MARK.skip} not applicable here, ${MARK.untested} not tested. For **out** features "pass" means *neutralised as the profile says*. The native columns are a plain browser engine without the pipeline: a reference for the tests, not an expectation.`,
    '',
    '## Totals',
    '',
    `| Renderer | in (${count('in')}) ${MARK.pass} / ${MARK.partial} / ${MARK.fail} / ${MARK.xfail} |${hasCandidates ? ` candidate (${count('candidate')}) ${MARK.pass} / ${MARK.partial} / ${MARK.fail} |` : ''} out (${count('out')}) neutralised ${MARK.pass} / not yet ${MARK.xfail} / ${MARK.fail} | auto runs loaded | errors |`,
    hasCandidates ? '|---|---|---|---|---|---|' : '|---|---|---|---|---|',
  ];
  for (const r of reports) {
    const s = r.summary, auto = r.tests.filter((t) => t.set === 'auto');
    md.push(`| ${r.client.platform} ${r.client.renderer} | ${s.in.pass} / ${s.in.partial} / ${s.in.fail} / ${s.in.xfail} |${hasCandidates ? ` ${s.candidate.pass} / ${s.candidate.partial} / ${s.candidate.fail} |` : ''} ${s.out.pass} / ${s.out.xfail + s.out.partial} / ${s.out.fail} | ${auto.filter((t) => (t.stops ? t.stopped : t.loaded && !t.dead)).length} / ${auto.length} | ${r.tests.reduce((n, t) => n + t.errors.length + t.harnessErrors.length, 0)} |`);
  }
  md.push('');
  if (hasCandidates) {
    md.push('## Candidates', '', 'Membership decided by measurement (D3: in when every client draws it).', '', `| ID | Feature | ${cols.map((c) => c.name).join(' | ')} |`, `|---|---|${cols.map(() => '---').join('|')}|`);
    for (const f of catalogue.features.filter((x) => x.status === 'candidate')) md.push(`| \`${f.id}\` | ${f.title.replace(/\|/g, '\\|')} | ${cols.map((c) => MARK[c.verdicts[f.id]]).join(' | ')} |`);
    md.push('');
  }
  for (const st of ['in', 'out']) {
    md.push(`## ${st === 'in' ? 'In the profile' : 'Out of the profile (pass = neutralised)'}`, '', `| ID | ${cols.map((c) => c.name).join(' | ')} |`, `|---|${cols.map(() => '---').join('|')}|`);
    for (const f of catalogue.features.filter((x) => x.status === st)) md.push(`| \`${f.id}\`${f.conditional ? ' *' : ''} | ${cols.map((c) => MARK[c.verdicts[f.id]]).join(' | ')} |`);
    md.push('');
  }
  md.push('`*` in the profile if every client draws it (D3a).', '');
  for (const r of reports) {
    md.push(`## ${r.client.platform} ${r.client.renderer}: in-profile failures`, '');
    let any = false;
    for (const f of catalogue.features.filter((x) => x.status === 'in')) {
      const v = r.features[f.id];
      if (v.verdict !== 'fail' && v.verdict !== 'partial') continue;
      any = true;
      // One line per failing check, whatever the theme and width variants.
      const bad = [...new Set(v.evidence.filter((e) => e.status === 'fail').map((e) => `${e.test.split('/').pop()}: ${e.name}${e.detail ? ` (${String(e.detail).slice(0, 90)})` : ''}`))].slice(0, 4);
      md.push(`- \`${f.id}\`: ${bad.join('; ').replace(/\|/g, '\\|')}`);
    }
    if (!any) md.push('- none');
    md.push('');
  }
  const visualIds = [...new Set(reports.flatMap((r) => r.tests.filter((t) => t.set === 'visual').map((t) => t.id)))].sort();
  if (visualIds.length) {
    md.push('## Visual set', '', 'For people (the test channel): judged by eye. Runners only check that each widget loads without errors.', '', `| Widget | ${reports.map((r) => `${r.client.platform} ${r.client.renderer}`).join(' | ')} |`, `|---|${reports.map(() => '---').join('|')}|`);
    for (const id of visualIds) md.push(`| ${id} | ${reports.map((r) => { const t = r.tests.find((x) => x.id === id && x.theme === 'light') ?? r.tests.find((x) => x.id === id); return !t ? '·' : !t.loaded || t.dead ? '❌ ' + String(t.dead ?? 'not loaded').slice(0, 40) : t.errors.length ? `⚠️ ${t.errors.length} errors` : '✅'; }).join(' | ')} |`);
    md.push('');
  }
  return md.join('\n');
}

function main() {
  const args = process.argv.slice(2);
  const opt = (n) => { const i = args.indexOf('--' + n); if (i < 0) return undefined; const v = args[i + 1]; args.splice(i, 2); return v; };
  const outDir = path.resolve(opt('out') ?? 'conformance-results');
  const failOn = opt('fail-on');
  if (!args.length) { console.error('usage: node report.mjs [--out DIR] [--fail-on in|candidate] RUN.json [RUN.json …]'); process.exit(2); }
  const manifest = readJson(path.join(PKG, 'manifest.json'));
  const catalogue = readJson(path.join(PKG, 'catalogue.json'));
  fs.mkdirSync(outDir, { recursive: true });
  const reports = [];
  for (const f of args) {
    const file = readJson(f);
    if (!Array.isArray(file.runs)) { console.error(`${f}: no runs[] (see run.schema.json)`); process.exit(2); }
    const r = buildReport(file, path.dirname(path.resolve(f)), manifest, catalogue, path.join(outDir, 'diffs'));
    const name = `${r.client.platform}-${r.client.renderer}.json`.replace(/[^\w.-]+/g, '_');
    fs.writeFileSync(path.join(outDir, name), JSON.stringify(r, null, 1) + '\n');
    console.log(`${name}: in ${JSON.stringify(r.summary.in)}`);
    reports.push(r);
  }
  fs.writeFileSync(path.join(outDir, 'summary.md'), summaryMarkdown(reports, manifest, catalogue));
  console.log(path.join(outDir, 'summary.md'));
  if (failOn) {
    const bad = reports.flatMap((r) => Object.entries(r.features).filter(([, v]) => v.status === failOn && (v.verdict === 'fail' || v.verdict === 'partial')).map(([id]) => `${r.client.platform} ${r.client.renderer}: ${id}`));
    if (bad.length) { console.error(`${bad.length} ${failOn}-profile IDs fail:\n  ${bad.join('\n  ')}`); process.exit(1); }
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
