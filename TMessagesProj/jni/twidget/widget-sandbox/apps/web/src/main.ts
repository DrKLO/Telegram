// Dev UI (trusted host page). Loads corpus files or dropped files into a
// WidgetHost, shows diagnostics, and optionally the native reference render
// side by side. Exposes `window.__tw` for the Playwright suites.

import './style.css';
import { BlitzRenderer } from './renderers/blitz-renderer';
import { LitehtmlRenderer } from './renderers/litehtml-renderer';
import { ShadowDomRenderer } from '@telegram/widget-sandbox/host/shadow-renderer';
import { WidgetHost, type BubbleGesture, type GestureClaim } from '@telegram/widget-sandbox/host/widget-host';
import { WorkerPool } from '@telegram/widget-sandbox/host/worker-pool';
import { WIDGET_FEATURES, WIDGET_POLICY } from './policy';
import { GESTURE_MODES, bubbleGestureLabel, bubbleGesturesOption, claimLabel, gestureMode, showBubbleGesture, type GestureMode } from './bubble-gestures';
import type { FeedApi } from './feed';
import { DEFAULT_PROFILE, MODES, MockBot, botOf, mountPanel, showCopied, showWalletWindow, type Mode, type RequestRecord } from './mock-bot';
import type { Stats } from '@telegram/widget-sandbox/shared/protocol';
import { CHAT_TYPES, DEFAULT_RESIZE_ANCHOR, DEFAULT_THEME_PARAMS, WIDGET_MESSAGE_KINDS, checkContentSafeArea, themeVariables, type ChatType, type ContentSafeArea, type ResizeAnchor, type StorageCommit, type WidgetAction, type WidgetMessageKind } from '@telegram/widget-sandbox/shared/sdk-policy';
import { REFERENCE_TIME_LABEL, referenceSafeArea, timeCornerStyle } from '@telegram/widget-sandbox/shared/reference';
import { sizeVerdict, type WidgetSize } from '@telegram/widget-sandbox/shared/widget-size';

const $ = <T extends HTMLElement = HTMLElement>(sel: string) => document.querySelector(sel) as T;
const el = {
  corpus: $('#corpus'), drop: $('#drop'), file: $<HTMLInputElement>('#file'), title: $('#title'),
  dark: $<HTMLInputElement>('#dark'), reference: $<HTMLInputElement>('#reference'), blitz: $<HTMLInputElement>('#blitz'), litehtml: $<HTMLInputElement>('#litehtml'), shadow: $<HTMLInputElement>('#shadow'), compare: $<HTMLInputElement>('#compare'), feed: $<HTMLInputElement>('#feed'), reload: $('#reload'),
  app: $('#app'), feedView: $('#feed-view'),
  sandboxWrap: $('#sandbox-wrap'), referenceWrap: $('#reference-wrap'), chatReference: $('#chat-reference'), compareChats: $('#compare-chats'), stage: $('.stage'),
  status: $('#status'), stats: $('#stats'), diags: $('#diags'), bridge: $('#bridge'), console: $('#console'),
  widgetError: $('#widget-error'), width: $<HTMLSelectElement>('#width'), widthValue: $('#width-value'), gestureMode: $<HTMLSelectElement>('#gesture-mode'),
  chatSandbox: $('#chat-sandbox'), bubble: $('#chat-sandbox .bubble'), gestures: $('#gestures'), botPanel: $('#bot-panel'),
  netMarker: $('#net-marker'), timeCorner: $<HTMLSelectElement>('#time-corner'), timeCornerView: $('#time-corner-view'), timeCornerRef: $('#time-corner-ref'),
};

export interface TwState {
  file: string | null;
  html: string;
  theme: 'light' | 'dark';
  loaded: boolean;
  dead: string | null;
  diags: Record<string, { count: number; details: string[] }>;
  logs: { level: string; text: string }[];
  bridge: { method: string; args: unknown[] }[];
  /** What the widget asked the client to do (SDK v1 `onAction`), in order. */
  actions: WidgetAction[];
  /** Gesture claims of the primary pointerdowns (`onGestureClaim`), in order. */
  gestures: GestureClaim[];
  /** Presses the bubble took (`onBubbleGesture`: the message menu, the reaction, a swipe, the list scroll), in order. */
  bubbleGestures: BubbleGesture[];
  stats: (Stats & { nodes: number }) | null;
  height: number;
  /** The edge of the bubble that stays put when the height changes (`setResizeAnchor`, reported with the height). */
  resizeAnchor: ResizeAnchor;
  /** Last uncaught widget error, shown under the bubble until a later task completes without one. */
  lastError: string | null;
  /** The HTML's size as the server counts it (zlib, UTF-8 bytes; widget-size.ts): null until measured. */
  size: WidgetSize | null;
  /** The widget's requests to the mock bot (`onRequest`) and what each got, in order (all files; the bot keeps the last 200). */
  requests: RequestRecord[];
  /** Texts the widget copied (`onCopyText`), in order. */
  copiedTexts: string[];
}

