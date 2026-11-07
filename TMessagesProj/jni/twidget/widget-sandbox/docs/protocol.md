# Sandbox ↔ host ↔ renderer protocol

Three trust domains exchange plain JSON messages:

```
sandbox (QuickJS, UNTRUSTED) ──ops / queries / listen / pd / raf / log / diag / bridge / storage──▶ host (trusted)
sandbox ◀── events / timer & frame ticks / query results / viewport / theme / safeArea / visibility ──── host
host ──validated RenderOps / queries / listen / preventDefault / requestFrame / theme / safeArea / policy / frozen / arbitrate──▶ renderer (trusted code, sandboxed iframe)
host ◀── events / query results / size / resize / frame / media / gesture / bubble / limit / violation ──── renderer
```

Type definitions live in `packages/core/src/shared/protocol.ts`. Nothing from the sandbox is
forwarded to the renderer without passing through `Validator`
(`packages/core/src/host/validator.ts`); the renderer additionally refuses forbidden tags and
attributes on its own (defense in depth).

This page is protocol 1 (`protocol` in `VERSION.json`). A change that a glue of the previous
release cannot run with (a config field it must send, a host call or message it does not
handle, a change to the validator's API) bumps it in the same commit; an addition the old glue
can ignore does not. Every native bundle carries the number on its first line and the native
glue refuses a bundle of another protocol before it runs (`bundle-mismatch`,
`docs/integration.md` "Pinning a release").

## Node ids

- Every node that the sandbox wants rendered gets a **stable integer id** chosen
  by the sandbox (`> 1`, `< 2^31`). Id `1` is reserved for the Document and is
  only valid as the parent of the `<html>` element.
- An id may be reused only after the host has been told to remove it (`rm`).
  Creating an existing id is a protocol violation and the op is dropped.
- The validator keeps its own model (kind, tag, parent, children, rendered or
  not) and never trusts anything the sandbox claims beyond the op itself.

## Sandbox → host: `ops` (mutation batch)

`{ type: 'ops', ops: SandboxOp[] }` — applied in order, at most 10 000 per
batch under `strict` (past it the widget stops), 200 000 under `compat`. The sandbox flushes a batch at the end of every task (event, timer,
frame, script) and *before* every sync query; a long flush goes out in several batches, split between two nodes once
about 1000 ops are buffered (since v0.6.0; its `call` ops, canvases and animations go in its last batch, with the staged
canvas payloads). A host applies them in order, as it always did.

| op | fields | meaning |
|---|---|---|
| `el` | `id, tag, ns?: 'svg', attrs?: {name: value}` | create element (detached) |
| `text` | `id, data` | create text node |
| `comment` | `id, data` | create comment (tracked, never rendered) |
| `attr` | `id, name, value` | set attribute |
| `rattr` | `id, name` | remove attribute |
| `data` | `id, data` | set text/comment data |
| `ins` | `parent, id, before: id \| null` | insert `id` under `parent` before `before` (null = append) |
| `rm` | `id` | remove node and forget its subtree |
| `style` | `id, name, value, important?` | set one inline style property (`value: ''` removes) |
| `prop` | `id, name, value` | set a DOM property: `scrollTop`, `scrollLeft` (profile 1; the form control properties left with the controls) |
| `call` | `id, method, args?` | call a method: `scrollTo scrollBy setPointerCapture releasePointerCapture observeResize unobserveResize` (profile 1: `focus`, `blur`, `select`, `setSelectionRange`, `scrollIntoView`, the dialog and popover methods and `completeEditorCommand` are no-ops in the shim and refused by the validator) |
| `canvas` | `id, cmds: CanvasCmd[]` | replay 2D canvas commands on `<canvas id>` |

### Canvas commands

`CanvasCmd` is an array `[kind, ...]`:

| kind | shape | meaning |
|---|---|---|
| `c` | `['c', method, ...args]` | call a method from the allowlist (`fillRect`, `arc`, `drawImage`, …) |
| `s` | `['s', prop, value]` | set a state property (`fillStyle`, `font`, …); `filter` is CSS-sanitized and may not contain `url()` |
| `r` | `['r', width, height]` | resize the bitmap (resets state), 0…16384 |
| `g` | `['g', gid, 'linear'\|'radial'\|'conic'\|'pattern', ...args]` | create a gradient / pattern object `gid` |
| `gs` | `['gs', gid, offset, color]` | `addColorStop` |
| `pn` | `['pn', pid, svgPath \| null]` | create a `Path2D` |
| `pc` | `['pc', pid, method, ...args]` | call a path-building method on `Path2D pid` |

Arguments are numbers, short strings, booleans, `null`, flat number arrays, or
object references: `{g: gid}` gradient/pattern, `{p: pid}` Path2D,
`{n: nodeId}` an image source that must be a rendered `canvas`/`img`/`video`,
`{w, h, b64}` an `ImageData`.

### Features (opt-in wire forms)

`config.features` lists the optional forms a host understands; absent or
empty means plain JSON only, which every host, glue and renderer speaks, so
web, Android and desktop keep working unchanged. The shim uses a form only when
the feature is listed **and** the glue installed the matching function. Each
glue passes on only the features it implements and drops the rest:

| feature | web (`WidgetHostOptions.features`) | Node harness | Swift | Kotlin | C++ |
|---|---|---|---|---|---|
| `webAnimations` | yes | yes | yes | yes | yes |
| `noInPlaceText`, `noAttrFast`, `noCanvasRect` (opt-outs) | yes | yes | yes | yes | yes |
| `canvasWords`, `binaryOps` | no | yes | yes | no | no |
| `morphUpdates` | no | yes | yes | no | no |

The Swift-only forms are performance paths for Telegram macOS; see
`docs/changes.md` for the full list of what stays Swift-only.

### Web Animations (`webAnimations`)

With `webAnimations` in `config.features`, `Element.animate`, `getAnimations`,
`document.timeline`, `document.getAnimations` and the `Animation`,
`KeyframeEffect`, `AnimationEffect`, `AnimationTimeline`, `DocumentTimeline`,
`AnimationPlaybackEvent`, `CSSAnimation` and `CSSTransition` globals run in the
shim (`packages/core/src/sandbox/waapi.ts`). Timing, events and promises are
computed in the sandbox; the renderer only plays what it is told. Without the
feature `Element.animate` returns a finished stub and reports `unsupported`.
It uses no new host function: the shim sends `__host('anim', json)` after the
tick's ops, and `getAnimations` asks the renderer with the `anims` query.

`anim` is a JSON array of records (max 4096 per batch, 4 MB of JSON):

- `{aid, remove: true}`: the animation left the renderer.
- Script animation: `{aid, target, keyframes: [{offset, easing, composite, values: {css-property: value}}],
  timing: {duration, delay, endDelay, iterations (null = infinite), iterationStart, direction, fill, easing},
  composite, iterationComposite, startTime, holdTime, playbackRate}`. `startTime` is on the
  sandbox clock (`now`), `holdTime` is a current time while paused or pending; one of them is set.
- CSS animation control: `{aid, target, css: {kind: 'animation'|'transition', name, origin}, startTime,
  holdTime, playbackRate, cancelled}`, for a CSS animation the widget paused, seeked or
  cancelled; `origin` is the renderer's id from the `anims` answer.

The host validates with `validateAnim` (`validator.ts`; the Swift native node
model has its twin in `NativeAnimValidator.swift`): `target` must be a rendered
element, at most 64 keyframes with sorted offsets in [0, 1] and 64 values each,
times finite and within 24 h, playback rate within ±1000, easings from the CSS
easing grammar, and at most 64 live script animations under `strict`, 1024 under
`compat` (`animation-limit`; a CSS animation or transition the script controls
does not count, in the shim or here).
Every keyframe value goes through the same CSS property sanitizer as
`element.style` (no `url()` other than `#fragment`, no `expression()`), and
`animation-*` / `transition-*` properties are dropped, and so are the keyframe
member names `offset`, `easing`, `composite` and `computed-offset`, so a value
can never override the checked keyframe fields. Rejected records are
reported as `protocol-violation` and a live one becomes a `remove` record.
The renderer gets `{type: 'anim', now, anims}`, where `now` is the sandbox
clock at delivery so it can map `startTime` onto its own timeline. Renderers
keep animation state per widget (`AnimationState` in `renderer/web-animations.ts`;
the shadow-DOM renderer puts many widgets in one page), apply a CSS animation
record only to the animation whose effect target is the record's `target`, and
cancel a script animation whose update throws instead of leaving it running. The
web worker forwards `anim` only when `webAnimations` is enabled.

### Canvas words (`canvasWords`)

The glue installs `__hostCanvas(key, words: Float64Array, strings: string)`
(captured by the bundle and deleted from the global object like `__host`).
Commands are written into one word buffer per canvas; at flush the `canvas` op
becomes a placeholder `{"op":"canvas","id":N,"bin":K}` in the JSON batch and
payload `K` is handed over first, right before the `ops` call:

```
words   = commands… , length(string 0) … length(string m-1), m
command = kind, name, ident, argc, (tag, payload)…
kind    = 0 c | 1 s | 2 r | 3 g | 4 gs | 5 pn | 6 pc
name    = string index (c, s, g, pc) or -1
ident   = gradient/path id (g, gs, pn, pc) or 0
tag     = 0 number | 1 string index | 2 {g:id} | 3 {p:id} | 4 {n:id}
        | 5 list (payload = n, followed by n numbers) | 6 null | 7 boolean
strings = the m strings joined, lengths in UTF-16 units
```

Command K maps 1:1 onto the JSON command array; non-finite numbers mean JSON
`null`. Anything the words cannot express (`ImageData`, other objects,
ill-formed UTF-16) turns that canvas back into JSON arrays for the frame. At
most 64 payloads per batch, 2 M words and 2 M string units per payload; the
glue validates payloads (the same rules as JSON commands) and drops malformed
ones. A payload key may be referenced **once per batch**: in a binary batch a
second `canvas` op with the same key rejects the whole batch (`BinaryOps`
`canvasKey`), in a JSON batch only the first placeholder is spliced and a
repeated one reaches the validator without commands and is rejected, so one
payload is never expanded or validated twice. The Node harness decodes payloads
back into JSON (`canvasWords: true`, `decodeCanvasWords`); the Swift glue
validates them natively.

## Host → renderer: `RenderOp`

Same shapes as above, after validation:

- `el` carries `attrs` as `[name, value][]` already filtered, plus
  `adopt: 'html' | 'head' | 'body'` the first time each of those appears (the
  renderer maps them to its own existing elements instead of creating new ones).
- `css { id, text }` replaces the text of a `<style>` element with the
  **sanitized** stylesheet (the sandbox never sends `<style>` text through
  `text`/`data` to the renderer; those nodes stay in the host model only).
- `ins` with `parent: 0` detaches a node but keeps it alive (used when a
  subtree moves under an unrendered parent).

### Typed delivery (Swift, in-process renderers)

A native renderer living in the host process may set `acceptsTypedOps` and
receive the same validated batch as `[WidgetOp]` through `send(ops:)`
(`WidgetSandboxProtocol`, a dependency-free target of the Swift package), so
neither side formats or parses JSON. `WidgetOp` has typed kinds for `el`,
`text`, `data`, `ins`, `rm`, `attr`, `rattr`, `style`, `css` and canvas word
batches; every other op stays a generic field map. It is a different encoding
of the same `RenderOp`s, not a protocol change: `WidgetOp.json` renders the
batch back to this document's JSON, and the default `send(ops:)` sends exactly
that as `{type:'ops'}`, so the WebView renderer and every other port are
unaffected.

### Validation rules (summary — see `allowlist.ts`, `validator.ts`, `css-sanitizer.ts`, `url-policy.ts`)

- The widget profile (`docs/profile-v1.md`, from `packages/core/profile.json`) sets
  every list below; what it leaves out is removed with the `profile` diagnostic:
  form controls dropped with their subtree, `<form>`/`<dialog>`/`<center>` as
  `<div>` and `<ruby>`/`<label>`/the legacy inline elements as `<span>`
  (`data-tg-tag` names the original), out-of-profile CSS properties, functions,
  units, selectors, at-rules and media queries dropped, `@supports` decided,
  nested rules dropped, font families reduced to the generic ones.
- Tags: allowlist of HTML and SVG elements. `script iframe frame frameset
  object embed link meta base portal noscript template set applet param track
  area map math …` are dropped with their subtrees (tracked in the model so
  later ops referring to them are consistent). Unknown elements render as
  `<span data-tg-tag="…">`.
- Media: `audio video source track` are dropped with their subtrees, even with
  `data:` sources (no codec parser ever sees widget bytes; no autoplay).
- Attributes: every `on*` is dropped; HTML and SVG have their own allowlists.
  URL-carrying attributes are allowed only for `img[src]` (with `data:` of a
  profile image type: PNG, JPEG, GIF, WebP) and SVG
  `use/linearGradient/radialGradient[href]` (fragment only). `a[href]` keeps fragments; any other value becomes
  `href="#" data-tg-link` and the real URL stays in the sandbox (`openLink`).
  `srcset`, `ping`, `action`, `formaction`, `background`, `data`, `srcdoc`, …
  are never forwarded.
- SVG `animate*` and `<set>` are outside the profile and dropped.
- CSS: parsed with css-tree, re-generated from the AST; `@import`, `@namespace`,
  `@font-face` (system fonts only: `font-family` can name only installed
  fonts) and unknown at-rules dropped; `url()`, `image-set()`, `image()`,
  `src()`, `cross-fade()` only with `data:` images or a fragment;
  `var()`/`env()`/`attr()` inside URL-taking functions rejected.
- Sizes under `strict`: ≤ 2000 nodes in the document, a text value ≤ 32 K and the document's
  text ≤ 48 K UTF-16 units, an attribute ≤ 8 K, a batch ≤ 10 000 ops, a canvas op
  ≤ 4000 commands, the document's stylesheets ≤ 400 rules and 32 `@keyframes`,
  the widget's canvases ≤ 2048² pixels together (a new canvas 300 × 150, then its
  `width` / `height` and the `r` command; the nodes, the document's text, rules and `@keyframes` as they stand after the whole
  batch); past one the validator reports the fatal `limit` diagnostic (the reason), refuses
  the batch and every later one, and the host stops the widget (`docs/decisions.md`,
  "Strict limits from the start"). Under `compat`: text ≤ 2 MB, attribute ≤ 4 MB,
  batch ≤ 200 000 ops, canvas ≤ 500 000 commands, no rule or canvas pixel cap
  (the renderer refuses a size past 4096² on its own), nothing stops. A `data:`
  image of more than 1024² pixels by its bytes (`checkUrl`) becomes the
  placeholder under both.

