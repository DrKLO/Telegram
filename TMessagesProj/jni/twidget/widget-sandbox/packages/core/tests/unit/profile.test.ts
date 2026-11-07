// The widget profile (packages/core/profile.json): the generated files are current, the
// features agree with the tables the validator enforces, and CSS.supports / @supports give
// the answer the sanitizer acts on.

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { sanitizeStylesheet, sanitizeStyleProperty } from '../../src/host/css-sanitizer.ts';
import { DROPPED_TAGS, HTML_ATTRS, HTML_TAGS, REPLACED_TAGS, SECURITY_DROPPED_TAGS, SVG_ALLOWED_ATTRS, SVG_TAGS } from '../../src/host/allowlist.ts';
import { checkUrl } from '../../src/host/url-policy.ts';
import { Validator } from '../../src/host/validator.ts';
import { cssSupports, supportsDeclaration, supportsSelector } from '../../src/shared/css-supports.ts';
import { PROFILE_TABLES } from '../../src/shared/profile-tables.ts';
import { EVENT_TYPES } from '../../src/shared/protocol.ts';
import { briefTitle, readProfile } from '../../scripts/profile-build.ts';

const pkgRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const profile = readProfile();
const T = PROFILE_TABLES;

/** What the sanitizer does with one declaration: kept as written (no diagnostic), changed or dropped. */
function sanitized(prop: string, value: string): boolean {
  const diags: string[] = [];
  const out = sanitizeStyleProperty(prop, value, (k) => diags.push(k));
  return out !== null && out.value !== '' && diags.length === 0;
}

describe('widget profile: the generated files', () => {
  it('profile-tables.ts, docs/profile-v1.md and the guide\'s out list are what profile-build.ts makes from profile.json', () => {
    execFileSync(process.execPath, ['--import', 'tsx', path.join(pkgRoot, 'scripts', 'profile-build.ts'), '--check'], { cwd: pkgRoot, stdio: 'pipe' });
  });
  it('the authors\' guide (docs/widget-runtime-profile.md) names every out feature and has its agent block', () => {
    const guide = fs.readFileSync(path.join(pkgRoot, '..', '..', 'docs', 'widget-runtime-profile.md'), 'utf8');
    const list = guide.slice(guide.indexOf('<!-- profile:summary -->'), guide.indexOf('<!-- /profile:summary -->'));
    for (const f of profile.features) if (f.status === 'out') assert.ok(list.includes(briefTitle(f.title).replace(/</g, '&lt;')), f.id);
    // The agent block stands alone (a bot's model sees nothing else): every out feature by its full title, within ~4K tokens.
    const block = guide.slice(guide.indexOf('<!-- agent-block -->'), guide.indexOf('<!-- /agent-block -->'));
    for (const f of profile.features) if (f.status === 'out') assert.ok(block.includes(f.title), f.id);
    // 2450 since the client's time over the corner (2026-10-29, docs/sdk-v1.md §2.17) took the block past 2400; 2600 since the wallet (§2.18);
    // 2700 since the paint limit (v0.6.0, decisions.md "Heavy boxes").
    assert.ok(block.split(/\s+/).length <= 2700, `agent block: ${block.split(/\s+/).length} words`);
  });
  it('docs/profile-v1.md lists every feature', () => {
    const doc = fs.readFileSync(path.join(pkgRoot, '..', '..', 'docs', 'profile-v1.md'), 'utf8');
    for (const f of profile.features) assert.ok(doc.includes('| `' + f.id + '` |'), f.id);
  });
  it('every feature has a known decision, and every out feature says how it is neutralised', () => {
    for (const f of profile.features) {
      assert.ok(profile.decisions[f.decision], `${f.id}: decision ${f.decision}`);
      assert.ok(f.status === 'in' || f.status === 'out', f.id);
      if (f.status === 'out') assert.ok(f.neutralise, `${f.id}: neutralise`);
    }
  });
  // The catalogue syncs its statuses from profile.json, so every catalogue id is here with the same status (a newer
  // profile.json may run ahead of a catalogue that has not synced yet).
  it('covers the conformance catalogue: every id, the same status where the catalogue has decided', () => {
    const cat = JSON.parse(fs.readFileSync(path.join(pkgRoot, '..', 'conformance', 'catalogue.json'), 'utf8'));
    const byId = new Map(profile.features.map((f) => [f.id, f]));
    for (const c of cat.features as { id: string; status: string }[]) {
      const f = byId.get(c.id);
      assert.ok(f, `catalogue id ${c.id} is not in profile.json`);
      if (c.status !== 'candidate') assert.equal(f.status, c.status, c.id);
    }
  });
});

