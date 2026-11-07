# SDK v1: the widget contract

Status: contract for the framework and the client teams, 2026-10-16; its open
questions were decided on 2026-10-17 (§6), and the framework implements it
(v0.1.0, 2026-10-17: the shim, all five glue copies and the Node harness, §7). It fixes
what a widget can call, what travels between sandbox, host and renderer, what
every host exposes and what every client implements, so that client teams can
build their side in parallel with the PoC. Decisions behind it: release plan
D4, D7a, D8, D9, D12, D14, D17, D18, D19, D22, D23, and the decisions of
2026-10-17 (no keyboard, no form controls, a client-styled `<button>`,
`docs/decisions.md`, "Profile v1").

What the framework did before v1 is noted where a client may meet it
(**before v1**: the API of the profile-0 builds).

**The widget's state and requests** (2026-10-25, widget interactivity, decision I1
in `docs/decisions.md`, "The bot's data and requests"; the server's side in
`docs/tgw-format.md`, "The widget's state and requests"): a widget is fixed code
plus changeable state. The code, its HTML, is set when the bot sends the widget
and never changes; the state changes, and only the bot writes it: the **chat
state** (`chatData`: this message in its chat, never forwarded) and the **shared
state** (`publicData`: wherever the widget is shown; a forward gets a copy, or
shares it with the original). The widget reads its state and cannot change it
itself: it can send the bot a request, a callback button inside the widget, and
gets the bot's `result`; the server's flags on the message say where it may ask
(on a tap, or in the background too); `copyText` copies a text on a tap
(§2.13–§2.15).
They are part of SDK v1 and of layer 233 from the launch. A widget in an
inline-results list, before it is sent, runs as a preview (§2.16).

Related documents, not repeated here: `docs/integration.md` (embedding the
host on each platform), `docs/tgw-format.md` (the widget payload: mini-HTML),
`docs/protocol.md` (the full wire protocol), `docs/changes.md`
(per-release notes).

## 1. Who does what

| | the framework (widget-sandbox) | the client |
|---|---|---|
| widget API | `TelegramWidget`, Web Storage, `CSS.supports`, `navigator.language`, `visibilitychange`, the inert stubs | — |
| checks | argument schemas, user activation, rate limits, storage quota, profile tables, in all five glue copies | — |
| theme | CSS variables on the renderer root, `themeChanged`, the defaults of the root, the text and `<button>` (the UA layer) | maps the bubble's colours to `themeParams`, its button and message text to the button and text tokens, calls `setTheme` |
| links | validates the URL, lets one call through per pointer press | `openLink`: shows the confirmation with the full URL, opens it; `openTelegramLink`: opens it as a `t.me` link in a message, no confirmation of its own |
| haptics | validates, rate-limits, drops calls from hidden widgets | plays them (or ignores them on desktop and web) |
| storage | preload at start, per-task commits, the quota | persists per message, deletes with the message, chat, account, "Clear cache" |
| gestures | reports per press what the widget claims with `touch-action` (horizontal drags, holds); the shared policy (thresholds, who wins); `dblclick` for every double tap, `contextmenu` for claimed holds; on the web, plays the bubble itself | drives its recognizers from the policy: menu and swipe-to-reply unless the widget claims the press, and then cancels the widget's touch; the list scroll always; no double-tap reaction over the widget (v0.1.2; vertical drags the chat's since v0.1.3, §5.5) |
| size | reports the content height and the widget's resize anchor (`setResizeAnchor`, v0.1.3) | sizes the bubble from the bot's hint before load, caps it, clips; keeps the anchored edge in place when the height changes (§5.6) |
| failures | freezes the last frame with a badge and reports `dead` | rebuilds it after a crash; after 2 crashes in a row leaves it stopped |
| the widget's state | `chatData` (the chat state), `publicData` (the shared state), `chatDataChanged`, `publicDataChanged` in the widget; the limits; a copy without chat state | passes the message's state at creation and the full state again on every change (`setData`): the server's update, and the shared state it polls (§5.11) |
| where the widget is | `isForward`, `chatType`, `viewer` in the widget; a copy is told nothing about the chat it is in (no `chatType`); the widget knows its `viewer` only where it may send requests | passes the server's flags (`isCopy`, `canQuery`, `backgroundQuery`), the chat type and the viewer at creation (`context`) |
| requests | the limits of `data`, the server's flags, one tap per request or the background rules, one in flight, the answer's limits | sends each request like a callback button's press, hands back the bot's `result` or the failure (§5.11) |
| copyText | the text's limits, one tap per call | copies the text and shows a toast that says what was copied |

## 2. Widget-facing API

Everything below is what widget code sees inside the sandbox. The object is
`window.TelegramWidget`; member names follow `Telegram.WebApp` (Mini Apps)
where one exists, so that code and models that know Mini Apps carry over,
and so that a JSON-RPC facade for MCP Apps (`ui/open-link`,
`ui/notifications/host-context-changed`, `ui/notifications/initialized`) can be
laid over the same calls later (D19).

### 2.1 Members

| member | type | note |
|---|---|---|
| `profile` | `number`, read-only | |
| `isProfileAtLeast(n)` | `(n: number) => boolean` | |
| `colorScheme` | `'light' \| 'dark'`, read-only | before v1: `theme` |
| `themeParams` | `ThemeParams`, read-only, a fresh frozen object per change | |
| `isActive` | `boolean`, read-only (`!document.hidden`) | |
| `contentSafeAreaInset` | `{ top, right, bottom, left, cornerLeft, cornerRight }` in CSS px, read-only, a fresh frozen object per change | §2.17: the client's time over the bottom corner of a widget with no caption below it; detect it with `TelegramWidget.contentSafeAreaInset !== undefined` |
| `onEvent(type, handler)`, `offEvent(type, handler)` | `type`: `'themeChanged' \| 'activated' \| 'deactivated' \| 'chatDataChanged' \| 'publicDataChanged' \| 'contentSafeAreaChanged'` (`chatDataChanged`, `publicDataChanged`: §2.13; `contentSafeAreaChanged`: §2.17); the handler is called with `this` = `TelegramWidget` and no arguments | before v1: `on(event, cb)`, reserved; removed |
| `openLink(url)` | `(url: string) => void` | before v1 without the checks below |
| `openTelegramLink(url)` | `(url: string) => void` | |
| `HapticFeedback.impactOccurred(style)` | `style`: `'light' \| 'medium' \| 'heavy' \| 'rigid' \| 'soft'` | |
| `HapticFeedback.notificationOccurred(type)` | `type`: `'error' \| 'success' \| 'warning'` | |
| `HapticFeedback.selectionChanged()` | `() => HapticFeedback` | |
| `ready()` | `() => void` | |
| `setResizeAnchor(edge)` | `(edge: 'top' \| 'bottom') => void` | v0.1.3 (§2.12); detect it with `typeof TelegramWidget.setResizeAnchor === 'function'` |
| `watchPublicData()`, `unwatchPublicData()` | `() => void` | §2.13: follow the shared state live (the client polls it only for widgets that watch) and stop; no tap needed |
| `chatData`, `publicData` | `string \| null`, read-only | §2.13: the widget's chat state and shared state, which only the bot writes; `chatData` is null in a copy |
| `isForward` | `boolean`, read-only | §2.13: this message is a forwarded copy |
| `chatType` | `'private' \| 'group' \| 'supergroup' \| 'channel' \| null`, read-only | §2.13: null in a copy (it is told nothing about the chat it is in) and in a preview, or when the client does not say |
| `viewer` | `{ id, first_name?, last_name?, username?, is_premium? } \| null` (Mini Apps' `WebAppUser`), read-only, frozen | §2.13: the user looking at the widget; null wherever the widget may not send requests (no `canQuery`), a preview included |
| `isPreview` | `boolean`, read-only | §2.16: an inline result shown before it is sent |
| `request(data, options?)` | `(data: string, options?: { wallet?: boolean }) => Promise<{ result: string \| null }>` | §2.14; `{ wallet: true }`: §2.18 |
| `wallet` | `{ sendTransaction(transaction, data), signData(payload, data) } \| null`, read-only, frozen | §2.18: Gram Wallet; null where the client has no wallet for this user, where the widget may not ask the bot, in a preview |
| `copyText(text)` | `(text: string) => void` | §2.15 |
| `theme`, `onTheme(cb)` | aliases of `colorScheme` and `onEvent('themeChanged')` (the callback gets the colour scheme, at once and on every change) | deprecated |
| `sendMessage` | `typeof TelegramWidget.sendMessage === 'undefined'` | removed |

All methods are synchronous and return `undefined`, except the three haptic
methods, which return `TelegramWidget.HapticFeedback` for chaining, as in Mini
Apps, `isProfileAtLeast`, and `request` and the two wallet calls, which return a promise of the
bot's answer. No other call gets an answer from the client: whether the user
confirmed a link is not reported back.

### 2.2 Profile detection (D23)

- `TelegramWidget.profile` is the integer version of the widget profile the
  client's framework build enforces (`profile` in `VERSION.json`, stamped into
  the bundles at build time; profile v1 is `1`). It is a property of the
  framework build, not of the client app: clients do not set it.
- `TelegramWidget.isProfileAtLeast(n)` is `profile >= n`; a non-number `n` gives
  `false`.
- `CSS.supports(property, value)` and `CSS.supports(conditionText)` answer from
  the profile tables compiled into the sandbox bundle: `true` only when the
  validator would keep that declaration (one evaluator for both,
  `packages/core/src/shared/css-supports.ts`).
- `@supports` rules are evaluated by the validator against the same tables:
  the block is kept or dropped before the CSS reaches the renderer, so the
  renderer's engine never decides.
- A widget that checks nothing still loads; on an older client the features
  outside that client's profile are dropped by its validator (accepted product
  behaviour). Clients below API layer 233 receive `messageMediaUnsupported`
  with the caption and show their usual placeholder; a client with widget
  rendering off does the same (`docs/tgw-format.md`).

### 2.3 Theme (D14)

`TelegramWidget.themeParams` carries the chat's palette as `#rrggbb` strings
(lowercase), with the keys of Mini Apps' `themeParams`:

| key | meaning inside a bubble |
|---|---|
| `bg_color` | the colour of this bubble, behind the widget: an incoming bubble's, or an outgoing bubble's when the user sent or forwarded the widget (§5.1). The client draws it under the transparent renderer: it is the widget's canvas (§2.4) |
| `text_color` | message text |
| `hint_color` | secondary text: time, meta |
| `link_color` | links in messages |
| `button_color`, `button_text_color` | the client's primary button; also the default `<button>` look (§2.4) |
| `secondary_bg_color` | a second surface on the bubble (quote, inline-button background) |
| `accent_text_color` | accent text |
| `subtitle_text_color` | subtitles |
| `destructive_text_color` | destructive actions |
| `section_bg_color`, `section_header_text_color`, `section_separator_color`, `header_bg_color`, `bottom_bar_bg_color` | no bubble counterpart; filled by the client from the closest colour so that Mini Apps code works (`section_separator_color` draws the lines of a table and `<hr>`, §2.4) |

The palette is the bubble's: a widget in an outgoing bubble gets that bubble's
background, text, hint, link and separator colours, as the client draws a
message there. Every key is always present. The same values are CSS custom properties on the
widget's root element: `--tg-theme-bg-color`, `--tg-theme-text-color`, … (the
key with `_` turned into `-`), plus `--tg-color-scheme: light | dark`. The host
sets them in the renderer; a widget cannot overwrite the root's values, only
shadow them on its own elements. `prefers-color-scheme` media queries and
`color-scheme: light dark` follow the chat's scheme. Keys the client leaves
out take the framework's defaults for the scheme (`DEFAULT_THEME_PARAMS` in
`packages/core/src/shared/sdk-policy.ts`, Web K-like); clients pass every key.

