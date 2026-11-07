// The dev app: the web package's plugin (bundles, isolation headers) plus
// dev-only middleware — the repo corpus, reference mode (native rendering of the
// same file, never a product path) and the Blitz / litehtml wasm prototypes.

import { defineConfig } from 'vite';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { widgetSandbox } from '@telegram/widget-sandbox/vite';
import { REFERENCE_STYLESHEET, referenceSafeAreaCss } from '@telegram/widget-sandbox/shared/reference';

const appRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(appRoot, '..', '..');
const corpusDir = path.join(repoRoot, 'corpus');
/** The Blitz wasm renderer prototype (pnpm build:blitz-web), served at /blitz/ outside the module graph. Dev tooling. */
const blitzDir = path.join(repoRoot, 'apps', 'renderer-blitz', 'pkg-web');
/** The litehtml wasm renderer prototype (pnpm build:litehtml-web), served at /litehtml/ the same way. Dev tooling. */
const litehtmlDir = path.join(repoRoot, 'apps', 'renderer-litehtml', 'pkg-web');

/**
 * Reference mode: a fragment is rendered inside our (standards-mode) document in
 * the product; give it a doctype so the native comparison does not fall into quirks mode,
 * and the renderer's own stylesheet (the root and text defaults, the client-styled <button>,
 * no selection) and the host's theme variables with the framework's default palettes and the
 * reference text tokens (docs/sdk-v1.md §2.3, §2.4), so the comparison shows what the
 * profile removes rather than the root style, the button style or the theme.
 */
const withDoctype = (html: string, safeArea = '') => {
  const ua = `<style data-tw-ua>${REFERENCE_STYLESHEET}${safeArea}</style>`;
  const m = /^\s*<!doctype[^>]*>/i.exec(html);
  return m ? m[0] + ua + html.slice(m[0].length) : '<!DOCTYPE html>\n' + ua + html;
};

/** CSP for the dev-only reference mode. Not part of the product path. */
const REFERENCE_CSP =
  "default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; " +
  "img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; " +
  "child-src 'none'; worker-src 'none'; form-action 'none'; base-uri 'none'";

/** The conformance set ships with the framework (packages/conformance); the dev app lists it as the `conformance/` group. */
const conformanceDir = path.join(repoRoot, 'packages', 'conformance');
const groupDir = (group: string) => (group === 'conformance' ? conformanceDir : path.join(corpusDir, group));

function listCorpus(): string[] {
  const out: string[] = [];
  for (const group of ['compat', 'attacks', 'generated', 'htmlbubbles-ported', 'common', 'showcase', 'defaults', 'gestures', 'resize', 'safe-area', 'interactive', 'bot-demos', 'conformance']) {
    const base = groupDir(group);
    const walk = (dir: string) => {
      if (!fs.existsSync(dir)) return;
      for (const name of fs.readdirSync(dir).sort()) {
        const p = path.join(dir, name);
        if (fs.statSync(p).isDirectory()) { if (group !== 'conformance' || name === 'auto' || name === 'visual' || dir !== base) walk(p); }
        else if (name.endsWith('.html')) out.push(group + '/' + path.relative(base, p).split(path.sep).join('/'));
      }
    };
    walk(base);
  }
  return out;
}

/** Resolve a corpus-relative path (`<group>/…`), refusing anything outside its group. */
function corpusFile(rel: string): string | null {
  const decoded = decodeURIComponent(rel);
  const group = decoded.split('/')[0];
  const base = groupDir(group);
  const p = path.normalize(path.join(base, decoded.slice(group.length + 1)));
  if (!p.startsWith(base + path.sep) || !p.endsWith('.html') || !fs.existsSync(p)) return null;
  return p;
}

function serveWasmDir(dir: string, prefix: string, hint: string) {
  return (url: string, res: import('node:http').ServerResponse): boolean => {
    if (!url.startsWith(prefix)) return false;
    const name = url.slice(prefix.length);
    const p = path.join(dir, name);
    if (!/^[\w.-]+\.(js|wasm)$/.test(name) || !fs.existsSync(p)) { res.statusCode = 404; res.end(`not built: ${hint}`); return true; }
    res.setHeader('Content-Type', name.endsWith('.wasm') ? 'application/wasm' : 'text/javascript');
    res.end(fs.readFileSync(p));
    return true;
  };
}
const serveBlitz = serveWasmDir(blitzDir, '/blitz/', 'corepack pnpm build:blitz-web');
const serveLitehtml = serveWasmDir(litehtmlDir, '/litehtml/', 'corepack pnpm build:litehtml-web');

export default defineConfig({
  plugins: [
    widgetSandbox(),
    {
      name: 'twidget-dev',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          const url = (req.url || '').split('?')[0];
          if (url === '/api/corpus') {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify(listCorpus()));
            return;
          }
          if (serveBlitz(url, res) || serveLitehtml(url, res)) return;
          if (url.startsWith('/corpus/')) {
            const p = corpusFile(url.slice('/corpus/'.length));
            if (!p) return next();
            res.setHeader('Content-Type', 'text/plain; charset=utf-8');
            res.end(fs.readFileSync(p));
            return;
          }
          // Reference mode (dev tooling only): native rendering of repo corpus files.
          if (url.startsWith('/reference/')) {
            const p = corpusFile(url.slice('/reference/'.length));
            if (!p) return next();
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.setHeader('Content-Security-Policy', REFERENCE_CSP);
            // `?safeArea=bottom,cornerRight,cornerLeft` (the dev app's time control): the root variables a renderer sets for
            // the client's time, there before the widget's first script runs (its env() stays the browser's own).
            const area = (new URLSearchParams((req.url || '').split('?')[1] ?? '').get('safeArea') ?? '').split(',').map(Number);
            res.end(withDoctype(fs.readFileSync(p, 'utf8'), area.some((n) => n > 0) ? referenceSafeAreaCss({ bottom: area[0], cornerRight: area[1], cornerLeft: area[2] }) : ''));
            return;
          }
          next();
        });
      },
    },
  ],
  server: { port: 5173, strictPort: true, host: '127.0.0.1' },
})
