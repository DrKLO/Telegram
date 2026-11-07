// Entry point of the validator bundle used by native hosts: the same Validator
// as the web host, evaluated in a TRUSTED QuickJS context (one per app, never
// the widget's). The native side talks JSON strings only.
//
//   __twValidator.create(theme)             → creates the instance
//   __twValidator.processOps(json)          → json RenderOp[]
//   __twValidator.validateQuery(json)       → json Query or '' if rejected
//   __twValidator.validateListen(json)      → json string[]
//   __twValidator.validatePreventDefault(json) → json string[]
//   __twValidator.setTheme('light'|'dark')  → json RenderOp[] (re-emitted stylesheets)
//   __twValidator.rewriteMediaQuery(query)  → string
//   __twValidator.nodeCount()               → number
//   __twValidator.markLoaded()              → the loading tasks are over: text changes are rate-limited from now on
//   __twValidator.isCanvas(id)              → boolean (would a canvas op for id be accepted)
//   __twValidator.stopped()                 → '' or the reason a counted cap stopped the widget (strict)
// Diagnostics are reported through `__diag(kind, detail)` if the host defines it. Kind `limit` is fatal: a
// counted cap was passed under strict, the host stops the widget with `detail` as the reason (every later
// batch comes back empty), as it does for memory or CPU.

import { clearStylesheetCache, sanitizeCssValue, sanitizeStyleAttribute, sanitizeStyleProperty, sanitizeStylesheet } from './css-sanitizer';
import { Validator, validatorTables } from './validator';
import { MAX_ANIM_BATCH_BYTES } from '../shared/protocol';

declare const __diag: ((kind: string, detail: string) => void) | undefined;
declare const __twNode: ((id: number) => string) | undefined;

let validator: Validator | null = null;
const diag = (kind: string, detail: string) => {
  if (typeof __diag === 'function') __diag(kind, detail);
};
const v = () => {
  if (!validator) throw new Error('validator not created');
  return validator;
};

(globalThis as any).__twValidator = {
  create(theme: 'light' | 'dark') {
    clearStylesheetCache();
    validator = new Validator(diag, { colorScheme: theme === 'dark' ? 'dark' : 'light' });
  },
  setNativeScrolling(enabled: string) {
    v().cssEnv.nativeScrolling = enabled === '1';
    clearStylesheetCache();
    return '';
  },
  processOps(json: string) {
    return JSON.stringify(v().processOps(JSON.parse(json)));
  },
  validateQuery(json: string) {
    const q = v().validateQuery(JSON.parse(json));
    return q ? JSON.stringify(q) : '';
  },
  validateListen(json: string) {
    return JSON.stringify(v().validateListen(JSON.parse(json)));
  },
  validateAnim(json: string) {
    return json.length > MAX_ANIM_BATCH_BYTES ? '[]' : JSON.stringify(v().validateAnim(JSON.parse(json)));
  },
  validatePreventDefault(json: string) {
    return JSON.stringify(v().validatePreventDefault(JSON.parse(json)));
  },
  setTheme(theme: 'light' | 'dark') {
    return JSON.stringify(v().setTheme(theme === 'dark' ? 'dark' : 'light'));
  },
  rewriteMediaQuery(query: string) {
    return v().rewriteMediaQuery(String(query));
  },
  nodeCount() {
    return v().nodeCount;
  },
  markLoaded() {
    v().markLoaded();
  },
  isCanvas(id: number) {
    return v().isCanvas(Number(id));
  },
  stopped() {
    return v().stopped ?? '';
  },
  // For a native node model (Swift NativeValidator): the rules as data and the
  // stateless sanitizers; with native('1') the queries ask the host's model
  // through __twNode(id) → tag or ''.
  /** Sanitizes the stylesheets of a widget that is still booting (results and diagnostics are cached, nothing is reported now). */
  warmStylesheets(json: string) {
    const texts = JSON.parse(json);
    if (Array.isArray(texts)) for (const t of texts.slice(0, 16)) if (typeof t === 'string' && t.length < 262_144) sanitizeStylesheet(t, () => {}, v().cssEnv);
    return '';
  },
  setPolicy(policy: string) {
    v().setPolicy(policy === 'compat' ? 'compat' : 'strict');
    return '';
  },
  tables() {
    return JSON.stringify(validatorTables(v().policy));
  },
  native(on: string) {
    v().setNodeLookup(on === '1' && typeof __twNode === 'function' ? (id) => __twNode!(id) || null : null);
    return '';
  },
  cssProp(name: string, value: string) {
    const r = sanitizeStyleProperty(String(name), String(value), v().diagnostics, v().cssEnv);
    return r ? '+' + r.value : '-';
  },
  cssAttr(value: string) {
    return sanitizeStyleAttribute(String(value), v().diagnostics, v().cssEnv);
  },
  cssValue(value: string, where: string) {
    const r = sanitizeCssValue(String(value), v().diagnostics, String(where), v().cssEnv);
    return r === null ? '-' : '+' + r;
  },
  cssSheet(text: string) {
    return sanitizeStylesheet(String(text), v().diagnostics, v().cssEnv);
  },
  canvasCmds(json: string) {
    return JSON.stringify(v().canvasCommands(JSON.parse(json)));
  },
};
