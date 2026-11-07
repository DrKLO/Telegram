// Dev tool: run a widget through the real sandbox bundle inside QuickJS under
// Node, with a fake host that logs everything. Layout queries get canned
// answers. Usage: pnpm tsx scripts/sandbox-node.ts corpus/compat/tabs.html [--ops] [--ops-file msgs.json] [--events]
// `--ops-file` writes the host → renderer messages a renderer gets (a JSON array: the `theme`
// and `safeArea` messages WidgetHost sends first, then the validated ops as one `ops` message) for
// the native renderer harnesses (renderer-litehtml/tools/run.cpp).
//
// Mirrors the worker glue (src/host/worker.ts) closely enough to debug the shim
// and to drive unit tests of the mutation protocol.

import { build } from 'esbuild';
import { newQuickJSWASMModuleFromVariant, newVariant, type QuickJSContext, type QuickJSHandle } from 'quickjs-emscripten-core';
import variant from '@telegram/widget-sandbox-quickjs';
import fs from 'node:fs';
import path from 'node:path';
import { deflateSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { CpuBudget, opsCostMs, queryLimitHit as queryLimitOf, queryLimitReason } from '../src/host/cpu-budget.ts';
import { GestureCounter } from '../src/host/user-activation.ts';
import type { GestureClaim } from '../src/shared/gesture-policy.ts';
import { formatReports, parseStep, type GestureReport, type GestureStep } from './gesture-run.ts';
import { sizeLine, sizeVerdict, type WidgetSize } from '../src/shared/widget-size.ts';
import {
  DEFAULT_LOCALE, DEFAULT_RESIZE_ANCHOR, HapticGate, PRIVILEGED_METHODS, RequestGate, checkBridgeCall, checkChatType, checkContentSafeArea, checkLocale, checkRequestAnswer, checkStorageCommit,
  allowedInPreview, checkThemeParams, checkViewer, checkWidgetData, requestFailure, requestRefusal, toWidgetTheme, viewerFromList, viewerUser, VIEWER_FIELD_RULES,
  walletFailure, WIDGET_MESSAGE_KINDS, type WalletCall, type WidgetMessageKind, type WidgetViewer,
  type ButtonTokens, type ContentSafeArea, type RequestError, type RequestVerdict, type ResizeAnchor, type StorageCommit, type TextTokens, type ThemeParams, type WidgetAction, type WidgetContext,
  type WidgetData, type WidgetRequest, type WidgetRequestAnswer, type WidgetTheme,
} from '../src/shared/sdk-policy.ts';
import { PROFILE_VERSION } from '../src/shared/profile-tables.ts';
import { PAINT_BACKDROP_BOXES, PAINT_HEAVY_BOXES, checkPaintLimit, paintLimitMessage } from '../src/shared/paint-policy.ts';
import { formatIntl } from '../src/shared/intl.ts';
import { decodeBinaryOps } from '../src/shared/binary-ops-decode.ts';
import { Validator } from '../src/host/validator.ts';
import { LIMIT_DEFAULTS, MAX_ANIM_BATCH_BYTES, MAX_TIMER_DELAY_MS, WASM_INITIAL_PAGES, WASM_PAGE_BYTES, type BridgeEvent, type RenderOp } from '../src/shared/protocol.ts';
import { sandboxBuildOptions } from './sandbox-build.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export interface RunResult {
  /** The theme WidgetHost passes to the renderer at load (`{type:'theme', theme}`, docs/sdk-v1.md §3.4): scheme, checked palette, button and text tokens. */
  theme: WidgetTheme;
  /** What the client draws over the widget as the renderer and the widget got it last (checked; docs/sdk-v1.md §2.17). */
  contentSafeArea: ContentSafeArea;
  /** One entry per `morph` script step: whether the shim applied it. */
  morphed: boolean[];
  ops: unknown[][];
  renderOps: RenderOp[];
  diags: { kind: string; detail: string }[];
  logs: { level: string; text: string }[];
  queries: unknown[];
  /** Bridge calls the glue let through, raw (`[method, args]`). */
  bridge: unknown[];
  /** What WidgetHost's `onAction` would get (docs/sdk-v1.md §4.5). */
  actions: WidgetAction[];
  /** Storage commits the glue accepted, in order (`onStorageCommit` gets one call per area of each). */
  storageCommits: StorageCommit[];
  /** The edge of the bubble that stays put when the height changes: the widget's latest `setResizeAnchor`, `top` until then (docs/sdk-v1.md §2.12). */
  resizeAnchor: ResizeAnchor;
  /** Each anchor the glue accepted, in order (the shim sends only changes). */
  resizeAnchors: ResizeAnchor[];
  /** Whether the widget watches its shared public_data: its latest `watchPublicData` / `unwatchPublicData`, false until then (docs/sdk-v1.md §2.13). */
  watchesPublicData: boolean;
  /** Each change the glue accepted, in order (the shim sends only changes). */
  publicDataWatches: boolean[];
  /**
   * The widget's requests to the bot, in order (docs/sdk-v1.md §2.14): the data, whether it went out without a press,
   * the request gate's verdict (`unsupported` without `onRequest`), and what went back into the widget.
   */
  requests: { data: string; wallet?: true; background: boolean; verdict: RequestVerdict | 'unsupported'; answer?: Omit<Extract<BridgeEvent, { type: 'answer' }>, 'type' | 'id'> }[];
  /** The widget's wallet calls, in order (docs/sdk-v1.md §2.18): the call as checked, the gate's verdict (`foreground`: a press, `granted`: the answer to a call made by a press), and what went back into the widget. */
  walletCalls: { call: WalletCall; verdict: RequestVerdict | 'unsupported'; answer?: Omit<Extract<BridgeEvent, { type: 'answer' }>, 'type' | 'id'> }[];
  /** Texts the glue let through to the client's clipboard (`onCopyText`). */
  copiedTexts: string[];
  timers: number;
  probes: string[];
  /** Tasks the CPU quota or the main-thread window deferred (the harness drops them instead of retrying). */
  throttled: number;
  /** What the run cost the host's main thread: layout-query time plus the modelled cost of the ops sent (cpu-budget.ts `opsCostMs`), ms. */
  mainThreadMs: number;
  /** Set when a counted cap stopped the widget (strict: the validator's `limit`): the reason, as a host's `onDead` gets it. */
  stopped?: string;
  /** The most layout queries any one task made, and their summed answer time (ms): how close a widget comes to the layout-query budget. */
  maxTaskQueries: number;
  /** The most nodes the document held after a batch (elements, text, comments; strict stops past 2000). */
  nodes: number;
  maxTaskQueryMs: number;
  /** Final size of the wasm linear memory: the runtime's footprint, never above INITIAL + memoryLimitBytes. */
  wasmBytes: number;
  /** Canvas ops that arrived as word payloads (`canvasWords`). */
  canvasWordBatches: number;
  /** Ops batches that arrived as binary records (`binaryOps`). */
  binaryBatches: number;
  anims: unknown[][];
  animOps: unknown[][];
  /** Ops batches, anim batches and sync queries in the order the shim sent them. */
  stream: { kind: 'ops' | 'anim' | 'query'; json: string }[];
  /** With `gestures` or `layout`: the content height the renderer laid out at `width` (before the bubble's 50–600 clamp). */
  height?: number;
  /** One report per `gestures` step: target, claim, the policy's outcome, what the widget received, a hint. */
  gestures?: GestureReport[];
  /**
   * With `gestures` or `layout`: the heavy boxes the renderer counted (paint-policy.ts; strict only): at the end and the
   * most at once, and of them with backdrop-filter. Past 16 (2) the renderer reports the limit and `stopped` is set.
   */
  paint?: { heavy: number; backdrop: number; peakHeavy: number; peakBackdrop: number };
  /** Event types the widget listens for (the shim's `listen`, validated). */
  listens: string[];
  /** The HTML's size as the server counts it: zlib at level 9 and UTF-8 bytes (widget-size.ts has the limits). */
  size: WidgetSize;
}

/** Bytes after zlib at level 9 (the server's: PHP gzcompress($html, 9)) and of the HTML as UTF-8. */
export function widgetSize(html: string): WidgetSize {
  const raw = Buffer.from(html, 'utf8');
  return { zlib: deflateSync(raw, { level: 9 }).length, raw: raw.length };
}

