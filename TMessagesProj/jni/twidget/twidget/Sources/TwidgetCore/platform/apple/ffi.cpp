// C ABI of the renderer for Apple hosts; the contract is include/twidget_litehtml.h. Strings returned by `twl_*` are
// owned by the caller and released with `twl_string_free`. Exceptions never
// cross the boundary: they are logged to stderr (Xcode's console) and the
// call answers its default.

#include "coregraphics.h"
#include "renderer.h"
#include "twidget_litehtml.h"

#include <chrono>
#include <cmath>
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

char* twl_set_max_height(twl_renderer* h, double height) {
    return ret_string(guarded<std::string>(h, "[]", "set_max_height", [&](twlite::Renderer& r) {
        std::vector<Json> out;
        r.set_max_height(height, out);
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

char* twl_pointer(twl_renderer* h, int32_t action, double x, double y, int32_t pointer, bool primary, bool mouse, int32_t modifiers, int32_t button) {
    return ret_string(guarded<std::string>(h, "[]", "pointer", [&](twlite::Renderer& r) { return messages(r.touch(action, x, y, pointer, primary, mouse, modifiers, button)); }));
}

char* twl_hover(twl_renderer* h, int32_t action, double x, double y, int32_t modifiers) {
    return ret_string(guarded<std::string>(h, "[]", "hover", [&](twlite::Renderer& r) { return messages(r.hover(action, x, y, modifiers)); }));
}

char* twl_wheel(twl_renderer* h, double x, double y, double dx, double dy, int32_t mode, int32_t modifiers) {
    return ret_string(guarded<std::string>(h, "[]", "wheel", [&](twlite::Renderer& r) { return messages(r.wheel(x, y, dx, dy, mode, modifiers)); }));
}

char* twl_key(twl_renderer* h, const char* type, const char* key, const char* code, bool repeat, int32_t modifiers) {
    return ret_string(guarded<std::string>(h, "[]", "key", [&](twlite::Renderer& r) {
        return messages(r.key(type ? type : "", key ? key : "", code ? code : "", repeat, modifiers));
    }));
}

char* twl_editor(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "null", "editor", [&](twlite::Renderer& r) { return r.editor_json(); }));
}

char* twl_edit(twl_renderer* h, uint64_t generation, const char* text, const char* input_type) {
    return ret_string(guarded<std::string>(h, "[]", "edit", [&](twlite::Renderer& r) {
        return messages(r.edit(generation, text ? text : "", input_type && *input_type ? input_type : "insertText"));
    }));
}

char* twl_end_editing(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "[]", "end_editing", [&](twlite::Renderer& r) { return messages(r.end_editing()); }));
}

char* twl_selected_text(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "", "selected_text", [&](twlite::Renderer& r) { return r.selected_text(); }));
}

bool twl_clear_selection(twl_renderer* h) {
    return guarded<bool>(h, false, "clear_selection", [&](twlite::Renderer& r) { return r.clear_selection(); });
}

char* twl_selectable_text(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "[]", "selectable_text", [&](twlite::Renderer& r) { return r.selectable_text_json(); }));
}

char* twl_cursor(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "auto", "cursor", [&](twlite::Renderer& r) { return r.cursor(); }));
}

char* twl_master_css(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "", "master_css", [&](twlite::Renderer& r) { return r.master_css(); }));
}

char* twl_theme_variables(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "{}", "theme_variables", [&](twlite::Renderer& r) { return r.theme_variables_json(); }));
}

void twl_set_resize_tracking(twl_renderer* h, bool enabled, bool prefers_bottom) {
    guarded<bool>(h, false, "set_resize_tracking", [&](twlite::Renderer& r) {
        r.set_resize_tracking(enabled, prefers_bottom);
        return true;
    });
}

char* twl_tick(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "[]", "tick", [&](twlite::Renderer& r) { return messages(r.tick()); }));
}

bool twl_wants_tick(twl_renderer* h) {
    return guarded<bool>(h, false, "wants_tick", [&](twlite::Renderer& r) { return r.wants_tick(); });
}

double twl_next_tick_in(twl_renderer* h) {
    return guarded<double>(h, INFINITY, "next_tick_in", [&](twlite::Renderer& r) { return r.next_tick_in(); });
}

void twl_set_compositing(twl_renderer* h, bool enabled) {
    guarded<bool>(h, false, "set_compositing", [&](twlite::Renderer& r) {
        r.set_compositing(enabled);
        return true;
    });
}

char* twl_composition(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "{\"v\":0,\"l\":[]}", "composition", [&](twlite::Renderer& r) { return twlite::to_json(r.composition()); }));
}

double twl_clock(void) {
    return twlite::Renderer::clock();
}

char* twl_layer_content(twl_renderer* h, uint32_t index) {
    return guarded<char*>(h, nullptr, "layer_content", [&](twlite::Renderer& r) -> char* {
        const auto& layers = r.composition().layers;
        return index < layers.size() ? ret_string(twlite::to_json(layers[index].content)) : nullptr;
    });
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

char* twl_damage(twl_renderer* h) {
    return ret_string(guarded<std::string>(h, "null", "damage", [&](twlite::Renderer& r) { return r.damage_json(); }));
}

void twl_trim(twl_renderer* h) {
    guarded<int>(h, 0, "trim", [&](twlite::Renderer& r) {
        r.trim();
        return 0;
    });
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
