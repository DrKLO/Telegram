// Who gets a press (docs/sdk-v1.md §5.5, packages/core/src/shared/gesture-policy.ts): the dev app's bubble plays a
// phone client by default (a mouse arbitrates as a finger), so a hold on an `auto` element ends for the widget with
// pointercancel (the message menu) and a horizontal drag is swipe-to-reply, while `touch-action: none` gives the widget
// holds (`contextmenu`) and horizontal drags; a vertical drag is always the chat's (v0.1.3); a double tap is always the
// widget's (`dblclick`, on every element); the leading 20 px take no claim. `?gestures=desktop` plays a desktop
// client, `off` no bubble. Iframe and Shadow DOM renderer.
// Also the dev app's other agent tooling: the bubble-width control and the size row (zlib as the server counts it).

import { expect, test, type Locator, type Page } from '@playwright/test';

const WIDGET = `<style>body{margin:0}.t{height:40px;margin:0 0 6px 40px;background:#ddd;font:12px sans-serif}</style>
<div class="t" id="auto">auto</div>
<div class="t" id="none" style="touch-action:none">none</div>
<div class="t" id="pany" style="touch-action:pan-y">pan-y</div>
<div class="t" id="panx" style="touch-action:pan-x">pan-x</div>
<div class="t" id="edge" style="touch-action:none;margin-left:0">none from the edge</div>
<p id="log"></p><p id="cs"></p>
<script>
var log = [];
document.getElementById('cs').textContent = ['auto', 'none', 'pany', 'panx'].map(function (id) { return getComputedStyle(document.getElementById(id)).touchAction; }).join(',');
['pointerdown', 'pointerup', 'pointercancel', 'click', 'dblclick', 'contextmenu'].forEach(function (t) {
  document.addEventListener(t, function (e) { log.push(e.target.id + ':' + t); document.getElementById('log').textContent = log.join(' '); });
});
</script>`;

async function load(page: Page, shadow: boolean, gestures?: 'desktop' | 'off'): Promise<Locator> {
  const q = new URLSearchParams();
  if (shadow) q.set('shadow', '1');
  if (gestures) q.set('gestures', gestures);
  await page.goto('/?' + q.toString());
  await page.evaluate((h) => (window as any).__tw.load(h, null), WIDGET);
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  await page.waitForTimeout(100);
  return shadow ? page.locator('#sandbox-wrap .widget-shadow') : page.frameLocator('#sandbox-wrap iframe').locator(':root');
}

/** What the widget logged since `from` entries, once it has settled. */
async function events(root: Locator, from: number): Promise<string[]> {
  let last = '';
  for (let i = 0; i < 20; i++) {
    await root.page().waitForTimeout(100);
    const now = (await root.locator('#log').textContent()) ?? '';
    if (now === last && i > 2) break;
    last = now;
  }
  return last ? last.split(' ').slice(from) : [];
}
const count = async (root: Locator) => ((await root.locator('#log').textContent()) || '').split(' ').filter(Boolean).length;
const bubble = (page: Page) => page.evaluate(() => (window as any).__tw.state.bubbleGestures.map((g: any) => g.action + (g.edge ? '@edge' : '')));
const claims = (page: Page) => page.evaluate(() => (window as any).__tw.state.gestures);

async function at(root: Locator, id: string, dx = 0.5): Promise<[number, number]> {
  const b = (await root.locator('#' + id).boundingBox())!;
  return [b.x + (dx < 1 ? b.width * dx : dx), b.y + b.height / 2];
}
async function hold(page: Page, [x, y]: [number, number], ms = 700) {
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.waitForTimeout(ms);
  await page.mouse.up();
}
async function drag(page: Page, [x, y]: [number, number], dx: number, dy: number) {
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx, y + dy, { steps: 8 });
  await page.mouse.up();
}

