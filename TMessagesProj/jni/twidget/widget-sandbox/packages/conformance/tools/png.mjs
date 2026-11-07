// Minimal PNG reader/writer for the conformance tools: no dependencies, any
// Node ≥ 18. Reads 8-bit greyscale, grey+alpha, RGB, RGBA and palette images
// (what screenshot APIs write), non-interlaced; writes RGBA.

import zlib from 'node:zlib';

const SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

/** @returns {{ width: number, height: number, data: Buffer }} RGBA, 4 bytes per pixel */
export function decodePng(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 8 || !buf.subarray(0, 8).equals(SIGNATURE)) throw new Error('not a PNG file');
  let pos = 8, width = 0, height = 0, depth = 0, type = 0, interlace = 0;
  let palette = null, alpha = null;
  const idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos);
    const kind = buf.toString('latin1', pos + 4, pos + 8);
    const body = buf.subarray(pos + 8, pos + 8 + len);
    pos += 12 + len;
    if (kind === 'IHDR') {
      width = body.readUInt32BE(0); height = body.readUInt32BE(4);
      depth = body[8]; type = body[9]; interlace = body[12];
    } else if (kind === 'PLTE') palette = body;
    else if (kind === 'tRNS') alpha = body;
    else if (kind === 'IDAT') idat.push(body);
    else if (kind === 'IEND') break;
  }
  if (depth !== 8) throw new Error(`PNG bit depth ${depth} is not supported (8 only)`);
  if (interlace) throw new Error('interlaced PNG is not supported');
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type];
  if (!channels) throw new Error(`PNG colour type ${type} is not supported`);
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const pixels = Buffer.alloc(stride * height);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    const out = pixels.subarray(y * stride, (y + 1) * stride);
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? out[x - channels] : 0, b = prev[x], c = x >= channels ? prev[x - channels] : 0;
      let v = line[x];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      out[x] = v & 255;
    }
    prev = out;
  }
  const data = Buffer.alloc(width * height * 4);
  for (let i = 0, j = 0; i < width * height; i++, j += channels) {
    const o = i * 4;
    if (type === 6) { data[o] = pixels[j]; data[o + 1] = pixels[j + 1]; data[o + 2] = pixels[j + 2]; data[o + 3] = pixels[j + 3]; }
    else if (type === 2) { data[o] = pixels[j]; data[o + 1] = pixels[j + 1]; data[o + 2] = pixels[j + 2]; data[o + 3] = 255; }
    else if (type === 0) { data[o] = data[o + 1] = data[o + 2] = pixels[j]; data[o + 3] = 255; }
    else if (type === 4) { data[o] = data[o + 1] = data[o + 2] = pixels[j]; data[o + 3] = pixels[j + 1]; }
    else { const k = pixels[j]; data[o] = palette[k * 3]; data[o + 1] = palette[k * 3 + 1]; data[o + 2] = palette[k * 3 + 2]; data[o + 3] = alpha && k < alpha.length ? alpha[k] : 255; }
  }
  return { width, height, data };
}

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return t;
})();
function crc32(buf) { let c = 0xffffffff; for (const b of buf) c = CRC_TABLE[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(kind, body) {
  const len = Buffer.alloc(4); len.writeUInt32BE(body.length);
  const kb = Buffer.from(kind, 'latin1');
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([kb, body])));
  return Buffer.concat([len, kb, body, crc]);
}

/** @param {{ width: number, height: number, data: Buffer | Uint8Array }} img RGBA */
export function encodePng(img) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(img.width, 0); ihdr.writeUInt32BE(img.height, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  const stride = img.width * 4;
  const raw = Buffer.alloc((stride + 1) * img.height);
  for (let y = 0; y < img.height; y++) Buffer.from(img.data.buffer, img.data.byteOffset + y * stride, stride).copy(raw, y * (stride + 1) + 1);
  return Buffer.concat([SIGNATURE, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
