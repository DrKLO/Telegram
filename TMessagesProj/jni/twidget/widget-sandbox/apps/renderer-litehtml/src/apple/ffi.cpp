// C ABI of the litehtml renderer for the Swift glue (ios/DemoApp/Sources/NativeRenderer.swift);
// the contract is include/twidget_litehtml.h. Strings returned by `twl_*` are
// owned by the caller and released with `twl_string_free`. Exceptions never
// cross the boundary: they are logged to stderr (Xcode's console) and the
// call answers its default.

#include "../../include/twidget_litehtml.h"
#include "../renderer.h"
#include "coregraphics.h"

#include <chrono>
#include <cstdio>
#include <cstring>
#include <exception>
#include <memory>
#include <string>
#include <vector>

using twidget::Json;

struct twl_renderer {
    std::unique_ptr<twlite::CGBackend> backend;
    std::unique_ptr<twlite::Renderer> renderer;
    size_t last_display_ops = 0;
    double last_paint_ms = 0;
};

namespace {

char* ret_string(const std::string& s) {
    char* out = (char*)std::malloc(s.size() + 1);
    if (!out) return nullptr;
    std::memcpy(out, s.c_str(), s.size() + 1);
    return out;
}

std::string messages(const std::vector<Json>& out) {
    Json arr = Json::array();
    for (const Json& m : out) arr.push(m);
    return arr.dump();
}

template <typename T, typename F>
T guarded(twl_renderer* h, T def, const char* what, F f) {
    if (!h || !h->renderer) return def;
    try {
        return f(*h->renderer);
    } catch (const std::exception& e) {
        std::fprintf(stderr, "twidget-litehtml [ERROR] %s: %s\n", what, e.what());
    } catch (...) {
        std::fprintf(stderr, "twidget-litehtml [ERROR] %s: unknown exception\n", what);
    }
    return def;
}

} // namespace

extern "C" {

twl_renderer* twl_create(double width, double dpr, const char* theme) {
    try {
        auto h = std::make_unique<twl_renderer>();
        h->backend = std::make_unique<twlite::CGBackend>();
        h->renderer = std::make_unique<twlite::Renderer>(width, dpr, theme && !std::strcmp(theme, "dark"), *h->backend);
        return h.release();
    } catch (const std::exception& e) {
        std::fprintf(stderr, "twidget-litehtml [ERROR] create: %s\n", e.what());
        return nullptr;
    }
}

void twl_destroy(twl_renderer* h) {
    delete h;
}

char* twl_receive(twl_renderer* h, const char* json) {
    return ret_string(guarded<std::string>(h, "[]", "receive", [&](twlite::Renderer& r) {
        auto msg = Json::parse(json ? json : "");
        if (!msg) return std::string("[{\"type\":\"violation\",\"detail\":\"bad message\"}]");
        return messages(r.receive(*msg));
    }));
}

char* twl_set_width(twl_renderer* h, double width) {
    return ret_string(guarded<std::string>(h, "[]", "set_width", [&](twlite::Renderer& r) {
        std::vector<Json> out;
        r.set_width(width, out);
        return messages(out);
    }));
}

char* twl_set_theme(twl_renderer* h, const char* theme) {
    return ret_string(guarded<std::string>(h, "[]", "set_theme", [&](twlite::Renderer& r) {
        std::vector<Json> out;
        r.set_theme(theme && !std::strcmp(theme, "dark"), out);
        return messages(out);
    }));
}

char* twl_touch(twl_renderer* h, int32_t action, double x, double y, int32_t pointer, bool primary) {
    return ret_string(guarded<std::string>(h, "[]", "touch", [&](twlite::Renderer& r) { return messages(r.touch(action, x, y, pointer, primary)); }));
}

void twl_size(twl_renderer* h, uint32_t* width, uint32_t* height) {
    auto size = guarded<std::pair<uint32_t, uint32_t>>(h, {0, 0}, "size", [&](twlite::Renderer& r) { return std::make_pair(r.px_width(), r.px_height()); });
    if (width) *width = size.first;
    if (height) *height = size.second;
}

bool twl_dirty(twl_renderer* h) {
    return guarded<bool>(h, false, "dirty", [&](twlite::Renderer& r) { return r.dirty(); });
}

double twl_render(twl_renderer* h, uint8_t* buffer, size_t capacity) {
    if (!buffer) return -1.0;
    return guarded<double>(h, -1.0, "render", [&](twlite::Renderer& r) {
        uint32_t w = r.px_width(), ht = r.px_height();
        if (capacity < size_t(w) * ht * 4) return -2.0;
        auto t0 = std::chrono::steady_clock::now();
        twlite::DisplayList list = r.paint();
        h->backend->paint(list, buffer, w, ht, r.dpr());
        h->last_display_ops = list.size();
        h->last_paint_ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        return h->last_paint_ms;
    });
}

char* twl_image_ready(twl_renderer* h, int32_t image, float width, float height) {
    return ret_string(guarded<std::string>(h, "[]", "image_ready", [&](twlite::Renderer& r) { return messages(r.image_ready(image, width, height)); }));
}

char* twl_display_list(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "[]", "display_list", [&](twlite::Renderer& r) {
        twlite::DisplayList list = r.paint();
        h->last_display_ops = list.size();
        return twlite::to_json(list);
    }));
}

char* twl_stats(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "{}", "stats", [&](twlite::Renderer& r) {
        const auto& s = r.stats();
        Json o = Json::object();
        o.set("serializeMs", s.serialize_ms).set("parseLayoutMs", s.parse_ms).set("passes", s.passes);
        o.set("htmlBytes", double(s.html_bytes)).set("builds", s.builds).set("displayOps", double(h->last_display_ops));
        o.set("viewportHeight", r.viewport_height());
        return o.dump();
    }));
}

char* twl_html(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "", "html", [&](twlite::Renderer& r) { return r.last_html(); }));
}

void twl_string_free(char* s) {
    std::free(s);
}

} // extern "C"
