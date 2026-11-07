# Android implementation

Native port of the sandbox: the same JavaScript (sandbox shim, renderer,
validator) runs on quickjs-ng and `android.webkit.WebView` instead of a Worker
and an iframe. The architecture is the iOS one (`packages/ios/README.md`), ported glue
for glue; the JS bundles are byte-for-byte what `packages/core/scripts/build-native-bundles.ts`
emits for iOS except for the renderer transport define.

```
android/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml   Gradle 8.14 · AGP 8.13 · Kotlin 2.2 · compileSdk 35 · minSdk 26
  widget-sandbox/                    library module (Kotlin + NDK)
    src/main/cpp/CMakeLists.txt      builds packages/ios/Sources/CQuickJS (quickjs-ng v0.17.0 + qjs_shim.c) unmodified, -O2, no STL
    src/main/cpp/quickjs_jni.c       JNI layer written against qjs_shim.h: strings cross as UTF-8 byte[]
    src/main/java/…/sandbox/
      QuickJSContext.kt              runtime/context wrapper, string host functions, interrupt deadline (CLOCK_BOOTTIME)
      JSThread.kt                    the thread a runtime is pinned to (8 MB stack, Looper/Handler, timers)
      SandboxEngine.kt               = SandboxEngine.swift: one runtime per widget on its own JSThread,
                                       `__host`/`__hostBinary`, timers, scripts, CPU/memory limits, Intl bridge
      TrustedValidator.kt            = validator.ts run in a separate TRUSTED QuickJS context
      RendererWebView.kt             = srcdoc iframe: WebView + shouldInterceptRequest origin + nonce CSP + hardening
      WidgetHost.kt                  = widget-host.ts: engine ↔ validator ↔ renderer, sync queries, public limits
      IntlBridge.kt, SandboxResources.kt
    src/main/assets/WidgetJS/        built by `pnpm build:android` (the three bundles and their bytecode)
    src/androidTest/                 instrumented tests (JUnit4 on the emulator)
  demo/                              Compose demo: corpus browser (compat, attacks, generated), Dark toggle,
                                     diagnostics panel, attack runner against the canary (address editable),
                                     generated / compat runners writing JSON reports into app storage,
                                     Ref toggle (ReferenceWebView.kt: the same file in a plain WebView without
                                     the sandbox — dev tooling, like `?reference=1` on the web; hidden for attacks/)
    src/main/assets/Corpus/          copied by `pnpm build:android` (gitignored)
  apps/renderer-blitz/                    Blitz renderer prototype (dev tooling, frozen since 2026-10-30: in the demo only with
                                     -Ptw.nativeRenderers=true; the same for renderer-litehtml): JNI glue + Kotlin Renderer for the
                                     Rust crate in ../renderer-blitz (repo root, shared with the iOS/macOS demo),
                                     built by cargo-ndk from build.gradle.kts
  apps/renderer-litehtml/                 litehtml renderer prototype (dev tooling): the C++ core in ../renderer-litehtml built by
                                     the NDK's CMake (externalNativeBuild), LitehtmlBackend.kt (fonts, text widths, images,
                                     display-list painter on Canvas) + LitehtmlRenderer.kt; docs/litehtml-renderer.md
                                     The demo's Renderer picker (WebView | Blitz | litehtml) / `--es renderer litehtml`
                                     (`--ez blitz true` still means `--es renderer blitz`)
```

## Build

