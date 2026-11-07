// SDK v1 checks (docs/sdk-v1.md §2.5, §2.6, §2.8, §2.9, §3). Pure and side-effect
// free, shared by the shim (which throws `TypeError` on what it rejects) and every
// TS glue (worker, WidgetHost, the Node harness), which check the same calls
// again because the shim is untrusted. Twins: SdkPolicy.swift, SdkPolicy.kt,
// sdk_policy.cpp; all four are checked against tests/fixtures/sdk-policy.json,
// so a rule change goes to the four copies and the fixture together.

export const MAX_LINK_LENGTH = 256;
/** Bytes of keys and values (UTF-8) per storage area and message (4 KB: a 50-item to-do list; docs/decisions.md, "Strict limits from the start"). */
export const STORAGE_QUOTA_BYTES = 4096;
export const MAX_LOCALE_LENGTH = 35;
export const DEFAULT_LOCALE = 'en-US';
export const HAPTIC_BURST = 5;
export const HAPTIC_PER_SECOND = 10;
/** `haptic-limit` is reported at most this often. */
export const HAPTIC_REPORT_MS = 1000;
/** Haptics are free (under the rate limit) while a finger or a mouse button is down on the widget and this long after it is lifted. */
export const HAPTIC_TOUCH_WINDOW_MS = 5000;
/** Outside a touch, the first haptic opens a series: the calls of its first second pass (under the rate limit), later ones are dropped. */
export const HAPTIC_SERIES_MS = 1000;
/** Without a touch since the last series, the next one may start this long after the last one began. */
export const HAPTIC_SERIES_GAP_MS = 10000;
/** A press whose end the host never saw (the finger left the view) counts as lifted this long after it began. */
export const HAPTIC_PRESS_MAX_MS = 30000;
/** Handlers per `onEvent` type (and for the legacy `onTheme`); more are ignored. */
export const MAX_HANDLERS_PER_EVENT = 16;

export const THEME_KEYS = [
  'bg_color', 'text_color', 'hint_color', 'link_color', 'button_color', 'button_text_color',
  'secondary_bg_color', 'accent_text_color', 'subtitle_text_color', 'destructive_text_color',
  'section_bg_color', 'section_header_text_color', 'section_separator_color', 'header_bg_color', 'bottom_bar_bg_color',
] as const;
export type ThemeKey = (typeof THEME_KEYS)[number];
export type ThemeParams = Record<ThemeKey, string>;

export const HAPTIC_IMPACT_STYLES = ['light', 'medium', 'heavy', 'rigid', 'soft'];
export const HAPTIC_NOTIFICATION_TYPES = ['error', 'success', 'warning'];
export const TELEGRAM_HOSTS = ['t.me', 'telegram.me', 'telegram.dog'];

/** The edge of the bubble that stays put on screen when the widget's height changes (`setResizeAnchor`, docs/sdk-v1.md §2.12). */
export type ResizeAnchor = 'top' | 'bottom';
export const RESIZE_ANCHORS: readonly ResizeAnchor[] = ['top', 'bottom'];
/** Until the widget calls `setResizeAnchor`: the bubble grows and shrinks downward. */
export const DEFAULT_RESIZE_ANCHOR: ResizeAnchor = 'top';

// ── the bot's data and requests (docs/sdk-v1.md §2.13–§2.15) ──

/** UTF-8 bytes of `chatData` and of `publicData` each (the server's limit, `chat_data` / `public_data`). */
export const MAX_WIDGET_DATA_BYTES = 1024;
/** UTF-8 bytes of a request's `data` (as `callback_data`). */
export const MAX_REQUEST_DATA_BYTES = 64;
/** UTF-8 bytes of an answer's `result`. */
export const MAX_REQUEST_RESULT_BYTES = 1024;
/** UTF-16 code units of a `copyText` text (as the `copy_text` button's). */
export const MAX_COPY_TEXT_LENGTH = 256;
/** A background request (not from a press): one when the widget appears on screen, then at most one per this long while it stays visible. */
export const BACKGROUND_REQUEST_INTERVAL_MS = 5000;
/** The request ids the shim numbers its calls with. */
export const MAX_REQUEST_ID = 2147483647;
/** A Telegram user id fits in 53 bits: a JS number holds it exactly. */
export const MAX_VIEWER_ID = 9007199254740991;
/** UTF-16 code units of the viewer's first and last name (each 1 to this). */
export const MAX_VIEWER_NAME_LENGTH = 64;
/** The viewer's username: 5–32 characters of A–Z, a–z, 0–9 and _. */
export const VIEWER_USERNAME = /^[A-Za-z0-9_]{5,32}$/;
/** The chat kinds a host may report (`Chat.type` of the Bot API); a value outside them is no chat type (null). */
export const CHAT_TYPES = ['private', 'group', 'supergroup', 'channel'] as const;
export type ChatType = (typeof CHAT_TYPES)[number];
/**
 * What a refused or failed request rejects with in the widget (`error.code`); `REJECTED` and `BAD_REQUEST` only for
 * a wallet call: the user declined in the client's window, the wallet refused the transaction or the payload.
 */
export const REQUEST_ERRORS = ['NOT_ALLOWED', 'BUSY', 'RATE_LIMITED', 'TIMEOUT', 'FAILED', 'INVALID_ANSWER', 'REJECTED', 'BAD_REQUEST'] as const;
export type RequestError = (typeof REQUEST_ERRORS)[number];
/** What a client reports for a request it could not complete: the bot did not answer in time, flood wait, anything else. */
export type RequestFailure = 'TIMEOUT' | 'RATE_LIMITED' | 'FAILED';
/** What a client reports for a wallet call it could not complete: a request's failures, the user's refusal, the wallet's. */
export type WalletFailure = RequestFailure | 'REJECTED' | 'BAD_REQUEST';

export type LinkMethod = 'openLink' | 'openTelegramLink';
export type HapticCall = { haptic: 'impact' | 'notification' | 'selection'; value?: string };
export type WidgetAction =
  | { kind: 'ready' }
  | { kind: 'openLink'; url: string }
  | { kind: 'openTelegramLink'; url: string }
  | ({ kind: 'haptic' } & HapticCall);
/**
 * A bridge call that passed its checks: an action for the client (`onAction`); the resize anchor, which no action
 * carries (the host keeps the latest one next to the height it reports); whether the widget watches its shared
 * `public_data` (the host keeps the latest, the client polls only for widgets that watch); a request to the bot (the
 * request gate, then the client's `onRequest`; `wallet`: the widget asks the server to attach the user's wallet); a
 * wallet call (the request gate, then the client's `onWallet`); a text to copy (`onCopyText`).
 */
export type BridgeCall =
  | WidgetAction
  | { kind: 'setResizeAnchor'; edge: ResizeAnchor }
  | { kind: 'watchPublicData'; on: boolean }
  | { kind: 'request'; id: number; data: string; wallet?: true }
  | { kind: 'wallet'; id: number; call: WalletCall }
  | { kind: 'copyText'; text: string };

/** Methods that need the task's user gesture (docs/protocol.md, "User activation"); one press unlocks one of them. `request` has its own gate (`RequestGate`). */
export const PRIVILEGED_METHODS = new Set(['openLink', 'openTelegramLink', 'copyText']);

