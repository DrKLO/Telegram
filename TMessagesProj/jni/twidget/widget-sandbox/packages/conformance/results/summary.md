# Conformance results

Generated 2026-11-02 12:44 UTC by `packages/conformance/tools/report.mjs` (profile 1; web iframe — Chromium 153.0.8010.12 (headless, Playwright) @ 9c64646; web shadow — Chromium 153.0.8010.12 (headless, Playwright) @ 9c64646; policy strict, features webAnimations).

Verdict per catalogue ID over the automatic set: ✅ pass, ◐ partly, ❌ fail, ⏳ expected to fail (not implemented yet: D23, the profile in the validator, …), ❗ passes although expected to fail, – not applicable here, · not tested. For **out** features "pass" means *neutralised as the profile says*. The native columns are a plain browser engine without the pipeline: a reference for the tests, not an expectation.

## Totals

| Renderer | in (201) ✅ / ◐ / ❌ / ⏳ | out (78) neutralised ✅ / not yet ⏳ / ❌ | auto runs loaded | errors |
|---|---|---|---|---|
| web iframe | 182 / 3 / 0 / 0 | 77 / 0 / 0 | 135 / 135 | 0 |
| web shadow | 182 / 3 / 0 / 0 | 77 / 0 / 0 | 135 / 135 | 0 |

## In the profile

