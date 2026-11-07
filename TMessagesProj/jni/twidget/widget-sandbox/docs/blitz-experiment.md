# Experiment: Blitz and litehtml as native renderers for the generated corpus

> **Frozen since 2026-10-30** (`docs/decisions.md`, "The renderer prototypes are frozen"): no client draws with this
> prototype, nothing builds or runs it by default; what follows is the record of the comparison.

Date: 2026-10-09. Question: can the system WebView be replaced with a native
HTML/CSS engine for rendering speed in the message list without lowering the
widgets' CSS profile (as litehtml would). The candidate is [Blitz](https://github.com/DioxusLabs/blitz)
(Rust: stylo from Servo + taffy + parley + vello), commit `971f1a3` of 2026-10-09,
version `0.3.0-beta.2`. Blitz does not execute JS, so this is a test of the
renderer only: JS and the DOM stay in our sandbox, and what goes out is the same
validated DOM.

## Method

1. `scripts/blitz-experiment/dump-dom.mts` opens each of the 40 widgets in
   `corpus/generated` in the dev UI's reference mode (native Chromium, the same
   frame that `pnpm test:generated` screenshots): load, 800 ms, clicks on the
   first four buttons, 500 ms. At that moment the reference PNG is taken
   (404 px wide, dpr 1) and the DOM is serialised after the scripts have run:
   `<canvas>` is replaced by its bitmap (with Blitz our command stream would be
   drawing the canvas anyway), form state is written into attributes, `<script>`/`<link>`
   are removed, non-`data:` `src` attributes are stripped (CSP blocks them in Chromium), `:hover` and
   `:focus` are removed on both sides.
2. `scripts/blitz-experiment/twidget_render.rs` (an example inside the Blitz
   checkout) lays this HTML out headless at the same width, in two passes
   like the iframe in the dev UI (first a 24 px viewport, then the document height, so that
   `100vh` does not inflate the page), and rasterises it through `vello_cpu` into a PNG.
   `TW_SHARED_FONTS=1` builds the `FontContext` once per process, as the host
   would.
3. `scripts/blitz-experiment/compare.mts` compares the PNGs with the same `pixelmatch`
   and the same thresholds as `tests/generated.spec.ts` (threshold 0.15, AA
   on, pass threshold 3%), and writes the table below. Output PNGs and
   diff images: `reports/blitz-experiment/` (gitignored, wiped by
   `tests/global-setup.ts` on the next Playwright run).

Machine: Apple Silicon, release build, CPU rasterisation at 1× scale.

## Result

## with-profile: Blitz 0 / 20 within 3% (sandbox WebKit/Chromium path: 20 / 20)

