# Chat feed on iOS: a widget in every bubble, as fast as WKWebView allows

The single-widget screens of the demo answer "does this HTML work in the
sandbox"; a messenger asks a different question: *a list of a hundred
bubbles, each with its own widget, scrolled with a thumb — does it stay
responsive, and how long does a bubble stay blank?* This note is the design of
the chat-feed demo (`apps/ios/DemoApp/Sources/ChatFeed/`, iOS only), the
optimisations it measures, and the numbers. Every optimisation is a switch
(`FeedConfig`), so `baseline` (the host exactly as the single-widget screen
uses it) and `max` (everything on) run in the same build, on the same corpus,
with the same scripted scroll.

## What a bubble costs without any of this

One `WidgetHost` is three things, each with a start-up cost on the critical
path of a bubble that just scrolled in:

| part | work before the widget can show anything | where it runs |
|---|---|---|
| renderer (`WKWebView`) | configuration, data store, rule list, web content process (the first one), document load, renderer bundle parse + `ready` | main thread (creation), web process (load) |
| engine (QuickJS) | thread, runtime, **sandbox bundle evaluation** (linkedom + shim, 600 KB of bytecode), then the widget's own HTML/scripts | the engine's `JSThread` |
| validator (trusted QuickJS) | thread, runtime, validator bundle (css-tree), then every ops batch | the validator's thread, **synchronously from the main thread** per batch |

And the single-widget host serialises them: the engine does not even start
until the renderer says `ready`. On the simulator (Release) that is ~380 ms
from creation to the first content of a bubble, every bubble, plus a
main-thread stall of up to half a second per screen of bubbles.

## The optimisations

All of them are host glue; the three JS bundles, the validator and the
invariants are untouched. Each is a `WidgetHost.Options` flag or a pool the
app fills, so a product can pick.

1. **Shared WebKit environment** (`RendererEnvironment`, `Options.sharedWebKit`).
   One process pool, one non-persistent data store, one
   `WKUserContentController` (hardening script, the `tw` message handler, the
   content rule list) and one scheme handler for every renderer, instead of a
   fresh set per web view. The per-widget origin stays
   (`tgwidget://w-<uuid>/`): the shared scheme handler serves each registered
   document by its own URL and refuses everything else, and the shared message
   handler routes by `WKScriptMessage.webView`. The content rule list is looked
   up in `WKContentRuleListStore`'s on-disk cache before being compiled (a
   compile is ~50 ms on every launch otherwise).
