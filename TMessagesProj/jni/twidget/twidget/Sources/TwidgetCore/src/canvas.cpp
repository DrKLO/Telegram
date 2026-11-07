#include "canvas.h"

#include "css.h"

#include <algorithm>
#include <cmath>
#include <unordered_map>
#include <string_view>

using twidget::Json;

namespace twlite {

namespace {

// Canvas method and property names, looked up once a command instead of compared one by one down
// the chain of them (the Swift renderer interns them too).
enum class Method : uint8_t { unknown, beginPath, clearRect, clip, drawImage, fill, fillRect, fillText, putImageData, resetTransform, restore, rotate, save, scale, setLineDash, setTransform, stroke, strokeRect, strokeText, transform, translate, addPath, arc, arcTo, bezierCurveTo, closePath, ellipse, lineTo, moveTo, quadraticCurveTo, rect, roundRect };

Method method_of(const std::string& name) {
    static const std::unordered_map<std::string_view, Method> names = {
        {"beginPath", Method::beginPath},
        {"clearRect", Method::clearRect},
        {"clip", Method::clip},
        {"drawImage", Method::drawImage},
        {"fill", Method::fill},
        {"fillRect", Method::fillRect},
        {"fillText", Method::fillText},
        {"putImageData", Method::putImageData},
        {"resetTransform", Method::resetTransform},
        {"restore", Method::restore},
        {"rotate", Method::rotate},
        {"save", Method::save},
        {"scale", Method::scale},
        {"setLineDash", Method::setLineDash},
        {"setTransform", Method::setTransform},
        {"stroke", Method::stroke},
        {"strokeRect", Method::strokeRect},
        {"strokeText", Method::strokeText},
        {"transform", Method::transform},
        {"translate", Method::translate},
        {"addPath", Method::addPath},
        {"arc", Method::arc},
        {"arcTo", Method::arcTo},
        {"bezierCurveTo", Method::bezierCurveTo},
        {"closePath", Method::closePath},
        {"ellipse", Method::ellipse},
        {"lineTo", Method::lineTo},
        {"moveTo", Method::moveTo},
        {"quadraticCurveTo", Method::quadraticCurveTo},
        {"rect", Method::rect},
        {"roundRect", Method::roundRect},
    };
    auto it = names.find(name);
    return it == names.end() ? Method::unknown : it->second;
}

enum class Property : uint8_t { unknown, fillStyle, font, globalAlpha, globalCompositeOperation, imageSmoothingEnabled, lineCap, lineDashOffset, lineJoin, lineWidth, miterLimit, shadowBlur, shadowColor, shadowOffsetX, shadowOffsetY, strokeStyle, textAlign, textBaseline };

Property property_of(const std::string& name) {
    static const std::unordered_map<std::string_view, Property> names = {
        {"fillStyle", Property::fillStyle},
        {"font", Property::font},
        {"globalAlpha", Property::globalAlpha},
        {"globalCompositeOperation", Property::globalCompositeOperation},
        {"imageSmoothingEnabled", Property::imageSmoothingEnabled},
        {"lineCap", Property::lineCap},
        {"lineDashOffset", Property::lineDashOffset},
        {"lineJoin", Property::lineJoin},
        {"lineWidth", Property::lineWidth},
        {"miterLimit", Property::miterLimit},
        {"shadowBlur", Property::shadowBlur},
        {"shadowColor", Property::shadowColor},
        {"shadowOffsetX", Property::shadowOffsetX},
        {"shadowOffsetY", Property::shadowOffsetY},
        {"strokeStyle", Property::strokeStyle},
        {"textAlign", Property::textAlign},
        {"textBaseline", Property::textBaseline},
    };
    auto it = names.find(name);
    return it == names.end() ? Property::unknown : it->second;
}


constexpr double PI = 3.14159265358979323846;
constexpr size_t MAX_OBJECTS = 4096;
constexpr size_t MAX_STOPS = 256;
constexpr size_t MAX_STACK = 512;
constexpr size_t MAX_TEXT = 4096;
constexpr size_t MAX_COLORS = 256;

std::vector<uint8_t> base64(const std::string& in) {
    static int8_t table[256];
    static bool ready = false;
    if (!ready) {
        std::fill(std::begin(table), std::end(table), int8_t(-1));
        const char* alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < 64; i++) table[(unsigned char)alphabet[i]] = int8_t(i);
        ready = true;
    }
    std::vector<uint8_t> out;
    out.reserve(in.size() * 3 / 4);
    int value = 0, bits = -8;
    for (unsigned char c : in) {
        if (table[c] < 0) continue;
        value = (value << 6) | table[c];
        bits += 6;
        if (bits >= 0) {
            out.push_back(uint8_t((value >> bits) & 0xFF));
            bits -= 8;
        }
    }
    return out;
}

Composite composite_of(const std::string& name, bool& known) {
    static const char* const NAMES[] = {
        "source-over", "source-in", "source-out", "source-atop", "destination-over", "destination-in", "destination-out", "destination-atop",
        "lighter", "copy", "xor", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge", "color-burn", "hard-light", "soft-light",
        "difference", "exclusion", "hue", "saturation", "color", "luminosity",
    };
    for (size_t i = 0; i < std::size(NAMES); i++) {
        if (name == NAMES[i]) {
            known = true;
            return Composite(i);
        }
    }
    known = false;
    return Composite::SourceOver;
}

/// Appends an elliptical arc as cubic béziers (at most a quarter turn each), connecting from the
/// current point with a line when there is one. Angles in radians, y down, clockwise positive.
template <class Emit>
void arc_segments(double cx, double cy, double rx, double ry, double rotation, double start, double sweep, Emit emit) {
    int segments = std::max(1, int(std::ceil(std::fabs(sweep) / (PI / 2) - 1e-9)));
    double step = sweep / segments;
    double k = 4.0 / 3.0 * std::tan(step / 4);
    double cr = std::cos(rotation), sr = std::sin(rotation);
    auto point = [&](double x, double y, double& ox, double& oy) {
        ox = cx + rx * x * cr - ry * y * sr;
        oy = cy + rx * x * sr + ry * y * cr;
    };
    double a = start;
    for (int i = 0; i < segments; i++) {
        double b = a + step;
        double ca = std::cos(a), sa = std::sin(a), cb = std::cos(b), sb = std::sin(b);
        double p1x, p1y, p2x, p2y, p3x, p3y;
        point(ca - k * sa, sa + k * ca, p1x, p1y);
        point(cb + k * sb, sb - k * cb, p2x, p2y);
        point(cb, sb, p3x, p3y);
        emit(p1x, p1y, p2x, p2y, p3x, p3y);
        a = b;
    }
}

double sweep_of(double start, double end, bool anticlockwise) {
    double sweep = end - start;
    if (!anticlockwise) {
        if (sweep >= 2 * PI) return 2 * PI;
        sweep = std::fmod(sweep, 2 * PI);
        if (sweep < 0) sweep += 2 * PI;
    } else {
        if (-sweep >= 2 * PI) return -2 * PI;
        sweep = std::fmod(sweep, 2 * PI);
        if (sweep > 0) sweep -= 2 * PI;
    }
    return sweep;
}

/// Polylines of a geometry, curves flattened, for hit testing.
std::vector<std::vector<std::pair<double, double>>> flatten(const Geometry& g) {
    std::vector<std::vector<std::pair<double, double>>> out;
    const float* p = g.points.data();
    size_t n = g.points.size(), i = 0;
    double cx = 0, cy = 0, sx = 0, sy = 0;
    auto line = [&](double x, double y) {
        if (out.empty()) out.push_back({{cx, cy}});
        out.back().emplace_back(x, y);
        cx = x;
        cy = y;
    };
    for (auto op : g.ops) {
        size_t need = op == Path::Cubic ? 6 : op == Path::Quad ? 4 : op == Path::Close ? 0 : 2;
        if (i + need > n) break;
        switch (op) {
        case Path::Move:
            out.push_back({{p[i], p[i + 1]}});
            cx = sx = p[i];
            cy = sy = p[i + 1];
            break;
        case Path::Line: line(p[i], p[i + 1]); break;
        case Path::Quad: {
            double x0 = cx, y0 = cy;
            for (int s = 1; s <= 16; s++) {
                double t = s / 16.0, u = 1 - t;
                line(u * u * x0 + 2 * u * t * p[i] + t * t * p[i + 2], u * u * y0 + 2 * u * t * p[i + 1] + t * t * p[i + 3]);
            }
            break;
        }
        case Path::Cubic: {
            double x0 = cx, y0 = cy;
            for (int s = 1; s <= 16; s++) {
                double t = s / 16.0, u = 1 - t;
                double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
                line(a * x0 + b * p[i] + c * p[i + 2] + d * p[i + 4], a * y0 + b * p[i + 1] + c * p[i + 3] + d * p[i + 5]);
            }
            break;
        }
        case Path::Close:
            line(sx, sy);
            break;
        }
        i += need;
    }
    return out;
}

bool contains(const Geometry& g, double x, double y, bool evenodd) {
    int winding = 0, crossings = 0;
    for (const auto& poly : flatten(g)) {
        if (poly.size() < 2) continue;
        // Subpaths fill as if closed.
        for (size_t k = 0; k < poly.size(); k++) {
            auto [x0, y0] = poly[k];
            auto [x1, y1] = poly[(k + 1) % poly.size()];
            if ((y0 <= y) != (y1 <= y)) {
                double at = x0 + (y - y0) * (x1 - x0) / (y1 - y0);
                if (at > x) {
                    crossings++;
                    winding += y1 > y0 ? 1 : -1;
                }
            }
        }
    }
    return evenodd ? (crossings & 1) != 0 : winding != 0;
}

bool near_stroke(const Geometry& g, double x, double y, double half) {
    for (const auto& poly : flatten(g)) {
        for (size_t k = 0; k + 1 < poly.size(); k++) {
            auto [x0, y0] = poly[k];
            auto [x1, y1] = poly[k + 1];
            double dx = x1 - x0, dy = y1 - y0, len = dx * dx + dy * dy;
            double t = len > 0 ? std::clamp(((x - x0) * dx + (y - y0) * dy) / len, 0.0, 1.0) : 0;
            if (std::hypot(x - (x0 + t * dx), y - (y0 + t * dy)) <= half) return true;
        }
    }
    return false;
}

} // namespace

