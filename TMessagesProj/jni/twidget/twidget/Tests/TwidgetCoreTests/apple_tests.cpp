// Unit tests of TwidgetCore with the CoreGraphics backend (macOS, no platform UI):
// ops → HTML, layout and size reports, queries, hit testing → events, the
// colour-scheme rewrite, frozen. Run through ctest or directly.
#include "coregraphics.h"
#include "renderer.h"

#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

using twidget::Json;
using namespace twlite;

static int failures = 0;
#define CHECK(cond) do { if (!(cond)) { std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); failures++; } } while (0)

static Json parse(const std::string& s) {
    auto v = Json::parse(s);
    if (!v) { std::printf("bad test JSON: %s\n", s.c_str()); failures++; return Json(); }
    return *v;
}

static const Json* find_type(const std::vector<Json>& out, const char* type) {
    for (const Json& m : out) if (m["type"].asString() == type) return &m;
    return nullptr;
}

/// A body with a 100 px tall red box carrying a button, as the validator would emit it.
static Json card_ops() {
    return parse(R"([
      {"op":"el","id":2,"tag":"html","adopt":"html","attrs":[["lang","en"]]},
      {"op":"el","id":3,"tag":"head","adopt":"head"},
      {"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"el","id":5,"tag":"style"},
      {"op":"css","id":5,"text":".box{height:100px;background:#f00;margin:0}@media (prefers-color-scheme: dark){.box{background:#00f}}"},
      {"op":"ins","id":5,"parent":3},
      {"op":"el","id":6,"tag":"div","attrs":[["class","box"]]},
      {"op":"ins","id":6,"parent":4},
      {"op":"el","id":7,"tag":"button","attrs":[["id","go"]]},
      {"op":"text","id":8,"data":"Go"},
      {"op":"ins","id":8,"parent":7},
      {"op":"ins","id":7,"parent":6},
      {"op":"style","id":4,"name":"margin","value":"0"}
    ])");
}

static void test_color_scheme_rewrite() {
    CHECK(rewrite_color_scheme("@media (prefers-color-scheme: dark){a{}}", true) == "@media (min-width:0px){a{}}");
    CHECK(rewrite_color_scheme("@media (prefers-color-scheme: dark){a{}}", false) == "@media (min-width:999999px){a{}}");
    CHECK(rewrite_color_scheme("@media (prefers-color-scheme:light) and (max-width:400px){}", true) == "@media (min-width:999999px) and (max-width:400px){}");
    CHECK(rewrite_color_scheme("body{color:red}", true) == "body{color:red}");
}

static void test_ops_layout_and_queries() {
    CGBackend backend;
    Renderer r(300, 1, false, backend);
    Json msg = Json::object();
    msg.set("type", "ops").set("ops", card_ops());
    auto out = r.receive(msg);
    const Json* size = find_type(out, "size");
    CHECK(size != nullptr);
    if (size) {
        CHECK(size->operator[]("width").asNumber() == 300);
        CHECK(size->operator[]("height").asNumber() >= 100);
        CHECK(size->operator[]("height").asNumber() < 140);
    }
    CHECK(r.last_html().find("data-twid=\"6\"") != std::string::npos);
    CHECK(r.last_html().find("lang=\"en\"") != std::string::npos);
    CHECK(r.last_html().find("<body data-twid=\"4\" style=\"margin:0\">") != std::string::npos);

    // rect of the box: full width, 100 px tall
    Json q = parse(R"({"type":"query","qid":1,"query":{"q":"rect","id":6}})");
    auto res = r.receive(q);
    CHECK(res.size() == 1);
    const Json& rect = res[0]["result"];
    CHECK(rect["width"].asNumber() == 300);
    CHECK(rect["height"].asNumber() == 100);
    // elementFromPoint inside the button hits the button (or its text's parent)
    Json efp = parse(R"({"type":"query","qid":2,"query":{"q":"elementFromPoint","x":10,"y":10}})");
    res = r.receive(efp);
    CHECK(res.size() == 1 && res[0]["result"].asNumber() == 7);
    // unknown element
    Json bad = parse(R"({"type":"query","qid":3,"query":{"q":"rect","id":99}})");
    res = r.receive(bad);
    CHECK(res.size() == 1 && res[0]["result"]["error"].asString() == "no such element");
    // paint produces something red in the box
    DisplayList list = r.paint();
    bool red = false;
    for (const Command& c : list) if (c.kind == Cmd::FillRect && c.color.r == 255 && c.color.g == 0) red = true;
    CHECK(red);
    CHECK(!r.dirty());
    std::vector<uint8_t> px(size_t(r.px_width()) * r.px_height() * 4);
    backend.paint(list, px.data(), r.px_width(), r.px_height(), 1);
    // pixel (150, 50): red box
    size_t i = (50 * r.px_width() + 150) * 4;
    CHECK(px[i] == 255 && px[i + 1] == 0 && px[i + 2] == 0 && px[i + 3] == 255);
}