// The mock bot (mock-bot.ts): the message's chatData / publicData, the bot's answers and edits, the client's context.
const bot = new MockBot({
  restart: () => { if (state.html) load(state.html, state.file); },
  setData: (data) => { for (const h of [host, ...compareHosts]) h?.setData(data); },
  changed: () => { coalesce(renderBot); coalesce(syncUrl); },
  confirmWallet: (call) => showWalletWindow(bot.bot + '_bot', call),
});
const state: TwState = { file: null, html: '', theme: 'light', loaded: false, dead: null, diags: {}, logs: [], bridge: [], actions: [], gestures: [], bubbleGestures: [], stats: null, height: 0, resizeAnchor: DEFAULT_RESIZE_ANCHOR, lastError: null, size: null, requests: bot.requests, copiedTexts: [] };
/** The file whose `<meta name="tw-bot">` chose the bot: a restart of the same file keeps the bot the panel shows. */
let botFile: string | null | undefined;
/** The bubble's own presses (docs/sdk-v1.md §5.5): `?gestures=phone` (default), `desktop` or `off`. */
let gestures: GestureMode = gestureMode(new URLSearchParams(location.search).get('gestures'));
/** Bubble widths a client lays out (CSS px); 0 = the default column, -1 = free (drag the column's corner). */
const WIDTH_PRESETS = [200, 320, 360, 390, 430, 600];
let bubbleWidth = 0;
/**
 * Where the client draws the message time (docs/sdk-v1.md §2.17, `?time=`): `caption` (a caption below the widget: the
 * time in its line, nothing over the widget, the safe area is 0), `corner` (no caption, or one shown above the widget:
 * the time over its bottom right corner, a left-to-right client) or `corner-rtl` (the left one). `__tw.setSafeArea(area)` passes any other numbers (the conformance runners).
 */
const TIME_MODES = [
  { mode: 'caption', label: 'in the caption', title: 'a widget with a caption below it: the time is in the caption\'s line, nothing over the widget' },
  { mode: 'corner', label: 'over the corner', title: 'a widget without a caption, or with its caption above it (invert_media): the time over its bottom right corner' },
  { mode: 'corner-rtl', label: 'over the corner, RTL', title: 'a right-to-left client: the time over the bottom left corner' },
] as const;
type TimeMode = (typeof TIME_MODES)[number]['mode'];
let timeMode: TimeMode = 'caption';
let customSafeArea: ContentSafeArea | null = null;
/** What the hosts get as `contentSafeArea`. */
const safeArea = (): ContentSafeArea => customSafeArea ?? (timeMode === 'caption' ? checkContentSafeArea(null) : referenceSafeArea(timeMode === 'corner-rtl'));
/** `?locale=ru`: the client's UI language passed to the widget (navigator.language, the Intl default); en-US otherwise. */
const LOCALE = new URLSearchParams(location.search).get('locale') ?? undefined;

// Web Storage as a client keeps it (docs/sdk-v1.md §5.4), per file: `local` in this page's localStorage (it survives a
// reload, as it survives an app restart), `session` in memory. `__tw.restart()` and reopening the file get it back.
const sessionAreas = new Map<string, Record<string, string>>();
const storageKey = (file: string) => 'tw-widget-storage:' + file;
function storageFor(file: string | null): StorageCommit | undefined {
  if (!file) return undefined;
  let local: Record<string, string> = {};
  try { local = JSON.parse(localStorage.getItem(storageKey(file)) ?? '{}'); } catch {}
  return { local, session: sessionAreas.get(file) ?? {} };
}
function commitStorage(file: string | null, area: 'local' | 'session', values: Record<string, string>) {
  if (!file) return;
  if (area === 'session') sessionAreas.set(file, values);
  else try { localStorage.setItem(storageKey(file), JSON.stringify(values)); } catch {}
}
function clearStorage(file: string | null) {
  if (!file) return;
  sessionAreas.delete(file);
  try { localStorage.removeItem(storageKey(file)); } catch {}
}
let tasksAtError = 0;
/** Runtime limits overridable from the URL for the Playwright suites (?cpuQuotaMs=…&cpuKillAfterMs=…&maxTimers=…). */
const LIMIT_PARAMS = ['cpuLimitMs', 'cpuQuotaMs', 'cpuQuotaWindowMs', 'cpuKillAfterMs', 'mainThreadQuotaMs', 'maxTimers', 'memoryLimitBytes'] as const;
const limits: Partial<Record<(typeof LIMIT_PARAMS)[number], number>> = {};
let host: WidgetHost | null = null;
/** `?pool=N`: sandbox workers from a pool of N warmed ones (worker-pool.ts), as a feed would do; prewarmed at init. */
let workerPool: WorkerPool | null = null;
/** `?feed=1`: the chat feed demo (src/feed.ts) takes over the centre column; the single-widget panels are hidden and `host` stays null. */
const FEED_MODE = new URLSearchParams(location.search).get('feed') === '1';
let feed: FeedApi | null = null;
/** Compare mode: the renderers the main bubble is not using, one WidgetHost each (same HTML, same limits). */
let compareHosts: WidgetHost[] = [];
let referenceFrame: HTMLIFrameElement | null = null;
/** Off-screen pause: the bubble's intersection with the viewport, combined with the page's own visibility. `forcedVisible` (tests) overrides both. */
let intersecting = true;
let forcedVisible: boolean | null = null;
const visibilityObserver = new IntersectionObserver((entries) => { intersecting = entries[entries.length - 1].isIntersecting; applyVisibility(); });
function applyVisibility() {
  host?.setVisible(forcedVisible ?? (intersecting && !document.hidden));
}
document.addEventListener('visibilitychange', applyVisibility);

// The client's poll of a shared widget's public_data (docs/integration.md): only while the widget watches
// (`watchPublicData`), is on screen and is a shared object (the mock bot's `shared`), every `bot.pollMs`.
let lastPoll = 0;
setInterval(() => {
  const now = performance.now();
  if (now - lastPoll < bot.pollMs) return;
  if (!host || !state.loaded || state.dead || !host.watchesPublicData || !(forcedVisible ?? (intersecting && !document.hidden))) return;
  lastPoll = now;
  const next = bot.poll(state.file);
  if (next) for (const h of [host, ...compareHosts]) h?.setData(next);
}, 50);
let referenceTimer = 0;
let loadWaiters: (() => void)[] = [];

