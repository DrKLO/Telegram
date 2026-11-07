import fs from 'node:fs';
import { PNG } from 'pngjs';
// Blitz experiment helper: the same crop from several renders, nearest-neighbour upscaled and stacked, to look at glyphs.
// usage: corepack pnpm tsx scripts/blitz-experiment/magnify.mts out.png scale x,y,w,h file1 file2 ...  → crops stacked, nearest-neighbour upscaled
const [out, scaleS, cropS, ...files] = process.argv.slice(2);
const scale = Number(scaleS); const [x, y, w, h] = cropS.split(',').map(Number);
const gap = 4;
const o = new PNG({ width: w * scale, height: (h * scale + gap) * files.length - gap }); o.data.fill(255);
files.forEach((f, k) => {
  const img = PNG.sync.read(fs.readFileSync(f));
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
    const si = ((y + j) * img.width + (x + i)) * 4; if (y + j >= img.height || x + i >= img.width) continue;
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) {
      const di = ((k * (h * scale + gap) + j * scale + dy) * o.width + i * scale + dx) * 4;
      // composite over white (premultiplied sources were saved straight by the harness)
      const a = img.data[si + 3] / 255;
      for (let c = 0; c < 3; c++) o.data[di + c] = Math.round(img.data[si + c] * a + 255 * (1 - a));
      o.data[di + 3] = 255;
    }
  }
});
fs.writeFileSync(out, PNG.sync.write(o));