for (const shadow of [false, true]) {
  const r = shadow ? 'shadow' : 'iframe';

  test(`${r}: the claim of each press comes from touch-action, the hold only from none`, async ({ page }) => {
    const root = await load(page, shadow);
    for (const id of ['auto', 'none', 'pany']) await root.locator('#' + id).click();
    await expect.poll(async () => (await claims(page)).length).toBe(3);
    expect(await claims(page)).toEqual([
      { x: false, y: false, hold: false },
      { x: true, y: false, hold: true },
      { x: true, y: false, hold: false },
    ]);
  });

  test(`${r}: a vertical drag is the chat's on none and pan-x too; a drag that starts sideways on none is the widget's on both axes`, async ({ page }) => {
    const root = await load(page, shadow);
    await drag(page, await at(root, 'none', 20), 2, 30);
    expect(await events(root, 0)).toEqual(['none:pointerdown', 'none:pointercancel']);
    await drag(page, await at(root, 'panx', 20), 2, 30);
    expect(await events(root, 2)).toEqual(['panx:pointerdown', 'panx:pointercancel']);
    // pan-x claims nothing: a horizontal drag there is swipe-to-reply.
    await drag(page, await at(root, 'panx', 20), 120, 4);
    expect(await events(root, 4)).toEqual(['panx:pointerdown', 'panx:pointercancel']);
    // The axis locks at the first move past the slop: sideways first, then down, stays the widget's.
    const [x, y] = await at(root, 'none', 20);
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x + 30, y, { steps: 4 });
    await page.mouse.move(x + 40, y + 16, { steps: 6 });
    await page.mouse.up();
    expect(await events(root, 6)).toEqual(['none:pointerdown', 'none:pointerup']);
    expect(await bubble(page)).toEqual(['scroll', 'scroll', 'swipe']);
    expect(await claims(page)).toContainEqual({ x: false, y: false, hold: false });
  });

  test(`${r}: the engine always allows vertical panning (none → pan-y), the widget and the claim see its own touch-action`, async ({ page }) => {
    const root = await load(page, shadow);
    const engine = await root.locator('#none').evaluate((el) => {
      const r = el.getRootNode() as Document | ShadowRoot;
      return ['auto', 'none', 'pany', 'panx'].map((id) => getComputedStyle(r.getElementById(id)!).touchAction).join(',');
    });
    expect(engine).toBe('auto,pan-y,pan-y,pan-x pan-y');
    await expect(root.locator('#cs')).toHaveText('auto,none,pan-y,pan-x');
  });

  test(`${r}: a hold on auto is the message menu: the widget gets pointercancel, no pointerup, no click`, async ({ page }) => {
    const root = await load(page, shadow);
    await hold(page, await at(root, 'auto'));
    expect(await events(root, 0)).toEqual(['auto:pointerdown', 'auto:pointercancel']);
    expect(await bubble(page)).toEqual(['menu']);
    // A tap still is the widget's.
    await root.locator('#auto').click();
    expect(await events(root, 2)).toEqual(['auto:pointerdown', 'auto:pointerup', 'auto:click']);
  });

  test(`${r}: a hold on touch-action: none is the widget's, with its own contextmenu`, async ({ page }) => {
    const root = await load(page, shadow);
    await hold(page, await at(root, 'none'));
    const ev = await events(root, 0);
    // A finger makes no click after a hold: the phone mode drops the mouse's.
    expect(ev).toEqual(['none:pointerdown', 'none:contextmenu', 'none:pointerup']);
    expect(await bubble(page)).toEqual([]);
  });

  test(`${r}: a double tap on auto is the widget's too: both clicks and one dblclick, no reaction`, async ({ page }) => {
    const root = await load(page, shadow);
    const [x, y] = await at(root, 'auto');
    await page.mouse.dblclick(x, y);
    expect(await events(root, 0)).toEqual(['auto:pointerdown', 'auto:pointerup', 'auto:click', 'auto:pointerdown', 'auto:pointerup', 'auto:click', 'auto:dblclick']);
    expect(await bubble(page)).toEqual([]);
  });

  test(`${r}: a double tap on touch-action: none gets both clicks and one dblclick`, async ({ page }) => {
    const root = await load(page, shadow);
    const [x, y] = await at(root, 'none');
    await page.mouse.dblclick(x, y);
    expect(await events(root, 0)).toEqual(['none:pointerdown', 'none:pointerup', 'none:click', 'none:pointerdown', 'none:pointerup', 'none:click', 'none:dblclick']);
    expect(await bubble(page)).toEqual([]);
  });

  test(`${r}: a horizontal drag on auto is swipe-to-reply, on pan-y the widget's; a vertical one on pan-y scrolls`, async ({ page }) => {
    const root = await load(page, shadow);
    await drag(page, await at(root, 'auto', 20), 120, 4);
    expect(await events(root, 0)).toEqual(['auto:pointerdown', 'auto:pointercancel']);
    await drag(page, await at(root, 'pany', 20), 120, 4);
    expect(await events(root, 2)).toEqual(['pany:pointerdown', 'pany:pointerup']);
    await drag(page, await at(root, 'pany', 20), 2, 30);
    expect(await events(root, 4)).toEqual(['pany:pointerdown', 'pany:pointercancel']);
    expect(await bubble(page)).toEqual(['swipe', 'scroll']);
  });

  test(`${r}: the leading 20 px take no claim: a drag there on none is the back gesture`, async ({ page }) => {
    const root = await load(page, shadow);
    await drag(page, await at(root, 'edge', 6), 120, 0);
    expect(await events(root, 0)).toEqual(['edge:pointerdown', 'edge:pointercancel']);
    expect(await bubble(page)).toEqual(['back@edge']);
    await drag(page, await at(root, 'edge', 30), 120, 0);
    expect(await events(root, 2)).toEqual(['edge:pointerdown', 'edge:pointerup']);
  });

  test(`${r}: desktop: a right click is the menu on auto and contextmenu on none; holds, drags and double clicks are the widget's`, async ({ page }) => {
    const root = await load(page, shadow, 'desktop');
    const [ax, ay] = await at(root, 'auto');
    await page.mouse.click(ax, ay, { button: 'right' });
    expect(await events(root, 0)).toEqual(['auto:pointerdown', 'auto:pointercancel']);
    const [nx, ny] = await at(root, 'none');
    await page.mouse.click(nx, ny, { button: 'right' });
    expect(await events(root, 2)).toEqual(['none:pointerdown', 'none:contextmenu', 'none:pointerup']);
    await hold(page, [ax, ay]);
    expect(await events(root, 5)).toEqual(['auto:pointerdown', 'auto:pointerup', 'auto:click']);
    await drag(page, await at(root, 'auto', 20), 120, 4);
    expect(await events(root, 8)).toEqual(['auto:pointerdown', 'auto:pointerup', 'auto:click']);
    await page.waitForTimeout(350);
    await page.mouse.dblclick(ax, ay);
    expect(await events(root, 11)).toEqual(['auto:pointerdown', 'auto:pointerup', 'auto:click', 'auto:pointerdown', 'auto:pointerup', 'auto:click', 'auto:dblclick']);
    expect(await bubble(page)).toEqual(['menu']);
  });

  test(`${r}: off: no bubble, the widget gets every press`, async ({ page }) => {
    const root = await load(page, shadow, 'off');
    await hold(page, await at(root, 'auto'));
    expect(await events(root, 0)).toEqual(['auto:pointerdown', 'auto:pointerup', 'auto:click']);
    expect(await bubble(page)).toEqual([]);
  });
}

