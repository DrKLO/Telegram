// Builds the stress kit (packages/stress): every level of every load of loads.ts as a widget, and
// manifest.json. Then the gates, which fail the build:
//   - size: every widget is a mini-widget (at most 3800 B after zlib at level 9 and 32 KB of HTML);
//   - through the sandbox (the dev app, Shadow DOM renderer, the measuring configuration of CLIENTS.md):
//     every widget boots, publishes `data-stress` and keeps its frame loop running, uses nothing outside
//     widget profile 1 (no profile, dropped-CSS, dropped-tag or blocked-URL diagnostic), throws nothing,
//     and is at most 560 px tall at 320 px wide. The nodes and the largest validated batch it measures go
//     into the manifest next to the table's estimates.
//
//   corepack pnpm tsx scripts/stress/build.ts [--sizes] [--origin http://127.0.0.1:5178] [--only shadow-]
// Without --origin it starts a dev server of this checkout on a free port; --sizes skips the sandbox gate
// (and keeps the measured values of the last full build).

import fs from 'node:fs';
import path from 'node:path';
import { chromium } from '@playwright/test';
import { MAX_HEIGHT, MEASURE_PARAMS, SIZE_LIMIT, STRESS, WIDGETS, crossesOf, generate, loadWidget, openDevApp, published, withDevServer, type Manifest, type ManifestWidget } from './lib';

const args = process.argv.slice(2);
const opt = (name: string) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : undefined);
const ONLY = opt('only');

/** Diagnostics that mean a widget used something outside widget profile 1 or the validator removed part of it. */
const PROFILE_DIAGS = /^(profile|css-dropped|tag-dropped|tag-replaced|attr-dropped|url-blocked|protocol-violation)$/;

async function gate(widgets: (ManifestWidget & { html: string })[], origin: string): Promise<string[]> {
  const failures: string[] = [];
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1200, height: 1000 }, deviceScaleFactor: 2 })).newPage();
  await openDevApp(page, origin, 'shadow', MEASURE_PARAMS);
  console.log(`\nthrough the sandbox (${origin}, shadow, compat): boots, frames, profile, at most ${MAX_HEIGHT.height} px at ${MAX_HEIGHT.width} px wide`);
  for (const w of widgets) {
    await loadWidget(page, w.html, `stress/${w.id}.html`, MAX_HEIGHT.width);
    const t0 = Date.now();
    let pub = null;
    let dead: string | null = null;
    // Two publishes after the first frame: the loop runs, and the second one carries a whole second of frames.
    while (Date.now() - t0 < 20_000) {
      pub = await published(page);
      dead = await page.evaluate(() => (window as any).__tw.state.dead);
      if (dead || (pub && pub.status === 'running' && pub.t >= 1000)) break;
      await page.waitForTimeout(150);
    }
    await page.waitForTimeout(300);
    const s = await page.evaluate(() => {
      const st = (window as any).__tw.state, x = (window as any).__stress;
      return { height: Math.ceil(x.height), maxBatch: x.maxBatch, nodes: st.stats?.nodes ?? 0, diags: Object.fromEntries(Object.entries(st.diags as Record<string, { count: number; details: string[] }>).map(([k, v]) => [k, v.details[0] ?? ''])), errors: (st.logs as { level: string; text: string }[]).filter((l) => l.level === 'error').map((l) => l.text.split('\n')[0]) };
    });
    const bad: string[] = [];
    if (dead) bad.push(`died: ${dead}`);
    else if (!pub || pub.status !== 'running' || pub.frames < 2) bad.push(`no running data-stress (${pub ? pub.status + ', ' + pub.frames + ' frames' : 'none'})`);
    const profile = Object.entries(s.diags).filter(([k]) => PROFILE_DIAGS.test(k));
    if (profile.length) bad.push('outside the profile: ' + profile.map(([k, d]) => `${k} ${d}`).join('; ').slice(0, 200));
    if (s.errors.length) bad.push('errors: ' + s.errors.join('; ').slice(0, 200));
    if (s.height > MAX_HEIGHT.height) bad.push(`${s.height} px tall`);
    w.measured = { nodes: s.nodes, maxBatchOps: s.maxBatch, height: s.height };
    console.log(`  ${w.id.padEnd(30)} ${String(s.height).padStart(4)} px ${String(s.nodes).padStart(5)} nodes ${String(s.maxBatch).padStart(6)} ops/batch ${pub ? String(pub.fps).padStart(3) + ' fps' : ''}${bad.length ? '  FAIL ' + bad.join(' | ') : ''}`);
    if (bad.length) failures.push(`${w.id}: ${bad.join(' | ')}`);
  }
  await browser.close();
  return failures;
}

