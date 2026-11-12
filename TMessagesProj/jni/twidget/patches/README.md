# The widget projects and the changes made to them here

The widget renderer is built from two public Telegram repositories, git submodules in `jni/twidget/` (since
2026-11-07; plain-file copies before), next to the Android bridge (`twidget-bridge.cpp/.h`, `twidget.cmake`).
Both are used exactly at the pinned commits: Telegram changes to them go into the repositories themselves,
and this file lists what earlier patches became there.

| Submodule (`jni/twidget/…`) | Repository | Version |
|---|---|---|
| `widget-sandbox/` | github.com/TelegramMessenger/widget-sandbox | `c498cef` on `v0.6.3` (2026-11-10: the QuickJS pin moves to `ca3594d`, `await using` only in async functions, bundles unchanged; `v0.6.3` at `7a2ec35` before it: 2026-11-09: QuickJS parser label hardening, failed `new_label()` allocations fail the parse instead of reaching `fd->label_slots[-1]`; the six `corpus/attacks/render-*` widgets for native renderers; the bundles differ only in the version stamp. `v0.6.2` before it: the same for `push_scope()`; `v0.6.1`: the QuickJS security release), unchanged |
| `widget-sandbox/packages/ios/Sources/CQuickJS/quickjs/` | its submodule, github.com/TelegramMessenger/quickjs | `ca3594d` (2026-11-10: `await using` in a for-in/of head is a SyntaxError outside async functions, its disposal ran `OP_await` in a normal frame whose exit skipped `close_var_refs()`, a use-after-return from widget script; the interpreter refuses suspending a normal frame; reported against 13.2.1. On top of `b02310a`, 2026-11-09: new_label() allocation failures fail the parse instead of reaching `fd->label_slots[-1]`, with a test, on top of `09bb809`: the same for push_scope() and `fd->scopes[-1]`, on top of `1a402cb`, branch `fixes-backport`, 2026-11-06: memory-safety fixes from quickjs-ng after 0.17.0 on `c24d6b0`, among them the RegExp bytecode use-after-free of the widget RCE report), pinned by widget-sandbox; only its `quickjs.c`, `dtoa.c`, `libregexp.c` and `libunicode.c` are compiled (its own `test262` submodule is `update = none`) |
| `twidget/` | github.com/TelegramMessenger/twidget | `4769d72` (2026-11-09: list markers out of a style's range are decimal, roman past 3999 drew one 'M' per thousand. `8c6d311` before it: var() substitution is capped at 16,384 tokens per declaration, and a layout pass at 100,000 + 50 per element boxes, past which the renderer freezes with its badge: nested var() and nested flex/grid/table boxes ran out of memory or hung the host thread. `f385525` before it: repeating gradients of a zero or tiny period draw their average colour; `repeating-linear-gradient(red 50%, blue 50%)` grew its stops until the engine ran out of memory. On main after the reorganisation (`f083a1f`: `core/`, `litehtml/` at the top), which changed only paths since `db9afca`'s layout fixes) |

