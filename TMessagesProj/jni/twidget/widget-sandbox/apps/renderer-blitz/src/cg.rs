//! `anyrender` backend on CoreGraphics + CoreText (Apple platforms only): the same
//! blitz-paint scene that `vello_cpu` rasterises, drawn by the platform instead.
//! Glyphs go through `CTFontDrawGlyphs` from the very font file parley shaped
//! with, so text antialiasing, hinting and colour emoji are the system's own;
//! fills, strokes, gradients, images and clips go to the CGContext over the
//! caller's RGBA buffer. Layout, cascade and shaping stay in Rust — only the
//! pixels change hands (docs/blitz-experiment.md, "PaintScene on CoreGraphics").
//!
//! Selected with `TWB_BACKEND=cg` or `twb_set_backend(handle, "cg")`. Raw FFI,
//! no new crates. Known gaps: `Extend::Repeat`/`Reflect` gradients draw once,
//! sweep gradients are 96 flat wedges, CSS filters are ignored, gradient text
//! takes its first stop colour.

#![allow(non_upper_case_globals, non_snake_case, clippy::too_many_arguments)]

use anyrender::{Filter, Glyph, ImageRenderer, NormalizedCoord, Paint, PaintRef, PaintScene, RenderContext};
use kurbo::{Affine, Cap, Join, PathEl, Point, Rect, RoundedRect, Shape, Stroke};
use peniko::color::Srgb;
use peniko::{BlendMode, Color, ColorStops, Compose, Extend, Fill, FontData, Gradient, GradientKind, ImageAlphaType, ImageBrushRef, ImageData, ImageFormat, ImageQuality, Mix, StyleRef};
use skrifa::MetadataProvider;
use std::collections::HashMap;
use std::ffi::{c_char, c_void};
use std::ptr::null;
use std::sync::{Arc, Mutex, OnceLock};

const TOLERANCE: f64 = 0.1;

// ───────────────────────── FFI ─────────────────────────

type CFTypeRef = *const c_void;
type CFDataRef = *const c_void;
type CFStringRef = *const c_void;
type CFArrayRef = *const c_void;
type CFDictionaryRef = *const c_void;
type CFNumberRef = *const c_void;
type CFIndex = isize;
type CGContextRef = *mut c_void;
type CGColorSpaceRef = *mut c_void;
type CGPathRef = *mut c_void;
type CGGradientRef = *mut c_void;
type CGImageRef = *mut c_void;
type CGDataProviderRef = *mut c_void;
type CTFontRef = *const c_void;
type CTFontDescriptorRef = *const c_void;
type CGGlyph = u16;

#[repr(C)]
#[derive(Clone, Copy)]
struct CGPoint {
    x: f64,
    y: f64,
}
#[repr(C)]
#[derive(Clone, Copy)]
struct CGSize {
    width: f64,
    height: f64,
}
#[repr(C)]
#[derive(Clone, Copy)]
struct CGRect {
    origin: CGPoint,
    size: CGSize,
}
#[repr(C)]
#[derive(Clone, Copy)]
struct CGAffineTransform {
    a: f64,
    b: f64,
    c: f64,
    d: f64,
    tx: f64,
    ty: f64,
}

const kCFStringEncodingUTF8: u32 = 0x0800_0100;
const kCFNumberSInt32Type: CFIndex = 3;
const kCFNumberDoubleType: CFIndex = 13;
const kCGImageAlphaPremultipliedLast: u32 = 1;
const kCGImageAlphaPremultipliedFirst: u32 = 2;
const kCGImageAlphaLast: u32 = 3;
const kCGImageAlphaFirst: u32 = 4;
const kCGBitmapByteOrder32Little: u32 = 2 << 12;
const kCGBitmapByteOrder32Big: u32 = 4 << 12;
const kCGGradientDrawsBeforeStartLocation: u32 = 1;
const kCGGradientDrawsAfterEndLocation: u32 = 2;
const kCGTextFill: i32 = 0;
const kCGTextStroke: i32 = 1;
const kCGRenderingIntentDefault: i32 = 0;

#[link(name = "CoreFoundation", kind = "framework")]
extern "C" {
    static kCFTypeDictionaryKeyCallBacks: c_void;
    static kCFTypeDictionaryValueCallBacks: c_void;
    fn CFRelease(cf: CFTypeRef);
    fn CFDataCreate(allocator: *const c_void, bytes: *const u8, length: CFIndex) -> CFDataRef;
    fn CFStringGetCString(s: CFStringRef, buffer: *mut c_char, size: CFIndex, encoding: u32) -> u8;
    fn CFArrayGetCount(array: CFArrayRef) -> CFIndex;
    fn CFArrayGetValueAtIndex(array: CFArrayRef, idx: CFIndex) -> *const c_void;
    fn CFDictionaryCreate(
        allocator: *const c_void,
        keys: *const *const c_void,
        values: *const *const c_void,
        count: CFIndex,
        key_callbacks: *const c_void,
        value_callbacks: *const c_void,
    ) -> CFDictionaryRef;
    fn CFNumberCreate(allocator: *const c_void, the_type: CFIndex, value: *const c_void) -> CFNumberRef;
}

