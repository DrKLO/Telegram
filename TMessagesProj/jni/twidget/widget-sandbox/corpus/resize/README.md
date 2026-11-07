# resize — which edge of the bubble stays put

Three realistic widgets, one per resize anchor (`TelegramWidget.setResizeAnchor`, v0.1.3,
`docs/sdk-v1.md` §2.12 and §5.6). When a widget's height changes, the client keeps the
anchored edge of its bubble where it is on screen and moves the rest of the chat:

- `top`, the default: the bubble grows and shrinks downward; its top stays and the messages
  below it move.
- `bottom`: the bubble grows and shrinks upward; its bottom stays and the messages above it
  move.

Each widget says in its first line which anchor it uses and what to try.

| File | Anchor | What it is | Try | Size | Height at 320 px |
|---|---|---|---|---|---|
| `1-default/01-faq.html` | `top`, no call | a delivery FAQ: one answer open at a time | open and close the answers: the question you tapped stays where it was | 1533 B | 356–439 px |
| `2-grows-up/02-support-thread.html` | `bottom` | a courier support thread with quick replies; the thread is kept in `localStorage`, older messages fold into a line above it | tap quick replies: the new messages appear at the bottom and the replies stay under your finger; Start over shrinks it from the top | 2196 B | 318–521 px |
| `3-grows-down/03-show-more.html` | `top`, said explicitly | hotel search results, two more per Show more, then Show fewer | open all, fold them back: the results you were reading stay put | 1602 B | 336–520 px |

Sizes as the harness counts them (its `size:` line, limit 4096 B); heights
from the smallest to the largest state at a 320 px wide bubble, in the dev app.

## Notes for authors

- Call `setResizeAnchor` before the change it should anchor: at the start of the script for
  a widget that always grows one way (02 and 03), so even the first height the client sees
  after a re-creation (from the cached height) is anchored. It applies until it is called
  again; a widget whose growth changes direction calls it before each change.
- Check that it exists: `typeof TelegramWidget.setResizeAnchor === 'function'`. A client
  that predates v0.1.3 grows every bubble downward, and the widget still works.
- The anchor is about the bubble's edges, not the scroll inside: nothing scrolls inside a
  widget. A thread that can outgrow the bubble keeps its newest messages and folds the
  older ones (02); keep every state under 560 px at 320 px wide.
- `hidden` on an element with a `display` of its own needs `[hidden] { display: none }`
  (03 has it): author CSS wins over the attribute.

## How to try them

- Dev app (`corepack pnpm dev`): the `resize/` group, for example
  `?file=resize/2-grows-up/02-support-thread.html&width=320`. A bubble anchored at the
  bottom sits at the bottom of the stage and grows upward; the stats panel shows the anchor
  next to the height.
- Chat feed: `?feed=1` keeps the anchored edge of every bubble on screen
  (`apps/web/src/feed.ts`).
- Node harness: `corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/resize/2-grows-up/02-support-thread.html`
  prints `resize anchor: bottom`.
- `corepack pnpm test:resize-corpus` loads the three in the sandbox and in reference mode
  (no JS errors, pixels within 3 %) → `docs/compat-report-resize.md`; with
  `TW_RENDERER=shadow` on the Shadow DOM renderer.
- The Telegram test group: the **Resize** topic of `scripts/conformance/publish-channel.ts`,
  not in the default run: `--topic resize` (each file's `idea:` line is its description).

Rules: no libraries, no form controls (out of profile 1), theme variables for every colour,
at most 3800 B after compression and 560 px of height at a 320 px wide bubble.
