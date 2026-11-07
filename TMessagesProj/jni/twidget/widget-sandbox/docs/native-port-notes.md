# Native port notes

The prototype runs in a browser, but every piece has a 1:1 counterpart on native
clients. This note lists what maps directly, what changes, and what was purely
web-specific. **The iOS / macOS and Android columns are implemented** in
[`packages/ios/`](../packages/ios/README.md) (Swift package `WidgetSandbox` + demo app; the same
package compiles for AppKit, see [`apps/macos/`](../apps/macos/README.md)) and
[`packages/android/`](../packages/android/README.md) (Gradle library `widget-sandbox` + Compose
demo); the Windows / Linux column remains a plan (the official desktop client
would take the C++ core in `packages/desktop/`, see `docs/decisions.md`).

## Component mapping

| Prototype (web) | iOS / macOS | Android | Desktop (Windows / Linux) |
|---|---|---|---|
| `Worker` + `quickjs-emscripten` | QuickJS-ng (C) on a dedicated `Thread` with a large stack (not a `DispatchQueue`: pool threads hop and have 512 KB stacks — see `docs/decisions.md`) | QuickJS-ng (same vendored C sources, NDK/CMake) through a 300-line JNI layer on a `JSThread` (plain `Thread` with an 8 MB stack + `Looper`; `HandlerThread` cannot set the stack size) | QuickJS-ng on a `std::thread` |
| `packages/core/src/sandbox/*` bundle (shim + linkedom + parse5), evaluated as source | same bundle, **precompiled to QuickJS bytecode** (`JS_WriteObject`), loaded with `JS_ReadObject` + `JS_EvalFunction` | same (source eval for now, like iOS) | same |
| `__host(kind, arg)` C→JS function | `JS_NewCFunction` with the same string API | same | same |
| `Validator` (TypeScript, no DOM dependency) | port to Swift (or compile the TS to JS and run it in a second, trusted QuickJS context) | trusted QuickJS context (`TrustedValidator.kt`, same `validator-bundle.js`) | C++ / trusted QuickJS |
| `<iframe sandbox srcdoc>` + nonce CSP | `WKWebView` loading the renderer document from a custom `WKURLSchemeHandler`; CSP as an HTTP header; `WKContentRuleList` blocking `http(s)`/`ws(s)`; non-persistent `WKWebsiteDataStore`; `decidePolicyFor` cancels every navigation; macOS additionally empties the context menu and refuses `runOpenPanelWith` | `WebView`; `shouldInterceptRequest` serves the document on `https://w-<uuid>.tgwidget.invalid/` and answers everything else with an empty 403; CSP as header and `<meta>`; `setBlockNetworkLoads(true)`; `shouldOverrideUrlLoading` cancels; document-start hardening script; windows/dialogs/permissions/storage off | WebView2 in its own environment with `--host-resolver-rules="MAP * ~NOTFOUND"`, `--proxy-server=…dead…`, `--webrtc-ip-handling-policy=disable_non_proxied_udp`; `NavigationStarting` cancels |
| `MessageChannel` port host ⇄ renderer | `WKScriptMessageHandler` (renderer → host, `window.webkit.messageHandlers.tg.postMessage`) and `evaluateJavaScript` / `callAsyncJavaScript` (host → renderer); or `WKWebView` `MessagePort` through `postMessage` | `@JavascriptInterface` object `tw` (renderer → host) + `evaluateJavascript("__twReceive(…)")` (host → renderer); binary results as base64 | `PostWebMessageAsJson` / `WebMessageReceived` |
| `SharedArrayBuffer` + `Atomics.wait/notify` sync query bridge | `DispatchSemaphore` (QuickJS thread waits; main thread evaluates the query in the WebView — `evaluateJavaScript` completion handler signals) | `Semaphore` (engine thread waits ≤ 3 s; main thread validates and calls `evaluateJavascript`; the bridge callback releases) | `std::binary_semaphore` + `ExecuteScript` callback |
| Dev server COOP/COEP | not needed | not needed | not needed |
| `setInterruptHandler` deadline | `JS_SetInterruptHandler` with `mach_absolute_time` | same with `CLOCK_BOOTTIME` (= `SystemClock.elapsedRealtimeNanos`) | same |
| `setMemoryLimit` + a `WebAssembly.Memory` with `maximum = 16 MB + limit` (the emscripten build cannot count its heap, `decisions.md`) | `JS_SetMemoryLimit` counts via `malloc_size`, nothing else needed | same via bionic `malloc_usable_size` | same (`_msize` / glibc) |
| `performance.now()` via host | monotonic clock | same | same |
| `Intl` delegated to host | `NumberFormatter`, `DateFormatter`, `String.localizedCompare` | `android.icu.text.*` (NumberFormat, DateFormat skeletons, PluralRules, RelativeDateTimeFormatter, ListFormatter, Collator) | ICU |