#[link(name = "CoreGraphics", kind = "framework")]
extern "C" {
    fn CGColorSpaceCreateDeviceRGB() -> CGColorSpaceRef;
    fn CGColorSpaceRelease(space: CGColorSpaceRef);
    fn CGBitmapContextCreate(
        data: *mut c_void,
        width: usize,
        height: usize,
        bits_per_component: usize,
        bytes_per_row: usize,
        space: CGColorSpaceRef,
        bitmap_info: u32,
    ) -> CGContextRef;
    fn CGContextRelease(c: CGContextRef);
    fn CGContextSaveGState(c: CGContextRef);
    fn CGContextRestoreGState(c: CGContextRef);
    fn CGContextConcatCTM(c: CGContextRef, t: CGAffineTransform);
    fn CGContextTranslateCTM(c: CGContextRef, tx: f64, ty: f64);
    fn CGContextScaleCTM(c: CGContextRef, sx: f64, sy: f64);
    fn CGContextSetRGBFillColor(c: CGContextRef, r: f64, g: f64, b: f64, a: f64);
    fn CGContextSetRGBStrokeColor(c: CGContextRef, r: f64, g: f64, b: f64, a: f64);
    fn CGContextBeginPath(c: CGContextRef);
    fn CGContextAddPath(c: CGContextRef, path: CGPathRef);
    fn CGContextFillPath(c: CGContextRef);
    fn CGContextEOFillPath(c: CGContextRef);
    fn CGContextStrokePath(c: CGContextRef);
    fn CGContextClip(c: CGContextRef);
    fn CGContextEOClip(c: CGContextRef);
    fn CGContextReplacePathWithStrokedPath(c: CGContextRef);
    fn CGContextSetLineWidth(c: CGContextRef, width: f64);
    fn CGContextSetLineJoin(c: CGContextRef, join: i32);
    fn CGContextSetLineCap(c: CGContextRef, cap: i32);
    fn CGContextSetMiterLimit(c: CGContextRef, limit: f64);
    fn CGContextSetLineDash(c: CGContextRef, phase: f64, lengths: *const f64, count: usize);
    fn CGContextSetAlpha(c: CGContextRef, alpha: f64);
    fn CGContextSetBlendMode(c: CGContextRef, mode: i32);
    fn CGContextBeginTransparencyLayer(c: CGContextRef, aux: *const c_void);
    fn CGContextEndTransparencyLayer(c: CGContextRef);
    fn CGContextSetShouldAntialias(c: CGContextRef, b: bool);
    fn CGContextSetAllowsAntialiasing(c: CGContextRef, b: bool);
    fn CGContextSetShouldSmoothFonts(c: CGContextRef, b: bool);
    fn CGContextSetAllowsFontSmoothing(c: CGContextRef, b: bool);
    fn CGContextSetShouldSubpixelPositionFonts(c: CGContextRef, b: bool);
    fn CGContextSetAllowsFontSubpixelPositioning(c: CGContextRef, b: bool);
    fn CGContextSetShouldSubpixelQuantizeFonts(c: CGContextRef, b: bool);
    fn CGContextSetAllowsFontSubpixelQuantization(c: CGContextRef, b: bool);
    fn CGContextSetTextMatrix(c: CGContextRef, t: CGAffineTransform);
    fn CGContextSetTextDrawingMode(c: CGContextRef, mode: i32);
    fn CGContextSetInterpolationQuality(c: CGContextRef, quality: i32);
    fn CGContextDrawLinearGradient(c: CGContextRef, g: CGGradientRef, start: CGPoint, end: CGPoint, options: u32);
    fn CGContextDrawRadialGradient(
        c: CGContextRef,
        g: CGGradientRef,
        start_center: CGPoint,
        start_radius: f64,
        end_center: CGPoint,
        end_radius: f64,
        options: u32,
    );
    fn CGGradientCreateWithColorComponents(space: CGColorSpaceRef, components: *const f64, locations: *const f64, count: usize) -> CGGradientRef;
    fn CGGradientRelease(g: CGGradientRef);
    fn CGContextDrawImage(c: CGContextRef, rect: CGRect, image: CGImageRef);
    fn CGContextDrawTiledImage(c: CGContextRef, rect: CGRect, image: CGImageRef);
    fn CGImageCreate(
        width: usize,
        height: usize,
        bits_per_component: usize,
        bits_per_pixel: usize,
        bytes_per_row: usize,
        space: CGColorSpaceRef,
        bitmap_info: u32,
        provider: CGDataProviderRef,
        decode: *const f64,
        should_interpolate: bool,
        intent: i32,
    ) -> CGImageRef;
    fn CGImageRelease(image: CGImageRef);
    fn CGDataProviderCreateWithCFData(data: CFDataRef) -> CGDataProviderRef;
    fn CGDataProviderRelease(provider: CGDataProviderRef);
    fn CGPathCreateMutable() -> CGPathRef;
    fn CGPathMoveToPoint(path: CGPathRef, m: *const CGAffineTransform, x: f64, y: f64);
    fn CGPathAddLineToPoint(path: CGPathRef, m: *const CGAffineTransform, x: f64, y: f64);
    fn CGPathAddQuadCurveToPoint(path: CGPathRef, m: *const CGAffineTransform, cpx: f64, cpy: f64, x: f64, y: f64);
    fn CGPathAddCurveToPoint(path: CGPathRef, m: *const CGAffineTransform, c1x: f64, c1y: f64, c2x: f64, c2y: f64, x: f64, y: f64);
    fn CGPathCloseSubpath(path: CGPathRef);
    fn CGPathGetBoundingBox(path: CGPathRef) -> CGRect;
    fn CGPathRelease(path: CGPathRef);
}

#[link(name = "CoreText", kind = "framework")]
extern "C" {
    static kCTFontVariationAttribute: CFStringRef;
    fn CTFontCreateWithFontDescriptor(descriptor: CTFontDescriptorRef, size: f64, matrix: *const CGAffineTransform) -> CTFontRef;
    fn CTFontCreateCopyWithAttributes(font: CTFontRef, size: f64, matrix: *const CGAffineTransform, attributes: CTFontDescriptorRef) -> CTFontRef;
    fn CTFontDrawGlyphs(font: CTFontRef, glyphs: *const CGGlyph, positions: *const CGPoint, count: usize, context: CGContextRef);
    fn CTFontCopyPostScriptName(font: CTFontRef) -> CFStringRef;
    fn CTFontDescriptorCreateWithAttributes(attributes: CFDictionaryRef) -> CTFontDescriptorRef;
    fn CTFontManagerCreateFontDescriptorsFromData(data: CFDataRef) -> CFArrayRef;
}

// ───────────────────────── helpers ─────────────────────────

fn cg_affine(a: Affine) -> CGAffineTransform {
    let [a0, b, c, d, tx, ty] = a.as_coeffs();
    CGAffineTransform { a: a0, b, c, d, tx, ty }
}

fn pt(p: Point) -> CGPoint {
    CGPoint { x: p.x, y: p.y }
}

fn rgba(c: Color) -> [f64; 4] {
    let [r, g, b, a] = c.components;
    [r.clamp(0.0, 1.0) as f64, g.clamp(0.0, 1.0) as f64, b.clamp(0.0, 1.0) as f64, a.clamp(0.0, 1.0) as f64]
}

fn cf_to_string(s: CFStringRef) -> String {
    if s.is_null() {
        return String::new();
    }
    let mut buf = vec![0u8; 512];
    let ok = unsafe { CFStringGetCString(s, buf.as_mut_ptr() as *mut c_char, buf.len() as CFIndex, kCFStringEncodingUTF8) };
    if ok == 0 {
        return String::new();
    }
    let end = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());
    String::from_utf8_lossy(&buf[..end]).into_owned()
}