/**
 * The bridge methods a preview (an inline result before it is sent: `WidgetContext.preview`) may call; everything
 * else is refused there: no request, link, copy or haptic. A preview gets no input either (its host drops the
 * renderer's events and gesture claims).
 */
export const PREVIEW_METHODS: ReadonlySet<string> = new Set(['ready', 'setResizeAnchor']);
export function allowedInPreview(method: unknown): boolean {
  return typeof method === 'string' && PREVIEW_METHODS.has(method);
}

/** UTF-8 length of a JS string; a lone surrogate counts 3 bytes (what U+FFFD takes). */
export function utf8Length(s: string): number {
  let n = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c < 0x80) n += 1;
    else if (c < 0x800) n += 2;
    else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length && (s.charCodeAt(i + 1) & 0xfc00) === 0xdc00) { n += 4; i++; }
    else n += 3;
  }
  return n;
}

const HEX = '0123456789ABCDEF';
const isHex = (c: number) => (c >= 48 && c <= 57) || (c >= 65 && c <= 70) || (c >= 97 && c <= 102);
/** ASCII that may stand unescaped after the authority: RFC 3986 unreserved, reserved and `%`. */
const URL_ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%";

/** Lowercases A–Z only: a Unicode mapping (U+212A KELVIN SIGN → `k`) must not turn a non-ASCII host into an ASCII one. */
const asciiLower = (s: string) => s.replace(/[A-Z]/g, (c) => String.fromCharCode(c.charCodeAt(0) + 32));
const isTelegramHost = (host: string) => TELEGRAM_HOSTS.some((t) => host === t || host.endsWith('.' + t));

/**
 * The URL a link call opens, or null when it breaks the rules: an absolute
 * `https:` URL, no user name or password, an ASCII host of LDH labels (an IDN in
 * its `xn--` form), an optional port, at most 256 characters once non-ASCII
 * characters after the host are percent-encoded as UTF-8. `openTelegramLink`
 * takes only t.me, telegram.me and telegram.dog (and their subdomains), any
 * path; `openLink` takes every other host. Scheme and host come out lowercase,
 * the rest as given.
 */
export function checkLink(method: LinkMethod, url: unknown): string | null {
  if (typeof url !== 'string' || utf8Length(url) > MAX_LINK_LENGTH) return null;
  if (asciiLower(url.slice(0, 8)) !== 'https://') return null;
  let end = 8;
  while (end < url.length && url[end] !== '/' && url[end] !== '?' && url[end] !== '#') end++;
  const authority = url.slice(8, end);
  if (authority.includes('@')) return null;
  const colon = authority.indexOf(':');
  const host = asciiLower(colon < 0 ? authority : authority.slice(0, colon));
  const port = colon < 0 ? null : authority.slice(colon + 1);
  if (port !== null && !/^[0-9]{1,5}$/.test(port)) return null;
  if (port !== null && Number(port) > 65535) return null;
  if (host.length === 0 || host.length > 253) return null;
  for (const label of host.split('.')) {
    if (label.length === 0 || label.length > 63 || !/^[a-z0-9-]+$/.test(label) || label[0] === '-' || label[label.length - 1] === '-') return null;
  }
  if (isTelegramHost(host) !== (method === 'openTelegramLink')) return null;
  let rest = '';
  for (let i = end; i < url.length; i++) {
    const c = url.charCodeAt(i);
    if (c < 0x80) {
      if (!URL_ASCII.includes(url[i])) return null;
      if (c === 37 && !(i + 2 < url.length && isHex(url.charCodeAt(i + 1)) && isHex(url.charCodeAt(i + 2)))) return null;
      rest += url[i];
      continue;
    }
    let cp = c;
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < url.length && (url.charCodeAt(i + 1) & 0xfc00) === 0xdc00) cp = 0x10000 + ((c - 0xd800) << 10) + (url.charCodeAt(++i) - 0xdc00);
    else if (c >= 0xd800 && c <= 0xdfff) return null;
    const bytes = cp < 0x800 ? [0xc0 | (cp >> 6), 0x80 | (cp & 63)]
      : cp < 0x10000 ? [0xe0 | (cp >> 12), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63)]
      : [0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 63), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63)];
    for (const b of bytes) rest += '%' + HEX[b >> 4] + HEX[b & 15];
  }
  const out = 'https://' + host + (port === null ? '' : ':' + port) + rest;
  return out.length > MAX_LINK_LENGTH ? null : out;
}

/** The haptic of a `haptic` bridge call's args, or null: `['impact', style]`, `['notification', type]`, `['selection']`. */
export function checkHaptic(args: unknown): HapticCall | null {
  if (!Array.isArray(args)) return null;
  if (args[0] === 'impact' && args.length === 2 && HAPTIC_IMPACT_STYLES.includes(args[1])) return { haptic: 'impact', value: args[1] };
  if (args[0] === 'notification' && args.length === 2 && HAPTIC_NOTIFICATION_TYPES.includes(args[1])) return { haptic: 'notification', value: args[1] };
  if (args[0] === 'selection' && args.length === 1) return { haptic: 'selection' };
  return null;
}

/** The resize anchor of a `setResizeAnchor` call's argument, or null: exactly `'top'` or `'bottom'`. */
export function checkResizeAnchor(edge: unknown): ResizeAnchor | null {
  return edge === 'top' || edge === 'bottom' ? edge : null;
}

/** The action of a bridge call, or null when the method is unknown or its arguments are wrong (`bridge-rejected`). */
export function checkBridgeCall(method: unknown, args: unknown): BridgeCall | null {
  if (!Array.isArray(args)) return null;
  switch (method) {
    case 'ready': return args.length === 0 ? { kind: 'ready' } : null;
    case 'openLink':
    case 'openTelegramLink': {
      const url = args.length === 1 ? checkLink(method, args[0]) : null;
      return url === null ? null : { kind: method, url };
    }
    case 'haptic': {
      const h = checkHaptic(args);
      return h === null ? null : { kind: 'haptic', ...h };
    }
    case 'setResizeAnchor': {
      const edge = args.length === 1 ? checkResizeAnchor(args[0]) : null;
      return edge === null ? null : { kind: 'setResizeAnchor', edge };
    }
    case 'watchPublicData':
      return args.length === 1 && typeof args[0] === 'boolean' ? { kind: 'watchPublicData', on: args[0] } : null;
    case 'request': {
      const id = args[0];
      const data = args.length === 2 || args.length === 3 ? checkRequestData(args[1]) : null;
      const options = args.length === 3 ? checkRequestOptions(args[2]) : { wallet: false };
      if (data === null || options === null || !isRequestId(id)) return null;
      return options.wallet ? { kind: 'request', id, data, wallet: true } : { kind: 'request', id, data };
    }
    case 'wallet': {
      // [id, method, the method's argument, data]
      const [id, name, arg, label] = args;
      const data = args.length === 4 ? checkRequestData(label) : null;
      if (data === null || !isRequestId(id)) return null;
      if (name === 'sendTransaction') {
        const transaction = checkWalletTransaction(arg);
        return transaction === null ? null : { kind: 'wallet', id, call: { method: 'sendTransaction', transaction, data } };
      }
      if (name === 'signData') {
        const payload = checkSignDataPayload(arg);
        return payload === null ? null : { kind: 'wallet', id, call: { method: 'signData', payload, data } };
      }
      return null;
    }
    case 'copyText': {
      const text = args.length === 1 ? checkCopyText(args[0]) : null;
      return text === null ? null : { kind: 'copyText', text };
    }
    default: return null;
  }
}