// ───────────── rendering of the panels ─────────────

// Panels are re-rendered at most once per frame: a misbehaving widget can emit
// thousands of diagnostics per second, and per-event DOM rebuilds froze the page.
const pending = new Set<() => void>();
let frame = 0;
function coalesce(fn: () => void) {
  pending.add(fn);
  if (!frame) frame = requestAnimationFrame(() => { frame = 0; const fns = [...pending]; pending.clear(); for (const f of fns) f(); });
}

function renderStatus() {
  el.status.textContent = state.dead ? `✖ sandbox terminated: ${state.dead}` : state.loaded ? '● loaded' : host ? '… loading' : '—';
  el.status.className = state.dead ? 'dead' : state.loaded ? 'ok' : '';
  // What a product would show under the bubble: the widget stays on screen (frozen) with a one-line reason.
  const line = state.dead ? `Widget stopped: ${state.dead.split('\n')[0]}` : state.lastError ? state.lastError.split('\n')[0] : '';
  el.widgetError.textContent = line;
  el.widgetError.classList.toggle('hidden', !line);
}
/** Bytes after zlib compression (CompressionStream 'deflate' is RFC 1950, at zlib's default level 6: a few bytes over the server's level 9) and of the HTML. */
async function measureSize(html: string): Promise<WidgetSize> {
  const raw = new TextEncoder().encode(html);
  const z = await new Response(new Blob([raw]).stream().pipeThrough(new CompressionStream('deflate'))).arrayBuffer();
  return { zlib: z.byteLength, raw: raw.length };
}

/** The size row: green under 3800 B compressed, a warning up to the 4096 B limit, an error past it or past 32 KB of HTML. */
function sizeRow(size: WidgetSize): HTMLTableRowElement {
  const v = sizeVerdict(size);
  const tr = document.createElement('tr');
  tr.className = 'size size-' + v.level;
  const th = document.createElement('th'); th.textContent = 'size (server)';
  const td = document.createElement('td');
  td.textContent = `${size.zlib} B zlib / ${(size.raw / 1024).toFixed(1)} KB raw (limit 4096 / 32768)`;
  if (v.message) { const d = document.createElement('div'); d.textContent = v.message; td.appendChild(d); }
  tr.title = 'zlib, as the server counts it (level 9 there, 6 here; docs/tgw-format.md); past either limit it answers INPUT_MEDIA_WIDGET_TOO_LARGE';
  tr.append(th, td);
  return tr;
}

function renderStats() {
  const s = state.stats;
  const size = state.size ? [sizeRow(state.size)] : [];
  if (!s) { el.stats.replaceChildren(...size); return; }
  const rows: [string, string][] = [
    ['JS time', `${s.jsMs.toFixed(0)} ms in ${s.tasks} tasks`],
    ['memory', `${(s.memoryBytes / 1024 / 1024).toFixed(1)} MB`],
    ['ops sent', String(s.ops)],
    ['nodes', String(s.nodes)],
    ['sync queries', `${s.queries} (${s.queryMs.toFixed(0)} ms, avg ${s.queries ? (s.queryMs / s.queries).toFixed(2) : '0'} ms), cache hits ${s.queryCacheHits}`],
    ['missed mutations', String(s.missedMutations)],
    ['CPU quota', `${s.cpuWindowMs.toFixed(0)} ms used in the current window, ${s.throttledTasks} tasks deferred`],
    ['bubble height', `${state.height} px, anchor ${state.resizeAnchor}: ${state.resizeAnchor === 'bottom' ? 'grows and shrinks upward, its bottom stays' : 'grows and shrinks downward, its top stays'}`],
  ];
  el.stats.replaceChildren(...size, ...rows.map(([k, v]) => {
    const tr = document.createElement('tr');
    const th = document.createElement('th'); th.textContent = k;
    const td = document.createElement('td'); td.textContent = v;
    tr.append(th, td);
    return tr;
  }));
}
function renderDiags() {
  el.diags.replaceChildren(...Object.entries(state.diags).map(([kind, d]) => {
    const li = document.createElement('li');
    li.className = 'diag diag-' + kind;
    const b = document.createElement('b'); b.textContent = `${kind} ×${d.count}`;
    const span = document.createElement('span'); span.textContent = d.details.slice(-3).join(' · ');
    li.append(b, ' ', span);
    li.title = d.details.join('\n');
    return li;
  }));
}
function renderBridge() {
  el.bridge.replaceChildren(...state.bridge.slice(-20).map((b) => {
    const li = document.createElement('li');
    li.textContent = `${b.method}(${b.args.map((a) => JSON.stringify(a)).join(', ')})`;
    return li;
  }));
}
function renderGestures() {
  const rows = state.gestures.slice(-6).map((c) => `press on ${claimLabel(c)}`);
  const taken = state.bubbleGestures.slice(-4).map((g) => `bubble: ${bubbleGestureLabel(g)} (${g.gesture}; the widget got pointercancel)`);
  el.gestures.replaceChildren(...[...rows, ...taken].map((t) => Object.assign(document.createElement('li'), { textContent: t })));
}
const botPanel = mountPanel(el.botPanel, bot, () => state.file);
function renderBot() { botPanel.render(); }
function renderConsole() {
  el.console.replaceChildren(...state.logs.slice(-300).map((l) => {
    const div = document.createElement('div');
    div.className = 'log log-' + l.level;
    div.textContent = `[${l.level}] ${l.text}`;
    return div;
  }));
  el.console.scrollTop = el.console.scrollHeight;
}