describe('widget profile: features agree with the tables', () => {
  const html = T.html.elements as Record<string, string>;
  const props = new Set<string>(T.css.properties);
  const propMatches = (pattern: string) => {
    if (pattern.startsWith('--')) return [pattern];
    const re = new RegExp('^' + pattern.replace(/[-]/g, '\\-').replace(/\*/g, '.*') + '$');
    return [...props].filter((p) => re.test(p));
  };
  for (const f of profile.features) {
    const m = f.match ?? {};
    const isIn = f.status === 'in';
    it(`${f.id} (${f.status})`, () => {
      // <img> is in; its SVG source is out through the URL policy (below).
      if (f.id !== 'html.el.img-svg') for (const tag of m.tags ?? []) {
        if (tag === '*unknown*') continue;
        if (isIn) assert.equal(html[tag], 'keep', `<${tag}>`);
        else assert.notEqual(html[tag] ?? 'unknown', 'keep', `<${tag}>`);
      }
      for (const tag of m.svgTags ?? []) {
        if (tag === '…') continue;
        assert.equal(SVG_TAGS.has(tag.toLowerCase()), isIn, `<svg:${tag}>`);
      }
      for (const attr of m.attrs ?? []) {
        if (['style', 'href', 'src', 'srcset', 'on*', 'data-*', 'aria-*'].includes(attr)) continue;
        if (f.id === 'canvas.hidpi' || f.id === 'svg.shapes' || f.id === 'svg.structure') { assert.ok(HTML_ATTRS.has(attr) || SVG_ALLOWED_ATTRS.has(attr.toLowerCase()), attr); continue; }
        assert.equal(HTML_ATTRS.has(attr), isIn, `[${attr}]`);
      }
      // A feature whose properties are shared with an in feature (display, font, filter…) is out through its values or functions.
      const sharedOnly = ['css.display.basic', 'css.display.other', 'css.position.fixed', 'css.position.sticky', 'css.overflow.scroll', 'css.font.named', 'css.font.ui-families',
        'css.filter.other', 'css.filter.url', 'css.background.clip-text', 'css.text.selection', 'css.display.table'];
      if (!sharedOnly.includes(f.id)) for (const p of m.properties ?? []) {
        if (isIn) assert.ok(propMatches(p).length > 0, `property ${p}`);
        else assert.deepEqual(propMatches(p), [], `property ${p}`);
      }
      for (const fn of m.functions ?? []) {
        if (fn === 'url' || (f.id === 'css.transform.3d' && fn === 'perspective')) continue;
        if (f.id.startsWith('css.filter') && !isIn) continue;
        assert.equal((T.css.functions as readonly string[]).includes(fn.toLowerCase()), isIn, `${fn}()`);
      }
      for (const u of m.units ?? []) assert.equal((T.css.units as readonly string[]).includes(u) || u === '%', isIn, `unit ${u}`);
      for (const s of m.selectors ?? []) {
        const pm = /^(::?)([\w-]+)$/.exec(s);
        if (!pm) continue;
        assert.equal(supportsSelector(`a${s}${['not', 'is', 'where', 'has', 'nth-child', 'nth-last-child', 'nth-of-type', 'nth-last-of-type', 'host', 'host-context', 'slotted', 'part'].includes(pm[2]) ? '(b)' : ''}`), isIn || ['disabled', 'enabled'].includes(pm[2]), s);
      }
      if (f.id === 'at.nesting') assert.equal(supportsSelector('& a'), false);
      for (const a of m.atrules ?? []) assert.equal((T.css.atRules as readonly string[]).includes(a), isIn, `@${a}`);
      for (const mf of m.media ?? []) assert.equal((T.css.mediaFeatures as readonly string[]).includes(mf) || (T.css.mediaTypes as readonly string[]).includes(mf), isIn, `media ${mf}`);
      for (const e of m.events ?? []) {
        // visibilitychange is the shim's own event, not one the renderer forwards.
        if (f.id === 'input.key-activation' || e === 'visibilitychange') continue;
        assert.equal(EVENT_TYPES.includes(e), isIn, `event ${e}`);
      }
      if (f.id === 'css.display.other' || f.id === 'css.display.grid' || f.id === 'css.display.flex' || f.id === 'css.display.table' || f.id === 'css.display.basic')
        for (const v of m.values ?? []) assert.equal(supportsDeclaration('display', v), isIn, `display: ${v}`);
      if (f.id === 'css.position.sticky' || f.id === 'css.position.fixed') for (const v of m.values ?? []) assert.equal(supportsDeclaration('position', v), isIn, `position: ${v}`);
      if (f.id === 'css.overflow.scroll' || f.id === 'css.overflow.clip') for (const v of m.values ?? []) assert.equal(supportsDeclaration('overflow', v), isIn, `overflow: ${v}`);
      if (f.id === 'css.font.ui-families' || f.id === 'css.font.generic') for (const v of m.values ?? []) assert.equal(supportsDeclaration('font-family', v), isIn, `font-family: ${v}`);
      if (f.id === 'html.el.img-svg' || f.id === 'html.el.img-other-formats') for (const v of m.values ?? []) assert.equal(checkUrl(v + ';base64,AA', 'image'), null, v);
    });
  }
  it('the security list is never in the profile, and every profile element has one action', () => {
    for (const tag of SECURITY_DROPPED_TAGS) {
      assert.ok(!HTML_TAGS.has(tag) && !REPLACED_TAGS.has(tag) && DROPPED_TAGS.has(tag), tag);
      assert.equal(html[tag], 'drop', tag);
    }
  });
});

