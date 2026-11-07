# Writing widgets

*The guide for widget authors and the models that write widgets for them
(v0.2.3, widget profile 1, SDK v1). The exact contracts: the profile feature by
feature in `docs/profile-v1.md` (generated from `packages/core/profile.json`),
the `TelegramWidget` API in `docs/sdk-v1.md` §2, the payload in
`docs/tgw-format.md`. A block for a model's system prompt, which stands alone, is at
the end, and `sandbox-node.ts --guide` prints it.*

## In one paragraph

A widget is **one self-contained HTML document** (or a bare fragment) with
inline CSS and JavaScript, sent by a bot in a message and drawn inside the
chat bubble. It has **no network access of any kind** and cannot navigate; no
library is bundled. It is drawn the same way on iOS, Android, macOS, desktop and
the web because every client removes what is outside the profile before it
reaches the renderer. The bubble is **200–600 px wide** (phones lay it out at
about 320–430) and at most 600 px tall; what does not fit is clipped, and
nothing scrolls inside a widget. A widget is fixed code plus changeable state:
the HTML never changes after sending, its state does, and only the bot writes
it.

## Size: mini-HTML

- At most **32 768 bytes of HTML** and **4096 bytes compressed**. The server
  compresses with zlib at level 9 (PHP `gzcompress($html, 9)`) and refuses
  anything larger with `INPUT_MEDIA_WIDGET_TOO_LARGE`.
- Measure as the server does: `python3 -c 'import sys,zlib; print(len(zlib.compress(sys.stdin.buffer.read(), 9)))' < widget.html`
  gives the same number (`gzip -9 -n` counts 12 bytes more). The harness
  (`sandbox-node.ts widget.html`, the line `size:`) and the dev app's stats
  panel count with Node's zlib, a few dozen bytes off the server's either way:
  near 4096, trust the Python number.
- What costs bytes: `data:` images barely compress; repeated inline styles and
  long class names compress well; SVG paths are cheap until they are long.

## The bubble