// ───────────── loading widgets ─────────────

function load(html: string, name: string | null) {
  host?.destroy();
  host = null;
  Object.assign(state, { file: name, html, loaded: false, dead: null, diags: {}, logs: [], bridge: [], actions: [], gestures: [], bubbleGestures: [], stats: null, height: 0, resizeAnchor: DEFAULT_RESIZE_ANCHOR, lastError: null, size: null, copiedTexts: [] });
  if (name !== botFile || name === null) { botFile = name; bot.bot = botOf(html); }
  applyAnchor();
  void measureSize(html).then((size) => { if (state.html === html) { state.size = size; coalesce(renderStats); } });
  tasksAtError = 0;
// ───────────── the resize anchor (docs/sdk-v1.md §5.6) ─────────────

// The bubble's anchored edge stays put on the stage, as in a chat: anchored at the bottom, the bubble sits at the
// stage's bottom and grows upward (style.css `.anchor-bottom`); once the stage scrolls, its scroll keeps the bottom edge.
// The anchor comes with the size reports, so a widget that sets it at start is laid out at the bottom from the first one.
let anchoredHeight = 0;
function applyAnchor() {
  el.chatSandbox.classList.toggle('anchor-bottom', state.resizeAnchor === 'bottom' && !FEED_MODE);
}
new ResizeObserver(() => {
  const h = el.chatSandbox.offsetHeight, delta = h - anchoredHeight;
  anchoredHeight = h;
  if (delta && state.resizeAnchor === 'bottom' && el.stage.scrollHeight > el.stage.clientHeight) el.stage.scrollTop += delta;
}).observe(el.chatSandbox);

  el.title.textContent = name ?? 'dropped file';
  el.sandboxWrap.replaceChildren();
  renderStatus(); renderStats(); renderDiags(); renderBridge(); renderConsole(); renderGestures(); renderBot();
  const context = bot.hostContext();
  bot.loaded(name);
  // The network marker, as a client draws it: exactly when the message has can_query (a preview has no flags).
  el.netMarker.classList.toggle('hidden', !context.canQuery || !!context.preview);
  host = new WidgetHost(el.sandboxWrap, html, {
    policy: WIDGET_POLICY,
    features: WIDGET_FEATURES,
    theme: state.theme,
    locale: LOCALE,
    contentSafeArea: safeArea(),
    storage: storageFor(name),
    data: bot.dataFor(name),
    context,
    // Forced off (tests): created off screen, as Telegram's clients create a bubble before it is shown.
    ...(forcedVisible !== null ? { visible: forcedVisible } : {}),
    onRequest: (request) => bot.request(name, request),
    onWallet: (call) => bot.walletCall(name, call),
    onCopyText: (text) => {
      state.copiedTexts.push(text);
      showCopied(el.bubble, text);
      navigator.clipboard?.writeText(text).catch(() => {});
    },
    ...limits,
    // Blitz / litehtml (dev tooling): a native renderer compiled to wasm instead of the sandboxed iframe; sandbox and validator unchanged.
    // Shadow DOM (prototype): the same renderer core on a shadow root in this page, no iframe.
    rendererFactory: el.litehtml.checked ? LitehtmlRenderer.factory : el.blitz.checked ? BlitzRenderer.factory : el.shadow.checked ? ShadowDomRenderer.factory : undefined,
    workerPool: workerPool ?? undefined,
    onLog: (level, text) => { if (state.logs.length < 5000) state.logs.push({ level, text }); coalesce(renderConsole); },
    onError: (text) => { state.lastError = text; tasksAtError = state.stats?.tasks ?? 0; coalesce(renderStatus); },
    onDiag: (kind, detail) => {
      const d = (state.diags[kind] ??= { count: 0, details: [] });
      d.count++;
      if (d.details.length < 50) d.details.push(detail);
      coalesce(renderDiags);
    },
    onStats: (s) => {
      state.stats = s;
      if (state.lastError && s.tasks > tasksAtError + 1) { state.lastError = null; coalesce(renderStatus); }
      coalesce(renderStats);
    },
    onBridge: (method, args) => { state.bridge.push({ method, args }); coalesce(renderBridge); },
    onAction: (action) => { state.actions.push(action); },
    onStorageCommit: (area, values) => commitStorage(name, area, values),
    onGestureClaim: (claim) => { if (state.gestures.length < 1000) state.gestures.push(claim); coalesce(renderGestures); },
    bubbleGestures: bubbleGesturesOption(gestures),
    onBubbleGesture: (g) => { if (state.bubbleGestures.length < 1000) state.bubbleGestures.push(g); showBubbleGesture(el.bubble, g); coalesce(renderGestures); },
    onDead: (reason) => { state.dead = reason; renderStatus(); settle(); },
    onLoaded: () => { state.loaded = true; renderStatus(); settle(); },
    onResize: (h, anchor) => { state.height = h; state.resizeAnchor = anchor; applyAnchor(); coalesce(renderStats); },
    onWatchPublicData: (on) => { bot.watching = on; coalesce(renderBot); },
  });
  visibilityObserver.disconnect();
  intersecting = true;
  visibilityObserver.observe(host.element);
  applyVisibility();
  updateCompare();
  updateReference();
  syncUrl();
}