| Widget | Blitz diff | Sandbox diff | Blitz parse / layout / paint (ms) | Height Blitz / ref | Clicks | canvas / svg / inputs | Error |
|---|---|---|---|---|---|---|---|
| 01-show-me-a-mortgage-calculator-loan-amount.html | 25.73% ❌ | 0.00% | 1 / 1 / 11 | 368 / 404 | 1 | 0 / 0 / 3 |  |
| 02-make-an-interactive-quiz-about-the-solar.html | 8.10% ❌ | 0.00% | 1 / 1 / 2 | 388 / 388 | 4 | 0 / 0 / 0 |  |
| 03-visualize-this-weekly-budget-as-a-chart.html | 8.56% ❌ | 0.00% | 1 / 1 / 11 | 348 / 353 | 0 | 1 / 0 / 0 |  |
| 04-build-a-tip-calculator-with-a-slider-for.html | 17.72% ❌ | 0.00% | 1 / 1 / 2 | 416 / 436 | 3 | 0 / 0 / 2 |  |
| 05-create-a-pomodoro-timer-with-start-pause.html | 20.99% ❌ | 0.00% | 1 / 1 / 11 | 362 / 360 | 4 | 0 / 1 / 0 |  |
| 06-show-a-comparison-table-of-three-laptops.html | 14.32% ❌ | 0.00% | 1 / 1 / 2 | 269 / 284 | 3 | 0 / 0 / 0 |  |
| 07-make-a-bmi-calculator-with-metric-and.html | 13.95% ❌ | 0.00% | 0 / 1 / 2 | 335 / 369 | 2 | 0 / 0 / 5 |  |
| 08-draw-a-line-chart-of-monthly-temperatures.html | 22.64% ❌ | 0.00% | 1 / 4 / 2 | 350 / 355 | 0 | 0 / 1 / 0 |  |
| 09-create-a-unit-converter-for-length-weight.html | 8.67% ❌ | 0.00% | 1 / 1 / 1 | 221 / 270 | 4 | 0 / 0 / 4 |  |
| 10-show-me-an-interactive-multiplication.html | 11.60% ❌ | 0.00% | 1 / 2 / 3 | 417 / 400 | 0 | 0 / 0 / 0 |  |
| 11-make-a-to-do-list-widget-where-i-can-add.html | 5.81% ❌ | 0.00% | 0 / 0 / 1 | 187 / 182 | 4 | 0 / 0 / 2 |  |
| 12-create-a-countdown-to-new-year-with-days.html | 10.83% ❌ | 0.24% | 1 / 1 / 2 | 179 / 183 | 0 | 0 / 0 / 0 |  |
| 13-visualize-a-simple-project-timeline-gantt.html | 11.45% ❌ | 0.00% | 1 / 1 / 3 | 350 / 355 | 4 | 0 / 0 / 0 |  |
| 14-build-a-color-palette-generator-pick-a.html | 7.30% ❌ | 0.00% | 1 / 1 / 5 | 288 / 292 | 4 | 0 / 0 / 2 |  |
| 15-create-a-flashcard-widget-for-8-spanish.html | 55.68% ❌ | 0.00% | 0 / 0 / 2 | 305 / 310 | 3 | 0 / 0 / 0 |  |
| 16-make-a-bar-chart-of-the-top-6-programming.html | 12.94% ❌ | 0.00% | 1 / 0 / 2 | 369 / 374 | 0 | 1 / 0 / 0 |  |
| 17-show-a-tic-tac-toe-game-against-a-simple.html | 5.27% ❌ | 0.34% | 0 / 0 / 2 | 457 / 462 | 4 | 0 / 0 / 0 |  |
| 18-create-a-currency-converter-for-usd-eur.html | 7.74% ❌ | 0.00% | 0 / 0 / 1 | 227 / 249 | 1 | 0 / 0 / 7 |  |
| 19-make-a-step-by-step-recipe-card-for.html | 13.90% ❌ | 0.00% | 0 / 1 / 3 | 834 / 805 | 4 | 0 / 0 / 7 |  |
| 20-build-a-habit-tracker-grid-for-one-month.html | 6.32% ❌ | 0.00% | 1 / 1 / 3 | 445 / 448 | 4 | 0 / 0 / 0 |  |

## without-profile: Blitz 0 / 20 within 3% (sandbox WebKit/Chromium path: 20 / 20)

