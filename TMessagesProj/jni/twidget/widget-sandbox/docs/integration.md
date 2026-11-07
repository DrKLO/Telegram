# Integrating the sandbox into a client

One `WidgetHost` per widget, on every platform: it owns the QuickJS engine
(on its own thread), the validator and the renderer, gives you a view to put
in the bubble, and reports height, diagnostics and bridge calls through
callbacks. The HTML you pass is the whole widget (inline CSS and JS); nothing
in it can reach the network.

The same `Options` exist everywhere with the same defaults (`memoryLimitBytes`
8 MB, `cpuLimitMs` 500, `cpuQuotaMs` 300 per `cpuQuotaWindowMs` 1000 (the
widget's JS time and, on the native hosts, the validator's CPU time for it:
`stats.validatorMs`), `mainThreadQuotaMs` 100 (the host's main-thread work for
the widget: its layout queries and the modelled cost of its ops), `maxTimers`
64, `visible`, `policy` `strict`), see `packages/core/src/shared/protocol.ts` and `docs/decisions.md`,
"Strict limits from the start". Keep the defaults in a product build; `policy`
`compat` and wider quotas are for tests and the stress kit's measuring runs.
Call `setVisible(false)` when the bubble leaves the screen (frames stop, timers
drop to 1 Hz), `setTheme` when the chat theme changes, `destroy()` when the
bubble is recycled. A widget that exceeds its limits is killed; the renderer
keeps the last frame with a "Widget stopped" badge (`stoppedLabel`) and `onDead`
fires — destroy or re-create, your choice.

The rest of this page is in the order a client team meets it: pinning a
release, the policy and features, the widget payload, bubble sizing, heavy boxes,
input, forwarding, then the per-platform API and the conformance set.

## Pinning a release

Releases are annotated tags on this repository (`v0.1.0` is the first). Pin
the tag, never a branch: `main` moves, and the JS bundles, the native
bytecode and the QuickJS submodule change together, so a mixed checkout does
not boot.

- **SwiftPM (iOS, macOS)**: `.package(url: "https://github.com/TelegramMessenger/widget-sandbox.git", exact: "0.6.0")`
  (SwiftPM reads the `v` prefix of the tag). The manifest is the repo-root
  `Package.swift`; SwiftPM clones the QuickJS submodule itself. A registry or
  zip copy without the submodule does not build.
- **Git submodule (Bazel, CMake, tdesktop)**: add the repository as a submodule
  at the tag and `git submodule update --init --recursive` (the sandbox has its
  own submodule, `packages/ios/Sources/CQuickJS/quickjs`). The C++ core is
  `add_subdirectory(<submodule>/packages/desktop)`.
- **Gradle (Android)**: a checkout at the tag and `includeBuild("<checkout>/packages/android")`
  in `settings.gradle.kts`, then depend on `org.telegram.widgetsandbox:widget-sandbox`
  as `apps/android` does. The AAR carries the native library (arm64-v8a, x86_64)
  and the bundles as assets; a Maven publication comes later.
- **npm (web)**: `@telegram/widget-sandbox` lives in `packages/core`. With pnpm:
  `"@telegram/widget-sandbox": "github:TelegramMessenger/widget-sandbox#v0.6.0&path:packages/core"`;
  with another package manager, a checkout at the tag and a `file:` / workspace
  dependency on `packages/core`. The package is not on the npm registry yet.

What you get at a tag is one state of the framework: the three JS bundles, the
`.qbc` bytecode next to them, the QuickJS submodule they were compiled with,
`packages/quickjs-wasm/dist` for the web, and `packages/conformance` (below).
Never mix files from two tags.

**`VERSION.json`** at the repository root names that state (since v0.6.0):

```json
{ "version": "0.6.0", "protocol": 1, "profile": 1, "sdk": 1 }
```