/** The JSON command arrays a canvas word payload stands for (docs/protocol.md, "Canvas words"). */
export function decodeCanvasWords(all: number[], joined: string): any[][] {
  const m = all[all.length - 1];
  const lengths = all.slice(all.length - 1 - m, all.length - 1);
  const strings: string[] = [];
  let at = 0;
  for (const n of lengths) { strings.push(joined.slice(at, at + n)); at += n; }
  const f = all.slice(0, all.length - 1 - m);
  const kinds = ['c', 's', 'r', 'g', 'gs', 'pn', 'pc'];
  const num = (x: number) => (Number.isFinite(x) ? x : null);
  const out: any[][] = [];
  let j = 0;
  while (j < f.length) {
    const kind = f[j], name = f[j + 1], ident = f[j + 2], argc = f[j + 3];
    j += 4;
    const a: any[] = [kinds[kind]];
    if (kind >= 3) a.push(ident);
    if (name >= 0) a.push(strings[name]);
    for (let q = 0; q < argc; q++) {
      const tag = f[j], x = f[j + 1];
      j += 2;
      if (tag === 0) a.push(num(x));
      else if (tag === 1) a.push(strings[x]);
      else if (tag === 7) a.push(x !== 0);
      else if (tag === 6) a.push(null);
      else if (tag === 2) a.push({ g: x });
      else if (tag === 3) a.push({ p: x });
      else if (tag === 4) a.push({ n: x });
      else { a.push(f.slice(j, j + x).map(num)); j += x; }
    }
    out.push(a);
  }
  return out;
}

export async function bundleSandbox(): Promise<string> {
  const out = await build(sandboxBuildOptions(root));
  return out.outputFiles![0].text;
}

let cachedBundle: string | null = null;
/** Widget clocks tick in 100 µs steps, as in src/host/worker.ts (`now` and frame times alike). */
const coarseNowMs = (ms: number) => Math.floor(ms / 0.1) * 0.1;

export interface RunOptions {
  verbose?: boolean;
  /** Events to inject after load: [{type:'click', targetId: 5}] etc. `gesture: n` marks a trusted user gesture (the web host's number), as for user activation. */
  events?: Record<string, unknown>[];
  /** How many rounds of pending timers to fire after load. */
  timerRounds?: number;
  /** Custom answers for sync queries. */
  answer?: (q: any) => unknown;
  /** Continuous-execution slice per task (default 10 s, so a harness run never trips it); lower it to exercise the CPU and layout-query limits. */
  cpuLimitMs?: number;
  /** Expressions evaluated inside the VM after load; results land in RunResult.probes. */
  probes?: string[];
  /** false: the shim always replaces a sole text child on textContent writes (the in-place update off). */
  inPlaceText?: boolean;
  /** false: getAttribute('class') and same-value className writes take the full path. */
  attrFast?: boolean;
  /** Live-timer cap passed to the shim (default LIMIT_DEFAULTS.maxTimers). */
  maxTimers?: number;
  /** Initial visibility (default true): a hidden widget gets no frames and cannot use a gesture, as in the worker and the web host. */
  visible?: boolean;
  /** Steps run after the timer rounds: visibility and theme changes and probes, in order (probe results land in RunResult.probes before `probes`). */
  script?: ({ visible: boolean } | { probe: string } | { morph: string } | { theme: 'light' | 'dark'; themeParams?: Partial<ThemeParams> } | { data: WidgetData } | { safeArea: Partial<ContentSafeArea> })[];
  /** The colour scheme at start (default light). */
  theme?: 'light' | 'dark';
  /** The client's palette (checked as WidgetHost does: unknown keys and non-#rrggbb values are dropped). */
  themeParams?: Record<string, unknown>;
  /** The client's button and text tokens (docs/sdk-v1.md §2.4): no effect on the sandbox, they go to the renderer in RunResult.theme. */
  button?: ButtonTokens;
  text?: TextTokens;
  /** The client's UI language (default en-US). */
  locale?: string;
  /** What the client draws over the widget (docs/sdk-v1.md §2.17): the time's band and corner, in CSS px; 0 by default. `{ safeArea }` script steps change it. */
  contentSafeArea?: Partial<ContentSafeArea>;
  /** The client's Web Storage preload; over the quota it is dropped whole (`storage-rejected`). */
  storage?: unknown;
  /** The bot's `chatData` and `publicData` at start (docs/sdk-v1.md §2.13); `{ data }` script steps are the client's `setData`. */
  data?: WidgetData;
  /** The server's flags for the message (`isCopy`, `canQuery`, `backgroundQuery`), the chat type, the viewer; `WIDGET_MESSAGE_KINDS` has them per kind of message. */
  context?: WidgetContext;
  /**
   * The client's side of a request: the server's answer `{ result? }`, sync or a promise; throw or reject with
   * 'TIMEOUT', 'RATE_LIMITED' or 'FAILED'.
   * Answers go into the widget at the next step of the run (after the events, each timer round, each script step and
   * each press). Without it every request is refused, as on the hosts.
   */
  onRequest?: (request: WidgetRequest) => WidgetRequestAnswer | Promise<WidgetRequestAnswer>;
  /**
   * The client's side of a wallet call (only with `context.wallet`): the bot's answer to the call's outcome `{ result? }`,
   * sync or a promise; throw or reject with 'REJECTED', 'BAD_REQUEST' or a request's failure. Without it every call is refused.
   */
  onWallet?: (call: WalletCall) => WidgetRequestAnswer | Promise<WidgetRequestAnswer>;
  /** CPU quota per second (default: effectively unlimited, like the harness's 10 s cpuLimitMs; wasm under Node is slow). */
  cpuQuotaMs?: number;
  /** Main-thread window per second (default: effectively unlimited too: the harness runs frames back to back, faster than a clock). */
  mainThreadQuotaMs?: number;
  /** Validator CSS scope: 'shadow' for ops meant for the Shadow DOM renderer (css-sanitizer.ts). */
  cssScope?: 'document' | 'shadow';
  /** Runtime heap cap (default LIMIT_DEFAULTS.memoryLimitBytes), enforced like the worker does: wasm Memory maximum + setMemoryLimit. */
  memoryLimitBytes?: number;
  /** Sandbox bundle source to run instead of the one built from packages/core/src (scripts/ops-diff.ts). */
  bundle?: string;
  /** `__host` kinds the harness does not know, with their argument. */
  unknownHost?: (kind: string, arg: string) => void;
  /** Answers for `intl` requests (the request as the shim sends it) instead of Node's own Intl; undefined falls through to it. */
  intl?: (req: any) => string | undefined;
  /** Host clock (`now`) and random bytes (`random`) instead of performance.now / Math.random, for reproducible runs. */
  now?: () => number;
  random?: (count: number) => number[];
  /** Advertise `canvasWords` and install `__hostCanvas`; payloads are decoded back into JSON commands before validation. */
  canvasWords?: boolean;
  policy?: 'compat' | 'strict';
  /** Advertise `binaryOps` and install `__hostOps`; batches are decoded back into JSON ops before validation. */
  binaryOps?: boolean;
  canvasRect?: boolean;
  webAnimations?: boolean;
  frames?: number;
  /** Advertise `morphUpdates` (in-place document updates through the `morph` dispatch). */
  morphUpdates?: boolean;
  /** The bubble's width in CSS px (default 400): the widget's innerWidth, and the renderer's page with `gestures` / `layout`. */
  width?: number;
  /** The widget's devicePixelRatio (default 1; a phone's is 2 or 3). */
  dpr?: number;
  /** Presses after the timer rounds, on the real renderer core in headless Chromium (gesture-run.ts); reported in RunResult.gestures. */
  gestures?: GestureStep[];
  /** The renderer plays a desktop client's bubble (a mouse) instead of a phone's (a finger). */
  desktop?: boolean;
  /** Lay the widget out in the renderer even without gestures (RunResult.height). */
  layout?: boolean;
}

