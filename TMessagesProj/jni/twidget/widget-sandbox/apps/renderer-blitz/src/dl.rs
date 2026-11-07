//! Display-list backend for `anyrender`: the blitz-paint scene recorded into a
//! compact binary stream that a platform executor replays with its own 2D API —
//! Android `Canvas` today (`CanvasScene.kt` in android/renderer-blitz), QPainter
//! would be the tdesktop twin. Layout, cascade and shaping stay in Rust; the
//! platform draws paths, gradients, images and — through its own font engine,
//! from the very bytes parley shaped with — the glyphs. Platform-neutral, so it
//! is unit-tested on the host. Selected with `TWB_BACKEND=canvas` or
//! `twb_set_backend(h, "canvas")` / `BlitzNative.setBackend(h, "canvas")`.
//!
//! Stream (little-endian, f32/u32/u64; the Kotlin reader mirrors this):
//! ```text
//! u32 magic 'TWDL' (0x4C445754), u32 version (1), u32 op count, then ops:
//!   1 PushLayer: u8 rule, u8 mix, u8 compose, f32 alpha, path         (clip path in base coordinates)
//!   2 PushClip:  u8 rule, path
//!   3 Pop
//!   4 Fill:      u8 rule, xf, paint, path
//!   5 Stroke:    xf, paint, f32 width, u8 join, u8 cap, f32 miter, u32 ndash, f32×ndash, f32 dash offset, path
//!   6 Glyphs:    u64 font id, u32 face, f32 size, u32 naxes, (u32 tag, f32 value)×naxes, u32 ARGB,
//!                xf, f32 skew (x shear of the glyph transform), u32 n, (u32 id, f32 x, f32 y)×n
//!   7 Shadow:    xf, f32 x0 y0 x1 y1, u32 ARGB, f32 corner radius, f32 sigma
//! xf    = 6 × f32 (a b c d tx ty), x' = a·x + c·y + tx, y' = b·x + d·y + ty
//! path  = u32 nverbs, then u8 verb: 0 M(x y) 1 L(x y) 2 Q(cx cy x y) 3 C(c1x c1y c2x c2y x y) 4 Z
//! paint = u8 kind: 0 solid(u32 ARGB) | 1 linear(f32 x0 y0 x1 y1, u8 extend, stops) | 2 radial(f32 cx0 cy0 r0 cx1 cy1 r1, u8 extend, stops)
//!         | 3 sweep(f32 cx cy a0 a1, u8 extend, stops) | 4 image(u64 id, u32 w, u32 h, u8 xext, u8 yext, u8 quality, f32 alpha) | 5 none,
//!         then u8 has brush transform (+ xf). stops = u32 n, (f32 offset, u32 ARGB)×n. rule: 0 non-zero, 1 even-odd. extend: 0 pad 1 repeat 2 reflect.
//! ```
//! Fonts and images are referenced by blob id; the bytes are fetched once per
//! id through `font_blob` / `image` (JNI `fontBytes` / `imagePixels`).

use anyrender::{Filter, Glyph, NormalizedCoord, Paint, PaintRef, PaintScene, RenderContext};
use blitz_dom::BaseDocument;
use blitz_paint::paint_scene;
use kurbo::{Affine, Cap, Join, PathEl, Point, Rect, Shape, Stroke};
use peniko::color::Srgb;
use peniko::{BlendMode, Blob, Color, ColorStops, Extend, Fill, FontData, GradientKind, ImageAlphaType, ImageData, ImageFormat, ImageQuality, StyleRef};
use skrifa::MetadataProvider;
use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

pub const MAGIC: u32 = 0x4C44_5754;
pub const VERSION: u32 = 1;
const TOLERANCE: f64 = 0.1;

static FONTS: OnceLock<Mutex<HashMap<u64, Blob<u8>>>> = OnceLock::new();
static IMAGES: OnceLock<Mutex<HashMap<u64, ImageData>>> = OnceLock::new();

/// The font file behind a glyph op's font id (kept alive for the process once referenced).
pub fn font_blob(id: u64) -> Option<Blob<u8>> {
    FONTS.get_or_init(Default::default).lock().unwrap().get(&id).cloned()
}

/// The decoded image behind an image paint's id.
pub fn image(id: u64) -> Option<ImageData> {
    IMAGES.get_or_init(Default::default).lock().unwrap().get(&id).cloned()
}

