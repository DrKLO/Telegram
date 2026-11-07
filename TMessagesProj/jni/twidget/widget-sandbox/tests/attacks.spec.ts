// Attack suite: every file in corpus/attacks/ is loaded in SANDBOX mode (never
// reference mode) and must cause (a) zero hits on the canary server and (b) no
// browser request from any frame to anything but the app's own origin.
// TW_RENDERER=shadow runs the same suite on the Shadow DOM renderer (no iframe,
// docs/shadow-dom-renderer.md): the widget's DOM is then in the app page itself,
// so (a) and (b) measure exactly what the frame boundary used to add.

import { expect, test, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { APP_ORIGIN, CANARY } from '../playwright.config';

const attacksDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'corpus', 'attacks');
const files = fs.readdirSync(attacksDir).filter((f) => f.endsWith('.html')).sort();

const ALLOWED = [APP_ORIGIN + '/', APP_ORIGIN.replace(/^http/, 'ws') + '/', 'data:', 'blob:', 'about:'];
const SHADOW = process.env.TW_RENDERER === 'shadow';
const RENDERER_PARAM = SHADOW ? '&shadow=1' : '';
/** The renderer's element in the bubble. */
const RENDERER_SEL = SHADOW ? '#sandbox-wrap .widget-shadow' : '#sandbox-wrap iframe';
/** Locator for the widget's DOM: the frame, or the shadow host (Playwright pierces open shadow roots). */
const widgetDom = (page: Page) => (SHADOW ? page.locator(RENDERER_SEL) : page.frameLocator(RENDERER_SEL));
const isAllowed = (url: string) => ALLOWED.some((p) => url.startsWith(p));

async function canaryHits(): Promise<unknown[]> {
  return (await (await fetch(CANARY + '/__hits')).json()) as unknown[];
}
async function resetCanary(): Promise<void> {
  await fetch(CANARY + '/__reset');
}

/** Record every request / websocket the page (any frame) attempts. */
function watchRequests(page: Page): string[] {
  const seen: string[] = [];
  page.on('request', (r) => { if (!isAllowed(r.url())) seen.push(`${r.method()} ${r.url()} [${r.resourceType()}]`); });
  page.on('websocket', (ws) => { if (!isAllowed(ws.url())) seen.push(`WS ${ws.url()}`); });
  page.on('requestfailed', (r) => { if (!isAllowed(r.url())) seen.push(`FAILED ${r.url()}`); });
  return seen;
}

test('control: the canary records hits from the app page', async ({ page }) => {
  await resetCanary();
  // Not reference mode and not a widget: trusted page code on the app origin
  // (loopback → loopback, like a leaking widget would be) proving the harness
  // can see leaks. An about:blank page would be in Chrome's "public" address
  // space and get Private Network Access treatment instead.
  await page.goto('/');
  await page.evaluate((canary) => {
    const img = document.createElement('img');
    img.src = `${canary}/hit/control-img`;
    document.body.appendChild(img);
    fetch(`${canary}/hit/control-fetch`, { mode: 'no-cors' }).catch(() => {});
  }, CANARY);
  await expect.poll(async () => (await canaryHits()).filter((h: any) => h.kind === 'http' && /control-(img|fetch)/.test(h.url)), { timeout: 5000 }).not.toHaveLength(0);
  await resetCanary();
});

// ───────────── runtime limits (docs/decisions.md) ─────────────

const twState = (page: Page) => page.evaluate(() => {
  const s = (window as any).__tw.state;
  return { dead: s.dead as string | null, loaded: s.loaded as boolean, diags: s.diags as Record<string, { count: number; details: string[] }>, stats: s.stats, logs: s.logs as { level: string; text: string }[], actions: s.actions as { kind: string }[] };
});
async function openAttack(page: Page, file: string, params = '') {
  await page.goto(`/?file=attacks/${file}${params}${RENDERER_PARAM}`);
  await page.waitForFunction(() => (window as any).__tw?.state && ((window as any).__tw.state.loaded || (window as any).__tw.state.dead), null, { timeout: 60_000 });
  return widgetDom(page);
}
const waitDead = (page: Page) => page.waitForFunction(() => (window as any).__tw.state.dead, null, { timeout: 15_000 });

