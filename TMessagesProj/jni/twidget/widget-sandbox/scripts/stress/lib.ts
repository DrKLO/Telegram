// Shared pieces of the stress kit's tooling (build.ts, run-web.ts): the widget page around a load, sizes,
// the manifest's shape, the dev server, and the in-page probe that reads `data-stress` from the validated ops.

import { spawn } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
import type { Page } from '@playwright/test';
import { LOADS, STRICT_LIMITS, type LimitKey, type Load } from './loads';

export const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const STRESS = path.join(ROOT, 'packages', 'stress');
export const WIDGETS = path.join(STRESS, 'widgets');

/** A mini-widget (docs/tgw-format.md): the server refuses more than 4096 B after zlib at level 9; the kit keeps the conformance set's margin. */
export const SIZE_LIMIT = { zlib: 3800, raw: 32_768 };
/** Every widget fits a bubble: at most 560 px tall at 320 px wide (the conformance set's rule, under the 600 px cap). */
export const MAX_HEIGHT = { width: 320, height: 560 };
export const sizeOf = (html: string) => { const b = Buffer.from(html); return { raw: b.length, zlib: zlib.deflateSync(b, { level: 9 }).length }; };

export interface ManifestWidget {
  id: string; file: string; load: string; level: number; value: number;
  /** Boxes on screen with a blurred shadow, filter, backdrop-filter or opacity < 1 (§3), and of those with backdrop-filter. */
  heavyBoxes: number; backdropBoxes: number;
  /** Boxes that change every frame. */
  changedBoxes: number;
  /** Rough counts of what the strict limits measure, and the limits this level crosses. */
  estimates: Partial<Record<LimitKey, number>>;
  crosses: string[];
  /** Measured by build.ts through the sandbox (Chromium, shadow renderer, compat): nodes, the largest validated batch, height at 320 px. */
  measured?: { nodes: number; maxBatchOps: number; height: number };
  size: { raw: number; zlib: number };
}
export interface Manifest {
  version: 2;
  limits: typeof STRICT_LIMITS;
  run: { warmupMs: number; measureMs: number; width: number };
  loads: { id: string; title: string; stresses: string; param: string; unit: string; levels: number[]; fit: 'heavy' | 'plain' | null; widgets: string[] }[];
  widgets: ManifestWidget[];
}
export const DEFAULT_RUN = { warmupMs: 2000, measureMs: 10_000, width: 390 };

const pad = (n: number) => String(n).padStart(6, '0');
export const widgetId = (load: Load, value: number) => `${load.id}/${value}`;
export const widgetFile = (load: Load, value: number) => `widgets/${load.id}/${pad(value)}.html`;

/** The measuring loop every widget carries: its own requestAnimationFrame rate, published as `data-stress` on <html>. */
const HARNESS = `(function(){var de=document.documentElement,h=document.getElementById('h'),lb=h.textContent,n=0,t0=0,lt=0,lp=0,mx=0,j=0,fs=0,fc=0,fps=0;
function pub(st,t){de.setAttribute('data-stress',JSON.stringify({v:1,id:ID,level:LV,value:N,status:st,t:Math.round(t),frames:n,fps:fps,maxGap:Math.round(mx),jank:j}));mx=0}
function loop(t){if(!t0)t0=lt=lp=fs=t;var g=t-lt;lt=t;if(g>mx)mx=g;if(g>50)j++;n++;fc++;if(t-fs>=1000){fps=Math.round(fc*1000/(t-fs));fc=0;fs=t}
if(typeof step=='function')step(t-t0);
if(t-lp>=500){lp=t;h.firstChild.data=lb+' \\u00b7 '+fps+' fps';pub('running',t-t0)}requestAnimationFrame(loop)}
pub('setup',0);requestAnimationFrame(loop)})();`;

/** One level of one load as a widget. */
export function widgetHtml(load: Load, level: number, value: number): string {
  const label = `${load.title} · ${value} ${load.unit}`;
  const meta = { id: widgetId(load, value), load: load.id, level, param: load.param, value, unit: load.unit };
  return `<!doctype html>
<html><head><meta charset="utf-8"><title>stress: ${label}</title>
<!-- stress ${JSON.stringify(meta)} -->
<style>body{margin:0;font:12px system-ui,sans-serif}#h{padding:5px 8px;color:var(--tg-theme-hint-color);font-variant-numeric:tabular-nums;white-space:nowrap;overflow:hidden}#f{position:relative;height:300px;overflow:hidden}${load.css}</style>
</head><body><div id="h">${label}</div><div id="f"></div>
<script>
var ID=${JSON.stringify(meta.id)},LV=${level},N=${value},F=document.getElementById('f'),W=F.clientWidth||300;
function mk(n,c){for(var a=[],i=0;i<n;i++){var d=document.createElement('div');d.className=c;F.appendChild(d);a.push(d)}return a}
${load.setup};
${load.step ? `function step(t){${load.step}}\n` : ''}${HARNESS}
</script>
</body></html>
`;
}

/** The strict limits a level crosses: the table's estimates, with the nodes and the largest batch measured through the sandbox where the build has them. */
export function crossesOf(w: Pick<ManifestWidget, 'estimates' | 'heavyBoxes' | 'backdropBoxes' | 'measured'>): string[] {
  const all: Partial<Record<LimitKey, number>> = { ...w.estimates, heavyBoxes: w.heavyBoxes, backdropBoxes: w.backdropBoxes };
  // Measured through the sandbox: the shim hands a long flush over in parts (v0.6.0), so the largest batch is the measured one.
  if (w.measured) { all.nodes = w.measured.nodes; all.opsPerBatch = w.measured.maxBatchOps; }
  return (Object.keys(STRICT_LIMITS) as LimitKey[]).filter((k) => (all[k] ?? 0) > STRICT_LIMITS[k]).map((k) => `${k} ${all[k]} > ${STRICT_LIMITS[k]}`);
}