/// `image` as premultiplied RGBA8 rows (what a Bitmap wants), whatever the decoder produced.
pub fn image_rgba_premultiplied(img: &ImageData) -> Vec<u8> {
    let src = img.data.data();
    let n = (img.width as usize) * (img.height as usize) * 4;
    let mut out = vec![0u8; n];
    for (o, px) in out.chunks_exact_mut(4).zip(src.chunks_exact(4)) {
        let (r, g, b, a) = match img.format {
            ImageFormat::Rgba8 => (px[0], px[1], px[2], px[3]),
            ImageFormat::Bgra8 => (px[2], px[1], px[0], px[3]),
            _ => (px[0], px[1], px[2], px[3]),
        };
        let (r, g, b) = match img.alpha_type {
            ImageAlphaType::AlphaPremultiplied => (r, g, b),
            _ => {
                let m = |c: u8| ((c as u32 * a as u32 + 127) / 255) as u8;
                (m(r), m(g), m(b))
            }
        };
        o.copy_from_slice(&[r, g, b, a]);
    }
    out
}

fn argb(c: Color) -> u32 {
    let [r, g, b, a] = c.components;
    let q = |v: f32| (v.clamp(0.0, 1.0) * 255.0 + 0.5) as u32;
    (q(a) << 24) | (q(r) << 16) | (q(g) << 8) | q(b)
}

fn extend_code(e: Extend) -> u8 {
    match e {
        Extend::Pad => 0,
        Extend::Repeat => 1,
        Extend::Reflect => 2,
    }
}

/// User-space values of the non-default variation axes (normalized F2Dot14 → axis units), as (tag, value).
pub fn axis_values(font: &FontData, coords: &[NormalizedCoord]) -> Vec<(u32, f32)> {
    let Ok(fr) = skrifa::FontRef::from_index(font.data.data(), font.index) else { return Vec::new() };
    let mut out = Vec::new();
    for (i, axis) in fr.axes().iter().enumerate() {
        let n = coords.get(i).copied().unwrap_or(0) as f32 / 16384.0;
        if n == 0.0 {
            continue;
        }
        let (min, def, max) = (axis.min_value(), axis.default_value(), axis.max_value());
        let v = if n < 0.0 { def + n * (def - min) } else { def + n * (max - def) };
        out.push((u32::from_be_bytes(axis.tag().to_be_bytes()), v));
    }
    out
}

/// Records one frame.
pub struct DisplayListScene {
    buf: Vec<u8>,
    count: u32,
}

impl Default for DisplayListScene {
    fn default() -> Self {
        Self::new()
    }
}

impl DisplayListScene {
    pub fn new() -> Self {
        let mut s = Self { buf: Vec::with_capacity(16 * 1024), count: 0 };
        s.u32(MAGIC);
        s.u32(VERSION);
        s.u32(0);
        s
    }

    /// The finished stream (op count patched in).
    pub fn finish(mut self) -> Vec<u8> {
        self.buf[8..12].copy_from_slice(&self.count.to_le_bytes());
        self.buf
    }

    pub fn op_count(&self) -> u32 {
        self.count
    }

    fn u8(&mut self, v: u8) {
        self.buf.push(v);
    }
    fn u32(&mut self, v: u32) {
        self.buf.extend_from_slice(&v.to_le_bytes());
    }
    fn u64(&mut self, v: u64) {
        self.buf.extend_from_slice(&v.to_le_bytes());
    }
    fn f32(&mut self, v: f64) {
        self.buf.extend_from_slice(&(v as f32).to_le_bytes());
    }
    fn op(&mut self, code: u8) {
        self.u8(code);
        self.count += 1;
    }
    fn xf(&mut self, a: Affine) {
        for c in a.as_coeffs() {
            self.f32(c);
        }
    }
    fn rule(&mut self, f: Fill) {
        self.u8(match f {
            Fill::NonZero => 0,
            Fill::EvenOdd => 1,
        });
    }

