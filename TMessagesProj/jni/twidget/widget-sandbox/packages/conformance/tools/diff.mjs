// The pixel rule of the conformance set, shared by every runner and by
// report.mjs: a stage passes when the pixels that differ from the reference
// are at most `maxDiff` of its ink (pixels that are not the white stage
// background in either image), and, for stages that name colours, each colour
// is about as present as in the reference. No dependencies.
//
// The per-pixel comparison follows pixelmatch (YIQ colour distance, anti-
// aliased pixels ignored):
//   pixelmatch, ISC License, Copyright (c) 2019 Mapbox.
//   Permission to use, copy, modify, and/or distribute this software for any
//   purpose with or without fee is hereby granted, provided that the above
//   copyright notice and this permission notice appear in all copies.
//   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES.

/** Defaults of the rule. */
export const DIFF = { maxRatio: 0.05, threshold: 0.12 };

const blend = (c, a) => 255 + (c - 255) * a;
const yOf = (r, g, b) => r * 0.29889531 + g * 0.58662247 + b * 0.11448223;
const iOf = (r, g, b) => r * 0.59597799 - g * 0.2741761 - b * 0.32180189;
const qOf = (r, g, b) => r * 0.21147017 - g * 0.52261711 + b * 0.31114694;

function colorDelta(a, b, k, m, yOnly) {
  let r1 = a[k], g1 = a[k + 1], b1 = a[k + 2], a1 = a[k + 3];
  let r2 = b[m], g2 = b[m + 1], b2 = b[m + 2], a2 = b[m + 3];
  if (a1 === a2 && r1 === r2 && g1 === g2 && b1 === b2) return 0;
  if (a1 < 255) { a1 /= 255; r1 = blend(r1, a1); g1 = blend(g1, a1); b1 = blend(b1, a1); }
  if (a2 < 255) { a2 /= 255; r2 = blend(r2, a2); g2 = blend(g2, a2); b2 = blend(b2, a2); }
  const y1 = yOf(r1, g1, b1), y2 = yOf(r2, g2, b2), y = y1 - y2;
  if (yOnly) return y;
  const i = iOf(r1, g1, b1) - iOf(r2, g2, b2), q = qOf(r1, g1, b1) - qOf(r2, g2, b2);
  const delta = 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q;
  return y1 > y2 ? -delta : delta;
}

function manySiblings(img, x1, y1, w, h) {
  const x0 = Math.max(x1 - 1, 0), y0 = Math.max(y1 - 1, 0), x2 = Math.min(x1 + 1, w - 1), y2 = Math.min(y1 + 1, h - 1);
  const p = (y1 * w + x1) * 4;
  let zeroes = x1 === x0 || x1 === x2 || y1 === y0 || y1 === y2 ? 1 : 0;
  for (let x = x0; x <= x2; x++) for (let y = y0; y <= y2; y++) {
    if (x === x1 && y === y1) continue;
    const q = (y * w + x) * 4;
    if (img[p] === img[q] && img[p + 1] === img[q + 1] && img[p + 2] === img[q + 2] && img[p + 3] === img[q + 3]) zeroes++;
    if (zeroes > 2) return true;
  }
  return false;
}

function antialiased(img, x1, y1, w, h, other) {
  const x0 = Math.max(x1 - 1, 0), y0 = Math.max(y1 - 1, 0), x2 = Math.min(x1 + 1, w - 1), y2 = Math.min(y1 + 1, h - 1);
  const p = (y1 * w + x1) * 4;
  let zeroes = x1 === x0 || x1 === x2 || y1 === y0 || y1 === y2 ? 1 : 0;
  let min = 0, max = 0, minX = 0, minY = 0, maxX = 0, maxY = 0;
  for (let x = x0; x <= x2; x++) for (let y = y0; y <= y2; y++) {
    if (x === x1 && y === y1) continue;
    const d = colorDelta(img, img, p, (y * w + x) * 4, true);
    if (d === 0) { if (++zeroes > 2) return false; }
    else if (d < min) { min = d; minX = x; minY = y; }
    else if (d > max) { max = d; maxX = x; maxY = y; }
  }
  if (min === 0 || max === 0) return false;
  return (manySiblings(img, minX, minY, w, h) && manySiblings(other, minX, minY, w, h)) || (manySiblings(img, maxX, maxY, w, h) && manySiblings(other, maxX, maxY, w, h));
}