export async function runWidget(html: string, opts: RunOptions = {}): Promise<RunResult> {
  const bundle = opts.bundle ?? (cachedBundle ??= await bundleSandbox());
  // As WidgetHost: a preview (an inline result not sent yet) asks nothing, takes no input and keeps no storage.
  const preview = opts.context?.preview === true;
  const copy = opts.context?.isCopy === true && !preview;
  const canQuery = opts.context?.canQuery === true && !preview;
  const wallet = opts.context?.wallet === true && canQuery;
  const dataDiags: { kind: string; detail: string }[] = [];
  /** As WidgetHost: the full state of both strings, a copy without chatData, a field over the limit null and reported. */
  const checkData = (data: unknown) => {
    const d = checkWidgetData(data, copy);
    if (d.rejected.length) dataDiags.push({ kind: 'data-rejected', detail: `${d.rejected.join(' and ')}: not text of at most 1024 bytes` });
    return { chatData: d.chatData, publicData: d.publicData };
  };
  /** As WidgetHost: the viewer as the widget sees it, a field that breaks its rule left out and reported. */
  const checkViewerFields = (viewer: WidgetViewer | undefined) => {
    const v = checkViewer(viewerUser(viewer));
    for (const field of v.rejected) dataDiags.push({ kind: 'data-rejected', detail: `viewer.${field}: ${VIEWER_FIELD_RULES[field]}; the widget does not see it` });
    return v.viewer;
  };
  // Same memory cap as src/host/worker.ts: the emscripten build cannot count its
  // heap, so the wasm Memory's maximum is the limit (see the comment there).
  const memoryLimitBytes = opts.memoryLimitBytes ?? LIMIT_DEFAULTS.memoryLimitBytes;
  const wasmMemory = new WebAssembly.Memory({ initial: WASM_INITIAL_PAGES, maximum: WASM_INITIAL_PAGES + Math.ceil(memoryLimitBytes / WASM_PAGE_BYTES) });
  const QuickJS = await newQuickJSWASMModuleFromVariant(newVariant(variant, { wasmMemory }));
  const rt = QuickJS.newRuntime();
  rt.setMemoryLimit(memoryLimitBytes);
  rt.setMaxStackSize(1 << 20);
  let deadline = Date.now() + 10_000;
  rt.setInterruptHandler(() => Date.now() > deadline);
  // The layout-query limit of the worker and the native glue (cpu-budget.ts queryLimitHit): the harness answers queries in-process, so the
  // time is the answer callback's, and the accounting is kept so a slow `answer` reproduces the kill.
  let taskQueryMs = 0;
  let taskQueries = 0;
  let queryLimitHit: 'count' | 'time' | null = null;
  let currentKind = 'boot';
  const vm = rt.newContext();
  const res: RunResult = { theme: toWidgetTheme({ colorScheme: opts.theme ?? 'light', params: opts.themeParams, button: opts.button, text: opts.text }), contentSafeArea: checkContentSafeArea(opts.contentSafeArea), ops: [], renderOps: [], diags: [], logs: [], queries: [], bridge: [], actions: [], storageCommits: [], resizeAnchor: DEFAULT_RESIZE_ANCHOR, resizeAnchors: [], watchesPublicData: false, publicDataWatches: [], requests: [], walletCalls: [], copiedTexts: [], timers: 0, probes: [], throttled: 0, mainThreadMs: 0, maxTaskQueries: 0, nodes: 0, maxTaskQueryMs: 0, wasmBytes: 0, canvasWordBatches: 0, binaryBatches: 0, anims: [], animOps: [], stream: [], morphed: [], listens: [], size: widgetSize(html) };
  const config = { ...LIMIT_DEFAULTS, features: [...(opts.canvasWords ? ['canvasWords'] : []), ...(opts.binaryOps ? ['binaryOps'] : []), ...(opts.webAnimations ? ['webAnimations'] : []), ...(opts.morphUpdates ? ['morphUpdates'] : []), ...(opts.inPlaceText === false ? ['noInPlaceText'] : []), ...(opts.attrFast === false ? ['noAttrFast'] : []), ...(opts.canvasRect === false ? ['noCanvasRect'] : [])], memoryLimitBytes, policy: opts.policy === 'compat' ? 'compat' as const : 'strict' as const, cpuLimitMs: opts.cpuLimitMs ?? 10_000, cpuQuotaMs: opts.cpuQuotaMs ?? 10_000, mainThreadQuotaMs: opts.mainThreadQuotaMs ?? 10_000, maxTimers: opts.maxTimers ?? LIMIT_DEFAULTS.maxTimers, visible: opts.visible !== false, viewport: { innerWidth: opts.width ?? 400, innerHeight: 300, dpr: opts.dpr ?? 1 }, theme: opts.theme ?? 'light', themeParams: checkThemeParams(opts.themeParams), locale: checkLocale(opts.locale) ?? DEFAULT_LOCALE, contentSafeArea: res.contentSafeArea, profile: PROFILE_VERSION, storage: {} as StorageCommit, ...checkData(opts.data), isCopy: copy, canQuery, isPreview: preview, chatType: preview || copy ? null : checkChatType(opts.context?.chatType), viewer: canQuery ? checkViewerFields(opts.context?.viewer) : null, wallet };
  res.diags.push(...dataDiags.splice(0));
  if (opts.storage !== undefined && !preview) {
    const preload = checkStorageCommit(opts.storage);
    if (preload) config.storage = preload;
    else res.diags.push({ kind: 'storage-rejected', detail: 'the preload is not { local, session } objects of strings within the quota' });
  }
  const haptics = new HapticGate();
  const requestGate = new RequestGate();
  /** A background request made off screen, sent when the widget appears. */
  let heldRequest = null as (() => void) | null;
  const now = () => (opts.now ? opts.now() : performance.now());
  /** Answers on their way into the widget, delivered in order at the next step of the run. */
  const answers: Promise<BridgeEvent>[] = [];
  const budget = new CpuBudget(config);
  const mainBudget = new CpuBudget({ ...config, cpuQuotaMs: config.mainThreadQuotaMs, cpuKillAfterMs: 0 });
  let taskOpsCostMs = 0;
  const validator = new Validator((kind, detail) => res.diags.push({ kind: 'validator:' + kind, detail }), { colorScheme: 'light', scope: opts.cssScope ?? 'document' });
  const policy = opts.policy === 'compat' ? 'compat' : 'strict';
  validator.setPolicy(policy);
  const timers = new Map<number, number>();
  let activeGesture = 0;
  let consumedGesture = 0;
  let pdKeys: string[] = [];
  let visible = config.visible;
  const log = (...a: unknown[]) => opts.verbose && console.log(...a);

  const hostFn = vm.newFunction('__host', (k: QuickJSHandle, a?: QuickJSHandle) => {
    const kind = vm.getString(k);
    const arg = a ? vm.getString(a) : '';
    switch (kind) {
      case 'ops': {
        acceptOps(JSON.parse(arg));
        return vm.undefined;
      }
      case 'query': {
        const q = JSON.parse(arg);
        res.queries.push(q);
        res.stream.push({ kind: 'query', json: arg });
        if (queryLimitHit) return vm.newString('{"error":"query budget exceeded"}');
        const clean = validator.validateQuery(q);
        const q0 = performance.now();
        const answer = clean ? opts.answer?.(clean) ?? defaultAnswer(clean, config.viewport) : { error: 'invalid query' };
        const dt = performance.now() - q0;
        taskQueries++;
        deadline += dt; // time spent in the answer is not widget CPU time (as in the worker); the query limit below bounds it instead
        taskQueryMs += dt;
        queryLimitHit = queryLimitOf(taskQueries, taskQueryMs);
        if (queryLimitHit) deadline = -Infinity;
        log('query', JSON.stringify(q), '→', JSON.stringify(answer));
        return vm.newString(JSON.stringify(answer));
      }
      case 'now': return vm.newNumber(opts.now ? opts.now() : coarseNowMs(performance.now()));
      case 'timer': {
        const [id, delay] = JSON.parse(arg);
        // An id that has a timer replaces it, as in the other glue copies.
        if (!timers.has(id) && timers.size >= config.maxTimers) { res.diags.push({ kind: 'timer-limit-enforced', detail: `glue refused a timer beyond maxTimers=${config.maxTimers}` }); return vm.undefined; }
        const ms = Math.min(Math.max(Number(delay) || 0, 0), MAX_TIMER_DELAY_MS);
        // Rounds fire timers by delay, as a clock would; hidden ones as the other glue copies clamp them (1 Hz, in order).
        timers.set(id, visible ? ms : Math.max(ms, 1000 + ms / 10));
        return vm.undefined;
      }
      case 'clearTimer': timers.delete(Number(arg)); return vm.undefined;
      case 'anim': {
        const list = JSON.parse(arg);
        res.anims.push(list);
        res.stream.push({ kind: 'anim', json: arg });
        res.animOps.push(arg.length > MAX_ANIM_BATCH_BYTES ? [] : validator.validateAnim(list));
        return vm.undefined;
      }
      case 'log': { const i = arg.indexOf(':'); res.logs.push({ level: arg.slice(0, i), text: arg.slice(i + 1) }); log('[' + arg.slice(0, i) + ']', arg.slice(i + 1)); return vm.undefined; }
      case 'diag': { const i = arg.indexOf(':'); res.diags.push({ kind: arg.slice(0, i), detail: arg.slice(i + 1) }); log('[diag]', arg); return vm.undefined; }
      case 'bridge': {
        // As WidgetHost (docs/sdk-v1.md §3.2): the method and its arguments, user activation for links, the haptic limit.
        let call: unknown;
        try { call = JSON.parse(arg); } catch {}
        const method = Array.isArray(call) ? call[0] : undefined;
        const action = Array.isArray(call) ? checkBridgeCall(method, call[1]) : null;
        if (!action) { res.diags.push({ kind: 'bridge-rejected', detail: `${String(method).slice(0, 64)}: unknown method or malformed arguments` }); return vm.undefined; }
        if (preview && !allowedInPreview(method)) {
          res.diags.push({ kind: 'preview-blocked', detail: `${String(method)} ignored in a preview` });
          if (action.kind === 'request' || action.kind === 'wallet') answers.push(Promise.resolve({ type: 'answer', id: action.id, error: 'NOT_ALLOWED', reason: 'preview' }));
          return vm.undefined;
        }
        // Not an action: the host keeps the latest anchor next to the height.
        if (action.kind === 'setResizeAnchor') {
          res.resizeAnchor = action.edge;
          res.resizeAnchors.push(action.edge);
          log('[bridge]', arg);
          return vm.undefined;
        }
        // Not an action either: the host keeps whether the widget watches its public_data (the client polls only then).
        if (action.kind === 'watchPublicData') {
          if (action.on !== res.watchesPublicData) { res.watchesPublicData = action.on; res.publicDataWatches.push(action.on); }
          log('[bridge]', arg);
          return vm.undefined;
        }
        // As WidgetHost.request: the gate (a press, or the background rules; one in flight), then no client side → refused.
        if (action.kind === 'request' || action.kind === 'wallet') {
          const entry: { verdict: RequestVerdict | 'unsupported'; answer?: Omit<Extract<BridgeEvent, { type: 'answer' }>, 'type' | 'id'> } = action.kind === 'request'
            ? { data: action.data, ...(action.wallet ? { wallet: true as const } : {}), background: false, verdict: 'no-activation' } as RunResult['requests'][number]
            : { call: action.call, verdict: 'no-activation' } as RunResult['walletCalls'][number];
          if (action.kind === 'request') res.requests.push(entry as RunResult['requests'][number]);
          else res.walletCalls.push(entry as RunResult['walletCalls'][number]);
          const label = action.kind === 'request' ? 'request' : 'wallet.' + action.call.method;
          const settle = (p: Promise<Omit<Extract<BridgeEvent, { type: 'answer' }>, 'type' | 'id'>>) =>
            answers.push(p.then((a) => { entry.answer = a; return { type: 'answer', id: action.id, ...a }; }));
          const refuse = (reason: RequestVerdict | 'unsupported') => {
            res.diags.push({ kind: 'request-blocked', detail: `${label} refused (${reason})` });
            settle(Promise.resolve({ error: (reason === 'unsupported' ? null : requestRefusal(reason)) ?? 'NOT_ALLOWED', reason }));
          };
          /** As WidgetHost.settle: the gate is free again, and an answer brings the grant when the call had a press or the grant. */
          const fromClient = (outcome: Promise<WidgetRequestAnswer>, failure: (reason: unknown) => RequestError) => settle(outcome.then((a) => {
            const checked = checkRequestAnswer(a);
            if (!checked) { requestGate.finish(); res.diags.push({ kind: 'answer-rejected', detail: 'result over 1024 bytes or not text' }); return { error: 'INVALID_ANSWER' as const }; }
            requestGate.finish(now());
            return { result: checked.result };
          }, (reason) => { requestGate.finish(); return { error: failure(reason) }; }));
          const pressed = policy === 'compat' || (visible && activeGesture > consumedGesture);
          entry.verdict = action.kind === 'request'
            ? requestGate.decide(now(), { gesture: pressed, visible, canQuery, backgroundQuery: opts.context?.backgroundQuery === true, withWallet: action.wallet === true, wallet })
            : requestGate.decideWallet(now(), { gesture: pressed, visible, canQuery, wallet });
          if (requestRefusal(entry.verdict)) { refuse(entry.verdict); return vm.undefined; }
          if (entry.verdict === 'foreground' && policy === 'strict') { consumedGesture = activeGesture; activeGesture = 0; }
          if (action.kind === 'wallet') {
            const onWallet = opts.onWallet;
            if (!onWallet) { requestGate.finish(); entry.verdict = 'unsupported'; refuse('unsupported'); return vm.undefined; }
            fromClient(new Promise<WidgetRequestAnswer>((resolve) => resolve(onWallet(action.call))), walletFailure);
            log('[wallet]', arg);
            return vm.undefined;
          }
          const sendToClient = () => {
            (entry as RunResult['requests'][number]).background = entry.verdict !== 'foreground';
            const onRequest = opts.onRequest;
            if (!onRequest) { requestGate.finish(); entry.verdict = 'unsupported'; refuse('unsupported'); return; }
            fromClient(new Promise<WidgetRequestAnswer>((resolve) => resolve(onRequest(action.wallet ? { data: action.data, wallet: true } : { data: action.data }))), requestFailure);
          };
          // Off screen: it waits for the widget to appear (a { visible: true } step), the background request of that appearance.
          if (entry.verdict === 'held') heldRequest = sendToClient;
          else sendToClient();
          log('[request]', arg);
          return vm.undefined;
        }
        if (policy === 'strict' && PRIVILEGED_METHODS.has(String(method))) {
          if (!visible || !(activeGesture > consumedGesture)) {
            res.diags.push({ kind: 'activation-blocked', detail: `${method} ignored outside a tap on the visible widget` });
            return vm.undefined;
          }
          consumedGesture = activeGesture;
          activeGesture = 0;
        }
        if (action.kind === 'haptic') {
          const d = haptics.decide(now(), visible);
          if (d === 'report') res.diags.push({ kind: 'haptic-limit', detail: visible ? 'over the rate limit, or outside a touch after a 1 s series' : 'hidden widget' });
          if (d !== 'play') return vm.undefined;
        }
        if (action.kind === 'copyText') {
          res.copiedTexts.push(action.text);
          log('[copyText]', arg);
          return vm.undefined;
        }
        res.bridge.push(call);
        res.actions.push(action);
        log('[bridge]', arg);
        return vm.undefined;
      }
      case 'storage': {
        if (preview) return vm.undefined; // a preview's storage is never committed
        let commit: StorageCommit | null = null;
        try { commit = checkStorageCommit(JSON.parse(arg)); } catch {}
        if (!commit || (!commit.local && !commit.session)) res.diags.push({ kind: 'storage-rejected', detail: arg.slice(0, 200) });
        else res.storageCommits.push(commit);
        log('[storage]', arg.slice(0, 2000));
        return vm.undefined;
      }
      case 'listen': { try { res.listens = validator.validateListen(JSON.parse(arg)); } catch {} return vm.undefined; }
      case 'pd': { try { pdKeys = validator.validatePreventDefault(JSON.parse(arg)); } catch {} return vm.undefined; }
      case 'intl': { const req = JSON.parse(arg); let v = opts.intl?.(req); if (v === undefined) { try { v = formatIntl(req); } catch { v = ''; } } return vm.newString(String(v)); }
      case 'random': return vm.newString(JSON.stringify(opts.random ? opts.random(Number(arg) | 0) : Array.from({ length: Number(arg) | 0 }, () => (Math.random() * 256) | 0)));
      default: opts.unknownHost?.(kind, arg); return vm.undefined;
    }
  });
  vm.setProp(vm.global, '__host', hostFn);
  hostFn.dispose();
  const canvasPayloads: any[][][] = [];
  // vm.getString stops at a NUL; strings handed over by length go through encodeURIComponent instead.
  const encoder = vm.unwrapResult(vm.evalCode('(s) => encodeURIComponent(s)', 'harness-encode.js'));
  const fullString = (h: QuickJSHandle): string => {
    const r = vm.callFunction(encoder, vm.undefined, h);
    if (r.error) { r.error.dispose(); return vm.getString(h); }
    const text = decodeURIComponent(vm.getString(r.value));
    r.value.dispose();
    return text;
  };
  const acceptOps = (ops: any[]) => {
    // Each payload is taken once (CanvasWords.swift `placeholders`): a repeated key keeps its `bin` and the validator rejects it.
    for (const op of ops) if (op && op.op === 'canvas' && typeof op.bin === 'number' && canvasPayloads[op.bin]) { op.cmds = canvasPayloads[op.bin]; delete canvasPayloads[op.bin]; delete op.bin; res.canvasWordBatches++; }
    canvasPayloads.length = 0;
    res.ops.push(ops);
    res.stream.push({ kind: 'ops', json: JSON.stringify(ops) });
    if (Array.isArray(ops)) taskOpsCostMs += opsCostMs(ops.length, ops.reduce((n, op) => n + (op && op.op === 'canvas' && Array.isArray(op.cmds) ? op.cmds.length : 0), 0));
    const out = validator.processOps(ops);
    res.nodes = Math.max(res.nodes, validator.nodeCount - 1);
    if (validator.stopped && res.stopped === undefined) {
      res.stopped = validator.stopped;
      log('STOPPED', validator.stopped);
    }
    res.renderOps.push(...out);
    log(`ops (${ops.length}) →`, JSON.stringify(out).slice(0, 2000));
  };
  if (opts.binaryOps) {
    const internTable: string[] = [];
    const opsFn = vm.newFunction('__hostOps', (w: QuickJSHandle, s: QuickJSHandle) => {
      const num = (name: string) => { const h = vm.getProp(w, name); const v = vm.getNumber(h); h.dispose(); return v; };
      const bufferHandle = vm.getProp(w, 'buffer');
      const bytes = vm.getArrayBuffer(bufferHandle);
      const copy = bytes.value.slice();
      bytes.dispose();
      bufferHandle.dispose();
      const start = num('byteOffset') / 4;
      const words = new Int32Array(copy.buffer, copy.byteOffset, copy.byteLength / 4).subarray(start, start + num('length'));
      try {
        acceptOps(decodeBinaryOps(words, fullString(s), internTable));
        res.binaryBatches++;
      } catch (e) {
        res.diags.push({ kind: 'protocol-violation', detail: 'malformed binary ops batch (' + (e as Error).message + ')' });
      }
      return vm.undefined;
    });
    vm.setProp(vm.global, '__hostOps', opsFn);
    opsFn.dispose();
  }
  if (opts.canvasWords) {
    const canvasFn = vm.newFunction('__hostCanvas', (k: QuickJSHandle, w: QuickJSHandle, s: QuickJSHandle) => {
      const key = vm.getNumber(k);
      const num = (name: string) => { const h = vm.getProp(w, name); const v = vm.getNumber(h); h.dispose(); return v; };
      const bufferHandle = vm.getProp(w, 'buffer');
      const bytes = vm.getArrayBuffer(bufferHandle);
      const copy = bytes.value.slice();
      bytes.dispose();
      bufferHandle.dispose();
      const start = num('byteOffset') / 8;
      const words = Array.from(new Float64Array(copy.buffer, copy.byteOffset, copy.byteLength / 8).subarray(start, start + num('length')));
      canvasPayloads[key] = decodeCanvasWords(words, fullString(s));
      return vm.undefined;
    });
    vm.setProp(vm.global, '__hostCanvas', canvasFn);
    canvasFn.dispose();
  }
  const binFn = vm.newFunction('__hostBinary', () => vm.null);
  vm.setProp(vm.global, '__hostBinary', binFn);
  binFn.dispose();

  const fail = (err: QuickJSHandle, where: string) => {
    const e = vm.dump(err);
    err.dispose();
    let text = e && typeof e === 'object' ? `${e.name}: ${e.message}\n${e.stack ?? ''}` : String(e);
    if (queryLimitHit && /interrupted/.test(text)) {
      text = queryLimitReason(queryLimitHit, currentKind);
      res.diags.push({ kind: 'query-limit', detail: text });
    }
    res.logs.push({ level: 'error', text: `${where}: ${text}` });
    log('ERROR', where, text);
  };
  const evalCode = (code: string, name: string, type: 'global' | 'module' = 'global') => {
    const r = vm.evalCode(code, name, { type });
    if (r.error) fail(r.error, name);
    else r.value.dispose();
  };
  const bundleResult = vm.evalCode(bundle, 'sandbox-bundle.js', { type: 'global' });
  if (bundleResult.error) {
    fail(bundleResult.error, 'sandbox-bundle.js');
    encoder.dispose();
    vm.dispose();
    rt.dispose();
    throw new Error('sandbox bundle failed to load');
  }
  bundleResult.value.dispose();
  const dispatch = vm.getProp(vm.global, '__twDispatch');
  if (vm.typeof(dispatch) !== 'function') throw new Error('bundle did not define __twDispatch');
  const hide = vm.evalCode("(function(g){for(const n of ['__host','__hostBinary','__hostCanvas','__hostOps','__twDispatch'])if(!delete g[n])throw new Error(n);})(globalThis);", 'hide-host.js');
  if (hide.error) {
    fail(hide.error, 'hide-host.js');
    throw new Error('sandbox bundle failed to load');
  }
  hide.value.dispose();
  const tw = (kind: string, arg = ''): string | null => {
    const k = vm.newString(kind);
    const a = vm.newString(arg);
    const r = vm.callFunction(dispatch, vm.undefined, k, a);
    k.dispose();
    a.dispose();
    if (r.error) { fail(r.error, kind); return null; }
    const v = vm.typeof(r.value) === 'string' ? vm.getString(r.value) : null;
    r.value.dispose();
    return v;
  };
  const drain = () => { for (let i = 0; i < 100; i++) { const r = rt.executePendingJobs(); if (r.error) { fail(r.error, 'job'); continue; } if (r.value === 0) break; } };
  const settle = () => { for (let i = 0; i < 20; i++) { drain(); const more = tw('endTick'); if (more !== '1' && !rt.hasPendingJob()) break; } };
  const pump = () => {
    for (let i = 0; i < 1000; i++) {
      const next = tw('nextScript');
      if (!next) break;
      const s = JSON.parse(next);
      let ok = true;
      // External scripts never run: nothing is fetched or bundled (profile 1).
      if (s.src !== undefined) { ok = false; res.diags.push({ kind: 'script-skipped', detail: s.src }); }
      else evalCode(s.code, `inline-${s.id}.js`, s.type);
      settle();
      tw('scriptDone', JSON.stringify([s.id, ok]));
      settle();
    }
  };
  /** Same policy as the worker: decide → run with the slice → record. A deferred task is dropped and counted. */
  const task = (kind: string, fn: () => void) => {
    if (res.stopped !== undefined) return; // the host stopped the widget
    const now = performance.now();
    const d = budget.decide(kind, now);
    if (!d.run) { res.throttled++; res.diags.push({ kind: 'cpu-throttled', detail: `${kind} deferred until ${d.retryAt.toFixed(0)}` }); return; }
    const main = mainBudget.decide(kind, now);
    if (!main.run) { res.throttled++; res.diags.push({ kind: 'cpu-throttled', detail: `${kind} deferred until ${main.retryAt.toFixed(0)} (main-thread window: layout queries and ops)` }); return; }
    deadline = Date.now() + d.sliceMs;
    currentKind = kind;
    taskQueryMs = 0;
    taskQueries = 0;
    taskOpsCostMs = 0;
    queryLimitHit = null;
    const diags0 = res.diags.length;
    fn(); settle(); pump();
    // Past the limit but done before the interrupt landed (a loop that ended on the error answers): the glue copies stop it all the same.
    if (queryLimitHit && !res.diags.slice(diags0).some((d) => d.kind === 'query-limit')) {
      const text = queryLimitReason(queryLimitHit, kind);
      res.diags.push({ kind: 'query-limit', detail: text });
      res.logs.push({ level: 'error', text: `${kind}: ${text}` });
    }
    res.maxTaskQueries = Math.max(res.maxTaskQueries, taskQueries);
    res.maxTaskQueryMs = Math.max(res.maxTaskQueryMs, taskQueryMs);
    budget.record(kind, performance.now() - now - taskQueryMs, performance.now());
    mainBudget.record(kind, taskQueryMs + taskOpsCostMs, performance.now());
    res.mainThreadMs += taskQueryMs + taskOpsCostMs;
  };

  task('boot', () => tw('boot', JSON.stringify({ html, config })));
  task('boot', () => tw('loaded'));
  validator.markLoaded(); // as a host does on `loaded`
  /** Answers settled by now go into the widget, one `bridge` task each, in the order of the requests (at most 100 per step: a widget that asks again from every answer would never end). */
  const deliver = async () => {
    for (let n = 0; n < 100 && answers.length; n++) {
      const event = await answers.shift()!;
      task('bridge', () => tw('bridge', JSON.stringify(event)));
    }
  };
  await deliver();
  const inject = (ev: Record<string, unknown>) => {
    if (preview) return; // a preview takes no input
    const { gesture, ...event } = ev as Record<string, unknown> & { gesture?: number };
    // Without a renderer, an injected trusted event stands for the renderer's press (claim) and release (haptic gate).
    if (typeof gesture === 'number' && gesture > 0) {
      const at = now();
      if (event.type === 'pointerdown' || event.type === 'mousedown') haptics.press(at);
      else haptics.release(at);
    }
    task('event', () => {
      activeGesture = visible && typeof gesture === 'number' && gesture > consumedGesture ? gesture : 0;
      try {
        tw('event', JSON.stringify(event));
      } finally {
        activeGesture = 0;
      }
    });
  };
  for (const ev of opts.events ?? []) { inject(ev); await deliver(); }
  // Due timers by delay; equal delays in the order they were set.
  const dueTimers = () => [...timers.entries()].sort((a, b) => a[1] - b[1]).map(([id]) => id);
  const timerRound = () => {
    const due = dueTimers();
    for (const id of due) { timers.delete(id); res.timers++; task('timer', () => tw('timer', String(id))); }
    if (visible) task('frame', () => tw('frame', String(coarseNowMs(performance.now()))));
  };
  for (let round = 0; round < (opts.timerRounds ?? 2); round++) { timerRound(); await deliver(); }
  if (opts.gestures?.length || opts.layout) await pressInRenderer();
  for (let round = 0; round < (opts.frames ?? 0); round++) {
    const due = dueTimers();
    for (const id of due) { timers.delete(id); res.timers++; task('timer', () => tw('timer', String(id))); }
    if (visible) task('frame', () => tw('frame', String(coarseNowMs(performance.now()))));
    await deliver();
  }
  const probe = (code: string) => {
    const r = vm.evalCode(`(function(){ try { return String(${code}); } catch (e) { return 'THREW ' + e; } })()`, 'probe.js');
    if (r.error) { res.probes.push('ERROR ' + JSON.stringify(vm.dump(r.error))); r.error.dispose(); }
    else { res.probes.push(vm.getString(r.value)); r.value.dispose(); }
    settle();
  };
  for (const step of opts.script ?? []) {
    if ('visible' in step) {
      const appears = step.visible && !visible;
      visible = step.visible;
      // The held request goes to the client before the widget hears it is visible, as in every other glue copy.
      if (appears && requestGate.show(now()) && heldRequest) { const send = heldRequest; heldRequest = null; send(); }
      task('visibility', () => tw('visibility', JSON.stringify({ visible: step.visible })));
    }
    else if ('morph' in step) task('morph', () => { res.morphed.push(tw('morph', JSON.stringify({ html: step.morph })) === '1'); });
    else if ('theme' in step) task('theme', () => tw('theme', JSON.stringify({ theme: step.theme, themeParams: checkThemeParams(step.themeParams) })));
    else if ('safeArea' in step) {
      res.contentSafeArea = checkContentSafeArea(step.safeArea);
      const inset = res.contentSafeArea;
      task('safeArea', () => tw('safeArea', JSON.stringify(inset)));
    }
    else if ('data' in step) {
      const event: BridgeEvent = { type: 'data', ...checkData(step.data) };
      res.diags.push(...dataDiags.splice(0));
      task('bridge', () => tw('bridge', JSON.stringify(event)));
    }
    else probe(step.probe);
    await deliver();
  }
  for (const code of opts.probes ?? []) probe(code);
  res.wasmBytes = wasmMemory.buffer.byteLength;
  dispatch.dispose();
  encoder.dispose();
  vm.dispose();
  rt.dispose();
  return res;

  /**
   * The validated ops go to the renderer core in headless Chromium, which lays them out at `width` and plays the bubble;
   * each press of `opts.gestures` goes in as real mouse input, and the events the renderer forwards come back into the
   * sandbox as trusted input (gesture-run.ts).
   */
  async function pressInRenderer() {
    const { openRenderer, replay, hint } = await import('./gesture-run.ts');
    const width = opts.width ?? 400;
    const desktop = opts.desktop === true;
    const counter = new GestureCounter();
    let current: { claims: GestureClaim[]; bubble: string[]; received: string[] } | null = null;
    const labels = new Map<number, string>();
    let labelled = 0;
    const labelOf = (id: unknown) => {
      for (; labelled < res.renderOps.length; labelled++) {
        const op = res.renderOps[labelled] as any;
        if (op.op !== 'el') continue;
        const attrs = new Map<string, string>(op.attrs ?? []);
        const cls = (attrs.get('class') ?? '').trim();
        labels.set(op.id, op.adopt ?? op.tag + (attrs.get('id') ? '#' + attrs.get('id') : '') + (cls ? '.' + cls.split(/\s+/).slice(0, 2).join('.') : ''));
      }
      return labels.get(Number(id)) ?? '#' + String(id);
    };
    let sent = 0, sentListens = '', sentPd = '';
    const r = await openRenderer(width, (msg) => {
      switch (msg?.type) {
        case 'event': {
          const ev = msg.ev as Record<string, unknown>;
          // any pointerdown begins a press, trusted or not (a second finger's is neither, and has no claim): as WidgetHost
          if (ev.type === 'pointerdown') counter.pressBegan();
          const gesture = msg.trusted === true ? counter.gestureFor(ev) : 0;
          current?.received.push(`${ev.type} ${labelOf(ev.targetId)}`);
          inject(gesture ? { ...ev, gesture } : ev);
          flush();
          break;
        }
        case 'gesture': haptics.press(now()); counter.pressBegan(); if (!preview) current?.claims.push({ x: msg.x === true, y: msg.y === true, hold: msg.hold === true }); break;
        case 'release': haptics.release(now()); break;
        case 'bubble': current?.bubble.push(String(msg.action) + (msg.edge ? ' (edge strip)' : '')); break;
        case 'size': if (Number.isFinite(msg.height)) res.height = msg.height; break;
        case 'paint': {
          const p = (res.paint ??= { heavy: 0, backdrop: 0, peakHeavy: 0, peakBackdrop: 0 });
          p.heavy = Number(msg.heavy) || 0;
          p.backdrop = Number(msg.backdrop) || 0;
          p.peakHeavy = Math.max(p.peakHeavy, p.heavy);
          p.peakBackdrop = Math.max(p.peakBackdrop, p.backdrop);
          break;
        }
        case 'limit': {
          // as WidgetHost: the renderer drew the frame without the effects, the host stops the widget (strict only)
          const limit = checkPaintLimit(msg.code, msg.count);
          if (!limit || config.policy === 'compat' || res.stopped !== undefined) break;
          res.stopped = paintLimitMessage(limit.code, limit.count);
          res.diags.push({ kind: limit.code, detail: res.stopped });
          break;
        }
      }
    });
    const flush = () => {
      if (res.renderOps.length > sent) { r.send({ type: 'ops', ops: res.renderOps.slice(sent) }); sent = res.renderOps.length; }
      const l = JSON.stringify(res.listens), p = JSON.stringify(pdKeys);
      if (l !== sentListens) { sentListens = l; r.send({ type: 'listen', types: res.listens }); }
      if (p !== sentPd) { sentPd = p; r.send({ type: 'preventDefault', keys: pdKeys }); }
    };
    try {
      r.send({ type: 'theme', theme: res.theme });
      r.send({ type: 'safeArea', inset: res.contentSafeArea });
      r.send({ type: 'policy', policy: config.policy, paintReports: true });
      r.send({ type: 'arbitrate', bubble: true, touch: !desktop });
      flush();
      await r.settle();
      const reports: GestureReport[] = [];
      for (const step of opts.gestures ?? []) {
        const { target, touchAction } = await r.describe(step.x, step.y);
        current = { claims: [], bubble: [], received: [] };
        await r.perform(step);
        const claim = current.claims.at(-1) ?? null;
        const report: GestureReport = { step, target, touchAction, claim, outcome: claim ? replay(step, claim, desktop) : null, bubble: current.bubble, received: current.received, hint: null };
        report.hint = hint(report, new Set(res.listens));
        reports.push(report);
        current = null;
        timerRound();
        await deliver();
        flush();
        await r.settle();
      }
      if (opts.gestures) res.gestures = reports;
    } finally {
      await r.close();
    }
  }
}