    fn path(&mut self, shape: &impl Shape, transform: Option<Affine>) {
        let at = self.buf.len();
        self.u32(0);
        let mut n = 0u32;
        let t = |p: Point| transform.map_or(p, |a| a * p);
        for el in shape.path_elements(TOLERANCE) {
            n += 1;
            match el {
                PathEl::MoveTo(p) => {
                    let p = t(p);
                    self.u8(0);
                    self.f32(p.x);
                    self.f32(p.y);
                }
                PathEl::LineTo(p) => {
                    let p = t(p);
                    self.u8(1);
                    self.f32(p.x);
                    self.f32(p.y);
                }
                PathEl::QuadTo(c, p) => {
                    let (c, p) = (t(c), t(p));
                    self.u8(2);
                    for v in [c.x, c.y, p.x, p.y] {
                        self.f32(v);
                    }
                }
                PathEl::CurveTo(c1, c2, p) => {
                    let (c1, c2, p) = (t(c1), t(c2), t(p));
                    self.u8(3);
                    for v in [c1.x, c1.y, c2.x, c2.y, p.x, p.y] {
                        self.f32(v);
                    }
                }
                PathEl::ClosePath => self.u8(4),
            }
        }
        self.buf[at..at + 4].copy_from_slice(&n.to_le_bytes());
    }

    fn stops(&mut self, stops: &ColorStops) {
        self.u32(stops.len() as u32);
        for s in stops.iter() {
            self.f32(s.offset as f64);
            self.u32(argb(s.color.to_alpha_color::<Srgb>()));
        }
    }

    fn paint(&mut self, paint: PaintRef<'_>, brush_transform: Option<Affine>) {
        match paint {
            Paint::Solid(c) => {
                self.u8(0);
                self.u32(argb(c));
            }
            Paint::Gradient(g) => {
                match &g.kind {
                    GradientKind::Linear(l) => {
                        self.u8(1);
                        for v in [l.start.x, l.start.y, l.end.x, l.end.y] {
                            self.f32(v);
                        }
                    }
                    GradientKind::Radial(r) => {
                        self.u8(2);
                        for v in [r.start_center.x, r.start_center.y, r.start_radius as f64, r.end_center.x, r.end_center.y, r.end_radius as f64] {
                            self.f32(v);
                        }
                    }
                    GradientKind::Sweep(s) => {
                        self.u8(3);
                        for v in [s.center.x, s.center.y, s.start_angle as f64, s.end_angle as f64] {
                            self.f32(v);
                        }
                    }
                }
                self.u8(extend_code(g.extend));
                self.stops(&g.stops);
            }
            Paint::Image(img) => {
                let id = img.image.data.id();
                IMAGES.get_or_init(Default::default).lock().unwrap().entry(id).or_insert_with(|| img.image.clone());
                self.u8(4);
                self.u64(id);
                self.u32(img.image.width);
                self.u32(img.image.height);
                self.u8(extend_code(img.sampler.x_extend));
                self.u8(extend_code(img.sampler.y_extend));
                self.u8(match img.sampler.quality {
                    ImageQuality::Low => 0,
                    ImageQuality::Medium => 1,
                    ImageQuality::High => 2,
                });
                self.f32(img.sampler.alpha as f64);
            }
            Paint::Resource(_) | Paint::Custom(_) => self.u8(5),
        }
        match brush_transform {
            Some(t) => {
                self.u8(1);
                self.xf(t);
            }
            None => self.u8(0),
        }
    }
}

impl RenderContext for DisplayListScene {}

impl PaintScene for DisplayListScene {
    fn reset(&mut self) {
        *self = Self::new();
    }

    fn push_layer(
        &mut self,
        fill: Fill,
        blend: impl Into<BlendMode>,
        alpha: f32,
        transform: Affine,
        clip: &impl Shape,
        _filter: Option<Arc<Filter>>,
        _backdrop_filter: Option<Arc<Filter>>,
    ) {
        let blend = blend.into();
        self.op(1);
        self.rule(fill);
        self.u8(blend.mix as u8);
        self.u8(blend.compose as u8);
        self.f32(alpha as f64);
        self.path(clip, Some(transform));
    }

    fn push_clip_layer(&mut self, fill: Fill, transform: Affine, clip: &impl Shape) {
        self.op(2);
        self.rule(fill);
        self.path(clip, Some(transform));
    }

    fn pop_layer(&mut self) {
        self.op(3);
    }

