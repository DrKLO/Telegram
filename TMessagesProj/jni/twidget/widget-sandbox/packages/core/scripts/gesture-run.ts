// The Node harness's renderer for gestures and layout (sandbox-node.ts `--width`, `--tap`, `--hold`, `--double-tap`,
// `--swipe`, `--right-click`): the real renderer core in headless Chromium (Playwright), on the validated ops of the
// QuickJS sandbox running in Node, playing a phone's bubble (`arbitrate` with a finger) or a desktop client's. Presses go
// in as mouse input with real timing, the renderer's events go back into the sandbox, and each press is reported: the
// element under it, its claim (`touch-action`), the shared policy's outcome (gesture-policy.ts), what the widget
// received, and a hint for the author. Layout queries of the sandbox still get the harness's canned answers.

import { build } from 'esbuild';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildSrcdoc, makeNonce } from '../src/renderer/srcdoc.ts';
import { DOUBLE_TAP_MS, EDGE_STRIP_PX, GestureArbiter, HOLD_MS, type GestureClaim, type GestureVerdict, type PointerKind } from '../src/shared/gesture-policy.ts';
import type { HostToRenderer } from '../src/shared/protocol.ts';
import { rendererBuildOptions } from './sandbox-build.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export type GestureStep =
  | { kind: 'tap'; x: number; y: number }
  | { kind: 'double-tap'; x: number; y: number }
  | { kind: 'right-click'; x: number; y: number }
  | { kind: 'hold'; x: number; y: number; ms?: number }
  | { kind: 'swipe'; x: number; y: number; dx: number; dy: number };

export interface GestureReport {
  step: GestureStep;
  /** The element under the press (`tag#id.class`) and its computed `touch-action` with the first non-`auto` one of its ancestors. */
  target: string;
  touchAction: string;
  /** The renderer's claim at pointerdown (the last press of a double tap); null when the press missed the widget. */
  claim: GestureClaim | null;
  /** The shared policy's verdict for this press, the one that decided it. */
  outcome: GestureVerdict | null;
  /** What the bubble did, as the renderer reported it (`bubble` messages). */
  bubble: string[];
  /** Events the widget received, in order (`type target`); only the types it listens for reach it. */
  received: string[];
  hint: string | null;
}

export const DEFAULT_HOLD_MS = 800;
const RENDERER_URL = 'http://renderer.invalid/';

/** Parses `x,y`, `x,y,ms` or `x,y,dx,dy` for a CLI flag; null when malformed. */
export function parseStep(flag: string, value: string | undefined): GestureStep | null {
  const n = (value ?? '').split(',').map(Number);
  if (n.length < 2 || n.some((v) => !Number.isFinite(v))) return null;
  const [x, y] = n;
  switch (flag) {
    case '--tap': return { kind: 'tap', x, y };
    case '--double-tap': return { kind: 'double-tap', x, y };
    case '--right-click': return { kind: 'right-click', x, y };
    case '--hold': return { kind: 'hold', x, y, ms: n[2] ?? DEFAULT_HOLD_MS };
    case '--swipe': return n.length === 4 ? { kind: 'swipe', x, y, dx: n[2], dy: n[3] } : null;
    default: return null;
  }
}

export interface RendererPage {
  send(msg: HostToRenderer): void;
  /** Waits until everything sent has been delivered and the renderer's answers have arrived. */
  settle(): Promise<void>;
  perform(step: GestureStep): Promise<void>;
  describe(x: number, y: number): Promise<{ target: string; touchAction: string }>;
  close(): Promise<void>;
}

