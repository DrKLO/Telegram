# generated — LLM-written widgets on profile 1 and SDK v1

Two sets of 20 widgets, written from the **same 20 prompts** (`manifest.json` in
each set has them) on 2026-10-08 by `claude-fable-5-1` in a Claude Code session:

| Set | How the model wrote it |
|---|---|
| `with-profile/` | with `docs/widget-runtime-profile.md` (the text of that day, before widget profile 1) in context, following it |
| `without-profile/` | as for a generic `show_widget(html)` tool, with no profile text |

`tests/generated.spec.ts` (`pnpm test:generated`) loads each file in the sandbox
and in reference mode (Chromium renders the same file natively, with the client's
default `<button>` stylesheet), clicks the first four visible buttons, and diffs
the screenshots: pass = loaded, no JS errors, pixel diff ≤ 3 %. The demos load
both sets too (`packages/core/scripts/build-native-bundles.ts`, `scripts/sync-demo-corpus.ts`).

## Adapted by hand, 2026-10-17

Widget profile 1 (`docs/profile-v1.md`) and SDK v1 (`docs/sdk-v1.md` §2) made half
of these files fail: the validator removes what is out of the profile, so the
sandbox drew something else than Chrome, and `TelegramWidget.sendMessage` no
longer exists. On 2026-10-17 both sets were **adapted by hand** (no model was
run) to fit profile 1 and SDK v1, keeping each widget's intent, look and size.
The model's originals are in git history at `a2c3c54`
(`git show a2c3c54:corpus/generated/with-profile/01-show-me-a-mortgage-calculator-loan-amount.html`);
they still show what a model writes with and without the old profile text: the
`without-profile` set reached for Chart.js, Google Fonts, `fetch`, a remote image,
`alert`/`confirm` and `100vh`, the `with-profile` set for `sendMessage` (11 of 20),
form controls and a 3D flip. The last run before the change passed 20 of 40.

What the adapted files do:

- **Taps only.** Number fields and sliders became − / + steppers or rows of
  presets, `<select>` a segmented row of buttons, checkboxes toggle buttons, the
  colour picker a row of colour buttons, a text field suggestion chips. No
  `<label>`, `<form>`, `accent-color`, `:focus`, `user-select`.
- **No `sendMessage`.** The result a widget used to send is kept as local state:
  `localStorage` (per message, survives the widget being re-created; 16 KB),
  shown in the UI and restored on load, through a small `try`/`catch` helper.
  None of the actions is naturally a link, so no file calls `openLink`.
  `window.TelegramWidget` is absent in reference mode, so every use is guarded.
- **No libraries.** The Chart.js charts are drawn by hand: inline SVG in
  `with-profile`, a 2D canvas in `without-profile` (as Chart.js would), with the
  tooltip on a tap.
- **Generic fonts only** (`system-ui, sans-serif`, `ui-monospace, monospace`);
  no `-apple-system` either (the validator rewrites it, a `validator:profile`
  diagnostic), no web fonts, no `vh`, no 3D transforms, no `text-shadow`, no
  remote images, no `fetch`, no dialogs, no `location.reload()`.
- **Short.** Every widget stays under about 500 px: a taller one scrolls the dev
  page during the click pass of `tests/generated.spec.ts`, and the dev app's
  bar ends up in both screenshots. The recipe cards show one step at a time.
- New code formats numbers with an explicit `'en-US'`, so the sandbox and the
  reference agree whatever the client locale.

## Per file

### with-profile

