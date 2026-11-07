// A widget's size as the server counts it (docs/tgw-format.md): mini-HTML is at most 32 768 bytes of HTML and
// 4096 bytes after zlib compression at level 9 (PHP gzcompress($html, 9), RFC 1950: a 2-byte header and a 4-byte
// Adler-32 around the deflate stream); past either the server refuses the widget with INPUT_MEDIA_WIDGET_TOO_LARGE.
// 3800 B is the margin the conformance set keeps (scripts/conformance/build.ts): another zlib can differ by a few bytes
// (Node's bundled zlib, Chromium's fork, compresses random text about 18 bytes tighter than the server's PHP at the
// 4096 B boundary: measured on the beta, 2026-10-28), and the dev app's CompressionStream compresses at level 6, a few
// bytes more than level 9 on real widgets. Shared by the harness and the dev app.

export const MINI_HTML = { zlib: 4096, raw: 32768, warnZlib: 3800 } as const;

/** Bytes after zlib compression (level 9 in the harness), and of the HTML as UTF-8. */
export interface WidgetSize { zlib: number; raw: number }

const kb = (n: number) => (n / 1024).toFixed(1) + ' KB';

/** `size: 2134 B zlib / 9.8 KB raw (limit 4096 / 32768)`. */
export function sizeLine(s: WidgetSize): string {
  return `size: ${s.zlib} B zlib / ${kb(s.raw)} raw (limit ${MINI_HTML.zlib} / ${MINI_HTML.raw})`;
}

/** What to tell the author: nothing, a warning close to the compressed limit, or an error past a limit. */
export function sizeVerdict(s: WidgetSize): { level: 'ok' | 'warning' | 'error'; message: string | null } {
  const over: string[] = [];
  if (s.zlib > MINI_HTML.zlib) over.push(`${s.zlib} B compressed is over the ${MINI_HTML.zlib} B limit by ${s.zlib - MINI_HTML.zlib} B`);
  if (s.raw > MINI_HTML.raw) over.push(`${s.raw} B of HTML is over the ${MINI_HTML.raw} B limit by ${s.raw - MINI_HTML.raw} B`);
  if (over.length) return { level: 'error', message: `error: too large, the server refuses it (INPUT_MEDIA_WIDGET_TOO_LARGE): ${over.join('; ')}. Shorten the code, drop data: images, reuse classes.` };
  if (s.zlib > MINI_HTML.warnZlib) return { level: 'warning', message: `warning: ${s.zlib} B compressed is ${MINI_HTML.zlib - s.zlib} B under the ${MINI_HTML.zlib} B limit; another zlib can count a few bytes more: aim for ${MINI_HTML.warnZlib} B or less.` };
  return { level: 'ok', message: null };
}
