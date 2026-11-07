# interactive — the widget's state and requests

Five widgets on the widget's state and requests (`docs/sdk-v1.md` §2.13–§2.15, the wallet: §2.18): the HTML stays as
the bot sent it, the state changes and only the bot writes it (the chat state in `chatData`, the
shared state in `publicData`), and a tap in the widget is a request to the bot (a callback button
inside the widget). Each names the dev app's mock bot
that plays its bot (`<meta name="tw-bot" content="…">`, `apps/web/src/mock-bot.ts`): open it in
the dev app (`corepack pnpm dev`, the `interactive/` group) and watch the **Bot (mock)** panel;
its **message** select sets where the widget is (the private chat, a group, via the bot, an
original sent without `allow_queries`, a live forward, a plain forward, a preview) and so what it
may ask; the bubble's header shows the network marker where it may.

| File | Bot | What it shows | Try | Size |
|---|---|---|---|---|
| `01-poll.html` | `poll` | the counts in the shared state, `publicData` (forwards see them too; the poll calls `watchPublicData()`, so the client polls them while it is on screen), who voted in the chat state, `chatData` (the original only); the viewer's own vote marked from `viewer.id`, or in a forward from the bot's `result` | vote, vote again to take it back; edit `publicData` in the panel (others voted): the bars move, nothing restarts; set **message** to live-copy (votes on a tap) and copy (no votes) | 1574 B |
| `02-tic-tac-toe.html` | `tictactoe` | one game in the chat state (`chatData`), the bot's move comes as its edit of the state, then the answer; a finished game goes to the shared state (`publicData`) | play to the end, then set **message** to copy: the forward shows the last game and offers no moves | 1305 B |
| `03-live-ticker.html` | `ticker` | background requests: a price at once when it appears on screen, then every 5 s while visible, only where the server allows it (the private chat with the bot); elsewhere a tap refreshes, and a plain forward cannot | set **message** to group: it asks for a tap; to copy: refreshing says to open the bot; set **answers** to `TIMEOUT`; scroll it away and back | 1536 B |
| `04-promo.html` | `promo` | `copyText` on a tap (the client's toast shows what was copied); redeem once, kept in the chat state (`chatData`); the bot's `result` says how it went | copy, redeem twice; in a plain forward the code still copies and redeeming says to use the bot | 1108 B |
| `05-wallet.html` | `shop` | the wallet (§2.18): a tap asks the bot with `{ wallet: true }`, the bot answers with a fresh transaction, the widget passes it to `wallet.sendTransaction` in the answer's handler (no new tap), the dev app's mock wallet window confirms it, the bot's answer and its `chatData` edit say what was paid; `signData` for the terms; `wallet` is `null` without a wallet or where the widget may not ask | pay, confirm; pay and decline (`REJECTED`); sign the terms; untick **wallet** in the panel or set **message** to copy: it says to pay in the bot | 1515 B |

Sizes as the harness counts them (`size:` line, limit 4096 B). From a shell:
`corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/interactive/01-poll.html --message private --public-data '{"v":[3,5,1]}' --viewer 1001 --width 360 --tap 60,50 --answer '{"result":"{\"voted\":0}"}' --set-data '{"chatData":"{\"u\":{\"1001\":0}}","publicData":"{\"v\":[4,5,1]}"}'`.

## Notes for authors

- Draw from the state, every time: one `render()` that reads `chatData` / `publicData` (in a
  `try` around `JSON.parse`), called at start and on `chatDataChanged` / `publicDataChanged`.
  The answer carries no state: the bot's edits arrive as the events, before or after it; what the
  widget needs at once (the user's own vote) the bot returns in `result`.
- One request at a time, and handle every error code. A forward has no chat state; it may ask on
  a tap only if the bot sent the widget with `fwd_allow_queries`: on `NOT_ALLOWED` say so and point to the bot.
- Background requests are for the private chat with the bot only: start them, and on
  `NOT_ALLOWED` fall back to a tap.
