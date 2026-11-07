import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { chromium, type Page } from '@playwright/test';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

/**
 * The Shadow DOM renderer in a page of its own next to a control of the "app" (`#app-button`), driven by Playwright's
 * trusted input. Messages the renderer posts land in `window.__messages`.
 */
async function openShadowRenderer(page: Page) {
  const out = await build({
    stdin: {
      contents: `import { ShadowDomRenderer } from './src/host/shadow-renderer.ts';
        const r = new ShadowDomRenderer(document.getElementById('bubble'), 'light');
        window.__messages = [];
        r.onMessage = (m) => window.__messages.push(m);
        r.load();
        // what WidgetHost does from the size reports
        r.element.style.height = '120px';
        window.__renderer = r;`,
      resolveDir: root,
      loader: 'ts',
    },
    bundle: true,
    write: false,
    format: 'iife',
    platform: 'browser',
  });
  const url = 'http://renderer.invalid/';
  const html = '<!doctype html><body style="margin:0">' +
    '<button id="app-button" style="display:block;width:200px;height:60px">app</button>' +
    '<div id="bubble" style="width:300px;height:120px"></div>' +
    `<script>window.__appClicks = 0; document.getElementById('app-button').addEventListener('click', () => window.__appClicks++);</script>` +
    `<script>${out.outputFiles![0].text}</script></body>`;
  await page.route('**/*', (route) => route.request().url() === url
    ? route.fulfill({ body: html, contentType: 'text/html' })
    : route.abort());
  await page.goto(url);
  await page.evaluate(() => {
    const w = window as any;
    w.__renderer.send({ type: 'ops', ops: [
      { op: 'el', id: 2, tag: 'html', adopt: 'html', attrs: [] }, { op: 'ins', parent: 1, id: 2, before: null },
      { op: 'el', id: 3, tag: 'body', adopt: 'body', attrs: [] }, { op: 'ins', parent: 2, id: 3, before: null },
      { op: 'el', id: 4, tag: 'div', attrs: [['style', 'width:200px;height:80px;background:#8cf']] },
      { op: 'ins', parent: 3, id: 4, before: null },
    ] });
    w.__renderer.send({ type: 'listen', types: ['pointerdown', 'pointerup', 'mouseup'] });
  });
}

/** The centre of the app's button and of the widget's element, in page coordinates. */
const points = (page: Page) => page.evaluate(() => {
  // no named functions here: tsx would wrap them in a helper the page does not have
  const [app, widget] = [document.getElementById('app-button')!, document.querySelector('.widget-shadow')!.shadowRoot!.querySelector('body > div')!]
    .map((el) => el.getBoundingClientRect())
    .map((r) => ({ x: r.x + r.width / 2, y: r.y + r.height / 2 }));
  return { app, widget };
});

/** Asks the renderer to capture `pointerId` on the widget's element (what `el.setPointerCapture` becomes) and says whether it has it. */
const capture = (page: Page, pointerId: number) => page.evaluate((id) => {
  const w = window as any;
  w.__renderer.send({ type: 'ops', ops: [{ op: 'call', id: 4, method: 'setPointerCapture', args: [id] }] });
  return (document.querySelector('.widget-shadow')!.shadowRoot!.querySelector('body > div') as Element).hasPointerCapture(id);
}, pointerId);

/** The press events the renderer sent the widget, of `types`, and whether it marked them trusted (a gesture). */
const sent = (page: Page, types: string[]) => page.evaluate((t) => (window as any).__messages
  .filter((m: any) => m.type === 'event' && t.includes(m.ev.type))
  .map((m: any) => `${m.ev.type}:${m.trusted ? 'trusted' : 'untrusted'}`), types);
const releases = (page: Page) => sent(page, ['pointerup', 'mouseup']);

