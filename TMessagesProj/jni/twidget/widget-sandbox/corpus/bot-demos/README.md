# bot-demos — the widget's state and requests, for real

Six widgets that a real bot sends to Telegram (`scripts/bot-demos.ts`, Bot API 10.4), each showing one way a widget
lives in a chat: personal state that never leaves the chat, a shared object every copy follows, a group's list, a game
between two people sent via the bot, a live quote, a leaderboard for everyone; and a seventh, the wallet test, from the
bot on Telegram Serverless. The protocol of each (its `chatData`,
`publicData`, request data and answers) is below; each file's header points here (the bytes go to the widget). The dev app's mock bot plays the same protocol
(`<meta name="tw-bot">`, `apps/web/src/mock-bot.ts`): open them in the `bot-demos/` group of `corepack pnpm dev`.

| File | Bot command | Kind | What it shows | zlib (server) |
|---|---|---|---|---|
| `01-order.html` | `/order` | private chat, `allow_queries` | an order tracker: the items, address and card in `chatData` (never forwarded), the status in `publicData` (a forward shows only that, as of its time, with no progress: a copy never changes); background `order:poll` when it appears, Refresh, Cancel with a second tap; the bot moves the order on every 30 s | 3738 B |
| `02-poll.html` | `/poll [question \| option \| …]`, inline | shared, `allow_queries` | a poll whose question and options are its `publicData`: forwards vote too (`shared_widget_query`), live counts for every copy (`watchPublicData`) | 2851 B |
| `03-rsvp.html` | `/rsvp`, inline | a group, `allow_queries` | who's in: the names in `chatData` (every member sees them), "You" from `viewer`, the count in `publicData` for a forward | 3611 B |
| `04-tic-tac-toe.html` | `/duel`, inline (a chat with a friend) | via the bot, `allow_queries` | a duel: the first two people to move play, "Your turn" from `viewer`, the board in `chatData`, a forward gets the board as of the forward | 3618 B |
| `05-ticker.html` | `/ticker`, inline | shared, no requests | a live quote: the bot edits the shared object every 10 s, every copy follows | 3574 B |
| `06-reaction.html` | `/reaction`, inline | one shared widget for everyone (`sendSharedWidget`) | a reaction test played locally; `score:N` puts your best on one leaderboard shared by every chat, and the answer brings the board at once | 3669 B |
| `07-wallet.html` | `/wallet` (Serverless only) | private chat, `allow_queries` | the wallet (`docs/sdk-v1.md` §2.18) on mainnet: the profile's wallet card, a text the bot gives, signed on the device and checked by the bot, 0.01 TON from the device's wallet to the card's address, then background `w:st` until toncenter has it | 2054 B |

Sizes as the server counts them (PHP `gzcompress($html, 9)`, limit 4096 B; the harness's Node zlib counts a few bytes
more on these). The demos stay inside widget profile 1 and use it as written: where a client draws one wrong, the client
is fixed, not the demo.

## Protocol

| File | `publicData` | `chatData` | Requests → answer (`result`, JSON) |
|---|---|---|---|
| `01-order` | `{shop,e,id,st,eta,u,bot}`: `st` 0 Placed, 1 Cooking, 2 On the way, 3 Delivered, -1 Cancelled; `u` the time it last changed | `{items:[[name,qty,cents]],cur,addr,pay}` | `order:poll` (background), `order:refresh`, `order:cancel` (`st` ≤ 1, a second tap) → `{st,eta}` or `{err}` |
| `02-poll` | `{q,o:[2–6 options],v:[counts],r}` | — | `vote:N` toggles the viewer's vote → `{my:N or -1,v,r}` or `{err}`; `my` is kept in `localStorage` |
| `03-rsvp` | `{t,w,p,max,n}`: the event and how many are in (what a forward shows) | `{in,may,out}`: `[[id,name]]` | `rsvp:in` / `may` / `out` (the same again clears) → `{me}` or `{err}` |
| `04-tic-tac-toe` | `{b,x,o,w,s,bot}`: a forward's snapshot | `{b,x,o,t,w,l,s}`: board, players `[id,name]`, turn, winner `x`/`o`/`d`, its line, score | `ttt:0`–`8`, `ttt:new` (a rematch) → `{ok}` or `{err}` |
| `05-ticker` | `{s,n,c,p,o,h:[the last 24 prices],t,r}`: symbol, name, currency, price, today's open, history, the bot's time | — | none; `watchPublicData()` |
| `06-reaction` | `{top:[[name,ms]],plays,r}` | `{best}`: this chat's best | `score:N` → `{rank,best,top,plays,r}` or `{err}` |
| `07-wallet` | — | `{m,s,t}`: `m` `{a,b}` the card (user-friendly address, nanotons), `s` `{ok,w}` the last signature check, `t` `{h,st,w}` the last transfer (`wait` / `ok` / `fail`) | `w:me` and `w:pay` with `{ wallet: true }` → `{m}` / `{tx}`; `w:text` → `{text}`, then `signData` with `w:sign` → `{s}`; `sendTransaction` with `w:sent` → `{t}`; `w:st` (background, every 5 s while `wait`) → `{t}`; or `{err}` |

`r` is a shared object's revision: the bot raises it on every edit and sends it with the answer too, and a widget keeps
the newest it has seen. A client's poll may bring an older copy back after the answer or a newer poll; without `r`
the counts would jump back and forth. The bot answers every request, also when its edit fails (an edit that changes
nothing is fine), and puts a move or an answer into its state only once the message shows it.

Run the bot: `corepack pnpm tsx scripts/bot-demos.ts` with `~/.config/widget-sandbox/widget-bot.env` (the token, and
`TW_BOT_API=https://api.telegram.org/beta`, `TW_BOT_TEST_ENV=1` for the beta); its state is kept next to that file.
Only one process may read a bot's updates at a time.

The wallet test is in the Serverless bot only (`apps/bot-demos-serverless/tgcloud/lib/wallet.js`): it checks the
signature with Ed25519, ties the key to the address by the wallet's StateInit (v3r2, v4r2, v5r1) and the key on chain,
and finds the transfer by its message hash, both through toncenter on mainnet. Each wallet call goes out on the answer
to the tap's request (the grant): `w:text` → `signData`, `w:pay` → `sendTransaction`. It reads the wallet's fields as
Bot API 10.4 has them (`widget_query.type`: `wallet_card`, `sign_data`, `sent_transaction` with `wallet`), and
`state_init`, which 10.4 does not have yet, by the schema's name; `walletFieldsOf` there, in one place. Until a client
passes `context.wallet`, the widget says it has no wallet.
