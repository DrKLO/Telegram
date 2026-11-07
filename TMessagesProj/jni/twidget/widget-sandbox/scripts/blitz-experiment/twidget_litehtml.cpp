// twidget experiment: render a static HTML file through litehtml with the
// repository's cairo+pango container (no GTK), at a given CSS width, and write
// a PNG. Prints stage timings as JSON. Usage: twidget_litehtml <in.html> <width> <out.png>
// Build (litehtml checkout at $LH, built with cmake; pango/cairo from Homebrew):
//   c++ -std=c++20 -O2 -o twidget_litehtml twidget_litehtml.cpp $LH/containers/cairo/{container_cairo,container_cairo_pango,cairo_borders,conic_gradient}.cpp \
//     -I$LH/include -I$LH/include/litehtml -I$LH/containers/cairo $(pkg-config --cflags --libs pangocairo cairo cairo-png fontconfig) \
//     $LH/build/liblitehtml.a $LH/build/src/gumbo/libgumbo.a
// Run with FONTCONFIG_FILE=scripts/blitz-experiment/fonts.conf so -apple-system resolves to SF Pro like in Chromium.
#include <litehtml.h>
#include "container_cairo_pango.h"
#include "cairo_images_cache.h"
#include <cairo.h>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

static std::vector<unsigned char> b64decode(const std::string& in) {
    static int T[256]; static bool init = false;
    if (!init) { for (int i = 0; i < 256; i++) T[i] = -1; const char* a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"; for (int i = 0; i < 64; i++) T[(unsigned char)a[i]] = i; init = true; }
    std::vector<unsigned char> out; int val = 0, bits = -8;
    for (unsigned char c : in) { if (T[c] == -1) continue; val = (val << 6) + T[c]; bits += 6; if (bits >= 0) { out.push_back((unsigned char)((val >> bits) & 0xFF)); bits -= 8; } }
    return out;
}
struct mem_reader { const std::vector<unsigned char>* data; size_t pos; };
static cairo_status_t read_mem(void* closure, unsigned char* out, unsigned int len) {
    auto* r = (mem_reader*)closure; if (r->pos + len > r->data->size()) return CAIRO_STATUS_READ_ERROR;
    memcpy(out, r->data->data() + r->pos, len); r->pos += len; return CAIRO_STATUS_SUCCESS;
}

class container : public container_cairo_pango {
    int m_w, m_h; cairo_images_cache m_images;
public:
    container(int w, int h) : m_w(w), m_h(h) {}
    void load_image(const char*, const char*, bool) override {}
    void set_caption(const char*) override {}
    void set_base_url(const char*) override {}
    void on_anchor_click(const char*, const litehtml::element::ptr&) override {}
    void on_mouse_event(const litehtml::element::ptr&, litehtml::mouse_event) override {}
    void set_cursor(const char*) override {}
    void import_css(std::string& text, const std::string&, std::string&) override { text.clear(); }
    double get_screen_dpi() const override { return 96; }
    int get_screen_width() const override { return m_w; }
    int get_screen_height() const override { return m_h; }
    void get_viewport(litehtml::position& v) const override { v.x = 0; v.y = 0; v.width = m_w; v.height = m_h; }
    const char* get_default_font_name() const override { return "-apple-system"; }
    void make_url(const char* url, const char* basepath, std::string& out) override { out = url; (void)basepath; }
    cairo_surface_t* get_image(const std::string& url) override {
        if (url.rfind("data:image/png;base64,", 0) != 0) return nullptr;
        cairo_surface_t* s = m_images.get_image(url);
        if (!s) {
            auto bytes = b64decode(url.substr(22));
            mem_reader r{ &bytes, 0 };
            s = cairo_image_surface_create_from_png_stream(read_mem, &r);
            if (cairo_surface_status(s) != CAIRO_STATUS_SUCCESS) { cairo_surface_destroy(s); return nullptr; }
            m_images.add_image(url, s);
            s = cairo_surface_reference(s);
        }
        return s;
    }
};

int main(int argc, char** argv) {
    if (argc < 4) { fprintf(stderr, "usage: %s in.html width out.png\n", argv[0]); return 2; }
    int width = atoi(argv[2]);
    std::stringstream ss; std::ifstream(argv[1], std::ios::binary) >> ss.rdbuf();
    std::string html = ss.str();
    using clk = std::chrono::steady_clock;
    auto ms = [](clk::time_point a, clk::time_point b) { return std::chrono::duration<double, std::milli>(b - a).count(); };

    // Pass 1 with a 24 px viewport (so 100vh does not inflate the page), pass 2
    // with the viewport at the resulting height, like the dev UI's iframe.
    int vp_h = 24; litehtml::document::ptr doc; container* cont = nullptr;
    double t_parse = 0, t_layout = 0; int height = 0; int passes = 0;
    for (;;) {
        doc = nullptr; delete cont; cont = new container(width, vp_h);
        auto t0 = clk::now();
        doc = litehtml::document::createFromString(html, cont);
        auto t1 = clk::now();
        doc->render(width);
        auto t2 = clk::now();
        t_parse = ms(t0, t1); t_layout = ms(t1, t2);
        height = (int)std::ceil((double)doc->height());
        height = std::max(24, std::min(900, height));
        passes++;
        if (height == vp_h || passes >= 6) break;
        vp_h = height;
    }
    auto t3 = clk::now();
    cairo_surface_t* surface = cairo_image_surface_create(CAIRO_FORMAT_ARGB32, width, height);
    cairo_t* cr = cairo_create(surface);
    cairo_set_source_rgb(cr, 1, 1, 1); cairo_paint(cr);
    litehtml::position clip(0, 0, width, height);
    doc->draw((litehtml::uint_ptr)cr, 0, 0, &clip);
    cairo_surface_flush(surface);
    auto t4 = clk::now();
    cairo_surface_write_to_png(surface, argv[3]);
    printf("{\"parse_ms\":%.1f,\"layout_ms\":%.1f,\"paint_ms\":%.1f,\"passes\":%d,\"width\":%d,\"height\":%d,\"doc_height\":%.0f}\n",
           t_parse, t_layout, ms(t3, t4), passes, width, height, (double)doc->height());
    cairo_destroy(cr); cairo_surface_destroy(surface);
    doc = nullptr; delete cont;
    return 0;
}
