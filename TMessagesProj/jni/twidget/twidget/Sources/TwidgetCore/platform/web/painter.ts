// The web side of TwidgetCore's paint: the display list (`to_json` in src/display_list.h) drawn on a
// 2D context, and canvas bitmaps that run canvas ops (`to_json` in src/canvas_ops.h). The wasm glue
// (wasm.cpp) calls `canvasRun`, `canvasRead` and `canvasRelease` on the backend object a host
// registers; a host forwards those to a `CanvasBitmaps` and paints with a `DisplayListPainter`.

/** A path as the JSON writes it: op codes (0 move, 1 line, 2 quad, 3 cubic, 4 close) and their points. */
export interface PathJson {
  o: number[];
  v: number[];
  e?: number;
}

/** An SVG path of the fork (Cmd::Path). */
export interface SvgPathJson extends PathJson {
  f?: number;
  g?: number;
  ga?: number[];
  s?: number[];
  sc?: number;
  sw?: number;
  lc?: number;
  lj?: number;
  ml?: number;
  d?: number[];
  do?: number;
}

/** One display-list command. */
export interface Cmd {
  k: number;
  r?: number[];
  q?: number[];
  l?: number[];
  c?: number;
  w?: number;
  f?: number;
  t?: string;
  x?: number;
  y?: number;
  i?: number;
  cv?: number;
  a?: number[];
  s?: number[];
  p?: SvgPathJson;
  m?: number[];
  o?: number;
  h?: number;
  n?: number;
  /** FilterPush, Backdrop: [kind, amount, x, y, argb] per function (display_list.h's Filter). */
  fl?: number[][];
}

/** One canvas operation. */
export interface CanvasOpJson extends Partial<PathJson> {
  k: number;
  m?: number[];
  a?: number;
  cm?: number;
  shc?: number;
  sh?: number[];
  cl?: PathJson[];
  pk?: number;
  c?: number;
  pa?: number[];
  s?: number[];
  lw?: number;
  lc?: number;
  lj?: number;
  ml?: number;
  d?: number[];
  do?: number;
  f?: number;
  t?: string;
  tp?: number[];
  st?: number;
  i?: number;
  cv?: number;
  ir?: number[];
  ns?: number;
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  px?: string;
}

/** Cmd kinds, CanvasOp kinds and Composite values, in the order of the C++ enums. */
export const enum K { FillRect, StrokeRect, FillEllipse, StrokeEllipse, Text, Image, Linear, Radial, Conic, ClipPush, ClipPop, Path, GroupPush, GroupPop, Shadow, FilterPush, FilterPop, Backdrop }
export const enum Op { Reset, Fill, Stroke, Clear, Text, Image, PutImage }
const COMPOSITE: GlobalCompositeOperation[] = [
  'source-over', 'source-in', 'source-out', 'source-atop', 'destination-over', 'destination-in', 'destination-out', 'destination-atop',
  'lighter', 'copy', 'xor', 'multiply', 'screen', 'overlay', 'darken', 'lighten', 'color-dodge', 'color-burn', 'hard-light', 'soft-light',
  'difference', 'exclusion', 'hue', 'saturation', 'color', 'luminosity',
];
const CAPS: CanvasLineCap[] = ['butt', 'round', 'square'];
const JOINS: CanvasLineJoin[] = ['miter', 'round', 'bevel'];

type Ctx = CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D;
type Surface = HTMLCanvasElement | OffscreenCanvas;

/** Fonts and images the painter draws with, owned by the host's backend. */
export interface PaintResources {
  font(id: number): string | undefined;
  image(id: number): CanvasImageSource | undefined;
  canvas(id: number): CanvasImageSource | undefined;
}

export function color(argb: number): string {
  const a = (argb >>> 24) & 255, r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
  return `rgba(${r},${g},${b},${a / 255})`;
}

