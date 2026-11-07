// Evaluated before any library code (first import of index.ts): QuickJS has no
// atob/btoa and linkedom's module-level code needs them.

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const G: any = globalThis;

// QuickJS implements V8's stack-trace API: with `Error.prepareStackTrace` set,
// `CallSite.getFunction()` returns the function of every frame, so a widget
// handler could pick up the shim's closures (the dispatcher among them) from its
// own stack. Every CallSite shares one prototype: its getFunction / getThis
// answer undefined from now on, as V8 does for strict-mode frames.
{
  const E: any = Error;
  const saved = E.prepareStackTrace;
  let callSite: any = null;
  try {
    E.prepareStackTrace = (_error: unknown, sites: unknown[]) => {
      callSite = Array.isArray(sites) && sites.length ? Object.getPrototypeOf(sites[0]) : null;
      return '';
    };
    void new Error().stack;
  } finally {
    E.prepareStackTrace = saved;
  }
  for (const name of ['getFunction', 'getThis']) {
    if (callSite && name in callSite) Object.defineProperty(callSite, name, { value: function () { return undefined; }, writable: false, enumerable: false, configurable: false });
  }
}

G.btoa = (input: string): string => {
  const s = String(input);
  let out = '';
  for (let i = 0; i < s.length; i += 3) {
    const a = s.charCodeAt(i), b = s.charCodeAt(i + 1), c = s.charCodeAt(i + 2);
    if (a > 255 || b > 255 || c > 255) throw new Error('InvalidCharacterError: btoa: non-Latin1 character');
    out += B64[a >> 2] + B64[((a & 3) << 4) | ((b || 0) >> 4)] + (i + 1 < s.length ? B64[((b & 15) << 2) | ((c || 0) >> 6)] : '=') + (i + 2 < s.length ? B64[c & 63] : '=');
  }
  return out;
};

G.atob = (input: string): string => {
  const s = String(input).replace(/[\t\n\f\r ]/g, '').replace(/=+$/, '');
  if (/[^A-Za-z0-9+/]/.test(s)) throw new Error('InvalidCharacterError: atob: invalid character');
  let out = '';
  let bits = 0;
  let n = 0;
  for (let i = 0; i < s.length; i++) {
    bits = (bits << 6) | B64.indexOf(s[i]);
    n += 6;
    if (n >= 8) {
      n -= 8;
      out += String.fromCharCode((bits >> n) & 255);
    }
  }
  return out;
};
