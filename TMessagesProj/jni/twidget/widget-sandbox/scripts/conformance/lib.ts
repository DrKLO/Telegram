// Shared pieces of the conformance tooling: widget files and their header,
// stages, actions (scripted input) and the pixel diff. Used by build.ts
// (references, manifest) and run-web.ts (the web runner).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Page } from '@playwright/test';
import { PNG } from 'pngjs';
import type { ContentSafeArea } from '../../packages/core/src/shared/sdk-policy.ts';

/** path.relative with / separators: what the manifest and the channel table store, on every OS. */
export const posixRelative = (from: string, to: string) => path.relative(from, to).split(path.sep).join('/');

export const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const CONF = path.join(ROOT, 'packages', 'conformance');
export const SETS = ['auto', 'visual'] as const;
export type SetName = (typeof SETS)[number];

/**
 * The bubble is at most 600 px tall on every client and on the server (docs/integration.md, "Bubble sizing").
 * Every widget stays within `height` at a narrow bubble width, which leaves room for platform fonts and an
 * automatic widget's list of failures; a header's "tall": true exempts a widget that tests the cap
 * (13-max-height). build.ts checks the visual set and the client-SDK group drawn natively; heights.ts checks
 * every widget through the sandbox (drawn natively, an automatic widget also lists the out-of-profile checks
 * Chromium fails, which makes it taller than on a client).
 */
export const MAX_HEIGHT = { width: 320, height: 560 };

/** Default stage sizes (CSS px) and the auto set's grid. */
export const STAGE = { auto: { w: 80, h: 60 }, visual: { w: 150, h: 90 } };
export const AUTO_GRID = { left: 8, top: 8, dx: 88, dy: 68, perRow: 4 };
/** The pixel rule lives in the package (packages/conformance/tools/diff.mjs), shared with every client's runner. */
export { DIFF, judgeStage } from '../../packages/conformance/tools/diff.mjs';

/**
 * Scripted input, in widget CSS px or by target name (the widget publishes
 * `targets[name]` = [x, y, w, h] from its own layout, so runners need no
 * selectors and work on canvas renderers too).
 */
export type Point = string | [number, number];
/** `ids`: catalogue IDs whose checks depend on this action; a runner that cannot deliver it reports them as skipped. */
export type Action = ActionKind & { ids?: string[] };
type ActionKind =
  | { tap: Point }
  | { dblclick: Point }
  | { rightclick: Point }
  | { hover: Point }
  | { press: Point; ms: number }
  | { drag: Point; to: Point | [number, number]; by?: boolean; steps?: number }
  | { key: string }
  | { wheel: Point; dy: number }
  | { theme: 'light' | 'dark' }
  | { visible: boolean }
  | { wait: number };

/** Host-side expectations a runner checks itself (the widget cannot observe them). */
export interface HostCheck {
  id: string;
  name: string;
  /** Bridge calls the host must have seen: method and first argument, exact count. */
  bridge?: { method: string; arg?: string; count: number };
  /** Needs a trusted user gesture (synthetic taps of the canvas renderers cannot grant one): skipped there. */
  gesture?: boolean;
  /** Bubble height reported by the host, CSS px. */
  height?: { min?: number; max?: number };
  /** 'chat': the chat's input field (focused by the runner before the widget loads) still has the focus once the widget has run its own checks, before any input. */
  focus?: 'chat';
  xfail?: string;
}

/**
 * How the test bot sends a widget and where the tester looks at it (layer 233: docs/tgw-format.md). `kind` is the
 * message context the client must report there (WIDGET_MESSAGE_KINDS in sdk-policy.ts); run-web gives the dev app's
 * mock bot the first spec's kind and data. The rest are the inputMediaWidget fields the bot sends with
 * (`sendWidget`); `chat: 'group'` = into the test group (else the tester's private chat), `forward` = the tester
 * forwards it and looks at the forward, `inline` = the bot's inline mode returns it for the query `conf-preview`
 * and the tester looks at the list of results.
 */
export interface MessageSpec {
  kind: 'private' | 'group' | 'via-bot' | 'no-queries' | 'live-copy' | 'copy' | 'preview';
  allow_queries?: boolean;
  fwd_allow_queries?: boolean;
  shared_widget?: boolean;
  chat_data?: string;
  public_data?: string;
  chat?: 'private' | 'group';
  forward?: boolean;
  inline?: boolean;
  /** Where the tester looks, when it is not obvious from the rest. */
  view?: string;
}