| File | Out of profile / SDK v1 | Replaced with |
|---|---|---|
| 01 mortgage | `<input type=number>`, two `<input type=range>`, `<label for>`, `accent-color`, `-apple-system`; `sendMessage` ("Send this estimate to the chat") | amount stepper (± $10,000), rate stepper (± 0.25 %), term presets 10–30 yr; "Save this estimate" stores it in `localStorage`, shown under the button, restored with the inputs |
| 02 quiz | `-apple-system`; `sendMessage` (score) | best score in `localStorage`, shown on the score screen |
| 03 budget | Chart.js `<script src>` (it never ran: the fallback table showed), `-apple-system` | hand-drawn SVG doughnut with the total in the middle and a legend; a tap on a slice or legend item shows its amount and share |
| 04 tip | `<input type=number>`, tip slider, labels, `accent-color`, `-apple-system`; `sendMessage` ("Share split in chat") | bill buttons −5 / −1 / +1 / +5, tip presets 0–25 %; "Save this split" in `localStorage`, restored |
| 05 pomodoro | `-apple-system`; `sendMessage` when a session ends | focus / break session counter in `localStorage`, shown under the timer |
| 06 laptops | `user-select`, `-apple-system`; `sendMessage` ("I pick …") | the pick in `localStorage`: the row gets a ✓, its button is highlighted, a "Your pick" line |
| 07 BMI | five `<input type=number>`, labels, `-apple-system` | − / + stepper per value, with limits |
| 08 line chart | `-apple-system` | `system-ui` (nothing else) |
| 09 unit converter | two `<input>`, two `<select>` with 54 `<option>`, `selectedIndex` / `value` writes the validator rejected, `-apple-system` | value stepper (± 1, ± 10), a row of unit buttons for each side, swap |
| 10 multiplication | `-apple-system` | `system-ui` (it already highlights on tap) |
| 11 to-do | `<form>`, text field, checkboxes, `accent-color`, `-apple-system`; `sendMessage` ("Send list") | suggestion chips add tasks, toggle buttons check them off; the list lives in `localStorage` (the original said it could not persist); "Send list" became "Reset list" |
| 12 countdown | `-apple-system` | `system-ui` |
| 13 Gantt | `-apple-system` | `system-ui` |
| 14 palette | `<input type=color>`, `<select>`, `user-select: all`, `-apple-system`; `sendMessage` (hex code) | eight base-colour buttons, harmony buttons; the tapped hex is saved as "your pick" in `localStorage` with base and harmony; the swatches no longer overflow the bubble (button padding). **Not kept:** "tap to copy" — no clipboard and no text selection in profile 1 |
| 15 flashcards | `perspective`, `transform-style: preserve-3d`, `rotateY`, `backface-visibility`, `-apple-system`; `sendMessage` (result) | 2D flip (the face folds with `scaleX`, the other unfolds); progress, best and the words to review in `localStorage`; "Start over" on the result screen |
| 16 bar chart | Chart.js `<script src>` (the fallback div bars showed), `-apple-system` | hand-drawn SVG horizontal bars with a % axis and grid; a tap on a bar shows the tooltip text |
| 17 tic-tac-toe | `-apple-system`; `sendMessage` (result) | running score in `localStorage`, restored |
| 18 currency | two `<input>`, two `<select>`, three rate fields with labels, `selectedIndex` / `value` writes, `-apple-system` | amount buttons −10 / −1 / +1 / +10, a row of currency buttons per side, swap; rate − / + steppers inside the `<details>`, edited rates in `localStorage` |
| 19 recipe | seven checkboxes in labels, `accent-color`, `-apple-system`; `sendMessage` when a timer ends; 846 px tall | ingredient toggle chips; one step at a time with Back / Next and dots (about 440 px); a timer per step as before; checks, current step and finished timers in `localStorage` |
| 20 habit tracker | `-apple-system`; `sendMessage` ("Save progress to chat") | marked days saved in `localStorage` on every tap (one key per month), "Saved on this device" and a "Clear month" button |

### without-profile