test.describe('last render as fallback', () => {
  test('a widget killed after a click stays on screen, frozen, and ignores further events', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (e) => pageErrors.push(String(e)));
    const f = await openAttack(page, 'fallback-after-kill.html');
    await f.locator('#go').click();
    await waitDead(page);
    expect((await twState(page)).dead).toMatch(/CPU limit/);
    // The last consistent render is still there; the click handler's own mutation never flushed.
    await expect(f.locator('#text')).toHaveText('Delivered to the front desk at 14:32.');
    await expect(f.locator('#clicks')).toHaveText('0');
    await expect(f.locator('html')).toHaveAttribute('data-tw-frozen', 'fatal');
    await expect(f.locator('[data-tw-badge]')).toHaveText('Widget stopped');
    await expect(page.locator('#widget-error')).toContainText('Widget stopped');
    await expect(page.locator(RENDERER_SEL)).toHaveCount(1);
    // Events are no longer forwarded (pointer-events: none on <html>; forwarding gated in the renderer too).
    await f.locator('#go').click({ force: true });
    await page.waitForTimeout(300);
    await expect(f.locator('#clicks')).toHaveText('0');
    expect(pageErrors).toEqual([]);
  });

  test('a task killed in the middle of a batch leaves none of its mutations in the renderer', async ({ page }) => {
    const f = await openAttack(page, 'fallback-mid-batch.html');
    await f.locator('#go').click();
    await waitDead(page);
    await expect(f.locator('#status')).toHaveText('ready');
    await expect(f.locator('#list li')).toHaveCount(0);
    await expect(f.locator('html')).toHaveAttribute('data-tw-frozen', 'fatal');
  });

  test('a script that hangs at boot leaves the static markup visible', async ({ page }) => {
    const f = await openAttack(page, 'fallback-at-boot.html');
    await waitDead(page);
    await expect(f.locator('#static')).toHaveText('Weather in Lisbon');
    await expect(f.locator('#temp')).toBeVisible();
    // Unlike a browser, the whole document is parsed before the first script runs, so later markup is there too.
    await expect(f.locator('#after')).toBeVisible();
    await expect(f.locator('[data-tw-badge]')).toBeVisible();
  });
});

test.describe('timer limit', () => {
  test('a 100 000-timer flood is refused past maxTimers with a RangeError; the widget stays alive and bounded', async ({ page }) => {
    const f = await openAttack(page, 'timer-flood.html');
    await expect(f.locator('#status')).toHaveText('done: RangeError: too many timers (max 64)');
    await expect(f.locator('#count')).toHaveText('64 accepted, 99936 refused');
    await page.waitForTimeout(1500);
    const s = await twState(page);
    expect(s.dead).toBeNull();
    expect(s.diags['timer-limit']?.count).toBe(1);
    expect(s.diags['timer-limit-enforced']).toBeUndefined();
    expect(s.stats.memoryBytes).toBeLessThan(32 * 1024 * 1024);
    await f.locator('#btn').click();
    await expect(f.locator('#clicks')).toHaveText('1');
  });

  test('a timer id the shim asks for again replaces its timer: 1000 forged calls on one id fire its callback once', async ({ page }) => {
    const f = await openAttack(page, 'timer-id-reuse.html');
    await expect(f.locator('#p')).toHaveText('fired 1', { timeout: 10_000 });
    const s = await twState(page);
    expect(s.dead).toBeNull();
    expect(s.diags['timer-limit-enforced']).toBeUndefined();
  });
});

