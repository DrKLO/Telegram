// Heavy boxes (docs/decisions.md, "Heavy boxes: 16, of them 2 with backdrop-filter"): the paint limit every renderer
// enforces, because only the renderer knows the cascade. A box is what the renderer builds in its layout tree: an
// element whose `display` is not `none` and which has no `display: none` ancestor (`display: contents` builds none, its
// children do), and a `::before` / `::after` whose `content` is not `none` / `normal` (and whose own display is not
// `none`); SVG elements alike; the widget's <html> and <body> count, the renderer's own nodes (the UA layer, the stopped
// badge, a host's wrappers) do not. A box is heavy when its computed style has
//
//   1. a `box-shadow` layer with a blur radius above 0 (inset alike; offsets and spread do not matter),
//   2. `filter` other than `none`,
//   3. `backdrop-filter` other than `none`,
//   4. `opacity` below 1 (0 too), except on a `<button>` that is disabled (the `disabled` attribute) or pressed
//      (`:active`): that is the button's own static dimming (the UA layer's client-styled button, or the author's),
//
// or 5. a running animation changes `filter`, `backdrop-filter` or `opacity`, or changes `box-shadow` through a
// blurred layer (a keyframe with one, or a missing first or last keyframe: the box's own value, unknown to the rule): a
// CSS animation from `animationstart` until `animationend` / `animationcancel`, a CSS transition from `transitionstart`
// until `transitionend` / `transitioncancel` (its keyframes are its two ends), a Web Animation while it is `running`;
// for the whole time of the animation, whatever the value at a given moment (an animation of a dimmed button's
// opacity counts like any other). An animation of shadows without blur (a
// ring, an inset fill) is no heavier than the ring itself. The value counts, not the picture: `opacity: 0.999` and
// `filter: brightness(1)` are heavy;
// `visibility`, clipping, being on screen, `transform`, `will-change` and engine layers do not matter.
//
// At most PAINT_HEAVY_BOXES heavy boxes at once, of which at most PAINT_BACKDROP_BOXES with `backdrop-filter` (its
// value, or an animation of it). The count is checked whenever the renderer is about to draw a frame: after each
// applied batch (a whole batch, never op by op), when an animation of rule 5 starts or ends, and when the cascade
// changes without ops (the theme, the bubble's width, :hover / :active). Past either number the renderer reports
// `paint-limit` (with the heavy count) or `backdrop-limit` (with the backdrop count) and the host stops the widget the
// usual way (`die`); the stopped widget is drawn without these effects. Under `compat` nothing is counted.
//
// Pure and side-effect free: the renderer core (src/renderer/core.ts) feeds a PaintCounter the computed values of the
// boxes it (re)computes and the animation events; a client's own renderer does the same and calls the seam's
// `limitExceeded(code, count)` (Renderer.swift, Renderer.kt, renderer.h). Checked against
// tests/fixtures/paint-policy.json, which the client teams' renderers implement against.

/** Heavy boxes a widget may have at once. */
export const PAINT_HEAVY_BOXES = 16;
/** Of them, boxes with `backdrop-filter` (it blurs whatever is under it: the most expensive effect per pixel). */
export const PAINT_BACKDROP_BOXES = 2;

export type PaintLimitCode = 'paint-limit' | 'backdrop-limit';
export const PAINT_LIMIT_CODES: readonly PaintLimitCode[] = ['paint-limit', 'backdrop-limit'];

/** The computed values a box is judged by (CSS serialisation; an empty string or a missing value is the initial one). */
export interface PaintStyle {
  display?: string;
  /** ::before / ::after only: its `content`. */
  content?: string;
  boxShadow?: string;
  filter?: string;
  backdropFilter?: string;
  opacity?: string | number;
  /**
   * The element is a `<button>` with the `disabled` attribute (`disabled`) or held down (`pressed`, `:active`): its
   * `opacity` is the button's own dimming and does not make it heavy. Absent for anything else, and ignored for a
   * pseudo-element (a button's ::before is a box of its own).
   */
  button?: 'disabled' | 'pressed';
}