/// A CGPath from any kurbo shape; `transform` bakes an affine into the points
/// (for clips, which must not disturb the CTM).
unsafe fn cg_path(shape: &impl Shape, transform: Option<Affine>) -> CGPathRef {
    let path = CGPathCreateMutable();
    let t = |p: Point| transform.map_or(p, |a| a * p);
    let mut open = false;
    for el in shape.path_elements(TOLERANCE) {
        match el {
            PathEl::MoveTo(p) => {
                let p = t(p);
                CGPathMoveToPoint(path, null(), p.x, p.y);
                open = true;
            }
            PathEl::LineTo(p) => {
                let p = t(p);
                if open {
                    CGPathAddLineToPoint(path, null(), p.x, p.y);
                } else {
                    CGPathMoveToPoint(path, null(), p.x, p.y);
                    open = true;
                }
            }
            PathEl::QuadTo(c, p) => {
                let (c, p) = (t(c), t(p));
                if !open {
                    CGPathMoveToPoint(path, null(), c.x, c.y);
                    open = true;
                }
                CGPathAddQuadCurveToPoint(path, null(), c.x, c.y, p.x, p.y);
            }
            PathEl::CurveTo(c1, c2, p) => {
                let (c1, c2, p) = (t(c1), t(c2), t(p));
                if !open {
                    CGPathMoveToPoint(path, null(), c1.x, c1.y);
                    open = true;
                }
                CGPathAddCurveToPoint(path, null(), c1.x, c1.y, c2.x, c2.y, p.x, p.y);
            }
            PathEl::ClosePath => {
                if open {
                    CGPathCloseSubpath(path);
                }
                open = false;
            }
        }
    }
    path
}

fn blend_mode(b: BlendMode) -> i32 {
    // CGBlendMode numbering; peniko's HardLight/SoftLight are swapped relative to CG.
    match b.compose {
        Compose::SrcOver => match b.mix {
            Mix::Normal => 0,
            Mix::Multiply => 1,
            Mix::Screen => 2,
            Mix::Overlay => 3,
            Mix::Darken => 4,
            Mix::Lighten => 5,
            Mix::ColorDodge => 6,
            Mix::ColorBurn => 7,
            Mix::HardLight => 9,
            Mix::SoftLight => 8,
            Mix::Difference => 10,
            Mix::Exclusion => 11,
            Mix::Hue => 12,
            Mix::Saturation => 13,
            Mix::Color => 14,
            Mix::Luminosity => 15,
        },
        Compose::Clear => 16,
        Compose::Copy => 17,
        Compose::SrcIn => 18,
        Compose::SrcOut => 19,
        Compose::SrcAtop => 20,
        Compose::DestOver => 21,
        Compose::DestIn => 22,
        Compose::DestOut => 23,
        Compose::DestAtop => 24,
        Compose::Xor => 25,
        Compose::Plus | Compose::PlusLighter => 27,
        Compose::Dest => 0,
    }
}

fn is_plain(b: BlendMode) -> bool {
    matches!(b.compose, Compose::SrcOver) && matches!(b.mix, Mix::Normal)
}

/// Colour at `t` along the stops, linearly in sRGB (what CG does too).
fn sample_stops(stops: &ColorStops, t: f32) -> [f64; 4] {
    let n = stops.len();
    if n == 0 {
        return [0.0; 4];
    }
    let col = |i: usize| stops[i].color.to_alpha_color::<Srgb>();
    if t <= stops[0].offset {
        return rgba(col(0));
    }
    for i in 1..n {
        if t <= stops[i].offset {
            let (a, b) = (stops[i - 1], stops[i]);
            let span = (b.offset - a.offset).max(1e-6);
            let f = ((t - a.offset) / span).clamp(0.0, 1.0);
            let ca = col(i - 1).components;
            let cb = col(i).components;
            let mut out = [0.0; 4];
            for k in 0..4 {
                out[k] = (ca[k] + (cb[k] - ca[k]) * f).clamp(0.0, 1.0) as f64;
            }
            return out;
        }
    }
    rgba(col(n - 1))
}

// ───────────────────────── font cache ─────────────────────────

#[derive(Hash, PartialEq, Eq)]
struct FontKey {
    blob: u64,
    index: u32,
    size: u32,
    coords: Vec<i16>,
}

struct Fonts {
    /// (blob id, face index) → CTFont at a nominal size, created from the font bytes.
    base: HashMap<(u64, u32), usize>,
    sized: HashMap<FontKey, usize>,
}
unsafe impl Send for Fonts {}
static FONTS: OnceLock<Mutex<Fonts>> = OnceLock::new();

fn fonts() -> &'static Mutex<Fonts> {
    FONTS.get_or_init(|| Mutex::new(Fonts { base: HashMap::new(), sized: HashMap::new() }))
}

/// The PostScript name skrifa reads from the face; the fallback lookup key.
fn postscript_name(font: &FontData) -> Option<String> {
    let fr = skrifa::FontRef::from_index(font.data.data(), font.index).ok()?;
    fr.localized_strings(skrifa::string::StringId::POSTSCRIPT_NAME).english_or_first().map(|s| s.to_string())
}

/// User-space values for the non-default variation axes (normalized F2Dot14 → axis units).
fn axis_values(font: &FontData, coords: &[NormalizedCoord]) -> Vec<(u32, f64)> {
    let Ok(fr) = skrifa::FontRef::from_index(font.data.data(), font.index) else { return Vec::new() };
    let mut out = Vec::new();
    for (i, axis) in fr.axes().iter().enumerate() {
        let n = coords.get(i).copied().unwrap_or(0) as f64 / 16384.0;
        if n == 0.0 {
            continue;
        }
        let (min, def, max) = (axis.min_value() as f64, axis.default_value() as f64, axis.max_value() as f64);
        let v = if n < 0.0 { def + n * (def - min) } else { def + n * (max - def) };
        out.push((u32::from_be_bytes(axis.tag().to_be_bytes()), v));
    }
    out
}

