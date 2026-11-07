// URL policy (security-critical, invariant 4).
//
// The ONLY URLs that may reach the renderer's DOM or CSS are:
//   - data: URLs with an allowlisted image MIME type
//   - blob: URLs minted by the host (none are minted in this prototype)
//   - same-document fragments ("#id")
// Everything else is rejected by returning null. There is deliberately no
// font or media kind: widgets use system fonts only (`@font-face` is dropped
// by the CSS sanitizer) and `<audio>`/`<video>` are dropped by the allowlist,
// so no font or codec parser is ever fed widget bytes.

import { PROFILE_IMAGES } from '../shared/profile-tables';

export type UrlKind = 'image' | 'fragment-only';

/** The profile's image types (PNG, JPEG, GIF, WebP: the decoder runs in the app process; SVG, AVIF, BMP and ICO are out). */
export const IMAGE_MIME = new Set<string>(PROFILE_IMAGES);

/** blob: URLs created by the host itself. Nothing else may use the blob: scheme. */
export const hostBlobUrls = new Set<string>();

/**
 * Strip the characters the URL parser ignores (ASCII tab/newline anywhere,
 * C0 control + space at the ends) so that "ja\nvascript:" style tricks and
 * leading whitespace cannot disguise a scheme.
 */
function normalize(raw: string): string {
  return raw.replace(/[\t\n\r]/g, '').replace(/^[\u0000- ]+|[\u0000- ]+$/g, '');
}

function dataMime(url: string): string | null {
  // data:[<mime>][;params][;base64],<data>
  const m = /^data:([^,;]*)(?:;[^,]*)?,/i.exec(url);
  return m ? m[1].trim().toLowerCase() : null;
}

/**
 * The most pixels a data: image may have. A decoder takes the size from the image's header and holds a bitmap
 * of it whatever the bytes are: a few hundred bytes of PNG can claim 65535² (16 GB as RGBA), and 2 KB of a one-colour
 * PNG is a real 4096² (64 MB). 1024² is 4 MB, an image across a 320 px bubble at 3×; the corpora's largest is 40 × 20
 * (docs/decisions.md, "Strict limits from the start").
 */
export const MAX_IMAGE_PIXELS = 1024 * 1024;
/** An image no decoder takes: it fires `error` and has no size, as a broken one does. */
export const BROKEN_IMAGE = 'data:image/png;base64,';
/**
 * How far into an image what gives its size is looked for: a JPEG's frame header past the segments before it, a GIF's
 * first image descriptor past its extensions. A widget's document is at most 32 KB, an image in it smaller still, and
 * one a script makes carries no camera's metadata.
 */
const HEADER_SCAN_BYTES = 64 * 1024;
/** The least of a plain base64 body decoded at once (4096 characters), from where a read leaves the last window. */
const BLOCK_BYTES = 3072;

/** Bytes of a data: URL's body, read in place: `has(from, to)` makes bytes from…to readable by `at` (false past the end). */
interface DataBytes {
  has(from: number, to: number): boolean;
  at(i: number): number;
}

/**
 * A data: URL's body as fetch decodes it (percent-decoding, then forgiving base64), read without a copy: a read inside
 * the bytes decoded last costs a comparison, and those are decoded again only where a read leaves them. A plain base64
 * body (the usual one: no escapes, no white space) is decoded by windows, byte i lying in the four characters of group
 * i / 3, from where the read starts and at least BLOCK_BYTES on, and is only checked to be plain up to them; any other is
 * decoded once from its start, as far as the reads go (`bodyDecoder`). So a scan stepping through the bytes decodes each
 * about once, one skipping far decodes where it lands, and none decodes much past HEADER_SCAN_BYTES, however long the URL.
 */
function dataBytes(url: string): DataBytes {
  // the fragment is the URL's, not the body's: fetch decodes the body without it
  const hash = url.indexOf('#');
  const data = hash < 0 ? url : url.slice(0, hash);
  const comma = data.indexOf(',');
  const base64 = comma >= 0 && /;[\t\n\f\r ]*base64$/i.test(data.slice(0, comma).replace(/[\t\n\f\r ]+$/, ''));
  const body = comma < 0 ? '' : data.slice(comma + 1);
  // the characters known to be plain base64 from the start; -1 once one is not
  let plain = base64 ? 0 : -1;
  // the bytes decoded last, as a binary string (a character a byte) from byte `start`, to the body's end if `toEnd`
  let start = 0, text = '', toEnd = false;
  // once part of the body is no base64, none of it is: the browser decodes nothing, and each scan that probes the
  // signatures after the first asks again
  let failed = false;
  let decoder: ReturnType<typeof bodyDecoder> | undefined;
  /** Decodes the bytes from…to where the last ones do not hold them: whether the body has them. */
  const load = (from: number, to: number): boolean => {
    if (failed || (from >= start && toEnd)) return false;
    if (plain >= 0) {
      const first = Math.floor(from / 3) * 3;
      const end = Math.min(Math.ceil(Math.max(to, first + BLOCK_BYTES) / 3) * 4, body.length);
      if (end > plain) plain = /[%\t\n\f\r ]/.test(body.slice(plain, end)) ? -1 : end;
      if (plain >= 0) {
        // a padding or a character no base64 has on the way (a window starts on a group of four, so it decodes as
        // the whole body would)
        try { text = atob(body.slice((first / 3) * 4, end)); } catch { failed = true; return false; }
        start = first;
        toEnd = end === body.length;
        return to <= start + text.length;
      }
    }
    decoder ??= bodyDecoder(body, base64);
    const decoded = decoder(to);
    if (decoded === null) { failed = true; return false; }
    start = 0;
    text = decoded;
    toEnd = decoded.length < to;
    return !toEnd;
  };
  // the check a scan makes at every step, small enough for the engine to inline
  return {
    has: (from, to) => (from >= start && to <= start + text.length) || load(from, to),
    at: (i) => text.charCodeAt(i - start),
  };
}