| field | what it is |
|---|---|
| `version` | the release, semver; the tag is `v` + it (`package.json`, `packages/core/package.json` and the Android library's `version` repeat it) |
| `protocol` | the wire between the glue and the bundles (`docs/protocol.md`: the config, the host calls, the messages, the validator's API); it changes when a bundle needs glue it cannot run without |
| `profile` | the widget profile, `TelegramWidget.profile` (`docs/profile-v1.md`; `packages/core/profile.json` repeats it) |
| `sdk` | the widget SDK (`docs/sdk-v1.md`); an addition to SDK v1 does not change it |

`pnpm build:ios|android|desktop` stamps all four on the first line of every
bundle (`/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1 */`) and
writes them into the glue as constants: `WidgetSandboxVersion` (Swift and
Kotlin) and `twidget::version` (C++, `twidget/version.h`), which also give the
glue its `profile`. **The native glue refuses a bundle without a stamp or with
another `protocol`, `profile` or `sdk`**, the way it refuses a stale `.qbc`,
except that there is nothing to fall back to: the engine stops at boot, before
any widget code, with the diagnostic `bundle-mismatch` (its detail names the
bundle, both versions and the fix) and dies with `sandbox bundle failed to
load`; the validator loads nothing and answers every batch empty. The release
itself is not compared: bundles of another release with the same three numbers
run. A `bundle-mismatch` means your copy mixes two releases (a resources folder
not updated with the code, a bundle copied over by hand): take the bundles and
the glue from the same tag. The web host builds its bundles from the same
`packages/core` sources as the rest of the page, so it has nothing to compare.
A client that loads the bundles from a resource layout of its own gets the same
check (Swift `SandboxResources(sandboxBundle:rendererBundle:validatorBundle:sandboxBytecode:validatorBytecode:)`,
the fields of the C++ `SandboxResources`); `SandboxResources.refusal` (Swift,
Kotlin) and `BundleStamp::refusal(resources)` (C++) give the reason before any
widget starts, for a startup check of your own.

## Policy and features

**`strict` is the default on every host.** Privileged bridge calls need a real
user gesture, text is budgeted, `data-*`/`aria-*` and CSS strings included, and
the limits of `docs/changes.md` apply. `compat` exists for tests and tooling;
a product client never sets it (Kotlin and C++ take a string: anything but
exactly `"compat"` is strict).

**Features** are opt-in wire forms the host advertises to the runtime; the
runtime uses none it did not list. The option has the same name everywhere:

| | option | type |
|---|---|---|
| Swift | `WidgetHost.Options.features` | `Set<String>` |
| Kotlin | `WidgetHost.Options.features` | `Set<String>` |
| C++ | `WidgetHostOptions.features` | `std::vector<std::string>` |
| TypeScript | `WidgetHost` option `features` | `string[]` |

The profile sets them, not the client: today that is `["webAnimations"]` on
every platform (Web Animations records instead of per-frame style writes).
`canvasWords`, `binaryOps` and `morphUpdates` are Swift-only optimisations of
the same validated output (`docs/changes.md`, "Swift-only"); list them only on
iOS/macOS and only after measuring. A feature the renderer behind your
`Renderer` seam cannot draw must not be listed.

## The widget payload

A **widget is mini-HTML**: one self-contained HTML document of at most 4 KB
compressed and 32 KB uncompressed. Since API layer 233 it is message media of
its own: `messageMediaWidget width:int height:int payload:bytes`
(`docs/tgw-format.md` has the TL, the server's checks and the errors). The
server compresses the bot's plain HTML with zlib (level 9) and enforces both
limits. The client takes `payload`, a **zlib stream (RFC 1950), not gzip**,
inflates it and passes the text to `WidgetHost`; what inflates it on each
platform, and the decoders that look right but are not (Apple's `COMPRESSION_ZLIB`
is raw deflate, `qUncompress` wants a length prefix): `docs/tgw-format.md`,
"Inflating the payload". Do not add size
checks of your own: the server enforces them and they can move without a
client update; the sandbox is the security boundary.

There is **no file download and no loading state for a file**: the bytes are
already in the message. The only thing to show before the first frame is the
bubble at the size the bot asked for (below) while the sandbox boots, which
takes tens of milliseconds warm and a few hundred cold. A widget is fixed code
plus changeable state: the HTML never changes after sending, and the bot edits
only the widget's state (and the caption), which goes to the live host with
`setData`, never through a new host ("The widget's state and requests", below).
A forwarded message carries a copy of the bytes.

**Enabling.** The server has no flag for widgets. Each client turns rendering
on with its own platform/version flag (a debug or rollout flag, not app
config); with it off, and on clients below layer 233 which receive
`messageMediaUnsupported`, the message shows as unsupported media with its
caption. Keep that fallback.

**Caption and preview.** The media caption is the preview text for the chat
list, notifications and search; without one show the localized "Widget" stub,
as for other media without a caption.

**Testing.** Everything in the shared test channel is a real layer-233
message: a test bot sends each widget to a tester's private chat (a bot may
also send widgets to the basic groups and supergroups it is a member of, and
via its inline mode; a channel gets them only as forwards) and the messages are
forwarded into the channel; the
copy works like the original. That includes every conformance widget
(`packages/conformance`: each one fits a mini-widget), with its reference
picture posted as a photo next to it in the order of
`packages/conformance/channel.json`, the corpus widgets that fit, forward-copy
storage, the bot's edits of the state and the size anchors from the bot
(`docs/tgw-format.md`).

## Bubble sizing

`messageMediaWidget` carries two size anchors, `width` and `height`, in logical
pixels (CSS px, pt, dp); `0` means auto and each axis is independent. Every
axis of the bubble is bounded, auto included. The shared constants of v0.1.0,
the same on every client and tunable in a later release: minimum width 200,
height within **[50, 600]**.

| `width` | `height` | before the widget loads | after the first render |
|---|---|---|---|
| 0 | 0 | the default placeholder at the bubble's width | the content height the host reports, clamped to [50, 600], cached per message |
| 0 | H | a bubble of height clamp(H, 50, 600) at the bubble's width | unchanged: the fixed height stays; the reported content height is ignored |
| W | 0 | a widget W px wide, W clamped to [200, the bubble's maximum width]; the default placeholder height | the content height, clamped to [50, 600], cached per message |
| W | H | a fixed widget: width clamp(W, 200, max), height clamp(H, 50, 600) | unchanged |

Width: `0` means the widget takes the bubble's width, the widest the chat
gives a bubble; `W > 0` is a fixed-width widget, and the host is created with
that width (`WidgetHost(width:)`, `widthDp`, the web host's container). Height:
in auto mode use the height the host reports (`onChange` / `onResize`),
clamped to [50, 600], and **cache it per message**, so the bubble opens at
the right height next time the message scrolls in and the list does not jump;
a fixed height is applied from the start and the reported height does not
change it.

The hosts clamp the reported height to these bounds by default since v0.1.0
(web `MIN_HEIGHT` / `MAX_HEIGHT`, Swift `minHeight` 50 and `Options.maximumHeight`
600, Kotlin `minHeightDp` 50 / `maxHeightDp` 600, C++ [50, 600]), so a client on
defaults gets the rule; a client that needs other bounds passes its own cap.
Content taller than the bubble's height (600 in auto mode, or the fixed height)
is **hard-clipped**: it does not scroll inside the bubble, and there is no
fullscreen or "open" button in v0.1.0. The width is never changed by the
widget.

**Which edge stays put** (v0.1.3, `docs/sdk-v1.md` §2.12, §5.6). The widget
chooses it with `TelegramWidget.setResizeAnchor('top' | 'bottom')`, and the
host reports it with every height: Swift `WidgetDiagnostics.resizeAnchor`
(`WidgetResizeAnchor`), Kotlin and C++ `WidgetDiagnostics.resizeAnchor`
(`"top"` / `"bottom"`), web `onResize(height, anchor)` and
`WidgetHost.resizeAnchor`; `top` until the widget sets one. When an auto height
changes on screen, compensate the list's scroll so that edge does not move:

| anchor | the bubble | what moves | in a list laid out from the top |
|---|---|---|---|
| `top` (default) | grows and shrinks downward | the content below it | nothing to do |
| `bottom` | grows and shrinks upward, its bottom stays | the content above it | add the height difference to the scroll offset |

In a chat list that is laid out from the bottom (inverted), `bottom` is what the
list does anyway and `top` needs the compensation instead. Apply it to every
height change of the bubble: a size report, the cached height giving way to the
first report, a re-created bubble (the new run reports its own anchor with its
height). A bubble off screen keeps what is on screen in place, as any list item
does. Keep the anchor next to the cached height per message. If your list
animates height changes, pin the widget view (or the snapshot you show during
the animation) to the anchored edge of the bubble, so the old pixels stay
aligned with the edge that stays put. The web dev app and the feed demo do all
of this (`apps/web/src/main.ts`, `feed.ts`); `corpus/resize` has one widget per
anchor to try it.

## The time over a widget with no caption below it

Without a caption, and with a caption shown above the widget (`invert_media`, as on
a photo whose caption is moved up), draw the message time and its status (ticks,
"edited", views) over the widget's bottom corner, on a backing, as over a photo:
the right corner in a left-to-right interface, the left one in a right-to-left one.
With a caption below the widget the time goes into the caption's line and nothing is
drawn over the widget: pass 0 (`docs/sdk-v1.md` §2.17, §5.12).

Pass what the time takes as the host's `contentSafeArea` (points, dp, CSS px):
`bottom`, the height of its band at the widget's bottom edge, and `cornerRight` (or
`cornerLeft` in a right-to-left interface), the width of the corner it takes in that
band, backing and margins included; the rest 0. Pass it **at creation**, so the first
height the host reports already has the band, and change it with
`setContentSafeArea` only when the bubble's layout changes (a caption added or
removed by an edit, or moved above or below the widget, "edited" appears, the views
counter grows). Never per frame, and
not for the floating date pill, which has no contract. The time is your view over
the widget: its taps are yours; nothing in the gesture policy changes.

The widget sees it as `env(safe-area-inset-bottom)` (the validator rewrites it to a
root variable the renderer sets), `--tg-content-safe-area-inset-*` /
`--tg-content-safe-area-corner-left|right` and `TelegramWidget.contentSafeAreaInset`;
the renderer's UA layer pads an unstyled body by the band. A client that passes
nothing keeps v0.3.0's behaviour (all 0). The dev app's **time** control and
`?time=corner|corner-rtl` show it; `visual/17-time-corner` and `auto/36-safe-area`
are its conformance cases (sent without a caption, and once more with the caption
above), and `corpus/safe-area` has examples (its `07` is posted three times: no
caption, a caption below, a caption above).

## Heavy boxes

Since v0.6.0 a widget has at most 16 heavy boxes at once, of them at most 2 with `backdrop-filter`; past either it
stops like at any limit (`onDead` / `WidgetDiagnostics.dead`: `Paint limit exceeded: 17 boxes with shadows, filters or
transparency (max 16)` or `Paint limit exceeded: 3 boxes with backdrop-filter (max 2)`, the diagnostic `paint-limit` /
`backdrop-limit`). A box is heavy when its computed style has a blurred `box-shadow` layer, a `filter`, a
`backdrop-filter` or `opacity` below 1, or while an animation of one of them runs on it (`docs/decisions.md`, "Heavy
boxes"). The opacity of a `<button>` that is disabled or held down (`:active`) is its own dimming and does not count,
so the client-styled button's disabled and pressed looks never stop a widget. Only the renderer knows the cascade, so
**the renderer counts**:

- **The framework's WebView renderer** (`RendererWebView` on iOS, macOS and Android, the iframe and Shadow DOM
  renderers on the web) counts by itself, before each frame it draws, and reports to the host. Nothing to do; keep
  `policy` `strict` (under `compat` nothing is counted).
- **A renderer of your own** behind the `Renderer` seam counts the same rule and calls the seam once, before it draws
  the frame that would pass the limit: `renderer.limitExceeded(.paint, count: 17)` (Swift),
  `limitExceeded(WidgetPaintLimit.PAINT, 17)` (Kotlin), `limitExceeded(WidgetPaintLimit::Paint, 17)` (C++); the
  WebView renderer's message on the web is `{ type: 'limit', code, count }` (`docs/protocol.md`). The host does the
  rest. The renderer then draws the stopped widget without those effects (no blurred shadow, filter, backdrop-filter
  or transparency; animations stopped): the frozen frame costs nothing while the chat scrolls.
- **Count against the fixture.** The rule is `packages/core/src/shared/paint-policy.ts` (`boxPaint`,
  `animationPaint`, `PaintCounter`) and `packages/core/tests/fixtures/paint-policy.json` is its contract: computed
  values to heavy or not (blur 0, inset without blur, opacity 0.999 and 1, `brightness(1)`, a `::before` with `content:
  none`, `visibility: hidden`, a disabled or pressed `<button>` dimmed, one with a blurred shadow), keyframes to an animation that counts or not, and sequences of batches and animation
  events to the counts and the moment of the stop. Count after a whole batch (never op by op), when an animation of
  the rule starts or ends (CSS animation and transition events, a Web Animation that runs), and when the cascade changes
  without an op (the theme, the safe area, the bubble's width, `:hover`, `:active`). You may recount only the boxes a
  change reaches (the changed elements and their subtrees, the parent's subtree with a sibling combinator or a structural
  pseudo-class in the widget's sheets, all of them for a stylesheet change). The conformance set has the cases
  (`auto/37`–`43`).

## Input

- **Text selection is the widget's choice** (since v0.1.1, `docs/sdk-v1.md`
  §2.4, §5.10). Text is not selectable by default; text the widget marks
  `user-select: text` is. A long press over text is the bubble's (below), so
  on touch a selection inside a widget starts the way your client
  selects text in an ordinary message (its menu's Select, or its selection mode); with a mouse,
  a drag over selectable text selects it. Copy is your normal copy command.
  The bubble never lets the user start a drag of the widget's content. A
  native renderer that cannot select yet keeps the text unselectable.
- **No keyboard input, no focus.** Widgets get no key events and never take
  keyboard focus (`docs/decisions.md`, "Profile v1"). The chat's input field
  keeps the focus while a widget is on screen; a widget's `focus()` must not
  move it (one of the known issues of v0.1.0 on the web, see below) and the
  conformance set checks `chatFocused` for exactly this.
- **User activation is a pointer press only.** A link call (`openLink`,
  `openTelegramLink`, a tapped `<a href>`) goes through only from the handler of a real
  tap or click the renderer saw (`trusted`), one call per press; no key press
  counts, and a finger's press counts from its release (it may still become the
  chat's scroll). A renderer behind your own `Renderer` implementation must mark its
  real input `trusted: true` or the widget never gets a gesture, but not a finger's
  or a pen's `pointerdown`.
- **Who gets a press: `touch-action` is the claim, and vertical drags are
  always the chat's** (v0.1.2 and v0.1.3, `docs/sdk-v1.md` §5.5). On every
  primary pointer press the host reports the widget's claim (`onGestureClaim`
  in Swift and on the web, `onGesture` in Kotlin and C++): horizontal drags
  (`x`: from `pan-y` or `none`) and `hold` (only from `touch-action: none`);
  `y` is always false since v0.1.3. Taps and double taps over the widget are
  always the widget's: your double-tap reaction must not act over the widget
  (it stays on the bubble outside it), and the renderer gives the widget its
  own `dblclick`. Drive your bubble's recognizers from the shared
  `GestureArbiter` (every package ships it with its thresholds: 10 px slop,
  500 ms hold, 300 ms / 40 px double tap, 20 px leading edge strip): a
  vertical drag always scrolls the list, over every element; over an element
  that does not claim the press, a hold opens the message menu and a
  horizontal swipe is reply or back; in each case you cancel the widget's
  touch the platform's way (it gets `pointercancel`, no `click`). Over one
  that claims it, you stand back for that gesture and the renderer gives the
  widget its own `contextmenu`. Never stop your list from scrolling because of
  a claim. The bubble outside the widget stays yours. With a mouse, a right
  click follows the same claim; clicks, double clicks, holds and drags are the
  widget's. On Android `RendererWebView` keeps its parent from intercepting
  only while a hold claim may still win. On the web pass `bubbleGestures` and
  the renderer plays the bubble itself (`onBubbleGesture` says what it took);
  the browser's vertical pan stays allowed over every widget. The conformance
  widgets `auto/23-gestures-a` / `-b` and `visual/sdk/12-drag-gestures` check your
  client.
- **What the widget asks for** arrives as `onAction`, checked: `openLink(url)`
  (show a confirmation with the full URL, then the browser),
  `openTelegramLink(url)` (your own t.me handling, as a tapped `t.me` link in a
  message: no confirmation of its own), `haptic(…)` (play it or
  ignore it on desktop and web), `ready`. `docs/sdk-v1.md` §5 is the client's
  side of each. The widget's state and requests add two callbacks of their own:
  `onRequest` and `onCopyText` (below).

## The widget's state and requests

A widget is fixed code plus changeable state. The HTML is set when the bot
sends the widget and never changes; the state changes, and only the bot writes
it: the **chat state**, `chat_data` (tied to this message, seen only in its
chat, never forwarded), and the **shared state**, `public_data` (seen wherever
the widget is shown: a forward gets a copy as of the forward, or, in a shared
widget, the one shared state of all its messages and forwards). The widget reads
its state and cannot change it itself; it can send the bot a request, a callback
button inside the widget (`docs/sdk-v1.md` §2.13–§2.15 and §5.11; the server's
side, `docs/tgw-format.md` "The widget's state and requests"). The host
interface is the same on every platform (`docs/sdk-v1.md` §4 has the exact
types):

| the client passes or implements | what it is |
|---|---|
| `data` (at creation) | `{ chatData, publicData }`: the message's chat state and shared state as the client has them now; none = null |
| `context` (at creation, fixed) | the server's flags from `messageMediaWidget`, read, not derived: `isCopy` (`is_copy`), `canQuery` (`can_query`: requests on a tap; never set for a widget the bot sent without `allow_queries`), `backgroundQuery` (`background_query`: requests without a tap too, one when the widget appears on screen, then one per 5 s while it is visible; one the widget makes off screen, at boot included, waits in the framework and reaches `onRequest` once `setVisible(true)` reports the bubble on screen); `chatType` (the kind of chat: `private`, `group`, `supergroup`), `viewer` (the account's own: its user id, first and last name, username and Premium flag, as the bot gets them in `from`). Pass them for every message: the framework gives no copy the chat type, and no message without `canQuery` the viewer (the widget knows who views it only where it may send requests). No flags: no requests |
| `setData(data)` | the state changed: the full new state (the chat state and the shared state); the widget gets `chatDataChanged` / `publicDataChanged`, nothing restarts. Call it on `updateMessageWidgetData` (apply the chat state and the shared state it carries) and when polling brings a new shared state (below) |
| `onRequest(request, complete)` | `{ data }`: call `messages.getBotWidgetQueryAnswer` (the peer, the message id, `data` as UTF-8 bytes) and complete with its `{ result }`, or a failure: `timeout` for `WIDGET_RESPONSE_TIMEOUT`, `rateLimited` for `FLOOD_WAIT`, `failed` for anything else; complete every request exactly once. The answer carries no state and no toast, alert or link: show nothing for it, and do not re-fetch the state after it |
| `onCopyText(text)` | copy it and show the client's toast with the copied text |
| `onWatchPublicData(on)` / `watchesPublicData` | the widget asks to follow its shared state live (`watchPublicData()`) or stops: poll its `public_data` only while it watches, is on screen and has a `widget_id` (below); called on a change, the field has the latest (web `WidgetHost.watchesPublicData`, native `WidgetDiagnostics.watchesPublicData`) |
| `context.preview` | a widget in a list of inline results, before it is sent (`botInlineMessageWidget` with its state, `public_data` and `chat_data`, and a `widget_id` for a result by reference, which takes the shared state from its object; a result has no flags): no input (a tap on it selects the result: route no touches to the view), no requests or actions, no viewer, no storage, no network marker. Create a new host for the message once it is sent |

**The network marker** (a UI contract of the server's spec). When the message
has `can_query` (`context.canQuery`), mark the widget as network-capable: "a
tap may send data to the bot's server", a small sign in the bubble that
explains itself on a tap or hover. Without `can_query` show no marker: the bot
sent the widget without `allow_queries`, or this copy may not ask, and the
server refuses every request from it, so the marker is exactly as honest as the
flag. A preview has no flags and no marker; a client that sets no `onRequest`
delivers no request and shows no marker either. The dev app draws one in its
bubble's header (`?message=private`: shown; `no-queries`, `copy`, `preview`:
none).

**Applying the bot's edits of the state.** `updateMessageWidgetData` carries
the new chat state (`chat_data`) and/or shared state (`public_data`) of one
message (a field not in it is unchanged, an empty string clears it). In private
chats and basic groups it comes in the account's pts sequence, as
`updateEditMessage` does; in a supergroup the post is updated through the usual
edit of channel posts, with the channel's pts (`updateEditChannelMessage` and
the like). Apply it by the rules of that sequence, store the state with the
message and pass the full new state to the live host with `setData`.

**Polling a shared widget's shared state.** When the media has a `widget_id`,
the widget is a shared object: its payload, size anchors and shared state
(`public_data`) are the same in the original, every forward and every other
message the bot sent by that id, and the bot changes the shared state for all
of them at once. Only **widgets that watch** are polled: a widget asks to follow
its shared state live with `TelegramWidget.watchPublicData()` and stops with
`unwatchPublicData()`, and the host reports the latest (web `onWatchPublicData(on)` and
`WidgetHost.watchesPublicData`; Swift, Kotlin and C++ `onWatchPublicData` and
`WidgetDiagnostics.watchesPublicData`). Every other widget gets its shared
state with the message only; a widget that watches without a `widget_id` changes
nothing (there is no object to poll), and a preview never watches. While
widgets with an id are on screen and watch, the messenger (never the sandbox)
polls **one visible carrier per `widget_id`** with
`messages.getWidgetPublicData`,
one message per call, with the hash of the shared state it has (`0` at first):
`widgetPublicDataNotModified` changes nothing, `widgetPublicData` brings the new
shared state, which the client applies with `setData` to **every** widget on
screen with that id (and stores with each message). Every answer carries the object's
`period`, the recommended cadence: poll that object again after it (the
server's period: 5–3600 s, 30 s by default, which also applies before the
first answer). An edit is visible at once to any read, so a message loaded
later is current anyway. A widget without a `widget_id` keeps the shared state
its message has (a forward's copy never changes); the chat state (`chat_data`)
is never polled: the server pushes it.

Optional: widgets with the same `widget_id` are one widget, so a client may
cache the checked payload (the inflated HTML) and the current shared state per
`widget_id` and share them between the messages that show it; the host still
gets the HTML and the state per message, as above.

The framework checks everything coming out of the sandbox (the limits of a
request's `data`, the flags, one tap per request or the background rules, one
request in flight, the `copyText` tap) and everything going in (the chat state,
the shared state and `result`, each a text of up to 1024 bytes (UTF-8) without a
NUL; a copy without chat state):
the client only delivers. A client that sets no `onRequest` gets every request
refused in the widget (`NOT_ALLOWED`). Requests are never sent over the
widget's own channels: the sandbox has no network (invariant 5); the client's
MTProto connection carries them.

## The wallet

A widget can pay and sign through Gram Wallet (`docs/sdk-v1.md` §2.18, §5.13). Nothing changes until the client
passes `context.wallet = true`: the client supports the wallet and the wallet is available to this user (their
country). Then:

| Host input / callback | What the client does |
|---|---|
| `WidgetRequest.wallet` | the widget asked, on a tap, for the user's wallet: call `getBotWidgetQueryAnswer` with `widgetQueryTypeData` and `with_wallet`; the server attaches the profile's wallet card (the cloud wallet) to the bot's update and creates the wallet if there is none |
| `onWallet(call, complete)` | `sendTransaction` (a `WalletTransaction`: TON Connect's `sendTransaction` request, `json` / `toJson()` in its field names) or `signData` (a `SignDataPayload`, TON Connect's payload), with `data`: show the existing TON Connect window over the chat with the bot of the message in its header, ignore taps on it for its first 0.5–1 s, top up an empty wallet over the chat; on confirmation send the transaction the wallet's way (or sign for the domain `bot<bot_id>`), send the outcome to the bot as a widget query with `data` (`widgetQueryTypeSentTransaction` with the normalised message hash, or `widgetQueryTypeSignData` with `signData`'s result, each with the device's wallet as `userWallet`: the address (raw or user-friendly, mainnet), the balance in nanotons, the 32-byte public key and the wallet's StateInit as a bag of cells (one flag: both or neither, both with a signature); it can differ from the profile's card when the wallet's backup is off; the formats: `docs/sdk-v1.md` §5.13), and complete with the bot's answer. Decline: `rejected`; the wallet's refusal: `badRequest`; the query's failures as a request's |

The framework has checked the transaction or the payload (TON Connect's fields only, 1–255 messages, user-friendly destinations), the tap or the
grant (the answer to a call made by a tap, within 10 s), and one call in flight with the requests. The server's API
is agreed and not shipped yet (`docs/decisions.md`, "The wallet"); until it is, leave `context.wallet` unset.

## Forwarding and state

A widget message may be forwarded to any chat, groups and channels included.
The copy runs the same HTML (isolated, no network) with the copy's own flags
(`is_copy`; `can_query` only when the bot sent the widget with
`fwd_allow_queries`, and then on a tap only) and a shared state: its own, frozen as of the forward, or the
shared widget's one shared state, which changes and is polled as above. It never has
a chat state, and it is told nothing about the chat it is in (no chat type: the
bot must not learn where its widget was forwarded, and the server sends the bot
neither the chat nor its type with a request from a copy); the viewer only with
`can_query`. It is a different message, so **each copy has its own storage**: keep Web Storage per (account, chat, message id), pass it as the
host's `storage` at creation and keep what `onStorageCommit(area, values)`
hands you — `local` on disk (with a short delay, and at once when the widget
is hidden or destroyed and when the app goes to the background), `session` in
memory — and delete both with the message, the chat, the account and on "Clear
cache" (`docs/sdk-v1.md` §5.4). The framework enforces the 4 KB quota.

**Theme and language.** Pass the chat's theme as a `WidgetTheme` (colour
scheme, the Mini Apps palette keys as `#rrggbb`, your button's tokens, your
message text's tokens) and call `setTheme` on every change; pass the UI
language as `locale` (`docs/sdk-v1.md` §2.3, §2.4, §2.9).

- The palette is the colours of **this bubble**. Keep the widget view
  transparent over your bubble as before: the bubble you draw in `bg_color` is
  the widget's canvas, its text is drawn in `text_color`, and a background the
  widget sets on its root or body covers the whole bubble. **The widget view
  fills the bubble: no padding of the bubble's own around it** (the widget's
  body margin, the `padding` text token, is the inset), and the bubble clips
  the view at its corner radius, so a widget background reaches the rounded
  edges (`visual/15-body-background`). A widget the user
  sent or forwarded sits in an outgoing bubble: pass the outgoing colours
  (fill, text, hint, link, separator).
- The text tokens (since v0.1.1) are your message text: `fontSize` in points /
  dp = CSS px, following the chat's text-size setting (call `setTheme` when the
  user changes it), `lineHeight` (unitless) and `padding` (the inset of text in
  your bubble, CSS lengths; the body's margin, so `body { margin: 0 }` in a
  widget still goes edge to edge). Without them the renderer uses the reference set
  15 / 1.35 / `8px 12px`. A widget without CSS is then drawn in your text style
  on your bubble's colour; any widget CSS wins (`docs/sdk-v1.md` §2.4).