/**
 * What the test bot does on a widget_query or shared_widget_query with exactly this data
 * (scripts/conformance/test-bot.ts, Bot API 10.4): edit the message's state (editMessageWidgetData, by chat and
 * message_id or by inline_message_id for a via-bot message), edit the shared widget (editSharedWidgetData by
 * widget_id, with cache_time), then answer with `result` (answerWidgetQuery's data). `repeat` runs the edits again
 * every `every` s, `times` times in all (a ticker). Templates in the strings: {n} counts the requests with this data
 * per message (per widget_id for a copy), {from} is who pressed (first_name), {time} the bot's HH:MM:SS.
 */
export interface BotAction {
  result?: string;
  edit?: { chat_data?: string; public_data?: string };
  editShared?: { public_data: string; cache_time?: number };
  repeat?: { every: number; times: number };
}

export interface WidgetMeta {
  title: string;
  /** How the test bot sends it, one entry per post (default: a plain send to the tester's private chat). */
  message?: MessageSpec[];
  /** What the test bot answers to the widget's requests, by their exact data. */
  bot?: Record<string, BotAction>;
  /** One line for a human tester (visual set) or for the report. */
  hint?: string;
  actions?: Action[];
  host?: HostCheck[];
  /** Run in these themes (default: light; auto widgets with theme stages: both). */
  themes?: ('light' | 'dark')[];
  /** Catalogue IDs judged by a human only (visual set), beyond the stage covers. */
  covers?: string[];
  /** Client-SDK group: what the client must do, in one line (the hint says what the tester does). */
  expect?: string;
  /** The section of docs/sdk-v1.md the test checks. */
  contract?: string;
  /** Not runnable yet: what it waits for (e.g. the server). */
  pending?: string;
  /** Taller than the bubble's cap on purpose (13-max-height tests the clip): exempt from the build's height gate. */
  tall?: boolean;
  /** No harness CSS (the harness script and the stage geometry only): the widget tests the renderer's own defaults (the UA layer). */
  bare?: boolean;
  /**
   * What the client draws over this widget (docs/sdk-v1.md §2.17): a content safe area, in CSS px (the band of the message
   * time at the bottom, its corner). The test bot sends the widget without a caption, so a client draws its own time over
   * it and passes its own numbers; the reference runner gives the dev app these, and the reference picture is drawn with
   * them and the reference time over the corner. The widget's checks compare with what its host passed, never with these.
   */
  safeArea?: Partial<ContentSafeArea>;
  /**
   * The host must stop this widget (a counted limit under strict, docs/decisions.md "Strict limits from the start"): its
   * covers pass when the runtime dies with a reason that starts with this text, and fail on any other end.
   */
  stops?: string;
}

export interface StageInfo {
  name: string;
  w: number;
  h: number;
  covers: string[];
  themes: ('light' | 'dark')[];
  /** Max differing pixel share for this stage (default DIFF.maxRatio). */
  maxDiff?: number;
  /** Has a <template data-ref> (reftest) instead of a self-render reference. */
  reftest: boolean;
  /** Expected to differ until something lands (catalogue-level xfail). */
  xfail?: string;
  /**
   * Colours that carry the feature (e.g. the orange of a text shadow): the
   * stage must show about as many pixels of each as the reference. For text
   * stages, where glyph shapes differ between platforms but the feature's
   * colour does not.
   */
  colors?: string[];
  /**
   * Visual set: the stage's template illustrates motion or a state after input (the frames a box moves between, the end
   * of a transition) rather than a frame the widget draws on load; picture-check.ts leaves the stage out.
   */
  illustration?: boolean;
}

export interface Widget {
  set: SetName;
  stem: string;
  id: string;
  file: string;
  html: string;
  meta: WidgetMeta;
  stages: StageInfo[];
}

