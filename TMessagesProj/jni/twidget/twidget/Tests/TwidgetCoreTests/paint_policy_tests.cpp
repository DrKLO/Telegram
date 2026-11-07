// widget-sandbox's packages/core/tests/fixtures/paint-policy.json against paint_policy.h: the rule every renderer
// that counts heavy boxes implements, case by case as the framework's renderer runs it.
#include "paint_policy.h"
#include "twidget/json.h"

#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>

using twidget::Json;
using namespace twlite::paint;

namespace {

int checks = 0, failures = 0;

void check(bool ok, const std::string& what) {
    checks++;
    if (!ok) {
        std::printf("FAIL %s\n", what.c_str());
        failures++;
    }
}

std::string text(const Json& v) {
    if (v.isString()) return v.asString();
    if (v.isNumber()) return Json::number(v.asNumber());
    return "";
}

std::string describe(const std::optional<BoxPaint>& p) {
    return p ? std::string("{heavy ") + (p->heavy ? "true" : "false") + ", backdrop " + (p->backdrop ? "true" : "false") + "}" : "null";
}

std::optional<BoxPaint> expected(const Json& v) {
    if (v.isNull()) return std::nullopt;
    return BoxPaint{v["heavy"].asBool(), v["backdrop"].asBool()};
}

/// Holds the strings a Style views.
struct OwnedStyle {
    std::string display, content, box_shadow, filter, backdrop_filter, opacity;
    Style::Button button = Style::Button::None;
    explicit OwnedStyle(const Json& s)
        : display(text(s["display"])), content(text(s["content"])), box_shadow(text(s["boxShadow"])), filter(text(s["filter"])),
          backdrop_filter(text(s["backdropFilter"])), opacity(text(s["opacity"])) {
        const std::string& b = s["button"].asString();
        button = b == "disabled" ? Style::Button::Disabled : b == "pressed" ? Style::Button::Pressed : Style::Button::None;
    }
    Style view() const { return Style{display, content, box_shadow, filter, backdrop_filter, opacity, button}; }
};

std::optional<BoxPaint> animation(const Json& keyframes) {
    AnimationPaint paint;
    for (const Json& k : keyframes.arr()) {
        const Json& computed = k["computedOffset"];
        const Json& offset = k["offset"];
        paint.keyframe(computed.isNumber() ? std::optional<double>(computed.asNumber())
                       : offset.isNumber() ? std::optional<double>(offset.asNumber())
                                           : std::nullopt);
        for (const auto& [name, value] : k.obj())
            paint.property(name, value.isString() ? std::optional<std::string_view>(value.asString()) : std::nullopt);
    }
    return paint.result();
}

std::vector<std::string> keys(const Json& v) {
    if (v.isString()) return {v.asString()};
    std::vector<std::string> out;
    for (const Json& k : v.arr()) out.push_back(k.asString());
    return out;
}

} // namespace

int main(int argc, char** argv) {
    const char* path = argc > 1 ? argv[1] : TW_PAINT_POLICY_FIXTURE;
    std::ifstream in(path, std::ios::binary);
    std::stringstream buffer;
    buffer << in.rdbuf();
    auto fixture = Json::parse(buffer.str());
    if (!fixture) {
        std::printf("cannot read %s\n", path);
        return 2;
    }
    const Json& f = *fixture;

    check(f["constants"]["PAINT_HEAVY_BOXES"].asInt() == twidget::kMaxHeavyBoxes, "constants: heavy boxes");
    check(f["constants"]["PAINT_BACKDROP_BOXES"].asInt() == twidget::kMaxBackdropBoxes, "constants: backdrop boxes");

    for (const Json& s : f["shadows"].arr()) {
        const std::string& value = s["value"].asString();
        check(box_shadow_blurred(value) == s["blurred"].asBool(), "shadows: blurred '" + value + "'");
        std::string without = box_shadow_without_blur(value);
        check(without == s["withoutBlur"].asString(), "shadows: without blur '" + value + "' (got '" + without + "')");
    }

    for (const Json& b : f["boxes"].arr()) {
        OwnedStyle style(b["style"]);
        auto got = box_paint(style.view(), b["pseudo"].asBool());
        auto want = expected(b["expect"]);
        check(got == want, "boxes: " + b["name"].asString() + " (got " + describe(got) + ")");
    }

    for (const Json& h : f["hidesSubtree"].arr())
        check(hides_subtree(h["display"].asString()) == h["expect"].asBool(), "hidesSubtree: '" + h["display"].asString() + "'");

    for (const Json& a : f["animations"].arr()) {
        auto got = animation(a["keyframes"]);
        check(got == expected(a["expect"]), "animations: " + a["name"].asString() + " (got " + describe(got) + ")");
    }

    for (const Json& m : f["messages"].arr()) {
        auto code = twidget::paintLimitFromCode(m["code"].asString());
        check(code && twidget::paintLimitMessage(*code, m["count"].asInt()) == m["expect"].asString(), "messages: " + m["code"].asString());
    }

    for (const Json& c : f["cases"].arr()) {
        PaintCounter<std::string> counter;
        const std::string& name = c["name"].asString();
        int step = 0;
        for (const Json& s : c["steps"].arr()) {
            step++;
            if (s.find("set")) {
                OwnedStyle style(s["style"]);
                auto paint = box_paint(style.view(), s["pseudo"].asBool());
                for (const auto& key : keys(s["set"])) counter.set_box(key, paint);
            } else if (s.find("remove")) {
                for (const auto& key : keys(s["remove"])) counter.remove_box(key);
            } else if (s.find("start")) {
                auto paint = animation(s["keyframes"]);
                if (paint)
                    for (const auto& key : keys(s["start"])) counter.start_animation(key, s["animation"].asString(), *paint);
            } else if (s.find("end")) {
                for (const auto& key : keys(s["end"])) counter.end_animation(key, s["animation"].asString());
            } else if (s.find("check")) {
                const Json& want = s["check"];
                auto verdict = counter.check();
                std::optional<Verdict> stop;
                if (!want["stop"].isNull())
                    stop = Verdict{*twidget::paintLimitFromCode(want["stop"]["code"].asString()), want["stop"]["count"].asInt()};
                std::string at = "cases: " + name + ", step " + std::to_string(step);
                check(counter.heavy() == want["heavy"].asInt(), at + ": heavy " + std::to_string(counter.heavy()));
                check(counter.backdrop() == want["backdrop"].asInt(), at + ": backdrop " + std::to_string(counter.backdrop()));
                check(verdict == stop, at + ": verdict" + (verdict ? " " + std::to_string(verdict->count) : std::string(" none")));
            }
        }
    }

    std::printf("%d checks, %d failure(s)\n", checks, failures);
    return failures ? 1 : 0;
}