/** Opens the renderer core in a page `width` CSS px wide; `onMessage` gets each renderer → host message (parsed). */
export async function openRenderer(width: number, onMessage: (msg: any) => void): Promise<RendererPage> {
  const { chromium } = await import('@playwright/test');
  const out = await build(rendererBuildOptions(root, 'webkit'));
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width, height: 900 } });
  page.on('pageerror', (e) => console.error('[renderer]', e.message));
  // The WKWebView transport: messages out through `webkit.messageHandlers.tw`, in through `__twReceive(json)`.
  await page.exposeFunction('__twOut', (json: string) => { try { onMessage(JSON.parse(json)); } catch {} });
  // A string, not a function: tsx's name-keeping helper would not exist in the page.
  await page.addInitScript({ content: 'window.webkit = { messageHandlers: { tw: { postMessage: function (s) { window.__twOut(s); } } } };' });
  // Served by a route, not setContent: init scripts run on a navigation only. Nothing else is reachable from the page.
  const doc = buildSrcdoc(out.outputFiles![0].text, makeNonce());
  await page.route('**/*', (route) => (route.request().url() === RENDERER_URL ? route.fulfill({ body: doc, contentType: 'text/html' }) : route.abort()));
  await page.goto(RENDERER_URL);
  await page.waitForFunction(() => typeof (window as any).__twReceive === 'function');
  let chain: Promise<unknown> = Promise.resolve();
  const send = (msg: HostToRenderer) => { const json = JSON.stringify(msg); chain = chain.then(() => page.evaluate((j) => (window as any).__twReceive(j), json)).catch(() => {}); };
  const settle = async () => {
    await chain;
    // Two frames: the renderer's throttled moves and its size report go out on animation frames.
    await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
    await page.waitForTimeout(50);
    await chain;
  };
  const m = page.mouse;
  return {
    send,
    settle,
    async perform(s) {
      await m.move(s.x, s.y);
      if (s.kind === 'right-click') { await m.down({ button: 'right' }); await page.waitForTimeout(60); await m.up({ button: 'right' }); }
      else if (s.kind === 'tap') { await m.down(); await page.waitForTimeout(60); await m.up(); }
      else if (s.kind === 'hold') { await m.down(); await page.waitForTimeout(s.ms ?? DEFAULT_HOLD_MS); await m.up(); }
      else if (s.kind === 'double-tap') { await m.down(); await page.waitForTimeout(40); await m.up(); await page.waitForTimeout(120); await m.down(); await page.waitForTimeout(40); await m.up(); }
      else {
        await m.down();
        for (let i = 1; i <= 10; i++) { await m.move(s.x + (s.dx * i) / 10, s.y + (s.dy * i) / 10); await page.waitForTimeout(15); }
        await m.up();
      }
      // Past the double-tap window, so the next press starts fresh.
      await page.waitForTimeout(DOUBLE_TAP_MS + 50);
      await settle();
    },
    describe: (x, y) => page.evaluate(([x, y]) => {
      const el = document.elementFromPoint(x, y);
      if (!el || el === document.documentElement) return { target: el ? 'html' : '(outside the widget)', touchAction: 'auto' };
      const id = el.id ? '#' + el.id : '';
      const cls = typeof el.className === 'string' && el.className.trim() ? '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.') : '';
      let ta = 'auto';
      for (let n: Element | null = el; n && n !== document.documentElement; n = n.parentElement) {
        // The engine's value is widened (pan-y always allowed, src/renderer/touch-action.ts); the widget's own is the claim property.
        const cs = getComputedStyle(n);
        if (cs.touchAction === 'auto') continue;
        const v = cs.getPropertyValue('--tw-touch-action').trim() || cs.touchAction;
        ta = n === el ? v : `${v} (from ${n.tagName.toLowerCase()}${n.id ? '#' + n.id : ''})`;
        break;
      }
      return { target: el.tagName.toLowerCase() + id + cls, touchAction: ta };
    }, [x, y] as const),
    close: () => browser.close(),
  };
}

/** The policy's deciding verdict for a scripted press with this claim (the same timeline `perform` plays). */
export function replay(s: GestureStep, claim: GestureClaim, desktop: boolean): GestureVerdict | null {
  const a = new GestureArbiter();
  const kind: PointerKind = desktop || s.kind === 'right-click' ? 'mouse' : 'touch';
  let decided: GestureVerdict | null = null;
  const see = (v: GestureVerdict | null) => { if (v && !decided && v.owner !== 'pending') decided = v; return v; };
  if (s.kind === 'right-click') return see(a.down(0, s.x, s.y, claim, 'mouse', 2));
  if (s.kind === 'tap') { a.down(0, s.x, s.y, claim, kind); return see(a.up(60, s.x, s.y)); }
  if (s.kind === 'hold') {
    const ms = s.ms ?? DEFAULT_HOLD_MS;
    a.down(0, s.x, s.y, claim, kind);
    if (ms >= HOLD_MS) see(a.tick(HOLD_MS));
    see(a.up(ms, s.x, s.y));
    return decided;
  }
  if (s.kind === 'double-tap') { a.down(0, s.x, s.y, claim, kind); a.up(40, s.x, s.y); a.down(160, s.x, s.y, claim, kind); return see(a.up(200, s.x, s.y)); }
  a.down(0, s.x, s.y, claim, kind);
  for (let i = 1; i <= 10; i++) see(a.move(i * 15, s.x + (s.dx * i) / 10, s.y + (s.dy * i) / 10));
  see(a.up(150, s.x + s.dx, s.y + s.dy));
  return decided;
}

