// The JSON forms of a display list and of a canvas batch, for painters outside C++ (Android's
// Canvas in Kotlin, the web's canvas in TypeScript). Their keys are documented with to_json.
#include "canvas_ops.h"
#include "compositor.h"
#include "display_list.h"
#include "twidget/json.h"

#include <cmath>

namespace twlite {

namespace {

struct Writer {
    std::string& out;
    bool fresh = false;

    void open() {
        out.push_back('{');
        fresh = true;
    }
    /// CSS and canvas px: hundredths are below what any painter resolves.
    void num(float v) { out += twidget::Json::number(std::round(double(v) * 100) / 100); }
    /// Matrix components and gradient offsets keep their precision: a rounded rotation drifts.
    void exact(float v) { out += twidget::Json::number(double(v)); }
    void key(const char* k) {
        if (!fresh) out.push_back(',');
        fresh = false;
        out += "\"";
        out += k;
        out += "\":";
    }
    void argb(const Color& c) { out += std::to_string((uint32_t(c.a) << 24) | (uint32_t(c.r) << 16) | (uint32_t(c.g) << 8) | c.b); }
    void color(const char* k, const Color& c) {
        key(k);
        argb(c);
    }
    void rect(const char* k, const Rect& r) {
        key(k);
        out.push_back('[');
        num(r.x); out.push_back(','); num(r.y); out.push_back(','); num(r.w); out.push_back(','); num(r.h);
        out.push_back(']');
    }
    void floats(const char* k, const float* v, size_t n, bool precise) {
        key(k);
        out.push_back('[');
        for (size_t i = 0; i < n; i++) {
            if (i) out.push_back(',');
            precise ? exact(v[i]) : num(v[i]);
        }
        out.push_back(']');
    }
    void stops(const char* k, const std::vector<Stop>& list) {
        key(k);
        out.push_back('[');
        for (size_t i = 0; i < list.size(); i++) {
            if (i) out.push_back(',');
            exact(list[i].offset);
            out.push_back(',');
            argb(list[i].color);
        }
        out.push_back(']');
    }
    /// A path as "o" (op codes: 0 move, 1 line, 2 quad, 3 cubic, 4 close) and "v" (their points).
    void geometry(const std::vector<Path::Op>& ops, const std::vector<float>& points) {
        key("o");
        out.push_back('[');
        for (size_t i = 0; i < ops.size(); i++) {
            if (i) out.push_back(',');
            out += std::to_string(int(ops[i]));
        }
        out.push_back(']');
        floats("v", points.data(), points.size(), false);
    }
    void flag(const char* k, bool on) {
        if (on) {
            key(k);
            out += "1";
        }
    }
};

void svg_path(Writer& w, const Path& p) {
    std::string& out = w.out;
    w.open();
    w.geometry(p.ops, p.points);
    w.flag("e", p.evenodd);
    if (p.fill && p.gradient != 0 && p.stops.size() >= 2) {
        w.key("g");
        out += std::to_string(p.gradient);
        const float a[] = {p.x1, p.y1, p.x2, p.y2, p.r, p.fx, p.fy};
        w.floats("ga", a, 7, false);
        w.stops("s", p.stops);
    } else if (p.fill) {
        w.color("f", p.fill_color);
    }
    if (p.stroke) {
        w.color("sc", p.stroke_color);
        if (p.stroke_gradient != 0 && p.stroke_stops.size() >= 2) {
            w.key("sg");
            out += std::to_string(p.stroke_gradient);
            w.floats("sga", p.sg, 7, false);
            w.stops("ss", p.stroke_stops);
        }
        w.key("sw"); w.num(p.stroke_width);
        w.key("lc"); out += std::to_string(p.line_cap);
        w.key("lj"); out += std::to_string(p.line_join);
        w.key("ml"); w.num(p.miter_limit);
        if (!p.dashes.empty()) {
            w.floats("d", p.dashes.data(), p.dashes.size(), false);
            w.key("do"); w.num(p.dash_offset);
        }
    }
    out.push_back('}');
}

} // namespace

std::string base64(const std::vector<uint8_t>& in) {
    static const char* A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    out.reserve((in.size() + 2) / 3 * 4);
    size_t i = 0;
    for (; i + 2 < in.size(); i += 3) {
        uint32_t v = (uint32_t(in[i]) << 16) | (uint32_t(in[i + 1]) << 8) | in[i + 2];
        out += A[v >> 18]; out += A[(v >> 12) & 63]; out += A[(v >> 6) & 63]; out += A[v & 63];
    }
    if (i + 1 == in.size()) {
        uint32_t v = uint32_t(in[i]) << 16;
        out += A[v >> 18]; out += A[(v >> 12) & 63]; out += "==";
    } else if (i + 2 == in.size()) {
        uint32_t v = (uint32_t(in[i]) << 16) | (uint32_t(in[i + 1]) << 8);
        out += A[v >> 18]; out += A[(v >> 12) & 63]; out += A[(v >> 6) & 63]; out += '=';
    }
    return out;
}

std::string to_json(const DisplayList& list) {
    std::string out;
    out.reserve(list.size() * 64 + 2);
    Writer w{out};
    out.push_back('[');
    bool first = true;
    for (const Command& c : list) {
        if (!first) out.push_back(',');
        first = false;
        w.open();
        w.key("k");
        out += std::to_string(int(c.kind));
        if (c.kind != Cmd::ClipPop && c.kind != Cmd::GroupPop && c.kind != Cmd::FilterPop && c.kind != Cmd::Text && c.kind != Cmd::Path) w.rect("r", c.rect);
        if (c.radii.any()) {
            const float v[] = {c.radii.tlx, c.radii.tly, c.radii.trx, c.radii.try_, c.radii.brx, c.radii.bry, c.radii.blx, c.radii.bly};
            w.floats("q", v, 8, false);
        }
        if (c.has_clip) w.rect("l", c.clip);
        switch (c.kind) {
        case Cmd::FillRect: case Cmd::FillEllipse: w.color("c", c.color); break;
        case Cmd::StrokeRect: case Cmd::StrokeEllipse: w.color("c", c.color); w.key("w"); w.num(c.width); break;
        case Cmd::Text:
            w.color("c", c.color);
            out += ",\"f\":" + std::to_string(c.font) + ",\"x\":"; w.num(c.x);
            out += ",\"y\":"; w.num(c.y);
            out += ",\"t\":" + twidget::Json::quote(c.text);
            break;
        case Cmd::Image:
            if (c.canvas) out += ",\"cv\":" + std::to_string(c.canvas);
            else out += ",\"i\":" + std::to_string(c.image);
            break;
        case Cmd::Linear: case Cmd::Radial: case Cmd::Conic: {
            const float a[] = {c.ax, c.ay, c.bx, c.by};
            w.floats("a", a, 4, false);
            w.stops("s", c.stops);
            break;
        }
        case Cmd::ClipPush:
            if (c.path) {
                w.key("p");
                w.open();
                w.geometry(c.path->ops, c.path->points);
                w.flag("e", c.path->evenodd);
                out.push_back('}');
            }
            break;
        case Cmd::Path:
            if (c.path) {
                w.key("p");
                svg_path(w, *c.path);
            }
            break;
        case Cmd::GroupPush:
            w.floats("m", c.matrix, 6, true);
            w.key("o"); w.exact(c.opacity);
            w.flag("h", c.hidden);
            break;
        case Cmd::Shadow: {
            w.color("c", c.color);
            const float a[] = {c.ax, c.ay, c.bx, c.by};
            w.floats("a", a, 4, false);
            w.flag("n", c.inset);
            break;
        }
        case Cmd::FilterPush:
        case Cmd::Backdrop:
            if (c.filter) {
                w.key("fl");
                out.push_back('[');
                for (size_t k = 0; k < c.filter->size(); k++) {
                    const Filter& f = (*c.filter)[k];
                    if (k) out.push_back(',');
                    out.push_back('[');
                    out += std::to_string(int(f.kind));
                    out.push_back(','); w.exact(f.amount);
                    out.push_back(','); w.num(f.x);
                    out.push_back(','); w.num(f.y);
                    out.push_back(','); w.argb(f.color);
                    out.push_back(']');
                }
                out.push_back(']');
            }
            break;
        case Cmd::ClipPop: case Cmd::GroupPop: case Cmd::FilterPop: break;
        }
        out.push_back('}');
    }
    out.push_back(']');
    return out;
}

std::string to_json(const std::vector<CanvasOp>& ops) {
    std::string out;
    out.reserve(ops.size() * 96 + 2);
    Writer w{out};
    out.push_back('[');
    bool first = true;
    for (const CanvasOp& op : ops) {
        if (!first) out.push_back(',');
        first = false;
        w.open();
        w.key("k");
        out += std::to_string(int(op.kind));
        switch (op.kind) {
        case CanvasOp::Reset:
            break;
        case CanvasOp::PutImage:
            // Pixels travel as base64 of straight RGBA, row by row.
            w.key("x"); w.num(op.dx);
            w.key("y"); w.num(op.dy);
            out += ",\"w\":" + std::to_string(op.w) + ",\"h\":" + std::to_string(op.h);
            if (op.rgba) out += ",\"px\":" + twidget::Json::quote(base64(*op.rgba));
            break;
        default: {
            if (op.kind == CanvasOp::Fill || op.kind == CanvasOp::Stroke || op.kind == CanvasOp::Clear) w.geometry(op.path.ops, op.path.points);
            if (op.kind == CanvasOp::Clear) break;
            w.flag("e", op.evenodd);
            w.floats("m", op.matrix, 6, true);
            if (op.alpha != 1) { w.key("a"); w.exact(op.alpha); }
            if (op.composite != Composite::SourceOver) { w.key("cm"); out += std::to_string(int(op.composite)); }
            if (op.has_shadow()) {
                w.color("shc", op.shadow_color);
                const float s[] = {op.shadow_x, op.shadow_y, op.shadow_blur};
                w.floats("sh", s, 3, false);
            }
            if (op.kind == CanvasOp::Fill || op.kind == CanvasOp::Stroke || op.kind == CanvasOp::Text) {
                // The paint: "pk" 0 solid ("c"), 1 linear, 2 radial, 3 conic ("pa" in user space, "s" stops).
                w.key("pk"); out += std::to_string(int(op.paint.kind));
                if (op.paint.kind == CanvasPaint::Solid) w.color("c", op.paint.color);
                else {
                    w.floats("pa", op.paint.args, 6, true);
                    w.stops("s", op.paint.stops);
                }
            }
            if (op.kind == CanvasOp::Stroke || (op.kind == CanvasOp::Text && op.stroke_text)) {
                w.key("lw"); w.exact(op.line_width);
                w.key("lc"); out += std::to_string(op.line_cap);
                w.key("lj"); out += std::to_string(op.line_join);
                w.key("ml"); w.num(op.miter_limit);
                if (!op.dashes.empty()) {
                    w.floats("d", op.dashes.data(), op.dashes.size(), false);
                    w.key("do"); w.num(op.dash_offset);
                }
            }
            if (op.kind == CanvasOp::Text) {
                out += ",\"f\":" + std::to_string(op.font) + ",\"t\":" + twidget::Json::quote(op.text);
                const float t[] = {op.x, op.y, op.scale_x, op.text_dx};
                w.floats("tp", t, 4, true);
                w.flag("st", op.stroke_text);
            }
            if (op.kind == CanvasOp::Image) {
                if (op.canvas) out += ",\"cv\":" + std::to_string(op.canvas);
                else out += ",\"i\":" + std::to_string(op.image);
                const float r[] = {op.sx, op.sy, op.sw, op.sh, op.dx, op.dy, op.dw, op.dh};
                w.floats("ir", r, 8, true);
                w.flag("ns", !op.smoothing);
            }
            break;
        }
        }
        if (op.clips && !op.clips->empty() && op.kind != CanvasOp::Reset && op.kind != CanvasOp::PutImage) {
            out += ",\"cl\":[";
            for (size_t i = 0; i < op.clips->size(); i++) {
                const CanvasClip& clip = (*op.clips)[i];
                if (i) out.push_back(',');
                w.open();
                w.geometry(clip.path.ops, clip.path.points);
                w.flag("e", clip.evenodd);
                out.push_back('}');
            }
            out.push_back(']');
        }
        out.push_back('}');
    }
    out.push_back(']');
    return out;
}

std::string to_json(const Composition& composition) {
    std::string out;
    Writer w{out};
    w.open();
    w.key("v");
    out += std::to_string(composition.version);
    w.key("l");
    out.push_back('[');
    for (size_t i = 0; i < composition.layers.size(); i++) {
        const Layer& layer = composition.layers[i];
        if (i) out.push_back(',');
        w.open();
        w.key("id"); out += std::to_string(layer.id);
        w.rect("b", layer.bounds);
        w.key("cv"); out += std::to_string(layer.content_version);
        w.key("rs"); w.exact(layer.raster_scale);
        if (layer.has_clip) {
            w.rect("l", layer.clip);
            if (layer.clip_radii.any()) {
                const Radii& q = layer.clip_radii;
                const float v[] = {q.tlx, q.tly, q.trx, q.try_, q.brx, q.bry, q.blx, q.bly};
                w.floats("q", v, 8, false);
            }
        }
        w.key("tv"); out += std::to_string(layer.track_version);
        w.key("ts"); out += twidget::Json::number(layer.track.start);
        w.key("td"); out += twidget::Json::number(layer.track.duration);
        w.flag("tr", layer.track.repeat);
        std::vector<float> keys;
        keys.reserve(layer.track.keys.size() * 8);
        for (const LayerKey& k : layer.track.keys)
            keys.insert(keys.end(), {k.t, k.matrix.a, k.matrix.b, k.matrix.c, k.matrix.d, k.matrix.e, k.matrix.f, k.opacity});
        w.floats("k", keys.data(), keys.size(), true);
        if (layer.parent) {
            w.key("p");
            out += std::to_string(layer.parent);
        }
        out.push_back('}');
    }
    out += "]}";
    return out;
}

} // namespace twlite
