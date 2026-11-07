// What the dev app's bubbles show when they take a press (WidgetHost `bubbleGestures`, docs/sdk-v1.md §5.5): a short
// label over the bubble, as a client would open its menu or set the reaction. Shared by the single-widget view and the feed.

import type { BubbleGesture, GestureClaim } from '@telegram/widget-sandbox/host/widget-host';

/** `?gestures=`: `phone` (default) arbitrates the mouse as a finger, `desktop` as a mouse, `off` gives the widget every press. */
export type GestureMode = 'phone' | 'desktop' | 'off';
export const GESTURE_MODES: { mode: GestureMode; title: string }[] = [
  { mode: 'phone', title: 'the bubble of a phone client: a vertical drag is always the list scroll; a hold is the message menu and a horizontal drag swipe-to-reply unless touch-action claims them (the mouse arbitrates as a finger); taps and double taps are the widget\'s' },
  { mode: 'desktop', title: 'the bubble of a desktop client: a right click is the message menu unless touch-action: none claims it; clicks, double clicks and drags are the widget\'s' },
  { mode: 'off', title: 'no bubble: the widget gets every press (what no client does; for comparison only)' },
];
export const gestureMode = (v: string | null): GestureMode => (v === 'desktop' || v === 'off' ? v : 'phone');
export const bubbleGesturesOption = (mode: GestureMode) => (mode === 'off' ? false : { touch: mode === 'phone' });

const LABELS: Record<BubbleGesture['action'], string> = {
  menu: '⋯ message menu',
  swipe: '↩ swipe: reply',
  scroll: '↕ list scroll',
  back: '← back',
};

export const bubbleGestureLabel = (g: BubbleGesture) => LABELS[g.action] + (g.edge ? ' (edge strip)' : '');

/** `auto`, `none`, `pan-y`: the claim as the touch-action that makes it (no claim has vertical drags since v0.1.3). */
export function claimLabel(c: GestureClaim): string {
  if (c.hold) return 'none (x + hold)';
  if (c.x) return 'pan-y (x)';
  return 'auto';
}

/** A label over `bubble` for a moment, the newest on top. */
export function showBubbleGesture(bubble: HTMLElement, g: BubbleGesture) {
  const tag = document.createElement('div');
  tag.className = 'bubble-gesture';
  tag.textContent = bubbleGestureLabel(g);
  bubble.querySelector('.bubble-gesture')?.remove();
  bubble.appendChild(tag);
  setTimeout(() => tag.remove(), 1200);
}
