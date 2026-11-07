// `CSS.supports()` and `@supports` answered from the widget profile (D23).
//
// One evaluator for both: the sandbox shim answers `CSS.supports` with it (no
// css-tree in QuickJS), and the validator decides every `@supports` block with
// it, so a widget gets the same answer from either. "Supported" means the
// validator keeps the declaration as written: nothing in it is removed or
// replaced (a fallback family the validator appends after `ui-monospace` does
// not count, nor a safe-area `env()` it renames to the root variable that holds
// the same value, docs/sdk-v1.md §2.17). `tests/unit/profile.test.ts` checks this against the sanitizer.

import { PROFILE_CSS, PROFILE_IMAGES } from './profile-tables';
import { SAFE_AREA_ENV_VARIABLES } from './sdk-policy';

const CSS = PROFILE_CSS;
const PROPERTIES = new Set<string>(CSS.properties);
const FUNCTIONS = new Set<string>(CSS.functions);
const UNITS = new Set<string>(CSS.units);
const FILTER_FUNCTIONS = new Set<string>(CSS.filterFunctions);
const TRANSFORM_3D = new Set<string>(CSS.transform3d);
const GENERIC_FONTS = new Set<string>(CSS.fonts.generic);
const PSEUDO_CLASSES = new Set<string>([...CSS.pseudoClasses, ...Object.keys(CSS.pseudoRewrite)]);
const PSEUDO_ELEMENTS = new Set<string>(CSS.pseudoElements);
const VALUES = CSS.values as Record<string, readonly string[]>;
const REWRITE = CSS.rewrite as Record<string, Record<string, string>>;
const GLOBAL_KEYWORDS = new Set(['inherit', 'initial', 'unset', 'revert', 'revert-layer']);
const FONT_SIZE_KEYWORDS = new Set(['xx-small', 'x-small', 'small', 'medium', 'large', 'x-large', 'xx-large', 'xxx-large', 'smaller', 'larger']);
const OVERFLOW = new Set(['overflow', 'overflow-x', 'overflow-y']);
const IMAGE_URL = new RegExp('^(?:#|data:(?:' + PROFILE_IMAGES.map((m) => m.replace(/[+.]/g, '\\$&')).join('|') + ')[;,])', 'i');

export function cssPropertyInProfile(name: string): boolean {
  const p = name.toLowerCase();
  return p.startsWith('--') || PROPERTIES.has(p);
}
export const cssFunctionInProfile = (name: string) => FUNCTIONS.has(name.toLowerCase());
export const cssUnitInProfile = (unit: string) => UNITS.has(unit.toLowerCase());
export const cssPseudoClassInProfile = (name: string) => PSEUDO_CLASSES.has(name.toLowerCase());
export const cssPseudoElementInProfile = (name: string) => PSEUDO_ELEMENTS.has(name.toLowerCase());
export const isGenericFont = (name: string) => GENERIC_FONTS.has(name.toLowerCase());

// ───────────── a small CSS tokenizer (enough to judge a value) ─────────────

type Tok =
  | { t: 'ident'; v: string }
  | { t: 'func'; v: string; args: Tok[] }
  | { t: 'dim'; unit: string }
  | { t: 'num' }
  | { t: 'pct' }
  | { t: 'str'; v: string }
  | { t: 'hash' }
  | { t: 'url'; v: string }
  | { t: 'delim'; v: string };

const IDENT_START = /[a-zA-Z_\-\u0080-￿\\]/;
const IDENT_CHAR = /[\w\-\u0080-￿\\]/;

