# Widget profile draft: what the validator cuts when the platform renderer is not WebView

> Historical draft (October 2026). The current profile is `docs/profile-v1.md`,
> generated from `packages/core/profile.json`; this document is kept as the record
> of the measurements that led to it.

Date: 2026-10-10. Context: the discussion after the three-way comparison
(`docs/litehtml-renderer.md`, `docs/blitz-experiment.md`). The idea: every
platform gets its most advantageous renderer (an iframe on the web), and so that
a widget looks and behaves the same everywhere, the validator strips everything
the weakest of the chosen renderers cannot do. This document answers the question
"what exactly, and how often, would have to be stripped" from data, not from
memory.

## Method

Two scripts, both on the shared pipeline (QuickJS sandbox → validator → renderer):

1. `scripts/profile/probes.ts` — 53 mini-widgets, one per feature; each one,
   through `packages/desktop/build/twidget-run --blitz | --litehtml --png` (the C++ core with
   both native renderers, macOS), puts a known colour at a known pixel if the
   feature is supported. In parallel the same HTML goes through the validator
   alone under Node, to see whether the feature survives to the renderer at all
   (that is what the product iframe / WebView gets). The result is
   `reports/profile/probes.{json,md}` and the table below.
2. `scripts/profile/corpus.ts` — all widgets of the four corpora
   (`compat` 21, `generated` 40, `htmlbubbles-ported` 116, `attacks` 89)
   are run through the sandbox and the validator; from the validated ops it
   extracts tags, controls, canvas, inline SVG, CSS properties, functions,
   at-rules, selectors (css-tree over the sanitised stylesheets and inline styles)
   and synchronous queries. Each feature is mapped to a renderer class by the
   table from step 1 and by what the experiment documents have already established.
   The result is `reports/profile/corpus.{json,md}` and the tables below.

Not measured: anything interactive (scrolling inside the bubble, transitions and
animations over time, focus and input in controls) — for those the table carries
the verdict from the documents, not from a probe.

## Support matrix (probes)

The "Validator keeps" column says whether the feature survives the validator and
reaches the renderer, i.e. what the product iframe / WebView gets. `light-dark()`
is not lost by the validator but resolved to the current theme's colour (as is
`prefers-color-scheme`), so ❌ there means "already resolved". CSS nesting the
validator strips (`.a{.b{…}}` becomes `.a{}`) — a validator hole, not the engines'.
`:has()` and `@supports` the validator passes through.