/** No lone surrogate: the string has a UTF-8 form (what the server and the clipboard take). */
function wellFormed(s: string): boolean {
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c < 0xd800 || c > 0xdfff) continue;
    if (c > 0xdbff || i + 1 >= s.length || (s.charCodeAt(i + 1) & 0xfc00) !== 0xdc00) return false;
    i++;
  }
  return true;
}

/** A request's `data`, or null: a well-formed string of 1–64 UTF-8 bytes. */
export function checkRequestData(data: unknown): string | null {
  if (typeof data !== 'string' || !wellFormed(data)) return null;
  const n = utf8Length(data);
  return n >= 1 && n <= MAX_REQUEST_DATA_BYTES ? data : null;
}

const isRequestId = (id: unknown): id is number => typeof id === 'number' && Number.isInteger(id) && id >= 1 && id <= MAX_REQUEST_ID;

/** A `request`'s options, or null: an object with at most `wallet`, a boolean (absent or null: false). */
export function checkRequestOptions(options: unknown): { wallet: boolean } | null {
  if (!isPlainObject(options) || !onlyKeys(options, ['wallet'])) return null;
  const w = options.wallet;
  return w === undefined || w === null ? { wallet: false } : typeof w === 'boolean' ? { wallet: w } : null;
}

/** A `copyText` text, or null: a well-formed string of 1–256 UTF-16 code units. */
export function checkCopyText(text: unknown): string | null {
  return typeof text === 'string' && text.length >= 1 && text.length <= MAX_COPY_TEXT_LENGTH && wellFormed(text) ? text : null;
}

/**
 * One of `chatData` / `publicData`: a well-formed string of 1–1024 UTF-8 bytes without a NUL (the server's rule for
 * its text fields); null for none (null, absent, the empty string the bot clears it with) and for anything else.
 */
export function checkWidgetDataValue(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 && wellFormed(value) && !value.includes('\0') && utf8Length(value) <= MAX_WIDGET_DATA_BYTES ? value : null;
}

/** The bot's data as the widget sees it, and the fields the client passed that broke the rules (the glue reports `data-rejected`). */
export interface CheckedWidgetData {
  chatData: string | null;
  publicData: string | null;
  rejected: ('chatData' | 'publicData')[];
}

/**
 * The full state of the bot's two strings, as the client passes it at creation and in `setData`. A forwarded copy
 * has no `chatData` (the server never copies it): the glue drops what the client passed, silently.
 */
export function checkWidgetData(data: unknown, copy: boolean): CheckedWidgetData {
  const d = (typeof data === 'object' && data !== null ? data : {}) as Record<string, unknown>;
  const out: CheckedWidgetData = { chatData: null, publicData: null, rejected: [] };
  for (const field of ['chatData', 'publicData'] as const) {
    const v = d[field];
    if (field === 'chatData' && copy) continue;
    out[field] = checkWidgetDataValue(v);
    if (out[field] === null && v !== undefined && v !== null && v !== '') out.rejected.push(field);
  }
  return out;
}

/**
 * Who looks at the widget, as the widget sees it (Mini Apps' `WebAppUser`): `id`, `first_name`, `last_name`,
 * `username`, `is_premium`. The input has the same field names. No viewer (null) without a positive integer id of at
 * most 53 bits; a field that breaks its rule is left out and listed in `rejected` (in this order): a name 1–64 UTF-16
 * code units without a lone surrogate, a username 5–32 characters of A–Z, a–z, 0–9 and _, `is_premium` a boolean,
 * kept only when true. Absent (undefined or null) fields are left out without a word.
 */
export function checkViewer(viewer: unknown): { viewer: WidgetUser | null; rejected: string[] } {
  const v = (typeof viewer === 'object' && viewer !== null ? viewer : {}) as Record<string, unknown>;
  const id = v.id;
  if (typeof id !== 'number' || !Number.isInteger(id) || id < 1 || id > MAX_VIEWER_ID) return { viewer: null, rejected: [] };
  const out: WidgetUser = { id };
  const rejected: string[] = [];
  const name = (s: unknown) => typeof s === 'string' && s.length >= 1 && s.length <= MAX_VIEWER_NAME_LENGTH && wellFormed(s);
  for (const field of ['first_name', 'last_name'] as const) {
    const s = v[field];
    if (s === undefined || s === null) continue;
    if (name(s)) out[field] = s as string;
    else rejected.push(field);
  }
  if (v.username !== undefined && v.username !== null) {
    if (typeof v.username === 'string' && VIEWER_USERNAME.test(v.username)) out.username = v.username;
    else rejected.push('username');
  }
  if (v.is_premium !== undefined && v.is_premium !== null) {
    if (v.is_premium === true) out.is_premium = true;
    else if (v.is_premium !== false) rejected.push('is_premium');
  }
  return { viewer: out, rejected };
}

/** Why a viewer field was left out (`data-rejected`: `viewer.<field>: <why>; the widget does not see it`). */
export const VIEWER_FIELD_RULES: Record<string, string> = {
  first_name: 'not text of 1–64 UTF-16 code units',
  last_name: 'not text of 1–64 UTF-16 code units',
  username: 'not 5–32 characters of A–Z, a–z, 0–9 and _',
  is_premium: 'not a boolean',
};

/** The account that looks at the widget, as the client passes it (docs/sdk-v1.md §4): its id and, when it has them, its names, username and Premium. */
export interface WidgetViewer {
  id: number;
  firstName?: string;
  lastName?: string;
  username?: string;
  isPremium?: boolean;
}

/** The viewer as the widget sees it (`TelegramWidget.viewer`, the shape of Mini Apps' `WebAppUser`); `is_premium` only when true. */
export interface WidgetUser {
  id: number;
  first_name?: string;
  last_name?: string;
  username?: string;
  is_premium?: true;
}

/** A client's `WidgetViewer` in the widget's field names, for `checkViewer`. */
export function viewerUser(viewer: WidgetViewer | null | undefined): Record<string, unknown> | null {
  if (typeof viewer !== 'object' || viewer === null) return null;
  return { id: viewer.id, first_name: viewer.firstName, last_name: viewer.lastName, username: viewer.username, is_premium: viewer.isPremium };
}

export function checkChatType(chatType: unknown): ChatType | null {
  return (CHAT_TYPES as readonly unknown[]).includes(chatType) ? (chatType as ChatType) : null;
}

/**
 * The bot's answer to a request as the widget gets it: `result`, a well-formed string of at most 1024 UTF-8 bytes
 * without a NUL (empty allowed), or none. Null when it breaks the rules (the widget gets `INVALID_ANSWER`). An
 * answer carries no data: the bot edits its data with separate calls, which reach the host as `setData`.
 */
export function checkRequestAnswer(answer: unknown): { result: string | null } | null {
  const r = typeof answer === 'object' && answer !== null ? (answer as Record<string, unknown>).result : undefined;
  if (r === undefined || r === null) return { result: null };
  return typeof r === 'string' && wellFormed(r) && !r.includes('\0') && utf8Length(r) <= MAX_REQUEST_RESULT_BYTES ? { result: r } : null;
}

