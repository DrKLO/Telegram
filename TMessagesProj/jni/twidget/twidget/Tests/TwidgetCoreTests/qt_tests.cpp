// Pixel checks of the QPainter backend (platform/qt): display list commands, canvas operations and a
// canvas through the whole renderer, read back from the images it draws. Runs headless on Qt's
// offscreen platform.
#include "qt_backend.h"
#include "renderer.h"

#include <QGuiApplication>
#include <QPainter>

#include <cmath>
#include <cstdio>
#include <string>

using twidget::Json;
using namespace twlite;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) {
        std::printf("FAIL: %s\n", what);
        failures++;
    }
}

static Json parse(const std::string& s) {
    auto v = Json::parse(s);
    if (!v) {
        std::printf("bad test JSON: %s\n", s.c_str());
        failures++;
        return Json();
    }
    return *v;
}

static bool near(QRgb a, QColor b, int tolerance = 6) {
    return std::abs(qRed(a) - b.red()) <= tolerance && std::abs(qGreen(a) - b.green()) <= tolerance && std::abs(qBlue(a) - b.blue()) <= tolerance &&
           std::abs(qAlpha(a) - b.alpha()) <= tolerance;
}

static QRgb at(const QImage& image, int x, int y) { return image.pixel(x, y); }

static Command fill_rect(Rect r, Color c, Radii q = {}) {
    Command cmd;
    cmd.kind = Cmd::FillRect;
    cmd.rect = r;
    cmd.color = c;
    cmd.radii = q;
    return cmd;
}

static Geometry rect_path(float x, float y, float w, float h) {
    Geometry g;
    g.ops = {Path::Move, Path::Line, Path::Line, Path::Line, Path::Close};
    g.points = {x, y, x + w, y, x + w, y + h, x, y + h};
    return g;
}

static void test_display_list() {
    QtBackend backend;
    QImage image = QtBackend::make_image(100, 100);

    DisplayList list;
    list.push_back(fill_rect({0, 0, 100, 100}, {255, 255, 255, 255}));
    // A rounded clip around a full-size red fill: the corners stay white.
    Command clip;
    clip.kind = Cmd::ClipPush;
    clip.rect = {10, 10, 80, 80};
    clip.radii = {20, 20, 20, 20, 20, 20, 20, 20};
    list.push_back(clip);
    list.push_back(fill_rect({0, 0, 100, 100}, {255, 0, 0, 255}));
    Command pop;
    pop.kind = Cmd::ClipPop;
    list.push_back(pop);
    // A half-transparent group over two overlapping blue squares: one layer, so the overlap is no darker.
    Command group;
    group.kind = Cmd::GroupPush;
    group.opacity = 0.5f;
    list.push_back(group);
    list.push_back(fill_rect({0, 90, 6, 6}, {0, 0, 255, 255}));
    list.push_back(fill_rect({3, 90, 6, 6}, {0, 0, 255, 255}));
    Command group_pop;
    group_pop.kind = Cmd::GroupPop;
    list.push_back(group_pop);
    backend.paint(list, image, QTransform());

    check(near(at(image, 50, 50), QColor(255, 0, 0)), "inside the rounded clip is red");
    check(near(at(image, 12, 12), QColor(255, 255, 255)), "the rounded clip's corner stays white");
    check(near(at(image, 5, 5), QColor(255, 255, 255)), "outside the clip stays white");
    check(near(at(image, 1, 92), at(image, 4, 92)), "an opacity group composites once: the overlap is not darker");
    check(near(at(image, 4, 92), QColor(128, 128, 255), 8), "half-transparent blue over white");
}

static void test_shadow_and_gradients() {
    QtBackend backend;
    QImage image = QtBackend::make_image(200, 100);
    DisplayList list;
    list.push_back(fill_rect({0, 0, 200, 100}, {255, 255, 255, 255}));
    Command shadow;
    shadow.kind = Cmd::Shadow;
    shadow.rect = {20, 20, 40, 40};
    shadow.color = {0, 0, 0, 255};
    shadow.ax = 0;
    shadow.ay = 0;
    shadow.bx = 10;  // blur
    shadow.by = 0;   // spread
    list.push_back(shadow);

    Command conic;
    conic.kind = Cmd::Conic;
    conic.rect = {100, 0, 100, 100};
    conic.ax = 150;
    conic.ay = 50;
    conic.bx = 0;
    conic.stops = {{0, {255, 0, 0, 255}}, {0.5f, {0, 0, 255, 255}}, {1, {255, 0, 0, 255}}};
    list.push_back(conic);
    backend.paint(list, image, QTransform());

    QRgb inside = at(image, 40, 40), edge = at(image, 61, 40), far = at(image, 95, 40);
    check(near(inside, QColor(255, 255, 255)), "an outer shadow does not draw inside its box");
    check(qRed(edge) < 200, "the shadow is dark next to the box");
    check(qRed(edge) > 40, "the shadow is blurred, not solid, next to the box");
    check(near(far, QColor(255, 255, 255)), "the blur fades out");
    // The conic starts at the top and turns clockwise: blue half way, at the bottom.
    check(qRed(at(image, 150, 5)) > 200 && qBlue(at(image, 150, 5)) < 60, "the conic is red at the top");
    check(qBlue(at(image, 150, 95)) > 200 && qRed(at(image, 150, 95)) < 60, "the conic is blue at the bottom");
    check(qRed(at(image, 195, 50)) > qBlue(at(image, 195, 50)) - 40 && qBlue(at(image, 195, 50)) > 60, "a quarter turn in, on the right, it is between");
}