/** What one box adds: to the heavy count, and to the backdrop-filter count. */
export interface BoxPaint {
  heavy: boolean;
  backdrop: boolean;
}

export interface PaintVerdict {
  code: PaintLimitCode;
  count: number;
}

const word = (v: string | undefined) => (v ?? '').trim().toLowerCase();
const isNone = (v: string | undefined) => { const w = word(v); return w === '' || w === 'none'; };

/** The comma-separated layers of a list value, commas inside parentheses kept. */
function layers(value: string): string[] {
  const out: string[] = [];
  let depth = 0, from = 0;
  for (let i = 0; i < value.length; i++) {
    const c = value[i];
    if (c === '(') depth++;
    else if (c === ')') depth = Math.max(0, depth - 1);
    else if (c === ',' && depth === 0) { out.push(value.slice(from, i)); from = i + 1; }
  }
  out.push(value.slice(from));
  return out.map((l) => l.trim()).filter(Boolean);
}

const LENGTH = /^[+-]?(\d+\.?\d*|\.\d+)(e[+-]?\d+)?[a-z%]*$/i;
const MATH = /^(calc|min|max|clamp)$/i;

/** The lengths of one shadow layer in order (offset x, offset y, blur, spread); NaN for a math function's. */
function layerLengths(layer: string): number[] {
  const out: number[] = [];
  let i = 0;
  while (i < layer.length) {
    if (/\s/.test(layer[i])) { i++; continue; }
    let j = i;
    while (j < layer.length && !/[\s(]/.test(layer[j])) j++;
    const token = layer.slice(i, j);
    if (layer[j] === '(') {
      // a function: a colour (rgb(), color-mix(), …) is skipped, a math one is a length of unknown size
      let depth = 0, k = j;
      for (; k < layer.length; k++) {
        if (layer[k] === '(') depth++;
        else if (layer[k] === ')' && --depth === 0) { k++; break; }
      }
      if (MATH.test(token)) out.push(NaN);
      i = k;
      continue;
    }
    if (LENGTH.test(token)) out.push(parseFloat(token));
    i = j;
  }
  return out;
}

/** Whether a layer's blur radius (its third length) is above 0; one of unknown size (calc()) counts as blurred. */
function layerBlurred(layer: string): boolean {
  const n = layerLengths(layer);
  return n.length >= 3 && !(n[2] <= 0);
}

/** Rule 1: whether a computed `box-shadow` has a layer with a blur radius above 0. */
export function boxShadowBlurred(value: string | undefined): boolean {
  if (isNone(value)) return false;
  return layers(value!).some(layerBlurred);
}

/** The layers of a `box-shadow` without the blurred ones ('none' when nothing is left): what a stopped widget keeps. */
export function boxShadowWithoutBlur(value: string | undefined): string {
  if (isNone(value)) return 'none';
  const kept = layers(value!).filter((l) => !layerBlurred(l));
  return kept.length ? kept.join(', ') : 'none';
}

/** Rule 4: `opacity` below 1 (a percentage is a share; a value that is no number is 1). */
function transparent(value: string | number | undefined): boolean {
  if (typeof value === 'number') return value < 1;
  const w = word(value);
  if (!w) return false;
  const n = w.endsWith('%') ? parseFloat(w) / 100 : parseFloat(w);
  return n < 1;
}

/** `display: none` on an element: neither it nor anything under it builds a box. */
export function hidesSubtree(display: string | undefined): boolean {
  return word(display) === 'none';
}

/**
 * The box an element (or, with `pseudo`, its ::before / ::after) builds and what it adds, from its computed values;
 * null when it builds none (`display: none` or `contents`; a pseudo-element whose content is none / normal). Rules 1–4
 * (a disabled or pressed `<button>`'s opacity aside); a running animation (rule 5) is PaintCounter's.
 */
export function boxPaint(style: PaintStyle, pseudo = false): BoxPaint | null {
  const display = word(style.display);
  if (display === 'none' || (!pseudo && display === 'contents')) return null;
  if (pseudo) {
    const content = word(style.content);
    if (content === '' || content === 'none' || content === 'normal') return null;
  }
  const backdrop = !isNone(style.backdropFilter);
  const dimmedButton = !pseudo && (style.button === 'disabled' || style.button === 'pressed');
  const heavy = backdrop || !isNone(style.filter) || (!dimmedButton && transparent(style.opacity)) || boxShadowBlurred(style.boxShadow);
  return { heavy, backdrop };
}

/** A property name as CSS writes it: camelCase (`boxShadow`, from getKeyframes) to kebab, the vendor prefix dropped. */
function cssName(name: string): string {
  return name.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()).toLowerCase().replace(/^-(webkit|moz|ms|o)-/, '');
}