/** The bot's two strings as the client has them for this message (absent = none); the full state each time. */
export interface WidgetData {
  chatData?: string | null;
  publicData?: string | null;
}

/**
 * Where the widget is and what it may do, from the message (`messageMediaWidget`'s flags) and the chat; fixed for the
 * host's life. The server computes the flags; the framework derives nothing itself (docs/sdk-v1.md §2.14).
 */
export interface WidgetContext {
  /** `is_copy`: the message is a forwarded copy: never any chatData. */
  isCopy?: boolean;
  /** `can_query`: requests are allowed, on a tap. */
  canQuery?: boolean;
  /** `background_query`: requests without a tap too (the original in the private chat with the bot): one when the widget appears on screen, then one per 5 s while visible; one made off screen waits for it to appear. */
  backgroundQuery?: boolean;
  /**
   * The client has Gram Wallet for this user: it supports the wallet and the wallet is available in the user's country
   * (docs/sdk-v1.md §2.18). The widget gets `TelegramWidget.wallet` only with `canQuery` too, never in a preview.
   */
  wallet?: boolean;
  /** The kind of chat the message is in. A copy gets none (null in the widget): the bot must not learn where its widget was forwarded. */
  chatType?: ChatType;
  /** The account that looks at the widget. Only a widget with `canQuery` gets it (null elsewhere, previews included). */
  viewer?: WidgetViewer;
  /**
   * An inline result shown before it is sent (no message yet): the widget gets its data and runs as usual, but no
   * input, no requests or other bridge calls but `ready` / `setResizeAnchor`, no chat type or viewer, and Web
   * Storage that is never committed. The request flags are ignored.
   */
  preview?: boolean;
}

/**
 * What the client sends to the bot: `data` (1–64 bytes). Whether it came from a tap stays in the framework: the server and the bot cannot tell.
 * `wallet`: the widget asked for the user's wallet (only on a tap): the client asks the server to attach the wallet card (`with_wallet`).
 */
export interface WidgetRequest {
  data: string;
  wallet?: boolean;
}

/** The bot's answer as the client hands it to the host: the server's `result` (at most 1024 bytes; absent: an empty answer). */
export interface WidgetRequestAnswer {
  result?: string | null;
}

/** What a client's failed request becomes in the widget: `'TIMEOUT'`, `'RATE_LIMITED'`, `'FAILED'`, or `{ code }` with one of them; anything else is `FAILED`. */
export function requestFailure(reason: unknown): RequestFailure {
  const code = typeof reason === 'object' && reason !== null ? (reason as Record<string, unknown>).code : reason;
  return code === 'TIMEOUT' || code === 'RATE_LIMITED' ? code : 'FAILED';
}

/** What a client's failed wallet call becomes in the widget: a request's failures, `'REJECTED'`, `'BAD_REQUEST'` (or `{ code }`); anything else is `FAILED`. */
export function walletFailure(reason: unknown): WalletFailure {
  const code = typeof reason === 'object' && reason !== null ? (reason as Record<string, unknown>).code : reason;
  return code === 'REJECTED' || code === 'BAD_REQUEST' ? code : requestFailure(code);
}

// ── the wallet (docs/sdk-v1.md §2.18): TON Connect's formats, Gram Wallet only ──

/** Messages in one transaction: what Gram Wallet takes (its TON Connect `maxMessages`). */
export const MAX_WALLET_MESSAGES = 255;
/** Characters of a message's `payload` or `stateInit` (a bag of cells in base64). */
export const MAX_WALLET_BOC_LENGTH = 8192;
/** Characters of every message's `payload` and `stateInit` together. */
export const MAX_WALLET_BOC_TOTAL = 65536;
/** UTF-8 bytes of a `signData` text; characters of its `bytes` or `cell` (base64). */
export const MAX_SIGN_DATA_LENGTH = 4096;
/** UTF-8 bytes of a `cell` payload's TL-B schema. */
export const MAX_SIGN_DATA_SCHEMA_BYTES = 1024;
/** After the answer to a call made by a tap (a request or a wallet call) reached the widget, one wallet call may follow without a new tap this long. */
export const WALLET_GRANT_MS = 10000;
/** TON Connect's network ids: the mainnet and the testnet. */
export const WALLET_NETWORKS = ['-239', '-3'];

/** One message of a transaction, as TON Connect's `sendTransaction` has it: the destination, nanotons as a decimal string, a body and a `StateInit` in base64. */
export interface WalletMessage {
  address: string;
  amount: string;
  payload?: string;
  stateInit?: string;
}

/** A transaction as TON Connect's `sendTransaction` request has it (its field names); the client hands it to its TON Connect window as is. */
export interface WalletTransaction {
  valid_until?: number;
  network?: string;
  from?: string;
  messages: WalletMessage[];
}

/** A `signData` payload as TON Connect has it: a text, bytes, or a cell with its TL-B schema. The client sets the domain (`bot<bot_id>`), never the widget. */
export interface SignDataPayload {
  type: 'text' | 'binary' | 'cell';
  text?: string;
  bytes?: string;
  schema?: string;
  cell?: string;
  network?: string;
  from?: string;
}

/** A wallet call that passed its checks; `data` (1–64 bytes) goes to the bot with the call's outcome, as a request's. */
export type WalletCall =
  | { method: 'sendTransaction'; transaction: WalletTransaction; data: string }
  | { method: 'signData'; payload: SignDataPayload; data: string };

const isPlainObject = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const onlyKeys = (o: Record<string, unknown>, keys: readonly string[]) => Object.keys(o).every((k) => keys.includes(k));
/** Absent: undefined or null (a JSON null the bot wrote). */
const absent = (v: unknown) => v === undefined || v === null;
const RAW_ADDRESS = /^(0|-1):[0-9a-fA-F]{64}$/;
const FRIENDLY_ADDRESS = /^[A-Za-z0-9+/_-]{48}$/;
const AMOUNT = /^(0|[1-9][0-9]{0,36})$/;
const BASE64 = /^[A-Za-z0-9+/_-]+={0,2}$/;

/** A TON address, or null: raw (`0:` or `-1:` and 64 hex digits) or user-friendly (48 characters of base64 or base64url); a transaction's `from`. The wallet checks the rest. */
export function checkWalletAddress(address: unknown): string | null {
  return typeof address === 'string' && (RAW_ADDRESS.test(address) || FRIENDLY_ADDRESS.test(address)) ? address : null;
}

/** A message's destination, or null: user-friendly only (TON Connect: the wallet must refuse a raw one; the bounce flag is in it). */
export function checkWalletDestination(address: unknown): string | null {
  return typeof address === 'string' && FRIENDLY_ADDRESS.test(address) ? address : null;
}

const base64 = (v: unknown, max: number): v is string => typeof v === 'string' && v.length <= max && BASE64.test(v);

/** The optional `network` and `from` of a transaction or a payload into `out`; false when one breaks its rule. */
function walletTarget(input: Record<string, unknown>, out: { network?: string; from?: string }): boolean {
  if (!absent(input.network)) {
    if (!WALLET_NETWORKS.includes(input.network as string)) return false;
    out.network = input.network as string;
  }
  if (!absent(input.from)) {
    const from = checkWalletAddress(input.from);
    if (from === null) return false;
    out.from = from;
  }
  return true;
}