static void test_canvas_ops() {
    QtBackend backend;
    std::vector<CanvasOp> ops(1);
    ops[0].kind = CanvasOp::Reset;

    CanvasOp fill;
    fill.kind = CanvasOp::Fill;
    fill.path = rect_path(0, 0, 10, 20);
    fill.paint.kind = CanvasPaint::Linear;
    fill.paint.args[0] = 0;
    fill.paint.args[1] = 20;
    fill.paint.args[2] = 0;
    fill.paint.args[3] = 0;
    fill.paint.stops = {{0, {255, 0, 0, 255}}, {1, {0, 0, 255, 255}}};
    ops.push_back(fill);

    CanvasOp solid;
    solid.kind = CanvasOp::Fill;
    solid.path = rect_path(20, 0, 10, 10);
    solid.paint.color = {0, 255, 0, 255};
    ops.push_back(solid);

    // Multiply over the green square: green × yellow stays green.
    CanvasOp multiply = solid;
    multiply.path = rect_path(20, 0, 10, 10);
    multiply.paint.color = {255, 255, 0, 255};
    multiply.composite = Composite::Multiply;
    ops.push_back(multiply);

    // Luminosity, which QPainter lacks: a white source over green takes the source's lightness.
    CanvasOp lum = solid;
    lum.path = rect_path(20, 10, 10, 10);
    ops.push_back(lum);
    CanvasOp lum2 = lum;
    lum2.paint.color = {255, 255, 255, 255};
    lum2.composite = Composite::Luminosity;
    ops.push_back(lum2);

    // Copy under a clip: replaces inside the clip only.
    CanvasOp copy = solid;
    copy.path = rect_path(40, 0, 10, 10);
    copy.paint.color = {0, 0, 255, 255};
    ops.push_back(copy);
    CanvasOp replace = copy;
    replace.path = rect_path(45, 5, 1, 1);
    replace.paint.color = {255, 0, 0, 255};
    replace.composite = Composite::Copy;
    auto clips = std::make_shared<std::vector<CanvasClip>>(1);
    (*clips)[0].path = rect_path(40, 0, 5, 10);
    replace.clips = clips;
    ops.push_back(replace);

    backend.canvas_run(7, 60, 20, ops);
    std::vector<uint8_t> px;
    backend.canvas_read(7, 0, 0, 60, 20, px);
    auto rgba = [&](int x, int y) { const uint8_t* p = px.data() + (size_t(y) * 60 + x) * 4; return QColor(p[0], p[1], p[2], p[3]); };

    check(rgba(5, 19).red() > 200 && rgba(5, 19).blue() < 40, "the gradient is red at its start");
    check(rgba(5, 0).blue() > 200 && rgba(5, 0).red() < 40, "and blue at its end");
    check(rgba(25, 5) == QColor(0, 255, 0, 255), "multiply: green × yellow is green");
    check(rgba(25, 15).red() > 200 && rgba(25, 15).green() > 200, "luminosity: white lightness over green is near white");
    check(rgba(42, 5) == QColor(0, 0, 0, 0), "copy clears inside the clip, outside its shape");
    check(rgba(47, 5) == QColor(0, 0, 255, 255), "copy leaves what is outside the clip");
}

