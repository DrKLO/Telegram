// WidgetHost: one widget instance = one worker (QuickJS) + one validator + one
// renderer (the sandboxed iframe unless a `rendererFactory` supplies another,
// see renderer.ts). Trusted. This is the only place where the three meet:
//
//   worker ──(untrusted ops/queries)──▶ Validator ──(RenderOps)──▶ renderer
//   worker ◀──(events, frames, query results)────────────────────── renderer
//
// Nothing from the worker is forwarded to the renderer without passing through
// the Validator.

import { finiteNumber, isTypedMessage } from './message-guards';
import sandboxBundle from 'virtual:sandbox-bundle';
import { IframeRenderer } from './iframe-renderer';
import { GestureCounter, HostActivationGate } from './user-activation';
import {
  DEFAULT_LOCALE, DEFAULT_RESIZE_ANCHOR, HapticGate, PRIVILEGED_METHODS, RequestGate, allowedInPreview, checkBridgeCall, checkChatType, checkContentSafeArea, checkLocale, checkRequestAnswer,
  checkStorageCommit, checkViewer, checkWidgetData, requestFailure, requestRefusal, STORAGE_QUOTA_BYTES, toWidgetTheme, viewerUser, VIEWER_FIELD_RULES, walletFailure,
  type ContentSafeArea, type RequestError, type RequestVerdict, type ResizeAnchor, type StorageCommit, type WalletCall, type WidgetAction, type WidgetContext, type WidgetData, type WidgetRequest, type WidgetRequestAnswer, type WidgetTheme,
  type WidgetUser, type WidgetViewer,
} from '../shared/sdk-policy';
import { PROFILE_VERSION } from '../shared/profile-tables';
import { checkPaintLimit, paintLimitMessage } from '../shared/paint-policy';
import type { BubbleAction, Gesture, GestureClaim } from '../shared/gesture-policy';
import type { Renderer, RendererFactory } from './renderer';
import type { PooledWorker, WorkerPool } from './worker-pool';
import { Validator } from './validator';
import {
  LIMIT_DEFAULTS, SAB_BIN_LEN, SAB_HEADER, SAB_JSON_LEN, SAB_SIZE, SAB_STATE,
  type BridgeEvent, type HostToRenderer, type HostToWorker, type RendererToHost, type SandboxConfig, type SandboxPolicy, type Stats, type WorkerToHost, resolvePolicy,
} from '../shared/protocol';

export type {
  ChatType, ContentSafeArea, RequestFailure, SignDataPayload, WalletCall, WalletFailure, WalletMessage, WalletTransaction, WidgetAction, WidgetContext, WidgetData, WidgetRequest,
  WidgetRequestAnswer, WidgetTheme, WidgetUser, WidgetViewer,
} from '../shared/sdk-policy';
export type { BubbleAction, Gesture, GestureClaim } from '../shared/gesture-policy';

/** A press the bubble took (`bubbleGestures`): what the bubble does with it, and where the pointer was, in widget coordinates. */
export interface BubbleGesture {
  action: BubbleAction;
  gesture: Gesture;
  x: number;
  y: number;
  /** It started in the leading edge strip, where the widget's claim does not apply. */
  edge: boolean;
}