unsafe fn base_font(font: &FontData) -> CTFontRef {
    let key = (font.data.id(), font.index);
    if let Some(&f) = fonts().lock().unwrap().base.get(&key) {
        return f as CTFontRef;
    }
    let mut created: CTFontRef = null();
    // Open the very bytes parley shaped with. CoreText lists one descriptor per face of a
    // collection, but for a variable font it lists every named instance (SF: 369, Ultralight
    // first), so the face is picked by the PostScript name skrifa reads from the default
    // instance; the collection index is the fallback. Hidden system fonts cannot be
    // created by name (".SFNS-Regular" yields Times), hence no name lookup.
    let want = postscript_name(font);
    let bytes = font.data.data();
    let data = CFDataCreate(null(), bytes.as_ptr(), bytes.len() as CFIndex);
    if !data.is_null() {
        let descs = CTFontManagerCreateFontDescriptorsFromData(data);
        if !descs.is_null() {
            let n = CFArrayGetCount(descs);
            let mut by_index: CTFontRef = null();
            for i in 0..n {
                let f = CTFontCreateWithFontDescriptor(CFArrayGetValueAtIndex(descs, i), 12.0, null());
                if f.is_null() {
                    continue;
                }
                let ps = CTFontCopyPostScriptName(f);
                let name = cf_to_string(ps);
                if !ps.is_null() {
                    CFRelease(ps);
                }
                if want.as_deref() == Some(name.as_str()) {
                    created = f;
                    break;
                }
                if i == font.index as CFIndex || (by_index.is_null() && i == 0) {
                    if !by_index.is_null() {
                        CFRelease(by_index);
                    }
                    by_index = f;
                } else {
                    CFRelease(f);
                }
            }
            if created.is_null() {
                created = by_index;
            } else if !by_index.is_null() {
                CFRelease(by_index);
            }
            CFRelease(descs);
        }
        CFRelease(data);
    }
    if created.is_null() {
        log::warn!("CoreText could not open font blob {} face {}", font.data.id(), font.index);
    }
    fonts().lock().unwrap().base.insert(key, created as usize);
    created
}

