# twidget

Native rendering for [twidget](#twidget-widgets) widgets on Apple platforms, built on a heavily optimized fork of [litehtml](https://github.com/litehtml/litehtml). It powers the interactive HTML messages in Telegram for macOS.

twidget is one framework shared by every client:
- **The shared parts.** A hostile sandbox runs the widget as written, and a validator checks every DOM operation against one policy.
- **The per-client parts.** Each client implements the engine glue and a renderer.

This repository is the Apple renderer. Validated operations come in, events and layout answers go out, and pixels come from litehtml instead of a WebView. It speaks the same host protocol as the WebView renderer, so any twidget host can use it. The twidget bundles themselves are not part of this repository.

| Product | What it is |
|---|---|
| `TwidgetRenderer` | The twidget renderer: mirror DOM, litehtml layout and drawing, events, layout queries, CSS animations and transitions, canvas 2D on CoreGraphics |
| `LiteHTML` | The renderer core: parsing, sanitizing, layout, CoreGraphics and Metal drawing, hit testing, text selection |
| `TwidgetAssets` | The widget icon set (a Phosphor Icons subset) and the portable C++ resolver for `icon:` URLs |
| `HTMLScript` | A lighter standalone mode: `render()` returns HTML, actions and timers drive it. Runs on QuickJS by default (hard heap cap, same engine as twidget's sandbox); JavaScriptCore is available as `JSCScriptRuntime` |
| `HTML3D` | `<script src="app:three">` for `HTMLScript`: three.js r160 with its WebGL renderer replaced by a Metal one, on QuickJS or JavaScriptCore |
| `HTMLSandbox` | The CPU quota policy and the large-stack script thread |

macOS 10.13+, Swift 5.5+, C++17.

Other platforms use TwidgetCore, the same renderer in portable C++20, built with CMake from the
repository root: see [docs/TWIDGET_CORE.md](docs/TWIDGET_CORE.md).

```swift
.package(url: "https://github.com/TelegramMessenger/twidget.git", branch: "main")
```

## twidget widgets

`LiteHTMLWidgetRenderer` implements the renderer side of the twidget host protocol:
- **Host → renderer:** `ops`, `query`, `listen`, `preventDefault`, `requestFrame` and `frozen`.
- **Renderer → host:** `ready`, `size`, `event`, `gesture`, `result`, `frame`, `resize`, `media` and `violation`.

A host adapter is a few lines:

```swift
import TwidgetRenderer
import WidgetSandbox

extension LiteHTMLWidgetRenderer: Renderer {
    public func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    public func snapshot(completion: @escaping (NSImage?) -> Void) { completion(snapshot()) }
}

let host = try WidgetHost(html: html, theme: theme, width: width) { _, theme, width in
    LiteHTMLWidgetRenderer(theme: theme, width: width)
}
```

How the protocol maps onto native code:

| Protocol | Native implementation |
|---|---|
| DOM operations | A mirror document laid out by litehtml. Frames that change styles, text, classes, attributes or child lists patch the live document instead of parsing it again; animation-only frames skip serialization entirely. |
| `light-dark()`, theme | Resolved for the current theme, re-rendered on `setTheme` |
| `@keyframes`, `animation`, `transition` | A native animation engine: timing functions, `steps()`, iterations, direction, fill modes, and interpolation of numbers, colors and transforms (through their CSS primitives, across units). Transform and opacity animations are baked into Core Animation keyframes and run on the render server with no per-frame CPU work; everything else writes into an animation layer that litehtml keeps out of the cascade it reports back. Animations stop under `display: none` and restart when shown, as in browsers. |
| Script-driven motion | Scripted transforms are considered for promotion on their first update; repeated opacity updates are also promoted. Layer and pixel budgets still apply. Transform-only updates then avoid layout and drawing. |
| Canvas 2D | CoreGraphics on a native BGRA backing: paths, Path2D, gradients, text, transforms, clipping, shadows, compositing, `getImageData` and `putImageData`. The canvas takes its CSS size like any replaced element. |
| Layout queries | `rect`, `box`, `cs`, `measureText`, `elementFromPoint`, `matchMedia`, `viewport` and canvas calls, answered synchronously |
| Events | Pointer, mouse, click, double-click, hover, key and animation events, with per-frame coalescing of moves like the WebView renderer |

Both renderers render the ported demo corpus and Chart.js and D3 widgets. `tools/perflab/TwidgetLab` in the Telegram for macOS repository compares them side by side.

Input changes join an already requested animation frame before painting, including widgets with a continuous frame loop. Rendering passes are charged by thread CPU time. Passes up to 100 ms remain interactive; more expensive passes accrue throttle debt capped at 100 ms, so transient overload cannot stack multi-second pauses. Script and DOM quotas remain independent of this rendering threshold. Simple element rasters reserve space for their text and shadows, and compositor admission uses the same bounds. Rotation retains sampling headroom without being treated as a 2× enlargement.

On the ported corpus, every widget lays out to WebKit's height within 4 pt, and the remaining pixel differences come from glyph antialiasing. Box edges snap to device pixels as in WebKit. A frozen-animation suite (keyframes paused at fixed times, across transform functions, origins, opacity, colors, sizes, easings, `steps()`, directions and fill modes) renders with no geometric difference from WebKit.

**Known gaps:**
- Form controls render as static content.
- Native hosts can enable scroll containers with `WidgetHost.Options.nativeScrolling`; the shared widget profile clips them by default.
- Fallback fonts for symbols such as ✕ can differ from WebKit's choice.
- WebGL is not part of the twidget protocol.

### Script animations

`Element.animate()` and the rest of the Web Animations API run in the sandbox shim: `Animation`, `KeyframeEffect`, `DocumentTimeline`, `getAnimations()` (with `CSSAnimation` and `CSSTransition` objects for running CSS animations and transitions), promises and `finish`, `cancel` and `remove` events. The shim owns the timing model, so reading `currentTime` or `playState` never leaves the sandbox. Renderers only mirror the effect state they are sent:

| Message | Direction | Content |
|---|---|---|
| `anim` | sandbox → host | `host('anim', json)` after the ops of the same task: a list of animation records |
| `{type: 'anim', now, anims}` | host → renderer | validated records; `now` is the host clock in ms when the host sent it |
| `{q: 'anims', id, subtree}` | query | running CSS animations and transitions of an element: `kind`, `id`, `name`, `index`, `origin`, `currentTime`, `playbackRate`, `paused` and their timing |

A record is `{aid, target, keyframes: [{offset, easing, composite, values}], timing: {duration, delay, endDelay, iterations, iterationStart, direction, fill, easing}, composite, iterationComposite, startTime, holdTime, playbackRate}`. Times are milliseconds; `iterations: null` is infinite. `startTime` is on the host clock (the clock behind the engine's `now`) and set while the animation runs; `holdTime` is the local time of a paused or pending animation. `{aid, remove: true}` drops a record. A record with `css: {kind, name, origin}` instead of keyframes controls a CSS animation reported by the `anims` query (`origin` is its opaque identity) and may carry `cancelled: true`.

The host validates every record like a style op: the target must be a rendered element, every keyframe value passes the CSS sanitizer (rejected values are dropped, `animation-*` and `transition-*` are never animated), easings match a strict grammar, and there are caps on keyframes (64), values per keyframe (64), times (24 h), iterations, playback rate (±1000) and live animations (1024 per widget).

`LiteHTMLWidgetRenderer` samples records with the same timing math as the shim. Effects on `transform` and `opacity` are baked into `CAKeyframeAnimation`s whose `beginTime` and `speed` carry the start time and playback rate, so running animations cost no main-thread work. Paused animations are applied to the promoted layer once. Other properties are sampled per frame like CSS animations. Finish times are known to the shim, which wakes once per finish instead of polling.

### Magic move

Mark a container with `data-transition="move"`. When an operations batch adds, removes or reorders its descendants, keyed descendants animate from their old boxes to their new ones instead of jumping:

```html
<div class="list" data-transition="move">
  <div data-key="a">…</div>
  <div data-key="b">…</div>
</div>
```

- An element's key is its `data-key`, or else its `id`. Keys must be unique; duplicates don't animate.
- An element whose key survives the batch moves and resizes on a spring. Its frame animates, not a scaled snapshot, so text never stretches; a solid background fills the growing area and rounded corners keep their radius. If its content changed, old and new content cross-fade inside the moving frame.
- New keys fade and scale in, and removed keys fade and scale out.
- A keyed element inside another animating keyed element rides inside its ancestor's snapshot.
- Elements that are running a CSS animation or transition keep their own motion.
- A change that arrives mid-flight continues from the on-screen position, size and velocity, and the half-finished cross-fade becomes the outgoing content.
- Moving layers keep the document's paint order. The first frame matches the old state and the settled frame matches a fresh render pixel for pixel.
- Content that keeps changing (a live counter) snaps instead of morphing.
- With Reduce Motion on, the change dissolves in place. Nothing animates off screen or beyond the layer budget (48 items, 4096² pixels); the change then applies at once.

The moving elements are snapshots on Core Animation layers, so the flight costs no layout or drawing. At the end, the layers are removed in the same transaction that repaints the document.

## Rendering a document

```swift
import LiteHTML

let document = LHDocument(html: html, css: css)
document.colorSpace = CGColorSpace(name: CGColorSpace.displayP3)
let size = document.layout(withMaxWidth: 360)
document.draw(in: context, origin: .zero, clip: CGRect(origin: .zero, size: size))
```

- **Links and actions.** `anchorHandler` receives links that passed the sanitizer. `actionHandler` receives clicks on elements with `data-action` (and an optional `data-value`).
- **Pointer input.** `mouseMoved`, `mouseDown`, `mouseUp` and `cursor` handle hover and active states.
- **Animated documents.** Build each new frame with `LHDocument(html:css:previous:)`. Inline styles, text, classes, attributes and child lists are patched into the previous tree instead of parsing it again; inserted elements go through the same sanitizer and limits as a parse. `LHDocument(previous:styles:attribute:css:)` patches style values directly, with no HTML at all, and `patchDirtyRects` names the boxes a paint-only patch can have changed.
- **Partial repaints.** `displayList(maxWidth:scale:)` and `changedRects(since:)` give exact dirty rectangles on the device-pixel grid of `scale`, so a host repaints only what changed.
- **Images.** `copyImage(maxWidth:rect:scale:base:dirty:)` redraws only a dirty area on top of the previous bitmap.

## Security model

Every document is validated before layout. The validator is an allowlist, not a blocklist:

- **Tags.** HTML and SVG tags are allowlisted. Scripts, iframes, frames, objects, embeds, audio and video, `<base>`, `<meta>`, `<link>` and `<template>` are dropped. Unknown tags render as `<span>`, with the original name kept in `data-lh-tag`, so their text survives and their behavior does not.
- **Attributes.** Attributes are allowlisted, every `on*` handler is dropped, and attributes with the reserved `data-lh-` prefix cannot be forged.
- **URLs.** Links may use `http`, `https`, `mailto` and `#fragment`. Images may only use `data:image/…` with an allowlisted MIME type and `icon:` names from the built-in icon set. SVG images go through the same validator as inline SVG and load nothing external. A host adds its own schemes with `LHSanitizer.setLinkSchemes(_:imageSchemes:)`. URLs with those schemes reach the host's link handler or image provider. The renderer never fetches anything.
- **CSS.** At-rules are allowlisted, so `@import`, `@font-face` and `@namespace` are removed. Every `url()` and image function is checked against the image rules, and `behavior` and `-moz-binding` are blocked.
- **Structure limits.** The parser stops at 256 levels of nesting, 20,000 elements and 200,000 table cells, so a hostile document cannot make layout explode. Patched-in subtrees count against the same limits.
- **Canvas sources.** Only `<canvas>` may show a canvas bitmap, through the renderer's own `twcanvas:` source; `<img>`, SVG `<image>` and CSS `url()` can't reference one.
- **Bounded caches.** Shared font, canvas font, color, raster and interpolation caches are all bounded, so no widget can grow memory through them.
- **Reporting.** `sanitizerCounts` and `sanitizerDetails` report what was removed, for logs and tests.

The policy lives in portable C++ (`lhx::SanitizerPolicy`, `lhx::Sanitizer` in `Sources/LiteHTML/core`), so other ports can share it.

## Standalone scripted documents

For hosts that don't run twidget, `HTMLScript` is a smaller model: the script returns HTML instead of driving a DOM.

A document with `<script>` blocks becomes an `HTMLScript`. The script's `render()` function returns the HTML to show. The host gets `scriptDidChange` whenever the output changes and draws `currentHTML`.

```swift
import HTMLScript

let script = HTMLScript.make(html: html)
script?.host = self
script?.setVisible(true)
let output = script?.currentHTML
script?.performAction("tap", value: nil)
```

**What scripts get:**
- `render()`, `onAction(name, value)` and `onKey` / `onPointer` / `onFocus`.
- `setTimeout`, `setInterval`, `requestAnimationFrame` and `performance.now`.
- `update()`, `transition(kind)` and `copyText` (only while handling a user action).
- `animate`, `tween` and `ease` helpers, plus `console`.

**What scripts don't get:** the DOM, `fetch`, XHR, WebSockets, storage, workers or WebAssembly.

**Limits:**

| Limit | Value |
|---|---|
| One call | 50 ms. A runaway call is terminated and the script is stopped. |
| Library load (three.js) | 2 s |
| CPU quota | 200 ms per sliding 1 s window. Timers and frames past the quota are deferred, never dropped. User events always run. |
| Memory | 128 MB per call (see below) |
| Timers | 16 at once, 50 ms minimum interval |
| Output | 256 KB of HTML per `render()` |
| Hidden documents | Timers and frames pause when the host calls `setVisible(false)`. Due timeouts fire when the document becomes visible again. |

On JavaScriptCore, the memory guard measures the process footprint around a call. That makes it a heuristic, not an isolation boundary. On QuickJS, the limit is a hard heap cap per document.

The frame clock is a `CVDisplayLink` bound to the display the host reports through `HTMLScript.setDisplay`, so animations run at the display's refresh rate, including 120 Hz ProMotion. `HTMLScript.errorMarkup` controls how a script error is shown.

## Bring your own engine

### JavaScript

The script engine is reached only through `HTMLScriptRuntime` and `HTMLScriptContext`:

```swift
public protocol HTMLScriptContext: AnyObject {
    func boot(prelude: String, native: [String], call: @escaping HTMLScriptNativeCall, arguments: [HTMLScriptValue]) throws
    func evaluate(_ source: String, name: String, timeLimit: TimeInterval?) throws
    func invoke(_ hook: String, _ arguments: [HTMLScriptValue]) throws -> HTMLScriptValue
}
```

- `boot` runs the prelude. The prelude evaluates to a function that takes a `native` object and returns the hooks object.
- `invoke` calls one of the hooks: `fire`, `frame`, `callRender`, `callAction`, `callInput` or `describe`.
- An engine throws `HTMLScriptError` with `.timeLimit`, `.outOfMemory` or `.exception`. The sandbox turns those into a stopped script or an error line.

`QuickJSScriptRuntime` (the default) and `JSCScriptRuntime` are the two implementations. QuickJS comes from the [TelegramMessenger/quickjs](https://github.com/TelegramMessenger/quickjs) submodule; clone with `--recurse-submodules` (SwiftPM fetches it automatically). To switch engines:

```swift
HTMLScript.runtime = JSCScriptRuntime.shared
HTMLScript.runtime = QuickJSScriptRuntime(memoryLimitBytes: 64 << 20)
```

QuickJS has no `Intl`. Scripts that format dates or numbers with it need a shim on that runtime.

### Libraries

`<script src="app:name">` loads a host library through `HTMLScriptLibrary`. Register a factory in `HTMLScript.libraryFactories["name"]`, and the script gets an instance of it, which the host reads back with `script.library(MyLibrary.self)`. `HTML3D` is one such library.

### GPU

There are two levels to plug into.

1. **Painter (immediate mode).** Subclass `lhx::Painter` (`Sources/LiteHTML/core/lhx_painter.h`) and call `paintWithMaxWidth:clip:painter:` from `LHDocumentPainting.h`. You receive every clip, group, text run, fill, gradient, image, border, shadow and SVG path in paint order, with resolved geometry. This is the right level for a Skia, Direct2D or WebGPU backend.
2. **Render list (retained).** Call `recordWithMaxWidth:recorder:` with an `lhx::RenderRecorder` (`include/LHRenderList.h`). The document becomes a flat list of `RenderOp`s:
   - `Fill`: a solid device rectangle.
   - `Mask`: a glyph or rounded corner from an alpha atlas, tinted with a color.
   - `Bitmap`: a content-keyed BGRA texture that the container rasterizes into a context you allocate.

   Your engine owns the atlas and texture cache. The callbacks (`cached`, `allocate`, `store`, `glyph`, `mask`) let it skip work it already has. `lhx::rasterizeGlyph` and `lhx::rasterizeMask` produce the same alpha coverage the built-in renderer uses.

`LHGPURenderer` is the Metal client of the render list. It uses tiled IOSurface targets, a glyph atlas, a 32 MB texture LRU and batched submission. If a document needs something the list can't express, `LHDisplayList.gpuSupported` is false and the host draws that document with CoreGraphics.

### Images

Widgets are single self-contained documents: pictures are `data:` URLs or inline SVG, and icons are `icon:heart-fill?size=24&color=ff2d55` URLs resolved from a bundled, MIT-licensed icon set (`TwidgetAssets`, portable C++). [docs/WIDGET_ASSETS.md](docs/WIDGET_ASSETS.md) is the specification every client implements.

`LHImages.setProvider { url in … }` supplies images for the host's own schemes (the scheme must also be allowed with `LHSanitizer`). For older Apple-only content, `LHImages.registerImage(_:name:)` registers `app:name` images and `sf:symbol.name?size=20&color=%23ff0000` renders SF Symbols on macOS 11+, falling back to the icon set's alias table; `LHImages.portableSymbols` resolves every `sf:` through the aliases.

## three.js and Metal

```html
<script src="app:three"></script>
<script>
const renderer = new THREE.WebGLRenderer();
</script>
```

`HTML3D` bundles three.js r160 (MIT). Its WebGL renderer is replaced by an adapter that sends geometry, instances and a compact per-frame scene record to a native Metal renderer, so there is no WebGL context. The renderer supports:
- Standard and basic materials, vertex colors and instancing.
- Points.
- Up to four lights, with a directional shadow map.
- Transparency sorting.

Call `HTML3DSession.register()` once. Then attach the layer from `script.scene3D?.makeLayer()` to your view. The bridge reads typed arrays in place on both runtimes, through the JavaScriptCore C API or the QuickJS typed-array shim.

## Performance

- **Core Animation does the motion.** Compositable CSS animations (including `steps()` and discrete jumps), script-driven transforms and magic-move flights run as Core Animation layers: up to 256 per widget, on one exact timeline, with snapshots cropped to their painted pixels and clip layers skipped when they can never clip.
- **Patches, not reparses.** Style, text, class, attribute and child-list changes patch the live document; paint-only changes reuse layout and repaint only the affected boxes.
- **Drawing.** 128 pt tiles with an 8-bit sRGB backing, per-rectangle invalidation, a raster cache for SVGs and images (a changing SVG rasterizes once per frame, not once per tile) and bounded font, color and canvas-font caches.
- **Measured against WebKit** in TwidgetLab, at equal frame rates: every animated widget of the ported corpus uses less CPU than WebKit, and a widget with 120 CSS-animated cards idles at under 1% against WebKit's 9–17%.

## Exactness

All work happens in the Lab, and every optimization is checked against a fresh render:

- **CoreGraphics path:** pixel-identical. `LiteHTMLLab bench --verify` replays recorded animation traces and compares every frame with a from-scratch render. `compare` diffs two runs trace by trace.
- **Metal path:** checked within a tolerance against CoreGraphics (`gpu --verify`). The thresholds are a maximum interior difference, a share of pixels more than 8 levels off (0.000% on the bundled traces) and zero structural differences.
- **Painter path:** receives exactly what the CoreGraphics path draws.
- **twidget renderer:** `tools/perflab/TwidgetLab` in the Telegram for macOS repository gates every change. `TW_VERIFY=1` compares every on-screen frame with a fresh render and checks handed-off animations against the animation engine; `TW_PATCHCHECK=1` compares every patched document with a fresh parse; `replaycheck` replays recorded click sessions through the patch paths.

## Lab

`Lab/` is a separate package with these tools:

| Tool | What it does |
|---|---|
| `LiteHTMLLab` | Benchmarks, verification, `sanitize`, `scripttest [--runtime quickjs]`, `threetest` and a live viewer |
| `WebKitLab` | Renders the same chats in WKWebView, for comparison |
| `EngineLab` | Compares JavaScriptCore with QuickJS |
| `PresentLab` | Experiments with presentation paths |

Sample chats live in `Examples/chats`. The [native solar-system example](Examples/native-3d/README.md)
uses smooth meshes, native lighting and depth through the existing HTML3D path;
its offscreen checker exercises QuickJS and JavaScriptCore. It is standalone
HTMLScript content, outside widget profile 1.

```sh
cd Lab
swift build -c release
.build/release/LiteHTMLLab record
.build/release/LiteHTMLLab bench --verify --label mine
.build/release/LiteHTMLLab gpu --verify
.build/release/LiteHTMLLab scripttest
```

## License

BSD 3-Clause, see `LICENSE`. Bundled third-party code (litehtml, Gumbo, QuickJS-ng, three.js and Phosphor Icons) keeps its own license, listed in `THIRD_PARTY_NOTICES.md`.
