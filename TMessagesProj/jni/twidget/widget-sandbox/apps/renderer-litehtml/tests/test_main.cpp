// Unit tests of the litehtml renderer core (host build, no platform UI):
// ops → HTML, layout and size reports, queries, hit testing → events, the
// colour-scheme rewrite, frozen. Run through ctest or directly.
#include "../src/apple/coregraphics.h"
#include "../src/renderer.h"

#include <cstdio>
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
      {"op":"style","id":4,"name":"margin","value":"0"},
      {"op":"style","id":4,"name":"padding","value":"0"}
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
    CHECK(r.last_html().find("<body data-twid=\"4\" style=\"margin:0;padding:0\">") != std::string::npos);

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

/// The host's theme message: the variables inline and !important on <html> (the palette and the text tokens completed,
/// bad values dropped), the widget's own style op on a theme variable ignored; the profile's UA layer draws the root on
/// them: the text tokens' size, the padding token as the body's margin, the first child's margin trimmed, and no
/// background of its own (the bubble behind the renderer is the canvas), so a body background covers the whole canvas.
static void test_theme_variables_and_ua_layer() {
    CGBackend backend;
    Renderer r(300, 1, false, backend);
    Json theme = parse(R"({"type":"theme","theme":{"colorScheme":"light","params":{"bg_color":"#123456","text_color":"red"},
      "button":{"radius":"10px","font":"15px Helvetica"},"text":{"fontSize":20,"lineHeight":9,"padding":"10px 20px"}}})");
    r.receive(theme);
    Json msg = Json::object();
    msg.set("type", "ops").set("ops", parse(R"([
      {"op":"el","id":2,"tag":"html","adopt":"html"},
      {"op":"el","id":3,"tag":"head","adopt":"head"},
      {"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"el","id":5,"tag":"h1"},
      {"op":"text","id":6,"data":"Title"},
      {"op":"ins","id":6,"parent":5},
      {"op":"ins","id":5,"parent":4},
      {"op":"el","id":7,"tag":"p"},
      {"op":"text","id":8,"data":"Text"},
      {"op":"ins","id":8,"parent":7},
      {"op":"ins","id":7,"parent":4},
      {"op":"style","id":2,"name":"--tg-theme-bg-color","value":"#ff0000"}
    ])"));
    r.receive(msg);
    const std::string& html = r.last_html();
    CHECK(html.find("--tg-theme-bg-color:#123456 !important") != std::string::npos);
    CHECK(html.find("--tg-theme-text-color:#000000 !important") != std::string::npos);
    CHECK(html.find("--tg-button-radius:10px !important") != std::string::npos);
    CHECK(html.find("--tg-button-font") == std::string::npos);
    CHECK(html.find("--tg-text-font-size:20px !important") != std::string::npos);
    CHECK(html.find("--tg-text-line-height:1.35 !important") != std::string::npos);
    CHECK(html.find("--tg-text-padding:10px 20px !important") != std::string::npos);
    CHECK(html.find("#ff0000") == std::string::npos);
    // h1: the first child, at the padding (its top margin trimmed), 1.6 × 20 px at line height 1.25
    Json res = r.receive(parse(R"({"type":"query","qid":1,"query":{"q":"rect","id":5}})"))[0]["result"];
    CHECK(res["x"].asNumber() == 20);
    CHECK(res["y"].asNumber() == 10);
    CHECK(res["width"].asNumber() == 260);
    CHECK(res["height"].asNumber() >= 39 && res["height"].asNumber() <= 41);
    // the renderer never paints the bubble's colour itself
    DisplayList list = r.paint();
    bool bg = false;
    for (const Command& c : list) if (c.kind == Cmd::FillRect && c.color.r == 0x12 && c.color.g == 0x34 && c.color.b == 0x56) bg = true;
    CHECK(!bg);

    // a body background propagates to the canvas: the body's margins are painted too, no ring of the bubble
    Renderer g(300, 1, false, backend);
    g.receive(theme);
    Json body_bg = Json::object();
    body_bg.set("type", "ops").set("ops", parse(R"([
      {"op":"el","id":2,"tag":"html","adopt":"html"},
      {"op":"el","id":3,"tag":"head","adopt":"head"},
      {"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"el","id":5,"tag":"style"},
      {"op":"css","id":5,"text":"body{background:#00ff00}"},
      {"op":"ins","id":5,"parent":3},
      {"op":"el","id":6,"tag":"p"},
      {"op":"text","id":7,"data":"x"},
      {"op":"ins","id":7,"parent":6},
      {"op":"ins","id":6,"parent":4}
    ])"));
    g.receive(body_bg);
    bool canvas = false;
    for (const Command& c : g.paint())
        if (c.kind == Cmd::FillRect && c.color.g == 255 && c.color.r == 0 && c.rect.x == 0 && c.rect.y == 0 && c.rect.w == 300) canvas = true;
    CHECK(canvas);
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

int main() {
    test_color_scheme_rewrite();
    test_ops_layout_and_queries();
    test_theme();
    test_theme_variables_and_ua_layer();
    test_tap_events();
    test_mutations();
    test_measure_text();
    if (failures) {
        std::printf("%d failure(s)\n", failures);
        return 1;
    }
    std::printf("litehtml renderer: all tests passed\n");
    return 0;
}
