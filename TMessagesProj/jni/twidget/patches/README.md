# The widget projects and the changes made to them here

The widget renderer is built from copies of two Telegram repositories, kept as plain files (no
submodules) in `jni/twidget/`, next to the Android bridge (`twidget-bridge.cpp/.h`, `twidget.cmake`).
Every change made to the copies here is listed below, with a patch file next to this one, so that each
update can start from the repository's state and re-apply only these.

| Copy (`jni/twidget/…`) | Repository | Version |
|---|---|---|
| `widget-sandbox/` | github.com/TelegramMessenger/widget-sandbox | `bf4b6bd` (2026-11-06: `v0.6.0` + the QuickJS fixes backport, a submodule bump only), unchanged |
| `widget-sandbox/packages/ios/Sources/CQuickJS/quickjs/` | its submodule, github.com/TelegramMessenger/quickjs | `f4dd164` (branch `fixes-backport`, 2026-11-06: memory-safety fixes from quickjs-ng after 0.17.0 on `c24d6b0`, among them the RegExp bytecode use-after-free of the widget RCE report): top-level `.c`/`.h`, `LICENSE`, `README.md` only (no tests, docs, examples) |
| `twidget/` | github.com/TelegramMessenger/twidget | `main` at `a22eff2` (2026-11-02: the Telegram layout fixes below, on `42baa3f`'s widget-sandbox v0.6.0 and heavy-box count), unchanged |

What the Android build compiles (`jni/twidget/twidget.cmake`): widget-sandbox's C++ core
(`packages/desktop/sandbox`, without `icu_intl.cpp`, Windows' ICU: Android answers Intl from Java) and QuickJS, twidget's `Sources/TwidgetCore/src` and its litehtml fork
(`Sources/LiteHTML/litehtml`); `twidget.cmake` generates `tw_ua_stylesheet.h` from widget-sandbox's `profile.json`, as twidget's CMake does. The JS bundles in `assets/WidgetSandbox/WidgetJS` are widget-sandbox's
`packages/desktop/WidgetJS` of the same commit, byte for byte; `manifest.txt` is their SHA-256.

The Java side is in `org.telegram.utils.twidget` (the bridge's `WidgetSandboxNative` and `WidgetSandboxBackend`,
the session and the message, painting, the chat's widget messages in `WidgetMedia`, the debug runners), except what
draws a widget into a View, which stays in `org.telegram.ui.Components`: `RichWidgetBlock` (the chat bubble),
`WidgetInlinePreview` (an inline result) and `WidgetSandboxPlayer` (frames and touches between the two).

## Updating

1. Replace the copy with the repository's new state (`git archive <tag> | tar -x`; for widget-sandbox, add the
   QuickJS subset from its submodule at the commit the tag pins). Copy it in without the archive's times
   (`rsync -rlpD --checksum --delete`, not `rsync -a`): `git archive` dates every file at its commit, older
   than the objects already built, so ninja would keep stale objects and link old and new code together
   (heap corruption at the first `~WidgetHost`, seen on 2026-10-21 and 2026-10-23).
2. `patch -p1 < ../patches/<name>.patch` inside its copy (not `git apply`: inside the Telegram repository it skips the paths silently), resolve, regenerate the patch with `git diff --no-index` against the repository's tree.
3. Copy the bundles, regenerate `assets/WidgetSandbox/manifest.txt` (`<sha256> <name>` with one space, which
   `WidgetSandboxResources` splits on: `shasum` writes two), update the table above and the versions in `WidgetStressRunner`.
   The copies' new sources are compiled by name in `twidget.cmake` for TwidgetCore (widget-sandbox's by a glob).

## twidget: no changes

The layout the conformance set (`auto/28-grid-percent-fill`, `29-grid-svg-marks`, `30-svg-fill-box`) and the bot demos'
poll, duel and ticker drew wrong, and a canvas sized by its CSS width alone, were `twidget-layout.patch` here from
2026-10-31 and are in twidget itself since `a22eff2` (2026-11-02):
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
through twidget's shared CPU pass (`src/filters.cpp`, `WidgetSandboxNative#filterPixels` in the bridge) on
a bitmap of the content, as twidget's own Android painter does. `backdrop-filter` needs the pixels under
it and stays API 31+ only.

## widget-sandbox: no changes

`v0.1.2` has the hidden timers' order in every glue copy, which Telegram carried as
`widget-sandbox-telegram.patch` until `b66576f` (a hidden widget's timers wait at least a second plus a tenth
of their own delay, so `setTimeout` 20, 10, 0 run 0, 10, 20; conformance `auto/15-js-runtime-a`), and the
gesture policy (below). The JS bundles are `v0.6.0`'s, stamped `version=0.6.0 protocol=1 profile=1 sdk=1`: with
bundles of another protocol, profile or sdk the engine refuses to boot (`bundle-mismatch`).

Telegram's earlier change (`holdScripts` / `releaseScripts` in the C++ `SandboxEngine` and `WidgetHost`: a
static first layout with the scripts held back) is not carried onto `v0.1.0`; the chat uses the cached
height or the message's size anchors instead. The Android-only `getrandom` shim
(`WidgetSandboxAndroidCompat.h`) is gone: `v0.1.0` uses `arc4random_buf` on Android itself.

`gradle.properties` files inside the copies are ignored by Telegram's `.gitignore`; that is not a change.