2. **Pools** (`RendererPool`, `EnginePool`, `ValidatorPool`, `WidgetWarmup`,
   `Options.pools`). Renderer documents are loaded ahead of time and parked
   (their `ready` is buffered until a host adopts them); engines are created
   with the sandbox bundle already evaluated (`SandboxEngine.prewarm()`, the
   widget's HTML comes later with `start(html:config:)`); validators are
   trusted contexts built on a shared thread, recycled on `destroy()` with
   `__twValidator.create()` (a fresh instance, the bundle stays evaluated).
   Engines are never recycled: a runtime that ran widget code dies with the
   widget. Refills happen one at a time when the main run loop is idle and
   the list is not scrolling (`RendererPool.canRefill`).
3. **Parallel boot** (`Options.parallelBoot`): the runtime and the sandbox
   bundle are prepared on the engine thread while WebKit loads the document;
   only the widget's own boot waits for `ready`.
4. **Validation off the main thread** (`Options.asyncValidation`): ops,
   queries, `listen` and `preventDefault` are validated on the validator's
   thread and only the renderer call hops to the main thread. Order is kept —
   one serial validator thread feeding one serial main queue — so a sync layout
   query still sees the ops flushed before it (`PoolsTests`). With one shared
   validator thread (`Options.sharedValidatorThread`) a chat of widgets costs
   one thread for all its validators instead of one each.
5. **Transport**: `RendererWebView.transport = .callAsync` hands the JSON to
   `callAsyncJavaScript` as an argument instead of escaping it into an
   `evaluateJavaScript` string literal on the main thread.
6. **The list's own lifecycle** (`FeedModel`, `ChatFeedViewController`):
   - hosts are created on display or on prefetch (the collection view's
     prefetch API plus one screen ahead in the scroll direction while a flick
     is in flight);
   - a bubble that leaves the screen is hidden (`setVisible(false)`: no
     frames, timers at 1 Hz) and, beyond `keepScreens` screens away, dropped:
     the renderer is asked for a snapshot (`WidgetHost.snapshot`, at 2 px per
     point, kept as JPEG), the host is destroyed, the cell shows the picture
     until the bubble comes back and a new host from the pools replaces it;
   - heights are remembered per widget (`UserDefaults`), so a bubble is laid
     out at its final height before the widget renders, and kept there until
     `loaded` (markup-then-script widgets would shrink and grow back
     otherwise); height changes are coalesced into one layout invalidation per
     run-loop turn, and a bubble above the viewport resizes with a
     `contentOffsetAdjustment` so the reader's view does not move;
   - snapshots are decoded off the main thread (`preparingForDisplay`), a
     handful kept decoded near the viewport.
7. **Prewarm at launch**: a messenger fills the pools when the app starts, not
   when a chat opens. The autorun does the same (`prewarmAtLaunch`), and the
   setup screen prewarms while the reader looks at it.

## Measuring

`--autorun feed --feed max|baseline --feed-count N --feed-scroll step|flick
[--feed-set key=value,…]` scrolls the feed like a reader (`step`: one screen,
pause 0.9 s, repeat, down and back up) or like a hard flick (`flick`: 2500
pt/s to the end and back), then writes `feed-report.json` (the simulator's app
container, or the phone's Documents). Per widget: creation → renderer ready →
first content → loaded (the `WidgetDiagnostics` clocks), and the same from the
moment the bubble was displayed (negative when prefetch finished first), pool
hits, layout jumps, recreations, snapshot. Per phase: main-thread hitch rate
(`CADisplayLink` late time per second, the number Xcode's hitch instrument
reports), worst frame, dropped frames, freezes > 100 ms. Plus the process
memory footprint (the app process only — the web content process is WebKit's
and not counted), pool statistics and a breakdown of main-thread work
(`host-create`, `renderer-create`, `layout-invalidate`, …).

```bash
cd apps/ios/DemoApp && xcodegen generate && xcodebuild build -scheme TwidgetDemo -configuration Release -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' CODE_SIGNING_ALLOWED=NO -derivedDataPath /tmp/twidget-sim
xcrun simctl install booted /tmp/twidget-sim/Build/Products/Release-iphonesimulator/TwidgetDemo.app
xcrun simctl launch booted org.telegram.widgetsandbox.TwidgetDemo --autorun feed --feed max --feed-count 40 --feed-scroll step
cat "$(xcrun simctl get_app_container booted org.telegram.widgetsandbox.TwidgetDemo data)/Documents/feed-report.json"
```

Measure in Release: a debug build of quickjs-ng is several times slower and
moves every number.

## Numbers (iPhone 17 simulator, iOS 26.5, Release, 2026-10-10)

40 widgets (compat + the start of the generated corpus), each after a text
bubble; the same scripted scroll for both presets. "display → content" is
what the reader waits for: from the bubble appearing to the renderer's first
size report after validated ops; negative means the widget was ready before
the bubble came into view (prefetch). "loaded" is from host creation. Hitch
rate is main-thread late frame time per second of scrolling.

| `step` scroll (read a screen, flick, repeat) | baseline | max |
|---|---|---|
| display → content, median / p90 | 370 / 669 ms | ready before display / −195 ms |
| loaded (host creation → scripts done), median / p90 | 381 / 677 ms | 17 / 61 ms |
| renderer ready (creation → `ready`), median | 363 ms | 4 ms (pooled) |
| first screen on a cold open, display → content | 755 ms (3 bubbles) | 597 ms (process launch; the pools were still filling) |
| hitch rate down / up | 61 / 0 ms/s, worst frame 294 ms, 4 freezes | 4.2 / 2.1 ms/s, worst 53 ms, 0 freezes |
| layout jumps seen by the reader | 35 | 11 (post-load growth of charts and timers) |
| app memory peak | 277 MB (40 live runtimes) | 127 MB (≤ ~12 live, snapshots for the rest) |
| hosts created / dropped | 40 / 0 | 75 / 68 (recreated on the way back up) |

| `flick` scroll (2500 pt/s to the end and back) | baseline | max |
|---|---|---|
| widgets that managed to load | 31 / 40 | 40 / 40 |
| display → content, median / p90 | 393 / 690 ms | 85 / 121 ms |
| hitch rate down / up | 231 / 65 ms/s, worst 323 ms | 74 / 88 ms/s, worst 91 ms |
| layout jumps | 22 | 2 |
| app memory peak | 177 MB | 132 MB |

120 widgets, `step`, max: 120/120 loaded, loaded median 19 ms / p90 65 ms,
hitch 4.6 ms/s both ways, memory peak 116 MB, 236 hosts created and 229
dropped over 3.5 minutes of scrolling — the list does not grow with the chat.

What each switch is worth (step, 40 widgets, `max` minus one):

- `park=false` (prefetched views not in the window): hitch 8.7 / 10.2 ms/s
  instead of 3.7 / 4.1 — WebKit reports the height only once the view is on
  screen, so the layout happens while the bubble is being shown.
- `asyncValidation=false`, `callAsync=false`: within noise on the simulator
  (hitch 0.5–4.6 ms/s either way). Kept in `max` because the main-thread cost
  they remove scales with the ops a widget emits and with the number of
  cores a phone has, both worse on a device than on the simulator.
- `poolRenderers=6,poolEngines=3`: no gain in `step`; the pools are refilled
  between flicks anyway.
- `keepScreens=4`: hitch 2.2 / 1.5 ms/s (fewer re-creations on the way back)
  for 166 MB instead of 103 MB peak. The knob a product tunes by device.
- Without the `rendered` ack (`WidgetDiagnostics.rendered`), the flick run
  had 117 layout jumps and 138 layout invalidations: `loaded` arrives from
  the engine before the renderer has applied the loading ops, so the bubble
  would briefly adopt the empty document's height. With it: 2 and 2.

What is left on the critical path with everything on: the widget's own boot
in QuickJS (the loaded median of 17 ms is mostly `__twDispatch('boot')` +
scripts; Chart.js / D3 widgets reach 60 ms), and on a cold open the web
content process launch (~600 ms on the simulator before the first renderer
is ready — a messenger prewarms at launch and never pays it in a chat).
Still open: the same pools on Android, and a renderer `reset` op so a
`WKWebView` could be recycled instead of created at idle (≈4 ms of main
thread each, see decisions.md).

### iPhone 17 Pro Max (iOS 27.0.1), Release, 40 widgets, `step`

The same build on the phone (`xcrun devicectl device process launch … --autorun
feed …`, the report copied with `devicectl device copy from --domain-type
appDataContainer`). The phone is several times faster than the simulator on
the baseline path, and the optimisations still turn the wait into zero:

| `step`, iPhone 17 Pro Max | baseline | max |
|---|---|---|
| display → content, median / p90 | 88 / 149 ms | ready before display (p90 −132 ms) |
| loaded (host creation → scripts done), median / p90 | 103 / 161 ms | 27 / 60 ms |
| renderer ready, median | 82 ms | 4 ms |
| first screen on a cold open, display → content | 167 ms median, 202 max (4 bubbles) | 93 ms (2 bubbles) |
| hitch rate down / up | 16.3 / 0 ms/s, worst frame 33 ms | 1.5 / 13.8 ms/s, worst 37 ms |
| layout jumps | 34 | 4 |
| app memory peak | 286 MB | 126 MB |

| `flick`, iPhone 17 Pro Max | baseline | max |
|---|---|---|
| widgets that managed to load | 29 / 40 | 40 / 40 |
| display → content, median / p90 | 54 / 140 ms (of those that loaded) | ready before display (p90 −126 ms) |
| hitch rate down / up | 5.1 / 0 ms/s | 3.5 / 0 ms/s |
| layout jumps | 28 | 0 |
| app memory peak | 211 MB | 126 MB |

The way back up in `step` costs `max` 14–15 ms/s on the phone (26–28 dropped
frames of ≤ 37 ms over 28 s, no freezes), and `keepScreens=4` does not change
it (15.2 ms/s, 152 MB peak), so it is not the re-creation of dropped bubbles
but the bursts of cell work when a screen of snapshots and hosts swaps in
after the flick; the flick run shows 0 ms/s on the way up. Left as the next
thing to profile on the device.

120 widgets, `step`, max, on the phone: 120/120 loaded, every bubble ready
before it was displayed (p90 −1208 ms), loaded median 26 ms / p90 62 ms,
hitch 1.5 ms/s down and 8.9 ms/s up (worst frame 38 ms, no freezes), memory
peak 127 MB and 58 MB at the end after 235 hosts created and 227 dropped over
three minutes of scrolling.

## Corpus (2026-10-12)

The setup screen's **Widgets** picker chooses what fills the bubbles: `showcase`
(default on the phone: `corpus/showcase`, the 21 interactive demos in exactly
the web feed's order — each batch shuffled with the web's seeds, one of each
batch in turn, the web's text bubbles before every other one) or `classic`
(compat → generated → Telegram demos, the set every number above was measured
on; still the default from a shell, `--feed-set corpus=showcase` switches). Since
v0.1.1 every widget fills its bubble, as on a client: no bubble padding (the widget's
body margin is the inset), the cell clips the widget at its 16 px corner radius, and
the bubble's colour is the `bg_color` the feed passes.

## The feed bar (2026-10-12)

The feed got the web feed's bar (`docs/chat-feed-web.md`), a SwiftUI strip
over the UIKit list (`FeedBar` / `FeedSwitches` in `ChatFeedView.swift`),
applied live through `ChatFeedViewController`:

| Switch | What it does |
|---|---|
| **WebView / Blitz / litehtml** | `FeedModel.switchRenderer`: every live bubble's host is destroyed and boots again behind the new `Renderer` seam — the visible ones at once, parked ones when scrolled to; heights are remembered, killed bubbles stay stopped. The report keeps the first host's timings; the overlay samples every boot |
| **pool** | `FeedModel.setPools`: hosts created from now on take from the pools (filled now) or boot cold (pools drained); the overlay keeps one column of samples per setting, so one scroll with the pool on and one with it off compare on the same screen |
| corpus, hostile, bubbles | a rebuild (`rebuild()`: every host dropped, metrics cleared, scroll to the top; renderer and pool setting stay). `hostile` puts one `corpus/attacks` file in the third bubble, on the first screen, like the web feed: `fallback-at-boot` (killed by the boot slice, the markup stays), `cpu-raf-burn` (throttled, "CPU deferred ×N" in the caption), `timer-flood`, `api-fetch`, `markup-img-src`, `mem-*`; a killed bubble is never re-run |
| reset, ▶ scroll | reset = the rebuild above; ▶ scroll = the autorun's scripted scroll on demand (writes feed-report.json at the end), a second tap stops it |

The overlay (the status label) now shows, like the web one: corpus, renderer,
live / dropped / stopped counts, the pools, `create → content` and
`shown → content` (median / p90, negative = ready before the reader got there)
per pool setting, pool misses, memory, what the validator and shim blocked and
how often the CPU quota deferred a task. Each bubble's caption starts with the
renderer it is on and whether it came pooled or cold. From a shell the same
switches: `--feed-set renderer=blitz,hostile=cpu-raf-burn,corpus=showcase`.
