# Reply Thread (Telegram Android)

Tapping a reply quote no longer just jumps. A thread of light grows out of the quote, travels through the left
gutter of the chat and ties itself to the original message while the list scrolls there. Pressing the ↓ button
after a jump runs the thread backwards, from the original to the message you came from.

## What you see
- **Living rope.** The thread is a damped wave on a cubic Bézier guide (36 nodes, fixed 1/120 s step) with bending
  stiffness, viscosity and inertia taken from the real motion of the anchors. Flinging the list makes it sway,
  and a soft limit keeps it hugging the gutter on long scrolls.
- **Tie-off.** When the head arrives and the list has settled, a snap wave runs back along the thread, a knot pops on a
  spring, two rings pulse, 14 sparks fly and a haptic tick fires.
- **Reverse thread.** The ↓ return button plays the same effect from the original back to the source.
- **Chain mini-map.** A thin rail on the right edge shows the whole reply chain (up to 8 messages) as dots plus the
  current viewport, so you can see where you are in a long conversation.
- Colours come from the current theme (`key_chat_messagePanelSend`); no allocations in `draw`.

## Files
- `TMessagesProj/src/main/java/org/telegram/ui/Components/ReplyThreadDrawer.java`: all logic and rendering
  (not a View; it draws on top of the chat list).
- `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`: integration (~40 lines):
  1. field `replyThreadDrawer`;
  2. `replyThreadDrawer.draw(canvas)` at the end of the chat list `dispatchDraw`;
  3. `replyThreadDrawer.onScrolled(dy)` in the scroll listener;
  4. `startReplyThread(cell, id)` in `didPressReplyMessage` (same-chat branch) plus its helper with the
     `ChainProvider` that supplies the reply chain and viewport;
  5. `startReverse(id)` in `onPageDownClicked()` when returning to a message.

## How it works
Both ends are looked up among visible cells by message id on every frame, so the thread stays glued to the
messages even during the fast-scroll animation (RecyclerAnimationScrollHelper moves cells with translation). If a
message is off-screen the end runs off the edge and the source is extrapolated from `dy`.

## Build and try
`.github/workflows/build-apk.yml` builds an arm64 debug APK on GitHub Actions (package `org.telegram.messenger.dxrk`,
so it installs next to the official app). Open any chat with replies and tap a quote.

Based on DrKLO/Telegram (GPLv2).