export interface WidgetHostOptions {
  /** The chat's theme (docs/sdk-v1.md §2.3, §2.4); a plain colour scheme is still accepted. */
  theme: 'light' | 'dark' | WidgetTheme;
  /** The client's UI language, BCP 47 (`navigator.language`, the Intl default); `en-US` when absent or malformed. */
  locale?: string;
  /**
   * What the client draws over the widget, in CSS px (docs/sdk-v1.md §2.17, §5.12): for a widget with no caption below it,
   * `bottom` = the height of the band the message time is drawn in over its bottom corner and `cornerRight` (or
   * `cornerLeft` in a right-to-left interface) = the width of that corner; omitted fields and a widget with a caption below
   * are 0. Pass it here, before the first render, so the first height already has the band; later only with the
   * bubble's layout (`setContentSafeArea`), never per frame. Out of range (insets 0–64, corners 0–200) is 0.
   */
  contentSafeArea?: Partial<ContentSafeArea>;
  /** Web Storage of this message as the client keeps it (docs/sdk-v1.md §5.4); over the quota it is dropped whole (`storage-rejected`). */
  storage?: StorageCommit;
  /** The bot's `chatData` and `publicData` as the message has them now (docs/sdk-v1.md §2.13); later edits go through `setData`. */
  data?: WidgetData;
  /**
   * The message's flags from the server (`isCopy`, `canQuery`, `backgroundQuery`), the chat type and the viewer
   * (docs/sdk-v1.md §4.4); fixed for the host's life. Without `canQuery` every request is refused.
   */
  context?: WidgetContext;
  /**
   * A request the widget sends to the bot (`TelegramWidget.request`, checked: the data, the server's flags, the press
   * or the background rules, one in flight). The client sends `data` like a callback button's press and resolves
   * with the server's answer, `{ result }`, or rejects with `'TIMEOUT'`, `'RATE_LIMITED'` or `'FAILED'`. Without it
   * every request is refused (`NOT_ALLOWED`). `request.wallet`: attach the user's wallet card (`with_wallet`).
   */
  onRequest?: (request: WidgetRequest) => Promise<WidgetRequestAnswer> | WidgetRequestAnswer;
  /**
   * A wallet call (`TelegramWidget.wallet`, docs/sdk-v1.md §2.18; checked: the transaction or the payload, a press or
   * the grant, one call in flight with the requests). The client shows its own wallet window with the bot of the
   * message; on confirmation it sends the transaction (or signs for `bot<bot_id>`), sends the outcome to the bot as a
   * query with `call.data` and resolves with the bot's answer, `{ result }`. It rejects with `'REJECTED'` (the user
   * declined), `'BAD_REQUEST'` (the wallet refused it) or a request's failure. Only with `context.wallet`; without it
   * every call is refused (`NOT_ALLOWED`).
   */
  onWallet?: (call: WalletCall) => Promise<WidgetRequestAnswer> | WidgetRequestAnswer;
  /** Copy this text to the clipboard and show a toast that says what was copied (`TelegramWidget.copyText`, checked, one per press). */
  onCopyText?: (text: string) => void;
  memoryLimitBytes?: number;
  cpuLimitMs?: number;
  cpuQuotaMs?: number;
  cpuQuotaWindowMs?: number;
  cpuKillAfterMs?: number;
  /** The host's main-thread work for this widget per window (layout queries + 25 µs per op + 2 µs per canvas command); default 100 ms. */
  mainThreadQuotaMs?: number;
  maxTimers?: number;
  policy?: SandboxPolicy;
  /** Initial visibility; change it later with `setVisible`. */
  visible?: boolean;
  onLog?: (level: string, text: string) => void;
  onDiag?: (kind: string, detail: string) => void;
  onStats?: (stats: Stats & { nodes: number }) => void;
  /** What the widget asked the client to do, checked (URL rules, user activation, the haptic rate limit). */
  onAction?: (action: WidgetAction) => void;
  /** The new full content of a Web Storage area, at the end of each task that changed it. */
  onStorageCommit?: (area: 'local' | 'session', values: Record<string, string>) => void;
  /**
   * On every primary pointerdown: what of this press the widget claims with `touch-action` (gesture-policy.ts):
   * horizontal drags (`x`) and holds (`hold`, no message menu). Taps and double taps are always its own, vertical
   * drags always the chat's (`y` is always false since v0.1.3).
   */
  onGestureClaim?: (claim: GestureClaim) => void;
  /**
   * The renderer plays the bubble (docs/sdk-v1.md §5.5): presses the widget does not claim are the bubble's, and the
   * widget gets `pointercancel` when the bubble takes one (`onBubbleGesture`). For a client with no recognizers of its
   * own over the widget (the web client, the dev app). `touch`: a mouse's primary button arbitrates as a finger, so a
   * desktop browser reproduces a phone; `rtl`: the leading edge strip is on the right.
   */
  bubbleGestures?: boolean | { touch?: boolean; rtl?: boolean };
  /** With `bubbleGestures`: the bubble took a press (the message menu, swipe-to-reply or back, the list scroll). */
  onBubbleGesture?: (gesture: BubbleGesture) => void;
  /** @deprecated since v1: `onAction`. The raw method and arguments of each call `onAction` gets. */
  onBridge?: (method: string, args: unknown[]) => void;
  /** Fatal: the runtime is gone. The renderer keeps the last state (frozen); call `destroy()` to remove it. */
  onDead?: (reason: string) => void;
  /** Non-fatal uncaught exception in widget code (also in the log); for showing an error line under the bubble. */
  onError?: (text: string) => void;
  /** Badge text shown by the renderer over a dead widget (user-facing, no error details). */
  stoppedLabel?: string;
  onLoaded?: () => void;
  /**
   * The bubble's height (the content's, clamped to [50, 600]) on every size report, and the edge of the bubble that
   * stays put on screen when it changes (`TelegramWidget.setResizeAnchor`, docs/sdk-v1.md §2.12 and §5.6): `top` (the
   * default: the content below moves) or `bottom` (the content above moves).
   */
  onResize?: (height: number, anchor: ResizeAnchor) => void;
  /**
   * The widget started (`true`) or stopped (`false`) watching its shared `public_data`
   * (`TelegramWidget.watchPublicData`, docs/sdk-v1.md §2.13): poll `getWidgetPublicData` for it only while it watches
   * and is on screen, and only when the message has a `widget_id`. Called on a change only; `watchesPublicData` has
   * the latest.
   */
  onWatchPublicData?: (on: boolean) => void;
  /** The renderer; the sandboxed iframe when omitted. */
  rendererFactory?: RendererFactory;
  /** Take the sandbox worker from a pool of warmed ones (worker-pool.ts) instead of booting a fresh one. */
  workerPool?: WorkerPool;
  /**
   * Opt-in sandbox features (docs/protocol.md, "Features"). The web runtime honours
   * `webAnimations`, `noInPlaceText`, `noAttrFast` and `noCanvasRect` (`WEB_FEATURES` in
   * worker.ts); the native-only wire forms (`canvasWords`, `binaryOps`, `morphUpdates`) are dropped.
   */
  features?: string[];
}

