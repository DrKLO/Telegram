// esbuild options for the in-sandbox bundle, shared by the Vite plugin
// (packages/core/vite.ts) and the Node harness (scripts/sandbox-node.ts).

import type { BuildOptions, Plugin } from 'esbuild';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);

/**
 * linkedom's `import 'css-select'` → our wrapper; the wrapper's own
 * `import 'css-select'` → the real package (resolved from linkedom's
 * node_modules, so no extra dependency is needed).
 */
function cssSelectShim(root: string): Plugin {
  const shim = path.join(root, 'src/sandbox/css-select-shim.ts');
  const linkedomDir = path.dirname(require.resolve('linkedom/package.json', { paths: [root] }));
  const real = require.resolve('css-select', { paths: [linkedomDir] });
  return {
    name: 'css-select-shim',
    setup(build) {
      build.onResolve({ filter: /^css-select$/ }, (args) => ({ path: args.importer === shim ? real : shim }));
    },
  };
}

type Edit = [from: string | RegExp, to: string];

function edit(file: string, text: string, edits: Edit[]): string {
  for (const [from, to] of edits) {
    const count = typeof from === 'string' ? text.split(from).length - 1 : (text.match(new RegExp(from.source, from.flags.includes('g') ? from.flags : from.flags + 'g')) ?? []).length;
    if (count !== 1) throw new Error(`lazy-entity-tables: ${file}: expected one match of ${String(from).slice(0, 80)}, found ${count}`);
    text = text.replace(from, to);
  }
  return text;
}

export function utf16TreeModule(tree: Uint16Array): string {
  let text = '';
  for (let i = 0; i < tree.length; i += 4096) text += String.fromCharCode(...tree.subarray(i, i + 4096));
  return [
    `const HTML_DECODE_TREE = ${JSON.stringify(text)};`,
    'const buildHtmlDecodeTree = () => {',
    '  const tree = new Uint16Array(HTML_DECODE_TREE.length);',
    '  for (let i = 0; i < HTML_DECODE_TREE.length; i++) tree[i] = HTML_DECODE_TREE.charCodeAt(i);',
    '  return tree;',
    '};',
    'let lazyHtmlDecodeTree = null;',
    `export ${LAZY_TREE}`,
    'export const htmlDecodeTree = /* #__PURE__ */ htmlDecodeTreeLazy();',
    '',
  ].join('\n');
}

const LAZY_TREE = 'function htmlDecodeTreeLazy() { return lazyHtmlDecodeTree ?? (lazyHtmlDecodeTree = buildHtmlDecodeTree()); }';
const RESOLVE_TREE = 'startEntity(decodeMode) {\n        if (typeof this.decodeTree === "function") this.decodeTree = this.decodeTree();';

/**
 * The HTML entity decode tables of the `entities` copies inside the bundle (htmlparser2's,
 * parse5's and dom-serializer's) are built when the first character reference is decoded
 * instead of while the bundle loads, and the legacy decoders nothing calls are not made at
 * boot. Every edit must match exactly once, so a dependency update fails the build instead
 * of silently undoing it.
 */