| Feature | Validator keeps | Blitz | litehtml |
|---|---|---|---|
| display: grid (two columns) (`grid`) | ✅ | ✅ | ❌ |
| flex + gap (`flex-gap`) | ✅ | ✅ | ❌ |
| box-shadow (`box-shadow`) | ✅ | ✅ | ❌ |
| opacity (`opacity`) | ✅ | ✅ | ❌ |
| transform: translateX (`transform-2d`) | ✅ | ✅ | ❌ |
| transform: rotateY(180deg) + backface-visibility: hidden (card flip) (`transform-3d`) | ✅ | ❌ | ❌ |
| overflow: hidden clips (`overflow-clip`) | ✅ | ✅ | ✅ |
| overflow: auto clips (scrolling itself is interactive, not probed) (`overflow-auto-clip`) | ✅ | ✅ | ✅ |
| border-radius + overflow: hidden rounds children (`border-radius-clip`) | ✅ | ✅ | ✅ |
| inline <svg> with presentation attributes (`svg-inline`) | ✅ | ✅ | ❌ |
| inline <svg> styled from CSS (fill in a rule) (`svg-css`) | ✅ | ❌ | ❌ |
| <img src="data:image/png"> (`img-png`) | ✅ | ✅ | ✅ |
| <img src="data:image/svg+xml"> (`img-svg`) | ✅ | ✅ | ❌ |
| background-image: url(data:png) (`bg-image`) | ✅ | ✅ | ✅ |
| <canvas> 2D (fillRect through the command stream) (`canvas`) | ✅ | ❌ | ❌ |
| <input> draws its value (`input-value`) | ✅ | ✅ | ❌ |
| <select> shows the selected option (`select`) | ✅ | ❌ | ❌ |
| <input type=range> draws a track/thumb (`range`) | ✅ | ✅ | ❌ |
| <input type=checkbox checked> draws a mark (`checkbox`) | ✅ | ✅ | ✅ |
| <button> has a visible box (`button`) | ✅ | ✅ | ✅ |
| aspect-ratio (`aspect-ratio`) | ✅ | ✅ | ❌ |
| clamp() in width (`clamp`) | ✅ | ✅ | ❌ |
| calc() in width (`calc`) | ✅ | ✅ | ❌ |
| var(--x) (`var`) | ✅ | ✅ | ✅ |
| vw unit (viewport = bubble width) (`vw`) | ✅ | ✅ | ✅ |
| light-dark() colour (light theme) (`light-dark`) | ❌ | ✅ | ✅ |
| color-mix() (`color-mix`) | ✅ | ✅ | ❌ |
| filter: grayscale(1) (`filter`) | ✅ | ❌ | ❌ |
| ::before with content (`pseudo-before`) | ✅ | ✅ | ✅ |
| :nth-child(2) (`nth-child`) | ✅ | ✅ | ✅ |
| :is() (`is-selector`) | ✅ | ✅ | ✅ |
| :has() (`has-selector`) | ✅ | ❌ | ❌ |
| CSS nesting (.a { .b { } }) (`nesting`) | ✅ | ❌ | ❌ |
| @supports (display: grid) (`supports`) | ✅ | ✅ | ❌ |
| @media (min-width: 200px) (bubble is 300 wide) (`media-width`) | ✅ | ✅ | ✅ |
| @keyframes + animation 0s forwards (end state applied) (`keyframes-forwards`) | ✅ | ❌ | ❌ |
| position: absolute inside relative (`position-absolute`) | ✅ | ✅ | ✅ |
| z-index stacking (earlier box on top) (`z-index`) | ✅ | ✅ | ✅ |
| visibility: hidden (`visibility-hidden`) | ✅ | ✅ | ✅ |
| margin-inline-start (logical property) (`logical-margin`) | ✅ | ✅ | ❌ |
| clip-path: inset() (`clip-path`) | ✅ | ✅ | ❌ |
| linear-gradient (`linear-gradient`) | ✅ | ✅ | ✅ |
| radial-gradient (`radial-gradient`) | ✅ | ✅ | ✅ |
| conic-gradient (`conic-gradient`) | ✅ | ✅ | ✅ |
| text-shadow (`text-shadow`) | ✅ | ❌ | ❌ |
| colour emoji glyph (host font; measured on macOS) (`emoji`) | ✅ | ✅ | ✅ |
| <details open> shows its content (`details-open`) | ✅ | ✅ | ✅ |
| <details> (closed) hides its content (`details-closed`) | ✅ | ✅ | ✅ |
| <table>: cells sized by CSS width/height (litehtml honours the width, ignores the height) (`table`) | ✅ | ✅ | ❌ |
| <table> as the first block in <body> (same, without a preceding paragraph) (`table-first`) | ✅ | ✅ | ❌ |
| position: sticky (lays out in flow; stickiness is interactive, not probed) (`sticky`) | ✅ | ✅ | ✅ |
| white-space: pre (line break kept → second line) (`pre-wrap`) | ✅ | ✅ | ❌ |
| overflow-wrap: anywhere breaks a long word (`word-wrap`) | ✅ | ✅ | ❌ |

## Features by corpus (share of widgets using the feature at least once)

