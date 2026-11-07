# litehtml as a swappable renderer: the third column in the WebView / Blitz / litehtml comparison

> **Frozen since 2026-10-30** (`docs/decisions.md`, "The renderer prototypes are frozen"): no client draws with this
> prototype, nothing builds or runs it by default; what follows is the record of the comparison.

Date: 2026-10-10. A continuation of `docs/blitz-experiment.md`: there litehtml
was run over static DOM dumps by a separate binary, here it is embedded in
the iOS, macOS and Android demos, in the C++ core `packages/desktop/` and in the web demo as the **third
renderer behind the same `Renderer` seam** as Blitz. The sandbox (QuickJS), the validator and the `docs/protocol.md` protocol are shared
by all three; only what paints the validated DOM changes. This is
a comparison tool, not a product path: the **Renderer** switch
(WebView | Blitz | litehtml) on the widget screen, `--renderer litehtml` /
`--litehtml` when launching the iOS/macOS demo, `--es renderer litehtml` on Android.

## What it is

`apps/renderer-litehtml/` at the repository root is a C++20 core (one for all platforms,
like the Rust crate `apps/renderer-blitz/`) plus two bindings:

| Layer | Where | What it does |
|---|---|---|
| litehtml | `third_party/litehtml` (master `4a651c52` from 2026-10-04, unmodified, with its own gumbo) | HTML/CSS parsing, layout, the paint traversal through `document_container` |
| core | `packages/core/src/renderer.cpp` | the `docs/protocol.md` protocol: `ops` → shadow tree → HTML string → `document::createFromString` → `render(width)`; queries (`rect`, `box`, `cs`, `elementFromPoint`, `measureText`, `matchMedia`, `viewport`), touches → hit test → `pointer*`/`mouse*`/`touch*`/`click` with `targetId`, `size`, `frozen`, `gesture`. The litehtml container writes not pixels but a **display list** (`display_list.h`: rectangles with radii, text, images, gradients, clips) |
| font and image backend | `src/backend.h` | what litehtml lacks: font creation with metrics, text width, decoding of `data:` images |
| Apple | `src/apple/coregraphics.cpp` (CoreText + CoreGraphics + ImageIO, pure C++), `src/apple/ffi.cpp` + `include/twidget_litehtml.h` | the backend and display-list execution into an RGBA buffer; the C ABI mirrors `twidget_blitz.h` with the `twl_` prefix, so there is one Swift binding for both engines — `apps/ios/DemoApp/Sources/NativeRenderer.swift` with the `NativeEngine` function table (`BlitzRenderer.swift`, `LitehtmlRenderer.swift` are tables only) |
| Android | `src/android/jni.cpp`, the `apps/android/renderer-litehtml` module | the backend through JNI callbacks into `LitehtmlBackend.kt` (`Paint`/`Typeface`/`BitmapFactory` — the NDK has no text API), the display list goes to Kotlin as JSON and is executed on a `Canvas` in `onDraw` (`LitehtmlRenderer.kt`) |
| desktop (C++ core) | `packages/desktop/renderer/…/litehtml_renderer.{h,cpp}`, `-DTW_LITEHTML=ON` | `twidget::LitehtmlRenderer` — the same owner contract as `BlitzRenderer` (rasters out through `paint()`/`onPaint`, pointers in through `touch()`, one mutex around the core); the same Apple backend, hence macOS only for now — on tdesktop the display list would be drawn by QPainter |
| web | `src/web/wasm.cpp` → Emscripten → `pkg-web/` (`pnpm build:litehtml-web`), `apps/web/src/renderers/litehtml-renderer.ts` | the same C ABI as wasm exports; the backend is a JS object in `Module.twlBackends` (a 2D context's `measureText` for fonts and widths, `Image` for `data:` images — they decode asynchronously and arrive through `twl_image_ready`), the display list is drawn on a `<canvas>`; the **litehtml** toggle / `?litehtml=1` in the dev UI, `TW_RENDERER=litehtml pnpm test:generated` |

Why a shadow tree and re-parsing: litehtml has no DOM mutation API —
the document is created from a string as a whole. So every batch of `ops` serialises
the tree to HTML (elements get `data-twid="<id>"`, which the queries
and the hit test work on) and assembles the document anew, in two passes over the viewport height,
like the iframe in the web demo (`100vh` must not inflate the card). This is the
"`render()` → string" model, so the comparison is fair: the cost of rebuilding on every
update is part of the answer, not an artefact of the glue.
The validator already rewrites `prefers-color-scheme` into `(min-width: 0px)` /
`(min-width: 99999px)` according to the chat theme; the core repeats the same for the case where
the text arrived unprocessed (`rewrite_color_scheme`).

What is added on top of litehtml in the UA styles (`UA_EXTRA` in `renderer.cpp`):
`box-sizing:border-box` for controls (as in the Blitz prototype), a border and background for
`input`/`select`/`textarea`/`button` (litehtml has no controls at all — they would
not even be drawn as a box), `[hidden]`, `details`/`dialog` without `open`.
The `<input>` value is still not drawn — that is a litehtml hole, not the glue's.

Since v0.1.1 the profile's UA layer follows `UA_EXTRA` in the master CSS, as on every
renderer (`docs/sdk-v1.md` §2.4, `docs/decisions.md`, "The renderer's defaults"): the
root and text defaults on the theme variables and the client-styled `<button>`.
CMake reads it from `packages/core/profile.json` (`tables.uaStylesheet`) into
`tw_ua_stylesheet.h` at configure time (`scripts/build-litehtml-web.ts` does the same
for the wasm build), so there is no copy here. The host's `theme` message goes
through `src/theme.cpp`, the C++ twin of `themeVariables` (the fixed names, the
grammars, the palette and the text tokens always complete), into an inline
`!important` style on `<html>`; the widget's own style ops for those names on the root
are dropped. Before the first theme message the scheme's defaults stand. Two litehtml
behaviours shape how the layer goes in: a `var()` inside a shorthand is substituted
after the cascade, so the master CSS's `margin: var(…)` would beat a widget's
`margin: 0`; and the master CSS is sorted by specificity, so litehtml's own
`body { margin: 8px }` would beat a later `:where(body)`. The renderer therefore
replaces each `var()` of the template with the theme's value (`theme::substitute`)
and unwraps `:where()` (`theme::unwrap_where`), and rebuilds the document when the
theme changes. litehtml applies the whole master CSS before any author CSS, so the
widget still wins every property it sets. litehtml propagates a root or
body background to the whole raster itself (`html_tag::get_background`, the root's
solid fill covers the canvas), so the layer paints no background and the bubble shows
where the widget paints none. Known differences from the WebViews: `ui-monospace` is
not resolved by the CoreText backend (code falls back to the text font), `opacity` on
a disabled `<button>` is not painted, the `cs` query answers only a few properties
and no custom properties (most of `auto/22-ua-defaults` reads as failed although the
picture is right), and there is no text selection at all, so `user-select: text`
leaves the text unselectable (allowed: the widget still works, `docs/decisions.md`,
"Text selection").

## How to look

iOS / macOS (the Xcode project's pre-build phase calls
`apps/renderer-litehtml/build-apple.sh`: cmake + ninja from Homebrew, the static
library is ≈ 3.4 MB before linking, the first build ≈ 10 s; the library is built for
iOS 16.3 / macOS 13.3 because of `std::to_chars(double)` in `twidget/json.h`, the demos
stay on 16.0 / 13.0 — the linker warns and links):

```bash
cd apps/macos/DemoApp && xcodegen generate && xcodebuild build -scheme TwidgetDemoMac -destination 'platform=macOS' -derivedDataPath /tmp/twidget-mac
/tmp/twidget-mac/Build/Products/Debug/TwidgetDemoMac.app/Contents/MacOS/TwidgetDemoMac --renderer litehtml            # or --litehtml / --blitz / --webview
/tmp/twidget-mac/Build/Products/Debug/TwidgetDemoMac.app/Contents/MacOS/TwidgetDemoMac --autorun generated --litehtml  # the runners work on any renderer
xcrun simctl launch booted org.telegram.widgetsandbox.TwidgetDemo --litehtml
```

Android (the `apps/android/renderer-litehtml` module, CMake NDK through
`externalNativeBuild`, `arm64-v8a`):

```bash
cd apps/android && JAVA_HOME=… ./gradlew :demo:installDebug
adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es open compat/calculator.html --es renderer litehtml
```

Desktop (C++ core, macOS):

```bash
cd packages/desktop && cmake -S . -B build -G Ninja -DTW_BLITZ=ON -DTW_LITEHTML=ON && cmake --build build
./build/twidget-tests                                                   # 29 tests: + litehtml (layout/queries/raster, click, frozen)
./build/twidget-run ../corpus/compat/tabs.html --litehtml --dpr 2 --png /tmp/tabs.png --click 60,30
./build/twidget-run --corpus ../../corpus/compat --litehtml --dpr 2 --png-dir /tmp/compat   # 21/21; --attacks --litehtml: 89, 0 leaks
```

Web (Emscripten from Homebrew; the module is gitignored, Vite serves it at `/litehtml/`):

```bash
corepack pnpm build:litehtml-web      # → apps/renderer-litehtml/pkg-web/twidget_litehtml.{js,wasm}, 1.4 MB
corepack pnpm dev                     # the litehtml toggle on the widget screen, or ?file=compat/calculator.html&litehtml=1
                                      # the "compare all" toggle / &compare=1: four columns side by side — iframe, Blitz, litehtml and reference,
                                      # each with its own sandbox from the same HTML and its own status line (loaded / dead, init / build / paint);
                                      # the sandboxes start one after another (three QuickJS workers at once do not fit into the cpuLimitMs boot slice)
corepack pnpm test:unit               # + apps/web/tests/unit/litehtml-wasm.test.ts: the same wasm from Node with stub fonts
TW_RENDERER=litehtml corepack pnpm test:generated   # → docs/compat-report-generated-litehtml.md, gallery in reports/generated-litehtml/
```

Without a device — the host harness on a Mac: validated ops from the Node harness →
PNG (and the HTML that litehtml saw):

```bash
corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/compat/tabs.html --ops-file /tmp/tabs.json
cd apps/renderer-litehtml && cmake -S . -B build -G Ninja && cmake --build build && ./build/twidget-litehtml-tests
./build/twidget-litehtml-run /tmp/tabs.json /tmp/tabs.png --width 360 --dpr 2 --html /tmp/tabs.html --tap 60,30
```

Diagnostics panel lines: `litehtml-init` (creating an empty document),
`litehtml-build` (serialisation, parsing+layout and the number of passes of the last
rebuild, HTML size, display-list length), `litehtml-paint` (ms and size of the
last frame; on Android — receiving and parsing the display list),
`litehtml-touch` (what the document answered to the touch, like `blitz-touch`).

## What comes out (host harness, 360 CSS px, 2×, Apple Silicon)

| Widget | HTML | parsing+layout, 2 passes | display list | CG rasterisation |
|---|---|---|---|---|
| compat/tabs | 2.0 KB | 6 ms | 15 ops | 1.3 ms |
| compat/calculator | 1.6 KB | 8 ms | 48 ops | 1.1 ms |
| generated/01 (mortgage calculator) | 2.3 KB | 7 ms | 32 ops | 4.6 ms |
| generated/06 (laptop table) | 3.0 KB | 27 ms | 87 ops | 5.9 ms |
| generated/13 (gantt) | 3.3 KB | 6 ms | 129 ops | 4.6 ms |

This is per batch of `ops`: a widget with a once-a-second timer rebuilds the
document once a second, an animation through `requestAnimationFrame` — 60 times a
second (in Blitz and WebView the update is incremental). Creating CoreText fonts
for every description is a noticeable share of the time: `tabs` on a repeated
build of the same document is cheaper than the first.

By eye (PNGs in `reports/` during runs, three columns — the gallery from
`docs/blitz-experiment.md`): text, flex, tables, borders with radii,
gradients, `var()`/`calc()` — as in Chromium up to text rasterisation.
The holes are the same as in the "The same check for litehtml" table in
`docs/blitz-experiment.md`, and now they are visible in a live widget:

| Hole | How it looks |
|---|---|
| `display: grid` | the grid collapses into a single column (gantt, habit tracker, palette) |
| inline `<svg>`, `<canvas>` | not drawn (timer ring, charts); the canvas is an empty box, as in the Blitz prototype |
| `transform`, `box-shadow`, `opacity`, `transition`, `position: sticky` | flat cards; `position: absolute` sometimes drifts away (the Pomodoro timer) |
| forms | boxes without values, slider or dropdown; `checked` is an attribute, the check mark is not drawn |
| scrollable blocks | `overflow: auto` lays out but does not scroll; `scrollTop` is ignored |
| `getComputedStyle` | only `display`, `visibility`, `color`, `background-color`, `font-size`, `width`, `height`; the rest `""` |
| `text-transform` | ASCII only (Unicode case would need a call into the platform) |
| events | `pointer*`/`mouse*`/`touch*`/`click` with `targetId`, `offsetX/Y`, the target's `value`/`checked`; no keyboard, wheel, or `input`/`change` from controls (there are no controls) |

Android, emulator, measured in a neighbouring session (`docs/android-renderer-bench.md`,
`--es autorun bench --es renderer litehtml`): 20 widgets, ≈ 1.1 MB per bubble,
start-up 19 / 37 / 107 ms (median / p90 / maximum).

Desktop harness (`twidget-run --corpus ../../corpus/compat --litehtml --dpr 2`,
800 px, Apple Silicon): 21/21 without errors, raster 0.3–6 ms, `loaded` 24–56 ms;
attacks 89, leaks into validated ops and requests from the renderer 0. Web (wasm in
Chromium, the calculator): creation 66 ms including module load and compilation,
parsing+layout 53 ms over two passes (wasm without SIMD fonts: every word
width is a call into JS `measureText`), a frame 5.7 ms for 48 operations.

## Conclusion for the comparison

Three renderers on one base now switch with a single gesture, and each has
its own line in the panel. For a "preview in a bubble" litehtml draws a card from the
validated DOM in single-digit milliseconds per frame and tens per rebuild —
without a WebContent process, but also without grid/SVG/transforms (on `corpus/generated`
grid is used by 17 of 40). Blitz holds the CSS profile but not iOS emoji fonts or
controls; WebView holds everything and costs a process. The next step, if the comparison
is to continue: run `--autorun generated` on all three with the same report
(`loaded in`, `renderer ready at`, frames) on a phone, and a gallery of screenshots from the
demo rather than from static dumps.