- **Size.** The bot sends `width` and `height` with the widget (`0` = auto, each
  axis on its own). Width `0`: the bubble's width; `W`: a fixed width clamped
  to [200, the bubble's maximum]. Height `0`: the content height clamped to
  [50, 600]; `H`: a fixed height clamp(H, 50, 600) that the content does not
  change. Above the height the content is clipped hard: no inner scrolling, no
  fullscreen. Lay out with `%`, flex or grid; never with `vh` (out of the
  profile).
- **The bubble is the canvas.** The widget view fills the bubble with no
  padding of its own, and the bubble clips it at its rounded corners. `html`
  has no background: the bubble's colour (`--tg-theme-bg-color`) shows through,
  and a background you set on `html` or `body` paints the whole bubble.
- **Defaults** (the renderer's own layer, `docs/sdk-v1.md` §2.4): the text is the
  client's message text (`--tg-text-font-size`, `--tg-text-line-height`), in
  `--tg-theme-text-color`; `body` has the client's text padding as its margin
  (`--tg-text-padding`, about `8px 12px`), so `body { margin: 0 }` goes edge to
  edge; headings, paragraphs, lists, tables, `pre`, quotes get native sizes
  and margins; a `<button>` you do not style looks like the client's own. Any
  CSS of yours wins over these defaults.
- **Theme.** The chat's palette is on the root as `--tg-theme-bg-color`,
  `--tg-theme-text-color`, `--tg-theme-hint-color`, `--tg-theme-link-color`,
  `--tg-theme-button-color`, `--tg-theme-button-text-color`,
  `--tg-theme-secondary-bg-color`, … and `--tg-color-scheme`; in JS
  `TelegramWidget.themeParams` and `colorScheme`. `prefers-color-scheme`,
  `color-scheme: light dark` and `light-dark()` follow the **chat** theme, live.
- **Text is not selectable** unless you say `user-select: text` (and
  `-webkit-user-select: text`) on what a user may want to copy.
- **The time in the corner.** Unless the message has a caption below the widget
  (none, or one shown above it), the client draws the message
  time over the widget's bottom corner (right, or left in a right-to-left
  client), on a backing, as on a photo; taps on it are the client's. Keep
  `env(safe-area-inset-bottom)` clear at the bottom (`padding-bottom:
  max(12px, env(safe-area-inset-bottom))`; the defaults pad an unstyled body for
  it, so when you pad for it yourself set `body { padding: 0 }` too, or you get
  two bands). Only the corner of that band is covered,
  `--tg-content-safe-area-corner-right` (or `-left`) wide, so content may run
  into the band on the other side. With a caption below, all of it is 0. In JS:
  `TelegramWidget.contentSafeAreaInset` (`docs/sdk-v1.md` §2.17); examples in
  `corpus/safe-area`.

## Input: who gets a press

Taps (`click`) and pointer events (`pointerdown`/`move`/`up`/`cancel`,
enter/leave/over/out, `setPointerCapture`) reach the widget, with the mouse
compatibility events. **Taps and double taps are always the widget's**, on every
element: a double tap arrives as both clicks and then `dblclick` (the chat's
reaction stays on the bubble outside the widget). **Vertical drags are always
the chat's**: the chat scrolls over every widget, whatever `touch-action` says.
Holds and horizontal drags are claimed **per element with CSS `touch-action`**,
decided when the finger goes down:

| over an element with | hold | horizontal drag | vertical drag |
|---|---|---|---|
| `auto` (the default) | the chat's message menu | swipe-to-reply | the chat scrolls |
| `pan-y` | menu | **the widget's** | the chat scrolls |
| `none` | **the widget's** (+ `contextmenu`) | **the widget's** | the chat scrolls |

`pan-x` claims nothing (it is `auto` here). The first move past 10 px decides
for the whole press, also after a hold: a drag that starts sideways on a
claimed element is the widget's on both axes until the finger lifts; one that
starts up or down is the chat's. When the chat takes a press, the widget gets
`pointercancel` and no `click`. The leading 20 px of the widget never take a claim (the system back
gesture), and the bubble outside the widget stays the chat's. With a mouse, a
right click follows the same claim; clicks, double clicks, holds and drags are
the widget's. Thresholds: a hold is 500 ms within 10 px, a double tap 300 ms and
40 px apart.

**Rule of thumb: a button you hold, a game control, a canvas → `touch-action:
none` on it; a horizontal slider, carousel or paddle → `pan-y`; everything else
stays `auto`**, so people keep the chat's menu and reply over the rest of the
widget. Nothing vertical can be dragged: no vertical sliders, faders or wheel
pickers, no up/down swipes, no 2D joysticks, drawing or map panning. Lay such
controls out sideways or use taps (buttons for up and down). When the chat takes
a press, undo what `pointerdown` started (put the value back, re-centre the
stick). `touch-action` on a container covers its children, as in CSS. Keep
controls at least 20 px from the widget's leading edge.

Move events may be coalesced per frame: their coordinates are the latest, while
`movementX` and `movementY` sum all moves for that event type and pointer.

No keyboard and no focus at all: no key events, no text input, `focus()` does
nothing. No touch events (use pointer events), no wheel or scroll events.

## What works

- **Markup:** text, headings, lists, tables, `<details>`/`<summary>`, `<a>`,
  `<img>` (`data:` PNG, JPEG, GIF, WebP), `<canvas>`, `<button>`, inline SVG
  (shapes, paths, text, `<use>`, gradients). `<form>` renders as a block, its
  controls are removed.
- **CSS:** flexbox, grid, tables, positioning (`fixed` is inside the bubble),
  box model, borders, radii, shadows, linear, radial and conic gradients, 2D
  transforms, `opacity`, `filter` / `backdrop-filter` with `blur()`,
  `brightness()`, `drop-shadow()`, transitions and `@keyframes`, custom
  properties, `calc()`/`min()`/`max()`/`clamp()`, `:hover` (desktop only),
  `:active`, structural pseudo-classes, `::before`/`::after`,
  `text-overflow: ellipsis`, `font-variant-numeric`, `touch-action`,
  `pointer-events`, `user-select`, `@media` on the width,
  `prefers-color-scheme`, `prefers-reduced-motion`, `@supports`. Fonts:
  `system-ui`, `sans-serif`, `serif`, `monospace`, `ui-*` only.
- **JavaScript:** ES2023, Promises, timers, `requestAnimationFrame`, the DOM
  (`querySelector`, `innerHTML`, `classList`, `style`, observers), layout reads
  (`getBoundingClientRect`, `getComputedStyle`, `offsetWidth`, `matchMedia`,
  `measureText`), canvas 2D, Web Animations (`element.animate`), `Intl` in the
  client's locale (`navigator.language`), `crypto.getRandomValues`,
  `TextEncoder`, `URL`, `structuredClone`, `localStorage` / `sessionStorage`.
  `new Image()` with a `data:` source loads (`load` / `error`, then
  `drawImage`) without being inserted; the `width` / `height` of an inserted
  image are its rendered size, a layout read (cache it in a drawing loop).
- **Element ids are not global variables** (a browser's named access): use
  `document.getElementById('x')`, not `x`.

## Out of the profile

Removed by every client before rendering: a declaration, a selector, an
element or an event that is out never reaches the renderer, the rest of the
widget still works. Detect anything newer with `CSS.supports`, `@supports`
or `TelegramWidget.isProfileAtLeast(n)`. `fetch`, `XMLHttpRequest`,
`WebSocket` and the like throw `NetworkDisabledError`; external `<script src>`,
stylesheets, fonts and images are never loaded. The full list, generated
from the profile:

<!-- profile:summary -->
*Generated from `packages/core/profile.json` (profile 1): 279 features, 201 in, 78 out; each one with how it is removed in `docs/profile-v1.md`.*

- **HTML elements and attributes** (20 in, 14 out): ruby, rt, rp, bdi, bdo · Legacy presentational elements: center, font, big, tt, strike, nobr · &lt;img src="data:image/svg+xml"> · &lt;img> with data: AVIF, BMP, ICO · &lt;img src="https://…"> (any non-data: URL) · Form controls: form, input, select, option, optgroup, textarea, label, fieldset, legend, datalist, output · progress, meter · dialog, popover attribute · audio, video, source, track · script, iframe, frame, object, embed, link, meta, base, portal, template, slot, math, fencedframe, … · align, valign, bgcolor, border, cellpadding, cellspacing, color, face, nowrap · contenteditable, spellcheck, inputmode, enterkeyhint, autocapitalize · draggable · tabindex, autofocus, accesskey
- **Inline SVG** (5 in, 5 out): clipPath, mask, pattern, marker · SVG filter primitives · SMIL animation: animate, animateTransform, animateMotion · foreignObject · &lt;image href="data:…"> inside SVG
- **CSS** (55 in, 21 out): display: flow-root, contents, list-item · Logical properties: margin-inline, padding-block, inset-inline, inline-size, … · Multi-column layout · position: sticky · overflow: auto / scroll · Colour functions: hwb(), lab(), lch(), oklab(), oklch(), color() · color-mix() · background-clip: text · url() / image-set() with anything but a data: image or #fragment · font-family naming a font · @font-face · -webkit-line-clamp / line-clamp · text-shadow · writing-mode, text-orientation · 3D transforms: rotateX/Y/3d, translateZ/3d, scaleZ/3d, matrix3d, perspective, transform-style, backface-visibility · filter: grayscale(), sepia(), saturate(), contrast(), hue-rotate(), invert(), opacity() · filter: url(#svg-filter) · mix-blend-mode, isolation, background-blend-mode · clip-path: inset(), circle(), ellipse(), polygon() · mask / -webkit-mask with gradients or data: images · vh / dvh / svh / vb: height-relative units
- **Selectors** (7 in, 6 out): :has() · ::first-letter, ::first-line, ::marker · :focus, :focus-visible, :focus-within · :checked, :disabled, :enabled, :valid, :invalid, :placeholder-shown, ::placeholder · :host, :host-context(), ::slotted(), ::part(), :scope · :target
- **At-rules** (5 in, 8 out): @media (hover: hover), (pointer: fine/coarse) · @media print, resolution, other media features · @import, @namespace · @container queries · @layer cascade layers · @property · @counter-style, @starting-style, @scope, @page, @font-feature-values, @font-palette-values, @position-try · CSS nesting
- **Animation** (7 in, 1 out): Magic move
- **Canvas 2D** (13 in, 8 out): createConicGradient · createPattern from a canvas or image · globalCompositeOperation · shadowBlur, shadowColor, shadowOffsetX/Y · ctx.filter · getImageData / putImageData / createImageData · toDataURL, toBlob, captureStream · WebGL, OffscreenCanvas, bitmaprenderer contexts
- **JavaScript runtime** (25 in, 9 out): element.focus() / blur() / select(), document.activeElement: the widget takes keyboard focus · Bundled libraries by CDN name: Chart.js 4, D3 7 · fetch, XMLHttpRequest, WebSocket, EventSource, RTCPeerConnection, sendBeacon, importScripts, Worker, BroadcastChannel · location assignment, history, window.open, form submission · alert, confirm, prompt, print · clipboard, share, vibrate, geolocation, mediaDevices, Notification · attachShadow, customElements.define · Blob, FileReader, URL.createObjectURL · requestFullscreen
- **TelegramWidget (SDK v1)** (17 in, 1 out): TelegramWidget.sendMessage
- **Input** (9 in, 5 out): touchstart / touchmove / touchend · Key events: keydown / keyup / keypress, beforeinput / input from keys, focus / blur / focusin / focusout from keys · Enter / Space activate a focused button or link · A key press counts as a user gesture for privileged bridge calls · wheel and scroll events
- **Limits** (21 in, 0 out)
- **Client behaviour** (17 in, 0 out)
<!-- /profile:summary -->

## `TelegramWidget` in brief

```js
TelegramWidget.profile;                      // 1; isProfileAtLeast(n)
TelegramWidget.colorScheme;                  // 'light' | 'dark', the chat's
TelegramWidget.themeParams.bg_color;         // the palette, '#rrggbb' (Mini Apps keys)
TelegramWidget.onEvent('themeChanged', fn);  // also 'activated' / 'deactivated'; isActive
TelegramWidget.openLink('https://…');        // in a tap handler: the user confirms the full URL
TelegramWidget.openTelegramLink('https://t.me/…'); // in a tap handler: opens inside Telegram, no confirmation
TelegramWidget.HapticFeedback.impactOccurred('light'); // notificationOccurred, selectionChanged
TelegramWidget.ready();                      // optional: first state drawn
TelegramWidget.setResizeAnchor('bottom');    // v0.1.3: grow upward ('top', the default: downward)
TelegramWidget.contentSafeAreaInset;         // { bottom, cornerLeft, cornerRight, … } px: the client's time over the corner; 'contentSafeAreaChanged'
TelegramWidget.chatData;                     // the chat state (this message, this chat), or null; publicData: the shared state
TelegramWidget.onEvent('chatDataChanged', fn); // and 'publicDataChanged': the bot changed the state: redraw from it
TelegramWidget.request('vote:2').then((a) => a.result, (e) => e.code); // ask the bot, in a tap handler
TelegramWidget.copyText('PROMO-2026');       // in a tap handler; the client shows what it copied
TelegramWidget.request('pay:1', { wallet: true }); // in a tap handler: the bot gets the user's wallet with it
TelegramWidget.wallet.sendTransaction(tx, 'paid:1'); // Gram Wallet: TON Connect's transaction; wallet.signData(payload, data); null where unavailable
TelegramWidget.isForward; TelegramWidget.viewer; TelegramWidget.chatType; // a forward? who looks ({ id, first_name, username, … }, only if you may ask the bot), where
```

- **Links:** `https:` only, at most 256 characters, one call per tap, only in
  the handler of a real tap (not from a timer or a promise; nor from a finger's
  `pointerdown`, which may still become the chat's scroll: use `click` or
  `pointerup`); a bad URL throws `TypeError`. A tapped `<a href="https://…">`
  does the same. `openLink` shows the user the full URL to confirm;
  `openTelegramLink` opens the `t.me` link inside Telegram without a
  confirmation, as a link in a message does. The widget is not told whether the
  user opened it.
- **Storage:** `localStorage` is kept for this message on this device, there
  from the first line of script, 4 KB per area (`QuotaExceededError` past
  it: catch it); `sessionStorage` until the app restarts. A forwarded copy
  starts empty. Your HTML never changes after the bot sends it: the bot changes
  only the widget's state (below).
- **Haptics:** only while visible, 5 in a burst then 10 per second, freely
  during a touch and 5 s after it; without a touch one 1 s series, the next one
  after a touch or 10 s later (a timer cannot buzz the phone); desktop and web
  play nothing.
- **The widget's state**: only the bot writes it; you read it and cannot
  change it, only ask the bot (`request`, below). `chatData` is the **chat
  state**: this message in its chat (in a group every member sees the same; a
  forward never gets it), for a game in progress or whose turn it is.
  `publicData` is the **shared state**: wherever the widget is shown (a forward
  gets it as it was then, or, in a shared widget, the one state the bot keeps
  changing for all of them), for poll results or a price. Each is a text of up
  to 1024 bytes (UTF-8), `null` when there is none. Keep what everyone sees there
  (votes, a board, a score) and draw it per `viewer.id`; when the bot changes it
  you get `chatDataChanged` / `publicDataChanged`: redraw, nothing restarts.
- **Live shared state**: call `TelegramWidget.watchPublicData()` if the bot
  sent the widget as a shared one (`is_shared`) and keeps changing its shared
  state (a ticker, a poll others vote in): only then does the client poll it
  while the widget is on screen, at the server's period (5–3600 s, 30 s by
  default). Without it you get the shared state with the message and the bot's
  edits of this message only.
- **Keep the newest state.** A poll can bring the shared state back older than
  what you already drew from a `result` or an earlier poll, and the counts would
  jump back and forth. Have the bot put a revision in the shared state (`"r":
  41`, raised on every edit, and in `result` too) and ignore anything older than
  the newest you have seen.
- **A forward of a widget that is not shared never changes**: it keeps the
  shared state as it was when forwarded. Draw it as a snapshot ("Cooking, as of
  19:40"), not as live progress the reader would wait on.
- **Requests**: `request(data)` is a callback button inside the widget: `data`
  1–64 bytes; the promise resolves with `{ result }` (the bot's answer for you,
  up to 1024 bytes, or `null`) or rejects with `error.code`: `NOT_ALLOWED` (no
  tap, a widget the bot did not allow to ask (`allow_queries`), a forward that may not ask), `BUSY` (one at a time), `RATE_LIMITED`,
  `TIMEOUT` (the bot did not answer), `FAILED`, `INVALID_ANSWER`. Call it in a
  tap handler (one call per tap, shared with links and `copyText`). The answer
  does not change the state: the bot's edits arrive as the events; if you need
  the new state at once, have the bot put it in `result`. In the private chat
  with the bot it may also run without a tap: once when the widget appears on
  screen, then once per 5 s while it is visible (poll a score); one asked while
  the widget is off screen (at start, before the client shows the bubble) waits
  and goes out when it appears, so ask for fresh state in the script's first
  lines. Elsewhere (groups, via the bot, forwards) only on a tap. Show
  "sending…" and handle every code.
- **A forward** (`isForward`) has no chat state (`chatData` is `null`) and no
  `chatType` (a copy is told nothing about its chat). It can ask on a tap only
  if the bot let forwards ask (a shared widget sent with `allow_queries`);
  otherwise it has no `viewer` either and every
  request is `NOT_ALLOWED`: show the shared state and point to the bot
  (`openTelegramLink('https://t.me/<bot>?start=…')`).
- **copyText**: `copyText(text)`, 1–256 characters, in a tap handler; the
  client copies and shows the text it copied.
- **The wallet** (Gram Wallet, TON Connect's formats): `TelegramWidget.wallet`
  is `null` where the client has no wallet for this user, where the widget may
  not ask the bot, and in a preview: check it before drawing a pay button. To
  pay: on a tap, `request('pay:1', { wallet: true })` (the bot gets the user's
  wallet address with it, a wallet is created if they had none), the bot
  answers with a fresh transaction (TON Connect's `{ valid_until, messages:
  [{ address, amount, payload?, stateInit? }] }`, 1–255 messages, a
  user-friendly `address`, `amount` in nanotons as a string), and you pass it
  on in the answer's handler:
  `wallet.sendTransaction(tx, 'paid:1')` needs no new tap within 10 s of the
  answer. The client's own window shows the bot and the amounts; the user
  confirms; the bot gets the outcome and checks the chain; the promise resolves
  with the bot's answer `{ result }`. `wallet.signData({ type: 'text', text },
  'terms')` asks for a signature the same way (or `{ type: 'binary', bytes }`,
  `{ type: 'cell', schema, cell }` in base64): it goes to the bot. One wallet
  call per tap or per answer, one call in flight with requests, never from a
  timer. Extra codes: `REJECTED` (the user declined), `BAD_REQUEST` (the wallet
  refused it); a malformed transaction or payload throws `TypeError`. After
  `TIMEOUT` the payment may have gone out: ask the bot about the order, do not
  pay again.
- **A preview** (`isPreview`): sent via the bot's inline mode, the widget is
  first shown in the list of results. There it has its state but gets no taps,
  cannot ask, link or copy, knows no viewer and keeps nothing in Web Storage:
  draw the state the user is about to send.
- **Resize anchor** (v0.1.3): when your height changes, the client keeps one
  edge of the bubble still on screen. `'top'` (the default) grows and shrinks
  downward: answers that expand, "show more", a summary that opens.
  `'bottom'` keeps the bottom still and grows upward: a chat log or thread where
  new lines appear at the bottom and older ones move up. Call it before the
  change, at the start of the script if the widget always grows one way; it
  applies until you call it again. Only `'top'` and `'bottom'` (anything else
  throws `TypeError`); older clients lack it:
  `if (typeof TelegramWidget.setResizeAnchor === 'function') …`.
- **Gone:** `sendMessage` (nothing is sent to the chat; ask the bot with
  `request`), `alert` / `confirm` / `prompt` (no dialog), `navigator.clipboard`
  (use `copyText`), share, fullscreen.
- Outside Telegram (a plain browser) `window.TelegramWidget` is undefined:
  check for it.

## Lifecycle and limits

- Off screen: `document.hidden`, no animation frames, timers at most once per
  second. The widget can be destroyed and recreated at any time; only Web
  Storage and the widget's state survive: keep the user's own choices in
  `localStorage`; what the whole chat sees (a board, the votes) belongs in the
  chat state, which the bot writes.
- CPU: a budget of 300 ms per second (timers and frames wait beyond it). One
  task may run up to 500 ms, the first run included; one that runs longer stops
  the widget. A task that runs past the second's 300 ms finishes, and what it
  spent past it makes the next timers and frames wait longer. One task may make
  128 layout reads (and their time is at most 1000 ms), and what the widget costs the client's main
  thread (its layout reads plus about 25 µs per DOM change it sends) is capped
  at 100 ms per second: past it timers and frames wait. 8 MB of memory; 64 live
  timers (`RangeError` past it);
  2000 nodes (elements, text, comments); one text value 32 K characters, the document's text 48 K, an
  attribute 8 K; 10 000 element method calls (`scrollTo`, …) in one task; 400 CSS rules and 32
  `@keyframes`; 4000 canvas commands per canvas in one task (the next throws
  `RangeError`); 64 running script animations; 2048 × 2048 pixels for all of
  a widget's canvases together (a canvas is 300 × 150 until you size it). Past a
  count or size limit the widget stops, as past the CPU or memory limit: a
  widget over a limit or crashed keeps its last frame with a "Widget stopped"
  badge.
- A `data:` image: PNG, JPEG, GIF or WebP of at most 1024 × 1024 pixels (larger:
  the placeholder); other bytes behind an image type are a broken image.
- Paint: at most 16 elements at once with a blurred shadow, a `filter`, a
  `backdrop-filter` or transparency (`opacity` below 1), animated ones included
  (a `::before` / `::after` counts as an element of its own), and of them at most
  2 with `backdrop-filter`; past it the widget stops (`Paint limit exceeded`).
  Mute text and small details with colour (`--tg-theme-hint-color`, `rgba()`,
  `fill-opacity` / `stroke-opacity` in SVG), not `opacity`: it looks the same and
  costs nothing. A shadow without blur (a ring, an inset border), an element
  under `display: none` and the dimming of a disabled or pressed `<button>`
  (its `opacity`) do not count.
- Keep the first render synchronous and fast; animate with
  `requestAnimationFrame`, never read layout in every frame.

## The bot's side (Bot API 10.4)

The names a bot uses; the whole mapping and what the beta does are in
`docs/tgw-format.md`, "The Bot API (10.4)".

- **Send:** `sendWidget` with `html`, `width`, `height` (`0` or none = auto),
  `chat_data`, `public_data`, `caption`, `allow_queries` (taps may ask, and in
  the private chat with the bot the widget may ask in the background),
  `is_shared` (one shared state for every copy; the sent `Widget` has an `id`;
  with `allow_queries` forwards may ask too) and `reply_markup` (an inline
  keyboard under the widget). `sendSharedWidget(widget_id, chat_data)` posts the
  same shared widget again. In inline mode: an article with
  `InputWidgetMessageContent` (the same fields) or
  `InputSharedWidgetMessageContent` (`widget_id`). `copyMessage` of a widget is
  refused; a forward is the way to pass it on.
- **Answer:** a request from an original arrives as the update `widget_query`
  (`from`, `chat` and `message_id` or `inline_message_id`, `data`, and
  `widget_id` for a shared widget); one from a copy of a shared widget as
  `shared_widget_query` (`from`, `data`, `widget_id`). Answer every one with
  `answerWidgetQuery(widget_query_id, data)` (`data` = `result`, at most 1024
  bytes), also when something failed: an unanswered request ends in the widget
  as `TIMEOUT`.
- **Change the state:** `editMessageWidgetData` (`chat_id` and `message_id`, or
  `inline_message_id`; `chat_data`, `public_data`) for this message;
  `editSharedWidgetData(widget_id, public_data, cache_time)` for a shared
  widget's state everywhere (`cache_time`: the clients' poll period, 5–3600 s).
  An edit that changes nothing fails with `message is not modified`: take it as
  done. Put the user's move into your own state only once the edit went
  through, so a failed edit does not take a turn away.
- **Examples:** `corpus/bot-demos` (an order, a shared poll, who's in, a duel,
  a live quote, a shared leaderboard; the protocol of each in its README) and
  the bot that sends them, `scripts/bot-demos.ts`.

## How to test

- **The harness** (no browser window, prints text for people and agents):
  `corepack pnpm tsx packages/core/scripts/sandbox-node.ts widget.html` runs the
  widget in the real sandbox and prints the size, what the profile removed
  (`diag` lines), errors and the actions it asked for. Add `--width 360` to lay
  it out in the real renderer at that bubble width (the content height), and
  presses to check gestures: `--tap x,y`, `--hold x,y[,ms]`,
  `--double-tap x,y`, `--swipe x,y,dx,dy`, `--right-click x,y` (`--desktop`
  for a mouse client). Each press reports the element, its claim, who got it,
  what the widget received and a hint, for example
  `hint: the hold went to the message menu and the widget got pointercancel: set touch-action: none on button#fire …`
  or `hint: vertical drags always scroll the chat; a widget cannot claim them …`.
  `--json` gives the whole result. The bot: `--message private|group|via-bot|no-queries|live-copy|copy|preview`
  sets where the widget is (what it may ask), `--viewer id,first,last,username,premium`, `--chat-data`,
  `--public-data` the rest, `--answer '{"result":"ok"}'` (or `--answer-error
  TIMEOUT`) answers every request, `--set-data '{…}'` is the bot's edit of the state, `--offscreen`
  starts the widget off screen and then shows it, as Telegram's clients do; a `request`
  line per request says whether it went out and what came back. With `--width` a
  `paint:` line counts the elements with a blurred shadow, a filter or transparency
  (16 at most; a warning past 12).
- **The dev app** (`corepack pnpm dev`): drop the file, pick a width (200–600 or
  free), toggle dark, and use the mouse as a finger (`gestures: phone`, the
  default): the bubble takes holds and swipes as a phone does and says so over
  the bubble. The **Bot (mock)** panel plays the bot: where the message is (the
  private chat, a group, via the bot, sent without requests, a live or plain
  forward, a preview), the chat type, the viewer, the chat state, the shared
  state and the bot's edit of them, how it answers (a built-in bot, manual, or a
  failure); `corpus/interactive` has a poll, tic-tac-toe, a live ticker and a
  promo code that use it, and `corpus/bot-demos` the six demos of the real bot.
- **On clients:** send it with your bot (`sendWidget`, above) to your private
  chat, a group and through inline mode, and forward it: each place gives the
  widget different rights. `scripts/bot-demos.ts` is a bot to start from.

## Agent block

For a model's system prompt (also `sandbox-node.ts --guide`). It stands alone: a
model that sees only this block, a bot's model or a cloud agent without this
repository, has everything it needs to write a correct widget; the links are for
depth. `{docs}` stands for the authors' documentation site, not published yet.
The out list between the `profile:agent` markers is generated from
`packages/core/profile.json` by `profile-build.ts` (`--guide` drops the marker lines).

<!-- agent-block -->
```text
You write Telegram widgets. A widget is one self-contained HTML document (or a
fragment) with inline <style> and <script>, sent by a bot in a message and drawn
inside the chat bubble on every Telegram client (iOS, Android, desktop, web).
Every client silently removes what is outside the widget profile below and runs
the rest: an out feature is a bug you will not see. Docs: {docs}/guide,
{docs}/sdk (the API), {docs}/profile (every feature).

SIZE
- At most 32768 bytes of HTML and 4096 bytes after zlib level 9; the server
  refuses anything bigger. Python's zlib.compress(html, 9) counts as the server
  does; if you cannot measure, aim for 3800. Repeated CSS and markup compress
  well; data: images and long unique text do not. No comments, no dead code.

THE BUBBLE
- 200-600 px wide (phones about 320-430: design for 320), as tall as the
  content, at most 600 px; the rest is clipped. Nothing scrolls inside a
  widget: overflow auto/scroll becomes hidden, position: sticky becomes
  relative. Fit long content into 600 px with pages, tabs or "show more".
- Lay out with %, flex, grid, vw/vmin; never vh (removed).
- html has no background: the bubble's colour shows through, and a background
  on html or body paints the whole bubble. body has the client's text padding
  as its margin (about 8px 12px); body{margin:0} goes edge to edge.
- Defaults: the client's message text (system-ui, --tg-text-font-size about
  15px, --tg-text-line-height) in --tg-theme-text-color; headings, lists,
  tables, pre get native margins; an unstyled <button> looks like the client's
  own button. Any CSS of yours wins.
- Theme: CSS variables on the root, each '#rrggbb': --tg-theme-bg-color,
  --tg-theme-text-color, --tg-theme-hint-color, --tg-theme-link-color,
  --tg-theme-button-color, --tg-theme-button-text-color,
  --tg-theme-secondary-bg-color, --tg-theme-accent-text-color,
  --tg-theme-subtitle-text-color, --tg-theme-destructive-text-color,
  --tg-theme-section-bg-color, --tg-theme-section-header-text-color,
  --tg-theme-section-separator-color; --tg-color-scheme is light or dark.
  prefers-color-scheme, color-scheme and light-dark() follow the chat's theme,
  live. Build colours on these so both themes work.
- Fonts: system-ui, sans-serif, serif, monospace, ui-sans-serif, ui-serif,
  ui-monospace, ui-rounded only; font names and @font-face are removed.
- Text is not selectable unless you set user-select:text and
  -webkit-user-select:text on what a user may want to copy.
- Unless a caption is below, the client draws the message time in the bottom corner:
  keep env(safe-area-inset-bottom) clear at the bottom (the defaults pad an
  unstyled body; if you pad for it, set body{padding:0}); only
  --tg-content-safe-area-corner-left|right of it is
  covered, so content may enter the band on the other side.
More: {docs}/guide#the-bubble

WHAT WORKS
- HTML: div, span, p, h1-h6, ul/ol/li, dl, table, details/summary (toggle
  event), figure, header/section/nav/article, b/i/em/strong/small/code/pre/
  mark/time/abbr, a, img (data: PNG, JPEG, GIF, WebP only), canvas, button.
- Inline SVG: svg, g, path, rect, circle, ellipse, line, polyline, polygon,
  text, tspan, defs, symbol, use, linearGradient, radialGradient, stop.
- CSS: flex, grid, positioning (fixed = inside the bubble), box model,
  borders, border-radius, box-shadow, opacity, linear/radial/conic gradients
  (and repeating), 2D transforms, filter and backdrop-filter with blur(),
  brightness() and drop-shadow() only, transitions, @keyframes, custom
  properties, calc/min/max/clamp, rgb()/hsl()/hex/light-dark() colours,
  ::before/::after, :hover (mouse only), :active, :not/:is/:where, structural
  pseudo-classes, [disabled] and :disabled on buttons, @media on width,
  aspect-ratio, orientation, prefers-color-scheme, prefers-reduced-motion,
  @supports, one-line text-overflow: ellipsis, touch-action, pointer-events.
- JS: ES2023 in an interpreter (no JIT: keep per-frame work small), Promises,
  async/await, setTimeout/setInterval, requestAnimationFrame, the DOM
  (createElement, querySelector, innerHTML, textContent, classList, style,
  dataset, addEventListener), MutationObserver, ResizeObserver, layout reads
  (getBoundingClientRect, getComputedStyle, offsetWidth, clientWidth, an
  inserted img's width: each is a round trip to the renderer, so cache them
  and never read them every frame), matchMedia, canvas 2D (paths, rect,
  roundRect, arc, fillText, measureText, gradients, setTransform, clip,
  drawImage of a data: image; scale by devicePixelRatio for sharp lines),
  new Image() with a data: src (load/error, then drawImage), Web Animations
  (el.animate), Intl and toLocale*String in the user's locale
  (navigator.language), crypto.getRandomValues, TextEncoder, URL,
  structuredClone, localStorage, sessionStorage, CSS.supports.
- Element ids are not global variables: use document.getElementById('x').

OUT OF THE PROFILE (removed by every client; the rest of the widget still runs)
<!-- profile:agent -->
- HTML: ruby, rt, rp, bdi, bdo; Legacy presentational elements: center, font, big, tt, strike, nobr; <img src="data:image/svg+xml">; <img> with data: AVIF, BMP, ICO; <img src="https://…"> (any non-data: URL); Form controls: form, input (all types), select, option, optgroup, textarea, label, fieldset, legend, datalist, output; progress, meter (native gauges); dialog (show/showModal), popover attribute; audio, video, source, track; script (as DOM), iframe, frame, object, embed, link, meta, base, portal, template, slot, math, fencedframe, …; align, valign, bgcolor, border, cellpadding, cellspacing, color, face, nowrap; contenteditable, spellcheck, inputmode, enterkeyhint, autocapitalize; draggable (HTML drag and drop); tabindex, autofocus, accesskey (keyboard focus and shortcuts).
- SVG: clipPath, mask, pattern, marker; SVG filter primitives (fe*); SMIL animation: animate, animateTransform, animateMotion; foreignObject (HTML inside SVG); <image href="data:…"> inside SVG.
- CSS: display: flow-root, contents, list-item; Logical properties: margin-inline, padding-block, inset-inline, inline-size, …; Multi-column layout (columns, column-count, column-gap); position: sticky; overflow: auto / scroll (scrolling inside the widget); Colour functions: hwb(), lab(), lch(), oklab(), oklch(), color(); color-mix(); background-clip: text (gradient text); url() / image-set() with anything but a data: image or #fragment; font-family naming a font ("Helvetica", "Courier New", "Comic Sans MS", …); @font-face (web fonts); -webkit-line-clamp / line-clamp (multi-line ellipsis); text-shadow; writing-mode, text-orientation (vertical text); 3D transforms: rotateX/Y/3d, translateZ/3d, scaleZ/3d, matrix3d, perspective, transform-style, backface-visibility; filter: grayscale(), sepia(), saturate(), contrast(), hue-rotate(), invert(), opacity(); filter: url(#svg-filter); mix-blend-mode, isolation, background-blend-mode; clip-path: inset(), circle(), ellipse(), polygon(); mask / -webkit-mask with gradients or data: images; vh / dvh / svh / vb: height-relative units.
- Selectors: :has(); ::first-letter, ::first-line, ::marker; :focus, :focus-visible, :focus-within; :checked, :disabled, :enabled, :valid, :invalid, :placeholder-shown, ::placeholder; :host, :host-context(), ::slotted(), ::part(), :scope; :target.
- At-rules: @media (hover: hover), (pointer: fine|coarse); @media print, resolution, other media features; @import, @namespace; @container queries (container-type in the widget); @layer cascade layers; @property (typed custom properties); @counter-style, @starting-style, @scope, @page, @font-feature-values, @font-palette-values, @position-try; CSS nesting (.a { .b { } }, &).
- Animation: Magic move (shared-element morph between renders).
- Canvas: createConicGradient; createPattern from a canvas or image; globalCompositeOperation (source-over, destination-out, multiply, …); shadowBlur, shadowColor, shadowOffsetX/Y; ctx.filter (CSS filter on canvas drawing); getImageData / putImageData / createImageData (pixel read-back); toDataURL, toBlob, captureStream; WebGL, OffscreenCanvas, bitmaprenderer contexts.
- JS: element.focus() / blur() / select(), document.activeElement: the widget takes keyboard focus; Bundled libraries by CDN name: Chart.js 4, D3 7 (nothing is fetched); fetch, XMLHttpRequest, WebSocket, EventSource, RTCPeerConnection, sendBeacon, importScripts, Worker, BroadcastChannel; location assignment, history, window.open (→ openLink), form submission; alert, confirm, prompt, print; clipboard, share, vibrate, geolocation, mediaDevices, Notification; attachShadow, customElements.define; Blob, FileReader, URL.createObjectURL; requestFullscreen.
- TelegramWidget: TelegramWidget.sendMessage.
- Input: touchstart / touchmove / touchend (touch clients only); Key events: keydown / keyup / keypress, beforeinput / input from keys, focus / blur / focusin / focusout from keys; Enter / Space activate a focused button or link (a click from the keyboard); A key press counts as a user gesture for privileged bridge calls; wheel and scroll events.
<!-- /profile:agent -->
More: {docs}/profile

INPUT
- Taps (click) and pointer events (pointerdown/move/up/cancel, enter/leave,
  setPointerCapture), with the mouse events (a coalesced move sums
  movementX/Y). No touch events (use pointer events), no keyboard, no focus, no
  text input, no wheel or scroll events.
- A double tap is always the widget's: two clicks, then dblclick.
- Vertical drags always scroll the chat: nothing can be dragged up or down (no
  vertical sliders, wheel pickers, joysticks, drawing or map panning); use
  horizontal controls or buttons.
- Holds and horizontal drags are claimed per element with CSS touch-action,
  which covers the element's children: auto (the default): a hold opens the
  chat's message menu, a sideways swipe is the chat's reply, and the widget
  gets pointercancel and no click. pan-y: the widget gets horizontal drags
  (sliders, carousels, paddles). none: horizontal drags and holds, plus
  contextmenu (held game buttons). pan-x claims nothing. Keep auto everywhere
  else. On pointercancel undo what pointerdown started.
- The leading 20 px never take a claim (the system back gesture): keep
  controls 20 px from the left edge (the right in RTL).
- A hold is 500 ms within 10 px; a double tap is two taps within 300 ms and
  40 px.
More: {docs}/guide#input

TELEGRAMWIDGET (window.TelegramWidget; undefined outside Telegram: check it)
- profile (1), isProfileAtLeast(n). colorScheme: 'light' | 'dark'.
  themeParams: { bg_color, text_color, hint_color, ... } ('#rrggbb', the keys of
  the CSS variables with _ for -).
- onEvent(type, fn) / offEvent(type, fn), type: 'themeChanged', 'activated' /
  'deactivated' (on and off screen; isActive), 'chatDataChanged',
  'publicDataChanged'.
- ready(): optional, once the first state is drawn.
- openLink(url): https only, at most 256 characters; the client shows the full
  URL and the user confirms. openTelegramLink(url): https://t.me/..., opens
  inside Telegram without a confirmation, as a t.me link in a message. A tapped
  <a href="https://..."> does the same. A bad URL throws TypeError.
- copyText(text): 1-256 characters; the client copies it and shows a toast.
- openLink, openTelegramLink, copyText and request need a real tap: call them
  synchronously in a click (or pointerup) handler, not after await, a timer or
  a promise, nor a finger's pointerdown (it may still become the chat's
  scroll); one of them per tap. Otherwise they are refused.
- HapticFeedback.impactOccurred('light'|'medium'|'heavy'|'rigid'|'soft'),
  .notificationOccurred('error'|'success'|'warning'), .selectionChanged():
  only on screen, free during a touch and 5 s after it; without a touch one
  short series, then nothing for 10 s (a timer cannot buzz the phone).
- setResizeAnchor('top' | 'bottom'): the edge of the bubble that stays still
  when your height changes ('top' by default; 'bottom' for a log or thread
  that grows at the bottom). Older clients lack it:
  TelegramWidget.setResizeAnchor?.('bottom').
- No sendMessage, alert/confirm/prompt, navigator.clipboard (use copyText),
  window.open (use openLink), share or fullscreen.
More: {docs}/sdk

THE WIDGET'S STATE AND REQUESTS (only the bot writes the state)
- The HTML never changes after sending; the bot changes only the state.
  chatData and publicData are strings of up to 1024 bytes, or null:
  JSON.parse them yourself (in try/catch) and draw from them.
  chatData is the chat state: this message in its chat, the same for every
  member of a group (a board, whose turn). publicData is the shared state,
  the same wherever the widget is shown, forwards included (poll results).
  A change of either fires chatDataChanged / publicDataChanged: redraw from
  it; nothing restarts.
- watchPublicData(): the client polls the shared state live while the widget
  is on screen (a ticker, a poll others vote in); unwatchPublicData() stops.
  A poll may bring a state older than a result gave you: keep a revision in
  the state (the bot raises it per edit and returns it in result) and ignore
  older ones.
- request(data): ask the bot, like a callback button; data is 1-64 bytes.
  Resolves with { result }: the bot's text for this user (up to 1024 bytes) or
  null. Rejects with an error whose code is 'NOT_ALLOWED' (no tap, requests not
  allowed here, a forward, a preview), 'BUSY' (one request at a time),
  'RATE_LIMITED', 'TIMEOUT', 'FAILED' or 'INVALID_ANSWER'. Show a pending state
  and every failure. The answer does not change the state: the bot's edit
  arrives as the change events; for the new state at once, have the bot
  return it in result.
- Without a tap request works only in the private chat with the bot: once
  when the widget appears on screen, then at most once per 5 s while visible.
  One made off screen (at start: the client creates the bubble before showing
  it) waits and goes out when the widget appears, so ask in the first lines.
- viewer: { id, first_name, last_name?, username?, is_premium? } of the person
  looking, or null where the widget may not ask the bot. Compare viewer.id
  with the ids in the state (my turn, my vote).
- isForward: a forwarded copy. chatData is null and chatType too; it may ask
  only if the bot allowed forwards to (otherwise viewer is null and every
  request is NOT_ALLOWED): show the shared state read-only. A forward of a
  widget that is not shared never changes: draw a snapshot ("as of 19:40"),
  not live progress.
- chatType: 'private' | 'group' | 'supergroup' | 'channel', or null.
- isPreview: shown in the bot's inline results before it is sent: no taps, no
  requests, links, copy or storage, viewer null. Draw the state as it will be
  sent.
- wallet (Telegram's Gram Wallet, TON Connect's formats): null where the
  client has no wallet for this user, the widget may not ask the bot, or in a
  preview. To pay, on a tap: request('pay:1', { wallet: true }) gives the bot
  the user's wallet address; the bot answers with a fresh transaction
  { valid_until, messages: [{ address, amount, payload?, stateInit? }] } (1-255
  messages, address user-friendly, amount: nanotons as a decimal string); in
  the answer's handler
  wallet.sendTransaction(tx, 'paid:1') needs no new tap (within 10 s). The
  client's window shows the bot and amounts; the promise resolves with the
  bot's answer { result }. wallet.signData({ type: 'text', text }, data)
  asks for a signature, which goes to the bot. One wallet call per tap or
  answer, never from a timer. Codes: 'REJECTED' (declined), 'BAD_REQUEST' (the
  wallet refused it); a malformed transaction throws TypeError. After TIMEOUT
  ask the bot, do not pay again.
More: {docs}/sdk#state

STORAGE, LIFECYCLE, LIMITS
- localStorage: this message on this device, 4 KB (QuotaExceededError past
  it: catch it); sessionStorage until the app restarts; a forward starts
  empty. Keep this user's own choices there (best score, "voted"); what
  everyone sees belongs in the bot's state.
- The widget can be destroyed and recreated at any time: restore from
  localStorage and the state on load, and draw the first frame synchronously.
- Off screen: document.hidden, no animation frames, timers at most 1 Hz.
- CPU: 300 ms per second (timers and frames wait beyond it, longer after a
  task that ran past it); one task may take 500 ms, longer stops the widget.
  At most 128 layout reads in one task; they plus about 25 µs per DOM change
  cost the client at most 100 ms per second (frames wait beyond it).
- Memory 8 MB; 64 live timers and 4000 canvas commands per canvas per task
  (RangeError past them); 2000 nodes; one text value 32 K characters, all
  text 48 K, an attribute 8 K; 10 000 element method calls per task; 400 CSS rules, 32
  @keyframes; 64 script animations; 2048 x 2048 canvas pixels together; a
  data: image at most 1024 x 1024 pixels (larger: a placeholder). Past the
  others, or the CPU or memory limit, the widget stops on its last frame
  ("Widget stopped").
- At most 16 elements at once with a blurred box-shadow, filter,
  backdrop-filter or opacity below 1 (animated ones and ::before/::after
  too), 2 with backdrop-filter; past it the widget stops. Mute with colour
  (var(--tg-theme-hint-color), rgba(), fill-opacity), never opacity; a shadow
  without blur is free.
- Animate with CSS or requestAnimationFrame; change only what moved, do not
  rebuild the DOM every frame.

EXAMPLE (the patterns, not a template)
<style>
body{margin:0;padding:12px 14px}
.opt{display:block;width:100%;margin:6px 0;text-align:left}
.me{outline:2px solid var(--tg-theme-button-color)}
.bar{height:4px;margin-top:4px;border-radius:2px;background:var(--tg-theme-button-color)}
.hint{color:var(--tg-theme-hint-color);font-size:13px}
</style>
<div id="list"></div><div class="hint" id="msg"></div>
<script>
const TW = window.TelegramWidget, $ = (id) => document.getElementById(id);
const NAMES = ['Pizza', 'Sushi'];
const votes = () => { try { return JSON.parse(TW && TW.publicData).v; } catch { return [0, 0]; } };
let mine = null; try { mine = JSON.parse(localStorage.getItem('vote')); } catch {}
function draw() {
  const v = votes(), total = v.reduce((a, b) => a + b, 0) || 1;
  $('list').replaceChildren(...NAMES.map((name, i) => {
    const b = document.createElement('button'), pct = Math.round(v[i] / total * 100);
    b.className = 'opt' + (mine === i ? ' me' : '');
    b.innerHTML = name + ' <span class="hint">' + pct + '%</span><div class="bar" style="width:' + pct + '%"></div>';
    b.onclick = () => vote(i);
    return b;
  }));
}
function vote(i) {
  if (!TW) return;
  $('msg').textContent = 'Sending...';
  TW.request('vote:' + i).then(() => {
    mine = i; try { localStorage.setItem('vote', i); } catch {}
    $('msg').textContent = ''; draw();
  }, (e) => { $('msg').textContent = e.code === 'NOT_ALLOWED' ? 'Open the bot to vote' : 'Not sent: ' + e.code; });
}
if (TW) TW.onEvent('publicDataChanged', draw);
draw();
</script>
```
<!-- /agent-block -->
