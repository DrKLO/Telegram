// SDK v1 on the web host (docs/sdk-v1.md): the theme variables on the
// renderer root, gesture claims, Web Storage across a restart, the client
// locale and the actions a tap produces, and the bot's data and requests (the bot's data,
// requests through the dev app's mock bot, copyText), on the iframe and the
// Shadow DOM renderer. The widget-facing API itself is unit-tested through QuickJS
// (packages/core/tests/unit/sdk.test.ts).

import { expect, test, type Locator, type Page } from '@playwright/test';
import { PNG } from 'pngjs';

test.skip(process.env.VITE_TW_POLICY === 'compat', 'user activation belongs to the strict policy');

const tw = (page: Page) => page.evaluate(() => (window as any).__tw.state);

async function load(page: Page, html: string, opts: { shadow?: boolean; name?: string | null; query?: string } = {}) {
  await page.goto('/' + (opts.query ?? (opts.shadow ? '?shadow=1' : '')));
  await page.evaluate(([h, n]) => (window as any).__tw.load(h, n), [html, opts.name ?? null] as const);
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  await page.waitForTimeout(100);
  return opts.shadow ? page.locator('#sandbox-wrap .widget-shadow') : page.frameLocator('#sandbox-wrap iframe').locator(':root');
}
const css = (l: Locator, prop: string) => l.evaluate((e, p) => getComputedStyle(e).getPropertyValue(p).trim(), prop);

const THEME_WIDGET = `<style>
  :root { --tg-theme-bg-color: #ff0000 !important; }
  html:root:root { --tg-theme-text-color: #ff0000 !important; }
  #inner { --tg-theme-link-color: #123456; }
</style>
<div id="probe">probe</div><div id="inner"><span id="shadowed">x</span></div><p id="seen"></p>
<script>
  document.documentElement.style.setProperty('--tg-theme-hint-color', '#00ff00', 'important');
  function show() { document.getElementById('seen').textContent = TelegramWidget.colorScheme + ' ' + TelegramWidget.themeParams.bg_color; }
  TelegramWidget.onEvent('themeChanged', show);
  show();
</script>`;

