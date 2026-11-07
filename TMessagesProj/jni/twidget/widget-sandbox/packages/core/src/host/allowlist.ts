// Element / attribute allowlists (security-critical, invariant 2).
// Anything not listed here does not reach the renderer. The lists come from the
// widget profile (packages/core/profile.json → src/shared/profile-tables.ts);
// the security lists below (URL attributes, the elements that are always
// dropped) do not depend on it.

import { PROFILE_TABLES } from '../shared/profile-tables';

const words = (s: string) => s.trim().split(/\s+/);
const P = PROFILE_TABLES;
const elementsWith = (action: string) => Object.entries(P.html.elements as Record<string, string>).filter(([, a]) => a === action).map(([t]) => t);

/**
 * Elements that are never rendered, together with their whole subtree, whatever
 * the profile says. (`style` is not here: it is handled separately through the
 * CSS sanitizer.) `audio`/`video`/`source`/`track` are dropped on purpose: a chat
 * bubble has no use for media playback, and `data:` media would hand the
 * platform's codec stack to the widget (and autoplay / battery).
 */
export const SECURITY_DROPPED_TAGS = new Set(words(`
  script iframe frame frameset object embed link meta base portal noscript set title template applet param
  noembed noframes plaintext xmp listing slot track area map math metadata fencedframe audio video source
`));

/** HTML elements rendered as-is. */
export const HTML_TAGS = new Set(elementsWith('keep').filter((t) => !SECURITY_DROPPED_TAGS.has(t)));

/** HTML elements outside the profile that keep their content: rendered as this element, with `data-tg-tag` naming the original. */
export const REPLACED_TAGS = new Map<string, string>([
  ...elementsWith('span').map((t) => [t, 'span'] as [string, string]),
  ...elementsWith('div').map((t) => [t, 'div'] as [string, string]),
]);

/** Elements dropped with their subtree: the security list and the profile's (form controls). */
export const DROPPED_TAGS = new Set([...SECURITY_DROPPED_TAGS, ...elementsWith('drop')]);

/** Canonical (case-sensitive) names of allowed SVG elements, keyed by lowercase. */
export const SVG_TAGS = new Map((P.svg.elements as readonly string[]).map((t) => [t.toLowerCase(), t]));

/** HTML attributes allowed on any element (lowercase); `data-*`, `aria-*` and `style` are handled apart. */
export const HTML_ATTRS = new Set<string>(P.html.attributes);

/** SVG attributes (lowercase keys of the canonical names). */
const SVG_ATTRS = P.svg.attributes as readonly string[];
export const SVG_ALLOWED_ATTRS = new Set(SVG_ATTRS.map((a) => a.toLowerCase()));

/** Every attribute allowed in either namespace. */
export const ALLOWED_ATTRS = new Set([...HTML_ATTRS, ...SVG_ALLOWED_ATTRS]);

/** lowercase → canonical case for SVG attributes (HTML-parser style adjustment). */
export const SVG_ATTR_CASE = new Map(SVG_ATTRS.filter((a) => a !== a.toLowerCase()).map((a) => [a.toLowerCase(), a]));

/**
 * Every attribute that can carry a URL. None of these is ever passed through
 * unchecked; unlisted (tag, attr) combinations are dropped. See validator.ts.
 */
export const URL_ATTRS = new Set(words(`
  href xlink:href src srcset poster action formaction background ping data cite codebase manifest longdesc
  usemap profile icon archive classid lowsrc dynsrc srcdoc imagesrcset imagesizes attributionsrc
`));

/** Attributes whose value is CSS and may contain url(): parsed by the CSS sanitizer. */
export const CSS_VALUE_ATTRS = new Set(words(`
  fill stroke filter mask clip-path marker marker-start marker-mid marker-end cursor color flood-color
  lighting-color stop-color
`));

/** Attribute names an SVG animation element may target. URL-like ones are excluded. */
export const ANIMATION_VALUE_ATTRS = new Set(['from', 'to', 'by', 'values']);

/** Attributes dropped silently (noise, not worth a diagnostic). */
export const SILENT_DROP_ATTRS = new Set(words(`
  xmlns xmlns:xlink target rel download autofocus nonce is loading decoding crossorigin referrerpolicy
  integrity fetchpriority charset http-equiv content async defer
`));

/** Properties settable through the `prop` op, with the tags they apply to (null = any): the profile's. */
const ALL_PROPS: Record<string, { type: 'string' | 'number' | 'boolean'; tags: Set<string> | null }> = {
  scrollTop: { type: 'number', tags: null },
  scrollLeft: { type: 'number', tags: null },
};
const inProfile = <T>(all: Record<string, T>, names: readonly string[]): Record<string, T> =>
  Object.fromEntries(Object.entries(all).filter(([k]) => names.includes(k)));
export const PROPS = inProfile(ALL_PROPS, P.properties);

/** Methods callable through the `call` op: name → number of primitive args forwarded (the profile's). */
const ALL_METHODS: Record<string, number> = {
  scrollIntoView: 4, setPointerCapture: 1, releasePointerCapture: 1, scrollTo: 2, scrollBy: 2,
  // Not DOM methods: (un)subscribe the element in the renderer's ResizeObserver.
  observeResize: 0, unobserveResize: 0,
};
export const METHODS = inProfile(ALL_METHODS, P.methods);

/** Canvas 2D: methods that may be replayed (the profile's). Nothing here takes a URL. */
export const CANVAS_METHODS = new Set<string>(P.canvas.methods);

/** Canvas 2D: settable properties (the profile's). */
export const CANVAS_PROPS = new Set<string>(P.canvas.properties);

/** Canvas 2D calls and properties the shim may send that are outside the profile: dropped with a `profile` diagnostic, not a protocol violation. */
export const CANVAS_OUT_OF_PROFILE = new Set(words(`
  putImageData globalCompositeOperation shadowBlur shadowColor shadowOffsetX shadowOffsetY filter
  letterSpacing wordSpacing fontKerning fontStretch fontVariantCaps textRendering
`));

/** Gradient kinds in the profile; a conic gradient or a pattern becomes a gradient that paints nothing. */
export const CANVAS_GRADIENTS = new Set<string>(P.canvas.gradients);

export const CANVAS_PATH_METHODS = new Set(words(`
  moveTo lineTo bezierCurveTo quadraticCurveTo arc arcTo ellipse rect roundRect closePath addPath
`));

export const SVG_QUERY_METHODS = new Set(words(`
  getBBox getComputedTextLength getTotalLength getPointAtLength getCTM getScreenCTM getSubStringLength
  getNumberOfChars
`));

export const CANVAS_QUERY_METHODS = new Set((P.canvas.queries as readonly string[]).filter((m) => m !== 'measureText'));