describe('widget profile: CSS.supports answers what the sanitizer does', () => {
  const declarations: [string, string][] = [
    ['display', 'grid'], ['display', 'inline-grid'], ['display', 'flex'], ['display', 'flow-root'], ['display', 'contents'], ['display', 'list-item'],
    ['display', '-webkit-box'], ['display', 'inline flex'], ['display', 'var(--d)'], ['position', 'sticky'], ['position', 'fixed'], ['position', '-webkit-sticky'],
    ['overflow', 'hidden'], ['overflow', 'auto'], ['overflow', 'hidden scroll'], ['overflow-y', 'scroll'], ['overflow-x', 'clip'], ['overflow-wrap', 'anywhere'],
    ['color', 'red'], ['color', 'rgb(1 2 3 / 50%)'], ['color', 'oklch(0.5 0.1 120)'], ['color', 'color-mix(in srgb, red, blue)'], ['color', 'light-dark(#fff, #000)'],
    ['height', '100vh'], ['height', '100dvh'], ['width', '100vw'], ['width', 'calc(100% - 2rem)'], ['width', '50cqw'], ['width', 'round(10px, 3px)'],
    ['margin-inline', 'auto'], ['inset-block', '0'], ['columns', '2'], ['text-shadow', '0 1px red'], ['-webkit-line-clamp', '2'], ['text-overflow', 'ellipsis'],
    ['font-variant-numeric', 'tabular-nums'], ['font-family', 'system-ui'], ['font-family', 'ui-monospace'], ['font-family', 'Inter, sans-serif'],
    ['font-family', '"serif"'], ['font-family', 'cursive'], ['font-family', 'var(--f)'], ['font', 'bold 12px/1.4 system-ui, sans-serif'], ['font', '12px Inter'],
    ['font', '700 40px/1 ui-monospace, monospace'], ['filter', 'blur(2px)'], ['filter', 'blur(2px) grayscale(1)'], ['filter', 'url(#f)'], ['filter', 'drop-shadow(0 0 2px red)'],
    ['backdrop-filter', 'blur(8px)'], ['backdrop-filter', 'saturate(2)'], ['transform', 'rotate(10deg) scale(1.1)'], ['transform', 'rotateY(10deg)'],
    ['transform', 'translate3d(0, 0, 0)'], ['rotate', '45deg'], ['rotate', 'x 45deg'], ['translate', '1px 2px'], ['translate', '1px 2px 3px'], ['scale', '1 1 2'],
    ['perspective', '100px'], ['background-clip', 'padding-box'], ['background-clip', 'text'], ['background', 'conic-gradient(red, blue)'],
    ['background', 'url(data:image/png;base64,AA)'], ['background', 'url(data:image/svg+xml,x)'], ['background', 'url(https://example.com/x.png)'],
    ['background-image', 'image-set("a.png" 1x)'], ['user-select', 'none'], ['writing-mode', 'vertical-rl'], ['touch-action', 'pan-y'], ['pointer-events', 'none'],
    ['clip-path', 'circle(50%)'], ['mix-blend-mode', 'multiply'], ['mask', 'none'], ['grid-template-columns', 'repeat(3, minmax(0, 1fr))'], ['aspect-ratio', '16 / 9'],
    ['transition', 'opacity .2s ease-out'], ['animation', 'spin 1s steps(4) infinite'], ['content', 'attr(data-x)'], ['content', 'counter(c)'], ['--x', 'oklch(1 0 0)'],
    ['--y', '12px'], ['--z', '1vh'], ['width', 'env(safe-area-inset-left)'], ['margin', 'inherit'], ['fill', 'url(#g)'], ['stroke-dasharray', '4 2'],
  ];
  for (const [p, v] of declarations) {
    it(`${p}: ${v}`, () => assert.equal(supportsDeclaration(p, v), sanitized(p, v), `CSS.supports('${p}', '${v}')`));
  }
  const selectors = ['a:hover', 'a:active', 'li:nth-child(2n)', ':is(a, b):not(.c)', ':root', 'a::before', 'a:before', 'a:focus', 'a:focus-visible', 'a:has(b)', 'p::first-letter',
    'li::marker', 'a:target', 'input:checked', 'button:disabled', 'button:enabled', ':host', ':scope', '::selection', 'a:visited', 'details[open] > summary', '& a'];
  for (const s of selectors) {
    it(`selector ${s}`, () => {
      const diags: string[] = [];
      const out = sanitizeStylesheet(`${s}{color:red}`, (k) => diags.push(k));
      const kept = out.includes('color:red') && !diags.some((k) => k === 'profile' || k === 'css-dropped');
      // :disabled and :enabled are rewritten to the attribute form, which keeps their meaning on a <button>.
      assert.equal(supportsSelector(s), kept || /:(?:dis|en)abled$/.test(s), s);
    });
  }
  const conditions = ['(display: grid)', '(display: flow-root)', 'not (position: sticky)', '(display: grid) and (gap: 1px)', '(color: oklch(1 0 0)) or (color: red)',
    'selector(:has(a))', 'selector(a:hover)', 'font-tech(color-COLRv1)', '((display: flex))', '(text-overflow: ellipsis) and (not (text-shadow: 0 0 red))'];
  for (const c of conditions) {
    it(`@supports ${c} and CSS.supports agree`, () => {
      const out = sanitizeStylesheet(`@supports ${c}{a{color:red}}`, () => {});
      assert.equal(out === 'a{color:red}', cssSupports(c), out);
      assert.ok(!out.includes('@supports'), 'the renderer never sees @supports');
    });
  }
  it('two-argument and one-argument forms', () => {
    assert.equal(cssSupports('display', 'grid'), true);
    assert.equal(cssSupports('display: grid'), true);
    assert.equal(cssSupports('(display: grid)'), true);
    assert.equal(cssSupports('color', 'red !important'), false);
    assert.equal(cssSupports(), false);
  });
});