for (const shadow of [false, true]) {
  const renderer = shadow ? 'shadow' : 'iframe';

  test(`${renderer}: the theme variables are on the root, the widget cannot override them there, only shadow them`, async ({ page }) => {
    const root = await load(page, THEME_WIDGET, { shadow });
    const probe = root.locator('#probe');
    expect(await css(probe, '--tg-theme-bg-color')).toBe('#ffffff');
    expect(await css(probe, '--tg-theme-text-color')).toBe('#000000');
    expect(await css(probe, '--tg-theme-hint-color')).toBe('#707579');
    expect(await css(probe, '--tg-color-scheme')).toBe('light');
    expect(await css(root.locator('#shadowed'), '--tg-theme-link-color')).toBe('#123456');
    await expect(root.locator('#seen')).toHaveText('light #ffffff');

    await page.evaluate(() => (window as any).__tw.setTheme('dark'));
    await expect(root.locator('#seen')).toHaveText('dark #212121');
    expect(await css(probe, '--tg-theme-bg-color')).toBe('#212121');
    expect(await css(probe, '--tg-color-scheme')).toBe('dark');
  });

  test(`${renderer}: a theme with params and button tokens reaches the widget and the root`, async ({ page }) => {
    const root = await load(page, THEME_WIDGET, { shadow });
    await page.evaluate(() => (window as any).__tw.host.setTheme({ colorScheme: 'light', params: { bg_color: '#FAFAFA', button_color: 'blue' }, button: { radius: '12px', font: '15px Helvetica' } }));
    await expect(root.locator('#seen')).toHaveText('light #fafafa');
    const probe = root.locator('#probe');
    expect(await css(probe, '--tg-theme-bg-color')).toBe('#fafafa');
    expect(await css(probe, '--tg-theme-button-color')).toBe('#3390ec');
    expect(await css(probe, '--tg-button-radius')).toBe('12px');
    expect(await css(probe, '--tg-button-font')).toBe('');
  });

  test(`${renderer}: the UA layer draws an unstyled widget on the text tokens, the bubble behind it; author CSS wins`, async ({ page }) => {
    const root = await load(page, `<h1 id="h">Title</h1><p id="p">Text <a id="a" href="https://telegram.org">link</a></p><ul id="ul"><li>x</li></ul>`, { shadow });
    const html = shadow ? root.locator('html') : root;
    const body = root.locator('body');
    const h = root.locator('#h');
    // no background of its own: the bubble behind the transparent renderer is the canvas
    expect(await css(html, 'background-color')).toBe('rgba(0, 0, 0, 0)');
    expect(await css(html, 'color')).toBe('rgb(0, 0, 0)');
    expect([await css(html, 'font-size'), await css(html, 'line-height')]).toEqual(['15px', '20.25px']);
    expect([await css(body, 'margin'), await css(body, 'padding')]).toEqual(['8px 12px', '0px']);
    expect([await css(h, 'font-size'), await css(h, 'margin-top'), await css(h, 'font-weight')]).toEqual(['24px', '0px', '600']);
    expect(await css(root.locator('#a'), 'color')).toBe('rgb(51, 144, 236)');
    expect(await css(root.locator('#ul'), 'padding-left')).toBe('22.5px');
    expect(await css(h, '--tg-text-font-size')).toBe('15px');
    const height = (await tw(page)).height;

    await page.evaluate(() => (window as any).__tw.host.setTheme({ colorScheme: 'dark', params: { bg_color: '#101820' }, text: { fontSize: 18, lineHeight: 1.5, padding: '4px 6px' } }));
    await expect.poll(() => css(html, 'font-size')).toBe('18px');
    expect(await css(html, '--tg-theme-bg-color')).toBe('#101820');
    expect(await css(html, 'background-color')).toBe('rgba(0, 0, 0, 0)');
    expect(await css(html, 'color')).toBe('rgb(255, 255, 255)');
    expect(await css(html, 'line-height')).toBe('27px');
    expect(await css(body, 'margin')).toBe('4px 6px');
    expect(await css(h, 'font-size')).toBe('28.8px');
    await expect.poll(async () => (await tw(page)).height).toBeGreaterThan(height);

    await page.evaluate(() => (window as any).__tw.host.setTheme({ colorScheme: 'light', text: { fontSize: 100, lineHeight: '2em', padding: 'auto' } }));
    await expect.poll(() => css(html, 'font-size')).toBe('15px');
    expect([await css(html, 'line-height'), await css(body, 'margin')]).toEqual(['20.25px', '8px 12px']);

    const styled = await load(page, `<style>body { margin: 0 } h1 { margin-top: 5px; font-size: 10px } a { color: #ff0000 }</style><h1 id="h">T</h1><a id="a" href="https://telegram.org">l</a>`, { shadow });
    expect(await css(styled.locator('body'), 'margin')).toBe('0px');
    expect([await css(styled.locator('#h'), 'margin-top'), await css(styled.locator('#h'), 'font-size')]).toEqual(['5px', '10px']);
    expect(await css(styled.locator('#a'), 'color')).toBe('rgb(255, 0, 0)');
  });

  test(`${renderer}: a body background reaches the whole bubble, its margins included (root background propagation)`, async ({ page }) => {
    await load(page, `<style>body { background: #2b5278; color: #fff }</style><p>Body background, no margin reset.</p>`, { shadow });
    const frame = page.locator('#sandbox-wrap .widget-frame');
    const box = (await frame.boundingBox())!;
    // pixels in the body's margins (12 px at the sides, 8 px at the top), away from the bubble's rounded corners
    const shot = await page.screenshot({ clip: { x: box.x, y: box.y, width: box.width, height: Math.min(box.height, 40) } });
    const png = PNG.sync.read(shot);
    const at = (x: number, y: number) => { const i = (y * png.width + x) * 4; return [png.data[i], png.data[i + 1], png.data[i + 2]]; };
    const scale = png.width / box.width;
    for (const [x, y] of [[3, 20], [Math.round(box.width / 2), 3], [Math.round(box.width) - 3, 20]]) expect(at(Math.round(x * scale), Math.round(y * scale)), `${x},${y}`).toEqual([0x2b, 0x52, 0x78]);
  });

  test(`${renderer}: the demo bubble is the canvas: a body background fills it to the rounded corners (visual/15-body-background)`, async ({ page }) => {
    for (const theme of ['light', 'dark'] as const) {
      const q = new URLSearchParams({ file: 'conformance/visual/15-body-background.html', ...(shadow ? { shadow: '1' } : {}), ...(theme === 'dark' ? { theme: 'dark' } : {}) });
      await page.goto('/?' + q);
      await page.waitForFunction(() => (window as any).__tw.state.loaded);
      await page.waitForTimeout(300);
      // The whole bubble the dev app draws, not only the renderer's element: a padded bubble would leave a strip of its own colour.
      const bubble = page.locator('#sandbox-wrap').locator('xpath=..');
      const png = PNG.sync.read(await bubble.screenshot());
      const at = (x: number, y: number) => { const i = (y * png.width + x) * 4; return [png.data[i], png.data[i + 1], png.data[i + 2]]; };
      const w = png.width, h = png.height, s = Math.round(png.width / ((await bubble.boundingBox())!.width));
      const blue = [0x2b, 0x52, 0x78];
      // just inside the 14 px radius at each corner, and the middle of each edge
      for (const [x, y] of [[6, 6], [w / s - 7, 6], [6, h / s - 7], [w / s - 7, h / s - 7], [w / s / 2, 1], [1, h / s / 2], [w / s - 2, h / s / 2], [w / s / 2, h / s - 2]]) {
        expect(at(Math.round(x * s), Math.round(y * s)), `${theme} ${x},${y}`).toEqual(blue);
      }
      // the very corner is outside the radius: clipped, not blue
      expect(at(0, 0), `${theme} corner`).not.toEqual(blue);
    }
  });

  test(`${renderer}: an unstyled widget sits on the bubble's own colour in both themes (the renderer stays transparent)`, async ({ page }) => {
    for (const theme of ['light', 'dark'] as const) {
      const q = new URLSearchParams({ file: 'defaults/static/01-text-card.html', ...(shadow ? { shadow: '1' } : {}), ...(theme === 'dark' ? { theme: 'dark' } : {}) });
      await page.goto('/?' + q);
      await page.waitForFunction(() => (window as any).__tw.state.loaded);
      await page.waitForTimeout(300);
      const bubble = page.locator('#sandbox-wrap').locator('xpath=..');
      const png = PNG.sync.read(await bubble.screenshot());
      const at = (x: number, y: number) => { const i = (y * png.width + x) * 4; return [png.data[i], png.data[i + 1], png.data[i + 2]]; };
      const s = Math.round(png.width / ((await bubble.boundingBox())!.width));
      const want = theme === 'dark' ? [0x21, 0x21, 0x21] : [0xff, 0xff, 0xff];
      // in the body's margin: the bubble's colour shows through the renderer, never a canvas of the renderer's own
      for (const [x, y] of [[6, 6], [png.width / s - 7, png.height / s - 4], [png.width / s / 2, 3]]) expect(at(Math.round(x * s), Math.round(y * s)), `${theme} ${x},${y}`).toEqual(want);
    }
  });

  test(`${renderer}: text is not selectable by default; user-select: text enables it, children included`, async ({ page }) => {
    const root = await load(page, `<p id="off">This text cannot be selected.</p><p id="on" style="user-select: text; -webkit-user-select: text">This text <b id="kid">can</b> be selected.</p>`, { shadow });
    // The renderer frame is sandboxed (an opaque origin): read its selection through Playwright, not from the page.
    const frame = shadow ? null : await (await page.locator('#sandbox-wrap iframe').elementHandle())!.contentFrame();
    const selected = () => frame ? frame.evaluate(() => getSelection()!.toString()) : page.evaluate(() => {
      const host = document.querySelector('#sandbox-wrap .widget-shadow')!;
      const r = (host.shadowRoot as any).getSelection?.() ?? document.getSelection();
      return r ? r.toString() : '';
    });
    const drag = async (sel: string) => {
      const b = (await root.locator(sel).boundingBox())!;
      await page.mouse.move(b.x + 2, b.y + b.height / 2);
      await page.mouse.down();
      await page.mouse.move(b.x + b.width - 2, b.y + b.height / 2, { steps: 8 });
      await page.mouse.up();
    };
    // Blink inherits user-select; the spec's `auto` would follow the parent to the same used value
    expect(await css(root.locator('#off'), 'user-select')).toMatch(/^(none|auto)$/);
    expect(await css(root.locator('#on'), 'user-select')).toBe('text');
    expect(await css(root.locator('#kid'), 'user-select')).toMatch(/^(text|auto)$/);
    await drag('#off');
    expect(await selected()).toBe('');
    await drag('#on');
    expect(await selected()).toContain('can be selected');
  });

  test(`${renderer}: each primary pointerdown reports the horizontal drags and holds the widget takes (never vertical ones)`, async ({ page }) => {
    const root = await load(page, `<style>div { height: 30px; margin: 4px; background: #ddd; }</style>
      <div id="none" style="touch-action: none">none</div><div id="pany" style="touch-action: pan-y">pan-y</div>
      <div id="panx" style="touch-action: pan-x">pan-x</div><div id="auto">auto</div>
      <div id="pd">prevented</div><script>document.getElementById('pd').addEventListener('pointerdown', function (e) { e.preventDefault(); });</script>`, { shadow });
    for (const id of ['none', 'pany', 'panx', 'auto', 'pd', 'pd']) await root.locator('#' + id).click();
    await expect.poll(async () => (await tw(page)).gestures.length).toBe(6);
    const gestures = (await tw(page)).gestures as { x: boolean; y: boolean; hold: boolean }[];
    // The first press on #pd teaches the renderer that the widget prevents it; the second one claims horizontal drags
    // (never holds). No claim has vertical drags since v0.1.3: pan-x claims nothing, none horizontal drags and holds.
    const c = (x: boolean, hold = false) => ({ x, y: false, hold });
    expect(gestures).toEqual([c(true, true), c(true), c(false), c(false), c(false), c(true)]);
  });

  test(`${renderer}: setResizeAnchor reaches the host with the height, and the dev app keeps the anchored edge`, async ({ page }) => {
    // Taller than the 50 px minimum from the start: the anchor reaches the client with a size report (the height's).
    const root = await load(page, `<style>body{margin:0}#g div{height:40px}</style><button id="b">grow</button><button id="t">top</button><div id="g"><div></div></div><script>
      TelegramWidget.setResizeAnchor('bottom');
      document.getElementById('b').addEventListener('click', function () { document.getElementById('g').appendChild(document.createElement('div')); });
      document.getElementById('t').addEventListener('click', function () { TelegramWidget.setResizeAnchor('top'); });
    </script>`, { shadow });
    const bubble = () => page.locator('#chat-sandbox .bubble').evaluate((e) => { const r = e.getBoundingClientRect(); return { top: Math.round(r.top), bottom: Math.round(r.bottom) }; });
    await expect.poll(async () => (await tw(page)).resizeAnchor).toBe('bottom');
    const before = await bubble(), h0 = (await tw(page)).height;
    await root.locator('#b').click();
    await expect.poll(async () => (await tw(page)).height).toBe(h0 + 40);
    const after = await bubble();
    // Anchored at the bottom: the bottom edge stays, the bubble grows upward.
    expect(after.bottom).toBe(before.bottom);
    expect(after.top).toBe(before.top - 40);
    expect((await tw(page)).bridge.map((b: { method: string }) => b.method)).not.toContain('setResizeAnchor');
    expect((await tw(page)).actions).toEqual([]);
    await root.locator('#t').click();
    await root.locator('#b').click();
    await expect.poll(async () => (await tw(page)).height).toBe(h0 + 80);
    expect((await tw(page)).resizeAnchor).toBe('top');
  });

  test(`${renderer}: a widget that never calls setResizeAnchor grows downward (top)`, async ({ page }) => {
    await load(page, '<p>x</p>', { shadow });
    expect((await tw(page)).resizeAnchor).toBe('top');
  });

  test(`${renderer}: a tap opens one link and plays haptics; the actions reach the client`, async ({ page }) => {
    const root = await load(page, `<button id="b">go</button><a id="t" href="https://t.me/durov">t</a><script>
      document.getElementById('b').addEventListener('click', function () {
        TelegramWidget.HapticFeedback.impactOccurred('light');
        TelegramWidget.openLink('https://Example.com/é');
        TelegramWidget.openLink('https://example.com/second');
      });</script>`, { shadow });
    await root.locator('#b').click();
    await root.locator('#t').click();
    await expect.poll(async () => (await tw(page)).actions.length).toBe(3);
    expect((await tw(page)).actions).toEqual([
      { kind: 'haptic', haptic: 'impact', value: 'light' },
      { kind: 'openLink', url: 'https://example.com/%C3%A9' },
      { kind: 'openTelegramLink', url: 'https://t.me/durov' },
    ]);
    expect((await tw(page)).diags['activation-blocked']?.count).toBe(1);
  });

  // ───────────── the bot's data, requests, copyText (docs/sdk-v1.md §2.13–§2.15; the dev app's mock bot) ─────────────

  const REQUEST_WIDGET = `<button id="b">ask</button><p id="out"></p><p id="data"></p><script>
    var T = TelegramWidget, out = document.getElementById('out');
    function show() { document.getElementById('data').textContent = [T.chatData, T.publicData, T.isForward, T.chatType, T.viewer && T.viewer.id].join('|'); }
    T.onEvent('chatDataChanged', show);
    T.onEvent('publicDataChanged', show);
    show();
    document.getElementById('b').addEventListener('click', function () {
      T.request('hello').then(function (a) { out.textContent = 'ok ' + a.result; }, function (e) { out.textContent = 'err ' + e.code; });
      T.copyText('never: the request took the tap');
    });
    setTimeout(function () { T.request('timer').catch(function (e) { out.textContent = 'timer ' + e.code; }); }, 30);
  </script>`;
  const withContext = async (page: Page, patch: Record<string, unknown>) => {
    await page.goto('/' + (shadow ? '?shadow=1' : ''));
    await page.evaluate((p) => { const tw = (window as any).__tw; Object.assign(tw.bot.context, p); tw.bot.bot = 'echo'; tw.bot.delayMs = 50; }, patch);
  };
  const open = async (page: Page, html: string, name: string) => {
    await page.evaluate(([h, n]) => (window as any).__tw.load(h, n), [html, name] as const);
    await page.waitForFunction(() => (window as any).__tw.state.loaded);
    return shadow ? page.locator('#sandbox-wrap .widget-shadow') : page.frameLocator('#sandbox-wrap iframe').locator(':root');
  };

  test(`${renderer}: a tap sends one request to the bot and gets its result; a timer cannot (a group); the bot's edit fires the data events`, async ({ page }) => {
    await withContext(page, { message: 'group', chatType: 'supergroup', viewer: 1001 });
    const root = await open(page, REQUEST_WIDGET, 'sdk/request.html');
    await expect(page.locator('#net-marker')).toBeVisible();
    await expect(root.locator('#data')).toHaveText('||false|supergroup|1001');
    await expect(root.locator('#out')).toHaveText('timer NOT_ALLOWED');
    await root.locator('#b').click();
    await expect(root.locator('#out')).toHaveText('ok hello');
    const state = await tw(page);
    expect(state.requests.map((r: any) => [r.data, r.answer?.result])).toEqual([['hello', 'hello']]);
    expect(state.copiedTexts).toEqual([]);
    expect(state.diags['request-blocked']?.count).toBe(1);
    expect(state.diags['activation-blocked']?.count).toBe(1);
    await page.evaluate(() => (window as any).__tw.bot.edit('sdk/request.html', { chatData: '{"turn":2}', publicData: 'score 1:0' }));
    await expect(root.locator('#data')).toHaveText('{"turn":2}|score 1:0|false|supergroup|1001');
  });

  test(`${renderer}: a forwarded copy without fwd_allow_queries knows only publicData and cannot request, even with a tap; its publicData can change`, async ({ page }) => {
    await withContext(page, { message: 'copy', chatType: null, viewer: 1001 });
    await page.evaluate(() => (window as any).__tw.bot.edit('sdk/copy.html', { chatData: 'secret', publicData: 'snapshot' }));
    const root = await open(page, REQUEST_WIDGET, 'sdk/copy.html');
    await expect(page.locator('#net-marker')).toBeHidden();
    await expect(root.locator('#data')).toHaveText('|snapshot|true||');
    await root.locator('#b').click();
    await expect(root.locator('#out')).toHaveText('err NOT_ALLOWED');
    expect((await tw(page)).requests).toEqual([]);
    await page.evaluate(() => (window as any).__tw.bot.edit('sdk/copy.html', { chatData: 'secret 2', publicData: 'shared value' }));
    await expect(root.locator('#data')).toHaveText('|shared value|true||');
  });

  test(`${renderer}: the client polls a shared widget's publicData only while it watches (watchPublicData); otherwise it comes with the message`, async ({ page }) => {
    await withContext(page, { message: 'private', chatType: null, viewer: 1001 });
    await page.evaluate(() => Object.assign((window as any).__tw.bot, { pollMs: 100, shared: true }));
    const html = (watch: boolean) => `<p id="p"></p><script>${watch ? 'TelegramWidget.watchPublicData();' : ''}
      function show() { document.getElementById('p').textContent = String(TelegramWidget.publicData); }
      TelegramWidget.onEvent('publicDataChanged', show); show();</script>`;
    let root = await open(page, html(true), 'sdk/watch.html');
    await expect(root.locator('#p')).toHaveText('null');
    await page.evaluate(() => (window as any).__tw.bot.editShared('sdk/watch.html', 'v2'));
    await expect(root.locator('#p')).toHaveText('v2');
    expect(await page.evaluate(() => (window as any).__tw.bot.watching)).toBe(true);
    root = await open(page, html(false), 'sdk/still.html');
    await page.evaluate(() => (window as any).__tw.bot.editShared('sdk/still.html', 'v2'));
    await page.waitForTimeout(500);
    await expect(root.locator('#p')).toHaveText('null');
    expect(await page.evaluate(() => (window as any).__tw.bot.watching)).toBe(false);
    root = await open(page, html(false), 'sdk/still.html');
    await expect(root.locator('#p')).toHaveText('v2');
  });

  test(`${renderer}: the viewer is a WebAppUser (id, names, username, Premium) where the widget may ask; a broken field is left out`, async ({ page }) => {
    await withContext(page, { message: 'private', chatType: null, viewer: 1001, profile: 'Ann,Lee,ann_lee,premium' });
    const html = '<p id="v"></p><script>document.getElementById("v").textContent = JSON.stringify(TelegramWidget.viewer) + "|" + Object.isFrozen(TelegramWidget.viewer);</script>';
    let root = await open(page, html, 'sdk/viewer.html');
    await expect(root.locator('#v')).toHaveText('{"id":1001,"first_name":"Ann","last_name":"Lee","username":"ann_lee","is_premium":true}|true');
    await page.evaluate(() => Object.assign((window as any).__tw.bot.context, { message: 'group', profile: 'Ann,,a-b' }));
    root = await open(page, html, 'sdk/viewer.html');
    await expect(root.locator('#v')).toHaveText('{"id":1001,"first_name":"Ann"}|true');
    expect((await tw(page)).diags['data-rejected']?.details).toEqual(['viewer.username: not 5–32 characters of A–Z, a–z, 0–9 and _; the widget does not see it']);
  });

  test(`${renderer}: an original sent without allow_queries has its data and the chat but no viewer, cannot request, tap or not, and shows no network marker`, async ({ page }) => {
    await withContext(page, { message: 'no-queries', chatType: null, viewer: 1001 });
    await page.evaluate(() => (window as any).__tw.bot.edit('sdk/no-queries.html', { chatData: 'mine', publicData: 'p' }));
    const root = await open(page, REQUEST_WIDGET, 'sdk/no-queries.html');
    await expect(page.locator('#net-marker')).toBeHidden();
    await expect(root.locator('#data')).toHaveText('mine|p|false|private|');
    await expect(root.locator('#out')).toHaveText('timer NOT_ALLOWED');
    await root.locator('#b').click();
    await expect(root.locator('#out')).toHaveText('err NOT_ALLOWED');
    expect((await tw(page)).requests).toEqual([]);
  });

  test(`${renderer}: a live copy (fwd_allow_queries) asks on a tap and knows the viewer, never the chat or chatData`, async ({ page }) => {
    await withContext(page, { message: 'live-copy', chatType: null, viewer: 1001 });
    await page.evaluate(() => (window as any).__tw.bot.edit('sdk/live-copy.html', { chatData: 'secret', publicData: 'live' }));
    const root = await open(page, REQUEST_WIDGET, 'sdk/live-copy.html');
    await expect(page.locator('#net-marker')).toBeVisible();
    await expect(root.locator('#data')).toHaveText('|live|true||1001');
    await expect(root.locator('#out')).toHaveText('timer NOT_ALLOWED');
    await root.locator('#b').click();
    await expect(root.locator('#out')).toHaveText('ok hello');
    expect((await tw(page)).requests.map((r: any) => r.data)).toEqual(['hello']);
  });

  test(`${renderer}: a preview (an inline result not sent yet) has its data but gets no tap, asks nothing and knows no viewer`, async ({ page }) => {
    await withContext(page, { message: 'preview', chatType: null, viewer: 1001 });
    await page.evaluate(() => (window as any).__tw.bot.edit('sdk/preview.html', { chatData: 'c', publicData: 'p' }));
    const root = await open(page, `<button id="b" style="width:200px;height:60px">tap</button><p id="out"></p><p id="data"></p><script>
      var T = TelegramWidget, taps = 0;
      document.getElementById('data').textContent = [T.isPreview, T.chatData, T.publicData, T.chatType, T.viewer].join('|');
      document.getElementById('b').addEventListener('click', function () { taps++; document.getElementById('out').textContent = 'tapped ' + taps; T.copyText('x'); });
      T.request('boot').catch(function (e) { document.getElementById('out').textContent = 'boot ' + e.code; });
    </script>`, 'sdk/preview.html');
    await expect(root.locator('#data')).toHaveText('true|c|p||');
    await expect(page.locator('#net-marker')).toBeHidden();
    await expect(root.locator('#out')).toHaveText('boot NOT_ALLOWED');
    await root.locator('#b').click();
    await page.waitForTimeout(400);
    await expect(root.locator('#out')).toHaveText('boot NOT_ALLOWED');
    const state = await tw(page);
    expect(state.requests).toEqual([]);
    expect(state.copiedTexts).toEqual([]);
    expect(state.gestures).toEqual([]);
  });

  test(`${renderer}: a failed request rejects with the client's code; copyText reaches the client with a toast`, async ({ page }) => {
    await withContext(page, { message: 'group' });
    await page.evaluate(() => { (window as any).__tw.bot.mode = 'TIMEOUT'; });
    let root = await open(page, REQUEST_WIDGET, 'sdk/fail.html');
    await root.locator('#b').click();
    await expect(root.locator('#out')).toHaveText('err TIMEOUT');
    root = await open(page, `<button id="c">copy</button><script>document.getElementById('c').onclick = function () { TelegramWidget.copyText('PROMO-2026'); TelegramWidget.copyText('second'); };</script>`, 'sdk/copy-text.html');
    await root.locator('#c').click();
    await expect.poll(async () => (await tw(page)).copiedTexts).toEqual(['PROMO-2026']);
    await expect(page.locator('#chat-sandbox .bubble-copied')).toHaveText('Copied: PROMO-2026');
  });
}