/** Pad to w×h with white (images of different sizes compare over the larger box). */
function padTo(img, w, h) {
  if (img.width === w && img.height === h) return img.data;
  const out = Buffer.alloc(w * h * 4, 255);
  for (let y = 0; y < img.height; y++) Buffer.from(img.data.buffer, img.data.byteOffset + y * img.width * 4, img.width * 4).copy(out, y * w * 4);
  return out;
}

/**
 * Differing pixels as a share of the ink and of the area.
 * @param {{width:number,height:number,data:Uint8Array}} a  the rendering
 * @param {{width:number,height:number,data:Uint8Array}} b  the reference
 * @returns {{ ink: number, area: number, image: {width:number,height:number,data:Buffer} }}
 */
export function diffStats(a, b, threshold = DIFF.threshold) {
  const w = Math.max(a.width, b.width), h = Math.max(a.height, b.height);
  const A = padTo(a, w, h), B = padTo(b, w, h);
  const out = Buffer.alloc(w * h * 4, 255);
  const maxDelta = 35215 * threshold * threshold;
  const inked = (d, i) => d[i + 3] > 16 && (d[i] < 240 || d[i + 1] < 240 || d[i + 2] < 240);
  let n = 0, ink = 0;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const p = (y * w + x) * 4;
    if (inked(A, p) || inked(B, p)) ink++;
    const d = colorDelta(A, B, p, p, false);
    if (Math.abs(d) > maxDelta) {
      if (antialiased(A, x, y, w, h, B) || antialiased(B, x, y, w, h, A)) { out[p] = 255; out[p + 1] = 255; out[p + 2] = 0; }
      else { n++; out[p] = 255; out[p + 1] = 0; out[p + 2] = 0; }
    } else { const v = 255 - ((255 - yOf(B[p], B[p + 1], B[p + 2])) * 0.1); out[p] = out[p + 1] = out[p + 2] = v; }
  }
  return { ink: n / Math.max(ink, 1), area: n / (w * h), image: { width: w, height: h, data: out } };
}

/** Pixels within a small distance of a #rgb / #rrggbb colour. */
export function colorCount(img, hex, tol = 60) {
  const h = hex.replace('#', '');
  const f = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  const r = parseInt(f.slice(0, 2), 16), g = parseInt(f.slice(2, 4), 16), b = parseInt(f.slice(4, 6), 16);
  let n = 0;
  for (let i = 0; i < img.data.length; i += 4) if (Math.abs(img.data[i] - r) + Math.abs(img.data[i + 1] - g) + Math.abs(img.data[i + 2] - b) < tol) n++;
  return n;
}

/** Each colour about as present as in the reference (0.4×–2.5×; absent stays absent). */
export function colorsMatch(got, ref, colors) {
  const parts = [];
  let ok = true;
  for (const c of colors) {
    const want = colorCount(ref, c), have = colorCount(got, c);
    const good = want < 20 ? have < 20 + want : have >= 0.4 * want && have <= 2.5 * want;
    ok &&= good;
    parts.push(`${c} ${have}/${want}px`);
  }
  return { ok, detail: parts.join(', ') };
}

/**
 * Judge one stage crop against its references (the better engine wins).
 * @param {{width:number,height:number,data:Uint8Array}} got
 * @param {{engine:string, png:{width:number,height:number,data:Uint8Array}}[]} refs
 * @param {{maxDiff?: number, colors?: string[], xfail?: string}} stage
 */
export function judgeStage(got, refs, stage) {
  const maxDiff = stage.maxDiff ?? DIFF.maxRatio;
  let best = null;
  for (const r of refs) {
    const d = diffStats(got, r.png);
    const colors = stage.colors?.length ? colorsMatch(got, r.png, stage.colors) : null;
    const ok = d.ink <= maxDiff && (!colors || colors.ok);
    const score = d.ink + (colors && !colors.ok ? 1 : 0);
    if (!best || score < best.score) best = { score, ok, diff: d.ink, ref: r.engine, colors, image: d.image };
  }
  if (!best) return { status: 'skip', diff: null, ref: null, maxDiff, detail: 'no reference', image: null };
  const status = stage.xfail ? (best.ok ? 'xpass' : 'xfail') : best.ok ? 'pass' : 'fail';
  return { status, diff: Math.round(best.diff * 10000) / 10000, ref: best.ref, maxDiff, detail: [best.colors?.detail, stage.xfail].filter(Boolean).join('; ') || undefined, image: best.image };
}
