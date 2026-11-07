# litehtml on twidget's fork: what was ported, and the profile still to decide

Date: 2026-10-13. Context: the renderer is now litehtml on twidget's fork
(`TelegramMessenger/twidget`, `Sources/LiteHTML`), with no WebView in the bubble.
This supersedes the litehtml column of
`docs/widget-profile-draft.md` only in what it lists below; the probes have not
been re-run on the fork.

The renderer has since moved to twidget as TwidgetCore (`Sources/TwidgetCore`,
`docs/TWIDGET_CORE.md` there), which reads this repository as its protocol.
`apps/renderer-litehtml` here is a copy kept until this repository drops it.

## What was ported from twidget

twidget's Swift layer did these things on Apple only. They are now shared C++ in
the litehtml core or shared TypeScript in the sandbox, so every host gets them.

| Piece | Where | From |
|---|---|---|
| Fork support: documents patched in place (`update_from_string`, `update_styles`), the fork's SVG, group and box-shadow paint hooks | `apps/renderer-litehtml/src/renderer.cpp`, `display_list.h`, behind `TW_LITEHTML_TWIDGET` | `LHContainer.mm` |
| CSS animations and transitions: `@keyframes`, timing functions incl. `steps()`, `linear()` and `calc()` times, interpolation of numbers, colours and transform lists, animation and transition events | `src/animations.*` | `WidgetAnimations.swift` |
| Web Animations: the renderer samples the validated `anim` records of the sandbox's `webAnimations` feature (`packages/core/src/sandbox/waapi.ts`, `Validator.validateAnim`), and answers the `anims` query | `src/animations.*` | `WidgetWebAnimations.swift` |
| Events: hover enter/leave/over/out over the whole ancestor chain, `dblclick`, `wheel`, `keydown`/`keyup`, moves coalesced per frame, click on the common ancestor | `src/renderer.cpp` (`hover`, `wheel`, `key`, `tick`) | `LiteHTMLWidgetRenderer.swift` |
| Canvas 2D: the interpreter (state stack, transforms, paths and `Path2D`, SVG `d` incl. arcs, gradients, clip, text, `drawImage`, `putImageData`, hit tests) emits `CanvasOp`s a `Backend` runs | `src/canvas.*`, `src/canvas_ops.h`, `Backend::canvas_run/read/release` | `WidgetCanvas.swift` |
| `light-dark()` resolved in the renderer; tag, attribute and property names checked before they are written into the generated HTML | `src/css.*`, `src/renderer.cpp` | `WidgetDOM.swift` |

The `anim` message and the `anims` query are the `webAnimations` feature of
`docs/protocol.md`: a host lists it in `config.features` to turn it on.

How it fits together:

- **An animation frame patches style holes.** Overrides are written after the
  fork's `-lh-anim` marker and applied with `update_styles`, so a frame neither
  serialises nor re-parses the document. A renderer asks for frames only while
  `wants_tick()` holds.
- **A canvas is laid out as an image.** The fork makes `<canvas>` an image
  element whose source is `tw-canvas:<id>/<w>x<h>`; the backend draws its
  bitmap there. Canvas ops run when they arrive, not at paint time.

### Compositor layers