bool geometry_contains(const Geometry& g, double x, double y, bool evenodd) { return contains(g, x, y, evenodd); }

struct Canvas::Value {
    enum Kind { Null, Number, String, List, Gradient, PathRef, Node, Pixels } kind = Null;
    double number = 0;
    std::string string;
    std::vector<float> list;
    int64_t ref = 0;
    std::shared_ptr<const std::vector<uint8_t>> pixels;
    int w = 0, h = 0;

    static Value of(const Json& j) {
        Value v;
        if (j.isNumber()) { v.kind = Number; v.number = j.asNumber(); }
        else if (j.isBool()) { v.kind = Number; v.number = j.asBool() ? 1 : 0; }
        else if (j.isString()) { v.kind = String; v.string = j.asString(); }
        else if (j.isArray()) {
            v.kind = List;
            for (const Json& x : j.arr()) v.list.push_back(float(x.asNumber()));
        } else if (j.isObject()) {
            if (const Json* g = j.find("g")) { v.kind = Gradient; v.ref = int64_t(g->asNumber()); }
            else if (const Json* p = j.find("p")) { v.kind = PathRef; v.ref = int64_t(p->asNumber()); }
            else if (const Json* n = j.find("n")) { v.kind = Node; v.ref = int64_t(n->asNumber()); }
            else if (const Json* b = j.find("b64"); b && b->isString()) {
                v.kind = Pixels;
                v.pixels = std::make_shared<std::vector<uint8_t>>(base64(b->asString()));
                v.w = j["w"].asInt();
                v.h = j["h"].asInt();
            }
        }
        return v;
    }
};

// ───────────────────────── matrices and geometry ─────────────────────────

Canvas::Matrix Canvas::Matrix::then(const Matrix& m) const {
    Matrix r;
    r.a = a * m.a + b * m.c;
    r.b = a * m.b + b * m.d;
    r.c = c * m.a + d * m.c;
    r.d = c * m.b + d * m.d;
    r.e = e * m.a + f * m.c + m.e;
    r.f = e * m.b + f * m.d + m.f;
    return r;
}

bool Canvas::Matrix::invert(Matrix& out) const {
    double det = a * d - b * c;
    if (det == 0 || !std::isfinite(det)) return false;
    out.a = d / det;
    out.b = -b / det;
    out.c = -c / det;
    out.d = a / det;
    out.e = (c * f - d * e) / det;
    out.f = (b * e - a * f) / det;
    return true;
}