## iOS and macOS (Swift package)

Add the repo as a package dependency (the manifest is the repo-root
`Package.swift`; a local checkout works with a path dependency), product
`WidgetSandbox`. iOS 16 / macOS 10.13 (the demo apps need macOS 13).

```swift
import WidgetSandbox

var options = WidgetHost.Options()                  // policy .strict, features [] by default
options.features = ["webAnimations"]
options.locale = "ru"
options.storage = WidgetStorage(local: savedLocal, session: savedSession)   // this message's Web Storage
options.contentSafeArea = media.caption == nil || message.invertMedia ? WidgetContentSafeArea(bottom: timeBand, cornerRight: timeWidth) : WidgetContentSafeArea()   // no caption below: the time over the corner
options.data = WidgetData(chatData: media.chatData, publicData: media.publicData)                    // the chat state and the shared state
options.context = WidgetContext(isCopy: media.isCopy, canQuery: media.canQuery, backgroundQuery: media.backgroundQuery,
                                chatType: .private, viewer: WidgetViewer(id: me.id, firstName: me.firstName, lastName: me.lastName,
                                                                         username: me.username, isPremium: me.isPremium))   // the server's flags; the account
let theme = WidgetTheme(colorScheme: .light, params: ["bg_color": "#ffffff", "text_color": "#000000" /* … every key, this bubble's */],
                        button: WidgetButtonTokens(radius: "10px", font: "500 15px system-ui"),
                        text: WidgetTextTokens(fontSize: 17, lineHeight: 1.3, padding: "6px 10px"))
let host = try WidgetHost(html: html, theme: theme, width: 360, options: options)
host.onChange = { d in /* d.height, d.loaded, d.dead, d.lastError, d.stats */ }
host.onAction = { action in /* .openLink(url): confirm, open; .openTelegramLink; .haptic; .ready */ }
host.onStorageCommit = { area, values in /* keep values as the new content of that area */ }
host.onGestureClaim = { claim in /* x, y, hold: feed your bubble's GestureArbiter (docs/sdk-v1.md §5.5) */ }
host.onRequest = { request, complete in /* getBotWidgetQueryAnswer(request.data); complete(.success(WidgetRequestAnswer(result: answer.result))) or .failure(.timeout) */ }
host.onCopyText = { text in /* copy it, show "Copied: \(text)" */ }
// updateMessageWidgetData, or a polled shared state: host.setData(WidgetData(chatData: …, publicData: …))
bubble.addSubview(host.view)          // UIView on iOS, NSView on macOS; its height follows the widget
// later:
host.setVisible(false)                // off screen
host.setTheme(darkTheme)
host.destroy()
```

