# common — widgets on the profile every renderer shares

32 widgets in the spirit of Telegram's HTMLBubbles demos (cards, polls, carts,
tickers, dashboards) written so that **the WebView, Blitz and litehtml draw the
same picture**. The generated corpus and the Telegram ports lean on grid, SVG,
transforms and shadows that one native engine skips and the other half-draws;
a timing taken on them compares different amounts of work. This corpus is the
intersection, so start-up, update and memory numbers from `Renderer`
WebView | Blitz | litehtml compare like for like. Dev tooling for the
three-way comparison, not a product path (`docs/blitz-experiment.md`,
`docs/litehtml-renderer.md`).

| Batch | Files | What it measures |
|---|---|---|
| `cards/` | 8 static layouts of realistic density: profile, carousel, digest, pricing tiers, receipt, timetable, notification stack, KPI dashboard with div bar charts | start-up to first content, one layout |
| `interactive/` | 9 state machines on a tap: tabs, accordion, poll (bars grow with rAF), quiz, cart, paged list (innerHTML rebuild), calendar (rebuilt from a string), tic-tac-toe, filter chips (`hidden` toggles) | cost of a structural update |
| `live/` | 8 that keep going on their own: clock + countdown (1 Hz, forever), progress bars (rAF, 4 s), counters (rAF, 1.4 s), deploy log (a line every 550 ms), ticker (2 Hz for 40 s), typewriter (a character every 28 ms), slideshow (every 2.5 s), skeleton → content (one swap at 1.4 s) | cost of a text-only or width-only update per frame; litehtml rebuilds the document on each |
| `heavy/` | 7 that make a layout engine work: 60-row table (sortable), 300 chips, 120-node tree, 900-word article, 224-cell palette, 40-message transcript (+20 per tap), dense dashboard (one big innerHTML on Refresh) | a full relayout under load |

Every clickable element carries `role="button"`, so `tests/generated.spec.ts`
clicks the first four of them (same rule as the generated corpus).

## The profile

Allowed (checked against litehtml master `4a651c5` and Blitz `c351a46`, and
by rendering every file on both; see Status):

- block, inline, `inline-block`, **flexbox** (`flex-wrap`, `flex-grow`/`-basis`,
  `justify-content`, `align-items`, `align-self`, `order`), **tables** with
  `border-collapse`, lists (`list-style`), `position: relative` / `absolute`
  (used sparingly: badges over images, a poll's fill bar, a slideshow caption)
- borders with `border-radius`, `border-style: dashed`, solid and
  `linear-gradient` backgrounds, `rgba()` / `hsl()` colours, `overflow: hidden`
- `var(--…)` custom properties; the theme is `:root` variables overridden under
  `@media (prefers-color-scheme: dark)` (the validator rewrites that query for
  every renderer; `light-dark()` is unknown to litehtml)
- `font-size/weight/family` (`system-ui, sans-serif` and `ui-monospace, monospace`:
  widget profile 1 keeps the generic families only), `line-height`,
  `text-align`, `text-decoration: line-through`, `text-transform: uppercase`
  (ASCII only), `white-space: nowrap`, `min-/max-width/height`, `box-sizing`,
  negative margins, `[hidden]`
- JS: `textContent`, `classList`, `style.width/height/backgroundColor/marginLeft`,
  `innerHTML` / `insertAdjacentHTML`, `hidden`, click delegation on `data-*`
  attributes, `setTimeout` / `setInterval` / `requestAnimationFrame`
- SDK v1: what the user chose is local state — `localStorage` (kept by the client
  per message, restored on load: the follow toggle, the carousel's bag, the
  billing period, dismissed notifications and their notes, the cart's
  quantities) — and linking out is `TelegramWidget.openLink('https://example.com/…')`
  called in the tap's click handler. Both are guarded: reference mode has no
  `TelegramWidget`, and storage access goes through a `try`/`catch` helper.

Kept out on purpose (one side lacks it, or it is untested there):

| Not used | Why |
|---|---|
| `display: grid`, `gap`, `calc()` | no grid in litehtml; `gap`/`calc` untested there — flex rows + margins instead |
| inline `<svg>`, `<canvas>`, `<img>` | litehtml skips SVG, Blitz ignores its CSS, canvas is empty on both — charts are bars made of divs, icons are letters in coloured circles, pictures are gradients |
| `transform`, `box-shadow`, `opacity`, `transition`, `@keyframes`, `filter` | none in litehtml — motion is JS writing widths/text per frame, which is also the update cost worth measuring |
| `<button>`, `<input>`, `<select>`, `<progress>`, `<details>` | neither native engine draws values, sliders or marks — buttons are styled spans with `role="button"`, toggles are two-state chips |
| emoji, symbol glyphs (★ ✓ → ⏱) | Blitz on iOS and the wasm build fall back to tofu — ASCII plus `· — × − € ‹ ›` only |
| `float`, `counter()`, `::before { content }`, `text-overflow`, `letter-spacing`, `table-layout: fixed`, `vertical-align` on inline boxes | missing or unverified on one side — numbers are written as text, truncation is `nowrap` + `overflow: hidden`, column widths are explicit |
| `light-dark()`, `color-scheme` | litehtml does not parse `light-dark()`; the media query does the same job everywhere |
| `getBoundingClientRect`, `getComputedStyle`, `scrollTop`, keyboard / `input` / `change` events | layout queries and control events differ per renderer; nothing here depends on them |
| `:hover` | touch targets; the test strips it anyway |

Conventions: a `<!-- common profile … idea: … -->` header, the same 14-line
`<style>` prelude (variables, `body`, `.card` at `width: 392px; max-width: 100%`,
`.row/.grow/.btn`, `[hidden]`), no `top`/`parent`/`self`/`window`/`document`
as global names, seeded pseudo-random data (an LCG) so two runs lay out the
same, every timer bounded except the 1 Hz clock. Keep to the profile when adding
a file; if a feature is missing from the table above, assume it is out.

## How to run

```bash
corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/common/cards/05-receipt.html --verbose   # no [error], no diag lines
corepack pnpm test:common                                     # sandbox vs native Chromium, up to 4 clicks → docs/compat-report-common.md, reports/common/
TW_RENDERER=litehtml corepack pnpm test:common                # sandbox on the litehtml wasm renderer (pnpm build:litehtml-web first); TW_RENDERER=blitz likewise
# native rasters without a device (macOS): litehtml host harness and the C++ core with Blitz
corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/common/heavy/01-long-table.html --ops-file /tmp/t.json && apps/renderer-litehtml/build/twidget-litehtml-run /tmp/t.json /tmp/t.lh.png --width 392 --dpr 2
packages/desktop/build-blitz/twidget-run corpus/common/heavy/01-long-table.html --blitz --dpr 2 --png /tmp/t.bz.png [--click 60,40]
cd packages/desktop && ./build/twidget-run --corpus ../../corpus/common/cards --litehtml --dpr 2 --png-dir /tmp/common   # or --blitz; one batch per call
# demos: the runner on any renderer, and the Android start-up/memory bench on this corpus
/tmp/twidget-mac/Build/Products/Debug/TwidgetDemoMac.app/Contents/MacOS/TwidgetDemoMac --autorun common --litehtml     # also --blitz / --webview; report common-report.json
xcrun simctl launch booted org.telegram.widgetsandbox.TwidgetDemo --autorun common --blitz
adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es autorun common --es renderer litehtml
CORPUS=common scripts/android-bench.sh webview 20 ; CORPUS=common scripts/android-bench.sh blitz 20 ; CORPUS=common scripts/android-bench.sh litehtml 20
```

`pnpm build:ios|macos|android|desktop` copies `common/<batch>` into the demos
like the other groups; the groups appear in the pickers and under **Run the
common-profile corpus**.

## Status (2026-10-10; profile 1 and SDK v1 on 2026-10-17)

2026-10-17: the prelude's font list is `system-ui, sans-serif` (the validator
rewrote `-apple-system`, a `validator:profile` diagnostic in every file), and
the actions that used to post a message on the user's behalf keep local state
or open a link (`cards/01`, `02`, `04`, `05`, `07`, `interactive/05`; the FAQ in
`interactive/02` says so). The Node harness is clean on all 32 again, in the
document and the shadow scope; the suites below were not re-run for that change.