An element whose animations write only `transform`, `opacity`, `translate`, `rotate` or `scale`
can be handed to the platform's compositor instead of being painted every frame
(`src/compositor.h`, ported from twidget's `WidgetCompositor.swift`):

- **The core decides and bakes.** `Renderer::composition()` lists the layers after each
  `paint()`. Each layer carries:
  - the element's subtree as its own display list, without the element's own transform and
    opacity;
  - its bounds;
  - an optional clip from one clipping ancestor;
  - a raster scale;
  - a track of linear keys, each a full 2D matrix plus an opacity.

  Easing, `steps()`, direction, iterations and Web Animations timing are all baked into those
  keys, by halving spans until the box's corners are within 0.05 px. A platform therefore only
  needs linear keyframes, and a loop repeats.
- **The document leaves layers out.** Promoted elements are skipped in the main paint (the fork's
  `skip_paint`), and their sampled transform and opacity no longer reach the document. The core
  stops asking for frames for them, and wakes only when an iteration or end event is due
  (`next_tick_in()`).
- **Promotion rules:**
  - nothing animates inside or around the element;
  - it has no filter, and no ancestor groups its paint;
  - at most one ancestor clips it;
  - nothing painted after it meets the area it sweeps over;
  - at most 64 layers, and no track shorter than 0.1 s.
- **Off by default.** `set_compositing(true)` turns it on; a platform without a compositor keeps
  painting every frame.
- **`resume_track()`** helps a compositor that cannot start an animation part way through: it
  turns a loop to its current phase and cuts a one-shot to what is left.

The Windows spike plays layers with `Windows.UI.Composition`:
- each layer is a sprite drawn once by Direct2D;
- its transform is an expression over a property set whose `Vector4`/`Vector2` keyframes
  interpolate component by component, as the core baked them;
- opacity is a scalar keyframe animation;
- the clip is a rounded-rectangle geometric clip, which cannot round uneven corners.

### State per platform

The shared parts build everywhere, but each host still has work to do:

| | Windows (UWP spike, Direct2D) | Apple (CoreGraphics) | Android | Web (wasm) |
|---|---|---|---|---|
| `canvas_run` / `canvas_read` | done (spike, outside this repo) | — | — | — |
| calls `tick()` (animations) | done | — | — | — |
| forwards `hover`, `wheel`, `key` | done | — | — | — |
| fork paint hooks (path, group, shadow) | done | — | — | — |
| compositor layers (`set_compositing`) | done | — | — | — |

Without `tick()` a host renders the state at each rebuild, as before. Without
`canvas_run` a canvas stays empty, as before.

### Checks

- The core was compiled with MSVC against both the fork and the vendored
  upstream litehtml (without `TW_LITEHTML_TWIDGET`). It was not built here
  with clang for Apple, Android or wasm; `CMakeLists.txt` and
  `scripts/build-litehtml-web.ts` list the new sources.
- A headless harness (events, animations, Web Animations, canvas, compositor
  layers, `light-dark()`, names) runs the core with a stub backend: twidget's
  `Tests/TwidgetCoreTests`. `tests/test_main.cpp` here needs the CoreGraphics
  backend and was not run.
- In the Windows spike, `corpus/common` (32) and `corpus/htmlbubbles-ported`
  (116) load with no errors.

## The profile: one subset, decided on purpose

What ships is user-generated content, not the widgets in `corpus/`. Those are
demos written to a profile, so how often they use a feature says little about
what users will write. The profile therefore has to be decided, not measured.

It also cannot be "everything": choosing litehtml is choosing a subset. Per
`docs/decisions.md` ("one profile for the promise"), whatever the subset is, it
is the same on every client:

- **The validator enforces it on every platform, the web included.** Anything
  outside is dropped and reported, so a widget never draws more in one client
  than another. The web renderer cannot be the iframe in that case, or it must
  be held to the same cut.
- **`docs/widget-runtime-profile.md` becomes that subset.** Today it describes the
  WebView, and it is what bots are told: `@container`, `@layer`, SVG masks and
  filters, canvas patterns. A bot that follows it today produces widgets
  litehtml does not draw.
- **A feature is in only if every backend draws it on the GPU natively.** Each
  backend draws in its own API; the core owns what a command means (state,
  transforms, paths, clip stack, compositing order). A feature one target lacks
  is left out rather than emulated on that target.

### What the fork changes against the draft's litehtml column

`docs/widget-profile-draft.md` found that litehtml switched off what LLM widgets
are made of: `gap`, grid, `box-shadow`, `opacity`, `transform`, inline SVG. The
fork has grid (`render_grid.cpp`), parses `gap`, `box-shadow`, `opacity` and 2D
transforms, has an SVG element and paint hooks for it, and the ports above
add canvas, `@keyframes` and transitions. That should move most of those rows,
but **it has not been probed**: re-running `scripts/profile/probes.ts` on the
fork is the first thing to do before settling the profile.

### Canvas

Canvas is in the profile for speed, so it is drawn on the GPU everywhere, not
rasterised in the core. That makes what each graphics API can do the limit. As
far as known, without checking each platform's documentation:

| Feature | Proposal |
|---|---|
| paths, fill and stroke with solid, linear and radial paints, text and `measureText`, transforms, `clip`, dashes, `globalAlpha`, shadows, `drawImage`, `getImageData` / `putImageData` | in |
| the Porter-Duff modes and `lighter` | in: every target has them |
| `strokeText`, `toDataURL` | in: glyph outlines and PNG encoding exist everywhere |
| `multiply`, `screen`, `overlay`, `darken`, `lighten` | in, if Android's minimum API has them through `PorterDuff.Mode` |
| the other separable blend modes | out, unless Android's minimum API is 29 or more (`BlendMode`) |
| `hue`, `saturation`, `color`, `luminosity` | out: QPainter has none |
| `createConicGradient` | decide: Direct2D has no conic brush; CoreGraphics only on recent systems |
| `ctx.filter` | decide |

The demo corpora use only rectangles, text, `save`/`restore`,
`translate`/`rotate`, `globalAlpha` and one linear gradient directly, plus
Chart.js. Running Chart.js 4 through the interpreter would show what it needs.

### What the demo corpora use (indicative only)

A regex scan of `common`, `htmlbubbles-ported`, `showcase`, `compat` and
`generated` (230 files) gave the following. It counts what the demos'
authors chose, not what users will.

- In `generated` (40 LLM widgets, the nearest to user content): grid 17,
  `<input>` 14, transitions 14, `box-shadow` 12, `<select>` 6, Chart.js 5.
- At-rules: `@keyframes` and `@media (prefers-color-scheme)` only; nothing uses
  `@container`, `@supports` or `@layer`.
- `filter`: 8 widgets, only `blur()`, `brightness()` and `drop-shadow()`.
  `backdrop-filter`: 1. `mix-blend-mode`: 0. `conic-gradient()`: 10.
- 3D transforms: 4 widgets (card flips).
- Properties the fork does not parse: `font-variant-numeric: tabular-nums`
  (28), `pointer-events` (16), `user-select` (16), `touch-action` (6),
  `text-overflow: ellipsis` (4).
- Inline SVG: 56 widgets, nearly all plain shapes, text and linear gradients.
- `element.animate()`: 1 widget.

## Open questions

1. **Form controls.** litehtml draws neither `<input>` values nor `<select>`.
   Does the core draw them itself, the same on every platform, or are they out
   of the profile?
2. **3D transforms.** In, or 2D only?
3. **`filter` and `backdrop-filter`.** Only `blur()`, `brightness()` and
   `drop-shadow()`? If `backdrop-filter` is in, it must sample only the
   widget's own content: what is behind the bubble differs per app.
4. **Canvas.** The "decide" rows above, and Android's minimum API level.
5. **Text.** Platform fonts and shaping make the same widget wrap differently
   on each platform. Is that accepted, or does the core bundle fonts and a
   shaper (HarfBuzz)?
6. **Web.** Does the web client run the litehtml core (wasm) so it cannot draw
   more than the native clients, or the iframe with the validator's cut?
7. **Unparsed properties.** `font-variant-numeric`, `pointer-events`,
   `touch-action`, `text-overflow`: in?
8. **Hover.** `:hover` and the hover events have no meaning under a finger.
   Keep them for desktop only, or out?
9. **Not ported from twidget yet.** Magic move, layers for elements with
   effects or overlapping content (twidget also promotes what covers a layer), `filter` painting
   (the fork's `draw_filtered` / `draw_backdrop`), scrolling inside a widget.
   Which of them belong in the profile?

## Next

- Re-run `scripts/profile/probes.ts` on the fork and redo the litehtml column.
- Answer the questions above, then rewrite `docs/widget-runtime-profile.md` and
  make the validator enforce it.
- Fill the platform table: `canvas_run`, `tick()` and the event calls on
  CoreGraphics, Android and wasm.
