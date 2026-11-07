// The sandbox's only link to the outside world: the `__host` function installed
// by the worker glue. Everything in src/sandbox runs INSIDE QuickJS and is
// untrusted from the host's point of view — nothing here is a security boundary.

declare const __host: (kind: string, arg?: string) => any;
declare const __hostBinary: () => ArrayBuffer | null;
declare const __hostCanvas: ((key: number, words: Float64Array, strings: string) => void) | undefined;

const hostFn = __host;
const hostBin = __hostBinary;
/** Optional binary channel for canvas command words (docs/protocol.md, "Canvas words"); only when the glue installs it. */
export const hostCanvas = typeof __hostCanvas === 'function' ? __hostCanvas : null;
declare const __hostOps: ((words: Int32Array, strings: string) => void) | undefined;
/** Optional binary channel for DOM ops (docs/protocol.md, "Binary DOM ops"); only when the glue installs it. */
export const hostOps = typeof __hostOps === 'function' ? __hostOps : null;

let stagedCanvas: { words: Float64Array; strings: string }[] = [];

/** Queue a canvas word payload for the next ops batch; its index is the `bin` of the placeholder op. */
export function stageCanvas(words: Float64Array, strings: string): number {
  return stagedCanvas.push({ words, strings }) - 1;
}

export function stagedCanvasCount(): number {
  return stagedCanvas.length;
}

/** Hands the staged payloads to the glue; called right before the `ops` batch that refers to them. */
export function sendStagedCanvas(): void {
  if (!stagedCanvas.length || !hostCanvas) return;
  const staged = stagedCanvas;
  stagedCanvas = [];
  for (let k = 0; k < staged.length; k++) hostCanvas(k, staged[k].words, staged[k].strings);
}

export const host = (kind: string, arg?: string): any => hostFn(kind, arg);
export const hostBinary = (): ArrayBuffer | null => hostBin();

export function diag(kind: string, detail: string): void {
  hostFn('diag', kind + ':' + detail);
}

const seenUnsupported = new Set<string>();
/** Record (once per API) that the widget touched something the runtime does not provide. */
export function unsupported(api: string): void {
  if (seenUnsupported.has(api)) return;
  seenUnsupported.add(api);
  diag('unsupported', api);
}

// ── sync queries with a layout cache ──


const cache = new Map<string, any>();
export const counters = { cacheHits: 0, missed: 0 };
let flushHook: () => void = () => {};
export function setFlushHook(fn: () => void) {
  flushHook = fn;
}

/** Layout may have changed: drop cached answers. Called after every flush that sent ops. */
export function invalidateLayout(keepBoxes = false): void {
  if (!keepBoxes) {
    cache.clear();
    return;
  }
  for (const key of cache.keys()) if (!key.startsWith('{"q":"box",')) cache.delete(key);
}

/**
 * Synchronous round trip to the renderer. Pending mutations are flushed first so
 * the answer reflects the current DOM; answers are cached until the next flush.
 */
export function query(q: Record<string, unknown>, cacheable = true): any {
  flushHook();
  const key = cacheable ? JSON.stringify(q) : '';
  if (cacheable) {
    const hit = cache.get(key);
    if (hit !== undefined) {
      counters.cacheHits++;
      return hit;
    }
  }
  let result: any;
  try {
    result = JSON.parse(hostFn('query', JSON.stringify(q)));
  } catch {
    result = { error: 'bad result' };
  }
  if (cacheable) cache.set(key, result);
  return result;
}
