# The widget payload: `messageMediaWidget` (layer 233)

A **widget is mini-HTML**: one self-contained HTML document, inline CSS and
JS, `data:` images only, no external resources, of at most **4 KB compressed**
and **32 KB uncompressed**. It travels as message media of its own since API
layer 233 (shipped together with widget-sandbox v0.1.0): the payload is in the
media, also when the widget is a shared object ("The widget's state and
requests", below). The server never runs, parses or renders the HTML: it checks
the bytes and packs them. A widget is fixed code plus changeable state: the HTML
never changes after sending, and with it travel the widget's state, which only
the bot writes, and the flags that say where the widget may ask the bot ("The
widget's state and requests", below), all in layer 233 from the launch.

The layer's widget TL, as the server's final spec of 2026-10-26 has it
(verbatim; the sections below refer to it):

```tl
inputMediaWidget flags:# allow_queries:flags.0?true fwd_allow_queries:flags.1?true shared_widget:flags.2?true width:int height:int html:string public_data:flags.3?bytes chat_data:flags.4?bytes = InputMedia;
inputMediaWidgetId flags:# widget_id:long chat_data:flags.0?bytes = InputMedia;
messageMediaWidget flags:# is_copy:flags.0?true can_query:flags.1?true background_query:flags.2?true width:int height:int payload:bytes public_data:flags.3?bytes chat_data:flags.4?bytes widget_id:flags.5?long = MessageMedia;

inputBotInlineMessageWidget flags:# allow_queries:flags.0?true fwd_allow_queries:flags.1?true shared_widget:flags.2?true width:int height:int html:string public_data:flags.3?bytes chat_data:flags.4?bytes reply_markup:flags.5?ReplyMarkup = InputBotInlineMessage;
inputBotInlineMessageWidgetId flags:# widget_id:long chat_data:flags.0?bytes reply_markup:flags.1?ReplyMarkup = InputBotInlineMessage;
botInlineMessageWidget flags:# width:int height:int payload:bytes public_data:flags.0?bytes chat_data:flags.1?bytes widget_id:flags.2?long reply_markup:flags.3?ReplyMarkup = BotInlineMessage;

updateBotWidgetQuery flags:# query_id:long user_id:long peer:Peer msg_id:int chat_instance:long chat_type:string data:bytes widget_id:flags.0?long = Update;

updateInlineBotWidgetQuery flags:# query_id:long user_id:long inline_message_id:InputBotInlineMessageID chat_instance:long chat_type:string data:bytes widget_id:flags.0?long = Update;

updateBotWidgetCopyQuery flags:# query_id:long user_id:long data:bytes widget_id:long = Update;

updateMessageWidgetData flags:# peer:Peer msg_id:int public_data:flags.0?bytes chat_data:flags.1?bytes pts:int pts_count:int = Update;

messages.botWidgetQueryAnswer flags:# result:flags.0?bytes = messages.BotWidgetQueryAnswer;

messages.widgetPublicDataNotModified period:int = messages.WidgetPublicData;
messages.widgetPublicData hash:long data:bytes period:int = messages.WidgetPublicData;

---functions---

messages.getBotWidgetQueryAnswer peer:InputPeer msg_id:int data:bytes = messages.BotWidgetQueryAnswer;
messages.setBotWidgetQueryAnswer flags:# query_id:long result:flags.0?bytes = Bool;

messages.editMessageWidgetData flags:# peer:InputPeer id:int public_data:flags.0?bytes chat_data:flags.1?bytes = Updates;
messages.editInlineBotMessageWidgetData flags:# id:InputBotInlineMessageID public_data:flags.0?bytes chat_data:flags.1?bytes = Bool;

messages.editWidgetData flags:# widget_id:long public_data:bytes cache_time:flags.0?int = Bool;

messages.getWidgetPublicData peer:InputPeer msg_id:int hash:long = messages.WidgetPublicData;
```

## Sending and delivery

- A bot sends `messages.sendMedia` / `messages.uploadMedia` with
  `inputMediaWidget`: `html` is the UTF-8 source of the widget (required);
  `width` and `height` are the optional size anchors in CSS px, `0` meaning
  "auto". Bots never send compressed bytes. A widget that already exists as a
  shared object is sent by reference instead (`inputMediaWidgetId`, below).
- The server checks the source (valid UTF-8, no NUL bytes, at most 32 KB),
  compresses it itself with zlib at level 9 (PHP `gzcompress($html, 9)`),
  checks the **packed** payload (at most 4 KB) and puts the bytes into the
  message (or into the shared object it makes). No document, no file id: a
  widget is reused only as a shared object, by `widget_id`.
- The client receives `messageMediaWidget`: `payload` is a **zlib stream**
  (RFC 1950: a 2-byte header, raw deflate, a 4-byte Adler-32), **not gzip**
  (RFC 1952, magic `1f 8b`) and not raw deflate (RFC 1951). The client inflates
  it (below) and hands the UTF-8 HTML to `WidgetHost` (`docs/integration.md`).
  The client keeps **no size limits of its own**: the server enforces both, so
  a payload the server produced always decodes to HTML within them, and the sandbox, the validator and the runtime limits
  are the security boundary, not the size.
- The HTML never changes after sending, in a message or in a shared object: no
  edit replaces it (an edit may change the caption, as for any media); a new
  layout is a new widget, and old messages keep the old one. What changes is the
  widget's state, `chat_data` and `public_data`, which only the bot writes ("The
  widget's state and requests"). What the widget keeps for itself lives in the
  message's storage on the device and is deleted with the message
  (`docs/sdk-v1.md` §2.8).

| Error | Cause |
|---|---|
| `INPUT_MEDIA_WIDGET_TOO_LARGE` | HTML over 32 KB, or the compressed payload over 4 KB |
| `INPUT_MEDIA_WIDGET_HTML_INVALID` | not text HTML: broken UTF-8, binary data, compressed bytes sent by the bot |
| `INPUT_MEDIA_WIDGET_NOT_ALLOWED` | the sender is not a bot, the chat is not one a bot may send widgets to (below), or a draft or ephemeral message |

### Inflating the payload

What reads RFC 1950 on each platform, and the traps:

| Platform | Inflate with | Not with |
|---|---|---|
| Web | `new Response(new Blob([payload]).stream().pipeThrough(new DecompressionStream('deflate'))).text()`: `'deflate'` is RFC 1950 | `'gzip'`, `'deflate-raw'` |
| Android, JVM | `java.util.zip.Inflater()` (the default, `nowrap = false`) or `InflaterInputStream` | `GZIPInputStream`, `Inflater(true)` |
| Apple | zlib's `uncompress` / `inflate` (libz) | `Compression`'s `COMPRESSION_ZLIB` and `NSData.decompressed(using: .zlib)`: raw deflate despite the name; with them, skip the 2-byte header and drop the 4-byte trailer |
| Qt (tdesktop) | zlib's `uncompress` / `inflate` | `qUncompress` alone: it expects a 4-byte big-endian length before the stream |
| C, C++ (zlib) | `inflateInit` / `uncompress`; `inflateInit2(&s, 15 + 32)` also accepts gzip, if a client has to read both | `inflateInit2(&s, 31)` (gzip only), `-15` (raw) |

A payload the server produced inflates to at most 32 KB of valid UTF-8, so a
32 KB output buffer is enough; a stream that fails to inflate is a broken
message: show the unsupported-media fallback.

## Who may send, and where it may go

- **Directly**, a bot sends widgets to its private chat with a user and to the
  basic groups and supergroups it is a member of (it needs the right to send
  media: the widget is checked like a photo, `CHAT_SEND_PHOTOS_FORBIDDEN`). The
  allowed chat types are server config; channels are not among them yet.
- **Via the bot** (inline mode): a bot's inline result may be a widget, in the
  same two forms as a direct send: `inputBotInlineMessageWidget` (the HTML, the
  size anchors, the state, `allow_queries` / `fwd_allow_queries` /
  `shared_widget`, `reply_markup`; the server checks the HTML and the state
  once and packs them when it saves the results) or
  `inputBotInlineMessageWidgetId` (a `widget_id`, its own `chat_data`,
  `reply_markup`). The results a client lists are `botInlineMessageWidget`: the
  payload, the size anchors, `reply_markup`, `public_data`, `chat_data` and
  `widget_id` (a result by reference takes the payload, the anchors and the
  shared state from its object and shows its id). A result is not a message, so
  it has no `is_copy` / `can_query` / `background_query`: the client shows it as a
  preview, the widget with its state, no input, no requests, nothing about the
  user (`docs/sdk-v1.md` §2.16, `context.preview`). Each send
  (`messages.sendInlineBotResult`) becomes an ordinary message with `via_bot_id`
  and its own `messageMediaWidget` and `chat_data`; a result with
  `shared_widget` / `fwd_allow_queries` makes a new object on every send, a
  result by reference binds the message to its `widget_id`, and the result's
  flags become the message's. Widget results need a layer-233 client: older ones
  do not get them; a stale result id is `INLINE_RESULT_EXPIRED`.
- **Forwarding is allowed to any chat**, groups and channels included. A
  forward is a copy (`is_copy`): it runs the same HTML (isolated, no network)
  with no chat state (`chat_data`), its own shared state (`public_data`, below)
  and its own storage on the device, which starts empty. A widget is never
  forwarded without its author: the server keeps the forward header even when
  the user asks to hide the sender, so a widget always shows which bot it is
  from (directly or via).
- `copyMessage` by the bot sends a new widget by the same rules: a shared
  widget stays bound to its `widget_id`, a static one gets the HTML and the
  shared state (`public_data`) by value, and the chat state (`chat_data`) is
  never copied.

## The widget's state and requests

Part of layer 233, as the server's final spec of 2026-10-26 describes it. The
widget-facing side is `docs/sdk-v1.md` §2.13–§2.16, the client's
`docs/sdk-v1.md` §5.11 and `docs/integration.md` "The widget's state and
requests"; the framework does not depend on the TL names: it has its own host
interface.

**Fixed code, changeable state.** The widget's code, its HTML, is set when the
bot sends it and never changes. Its state changes, and only the bot writes it.
The widget reads its state and learns of changes; it cannot change it itself,
only ask the bot (a request, below).

**Two kinds of widget.** A **static** widget keeps its HTML and its state in
the message; a forward copies the HTML and the shared state, frozen. A **shared
widget** is a server object, like a poll: the HTML (payload), the size anchors,
the shared state (`public_data`), its refresh `period` and its request flags, in
one object that any number of messages refer to by `widget_id`. The media shows
`widget_id` to everyone, the bot that owns it included; like a poll, the object
outlives every message that refers to it.
One object is one behaviour and one network marker in every carrier: its
flags, layout and size do not differ between messages.

**Two kinds of state.** The chat state, `chat_data`, and the shared state,
`public_data`: each optional, **a text of up to 1024 bytes (UTF-8)** without NUL
bytes (the server checks it; otherwise `*_DATA_TOO_LARGE` / `*_DATA_INVALID`),
written only by the bot: when the widget is made and with the edit methods
below, never in an answer to a request. In an edit a field not passed is
unchanged and an empty string clears it.

- The **chat state** (`chat_data`) is tied to one original message and seen
  only in its chat (the private chat; a group, where all its members share it;
  or the chat of a via-bot message); it is set with each send separately and is
  **never copied** by a forward. For example a game in progress, whose turn it
  is.
- The **shared state** (`public_data`) is seen wherever the widget is shown,
  in the original and in every forward: a static widget keeps it in the message
  (a forward gets a frozen copy as of the forward; an edit changes only the
  original); a shared widget has one shared state, the object's, for all its
  messages and forwards: every read of any carrier gives its current value, and
  an edit changes it for all of them. For example poll results, a price.

**Sending, in two forms** (`inputMediaWidget` / `inputMediaWidgetId` directly,
`inputBotInlineMessageWidget` / `inputBotInlineMessageWidgetId` in inline
results; the TL above):

- **With `html`**, the bot's choices:
  1. `allow_queries`: the widget may ask the bot from its original. No
     whitelist (the permission model of inline buttons: every request names who
     pressed). Without it the server refuses **every** request to the widget
     (`WIDGET_QUERY_NOT_ALLOWED`) and never sets `can_query` or
     `background_query`.
  2. `fwd_allow_queries`: forwards may ask too (on a tap; a copy never asks in
     the background). It implies `allow_queries` and makes the widget a shared
     object (its `widget_id` in the sent media); whitelisted bots only
     (`INPUT_MEDIA_WIDGET_FWD_ALLOW_QUERIES_NOT_ALLOWED`).
  3. `shared_widget`: make the widget a shared object, whatever the requests (a
     ticker without requests is `shared_widget` without `allow_queries`:
     forwards follow its shared state but cannot ask).

  With `shared_widget` or `fwd_allow_queries` the HTML, the size anchors and
  the shared state (`public_data`) go into the new object (the anchors are
  fixed, `public_data` is its first shared state); without them the widget is
  static. The chat state (`chat_data`) stays the message's.
- **With `widget_id`**: a reference to an existing object of the same bot, with
  only the message's own chat state (`chat_data`; and `reply_markup` in an
  inline result). The HTML, the anchors, the shared state and the flags come
  from the object; the constructor has no fields for them, and the send does not
  change the object's shared state. An unknown or another bot's id is
  `WIDGET_ID_INVALID`.

There is no method to make an object: the first send makes it. A bot that
needs a `widget_id` in advance (for a mailing, for inline results) sends the
widget with `shared_widget` or `fwd_allow_queries` to any chat and reads the id
from the sent media. An object's `period` starts at the server's 30 s and changes
only with `editWidgetData` (below).

| sent with | the case | the shared state (`public_data`) | forwards may ask the bot | edited |
|---|---|---|---|---|
| `html`, no flags | a static widget with state | in the message; forwards get a copy as of the forward | – | by the original's coordinates; only the original changes |
| `html` + `fwd_allow_queries` | live forwards (a poll) | an object the server makes; `widget_id` in the media | ✓, on a tap | by id or by coordinates |
| `html` + `shared_widget` | a ticker, one shared state for all | an object; `widget_id` in the media | –; forwards follow its shared state | by id or by coordinates |
| `html` + both | a live ticker | an object | ✓, on a tap | both |
| `widget_id` | a mailing, one object reused | the object's, unchanged by the send | as the object's flags say | by id |

**Who may request** (the server computes the flags; the client passes them on
and the framework derives nothing). For a widget with `allow_queries` (a static
widget's message, a shared widget's object; copies: also
`fwd_allow_queries`, which a static widget never has); without
`allow_queries`, `can_query` and `background_query` are never set:

| message | `is_copy` | `can_query` | `background_query` |
|---|:-:|:-:|:-:|
| the original in the private chat with the bot | – | ✓ | ✓ |
| an original via the bot (in any chat) | – | ✓ | – |
| an original in a group the bot sent it to | – | ✓ | – |
| a forward of a `fwd_allow_queries` widget (in any chat) | ✓ | ✓ | – |
| a forward of any other widget | ✓ | – | – |

The framework's policy from these flags: a request on a tap whenever
`can_query`; without a tap only with `background_query`: one when the widget
appears on screen, then at most one per 5 s while it stays visible; a copy
never has a chat state and is told nothing about its chat (no chat type, no
chat instance); the sandbox never goes to the network itself. **The network
marker:** where `can_query` is set, the client marks the widget as one whose
tap may send data to the bot's server; without it there is no marker, and the
server guarantees no request can go out (`docs/integration.md`, "The network
marker"). Widgets with the same `widget_id` on screen are one widget: a client
may cache the checked payload and the shared state per `widget_id`.

**A request** is a callback button inside the widget:
`messages.getBotWidgetQueryAnswer` (the client's call, not the Bot API's) with
the message in the user's own coordinates and `data`, answered with
`messages.botWidgetQueryAnswer`. `data` is 1–64 opaque bytes
(`WIDGET_QUERY_DATA_INVALID` otherwise); at most **5 requests per 5 s** per user
and bot, as for callback buttons (`FLOOD_WAIT`); the server waits for the bot
15 s (`WIDGET_RESPONSE_TIMEOUT`); a widget without `allow_queries`, or a copy of
one without `fwd_allow_queries`, is `WIDGET_QUERY_NOT_ALLOWED`, an unknown
message `MESSAGE_ID_INVALID`. The server does not tell a tap from a background
request: that is the client's policy.

The bot gets one of three updates, by where the widget was tapped; the Bot
API has one `widget_query`, shaped like `callback_query`, with the fields
filled per kind:

| update (MTProto) | tapped in | the bot gets |
|---|---|---|
| `updateBotWidgetQuery` | an original (the private chat with the bot, a group it is in) | `from`, the message in the bot's coordinates with its current state, `chat_instance`, the chat type, `data`, `widget_id` when the widget is shared |
| `updateInlineBotWidgetQuery` | a via-bot message | `from`, `inline_message_id`, `chat_instance`, the chat type, `data`, `widget_id` when shared |
| `updateBotWidgetCopyQuery` | any forward | **only** `from`, `data` and `widget_id`: no message, no `chat_instance`, no chat type: the bot learns nothing about where it was forwarded |

A copy's `widget_id` is always there (a live forward exists only for a
`fwd_allow_queries` widget, which is shared) and the server takes it from the
tapped message, so it cannot be forged: the bot counts "one vote" by
(`widget_id`, `from`). The bot answers by the query's id
(`answerWidgetQuery { widget_query_id, result? }`, MTProto
`messages.setBotWidgetQueryAnswer`), also after the original is deleted:
`result` at most 1024 bytes of text (`WIDGET_RESULT_TOO_LARGE`,
`WIDGET_DATA_INVALID`), only for the client that asked; none is an empty
answer. **An answer carries no state, no toast, alert, URL or `cache_time`.**

**The bot's edits of the state**, all silent (no "edited" mark, no
notification):

