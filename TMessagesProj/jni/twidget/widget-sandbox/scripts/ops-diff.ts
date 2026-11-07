// Compares what two sandbox bundles render for the same widgets: each widget runs
// through packages/core/scripts/sandbox-node.ts with bundle A and bundle B (B is
// the one built from packages/core/src unless --b is given), the validated ops of
// each run are applied to a small document model, and the resulting trees are
// compared without node ids (a shim may reuse or renumber ids). Each widget
// first runs once to find clickable elements in document order, then runs
// again with pointerdown/up + click on each of them (at most 24) before the
// timer rounds, so event paths are diffed too. `adopt` elements (html, head,
// body) are attached where the renderer maps them; the validator sends no `ins`
// for them. Inline style is compared as a property map whether it arrived as a `style` attribute or as
// `style` ops. Every widget also runs twice on A; a widget whose two A runs
// differ is reported as nondeterministic, not as a difference. Both bundles run
// with a fixed Date, a seeded Math.random and a virtual host clock and random
// source, so time- and random-driven widgets compare too.
//
// Usage: pnpm tsx scripts/ops-diff.ts --a path/to/sandbox-bundle.js [--b other.js]
//          [--timers N] [--features canvasWords,binaryOps,webAnimations] [--policy strict] [--json out.json] corpus/compat corpus/generated/… file.html
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { bundleSandbox, runWidget, type RunResult } from '../packages/core/scripts/sandbox-node.ts';

interface Node {
  id: number;
  kind: 'el' | 'text' | 'comment';
  tag: string;
  attrs: Map<string, string>;
  style: Map<string, string>;
  data: string;
  css: string | null;
  canvas: string[];
  parent: Node | null;
  children: Node[];
}

const ROOT_ID = 1;
const PROTOCOL_HOST_KINDS = new Set(['listen', 'pd', 'raf', 'stat']);

function parseStyle(text: string): Map<string, string> {
  const out = new Map<string, string>();
  let depth = 0, quote = '', start = 0;
  const parts: string[] = [];
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quote) { if (c === '\\') i++; else if (c === quote) quote = ''; continue; }
    if (c === '"' || c === "'") quote = c;
    else if (c === '(') depth++;
    else if (c === ')') depth = Math.max(0, depth - 1);
    else if (c === ';' && depth === 0) { parts.push(text.slice(start, i)); start = i + 1; }
  }
  parts.push(text.slice(start));
  for (const part of parts) {
    const i = part.indexOf(':');
    if (i < 0) continue;
    const name = part.slice(0, i).trim().toLowerCase();
    const value = part.slice(i + 1).trim().replace(/\s+/g, ' ');
    if (name) out.set(name, value);
  }
  return out;
}

export function applyOps(ops: any[]): Node {
  const nodes = new Map<number, Node>();
  const root: Node = { id: ROOT_ID, kind: 'el', tag: '#document', attrs: new Map(), style: new Map(), data: '', css: null, canvas: [], parent: null, children: [] };
  nodes.set(ROOT_ID, root);
  const make = (id: number, kind: Node['kind'], tag: string): Node => ({ id, kind, tag, attrs: new Map(), style: new Map(), data: '', css: null, canvas: [], parent: null, children: [] });
  const detach = (n: Node) => {
    if (!n.parent) return;
    const list = n.parent.children;
    const i = list.indexOf(n);
    if (i >= 0) list.splice(i, 1);
    n.parent = null;
  };
  const setAttr = (n: Node, name: string, value: string) => {
    if (name === 'style') { n.style = parseStyle(value); return; }
    n.attrs.set(name, value);
  };
  for (const op of ops) {
    const n = nodes.get(op.id);
    switch (op.op) {
      case 'el': {
        const e = make(op.id, 'el', (op.ns === 'svg' ? 'svg:' : '') + op.tag);
        for (const [k, v] of op.attrs ?? []) setAttr(e, k, v);
        nodes.set(op.id, e);
        if (op.adopt) {
          const parent = op.adopt === 'html' ? root : root.children.find((c) => c.tag === 'html');
          if (parent) { e.parent = parent; parent.children.push(e); }
        }
        break;
      }
      case 'text': { const t = make(op.id, 'text', '#text'); t.data = op.data; nodes.set(op.id, t); break; }
      case 'comment': { const t = make(op.id, 'comment', '#comment'); t.data = op.data; nodes.set(op.id, t); break; }
      case 'data': if (n) n.data = op.data; break;
      case 'attr': if (n) setAttr(n, op.name, op.value); break;
      case 'rattr': if (n) { if (op.name === 'style') n.style.clear(); else n.attrs.delete(op.name); } break;
      case 'style':
        if (n) { if (op.value === '') n.style.delete(op.name); else n.style.set(op.name, op.value + (op.important ? ' !important' : '')); }
        break;
      case 'css': if (n) n.css = op.text; break;
      case 'canvas': if (n) for (const c of op.cmds ?? []) n.canvas.push(JSON.stringify(c)); break;
      case 'ins': {
        if (!n) break;
        detach(n);
        if (op.parent === 0) break;
        const p = op.parent === ROOT_ID ? root : nodes.get(op.parent);
        if (!p) break;
        n.parent = p;
        const before = op.before == null ? null : nodes.get(op.before);
        const i = before ? p.children.indexOf(before) : -1;
        if (i >= 0) p.children.splice(i, 0, n); else p.children.push(n);
        break;
      }
      case 'rm': if (n) { detach(n); nodes.delete(op.id); } break;
      default: if (n) n.attrs.set(`#${op.op}:${op.name ?? op.method ?? ''}`, JSON.stringify(op.value ?? op.args ?? null)); break;
    }
  }
  return root;
}