| File | Out of profile / SDK v1 | Replaced with |
|---|---|---|
| 01 mortgage | Google Fonts `<link>`, `'Inter'`, `<input type=number>`, two sliders, labels | amount and rate steppers, term presets |
| 02 quiz | `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto`, `min-height: 100vh` (901 px in Chrome, content height in the sandbox), `location.reload()` for "Try again", `:disabled` | `system-ui`, no min-height, "Try again" restarts in place, `[disabled]` |
| 03 budget | Chart.js `<script src>` (`ReferenceError: Chart is not defined`) | doughnut drawn on a 2D canvas, HTML legend; a tap on a slice or legend item shows its amount |
| 04 tip | `<input type=number>`, slider, labels, `accent-color`, named fonts | bill buttons, tip presets |
| 05 pomodoro | named fonts; `Notification` / `alert()` when the time is up | a "Time is up!" line under the controls |
| 06 laptops | named fonts, `user-select` | `system-ui` |
| 07 BMI | five `<input type=number>`, labels, named fonts | − / + steppers |
| 08 line chart | Chart.js 4.4.1 from a CDN (`ReferenceError`) | canvas: smooth filled lines with points, ° axis, month labels, HTML legend; a tap draws a guide and a tooltip |
| 09 unit converter | `<input>`, `<select>`, named fonts | value stepper, unit button rows, swap |
| 10 multiplication | named fonts; highlight on hover only | a tap highlights too (hover is a desktop-only enhancement in profile 1) |
| 11 to-do | `<form>`, text field, checkboxes, `accent-color`, named fonts, `confirm()` before a delete (always `false` in the sandbox: nothing could be deleted) | quick-add buttons, toggle buttons, delete at once; `localStorage` as before, behind `try`/`catch` |
| 12 countdown | two Google Fonts `<link>`, `'Orbitron'`, `min-height: 100vh`, named fonts | no links, `ui-monospace` digits, `min-height: 220px` |
| 13 Gantt | named fonts | `system-ui` |
| 14 palette | `<input type=color>`, `<select>`, `navigator.clipboard.writeText` (rejects), named fonts | base-colour and harmony buttons; a tapped hex goes to a "Saved" strip (last five, `localStorage`) with a toast. **Not kept:** copying |
| 15 flashcards | 3D flip (`perspective`, `preserve-3d`, `rotateY`, `backface-visibility`), named fonts | 2D flip with `scaleX` |
| 16 bar chart | Chart.js `<script src>` (`ReferenceError`) | bars drawn on a canvas, an HTML tooltip on tap |
| 17 tic-tac-toe | named fonts | `system-ui` |
| 18 currency | `<input>`, `<select>`, rate fields, `fetch` of live rates (`network-blocked`), named fonts | amount buttons, currency button rows, rate steppers; the note says the rates are built in. **Not kept:** live rates (no network in a widget) |
| 19 recipe | remote Unsplash hero image (`url-blocked`), `text-shadow`, checkboxes, `accent-color`, named fonts; 901 px tall | gradient hero, ingredient toggle chips, one step at a time (a timer per step as before) |
| 20 habit tracker | named fonts, `user-select`, `el.animate()` (Web Animations are not on every client: `unsupported` in the harness) | a CSS `@keyframes` pop; `localStorage` behind `try`/`catch` |

## Checking a file

```bash
corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/generated/with-profile/01-show-me-a-mortgage-calculator-loan-amount.html
```

On 2026-10-17 all 40 ran through the Node harness (document and `--shadow`
scope) with no `validator:profile` or `validator:css-dropped` line, no
`[error]`, no `script-skipped` or `unsupported` diagnostic, with clicks on the
first four buttons and scripted taps on the charts, cells and timers.
`pnpm test:generated` (pixel diff against Chrome) was not run with this change.

## The paint limit, 2026-11-02

Since v0.6.0 a widget with more than 16 heavy boxes stops (`docs/decisions.md`, "Heavy boxes"). Both
`12-create-a-countdown-to-new-year-with-days.html` twinkled their 30 and 40 stars with an `opacity` animation; their
stars now twinkle with the alpha of their background colour, which looks the same and costs nothing.