/// A <canvas> through the renderer: the ops, the canvas run and the display list image.
static void test_canvas_through_renderer(double dpr) {
    QtBackend backend;
    Renderer r(100, dpr, false, backend);
    r.receive(parse(R"J({"type":"ops","ops":[
      {"op":"el","id":2,"tag":"html","adopt":"html"},{"op":"el","id":3,"tag":"head","adopt":"head"},{"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"style","id":4,"name":"margin","value":"0"},
      {"op":"el","id":30,"tag":"canvas","attrs":[["width","100"],["height","50"]]},{"op":"ins","parent":4,"id":30,"before":null},
      {"op":"canvas","id":30,"cmds":[["c","clearRect",0,0,100,50],["g",1,"linear",0,50,0,0],["gs",1,0,"#ff0000"],["gs",1,1,"rgb(255,0,0)"],
        ["s","fillStyle",{"g":1}],["c","fillRect",0,0,100,50]]}]})J"));
    QImage image = QtBackend::make_image(int(r.px_width()), int(r.px_height()));
    backend.paint(r.paint(), image, QTransform::fromScale(dpr, dpr));
    check(r.px_height() >= 50 * dpr, "the canvas takes its height");
    check(near(at(image, int(50 * dpr), int(25 * dpr)), QColor(255, 0, 0)), "the canvas bitmap is painted where the element is");
}

/// Random changes painted twice, whole and over the last frame where the renderer reports damage:
/// the same pixels every time.
static void test_damage_repaints() {
    QtBackend backend;
    Renderer r(240, 1.5, false, backend);
    std::string setup = R"({"type":"ops","ops":[{"op":"el","id":2,"tag":"html","adopt":"html"},{"op":"el","id":3,"tag":"head","adopt":"head"},
        {"op":"el","id":4,"tag":"body","adopt":"body","attrs":[["style","margin:0;font:13px sans-serif"]]},{"op":"el","id":5,"tag":"style"},
        {"op":"css","id":5,"text":".b{display:inline-block;width:30px;height:20px;margin:3px;border-radius:6px;background:#39f;box-shadow:0 2px 6px rgba(0,0,0,.4)} .on{background:#f60} .d{display:inline-block;width:24px;height:16px;margin:3px;border-radius:5px;background:#3c6}"},
        {"op":"ins","parent":3,"id":5,"before":null})";
    // Rotation goes on boxes without text or shadow. Qt rasterises transformed glyphs differently once
    // its glyph cache holds them, and samples a transformed image (a rotated shadow) from where each
    // clipped span starts, so neither repeats exactly under another clip.
    for (int i = 0; i < 16; i++) {
        std::string e = std::to_string(100 + i), t = std::to_string(200 + i), d = std::to_string(300 + i);
        setup += R"(,{"op":"el","id":)" + e + R"(,"tag":"span","attrs":[["class","b"],["style","opacity:1"]]},{"op":"ins","parent":4,"id":)" + e +
                 R"(,"before":null},{"op":"text","id":)" + t + R"(,"data":"x"},{"op":"ins","parent":)" + e + R"(,"id":)" + t + R"(,"before":null})" +
                 R"(,{"op":"el","id":)" + d + R"(,"tag":"span","attrs":[["class","d"]]},{"op":"ins","parent":4,"id":)" + d + R"(,"before":null})";
    }
    setup += "]}";
    r.receive(parse(setup));
    QTransform scale = QTransform::fromScale(1.5, 1.5);
    QImage incremental;
    uint32_t seed = 11;
    auto next = [&] { return seed = seed * 1664525u + 1013904223u, seed >> 8; };
    int same = 0, partial = 0, rounds = 120;
    for (int round = 0; round < rounds; round++) {
        std::string ops;
        for (int k = 0; k < 2; k++) {
            std::string e = std::to_string(100 + next() % 16), t = std::to_string(200 + next() % 16);
            if (!ops.empty()) ops += ",";
            switch (next() % 4) {
            case 0: {
                std::string d = std::to_string(300 + next() % 16);
                ops += "{\"op\":\"style\",\"id\":" + e + ",\"name\":\"transform\",\"value\":\"translate(" + std::to_string(next() % 9) + "px,1px)\"},";
                ops += "{\"op\":\"style\",\"id\":" + d + ",\"name\":\"transform\",\"value\":\"rotate(" + std::to_string(next() % 40) + "deg) scale(0.9)\"}";
                break;
            }
            case 1: ops += "{\"op\":\"attr\",\"id\":" + e + ",\"name\":\"class\",\"value\":\"" + (next() % 2 ? "b" : "b on") + "\"}"; break;
            case 2: ops += "{\"op\":\"data\",\"id\":" + t + ",\"data\":\"" + std::string(1 + next() % 3, char('a' + next() % 26)) + "\"}"; break;
            default: ops += "{\"op\":\"style\",\"id\":" + e + ",\"name\":\"opacity\",\"value\":\"0." + std::to_string(3 + next() % 7) + "\"}"; break;
            }
        }
        r.receive(parse(R"({"type":"ops","ops":[)" + ops + "]}"));
        DisplayList list = r.paint();
        std::vector<Rect> damage;
        bool has = r.damage(damage) && incremental.width() == int(r.px_width()) && incremental.height() == int(r.px_height());
        QImage whole = QtBackend::make_image(int(r.px_width()), int(r.px_height()));
        backend.paint(list, whole, scale);
        if (!has) incremental = QtBackend::make_image(int(r.px_width()), int(r.px_height()));
        else partial++;
        backend.paint(list, incremental, scale, has ? &damage : nullptr);
        if (whole == incremental) same++;
        else if (same == round) whole.save("damage-whole.png"), incremental.save("damage-partial.png");
    }
    check(same == rounds, ("repainting only the damage gives the same pixels as painting everything (" + std::to_string(same) + " of " + std::to_string(rounds) + ")").c_str());
    check(partial > rounds / 2, "and most frames repaint only part");
}