/** The shared bubble height bounds of v0.1.0 (docs/integration.md, "Bubble sizing"); the same on every client. */
const MIN_HEIGHT = 50;
const MAX_HEIGHT = 600;
const encoder = new TextEncoder();
const BUBBLE_ACTIONS = new Set<unknown>(['menu', 'swipe', 'scroll', 'back']);
const REQUEST_REFUSED: Record<string, string> = {
  disabled: 'the server allows no requests for this message (no can_query: a widget sent without allow_queries, or a forwarded copy of one sent without fwd_allow_queries)',
  busy: 'a request is already in flight',
  'no-activation': 'it needs a tap on the visible widget, one request per tap; this message allows no background requests (no background_query)',
  'rate-limited': 'background requests at most once per 5 s',
  'no-wallet': 'the client has no wallet for this user (no WidgetContext.wallet)',
};
/** Where a wallet call's refusal differs from a request's. */
const WALLET_REFUSED: Record<string, string> = {
  'no-activation': 'it needs a tap on the visible widget, or the answer to a call made by a tap within 10 s; one call each',
  busy: 'a request or a wallet call is already in flight',
};
const GESTURES = new Set<unknown>(['press', 'tap', 'double-tap', 'hold', 'drag-x', 'drag-y', 'menu-click']);

export class WidgetHost {
  /** The renderer's element in the bubble (an iframe for the product renderer, a canvas for Blitz). */
  readonly element: HTMLElement;
  /** The product renderer's frame; null for other renderers (Playwright suites look inside it). */
  get iframe(): HTMLIFrameElement | null { return this.element instanceof HTMLIFrameElement ? this.element : null; }
  /** The edge of the bubble that stays put when the widget's height changes: the widget's latest `setResizeAnchor`, `top` until then. */
  get resizeAnchor(): ResizeAnchor { return this.anchor; }
  /** Whether the widget watches its shared `public_data` (its latest `watchPublicData` / `unwatchPublicData`), false until then. */
  get watchesPublicData(): boolean { return this.watching; }
  /** What the client draws over the widget as the widget sees it (checked: `contentSafeArea`, `setContentSafeArea`). */
  get contentSafeArea(): ContentSafeArea { return { ...this.safeArea }; }
  private worker: Worker | null = null;
  private renderer: Renderer;
  private validator: Validator;
  private sab: SharedArrayBuffer;
  private header: Int32Array;
  private payload: Uint8Array;
  private qid = 0;
  private pendingQuery = 0;
  private destroyed = false;
  private dead = false;
  private viewport: SandboxConfig['viewport'] | undefined;
  private theme: 'light' | 'dark';
  private widgetTheme: ReturnType<typeof toWidgetTheme>;
  private safeArea: ContentSafeArea;
  private visible: boolean;
  private policy: SandboxPolicy;
  private gestures = new GestureCounter();
  private activation = new HostActivationGate();
  private blockedBridgeReported = 0;
  private rejectedBridgeReported = 0;
  private rejectedStorageReported = 0;
  private haptics = new HapticGate();
  private anchor: ResizeAnchor = DEFAULT_RESIZE_ANCHOR;
  private watching = false;
  private context: { isCopy: boolean; canQuery: boolean; backgroundQuery: boolean; wallet: boolean; preview: boolean };
  private previewBlockedReported = 0;
  private requests = new RequestGate();
  /** A background request the widget made off screen: it goes to the client when the widget appears (`setVisible`). */
  private heldRequest: { id: number; data: string } | null = null;
  private blockedRequestsReported = 0;

