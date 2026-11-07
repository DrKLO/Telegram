// Validator / sanitizer (security-critical, invariants 2–4).
//
// Input:  untrusted JSON produced inside QuickJS (mutation ops, queries, …).
// Output: RenderOps that are safe to apply to the renderer DOM as-is.
//
// The validator keeps its own model of the widget tree (ids, tags, parents) so
// that decisions never depend on anything the sandbox claims beyond the op
// itself. It is pure TypeScript with no DOM dependency: it can be unit-tested in
// Node and ported 1:1 to native hosts.

import {
  ANIMATION_VALUE_ATTRS, CANVAS_GRADIENTS, CANVAS_METHODS, CANVAS_OUT_OF_PROFILE, CANVAS_PATH_METHODS, CANVAS_PROPS, CANVAS_QUERY_METHODS,
  CSS_VALUE_ATTRS, DROPPED_TAGS, HTML_ATTRS, HTML_TAGS, METHODS, PROPS, REPLACED_TAGS, SECURITY_DROPPED_TAGS, SILENT_DROP_ATTRS,
  SVG_ALLOWED_ATTRS, SVG_ATTR_CASE, SVG_QUERY_METHODS, SVG_TAGS, URL_ATTRS,
} from './allowlist';
import {
  DEFAULT_ENV, TEXT_ATTRS, TEXT_ATTR_PREFIXES, cssStringText, isTextAttr, rewriteMediaQuery, sanitizeCssValue, sanitizeFontValue,
  sanitizeStyleAttribute, sanitizeStyleProperty, sanitizeStylesheet, type CssEnv, type Diag,
} from './css-sanitizer';
import { checkUrl, IMAGE_MIME, isFragment, PLACEHOLDER_IMAGE } from './url-policy';
import { DOCUMENT_ID, EVENT_TYPES, type AnimKeyframe, type AnimRecord, type CanvasCmd, type NodeId, type Query, type RenderOp } from '../shared/protocol';

const MAX_NODES = 50_000;
/** Text nodes, string properties and Path2D data; a chat widget has no use for more, and every byte is copied through the renderer. */
const MAX_TEXT = 32 * 1024;
const COMPAT_MAX_TEXT = 2 * 1024 * 1024;
/**
 * Per-document budget of complex-script text (UTF-16 units at or above U+0590:
 * Hebrew, Arabic, Indic, Thai, CJK, emoji, …) in text nodes. Shaping and line
 * breaking such text is what makes a renderer slow, so a widget gets this much
 * of it in its whole document, counted on create, data change and removal.
 */
const COMPLEX_TEXT_BUDGET = 8192;
const COMPLEX_CHURN_PER_SECOND = 1024;
const TEXT_BUDGET = 48 * 1024;

export function textWork(text: string): number {
  let count = 0;
  let run = false;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    if ((c >= 0x2e80 && c <= 0xa4cf) || (c >= 0xac00 && c <= 0xd7ff) || c >= 0xf900 || (c >= 0xd800 && c <= 0xdbff)) {
      if (run) count++;
      run = false;
      count++;
      if (c >= 0xd800 && c <= 0xdbff && i + 1 < text.length && text.charCodeAt(i + 1) >= 0xdc00 && text.charCodeAt(i + 1) <= 0xdfff) i++;
    } else if (c <= 0x20 || c === 0x2d || c === 0x3f || c === 0x2f || c === 0x200b) {
      if (run) count++;
      run = false;
    } else run = true;
  }
  if (run) count++;
  return Math.max(count, Math.ceil(text.length / 1024));
}

const SMALL_TEXT = 256;

export function chargesChurn(text: string): boolean {
  if (text.length > SMALL_TEXT) return true;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    if ((c >= 0x0590 && c < 0x2000) || (c >= 0x2e80 && c <= 0xd7ff) || (c >= 0xf900 && c <= 0xffef)) return true;
  }
  return false;
}

function complexUnits(text: string): number {
  let n = 0;
  for (let i = 0; i < text.length; i++) if (text.charCodeAt(i) >= 0x0590) n++;
  return n;
}
const KEYFRAME_MEMBERS = new Set(['offset', 'easing', 'composite', 'computed-offset']);
const OUT_OF_PROFILE = Symbol('out of profile');
// Strict caps (docs/decisions.md, "Strict limits from the start"): about twice what real widgets use; past one the widget stops.
/** An attribute (or inline style) value: the largest `data:` image a 4 KB message can carry fits. */
const MAX_ATTR = 8 * 1024;
const MAX_OPS_PER_BATCH = 10_000;
/**
 * Nodes in the document (elements, text, comments; detached ones the widget keeps included), compared once a batch is
 * applied: twice the corpora's largest widget (962). Below the 8 MB heap on every host since the shim hands a long flush
 * over in parts, so the author sees this reason everywhere, not a memory stop that comes at 2000 nodes on a 64-bit host
 * and 4000 on a 32-bit one.
 */
const MAX_DOCUMENT_NODES = 2000;
/**
 * Canvas commands in one `canvas` op (one canvas, one task): outside the projected 3D showcase no widget draws more than
 * 237, and the main-thread window (2 µs a command) already holds 4000 a frame to ~13 fps on every client the stress kit
 * measured (9 510 to ~5 fps); the 3D showcase's meshes stay under 2000 a frame.
 */
const MAX_CANVAS_CMDS = 4000;
const MAX_CSS_RULES = 400;
const MAX_KEYFRAMES = 32;
const MAX_LIVE_ANIMS = 64;
/**
 * A widget's canvases together, in bitmap pixels (16 MB as RGBA): a canvas over a whole bubble at 3× is ~2.3 M, the
 * corpora's largest 0.4 M. compat has none here; the renderer holds every widget to 4096 × 4096 (its own backstop).
 */
const MAX_CANVAS_PIXELS = 2048 * 2048;
// compat keeps the caps hosts had before (tests and tooling, the stress kit's measuring runs).
const COMPAT_MAX_ATTR = 4 * 1024 * 1024; // data: URLs can be large
const COMPAT_MAX_OPS_PER_BATCH = 200_000;
const COMPAT_MAX_CANVAS_CMDS = 500_000;
const COMPAT_MAX_LIVE_ANIMS = 1024;
const MAX_ID = 2 ** 31;
/** Renderer-side parent id meaning "detached, but keep the node". */
const LIMBO = 0;

const ATTR_NAME = /^[a-zA-Z_][\w.:\-]*$/;
const TAG_NAME = /^[a-zA-Z][\w.\-]*$/;
const EVENT_TYPE_SET = new Set<string>(EVENT_TYPES);
const SVG_ANIMATION_TAGS = new Set(['animate', 'animateTransform', 'animateMotion']);
const SVG_HREF_FRAGMENT_TAGS = new Set(['use', 'linearGradient', 'radialGradient', 'pattern', 'filter', 'textPath', 'mpath', 'feImage']);

interface NodeInfo {
  id: NodeId;
  kind: 'el' | 'text' | 'comment' | 'doc';
  /** Canonical tag as rendered ('' for non-elements). */
  tag: string;
  ns: 'html' | 'svg';
  /** True if this node (not counting ancestors) has a counterpart in the renderer. */
  rendered: boolean;
  /** <style> element: its text children are sanitized as a stylesheet instead of being rendered. */
  isStyle: boolean;
  adopted: boolean;
  parent: NodeId | null;
  children: NodeId[];
  data: string;
  /** Complex-script units this text node counts against the document budget. */
  complex?: number;
  /** A `<style>` element: the rules and `@keyframes` of its sanitized sheet, counted against the document's caps. */
  cssRules?: number;
  cssKeyframes?: number;
  textSlots?: Map<string, { length: number; complex: number }>;
  /** Renderer currently has this node attached under its parent. */
  attached: boolean;
}

/**
 * The allowlists, limits and placeholder this validator enforces, as data, so a
 * native host's own node model (Swift `NativeValidator`) applies the same rules
 * from one source of truth instead of a copy.
 */
export type ValidatorPolicy = 'compat' | 'strict';