A theme change (the user switches theme, the chat's theme changes, night mode)
updates `colorScheme`, `themeParams` and the variables in one task, then fires
`themeChanged` (and the legacy `onTheme` callbacks). The renderer sets the
variables inline and `!important` on the root, so neither a widget stylesheet
(`:root { … !important }`, any specificity) nor a style the widget sets on its
own root element replaces them.

### 2.4 The renderer's defaults: the root, the text, `<button>`

Every renderer inserts one user-agent-level stylesheet built from one template
in `packages/core` (`uaStylesheet` in `packages/core/profile.json`, printed in
`docs/profile-v1.md`): `@layer tw-ua` declared first, every selector inside
`:where()`, so any rule of the widget wins over it, whatever its specificity.
A widget without CSS therefore looks the same on every client and like the
client's own messages; the engine's own stylesheet (Times 16 px and an 8 px
body margin in a WebView, litehtml's master CSS) never shows through. Since
v0.1.1 the layer covers the root and the text elements, not only `<button>`: a
clarification of profile 1, not profile 2 (`docs/decisions.md`, "The
renderer's defaults").

**The root.** `html`: text `--tg-theme-text-color`, font
`var(--tg-text-font-size)/var(--tg-text-line-height) system-ui, sans-serif`,
no text-size adjustment, long words wrap, text not selectable (below). It has
no background of its own: the renderer is transparent and the bubble the
client draws under it, in `bg_color`, is the canvas. A background the widget
sets on its root or on its body paints the whole bubble, as in a browser (root
background propagation: the shadow-DOM renderer paints it on its host
element). `body`: the padding token as its margin
(`margin: var(--tg-text-padding)`), no padding; the first child's top margin
and the last child's bottom margin are dropped, so text starts at the token's
inset as in a message. A widget that writes `body { margin: 0 }` goes edge to
edge, as authors expect from a browser. The content height the renderer
reports, and so the bubble's height, includes the body's margins.

**Text selection** (D18 since v0.1.1). Text in a widget is not selectable by
default: the layer sets `user-select: none` on the root. A widget makes text
selectable with `user-select: text` (or `-webkit-user-select: text`) on an
element; its children follow (`auto`), and `none` turns it off again below.
`user-select: all` and `contain`, `::selection` and editing stay outside the
profile. The widget cannot read or change the selection (`getSelection()` is a
stub); the user selects and copies with the client's own gestures and copy
command (§5.10). A renderer that cannot select text yet keeps it
unselectable: the widget still works (an accepted exception to "every client
draws it", like `:hover`).

**The text elements**, in `em` of the parent's size, so they follow the
client's text size:

| element | default |
|---|---|
| `h1` … `h6` | 1.6, 1.35, 1.2, 1.07, 1, 0.87 em; weight 600; line height 1.25; 0.75 em above, 0.35 em below |
| `p`, `ul`, `ol`, `menu`, `dl`, `pre`, `blockquote`, `figure`, `table` | 0.6 em above and below |
| `ul`, `ol`, `menu` | 1.5 em of start padding; disc, decimal; a list inside a list item: circle, no margins |
| `dt`, `dd` | weight 600; 1.5 em of start margin |
| `table` | collapsed borders, no spacing; cells 0.3 em × 0.6 em of padding, start-aligned, top, a 1 px `--tg-theme-section-separator-color` line under each; `th` 600; no outer padding on the first and last cell of a row, so the text lines up with the paragraphs; `caption` in the hint colour, 0.87 em |
| `hr` | one 1 px line in `--tg-theme-section-separator-color`, 0.6 em margins |
| `pre`, `code`, `kbd`, `samp` | `ui-monospace, monospace` at 0.9 em (once: `code` inside `pre` keeps the size); `pre` wraps (`pre-wrap`, the bubble does not scroll) on a translucent grey, 0.5 em × 0.7 em of padding, 6 px radius |
| `blockquote` | no indent; a 3 px bar in `--tg-theme-accent-text-color` and 0.75 em before the text |
| `figure`, `figcaption` | no indent; `figcaption` in `--tg-theme-hint-color` at 0.87 em |
| `a[href]` | `--tg-theme-link-color`, not underlined |
| `b`, `strong`; `i`, `em`, `cite`, `dfn`, `var`; `small`; `sub`, `sup`; `mark` | 600; italic; 0.85 em; 0.75 em, shifted without changing the line height; a translucent yellow |
| `img` | at most the bubble's width (`max-width: 100%`; the `width`/`height` attributes still apply) |

Every property that WebKit's, Chromium's or litehtml's own stylesheet sets
differently is set here, so the three draw the same. RTL widgets get the list,
`dd`, table and quote indents on the right (logical properties after the
physical ones, which litehtml keeps).

**The text tokens.** The client passes them in the theme (`WidgetTheme.text`,
§4); the renderer checks each one and always sets the three variables on the
root, with the reference value for a token it did not get or refused:

| token (`text.…`) | variable | grammar | reference value |
|---|---|---|---|
| `fontSize` | `--tg-text-font-size` | CSS px (= points, dp) 8–64: a number, or a string `"15px"` | `15px` |
| `lineHeight` | `--tg-text-line-height` | a unitless number 1–3 | `1.35` |
| `padding` | `--tg-text-padding` (the body's margin) | one to four CSS lengths in `px`, `em`, `rem` | `8px 12px` (vertical, horizontal) |

`fontSize` is the chat's message text size: it follows the client's text-size
setting, and the client calls `setTheme` when it changes. A widget may use the
variables in its own CSS (`calc(var(--tg-text-font-size) * 1.2)`). Every
reference picture (the conformance set, the corpus reference mode) is drawn
with the reference set and the default palettes
(`packages/core/src/shared/reference.ts`).

**The `<button>`.** A `<button>` the widget did not style is drawn in the
client's own button style; any author CSS overrides it (`docs/decisions.md`,
"Profile v1"). The template fixes the states: base, `:active` (not disabled),
`[disabled]`; no hover state, no focus ring.

The client passes its tokens in the theme (`WidgetTheme.button`, §4); the
renderer sets each one it gets as the variable on the root and checks its
grammar (`themeVariables`): colours `#rrggbb`, lengths in `px`/`em`/`rem`, a
font shorthand naming generic families only, opacities 0–1. A token the client
does not pass keeps the template's default:

| token (`button.…`) | variable | the template's default |
|---|---|---|
| `bg` | `--tg-button-bg` | `rgba(51,144,236,.12)`: a tint, so that a widget that sets only its own `color` or only its own `background` stays readable (`docs/decisions.md`, "Profile v1") |
| `text` | `--tg-button-text` | `inherit` |
| `radius` | `--tg-button-radius` | `8px` |
| `font` | `--tg-button-font` | `500 14px/1.2 system-ui,sans-serif` (D3b) |
| `padding` | `--tg-button-padding` | `8px 14px` |
| `height` | `--tg-button-height` (minimum height) | `auto` |
| `pressedOpacity`, `disabledOpacity` | `--tg-button-pressed-opacity`, `--tg-button-disabled-opacity` | `.7`, `.5` |

A client whose buttons are solid (`bg` = its button colour, `text` = white)
accepts that a widget overriding only its button's `background` keeps the
client's text colour on it.

Other form controls are outside the profile and dropped by the validator.

### 2.5 Links (D8)

- `openLink(url)` opens an external page. `url` must be an absolute `https:`
  URL of at most 256 characters, without user name or password,
  whose host is not `t.me`, `telegram.me` or `telegram.dog`.
- `openTelegramLink(url)` opens a Telegram link: `https://t.me/…` (also
  `telegram.me`, `telegram.dog`), at most 256 characters, any path, as in Mini
  Apps: the client's own `t.me` handler confirms the dangerous ones (proxy,
  login, language and the like). `tg:` URLs are not accepted.
- An invalid argument throws `TypeError` in the widget synchronously (the shim
  checks the syntax; the glue checks it again). A valid call returns
  `undefined` and goes to the host.
- **User activation** (`docs/protocol.md`, "User activation"): the glue
  forwards a call only while the task of a real pointer press on the visible
  widget runs, and one press unlocks one call shared by both methods. A press
  is `pointerdown` … `click` from the renderer, marked `trusted` (a finger's or
  a pen's from its `pointerup`: the renderer does not mark their `pointerdown`,
  which may still become the chat's scroll), and the
  `dblclick` that ends a double tap (delivered since v0.1.2, §2.10) belongs to
  the press of its second click;
  keys never count (D4: no keyboard input or focus at all), nor does a click a
  key produced (`detail` 0), a scripted `click()`, a timer, a frame or a promise
  job after the task. Blocked calls are dropped without an exception and
  reported as the diagnostic `activation-blocked` (at most 20 per widget).
- `<a href="https://…">` clicks and `window.open(url)` take the same path: the
  shim calls `openTelegramLink` for Telegram hosts and `openLink` otherwise
  (before v1 everything went to `openLink`); what neither accepts (`http:`,
  `javascript:`) is ignored with the diagnostic `navigation-blocked`, never an
  exception. `window.open` returns `null`.
- The URL the client gets is normalised: scheme and host lowercase (A–Z only),
  characters outside ASCII after the host percent-encoded as UTF-8. The host
  is ASCII (an IDN in its `xn--` form); ASCII outside RFC 3986's unreserved and
  reserved characters and `%` (space, `"<>\^\`{|}`, controls) and a `%` that does
  not start `%XX` are a `TypeError`. The 256-character limit applies to the
  normalised URL.
- The widget is not told whether the user confirmed.

### 2.6 Haptics

- `impactOccurred`, `notificationOccurred`, `selectionChanged` as in Mini
  Apps' `HapticFeedback`. An unknown style or type throws `TypeError`.
- Delivered only while the widget is visible. Rate limit per widget: a bucket
  of 5 calls refilled at 10 per second.
- **A touch** (since v0.2.2): while a finger (or a mouse button) is down on the
  widget, and for 5 s after it is lifted, haptics pass under the rate limit.
  Outside a touch the first call opens a **series**: the calls of its first
  second pass, later ones do not. A new series may start after a touch (one
  series at any time later, until the next touch: a bot's answer that arrives
  seconds after the tap, the end of an animation), or, with no touch since the
  last series, 10 s after the last one began. So a timer cannot buzz the phone:
  it gets one second of haptics, then nothing for nine. The touch is the
  client's trusted input (the renderer's press and its release), never an event
  the widget dispatches; a press whose end never arrives counts as lifted 30 s
  after it began.
- Calls over the limit, outside a touch after the series, or from a hidden
  widget are dropped without an exception and reported once per second as
  `haptic-limit`. The numbers are the policy's named constants
  (`HAPTIC_TOUCH_WINDOW_MS`, `HAPTIC_SERIES_MS`, `HAPTIC_SERIES_GAP_MS`,
  `HAPTIC_PRESS_MAX_MS`).
- Desktop and web clients accept the calls and play nothing.

### 2.7 Lifecycle

- `document.hidden`, `document.visibilityState` and `visibilitychange`: a widget scrolled off screen, in a background tab or app gets
  `hidden`; while hidden it gets no animation frames and its timers fire at
  most once per second. `isActive` and the `activated` / `deactivated` events
  mirror the same state (fired after `visibilitychange`).
- `ready()` tells the host the first state is drawn. It is optional:
  the host also notices the first content on its own.
- A widget can be destroyed and recreated at any time (scrolled far away,
  memory pressure, app restart). Only Web Storage survives (§2.8).
- When the runtime is killed (CPU, memory, a crash), the last frame stays
  frozen with a "Widget stopped" badge.

### 2.8 Storage (D17)

Why local storage, and why per message: the state belongs to the message
(a client already keeps a record per message, the cached bubble height); the
bot cannot hold state for the widget because v1 has no messages or callbacks
to the bot (D8); and the server checks only the payload's size (D15), so any
cloud synchronisation would be new server work. (Since 2026-10-25 a widget also
has a state that only the bot writes, the chat state and the shared state, and
can ask the bot to change it, §2.13–§2.14. Web Storage stays what the widget
keeps for itself on this device.) Local Web Storage keyed by
the message is what fits all three; a cloud or per-bot `DeviceStorage` is
out of v1 (§6).

- `localStorage` is stored by the client for this message on this device,
  under (account, chat, message id), where the chat is the one the message is
  in: the bot's private chat or the group for the message it sent; a forwarded
  copy (allowed into any chat) is another message in another chat, another key,
  and starts empty. (D17 first said (account, bot, `message_id`); message ids
  are unique only within a chat, in supergroups too, so the chat is the key.) It
  survives the widget's
  destruction and app restarts, and is deleted with the message, the chat
  (or its history), the account and on "Clear cache". It is never sent to the
  bot or the server.
- `sessionStorage` has the same key and lives in the client's memory until the
  app restarts.
- Quota: 4 KB per area and message (4096 bytes of keys and values counted in
  UTF-8; 16 KB until the strict limits, `docs/decisions.md` "Strict limits from the start"). `setItem` that would exceed it throws a `DOMException` named
  `QuotaExceededError` (the shim provides `DOMException`) and stores nothing.
  Keys and values are converted to strings, as in browsers.
- The data is in the widget at start (synchronous reads from the first line of
  script). Writes are local at once; at the end of the task in which an area
  changed, the shim commits that area to the glue (§3.2), which hands it to
  the client.
- No `storage` event: a message is shown in one place at a time; if it ever is
  in two, the last commit wins.
- The code of a sent widget, its HTML, never changes: no edit replaces it (only
  the caption can be edited, as for any media). The bot changes only the
  widget's state (§2.13), and the changes reach the running widget as
  `chatDataChanged` / `publicDataChanged`; nothing restarts. A bot that wants a
  new widget sends a new message.

### 2.9 Locale

`navigator.language` is the client's UI language as a BCP 47 tag (`ru`,
`en-US`, `pt-BR`), `navigator.languages` is `[language]`, and the `Intl`
default locale is the same (a locale argument the widget passes still wins). A
missing or malformed locale is `en-US` (before v1: always `en-US`).

### 2.10 Removed and inert

| API | v1 behaviour |
|---|---|
| `TelegramWidget.sendMessage` | removed; no message is ever sent on the user's behalf |
| `alert`, `confirm`, `prompt` | no dialog, nothing sent to the host; `confirm` returns `false`, `prompt` `null` (before v1 they reached the host as a bridge call `alert`) |
| `navigator.clipboard.writeText`, `readText`, `write`, `read`, `navigator.share` | reject with a `DOMException` named `NotAllowedError`, nothing sent to the host (before v1 `writeText` reached the host as `clipboard`); `navigator.canShare()` is `false` |
| `fetch`, `XMLHttpRequest`, `WebSocket`, … | throw `NetworkDisabledError` (invariant 5) |
| keyboard | no key events, no keyboard focus (D4; the renderers and glue drop key events in profile v1) |
| double tap | always the widget's, on every element: both clicks, then the renderer's own `dblclick` (since v0.1.2; before, never delivered) |
| long press | the bubble's over `touch-action: auto` and `pan-*` (§5.5): the touch the client takes ends for the widget with `pointercancel` and no `click`; over `touch-action: none` the widget's, with the renderer's own `contextmenu` (since v0.1.2), unless the press first leaves the slop vertically: then it is the list's scroll (since v0.1.3). The engine's own `dblclick` and `contextmenu` never reach the widget |
| vertical drag | always the chat's (the list scrolls), on every element and whatever `touch-action` says; the widget gets `pointercancel` (since v0.1.3; before, `pan-x` and `none` claimed it) |

### 2.11 Errors and limits

| call | invalid arguments | outside the rules | other limits |
|---|---|---|---|
| `openLink`, `openTelegramLink` | `TypeError` | dropped, `activation-blocked` (no press, hidden, press used) | 256 characters; one call per press |
| haptics | `TypeError` | dropped, `haptic-limit` (hidden, over the bucket, outside a touch after the series) | bucket 5, 10 per second; outside a touch (a press and 5 s after it) one 1 s series, the next after a touch or 10 s later |
| `localStorage`, `sessionStorage` | — | `QuotaExceededError` | 4 KB per area |
| `isProfileAtLeast` | `false` | — | — |
| `onEvent`, `offEvent` | ignored (unknown type or non-function) | — | 16 handlers per type; further ones are ignored |
| `ready` | — | — | — |
| `setResizeAnchor` | `TypeError` (anything but exactly `'top'` or `'bottom'`) | — | only a change reaches the host |
| `watchPublicData`, `unwatchPublicData` | — | dropped, `preview-blocked` (a preview) | only a change reaches the host |
| `request` | `TypeError` (not a string of 1–64 bytes in UTF-8, or a lone surrogate) | the promise rejects: `NOT_ALLOWED` (no `can_query` on this message, no tap where background requests are off, a background call while hidden, a client without requests), `BUSY` (one in flight), `RATE_LIMITED` (background sooner than 5 s after the last one while visible, or the server's flood limit) | one in flight; one per tap; background: one when the widget appears on screen, then one per 5 s |
| `copyText` | `TypeError` (not a string of 1–256 UTF-16 code units, or a lone surrogate) | dropped, `activation-blocked` | one per tap |

Diagnostics go to the host's diagnostics (`onDiag` / `WidgetDiagnostics`),
never to the widget. The widget's state and requests add `request-blocked` (a refused
request, at most 20 per widget), `answer-rejected` (an answer whose `result`
broke the limits) and `data-rejected` (a chat state or shared state the client
passed that is over 1024 bytes or not text).

A widget is killed, and its last frame kept (§5.7), for one of these reasons the
host sees in `died` / `onDead`: `CPU limit exceeded` (more than `cpuLimitMs`,
500 ms, of continuous JS in one task), `Layout query limit exceeded` (more than 128 synchronous layout
queries in one task, or their time past 1000 ms; the queries also count
toward the host's main-thread window), `Memory limit exceeded (8 MB)`, and, under
`strict`, a counted cap of the validator (`Text limit exceeded`, `Attribute limit
exceeded`, `Node limit exceeded`, `Ops limit exceeded`, `Canvas limit exceeded`, `CSS limit exceeded`; the
validator's `limit` diagnostic carries the same reason; `docs/decisions.md`, "Strict
limits from the start") or the renderer's paint limit (v0.6.0: `Paint limit exceeded: N
boxes with shadows, filters or transparency (max 16)` or `Paint limit exceeded: N boxes
with backdrop-filter (max 2)`, more than 16 heavy boxes at once or more than 2 of them
with backdrop-filter; the diagnostic `paint-limit` / `backdrop-limit` carries the same
reason; `docs/decisions.md`, "Heavy boxes"). The paint limit's last frame is drawn
without the effects (no blurred shadow, filter, backdrop-filter or transparency,
animations stopped).

### 2.12 Resize anchor (v0.1.3)

`TelegramWidget.setResizeAnchor(edge)` says which edge of the bubble stays put
on screen when the widget's height changes:

- `'top'` (the default, also before any call): the bubble grows and shrinks
  downward; the content below it moves. An FAQ whose answers expand, "show
  more" results, an order summary that opens.
- `'bottom'`: the bubble grows and shrinks upward; its bottom stays and the
  content above it moves. A chat log or a support thread where new messages
  appear at the bottom and the older ones move up.

It may be called at any time and applies to the height changes that follow,
until it is called again; call it before the change it should anchor, at the
start of the script for a widget that always grows one way (the anchor of a
re-created widget is what its new run sets). It needs no tap and works while
hidden. Anything but exactly `'top'` or `'bottom'` throws `TypeError`. A client
whose framework predates v0.1.3 has no such method: check
`typeof TelegramWidget.setResizeAnchor === 'function'` (the profile number does
not tell: it stays 1). The anchor is a request: a client keeps it for a bubble
on screen; for a bubble off screen it keeps what is on screen in place
(§5.6). The widget is not told which edge moved.

### 2.13 The widget's state and where it is

A widget is fixed code plus changeable state. The code, its HTML, is set when
the bot sends the widget and never changes (§2.8). The state changes, and only
the bot writes it. Besides what it keeps for itself in Web Storage (§2.8), a
widget gets:

| member | what it is | in a forwarded copy |
|---|---|---|
| `chatData` | the **chat state**: tied to this message and seen only in its chat; in the private chat it is the user's, in a group every member sees the same one; a forward never carries it. A text of up to 1024 bytes (UTF-8), or `null`. For example a game in progress, whose turn it is | `null`, always |
| `publicData` | the **shared state**: seen wherever the widget is shown. A static widget keeps it in the message, and a forward gets a copy as of the forward, which never changes again; a shared widget (the server's object by `widget_id`, like a poll) has one shared state for all its messages and forwards, which changes for all of them at once. A text of up to 1024 bytes (UTF-8), or `null`. For example poll results, a price | the copy taken at the forward, or the shared widget's shared state |
| `isForward` | the message is a forwarded copy | `true` |
| `chatType` | `'private'`, `'group'`, `'supergroup'` or `'channel'`: the kind of chat the message is in (Bot API `Chat.type`; `'channel'` is not used yet); `null` when the client does not say | `null`, always: a copy is told nothing about the chat it is in (below) |
| `viewer` | the account looking at the widget, shaped like Mini Apps' `WebAppUser`: `id` (the Telegram user id, the same the bot gets with a request), `first_name`, `last_name`, `username` and `is_premium` (only when true); a field the client does not have is absent (frozen); **only where the widget may send requests** (the message's `can_query`), else `null`, in an original too | the viewer if the copy may send requests (below), else `null` |

- Only the bot writes the state: when it sends the widget and by separate
  edits. The widget reads it and cannot change it itself; it can only ask the
  bot (`request`, §2.14). A request's answer does not change the state.
- When the state changes, the widget gets `chatDataChanged` for the chat state
  and `publicDataChanged` for the shared state (`onEvent`), with the new values
  already in place: it redraws itself, nothing restarts and the page keeps its
  DOM and variables. One change that touches both fires both,
  `chatDataChanged` first, in the same task; a change that leaves a value as it
  was fires nothing for it. `chatDataChanged` never fires in a copy.
- The empty string is how the bot clears the chat state or the shared state:
  the widget sees `null`, never `''`.
- **Live shared state: `watchPublicData()`.** A shared widget (the bot sent it
  as one: a ticker, the counts of a poll others vote in) calls
  `TelegramWidget.watchPublicData()` to follow its shared state live: while it
  is on screen the client polls the shared state at the server's period
  (5–3600 s, 30 s by default) and `publicDataChanged` brings each change.
  `unwatchPublicData()` stops it. Synchronous, no tap needed, calling it again
  changes nothing. A widget that does not watch gets its shared state with the
  message, and the bot's edits of this message, but nothing polled: most
  widgets need nothing more. In a static widget, and in a preview, it does
  nothing.
- Where a widget lives: the private chat with the bot that sent it (it may
  also ask in the background, §2.14); a group or supergroup the bot is a member
  of (`chatType` `'group'` / `'supergroup'`: requests on a tap only, and the
  chat state is one for everyone in the group: no secrets there, draw it per
  `viewer.id`); any chat through the bot's inline mode (a message "via the
  bot": requests on a tap only); and forwards of any of these.
- `viewer` is what the bot gets as `from` with a tap: `{ id: 42, first_name:
  'Ann', last_name: 'Lee', username: 'ann_lee', is_premium: true }`. The names
  are 1–64 characters (UTF-16 code units), the username 5–32 characters of
  A–Z, a–z, 0–9 and _; the client leaves out a field that breaks its rule (and
  reports it), never the whole viewer.
- **The widget knows its viewer only where it may send requests** (the
  message's `can_query`): an original the bot sent without requests allowed,
  a copy that may not ask and a preview have `viewer` `null`. A widget that may
  not talk to the bot does not know who views it, so it cannot pass the id out
  through a link either, and the rule matches the network marker (§5.11). A
  bot that wants the user's id in its private chat puts it into the chat state
  itself.
- **A copy is told nothing about the chat it is in**: `chatType` is `null` in
  every forward, with requests or without. The bot must not learn where its
  widget was forwarded, and whatever the widget knows it could put into a
  request's `data`.
- A forward has no chat state. It may send requests (on a tap only) when the bot
  sent the widget with forwards allowed to ask (the server's
  `fwd_allow_queries`): then it also knows the viewer, whom the bot learns with
  a tap anyway (who pressed); the bot gets no message, chat or chat type with a
  copy's request, only who pressed, `data` and the shared object's id. A forward of a
  widget sent without it sends nothing and has no `viewer` either. To bring its reader to the bot, link to it with
  `openTelegramLink('https://t.me/<bot>?start=…')`: the bot learns who it is only
  when they press Start. A forward has its own Web Storage (§2.8).
- One state for all, drawn per viewer: chess for two is one widget and one
  chat state, drawn from `viewer.id`.

### 2.14 Requests to the bot

`TelegramWidget.request(data)` is how a widget asks the bot, the only writer of
its state; it is a callback button inside the widget: the bot gets what an
inline button's press gives it (who pressed, the message with its chat state and
shared state, `chat_instance`, the chat type; from a copy only who pressed and
the shared object's id) plus `data`, and answers with a `result` for this
widget. Only a widget the bot sent with requests allowed (the server's
`allow_queries`) may ask at all; the client marks such a widget as one whose tap
may send data to the bot (§5.11).

```js
TelegramWidget.request('vote:2').then(
  (answer) => show(answer.result),          // { result: string | null }, frozen
  (error) => showError(error.code),         // error.name === 'WidgetRequestError'
);
```

- `data`: a string of 1–64 bytes in UTF-8 (as `callback_data`); anything else
  throws `TypeError` at once.
- The promise resolves with `{ result }`: the bot's answer for this widget,
  text of at most 1024 bytes (empty allowed), or `null` when the bot answered
  with nothing. Only the widget that asked gets it.
- **The answer does not change the state.** A bot that changes the chat state
  or the shared state does it with separate calls, and the change reaches the
  widget the usual way (`chatDataChanged` / `publicDataChanged`), before or
  after the answer. A
  widget that needs the new state at once gets it from the bot in `result`
  (for example the user's own vote, or the new score as JSON).
- **Where a widget may ask** is the server's decision, per message, and the
  client passes it on; the widget does not derive it:

| message (of a widget the bot sent with `allow_queries`) | on a tap | in the background |
|---|:-:|:-:|
| the original in the private chat with the bot | ✓ | ✓ |
| an original in a group, or sent via the bot (inline) | ✓ | – |
| a forward of a widget the bot sent with `fwd_allow_queries` | ✓ | – |
| a forward of any other widget | – | – |

  A widget sent without `allow_queries` asks nowhere, its originals included.

- **A tap per request.** A request on a tap runs in that tap's handler, on the
  visible widget, like `openLink`: one tap unlocks one call, shared with
  `openLink`, `openTelegramLink` and `copyText` (a handler that calls `request`
  and then `copyText` copies nothing).
- **Background requests**, without a tap, only where the table allows them;
  they go out only while the widget is visible: one at once when the widget
  appears on screen (scrolled into view, the app back in front, at creation on
  screen), then at most one per 5 s (from the start of the last one) while it
  stays visible. One made while the widget is off screen waits in the client
  and goes out when the widget appears, as that appearance's one; its promise
  stays pending meanwhile (a widget that never appears never asks). Clients
  create a bubble before it is on screen, so a request in the script's first
  lines is the way to fetch fresh state: it goes out as soon as the widget is
  seen. The server and the bot cannot tell a background request from a tap (as
  for callback buttons); the limit is the client's, the framework's.
- **One in flight.** A second call while a request waits for its answer, or
  waits off screen to go out, rejects with `BUSY`. Only the client's gate
  decides it: a request it refused never makes the next one `BUSY`.
- The promise rejects with an `Error` whose `name` is `WidgetRequestError` and
  whose `code` says why:

| `code` | when |
|---|---|
| `NOT_ALLOWED` | this message may not ask (a widget sent without `allow_queries`, or a forward of one sent without `fwd_allow_queries`); no tap where background requests are off; a client that does not deliver requests |
| `BUSY` | another request is in flight, or waits off screen to go out |
| `RATE_LIMITED` | a background call sooner than 5 s after the last one while the widget stayed visible, or the server's flood limit |
| `TIMEOUT` | the bot did not answer in time (15 s: it is down, not subscribed to the updates, or slow) |
| `FAILED` | anything else: the bot was blocked or deleted, the message is gone, the network |
| `INVALID_ANSWER` | the bot's `result` broke the limits (it never reaches the widget) |

- What the bot learns: everything the widget's code knows can go into the 64
  bytes (its language, theme, text size, bubble width, time zone, what it kept
  in Web Storage); in the private chat background requests also tell the bot
  when the user has the chat open. Bot-side handling, the server's checks and
  the update the bot gets (`widget_query`, `answerWidgetQuery`) are the server's
  contract (`docs/tgw-format.md`, "The widget's state and requests").

### 2.15 `copyText`

`TelegramWidget.copyText(text)` copies a text to the clipboard, as an inline
button's `copy_text` does: a string of 1–256 UTF-16 code units (anything else
throws `TypeError`), on a tap only (one call per tap, shared as in §2.14;
outside a tap it is dropped with `activation-blocked`). The client shows a
toast that says what it copied, so a widget cannot show one text and copy
another unnoticed. The widget is not told that it was copied. A copy may copy.
`navigator.clipboard` stays a stub (§2.10).

### 2.16 Previews

A bot's inline result can be a widget; in the list of results, before the user
sends one, the client shows the widget as a **preview**: `isPreview` is `true`.
A preview has its state (`chatData`, `publicData`) and runs as usual
(timers, animations, the theme, the visibility and CPU rules of any bubble), but
it is not a message yet and is not the user's:

- it gets no input: a tap on it selects the result, and the widget gets no
  pointer events, clicks or gestures;
- it asks nothing: `request` rejects with `NOT_ALLOWED`; `openLink`,
  `openTelegramLink`, `copyText` and haptics do nothing (`ready` and
  `setResizeAnchor` work);
- it knows nothing about the user: `chatType` and `viewer` are `null`,
  `isForward` is `false`;
- its Web Storage starts empty and lives only for that run: nothing is kept.

Draw the same first state a sent widget would draw: the preview is what the
user is about to send.

### 2.17 The client's time over the widget: the content safe area

A message with a widget and **no caption below it** (no caption, or a caption
shown above the media, `invert_media`) gets its time and status (the ticks,
"edited", a channel's views) drawn by the client **over the widget's bottom
corner**, on a backing, as over a photo: the right corner in a left-to-right
interface, the left one in a right-to-left one (a physical side: an English widget
in an Arabic client has the time on its left). With a caption below the widget the
time is in the caption's line and nothing is drawn over the widget: everything is
0. A caption above the widget is as no caption: the time is over the corner
again, as on a photo whose caption is moved up. Taps on the time are the
client's (a view over the widget), never the widget's: put nothing a user taps
under it. The floating date pill of a chat that scrolls past the widget has no
contract: it is drawn over the widget like over a photo and moves every frame.

The widget gets the band and the corner the way a web page gets the screen's notch;
the client gives the numbers:

```css
/* the band the time is drawn in, at the bottom: keep it clear */
.footer { padding-bottom: max(8px, env(safe-area-inset-bottom)); }
/* only the corner of the band is covered: content may run into the band on the other side */
.footer { padding-right: max(12px, var(--tg-content-safe-area-corner-right));
          padding-left: max(12px, var(--tg-content-safe-area-corner-left)); }
```

- `env(safe-area-inset-top|right|bottom|left[, fallback])` is in profile 1 (a
  clarification of 2026-10-29, before widgets launched; `P1-safe-area` in
  `profile.json`). The validator rewrites it, in stylesheets, `style` attributes
  and CSSOM writes alike, to `var(--tg-content-safe-area-inset-…[, fallback])`: the
  engine's own `env()` belongs to the document (on the web, to the chat page) and
  comes from the view's system insets, so it never reaches the renderer. The
  variables are always set (0 with a caption below), so a fallback applies only where a
  client sets nothing. Any other `env()` name (`keyboard-inset-*`, `titlebar-area-*`)
  resolves to its fallback, or drops the declaration without one; `env()` inside
  `url()` and the other URL-taking functions is rejected like `var()`. `CSS.supports`
  and `@supports` answer `true` for the safe-area insets only.
- The root variables, set by the renderer like the theme's (inline and
  `!important`, names from a fixed list, values checked): `--tg-content-safe-area-inset-top`,
  `-right`, `-bottom`, `-left` (bottom = the height of the time's band, the rest
  0; the names are Mini Apps', so `var(--tg-content-safe-area-inset-bottom)` from
  Mini Apps code works too) and `--tg-content-safe-area-corner-left`, `-right`: the
  width the client's corner takes in the band, on its physical side (`right` in a
  left-to-right interface, `left` in a right-to-left one, the other 0). An inset is
  0–64 px and a corner 0–200 px; a client value outside that is 0.
- The renderer's UA layer (§2.4) adds the band to the body's bottom padding
  (`:where(body){padding-bottom:var(--tg-content-safe-area-inset-bottom,0px)}`), so
  an unstyled widget (and one that sets only `body { margin: 0 }`) stays clear of
  the time and is that much taller. A widget that sets the body's padding, or resets
  every padding (`* { padding: 0 }`), handles the band itself. The converse: a widget that
  pads for the band itself must set the body's padding too (`body { margin: 0; padding: 0 }`),
  or the default bottom padding comes on top of its own (`corpus/safe-area`, README).
- `TelegramWidget.contentSafeAreaInset`, `{ top, right, bottom, left, cornerLeft,
  cornerRight }` in CSS px, has the same numbers for script (a canvas widget lays
  its own controls out with them; `getComputedStyle` on the variables would be a
  layout query). A change (a caption added, removed or moved above or below the widget
  by an edit, an "edited" mark that widens the time) gives a fresh frozen object and fires
  `contentSafeAreaChanged`, in one task, with the variables already set. It never
  changes per frame.

### 2.18 The wallet

A widget can ask the user to pay or to sign through Telegram's built-in wallet (Gram Wallet). The widget is the
front of a dApp and its bot is the back end, as a site and its server are in TON Connect; the formats are TON
Connect's, but there is no connection, no manifest, no bridge and no domain: who asks is the bot that sent the
message, and the client's own wallet window says so. Decided in `docs/decisions.md`, "The wallet".

```js
// A tap asks the bot with the user's wallet; the bot answers with a fresh transaction; the widget passes it on.
TelegramWidget.request('pay:42', { wallet: true })
  .then((a) => TelegramWidget.wallet.sendTransaction(JSON.parse(a.result), 'paid:42'))
  .then((a) => show(a.result), (e) => showError(e.code));

TelegramWidget.wallet.signData({ type: 'text', text: 'I accept the terms of order 42' }, 'terms:42')
  .then((a) => show(a.result));
```

- **`TelegramWidget.wallet`** is `null` where the client has no wallet for this user (a client without it, a
  country where the wallet is not available), where the widget may not ask the bot (no `canQuery`: §2.14's table;
  a forward of a widget sent with `fwd_allow_queries` may) and in a preview. Check it before drawing a pay button.
  The widget never learns the user's address from the client; the bot does (below) and may put it in `result`.
- **`request(data, { wallet: true })`**, on a tap only: the bot gets the wallet card of the user's profile (the
  cloud wallet's address and balance) with the request; a user without a wallet gets one created then (a new wallet
  is empty: the client offers to top it up when it is paid from). Refused (`NOT_ALLOWED`) without `wallet` or
  without a tap, background requests included. `options` is an object with at most `wallet`, a boolean; anything
  else throws `TypeError`. The wallet that pays or signs is the one on the device, and it can be another one: with
  the wallet's backup off a device keeps its own key and its own wallet. So the bot does not put the profile's
  address in a transaction's `from` (the wallet refuses a `from` that is not its own: `BAD_REQUEST`); it learns the
  device's wallet with the outcome (below).
- **`wallet.sendTransaction(transaction, data)`**: `transaction` is TON Connect's `sendTransaction` request with
  its own field names: `{ valid_until?, network?, from?, messages: [{ address, amount, payload?, stateInit? }] }`;
  1–255 messages (what Gram Wallet takes); `valid_until` in Unix seconds; `network` `'-239'` (the mainnet) or
  `'-3'` (the testnet); a message's `address` user-friendly (48 characters, the bounce flag in it; a raw `0:…` is
  refused, as TON Connect wallets must), `from` raw or user-friendly; `amount` nanotons as a decimal string;
  `payload` and `stateInit` bags of cells in base64, at most 8192 characters each and 65536 for all messages
  together. `null` fields are left out; any other field (`items`, `extra_currency`, the SDK's `validUntil`) throws
  `TypeError`. The client shows the transaction in its own wallet window
  (each message's amount and destination, the emulated outcome) with the bot of the message; on confirmation it
  sends it, gives the bot the outcome (the message hash and the device's wallet that sent it: its address and
  balance, its key and StateInit where the client has them; the bot checks the transaction on the chain) and resolves
  with the bot's answer to it, `{ result }`, as `request` does. The widget never gets the signed message.
- **`wallet.signData(payload, data)`**: `payload` is TON Connect's: `{ type: 'text', text }` (at most 4096 bytes
  of UTF-8, shown to the user), `{ type: 'binary', bytes }` or `{ type: 'cell', schema, cell }` (base64 of at most
  4096 characters, the TL-B schema at most 1024 bytes; the window warns that it cannot show them), optionally with
  `network` and `from`. There is no domain: the client signs for `bot<bot_id>` of the message. The signature goes
  to the bot with the device's wallet that signed (its address, public key and StateInit: the bot checks the signature
  with the key and ties the key to the address by the StateInit or on the chain); the bot checks it or passes it on,
  and resolves this with its answer.
- `data` (1–64 bytes, a request's) is the label the bot gets with the outcome: what the payment or the signature
  is for.
- **When.** On a tap, in its handler, on the visible widget (one call per tap, shared as in §2.14), or **within
  10 s of the answer to a call made by a tap** (a request or a wallet call): the answer grants one wallet call
  without a new tap, so a tap can pay for what the bot answers. Any call made on a tap or on the grant uses the
  grant up; a refusal, a failure or a background request's answer brings none. Never in the background, never
  from a timer. One call in flight, shared with requests (`BUSY`).
- The promise rejects as a request's does (§2.14), plus two codes:

| `code` | when |
|---|---|
| `REJECTED` | the user declined in the wallet window |
| `BAD_REQUEST` | the wallet refused the transaction or the payload (another network or sender than the user's, an expired `valid_until`, cells it cannot parse) |
| `NOT_ALLOWED` | no wallet, no tap and no grant, a preview |

  A failure after the window (`TIMEOUT`, `FAILED`) means the bot's answer did not come: the transaction may have
  been sent; ask the bot about the order again rather than paying twice.

## 3. Protocol

What carries the API between the sandbox (QuickJS), the host glue and the
renderer. Every message from the sandbox is untrusted and is checked in all
five glue copies: the web worker and `WidgetHost` (TS), the Node harness,
Swift, Kotlin, C++ (`sandbox_engine.cpp`, `widget_host.cpp`). The checks live in
one policy with a shared JSON fixture, as `cpu-budget` does:
`packages/core/src/shared/sdk-policy.ts` and its twins `SdkPolicy.swift`,
`SdkPolicy.kt`, `sdk_policy.cpp`, all checked against
`packages/core/tests/fixtures/sdk-policy.json`.

### 3.1 Config (`SandboxConfig`, host → sandbox at start)

| field | type | set by | status |
|---|---|---|---|
| `theme` | `'light' \| 'dark'` | client (`colorScheme`) | |
| `themeParams` | `Record<ThemeKey, '#rrggbb'>` | client | |
| `locale` | BCP 47 string, at most 35 characters | client | |
| `contentSafeArea` | `{ top, right, bottom, left, cornerLeft, cornerRight }`, CSS px | client (§2.17, §5.12; all 0 when absent); the shim checks it: an inset 0–64, a corner 0–200, two decimals, else 0 | |
| `profile` | integer | glue (`WidgetSandboxVersion` / `twidget::version`, generated from `VERSION.json`'s `profile`) | the shim reports `profile-mismatch` when it differs from the bundle's; the native glue does not start a bundle stamped with another profile (`bundle-mismatch`, `docs/integration.md` "Pinning a release") |
| `storage` | `{ local: Record<string, string>, session: Record<string, string> }` | client (the preload), glue checks the quota | |
| `policy` | `'strict'` | glue default | |
| `chatData`, `publicData` | `string \| null` | client (`data`: the chat state and the shared state), glue checks: text of 1–1024 bytes without a NUL, else `null` (`data-rejected`); a copy's `chatData` is dropped | |
| `isCopy` | `boolean` | client (`context.isCopy`, the server's `is_copy`); the widget's `isForward` | |
| `canQuery` | `boolean` | client (`context.canQuery`, the server's `can_query`); the shim refuses a request at once without it; `false` in a preview | |
| `isPreview` | `boolean` | client (`context.preview`): an inline result before it is sent (§2.16) | |
| `chatType` | one of the four or `null` | client (`context`), `null` in every copy and in a preview | |
| `viewer` | `{ id, first_name?, last_name?, username?, is_premium? } \| null` | client (`context`), checked: an id of 1 to 2^53 − 1 (else `null`), names of 1–64 UTF-16 code units, a username of 5–32 characters of A–Z, a–z, 0–9 and _, `is_premium` only when true; a field that breaks its rule is left out (`data-rejected`); `null` without `canQuery` (an original sent without requests allowed, a copy that may not ask, a preview) | |

The client's values are trusted but still checked: unknown theme keys and
values that are not `#rrggbb` are dropped, a storage preload over the quota is
dropped whole with the diagnostic `storage-rejected` (the widget starts empty).

### 3.2 Sandbox → host

`__host('bridge', JSON.stringify([method, args]))`, one call per API call:

| method | args | glue checks | goes to the client as |
|---|---|---|---|
| `ready` | `[]` | — | `ready` |
| `openLink` | `[url]` | URL rules of §2.5; privileged: needs the task's gesture, consumes it | `openLink(url)` |
| `openTelegramLink` | `[url]` | URL rules of §2.5; privileged | `openTelegramLink(url)` |
| `haptic` | `['impact', style]`, `['notification', type]`, `['selection']` | enum values; visible; rate limit | `haptic(...)` |
| `setResizeAnchor` | `[edge]` | exactly `'top'` or `'bottom'` (v0.1.3); no gesture, no visibility gate | not an action: the host keeps the latest value and reports it with the height (§4) |
| `watchPublicData` | `[on]` | exactly one boolean; no gesture, no visibility gate; refused in a preview | not an action: the host keeps the latest value and reports a change (`onWatchPublicData`, `watchesPublicData`, §4) |
| `request` | `[id, data]` or `[id, data, { wallet }]` | `id` an integer 1–2147483647 (the shim numbers its requests), `data` 1–64 bytes, the options an object with at most `wallet`, a boolean; then the request gate (below) | `onRequest({ data, wallet? })`; the answer goes back as a `bridge` event (§3.3) |
| `wallet` | `[id, 'sendTransaction', transaction, data]` or `[id, 'signData', payload, data]` | the transaction or the payload as §2.18 has it (`checkWalletTransaction`, `checkSignDataPayload`), `data` 1–64 bytes; then the request gate's `decideWallet` (below) | `onWallet(call)`; the answer goes back as a `bridge` event, as a request's |
| `copyText` | `[text]` | 1–256 UTF-16 code units; privileged: needs the task's gesture, consumes it | `onCopyText(text)` |

The glue checks in this order: the method and its arguments
(`checkBridgeCall`; any other method or a malformed call is dropped with
`bridge-rejected`, at most 20 reported); for `request` and `wallet` the request gate (below);
user activation for the two link methods and `copyText` (`activation-blocked`);
for `haptic` the gate of §2.6 (`haptic-limit`).

In a preview (§2.16) the host takes no input from the renderer (no events, no
gesture claims), lets only `ready` and `setResizeAnchor` through
(`allowedInPreview`; the rest are dropped with `preview-blocked`, a request is
answered `NOT_ALLOWED`) and drops storage commits.

The request gate (`RequestGate` in the policy), from the server's flags only, in
this order: no `canQuery` → `disabled`; a request in flight → `busy`; the
task's gesture is unconsumed and the widget visible → `foreground` (the gesture
is consumed; under `compat` every call counts as pressed); no
`backgroundQuery` → `no-activation`; hidden → `hidden`; less than 5 s since the
last background request → `rate-limited`; else `background`. The host calls the gate's `show`
when the widget becomes visible: the next background request may go out at
once. A refusal goes back to the widget as an `answer` event with the error code
(`requestRefusal`) and is reported as `request-blocked` (at most 20). An
accepted request goes to the client with its `data` only (whether it came from
a tap stays in the framework); a client without `onRequest` at that moment
refuses it (`NOT_ALLOWED`, reason `unsupported`). The answer is checked
(`checkRequestAnswer`: `result` text of at most 1024 bytes without a NUL, else
`INVALID_ANSWER` and `answer-rejected`) and frees the gate; so does a failure
(`TIMEOUT`, `RATE_LIMITED`, `FAILED`).

The wallet (§2.18) in the same gate: a request with `{ wallet: true }` without the context's `wallet` →
`no-wallet`; it goes out only as `foreground` (no background, never held). A wallet call (`decideWallet`): no
`canQuery` → `disabled`; no `wallet` → `no-wallet`; a call in flight → `busy`; a press on the visible widget →
`foreground`; the visible widget within 10 s of the grant → `granted`; else `no-activation`. The grant: an answer
with no error to a call that went out as `foreground` or `granted` (`finish(answeredAt)`); any call that goes out
as either uses it up. A wallet call's failure keeps the wallet's codes (`REJECTED`, `BAD_REQUEST`); a request's
turns them into `FAILED`.
A call that passes goes to the client as a `WidgetAction` with the normalised
URL (and, deprecated, to `onBridge` with the raw method and arguments), except
`setResizeAnchor` and `watchPublicData`, which only set a value the host keeps:
the shim sends them only when the value changes, and the host keeps one value,
so a widget that calls them in a loop costs the client nothing. The
method names `sendMessage`, `alert` and `clipboard` are gone from the wire.

Gesture tagging: on native hosts the engine runs a trusted event's task
with that press's gesture number and lets the first privileged call of the task
consume it. On the web the worker tags a `bridge` message with the gesture of
the task (`{type:'bridge', method, args, gesture}`) and the main-thread host
accepts each issued gesture once, within 5 s.

`__host('storage', JSON.stringify(commit))`, at most once per task, after the
task's `ops`: `commit` is `{ local?: Record<string, string>, session?:
Record<string, string> }`, the full content of each area that changed in the
task. The shim sends it once the task is over (after the MutationObserver
callbacks, when the glue's last `endTick` of the task returns). The glue
refuses an argument longer than any commit within the quota can be (2 × 12 ×
4096 + 64 characters) before parsing it, checks that the commit has only
`local` / `session`, each an object of strings within the quota, and hands
each area to the client (`onStorageCommit(area, values)`, `local` first); a
commit that fails is dropped whole with `storage-rejected` (at most 20
reported) and the client keeps the previous content. Commits reach the client
in order.

### 3.3 Host → sandbox

| message | payload |
|---|---|
| `theme` | `{ theme: 'light' \| 'dark', themeParams }` (before v1: the scheme only) |
| `safeArea` | `{ top, right, bottom, left, cornerLeft, cornerRight }` (§2.17): the shim checks it, and on a change sets a fresh `contentSafeAreaInset`, drops its cached layout answers and fires `contentSafeAreaChanged`, in one task. Not deferred under the CPU quota (it comes from the client, never from the widget, and only with the bubble's layout) |
| `visibility` | `{ visible }` |
| `event` | a renderer event, with `gesture: n` for a trusted press |
| `bridge` | `{ type: 'data', chatData, publicData }` (the full state after a change: the shim fires `chatDataChanged`, then `publicDataChanged`, for each that differs), or `{ type: 'answer', id, result }` / `{ type: 'answer', id, error, reason? }`. Deferred under the CPU quota like `theme`, in order |

The `bridge` message of the profile-0 builds (host → widget events such as a
bot's answer to `sendMessage`) went with `sendMessage` in v1; the name is back
for the widget's state and the answers to `request`.

### 3.4 Host → renderer

| message | payload |
|---|---|
| `theme` | `{ type: 'theme', theme: { colorScheme, params, button, text } }`: the renderer core builds the variables of §2.3 and §2.4 (`themeVariables`) and sets them on the root (`:root`, or `[data-tw-root]` on the shadow-DOM renderer); the colour scheme itself still goes to the renderer element (`color-scheme`, the WebView's appearance) as before. A native renderer does the same with its own copy of `themeVariables` (`apps/renderer-litehtml/src/theme.cpp`) |
| `safeArea` | `{ type: 'safeArea', inset: { top, right, bottom, left, cornerLeft, cornerRight } }` (§2.17): the renderer core sets the six `--tg-content-safe-area-*` variables (`contentSafeAreaVariables`) on the root next to the theme's, 0 until the first one; hosts send it right after `theme` and on every `setContentSafeArea`. The litehtml prototype's twin is `theme::safe_area_variables`; Blitz ignores it (the fallback of a widget's `env()` then applies) |

| `policy` | `{ type: 'policy', policy, paintReports? }` (v0.6.0): the host's policy, right after `safeArea`. Under `strict` (also before it arrives, and for anything but exactly `compat`) the renderer core counts heavy boxes (`paint-policy.ts`) and reports `limit`; under `compat` it counts nothing. `paintReports` (tooling) asks for `paint { heavy, backdrop }` on every change |
| `arbitrate` | `{ type: 'arbitrate', bubble, touch?, rtl? }` (v0.1.2): `bubble` true, the renderer plays the bubble itself with the gesture policy (the web hosts, `bubbleGestures`): a press the bubble takes ends for the widget with `pointercancel` and the host gets `bubble`. `touch`: a mouse's primary button arbitrates as a finger (and the widget sees `pointerType: 'touch'`). `rtl`: the edge strip is on the right. Native hosts leave it off: their own recognizers take the presses |

The renderer takes only variable names from the fixed list above and values
that match their grammar (`#rrggbb`, CSS lengths, a font shorthand with a
generic family, numbers 0–1, a font size of 8–64 px, a line height of 1–3, a
safe-area inset of 0–64 px and a corner of 0–200 px).

### 3.5 Renderer → host

| message | payload |
|---|---|
| `gesture` | `{ wants, x, y, hold }` (booleans) on every primary `pointerdown`: what of this press the widget claims (`claimOf` of the gesture policy): horizontal drags (`x`), from the `touch-action` the widget gave the target and its ancestors and its `preventDefault` on `pointerdown`/`touchstart`; `hold` only from `touch-action: none` (v0.1.2). Taps and double taps are always the widget's, vertical drags always the chat's: `y` and `wants` (the field before v1, equal to `y`) are always false since v0.1.3, and hosts report `y` false whatever a renderer sends |
| `bubble` | `{ action, gesture, x, y, edge }` with `arbitrate` `bubble` on (v0.1.2): the bubble took a press: `action` `menu` \| `swipe` \| `scroll` \| `back`; (x, y) where the pointer was, widget coordinates; `edge`: it started in the leading edge strip |
| `size` | the content height |
| `event` | `trusted: true` only for real input |
| `limit` | `{ code, count }` (v0.6.0), past a paint limit, once, strict only: `paint-limit` with the number of heavy boxes (more than 16) or `backdrop-limit` with the number of boxes with backdrop-filter (more than 2). The renderer has drawn that frame without the effects and takes no more ops; the host stops the widget with `Paint limit exceeded: …` (§2.11's reasons). A client's own renderer sends it through the seam's `limitExceeded(code, count)` (§4.1–§4.3) |

## 4. Host interface per platform

The shapes the client wires up, as implemented (v0.1.0). Callbacks run on the
main thread (Swift, Kotlin), on the host thread (C++) or on the page's event
loop (web). On every platform the string-theme constructor, `setTheme(String)`
and `onBridge` are kept as deprecated forwards (no behaviour of their own;
`onBridge` gets the raw method and arguments of each call `onAction` got, right
after it); the host → widget bridge events (`bridgeEvent`) are removed.

### 4.1 Swift (iOS, macOS): `WidgetHost`

```swift
public struct WidgetTheme { var colorScheme: ColorScheme /* .light / .dark */; var params: [String: String]; var button: WidgetButtonTokens; var text: WidgetTextTokens }
public struct WidgetButtonTokens { var bg, text, radius, font, padding, height: String?; var pressedOpacity, disabledOpacity: Double? }
public struct WidgetTextTokens { var fontSize, lineHeight: Double?; var padding: String? }   // v0.1.1
public struct WidgetStorage { var local: [String: String]; var session: [String: String] }
public enum WidgetStorageArea: String { case local, session }
public enum WidgetHaptic { case impact(String), notification(String), selection }
public enum WidgetAction { case ready, openLink(URL), openTelegramLink(URL), haptic(WidgetHaptic) }
public struct WidgetGestureClaim { var x: Bool; var y: Bool; var hold: Bool }   // hold: v0.1.2; y: always false since v0.1.3
public enum WidgetResizeAnchor: String { case top, bottom }   // v0.1.3: WidgetDiagnostics.resizeAnchor, next to height

init(html: String, theme: WidgetTheme = WidgetTheme(), width: CGFloat = 400, options: Options = Options(), …) throws
// Options: maximumHeight (600), policy (.strict), features, pools, …, locale ("en-US"), storage (the preload),
//          contentSafeArea (WidgetContentSafeArea(): the client's time over a widget with no caption below it, §2.17, §5.12)
public struct WidgetContentSafeArea { var top, right, bottom, left, cornerLeft, cornerRight: Double }   // points = CSS px, 0 by default
var onChange: ((WidgetDiagnostics) -> Void)?           // height, resizeAnchor (v0.1.3), watchesPublicData, loaded, rendered, dead, diags, actions
var onWatchPublicData: ((Bool) -> Void)?                // the widget started or stopped watching its shared state (§5.11): on a change
var onError: ((String) -> Void)?
var onAction: ((WidgetAction) -> Void)?
var onStorageCommit: ((WidgetStorageArea, [String: String]) -> Void)?   // local before session
var onGestureClaim: ((WidgetGestureClaim) -> Void)?   // every primary pointerdown; drive the bubble's recognizers with GestureArbiter (§5.5)
func setTheme(_ theme: WidgetTheme)
func setContentSafeArea(_ area: WidgetContentSafeArea)   // only with the bubble's layout (a caption edit or move), never per frame
func setVisible(_ visible: Bool); func snapshot(completion:); func destroy()
// the renderer seam (rendererFactory), v0.6.0: a renderer of your own reports the paint limit, once, before it draws that frame (§5.14)
public enum WidgetPaintLimit: String { case paint = "paint-limit", backdrop = "backdrop-limit" }   // maxHeavyBoxes 16, maxBackdropBoxes 2; message(count:)
extension Renderer { func limitExceeded(_ code: WidgetPaintLimit, count: Int) }
```

The widget's state and requests (§2.13–§2.15, §5.11):

```swift
public struct WidgetData { var chatData: String?; var publicData: String? }          // the full state: the chat state and the shared state
public enum WidgetChatType: String { case `private`, group, supergroup, channel }
public struct WidgetViewer { var id: Int64; var firstName: String?; var lastName: String?; var username: String?; var isPremium = false }  // the account's own
public struct WidgetContext { var isCopy = false; var canQuery = false; var backgroundQuery = false; var chatType: WidgetChatType?; var viewer: WidgetViewer?; var preview = false; var wallet = false }  // the server's flags; preview: §2.16; wallet: §2.18
public struct WidgetRequest { let data: String; let wallet: Bool }   // wallet: attach the wallet card (with_wallet)
public struct WidgetRequestAnswer { var result: String? }
public enum WidgetRequestFailure: Error { case timeout, rateLimited, notAllowed, failed, rejected, badRequest }   // rejected, badRequest: wallet calls
public enum WalletCall { case sendTransaction(WalletTransaction, data: String), signData(SignDataPayload, data: String) }   // .json: TON Connect's shapes
// Options: data (WidgetData()), context (WidgetContext())
var onRequest: ((WidgetRequest, @escaping (Result<WidgetRequestAnswer, WidgetRequestFailure>) -> Void) -> Void)?   // complete once, from any thread
var onWallet: ((WalletCall, @escaping (Result<WidgetRequestAnswer, WidgetRequestFailure>) -> Void) -> Void)?    // §2.18, §5.13
var onCopyText: ((String) -> Void)?
func setData(_ data: WidgetData)
```

`WidgetBubble`, `WidgetModel` (`WidgetView`) and `WidgetPreparer` take a
`WidgetTheme` the same way. `URL(string:)` may escape a few characters the
check keeps (`[`, `]`, a second `#`); the checked string is
`SdkPolicy.checkLink`'s.

### 4.2 Kotlin (Android): `WidgetHost`

```kotlin
data class WidgetTheme(val colorScheme: String = "light", val params: Map<String, String> = emptyMap(), val button: WidgetButtonTokens = WidgetButtonTokens(),
                       val text: WidgetTextTokens = WidgetTextTokens())
data class WidgetButtonTokens(val bg: String? = null, val text: String? = null, val radius: String? = null, val font: String? = null,
                              val padding: String? = null, val height: String? = null, val pressedOpacity: Double? = null, val disabledOpacity: Double? = null)
data class WidgetTextTokens(val fontSize: Double? = null, val lineHeight: Double? = null, val padding: String? = null)   // v0.1.1
data class WidgetStorage(val local: Map<String, String> = emptyMap(), val session: Map<String, String> = emptyMap())
sealed class WidgetAction { Ready; OpenLink(url); OpenTelegramLink(url); Haptic(kind, value?) }   // kind: impact | notification | selection

WidgetHost(context, html, theme: WidgetTheme, widthDp, options, resources, rendererFactory)
// Options: minHeightDp (50), maxHeightDp (600), policy ("strict"), features, pools, …, locale ("en-US"), storage (the preload),
//          contentSafeArea (WidgetContentSafeArea(): the client's time over a widget with no caption below it, §2.17, §5.12)
data class WidgetContentSafeArea(val top: Double = 0.0, val right: Double = 0.0, val bottom: Double = 0.0, val left: Double = 0.0, val cornerLeft: Double = 0.0, val cornerRight: Double = 0.0)   // dp = CSS px
var onChange: ((WidgetDiagnostics) -> Unit)?               // … height, resizeAnchor ("top" | "bottom", v0.1.3), watchesPublicData, actions
var onWatchPublicData: ((Boolean) -> Unit)?                 // the widget started or stopped watching its shared state (§5.11): on a change
var onError: ((String) -> Unit)?
var onAction: ((WidgetAction) -> Unit)?
var onStorageCommit: ((area: String, values: Map<String, String>) -> Unit)?   // "local" | "session"
var onGesture: ((WidgetGestureClaim) -> Unit)?             // v0.1.2: x, y, hold (y always false since v0.1.3); drive the bubble's recognizers with GestureArbiter (§5.5)
var onGestureClaim: ((x: Boolean, y: Boolean) -> Unit)?    // deprecated since v0.1.2: called right after onGesture
fun setTheme(theme: WidgetTheme); fun setContentSafeArea(area: WidgetContentSafeArea); fun setVisible(visible: Boolean); fun destroy()
// the renderer seam (rendererFactory), v0.6.0: a renderer of your own reports the paint limit, once, before it draws that frame (§5.14)
enum class WidgetPaintLimit(val code: String) { PAINT("paint-limit"), BACKDROP("backdrop-limit") }   // MAX_HEAVY_BOXES 16, MAX_BACKDROP_BOXES 2; message(count)
interface Renderer { …; fun limitExceeded(code: WidgetPaintLimit, count: Int) /* default: posts the limit message to WidgetHost */ }
```

The widget's state and requests:

```kotlin
data class WidgetData(val chatData: String? = null, val publicData: String? = null)
data class WidgetViewer(val id: Long, val firstName: String? = null, val lastName: String? = null, val username: String? = null, val isPremium: Boolean = false)
data class WidgetContext(val isCopy: Boolean = false, val canQuery: Boolean = false, val backgroundQuery: Boolean = false,
                         val chatType: String? = null, val viewer: WidgetViewer? = null, val preview: Boolean = false,
                         val wallet: Boolean = false)   // the server's flags; preview: §2.16; wallet: §2.18
data class WidgetRequest(val data: String, val wallet: Boolean = false)   // wallet: attach the wallet card (with_wallet)
data class WidgetRequestAnswer(val result: String? = null)
enum class WidgetRequestFailure { TIMEOUT, RATE_LIMITED, FAILED, REJECTED, BAD_REQUEST }   // REJECTED, BAD_REQUEST: wallet calls
class WidgetRequestException(val failure: WidgetRequestFailure) : Exception()
sealed class WalletCall { SendTransaction(transaction, data), SignData(payload, data) }   // .json(): TON Connect's shapes
// Options: data, context
var onRequest: ((WidgetRequest, (Result<WidgetRequestAnswer>) -> Unit) -> Unit)?   // Result.failure(WidgetRequestException(…)); any other exception is FAILED
var onWallet: ((WalletCall, (Result<WidgetRequestAnswer>) -> Unit) -> Unit)?     // §2.18, §5.13
var onCopyText: ((String) -> Unit)?
fun setData(data: WidgetData)
```

The WebView renderer keeps its parent from intercepting a press only while the
widget may still take it as a hold (a `hold` claim, until the press leaves the
slop vertically: `requestDisallowInterceptTouchEvent`). The Blitz and litehtml
prototypes send only `wants`, always false now: they claim nothing.

### 4.3 C++ (desktop core): `twidget::WidgetHost`

```cpp
struct WidgetButtonTokens { std::optional<std::string> bg, text, radius, font, padding, height; std::optional<double> pressedOpacity, disabledOpacity; };
struct WidgetTextTokens { std::optional<double> fontSize, lineHeight; std::optional<std::string> padding; };   // v0.1.1
struct WidgetTheme { std::string colorScheme; std::map<std::string, std::string> params; WidgetButtonTokens button; WidgetTextTokens text; };
struct WidgetStorage { std::map<std::string, std::string> local, session; };
struct WidgetAction { enum Kind { Ready, OpenLink, OpenTelegramLink, Haptic } kind; std::string url, hapticKind, hapticValue; };

WidgetHost(std::string html, WidgetTheme theme, double width, WidgetHostOptions, std::shared_ptr<SandboxResources>, RendererFactory, …);
// WidgetHostOptions: …, std::string locale ("en-US"), WidgetStorage storage (the preload),
//                    WidgetContentSafeArea contentSafeArea (the client's time over a widget with no caption below it, §2.17, §5.12)
struct WidgetContentSafeArea { double top = 0, right = 0, bottom = 0, left = 0, cornerLeft = 0, cornerRight = 0; };   // CSS px
std::function<void(const WidgetDiagnostics&)> onChange;           // … height, resizeAnchor ("top" | "bottom", v0.1.3), watchesPublicData, actions, storageCommits (first 1000)
std::function<void(bool)> onWatchPublicData;                      // the widget started or stopped watching its shared state (§5.11): on a change
std::function<void(const std::string&)> onError;
std::function<void(const WidgetAction&)> onAction;
std::function<void(const std::string& area, const std::map<std::string, std::string>&)> onStorageCommit;
std::function<void(const WidgetGestureClaim&)> onGesture;          // v0.1.2: x, y, hold (y always false since v0.1.3; twidget/gesture_policy.h, GestureArbiter)
std::function<void(bool x, bool y)> onGestureClaim;                 // deprecated since v0.1.2: called right after onGesture
void start(); void setTheme(const WidgetTheme&); void setContentSafeArea(const WidgetContentSafeArea&); void setVisible(bool); void destroy();
// the renderer seam (RendererFactory), v0.6.0: a renderer of your own reports the paint limit, once, before it draws that frame (§5.14)
enum class WidgetPaintLimit { Paint, Backdrop };   // kMaxHeavyBoxes 16, kMaxBackdropBoxes 2; paintLimitCode, paintLimitMessage, paintLimitFromCode
void Renderer::limitExceeded(WidgetPaintLimit code, int count);
```

The widget's state and requests:

```cpp
struct WidgetData { std::optional<std::string> chatData, publicData; };
struct WidgetViewer { int64_t id = 0; std::optional<std::string> firstName, lastName, username; bool isPremium = false; };
struct WidgetContext { bool isCopy = false, canQuery = false, backgroundQuery = false; std::string chatType; /* "" = none */ std::optional<WidgetViewer> viewer; bool preview = false; bool wallet = false; };
struct WidgetRequest { std::string data; bool wallet = false; };   // wallet: attach the wallet card (with_wallet)
struct WidgetRequestAnswer { std::optional<std::string> result; };
enum class WidgetRequestFailure { None, Timeout, RateLimited, Failed, Rejected, BadRequest };   // Rejected, BadRequest: wallet calls
struct WalletCall { Method method; WalletTransaction transaction; SignDataPayload payload; std::string data; };   // toJson(): TON Connect's shapes
struct WidgetRequestOutcome { WidgetRequestFailure failure = WidgetRequestFailure::None; WidgetRequestAnswer answer; };
// WidgetHostOptions: WidgetData data; WidgetContext context;
std::function<void(const WidgetRequest&, std::function<void(const WidgetRequestOutcome&)> respond)> onRequest;   // respond once, from any thread
std::function<void(const WalletCall&, std::function<void(const WidgetRequestOutcome&)> respond)> onWallet;      // §2.18, §5.13
std::function<void(const std::string& text)> onCopyText;
void setData(const WidgetData& data);
```

Storage strings keep a lone surrogate as WTF-8 (what QuickJS hands over), so
a value round-trips exactly; it is not strict UTF-8 in that case. Commits
already on their way reach `onStorageCommit` before `destroy()` returns.

### 4.4 Web: `WidgetHost` (`@telegram/widget-sandbox/host/widget-host`)

```ts
interface WidgetTheme { colorScheme: 'light' | 'dark'; params?: Partial<ThemeParams>; button?: ButtonTokens; text?: TextTokens }
interface TextTokens { fontSize?: number | string; lineHeight?: number | string; padding?: string }   // v0.1.1
type WidgetAction =
  | { kind: 'ready' }
  | { kind: 'openLink'; url: string }
  | { kind: 'openTelegramLink'; url: string }
  | { kind: 'haptic'; haptic: 'impact' | 'notification' | 'selection'; value?: string };
new WidgetHost(container, html, {
  theme,                                   // WidgetTheme, or a plain 'light' | 'dark'
  locale, storage: { local, session },     // the client's language and Web Storage preload
  contentSafeArea: { bottom, cornerRight, cornerLeft },   // the client's time over a widget with no caption below it (§2.17, §5.12); 0 by default
  onAction(action), onStorageCommit(area, values), onGestureClaim({ x, y, hold }),
  bubbleGestures, onBubbleGesture({ action, gesture, x, y, edge }),   // v0.1.2: the renderer plays the bubble (§5.5)
  onResize(height, anchor),                // anchor: 'top' | 'bottom', the widget's setResizeAnchor (v0.1.3)
  onWatchPublicData(on),                   // the widget started or stopped watching its shared state (§5.11): on a change
  data: { chatData, publicData },          // the chat state and the shared state as the message has them now
  context: { isCopy, canQuery, backgroundQuery, chatType, viewer: { id, firstName, lastName, username, isPremium }, preview, wallet },   // the server's flags (preview: §2.16; wallet: §2.18); fixed for the host's life
  onRequest({ data, wallet }),             // resolve with the server's answer { result } or reject 'TIMEOUT' | 'RATE_LIMITED' | 'FAILED'; wallet: attach the wallet card
  onWallet(call),                          // §2.18, §5.13: { method: 'sendTransaction', transaction, data } | { method: 'signData', payload, data }; resolve with the bot's answer or reject 'REJECTED' | 'BAD_REQUEST' | …
  onCopyText(text),
  policy, visible, onDead, onError, onLoaded, onDiag, rendererFactory, workerPool, features, …
})
host.resizeAnchor;                         // v0.1.3: the latest anchor, 'top' until the widget sets one
host.watchesPublicData;                    // the widget's latest watchPublicData / unwatchPublicData, false until then
host.setTheme(theme); host.setVisible(visible); host.setBubbleGestures(mode); host.destroy();
host.setContentSafeArea({ bottom, cornerRight, cornerLeft }); host.contentSafeArea;   // only with the bubble's layout, never per frame
host.setData({ chatData, publicData });    // the state changed (the server's update, the polled shared state): the full state
// @telegram/widget-sandbox/shared/gesture-policy: GestureArbiter, claimOf and the thresholds, for a client's own recognizers
```

The web client's page must also carry the CSP of invariant 6 (`connect-src`
only Telegram's endpoints, `form-action 'none'`, no third-party
`img-src`/`media-src`/`font-src`/`frame-src`).

### 4.5 Node harness (`packages/core/scripts/sandbox-node.ts`)

For tests and tooling, not for clients. `RunOptions` takes `theme`, `locale`,
`themeParams`, `button`, `text`, `storage` and `script` steps `{ theme, themeParams }`;
`RunResult` has `actions` (what `onAction` would get), `storageCommits`,
`resizeAnchor` and `resizeAnchors` (the latest anchor and each one the glue
accepted, v0.1.3; the CLI prints a `resize anchor:` line), `watchesPublicData`
and `publicDataWatches` (the same for `watchPublicData`; a `public data:` line) and `theme` (the
renderer's `theme` message, which `--ops-file` writes before the ops, then the
`safeArea` one). `contentSafeArea` and `script` steps `{ safeArea }` are the client's
time over the widget (§2.17; `RunResult.contentSafeArea`); from a shell
`--safe-area bottom[,cornerRight[,cornerLeft]]` (`--safe-area 24,64`, or `24,0,64` for a
right-to-left client).
Gestures are injected per event (`gesture: n`). Since v0.1.2 `width` (the bubble's,
default 400) and `gestures` (`tap`, `hold`, `double-tap`, `swipe`, `right-click`) run the
widget on the real renderer core in headless Chromium after load: `RunResult.gestures`
reports per press the element, its `touch-action` claim, the policy's outcome, the events
the widget received and a hint; `height` is the laid-out content height. From a shell:
`sandbox-node.ts widget.html --width 360 --hold 120,40 --double-tap 120,40 --swipe 60,80,120,0`
(`--desktop` for a desktop client's bubble, `--json` for the full result). With `width` the
renderer also counts heavy boxes (v0.6.0): `RunResult.paint` (`heavy`, `backdrop` at the end, `peakHeavy`,
`peakBackdrop`), a `paint: N heavy boxes (max 16), M with backdrop-filter (max 2)` line with a warning past 12, and
past the limit `stopped` with the renderer's reason, as a host stops the widget.

The widget's state and requests: `data`, `context` (`WIDGET_MESSAGE_KINDS` in
`sdk-policy.ts` has the server's flags per kind of message) and `onRequest`
(sync or a promise; its answers go into the widget at the next step of the
run), `script` steps `{ data }` (the client's `setData`) and `{ visible }` (a
hidden → visible step resets the background interval); `RunResult.requests`
(each request's data, whether it went out without a tap, the gate's verdict and
what went back) and `copiedTexts`. From a shell: `--message
private|group|via-bot|no-queries|live-copy|copy|preview`, or `--copy`, `--can-query`,
`--background-query`, `--chat-type` one by one; `--viewer id[,first,last,username,premium]`, `--chat-data`,
`--public-data`, `--answer '{"result":"…"}'` or `--answer-error TIMEOUT`,
`--set-data '{…}'`; the CLI prints a `request` line per request and a
`copyText` line per copy. The wallet (§2.18): `context.wallet` and `onWallet` (`RunResult.walletCalls`: each call
as checked, the gate's verdict, what went back); from a shell `--wallet`, `--wallet-answer '{"result":"…"}'` or
`--wallet-error REJECTED`, and a `wallet.<method>` line per call.

## 5. What the client implements

### 5.1 Theme

- Map the chat's theme to every key of §2.3 for the bubble the widget sits
  in: `bg_color` is that bubble's own fill, the colour the UA layer paints
  behind the widget. A widget the user sent or forwarded sits in an outgoing
  bubble: pass the outgoing bubble's colours (fill, text, hint, link,
  separator), not the incoming ones; custom chat themes and night mode the
  same way. A bubble with a gradient or a translucent fill has no single
  colour: pass the one the widget's area shows most (the client draws the
  bubble under a transparent widget view either way).
- Pass the button tokens of §2.4 from the client's own button, and the text
  tokens from its message text: `fontSize` = the chat's message font size,
  following the text-size setting; `lineHeight` and `padding` as the client's
  text bubble lays out text (the reference set 15 / 1.35 / `8px 12px` when
  unsure). Pass them at creation and call `setTheme` on every change.
- Pass the UI language as `locale`.

### 5.2 Links

- On `openLink(url)`: show a native confirmation that displays the **full
  URL** (not only the domain; the host in its ASCII form, as the framework's
  URL parser gives it), with Open and Cancel. On Open, go to the external
  browser (or the client's in-app browser, by its usual setting).
- On `openTelegramLink(url)`: no confirmation of its own; open it as a tapped
  `t.me` link in a message, through the client's own `t.me` handling, which
  keeps its own confirmations (joining a chat, a proxy, a login and the like).
  Telegram opens its own links without asking (decided 2026-10-29,
  `docs/decisions.md`, "Telegram links open without a confirmation").
- One dialog at a time; drop actions that arrive while one is shown.
- The framework guarantees at most one link action per pointer press.

### 5.3 Haptics

Play `impact`, `notification` and `selection` with the platform's generators
(iOS `UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator`,
`UISelectionFeedbackGenerator`; Android `View.performHapticFeedback` or
`VibrationEffect`); respect the system's haptics setting. Desktop and web
ignore them.

### 5.4 Storage

- Key: (account, chat, message id) of the message the widget belongs to (§2.8).
- At creation, pass the stored `local` area and the in-memory `session` area as
  `storage`.
- On `onStorageCommit(area, values)`: keep `values` as the new content of that
  area. Write `local` to disk with a short delay (for example 1 s) and at once
  when the widget is hidden or destroyed and when the app goes to the
  background or terminates. Keep `session` in memory only.
- Delete both areas with the message, with the chat or its history, with the
  account, and on "Clear cache". There are no per-bot quotas and no age-based
  eviction.
- The framework enforces the 4 KB quota; the client stores what it gets.

### 5.5 Gestures and drag (D4)

Who gets a press is decided by one shared policy, so every client and the sandbox
behave the same (v0.1.2, `docs/decisions.md` "Gestures: `touch-action` is the
claim" and, v0.1.3, "Vertical drags are the chat's"). Taps and **double taps
over the widget are always the widget's**, on every element: a double tap
reaches it as both clicks and then `dblclick`, and the client's double-tap
reaction stays on the bubble outside the widget. **Vertical drags are always
the chat's**: the list scrolls over every widget, whatever `touch-action` says,
and the widget gets `pointercancel`. Horizontal drags and holds are claimed per
element at `pointerdown` by CSS `touch-action`:

| over an element with | hold | horizontal drag | vertical drag |
|---|---|---|---|
| `touch-action: auto` (default), `manipulation` | bubble: message menu | bubble: swipe-to-reply / back | list scroll |
| `pan-y` | bubble | widget | list scroll |
| `pan-x` (claims nothing since v0.1.3) | bubble | bubble | list scroll |
| `none` | widget (`contextmenu`) | widget | list scroll (since v0.1.3) |

The first move past the slop locks the press's axis: a press that starts
horizontally on a claimed element is the widget's on both axes until the
finger lifts; one that starts vertically is the list's. A hold the widget has
won (on `none`) is locked the same way at its first move past the slop:
sideways it stays the widget's, up or down the list takes it and the widget
gets `pointercancel` after its `contextmenu`.

`touch-action` on a container covers its children, as in CSS (the ancestor
chain runs up to the widget's `<html>`). The leading 20 px of the widget (the
right edge in a right-to-left UI) take no claim (the system back gesture), and the bubble
outside the widget (caption, time, edges) is always the place for the menu and
the reaction. With a mouse: a
right click is the menu, or `contextmenu` for the widget on `none`; clicks,
double clicks, holds and drags are the widget's.

The policy is `GestureArbiter` (`packages/core/src/shared/gesture-policy.ts`;
`GesturePolicy.swift`, `GesturePolicy.kt`, `twidget/gesture_policy.h`), checked on
every platform against `packages/core/tests/fixtures/gesture-policy.json`:

| | value |
|---|---|
| touch slop (a tap or a hold stays within it) | 10 px |
| hold | 500 ms within the slop |
| double tap (`dblclick`) | the 2nd `pointerdown` ≤ 300 ms after the 1st `pointerup`, ≤ 40 px from the 1st `pointerdown` |
| axis lock | the dominant axis when the press leaves the slop (a tie is vertical) |
| edge strip | 20 px from the widget's leading edge |

What the client does:

1. On every primary `pointerdown` the host reports `onGestureClaim` (Kotlin and
   C++: `onGesture`): `x` (horizontal drags) and `hold`; `y` is always false
   since v0.1.3 (the arbiter ignores it). It arrives a few
   milliseconds after the touch starts; hold the bubble's recognizers until it
   does (or 150 ms pass, then treat it as no claim).
2. Feed the touch over the widget to a `GestureArbiter`: `down(t, x, y, claim,
   pointer, button)` with x from the widget's leading edge (`width - x` in a
   right-to-left UI) and y from its top, in CSS px (points, dp), then `move`,
   `up`, `cancel` (the platform cancelled it), and `tick` at `holdDeadline()`.
3. On a verdict with `cancel`: take the touch from the widget view the
   platform's way (iOS: a recognizer that begins with `cancelsTouchesInView`;
   Android: intercept in the parent, the WebView gets `ACTION_CANCEL`; desktop:
   end the press for the view), so the widget gets `pointercancel` and no
   `click`, and do `action`: `menu` (the message menu), `swipe` (swipe-to-reply,
   or back by its direction), `scroll` (the list), `back` (the system's or the
   chat's back gesture).
4. On a verdict for the widget: stand back for the rest of the press (no menu,
   no swipe, no list scroll from it: the axis is locked). Until then the list's
   own scroll must still be able to start on a vertical move: never keep the
   list from scrolling because of a claim. The double-tap reaction recognizer must not
   act over the widget at all. The renderer sends the widget its own `dblclick`
   / `contextmenu`; the client sends nothing.
5. No keyboard: the client never routes key events to the widget, and the
   chat's input field keeps the focus.

Platform notes, from the demo bubbles: on iOS a recognizer with
`cancelsTouchesInView` does not cancel a touch WKWebView already handles (WebKit
reads touches through its own recognizers); disabling and re-enabling the web
view's enabled gesture recognizers once, when the bubble takes the press, makes
WebKit send `pointercancel` (public API; the iOS demo's `BubbleGestures`). On
Android, intercept in the parent and send the child an `ACTION_CANCEL` (the
Android demo's `BubbleGestureLayout`). The Android WebView renderer keeps its
parent from intercepting only while a `hold` claim may still win (until the
press leaves the slop: vertically, the list takes it). The Blitz and litehtml
prototypes report only `wants`, always false: they claim nothing. On the web
there are no recognizers over the widget: `WidgetHost` with `bubbleGestures`
lets the renderer play the bubble with the same policy and reports what it took
(`onBubbleGesture`); the dev app and the feed demo do that by default
(`gestures`: phone, desktop or off). The browser's own panning is the list
scroll there: the renderer gives the engine every `touch-action` widened to
allow vertical panning (`none` → `pan-y`), so a touch screen scrolls the page
over any widget, while the claim reads the widget's own value.

### 5.6 Bubble size

- The size anchors `width` and `height` of `messageMediaWidget` (D12b; `0` =
  auto, each axis independent, `docs/tgw-format.md`). Width `0`: the widget
  takes the bubble's width; `W > 0`: a fixed-width widget, `W` clamped to
  [200, the bubble's maximum width], and the host is created with that width.
  Height `0`: the default placeholder before load, then the content height the
  host reports (`onChange` → `height`, web `onResize`) clamped to [50, 600] and
  cached per message; `H > 0`: a fixed height clamp(H, 50, 600) from the start,
  unchanged by the content. The four combinations are tabulated in
  `docs/integration.md` ("Bubble sizing"). 200 / 50 / 600 are the shared
  v0.1.0 constants, the same on every client; the hosts clamp to [50, 600] by
  default. Test it with a test bot sending `inputMediaWidget` with the anchors
  set.
- Content beyond the bubble's height (600 in auto mode, or the fixed height) is
  clipped: no inner scrolling (outside the profile), no fullscreen button
  (D12a).
- The widget view fills the bubble: no padding of the bubble's own around it
  (the widget's body margin, the `padding` text token, is the inset, §2.4), and
  the bubble clips the view at its corner radius, so a background the widget
  paints reaches the rounded edges.
- Text is not selectable unless the widget enables it with `user-select: text`
  (D18 since v0.1.1; §2.4, §5.10).
- **The resize anchor** (v0.1.3, §2.12): with every height the host reports the
  edge that stays put (`WidgetDiagnostics.resizeAnchor` next to `height`; web
  `onResize(height, anchor)`), `top` until the widget sets one. When an auto
  height changes, the list keeps that edge where it is on screen: for `top` the
  content below the bubble moves; for `bottom` the content above moves (in a
  list laid out from the top, add the height difference to the scroll offset;
  in an inverted chat list that grows from the bottom it is the natural
  behaviour). The same holds when the bubble opens at its cached height and the
  first report differs, and after the bubble is re-created (the anchor is the
  one the new run reports with its height). A bubble off screen keeps the
  visible content in place, as for any item that changes height. Keep the
  anchor with the cached height per message.
- During an animated height change, align the old pixels to the anchored
  edge: the widget view (or its snapshot) is pinned to the bubble's bottom for
  `bottom` and to its top for `top`, so the edge that stays put does not move
  while the bubble's other edge animates. A fixed height (`H > 0`) never
  changes, whatever the anchor.

### 5.7 Failures and restarts (D22)

- When the host reports `dead`, the last frame stays with the badge
  (`stoppedLabel`, localised by the client).
- The client may build a new host for the message the next time the bubble is
  shown; after 2 crashes in a row the widget stays stopped until the app
  restarts (D22). The HTML never changes after sending, so no edit brings a
  stopped widget back.

### 5.8 Visibility

Call `setVisible(false)` when the bubble leaves the screen and when the app
goes to the background, `setVisible(true)` when it is back.

### 5.9 The message: media, caption, flag

- The widget is `messageMediaWidget` (layer 233): inflate `payload` (zlib,
  RFC 1950, not gzip), no size limit of your own, hand the HTML to the host (`docs/tgw-format.md`,
  `docs/integration.md`). The HTML never changes after sending: a change of the
  widget's state goes to the live host with `setData` (§5.11); there is no new
  host for it.
- Rendering is enabled by the client's own platform/version flag; off, or
  below layer 233 (`messageMediaUnsupported`), the message shows as
  unsupported media with its caption. Keep that fallback.
- The media caption is the preview and search text; without one, the localized
  "Widget" stub. A widget with no caption below it (none, or one shown above it)
  gets the message time over its bottom corner (§5.12).
- Test with a test bot in a tester's private chat and forward into the shared
  test channel (a bot sends widgets to its private chats, to the basic groups
  and supergroups it is a member of, and via its inline mode; the channel gets
  forwards). Every conformance widget is sent that way as a layer-233 message, with its
  reference picture posted next to it (`packages/conformance/channel.json`,
  `docs/integration.md`, "Testing").

### 5.10 Text selection (v0.1.1)

Text a widget marked `user-select: text` is selectable; everything else is not
(§2.4).

- Touch: a long press over text (`touch-action: auto`) is the bubble's (§5.5),
  so selection inside a widget starts the way the client
  selects text in an ordinary message (its bubble menu's Select, or its own
  selection mode), and the renderer view then takes the selection gestures.
- Mouse (desktop, web): a drag over selectable text selects it; a press
  anywhere else still selects nothing and keeps the chat input's focus.
- Copy uses the client's normal copy command and menu.
- A native renderer (litehtml, TwidgetCore, the Swift renderer) must honour
  `user-select: text` to be conformant; until it does, the text stays
  unselectable and the widget still works.

### 5.11 The widget's state, requests and copies

The server's side is `docs/tgw-format.md`, "The widget's state and requests"
(the TL names there are the server's). The widget's HTML never changes; its
state does, and only the bot writes it: the chat state (`chat_data`) and the
shared state (`public_data`).

- **Create** the host with `data` = the message's state, `chat_data` and
  `public_data`, as the client has it now (absent = none), and `context` from
  the message, read, not derived: `isCopy` = `is_copy`, `canQuery` = `can_query`,
  `backgroundQuery` = `background_query` (the server computes them per
  message), `chatType` = the kind of chat the message is in, `viewer` = the
  account's user id. Pass them for every message; the framework drops the chat
  type and the chat state of any copy (a copy is told nothing about the chat it
  is in) and the viewer of any message without `canQuery`. A client that passes no flags gets no requests.
- **Changes of the state** reach the client two ways; pass the full new state
  to the live host each time (`setData`), no new host, no restart:
  - `updateMessageWidgetData` (the bot edited `chat_data` and/or
    `public_data`; in a supergroup it comes with the channel's pts, through the
    usual edit of channel posts): apply the chat state and the shared state it
    carries;
  - a shared widget's shared state (`public_data`; the media has a `widget_id`)
    changes for every carrier at once, and the client polls it **for widgets
    that watch** (`onWatchPublicData` / `watchesPublicData`, §2.13): while such
    widgets are on screen, call `messages.getWidgetPublicData` for one visible
    carrier per id, one message per call with its `hash` (the last one, 0 at
    first), again after the `period` each answer carries (the server's:
    5–3600 s, 30 s by default, also before the first answer), and apply a new
    shared state to every widget on screen with that id. A widget that does not watch is never polled; one
    without a `widget_id` has nothing to poll. This includes
    forwarded copies: on a copy `setData` changes only the shared state.
  Keep the state with the message, so a host created later gets it.
- **Requests.** On `onRequest`, call `messages.getBotWidgetQueryAnswer` (the peer,
  the message id in the user's own coordinates, `data` as UTF-8 bytes) and
  complete with the answer's `result` (absent: `{ result: null }`). Map the
  errors: `WIDGET_RESPONSE_TIMEOUT` → `timeout`, `FLOOD_WAIT` → `rateLimited`,
  anything else (`WIDGET_QUERY_NOT_ALLOWED`, `MESSAGE_ID_INVALID`, the network)
  → `failed`. Complete every request exactly once: the widget can send its next
  request only after it. Do not re-fetch the state after an answer: the bot's
  edits arrive the usual ways above, and a widget that needs them at once gets
  them in `result`. Whether a request came from a tap is not passed on: the
  server and the bot cannot tell either. The framework has already checked
  `data`, the flags, the tap or the background rules and the one-in-flight rule:
  the client only delivers. Show nothing of its own for a request: the answer
  has no toast, alert or link; the bot's `result` is the widget's to show.
- **copyText.** On `onCopyText`, copy the text and show the client's toast
  with the text itself ("Copied: …", truncated to the toast if long). No
  confirmation: the framework allowed it only on a tap.
- **The network marker.** Where the message has `can_query`, mark the widget
  as network-capable ("a tap may send data to the bot's server"); without it,
  no marker: the server refuses every request from that message
  (`docs/integration.md`, "The network marker").
- **Previews.** In a list of inline results, show a widget result
  (`botInlineMessageWidget`: its state, no flags) as a preview: create the host
  with `context.preview` and the result's `chat_data` / `public_data` as
  `data`, no viewer, no storage, no `onRequest` needed, no network marker; a tap
  on it is yours (it selects the result: route no touches to the widget view).
  When the user sends it, the sent message is a new widget: create a new host
  for it.
- **Forwards.** Forwarding a widget without its author is not offered (the
  server keeps the forward header anyway), so a widget always shows which bot it
  is from.

### 5.13 The wallet (§2.18)

- **`context.wallet`**: pass `true` when the client supports Gram Wallet and the wallet is available to this user
  (their country); the widget then gets `TelegramWidget.wallet` wherever it may ask the bot. Whether the user
  already has a wallet is not part of it: one is created when a widget asks for it.
- **A request with the wallet** (`WidgetRequest.wallet`): call `messages.getBotWidgetQueryAnswer` with
  `widgetQueryTypeData` and `with_wallet`: the server attaches the cloud wallet's card from the user's profile
  (`wallet_card`) to the bot's update, creating the wallet if the user has none. The framework let it through only
  on a tap.
- **`onWallet`**: show the client's existing TON Connect window over the chat, with the bot of the message in its
  header (name, username, avatar; never anything the widget says about itself) and the emulation of the
  transaction, or the text to sign (a warning for bytes and cells). Ignore touches on it for its first 0.5–1 s: the
  second tap of a double tap over the widget must not land on Confirm. If the wallet is empty, offer the top-up over
  the chat before sending; if `valid_until` has passed meanwhile, fail with `badRequest`. Decline → `rejected`;
  the wallet's refusal (network, sender, cells) → `badRequest`.
- **After the confirmation**: `sendTransaction` goes out the wallet's usual way (`wallet.sendTransfer`); then send
  the outcome to the bot as a widget query with `call.data`: `widgetQueryTypeSentTransaction` with the
  transaction's normalised message hash (`msg_hash`), or `widgetQueryTypeSignData` with the signature as TON
  Connect's result (`signature`, `address`, `timestamp`, `domain` = `bot<bot_id>`, `payload`), each with **the
  device's wallet** (`userWallet`: address, balance, public key, StateInit), the one that signed: with the wallet's
  backup off a device can hold another key and another wallet than the profile's card. Complete with the bot's answer
  as for a request. No second tap: the call already had one. A failure of that query maps as a request's.
- **The formats**, as the server takes them (the server team, 2026-10-30); `userWallet` reaches the bot as the client
  wrote it:
  - `userWallet.address`: the raw form (`0:` and 64 hex digits) or the user-friendly one (base64url, 48 characters,
    `UQ…` / `EQ…`), mainnet only: the server refuses a testnet form, parses either and checks it against
    `sign_data.address`. A bot parses it as well: the two forms are one address;
  - `userWallet.balance`: nanotons (1 TON = 10⁹), a whole number, the balance the device last knew (toncenter or the
    app's cache). For display only: the server checks its type alone, and a bot that needs the balance asks the chain;
    - `userWallet.public_key` and `userWallet.state_init` share `flags.0`: both or neither. Both with
    `widgetQueryTypeSignData` (the server requires the key and checks its length), and with
    `widgetQueryTypeSentTransaction` whenever the wallet SDK has them;
  - `userWallet.public_key`: the 32 bytes of the Ed25519 key, as bytes (not hex or base64), from the wallet SDK or the
    StateInit's data: the bot checks the signature with it;
  - `userWallet.state_init`: the wallet contract's StateInit as a bag of cells, what the TON SDK gives as
    `stateInit.toBoc()` (`wallet.createStateInit()`; a few hundred bytes for a standard wallet; the server takes about
    1 KB and does not look inside). Its hash is the address, and a standard wallet's data holds the key, so a bot ties
    the key to the address before the contract is deployed (a new wallet is not on chain until its first transfer, and
    there is no `connect` with `walletStateInit`). Once the wallet is deployed, the chain's `get_public_key` ties them
    too;
  - `msg_hash`: the normalised hash of the external message (TEP-467), 64 lowercase hex digits, by which toncenter
    and tonapi find the transaction;
  - `sign_data`: `signData`'s result as is: `signature` in base64, `address` in the raw form, `timestamp` in Unix
    seconds, `domain`, and `payload` as the widget sent it.
- The server's API (the query types, `with_wallet`, `userWallet`, the card in the updates; `docs/decisions.md`,
  "The wallet") is agreed and not shipped yet: until it is, leave `context.wallet` unset.

### 5.12 The time over a widget with no caption below it (§2.17)

- With a caption below the widget, draw the time in the caption's line as for any
  media and pass nothing (the content safe area stays 0). Without a caption, and
  with a caption shown above the widget (`invert_media`, as on a photo), draw the
  message time and its status (ticks, "edited", views) over the widget's bottom
  corner, on a
  backing as over a photo or a video (the widget's background can be any colour):
  the right corner in a left-to-right interface, the left one in a right-to-left
  one.
- Pass the band and the corner as the host's `contentSafeArea`, in points / dp /
  CSS px: `bottom` = the height of the band the time takes at the widget's
  bottom edge (the backing and its margins), `cornerRight` (or `cornerLeft` in a
  right-to-left interface) = the width of the corner the time takes in that band,
  the backing and its margins; `top`, `left` and `right` 0. Give them **at
  creation**, before the first render, so the first height the host reports
  already has the band.
- Change them with `setContentSafeArea` only when the bubble's layout changes: a
  caption added or removed by an edit, or moved above or below the widget,
  "edited" appearing, a views counter that grows wider. Never per frame and never for the chat's scroll: the floating date
  pill has no contract (draw it over the widget like over a photo).
- The time is a view over the widget: taps on it are the client's (its own
  hit test; on the web an element over the renderer), never forwarded to the
  widget. Nothing in the gesture policy changes.
- What the widget sees and how a widget handles it: §2.17. The renderer's UA
  layer pads an unstyled body by the band, so such a widget is that much taller
  with no caption below it.

### 5.14 Heavy boxes, with a renderer of your own (v0.6.0)

The framework's WebView renderer counts by itself. A client renderer counts the boxes it lays out that are heavy, by
the rule of `packages/core/src/shared/paint-policy.ts` on the computed style (a blurred shadow, a filter, a
backdrop-filter, opacity below 1 but a disabled or pressed `<button>`'s own dimming, or an animation of one of them), checks the fixture
`packages/core/tests/fixtures/paint-policy.json`, and past 16 (or 2 with backdrop-filter) calls the seam's
`limitExceeded(code, count)` before it draws that frame. It draws the stopped widget without those effects; the host
does the rest (`docs/integration.md`, "Heavy boxes").

## 6. Out of v1 and the decisions of 2026-10-17

Out of v1: sending messages on the user's behalf (requests to the bot and the
widget's state came in on 2026-10-25, §2.13–§2.14); sharing; payments; the viewer's
identity beyond the id (§2.13); popups; `MainButton`; per-bot
`DeviceStorage` and cloud storage; keyboard input; form controls other than
`<button>`; `TelegramWidget.platform`;
a `linkOpened` event; fullscreen; accessibility (D21); MCP Apps transport (D19,
later).

The questions this contract left open, as decided on 2026-10-17:

1. Haptics need no gesture: allowed while the widget is visible, with the rate
   limit of §2.6 (a bucket of 5, refilled at 10 per second). Since v0.2.2 they
   need a touch, or get one 1 s series without one (§2.6).
2. Limits: URLs at most 256 characters, at most 16 handlers per event type, and
   the widget stays stopped after 2 crashes in a row (D22, which said 2–3).
3. `openTelegramLink` takes any `t.me` path, as in Mini Apps; the client's own
   handler confirms the dangerous ones. No deny-list in the glue.
4. No `TelegramWidget.platform` in v1.
5. Double tap and long press always stay with the bubble (the reaction, the
   message menu): the widget gets single taps and, in v1, drags. *Replaced in
   v0.1.2*: double taps over the widget are always the widget's, and
   `touch-action: none` gives it holds too (§5.5, `docs/decisions.md` "Gestures:
   `touch-action` is the claim").
6. No `linkOpened` event in v1: the widget is not told whether the user opened
   the link.
7. The bubble size from the bot's `width` and `height` is tested end to end
   with a test bot (layer 233 ships with v0.1.0).

## 7. The framework milestone

SDK v1 is implemented in the framework as part of v0.1.0 (2026-10-17): the
shim, all five glue copies and the Node harness, with tests on every platform
(the policy fixture `packages/core/tests/fixtures/sdk-policy.json` in each
suite; `packages/core/tests/unit/sdk.test.ts`, `tests/sdk.spec.ts` —
`pnpm test:sdk` — and the platform suites through the real engine). The corpus
widgets that called `sendMessage` keep their state in `localStorage` or link
out with `openLink`, and the corpus READMEs say so; the attack files keep
calling it to check that it is gone.

The widget's state and requests (§2.13–§2.15, §5.11) are implemented in the
framework on `claude/widget-queries-25a3d2` (2026-10-25, for the next release):
the shim, all five glue copies and the Node harness, the policy's new sections
in `sdk-policy.json` on every platform, `packages/core/tests/unit/sdk.test.ts`,
`tests/sdk.spec.ts` and the `request-*` attacks (`tests/attacks.spec.ts`,
"requests to the bot") through the dev app's mock bot
(`apps/web/src/mock-bot.ts`), and the Swift, Kotlin and C++ host tests through
the real engine. The server side (`docs/tgw-format.md`, "The widget's state and
requests") is being built in parallel; clients wire `onRequest`, `setData` and
the polling to it once it ships.