// The paint limit (docs/decisions.md, "Heavy boxes"): the renderer counts the widget's heavy boxes before each frame and
// stops it past 16 (2 with backdrop-filter). Each corpus/attacks/paint-* is stopped with its count, its last frame drawn
// without the effects (no blurred shadow, filter, backdrop-filter or transparency on any of its elements) under the
// badge, and the page keeps its frame rate.
test.describe('paint limit', () => {
  const PAINT = (n: number) => new RegExp(`^Paint limit exceeded: ${n} boxes with shadows, filters or transparency \\(max 16\\)$`);
  const cases: [string, RegExp, string][] = [
    ['paint-shadow-layers.html', PAINT(120), 'paint-limit'],
    ['paint-nested-filters.html', PAINT(60), 'paint-limit'],
    ['paint-heavy-17.html', PAINT(17), 'paint-limit'],
    ['paint-animation-17.html', PAINT(17), 'paint-limit'],
    ['paint-ars.html', PAINT(250), 'paint-limit'],
    ['paint-backdrop-3.html', /^Paint limit exceeded: 3 boxes with backdrop-filter \(max 2\)$/, 'backdrop-limit'],
  ];
  /** The widget's elements that still draw an effect: a blurred shadow layer, a filter, a backdrop-filter, opacity below 1. */
  const effectsLeft = (page: Page) => widgetDom(page).locator('[data-tw-frozen]').evaluate((root) => [root, ...Array.from(root.querySelectorAll('*'))]
    .filter((el) => !el.hasAttribute('data-tw-badge'))
    .filter((el) => {
      const cs = getComputedStyle(el);
      const blurred = cs.boxShadow !== 'none' && cs.boxShadow.replace(/\([^)]*\)/g, '').split(',').some((l) => {
        const n = l.trim().split(/\s+/).filter((t) => /^-?[\d.]+px$/.test(t));
        return n.length >= 3 && parseFloat(n[2]) > 0;
      });
      return blurred || cs.filter !== 'none' || (cs.backdropFilter || 'none') !== 'none' || parseFloat(cs.opacity) < 1;
    }).length);
  const pageFps = (page: Page) => page.evaluate(async () => {
    let n = 0;
    const t0 = performance.now();
    while (performance.now() - t0 < 1000) { await new Promise((r) => requestAnimationFrame(r)); n++; }
    return n;
  });

  for (const [file, reason, code] of cases) {
    test(`${file}: stopped by the paint limit, its last frame without the effects, the page at full rate`, async ({ page }) => {
      const f = await openAttack(page, file);
      await waitDead(page);
      const s = await twState(page);
      expect(s.dead).toMatch(reason);
      expect(s.diags[code]?.details).toEqual([s.dead]);
      await expect(f.locator('[data-tw-badge]')).toHaveText('Widget stopped');
      expect(await effectsLeft(page)).toBe(0);
      expect(await pageFps(page)).toBeGreaterThan(40);
    });
  }

  test('compat counts nothing: 17 heavy boxes draw with their effects', async ({ page }) => {
    const f = await openAttack(page, 'paint-heavy-17.html', '&policy=compat');
    await expect(f.locator('#p')).toHaveText('drawn');
    await page.waitForTimeout(500);
    const s = await twState(page);
    expect(s.dead).toBeNull();
    expect(s.diags['paint-limit']).toBeUndefined();
    const shadows = await f.locator('.b').evaluateAll((els) => els.filter((el) => getComputedStyle(el).boxShadow !== 'none').length);
    expect(shadows).toBe(17);
  });
});

test.describe('bitmaps', () => {
  const claimed = fs.readFileSync(path.join(attacksDir, 'mem-huge-bitmaps.html'), 'utf8').match(/id="claims" alt="" src="data:image\/png;base64,([^"]+)"/)![1];
  test('strict: canvases past 2048² pixels together stop the widget before they reach the page', async ({ page }) => {
    await openAttack(page, 'mem-huge-bitmaps.html');
    await waitDead(page);
    expect((await twState(page)).dead).toMatch(/^Canvas limit exceeded: canvases of \d+ pixels together \(max 4194304\)$/);
    const sizes = await widgetDom(page).locator('canvas').evaluateAll((cs) => cs.map((c) => (c as HTMLCanvasElement).width * (c as HTMLCanvasElement).height));
    expect(sizes.reduce((a, b) => a + b, 0)).toBeLessThanOrEqual(2048 * 2048);
  });

  test('compat: the renderer itself never applies a canvas size past 4096 × 4096, and images past 1024² are the placeholder', async ({ page }) => {
    const f = await openAttack(page, 'mem-huge-bitmaps.html', '&policy=compat');
    await expect(f.locator('#log')).toHaveText('drawn');
    const image = (id: string) => f.locator(id).evaluate(async (el: HTMLImageElement) => {
      await el.decode().catch(() => {});
      return [el.naturalWidth, el.naturalHeight];
    });
    const canvas = (id: string) => f.locator(id).evaluate((el: HTMLCanvasElement) => [el.width, el.height]);
    const pixels = ([w, h]: number[]) => w * h;
    await expect.poll(() => image('#small')).toEqual([40, 20]);
    for (const id of ['#claims', '#past']) expect(pixels(await image(id)), id).toBeLessThanOrEqual(1024 * 1024);
    for (const id of ['#attr', '#scripted']) expect(pixels(await canvas(id)), id).toBeLessThanOrEqual(4096 * 4096);
    expect(await canvas('#plain')).toEqual([300, 150]);
    expect(await f.locator('#css').evaluate((el) => getComputedStyle(el).backgroundImage)).not.toContain(claimed.slice(0, 40));
    const s = await twState(page);
    expect(s.dead).toBeNull();
    expect(s.diags['renderer-violation']?.count).toBeGreaterThanOrEqual(1);
  });
});