| ID | web iframe | web shadow | native chromium |
|---|---|---|---|
| `html.el.structure` | ✅ | ✅ | ✅ |
| `html.el.headings` | ✅ | ✅ | ✅ |
| `html.el.sections` | ✅ | ✅ | ✅ |
| `html.el.inline-text` | ✅ | ✅ | ✅ |
| `html.el.sub-sup` | ✅ | ✅ | ✅ |
| `html.el.pre` | ✅ | ✅ | ✅ |
| `html.el.blockquote` | ✅ | ✅ | ✅ |
| `html.el.lists` | ✅ | ✅ | ✅ |
| `html.el.table` | ✅ | ✅ | ✅ |
| `html.el.img` | ✅ | ✅ | ✅ |
| `html.el.canvas` | ✅ | ✅ | · |
| `html.el.button` | ✅ | ✅ | ✅ |
| `html.el.details` | ✅ | ✅ | ✅ |
| `html.el.a-fragment` | ✅ | ✅ | ✅ |
| `html.el.a-external` | ✅ | ✅ | ✅ |
| `html.el.unknown` | ✅ | ✅ | ✅ |
| `html.attr.global` | ✅ | ✅ | ✅ |
| `html.attr.dir` | ✅ | ✅ | ✅ |
| `html.attr.data-aria` | ✅ | ✅ | ✅ |
| `html.attr.event-handlers` | ✅ | ✅ | ✅ |
| `svg.shapes` | ✅ | ✅ | ✅ |
| `svg.structure` | ✅ | ✅ | ✅ |
| `svg.text` | ✅ | ✅ | ✅ |
| `svg.gradients` | ✅ | ✅ | · |
| `svg.css` | ✅ | ✅ | ✅ |
| `css.display.basic` | ✅ | ✅ | ✅ |
| `css.display.flex` | ✅ | ✅ | ✅ |
| `css.gap.flex` | ✅ | ✅ | ✅ |
| `css.display.grid` | ✅ | ✅ | ✅ |
| `css.display.table` | ✅ | ✅ | ✅ |
| `css.box.size` | ✅ | ✅ | ✅ |
| `css.box.spacing` | ✅ | ✅ | ✅ |
| `css.box.border` | ✅ | ✅ | ✅ |
| `css.box.radius` | ✅ | ✅ | · |
| `css.box.sizing` | ✅ | ✅ | ✅ |
| `css.box.aspect-ratio` | ✅ | ✅ | ✅ |
| `css.box.outline` | ✅ | ✅ | ✅ |
| `css.box.shadow` | ✅ | ✅ | · |
| `css.box.float` | ✅ | ✅ | ✅ |
| `css.box.object-fit` | ✅ | ✅ | · |
| `css.position.relative-absolute` | ✅ | ✅ | ✅ |
| `css.position.fixed` | ✅ | ✅ | ✅ |
| `css.position.z-index` | ✅ | ✅ | ✅ |
| `css.overflow.clip` | ✅ | ✅ | ✅ |
| `css.visibility` | ✅ | ✅ | ✅ |
| `css.opacity` | ✅ | ✅ | ✅ |
| `css.color.basic` | ✅ | ✅ | ✅ |
| `css.color.light-dark` | ✅ | ✅ | ✅ |
| `css.background.basic` | ✅ | ✅ | ✅ |
| `css.gradient.linear` | ✅ | ✅ | · |
| `css.gradient.radial` | ✅ | ✅ | · |
| `css.gradient.conic` | ✅ | ✅ | · |
| `css.font.generic` | ✅ | ✅ | ✅ |
| `css.font.ui-families` | ✅ | ✅ | ✅ |
| `css.font.basic` | ✅ | ✅ | ✅ |
| `css.font.variant-numeric` | ✅ | ✅ | ✅ |
| `css.text.align` | ✅ | ✅ | ✅ |
| `css.text.decoration` | ✅ | ✅ | ✅ |
| `css.text.transform` | ✅ | ✅ | ✅ |
| `css.text.white-space` | ✅ | ✅ | ✅ |
| `css.text.wrap` | ✅ | ✅ | ✅ |
| `css.text.overflow` | ✅ | ✅ | · |
| `css.text.vertical-align` | ✅ | ✅ | ✅ |
| `css.text.list-style` | ✅ | ✅ | ✅ |
| `css.text.rtl` | ✅ | ✅ | ✅ |
| `css.text.emoji` | ✅ | ✅ | ✅ |
| `css.text.selection` | ✅ | ✅ | ✅ |
| `css.transform.2d` | ✅ | ✅ | ✅ |
| `css.filter.blur-brightness-drop-shadow` * | ✅ | ✅ | ✅ |
| `css.backdrop-filter` * | ✅ | ✅ | · |
| `css.pointer-events` | ✅ | ✅ | ✅ |
| `css.touch-action` | ✅ | ✅ | ✅ |
| `css.cursor` | ✅ | ✅ | ✅ |
| `css.value.units` | ✅ | ✅ | ✅ |
| `css.value.vw` | ✅ | ✅ | ✅ |
| `css.value.math` | ✅ | ✅ | ✅ |
| `css.value.custom-properties` | ✅ | ✅ | ✅ |
| `css.value.keywords` | ✅ | ✅ | ✅ |
| `css.value.content` | ✅ | ✅ | ✅ |
| `css.value.env` | ✅ | ✅ | ✅ |
| `sel.basic` | ✅ | ✅ | ✅ |
| `sel.structural` | ✅ | ✅ | ✅ |
| `sel.is-where` | ✅ | ✅ | ✅ |
| `sel.root` | ✅ | ✅ | ✅ |
| `sel.before-after` | ✅ | ✅ | ✅ |
| `sel.hover` | ✅ | ✅ | ✅ |
| `sel.active` | ✅ | ✅ | ✅ |
| `at.media.width` | ✅ | ✅ | ✅ |
| `at.media.color-scheme` | ✅ | ✅ | ✅ |
| `at.media.reduced-motion` | ✅ | ✅ | ✅ |
| `at.supports` | ✅ | ✅ | ✅ |
| `at.keyframes` | ✅ | ✅ | · |
| `anim.transition` | ✅ | ✅ | ✅ |
| `anim.keyframes` | ✅ | ✅ | ✅ |
| `anim.play-state` | ✅ | ✅ | ✅ |
| `anim.easing` | ✅ | ✅ | ✅ |
| `anim.events` | ◐ | ◐ | ✅ |
| `anim.waapi` * | ✅ | ✅ | ✅ |
| `anim.waapi-css-control` * | ✅ | ✅ | ✅ |
| `canvas.rect` | ✅ | ✅ | · |
| `canvas.path` | ✅ | ✅ | · |
| `canvas.line-style` | ✅ | ✅ | · |
| `canvas.colors` | ✅ | ✅ | · |
| `canvas.gradients` | ✅ | ✅ | · |
| `canvas.transform` | ✅ | ✅ | · |
| `canvas.clip` | ✅ | ✅ | · |
| `canvas.text` | ✅ | ✅ | ✅ |
| `canvas.alpha` | ✅ | ✅ | · |
| `canvas.path2d` | ✅ | ✅ | · |
| `canvas.draw-image` | ◐ | ◐ | ✅ |
| `canvas.hit-test` | ✅ | ✅ | ✅ |
| `canvas.hidpi` | ✅ | ✅ | ✅ |
| `js.dom.core` | ✅ | ✅ | ✅ |
| `js.dom.query` | ✅ | ✅ | ✅ |
| `js.dom.style` | ✅ | ✅ | ✅ |
| `js.dom.events` | ✅ | ✅ | ✅ |
| `js.layout.rect` | ✅ | ✅ | ✅ |
| `js.layout.box` | ✅ | ✅ | ✅ |
| `js.layout.computed-style` | ◐ | ◐ | ✅ |
| `js.layout.match-media` | ✅ | ✅ | ✅ |
| `js.layout.resize-observer` | ✅ | ✅ | ✅ |
| `js.layout.intersection-observer` | ✅ | ✅ | ✅ |
| `js.layout.element-from-point` | ✅ | ✅ | ✅ |
| `js.layout.measure-text` | ✅ | ✅ | ✅ |
| `js.timers` | ✅ | ✅ | ✅ |
| `js.raf` | ✅ | ✅ | ✅ |
| `js.microtasks` | ✅ | ✅ | ✅ |
| `js.storage.local` | ✅ | ✅ | ✅ |
| `js.storage.session` | ✅ | ✅ | ✅ |
| `js.intl` | ✅ | ✅ | ✅ |
| `js.intl.format-to-parts` | ✅ | ✅ | ✅ |
| `js.locale` | ✅ | ✅ | ✅ |
| `js.crypto-random` | ✅ | ✅ | ✅ |
| `js.builtins` | ✅ | ✅ | ✅ |
| `js.eval` | ✅ | ✅ | ✅ |
| `js.css-supports` | ✅ | ✅ | ✅ |
| `js.visibility` | ✅ | ✅ | ✅ |
| `tw.ready` | ✅ | ✅ | – |
| `tw.theme` | ✅ | ✅ | – |
| `tw.theme-params` | ✅ | ✅ | ✅ |
| `tw.events` | ✅ | ✅ | – |
| `tw.is-active` | ✅ | ✅ | – |
| `tw.resize-anchor` | ✅ | ✅ | – |
| `tw.content-safe-area` | ✅ | ✅ | ✅ |
| `tw.bot-data` | ✅ | ✅ | – |
| `tw.context` | ✅ | ✅ | – |
| `tw.request` | ✅ | ✅ | – |
| `tw.copy-text` | ✅ | ✅ | – |
| `tw.watch-public-data` | ✅ | ✅ | – |
| `tw.preview` | ✅ | ✅ | – |
| `tw.open-link` | ✅ | ✅ | – |
| `tw.open-telegram-link` | ✅ | ✅ | – |
| `tw.haptic` | ✅ | ✅ | – |
| `tw.profile` | ✅ | ✅ | – |
| `input.tap` | ✅ | ✅ | ✅ |
| `input.pointer` | ✅ | ✅ | ✅ |
| `input.mouse-compat` | ✅ | ✅ | ✅ |
| `input.drag` | ✅ | ✅ | ✅ |
| `input.hover-events` | ✅ | ✅ | ✅ |
| `input.dblclick` | ✅ | ✅ | ✅ |
| `input.long-press` | ✅ | ✅ | ✅ |
| `input.user-activation` | ✅ | ✅ | · |
| `input.prevent-default` | ✅ | ✅ | ✅ |
| `limit.text-node` | ✅ | ✅ | ✅ |
| `limit.text-budget` | ✅ | ✅ | ✅ |
| `limit.nodes` | ✅ | ✅ | – |
| `limit.timers` | ✅ | ✅ | – |
| `limit.cpu` | · | · | · |
| `limit.memory` | · | · | · |
| `limit.storage` | ✅ | ✅ | – |
| `limit.attribute` | ✅ | ✅ | ✅ |
| `limit.batch` | ✅ | ✅ | – |
| `limit.css-rules` | ✅ | ✅ | – |
| `limit.canvas-commands` | ✅ | ✅ | – |
| `limit.animations` | · | · | · |
| `limit.main-thread` | · | · | · |
| `limit.canvas-pixels` | ✅ | ✅ | – |
| `limit.paint-heavy` | ✅ | ✅ | ✅ |
| `limit.paint-backdrop` | ✅ | ✅ | ✅ |
| `limit.image-pixels` | ✅ | ✅ | ✅ |
| `limit.url-length` | ✅ | ✅ | – |
| `limit.event-handlers` | ✅ | ✅ | – |
| `limit.haptic-rate` | ✅ | ✅ | – |
| `limit.html-size` | · | · | · |
| `behaviour.theme-switch` | ✅ | ✅ | – |
| `behaviour.offscreen-pause` | ✅ | ✅ | – |
| `behaviour.auto-height` | ✅ | ✅ | · |
| `behaviour.no-selection` | ✅ | ✅ | · |
| `behaviour.restart` | · | · | · |
| `behaviour.gesture-claim` | ✅ | ✅ | ✅ |
| `behaviour.storage-persist` | · | · | · |
| `behaviour.storage-forward` | · | · | · |
| `behaviour.message-edit` | · | · | · |
| `behaviour.resize-anchor` | · | · | · |
| `behaviour.time-corner` | · | · | · |
| `behaviour.data-edit` | · | · | · |
| `behaviour.forward-copy` | · | · | · |
| `behaviour.preview` | · | · | · |
| `behaviour.network-marker` | · | · | · |
| `behaviour.size-hint` | · | · | · |
| `behaviour.policy-strict` | ✅ | ✅ | ✅ |

