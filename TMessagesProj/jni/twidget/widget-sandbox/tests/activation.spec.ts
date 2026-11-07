// User activation (docs/decisions.md): privileged bridge calls need a real
// pointer gesture. Playwright's clicks are trusted input (isTrusted = true);
// key presses are trusted too but never reach the widget (profile 1: no
// keyboard, no focus), and scripted clicks, timers or the frame's own
// dispatchEvent are no gesture.

import { expect, test, type Page } from '@playwright/test';

test.skip(process.env.VITE_TW_POLICY === 'compat', 'the gesture gate belongs to the strict policy');

const WIDGET = `<button id="b">open</button>
<script>
TelegramWidget.ready();
setTimeout(function () { TelegramWidget.openLink('https://example.com/timer'); }, 10);
document.getElementById('b').addEventListener('click', function () {
  TelegramWidget.openLink('https://example.com/clicked');
  TelegramWidget.openTelegramLink('https://t.me/second_in_the_same_gesture');
  setTimeout(function () { TelegramWidget.openLink('https://example.com/later'); }, 0);
});
document.addEventListener('keydown', function () { TelegramWidget.openLink('https://example.com/key'); });
setTimeout(function () { document.getElementById('b').click(); }, 50);
</script>`;

async function load(page: Page) {
  await page.goto('/');
  await page.evaluate((html) => (window as any).__tw.load(html, null), WIDGET);
  await page.waitForFunction(() => (window as any).__tw.state.loaded);
  return page.frameLocator('#sandbox-wrap iframe');
}
const calls = (page: Page) => page.evaluate(() => (window as any).__tw.state.bridge.map((b: any) => `${b.method}:${JSON.stringify(b.args)}`) as string[]);
const blocked = (page: Page) => page.evaluate(() => (window as any).__tw.state.diags['activation-blocked']?.count ?? 0);

test('timers and scripted clicks reach no privileged bridge method', async ({ page }) => {
  await load(page);
  await page.waitForTimeout(500);
  expect(await calls(page)).toEqual(['ready:[]']);
  expect(await blocked(page)).toBeGreaterThanOrEqual(3);
});

test('one real click unlocks exactly one call, made in its own handler', async ({ page }) => {
  const f = await load(page);
  await page.waitForTimeout(300);
  await f.locator('#b').click();
  await expect.poll(() => calls(page)).toContain('openLink:["https://example.com/clicked"]');
  await page.waitForTimeout(300);
  expect(await calls(page)).toEqual(['ready:[]', 'openLink:["https://example.com/clicked"]']);
  await f.locator('#b').click();
  await expect.poll(async () => (await calls(page)).length).toBe(3);
});

test('a real key press reaches nothing: no key event, no Enter or Space click (widgets get no keyboard)', async ({ page }) => {
  const f = await load(page);
  await page.waitForTimeout(300);
  const before = await blocked(page);
  await f.locator('#b').press('Enter');
  await f.locator('#b').press(' ');
  await f.locator('#b').press('a');
  await page.waitForTimeout(500);
  // Neither the keydown handler nor the button's click handler ran, so nothing was even attempted.
  expect(await calls(page)).toEqual(['ready:[]']);
  expect(await blocked(page)).toBe(before);
});

test('a hidden widget cannot use a gesture', async ({ page }) => {
  const f = await load(page);
  await page.evaluate(() => (window as any).__tw.setVisible(false));
  await f.locator('#b').click({ force: true });
  await page.waitForTimeout(500);
  expect(await calls(page)).toEqual(['ready:[]']);
});