### `icon:` images (Swift hosts, `WidgetHost.Options.assetResolver`)

A host that knows a built-in icon set passes `assetResolver: (String) -> String?`
(the Telegram app uses the twidget kit's `twa_icon_data_url`). Before validation,
`TrustedValidator` replaces `icon:<name>[?query]` tokens with the resolver's answer: a
whole JSON string value under any key except `data` and `text`, and `url(icon:…)` anywhere,
in JSON batches and in binary op fields alike (`IconExpansion.swift`). The resolver only
sees tokens of the grammar `icon:[A-Za-z0-9_.-]{0,96}(?:\?[A-Za-z0-9=&.%#_-]{0,400})?`;
the prefix is exactly `icon:` (`ICON:` stays a blocked URL), only the CSS function
name `url(` is ASCII case-insensitive.
An answer is used only if it is a `data:image/` URL of at most 64 KB made of
`[A-Za-z0-9-._~,/:=%;+]`, so it can never become a fetchable URL or break out of a
JSON string or a CSS `url()`. A batch expands at most 1024 tokens and 2 MB
(`icon-limit`, once per batch). A widget asks the resolver about at most 256
distinct tokens and keeps at most 2 MB of its answers; past that, new tokens stay
unresolved (`icon-limit`, once per widget) while the known ones keep expanding.
An answer must also be one of the profile's image types (the validator's `tables.mime`).
The validator never sees the answer: each token goes in as a stand-in, a 2×2 PNG `data:`
URL whose fragment carries a random nonce of this widget and the icon's index
(`#tw<nonce><index>`, about 140 characters), so the strict caps (an attribute 8 K, the
text budget, the image pixels) count what the widget sent, not the host's icon; after
validation `restore` puts the answer back in the validated ops, on the JSON, the binary
and the typed outputs and in stylesheets re-sent on a theme change. A stand-in the widget
writes itself (another nonce, an index it never had) stays the 2×2 image. At most 2 MB of
answers are restored per validated batch (`icon-limit`); past that the rest stay the
stand-in. Without a resolver `icon:` URLs are blocked like every other non-`data:` URL. Widgets can read the answers
back (computed style, canvas), so a resolver hands out public icons only.