/**
 * Decodes a data: URL's body from its start (percent-decoding, then forgiving base64 if `base64`) part by part, with
 * the engine's own string functions, into a binary string it keeps: `upTo(n)` gives it with at least the first n bytes
 * if the body has them, null if the body is no base64.
 */
function bodyDecoder(body: string, base64: boolean): (n: number) => string | null {
  let decoded = '', read = 0, carry = '', failed = false;
  return (n) => {
    while (!failed && decoded.length < n && read < body.length) {
      // parts that grow with what is decoded, so that reading far costs about what is read
      let part = body.slice(read, read + Math.max(4096, decoded.length));
      const last = read + part.length >= body.length;
      // an escape or a surrogate pair cut in two is read with the next part (UTF-8 of half a pair is another byte count)
      if (!last) part = part.slice(0, part.length - cutTail(part));
      read += part.length;
      const text = percentDecode(part);
      if (!base64) { decoded += text; continue; }
      let b64 = carry + text.replace(/[\t\n\f\r ]/g, '');
      // whole groups only, but at the end, where atob takes a short last group and the padding as fetch does
      carry = last ? '' : b64.slice(b64.length - (b64.length % 4));
      b64 = b64.slice(0, b64.length - carry.length);
      try { decoded += atob(b64); } catch { failed = true; }
    }
    return failed ? null : decoded;
  };
}

/** How many characters at the end of `part` start what the next part completes: `%`, `%X`, a high surrogate. */
function cutTail(part: string): number {
  const n = part.length, last = part.charCodeAt(n - 1);
  if (last === 37 || (last >= 0xd800 && last <= 0xdbff)) return 1;
  return n > 1 && part.charCodeAt(n - 2) === 37 && /[0-9a-f]/i.test(part[n - 1]) ? 2 : 0;
}

/** Percent-decoding as the URL standard does it, into a binary string (one character a byte). */
function percentDecode(text: string): string {
  // a character past ASCII is its UTF-8 bytes, as the URL parser encoded it (a lone surrogate as U+FFFD's); no
  // TextEncoder: native hosts run this in a QuickJS context, which has none
  if (/[^\x00-\x7f]/.test(text)) {
    text = unescape(encodeURIComponent(text.replace(/[\ud800-\udbff][\udc00-\udfff]|[\ud800-\udfff]/g, (c) => (c.length === 2 ? c : '\ufffd'))));
  }
  if (!text.includes('%')) return text;
  // unescape() decodes %XX as the standard does and leaves a malformed escape, but also takes %uXXXX: keep that literal
  return unescape(/%u/i.test(text) ? text.replace(/%u/gi, '%25u') : text);
}

/**
 * The size a decoder would give a data: image, by its signature (the browser sniffs it: the declared type does not
 * pick the decoder), or null for none of the profile's formats — BMP, ICO or AVIF bytes behind an image/png type
 * could reach decoders the profile keeps out.
 */
