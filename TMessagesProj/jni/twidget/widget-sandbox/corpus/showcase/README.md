# showcase — interactive, animated demos on widget profile 1

27 widgets written the way a widget author would write them for widget
profile 1 (`docs/profile-v1.md`) and SDK v1 (`docs/sdk-v1.md` §2), with
nothing held back for a `render()` harness or for a native renderer: CSS
transitions and `@keyframes`, grid, 2D transforms (flips included), inline
SVG with gradients and `stroke-dashoffset`, canvas 2D, pointer events (drag,
swipe, scrub, a drawn slider and dial), segmented controls and steppers
instead of form controls, live clocks and tweened numbers. Each one is a
realistic chat-message case with a small state machine behind it, not a
feature probe; what the user chose is kept in `localStorage`, what leaves
the widget is a link. They are the content of the web chat feed (`?feed=1`,
`apps/web/src/feed.ts`, `docs/chat-feed-web.md`) and run in the dev picker like every
other group. Product-shaped dev content, not a test of the renderers: the
canvas renderers (Blitz, litehtml) draw only the subset they support.

The difference from the other groups: `htmlbubbles-ported` keeps Telegram's
`render()`-era demos faithful (static cards, little motion), `common` is
deliberately flat so three renderers compare like for like, `generated` is
what a model writes from a prompt. This group is what the feature should look
like at its best within the profile every client draws.

| Batch | File | The case | What moves | Kept locally / links out |
|---|---|---|---|---|
| `3d/` | `01-robot` | pocket robot viewer | horizontal orbit, elevation and zoom buttons; three poses | view and pose |
| | `02-camera` | camera assembly viewer | orbit the assembled body or separate lens elements | view and assembly |
| | `03-orbits` | single shaded planet | perspective ray/sphere shading, camera orbit, elevation and cloud toggle | view and cloud cover |
| | `04-island` | miniature coastal retreat | orbit the model; switch between three tide levels | view and tide |
| | `05-molecules` | ball-and-stick molecular viewer | orbit water, methane or ammonia | view and molecule |
| | `06-cat` | sculpted cat model | mouse X/Y rotation, tail pose, zoom and elevation | view and pose |
| `travel/` | `01-boarding-pass` | airline boarding pass | gate countdown (1 Hz, turns orange → red), tap flips the pass to a QR pattern (a 2D flip: one face folds to a line with `scaleX`, the other unfolds; both faces share one grid cell), Add to Wallet settles into a done state, the plane glides along the route | "In Wallet"; Wallet opens the `.pkpass`, Share opens Telegram's share sheet (`t.me/share/url`) |
| | `02-ride-tracking` | taxi on its way | the car drives the route on a stylised SVG map (the streets are one path, the car a JS polyline: no layout queries), the ETA and distance count down, status steps light up | Call / Message open the ride's page |
| | `03-hotel-booking` | hotel card | swipeable photo carousel (pointer events + snap), two-tap date range on a mini calendar, guests stepper, price breakdown tweens, Book → a drawn check mark | the booking (a re-created widget opens on the confirmation), the heart |
| | `04-weather` | weather forecast | animated SVG sky (sun rays, drifting clouds, rain), an hourly temperature curve that morphs (`d` transition) when a day is tapped, the big number tweens | — |
| | `05-itinerary` | a day's plan | vertical timeline whose fill and NOW marker move with a 60× demo clock (1 Hz), cards expand with a height transition | Push 30 min; Navigate opens a maps link |
| `commerce/` | `01-order-status` | food delivery | steps complete in sequence (check marks drawn with `stroke-dashoffset`), "stops away" ticks, receipt collapses, a star rating slides in after delivery | the rating (a rated order opens delivered); Help and Order again are links |
| | `02-product-configurator` | headphones in a shop | the inline-SVG product recolours with a transition per swatch, cushions and fit chips, the price rolls, Add to cart bounces the cart badge | the cart (badge, last configuration) |
| | `03-split-bill` | restaurant bill | tap a dish then the friends who shared it, per-person totals tween, a tip segmented control (0–25 %), Request marks friends as paid one by one | the split, the tip, "requested"; Request opens the share sheet with the amounts |
| | `04-flash-sale` | flash sale | flip-clock countdown (the new digit unfolds with `scaleY`; two alternating class names restart it without a reflow), a stock bar that depletes as others buy, tiles lift on hover, Buy turns into a stepper, one item sells out | the cart; Checkout links out |
| | `05-subscription-plans` | pricing plans | monthly / yearly segmented thumb, prices roll to the new numbers, the chosen plan lifts with a gradient border and reveals its features | plan and period; Start opens the subscription page |
| `life/` | `01-fitness-rings` | activity summary | three rings sweep in with staggered easing, the step count rolls up, a week of bars grows; tapping a day replays its rings | — |
| | `02-smart-home` | smart-home panel | animated toggles, a dimmer (a drawn slider dragged with pointer events, the track measured once per press) that lights the tile, a thermostat dial dragged with pointer events (SVG arc), scenes that set everything at once | the whole panel and the scene |
| | `03-pomodoro` | focus timer | an SVG ring drains at 1 Hz, Start ↔ Pause, session dots, a break turns the card calm with a breathing animation; the task is a tap-to-cycle preset (no text field); a "demo speed ×20" link | the task, finished sessions, focus time today |
| | `04-habit-tracker` | weekly habits | tapping a cell pops a check in, the streak flame and completion ring update, a perfect day fires canvas confetti | the week's ticks |
| | `05-recipe` | cooking steps | a servings stepper rescales every quantity (with a bump), ingredients tick off, step cards slide with a progress bar, a step's timer counts down on a ring | servings, ticks, the step, "cooked" |
| | `06-music-player` | music player | spinning vinyl art (paused when paused), a seekable waveform (pointer scrub), play/pause icon morph, an equaliser on the playing row; no audio (the sandbox has none) | liked tracks, the current track |
| `work/` | `01-ci-pipeline` | CI run | stages spin, pass and fail in sequence, log lines stream into a terminal pane, durations tick, Re-run failed replays it green | Open links to the run |
| | `02-kanban` | sprint board | drag a card between columns (a fixed-position clone follows the pointer, the column under it highlights), counts and the progress bar update, Add pops a card in | the board |
| | `03-event-rsvp` | event invite | blurred gradient orbs on the cover, an avatar stack that grows as others accept (timers), Going / Maybe / Can't, confetti on Going, a live "starts in" clock | your reply, +1, calendar; Add to calendar opens the `.ics` |
| | `04-live-poll` | live poll | other voters trickle in (seeded), options re-sort with a FLIP transform, bars and percentages tween, your vote is highlighted and retractable | your vote |
| | `05-stock-card` | stock quote | the price ticks with a green/red flash, the sparkline (path + gradient area) grows with each tick, range chips morph the curve, a scrubber reads the price at a point | the price alert; Buy / Sell open the broker's page |