| Widget | Blitz diff | Sandbox diff | Blitz parse / layout / paint (ms) | Height Blitz / ref | Clicks | canvas / svg / inputs | Error |
|---|---|---|---|---|---|---|---|
| 01-show-me-a-mortgage-calculator-loan-amount.html | 9.22% ❌ | 0.00% | 0 / 0 / 2 | 396 / 425 | 0 | 0 / 0 / 3 |  |
| 02-make-an-interactive-quiz-about-the-solar.html | 35.35% ❌ | 0.00% | 0 / 0 / 2 | 568 / 900 | 4 | 0 / 0 / 0 |  |
| 03-visualize-this-weekly-budget-as-a-chart.html | 6.99% ❌ | 0.00% | 1 / 0 / 1 | 471 / 475 | 0 | 1 / 0 / 0 |  |
| 04-build-a-tip-calculator-with-a-slider-for.html | 15.14% ❌ | 0.00% | 0 / 1 / 2 | 431 / 451 | 2 | 0 / 0 / 2 |  |
| 05-create-a-pomodoro-timer-with-start-pause.html | 24.61% ❌ | 0.00% | 0 / 1 / 1 | 366 / 364 | 4 | 0 / 1 / 0 |  |
| 06-show-a-comparison-table-of-three-laptops.html | 11.67% ❌ | 0.00% | 0 / 1 / 2 | 276 / 326 | 0 | 0 / 0 / 0 |  |
| 07-make-a-bmi-calculator-with-metric-and.html | 13.16% ❌ | 0.00% | 0 / 1 / 2 | 353 / 386 | 2 | 0 / 0 / 5 |  |
| 08-draw-a-line-chart-of-monthly-temperatures.html | 10.91% ❌ | 0.00% | 1 / 0 / 1 | 348 / 352 | 0 | 1 / 0 / 0 |  |
| 09-create-a-unit-converter-for-length-weight.html | 6.42% ❌ | 0.00% | 0 / 0 / 2 | 276 / 324 | 4 | 0 / 0 / 4 |  |
| 10-show-me-an-interactive-multiplication.html | 11.17% ❌ | 0.00% | 1 / 1 / 2 | 419 / 401 | 0 | 0 / 0 / 0 |  |
| 11-make-a-to-do-list-widget-where-i-can-add.html | 5.71% ❌ | 0.00% | 1 / 1 / 2 | 330 / 315 | 4 | 0 / 0 / 4 |  |
| 12-create-a-countdown-to-new-year-with-days.html | 14.67% ❌ | 0.74% | 1 / 1 / 2 | 195 / 203 | 0 | 0 / 0 / 0 |  |
| 13-visualize-a-simple-project-timeline-gantt.html | 8.44% ❌ | 0.00% | 0 / 1 / 2 | 358 / 362 | 0 | 0 / 0 / 0 |  |
| 14-build-a-color-palette-generator-pick-a.html | 14.64% ❌ | 0.00% | 0 / 0 / 2 | 217 / 230 | 0 | 0 / 0 / 2 |  |
| 15-create-a-flashcard-widget-for-8-spanish.html | 49.67% ❌ | 0.00% | 1 / 1 / 3 | 325 / 329 | 2 | 0 / 0 / 0 |  |
| 16-make-a-bar-chart-of-the-top-6-programming.html | 11.44% ❌ | 0.00% | 1 / 0 / 1 | 367 / 371 | 0 | 1 / 0 / 0 |  |
| 17-show-a-tic-tac-toe-game-against-a-simple.html | 4.14% ❌ | 0.00% | 0 / 0 / 2 | 469 / 472 | 1 | 0 / 0 / 0 |  |
| 18-create-a-currency-converter-for-usd-eur.html | 6.13% ❌ | 0.00% | 0 / 1 / 2 | 281 / 302 | 1 | 0 / 0 / 7 |  |
| 19-make-a-step-by-step-recipe-card-for.html | 24.36% ❌ | 0.00% | 1 / 14 / 4 | 900 / 900 | 4 | 0 / 0 / 7 |  |
| 20-build-a-habit-tracker-grid-for-one-month.html | 4.32% ❌ | 0.00% | 0 / 1 / 2 | 411 / 416 | 0 | 0 / 0 / 0 |  |

Medians over 40 rendered widgets: Blitz diff 11.45%, sandbox diff 0.00%; Blitz parse 0.5 ms, layout 0.6 ms, paint 2.0 ms, first document in the process (one-time init included) 9 ms, whole process 72 ms.

For comparison: our "sandbox → WebKit/Chromium" path gives 40/40 within
3% (`docs/compat-report-generated.md`). Blitz: 0/40 within 3%, 16/40 within
10%, 30/40 within 15%; quartiles 7.7 / 11.4 / 15.1%, maximum
55.7%.

## Where the differences come from

Breakdown from the dumps and by eye (images in `reports/blitz-experiment/`):

| Cause | Widgets | Diff | Status in Blitz |
|---|---|---|---|
| 3D transform (`rotateY` + `backface-visibility`, card flip) | 15 in both batches | 50–56% | 2D transforms only; the back of the card is visible |
| Inline SVG styled through CSS (`fill: none`, `stroke` in `<style>`) | 05 (timer ring), 08 with-profile (line chart) | 21–25% | the SVG is drawn, but CSS properties are not applied to it: outlines are filled black |
| Forms: the `<input>` value is not drawn, `range` has no thumb, `<select>` is empty, `<input type=color>` is a circle, checkboxes are larger | 01, 04, 07, 14, 18, 19 | 13–26% | forms are declared as a goal, partially implemented |
| `min-height: 100vh` on `body` | 02, 12 without-profile | 15–35% | an artefact of the reference: the dev-UI iframe adapts to the document height, `100vh` grows with it up to the 900 px ceiling; Blitz converges to a smaller height |
| Emoji with the VS16 selector (`⏱️`, `🍳`) | 19 | part of the diff | tofu; ordinary colour emoji (`🏠`, `💰`) are drawn |
| The rest: the same `.SF NS`, a different rasteriser and text metrics, occasionally a different line break | all | 4–12% | the "floor" of the comparison; `pixelmatch` at 3% does not forgive it, by eye the cards are indistinguishable |

Grid, flex, `var(--…)`, `calc()`, `box-shadow`, gradients, `border-radius`,
`opacity`, tables, `position` — everything litehtml lacks, Blitz has, and it
matches Chromium down to the text. Not a single widget crashed or
hung.