SwiftUI: `WidgetView(host:)` wraps the view, `WidgetModel` is an
`ObservableObject` that owns a host and publishes its diagnostics.

For a list of many bubbles, `WidgetHost.Options` has `sharedWebKit`, `pools`,
`parallelBoot`, `asyncValidation`, `sharedValidatorThread`; `docs/chat-feed-ios.md`
explains what each buys and `apps/ios/DemoApp/Sources/ChatFeed` is a working
list.

## Android (Gradle library)

Coordinates `org.telegram.widgetsandbox:widget-sandbox`. Inside this repo
`apps/android` takes them from `packages/android` through
`includeBuild("../../packages/android")`; a client outside the repo does the
same with a checkout at the tag, or takes the AAR from a Maven repository once
one is published. minSdk 26; the AAR carries the QuickJS native library for
arm64-v8a and x86_64 and the JS bundles as assets.

```kotlin
import org.telegram.widgetsandbox.WidgetHost

val options = WidgetHost.Options(features = setOf("webAnimations"),   // policy "strict" by default
    locale = "ru", storage = WidgetStorage(local = savedLocal, session = savedSession),
    contentSafeArea = if (caption == null || message.invertMedia) WidgetContentSafeArea(bottom = timeBand, cornerRight = timeWidth) else WidgetContentSafeArea(),   // no caption below: the time over the corner
    data = WidgetData(media.chatData, media.publicData),
    context = WidgetContext(isCopy = media.isCopy, canQuery = media.canQuery, backgroundQuery = media.backgroundQuery,
                            chatType = "private", viewer = WidgetViewer(me.id, me.firstName, me.lastName, me.username, me.isPremium)))   // the server's flags; the account
val theme = WidgetTheme("light", params = mapOf("bg_color" to "#ffffff" /* … every key, this bubble's */), button = WidgetButtonTokens(radius = "10px"),
                        text = WidgetTextTokens(fontSize = 16.0, padding = "6px 10px"))
val host = WidgetHost(context, html, theme, widthDp = 360.0, options = options)
host.onChange = { d -> /* d.height, d.loaded, d.dead, d.lastError */ }
host.onAction = { action -> /* WidgetAction.OpenLink(url): confirm, open; OpenTelegramLink; Haptic; Ready */ }
host.onStorageCommit = { area, values -> /* "local" | "session": keep values */ }
host.onGesture = { claim -> /* x, y, hold: feed your bubble's GestureArbiter (docs/sdk-v1.md §5.5) */ }
host.onRequest = { request, complete -> /* getBotWidgetQueryAnswer(request.data); complete(Result.success(WidgetRequestAnswer(answer.result))) or Result.failure(WidgetRequestException(WidgetRequestFailure.TIMEOUT)) */ }
host.onCopyText = { text -> /* copy it, show the text */ }
// updateMessageWidgetData, or a polled shared state: host.setData(WidgetData(…, …))
bubble.addView(host.view)             // a View whose height follows the widget (minHeightDp / maxHeightDp in Options)
host.setVisible(false); host.setTheme(darkTheme); host.destroy()
```