## Conventions

- A `<!-- showcase: widget profile 1, SDK v1 … idea: … -->` header, then one
  `:root` block with `color-scheme: light dark` and `light-dark()` variables
  (both themes), generic font families only (`system-ui, sans-serif`;
  `ui-monospace, monospace` for code), a `.card` at `width: 100%` with no
  radius and no shadow of its own: the widget owns the whole bubble like a
  photo message, the host decides the width and clips with the bubble's
  radius. Since v0.1.1 every bubble of the dev page, the feeds and the native
  demos works that way, as on a client (`apps/web/src/style.css` `.bubble`: no
  padding, `overflow: hidden`): the widget's body margin is the inset, so the
  older groups (`htmlbubbles-ported`, `common`), 392 px cards with a body margin
  of 0, now touch the bubble's edges.
- Inside profile 1, so the validator removes nothing: no form controls (a
  slider is a drawn track dragged with pointer events or a segmented
  control, a text field is a set of presets, a checkbox is a toggle
  button), no WebGL or CSS 3D transforms (flips are `scaleX` / `scaleY`), no SVG `<pattern>`, no
  `text-shadow` (a `drop-shadow()` filter instead), no `user-select`, no
  keyboard or focus, no inner scrolling, no named fonts.
- At most 16 heavy boxes (a blurred shadow, a filter, a backdrop-filter, `opacity` below 1, or an animation of one
  of them; v0.6.0, `docs/decisions.md` "Heavy boxes"): muted text and small details take a colour
  (`--muted`, `rgba()`, `fill-opacity` / `stroke-opacity` in SVG), not `opacity`. `travel/04-weather` and
  `life/01-fitness-rings` were reworked that way (18 each before).
- State lives in JS, the DOM is updated in place; lists may be rebuilt with a
  template literal, the whole widget never is. No `top`/`parent`/`self`/
  `window`/`document` as global names.
- SDK v1 has no `sendMessage`. What the user chose (a vote, an RSVP, a cart,
  a booking, a scene, ticks) is **local state**: one JSON value under one
  key per widget in `localStorage`, which the client keeps per message (16 KB;
  it survives the widget being dropped and re-created and an app restart),
  through a small try/catch helper (`store.get` / `store.set`) so a missing
  or full storage only loses the memory, never the widget. It is read at
  load and the UI is restored from it (a rated order opens delivered, a
  booking opens on its confirmation, a focus timer comes back stopped), and
  written on taps and at the end of a drag, never at load or per frame. The
  keys differ from widget to widget only because the dev app's reference
  mode shares one origin's storage.
