// The fast path of a style op (css-sanitizer.ts `fastStyleValue`) against the full path through css-tree: whatever the
// fast path accepts, the full path returns byte for byte, with no diagnostic, in every CssEnv (both colour schemes, the
// iframe's document scope and the shadow scope, strict and compat, reduced motion, native scrolling). The values: every
// declaration of the corpora (`corpus/**`, attacks included), the conformance set and the stress kit (stylesheets,
// style attributes and the style values their scripts write as literals), each also under the fast path's other
// properties, plus a generated set (every pair of token kinds with every separator, escapes, strings, url(), var(),
// env(), attr(), light-dark(), `!important`, comments, non-ASCII, exponents, huge numbers, a seeded fuzz) and the shapes
// the stress kit and the showcase send each frame.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import * as csstree from 'css-tree';
import { fastStyleValue, sanitizeStyleProperty, sanitizeStylePropertyFull, setCssFastPath, type CssEnv } from '../../src/host/css-sanitizer.ts';
import { Validator } from '../../src/host/validator.ts';
import { PROFILE_TABLES } from '../../src/shared/profile-tables.ts';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');

const ENVS: CssEnv[] = [];
for (const colorScheme of ['light', 'dark'] as const)
  for (const scope of ['document', 'shadow'] as const)
    for (const strict of [false, true])
      for (const reducedMotion of [false, true])
        for (const nativeScrolling of [false, true]) ENVS.push({ colorScheme, scope, strict, reducedMotion, nativeScrolling });
/** The envs for the cross products: the two corners, one per declaration in turn. */
const CORNERS: CssEnv[] = [
  { colorScheme: 'light', scope: 'document' },
  { colorScheme: 'dark', scope: 'shadow', strict: true, reducedMotion: true, nativeScrolling: true },
];
let corner = 0;
const nextCorner = () => [CORNERS[corner++ & 1]];

const PROPS = PROFILE_TABLES.css.properties as readonly string[];
/** One property per rule of the fast path and the ones it never takes, for the generated set. */
const RULE_PROPS = ['transform', 'rotate', 'translate', 'scale', 'position', 'display', 'user-select', '-webkit-user-select', 'overflow',
  'overflow-y', 'background-clip', 'color', 'width', 'background-image', 'background', 'box-shadow', 'transition', 'grid-template-columns',
  'z-index', 'opacity', 'font-size', '--x', '--Mixed_case', 'font', 'font-family', 'filter', 'backdrop-filter', 'color-scheme', 'Transform',
  'cursor', 'content', '--1x', '-', '', '--', 'a b', '__proto__'];

interface Tally { checked: number; accepted: number }

/** Checks one declaration in `envs`; returns whether the fast path took it. */
function check(name: string, value: string, envs: CssEnv[], tally: Tally): void {
  tally.checked++;
  const fast = fastStyleValue(name, value);
  if (fast === undefined) return;
  tally.accepted++;
  for (const env of envs) {
    const diags: string[] = [];
    const full = sanitizeStylePropertyFull(name, value, (k, d) => diags.push(`${k}: ${d}`), env);
    assert.deepEqual({ value: full?.value ?? null, diags }, { value: fast, diags: [] },
      `${name}: ${JSON.stringify(value)} in ${JSON.stringify(env)}`);
  }
}

// ───────────── the corpora ─────────────

function walk(dir: string, out: string[]): void {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p, out);
    else if (/\.(html|css)$/.test(entry.name)) out.push(p);
  }
}

const decode = (s: string) => s.replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
const kebab = (s: string) => s.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());