describe('widget profile: elements, attributes, events and methods in the validator', () => {
  const run = (ops: unknown[]) => {
    const diags: string[] = [];
    const v = new Validator((k, d) => diags.push(`${k}: ${d}`));
    v.processOps([{ op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null }, { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null }]);
    return { ops: v.processOps(ops), diags, v };
  };
  it('form controls are dropped with their subtree; form, dialog and the legacy elements keep their content', () => {
    const { ops, diags } = run([
      { op: 'el', id: 10, tag: 'input', attrs: { value: 'x' } }, { op: 'el', id: 11, tag: 'select' }, { op: 'el', id: 12, tag: 'option' }, { op: 'ins', parent: 11, id: 12, before: null },
      { op: 'el', id: 13, tag: 'textarea' }, { op: 'el', id: 14, tag: 'progress' }, { op: 'el', id: 15, tag: 'fieldset' },
      { op: 'el', id: 20, tag: 'form' }, { op: 'el', id: 21, tag: 'dialog', attrs: { open: '' } }, { op: 'el', id: 22, tag: 'center' }, { op: 'el', id: 23, tag: 'ruby' },
      { op: 'el', id: 24, tag: 'label', attrs: { for: 'x' } }, { op: 'el', id: 25, tag: 'button', attrs: { type: 'button', disabled: '' } },
    ]);
    const els = ops.filter((o) => o.op === 'el') as any[];
    assert.deepEqual(els.map((o) => [o.id, o.tag]), [[20, 'div'], [21, 'div'], [22, 'div'], [23, 'span'], [24, 'span'], [25, 'button']]);
    assert.deepEqual(Object.fromEntries(els.find((o) => o.id === 20).attrs), { 'data-tg-tag': 'form' });
    assert.deepEqual(Object.fromEntries(els.find((o) => o.id === 25).attrs), { type: 'button', disabled: '' });
    assert.ok(diags.includes('profile: <input> dropped') && diags.includes('profile: <form> rendered as <div>'), diags.join('\n'));
  });
  it('keyboard, focus, editing, drag, popover and legacy presentational attributes are dropped', () => {
    const { ops } = run([{ op: 'el', id: 10, tag: 'div', attrs: { tabindex: '0', accesskey: 'k', contenteditable: 'true', draggable: 'true', popover: '', align: 'center', bgcolor: 'red', class: 'c', title: 't' } }]);
    assert.deepEqual(Object.fromEntries((ops[0] as any).attrs), { class: 'c', title: 't' });
  });
  it('SVG attributes have their own list; font-family keeps the generic families', () => {
    const { ops } = run([
      { op: 'el', id: 10, tag: 'svg', ns: 'svg', attrs: { viewbox: '0 0 1 1', 'clip-path': 'url(#c)', filter: 'url(#f)', mask: 'url(#m)', class: 'chart' } },
      { op: 'el', id: 11, tag: 'text', ns: 'svg', attrs: { 'font-family': 'Inter, monospace', 'text-anchor': 'middle', tabindex: '0' } },
      { op: 'el', id: 12, tag: 'linearGradient', ns: 'svg', attrs: { id: 'g', x2: '1' } },
      { op: 'el', id: 13, tag: 'rect', ns: 'svg', attrs: { fill: 'url(#g)', width: '1' } },
    ]);
    assert.deepEqual(Object.fromEntries((ops[0] as any).attrs), { viewBox: '0 0 1 1', class: 'chart' });
    assert.deepEqual(Object.fromEntries((ops[1] as any).attrs), { 'font-family': 'monospace', 'text-anchor': 'middle' });
    // A gradient keeps its id, so `fill: url(#g)` still finds it.
    assert.deepEqual(Object.fromEntries((ops[2] as any).attrs), { id: 'g', x2: '1' });
    assert.deepEqual(Object.fromEntries((ops[3] as any).attrs), { fill: 'url(#g)', width: '1' });
  });
  it('focus, scrollIntoView, dialog and popover calls never reach the renderer; scrolling a clipped box does', () => {
    const { ops } = run([{ op: 'el', id: 10, tag: 'div' }, ...['focus', 'blur', 'scrollIntoView', 'showModal', 'showPopover', 'select', 'completeEditorCommand'].map((method) => ({ op: 'call', id: 10, method })), { op: 'call', id: 10, method: 'scrollTo', args: [0, 5] }]);
    assert.deepEqual(ops.filter((o) => o.op === 'call').map((o: any) => o.method), ['scrollTo']);
  });
  it('form properties are not settable', () => {
    const { ops } = run([{ op: 'el', id: 10, tag: 'div' }, { op: 'prop', id: 10, name: 'value', value: 'x' }, { op: 'prop', id: 10, name: 'checked', value: true }, { op: 'prop', id: 10, name: 'scrollTop', value: 3 }]);
    assert.deepEqual(ops.filter((o) => o.op === 'prop').map((o: any) => o.name), ['scrollTop']);
  });
  it('the event list has no keyboard, focus, touch, wheel or scroll; dblclick and contextmenu since v0.1.2 (the renderer sends its own, on touch-action: none)', () => {
    for (const e of ['keydown', 'keyup', 'keypress', 'input', 'change', 'submit', 'focus', 'blur', 'focusin', 'touchstart', 'wheel', 'scroll']) assert.ok(!EVENT_TYPES.includes(e), e);
    for (const e of ['dblclick', 'contextmenu']) assert.ok(EVENT_TYPES.includes(e), e);
    for (const e of ['click', 'pointerdown', 'pointermove', 'pointerenter', 'mouseenter', 'toggle', 'transitionend']) assert.ok(EVENT_TYPES.includes(e), e);
  });
  it('font families: the system font\'s vendor names become system-ui, named families go, ui-* get their fallback', () => {
    const font = (v: string) => sanitizeStyleProperty('font-family', v, () => {})?.value;
    assert.equal(font('-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'), 'system-ui,sans-serif');
    assert.equal(font('-apple-system, system-ui, sans-serif'), 'system-ui,sans-serif');
    assert.equal(font('"Inter"'), 'system-ui');
    assert.equal(font('ui-monospace, SFMono-Regular, Menlo'), 'ui-monospace,monospace');
    assert.equal(sanitizeStyleProperty('font', '600 14px/1.2 -apple-system, sans-serif', () => {})?.value, '600 14px/1.2 system-ui,sans-serif');
  });
  it('matchMedia answers false outside the profile; prefers-reduced-motion follows the client', () => {
    const v = new Validator(() => {});
    assert.equal(v.rewriteMediaQuery('(hover: hover)'), '(min-width: 99999px)');
    assert.equal(v.rewriteMediaQuery('print'), '(min-width: 99999px)');
    assert.equal(v.rewriteMediaQuery('(max-height: 400px)'), '(min-width: 99999px)');
    assert.equal(v.rewriteMediaQuery('(max-width: 400px)'), '(max-width: 400px)');
    assert.equal(v.rewriteMediaQuery('(prefers-reduced-motion: reduce)'), '(min-width: 99999px)');
    assert.equal(v.rewriteMediaQuery('(prefers-reduced-motion: no-preference)'), '(min-width: 0px)');
    assert.equal(sanitizeStylesheet('@media (prefers-reduced-motion){a{color:red}}', () => {}, { colorScheme: 'light', reducedMotion: true }), '@media (min-width: 0px){a{color:red}}');
  });
});