## Sync queries

The sandbox posts `{ type: 'query', query }` and blocks with `Atomics.wait` on
the shared buffer (`SAB_STATE` word: 0 idle, 1 waiting, 2 done). The host
validates the query, relays it to the renderer with a `qid`, writes the
answer (UTF-8 JSON, optional binary payload for `getImageData`) into the buffer,
sets state 2 and `Atomics.notify`s. Timeout: 3 s (answer `{error:'timeout'}`).

| query | answer |
|---|---|
| `rect {id}` | DOMRect JSON |
| `rects {id}` | DOMRect[] |
| `box {id}` | `offsetWidth/Height/Top/Left, offsetParent (id), clientWidth/Height/Top/Left, scrollWidth/Height/Top/Left` |
| `cs {id, pseudo?, prop?}` | all computed properties as `{name: value}`, or one custom property |
| `svg {id, method, args}` | `getBBox`, `getCTM`, `getScreenCTM`, `getComputedTextLength`, `getTotalLength`, `getPointAtLength`, `getSubStringLength`, `getNumberOfChars` |
| `measureText {font, text}` | TextMetrics JSON |
| `imageData {id, x, y, w, h}` | `{width, height}` + binary RGBA |
| `canvasCall {id, method, args}` | `isPointInPath`, `isPointInStroke`, `getTransform`, `getLineDash`, `toDataURL` |
| `elementFromPoint {x, y}` | node id or 0 |
| `validity {id}` | ValidityState JSON + `message` |
| `matchMedia {query}` | `{matches}` — `prefers-color-scheme` is rewritten by the host against the chat theme before the renderer evaluates the query |
| `viewport` | `{innerWidth, innerHeight, dpr}` |
| `anims {id, subtree}` | `{list: [{kind, id, name, index, origin, currentTime, playbackRate, paused, duration, delay, iterations, direction, fill, easing}]}`: running CSS animations and transitions (`webAnimations`) |

