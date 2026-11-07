// CoreText fonts and a CoreGraphics painter for the DisplayList: the Apple
// Backend (iOS and macOS alike; CoreGraphics, CoreText and ImageIO are C APIs,
// so this is plain C++). One instance per renderer; everything on the main thread.
#pragma once

#include "../backend.h"
#include "../display_list.h"

#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include <string>
#include <unordered_map>

namespace twlite {

class CGBackend : public Backend {
public:
    CGBackend();
    ~CGBackend() override;

    int create_font(const std::string& families, float size_px, int weight, bool italic, FontMetrics& out) override;
    void delete_font(int font) override;
    float text_width(int font, const std::string& text) override;
    bool load_image(int id, const std::string& url, float& w, float& h) override;

    /// Paints `list` (CSS px) into `buffer` (premultiplied RGBA8, `w`×`h` physical px, scale `dpr`).
    void paint(const DisplayList& list, uint8_t* buffer, uint32_t w, uint32_t h, double dpr);

private:
    struct Font {
        CTFontRef font = nullptr;
        FontMetrics metrics;
    };
    CTLineRef make_line(const Font& f, const std::string& text, CGColorRef color);
    CGColorRef cg_color(const Color& c);

    CGColorSpaceRef rgb_;
    std::unordered_map<int, Font> fonts_;
    int next_font_ = 0;
    std::unordered_map<int, CGImageRef> images_;
};

} // namespace twlite
