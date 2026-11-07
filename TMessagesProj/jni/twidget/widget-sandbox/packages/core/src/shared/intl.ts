// The `intl` host capability on V8 (the web worker, the Node harness): Intl is delegated to the
// host's ICU, QuickJS has none. The request is what the shim sends (`{kind, locale, opts, value,
// method, unit, a, b}`, src/sandbox/globals.ts); the native copies (IntlBridge.swift,
// IntlBridge.kt, intl.cpp) are checked against these answers in tests/fixtures/intl-cases.json.
// Pure computation, no I/O.

export function formatIntl(req: any): string {
  const locale = typeof req.locale === 'string' || Array.isArray(req.locale) ? req.locale : undefined;
  const opts = req.opts && typeof req.opts === 'object' ? req.opts : undefined;
  const tryLocale = <T>(f: (l: any) => T): T => {
    try { return f(locale); } catch { return f(undefined); }
  };
  switch (req.kind) {
    case 'number': return tryLocale((l) => new Intl.NumberFormat(l, opts).format(Number(req.value)));
    case 'numberParts': return JSON.stringify(tryLocale((l) => new Intl.NumberFormat(l, opts).formatToParts(Number(req.value))));
    case 'numberOptions': return JSON.stringify(tryLocale((l) => new Intl.NumberFormat(l, opts).resolvedOptions()));
    case 'date': return tryLocale((l) => new Intl.DateTimeFormat(l, opts).format(new Date(Number(req.value))));
    case 'dateMethod': {
      const d = new Date(Number(req.value));
      return tryLocale((l) => (req.method === 'toLocaleDateString' ? d.toLocaleDateString(l, opts) : req.method === 'toLocaleTimeString' ? d.toLocaleTimeString(l, opts) : d.toLocaleString(l, opts)));
    }
    case 'dateParts': return JSON.stringify(tryLocale((l) => new Intl.DateTimeFormat(l, opts).formatToParts(new Date(Number(req.value)))));
    case 'dateOptions': return JSON.stringify(tryLocale((l) => new Intl.DateTimeFormat(l, opts).resolvedOptions()));
    case 'plural': return tryLocale((l) => new Intl.PluralRules(l, opts).select(Number(req.value)));
    case 'relative': return tryLocale((l) => new Intl.RelativeTimeFormat(l, opts).format(Number(req.value), req.unit));
    case 'list': return tryLocale((l) => new Intl.ListFormat(l, opts).format((req.value as unknown[]).map(String)));
    case 'compare': return String(tryLocale((l) => new Intl.Collator(l, opts).compare(String(req.a), String(req.b))));
    case 'upper': return tryLocale((l) => String(req.value).toLocaleUpperCase(l));
    case 'lower': return tryLocale((l) => String(req.value).toLocaleLowerCase(l));
    default: return '';
  }
}