export function lazyEntityTables(): Plugin {
  const transforms: [RegExp, (text: string, file: string) => string][] = [
    [/[\\/]entities[\\/]dist[\\/](esm[\\/])?decode\.js$/, (text, file) => edit(file, text, [
      ['startEntity(decodeMode) {', RESOLVE_TREE],
    ]) + '\nexport { htmlDecodeTreeLazy } from "./generated/decode-data-html.js";\n'],
    [/[\\/]htmlparser2[\\/]dist[\\/]esm[\\/]Tokenizer\.js$/, (text, file) => edit(file, text, [
      ['import { EntityDecoder, DecodingMode, htmlDecodeTree, xmlDecodeTree, } from "entities/decode";', 'import { EntityDecoder, DecodingMode, htmlDecodeTreeLazy, xmlDecodeTree, } from "entities/decode";'],
      ['new EntityDecoder(xmlMode ? xmlDecodeTree : htmlDecodeTree,', 'new EntityDecoder(xmlMode ? xmlDecodeTree : htmlDecodeTreeLazy,'],
    ])],
    [/[\\/]parse5[\\/]dist[\\/]tokenizer[\\/]index\.js$/, (text, file) => edit(file, text, [
      ["import { htmlDecodeTree, EntityDecoder, DecodingMode } from 'entities/decode';", "import { htmlDecodeTreeLazy, EntityDecoder, DecodingMode } from 'entities/decode';"],
      ['new EntityDecoder(htmlDecodeTree,', 'new EntityDecoder(htmlDecodeTreeLazy,'],
    ])],
    [/[\\/]entities[\\/]lib[\\/]esm[\\/]generated[\\/]decode-data-html\.js$/, (text, file) => edit(file, text, [
      ['export default new Uint16Array(', `let lazyHtmlDecodeTree = null;\nexport ${LAZY_TREE}\nconst buildHtmlDecodeTree = () => new Uint16Array(`],
    ])],
    [/[\\/]entities[\\/]lib[\\/]esm[\\/]decode\.js$/, (text, file) => edit(file, text, [
      ['import htmlDecodeTree from "./generated/decode-data-html.js";', 'import { htmlDecodeTreeLazy } from "./generated/decode-data-html.js";'],
      ['export { htmlDecodeTree, xmlDecodeTree, decodeCodePoint };', 'export { xmlDecodeTree, decodeCodePoint };'],
      ['startEntity(decodeMode) {', RESOLVE_TREE],
      ['const htmlDecoder = getDecoder(htmlDecodeTree);', 'let htmlDecoderLazy = null;\nconst htmlDecoder = (str, mode) => (htmlDecoderLazy ?? (htmlDecoderLazy = getDecoder(htmlDecodeTreeLazy())))(str, mode);'],
      ['const xmlDecoder = getDecoder(xmlDecodeTree);', 'let xmlDecoderLazy = null;\nconst xmlDecoder = (str, mode) => (xmlDecoderLazy ?? (xmlDecoderLazy = getDecoder(xmlDecodeTree)))(str, mode);'],
    ])],
  ];
  return {
    name: 'lazy-entity-tables',
    setup(build) {
      build.onLoad({ filter: /[\\/]entities[\\/]dist[\\/](esm[\\/])?generated[\\/]decode-data-html\.js$/ }, async (args) => {
        const tree: unknown = (await import(pathToFileURL(args.path).href)).htmlDecodeTree;
        if (!(tree instanceof Uint16Array)) throw new Error(`lazy-entity-tables: ${args.path}: htmlDecodeTree is not a Uint16Array`);
        return { contents: utf16TreeModule(tree), loader: 'js' };
      });
      build.onLoad({ filter: /[\\/](entities|htmlparser2|parse5)[\\/].*\.js$/ }, (args) => {
        const t = transforms.find(([re]) => re.test(args.path));
        if (!t) return undefined;
        return { contents: t[1](fs.readFileSync(args.path, 'utf8'), args.path), loader: 'js' };
      });
    },
  };
}

export function sandboxBuildOptions(root: string): BuildOptions {
  return {
    entryPoints: [path.join(root, 'src/sandbox/index.ts')],
    bundle: true,
    format: 'iife',
    write: false,
    minify: true,
    platform: 'neutral',
    mainFields: ['module', 'main'],
    target: 'es2022',
    logLevel: 'warning',
    alias: { canvas: path.join(root, 'src/sandbox/canvas-stub.cjs') },
    plugins: [cssSelectShim(root), lazyEntityTables()],
  };
}

/** `transport`: 'port' for the web host (MessageChannel), 'webkit' for WKWebView hosts, 'android' for android.webkit.WebView hosts. */
export function rendererBuildOptions(root: string, transport: 'port' | 'webkit' | 'android' = 'port'): BuildOptions {
  return {
    entryPoints: [path.join(root, 'src/renderer/renderer.ts')],
    bundle: true,
    format: 'iife',
    write: false,
    minify: true,
    platform: 'neutral',
    target: 'es2022',
    logLevel: 'warning',
    define: { __TW_TRANSPORT__: JSON.stringify(transport) },
  };
}

/** The validator as an IIFE for a trusted QuickJS context on native hosts (see src/host/validator-bundle.ts). */
export function validatorBuildOptions(root: string): BuildOptions {
  return {
    entryPoints: [path.join(root, 'src/host/validator-bundle.ts')],
    bundle: true,
    format: 'iife',
    write: false,
    minify: true,
    // 'browser' picks css-tree's prebuilt data/version modules (its lib/ uses node's createRequire).
    platform: 'browser',
    target: 'es2022',
    logLevel: 'warning',
  };
}