static void test_theme() {
    CGBackend backend;
    Renderer r(300, 1, true, backend);
    Json msg = Json::object();
    msg.set("type", "ops").set("ops", card_ops());
    r.receive(msg);
    DisplayList list = r.paint();
    bool blue = false, red = false;
    for (const Command& c : list) if (c.kind == Cmd::FillRect) { if (c.color.b == 255 && c.color.r == 0) blue = true; if (c.color.r == 255 && c.color.g == 0) red = true; }
    CHECK(blue);
    CHECK(!red);
    std::vector<Json> out;
    r.set_theme(false, out);
    list = r.paint();
    red = false;
    for (const Command& c : list) if (c.kind == Cmd::FillRect && c.color.r == 255 && c.color.g == 0) red = true;
    CHECK(red);
}

static void test_tap_events() {
    CGBackend backend;
    Renderer r(300, 1, false, backend);
    Json msg = Json::object();
    msg.set("type", "ops").set("ops", card_ops());
    r.receive(msg);
    r.receive(parse(R"({"type":"listen","types":["pointerdown","pointerup"]})"));
    auto down = r.touch(0, 10, 10, 0, true);
    const Json* gesture = find_type(down, "gesture");
    CHECK(gesture && gesture->operator[]("wants").asBool() == false);
    int pointerdown = 0;
    for (const Json& m : down) if (m["type"].asString() == "event" && m["ev"]["type"].asString() == "pointerdown") { pointerdown++; CHECK(m["ev"]["targetId"].asNumber() == 7); }
    CHECK(pointerdown == 1);
    // a small jitter stays inside the slop
    auto move = r.touch(1, 12, 11, 0, true);
    CHECK(move.empty());
    auto up = r.touch(2, 12, 11, 0, true);
    bool click = false, pointerup = false, mouseup = false;
    for (const Json& m : up) if (m["type"].asString() == "event") {
        const std::string& t = m["ev"]["type"].asString();
        if (t == "click") { click = true; CHECK(m["ev"]["targetId"].asNumber() == 7); CHECK(m["ev"]["detail"].asNumber() == 1); }
        if (t == "pointerup") pointerup = true;
        if (t == "mouseup") mouseup = true;  // not listened for
    }
    CHECK(click);
    CHECK(pointerup);
    CHECK(!mouseup);
    // a drag is no click
    r.touch(0, 10, 10, 0, true);
    r.touch(1, 60, 10, 0, true);
    auto up2 = r.touch(2, 60, 10, 0, true);
    click = false;
    for (const Json& m : up2) if (m["type"].asString() == "event" && m["ev"]["type"].asString() == "click") click = true;
    CHECK(!click);
    // learned preventDefault on the target makes the gesture the widget's
    r.receive(parse(R"({"type":"preventDefault","keys":["7:pointerdown"]})"));
    auto down2 = r.touch(0, 10, 10, 0, true);
    gesture = find_type(down2, "gesture");
    CHECK(gesture && gesture->operator[]("wants").asBool() == true);
    r.touch(3, 10, 10, 0, true);
}

static void test_mutations() {
    CGBackend backend;
    Renderer r(300, 1, false, backend);
    Json msg = Json::object();
    msg.set("type", "ops").set("ops", card_ops());
    r.receive(msg);
    // grow the box, then remove it: the size follows
    auto out = r.receive(parse(R"({"type":"ops","ops":[{"op":"style","id":6,"name":"height","value":"200px"}]})"));
    const Json* size = find_type(out, "size");
    CHECK(size && size->operator[]("height").asNumber() >= 200);
    out = r.receive(parse(R"({"type":"ops","ops":[{"op":"rm","id":6}]})"));
    size = find_type(out, "size");
    CHECK(size && size->operator[]("height").asNumber() < 100);
    CHECK(r.last_html().find("data-twid=\"6\"") == std::string::npos);
    CHECK(r.last_html().find("data-twid=\"7\"") == std::string::npos);  // the subtree went with it
    // ops on forgotten ids are ignored; on* attributes are refused
    out = r.receive(parse(R"({"type":"ops","ops":[{"op":"attr","id":7,"name":"class","value":"x"},{"op":"attr","id":4,"name":"onclick","value":"x"}]})"));
    CHECK(find_type(out, "violation") != nullptr);
    CHECK(r.last_html().find("onclick") == std::string::npos);
    // text escaping
    out = r.receive(parse(R"({"type":"ops","ops":[{"op":"text","id":20,"data":"<b>&"},{"op":"ins","id":20,"parent":4}]})"));
    CHECK(r.last_html().find("&lt;b&gt;&amp;") != std::string::npos);
    // frozen: badge and attribute, touches ignored
    out = r.receive(parse(R"({"type":"frozen","reason":"CPU limit","fatal":true})"));
    CHECK(r.last_html().find("data-tw-frozen=\"fatal\"") != std::string::npos);
    CHECK(r.last_html().find("CPU limit</div>") != std::string::npos);
    CHECK(r.touch(0, 10, 10, 0, true).empty());
}