## Things that change

### Sync queries on native

The sandbox thread must block while the UI thread asks the WebView. On the web
the worker blocks in `Atomics.wait`; natively the QuickJS thread waits on a
semaphore. Two consequences, same as on the web:

1. **Never run QuickJS on the UI thread** — a blocked UI thread would deadlock
   on its own WebView.
2. The WebView must answer **inside one run-loop turn**. `evaluateJavaScript`
   returns asynchronously but reasonably fast (~0.1–0.5 ms on iOS for a
   `getBoundingClientRect`). The prototype's query cache (valid until the next
   flush) is what keeps D3-style layout loops bearable; keep it.

The timeout (3 s on the web) becomes the safety net for a frozen WebView.

### Swift renderer task boundaries

`TaskRenderer` is an optional Swift renderer interface. The host delivers
`beginTask()` and `endTask()` around a sandbox task through the same ordered
validation/executor pipeline as its operations and queries. A native renderer
can lay out intermediate changes to answer a synchronous query while delaying
presentation and height notifications until the task ends. Query answers must
not wait for `endTask()`: the script is blocked on that answer.

This is a renderer presentation optimization, not a widget profile feature or
a new SDK capability. A renderer without this interface still receives the same
validated operations and query requests. The interface does not change script
execution, validation, query answers, CPU accounting, event policy or the shared
wire protocol, so Kotlin, C++ and the web do not need a matching presentation
callback. A change to any of those shared behaviors would require the parity
work in `CONTRIBUTING.md`; a platform-only callback is not permission to change
the contract.

The engine closes a started task after a script exception or CPU-limit stop.
When the renderer is not ready, the host buffers task boundaries along with the
operations and replays them in order. Destroying the host discards later
renderer delivery and releases a blocked query; renderer destruction must
release any held presentation state without waiting for another callback.

`TaskRendererHostTests` exercises the real Swift host, QuickJS engine and
validator with synchronous/asynchronous validation, JSON/typed binary output,
buffered startup, exceptions, CPU stops and destruction during a query. It also
compares a plain renderer with a task-aware renderer for identical validated
operations, query requests and script-observed answers. Native pixel and
presentation tests remain in the renderer repository; these host tests alone
do not establish visual parity across clients.

### Renderer is the same JavaScript

`packages/core/src/renderer/renderer.ts` is a plain script with no bundler-specific code. It
only needs `postMessage`/`onmessage` replaced by the platform bridge; the
`RenderOp` applier, event serializer and query answerer are unchanged. The
renderer document is tiny (`srcdoc.ts`), so serving it from a custom scheme is
trivial, and the nonce CSP can be sent as a response header instead of a
`<meta>`.

### Validator placement

The validator must run in trusted code **outside** the widget's QuickJS context
and **before** the WebView. Two viable placements:

- a native port (Swift/Kotlin/C++) — most auditable, ~900 lines + css-tree
  replacement (a real CSS parser is needed; `swift-css-parser`, `ph-css`, or
  running css-tree in a *trusted* QuickJS context);
- the same TypeScript compiled to JS and run in a second, trusted QuickJS
  context (one per app, not per widget). This reuses `css-tree` unchanged and
  keeps a single implementation across platforms; the cost is one extra
  JSON round trip per batch.

### Bytecode (done, Oct 2026)

`packages/core/scripts/build-native-bundles.ts` compiles the sandbox bundle, the validator
bundle and the bundled libraries to quickjs-ng bytecode next to each `.js`
(`sandbox-bundle.qbc`, `validator-bundle.qbc`, `libs/*.qbc`) with
`packages/tools/qjsc/qjsc.c`, a 60-line tool built on the fly from the vendored
`packages/ios/Sources/CQuickJS` — the same sources every port links, so the bytecode
format (quickjs-ng `BC_VERSION`) always matches the engine. The blob is a
`TWQB` container (format version, source length, FNV-1a 64 of the source) in
front of `JS_WriteObject(JS_WRITE_OBJ_BYTECODE | JS_WRITE_OBJ_STRIP_SOURCE)`
output; `qjs_eval_bytecode` in `qjs_shim.c` checks the header against the
source the host would otherwise evaluate and quickjs-ng checks its own version
byte, so a stale `.qbc` next to a newer `.js` is refused with an exception and
the glue falls back to evaluating the source (diag `bytecode`). Every port
(`SandboxEngine.swift` / `.kt` / `.cpp`, the three `TrustedValidator`s) tries
the blob first and reports `(bytecode)` or `(source)` in the `boot` and
`library` diags; the renderer bundle runs in a web view and has no bytecode.
Only the host's own bundles ever go through `JS_ReadObject` (it has no
verifier); widget code is always parsed from source. Debug info is kept (shim
stack traces keep line numbers), source text is not, so the blobs are 1.2–1.5×
the minified source. Measured on the desktop core (release quickjs-ng, macOS
arm64): sandbox bundle boot 38–74 ms → 20–22 ms, Chart.js 18 ms → 4 ms; the
remainder is the bundle's own top-level execution, which bytecode cannot remove
(QuickJS has no heap snapshots).

