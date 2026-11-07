// The renderer for the web: the same `twl_*` C ABI as include/twidget_litehtml.h,
// compiled to wasm32 by Emscripten (CMakeLists.txt, under EMSCRIPTEN), with the
// Backend calling back into JavaScript (the page registers one backend object per
// handle in `Module.twlBackends`): fonts and text widths come from a 2D canvas
// context's measureText, images from an Image element (decoded
// asynchronously, reported through twl_image_ready), canvases are bitmaps the
// TS side runs canvas ops on (the ops as JSON, canvas_ops.h), and the paint is
// the display list as JSON (twl_display_list) that the TS side draws on its
// canvas — so twl_render has nothing to paint here and answers -3. One
// renderer per handle, every call on the page's main thread.

#include "renderer.h"

#include <emscripten/emscripten.h>

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

using twidget::Json;

// ───────────── the JS side of the backend ─────────────
// `Module` is the Emscripten module object (MODULARIZE); the TS glue puts its backend registry there.

EM_JS(int, twl_js_create_font, (int backend, const char* families, float size, int weight, int italic, const char* features, float* metrics), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    if (!b) return 0;
    var r = b.createFont(UTF8ToString(families), size, weight, !!italic, UTF8ToString(features));
    if (!r) return 0;
    var o = metrics >> 2;
    HEAPF32[o] = r.ascent; HEAPF32[o + 1] = r.descent; HEAPF32[o + 2] = r.height; HEAPF32[o + 3] = r.xHeight; HEAPF32[o + 4] = r.zeroWidth;
    return r.id;
});

EM_JS(void, twl_js_delete_font, (int backend, int font), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    if (b) b.deleteFont(font);
});

EM_JS(float, twl_js_text_width, (int backend, int font, const char* text), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    return b ? b.textWidth(font, UTF8ToString(text)) : 0;
});

EM_JS(int, twl_js_load_image, (int backend, int id, const char* url, float* size), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    if (!b) return 0;
    var r = b.loadImage(id, UTF8ToString(url));
    if (!r) return 0;
    HEAPF32[size >> 2] = r.width; HEAPF32[(size >> 2) + 1] = r.height;
    return 1;
});

EM_JS(void, twl_js_canvas_run, (int backend, int id, int w, int h, const char* ops), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    if (b && b.canvasRun) b.canvasRun(id, w, h, UTF8ToString(ops));
});

/// Straight RGBA of a canvas region into `out` (w * h * 4 bytes); 0 when the backend has no canvas.
EM_JS(int, twl_js_canvas_read, (int backend, int id, int x, int y, int w, int h, uint8_t* out), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    if (!b || !b.canvasRead) return 0;
    var pixels = b.canvasRead(id, x, y, w, h);
    if (!pixels || pixels.length !== w * h * 4) return 0;
    HEAPU8.set(pixels, out);
    return 1;
});

EM_JS(void, twl_js_canvas_release, (int backend, int id), {
    var b = Module.twlBackends && Module.twlBackends.get(backend);
    if (b && b.canvasRelease) b.canvasRelease(id);
});

namespace {

class JsBackend : public twlite::Backend {
public:
    explicit JsBackend(int id) : id_(id) {}
    int create_font(const std::string& families, float size, int weight, bool italic, const std::string& features, twlite::FontMetrics& out) override {
        float m[5] = {0, 0, 0, 0, 0};
        int font = twl_js_create_font(id_, families.c_str(), size, weight, italic ? 1 : 0, features.c_str(), m);
        out.ascent = m[0];
        out.descent = m[1];
        out.height = m[2];
        out.x_height = m[3];
        out.zero_width = m[4];
        return font;
    }
    void delete_font(int font) override { twl_js_delete_font(id_, font); }
    float text_width(int font, const std::string& text) override { return twl_js_text_width(id_, font, text.c_str()); }
    bool load_image(int id, const std::string& url, float& w, float& h) override {
        float size[2] = {0, 0};
        // The image may still be decoding: the size arrives through twl_image_ready; 0×0 until then keeps the id.
        bool known = twl_js_load_image(id_, id, url.c_str(), size) != 0;
        w = size[0];
        h = size[1];
        return known;
    }
    void canvas_run(int id, int w, int h, const std::vector<twlite::CanvasOp>& ops) override {
        twl_js_canvas_run(id_, id, w, h, twlite::to_json(ops).c_str());
    }
    bool canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba) override {
        rgba.assign(size_t(std::max(w, 0)) * std::max(h, 0) * 4, 0);
        return twl_js_canvas_read(id_, id, x, y, w, h, rgba.data()) != 0;
    }
    void canvas_release(int id) override { twl_js_canvas_release(id_, id); }

private:
    int id_;
};

