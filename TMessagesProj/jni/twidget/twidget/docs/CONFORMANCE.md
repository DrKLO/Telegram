# Conformance with widget-sandbox v0.2.3

widget-sandbox v0.1.0 froze widget profile 1, v0.1.1 settles its defaults (the renderer's UA layer, text
tokens, the bubble as the canvas, opt-in selection), v0.1.2 decides who gets a press (one gesture policy
on every client), v0.1.3 gives vertical drags to the chat and the resize anchor to the widget, and v0.2
gives the widget the bot's state and requests to the bot. Each ships a conformance set that every client
runs on its renderer (`packages/conformance/CLIENTS.md` there). This page tracks TwidgetCore against it:
what the contract asks of the renderer, and what the set reports, item by item.

Measured with `tools/conformance` (Windows, the Direct2D painter on WARP, 390 CSS px, DPR 2) and the
set's `tools/report.mjs`. The verdicts that come from the widgets' own checks (layout, computed style,
events) hold for TwidgetCore on every platform; the pixel stages judge the Direct2D painter only, and
each platform's painter needs its own run.

## Where it stands

| Date | Commit | In profile: pass / partly / fail / untested | Out of profile: neutralised / not yet / fail |
|---|---|---|---|
| 2026-10-18 | f649455 (contract) | 90 / 49 / 24 / 10 | 69 / 3 / 8 |
| 2026-10-18 | 3420fc5 (computed style) | 111 / 38 / 14 / 10 | 73 / 2 / 5 |
| 2026-10-18 | 099f3cb (inline boxes) | 120 / 32 / 11 / 10 | 73 / 2 / 5 |
| 2026-10-18 | image load and error (E1) | 121 / 32 / 10 / 10 | 73 / 2 / 5 |
| 2026-10-19 | the small fixes (Q2, E5, E6, L5, L10, L12, O1–O3, sans-serif) | 129 / 26 / 8 / 10 | 79 / 1 / 0 |
| 2026-10-19 | events and animations (E2–E4, A1, A2, L7) | 135 / 21 / 7 / 10 | 79 / 1 / 0 |
| 2026-10-19 | layout and style in the fork (L1–L13, O4) | 148 / 9 / 6 / 10 | 80 / 0 / 0 |
| 2026-10-19 | SVG queries and painting (Q3, P1–P9) | 158 / 5 / 0 / 10 | 80 / 0 / 0 |
| 2026-10-20 | the Swift renderer's text, border, canvas and image fixes | 159 / 4 / 0 / 10 | 80 / 0 / 0 |
| 2026-10-20 | widget-sandbox main 7654e69 (live collections, Web Animations state before a query); Intl on the system's ICU (`IcuIntl`) | 162 / 1 / 0 / 10 | 80 / 0 / 0 |
| 2026-10-21 | v0.1.1 (selection moves into the profile): the profile's UA layer, text tokens, `user-select`, relative offsets on inline elements | 163 / 1 / 0 / 10 | 79 / 0 / 0 |
| 2026-10-23 | v0.1.2 (`dblclick` and `contextmenu` move into the profile): 163 / 2 / 2 / 9 before; the gesture policy in the renderer, the runner's presses through a bubble | 166 / 1 / 0 / 9 | 78 / 0 / 0 |
| 2026-10-24 | v0.1.3 (vertical drags are the chat's, `setResizeAnchor`, transforms in % and `calc()`): 165 / 3 / 0 / 10 before; `calc()` of % and px inside a transform | 167 / 1 / 0 / 10 | 78 / 0 / 0 |
| 2026-10-26 | v0.2.1 (the bot's state, requests, the message context; nothing new for the renderer): 170 / 4 / 0 / 14 before; the runner creates the host as the test's kind of message and answers as the test bot | 173 / 1 / 0 / 14 | 78 / 0 / 0 |
| 2026-10-26 | v0.2.2 (haptics need a touch: host glue only) | 173 / 1 / 0 / 14 | 78 / 0 / 0 |
| 2026-10-27 | v0.2.3 (the shim converts canvas arguments and loads images a script creates; pictures follow profile 1) | 173 / 1 / 0 / 14 | 78 / 0 / 0 |
| 2026-11-02 | v0.6.0 (the renderer counts heavy boxes, a long flush comes in parts, 4000 canvas commands): 175 / 6 / 1 / 16 at v0.4.0; the paint count, the stopped paint, and the runner waits for the stop of a `stops` test | 179 / 6 / 0 / 16 | 77 / 0 / 0 |

What is left partly passing is not TwidgetCore's to fix: see the last section.

The 14 untested features are the host's (limits, restart, storage, message and state edits, forwarded
copies, previews, the network marker, size hints, the resize anchor): the automatic set does not reach them,
and each client checks them in its own host. The gesture claim is tested since v0.1.2: the runner's presses
go through a bubble that follows the shared policy, as CLIENTS.md asks. Since v0.2.1 a test with a
`message` runs as that kind of message (the server's flags, the bot's state and a viewer where requests are
allowed), and the runner answers its requests from the manifest's `bot` table, as the test bot would.

For comparison, the old litehtml renderer copy in widget-sandbox (web build, upstream litehtml) is at
66 / 41 / 52 in widget-sandbox's own results.

## Swift renderer on macOS

`ConformanceRunnerTests.testAutomaticSet` runs the same automatic widgets through Swift's
`WidgetHost` and `LiteHTMLWidgetRenderer`, with native mouse input and renderer snapshots.
The runner configures each test's first `message` spec with its flags, chat state, shared state and
viewer, and answers requests from the manifest's `bot` table. This lets the context widgets exercise
originals, copies and previews with their actual permissions. Tests without a `message` use the
private-chat context, as `CLIENTS.md` requires. Unknown message kinds stop the run.

The macOS renderer corrections checked on 28 October 2026 restore existing behavior:

- Include `<html>` in the `touch-action` ancestor walk, as the shared gesture policy requires.
- Accumulate `movementX` and `movementY` across coalesced moves while retaining the latest position.
- Deliver pending moves in insertion order, including `pointermove` before its compatibility `mousemove`.
- Preserve native click counts and give the shared arbiter's `dblclick` a count of at least two, without changing gesture thresholds.

The ancestor walk and move accumulation/order match the existing portable C++ renderer. These are
native renderer corrections; the profile, protocol, host-glue policies and runtime quotas are unchanged.

Before the rebase, recorded on macOS 27.0.1: all 25 affected native tests pass. All 53 automatic runs complete with 609
passing checks. One hover fixture check skips because its capability query returns false; native
hover events and rendering pass separate interaction tests. One touch-event check skips because
the native runner sends mouse input. The upstream report has 174 in-profile
features passing, zero partial/failing features and 14 host features untested. TwidgetLab's 12 gates
verify 1,146 fresh-render frames, 289 fresh-parse patches and 108 replay frames (97 patched), with zero
differences. These scoped results do not replace the recorded full-suite timing diagnostics or the
host, manual client and bot checks.

After rebasing the nine local commits onto `538e420` on 28 October 2026, the production and test patches are equivalent;
the incoming change only removes duplicate text conversion in the portable canvas. The pinned
widget-sandbox v0.2.3 shim already converts those arguments. The portable Release build passes all
329 assertions. Rebuilt TwidgetLab passes all 12 gates: 1,142 fresh-render frames, 285 fresh-parse
patches and 108 replay frames (98 patched), with zero differences.

The fresh native checks are not fully green: 24 of 25 cases pass; the packing in-flight case records
13 px of checkbox ink against its unchanged 18 px requirement. An isolated run passes the pixel
assertions, but one sample arrives 30.4 ms after its target, exceeding the unchanged 25 ms allowance. Its
post-click sampling assertions are unchanged by these commits. The fresh automatic set completes
53 runs without runtime errors, but judges 173 in-profile features passing and one partial:
`tw.theme-params` observes the new palette with an old CSS background inside `themeChanged`.
The theme paths are unchanged by the local patches and rebase; the cause of that ordering failure
remains open. The earlier passing records do not establish a clean result for this latest run.

From this repository:

```sh
TW_CONFORMANCE=1 TW_CONFORMANCE_OUT=/tmp/twidget-conformance \
  swift test --scratch-path /tmp/twidget-tests --disable-sandbox \
  --enable-xctest --disable-swift-testing \
  -Xlinker -framework -Xlinker QuartzCore \
  --filter ConformanceRunnerTests.testAutomaticSet
node ../widget-sandbox/packages/conformance/tools/report.mjs \
  --out /tmp/twidget-conformance/report --fail-on in \
  /tmp/twidget-conformance/macos-twidget-litehtml.run.json
```

Always judge the run file: XCTest records the widget checks without turning their failures into
XCTest assertions. `TW_CONFORMANCE_ONLY` accepts comma-separated manifest IDs for a focused run;
leave it unset for the whole set. `TW_CONFORMANCE_FEATURES` selects the host's feature set, with
`webAnimations` as the default. The test needs a logged-in macOS graphical session. Telegram's
message-list integration and live bot delivery need their own checks.

## Done

| What the contract asks | Where |
|---|---|
| Real input marked `trusted: true`, or the hosts count no user gesture | `Renderer::event_message` |
| Only profile 1's events: no keys, focus, input, touch, wheel or scroll | `PROFILE_EVENTS`, `listened` |
| The gesture policy (v0.1.2): `dblclick` after every double tap's second click; `contextmenu` for a hold or a secondary-button press on `touch-action: none`, and no click after it; `arbitrate` plays the bubble | `Renderer::touch`, `settle_gesture`, `bubble_takes` (widget-sandbox's `GestureArbiter`) |
| The `theme` message: `--tg-theme-*`, `--tg-color-scheme`, `--tg-button-*` and `--tg-text-*` on the root, important, values checked, the palette and text tokens always complete | `css::theme_variables` |
| The profile's UA layer (`tables.uaStylesheet`) in the master CSS, the theme's values substituted and `:where()` unwrapped | `Renderer::apply_theme`, `tw_ua_stylesheet.h` (CMake) |
| `revert` goes past that layer to litehtml's own sheet, as it goes past author layers to Chromium's | the fork's `css_selector::m_layered` |
| `user-select` and `-webkit-user-select` computed (`none`, `text`, `auto`) | the fork's `css_properties::get_user_select` |
| Text selection (`docs/sdk-v1.md` §5.10): a mouse drag that starts on `user-select: text`, unless the widget prevents the press, selects it and claims the drag; the host copies `selected_text()` and calls `clear_selection()` on a press of its own. Touch selection starts from the client's message menu and is the host's: `selectable_text_json()` lists the runs, each with the transform it shows under | `Renderer::touch`, `text_runs`, `paint_selection`, `selectable_text_json` |
| `position: relative` on an inline element moves its text and its line boxes | the fork's `line_box::finish` |
| `gesture` claims with `x` and `hold` (never `y` since v0.1.3: vertical drags are the chat's), from the target and its ancestors up to `<html>` | `Renderer::gesture_claim` |
| The shared height bounds, 50 to 600 CSS px | `MIN_HEIGHT`, `max_height_` |
| `getComputedStyle` answered for every property the fork computes, as Chromium writes it | `computed_style.cpp` |
| An inline element's rect is its line boxes, with a rect per line | `inline_boxes_of` |
| `<img>` sends `load` with its natural size, or `error` | `Renderer::load_images` |
| `scrollWidth` and `scrollHeight` reach the overflowing content | `content_extent` |
| `pointer-events: none` passes a hit to what is below | `Container::hittable` |
| Pointer and mouse events carry `movementX` and `movementY` | `Renderer::emit_event`, `dispatch` |
| `initial`, `unset` and `revert` | the fork's `css_wide_keyword`, `html_tag::ua_property` |
| Every CSS colour name, `rebeccapurple` included | the fork's `web_color.cpp` |
| A refused image is widget-sandbox's 48 px placeholder | `Container::draw_placeholder` |
| A canvas gradient with no length (the validator's conic gradient or pattern) paints nothing | `Canvas::paint_of` |
| On Windows, `sans-serif` and Helvetica are Arial, as in browsers | `D2DBackend::resolve_family` |
| `getComputedStyle` reads running, paused and seeked animations | `Animator::peek` in the `cs` query |
| `animationstart`, `animationend` and `transitionend` go out before the widget's `listen` | `ALWAYS_FORWARD` |
| ResizeObserver: `resize` on observe and on every change | `Renderer::report_resizes` |
| Tapping a `<summary>` opens and closes its `<details>` and sends `toggle`; a closed one shows only its summary, with a disclosure marker | `Renderer::toggle_details`, `UA_EXTRA`, the fork's `list_style_type_disclosure_*` |
| `calc()`, `min()`, `max()`, `clamp()` mixing percentages with lengths | the fork's `css_units_calc`, `calc_evaluate` |
| `width: fit-content`, `min-content`, `max-content` on blocks | the fork's `render_block` |
| Empty auto table cells share the spare width | the fork's `table_grid::calc_table_width` |
| `word-break` (`break-all`, `break-word`) | the fork's `css_properties::get_wrapping` |
| `open-quote` / `close-quote` from `quotes`, and `<q>` marks | the fork's `el_before_after_base::add_quote`, `UA_EXTRA` |
| `direction` and `dir` (`auto` too): right to left lines, alignment, blocks, and a simplified bidi that keeps embedded runs in their order | the fork's `line_box::reorder_bidi`, `render_block_context` |
| `:lang()` on the nearest `lang`, `:empty` | the fork's `html_tag::select_pseudoclass` |
| The `translate`, `rotate` and `scale` properties | the fork's `css_properties::compute_transform` |
| `letter-spacing` and `word-spacing`, a no-break space widened inside its word too | `Container` (`spacing_units`, `spacing_words`), the fork's `font_description` |
| `font-variant-numeric` and `font-feature-settings` as OpenType features | the fork's `font_description::features`; `Backend::create_font` |
| SVG: `getComputedTextLength`, text `getBBox`, a shape's or text's rect through the view box, `<use>` of a `<symbol>`, `<tspan>` with its own fill and shifts | the fork's `svg_ctm`, `svg_text_runs`, `el_svg` painter; `svg_box_of` |
| Dashed and dotted borders and outlines, mitred corners between sides that differ; dashes and dots spaced as Chromium does, round the centre line of a rounded border | `Container::border_dash`, `draw_sides`, `dashed_ring` |
| Computed `fill` and `stroke` inherited (through `<use>` too), `currentColor` resolved | the fork's `svg_paint_color` |
| Canvas `textBaseline` on the em box (the font's OS/2 typographic metrics), as Chromium places it | `FontMetrics::em_ascent`, `Canvas::text` |
| A PNG cut short or corrupt keeps the rows before the damage, the rest transparent | `png_intact_rows` (`src/png.cpp`), in each backend's image loading |
| `background-repeat` | `Container::draw_image` |
| `object-fit`, `object-position` | the fork's `el_image::fitted` |
| Gradient stop positions fixed up as CSS does (an explicit `0` is a position) | the fork's `prepare_color_points` |
| `text-overflow: ellipsis` | the fork's `render_item_inline_context::apply_ellipsis` |
| `filter` and `backdrop-filter`: blur, brightness, contrast, grayscale, hue-rotate, invert, opacity, saturate, sepia, drop-shadow | `FilterPush`, `Backdrop` in the display list; `D2DBackend::filtered` |
| A canvas drawn from another canvas that the same batch draws later | `Renderer::apply_ops` |

## The visual set

`twidget-conformance --visual` runs the visual widgets (the SDK group aside, which needs a client's
host) at each of their widths, drawn whole as the Swift runner draws them (2400 px), and writes the stage
crops for `tools/conformance/visual-report.mjs`, which judges them with the set's `diff.mjs`. On
2026-10-20: 123 of 171 crops pass. What fails is not a rendering difference to fix:

- text stages (03 align, families, weights, scripts, ellipsis, text-shadow; 08 and 09 text; 13 fonts and
  forms): the references were drawn with other fonts (system-ui is not Segoe UI there, and the strokes are
  heavier); the layout matches;
- motion diagrams (06 slide, spin-pulse; 07 bounce; 09 smil): a template shows the animation's path, the
  crop is a frame of it;
- 08 paint: `createPattern` is out of profile 1 and draws nothing, the reference shows Chromium's;
- 04 table: 5 % of ink, at the threshold, in antialiasing.

v0.1.1 adds widgets judged whole: 14 (no CSS at all), 15 (a body background without a margin reset) and 16
(selection). The runner paints the default palette's `bg_color` under the transparent renderer, as a client
draws its bubble. On 2026-10-21 they differ from the references by where their lines wrap, the fonts again:
the body background of 15 fills the bubble edge to edge, and 14 lays out as the reference does. 16 is a
tester's: a mouse selects its opted-in paragraph in the Windows spike, touch does not yet.

## Each platform's painter

The pixel stages above judge the Direct2D painter. The filter commands on the others, which this machine
type-checks but does not run:

| Command | CoreGraphics | Android | Web | Qt |
|---|---|---|---|---|
| `FilterPush` / `FilterPop` (filter) | a bitmap context through `filter_pixels` | a bitmap through `filter_pixels` (JNI) | the canvas's own `filter` | a layer through `filter_pixels` |
| `Backdrop` (backdrop-filter) | the bitmap's pixels through `filter_pixels` | not drawn: a `Canvas` gives no pixels to read | a copy of the canvas through `filter` | the layer's pixels through `filter_pixels` |

Dashed and dotted borders are `Path` strokes with dashes, which every painter draws for SVG already.

## Not TwidgetCore's

| Item | Where |
|---|---|
| The `emoji` stage (`css.text.emoji`) | the reference is Apple Color Emoji; Windows draws Segoe UI Emoji |