/** Every level of every load, with what the manifest says about it. */
export function generate(): { widgets: (ManifestWidget & { html: string })[]; manifest: Omit<Manifest, 'widgets'> } {
  const widgets: (ManifestWidget & { html: string })[] = [];
  for (const load of LOADS) {
    load.levels.forEach((value, level) => {
      const html = widgetHtml(load, level, value);
      const w = { id: widgetId(load, value), file: widgetFile(load, value), load: load.id, level, value, heavyBoxes: load.heavy?.(value) ?? 0, backdropBoxes: load.backdrop?.(value) ?? 0, changedBoxes: load.changed?.(value) ?? 0, estimates: load.est(value), crosses: [] as string[], size: sizeOf(html), html };
      w.crosses = crossesOf(w);
      widgets.push(w);
    });
  }
  return {
    widgets,
    manifest: {
      version: 2, limits: STRICT_LIMITS, run: DEFAULT_RUN,
      loads: LOADS.map((l) => ({ id: l.id, title: l.title, stresses: l.stresses, param: l.param, unit: l.unit, levels: l.levels, fit: l.fit ?? null, widgets: l.levels.map((v) => widgetId(l, v)) })),
    },
  };
}

export function readManifest(): Manifest {
  return JSON.parse(fs.readFileSync(path.join(STRESS, 'manifest.json'), 'utf8'));
}

/** The measuring configuration of CLIENTS.md as the dev app's URL parameters (unknown ones are ignored until the app reads them). */
export const MEASURE_PARAMS = { policy: 'compat', cpuLimitMs: '500', cpuQuotaMs: '10000', cpuQuotaWindowMs: '1000', mainThreadQuotaMs: '10000', maxTimers: '256', memoryLimitBytes: String(64 * 1024 * 1024) };

/** Runs `fn` on the dev app: `origin`, or a dev server started for it on a free port (stopped afterwards). */
export async function withDevServer<T>(origin: string | undefined, fn: (origin: string) => Promise<T>): Promise<T> {
  let server: ReturnType<typeof spawn> | undefined;
  if (!origin) {
    const port = await new Promise<number>((resolve) => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = (s.address() as net.AddressInfo).port; s.close(() => resolve(p)); }); });
    origin = `http://127.0.0.1:${port}`;
    server = spawn('corepack', ['pnpm', 'dev', '--port', String(port)], { cwd: ROOT, stdio: 'ignore', detached: true });
    const t0 = Date.now();
    for (;;) {
      try { if ((await fetch(origin)).ok) break; } catch {}
      if (Date.now() - t0 > 90_000) { process.kill(-server.pid!); throw new Error(`the dev server did not start on ${origin}`); }
      await new Promise((r) => setTimeout(r, 500));
    }
  }
  try { return await fn(origin); }
  finally { if (server) process.kill(-server.pid!); }
}

/** Opens the dev app with `params` on `renderer` and waits for its harness API (`window.__tw`). */
export async function openDevApp(page: Page, origin: string, renderer: string, params: Record<string, string>) {
  const q = new URLSearchParams({ theme: 'light', features: 'webAnimations', ...params });
  if (renderer !== 'iframe') q.set(renderer, '1');
  await page.goto(`${origin}/?${q}`);
  await page.waitForFunction(() => !!(window as any).__tw, null, { timeout: 60_000 });
}

/**
 * Loads a widget into the dev app at `width` and taps what any host can see: the validated ops (the last
 * `data-stress` on the element the `el` op adopted as <html>, the ops per batch) and the renderer's size reports.
 */
export async function loadWidget(page: Page, html: string, name: string, width: number) {
  await page.evaluate(({ html, name, width }) => {
    const wrap = document.querySelector('#sandbox-wrap') as HTMLElement;
    (wrap.closest('.chat') as HTMLElement).style.width = width + 'px';
    const bubble = wrap.closest('.bubble') as HTMLElement;
    bubble.style.padding = '0';
    bubble.style.borderRadius = '0';
    bubble.style.boxShadow = 'none';
    const tw = (window as any).__tw;
    const s: any = ((window as any).__stress = { html: null, attr: null, height: 0, batches: 0, ops: 0, maxBatch: 0, t0: performance.now(), loadedAt: 0 });
    tw.load(html, name);
    const host = tw.host;
    host.element.setAttribute('data-stress-el', '');
    const onRenderer = host.onRenderer;
    host.onRenderer = function (msg: any) {
      if (msg?.type === 'size' && Number.isFinite(msg.height)) s.height = msg.height;
      return onRenderer.call(this, msg);
    };
    const r = host.renderer;
    const send = r.send.bind(r);
    r.send = (m: any) => {
      if (m?.type === 'ops') {
        s.batches++;
        s.ops += m.ops.length;
        if (m.ops.length > s.maxBatch) s.maxBatch = m.ops.length;
        for (const op of m.ops) {
          if (op.op === 'el' && op.adopt === 'html') {
            s.html = op.id;
            for (const [n, v] of op.attrs) if (n === 'data-stress') s.attr = v;
          } else if (op.op === 'attr' && op.id === s.html && op.name === 'data-stress') s.attr = op.value;
        }
      }
      return send(m);
    };
  }, { html, name, width });
}

export interface Published { v: number; id: string; level: number; value: number; status: string; t: number; frames: number; fps: number; maxGap: number; jank: number }
export async function published(page: Page): Promise<Published | null> {
  return page.evaluate(() => { const a = (window as any).__stress?.attr; try { return a ? JSON.parse(a) : null; } catch { return null; } });
}
