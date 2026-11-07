// Frame cost of TwidgetCore under a busy widget: the scenarios of the Swift renderer's
// FrameCostBenchTests (Tests/TwidgetRendererTests), sent as the same protocol ops, so the two can be
// compared. It measures the core alone: the stub backend measures text at 8 px a byte and rasters
// nothing, so a platform's painting is not in the numbers.
//   twidget_core_bench [mixed|transform|swarm|css|drag|drag-doc|canvas] [count] [frames]
#include "renderer.h"

#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

using twidget::Json;
using namespace twlite;

namespace {

struct StubBackend : Backend {
    int next = 0;
    int create_font(const std::string&, float size, int, bool, const std::string&, FontMetrics& out) override {
        out.ascent = size * 0.8f;
        out.descent = size * 0.2f;
        out.height = size * 1.2f;
        out.x_height = size * 0.5f;
        out.zero_width = 8;
        return ++next;
    }
    void delete_font(int) override {}
    float text_width(int, const std::string& text) override { return 8.0f * float(text.size()); }
    bool load_image(int, const std::string&, float&, float&) override { return false; }
};

double now_us() { return std::chrono::duration<double, std::micro>(std::chrono::steady_clock::now().time_since_epoch()).count(); }

Json parse(const std::string& s) {
    auto j = Json::parse(s);
    if (!j) {
        std::fprintf(stderr, "bad json\n");
        std::exit(2);
    }
    return *j;
}

std::string str(const std::string& s) { return Json(s).dump(); }
std::string num(double v) { return Json::number(v); }

/// Ops of `body` as a message; each op a JSON object without its braces.
std::string message(const std::vector<std::string>& ops) {
    std::string out = "{\"type\":\"ops\",\"ops\":[";
    for (size_t i = 0; i < ops.size(); i++) {
        if (i) out += ',';
        out += '{';
        out += ops[i];
        out += '}';
    }
    return out + "]}";
}

std::string el(int id, const char* tag, const std::string& attrs = "") {
    return "\"op\":\"el\",\"id\":" + std::to_string(id) + ",\"tag\":\"" + tag + "\",\"attrs\":[" + attrs + "]";
}
std::string ins(int id, int parent) { return "\"op\":\"ins\",\"id\":" + std::to_string(id) + ",\"parent\":" + std::to_string(parent) + ",\"before\":null"; }
std::string text(int id, const std::string& data) { return "\"op\":\"text\",\"id\":" + std::to_string(id) + ",\"data\":" + str(data); }
std::string data(int id, const std::string& value) { return "\"op\":\"data\",\"id\":" + std::to_string(id) + ",\"data\":" + str(value); }
std::string style(int id, const char* name, const std::string& value) {
    return "\"op\":\"style\",\"id\":" + std::to_string(id) + ",\"name\":\"" + name + "\",\"value\":" + str(value);
}
std::string attr(const char* name, const std::string& value) { return "[\"" + std::string(name) + "\"," + str(value) + "]"; }

std::vector<std::string> document(const std::string& css) {
    return {el(2, "html") + ",\"adopt\":\"html\"", el(3, "head") + ",\"adopt\":\"head\"", el(1, "body") + ",\"adopt\":\"body\"", el(4, "style"),
            "\"op\":\"css\",\"id\":4,\"text\":" + str(css), ins(4, 3)};
}

struct Scenario {
    std::string setup;
    std::vector<std::string> frames;
    std::vector<std::string> queries;  // sent after every frame's ops
    bool hover = false;
};

Scenario busy(const std::string& mode, int count, int frames) {
    Scenario s;
    auto ops = document("body { margin: 0; font: 12px sans-serif; } .box { position: absolute; width: 18px; height: 18px; border-radius: 4px; background: #3a7; } "
                        ".label { position: absolute; left: 0; color: #222; } canvas { position: absolute; left: 200px; top: 0; width: 200px; height: 200px; }");
    int next = 100;
    std::vector<int> boxes, texts;
    for (int i = 0; i < count; i++) {
        int id = next++;
        ops.push_back(el(id, "div", attr("class", "box") + "," + attr("style", "left:" + std::to_string((i % 20) * 20) + "px;top:" + std::to_string((i / 20) * 20) + "px")));
        ops.push_back(ins(id, 1));
        boxes.push_back(id);
    }
    for (int i = 0; i < 30; i++) {
        int id = next, t = next + 1;
        next += 2;
        ops.push_back(el(id, "div", attr("class", "label") + "," + attr("style", "top:" + std::to_string(i * 16) + "px")));
        ops.push_back(ins(id, 1));
        ops.push_back(text(t, "label " + std::to_string(i)));
        ops.push_back(ins(t, id));
        texts.push_back(t);
    }
    int canvas = next++;
    ops.push_back(el(canvas, "canvas", attr("width", "200") + "," + attr("height", "200")));
    ops.push_back(ins(canvas, 1));
    s.setup = message(ops);

    for (int f = 0; f < frames; f++) {
        std::vector<std::string> frame;
        bool drag = mode.rfind("drag", 0) == 0;
        int moving = std::min(int(boxes.size()), drag ? 6 : 300);
        for (int k = 0; k < moving; k++) {
            if (drag) {
                // The same few, in the last row where nothing paints over them, as a drag would have it.
                int id = boxes[boxes.size() - 1 - size_t(k) % boxes.size()];
                frame.push_back(style(id, "transform", "translate(" + std::to_string(f % 13) + "px, " + std::to_string((f + k) % 9) + "px)"));
                continue;
            }
            int id = boxes[size_t((k * 7 + f) % int(boxes.size()))];
            frame.push_back(style(id, "transform", "translate(" + std::to_string(f % 13) + "px, " + std::to_string((f + k) % 9) + "px) rotate(" + std::to_string((f * 3 + k) % 360) + "deg)"));
        }
        if (mode != "transform" && !drag) {
            for (int k = 0; k < 20; k++)
                frame.push_back(style(boxes[size_t((k * 31 + f) % int(boxes.size()))], "background-color", "rgb(" + std::to_string(f % 255) + ", " + std::to_string(k * 10) + ", 120)"));
            for (size_t k = 0; k < texts.size(); k++) frame.push_back(data(texts[k], "label " + std::to_string(k) + " frame " + std::to_string(f)));
            frame.push_back("\"op\":\"canvas\",\"id\":" + std::to_string(canvas) + ",\"cmds\":[[\"s\",\"fillStyle\",\"#f80\"],[\"c\",\"fillRect\"," + std::to_string(f % 100) + ",10,40,40]]");
        }
        s.frames.push_back(message(frame));
    }
    if (mode != "transform" && mode.rfind("drag", 0) != 0)
        for (int q = 0; q < 3; q++) s.queries.push_back("{\"type\":\"query\",\"qid\":" + std::to_string(q) + ",\"query\":{\"q\":\"rect\",\"id\":" + std::to_string(boxes[size_t(q * 50) % boxes.size()]) + "}}");
    s.hover = mode == "mixed";
    return s;
}

Scenario swarm(int count, int frames) {
    Scenario s;
    auto ops = document(
        "body { margin: 0; font: 14px sans-serif; } .card { padding: 14px 16px 16px; background: #fff; overflow: hidden; } .stage { position: relative; height: 260px; "
        "border-radius: 14px; overflow: hidden; background: #eef0f6; border: 1px solid rgba(0,0,0,.08); } .t { position: absolute; left: 0; top: 0; width: 9px; "
        "height: 9px; border-radius: 3px; } .orb { position: absolute; left: 0; top: 0; width: 92px; height: 92px; border-radius: 46px; background: "
        "rgba(255,255,255,.16); border: 1px solid rgba(255,255,255,.35); box-shadow: 0 8px 30px rgba(0,0,0,.25); } .log { position: relative; height: 128px; "
        "margin-top: 12px; overflow: auto; border-radius: 12px; background: #f2f2f7; } .sh { position: sticky; top: 0; display: flex; justify-content: "
        "space-between; padding: 6px 10px; font-size: 11px; font-weight: 700; color: #6e6e73; background: #f2f2f7; } .r { display: flex; justify-content: "
        "space-between; padding: 5px 10px; font-size: 12px; border-bottom: 1px solid rgba(0,0,0,.08); }");
    ops.push_back(el(5, "div", attr("class", "card")));
    ops.push_back(ins(5, 1));
    ops.push_back(el(6, "div", attr("class", "stage")));
    ops.push_back(ins(6, 5));
    ops.push_back(el(7, "div", attr("class", "log")));
    ops.push_back(ins(7, 5));
    ops.push_back(el(8, "div", attr("class", "sh")));
    ops.push_back(ins(8, 7));
    ops.push_back(text(9, "Event Frame"));
    ops.push_back(ins(9, 8));

    struct Tile { int id; double hx, hy, ph; int hue; };
    int next = 100;
    const double w = 366, h = 260;
    int cols = int(std::ceil(std::sqrt(double(count) * w / h))), rows = (count + cols - 1) / cols;
    std::vector<Tile> tiles;
    for (int i = 0; i < count; i++) {
        int id = next++, c = i % cols, r = i / cols, hue = 360 * i / count;
        ops.push_back(el(id, "div", attr("class", "t") + "," + attr("style", "background-color: hsl(" + std::to_string(hue) + ", 80%, 58%)")));
        ops.push_back(ins(id, 6));
        tiles.push_back({id, (c + 0.5) * w / cols - 4.5, (r + 0.5) * h / rows - 4.5, c * 0.35 + r * 0.22, hue});
    }
    std::vector<int> orbs;
    for (int i = 0; i < 3; i++) {
        ops.push_back(el(next, "div", attr("class", "orb")));
        ops.push_back(ins(next, 6));
        orbs.push_back(next++);
    }
    std::vector<std::pair<int, int>> logs;
    for (int i = 0; i < 40; i++) {
        int row = next, a = next + 1, at = next + 2, b = next + 3, bt = next + 4;
        next += 5;
        ops.push_back(el(row, "div", attr("class", "r")));
        ops.push_back(ins(row, 7));
        ops.push_back(el(a, "span"));
        ops.push_back(ins(a, row));
        ops.push_back(text(at, "tile " + std::to_string(i * 37 % 800) + " re-coloured"));
        ops.push_back(ins(at, a));
        ops.push_back(el(b, "span"));
        ops.push_back(ins(b, row));
        ops.push_back(text(bt, "#" + std::to_string(i)));
        ops.push_back(ins(bt, b));
        logs.emplace_back(at, bt);
    }
    s.setup = message(ops);

    const double log_max = 40.0 * 24 + 24 - 128;
    std::vector<double> px(tiles.size()), py(tiles.size());
    std::vector<int> hues;
    for (const Tile& t : tiles) hues.push_back(t.hue);
    double orb[3] = {0, 2.1, 4.2};
    char buf[160];
    for (int f = 1; f <= frames; f++) {
        double t = f / 60.0;
        std::vector<std::string> frame;
        for (size_t i = 0; i < tiles.size(); i++) {
            const Tile& p = tiles[i];
            double x = p.hx + std::cos(t * 1.3 + p.ph * 0.7) * 6, y = p.hy + std::sin(t * 2.2 + p.ph) * 14;
            double sc = 0.75 + 0.35 * std::sin(t * 3 + p.ph), rot = std::sin(t + p.ph) * 40;
            px[i] += (x - px[i]) * 0.35;
            py[i] += (y - py[i]) * 0.35;
            std::snprintf(buf, sizeof buf, "translate(%.1fpx, %.1fpx) rotate(%.1fdeg) scale(%.2f)", px[i], py[i], rot, sc);
            frame.push_back(style(p.id, "transform", buf));
        }
        for (int k = 0; k < 16; k++) {
            size_t i = size_t((f * 16 + k * 97) % int(tiles.size()));
            hues[i] = (hues[i] + 47) % 360;
            frame.push_back(style(tiles[i].id, "background-color", "hsl(" + std::to_string(hues[i]) + ", 80%, 58%)"));
        }
        for (int i = 0; i < 3; i++) {
            orb[i] += 0.012 * (1 + i * 0.4);
            std::snprintf(buf, sizeof buf, "translate(%.1fpx, %.1fpx) scale(%.2f)", w / 2 - 46 + std::cos(orb[i]) * w * 0.33, h / 2 - 46 + std::sin(orb[i] * 1.3) * 70,
                          0.8 + 0.25 * std::sin(orb[i] * 2));
            frame.push_back(style(orbs[size_t(i)], "transform", buf));
        }
        if (f % 12 == 0) {
            auto row = logs[size_t((f / 12) % int(logs.size()))];
            frame.push_back(data(row.first, "tile " + std::to_string((f * 16) % int(tiles.size())) + " re-coloured"));
            frame.push_back(data(row.second, "#" + std::to_string(f)));
        }
        frame.push_back("\"op\":\"prop\",\"id\":7,\"name\":\"scrollTop\",\"value\":" + num(std::fmod(f * 0.6, log_max)));
        s.frames.push_back(message(frame));
    }
    return s;
}

/// A chart a script redraws on a canvas every frame: `count` points as line segments, bars and labels.
Scenario canvas_chart(int count, int frames) {
    Scenario s;
    auto ops = document("body { margin: 0; } canvas { width: 400px; height: 300px; }");
    ops.push_back(el(100, "canvas", attr("width", "800") + "," + attr("height", "600")));
    ops.push_back(ins(100, 1));
    s.setup = message(ops);
    for (int f = 0; f < frames; f++) {
        std::string cmds = "[\"c\",\"clearRect\",0,0,800,600],[\"s\",\"lineWidth\",2],[\"s\",\"strokeStyle\",\"#3a7\"],[\"c\",\"beginPath\"]";
        for (int i = 0; i < count; i++) {
            double x = 800.0 * i / count, y = 300 + 200 * std::sin((i + f) * 0.05);
            cmds += std::string(",[\"c\",\"") + (i ? "lineTo" : "moveTo") + "\"," + num(x) + "," + num(y) + "]";
        }
        cmds += ",[\"c\",\"stroke\"],[\"s\",\"fillStyle\",\"#f80\"]";
        for (int i = 0; i < count / 10; i++)
            cmds += ",[\"c\",\"fillRect\"," + num(800.0 * i * 10 / count) + ",580,6," + num(-((i * 37 + f) % 200)) + "]";
        cmds += ",[\"s\",\"fillStyle\",\"#222\"],[\"s\",\"font\",\"12px sans-serif\"]";
        for (int i = 0; i < 10; i++) cmds += ",[\"c\",\"fillText\",\"" + std::to_string(i * 10 + f % 10) + "\"," + num(i * 80) + ",20]";
        s.frames.push_back(message({"\"op\":\"canvas\",\"id\":100,\"cmds\":[" + cmds + "]"}));
    }
    return s;
}

/// CSS animations the core runs itself, composited: frames are ticks, with no ops at all. Rows of
/// spinners, every tenth row with a pulsing background behind it.
Scenario css_animations(int count) {
    Scenario s;
    auto ops = document("body { margin: 0; } @keyframes spin { to { transform: rotate(360deg) } } @keyframes glow { to { background-color: #fc0 } } "
                        ".row { padding: 2px; } .glow { animation: glow 2s infinite alternate; } "
                        ".s { display: inline-block; width: 10px; height: 10px; margin: 4px; background: #39f; animation: spin 1s linear infinite; }");
    int next = 100, row = 0;
    for (int i = 0; i < count; i++) {
        if (i % 20 == 0) {
            row = next++;
            ops.push_back(el(row, "div", attr("class", (i / 20) % 10 == 0 ? "row glow" : "row")));
            ops.push_back(ins(row, 1));
        }
        int id = next++;
        ops.push_back(el(id, "div", attr("class", "s") + "," + attr("style", "animation-delay:-" + std::to_string(i % 17) + "0ms")));
        ops.push_back(ins(id, row));
    }
    s.setup = message(ops);
    return s;
}

} // namespace