### Version stamp (v0.6.0)

`build-native-bundles.ts` writes `VERSION.json` on the first line of each of
the three bundles (`/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1 */`)
and into the glue as constants (`WidgetSandboxVersion.swift` / `.kt`,
`twidget/version.h`). Before it evaluates a bundle (the `.qbc` or the source)
the engine checks the stamps of the whole set (`BundleStamp.swift` / `.kt`,
`bundle_stamp.cpp`, one parser in three copies with the format's tests in
`version.test.ts`): no stamp, or another protocol, profile or sdk, and it dies
at boot with `bundle-mismatch`; the validator loads nothing. A port reads the
stamp from the source it already has for the `.qbc` check, so the check costs
one line's parse.

### Lifecycle

One QuickJS runtime per visible widget; when a message scrolls out of view,
stop timers, drop the runtime and keep the WebView (or a bitmap snapshot) as a
static view; recreate on return. Memory: a QuickJS runtime with linkedom and a
chart library costs ~5–15 MB; the WebView dominates.

## Purely web-specific parts (can be dropped)

- `COOP/COEP` headers and `crossOriginIsolated` (only needed for SAB).
- `Worker` + `postMessage` glue in `packages/core/src/host/worker.ts` (becomes the thread +
  C bindings).
- `<iframe sandbox>` / `srcdoc` / `MessageChannel` / `window.parent` handshake.
- Synchronous `XMLHttpRequest` to fetch bundled libraries (becomes a file read).
- Vite dev server, `/reference/` mode, Playwright harness.

## Open questions for native

- **App Store review**: executing downloaded JavaScript in a bundled
  interpreter (not JavaScriptCore) — Figma plugins do it; check guideline 2.5.2
  wording for the chosen framing ("the code cannot change app features").
- **`WKContentRuleList` and WebSocket**: rule lists cover `ws(s)` schemes in
  recent iOS versions; keep `connect-src 'none'` as the second barrier.
- **Android WebView versions** vary in CSP support; the renderer should also
  install a document-start script that deletes `fetch`, `XMLHttpRequest`,
  `WebSocket`, `RTCPeerConnection` from the renderer window (defense in depth;
  only our code runs there anyway).
- **IME and caret**: native WebViews behave like Chrome for the "assign
  `value` only if different" rule used by the renderer; verify on Android
  Gboard composition.

## Android port: start here (lessons from the iOS port, Oct 2026)

Everything below was learned while shipping `ios/` and was the plan for
`android/`. The port followed it; what actually differed is in the "Android
implementation" section of `docs/decisions.md` and in `packages/android/README.md`.

### Reuse, do not re-implement

- **Three JS bundles are the product**: `sandbox-bundle.js` (shim + linkedom,
  untrusted once loaded), `validator-bundle.js` (trusted, runs in its *own*
  QuickJS runtime, one per app or per widget), `renderer-bundle.js` (trusted,
  runs in the WebView). `packages/core/scripts/build-native-bundles.ts` emits them
  with their bytecode (no libraries are bundled since profile 1). Add an output
  directory parameter or an `android/` target to that script instead of a new
  one; keep `pnpm build:ios` working.
- **Renderer transport** is a build-time define `__TW_TRANSPORT__` in
  `packages/core/scripts/sandbox-build.ts` (`'port'` for the web, `'webkit'` for iOS). The
  `webkit` branch at the end of `packages/core/src/renderer/renderer.ts` is ~20 lines:
  host → renderer by `window.__twReceive(json)` (`evaluateJavaScript`), renderer
  → host by `webkit.messageHandlers.tw.postMessage(json)`, binary query results
  as `binaryBase64`. Android needs a third value (`'android'`): renderer → host via
  an `@JavascriptInterface` object (`window.tw.postMessage(json)`), host →
  renderer via `WebView.evaluateJavascript("__twReceive(...)")`. The rest of the
  renderer is unchanged.