/**
 * `stop`: a counted cap stops the widget (strict) instead of dropping the op with a diagnostic (compat);
 * `maxCssRules` / `maxKeyframes` 0 = no cap.
 */
export function policyLimits(policy: ValidatorPolicy) {
  return policy === 'strict'
    ? {
      maxText: MAX_TEXT, maxCanvasString: MAX_TEXT, maxAttr: MAX_ATTR, maxOps: MAX_OPS_PER_BATCH, maxCanvasCmds: MAX_CANVAS_CMDS,
      maxNodes: MAX_DOCUMENT_NODES, maxCssRules: MAX_CSS_RULES, maxKeyframes: MAX_KEYFRAMES, maxLiveAnims: MAX_LIVE_ANIMS, maxCanvasPixels: MAX_CANVAS_PIXELS,
      complexTextBudget: COMPLEX_TEXT_BUDGET, textBudget: TEXT_BUDGET, stop: true,
    }
    : {
      maxText: COMPAT_MAX_TEXT, maxCanvasString: COMPAT_MAX_ATTR, maxAttr: COMPAT_MAX_ATTR, maxOps: COMPAT_MAX_OPS_PER_BATCH,
      maxCanvasCmds: COMPAT_MAX_CANVAS_CMDS, maxNodes: 0, maxCssRules: 0, maxKeyframes: 0, maxLiveAnims: COMPAT_MAX_LIVE_ANIMS, maxCanvasPixels: 0,
      complexTextBudget: 0, textBudget: 0, stop: false,
    };
}

/**
 * Style rules and `@keyframes` blocks in a sanitized stylesheet: a rule inside `@media` / `@supports` / `@container` /
 * `@layer` counts, the keyframe selectors inside `@keyframes` do not.
 */
export function countCssRules(css: string): { rules: number; keyframes: number } {
  let rules = 0;
  let keyframes = 0;
  const stack: ('rule' | 'at' | 'kf' | 'frame')[] = [];
  let prelude = 0;
  for (let i = 0; i < css.length; i++) {
    const c = css.charCodeAt(i);
    if (c === 0x22 || c === 0x27) { // a string: skip to its end
      for (i++; i < css.length && css.charCodeAt(i) !== c; i++) if (css.charCodeAt(i) === 0x5c) i++;
    } else if (c === 0x5c) { // an escape (`.a\{b`, `@keyframes k\}`): the next character is no brace
      i++;
    } else if (c === 0x2f && css.charCodeAt(i + 1) === 0x2a) { // a comment
      const end = css.indexOf('*/', i + 2);
      i = end < 0 ? css.length : end + 1;
    } else if (c === 0x7b) { // {
      const head = css.slice(prelude, i).trim().toLowerCase();
      const top = stack[stack.length - 1];
      let kind: 'rule' | 'at' | 'kf' | 'frame';
      if (top === 'kf') kind = 'frame';
      else if (/^@(-webkit-)?keyframes\b/.test(head)) kind = 'kf';
      else if (head.startsWith('@')) kind = 'at';
      else kind = 'rule';
      if (kind === 'kf') keyframes++;
      else if (kind === 'rule') rules++;
      stack.push(kind);
      prelude = i + 1;
    } else if (c === 0x7d) { // }
      stack.pop();
      prelude = i + 1;
    } else if (c === 0x3b) prelude = i + 1; // ;
  }
  return { rules, keyframes };
}

export function validatorTables(policy: ValidatorPolicy = 'compat'): unknown {
  const limits = policyLimits(policy);
  return {
    html: [...HTML_TAGS], svg: [...SVG_TAGS.entries()], dropped: [...DROPPED_TAGS], attrs: [...HTML_ATTRS], svgAttrs: [...SVG_ALLOWED_ATTRS],
    replace: [...REPLACED_TAGS.entries()], securityDropped: [...SECURITY_DROPPED_TAGS],
    silent: [...SILENT_DROP_ATTRS], url: [...URL_ATTRS], css: [...CSS_VALUE_ATTRS], anim: [...ANIMATION_VALUE_ATTRS],
    animTags: [...SVG_ANIMATION_TAGS], fragTags: [...SVG_HREF_FRAGMENT_TAGS], svgCase: [...SVG_ATTR_CASE.entries()],
    props: Object.entries(PROPS).map(([k, v]) => [k, v.type, v.tags ? [...v.tags] : null]), methods: Object.entries(METHODS),
    events: [...EVENT_TYPE_SET], mime: [...IMAGE_MIME], placeholder: PLACEHOLDER_IMAGE,
    limits: [MAX_NODES, limits.maxText, limits.maxAttr, limits.maxOps, limits.maxCanvasCmds, LIMBO],
    complexTextBudget: limits.complexTextBudget,
    textBudget: limits.textBudget,
    documentNodes: limits.maxNodes, cssRules: limits.maxCssRules, keyframes: limits.maxKeyframes, liveAnims: limits.maxLiveAnims, canvasPixels: limits.maxCanvasPixels, stop: limits.stop,
    textAttrs: TEXT_ATTRS, textAttrPrefixes: TEXT_ATTR_PREFIXES,
  };
}