type RendererKind = 'iframe' | 'shadow' | 'blitz' | 'litehtml';
const RENDERERS: { kind: RendererKind; title: string; factory?: typeof BlitzRenderer.factory }[] = [
  { kind: 'iframe', title: 'sandbox · iframe renderer (product)' },
  { kind: 'shadow', title: 'sandbox · Shadow DOM renderer (no iframe, prototype)', factory: ShadowDomRenderer.factory },
  { kind: 'blitz', title: 'sandbox · Blitz renderer (wasm, dev tooling)', factory: BlitzRenderer.factory },
  { kind: 'litehtml', title: 'sandbox · litehtml renderer (wasm, dev tooling)', factory: LitehtmlRenderer.factory },
];
function mainRenderer(): RendererKind { return el.litehtml.checked ? 'litehtml' : el.blitz.checked ? 'blitz' : el.shadow.checked ? 'shadow' : 'iframe'; }

/** Compare mode (dev tooling): the other two renderers next to the main bubble, each on its own sandbox from the same
 * HTML, plus the native reference. The right-hand panels keep describing the main bubble; each extra column shows its
 * own one-line status (loaded / dead, the renderer's paint line). The sandboxes boot one after another — three QuickJS
 * workers starting at once contend for the CPU and the boot slice (cpuLimitMs) kills them. */
let compareGeneration = 0;
function updateCompare() {
  compareGeneration++;
  for (const h of compareHosts) h.destroy();
  compareHosts = [];
  el.compareChats.replaceChildren();
  const on = el.compare.checked && !!state.html;
  el.stage.classList.toggle('compare', on);
  if (!on) return;
  const generation = compareGeneration;
  const html = state.html;
  const columns = RENDERERS.filter((r) => r.kind !== mainRenderer()).map((r) => {
    const chat = document.createElement('div');
    chat.className = 'chat';
    chat.dataset.renderer = r.kind;
    const head = document.createElement('div');
    head.className = 'bubble-head';
    head.textContent = r.title;
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    const wrap = document.createElement('div');
    wrap.className = 'frame-wrap';
    bubble.appendChild(wrap);
    const status = document.createElement('div');
    status.className = 'compare-status';
    status.textContent = '… waiting for the previous sandbox to boot';
    chat.append(head, bubble, status);
    el.compareChats.appendChild(chat);
    return { r, wrap, status };
  });
  let i = 0;
  const startNext = () => {
    if (generation !== compareGeneration || i >= columns.length) return;
    const { r, wrap, status } = columns[i++];
    const t0 = performance.now();
    const lines: Record<string, string> = { status: '… loading' };
    const show = () => { status.textContent = Object.entries(lines).map(([k, v]) => `${k}: ${v}`).join('\n'); };
    show();
    let settled = false;
    const settle = () => { if (!settled) { settled = true; startNext(); } };
    // `current` (what __tw.tap / __tw.blitz / __tw.litehtml talk to) stays the main bubble's renderer.
    const mainBlitz = BlitzRenderer.current, mainLitehtml = LitehtmlRenderer.current;
    const h = new WidgetHost(wrap, html, {
      policy: WIDGET_POLICY,
      features: WIDGET_FEATURES,
      theme: state.theme,
      contentSafeArea: safeArea(),
      data: bot.dataFor(state.file),
      context: bot.hostContext(),
      ...limits,
      rendererFactory: r.factory,
      bubbleGestures: bubbleGesturesOption(gestures),
      onLoaded: () => { lines.status = `loaded in ${(performance.now() - t0).toFixed(0)} ms`; coalesce(show); settle(); },
      onDead: (reason) => { lines.status = `dead: ${reason.split('\n')[0]}`; coalesce(show); settle(); },
      onError: (text) => { lines.error = text.split('\n')[0]; coalesce(show); },
      onDiag: (kind, detail) => { if (kind === 'renderer' || /-(init|paint|build)$/.test(kind)) { lines[kind] = detail; coalesce(show); } },
    });
    BlitzRenderer.current = mainBlitz;
    LitehtmlRenderer.current = mainLitehtml;
    compareHosts.push(h);
    setTimeout(settle, 15_000);
  };
  ready().then(startNext);
}

function settle() {
  const w = loadWaiters;
  loadWaiters = [];
  for (const f of w) f();
}

/** Resolves once the current widget has finished loading (or died). */
function ready(): Promise<void> {
  if (state.loaded || state.dead) return Promise.resolve();
  return new Promise((r) => loadWaiters.push(r));
}

async function openFile(rel: string) {
  const res = await fetch('/corpus/' + rel);
  if (!res.ok) throw new Error('no such corpus file: ' + rel);
  load(await res.text(), rel);
  for (const li of el.corpus.children) li.classList.toggle('active', (li as HTMLElement).dataset.file === rel);
}

// Reference mode (dev tooling): native render of a repo corpus file in a
// sandboxed iframe served with connect-src 'none'. Never used for attacks.
/**
 * The dev app's theme in the reference frame: the palette of the toggle (the reference stylesheet alone follows the
 * browser's prefers-color-scheme), and the frame's root in the same scheme as its element, or the frame gets an opaque
 * canvas over the bubble, as the renderer's frame would (core.ts frameColorScheme). The time control's content safe
 * area comes with the frame's URL (`?safeArea=`, vite.config.ts) as the root variables; the frame's
 * `env(safe-area-inset-*)` stays the browser's own (0, unless the page runs with Chromium's safe-area override, as
 * `pnpm test:safe-area-corpus` does).
 */
function referenceScheme(f: HTMLIFrameElement, theme: 'light' | 'dark') {
  try {
    const d = f.contentDocument;
    if (!d?.head) return;
    const style = d.getElementById('tw-ref-scheme') ?? d.head.appendChild(Object.assign(d.createElement('style'), { id: 'tw-ref-scheme' }));
    const vars = Object.entries(themeVariables({ colorScheme: theme })).map(([k, v]) => `${k}:${v}`).join(';');
    style.textContent = `@layer tw-ua{:where(html){color-scheme:${theme}}}:root{${vars}}`;
  } catch {}
}

