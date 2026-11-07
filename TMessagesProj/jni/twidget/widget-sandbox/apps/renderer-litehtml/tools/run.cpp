// Host harness (macOS): feeds a JSON array of host → renderer messages (as
// `pnpm tsx scripts/sandbox-node.ts <widget.html> --ops-file msgs.json`
// writes it) to the litehtml renderer, prints what it answers, optionally taps
// it, and writes the paint as PNG. Usage:
//   twidget-litehtml-run msgs.json out.png [--width 360] [--dpr 2] [--dark] [--tap x,y]... [--html out.html] [--quiet]
#include "../include/twidget_litehtml.h"

#include <CoreGraphics/CoreGraphics.h>
#include <ImageIO/ImageIO.h>

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

static bool write_png(const char* path, const std::vector<uint8_t>& rgba, uint32_t w, uint32_t h) {
    CGColorSpaceRef rgb = CGColorSpaceCreateDeviceRGB();
    CGDataProviderRef provider = CGDataProviderCreateWithData(nullptr, rgba.data(), rgba.size(), nullptr);
    CGImageRef image = CGImageCreate(w, h, 8, 32, size_t(w) * 4, rgb, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big, provider, nullptr, false, kCGRenderingIntentDefault);
    CFStringRef cfpath = CFStringCreateWithCString(kCFAllocatorDefault, path, kCFStringEncodingUTF8);
    CFURLRef url = CFURLCreateWithFileSystemPath(kCFAllocatorDefault, cfpath, kCFURLPOSIXPathStyle, false);
    CGImageDestinationRef dest = CGImageDestinationCreateWithURL(url, CFSTR("public.png"), 1, nullptr);
    bool ok = false;
    if (dest && image) {
        CGImageDestinationAddImage(dest, image, nullptr);
        ok = CGImageDestinationFinalize(dest);
    }
    if (dest) CFRelease(dest);
    CFRelease(url);
    CFRelease(cfpath);
    if (image) CGImageRelease(image);
    CGDataProviderRelease(provider);
    CGColorSpaceRelease(rgb);
    return ok;
}

/// Splits a top-level JSON array of objects into its elements (the messages file is trusted, produced by our own tooling).
static std::vector<std::string> split_messages(const std::string& text) {
    std::vector<std::string> out;
    int depth = 0;
    bool in_string = false, escape = false;
    size_t start = 0;
    for (size_t i = 0; i < text.size(); i++) {
        char c = text[i];
        if (in_string) {
            if (escape) escape = false;
            else if (c == '\\') escape = true;
            else if (c == '"') in_string = false;
            continue;
        }
        if (c == '"') in_string = true;
        else if (c == '{' || c == '[') {
            if (depth == 1 && c == '{') start = i;
            depth++;
        } else if (c == '}' || c == ']') {
            depth--;
            if (depth == 1 && c == '}') out.push_back(text.substr(start, i - start + 1));
        }
    }
    return out;
}

int main(int argc, char** argv) {
    if (argc < 3) {
        std::fprintf(stderr, "usage: %s msgs.json out.png [--width 360] [--dpr 1] [--dark] [--tap x,y]... [--html out.html] [--quiet]\n", argv[0]);
        return 2;
    }
    double width = 360, dpr = 1;
    bool dark = false, quiet = false;
    const char* html_out = nullptr;
    std::vector<std::pair<double, double>> taps;
    for (int i = 3; i < argc; i++) {
        std::string a = argv[i];
        if (a == "--width" && i + 1 < argc) width = std::atof(argv[++i]);
        else if (a == "--dpr" && i + 1 < argc) dpr = std::atof(argv[++i]);
        else if (a == "--dark") dark = true;
        else if (a == "--quiet") quiet = true;
        else if (a == "--html" && i + 1 < argc) html_out = argv[++i];
        else if (a == "--tap" && i + 1 < argc) {
            double x = 0, y = 0;
            if (std::sscanf(argv[++i], "%lf,%lf", &x, &y) == 2) taps.emplace_back(x, y);
        }
    }
    std::stringstream ss;
    std::ifstream in(argv[1], std::ios::binary);
    if (!in) { std::fprintf(stderr, "cannot read %s\n", argv[1]); return 1; }
    ss << in.rdbuf();
    std::vector<std::string> msgs = split_messages(ss.str());

    using clk = std::chrono::steady_clock;
    auto ms = [](clk::time_point a, clk::time_point b) { return std::chrono::duration<double, std::milli>(b - a).count(); };
    auto t0 = clk::now();
    twl_renderer* r = twl_create(width, dpr, dark ? "dark" : "light");
    if (!r) { std::fprintf(stderr, "twl_create failed\n"); return 1; }
    auto t1 = clk::now();
    auto say = [&](const char* label, char* json) {
        if (json && !quiet && std::strcmp(json, "[]")) std::printf("%s → %s\n", label, json);
        twl_string_free(json);
    };
    for (const std::string& m : msgs) {
        auto a = clk::now();
        char* out = twl_receive(r, m.c_str());
        auto b = clk::now();
        if (!quiet) std::printf("message %.60s… in %.1f ms\n", m.c_str(), ms(a, b));
        say("  answer", out);
    }
    for (auto [x, y] : taps) {
        say("tap down", twl_touch(r, 0, x, y, 0, true));
        say("tap up", twl_touch(r, 2, x, y, 0, true));
    }
    uint32_t w = 0, h = 0;
    twl_size(r, &w, &h);
    std::vector<uint8_t> pixels(size_t(w) * h * 4);
    double paint_ms = twl_render(r, pixels.data(), pixels.size());
    char* stats = twl_stats(r);
    std::printf("{\"create_ms\":%.2f,\"paint_ms\":%.2f,\"width_px\":%u,\"height_px\":%u,\"messages\":%zu,\"stats\":%s}\n", ms(t0, t1), paint_ms, w, h, msgs.size(), stats ? stats : "null");
    twl_string_free(stats);
    if (html_out) {
        char* html = twl_html(r);
        std::ofstream(html_out, std::ios::binary) << (html ? html : "");
        twl_string_free(html);
    }
    bool ok = paint_ms >= 0 && write_png(argv[2], pixels, w, h);
    twl_destroy(r);
    if (!ok) { std::fprintf(stderr, "paint or PNG failed\n"); return 1; }
    return 0;
}