export function serialize(n: Node, indent = ''): string {
  if (n.kind !== 'el') return `${indent}${n.tag} ${JSON.stringify(n.data)}\n`;
  const attrs = [...n.attrs].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)).map(([k, v]) => ` ${k}=${JSON.stringify(v)}`).join('');
  const style = n.style.size ? ` {${[...n.style].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)).map(([k, v]) => `${k}:${v}`).join(';')}}` : '';
  let out = `${indent}<${n.tag}${attrs}${style}>\n`;
  if (n.css !== null) out += `${indent}  css ${JSON.stringify(n.css)}\n`;
  if (n.canvas.length) out += `${indent}  canvas ${n.canvas.length} ${hash(n.canvas.join('\n'))}\n`;
  for (const c of n.children) out += serialize(c, indent + '  ');
  return out;
}

function hash(s: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193); }
  return (h >>> 0).toString(16);
}

interface Outcome {
  tree: string;
  diags: string[];
  errors: string[];
  unknown: string[];
}

const CLICKABLE = /^(button|a|input|select|label|summary|li|td|th|span|div|svg:rect|svg:circle|svg:path|canvas)$/;
const MAX_CLICKS = 24;

export function clickTargets(n: Node, out: number[] = []): number[] {
  if (n.kind === 'el' && n.id !== ROOT_ID && (CLICKABLE.test(n.tag) || n.attrs.has('onclick') || n.attrs.has('role') || n.attrs.has('tabindex'))) out.push(n.id);
  for (const c of n.children) clickTargets(c, out);
  return out;
}

export const DETERMINISM = `(function () {
  var seed = 42;
  Math.random = function () { seed = (Math.imul(seed, 1103515245) + 12345) & 0x7fffffff; return seed / 0x80000000; };
  var NativeDate = Date, fixed = 1767225600000;
  function FixedDate() { var a = Array.prototype.slice.call(arguments); if (!new.target) return new NativeDate(fixed).toString(); return a.length ? Reflect.construct(NativeDate, a, new.target) : Reflect.construct(NativeDate, [fixed], new.target); }
  FixedDate.prototype = NativeDate.prototype;
  FixedDate.now = function () { return fixed; };
  FixedDate.parse = NativeDate.parse;
  FixedDate.UTC = NativeDate.UTC;
  globalThis.Date = FixedDate;
})();
`;

function deterministic() {
  let clock = 0, seed = 7;
  return {
    now: () => (clock += 4),
    random: (count: number) => Array.from({ length: count }, () => ((seed = (Math.imul(seed, 1103515245) + 12345) & 0x7fffffff) >>> 16) & 255),
  };
}