Toolchain (no Android Studio needed): JDK 17+, Android SDK with
`platforms;android-35`, `build-tools;35.0.0`, `ndk;28.2.13676358`,
`cmake;3.31.6`, and for the runs an arm64 API 35 emulator image. Put
`sdk.dir=…` into `android/local.properties` (gitignored) and export
`JAVA_HOME`; with Homebrew on a Mac:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
corepack pnpm build:android                      # bundles → widget-sandbox/src/main/assets/WidgetJS, corpus → demo/src/main/assets/Corpus
cd packages/android && ./gradlew :widget-sandbox:assembleDebug :demo:assembleDebug
```

Tests need a booted emulator (or a device over USB):

```bash
$ANDROID_HOME/emulator/emulator -avd twidget-35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
cd packages/android && ./gradlew :widget-sandbox:connectedDebugAndroidTest   # results: widget-sandbox/build/reports/androidTests/connected/
```

To look at the demo by eye start the emulator with a window and the host
GPU: `emulator -avd twidget-35 -gpu host`. With `-gpu auto` an API 35 image
falls back to software rendering (swangle + lavapipe) where a frame takes
seconds, so taps land late and Back looks broken; switching the renderer cold
boots the AVD (reinstall the APK).

Demo on the emulator, driven from the command line:

```bash
adb install -r -t apps/android/demo/build/outputs/apk/debug/demo-debug.apk
corepack pnpm canary                                                        # on the Mac; 10.0.2.2 is the Mac from the emulator
adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es autorun attacks --es canary 10.0.2.2:9999
adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es autorun generated
adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es autorun compat
adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es autorun htmlbubbles   # Telegram's demos as classic widgets (corpus/htmlbubbles-ported)
adb shell run-as org.telegram.widgetsandbox.demo cat files/attack-report.json     # also generated-report.json, compat-report.json, htmlbubbles-report.json
```

On a real phone replace `10.0.2.2:9999` with the Mac's LAN address (the field
on the attack screen is editable and remembered) and start the canary bound
to all interfaces: `CANARY_HOST=0.0.0.0 corepack pnpm canary`.

## Verified on the Android 15 emulator (Pixel 7 AVD, arm64)

- Instrumented tests, 27 green: QuickJS/JNI (UTF-8 round trips, host
  functions, exceptions, interrupt deadline, memory limit, promise jobs, stack
  check follows the calling thread, pinned thread recursion depth), engine
  (boot, network stubs throw `NetworkDisabledError`, CPU limit kills, memory
  limit kills, layout query round trip, pre-boot buffering, bridge + timers,
  Intl), trusted validator (diag bridge, URL/handler stripping, stylesheet
  text only shipped sanitized, 25× create/teardown), renderer in a real
  WebView (ready, query answered, network APIs removed, external image refused
  and reported, navigation blocked), WidgetHost end-to-end (validator
  diagnostics reach the host, CSSOM attacks reported as blocked, limits from
  the public API, theme toggle reaches computed styles and form controls,
  25 full widgets created and torn down).
- Attack runner: all 82 `corpus/attacks` files, canary hits **0**, no widget terminated (`attack-report.json`).
- Generated corpus runner: 40/40 (`corpus/generated/{with,without}-profile`)
  load without JS errors or termination; median JS time 64 ms per widget
  including the sandbox-bundle boot (debug build, no bytecode cache); the
  "without" widgets show the same `tag-dropped` / `url-blocked` /
  `network-blocked` diagnostics as on the web and iOS.
- Compat corpus runner: 21/21 load without JS errors or termination, median
  JS time 67 ms (chartjs-line 198 ms, d3-bar 112 ms); no pixel comparison on
  Android (the Ref toggle shows the native render under the sandbox one for a
  comparison by eye, it is not scripted). Checked by eye on the emulator:
  chartjs-line, d3-bar, dark-mode, dark toggle, sandbox vs reference.
- On a real phone (Nothing A142, Android 16, WebView 154, same debug APK over
  USB): the demo renders the compat corpus (chartjs-line 203 ms JS, d3-bar
  137 ms) and the attack runner against the canary on the Mac's LAN address
  (`CANARY_HOST=0.0.0.0 pnpm canary`) reports **0** hits over all
  `corpus/attacks` files; the canary's own log, which also records bare TCP
  connections and UDP datagrams, stayed empty.
  Both corpus runners (`--es autorun generated|compat`) pass on the phone
  as well: generated 40/40 (median JS time 14 ms, max 331 ms), compat 21/21
  (median 9 ms, max 195 ms), no JS errors, no widget terminated, the same
  diagnostics as on the emulator.
- Widget boot (sandbox bundle eval + initial tree) is ~60–90 ms in a debug
  build: quickjs-ng is compiled with `-O2` even in debug, see below.

## Security mapping (invariants → Android)

| Invariant | Mechanism |
|---|---|
| 1 Widget JS only in QuickJS | `SandboxEngine`: one `JSRuntime` per widget on its own `JSThread`; no module loader; only `__host`/`__hostBinary` exist |
| 2 Everything from the context is hostile | engine output goes through `TrustedValidator` (same TS validator, separate runtime) before the renderer |
| 3 Renderer runs only trusted code | `RendererWebView`: document served by our `shouldInterceptRequest` with `script-src 'nonce-…'` (header and `<meta>`); `__twReceive` is the only entry point |
| 4 No external URLs in renderer DOM/CSS | validator URL policy (`data:` / `#fragment` only); every non-document request is answered with an empty 403 and reported as `url-blocked` |
| 5 QuickJS has no network | nothing but `__host` is bound; `fetch`/`XMLHttpRequest`/… are the shim's `NetworkDisabledError` stubs |
| 6 Defense in depth | `setBlockNetworkLoads(true)`, `connect-src 'none'`, every navigation cancelled in `shouldOverrideUrlLoading`, `onCreateWindow` → false, dialogs cancelled, geolocation / permissions / file chooser denied, file/content/DOM-storage/database access off, cookies off, Safe Browsing off, mixed content never, document-start script deleting `fetch`/XHR/WebSocket/EventSource/RTCPeerConnection/WebTransport/workers/`sendBeacon`/`window.open`, opaque per-widget origin `https://w-<uuid>.tgwidget.invalid/` |