- `editMessageWidgetData` (`messages.editMessageWidgetData`): by the
  original's coordinates as the bot knows them, only the bot that sent it and
  only the original; `public_data` and/or `chat_data` (a `public_data` edit of
  a shared widget writes its object). A message's state edits go through the
  flood control of `sendMessage`, each counted as one message.
- The same with `inline_message_id` for a via-bot message
  (`messages.editInlineBotMessageWidgetData`): `chat_data` is allowed there, a
  via-bot message is an original.
- `editWidgetData` (`messages.editWidgetData`): an object by its `widget_id`,
  which works always, even when every carrier is deleted or unknown to the bot;
  its optional `cache_time` is the only way to set the object's `period` (0 sets
  it back to 30 s). At most one edit per second per object.

A change of the chat state (`chat_data`) reaches every holder of the message as
a light update without the HTML (`updateMessageWidgetData`). In private chats
and basic groups it comes in the owner's pts sequence, as `updateEditMessage`
does; in a supergroup the post goes through the usual edit of channel posts,
with the channel's pts (`updateEditChannelMessage` and the like). The client
applies it by the rules of that pts sequence.

A shared widget's shared state (`public_data`) reaches its carriers by
polling, **only for widgets that ask to follow it live**: the messenger (never
the sandbox) polls a widget on screen that called
`TelegramWidget.watchPublicData()` (the host reports it: `docs/integration.md`);
every other widget gets its shared state with the message (and a static
widget's edits by the update above). A bot may make a
widget shared only to send it by `widget_id`, and polling every such card would
ask the server for nothing (the proposal to the server of 2026-10-26, agreed
with Artem; the protocol does not change, the client's policy does). The
messenger calls `messages.getWidgetPublicData` for **one** message at a time,
with the hash of the shared state it has (0 at first), and applies a new one to
every widget on screen with the same `widget_id` (the shared state is the
object's, so one visible carrier per object is enough):

- `widgetPublicDataNotModified { period }`: the message has no object (a
  static widget always answers so; `period` is then the server's 30 s) or
  the hash matched (the object's `period`);
- `widgetPublicData { hash, data, period }`: the object's current shared state.

`period` is the recommended cadence of the poll, not a delay: an edit is
visible at once to every read (history, the update, the poll). It is the
server's: the bot's `cache_time` clamped to 5–3600 s, 30 s without one (the
server's numbers of 2026-10-26, after the final spec's 2–3600 s and 5 s); the
next poll is planned by it (30 s before the first answer). The client
does not poll after an answer: a bot that wants the widget to have the new
state at once returns it in `result`.

| Error | Cause |
|---|---|
| `INPUT_MEDIA_WIDGET_DATA_TOO_LARGE`, `INPUT_MEDIA_WIDGET_DATA_INVALID` | `chat_data` / `public_data` at sending or in an inline result: over 1024 bytes, or not UTF-8 text (NUL bytes) |
| `WIDGET_DATA_TOO_LARGE`, `WIDGET_DATA_INVALID` | the same in an edit; `WIDGET_DATA_INVALID` also for a `result` that is not text |
| `WIDGET_RESULT_TOO_LARGE` | `result` over 1024 bytes |
| `WIDGET_QUERY_DATA_INVALID` | `data` empty or over 64 bytes |
| `WIDGET_QUERY_NOT_ALLOWED` | the widget has no `allow_queries`, a forward of a widget without `fwd_allow_queries`, or requests are not allowed there |
| `WIDGET_RESPONSE_TIMEOUT` | the bot did not answer in 15 s |
| `WIDGET_ID_INVALID` | an unknown `widget_id`, or another bot's object |
| `INPUT_MEDIA_WIDGET_FWD_ALLOW_QUERIES_NOT_ALLOWED` | `fwd_allow_queries` from a bot outside the whitelist (at sending or in an inline result) |
| `CHAT_SEND_PHOTOS_FORBIDDEN` | the bot may not send media in that group |
| `MESSAGE_ID_INVALID` | no such widget message, or not in this chat, or not this bot's, or the original is gone |
| `QUERY_ID_INVALID` | the `widget_query_id` is unknown, expired (15 s) or of another kind |
| `INLINE_RESULT_EXPIRED` | a widget result sent by an invalid or expired id (as for any inline result) |
| `FLOOD_WAIT_<N>` | more than 5 requests per 5 s per user and bot; a message's state edits over the `sendMessage` flood control; more than one object edit per second |

**Old clients.** Below layer 233: `messageMediaUnsupported` with the caption,
as for any widget, and no widget results in inline lists.

## The Bot API (10.4)

Bots see the same widget under Bot API names (checked against the beta,
`https://api.telegram.org/beta/bot<token>/test/<method>`, on 2026-10-28;
`scripts/conformance/test-bot.ts` speaks it):

| Layer 233 (this document) | Bot API 10.4 |
|---|---|
| `inputMediaWidget` | `sendWidget`; in inline mode an article with `InputWidgetMessageContent` |
| `allow_queries` | `allow_queries` |
| `shared_widget` | `is_shared` (the sent `Widget` has `id`) |
| `fwd_allow_queries` | none of its own: a widget sent with `is_shared` and `allow_queries` is one whose forwards may ask |
| `inputMediaWidgetId`, `inputBotInlineMessageWidgetId` | `sendSharedWidget`, `InputSharedWidgetMessageContent` |
| `messageMediaWidget`: `is_copy`, `can_query`, `background_query` | `Widget`: `is_copy`, `can_send_queries`, `can_send_background_queries` |
| the request from an original (also via the bot) | update `widget_query`: `id`, `from`, `chat` and `message_id` or `inline_message_id`, `chat_instance`, `chat_type` (via the bot), `data`, `widget_id` (a shared widget's) |
| the request from a copy of a shared widget | update `shared_widget_query`: `id`, `from`, `data`, `widget_id` |
| `result` | `answerWidgetQuery`'s `data` |
| the message's state edit | `editMessageWidgetData` (`chat_id` + `message_id` or `inline_message_id`) |
| `messages.editWidgetData` | `editSharedWidgetData` (`widget_id`, `public_data`, `cache_time`) |

What the beta does: the HTML is refused past 32768 bytes or past 4096 bytes of
PHP `gzcompress($html, 9)` (`INPUT_MEDIA_WIDGET_TOO_LARGE`; a Node build's own
zlib compresses random text about 18 bytes tighter, `src/shared/widget-size.ts`);
`chat_data`, `public_data` and the answer's `data` take 1024 bytes each, counted
in bytes; `cache_time` is clamped to 3600 above but 4 is refused; `copyMessage`
of a widget is refused; a forward carries `is_copy` and the `public_data` of the
moment, no `chat_data`; a forward of a shared widget keeps its `id` and
`can_send_queries`. `can_send_background_queries` was not set on any message
yet, the private chat with `allow_queries` included. An `editMessageWidgetData`
that changes nothing is refused with `message is not modified`, as
`editMessageText` is; a bot takes it as done and still answers the query (a
query left unanswered ends in the widget as `TIMEOUT`).

## Caption, preview, old clients

The message's ordinary media caption is the text for the preview, the chat
list and search; without a caption the client shows the localized "Widget"
stub, as for other media. Clients below layer 233 receive
`messageMediaUnsupported` with the caption text and show their usual
placeholder; a new client keeps that fallback for messages it decides not to
render.

The server has no flag for widgets: each client enables rendering itself, by
platform and version (a debug or rollout flag on the client side).

## Size anchors

`width` and `height` are always present in the media; `0` means auto, and
each axis is independent. The server passes them through unchanged; the client
keeps them within sensible bounds. How the client turns them into the bubble
before the widget has loaded is in `docs/integration.md` ("Bubble sizing").

## Writing a mini-widget

4 KB compressed is about 12–20 KB of ordinary HTML, CSS and JS: a card with a
few controls, a chart drawn on a canvas, an animation. What does not fit:
embedded images beyond tiny icons (a `data:` PNG compresses badly), bundled
libraries, long text. Widget profile 1 (`docs/profile-v1.md`) and SDK v1
(`docs/sdk-v1.md`) exist partly to keep widgets small: the client provides
the theme, the button style and the APIs every widget would otherwise embed,
and there are no bundled libraries.

The server compresses with zlib at level 9 (PHP `gzcompress($html, 9)`), so an
author can measure what it counts:
`python3 -c 'import sys,zlib; print(len(zlib.compress(sys.stdin.buffer.read(), 9)))' < widget.html`
(`gzip -9 -n` counts about 12 bytes more: its header and trailer are longer), the
harness's `size:` line or the dev app's stats panel (both warn above 3800 B,
the margin the conformance set keeps, and flag anything over a limit). A bot
that gets `INPUT_MEDIA_WIDGET_TOO_LARGE` for HTML under 32 KB has hit the
compressed limit.
How to write widgets that fit, and everything else an author needs:
`docs/widget-runtime-profile.md`.

## Testing

Widgets come only as `messageMediaWidget`: an `.html` a user uploads as a
document is a file, not a widget. Testing goes through real layer-233
messages, the conformance set included. Every conformance widget fits a mini-widget (`packages/conformance`
builds each one under 3800 bytes after zlib at level 9). A test bot sends
it to a tester's private chat with its reference picture as a photo next to
it, in the order of `packages/conformance/channel.json`, and the messages are
forwarded into the shared test channel. That also covers the corpus widgets
that fit, forward-copy storage and the size anchors. The channel holds
forwards, so there every widget is a copy (`is_copy`; requests only for a
widget sent with `fwd_allow_queries`): requests in the background and the chat
state are tested in the tester's private chat with the bot, and a group's in a
test group the bot is a member of.

## Profile and versions

The HTML is written against widget profile 1 (`docs/profile-v1.md`;
`packages/core/profile.json` is the source). A widget learns the client's
profile at runtime through `TelegramWidget.profile`, `isProfileAtLeast(n)`,
`CSS.supports` and `@supports`, all answered from the same tables. The payload
itself has no version field: the profile is the framework build's, the media
layout is the layer's.
