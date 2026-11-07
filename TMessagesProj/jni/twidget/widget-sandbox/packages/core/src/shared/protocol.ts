// Message schema shared by sandbox shim, host and renderer. See docs/protocol.md.
// Types only (plus a few constants): no code here runs with any privilege.

import { PROFILE_EVENTS } from './profile-tables';
import type { ChatType, ContentSafeArea, RequestError, StorageCommit, ThemeParams, WidgetTheme, WidgetUser } from './sdk-policy';
import type { BubbleAction, Gesture } from './gesture-policy';
import type { PaintLimitCode } from './paint-policy';

export type NodeId = number;
/** The widget's Document node. Never rendered; parent of the <html> element. */
export const DOCUMENT_ID = 1;

// ───────────── sandbox → host: mutation ops (UNTRUSTED, validated by host) ─────────────

export type SandboxOp =
  | { op: 'el'; id: NodeId; tag: string; ns?: 'svg'; attrs?: Record<string, string> }
  | { op: 'text'; id: NodeId; data: string }
  | { op: 'comment'; id: NodeId; data: string }
  | { op: 'attr'; id: NodeId; name: string; value: string }
  | { op: 'rattr'; id: NodeId; name: string }
  | { op: 'data'; id: NodeId; data: string }
  | { op: 'ins'; parent: NodeId; id: NodeId; before: NodeId | null }
  | { op: 'rm'; id: NodeId }
  | { op: 'style'; id: NodeId; name: string; value: string; important?: boolean }
  | { op: 'prop'; id: NodeId; name: string; value: string | number | boolean }
  | { op: 'call'; id: NodeId; method: string; args?: unknown[] }
  | { op: 'canvas'; id: NodeId; cmds: CanvasCmd[] };

/** [kind, name, ...args]. kind: 'c' call, 's' set property, 'r' resize, 'g' gradient/pattern op. */
export type CanvasCmd = unknown[];

// ───────────── host → renderer: render ops (TRUSTED, already sanitized) ─────────────

export type RenderOp =
  | { op: 'el'; id: NodeId; tag: string; ns?: 'svg'; attrs: [string, string][]; adopt?: 'html' | 'head' | 'body' }
  | { op: 'text'; id: NodeId; data: string }
  | { op: 'comment'; id: NodeId; data: string }
  | { op: 'attr'; id: NodeId; name: string; value: string }
  | { op: 'rattr'; id: NodeId; name: string }
  | { op: 'data'; id: NodeId; data: string }
  | { op: 'ins'; parent: NodeId; id: NodeId; before: NodeId | null }
  | { op: 'rm'; id: NodeId }
  | { op: 'style'; id: NodeId; name: string; value: string; important: boolean }
  | { op: 'css'; id: NodeId; text: string }
  | { op: 'prop'; id: NodeId; name: string; value: string | number | boolean }
  | { op: 'call'; id: NodeId; method: string; args: (string | number | boolean | null)[] }
  | { op: 'canvas'; id: NodeId; cmds: CanvasCmd[] };

// ───────────── sync queries (sandbox → host → renderer → host → sandbox) ─────────────

export type Query =
  | { q: 'rect'; id: NodeId }
  | { q: 'rects'; id: NodeId }
  | { q: 'box'; id: NodeId }
  | { q: 'cs'; id: NodeId; pseudo?: string; prop?: string }
  | { q: 'svg'; id: NodeId; method: string; args?: number[] }
  | { q: 'measureText'; font: string; text: string }
  | { q: 'imageData'; id: NodeId; x: number; y: number; w: number; h: number }
  | { q: 'canvasCall'; id: NodeId; method: string; args?: unknown[] }
  | { q: 'elementFromPoint'; x: number; y: number }
  | { q: 'validity'; id: NodeId }
  | { q: 'matchMedia'; query: string }
  | { q: 'viewport' }
  | { q: 'anims'; id: NodeId; subtree: boolean };

export interface AnimKeyframe {
  offset: number;
  easing: string;
  composite: 'replace' | 'add' | 'accumulate' | null;
  values: Record<string, string>;
}

