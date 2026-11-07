// Widget profile, step 2: which features the corpora actually use. Every
// widget of corpus/{compat,generated,htmlbubbles-ported,attacks} runs through
// the real sandbox + validator under Node (scripts/sandbox-node.ts) and its
// validated render ops are mined: tags, form controls, canvas, inline SVG,
// CSS properties / values / functions / at-rules / selectors (css-tree over
// the sanitised stylesheets and the inline styles), sync queries. Then each
// feature is mapped to the three renderer classes (iframe/WebView, Blitz,
// litehtml) with the support table from probes.ts plus what the docs already
// established, and the impact per corpus is counted: how many widgets touch
// at least one feature a class cannot render, and which features those are.
// Output: reports/profile/corpus.json + Markdown tables on stdout for
// docs/widget-profile-draft.md (historical) and widget profile 1 (docs/profile-v1.md).
//
//   corepack pnpm tsx scripts/profile/corpus.ts [--groups compat,generated]

import * as csstree from 'css-tree';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runWidget } from '@telegram/widget-sandbox/node';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const out = path.join(root, 'reports', 'profile');
fs.mkdirSync(out, { recursive: true });
const groups = process.argv.includes('--groups') ? process.argv[process.argv.indexOf('--groups') + 1].split(',') : ['compat', 'generated', 'htmlbubbles-ported', 'attacks'];

/** Feature keys, each the name under which a widget's use of it is counted. */
type Features = Map<string, number>;

