// What one paint of the litehtml document consists of, in CSS px: the
// container records litehtml's draw callbacks into this list, and a platform
// painter executes it (CoreGraphics, Direct2D, or Android's Canvas from the
// JSON form). Everything the painter needs is here; nothing refers back to
// litehtml.
#pragma once

#include <cmath>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace twlite {

struct Color {
    uint8_t r = 0, g = 0, b = 0, a = 255;
};

struct Rect {
    float x = 0, y = 0, w = 0, h = 0;
    bool empty() const { return w <= 0 || h <= 0; }
};

/// A 2D affine map, SVG order: x' = a·x + c·y + e, y' = b·x + d·y + f.
struct Affine {
    float a = 1, b = 0, c = 0, d = 1, e = 0, f = 0;
    /// `this` after `next`: maps a point through `next` first.
    Affine operator*(const Affine& next) const {
        return Affine{a * next.a + c * next.b, b * next.a + d * next.b, a * next.c + c * next.d,
                      b * next.c + d * next.d, a * next.e + c * next.f + e, b * next.e + d * next.f + f};
    }
    static Affine translate(float x, float y) { return Affine{1, 0, 0, 1, x, y}; }
    float apply_x(float x, float y) const { return a * x + c * y + e; }
    float apply_y(float x, float y) const { return b * x + d * y + f; }
    /// False when the map flattens the plane and has no inverse.
    bool invert(Affine& out) const {
        float det = a * d - b * c;
        if (!(det != 0) || !std::isfinite(det)) return false;
        out = Affine{d / det, -b / det, -c / det, a / det, (c * f - d * e) / det, (b * e - a * f) / det};
        return true;
    }
    bool operator==(const Affine&) const = default;
};

/// Per-corner elliptical radii (CSS order, x then y). All zero = plain rectangle.
struct Radii {
    float tlx = 0, tly = 0, trx = 0, try_ = 0, brx = 0, bry = 0, blx = 0, bly = 0;
    bool any() const { return tlx > 0 || tly > 0 || trx > 0 || try_ > 0 || brx > 0 || bry > 0 || blx > 0 || bly > 0; }
};

struct Stop {
    float offset = 0;  // 0..1
    Color color;
};

/// A CSS filter function (filter, backdrop-filter), as Filter Effects 1 defines it.
struct Filter {
    enum Kind : uint8_t { Blur, Brightness, Contrast, Grayscale, HueRotate, Invert, Opacity, Saturate, Sepia, DropShadow } kind = Blur;
    float amount = 0;   // Blur and DropShadow: the blur radius in CSS px; HueRotate: degrees; the others: the factor
    float x = 0, y = 0; // DropShadow: the offset
    Color color;        // DropShadow
};
using Filters = std::vector<Filter>;

enum class Cmd {
    FillRect,       // rect, radii, color; clipped to `clip` when has_clip
    StrokeRect,     // rect (the stroke's centre line), radii, color, width
    FillEllipse,    // rect (bounding box), color
    StrokeEllipse,  // rect, color, width
    Text,           // text, font, color, x, y (baseline)
    Image,          // image (or canvas), rect (destination), radii + clip from the background layer
    Linear,         // rect (border box), radii, clip, ax/ay → bx/by, stops
    Radial,         // rect, radii, clip, centre (ax, ay), radii (bx, by), stops
    Conic,          // rect, radii, clip, centre (ax, ay), angle (bx, degrees clockwise from the top), stops
    ClipPush,       // rect, radii: intersect the clip until the matching ClipPop; a `path` (clip-path) replaces both, rect is its bounds
    ClipPop,
    // Only from twidget's litehtml fork (TW_LITEHTML_TWIDGET):
    Path,           // path (SVG geometry, fill and stroke), in CSS px
    GroupPush,      // matrix, concatenated onto the current transform; opacity over the group; hidden = draw nothing until GroupPop;
                    // key, when not 0: the group draws the same wherever the key recurs, inside rect (in the group's own
                    // coordinates, after matrix), so a painter may raster it once
    GroupPop,
    Shadow,         // rect (border box, or padding box when inset), radii, color, offset (ax, ay), blur (bx), spread (by), inset
    FilterPush,     // filter, rect (where the result may paint): what is drawn until the matching FilterPop goes through it.
                    // A painter that has no filters draws it as it is.
    FilterPop,
    Backdrop,       // rect, radii, filter: what is painted under rect so far, filtered, inside rect (backdrop-filter)
};