## Out of the profile (pass = neutralised)

| ID | web iframe | web shadow | native chromium |
|---|---|---|---|
| `html.el.ruby` | ✅ | ✅ | ❌ |
| `html.el.legacy` | ✅ | ✅ | ❌ |
| `html.el.img-svg` | ✅ | ✅ | ❌ |
| `html.el.img-other-formats` | ✅ | ✅ | ❌ |
| `html.el.img-remote` | ✅ | ✅ | – |
| `html.el.forms` | ✅ | ✅ | ❌ |
| `html.el.progress-meter` | ✅ | ✅ | ❌ |
| `html.el.dialog-popover` | ✅ | ✅ | ❌ |
| `html.el.media` | ✅ | ✅ | ❌ |
| `html.el.embedding` | ✅ | ✅ | ❌ |
| `html.attr.legacy-presentational` | ✅ | ✅ | ◐ |
| `html.attr.contenteditable` | ✅ | ✅ | ❌ |
| `html.attr.draggable` | ✅ | ✅ | ❌ |
| `html.attr.tabindex` | ✅ | ✅ | ❌ |
| `svg.clip-mask` | ✅ | ✅ | · |
| `svg.filters` | ✅ | ✅ | · |
| `svg.smil` | ✅ | ✅ | · |
| `svg.foreign-object` | ✅ | ✅ | · |
| `svg.image` | ✅ | ✅ | · |
| `css.display.other` | ✅ | ✅ | ❌ |
| `css.box.logical` | ✅ | ✅ | ❌ |
| `css.box.columns` | ✅ | ✅ | ❌ |
| `css.position.sticky` | ✅ | ✅ | ❌ |
| `css.overflow.scroll` | ✅ | ✅ | ❌ |
| `css.color.modern` | ✅ | ✅ | · |
| `css.color.mix` | ✅ | ✅ | ❌ |
| `css.background.clip-text` | ✅ | ✅ | · |
| `css.url.remote` | ✅ | ✅ | – |
| `css.font.named` | ✅ | ✅ | ❌ |
| `css.font.face` | ✅ | ✅ | ✅ |
| `css.text.line-clamp` | ✅ | ✅ | ❌ |
| `css.text.shadow` | ✅ | ✅ | · |
| `css.text.writing-mode` | ✅ | ✅ | ❌ |
| `css.transform.3d` | ✅ | ✅ | ❌ |
| `css.filter.other` | ✅ | ✅ | · |
| `css.filter.url` | ✅ | ✅ | · |
| `css.blend` | ✅ | ✅ | · |
| `css.clip-path` | ✅ | ✅ | · |
| `css.mask` | ✅ | ✅ | · |
| `css.value.vh` | ✅ | ✅ | ❌ |
| `sel.has` | ✅ | ✅ | ❌ |
| `sel.first-letter-line` | ✅ | ✅ | ❌ |
| `sel.focus` | ✅ | ✅ | ❌ |
| `sel.form-state` | ✅ | ✅ | ❌ |
| `sel.host-scoped` | ✅ | ✅ | ❌ |
| `sel.target` | ✅ | ✅ | ✅ |
| `at.media.hover-pointer` | ✅ | ✅ | ❌ |
| `at.media.other` | ✅ | ✅ | ✅ |
| `at.import-namespace` | ✅ | ✅ | ❌ |
| `at.container` | ✅ | ✅ | ◐ |
| `at.layer` | ✅ | ✅ | ❌ |
| `at.property` | ✅ | ✅ | ❌ |
| `at.exotic` | ✅ | ✅ | ❌ |
| `at.nesting` | ✅ | ✅ | ◐ |
| `anim.magic-move` | ✅ | ✅ | – |
| `canvas.conic-gradient` | ✅ | ✅ | · |
| `canvas.pattern` | ✅ | ✅ | · |
| `canvas.composite` | ✅ | ✅ | · |
| `canvas.shadow` | ✅ | ✅ | · |
| `canvas.filter` | ✅ | ✅ | · |
| `canvas.image-data` | ✅ | ✅ | ❌ |
| `canvas.readback` | ✅ | ✅ | ❌ |
| `canvas.webgl` | ✅ | ✅ | – |
| `js.focus` | ✅ | ✅ | ❌ |
| `js.libraries` | ✅ | ✅ | – |
| `js.network` | ✅ | ✅ | – |
| `js.navigation` | ✅ | ✅ | – |
| `js.dialogs` | ✅ | ✅ | – |
| `js.device` | ✅ | ✅ | – |
| `js.shadow-dom` | ✅ | ✅ | – |
| `js.blob-file` | ✅ | ✅ | – |
| `js.fullscreen` | ✅ | ✅ | – |
| `tw.send-message` | ✅ | ✅ | – |
| `input.touch-events` | – | – | – |
| `input.keyboard` | ✅ | ✅ | ❌ |
| `input.key-activation` | ✅ | ✅ | ❌ |
| `input.key-gesture` | ✅ | ✅ | · |
| `input.wheel-scroll` | ✅ | ✅ | ❌ |