/**
 * One keyframe as `getKeyframes()` gives it: property → value (CSS or camelCase names), with its `offset` (0–1;
 * `computedOffset` when the offset is implicit). `easing` and `composite` are no properties.
 */
export type PaintKeyframe = Record<string, unknown>;
const KEYFRAME_MEMBERS = new Set(['offset', 'computedoffset', 'easing', 'composite']);

/**
 * Rule 5: what an animation with these keyframes adds to its box while it runs; null when it changes none of
 * filter, backdrop-filter and opacity, and box-shadow only between layers without blur (every keyframe from the first
 * to the last sets it, none with a blurred layer; a value that is not a string is unknown, so blurred).
 */
export function animationPaint(keyframes: readonly PaintKeyframe[]): BoxPaint | null {
  let heavy = false, backdrop = false, shadow = false, shadowBlurred = false, shadowFirst = false, shadowLast = false;
  for (const k of keyframes) {
    const at = typeof k.computedOffset === 'number' ? k.computedOffset : typeof k.offset === 'number' ? k.offset : null;
    for (const p of Object.keys(k)) {
      if (KEYFRAME_MEMBERS.has(p.toLowerCase())) continue;
      const name = cssName(p);
      if (name === 'backdrop-filter') heavy = backdrop = true;
      else if (name === 'filter' || name === 'opacity') heavy = true;
      else if (name === 'box-shadow') {
        shadow = true;
        const v = k[p];
        if (typeof v !== 'string' || boxShadowBlurred(v)) shadowBlurred = true;
        if (at === 0) shadowFirst = true;
        if (at === 1) shadowLast = true;
      }
    }
  }
  if (shadow && (shadowBlurred || !shadowFirst || !shadowLast)) heavy = true;
  return heavy ? { heavy, backdrop } : null;
}

/** The reason a host stops the widget with (`onDead`, the diagnostics), the same on every platform. */
export function paintLimitMessage(code: PaintLimitCode, count: number): string {
  return code === 'backdrop-limit'
    ? `Paint limit exceeded: ${count} boxes with backdrop-filter (max ${PAINT_BACKDROP_BOXES})`
    : `Paint limit exceeded: ${count} boxes with shadows, filters or transparency (max ${PAINT_HEAVY_BOXES})`;
}

/** A renderer's `limit` report as the host takes it: a known code and a count (a whole number from 0); null otherwise. */
export function checkPaintLimit(code: unknown, count: unknown): PaintVerdict | null {
  if (!PAINT_LIMIT_CODES.includes(code as PaintLimitCode)) return null;
  if (typeof count !== 'number' || !Number.isFinite(count) || count < 0) return null;
  return { code: code as PaintLimitCode, count: Math.floor(count) };
}

interface Box {
  /** What its computed values add (rules 1–4); null while it builds no box. */
  paint: BoxPaint | null;
  /** Running animations of rule 5 by key: how many are running, and whether they change backdrop-filter. */
  anims: Map<string, { n: number; backdrop: boolean }> | null;
}

/**
 * The renderer's ledger of heavy boxes, by any key it gives a box (the core uses node id × 4 + 0 / 1 / 2 for the
 * element, ::before, ::after). It keeps the two counts current as boxes change and animations start and end, and gives
 * the verdict when the renderer is about to draw: the first one past a limit sticks (the widget stops there).
 */