  constructor(container: HTMLElement, html: string, private opts: WidgetHostOptions) {
    const diag = (kind: string, detail: string) => opts.onDiag?.(kind, detail);
    this.widgetTheme = toWidgetTheme(opts.theme);
    this.theme = this.widgetTheme.colorScheme;
    this.safeArea = checkContentSafeArea(opts.contentSafeArea);
    this.visible = opts.visible ?? true;
    // A preview (an inline result not sent yet) asks nothing whatever the flags say (docs/sdk-v1.md §2.16).
    const preview = opts.context?.preview === true;
    const canQuery = opts.context?.canQuery === true && !preview;
    this.context = {
      isCopy: opts.context?.isCopy === true && !preview, canQuery, backgroundQuery: opts.context?.backgroundQuery === true && !preview,
      wallet: opts.context?.wallet === true && canQuery, preview,
    };
    // ── renderer ──
    this.renderer = (opts.rendererFactory ?? IframeRenderer.factory)(container, this.theme);
    this.element = this.renderer.element;
    this.validator = new Validator(diag, { colorScheme: this.theme, scope: this.renderer.cssScope ?? 'document' });
    this.policy = resolvePolicy(opts.policy);
    this.validator.setPolicy(this.policy);
    this.renderer.onMessage = (msg) => { if (isTypedMessage(msg)) this.onRenderer(msg as RendererToHost); };
    this.renderer.onDiag = diag;
    this.renderer.load();
    this.toRenderer({ type: 'theme', theme: this.widgetTheme });
    this.toRenderer({ type: 'safeArea', inset: this.safeArea });
    // Under strict the renderer counts heavy boxes and reports `limit` past them (paint-policy.ts); compat counts nothing.
    this.toRenderer({ type: 'policy', policy: this.policy });
    if (opts.bubbleGestures && !preview) this.setBubbleGestures(opts.bubbleGestures);

    // ── sandbox ──
    // The worker boots in parallel with the renderer: a renderer that is not ready yet queues what it gets.
    // A synchronous renderer (shadow DOM) has already reported its size by now; the iframe has not.
    this.viewport ??= { innerWidth: this.element.clientWidth || 400, innerHeight: this.element.clientHeight || 300, dpr: window.devicePixelRatio };
    const config: SandboxConfig = {
      memoryLimitBytes: opts.memoryLimitBytes ?? LIMIT_DEFAULTS.memoryLimitBytes,
      cpuLimitMs: opts.cpuLimitMs ?? LIMIT_DEFAULTS.cpuLimitMs,
      cpuQuotaMs: opts.cpuQuotaMs ?? LIMIT_DEFAULTS.cpuQuotaMs,
      cpuQuotaWindowMs: opts.cpuQuotaWindowMs ?? LIMIT_DEFAULTS.cpuQuotaWindowMs,
      cpuKillAfterMs: opts.cpuKillAfterMs ?? LIMIT_DEFAULTS.cpuKillAfterMs,
      mainThreadQuotaMs: opts.mainThreadQuotaMs ?? LIMIT_DEFAULTS.mainThreadQuotaMs,
      maxTimers: opts.maxTimers ?? LIMIT_DEFAULTS.maxTimers,
      visible: opts.visible ?? true,
      policy: this.policy,
      features: opts.features ?? [],
      viewport: this.viewport,
      theme: this.theme,
      themeParams: this.widgetTheme.params,
      locale: checkLocale(opts.locale) ?? DEFAULT_LOCALE,
      contentSafeArea: this.safeArea,
      profile: PROFILE_VERSION,
      // A preview has no message yet: no storage of its own (it lives in memory for this run and is never committed).
      storage: preview ? {} : this.preload(opts.storage),
      ...this.checkData(opts.data),
      isCopy: this.context.isCopy,
      canQuery: this.context.canQuery,
      isPreview: preview,
      // A copy is told nothing about the chat it is in (the bot must not learn where its widget was forwarded). The
      // widget knows its viewer only where it may send requests (canQuery, never in a preview): a widget that may not
      // talk to the bot cannot pass the id out through a link either, and the rule matches the network marker.
      chatType: preview || this.context.isCopy ? null : checkChatType(opts.context?.chatType),
      viewer: this.context.canQuery ? this.checkViewer(opts.context?.viewer) : null,
      wallet: this.context.wallet,
    };
    let pooled: PooledWorker | null = null;
    if (opts.workerPool) {
      // A warmed worker: QuickJS and the bundle are already there (or on their way), only the widget is left to load.
      pooled = opts.workerPool.take();
      this.worker = pooled.worker;
      this.sab = pooled.sab;
    } else {
      this.worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
      this.sab = new SharedArrayBuffer(SAB_SIZE);
    }
    this.header = new Int32Array(this.sab, 0, SAB_HEADER / 4);
    this.payload = new Uint8Array(this.sab, SAB_HEADER);
    this.worker.onmessage = (e) => { if (isTypedMessage(e.data)) this.onWorker(e.data as WorkerToHost); };
    this.worker.onerror = (e) => this.die('worker error: ' + e.message);
    // What the pooled worker said while idle (the shim's document skeleton) comes first, in order.
    if (pooled) for (const m of pooled.pending.splice(0)) this.onWorker(m);
    if (opts.workerPool) this.toWorker({ type: 'start', html, config });
    else this.toWorker({ type: 'init', html, sab: this.sab, config, sandboxBundle });
  }

  setTheme(theme: 'light' | 'dark' | WidgetTheme) {
    // The host owns the theme: stylesheets are re-sanitized against the scheme
    // (prefers-color-scheme, color-scheme, light-dark()), matchMedia queries
    // are answered from it, the renderer gets the variables, and the sandbox
    // gets the scheme and the palette.
    this.widgetTheme = toWidgetTheme(theme);
    this.theme = this.widgetTheme.colorScheme;
    this.renderer.setTheme(this.theme);
    this.toRenderer({ type: 'theme', theme: this.widgetTheme });
    const ops = this.validator.setTheme(this.theme);
    if (ops.length) this.toRenderer({ type: 'ops', ops });
    this.toWorker({ type: 'theme', theme: this.theme, themeParams: this.widgetTheme.params });
  }

