// What a platform executes for a 2D canvas: drawing operations already resolved by the shared
// interpreter (canvas.h). Geometry is in canvas pixels with the current transform applied, so a
// backend only fills, strokes, clips and blits. Ported in substance from twidget's WidgetCanvas.swift.
#pragma once

#include "display_list.h"

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace twlite {

/// A path as Move/Line/Quad/Cubic/Close with its points.
struct Geometry {
    std::vector<Path::Op> ops;
    std::vector<float> points;
    bool empty() const { return ops.empty(); }
};

struct CanvasPaint {
    enum Kind : uint8_t { Solid, Linear, Radial, Conic } kind = Solid;
    Color color;
    /// Linear: x0, y0, x1, y1. Radial: x0, y0, r0, x1, y1, r1. Conic: start angle (radians), x, y. In user space.
    float args[6] = {};
    std::vector<Stop> stops;  // sorted by offset
};

/// globalCompositeOperation, in the spec's order.
enum class Composite : uint8_t {
    SourceOver, SourceIn, SourceOut, SourceAtop, DestinationOver, DestinationIn, DestinationOut, DestinationAtop,
    Lighter, Copy, Xor, Multiply, Screen, Overlay, Darken, Lighten, ColorDodge, ColorBurn, HardLight, SoftLight,
    Difference, Exclusion, Hue, Saturation, Color, Luminosity,
};

struct CanvasClip {
    Geometry path;  // canvas pixels
    bool evenodd = false;
};

struct CanvasOp {
    enum Kind : uint8_t {
        Reset,     // the bitmap is (re)allocated transparent: after a resize, and first of all
        Fill,      // path (canvas px), evenodd, paint
        Stroke,    // path (canvas px), paint, line style; the width is in user space, so under `matrix`
        Clear,     // path: the cleared area (canvas px), to transparent
        Text,      // text in `font`, baseline at (x, y) user space: drawn at text_dx under scale_x (maxWidth)
        Image,     // image or canvas: src (sx, sy, sw, sh; sw < 0 = all) into dst (dx, dy, dw, dh) user space
        PutImage,  // rgba (straight alpha) w × h at (dx, dy) canvas px; ignores transform, alpha, shadow, clip, composite
    } kind = Fill;

    Geometry path;
    bool evenodd = false;
    CanvasPaint paint;
    float matrix[6] = {1, 0, 0, 1, 0, 0};  // user → canvas px: a, b, c, d, tx, ty

    // stroke
    float line_width = 1;
    int line_cap = 0;   // 0 butt, 1 round, 2 square
    int line_join = 0;  // 0 miter, 1 round, 2 bevel
    float miter_limit = 10;
    std::vector<float> dashes;
    float dash_offset = 0;

    // compositing, common to every kind but Reset/PutImage
    float alpha = 1;
    Composite composite = Composite::SourceOver;
    Color shadow_color{0, 0, 0, 0};
    float shadow_blur = 0, shadow_x = 0, shadow_y = 0;
    std::shared_ptr<const std::vector<CanvasClip>> clips;  // intersected, in order; null = none

    // text
    int font = 0;
    std::string text;
    float x = 0, y = 0, scale_x = 1;
    float text_dx = 0;  // textAlign offset, before scale_x
    bool stroke_text = false;

    // image
    int image = 0;   // a Backend::load_image id
    int canvas = 0;  // or another canvas
    float sx = 0, sy = 0, sw = -1, sh = -1;
    float dx = 0, dy = 0, dw = 0, dh = 0;
    bool smoothing = true;

    // put
    std::shared_ptr<const std::vector<uint8_t>> rgba;
    int w = 0, h = 0;

    bool has_shadow() const { return shadow_color.a > 0 && (shadow_blur > 0 || shadow_x != 0 || shadow_y != 0); }
};

} // namespace twlite