Answers are cached in the sandbox until the next flush that sent ops. The
diagnostics panel shows query count, total latency and cache hits.

## Events (renderer → host → sandbox)

The renderer listens at document level (capture) and forwards an event only if
it is in the profile's list (`tables.events`: taps, pointer and mouse events,
`load`, `error`, `toggle`, the animation and transition events) and the
sandbox declared interest in its type (`listen`) or it is one of
`click load error toggle`. Move-type events are throttled to animation frames.
Keys, focus, input, touch, wheel and scroll events are never forwarded
(profile 1): the renderer cancels the keys' and the context menu's default
actions, never lets the focus into the widget and drops a trusted `click`
without a press (`detail` 0). The engine's own `dblclick` and `contextmenu`
are never forwarded either; since v0.1.2 the renderer sends its own, decided by
the gesture policy (`src/shared/gesture-policy.ts`): `dblclick` after the second
tap's `click`, for every double tap, and `contextmenu` at a hold (or a
right-button press) only when the press's claim gives the widget the hold
(`touch-action: none`).

```ts
interface WidgetEvent {
  type; targetId; relatedId?;
  clientX/Y, offsetX/Y, pageX/Y, movementX/Y, button, buttons, detail,
  pointerId, pointerType, isPrimary, pressure,
  modifiers (bit0 alt, bit1 ctrl, bit2 meta, bit3 shift),
  naturalWidth/Height (img load), open (details toggle)
}
```

### `defaultActions` on a click

A native renderer that does not perform a button's default action itself sends
`defaultActions: true` on the `click`, and the shim does it (a submit button
submits its form through `requestSubmit`). The WebView and Shadow DOM
renderers ignore it. (The native editor commands, `editorGeneration` /
`editorCommand` and `completeEditorCommand`, went with `<textarea>` in profile 1.)

### preventDefault

`preventDefault()` in the sandbox runs after the renderer has already
dispatched the event, so it cannot cancel native defaults. The renderer
cancels navigation and form submission unconditionally. For everything else the
sandbox reports keys `"<targetId>:<type>"` (or `"<id>:keydown:<key>"`) of events
it prevented (`pd` message); the renderer cancels those synchronously from the
next occurrence on ("learned preventDefault"). See `docs/decisions.md`.

### Touch gestures inside a scrolling host

On every primary `pointerdown` the renderer also posts
`{ type: 'gesture', wants, x, y }` — `x` is true when an ancestor of the target
has a computed `touch-action` that forbids horizontal panning (`none`, `pan-y`,
`pinch-zoom`, …), `y` when one forbids vertical panning (`none`, `pan-x`, …),
both when the sandbox has learned to prevent `pointerdown` / `touchstart` on that
target; `wants` is `y`. (Range inputs and scrollable boxes, which also claimed the gesture,
are outside profile 1.) Every host hands `x` and `y` to the client
(`onGestureClaim`, docs/sdk-v1.md §5.5): the client keeps swipe-to-reply off a
touch the widget takes horizontally and the list still on one it takes
vertically. Hosts whose renderer view sits inside a scrolling container that
cannot see into it also act on `wants` themselves: the Android host denies the
container's touch interception from `ACTION_DOWN` and releases it on
`wants: false` (or after 150 ms without an answer).

