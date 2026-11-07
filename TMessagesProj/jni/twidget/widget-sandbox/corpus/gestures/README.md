# gestures — what the horizontal-only rule removed

Widgets made for one question: **should vertical drags over a widget always scroll the
chat, so that widgets can claim only horizontal drags?** Artem tried them in the Telegram
test group and decided yes (v0.1.3, `docs/decisions.md` "Vertical drags are the chat's").
They stay as the record of that decision:

- **What the rule removed**: two games in pairs, the same widget on `touch-action: none`
  (`…-xy`, written to claim both axes) and on `pan-y` (`…-x`, horizontal only), and a
  wheel picker on `pan-x`, a vertical control. The two files of a pair differ in one CSS
  value and their first line. Since v0.1.3 **the `-xy` variants behave like their `-x`
  twins** (a vertical drag scrolls the chat on `none` too; `none` still takes holds), and
  **the wheel picker can no longer turn**: `pan-x` claims nothing, every drag over it is
  the chat's.
- **What is fine with horizontal only**: a paddle game, a card carousel and a range slider,
  all on `pan-y`, unchanged by the rule.

## The rule they run on (v0.1.3)

A press is undecided while it stays within 10 px. Past that, the dominant axis decides
**once, for the whole press**: a vertical-first drag is always the chat's (list scroll) and
the widget gets `pointercancel`; a horizontal-first one is the widget's where it claims
horizontal drags (every move until the finger lifts, on both axes), else the chat's
swipe-to-reply. A press held 500 ms within the slop is a hold (`docs/sdk-v1.md` §5.5,
`packages/core/src/shared/gesture-policy.ts`).