function updateReference() {
  // compare mode shows the reference too (never for attacks: reference mode has no sandbox)
  const on = (el.reference.checked || el.compare.checked) && state.file && !state.file.startsWith('attacks/');
  el.chatReference.classList.toggle('hidden', !on);
  referenceFrame?.remove();
  referenceFrame = null;
  clearInterval(referenceTimer);
  if (!on) return;
  const f = document.createElement('iframe');
  f.className = 'widget-frame';
  // allow-same-origin only so that the dev UI can read the document height;
  // allow-forms so that `submit` events fire, like in the product renderer.
  f.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms');
  f.style.colorScheme = state.theme;
  f.addEventListener('load', () => referenceScheme(f, state.theme));
  const a = safeArea();
  f.src = '/reference/' + state.file + (a.bottom || a.cornerLeft || a.cornerRight ? `?safeArea=${a.bottom},${a.cornerRight},${a.cornerLeft}` : '');
  el.referenceWrap.replaceChildren(f);
  referenceFrame = f;
  const size = () => {
    try {
      const d = f.contentDocument;
      if (d?.documentElement) f.style.height = Math.min(Math.max(Math.ceil(d.documentElement.getBoundingClientRect().height), 50), 600) + 'px';
    } catch {}
  };
  // Same-origin frame: size it from inside (ResizeObserver) plus a slow fallback poll.
  f.addEventListener('load', () => {
    size();
    try {
      const d = f.contentDocument!;
      new (f.contentWindow as any).ResizeObserver(size).observe(d.documentElement);
      new (f.contentWindow as any).ResizeObserver(size).observe(d.body);
    } catch {}
  });
  referenceTimer = window.setInterval(size, 1000);
}

function setTheme(theme: 'light' | 'dark') {
  state.theme = theme;
  el.dark.checked = theme === 'dark';
  document.documentElement.dataset.theme = theme;
  // The bubbles are the widgets' canvas: their colour is the bg_color the hosts get (the scheme's default palette here).
  document.documentElement.style.setProperty('--bubble', DEFAULT_THEME_PARAMS[theme].bg_color);
  host?.setTheme(theme);
  for (const h of compareHosts) h.setTheme(theme);
  if (referenceFrame) { referenceFrame.style.colorScheme = theme; referenceScheme(referenceFrame, theme); }
  feed?.setTheme(theme);
  syncUrl();
}

function syncUrl() {
  if (FEED_MODE) return; // the feed owns the URL (its own switches, plus the theme)
  const p = new URLSearchParams();
  if (state.file) p.set('file', state.file);
  if (state.theme === 'dark') p.set('theme', 'dark');
  if (el.reference.checked) p.set('reference', '1');
  if (el.blitz.checked) p.set('blitz', '1');
  if (el.litehtml.checked) p.set('litehtml', '1');
  if (el.shadow.checked) p.set('shadow', '1');
  if (el.compare.checked) p.set('compare', '1');
  for (const k of LIMIT_PARAMS) if (limits[k] !== undefined) p.set(k, String(limits[k]));
  if (workerPool) p.set('pool', String(workerPool.size));
  if (bubbleWidth) p.set('width', bubbleWidth > 0 ? String(bubbleWidth) : 'free');
  if (gestures !== 'phone') p.set('gestures', gestures);
  if (timeMode !== 'caption') p.set('time', timeMode);
  // The mock bot's context and answers, when not the defaults (the original in the private chat with the bot, viewer 1001).
  const c = bot.context;
  if (c.message !== 'private') p.set('message', c.message);
  if (c.chatType) p.set('chat', c.chatType);
  if (c.viewer !== 1001 || c.profile !== DEFAULT_PROFILE) p.set('viewer', c.viewer === null ? 'none' : `${c.viewer},${c.profile}`);
  if (bot.mode !== 'bot') p.set('botmode', bot.mode);
  if (!bot.wallet) p.set('wallet', '0');
  history.replaceState(null, '', '?' + p.toString());
}

/** The bubble's width (every column of the stage: sandbox, reference, compare): a preset, the default column, or free. */
function setWidth(w: number | 'free') {
  bubbleWidth = w === 'free' ? -1 : WIDTH_PRESETS.includes(w) || (w >= 200 && w <= 1200) ? w : 0;
  el.stage.classList.toggle('free-width', bubbleWidth < 0);
  if (bubbleWidth > 0) el.stage.style.setProperty('--chat-width', bubbleWidth + 'px');
  else el.stage.style.removeProperty('--chat-width');
  if (bubbleWidth >= 0) el.chatSandbox.style.width = '';
  el.width.value = bubbleWidth < 0 ? 'free' : String(bubbleWidth);
  if (el.width.value === '' && bubbleWidth > 0) el.width.value = String(bubbleWidth); // a width from the URL outside the presets
  showWidth();
  syncUrl();
}
function showWidth() { el.widthValue.textContent = `${Math.round(el.chatSandbox.getBoundingClientRect().width)} px`; }
// Free width: the sandbox column has a resize handle; the reference column follows it.
new ResizeObserver(() => {
  showWidth();
  if (bubbleWidth < 0) el.stage.style.setProperty('--chat-width', Math.round(el.chatSandbox.getBoundingClientRect().width) + 'px');
}).observe(el.chatSandbox);