  /**
   * What the client draws over the widget changed with the bubble's layout (docs/sdk-v1.md §2.17, §5.12): a caption
   * added or removed by an edit, an "edited" mark that widens the time. The renderer gets the root variables and the
   * widget `contentSafeAreaInset` and `contentSafeAreaChanged`, from the same call; never per frame.
   */
  setContentSafeArea(area: Partial<ContentSafeArea>) {
    this.safeArea = checkContentSafeArea(area);
    this.toRenderer({ type: 'safeArea', inset: this.safeArea });
    this.toWorker({ type: 'safeArea', inset: this.safeArea });
  }

  /** Turns the renderer's bubble on or off (`WidgetHostOptions.bubbleGestures`); a preview takes no input at all. */
  setBubbleGestures(mode: boolean | { touch?: boolean; rtl?: boolean }) {
    if (this.context.preview) return;
    const o = typeof mode === 'object' ? mode : {};
    this.toRenderer({ type: 'arbitrate', bubble: mode !== false, touch: o.touch === true, rtl: o.rtl === true });
  }

  /**
   * The bot's data changed (`updateMessageWidgetData`, or a new `public_data` from polling the shared object): the
   * full state of both strings. The widget gets `chatDataChanged` / `publicDataChanged` for each that differs; a copy
   * keeps no `chatData`, only its `publicData` changes.
   */
  setData(data: WidgetData) {
    this.toWorker({ type: 'bridge', event: { type: 'data', ...this.checkData(data) } });
  }

  /** The viewer as the widget sees it; a field that breaks its rule is left out and reported (`data-rejected`). */
  private checkViewer(viewer: WidgetViewer | undefined): WidgetUser | null {
    const v = checkViewer(viewerUser(viewer));
    for (const field of v.rejected) this.opts.onDiag?.('data-rejected', `viewer.${field}: ${VIEWER_FIELD_RULES[field]}; the widget does not see it`);
    return v.viewer;
  }

  private checkData(data: unknown): { chatData: string | null; publicData: string | null } {
    const d = checkWidgetData(data, this.context.isCopy);
    if (d.rejected.length) this.opts.onDiag?.('data-rejected', `${d.rejected.join(' and ')}: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null`);
    return { chatData: d.chatData, publicData: d.publicData };
  }

  /**
   * docs/sdk-v1.md §2.14: a press unlocks one request, background ones only where the context allows them; one in
   * flight. The gate decides first; one made off screen waits here for the widget to appear (`held`); a client
   * without `onRequest` refuses at delivery, as the native hosts do.
   */
  private request(id: number, data: string, wallet: boolean, gesture: unknown) {
    const now = performance.now();
    const pressed = this.policy === 'compat' || this.activation.available(gesture, now, this.visible);
    const c = this.context;
    const verdict = this.requests.decide(now, { gesture: pressed, visible: this.visible, canQuery: c.canQuery, backgroundQuery: c.backgroundQuery, withWallet: wallet, wallet: c.wallet });
    if (requestRefusal(verdict)) {
      const why = wallet && verdict === 'no-activation' ? 'a request for the wallet needs a tap on the visible widget, one request per tap' : REQUEST_REFUSED[verdict] ?? verdict;
      return this.refuseRequest(id, verdict, `request refused (${verdict}): ${why}`);
    }
    if (verdict === 'foreground' && this.policy === 'strict') this.activation.accept(gesture, now, this.visible);
    if (verdict === 'held') this.heldRequest = { id, data };
    else this.deliverRequest(id, data, wallet);
  }

  /**
   * docs/sdk-v1.md §2.18: a wallet call goes out on a press or on the grant (the answer to a call made by a press,
   * within 10 s), never in the background; one call in flight with the requests.
   */
  private walletCall(id: number, call: WalletCall, gesture: unknown) {
    const now = performance.now();
    const pressed = this.policy === 'compat' || this.activation.available(gesture, now, this.visible);
    const verdict = this.requests.decideWallet(now, { gesture: pressed, visible: this.visible, canQuery: this.context.canQuery, wallet: this.context.wallet });
    if (requestRefusal(verdict)) return this.refuseRequest(id, verdict, `wallet.${call.method} refused (${verdict}): ${WALLET_REFUSED[verdict] ?? REQUEST_REFUSED[verdict] ?? verdict}`);
    if (verdict === 'foreground' && this.policy === 'strict') this.activation.accept(gesture, now, this.visible);
    const onWallet = this.opts.onWallet;
    if (!onWallet) {
      this.requests.finish();
      return this.refuseRequest(id, 'unsupported', `wallet.${call.method} refused: the client handles no wallet calls (WidgetHostOptions.onWallet)`);
    }
    this.settle(id, new Promise<WidgetRequestAnswer>((resolve) => resolve(onWallet(call))), walletFailure);
  }

  private answerRequest(id: number, event: Omit<Extract<BridgeEvent, { type: 'answer' }>, 'type' | 'id'>) {
    this.toWorker({ type: 'bridge', event: { type: 'answer', id, ...event } });
  }

