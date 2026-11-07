import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { describe, it } from 'node:test';
import { build } from 'esbuild';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { sandboxBuildOptions, utf16TreeModule } from '../../scripts/sandbox-build.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

async function bundle(lazy: boolean, minify = true): Promise<string> {
  const options = sandboxBuildOptions(root);
  const plugins = (options.plugins ?? []).filter((p) => lazy || p.name !== 'lazy-entity-tables');
  const out = await build({ ...options, plugins, minify });
  return out.outputFiles![0].text;
}

function packageDir(name: string, from: string): string {
  let dir = path.dirname(createRequire(from).resolve(name));
  while (!(fs.existsSync(path.join(dir, 'package.json')) && JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8')).name === name)) dir = path.dirname(dir);
  return dir;
}

function treeFiles(): string[] {
  const linkedom = packageDir('linkedom', import.meta.url);
  const htmlparser2 = packageDir('htmlparser2', path.join(linkedom, 'package.json'));
  const parse5 = packageDir('parse5', import.meta.url);
  return [
    path.join(packageDir('entities', path.join(htmlparser2, 'package.json')), 'dist/esm/generated/decode-data-html.js'),
    path.join(packageDir('entities', path.join(parse5, 'package.json')), 'dist/generated/decode-data-html.js'),
  ];
}

const ENTITIES = [
  '&amp;', '&lt;', '&gt;', '&quot;', '&apos;', '&nbsp;', '&copy', '&copy;', '&reg', '&AMP', '&amp', '&notin;', '&notit;', '&not',
  '&Afr;', '&Bopf;', '&Zscr;', '&fjlig;', '&NotNestedGreaterGreater;', '&CounterClockwiseContourIntegral;', '&ThickSpace;',
  '&acE;', '&bne;', '&nvlt;', '&#65;', '&#x1F600;', '&#0;', '&#x110000;', '&#128;', '&#xD800;', '&unknown;', '&;', '&#;', '&#x;',
  '&amp;amp;', '&ampx', '&lt3', '&LT;', '&Gt;', '&nGt;', '&emsp13;', '&frac12', '&frac12;', '&times', '&divide;', '&euro;',
];

function widget(): string {
  const text = ENTITIES.join(' ');
  const attr = ENTITIES.map((e, i) => `data-e${i}="${e}"`).join(' ');
  return `<p id=t>${text}</p><a id=a href="?x=1&copy=2&amp;y=3&notit" ${attr}>link</a><textarea>${text}</textarea><title>${text}</title>
<script>
var p = document.getElementById('t');
var d = document.createElement('div');
d.innerHTML = ${JSON.stringify(text)} + '<b title="' + ${JSON.stringify(ENTITIES.slice(0, 20).join(''))} + '">x</b>';
document.body.appendChild(d);
var parsed = new DOMParser().parseFromString('<p>' + ${JSON.stringify(text)} + '</p>', 'text/html');
console.log(JSON.stringify([p.textContent, d.textContent, d.firstElementChild ? d.lastElementChild.getAttribute('title') : '', parsed.body.textContent, document.getElementById('a').getAttribute('href')]));
</script>`;
}

describe('lazy entity tables', () => {
  it('the UTF-16 tree modules rebuild exactly the trees entities ships, surrogate pairs included', async () => {
    for (const file of treeFiles()) {
      const original: Uint16Array = (await import(pathToFileURL(file).href)).htmlDecodeTree;
      const source = utf16TreeModule(original).replace(/^export /gm, '');
      const rebuilt = new Function(`${source}; return htmlDecodeTreeLazy();`)() as Uint16Array;
      assert.equal(rebuilt.length, original.length, file);
      assert.deepEqual(Array.from(rebuilt), Array.from(original), file);
      const lossy = Uint16Array.from(String.fromCharCode(...original), (c) => c.charCodeAt(0));
      assert.ok(lossy.length < original.length, 'iterating the string by code points would merge surrogate pairs');
    }
  });

  it('no decode table is built while the bundle loads', async () => {
    const text = await bundle(true, false);
    assert.doesNotMatch(text, /^\s*var htmlDecodeTree\d* = /m);
    assert.doesNotMatch(text, /^\s*var (htmlDecoder|xmlDecoder)\d* = getDecoder\(/m);
    assert.doesNotMatch(text, /^\s*var \w+ = (\/\* @__PURE__ \*\/ )?decode(Base64|TrieDict)\("QR08/m);
    const eager = await bundle(false, false);
    assert.match(eager, /^\s*var htmlDecodeTree\d* = /m);
  });

  it('widgets get the same ops, text and attributes with and without the lazy tables', async () => {
    const lazy = await bundle(true);
    const eager = await bundle(false);
    const html = widget();
    const clock = () => { let t = 1000; return () => (t += 16); };
    const bytes = () => { let s = 3; return (n: number) => Array.from({ length: n }, () => (s = (s * 1103515245 + 12345) & 0xff)); };
    for (const binaryOps of [false, true]) {
      const a = await runWidget(html, { bundle: lazy, now: clock(), random: bytes(), binaryOps });
      const b = await runWidget(html, { bundle: eager, now: clock(), random: bytes(), binaryOps });
      assert.ok(a.ops.length > 0);
      assert.deepEqual(a.ops, b.ops);
      assert.deepEqual(a.logs, b.logs);
      assert.ok(a.logs.some((l) => l.text.includes('©') && l.text.includes('𝔄')), JSON.stringify(a.logs).slice(0, 300));
    }
  });
});
