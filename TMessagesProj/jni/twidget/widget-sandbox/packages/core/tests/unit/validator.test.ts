// Unit tests for the host-side validator / sanitizer (security-critical).
// Run with: pnpm test:unit

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { sanitizeStyleAttribute, sanitizeStyleProperty, sanitizeStylesheet } from '../../src/host/css-sanitizer.ts';
import { BROKEN_IMAGE, checkUrl } from '../../src/host/url-policy.ts';
import { Validator } from '../../src/host/validator.ts';
import type { RenderOp } from '../../src/shared/protocol.ts';

const CANARY = 'http://127.0.0.1:9999/hit/unit';
const noDiag = () => {};

function run(ops: unknown[], diags: string[] = [], policy: 'compat' | 'strict' = 'compat'): RenderOp[] {
  const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
  v.setPolicy(policy);
  // Minimal document skeleton so inserts under <body> are rendered.
  const base = [
    { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
    { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null },
  ];
  v.processOps(base);
  v.markLoaded();
  return v.processOps(ops);
}
const attrsOf = (ops: RenderOp[], id: number) => Object.fromEntries((ops.find((o) => o.op === 'el' && o.id === id) as any)?.attrs ?? []);
const json = (ops: RenderOp[]) => JSON.stringify(ops);
/** A 2 × 2 PNG, and the header of a 1 × 1 lossless WebP: what checkUrl reads of an image is its size. */
const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR4AWM8Y2z8nwEImBigAAAh4QI1lhEEowAAAABJRU5ErkJggg==';
const WEBP = 'data:image/webp;base64,' + Buffer.from('RIFF\x1a\x00\x00\x00WEBPVP8L\x0d\x00\x00\x00\x2f' + '\x00'.repeat(9), 'latin1').toString('base64');

describe('url policy', () => {
  it('allows only data: images / fonts, host blobs and fragments', () => {
    assert.equal(checkUrl(PNG, 'image'), PNG);
    // bytes of no image of the profile's behind an image type: broken (an error, no size), never decoded as anything
    assert.equal(checkUrl('data:image/png;base64,AAAA', 'image'), BROKEN_IMAGE);
    // No font or media kind exists any more: only images may be data: URLs.
    assert.equal(checkUrl('data:font/woff2;base64,AAAA', 'image'), null);
    assert.equal(checkUrl('data:video/mp4;base64,AAAA', 'image'), null);
    assert.equal(checkUrl('data:text/html,<img src=x>', 'image'), null);
    // The profile's image types only: SVG, AVIF, BMP and ICO are out (the decoder runs in the app process).
    assert.equal(checkUrl('data:image/svg+xml,<svg/>', 'image'), null);
    assert.equal(checkUrl('data:image/avif;base64,AAAA', 'image'), null);
    assert.equal(checkUrl(WEBP, 'image'), WEBP);
    assert.equal(checkUrl('#frag', 'image'), '#frag');
    assert.equal(checkUrl('#frag', 'fragment-only'), '#frag');
    assert.equal(checkUrl(CANARY, 'image'), null);
    assert.equal(checkUrl('//127.0.0.1:9999/x', 'image'), null);
    assert.equal(checkUrl('HTTP://127.0.0.1:9999/x', 'image'), null);
    assert.equal(checkUrl('\t\nhttp://127.0.0.1:9999/x', 'image'), null);
    assert.equal(checkUrl('javascript:alert(1)', 'image'), null);
    assert.equal(checkUrl('blob:http://127.0.0.1:5173/abc', 'image'), null);
    assert.equal(checkUrl('relative.png', 'image'), null);
    assert.equal(checkUrl('', 'image'), null);
  });
});

describe('css sanitizer', () => {
  it('drops url() that is not data:, keeps data: and fragments', () => {
    const d: string[] = [];
    const css = sanitizeStylesheet(`p{background:url(${CANARY});color:red} b{background:url("data:image/png;base64,AA")} i{fill:url(#f)}`, (k, x) => d.push(k + x));
    assert.ok(!css.includes('127.0.0.1'));
    assert.ok(css.includes('color:red'));
    assert.ok(css.includes('data:image/png'));
    assert.ok(css.includes('url(#f)'));
    assert.ok(d.some((x) => x.startsWith('url-blocked')));
  });
  it('drops @import, every @font-face (system fonts only), @namespace and unknown at-rules', () => {
    const css = sanitizeStylesheet(`@import url(${CANARY}); @import "${CANARY}"; @namespace svg url(${CANARY}); @font-face{font-family:L;src:url(${CANARY}) format("woff2")} @font-face{font-family:M;src:url(data:font/woff2;base64,AA)} @document url(${CANARY}){p{color:red}} p{color:blue;font-family:M,system-ui}`, noDiag);
    assert.ok(!css.includes('127.0.0.1'), css);
    assert.ok(!css.includes('@import'));
    assert.ok(!css.includes('@font-face'), css);
    assert.ok(!css.includes('data:font'), css);
    // Without @font-face a family can only name an installed font, and the profile keeps the generic ones only (D3b).
    assert.ok(css.includes('font-family:system-ui'), css);
    assert.ok(css.includes('color:blue'));
  });
  it('handles image-set, strings in url functions, var() inside url functions, escapes and nesting', () => {
    const css = sanitizeStylesheet(
      `p{background-image:image-set("${CANARY}" 1x)} q{background:-webkit-image-set(url(${CANARY}) 1x)} r{background:image-set(var(--x) 1x)}` +
        ` s{background:u\\72 l(${CANARY})} t{background:url(\\68 ttp://127.0.0.1:9999/x)} u{background:URL( ${CANARY} )}` +
        ` @media screen{v{background:url(${CANARY})} @supports (display:grid){w{background:url(${CANARY})}}} @keyframes k{from{background:url(${CANARY})}}` +
        ` :root{--leak:url(${CANARY})} x{background:var(--leak)} y{color:green}`,
      noDiag,
    );
    assert.ok(!css.includes('127.0.0.1'), css);
    assert.ok(!/image-set\(var/.test(css));
    assert.ok(css.includes('color:green'));
  });
  it('style attributes and single properties', () => {
    assert.ok(!sanitizeStyleAttribute(`color:red;background:url(${CANARY});cursor:url(${CANARY}),auto`, noDiag).includes('127'));
    assert.ok(sanitizeStyleAttribute(`color:red;background:url(${CANARY})`, noDiag).includes('color:red'));
    assert.equal(sanitizeStyleProperty('background-image', `url(${CANARY})`, noDiag), null);
    assert.deepEqual(sanitizeStyleProperty('color', 'red', noDiag), { name: 'color', value: 'red' });
    assert.equal(sanitizeStyleProperty('behavior', 'url(x.htc)', noDiag), null);
    assert.equal(sanitizeStyleProperty('bad name', 'red', noDiag), null);
    assert.equal(sanitizeStyleProperty('--leak', `url(${CANARY})`, noDiag), null);
  });
});

describe('css sanitizer: host-owned colour scheme', () => {
  it('evaluates prefers-color-scheme, color-scheme and light-dark() against the host theme', () => {
    const css = '@media (prefers-color-scheme: dark){p{color:red}} @media screen and (prefers-color-scheme: light){p{color:blue}} :root{color-scheme:light dark} b{background:light-dark(#fff,#000)}';
    const dark = sanitizeStylesheet(css, noDiag, { colorScheme: 'dark' });
    assert.ok(dark.includes('@media (min-width: 0px){p{color:red}}'), dark);
    assert.ok(dark.includes('screen and (min-width: 99999px)'), dark);
    assert.ok(dark.includes('color-scheme:dark'), dark);
    assert.ok(dark.includes('background:#000'), dark);
    const light = sanitizeStylesheet(css, noDiag, { colorScheme: 'light' });
    assert.ok(light.includes('@media (min-width: 99999px){p{color:red}}'), light);
    assert.ok(light.includes('color-scheme:light') && light.includes('background:#fff'), light);
  });
  it('checks the light-dark() branch it picks like the rest of the value, nested ones too', () => {
    for (const colorScheme of ['light', 'dark'] as const) {
      const d: string[] = [];
      const css = sanitizeStylesheet('p{background:light-dark(url(/x.png),url(https://example.com/y.png))} b{background:light-dark(red,light-dark(blue,url(/z.png)))} i{color:light-dark(red,blue)}', (k) => d.push(k), { colorScheme });
      assert.ok(!/url\(/.test(css), css);
      assert.ok(d.includes('url-blocked'), d.join());
      assert.ok(css.includes(colorScheme === 'light' ? 'b{background:red}' : 'i{color:blue}'), css);
    }
    const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR4AWM8Y2z8nwEImBigAAAh4QI1lhEEowAAAABJRU5ErkJggg==';
    assert.equal(sanitizeStyleProperty('background', `light-dark(url(${png}),red)`, noDiag, { colorScheme: 'light' })?.value, `url(${png})`);
  });
  it('re-emits stylesheets when the validator theme changes', () => {
    const v = new Validator(noDiag, { colorScheme: 'light' });
    v.processOps([{ op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null }, { op: 'el', id: 3, tag: 'style' }, { op: 'ins', parent: 2, id: 3, before: null }, { op: 'text', id: 4, data: '@media (prefers-color-scheme: dark){p{color:red}}' }, { op: 'ins', parent: 3, id: 4, before: null }]);
    const ops = v.setTheme('dark');
    assert.deepEqual(ops, [{ op: 'css', id: 3, text: '@media (min-width: 0px){p{color:red}}' }]);
    assert.equal(v.rewriteMediaQuery('(prefers-color-scheme: dark)'), '(min-width: 0px)');
  });
});

describe('validator: elements and attributes', () => {
  it('drops script/iframe/object/embed/link/meta/base and their subtrees, keeps the model consistent', () => {
    const ops = run([
      { op: 'el', id: 10, tag: 'iframe', attrs: { src: CANARY } }, { op: 'ins', parent: 3, id: 10, before: null },
      { op: 'el', id: 11, tag: 'script', attrs: { src: CANARY } }, { op: 'ins', parent: 3, id: 11, before: null },
      { op: 'el', id: 12, tag: 'link', attrs: { rel: 'stylesheet', href: CANARY } }, { op: 'ins', parent: 3, id: 12, before: null },
      { op: 'el', id: 13, tag: 'object', attrs: { data: CANARY } }, { op: 'ins', parent: 3, id: 13, before: null },
      { op: 'el', id: 14, tag: 'embed', attrs: { src: CANARY } }, { op: 'ins', parent: 3, id: 14, before: null },
      { op: 'el', id: 15, tag: 'meta', attrs: { 'http-equiv': 'refresh', content: '0;url=' + CANARY } }, { op: 'ins', parent: 3, id: 15, before: null },
      { op: 'el', id: 16, tag: 'base', attrs: { href: CANARY } }, { op: 'ins', parent: 3, id: 16, before: null },
      { op: 'el', id: 17, tag: 'img', attrs: { src: 'data:image/gif;base64,AA' } }, { op: 'ins', parent: 10, id: 17, before: null },
      { op: 'el', id: 18, tag: 'p' }, { op: 'ins', parent: 3, id: 18, before: null },
    ]);
    assert.ok(!json(ops).includes('127.0.0.1'));
    assert.deepEqual(ops.filter((o) => o.op === 'el').map((o: any) => o.tag), ['img', 'p']);
    // img under the dropped iframe is created but never attached
    assert.ok(!ops.some((o) => o.op === 'ins' && o.id === 17));
    assert.ok(ops.some((o) => o.op === 'ins' && o.id === 18 && o.parent === 3));
  });
  it('strips on* handlers, srcset, ping, formaction, background; rewrites external hrefs and img src', () => {
    const ops = run([
      { op: 'el', id: 10, tag: 'a', attrs: { href: CANARY, ping: CANARY, onclick: 'x()', target: '_blank', ONMOUSEOVER: 'y()' } },
      { op: 'el', id: 11, tag: 'img', attrs: { src: CANARY, srcset: CANARY + ' 2x', onerror: 'z()' } },
      { op: 'el', id: 12, tag: 'button', attrs: { formaction: CANARY } },
      { op: 'el', id: 13, tag: 'body', attrs: { background: CANARY } },
      { op: 'el', id: 14, tag: 'a', attrs: { href: '#top' } },
      { op: 'el', id: 15, tag: 'a', attrs: { href: 'javascript:alert(1)' } },
      { op: 'el', id: 16, tag: 'img', attrs: { src: PNG } },
    ]);
    assert.ok(!json(ops).includes('127.0.0.1'));
    assert.ok(!json(ops).toLowerCase().includes('onclick'));
    assert.deepEqual(attrsOf(ops, 10), { href: '#', 'data-tg-link': '' });
    assert.equal(attrsOf(ops, 11).src.slice(0, 19), 'data:image/svg+xml,');
    assert.ok(!('srcset' in attrsOf(ops, 11)));
    assert.deepEqual(attrsOf(ops, 12), {});
    assert.deepEqual(attrsOf(ops, 14), { href: '#top' });
    assert.deepEqual(attrsOf(ops, 15), { href: '#', 'data-tg-link': '' });
    assert.deepEqual(attrsOf(ops, 16), { src: PNG });
  });
  it('attribute updates: rejected values clear the attribute', () => {
    const ops = run([
      { op: 'el', id: 10, tag: 'img', attrs: { src: 'data:image/png;base64,AA' } },
      { op: 'attr', id: 10, name: 'src', value: CANARY },
      { op: 'attr', id: 10, name: 'onload', value: 'x()' },
      { op: 'attr', id: 10, name: 'style', value: `background:url(${CANARY})` },
    ]);
    const after = ops.slice(1);
    assert.ok(!json(after).includes('127.0.0.1'));
    // external img src → placeholder image (not removed), so the layout keeps its slot
    assert.ok(after.some((o) => o.op === 'attr' && o.name === 'src' && o.value.startsWith('data:image/svg+xml')));
    assert.ok(!after.some((o) => o.op === 'attr' && o.name === 'onload'));
    // Media elements are dropped outright, even with data: sources; nothing about them reaches the renderer.
    const ops2 = run([
      { op: 'el', id: 10, tag: 'video', attrs: { poster: 'data:image/png;base64,AA', src: 'data:video/mp4;base64,AA', autoplay: '' } },
      { op: 'el', id: 11, tag: 'source', attrs: { src: 'data:video/mp4;base64,AA' } },
      { op: 'ins', parent: 10, id: 11, before: null },
      { op: 'el', id: 12, tag: 'audio', attrs: { src: CANARY } },
      { op: 'attr', id: 10, name: 'poster', value: CANARY },
    ]);
    assert.ok(!json(ops2).includes('127.0.0.1'));
    assert.ok(!ops2.some((o) => o.op === 'el' && ['video', 'source', 'audio'].includes((o as any).tag)), json(ops2));
    assert.ok(!json(ops2).includes('poster'));
  });
  it('SVG: href only as fragment, fill url() checked; image, feImage and SMIL are outside the profile', () => {
    const ops = run([
      { op: 'el', id: 10, tag: 'image', ns: 'svg', attrs: { href: CANARY } },
      { op: 'el', id: 11, tag: 'image', ns: 'svg', attrs: { 'xlink:href': 'data:image/png;base64,AA' } },
      { op: 'el', id: 12, tag: 'use', ns: 'svg', attrs: { href: CANARY + '#i' } },
      { op: 'el', id: 13, tag: 'use', ns: 'svg', attrs: { 'xlink:href': '#i' } },
      { op: 'el', id: 14, tag: 'animate', ns: 'svg', attrs: { attributeName: 'href', to: CANARY } },
      { op: 'el', id: 15, tag: 'animate', ns: 'svg', attrs: { attributeName: 'fill', values: `url(${CANARY}#g);red` } },
      { op: 'el', id: 16, tag: 'set', ns: 'svg', attrs: { attributeName: 'href', to: CANARY } },
      { op: 'el', id: 17, tag: 'rect', ns: 'svg', attrs: { fill: `url(${CANARY}#g)`, stroke: 'url(#g)', viewbox: '0 0 1 1' } },
      { op: 'el', id: 18, tag: 'a', ns: 'svg', attrs: { 'xlink:href': CANARY } },
      { op: 'el', id: 19, tag: 'feImage', ns: 'svg', attrs: { href: CANARY } },
    ]);
    assert.ok(!json(ops).includes('127.0.0.1'), json(ops));
    for (const id of [10, 11, 14, 15, 16, 19]) assert.ok(!ops.some((o) => o.op === 'el' && o.id === id), `#${id} is not rendered`);
    assert.deepEqual(attrsOf(ops, 12), {});
    assert.deepEqual(attrsOf(ops, 13), { href: '#i' });
    assert.deepEqual(attrsOf(ops, 17), { stroke: 'url(#g)', viewBox: '0 0 1 1' });
    assert.deepEqual(attrsOf(ops, 18), { href: '#', 'data-tg-link': '' });
  });
  it('style elements: text is sanitized as a stylesheet and re-emitted on change', () => {
    const ops = run([
      { op: 'el', id: 10, tag: 'style' }, { op: 'ins', parent: 3, id: 10, before: null },
      { op: 'text', id: 11, data: `@import url(${CANARY}); p{color:red;background:url(${CANARY})}` }, { op: 'ins', parent: 10, id: 11, before: null },
    ]);
    const css = ops.find((o) => o.op === 'css') as any;
    assert.ok(css && !css.text.includes('127.0.0.1') && css.text.includes('color:red'));
    assert.ok(!ops.some((o) => o.op === 'ins' && o.id === 11));
    // the raw text node is forgotten in the renderer: only the sanitized css op survives
    assert.ok(ops.some((o) => o.op === 'rm' && o.id === 11));
    assert.ok(!JSON.stringify(ops.filter((o) => o.op !== 'text')).includes('127.0.0.1'));
  });
  it('unknown elements become inert spans; html/head/body are adopted once', () => {
    const ops = run([{ op: 'el', id: 10, tag: 'x-foo', attrs: { src: CANARY } }, { op: 'el', id: 11, tag: 'body' }, { op: 'el', id: 12, tag: 'portal', attrs: { src: CANARY } }]);
    assert.equal((ops[0] as any).tag, 'span');
    assert.deepEqual(attrsOf(ops, 10), { 'data-tg-tag': 'x-foo' });
    assert.equal((ops[1] as any).tag, 'div');
    assert.ok(!ops.some((o) => o.op === 'el' && o.id === 12));
  });
});

describe('validator: protocol strictness', () => {
  it('rejects malformed ops, cycles, unknown ids and bad props/calls', () => {
    const diags: string[] = [];
    const ops = run([
      'nonsense', { op: 'el' }, { op: 'el', id: 1, tag: 'div' }, { op: 'el', id: 10, tag: 'div<script>' }, { op: 'el', id: 10, tag: 'div' },
      { op: 'ins', parent: 10, id: 10, before: null }, { op: 'ins', parent: 999, id: 10, before: null },
      { op: 'prop', id: 10, name: 'innerHTML', value: '<img src=x>' }, { op: 'prop', id: 10, name: '__proto__', value: 1 },
      { op: 'call', id: 10, method: 'remove' }, { op: 'call', id: 10, method: 'constructor' },
      { op: 'style', id: 10, name: 'color', value: 'red' },
      { op: 'canvas', id: 10, cmds: [['c', 'fillRect', 0, 0, 1, 1]] },
    ], diags);
    assert.deepEqual(ops.map((o) => o.op), ['el', 'style']);
    assert.ok(diags.filter((d) => d.startsWith('protocol-violation')).length >= 8);
  });
  it('queries are validated against rendered elements and an allowlist', () => {
    const v = new Validator(noDiag);
    v.processOps([{ op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null }, { op: 'el', id: 10, tag: 'canvas' }, { op: 'el', id: 11, tag: 'script' }]);
    assert.deepEqual(v.validateQuery({ q: 'rect', id: 10 }), { q: 'rect', id: 10 });
    assert.equal(v.validateQuery({ q: 'rect', id: 11 }), null);
    assert.equal(v.validateQuery({ q: 'rect', id: 1 }), null);
    assert.equal(v.validateQuery({ q: 'svg', id: 10, method: 'innerHTML' }), null);
    assert.deepEqual(v.validateQuery({ q: 'canvasCall', id: 10, method: 'isPointInPath', args: [1, 2] }), { q: 'canvasCall', id: 10, method: 'isPointInPath', args: [1, 2] });
    // Read-back is outside the profile.
    assert.equal(v.validateQuery({ q: 'canvasCall', id: 10, method: 'toDataURL', args: ['image/png'] }), null);
    assert.equal(v.validateQuery({ q: 'imageData', id: 10, x: 0, y: 0, w: 1, h: 1 }), null);
    assert.deepEqual(v.validateQuery({ q: 'measureText', font: '12px Inter, monospace', text: 'x' }), { q: 'measureText', font: '12px monospace', text: 'x' });
    assert.equal(v.validateQuery({ q: 'eval', code: '1' }), null);
    assert.deepEqual(v.validateListen(['click', 'install', 1, 'keydown', 'dblclick', 'contextmenu', 'touchstart', 'wheel', 'focus']), ['click', 'dblclick', 'contextmenu']);
    assert.deepEqual(v.validateListen(['scroll', 'wheel']), []);
    v.cssEnv.nativeScrolling = true;
    assert.deepEqual(v.validateListen(['scroll', 'wheel', 'click']), ['scroll', 'click']);
    v.cssEnv.nativeScrolling = false;
    assert.deepEqual(v.validateListen(['scroll', 'click']), ['click']);
    assert.deepEqual(v.validatePreventDefault(['12:click', 'x:y', '12:keydown:Enter']), ['12:click', '12:keydown:Enter']);
  });
  it('canvas commands: allowlisted methods, props and object refs only; filter, shadows, compositing and conic gradients are outside the profile', () => {
    const diags: string[] = [];
    const ops = run([
      { op: 'el', id: 10, tag: 'canvas' },
      { op: 'canvas', id: 10, cmds: [
        ['c', 'fillRect', 0, 0, 1, 1], ['s', 'fillStyle', 'red'], ['c', 'drawImage', { n: 10 }, 0, 0], ['c', 'drawImage', { n: 3 }, 0, 0],
        ['s', 'filter', `url(${CANARY}#f)`], ['s', 'filter', 'blur(2px)'], ['c', 'eval', '1'], ['s', 'canvas', 1], ['r', 100, 50], ['r', -1, 1e9],
        ['g', 1, 'linear', 0, 0, 1, 1], ['gs', 1, 0, 'red'], ['pn', 2, 'M0 0L1 1'], ['pc', 2, 'lineTo', 1, 2], ['pc', 2, 'eval', 1],
        ['s', 'shadowBlur', 4], ['s', 'globalCompositeOperation', 'multiply'], ['g', 3, 'conic', 0, 0, 0], ['s', 'font', '12px "Inter", serif'],
      ] },
    ], diags);
    const cmds = (ops.find((o) => o.op === 'canvas') as any).cmds;
    assert.deepEqual(cmds, [
      ['c', 'fillRect', 0, 0, 1, 1], ['s', 'fillStyle', 'red'], ['c', 'drawImage', { n: 10 }, 0, 0], ['r', 100, 50],
      ['g', 1, 'linear', 0, 0, 1, 1], ['gs', 1, 0, 'red'], ['pn', 2, 'M0 0L1 1'], ['pc', 2, 'lineTo', 1, 2],
      // A conic gradient paints nothing: a zero-length linear one.
      ['g', 3, 'linear', 0, 0, 0, 0], ['s', 'font', '12px serif'],
    ]);
    assert.ok(['canvas filter', 'canvas shadowBlur', 'canvas globalCompositeOperation', 'canvas conic'].every((x) => diags.includes('profile: ' + x)), diags.join('\n'));
  });
});

describe('css sanitizer: shadow scope (ShadowDomRenderer prototype)', () => {
  const shadow = { colorScheme: 'light' as const, scope: 'shadow' as const };
  it('drops selectors that could reach the host page, keeps the rest of the list', () => {
    const d: string[] = [];
    const css = sanitizeStylesheet(':host{color:red} :host(.x) p{color:red} :host-context(.dark) b{color:red} ::slotted(span){color:red} i::part(x){color:red} :scope{color:red} p, :host{color:blue} q{color:green}', (k, x) => d.push(k + ':' + x), shadow);
    assert.equal(css, 'p{color:blue}q{color:green}');
    // Outside the profile on every renderer: each dropped selector is reported, `p, :host` keeps `p`
    // (css-tree cannot parse `::part(x)`: that rule goes as unparsable).
    assert.equal(d.filter((x) => x.startsWith('profile:selector')).length, 6);
  });
  it(':root becomes the renderer root attribute with the same specificity; :is(:root) too', () => {
    const css = sanitizeStylesheet(':root{--a:1} :is(:root) b{color:red} :root.dark{color:red}', noDiag, shadow);
    assert.equal(css, '[data-tw-root]{--a:1}:is([data-tw-root]) b{color:red}[data-tw-root].dark{color:red}');
  });
  it('width media queries become container queries on the bubble; queries outside the profile are dropped', () => {
    const css = sanitizeStylesheet(
      '@media (max-width:480px){p{color:red}} @media screen and (min-width: 600px) and (orientation: landscape){p{color:red}} @media only screen{p{color:red}} @media (hover:hover){p{color:red}} @media print{p{color:red}} @media (width >= 400px), (max-height: 20px){p{color:red}} @media (prefers-color-scheme: dark){p{color:red}}',
      noDiag, shadow);
    assert.equal(css,
      '@container tw (max-width:480px){p{color:red}}@container tw (min-width:600px) and (orientation:landscape){p{color:red}}@container tw (min-width: 0px){p{color:red}}@container tw (min-width: 99999px){p{color:red}}');
  });
  it('viewport units become container units (stylesheets, style attributes, properties)', () => {
    assert.equal(sanitizeStylesheet('p{width:50vw;font-size:2vmin;margin:1vi 3svw}', noDiag, shadow), 'p{width:50cqw;font-size:2cqmin;margin:1cqi 3cqw}');
    assert.equal(sanitizeStyleAttribute('width:10vw', noDiag, shadow), 'width:10cqw');
    assert.deepEqual(sanitizeStyleProperty('max-width', '100vw', noDiag, shadow), { name: 'max-width', value: '100cqw' });
    // Height-relative units are outside the profile (the bubble's height comes from its content).
    assert.equal(sanitizeStylesheet('p{height:100vh;margin:1dvh}', noDiag, shadow), 'p{}');
  });
  it('document scope (default) keeps :root, media queries and viewport units', () => {
    const css = ':root{--a:1} :host{color:red} @media (max-width:480px){p{width:50vw}}';
    assert.equal(sanitizeStylesheet(css, noDiag), ':root{--a:1}@media (max-width:480px){p{width:50vw}}');
  });
});

/** A strict validator with the document skeleton applied, for batches one at a time and `stopped`. */
function strictValidator(diags: string[] = []): Validator {
  const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
  v.setPolicy('strict');
  v.processOps([
    { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
    { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null },
  ]);
  return v;
}

describe('validator: 32 KB text values (strict stops the widget past one)', () => {
  const ok = 'a'.repeat(32 * 1024);
  const big = 'b'.repeat(32 * 1024 + 1);
  const canvas = [{ op: 'el', id: 23, tag: 'canvas' }, { op: 'ins', parent: 3, id: 23, before: null }];
  it('a text node, text data and a canvas string of 32 KB pass', () => {
    const v = strictValidator();
    const out = json(v.processOps([
      { op: 'text', id: 20, data: 'x' }, { op: 'ins', parent: 3, id: 20, before: null }, { op: 'data', id: 20, data: ok },
      ...canvas, { op: 'canvas', id: 23, cmds: [['c', 'fillText', 'c'.repeat(16 * 1024), 0, 0]] },
    ]));
    assert.ok(out.includes(ok));
    assert.equal(v.stopped, null);
  });
  for (const [what, ops] of [
    ['a text node', [{ op: 'text', id: 21, data: big }]],
    ['text data', [{ op: 'text', id: 21, data: 'x' }, { op: 'data', id: 21, data: big }]],
    ['a canvas string', [...canvas, { op: 'canvas', id: 23, cmds: [['c', 'fillText', 'short', 0, 0], ['c', 'fillText', big, 0, 0]] }]],
    ['Path2D data', [...canvas, { op: 'canvas', id: 23, cmds: [['pn', 1, big]] }]],
  ] as [string, unknown[]][]) {
    it(`${what} of 32 KB + 1 stops the widget and its batch is refused whole`, () => {
      const diags: string[] = [];
      const v = strictValidator(diags);
      assert.deepEqual(v.processOps([{ op: 'text', id: 30, data: 'before' }, ...ops]), []);
      assert.equal(v.stopped, 'Text limit exceeded: a text value of 32769 characters (max 32768)');
      assert.ok(diags.some((d) => d.startsWith('limit: Text limit exceeded')));
      assert.deepEqual(v.processOps([{ op: 'text', id: 31, data: 'after' }]), [], 'a stopped validator refuses every batch');
    });
  }

  it('keeps the 2 MB text and 4 MB canvas string limits under the default compat policy', () => {
    const big = 'b'.repeat(2 * 1024 * 1024);
    const canvasBig = 'c'.repeat(3 * 1024 * 1024);
    const over = 'o'.repeat(2 * 1024 * 1024 + 1);
    const out = run([
      { op: 'text', id: 21, data: big }, { op: 'ins', parent: 3, id: 21, before: null },
      { op: 'text', id: 24, data: over }, { op: 'ins', parent: 3, id: 24, before: null },
      { op: 'el', id: 23, tag: 'canvas' }, { op: 'ins', parent: 3, id: 23, before: null },
      { op: 'canvas', id: 23, cmds: [['c', 'fillText', canvasBig, 0, 0]] },
    ]);
    const texts = out.filter((o: any) => o.op === 'text').map((o: any) => o.id);
    assert.deepEqual(texts, [21]);
    assert.ok(out.some((o: any) => o.op === 'canvas' && JSON.stringify(o).includes(canvasBig)));
  });
});

describe('validator: complex-script text budget', () => {
  it('a document holds at most 8192 complex-script units in text nodes, counted on create, data and remove', () => {
    const arabic = (n: number) => 'ا'.repeat(n);
    const diags: string[] = [];
    const out = run([
      { op: 'text', id: 20, data: arabic(5000) + 'latin is free ' + 'x'.repeat(20000) }, { op: 'ins', parent: 3, id: 20, before: null },
      { op: 'text', id: 21, data: arabic(4000) }, { op: 'ins', parent: 3, id: 21, before: null },
      { op: 'text', id: 22, data: arabic(3000) }, { op: 'ins', parent: 3, id: 22, before: null },
      { op: 'data', id: 22, data: arabic(3193) },
      { op: 'data', id: 20, data: 'shrunk' },
      { op: 'text', id: 23, data: arabic(5000) }, { op: 'ins', parent: 3, id: 23, before: null },
      { op: 'rm', id: 23 },
      { op: 'text', id: 24, data: arabic(5000) }, { op: 'ins', parent: 3, id: 24, before: null },
      { op: 'text', id: 25, data: '😀'.repeat(100) }, { op: 'ins', parent: 3, id: 25, before: null },
      { op: 'comment', id: 26, data: arabic(9000) },
    ], diags, 'strict');
    const texts = out.filter((o: any) => o.op === 'text').map((o: any) => o.id);
    assert.deepEqual(texts, [20, 22, 23, 24]);
    assert.ok(out.some((o: any) => o.op === 'data' && o.id === 20));
    assert.ok(!out.some((o: any) => o.op === 'data' && o.id === 22), 'growing past the budget is refused');
    assert.ok(!out.some((o: any) => o.id === 25), '200 more units would pass 8192');
    assert.ok(diags.filter((d) => d.startsWith('text-budget')).length >= 3);
  });

  it('does not budget complex-script text under the default compat policy', () => {
    const diags: string[] = [];
    const out = run([
      { op: 'text', id: 20, data: '\u0627'.repeat(20000) }, { op: 'ins', parent: 3, id: 20, before: null },
      { op: 'text', id: 21, data: '\u4e2d'.repeat(20000) }, { op: 'ins', parent: 3, id: 21, before: null },
    ], diags);
    assert.deepEqual(out.filter((o: any) => o.op === 'text').map((o: any) => o.id), [20, 21]);
    assert.ok(!diags.some((d) => d.startsWith('text-budget')));
  });
});

describe('validator: visible attribute text budgets', () => {
  it('an attribute value over 8 KB stops the widget, on creation and on mutation', () => {
    const fits = 'x'.repeat(8 * 1024);
    const big = 'x'.repeat(8 * 1024 + 1);
    const v = strictValidator();
    const out = v.processOps([
      { op: 'el', id: 20, tag: 'button', attrs: { value: fits.slice(4000), title: fits.slice(5000) } },
      { op: 'el', id: 21, tag: 'img', attrs: { src: 'data:image/png;base64,' + 'A'.repeat(8000) } },
    ]);
    assert.equal(out.filter(o => o.op === 'el').length, 2);
    assert.equal(v.stopped, null);
    for (const op of [
      { op: 'el', id: 22, tag: 'div', attrs: { 'data-label': big } },
      { op: 'attr', id: 20, name: 'VALUE', value: big },
      { op: 'style', id: 20, name: '--x', value: big },
    ]) {
      const w = strictValidator();
      w.processOps([{ op: 'el', id: 20, tag: 'button' }]);
      assert.deepEqual(w.processOps([op]), []);
      assert.equal(w.stopped, 'Attribute limit exceeded: a value of 8193 characters (max 8192)');
    }
  });

  it('shares the 48 KB document budget with visible attributes and refunds removals', () => {
    const chunk = 'a'.repeat(16 * 1024);
    const v = strictValidator();
    const out = v.processOps([
      ...[20, 21].map(id => ({ op: 'text', id, data: chunk })),
      { op: 'el', id: 30, tag: 'div', attrs: { title: chunk.slice(0, 8192) } },
      { op: 'text', id: 22, data: chunk.slice(0, 8192) },
      { op: 'rm', id: 30 },
      { op: 'text', id: 24, data: chunk.slice(0, 8192) },
    ]);
    assert.equal(attrsOf(out, 30).title, chunk.slice(0, 8192));
    assert.ok(out.some(o => o.op === 'text' && o.id === 24), 'the removed title was refunded');
    assert.equal(v.stopped, null);
    assert.deepEqual(v.processOps([{ op: 'text', id: 25, data: 'overflow' }]), []);
    assert.equal(v.stopped, "Text limit exceeded: the document's text is over 49152 characters");
  });

  it('shares complex text budgets across attributes and text nodes', () => {
    const text = (n: number) => 'ا'.repeat(n);
    const out = run([
      { op: 'text', id: 20, data: text(6000) },
      { op: 'el', id: 21, tag: 'button', attrs: { title: text(3000), value: text(2000) } },
      { op: 'rattr', id: 21, name: 'VALUE' },
      { op: 'attr', id: 21, name: 'value', value: text(2192) },
      { op: 'attr', id: 21, name: 'title', value: text(1) },
    ], [], 'strict');
    assert.equal(attrsOf(out, 21).title, undefined);
    assert.equal(attrsOf(out, 21).value, text(2000));
    assert.ok(out.some(o => o.op === 'attr' && o.name === 'value' && o.value === text(2192)));
    assert.ok(!out.some(o => o.op === 'attr' && o.name === 'title'));
  });

  it('rate limits repeated attribute changes', () => {
    const diags: string[] = [];
    const out = run([
      { op: 'el', id: 20, tag: 'div' },
      ...Array.from({ length: 10 }, (_, i) => ({ op: 'attr', id: 20, name: 'title', value: String.fromCharCode(0x4e00 + i).repeat(3000) })),
    ], diags, 'strict');
    assert.equal(out.filter(o => o.op === 'attr').length, 2);
    assert.ok(diags.some(d => d.startsWith('text-churn')));
  });

  it('preserves compatibility policy limits for visible attributes and their churn', () => {
    const big = 'x'.repeat(64 * 1024 + 1);
    const diags: string[] = [];
    const out = run([
      { op: 'el', id: 20, tag: 'button', attrs: { value: big } },
      ...Array.from({ length: 10 }, (_, i) => ({ op: 'attr', id: 20, name: 'title', value: String.fromCharCode(0x4e00 + i).repeat(3000) })),
    ], diags);
    assert.equal(attrsOf(out, 20).value, big);
    assert.equal(out.filter(o => o.op === 'attr').length, 10);
    assert.ok(!diags.some(d => d.startsWith('text-')));
  });
});

describe('validator: strict budgets for text reached through attributes and CSS', () => {
  const cjk = (n: number, base = 0x4e00) => Array.from({ length: n }, (_, i) => String.fromCharCode(base + (i % 512))).join('');

  it('data-* and aria-* values count like visible text, other attributes do not', () => {
    const diags: string[] = [];
    const out = run([
      { op: 'el', id: 20, tag: 'p', attrs: { 'data-x': cjk(6000), class: 'a b' } },
      { op: 'el', id: 21, tag: 'p', attrs: { 'aria-label': cjk(3000) } },
      { op: 'el', id: 22, tag: 'p' },
      { op: 'attr', id: 22, name: 'data-y', value: cjk(3000) },
      { op: 'attr', id: 22, name: 'class', value: 'x'.repeat(8000) },
    ], diags, 'strict');
    assert.equal(attrsOf(out, 20)['data-x'], cjk(6000));
    assert.equal(attrsOf(out, 21)['aria-label'], undefined);
    assert.ok(!out.some((o) => o.op === 'attr' && o.name === 'data-y'));
    assert.ok(out.some((o) => o.op === 'attr' && o.name === 'class'));
    assert.ok(diags.some((d) => d.startsWith('text-budget')));
  });

  it('the 60 000-ideograph data attribute from the branch review stops the widget (past the 8 KB attribute cap)', () => {
    const v = strictValidator();
    assert.deepEqual(v.processOps([{ op: 'el', id: 20, tag: 'p', attrs: { 'data-x': cjk(60_000) } }]), []);
    assert.match(v.stopped ?? '', /^Attribute limit exceeded/);
  });

  it('attr() may only read budgeted attributes under strict', () => {
    const strict = { colorScheme: 'light' as const, strict: true };
    assert.equal(sanitizeStyleProperty('content', 'attr(data-x)', noDiag, strict)?.value, 'attr(data-x)');
    assert.equal(sanitizeStyleProperty('content', 'attr(title)', noDiag, strict)?.value, 'attr(title)');
    const diags: string[] = [];
    assert.equal(sanitizeStyleProperty('content', 'attr(class)', (k, d) => diags.push(`${k}: ${d}`), strict), null);
    assert.ok(diags.some((d) => d.includes('attr(class)')));
    assert.ok(!sanitizeStylesheet('p::before{content:attr(id)}', noDiag, strict).includes('attr'));
    assert.equal(sanitizeStyleProperty('content', 'attr(class)', noDiag, { colorScheme: 'light' })?.value, 'attr(class)');
    const out = run([
      { op: 'el', id: 20, tag: 'style' }, { op: 'ins', parent: 3, id: 20, before: null },
      { op: 'text', id: 21, data: 'p::before{content:attr(class)} q::before{content:attr(data-q)}' }, { op: 'ins', parent: 20, id: 21, before: null },
    ], [], 'strict');
    const css = out.filter((o) => o.op === 'css').map((o: any) => o.text).join('');
    assert.ok(!css.includes('attr(class)') && css.includes('attr(data-q)'), css);
  });

  it('strings in style properties and style attributes are charged', () => {
    const diags: string[] = [];
    const out = run([
      { op: 'el', id: 20, tag: 'p' }, { op: 'ins', parent: 3, id: 20, before: null },
      { op: 'style', id: 20, name: '--x', value: `"${cjk(5000)}"` },
      { op: 'el', id: 21, tag: 'p', attrs: { style: `--y: "${cjk(5000)}"` } },
      { op: 'style', id: 20, name: 'content', value: `"${cjk(5000)}"` },
    ], diags, 'strict');
    assert.ok(out.some((o) => o.op === 'style' && o.name === '--x' && o.value.length > 5000));
    assert.equal(attrsOf(out, 21).style, undefined, 'the second 5000 ideographs are over the budget');
    assert.ok(out.some((o) => o.op === 'style' && o.name === 'content' && o.value === ''), 'an over-budget property is cleared');
    assert.ok(diags.some((d) => d.startsWith('text-')));
  });

  it('stylesheet strings are charged decoded, so escapes do not hide ideographs', () => {
    const diags: string[] = [];
    const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
    v.setPolicy('strict');
    let t = 0;
    v.setClock(() => t);
    v.processOps([
      { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
      { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null },
      { op: 'text', id: 10, data: cjk(7000) }, { op: 'ins', parent: 3, id: 10, before: null },
    ]);
    t += 60;
    const escaped = Array.from({ length: 2000 }, () => '\\5b57 ').join('');
    const out = v.processOps([
      { op: 'el', id: 22, tag: 'style' }, { op: 'ins', parent: 3, id: 22, before: null },
      { op: 'text', id: 23, data: `p::after{content:"${escaped}"}` }, { op: 'ins', parent: 22, id: 23, before: null },
    ]);
    assert.equal((out.filter((o) => o.op === 'css' && o.id === 22).pop() as any)?.text, '');
    assert.ok(diags.some((d) => d.startsWith('text-budget')));
    const small = v.processOps([{ op: 'data', id: 23, data: 'p::after{content:"\\5b57"}' }]);
    assert.equal((small.filter((o) => o.op === 'css' && o.id === 22).pop() as any)?.text, 'p::after{content:"字"}');
  });

  it('compat leaves attributes and CSS strings unbudgeted', () => {
    const diags: string[] = [];
    const out = run([
      { op: 'el', id: 20, tag: 'p', attrs: { 'data-x': cjk(60_000), style: `--y: "${cjk(9000)}"` } },
    ], diags);
    assert.equal(attrsOf(out, 20)['data-x'], cjk(60_000));
    assert.ok(attrsOf(out, 20).style.length > 9000);
    assert.ok(!diags.some((d) => d.startsWith('text-')));
  });
});

describe('validator: canvas text churn', () => {
  it('limits repeated canvas text while preserving other drawing commands', () => {
    const diags: string[] = [];
    const out = run([
      { op: 'el', id: 20, tag: 'canvas' },
      { op: 'canvas', id: 20, cmds: [
        ...Array.from({ length: 10 }, () => ['c', 'fillText', '中'.repeat(3000), 0, 0]),
        ['c', 'fillRect', 0, 0, 10, 10], ['c', 'strokeText', 'Ready', 0, 0],
      ] },
    ], diags, 'strict');
    const commands = out.filter(o => o.op === 'canvas').flatMap(o => o.cmds);
    assert.equal(commands.length, 4);
    assert.equal(commands.filter(c => c[1] === 'fillText').length, 2);
    assert.ok(diags.some(d => d.startsWith('text-churn')));
  });

  it('shares canvas churn with document text and keeps compatibility unrestricted', () => {
    for (const policy of ['strict', 'compat'] as const) {
      const out = run([
        { op: 'text', id: 21, data: '中'.repeat(6000) },
        { op: 'el', id: 20, tag: 'canvas' },
        { op: 'canvas', id: 20, cmds: [['c', 'strokeText', '中'.repeat(3000), 0, 0]] },
      ], [], policy);
      assert.equal(out.filter(o => o.op === 'canvas').length, policy === 'strict' ? 0 : 1);
    }
  });
});