- **Protocol** (`docs/protocol.md`, `packages/core/src/shared/protocol.ts`) and the `__host`
  string API are identical on every platform. Host kinds the engine must answer:
  `ops`, `query` (sync), `now`, `timer`, `clearTimer`, `raf`, `log`, `diag`,
  `listen`, `pd`, `bridge`, `intl`, `random`, `stat`. Inputs to the sandbox:
  `event`, `frame`, `viewport`, `resize`, `theme`, `bridge`. Copy the Swift
  `SandboxEngine.swift` structure 1:1; it is the shortest correct reference.
- **Intl** lives in the host: kinds `number`, `numberParts`, `numberOptions`,
  `date`, `dateMethod`, `dateParts`, `dateOptions`, `plural`, `relative`, `list`,
  `compare`, `upper`, `lower`. iOS maps them to Foundation and returns `[]` for
  the `*Parts` kinds; `java.text` / `android.icu.text` can do better.
- **Tests to port** (`packages/ios/Tests/WidgetSandboxTests/`): engine boot, network
  stubs throw `NetworkDisabledError`, CPU limit kills, memory limit kills,
  layout query round trip, pre-boot buffering, bridge + timers, validator
  diagnostics reach the host, CSSOM attacks (`api-css-adopted`,
  `api-css-insertrule`) reported as `url-blocked`/`css-dropped`, create/teardown
  churn (25 runtimes). Demo app features worth copying: corpus browser
  (compat/attacks/generated), attack runner against `pnpm canary` (0 hits is the
  bar), generated runner writing a JSON report to app storage.

### QuickJS on a JVM process

- quickjs-ng v0.17.0 is vendored under `packages/ios/Sources/CQuickJS/` with a 40-line
  `qjs_shim.c` that hides `JSValue` macros; reuse both via the NDK (CMake) and
  write the JNI layer against the shim, not against `quickjs.h` directly.
- **One dedicated thread per runtime with a big stack.** On iOS a GCD queue
  produced bogus `RangeError: Maximum call stack size exceeded` because QuickJS
  records the stack top at `JS_NewRuntime` and pool threads hop. On Android use
  `Thread(null, runnable, "widget.sandbox", 8L shl 20)` + `Looper.prepare()` /
  `Handler` (a `HandlerThread` has no stack-size constructor), never a shared
  executor. Set `JS_SetMaxStackSize` to ~6 MB and call `JS_UpdateStackTop`
  on every entry (eval, call, pending jobs). Debug builds of quickjs-ng use
  ~10 KB of C stack per JS call; css-tree inside the validator needs > 40
  levels.
- CPU limit: `JS_SetInterruptHandler` with a deadline from
  `SystemClock.elapsedRealtime()`; 500 ms per continuous run, 5 s for boot.
  Memory: `JS_SetMemoryLimit`, 8 MB (64 MB until the strict limits). Both are `SandboxConfig` fields, keep them
  configurable from the public host API (the iOS `WidgetHost.init` does not yet
  expose them, only `SandboxEngine` does).
- Buffer inputs that arrive before the runtime has booted (the renderer sends
  `size` before `ready`).

### WebView renderer on Android

- Serve the renderer document from a custom path with `shouldInterceptRequest`
  (per-widget origin, e.g. `https://w-<uuid>.tgwidget.invalid/`); refuse every
  other request there with an empty response, so no subresource ever leaves.
  `WebViewAssetLoader` is a convenience, not a security boundary.
- Belt and braces, like iOS: CSP as both response header and `<meta>` with
  `connect-src 'none'`, nonce `script-src`; `WebSettings.setBlockNetworkLoads(true)`
  (requires the `INTERNET` permission to be *present* when toggled);
  `shouldOverrideUrlLoading` returns `true` for every navigation;
  `setJavaScriptCanOpenWindowsAutomatically(false)`, `setSupportMultipleWindows(false)`;
  a document-start script (`WebViewCompat.addDocumentStartJavaScript`) deleting
  `fetch`/`XMLHttpRequest`/`WebSocket`/`RTCPeerConnection`/`EventSource`/
  `navigator.sendBeacon`; disable geolocation, file access, content access,
  DOM storage; `CookieManager.setAcceptThirdPartyCookies(false)`.
- Register the `@JavascriptInterface` object and the client callbacks *before*
  `loadUrl` (iOS lesson: handlers added after view creation were ignored).
- Sync layout queries: the engine thread blocks on a `Semaphore` (3 s timeout)
  while the main thread runs `evaluateJavascript` and the callback signals.
  Never run QuickJS on the main thread.
- Attack suite on a device needs the canary at the Mac's LAN address (the iOS
  demo hardcodes `127.0.0.1`, which only works on the simulator).