struct Handle {
    int id;
    std::unique_ptr<JsBackend> backend;
    std::unique_ptr<twlite::Renderer> renderer;
    size_t last_display_ops = 0;
};

int g_next_id = 0;

char* ret_string(const std::string& s) {
    char* out = (char*)std::malloc(s.size() + 1);
    if (out) std::memcpy(out, s.c_str(), s.size() + 1);
    return out;
}

std::string messages(const std::vector<Json>& out) {
    Json arr = Json::array();
    for (const Json& m : out) arr.push(m);
    return arr.dump();
}

} // namespace

extern "C" {

/// The backend id the TS side registers its object under (`Module.twlBackends.set(id, backend)`) before any
/// message: handles are small integers starting at 1, the same number twl_backend_id returns.
EMSCRIPTEN_KEEPALIVE Handle* twl_create(double width, double dpr, const char* theme) {
    auto h = new Handle();
    h->id = ++g_next_id;
    h->backend = std::make_unique<JsBackend>(h->id);
    h->renderer = std::make_unique<twlite::Renderer>(width, dpr, theme && !std::strcmp(theme, "dark"), *h->backend);
    return h;
}

/// Creates the handle without building the first (empty) document? No: the empty document needs no fonts,
/// so twl_create is safe before the backend is registered; the first `ops` batch needs it.
EMSCRIPTEN_KEEPALIVE int twl_backend_id(Handle* h) {
    return h ? h->id : 0;
}

EMSCRIPTEN_KEEPALIVE void twl_destroy(Handle* h) {
    delete h;
}

EMSCRIPTEN_KEEPALIVE char* twl_receive(Handle* h, const char* json) {
    if (!h) return ret_string("[]");
    auto msg = Json::parse(json ? json : "");
    if (!msg) return ret_string("[{\"type\":\"violation\",\"detail\":\"bad message\"}]");
    return ret_string(messages(h->renderer->receive(*msg)));
}

EMSCRIPTEN_KEEPALIVE char* twl_set_width(Handle* h, double width) {
    if (!h) return ret_string("[]");
    std::vector<Json> out;
    h->renderer->set_width(width, out);
    return ret_string(messages(out));
}

EMSCRIPTEN_KEEPALIVE char* twl_set_max_height(Handle* h, double height) {
    if (!h) return ret_string("[]");
    std::vector<Json> out;
    h->renderer->set_max_height(height, out);
    return ret_string(messages(out));
}

EMSCRIPTEN_KEEPALIVE char* twl_set_theme(Handle* h, const char* theme) {
    if (!h) return ret_string("[]");
    std::vector<Json> out;
    h->renderer->set_theme(theme && !std::strcmp(theme, "dark"), out);
    return ret_string(messages(out));
}

EMSCRIPTEN_KEEPALIVE char* twl_touch(Handle* h, int action, double x, double y, int pointer, int primary) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->touch(action, x, y, pointer, primary != 0)));
}

EMSCRIPTEN_KEEPALIVE char* twl_pointer(Handle* h, int action, double x, double y, int pointer, int primary, int mouse, int modifiers, int button) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->touch(action, x, y, pointer, primary != 0, mouse != 0, modifiers, button)));
}

EMSCRIPTEN_KEEPALIVE char* twl_hover(Handle* h, int action, double x, double y, int modifiers) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->hover(action, x, y, modifiers)));
}

EMSCRIPTEN_KEEPALIVE char* twl_wheel(Handle* h, double x, double y, double dx, double dy, int mode, int modifiers) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->wheel(x, y, dx, dy, mode, modifiers)));
}

EMSCRIPTEN_KEEPALIVE char* twl_key(Handle* h, const char* type, const char* key, const char* code, int repeat, int modifiers) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->key(type ? type : "", key ? key : "", code ? code : "", repeat != 0, modifiers)));
}

/// The text control being edited (Renderer::EditorSpec as JSON), or null: the page covers it with an
/// <input> or <textarea> of its own.
EMSCRIPTEN_KEEPALIVE char* twl_editor(Handle* h) {
    if (!h) return ret_string("null");
    return ret_string(h->renderer->editor_json());
}

EMSCRIPTEN_KEEPALIVE char* twl_edit(Handle* h, double generation, const char* text, const char* input_type) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->edit(uint64_t(generation), text ? text : "", input_type && *input_type ? input_type : "insertText")));
}

EMSCRIPTEN_KEEPALIVE char* twl_end_editing(Handle* h) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->end_editing()));
}

/// The text a mouse drag selected, "" without a selection: what the page's copy takes.
EMSCRIPTEN_KEEPALIVE char* twl_selected_text(Handle* h) {
    return ret_string(h ? h->renderer->selected_text() : std::string());
}