test.describe('pointers', () => {
  test('a widget that asks for the mouse every frame never takes a press on the page; its own press is its own', async ({ page }) => {
    const f = await openAttack(page, 'activation-pointer-capture.html');
    await page.evaluate(() => {
      const b = document.createElement('button');
      b.textContent = 'app';
      b.style.cssText = 'position:fixed;left:0;top:0;width:120px;height:40px;z-index:99999';
      (window as any).__appClicks = 0;
      b.addEventListener('click', () => (window as any).__appClicks++);
      document.body.appendChild(b);
    });
    await page.waitForTimeout(300);
    await page.mouse.move(60, 20);
    await page.mouse.down();
    await page.waitForTimeout(200);
    await page.mouse.up();
    await page.waitForTimeout(500);
    expect(await page.evaluate(() => (window as any).__appClicks)).toBe(1);
    expect(await page.evaluate(() => (window as any).__tw.state.copiedTexts)).toEqual([]);
    await f.locator('#t').click();
    // the dev app's phone mode plays a finger with the mouse: the widget sees pointerType touch
    await expect.poll(() => page.evaluate(() => (window as any).__tw.state.copiedTexts)).toEqual([expect.stringMatching(/^captured-(mouse|touch)$/)]);
  });
});

test.describe("a finger's press", () => {
  // Real touch input (CDP touch events): the dev app's phone mode and Playwright's clicks play a finger with the mouse,
  // whose pointerdown is a gesture.
  test.use({ hasTouch: true });
  const touches = async (page: Page) => {
    const cdp = await page.context().newCDPSession(page);
    const box = (await page.locator(RENDERER_SEL).boundingBox())!;
    const x = box.x + box.width / 2, y = box.y + 60;
    const touch = (type: 'touchStart' | 'touchMove' | 'touchEnd', points: { x: number; y: number }[]) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: points.map((p) => ({ ...p, id: 1 })) });
    return {
      async swipe(dy: number) {
        await touch('touchStart', [{ x, y }]);
        for (let i = 1; i <= 10; i++) { await touch('touchMove', [{ x, y: y + (dy * i) / 10 }]); await page.waitForTimeout(16); }
        await touch('touchEnd', []);
      },
      async tap() {
        await touch('touchStart', [{ x, y }]);
        await page.waitForTimeout(60);
        await touch('touchEnd', []);
      },
    };
  };
  const seen = (page: Page) => page.evaluate(() => ({ requests: (window as any).__tw.bot.requests.map((r: any) => r.data as string), copied: (window as any).__tw.state.copiedTexts as string[] }));

  test('a swipe that starts on the widget is the chat\'s scroll: no request and no copyText from its pointerdown; a tap still gets its click', async ({ page }) => {
    await resetCanary();
    await openAttack(page, 'activation-touch-swipe.html', '&message=group');
    await page.waitForTimeout(300);
    const t = await touches(page);
    await t.swipe(160);
    await page.waitForTimeout(800);
    expect(await seen(page)).toEqual({ requests: [], copied: [] });
    await t.tap();
    await expect.poll(async () => (await seen(page)).copied).toEqual(['click']);
    await page.waitForTimeout(300);
    expect((await seen(page)).requests).toEqual([]);
    expect(await canaryHits()).toHaveLength(0);
  });
});

test.describe('light-dark()', () => {
  test('no branch of light-dark() fetches anything in the dark theme either', async ({ page }) => {
    await resetCanary();
    const outside = watchRequests(page);
    await page.goto(`/?file=attacks/css-light-dark-url.html&theme=dark${RENDERER_PARAM}`);
    await page.waitForFunction(() => (window as any).__tw?.state?.loaded, null, { timeout: 60_000 });
    await page.waitForTimeout(1500);
    expect(await canaryHits()).toHaveLength(0);
    expect(outside).toHaveLength(0);
    expect((await twState(page)).diags['url-blocked']?.count).toBeGreaterThan(0);
  });
});