unsafe fn sized_font(font: &FontData, size: f32, coords: &[NormalizedCoord]) -> CTFontRef {
    let key = FontKey { blob: font.data.id(), index: font.index, size: size.to_bits(), coords: coords.to_vec() };
    if let Some(&f) = fonts().lock().unwrap().sized.get(&key) {
        return f as CTFontRef;
    }
    let base = base_font(font);
    let mut out: CTFontRef = null();
    if !base.is_null() {
        let axes = axis_values(font, coords);
        if axes.is_empty() {
            out = CTFontCreateCopyWithAttributes(base, size as f64, null(), null());
        } else {
            let mut keys: Vec<CFNumberRef> = Vec::new();
            let mut vals: Vec<CFNumberRef> = Vec::new();
            for (tag, v) in &axes {
                let t = *tag as i32;
                keys.push(CFNumberCreate(null(), kCFNumberSInt32Type, &t as *const i32 as *const c_void));
                vals.push(CFNumberCreate(null(), kCFNumberDoubleType, v as *const f64 as *const c_void));
            }
            let variation = CFDictionaryCreate(
                null(),
                keys.as_ptr() as *const *const c_void,
                vals.as_ptr() as *const *const c_void,
                keys.len() as CFIndex,
                &kCFTypeDictionaryKeyCallBacks,
                &kCFTypeDictionaryValueCallBacks,
            );
            let attr_key: CFStringRef = kCTFontVariationAttribute;
            let attrs = CFDictionaryCreate(null(), &attr_key, &variation, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
            let desc = CTFontDescriptorCreateWithAttributes(attrs);
            out = CTFontCreateCopyWithAttributes(base, size as f64, null(), desc);
            CFRelease(desc);
            CFRelease(attrs);
            CFRelease(variation);
            for k in keys.into_iter().chain(vals) {
                CFRelease(k);
            }
        }
    }
    let mut cache = fonts().lock().unwrap();
    if cache.sized.len() > 4096 {
        for (_, f) in cache.sized.drain() {
            if f != 0 {
                CFRelease(f as CFTypeRef);
            }
        }
    }
    cache.sized.insert(key, out as usize);
    out
}

// ───────────────────────── image cache ─────────────────────────

struct Images(HashMap<u64, usize>);
unsafe impl Send for Images {}
static IMAGES: OnceLock<Mutex<Images>> = OnceLock::new();

unsafe fn cg_image_from_rgba(width: usize, height: usize, bytes: &[u8], bitmap_info: u32) -> CGImageRef {
    if width == 0 || height == 0 || bytes.len() < width * height * 4 {
        return std::ptr::null_mut();
    }
    let data = CFDataCreate(null(), bytes.as_ptr(), (width * height * 4) as CFIndex);
    let provider = CGDataProviderCreateWithCFData(data);
    let space = CGColorSpaceCreateDeviceRGB();
    let img = CGImageCreate(width, height, 8, 32, width * 4, space, bitmap_info, provider, null(), true, kCGRenderingIntentDefault);
    CGColorSpaceRelease(space);
    CGDataProviderRelease(provider);
    CFRelease(data);
    img
}

unsafe fn cached_image(image: &ImageData) -> CGImageRef {
    let cache = IMAGES.get_or_init(|| Mutex::new(Images(HashMap::new())));
    let id = image.data.id();
    if let Some(&p) = cache.lock().unwrap().0.get(&id) {
        return p as CGImageRef;
    }
    let info = match (image.format, image.alpha_type) {
        (ImageFormat::Rgba8, ImageAlphaType::AlphaPremultiplied) => kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big,
        (ImageFormat::Rgba8, ImageAlphaType::Alpha) => kCGImageAlphaLast | kCGBitmapByteOrder32Big,
        (ImageFormat::Bgra8, ImageAlphaType::AlphaPremultiplied) => kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little,
        (ImageFormat::Bgra8, ImageAlphaType::Alpha) => kCGImageAlphaFirst | kCGBitmapByteOrder32Little,
        _ => return std::ptr::null_mut(),
    };
    let img = cg_image_from_rgba(image.width as usize, image.height as usize, image.data.data(), info);
    let mut cache = cache.lock().unwrap();
    if cache.0.len() > 256 {
        for (_, p) in cache.0.drain() {
            if p != 0 {
                CGImageRelease(p as CGImageRef);
            }
        }
    }
    cache.0.insert(id, img as usize);
    img
}

// ───────────────────────── blurred rounded rect ─────────────────────────
// Analytic Gaussian blur of a rounded rectangle (Evan Wallace's approximation, the
// same family vello_cpu uses), so `draw_box_shadow` matches the vello backend.

fn erf(x: f64) -> f64 {
    let s = x.signum();
    let a = x.abs();
    let mut t = 1.0 + (0.278393 + (0.230389 + 0.078108 * (a * a)) * a) * a;
    t *= t;
    s - s / (t * t)
}

fn gaussian(x: f64, sigma: f64) -> f64 {
    (-(x * x) / (2.0 * sigma * sigma)).exp() / ((2.0 * std::f64::consts::PI).sqrt() * sigma)
}

fn shadow_x(x: f64, y: f64, sigma: f64, corner: f64, half: (f64, f64)) -> f64 {
    let delta = (half.1 - corner - y.abs()).min(0.0);
    let curved = half.0 - corner + (corner * corner - delta * delta).max(0.0).sqrt();
    let k = 0.5f64.sqrt() / sigma;
    let lo = 0.5 + 0.5 * erf((x - curved) * k);
    let hi = 0.5 + 0.5 * erf((x + curved) * k);
    hi - lo
}

fn rounded_box_shadow(rect: Rect, p: Point, sigma: f64, corner: f64) -> f64 {
    let center = rect.center();
    let half = (rect.width() / 2.0, rect.height() / 2.0);
    let (px, py) = (p.x - center.x, p.y - center.y);
    let low = py - half.1;
    let high = py + half.1;
    let start = (-3.0 * sigma).clamp(low, high);
    let end = (3.0 * sigma).clamp(low, high);
    let step = (end - start) / 4.0;
    let mut y = start + step * 0.5;
    let mut value = 0.0;
    for _ in 0..4 {
        value += shadow_x(px, py - y, sigma, corner, half) * gaussian(y, sigma) * step;
        y += step;
    }
    value.clamp(0.0, 1.0)
}

// ───────────────────────── the scene ─────────────────────────

pub struct CgScene {
    ctx: CGContextRef,
    /// For each pushed layer: whether a transparency layer was begun.
    layers: Vec<bool>,
}

impl CgScene {
    unsafe fn set_fill(&self, c: [f64; 4]) {
        CGContextSetRGBFillColor(self.ctx, c[0], c[1], c[2], c[3]);
    }

    unsafe fn set_stroke(&self, c: [f64; 4]) {
        CGContextSetRGBStrokeColor(self.ctx, c[0], c[1], c[2], c[3]);
    }

    unsafe fn clip_current_path(&self, fill: Fill) {
        match fill {
            Fill::NonZero => CGContextClip(self.ctx),
            Fill::EvenOdd => CGContextEOClip(self.ctx),
        }
    }

    unsafe fn draw_gradient(&self, g: &Gradient, bbox: CGRect) {
        let n = g.stops.len();
        if n == 0 {
            return;
        }
        if n == 1 {
            self.set_fill(rgba(g.stops[0].color.to_alpha_color::<Srgb>()));
            let p = cg_path(&Rect::new(bbox.origin.x, bbox.origin.y, bbox.origin.x + bbox.size.width, bbox.origin.y + bbox.size.height), None);
            CGContextBeginPath(self.ctx);
            CGContextAddPath(self.ctx, p);
            CGContextFillPath(self.ctx);
            CGPathRelease(p);
            return;
        }
        let options = if g.extend == Extend::Pad { kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation } else { 0 };
        match &g.kind {
            GradientKind::Linear(l) => {
                let (comps, locs) = Self::ramp(&g.stops);
                let space = CGColorSpaceCreateDeviceRGB();
                let grad = CGGradientCreateWithColorComponents(space, comps.as_ptr(), locs.as_ptr(), n);
                CGContextDrawLinearGradient(self.ctx, grad, pt(l.start), pt(l.end), options);
                CGGradientRelease(grad);
                CGColorSpaceRelease(space);
            }
            GradientKind::Radial(r) => {
                let (comps, locs) = Self::ramp(&g.stops);
                let space = CGColorSpaceCreateDeviceRGB();
                let grad = CGGradientCreateWithColorComponents(space, comps.as_ptr(), locs.as_ptr(), n);
                CGContextDrawRadialGradient(self.ctx, grad, pt(r.start_center), r.start_radius as f64, pt(r.end_center), r.end_radius as f64, options);
                CGGradientRelease(grad);
                CGColorSpaceRelease(space);
            }
            GradientKind::Sweep(s) => {
                // No native conic gradient in CG: 96 flat wedges around the centre.
                let c = s.center;
                let corners = [
                    (bbox.origin.x, bbox.origin.y),
                    (bbox.origin.x + bbox.size.width, bbox.origin.y),
                    (bbox.origin.x, bbox.origin.y + bbox.size.height),
                    (bbox.origin.x + bbox.size.width, bbox.origin.y + bbox.size.height),
                ];
                let radius = corners.iter().map(|(x, y)| ((x - c.x).powi(2) + (y - c.y).powi(2)).sqrt()).fold(1.0, f64::max) + 2.0;
                let (a0, a1) = (s.start_angle as f64, s.end_angle as f64);
                let span = if (a1 - a0).abs() < 1e-6 { std::f64::consts::TAU } else { a1 - a0 };
                let wedges = 96;
                let step = std::f64::consts::TAU / wedges as f64;
                for i in 0..wedges {
                    let th0 = a0 + i as f64 * step;
                    let th1 = th0 + step * 1.02;
                    let mid = (th0 + step * 0.5 - a0) / span;
                    let t = match g.extend {
                        Extend::Pad => mid.clamp(0.0, 1.0),
                        Extend::Repeat => mid.rem_euclid(1.0),
                        Extend::Reflect => {
                            let m = mid.rem_euclid(2.0);
                            if m > 1.0 { 2.0 - m } else { m }
                        }
                    };
                    self.set_fill(sample_stops(&g.stops, t as f32));
                    let path = CGPathCreateMutable();
                    CGPathMoveToPoint(path, null(), c.x, c.y);
                    CGPathAddLineToPoint(path, null(), c.x + radius * th0.cos(), c.y + radius * th0.sin());
                    CGPathAddLineToPoint(path, null(), c.x + radius * th1.cos(), c.y + radius * th1.sin());
                    CGPathCloseSubpath(path);
                    CGContextBeginPath(self.ctx);
                    CGContextAddPath(self.ctx, path);
                    CGContextFillPath(self.ctx);
                    CGPathRelease(path);
                }
            }
        }
    }

    fn ramp(stops: &ColorStops) -> (Vec<f64>, Vec<f64>) {
        let mut comps = Vec::with_capacity(stops.len() * 4);
        let mut locs = Vec::with_capacity(stops.len());
        for s in stops.iter() {
            comps.extend_from_slice(&rgba(s.color.to_alpha_color::<Srgb>()));
            locs.push(s.offset.clamp(0.0, 1.0) as f64);
        }
        (comps, locs)
    }

    /// Draws an image brush over the current clip: image px (0,0)-(w,h) in the current user space.
    unsafe fn draw_image_brush(&self, brush: ImageBrushRef<'_>) {
        let img = cached_image(brush.image);
        if img.is_null() {
            return;
        }
        let (w, h) = (brush.image.width as f64, brush.image.height as f64);
        CGContextSetInterpolationQuality(
            self.ctx,
            match brush.sampler.quality {
                ImageQuality::Low => 1,
                ImageQuality::Medium => 2,
                ImageQuality::High => 3,
            },
        );
        if brush.sampler.alpha < 1.0 {
            CGContextSetAlpha(self.ctx, brush.sampler.alpha.clamp(0.0, 1.0) as f64);
        }
        // CG images are y-up: flip the image box.
        CGContextTranslateCTM(self.ctx, 0.0, h);
        CGContextScaleCTM(self.ctx, 1.0, -1.0);
        let rect = CGRect { origin: CGPoint { x: 0.0, y: 0.0 }, size: CGSize { width: w, height: h } };
        if brush.sampler.x_extend == Extend::Repeat || brush.sampler.y_extend == Extend::Repeat {
            CGContextDrawTiledImage(self.ctx, rect, img);
        } else {
            CGContextDrawImage(self.ctx, rect, img);
        }
    }

    /// Fills or clips the current path with `paint`; the path is already on the context.
    unsafe fn paint_current_path(&self, fill: Fill, paint: PaintRef<'_>, brush_transform: Option<Affine>, bbox: CGRect) {
        match paint {
            Paint::Solid(c) => {
                self.set_fill(rgba(c));
                match fill {
                    Fill::NonZero => CGContextFillPath(self.ctx),
                    Fill::EvenOdd => CGContextEOFillPath(self.ctx),
                }
            }
            Paint::Gradient(g) => {
                self.clip_current_path(fill);
                if let Some(bt) = brush_transform {
                    CGContextConcatCTM(self.ctx, cg_affine(bt));
                }
                self.draw_gradient(g, bbox);
            }
            Paint::Image(img) => {
                self.clip_current_path(fill);
                if let Some(bt) = brush_transform {
                    CGContextConcatCTM(self.ctx, cg_affine(bt));
                }
                self.draw_image_brush(img);
            }
            Paint::Resource(_) | Paint::Custom(_) => {}
        }
    }

    unsafe fn set_stroke_style(&self, style: &Stroke) {
        CGContextSetLineWidth(self.ctx, style.width);
        CGContextSetLineJoin(
            self.ctx,
            match style.join {
                Join::Miter => 0,
                Join::Round => 1,
                Join::Bevel => 2,
            },
        );
        CGContextSetLineCap(
            self.ctx,
            match style.start_cap {
                Cap::Butt => 0,
                Cap::Round => 1,
                Cap::Square => 2,
            },
        );
        CGContextSetMiterLimit(self.ctx, style.miter_limit);
        if !style.dash_pattern.is_empty() {
            let dashes: Vec<f64> = style.dash_pattern.iter().copied().collect();
            CGContextSetLineDash(self.ctx, style.dash_offset, dashes.as_ptr(), dashes.len());
        }
    }

    fn finish(&mut self) {
        while !self.layers.is_empty() {
            self.pop_layer();
        }
    }
}

impl RenderContext for CgScene {}

impl PaintScene for CgScene {
    fn reset(&mut self) {}

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
        unsafe {
            CGContextSaveGState(self.ctx);
            let path = cg_path(clip, Some(transform));
            CGContextBeginPath(self.ctx);
            CGContextAddPath(self.ctx, path);
            self.clip_current_path(fill);
            CGPathRelease(path);
            let composite = alpha < 1.0 || !is_plain(blend);
            if composite {
                CGContextSetAlpha(self.ctx, alpha.clamp(0.0, 1.0) as f64);
                CGContextSetBlendMode(self.ctx, blend_mode(blend));
                CGContextBeginTransparencyLayer(self.ctx, null());
            }
            self.layers.push(composite);
        }
    }

    fn push_clip_layer(&mut self, fill: Fill, transform: Affine, clip: &impl Shape) {
        unsafe {
            CGContextSaveGState(self.ctx);
            let path = cg_path(clip, Some(transform));
            CGContextBeginPath(self.ctx);
            CGContextAddPath(self.ctx, path);
            self.clip_current_path(fill);
            CGPathRelease(path);
            self.layers.push(false);
        }
    }

    fn pop_layer(&mut self) {
        if let Some(composite) = self.layers.pop() {
            unsafe {
                if composite {
                    CGContextEndTransparencyLayer(self.ctx);
                }
                CGContextRestoreGState(self.ctx);
            }
        }
    }

    fn stroke<'a>(&mut self, style: &Stroke, transform: Affine, brush: impl Into<PaintRef<'a>>, brush_transform: Option<Affine>, shape: &impl Shape) {
        let paint: PaintRef<'a> = brush.into();
        unsafe {
            CGContextSaveGState(self.ctx);
            CGContextConcatCTM(self.ctx, cg_affine(transform));
            let path = cg_path(shape, None);
            let bbox = CGPathGetBoundingBox(path);
            CGContextBeginPath(self.ctx);
            CGContextAddPath(self.ctx, path);
            CGPathRelease(path);
            self.set_stroke_style(style);
            match paint {
                Paint::Solid(c) => {
                    self.set_stroke(rgba(c));
                    CGContextStrokePath(self.ctx);
                }
                other => {
                    CGContextReplacePathWithStrokedPath(self.ctx);
                    self.paint_current_path(Fill::NonZero, other, brush_transform, bbox);
                }
            }
            CGContextRestoreGState(self.ctx);
        }
    }

    fn fill<'a>(&mut self, style: Fill, transform: Affine, brush: impl Into<PaintRef<'a>>, brush_transform: Option<Affine>, shape: &impl Shape) {
        let paint: PaintRef<'a> = brush.into();
        unsafe {
            CGContextSaveGState(self.ctx);
            CGContextConcatCTM(self.ctx, cg_affine(transform));
            let path = cg_path(shape, None);
            let bbox = CGPathGetBoundingBox(path);
            CGContextBeginPath(self.ctx);
            CGContextAddPath(self.ctx, path);
            CGPathRelease(path);
            self.paint_current_path(style, paint, brush_transform, bbox);
            CGContextRestoreGState(self.ctx);
        }
    }

    fn draw_glyphs<'a, 's: 'a>(
        &'s mut self,
        font: &'a FontData,
        font_size: f32,
        _hint: bool,
        normalized_coords: &'a [NormalizedCoord],
        _embolden: kurbo::Vec2,
        style: impl Into<StyleRef<'a>>,
        brush: impl Into<PaintRef<'a>>,
        brush_alpha: f32,
        transform: Affine,
        glyph_transform: Option<Affine>,
        glyphs: impl Iterator<Item = Glyph> + Clone,
    ) {
        let paint: PaintRef<'a> = brush.into();
        let mut color = match paint {
            Paint::Solid(c) => rgba(c),
            Paint::Gradient(g) => sample_stops(&g.stops, 0.0),
            _ => return,
        };
        color[3] *= brush_alpha.clamp(0.0, 1.0) as f64;
        let mut ids: Vec<CGGlyph> = Vec::new();
        let mut positions: Vec<CGPoint> = Vec::new();
        for g in glyphs {
            if g.id > u16::MAX as u32 {
                continue;
            }
            ids.push(g.id as CGGlyph);
            // y negated: the run is drawn with the CTM flipped back to y-up (below).
            positions.push(CGPoint { x: g.x as f64, y: -(g.y as f64) });
        }
        if ids.is_empty() {
            return;
        }
        unsafe {
            let ct = sized_font(font, font_size, normalized_coords);
            if ct.is_null() {
                return;
            }
            CGContextSaveGState(self.ctx);
            CGContextConcatCTM(self.ctx, cg_affine(transform));
            // Glyph outlines are y-up in font space and CoreText refuses a mirrored text
            // matrix, so flip the CTM back to y-up for the run and negate the baselines.
            // A glyph transform (synthetic italic skew) is conjugated into that space.
            CGContextScaleCTM(self.ctx, 1.0, -1.0);
            let tm = Affine::FLIP_Y * glyph_transform.unwrap_or(Affine::IDENTITY) * Affine::FLIP_Y;
            CGContextSetTextMatrix(self.ctx, cg_affine(tm));
            match style.into() {
                StyleRef::Fill(_) => {
                    CGContextSetTextDrawingMode(self.ctx, kCGTextFill);
                    self.set_fill(color);
                }
                StyleRef::Stroke(s) => {
                    CGContextSetTextDrawingMode(self.ctx, kCGTextStroke);
                    self.set_stroke(color);
                    self.set_stroke_style(s);
                }
            }
            CTFontDrawGlyphs(ct, ids.as_ptr(), positions.as_ptr(), ids.len(), self.ctx);
            CGContextRestoreGState(self.ctx);
        }
    }

    fn draw_box_shadow(&mut self, transform: Affine, rect: Rect, brush: Color, radius: f64, std_dev: f64) {
        let color = rgba(brush);
        if color[3] <= 0.0 || rect.is_zero_area() && std_dev <= 0.0 {
            return;
        }
        if std_dev < 0.05 {
            self.fill(Fill::NonZero, transform, brush, None, &RoundedRect::from_rect(rect, radius));
            return;
        }
        let sigma = std_dev;
        let pad = (3.0 * sigma).ceil();
        let x0 = (rect.x0 - pad).floor();
        let y0 = (rect.y0 - pad).floor();
        let x1 = (rect.x1 + pad).ceil();
        let y1 = (rect.y1 + pad).ceil();
        let (w, h) = ((x1 - x0) as usize, (y1 - y0) as usize);
        if w == 0 || h == 0 || w * h > 16_000_000 {
            return;
        }
        let corner = radius.clamp(0.0, rect.width().min(rect.height()) / 2.0);
        let mut px = vec![0u8; w * h * 4];
        for j in 0..h {
            for i in 0..w {
                let p = Point::new(x0 + i as f64 + 0.5, y0 + j as f64 + 0.5);
                let a = rounded_box_shadow(rect, p, sigma, corner) * color[3];
                if a <= 0.0 {
                    continue;
                }
                let o = (j * w + i) * 4;
                px[o] = (color[0] * a * 255.0 + 0.5) as u8;
                px[o + 1] = (color[1] * a * 255.0 + 0.5) as u8;
                px[o + 2] = (color[2] * a * 255.0 + 0.5) as u8;
                px[o + 3] = (a * 255.0 + 0.5) as u8;
            }
        }
        unsafe {
            let img = cg_image_from_rgba(w, h, &px, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
            if img.is_null() {
                return;
            }
            CGContextSaveGState(self.ctx);
            CGContextConcatCTM(self.ctx, cg_affine(transform));
            CGContextTranslateCTM(self.ctx, x0, y0 + h as f64);
            CGContextScaleCTM(self.ctx, 1.0, -1.0);
            CGContextSetInterpolationQuality(self.ctx, 1);
            CGContextDrawImage(self.ctx, CGRect { origin: CGPoint { x: 0.0, y: 0.0 }, size: CGSize { width: w as f64, height: h as f64 } }, img);
            CGContextRestoreGState(self.ctx);
            CGImageRelease(img);
        }
    }
}

