// Wrapper around css-select (linkedom's selector engine). css-select decides
// `:checked` and friends from attributes; browsers use the live state. The
// build redirects linkedom's `import 'css-select'` to this module (see
// scripts/sandbox-build.ts).
//
// css-select resolves its built-in string aliases (`:checked` →
// `[checked]`-style) *before* user-defined pseudo functions, so overriding by
// name does not work. Instead the selector text is rewritten (`:checked` →
// `:tw-checked`) and the state-backed predicates are registered under the
// rewritten names. The predicates themselves are set by index.ts once the
// form-state module is up.

import * as real from 'css-select';

export * from 'css-select';

export const pseudoHooks: Record<string, (el: any) => boolean> = {};

const STATE_PSEUDOS = ['checked', 'focus-visible', 'focus-within', 'focus', 'indeterminate', 'hover', 'active', 'visited', 'placeholder-shown', 'default'];
const PREFIX = 'tw-';
const RE = new RegExp(`:(${STATE_PSEUDOS.join('|')})(?![\\w-])`, 'g');

/** Rename state pseudo-classes outside of quoted strings. */
export function rewriteSelector(selector: string): string {
  if (!selector.includes(':')) return selector;
  let out = '';
  let i = 0;
  while (i < selector.length) {
    const ch = selector[i];
    if (ch === '"' || ch === "'") {
      const end = selector.indexOf(ch, i + 1);
      const stop = end < 0 ? selector.length : end + 1;
      out += selector.slice(i, stop);
      i = stop;
      continue;
    }
    const next = Math.min(...['"', "'"].map((q) => { const p = selector.indexOf(q, i); return p < 0 ? selector.length : p; }));
    out += selector.slice(i, next).replace(RE, `:${PREFIX}$1`);
    i = next;
  }
  return out;
}

function withPseudos<T extends { pseudos?: Record<string, unknown> } | undefined>(options: T): T {
  const pseudos: Record<string, (el: any) => boolean> = { ...(options?.pseudos as any) };
  for (const name of STATE_PSEUDOS) pseudos[PREFIX + name] = (el: any) => pseudoHooks[name]?.(el) ?? false;
  return { ...(options as any), pseudos };
}

const sel = (s: any) => (typeof s === 'string' ? rewriteSelector(s) : s);

export function compile(selector: any, options?: any) {
  return real.compile(sel(selector), withPseudos(options));
}
export function is(element: any, selector: any, options?: any) {
  return real.is(element, sel(selector), withPseudos(options));
}
export function selectAll(query: any, elements: any, options?: any) {
  return real.selectAll(sel(query), elements, withPseudos(options));
}
export function selectOne(query: any, elements: any, options?: any) {
  return real.selectOne(sel(query), elements, withPseudos(options));
}