test.describe('memory limit', () => {
  // The wasm build cannot count its heap (quickjs-ng has no malloc_usable_size under emscripten), so the
  // worker caps the wasm Memory at INITIAL + memoryLimitBytes; the engine then throws the same catchable
  // `InternalError: out of memory` as the native builds do at JS_SetMemoryLimit. tests/unit/memory-limit.test.ts
  // covers the harness; this is the real worker, with the kill, the frozen fallback and the stats.
  const FOOTPRINT_MB = 16 + 8;

  test('steady growth without a try/catch is killed with "Memory limit exceeded" and the last render stays frozen', async ({ page }) => {
    const f = await openAttack(page, 'mem-uncaught-growth.html');
    await waitDead(page);
    const s = await twState(page);
    expect(s.dead).toMatch(/^Memory limit exceeded \(8 MB\)/);
    // The render before the fatal tick is still there: a count in MB, below the cap.
    const reached = parseInt(await f.locator('#mb').textContent() ?? '', 10);
    expect(reached).toBeGreaterThanOrEqual(4);
    expect(reached).toBeLessThanOrEqual(FOOTPRINT_MB);
    await expect(f.locator('html')).toHaveAttribute('data-tw-frozen', 'fatal');
    await expect(f.locator('[data-tw-badge]')).toHaveText('Widget stopped');
  });

  // QuickJS from the TelegramMessenger/quickjs fork (quickjs-ng 0.17, as on native): out-of-memory is
  // uncatchable, so a widget that tries to catch it is stopped like one that does not; the last render stays.
  test('steady growth that tries to catch the error is still stopped at the cap, with its last render kept', async ({ page }) => {
    const f = await openAttack(page, 'mem-steady-growth.html');
    await page.waitForFunction(() => (window as any).__tw.state.dead, null, { timeout: 15_000 });
    const s = await twState(page);
    expect(s.dead).toMatch(/^Memory limit exceeded/);
    const reached = parseInt(await f.locator('#mb').textContent() ?? '', 10);
    expect(reached).toBeGreaterThanOrEqual(4);
    expect(reached).toBeLessThanOrEqual(FOOTPRINT_MB);
    await expect(f.locator('#mb')).not.toHaveText(/alloc refused/);
    await expect(f.locator('html')).toHaveAttribute('data-tw-frozen', 'fatal');
  });

  test('one 256 MB allocation is refused outright and stops the widget without growing it', async ({ page }) => {
    const f = await openAttack(page, 'mem-burst-alloc.html');
    await f.locator('#go').click();
    await page.waitForFunction(() => (window as any).__tw.state.dead, null, { timeout: 15_000 });
    const s = await twState(page);
    expect(s.dead).toMatch(/^Memory limit exceeded/);
    await expect(f.locator('#status')).not.toHaveText(/allocated/);
    expect(s.stats.memoryBytes).toBeLessThan(32 * 1024 * 1024);
  });

  test('the engine-agnostic probe is stopped at the cap during boot', async ({ page }) => {
    // mem-cap-probe.html allocates synchronously at boot: the runtime dies there, never past the cap.
    await openAttack(page, 'mem-cap-probe.html');
    const s = await twState(page);
    expect(s.dead).toMatch(/^Memory limit exceeded/);
  });
});

// ───────────── requests to the bot (docs/sdk-v1.md §2.14) ─────────────

