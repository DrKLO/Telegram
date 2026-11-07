// The widget profile feature catalogue: every feature a widget may use (or
// tries to use), with a stable ID, its v1 status and the decision it comes
// from. Source of packages/conformance/catalogue.json (`pnpm tsx
// scripts/conformance/build.ts catalogue`), which is meant to seed the
// versioned profile.json of the release plan: `match` names the tags,
// attributes, properties, values, selectors, at-rules, canvas members and JS
// APIs a validator/shim table would key on, `neutralise` says what happens to
// an out-of-profile feature.
//
// status:   in        — in profile v1; every client's renderer must draw it
//           out       — not in v1; the validator/shim neutralises it the same way everywhere
//           candidate — membership decided by the conformance results
// decision: the release-plan decision (D3, D3a, …, P1), an invariant (inv2…inv6),
//           SDK v1 (SDK) or `proposed` (this catalogue's proposal, to be confirmed)
// today:    what the pipeline of the tag (validator + shim + renderer core, strict;
//           profile 1 enforced and SDK v1, claude/sdk-v1-impl bd476e6) does with it:
//           kept, stripped, replaced, resolved, stub, emulated, host or missing
//           (TODAY_LEGEND below)

export type Status = 'in' | 'out' | 'candidate';
export type Today = 'kept' | 'stripped' | 'resolved' | 'replaced' | 'stub' | 'emulated' | 'missing' | 'host';

export const TODAY_LEGEND: Record<Today, string> = {
  kept: 'passes through to the renderer, or the shim provides it as specified',
  stripped: 'dropped by the validator, or never forwarded by the renderer',
  replaced: 'rewritten to an in-profile equivalent (an element, a value, a placeholder image, a gradient that paints nothing)',
  resolved: 'answered by the pipeline from the profile, the theme or the client settings, not by the renderer engine',
  stub: 'present but inert: a fixed answer, a throw, or nothing drawn',
  emulated: 'implemented by the shim with its own semantics',
  host: 'needs the client: the host glue answers or acts',
  missing: 'not provided',
};

export interface Match {
  tags?: string[];
  svgTags?: string[];
  attrs?: string[];
  properties?: string[];
  values?: string[];
  functions?: string[];
  units?: string[];
  selectors?: string[];
  atrules?: string[];
  media?: string[];
  canvas?: string[];
  apis?: string[];
  events?: string[];
}

export interface Feature {
  id: string;
  area: string;
  title: string;
  status: Status;
  decision: string;
  today: Today;
  /** D3a: in v1 if every client's renderer draws it. */
  conditional?: boolean;
  match?: Match;
  /** For out features: what the profile does with it. */
  neutralise?: string;
  note?: string;
}

const F: Feature[] = [];
function add(area: string, id: string, title: string, status: Status, decision: string, today: Today, extra: Partial<Feature> = {}) {
  F.push({ id, area, title, status, decision, today, ...extra });
}