`SandboxResources.get(context)` loads the bundles from the library's assets
once per process. `Options.pools` / `sharedValidatorThread` are the list
optimisations ported so far (`docs/android-renderer-bench.md`).

## Web (npm package)

`@telegram/widget-sandbox` (a pnpm workspace package here; pin it as above).
The host runs the widget in a Worker with QuickJS compiled to wasm and renders
through the Shadow DOM renderer (the product renderer, `docs/shadow-dom-renderer.md`)
or into a sandboxed `srcdoc` iframe (the dev app's default and the reference).
Two requirements on the embedding page:

- the page must be **cross-origin isolated** (`Cross-Origin-Opener-Policy:
  same-origin`, `Cross-Origin-Embedder-Policy: require-corp`) — the sync
  query bridge is a `SharedArrayBuffer`; the Vite plugin sets the headers in
  dev, your server must send them in production — and, for the Shadow DOM
  renderer, the page's own CSP must close the network as `docs/decisions.md`
  ("The web renders through Shadow DOM") lists;
- the two in-page bundles are built from the package's sources by its Vite
  plugin (`virtual:sandbox-bundle`, `virtual:renderer-bundle`), so the host
  must be bundled with Vite + `widgetSandbox()` from `@telegram/widget-sandbox/vite`.

```ts
// vite.config.ts
import { widgetSandbox } from '@telegram/widget-sandbox/vite';
export default { plugins: [widgetSandbox()] };

// in the app
import { WidgetHost } from '@telegram/widget-sandbox/host/widget-host';
import { ShadowDomRenderer } from '@telegram/widget-sandbox/host/shadow-renderer';
const host = new WidgetHost(bubbleElement, html, {
  theme: { colorScheme: 'light', params: { bg_color: '#ffffff' /* … every key, this bubble's */ }, button: { radius: '10px' }, text: { fontSize: 16, padding: '6px 10px' } },
  locale: 'ru',
  storage: { local: savedLocal, session: savedSession },   // this message's Web Storage
  contentSafeArea: caption && !message.invertMedia ? {} : { bottom: timeBand, cornerRight: timeWidth },   // no caption below: the time over the corner
  data: { chatData, publicData },  // the chat state and the shared state
  context: { isCopy, canQuery, backgroundQuery, chatType: 'private', viewer: { id: me.id, firstName: me.firstName, lastName: me.lastName, username: me.username, isPremium: me.isPremium } },   // the server's flags; the account
  onRequest: async ({ data }) => ({ result }),   // getBotWidgetQueryAnswer; or throw 'TIMEOUT' | 'RATE_LIMITED' | 'FAILED'
  onCopyText: (text) => {},        // copy it, show the text
  features: ['webAnimations'],     // policy 'strict' by default
  rendererFactory: ShadowDomRenderer.factory,
  onResize: (h) => {},            // CSS px
  onAction: (action) => {},       // { kind: 'openLink', url } | 'openTelegramLink' | 'haptic' | 'ready'
  onStorageCommit: (area, values) => {},
  onGestureClaim: ({ x, y, hold }) => {},
  bubbleGestures: true,           // no recognizers of your own over the widget: the renderer plays the bubble
  onBubbleGesture: ({ action }) => {},   // 'menu' | 'swipe' | 'scroll' | 'back'
  onDead: (reason) => {},
});
host.setVisible(false); host.setTheme(darkTheme); host.destroy();
host.setData({ chatData, publicData });   // updateMessageWidgetData, or a polled shared state
```

`workerPool` (a `WorkerPool` of warmed workers) is the option for a feed of
many bubbles; `docs/chat-feed-web.md`.

## Desktop (C++ core)

`packages/desktop` is a CMake project (`add_subdirectory` or `FetchContent`),
targets `twidget-sandbox` (+ `twquickjs`), C++20, no Qt. It has no renderer of
its own: you implement `twidget::Renderer` (`renderer.h`) over the widget
toolkit you have, or go one level down and drive `SandboxEngine` with your own
delegate. `packages/desktop/README.md` § Embedding has the threading rules
(engine callbacks arrive on the engine thread; `onQuery` is synchronous).

```cpp
#include <twidget/widget_host.h>
auto resources = twidget::SandboxResources::fromDirectory("<path to WidgetJS>");  // the three bundles and their bytecode
twidget::WidgetHostOptions options;            // policy "strict" by default
options.features = {"webAnimations"};
options.locale = "ru";
options.storage = {savedLocal, savedSession};  // this message's Web Storage
if (!media.caption || message.invertMedia) options.contentSafeArea = {.bottom = timeBand, .cornerRight = timeWidth};   // no caption below: the time over the corner
options.data = {media.chatData, media.publicData};                          // std::optional<std::string> each
options.context = {.isCopy = media.isCopy, .canQuery = media.canQuery, .backgroundQuery = media.backgroundQuery, .chatType = "private", .viewer = twidget::WidgetViewer{me.id, me.firstName, me.lastName, me.username, me.isPremium}};
twidget::WidgetTheme theme{"light", {{"bg_color", "#ffffff"} /* … every key, this bubble's */}, {}, {.fontSize = 14, .padding = "6px 10px"}};
twidget::WidgetHost host(html, theme, 360, options, resources, myRendererFactory);
host.onAction = [](const twidget::WidgetAction& a) { /* OpenLink: confirm, open; OpenTelegramLink; Haptic; Ready */ };
host.onStorageCommit = [](const std::string& area, const std::map<std::string, std::string>& values) {};
host.onGesture = [](const twidget::WidgetGestureClaim& c) { /* feed your bubble's twidget::GestureArbiter */ };
host.onRequest = [](const twidget::WidgetRequest& r, std::function<void(const twidget::WidgetRequestOutcome&)> respond) { /* send r.data; respond({.answer = {.result = …}}) or respond({.failure = twidget::WidgetRequestFailure::Timeout}) */ };
host.onCopyText = [](const std::string& text) { /* copy it, show the text */ };
host.start();
// the bot edited the state: host.setData({newChatData, newPublicData});
// host.setVisible(false); host.setTheme(darkTheme); host.destroy();
```

## The conformance set

`packages/conformance` ships with the tag: 40 self-checking widgets
(`auto/`), 20 visual widgets and 17 client-SDK widgets for the test channel
(`visual/`), all of them mini-widgets, the feature catalogue, reference images,
the posting order (`channel.json`) and the report tool. **`packages/conformance/CLIENTS.md`**
is the step-by-step guide for a client team: configure the host as in
production, load each widget, read its result from the validated ops, perform
the scripted taps, crop the stages, and run `node packages/conformance/tools/report.mjs`
to get the per-renderer report that is compared across clients. Run it at
every framework update; `catalogue.json` `version` is the profile version.

## What the host does not do

- Open a link, play a haptic or show a dialog: `onAction` is the widget
  asking, already checked; the app confirms and does it (or ignores it). The
  widget is never told the outcome.
- Talk to the bot by itself: a request reaches the bot only through the app's
  `onRequest`, and the widget's state only through `data` and `setData`; the
  sandbox has no network.
- Persist anything, or share anything between widgets: the framework hands
  you each storage commit, the app stores it; each widget is a fresh runtime
  with its own limits.
- Load fonts, media or remote images: `data:` images only (`docs/decisions.md`).
- Check the payload's size or origin: the server compresses it and enforces the
  limits, the client trusts it and sandboxes the content.