| Feature | compat (21) | generated (40) | htmlbubbles-ported (116) | attacks (89) | Blitz | litehtml |
|---|---|---|---|---|---|---|
| `border-radius` | 76% (16) | 88% (35) | 84% (98) | 1% (1) | ✅ | ✅ |
| `:root` | 5% (1) | 50% (20) | 100% (116) | 1% (1) | ✅ | ✅ |
| `var()` | 5% (1) | 50% (20) | 99% (115) | 2% (2) | ✅ | ✅ |
| `custom-props` | 5% (1) | 50% (20) | 99% (115) | 1% (1) | ✅ | ✅ |
| `color-scheme` | 5% (1) | 50% (20) | 100% (116) | · | ✅ | ✅ |
| `ux-props` | 62% (13) | 78% (31) | 59% (69) | · | ✅ | ✅ |
| `display:flex` | 33% (7) | 60% (24) | 66% (76) | · | ✅ | ✅ |
| `:hover` | 10% (2) | 18% (7) | 66% (77) | · | ✅ | ✅ |
| `form:button` | 57% (12) | 63% (25) | 31% (36) | 9% (8) | ✅ | ✅ |
| `opacity` | 14% (3) | 23% (9) | 52% (60) | · | ✅ | ❌ |
| `animation` | 5% (1) | 5% (2) | 53% (62) | 1% (1) | ❌ | ❌ |
| `@keyframes` | 5% (1) | 5% (2) | 53% (62) | 1% (1) | ❌ | ❌ |
| `[attr-selector]` | 5% (1) | 20% (8) | 48% (56) | · | ✅ | ✅ |
| `transform:2d` | 14% (3) | 15% (6) | 47% (55) | · | ✅ | ❌ |
| `transition` | 24% (5) | 35% (14) | 37% (43) | · | ◐ | ◐ |
| `position:absolute` | 10% (2) | 25% (10) | 36% (42) | · | ✅ | ✅ |
| `overflow:hidden` | 19% (4) | 20% (8) | 35% (41) | · | ✅ | ✅ |
| `svg` | 14% (3) | 8% (3) | 31% (36) | 11% (10) | ◐ | ❌ |
| `!important` | · | · | 38% (44) | · | ✅ | ✅ |
| `:active` | 5% (1) | 8% (3) | 32% (37) | · | ✅ | ✅ |
| `gap` | 33% (7) | 73% (29) | · | · | ✅ | ❌ |
| `linear-gradient` | 10% (2) | 10% (4) | 25% (29) | · | ✅ | ✅ |
| `:last-child` | · | 8% (3) | 25% (29) | · | ✅ | ✅ |
| `@media` | 5% (1) | 50% (20) | 8% (9) | 1% (1) | ✅ | ✅ |
| `:first-child` | · | 5% (2) | 23% (27) | · | ✅ | ✅ |
| `code` | 10% (2) | · | 22% (26) | · | ✅ | ✅ |
| `form:input` | 24% (5) | 40% (16) | · | 4% (4) | ◐ | ◐ |
| `display:grid` | 14% (3) | 43% (17) | · | 1% (1) | ✅ | ❌ |
| `table` | 5% (1) | 10% (4) | 13% (15) | 1% (1) | ✅ | ◐ |
| `grid-*props` | 10% (2) | 43% (17) | · | · | ✅ | ❌ |
| `box-shadow` | 10% (2) | 30% (12) | 2% (2) | · | ✅ | ❌ |
| `radial-gradient` | · | 3% (1) | 11% (13) | · | ✅ | ✅ |
| `canvas` | 19% (4) | 13% (5) | 2% (2) | · | ❌ | ❌ |
| `input:number` | 5% (1) | 25% (10) | · | · | ✅ | ✅ |
| `canvas:cmds` | 14% (3) | 13% (5) | 2% (2) | · | ❌ | ❌ |
| `white-space:pre*` | · | · | 9% (10) | · | ✅ | ❌ |
| `font-features` | 19% (4) | 13% (5) | · | · | ✅ | ✅ |
| `input:text` | 14% (3) | 10% (4) | · | 2% (2) | ✅ | ✅ |
| `calc()` | · | 5% (2) | 6% (7) | · | ✅ | ❌ |
| `query:cs` | 10% (2) | 13% (5) | · | · | ✅ | ✅ |
| `query:rect` | 10% (2) | 13% (5) | · | · | ✅ | ✅ |
| `query:measureText` | 10% (2) | 13% (5) | · | · | ✅ | ✅ |
| `letter-spacing` | 5% (1) | 15% (6) | · | · | ✅ | ✅ |
| `form:select` | 5% (1) | 15% (6) | · | · | ◐ | ❌ |
| `accent-color` | · | 18% (7) | · | · | ✅ | ✅ |
| `input:checkbox` | 10% (2) | 10% (4) | · | · | ✅ | ✅ |
| `logical-props` | · | 15% (6) | · | · | ✅ | ❌ |
| `input:range` | 5% (1) | 10% (4) | · | · | ✅ | ✅ |
| `aspect-ratio` | · | 13% (5) | · | · | ✅ | ❌ |
| `outline` | · | 10% (4) | · | · | ✅ | ✅ |
| `filter` | 5% (1) | 3% (1) | 1% (1) | · | ❌ | ❌ |
| `query:svg` | 14% (3) | · | · | · | ✅ | ✅ |
| `transform:3d` | · | 5% (2) | · | · | ❌ | ❌ |
| `text-shadow` | · | 3% (1) | · | · | ❌ | ❌ |
| `url(data:svg)` | · | · | 1% (1) | · | ✅ | ❌ |
| `@supports` | · | · | · | 1% (1) | ✅ | ❌ |