struct Path {
    enum Op : uint8_t { Move, Line, Quad, Cubic, Close };
    std::vector<Op> ops;
    std::vector<float> points;  // 2 per Move/Line, 4 per Quad, 6 per Cubic
    bool fill = false;  // and evenodd: the fill rule of a clip path too
    bool evenodd = false;
    Color fill_color;
    // fill_gradient: 0 none, 1 linear (x1, y1 → x2, y2), 2 radial (centre x1, y1, radius r, focus fx, fy)
    int gradient = 0;
    float x1 = 0, y1 = 0, x2 = 0, y2 = 0, r = 0, fx = 0, fy = 0;
    std::vector<Stop> stops;
    bool stroke = false;
    Color stroke_color;
    float stroke_width = 1;
    int line_cap = 0;   // 0 butt, 1 round, 2 square
    int line_join = 0;  // 0 miter, 1 round, 2 bevel
    float miter_limit = 4;
    std::vector<float> dashes;
    float dash_offset = 0;
    // stroke gradient: as the fill's, sg[] = x1, y1, x2, y2, r, fx, fy
    int stroke_gradient = 0;
    float sg[7] = {};
    std::vector<Stop> stroke_stops;
};

struct Command {
    Cmd kind = Cmd::FillRect;
    Rect rect;
    Radii radii;
    Rect clip;
    bool has_clip = false;
    Color color;
    float width = 0;
    int font = 0;
    std::string text;
    float x = 0, y = 0;
    int image = 0;
    int canvas = 0;  // Image: a canvas bitmap (Backend::canvas_run) instead of a decoded image
    float ax = 0, ay = 0, bx = 0, by = 0;
    std::vector<Stop> stops;
    std::shared_ptr<const Path> path;
    std::shared_ptr<const Filters> filter;  // FilterPush, Backdrop
    float opacity = 1;
    float matrix[6] = {1, 0, 0, 1, 0, 0};  // a, b, c, d, tx, ty
    bool hidden = false;
    bool inset = false;
    uint64_t key = 0;
};

using DisplayList = std::vector<Command>;

/// JSON for the painters outside C++ (Android, the web): an array of `{"k": <Cmd>, ...}`.
/// - "r" rect, "q" radii (8), "l" clip rect, "c" colour as ARGB, "w" width;
/// - Text: "f" font, "x", "y", "t" text. Image: "i" image or "cv" canvas;
/// - Linear/Radial/Conic and Shadow: "a" (ax, ay, bx, by), "s" stops as offset, ARGB pairs; Shadow: "n" inset;
/// - ClipPush with a clip path, and Path: "p" {"o" op codes (0 move, 1 line, 2 quad, 3 cubic, 4 close),
///   "v" points, "e" evenodd}; a Path adds "f" fill, or "g" gradient (1 linear, 2 radial) with "ga"
///   (x1, y1, x2, y2, r, fx, fy) and "s", and "sc", "sw", "lc", "lj", "ml", "d", "do" for its stroke,
///   with "sg", "sga", "ss" for a gradient stroke as "g", "ga", "s";
/// - GroupPush: "m" matrix (a, b, c, d, tx, ty), "o" opacity, "h" hidden;
/// - FilterPush and Backdrop: "fl" the filters, each [kind, amount, x, y, ARGB].
/// Flags are present only when set.
std::string to_json(const DisplayList& list);

std::string base64(const std::vector<uint8_t>& bytes);

} // namespace twlite