| `touch-action` | vertical-first drag | horizontal-first drag | hold | double tap |
|---|---|---|---|---|
| `none` (`…-xy`) | chat: scroll (the widget's until v0.1.3) | widget | widget (`contextmenu`) | widget |
| `pan-y` (`…-x`) | chat: scroll | widget | chat: message menu | widget |
| `pan-x` (`…-y`) | chat: scroll (the widget's until v0.1.3) | chat: swipe-to-reply | chat: message menu | widget |

The leading 20 px of a widget never take a claim (the system back gesture), so every claimed
surface starts at least 20 px in. Every widget says in its first line which `touch-action` it
uses and what to try, and counts **presses taken by the chat** (its `pointercancel`s); the
first lines still describe the question as it was asked.

## The widgets

Sizes as the server counts them (zlib -9, limit 4096 B); heights at a 320 px wide bubble. Since v0.6.0 (the heavy-box
paint limit, `docs/decisions.md` "Heavy boxes") 08 mutes the wheel's other numbers with `--tg-theme-hint-color` and
07 the bars outside the range with a grey, where both had `opacity` (34 and 18 boxes past the limit of 16).

| File | `touch-action` | What it is | Try | Size | Height |
|---|---|---|---|---|---|
| **What the rule removed** (the `-xy` files now behave like `-x`; 08 cannot turn) | | | | | |
| `1-pairs/01-joystick-xy.html` | `none` | Star catcher: fly a dot with a virtual stick, collect stars | start up or down, circle, rest your thumb on the stick | 1825 B | 435 px |
| `1-pairs/02-joystick-x.html` | `pan-y` | the same game | the same; then start sideways and steer up | 1887 B | 435 px |
| `1-pairs/03-2048-xy.html` | `none` | 2048: swipe in four directions, equal tiles merge; score, best and the game are kept | play a few moves | 2478 B | 404 px |
| `1-pairs/04-2048-x.html` | `pan-y` | the same game | swipe up or down | 2490 B | 404 px |
| `3-vertical/08-wheel-picker-y.html` | `pan-x` | an alarm time picker: hour and minute wheels with momentum and snap | spin, flick, tap a number; start sideways | 2198 B | 294 px |
| **Fine with horizontal only** | | | | | |
| `2-horizontal/05-paddle-x.html` | `pan-y` | Brick breaker: slide anywhere to move the paddle, tap to serve | play it | 2073 B | 353 px |
| `2-horizontal/06-card-carousel-x.html` | `pan-y` | trips on cards with peeking neighbours, flick, snap, dots | swipe and flick, tap a card, drag up over a card | 2063 B | 310 px |
| `2-horizontal/07-range-slider-x.html` | `pan-y` | a two-thumb price filter from divs, with a histogram and haptic steps | slide either thumb, press the track; start a drag up on the track | 2016 B | 274 px |

## What the rule changed

- **Joystick (01 → 02).** The first move decides. A thumb on a stick rarely starts sideways on
  purpose: aiming up or down loses the press to the chat (the stick snaps back, the chat
  scrolls); a diagonal works only when its horizontal part is the larger. Once a press is the
  widget's, up and down drift reaches it: nothing is taken away mid-press. 01 on `none` now
  plays exactly like 02 for drags; it keeps holds (a resting thumb does not open the message
  menu), which `pan-y` in 02 gives to the chat.
- **2048 (03 → 04).** Up and down swipes scroll the chat, so only left and right move the tiles
  and the board fills up, on both files now. An author adds buttons for up and down.
- **Wheel picker (08).** A vertical control: it cannot be spun at all, every drag scrolls the
  chat (on `pan-x` it never claimed horizontal drags either). An author turns it on its side
  or replaces it with buttons.
- Drawing on a canvas, panning a map and guiding a ball through a maze are 2D in the same way
  as the joystick, and lose the same.

Fine with horizontal only, unchanged by the rule:

- **Paddle (05)**, **card carousel (06)** and **range slider (07)** move along one horizontal
  axis; a vertical drag over them is the chat's scroll, which is what a reader wants. (`pan-y`
  gives a resting thumb to the message menu; none of them needs holds.)

## Renderer notes

- **The card carousel** moves its track with
  `transform: translateX(calc(11% - i * (78% + 10px) + dx px))` and snaps to a card through a
  `transition` on `transform`. It does not move on the iOS and Android Telegram clients, while the
  paddle, also on `pan-y`, works there: the claim works, and the paddle moves with `left`. On the
  web prototypes, litehtml draws no transform (the dot moves on, the track does not) and Blitz
  does not apply a transform set through a transition (the track stays where the finger left it).
  The automatic conformance set checks these forms (`css.transform.2d`, `css.value.math` in
  `auto/09-transforms-effects-a`; `anim.transition` in `auto/10-css-animations`), and
  `visual/05-transforms-effects-a` draws them for its reference picture. The carousel keeps its
  transform as the real case; the other widgets move things with `left` and `top` only.
- **The 2048 board** was a 4-column grid whose height came from `aspect-ratio: 1`, with
  content-sized rows: browsers share the free height unevenly between rows with and without a
  number, and a renderer without `aspect-ratio` collapses the board to its numbers. It is now
  sixteen cells placed in whole px from JS (the board measured once and again on resize with
  `ResizeObserver`), which iframe, Shadow DOM, Blitz, litehtml and the reference draw alike.
  Both features are in profile 1 and covered: `css.box.aspect-ratio` (`auto/03-layout-b`) and
  `css.display.grid` (`auto/03-layout-a`: `repeat(4, 1fr)` tracks, `grid-template-rows: 20px 1fr`
  on a definite height; `visual/04-layout`), so a client that cannot draw them has a renderer gap.

## Notes for authors

- A tap's `click` goes to the element with the pointer capture, not to the child that was
  pressed: the carousel and the wheel picker remember the card or the number pressed at
  `pointerdown`.
- A control that reacts at `pointerdown` or to the first pixels of a move changes before the
  policy has decided; when the chat takes the press (`pointercancel`), put the value back or end
  the press cleanly: the range slider undoes the jump of its nearest thumb, the stick
  re-centres, the carousel and the wheel snap back.
- A mouse drag still ends with a `click` (a finger's never does): the carousel ignores a click
  after the pointer moved more than 6 px.
- A game board: fixed sizes in px rather than a grid sized by `aspect-ratio`.

## How to try them

- Dev app (`corepack pnpm dev`): the `gestures/` group in the corpus list, or
  `?file=gestures/1-pairs/04-2048-x.html&width=320`. The **gestures** select is `phone` by
  default: the mouse plays a finger and the bubble shows what it took (`↕ list scroll`,
  `↩ swipe: reply`, `⋯ message menu`). **compare all** (`&compare=1`) draws the widget on every
  renderer side by side (Blitz and litehtml need `pnpm build:blitz-web`, `pnpm build:litehtml-web`).
- Chat feed: `?feed=1&corpus=gestures`. On a touch device, or with touch emulation in Chrome's
  device toolbar, a drag the chat takes really scrolls the feed.
- Node harness, the press report:
  `corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/gestures/1-pairs/02-joystick-x.html --width 360 --swipe 180,335,0,-60 --swipe 180,335,60,0 --hold 180,335`.
- `corepack pnpm test:gesture-corpus` loads the eight widgets in the sandbox and in reference
  mode (no JS errors, pixels within 3 %) → `docs/compat-report-gestures.md`; with
  `TW_RENDERER=shadow` on the Shadow DOM renderer. It checks that they load and draw, not
  who gets a press (`tests/gestures.spec.ts` and the conformance set do).
- The Telegram test group: the **Gestures** topic of `scripts/conformance/publish-channel.ts`,
  not in the default run: `--topic gestures` (each file's `idea:` line is its description).

Rules: pointer events and `setPointerCapture`, no libraries, no form controls (out of profile
1), theme variables where a colour has a theme counterpart, at most 3800 B compressed and 560 px
of height at a 320 px wide bubble.