Geometry Canvas::transformed(const Geometry& path, const Matrix& m) const {
    Geometry g;
    g.ops = path.ops;
    g.points.resize(path.points.size());
    for (size_t i = 0; i + 1 < path.points.size(); i += 2) m.apply(path.points[i], path.points[i + 1], g.points[i], g.points[i + 1]);
    return g;
}

Geometry Canvas::rect_geometry(double x, double y, double w, double h) const {
    Builder b;
    path_method(b, state_.matrix, "rect", {Value{Value::Number, x}, Value{Value::Number, y}, Value{Value::Number, w}, Value{Value::Number, h}});
    return std::move(b.geometry);
}

// ───────────────────────── interpreter ─────────────────────────

Canvas::Canvas(int id, int width, int height, CanvasHost& host) : id_(id), width_(0), height_(0), host_(host) {
    std::vector<CanvasOp> ignored;
    resize(width, height, ignored);
}

void Canvas::resize(int width, int height, std::vector<CanvasOp>& out) {
    width = std::clamp(width, 0, MAX_SIDE);
    height = std::clamp(height, 0, MAX_SIDE);
    // A size past the widget's canvas pixels is not applied but still clears the bitmap: the canvas keeps the size it
    // had, a new one stays 0 × 0 (widget-sandbox v0.3.0).
    if (int64_t(width) * height <= std::min<int64_t>(MAX_PIXELS, host_.canvas_allowance(id_))) {
        width_ = width;
        height_ = height;
    }
    state_ = State();
    stack_.clear();
    path_ = Builder();
    CanvasOp reset;
    reset.kind = CanvasOp::Reset;
    out.push_back(std::move(reset));
}

void Canvas::run(const Json& cmds, std::vector<CanvasOp>& out) {
    std::vector<Value> args;
    for (const Json& cmd : cmds.arr()) {
        const auto& list = cmd.arr();
        if (list.size() < 2 || !list[0].isString()) continue;
        const std::string& kind = list[0].asString();
        args.clear();
        auto rest = [&](size_t from) {
            for (size_t i = from; i < list.size(); i++) args.push_back(Value::of(list[i]));
        };
        if (kind == "c") {
            rest(2);
            if (list[1].isString()) call_method(list[1].asString(), args, out);
        } else if (kind == "s") {
            rest(2);
            if (list[1].isString() && !args.empty()) set(list[1].asString(), args[0]);
        } else if (kind == "r") {
            resize(list[1].asInt(), list.size() > 2 ? list[2].asInt() : 0, out);
        } else if (kind == "g") {
            int gid = list[1].asInt();
            if (list.size() < 3 || !list[2].isString()) continue;
            if (!gradients_.count(gid) && gradients_.size() >= MAX_OBJECTS) gradients_.erase(gradients_.begin());
            Gradient g;
            g.kind = list[2].asString();
            for (size_t i = 3; i < list.size(); i++) g.args.push_back(float(list[i].asNumber()));
            gradients_[gid] = std::move(g);
        } else if (kind == "gs") {
            auto it = gradients_.find(list[1].asInt());
            Color color;
            if (it == gradients_.end() || list.size() < 4 || it->second.stops.size() >= MAX_STOPS || !color_of(list[3].asString(), color)) continue;
            Stop stop{std::clamp(float(list[2].asNumber()), 0.0f, 1.0f), color};
            auto& stops = it->second.stops;
            stops.insert(std::upper_bound(stops.begin(), stops.end(), stop, [](const Stop& a, const Stop& b) { return a.offset < b.offset; }), stop);
        } else if (kind == "pn") {
            int pid = list[1].asInt();
            // At the cap a new path is refused: a Path2D is sent once, so evicting one a widget still
            // holds would make it vanish.
            if (!paths_.count(pid) && paths_.size() >= MAX_OBJECTS) continue;
            Builder b;
            if (list.size() > 2 && list[2].isString() && list[2].asString().size() <= (1u << 20)) {
                append_svg_path(list[2].asString(), b.geometry);
                // An SVG path ends where its last command did; later Path2D calls continue from there.
                const auto& pts = b.geometry.points;
                if (pts.size() >= 2) {
                    b.has_point = true;
                    b.open = b.geometry.ops.back() != Path::Close;
                    b.cx = pts[pts.size() - 2];
                    b.cy = pts[pts.size() - 1];
                }
            }
            paths_[pid] = std::move(b);
        } else if (kind == "pc") {
            auto it = paths_.find(list[1].asInt());
            if (it == paths_.end() || list.size() < 3 || !list[2].isString()) continue;
            rest(3);
            path_method(it->second, Matrix(), list[2].asString(), args);
        }
    }
}

void Canvas::set(const std::string& p, const Value& v) {
    State& s = state_;
    const Property q = property_of(p);
    Color color;
    if (q == Property::fillStyle || q == Property::strokeStyle) {
        Paint& paint = q == Property::fillStyle ? s.fill : s.stroke;
        if (v.kind == Value::String && color_of(v.string, color)) paint = Paint{false, color, 0};
        else if (v.kind == Value::Gradient) paint = Paint{true, Color{}, int(v.ref)};
    } else if (q == Property::lineWidth) {
        if (v.kind == Value::Number && v.number > 0 && std::isfinite(v.number)) s.line_width = float(v.number);
    } else if (q == Property::lineCap) {
        s.line_cap = v.string == "round" ? 1 : v.string == "square" ? 2 : v.string == "butt" ? 0 : s.line_cap;
    } else if (q == Property::lineJoin) {
        s.line_join = v.string == "round" ? 1 : v.string == "bevel" ? 2 : v.string == "miter" ? 0 : s.line_join;
    } else if (q == Property::miterLimit) {
        if (v.kind == Value::Number && v.number > 0 && std::isfinite(v.number)) s.miter_limit = float(v.number);
    } else if (q == Property::lineDashOffset) {
        if (v.kind == Value::Number && std::isfinite(v.number)) s.dash_offset = float(v.number);
    } else if (q == Property::font) {
        if (v.kind == Value::String && !v.string.empty()) s.font = v.string;
    } else if (q == Property::textAlign) {
        if (v.kind == Value::String) s.align = v.string;
    } else if (q == Property::textBaseline) {
        if (v.kind == Value::String) s.baseline = v.string;
    } else if (q == Property::globalAlpha) {
        if (v.kind == Value::Number && v.number >= 0 && v.number <= 1) s.alpha = float(v.number);
    } else if (q == Property::globalCompositeOperation) {
        bool known;
        Composite c = composite_of(v.string, known);
        if (known) s.composite = c;
    } else if (q == Property::shadowBlur) {
        if (v.kind == Value::Number && v.number >= 0 && std::isfinite(v.number)) s.shadow_blur = float(std::min(v.number, 100.0));
    } else if (q == Property::shadowColor) {
        if (v.kind == Value::String && color_of(v.string, color)) s.shadow_color = color;
    } else if (q == Property::shadowOffsetX) {
        if (v.kind == Value::Number && std::isfinite(v.number)) s.shadow_x = float(v.number);
    } else if (q == Property::shadowOffsetY) {
        if (v.kind == Value::Number && std::isfinite(v.number)) s.shadow_y = float(v.number);
    } else if (q == Property::imageSmoothingEnabled) {
        if (v.kind == Value::Number) s.smoothing = v.number != 0;
    }
}