export function listWidgets(only?: string): Widget[] {
  const out: Widget[] = [];
  for (const set of SETS) {
    const dir = path.join(CONF, set);
    if (!fs.existsSync(dir)) continue;
    // One level of groups: visual/sdk/01-links.html is `visual/sdk/01-links`.
    const files = fs.readdirSync(dir).sort().flatMap((f) => (fs.statSync(path.join(dir, f)).isDirectory() ? fs.readdirSync(path.join(dir, f)).sort().map((g) => `${f}/${g}`) : [f]));
    for (const f of files) {
      if (!f.endsWith('.html')) continue;
      const stem = f.slice(0, -5);
      const id = `${set}/${stem}`;
      if (only && !only.split(',').some((o) => id.includes(o))) continue;
      out.push(parseWidget(set, stem, fs.readFileSync(path.join(dir, f), 'utf8')));
    }
  }
  return out;
}

export function parseWidget(set: SetName, stem: string, html: string): Widget {
  const id = `${set}/${stem}`;
  const m = /<!--\s*conformance\s*(\{[\s\S]*?\})\s*-->/.exec(html);
  if (!m) throw new Error(`${id}: no <!-- conformance {…} --> header`);
  let meta: WidgetMeta;
  try { meta = JSON.parse(m[1]); } catch (e) { throw new Error(`${id}: header is not JSON: ${(e as Error).message}`); }
  const stages: StageInfo[] = [];
  const attr = (tag: string, name: string) => new RegExp(`\\s${name}="([^"]*)"`).exec(tag)?.[1];
  for (const sm of html.matchAll(/<[a-zA-Z][\w-]*\s[^>]*\bdata-stage="([^"]+)"[^>]*>/g)) {
    const tag = sm[0];
    const name = sm[1];
    const size = attr(tag, 'data-size');
    const def = STAGE[set];
    const [w, h] = size ? size.split('x').map(Number) : [def.w, def.h];
    const themes = (attr(tag, 'data-themes') ?? 'light').split(/\s+/).filter(Boolean) as ('light' | 'dark')[];
    const covers = (attr(tag, 'data-covers') ?? '').split(/\s+/).filter(Boolean);
    const maxDiff = attr(tag, 'data-max-diff');
    const colors = (attr(tag, 'data-colors') ?? '').split(/\s+/).filter(Boolean);
    stages.push({ name, w, h, covers, themes, maxDiff: maxDiff ? Number(maxDiff) : undefined, reftest: html.includes(`data-ref="${name}"`), xfail: attr(tag, 'data-xfail'), colors: colors.length ? colors : undefined, illustration: /\sdata-illustration(?=[\s=>])/.test(tag) || undefined });
  }
  return { set, stem, id, file: path.join(CONF, set, stem + '.html'), html, meta, stages };
}

/** Auto set: the fixed position of stage i (CSS px, relative to the widget's top-left). */
export function autoStagePos(i: number): [number, number] {
  return [AUTO_GRID.left + (i % AUTO_GRID.perRow) * AUTO_GRID.dx, AUTO_GRID.top + Math.floor(i / AUTO_GRID.perRow) * AUTO_GRID.dy];
}

export interface Published {
  v: number;
  id: string;
  mode: string;
  status: 'running' | 'awaiting' | 'done';
  n: Record<string, number>;
  checks: [string, string, string, string][];
  targets?: Record<string, [number, number, number, number]>;
  stages?: Record<string, [number, number, number, number]>;
}

/** Where to send input: page coordinates of the widget's origin, and how to deliver a tap (canvas renderers use __tw.tap). */
export interface InputSurface {
  origin: () => Promise<{ x: number; y: number }>;
  setTheme?: (t: 'light' | 'dark') => Promise<void>;
  setVisible?: (v: boolean) => Promise<void>;
  /** Canvas renderers: no DOM to click; only taps are delivered (through the renderer's own hit test). */
  tapOnly?: (x: number, y: number) => Promise<void>;
}

function resolve(p: Point, pub: Published | null): [number, number] {
  if (Array.isArray(p)) return p;
  const r = pub?.targets?.[p];
  if (!r) throw new Error(`no target "${p}" published`);
  return [r[0] + r[2] / 2, r[1] + r[3] / 2];
}

