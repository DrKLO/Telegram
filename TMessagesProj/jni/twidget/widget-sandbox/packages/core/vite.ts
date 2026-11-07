// Vite plugin for hosts that embed the web package: builds the two in-page
// bundles on demand (`virtual:sandbox-bundle` for the QuickJS worker,
// `virtual:renderer-bundle` for the iframe / shadow renderer), serves the
// bundled libraries at /libs/ and sets the cross-origin isolation headers the
// SharedArrayBuffer bridge needs. Dev-only middleware (corpus, reference
// mode, native renderer prototypes) belongs to the app, see apps/web/vite.config.ts.

import { build, type BuildOptions } from 'esbuild';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Plugin } from 'vite';
import { rendererBuildOptions, sandboxBuildOptions } from './scripts/sandbox-build.ts';

const pkgRoot = path.dirname(fileURLToPath(import.meta.url));

/** Cross-origin isolation (COOP/COEP/CORP): required for SharedArrayBuffer (the sync query bridge). */
export const ISOLATION_HEADERS = {
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Embedder-Policy': 'require-corp',
  'Cross-Origin-Resource-Policy': 'same-origin',
};

const VIRTUAL: Record<string, () => BuildOptions> = {
  'virtual:sandbox-bundle': () => sandboxBuildOptions(pkgRoot),
  'virtual:renderer-bundle': () => rendererBuildOptions(pkgRoot),
};

async function bundleIife(options: BuildOptions): Promise<string> {
  const out = await build(options);
  return out.outputFiles![0].text;
}


export function widgetSandbox(): Plugin {
  return {
    name: 'widget-sandbox',
    config: () => ({
      worker: { format: 'es' },
      optimizeDeps: { exclude: ['@telegram/widget-sandbox-quickjs'] },
      server: { headers: ISOLATION_HEADERS },
      preview: { headers: ISOLATION_HEADERS },
    }),
    resolveId(id) {
      if (id in VIRTUAL) return '\0' + id;
    },
    async load(id) {
      const v = VIRTUAL[id.slice(1)];
      if (!id.startsWith('\0') || !v) return;
      const code = await bundleIife(v());
      return `export default ${JSON.stringify(code)};`;
    },
    handleHotUpdate({ file, server }) {
      if (/\/src\/(sandbox|renderer|shared)\//.test(file)) {
        for (const id of Object.keys(VIRTUAL)) {
          const mod = server.moduleGraph.getModuleById('\0' + id);
          if (mod) server.moduleGraph.invalidateModule(mod);
        }
        server.ws.send({ type: 'full-reload' });
      }
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = (req.url || '').split('?')[0];
        // Responses ended by any middleware bypass Vite's own header injection; the page is
        // cross-origin isolated, so nested documents and subresources need these too.
        for (const [k, v] of Object.entries(ISOLATION_HEADERS)) res.setHeader(k, v);
        next();
      });
    },
  };
}
