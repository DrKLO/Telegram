// Vertical drags are the chat's (gesture-policy.ts, docs/sdk-v1.md §5.5, v0.1.3): the
// engine under the renderer must never stop a vertical pan either, or a browser on a
// touch screen (the web client on a phone) would keep the page from scrolling over a
// widget whose CSS says `touch-action: none` or `pan-x`. The renderer core gives the
// engine the widened value and keeps the widget's own in `TOUCH_ACTION_CLAIM`, which
// the claim reads. Pure; the renderer core's only.

/** The custom property that keeps the widget's own `touch-action` next to the engine's (it inherits, so an ancestor's value reaches its descendants, as `claimOf` reads the chain). */
export const TOUCH_ACTION_CLAIM = '--tw-touch-action';

const VERTICAL = new Set(['pan-y', 'pan-up', 'pan-down']);

/**
 * The `touch-action` the engine gets for the widget's `value`: vertical panning in both directions is always allowed.
 * `auto`, `manipulation`, the CSS-wide keywords and a value with `pan-y` stay; `none` becomes `pan-y`; `pan-up` and
 * `pan-down` become `pan-y`; any other value gains `pan-y` (`pan-x` → `pan-x pan-y`, `pinch-zoom` → `pinch-zoom pan-y`).
 */
export function engineTouchAction(value: string): string {
  const words = value.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return value;
  if (words.length === 1 && ['auto', 'manipulation', 'inherit', 'initial', 'unset', 'revert', 'revert-layer', 'pan-y'].includes(words[0])) return value;
  if (words.length === 1 && words[0] === 'none') return 'pan-y';
  if (words.includes('pan-y')) return value;
  return [...words.filter((w) => !VERTICAL.has(w)), 'pan-y'].join(' ');
}