CanvasOp Canvas::op(CanvasOp::Kind kind) const {
    CanvasOp o;
    o.kind = kind;
    const Matrix& m = state_.matrix;
    o.matrix[0] = float(m.a); o.matrix[1] = float(m.b); o.matrix[2] = float(m.c);
    o.matrix[3] = float(m.d); o.matrix[4] = float(m.e); o.matrix[5] = float(m.f);
    o.alpha = state_.alpha;
    o.composite = state_.composite;
    o.shadow_color = state_.shadow_color;
    o.shadow_blur = state_.shadow_blur;
    o.shadow_x = state_.shadow_x;
    o.shadow_y = state_.shadow_y;
    o.clips = state_.clips;
    o.smoothing = state_.smoothing;
    return o;
}

bool Canvas::paint_of(const Paint& paint, CanvasPaint& out) const {
    if (!paint.gradient) {
        out.kind = CanvasPaint::Solid;
        out.color = paint.color;
        return true;
    }
    auto it = gradients_.find(paint.id);
    if (it == gradients_.end()) return false;
    const Gradient& g = it->second;
    out.stops = g.stops;
    auto args = [&](size_t n) {
        if (g.args.size() < n) return false;
        for (size_t i = 0; i < n; i++) out.args[i] = std::isfinite(g.args[i]) ? g.args[i] : 0;
        return true;
    };
    if (g.kind == "linear" && args(4)) out.kind = CanvasPaint::Linear;
    else if (g.kind == "radial" && args(6)) out.kind = CanvasPaint::Radial;
    else if (g.kind == "conic" && args(3)) out.kind = CanvasPaint::Conic;
    else return false;  // patterns are not drawn
    // A gradient without stops paints nothing, as in browsers; so does one with no length (the canvas
    // spec), which is what widget-sandbox's validator makes of a conic gradient or a pattern.
    const float* a = out.args;
    bool degenerate = (out.kind == CanvasPaint::Linear && a[0] == a[2] && a[1] == a[3]) ||
                      (out.kind == CanvasPaint::Radial && a[0] == a[3] && a[1] == a[4] && a[2] == a[5]);
    if (out.stops.empty() || degenerate) out = CanvasPaint{CanvasPaint::Solid, Color{0, 0, 0, 0}};
    return true;
}

void Canvas::fill(const Geometry& path, bool evenodd, std::vector<CanvasOp>& out) {
    if (path.empty()) return;
    CanvasOp o = op(CanvasOp::Fill);
    if (!paint_of(state_.fill, o.paint)) return;
    o.path = path;
    o.evenodd = evenodd;
    out.push_back(std::move(o));
}

void Canvas::stroke(const Geometry& path, std::vector<CanvasOp>& out) {
    if (path.empty()) return;
    CanvasOp o = op(CanvasOp::Stroke);
    if (!paint_of(state_.stroke, o.paint)) return;
    o.path = path;
    o.line_width = state_.line_width;
    o.line_cap = state_.line_cap;
    o.line_join = state_.line_join;
    o.miter_limit = state_.miter_limit;
    o.dashes = state_.dash;
    o.dash_offset = state_.dash_offset;
    out.push_back(std::move(o));
}

void Canvas::text(const std::string& full, double x, double y, const Value* max_width, bool stroke_text, std::vector<CanvasOp>& out) {
    // Truncated at a UTF-8 boundary.
    size_t cut = std::min(full.size(), MAX_TEXT);
    while (cut < full.size() && cut > 0 && (uint8_t(full[cut]) & 0xC0) == 0x80) cut--;
    std::string s = full.substr(0, cut);
    FontMetrics m;
    int font = host_.canvas_font(state_.font, m);
    if (!font || s.empty()) return;
    float width = host_.canvas_text_width(font, s);
    CanvasOp o = op(CanvasOp::Text);
    if (!paint_of(stroke_text ? state_.stroke : state_.fill, o.paint)) return;
    const std::string& align = state_.align;
    o.text_dx = align == "center" ? -width / 2 : (align == "right" || align == "end") ? -width : 0;
    const std::string& baseline = state_.baseline;
    // As Chromium places them: top, middle and bottom on the em box, hanging and ideographic on the
    // font's own ascent and descent.
    float shift = 0;
    if (baseline == "top") shift = m.em_ascent;
    else if (baseline == "hanging") shift = m.ascent * 0.8f;
    else if (baseline == "middle") shift = (m.em_ascent - m.em_descent) / 2;
    else if (baseline == "bottom") shift = -m.em_descent;
    else if (baseline == "ideographic") shift = -m.descent;
    o.x = float(x);
    o.y = float(y) + shift;
    if (max_width && max_width->kind == Value::Number && max_width->number > 0 && width > max_width->number) o.scale_x = float(max_width->number / width);
    else if (max_width && max_width->kind == Value::Number && max_width->number <= 0) return;
    o.font = font;
    o.text = std::move(s);
    o.stroke_text = stroke_text;
    o.line_width = state_.line_width;
    if (stroke_text) {
        o.line_cap = state_.line_cap;
        o.line_join = state_.line_join;
        o.miter_limit = state_.miter_limit;
        o.dashes = state_.dash;
        o.dash_offset = state_.dash_offset;
    }
    out.push_back(std::move(o));
}