Since v0.2.2 the renderer also posts `{ type: 'release' }` when the primary
press ends (`pointerup` or `pointercancel`), whether or not the widget listens:
with the claim at `pointerdown` (its start) the host's haptic gate knows when a
finger is down on the widget and when it was lifted (docs/sdk-v1.md §2.6). Both
are the renderer's own messages from trusted input; nothing the widget does
makes one.

Since v0.1.2 the message is `{ type: 'gesture', wants, x, y, hold }`: the claim
is `claimOf` of the gesture policy over the target's and its ancestors' computed
`touch-action` (a learned `preventDefault` claims both axes), and `hold` is true
only under `touch-action: none`; taps and double taps are always the widget's. The
client drives its recognizers with the policy's `GestureArbiter` (docs/sdk-v1.md
§5.5) and cancels the widget's touch the platform's way when the bubble takes
the press.

Since v0.1.3 vertical drags are always the chat's: `y` and `wants` are always
false, `none` claims `x` and `hold`, `pan-x` claims nothing, a learned
`preventDefault` claims `x`, and every host reports `y` false whatever a
renderer sends (the Blitz and litehtml prototypes, which send only `wants`, now
claim nothing). The claim reads the widget's own `touch-action`: the renderer
core gives the engine each value widened to allow vertical panning (`none` →
`pan-y`, `pan-x` → `pan-x pan-y`, `src/renderer/touch-action.ts`) and keeps the
widget's in the inherited custom property `--tw-touch-action`; its `cs` query
answers `touch-action` with the widget's value.

### The renderer as the bubble (`arbitrate`, v0.1.2)

A host without recognizers over the widget (the web: `WidgetHost`
`bubbleGestures`) sends `{ type: 'arbitrate', bubble: true, touch?, rtl? }`.
The renderer then acts on the arbiter's verdicts itself: a press the bubble
takes (a hold, a swipe or a scroll over an element that does not claim it, a
right click) ends for the widget with a
`pointercancel` built from the press's last event, nothing more of that pointer
is forwarded (no `pointerup`, no compatibility `mouseup` / `click`), and the
host gets `{ type: 'bubble', action, gesture, x, y, edge }`. `touch`: the
mouse's primary button arbitrates as a finger, and the widget sees
`pointerType: 'touch'` for the press and no click after a drag or a hold, as on
a phone; `rtl`: the leading edge strip is the right one. `{ bubble: false }`
(the default) leaves the presses to the client.

## Host ⇄ worker envelope

Host → worker: `init {html, sab, config, sandboxBundle}`, `event {ev}`,
`frame {time}`, `viewport`, `resize {entries}`, `theme {theme, themeParams}`,
`safeArea {inset}` (the client's time over the widget, docs/sdk-v1.md §2.17: the shim's `safeArea` dispatch with
`{top, right, bottom, left, cornerLeft, cornerRight}`; not deferred under the CPU quota),
`visibility {visible}`, `bridge {event}` (a change of the widget's state, a request's answer;
see "Bridge calls and storage commits" below). (The profile-0
`bridge {event, data}` message, host → widget events for `sendMessage`, went
with v1.)

