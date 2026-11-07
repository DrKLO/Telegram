import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { chromium } from '@playwright/test';
import { rendererBuildOptions } from '../../scripts/sandbox-build.ts';
import { buildSrcdoc, makeNonce } from '../../src/renderer/srcdoc.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

describe('renderer: canvas sizes', () => {
  it("holds a widget's canvases to 4096² pixels together, by attributes, the size command and removal", async () => {
    const out = await build(rendererBuildOptions(root, 'webkit'));
    const browser = await chromium.launch();
    try {
      const page = await browser.newPage();
      // helpers as page script: tsx would wrap named functions inside page.evaluate in a helper the page does not have
      await page.addInitScript({ content: 'window.__messages = []; window.webkit = {messageHandlers: {tw: {postMessage: function(s) {window.__messages.push(JSON.parse(s));}}}};' +
        'window.__send = function(ops) { window.__twReceive(JSON.stringify({type: "ops", ops: ops})); };' +
        'window.__size = function(id) { var c = document.getElementById(id); return [c.width, c.height]; };' });
      const url = 'http://renderer.invalid/';
      await page.route('**/*', (route) => route.request().url() === url
        ? route.fulfill({ body: buildSrcdoc(out.outputFiles![0].text, makeNonce()), contentType: 'text/html' })
        : route.abort());
      await page.goto(url);
      const result = await page.evaluate(() => {
        const w = window as any;
        w.__send([
          { op: 'el', id: 2, tag: 'html', adopt: 'html', attrs: [] }, { op: 'ins', parent: 1, id: 2, before: null },
          { op: 'el', id: 3, tag: 'body', adopt: 'body', attrs: [] }, { op: 'ins', parent: 2, id: 3, before: null },
          // 3000 × 3000 fits; a second one does not, past the first attribute that makes it too large
          { op: 'el', id: 4, tag: 'canvas', attrs: [['id', 'a'], ['width', '3000'], ['height', '3000']] }, { op: 'ins', parent: 3, id: 4, before: null },
          { op: 'el', id: 5, tag: 'canvas', attrs: [['id', 'b'], ['width', '3000'], ['height', '3000']] }, { op: 'ins', parent: 3, id: 5, before: null },
          // the size command a script's `canvas.width = …` sends
          { op: 'el', id: 6, tag: 'canvas', attrs: [['id', 'c']] }, { op: 'ins', parent: 3, id: 6, before: null },
          { op: 'canvas', id: 6, cmds: [['r', 4000, 4000]] },
        ]);
        const crowded = { a: w.__size('a'), b: w.__size('b'), c: w.__size('c') };
        // a removed canvas gives its pixels back
        w.__send([{ op: 'rm', id: 4 }, { op: 'canvas', id: 6, cmds: [['r', 4000, 4000]] }]);
        const freed = w.__size('c');
        return { crowded, freed, violations: w.__messages.filter((m: any) => m.type === 'violation').length };
      });
      assert.deepEqual(result.crowded.a, [3000, 3000]);
      assert.ok(result.crowded.b[0] * result.crowded.b[1] <= 4096 * 4096 - 3000 * 3000, `b: ${result.crowded.b}`);
      assert.deepEqual(result.crowded.c, [300, 150]);
      assert.deepEqual(result.freed, [4000, 4000]);
      assert.ok(result.violations >= 2, `${result.violations} violations`);
    } finally {
      await browser.close();
    }
  });
});