## Speed

Per document (median / p90 over 40 widgets, `TW_SHARED_FONTS=1`):

| Stage | Median | p90 | Maximum |
|---|---|---|---|
| HTML + CSS parsing | 0.5 ms | — | 0.8 ms |
| Styles + layout (both passes) | 0.6 ms | — | 14 ms (39 of 40); outlier 160–190 ms, see below |
| Rasterisation of 404 px on the CPU, 1× | 2.0 ms | — | 11 ms |
| Total | 3 ms | 12 ms | — |

The outlier: `without-profile/19` (the recipe card) consistently takes 160–190 ms
to lay out against 1 ms for its twin from `with-profile`. The cause was not found;
the suspicion is fallback-font lookup for the VS16 emoji sequences, which in this
same widget are drawn as tofu. Such pathologies in a beta engine are expected, and
in the product they would have to be caught with a layout timeout.

One-time per process: scanning the system fonts ≈ 40 ms. Without a shared
`FontContext` Blitz repeats it for every document (38 ms even on
`<p>hi</p>`): the host must create the font context once.

Not measured: JS execution and applying the diffs (they stay as they are),
rasterisation at 3× (9 times the pixels, expected 10–20 ms on the CPU, or the
`vello` GPU backend), the cost of incremental re-layout on updates,
memory per document.

## The same check for litehtml

Added on the evening of 2026-10-09 with the same pipeline: `scripts/blitz-experiment/twidget_litehtml.cpp`
(litehtml master `4a651c5` of 2026-10-08 with the `containers/cairo` container
from the repository itself, cairo + pango from Homebrew, no GTK; `data:` PNGs
are decoded into cairo surfaces, `-apple-system`/`sans-serif` mapped to SF Pro Text through
`scripts/blitz-experiment/fonts.conf`, as in Chromium).
Three columns and diff maps: `reports/blitz-experiment/gallery.html`
(`scripts/blitz-experiment/gallery.mts`), the numbers are in `metrics.json` next to it.