/**
 * The time over the bubble (docs/sdk-v1.md §2.17): drawn here, outside the widget, as a client draws it (a view over the
 * widget that takes its own taps), and passed to the live hosts; a new widget starts with it.
 */
function setTimeCorner(mode: TimeMode | null, area: Partial<ContentSafeArea> | null = null) {
  if (mode) timeMode = mode;
  customSafeArea = area ? checkContentSafeArea(area) : null;
  el.timeCorner.value = timeMode;
  const style = timeCornerStyle(safeArea());
  el.timeCornerView.classList.toggle('hidden', !style);
  el.timeCornerView.setAttribute('style', style);
  el.timeCornerView.textContent = REFERENCE_TIME_LABEL;
  // The reference column is a client's bubble too: the same time over it and the same variables in its frame.
  el.timeCornerRef.classList.toggle('hidden', !style);
  el.timeCornerRef.setAttribute('style', style);
  el.timeCornerRef.textContent = REFERENCE_TIME_LABEL;
  // The reference frame reads the band from its URL before its scripts run: a new one.
  if (referenceFrame) updateReference();
  for (const h of [host, ...compareHosts]) h?.setContentSafeArea(safeArea());
  syncUrl();
}

/** The bubble's presses: `phone` (the mouse as a finger), `desktop` (as a mouse) or `off`; applied to the live hosts. */
function setGestures(mode: GestureMode) {
  gestures = mode;
  el.gestureMode.value = mode;
  for (const h of [host, ...compareHosts]) h?.setBubbleGestures(bubbleGesturesOption(mode));
  feed?.setGestures(mode);
  syncUrl();
}

// ───────────── wiring ─────────────

async function init() {
  const files: string[] = await (await fetch('/api/corpus')).json();
  el.corpus.replaceChildren(...files.map((f) => {
    const li = document.createElement('li');
    li.textContent = f;
    li.dataset.file = f;
    li.addEventListener('click', () => openFile(f));
    return li;
  }));
  const params = new URLSearchParams(location.search);
  for (const k of LIMIT_PARAMS) if (params.has(k) && Number.isFinite(Number(params.get(k)))) limits[k] = Number(params.get(k));
  if (FEED_MODE) {
    // Feed demo: the theme toggle and the feed checkbox are the only toolbar controls left; everything else is per bubble inside the feed.
    el.feed.checked = true;
    el.app.classList.add('feed-mode');
    el.feedView.classList.remove('hidden');
    el.gestureMode.value = gestures;
    setTheme(params.get('theme') === 'dark' ? 'dark' : 'light');
    const { startFeed } = await import('./feed');
    feed = startFeed(el.feedView, { theme: state.theme, params, gestures });
    (window as any).__tw.feed = feed;
    return;
  }
  const poolSize = Number(params.get('pool') ?? 0);
  if (poolSize > 0) {
    workerPool = new WorkerPool(poolSize);
    await workerPool.prewarm();
  }
  el.reference.checked = params.get('reference') === '1';
  el.blitz.checked = params.get('blitz') === '1';
  el.litehtml.checked = params.get('litehtml') === '1';
  el.shadow.checked = params.get('shadow') === '1';
  el.compare.checked = params.get('compare') === '1';
  // The mock bot's context from the URL (`?message=group&chat=supergroup&viewer=42,Ann,Lee,ann_lee,premium&botmode=TIMEOUT`), before the first widget.
  const kind = params.get('message');
  if (kind && kind in WIDGET_MESSAGE_KINDS) bot.context.message = kind as WidgetMessageKind;
  const chat = params.get('chat');
  if (chat) bot.context.chatType = (CHAT_TYPES as readonly string[]).includes(chat) ? (chat as ChatType) : null;
  const viewer = params.get('viewer');
  if (viewer) {
    // `?viewer=42` keeps the default names; `?viewer=42,Ann,,ann_lee,premium` sets them (`viewerFromList`).
    const [id, ...rest] = viewer.split(',');
    bot.context.viewer = Number(id) > 0 ? Number(id) : null;
    if (rest.length) bot.context.profile = rest.join(',');
  }
  const botMode = params.get('botmode');
  if (botMode && (MODES as readonly string[]).includes(botMode)) bot.mode = botMode as Mode;
  // ?wallet=0: the client has no wallet for this user (TelegramWidget.wallet is null).
  if (params.get('wallet') === '0') bot.wallet = false;
  renderBot();
  const w = params.get('width');
  setWidth(w === 'free' ? 'free' : Number(w) || 0);
  setGestures(gestures);
  const time = params.get('time');
  setTimeCorner(TIME_MODES.some((t) => t.mode === time) ? (time as TimeMode) : 'caption');
  setTheme(params.get('theme') === 'dark' ? 'dark' : 'light');
  // ?offscreen=1: the widget boots off screen until __tw.setVisible(true | null), as Telegram's clients create a bubble.
  if (params.get('offscreen') === '1') forcedVisible = false;
  const file = params.get('file');
  if (file && files.includes(file)) await openFile(file);
}