// ───────────────────────── the image renderer ─────────────────────────

/// Paints an anyrender scene into a premultiplied RGBA8 buffer through a CGBitmapContext.
pub struct CgImageRenderer {
    width: u32,
    height: u32,
}

impl RenderContext for CgImageRenderer {}

impl ImageRenderer for CgImageRenderer {
    type ScenePainter<'a> = CgScene;

    fn new(width: u32, height: u32) -> Self {
        Self { width, height }
    }

    fn resize(&mut self, width: u32, height: u32) {
        self.width = width;
        self.height = height;
    }

    fn reset(&mut self) {}

    fn render_to_vec<F: FnOnce(&mut Self::ScenePainter<'_>)>(&mut self, draw_fn: F, vec: &mut Vec<u8>) {
        vec.resize(self.width as usize * self.height as usize * 4, 0);
        self.render(draw_fn, vec);
    }

    fn render<F: FnOnce(&mut Self::ScenePainter<'_>)>(&mut self, draw_fn: F, buffer: &mut [u8]) {
        let (w, h) = (self.width as usize, self.height as usize);
        if w == 0 || h == 0 || buffer.len() < w * h * 4 {
            return;
        }
        buffer[..w * h * 4].fill(0);
        unsafe {
            let space = CGColorSpaceCreateDeviceRGB();
            let ctx = CGBitmapContextCreate(buffer.as_mut_ptr() as *mut c_void, w, h, 8, w * 4, space, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
            CGColorSpaceRelease(space);
            if ctx.is_null() {
                log::error!("CGBitmapContextCreate failed for {w}×{h}");
                return;
            }
            // y-down like the scene; text the way UIKit/AppKit layers draw it: grayscale
            // antialiasing, subpixel positioning, no LCD smoothing on a transparent bitmap.
            CGContextTranslateCTM(ctx, 0.0, h as f64);
            CGContextScaleCTM(ctx, 1.0, -1.0);
            CGContextSetAllowsAntialiasing(ctx, true);
            CGContextSetShouldAntialias(ctx, true);
            CGContextSetAllowsFontSmoothing(ctx, false);
            CGContextSetShouldSmoothFonts(ctx, false);
            CGContextSetAllowsFontSubpixelPositioning(ctx, true);
            CGContextSetShouldSubpixelPositionFonts(ctx, true);
            CGContextSetAllowsFontSubpixelQuantization(ctx, false);
            CGContextSetShouldSubpixelQuantizeFonts(ctx, false);
            let mut scene = CgScene { ctx, layers: Vec::new() };
            draw_fn(&mut scene);
            scene.finish();
            CGContextRelease(ctx);
        }
    }
}

/// Diagnostics: what CoreText calls the face behind a parley font (None when it could not be opened).
pub fn coretext_name(font: &FontData) -> Option<String> {
    unsafe {
        let f = base_font(font);
        if f.is_null() {
            return None;
        }
        let ps = CTFontCopyPostScriptName(f);
        let s = cf_to_string(ps);
        if !ps.is_null() {
            CFRelease(ps);
        }
        Some(s)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use anyrender::render_to_buffer;
    use kurbo::BezPath;
    use peniko::color::palette::css::{BLACK, BLUE, RED};

    fn pixel(buf: &[u8], w: usize, x: usize, y: usize) -> [u8; 4] {
        let i = (y * w + x) * 4;
        [buf[i], buf[i + 1], buf[i + 2], buf[i + 3]]
    }

    #[test]
    fn fills_land_y_down_in_rgba() {
        let buf = render_to_buffer::<CgImageRenderer, _>(
            |s| {
                s.fill(Fill::NonZero, Affine::IDENTITY, RED, None, &Rect::new(0.0, 0.0, 100.0, 50.0));
                s.fill(Fill::NonZero, Affine::translate((80.0, 80.0)), BLUE, None, &Rect::new(0.0, 0.0, 10.0, 10.0));
            },
            100,
            100,
        );
        assert_eq!(pixel(&buf, 100, 50, 25), [255, 0, 0, 255], "top half red");
        assert_eq!(pixel(&buf, 100, 50, 75), [0, 0, 0, 0], "bottom half empty");
        assert_eq!(pixel(&buf, 100, 85, 85), [0, 0, 255, 255], "translated square");
    }

    #[test]
    fn clip_layers_respect_fill_rule() {
        let path = BezPath::from_svg("M0 0H100V100H0Z M25 25H75V75H25Z").unwrap();
        for (fill, composite) in [(Fill::NonZero, false), (Fill::EvenOdd, false), (Fill::NonZero, true), (Fill::EvenOdd, true)] {
            let buf = render_to_buffer::<CgImageRenderer, _>(
                |s| {
                    if composite {
                        s.push_layer(fill, Mix::Normal, 0.5, Affine::IDENTITY, &path, None, None);
                    } else {
                        s.push_clip_layer(fill, Affine::IDENTITY, &path);
                    }
                    s.fill(Fill::NonZero, Affine::IDENTITY, RED, None, &Rect::new(0.0, 0.0, 100.0, 100.0));
                    s.pop_layer();
                },
                100,
                100,
            );
            let edge = pixel(&buf, 100, 10, 10);
            let hole = pixel(&buf, 100, 50, 50);
            // Premultiplied: a half-transparent layer halves red too.
            let want = if composite { 128 } else { 255 };
            assert!((edge[3] as i32 - want).abs() <= 2 && (edge[0] as i32 - want).abs() <= 2, "edge {edge:?} for composite={composite}");
            if fill == Fill::EvenOdd {
                assert_eq!(hole, [0, 0, 0, 0], "even-odd hole");
            } else {
                assert_eq!(hole, edge, "non-zero fills the hole");
            }
        }
    }

    #[test]
    fn box_shadow_sits_under_the_rect() {
        let buf = render_to_buffer::<CgImageRenderer, _>(
            |s| s.draw_box_shadow(Affine::IDENTITY, Rect::new(40.0, 40.0, 60.0, 60.0), BLACK, 0.0, 4.0),
            100,
            100,
        );
        assert!(pixel(&buf, 100, 50, 50)[3] > 240, "centre opaque: {:?}", pixel(&buf, 100, 50, 50));
        let edge = pixel(&buf, 100, 50, 64)[3];
        assert!(edge > 10 && edge < 200, "edge blurred: {edge}");
        assert_eq!(pixel(&buf, 100, 10, 10)[3], 0, "far away clear");
        assert_eq!(pixel(&buf, 100, 50, 80)[3], 0, "beyond 3σ clear");
    }

    #[test]
    fn system_fonts_resolve_to_their_default_instance() {
        use peniko::Blob;
        for path in ["/System/Library/Fonts/SFNS.ttf", "/System/Library/Fonts/Apple Color Emoji.ttc", "/System/Library/Fonts/Helvetica.ttc"] {
            let Ok(bytes) = std::fs::read(path) else { continue };
            let font = FontData { data: Blob::new(Arc::new(bytes)), index: 0 };
            let want = postscript_name(&font);
            assert_eq!(coretext_name(&font), want, "{path}");
        }
    }

    #[test]
    fn gradient_fill_spans_the_shape() {
        let g = Gradient::new_linear((0.0, 0.0), (100.0, 0.0)).with_stops([RED, BLUE]);
        let buf = render_to_buffer::<CgImageRenderer, _>(|s| s.fill(Fill::NonZero, Affine::IDENTITY, &g, None, &Rect::new(0.0, 0.0, 100.0, 100.0)), 100, 100);
        let left = pixel(&buf, 100, 2, 50);
        let right = pixel(&buf, 100, 97, 50);
        assert!(left[0] > 200 && left[2] < 60, "left red: {left:?}");
        assert!(right[2] > 200 && right[0] < 60, "right blue: {right:?}");
    }
}



