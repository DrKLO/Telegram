// Drives platform/web/painter.ts against recording fakes of the 2D canvas API, without a browser:
// `npx tsx Tests/TwidgetCoreTests/web_painter_test.ts` (Node 18 or later).
let failures = 0;
function check(ok: boolean, what: string) {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${what}`);
  if (!ok) failures++;
}

class FakeMatrix {
  constructor(public a = 1, public b = 0, public c = 0, public d = 1, public e = 0, public f = 0) {}
  get isIdentity() { return this.a === 1 && this.b === 0 && this.c === 0 && this.d === 1 && this.e === 0 && this.f === 0; }
  multiply(n: FakeMatrix) {
    return new FakeMatrix(this.a * n.a + this.c * n.b, this.b * n.a + this.d * n.b, this.a * n.c + this.c * n.d, this.b * n.c + this.d * n.d,
      this.a * n.e + this.c * n.f + this.e, this.b * n.e + this.d * n.f + this.f);
  }
  translate(x: number, y: number) { return this.multiply(new FakeMatrix(1, 0, 0, 1, x, y)); }
  scale(x: number, y: number) { return this.multiply(new FakeMatrix(x, 0, 0, y, 0, 0)); }
  inverse() {
    const det = this.a * this.d - this.b * this.c;
    return new FakeMatrix(this.d / det, -this.b / det, -this.c / det, this.a / det, (this.c * this.f - this.d * this.e) / det, (this.b * this.e - this.a * this.f) / det);
  }
}
(globalThis as any).DOMMatrix = class extends FakeMatrix {
  constructor(m?: number[]) { super(...(m ?? [1, 0, 0, 1, 0, 0])); }
};

class FakePath {
  calls: string[] = [];
  moveTo() { this.calls.push('M'); }
  lineTo() { this.calls.push('L'); }
  quadraticCurveTo() { this.calls.push('Q'); }
  bezierCurveTo() { this.calls.push('C'); }
  closePath() { this.calls.push('Z'); }
  rect() { this.calls.push('rect'); }
  roundRect() { this.calls.push('roundRect'); }
  addPath(p: FakePath) { this.calls.push(...p.calls); }
}
(globalThis as any).Path2D = FakePath;

const surfaces: FakeSurface[] = [];
class FakeSurface {
  ctx: FakeCtx;
  constructor(public width: number, public height: number) {
    this.ctx = new FakeCtx(this);
    surfaces.push(this);
  }
  getContext() { return this.ctx; }
}
(globalThis as any).OffscreenCanvas = FakeSurface;

class FakeCtx {
  log: string[] = [];
  depth = 0;
  minDepth = 0;
  current = new FakeMatrix();
  saved: FakeMatrix[] = [];
  globalAlpha = 1;
  globalCompositeOperation = 'source-over';
  fillStyle: unknown = '';
  strokeStyle: unknown = '';
  shadowOffsetX = 0;
  shadowOffsetY = 0;
  shadowBlur = 0;
  shadowColor = '';
  lineWidth = 1; lineCap = 'butt'; lineJoin = 'miter'; miterLimit = 10; lineDashOffset = 0; font = ''; textBaseline = ''; textAlign = ''; imageSmoothingEnabled = true;
  constructor(public canvas: FakeSurface) {}
  save() { this.depth++; this.saved.push(this.current); }
  restore() { this.depth--; this.minDepth = Math.min(this.minDepth, this.depth); this.current = this.saved.pop() ?? new FakeMatrix(); }
  setTransform(a: number | FakeMatrix, b?: number, c?: number, d?: number, e?: number, f?: number) {
    this.current = typeof a === 'number' ? new FakeMatrix(a, b, c, d, e, f) : new FakeMatrix(a.a, a.b, a.c, a.d, a.e, a.f);
  }
  getTransform() { return this.current; }
  transform(a: number, b: number, c: number, d: number, e: number, f: number) { this.current = this.current.multiply(new FakeMatrix(a, b, c, d, e, f)); }
  translate(x: number, y: number) { this.current = this.current.translate(x, y); }
  scale(x: number, y: number) { this.current = this.current.scale(x, y); }
  clip(p?: unknown, rule?: string) { this.log.push(`clip:${rule ?? 'nonzero'}`); }
  fill(p?: unknown, rule?: string) { this.log.push(`fill:${String(this.fillStyle)}:${rule ?? 'nonzero'}:${this.shadowBlur}`); }
  stroke() { this.log.push(`stroke:${String(this.strokeStyle)}:${this.lineWidth}`); }
  fillRect() { this.log.push('fillRect'); }
  clearRect() { this.log.push('clearRect'); }
  beginPath() {}
  rect() {}
  ellipse() {}
  fillText(t: string) { this.log.push(`text:${t}`); }
  strokeText(t: string) { this.log.push(`strokeText:${t}`); }
  setLineDash(d: number[]) { this.log.push(`dash:${d.join(',')}`); }
  drawImage(img: unknown) { this.log.push(`drawImage:${img === this.canvas ? 'self' : surfaces.indexOf(img as FakeSurface)}@${this.globalAlpha}`); }
  createLinearGradient() { return { addColorStop() {}, toString: () => 'linear' }; }
  createRadialGradient() { return { addColorStop() {}, toString: () => 'radial' }; }
  createConicGradient() { return { addColorStop() {}, toString: () => 'conic' }; }
  getImageData(x: number, y: number, w: number, h: number) { return { data: new Uint8ClampedArray(w * h * 4).fill(9) }; }
  putImageData(d: { data: Uint8ClampedArray }) { this.log.push(`put:${d.data.length}`); }
}
(globalThis as any).ImageData = class { constructor(public data: Uint8ClampedArray, public width: number, public height: number) {} };

async function main() {
  const { DisplayListPainter, CanvasBitmaps } = await import('../../Sources/TwidgetCore/platform/web/painter.ts');
  const root = new FakeSurface(100, 100);
  const ctx = root.ctx as any;
  const res = { font: () => '10px sans-serif', image: () => undefined, canvas: () => undefined };
  const red = 0xffff0000;
  new DisplayListPainter().paint(ctx, [
    { k: 9, r: [0, 0, 50, 50] },
    { k: 12, m: [1, 0, 0, 1, 10, 0], o: 0.5, r: [0, 0, 10, 10] },
    { k: 0, r: [0, 0, 10, 10], c: red },
    { k: 0, r: [5, 5, 10, 10], c: red },
    { k: 13 },
    { k: 10 },
    { k: 9, p: { o: [0, 1, 1, 4], v: [0, 0, 10, 0, 0, 10], e: 1 } },
    { k: 11, p: { o: [0, 1], v: [0, 0, 5, 5], f: red, sc: 0xff0000ff, sw: 2, d: [3, 1] } },
    { k: 10 },
    { k: 14, r: [10, 10, 20, 20], c: 0x80000000, a: [0, 2, 6, 0] },
  ] as any, res);
  const layer = surfaces[1];
  check(layer !== undefined && layer.ctx.log.filter((l) => l.startsWith('fill:')).length === 2, 'an opacity group draws its children into its own surface');
  check(ctx.log.includes('drawImage:1@0.5'), 'the group is composited once, at its opacity');
  check(ctx.depth === 0 && ctx.minDepth === 0, 'clips and groups unwind to the starting state');
  check(ctx.log.includes('clip:evenodd'), 'a clip path keeps its fill rule');
  check(ctx.log.some((l) => l.startsWith('fill:rgba(255,0,0,1)')) && ctx.log.includes('dash:3,1') && ctx.log.some((l) => l.startsWith('stroke:rgba(0,0,255,1):2')), 'an SVG path fills, then strokes dashed');
  check(ctx.log.some((l) => l.startsWith('fill:#000:nonzero:6')), 'a box shadow draws with its blur');

  const partial = new FakeSurface(100, 100).ctx as any;
  new DisplayListPainter().paint(partial, [{ k: 0, r: [0, 0, 10, 10], c: red }] as any, res, [[2.5, 3, 4, 4]]);
  check(partial.log[0] === 'clearRect' && partial.log.includes('clip:nonzero') && partial.depth === 0, 'damage clears and clips its areas, and unwinds');
  const nothing = new FakeSurface(100, 100).ctx as any;
  new DisplayListPainter().paint(nothing, [{ k: 0, r: [0, 0, 10, 10], c: red }] as any, res, []);
  check(nothing.log.length === 0, 'no damage paints nothing');

  const bitmaps = new CanvasBitmaps(res);
  bitmaps.run(7, 20, 10, JSON.stringify([
    { k: 0 },
    { k: 1, o: [0, 1, 1], v: [0, 0, 10, 0, 10, 10], m: [2, 0, 0, 2, 0, 0], pk: 1, pa: [0, 0, 1, 0, 0, 0], s: [0, 0xff000000, 1, 0xffffffff], cm: 11, a: 0.5 },
    { k: 6, x: 1, y: 1, w: 1, h: 1, px: 'AQIDBA==' },
    { k: 5, cv: 7, ir: [0, 0, -1, -1, 0, 0, 20, 10] },
  ]));
  const canvas = surfaces[surfaces.length - 2];
  check(canvas.width === 20 && canvas.height === 10 && canvas.ctx.log[0] === 'clearRect', 'a canvas runs on a bitmap of its size, cleared first');
  check(canvas.ctx.log.includes('fill:linear:nonzero:0'), 'a gradient fill is drawn');
  check(canvas.ctx.log.includes('put:4'), 'putImageData writes its pixels');
  check(canvas.ctx.log.some((l) => l.startsWith('drawImage:') && !l.startsWith('drawImage:self')), 'drawing a canvas onto itself reads a copy');
  check(bitmaps.read(7, 0, 0, 2, 2)?.length === 16, 'getImageData reads RGBA');

  console.log(`${failures} failure(s)`);
  process.exit(failures ? 1 : 0);
}
main();