function defaultAnswer(q: any, viewport: { innerWidth: number; innerHeight: number; dpr: number }): unknown {
  switch (q.q) {
    case 'rect': return { x: 0, y: 0, width: 100, height: 20, top: 0, left: 0, right: 100, bottom: 20 };
    case 'rects': return [{ x: 0, y: 0, width: 100, height: 20, top: 0, left: 0, right: 100, bottom: 20 }];
    case 'box': return { offsetWidth: 100, offsetHeight: 20, offsetTop: 0, offsetLeft: 0, offsetParent: 0, clientWidth: 100, clientHeight: 20, clientTop: 0, clientLeft: 0, scrollWidth: 100, scrollHeight: 20, scrollTop: 0, scrollLeft: 0 };
    case 'cs': return q.prop ? '' : { display: 'block', width: '100px', height: '20px', 'font-size': '16px' };
    case 'svg': return q.method === 'getBBox' ? { x: 0, y: 0, width: 50, height: 12 } : q.method.startsWith('get') && /CTM/.test(q.method) ? { a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 } : 42;
    case 'measureText': return { width: String(q.text).length * 6, actualBoundingBoxAscent: 8, actualBoundingBoxDescent: 2 };
    case 'matchMedia': return { matches: false };
    case 'viewport': return viewport;
    case 'anims': return { list: [] };
    case 'validity': return { valid: true, message: '' };
    case 'elementFromPoint': return 0;
    case 'canvasCall': return q.method === 'toDataURL' ? 'data:,' : false;
    default: return null;
  }
}