void Canvas::image(const std::vector<Value>& a, std::vector<CanvasOp>& out) {
    if (a.empty() || a[0].kind != Value::Node) return;
    int image = 0, canvas = 0;
    float w = 0, h = 0;
    if (!host_.canvas_source(a[0].ref, image, canvas, w, h) || w <= 0 || h <= 0) return;
    auto n = [&](size_t i) { return i < a.size() && a[i].kind == Value::Number && std::isfinite(a[i].number) ? float(a[i].number) : 0.0f; };
    CanvasOp o = op(CanvasOp::Image);
    o.image = image;
    o.canvas = canvas;
    switch (a.size()) {
    case 3: o.dx = n(1); o.dy = n(2); o.dw = w; o.dh = h; break;
    case 5: o.dx = n(1); o.dy = n(2); o.dw = n(3); o.dh = n(4); break;
    case 9:
        o.sx = n(1); o.sy = n(2); o.sw = n(3); o.sh = n(4);
        o.dx = n(5); o.dy = n(6); o.dw = n(7); o.dh = n(8);
        break;
    default: return;
    }
    if (o.dw == 0 || o.dh == 0 || (a.size() == 9 && (o.sw == 0 || o.sh == 0))) return;
    if (o.image || o.canvas) {
        out.push_back(std::move(o));
        return;
    }
    // A refused image is widget-sandbox's placeholder (url-policy.ts, PLACEHOLDER_IMAGE), which no
    // backend holds: its drawing, as a document draws it (draw_placeholder), from the source rect into
    // the destination.
    double sx = a.size() == 9 ? o.sx : 0, sy = a.size() == 9 ? o.sy : 0, sw = a.size() == 9 ? o.sw : w, sh = a.size() == 9 ? o.sh : h;
    double kx = o.dw / sw * w / 48, ky = o.dh / sh * h / 48;
    auto at = [&](double px, double py) { return std::pair{o.dx + (px * w / 48 - sx) * o.dw / sw, o.dy + (py * h / 48 - sy) * o.dh / sh}; };
    auto number = [](double v) { return Value{Value::Number, v}; };
    auto clips = state_.clips ? std::make_shared<std::vector<CanvasClip>>(*state_.clips) : std::make_shared<std::vector<CanvasClip>>();
    clips->push_back(CanvasClip{rect_geometry(o.dx, o.dy, o.dw, o.dh), false});
    auto draw = [&](Geometry g, Color color) {
        CanvasOp f = op(CanvasOp::Fill);
        f.paint.kind = CanvasPaint::Solid;
        f.paint.color = color;
        f.path = std::move(g);
        f.clips = clips;
        out.push_back(std::move(f));
    };
    const Color faint{136, 136, 136, 34}, mark{136, 136, 136, 102};
    auto [x0, y0] = at(0, 0);
    draw(rect_geometry(x0, y0, 48 * kx, 48 * ky), faint);
    Builder hills;
    const double points[] = {8, 36, 18, 24, 26, 33, 32, 26, 40, 36};
    for (size_t i = 0; i < std::size(points); i += 2) {
        auto [x, y] = at(points[i], points[i + 1]);
        path_method(hills, state_.matrix, i ? "lineTo" : "moveTo", {number(x), number(y)});
    }
    path_method(hills, state_.matrix, "closePath", {});
    draw(std::move(hills.geometry), mark);
    Builder sun;
    auto [cx, cy] = at(33, 15);
    path_method(sun, state_.matrix, "ellipse", {number(cx), number(cy), number(4 * std::fabs(kx)), number(4 * std::fabs(ky)), number(0), number(0), number(2 * PI)});
    draw(std::move(sun.geometry), mark);
}

bool Canvas::color_of(const std::string& text, Color& out) {
    // A widget sets the same few colours frame after frame.
    if (auto it = colors_.find(text); it != colors_.end()) {
        if (!it->second) return false;
        out = *it->second;
        return true;
    }
    if (colors_.size() >= MAX_COLORS) colors_.clear();
    Color parsed;
    bool ok = css::parse_color(text, parsed);
    colors_.emplace(text, ok ? std::optional<Color>(parsed) : std::nullopt);
    if (ok) out = parsed;
    return ok;
}

void Canvas::call_method(const std::string& method, const std::vector<Value>& a, std::vector<CanvasOp>& out) {
    if (width_ == 0 || height_ == 0) return;
    const Method m = method_of(method);
    auto n = [&](size_t i, double fallback = 0) { return i < a.size() && a[i].kind == Value::Number && std::isfinite(a[i].number) ? a[i].number : fallback; };
    auto evenodd = [&](size_t i) { return i < a.size() && a[i].kind == Value::String && a[i].string == "evenodd"; };
    auto path_arg = [&]() -> const Builder* {
        if (a.empty() || a[0].kind != Value::PathRef) return nullptr;
        auto it = paths_.find(int(a[0].ref));
        return it == paths_.end() ? nullptr : &it->second;
    };
    State& s = state_;
    if (m == Method::save) {
        if (stack_.size() < MAX_STACK) stack_.push_back(s);
    } else if (m == Method::restore) {
        if (!stack_.empty()) {
            s = std::move(stack_.back());
            stack_.pop_back();
        }
    } else if (m == Method::scale) {
        s.matrix = Matrix{n(0, 1), 0, 0, n(1, 1), 0, 0}.then(s.matrix);
    } else if (m == Method::rotate) {
        double r = n(0), c = std::cos(r), si = std::sin(r);
        s.matrix = Matrix{c, si, -si, c, 0, 0}.then(s.matrix);
    } else if (m == Method::translate) {
        s.matrix = Matrix{1, 0, 0, 1, n(0), n(1)}.then(s.matrix);
    } else if (m == Method::transform) {
        s.matrix = Matrix{n(0, 1), n(1), n(2), n(3, 1), n(4), n(5)}.then(s.matrix);
    } else if (m == Method::setTransform) {
        if (!a.empty() && a[0].kind == Value::List && a[0].list.size() >= 6) {
            const auto& l = a[0].list;
            s.matrix = Matrix{l[0], l[1], l[2], l[3], l[4], l[5]};
        } else s.matrix = Matrix{n(0, 1), n(1), n(2), n(3, 1), n(4), n(5)};
    } else if (m == Method::resetTransform) {
        s.matrix = Matrix();
    } else if (m == Method::clearRect) {
        CanvasOp o = op(CanvasOp::Clear);
        o.path = rect_geometry(n(0), n(1), n(2), n(3));
        out.push_back(std::move(o));
    } else if (m == Method::fillRect) {
        fill(rect_geometry(n(0), n(1), n(2), n(3)), false, out);
    } else if (m == Method::strokeRect) {
        stroke(rect_geometry(n(0), n(1), n(2), n(3)), out);
    } else if (m == Method::beginPath) {
        path_ = Builder();
    } else if (m == Method::fill) {
        if (const Builder* p = path_arg()) fill(transformed(p->geometry, s.matrix), evenodd(1), out);
        else fill(path_.geometry, evenodd(0), out);
    } else if (m == Method::stroke) {
        if (const Builder* p = path_arg()) stroke(transformed(p->geometry, s.matrix), out);
        else stroke(path_.geometry, out);
    } else if (m == Method::clip) {
        CanvasClip clip;
        if (const Builder* p = path_arg()) {
            clip.path = transformed(p->geometry, s.matrix);
            clip.evenodd = evenodd(1);
        } else {
            clip.path = path_.geometry;
            clip.evenodd = evenodd(0);
        }
        auto clips = s.clips ? std::make_shared<std::vector<CanvasClip>>(*s.clips) : std::make_shared<std::vector<CanvasClip>>();
        // An empty path clips everything away.
        clips->push_back(std::move(clip));
        s.clips = std::move(clips);
    } else if (m == Method::fillText || m == Method::strokeText) {
        if (!a.empty() && a[0].kind == Value::String) text(a[0].string, n(1), n(2), a.size() > 3 ? &a[3] : nullptr, m == Method::strokeText, out);
    } else if (m == Method::setLineDash) {
        if (!a.empty() && a[0].kind == Value::List && a[0].list.size() <= 64) {
            const auto& v = a[0].list;
            if (std::all_of(v.begin(), v.end(), [](float x) { return std::isfinite(x) && x >= 0; })) {
                s.dash = v;
                if (v.size() % 2 == 1) s.dash.insert(s.dash.end(), v.begin(), v.end());
            }
        }
    } else if (m == Method::putImageData) {
        if (a.empty() || a[0].kind != Value::Pixels) return;
        const Value& px = a[0];
        if (px.w <= 0 || px.h <= 0 || px.w > MAX_SIDE || px.h > MAX_SIDE || int64_t(px.w) * px.h > MAX_PIXELS || px.pixels->size() < size_t(px.w) * px.h * 4) return;
        CanvasOp o;
        o.kind = CanvasOp::PutImage;
        o.rgba = px.pixels;
        o.w = px.w;
        o.h = px.h;
        o.dx = float(n(1));
        o.dy = float(n(2));
        out.push_back(std::move(o));
    } else if (m == Method::drawImage) {
        image(a, out);
    } else {
        path_method(path_, s.matrix, method, a);
    }
}