test('the poll demo: a tap votes through the mock bot, the widget redraws from the edited chatData', async ({ page }) => {
  await page.goto('/?file=interactive/01-poll.html');
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
  await expect(root.locator('#hint')).toHaveText(/^9 votes/);
  await root.locator('.opt').nth(2).click();
  await expect(root.locator('.opt').nth(2)).toHaveClass(/mine/);
  await expect(root.locator('#hint')).toHaveText(/^10 votes/);
  const data = await page.evaluate(() => (window as any).__tw.bot.dataFor('interactive/01-poll.html'));
  expect(JSON.parse(data.chatData).u['1001']).toBe(2);
  expect(data.publicData).toBe('{"v":[3,5,2]}');
});

test('the wallet demo: a tap pays through the client\'s wallet window, the bot gets the outcome; a decline is REJECTED; without the wallet the widget says so', async ({ page }) => {
  await page.goto('/?file=interactive/05-wallet.html');
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
  await root.locator('.item button').first().click();
  // The window is the client's, outside the bubble: the bot of the message and what is paid.
  await expect(page.locator('.wallet-sheet')).toContainText('@shop_bot asks you to send');
  await expect(page.locator('.wallet-sheet')).toContainText('1.5 TON');
  await page.locator('#wallet-confirm').click();
  await expect(root.locator('#note')).toHaveText('Paid for Coffee. Thank you!');
  await expect(root.locator('#paid')).toHaveText('Paid: Coffee');
  let state = await tw(page);
  expect(state.requests.map((r: any) => [r.data, r.kind])).toEqual([['pay:0', 'wallet'], ['paid:0', 'sendTransaction']]);
  await root.locator('#terms').click();
  await expect(page.locator('.wallet-sheet pre')).toHaveText('I accept the Corner café terms of 30 October 2026.');
  await page.locator('#wallet-decline').click();
  await expect(root.locator('#note')).toHaveText('You declined in the wallet.');
  state = await tw(page);
  expect(state.requests.at(-1)).toMatchObject({ data: 'terms', kind: 'signData', error: 'REJECTED' });
  await page.goto('/?file=interactive/05-wallet.html&wallet=0');
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  await expect(root.locator('#note')).toHaveText('No wallet here: pay in the bot.');
  await root.locator('.item button').first().click();
  await expect(page.locator('.wallet-sheet')).toHaveCount(0);
  expect((await tw(page)).requests).toEqual([]);
});