/** A filter chain as the CSS the canvas's own `filter` takes; lengths times `scale`, canvas px per CSS px. */
export function filterCss(fl: number[][], scale: number): string {
  const names = ['blur', 'brightness', 'contrast', 'grayscale', 'hue-rotate', 'invert', 'opacity', 'saturate', 'sepia'];
  return fl.map(([kind, amount, x, y, argb]) => {
    if (kind === 0) return `blur(${amount * scale}px)`;
    if (kind === 4) return `hue-rotate(${amount}deg)`;
    if (kind === 9) return `drop-shadow(${x * scale}px ${y * scale}px ${amount * scale}px ${color(argb)})`;
    return names[kind] ? `${names[kind]}(${amount})` : '';
  }).join(' ') || 'none';
}

export function path2d(p: PathJson, into = new Path2D()): Path2D {
  const v = p.v;
  let i = 0;
  for (const op of p.o) {
    const need = op === 3 ? 6 : op === 2 ? 4 : op === 4 ? 0 : 2;
    if (i + need > v.length) break;
    switch (op) {
      case 0: into.moveTo(v[i], v[i + 1]); break;
      case 1: into.lineTo(v[i], v[i + 1]); break;
      case 2: into.quadraticCurveTo(v[i], v[i + 1], v[i + 2], v[i + 3]); break;
      case 3: into.bezierCurveTo(v[i], v[i + 1], v[i + 2], v[i + 3], v[i + 4], v[i + 5]); break;
      case 4: into.closePath(); break;
    }
    i += need;
  }
  return into;
}