describe('renderer: presses that began on the widget', () => {
  it('captures only a pointer pressed on the widget; a press begun elsewhere and let go over it is no gesture', async () => {
    const browser = await chromium.launch();
    try {
      const page = await browser.newPage();
      await openShadowRenderer(page);
      await page.evaluate(() => addEventListener('pointerdown', (e) => { (window as any).__pointerId = e.pointerId; }, true));
      const p = await points(page);

      // a chorded press of the widget's own (a second button pressed and let go while the first is held: a pointermove and
      // a mouse event, no pointerdown or pointerup) is the widget's: its mouseup is trusted, here before any pointerup
      const cdp = await page.context().newCDPSession(page);
      const mouse = (type: 'mousePressed' | 'mouseReleased', button: 'left' | 'right', buttons: number) =>
        cdp.send('Input.dispatchMouseEvent', { type, x: p.widget.x, y: p.widget.y, button, buttons, clickCount: 1 });
      await mouse('mousePressed', 'left', 1);
      await mouse('mousePressed', 'right', 3);
      await mouse('mouseReleased', 'right', 1);
      const chorded = await releases(page);
      await mouse('mouseReleased', 'left', 0);
      assert.deepEqual(chorded, ['mouseup:trusted']);
      await page.evaluate(() => { (window as any).__messages = []; });

      // pressed on the app's button: the widget cannot take the pointer, and the button keeps its click
      await page.mouse.move(p.app.x, p.app.y);
      await page.mouse.down();
      const id = await page.evaluate(() => (window as any).__pointerId as number);
      assert.equal(await capture(page, id), false);
      await page.mouse.up();
      assert.equal(await page.evaluate(() => (window as any).__appClicks), 1);

      // pressed on the widget: its own pointer it may capture
      await page.mouse.move(p.widget.x, p.widget.y);
      await page.mouse.down();
      assert.equal(await capture(page, id), true);
      await page.mouse.up();
      assert.deepEqual(await releases(page), ['pointerup:trusted', 'mouseup:trusted']);

      // pressed on the app's button and let go over the widget: the release reaches it, untrusted
      await page.evaluate(() => { (window as any).__messages = []; });
      await page.mouse.move(p.app.x, p.app.y);
      await page.mouse.down();
      await page.mouse.move(p.widget.x, p.widget.y, { steps: 5 });
      await page.mouse.up();
      assert.deepEqual(await releases(page), ['pointerup:untrusted', 'mouseup:untrusted']);
    } finally {
      await browser.close();
    }
  });

  it("a finger's press is a gesture from its release: until then it may still become the chat's scroll", async () => {
    const browser = await chromium.launch();
    try {
      const context = await browser.newContext({ hasTouch: true });
      const page = await context.newPage();
      await openShadowRenderer(page);
      const p = await points(page);
      const cdp = await context.newCDPSession(page);
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: p.widget.x, y: p.widget.y, id: 1 }] });
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await page.waitForTimeout(100);
      assert.deepEqual(await sent(page, ['pointerdown', 'pointerup']), ['pointerdown:untrusted', 'pointerup:trusted']);

      // a pen's contact makes its compatibility mousedown at once: no gesture either, until the pen is lifted
      await page.evaluate(() => { (window as any).__messages = []; (window as any).__renderer.send({ type: 'listen', types: ['pointerdown', 'pointerup', 'mousedown', 'mouseup'] }); });
      for (const type of ['mousePressed', 'mouseReleased'] as const) {
        await cdp.send('Input.dispatchMouseEvent', { type, x: p.widget.x, y: p.widget.y, button: 'left', clickCount: 1, pointerType: 'pen' });
      }
      await page.waitForTimeout(100);
      const pen = await sent(page, ['pointerdown', 'mousedown', 'pointerup', 'mouseup']);
      assert.ok(pen.includes('pointerdown:untrusted') && pen.includes('pointerup:trusted'), pen.join());
      assert.ok(!pen.includes('mousedown:trusted'), pen.join());

      // a mouse pressed while a finger is held on the widget keeps its press: its mousedown is its own, not the finger's
      await page.evaluate(() => { (window as any).__messages = []; });
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: p.widget.x - 40, y: p.widget.y, id: 2 }] });
      await page.mouse.move(p.widget.x, p.widget.y);
      await page.mouse.down();
      await page.mouse.up();
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await page.waitForTimeout(100);
      // the finger's pointerdown, then the mouse's and its mousedown (the finger's own mousedown follows its release)
      const both = await sent(page, ['pointerdown', 'mousedown']);
      assert.deepEqual(both.slice(0, 3), ['pointerdown:untrusted', 'pointerdown:trusted', 'mousedown:trusted'], both.join());

      // a pen held on the widget, a mouse clicked meanwhile, then the pen's barrel button with the tip still down: a
      // chorded press (a pointermove and a mousedown, no pointerdown) is the pen's, no gesture before the pen is lifted
      await page.evaluate(() => { (window as any).__messages = []; });
      const penInput = (type: 'mousePressed' | 'mouseReleased', button: 'left' | 'right', buttons: number) =>
        cdp.send('Input.dispatchMouseEvent', { type, x: p.widget.x, y: p.widget.y, button, buttons, clickCount: 1, pointerType: 'pen' });
      await penInput('mousePressed', 'left', 1);
      await page.mouse.down();
      await page.mouse.up();
      await penInput('mousePressed', 'right', 3);
      await page.waitForTimeout(100);
      const chorded = await sent(page, ['pointerdown', 'mousedown']);
      // and so is the barrel button let go with the tip still down (a pointermove and a mouseup)
      await page.evaluate(() => { (window as any).__messages = []; });
      await penInput('mouseReleased', 'right', 1);
      await page.waitForTimeout(100);
      const barrelUp = await sent(page, ['mouseup']);
      await penInput('mouseReleased', 'left', 0);
      assert.equal(chorded[chorded.length - 1], 'mousedown:untrusted', chorded.join());
      assert.deepEqual(barrelUp, ['mouseup:untrusted']);

      // a mouse's press is one from its pointerdown
      await page.evaluate(() => { (window as any).__messages = []; });
      await page.mouse.move(p.widget.x, p.widget.y);
      await page.mouse.down();
      await page.mouse.up();
      assert.deepEqual(await sent(page, ['pointerdown', 'pointerup']), ['pointerdown:trusted', 'pointerup:trusted']);
    } finally {
      await browser.close();
    }
  });
});