- What leaves the widget is a link: `openLink('https://….example.com/…')`, or
  `openTelegramLink('https://t.me/share/url?…')` for Telegram's share sheet,
  called directly in the tap's click handler, one per tap, ≤ 256 characters.
  The client asks the user to confirm and the widget is not told the
  outcome, so link buttons keep their label. `window.TelegramWidget` is
  absent when the dev app renders the file natively (reference mode), so the
  calls are `tw?.openLink(…)` / `tw?.openTelegramLink?.(…)`. No `<a href>` on
  tapped elements (the reference frame would navigate).
- Everything that moves is cheap for the sandbox: CSS transitions and
  keyframes where the browser can do it alone, `requestAnimationFrame` only
  for tweened numbers and short sweeps (≤ 1.5 s), 1 Hz `setInterval` for
  clocks, 4 Hz for the player's progress. **No layout query per frame**: the
  ride's car is interpolated along a JS polyline instead of
  `getPointAtLength`, the itinerary measures `offsetTop` once a second, the
  dimmer measures its track once per press, the flip clock and the price
  flash restart their animation by alternating two class names instead of
  `void el.offsetWidth` (every such read is a sync round trip to the
  renderer, `docs/protocol.md`).
- Demo clocks are sped up where a real one would show nothing in a minute
  (the itinerary runs at 60×, the ride takes 90 s, the recipe timer ×10,
  the CI run is real seconds); countdowns start at a value that reads well.
- Seeded pseudo-random data (an LCG) so two runs lay out the same; "other
  people" arrive on fixed timers.
- Every clickable element that is a real button carries `role="button"` or
  is a `<button>`: `tests/generated.spec.ts` clicks the first four visible
  ones, so the first four must leave the widget in a state that is still
  deterministic enough to screenshot (the pomodoro's speed link is a plain
  span for that reason, the boarding pass keeps its Wallet / Share buttons
  outside the flipping card so they never hide mid-flip, and hidden steps of
  the recipe's deck are `visibility: hidden` so Playwright does not scroll
  them into view).

## How to run

```bash
corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/showcase/work/04-live-poll.html --verbose   # the Node harness: no [error], no validator diagnostics
corepack pnpm test:showcase                      # sandbox vs native Chromium, up to 4 clicks → docs/compat-report-showcase.md, reports/showcase/
TW_RENDERER=shadow corepack pnpm test:showcase   # the same on the Shadow DOM renderer → docs/compat-report-showcase-shadow.md
corepack pnpm dev                                # 127.0.0.1:5173/?feed=1 — the feed on this corpus (corpus=common for the flat one); the picker lists showcase/… too
```

`pnpm build:ios|macos|android|desktop` copies the group into the native demos
(`packages/core/scripts/build-native-bundles.ts`); the iOS/macOS demo lists the
`showcase/<batch>` sections, runs them with `--autorun showcase`, and the iOS
chat feed's **Widgets** picker fills the feed with them (`--feed-set corpus=showcase`). The widgets use grid, transforms,
SVG and canvas, so the Blitz and litehtml renderers show their gaps there,
which is a fair thing to look at but not what the group is for.

## Status (2026-10-17)

21 files on widget profile 1 and SDK v1, no `sendMessage` anywhere. Every
file passes the Node harness with no errors and no validator diagnostics, in
the document and the shadow scope, at load and after the first four taps
(no layout queries at load except the itinerary's and the poll's
`offsetTop`, which are on 1 Hz / per-vote clocks, not per frame); the restore
paths were checked with seeded storage.

On the full web profile (2026-10-12) `pnpm test:showcase` (sandbox vs native
Chromium, up to 4 clicks, 3 % pixel tolerance) was **21/21**. Once the
validator enforced profile 1 it fell to 18/21: the boarding pass's 3D flip
(the back face showed through, 41 %), the pomodoro's task `<input>` (12 %)
and the smart-home dimmer's `<input type=range>` (3.8 %) were removed in the
sandbox only; the other files carried smaller removals (vendor font names,
`accent-color`, `user-select`, the flip clock's `rotateX`, the map's
`<pattern>`, `text-shadow`). All of them are replaced here by in-profile
equivalents; the suite has not been re-run on this version yet. Looked at in
the feed on the iframe and the Shadow DOM renderer, light and dark
(2026-10-12): content in ~50 ms pooled / ~250 ms cold per bubble. Not run on
Blitz / litehtml on purpose (see above).