export interface AnimTiming {
  duration: number;
  delay: number;
  endDelay: number;
  iterations: number | null;
  iterationStart: number;
  direction: 'normal' | 'reverse' | 'alternate' | 'alternate-reverse';
  fill: 'none' | 'forwards' | 'backwards' | 'both';
  easing: string;
}

export type AnimRecord =
  | { aid: number; remove: true }
  | {
    aid: number; target: NodeId; css: { kind: 'animation' | 'transition'; name: string; origin: number };
    startTime: number | null; holdTime: number | null; playbackRate: number; cancelled: boolean;
  }
  | {
    aid: number; target: NodeId; keyframes: AnimKeyframe[]; timing: AnimTiming;
    composite: 'replace' | 'add' | 'accumulate'; iterationComposite: 'replace' | 'accumulate';
    startTime: number | null; holdTime: number | null; playbackRate: number;
  };

export const MAX_ANIM_BATCH_BYTES = 4 * 1024 * 1024;

// ───────────── renderer → host → sandbox: events ─────────────

export interface WidgetEvent {
  type: string;
  targetId: NodeId;
  relatedId?: NodeId;
  clientX?: number;
  clientY?: number;
  offsetX?: number;
  offsetY?: number;
  pageX?: number;
  pageY?: number;
  movementX?: number;
  movementY?: number;
  button?: number;
  buttons?: number;
  detail?: number;
  pointerId?: number;
  pointerType?: string;
  isPrimary?: boolean;
  pressure?: number;
  deltaX?: number;
  deltaY?: number;
  deltaMode?: number;
  key?: string;
  code?: string;
  repeat?: boolean;
  modifiers?: number; // bit 0 alt, 1 ctrl, 2 meta, 3 shift
  value?: string;
  checked?: boolean;
  selectedIndex?: number;
  inputType?: string;
  data?: string | null;
  submitterId?: NodeId;
  defaultActions?: boolean;
  editorGeneration?: number;
  editorCommand?: number;
  naturalWidth?: number;
  naturalHeight?: number;
  open?: boolean;
  scrollTop?: number;
  scrollLeft?: number;
}

/**
 * Event types the renderer delivers to the widget: the widget profile's (no keyboard, focus, touch, wheel or scroll).
 * `dblclick` and `contextmenu` come from the renderer itself, only for a double tap or a hold the widget claims with
 * `touch-action: none` (gesture-policy.ts); the engine's own never reach the widget. Everything else is ignored.
 */
export const EVENT_TYPES: readonly string[] = PROFILE_EVENTS;

// ───────────── host ⇄ renderer envelope ─────────────

export type HostToRenderer =
  | { type: 'ops'; ops: RenderOp[] }
  | { type: 'query'; qid: number; query: Query }
  | { type: 'listen'; types: string[] }
  | { type: 'preventDefault'; keys: string[] }
  | { type: 'anim'; now: number; anims: AnimRecord[] }
  | { type: 'requestFrame' }
  /** The chat's theme (docs/sdk-v1.md §3.4): the renderer sets the CSS variables `themeVariables` makes of it on the widget's root. */
  | { type: 'theme'; theme: WidgetTheme }
  /**
   * What the client draws over the widget (docs/sdk-v1.md §2.17, §3.4): the renderer sets the
   * `--tg-content-safe-area-*` variables `contentSafeAreaVariables` makes of it on the widget's root (0 until then).
   */
  | { type: 'safeArea'; inset: ContentSafeArea }
  /** The runtime is gone (fatal) or paused: keep the last render, stop forwarding events and frames, show a badge with `reason`. */
  | { type: 'frozen'; reason: string; fatal: boolean }
  /**
   * How the renderer arbitrates presses (gesture-policy.ts, docs/sdk-v1.md §5.5). `bubble`: the renderer plays the
   * bubble itself (the web hosts, which have no recognizers of their own): a press the bubble takes ends for the widget
   * with `pointercancel` and the host gets `bubble`. Off (the default), the client's own recognizers take presses and
   * the renderer only gives the widget the double taps and holds it claims. `touch`: arbitrate a mouse's primary
   * button as a finger (the dev app reproducing a phone). `rtl`: the leading edge is the right one.
   */
  | { type: 'arbitrate'; bubble: boolean; touch?: boolean; rtl?: boolean }
  /**
   * The host's policy (`SandboxConfig.policy`), sent at load: under `strict` (also before this message, and for anything
   * but exactly `compat`) the renderer counts heavy boxes (paint-policy.ts) and reports `limit` past them; under `compat`
   * it counts nothing. `paintReports` (tooling: the Node harness): also report the counts on every change (`paint`).
   */
  | { type: 'policy'; policy: SandboxPolicy; paintReports?: boolean };