Host → renderer, besides ops and queries: `listen {types}`, `preventDefault
{keys}`, `requestFrame`, `anim {now, anims}` (`webAnimations`), `theme {theme:
{colorScheme, params, button}}` — the renderer core builds the CSS variables of
docs/sdk-v1.md §2.3–§2.4 from it (`themeVariables` in
`packages/core/src/shared/sdk-policy.ts`: names from a fixed list, values
checked against their grammar) and sets them inline and `!important` on the
widget's root, ignoring style ops of the widget that would set those names
there and re-applying them when the widget replaces the root's `style`
attribute — `safeArea {inset}` — the six `--tg-content-safe-area-*` variables
(`contentSafeAreaVariables`, docs/sdk-v1.md §2.17), set and protected the same
way, `0px` until the first one; hosts send it right after `theme` — and `frozen {reason, fatal}` — the runtime is gone
(or paused): the renderer keeps the DOM as it is, cancels the pending frame,
stops forwarding events, sets `data-tw-frozen="fatal"|"paused"` on `<html>`
(`pointer-events: none`, a slight grayscale when fatal) and shows a small badge
with `reason` (a renderer element, never in the widget's node table). The host
sends it from its death path; the iframe / WKWebView / WebView stays on screen
until the owner calls `destroy()`. And `policy {policy, paintReports?}`, the host's
`SandboxConfig.policy`, sent at load right after `safeArea`: under `strict` (also before
it arrives, and for any value but exactly `compat`) the renderer counts heavy boxes
(`packages/core/src/shared/paint-policy.ts`, decisions.md "Heavy boxes"), under `compat`
it counts nothing; `paintReports` (tooling: the Node harness) asks for
`paint {heavy, backdrop}` whenever the counts change, which a host ignores.

Renderer → host, besides events, results and sizes: `limit {code, count}`, past a paint
limit: `paint-limit` with the number of heavy boxes (more than 16: a blurred shadow, a
filter, a backdrop-filter, opacity below 1, or an animation of one of them) or
`backdrop-limit` with the number of boxes with backdrop-filter (more than 2). Once per
widget, and only under `strict`. The renderer has already drawn that frame without the
effects (no blurred shadow layer, filter, backdrop-filter or transparency; animations
paused) and takes no more ops. The host stops the widget like a counted cap: the
diagnostic `paint-limit` / `backdrop-limit` with the reason, then its death path with
`Paint limit exceeded: 17 boxes with shadows, filters or transparency (max 16)` or
`Paint limit exceeded: 3 boxes with backdrop-filter (max 2)` (`paintLimitMessage`);
`compat` ignores it, an unknown code or a count that is no number from 0 is a
`renderer-violation`. A client's own renderer sends the same message through the seam's
`limitExceeded(code, count)` (`Renderer.swift`, `Renderer.kt`, `renderer.h`).

Worker → host (payload untrusted unless noted): `ops`, `query`, `listen
{types}`, `pd {keys}`, `anim {anims, now}` (`now` from the glue), `raf`, `log {level, text}`, `diag {kind, detail}`,
`bridge {method, args}`, `storage {commit}`; trusted glue: `loaded`, `stats`, `dead {reason}`.
Diagnostic kinds added by the runtime limits: `timer-limit` (shim, once),
`timer-limit-enforced` (glue backstop — a shim bug), `cpu-throttled` (glue, at
most once per second).

Inside the worker the QuickJS context has one capability,
`__host(kind: string, arg: string): string | number | undefined`, with kinds
`ops query now timer clearTimer raf log diag listen pd anim bridge storage intl random stat`,
plus `__hostBinary()` to fetch the binary part of the last query answer and,
when the host enabled them, `__hostCanvas` (`canvasWords`) and `__hostOps`
(`binaryOps`). The bundle captures them and the glue deletes all of them from
the global object before widget code runs. QuickJS implements V8's stack-trace
API (`Error.prepareStackTrace`, `CallSite`); before any library code the shim
makes `CallSite.getFunction` (and `getThis`, where present) answer `undefined`, so
no stack frame hands widget code a shim closure. One more function exists only in
Swift debug builds: `__tw3d`, the 3D side channel of the twidget kit
(`Widget3DChannel`, `WidgetHost.Options.sideChannels`), which hands buffers to
a Metal backend past the validator. It is compiled out of release builds
(`DEBUG || TWIDGET_EXPERIMENTAL_3D`); see decisions.md, "3D side channel".

### User activation for privileged bridge calls

The `bridge` methods `openLink` and `openTelegramLink` (also what the shim
makes of `<a href>` taps and `window.open`) and `copyText` are
privileged (`PRIVILEGED_METHODS` in `sdk-policy.ts`; `ready` and `haptic` are
not): the glue forwards one only while the task of a trusted user gesture runs
on a visible widget, and one gesture unlocks one call. A `request` shares the
same gesture through its own gate: it looks at the task's gesture without
consuming it, and consumes it only when the request goes out as the tap's
(`foreground`); without a gesture it may go out as a background request where
the server's flags allow them (below). A gesture is one physical pointer press (`pointerdown`,
`mousedown`, `pointerup`, `mouseup`, `click`, `dblclick` of the same press).
A finger's or a pen's press is one from its `pointerup` on: its `pointerdown` may still
become the chat's scroll (it then ends in `pointercancel`), so the renderer does not mark
it `trusted` (HTML's activation-triggering input events; a mouse's press counts from its
`pointerdown`); nor is a mouse event of a finger or a pen still down (the `mousedown` a pen's
contact makes at once, its barrel button with the tip down). Neither is a release of a press begun
outside the widget `trusted`. Every host starts a new gesture at the renderer's `gesture` claim,
sent for every primary press before its events, and at every `pointerdown` event, trusted or not.
Keys are never gestures: widgets get no keyboard. A `click` or `dblclick`
starts a gesture only with a click count (`detail` ≥ 1); the click that Enter
or Space on a focused control produces has `detail` 0 and counts only as the
end of a pointer press already open. Only renderer `event` messages with
`trusted: true` count, on every host: the WebView/iframe renderer sets it from
the DOM event's `isTrusted`, and a renderer without a script-driven document
(Blitz, litehtml) sets it on every input event; a renderer behind the
`Renderer` seam that leaves it out gets no gestures. The host numbers gestures
and hands them to the engine with the event. Calls from timers, frames,
promises after the task, scripted `click()`/`dispatchEvent` or a hidden widget
are dropped with an `activation-blocked` diag (at most 20). On the web the
main-thread host decides: the host's `event` to the worker carries `gesture: n`
for trusted gestures, the worker tags `bridge` messages made in that task with
`gesture`, and the host accepts each issued gesture once, within 5 s. Native
glue gates in the engine (`activationEvent(json, gesture)`). See decisions.md,
"User activation".

### Bridge calls and storage commits (SDK v1)

