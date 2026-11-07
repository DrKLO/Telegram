# Widget profile feature catalogue

Generated from `scripts/conformance/catalogue.ts` (`corepack pnpm tsx scripts/conformance/emit-catalogue.ts`); machine-readable: [catalogue.json](catalogue.json). 279 features: 201 in (4 conditional on every renderer drawing them), 78 out, 0 candidates. Decision `proposed` marks this catalogue's own proposals (0).

| Area | Features | in | out | candidate | covered by tests |
|---|---|---|---|---|---|
| html | 34 | 20 | 14 | 0 | 34 |
| svg | 10 | 5 | 5 | 0 | 10 |
| css | 76 | 55 | 21 | 0 | 76 |
| selector | 13 | 7 | 6 | 0 | 13 |
| at-rule | 13 | 5 | 8 | 0 | 13 |
| animation | 8 | 7 | 1 | 0 | 8 |
| canvas | 21 | 13 | 8 | 0 | 21 |
| js | 34 | 25 | 9 | 0 | 34 |
| sdk | 18 | 17 | 1 | 0 | 18 |
| input | 14 | 9 | 5 | 0 | 14 |
| limit | 21 | 21 | 0 | 0 | 16 |
| behaviour | 17 | 17 | 0 | 0 | 17 |
| **all** | 279 | 201 | 78 | 0 | 274 |

Columns: **status** (in / out / candidate; "in*" = in if every renderer draws it, D3a), **decision** (see the legend at the end), **today** (what the pipeline of the tag does with it under `strict`: profile 1 enforced, SDK v1; legend below), **tests** (conformance widgets that judge it).

## html

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `html.el.structure` | div, span, p, br, hr, wbr | in | D3a | kept |  | auto/01-html-elements-a, auto/22-ua-defaults, visual/14-unstyled-document, visual/15-body-background |
| `html.el.headings` | h1–h6 with UA default sizes and margins | in | D3a | kept |  | auto/01-html-elements-a, auto/22-ua-defaults, visual/14-unstyled-document |
| `html.el.sections` | header, footer, main, nav, section, article, aside, hgroup, search, address | in | D3a | kept |  | auto/01-html-elements-a |
| `html.el.inline-text` | Inline text semantics with UA styles: b, i, u, s, em, strong, small, mark, code, kbd, samp, var, abbr, cite, q, del, ins, dfn, time, data | in | D3a | kept |  | auto/01-html-elements-a, auto/22-ua-defaults, visual/14-unstyled-document |
| `html.el.sub-sup` | sub, sup (baseline shift, smaller size) | in | D3a | kept |  | auto/01-html-elements-c, auto/22-ua-defaults |
| `html.el.pre` | pre (monospace, preserved white space and line breaks) | in | D3a | kept |  | auto/01-html-elements-b, auto/22-ua-defaults, visual/14-unstyled-document |
| `html.el.blockquote` | blockquote, figure, figcaption (UA indents) | in | D3a | kept |  | auto/01-html-elements-b, auto/22-ua-defaults, visual/14-unstyled-document |
| `html.el.lists` | ul, ol, li, dl, dt, dd, menu with markers | in | D3a | kept |  | auto/01-html-elements-b, auto/22-ua-defaults, visual/14-unstyled-document |
| `html.el.table` | table, thead, tbody, tfoot, tr, td, th, caption, colgroup, col; colspan/rowspan | in | D3a | kept |  | auto/01-html-elements-b, auto/22-ua-defaults, visual/04-layout, visual/14-unstyled-document |
| `html.el.ruby` | ruby, rt, rp, bdi, bdo<br><sub>Annotation layout is rarely implemented by small engines.</sub> | out | P1 | replaced | rendered as <span> (the annotation shows inline) | auto/01-html-elements-b |
| `html.el.legacy` | Legacy presentational elements: center, font, big, tt, strike, nobr | out | P1 | replaced | <center> becomes a <div>, the others a <span>; the presentational attributes are dropped | auto/01-html-elements-c |
| `html.el.img` | <img> with a data: PNG, JPEG, GIF or WebP; width/height attributes | in | D3a | kept |  | auto/02-attributes-images-a, auto/02-attributes-images-b, visual/10-images |
| `html.el.img-svg` | <img src="data:image/svg+xml"><br><sub>Release plan, security stream: restrict image formats to PNG, JPEG, GIF, WebP (the decoder runs in the app process).</sub> | out | P1 | replaced | url-policy refuses the MIME: placeholder image | auto/02-attributes-images-b, visual/10-images |
| `html.el.img-other-formats` | <img> with data: AVIF, BMP, ICO<br><sub>Release plan, security stream 7.</sub> | out | P1 | replaced | url-policy refuses the MIME: placeholder image | auto/02-attributes-images-b, visual/10-images |
| `html.el.img-remote` | <img src="https://…"> (any non-data: URL) | out | inv4 | replaced | placeholder image; no request | auto/02-attributes-images-b, visual/10-images |
| `html.el.canvas` | <canvas> element (2D context through the command stream) | in | D3a | kept |  | auto/12-canvas-a |
| `html.el.button` | <button> as a tappable box with UA look<br><sub>An unstyled <button> is drawn in the client's style: the profile's UA stylesheet and --tg-button-*.</sub> | in | D4 | kept |  | auto/01-html-elements-c, visual/14-unstyled-document |
| `html.el.details` | details/summary: open state and toggle on tap<br><sub>Probes: drawn by Blitz and litehtml; it is a disclosure, not a form.</sub> | in | P1 | kept |  | auto/01-html-elements-c, auto/16-sdk-taps-b, visual/11-interaction |
| `html.el.a-fragment` | <a href="#id">: no navigation, :target-free | in | P1 | kept |  | auto/01-html-elements-c, auto/16-sdk-taps-b |
| `html.el.a-external` | <a href="https://…">: href becomes "#" + data-tg-link, the tap goes to openLink (with a gesture) | in | D8 | replaced |  | auto/01-html-elements-c, auto/16-sdk-taps-a, visual/11-interaction, visual/14-unstyled-document, visual/sdk/01-link-external |
| `html.el.forms` | Form controls: form, input (all types), select, option, optgroup, textarea, label, fieldset, legend, datalist, output | out | D3a | stripped | controls are dropped with their subtree (input, select, option, optgroup, textarea, datalist, fieldset, progress, meter); <form> becomes a <div>, <label>, <legend> and <output> a <span> | auto/18-out-of-profile, visual/13-out-of-profile |
| `html.el.progress-meter` | progress, meter (native gauges)<br><sub>Not input, but a native control look that differs per platform.</sub> | out | P1 | stripped | dropped with the subtree, like the other controls | auto/01-html-elements-c |
| `html.el.dialog-popover` | dialog (show/showModal), popover attribute<br><sub>The top layer is the page: in the Shadow DOM renderer a modal dialog or popover is drawn over the whole chat, outside the bubble.</sub> | out | P1 | replaced | a dialog renders as a <div>; the popover attributes and the dialog and popover methods are dropped | auto/18-out-of-profile, visual/13-out-of-profile |
| `html.el.unknown` | Unknown / custom elements render as an inert <span data-tg-tag> | in | P1 | replaced |  | auto/01-html-elements-c |
| `html.el.media` | audio, video, source, track | out | P1 | stripped | dropped with the subtree | auto/18-out-of-profile |
| `html.el.embedding` | script (as DOM), iframe, frame, object, embed, link, meta, base, portal, template, slot, math, fencedframe, … | out | inv2 | stripped | dropped with the subtree | auto/18-out-of-profile |
| `html.attr.global` | class, id, title, hidden, lang, style attribute | in | D3a | kept |  | auto/02-attributes-images-a |
| `html.attr.dir` | dir="rtl" / dir="auto": bidi layout | in | D3a | kept |  | auto/02-attributes-images-a |
| `html.attr.data-aria` | data-* and aria-* attributes (selectors, attr()); no a11y semantics in v1<br><sub>Counted against the strict text budget.</sub> | in | D21 | kept |  | auto/02-attributes-images-a |
| `html.attr.legacy-presentational` | align, valign, bgcolor, border, cellpadding, cellspacing, color, face, nowrap | out | P1 | stripped | the attributes are dropped | auto/02-attributes-images-a |
| `html.attr.event-handlers` | Inline on* handler attributes (run inside QuickJS, never reach the renderer) | in | P1 | host |  | auto/02-attributes-images-a |
| `html.attr.contenteditable` | contenteditable, spellcheck, inputmode, enterkeyhint, autocapitalize<br><sub>Editing is form input. Text is not selectable by default (D18); user-select: text enables selection, never editing.</sub> | out | P1 | stripped | drop the attribute | auto/02-attributes-images-a |
| `html.attr.draggable` | draggable (HTML drag and drop) | out | D4 | stripped | drop the attribute; drag in v1 is pointer events | auto/02-attributes-images-a |
| `html.attr.tabindex` | tabindex, autofocus, accesskey (keyboard focus and shortcuts) | out | D4 | stripped | drop the attributes (no keyboard: D4) | auto/02-attributes-images-a |