/** Every declaration of the corpora: the authored text of its value (and css-tree's own text of it). */
function corpusDeclarations(): { files: number; decls: [string, string][] } {
  const files: string[] = [];
  for (const dir of ['corpus', 'packages/conformance', 'packages/stress/widgets']) walk(path.join(repoRoot, dir), files);
  const seen = new Set<string>();
  const decls: [string, string][] = [];
  const add = (name: string, value: string) => {
    const key = name + '\u0000' + value;
    if (seen.has(key)) return;
    seen.add(key);
    decls.push([name, value]);
  };
  const sheet = (css: string, context: 'stylesheet' | 'declarationList') => {
    let ast: csstree.CssNode;
    try { ast = csstree.parse(css, { context, positions: true, parseCustomProperty: true, onParseError: () => {} } as csstree.ParseOptions); } catch { return; }
    csstree.walk(ast, (node) => {
      if (node.type !== 'Declaration' || !node.value.loc) return;
      const raw = css.slice(node.value.loc.start.offset, node.value.loc.end.offset);
      add(node.property, raw);
      add(node.property, raw.trim());
      try { add(node.property, csstree.generate(node.value)); } catch { /* a Raw value */ }
    });
  };
  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    if (file.endsWith('.css')) { sheet(text, 'stylesheet'); continue; }
    for (const m of text.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)) sheet(m[1], 'stylesheet');
    for (const m of text.matchAll(/\sstyle\s*=\s*(?:"([^"]*)"|'([^']*)')/gi)) sheet(decode(m[1] ?? m[2]), 'declarationList');
    // What scripts write as literals: el.style.prop = '…', style.setProperty('name', '…'), style.cssText = '…'.
    for (const m of text.matchAll(/\.style\.([a-zA-Z]+)\s*=\s*(['"])((?:(?!\2)[^\\\n]|\\.)*)\2/g)) {
      if (m[1] === 'cssText') sheet(m[3], 'declarationList');
      else add(kebab(m[1]), m[3]);
    }
    for (const m of text.matchAll(/\.setProperty\(\s*(['"])([-\w]+)\1\s*,\s*(['"])((?:(?!\3)[^\\\n]|\\.)*)\3/g)) add(m[2], m[4]);
  }
  return { files: files.length, decls };
}

// ───────────── the generated set ─────────────

/** A seeded PRNG (mulberry32): the same set on every run. */
function rng(seed: number): () => number {
  return () => {
    seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const TOKENS = ['a', 'A1', 'red', '-b', '--c', '-webkit-box', 'x', 'text', 'sticky', 'auto', 'inherit', '1', '-1', '0', '.5', '-.5', '1.25', '10%', '-10%',
  '10px', '-10px', '10PX', '1ex', '2fr', '3Q', '#fff', '#FFF0', '#-', '#1a', 'rgb(1,2,3)', 'hsl(1,2%,3%)', 'scale(2)', 'foo(1)', 'calc(1px)',
  'translateX(-1px)', 'rotateX(1deg)'];
const SEPARATORS = ['', ' ', '  ', ',', ', ', ' ,', ' , '];
const EDGES = [
  // escapes and strings
  '\\31 0px', '1\\30 px', '#\\66 ff', 'red\\9', '\\72 ed', 'r\\ed', '"a"', "'a'", '"a\\"b"', 'counter(a,"x")',
  // URLs and substitutions
  'url(#a)', 'url("#a")', 'URL(#a)', 'url( #a )', 'image-set("a.png" 1x)', 'image(#fff)', 'src("a")', 'cross-fade(red,blue)',
  'var(--x)', 'var(--x,1px)', 'VAR(--x)', 'env(safe-area-inset-top)', 'env(safe-area-inset-bottom,8px)', 'env(foo,1px)', 'env(foo)',
  'attr(data-x)', 'attr(title)', 'attr(href)', 'light-dark(red,blue)', 'Light-Dark(#fff,#000)', 'rgb(light-dark(1,2),2,3)',
  'expression(alert(1))', '-moz-binding', 'element(#a)', 'paint(x)',
  // what the declaration parser treats specially
  'red !important', 'red!important', 'red;color:blue', 'red}a{color:blue', 'red/**/', '/**/red', 'red /* x */ blue', 'a{', '<a>', '@x',
  // whitespace other than the space, non-ASCII, controls
  'red\tblue', 'red\nblue', 'red\rblue', 'red\fblue', ' red', 'réd', 'r\u0000d', '​red', '1px 2px', '😀',
  // numbers
  '1e3', '1E3', '1e+3', '1e-3', '1e3px', '1e-3px', '1e', '1ex', '1e-x', '1.', '1.5.5', '.', '-', '--', '-.', '-.5.5', '+1', '+.5px',
  '1e999px', '99999999999999999999999999999999px', '0.0000000000000000001', '-0', '00012px', '1px-2px', '10-x', '10--', '1-2', '1 -2',
  '10vw', '10VH', '10dvmin', '10cqw', '10Q', '10q', '10fr', '10%10%', '10px10px', '0#fff', '10px#fff', '#fff#000',
  // structure
  '#', '##', ')', '(', '((', 'a(', 'a(b', 'a)', 'rgb(1,2,3', 'rgb(1,2,3))', 'rgb (1,2,3)', 'a,', ',a', 'a,,b', 'rgb(,1)', 'rgb(1,)',
  'rgb()', 'calc()', 'calc(1px + 2px)', 'calc(100% - 10px)', 'calc(100%- 10px)', 'calc(2*3px)', 'calc(1px/2)', '1/2', 'a / b', '[a]',
  // what the profile rules decide
  'rotateX(1deg)', 'translate3d(1px,2px,3px)', 'perspective(1px)', 'matrix3d(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)', 'scale3d(1,1,1)',
  'sticky', '-webkit-sticky', 'STICKY', 'auto', 'scroll', 'overlay', 'hidden', 'clip', 'text', 'TEXT', 'border-box text', 'x 45deg', 'z',
  '1 2 45deg', '45deg', '1px 2px 3px', '1px 2px', '1px,2px,3px', 'inherit', 'INHERIT', 'revert-layer', 'initial', 'unset', 'flex', 'FLEX',
  'block flex', 'none', 'contents', 'grid', 'blur(2px)', 'blur(2px) grayscale(1)', 'drop-shadow(0 0 1px red)', 'light dark', 'dark',
  'system-ui', '-apple-system', 'Arial', '12px/1.5 Arial', 'bold 12px sans-serif', 'repeat(3,1fr)', 'minmax(10px,1fr)', 'steps(4)',
  'cubic-bezier(.1,.2,.3,.4)', 'linear(0,.5,1)', 'conic-gradient(#f00 10deg,rgba(0,0,0,.5) 20deg)', 'counter(a)', 'counters(a,b)',
  'x'.repeat(256), 'x'.repeat(257), ('1px ').repeat(64).trim(), ('1px ').repeat(65).trim(),
];
/** The shapes the stress kit and the showcase send every frame (the run-time style ops of the corpora), with random numbers. */
const SHAPES = ['translate(Lpx,Lpx) rotate(Ddeg)', 'hsl(D,P%,P%)', 'linear-gradient(Ddeg,hsl(D,P%,P%),hsl(D,P%,P%))', 'translateY(Lpx)',
  'translateX(L)', 'Lpx', 'P%', 'rgba(B,B,B,F)', 'rgba(B, B, B, F)', 'linear-gradient(Ddeg, rgba(B,B,B,F) P%, rgba(B,B,B,F) P%)',
  'conic-gradient(#HcH Ddeg, rgba(B,B,B,F) Ddeg)', 'F', 'transform Dms cubic-bezier(F,F,F,F)', 'width Dms ease', 'translateX(calc(P% + Lpx))',
  'L Lpx Lpx L', 'D', 'scale(F)', 'rotate(Ddeg)', 'translate(-50%,-50%) scale(F)', '0 0 Lpx rgba(B,B,B,F)', 'Lpx solid #HHH', 'Dms'];

function shapeValue(shape: string, r: () => number): string {
  const num = (scale: number, decimals: boolean) => {
    const v = (r() - 0.5) * 2 * scale;
    const s = decimals ? v.toFixed(Math.floor(r() * 4)) : String(Math.round(v));
    return r() < 0.1 ? s.replace(/^(-?)0\./, '$1.') : s;
  };
  return shape.replace(/[LDPBFH]/g, (k) => {
    switch (k) {
      case 'L': return num(500, true);
      case 'D': return String(Math.abs(Math.round((r() - 0.5) * 720)));
      case 'P': return String(Math.round(r() * 100));
      case 'B': return String(Math.floor(r() * 256));
      case 'F': return r().toFixed(Math.floor(r() * 4));
      default: return Math.floor(r() * 16).toString(16);
    }
  });
}

function generated(): string[] {
  const out = new Set<string>(EDGES);
  for (const a of TOKENS) {
    out.add(a);
    for (const b of TOKENS) for (const sep of SEPARATORS) {
      out.add(a + sep + b);
      out.add(`calc(${a}${sep}${b})`);
      out.add(`translate(${a}${sep}${b}) ${b}`);
    }
  }
  // A seeded fuzz over the fast alphabet's characters and the ones next to it.
  const r = rng(0x5eed);
  const pieces = [...TOKENS, ...SEPARATORS, '(', ')', '-', '.', '%', '#', 'e', 'E', '+', '/', '*', '!', '\\', '"', '_', 'é', '\t', '0', '9', 'px', 'url(', 'var(', 'env('];
  for (let k = 0; k < 6000; k++) {
    let s = '';
    const len = 1 + Math.floor(r() * 8);
    for (let j = 0; j < len; j++) s += pieces[Math.floor(r() * pieces.length)];
    out.add(s);
  }
  return [...out];
}

describe('css fast path: the same answer as css-tree', () => {
  const corpus = corpusDeclarations();

  it('takes the shapes moving widgets send, as css-tree writes them', () => {
    const cases: [string, string, string][] = [
      ['transform', 'translate(10.5px,-3px) rotate(45deg)', 'translate(10.5px,-3px) rotate(45deg)'],
      ['transform', 'translate(10.5px, -3px)  rotate(45deg)', 'translate(10.5px,-3px) rotate(45deg)'],
      ['background-color', 'hsl(120,50%,50%)', 'hsl(120,50%,50%)'],
      ['background-image', 'linear-gradient(90deg,hsl(1,2%,3%),hsl(4,5%,6%))', 'linear-gradient(90deg,hsl(1,2%,3%),hsl(4,5%,6%))'],
      ['color', 'rgba(0, 0, 0, 0.5)', 'rgba(0,0,0,0.5)'],
      ['box-shadow', 'rgba(0,0,0,.5) 0 1px #fff', 'rgba(0,0,0,.5)0 1px #fff'],
      ['box-shadow', '0 0 0 #fff', '0 0 0#fff'],
      ['background-position', '50% center', '50%center'],
      ['width', ' 50% ', '50%'],
      ['opacity', '.5', '.5'],
      ['--x', '10px', '10px'],
      ['display', 'flex', 'flex'],
      ['position', 'absolute', 'absolute'],
      ['overflow', 'hidden', 'hidden'],
      ['translate', '1px 2px', '1px 2px'],
      ['rotate', '45deg', '45deg'],
    ];
    for (const [name, value, out] of cases) {
      assert.equal(fastStyleValue(name, value), out, `${name}: ${value}`);
      assert.equal(sanitizeStylePropertyFull(name, value, () => {})?.value, out, `${name}: ${value} (full path)`);
    }
  });

  it('leaves what css-tree must decide to css-tree', () => {
    const cases: [string, string][] = [
      ['position', 'sticky'], ['overflow', 'auto'], ['overflow-x', 'scroll'], ['background-clip', 'text'], ['display', 'flex block'],
      ['display', 'table-foo'], ['width', '10vw'], ['width', '1e3px'], ['color', 'var(--x)'], ['color', 'light-dark(red,blue)'],
      ['background', 'url(#a)'], ['padding', 'env(safe-area-inset-top)'], ['content', 'attr(data-x)'], ['content', '"x"'],
      ['transform', 'rotateX(1deg)'], ['transform', 'translate3d(1px,2px,3px)'], ['rotate', 'x 45deg'], ['rotate', '1 2 3 45deg'],
      ['translate', '1px 2px 3px'], ['font-family', 'system-ui'], ['font', '12px system-ui'], ['filter', 'blur(2px)'],
      ['color-scheme', 'light dark'], ['color', 'red !important'], ['color', 'red/**/'], ['width', 'calc(100% - 10px)'],
      ['color', 'r\\ed'], ['color', 'réd'], ['color', 'red\tblue'], ['Color', 'red'], ['grid-template-columns', '1px '.repeat(80)],
      ['color', 'foo(1)'], ['width', '10furlong'], ['width', '-'], ['color', '#'], ['color', 'rgb(1,2,3'], ['color', 'rgb(1,2,)'],
    ];
    for (const [name, value] of cases) assert.equal(fastStyleValue(name, value), undefined, `${name}: ${value}`);
  });

  it(`every declaration of the corpora, the conformance set and the stress kit, in every env`, () => {
    const tally: Tally = { checked: 0, accepted: 0 };
    for (const [name, value] of corpus.decls) check(name, value, ENVS, tally);
    console.log(`  corpus: ${corpus.files} files, ${tally.checked} declarations, ${tally.accepted} taken by the fast path, ${ENVS.length} envs`);
    assert.ok(corpus.files > 600 && tally.checked > 5000 && tally.accepted > 2000, JSON.stringify({ files: corpus.files, ...tally }));
  });

  it('every value of the corpora under every property the fast path takes', () => {
    const values = [...new Set(corpus.decls.map(([, v]) => v))];
    const tally: Tally = { checked: 0, accepted: 0 };
    for (const name of [...PROPS, '--x']) for (const value of values) check(name, value, nextCorner(), tally);
    console.log(`  corpus × properties: ${values.length} values × ${PROPS.length + 1} properties, ${tally.accepted} taken`);
  });

  it('the generated set: token pairs and separators, escapes, URLs, substitutions, numbers, structure, fuzz', () => {
    const values = generated();
    const tally: Tally = { checked: 0, accepted: 0 };
    for (const name of RULE_PROPS) for (const value of values) check(name, value, nextCorner(), tally);
    // The edge set in every env.
    for (const name of RULE_PROPS) for (const value of EDGES) check(name, value, ENVS, tally);
    console.log(`  generated: ${values.length} values × ${RULE_PROPS.length} properties, ${tally.accepted} taken`);
  });

  it('the run-time shapes with random numbers, in every env', () => {
    const r = rng(42);
    const tally: Tally = { checked: 0, accepted: 0 };
    const props = ['transform', 'background-color', 'background-image', 'background', 'width', 'left', 'color', 'opacity', 'transition',
      'box-shadow', 'border', 'margin-top', 'stroke-dashoffset', 'font-size', 'border-radius', 'transition-duration', '--mv', 'z-index', 'translate'];
    for (const shape of SHAPES) for (let k = 0; k < 40; k++) {
      const value = shapeValue(shape, r);
      for (const name of props) check(name, value, k < 4 ? ENVS : nextCorner(), tally);
    }
    console.log(`  run-time shapes: ${tally.checked} declarations, ${tally.accepted} taken`);
    assert.ok(tally.accepted > tally.checked / 3);
  });

  it('the validator gives the same ops and diagnostics with the fast path on and off', () => {
    const values = corpus.decls.slice(0, 4000);
    const run = (fast: boolean, env: { scope?: 'shadow'; policy: 'strict' | 'compat' }) => {
      setCssFastPath(fast);
      try {
        const diags: string[] = [];
        const v = new Validator((k, d) => diags.push(`${k}: ${d}`), { colorScheme: 'light', scope: env.scope });
        v.setPolicy(env.policy);
        const out: unknown[] = [];
        out.push(...v.processOps([{ op: 'el', id: 10, tag: 'div' }] as any));
        for (let k = 0; k < values.length; k += 100) {
          const batch = values.slice(k, k + 100).map(([name, value]) => ({ op: 'style', id: 10, name, value }));
          out.push(...v.processOps(batch as any));
        }
        return { out: JSON.stringify(out), diags };
      } finally {
        setCssFastPath(true);
      }
    };
    for (const env of [{ policy: 'compat' as const }, { scope: 'shadow' as const, policy: 'strict' as const }]) {
      const off = run(false, env), on = run(true, env);
      assert.equal(on.out, off.out);
      assert.deepEqual(on.diags, off.diags);
    }
  });

  it('sanitizeStyleProperty takes the fast path only when it is on', () => {
    assert.deepEqual(sanitizeStyleProperty('opacity', '.5', () => {}), { name: 'opacity', value: '.5' });
    setCssFastPath(false);
    try {
      assert.deepEqual(sanitizeStyleProperty('opacity', '.5', () => {}), { name: 'opacity', value: '.5' });
    } finally {
      setCssFastPath(true);
    }
  });
});
