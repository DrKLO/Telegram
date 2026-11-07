import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { chromium } from '@playwright/test';
import { rendererBuildOptions } from '../../scripts/sandbox-build.ts';
import { buildSrcdoc, makeNonce } from '../../src/renderer/srcdoc.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

describe('renderer move coalescing', () => {
  it('keeps all movement and the latest coordinates per pointer and event type before release and frame flushes', async () => {
    const out = await build(rendererBuildOptions(root, 'webkit'));
    const browser = await chromium.launch();
    try {
      const page = await browser.newPage();
      await page.addInitScript({ content: 'window.__messages = []; window.webkit = {messageHandlers: {tw: {postMessage: function(s) {window.__messages.push(JSON.parse(s));}}}};' });
      const url = 'http://renderer.invalid/';
      await page.route('**/*', (route) => route.request().url() === url
        ? route.fulfill({ body: buildSrcdoc(out.outputFiles![0].text, makeNonce()), contentType: 'text/html' })
        : route.abort());
      await page.goto(url);
      const events = await page.evaluate(async () => {
        const w = window as any;
        w.__twReceive(JSON.stringify({ type: 'ops', ops: [
          { op: 'el', id: 2, tag: 'html', adopt: 'html', attrs: [] }, { op: 'ins', parent: 1, id: 2, before: null },
          { op: 'el', id: 3, tag: 'body', adopt: 'body', attrs: [] }, { op: 'ins', parent: 2, id: 3, before: null },
          { op: 'el', id: 4, tag: 'div', attrs: [] }, { op: 'attr', id: 4, name: 'id', value: 'target' },
          { op: 'ins', parent: 3, id: 4, before: null },
        ] }));
        w.__twReceive(JSON.stringify({ type: 'listen', types: ['pointermove', 'mousemove', 'pointerup'] }));
        w.__messages = [];
        const target = document.getElementById('target')!;
        for (const [type, pointerId, x, y, dx, dy] of [
          ['pointermove', 1, 10, 20, 3, -2],
          ['pointermove', 2, 30, 40, 7, 8],
          ['mousemove', 1, 50, 60, 2, 3],
          ['pointermove', 1, 70, 80, -1, 5],
          ['mousemove', 1, 90, 100, 4, -1],
          ['pointerup', 1, 0, 0, 0, 0],
          ['pointermove', 1, 110, 120, 9, -3],
          ['pointermove', 1, 130, 140, 1, 2],
        ] as const) {
          const EventClass = type === 'mousemove' ? MouseEvent : PointerEvent;
          target.dispatchEvent(new EventClass(type, { bubbles: true, pointerId, clientX: x, clientY: y, movementX: dx, movementY: dy }));
        }
        await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        return w.__messages.filter((message: any) => message.type === 'event').map((message: any) => ({
          type: message.ev.type, pointerId: message.ev.pointerId ?? 0, x: message.ev.clientX,
          y: message.ev.clientY, dx: message.ev.movementX, dy: message.ev.movementY,
        }));
      });
      assert.deepEqual(events, [
        { type: 'pointermove', pointerId: 1, x: 70, y: 80, dx: 2, dy: 3 },
        { type: 'pointermove', pointerId: 2, x: 30, y: 40, dx: 7, dy: 8 },
        { type: 'mousemove', pointerId: 0, x: 90, y: 100, dx: 6, dy: 2 },
        { type: 'pointerup', pointerId: 1, x: 0, y: 0, dx: 0, dy: 0 },
        { type: 'pointermove', pointerId: 1, x: 130, y: 140, dx: 10, dy: -1 },
      ]);
    } finally {
      await browser.close();
    }
  });
});