`__host('bridge', JSON [method, args])`: `ready []`, `openLink [url]`,
`openTelegramLink [url]`, `haptic ['impact', style] | ['notification', type] |
['selection']`, `setResizeAnchor ['top' | 'bottom']` (v0.1.3: not privileged,
not an action; the host keeps the latest edge and reports it with the height,
and the shim sends only changes), `watchPublicData [on]` (one boolean; not
privileged, not an action, refused in a preview: the host keeps the latest value
and reports a change, the shim sends only changes), `request [id, data]` and
`copyText [text]` (the widget's state and requests). Every glue checks, in order: the method and its arguments
(`checkBridgeCall`: anything else is dropped with `bridge-rejected`, at most 20;
`request` takes an integer id 1–2147483647 and 1–64 bytes of UTF-8, `copyText`
1–256 UTF-16 code units, neither a lone surrogate), the request gate for
`request` (below), user activation for the two link methods and `copyText`
(above), the haptic gate for `haptic` (visible, a bucket of 5 refilled at 10 per
second; `haptic-limit` at most once per second). What passes reaches the client
as a `WidgetAction` (`onAction`) with the URL normalised; the deprecated
`onBridge` still gets the raw method and arguments. `copyText` goes to
`onCopyText(text)` and `request` to `onRequest({data, background})`, neither to
`onAction` / `onBridge`.

**Requests and the widget's state (docs/sdk-v1.md §2.13–§2.14).** The request gate
(`RequestGate`) reads only the server's flags the host passes in its context:
no `canQuery` → `disabled`; one in flight → `busy`; an unconsumed gesture on a
visible widget → `foreground` (consumed; `compat` counts every call as
pressed); no `backgroundQuery` → `no-activation`; hidden → `hidden`; under 5 s
since the last background request → `rate-limited`; else `background`; the host
calls the gate's `show` when the widget becomes visible (its next background
request may go out at once). Refusals are reported as `request-blocked` (at
most 20) and go back at once. An accepted request goes to the client as
`{data}`; a client without `onRequest` then refuses it (`unsupported`). The host
→ sandbox `bridge` dispatch (a task of kind `bridge`, deferred under the CPU
quota like `theme`, order kept) carries:

- `{type: 'data', chatData, publicData}`: the full state, the chat state and
  the shared state, after a change (the client's `setData`: the server's update,
  or a polled shared state);
  the shim fires `chatDataChanged`, then `publicDataChanged`, for each that
  differs, in that task;
- `{type: 'answer', id, result}`: the bot's result (an answer carries no state);
- `{type: 'answer', id, error, reason?}`: `error` one of `NOT_ALLOWED`, `BUSY`,
  `RATE_LIMITED` (refusals; `reason` = the gate's verdict or `unsupported`, for
  the error's message), `TIMEOUT`, `RATE_LIMITED`, `FAILED` (the client's
  failures) and `INVALID_ANSWER` (the answer's `result` over 1024 bytes or not
  text: `answer-rejected`).

Every string coming in is checked again in the glue, as the server checks its
text fields: the chat state (`chatData`), the shared state (`publicData`) and
`result` are each a text of up to 1024 bytes (UTF-8) without a NUL; a state that
fails is `null` (`data-rejected`), a copy's `chatData` is dropped. Request ids are the shim's; an answer to an id the
shim is not waiting for is ignored.

`__host('storage', JSON {local?, session?})`: the full areas of Web Storage a
task changed, sent once at the end of that task. The glue checks the commit
whole (only those two keys, objects of strings, 4096 bytes of UTF-8 per area;
an argument longer than 2 × 12 × 4096 + 64 characters is refused unparsed) and
hands each area to the client (`onStorageCommit(area, values)`), or drops it with
`storage-rejected` (at most 20).

The rules live in one policy with four copies, `packages/core/src/shared/sdk-policy.ts`,
`SdkPolicy.swift`, `SdkPolicy.kt` and `sdk_policy.cpp`, checked against one fixture,
`packages/core/tests/fixtures/sdk-policy.json`. The widget-facing side and the
client's duties are in docs/sdk-v1.md.

### `config` (SandboxConfig, same fields on iOS and Android)

