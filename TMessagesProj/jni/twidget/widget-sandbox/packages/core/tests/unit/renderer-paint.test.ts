// The heavy-box paint limit in the renderer core (paint-policy.ts, docs/decisions.md "Heavy boxes"): what it counts,
// when it reports `limit`, and the stopped widget drawn without effects. The Shadow DOM renderer (the core on a shadow
// root) in headless Chromium, and the document renderer (the iframe's and the native WebViews' bundle) once.

import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { chromium, type Browser, type Page } from '@playwright/test';
import { buildSrcdoc, makeNonce } from '../../src/renderer/srcdoc.ts';
import { rendererBuildOptions } from '../../scripts/sandbox-build.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

type Node = { tag: string; attrs?: [string, string][]; ns?: 'svg'; children?: Node[]; text?: string };
/** Ops for a document: the stylesheet in <head>, `body` under <body>. Ids from 10 up; returns the ops and the ids by `data-k`. */
function documentOps(css: string, body: Node[]) {
  const ops: unknown[] = [
    { op: 'el', id: 2, tag: 'html', adopt: 'html', attrs: [] }, { op: 'ins', parent: 1, id: 2, before: null },
    { op: 'el', id: 3, tag: 'head', adopt: 'head', attrs: [] }, { op: 'ins', parent: 2, id: 3, before: null },
    { op: 'el', id: 4, tag: 'body', adopt: 'body', attrs: [] }, { op: 'ins', parent: 2, id: 4, before: null },
    { op: 'el', id: 5, tag: 'style', attrs: [] }, { op: 'ins', parent: 3, id: 5, before: null }, { op: 'css', id: 5, text: css },
  ];
  const ids: Record<string, number> = {};
  let next = 10;
  const add = (n: Node, parent: number) => {
    const id = next++;
    if (n.text !== undefined) { ops.push({ op: 'text', id, data: n.text }, { op: 'ins', parent, id, before: null }); return; }
    ops.push({ op: 'el', id, tag: n.tag, ...(n.ns ? { ns: n.ns } : {}), attrs: n.attrs ?? [] }, { op: 'ins', parent, id, before: null });
    const k = n.attrs?.find(([a]) => a === 'data-k')?.[1];
    if (k) ids[k] = id;
    for (const c of n.children ?? []) add(c, id);
  };
  for (const n of body) add(n, 4);
  return { ops, ids };
}
const divs = (n: number, cls: string, from = 0): Node[] => Array.from({ length: n }, (_, i) => ({ tag: 'div', attrs: [['class', cls], ['data-k', `d${from + i}`]] }));

let browser: Browser;
before(async () => { browser = await chromium.launch(); });
after(async () => { await browser.close(); });

let shadowBundle: string | null = null;
/** A page with the Shadow DOM renderer; `send` delivers host → renderer messages, `messages` what it posted. */
async function shadowPage(policy?: 'strict' | 'compat') {
  shadowBundle ??= (await build({
    stdin: {
      contents: `import { ShadowDomRenderer } from './src/host/shadow-renderer.ts';
        const r = new ShadowDomRenderer(document.getElementById('bubble'), 'light');
        window.__messages = [];
        r.onMessage = (m) => window.__messages.push(m);
        r.load();
        r.element.style.height = '400px';
        window.__renderer = r;
        window.__dom = r.shadow;`,
      resolveDir: root,
      loader: 'ts',
    },
    bundle: true, write: false, format: 'iife', platform: 'browser',
  })).outputFiles![0].text;
  const page = await browser.newPage({ viewport: { width: 400, height: 600 } });
  const url = 'http://renderer.invalid/';
  const html = `<!doctype html><body style="margin:0"><div id="bubble" style="width:360px;height:400px"></div><script>${shadowBundle}</script></body>`;
  await page.route('**/*', (route) => (route.request().url() === url ? route.fulfill({ body: html, contentType: 'text/html' }) : route.abort()));
  await page.goto(url);
  if (policy) await send(page, { type: 'policy', policy });
  return page;
}