/// Drops the selection; 1 when there was one.
EMSCRIPTEN_KEEPALIVE int twl_clear_selection(Handle* h) {
    return h && h->renderer->clear_selection() ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE char* twl_selectable_text(Handle* h) {
    return ret_string(h ? h->renderer->selectable_text_json() : std::string("[]"));
}

EMSCRIPTEN_KEEPALIVE char* twl_cursor(Handle* h) {
    return ret_string(h ? h->renderer->cursor() : std::string("auto"));
}

EMSCRIPTEN_KEEPALIVE char* twl_master_css(Handle* h) {
    return ret_string(h ? h->renderer->master_css() : std::string());
}

EMSCRIPTEN_KEEPALIVE char* twl_theme_variables(Handle* h) {
    return ret_string(h ? h->renderer->theme_variables_json() : std::string("{}"));
}

EMSCRIPTEN_KEEPALIVE void twl_set_resize_tracking(Handle* h, int enabled, int prefers_bottom) {
    if (h) h->renderer->set_resize_tracking(enabled != 0, prefers_bottom != 0);
}

/// One animation frame (requestAnimationFrame): animations advance. Call it every frame while twl_wants_tick
/// holds, otherwise after twl_next_tick_in seconds.
EMSCRIPTEN_KEEPALIVE char* twl_tick(Handle* h) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->tick()));
}

EMSCRIPTEN_KEEPALIVE int twl_wants_tick(Handle* h) {
    return h && h->renderer->wants_tick() ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE double twl_next_tick_in(Handle* h) {
    return h ? h->renderer->next_tick_in() : INFINITY;
}

EMSCRIPTEN_KEEPALIVE void twl_set_compositing(Handle* h, int enabled) {
    if (h) h->renderer->set_compositing(enabled != 0);
}

/// The layers above the last twl_display_list (src/compositor.h, to_json(Composition)).
EMSCRIPTEN_KEEPALIVE char* twl_composition(Handle* h) {
    if (!h) return ret_string("{\"v\":0,\"l\":[]}");
    return ret_string(twlite::to_json(h->renderer->composition()));
}

/// The renderer clock, in seconds: what the layers' track starts are on.
EMSCRIPTEN_KEEPALIVE double twl_clock() {
    return twlite::Renderer::clock();
}

/// Layer `index` of the last composition as a display list; NULL past the end.
EMSCRIPTEN_KEEPALIVE char* twl_layer_content(Handle* h, uint32_t index) {
    if (!h || index >= h->renderer->composition().layers.size()) return nullptr;
    return ret_string(twlite::to_json(h->renderer->composition().layers[index].content));
}

EMSCRIPTEN_KEEPALIVE char* twl_image_ready(Handle* h, int image, float width, float height) {
    if (!h) return ret_string("[]");
    return ret_string(messages(h->renderer->image_ready(image, width, height)));
}

/// CSS px: width and the viewport height the document converged to.
EMSCRIPTEN_KEEPALIVE void twl_size(Handle* h, uint32_t* width, uint32_t* height) {
    if (width) *width = h ? uint32_t(h->renderer->width()) : 0;
    if (height) *height = h ? uint32_t(h->renderer->viewport_height()) : 0;
}

EMSCRIPTEN_KEEPALIVE int twl_dirty(Handle* h) {
    return h && h->renderer->dirty() ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE char* twl_display_list(Handle* h) {
    if (!h) return ret_string("[]");
    twlite::DisplayList list = h->renderer->paint();
    h->last_display_ops = list.size();
    return ret_string(twlite::to_json(list));
}

EMSCRIPTEN_KEEPALIVE char* twl_damage(Handle* h) {
    if (!h) return ret_string("null");
    return ret_string(h->renderer->damage_json());
}

EMSCRIPTEN_KEEPALIVE void twl_trim(Handle* h) {
    if (h) h->renderer->trim();
}

EMSCRIPTEN_KEEPALIVE char* twl_stats(Handle* h) {
    if (!h) return ret_string("{}");
    const auto& s = h->renderer->stats();
    Json o = Json::object();
    o.set("serializeMs", s.serialize_ms).set("parseLayoutMs", s.parse_ms).set("passes", s.passes);
    o.set("htmlBytes", double(s.html_bytes)).set("builds", s.builds).set("displayOps", double(h->last_display_ops));
    o.set("viewportHeight", h->renderer->viewport_height());
    return ret_string(o.dump());
}

EMSCRIPTEN_KEEPALIVE char* twl_html(Handle* h) {
    return ret_string(h ? h->renderer->last_html() : "");
}

EMSCRIPTEN_KEEPALIVE void twl_string_free(char* s) {
    std::free(s);
}

EMSCRIPTEN_KEEPALIVE char* twl_alloc(size_t len) {
    return (char*)std::malloc(len);
}

EMSCRIPTEN_KEEPALIVE void twl_free(char* p) {
    std::free(p);
}

} // extern "C"