32 files. Every file passes the Node harness (no errors, no validator
diagnostics) and loads without JS errors on every renderer that exists today:

| Where | Result |
|---|---|
| `pnpm test:common` — sandbox vs native Chromium, up to 4 clicks, 3 % pixel tolerance | 32/32; worst diff 2.2 % (`live/05-ticker`, screenshots taken ~800 ms apart on a 2 Hz feed), everything else ≤ 1.6 %, 26 files at 0.00 % (`docs/compat-report-common.md`) |
| `TW_RENDERER=litehtml` / `TW_RENDERER=blitz pnpm test:common` — the wasm renderers in the web harness | 32/32 each (`docs/compat-report-common-{litehtml,blitz}.md`; the pixel column is informational there) |
| macOS demo, `--autorun common --webview` / `--blitz` / `--litehtml` (Debug build, idle machine) | 32/32 on each; widget JS at load (`jsMs`) median 46–47 ms, p90 170–180 ms, max 193 ms (`heavy/`), the same on all three renderers as expected: the renderer is not on that clock |
| litehtml host harness, 392 px, 2× (`apps/renderer-litehtml/build/twidget-litehtml-run`) | all 32 drawn; parse + layout per build 5–8 ms for `cards/` and `interactive/`, 10–13 ms for `heavy/` (8–27 KB of serialised HTML, 170–570 display-list ops), raster 2–7 ms |
| C++ core with Blitz, 800 px, 2× (`packages/desktop/build-blitz/twidget-run --blitz --png`) | all 32 drawn, taps reach the handlers (`--click`): poll bars grow, tabs swap, calendar range selects |

Looked at side by side (litehtml CoreText vs Blitz vello vs Chromium): the
pictures differ only in text rasterisation and metrics (litehtml's glyphs come
out slightly wider, so a long name may wrap a line earlier). Two engine
differences surfaced while writing and are designed around in the files, not
worked around in the engines: litehtml ignores `table-layout: fixed`
(column widths are now explicit `width: n%` on `th`), and Blitz treats
`height` on a `<td>` as extra height (cells now size by padding). Clicked
state matches too (`interactive/03-poll`, `01-tabs`, `07-calendar` on both
native engines).

Not done yet: Android (the demo compiles and the corpus is copied; `--es
autorun common` and `CORPUS=common scripts/android-bench.sh` have not been run
on the emulator in this pass), iOS on a device, and the actual three-way timing
tables — the corpus is the instrument, the measurement is the next step
(`docs/android-renderer-bench.md` is the template: start-up, per-update cost,
memory per bubble, all on this corpus).