## How many widgets each renderer class affects (at least one feature the class does not draw fully)

### blitz

| Corpus | Widgets | Affected (❌) | Affected (❌ or ◐) | Top features |
|---|---|---|---|---|
| compat | 21 | 6 (29%) | 16 (76%) | transition 5, form:input 5, canvas 4, canvas:cmds 3, svg 3, filter 1 |
| generated | 40 | 11 (28%) | 36 (90%) | form:input 16, transition 14, form:select 6, canvas 5, canvas:cmds 5, svg 3 |
| htmlbubbles-ported | 116 | 63 (54%) | 70 (60%) | @keyframes 62, animation 62, transition 43, svg 36, canvas 2, canvas:cmds 2 |
| attacks | 89 | 1 (1%) | 15 (17%) | svg 10, form:input 4, @keyframes 1, animation 1 |

### litehtml

| Corpus | Widgets | Affected (❌) | Affected (❌ or ◐) | Top features |
|---|---|---|---|---|
| compat | 21 | 17 (81%) | 19 (90%) | gap 7, transition 5, form:input 5, canvas 4, display:grid 3, canvas:cmds 3 |
| generated | 40 | 36 (90%) | 40 (100%) | gap 29, display:grid 17, grid-*props 17, form:input 16, transition 14, box-shadow 12 |
| htmlbubbles-ported | 116 | 73 (63%) | 76 (66%) | @keyframes 62, animation 62, opacity 60, transform:2d 55, transition 43, svg 36 |
| attacks | 89 | 11 (12%) | 16 (18%) | svg 10, form:input 4, @supports 1, display:grid 1, @keyframes 1, animation 1 |

## Synchronous queries and DOM calls

| Key | compat | generated | htmlbubbles-ported | attacks |
|---|---|---|---|---|
| `query:box` | 5% (1) | · | 7% (8) | · |
| `call:observeResize` | 10% (2) | 13% (5) | · | · |
| `query:cs` | 10% (2) | 13% (5) | · | · |
| `query:rect` | 10% (2) | 13% (5) | · | · |
| `query:measureText` | 10% (2) | 13% (5) | · | · |
| `prop:value` | · | 10% (4) | · | · |
| `query:svg` | 14% (3) | · | · | · |
| `query:matchMedia` | 5% (1) | 5% (2) | · | · |
| `prop:selectedIndex` | · | 5% (2) | · | · |

## What this means

Reading the tables: ❌ — the feature is not drawn (or drawn wrong), ◐ — drawn
partially (the end state without motion, a box without its value, SVG without
CSS). "Affected" — the widget has at least one such feature; it is an upper bound,
the widget may remain usable (losing only a shadow, say).

### The validator on its own (what the product iframe sees)

- CSS nesting (`.a{.b{…}}`) the validator strips: css-tree 3 regenerates
  `.a{}`. This is a validator hole on every platform; that is why nesting does
  not show up in the corpus statistics at all (the analysis runs over validated ops).
- `light-dark()` is resolved to the theme colour, `prefers-color-scheme` to a
  `min-width` query; the engines never need them. `:has()`, `@supports`,
  `@container`, `@layer`, `@property` pass straight through.
- The other 50 probes the validator leaves alone: the product iframe / WebView
  draws everything in the table except nesting.

### The Blitz class (mobile + desktop on Blitz, web on iframe)

There are five hard blockers, and they are concrete:

| Blocker | Where in the corpora | What to do |
|---|---|---|
| `<canvas>` | compat 4 (Chart.js charts), generated 5 (charts 03, 08, 16) | the command stream is our own (`CanvasCmd`); it has to be laid onto vello — work in the prototype, not an engine hole |
| CSS animations (`@keyframes`) | htmlbubbles 62 of 116 (spinners, pulses — the Telegram demo style), generated 2 | Blitz has no animation driver: either the profile forbids them, or we accept a "static final frame" and lose parity in motion |
| 3D transforms | generated 2 (card flip) | forbid in the profile: Blitz is 2D only |
| `filter` / `text-shadow` / `select` | 1–6 widgets each | forbid `filter`/`text-shadow`; draw `<select>` with a control of our own or replace it with a radio group in the validator |
| CSS on inline SVG | compat 3, htmlbubbles 36 (icons with `fill` from the stylesheet) | either the validator moves `fill`/`stroke` from rules into presentation attributes (hard: the cascade), or the hole gets fixed in Blitz |