What the Android build compiles (`jni/twidget/twidget.cmake`, into `libtwidget.so` alone, not the client library, but for the client's share below): widget-sandbox's C++ core
(`packages/desktop/sandbox`, without `icu_intl.cpp`, Windows' ICU: Android answers Intl from Java) and QuickJS, twidget's `core/src` and its litehtml fork
(`litehtml`); `twidget.cmake` generates `tw_ua_stylesheet.h` from widget-sandbox's `profile.json`, as twidget's CMake does. The JS bundles in `assets/WidgetSandbox/WidgetJS` are widget-sandbox's
`packages/desktop/WidgetJS` of the same commit, byte for byte; `manifest.txt` is their SHA-256.

The Java side is in `org.telegram.utils.twidget` (the bridge's `WidgetSandboxNative` and `WidgetSandboxBackend`,
the session and the message, painting, the chat's widget messages in `WidgetMedia`, the debug runners), except what
draws a widget into a View, which stays in `org.telegram.ui.Components`: `RichWidgetBlock` (the chat bubble),
`WidgetInlinePreview` (an inline result) and `WidgetSandboxPlayer` (frames and touches between the two).

## Processes (since 2026-11-07)

A widget's code (its JavaScript in QuickJS, the validator, the TwidgetCore renderer) runs in an isolated process,
`WidgetEngineService` (`android:isolatedProcess`, `:widgets`): no permissions, no network (no `inet` group), no access
to the app's files, SELinux `isolated_app`; on every Android widgets run on (6+). One process holds every widget,
each session on threads of its own with a QuickJS runtime of its own: it keeps the app's accounts, messages and keys
from widget code, not one widget from another (a process per bot was tried and dropped on 2026-11-08 for the memory,
about 25 MB PSS a process). It is bound with `BIND_IMPORTANT`, so it runs in the client's scheduling group (top-app
with the chat on screen; in `foreground` a weak phone kept it on slow cores). The process loads only `libtwidget.so`
and the bundles from the APK's assets (`WidgetSandboxNative#registerResources`); `ApplicationLoader` starts nothing of
the client there. When it dies every shown widget restarts (`RichWidgetBlock#restartWidget`, twice a minute at most).

- Start: the app binds the engine 3 s after `postInitApplication` and keeps it for its lifetime
  (`WidgetRemoteEngine#prewarm`, `IWidgetEngine#warmup` loads the bundles), so a widget's first frame does not wait for
  the process: on the Fold emulator a first session took 356 ms cold (206 ms to bind, 69 ms bundles) and ~40 ms warm.
  An idle engine takes no CPU. Low-RAM devices and the kill switch keep the lazy start (first widget, 30 s idle unbind).
- Death: the client gives the binding up (`WidgetRemoteEngine#drop`) and binds afresh for the next session, or on the
  next return to the foreground (prewarm), instead of waiting for the system to bring the service back: on One UI and
  MIUI 14 it never did for an isolated process, so after the engine was killed in the background no widget started
  ("Widget engine did not start") until the app restarted (a Galaxy A24 on 7297, reproduced with `am crash`).
- seccomp (`twidget-seccomp.cpp`, `WidgetEngineService#onCreate`, before any session): exec, process creation,
  ptrace, perf events, eBPF, io_uring, keyrings, module and mount calls, non-`AF_UNIX` sockets, kernel-mode
  `userfaultfd`. Audit for now (`SECCOMP_ENFORCE`): the calls trap, are logged as `TwidgetSeccomp` and redone through
  an escape hatch the filter lets through, so nothing changes; exec and process creation get EPERM even then. Enforce
  kills the process on them. Not filtered: `ioctl` (ART's GC makes userfaultfd ioctls with SIGSYS blocked, where any
  trap kills the process) and user-mode `userfaultfd` (ART's GC on Android 14+). Calls the system's own app filter
  refuses as well come back ENOSYS in audit (stacked filters report our trap data). Needs `SECCOMP_FILTER_FLAG_TSYNC`;
  without it the engine runs unfiltered and logs so.

- The client (`WidgetRemoteEngine`) creates sessions over `IWidgetEngine` (AIDL, oneway after `create`) and gets each
  session's calls back over `IWidgetEngineCallback`, synchronously, so frames keep their order and the engine waits for
  the painter as it did in-process. The engine's backend (`WidgetEngineBackend`) answers layout itself (text widths,
  decoded images, Intl) and sends the client what it paints with: fonts by their parameters, images as pixels (a
  parcelled `Bitmap`), canvas batches, display lists (in 256 KB parts above that; only those large calls share a lock,
  so the host thread's signals never wait for the recorder's frame, which serialised every tick behind the client's
  draw). Font metrics come back from the
  client's `defineFont`, so layout and painting use one font. MIUI's `Typeface.create` reads a font setting from
  `Settings`, which an isolated process is refused: there the engine makes no fonts and asks the client for each
  width (`textWidth`, cached per font). Painting (RenderNodes,
  canvases, filters, layers) stays in the client, as do requests, actions, storage and the gesture arbiter.
- What comes back is untrusted: `WidgetGate` keeps the user- and bot-facing part of the engine's policy in the client,
  never stricter than it (links and `copyText` one per delivered press, requests with `can_query` one in flight, as a
  press's or under `background_query`, wallet calls by a press or the grant, haptics at 10 a second, the engine's sizes
  and link rules); `WidgetSandboxBackend` caps fonts, layers, display ops, canvas batches and frames per second.
- A process that dies (a crash, the system) ends its sessions (`WidgetSandboxSession#onEngineDied`); a shown widget
  starts again through its block, twice a minute at most (`WidgetSandboxMessage#onEngineDied`). The client process
  lives on. A process is unbound 30 s after its last session.
- The client never loads `libtwidget.so` (since 2026-11-09): QuickJS, the validator and the renderer are mapped only
  into the engine process. What the client runs of the same code, widget-sandbox's gesture policy
  (`gesture_policy.cpp`) and twidget's CPU filter pass (`core/src/filters.cpp`), neither of which reads widget code, is
  compiled into the client library as an object library (`twidget_client_sources` in `twidget.cmake`, JNI in
  `jni/twidget/twidget-client.cpp`, Java `WidgetClientNative`). Private builds can run the engine in-process for
  comparisons (`WidgetConformanceActivity --ez inProcess true`), which loads `libtwidget.so` into the client at that
  session's create; every other build always isolates.

## Updating

1. Check out the new commit in the submodule and stage it: `git -C TMessagesProj/jni/twidget/widget-sandbox checkout
   <tag>`, then `git submodule update --init --recursive TMessagesProj/jni/twidget/widget-sandbox` for the QuickJS
   commit it pins (a fresh clone needs the same command). Ninja rebuilds by the checked-out files' times, which are
   the checkout's own, so no objects go stale (the copies dated by `git archive` once linked old and new code
   together: heap corruption at the first `~WidgetHost`, 2026-10-21 and 2026-10-23).
2. A change Telegram needs goes into the repository first (a branch or `main`, as its owners take it), then step 1.
3. Copy the bundles, regenerate `assets/WidgetSandbox/manifest.txt` (`<sha256> <name>` with one space, which
   `WidgetSandboxResources` splits on: `shasum` writes two), update the table above and the versions in `WidgetStressRunner`.
   New TwidgetCore sources are compiled by name in `twidget.cmake` (widget-sandbox's by a glob). A new or renamed JNI
   callback goes into both backends, `WidgetSandboxBackend` and `WidgetEngineBackend`, and `IWidgetEngineCallback`.

## twidget: no changes

The layout the conformance set (`auto/28-grid-percent-fill`, `29-grid-svg-marks`, `30-svg-fill-box`) and the bot demos'
poll, duel and ticker drew wrong, and a canvas sized by its CSS width alone, were `twidget-layout.patch` here from
2026-10-31 and are in twidget itself since `db9afca` (2026-11-02):
- `render_grid.cpp`: an item across a flexible track has no automatic minimum and grows only the flexible tracks; a
  replaced item with a % width has no minimum; a % height of an item needs this grid's own height, not an ancestor's;
  flexible rows take a share per `fr`, as the columns do.
- `render_block.cpp`: `aspect-ratio` from the width after `max-width` and `min-width`.
- `render_image.cpp`: a % height with nothing to resolve against is the ratio's.
- `transform-box: fill-box` (and `stroke-box`, the same centre) on SVG shapes: `string_id.h`, `style.cpp`,
  `css_properties`, `el_svg.cpp`.
- `renderer.cpp`: a drawn `<canvas>` no longer passes its `width` and `height` attributes to the fork's image, where they
  became its style: with only a CSS width its height stayed at the attribute and the widget's own coordinates missed
  (TwidgetCore's `core_tests.cpp` checks it). The widget case is `tests/canvas-aspect.html` (no number: widget-sandbox's
  `auto/37` is another case), not in widget-sandbox's set yet. A canvas not drawn on yet still has them.

The Telegram changes that used to be `twidget-telegram.patch` are in twidget itself since 2026-10-20
(`3f242e4`…`edfafc8`: line height, right alignment, `var()` in cascade order, out-of-flow children of flex
and grid, collapsed table borders, SVG gradient strokes, rounded dashed/dotted and double borders, the image
placeholder's aspect). The border snapping to device pixels (Telegram `5b74389`) was dropped earlier: the
fork's half-CSS-px widths are what the conformance set measures.

What the bridge implements for `9f68fbc`'s `Backend` (`twidget-bridge.cpp`, `WidgetSandboxBackend`):
- `canvas_run` hands a batch to `WidgetCanvas` as int words (`encodeCanvasOp`), not as `twlite::to_json`: on the Redmi Note 8T
  JSON took most of `receive` on a canvas-drawn widget.
- Display lists (the frame's and layer contents) go to Java in the bridge's binary form (`encodeDisplayList`, read
  by `WidgetDisplayList` as untrusted input: bounds-checked, counts checked before allocating), not as
  `twlite::to_json` (since 2026-11-08). On the Redmi Note 11 `dom-tick/1000` went from 22 to 10.5 ms per frame and
  `svg-path/4000` from 14 to 6 (parse 6 ms → 0.1, serialise 1–2 ms → 0.1). The stress kit's `parse` now covers the
  header only; decoding happens while painting, in `canvas`. Points keep full float precision (JSON rounded to
  hundredths). The composition stays JSON (small), as does the selectable text; `WidgetSandboxSession#dump`
  converts the list back to to_json's keys (`WidgetDisplayList#toJson`).
- `create_font` takes the OpenType features (`tnum=1,zero=1`, from `font-variant-numeric` and
  `font-feature-settings`); `WidgetSandboxBackend#featureSettings` turns them into
  `Paint#setFontFeatureSettings` syntax (`"tnum" 1, "zero" 1`).
- `FontMetrics::em_ascent` / `em_descent` (the OS/2 em box for canvas `textBaseline`) stay 0: Android does
  not expose the OS/2 table, and the core then normalises ascent and descent itself.
- A damaged PNG: twidget's `png_intact_rows` (`src/png.cpp`, compiled) is the same rule as
  `WidgetPng`, which keeps doing it on the Java side, where BitmapFactory refuses such a PNG outright.
- `bg_color` of an outgoing bubble with a gradient: the gradient spans the chat's height and moves under the
  bubble as it scrolls, so `WidgetSandboxTheme` passes the average of its colours (sdk-v1 §5.1).
- The theme's text tokens (`WidgetTheme.text`, v0.1.1) for the UA layer: `WidgetSandboxTheme` passes the chat's
  text size, the message paint's line height and the text's inset in the bubble; `parseTheme` reads them.
- Who gets a press (sdk-v1 §5.5, v0.1.2; v0.1.3: vertical drags are always the chat's, `y` is always false): the
  host's `onGesture` claim (`x`, `hold` from `touch-action`)
  goes to the player (signal 3), which feeds it with the press to widget-sandbox's own C++ `GestureArbiter`
  through the bridge (`WidgetGestureArbiter`, no Java copy of the policy). A verdict for the bubble ends the
  press for the widget (`touch` action 3, `pointercancel`) and frees the chat's recognizers: the list scroll,
  swipe-to-reply (`ChatActivity`'s list keeps tracking a swipe while the widget may still give it), the
  message menu: a hold over the widget opens it at the policy's 500 ms from the arbiter's verdict
  (`ChatMessageCell#onWidgetHold`, with the widget's `pointercancel`); the cell's own long press, on the system's
  timeout, skips presses over the widget (`ChatMessageCell#onLongPress`). A double tap over the widget is the
  widget's (`ChatActivity#hasDoubleTap` is false for it); the renderer sends the widget its `dblclick` and
  `contextmenu` itself. The conformance runner's touches go through the same arbiter as a bubble.
  The chat list hands a cell a copy of each DOWN first (`RecyclerListView`: no down time, no tool type); the
  player sends the widget the DOWN of the real event that follows.
- A mouse (Chromebook, DeX, a tablet's mouse): its presses go to the arbiter and the renderer as a mouse's
  (`MotionEvent#getToolType`, the secondary button, the modifier keys): its drags and holds are the widget's, the
  list never pans or swipes under it, a right click opens the menu or the widget's `contextmenu`. Hover events
  (`ChatMessageCell#onHoverEvent` → `RichWidgetBlock#onHoverEvent`) give the widget `:hover` and the over/out
  events, and the renderer's CSS cursor (signal 11) becomes the cell's `PointerIcon` (API 24). No keyboard and no
  wheel: both are out of profile 1 (D4, D3a).
- The finger rule (v0.3.0, `docs/decisions.md` "User activation"): TwidgetCore marks a finger's or a pen's `pointerdown`
  and compatibility `mousedown` untrusted itself since `710bb77` (the bridge did it before): the gesture counts from the
  release, and a press that becomes the chat's scroll calls nothing. A mouse keeps its press.
- Strict limits (v0.3.0): `WidgetHost` stops the widget at the validator's counted caps and runs the main-thread
  window itself; the bridge scales `mainThreadQuotaMs` with the CPU multiplier as it does `cpuQuotaMs`.
- Heavy boxes (v0.6.0): TwidgetCore counts them before each frame (`src/paint_policy.cpp`) and answers `WidgetHost`'s
  `policy` itself; its `limit` message reaches the host with the renderer's other output (`AndroidRenderer#dispatch`),
  and `WidgetHost` stops the widget. The validator's time (`chargeValidator`) and the bundles' version stamps
  (`bundle_stamp.cpp`, `bundle-mismatch`) are `WidgetHost`'s and `SandboxEngine`'s own: nothing in the bridge.
- Touch text selection (sdk-v1 §5.10): the bridge sends `Renderer::selectable_text_json()` to
  `WidgetSandboxBackend#selectableText` after a layout that changes it; `WidgetText` turns it into
  `android.text.Layout`s that `RichWidgetBlock` hands to the chat's `TextSelectionHelper`. The renderer's
  own mouse selection is not used on Android. A run's matrix (where its transforms and layer show it) is
  applied when it only moves and scales; a turned or skewed run is selected where layout put it.
- The time over the widget (v0.4.0, sdk-v1 §2.17, §5.12): with no caption below the widget (none, or shown above it,
  `invert_media`), a bubble (not the compact incoming layout) and no reactions row, `ChatMessageCell` draws the time
  over the widget's bottom right corner on the backing it draws over a photo, 6 dp inside the widget's edges; the
  bubble has no time line and ends 2 dp under the widget, whose bottom corner away from the tail follows the bubble's.
  An incoming message's backing is 6 dp inside the widget's right edge; an outgoing one's sits as over a photo (the
  ticks over media keep to the cell's width). The band (23 dp: the backing and the 6 dp under it) and the corner (6 dp,
  the backing: the time, its padding, an outgoing message's ticks; 4 dp before it), rounded up to whole CSS px, go to
  the host as `contentSafeArea`: at creation in the sdk JSON of
  `WidgetSandboxNative#create`, from the layout's estimate (`RichWidgetBlock#estimateSafeArea`: the cell's own time text,
  `ChatMessageCell#timeString`, and a channel post's views), so a widget measured off screen already has the band in its height; then the cell's own numbers on its layout
  (`WidgetSandboxNative#safeArea` → `setContentSafeArea`, only when they change). The right corner in a right-to-left
  interface too: the chat draws the time on the right there as well. Taps on the time stay the bubble's as before.
  The conformance runner passes a test's `safeArea` (auto/36, visual/17).
- Debug: `WidgetSandboxNative#conformance` makes every session report its `data-conformance` attribute
  (signal 9) for `WidgetConformanceRunner`.
- The resize anchor (sdk-v1 §2.12, §5.6, v0.1.3): the bridge adds `WidgetDiagnostics::resizeAnchor` to the
  diagnostics (signal 4); `WidgetSandboxSession` puts it on the next recorded frame (`Frame#anchorBottom`) and
  `WidgetSandboxMessage` keeps the shown frame's. `RichWidgetBlock#relayout` keeps the cell's top for `top`
  (`forceUpdate(cell, true, true)`, as "Summarize" does) and leaves `bottom` to the chat, laid out from its
  bottom; while the bubble animates, the frame is clipped to its drawn bounds and moves with the anchored edge
  (`TransitionParams#deltaTop` / `deltaBottom`). The anchor is kept with each cached height (`widget_heights.anchor`,
  database version 181): before its first frame a widget opens on the anchor of its latest cached height.
  Only a cell the list shows keeps its top; one laid out off screen changes height as any message does.
- The payload (`WidgetMedia#inflate`): zlib (RFC 1950) since the server's `gzcompress` switch; gzip (`1f 8b`)
  is still read for messages sent before it.
- Not used: `Renderer::set_resize_tracking` (a guess of which side to keep still; the widget's anchor decides).
- The widget's state and requests (sdk-v1 §5.11, v0.2.0): `WidgetMedia` passes each message's `chat_data` /
  `public_data` as `data` and the server's flags as `context` (`is_copy`, `can_query`, `background_query` as they are;
  the chat type, none for a copy; the account as `viewer` only with `can_query`; `preview` for an inline result) in the
  sdk JSON of `WidgetSandboxNative#create`. `updateMessageWidgetData` (the account's pts sequence; a supergroup's state
  comes with the usual post edit) is stored with the message (`MessagesStorage#updateMessageWidgetData`) and reaches the
  live widget through `WidgetSandboxNative#data` (`setData`). Requests (signal 12) go out as
  `messages.getBotWidgetQueryAnswer` and come back through `answerRequest`: `WIDGET_RESPONSE_TIMEOUT` a timeout,
  `FLOOD_WAIT` rate limited, anything else (and 30 s without an answer) failed. `copyText` (signal 13) copies and shows
  the text (the system shows it on Android 13+). `watchPublicData` (signal 14): one poll per shared widget (`widget_id`)
  with a watching widget on screen, `messages.getWidgetPublicData` with the last hash at the server's period (30 s
  before the first answer, 5–3600 s); a new value goes to every widget of that id and is stored. The network marker
  (`RichWidgetBlock`): a globe at the widget's top trailing corner where the message has `can_query`, explained on a
  tap; none in a private chat with a bot (`WidgetMedia#showsNetworkMarker`). Inline results (`botInlineMessageWidget`) are previews (`WidgetInlinePreview` in `ContextLinkCell`, no input)
  and are sent as widgets; a forwarded widget keeps its header (drop_author does not apply).
- Haptics need a touch (v0.2.2): TwidgetCore reports the press's start (`gesture`) but not its end, so the bridge
  posts the framework's `{ type: 'release' }` when the primary pointer goes up or is cancelled.
- The wallet (v0.5.0, sdk-v1 §2.18, §5.13; the server's API as agreed in `2e575a7`): `context.wallet` where the
  widget may ask its bot and `config.walletAvailable` (the country) allows it. A request with `{ wallet: true }` goes out
  as `widgetQueryTypeData` with `with_wallet`. A wallet call (`onWallet`, signal 19) opens `GramWalletWidget`, the
  wallet's own window: the bot of the message in its header (`via_bot_id`, else the author or the forward's author,
  a bot; otherwise `badRequest`), taps ignored for its first 0.7 s, the preview's fee, "Top up" (onramp) while the
  wallet holds less than the transaction. On confirmation the transfer goes out by `wallet.sendTransfer` and the
  message's normalised hash (TEP-467, `WalletMessageHash`: the server's `msg_hash` is its plain hash) goes to the bot as
  `widgetQueryTypeSentTransaction`, in base64 (the server refuses hex with `WIDGET_MSG_HASH_INVALID` and gives the bot
  hex itself); `signData` is signed for `bot<bot_id>` (a
  throwaway TON Connect session for the engine: it takes no part in the signature) and goes as
  `widgetQueryTypeSignData`; both with the device's wallet (`userWallet`: the address, the balance, the key and the
  contract's StateInit, TON Connect's `walletStateInit`). The window closed or declined answers `rejected` (4), the
  wallet's refusal `badRequest` (5). A plain request on a server without the query types yet
  (`INPUT_METHOD_INVALID` / `INPUT_CONSTRUCTOR_INVALID`) goes again in its earlier form
  (`TL_messages_getBotWidgetQueryAnswer_layer233`), and the account keeps that form until the app restarts; a wallet
  query fails there.

`FilterPush`/`FilterPop` and `Backdrop` (`filter`, `backdrop-filter`) are drawn by the Java painter
(`WidgetSandboxPainter`): on API 31+ on a hardware canvas through `RenderEffect`; otherwise `filter` goes
through twidget's shared CPU pass (`core/src/filters.cpp`, `WidgetClientNative#filterPixels` in the client library) on
a bitmap of the content, as twidget's own Android painter does. `backdrop-filter` needs the pixels under
it and stays API 31+ only.

## widget-sandbox: no changes

`v0.1.2` has the hidden timers' order in every glue copy, which Telegram carried as
`widget-sandbox-telegram.patch` until `b66576f` (a hidden widget's timers wait at least a second plus a tenth
of their own delay, so `setTimeout` 20, 10, 0 run 0, 10, 20; conformance `auto/15-js-runtime-a`), and the
gesture policy (below). The JS bundles are `v0.6.3`'s, stamped `version=0.6.3 protocol=1 profile=1 sdk=1`: with
bundles of another protocol, profile or sdk the engine refuses to boot (`bundle-mismatch`).

Telegram's earlier change (`holdScripts` / `releaseScripts` in the C++ `SandboxEngine` and `WidgetHost`: a
static first layout with the scripts held back) is not carried onto `v0.1.0`; the chat uses the cached
height or the message's size anchors instead. The Android-only `getrandom` shim
(`WidgetSandboxAndroidCompat.h`) is gone: `v0.1.0` uses `arc4random_buf` on Android itself.
