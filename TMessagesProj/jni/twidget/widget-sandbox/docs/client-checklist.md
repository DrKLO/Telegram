# Client checklist for widgets v0.1.0

One page for a client team. Each box links to where the detail is:
`docs/integration.md` (the host), `docs/sdk-v1.md` §4–5 (the SDK and what the
client implements), `packages/conformance/CLIENTS.md` (the conformance set),
`docs/tgw-format.md` (the message media).

## a. Adopt the framework

- [ ] Pin the tag `v0.1.0`, never a branch ([integration.md, "Pinning a release"](integration.md#pinning-a-release)).
- [ ] Create one `WidgetHost` per widget on your platform; policy `strict`
      (the default), `features` as the profile sets them, today `["webAnimations"]`
      ([integration.md, "Policy and features"](integration.md#policy-and-features); the per-platform
      API in [sdk-v1.md §4](sdk-v1.md#4-host-interface-per-platform)).
- [ ] `setVisible(false)` off screen, `setTheme` on theme change, `destroy()` on
      recycle ([integration.md](integration.md)).

## b. PoC on real messages (layer 233) from a test bot

- [ ] Render `messageMediaWidget`: inflate `payload` as **zlib (RFC 1950), not gzip**, no size limit
      of your own ([tgw-format.md, "Inflating the payload"](tgw-format.md#inflating-the-payload), [integration.md, "The widget payload"](integration.md#the-widget-payload)).
- [ ] Enable rendering with your own platform/version flag; without it, and on
      old layers, the message shows as unsupported media with its caption.
- [ ] Bubble sizing from the anchors: width 0 = the bubble's width, W = a fixed width in
      [200, max]; height 0 = placeholder, then the content height in [50, 600] cached per
      message, H = fixed clamp(H, 50, 600); hard clip at the bubble's height, no fullscreen
      ([integration.md, "Bubble sizing"](integration.md#bubble-sizing), [sdk-v1.md §5.6](sdk-v1.md#56-bubble-size)).
- [ ] Taps only reach the widget; no keyboard input, no focus; text is not selectable
      unless the widget sets `user-select: text`, and then it is selected the way your
      client selects message text and copied with your copy command
      ([integration.md, "Input"](integration.md#input), [sdk-v1.md §5.10](sdk-v1.md#510-text-selection-v011)).
- [ ] Off-screen pause: frames stop, timers at 1 Hz when the bubble leaves the screen.
- [ ] A killed widget keeps its last frame with the "Widget stopped" badge; the
      bubble does not collapse ([sdk-v1.md §5.7](sdk-v1.md#57-failures-and-restarts-d22)).
- [ ] Caption as the preview text; the localized "Widget" stub without one.
- [ ] With no caption below the widget (none, or the caption shown above it, `invert_media`), the message
      time over the widget's bottom corner on a backing (right, or left in a right-to-left interface), taps
      on it yours; its band and corner as the host's `contentSafeArea` at creation, `setContentSafeArea`
      only when the bubble's layout changes; with a caption below all 0
      ([integration.md, "The time over a widget with no caption below it"](integration.md#the-time-over-a-widget-with-no-caption-below-it),
      [sdk-v1.md §5.12](sdk-v1.md#512-the-time-over-a-widget-with-no-caption-below-it-217)). `visual/17-time-corner`,
      `corpus/safe-area`.
- [ ] A test bot sends widgets (≤ 4 KB compressed, every conformance widget included) to a
      tester's private chat; forward them into the shared test channel (bots may send
      widgets only to private chats).

## c. SDK v1

- [ ] Theme: the palette of the bubble the widget sits in (an outgoing bubble's when
      the user sent or forwarded it), the button tokens and the text tokens into
      `WidgetTheme`, updated on theme change; the widget view stays transparent over
      the bubble, which is its canvas ([sdk-v1.md §5.1](sdk-v1.md#51-theme)).
- [ ] Text tokens (v0.1.1): `fontSize` follows the chat's text-size setting and
      `setTheme` runs when the user changes it; `lineHeight` and `padding` match your
      message text. `visual/14-unstyled-document` and `corpus/defaults` look like your
      own messages ([sdk-v1.md §2.4](sdk-v1.md#24-the-renderers-defaults-the-root-the-text-button)).
- [ ] A native renderer (not a WebView) puts the profile's UA layer into its master CSS,
      sets the theme variables on the root, propagates a body background to the whole
      bubble and honours `user-select: text` (`docs/decisions.md`, "The renderer's
      defaults", "Text selection").
- [ ] Links: `openLink` / `openTelegramLink` only from a real tap; `openLink` with a
      confirmation showing the full URL, then open; `openTelegramLink` straight into the
      client's `t.me` handling, no confirmation of its own ([sdk-v1.md §5.2](sdk-v1.md#52-links)).
- [ ] Haptics with the rate limit ([sdk-v1.md §5.3](sdk-v1.md#53-haptics)).
- [ ] Storage per (account, chat, message id) on disk, preloaded at creation,
      committed from `onStorageCommit`, deleted with the message, the chat, the
      account and on "Clear cache" ([sdk-v1.md §5.4](sdk-v1.md#54-storage)).
- [ ] Gestures by the shared policy (v0.1.2): the bubble's recognizers driven by
      `GestureArbiter` and the claim from `onGestureClaim` / `onGesture`; the menu,
      swipe-to-reply and list scroll over elements that do not claim the press, the
      widget's touch cancelled (`pointercancel`) when the bubble takes it; nothing taken
      over `touch-action: none`; no double-tap reaction over the widget (double taps are
      always the widget's); the leading 20 px never claimed
      ([sdk-v1.md §5.5](sdk-v1.md#55-gestures-and-drag-d4); conformance `auto/23-gestures-a` / `-b`,
      `visual/sdk/12-drag-gestures`).
- [ ] A widget stays stopped after 2 crashes in a row ([sdk-v1.md §5.7](sdk-v1.md#57-failures-and-restarts-d22)).
- [ ] A forwarded copy works like the original with its own, empty storage. The HTML
      of a sent widget never changes: no edit replaces it (only the caption can be
      edited), so there is no host to re-create for an edit.

## d. The widget's state and requests

- [ ] Create the host with the message's chat state (`chat_data`) and shared state
      (`public_data`) as `data` and the server's flags as `context`: `isCopy` =
      `is_copy`, `canQuery` = `can_query`,
      `backgroundQuery` = `background_query`, read from `messageMediaWidget`, never
      derived; the chat type and the account's user id as `viewer` (the framework keeps it
      from a widget without `can_query`)
      ([integration.md, "The widget's state and requests"](integration.md#the-widgets-state-and-requests),
      [sdk-v1.md §5.11](sdk-v1.md#511-the-widgets-state-requests-and-copies)).
- [ ] The network marker: a sign in the bubble, "a tap may send data to the bot's
      server", exactly when the message has `can_query`; none without it, none on a
      preview ([integration.md, "The network marker"](integration.md#the-widgets-state-and-requests)).
- [ ] `updateMessageWidgetData`: applied by its pts sequence (the account's in private
      chats and basic groups; in a supergroup the channel's, through the usual edit of
      channel posts), then `setData` with the full state on the live host; keep it with
      the message.
- [ ] A shared widget (`widget_id` in the media) that watches (`onWatchPublicData` /
      `watchesPublicData`): poll `messages.getWidgetPublicData` for one visible carrier per
      id, one message per call with its last hash, again after the `period` each answer
      carries (5–3600 s, 30 s by default, also before the first answer); a widget
      that does not watch is never polled; apply a new
      value with `setData` to every widget on screen with that id, forwards included.
      Optionally cache the checked payload and the shared state per `widget_id`.
- [ ] `onRequest`: `messages.getBotWidgetQueryAnswer` with the widget's `data`; complete
      once with `{ result }`, or `timeout` (`WIDGET_RESPONSE_TIMEOUT`), `rateLimited`
      (`FLOOD_WAIT`), `failed` (anything else). Show nothing for an answer and do not
      re-fetch the state after it.
- [ ] `onCopyText`: copy the text and show a toast with the text itself.
- [ ] Groups: widgets the bot sends to a group show and ask on a tap; via-bot results
      are sent as widgets; forwarding a widget always keeps its author.
- [ ] Inline results: a widget result in the list (`botInlineMessageWidget`) is a preview
      (`context.preview`, the result's state, `chat_data` / `public_data`, no flags, no marker):
      no touches routed to it, a tap selects the result; the sent message gets a new
      host ([sdk-v1.md §2.16](sdk-v1.md#216-previews)).

## e. Test

- [ ] The conformance automatic set on your renderer, with the report
      ([CLIENTS.md](../packages/conformance/CLIENTS.md)).
- [ ] The attack corpus (`corpus/attacks`): 0 leaks, 0 outside requests.
- [ ] The whole conformance set in the test channel as real layer-233 messages from
      the test bot, in the order of `packages/conformance/channel.json`, each widget
      with its reference picture posted next to it (`visual/` and `visual/sdk/`;
      the automatic widgets report their own checks). Go through the visual ones with
      the manual checklist: light and dark themes, rotation, system font size, an RTL
      locale, reduce motion, bubble re-creation on scroll, forward, a caption edit.
- [ ] The widget's state and requests with a test bot: in the private chat (a tap, a
      background request when the widget appears and every 5 s while it is on screen,
      `editMessageWidgetData` without a restart, the network marker), a widget sent
      without `allow_queries` (no requests, no marker), in a group (taps only, one
      chat state for all members), a forward of a `fwd_allow_queries` widget (taps
      only, no chat state, the marker), a forward without it (no requests, no marker),
      a shared widget's shared state changing in the original and its forwards together.
- [ ] A feed bench on a low-end device: many widgets in a list, scroll, memory.

## f. Send back

- [ ] The conformance report (`<platform>-<renderer>.json` and `summary.md`) and
      every discrepancy you found, to the widget-sandbox maintainers or as a PR
      into `packages/conformance/results/`.