    fn stroke<'a>(&mut self, style: &Stroke, transform: Affine, brush: impl Into<PaintRef<'a>>, brush_transform: Option<Affine>, shape: &impl Shape) {
        self.op(5);
        self.xf(transform);
        self.paint(brush.into(), brush_transform);
        self.f32(style.width);
        self.u8(match style.join {
            Join::Miter => 0,
            Join::Round => 1,
            Join::Bevel => 2,
        });
        self.u8(match style.start_cap {
            Cap::Butt => 0,
            Cap::Round => 1,
            Cap::Square => 2,
        });
        self.f32(style.miter_limit);
        self.u32(style.dash_pattern.len() as u32);
        for d in style.dash_pattern.iter() {
            self.f32(*d);
        }
        self.f32(style.dash_offset);
        self.path(shape, None);
    }

    fn fill<'a>(&mut self, style: Fill, transform: Affine, brush: impl Into<PaintRef<'a>>, brush_transform: Option<Affine>, shape: &impl Shape) {
        self.op(4);
        self.rule(style);
        self.xf(transform);
        self.paint(brush.into(), brush_transform);
        self.path(shape, None);
    }

    fn draw_glyphs<'a, 's: 'a>(
        &'s mut self,
        font: &'a FontData,
        font_size: f32,
        _hint: bool,
        normalized_coords: &'a [NormalizedCoord],
        _embolden: kurbo::Vec2,
        _style: impl Into<StyleRef<'a>>,
        brush: impl Into<PaintRef<'a>>,
        brush_alpha: f32,
        transform: Affine,
        glyph_transform: Option<Affine>,
        glyphs: impl Iterator<Item = Glyph> + Clone,
    ) {
        let paint: PaintRef<'a> = brush.into();
        let mut color = match paint {
            Paint::Solid(c) => c,
            Paint::Gradient(g) => g.stops.first().map(|s| s.color.to_alpha_color::<Srgb>()).unwrap_or(Color::BLACK),
            _ => return,
        };
        color.components[3] *= brush_alpha.clamp(0.0, 1.0);
        let glyphs: Vec<Glyph> = glyphs.collect();
        if glyphs.is_empty() {
            return;
        }
        let id = font.data.id();
        FONTS.get_or_init(Default::default).lock().unwrap().entry(id).or_insert_with(|| font.data.clone());
        self.op(6);
        self.u64(id);
        self.u32(font.index);
        self.f32(font_size as f64);
        let axes = axis_values(font, normalized_coords);
        self.u32(axes.len() as u32);
        for (tag, v) in axes {
            self.u32(tag);
            self.f32(v as f64);
        }
        self.u32(argb(color));
        self.xf(transform);
        self.f32(glyph_transform.map_or(0.0, |g| g.as_coeffs()[2]));
        self.u32(glyphs.len() as u32);
        for g in glyphs {
            self.u32(g.id);
            self.f32(g.x as f64);
            self.f32(g.y as f64);
        }
    }

    fn draw_box_shadow(&mut self, transform: Affine, rect: Rect, brush: Color, radius: f64, std_dev: f64) {
        self.op(7);
        self.xf(transform);
        for v in [rect.x0, rect.y0, rect.x1, rect.y1] {
            self.f32(v);
        }
        self.u32(argb(brush));
        self.f32(radius);
        self.f32(std_dev);
    }
}

/// One frame of `doc` as a display list, `scale` = device pixel ratio, `w`×`h` device px.
pub fn record(doc: &mut BaseDocument, scale: f64, w: u32, h: u32) -> Vec<u8> {
    let mut scene = DisplayListScene::new();
    paint_scene(&mut scene, doc, scale, w, h, 0, 0);
    scene.finish()
}

#[cfg(test)]
mod tests {
    use super::*;
    use peniko::color::palette::css::{BLUE, RED};

    fn u32_at(b: &[u8], i: usize) -> u32 {
        u32::from_le_bytes(b[i..i + 4].try_into().unwrap())
    }
    fn f32_at(b: &[u8], i: usize) -> f32 {
        f32::from_le_bytes(b[i..i + 4].try_into().unwrap())
    }

    #[test]
    fn fill_records_rule_transform_paint_and_path() {
        let mut s = DisplayListScene::new();
        s.fill(Fill::EvenOdd, Affine::translate((5.0, 7.0)), RED, None, &Rect::new(0.0, 0.0, 10.0, 20.0));
        let b = s.finish();
        assert_eq!(u32_at(&b, 0), MAGIC);
        assert_eq!(u32_at(&b, 8), 1, "one op");
        let mut i = 12;
        assert_eq!(b[i], 4, "fill");
        i += 1;
        assert_eq!(b[i], 1, "even-odd");
        i += 1;
        let xf: Vec<f32> = (0..6).map(|k| f32_at(&b, i + k * 4)).collect();
        assert_eq!(xf, vec![1.0, 0.0, 0.0, 1.0, 5.0, 7.0]);
        i += 24;
        assert_eq!(b[i], 0, "solid");
        assert_eq!(u32_at(&b, i + 1), 0xFFFF_0000, "opaque red ARGB");
        i += 5;
        assert_eq!(b[i], 0, "no brush transform");
        i += 1;
        assert_eq!(u32_at(&b, i), 5, "M L L L Z");
        i += 4;
        assert_eq!(b[i], 0, "move");
        assert_eq!((f32_at(&b, i + 1), f32_at(&b, i + 5)), (0.0, 0.0));
    }