/** Perform the scripted actions. Returns the actions that could not be delivered on this surface (with the catalogue IDs they feed). */
export async function perform(page: Page, actions: Action[], surface: InputSurface, published: () => Promise<Published | null>): Promise<{ skipped: string[]; ids: string[]; indices: number[] }> {
  const skipped: string[] = [];
  const ids: string[] = [];
  const indices: number[] = [];
  const skip = (a: Action, what: string) => { skipped.push(what); ids.push(...(a.ids ?? [])); indices.push(actions.indexOf(a)); };
  for (const a of actions) {
    const pub = await published();
    const o = await surface.origin();
    const at = (p: Point) => { const [x, y] = resolve(p, pub); return [o.x + x, o.y + y] as [number, number]; };
    if ('wait' in a) { await page.waitForTimeout(a.wait); continue; }
    if ('theme' in a) { if (surface.setTheme) await surface.setTheme(a.theme); else skip(a, 'theme'); await page.waitForTimeout(150); continue; }
    if ('visible' in a) { if (surface.setVisible) await surface.setVisible(a.visible); else skip(a, 'visible'); await page.waitForTimeout(150); continue; }
    if (surface.tapOnly) {
      if ('tap' in a) { const [x, y] = resolve(a.tap, pub); await surface.tapOnly(x, y); await page.waitForTimeout(350); }
      else skip(a, Object.keys(a).filter((k) => k !== 'ids')[0]);
      continue;
    }
    if ('tap' in a) { const [x, y] = at(a.tap); await page.mouse.click(x, y); }
    else if ('dblclick' in a) { const [x, y] = at(a.dblclick); await page.mouse.dblclick(x, y); }
    else if ('rightclick' in a) { const [x, y] = at(a.rightclick); await page.mouse.click(x, y, { button: 'right' }); }
    else if ('hover' in a) { const [x, y] = at(a.hover); await page.mouse.move(x, y, { steps: 4 }); }
    else if ('press' in a) { const [x, y] = at(a.press); await page.mouse.move(x, y); await page.mouse.down(); await page.waitForTimeout(a.ms); await page.mouse.up(); }
    else if ('drag' in a) {
      const [x0, y0] = at(a.drag);
      let x1: number, y1: number;
      if (a.by) { const d = a.to as [number, number]; x1 = x0 + d[0]; y1 = y0 + d[1]; } else [x1, y1] = at(a.to);
      await page.mouse.move(x0, y0);
      await page.mouse.down();
      await page.mouse.move(x1, y1, { steps: a.steps ?? 10 });
      await page.mouse.up();
    } else if ('key' in a) await page.keyboard.press(a.key);
    else if ('wheel' in a) { const [x, y] = at(a.wheel); await page.mouse.move(x, y); await page.mouse.wheel(0, a.dy); }
    // Let the event cross the worker and the widget react before the next gesture (and keep taps apart from double-clicks).
    await page.waitForTimeout(Number(process.env.CONF_GAP ?? 350));
  }
  return { skipped, ids, indices };
}

/** Crop a CSS-px rect out of a DPR-scaled PNG whose widget origin is at device pixel (ox, oy). */
export function crop(png: PNG, x: number, y: number, w: number, h: number, dpr: number, ox = 0, oy = 0): PNG {
  const out = new PNG({ width: Math.round(w * dpr), height: Math.round(h * dpr) });
  out.data.fill(255);
  const sx = ox + Math.round(x * dpr), sy = oy + Math.round(y * dpr);
  const cw = Math.max(0, Math.min(out.width, png.width - sx)), ch = Math.max(0, Math.min(out.height, png.height - sy));
  if (cw > 0 && ch > 0 && sx >= 0 && sy >= 0) PNG.bitblt(png, out, sx, sy, cw, ch, 0, 0);
  return out;
}

export function refPath(w: Widget, stage: string, engine: string, theme: 'light' | 'dark'): string {
  return path.join(CONF, 'refs', w.set, w.stem, `${stage}.${engine}${theme === 'dark' ? '.dark' : ''}.png`);
}

/** The picture posted next to a visual or client-SDK widget: refs/visual/<widget>[.dark].png. */
export function picturePath(w: Widget, theme: 'light' | 'dark'): string {
  return path.join(CONF, 'refs', w.set, `${w.stem}${theme === 'dark' ? '.dark' : ''}.png`);
}

export function readJson<T>(file: string, fallback: T): T {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')) as T; } catch { return fallback; }
}
