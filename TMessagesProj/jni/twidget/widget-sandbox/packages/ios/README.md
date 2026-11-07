# iOS implementation

Native port of the sandbox: the same JavaScript (sandbox shim, renderer,
validator) runs on quickjs-ng and WKWebView instead of a Worker and an iframe.
The package also compiles for macOS (AppKit); the macOS demo and the platform
differences are described in [`apps/macos/README.md`](../../apps/macos/README.md).

```
ios/
  Package.swift                      Swift package `WidgetSandbox`
  Sources/CQuickJS/                  quickjs-ng v0.17.0 (vendored, MIT) + qjs_shim.c for Swift
  Sources/WidgetSandbox/
    QuickJS.swift                    runtime/context wrapper, string host functions, interrupt deadline
    JSThread.swift                   the thread a runtime is pinned to (8 MB stack, run loop, timers)
    SandboxEngine.swift              = packages/core/src/host/worker.ts: one runtime per widget on its own JSThread,
                                       `__host`/`__hostBinary`, timers, scripts, CPU/memory limits, Intl bridge
    TrustedValidator.swift           = packages/core/src/host/validator.ts, run in a separate TRUSTED QuickJS context
    Renderer.swift                   the renderer as WidgetHost sees it (view + JSON contract); RendererFactory
    RendererWebView.swift            = srcdoc iframe: WKWebView + custom scheme + nonce CSP + rule list
    WidgetHost.swift                 = packages/core/src/host/widget-host.ts: engine ↔ validator ↔ renderer, sync queries
    WidgetView.swift                 SwiftUI wrapper + observable model
    Platform.swift                   PlatformView = UIView | NSView; the UIKit / AppKit seam
    RendererEnvironment.swift        one WebKit environment for many renderers (process pool, data store, content
                                       controller, rule list, scheme handler) + RendererPool (documents loaded ahead)
    Pools.swift                      EnginePool (runtime + sandbox bundle evaluated ahead), ValidatorPool (contexts on the
                                       shared validator thread, recycled), WidgetWarmup — see docs/chat-feed-ios.md
    WidgetJS/                        built by `pnpm build:ios` (bundles, libs, libraries.json, and
                                       .qbc bytecode next to each file QuickJS evaluates — see
                                       docs/native-port-notes.md "Bytecode")
  Tests/WidgetSandboxTests/          XCTest: engine boot, network stubs, CPU limit, layout query, bridge, validator
  DemoApp/                           SwiftUI demo (xcodegen project, sources shared with apps/macos/DemoApp): corpus
                                       browser (compat, attacks, generated), dark toggle, diagnostics, Reference
                                       toggle (native WebKit twin, dev tooling), Renderer picker WebView | Blitz | litehtml (see below), runners for attacks / generated /
                                       compat / Telegram demos (htmlbubbles-ported) / sandbox-vs-reference timing (JSON reports, `--autorun` launch argument)
    Sources/NativeRenderer.swift     the Renderer contract on a native engine's C ABI: raster view, touches, frames, diagnostics (dev tooling)
    Sources/BlitzRenderer.swift      the Blitz engine's function table (apps/renderer-blitz/, Rust)
    Sources/LitehtmlRenderer.swift   the litehtml engine's function table (apps/renderer-litehtml/, C++; docs/litehtml-renderer.md)
    Sources/ChatFeed/                the chat feed (iOS only): UICollectionView of bubbles with a widget in each, every
                                       optimisation behind a FeedConfig switch, autorun with hitch / timing report
                                       (`--autorun feed`, docs/chat-feed-ios.md)
    BridgingHeader.h                 their C headers (apps/renderer-blitz/include/twidget_blitz.h, apps/renderer-litehtml/include/twidget_litehtml.h)
```

## Build

```bash
pnpm build:ios                       # bundles → packages/ios/Sources/WidgetSandbox/WidgetJS, corpus → apps/ios/DemoApp/Corpus
cd apps/ios/DemoApp && xcodegen generate  # regenerates TwidgetDemo.xcodeproj (brew install xcodegen)
open apps/ios/DemoApp/TwidgetDemo.xcodeproj
```