void Canvas::path_method(Builder& b, const Matrix& m, const std::string& method, const std::vector<Value>& a) const {
    const Method verb = method_of(method);
    auto n = [&](size_t i, double fallback = 0) { return i < a.size() && a[i].kind == Value::Number && std::isfinite(a[i].number) ? a[i].number : fallback; };
    auto truthy = [&](size_t i) {
        if (i >= a.size()) return false;
        if (a[i].kind == Value::Number) return a[i].number != 0;
        if (a[i].kind == Value::String) return !a[i].string.empty() && a[i].string != "false";
        return false;
    };
    auto point = [&](double x, double y) {
        float px, py;
        m.apply(x, y, px, py);
        b.geometry.points.push_back(px);
        b.geometry.points.push_back(py);
        return std::make_pair(px, py);
    };
    auto move = [&](double x, double y) {
        b.geometry.ops.push_back(Path::Move);
        auto [px, py] = point(x, y);
        b.sx = b.cx = px;
        b.sy = b.cy = py;
        b.open = b.has_point = true;
    };
    // Canvas: a segment with no current point starts its subpath at its own first point; after
    // closePath the next one starts where the closed figure did.
    auto ensure = [&](double x, double y) {
        if (b.open) return;
        if (!b.has_point) {
            move(x, y);
            return;
        }
        b.geometry.ops.push_back(Path::Move);
        b.geometry.points.push_back(b.cx);
        b.geometry.points.push_back(b.cy);
        b.sx = b.cx;
        b.sy = b.cy;
        b.open = true;
    };
    auto close = [&] {
        if (!b.open) return;
        b.geometry.ops.push_back(Path::Close);
        b.cx = b.sx;
        b.cy = b.sy;
        b.open = false;
    };
    auto set_current = [&](double x, double y) {
        m.apply(x, y, b.cx, b.cy);
        b.has_point = true;
    };
    auto line = [&](double x, double y) {
        ensure(x, y);
        b.geometry.ops.push_back(Path::Line);
        auto [px, py] = point(x, y);
        b.cx = px;
        b.cy = py;
    };
    auto cubic = [&](double x1, double y1, double x2, double y2, double x, double y) {
        b.geometry.ops.push_back(Path::Cubic);
        point(x1, y1);
        point(x2, y2);
        auto [px, py] = point(x, y);
        b.cx = px;
        b.cy = py;
    };
    auto arc = [&](double cx, double cy, double rx, double ry, double rotation, double start, double end, bool anticlockwise) {
        double sweep = sweep_of(start, end, anticlockwise);
        double cr = std::cos(rotation), sr = std::sin(rotation);
        double x0 = cx + rx * std::cos(start) * cr - ry * std::sin(start) * sr;
        double y0 = cy + rx * std::cos(start) * sr + ry * std::sin(start) * cr;
        if (b.has_point) line(x0, y0);
        else move(x0, y0);
        if (sweep == 0) return;
        arc_segments(cx, cy, rx, ry, rotation, start, sweep, [&](double x1, double y1, double x2, double y2, double x, double y) { cubic(x1, y1, x2, y2, x, y); });
    };
    /// The current point in this path's own space.
    auto current = [&](double& x, double& y) {
        Matrix inverse;
        if (!m.invert(inverse)) return false;
        float fx, fy;
        inverse.apply(b.cx, b.cy, fx, fy);
        x = fx;
        y = fy;
        return true;
    };
    if (verb == Method::moveTo) move(n(0), n(1));
    else if (verb == Method::lineTo) line(n(0), n(1));
    else if (verb == Method::closePath) close();
    else if (verb == Method::bezierCurveTo) {
        ensure(n(0), n(1));
        cubic(n(0), n(1), n(2), n(3), n(4), n(5));
    } else if (verb == Method::quadraticCurveTo) {
        ensure(n(0), n(1));
        b.geometry.ops.push_back(Path::Quad);
        point(n(0), n(1));
        auto [px, py] = point(n(2), n(3));
        b.cx = px;
        b.cy = py;
    } else if (verb == Method::arc) {
        arc(n(0), n(1), std::max(0.0, n(2)), std::max(0.0, n(2)), 0, n(3), n(4), truthy(5));
    } else if (verb == Method::ellipse) {
        if (n(2) < 0 || n(3) < 0) return;
        arc(n(0), n(1), n(2), n(3), n(4), n(5), n(6), truthy(7));
    } else if (verb == Method::arcTo) {
        double x1 = n(0), y1 = n(1), x2 = n(2), y2 = n(3), r = std::max(0.0, n(4));
        double x0, y0;
        if (!b.has_point) {
            move(x1, y1);
            return;
        }
        if (!current(x0, y0)) return;
        double ax = x0 - x1, ay = y0 - y1, bx = x2 - x1, by = y2 - y1;
        double la = std::hypot(ax, ay), lb = std::hypot(bx, by);
        double cross = ax * by - ay * bx;
        if (r == 0 || la == 0 || lb == 0 || std::fabs(cross) < 1e-9 * la * lb) {
            line(x1, y1);
            return;
        }
        double cosine = (ax * bx + ay * by) / (la * lb);
        double angle = std::acos(std::clamp(cosine, -1.0, 1.0));
        double tangent = r / std::tan(angle / 2);
        double t1x = x1 + ax / la * tangent, t1y = y1 + ay / la * tangent;
        double t2x = x1 + bx / lb * tangent, t2y = y1 + by / lb * tangent;
        double mx = ax / la + bx / lb, my = ay / la + by / lb, ml = std::hypot(mx, my);
        double distance = r / std::sin(angle / 2);
        double cx = x1 + mx / ml * distance, cy = y1 + my / ml * distance;
        double start = std::atan2(t1y - cy, t1x - cx), end = std::atan2(t2y - cy, t2x - cx);
        line(t1x, t1y);
        arc(cx, cy, r, r, 0, start, end, cross > 0);
    } else if (verb == Method::rect) {
        double x = n(0), y = n(1), w = n(2), h = n(3);
        move(x, y);
        line(x + w, y);
        line(x + w, y + h);
        line(x, y + h);
        close();
    } else if (verb == Method::roundRect) {
        double x = n(0), y = n(1), w = n(2), h = n(3);
        if (w < 0) { x += w; w = -w; }
        if (h < 0) { y += h; h = -h; }
        double r = 0;
        if (a.size() > 4) {
            if (a[4].kind == Value::Number) r = n(4);
            else if (a[4].kind == Value::List && !a[4].list.empty() && std::isfinite(a[4].list[0])) r = a[4].list[0];
        }
        r = std::max(0.0, std::min({r, w / 2, h / 2}));
        if (r == 0) {
            path_method(b, m, "rect", {Value{Value::Number, x}, Value{Value::Number, y}, Value{Value::Number, w}, Value{Value::Number, h}});
            return;
        }
        move(x + r, y);
        line(x + w - r, y);
        arc_segments(x + w - r, y + r, r, r, 0, -PI / 2, PI / 2, [&](double a1, double b1, double a2, double b2, double a3, double b3) { cubic(a1, b1, a2, b2, a3, b3); });
        line(x + w, y + h - r);
        arc_segments(x + w - r, y + h - r, r, r, 0, 0, PI / 2, [&](double a1, double b1, double a2, double b2, double a3, double b3) { cubic(a1, b1, a2, b2, a3, b3); });
        line(x + r, y + h);
        arc_segments(x + r, y + h - r, r, r, 0, PI / 2, PI / 2, [&](double a1, double b1, double a2, double b2, double a3, double b3) { cubic(a1, b1, a2, b2, a3, b3); });
        line(x, y + r);
        arc_segments(x + r, y + r, r, r, 0, PI, PI / 2, [&](double a1, double b1, double a2, double b2, double a3, double b3) { cubic(a1, b1, a2, b2, a3, b3); });
        close();
        // The spec starts the next subpath at the rectangle's origin, not at the first corner arc.
        set_current(x, y);
    } else if (verb == Method::addPath) {
        if (a.empty() || a[0].kind != Value::PathRef) return;
        auto it = paths_.find(int(a[0].ref));
        if (it == paths_.end() || &it->second == &b) return;
        Geometry g = transformed(it->second.geometry, m);
        b.geometry.ops.insert(b.geometry.ops.end(), g.ops.begin(), g.ops.end());
        b.geometry.points.insert(b.geometry.points.end(), g.points.begin(), g.points.end());
        b.open = it->second.open;
        b.has_point = it->second.has_point;
        if (g.points.size() >= 2) {
            b.cx = g.points[g.points.size() - 2];
            b.cy = g.points.back();
        }
    }
}

