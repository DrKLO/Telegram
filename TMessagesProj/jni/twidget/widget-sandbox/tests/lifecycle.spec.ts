// Off-screen pause (docs/decisions.md, Runtime limits): a hidden widget gets no
// animation frames and its timers run at 1 Hz; back on screen, animation
// resumes. Driven through `__tw.setVisible`, which overrides the dev UI's
// IntersectionObserver.

import { expect, test, type Page } from '@playwright/test';

async function open(page: Page, file: string) {
  await page.goto(`/?file=compat/${file}`);
  await page.waitForFunction(() => { const s = (window as any).__tw?.state; return s && (s.loaded || s.dead); }, null, { timeout: 60_000 });
  return page.frameLocator('#sandbox-wrap iframe');
}
const tasks = (page: Page) => page.evaluate(() => (window as any).__tw.state.stats?.tasks as number);
const setVisible = (page: Page, v: boolean | null) => page.evaluate((v) => (window as any).__tw.setVisible(v), v);

test('hidden: animation frames stop; visible again: they resume', async ({ page }) => {
  const f = await open(page, 'raf-animation.html');
  await page.waitForTimeout(500);
  await setVisible(page, false);
  await page.waitForTimeout(300); // let an in-flight frame land
  const framesHidden = Number(await f.locator('#frames').textContent());
  const tasksHidden = await tasks(page);
  expect(framesHidden).toBeGreaterThan(5);
  await page.waitForTimeout(1200);
  expect(Number(await f.locator('#frames').textContent())).toBe(framesHidden);
  // Only the visibility task itself (and at most one straggler) may have run.
  expect((await tasks(page)) - tasksHidden).toBeLessThanOrEqual(2);
  const posHidden = await f.locator('#b1').evaluate((e) => (e as HTMLElement).style.transform);
  await setVisible(page, true);
  await page.waitForTimeout(600);
  expect(Number(await f.locator('#frames').textContent())).toBeGreaterThan(framesHidden + 10);
  expect(await f.locator('#b1').evaluate((e) => (e as HTMLElement).style.transform)).not.toBe(posHidden);
  await expect(page.locator('#status')).toHaveText('● loaded');
});

test('hidden: a 1 s interval ticks at most once per second; visible: no ticks are lost', async ({ page }) => {
  const f = await open(page, 'clock.html');
  await page.waitForTimeout(200);
  await setVisible(page, false);
  const t0 = Number((await f.locator('#ticks').textContent())!.split(' ')[0]);
  await page.waitForTimeout(2600);
  const t1 = Number((await f.locator('#ticks').textContent())!.split(' ')[0]);
  expect(t1 - t0).toBeGreaterThanOrEqual(1);
  expect(t1 - t0).toBeLessThanOrEqual(3);
  await setVisible(page, true);
  await page.waitForTimeout(2300);
  const t2 = Number((await f.locator('#ticks').textContent())!.split(' ')[0]);
  expect(t2 - t1).toBeGreaterThanOrEqual(2);
});

test('the widget sees document.hidden and visibilitychange, like a background tab', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => (window as any).__tw.load(`<p id="s">?</p><p id="n">0</p><p id="a"></p><script>var n = 0, a = ''; function show() { document.getElementById('s').textContent = document.visibilityState + '/' + document.hidden; document.getElementById('n').textContent = ++n; } document.addEventListener('visibilitychange', show); show();
    TelegramWidget.onEvent('activated', function () { a += '+' + TelegramWidget.isActive; document.getElementById('a').textContent = a; });
    TelegramWidget.onEvent('deactivated', function () { a += '-' + TelegramWidget.isActive; document.getElementById('a').textContent = a; });</script>`, null));
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const f = page.frameLocator('#sandbox-wrap iframe');
  await expect(f.locator('#s')).toHaveText('visible/false');
  await setVisible(page, false);
  await expect(f.locator('#s')).toHaveText('hidden/true');
  await expect(f.locator('#n')).toHaveText('2');
  await setVisible(page, true);
  await expect(f.locator('#s')).toHaveText('visible/false');
  await expect(f.locator('#n')).toHaveText('3');
  await setVisible(page, false);
  await setVisible(page, true);
  await expect(f.locator('#n')).toHaveText('5');
  // SDK v1: isActive and activated / deactivated mirror the same state.
  await expect(f.locator('#a')).toHaveText('-false+true-false+true');
});

test('hidden: the timers still run in the order of their delays (setTimeout 20, 10, 0 → 0, 10, 20)', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => (window as any).__tw.load(`<p id="o"></p><script>var o = []; document.addEventListener('visibilitychange', function () { if (!document.hidden) return; [20, 10, 0].forEach(function (d) { setTimeout(function () { o.push(d); document.getElementById('o').textContent = o.join(); }, d); }); });</script>`, null));
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  await setVisible(page, false);
  await page.waitForTimeout(1600);
  await setVisible(page, true);
  await expect(page.frameLocator('#sandbox-wrap iframe').locator('#o')).toHaveText('0,10,20');
});

// ───────── the triggers: the dev UI's IntersectionObserver and the page's own visibility ─────────

const frames = async (page: Page) => Number(await page.frameLocator('#sandbox-wrap iframe').locator('#frames').textContent());

test('scrolling the bubble out of view pauses the widget; scrolling back resumes it', async ({ page }) => {
  await open(page, 'raf-animation.html');
  await page.waitForTimeout(400);
  expect(await frames(page)).toBeGreaterThan(5);
  // Make the chat column tall and scroll the stage so the iframe leaves the viewport.
  await page.evaluate(() => {
    const spacer = document.createElement('div');
    spacer.id = 'spacer';
    spacer.style.height = '3000px';
    document.querySelector('#chat-sandbox')!.appendChild(spacer);
    document.querySelector('.stage')!.scrollTop = 2500;
  });
  await page.waitForTimeout(400); // observer callback + an in-flight frame
  const atHidden = await frames(page);
  await page.waitForTimeout(1000);
  expect(await frames(page)).toBe(atHidden);
  await page.evaluate(() => { document.querySelector('.stage')!.scrollTop = 0; });
  await page.waitForTimeout(600);
  expect(await frames(page)).toBeGreaterThan(atHidden + 10);
});

test('the page going hidden pauses the widget even while the bubble is in view', async ({ page }) => {
  await open(page, 'raf-animation.html');
  await page.waitForTimeout(400);
  // Playwright cannot background the tab; emulate what the browser does: document.hidden flips and visibilitychange fires.
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await page.waitForTimeout(400);
  const atHidden = await frames(page);
  await page.waitForTimeout(1000);
  expect(await frames(page)).toBe(atHidden);
  await page.evaluate(() => {
    delete (document as any).hidden;
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await page.waitForTimeout(600);
  expect(await frames(page)).toBeGreaterThan(atHidden + 10);
});