function tokenize(s: string): Tok[] | null {
  let i = 0;
  const readIdent = () => { const a = i; while (i < s.length && IDENT_CHAR.test(s[i])) i += s[i] === '\\' ? 2 : 1; return s.slice(a, i); };
  const list = (close: string | null): Tok[] | null => {
    const out: Tok[] = [];
    while (i < s.length) {
      const c = s[i];
      if (/\s/.test(c)) { i++; continue; }
      if (close && c === close) { i++; return out; }
      if (c === ')' || c === ']' || c === '}' || c === ';' || c === '{') return null;
      if (c === '"' || c === "'") {
        let v = '';
        i++;
        while (i < s.length && s[i] !== c) { if (s[i] === '\\') i++; v += s[i++] ?? ''; }
        if (i >= s.length) return null;
        i++;
        out.push({ t: 'str', v });
        continue;
      }
      if (c === '#') { i++; readIdent(); out.push({ t: 'hash' }); continue; }
      const num = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?/.exec(s.slice(i));
      if (num && !(c === '-' && IDENT_START.test(s[i + 1] ?? '') && !/\d/.test(s[i + 1]))) {
        i += num[0].length;
        if (s[i] === '%') { i++; out.push({ t: 'pct' }); }
        else if (i < s.length && IDENT_START.test(s[i]) && !(s[i] === '-' && !IDENT_START.test(s[i + 1] ?? ''))) out.push({ t: 'dim', unit: readIdent() });
        else out.push({ t: 'num' });
        continue;
      }
      if (IDENT_START.test(c) && !(c === '-' && /\d/.test(s[i + 1] ?? '') )) {
        const v = readIdent();
        if (!v) return null;
        if (s[i] === '(') {
          i++;
          if (v.toLowerCase() === 'url') {
            const a = i;
            const end = s.indexOf(')', i);
            if (end < 0) return null;
            i = end + 1;
            out.push({ t: 'url', v: s.slice(a, end).trim().replace(/^(['"])(.*)\1$/, '$2') });
            continue;
          }
          const args = list(')');
          if (!args) return null;
          out.push({ t: 'func', v: v.toLowerCase(), args });
          continue;
        }
        out.push({ t: 'ident', v });
        continue;
      }
      if (c === '!') return null;
      out.push({ t: 'delim', v: c });
      i++;
    }
    return close ? null : out;
  };
  return list(null);
}

function* deep(toks: Tok[]): Generator<Tok> {
  for (const t of toks) {
    yield t;
    if (t.t === 'func') yield* deep(t.args);
  }
}

const hasVar = (toks: Tok[]) => [...deep(toks)].some((t) => t.t === 'func' && (t.v === 'var' || t.v === 'env' || t.v === 'attr'));

/** Commas at the top level split a list (font families, filter is space-separated). */
function splitCommas(toks: Tok[]): Tok[][] {
  const out: Tok[][] = [[]];
  for (const t of toks) {
    if (t.t === 'delim' && t.v === ',') out.push([]);
    else out[out.length - 1].push(t);
  }
  return out;
}

/** Is this font-family list kept as written (generic families, keywords, var())? */
function familiesSupported(toks: Tok[]): boolean {
  for (const group of splitCommas(toks)) {
    if (group.length === 0) return false;
    if (group.some((t) => t.t === 'func')) continue;
    if (group.length !== 1 || group[0].t !== 'ident') return false;
    const name = group[0].v.toLowerCase();
    if (!GENERIC_FONTS.has(name) && !GLOBAL_KEYWORDS.has(name)) return false;
  }
  return true;
}

/** The family part of a `font` shorthand: everything after the size (and `/ line-height`), or null when there is none. */
export function fontShorthandFamilyStart<T>(items: T[], kind: (t: T) => { size: boolean; slash: boolean }): number {
  for (let i = 0; i < items.length; i++) {
    if (!kind(items[i]).size) continue;
    let j = i + 1;
    if (j < items.length && kind(items[j]).slash) j += 2;
    return j;
  }
  return -1;
}

function tokKind(t: Tok) {
  // A unitless number before the size is the weight.
  const size = t.t === 'dim' || t.t === 'pct' || (t.t === 'ident' && FONT_SIZE_KEYWORDS.has(t.v.toLowerCase()))
    || (t.t === 'func' && ['calc', 'min', 'max', 'clamp'].includes(t.v));
  return { size, slash: t.t === 'delim' && t.v === '/' };
}

/** `env(<safe-area-inset-*>[, fallback])`: the validator's rewrite keeps its meaning; another name is replaced or dropped. */
const envSupported = (args: Tok[]) => args[0]?.t === 'ident' && Object.prototype.hasOwnProperty.call(SAFE_AREA_ENV_VARIABLES, args[0].v)
  && (args.length === 1 || (args[1].t === 'delim' && args[1].v === ','));

function valueTokensSupported(toks: Tok[]): boolean {
  for (const t of deep(toks)) {
    if (t.t === 'func' && (!FUNCTIONS.has(t.v) || (t.v === 'env' && !envSupported(t.args)))) return false;
    if (t.t === 'dim' && !UNITS.has(t.unit.toLowerCase())) return false;
    if (t.t === 'url' && !IMAGE_URL.test(t.v)) return false;
  }
  return true;
}

/** `CSS.supports(property, value)`: would the validator keep `property: value` as written? */
export function supportsDeclaration(property: string, value: string): boolean {
  const prop = String(property).trim().toLowerCase();
  const text = String(value).trim();
  if (!prop || !text) return false;
  if (!cssPropertyInProfile(prop)) return false;
  const toks = tokenize(text);
  if (!toks || !toks.length) return false;
  if (!valueTokensSupported(toks)) return false;
  if (prop.startsWith('--')) return true;
  const idents = toks.filter((t): t is { t: 'ident'; v: string } => t.t === 'ident').map((t) => t.v.toLowerCase());
  if (toks.length === 1 && idents.length === 1 && GLOBAL_KEYWORDS.has(idents[0])) return true;
  const varUsed = hasVar(toks);
  const allowed = VALUES[prop];
  if (allowed && !varUsed && !(toks.length === 1 && idents.length === 1 && allowed.includes(idents[0]))) return false;
  const rewriteKey = OVERFLOW.has(prop) ? 'overflow' : prop;
  if (REWRITE[rewriteKey] && idents.some((v) => Object.prototype.hasOwnProperty.call(REWRITE[rewriteKey], v))) return false;
  if ((prop === 'background-clip') && idents.includes('text')) return false;
  if (prop === 'transform' && [...deep(toks)].some((t) => t.t === 'func' && TRANSFORM_3D.has(t.v))) return false;
  if ((prop === 'rotate' && (idents.some((v) => v === 'x' || v === 'y' || v === 'z') || toks.filter((t) => t.t === 'num').length > 1))
    || ((prop === 'translate' || prop === 'scale') && toks.filter((t) => t.t !== 'delim').length > 2)) return false;
  if ((prop === 'filter' || prop === 'backdrop-filter' || prop === '-webkit-backdrop-filter')
    && toks.some((t) => (t.t === 'func' && !FILTER_FUNCTIONS.has(t.v) && t.v !== 'var') || t.t === 'url')) return false;
  if (prop === 'font-family' && !varUsed && !familiesSupported(toks)) return false;
  if (prop === 'font' && !varUsed) {
    const start = fontShorthandFamilyStart(toks, tokKind);
    if (start >= 0 && !familiesSupported(toks.slice(start))) return false;
  }
  return true;
}

/** `CSS.supports('selector(…)')`: every pseudo-class and pseudo-element in the profile, no nesting selector. */
export function supportsSelector(selector: string): boolean {
  const s = String(selector).trim();
  if (!s || s.includes('&') || /[{};]/.test(s)) return false;
  const re = /(::?)(-?[a-zA-Z][\w-]*)/g;
  for (let m; (m = re.exec(s.replace(/"[^"]*"|'[^']*'|\[[^\]]*\]/g, '')));) {
    const ok = m[1] === '::' ? PSEUDO_ELEMENTS.has(m[2].toLowerCase()) : PSEUDO_CLASSES.has(m[2].toLowerCase());
    if (!ok) return false;
  }
  return true;
}

/**
 * A `@supports` condition / `CSS.supports(conditionText)`: `not`, `and`, `or`,
 * parentheses, `(property: value)`, `selector(…)`. Anything else (font-tech(),
 * font-format(), unknown functions, general enclosed) is false.
 */
export function supportsCondition(text: string): boolean {
  const s = String(text);
  let i = 0;
  const ws = () => { while (i < s.length && /\s/.test(s[i])) i++; };
  const word = (w: string) => {
    ws();
    if (s.slice(i, i + w.length).toLowerCase() === w && !/[\w-]/.test(s[i + w.length] ?? '')) { i += w.length; return true; }
    return false;
  };
  /** The text up to the parenthesis that closes the one just consumed. */
  const balanced = (): string | null => {
    let depth = 1;
    const a = i;
    let q = '';
    for (; i < s.length; i++) {
      const c = s[i];
      if (q) { if (c === '\\') i++; else if (c === q) q = ''; continue; }
      if (c === '"' || c === "'") q = c;
      else if (c === '(') depth++;
      else if (c === ')' && --depth === 0) { i++; return s.slice(a, i - 1); }
    }
    return null;
  };
  const inParens = (): boolean | null => {
    ws();
    if (s.slice(i, i + 9).toLowerCase() === 'selector(') { i += 9; const b = balanced(); return b === null ? null : supportsSelector(b); }
    const fn = /^[a-zA-Z-]+\(/.exec(s.slice(i));
    if (fn) { i += fn[0].length; return balanced() === null ? null : false; }
    if (s[i] !== '(') return null;
    i++;
    const save = i;
    // A nested condition, or a declaration.
    const nested = condition();
    ws();
    if (nested !== null && s[i] === ')') { i++; return nested; }
    i = save;
    const b = balanced();
    if (b === null) return null;
    const colon = b.indexOf(':');
    if (colon <= 0) return false;
    const prop = b.slice(0, colon).trim();
    if (!/^-{0,2}[a-zA-Z_][\w-]*$/.test(prop)) return false;
    return supportsDeclaration(prop, b.slice(colon + 1));
  };
  const condition = (): boolean | null => {
    if (word('not')) { const v = inParens(); return v === null ? null : !v; }
    let v = inParens();
    if (v === null) return null;
    for (;;) {
      if (word('and')) { const r = inParens(); if (r === null) return null; v = v && r; }
      else if (word('or')) { const r = inParens(); if (r === null) return null; v = v || r; }
      else return v;
    }
  };
  const v = condition();
  ws();
  return v === true && i === s.length;
}

/** `CSS.supports(a)` / `CSS.supports(a, b)`. */
export function cssSupports(...args: unknown[]): boolean {
  if (args.length >= 2) return supportsDeclaration(String(args[0]), String(args[1]));
  if (args.length === 1) {
    const text = String(args[0]).trim();
    // A bare declaration without parentheses is accepted by browsers too.
    if (!/^(?:not\b|\(|selector\()/i.test(text) && /^-{0,2}[a-zA-Z_][\w-]*\s*:/.test(text)) {
      const colon = text.indexOf(':');
      return supportsDeclaration(text.slice(0, colon), text.slice(colon + 1));
    }
    return supportsCondition(text);
  }
  return false;
}