/**
 * A transaction for `sendTransaction`, or null: TON Connect's fields only (`valid_until`, `network`, `from`,
 * `messages`), 1–255 messages of `address`, `amount` and optionally `payload` and `stateInit`. `valid_until` an
 * integer of 1 to 2^32 − 1 (Unix seconds), `network` `-239` or `-3`, `from` raw or user-friendly, a message's
 * `address` user-friendly only, amounts decimal strings of nanotons (at most 37 digits, no leading zero), payloads
 * base64 of at most 8192 characters each and 65536 together. Absent and null fields are left out. The wallet checks
 * the rest (the network and the sender against the account, the cells, the expiry) and refuses with `BAD_REQUEST`.
 */
export function checkWalletTransaction(tx: unknown): WalletTransaction | null {
  if (!isPlainObject(tx) || !onlyKeys(tx, ['valid_until', 'network', 'from', 'messages'])) return null;
  const out: WalletTransaction = { messages: [] };
  if (!absent(tx.valid_until)) {
    const v = tx.valid_until;
    if (typeof v !== 'number' || !Number.isInteger(v) || v < 1 || v > 4294967295) return null;
    out.valid_until = v;
  }
  if (!walletTarget(tx, out)) return null;
  const messages = tx.messages;
  if (!Array.isArray(messages) || messages.length < 1 || messages.length > MAX_WALLET_MESSAGES) return null;
  let total = 0;
  for (const m of messages) {
    if (!isPlainObject(m) || !onlyKeys(m, ['address', 'amount', 'payload', 'stateInit'])) return null;
    const address = checkWalletDestination(m.address);
    if (address === null || typeof m.amount !== 'string' || !AMOUNT.test(m.amount)) return null;
    const message: WalletMessage = { address, amount: m.amount };
    for (const field of ['payload', 'stateInit'] as const) {
      if (absent(m[field])) continue;
      if (!base64(m[field], MAX_WALLET_BOC_LENGTH)) return null;
      message[field] = m[field] as string;
      total += (m[field] as string).length;
    }
    out.messages.push(message);
  }
  return total <= MAX_WALLET_BOC_TOTAL ? out : null;
}

/** Text the server and the bot can take: well-formed, no NUL, 1 to `max` UTF-8 bytes. */
const signText = (v: unknown, max: number): v is string => typeof v === 'string' && v.length > 0 && wellFormed(v) && !v.includes('\0') && utf8Length(v) <= max;

/**
 * A `signData` payload, or null: TON Connect's three kinds with their fields only, and optionally `network` and
 * `from` as a transaction's. `text`: 1–4096 UTF-8 bytes; `binary`: `bytes`, base64 of at most 4096 characters;
 * `cell`: `schema` (TL-B, 1–1024 UTF-8 bytes) and `cell`, base64 of at most 4096 characters. Absent and null fields
 * are left out. No domain: the client signs for the bot of the message (`bot<bot_id>`).
 */
export function checkSignDataPayload(payload: unknown): SignDataPayload | null {
  if (!isPlainObject(payload)) return null;
  const common = ['type', 'network', 'from'];
  let out: SignDataPayload;
  switch (payload.type) {
    case 'text':
      if (!onlyKeys(payload, [...common, 'text']) || !signText(payload.text, MAX_SIGN_DATA_LENGTH)) return null;
      out = { type: 'text', text: payload.text };
      break;
    case 'binary':
      if (!onlyKeys(payload, [...common, 'bytes']) || !base64(payload.bytes, MAX_SIGN_DATA_LENGTH)) return null;
      out = { type: 'binary', bytes: payload.bytes };
      break;
    case 'cell':
      if (!onlyKeys(payload, [...common, 'schema', 'cell']) || !signText(payload.schema, MAX_SIGN_DATA_SCHEMA_BYTES) || !base64(payload.cell, MAX_SIGN_DATA_LENGTH)) return null;
      out = { type: 'cell', schema: payload.schema, cell: payload.cell };
      break;
    default:
      return null;
  }
  return walletTarget(payload, out) ? out : null;
}

/**
 * The verdict on a request (docs/sdk-v1.md §2.14): `foreground` (it consumes the task's press) and `background`
 * go to the client; `held` (a background request from a widget off screen) waits in the glue and goes to the client
 * when the widget appears on screen (`RequestGate.show`); the rest are refused: `disabled` (no `canQuery`: a widget
 * sent without `allow_queries`, or a forwarded copy of one sent without `fwd_allow_queries`), `busy` (one request in
 * flight per widget, a held one included), `no-activation` (no press, and no `backgroundQuery`), `rate-limited` (a
 * background request sooner than 5 s after the last one, unless the widget appeared on screen since). A wallet call
 * (`decideWallet`), or a request that asks for the wallet, goes out only as `foreground` or `granted` (no press, but
 * within 10 s of the answer to a call made by a tap); `no-wallet` when the client has no wallet for this user.
 */
export type RequestVerdict = 'foreground' | 'background' | 'held' | 'granted' | 'disabled' | 'no-wallet' | 'busy' | 'no-activation' | 'rate-limited';

/** What the widget's promise rejects with for a refused request. */
export function requestRefusal(verdict: RequestVerdict): RequestError | null {
  switch (verdict) {
    case 'foreground': case 'background': case 'held': case 'granted': return null;
    case 'busy': return 'BUSY';
    case 'rate-limited': return 'RATE_LIMITED';
    default: return 'NOT_ALLOWED';
  }
}

/**
 * The client's sandbox policy for requests, from the server's flags only: on a tap whenever `canQuery`; without one
 * only with `backgroundQuery`: one when the widget appears on screen (`show`, and at creation), then at most one per
 * 5 s counted from the start of the last background request, while it stays visible; one made while it is off screen
 * is held (`held`) and is the one of its next appearance (`show` returns true: the glue sends it then). One request
 * in flight, a held one included. `gesture`: the task has a press no privileged call consumed yet (the glue consumes
 * it on `foreground`). `finish` when the answer or the failure has gone into the widget, or when the glue drops a
 * held request (the widget stopped); `answeredAt` when an answer with no error went in then.
 *
 * The wallet (docs/sdk-v1.md §2.18): only with `wallet` (the client has Gram Wallet for this user). A request that
 * asks for the wallet (`withWallet`) and a wallet call (`decideWallet`) need the visible widget and a press, or the
 * grant: the answer to the last call made by a press or a grant (a request or a wallet call) lets one wallet call
 * follow within 10 s, so a tap can pay for what the bot answers (tap → request → transaction). Any call that goes
 * out as `foreground` or `granted` uses up the grant; its answer brings the next one.
 */
export class RequestGate {
  private inFlight = false;
  private held = false;
  private lastBackground: number | null = null;
  /** The call in flight went out on a press or the grant: its answer grants one wallet call. */
  private granting = false;
  private grantAt: number | null = null;

  get busy(): boolean { return this.inFlight; }