  private refuseRequest(id: number, reason: RequestVerdict | 'unsupported', detail: string) {
    if (this.blockedRequestsReported < 20) {
      this.blockedRequestsReported++;
      this.opts.onDiag?.('request-blocked', detail);
    }
    this.answerRequest(id, { error: (reason === 'unsupported' ? null : requestRefusal(reason)) ?? 'NOT_ALLOWED', reason });
  }

  /** A request the gate let through, to the client; its answer or failure frees the gate. */
  private deliverRequest(id: number, data: string, wallet = false) {
    const onRequest = this.opts.onRequest;
    if (!onRequest) {
      this.requests.finish();
      return this.refuseRequest(id, 'unsupported', 'request refused: the client handles no requests (WidgetHostOptions.onRequest)');
    }
    this.settle(id, new Promise<WidgetRequestAnswer>((resolve) => resolve(onRequest(wallet ? { data, wallet: true } : { data }))), requestFailure);
  }

  /** The client's outcome of a request or a wallet call into the widget; it frees the gate, and an answer brings the grant when the call had a press or the grant. */
  private settle(id: number, outcome: Promise<WidgetRequestAnswer>, failure: (reason: unknown) => RequestError) {
    outcome.then((a) => {
      const checked = checkRequestAnswer(a);
      if (!checked) {
        this.requests.finish();
        this.opts.onDiag?.('answer-rejected', 'the answer\'s result is not text of at most 1024 bytes (UTF-8, no NUL); the widget gets INVALID_ANSWER');
        return this.answerRequest(id, { error: 'INVALID_ANSWER' });
      }
      this.requests.finish(performance.now());
      this.answerRequest(id, { result: checked.result });
    }, (reason) => {
      this.requests.finish();
      this.answerRequest(id, { error: failure(reason) });
    });
  }

  /** The widget stopped (dead or destroyed): a held request never reaches the client. */
  private dropHeldRequest() {
    if (!this.heldRequest) return;
    this.heldRequest = null;
    this.requests.finish();
  }

  private preload(storage: unknown): StorageCommit {
    if (storage === undefined) return {};
    const checked = checkStorageCommit(storage);
    if (checked === null) this.opts.onDiag?.('storage-rejected', `the preload is not { local, session } objects of strings within ${STORAGE_QUOTA_BYTES} bytes each; the widget starts with empty storage`);
    return checked ?? {};
  }