Json Canvas::call(const std::string& method, const Json& args) const {
    const auto& list = args.arr();
    std::vector<Value> a;
    for (const Json& j : list) a.push_back(Value::of(j));
    const Matrix& m = state_.matrix;
    if (method == "getTransform") {
        Json out = Json::array();
        for (double v : {m.a, m.b, m.c, m.d, m.e, m.f}) out.push(v);
        return out;
    }
    if (method == "getLineDash") {
        Json out = Json::array();
        for (float v : state_.dash) out.push(double(v));
        return out;
    }
    if (method == "isPointInPath" || method == "isPointInStroke") {
        Geometry path = path_.geometry;
        size_t at = 0;
        if (!a.empty() && a[0].kind == Value::PathRef) {
            auto it = paths_.find(int(a[0].ref));
            if (it == paths_.end()) return false;
            path = transformed(it->second.geometry, m);
            at = 1;
        }
        auto n = [&](size_t i) { return i < a.size() && a[i].kind == Value::Number ? a[i].number : 0.0; };
        double x = n(at), y = n(at + 1);
        if (method == "isPointInPath") return contains(path, x, y, at + 2 < a.size() && a[at + 2].string == "evenodd");
        double scale = std::sqrt(std::fabs(m.a * m.d - m.b * m.c));
        return near_stroke(path, x, y, state_.line_width * scale / 2);
    }
    return Json::object().set("error", "unsupported");
}

// ───────────────────────── SVG path data ─────────────────────────