test.describe('requests to the bot', () => {
  const bot = (page: Page) => page.evaluate(() => {
    const tw = (window as any).__tw;
    return { requests: tw.bot.requests.map((r: any) => r.data as string), pending: tw.bot.pendingRequests.length, copied: tw.state.copiedTexts as string[], actions: tw.state.actions as unknown[] };
  });
  const diagCount = async (page: Page, kind: string) => (await twState(page)).diags[kind]?.count ?? 0;

  test('no request and no copyText without a real tap; the tap sends exactly one', async ({ page }) => {
    await resetCanary();
    const f = await openAttack(page, 'request-no-activation.html', '&message=group');
    await page.waitForTimeout(800);
    expect((await bot(page)).requests).toEqual([]);
    expect((await bot(page)).copied).toEqual([]);
    expect(await diagCount(page, 'request-blocked')).toBeGreaterThanOrEqual(4);
    await f.locator('#go').click();
    await expect.poll(async () => (await bot(page)).requests).toEqual(['http://127.0.0.1:9999/hit/request-tap']);
    await page.waitForTimeout(500);
    const b = await bot(page);
    expect(b.requests).toHaveLength(1);
    expect(b.copied).toEqual([]); // the request took the tap
    await expect(f.locator('#log')).toContainText('tap:sent');
    expect(await canaryHits()).toHaveLength(0);
  });

  test('a widget that rewrites the wire gets nothing over the limits through: the glue re-checks every call', async ({ page }) => {
    const f = await openAttack(page, 'request-forged-wire.html', '&message=group');
    await f.locator('#go').click();
    // The oversized request, the one with a forged id (the shim forwards it: only the glue's gate says BUSY) and the copyText.
    await expect.poll(() => diagCount(page, 'bridge-rejected')).toBe(3);
    await page.waitForTimeout(300);
    const b = await bot(page);
    expect(b.requests).toEqual([]);
    expect(b.copied).toEqual([]);
    expect(b.actions).toEqual([]);
    expect(await canaryHits()).toHaveLength(0);
  });

  test('background requests: none where they are not allowed; where they are, one at a time and one per 5 s', async ({ page }) => {
    await openAttack(page, 'request-background-flood.html', '&message=group');
    await page.waitForTimeout(1500);
    expect((await bot(page)).requests).toEqual([]);
    await openAttack(page, 'request-background-flood.html');
    await page.waitForTimeout(6500);
    const b = await bot(page);
    expect(b.requests.length).toBeGreaterThanOrEqual(1);
    expect(b.requests.length).toBeLessThanOrEqual(2);
    expect((await twState(page)).dead).toBeNull();
    expect(await canaryHits()).toHaveLength(0);
  });

  test('background requests off screen: none go out while hidden; one per appearance, then one per 5 s', async ({ page }) => {
    await openAttack(page, 'request-background-flood.html', '&offscreen=1');
    await page.waitForTimeout(1500);
    expect((await bot(page)).requests).toEqual([]);
    const show = async (n: number) => {
      await page.evaluate(() => (window as any).__tw.setVisible(true));
      await expect.poll(async () => (await bot(page)).requests.length).toBe(n);
      await page.waitForTimeout(2500);
      expect((await bot(page)).requests).toHaveLength(n);
    };
    await show(1); // the request held since boot, at once
    for (const n of [2, 3]) {
      await page.evaluate(() => (window as any).__tw.setVisible(false));
      await page.waitForTimeout(1200); // the hidden flood's timers run at 1 Hz: one request is held, the rest BUSY
      expect((await bot(page)).requests).toHaveLength(n - 1);
      await show(n);
    }
    expect((await twState(page)).dead).toBeNull();
    expect(await canaryHits()).toHaveLength(0);
  });

  test('one request in flight: a second one in the same tap, from a timer or forged on the wire never reaches the client', async ({ page }) => {
    const f = await openAttack(page, 'request-in-flight.html', '&message=group&botmode=manual');
    await f.locator('#go').click();
    await expect.poll(async () => (await bot(page)).pending).toBe(1);
    await page.waitForTimeout(500);
    const b = await bot(page);
    expect(b.requests).toEqual(['first']);
    await expect(f.locator('#log')).toContainText('second:BUSY');
    await expect(f.locator('#log')).toContainText('timer:BUSY');
    expect(await diagCount(page, 'request-blocked')).toBeGreaterThanOrEqual(1); // the forged one: busy in the glue
  });

  test('an answer over the limits never reaches the widget: INVALID_ANSWER', async ({ page }) => {
    const f = await openAttack(page, 'request-in-flight.html', '&message=group');
    await page.evaluate(() => { (window as any).__tw.bot.request = () => Promise.resolve({ result: 'x'.repeat(1025) }); });
    await f.locator('#go').click();
    await expect(f.locator('#log')).toContainText('first:INVALID_ANSWER');
    expect(await diagCount(page, 'answer-rejected')).toBe(1);
  });

  test('a forwarded copy without fwd_allow_queries reads no chatData, chat or viewer and sends nothing, tap or not', async ({ page }) => {
    const f = await openAttack(page, 'request-copy.html', '&message=copy');
    await expect(f.locator('#seen')).toHaveText('true||||');
    await f.locator('#go').click();
    await page.waitForTimeout(800);
    expect((await bot(page)).requests).toEqual([]);
    await expect(f.locator('#log')).toContainText('tap:NOT_ALLOWED');
  });

  test('a live copy (fwd_allow_queries) reads no chatData or chat and asks only on a tap, never in the background', async ({ page }) => {
    const f = await openAttack(page, 'request-copy.html', '&message=live-copy');
    await expect(f.locator('#seen')).toHaveText(/^true\|\|\|\[object Object\]\|/);
    await page.waitForTimeout(800);
    expect((await bot(page)).requests).toEqual([]);
    await f.locator('#go').click();
    await expect.poll(async () => (await bot(page)).requests).toEqual(['copy-tap']);
    await page.waitForTimeout(800);
    expect((await bot(page)).requests).toEqual(['copy-tap']);
  });
});