  /**
   * The host knows whether the bubble is on screen; the sandbox does not.
   * Hidden: no animation frames, timers at 1 Hz, `document.hidden` /
   * `visibilitychange` inside the widget. Visible again: deferred timers fire,
   * one frame is requested if the widget asked for one meanwhile.
   */
  setVisible(visible: boolean) {
    if (this.visible === visible) return;
    this.visible = visible;
    // On screen again: the widget's next background request may go out at once, the one it made off screen now
    // (docs/sdk-v1.md §2.14), before the widget hears it is visible.
    if (visible && this.requests.show(performance.now()) && this.heldRequest) {
      const { id, data } = this.heldRequest;
      this.heldRequest = null;
      this.deliverRequest(id, data);
    }
    this.toWorker({ type: 'visibility', visible });
  }

  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    this.dropHeldRequest();
    this.worker?.terminate();
    this.renderer.destroy();
  }

  private toWorker(msg: HostToWorker) {
    // Before the worker exists (a renderer reporting its size synchronously) nothing is lost: the config carries the viewport.
    if (!this.destroyed) this.worker?.postMessage(msg);
  }
  private toRenderer(msg: HostToRenderer) {
    if (!this.destroyed) this.renderer.send(msg);
  }

  private die(reason: string) {
    if (this.dead) return;
    this.dead = true;
    this.opts.onDead?.(reason);
    // The renderer keeps showing the last state (a static snapshot); the VM is
    // gone. `frozen` tells the renderer to stop forwarding events and frames
    // and to show the badge; the owner removes the widget with destroy().
    this.worker?.terminate();
    this.dropHeldRequest();
    this.answerQuery({ error: 'dead' });
    this.toRenderer({ type: 'frozen', reason: this.opts.stoppedLabel ?? 'Widget stopped', fatal: true });
  }

  // ───────────── worker → host ─────────────

  private onWorker(msg: WorkerToHost) {
    switch (msg.type) {
      case 'ops': {
        const ops = this.validator.processOps(msg.ops);
        // A counted cap passed under strict: the widget stops like it does for memory or CPU (the renderer keeps its last render).
        if (this.validator.stopped) return this.die(this.validator.stopped);
        if (ops.length) this.toRenderer({ type: 'ops', ops });
        break;
      }
      case 'query': {
        const query = this.validator.validateQuery(msg.query);
        if (!query) {
          this.answerQuery({ error: 'invalid query' });
          break;
        }
        if (query.q === 'matchMedia') query.query = this.validator.rewriteMediaQuery(query.query);
        this.pendingQuery = ++this.qid;
        this.toRenderer({ type: 'query', qid: this.pendingQuery, query });
        break;
      }
      case 'listen':
        this.toRenderer({ type: 'listen', types: this.validator.validateListen(msg.types) });
        break;
      case 'pd':
        this.toRenderer({ type: 'preventDefault', keys: this.validator.validatePreventDefault(msg.keys) });
        break;
      case 'anim': {
        const anims = this.validator.validateAnim(msg.anims);
        const now = finiteNumber(msg.now);
        if (anims.length && now !== undefined) this.toRenderer({ type: 'anim', now, anims });
        break;
      }
      case 'raf':
        this.toRenderer({ type: 'requestFrame' });
        break;
      case 'log': {
        const text = String(msg.text);
        this.opts.onLog?.(String(msg.level), text);
        if (msg.level === 'error' && text.startsWith('Uncaught ')) this.opts.onError?.(text);
        break;
      }
      case 'diag':
        this.opts.onDiag?.(String(msg.kind), String(msg.detail));
        break;
      case 'bridge': {
        // docs/sdk-v1.md §3.2: the method and its arguments, then user activation for links, then the haptic limit.
        const method = String(msg.method).slice(0, 64);
        const action = checkBridgeCall(method, msg.args);
        if (!action) {
          if (this.rejectedBridgeReported < 20) {
            this.rejectedBridgeReported++;
            this.opts.onDiag?.('bridge-rejected', `${method}: unknown method or malformed arguments`);
          }
          break;
        }
        // A preview may only say it is ready and set its anchor: no request, link, copy or haptic (docs/sdk-v1.md §2.16).
        if (this.context.preview && !allowedInPreview(method)) {
          if (this.previewBlockedReported < 20) {
            this.previewBlockedReported++;
            this.opts.onDiag?.('preview-blocked', `${method} ignored: a preview (an inline result not sent yet) takes no actions`);
          }
          if (action.kind === 'request' || action.kind === 'wallet') this.toWorker({ type: 'bridge', event: { type: 'answer', id: action.id, error: 'NOT_ALLOWED', reason: 'preview' } });
          break;
        }
        // Not an action: the host keeps the latest anchor and reports it with the height (no gesture, no callback of its own).
        if (action.kind === 'setResizeAnchor') {
          this.anchor = action.edge;
          break;
        }
        // Not an action either: the host keeps whether the widget watches its public_data and tells the client on a change.
        if (action.kind === 'watchPublicData') {
          if (action.on !== this.watching) {
            this.watching = action.on;
            this.opts.onWatchPublicData?.(action.on);
          }
          break;
        }
        // A request has its own gate (a press or the background rules) and goes to `onRequest`, not `onAction`.
        if (action.kind === 'request') {
          this.request(action.id, action.data, action.wallet === true, msg.gesture);
          break;
        }
        // A wallet call shares the request gate (a press or the grant, never the background) and goes to `onWallet`.
        if (action.kind === 'wallet') {
          this.walletCall(action.id, action.call, msg.gesture);
          break;
        }
        if (this.policy === 'strict' && PRIVILEGED_METHODS.has(method) && !this.activation.accept(msg.gesture, performance.now(), this.visible)) {
          if (this.blockedBridgeReported < 20) {
            this.blockedBridgeReported++;
            this.opts.onDiag?.('activation-blocked', `${method} ignored: it needs a tap on the visible widget and runs in that tap's handler, one call per tap`);
          }
          break;
        }
        if (action.kind === 'haptic') {
          const d = this.haptics.decide(performance.now(), this.visible);
          if (d === 'report') this.opts.onDiag?.('haptic-limit', this.visible ? 'haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped' : 'haptics of a hidden widget are dropped');
          if (d !== 'play') break;
        }
        if (action.kind === 'copyText') {
          this.opts.onCopyText?.(action.text);
          break;
        }
        this.opts.onAction?.(action);
        this.opts.onBridge?.(method, msg.args as unknown[]);
        break;
      }
      case 'storage': {
        // A preview's Web Storage lives in this run only: there is no message to keep it for.
        if (this.context.preview) break;
        const commit = checkStorageCommit(msg.commit);
        if (!commit || (!commit.local && !commit.session)) {
          if (this.rejectedStorageReported < 20) {
            this.rejectedStorageReported++;
            this.opts.onDiag?.('storage-rejected', `a storage commit that is not { local?, session? } objects of strings within ${STORAGE_QUOTA_BYTES} bytes each; the client keeps the previous content`);
          }
          break;
        }
        if (commit.local) this.opts.onStorageCommit?.('local', commit.local);
        if (commit.session) this.opts.onStorageCommit?.('session', commit.session);
        break;
      }
      case 'warmed':
        break;
      case 'loaded':
        // After the loading tasks' ops (the worker posts them first): text changes are rate-limited from here on.
        this.validator.markLoaded();
        this.opts.onLoaded?.();
        break;
      case 'stats':
        if (typeof msg.stats !== 'object' || msg.stats === null) break;
        this.opts.onStats?.({ ...msg.stats, nodes: this.validator.nodeCount });
        break;
      case 'dead':
        this.die(msg.reason);
        break;
    }
  }

  /** Write a query answer into the shared buffer and wake the blocked worker. */
  private answerQuery(result: unknown, binary?: ArrayBuffer) {
    if (Atomics.load(this.header, SAB_STATE) !== 1) return;
    let json = encoder.encode(JSON.stringify(result ?? null));
    let bin = binary ? new Uint8Array(binary) : null;
    if (json.length + (bin?.length ?? 0) > this.payload.length) {
      json = encoder.encode('{"error":"result too large"}');
      bin = null;
    }
    this.payload.set(json, 0);
    if (bin) this.payload.set(bin, json.length);
    Atomics.store(this.header, SAB_JSON_LEN, json.length);
    Atomics.store(this.header, SAB_BIN_LEN, bin ? bin.length : 0);
    Atomics.store(this.header, SAB_STATE, 2);
    Atomics.notify(this.header, SAB_STATE);
    this.pendingQuery = 0;
  }

  // ───────────── renderer → host ─────────────

  private onRenderer(msg: RendererToHost) {
    switch (msg.type) {
      case 'event': {
        // A preview takes no input: a tap on it selects the result (the client's), and the widget gets nothing.
        if (this.context.preview) break;
        // Any pointerdown begins a press, trusted or not: a second finger's is neither trusted nor claimed (`gesture` is the
        // primary pointer's), and without this its release continued the press before it.
        if (msg.ev?.type === 'pointerdown') this.gestures.pressBegan();
        const gesture = msg.trusted === true ? this.gestures.gestureFor(msg.ev) : 0;
        if (gesture) this.activation.issue(gesture, performance.now());
        this.toWorker(gesture ? { type: 'event', ev: msg.ev, gesture } : { type: 'event', ev: msg.ev });
        break;
      }
      case 'result':
        if (msg.qid === this.pendingQuery) this.answerQuery(msg.result, msg.binary);
        break;
      case 'size': {
        const h = finiteNumber(msg.height), iw = finiteNumber(msg.innerWidth), ih = finiteNumber(msg.innerHeight), dpr = finiteNumber(msg.dpr);
        if (h === undefined || iw === undefined || ih === undefined || dpr === undefined) break;
        const height = Math.min(Math.max(h, MIN_HEIGHT), MAX_HEIGHT);
        this.element.style.height = height + 'px';
        this.opts.onResize?.(height, this.anchor);
        const vp = { innerWidth: iw, innerHeight: ih, dpr };
        if (JSON.stringify(vp) !== JSON.stringify(this.viewport)) {
          this.viewport = vp;
          this.toWorker({ type: 'viewport', viewport: vp });
        }
        break;
      }
      case 'resize':
        this.toWorker({ type: 'resize', entries: msg.entries });
        break;
      case 'frame': {
        const time = finiteNumber(msg.time);
        if (time !== undefined) this.toWorker({ type: 'frame', time });
        break;
      }
      case 'gesture':
        // A primary press began (renderer-made: the widget cannot fake it): haptics are free while it lasts, and 5 s after.
        this.haptics.press(performance.now());
        this.gestures.pressBegan();
        if (this.context.preview) break;
        // Vertical drags are the chat's (v0.1.3): `y` is false whatever a renderer sends.
        this.opts.onGestureClaim?.({ x: msg.x === true, y: false, hold: msg.hold === true });
        break;
      case 'release':
        this.haptics.release(performance.now());
        break;
      case 'bubble': {
        if (this.context.preview) break;
        const x = finiteNumber(msg.x), y = finiteNumber(msg.y);
        if (!BUBBLE_ACTIONS.has(msg.action) || !GESTURES.has(msg.gesture) || x === undefined || y === undefined) break;
        this.opts.onBubbleGesture?.({ action: msg.action, gesture: msg.gesture, x, y, edge: msg.edge === true });
        break;
      }
      case 'media':
        // OS-level change inside the renderer: the host theme is authoritative, just re-notify.
        this.toWorker({ type: 'theme', theme: this.theme, themeParams: this.widgetTheme.params });
        break;
      case 'limit': {
        // Past a paint limit (16 heavy boxes, 2 with backdrop-filter; docs/decisions.md "Heavy boxes"): the renderer drew
        // the frame without the effects, the widget stops like it does for memory or CPU. compat counts nothing.
        const limit = checkPaintLimit(msg.code, msg.count);
        if (!limit) {
          this.opts.onDiag?.('renderer-violation', 'a limit report with an unknown code or no count');
          break;
        }
        if (this.policy === 'compat' || this.dead) break;
        const reason = paintLimitMessage(limit.code, limit.count);
        this.opts.onDiag?.(limit.code, reason);
        this.die(reason);
        break;
      }
      case 'violation':
        this.opts.onDiag?.('renderer-violation', String(msg.detail).slice(0, 4000));
        break;
    }
  }
}