void append_svg_path(const std::string& d, Geometry& out) {
    std::vector<std::string> tokens;
    std::string current;
    auto flush = [&] {
        if (!current.empty()) tokens.push_back(std::move(current));
        current.clear();
    };
    for (char ch : d) {
        bool letter = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
        if (letter && ch != 'e' && ch != 'E') {
            flush();
            tokens.emplace_back(1, ch);
        } else if (ch == ',' || ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r') {
            flush();
        } else if (ch == '-' && !current.empty() && current.back() != 'e' && current.back() != 'E') {
            flush();
            current = "-";
        } else if (ch == '.' && current.find('.') != std::string::npos) {
            flush();
            current = ".";
        } else current.push_back(ch);
    }
    flush();
    size_t i = 0;
    char command = 'M';
    double px = 0, py = 0, sx = 0, sy = 0;
    bool has_control = false;
    double lcx = 0, lcy = 0;
    bool open = false;
    auto next = [&](double& v) {
        if (i >= tokens.size()) return false;
        auto n = css::number(tokens[i]);
        if (!n) return false;
        v = *n;
        i++;
        return true;
    };
    auto push = [&](Path::Op op, std::initializer_list<double> pts) {
        out.ops.push_back(op);
        for (double v : pts) out.points.push_back(float(v));
    };
    auto ensure = [&] {
        if (!open) {
            push(Path::Move, {px, py});
            open = true;
        }
    };
    while (i < tokens.size()) {
        const std::string& t = tokens[i];
        if (t.size() == 1 && ((t[0] >= 'a' && t[0] <= 'z') || (t[0] >= 'A' && t[0] <= 'Z'))) {
            command = t[0];
            i++;
            if (command == 'Z' || command == 'z') {
                if (open) push(Path::Close, {});
                open = false;
                px = sx;
                py = sy;
                has_control = false;
                continue;
            }
        }
        bool relative = command >= 'a' && command <= 'z';
        double ox = relative ? px : 0, oy = relative ? py : 0;
        switch (command & ~0x20) {
        case 'M': {
            double x, y;
            if (!next(x) || !next(y)) return;
            px = ox + x;
            py = oy + y;
            push(Path::Move, {px, py});
            open = true;
            sx = px;
            sy = py;
            command = relative ? 'l' : 'L';
            has_control = false;
            break;
        }
        case 'L': {
            double x, y;
            if (!next(x) || !next(y)) return;
            ensure();
            px = ox + x;
            py = oy + y;
            push(Path::Line, {px, py});
            has_control = false;
            break;
        }
        case 'H': {
            double x;
            if (!next(x)) return;
            ensure();
            px = (relative ? px : 0) + x;
            push(Path::Line, {px, py});
            has_control = false;
            break;
        }
        case 'V': {
            double y;
            if (!next(y)) return;
            ensure();
            py = (relative ? py : 0) + y;
            push(Path::Line, {px, py});
            has_control = false;
            break;
        }
        case 'C': {
            double x1, y1, x2, y2, x, y;
            if (!next(x1) || !next(y1) || !next(x2) || !next(y2) || !next(x) || !next(y)) return;
            ensure();
            push(Path::Cubic, {ox + x1, oy + y1, ox + x2, oy + y2, ox + x, oy + y});
            lcx = ox + x2;
            lcy = oy + y2;
            has_control = true;
            px = ox + x;
            py = oy + y;
            break;
        }
        case 'S': {
            double x2, y2, x, y;
            if (!next(x2) || !next(y2) || !next(x) || !next(y)) return;
            ensure();
            double c1x = has_control ? 2 * px - lcx : px, c1y = has_control ? 2 * py - lcy : py;
            push(Path::Cubic, {c1x, c1y, ox + x2, oy + y2, ox + x, oy + y});
            lcx = ox + x2;
            lcy = oy + y2;
            has_control = true;
            px = ox + x;
            py = oy + y;
            break;
        }
        case 'Q': {
            double x1, y1, x, y;
            if (!next(x1) || !next(y1) || !next(x) || !next(y)) return;
            ensure();
            push(Path::Quad, {ox + x1, oy + y1, ox + x, oy + y});
            lcx = ox + x1;
            lcy = oy + y1;
            has_control = true;
            px = ox + x;
            py = oy + y;
            break;
        }
        case 'T': {
            double x, y;
            if (!next(x) || !next(y)) return;
            ensure();
            double cx = has_control ? 2 * px - lcx : px, cy = has_control ? 2 * py - lcy : py;
            push(Path::Quad, {cx, cy, ox + x, oy + y});
            lcx = cx;
            lcy = cy;
            has_control = true;
            px = ox + x;
            py = oy + y;
            break;
        }
        case 'A': {
            double rx, ry, rotation, large, sweep_flag, x, y;
            if (!next(rx) || !next(ry) || !next(rotation) || !next(large) || !next(sweep_flag) || !next(x) || !next(y)) return;
            ensure();
            double x2 = ox + x, y2 = oy + y;
            rx = std::fabs(rx);
            ry = std::fabs(ry);
            if (rx == 0 || ry == 0 || (x2 == px && y2 == py)) {
                push(Path::Line, {x2, y2});
            } else {
                // SVG 1.1 F.6.5: endpoint to centre parameterisation.
                double phi = rotation * PI / 180, c = std::cos(phi), s = std::sin(phi);
                double dx = (px - x2) / 2, dy = (py - y2) / 2;
                double x1p = c * dx + s * dy, y1p = -s * dx + c * dy;
                double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
                if (lambda > 1) {
                    rx *= std::sqrt(lambda);
                    ry *= std::sqrt(lambda);
                }
                double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
                double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
                double k = std::sqrt(std::max(0.0, num / den)) * ((large != 0) == (sweep_flag != 0) ? -1 : 1);
                double cxp = k * rx * y1p / ry, cyp = -k * ry * x1p / rx;
                double cx = c * cxp - s * cyp + (px + x2) / 2, cy = s * cxp + c * cyp + (py + y2) / 2;
                auto angle = [](double ux, double uy, double vx, double vy) {
                    double a = std::atan2(ux * vy - uy * vx, ux * vx + uy * vy);
                    return a;
                };
                double theta = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
                double delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
                if (sweep_flag == 0 && delta > 0) delta -= 2 * PI;
                else if (sweep_flag != 0 && delta < 0) delta += 2 * PI;
                arc_segments(cx, cy, rx, ry, phi, theta, delta, [&](double a1, double b1, double a2, double b2, double a3, double b3) {
                    push(Path::Cubic, {a1, b1, a2, b2, a3, b3});
                });
            }
            px = x2;
            py = y2;
            has_control = false;
            break;
        }
        default:
            return;
        }
    }
}

} // namespace twlite