  decide(now: number, s: { gesture: boolean; visible: boolean; canQuery: boolean; backgroundQuery: boolean; withWallet?: boolean; wallet?: boolean }): RequestVerdict {
    if (!s.canQuery) return 'disabled';
    if (s.withWallet && !s.wallet) return 'no-wallet';
    if (this.inFlight) return 'busy';
    if (s.gesture && s.visible) return this.out('foreground');
    if (!s.backgroundQuery || s.withWallet) return 'no-activation';
    if (!s.visible) {
      this.inFlight = this.held = true;
      return 'held';
    }
    if (this.lastBackground !== null && now - this.lastBackground < BACKGROUND_REQUEST_INTERVAL_MS) return 'rate-limited';
    this.lastBackground = now;
    this.inFlight = true;
    return 'background';
  }

  /** A wallet call (`sendTransaction`, `signData`): on a press, or on the grant within 10 s of its answer. */
  decideWallet(now: number, s: { gesture: boolean; visible: boolean; canQuery: boolean; wallet: boolean }): RequestVerdict {
    if (!s.canQuery) return 'disabled';
    if (!s.wallet) return 'no-wallet';
    if (this.inFlight) return 'busy';
    if (s.gesture && s.visible) return this.out('foreground');
    if (s.visible && this.grantAt !== null && now - this.grantAt <= WALLET_GRANT_MS) return this.out('granted');
    return 'no-activation';
  }

  private out(verdict: 'foreground' | 'granted'): RequestVerdict {
    this.inFlight = this.granting = true;
    this.grantAt = null;
    return verdict;
  }

  finish(answeredAt?: number): void {
    if (this.granting && answeredAt !== undefined) this.grantAt = answeredAt;
    this.inFlight = this.held = this.granting = false;
  }

  /**
   * The widget appeared on screen (it was hidden): its next background request may go out at once. True when a held
   * request goes out now, as this appearance's background request (the glue sends it; it stays in flight).
   */
  show(now: number): boolean {
    this.lastBackground = null;
    if (!this.held) return false;
    this.held = false;
    this.lastBackground = now;
    return true;
  }
}

/**
 * Haptics need a visible widget and room in the bucket (5 calls, refilled at 10 per second), and a touch: while a
 * finger is down on the widget and for 5 s after it is lifted they are free (under the rate limit); outside that, the
 * first call opens a series whose first second passes, then nothing until the next touch, or 10 s after the series
 * began when there was none (docs/sdk-v1.md §2.6; a widget cannot buzz the phone from a timer). `press` / `release`
 * are the host's trusted presses (the renderer's claim at a primary pointerdown and its release), never the widget's
 * events. `play` delivers; `drop` drops silently; `report` drops and reports `haptic-limit` (at most once per second).
 */
export class HapticGate {
  private tokens = HAPTIC_BURST;
  private last: number | null = null;
  private reported: number | null = null;
  /** A press is on since then. */
  private down: number | null = null;
  /** The last press ended then. */
  private lifted: number | null = null;
  /** The last series (haptics outside a touch) began then. */
  private series: number | null = null;

  /** A finger or a mouse button went down on the widget (trusted input only). */
  press(now: number) {
    this.down = now;
  }

  /** The press ended: the finger was lifted or the press was cancelled. */
  release(now: number) {
    this.down = null;
    this.lifted = now;
  }

  decide(now: number, visible: boolean): 'play' | 'drop' | 'report' {
    if (this.last !== null) this.tokens = Math.min(HAPTIC_BURST, this.tokens + (Math.max(0, now - this.last) * HAPTIC_PER_SECOND) / 1000);
    this.last = now;
    if (this.down !== null && now - this.down > HAPTIC_PRESS_MAX_MS) this.release(this.down + HAPTIC_PRESS_MAX_MS);
    const touching = this.down !== null || (this.lifted !== null && now - this.lifted <= HAPTIC_TOUCH_WINDOW_MS);
    let allowed = visible;
    if (allowed && !touching && !(this.series !== null && now - this.series < HAPTIC_SERIES_MS)) {
      // A new series: after a touch at any time (one per touch), else 10 s after the last one began.
      const touchedSince = this.lifted !== null && (this.series === null || this.lifted > this.series);
      if (this.series === null || touchedSince || now - this.series >= HAPTIC_SERIES_GAP_MS) this.series = now;
      else allowed = false;
    }
    if (allowed && this.tokens >= 1) {
      this.tokens -= 1;
      return 'play';
    }
    if (this.reported !== null && now - this.reported < HAPTIC_REPORT_MS) return 'drop';
    this.reported = now;
    return 'report';
  }
}