static void test_measure_text() {
    CGBackend backend;
    Renderer r(300, 1, false, backend);
    auto res = r.receive(parse(R"({"type":"query","qid":1,"query":{"q":"measureText","font":"bold 20px sans-serif","text":"Hello"}})"));
    CHECK(res.size() == 1);
    double w = res[0]["result"]["width"].asNumber();
    CHECK(w > 30 && w < 90);
    CHECK(res[0]["result"]["fontBoundingBoxAscent"].asNumber() > 10);
}

// An SVG's paint is rastered once and drawn from the raster where its key recurs: the pixels match
// painting it directly, after a move too.
static void test_cached_svg() {
    CGBackend backend;
    Renderer r(200, 1.5, false, backend);
    r.receive(parse(R"J({"type":"ops","ops":[
      {"op":"el","id":2,"tag":"html","adopt":"html"},{"op":"el","id":3,"tag":"head","adopt":"head"},{"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"style","id":4,"name":"margin","value":"0"},
      {"op":"el","id":9,"tag":"div","attrs":[["style","width:40px;height:0"]]},{"op":"ins","parent":4,"id":9,"before":null},
      {"op":"el","id":10,"tag":"div"},{"op":"ins","parent":4,"id":10,"before":null},
      {"op":"el","id":40,"tag":"svg","attrs":[["width","60"],["height","60"],["viewBox","0 0 60 60"]]},{"op":"ins","parent":10,"id":40,"before":null},
      {"op":"el","id":41,"tag":"circle","attrs":[["cx","30"],["cy","30"],["r","20"],["fill","#f00"],["stroke","#00f"],["stroke-width","4"]]},{"op":"ins","parent":40,"id":41,"before":null},
      {"op":"el","id":42,"tag":"path","attrs":[["d","M5 5 L55 10 L30 55 Z"],["fill","rgba(0,128,0,.6)"]]},{"op":"ins","parent":40,"id":42,"before":null}]})J"));
    const uint32_t w = 300, h = 210;
    auto paint = [&](CGBackend& b, const DisplayList& list) {
        std::vector<uint8_t> px(size_t(w) * h * 4);
        b.paint(list, px.data(), w, h, 1.5);
        return px;
    };
    auto direct = [&](DisplayList list) {
        for (Command& c : list) c.key = 0;
        CGBackend plain;
        return paint(plain, list);
    };
    auto close = [](const std::vector<uint8_t>& a, const std::vector<uint8_t>& b) {
        for (size_t i = 0; i < a.size(); i++)
            if (std::abs(int(a[i]) - int(b[i])) > 2) return false;
        return true;
    };
    auto pixel = [&](const std::vector<uint8_t>& px, int x, int y) { return px.data() + (size_t(y) * w + x) * 4; };

    DisplayList first = r.paint();
    auto a = paint(backend, first);
    CHECK(backend.cached_rasters() == 1);
    CHECK(close(a, direct(first)));
    CHECK(pixel(a, 21, 45)[0] > 240 && pixel(a, 21, 45)[1] < 15);

    r.receive(parse(R"({"type":"ops","ops":[{"op":"style","id":9,"name":"height","value":"40px"}]})"));
    DisplayList moved = r.paint();
    auto b = paint(backend, moved);
    CHECK(backend.cached_rasters() == 1);
    CHECK(close(b, direct(moved)));
    CHECK(pixel(b, 21, 105)[0] > 240 && pixel(b, 21, 105)[1] < 15 && pixel(b, 21, 45)[3] == 0);

    backend.trim();
    CHECK(backend.cached_rasters() == 0);
}

int main() {
    test_color_scheme_rewrite();
    test_ops_layout_and_queries();
    test_theme();
    test_tap_events();
    test_mutations();
    test_measure_text();
    test_cached_svg();
    if (failures) {
        std::printf("%d failure(s)\n", failures);
        return 1;
    }
    std::printf("litehtml renderer: all tests passed\n");
    return 0;
}