el.dark.addEventListener('change', () => setTheme(el.dark.checked ? 'dark' : 'light'));
el.width.replaceChildren(...[['0', 'default'], ...WIDTH_PRESETS.map((w) => [String(w), `${w} px`]), ['free', 'free (drag)']].map(([v, t]) => Object.assign(document.createElement('option'), { value: v, textContent: t })));
el.width.addEventListener('change', () => setWidth(el.width.value === 'free' ? 'free' : Number(el.width.value)));
el.gestureMode.replaceChildren(...GESTURE_MODES.map((g) => Object.assign(document.createElement('option'), { value: g.mode, textContent: g.mode, title: g.title })));
el.gestureMode.addEventListener('change', () => setGestures(gestureMode(el.gestureMode.value)));
el.timeCorner.replaceChildren(...TIME_MODES.map((t) => Object.assign(document.createElement('option'), { value: t.mode, textContent: t.label, title: t.title })));
el.timeCorner.addEventListener('change', () => setTimeCorner(el.timeCorner.value as TimeMode));
el.reference.addEventListener('change', () => { updateReference(); syncUrl(); });
el.compare.addEventListener('change', () => { updateCompare(); updateReference(); syncUrl(); });
// The two native renderers are exclusive: ticking one unticks the other.
el.blitz.addEventListener('change', () => { if (el.blitz.checked) { el.litehtml.checked = false; el.shadow.checked = false; } if (state.html) load(state.html, state.file); else syncUrl(); });
el.litehtml.addEventListener('change', () => { if (el.litehtml.checked) { el.blitz.checked = false; el.shadow.checked = false; } if (state.html) load(state.html, state.file); else syncUrl(); });
el.shadow.addEventListener('change', () => { if (el.shadow.checked) { el.blitz.checked = false; el.litehtml.checked = false; } if (state.html) load(state.html, state.file); else syncUrl(); });
el.reload.addEventListener('click', () => { if (state.html) load(state.html, state.file); });
// The feed is a different app mode: a reload with `?feed=1` (keeping the theme), or back to the single-widget UI.
el.feed.addEventListener('change', () => { location.search = el.feed.checked ? `?feed=1${state.theme === 'dark' ? '&theme=dark' : ''}` : state.theme === 'dark' ? '?theme=dark' : ''; });
el.drop.addEventListener('click', () => el.file.click());
el.file.addEventListener('change', async () => {
  const f = el.file.files?.[0];
  if (f) load(await f.text(), null);
});
el.drop.addEventListener('dragover', (e) => { e.preventDefault(); el.drop.classList.add('over'); });
el.drop.addEventListener('dragleave', () => el.drop.classList.remove('over'));
el.drop.addEventListener('drop', async (e) => {
  e.preventDefault();
  el.drop.classList.remove('over');
  const f = e.dataTransfer?.files[0];
  if (f) load(await f.text(), null);
});

(window as any).__tw = {
  state,
  load: (html: string, name: string | null = null) => load(html, name),
  openFile,
  ready,
  setTheme,
  /** Re-create the WidgetHost from the same HTML (what a "Restart" action in a product would do). */
  restart: () => { if (state.html) load(state.html, state.file); },
  /** The bubble's width in CSS px (200–1200; 0 = the default column) or 'free'. */
  setWidth,
  /** The bubble's presses: 'phone' (default: a hold is the menu, a double tap the reaction, swipes the chat's, unless touch-action claims them), 'desktop' or 'off'. */
  setGestures: (mode: GestureMode) => setGestures(mode),
  /** Where the client draws the message time: 'caption' (default), 'corner' or 'corner-rtl'; live, and for the next widget. */
  setTimeCorner: (mode: TimeMode) => setTimeCorner(mode),
  /** Any content safe area (docs/sdk-v1.md §2.17), drawn and passed as the time control's are; null = back to the control's. */
  setSafeArea: (area: Partial<ContentSafeArea> | null) => setTimeCorner(null, area),
  /** Force the widget's visibility (null = back to the IntersectionObserver / page visibility). */
  setVisible: (v: boolean | null) => { forcedVisible = v; applyVisibility(); },
  /** The Web Storage the dev app keeps for a file (`local` survives a page reload), and forgetting it. */
  storage: (file: string | null = state.file) => storageFor(file),
  clearStorage: (file: string | null = state.file) => clearStorage(file),
  get host() { return host; },
  /** Native renderers only: a tap at CSS px inside the bubble, hit-tested by the native document (the runners cannot click into a canvas). */
  tap: (x: number, y: number) => { const r = BlitzRenderer.current ?? LitehtmlRenderer.current; if (r) { r.touch(0, x, y); r.touch(2, x, y); } },
  /** Blitz renderer only: paint statistics. */
  get blitz() { return BlitzRenderer.current?.stats ?? null; },
  /** litehtml renderer only: paint statistics. */
  get litehtml() { return LitehtmlRenderer.current?.stats ?? null; },
  /** Compare mode: the extra hosts (the renderers the main bubble is not using). */
  get compareHosts() { return compareHosts; },
  /** `?pool=N`: the worker pool's counters. */
  get pool() { return workerPool ? { size: workerPool.size, warm: workerPool.warm, ...workerPool.stats } : null; },
  /** `?feed=1`: the chat feed demo's API (src/feed.ts); null otherwise. */
  feed: null as FeedApi | null,
  /**
   * The mock bot (mock-bot.ts): `bot.bot` (echo, poll, tictactoe, ticker, promo), `bot.mode` (bot, manual, TIMEOUT,
   * RATE_LIMITED, FAILED), `bot.delayMs`, `bot.context` (then `restart()`), `bot.requests`, `bot.dataFor(file)`,
   * `bot.edit(file, { chatData, publicData })`, `bot.answer(record)` / `bot.fail(record, code)` for manual answers.
   */
  bot,
  /** Set the client's context for the widget (the kind of message: its server flags; the chat type; the viewer) and re-create it. */
  setContext: (patch: Partial<MockBot['context']>) => { Object.assign(bot.context, patch); renderBot(); if (state.html) load(state.html, state.file); else syncUrl(); },
};

init().catch((e) => { el.status.textContent = String(e); });