export function dataImageSize(url: string): [number, number] | null {
  // The scans read the bytes in place, a step a comparison or two whatever the bytes are (64 KB of a JPEG's fill bytes,
  // 32 000 one-byte GIF sub-blocks), and take a bounded number of steps: a check is paid for every element.
  const { has, at } = dataBytes(url);
  const u16be = (i: number) => (at(i) << 8) | at(i + 1);
  const le16 = (i: number) => at(i) | (at(i + 1) << 8);
  const u24le = (i: number) => at(i) | (at(i + 1) << 8) | (at(i + 2) << 16);
  /** Whether the bytes at i are those of `magic`. */
  const is = (i: number, magic: string) => {
    if (!has(i, i + magic.length)) return false;
    for (let k = 0; k < magic.length; k++) if (at(i + k) !== magic.charCodeAt(k)) return false;
    return true;
  };
  // PNG: the signature, then IHDR's width and height
  if (is(0, '\x89PNG\r\n\x1a\n') && is(12, 'IHDR') && has(0, 24)) return [u16be(16) * 65536 + u16be(18), u16be(20) * 65536 + u16be(22)];
  // GIF: the logical screen, grown to the first frame where that frame reaches past it, as decoders grow the image
  // (Wuffs without its strict-bounds quirk, Chromium's GIF reader before it); later frames are clipped to it
  if ((is(0, 'GIF87a') || is(0, 'GIF89a')) && has(0, 13)) {
    const screen: [number, number] = [le16(6), le16(8)];
    // past the global colour table, extensions until the first image descriptor (an image has a few: frame control,
    // NETSCAPE looping, a comment), in at most 4096 sub-blocks (an XMP packet, read as sub-blocks, takes one for every
    // 30 to 120 bytes)
    let blocks = 0;
    for (let i = 13 + (at(10) & 0x80 ? 3 << ((at(10) & 7) + 1) : 0), extensions = 0; i < HEADER_SCAN_BYTES && extensions < 256; extensions++) {
      if (!has(i, i + 2)) return null;
      if (at(i) === 0x2c) return has(i, i + 9) ? [Math.max(screen[0], le16(i + 1) + le16(i + 5)), Math.max(screen[1], le16(i + 3) + le16(i + 7))] : null;
      // an extension (introducer, label, its sub-blocks up to the empty one); anything else, the trailer included: no frame
      if (at(i) !== 0x21) return null;
      for (i += 2; ;) {
        if (i >= HEADER_SCAN_BYTES || ++blocks > 4096 || !has(i, i + 1)) return null;
        const n = at(i);
        i += 1 + n;
        if (n === 0) break;
      }
    }
    return null;
  }
  // WebP: its first chunk, the canvas of an extended one
  if (is(0, 'RIFF') && is(8, 'WEBP')) {
    // each chunk's size ends where it ends: a whole lossless 1 × 1 image is 28 bytes
    if (is(12, 'VP8X') && has(0, 30)) return [1 + u24le(24), 1 + u24le(27)];
    if (is(12, 'VP8L') && has(0, 25) && at(20) === 0x2f) return [1 + (at(21) | ((at(22) & 0x3f) << 8)), 1 + ((at(22) >> 6) | (at(23) << 2) | ((at(24) & 0x0f) << 10))];
    if (is(12, 'VP8 ') && has(0, 30)) return [(at(26) | (at(27) << 8)) & 0x3fff, (at(28) | (at(29) << 8)) & 0x3fff];
    return null;
  }
  // JPEG: the first frame header (SOFn) past the segments before it, in at most 1024 steps (an image takes tens: its
  // APPn and tables; a fill byte or a standalone marker is a step)
  if (is(0, '\xff\xd8') && has(0, 4)) {
    for (let i = 2, steps = 0; i + 9 <= HEADER_SCAN_BYTES && steps < 1024; steps++) {
      if (!has(i, i + 4) || at(i) !== 0xff) return null;
      const marker = at(i + 1);
      if (marker === 0xff) { i++; continue; }
      if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd8)) { i += 2; continue; }
      if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) return has(i, i + 9) ? [u16be(i + 7), u16be(i + 5)] : null;
      if (marker === 0xd9 || marker === 0xda) return null;
      i += 2 + u16be(i + 2);
    }
  }
  return null;
}

/** The last data: image checked, and the answer: elements in a row often show one image (a list's icon). */
let lastImage: { url: string; checked: string | null } | null = null;

/**
 * Returns the URL to use (normalized) if it is allowed for `kind`, else null.
 * Fragments are returned as-is. No relative URLs: there is nothing to resolve against.
 */
export function checkUrl(raw: string, kind: UrlKind): string | null {
  const url = normalize(String(raw));
  if (url.startsWith('#')) {
    // Fragment: must not contain anything that could re-open a URL context.
    return /^#[^\s"'()\\<>]*$/.test(url) ? url : null;
  }
  if (kind === 'fragment-only') return null;
  if (/^data:/i.test(url)) {
    const mime = dataMime(url);
    if (mime === null) return null;
    if (kind !== 'image' || !IMAGE_MIME.has(mime)) return null;
    // An image of the profile's formats of a size its bitmap can be held in; bytes of none of them are broken, as
    // the browser would find them — unless a decoder outside the profile takes them, which they never reach
    if (lastImage?.url === url) return lastImage.checked;
    const size = dataImageSize(url);
    const checked = !size ? BROKEN_IMAGE : size[0] * size[1] <= MAX_IMAGE_PIXELS ? url : null;
    lastImage = { url, checked };
    return checked;
  }
  if (/^blob:/i.test(url)) return hostBlobUrls.has(url) ? url : null;
  return null;
}

export function isFragment(raw: string): boolean {
  return normalize(String(raw)).startsWith('#');
}

/** 1×1 transparent placeholder used where an external image was stripped. */
export const PLACEHOLDER_IMAGE =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">' +
      '<rect width="48" height="48" fill="#8882"/><path d="M8 36l10-12 8 9 6-7 8 10z" fill="#8886"/>' +
      '<circle cx="33" cy="15" r="4" fill="#8886"/></svg>',
  );