/// Clips whose content stays inside them go, and the pixels stay the same.
static void test_inert_clips() {
    QtBackend backend;
    auto clip = [](Rect r, float radius) {
        Command c;
        c.kind = Cmd::ClipPush;
        c.rect = r;
        c.radii = Radii{radius, radius, radius, radius, radius, radius, radius, radius};
        return c;
    };
    Command pop;
    pop.kind = Cmd::ClipPop;
    DisplayList list{clip({0, 0, 100, 100}, 10), fill_rect({20, 20, 30, 30}, {255, 0, 0, 255}), pop,
                     clip({0, 0, 100, 100}, 10), fill_rect({80, 80, 40, 40}, {0, 0, 255, 255}), pop};
    DisplayList dropped = list;
    size_t gone = drop_inert_clips(dropped);
    check(gone == 1 && dropped.size() == 4, "a clip its content stays inside goes, one it crosses stays");
    QImage a = QtBackend::make_image(150, 150), b = QtBackend::make_image(150, 150);
    backend.paint(list, a, QTransform::fromScale(1.5, 1.5));
    backend.paint(dropped, b, QTransform::fromScale(1.5, 1.5));
    check(a == b, "and the pixels are the same");
}

/// An SVG's paint is recorded once and copied in where it recurs, and the painter rasters it once:
/// the pixels match painting it directly, after a move too.
static void test_cached_svg() {
    QtBackend backend;
    Renderer r(200, 1.5, false, backend);
    r.receive(parse(R"J({"type":"ops","ops":[
      {"op":"el","id":2,"tag":"html","adopt":"html"},{"op":"el","id":3,"tag":"head","adopt":"head"},{"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"style","id":4,"name":"margin","value":"0"},
      {"op":"el","id":9,"tag":"div","attrs":[["style","width:40px;height:0"]]},{"op":"ins","parent":4,"id":9,"before":null},
      {"op":"el","id":10,"tag":"div"},{"op":"ins","parent":4,"id":10,"before":null},
      {"op":"el","id":40,"tag":"svg","attrs":[["width","60"],["height","60"],["viewBox","0 0 60 60"]]},{"op":"ins","parent":10,"id":40,"before":null},
      {"op":"el","id":41,"tag":"circle","attrs":[["cx","30"],["cy","30"],["r","20"],["fill","#f00"],["stroke","#00f"],["stroke-width","4"]]},{"op":"ins","parent":40,"id":41,"before":null},
      {"op":"el","id":42,"tag":"path","attrs":[["d","M5 5 L55 10 L30 55 Z"],["fill","rgba(0,128,0,.6)"]]},{"op":"ins","parent":40,"id":42,"before":null}]})J"));
    auto keyed = [](const DisplayList& list, float& dy) {
        for (const Command& c : list)
            if (c.kind == Cmd::GroupPush && c.key) {
                dy = c.matrix[5];
                return true;
            }
        return false;
    };
    // Without keys, the same list paints directly.
    auto direct = [](DisplayList list) {
        for (Command& c : list) c.key = 0;
        QtBackend plain;
        QImage image = QtBackend::make_image(300, 210);
        plain.paint(list, image, QTransform::fromScale(1.5, 1.5));
        return image;
    };
    auto close = [](const QImage& a, const QImage& b) {
        for (int y = 0; y < a.height(); y++)
            for (int x = 0; x < a.width(); x++) {
                QRgb p = a.pixel(x, y), q = b.pixel(x, y);
                if (std::abs(qRed(p) - qRed(q)) > 2 || std::abs(qGreen(p) - qGreen(q)) > 2 || std::abs(qBlue(p) - qBlue(q)) > 2 || std::abs(qAlpha(p) - qAlpha(q)) > 2) return false;
            }
        return true;
    };
    float dy = -1;
    DisplayList first = r.paint();
    check(keyed(first, dy) && dy == 0, "the SVG paints inside a keyed group");
    QImage a = QtBackend::make_image(300, 210);
    backend.paint(first, a, QTransform::fromScale(1.5, 1.5));
    check(backend.cached_rasters() == 1, "which the painter rasters");
    check(close(a, direct(first)), "the raster draws what the paint would");
    check(near(at(a, 21, 45), QColor(255, 0, 0)), "the circle is there");

    r.receive(parse(R"({"type":"ops","ops":[{"op":"style","id":9,"name":"height","value":"40px"}]})"));
    DisplayList moved = r.paint();
    check(keyed(moved, dy) && dy == 40, "moved, the recorded paint is copied in, shifted");
    QImage b = QtBackend::make_image(300, 210);
    backend.paint(moved, b, QTransform::fromScale(1.5, 1.5));
    check(backend.cached_rasters() == 1, "and the raster is drawn again where it went");
    check(close(b, direct(moved)), "the same as painting it there");
    check(near(at(b, 21, 105), QColor(255, 0, 0)) && qAlpha(at(b, 21, 45)) == 0, "the circle moved");

    backend.trim();
    check(backend.cached_rasters() == 0, "trim drops the rasters");
}