test('background requests: allowed in the private chat with the bot (backgroundQuery) while visible, one at once, then one per 5 s', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => { const tw = (window as any).__tw; Object.assign(tw.bot.context, { message: 'private' }); tw.bot.bot = 'echo'; tw.bot.delayMs = 0; });
  await page.evaluate((h) => (window as any).__tw.load(h, 'sdk/background.html'), `<p id="log"></p><script>
    var log = [];
    function ask() { TelegramWidget.request('bg').then(function () { log.push('ok'); next(); }, function (e) { log.push(e.code); next(); }); }
    function next() { document.getElementById('log').textContent = log.join(' '); if (log.length < 8) setTimeout(ask, 800); }
    ask();
  </script>`);
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
  await expect(root.locator('#log')).toHaveText(/^ok( RATE_LIMITED){5,6} ok/, { timeout: 15_000 });
  expect((await tw(page)).requests).toHaveLength(2);
});

test('background requests: the widget appearing on screen again may ask at once, before the 5 s are up', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => { const tw = (window as any).__tw; Object.assign(tw.bot.context, { message: 'private' }); tw.bot.bot = 'echo'; tw.bot.delayMs = 0; });
  await page.evaluate((h) => (window as any).__tw.load(h, 'sdk/background-show.html'), `<p id="log"></p><script>
    var log = [];
    function ask() { TelegramWidget.request('bg').then(function () { log.push('ok'); next(); }, function (e) { log.push(e.code); next(); }); }
    function next() { document.getElementById('log').textContent = log.join(' '); if (log.length < 40) setTimeout(ask, 300); }
    ask();
  </script>`);
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const t0 = Date.now();
  const root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
  await expect(root.locator('#log')).toHaveText(/^ok RATE_LIMITED/);
  await page.evaluate(() => (window as any).__tw.setVisible(false));
  // Off screen the next ask (its 300 ms timer fires at the hidden 1 Hz) waits in the host: no answer, nothing sent.
  await page.waitForTimeout(1500);
  const held = (await root.locator('#log').textContent()) ?? '';
  expect(held).not.toMatch(/NOT_ALLOWED|BUSY/);
  expect((await tw(page)).requests).toHaveLength(1);
  await page.evaluate(() => (window as any).__tw.setVisible(true));
  await expect.poll(async () => (await tw(page)).requests.length, { timeout: 3000 }).toBe(2);
  await expect(root.locator('#log')).toHaveText(new RegExp('^' + held + ' ok'));
  expect(Date.now() - t0).toBeLessThan(5000);
  await page.evaluate(() => (window as any).__tw.setVisible(null));
});