for (const shadow of [false, true]) {
  test(`${shadow ? 'shadow' : 'iframe'}: touch-action on a container covers its children, as in CSS`, async ({ page }) => {
    await page.goto(shadow ? '/?shadow=1' : '/');
    await page.evaluate(() => (window as any).__tw.load(`<style>body{margin:0}#pad{touch-action:none;padding:10px 10px 10px 40px}#t{height:60px;background:#ddd}</style><div id="pad"><div id="t"></div></div><p id="log"></p><script>
      var log = [];
      ['pointerdown', 'pointerup', 'pointercancel', 'contextmenu'].forEach(function (t) { document.getElementById('t').addEventListener(t, function () { log.push(t); document.getElementById('log').textContent = log.join(' '); }); });
      </script>`, null));
    await page.waitForFunction(() => (window as any).__tw.state.loaded);
    await page.waitForTimeout(200);
    const root = shadow ? page.locator('#sandbox-wrap .widget-shadow') : page.frameLocator('#sandbox-wrap iframe').locator(':root');
    // #t says nothing itself: its container's touch-action: none claims the hold.
    await root.locator('#t').click();
    await expect.poll(async () => (await claims(page)).length).toBe(1);
    expect(await claims(page)).toEqual([{ x: true, y: false, hold: true }]);
    await page.waitForTimeout(400);
    await hold(page, await at(root, 't'));
    await expect(root.locator('#log')).toHaveText('pointerdown pointerup pointerdown contextmenu pointerup');
  });
}