    #[test]
    fn clip_paths_are_pre_transformed_and_layers_carry_alpha() {
        let mut s = DisplayListScene::new();
        s.push_layer(Fill::NonZero, peniko::Mix::Multiply, 0.5, Affine::translate((100.0, 0.0)), &Rect::new(0.0, 0.0, 1.0, 1.0), None, None);
        s.pop_layer();
        let b = s.finish();
        assert_eq!(u32_at(&b, 8), 2);
        let i = 12;
        assert_eq!(b[i], 1);
        assert_eq!((b[i + 1], b[i + 2], b[i + 3]), (0, 1, 3), "non-zero, multiply, src-over");
        assert_eq!(f32_at(&b, i + 4), 0.5);
        assert_eq!(u32_at(&b, i + 8), 5);
        assert_eq!(b[i + 12], 0);
        assert_eq!(f32_at(&b, i + 13), 100.0, "clip x translated into base space");
        assert_eq!(b[b.len() - 1], 3, "pop");
    }

    #[test]
    fn gradients_keep_their_stops() {
        let g = peniko::Gradient::new_linear((0.0, 0.0), (10.0, 0.0)).with_stops([RED, BLUE]);
        let mut s = DisplayListScene::new();
        s.fill(Fill::NonZero, Affine::IDENTITY, &g, Some(Affine::scale(2.0)), &Rect::new(0.0, 0.0, 10.0, 10.0));
        let b = s.finish();
        let i = 12 + 1 + 1 + 24;
        assert_eq!(b[i], 1, "linear");
        assert_eq!(f32_at(&b, i + 9), 10.0, "end x");
        assert_eq!(b[i + 17], 0, "pad");
        assert_eq!(u32_at(&b, i + 18), 2, "two stops");
        assert_eq!(u32_at(&b, i + 22 + 4), 0xFFFF_0000);
        assert_eq!(u32_at(&b, i + 22 + 8 + 4), 0xFF00_00FF);
        assert_eq!(b[i + 22 + 16], 1, "brush transform follows");
    }

    #[test]
    fn glyph_runs_register_the_font_and_carry_axes() {
        let Ok(bytes) = std::fs::read("/System/Library/Fonts/SFNS.ttf") else { return };
        let font = FontData { data: Blob::new(Arc::new(bytes)), index: 0 };
        let mut s = DisplayListScene::new();
        // wght is the first axis of SF: +0.5 of the way to the maximum.
        s.draw_glyphs(&font, 24.0, true, &[8192], kurbo::Vec2::ZERO, Fill::NonZero, RED, 1.0, Affine::IDENTITY, None, [Glyph { id: 40, x: 1.0, y: 2.0 }].into_iter());
        let b = s.finish();
        let i = 12;
        assert_eq!(b[i], 6);
        assert_eq!(u64::from_le_bytes(b[i + 1..i + 9].try_into().unwrap()), font.data.id());
        assert!(font_blob(font.data.id()).is_some(), "font registered for the executor");
        assert_eq!(f32_at(&b, i + 13), 24.0);
        let naxes = u32_at(&b, i + 17) as usize;
        assert!(naxes >= 1, "a variation axis is carried");
        let axes: Vec<(u32, f32)> = (0..naxes).map(|k| (u32_at(&b, i + 21 + k * 8), f32_at(&b, i + 25 + k * 8))).collect();
        let (tag, v) = axes[0];
        let fr = skrifa::FontRef::from_index(font.data.data(), 0).unwrap();
        let axis = fr.axes().get(0).unwrap();
        assert_eq!(tag, u32::from_be_bytes(axis.tag().to_be_bytes()), "first axis of the font");
        assert!(v > axis.default_value() && v <= axis.max_value(), "halfway to the maximum in axis units: {v}");
    }
}