/** The theme keys of `params` whose values are `#rrggbb` (lowercased); everything else is dropped. */
export function checkThemeParams(params: unknown): Partial<ThemeParams> {
  const out: Partial<ThemeParams> = {};
  if (typeof params !== 'object' || params === null || Array.isArray(params)) return out;
  for (const key of THEME_KEYS) {
    const v = (params as Record<string, unknown>)[key];
    if (typeof v === 'string' && /^#[0-9a-fA-F]{6}$/.test(v)) out[key] = v.toLowerCase();
  }
  return out;
}

/** A BCP 47 tag of at most 35 characters (`ru`, `en-US`, `pt-BR`, `zh-Hant-TW`), or null. */
export function checkLocale(locale: unknown): string | null {
  if (typeof locale !== 'string' || locale.length === 0 || locale.length > MAX_LOCALE_LENGTH) return null;
  return /^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$/.test(locale) ? locale : null;
}

/** UTF-8 bytes of an area's keys and values. */
export function storageBytes(area: Record<string, string>): number {
  let n = 0;
  for (const k of Object.keys(area)) n += utf8Length(k) + utf8Length(area[k]);
  return n;
}

/** An object of strings within the quota, or null. */
export function checkStorageArea(area: unknown): Record<string, string> | null {
  if (typeof area !== 'object' || area === null || Array.isArray(area)) return null;
  const out: Record<string, string> = {};
  for (const k of Object.keys(area)) {
    const v = (area as Record<string, unknown>)[k];
    if (typeof v !== 'string') return null;
    // An own property even for `__proto__` (an assignment would hit the prototype setter and lose the key).
    Object.defineProperty(out, k, { value: v, enumerable: true, writable: true, configurable: true });
  }
  return storageBytes(out) <= STORAGE_QUOTA_BYTES ? out : null;
}

export type StorageCommit = { local?: Record<string, string>; session?: Record<string, string> };

/** A commit (or a preload) of `local` and/or `session`, each checked whole; null when anything is wrong (`storage-rejected`). */
export function checkStorageCommit(commit: unknown): StorageCommit | null {
  if (typeof commit !== 'object' || commit === null || Array.isArray(commit)) return null;
  const out: StorageCommit = {};
  for (const k of Object.keys(commit)) {
    if (k !== 'local' && k !== 'session') return null;
    const area = checkStorageArea((commit as Record<string, unknown>)[k]);
    if (area === null) return null;
    out[k] = area;
  }
  return out;
}

// ── dev tooling (TS only: the dev app's mock bot and the harness) ──

/**
 * The server's flags for each kind of widget message (the server's matrix, docs/tgw-format.md "Who may request"; the
 * originals and live copies of a widget sent with `allow_queries`): what a client passes as `WidgetContext` for it. Dev tooling only: a client reads the flags from the message.
 */
export const WIDGET_MESSAGE_KINDS = {
  /** The original in the private chat with the bot that sent it. */
  private: { isCopy: false, canQuery: true, backgroundQuery: true, chatType: 'private' },
  /** An original the bot sent to a group (or a supergroup) it is a member of. */
  group: { isCopy: false, canQuery: true, backgroundQuery: false, chatType: 'group' },
  /** An original sent via the bot (inline mode), here in a private chat between two users. */
  'via-bot': { isCopy: false, canQuery: true, backgroundQuery: false, chatType: 'private' },
  /** An original the bot sent without `allow_queries` (here in its private chat): no requests anywhere, no network marker. */
  'no-queries': { isCopy: false, canQuery: false, backgroundQuery: false, chatType: 'private' },
  /** A forwarded copy of a widget the bot sent with `fwd_allow_queries` (and so `allow_queries`). */
  'live-copy': { isCopy: true, canQuery: true, backgroundQuery: false, chatType: 'private' },
  /** A forwarded copy of a widget sent without `fwd_allow_queries`. */
  copy: { isCopy: true, canQuery: false, backgroundQuery: false, chatType: 'private' },
  /** An inline result in the list, before it is sent: data, no input, nothing about the user. */
  preview: { isCopy: false, canQuery: false, backgroundQuery: false, chatType: 'private', preview: true },
} as const satisfies Record<string, Required<Omit<WidgetContext, 'viewer' | 'preview' | 'wallet'>> & { preview?: boolean }>;
export type WidgetMessageKind = keyof typeof WIDGET_MESSAGE_KINDS;

/**
 * A viewer written as `id,first,last,username,premium` (the harness's `--viewer`, the dev app's `?viewer=`): empty
 * parts are absent, `premium` is any of `1`, `true`, `premium`; no positive id → null. Unchecked: the host checks it.
 */
export function viewerFromList(list: string): WidgetViewer | null {
  const [id, firstName, lastName, username, premium] = list.split(',').map((s) => s.trim());
  if (!id || !(Number(id) > 0)) return null;
  return {
    id: Number(id),
    ...(firstName ? { firstName } : {}), ...(lastName ? { lastName } : {}), ...(username ? { username } : {}),
    ...(premium ? { isPremium: ['1', 'true', 'premium'].includes(premium) } : {}),
  };
}

// ── theme defaults and renderer variables (TS only: the shim and the renderer core) ──

/** What a key the client did not pass falls back to. Clients pass every key (docs/sdk-v1.md §5.1). */
export const DEFAULT_THEME_PARAMS: Record<'light' | 'dark', ThemeParams> = {
  light: {
    bg_color: '#ffffff', text_color: '#000000', hint_color: '#707579', link_color: '#3390ec',
    button_color: '#3390ec', button_text_color: '#ffffff', secondary_bg_color: '#f4f4f5',
    accent_text_color: '#3390ec', subtitle_text_color: '#707579', destructive_text_color: '#e53935',
    section_bg_color: '#ffffff', section_header_text_color: '#3390ec', section_separator_color: '#e7e7e7',
    header_bg_color: '#ffffff', bottom_bar_bg_color: '#ffffff',
  },
  dark: {
    bg_color: '#212121', text_color: '#ffffff', hint_color: '#aaaaaa', link_color: '#8774e1',
    button_color: '#8774e1', button_text_color: '#ffffff', secondary_bg_color: '#181818',
    accent_text_color: '#8774e1', subtitle_text_color: '#aaaaaa', destructive_text_color: '#ff595a',
    section_bg_color: '#212121', section_header_text_color: '#8774e1', section_separator_color: '#3d3d3d',
    header_bg_color: '#212121', bottom_bar_bg_color: '#212121',
  },
};

export function completeThemeParams(params: unknown, scheme: 'light' | 'dark'): ThemeParams {
  return { ...DEFAULT_THEME_PARAMS[scheme], ...checkThemeParams(params) };
}

/** The client's own `<button>` (docs/sdk-v1.md §2.4); every field optional, the template's defaults stand for the rest. */
export interface ButtonTokens {
  bg?: string;
  text?: string;
  radius?: string;
  font?: string;
  padding?: string;
  height?: string;
  pressedOpacity?: number | string;
  disabledOpacity?: number | string;
}

/**
 * The client's message text (docs/sdk-v1.md §2.4): the root's font size (the chat's text-size setting, in CSS px),
 * its line height (unitless) and the padding between the bubble's edge and the widget's content; every field
 * optional, the reference set (`DEFAULT_TEXT_TOKENS`) stands for the rest.
 */
export interface TextTokens {
  fontSize?: number | string;
  lineHeight?: number | string;
  padding?: string;
}

/** The reference text tokens: what a client on defaults gets, and what every reference picture is drawn with. */
export const DEFAULT_TEXT_TOKENS = { fontSize: '15px', lineHeight: '1.35', padding: '8px 12px' } as const;

/** What the host passes to the renderer: the colour scheme, the palette, the button and text tokens. */
export interface WidgetTheme {
  colorScheme: 'light' | 'dark';
  params?: Partial<ThemeParams>;
  button?: ButtonTokens;
  text?: TextTokens;
}

const LENGTH = /^(0|[0-9]{1,4}(\.[0-9]{1,3})?(px|em|rem))$/;
const COLOR = /^#[0-9a-fA-F]{6}$/;
const GENERIC = new Set(['system-ui', 'sans-serif', 'serif', 'monospace', 'ui-sans-serif', 'ui-serif', 'ui-monospace', 'ui-rounded']);
const FONT = /^(?:(normal|italic) )?(?:(normal|bold|[1-9]00) )?[0-9]{1,3}(?:\.[0-9]{1,2})?(?:px|em|rem)(?:\/[0-9]{1,3}(?:\.[0-9]{1,2})?(?:px|em|rem)?)? ([a-z-]+(?:, ?[a-z-]+)*)$/;

function opacity(v: unknown): string | null {
  const s = typeof v === 'number' ? String(v) : typeof v === 'string' ? v : '';
  return /^(0(\.[0-9]{1,3})?|1(\.0{1,3})?|\.[0-9]{1,3})$/.test(s) ? s : null;
}

const padding = (v: unknown) => (typeof v === 'string' && /^\S+( \S+){0,3}$/.test(v) && v.split(' ').every((p) => LENGTH.test(p)) ? v : null);

/** A number in [min, max] with at most `decimals` decimals, as a number or its plain decimal string (+ `unit`); shortest form. */
function bounded(v: unknown, min: number, max: number, decimals: number, unit: string): string | null {
  let n: number;
  if (typeof v === 'number') n = v;
  else if (typeof v === 'string' && new RegExp(`^[0-9]{1,2}(\\.[0-9]{1,${decimals}})?${unit}$`).test(v)) n = parseFloat(v);
  else return null;
  if (!Number.isFinite(n) || n < min || n > max) return null;
  const f = 10 ** decimals;
  return String(Math.round(n * f) / f) + unit;
}

const BUTTON_VARS: [keyof ButtonTokens, string, (v: unknown) => string | null][] = [
  ['bg', '--tg-button-bg', (v) => (typeof v === 'string' && COLOR.test(v) ? v.toLowerCase() : null)],
  ['text', '--tg-button-text', (v) => (typeof v === 'string' && COLOR.test(v) ? v.toLowerCase() : null)],
  ['radius', '--tg-button-radius', (v) => (typeof v === 'string' && LENGTH.test(v) ? v : null)],
  ['font', '--tg-button-font', (v) => {
    const m = typeof v === 'string' ? FONT.exec(v) : null;
    return m && m[3].split(',').every((f) => GENERIC.has(f.trim())) ? (v as string) : null;
  }],
  ['padding', '--tg-button-padding', padding],
  ['height', '--tg-button-height', (v) => (typeof v === 'string' && LENGTH.test(v) ? v : null)],
  ['pressedOpacity', '--tg-button-pressed-opacity', opacity],
  ['disabledOpacity', '--tg-button-disabled-opacity', opacity],
];

/** Text tokens: font size 8–64 px (a number or `"15px"`), line height 1–3 (unitless), padding as the button's. */
const TEXT_VARS: [keyof TextTokens, string, (v: unknown) => string | null][] = [
  ['fontSize', '--tg-text-font-size', (v) => bounded(v, 8, 64, 2, 'px')],
  ['lineHeight', '--tg-text-line-height', (v) => bounded(v, 1, 3, 3, '')],
  ['padding', '--tg-text-padding', padding],
];

// ── what the client draws over the widget (docs/sdk-v1.md §2.17) ──

/**
 * The client's chrome over the widget, in logical px (docs/sdk-v1.md §2.17): a widget with no caption below it gets the
 * message time and status drawn over its bottom corner, as on a photo. `bottom` is the height of the time's band;
 * `cornerLeft` / `cornerRight` the width of the client's corner in that band, on a physical side (right in a
 * left-to-right interface, left in a right-to-left one, the other 0); `top`, `left` and `right` are 0. All 0 with a
 * caption below it (the time is in the caption's line; a caption shown above it, `invert_media`, is as none) and on a
 * client that draws nothing over the widget.
 */
export interface ContentSafeArea {
  top: number;
  right: number;
  bottom: number;
  left: number;
  cornerLeft: number;
  cornerRight: number;
}
export const CONTENT_SAFE_AREA_FIELDS = ['top', 'right', 'bottom', 'left', 'cornerLeft', 'cornerRight'] as const;
/** The largest inset (`top`, `right`, `bottom`, `left`) and corner a client may pass; a value outside 0…max is 0. */
export const MAX_SAFE_AREA_INSET = 64;
export const MAX_SAFE_AREA_CORNER = 200;
/** No chrome over the widget: what every host has until the client passes its own. */
export const NO_CONTENT_SAFE_AREA: Readonly<ContentSafeArea> = Object.freeze({ top: 0, right: 0, bottom: 0, left: 0, cornerLeft: 0, cornerRight: 0 });

/** Every field a finite number of logical px in its range (insets 0–64, corners 0–200), at most two decimals; anything else is 0. */
export function checkContentSafeArea(area: unknown): ContentSafeArea {
  const a = (typeof area === 'object' && area !== null ? area : {}) as Record<string, unknown>;
  const out = { ...NO_CONTENT_SAFE_AREA };
  for (const field of CONTENT_SAFE_AREA_FIELDS) {
    const v = a[field];
    const max = field.startsWith('corner') ? MAX_SAFE_AREA_CORNER : MAX_SAFE_AREA_INSET;
    if (typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= max) out[field] = Math.round(v * 100) / 100;
  }
  return out;
}

/** The root variables of the content safe area, by field. */
const SAFE_AREA_VARS: [keyof ContentSafeArea, string][] = [
  ['top', '--tg-content-safe-area-inset-top'],
  ['right', '--tg-content-safe-area-inset-right'],
  ['bottom', '--tg-content-safe-area-inset-bottom'],
  ['left', '--tg-content-safe-area-inset-left'],
  ['cornerLeft', '--tg-content-safe-area-corner-left'],
  ['cornerRight', '--tg-content-safe-area-corner-right'],
];

/**
 * The `env()` names the validator rewrites to the root variable the renderer sets (docs/sdk-v1.md §2.17):
 * `env(safe-area-inset-bottom, 8px)` → `var(--tg-content-safe-area-inset-bottom, 8px)`. Exact names, as in CSS.
 */
export const SAFE_AREA_ENV_VARIABLES: Readonly<Record<string, string>> = Object.freeze({
  'safe-area-inset-top': '--tg-content-safe-area-inset-top',
  'safe-area-inset-right': '--tg-content-safe-area-inset-right',
  'safe-area-inset-bottom': '--tg-content-safe-area-inset-bottom',
  'safe-area-inset-left': '--tg-content-safe-area-inset-left',
});

/** The CSS variables of a content safe area (checked; every one always set, `0px` for none). */
export function contentSafeAreaVariables(area: unknown): Record<string, string> {
  const a = checkContentSafeArea(area);
  const vars: Record<string, string> = {};
  for (const [field, name] of SAFE_AREA_VARS) vars[name] = a[field] + 'px';
  return vars;
}

/** Every variable name the renderer may set on the widget's root: the theme's and the content safe area's. */
export const THEME_VARIABLES: readonly string[] = [
  ...THEME_KEYS.map((k) => '--tg-theme-' + k.replace(/_/g, '-')),
  '--tg-color-scheme',
  ...BUTTON_VARS.map(([, name]) => name),
  ...TEXT_VARS.map(([, name]) => name),
  ...SAFE_AREA_VARS.map(([, name]) => name),
];

/**
 * The CSS variables of a theme (docs/sdk-v1.md §2.3, §2.4, §3.4): names from the fixed list, values that match their
 * grammar. The palette and the text tokens are always complete (the scheme's defaults, `DEFAULT_TEXT_TOKENS`); a
 * button token is set only when the client passed it (the template's default stands for it).
 */
export function themeVariables(theme: unknown): Record<string, string> {
  const t = (typeof theme === 'object' && theme !== null ? theme : {}) as Record<string, unknown>;
  const scheme = t.colorScheme === 'dark' ? 'dark' : 'light';
  const params = completeThemeParams(t.params, scheme);
  const vars: Record<string, string> = {};
  for (const k of THEME_KEYS) vars['--tg-theme-' + k.replace(/_/g, '-')] = params[k];
  vars['--tg-color-scheme'] = scheme;
  const button = (typeof t.button === 'object' && t.button !== null ? t.button : {}) as Record<string, unknown>;
  for (const [field, name, check] of BUTTON_VARS) {
    const v = check(button[field]);
    if (v !== null) vars[name] = v;
  }
  const text = (typeof t.text === 'object' && t.text !== null ? t.text : {}) as Record<string, unknown>;
  for (const [field, name, check] of TEXT_VARS) vars[name] = check(text[field]) ?? DEFAULT_TEXT_TOKENS[field];
  return vars;
}

/** A host's `theme` option: a plain scheme (the API before v1) or a WidgetTheme, with its palette checked. */
export function toWidgetTheme(theme: unknown): WidgetTheme & { params: Partial<ThemeParams> } {
  if (theme === 'light' || theme === 'dark') return { colorScheme: theme, params: {} };
  const t = (typeof theme === 'object' && theme !== null ? theme : {}) as Record<string, unknown>;
  const button = typeof t.button === 'object' && t.button !== null ? (t.button as ButtonTokens) : undefined;
  const text = typeof t.text === 'object' && t.text !== null ? (t.text as TextTokens) : undefined;
  return { colorScheme: t.colorScheme === 'dark' ? 'dark' : 'light', params: checkThemeParams(t.params), ...(button ? { button } : {}), ...(text ? { text } : {}) };
}