Sync layout queries: the engine thread blocks on a `Semaphore` (3 s timeout)
while the main thread validates the query and asks the WebView through
`evaluateJavascript`; the renderer answers through the `tw`
`@JavascriptInterface` object (JavaBridge thread → main). The main thread
never waits for the engine. QuickJS never runs on the main thread.

Memory and CPU limits are part of the public API: `WidgetHost.Options(memoryLimitBytes, cpuLimitMs)`.

SDK v1 (`docs/sdk-v1.md` §4.2): `WidgetHost(context, html, WidgetTheme(colorScheme, params,
button), widthDp, Options(locale, storage = WidgetStorage(local, session), …))`, `setTheme(WidgetTheme)`,
`onAction` (`WidgetAction.Ready` / `OpenLink` / `OpenTelegramLink` / `Haptic`), `onStorageCommit(area,
values)`, `onGesture(WidgetGestureClaim)`; all callbacks on the main thread. `SandboxEngine` checks every bridge
call with `SdkPolicy.kt` (the twin of `sdk-policy.ts`, `SdkPolicyTest` against the shared fixture):
the call itself (`bridge-rejected`), one link per tap under `strict` (`activation-blocked`), the haptic
limit (`haptic-limit`); storage commits and the preload against the 16 KB quota (`storage-rejected`).
`TelegramWidget.setResizeAnchor('top' | 'bottom')` (v0.1.3) is no action: `SdkPolicy.checkBridgeCall`
returns a `BridgeCall` (a `WidgetAction`, or `BridgeCall.SetResizeAnchor(edge)`), the engine hands the
anchor to `SandboxEngine.Delegate.onResizeAnchor`, and `WidgetHost` keeps the latest one in
`WidgetDiagnostics.resizeAnchor` ("top" until the widget calls it) next to `height`, set before the size
report of the content its task changed; the client keeps that edge of the bubble in place when the height
changes (the demo's lists: `WidgetView(scroll = …)`).
The client's time over a widget with no caption below it (`docs/sdk-v1.md` §2.17, §5.12): `Options(contentSafeArea =
WidgetContentSafeArea(bottom, cornerRight, cornerLeft))` at creation and `setContentSafeArea` with the bubble's layout
(dp = CSS px, 0 by default); the renderer core sets the `--tg-content-safe-area-*` variables, the engine passes
`contentSafeArea` at boot and the `safeArea` dispatch (`SandboxEngine.contentSafeArea`).
The widget's state and requests (`docs/sdk-v1.md` §2.13–§2.15, §4.2): `Options(data = WidgetData(chatData, publicData),
context = WidgetContext(isCopy, canQuery, backgroundQuery, chatType, viewer))` (the chat state and the shared state,
which only the bot writes; the server's flags), `setData(WidgetData)` (the full state on every change),
`onRequest(WidgetRequest(data), complete)` (complete once, from any thread, with `Result.success(WidgetRequestAnswer(result))`,
the server's answer, or `Result.failure(WidgetRequestException(WidgetRequestFailure.…))`),
`onCopyText(text)`. `SandboxEngine` runs
the request gate of `SdkPolicy.kt` (`RequestGate`: from the server's flags, one press per request, background only with `backgroundQuery`,
one when the widget appears on screen, then one per 5 s; one in flight; `request-blocked`) and checks the answers
(`answer-rejected`, `data-rejected`).
The string-theme constructor, `setTheme(String)`, `onBridge` and `onGestureClaim(x, y)` remain as deprecated conveniences;
`bridgeEvent` is gone.

Touch gestures: a `WebView` inside a scrolling container loses the touch to
the container as soon as the finger moves past the slop — Android cannot see
`touch-action` inside the page. Since v0.1.3 a vertical drag is always the
chat's: the list scrolls over every widget, `touch-action: none` included, and
the widget gets `pointercancel`. `RendererWebView` denies the parent's
interception from `ACTION_DOWN` until the renderer's `gesture` message (sent
on every primary `pointerdown`, see `docs/protocol.md`) arrives, or for 150 ms;
then only a `hold` claim (`touch-action: none`) keeps the parent out, and only
until the press first leaves the slop: vertically (a tie is vertical) the list
may intercept, horizontally the press stays the widget's to its end. Without a
hold claim, or in the leading 20 px edge strip, the list may intercept at once
(`GestureTest`). The same message carries the whole claim
(`WidgetHost.onGesture`): horizontal drags (`pan-y`, `none`) and holds
(`none`); `y` is always false; taps and double taps over the widget are always
the widget's. The bubble's own recognizers (the message menu on a hold, swipe-to-reply) are the
client's: `GesturePolicy.kt` (the twin of `gesture-policy.ts`,
`GesturePolicyTest` against the shared fixture) gives them a `GestureArbiter`
to feed the press and the claim, and a verdict with `cancel` is the moment to
take the touch from the renderer's view (`ACTION_CANCEL`: the widget gets
`pointercancel`); the demo's `BubbleGestureLayout` does exactly that.

Native pickers: Chromium's colour / date dialogs and `<select>` popups need
an Activity reachable behind the WebView's context — pass an Activity (or a
wrapper over one) to `WidgetHost`, not an application context, or they
silently do nothing.

Theme: the WebView's own `prefers-color-scheme` is whatever the configuration
context said at creation and cannot be flipped later (`WKWebView` has
`overrideUserInterfaceStyle`, `WebView` has nothing). It does not matter: the
validator resolves `prefers-color-scheme` media queries, `color-scheme: light
dark` and `light-dark()` against the host theme and re-emits the stylesheets
on `setTheme`, and `matchMedia` is answered by the host. Verified by
`themeToggleReachesComputedStylesAndFormControls`: after a toggle the root
`color-scheme`, `light-dark()` colours and the UA's own form-control colours
all switch.

## Not done / next

- `Intl` bridge covers number/date/plural/relative/list/compare through
  `android.icu`; `formatToParts` returns empty arrays.
- Snapshot-and-drop of a runtime hidden for minutes (`WidgetHost.setVisible`
  already pauses frames and slows timers to 1 Hz; see `docs/decisions.md`,
  "Runtime limits").
- Device pentest of the WebView blockers beyond the attack corpus (the corpus
  itself and both widget corpora have run on one real phone, see above).