export type RendererToHost =
  | { type: 'ready' }
  | { type: 'event'; ev: WidgetEvent; trusted?: boolean }
  | { type: 'result'; qid: number; result: unknown; binary?: ArrayBuffer; binaryBase64?: string }
  | { type: 'size'; width: number; height: number; innerWidth: number; innerHeight: number; dpr: number }
  | { type: 'resize'; entries: { id: NodeId; width: number; height: number }[] }
  | { type: 'frame'; time: number }
  | { type: 'media' }
  /**
   * Sent on every primary pointerdown: what of this press the widget claims
   * (gesture-policy.ts `claimOf`: the `touch-action` the widget gave the target
   * and its ancestors, and a pointerdown the sandbox has prevented there before).
   * `x`: horizontal drags; `hold`: a hold or right click (no message menu). Taps
   * and double taps are always the widget's, vertical drags always the chat's:
   * `y` and `wants` (`y`, the field before v1) are always false since v0.1.3.
   * The host's `onGestureClaim`, docs/sdk-v1.md §5.5.
   */
  | { type: 'gesture'; wants: boolean; x: boolean; y: boolean; hold: boolean }
  /**
   * Sent when the primary press ends (pointerup or pointercancel), whether or not the widget listens: with
   * `gesture` (the press's start) the host's haptic gate knows when a finger is down on the widget and when it
   * was lifted (docs/sdk-v1.md §2.6). Trusted input only.
   */
  | { type: 'release' }
  /** With `arbitrate` `bubble` on: the bubble took a press (`action`); (x, y): the pointer then, in widget coordinates. */
  | { type: 'bubble'; action: BubbleAction; gesture: Gesture; x: number; y: number; edge: boolean }
  /**
   * Past a paint limit (paint-policy.ts, docs/decisions.md "Heavy boxes"): more than 16 heavy boxes (`paint-limit`, with
   * their count) or more than 2 with backdrop-filter (`backdrop-limit`). The renderer has already drawn the frame without
   * the effects; the host stops the widget (`die`) with `paintLimitMessage(code, count)`. Once per widget, strict only.
   */
  | { type: 'limit'; code: PaintLimitCode; count: number }
  /** With `policy` `paintReports`: the heavy-box counts whenever they change (tooling; a host ignores it). */
  | { type: 'paint'; heavy: number; backdrop: number }
  | { type: 'violation'; detail: string };

// ───────────── worker ⇄ host envelope ─────────────