Package only (no simulator runtime needed; the WKWebView tests run natively on macOS too):

```bash
xcodebuild test -scheme WidgetSandbox -destination 'platform=macOS'
```

The Blitz and litehtml renderer prototypes (next section) are frozen since 2026-10-30 (`docs/decisions.md`, "The renderer prototypes are frozen"):
the demo builds and links them only with `TW_NATIVE_RENDERERS=YES` on the `xcodebuild` command line. Such a build
compiles the Rust crate, which needs rustup's `cargo` (`brew install rustup`, then `rustup default stable`; the iOS
targets are added on first use), and the C++ core, which needs `cmake` and `ninja` from Homebrew.

Cross-compile check for the simulator:

```bash
cd packages/ios && swift build --build-tests --triple arm64-apple-ios16.0-simulator --sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)"
```

On a real iPhone (free Apple ID is enough; the app is only installed, not
distributed):

1. On the phone: Settings → Privacy & Security → Developer Mode → on (reboots).
   Connect it by cable and tap "Trust this computer".
2. `open apps/ios/DemoApp/TwidgetDemo.xcodeproj`, select the TwidgetDemo target →
   Signing & Capabilities → tick "Automatically manage signing" and pick your
   Team (add your Apple ID under Xcode → Settings → Accounts if the list is
   empty). If the bundle id is taken, change its last part.
3. Choose your iPhone as the run destination and press Run. The first launch
   asks you to trust the developer on the phone: Settings → General → VPN &
   Device Management → your Apple ID → Trust.
4. The attack runner expects the canary on the Mac; on a phone replace
   `127.0.0.1` in `AttackRunnerView.swift` with the Mac's LAN address and run
   `pnpm canary` there. Free-account builds expire after 7 days.

Verified on an iPhone 17 Pro Max (iOS 27.0.1) with Xcode 26.6: the first
connection extracts ~13 GB of device support files from the phone's dyld
shared cache into `~/Library/Developer/Xcode/iOS DeviceSupport/`; with a full
disk Xcode shows `dyld_shared_cache_extract_dylibs failed` next to the device
and cannot build for it. Free the space and reconnect. CLI install without
Xcode's Run button:

```bash
cd apps/ios/DemoApp && xcodebuild build -project TwidgetDemo.xcodeproj -scheme TwidgetDemo -destination 'id=<device udid>' -allowProvisioningUpdates -derivedDataPath /tmp/twidget-device
xcrun devicectl list devices                     # udid column
xcrun devicectl device install app --device <udid> /tmp/twidget-device/Build/Products/Debug-iphoneos/TwidgetDemo.app
xcrun devicectl device process launch --device <udid> org.telegram.widgetsandbox.TwidgetDemo
```

Or without opening Xcode, once the team is known:

```bash
cd apps/ios/DemoApp && TWIDGET_TEAM=ABCDE12345 xcodegen generate
xcodebuild -project TwidgetDemo.xcodeproj -scheme TwidgetDemo -destination 'generic/platform=iOS' -allowProvisioningUpdates build
```

Tests and the demo need a simulator runtime matching Xcode (`xcodebuild -downloadPlatform iOS`), then:

```bash
xcodebuild test -scheme WidgetSandbox -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'
```

Note for the host-side code: register the `tgwidget://` scheme handler and the
`tw` script message handler on the `WKWebViewConfiguration` *before* creating
the `WKWebView` (it copies the configuration); and expect renderer messages
(`size`) before `ready` — `SandboxEngine` buffers anything that arrives before
its runtime has booted. Both QuickJS runtimes are pinned to a `JSThread`
(8 MB stack, run loop) — never run a context on a GCD queue: pool threads have
512 KB stacks and a debug build of quickjs-ng needs ~10 KB per JS call.

## Verified on the iPhone 17 simulator (iOS 26.5) and on a real iPhone