const where = (r: GestureReport) => r.target === '(outside the widget)' ? 'the element' : r.target;

/** One line of advice for the widget's author, or null when the press did what such a control needs. */
export function hint(r: GestureReport, listens: ReadonlySet<string>): string | null {
  const v = r.outcome;
  if (!v || !r.claim) return r.target === '(outside the widget)' ? 'the press missed the widget (outside its area at this width)' : null;
  const t = where(r);
  const edge = v.edge ? ` It started in the leading ${EDGE_STRIP_PX} px of the widget, kept for the system back gesture, where no touch-action claims a press: keep controls at least ${EDGE_STRIP_PX} px from the leading edge.` : '';
  if (v.owner === 'bubble') {
    switch (v.action) {
      case 'back': return `the drag became the system back gesture.${edge}`;
      case 'menu': return (v.gesture === 'menu-click'
        ? `the right click opened the message menu: to handle it in the widget (contextmenu), set touch-action: none on ${t}.`
        : `the hold went to the message menu and the widget got pointercancel: set touch-action: none on ${t} if it is held (a game button, a joystick, a canvas).`) + edge;
      case 'swipe': return `the horizontal drag became swipe-to-reply and the widget got pointercancel: set touch-action: pan-y on ${t} for a horizontal slider or carousel (the chat still scrolls over it), or none to take holds too.${edge}`;
      case 'scroll': return `vertical drags always scroll the chat; a widget cannot claim them (the widget got pointercancel): lay the control out sideways (touch-action: pan-y or none on it) or use taps, for example buttons for up and down.${edge}`;
    }
  }
  if (v.owner === 'widget') {
    if (!r.received.length) return `the press was the widget's, but it listens for none of its events (pointerdown, pointerup, click${v.event ? ', ' + v.event : ''}).`;
    if (v.gesture === 'hold' && !listens.has('pointerup') && !listens.has('contextmenu')) return `the hold was the widget's, but it cannot tell it from a tap: listen for pointerdown and pointerup (or contextmenu) on ${t}.`;
  }
  return null;
}

function claimText(c: GestureClaim | null): string {
  if (!c) return '— (no press reached the widget)';
  // Vertical drags are always the chat's (v0.1.3): a claim never has `y`.
  if (c.hold && c.x) return 'horizontal drags and holds (touch-action: none); taps and double taps are always the widget\'s, vertical drags the chat\'s';
  if (c.x) return 'horizontal drags (touch-action: pan-y); holds stay with the bubble, vertical drags scroll the chat';
  return 'taps and double taps only: holds and drags are the bubble\'s (touch-action: auto; pan-x claims nothing either)';
}

const ACTIONS: Record<string, string> = { menu: 'message menu', swipe: 'swipe-to-reply', scroll: 'list scroll', back: 'system back' };

function outcomeText(v: GestureVerdict | null): string {
  if (!v) return '—';
  if (v.owner === 'bubble') return `bubble: ${ACTIONS[v.action ?? 'menu']} (${v.gesture}); the widget got pointercancel${v.edge ? ' (leading edge strip)' : ''}`;
  if (v.owner === 'widget') return `widget: ${v.gesture}${v.event ? `, plus ${v.event}` : ''}`;
  return 'undecided';
}

function stepText(s: GestureStep): string {
  if (s.kind === 'hold') return `hold (${s.x}, ${s.y}) ${s.ms ?? DEFAULT_HOLD_MS} ms`;
  if (s.kind === 'swipe') return `swipe (${s.x}, ${s.y}) by (${s.dx}, ${s.dy})`;
  return `${s.kind} (${s.x}, ${s.y})`;
}

/** The plain-text report sandbox-node.ts prints: one block per press. */
export function formatReports(reports: GestureReport[], width: number, desktop: boolean): string {
  const lines = [`gestures at width ${width} px, ${desktop ? 'desktop client (a mouse)' : 'phone client (a finger)'}; the rule: docs/sdk-v1.md §5.5`];
  reports.forEach((r, i) => {
    lines.push(`${i + 1}. ${stepText(r.step)} on ${r.target}, touch-action: ${r.touchAction}`);
    lines.push(`   claim:    ${claimText(r.claim)}`);
    lines.push(`   outcome:  ${outcomeText(r.outcome)}`);
    lines.push(`   received: ${r.received.length ? r.received.join(' → ') : '(nothing)'}`);
    if (r.hint) lines.push(`   hint:     ${r.hint}`);
  });
  return lines.join('\n');
}