const send = (page: Page, msg: unknown) => page.evaluate((m) => (window as any).__renderer.send(m), msg);
/** Two frames and a little: the count runs in the frame after a change. */
const frames = (page: Page, n = 2) => page.evaluate(async (n) => { for (let i = 0; i < n; i++) await new Promise((r) => requestAnimationFrame(r)); }, n);
const limits = (page: Page) => page.evaluate(() => (window as any).__messages.filter((m: any) => m.type === 'limit'));
/** Computed values of the element with `data-k` = k in the widget. */
const computed = (page: Page, k: string, pseudo?: string) => page.evaluate(([k, pseudo]) => {
  const el = (window as any).__dom.querySelector(`[data-k="${k}"]`);
  const cs = getComputedStyle(el, pseudo || null);
  return { opacity: cs.opacity, boxShadow: cs.boxShadow, filter: cs.filter, backdropFilter: cs.backdropFilter, visibility: cs.visibility };
}, [k, pseudo ?? ''] as const);

async function load(page: Page, css: string, body: Node[]) {
  const d = documentOps(css, body);
  await send(page, { type: 'ops', ops: d.ops });
  await frames(page);
  return d.ids;
}

const SHADOW = 'box-shadow:0 2px 6px rgba(0,0,0,.3)';

describe('renderer core: heavy boxes', () => {
  it('16 heavy boxes run; 17 stop with paint-limit 17, drawn without the effects', async () => {
    const page = await shadowPage();
    await load(page, `.h{${SHADOW};width:10px;height:10px}`, divs(16, 'h'));
    assert.deepEqual(await limits(page), []);
    const page2 = await shadowPage();
    await load(page2, `.h{${SHADOW};width:10px;height:10px}`, divs(17, 'h'));
    assert.deepEqual(await limits(page2), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    assert.equal((await computed(page2, 'd0')).boxShadow, 'none');
    await page.close(); await page2.close();
  });

  it('a class change that makes the 17th box heavy stops it; later ops are not applied', async () => {
    const page = await shadowPage();
    const ids = await load(page, '.h{opacity:.5}', [...divs(16, 'h'), { tag: 'div', attrs: [['data-k', 'last']] }]);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.last, name: 'class', value: 'h' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    assert.equal((await computed(page, 'last')).opacity, '1');
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.last, name: 'style', value: 'opacity:.2 !important' }] });
    await frames(page);
    assert.equal((await computed(page, 'last')).opacity, '1');
    await page.close();
  });

  it('a CSS animation of opacity counts while it runs, whatever the value', async () => {
    const page = await shadowPage();
    // opacity stays 1 all the time: only rule 5 makes these boxes heavy
    await load(page, '@keyframes p{from{opacity:1}to{opacity:1}}.a{width:10px;height:10px;animation:p 1s infinite}', divs(17, 'a'));
    await frames(page, 4);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('an animation of shadows without blur does not count; a transition to a blurred one does', async () => {
    const page = await shadowPage();
    const css = '@keyframes sp{0%{box-shadow:inset 0 0 0 9px rgba(255,255,255,0)}50%{box-shadow:inset 0 0 0 9px rgba(255,255,255,.5)}100%{box-shadow:inset 0 0 0 9px rgba(255,255,255,0)}}' +
      '.s{width:18px;height:18px;background:#36c;animation:sp 400ms linear infinite}.g{width:10px;height:10px;transition:box-shadow 2s}.g.on{box-shadow:0 0 10px #fc0}';
    const ids = await load(page, css, [...divs(20, 's'), ...divs(17, 'g', 20)]);
    await frames(page, 6);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: Array.from({ length: 17 }, (_, i) => ({ op: 'attr', id: ids[`d${20 + i}`], name: 'class', value: 'g on' })) });
    await frames(page, 6);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('an animation of another property does not count, and an ended one gives its box back', async () => {
    const page = await shadowPage();
    await load(page, '@keyframes s{to{transform:translateX(5px)}}@keyframes f{to{opacity:.5}}.a{width:10px;height:10px;animation:s 1s infinite}.b{width:10px;height:10px;animation:f 50ms}', [...divs(17, 'a'), ...divs(16, 'b', 17)]);
    await frames(page, 4);
    await page.waitForTimeout(150);
    await frames(page);
    assert.deepEqual(await limits(page), []);
    await page.close();
  });

  it('a box is read again when its animation ends: a finished fade-in leaves nothing heavy', async () => {
    const page = await shadowPage();
    const ids = await load(page, '@keyframes in{from{opacity:0}to{opacity:1}}.f{width:10px;height:10px;animation:in 120ms}.h{filter:blur(1px)}', [...divs(16, 'f'), { tag: 'div', attrs: [['data-k', 'last']] }]);
    await page.waitForTimeout(400);
    await frames(page);
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.last, name: 'class', value: 'h' }] });
    await frames(page);
    assert.deepEqual(await limits(page), []);
    await page.close();
  });

  it('inline styles: a transform is no change of a heavy property, unless a selector reads the style attribute', async () => {
    const page = await shadowPage();
    const ids = await load(page, 'div{width:10px;height:10px}div[style*="rotate"]{opacity:.5}', divs(17, 'x'));
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: Object.values(ids).map((id) => ({ op: 'style', id, name: 'transform', value: 'rotate(10deg)' })) });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('a style attribute with opacity is counted, and one that drops it gives the box back', async () => {
    const page = await shadowPage();
    const ids = await load(page, 'div{width:10px;height:10px}', divs(17, 'x'));
    const all = Object.values(ids);
    await send(page, { type: 'ops', ops: all.slice(0, 16).map((id) => ({ op: 'attr', id, name: 'style', value: 'opacity:.5;transform:none' })) });
    await frames(page);
    await send(page, { type: 'ops', ops: [...all.slice(0, 2).map((id) => ({ op: 'attr', id, name: 'style', value: 'transform:rotate(1deg)' })), { op: 'attr', id: all[16], name: 'style', value: 'opacity:.5' }] });
    await frames(page);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: all.slice(0, 2).map((id) => ({ op: 'attr', id, name: 'style', value: 'transform:none;opacity:0.9' })) });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('an inline custom property that feeds opacity is counted', async () => {
    const page = await shadowPage();
    const ids = await load(page, '.w{--o:1}.w div{width:10px;height:10px;opacity:var(--o)}', [{ tag: 'div', attrs: [['class', 'w'], ['data-k', 'w']], children: divs(17, 'x') }]);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: [{ op: 'style', id: ids.w, name: '--o', value: '.5' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('a Web Animation of the renderer counts while it runs', async () => {
    const page = await shadowPage();
    const ids = await load(page, '.a{width:10px;height:10px}', divs(17, 'a'));
    const anims = Object.values(ids).map((target, i) => ({
      aid: i + 1, target, keyframes: [{ offset: 0, easing: 'linear', composite: null, values: { opacity: '1' } }, { offset: 1, easing: 'linear', composite: null, values: { opacity: '1' } }],
      timing: { duration: 1000, delay: 0, endDelay: 0, iterations: null, iterationStart: 0, direction: 'normal', fill: 'none', easing: 'linear' },
      composite: 'replace', iterationComposite: 'replace', startTime: 0, holdTime: null, playbackRate: 1,
    }));
    // paused (a hold time): not running, not counted
    await send(page, { type: 'anim', now: 0, anims: anims.map((a) => ({ ...a, holdTime: 0 })) });
    await frames(page);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'anim', now: await page.evaluate(() => performance.now()), anims });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('a ::before with content is a box of its own; one without content is not', async () => {
    const page = await shadowPage();
    const css = '.h{opacity:.8;width:10px;height:10px}.n::before{content:none;opacity:.5}.x::before{content:"";display:block;width:4px;height:4px;filter:blur(1px)}';
    const ids = await load(page, css, [...divs(16, 'h n'), { tag: 'p', attrs: [['data-k', 'p']], children: [{ tag: '', text: 'x' }] }]);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.p, name: 'class', value: 'x' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    assert.equal((await computed(page, 'p', '::before')).filter, 'none');
    await page.close();
  });

  it('two boxes with backdrop-filter run; a third stops with backdrop-limit 3', async () => {
    const page = await shadowPage();
    await load(page, '.p{backdrop-filter:blur(6px);width:10px;height:10px}', divs(2, 'p'));
    assert.deepEqual(await limits(page), []);
    const page2 = await shadowPage();
    await load(page2, '.p{backdrop-filter:blur(6px);width:10px;height:10px}', divs(3, 'p'));
    assert.deepEqual(await limits(page2), [{ type: 'limit', code: 'backdrop-limit', count: 3 }]);
    assert.equal((await computed(page2, 'd0')).backdropFilter, 'none');
    await page.close(); await page2.close();
  });

  it('blur-less shadows, boxes under display: none and display: contents do not count', async () => {
    const page = await shadowPage();
    const css = '.r{box-shadow:0 0 0 2px red,inset 0 1px 0 #fff;width:10px;height:10px}.h{filter:blur(2px)}.off{display:none}.c{display:contents;opacity:.5}';
    await load(page, css, [
      ...divs(30, 'r'),
      { tag: 'div', attrs: [['class', 'off']], children: divs(20, 'h', 30) },
      ...Array.from({ length: 20 }, (_, i): Node => ({ tag: 'span', attrs: [['class', 'c']], children: [{ tag: 'b', attrs: [['data-k', `c${i}`]] }] })),
    ]);
    assert.deepEqual(await limits(page), []);
    await page.close();
  });

  it('a subtree shown by removing display: none is counted', async () => {
    const page = await shadowPage();
    const ids = await load(page, '.h{filter:blur(2px);width:10px;height:10px}.off{display:none}', [{ tag: 'div', attrs: [['class', 'off'], ['data-k', 'w']], children: divs(17, 'h') }]);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: [{ op: 'rattr', id: ids.w, name: 'class' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('a sibling combinator reaches the siblings of the element that changed', async () => {
    const page = await shadowPage();
    const ids = await load(page, '.on ~ div{opacity:.5}div{width:10px;height:10px}', [{ tag: 'div', attrs: [['data-k', 'first']] }, ...divs(17, 'x')]);
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.first, name: 'class', value: 'on' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it(':hover is counted when the pointer moves', async () => {
    const page = await shadowPage();
    await load(page, '.g{width:300px;height:100px}.g:hover .c{opacity:.5}.c{width:10px;height:10px}', [{ tag: 'div', attrs: [['class', 'g'], ['data-k', 'g']], children: divs(17, 'c') }]);
    assert.deepEqual(await limits(page), []);
    await page.mouse.move(100, 50);
    await frames(page, 3);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('a change of the bubble width (@container) recounts every box', async () => {
    const page = await shadowPage();
    await load(page, '.c{width:10px;height:10px}@container tw (max-width: 300px){.c{opacity:.5}}', divs(17, 'c'));
    assert.deepEqual(await limits(page), []);
    await page.evaluate(() => { document.getElementById('bubble')!.style.width = '280px'; });
    await frames(page, 3);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('disabled buttons dimmed by the UA layer do not count: 30 of them next to 16 heavy boxes run, and keep their dimming when stopped', async () => {
    const page = await shadowPage();
    const buttons: Node[] = Array.from({ length: 30 }, (_, i) => ({ tag: 'button', attrs: [['disabled', ''], ['data-k', `b${i}`]], children: [{ tag: '', text: String(i) }] }));
    const ids = await load(page, `.h{${SHADOW};width:10px;height:10px}`, [...buttons, ...divs(16, 'h')]);
    assert.equal((await computed(page, 'b0')).opacity, '0.5');
    assert.deepEqual(await limits(page), []);
    // a blurred shadow on one of them still counts: the 17th
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.b1, name: 'style', value: 'box-shadow:0 2px 6px #000' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    assert.equal((await computed(page, 'b0')).opacity, '0.5');
    assert.equal((await computed(page, 'b1')).boxShadow, 'none');
    await page.close();
  });

  it('a pressed button\'s dimming does not count; an enabled button with opacity does', async () => {
    const page = await shadowPage();
    const ids = await load(page, `.h{${SHADOW};width:10px;height:10px}button{width:120px;height:40px}`, [...divs(16, 'h'), { tag: 'button', attrs: [['data-k', 'btn']], children: [{ tag: '', text: 'press' }] }]);
    const box = await page.evaluate(() => { const r = (window as any).__dom.querySelector('[data-k="btn"]').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
    await page.mouse.move(box.x, box.y);
    await page.mouse.down();
    await frames(page, 3);
    const pressed = (await computed(page, 'btn')).opacity;
    await page.mouse.up();
    await frames(page, 2);
    assert.equal(pressed, '0.7');
    assert.deepEqual(await limits(page), []);
    await send(page, { type: 'ops', ops: [{ op: 'attr', id: ids.btn, name: 'style', value: 'opacity:.6' }] });
    await frames(page);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });

  it('compat counts nothing', async () => {
    const page = await shadowPage('compat');
    await load(page, `.h{${SHADOW};width:10px;height:10px}`, divs(40, 'h'));
    assert.deepEqual(await limits(page), []);
    assert.notEqual((await computed(page, 'd0')).boxShadow, 'none');
    await page.close();
  });

  it('the stopped frame: no blurred shadow layer, no filter, opacity 1, a fully transparent box hidden, blur-less layers kept', async () => {
    const page = await shadowPage();
    const css = '.h{width:10px;height:10px;opacity:.6!important}#z.h.h{filter:blur(3px)!important}.s{box-shadow:0 0 0 2px red,0 4px 12px rgba(0,0,0,.3)}.t{opacity:0}';
    await load(page, css, [
      ...divs(14, 'h'),
      { tag: 'div', attrs: [['class', 'h'], ['id', 'z'], ['data-k', 'z']] },
      { tag: 'div', attrs: [['class', 's'], ['data-k', 's']] },
      { tag: 'div', attrs: [['class', 't'], ['data-k', 't']] },
      { tag: 'div', attrs: [['style', 'opacity:.3 !important;width:10px;height:10px'], ['data-k', 'inline']] },
    ]);
    assert.deepEqual(await limits(page), [{ type: 'limit', code: 'paint-limit', count: 18 }]);
    assert.deepEqual(await computed(page, 'z'), { opacity: '1', boxShadow: 'none', filter: 'none', backdropFilter: 'none', visibility: 'visible' });
    assert.equal((await computed(page, 's')).boxShadow, 'rgb(255, 0, 0) 0px 0px 0px 2px');
    assert.deepEqual([(await computed(page, 't')).opacity, (await computed(page, 't')).visibility], ['1', 'hidden']);
    assert.equal((await computed(page, 'inline')).opacity, '1');
    // the host's frozen comes next: the badge, and no grayscale filter on the root
    await send(page, { type: 'frozen', reason: 'Widget stopped', fatal: true });
    const rootFilter = await page.evaluate(() => getComputedStyle((window as any).__dom.querySelector('html')).filter);
    assert.equal(rootFilter, 'none');
    await page.close();
  });

  it('animations stop where they are when the widget is stopped', async () => {
    const page = await shadowPage();
    await load(page, '@keyframes m{to{transform:translateX(100px)}}.a{width:10px;height:10px;opacity:.5;animation:m 10s linear infinite}', divs(17, 'a'));
    const states = await page.evaluate(() => (window as any).__dom.querySelector('.a').getAnimations().map((a: Animation) => a.playState));
    assert.deepEqual(states, ['paused']);
    await page.close();
  });

  it('the document renderer (the iframe\'s and the native WebViews\' bundle) counts too', async () => {
    const out = await build(rendererBuildOptions(root, 'webkit'));
    const page = await browser.newPage({ viewport: { width: 360, height: 400 } });
    const messages: any[] = [];
    await page.exposeFunction('__twOut', (json: string) => { messages.push(JSON.parse(json)); });
    await page.addInitScript({ content: 'window.webkit = { messageHandlers: { tw: { postMessage: function (s) { window.__twOut(s); } } } };' });
    const url = 'http://renderer.invalid/';
    const doc = buildSrcdoc(out.outputFiles![0].text, makeNonce());
    await page.route('**/*', (route) => (route.request().url() === url ? route.fulfill({ body: doc, contentType: 'text/html' }) : route.abort()));
    await page.goto(url);
    await page.waitForFunction(() => typeof (window as any).__twReceive === 'function');
    const recv = (m: unknown) => page.evaluate((j) => (window as any).__twReceive(j), JSON.stringify(m));
    await recv({ type: 'policy', policy: 'strict', paintReports: true });
    await recv({ type: 'ops', ops: documentOps('.h{filter:saturate(2);width:10px;height:10px}', divs(16, 'h')).ops });
    await frames(page);
    await page.waitForTimeout(50);
    assert.deepEqual(messages.filter((m) => m.type === 'paint').at(-1), { type: 'paint', heavy: 16, backdrop: 0 });
    assert.deepEqual(messages.filter((m) => m.type === 'limit'), []);
    await recv({ type: 'ops', ops: [{ op: 'el', id: 100, tag: 'div', attrs: [['class', 'h']] }, { op: 'ins', parent: 4, id: 100, before: null }] });
    await frames(page);
    await page.waitForTimeout(50);
    assert.deepEqual(messages.filter((m) => m.type === 'limit'), [{ type: 'limit', code: 'paint-limit', count: 17 }]);
    await page.close();
  });
});