// ───────────── the wallet (docs/sdk-v1.md §2.18) ─────────────

test.describe('the wallet', () => {
  const calls = (page: Page) => page.evaluate(() => (window as any).__tw.bot.requests.map((r: any) => (r.kind ? r.kind + ':' : '') + r.data) as string[]);
  const diagCount = async (page: Page, kind: string) => (await twState(page)).diags[kind]?.count ?? 0;

  test('no wallet window without a real tap, not even on a background request\'s answer; the tap opens one', async ({ page }) => {
    await resetCanary();
    const f = await openAttack(page, 'wallet-no-activation.html');
    await page.waitForTimeout(1000);
    expect(await calls(page)).toEqual(['bg']); // the background request itself, which grants nothing
    await expect(page.locator('.wallet-sheet')).toHaveCount(0);
    await expect(f.locator('#log')).toContainText('canary:TypeError');
    await expect(f.locator('#log')).toContainText('background-answer:NOT_ALLOWED');
    expect(await diagCount(page, 'request-blocked')).toBeGreaterThanOrEqual(5);
    await f.locator('#go').click();
    await expect(page.locator('.wallet-sheet')).toHaveCount(1);
    await page.locator('#wallet-decline').click();
    await expect(f.locator('#log')).toContainText('tap:REJECTED');
    expect(await calls(page)).toEqual(['bg', 'sendTransaction:tap']);
    expect(await canaryHits()).toHaveLength(0);
  });

  test('a widget that rewrites the wire gets no wallet call through: the glue re-checks every one', async ({ page }) => {
    await resetCanary();
    const f = await openAttack(page, 'wallet-forged-wire.html');
    await f.locator('#go').click();
    await expect.poll(() => diagCount(page, 'bridge-rejected')).toBe(5);
    await page.waitForTimeout(500);
    expect(await calls(page)).toEqual([]);
    await expect(page.locator('.wallet-sheet')).toHaveCount(0);
    expect(await canaryHits()).toHaveLength(0);
  });

  test('one grant, one window: a second call is BUSY, a refusal brings no grant, a later timer gets none', async ({ page }) => {
    const f = await openAttack(page, 'wallet-grant-reuse.html', '&message=group');
    await f.locator('#go').click();
    await expect(page.locator('.wallet-sheet')).toHaveCount(1);
    await expect(f.locator('#log')).toContainText('second:BUSY');
    await page.locator('#wallet-decline').click();
    await expect(f.locator('#log')).toContainText('after-refusal:NOT_ALLOWED');
    await expect(f.locator('#log')).toContainText('timer:NOT_ALLOWED', { timeout: 5000 });
    await expect(page.locator('.wallet-sheet')).toHaveCount(0);
    expect(await calls(page)).toEqual(['order', 'sendTransaction:first']);
  });
});

