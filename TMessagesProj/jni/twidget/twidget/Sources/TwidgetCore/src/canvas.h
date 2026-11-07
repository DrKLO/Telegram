// The 2D canvas interpreter: the `canvas` op's command list (widget-sandbox's docs/protocol.md, "Canvas commands")
// becomes CanvasOps a Backend draws. All state lives here (the transform, the state stack,
// styles, paths, Path2D and gradient objects, clips), so a backend only executes resolved
// geometry. Ported from TwidgetRenderer's WidgetCanvas.swift, whose CoreGraphics calls became this
// interpreter plus the backend interface.
#pragma once

#include "backend.h"
#include "canvas_ops.h"
#include "twidget/json.h"

#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace twlite {

/// What the interpreter asks of the renderer.
class CanvasHost {
public:
    virtual ~CanvasHost() = default;
    /// A backend font for a CSS `font` shorthand; kept by the host for the canvas's lifetime.
    virtual int canvas_font(const std::string& css, FontMetrics& metrics) = 0;
    virtual float canvas_text_width(int font, const std::string& text) = 0;
    /// An image source `{n: id}`: a decoded <img> (`image`) or another canvas (`canvas`), with its size in px.
    virtual bool canvas_source(int64_t node, int& image, int& canvas, float& w, float& h) = 0;
    /// Pixels the canvas may still allocate, given every other canvas of the widget.
    virtual int64_t canvas_allowance(int id) = 0;
};

class Canvas {
public:
    static constexpr int MAX_SIDE = 8192;
    static constexpr int64_t MAX_PIXELS = 4096 * 4096;

    Canvas(int id, int width, int height, CanvasHost& host);

    int id() const { return id_; }
    int width() const { return width_; }
    int height() const { return height_; }
    int64_t pixels() const { return int64_t(width_) * height_; }

    /// One `canvas` op. `out` receives what the backend must run, in order.
    void run(const twidget::Json& cmds, std::vector<CanvasOp>& out);
    /// The bitmap size changed (`r`, or the element's width/height): cleared, state reset.
    void resize(int width, int height, std::vector<CanvasOp>& out);
    /// `canvasCall` queries that the state answers: isPointInPath, isPointInStroke, getTransform, getLineDash.
    twidget::Json call(const std::string& method, const twidget::Json& args) const;

private:
    struct Matrix {
        double a = 1, b = 0, c = 0, d = 1, e = 0, f = 0;
        void apply(double x, double y, float& ox, float& oy) const {
            ox = float(a * x + c * y + e);
            oy = float(b * x + d * y + f);
        }
        Matrix then(const Matrix& m) const;  // this, then m
        bool invert(Matrix& out) const;
    };
    struct Paint {
        bool gradient = false;
        Color color{0, 0, 0, 255};
        int id = 0;
    };
    struct State {
        Matrix matrix;
        Paint fill, stroke;
        std::string font = "10px sans-serif";
        std::string align = "start", baseline = "alphabetic";
        std::vector<float> dash;
        float dash_offset = 0;
        float line_width = 1;
        int line_cap = 0, line_join = 0;
        float miter_limit = 10;
        float alpha = 1;
        Composite composite = Composite::SourceOver;
        Color shadow_color{0, 0, 0, 0};
        float shadow_blur = 0, shadow_x = 0, shadow_y = 0;
        bool smoothing = true;
        std::shared_ptr<const std::vector<CanvasClip>> clips;
    };
    struct Gradient {
        std::string kind;
        std::vector<float> args;
        std::vector<Stop> stops;
    };
    /// A path being built: points already in canvas px (the current path) or in its own space (Path2D).
    struct Builder {
        Geometry geometry;
        bool open = false;       // a figure is open: segments append to it
        bool has_point = false;  // there is a current point; after closePath it is the closed figure's start
        float sx = 0, sy = 0, cx = 0, cy = 0;  // subpath start and current point, as stored
    };
    struct Value;

    void call_method(const std::string& method, const std::vector<Value>& args, std::vector<CanvasOp>& out);
    void path_method(Builder& path, const Matrix& m, const std::string& method, const std::vector<Value>& args) const;
    void set(const std::string& property, const Value& value);
    CanvasOp op(CanvasOp::Kind kind) const;
    bool paint_of(const Paint& paint, CanvasPaint& out) const;
    void fill(const Geometry& path, bool evenodd, std::vector<CanvasOp>& out);
    void stroke(const Geometry& path, std::vector<CanvasOp>& out);
    void text(const std::string& s, double x, double y, const Value* max_width, bool stroke, std::vector<CanvasOp>& out);
    void image(const std::vector<Value>& args, std::vector<CanvasOp>& out);
    Geometry transformed(const Geometry& path, const Matrix& m) const;
    Geometry rect_geometry(double x, double y, double w, double h) const;

    int id_;
    int width_, height_;
    CanvasHost& host_;
    State state_;
    std::vector<State> stack_;
    Builder path_;
    // By id, which the sandbox only ever increases: at the cap the oldest object goes, as a browser
    // collects the ones a widget dropped, rather than the newest being refused.
    std::map<int, Gradient> gradients_;
    bool color_of(const std::string& text, Color& out);
public:
    void trim() { colors_.clear(); }
private:
    std::unordered_map<std::string, std::optional<Color>> colors_;
    std::map<int, Builder> paths_;
};

/// Appends an SVG path (`d`) to a geometry, arcs as cubic béziers. Shared with Path2D.
void append_svg_path(const std::string& d, Geometry& out);

/// Whether a point is inside a geometry filled with the nonzero or even-odd rule, curves flattened.
bool geometry_contains(const Geometry& g, double x, double y, bool evenodd);

} // namespace twlite