export interface SandboxConfig {
  memoryLimitBytes: number;
  /** Longest continuous run of widget JS in one task before the runtime is killed. */
  cpuLimitMs: number;
  /** JS time the widget may use per `cpuQuotaWindowMs` (sliding window); beyond it timers and frames are deferred, never killed. */
  cpuQuotaMs: number;
  cpuQuotaWindowMs: number;
  /** Kill after this long of uninterrupted throttling; 0 disables (default). */
  cpuKillAfterMs: number;
  /**
   * The host's main-thread work for the widget per `cpuQuotaWindowMs`, apart from the JS quota: the time its layout
   * queries block the renderer and the modelled cost of the ops it sends (`opsCostMs` in cpu-budget.ts). Beyond it
   * timers and frames are deferred like under the JS quota. Optional for older configs (LIMIT_DEFAULTS).
   */
  mainThreadQuotaMs?: number;
  /** Live timers (setTimeout + setInterval) a widget may hold; the shim throws RangeError beyond it, the glue refuses as a backstop. */
  maxTimers: number;
  /** Initial visibility (see `setVisible`): hidden widgets get no frames and timers at 1 Hz. */
  visible: boolean;
  viewport: { innerWidth: number; innerHeight: number; dpr: number };
  theme: 'light' | 'dark';
  /** The chat's palette (docs/sdk-v1.md §2.3), checked by the glue (`checkThemeParams`); missing keys take the scheme's defaults. */
  themeParams?: Partial<ThemeParams>;
  /** The client's UI language, BCP 47 (`checkLocale`); `navigator.language` and the Intl default. */
  locale?: string;
  /** What the client draws over the widget (docs/sdk-v1.md §2.17), logical px; all 0 when absent. The shim checks it. */
  contentSafeArea?: ContentSafeArea;
  /** The widget profile the glue enforces (`PROFILE_VERSION`); the shim reports a mismatch with its own. */
  profile?: number;
  /** The client's preload of Web Storage (docs/sdk-v1.md §2.8), checked by the glue (`checkStorageCommit`). */
  storage?: StorageCommit;
  /**
   * The bot's two strings and where the widget is (docs/sdk-v1.md §2.13), checked by the glue: null for none; a
   * forwarded copy (`isCopy`) gets no `chatData` and no `chatType`, and no `viewer` unless it may request (`canQuery`).
   */
  chatData?: string | null;
  publicData?: string | null;
  isCopy?: boolean;
  /** The server's `can_query`: the shim refuses a request at once without it (the glue refuses it anyway). */
  canQuery?: boolean;
  /** An inline result before it is sent (`WidgetContext.preview`): `TelegramWidget.isPreview`; requests refused at once. */
  isPreview?: boolean;
  chatType?: ChatType | null;
  viewer?: WidgetUser | null;
  /** The client has the wallet for this user and the widget may request (docs/sdk-v1.md §2.18): `TelegramWidget.wallet`. */
  wallet?: boolean;
  /**
   * Optional wire forms this host understands, opt-in per host (docs/protocol.md,
   * "Features"). Absent or empty: plain JSON only, which every host and renderer speaks.
   * `canvasWords`: canvas commands as a word stream through `__hostCanvas`.
   */
  features?: string[];
  /**
   * `strict` (default): privileged bridge calls need a user gesture, text values are capped at
   * 64 KB and visible text is budgeted. `compat`: the behaviour hosts had before that hardening,
   * for tests and tooling. Anything but exactly `compat` means `strict` (`resolvePolicy`).
   */
  policy?: SandboxPolicy;
}

export type SandboxPolicy = 'compat' | 'strict';

/** Fail-closed policy parsing shared by every TS host: a typo or a missing value is `strict`. */
export function resolvePolicy(policy: unknown): SandboxPolicy {
  return policy === 'compat' ? 'compat' : 'strict';
}

/** Defaults shared by every host (the native configs repeat them; docs/decisions.md, "Strict limits from the start"). */
export const LIMIT_DEFAULTS = {
  memoryLimitBytes: 8 * 1024 * 1024,
  cpuLimitMs: 500,
  cpuQuotaMs: 300,
  cpuQuotaWindowMs: 1000,
  cpuKillAfterMs: 0,
  /** The host's main-thread work for the widget per `cpuQuotaWindowMs`: layout queries and the modelled cost of its ops. */
  mainThreadQuotaMs: 100,
  maxTimers: 64,
} as const;

/** Timer delays are clamped to this on every platform (24 h, like Telegram). */
export const MAX_TIMER_DELAY_MS = 86_400_000;

/**
 * Web only: the QuickJS wasm module imports its memory, and the glue hands it one
 * whose `maximum` is INITIAL + memoryLimitBytes. INITIAL mirrors the variant's
 * INITIAL_MEMORY (16 MB: static data, C stack and dlmalloc's first arena); the
 * runtime's heap may grow memoryLimitBytes beyond it. See src/host/worker.ts.
 */