export class PaintCounter<K = number | string> {
  private boxes = new Map<K, Box>();
  private heavyBoxes = 0;
  private backdropBoxes = 0;
  private stopped: PaintVerdict | null = null;
  /** The most heavy boxes there were at once, and with backdrop-filter. */
  peakHeavy = 0;
  peakBackdrop = 0;

  get heavy(): number { return this.heavyBoxes; }
  get backdrop(): number { return this.backdropBoxes; }

  private contribution(b: Box | undefined): { heavy: boolean; backdrop: boolean } {
    if (!b || !b.paint) return { heavy: false, backdrop: false };
    let heavy = b.paint.heavy, backdrop = b.paint.backdrop;
    if (b.anims) for (const a of b.anims.values()) { heavy = true; if (a.backdrop) backdrop = true; }
    return { heavy, backdrop };
  }

  /** Applies `change` to the box of `key` and keeps the counts in step. */
  private update(key: K, change: (b: Box) => void) {
    let b = this.boxes.get(key);
    const before = this.contribution(b);
    if (!b) b = { paint: null, anims: null };
    change(b);
    const after = this.contribution(b);
    this.heavyBoxes += Number(after.heavy) - Number(before.heavy);
    this.backdropBoxes += Number(after.backdrop) - Number(before.backdrop);
    if (b.paint || b.anims) this.boxes.set(key, b);
    else this.boxes.delete(key);
    if (this.heavyBoxes > this.peakHeavy) this.peakHeavy = this.heavyBoxes;
    if (this.backdropBoxes > this.peakBackdrop) this.peakBackdrop = this.backdropBoxes;
  }

  /** The box's computed values (`boxPaint`), or null: it builds no box now (display contents, a pseudo-element without content); its animations stay. */
  setBox(key: K, paint: BoxPaint | null) {
    if (!paint && !this.boxes.has(key)) return;
    this.update(key, (b) => { b.paint = paint ? { heavy: paint.heavy, backdrop: paint.backdrop } : null; });
  }

  /** The box is not rendered (`display: none` on it or an ancestor), or its node left the document: its CSS animations are cancelled with it. */
  removeBox(key: K) {
    if (this.boxes.has(key)) this.update(key, (b) => { b.paint = null; b.anims = null; });
  }

  /** An animation of rule 5 started on the box (`animationPaint` of its properties); `id` tells it from the box's others. */
  startAnimation(key: K, id: string, paint: BoxPaint) {
    this.update(key, (b) => {
      b.anims ??= new Map();
      const a = b.anims.get(id);
      if (a) { a.n++; a.backdrop ||= paint.backdrop; }
      else b.anims.set(id, { n: 1, backdrop: paint.backdrop });
    });
  }

  /** That animation ended or was cancelled (an end without a start, a cancel during its delay, changes nothing). */
  endAnimation(key: K, id: string) {
    const a = this.boxes.get(key)?.anims?.get(id);
    if (!a) return;
    this.update(key, (b) => {
      if (--a.n > 0) return;
      b.anims!.delete(id);
      if (!b.anims!.size) b.anims = null;
    });
  }

  /** Whether the box of `key` has running animations under `id`. */
  animating(key: K, id: string): boolean {
    return !!this.boxes.get(key)?.anims?.has(id);
  }

  /**
   * The verdict before a frame is drawn: null while both counts are within their limits; the first time one is past
   * its limit, `paint-limit` with the heavy count (checked first) or `backdrop-limit` with the backdrop count, and the
   * same verdict from then on.
   */
  check(): PaintVerdict | null {
    if (this.stopped) return this.stopped;
    if (this.heavyBoxes > PAINT_HEAVY_BOXES) this.stopped = { code: 'paint-limit', count: this.heavyBoxes };
    else if (this.backdropBoxes > PAINT_BACKDROP_BOXES) this.stopped = { code: 'backdrop-limit', count: this.backdropBoxes };
    return this.stopped;
  }
}