`*` in the profile if every client draws it (D3a).

## web iframe: in-profile failures

- `anim.events`: 10-css-animations: animation events carry animationName and elapsedTime ("" at 0); 10-css-animations: transitionend carries propertyName ("")
- `canvas.draw-image`: 12-canvas-b: draw-image (diff 50.4 % of ink (max 5 %))
- `js.layout.computed-style`: 14-dom-layout-api-b: shorthand white-space (got "", want "nowrap"); 14-dom-layout-api-b: shorthand overflow (got "", want "hidden"); 14-dom-layout-api-b: shorthand margin (got "", want "4px 0px")

## web shadow: in-profile failures

- `anim.events`: 10-css-animations: animation events carry animationName and elapsedTime ("" at 0); 10-css-animations: transitionend carries propertyName ("")
- `canvas.draw-image`: 12-canvas-b: draw-image (diff 50.4 % of ink (max 5 %))
- `js.layout.computed-style`: 14-dom-layout-api-b: shorthand white-space (got "", want "nowrap"); 14-dom-layout-api-b: shorthand overflow (got "", want "hidden"); 14-dom-layout-api-b: shorthand margin (got "", want "4px 0px")

## Visual set

For people (the test channel): judged by eye. Runners only check that each widget loads without errors.

| Widget | web iframe | web shadow |
|---|---|---|
| visual/01-boxes | ✅ | ✅ |
| visual/02-colours-gradients | ✅ | ✅ |
| visual/03-text-a | ✅ | ✅ |
| visual/03-text-b | ✅ | ✅ |
| visual/04-layout | ✅ | ✅ |
| visual/05-transforms-effects-a | ✅ | ✅ |
| visual/05-transforms-effects-b | ✅ | ✅ |
| visual/06-css-animations | ✅ | ✅ |
| visual/07-web-animations | ✅ | ✅ |
| visual/08-canvas-a | ✅ | ✅ |
| visual/08-canvas-b | ✅ | ✅ |
| visual/09-svg-a | ✅ | ✅ |
| visual/09-svg-b | ✅ | ✅ |
| visual/10-images | ✅ | ✅ |
| visual/11-interaction | ✅ | ✅ |
| visual/12-theme | ✅ | ✅ |
| visual/13-out-of-profile | ✅ | ✅ |
| visual/14-unstyled-document | ✅ | ✅ |
| visual/15-body-background | ✅ | ✅ |
| visual/16-selection | ✅ | ✅ |
| visual/17-time-corner | ✅ | ✅ |
| visual/sdk/01-link-external | ✅ | ✅ |
| visual/sdk/02-link-telegram | ✅ | ✅ |
| visual/sdk/03-link-rejected | ✅ | ✅ |
| visual/sdk/04-link-timer | ✅ | ✅ |
| visual/sdk/05-haptics | ✅ | ✅ |
| visual/sdk/06-haptics-rate | ✅ | ✅ |
| visual/sdk/07-storage-recreate | ✅ | ✅ |
| visual/sdk/08-storage-restart | ✅ | ✅ |
| visual/sdk/09-storage-delete | ✅ | ✅ |
| visual/sdk/10-storage-quota | ✅ | ✅ |
| visual/sdk/11-crash-restart | ✅ | ✅ |
| visual/sdk/12-drag-gestures | ✅ | ✅ |
| visual/sdk/13-max-height | ✅ | ✅ |
| visual/sdk/14-theme-palette | ✅ | ✅ |
| visual/sdk/15-storage-forward | ✅ | ✅ |
| visual/sdk/16-state-edit | ✅ | ✅ |
| visual/sdk/17-size-hint | ✅ | ✅ |
| visual/sdk/18-resize-anchor | ✅ | ✅ |
| visual/sdk/19-request-tap | ✅ | ✅ |
| visual/sdk/20-forward-requests | ✅ | ✅ |
| visual/sdk/21-network-marker | ✅ | ✅ |
| visual/sdk/22-copy-text | ✅ | ✅ |
| visual/sdk/23-preview | ✅ | ✅ |
| visual/sdk/24-watch-public-data | ✅ | ✅ |
| visual/sdk/25-group-chat-state | ✅ | ✅ |
| visual/sdk/26-haptics-no-touch | ✅ | ✅ |