const TAG_FEATURES: Record<string, string> = {
  svg: 'svg', canvas: 'canvas', input: 'form:input', select: 'form:select', textarea: 'form:textarea', button: 'form:button',
  progress: 'progress/meter', meter: 'progress/meter', details: 'details', dialog: 'dialog', table: 'table', video: 'media', audio: 'media',
  img: 'img', iframe: 'iframe', pre: 'pre', code: 'code', ul: 'list', ol: 'list',
};
const THREE_D = /\b(rotate[XYZ]|rotate3d|translateZ|translate3d|scaleZ|scale3d|matrix3d|perspective)\s*\(/i;

function bump(f: Features, key: string) {
  f.set(key, (f.get(key) ?? 0) + 1);
}

function declarationFeatures(f: Features, prop: string, value: string) {
  const p = prop.toLowerCase();
  const v = value.toLowerCase();
  const fn = (name: string) => new RegExp(`\\b${name}\\(`).test(v);
  switch (p) {
    case 'display':
      if (/\bgrid\b/.test(v)) bump(f, 'display:grid');
      else if (/\bflex\b/.test(v)) bump(f, 'display:flex');
      else if (/\btable/.test(v)) bump(f, 'display:table');
      break;
    case 'position':
      if (v.includes('sticky')) bump(f, 'position:sticky');
      else if (v.includes('fixed')) bump(f, 'position:fixed');
      else if (v.includes('absolute')) bump(f, 'position:absolute');
      break;
    case 'overflow': case 'overflow-x': case 'overflow-y':
      if (/auto|scroll/.test(v)) bump(f, 'overflow:auto/scroll');
      else if (v.includes('hidden')) bump(f, 'overflow:hidden');
      break;
    case 'transform':
      if (THREE_D.test(v)) bump(f, 'transform:3d');
      else if (v !== 'none') bump(f, 'transform:2d');
      break;
    case 'transition': case 'transition-property': case 'transition-duration': bump(f, 'transition'); break;
    case 'animation': case 'animation-name': bump(f, 'animation'); break;
    case 'box-shadow': if (v !== 'none') bump(f, 'box-shadow'); break;
    case 'text-shadow': if (v !== 'none') bump(f, 'text-shadow'); break;
    case 'opacity': bump(f, 'opacity'); break;
    case 'filter': if (v !== 'none') bump(f, 'filter'); break;
    case 'backdrop-filter': bump(f, 'backdrop-filter'); break;
    case 'clip-path': bump(f, 'clip-path'); break;
    case 'mask': case 'mask-image': case '-webkit-mask': case '-webkit-mask-image': bump(f, 'mask'); break;
    case 'mix-blend-mode': bump(f, 'mix-blend-mode'); break;
    case 'aspect-ratio': bump(f, 'aspect-ratio'); break;
    case 'gap': case 'row-gap': case 'column-gap': bump(f, 'gap'); break;
    case 'border-radius': case 'border-top-left-radius': case 'border-top-right-radius': case 'border-bottom-left-radius': case 'border-bottom-right-radius': bump(f, 'border-radius'); break;
    case 'object-fit': bump(f, 'object-fit'); break;
    case 'writing-mode': bump(f, 'writing-mode'); break;
    case 'scroll-snap-type': case 'scroll-behavior': bump(f, 'scroll-snap/behavior'); break;
    case 'user-select': case 'pointer-events': case 'cursor': bump(f, 'ux-props'); break;
    case 'outline': case 'outline-offset': bump(f, 'outline'); break;
    case 'text-overflow': if (v.includes('ellipsis')) bump(f, 'text-overflow:ellipsis'); break;
    case '-webkit-line-clamp': case 'line-clamp': bump(f, 'line-clamp'); break;
    case 'white-space': if (/pre/.test(v)) bump(f, 'white-space:pre*'); break;
    case 'font-variant-numeric': case 'font-feature-settings': bump(f, 'font-features'); break;
    case 'letter-spacing': bump(f, 'letter-spacing'); break;
    case 'background-clip': case '-webkit-background-clip': if (v.includes('text')) bump(f, 'background-clip:text'); break;
    case 'color-scheme': bump(f, 'color-scheme'); break;
    case 'accent-color': bump(f, 'accent-color'); break;
    case 'appearance': case '-webkit-appearance': bump(f, 'appearance'); break;
    case 'contain': case 'content-visibility': bump(f, 'contain'); break;
    case 'inset': case 'inset-inline': case 'inset-block': case 'margin-inline': case 'margin-block': case 'padding-inline': case 'padding-block':
    case 'margin-inline-start': case 'margin-inline-end': case 'padding-inline-start': case 'padding-inline-end': case 'inline-size': case 'block-size': case 'min-inline-size': case 'max-inline-size':
      bump(f, 'logical-props'); break;
  }
  if (p.startsWith('grid-')) bump(f, 'grid-*props');
  if (p.startsWith('--')) bump(f, 'custom-props');
  if (fn('var')) bump(f, 'var()');
  if (fn('calc')) bump(f, 'calc()');
  if (fn('clamp') || fn('min') || fn('max')) bump(f, 'clamp/min/max()');
  if (fn('linear-gradient') || fn('repeating-linear-gradient')) bump(f, 'linear-gradient');
  if (fn('radial-gradient') || fn('repeating-radial-gradient')) bump(f, 'radial-gradient');
  if (fn('conic-gradient') || fn('repeating-conic-gradient')) bump(f, 'conic-gradient');
  if (fn('light-dark')) bump(f, 'light-dark()');
  if (fn('color-mix')) bump(f, 'color-mix()');
  if (/\b(oklch|oklab|lab|lch|hwb)\(/.test(v)) bump(f, 'modern-color-spaces');
  if (fn('url') && v.includes('data:image/svg')) bump(f, 'url(data:svg)');
  else if (fn('url') && v.includes('data:image')) bump(f, 'url(data:image)');
  if (/\d(vw|vh|vmin|vmax|dvh|svh)\b/.test(v)) bump(f, 'viewport-units');
  if (/\b(env)\(/.test(v)) bump(f, 'env()');
  if (v.includes('!important')) bump(f, '!important');
  if (/\b\d+(\.\d+)?(ch|ex|rem)\b/.test(v) && /ch\b/.test(v)) bump(f, 'ch-unit');
}

function cssFeatures(f: Features, text: string) {
  let ast: csstree.CssNode;
  try { ast = csstree.parse(text, { parseValue: false, parseCustomProperty: false, positions: false }); } catch { bump(f, 'css:unparsable'); return; }
  csstree.walk(ast, {
    enter(node: csstree.CssNode) {
      if (node.type === 'Declaration') declarationFeatures(f, node.property, csstree.generate(node.value) + (node.important ? ' !important' : ''));
      else if (node.type === 'Atrule') bump(f, '@' + node.name.toLowerCase());
      else if (node.type === 'PseudoClassSelector') {
        const n = node.name.toLowerCase();
        if (['hover', 'active', 'focus', 'focus-visible', 'focus-within', 'checked', 'disabled', 'empty', 'is', 'where', 'has', 'not', 'nth-child', 'nth-of-type', 'first-child', 'last-child', 'root'].includes(n)) bump(f, ':' + n);
        else bump(f, ':other-pseudo-class');
      } else if (node.type === 'PseudoElementSelector') bump(f, '::' + node.name.toLowerCase());
      else if (node.type === 'Rule' && (node as any).block && (this as any).rule && (this as any).rule !== node) bump(f, 'css-nesting');
      else if (node.type === 'AttributeSelector') bump(f, '[attr-selector]');
    },
  });
}

function inlineFeatures(f: Features, name: string, value: string) {
  declarationFeatures(f, name, value);
}

interface WidgetReport { group: string; file: string; loaded: boolean; dead: string | null; features: Record<string, number>; elements: number; queries: string[] }

async function analyse(group: string, file: string): Promise<WidgetReport> {
  const html = fs.readFileSync(file, 'utf8');
  const res = await runWidget(html, { timerRounds: 2 });
  const f: Features = new Map();
  let elements = 0;
  for (const op of res.renderOps as any[]) {
    switch (op.op) {
      case 'el': {
        elements++;
        const tag = String(op.tag).toLowerCase();
        if (op.ns === 'svg') bump(f, 'svg');
        else if (TAG_FEATURES[tag]) bump(f, TAG_FEATURES[tag]);
        if (op.tag === 'input') { const t = (op.attrs as [string, string][]).find((a) => a[0] === 'type')?.[1]?.toLowerCase() ?? 'text'; bump(f, `input:${t}`); }
        for (const [name, value] of op.attrs as [string, string][]) {
          if (name === 'style') for (const d of value.split(';')) { const i = d.indexOf(':'); if (i > 0) inlineFeatures(f, d.slice(0, i).trim(), d.slice(i + 1).trim()); }
          if (name === 'contenteditable') bump(f, 'contenteditable');
          if (name === 'tabindex') bump(f, 'tabindex');
        }
        break;
      }
      case 'style': inlineFeatures(f, op.name, op.value); break;
      case 'css': cssFeatures(f, op.text); break;
      case 'canvas': bump(f, 'canvas:cmds'); break;
      case 'prop': bump(f, `prop:${op.name}`); break;
      case 'call': bump(f, `call:${op.method}`); break;
    }
  }
  const queries = [...new Set((res.queries as any[]).map((q) => q.q))];
  for (const q of queries) bump(f, `query:${q}`);
  // what the scripts did matters for the behaviour profile too
  const diagKinds = new Set(res.diags.map((d) => d.kind));
  for (const k of ['unsupported', 'script-skipped', 'network-blocked', 'url-blocked', 'tag-dropped', 'css-dropped', 'timer-limit']) if (diagKinds.has(k) || [...diagKinds].some((d) => d.startsWith('validator:' + k))) bump(f, 'diag:' + k);
  const dead = res.logs.find((l) => l.level === 'error' && /killed|terminated/i.test(l.text))?.text ?? null;
  return { group, file: path.relative(path.join(root, 'corpus'), file), loaded: true, dead, features: Object.fromEntries(f), elements, queries };
}

function htmlFiles(dir: string): string[] {
  const outFiles: string[] = [];
  if (!fs.existsSync(dir)) return outFiles;
  for (const e of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) outFiles.push(...htmlFiles(p));
    else if (e.name.endsWith('.html')) outFiles.push(p);
  }
  return outFiles;
}

// ───────────── renderer classes: what each one cannot render (from probes.md + the docs) ─────────────

interface Verdict { blitz?: 'no' | 'partial'; litehtml?: 'no' | 'partial'; note: string }
const SUPPORT: Record<string, Verdict> = {
  'display:grid': { litehtml: 'no', note: 'litehtml: grid collapses to one column' },
  'grid-*props': { litehtml: 'no', note: 'grid properties' },
  'svg': { blitz: 'partial', litehtml: 'no', note: 'litehtml: not drawn; Blitz: drawn, CSS not applied' },
  'canvas': { blitz: 'no', litehtml: 'no', note: 'command stream not replayed in either prototype (implementable)' },
  'canvas:cmds': { blitz: 'no', litehtml: 'no', note: 'same' },
  'transform:2d': { litehtml: 'no', note: 'litehtml has no transforms' },
  'transform:3d': { blitz: 'no', litehtml: 'no', note: 'Blitz: 2D only (card flips show the back)' },
  'transition': { blitz: 'partial', litehtml: 'partial', note: 'no animation driver: the end state is applied, the motion is lost' },
  'animation': { blitz: 'no', litehtml: 'no', note: 'same' },
  '@keyframes': { blitz: 'no', litehtml: 'no', note: 'same' },
  'box-shadow': { litehtml: 'no', note: 'litehtml: no shadows' },
  'text-shadow': { litehtml: 'no', note: 'litehtml: no shadows' },
  'opacity': { litehtml: 'no', note: 'litehtml: no opacity' },
  'filter': { litehtml: 'no', note: 'litehtml: no filters' },
  'backdrop-filter': { blitz: 'no', litehtml: 'no', note: 'neither' },
  'clip-path': { litehtml: 'no', note: 'litehtml: no clipping paths' },
  'mask': { blitz: 'no', litehtml: 'no', note: 'neither' },
  'mix-blend-mode': { blitz: 'no', litehtml: 'no', note: 'neither' },
  'position:sticky': { blitz: 'partial', litehtml: 'no', note: 'static layout only' },
  'overflow:auto/scroll': { blitz: 'partial', litehtml: 'partial', note: 'clips, but the box does not scroll' },
  'form:input': { blitz: 'partial', litehtml: 'partial', note: 'boxes without values / sliders / pickers' },
  'form:select': { blitz: 'partial', litehtml: 'no', note: 'no dropdown; litehtml empty box' },
  'form:textarea': { blitz: 'partial', litehtml: 'partial', note: 'no editing' },
  'progress/meter': { blitz: 'partial', litehtml: 'partial', note: 'no native widget' },
  'dialog': { blitz: 'partial', litehtml: 'partial', note: 'no top layer' },
  'line-clamp': { litehtml: 'no', note: 'litehtml: no line clamping' },
  'background-clip:text': { blitz: 'no', litehtml: 'no', note: 'neither' },
  'object-fit': { litehtml: 'no', note: 'litehtml: no object-fit' },
  'writing-mode': { blitz: 'no', litehtml: 'no', note: 'neither' },
  'scroll-snap/behavior': { blitz: 'no', litehtml: 'no', note: 'no scrolling' },
  'contenteditable': { blitz: 'no', litehtml: 'no', note: 'no editing' },
  'table': { litehtml: 'partial', note: 'litehtml lays tables out but ignores CSS height on cells (probe `table`)' },
};
/** Overrides from the measured probes (filled in by `applyProbes`). */
const PROBE_TO_FEATURE: Record<string, string[]> = {
  grid: ['display:grid', 'grid-*props'], 'flex-gap': ['gap'], 'box-shadow': ['box-shadow'], opacity: ['opacity'], 'transform-2d': ['transform:2d'], 'transform-3d': ['transform:3d'],
  'svg-inline': ['svg'], canvas: ['canvas', 'canvas:cmds'], 'input-value': ['form:input'], select: ['form:select'], 'aspect-ratio': ['aspect-ratio'], clamp: ['clamp/min/max()'],
  calc: ['calc()'], var: ['var()', 'custom-props'], vw: ['viewport-units'], 'light-dark': ['light-dark()'], 'color-mix': ['color-mix()'], filter: ['filter'], 'pseudo-before': ['::before', '::after'],
  'nth-child': [':nth-child'], 'is-selector': [':is', ':where'], 'has-selector': [':has'], nesting: ['css-nesting'], supports: ['@supports'], 'media-width': ['@media'], 'keyframes-forwards': ['@keyframes', 'animation'],
  'position-absolute': ['position:absolute'], 'z-index': ['z-index'], 'logical-margin': ['logical-props'], 'clip-path': ['clip-path'], 'linear-gradient': ['linear-gradient'], 'radial-gradient': ['radial-gradient'],
  'conic-gradient': ['conic-gradient'], 'text-shadow': ['text-shadow'], 'details-open': ['details'], table: ['table'], 'table-first': ['table:first-in-body'], sticky: ['position:sticky'], 'pre-wrap': ['white-space:pre*'], 'word-wrap': ['overflow-wrap'],
  'img-svg': ['url(data:svg)'], 'bg-image': ['url(data:image)'], 'img-png': ['img'], button: ['form:button'],
};
function applyProbes() {
  const file = path.join(out, 'probes.json');
  if (!fs.existsSync(file)) { console.error('probes.json missing: run scripts/profile/probes.ts first (the support table falls back to the docs)'); return; }
  const probes = JSON.parse(fs.readFileSync(file, 'utf8')) as { key: string; validator: boolean; blitz: { pass: boolean | null }; litehtml: { pass: boolean | null } }[];
  for (const p of probes) {
    for (const feature of PROBE_TO_FEATURE[p.key] ?? []) {
      const v: Verdict = SUPPORT[feature] ?? { note: `probe ${p.key}` };
      if (p.blitz.pass === false) v.blitz ??= 'no'; else if (p.blitz.pass === true && v.blitz === 'no') v.blitz = 'partial';
      if (p.litehtml.pass === false) v.litehtml ??= 'no'; else if (p.litehtml.pass === true && v.litehtml === 'no') v.litehtml = 'partial';
      if (p.blitz.pass === true && v.blitz === 'partial' && !/CSS|values|clips|static|dropdown|editing|native|top layer/.test(v.note)) delete v.blitz;
      if (p.litehtml.pass === true && v.litehtml === 'partial' && !/CSS|values|clips|static|dropdown|editing|native|top layer/.test(v.note)) delete v.litehtml;
      SUPPORT[feature] = v;
    }
  }
}

async function main() {
  applyProbes();
  let reports: WidgetReport[] = [];
  // --tables-only: rebuild the tables from the last run's corpus.json (new probe verdicts, no sandbox runs)
  const cached = path.join(out, 'corpus.json');
  if (process.argv.includes('--tables-only') && fs.existsSync(cached)) reports = (JSON.parse(fs.readFileSync(cached, 'utf8')) as { widgets: WidgetReport[] }).widgets;
  for (const group of reports.length ? [] : groups) {
    const files = htmlFiles(path.join(root, 'corpus', group));
    for (const file of files) {
      process.stdout.write(`${group}/${path.basename(file)} … `);
      try {
        const r = await analyse(group, file);
        reports.push(r);
        console.log(`${Object.keys(r.features).length} features`);
      } catch (e) {
        console.log(`failed: ${String(e).split('\n')[0]}`);
      }
    }
  }
  fs.writeFileSync(path.join(out, 'corpus.json'), JSON.stringify({ support: SUPPORT, widgets: reports }, null, 1));

  // ── tables ──
  const md: string[] = [];
  const byGroup = new Map<string, WidgetReport[]>();
  for (const r of reports) byGroup.set(r.group, [...(byGroup.get(r.group) ?? []), r]);
  const allFeatures = new Map<string, number>();
  for (const r of reports) for (const k of Object.keys(r.features)) allFeatures.set(k, (allFeatures.get(k) ?? 0) + 1);
  const groupList = [...byGroup.keys()];
  md.push(`## Features by corpus (% of widgets using the feature at least once)`, '', `| Feature | ${groupList.map((g) => `${g} (${byGroup.get(g)!.length})`).join(' | ')} | Blitz | litehtml |`, `|---|${groupList.map(() => '---').join('|')}|---|---|`);
  const pct = (g: string, k: string) => { const rs = byGroup.get(g)!; const n = rs.filter((r) => r.features[k]).length; return n ? `${Math.round((100 * n) / rs.length)}% (${n})` : '·'; };
  const mark = (v?: 'no' | 'partial') => (v === 'no' ? '❌' : v === 'partial' ? '◐' : '✅');
  const interesting = [...allFeatures.entries()].filter(([k]) => !k.startsWith('prop:') && !k.startsWith('call:') && !k.startsWith('diag:')).sort((a, b) => b[1] - a[1]);
  for (const [k] of interesting) md.push(`| \`${k}\` | ${groupList.map((g) => pct(g, k)).join(' | ')} | ${mark(SUPPORT[k]?.blitz)} | ${mark(SUPPORT[k]?.litehtml)} |`);
  md.push('', `## Impact per renderer class (widgets touching at least one feature the class cannot render fully)`, '');
  for (const cls of ['blitz', 'litehtml'] as const) {
    md.push(`### ${cls}`, '', `| Corpus | Widgets | Affected (❌) | Affected (❌ or ◐) | Top features |`, `|---|---|---|---|---|`);
    for (const g of groupList) {
      const rs = byGroup.get(g)!;
      const hard = rs.filter((r) => Object.keys(r.features).some((k) => SUPPORT[k]?.[cls] === 'no'));
      const any = rs.filter((r) => Object.keys(r.features).some((k) => SUPPORT[k]?.[cls]));
      const counts = new Map<string, number>();
      for (const r of any) for (const k of Object.keys(r.features)) if (SUPPORT[k]?.[cls]) counts.set(k, (counts.get(k) ?? 0) + 1);
      const top = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, n]) => `${k} ${n}`).join(', ');
      md.push(`| ${g} | ${rs.length} | ${hard.length} (${Math.round((100 * hard.length) / rs.length)}%) | ${any.length} (${Math.round((100 * any.length) / rs.length)}%) | ${top} |`);
    }
    md.push('');
  }
  md.push(`## Sync queries and DOM calls used`, '', `| Key | ${groupList.join(' | ')} |`, `|---|${groupList.map(() => '---').join('|')}|`);
  for (const [k] of [...allFeatures.entries()].filter(([k]) => k.startsWith('query:') || k.startsWith('call:') || k.startsWith('prop:')).sort((a, b) => b[1] - a[1])) md.push(`| \`${k}\` | ${groupList.map((g) => pct(g, k)).join(' | ')} |`);
  fs.writeFileSync(path.join(out, 'corpus.md'), md.join('\n') + '\n');
  console.log(md.join('\n'));
}

main().catch((e) => { console.error(e); process.exit(1); });