- Device: the demo installs, launches and renders the compat and generated
  corpora on an iPhone 17 Pro Max running iOS 27.0.1 (attack runner not run
  there yet: it needs the canary's LAN address).

- XCTest: engine (boot, network stubs, CPU limit, layout query, pre-boot
  buffering, bridge/timers), QuickJS thread-hopping stack check, trusted
  validator (incl. create/teardown churn), renderer in a real WKWebView, and
  WidgetHost end-to-end (validator diagnostics reach the host; CSSOM attacks
  reported as blocked) — all green.
- Demo: chartjs-line, d3-bar (click → highlight), drag, forms, Dark toggle.
- Attack runner: all 82 `corpus/attacks` files, canary hits **0**.
- Generated corpus runner: 40/40 (`corpus/generated/{with,without}-profile`)
  load without errors; median JS time 171 ms per widget in a debug build.

## Chat feed (iOS): a widget in every bubble

`Chat feed` in the corpus list opens a UIKit list of bubbles (text + widget,
cycling through compat, generated and the Telegram demos) with every
optimisation of `docs/chat-feed-ios.md` behind a switch: shared WebKit
environment, renderer/engine/validator pools, parallel boot, validation off
the main thread, `callAsyncJavaScript` transport, remembered heights,
prefetch, parked prefetched views, snapshot-and-drop of far-away runtimes,
prewarm at launch. `baseline` is the host as the single-widget screens use
it; `max` is everything on. The autorun scrolls like a reader (`step`) or a
flick, then writes `feed-report.json` (per-widget clocks, hitch rate per
phase, memory, pool hits, main-thread work) to the app's Documents:

```bash
xcrun simctl launch booted org.telegram.widgetsandbox.TwidgetDemo --autorun feed --feed max --feed-count 40 --feed-scroll step
xcrun simctl launch booted org.telegram.widgetsandbox.TwidgetDemo --autorun feed --feed baseline --feed-count 40 --feed-set heightCache=true
```

Build with `-configuration Release` for numbers. The package side of this
(`RendererEnvironment`, `Pools.swift`, the new `WidgetHost.Options` flags,
`WidgetHost.snapshot`, `WidgetDiagnostics.rendered`) is covered by
`PoolsTests`; the flags default to the previous behaviour.

## Security mapping (invariants → iOS)

| Invariant | Mechanism |
|---|---|
| 1 Widget JS only in QuickJS | `SandboxEngine`: one `JSRuntime` per widget on its own queue; no module loader; only `__host`/`__hostBinary` exist |
| 2 Everything from the context is hostile | engine output goes through `TrustedValidator` (same TS validator, separate runtime) before the renderer |
| 3 Renderer runs only trusted code | `RendererWebView`: document served by our `WKURLSchemeHandler` with `script-src 'nonce-…'`; `__twReceive` is the only entry point |
| 4 No external URLs in renderer DOM/CSS | validator URL policy (`data:` / `#fragment` only); scheme handler refuses every subresource |
| 5 QuickJS has no network | nothing but `__host` is bound; `fetch`/`XMLHttpRequest`/… are the shim's `NetworkDisabledError` stubs |
| 6 Defense in depth | `WKContentRuleList` blocking `http(s)://`/`ws(s)://`, `connect-src 'none'`, non-persistent data store, every navigation cancelled in `decidePolicyFor`, `window.open` → nil, document-start script deleting network APIs, `allowsInlineMediaPlayback`, no `allow-same-origin` equivalent (opaque custom-scheme origin per widget) |

Sync layout queries: the engine queue blocks on a `DispatchSemaphore` (3 s
timeout) while the main thread validates the query and asks the WKWebView
through `evaluateJavaScript`; the renderer answers via the `tw` script message
handler. The main thread never waits for the engine.

## Not done / next

- `Intl` bridge covers number/date/plural/relative/list/compare; `formatToParts`
  returns empty arrays.
- Snapshot-and-drop of a runtime hidden for minutes (`WidgetHost.setVisible`
  already pauses frames and slows timers to 1 Hz; see `docs/decisions.md`,
  "Runtime limits").
- Pentest of `WKContentRuleList` + WebSocket and of the scheme handler on real devices.

## Native renderer prototypes (dev tooling, never a product path; frozen)

Frozen since 2026-10-30: no client draws with them, so the demo has them only in a `TW_NATIVE_RENDERERS=YES` build
(the Swift condition of the same name; without it the picker is hidden). The **Renderer** picker on a widget screen (WebView | Blitz | litehtml) swaps
the renderer; the sandbox and the validator stay the same. Both native engines
sit behind one Swift glue, `NativeRenderer.swift` (raster view, touches,
coalesced paints, diagnostics), parameterised by a table of C functions: the
two ABIs are the same shape (`twb_*` / `twl_*`). litehtml is described in
`docs/litehtml-renderer.md` (C++ core in `apps/renderer-litehtml/`, built by
`apps/renderer-litehtml/build-apple.sh` from the pre-build phase like the Rust
crate; `litehtml-init` / `litehtml-build` / `litehtml-paint` / `litehtml-touch`
panel lines). With Blitz, instead of the
WKWebView running the renderer bundle, the validated DOM goes to
[`apps/renderer-blitz/`](../../apps/renderer-blitz) (Rust: blitz-dom + blitz-paint +
vello_cpu, the engine measured in `docs/blitz-experiment.md`), which lays it
out and paints it on the CPU into a CGImage shown by a plain view. The sandbox
and the validator are untouched — the Rust side only ever sees validated
`RenderOp`s and `data:` images, has no script and no network — so this is a
renderer comparison, not a second trust model. The same crate is the Android
demo's `renderer-blitz` module; `Renderer.swift` (= `Renderer.kt`) is the seam
`WidgetHost` plugs it into (`WidgetModel(…, rendererFactory: BlitzRenderer.factory)`).

- `apps/ios/DemoApp/project.yml` (and the macOS twin) run `apps/renderer-blitz/build-apple.sh`
  as a pre-build script phase: `cargo build --release` for the platform and
  architectures of the current build (`PLATFORM_NAME`, `ARCHS`), lipo into
  `apps/renderer-blitz/target/apple/<platform>/libtwidget_blitz.a`, linked with
  `-ltwidget_blitz -framework CoreText -liconv`. First build ≈ 1–2 min per
  platform; after that cargo is incremental. The crate's `target/` is gitignored.
- `--renderer webview|blitz|litehtml` (shorthands `--blitz`, `--litehtml`,
  `--webview` = `--no-blitz`) picks the renderer before anything loads (it is
  persisted as `demo.renderer`), so the runners can be driven on any of them:
  `--autorun generated --blitz`.
- What the prototype shows today (iPhone 17 Pro Max and the simulator, 3×):
  paint 2–3 ms for a 360 × 360 pt card, touches reach the widget's handlers
  (events go back through the same JSON contract), `size` reports drive the
  bubble height, the Dark toggle flips `prefers-color-scheme`. Wall time to
  `loaded` on the phone is ~20 ms better than the WebView renderer (median
  107 vs 128 ms over compat + generated), because `loaded` is mostly QuickJS
  boot; the renderer only decides when `ready` arrives.
- Real fingers jitter: Blitz turns any move beyond 2 px into a document pan
  and drops the click, so the Rust core keeps an 8 px tap slop before it
  forwards moves (`TAP_SLOP` in `renderer.rs`; the Android glue gets it too).
- Diagnostics panel lines: `blitz-init` (creating the document; the first in
  the process scans the system fonts, ≈ 0.4–0.5 s on a phone, once),
  `blitz-paint` (ms and px of the last raster, reported at most once a
  second), `blitz-touch` (per finger lift: moves, distance, and what the
  document answered, e.g. `event:click`). The status line's `renderer ready
  at` is the renderer's share of `loaded in`. Known gaps are Blitz's own
  (`docs/blitz-experiment.md`): no canvas (the element stays empty), no 3D
  transforms, CSS does not style inline SVG, form controls are partial.
- Emoji are blank on iOS (fine on macOS): the iOS `Apple Color Emoji` font
  stores its `sbix` bitmaps in Apple's proprietary `emjc` format, which
  skrifa/glifo cannot decode (macOS ships PNG). The `blitz-fonts` panel line
  confirms the family is found; `renderer-blitz`'s ignored `font_probe` /
  `sbix_probe` tests show the graphic type per strike. The way out is a
  bundled open colour-emoji font (COLR, e.g. Twemoji) registered as the
  generic `emoji` family on iOS, or an `emjc` decoder.