test.describe('cpu quota', () => {
  test('a forced-layout loop is killed by the layout-query budget, the host stays responsive', async ({ page }) => {
    await resetCanary();
    const t0 = Date.now();
    await openAttack(page, 'layout-query-loop.html');
    await waitDead(page);
    const s = await twState(page);
    // Which limit fires first depends on the renderer's answer time: the iframe's round trip makes the query budget the binding one
    // ("Layout query limit exceeded"), the Shadow DOM renderer answers fast enough for the loop's own CPU to reach the slice first.
    expect(s.dead).toMatch(/^(Layout query limit exceeded: (more than 128 layout queries|host queries blocked the renderer for more than \d+ ms) in one boot task|CPU limit exceeded: more than \d+ ms of continuous execution)/);
    // The main thread answered the queries all along and is free now: a round trip through the page is instant.
    expect(Date.now() - t0).toBeLessThan(15_000);
    expect(await page.evaluate(() => 1 + 1)).toBe(2);
    expect(await canaryHits()).toHaveLength(0);
  });

  test('a greedy interval is throttled, not killed, and clicks still get through', async ({ page }) => {
    const f = await openAttack(page, 'cpu-greedy-timer.html');
    const js0 = (await twState(page)).stats.jsMs as number;
    await page.waitForTimeout(3000);
    const s = await twState(page);
    expect(s.dead).toBeNull();
    expect(s.diags['cpu-throttled']?.count).toBeGreaterThan(0);
    expect(s.stats.throttledTasks).toBeGreaterThan(0);
    // 40 ms per tick at 10 Hz would be 1200 ms of JS in 3 s; the 300 ms/s quota caps it (plus one slice of slack).
    expect((s.stats.jsMs as number) - js0).toBeLessThan(3 * 300 * 1.2 + 50);
    expect(Number(await f.locator('#ticks').textContent())).toBeGreaterThan(3);
    await f.locator('#btn').click();
    await expect(f.locator('#clicks')).toHaveText('1');
    await expect(page.locator('#status')).toHaveText('● loaded');
  });

  test('haptics from a timer with no touch: one second of them, then nothing (the widget cannot buzz the phone)', async ({ page }) => {
    const f = await openAttack(page, 'haptic-timer-buzz.html');
    await page.waitForTimeout(3000);
    const s = await twState(page);
    const played = s.actions.filter((a) => a.kind === 'haptic').length;
    // One series: the first second, under the rate limit (a burst of 5, then 10 per second); the widget asked far more.
    expect(played).toBeGreaterThan(0);
    expect(played).toBeLessThanOrEqual(5 + 10 + 1);
    expect(Number(await f.locator('#n').textContent())).toBeGreaterThan(played * 3);
    expect(s.diags['haptic-limit']?.count).toBeGreaterThan(0);
  });

  test('a rAF loop burning 30 ms per frame is throttled and frames collapse', async ({ page }) => {
    const f = await openAttack(page, 'cpu-raf-burn.html');
    await page.waitForTimeout(3000);
    const s = await twState(page);
    expect(s.dead).toBeNull();
    expect(s.diags['cpu-throttled']?.count).toBeGreaterThan(0);
    const frames = Number(await f.locator('#frames').textContent());
    // 60 Hz would give ~180 frames in 3 s; at 300 ms/s and 30 ms a frame at most ~10 per second.
    expect(frames).toBeGreaterThan(3);
    expect(frames).toBeLessThan(45);
    expect(s.diags['cpu-throttled'].details[0]).toMatch(/deferred \d+ frames?/);
  });

  test('cpuKillAfterMs terminates a sustained hog and keeps its last render', async ({ page }) => {
    const f = await openAttack(page, 'cpu-kill-after.html', '&cpuKillAfterMs=2000');
    await page.waitForFunction(() => (window as any).__tw.state.dead, null, { timeout: 15_000 });
    const s = await twState(page);
    expect(s.dead).toMatch(/^CPU quota: throttled for \d+\.\d s/);
    expect(s.diags['cpu-throttled']?.count).toBeGreaterThan(0);
    await expect(f.locator('#keep')).toHaveText('Price alert: BTC 61 240 USD');
    await expect(f.locator('html')).toHaveAttribute('data-tw-frozen', 'fatal');
    expect(Number(await f.locator('#ticks').textContent())).toBeGreaterThan(0);
  });
});

for (const file of files) {
  test(`attack ${file} leaks nothing`, async ({ page }) => {
    await resetCanary();
    const outside = watchRequests(page);
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(String(e)));

    await page.goto(`/?file=attacks/${file}${RENDERER_PARAM}`);
    await page.waitForFunction(() => (window as any).__tw?.state && ((window as any).__tw.state.loaded || (window as any).__tw.state.dead), null, { timeout: 60_000 });
    // Give timers, rAF callbacks and deferred mutations time to fire.
    await page.waitForTimeout(1500);
    // Poke the widget: click into it and press a key, in case a vector needs user activation.
    const frame = page.locator(RENDERER_SEL);
    await frame.click({ position: { x: 20, y: 10 }, force: true }).catch(() => {});
    await page.keyboard.press('Enter').catch(() => {});
    await page.waitForTimeout(700);

    const state = await page.evaluate(() => {
      const s = (window as any).__tw.state;
      return { dead: s.dead, diags: Object.keys(s.diags), logs: s.logs.slice(0, 5) };
    });
    const hits = await canaryHits();
    expect(hits, `canary hits for ${file}: ${JSON.stringify(hits)}`).toHaveLength(0);
    expect(outside, `requests outside app origin for ${file} (state ${JSON.stringify(state)})`).toHaveLength(0);
    expect(consoleErrors.filter((e) => /SecurityError|not allowed/.test(e) ? false : true)).toHaveLength(0);
  });
}