static void test_stroke_gradient() {
    QtBackend backend;
    QImage image = QtBackend::make_image(100, 20);
    auto line = std::make_shared<Path>();
    line->ops = {Path::Move, Path::Line};
    line->points = {0, 10, 100, 10};
    line->stroke = true;
    line->stroke_width = 10;
    line->stroke_gradient = 1;
    float sg[7] = {0, 0, 100, 0, 0, 0, 0};
    std::copy(std::begin(sg), std::end(sg), line->sg);
    line->stroke_stops = {Stop{0, Color{255, 0, 0, 255}}, Stop{1, Color{0, 0, 255, 255}}};
    Command stroke;
    stroke.kind = Cmd::Path;
    stroke.path = line;
    backend.paint({stroke}, image, QTransform());
    check(qRed(at(image, 3, 10)) > 200 && qBlue(at(image, 3, 10)) < 60 && qBlue(at(image, 97, 10)) > 200 && qRed(at(image, 97, 10)) < 60,
          "an SVG stroke paints its gradient");
}

static void test_budgets() {
    QtBackend backend;
    float w = 0, h = 0;
    check(!backend.load_image(1, "data:IMAGE/SVG+XML,<svg xmlns='http://www.w3.org/2000/svg'/>", w, h), "an SVG in capitals is refused too");
    check(backend.load_image(2, "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=", w, h) && w == 1 && h == 1,
          "a small PNG decodes");
    // A valid 4097 × 1 PNG of 83 bytes: refused from its header, before it is decoded.
    check(!backend.load_image(3, "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAEAEAAAABCAAAAACUiF+eAAAAGklEQVR42u3BAQ0AAADCoPdPbQ8HFAAAAPBvEAIAAaWMqb0AAAAASUVORK5CYII=", w, h),
          "an image past 4096 px a side is refused from its header");

    auto red_square = [&](int id) {
        CanvasOp fill;
        fill.kind = CanvasOp::Fill;
        fill.path = rect_path(0, 0, 10, 10);
        fill.paint.color = Color{255, 0, 0, 255};
        backend.canvas_run(id, 10, 10, {fill});
        std::vector<uint8_t> rgba;
        backend.canvas_read(id, 5, 5, 1, 1, rgba);
        return rgba.size() == 4 && rgba[0] == 255 && rgba[3] == 255;
    };
    backend.canvas_run(10, 4096, 4096, {});
    check(!red_square(11), "past every canvas's pixel budget a new canvas gets no bitmap");
    backend.canvas_release(10);
    check(red_square(11), "and gets one once another is released");
}

int main(int argc, char** argv) {
    qputenv("QT_QPA_PLATFORM", "offscreen");
    QGuiApplication app(argc, argv);
    test_display_list();
    test_shadow_and_gradients();
    test_canvas_ops();
    test_canvas_through_renderer(1);
    test_canvas_through_renderer(1.5);
    test_damage_repaints();
    test_inert_clips();
    test_cached_svg();
    test_stroke_gradient();
    test_budgets();
    if (failures) {
        std::printf("%d failure(s)\n", failures);
        return 1;
    }
    std::printf("qt backend: all passed\n");
    return 0;
}