| field | default | meaning |
|---|---|---|
| `memoryLimitBytes` | 8 MB | QuickJS heap limit; exceeding it kills the runtime |
| `cpuLimitMs` | 500 | longest continuous run of widget JS in one task (interrupt handler); exceeding it kills |
| `cpuQuotaMs` / `cpuQuotaWindowMs` | 300 / 1000 | JS time the widget may spend per sliding window (ten buckets); on the native hosts also the validator's CPU time for its messages (`stats.validatorMs`); beyond it timers, frames, resize/viewport/theme/bridge tasks are **deferred**, never killed; events always run with at least 50 ms |
| `cpuKillAfterMs` | 0 (off) | kill once throttling has lasted this long without a gap longer than one window |
| `mainThreadQuotaMs` | 100 | the host's main-thread work for the widget per `cpuQuotaWindowMs`: the wall time its layout queries block the renderer plus 25 µs per op and 2 µs per canvas command the glue forwards (`opsCostMs`); beyond it the same tasks are deferred, apart from the JS quota (which counts JS time only) |
| `maxTimers` | 64 | live `setTimeout` + `setInterval` slots; the shim throws `RangeError` past it, the glue refuses as a backstop; timer delays are clamped to 24 h |
| `visible` | true | initial visibility (see `visibility` below) |
| `policy` | `strict` | `strict`: privileged bridge calls need a user gesture, the counted caps above stop the widget (text values 32 K, document text 48 K, attributes 8 K, 10 000 ops per batch, 10 000 canvas commands per op, 400 CSS rules / 32 `@keyframes`, 2048² canvas pixels together), the shim caps canvas commands per canvas and task (10 000, `RangeError`) and script animations (64), visible text is budgeted (text nodes, visible and `data-*` / `aria-*` attributes, strings in CSS), `attr()` reads only budgeted attributes. `compat`: the limits before that hardening, for tests and tooling. Anything but exactly `compat` is `strict` |
| `features` | `[]` | opt-in wire forms (see "Features"); each glue drops the ones it does not implement |
| `viewport`, `theme` | | as before |
| `themeParams` | `{}` | the chat's palette, `#rrggbb` per Mini Apps key (docs/sdk-v1.md §2.3), checked by the glue; missing keys take the scheme's defaults in the shim |
| `locale` | `en-US` | the client's UI language (BCP 47, ≤ 35 characters), checked by the glue: `navigator.language` and the Intl default |
| `contentSafeArea` | all 0 | the client's time over a widget with no caption below it (docs/sdk-v1.md §2.17), CSS px `{top, right, bottom, left, cornerLeft, cornerRight}`; the shim checks it (an inset 0–64, a corner 0–200, else 0): `TelegramWidget.contentSafeAreaInset` |
| `profile` | 1 | the widget profile the glue enforces (`VERSION.json`'s `profile`); the shim reports `profile-mismatch` when its bundle's differs |
| `storage` | `{}` | the client's Web Storage preload `{local?, session?}`, checked by the glue (4096 bytes of UTF-8 per area; over it: `storage-rejected`, empty) |
| `chatData`, `publicData` | `null` | the chat state and the shared state, which only the bot writes, checked by the glue (text of 1–1024 bytes of UTF-8 without a NUL, else `null` and `data-rejected`); a copy's `chatData` is `null` |
| `isCopy` | `false` | a forwarded copy (the server's `is_copy`; the widget's `isForward`) |
| `canQuery` | `false` | the server's `can_query`: the shim refuses a request at once without it |
| `chatType` | `null` | `private`, `group`, `supergroup` or `channel`; `null` in every copy (`isCopy`) and in a preview |
| `viewer` | `null` | `{id, first_name?, last_name?, username?, is_premium?}` (`WebAppUser`): an id of 1 to 2^53 − 1, names of 1–64 UTF-16 code units, a username of 5–32 of `[A-Za-z0-9_]`, `is_premium` only when true; `null` without `canQuery` (and so in a preview) |

The policy that counts and decides lives in trusted glue only:
`packages/core/src/host/cpu-budget.ts`, `ios/.../CpuBudget.swift`, `android/.../CpuBudget.kt`
(one fixture, `packages/core/tests/fixtures/cpu-budget.json`). The shim gets `maxTimers`
through `boot` and the visibility signal; it enforces nothing the host relies
on. Before each task the glue asks `decide(kind, now)`: `{run, sliceMs}` sets
the interrupt deadline (always `cpuLimitMs`: what a task spends past the quota
is a debt of at most one more quota, `record(kind, cpuMs, now)` after the task),
`{run: false, retryAt}` puts the task in the deferred
queue (frames collapse into one, one fire per timer id, order kept) and a
single wake-up timer. A task that overruns its slice is reported as
`CPU limit exceeded: more than N ms of continuous execution (…)`. On the native
hosts the validator measures its thread's CPU time per call for the widget's
messages (ops, queries, `listen`, `preventDefault`, anim) and the engine records
it the same way when it arrives, `record('validator', ms, now)`
(`SandboxEngine.chargeValidator`; docs/decisions.md, "The validator's time
counts"); the web, which validates on its main thread in V8, leaves it to the
main-thread window. Diagnostics: `cpu-throttled` at most once per second;
`stats.throttledTasks`, `stats.cpuWindowMs` (the window plus the debt, the
validator's records included), `stats.validatorMs` (the validator's time charged
so far; native hosts).

### `visibility {visible}`

The host knows whether the bubble is on screen (`WidgetHost.setVisible`); the
sandbox does not. Hidden: the glue requests no frames from the renderer (one
is requested on return if the shim asked meanwhile; a frame already in flight
is dropped), timers fire at most once per second (new timers are armed with
`max(delay, 1000)`, due ones are batched into one wake-up; intervals do not
accumulate missed ticks), events pass through as usual. The shim receives
`visibility` through `__twDispatch` and flips `document.hidden` /
`document.visibilityState` and dispatches `visibilitychange` on the document,
like a background tab (no `pagehide`/`pageshow`).

### `morph {html}` (`morphUpdates`)

In-place document update, opt-in through the `morphUpdates` feature (it must be in
both the host's `config.features` and the engine's runtime features). The shim parses
`html` like `boot` does and patches the live DOM to match it: element children are
matched by `id`, then by tag in order; attributes (`class`, `style` included) and text
are updated in place, unmatched nodes are inserted or removed. Node identity is kept,
so CSS transitions run from the old values. Scripts in `html` are inert and existing
scripts are left alone; elements with `data-morph-keep` keep their children (content
the widget built itself). The changes leave the sandbox as an ordinary `ops` batch
and are validated like any other. Afterwards `widgetupdate` fires on `document`.
The dispatch returns `'1'` when applied; hosts fall back to a fresh boot otherwise
(Swift: `WidgetBubble.morph(html:completion:)`, `WidgetHost.morph`, `SandboxEngine.morph`).

## Shared buffer layout

```
Int32[0]  state (0 idle, 1 waiting, 2 done)   ← Atomics.wait / notify
Int32[1]  json byte length
Int32[2]  binary byte length
Int32[3]  reserved
bytes[16…]  json, then binary (16 MB total)
```

## Mapping to MCP Apps later

The bridge (`TelegramWidget.*` → `bridge` messages, docs/sdk-v1.md §3) is a thin method/args
envelope; wrapping it as JSON-RPC 2.0 over the same channel (`ui/initialize`,
`ui/notifications/*`, `tools/call`) is a renaming exercise — the sandbox
already cannot reach `postMessage` directly, so the host would be the JSON-RPC
endpoint.