/** The authors' guide's block for a model's system prompt (docs/widget-runtime-profile.md, "Agent block"). */
export function agentBlock(): string {
  const guide = fs.readFileSync(path.join(root, '..', '..', 'docs', 'widget-runtime-profile.md'), 'utf8');
  const block = guide.slice(guide.indexOf('<!-- agent-block -->'), guide.indexOf('<!-- /agent-block -->'));
  return block.replace(/^[^]*?```text\n/, '').replace(/\n```[^]*$/, '').replace(/^<!-- \/?profile:agent -->\n/gm, '');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const file = process.argv[2];
  const args = process.argv.slice(3);
  if (file === '--guide') {
    console.log(agentBlock());
    process.exit(0);
  }
  if (!file) {
    console.error(`usage: tsx scripts/sandbox-node.ts --guide     (the rules for a model writing widgets)
       tsx scripts/sandbox-node.ts <widget.html> [--ops] [--ops-file msgs.json] [--dark] [--font-size px] [--verbose] [--json]
       [--width px] [--desktop] [--tap x,y] [--hold x,y[,ms]] [--double-tap x,y] [--swipe x,y,dx,dy] [--right-click x,y]
       [--message private|group|via-bot|no-queries|live-copy|copy|preview] [--copy] [--can-query] [--background-query] [--chat-type t] [--viewer id[,first,last,username,premium]]
       [--chat-data s] [--public-data s] [--answer json | --answer-error TIMEOUT|RATE_LIMITED|FAILED] [--set-data json] [--offscreen]
       [--wallet] [--wallet-answer json | --wallet-error REJECTED|BAD_REQUEST|TIMEOUT|RATE_LIMITED|FAILED]
       [--safe-area bottom[,cornerRight[,cornerLeft]]]
  --width lays the widget out at that bubble width in the real renderer (headless Chromium) and reports its height;
  the presses run there in order, on a phone's bubble (a finger) or with --desktop a desktop client's (a mouse),
  and each is reported: the element, its touch-action claim, who got the press, the events the widget received, a hint.
  The bot (docs/sdk-v1.md §2.13–§2.15): --message sets the server's flags for a kind of message (private: the original
  in the private chat with the bot, taps and background requests; group, via-bot: taps only; no-queries: an original sent
  without allow_queries, no requests; live-copy: a forward of a widget sent with fwd_allow_queries, taps only; copy: no
  requests; preview: an inline result not sent yet, no input, no requests,
  nothing about the user); --copy, --can-query, --background-query, --chat-type set
  them one by one (none given: no requests); --viewer who looks (id, then optionally the first and last name, the username and premium); --chat-data / --public-data the strings at start;
  every request gets --answer (the server's answer: {"result":"…"}, default {"result":null}) or fails with
  --answer-error; --set-data applies the bot's data (the full state: {"chatData":"…","publicData":"…"}) after the presses.
  --offscreen: the widget boots off screen and then appears, as Telegram's clients create it (a background request
  made at boot waits for that and goes out then).
  --wallet: the client has Gram Wallet for this user (docs/sdk-v1.md §2.18; TelegramWidget.wallet where the widget may
  request); every wallet call is confirmed and gets --wallet-answer (the bot's answer to its outcome, default
  {"result":null}) or fails with --wallet-error (REJECTED: the user declined; BAD_REQUEST: the wallet refused it).
  --safe-area: what the client draws over a widget with no caption below it (docs/sdk-v1.md §2.17), in CSS px: the band of the
  message time at the bottom and its corner on the right (a left-to-right interface), or on the left as the third number
  (right-to-left: 24,0,72); env(safe-area-inset-bottom), the --tg-content-safe-area-* variables and
  TelegramWidget.contentSafeAreaInset get them, and the UA layer adds the band to an unstyled body's bottom padding.`);
    process.exit(2);
  }
  const verbose = args.includes('--verbose');
  const json = args.includes('--json');
  const probes = args.flatMap((a, i) => (a === '--eval' ? [args[i + 1]] : []));
  const fontSize = args.indexOf('--font-size');
  const widthAt = args.indexOf('--width');
  const width = widthAt >= 0 ? Number(args[widthAt + 1]) : undefined;
  if (width !== undefined && !(width >= 100 && width <= 2000)) { console.error('--width takes CSS px between 100 and 2000'); process.exit(2); }
  const gestures: GestureStep[] = [];
  for (let i = 0; i < args.length; i++) {
    if (!['--tap', '--hold', '--double-tap', '--swipe', '--right-click'].includes(args[i])) continue;
    const step = parseStep(args[i], args[i + 1]);
    if (!step) { console.error(`${args[i]} ${args[i + 1] ?? ''}: expected x,y${args[i] === '--hold' ? '[,ms]' : args[i] === '--swipe' ? ',dx,dy' : ''} in CSS px`); process.exit(2); }
    gestures.push(step);
  }
  const desktop = args.includes('--desktop');
  const value = (flag: string) => { const i = args.indexOf(flag); return i >= 0 ? args[i + 1] : undefined; };
  const jsonFlag = (flag: string) => {
    const v = value(flag);
    if (v === undefined) return undefined;
    try { return JSON.parse(v); } catch { console.error(`${flag}: expected JSON, got ${v}`); process.exit(2); }
  };
  const answer = jsonFlag('--answer') ?? { result: null };
  const answerError = value('--answer-error');
  const walletAnswer = jsonFlag('--wallet-answer') ?? { result: null };
  const walletError = value('--wallet-error');
  const setData = jsonFlag('--set-data');
  const viewerId = value('--viewer');
  const kind = value('--message') as WidgetMessageKind | undefined;
  const safeArea = value('--safe-area');
  const [bottom, cornerRight, cornerLeft] = (safeArea ?? '').split(',').map((n) => (n.trim() === '' ? 0 : Number(n)));
  if (safeArea !== undefined && ![bottom, cornerRight ?? 0, cornerLeft ?? 0].every((n) => n >= 0 && Number.isFinite(n))) { console.error('--safe-area takes bottom[,cornerRight[,cornerLeft]] in CSS px'); process.exit(2); }
  if (kind !== undefined && !(kind in WIDGET_MESSAGE_KINDS)) { console.error(`--message takes ${Object.keys(WIDGET_MESSAGE_KINDS).join(', ')}`); process.exit(2); }
  const r = await runWidget(fs.readFileSync(file, 'utf8'), {
    data: { chatData: value('--chat-data') ?? null, publicData: value('--public-data') ?? null },
    context: {
      ...(kind ? WIDGET_MESSAGE_KINDS[kind] : {}),
      ...(args.includes('--copy') ? { isCopy: true } : {}), ...(args.includes('--can-query') ? { canQuery: true } : {}),
      ...(args.includes('--background-query') ? { backgroundQuery: true } : {}), ...(value('--chat-type') ? { chatType: value('--chat-type') as any } : {}),
      ...(viewerId !== undefined && viewerFromList(viewerId) ? { viewer: viewerFromList(viewerId)! } : {}),
      ...(args.includes('--wallet') ? { wallet: true } : {}),
    },
    onRequest: () => (answerError ? Promise.reject(answerError) : answer),
    onWallet: () => (walletError ? Promise.reject(walletError) : walletAnswer),
    ...(args.includes('--offscreen') ? { visible: false } : {}),
    ...(safeArea !== undefined ? { contentSafeArea: { bottom, cornerRight: cornerRight ?? 0, cornerLeft: cornerLeft ?? 0 } } : {}),
    script: [...(args.includes('--offscreen') ? [{ visible: true }] : []), ...(setData !== undefined ? [{ data: setData }] : [])],
    verbose, probes, cssScope: args.includes('--shadow') ? 'shadow' : 'document', theme: args.includes('--dark') ? 'dark' : 'light',
    ...(fontSize >= 0 ? { text: { fontSize: Number(args[fontSize + 1]) } } : {}),
    width, desktop, layout: width !== undefined, ...(gestures.length ? { gestures } : {}),
  });
  if (json) {
    const { ops, renderOps, stream, anims, animOps, ...rest } = r;
    console.log(JSON.stringify({ ...rest, sizeCheck: sizeVerdict(r.size) }, null, 1));
    process.exit(0);
  }
  r.probes.forEach((p, i) => console.log(`probe[${i}] ${probes[i]} → ${p}`));
  if (args.includes('--ops')) console.log(JSON.stringify(r.renderOps, null, 0));
  const opsFile = args.indexOf('--ops-file');
  if (opsFile >= 0 && args[opsFile + 1]) fs.writeFileSync(args[opsFile + 1], JSON.stringify([{ type: 'theme', theme: r.theme }, { type: 'safeArea', inset: r.contentSafeArea }, { type: 'ops', ops: r.renderOps }]));
  const tags = r.renderOps.filter((o) => o.op === 'el').length;
  const size = sizeVerdict(r.size);
  console.log(sizeLine(r.size));
  if (size.message) console.log(size.message);
  console.log(`batches ${r.ops.length}, sandbox ops ${r.ops.reduce((n, b) => n + b.length, 0)}, render ops ${r.renderOps.length} (${tags} elements), queries ${r.queries.length}, timers fired ${r.timers}; main thread ${r.mainThreadMs.toFixed(1)} ms (layout queries + 25 µs per op + 2 µs per canvas command; a host allows ${LIMIT_DEFAULTS.mainThreadQuotaMs} ms per second)`);
  console.log(`nodes: ${r.nodes} (max 2000)${r.nodes > 1500 ? ' — over 1500: close to the node limit, past it the widget stops' : ''}`);
  if (r.stopped) console.log(`STOPPED: ${r.stopped} (a host stops the widget here, the bubble keeps its last render)`);
  if (r.height !== undefined) console.log(`height ${Math.ceil(r.height)} px at width ${width ?? 400} px (a bubble shows 50–600 px, the rest is clipped)`);
  if (r.paint) {
    const p = r.paint;
    const most = p.peakHeavy > p.heavy || p.peakBackdrop > p.backdrop ? ` (at most ${p.peakHeavy}, ${p.peakBackdrop} with backdrop-filter)` : '';
    console.log(`paint: ${p.heavy} heavy boxes (max ${PAINT_HEAVY_BOXES}), ${p.backdrop} with backdrop-filter (max ${PAINT_BACKDROP_BOXES})${most}: boxes with a blurred shadow, a filter, a backdrop-filter, opacity below 1, or an animation of one of them`);
    if (!r.stopped && (p.peakHeavy > 12 || p.peakBackdrop >= PAINT_BACKDROP_BOXES)) console.log(`warning: close to the paint limit; past ${PAINT_HEAVY_BOXES} heavy boxes (${PAINT_BACKDROP_BOXES} with backdrop-filter) the widget stops. Mute text and small details with colour (--tg-theme-hint-color, rgba(), fill-opacity / stroke-opacity in SVG) instead of opacity`);
  }
  if (r.publicDataWatches.length) console.log(`public data: ${r.watchesPublicData ? 'watched' : 'not watched'} (watchPublicData: ${r.publicDataWatches.join(' → ')}): a client polls a shared widget's public_data only while it watches`);
  const sa = r.contentSafeArea;
  if (sa.bottom || sa.cornerLeft || sa.cornerRight) console.log(`content safe area: the client's time over the bottom ${sa.bottom} px, its corner ${sa.cornerRight ? `${sa.cornerRight} px on the right` : sa.cornerLeft ? `${sa.cornerLeft} px on the left` : 'none'}`);
  if (r.resizeAnchors.length) console.log(`resize anchor: ${r.resizeAnchor} (setResizeAnchor: ${r.resizeAnchors.join(' → ')}): the ${r.resizeAnchor} edge of the bubble stays put when the height changes`);
  console.log(`listens for: ${r.listens.length ? r.listens.join(', ') : '(no listeners)'}; click, load, error and toggle are always delivered`);
  for (const d of r.diags) console.log('diag ', d.kind, '—', d.detail);
  for (const l of r.logs) console.log(`[${l.level}]`, l.text);
  for (const a of r.actions) console.log('action', JSON.stringify(a));
  const requestOutcome = (q: RunResult['requests'][number]) => q.verdict === 'foreground' ? 'sent (a tap)' : q.verdict === 'background' ? 'sent (background)'
    : q.verdict === 'held' ? (q.background ? 'held off screen, sent (background) when the widget appeared' : 'held off screen, never sent (the widget did not appear)') : 'refused (' + q.verdict + ')';
  for (const q of r.requests) console.log(`request ${JSON.stringify(q.data)}${q.wallet ? ' with the wallet' : ''}: ${requestOutcome(q)} → ${JSON.stringify(q.answer ?? null)}`);
  const walletOutcome = (v: RequestVerdict | 'unsupported') => v === 'foreground' ? 'sent (a tap)' : v === 'granted' ? 'sent (the answer to a call made by a tap)' : 'refused (' + v + ')';
  for (const w of r.walletCalls) console.log(`wallet.${w.call.method} ${JSON.stringify(w.call.data)}: ${walletOutcome(w.verdict)} → ${JSON.stringify(w.answer ?? null)}`);
  for (const t of r.copiedTexts) console.log('copyText', JSON.stringify(t));
  for (const c of r.storageCommits) console.log('storage', JSON.stringify(c).slice(0, 500));
  if (r.gestures) console.log('\n' + formatReports(r.gestures, width ?? 400, desktop));
}
