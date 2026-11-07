// The stylesheet of a reference rendering: the widget file drawn natively by a
// browser engine, without the sandbox (the dev app's ?reference=1, the native
// demos' reference mode, the conformance set's Chromium runs). Dev tooling,
// never a product path. It carries what the renderer itself adds to every
// widget: the UA layer of the profile (profile.json `uaStylesheet`) and the
// theme variables, here with the default palettes and the reference text
// tokens (DEFAULT_TEXT_TOKENS), so a reference picture shows what the profile
// removes, not a different root style.

import { PROFILE_UA_STYLESHEET } from './profile-tables.ts';
import { checkContentSafeArea, contentSafeAreaVariables, themeVariables, type ContentSafeArea } from './sdk-policy.ts';

const declarations = (vars: Record<string, string>) => Object.entries(vars).map(([k, v]) => `${k}:${v}`).join(';');
const rootVars = (scheme: 'light' | 'dark') => declarations({ ...themeVariables({ colorScheme: scheme }), ...contentSafeAreaVariables(null) });

/**
 * The UA layer, then the reference theme on `:root` for both colour schemes (`prefers-color-scheme` picks one). Not
 * `!important` (the renderer's are): a reference harness that sets its own palette inline still wins.
 */
export const REFERENCE_STYLESHEET =
  `${PROFILE_UA_STYLESHEET}:root{${rootVars('light')}}@media (prefers-color-scheme: dark){:root{${rootVars('dark')}}}`;

/**
 * The reference chrome over a widget with no caption below it (docs/sdk-v1.md §2.17): the band of the message time at the
 * bottom and the corner it takes, in CSS px, as the dev app's "time" control and the conformance pictures draw it. A
 * client passes its own numbers; these are what Telegram's time over a photo takes, about.
 */
export const REFERENCE_TIME_CORNER = { bottom: 24, corner: 64 } as const;

/** The reference content safe area: the time over the right corner (a left-to-right interface) or the left one. */
export function referenceSafeArea(rtl = false): ContentSafeArea {
  const { bottom, corner } = REFERENCE_TIME_CORNER;
  return checkContentSafeArea({ bottom, ...(rtl ? { cornerLeft: corner } : { cornerRight: corner }) });
}

/** The root variables of a content safe area for a reference rendering (after REFERENCE_STYLESHEET, which sets them to 0). */
export const referenceSafeAreaCss = (area: Partial<ContentSafeArea>) => `:root{${declarations(contentSafeAreaVariables(area))}}`;

/**
 * The message time as a client draws it over the widget's corner, for the dev app and the reference pictures: a
 * backing as over a photo, in the corner box (`cornerRight` or `cornerLeft` wide, `bottom` tall) of the bubble whose
 * positioned box contains it. Inline style for an absolutely positioned element; empty when there is no corner.
 */
export function timeCornerStyle(area: Partial<ContentSafeArea>): string {
  const a = checkContentSafeArea(area);
  const corner = a.cornerRight || a.cornerLeft;
  if (!a.bottom || !corner) return '';
  const h = Math.min(18, a.bottom - 4);
  return `position:absolute;${a.cornerRight ? 'right' : 'left'}:6px;bottom:${(a.bottom - h) / 2}px;width:${corner - 12}px;height:${h}px;`
    + `box-sizing:border-box;border-radius:${h / 2}px;background:rgba(0,0,0,.35);color:#fff;font:500 11px/${h}px system-ui,sans-serif;`
    + 'text-align:center;white-space:nowrap;overflow:hidden;z-index:2147483647';
}

/** What the reference time says: a fixed time and the double tick of a read outgoing message. */
export const REFERENCE_TIME_LABEL = '12:00 ✓✓';