export async function run(html: string, bundle: string, timerRounds: number, features: string[] = [], policy: 'compat' | 'strict' = 'compat'): Promise<Outcome> {
  const unknown: string[] = [];
  const wire = { canvasWords: features.includes('canvasWords'), binaryOps: features.includes('binaryOps'), webAnimations: features.includes('webAnimations'), policy };
  const first = await runWidget(html, { bundle, timerRounds: 1, ...wire, ...deterministic() });
  const targets = clickTargets(applyOps(first.renderOps)).slice(0, MAX_CLICKS);
  const events = targets.flatMap((targetId) => [{ type: 'pointerdown', targetId }, { type: 'pointerup', targetId }, { type: 'click', targetId }]);
  const r: RunResult = await runWidget(html, { bundle, timerRounds, events, ...wire, ...deterministic(), unknownHost: (kind) => { if (!PROTOCOL_HOST_KINDS.has(kind)) unknown.push(kind); } });
  return {
    tree: serialize(applyOps(r.renderOps)),
    diags: r.diags.map((d) => d.kind).sort(),
    errors: r.logs.filter((l) => l.level === 'error').map((l) => l.text.split('\n')[0]),
    unknown: [...new Set(unknown)].sort(),
  };
}

function firstDifference(a: string, b: string): string {
  const la = a.split('\n'), lb = b.split('\n');
  for (let i = 0; i < Math.max(la.length, lb.length); i++) {
    if (la[i] !== lb[i]) return `line ${i + 1}:\n    A: ${(la[i] ?? '<end>').slice(0, 300)}\n    B: ${(lb[i] ?? '<end>').slice(0, 300)}`;
  }
  return '';
}

function widgets(args: string[]): string[] {
  const out: string[] = [];
  for (const a of args) {
    const st = fs.statSync(a);
    if (st.isDirectory()) {
      for (const f of fs.readdirSync(a).sort()) {
        const p = path.join(a, f);
        if (fs.statSync(p).isDirectory()) out.push(...widgets([p]));
        else if (f.endsWith('.html')) out.push(p);
      }
    } else out.push(a);
  }
  return out;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const argv = process.argv.slice(2);
  const opt = (name: string) => { const i = argv.indexOf(name); if (i < 0) return undefined; const v = argv[i + 1]; argv.splice(i, 2); return v; };
  const aPath = opt('--a');
  const bPath = opt('--b');
  const jsonOut = opt('--json');
  const timerRounds = Number(opt('--timers') ?? 3);
  const features = (opt('--features') ?? '').split(',').filter(Boolean);
  const policy = opt('--policy') === 'strict' ? 'strict' : 'compat';
  if (!aPath || !argv.length) { console.error('usage: tsx scripts/ops-diff.ts --a bundle.js [--b bundle.js] [--timers N] [--features a,b] [--policy strict] [--json out.json] <widget.html | dir>…'); process.exit(2); }
  const a = DETERMINISM + fs.readFileSync(aPath, 'utf8');
  const b = DETERMINISM + (bPath ? fs.readFileSync(bPath, 'utf8') : await bundleSandbox());
  const report: { file: string; status: string; detail?: string }[] = [];
  for (const file of widgets(argv)) {
    const html = fs.readFileSync(file, 'utf8');
    let status = 'same', detail = '';
    try {
      const [ra, ra2, rb] = [await run(html, a, timerRounds, features, policy), await run(html, a, timerRounds, features, policy), await run(html, b, timerRounds, features, policy)];
      if (ra.tree !== ra2.tree) status = 'nondeterministic';
      else if (ra.tree !== rb.tree) { status = 'tree'; detail = firstDifference(ra.tree, rb.tree); }
      else if (ra.errors.join('\n') !== rb.errors.join('\n')) { status = 'errors'; detail = `A: ${ra.errors.join(' | ')}\n    B: ${rb.errors.join(' | ')}`; }
      else if (ra.diags.join() !== rb.diags.join()) { status = 'diags'; detail = `A: ${ra.diags.join(',')}\n    B: ${rb.diags.join(',')}`; }
      if (ra.unknown.length || rb.unknown.length) detail += `${detail ? '\n    ' : ''}unknown host kinds A: ${ra.unknown.join(',') || '-'} B: ${rb.unknown.join(',') || '-'}`;
    } catch (e) {
      status = 'crash';
      detail = String(e);
    }
    report.push({ file, status, detail: detail || undefined });
    console.log(`${status.padEnd(16)} ${file}${detail ? '\n    ' + detail : ''}`);
  }
  const counts = report.reduce<Record<string, number>>((m, r) => ((m[r.status] = (m[r.status] ?? 0) + 1), m), {});
  console.log('\n' + Object.entries(counts).map(([k, v]) => `${k}: ${v}`).join(', '));
  if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify(report, null, 1));
  process.exit(report.some((r) => r.status !== 'same' && r.status !== 'nondeterministic') ? 1 : 0);
}