test('background requests: one at boot off screen (as Telegram\'s clients create a bubble) goes out when the widget appears; one on activated is BUSY', async ({ page }) => {
  await page.goto('/');
  // The bot takes 300 ms, as a real one does: the boot request is still in flight when the widget hears it is visible.
  await page.evaluate(() => { const tw = (window as any).__tw; Object.assign(tw.bot.context, { message: 'private' }); tw.bot.bot = 'echo'; tw.bot.delayMs = 300; tw.setVisible(false); });
  await page.evaluate((h) => (window as any).__tw.load(h, 'sdk/background-boot.html'), `<p id="log"></p><p id="active"></p><script>
    var log = [];
    function ask(t) { TelegramWidget.request(t).then(function (a) { log.push(t + ':' + a.result); show(); }, function (e) { log.push(t + ':' + e.code); show(); }); }
    function show() { document.getElementById('log').textContent = log.slice().sort().join(' '); }
    document.getElementById('active').textContent = 'active ' + TelegramWidget.isActive;
    TelegramWidget.onEvent('activated', function () { ask('activated'); });
    ask('boot');
  </script>`);
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  const root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
  await expect(root.locator('#active')).toHaveText('active false');
  await page.waitForTimeout(500);
  await expect(root.locator('#log')).toHaveText('');
  expect((await tw(page)).requests).toEqual([]);
  await page.evaluate(() => (window as any).__tw.setVisible(true));
  await expect(root.locator('#log')).toHaveText('activated:BUSY boot:boot');
  expect((await tw(page)).requests.map((r: any) => r.data)).toEqual(['boot']);
  await page.evaluate(() => (window as any).__tw.setVisible(null));
});