int main(int argc, char** argv) {
    std::string mode = argc > 1 ? argv[1] : "mixed";
    int count = argc > 2 ? std::atoi(argv[2]) : (mode == "swarm" ? 800 : 1000);
    int frames = argc > 3 ? std::atoi(argv[3]) : 300;
    Scenario scenario = mode == "swarm" ? swarm(count, frames) : mode == "css" ? css_animations(count) : mode == "canvas" ? canvas_chart(count, frames) : busy(mode, count, frames);

    StubBackend backend;
    Renderer r(400, 2, false, backend);
    // drag: a few elements a script moves, as scripted layers; drag-doc: the same, painted in the document.
    if (mode == "css" || mode == "drag") r.set_compositing(true);
    double t = now_us();
    r.receive(parse(scenario.setup));
    r.paint();
    double setup = now_us() - t;

    double receive = 0, tick = 0, paint = 0, serialize = 0, build = 0;
    uint64_t layouts = r.layouts();
    int builds = r.stats().builds, patched = r.stats().patched, in_place = r.stats().in_place;
    size_t commands = 0;
    for (int f = 0; f < frames; f++) {
        double a = now_us();
        int before = r.stats().builds;
        if (size_t(f) < scenario.frames.size()) r.receive(parse(scenario.frames[size_t(f)]));
        if (r.stats().builds != before) {
            serialize += r.stats().serialize_ms * 1000;
            build += r.stats().parse_ms * 1000;
        }
        for (const std::string& q : scenario.queries) r.receive(parse(q));
        if (scenario.hover) r.hover(0, f % 400, 300);
        double b = now_us();
        r.tick();
        double c = now_us();
        if (r.dirty()) commands += r.paint().size();
        double d = now_us();
        receive += b - a;
        tick += c - b;
        paint += d - c;
    }
    std::printf("BENCH mode=%s n=%d frames=%d setup=%.0fms total=%.1fus receive=%.1fus tick=%.1fus paint=%.1fus\n", mode.c_str(), count, frames, setup / 1000,
                (receive + tick + paint) / frames, receive / frames, tick / frames, paint / frames);
    std::printf("BENCH stages serialize=%.1fus build=%.1fus (the last build of each frame)\n", serialize / frames, build / frames);
    std::printf("BENCH layouts=%.2f/frame builds=%.2f/frame patched=%d in-place=%d commands=%.0f/frame layers=%zu\n", double(r.layouts() - layouts) / frames,
                double(r.stats().builds - builds) / frames, r.stats().patched - patched, r.stats().in_place - in_place, double(commands) / frames, r.composition().layers.size());
    return 0;
}