for (const [mode, type] of [['phone', 'touch'], ['desktop', 'mouse']] as const) {
  test(`${mode}: the widget sees the press as pointerType ${type}, a hover move stays the mouse's`, async ({ page }) => {
    await page.goto(mode === 'phone' ? '/' : '/?gestures=desktop');
    await page.evaluate(() => (window as any).__tw.load(`<div id="t" style="height:60px;margin-left:40px;background:#ddd"></div><p id="log"></p><script>
      var log = [];
      ['pointerdown', 'pointermove', 'pointerup', 'click'].forEach(function (t) { document.addEventListener(t, function (e) { if (t !== 'pointermove' || !log.length || log[log.length - 1].indexOf('pointermove') < 0) log.push(t + ':' + e.pointerType); document.getElementById('log').textContent = log.join(' '); }); });
      </script>`, null));
    await page.waitForFunction(() => (window as any).__tw.state.loaded);
    const root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
    const b = (await root.locator('#t').boundingBox())!;
    await page.mouse.move(b.x + 100, b.y + 30, { steps: 3 });
    await page.waitForTimeout(150);
    await page.mouse.click(b.x + 100, b.y + 30);
    await expect(root.locator('#log')).toHaveText(`pointermove:mouse pointerdown:${type} pointerup:${type} click:${type}`);
  });
}

test('the width control lays the bubble out at a preset or a free width', async ({ page }) => {
  await page.goto('/?width=360');
  await page.evaluate(() => (window as any).__tw.load('<div id="w"></div><script>function w() { document.getElementById("w").textContent = innerWidth; } w(); addEventListener("resize", w);</script>', null));
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const frame = page.frameLocator('#sandbox-wrap iframe');
  await expect(frame.locator('#w')).toHaveText('360');
  await page.evaluate(() => (window as any).__tw.setWidth(200));
  await expect(frame.locator('#w')).toHaveText('200');
  expect(new URL(page.url()).searchParams.get('width')).toBe('200');
  await page.selectOption('#width', 'free');
  expect(new URL(page.url()).searchParams.get('width')).toBe('free');
  await expect(page.locator('#chat-sandbox')).toHaveCSS('resize', 'horizontal');
});

test('the stats panel shows the size as the server counts it, with a warning and an error past the limits', async ({ page }) => {
  await page.goto('/');
  const load = async (html: string) => {
    await page.evaluate((h) => (window as any).__tw.load(h, null), html);
    await page.waitForFunction(() => (window as any).__tw.state.size);
    return page.evaluate(() => (window as any).__tw.state.size as { zlib: number; raw: number });
  };
  // Incompressible text: a deterministic pseudo-random string of n characters (xorshift32; 5150 → 3888 B, 6000 → 4525 B).
  const noise = (n: number) => { let x = 2463534242, t = ''; for (let i = 0; i < n; i++) { x ^= x << 13; x >>>= 0; x ^= x >>> 17; x ^= x << 5; x >>>= 0; t += 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'[(x >>> 8) % 62]; } return t; };
  const small = await load('<p>hello</p>');
  expect(small.raw).toBe(12);
  expect(small.zlib).toBeGreaterThan(10);
  await expect(page.locator('#stats tr.size-ok')).toContainText('B zlib / 0.0 KB raw (limit 4096 / 32768)');
  const near = await load('<!--' + noise(5150) + '-->');
  expect(near.zlib).toBeGreaterThan(3800);
  expect(near.zlib).toBeLessThanOrEqual(4096);
  await expect(page.locator('#stats tr.size-warning')).toContainText('aim for 3800 B or less');
  await load('<!--' + noise(6000) + '-->');
  await expect(page.locator('#stats tr.size-error')).toContainText('INPUT_MEDIA_WIDGET_TOO_LARGE');
});