// ───────────── HTML elements ─────────────
const h = (id: string, title: string, s: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('html', 'html.' + id, title, s, d, t, x);
h('el.structure', 'div, span, p, br, hr, wbr', 'in', 'D3a', 'kept', { match: { tags: ['div', 'span', 'p', 'br', 'hr', 'wbr'] } });
h('el.headings', 'h1–h6 with UA default sizes and margins', 'in', 'D3a', 'kept', { match: { tags: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6'] } });
h('el.sections', 'header, footer, main, nav, section, article, aside, hgroup, search, address', 'in', 'D3a', 'kept', { match: { tags: ['header', 'footer', 'main', 'nav', 'section', 'article', 'aside', 'hgroup', 'search', 'address'] } });
h('el.inline-text', 'Inline text semantics with UA styles: b, i, u, s, em, strong, small, mark, code, kbd, samp, var, abbr, cite, q, del, ins, dfn, time, data', 'in', 'D3a', 'kept', { match: { tags: ['b', 'i', 'u', 's', 'em', 'strong', 'small', 'mark', 'code', 'kbd', 'samp', 'var', 'abbr', 'cite', 'q', 'del', 'ins', 'dfn', 'time', 'data'] } });
h('el.sub-sup', 'sub, sup (baseline shift, smaller size)', 'in', 'D3a', 'kept', { match: { tags: ['sub', 'sup'] } });
h('el.pre', 'pre (monospace, preserved white space and line breaks)', 'in', 'D3a', 'kept', { match: { tags: ['pre'] } });
h('el.blockquote', 'blockquote, figure, figcaption (UA indents)', 'in', 'D3a', 'kept', { match: { tags: ['blockquote', 'figure', 'figcaption'] } });
h('el.lists', 'ul, ol, li, dl, dt, dd, menu with markers', 'in', 'D3a', 'kept', { match: { tags: ['ul', 'ol', 'li', 'dl', 'dt', 'dd', 'menu'] } });
h('el.table', 'table, thead, tbody, tfoot, tr, td, th, caption, colgroup, col; colspan/rowspan', 'in', 'D3a', 'kept', { match: { tags: ['table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th', 'caption', 'colgroup', 'col'], attrs: ['colspan', 'rowspan'] } });
h('el.ruby', 'ruby, rt, rp, bdi, bdo', 'candidate', 'proposed', 'replaced', { match: { tags: ['ruby', 'rt', 'rp', 'bdi', 'bdo'] }, note: 'Annotation layout is rarely implemented by small engines.' });
h('el.legacy', 'Legacy presentational elements: center, font, big, tt, strike, nobr', 'candidate', 'proposed', 'replaced', { match: { tags: ['center', 'font', 'big', 'tt', 'strike', 'nobr'] } });
h('el.img', '<img> with a data: PNG, JPEG, GIF or WebP; width/height attributes', 'in', 'D3a', 'kept', { match: { tags: ['img', 'picture'], attrs: ['src', 'width', 'height', 'alt'] } });
h('el.img-svg', '<img src="data:image/svg+xml">', 'candidate', 'proposed', 'replaced', { match: { tags: ['img'], values: ['data:image/svg+xml'] }, note: 'Release plan, security stream: restrict image formats to PNG, JPEG, GIF, WebP (the decoder runs in the app process).' });
h('el.img-other-formats', '<img> with data: AVIF, BMP, ICO', 'out', 'proposed', 'replaced', { match: { values: ['data:image/avif', 'data:image/bmp', 'data:image/x-icon', 'data:image/vnd.microsoft.icon'] }, neutralise: 'url-policy refuses the MIME: placeholder image', note: 'Release plan, security stream 7.' });
h('el.img-remote', '<img src="https://…"> (any non-data: URL)', 'out', 'inv4', 'replaced', { match: { attrs: ['src', 'srcset'] }, neutralise: 'placeholder image; no request' });
h('el.canvas', '<canvas> element (2D context through the command stream)', 'in', 'D3a', 'kept', { match: { tags: ['canvas'] } });
h('el.button', '<button> as a tappable box with UA look', 'in', 'D4', 'kept', { match: { tags: ['button'] }, note: 'An unstyled <button> is drawn in the client\'s style: the profile\'s UA stylesheet and --tg-button-*.' });
h('el.details', 'details/summary: open state and toggle on tap', 'in', 'proposed', 'kept', { match: { tags: ['details', 'summary'], events: ['toggle'] }, note: 'Probes: drawn by Blitz and litehtml; it is a disclosure, not a form.' });
h('el.a-fragment', '<a href="#id">: no navigation, :target-free', 'in', 'today', 'kept', { match: { tags: ['a'], attrs: ['href'] } });
h('el.a-external', '<a href="https://…">: href becomes "#" + data-tg-link, the tap goes to openLink (with a gesture)', 'in', 'D8', 'replaced', { match: { tags: ['a'], attrs: ['href'] } });
h('el.forms', 'Form controls: form, input (all types), select, option, optgroup, textarea, label, fieldset, legend, datalist, output', 'out', 'D3a', 'stripped', { match: { tags: ['form', 'input', 'select', 'option', 'optgroup', 'textarea', 'label', 'fieldset', 'legend', 'datalist', 'output'] }, neutralise: 'not decided: drop the control (no box) or render it as an inert span; the tests expect "no control is drawn"' });
h('el.progress-meter', 'progress, meter (native gauges)', 'candidate', 'proposed', 'stripped', { match: { tags: ['progress', 'meter'] }, note: 'Not input, but a native control look that differs per platform.' });
h('el.dialog-popover', 'dialog (show/showModal), popover attribute', 'out', 'proposed', 'replaced', { match: { tags: ['dialog'], attrs: ['popover', 'popovertarget', 'popovertargetaction'], apis: ['showModal', 'show', 'showPopover', 'togglePopover'] }, neutralise: 'drop the methods and the popover attributes; a dialog renders as a block', note: 'The top layer is the page: in the Shadow DOM renderer a modal dialog or popover is drawn over the whole chat, outside the bubble.' });
h('el.unknown', 'Unknown / custom elements render as an inert <span data-tg-tag>', 'in', 'today', 'replaced', { match: { tags: ['*unknown*'] } });
h('el.media', 'audio, video, source, track', 'out', 'today', 'stripped', { match: { tags: ['audio', 'video', 'source', 'track'] }, neutralise: 'dropped with the subtree' });
h('el.embedding', 'script (as DOM), iframe, frame, object, embed, link, meta, base, portal, template, slot, math, fencedframe, …', 'out', 'inv2', 'stripped', { match: { tags: ['iframe', 'frame', 'frameset', 'object', 'embed', 'link', 'meta', 'base', 'portal', 'noscript', 'template', 'slot', 'math', 'fencedframe', 'applet', 'param', 'area', 'map'] }, neutralise: 'dropped with the subtree' });

// ───────────── HTML attributes ─────────────
h('attr.global', 'class, id, title, hidden, lang, style attribute', 'in', 'D3a', 'kept', { match: { attrs: ['class', 'id', 'title', 'hidden', 'lang', 'style'] } });
h('attr.dir', 'dir="rtl" / dir="auto": bidi layout', 'in', 'D3a', 'kept', { match: { attrs: ['dir'] } });
h('attr.data-aria', 'data-* and aria-* attributes (selectors, attr()); no a11y semantics in v1', 'in', 'D21', 'kept', { match: { attrs: ['data-*', 'aria-*'] }, note: 'Counted against the strict text budget.' });
h('attr.legacy-presentational', 'align, valign, bgcolor, border, cellpadding, cellspacing, color, face, nowrap', 'candidate', 'proposed', 'stripped', { match: { attrs: ['align', 'valign', 'bgcolor', 'border', 'cellpadding', 'cellspacing', 'color', 'face', 'nowrap'] } });
h('attr.event-handlers', 'Inline on* handler attributes (run inside QuickJS, never reach the renderer)', 'in', 'today', 'host', { match: { attrs: ['on*'] } });
h('attr.contenteditable', 'contenteditable, spellcheck, inputmode, enterkeyhint, autocapitalize', 'out', 'proposed', 'stripped', { match: { attrs: ['contenteditable', 'spellcheck', 'inputmode', 'enterkeyhint', 'autocapitalize'] }, neutralise: 'drop the attribute', note: 'Editing is form input. Text is not selectable by default (D18); user-select: text enables selection, never editing.' });
h('attr.draggable', 'draggable (HTML drag and drop)', 'out', 'D4', 'stripped', { match: { attrs: ['draggable'] }, neutralise: 'drop the attribute; drag in v1 is pointer events' });
h('attr.tabindex', 'tabindex, autofocus, accesskey (keyboard focus and shortcuts)', 'out', 'D4', 'stripped', { match: { attrs: ['tabindex', 'autofocus', 'accesskey'] }, neutralise: 'drop the attributes (no keyboard: D4)' });

// ───────────── SVG ─────────────
const s = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('svg', 'svg.' + id, title, st, d, t, x);
s('shapes', 'Inline <svg>: rect, circle, ellipse, line, polyline, polygon, path with presentation attributes', 'in', 'D3a', 'kept', { match: { svgTags: ['svg', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon', 'path'], attrs: ['fill', 'stroke', 'stroke-width', 'd', 'viewBox'] } });
s('structure', 'g, defs, symbol, use (fragment href), transform attribute, viewBox/preserveAspectRatio', 'in', 'D3a', 'kept', { match: { svgTags: ['g', 'defs', 'symbol', 'use'], attrs: ['transform', 'viewBox', 'preserveAspectRatio'] } });
s('text', 'SVG text, tspan', 'in', 'D3a', 'kept', { match: { svgTags: ['text', 'tspan'] } });
s('gradients', 'linearGradient, radialGradient, stop; fill="url(#id)"', 'in', 'proposed', 'kept', { match: { svgTags: ['linearGradient', 'radialGradient', 'stop'] } });
s('css', 'CSS on inline SVG: fill/stroke from a stylesheet rule, :hover, CSS animation of SVG properties', 'candidate', 'brief', 'kept', { match: { properties: ['fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'stroke-dashoffset'] }, note: 'Blitz and the litehtml prototype ignore SVG CSS (probe svg-css).' });
s('clip-mask', 'clipPath, mask, pattern, marker', 'candidate', 'proposed', 'stripped', { match: { svgTags: ['clipPath', 'mask', 'pattern', 'marker'] } });
s('filters', 'SVG filter primitives (fe*)', 'candidate', 'proposed', 'stripped', { match: { svgTags: ['filter', 'feGaussianBlur', 'feColorMatrix', 'feOffset', 'feMerge', 'feDropShadow', '…'] } });
s('smil', 'SMIL animation: animate, animateTransform, animateMotion', 'candidate', 'proposed', 'stripped', { match: { svgTags: ['animate', 'animateTransform', 'animateMotion', 'mpath'] }, note: 'Animation driven by the renderer, outside CSS animations and WAAPI.' });
s('foreign-object', 'foreignObject (HTML inside SVG)', 'out', 'proposed', 'stripped', { match: { svgTags: ['foreignObject'] }, neutralise: 'drop the subtree' });
s('image', '<image href="data:…"> inside SVG', 'candidate', 'proposed', 'stripped', { match: { svgTags: ['image'] } });

// ───────────── CSS: layout and box model ─────────────
const c = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('css', 'css.' + id, title, st, d, t, x);
c('display.basic', 'display: block, inline, inline-block, none', 'in', 'D3a', 'kept', { match: { properties: ['display'], values: ['block', 'inline', 'inline-block', 'none'] } });
c('display.flex', 'Flexbox: display flex/inline-flex, direction, wrap, justify-content, align-items/self/content, flex grow/shrink/basis, order', 'in', 'D3a', 'kept', { match: { properties: ['display', 'flex', 'flex-direction', 'flex-wrap', 'flex-flow', 'justify-content', 'align-items', 'align-self', 'align-content', 'flex-grow', 'flex-shrink', 'flex-basis', 'order'], values: ['flex', 'inline-flex'] } });
c('gap.flex', 'gap / row-gap / column-gap in flex containers', 'in', 'D3a', 'kept', { match: { properties: ['gap', 'row-gap', 'column-gap'] }, note: 'Missing in the litehtml prototype (probe flex-gap).' });
c('display.grid', 'CSS grid: display grid, grid-template-*, grid-area/column/row, auto-flow, place-*', 'candidate', 'brief', 'kept', { match: { properties: ['grid-template-columns', 'grid-template-rows', 'grid-template-areas', 'grid-column', 'grid-row', 'grid-area', 'grid-auto-flow', 'grid-auto-rows', 'grid-auto-columns', 'place-items', 'place-content', 'place-self', 'justify-items'], values: ['grid', 'inline-grid'] } });
c('display.table', 'display: table / table-row / table-cell', 'in', 'D3a', 'kept', { match: { properties: ['display'], values: ['table', 'table-row', 'table-cell', 'table-caption'] } });
c('display.other', 'display: flow-root, contents, list-item', 'candidate', 'proposed', 'stripped', { match: { values: ['flow-root', 'contents', 'list-item'] } });
c('box.size', 'width, height, min-/max-width/height (px, %, auto, fit-content, min/max-content)', 'in', 'D3a', 'kept', { match: { properties: ['width', 'height', 'min-width', 'min-height', 'max-width', 'max-height'] } });
c('box.spacing', 'margin, padding (incl. auto margins and negative margins)', 'in', 'D3a', 'kept', { match: { properties: ['margin', 'margin-*', 'padding', 'padding-*'] } });
c('box.border', 'border width/style/colour (solid, dashed, dotted, double)', 'in', 'D3a', 'kept', { match: { properties: ['border', 'border-*'] } });
c('box.radius', 'border-radius, including clipping of the background', 'in', 'D3a', 'kept', { match: { properties: ['border-radius', 'border-*-radius'] } });
c('box.sizing', 'box-sizing: border-box / content-box', 'in', 'D3a', 'kept', { match: { properties: ['box-sizing'] } });
c('box.aspect-ratio', 'aspect-ratio', 'in', 'D3a', 'kept', { match: { properties: ['aspect-ratio'] }, note: 'Missing in the litehtml prototype.' });
c('box.logical', 'Logical properties: margin-inline, padding-block, inset-inline, inline-size, …', 'candidate', 'proposed', 'stripped', { match: { properties: ['margin-inline*', 'margin-block*', 'padding-inline*', 'padding-block*', 'inset-inline*', 'inset-block*', 'inline-size', 'block-size'] }, note: 'Used by LLM widgets (15 % of generated); missing in the litehtml prototype.' });
c('box.outline', 'outline (width, style, colour, offset)', 'in', 'D3a', 'kept', { match: { properties: ['outline', 'outline-*'] } });
c('box.shadow', 'box-shadow (outer, inset, spread, several layers)', 'in', 'D3a', 'kept', { match: { properties: ['box-shadow'] } });
c('box.float', 'float, clear', 'in', 'D3a', 'kept', { match: { properties: ['float', 'clear'] } });
c('box.columns', 'Multi-column layout (columns, column-count, column-gap)', 'candidate', 'proposed', 'stripped', { match: { properties: ['columns', 'column-count', 'column-width', 'column-rule'] } });
c('box.object-fit', 'object-fit, object-position on <img>', 'in', 'proposed', 'kept', { match: { properties: ['object-fit', 'object-position'] } });
c('position.relative-absolute', 'position: relative / absolute with inset, top/right/bottom/left', 'in', 'D3a', 'kept', { match: { properties: ['position', 'top', 'right', 'bottom', 'left', 'inset'], values: ['relative', 'absolute'] } });
c('position.fixed', 'position: fixed (relative to the bubble, never the chat)', 'in', 'proposed', 'kept', { match: { values: ['fixed'] } });
c('position.sticky', 'position: sticky', 'out', 'proposed', 'replaced', { match: { values: ['sticky'] }, neutralise: 'becomes relative (no scrolling inside a widget)' });
c('position.z-index', 'z-index and stacking order', 'in', 'D3a', 'kept', { match: { properties: ['z-index'] } });
c('overflow.clip', 'overflow: hidden / clip clips the content (with border-radius)', 'in', 'D3a', 'kept', { match: { properties: ['overflow', 'overflow-x', 'overflow-y'], values: ['hidden', 'clip'] } });
c('overflow.scroll', 'overflow: auto / scroll (scrolling inside the widget)', 'out', 'D3a', 'replaced', { match: { properties: ['overflow', 'overflow-x', 'overflow-y'], values: ['auto', 'scroll', 'overlay'] }, neutralise: 'becomes hidden: the box clips, no scrollbar, no scroll gesture' });
c('visibility', 'visibility: hidden / visible', 'in', 'D3a', 'kept', { match: { properties: ['visibility'] } });
c('opacity', 'opacity', 'in', 'D3a', 'kept', { match: { properties: ['opacity'] }, note: 'Missing in the litehtml prototype.' });

// ───────────── CSS: colour and backgrounds ─────────────
c('color.basic', 'Colours: named, #hex (3/4/6/8), rgb()/rgba(), hsl()/hsla(), transparent, currentColor', 'in', 'D3a', 'kept', { match: { properties: ['color', 'background-color', 'border-color'], functions: ['rgb', 'rgba', 'hsl', 'hsla'] } });
c('color.modern', 'Colour functions: hwb(), lab(), lch(), oklab(), oklch(), color()', 'candidate', 'proposed', 'stripped', { match: { functions: ['hwb', 'lab', 'lch', 'oklab', 'oklch', 'color'] } });
c('color.mix', 'color-mix()', 'candidate', 'proposed', 'stripped', { match: { functions: ['color-mix'] }, note: 'Missing in the litehtml prototype.' });
c('color.light-dark', 'light-dark() and color-scheme: light dark, resolved to the chat theme', 'in', 'today', 'resolved', { match: { functions: ['light-dark'], properties: ['color-scheme'] } });
c('background.basic', 'background-color, background-image: url(data:…), background-size/position/repeat, several layers', 'in', 'D3a', 'kept', { match: { properties: ['background', 'background-color', 'background-image', 'background-size', 'background-position', 'background-repeat', 'background-origin'] } });
c('background.clip-text', 'background-clip: text (gradient text)', 'candidate', 'proposed', 'stripped', { match: { properties: ['background-clip', '-webkit-background-clip'], values: ['text'] } });
c('gradient.linear', 'linear-gradient(), repeating-linear-gradient()', 'in', 'D3a', 'kept', { match: { functions: ['linear-gradient', 'repeating-linear-gradient'] } });
c('gradient.radial', 'radial-gradient(), repeating-radial-gradient()', 'in', 'D3a', 'kept', { match: { functions: ['radial-gradient', 'repeating-radial-gradient'] } });
c('gradient.conic', 'conic-gradient()', 'candidate', 'brief', 'kept', { match: { functions: ['conic-gradient', 'repeating-conic-gradient'] } });
c('url.remote', 'url() / image-set() with anything but a data: image or #fragment', 'out', 'inv4', 'stripped', { match: { functions: ['url', 'image-set', 'image', 'cross-fade'] }, neutralise: 'the declaration is dropped' });

// ───────────── CSS: text and fonts ─────────────
c('font.generic', 'font-family with system generic families: system-ui, sans-serif, serif, monospace', 'in', 'D3b', 'kept', { match: { properties: ['font-family', 'font'], values: ['system-ui', 'sans-serif', 'serif', 'monospace'] } });
c('font.ui-families', 'font-family: ui-sans-serif, ui-serif, ui-monospace, ui-rounded', 'candidate', 'proposed', 'kept', { match: { properties: ['font-family', 'font'], values: ['ui-sans-serif', 'ui-serif', 'ui-monospace', 'ui-rounded'] }, note: 'Generic per CSS Fonts 4, but only WebKit resolves them; the validator appends the generic fallback (ui-monospace → monospace).' });
c('font.named', 'font-family naming a font ("Helvetica", "Courier New", "Comic Sans MS", …)', 'out', 'D3b', 'stripped', { match: { properties: ['font-family', 'font'] }, neutralise: 'the validator drops non-generic names from the list; -apple-system and BlinkMacSystemFont, the vendor names of the system UI font, become system-ui; an empty list falls back to the default (system-ui)' });
c('font.face', '@font-face (web fonts)', 'out', 'D3b', 'stripped', { match: { atrules: ['font-face'] }, neutralise: 'the at-rule is dropped' });
c('font.basic', 'font-size, font-weight (100–900, bold), font-style italic, line-height, font shorthand', 'in', 'D3a', 'kept', { match: { properties: ['font-size', 'font-weight', 'font-style', 'line-height', 'font'] } });
c('font.variant-numeric', 'font-variant-numeric: tabular-nums (and font-feature-settings "tnum")', 'candidate', 'brief', 'kept', { match: { properties: ['font-variant-numeric', 'font-feature-settings'] } });
c('text.align', 'text-align (left, right, center, justify, start, end), text-indent', 'in', 'D3a', 'kept', { match: { properties: ['text-align', 'text-indent'] } });
c('text.decoration', 'text-decoration (underline, line-through, overline; colour, style, thickness)', 'in', 'D3a', 'kept', { match: { properties: ['text-decoration', 'text-decoration-*'] } });
c('text.transform', 'text-transform, letter-spacing, word-spacing', 'in', 'D3a', 'kept', { match: { properties: ['text-transform', 'letter-spacing', 'word-spacing'] } });
c('text.white-space', 'white-space: normal, nowrap, pre, pre-wrap, pre-line', 'in', 'D3a', 'kept', { match: { properties: ['white-space'] }, note: 'pre missing in the litehtml prototype.' });
c('text.wrap', 'overflow-wrap / word-break (breaking long words)', 'in', 'D3a', 'kept', { match: { properties: ['overflow-wrap', 'word-wrap', 'word-break'] } });
c('text.overflow', 'text-overflow: ellipsis (single line)', 'candidate', 'brief', 'kept', { match: { properties: ['text-overflow'] } });
c('text.line-clamp', '-webkit-line-clamp / line-clamp (multi-line ellipsis)', 'candidate', 'proposed', 'stripped', { match: { properties: ['-webkit-line-clamp', 'line-clamp', '-webkit-box-orient'] } });
c('text.shadow', 'text-shadow', 'candidate', 'proposed', 'stripped', { match: { properties: ['text-shadow'] }, note: 'Not drawn by Blitz or the litehtml prototype (probe text-shadow).' });
c('text.vertical-align', 'vertical-align (baseline, middle, top, sub, super, lengths)', 'in', 'D3a', 'kept', { match: { properties: ['vertical-align'] } });
c('text.list-style', 'list-style-type / position (disc, decimal, none, …)', 'in', 'D3a', 'kept', { match: { properties: ['list-style', 'list-style-type', 'list-style-position'] } });
c('text.writing-mode', 'writing-mode, text-orientation (vertical text)', 'out', 'proposed', 'stripped', { match: { properties: ['writing-mode', 'text-orientation'] }, neutralise: 'the declaration is dropped' });
c('text.rtl', 'Right-to-left and bidi text (direction, unicode-bidi, Hebrew/Arabic shaping)', 'in', 'D3a', 'kept', { match: { properties: ['direction', 'unicode-bidi'] } });
c('text.emoji', 'Colour emoji glyphs (system emoji font)', 'in', 'D3a', 'kept', { note: 'Blitz on iOS had blank emoji on the vello backend.' });
c('text.selection', 'Text selection: not selectable by default; user-select: text (or -webkit-user-select) enables it (none | text | auto)', 'in', 'D18', 'kept', { match: { properties: ['user-select', '-webkit-user-select'] }, note: "user-select: all and contain, and ::selection, stay out. A native renderer that cannot select text yet keeps it unselectable: the widget still works (an accepted exception to 'every client draws it', like :hover)." });

// ───────────── CSS: transforms, effects ─────────────
c('transform.2d', '2D transforms: translate, rotate, scale, skew, matrix; transform-origin; the translate/rotate/scale properties', 'in', 'D3a', 'kept', { match: { properties: ['transform', 'transform-origin', 'translate', 'rotate', 'scale'], functions: ['translate', 'translateX', 'translateY', 'rotate', 'scale', 'scaleX', 'scaleY', 'skew', 'skewX', 'skewY', 'matrix'] }, note: 'Missing in the litehtml prototype.' });
c('transform.3d', '3D transforms: rotateX/Y/3d, translateZ/3d, scaleZ/3d, matrix3d, perspective, transform-style, backface-visibility', 'out', 'D3a', 'stripped', { match: { properties: ['perspective', 'perspective-origin', 'transform-style', 'backface-visibility'], functions: ['rotateX', 'rotateY', 'rotateZ', 'rotate3d', 'translateZ', 'translate3d', 'scaleZ', 'scale3d', 'matrix3d', 'perspective'] }, neutralise: 'a transform with a 3D function is dropped; perspective, transform-style and backface-visibility are dropped' });
c('filter.blur-brightness-drop-shadow', 'filter: blur(), brightness(), drop-shadow()', 'in', 'D3a', 'kept', { conditional: true, match: { properties: ['filter'], functions: ['blur', 'brightness', 'drop-shadow'] }, note: 'Not drawn by Blitz or the litehtml prototype today (probe filter).' });
c('filter.other', 'filter: grayscale(), sepia(), saturate(), contrast(), hue-rotate(), invert(), opacity()', 'candidate', 'proposed', 'stripped', { match: { properties: ['filter'], functions: ['grayscale', 'sepia', 'saturate', 'contrast', 'hue-rotate', 'invert', 'opacity'] } });
c('filter.url', 'filter: url(#svg-filter)', 'out', 'proposed', 'stripped', { match: { properties: ['filter'], functions: ['url'] }, neutralise: 'the declaration is dropped' });
c('backdrop-filter', 'backdrop-filter: blur(), brightness()', 'in', 'D3a', 'kept', { conditional: true, match: { properties: ['backdrop-filter', '-webkit-backdrop-filter'] } });
c('blend', 'mix-blend-mode, isolation, background-blend-mode', 'candidate', 'proposed', 'stripped', { match: { properties: ['mix-blend-mode', 'isolation', 'background-blend-mode'] } });
c('clip-path', 'clip-path: inset(), circle(), ellipse(), polygon()', 'candidate', 'proposed', 'stripped', { match: { properties: ['clip-path'] }, note: 'Blitz draws it, the litehtml prototype does not.' });
c('mask', 'mask / -webkit-mask with gradients or data: images', 'candidate', 'proposed', 'stripped', { match: { properties: ['mask', 'mask-*', '-webkit-mask', '-webkit-mask-*'] } });
c('pointer-events', 'pointer-events: none / auto (hit testing passes through)', 'candidate', 'brief', 'kept', { match: { properties: ['pointer-events'] } });
c('touch-action', 'touch-action: none / pan-y (the widget claims horizontal drags from the chat; vertical drags are always the chat\'s)', 'candidate', 'brief', 'kept', { match: { properties: ['touch-action'] }, note: 'D4: drag in v1 needs gesture arbitration; the renderer reports the claim (`gesture` message). v0.1.3 (G2): pan-x claims nothing, and the engine under the renderer gets the value widened to allow vertical panning (none → pan-y), while the claim and getComputedStyle keep the widget\'s own.' });
c('cursor', 'cursor (desktop only)', 'in', 'D3', 'kept', { match: { properties: ['cursor'] }, note: 'Progressive enhancement like :hover; no-op on touch clients.' });

// ───────────── CSS: values, units, functions ─────────────
c('value.units', 'Units px, em, rem, %, ch, ex; pt/cm/mm/in', 'in', 'D3a', 'kept', { match: { units: ['px', 'em', 'rem', '%', 'ch', 'ex', 'pt', 'cm', 'mm', 'in'] } });
c('value.vw', 'vw / vmin / vmax / vi: the bubble width', 'in', 'today', 'replaced', { match: { units: ['vw', 'svw', 'lvw', 'dvw', 'vi', 'vmin', 'vmax'] }, note: 'Container units (cqw) on the Shadow DOM renderer.' });
c('value.vh', 'vh / dvh / svh / vb: height-relative units', 'candidate', 'proposed', 'stripped', { match: { units: ['vh', 'svh', 'lvh', 'dvh', 'vb'] }, note: 'The bubble height comes from the content, so vh is circular: the iframe uses its own height, the shadow renderer the container height.' });
c('value.math', 'calc(), min(), max(), clamp()', 'in', 'D3a', 'kept', { match: { functions: ['calc', 'min', 'max', 'clamp'] }, note: 'Missing in the litehtml prototype.' });
c('value.custom-properties', 'Custom properties and var() with fallbacks; inheritance', 'in', 'D3a', 'kept', { match: { functions: ['var'], properties: ['--*'] } });
c('value.keywords', 'inherit, initial, unset, revert; !important', 'in', 'D3a', 'kept', { match: { values: ['inherit', 'initial', 'unset', 'revert', '!important'] } });
c('value.content', 'content in ::before/::after: strings, attr(data-*), counters', 'in', 'D3a', 'kept', { match: { properties: ['content', 'counter-reset', 'counter-increment', 'quotes'], functions: ['attr', 'counter', 'counters'] }, note: 'Strict: attr() reads only budgeted attributes.' });
c('value.env', 'env() (safe-area-inset-*, …)', 'in', 'P1-safe-area', 'replaced', { match: { functions: ['env'] }, note: 'env(safe-area-inset-top|right|bottom|left[, fallback]) becomes var(--tg-content-safe-area-inset-…[, fallback]), the root variable the renderer sets (the client\'s time over the bottom corner of a widget with no caption below it); another name resolves to its fallback or the declaration is dropped; never inside url().' });

// ───────────── CSS: selectors ─────────────
const sel = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('selector', 'sel.' + id, title, st, d, t, x);
sel('basic', 'Type, class, id, universal and attribute selectors; descendant, child, + and ~ combinators; selector lists', 'in', 'D3a', 'kept', { match: { selectors: ['E', '.c', '#i', '*', '[a]', '[a=v]', '[a^=v]', '[a$=v]', '[a*=v]', '[a~=v]', 'A B', 'A > B', 'A + B', 'A ~ B'] } });
sel('structural', ':first-child, :last-child, :nth-child(), :nth-last-child(), :nth-of-type(), :only-child, :empty, :not()', 'in', 'D3a', 'kept', { match: { selectors: [':first-child', ':last-child', ':nth-child', ':nth-last-child', ':nth-of-type', ':first-of-type', ':last-of-type', ':only-child', ':empty', ':not'] } });
sel('is-where', ':is(), :where()', 'in', 'D3a', 'kept', { match: { selectors: [':is', ':where'] } });
sel('has', ':has()', 'candidate', 'proposed', 'stripped', { match: { selectors: [':has'] }, note: 'Not matched by Blitz or the litehtml prototype (probe has-selector).' });
sel('root', ':root (custom properties on the widget root)', 'in', 'D3a', 'replaced', { match: { selectors: [':root'] }, note: '[data-tw-root] on the Shadow DOM renderer.' });
sel('before-after', '::before, ::after', 'in', 'D3a', 'kept', { match: { selectors: ['::before', '::after'] } });
sel('first-letter-line', '::first-letter, ::first-line, ::marker', 'candidate', 'proposed', 'stripped', { match: { selectors: ['::first-letter', '::first-line', '::marker'] } });
sel('hover', ':hover (progressive enhancement: desktop with a pointer; no-op on touch)', 'in', 'D3', 'kept', { match: { selectors: [':hover'] } });
sel('active', ':active (pressed feedback while a tap is down)', 'in', 'proposed', 'kept', { match: { selectors: [':active'] }, note: 'Used by 32 % of the HTMLBubbles demos for tap feedback.' });
sel('focus', ':focus, :focus-visible, :focus-within', 'out', 'D4', 'stripped', { match: { selectors: [':focus', ':focus-visible', ':focus-within'] }, neutralise: 'never matches (the widget never owns keyboard focus: D4)' });
sel('form-state', ':checked, :disabled, :enabled, :valid, :invalid, :placeholder-shown, ::placeholder', 'out', 'D3a', 'replaced', { match: { selectors: [':checked', ':disabled', ':enabled', ':valid', ':invalid', ':placeholder-shown', '::placeholder'] }, neutralise: 'follows the forms decision' });
sel('host-scoped', ':host, :host-context(), ::slotted(), ::part(), :scope', 'out', 'inv6', 'stripped', { match: { selectors: [':host', ':host-context', '::slotted', '::part', ':scope'] }, neutralise: 'the rule is dropped (Shadow DOM scope); in a document scope they match nothing' });
sel('target', ':target', 'out', 'proposed', 'stripped', { match: { selectors: [':target'] }, neutralise: 'never matches (no navigation)' });

// ───────────── CSS: at-rules and nesting ─────────────
const at = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('at-rule', 'at.' + id, title, st, d, t, x);
at('media.width', '@media (min-width/max-width/orientation): the bubble width', 'in', 'D3a', 'replaced', { match: { atrules: ['media'], media: ['width', 'min-width', 'max-width', 'orientation', 'aspect-ratio'] }, note: '@container on the Shadow DOM renderer.' });
at('media.color-scheme', '@media (prefers-color-scheme): the chat theme, live on theme change', 'in', 'D14', 'resolved', { match: { atrules: ['media'], media: ['prefers-color-scheme'] } });
at('media.reduced-motion', '@media (prefers-reduced-motion)', 'candidate', 'proposed', 'resolved', { match: { media: ['prefers-reduced-motion'] }, note: 'The validator answers it with the client\'s setting; until the client passes it, no preference.' });
at('media.hover-pointer', '@media (hover: hover), (pointer: fine|coarse)', 'candidate', 'proposed', 'stripped', { match: { media: ['hover', 'any-hover', 'pointer', 'any-pointer'] }, note: 'Lets a widget gate :hover styles; the answer must come from the client, not the engine.' });
at('media.other', '@media print, resolution, other media features', 'candidate', 'proposed', 'stripped', { match: { media: ['print', 'resolution', 'display-mode', 'forced-colors', 'prefers-contrast'] } });
at('supports', '@supports evaluated from the profile tables (not the renderer engine)', 'in', 'D23', 'resolved', { match: { atrules: ['supports'] }, note: 'Decided by the validator from the profile tables: false for anything outside the profile.' });
at('keyframes', '@keyframes (and -webkit-keyframes)', 'in', 'D3a', 'kept', { match: { atrules: ['keyframes', '-webkit-keyframes'] } });
at('import-namespace', '@import, @namespace', 'out', 'inv4', 'stripped', { match: { atrules: ['import', 'namespace'] }, neutralise: 'dropped' });
at('container', '@container queries (container-type in the widget)', 'candidate', 'proposed', 'stripped', { match: { atrules: ['container'], properties: ['container', 'container-type', 'container-name'] } });
at('layer', '@layer cascade layers', 'candidate', 'proposed', 'stripped', { match: { atrules: ['layer'] } });
at('property', '@property (typed custom properties)', 'candidate', 'proposed', 'stripped', { match: { atrules: ['property'] } });
at('exotic', '@counter-style, @starting-style, @scope, @page, @font-feature-values, @font-palette-values, @position-try', 'out', 'proposed', 'stripped', { match: { atrules: ['counter-style', 'starting-style', 'scope', 'page', 'font-feature-values', 'font-palette-values', 'position-try'] }, neutralise: 'dropped like unknown at-rules' });
at('nesting', 'CSS nesting (.a { .b { } }, &)', 'candidate', 'brief', 'stripped', { match: { selectors: ['&'] }, neutralise: 'flatten in the validator, or drop the nested rules', note: 'Nested rules are dropped with a profile diagnostic.' });

// ───────────── Animation ─────────────
const an = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('animation', 'anim.' + id, title, st, d, t, x);
an('transition', 'CSS transitions (property, duration, delay incl. negative, timing functions)', 'in', 'D3a', 'kept', { match: { properties: ['transition', 'transition-*'] } });
an('keyframes', 'CSS animations: animation-name/duration/delay/iteration-count/direction/fill-mode/timing-function', 'in', 'D3a', 'kept', { match: { properties: ['animation', 'animation-*'] }, note: 'Not driven by Blitz or the litehtml prototype.' });
an('play-state', 'animation-play-state: paused (and negative delays) freeze a frame', 'in', 'D3a', 'kept', { match: { properties: ['animation-play-state'] } });
an('easing', 'Timing functions: ease*, linear, cubic-bezier(), steps(), linear() stops', 'in', 'D3a', 'kept', { match: { functions: ['cubic-bezier', 'steps', 'linear'] } });
an('events', 'animationstart/end/iteration and transitionend events reach the widget', 'in', 'D3a', 'kept', { match: { events: ['animationstart', 'animationend', 'animationiteration', 'transitionend'] } });
an('waapi', 'Web Animations: Element.animate, getAnimations, Animation play/pause/currentTime/finished, document.timeline', 'in', 'D3a', 'kept', { conditional: true, match: { apis: ['Element.animate', 'Element.getAnimations', 'document.getAnimations', 'document.timeline', 'Animation', 'KeyframeEffect'] }, note: 'Behind the webAnimations feature (D5: profile flag, the same on every client); without it animate() is a finished stub.' });
an('waapi-css-control', 'Web Animations control of CSS animations/transitions (pause, seek, cancel via getAnimations)', 'in', 'D3a', 'kept', { conditional: true, match: { apis: ['CSSAnimation', 'CSSTransition'] } });
an('magic-move', 'Magic move (shared-element morph between renders)', 'out', 'D3a', 'missing', { neutralise: 'not offered (it belonged to the render() model)' });

// ───────────── Canvas 2D ─────────────
const cv = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('canvas', 'canvas.' + id, title, st, d, t, x);
cv('rect', 'fillRect, strokeRect, clearRect', 'in', 'D3a', 'kept', { match: { canvas: ['fillRect', 'strokeRect', 'clearRect'] } });
cv('path', 'Paths: beginPath, moveTo, lineTo, arc, arcTo, bezierCurveTo, quadraticCurveTo, ellipse, rect, roundRect, closePath, fill (nonzero/evenodd), stroke', 'in', 'D3a', 'kept', { match: { canvas: ['beginPath', 'moveTo', 'lineTo', 'arc', 'arcTo', 'bezierCurveTo', 'quadraticCurveTo', 'ellipse', 'rect', 'roundRect', 'closePath', 'fill', 'stroke'] } });
cv('line-style', 'lineWidth, lineCap, lineJoin, miterLimit, setLineDash, lineDashOffset', 'in', 'D3a', 'kept', { match: { canvas: ['lineWidth', 'lineCap', 'lineJoin', 'miterLimit', 'setLineDash', 'lineDashOffset'] } });
cv('colors', 'fillStyle / strokeStyle with CSS colours', 'in', 'D3a', 'kept', { match: { canvas: ['fillStyle', 'strokeStyle'] } });
cv('gradients', 'createLinearGradient, createRadialGradient, addColorStop', 'in', 'D3a', 'kept', { match: { canvas: ['createLinearGradient', 'createRadialGradient', 'addColorStop'] } });
cv('conic-gradient', 'createConicGradient', 'candidate', 'brief', 'replaced', { match: { canvas: ['createConicGradient'] } });
cv('pattern', 'createPattern from a canvas or image', 'candidate', 'proposed', 'replaced', { match: { canvas: ['createPattern'] } });
cv('transform', 'save, restore, translate, rotate, scale, transform, setTransform, resetTransform', 'in', 'D3a', 'kept', { match: { canvas: ['save', 'restore', 'translate', 'rotate', 'scale', 'transform', 'setTransform', 'resetTransform'] } });
cv('clip', 'clip() with a path', 'in', 'D3a', 'kept', { match: { canvas: ['clip'] } });
cv('text', 'fillText, strokeText, font, textAlign, textBaseline, measureText', 'in', 'D3a', 'kept', { match: { canvas: ['fillText', 'strokeText', 'font', 'textAlign', 'textBaseline', 'measureText'] } });
cv('alpha', 'globalAlpha', 'in', 'D3a', 'kept', { match: { canvas: ['globalAlpha'] } });
cv('composite', 'globalCompositeOperation (source-over, destination-out, multiply, …)', 'candidate', 'proposed', 'stripped', { match: { canvas: ['globalCompositeOperation'] } });
cv('shadow', 'shadowBlur, shadowColor, shadowOffsetX/Y', 'candidate', 'proposed', 'stripped', { match: { canvas: ['shadowBlur', 'shadowColor', 'shadowOffsetX', 'shadowOffsetY'] } });
cv('filter', 'ctx.filter (CSS filter on canvas drawing)', 'candidate', 'brief', 'stripped', { match: { canvas: ['filter'] } });
cv('path2d', 'Path2D objects (from SVG path data or built), fill/stroke/clip with them', 'in', 'D3a', 'kept', { match: { canvas: ['Path2D'] } });
cv('draw-image', 'drawImage from an <img> (data:) or another canvas', 'in', 'D3a', 'kept', { match: { canvas: ['drawImage'] } });
cv('image-data', 'getImageData / putImageData / createImageData (pixel read-back)', 'candidate', 'proposed', 'stub', { match: { canvas: ['getImageData', 'putImageData', 'createImageData'] }, note: 'getImageData is a sync query answered by the renderer: a native renderer must rasterize to answer.' });
cv('hit-test', 'isPointInPath / isPointInStroke', 'in', 'proposed', 'kept', { match: { canvas: ['isPointInPath', 'isPointInStroke'] } });
cv('hidpi', 'Backing store sized by width/height attributes, CSS size independent (devicePixelRatio scaling)', 'in', 'D3a', 'kept', { match: { attrs: ['width', 'height'], apis: ['devicePixelRatio'] } });
cv('readback', 'toDataURL, toBlob, captureStream', 'out', 'proposed', 'stub', { match: { canvas: ['toDataURL', 'toBlob', 'captureStream'] }, neutralise: 'toBlob/captureStream are stubs; toDataURL is a renderer query today' });
cv('webgl', 'WebGL, OffscreenCanvas, bitmaprenderer contexts', 'out', 'D6', 'stub', { match: { apis: ['getContext("webgl")', 'getContext("webgl2")', 'OffscreenCanvas'] }, neutralise: 'getContext returns null; OffscreenCanvas throws' });

// ───────────── JS and DOM APIs of the shim ─────────────
const js = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('js', 'js.' + id, title, st, d, t, x);
js('dom.core', 'DOM building: createElement(NS), append/insertBefore/remove, textContent, innerHTML (parsed in the sandbox), attributes, classList, dataset', 'in', 'D11', 'kept', { match: { apis: ['document.createElement', 'Node.appendChild', 'Element.innerHTML', 'Element.classList', 'HTMLElement.dataset'] } });
js('dom.query', 'querySelector(All), getElementById, getElementsBy*, closest, matches', 'in', 'D11', 'kept', { match: { apis: ['querySelector', 'querySelectorAll', 'getElementById', 'closest', 'matches'] } });
js('dom.style', 'element.style (CSSOM properties, setProperty, cssText), className', 'in', 'D11', 'kept', { match: { apis: ['CSSStyleDeclaration.setProperty', 'element.style'] } });
js('dom.events', 'addEventListener (capture and bubble), dispatchEvent of CustomEvent, preventDefault, stopPropagation', 'in', 'D11', 'kept', { match: { apis: ['EventTarget.addEventListener', 'CustomEvent', 'Event.stopPropagation'] } });
js('layout.rect', 'getBoundingClientRect, getClientRects (answered by the renderer)', 'in', 'today', 'host', { match: { apis: ['Element.getBoundingClientRect', 'Element.getClientRects'] } });
js('layout.box', 'offsetWidth/Height/Top/Left, clientWidth/Height, scrollWidth/Height', 'in', 'today', 'host', { match: { apis: ['offsetWidth', 'offsetHeight', 'clientWidth', 'scrollHeight'] } });
js('layout.computed-style', 'getComputedStyle (incl. pseudo-elements and custom properties)', 'in', 'today', 'host', { match: { apis: ['getComputedStyle'] } });
js('layout.match-media', 'matchMedia (prefers-color-scheme answered from the chat theme)', 'in', 'today', 'host', { match: { apis: ['matchMedia'] } });
js('layout.resize-observer', 'ResizeObserver', 'in', 'today', 'host', { match: { apis: ['ResizeObserver'] } });
js('layout.intersection-observer', 'IntersectionObserver (emulated: always visible)', 'in', 'today', 'emulated', { match: { apis: ['IntersectionObserver'] } });
js('layout.element-from-point', 'document.elementFromPoint', 'in', 'today', 'host', { match: { apis: ['document.elementFromPoint'] } });
js('layout.measure-text', 'Text measurement through canvas measureText (fonts of the client)', 'in', 'today', 'host', { match: { apis: ['CanvasRenderingContext2D.measureText'] } });
js('focus', 'element.focus() / blur() / select(), document.activeElement: the widget takes keyboard focus', 'out', 'D4', 'stub', { match: { apis: ['HTMLElement.focus', 'HTMLElement.blur', 'HTMLInputElement.select', 'setSelectionRange'] }, neutralise: 'the call op is dropped: focus stays in the chat input' });
js('timers', 'setTimeout / setInterval / clear*, ordering, clamped delays', 'in', 'today', 'kept', { match: { apis: ['setTimeout', 'setInterval', 'clearTimeout', 'clearInterval'] } });
js('raf', 'requestAnimationFrame / cancelAnimationFrame (frames from the renderer)', 'in', 'today', 'kept', { match: { apis: ['requestAnimationFrame', 'cancelAnimationFrame'] } });
js('microtasks', 'Promise, async/await, queueMicrotask', 'in', 'today', 'kept', { match: { apis: ['Promise', 'queueMicrotask'] } });
js('storage.local', 'localStorage: per (account, chat, message), preloaded, 4 KB, QuotaExceededError past it', 'in', 'D17', 'host', { match: { apis: ['localStorage'] }, note: 'The shim keeps the map (4 KB per area, QuotaExceededError past it); the client preloads it and keeps each commit per message.' });
js('storage.session', 'sessionStorage: same key, in memory until app restart', 'in', 'D17', 'host', { match: { apis: ['sessionStorage'] } });
js('intl', 'Intl.NumberFormat, DateTimeFormat, PluralRules, RelativeTimeFormat, ListFormat, Collator (host ICU)', 'in', 'today', 'host', { match: { apis: ['Intl.NumberFormat', 'Intl.DateTimeFormat', 'Intl.PluralRules', 'Intl.RelativeTimeFormat', 'Intl.ListFormat', 'Intl.Collator'] } });
js('intl.format-to-parts', 'Intl.NumberFormat/DateTimeFormat formatToParts', 'candidate', 'proposed', 'host', { match: { apis: ['formatToParts'] }, note: 'Numbers and dates, in every glue copy.' });
js('locale', 'navigator.language reflects the client locale', 'in', 'proposed', 'host', { match: { apis: ['navigator.language'] }, note: 'navigator.language and the Intl default come from the client\'s locale.' });
js('crypto-random', 'crypto.getRandomValues, crypto.randomUUID, Math.random', 'in', 'today', 'host', { match: { apis: ['crypto.getRandomValues', 'crypto.randomUUID'] } });
js('builtins', 'TextEncoder/TextDecoder, URL, URLSearchParams, structuredClone, AbortController, atob/btoa', 'in', 'today', 'kept', { match: { apis: ['TextEncoder', 'TextDecoder', 'URL', 'URLSearchParams', 'structuredClone', 'AbortController', 'atob', 'btoa'] } });
js('eval', 'eval / new Function inside QuickJS (never in a browser context)', 'in', 'inv1', 'kept', { match: { apis: ['eval', 'Function'] } });
js('css-supports', 'CSS.supports() answered from the profile tables', 'in', 'D23', 'resolved', { match: { apis: ['CSS.supports'] }, note: 'Answers from the profile tables, like @supports.' });
js('visibility', 'document.hidden / visibilityState / visibilitychange when the bubble leaves the screen', 'in', 'today', 'host', { match: { apis: ['document.hidden', 'document.visibilityState'], events: ['visibilitychange'] } });
js('libraries', 'Bundled libraries by CDN name: Chart.js 4, D3 7 (nothing is fetched)', 'candidate', 'proposed', 'stripped', { match: { apis: ['<script src=cdn chart.js>', '<script src=cdn d3>'] } });
js('network', 'fetch, XMLHttpRequest, WebSocket, EventSource, RTCPeerConnection, sendBeacon, importScripts, Worker, BroadcastChannel', 'out', 'inv5', 'stub', { match: { apis: ['fetch', 'XMLHttpRequest', 'WebSocket', 'EventSource', 'RTCPeerConnection', 'navigator.sendBeacon', 'importScripts', 'Worker', 'SharedWorker', 'BroadcastChannel', 'WebTransport'] }, neutralise: 'NetworkDisabledError (fetch rejects with it)' });
js('navigation', 'location assignment, history, window.open (→ openLink), form submission', 'out', 'inv6', 'stub', { match: { apis: ['location', 'history.pushState', 'window.open'] }, neutralise: 'navigation never happens; window.open goes through openLink' });
js('dialogs', 'alert, confirm, prompt, print', 'out', 'today', 'stub', { match: { apis: ['alert', 'confirm', 'prompt', 'print'] }, neutralise: 'non-blocking stubs: confirm → false, prompt → null' });
js('device', 'clipboard, share, vibrate, geolocation, mediaDevices, Notification', 'out', 'D8', 'stub', { match: { apis: ['navigator.clipboard', 'navigator.share', 'navigator.vibrate', 'navigator.geolocation', 'navigator.mediaDevices'] }, neutralise: 'rejected promises / undefined; haptics come from the SDK' });
js('shadow-dom', 'attachShadow, customElements.define', 'out', 'today', 'stub', { match: { apis: ['Element.attachShadow', 'customElements.define'] }, neutralise: 'attachShadow throws NotSupportedError, customElements.define is a no-op' });
js('blob-file', 'Blob, FileReader, URL.createObjectURL', 'out', 'inv4', 'stub', { match: { apis: ['Blob', 'FileReader', 'URL.createObjectURL'] }, neutralise: 'inert stubs' });
js('fullscreen', 'requestFullscreen', 'out', 'D12a', 'stub', { match: { apis: ['Element.requestFullscreen'] }, neutralise: 'rejects' });

// ───────────── TelegramWidget SDK ─────────────
const tw = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('sdk', 'tw.' + id, title, st, d, t, x);
tw('ready', 'TelegramWidget.ready()', 'in', 'D8', 'kept', { match: { apis: ['TelegramWidget.ready'] } });
tw('theme', 'TelegramWidget.colorScheme ("light" | "dark"); theme and onTheme(cb) kept as deprecated aliases', 'in', 'SDK', 'kept', { match: { apis: ['TelegramWidget.colorScheme', 'TelegramWidget.theme', 'TelegramWidget.onTheme'] } });
tw('theme-params', 'themeParams (every Mini Apps key, #rrggbb) and the --tg-theme-* / --tg-color-scheme variables on the root, updated in one task with themeChanged', 'in', 'SDK', 'kept', { match: { apis: ['TelegramWidget.themeParams'], properties: ['--tg-theme-*', '--tg-color-scheme'] } });
tw('events', 'onEvent / offEvent for themeChanged, activated, deactivated; at most 16 handlers per event type', 'in', 'SDK', 'kept', { match: { apis: ['TelegramWidget.onEvent', 'TelegramWidget.offEvent'] } });
tw('is-active', 'TelegramWidget.isActive mirrors visibility (activated / deactivated)', 'in', 'SDK', 'kept', { match: { apis: ['TelegramWidget.isActive'] } });
tw('resize-anchor', 'TelegramWidget.setResizeAnchor(\'top\' | \'bottom\'): the edge of the bubble that stays put when the widget\'s height changes', 'in', 'R1', 'host', { match: { apis: ['TelegramWidget.setResizeAnchor'] }, note: 'v0.1.3: detect it with typeof TelegramWidget.setResizeAnchor === \'function\'; anything but \'top\' or \'bottom\' throws TypeError; the host reports it with the height.' });
tw('content-safe-area', 'TelegramWidget.contentSafeAreaInset { top, right, bottom, left, cornerLeft, cornerRight } (logical px, frozen, a fresh object per change) and contentSafeAreaChanged, with the --tg-content-safe-area-inset-* / -corner-left|right variables on the root, updated in one task', 'in', 'P1-safe-area', 'host', { match: { apis: ['TelegramWidget.contentSafeAreaInset'], properties: ['--tg-content-safe-area-*'] }, note: 'bottom is the height of the band the client draws the message time in over a widget with no caption below it; cornerRight (left-to-right interface) or cornerLeft (right-to-left) the width of its corner in that band; 0 with a caption below.' });
tw('bot-data', 'TelegramWidget.chatData and publicData: the bot\'s two strings (text of at most 1024 bytes each, read-only, null for none); chatDataChanged / publicDataChanged when they change', 'in', 'I1', 'host', { match: { apis: ['TelegramWidget.chatData', 'TelegramWidget.publicData'] }, note: 'chatData belongs to one original message (in a group every member sees it) and is null in a forward; publicData is the forward\'s snapshot or a value shared with the original; one change of both fires both, chat first; an unchanged value fires nothing.' });
tw('context', 'TelegramWidget.isForward, chatType and viewer (a WebAppUser): a forwarded copy, the kind of chat, who looks; no chatType in a forward, no viewer where the widget may not ask the bot', 'in', 'I1', 'host', { match: { apis: ['TelegramWidget.isForward', 'TelegramWidget.chatType', 'TelegramWidget.viewer'] }, note: 'viewer is shaped like Mini Apps\' WebAppUser (id, first_name, last_name, username, is_premium only when true; names 1-64 UTF-16 code units, a username 5-32 of [A-Za-z0-9_]; a field that breaks its rule is left out); chatType is private, group, supergroup or channel and null in every forward (a copy is told nothing about its chat); viewer only where the message has can_query (an original sent with allow_queries, a forward of one sent with fwd_allow_queries), else null.' });
tw('request', 'TelegramWidget.request(data): a callback button inside the widget; data of 1-64 bytes, resolves { result } (text of at most 1024 bytes), rejects with error.code; where it may ask is the server\'s flag per message', 'in', 'I1', 'host', { match: { apis: ['TelegramWidget.request'] }, note: 'On a tap where can_query (originals of a widget sent with allow_queries, forwards of one sent with fwd_allow_queries; the client shows a network marker there), in the background only where background_query (the original in the private chat with the bot) while visible: one when the widget appears on screen, then one per 5 s; one in flight; the answer carries no data.' });
tw('copy-text', 'TelegramWidget.copyText(text): copies 1-256 characters on a tap; the client\'s toast shows the copied text', 'in', 'I1', 'host', { match: { apis: ['TelegramWidget.copyText'] }, note: 'One call per tap, shared with the links and request; outside a tap dropped with activation-blocked; navigator.clipboard stays a stub.' });
tw('watch-public-data', 'TelegramWidget.watchPublicData() / unwatchPublicData(): the widget asks for live shared data; the client polls a shared widget\'s public_data only while it watches and is on screen', 'in', 'I1', 'host', { match: { apis: ['TelegramWidget.watchPublicData', 'TelegramWidget.unwatchPublicData'] }, note: 'No tap needed, idempotent; the shim sends only changes and the host keeps the latest (web onWatchPublicData and watchesPublicData, the native diagnostics); a widget that does not watch gets public_data with the message and the bot\'s pushed edits only; a preview never watches.' });
tw('preview', 'TelegramWidget.isPreview: an inline result shown in the list before it is sent', 'in', 'I1', 'host', { match: { apis: ['TelegramWidget.isPreview'] }, note: 'A preview has its data but no input, no request, link, copy or haptic (only ready and setResizeAnchor), no chatType or viewer, and Web Storage that is never committed.' });
tw('open-link', 'openLink(url): an absolute https URL of at most 256 characters, no user info, not a t.me host; one call per pointer press; the client confirms showing the full URL', 'in', 'SDK', 'host', { match: { apis: ['TelegramWidget.openLink'] }, note: 'Invalid arguments throw TypeError; one call per tap reaches the client.' });
tw('open-telegram-link', 'openTelegramLink(url): any https://t.me/… (telegram.me, telegram.dog) path, at most 256 characters, opened inside the client; tg: URLs refused', 'in', 'SDK', 'host', { match: { apis: ['TelegramWidget.openTelegramLink'] } });
tw('haptic', 'HapticFeedback: impactOccurred(light|medium|heavy|rigid|soft), notificationOccurred(error|success|warning), selectionChanged(); visible widgets only, during a touch or in one 1 s series without one (v0.2.2)', 'in', 'SDK', 'host', { match: { apis: ['TelegramWidget.HapticFeedback'] }, note: 'Desktop and web accept the calls and play nothing.' });
tw('profile', 'TelegramWidget.profile (integer) and isProfileAtLeast(n)', 'in', 'D23', 'kept', { match: { apis: ['TelegramWidget.profile', 'TelegramWidget.isProfileAtLeast'] } });
tw('send-message', 'TelegramWidget.sendMessage', 'out', 'D8', 'missing', { match: { apis: ['TelegramWidget.sendMessage'] }, neutralise: 'removed from the SDK' });

// ───────────── Input ─────────────
const inp = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('input', 'input.' + id, title, st, d, t, x);
inp('tap', 'Tap: click on the element under the finger, with clientX/Y and target', 'in', 'D4', 'kept', { match: { events: ['click'] } });
inp('pointer', 'pointerdown / pointerup / pointercancel with coordinates, pointerId, pointerType, isPrimary', 'in', 'D4', 'kept', { match: { events: ['pointerdown', 'pointerup', 'pointercancel'] } });
inp('mouse-compat', 'mousedown / mouseup compatibility events of a tap', 'in', 'D4', 'kept', { match: { events: ['mousedown', 'mouseup'] } });
inp('touch-events', 'touchstart / touchmove / touchend (touch clients only)', 'candidate', 'proposed', 'stripped', { match: { events: ['touchstart', 'touchmove', 'touchend', 'touchcancel'] }, note: 'Desktop clients have none; widgets should use pointer events.' });
inp('drag', 'Drag: pointermove with buttons while pressed, setPointerCapture, movementX/Y', 'in', 'D4', 'kept', { match: { events: ['pointermove'], apis: ['setPointerCapture', 'releasePointerCapture'] }, note: 'v1 (not the PoC): drag with gesture arbitration.' });
inp('hover-events', 'pointerover/enter/leave/out, mouseover/enter/leave (desktop pointer only)', 'in', 'D3', 'kept', { match: { events: ['pointerover', 'pointerenter', 'pointerleave', 'pointerout', 'mouseover', 'mouseenter', 'mouseleave', 'mouseout', 'mousemove'] } });
inp('dblclick', 'dblclick / double tap: a double tap over the widget is always the widget\'s, on every element (the reaction stays outside the widget)', 'in', 'G1', 'emulated', { match: { events: ['dblclick'] }, note: 'v0.1.2: both clicks, then the renderer\'s own dblclick (gesture-policy.ts: 300 ms, 40 px); the engine\'s own never reaches the widget.' });
inp('long-press', 'contextmenu / long press (or right click) claimed by touch-action: none on the element or an ancestor; on auto the hold is the bubble\'s message menu and the widget gets pointercancel', 'in', 'G1', 'emulated', { match: { events: ['contextmenu'] }, note: 'v0.1.2: the renderer sends its own contextmenu at a 500 ms hold within 10 px or a right-button press; the platform menu stays cancelled. v0.1.3: a press whose first move past the slop is vertical is the chat\'s scroll, also on none and after the hold.' });
inp('keyboard', 'Key events: keydown / keyup / keypress, beforeinput / input from keys, focus / blur / focusin / focusout from keys', 'out', 'D4', 'stripped', { match: { events: ['keydown', 'keyup', 'keypress', 'beforeinput', 'input', 'focus', 'blur', 'focusin', 'focusout'] }, neutralise: 'the renderer never forwards key events; the widget never owns keyboard focus' });
inp('key-activation', 'Enter / Space activate a focused button or link (a click from the keyboard)', 'out', 'D4', 'stripped', { match: { events: ['click'] }, neutralise: 'keys do nothing in a widget: no synthetic click from Enter or Space' });
inp('key-gesture', 'A key press counts as a user gesture for privileged bridge calls', 'out', 'D4', 'stripped', { match: { apis: ['TelegramWidget.openLink'] }, neutralise: 'only taps (pointer presses) grant a gesture' });
inp('wheel-scroll', 'wheel and scroll events', 'out', 'D3a', 'stripped', { match: { events: ['wheel', 'scroll'] }, neutralise: 'no scrolling inside a widget' });
inp('user-activation', 'Privileged calls (openLink) only in the task of a real tap; not from timers or synthetic clicks', 'in', 'D8', 'kept', { match: { apis: ['TelegramWidget.openLink'] } });
inp('prevent-default', 'preventDefault on pointer events keeps the gesture in the widget (learned by the renderer)', 'in', 'D4', 'kept', { match: { apis: ['Event.preventDefault'] } });

// ───────────── Limits ─────────────
const lim = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('limit', 'limit.' + id, title, st, d, t, x);
lim('text-node', 'Text values (text node, canvas string, Path2D data) up to 32 K UTF-16 units; past it the widget stops (strict)', 'in', 'D5', 'kept');
lim('text-budget', 'Document text 48 K units, past it the widget stops; complex-script text 8192 units, past it the text is refused (strict)', 'in', 'D5', 'kept');
lim('nodes', "At most 2000 nodes (elements, text, comments) in the document once a batch is applied; past it the widget stops (strict)", 'in', 'today', 'kept');
lim('timers', 'At most 64 live timers (RangeError past it)', 'in', 'today', 'kept');
lim('cpu', "CPU quota 300 ms of JS per second (timers deferred, not killed; a task that runs past it makes the next ones wait longer); one task may run 500 ms; at most 128 layout queries in one task, and their time at most 1000 ms; past these the widget stops", 'in', 'today', 'kept');
lim('memory', 'QuickJS heap 8 MB', 'in', 'today', 'kept');
lim('storage', 'localStorage and sessionStorage: 4 KB per area and message (4096 bytes of keys and values in UTF-8); over it setItem throws QuotaExceededError and stores nothing', 'in', 'SDK', 'kept');
lim('attribute', 'Attribute and inline-style values up to 8 K UTF-16 units; past it the widget stops (strict)', 'in', 'L1', 'kept');
lim('batch', "At most 10 000 operations in one batch (the shim hands a task's DOM changes over in batches of about 1000, its element method calls in one); past it the widget stops (strict)", 'in', 'L1', 'kept');
lim('css-rules', "At most 400 CSS rules and 32 @keyframes in the document's stylesheets; past them the widget stops (strict)", 'in', 'L1', 'kept');
lim('canvas-commands', "At most 4000 canvas commands per canvas in one task (RangeError past it; strict)", 'in', 'L1', 'kept');
lim('animations', 'At most 64 running script animations (further ones are not rendered; strict)', 'in', 'L1', 'kept');
lim('main-thread', "The host's main-thread work for a widget, its layout queries plus 25 µs per operation and 2 µs per canvas command, at most 100 ms per second (timers and frames deferred past it)", 'in', 'L1', 'host');
lim('canvas-pixels', "A widget's canvases hold at most 2048 × 2048 pixels together; past it the widget stops (strict; the renderer itself never applies a size past 4096 × 4096)", 'in', 'L1', 'kept');
lim('paint-heavy', 'At most 16 heavy boxes at once: a blurred box-shadow, a filter, a backdrop-filter, opacity below 1, or a running animation of one of them (shadows animated without blur do not count, nor the opacity of a disabled or pressed <button>: its own dimming); elements under display: none and a ::before / ::after without content are no boxes; past it the widget stops (strict; every renderer counts on the computed style)', 'in', 'L2', 'host');
lim('paint-backdrop', 'Of the heavy boxes at most 2 with backdrop-filter; past it the widget stops (strict)', 'in', 'L2', 'host');
lim('image-pixels', "A data: image of at most 1024 × 1024 pixels, as its bytes say (larger: the placeholder); bytes of none of the profile's image formats are a broken image", 'in', 'L1', 'kept');
lim('url-length', 'openLink / openTelegramLink URLs of at most 256 characters (TypeError past it)', 'in', 'SDK', 'kept');
lim('event-handlers', 'At most 16 handlers per event type in onEvent (more are ignored)', 'in', 'SDK', 'kept');
lim('haptic-rate', 'Haptics: a bucket of 5 calls refilled at 10 per second, and a touch: free while a finger is down on the widget and 5 s after it is lifted, otherwise one 1 s series (the next after a touch or 10 s later); the rest dropped (haptic-limit)', 'in', 'SDK', 'kept');
lim('html-size', 'HTML up to 32 KB uncompressed and 4 KB after zlib compression (server-side)', 'in', 'D20', 'host', { note: 'Checked by the server, not the client. Every conformance widget stays under it (build.ts keeps each one under 3800 bytes after zlib at level 9, as the server compresses) and reaches a client from a test bot.' });

// ───────────── Client behaviour ─────────────
const b = (id: string, title: string, st: Status, d: string, t: Today, x: Partial<Feature> = {}) => add('behaviour', 'behaviour.' + id, title, st, d, t, x);
b('theme-switch', 'Live theme switch: prefers-color-scheme, light-dark() and onTheme follow the chat theme without a reload', 'in', 'D14', 'host');
b('offscreen-pause', 'Off-screen: frames stop, timers at most 1 Hz, visibilitychange fires', 'in', 'today', 'host');
b('auto-height', 'The bubble takes the widget height; above the cap it is clipped hard', 'in', 'D12a', 'host');
b('no-selection', 'Text is not selectable by default (a drag over text selects nothing); user-select: text enables it', 'in', 'D18', 'kept');
b('restart', 'After 2 consecutive crashes the widget stays stopped on its last frame with the "Widget stopped" badge; before that the client rebuilds it when shown again', 'in', 'SDK', 'host', { note: 'The frozen frame and badge are the framework\'s; the restart count is the client\'s.' });
b('gesture-claim', 'touch-action is the claim: taps and double taps over the widget are always its own, vertical drags always the chat\'s; on auto holds and horizontal swipes are the bubble\'s (the widget gets pointercancel); pan-y gives the widget horizontal drags; none gives it horizontal drags and holds; pan-x claims nothing; the leading 20 px and the bubble outside the widget never take a claim', 'in', 'G2', 'host', { note: 'onGestureClaim (x, y, hold) per primary pointerdown, y always false since v0.1.3; the client\'s recognizers follow the shared gesture policy (v0.1.2; vertical drags the chat\'s since v0.1.3, G2).' });
b('storage-persist', 'localStorage survives the widget being dropped and re-created, and an app restart; sessionStorage survives re-creation but not a restart; both are deleted with the message', 'in', 'SDK', 'host');
b('storage-forward', 'A forwarded copy has its own storage, keyed by its own chat and message, so it starts empty', 'in', 'SDK', 'host', { note: 'Testable with a test bot: forward its message into another chat; the copy starts with empty storage.' });
b('message-edit', 'The HTML of a sent widget never changes (an edit may change only the caption): a bot changes only chatData and publicData (behaviour.data-edit)', 'in', 'I1', 'host', { note: 'visual/sdk/16-bot-edit still tests an HTML edit, which the server does not allow, and is to be replaced by a data-edit case once the server ships editMessageWidgetData.' });
b('resize-anchor', 'When an auto height changes, the client keeps the widget\'s anchored edge in place on screen: top (default), the content below moves; bottom, the content above moves; also from a cached height and after a re-creation', 'in', 'R1', 'host', { note: 'v0.1.3: WidgetDiagnostics.resizeAnchor / web onResize(height, anchor); the old pixels stay aligned to the anchored edge during a height animation.' });
b('time-corner', 'A widget with no caption below it (none, or one shown above it) gets the message time and status over its bottom corner, on a backing as on a photo, and taps on it are the client\'s; the client passes the band (contentSafeArea bottom) and its corner (cornerRight, or cornerLeft in a right-to-left interface) before the first render and changes them only with the bubble\'s layout (a caption added, removed or moved above or below by an edit, "edited"); with a caption below the time is in the caption\'s line and the safe area is 0', 'in', 'P1-safe-area', 'host', { note: 'WidgetHost contentSafeArea / setContentSafeArea on every host; never per frame; the floating day pill has no contract and is drawn over the widget like over a photo.' });
b('data-edit', 'A change of the bot\'s data reaches the running widget as chatDataChanged / publicDataChanged (the client\'s setData with the full state: the server\'s update, or the polled shared public_data): no new host, no restart', 'in', 'I1', 'host', { note: 'The client keeps the values with the message; a shared widget\'s public_data (widget_id) is polled for one visible carrier per id, one message per call, at the object\'s period and applied to every widget with that id, forwards included; an answer carries no data; testable with a test bot once the server ships.' });
b('forward-copy', 'A forwarded copy never has chatData; its publicData is the forward\'s snapshot or the shared value; it is told nothing about the chat it is in (no chatType); it asks the bot only on a tap and only if the widget was sent with fwd_allow_queries, and otherwise has no viewer either', 'in', 'I1', 'host', { note: 'The client passes the server\'s flags (is_copy, can_query); the framework drops chatData and chatType of a copy, and viewer of any message without can_query.' });
b('preview', 'A widget in a list of inline results is a preview: the client creates it with context.preview and the result\'s data, routes no touches to it (a tap selects the result) and creates a new host for the sent message', 'in', 'I1', 'host', { note: 'The host drops the preview\'s input, every bridge call but ready and setResizeAnchor, and its storage commits; chatType and viewer are null.' });
b('network-marker', 'The network marker: the client marks a widget whose tap may send data to the bot\'s server exactly where the message has can_query (a UI contract of the server\'s spec)', 'in', 'I1', 'host', { note: 'Never without allow_queries, never on a forward of a widget without fwd_allow_queries, never on a preview; the client\'s own UI from context.canQuery (docs/integration.md).' });
b('size-hint', 'Before load the bubble takes the bot\'s size anchors (width 0 = the bubble\'s width, W = a fixed width in [200, max]; height 0 = the placeholder, then the content height in [50, 600]; H = a fixed clamp(H, 50, 600)), cached per message', 'in', 'D12a', 'host', { note: 'Testable with a test bot sending inputMediaWidget with the anchors set; 200 / 50 / 600 are the shared v0.1.0 constants.' });
b('policy-strict', 'strict policy on every client (gesture gate, text caps, budgets)', 'in', 'D5', 'kept');

// ───────────── Profile 1, frozen 2026-10-17 ─────────────
// packages/core/profile.json (claude/profile-v1 83da388) is the single source of the
// profile; this overlay copies its statuses, decisions and neutralisations onto the
// catalogue so the two agree on branches that do not have the file yet.
// emit-catalogue.ts overlays the file itself when it is present.
const PROFILE_1: Record<string, Partial<Feature>> = {
  "html.el.ruby": {"status": "out", "decision": "P1", "neutralise": "rendered as <span> (the annotation shows inline)"},
  "html.el.legacy": {"status": "out", "decision": "P1", "neutralise": "<center> becomes a <div>, the others a <span>; the presentational attributes are dropped"},
  "html.el.img-svg": {"status": "out", "decision": "P1", "neutralise": "url-policy refuses the MIME: placeholder image"},
  "html.el.img-other-formats": {"status": "out", "decision": "P1", "neutralise": "url-policy refuses the MIME: placeholder image"},
  "html.el.details": {"status": "in", "decision": "P1"},
  "html.el.a-fragment": {"status": "in", "decision": "P1"},
  "html.el.forms": {"status": "out", "decision": "D3a", "neutralise": "controls are dropped with their subtree (input, select, option, optgroup, textarea, datalist, fieldset, progress, meter); <form> becomes a <div>, <label>, <legend> and <output> a <span>"},
  "html.el.progress-meter": {"status": "out", "decision": "P1", "neutralise": "dropped with the subtree, like the other controls"},
  "html.el.dialog-popover": {"status": "out", "decision": "P1", "neutralise": "a dialog renders as a <div>; the popover attributes and the dialog and popover methods are dropped"},
  "html.el.unknown": {"status": "in", "decision": "P1"},
  "html.el.media": {"status": "out", "decision": "P1", "neutralise": "dropped with the subtree"},
  "html.attr.legacy-presentational": {"status": "out", "decision": "P1", "neutralise": "the attributes are dropped"},
  "html.attr.event-handlers": {"status": "in", "decision": "P1"},
  "html.attr.contenteditable": {"status": "out", "decision": "P1", "neutralise": "drop the attribute"},
  "svg.gradients": {"status": "in", "decision": "P1"},
  "svg.css": {"status": "in", "decision": "P1"},
  "svg.clip-mask": {"status": "out", "decision": "P1", "neutralise": "the elements are dropped with their subtree; clip-path, mask and marker attributes are dropped"},
  "svg.filters": {"status": "out", "decision": "P1", "neutralise": "filter and the fe* elements are dropped with their subtree; the filter attribute is dropped"},
  "svg.smil": {"status": "out", "decision": "P1", "neutralise": "animate, animateTransform, animateMotion and mpath are dropped"},
  "svg.foreign-object": {"status": "out", "decision": "P1", "neutralise": "drop the subtree"},
  "svg.image": {"status": "out", "decision": "P1", "neutralise": "dropped"},
  "css.display.grid": {"status": "in", "decision": "P1-grid"},
  "css.display.other": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.box.logical": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.box.columns": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.box.object-fit": {"status": "in", "decision": "P1"},
  "css.position.fixed": {"status": "in", "decision": "P1"},
  "css.position.sticky": {"status": "out", "decision": "P1", "neutralise": "becomes relative (no scrolling inside a widget)"},
  "css.color.modern": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped (an earlier fallback declaration applies)"},
  "css.color.mix": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.color.light-dark": {"status": "in", "decision": "P1"},
  "css.background.clip-text": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.gradient.conic": {"status": "in", "decision": "P1"},
  "css.font.ui-families": {"status": "in", "decision": "P1"},
  "css.font.variant-numeric": {"status": "in", "decision": "P1"},
  "css.text.overflow": {"status": "in", "decision": "P1"},
  "css.text.line-clamp": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.text.shadow": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.text.writing-mode": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.text.selection": {"status": "in", "decision": "D18"},
  "css.filter.other": {"status": "out", "decision": "P1", "neutralise": "the function is removed from the filter list; an empty list drops the declaration"},
  "css.filter.url": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.blend": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.clip-path": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.mask": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.pointer-events": {"status": "in", "decision": "P1"},
  "css.touch-action": {"status": "in", "decision": "P1"},
  "css.value.vw": {"status": "in", "decision": "P1"},
  "css.value.vh": {"status": "out", "decision": "P1", "neutralise": "the declaration is dropped"},
  "css.value.env": {"status": "in", "decision": "P1-safe-area"},
  "sel.has": {"status": "out", "decision": "P1", "neutralise": "the selector never matches (dropped from its list)"},
  "sel.first-letter-line": {"status": "out", "decision": "P1", "neutralise": "the selector never matches (dropped from its list)"},
  "sel.active": {"status": "in", "decision": "P1"},
  "sel.focus": {"status": "out", "decision": "D4", "neutralise": "the selector never matches (dropped from its list); the widget never owns keyboard focus"},
  "sel.form-state": {"status": "out", "decision": "D3a", "neutralise": ":disabled and :enabled become [disabled] and :not([disabled]) (the <button> state); the other form states never match"},
  "sel.host-scoped": {"status": "out", "decision": "inv6", "neutralise": "the selector never matches (dropped from its list) on every renderer"},
  "sel.target": {"status": "out", "decision": "P1", "neutralise": "the selector never matches (dropped from its list)"},
  "at.media.reduced-motion": {"status": "in", "decision": "P1"},
  "at.media.hover-pointer": {"status": "out", "decision": "P1", "neutralise": "the @media rule is dropped; matchMedia answers false"},
  "at.media.other": {"status": "out", "decision": "P1", "neutralise": "the @media rule is dropped; matchMedia answers false"},
  "at.container": {"status": "out", "decision": "P1", "neutralise": "the at-rule is dropped; container properties are dropped"},
  "at.layer": {"status": "out", "decision": "P1", "neutralise": "the at-rule is dropped with its rules"},
  "at.property": {"status": "out", "decision": "P1", "neutralise": "the at-rule is dropped"},
  "at.exotic": {"status": "out", "decision": "P1", "neutralise": "dropped like unknown at-rules"},
  "at.nesting": {"status": "out", "decision": "P1", "neutralise": "nested rules are dropped with a css-dropped diagnostic"},
  "canvas.conic-gradient": {"status": "out", "decision": "P1", "neutralise": "createConicGradient returns a gradient that draws nothing"},
  "canvas.pattern": {"status": "out", "decision": "P1", "neutralise": "createPattern returns a pattern that draws nothing"},
  "canvas.composite": {"status": "out", "decision": "P1", "neutralise": "the property is not applied (source-over)"},
  "canvas.shadow": {"status": "out", "decision": "P1", "neutralise": "the properties are not applied"},
  "canvas.filter": {"status": "out", "decision": "P1", "neutralise": "the property is not applied"},
  "canvas.image-data": {"status": "out", "decision": "P1", "neutralise": "getImageData returns transparent pixels, putImageData draws nothing"},
  "canvas.hit-test": {"status": "in", "decision": "P1"},
  "canvas.readback": {"status": "out", "decision": "P1", "neutralise": "toBlob/captureStream are stubs; toDataURL returns an empty image (data:,)"},
  "js.layout.rect": {"status": "in", "decision": "P1"},
  "js.layout.box": {"status": "in", "decision": "P1"},
  "js.layout.computed-style": {"status": "in", "decision": "P1"},
  "js.layout.match-media": {"status": "in", "decision": "P1"},
  "js.layout.resize-observer": {"status": "in", "decision": "P1"},
  "js.layout.intersection-observer": {"status": "in", "decision": "P1"},
  "js.layout.element-from-point": {"status": "in", "decision": "P1"},
  "js.layout.measure-text": {"status": "in", "decision": "P1"},
  "js.focus": {"status": "out", "decision": "D4", "neutralise": "focus(), blur(), select() and setSelectionRange() do nothing; no call op reaches the renderer"},
  "js.timers": {"status": "in", "decision": "P1"},
  "js.raf": {"status": "in", "decision": "P1"},
  "js.microtasks": {"status": "in", "decision": "P1"},
  "js.intl": {"status": "in", "decision": "P1"},
  "js.intl.format-to-parts": {"status": "in", "decision": "P1"},
  "js.locale": {"status": "in", "decision": "P1"},
  "js.crypto-random": {"status": "in", "decision": "P1"},
  "js.builtins": {"status": "in", "decision": "P1"},
  "js.visibility": {"status": "in", "decision": "P1"},
  "js.libraries": {"status": "out", "decision": "P1-libs", "neutralise": "nothing is bundled: a <script src> to a CDN is an external script like any other and is not run"},
  "js.dialogs": {"status": "out", "decision": "P1", "neutralise": "non-blocking stubs: confirm → false, prompt → null"},
  "js.shadow-dom": {"status": "out", "decision": "P1", "neutralise": "attachShadow throws NotSupportedError, customElements.define is a no-op"},
  "tw.theme": {"status": "in", "decision": "D14"},
  "tw.theme-params": {"status": "in", "decision": "D14"},
  "tw.open-link": {"status": "in", "decision": "D8"},
  "tw.open-telegram-link": {"status": "in", "decision": "D8"},
  "tw.haptic": {"status": "in", "decision": "D8"},
  "input.touch-events": {"status": "out", "decision": "D4", "neutralise": "the renderer never forwards touch events; use pointer events"},
  "input.dblclick": {"status": "in", "decision": "G1"},
  "input.long-press": {"status": "in", "decision": "G1"},
  "input.wheel-scroll": {"status": "out", "decision": "D3a", "neutralise": "the renderer never forwards wheel or scroll; no scrolling inside a widget"},
  "limit.nodes": {"status": "in", "decision": "P1"},
  "limit.timers": {"status": "in", "decision": "P1"},
  "limit.cpu": {"status": "in", "decision": "P1"},
  "limit.memory": {"status": "in", "decision": "P1"},
  "limit.storage": {"status": "in", "decision": "D17"},
  "behaviour.offscreen-pause": {"status": "in", "decision": "P1"},
};
for (const f of F) if (PROFILE_1[f.id]) Object.assign(f, PROFILE_1[f.id]);

export const CATALOGUE: Feature[] = F;
export const CATALOGUE_VERSION = 1;

/** Decisions referenced above, for the generated table. */
export const DECISIONS: Record<string, string> = {
  D3: 'A feature is in when every client draws it; :hover is the exception (progressive enhancement)',
  D3a: 'v1: canvas 2D; Web Animations, filter, backdrop-filter if clients draw them; out: forms, inner scroll, 3D transforms, magic move; CSS animations and transitions in',
  D3b: 'Fonts: system generic families only, other names dropped',
  D4: 'Input: taps in the PoC, drag in v1; no keyboard at all, not even Enter or Space (it conflicts with the chat input field; Artem, 2026-10-16)',
  D5: 'strict policy everywhere; webAnimations and morphUpdates from the profile',
  D6: '3D (__tw3d) cut from the release',
  D8: 'SDK outward: openLink (confirmed with the full URL) and openTelegramLink (no confirmation of its own, 2026-10-29) on a tap, haptics; no sendMessage',
  SDK: 'SDK v1 (docs/sdk-v1.md), with Artem\'s answers of 2026-10-17: haptics without a gesture (bucket 5, 10 per second), any t.me path, URLs of at most 256 characters, 16 handlers per event type, stopped after 2 crashes in a row, double tap and long press stay with the bubble',
  D11: 'One widget model: classic DOM in the sandbox',
  D12a: 'Above the cap: hard clip, no fullscreen',
  D14: 'Theme: themeParams + --tg-theme-*, theme change event',
  D17: 'State: localStorage per (account, chat, message id): the chat the message is in, as message ids are unique only within a chat (supergroups); 4 KB, QuotaExceededError; sessionStorage in memory',
  L1: 'Strict limits from the start (2026-10-29): about twice what real widgets use; past a counted cap the widget stops (docs/decisions.md)',
  L2: 'Heavy boxes (Artem, 2026-11-02, after the client teams\' stress-kit runs on five devices): at most 16 heavy boxes at once (a blurred box-shadow layer, a filter, a backdrop-filter, opacity below 1 but a disabled or pressed <button>\'s, or a running animation of filter, backdrop-filter or opacity, or of box-shadow through a blurred layer), of them at most 2 with backdrop-filter; every renderer counts on the computed style (packages/core/src/shared/paint-policy.ts and its fixture) and past either the widget stops. A tightening of profile 1 (widgets were not launched yet), not profile 2',
  D18: 'Text is not selectable by default; user-select: text enables it (2026-10-20)',
  D20: 'Mini-HTML (2026-10-17): a widget is one HTML document of at most 4 KB compressed and 32 KB uncompressed, delivered in the message; the server compresses the HTML from the bot (zlib at level 9 since 2026-10-24) and enforces both limits',
  D21: 'No accessibility in v1',
  D23: 'Runtime profile detection: TelegramWidget.profile / isProfileAtLeast, CSS.supports and @supports from the profile',
  brief: 'Named as a candidate in the conformance brief (decided by measurement)',
  inv1: 'Invariant 1: widget JS only in QuickJS',
  inv2: 'Invariant 2: the host validates everything against the allowlists',
  inv4: 'Invariant 4: only data: images, host blob: and #fragment URLs reach the renderer',
  inv5: 'Invariant 5: no network in QuickJS',
  inv6: 'Invariant 6: navigation cancelled, shadow scope',
  proposed: 'Proposed by this catalogue, to be confirmed',
  "P1": "Profile v1 freeze (2026-10-17): a candidate is in when the corpus uses it and the web renderers draw it; the rest are out until the native renderers are measured (profile 2)",
  "P1-grid": "Grid is in on condition that the Swift renderer and TwidgetCore implement it before their releases (2026-10-17)",
  "P1-libs": "No bundled libraries in v1 (2026-10-17)",
};