## svg

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `svg.shapes` | Inline <svg>: rect, circle, ellipse, line, polyline, polygon, path with presentation attributes | in | D3a | kept |  | auto/13-svg-a, auto/30-svg-fill-box, visual/09-svg-a |
| `svg.structure` | g, defs, symbol, use (fragment href), transform attribute, viewBox/preserveAspectRatio | in | D3a | kept |  | auto/13-svg-a, auto/30-svg-fill-box, visual/09-svg-a |
| `svg.text` | SVG text, tspan | in | D3a | kept |  | auto/13-svg-a, visual/09-svg-a |
| `svg.gradients` | linearGradient, radialGradient, stop; fill="url(#id)" | in | P1 | kept |  | auto/13-svg-a, visual/09-svg-a |
| `svg.css` | CSS on inline SVG: fill/stroke from a stylesheet rule, :hover, CSS animation of SVG properties<br><sub>Blitz and the litehtml prototype ignore SVG CSS (probe svg-css).</sub> | in | P1 | kept |  | auto/13-svg-a, visual/09-svg-b |
| `svg.clip-mask` | clipPath, mask, pattern, marker | out | P1 | stripped | the elements are dropped with their subtree; clip-path, mask and marker attributes are dropped | auto/13-svg-b, visual/09-svg-b |
| `svg.filters` | SVG filter primitives (fe*) | out | P1 | stripped | filter and the fe* elements are dropped with their subtree; the filter attribute is dropped | auto/13-svg-b, visual/09-svg-b |
| `svg.smil` | SMIL animation: animate, animateTransform, animateMotion<br><sub>Animation driven by the renderer, outside CSS animations and WAAPI.</sub> | out | P1 | stripped | animate, animateTransform, animateMotion and mpath are dropped | auto/13-svg-b, visual/09-svg-b |
| `svg.foreign-object` | foreignObject (HTML inside SVG) | out | P1 | stripped | drop the subtree | auto/13-svg-b |
| `svg.image` | <image href="data:…"> inside SVG | out | P1 | stripped | dropped | auto/13-svg-b |