function makeSurface(w: number, h: number): Surface {
  if (typeof OffscreenCanvas !== 'undefined') return new OffscreenCanvas(w, h);
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

function rounded(r: number[], q?: number[], into = new Path2D()): Path2D {
  if (q) into.roundRect(r[0], r[1], r[2], r[3], [{ x: q[0], y: q[1] }, { x: q[2], y: q[3] }, { x: q[4], y: q[5] }, { x: q[6], y: q[7] }]);
  else into.rect(r[0], r[1], r[2], r[3]);
  return into;
}

function addStops(g: CanvasGradient, s: number[]) {
  for (let i = 0; i + 1 < s.length; i += 2) g.addColorStop(Math.min(1, Math.max(0, s[i])), color(s[i + 1]));
}

/** Executes display lists. Clips and groups nest in paint order and unwind together, as on every platform. */
export class DisplayListPainter {
  /**
   * Paints `list` on `ctx`, already scaled to CSS px. With `damage` (twl_damage, CSS px), only those
   * areas are cleared and painted again; the rest of the canvas is kept. `null` paints everything.
   */
  paint(root: Ctx, list: Cmd[], res: PaintResources, damage: number[][] | null = null) {
    if (damage) {
      if (!damage.length) return;
      root.save();
      root.beginPath();
      for (const d of damage) {
        // Whole CSS px, one more around, for the antialiased edges.
        const x = Math.floor(d[0]) - 1, y = Math.floor(d[1]) - 1, w = Math.ceil(d[0] + d[2]) + 1 - x, h = Math.ceil(d[1] + d[3]) + 1 - y;
        root.clearRect(x, y, w, h);
        root.rect(x, y, w, h);
      }
      root.clip();
    }
    // A group with opacity draws into its own surface, composited once at its end: canvas 2D has no
    // transparency layer, and an alpha per primitive would darken where children overlap.
    type Frame = { kind: 'save' } | { kind: 'layer'; parent: Ctx; surface: Surface; alpha: number; filter?: string };
    const stack: Frame[] = [];
    let ctx: Ctx = root;
    const pop = () => {
      const f = stack.pop();
      if (!f) return;
      if (f.kind === 'save') { ctx.restore(); return; }
      ctx = f.parent;
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.globalAlpha = f.alpha;
      if (f.filter) ctx.filter = f.filter;
      ctx.drawImage(f.surface, 0, 0);
      ctx.restore();
      ctx.restore();
    };
    const clipRect = (c: Cmd) => { if (c.l) { ctx.beginPath(); ctx.rect(c.l[0], c.l[1], c.l[2], c.l[3]); ctx.clip(); } };

    for (const c of list) {
      switch (c.k) {
        case K.FillRect:
          ctx.save();
          clipRect(c);
          ctx.fillStyle = color(c.c!);
          ctx.fill(rounded(c.r!, c.q));
          ctx.restore();
          break;
        case K.StrokeRect:
          ctx.strokeStyle = color(c.c!);
          ctx.lineWidth = c.w ?? 1;
          ctx.stroke(rounded(c.r!, c.q));
          break;
        case K.FillEllipse:
        case K.StrokeEllipse: {
          const [x, y, w, h] = c.r!;
          const lw = c.w ?? 1;
          const inset = c.k === K.StrokeEllipse ? lw / 2 : 0;
          ctx.beginPath();
          ctx.ellipse(x + w / 2, y + h / 2, Math.max(0, w / 2 - inset), Math.max(0, h / 2 - inset), 0, 0, Math.PI * 2);
          if (c.k === K.FillEllipse) { ctx.fillStyle = color(c.c!); ctx.fill(); } else { ctx.strokeStyle = color(c.c!); ctx.lineWidth = lw; ctx.stroke(); }
          break;
        }
        case K.Text: {
          const css = res.font(c.f!);
          if (!css) break;
          ctx.font = css;
          ctx.fillStyle = color(c.c!);
          ctx.textBaseline = 'alphabetic';
          ctx.fillText(c.t!, c.x!, c.y!);
          break;
        }
        case K.Image: {
          const img = c.cv ? res.canvas(c.cv) : res.image(c.i!);
          if (!img) break;
          ctx.save();
          clipRect(c);
          if (c.q) ctx.clip(rounded(c.l ?? c.r!, c.q));
          ctx.drawImage(img, c.r![0], c.r![1], c.r![2], c.r![3]);
          ctx.restore();
          break;
        }
        case K.Linear: case K.Radial: case K.Conic: {
          const a = c.a!, s = c.s ?? [];
          if (s.length < 2) break;
          ctx.save();
          clipRect(c);
          ctx.clip(rounded(c.r!, c.q));
          let g: CanvasGradient;
          if (c.k === K.Linear) g = ctx.createLinearGradient(a[0], a[1], a[2], a[3]);
          else if (c.k === K.Radial) {
            const rx = Math.max(a[2], 0.01), ry = Math.max(a[3], 0.01);
            g = ctx.createRadialGradient(a[0], a[1], 0, a[0], a[1], rx);
            ctx.translate(a[0], a[1]); ctx.scale(1, ry / rx); ctx.translate(-a[0], -a[1]);
          } else g = ctx.createConicGradient(((a[2] - 90) * Math.PI) / 180, a[0], a[1]);
          addStops(g, s);
          ctx.fillStyle = g;
          const r = c.r!;
          ctx.fillRect(r[0] - 4000, r[1] - 4000, r[2] + 8000, r[3] + 8000);
          ctx.restore();
          break;
        }
        case K.ClipPush:
          ctx.save();
          stack.push({ kind: 'save' });
          if (c.p) ctx.clip(path2d(c.p), c.p.e ? 'evenodd' : 'nonzero');
          else ctx.clip(rounded(c.r!, c.q));
          break;
        case K.ClipPop:
        case K.GroupPop:
          pop();
          break;
        case K.GroupPush: {
          const m = c.m!;
          ctx.save();
          ctx.transform(m[0], m[1], m[2], m[3], m[4], m[5]);
          if (c.h) {
            ctx.beginPath();
            ctx.rect(0, 0, 0, 0);
            ctx.clip();
            stack.push({ kind: 'save' });
          } else if ((c.o ?? 1) < 1) {
            const surface = makeSurface(ctx.canvas.width, ctx.canvas.height);
            const inner = surface.getContext('2d') as Ctx;
            inner.setTransform(ctx.getTransform());
            stack.push({ kind: 'layer', parent: ctx, surface, alpha: Math.max(0, c.o ?? 1) });
            ctx = inner;
          } else {
            stack.push({ kind: 'save' });
          }
          break;
        }
        case K.FilterPush: {
          // What the element paints, on a surface of its own, drawn through the filters at FilterPop.
          ctx.save();
          if (!c.fl?.length) {
            stack.push({ kind: 'save' });
            break;
          }
          const t = ctx.getTransform();
          const surface = makeSurface(ctx.canvas.width, ctx.canvas.height);
          const inner = surface.getContext('2d') as Ctx;
          inner.setTransform(t);
          stack.push({ kind: 'layer', parent: ctx, surface, alpha: 1, filter: filterCss(c.fl, Math.hypot(t.a, t.b)) });
          ctx = inner;
          break;
        }
        case K.FilterPop:
          pop();
          break;
        case K.Backdrop: {
          // What is painted so far, filtered, back inside the box.
          if (!c.fl?.length || !c.r) break;
          const t = ctx.getTransform();
          const copy = makeSurface(ctx.canvas.width, ctx.canvas.height);
          (copy.getContext('2d') as Ctx).drawImage(ctx.canvas, 0, 0);
          ctx.save();
          ctx.clip(rounded(c.r, c.q));
          ctx.setTransform(1, 0, 0, 1, 0, 0);
          ctx.filter = filterCss(c.fl, Math.hypot(t.a, t.b));
          ctx.globalCompositeOperation = 'copy';
          ctx.drawImage(copy, 0, 0);
          ctx.restore();
          break;
        }
        case K.Path:
          if (c.p) DisplayListPainter.svgPath(ctx, c.p);
          break;
        case K.Shadow:
          DisplayListPainter.shadow(ctx, c);
          break;
      }
    }
    while (stack.length) pop();
    if (damage) root.restore();
  }

  private static svgPath(ctx: Ctx, p: SvgPathJson) {
    const shape = path2d(p);
    const rule: CanvasFillRule = p.e ? 'evenodd' : 'nonzero';
    if (p.g && p.s && p.ga) {
      const a = p.ga;
      const g = p.g === 1 ? ctx.createLinearGradient(a[0], a[1], a[2], a[3]) : ctx.createRadialGradient(a[5], a[6], 0, a[0], a[1], a[4]);
      addStops(g, p.s);
      ctx.fillStyle = g;
      ctx.fill(shape, rule);
    } else if (p.f !== undefined && (p.f >>> 24) > 0) {
      ctx.fillStyle = color(p.f);
      ctx.fill(shape, rule);
    }
    if (p.sc !== undefined && (p.sc >>> 24) > 0 && (p.sw ?? 0) > 0) {
      ctx.save();
      ctx.strokeStyle = color(p.sc);
      ctx.lineWidth = p.sw!;
      ctx.lineCap = CAPS[p.lc ?? 0] ?? 'butt';
      ctx.lineJoin = JOINS[p.lj ?? 0] ?? 'miter';
      ctx.miterLimit = p.ml ?? 4;
      ctx.setLineDash(p.d ?? []);
      ctx.lineDashOffset = p.do ?? 0;
      ctx.stroke(shape);
      ctx.restore();
    }
  }

  /**
   * CSS box-shadow as the other platforms draw it: the shape is filled far outside the clip and only its
   * shadow, offset back, lands by the box. Canvas shadows ignore the transform, so offset and blur are
   * mapped through it by hand.
   */
  private static shadow(ctx: Ctx, c: Cmd) {
    const r = c.r!, a = c.a ?? [0, 0, 0, 0];
    const [dx, dy] = a, blur = Math.max(a[2], 0), spread = a[3];
    if (r[2] <= 0 || r[3] <= 0 || ((c.c ?? 0) >>> 24) === 0) return;
    const reach = blur + Math.abs(spread) + Math.max(Math.abs(dx), Math.abs(dy)) + 2;
    const grow = (q: number[] | undefined, by: number) => q?.map((v) => Math.max(v + by, 0));
    const inset = !!c.n;
    const shape = inset ? [r[0] + spread, r[1] + spread, r[2] - spread * 2, r[3] - spread * 2] : [r[0] - spread, r[1] - spread, r[2] + spread * 2, r[3] + spread * 2];
    const shapeRadii = inset ? grow(c.q, -spread) : c.q ? grow(c.q, spread) : undefined;
    const far = r[2] + reach * 6 + 1000;

    ctx.save();
    const box = rounded(r, c.q);
    const fill = new Path2D();
    if (inset) {
      ctx.clip(box);
      fill.rect(r[0] - reach * 2 + far, r[1] - reach * 2, r[2] + reach * 4, r[3] + reach * 4);
      if (shape[2] > 0 && shape[3] > 0) rounded([shape[0] + far, shape[1], shape[2], shape[3]], shapeRadii, fill);
    } else {
      const outside = new Path2D();
      outside.rect(r[0] - reach - 1, r[1] - reach - 1, r[2] + reach * 2 + 2, r[3] + reach * 2 + 2);
      outside.addPath(box);
      ctx.clip(outside, 'evenodd');
      if (shape[2] <= 0 || shape[3] <= 0) { ctx.restore(); return; }
      rounded([shape[0] + far, shape[1], shape[2], shape[3]], shapeRadii, fill);
    }
    const t = ctx.getTransform();
    const ox = dx - far, oy = dy;
    ctx.shadowOffsetX = t.a * ox + t.c * oy;
    ctx.shadowOffsetY = t.b * ox + t.d * oy;
    ctx.shadowBlur = blur * Math.sqrt(Math.abs(t.a * t.d - t.b * t.c));
    ctx.shadowColor = color(c.c!);
    ctx.fillStyle = '#000';
    ctx.fill(fill, inset ? 'evenodd' : 'nonzero');
    ctx.restore();
  }
}

/** Canvas bitmaps for `<canvas>` elements, run from canvas op batches. */
export class CanvasBitmaps implements Pick<PaintResources, 'canvas'> {
  private readonly bitmaps = new Map<number, Surface>();

  constructor(private readonly res: Pick<PaintResources, 'font' | 'image'>) {}

  canvas(id: number): CanvasImageSource | undefined {
    return this.bitmaps.get(id);
  }

  /** wasm → `canvasRun(id, w, h, json)`: a w × h bitmap, created on the first call, runs the ops. */
  run(id: number, w: number, h: number, json: string) {
    let surface = this.bitmaps.get(id);
    if (w <= 0 || h <= 0) { this.release(id); return; }
    if (!surface || surface.width !== w || surface.height !== h) {
      surface = makeSurface(w, h);
      this.bitmaps.set(id, surface);
    }
    const ctx = surface.getContext('2d') as Ctx;
    let ops: CanvasOpJson[];
    try { ops = JSON.parse(json); } catch { return; }
    for (const op of ops) this.op(ctx, id, op);
  }

  /** wasm → `canvasRead`: straight RGBA, transparent outside the bitmap. */
  read(id: number, x: number, y: number, w: number, h: number): Uint8Array | null {
    const surface = this.bitmaps.get(id);
    if (!surface || w <= 0 || h <= 0) return new Uint8Array(Math.max(w, 0) * Math.max(h, 0) * 4);
    const ctx = surface.getContext('2d') as Ctx;
    // getImageData is straight alpha already, and transparent outside the bitmap.
    return new Uint8Array(ctx.getImageData(x, y, w, h).data.buffer);
  }

  release(id: number) {
    this.bitmaps.delete(id);
  }

  private op(ctx: Ctx, id: number, op: CanvasOpJson) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    if (op.k === Op.Reset) { ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height); return; }
    if (op.k === Op.PutImage) {
      if (!op.px || !op.w || !op.h) return;
      const bytes = Uint8ClampedArray.from(atob(op.px), (ch) => ch.charCodeAt(0));
      if (bytes.length !== op.w * op.h * 4) return;
      ctx.putImageData(new ImageData(bytes, op.w, op.h), op.x ?? 0, op.y ?? 0);
      return;
    }
    ctx.save();
    for (const clip of op.cl ?? []) ctx.clip(path2d(clip), clip.e ? 'evenodd' : 'nonzero');
    if (op.k === Op.Clear) {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.fillStyle = '#000';
      ctx.fill(path2d(op as PathJson));
      ctx.restore();
      return;
    }
    ctx.globalAlpha = op.a ?? 1;
    ctx.globalCompositeOperation = COMPOSITE[op.cm ?? 0] ?? 'source-over';
    if (op.shc !== undefined && op.sh) {
      // Canvas px already: the ops' shadows are not under a transform.
      ctx.shadowColor = color(op.shc);
      ctx.shadowOffsetX = op.sh[0];
      ctx.shadowOffsetY = op.sh[1];
      ctx.shadowBlur = op.sh[2];
    }
    const m = op.m ?? [1, 0, 0, 1, 0, 0];
    const user = new DOMMatrix([m[0], m[1], m[2], m[3], m[4], m[5]]);
    const paint = (): string | CanvasGradient => {
      if (!op.pk || !op.pa || !op.s) return color(op.c ?? 0xff000000);
      const a = op.pa;
      const g = op.pk === 1 ? ctx.createLinearGradient(a[0], a[1], a[2], a[3])
        : op.pk === 2 ? ctx.createRadialGradient(a[0], a[1], a[2], a[3], a[4], a[5])
        : ctx.createConicGradient(a[0], a[1], a[2]);
      addStops(g, op.s);
      return g;
    };
    const lineStyle = () => {
      ctx.lineWidth = op.lw ?? 1;
      ctx.lineCap = CAPS[op.lc ?? 0] ?? 'butt';
      ctx.lineJoin = JOINS[op.lj ?? 0] ?? 'miter';
      ctx.miterLimit = op.ml ?? 10;
      ctx.setLineDash(op.d ?? []);
      ctx.lineDashOffset = op.do ?? 0;
    };
    switch (op.k) {
      case Op.Fill:
      case Op.Stroke: {
        // The path is in canvas px; paints and line styles are in user space, so draw it back there.
        if (!user.isIdentity && Math.abs(user.a * user.d - user.b * user.c) < 1e-12) break;
        const shape = new Path2D();
        shape.addPath(path2d(op as PathJson), user.inverse());
        ctx.setTransform(user);
        if (op.k === Op.Fill) {
          ctx.fillStyle = paint();
          ctx.fill(shape, op.e ? 'evenodd' : 'nonzero');
        } else {
          ctx.strokeStyle = paint();
          lineStyle();
          ctx.stroke(shape);
        }
        break;
      }
      case Op.Text: {
        const css = this.res.font(op.f ?? 0);
        const tp = op.tp ?? [0, 0, 1, 0];
        if (!css || !op.t) break;
        ctx.setTransform(user.translate(tp[0], tp[1]).scale(tp[2], 1));
        ctx.font = css;
        ctx.textBaseline = 'alphabetic';
        ctx.textAlign = 'left';
        if (op.st) { ctx.strokeStyle = paint(); lineStyle(); ctx.strokeText(op.t, tp[3], 0); } else { ctx.fillStyle = paint(); ctx.fillText(op.t, tp[3], 0); }
        break;
      }
      case Op.Image: {
        let img = op.cv ? this.bitmaps.get(op.cv) : this.res.image(op.i ?? 0);
        const r = op.ir ?? [0, 0, -1, -1, 0, 0, 0, 0];
        if (!img) break;
        // Drawing a canvas onto itself reads what it held before the draw.
        if (op.cv === id) { const copy = makeSurface((img as Surface).width, (img as Surface).height); (copy.getContext('2d') as Ctx).drawImage(img, 0, 0); img = copy; }
        ctx.setTransform(user);
        ctx.imageSmoothingEnabled = !op.ns;
        if (r[2] < 0) ctx.drawImage(img, r[4], r[5], r[6], r[7]);
        else ctx.drawImage(img, r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7]);
        break;
      }
    }
    ctx.restore();
  }
}