/** The README's table of loads, between its stress:loads markers. */
function writeReadmeTable(m: Manifest) {
  const file = path.join(STRESS, 'README.md');
  const rows = ['| load | what it stresses | levels (over a strict limit in bold) | fit |', '|---|---|---|---|'];
  for (const l of m.loads) {
    const levels = l.widgets.map((id) => m.widgets.find((w) => w.id === id)!).map((w) => (w.crosses.length ? `**${w.value}**` : String(w.value))).join(', ');
    rows.push(`| \`${l.id}\` | ${l.title}: ${l.stresses} | ${levels} ${l.unit} | ${l.fit === 'heavy' ? 'A' : l.fit === 'plain' ? 'B' : ''} |`);
  }
  const text = fs.readFileSync(file, 'utf8');
  const begin = '<!-- stress:loads -->', end = '<!-- /stress:loads -->';
  const i = text.indexOf(begin), j = text.indexOf(end);
  if (i < 0 || j < i) throw new Error('README.md has no stress:loads markers');
  fs.writeFileSync(file, text.slice(0, i + begin.length) + '\n' + rows.join('\n') + '\n' + text.slice(j));
}

async function main() {
  const { widgets: all, manifest } = generate();
  const widgets = ONLY ? all.filter((w) => w.id.includes(ONLY)) : all;
  // A partial or --sizes build keeps the measured values of the last full build.
  const prevFile = path.join(STRESS, 'manifest.json');
  const prev = fs.existsSync(prevFile) ? new Map((JSON.parse(fs.readFileSync(prevFile, 'utf8')) as Manifest).widgets.map((w) => [w.id, w.measured])) : new Map();
  for (const w of all) w.measured = prev.get(w.id);

  if (!ONLY) fs.rmSync(WIDGETS, { recursive: true, force: true });
  for (const w of widgets) {
    fs.mkdirSync(path.dirname(path.join(STRESS, w.file)), { recursive: true });
    fs.writeFileSync(path.join(STRESS, w.file), w.html);
  }
  console.log(`${widgets.length} widgets in ${manifest.loads.length} loads`);
  const over = widgets.filter((w) => w.size.zlib > SIZE_LIMIT.zlib || w.size.raw > SIZE_LIMIT.raw);
  const largest = [...widgets].sort((a, b) => b.size.zlib - a.size.zlib)[0];
  console.log(`largest: ${largest.id}, ${largest.size.zlib} B after zlib -9, ${largest.size.raw} B of HTML (limits ${SIZE_LIMIT.zlib} / ${SIZE_LIMIT.raw})`);
  for (const w of over) console.log(`  OVER ${w.id}: ${w.size.zlib} B compressed, ${w.size.raw} B of HTML`);

  let failures: string[] = over.map((w) => `${w.id}: too large`);
  if (!args.includes('--sizes')) failures.push(...(await withDevServer(opt('origin'), (origin) => gate(widgets, origin))));

  for (const w of all) w.crosses = crossesOf(w);
  const out: Manifest = { ...manifest, widgets: all.map(({ html: _h, ...w }) => w) };
  fs.writeFileSync(prevFile, JSON.stringify(out, null, 1) + '\n');
  writeReadmeTable(out);
  if (failures.length) {
    console.log(`\n${failures.length} widget(s) failed:`);
    for (const f of failures) console.log('  ' + f);
    process.exitCode = 1;
  } else console.log('\nall gates passed');
}

main().catch((e) => { console.error(e); process.exit(1); });