function isObj(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}
function isId(v: unknown): v is NodeId {
  return typeof v === 'number' && Number.isInteger(v) && v > DOCUMENT_ID && v < MAX_ID;
}
function isStr(v: unknown, max: number): v is string {
  return typeof v === 'string' && v.length <= max;
}
function isNum(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

export class Validator {
  private nodes = new Map<NodeId, NodeInfo>();
  private adopted = { html: false, head: false, body: false };
  private dirtyStyles = new Set<NodeId>();
  private complexText = 0;
  private totalText = 0;
  private cssRules = 0;
  private cssKeyframes = 0;
  /** Each canvas's bitmap size (width, height), and their pixels together: the strict cap is on the sum. */
  private canvasSizes = new Map<NodeId, [number, number]>();
  private canvasPixels = 0;
  private stopReason: string | null = null;
  private churnTokens = COMPLEX_TEXT_BUDGET;
  private churnAt: number | null = null;
  private clock: () => number = () => Date.now() / 1000;

  setClock(clock: () => number) {
    this.clock = clock;
  }

  /**
   * Until the host says the widget has loaded (`markLoaded`), inserted text is not charged to the churn bucket: building
   * the document in the loading tasks, in any number of batches, is bounded by the document's caps alone, so a load never
   * drops text they allow (a 48 K document of paragraphs lost them past ~45 K, silently, on every host).
   */
  private loading = true;

  /** The host's word that the loading tasks are over (their ops are in): from now on text changes are rate-limited. */
  markLoaded() {
    this.loading = false;
  }

  private takeChurn(words: number): boolean {
    if (this.loading) return true;
    const now = this.clock();
    if (this.churnAt !== null) this.churnTokens = Math.min(COMPLEX_TEXT_BUDGET, this.churnTokens + Math.max(0, now - this.churnAt) * COMPLEX_CHURN_PER_SECOND);
    this.churnAt = now;
    if (this.churnTokens < words) return false;
    this.churnTokens -= words;
    return true;
  }

  private overChurn(): boolean {
    this.diag('text-churn', `text changes over ${COMPLEX_CHURN_PER_SECOND} units per second`);
    return false;
  }

  private policyName: ValidatorPolicy = 'compat';
  private limits = policyLimits('compat');

  get policy(): ValidatorPolicy { return this.policyName; }

  /**
   * Set once a counted cap was passed under strict: the reason the host stops the widget with (`die`), as it does for
   * memory or CPU. From then on every batch is refused whole, so the renderer keeps the last render that fit.
   */
  get stopped(): string | null { return this.stopReason; }

  /** A cap passed: strict stops the widget (diagnostic `limit`, the host acts on it); compat only rejects the op. */
  private stop(reason: string): false {
    if (!this.limits.stop) return false;
    if (this.stopReason === null) {
      this.stopReason = reason;
      this.diag('limit', reason);
    }
    return false;
  }

  private tooLongText(length: number): false {
    return this.stop(`Text limit exceeded: a text value of ${length} characters (max ${this.limits.maxText})`);
  }

  private tooLongAttr(length: number): false {
    return this.stop(`Attribute limit exceeded: a value of ${length} characters (max ${this.limits.maxAttr})`);
  }

  /**
   * The document's counted totals (its text, CSS rules, `@keyframes`) after a whole batch, not op by op: a subtree
   * replaced in one task (`innerHTML`, `replaceChildren`) or two sheets rewritten in one task pass through a sum of the
   * old and the new that the document never holds. False when a total is over its cap (the widget stops).
   */
  private checkTotals(): boolean {
    if (this.limits.maxNodes && this.nodes.size - 1 > this.limits.maxNodes) {
      return this.stop(`Node limit exceeded: ${this.nodes.size - 1} nodes in the document (max ${this.limits.maxNodes})`);
    }
    if (this.limits.textBudget && this.totalText > this.limits.textBudget) {
      return this.stop(`Text limit exceeded: the document's text is over ${this.limits.textBudget} characters`);
    }
    if (this.limits.maxCssRules && this.cssRules > this.limits.maxCssRules) {
      return this.stop(`CSS limit exceeded: ${this.cssRules} rules in the document's stylesheets (max ${this.limits.maxCssRules})`);
    }
    if (this.limits.maxKeyframes && this.cssKeyframes > this.limits.maxKeyframes) {
      return this.stop(`CSS limit exceeded: ${this.cssKeyframes} @keyframes in the document's stylesheets (max ${this.limits.maxKeyframes})`);
    }
    return true;
  }

  /**
   * Canvas `id` takes the bitmap `w` × `h` (an attribute, its removal, the shim's `r` command, a new canvas's 300 × 150);
   * false when the widget's canvases would hold more pixels together than strict allows (the widget stops).
   */
  private sizeCanvas(id: NodeId, w: number, h: number): boolean {
    const old = this.canvasSizes.get(id);
    const total = this.canvasPixels - (old ? old[0] * old[1] : 0) + w * h;
    // NaN-safe: no size may slip past as a comparison that is false both ways
    if (this.limits.maxCanvasPixels && !(total <= this.limits.maxCanvasPixels)) {
      return this.stop(`Canvas limit exceeded: canvases of ${total} pixels together (max ${this.limits.maxCanvasPixels})`);
    }
    this.canvasSizes.set(id, [w, h]);
    this.canvasPixels = total;
    return true;
  }

  /** A canvas's `width` or `height` attribute set to `value` (null: removed), by HTML's rules for non-negative integers. */
  private sizeCanvasAttr(node: NodeInfo, name: string, value: string | null): boolean {
    const lower = name.toLowerCase();
    if (node.tag !== 'canvas' || node.ns !== 'html' || (lower !== 'width' && lower !== 'height')) return true;
    const parsed = /^[\t\n\f\r ]*\+?(\d+)/.exec(value ?? '');
    const fallback = lower === 'width' ? 300 : 150;
    // past 2147483647 it is no unsigned long and the canvas takes the default, as the browser does (0 × 1e310 is NaN)
    const n = parsed && Number(parsed[1]) <= 2147483647 ? Number(parsed[1]) : fallback;
    const [w, h] = this.canvasSizes.get(node.id) ?? [300, 150];
    return this.sizeCanvas(node.id, lower === 'width' ? n : w, lower === 'height' ? n : h);
  }

  setPolicy(policy: ValidatorPolicy) {
    this.policyName = policy === 'compat' ? 'compat' : 'strict';
    this.limits = policyLimits(this.policyName);
    this.env = { ...this.env, strict: this.policyName === 'strict' };
  }
  private out: RenderOp[] = [];
  private diag: Diag;
  private env: CssEnv;

  constructor(diag: Diag, env: CssEnv = DEFAULT_ENV) {
    this.diag = diag;
    this.env = { ...env };
    this.nodes.set(DOCUMENT_ID, {
      id: DOCUMENT_ID, kind: 'doc', tag: '', ns: 'html', rendered: false, isStyle: false, adopted: false,
      parent: null, children: [], data: '', attached: false,
    });
  }

  get nodeCount(): number {
    return this.nodes.size;
  }

  /** The host theme changed: re-evaluate every stylesheet against it. */
  setTheme(colorScheme: CssEnv['colorScheme']): RenderOp[] {
    this.env = { ...this.env, colorScheme };
    const out: RenderOp[] = [];
    for (const n of this.nodes.values()) {
      if (n.kind === 'el' && n.isStyle && n.rendered) out.push({ op: 'css', id: n.id, text: this.styleText(n) });
    }
    return this.checkTotals() ? out : [];
  }

  private styleText(style: NodeInfo): string {
    const css = style.children.map((c) => this.nodes.get(c)?.data ?? '').join('');
    const text = sanitizeStylesheet(css, this.diag, this.env);
    // The raw text is charged as text nodes; the strings it draws are charged decoded.
    if (this.budgeted && !this.updateTextSlot(style, 'sheet', cssStringText(text))) {
      this.releaseTextSlot(style, 'sheet');
      return '';
    }
    // Counted here, compared with the caps by checkTotals once every changed sheet of the batch is counted.
    if (this.limits.maxCssRules || this.limits.maxKeyframes) {
      const { rules, keyframes } = countCssRules(text);
      this.cssRules += rules - (style.cssRules ?? 0);
      this.cssKeyframes += keyframes - (style.cssKeyframes ?? 0);
      style.cssRules = rules;
      style.cssKeyframes = keyframes;
    }
    return text;
  }

  private get budgeted(): boolean {
    return this.limits.textBudget > 0 || this.limits.complexTextBudget > 0;
  }

  /** The text an attribute value can put on screen under the strict budgets, or null if none. */
  private attrText(name: string, value: string): string | null {
    if (!this.budgeted) return null;
    if (name === 'style') return cssStringText(value);
    return isTextAttr(name) ? value : null;
  }

  /** Media query strings from matchMedia(): prefers-color-scheme is answered from the host theme. */
  rewriteMediaQuery(query: string): string {
    return rewriteMediaQuery(query, this.env);
  }

  /** Would a `canvas` op for `id` be accepted (a known `<canvas>` element)? Native hosts check this before validating canvas words themselves. */
  isCanvas(id: unknown): boolean {
    if (!isId(id)) return false;
    const n = this.nodes.get(id);
    return !!n && n.kind === 'el' && n.tag === 'canvas';
  }

  /**
   * A native host that keeps the node model itself (Swift `NativeValidator`) answers
   * "which rendered element is `id`" for the checks still done here (queries,
   * Web Animations): the tag, or null.
   */
  private nodeLookup: ((id: number) => string | null) | null = null;
  setNodeLookup(lookup: ((id: number) => string | null) | null) {
    this.nodeLookup = lookup;
  }

  // Stateless pieces for a native node model: the same sanitizers with this
  // validator's diagnostics and colour scheme.
  get cssEnv(): CssEnv { return this.env; }
  get diagnostics(): Diag { return this.diag; }

  /** Validated canvas commands, with the same diagnostics as a `canvas` op. */
  canvasCommands(cmds: unknown): CanvasCmd[] {
    if (!Array.isArray(cmds) || this.stopReason !== null) return [];
    if (cmds.length > this.limits.maxCanvasCmds) {
      this.tooManyCanvasCmds(cmds.length);
      return [];
    }
    const out: CanvasCmd[] = [];
    for (const cmd of cmds) {
      const clean = this.canvasCmd(cmd);
      if (this.stopReason !== null) return [];
      if (clean === OUT_OF_PROFILE) continue;
      if (clean) out.push(clean);
      else this.diag('protocol-violation', 'rejected canvas cmd ' + safeJson(cmd));
    }
    return out;
  }

  /** Does `id` name an element the renderer knows about? (Used to validate queries.) */
  private renderedElement(id: unknown): NodeInfo | null {
    if (!isId(id)) return null;
    if (this.nodeLookup) {
      const tag = this.nodeLookup(id);
      return tag ? { id, kind: 'el', tag, ns: 'html', rendered: true, isStyle: false, adopted: false, parent: null, children: [], data: '', attached: true } : null;
    }
    const n = this.nodes.get(id);
    return n && n.kind === 'el' && n.rendered ? n : null;
  }

  // ───────────────────────── ops ─────────────────────────

  processOps(batch: unknown): RenderOp[] {
    this.out = [];
    if (this.stopReason !== null) return [];
    if (Array.isArray(batch) && batch.length > this.limits.maxOps && this.limits.stop) {
      this.stop(`Ops limit exceeded: ${batch.length} operations in one batch (max ${this.limits.maxOps})`);
      return [];
    }
    if (!Array.isArray(batch) || batch.length > this.limits.maxOps) {
      this.diag('protocol-violation', 'ops batch is not an array or too large');
      return [];
    }
    for (const op of batch) {
      try {
        if (!isObj(op) || !this.applyOp(op)) {
          if (this.stopReason !== null) break;
          this.diag('protocol-violation', 'rejected op ' + safeJson(op));
        }
      } catch (e) {
        this.diag('protocol-violation', 'validator error: ' + (e as Error).message);
      }
      if (this.stopReason !== null) break;
    }
    for (const id of this.dirtyStyles) {
      if (this.stopReason !== null) break;
      const style = this.nodes.get(id);
      if (style) this.out.push({ op: 'css', id, text: this.styleText(style) });
    }
    this.dirtyStyles.clear();
    if (this.stopReason === null) this.checkTotals();
    const out = this.out;
    this.out = [];
    // A stopped widget's last batch is refused whole: the renderer keeps the last render that fit.
    return this.stopReason !== null ? [] : out;
  }

  private applyOp(op: Record<string, unknown>): boolean {
    switch (op.op) {
      case 'el': return this.opEl(op);
      case 'text': case 'comment': return this.opCharData(op);
      case 'attr': return this.opAttr(op);
      case 'rattr': return this.opRemoveAttr(op);
      case 'data': return this.opData(op);
      case 'ins': return this.opInsert(op);
      case 'rm': return this.opRemove(op);
      case 'style': return this.opStyle(op);
      case 'prop': return this.opProp(op);
      case 'call': return this.opCall(op);
      case 'canvas': return this.opCanvas(op);
      default: return false;
    }
  }

  private newNode(id: NodeId, partial: Partial<NodeInfo>): NodeInfo | null {
    if (this.nodes.has(id) || this.nodes.size >= MAX_NODES) return null;
    const n: NodeInfo = {
      id, kind: 'el', tag: '', ns: 'html', rendered: true, isStyle: false, adopted: false, parent: null,
      children: [], data: '', attached: false, ...partial,
    };
    this.nodes.set(id, n);
    return n;
  }

  private opEl(op: Record<string, unknown>): boolean {
    const { id, tag, ns, attrs } = op;
    if (!isId(id) || !isStr(tag, 64) || !TAG_NAME.test(tag)) return false;
    if (ns !== undefined && ns !== 'svg') return false;
    if (attrs !== undefined && !isObj(attrs)) return false;
    const lower = tag.toLowerCase();

    // Decide what (if anything) this element becomes in the renderer.
    let outTag: string | null;
    let extra: [string, string][] = [];
    const isStyle = lower === 'style';
    if (ns === 'svg') {
      if (isStyle) outTag = 'style';
      else if (DROPPED_TAGS.has(lower) && lower !== 'title') outTag = null;
      else outTag = SVG_TAGS.get(lower) ?? null;
      if (outTag === null && !DROPPED_TAGS.has(lower)) this.diag('tag-dropped', `<svg:${tag}>`);
    } else if (DROPPED_TAGS.has(lower)) {
      outTag = null;
      if (!SECURITY_DROPPED_TAGS.has(lower)) this.diag('profile', `<${lower}> dropped`);
      else if (lower !== 'script' && lower !== 'title' && lower !== 'meta') this.diag('tag-dropped', `<${lower}>`);
    } else if (REPLACED_TAGS.has(lower)) {
      // Outside the profile but its content stays (docs/profile-v1.md): <center>, <form>, <dialog> as a div, <ruby>, <label>… as a span.
      outTag = REPLACED_TAGS.get(lower)!;
      extra = [['data-tg-tag', lower]];
      this.diag('profile', `<${lower}> rendered as <${outTag}>`);
    } else if (isStyle || HTML_TAGS.has(lower)) {
      outTag = lower;
    } else {
      // Unknown element (custom element, typo, MathML…): render as an inert span.
      outTag = 'span';
      extra = [['data-tg-tag', lower]];
      this.diag('tag-replaced', `<${lower}> rendered as <span>`);
    }

    let adopt: 'html' | 'head' | 'body' | undefined;
    if (ns !== 'svg' && (lower === 'html' || lower === 'head' || lower === 'body')) {
      if (this.adopted[lower]) {
        outTag = 'div';
        extra = [['data-tg-tag', lower]];
      } else {
        this.adopted[lower] = true;
        adopt = lower;
      }
    }

    const node = this.newNode(id, {
      kind: 'el', tag: outTag ?? '', ns: ns === 'svg' ? 'svg' : 'html', rendered: outTag !== null, isStyle,
      adopted: adopt !== undefined,
    });
    if (!node) return false;
    if (outTag === null) return true; // tracked, never rendered

    const outAttrs: [string, string][] = [...extra];
    // a canvas is born 300 × 150
    if (outTag === 'canvas' && node.ns === 'html' && !this.sizeCanvas(id, 300, 150)) return false;
    if (attrs) {
      for (const [name, value] of Object.entries(attrs)) {
        if (typeof value !== 'string') continue;
        if (value.length > this.limits.maxAttr) {
          if (this.limits.stop) return this.tooLongAttr(value.length);
          continue;
        }
        if (!this.sizeCanvasAttr(node, name, value)) return false;
        for (const pair of this.sanitizeAttr(node, name, value)) {
          const text = this.attrText(pair[0], pair[1]);
          if (text === null || this.updateTextSlot(node, 'attr:' + pair[0], text)) outAttrs.push(pair);
        }
      }
    }
    this.out.push({ op: 'el', id, tag: outTag, ...(node.ns === 'svg' ? { ns: 'svg' as const } : {}), attrs: outAttrs, ...(adopt ? { adopt } : {}) });
    return true;
  }

  private opCharData(op: Record<string, unknown>): boolean {
    const { id, data } = op;
    if (!isId(id) || typeof data !== 'string') return false;
    if (data.length > this.limits.maxText) return this.tooLongText(data.length);
    if (this.nodes.has(id) || this.nodes.size >= MAX_NODES) return false;
    const kind = op.op === 'text' ? 'text' : 'comment';
    const complex = kind === 'text' ? complexUnits(data) : 0;
    if (complex && this.limits.complexTextBudget && this.complexText + complex > this.limits.complexTextBudget) {
      this.diag('text-budget', `document complex-script text over ${this.limits.complexTextBudget} units`);
      return false;
    }
    if (this.limits.textBudget && chargesChurn(data) && !this.takeChurn(textWork(data))) return this.overChurn();
    // Comments are tracked for tree consistency but never rendered.
    const node = this.newNode(id, { kind, data, rendered: kind === 'text', complex });
    if (!node) return false;
    this.complexText += complex;
    this.totalText += data.length;
    if (kind === 'text') this.out.push({ op: 'text', id, data });
    return true;
  }

  private opData(op: Record<string, unknown>): boolean {
    const { id, data } = op;
    if (!isId(id) || typeof data !== 'string') return false;
    if (data.length > this.limits.maxText) return this.tooLongText(data.length);
    const node = this.nodes.get(id);
    if (!node || node.kind === 'el' || node.kind === 'doc') return false;
    const textGrowth = data.length - node.data.length;
    const complex = node.kind === 'text' ? complexUnits(data) : 0;
    const growth = complex - (node.complex ?? 0);
    if (growth > 0 && this.limits.complexTextBudget && this.complexText + growth > this.limits.complexTextBudget) {
      this.diag('text-budget', `document complex-script text over ${this.limits.complexTextBudget} units`);
      return false;
    }
    if (this.limits.textBudget && chargesChurn(data) && !this.takeChurn(textWork(data))) return this.overChurn();
    this.complexText += growth;
    this.totalText += textGrowth;
    node.complex = complex;
    node.data = data;
    const parent = node.parent !== null ? this.nodes.get(node.parent) : null;
    if (parent?.isStyle) this.dirtyStyles.add(parent.id);
    else if (node.rendered) this.out.push({ op: 'data', id, data });
    return true;
  }

  private opInsert(op: Record<string, unknown>): boolean {
    const { parent, id, before } = op;
    if (!isId(id) || !(isId(parent) || parent === DOCUMENT_ID)) return false;
    if (before !== null && !isId(before)) return false;
    const node = this.nodes.get(id);
    const p = this.nodes.get(parent as NodeId);
    if (!node || !p || p.kind === 'text' || p.kind === 'comment') return false;
    // Reject cycles: parent must not be the node itself or one of its descendants.
    for (let a: NodeInfo | undefined = p; a; a = a.parent !== null ? this.nodes.get(a.parent) : undefined) {
      if (a === node) return false;
    }
    let ref: NodeInfo | null = null;
    if (before !== null) {
      ref = this.nodes.get(before as NodeId) ?? null;
      if (!ref || ref.parent !== p.id || ref === node) return false;
    }

    // Update the model.
    this.unlink(node);
    node.parent = p.id;
    if (ref) p.children.splice(p.children.indexOf(ref.id), 0, id);
    else p.children.push(id);
    if (p.isStyle) this.dirtyStyles.add(p.id);

    // Decide whether the renderer should attach it.
    const renderHere = node.rendered && !node.adopted && p.rendered && !p.isStyle && p.kind === 'el';
    if (!renderHere) {
      if (p.isStyle && node.rendered && node.kind !== 'el') {
        // Stylesheet text only ever reaches the renderer sanitized (`css` op):
        // forget the raw text node there. The model keeps its data.
        this.out.push({ op: 'rm', id });
        node.rendered = false;
      } else if (node.attached) {
        this.out.push({ op: 'ins', parent: LIMBO, id, before: null });
      }
      node.attached = false;
      return true;
    }
    // The reference node must itself be attached in the renderer; otherwise use
    // the next sibling that is.
    let refId: NodeId | null = null;
    const idx = p.children.indexOf(id);
    for (let i = idx + 1; i < p.children.length; i++) {
      const sib = this.nodes.get(p.children[i]);
      if (sib && sib.attached) {
        refId = sib.id;
        break;
      }
    }
    this.out.push({ op: 'ins', parent: p.id, id, before: refId });
    node.attached = true;
    return true;
  }

  private unlink(node: NodeInfo): void {
    if (node.parent === null) return;
    const old = this.nodes.get(node.parent);
    if (old) {
      const i = old.children.indexOf(node.id);
      if (i >= 0) old.children.splice(i, 1);
      if (old.isStyle) this.dirtyStyles.add(old.id);
    }
    node.parent = null;
  }

  private opRemove(op: Record<string, unknown>): boolean {
    const { id } = op;
    if (!isId(id)) return false;
    const node = this.nodes.get(id);
    if (!node) return false;
    if (node.adopted) {
      // <html>/<head>/<body> cannot be removed from the renderer; empty them instead.
      for (const c of [...node.children]) this.opRemove({ id: c });
      return true;
    }
    this.unlink(node);
    if (node.rendered) this.out.push({ op: 'rm', id });
    // Forget the whole subtree (the renderer does the same).
    const stack = [node];
    while (stack.length) {
      const n = stack.pop()!;
      this.complexText -= n.complex ?? 0;
      this.totalText -= n.data.length;
      this.cssRules -= n.cssRules ?? 0;
      this.cssKeyframes -= n.cssKeyframes ?? 0;
      const size = this.canvasSizes.get(n.id);
      if (size) { this.canvasPixels -= size[0] * size[1]; this.canvasSizes.delete(n.id); }
      for (const charge of n.textSlots?.values() ?? []) {
        this.totalText -= charge.length;
        this.complexText -= charge.complex;
      }
      this.nodes.delete(n.id);
      this.dirtyStyles.delete(n.id);
      for (const c of n.children) {
        const child = this.nodes.get(c);
        if (child) {
          // Children that were never attached under this parent still exist in the renderer map.
          if (child.rendered && !child.attached) this.out.push({ op: 'rm', id: child.id });
          stack.push(child);
        }
      }
    }
    return true;
  }

  private opAttr(op: Record<string, unknown>): boolean {
    const { id, name, value } = op;
    if (!isId(id) || !isStr(name, 128) || typeof value !== 'string') return false;
    if (value.length > this.limits.maxAttr) return this.tooLongAttr(value.length);
    const node = this.nodes.get(id);
    if (!node || node.kind !== 'el') return false;
    if (!node.rendered) return true;
    if (!this.sizeCanvasAttr(node, name, value)) return false;
    const pairs = this.sanitizeAttr(node, name, value);
    if (pairs.length === 0) {
      // The previous (possibly valid) value must not survive a rejected update.
      const canon = this.canonAttrName(node, name);
      if (canon) {
        this.releaseTextSlot(node, 'attr:' + canon);
        this.out.push({ op: 'rattr', id, name: canon });
      }
      return true;
    }
    for (const [n, v] of pairs) {
      const text = this.attrText(n, v);
      if (text !== null && !this.updateTextSlot(node, 'attr:' + n, text)) return false;
      this.out.push({ op: 'attr', id, name: n, value: v });
    }
    return true;
  }

  private opRemoveAttr(op: Record<string, unknown>): boolean {
    const { id, name } = op;
    if (!isId(id) || !isStr(name, 128)) return false;
    const node = this.nodes.get(id);
    if (!node || node.kind !== 'el') return false;
    if (!node.rendered) return true;
    const canon = this.canonAttrName(node, name);
    if (!canon) return true;
    if (!this.sizeCanvasAttr(node, canon, null)) return false;
    this.releaseTextSlot(node, 'attr:' + canon);
    this.out.push({ op: 'rattr', id, name: canon });
    if (node.tag === 'a' && (canon === 'href' || canon === 'xlink:href')) this.out.push({ op: 'rattr', id, name: 'data-tg-link' });
    return true;
  }

  private canonAttrName(node: NodeInfo, name: string): string | null {
    if (!ATTR_NAME.test(name)) return null;
    const lower = name.toLowerCase();
    if (lower.startsWith('on')) return null;
    // SVG in an HTML document: plain `href` is the namespace-free equivalent of xlink:href.
    if (node.ns === 'svg') return lower === 'xlink:href' ? 'href' : (SVG_ATTR_CASE.get(lower) ?? lower);
    return lower;
  }

  /**
   * The attribute gate. Returns the attribute(s) to set in the renderer:
   * usually one, none if the attribute is dropped.
   */
  private sanitizeAttr(node: NodeInfo, rawName: string, value: string): [string, string][] {
    const name = this.canonAttrName(node, rawName);
    if (name === null) return []; // malformed name or on* handler
    const lower = name.toLowerCase();
    const tag = node.tag;

    if (lower === 'style') return [['style', sanitizeStyleAttribute(value, this.diag, this.env)]];

    if (URL_ATTRS.has(lower)) return this.sanitizeUrlAttr(node, name, lower, value);

    if (lower.startsWith('data-tg-')) return []; // reserved for the host
    if (lower.startsWith('data-') || lower.startsWith('aria-')) return [[lower, value]];
    if (SILENT_DROP_ATTRS.has(lower)) return [];
    if (!(node.ns === 'svg' ? SVG_ALLOWED_ATTRS : HTML_ATTRS).has(lower)) {
      this.diag('attr-dropped', `${tag}[${lower}]`);
      return [];
    }

    if (node.ns === 'svg') {
      if (SVG_ANIMATION_TAGS.has(tag)) {
        if (lower === 'attributename' && /href|src/i.test(value)) return []; // never animate URL attributes
        if (ANIMATION_VALUE_ATTRS.has(lower)) {
          const parts: string[] = [];
          for (const part of value.split(';')) {
            const clean = sanitizeCssValue(part, this.diag, `${tag}[${lower}]`, this.env);
            if (clean === null) return [];
            parts.push(clean);
          }
          return [[name, parts.join(';')]];
        }
      }
      if (CSS_VALUE_ATTRS.has(lower)) {
        const clean = sanitizeCssValue(value, this.diag, `${tag}[${lower}]`, this.env);
        return clean === null ? [] : [[name, clean]];
      }
      if (lower === 'font-family') {
        // Generic families only, as in CSS (D3b).
        const clean = sanitizeStyleProperty('font-family', value, this.diag, this.env);
        return clean && clean.value ? [[name, clean.value]] : [];
      }
    }
    return [[name, value]];
  }

  private sanitizeUrlAttr(node: NodeInfo, name: string, lower: string, value: string): [string, string][] {
    const tag = node.tag;
    const block = (): [string, string][] => {
      this.diag('url-blocked', `${tag}[${lower}]=${clip(value, 80)}`);
      return [];
    };
    const isHref = lower === 'href' || lower === 'xlink:href';

    if (tag === 'a' && isHref) {
      // Links never navigate. Fragments are kept; anything else renders as an
      // inert "#" link and the real URL stays in the sandbox (openLink flow).
      if (isFragment(value)) {
        const frag = checkUrl(value, 'fragment-only');
        return frag ? [[name, frag]] : [[name, '#']];
      }
      return [[name, '#'], ['data-tg-link', '']];
    }
    if (node.ns === 'svg') {
      if (!isHref) return block();
      if (tag === 'image') {
        const url = checkUrl(value, 'image');
        return url && !url.startsWith('#') ? [[name, url]] : block();
      }
      if (SVG_HREF_FRAGMENT_TAGS.has(tag)) {
        const url = checkUrl(value, tag === 'feImage' ? 'image' : 'fragment-only');
        return url ? [[name, url]] : block();
      }
      return block();
    }
    if (lower === 'src') {
      if (tag === 'img' || tag === 'input') {
        const url = checkUrl(value, 'image');
        if (url && !url.startsWith('#')) return [['src', url]];
        block();
        return tag === 'img' ? [['src', PLACEHOLDER_IMAGE], ['data-tg-placeholder', '']] : [];
      }
      // audio/video/source never get here: the elements are dropped by the allowlist.
      return block();
    }
    if (lower === 'usemap') return [];
    // srcset, poster, action, formaction, background, ping, data, srcdoc, …: never allowed.
    return block();
  }

  private opStyle(op: Record<string, unknown>): boolean {
    const { id, name, value, important } = op;
    if (!isId(id) || !isStr(name, 128) || typeof value !== 'string') return false;
    if (value.length > this.limits.maxAttr) return this.tooLongAttr(value.length);
    const node = this.nodes.get(id);
    if (!node || node.kind !== 'el') return false;
    if (!node.rendered) return true;
    if (!/^-{0,2}[a-zA-Z_][\w-]*$/.test(name)) return false;
    const clean = sanitizeStyleProperty(name, value, this.diag, this.env);
    // A rejected value clears the property rather than leaving a stale one.
    let out = clean ? clean.value : '';
    if (this.budgeted && !this.updateTextSlot(node, 'style:' + name, cssStringText(out))) {
      this.releaseTextSlot(node, 'style:' + name);
      out = '';
    }
    this.out.push({ op: 'style', id, name, value: out, important: important === true });
    return true;
  }

  private opProp(op: Record<string, unknown>): boolean {
    const { id, name, value } = op;
    if (!isId(id) || typeof name !== 'string') return false;
    const spec = Object.prototype.hasOwnProperty.call(PROPS, name) ? PROPS[name] : null;
    const node = this.nodes.get(id);
    if (!spec || !node || node.kind !== 'el') return false;
    if (typeof value !== spec.type) return false;
    if (typeof value === 'string' && value.length > this.limits.maxText) return this.tooLongText(value.length);
    if (typeof value === 'number' && !Number.isFinite(value)) return false;
    if (!node.rendered || (spec.tags && !spec.tags.has(node.tag))) return true;
    if (typeof value === 'string' && !this.updateTextSlot(node, 'prop:' + name, value)) return false;
    this.out.push({ op: 'prop', id, name, value: value as string | number | boolean });
    return true;
  }

  private updateTextSlot(node: NodeInfo, key: string, text: string): boolean {
    if (!this.limits.textBudget && !this.limits.complexTextBudget) return true;
    if (text.length > this.limits.maxText) return this.tooLongText(text.length);
    const previous = node.textSlots?.get(key);
    const lengthDelta = text.length - (previous?.length ?? 0);
    const complex = complexUnits(text);
    const complexDelta = complex - (previous?.complex ?? 0);
    if (complexDelta > 0 && this.limits.complexTextBudget && this.complexText + complexDelta > this.limits.complexTextBudget) {
      this.diag('text-budget', `document complex-script text over ${this.limits.complexTextBudget} units`);
      return false;
    }
    if (this.limits.textBudget && chargesChurn(text) && !this.takeChurn(textWork(text))) return this.overChurn();
    this.totalText += lengthDelta;
    this.complexText += complexDelta;
    if (text.length) (node.textSlots ??= new Map()).set(key, { length: text.length, complex });
    else node.textSlots?.delete(key);
    return true;
  }

  private releaseTextSlot(node: NodeInfo, key: string): void {
    const charge = node.textSlots?.get(key);
    if (!charge) return;
    this.totalText -= charge.length;
    this.complexText -= charge.complex;
    node.textSlots!.delete(key);
  }

  private opCall(op: Record<string, unknown>): boolean {
    const { id, method, args } = op;
    if (!isId(id) || typeof method !== 'string') return false;
    if (!Object.prototype.hasOwnProperty.call(METHODS, method)) return false;
    const node = this.nodes.get(id);
    if (!node || node.kind !== 'el') return false;
    if (!node.rendered) return true;
    if (method === 'scrollIntoView' && (args === undefined || Array.isArray(args) && args.length === 0)) {
      this.out.push({ op: 'call', id, method, args: [0, 3, 0, 0] });
      return true;
    }
    const n = METHODS[method];
    const out: number[] = [];
    if (n > 0) {
      if (!Array.isArray(args)) return false;
      for (let i = 0; i < n; i++) {
        if (!isNum(args[i])) return false;
        out.push(args[i] as number);
      }
    }
    if (method === 'scrollIntoView' && out.some((value, index) => !Number.isInteger(value) || value < 0 || value > [3, 3, 2, 1][index])) return false;
    this.out.push({ op: 'call', id, method, args: out });
    return true;
  }

  // ───────────────────────── canvas ─────────────────────────

  private opCanvas(op: Record<string, unknown>): boolean {
    const { id, cmds } = op;
    if (!isId(id) || !Array.isArray(cmds)) return false;
    if (cmds.length > this.limits.maxCanvasCmds) return this.tooManyCanvasCmds(cmds.length);
    const node = this.nodes.get(id);
    if (!node || node.kind !== 'el' || node.tag !== 'canvas') return false;
    const out: CanvasCmd[] = [];
    for (const cmd of cmds) {
      const clean = this.canvasCmd(cmd);
      if (this.stopReason !== null) return false;
      if (clean === OUT_OF_PROFILE) continue;
      if (clean && clean[0] === 'r' && !this.sizeCanvas(id, Math.trunc(Number(clean[1])), Math.trunc(Number(clean[2])))) return false;
      if (clean) {
        if (clean[0] === 'c' && (clean[1] === 'fillText' || clean[1] === 'strokeText') && typeof clean[2] === 'string'
          && this.limits.textBudget && chargesChurn(clean[2]) && !this.takeChurn(textWork(clean[2]))) {
          this.overChurn();
        } else out.push(clean);
      }
      else this.diag('protocol-violation', 'rejected canvas cmd ' + safeJson(cmd));
    }
    if (out.length) this.out.push({ op: 'canvas', id, cmds: out });
    return true;
  }

  private tooManyCanvasCmds(count: number): false {
    return this.stop(`Canvas limit exceeded: ${count} commands in one send (max ${this.limits.maxCanvasCmds})`);
  }

  /** A canvas argument: finite number, short string, boolean, null, flat number array, or object ref. */
  private canvasArg(v: unknown): unknown {
    if (v === null || typeof v === 'boolean') return v;
    if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
    if (typeof v === 'string') return v.length <= this.limits.maxCanvasString ? v : (this.tooLongText(v.length), undefined);
    if (Array.isArray(v)) return v.length <= 4096 && v.every(isNum) ? v : undefined;
    if (isObj(v)) {
      if (isNum(v.g) && Number.isInteger(v.g)) return { g: v.g }; // gradient / pattern
      if (isNum(v.p) && Number.isInteger(v.p)) return { p: v.p }; // Path2D
      if (isId(v.n)) {
        // image source: must be a rendered canvas / img / SVG image (no <video>: never rendered)
        const n = this.renderedElement(v.n);
        return n && (n.tag === 'canvas' || n.tag === 'img' || n.tag === 'image') ? { n: n.id } : undefined;
      }
      if (isNum(v.w) && isNum(v.h) && typeof v.b64 === 'string') return { w: v.w, h: v.h, b64: v.b64 }; // ImageData
    }
    return undefined;
  }

  private canvasArgs(list: unknown[]): unknown[] | null {
    const out: unknown[] = [];
    for (const a of list) {
      const c = this.canvasArg(a);
      if (c === undefined) return null;
      out.push(c);
    }
    return out;
  }

  /** A canvas call or property outside the profile: dropped with a `profile` diagnostic (once per name and canvas op). */
  private outOfProfile(name: string): typeof OUT_OF_PROFILE {
    this.diag('profile', `canvas ${name}`);
    return OUT_OF_PROFILE;
  }

  private canvasCmd(cmd: unknown): CanvasCmd | null | typeof OUT_OF_PROFILE {
    if (!Array.isArray(cmd) || cmd.length < 2 || cmd.length > 16) return null;
    const [kind, name, ...rest] = cmd;
    switch (kind) {
      case 'c': {
        if (typeof name === 'string' && CANVAS_OUT_OF_PROFILE.has(name)) return this.outOfProfile(name);
        if (typeof name !== 'string' || !CANVAS_METHODS.has(name)) return null;
        const args = this.canvasArgs(rest);
        return args ? ['c', name, ...args] : null;
      }
      case 's': {
        if (typeof name === 'string' && CANVAS_OUT_OF_PROFILE.has(name)) return this.outOfProfile(name);
        if (typeof name !== 'string' || !CANVAS_PROPS.has(name) || rest.length !== 1) return null;
        let v = this.canvasArg(rest[0]);
        if (v === undefined || Array.isArray(v)) return null;
        if (name === 'font') {
          // Generic families only, as in CSS (D3b).
          if (typeof v !== 'string') return null;
          v = sanitizeFontValue('font', v, this.diag);
          if (v === null) return null;
        }
        return ['s', name, v];
      }
      case 'r':
        return isNum(name) && isNum(rest[0]) && name >= 0 && rest[0] >= 0 && name <= 16384 && rest[0] <= 16384 ? ['r', name, rest[0]] : null;
      case 'g': {
        // ['g', gid, 'linear'|'radial'|'conic'|'pattern', ...args]
        if (!isNum(name) || typeof rest[0] !== 'string' || !['linear', 'radial', 'conic', 'pattern'].includes(rest[0])) return null;
        // A conic gradient or a pattern (outside the profile) is a zero-length linear gradient: it paints nothing.
        if (!CANVAS_GRADIENTS.has(rest[0])) { this.diag('profile', `canvas ${rest[0]}`); return ['g', name, 'linear', 0, 0, 0, 0]; }
        const args = this.canvasArgs(rest.slice(1));
        return args ? ['g', name, rest[0], ...args] : null;
      }
      case 'gs':
        return isNum(name) && isNum(rest[0]) && typeof rest[1] === 'string' && rest[1].length < 256 ? ['gs', name, rest[0], rest[1]] : null;
      case 'pn':
        // ['pn', pid, svgPathData | null]
        if (typeof rest[0] === 'string' && rest[0].length > this.limits.maxText) return this.tooLongText(rest[0].length) || null;
        return isNum(name) && (rest[0] === null || isStr(rest[0], this.limits.maxText)) ? ['pn', name, rest[0]] : null;
      case 'pc': {
        // ['pc', pid, method, ...args]
        if (!isNum(name) || typeof rest[0] !== 'string' || !CANVAS_PATH_METHODS.has(rest[0])) return null;
        const args = this.canvasArgs(rest.slice(1));
        return args ? ['pc', name, rest[0], ...args] : null;
      }
      default:
        return null;
    }
  }

  // ───────────────────────── queries & misc ─────────────────────────

  /** Validate a sync query. Returns a clean copy, or null to answer with an error. */
  validateQuery(q: unknown): Query | null {
    if (!isObj(q)) return null;
    const el = () => this.renderedElement(q.id);
    switch (q.q) {
      case 'rect': case 'rects': case 'box': case 'validity':
        return el() ? { q: q.q, id: q.id as NodeId } : null;
      case 'cs': {
        if (!el()) return null;
        const pseudo = typeof q.pseudo === 'string' && /^::?[a-z-]{1,32}$/.test(q.pseudo) ? q.pseudo : undefined;
        const prop = typeof q.prop === 'string' && /^--[\w-]{1,128}$/.test(q.prop) ? q.prop : undefined;
        return { q: 'cs', id: q.id as NodeId, ...(pseudo ? { pseudo } : {}), ...(prop ? { prop } : {}) };
      }
      case 'svg': {
        if (!el() || typeof q.method !== 'string' || !SVG_QUERY_METHODS.has(q.method)) return null;
        const args = Array.isArray(q.args) && q.args.length <= 4 && q.args.every(isNum) ? (q.args as number[]) : [];
        return { q: 'svg', id: q.id as NodeId, method: q.method, args };
      }
      case 'measureText': {
        if (!isStr(q.font, 512) || !isStr(q.text, 100_000)) return null;
        // Measured with the font the canvas draws with: generic families only.
        const font = sanitizeFontValue('font', q.font, () => {});
        return font === null ? null : { q: 'measureText', font, text: q.text };
      }
      // Pixel read-back (getImageData) is outside the profile: a native renderer would have to rasterize to answer.
      case 'imageData':
        return null;
      case 'canvasCall': {
        const n = el();
        if (!n || n.tag !== 'canvas' || typeof q.method !== 'string' || !CANVAS_QUERY_METHODS.has(q.method)) return null;
        const args = Array.isArray(q.args) ? this.canvasArgs(q.args.slice(0, 8)) : [];
        return args ? { q: 'canvasCall', id: n.id, method: q.method, args } : null;
      }
      case 'elementFromPoint':
        return isNum(q.x) && isNum(q.y) ? { q: 'elementFromPoint', x: q.x, y: q.y } : null;
      case 'matchMedia':
        return isStr(q.query, 1024) ? { q: 'matchMedia', query: q.query } : null;
      case 'viewport':
        return { q: 'viewport' };
      case 'anims':
        return el() ? { q: 'anims', id: q.id as NodeId, subtree: q.subtree === true } : null;
      default:
        return null;
    }
  }

  private liveAnims = new Set<number>();

  validateAnim(list: unknown): AnimRecord[] {
    if (!Array.isArray(list) || list.length > MAX_ANIM_RECORDS) {
      this.diag('protocol-violation', 'anim batch is not an array or too large');
      return [];
    }
    const live = this.liveAnims;
    const out: AnimRecord[] = [];
    const drop = (aid: number) => { if (live.delete(aid)) out.push({ aid, remove: true }); };
    for (const a of list) {
      if (!isObj(a) || !Number.isInteger(a.aid) || (a.aid as number) < 1 || (a.aid as number) > 2147483647) {
        this.diag('protocol-violation', 'rejected anim record');
        continue;
      }
      const aid = a.aid as number;
      if (a.remove === true) { drop(aid); continue; }
      if (a.css !== undefined) {
        const c = a.css;
        const valid = this.renderedElement(a.target) && isObj(c) && (c.kind === 'animation' || c.kind === 'transition') && typeof c.name === 'string' && /^[\w-]{1,128}$/.test(c.name) && finiteWithin(c.origin, 1e15)
          && (a.startTime === null || finiteWithin(a.startTime, 1e15)) && (a.holdTime === null || finiteWithin(a.holdTime, 1e12)) && finiteWithin(a.playbackRate, 1000) && typeof a.cancelled === 'boolean';
        if (!valid) { this.diag('protocol-violation', 'rejected css anim record'); continue; }
        out.push({
          aid, target: a.target as NodeId, css: { kind: c.kind as 'animation' | 'transition', name: c.name as string, origin: c.origin as number },
          startTime: a.startTime as number | null, holdTime: a.holdTime as number | null, playbackRate: a.playbackRate as number, cancelled: a.cancelled as boolean,
        });
        continue;
      }
      const target = this.renderedElement(a.target);
      const t = a.timing;
      const ok = !!target && Array.isArray(a.keyframes) && a.keyframes.length <= MAX_ANIM_KEYFRAMES && isObj(t)
        && finiteWithin(t.duration, MAX_ANIM_TIME) && (t.duration as number) >= 0 && finiteWithin(t.delay, MAX_ANIM_TIME) && finiteWithin(t.endDelay, MAX_ANIM_TIME)
        && (t.iterations === null || (finiteWithin(t.iterations, 1e9) && (t.iterations as number) >= 0)) && finiteWithin(t.iterationStart, 1e9) && (t.iterationStart as number) >= 0
        && ANIM_DIRECTIONS.has(t.direction as string) && ANIM_FILLS.has(t.fill as string)
        && animEasing(t.easing) !== null && ANIM_COMPOSITES.has(a.composite as string) && ANIM_ITERATION_COMPOSITES.has(a.iterationComposite as string)
        && (a.startTime === null || finiteWithin(a.startTime, 1e15)) && (a.holdTime === null || finiteWithin(a.holdTime, 1e12)) && (a.startTime !== null || a.holdTime !== null)
        && finiteWithin(a.playbackRate, 1000);
      if (!ok) {
        if (target) this.diag('protocol-violation', 'rejected anim record');
        drop(aid);
        continue;
      }
      const timing = t as Record<string, unknown>;
      const frames: AnimKeyframe[] = [];
      let last = 0;
      let bad = false;
      for (const f of a.keyframes as unknown[]) {
        if (!isObj(f) || !finiteWithin(f.offset, 1) || (f.offset as number) < last || (f.offset as number) > 1 || animEasing(f.easing) === null
          || !(f.composite === null || ANIM_COMPOSITES.has(f.composite as string)) || !isObj(f.values)) { bad = true; break; }
        last = f.offset as number;
        const names = Object.keys(f.values);
        if (names.length > MAX_ANIM_VALUES) { bad = true; break; }
        const values: Record<string, string> = {};
        for (const name of names) {
          const value = f.values[name];
          if (typeof value !== 'string' || value.length > 4096 || !/^(?:--[\w-]{1,128}|-?[a-z][a-z-]{0,63})$/.test(name) || /^(?:-webkit-)?(?:animation|transition)(?:-|$)/.test(name)) continue;
          // Keyframe members, not CSS properties: a renderer that merges values into the keyframe object would let them override the checked fields.
          if (KEYFRAME_MEMBERS.has(name)) continue;
          const clean = sanitizeStyleProperty(name, value, this.diag, this.env);
          if (clean && clean.value !== '') values[name] = clean.value;
        }
        frames.push({ offset: f.offset as number, easing: f.easing as string, composite: f.composite as AnimKeyframe['composite'], values });
      }
      if (bad) { this.diag('protocol-violation', 'rejected anim keyframes'); drop(aid); continue; }
      if (!live.has(aid) && live.size >= this.limits.maxLiveAnims) { this.diag('animation-limit', 'more than ' + this.limits.maxLiveAnims + ' live script animations'); continue; }
      live.add(aid);
      out.push({
        aid, target: a.target as NodeId, keyframes: frames,
        timing: {
          duration: timing.duration as number, delay: timing.delay as number, endDelay: timing.endDelay as number, iterations: timing.iterations as number | null,
          iterationStart: timing.iterationStart as number, direction: timing.direction as any, fill: timing.fill as any, easing: timing.easing as string,
        },
        composite: a.composite as any, iterationComposite: a.iterationComposite as any,
        startTime: a.startTime as number | null, holdTime: a.holdTime as number | null, playbackRate: a.playbackRate as number,
      });
    }
    return out;
  }

  /** Event types the sandbox wants forwarded. */
  validateListen(types: unknown): string[] {
    if (!Array.isArray(types)) return [];
    return types.filter((t): t is string => typeof t === 'string' && (EVENT_TYPE_SET.has(t) || t === 'scroll' && this.cssEnv.nativeScrolling === true));
  }

  /** "Learned preventDefault" keys: `${nodeId}:${eventType}`. */
  validatePreventDefault(keys: unknown): string[] {
    if (!Array.isArray(keys)) return [];
    return keys.filter((k): k is string => typeof k === 'string' && /^\d{1,10}:[a-z]{1,20}(?::[\w ]{1,20})?$/.test(k)).slice(0, 256);
  }
}

const MAX_ANIM_RECORDS = 4096;
const MAX_ANIM_KEYFRAMES = 64;
const MAX_ANIM_VALUES = 64;
const MAX_ANIM_TIME = 86_400_000;
const ANIM_DIRECTIONS = new Set(['normal', 'reverse', 'alternate', 'alternate-reverse']);
const ANIM_FILLS = new Set(['none', 'forwards', 'backwards', 'both']);
const ANIM_COMPOSITES = new Set(['replace', 'add', 'accumulate']);
const ANIM_ITERATION_COMPOSITES = new Set(['replace', 'accumulate']);
const ANIM_NUMBER = '[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?';
const ANIM_EASING = new RegExp('^(?:linear|ease|ease-in|ease-out|ease-in-out|step-start|step-end|cubic-bezier\\(\\s*' + ANIM_NUMBER + '\\s*,\\s*' + ANIM_NUMBER + '\\s*,\\s*' + ANIM_NUMBER + '\\s*,\\s*' + ANIM_NUMBER
  + '\\s*\\)|steps\\(\\s*\\d{1,6}\\s*(?:,\\s*(?:start|end|jump-start|jump-end|jump-both|jump-none)\\s*)?\\)|linear\\([0-9eE.%,\\s+-]{1,8192}\\))$');

function animEasing(e: unknown): string | null {
  return typeof e === 'string' && e.length <= 8192 && ANIM_EASING.test(e) && e.split(',').length <= 257 ? e : null;
}

function finiteWithin(n: unknown, max: number): boolean {
  return typeof n === 'number' && Number.isFinite(n) && Math.abs(n) <= max;
}

/** At most `n` UTF-16 units plus an ellipsis, never ending on half a surrogate pair (JSONText.truncated in Swift). */
function clip(s: string, n: number): string {
  if (s.length <= n) return s;
  const last = s.charCodeAt(n - 1);
  return s.slice(0, last >= 0xd800 && last <= 0xdbff ? n - 1 : n) + '…';
}

function safeJson(v: unknown): string {
  try {
    return clip(JSON.stringify(v), 160);
  } catch {
    return '[unserializable]';
  }
}