## css

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `css.display.basic` | display: block, inline, inline-block, none | in | D3a | kept |  | auto/03-layout-a |
| `css.display.flex` | Flexbox: display flex/inline-flex, direction, wrap, justify-content, align-items/self/content, flex grow/shrink/basis, order | in | D3a | kept |  | auto/03-layout-a, visual/04-layout |
| `css.gap.flex` | gap / row-gap / column-gap in flex containers<br><sub>Missing in the litehtml prototype (probe flex-gap).</sub> | in | D3a | kept |  | auto/03-layout-a, visual/04-layout |
| `css.display.grid` | CSS grid: display grid, grid-template-*, grid-area/column/row, auto-flow, place-* | in | P1-grid | kept |  | auto/03-layout-a, auto/28-grid-percent-fill, auto/29-grid-svg-marks, visual/04-layout |
| `css.display.table` | display: table / table-row / table-cell | in | D3a | kept |  | auto/03-layout-c |
| `css.display.other` | display: flow-root, contents, list-item | out | P1 | stripped | the declaration is dropped | auto/03-layout-c |
| `css.box.size` | width, height, min-/max-width/height (px, %, auto, fit-content, min/max-content) | in | D3a | kept |  | auto/03-layout-b, auto/28-grid-percent-fill, auto/29-grid-svg-marks |
| `css.box.spacing` | margin, padding (incl. auto margins and negative margins) | in | D3a | kept |  | auto/03-layout-b |
| `css.box.border` | border width/style/colour (solid, dashed, dotted, double) | in | D3a | kept |  | auto/03-layout-b, visual/01-boxes |
| `css.box.radius` | border-radius, including clipping of the background | in | D3a | kept |  | auto/03-layout-c, visual/01-boxes |
| `css.box.sizing` | box-sizing: border-box / content-box | in | D3a | kept |  | auto/03-layout-b |
| `css.box.aspect-ratio` | aspect-ratio<br><sub>Missing in the litehtml prototype.</sub> | in | D3a | kept |  | auto/03-layout-b, auto/29-grid-svg-marks |
| `css.box.logical` | Logical properties: margin-inline, padding-block, inset-inline, inline-size, …<br><sub>Used by LLM widgets (15 % of generated); missing in the litehtml prototype.</sub> | out | P1 | stripped | the declaration is dropped | auto/03-layout-b |
| `css.box.outline` | outline (width, style, colour, offset) | in | D3a | kept |  | auto/03-layout-b, auto/05-colour-backgrounds-b, visual/01-boxes |
| `css.box.shadow` | box-shadow (outer, inset, spread, several layers) | in | D3a | kept |  | auto/05-colour-backgrounds-b, visual/01-boxes |
| `css.box.float` | float, clear | in | D3a | kept |  | auto/03-layout-b, visual/04-layout |
| `css.box.columns` | Multi-column layout (columns, column-count, column-gap) | out | P1 | stripped | the declaration is dropped | auto/03-layout-b, visual/04-layout |
| `css.box.object-fit` | object-fit, object-position on <img> | in | P1 | kept |  | auto/05-colour-backgrounds-b, visual/10-images |
| `css.position.relative-absolute` | position: relative / absolute with inset, top/right/bottom/left | in | D3a | kept |  | auto/03-layout-c, visual/04-layout |
| `css.position.fixed` | position: fixed (relative to the bubble, never the chat) | in | P1 | kept |  | auto/03-layout-c |
| `css.position.sticky` | position: sticky | out | P1 | replaced | becomes relative (no scrolling inside a widget) | auto/03-layout-c, visual/13-out-of-profile |
| `css.position.z-index` | z-index and stacking order | in | D3a | kept |  | auto/03-layout-c, visual/04-layout |
| `css.overflow.clip` | overflow: hidden / clip clips the content (with border-radius) | in | D3a | kept |  | auto/03-layout-c, visual/01-boxes |
| `css.overflow.scroll` | overflow: auto / scroll (scrolling inside the widget) | out | D3a | replaced | becomes hidden: the box clips, no scrollbar, no scroll gesture | auto/03-layout-c, visual/13-out-of-profile |
| `css.visibility` | visibility: hidden / visible | in | D3a | kept |  | auto/03-layout-c |
| `css.opacity` | opacity<br><sub>Missing in the litehtml prototype.</sub> | in | D3a | kept |  | auto/03-layout-c, auto/09-transforms-effects-b, visual/01-boxes |
| `css.color.basic` | Colours: named, #hex (3/4/6/8), rgb()/rgba(), hsl()/hsla(), transparent, currentColor | in | D3a | kept |  | auto/05-colour-backgrounds-a |
| `css.color.modern` | Colour functions: hwb(), lab(), lch(), oklab(), oklch(), color() | out | P1 | stripped | the declaration is dropped (an earlier fallback declaration applies) | auto/05-colour-backgrounds-a, visual/02-colours-gradients |
| `css.color.mix` | color-mix()<br><sub>Missing in the litehtml prototype.</sub> | out | P1 | stripped | the declaration is dropped | auto/05-colour-backgrounds-a, visual/02-colours-gradients |
| `css.color.light-dark` | light-dark() and color-scheme: light dark, resolved to the chat theme | in | P1 | resolved |  | auto/05-colour-backgrounds-a, visual/12-theme |
| `css.background.basic` | background-color, background-image: url(data:…), background-size/position/repeat, several layers | in | D3a | kept |  | auto/05-colour-backgrounds-b, visual/02-colours-gradients, visual/15-body-background |
| `css.background.clip-text` | background-clip: text (gradient text) | out | P1 | stripped | the declaration is dropped | auto/05-colour-backgrounds-b |
| `css.gradient.linear` | linear-gradient(), repeating-linear-gradient() | in | D3a | kept |  | auto/05-colour-backgrounds-b, visual/02-colours-gradients |
| `css.gradient.radial` | radial-gradient(), repeating-radial-gradient() | in | D3a | kept |  | auto/05-colour-backgrounds-b, visual/02-colours-gradients |
| `css.gradient.conic` | conic-gradient() | in | P1 | kept |  | auto/05-colour-backgrounds-b, visual/02-colours-gradients |
| `css.url.remote` | url() / image-set() with anything but a data: image or #fragment | out | inv4 | stripped | the declaration is dropped | auto/05-colour-backgrounds-b |
| `css.font.generic` | font-family with system generic families: system-ui, sans-serif, serif, monospace | in | D3b | kept |  | auto/06-text-fonts-a, visual/03-text-a |
| `css.font.ui-families` | font-family: ui-sans-serif, ui-serif, ui-monospace, ui-rounded<br><sub>Generic per CSS Fonts 4, but only WebKit resolves them; the validator appends the generic fallback (ui-monospace → monospace).</sub> | in | P1 | kept |  | auto/06-text-fonts-a |
| `css.font.named` | font-family naming a font ("Helvetica", "Courier New", "Comic Sans MS", …) | out | D3b | stripped | the validator drops non-generic names from the list; -apple-system and BlinkMacSystemFont, the vendor names of the system UI font, become system-ui; an empty list falls back to the default (system-ui) | auto/06-text-fonts-a, visual/13-out-of-profile |
| `css.font.face` | @font-face (web fonts) | out | D3b | stripped | the at-rule is dropped | auto/06-text-fonts-a |
| `css.font.basic` | font-size, font-weight (100–900, bold), font-style italic, line-height, font shorthand | in | D3a | kept |  | auto/06-text-fonts-a, visual/03-text-a |
| `css.font.variant-numeric` | font-variant-numeric: tabular-nums (and font-feature-settings "tnum") | in | P1 | kept |  | auto/06-text-fonts-a |
| `css.text.align` | text-align (left, right, center, justify, start, end), text-indent | in | D3a | kept |  | auto/06-text-fonts-b, visual/03-text-a |
| `css.text.decoration` | text-decoration (underline, line-through, overline; colour, style, thickness) | in | D3a | kept |  | auto/06-text-fonts-b, auto/06-text-fonts-c, visual/03-text-a |
| `css.text.transform` | text-transform, letter-spacing, word-spacing | in | D3a | kept |  | auto/06-text-fonts-b |
| `css.text.white-space` | white-space: normal, nowrap, pre, pre-wrap, pre-line<br><sub>pre missing in the litehtml prototype.</sub> | in | D3a | kept |  | auto/06-text-fonts-b |
| `css.text.wrap` | overflow-wrap / word-break (breaking long words) | in | D3a | kept |  | auto/06-text-fonts-b |
| `css.text.overflow` | text-overflow: ellipsis (single line) | in | P1 | kept |  | auto/06-text-fonts-c, visual/03-text-b |
| `css.text.line-clamp` | -webkit-line-clamp / line-clamp (multi-line ellipsis) | out | P1 | stripped | the declaration is dropped | auto/06-text-fonts-b, visual/03-text-b |
| `css.text.shadow` | text-shadow<br><sub>Not drawn by Blitz or the litehtml prototype (probe text-shadow).</sub> | out | P1 | stripped | the declaration is dropped | auto/06-text-fonts-c, visual/03-text-b |
| `css.text.vertical-align` | vertical-align (baseline, middle, top, sub, super, lengths) | in | D3a | kept |  | auto/06-text-fonts-b |
| `css.text.list-style` | list-style-type / position (disc, decimal, none, …) | in | D3a | kept |  | auto/06-text-fonts-b |
| `css.text.writing-mode` | writing-mode, text-orientation (vertical text) | out | P1 | stripped | the declaration is dropped | auto/06-text-fonts-b |
| `css.text.rtl` | Right-to-left and bidi text (direction, unicode-bidi, Hebrew/Arabic shaping) | in | D3a | kept |  | auto/06-text-fonts-c, visual/03-text-b |
| `css.text.emoji` | Colour emoji glyphs (system emoji font)<br><sub>Blitz on iOS had blank emoji on the vello backend.</sub> | in | D3a | kept |  | auto/06-text-fonts-c, visual/03-text-b |
| `css.text.selection` | Text selection: not selectable by default; user-select: text (or -webkit-user-select) enables it (none \| text \| auto)<br><sub>user-select: all and contain, and ::selection, stay out. A native renderer that cannot select text yet keeps it unselectable: the widget still works (an accepted exception to 'every client draws it', like :hover).</sub> | in | D18 | kept |  | auto/06-text-fonts-c, visual/16-selection |
| `css.transform.2d` | 2D transforms: translate, rotate, scale, skew, matrix; transform-origin; the translate/rotate/scale properties<br><sub>Missing in the litehtml prototype.</sub> | in | D3a | kept |  | auto/09-transforms-effects-a, auto/30-svg-fill-box, visual/05-transforms-effects-a |
| `css.transform.3d` | 3D transforms: rotateX/Y/3d, translateZ/3d, scaleZ/3d, matrix3d, perspective, transform-style, backface-visibility | out | D3a | stripped | a transform with a 3D function is dropped; perspective, transform-style and backface-visibility are dropped | auto/09-transforms-effects-a, visual/05-transforms-effects-a, visual/13-out-of-profile |
| `css.filter.blur-brightness-drop-shadow` | filter: blur(), brightness(), drop-shadow()<br><sub>Not drawn by Blitz or the litehtml prototype today (probe filter).</sub> | in* | D3a | kept |  | auto/09-transforms-effects-a, visual/05-transforms-effects-a |
| `css.filter.other` | filter: grayscale(), sepia(), saturate(), contrast(), hue-rotate(), invert(), opacity() | out | P1 | stripped | the function is removed from the filter list; an empty list drops the declaration | auto/09-transforms-effects-a, visual/05-transforms-effects-b |
| `css.filter.url` | filter: url(#svg-filter) | out | P1 | stripped | the declaration is dropped | auto/09-transforms-effects-a |
| `css.backdrop-filter` | backdrop-filter: blur(), brightness() | in* | D3a | kept |  | auto/09-transforms-effects-b, visual/05-transforms-effects-b |
| `css.blend` | mix-blend-mode, isolation, background-blend-mode | out | P1 | stripped | the declaration is dropped | auto/09-transforms-effects-b, visual/05-transforms-effects-b |
| `css.clip-path` | clip-path: inset(), circle(), ellipse(), polygon()<br><sub>Blitz draws it, the litehtml prototype does not.</sub> | out | P1 | stripped | the declaration is dropped | auto/09-transforms-effects-b, visual/05-transforms-effects-b |
| `css.mask` | mask / -webkit-mask with gradients or data: images | out | P1 | stripped | the declaration is dropped | auto/09-transforms-effects-b, visual/05-transforms-effects-b |
| `css.pointer-events` | pointer-events: none / auto (hit testing passes through) | in | P1 | kept |  | auto/09-transforms-effects-b, auto/17-drag-hover |
| `css.touch-action` | touch-action: none / pan-y (the widget claims horizontal drags from the chat; vertical drags are always the chat's)<br><sub>D4: drag in v1 needs gesture arbitration; the renderer reports the claim (`gesture` message). v0.1.3 (G2): pan-x claims nothing, and the engine under the renderer gets the value widened to allow vertical panning (none → pan-y), while the claim and getComputedStyle keep the widget's own.</sub> | in | P1 | kept |  | auto/09-transforms-effects-b |
| `css.cursor` | cursor (desktop only)<br><sub>Progressive enhancement like :hover; no-op on touch clients.</sub> | in | D3 | kept |  | auto/09-transforms-effects-b |
| `css.value.units` | Units px, em, rem, %, ch, ex; pt/cm/mm/in | in | D3a | kept |  | auto/04-values-units |
| `css.value.vw` | vw / vmin / vmax / vi: the bubble width<br><sub>Container units (cqw) on the Shadow DOM renderer.</sub> | in | P1 | replaced |  | auto/04-values-units |
| `css.value.vh` | vh / dvh / svh / vb: height-relative units<br><sub>The bubble height comes from the content, so vh is circular: the iframe uses its own height, the shadow renderer the container height.</sub> | out | P1 | stripped | the declaration is dropped | auto/04-values-units |
| `css.value.math` | calc(), min(), max(), clamp()<br><sub>Missing in the litehtml prototype.</sub> | in | D3a | kept |  | auto/04-values-units, auto/09-transforms-effects-a |
| `css.value.custom-properties` | Custom properties and var() with fallbacks; inheritance | in | D3a | kept |  | auto/04-values-units |
| `css.value.keywords` | inherit, initial, unset, revert; !important | in | D3a | kept |  | auto/04-values-units |
| `css.value.content` | content in ::before/::after: strings, attr(data-*), counters<br><sub>Strict: attr() reads only budgeted attributes.</sub> | in | D3a | kept |  | auto/04-values-units |
| `css.value.env` | env() (safe-area-inset-*, …)<br><sub>env(safe-area-inset-top\|right\|bottom\|left[, fallback]) becomes var(--tg-content-safe-area-inset-…[, fallback]), the root variable the renderer sets (the client's time over the bottom corner of a widget with no caption below it); another name resolves to its fallback or the declaration is dropped; never inside url().</sub> | in | P1-safe-area | replaced |  | auto/04-values-units, auto/36-safe-area, visual/17-time-corner |

## selector

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `sel.basic` | Type, class, id, universal and attribute selectors; descendant, child, + and ~ combinators; selector lists | in | D3a | kept |  | auto/07-selectors-a, auto/07-selectors-b |
| `sel.structural` | :first-child, :last-child, :nth-child(), :nth-last-child(), :nth-of-type(), :only-child, :empty, :not() | in | D3a | kept |  | auto/07-selectors-b |
| `sel.is-where` | :is(), :where() | in | D3a | kept |  | auto/07-selectors-b |
| `sel.has` | :has()<br><sub>Not matched by Blitz or the litehtml prototype (probe has-selector).</sub> | out | P1 | stripped | the selector never matches (dropped from its list) | auto/07-selectors-b |
| `sel.root` | :root (custom properties on the widget root)<br><sub>[data-tw-root] on the Shadow DOM renderer.</sub> | in | D3a | replaced |  | auto/07-selectors-b |
| `sel.before-after` | ::before, ::after | in | D3a | kept |  | auto/07-selectors-b |
| `sel.first-letter-line` | ::first-letter, ::first-line, ::marker | out | P1 | stripped | the selector never matches (dropped from its list) | auto/07-selectors-b |
| `sel.hover` | :hover (progressive enhancement: desktop with a pointer; no-op on touch) | in | D3 | kept |  | auto/07-selectors-b, auto/17-drag-hover, visual/11-interaction |
| `sel.active` | :active (pressed feedback while a tap is down)<br><sub>Used by 32 % of the HTMLBubbles demos for tap feedback.</sub> | in | P1 | kept |  | auto/16-sdk-taps-b, visual/11-interaction |
| `sel.focus` | :focus, :focus-visible, :focus-within | out | D4 | stripped | the selector never matches (dropped from its list); the widget never owns keyboard focus | auto/07-selectors-b |
| `sel.form-state` | :checked, :disabled, :enabled, :valid, :invalid, :placeholder-shown, ::placeholder | out | D3a | replaced | :disabled and :enabled become [disabled] and :not([disabled]) (the <button> state); the other form states never match | auto/18-out-of-profile |
| `sel.host-scoped` | :host, :host-context(), ::slotted(), ::part(), :scope | out | inv6 | stripped | the selector never matches (dropped from its list) on every renderer | auto/07-selectors-b |
| `sel.target` | :target | out | P1 | stripped | the selector never matches (dropped from its list) | auto/07-selectors-b |

## at-rule

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `at.media.width` | @media (min-width/max-width/orientation): the bubble width<br><sub>@container on the Shadow DOM renderer.</sub> | in | D3a | replaced |  | auto/08-at-rules |
| `at.media.color-scheme` | @media (prefers-color-scheme): the chat theme, live on theme change | in | D14 | resolved |  | auto/08-at-rules, visual/12-theme |
| `at.media.reduced-motion` | @media (prefers-reduced-motion)<br><sub>The validator answers it with the client's setting; until the client passes it, no preference.</sub> | in | P1 | resolved |  | auto/08-at-rules |
| `at.media.hover-pointer` | @media (hover: hover), (pointer: fine\|coarse)<br><sub>Lets a widget gate :hover styles; the answer must come from the client, not the engine.</sub> | out | P1 | stripped | the @media rule is dropped; matchMedia answers false | auto/08-at-rules |
| `at.media.other` | @media print, resolution, other media features | out | P1 | stripped | the @media rule is dropped; matchMedia answers false | auto/08-at-rules |
| `at.supports` | @supports evaluated from the profile tables (not the renderer engine)<br><sub>Decided by the validator from the profile tables: false for anything outside the profile.</sub> | in | D23 | resolved |  | auto/08-at-rules |
| `at.keyframes` | @keyframes (and -webkit-keyframes) | in | D3a | kept |  | auto/10-css-animations |
| `at.import-namespace` | @import, @namespace | out | inv4 | stripped | dropped | auto/08-at-rules |
| `at.container` | @container queries (container-type in the widget) | out | P1 | stripped | the at-rule is dropped; container properties are dropped | auto/08-at-rules |
| `at.layer` | @layer cascade layers | out | P1 | stripped | the at-rule is dropped with its rules | auto/08-at-rules |
| `at.property` | @property (typed custom properties) | out | P1 | stripped | the at-rule is dropped | auto/08-at-rules |
| `at.exotic` | @counter-style, @starting-style, @scope, @page, @font-feature-values, @font-palette-values, @position-try | out | P1 | stripped | dropped like unknown at-rules | auto/08-at-rules |
| `at.nesting` | CSS nesting (.a { .b { } }, &)<br><sub>Nested rules are dropped with a profile diagnostic.</sub> | out | P1 | stripped | nested rules are dropped with a css-dropped diagnostic | auto/08-at-rules |

## animation

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `anim.transition` | CSS transitions (property, duration, delay incl. negative, timing functions) | in | D3a | kept |  | auto/10-css-animations, auto/28-grid-percent-fill, visual/06-css-animations |
| `anim.keyframes` | CSS animations: animation-name/duration/delay/iteration-count/direction/fill-mode/timing-function<br><sub>Not driven by Blitz or the litehtml prototype.</sub> | in | D3a | kept |  | auto/10-css-animations, auto/30-svg-fill-box, visual/06-css-animations |
| `anim.play-state` | animation-play-state: paused (and negative delays) freeze a frame | in | D3a | kept |  | auto/10-css-animations, visual/06-css-animations |
| `anim.easing` | Timing functions: ease*, linear, cubic-bezier(), steps(), linear() stops | in | D3a | kept |  | auto/10-css-animations, visual/06-css-animations |
| `anim.events` | animationstart/end/iteration and transitionend events reach the widget | in | D3a | kept |  | auto/10-css-animations |
| `anim.waapi` | Web Animations: Element.animate, getAnimations, Animation play/pause/currentTime/finished, document.timeline<br><sub>Behind the webAnimations feature (D5: profile flag, the same on every client); without it animate() is a finished stub.</sub> | in* | D3a | kept |  | auto/11-web-animations, visual/07-web-animations |
| `anim.waapi-css-control` | Web Animations control of CSS animations/transitions (pause, seek, cancel via getAnimations) | in* | D3a | kept |  | auto/11-web-animations, visual/07-web-animations |
| `anim.magic-move` | Magic move (shared-element morph between renders) | out | D3a | missing | not offered (it belonged to the render() model) | auto/15-js-runtime-c |

## canvas

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `canvas.rect` | fillRect, strokeRect, clearRect | in | D3a | kept |  | auto/12-canvas-a, visual/08-canvas-a, visual/08-canvas-b |
| `canvas.path` | Paths: beginPath, moveTo, lineTo, arc, arcTo, bezierCurveTo, quadraticCurveTo, ellipse, rect, roundRect, closePath, fill (nonzero/evenodd), stroke | in | D3a | kept |  | auto/12-canvas-a, visual/08-canvas-a, visual/08-canvas-b |
| `canvas.line-style` | lineWidth, lineCap, lineJoin, miterLimit, setLineDash, lineDashOffset | in | D3a | kept |  | auto/12-canvas-a, visual/08-canvas-a, visual/08-canvas-b |
| `canvas.colors` | fillStyle / strokeStyle with CSS colours | in | D3a | kept |  | auto/12-canvas-a, visual/08-canvas-a |
| `canvas.gradients` | createLinearGradient, createRadialGradient, addColorStop | in | D3a | kept |  | auto/12-canvas-a, visual/08-canvas-a |
| `canvas.conic-gradient` | createConicGradient | out | P1 | replaced | createConicGradient returns a gradient that draws nothing | auto/12-canvas-a, visual/08-canvas-b |
| `canvas.pattern` | createPattern from a canvas or image | out | P1 | replaced | createPattern returns a pattern that draws nothing | auto/12-canvas-b, visual/08-canvas-b |
| `canvas.transform` | save, restore, translate, rotate, scale, transform, setTransform, resetTransform | in | D3a | kept |  | auto/12-canvas-b, visual/08-canvas-b |
| `canvas.clip` | clip() with a path | in | D3a | kept |  | auto/12-canvas-b, visual/08-canvas-b |
| `canvas.text` | fillText, strokeText, font, textAlign, textBaseline, measureText | in | D3a | kept |  | auto/12-canvas-a, auto/12-canvas-b, visual/08-canvas-a |
| `canvas.alpha` | globalAlpha | in | D3a | kept |  | auto/12-canvas-b, visual/08-canvas-a |
| `canvas.composite` | globalCompositeOperation (source-over, destination-out, multiply, …) | out | P1 | stripped | the property is not applied (source-over) | auto/12-canvas-b |
| `canvas.shadow` | shadowBlur, shadowColor, shadowOffsetX/Y | out | P1 | stripped | the properties are not applied | auto/12-canvas-b, visual/08-canvas-b |
| `canvas.filter` | ctx.filter (CSS filter on canvas drawing) | out | P1 | stripped | the property is not applied | auto/12-canvas-b, visual/08-canvas-b |
| `canvas.path2d` | Path2D objects (from SVG path data or built), fill/stroke/clip with them | in | D3a | kept |  | auto/12-canvas-b |
| `canvas.draw-image` | drawImage from an <img> (data:) or another canvas | in | D3a | kept |  | auto/12-canvas-b |
| `canvas.image-data` | getImageData / putImageData / createImageData (pixel read-back)<br><sub>getImageData is a sync query answered by the renderer: a native renderer must rasterize to answer.</sub> | out | P1 | stub | getImageData returns transparent pixels, putImageData draws nothing | auto/12-canvas-a, auto/12-canvas-b |
| `canvas.hit-test` | isPointInPath / isPointInStroke | in | P1 | kept |  | auto/12-canvas-a |
| `canvas.hidpi` | Backing store sized by width/height attributes, CSS size independent (devicePixelRatio scaling) | in | D3a | kept |  | auto/12-canvas-a, visual/08-canvas-a, visual/08-canvas-b |
| `canvas.readback` | toDataURL, toBlob, captureStream | out | P1 | stub | toBlob/captureStream are stubs; toDataURL returns an empty image (data:,) | auto/12-canvas-a |
| `canvas.webgl` | WebGL, OffscreenCanvas, bitmaprenderer contexts | out | D6 | stub | getContext returns null; OffscreenCanvas throws | auto/12-canvas-a |

## js

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `js.dom.core` | DOM building: createElement(NS), append/insertBefore/remove, textContent, innerHTML (parsed in the sandbox), attributes, classList, dataset | in | D11 | kept |  | auto/14-dom-layout-api-a |
| `js.dom.query` | querySelector(All), getElementById, getElementsBy*, closest, matches | in | D11 | kept |  | auto/14-dom-layout-api-b |
| `js.dom.style` | element.style (CSSOM properties, setProperty, cssText), className | in | D11 | kept |  | auto/14-dom-layout-api-a |
| `js.dom.events` | addEventListener (capture and bubble), dispatchEvent of CustomEvent, preventDefault, stopPropagation | in | D11 | kept |  | auto/14-dom-layout-api-a |
| `js.layout.rect` | getBoundingClientRect, getClientRects (answered by the renderer) | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.layout.box` | offsetWidth/Height/Top/Left, clientWidth/Height, scrollWidth/Height | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.layout.computed-style` | getComputedStyle (incl. pseudo-elements and custom properties) | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.layout.match-media` | matchMedia (prefers-color-scheme answered from the chat theme) | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.layout.resize-observer` | ResizeObserver | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.layout.intersection-observer` | IntersectionObserver (emulated: always visible) | in | P1 | emulated |  | auto/14-dom-layout-api-b |
| `js.layout.element-from-point` | document.elementFromPoint | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.layout.measure-text` | Text measurement through canvas measureText (fonts of the client) | in | P1 | host |  | auto/14-dom-layout-api-b |
| `js.focus` | element.focus() / blur() / select(), document.activeElement: the widget takes keyboard focus | out | D4 | stub | focus(), blur(), select() and setSelectionRange() do nothing; no call op reaches the renderer | auto/21-no-keyboard |
| `js.timers` | setTimeout / setInterval / clear*, ordering, clamped delays | in | P1 | kept |  | auto/15-js-runtime-a |
| `js.raf` | requestAnimationFrame / cancelAnimationFrame (frames from the renderer) | in | P1 | kept |  | auto/15-js-runtime-a |
| `js.microtasks` | Promise, async/await, queueMicrotask | in | P1 | kept |  | auto/15-js-runtime-a |
| `js.storage.local` | localStorage: per (account, chat, message), preloaded, 4 KB, QuotaExceededError past it<br><sub>The shim keeps the map (4 KB per area, QuotaExceededError past it); the client preloads it and keeps each commit per message.</sub> | in | D17 | host |  | auto/15-js-runtime-a, visual/sdk/07-storage-recreate, visual/sdk/08-storage-restart |
| `js.storage.session` | sessionStorage: same key, in memory until app restart | in | D17 | host |  | auto/15-js-runtime-a, visual/sdk/07-storage-recreate, visual/sdk/08-storage-restart |
| `js.intl` | Intl.NumberFormat, DateTimeFormat, PluralRules, RelativeTimeFormat, ListFormat, Collator (host ICU) | in | P1 | host |  | auto/15-js-runtime-b |
| `js.intl.format-to-parts` | Intl.NumberFormat/DateTimeFormat formatToParts<br><sub>Numbers and dates, in every glue copy.</sub> | in | P1 | host |  | auto/15-js-runtime-b |
| `js.locale` | navigator.language reflects the client locale<br><sub>navigator.language and the Intl default come from the client's locale.</sub> | in | P1 | host |  | auto/15-js-runtime-b |
| `js.crypto-random` | crypto.getRandomValues, crypto.randomUUID, Math.random | in | P1 | host |  | auto/15-js-runtime-b |
| `js.builtins` | TextEncoder/TextDecoder, URL, URLSearchParams, structuredClone, AbortController, atob/btoa | in | P1 | kept |  | auto/15-js-runtime-b |
| `js.eval` | eval / new Function inside QuickJS (never in a browser context) | in | inv1 | kept |  | auto/15-js-runtime-b |
| `js.css-supports` | CSS.supports() answered from the profile tables<br><sub>Answers from the profile tables, like @supports.</sub> | in | D23 | resolved |  | auto/15-js-runtime-b |
| `js.visibility` | document.hidden / visibilityState / visibilitychange when the bubble leaves the screen | in | P1 | host |  | auto/15-js-runtime-b |
| `js.libraries` | Bundled libraries by CDN name: Chart.js 4, D3 7 (nothing is fetched) | out | P1-libs | stripped | nothing is bundled: a <script src> to a CDN is an external script like any other and is not run | auto/15-js-runtime-b |
| `js.network` | fetch, XMLHttpRequest, WebSocket, EventSource, RTCPeerConnection, sendBeacon, importScripts, Worker, BroadcastChannel | out | inv5 | stub | NetworkDisabledError (fetch rejects with it) | auto/15-js-runtime-c |
| `js.navigation` | location assignment, history, window.open (→ openLink), form submission | out | inv6 | stub | navigation never happens; window.open goes through openLink | auto/15-js-runtime-c |
| `js.dialogs` | alert, confirm, prompt, print | out | P1 | stub | non-blocking stubs: confirm → false, prompt → null | auto/15-js-runtime-c |
| `js.device` | clipboard, share, vibrate, geolocation, mediaDevices, Notification | out | D8 | stub | rejected promises / undefined; haptics come from the SDK | auto/15-js-runtime-c |
| `js.shadow-dom` | attachShadow, customElements.define | out | P1 | stub | attachShadow throws NotSupportedError, customElements.define is a no-op | auto/15-js-runtime-c |
| `js.blob-file` | Blob, FileReader, URL.createObjectURL | out | inv4 | stub | inert stubs | auto/15-js-runtime-c |
| `js.fullscreen` | requestFullscreen | out | D12a | stub | rejects | auto/15-js-runtime-c |

## sdk

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `tw.ready` | TelegramWidget.ready() | in | D8 | kept |  | auto/16-sdk-taps-c |
| `tw.theme` | TelegramWidget.colorScheme ("light" \| "dark"); theme and onTheme(cb) kept as deprecated aliases | in | D14 | kept |  | auto/16-sdk-taps-c, visual/12-theme, visual/sdk/14-theme-palette |
| `tw.theme-params` | themeParams (every Mini Apps key, #rrggbb) and the --tg-theme-* / --tg-color-scheme variables on the root, updated in one task with themeChanged | in | D14 | kept |  | auto/16-sdk-taps-c, auto/20-client-behaviour, auto/22-ua-defaults, visual/12-theme, visual/14-unstyled-document, visual/sdk/14-theme-palette |
| `tw.events` | onEvent / offEvent for themeChanged, activated, deactivated; at most 16 handlers per event type | in | SDK | kept |  | auto/16-sdk-taps-c, visual/sdk/14-theme-palette |
| `tw.is-active` | TelegramWidget.isActive mirrors visibility (activated / deactivated) | in | SDK | kept |  | auto/16-sdk-taps-c, auto/20-client-behaviour |
| `tw.resize-anchor` | TelegramWidget.setResizeAnchor('top' \| 'bottom'): the edge of the bubble that stays put when the widget's height changes<br><sub>v0.1.3: detect it with typeof TelegramWidget.setResizeAnchor === 'function'; anything but 'top' or 'bottom' throws TypeError; the host reports it with the height.</sub> | in | R1 | host |  | auto/24-resize-anchor, visual/sdk/18-resize-anchor |
| `tw.content-safe-area` | TelegramWidget.contentSafeAreaInset { top, right, bottom, left, cornerLeft, cornerRight } (logical px, frozen, a fresh object per change) and contentSafeAreaChanged, with the --tg-content-safe-area-inset-* / -corner-left\|right variables on the root, updated in one task<br><sub>bottom is the height of the band the client draws the message time in over a widget with no caption below it; cornerRight (left-to-right interface) or cornerLeft (right-to-left) the width of its corner in that band; 0 with a caption below.</sub> | in | P1-safe-area | host |  | auto/36-safe-area, visual/17-time-corner |
| `tw.bot-data` | TelegramWidget.chatData and publicData: the bot's two strings (text of at most 1024 bytes each, read-only, null for none); chatDataChanged / publicDataChanged when they change<br><sub>chatData belongs to one original message (in a group every member sees it) and is null in a forward; publicData is the forward's snapshot or a value shared with the original; one change of both fires both, chat first; an unchanged value fires nothing.</sub> | in | I1 | host |  | auto/25-state-api-a, visual/sdk/16-state-edit, visual/sdk/25-group-chat-state |
| `tw.context` | TelegramWidget.isForward, chatType and viewer (a WebAppUser): a forwarded copy, the kind of chat, who looks; no chatType in a forward, no viewer where the widget may not ask the bot<br><sub>viewer is shaped like Mini Apps' WebAppUser (id, first_name, last_name, username, is_premium only when true; names 1-64 UTF-16 code units, a username 5-32 of [A-Za-z0-9_]; a field that breaks its rule is left out); chatType is private, group, supergroup or channel and null in every forward (a copy is told nothing about its chat); viewer only where the message has can_query (an original sent with allow_queries, a forward of one sent with fwd_allow_queries), else null.</sub> | in | I1 | host |  | auto/25-state-api-a, auto/26-context-copy, auto/26-context-group, auto/26-context-live-copy, auto/26-context-no-queries, auto/26-context-preview, auto/26-context-private, visual/sdk/20-forward-requests, visual/sdk/25-group-chat-state |
| `tw.request` | TelegramWidget.request(data): a callback button inside the widget; data of 1-64 bytes, resolves { result } (text of at most 1024 bytes), rejects with error.code; where it may ask is the server's flag per message<br><sub>On a tap where can_query (originals of a widget sent with allow_queries, forwards of one sent with fwd_allow_queries; the client shows a network marker there), in the background only where background_query (the original in the private chat with the bot) while visible: one when the widget appears on screen, then one per 5 s; one in flight; the answer carries no data.</sub> | in | I1 | host |  | auto/25-state-api-b, auto/26-context-copy, auto/26-context-group, auto/26-context-live-copy, auto/26-context-no-queries, auto/26-context-preview, auto/26-context-private, visual/sdk/19-request-tap |
| `tw.copy-text` | TelegramWidget.copyText(text): copies 1-256 characters on a tap; the client's toast shows the copied text<br><sub>One call per tap, shared with the links and request; outside a tap dropped with activation-blocked; navigator.clipboard stays a stub.</sub> | in | I1 | host |  | auto/25-state-api-b, visual/sdk/22-copy-text |
| `tw.watch-public-data` | TelegramWidget.watchPublicData() / unwatchPublicData(): the widget asks for live shared data; the client polls a shared widget's public_data only while it watches and is on screen<br><sub>No tap needed, idempotent; the shim sends only changes and the host keeps the latest (web onWatchPublicData and watchesPublicData, the native diagnostics); a widget that does not watch gets public_data with the message and the bot's pushed edits only; a preview never watches.</sub> | in | I1 | host |  | auto/25-state-api-b, visual/sdk/24-watch-public-data |
| `tw.preview` | TelegramWidget.isPreview: an inline result shown in the list before it is sent<br><sub>A preview has its data but no input, no request, link, copy or haptic (only ready and setResizeAnchor), no chatType or viewer, and Web Storage that is never committed.</sub> | in | I1 | host |  | auto/25-state-api-a, visual/sdk/23-preview |
| `tw.open-link` | openLink(url): an absolute https URL of at most 256 characters, no user info, not a t.me host; one call per pointer press; the client confirms showing the full URL<br><sub>Invalid arguments throw TypeError; one call per tap reaches the client.</sub> | in | D8 | host |  | auto/16-sdk-taps-a, visual/11-interaction, visual/sdk/01-link-external, visual/sdk/03-link-rejected, visual/sdk/04-link-timer |
| `tw.open-telegram-link` | openTelegramLink(url): any https://t.me/… (telegram.me, telegram.dog) path, at most 256 characters, opened inside the client; tg: URLs refused | in | D8 | host |  | auto/16-sdk-taps-c, visual/sdk/02-link-telegram, visual/sdk/03-link-rejected |
| `tw.haptic` | HapticFeedback: impactOccurred(light\|medium\|heavy\|rigid\|soft), notificationOccurred(error\|success\|warning), selectionChanged(); visible widgets only, during a touch or in one 1 s series without one (v0.2.2)<br><sub>Desktop and web accept the calls and play nothing.</sub> | in | D8 | host |  | auto/16-sdk-taps-c, visual/sdk/05-haptics, visual/sdk/06-haptics-rate, visual/sdk/26-haptics-no-touch |
| `tw.profile` | TelegramWidget.profile (integer) and isProfileAtLeast(n) | in | D23 | kept |  | auto/16-sdk-taps-c |
| `tw.send-message` | TelegramWidget.sendMessage | out | D8 | missing | removed from the SDK | auto/16-sdk-taps-c |

## input

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `input.tap` | Tap: click on the element under the finger, with clientX/Y and target | in | D4 | kept |  | auto/16-sdk-taps-a, visual/11-interaction |
| `input.pointer` | pointerdown / pointerup / pointercancel with coordinates, pointerId, pointerType, isPrimary | in | D4 | kept |  | auto/16-sdk-taps-a |
| `input.mouse-compat` | mousedown / mouseup compatibility events of a tap | in | D4 | kept |  | auto/16-sdk-taps-a |
| `input.touch-events` | touchstart / touchmove / touchend (touch clients only)<br><sub>Desktop clients have none; widgets should use pointer events.</sub> | out | D4 | stripped | the renderer never forwards touch events; use pointer events | auto/17-drag-hover |
| `input.drag` | Drag: pointermove with buttons while pressed, setPointerCapture, movementX/Y<br><sub>v1 (not the PoC): drag with gesture arbitration.</sub> | in | D4 | kept |  | auto/17-drag-hover, visual/11-interaction, visual/sdk/12-drag-gestures |
| `input.hover-events` | pointerover/enter/leave/out, mouseover/enter/leave (desktop pointer only) | in | D3 | kept |  | auto/17-drag-hover |
| `input.dblclick` | dblclick / double tap: a double tap over the widget is always the widget's, on every element (the reaction stays outside the widget)<br><sub>v0.1.2: both clicks, then the renderer's own dblclick (gesture-policy.ts: 300 ms, 40 px); the engine's own never reaches the widget.</sub> | in | G1 | emulated |  | auto/16-sdk-taps-b, auto/23-gestures-a, visual/sdk/12-drag-gestures |
| `input.long-press` | contextmenu / long press (or right click) claimed by touch-action: none on the element or an ancestor; on auto the hold is the bubble's message menu and the widget gets pointercancel<br><sub>v0.1.2: the renderer sends its own contextmenu at a 500 ms hold within 10 px or a right-button press; the platform menu stays cancelled. v0.1.3: a press whose first move past the slop is vertical is the chat's scroll, also on none and after the hold.</sub> | in | G1 | emulated |  | auto/16-sdk-taps-b, auto/23-gestures-a, visual/sdk/12-drag-gestures |
| `input.keyboard` | Key events: keydown / keyup / keypress, beforeinput / input from keys, focus / blur / focusin / focusout from keys | out | D4 | stripped | the renderer never forwards key events; the widget never owns keyboard focus | auto/16-sdk-taps-b, auto/21-no-keyboard |
| `input.key-activation` | Enter / Space activate a focused button or link (a click from the keyboard) | out | D4 | stripped | keys do nothing in a widget: no synthetic click from Enter or Space | auto/21-no-keyboard |
| `input.key-gesture` | A key press counts as a user gesture for privileged bridge calls | out | D4 | stripped | only taps (pointer presses) grant a gesture | auto/21-no-keyboard |
| `input.wheel-scroll` | wheel and scroll events | out | D3a | stripped | the renderer never forwards wheel or scroll; no scrolling inside a widget | auto/16-sdk-taps-b |
| `input.user-activation` | Privileged calls (openLink) only in the task of a real tap; not from timers or synthetic clicks | in | D8 | kept |  | auto/16-sdk-taps-a, visual/sdk/04-link-timer |
| `input.prevent-default` | preventDefault on pointer events keeps the gesture in the widget (learned by the renderer) | in | D4 | kept |  | auto/16-sdk-taps-a |

## limit

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `limit.text-node` | Text values (text node, canvas string, Path2D data) up to 32 K UTF-16 units; past it the widget stops (strict) | in | D5 | kept |  | auto/19-limits, auto/31-stop-text |
| `limit.text-budget` | Document text 48 K units, past it the widget stops; complex-script text 8192 units, past it the text is refused (strict) | in | D5 | kept |  | auto/19-limits |
| `limit.nodes` | At most 2000 nodes (elements, text, comments) in the document once a batch is applied; past it the widget stops (strict) | in | P1 | kept |  | auto/44-stop-nodes |
| `limit.timers` | At most 64 live timers (RangeError past it) | in | P1 | kept |  | auto/19-limits |
| `limit.cpu` | CPU quota 300 ms of JS per second (timers deferred, not killed; a task that runs past it makes the next ones wait longer); one task may run 500 ms; at most 128 layout queries in one task, and their time at most 1000 ms; past these the widget stops | in | P1 | kept |  |  |
| `limit.memory` | QuickJS heap 8 MB | in | P1 | kept |  |  |
| `limit.storage` | localStorage and sessionStorage: 4 KB per area and message (4096 bytes of keys and values in UTF-8); over it setItem throws QuotaExceededError and stores nothing | in | D17 | kept |  | auto/15-js-runtime-a, visual/sdk/10-storage-quota |
| `limit.attribute` | Attribute and inline-style values up to 8 K UTF-16 units; past it the widget stops (strict) | in | L1 | kept |  | auto/19-limits, auto/32-stop-attribute |
| `limit.batch` | At most 10 000 operations in one batch (the shim hands a task's DOM changes over in batches of about 1000, its element method calls in one); past it the widget stops (strict) | in | L1 | kept |  | auto/33-stop-batch |
| `limit.css-rules` | At most 400 CSS rules and 32 @keyframes in the document's stylesheets; past them the widget stops (strict) | in | L1 | kept |  | auto/34-stop-css-rules |
| `limit.canvas-commands` | At most 4000 canvas commands per canvas in one task (RangeError past it; strict) | in | L1 | kept |  | auto/19-limits |
| `limit.animations` | At most 64 running script animations (further ones are not rendered; strict) | in | L1 | kept |  |  |
| `limit.main-thread` | The host's main-thread work for a widget, its layout queries plus 25 µs per operation and 2 µs per canvas command, at most 100 ms per second (timers and frames deferred past it) | in | L1 | host |  |  |
| `limit.canvas-pixels` | A widget's canvases hold at most 2048 × 2048 pixels together; past it the widget stops (strict; the renderer itself never applies a size past 4096 × 4096) | in | L1 | kept |  | auto/35-stop-canvas-pixels |
| `limit.paint-heavy` | At most 16 heavy boxes at once: a blurred box-shadow, a filter, a backdrop-filter, opacity below 1, or a running animation of one of them (shadows animated without blur do not count, nor the opacity of a disabled or pressed <button>: its own dimming); elements under display: none and a ::before / ::after without content are no boxes; past it the widget stops (strict; every renderer counts on the computed style) | in | L2 | host |  | auto/37-paint-counted, auto/38-stop-paint-load, auto/39-stop-paint-class, auto/40-stop-paint-animation, auto/41-stop-paint-web-animation, auto/42-stop-paint-before |
| `limit.paint-backdrop` | Of the heavy boxes at most 2 with backdrop-filter; past it the widget stops (strict) | in | L2 | host |  | auto/37-paint-counted, auto/43-stop-paint-backdrop |
| `limit.image-pixels` | A data: image of at most 1024 × 1024 pixels, as its bytes say (larger: the placeholder); bytes of none of the profile's image formats are a broken image | in | L1 | kept |  | auto/19-limits |
| `limit.url-length` | openLink / openTelegramLink URLs of at most 256 characters (TypeError past it) | in | SDK | kept |  | auto/16-sdk-taps-a, visual/sdk/03-link-rejected |
| `limit.event-handlers` | At most 16 handlers per event type in onEvent (more are ignored) | in | SDK | kept |  | auto/20-client-behaviour |
| `limit.haptic-rate` | Haptics: a bucket of 5 calls refilled at 10 per second, and a touch: free while a finger is down on the widget and 5 s after it is lifted, otherwise one 1 s series (the next after a touch or 10 s later); the rest dropped (haptic-limit) | in | SDK | kept |  | auto/16-sdk-taps-c, auto/27-haptics-no-touch, visual/sdk/06-haptics-rate, visual/sdk/26-haptics-no-touch |
| `limit.html-size` | HTML up to 32 KB uncompressed and 4 KB after zlib compression (server-side)<br><sub>Checked by the server, not the client. Every conformance widget stays under it (build.ts keeps each one under 3800 bytes after zlib at level 9, as the server compresses) and reaches a client from a test bot.</sub> | in | D20 | host |  |  |

## behaviour

| ID | Feature | Status | Decision | Today | Out: neutralised as | Tests |
|---|---|---|---|---|---|---|
| `behaviour.theme-switch` | Live theme switch: prefers-color-scheme, light-dark() and onTheme follow the chat theme without a reload | in | D14 | host |  | auto/20-client-behaviour, visual/12-theme, visual/sdk/14-theme-palette |
| `behaviour.offscreen-pause` | Off-screen: frames stop, timers at most 1 Hz, visibilitychange fires | in | P1 | host |  | auto/20-client-behaviour |
| `behaviour.auto-height` | The bubble takes the widget height; above the cap it is clipped hard | in | D12a | host |  | auto/20-client-behaviour, visual/sdk/13-max-height, visual/sdk/17-size-hint |
| `behaviour.no-selection` | Text is not selectable by default (a drag over text selects nothing); user-select: text enables it | in | D18 | kept |  | auto/17-drag-hover, visual/16-selection |
| `behaviour.restart` | After 2 consecutive crashes the widget stays stopped on its last frame with the "Widget stopped" badge; before that the client rebuilds it when shown again<br><sub>The frozen frame and badge are the framework's; the restart count is the client's.</sub> | in | SDK | host |  | visual/sdk/11-crash-restart |
| `behaviour.gesture-claim` | touch-action is the claim: taps and double taps over the widget are always its own, vertical drags always the chat's; on auto holds and horizontal swipes are the bubble's (the widget gets pointercancel); pan-y gives the widget horizontal drags; none gives it horizontal drags and holds; pan-x claims nothing; the leading 20 px and the bubble outside the widget never take a claim<br><sub>onGestureClaim (x, y, hold) per primary pointerdown, y always false since v0.1.3; the client's recognizers follow the shared gesture policy (v0.1.2; vertical drags the chat's since v0.1.3, G2).</sub> | in | G2 | host |  | auto/23-gestures-a, auto/23-gestures-b, visual/sdk/12-drag-gestures |
| `behaviour.storage-persist` | localStorage survives the widget being dropped and re-created, and an app restart; sessionStorage survives re-creation but not a restart; both are deleted with the message | in | SDK | host |  | visual/sdk/07-storage-recreate, visual/sdk/08-storage-restart, visual/sdk/09-storage-delete |
| `behaviour.storage-forward` | A forwarded copy has its own storage, keyed by its own chat and message, so it starts empty<br><sub>Testable with a test bot: forward its message into another chat; the copy starts with empty storage.</sub> | in | SDK | host |  | visual/sdk/15-storage-forward |
| `behaviour.message-edit` | The HTML of a sent widget never changes (an edit may change only the caption): a bot changes only chatData and publicData (behaviour.data-edit)<br><sub>visual/sdk/16-bot-edit still tests an HTML edit, which the server does not allow, and is to be replaced by a data-edit case once the server ships editMessageWidgetData.</sub> | in | I1 | host |  | visual/sdk/16-state-edit |
| `behaviour.resize-anchor` | When an auto height changes, the client keeps the widget's anchored edge in place on screen: top (default), the content below moves; bottom, the content above moves; also from a cached height and after a re-creation<br><sub>v0.1.3: WidgetDiagnostics.resizeAnchor / web onResize(height, anchor); the old pixels stay aligned to the anchored edge during a height animation.</sub> | in | R1 | host |  | visual/sdk/18-resize-anchor |
| `behaviour.time-corner` | A widget with no caption below it (none, or one shown above it) gets the message time and status over its bottom corner, on a backing as on a photo, and taps on it are the client's; the client passes the band (contentSafeArea bottom) and its corner (cornerRight, or cornerLeft in a right-to-left interface) before the first render and changes them only with the bubble's layout (a caption added, removed or moved above or below by an edit, "edited"); with a caption below the time is in the caption's line and the safe area is 0<br><sub>WidgetHost contentSafeArea / setContentSafeArea on every host; never per frame; the floating day pill has no contract and is drawn over the widget like over a photo.</sub> | in | P1-safe-area | host |  | visual/17-time-corner |
| `behaviour.data-edit` | A change of the bot's data reaches the running widget as chatDataChanged / publicDataChanged (the client's setData with the full state: the server's update, or the polled shared public_data): no new host, no restart<br><sub>The client keeps the values with the message; a shared widget's public_data (widget_id) is polled for one visible carrier per id, one message per call, at the object's period and applied to every widget with that id, forwards included; an answer carries no data; testable with a test bot once the server ships.</sub> | in | I1 | host |  | visual/sdk/16-state-edit, visual/sdk/24-watch-public-data, visual/sdk/25-group-chat-state |
| `behaviour.forward-copy` | A forwarded copy never has chatData; its publicData is the forward's snapshot or the shared value; it is told nothing about the chat it is in (no chatType); it asks the bot only on a tap and only if the widget was sent with fwd_allow_queries, and otherwise has no viewer either<br><sub>The client passes the server's flags (is_copy, can_query); the framework drops chatData and chatType of a copy, and viewer of any message without can_query.</sub> | in | I1 | host |  | visual/sdk/20-forward-requests |
| `behaviour.preview` | A widget in a list of inline results is a preview: the client creates it with context.preview and the result's data, routes no touches to it (a tap selects the result) and creates a new host for the sent message<br><sub>The host drops the preview's input, every bridge call but ready and setResizeAnchor, and its storage commits; chatType and viewer are null.</sub> | in | I1 | host |  | visual/sdk/23-preview |
| `behaviour.network-marker` | The network marker: the client marks a widget whose tap may send data to the bot's server exactly where the message has can_query (a UI contract of the server's spec)<br><sub>Never without allow_queries, never on a forward of a widget without fwd_allow_queries, never on a preview; the client's own UI from context.canQuery (docs/integration.md).</sub> | in | I1 | host |  | visual/sdk/21-network-marker |
| `behaviour.size-hint` | Before load the bubble takes the bot's size anchors (width 0 = the bubble's width, W = a fixed width in [200, max]; height 0 = the placeholder, then the content height in [50, 600]; H = a fixed clamp(H, 50, 600)), cached per message<br><sub>Testable with a test bot sending inputMediaWidget with the anchors set; 200 / 50 / 600 are the shared v0.1.0 constants.</sub> | in | D12a | host |  | visual/sdk/17-size-hint |
| `behaviour.policy-strict` | strict policy on every client (gesture gate, text caps, budgets) | in | D5 | kept |  | auto/19-limits |

## Today

- **kept**: passes through to the renderer, or the shim provides it as specified
- **stripped**: dropped by the validator, or never forwarded by the renderer
- **replaced**: rewritten to an in-profile equivalent (an element, a value, a placeholder image, a gradient that paints nothing)
- **resolved**: answered by the pipeline from the profile, the theme or the client settings, not by the renderer engine
- **stub**: present but inert: a fixed answer, a throw, or nothing drawn
- **emulated**: implemented by the shim with its own semantics
- **host**: needs the client: the host glue answers or acts
- **missing**: not provided

## Decisions

- **D3**: A feature is in when every client draws it; :hover and user-select: text are the exceptions (progressive enhancement: where a client lacks them, the widget still works)
- **D3a**: v1: canvas 2D; Web Animations, filter, backdrop-filter if clients draw them; out: forms, inner scroll, 3D transforms, magic move; CSS animations and transitions in
- **D3b**: Fonts: system generic families only, other names dropped
- **D4**: Input: taps in the PoC, drag in v1; no keyboard at all, not even Enter or Space (it conflicts with the chat input field; Artem, 2026-10-16)
- **D5**: strict policy everywhere; webAnimations and morphUpdates from the profile
- **D6**: 3D (__tw3d) cut from the release
- **D8**: SDK outward: openLink (confirmed with the full URL) and openTelegramLink (no confirmation of its own, 2026-10-29) on a tap, haptics; no sendMessage
- **SDK**: SDK v1 (docs/sdk-v1.md), with Artem's answers of 2026-10-17: haptics without a gesture (bucket 5, 10 per second), any t.me path, URLs of at most 256 characters, 16 handlers per event type, stopped after 2 crashes in a row, double tap and long press stay with the bubble (replaced by G1 in v0.1.2)
- **D11**: One widget model: classic DOM in the sandbox
- **D12a**: Above the cap: hard clip, no fullscreen
- **D14**: Theme: themeParams + --tg-theme-*, theme change event
- **D17**: State: localStorage per (account, chat, message id): the chat the message is in, as message ids are unique only within a chat (supergroups); 4 KB, QuotaExceededError; sessionStorage in memory
- **L1**: Strict limits from the start (2026-10-29): about twice what real widgets use; past a counted cap the widget stops (docs/decisions.md)
- **L2**: Heavy boxes (Artem, 2026-11-02, after the client teams' stress-kit runs on five devices): at most 16 heavy boxes at once (a blurred box-shadow layer, a filter, a backdrop-filter, opacity below 1 but a disabled or pressed <button>'s, or a running animation of filter, backdrop-filter or opacity, or of box-shadow through a blurred layer), of them at most 2 with backdrop-filter; every renderer counts on the computed style (packages/core/src/shared/paint-policy.ts and its fixture) and past either the widget stops. A tightening of profile 1 (widgets were not launched yet), not profile 2
- **D18**: Text is not selectable by default; a widget enables selection with user-select: text (2026-10-20, replaces "Text selection forbidden")
- **D20**: Mini-HTML (2026-10-17): a widget is one HTML document of at most 4 KB compressed and 32 KB uncompressed, delivered in the message; the server compresses the HTML from the bot (zlib at level 9 since 2026-10-24) and enforces both limits
- **D21**: No accessibility in v1
- **D23**: Runtime profile detection: TelegramWidget.profile / isProfileAtLeast, CSS.supports and @supports from the profile
- **brief**: Named as a candidate in the conformance brief (decided by measurement)
- **inv1**: Invariant 1: widget JS only in QuickJS
- **inv2**: Invariant 2: the host validates everything against the allowlists
- **inv4**: Invariant 4: only data: images, host blob: and #fragment URLs reach the renderer
- **inv5**: Invariant 5: no network in QuickJS
- **inv6**: Invariant 6: navigation cancelled, shadow scope
- **proposed**: Proposed by this catalogue, to be confirmed
- **P1**: Profile v1 freeze (2026-10-17): a candidate is in when the corpus uses it and the web renderers draw it; the rest are out until the native renderers are measured (profile 2)
- **P1-grid**: Grid is in on condition that the Swift renderer and TwidgetCore implement it before their releases (2026-10-17)
- **P1-libs**: No bundled libraries in v1 (2026-10-17)
- **P1-ua**: Clarified in v0.1.1 (2026-10-20): the renderer's user-agent layer (tables.uaStylesheet) also fixes the root and the text elements: the bubble's text colour (the bubble's own fill is the canvas behind a transparent renderer), the client's text tokens (--tg-text-font-size, --tg-text-line-height, --tg-text-padding as the body's margin) and explicit sizes and margins for headings, paragraphs, lists, tables, quotes and code, so a UA default in this profile means that layer, not an engine's own stylesheet
- **G1**: Gestures, v0.1.2 (Artem, 2026-10-22): one shared policy with the same thresholds on every client and in the sandbox. A double tap over the widget is always the widget's (both clicks and dblclick, on every element; the reaction stays with the bubble outside the widget). For the rest touch-action is the claim, decided per element at pointerdown over the target and its ancestors (a container covers its children, as in CSS): auto: holds and swipes are the bubble's (the widget gets pointercancel); pan-y / pan-x: drags on the other axis are the widget's; none: everything is the widget's, holds (contextmenu) included. The leading 20 px of the widget and the bubble outside it never take a claim. Replaces "double tap and long press stay with the bubble" (SDK)
- **G2**: Vertical drags are the chat's, v0.1.3 (Artem, 2026-10-24): a widget claims only horizontal drags; a vertical drag over a widget always scrolls the chat, whatever touch-action says, and the widget gets pointercancel. auto: holds and drags are the bubble's; pan-y: horizontal drags are the widget's; pan-x claims nothing; none: horizontal drags and holds (contextmenu) are the widget's. The axis lock (the first move past the 10 px slop decides for the whole press), double taps and the leading 20 px are unchanged. The engine under the renderer always allows vertical panning. Replaces G1's pan-x and the vertical half of none
- **R1**: Resize anchor, v0.1.3 (Artem, 2026-10-24): TelegramWidget.setResizeAnchor('top' | 'bottom') says which edge of the bubble stays put on screen when the widget's height changes (top by default: it grows downward; bottom: it grows upward and the content above moves). An SDK v1 addition inside profile 1: TelegramWidget.profile stays 1 and widgets detect the method with typeof. It goes through the bridge as a checked call (sdk-policy, four copies and the fixture), needs no gesture, and the host keeps the latest value and reports it with the height; the client's list compensates its scroll
- **I1**: The bot's data and requests (Artem, 2026-10-25; the server's spec): a widget is fixed code plus changeable state: the HTML never changes after sending; the state changes, and only the bot writes it. TelegramWidget.chatData, the chat state (one original message, seen only in its chat: in a group every member sees it; never forwarded), and publicData, the shared state (wherever the widget is shown: a forward gets a copy, or shares a shared widget's one state with the original): each a text of up to 1024 bytes (UTF-8), read-only in the widget; chatDataChanged / publicDataChanged when they change (no restart). isForward, chatType and viewer (Mini Apps' WebAppUser: id, first_name, last_name, username, is_premium; a field that breaks its rule is left out); a forward is told nothing about the chat it is in (no chatType: the bot must not learn where its widget was forwarded), and the widget knows its viewer only where it may ask the bot (can_query), originals included. request(data of 1-64 bytes): a callback button inside the widget; where it may ask is the server's per message (can_query: originals of a widget sent with allow_queries, forwards of one sent with fwd_allow_queries; background_query: the original in the private chat with the bot); the client marks a widget with can_query as one whose tap may send data to the bot's server; on a tap, one per tap (shared with openLink, openTelegramLink and copyText); in the background while visible: one when the widget appears on screen, then one per 5 s; one in flight; resolves { result } (text of at most 1024 bytes), rejects with error.code; the answer does not change the state. copyText(text of 1-256 characters) on a tap; the client's toast shows what it copied. Part of SDK v1 and layer 233 from the launch, inside profile 1. TelegramWidget.isPreview: an inline result in the list before it is sent gets its state but no input, no bridge call but ready and setResizeAnchor, no chat type or viewer, and Web Storage that is never committed. The checks are sdk-policy (four copies and the fixture)
- **P1-safe-area**: Clarified on 2026-10-29 (Artem; widgets were not launched yet), not profile 2: a client draws the message time and its status over the bottom corner of a widget with no caption below it, as on a photo (with a caption below the time is in the caption's line and nothing is drawn over the widget; a caption shown above the widget, invert_media, is as none: Artem's decision of 2026-10-30). env(safe-area-inset-top|right|bottom|left[, fallback]) is in: the validator rewrites it to var(--tg-content-safe-area-inset-…[, fallback]), root variables the renderer sets like the theme's (bottom = the height of the time's band, the others 0), with --tg-content-safe-area-corner-left|right for the width of the client's corner in that band (a physical side: right in a left-to-right interface, left in a right-to-left one); 0 with a caption below. Another env() name resolves to its fallback or drops the declaration. The UA layer adds the band to the body's bottom padding, and TelegramWidget.contentSafeAreaInset with the contentSafeAreaChanged event gives the same numbers to script