| | Blitz | litehtml |
|---|---|---|
| Rendered without crashing | 40 / 40 | 40 / 40 (after fixing the destruction order of the document and the container in my harness) |
| strict-diff, median (the test's metric) | 11.4% | 12.5% |
| soft+shift, median (threshold 0.3, no AA, shift ±2 px) | 5.4% | 7.1% |
| Parsing + layout + rasterisation, median | 3 ms | 44 ms (layout runs inside `createFromString` and is not measured separately; `<p>hi</p>` is 5.5 ms, the rest is creating pango fonts through fontconfig for every font description) |

What today's litehtml can do beyond its documented profile:
`var(--…)` and `calc()` are implemented (the mortgage calculator with 10 variables,
the laptop table, the chart legend match Chromium down to the
text). What is still missing, and how it looks in the gallery:

| Gap | Widgets | How it looks |
|---|---|---|
| `display: grid` | 13 (gantt), 14 (palette), 20 (habit tracker), 12 without-profile (countdown) | the grid collapses into a single column: the tracker's 31 days in one column, the countdown cards one under another; the document height is 1.5–2 times the reference |
| Inline `<svg>` | 05 (timer ring), 08 with-profile (line chart) | the SVG is not drawn at all: no ring, the chart collapsed into a row of axis labels |
| `transform`, `box-shadow`, `opacity`, `transition` | 15 (card flip) and everywhere there are shadows | the same result as Blitz for 3D, the rest are flat cards without shadows |
| Forms | 01, 04, 07, 14 | as with Blitz: the `<input>` value is not drawn, no thumb, `<select>` is empty |

The main lesson about the metric: by strict-diff litehtml looks almost like Blitz
(12.5% against 11.4%), although by eye the difference is gross. A missing ring or
chart costs few pixels, a collapsed grid costs many, and the "floor" from text is
the same. The pixel percentage against Chromium is not a measure of a native
renderer's accuracy; the gallery is what to look at. The conclusion on litehtml
does not change: without grid, SVG, transforms and shadows
it is a different widget profile, not a renderer replacement; in this corpus
17 of 40 use grid.

## Conclusion

- Blitz is the only open engine that holds the CSS profile of LLM widgets
  (grid/var/calc/shadow/opacity) and still lays out a card in single-digit
  milliseconds without a WebContent process. For a "preview in the bubble" it is a
  working option already: a static card from the validated DOM.
- Not yet for a live widget: no 3D transforms, CSS does not reach SVG, forms
  without values and thumbs, no canvas (we would have to put our command stream onto
  vello ourselves), no `overflow: auto`, beta status. These are three concrete gaps with
  known code, not "months of our own engine" as in the litehtml case.
- A 3% pixel threshold against Chromium is unattainable in principle for a native rasteriser
  (different text); such a renderer needs a different acceptance
  criterion (a structural diff or a comparison with a threshold of ~10%).
- Stack: Rust in iOS/Android/tdesktop. Acceptable for tdesktop; for the mobile
  ports it is a new build dependency, ask before pulling it in.

Next step, if we go further: run `corpus/compat` (22 real
widgets, Chart.js/D3/Tailwind) through the same pipeline and measure 3× rasterisation
on the GPU backend.

## PaintScene on CoreGraphics: Blitz layout, platform pixels (2026-10-10)

Question: can we keep stylo + taffy + parley (cascade, layout, shaping)
and hand painting to the platform, as our litehtml port does through a display
list, so that text and emoji are the system's. The seam exists: blitz-paint does not draw
itself but calls the `anyrender::PaintScene` trait (`fill`, `stroke`, `draw_glyphs`,
`draw_box_shadow`, `push_layer`/`push_clip_layer`, images through a brush).
`vello_cpu` is one of its backends. The second one is now ours:
`apps/renderer-blitz/src/cg.rs` (Apple only, raw FFI to CoreGraphics/CoreText,
no new crates; skrifa moved from the dev-dependencies to a regular one for reading the font
name and axes).

What the backend does: a `CGBitmapContextCreate` context over the same
RGBA buffer that vello returns, the CTM flipped to y-down; paths → `CGPath`;
fills and strokes → CG; layers with alpha and blend → `CGContextBeginTransparencyLayer`;
linear and radial gradients → `CGGradient`, conic ones as 96 flat
sectors; images → `CGImage` from the blob (cached by id); the box shadow is the analytic
Gaussian blur of a rounded rectangle (the same formula as vello's) into an
image. Glyphs: `CTFontDrawGlyphs` with parley's glyph ids and positions, the font
opened **from the same bytes** parley shaped with (`CTFontManagerCreateFontDescriptorsFromData`),
variable axes (SF weight) through `kCTFontVariationAttribute` from parley's normalised
coordinates. Gradient text takes the colour of the first stop; CSS filters and
`Extend::Repeat`/`Reflect` on gradients are not supported (drawn once).

Switching: `TWB_BACKEND=cg` in the process environment or `twb_set_backend(h, "cg")`
(`twb_backend(h)` answers with the name). Everything else is the same crate, the same C ABI,
the same Swift glue: the iOS/macOS demos and `twidget-run` know nothing about the
backend.

Pitfalls along the way: CoreText refuses to draw with a mirrored text
matrix (zero pixels, silently) — for glyph runs the CTM is flipped back
to y-up and the baselines are taken negated; hidden system fonts
cannot be created by name (`.SFNS-Regular` gives Times), and descriptors from the bytes
of variable SF enumerate all 369 named instances, Ultralight first — the
face is picked by its PostScript name, which skrifa reads from the default
instance.

### Result (`twidget-run --corpus … --blitz --dpr 2 --png-dir`, 400 CSS px, Apple Silicon)

| | vello (CPU) | CoreGraphics | litehtml |
|---|---|---|---|
| generated/with-profile, 20 widgets: paint median / p90 / max | 3.9 / 7.1 / 9.3 ms | 1.6 / 7.1 / 9.2 ms | 1.7 / 3.1 / 7.5 ms |
| compat, 21 widgets: paint median / p90 / max | 1.6 / 3.2 / 4.1 ms | 1.3 / 5.7 / 10.7 ms | 0.8 / 2.2 / 6.6 ms |
| strict-diff against vello, generated: median / p90 | — | 0.5% / 1.6% | 21.9% / 59% |
| strict-diff against vello, compat: median / p90 | — | 0.4% / 1.1% | 7.4% / 72% |

All 41 loaded and rendered on all three; CoreGraphics is no slower than
vello on the CPU at 2×. The CG-vs-vello diff is a fraction of a percent: the geometry matches
to the pixel, only the text differs. A gallery with 2× zoom and 1:1 strips
(`scripts/blitz-experiment/gallery-native.mts`, `magnify.mts`):
`reports/blitz-experiment/native/{generated,compat}/gallery.html`.

By eye on SF at 2×: vello and CoreText rasterisation are indistinguishable (the same
face, the same glyph ids, the same positions; weight through the axes works —
`font-weight:700` covers more pixels, test `coregraphics_backend_paints_text_and_emoji`).
So on macOS/iOS with Retina the "nativeness" of Blitz's text was never held back by the
rasteriser, but by the emoji fonts.

Emoji:

- ordinary colour ones (`🏠`, `🥞`, `🔥`) are drawn in both (on macOS vello
  reads the PNG strikes);
- **VS16 sequences (`⏱️`, `🍽️`) remain tofu in both Blitz backends,
  while litehtml through CoreText draws them.** This is not rasterisation but font
  selection in parley/fontique: the run gets a face without these glyphs, and CoreText
  faithfully draws `.notdef`. To be fixed in shaping (fallback to the emoji family for
  sequences); the backend has nothing to do with it;
- the iOS emoji font (`emjc` strikes, which skrifa does not decode): CoreText on
  macOS draws outline glyphs from the same `.ttc`, but not the `emjc` bitmaps
  (`🪐` — 0 px in both backends, test `emoji_is_painted_with_the_ios_font`
  stays ignored); this cannot be checked on a Mac. On iOS itself CoreText draws
  its own font: **verified on the iPhone 17 simulator (iOS 26.5)** —
  `SIMCTL_CHILD_TWB_BACKEND=cg xcrun simctl launch booted org.telegram.widgetsandbox.TwidgetDemo --blitz`,
  widget 01 (the mortgage calculator): `🏠` is drawn, the same launch without the
  variable (vello) — an empty space (`reports/blitz-experiment/native/ios-blitz-{cg,vello}.png`).
  This is the closed "emoji on iOS" gap from the table above.

### Conclusion

Yes, "litehtml with a wide profile" works out: layout and cascade from Blitz
(grid, shadows, transforms, incremental updates), pixels and fonts from the
platform, and the price is a primitive executor per platform, exactly
like the one already written for the litehtml display list (Apple ≈ 900 lines of
Rust here). The Android variant is the same `PaintScene`, but through a display list over
JNI to `Canvas`, like `LitehtmlRenderer.kt`; tdesktop — QPainter. This step does not touch the other
Blitz gaps: `<canvas>` is empty, controls are drawn, not native
(`custom-widget` is the next candidate), VS16 emoji are a question for parley.

## Display list on the Android `Canvas`: the same seam, a second platform (2026-10-10)

The same question for Android, but without raw FFI to the platform: the crate gained a
third `anyrender` backend, `apps/renderer-blitz/src/dl.rs`, a platform-
neutral **recorder**: the blitz-paint scene is written into a compact binary
display list (format in the file header: layers, clips, fills and strokes with paths,
solid/linear/radial/sweep/image brushes, glyph runs with font id, face,
size, variation axes and positions, box shadow), and fonts and images are
handed out by blob id through a registry. It is checked by unit tests on the host and
suits any executor — QPainter in tdesktop would be next.

The executor on Android is `apps/android/renderer-blitz/.../CanvasScene.kt`:
it parses the stream once per frame into a list of commands with ready-made `Path`/`Paint`/
`Matrix` and replays them in `onDraw` on the `View`'s own `Canvas`
(the hardware canvas, no Bitmap copy): `clipPath`/`saveLayer` with alpha and
`BlendMode`, `LinearGradient`/`RadialGradient`/`SweepGradient`/`BitmapShader`,
`DashPathEffect`, `BlurMaskFilter` for the shadow (σ → radius recomputed with Skia's
formula) and **`Canvas.drawGlyphs` (API 31+) with a `Font` assembled from the same bytes
parley shaped with** (`Font.Builder(ByteBuffer)` + `setTtcIndex` +
`setFontVariationSettings`): the buffer is a direct `ByteBuffer` over fontique
memory that Rust keeps alive for the whole process (`BlitzNative.fontBytes`),
images are `imagePixels` as premultiplied RGBA8 in a `Bitmap`. JNI:
`setBackend`, `displayList`, `fontBytes`, `imagePixels`. In the demo there is a fourth
item in the picker, **Blitz·Canvas** (`--es renderer blitz_canvas`,
`BlitzRenderer.canvasFactory`); the `blitz-backend` and `blitz-paint` rows
(parse ms, number of operations, KB) in the panel.

Result on the emulator (AVD twidget-35, API 35, 3×; timing numbers on the
emulator are indicative only as orders of magnitude):

- generated/01 (the mortgage calculator): display list of 28 operations, 4 KB,
  first-frame parse 10.7 ms (including font assembly), fractions of a ms afterwards;
  the same frame with vello on the emulator's CPU — 64 ms of 945×951 px rasterisation.
- By eye: Roboto text through `drawGlyphs`, `🏠`, `🥞`, `🔥` in the system Noto
  Color Emoji, gradients, rounded corners, control borders — as with vello.
  On Android vello draws emoji too (Noto, not the Apple format), so
  here the win of the native path is Skia's text rasterisation, not emoji.
- VS16 sequences (`⏱️`, `🍽️`) are tofu here too: face selection in
  parley, the backend has nothing to do with it (see the CoreGraphics section).

Executor gaps: a radial gradient is a single circle (Android has no
public two-point one), conic through `SweepGradient` with a rotation,
`Extend` on the image brush only `CLAMP`/`REPEAT`/`MIRROR` as in Skia, CSS
filters are ignored, gradient text takes the first stop, `<canvas>`
is still empty; before API 31 text is not drawn (no `drawGlyphs`), the module's minSdk
is 26. Screenshots: `reports/blitz-experiment/native/android-blitz-{canvas,vello}-{01,19}.png`.

## Prototype in the demos (2026-10-09, evening)

(2026-10-10: litehtml is embedded in the same demos as a third renderer behind the same seam —
`docs/litehtml-renderer.md`.)

The same engine is embedded as a swappable renderer in the native demos: `apps/renderer-blitz/`
at the repository root is one Rust crate (`renderer.rs`: validated
`RenderOp`s → blitz-dom, layout queries, events through `EventDriver`,
`net.rs`: `data:` only, `text.rs`: `measureText` through parley) with two
bindings — JNI (`android.rs`, the `apps/android/renderer-blitz` module, Kotlin
`BlitzRenderer.kt`) and C ABI (`ffi.rs` + `include/twidget_blitz.h`,
`apps/ios/DemoApp/Sources/BlitzRenderer.swift` for UIKit and AppKit). The sandbox and
validator do not change: `WidgetHost` got a `Renderer` seam (`Renderer.kt`,
`Renderer.swift`) and a factory, the Rust side sees only validated ops and
`data:` images, it has no script and no network. Enabled with the **Blitz** toggle in the
demo (`--ez blitz true` on Android, `--blitz` on iOS/macOS, persisted across
launches), and works in the runners too: `--autorun generated --blitz`.

On the iPhone 17 simulator (3×): the calculator from `corpus/compat` is laid out and
drawn in 2–3 ms per frame (1080 × 1077 px, CPU), touches reach the widget's
handlers and come back through the same JSON contract, `size` drives the bubble
height, **Dark** switches `prefers-color-scheme`. The gaps are the same as in the
table above, plus an empty `<canvas>` (the command stream is not yet put onto vello).
Build: a pre-build phase of the Xcode project calls `apps/renderer-blitz/build-apple.sh`
(rustup cargo, targets `aarch64-apple-ios[-sim]` / `aarch64-apple-darwin`,
static library ≈ 39 MB before linking, first build 1–2 min).

## Prototype in the C++ core `packages/desktop/` (2026-10-10)

The same crate is wired into the C++ core for tdesktop-class hosts:
`cmake -DTW_BLITZ=ON` calls rustup's `cargo` for the host triple and
links the static library through the C ABI (`include/twidget_blitz.h`, the
same one Swift uses). The core gained a `twidget/renderer.h` seam (`Renderer` +
`RendererFactory`, the twin of `Renderer.swift` / `Renderer.kt`) and `WidgetHost`
(`twidget/widget_host.h`: engine + validator + renderer, the fourth copy of
`WidgetHost.swift` / `.kt` / `widget-host.ts`); `twidget/blitz_renderer.h` is a
`Renderer` over the C ABI without a view: the owner takes the rasters (`paint()` or
`onPaint` from the renderer's own 60 Hz clock, which also delivers
`requestAnimationFrame`) and feeds pointers in CSS px (`touch()`, `click()`).
A library without Qt has no main thread — `WidgetHost` keeps its own
"host thread" instead; in Qt the UI thread replaces it one to one.

Harness: `twidget-run … --blitz [--png FILE | --png-dir DIR] [--click X,Y]
[--dpr N]`. On macOS arm64 (2×): `corpus/compat` 21/21 and `corpus/generated`
40/40 load and draw, 1–9 ms per frame at 800 px width; in
`--attacks --blitz` a load attempt from the Rust document counts as a leak —
89 attacks, 0 leaks, 0 attempts. Three tests in `twidget-tests` (26 in total):
layout answers come from the document (not the harness's 100×20), a click on a button
reaches the handler, the last raster remains after kill. The gaps are the same
as the crate's everywhere. Windows is not wired up (no link line).

## Prototype in the web demo (2026-10-10)

The same crate is built for `wasm32-unknown-unknown` (`pnpm build:blitz-web`:
cargo with the `wasm-release` profile, then `wasm-bindgen --target web` — it is needed
only because of `web-time` inside blitz-dom, the `twb_*` exports themselves remain
a plain C ABI, and the TS glue calls them directly through the module's memory). The browser does not
give wasm the system fonts, so this build embeds DejaVu Sans
(`apps/renderer-blitz/assets/`, 259 KB woff2, Bitstream Vera licence) for all
generic families: monospace and serif text comes out sans, emoji are empty.
The module is 8 MB (`opt-level = "s"`, LTO), the first widget on the page pays ~150 ms
for fetch + compilation + document, after that the instance is shared.

`packages/core/src/host/` gained a `renderer.ts` seam (`Renderer` + `RendererFactory`,
the twin of `Renderer.swift` / `.kt` / `packages/desktop/.../renderer.h`), the former iframe
moved out into `iframe-renderer.ts`, `widget-host.ts` takes the factory from the options;
`blitz-renderer.ts` is a `Renderer` on a `<canvas>`: validated ops go to
wasm as JSON, pointers from the canvas in CSS px go to `twb_touch`, the raster
(premultiplied RGBA from vello) is taken on `requestAnimationFrame`, and the same
clock delivers `requestAnimationFrame` frames to the widget. The **Blitz** toggle /
`?blitz=1` in the dev app; without a built module there is a `renderer` diagnostic.
The module lives in `apps/renderer-blitz/pkg-web/` (gitignored) and is served by a Vite plugin
under `/blitz/` outside the module graph, like `/libs/`; it does not go into `vite build` —
it is a development tool.

Checks: `apps/web/tests/unit/blitz-wasm.test.ts` (6 tests on Node through the same ABI:
font, layout and query answers from the geometry, dpr, tap → `click`,
a `data:` image without a single load attempt, frozen) and
`TW_RENDERER=blitz pnpm test:generated` (the sandbox side on Blitz, report in
`docs/compat-report-generated-blitz.md`, the pixel threshold there is informational):
40/40 load without errors and draw on the canvas; the gallery is
`reports/generated-blitz/`, the gaps are the same as in the table above, plus empty emoji.

## Reproduction

```bash
# Blitz
git clone --depth 1 https://github.com/DioxusLabs/blitz.git /tmp/blitz && git -C /tmp/blitz checkout 971f1a3
cp scripts/blitz-experiment/twidget_render.rs /tmp/blitz/examples/
(cd /tmp/blitz && cargo build --release --example twidget_render)
corepack pnpm dev   # reference mode must be live
corepack pnpm tsx scripts/blitz-experiment/dump-dom.mts reports/blitz-experiment/dump
TW_SHARED_FONTS=1 corepack pnpm tsx scripts/blitz-experiment/compare.mts reports/blitz-experiment/dump /tmp/blitz/target/release/examples/twidget_render reports/blitz-experiment
# litehtml (brew install pango; build: see the header of twidget_litehtml.cpp)
git clone --depth 1 https://github.com/litehtml/litehtml.git /tmp/litehtml && cmake -S /tmp/litehtml -B /tmp/litehtml/build -G Ninja -DCMAKE_BUILD_TYPE=Release -DLITEHTML_BUILD_TESTING=OFF -DLITEHTML_ENABLE_LINT=OFF && cmake --build /tmp/litehtml/build
mkdir -p reports/blitz-experiment/litehtml && for f in reports/blitz-experiment/dump/*.html.html; do n=$(basename $f .html); printf "%s " $n; FONTCONFIG_FILE=scripts/blitz-experiment/fonts.conf ./twidget_litehtml $f 404 reports/blitz-experiment/litehtml/$n.litehtml.png; done > reports/blitz-experiment/litehtml/timings.txt
corepack pnpm tsx scripts/blitz-experiment/gallery.mts reports/blitz-experiment/dump reports/blitz-experiment reports/blitz-experiment/litehtml reports/blitz-experiment/gallery.html
```