test('Web Storage: the client keeps what the widget commits and preloads it after a restart and a reload', async ({ page }) => {
  const widget = `<button id="b">+1</button><p id="n"></p><script>
    var n = Number(localStorage.getItem('n') || 0), s = Number(sessionStorage.getItem('s') || 0);
    function show() { document.getElementById('n').textContent = n + '/' + s; }
    document.getElementById('b').addEventListener('click', function () { localStorage.setItem('n', String(++n)); sessionStorage.setItem('s', String(++s)); show(); });
    show();</script>`;
  let root = await load(page, widget, { name: 'sdk/storage.html' });
  await page.evaluate(() => (window as any).__tw.clearStorage());
  root = await load(page, widget, { name: 'sdk/storage.html' });
  await expect(root.locator('#n')).toHaveText('0/0');
  await root.locator('#b').click();
  await root.locator('#b').click();
  await expect(root.locator('#n')).toHaveText('2/2');
  await page.evaluate(() => (window as any).__tw.restart());
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  root = page.frameLocator('#sandbox-wrap iframe').locator(':root');
  await expect(root.locator('#n')).toHaveText('2/2');
  expect(await page.evaluate(() => (window as any).__tw.storage())).toEqual({ local: { n: '2' }, session: { s: '2' } });
  // A reload is an app restart: `local` survives, `session` does not.
  root = await load(page, widget, { name: 'sdk/storage.html' });
  await expect(root.locator('#n')).toHaveText('2/0');
});

test('the client locale is navigator.language and the Intl default', async ({ page }) => {
  const root = await load(page, `<p id="l"></p><script>document.getElementById('l').textContent = navigator.language + ' ' + (1234.5).toLocaleString();</script>`, { query: '?locale=ru' });
  await expect(root.locator('#l')).toHaveText('ru 1 234,5');
});