export const WASM_PAGE_BYTES = 65_536;
export const WASM_INITIAL_PAGES = (16 * 1024 * 1024) / WASM_PAGE_BYTES;

export type HostToWorker =
  | { type: 'init'; html: string; sab: SharedArrayBuffer; config: SandboxConfig; sandboxBundle: string }
  /** Pool: boot QuickJS and evaluate the sandbox bundle now (`config` is provisional: limits only), the widget comes later with `start`. */
  | { type: 'warm'; sab: SharedArrayBuffer; config: SandboxConfig; sandboxBundle: string }
  /** Pool: load the widget into a warmed worker (`config` is the real one). */
  | { type: 'start'; html: string; config: SandboxConfig }
  | { type: 'event'; ev: WidgetEvent; gesture?: number }
  | { type: 'frame'; time: number }
  | { type: 'viewport'; viewport: SandboxConfig['viewport'] }
  | { type: 'resize'; entries: { id: NodeId; width: number; height: number }[] }
  | { type: 'theme'; theme: 'light' | 'dark'; themeParams?: Partial<ThemeParams> }
  /** The shim's `safeArea` dispatch: `TelegramWidget.contentSafeAreaInset` and `contentSafeAreaChanged` (docs/sdk-v1.md §2.17). */
  | { type: 'safeArea'; inset: ContentSafeArea }
  | { type: 'visibility'; visible: boolean }
  /** Into the widget (the shim's `bridge` dispatch): the bot's data changed, or the answer to a request. */
  | { type: 'bridge'; event: BridgeEvent };

/** The bot's two strings as the widget sees them (checked: null for none, at most 1024 bytes each). */
export interface WidgetDataState { chatData: string | null; publicData: string | null }

/**
 * Host → sandbox bridge events (docs/sdk-v1.md §3.3): `data` sets both strings (the shim fires `chatDataChanged`
 * and then `publicDataChanged` for the one that differs); `answer` settles request `id` with `result` or a refusal /
 * failure `error` (`reason`: the request gate's verdict, or `unsupported`, for the error's message).
 */
export type BridgeEvent =
  | ({ type: 'data' } & WidgetDataState)
  | { type: 'answer'; id: number; result?: string | null; error?: RequestError; reason?: string };

export interface Stats {
  jsMs: number;
  tasks: number;
  memoryBytes: number;
  ops: number;
  queries: number;
  queryMs: number;
  queryCacheHits: number;
  missedMutations: number;
  /** Tasks deferred by the CPU quota (frames collapse into one, so this undercounts frames). */
  throttledTasks: number;
  /** JS time used in the current quota window at the time of the report. */
  cpuWindowMs: number;
}

export type WorkerToHost =
  // Messages whose payload originates in the sandbox (UNTRUSTED):
  | { type: 'ops'; ops: unknown }
  | { type: 'query'; query: unknown }
  | { type: 'listen'; types: unknown }
  | { type: 'pd'; keys: unknown }
  | { type: 'anim'; anims: unknown; now: number }
  | { type: 'raf' }
  | { type: 'log'; level: string; text: string }
  | { type: 'diag'; kind: string; detail: string }
  | { type: 'bridge'; method: string; args: unknown; gesture?: number }
  /** The areas of Web Storage a task changed, in full (`__host('storage', …)`, docs/sdk-v1.md §3.2). */
  | { type: 'storage'; commit: unknown }
  // Messages produced by the trusted worker glue:
  /** The sandbox bundle is evaluated; a `start` can follow at no boot cost (pool). */
  | { type: 'warmed'; bootMs: number }
  | { type: 'loaded' }
  | { type: 'stats'; stats: Stats }
  | { type: 'dead'; reason: string };

// Sync bridge SharedArrayBuffer layout (Int32 header, then payload bytes).
export const SAB_SIZE = 16 * 1024 * 1024;
export const SAB_HEADER = 16; // bytes
export const SAB_STATE = 0; // 0 idle, 1 waiting, 2 done
export const SAB_JSON_LEN = 1;
export const SAB_BIN_LEN = 2;