Without the animation family, Blitz hard-affects 3 widgets of 116 htmlbubbles
(canvas 2, filter 1) and 11 of 40 generated, 6 of them canvas. With canvas done
and 3D/filter/text-shadow forbidden, what remains is animations and `select`.
The soft losses (transitions without motion, inputs without the system look)
touch 76–90 % of compat/generated, but that is cosmetics on the same layout.

### The litehtml class

The litehtml profile is a different class of widgets: 0 of 40 generated,
2 of 21 compat and 40 of 116 htmlbubbles stay clean (simple cards with text,
flex, borders and gradients). The main losses by frequency: `gap` (73 %
generated), grid (43 %), `box-shadow` (30 %), `opacity` (52 % htmlbubbles),
any `transform` (47 % htmlbubbles), inline SVG (31 % htmlbubbles), then
`calc()`/`clamp()`, `aspect-ratio`, logical properties, `clip-path`,
`white-space: pre`, long-word wrapping, table cell heights, input values.
This is not "trimming the profile", it is switching off what LLM widgets are
made of. litehtml only makes sense if the target class is the "rich static
card" of the HTMLBubbles kind, and then the profile has to be written from
scratch from its capabilities, not subtracted from WebView.

### Profile draft (if the Blitz class is chosen)

The profile is one module in four copies with a shared fixture (like `cpu-budget`);
the validator strips everything outside it and reports `profile-dropped`, and the
same text goes into the generator prompt.

Forbidden (the validator strips the property/tag, the widget keeps working):

- `transform` with 3D functions (`rotateX/Y/Z`, `rotate3d`, `translateZ/3d`,
  `scaleZ/3d`, `matrix3d`, `perspective`), `perspective`,
  `backface-visibility`, `transform-style`.
- `filter`, `backdrop-filter`, `text-shadow`, `mix-blend-mode`, `mask*`,
  `background-clip: text`, `writing-mode`, `-webkit-line-clamp`.
- `:has()` in selectors (the whole rule is stripped), CSS nesting (already).
- `<select>`: replaced by the validator with `<span data-tg-tag="select">`
  holding the text of the selected `<option>` (as for unknown tags today) until
  there is a control of our own; `<input type=color|date|time|file>` — the same.
- `overflow: auto|scroll` → `hidden` inside the bubble (scrolling the bubble is
  the chat's business; the native renderers have none); `position: sticky` → `relative`.

A decision is needed (parity versus expressiveness):

- `transition`, `animation`, `@keyframes`: keep them and accept that the
  native renderers show the final frame (53 % of the htmlbubbles demos are
  animated), or forbid them and lose them on the web too.
- `<canvas>`: keep it provided the command stream gets laid onto vello
  (otherwise 13–19 % of compat/generated lose their charts).
- CSS on inline SVG: keep it if the validator learns to "bake"
  `fill`/`stroke`/`stroke-width` from rules into attributes for simple
  selectors (by class and tag), otherwise the icons are black.

Stays unrestricted (probes green on Blitz): grid, flex and `gap`,
`var()`/`calc()`/`clamp()`, `aspect-ratio`, logical properties, all
gradients, `box-shadow`, `opacity`, 2D transforms, `border-radius` with
clipping, `overflow: hidden`, `clip-path`, `position: absolute/fixed`,
`z-index`, `::before/::after`, `:nth-child`, `:is()`, `@media`, `@supports`,
`<details>`, tables, `<img>` and `background-image` with `data:` PNG/SVG,
`<input>` values, the `range` slider, checkboxes, emoji (on macOS; on iOS
Blitz emoji are blank — a separate, known font hole).

### What this draft does not cover

- Behaviour: focus and input in controls, scrolling inside the bubble, the
  keyboard — absent in the native renderers, and the validator cannot even that
  out. A second list is needed, of "capabilities", not properties.
- Text: font metrics differ across platforms even within the profile; the
  acceptance criterion is structural, not pixel-level.
- The probes are static and on macOS; iOS/Android specifics (Blitz emoji on
  iOS, Android fonts) come from the documents, not from this run.
- The corpora are not the product: generated is 40 LLM widgets from two
  prompts, htmlbubbles 116 Telegram demos, compat 21 hand-written. The
  percentages speak about the widget class, not about the share of real traffic.
