// CoreText fonts and a CoreGraphics painter for the DisplayList: the Apple
// Backend (iOS and macOS alike; CoreGraphics, CoreText and ImageIO are C APIs,
// so this is plain C++). One instance per renderer; everything on the main thread.
#pragma once

#include "backend.h"
#include "display_list.h"

#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include <map>
#include <span>
#include <string>
#include <tuple>
#include <unordered_map>

namespace twlite {

class CGBackend : public Backend {
public:
    CGBackend();
    ~CGBackend() override;

    int create_font(const std::string& families, float size_px, int weight, bool italic, const std::string& features, FontMetrics& out) override;
    void delete_font(int font) override;
    float text_width(int font, const std::string& text) override;
    bool load_image(int id, const std::string& url, float& w, float& h) override;
    void canvas_run(int id, int w, int h, const std::vector<CanvasOp>& ops) override;
    bool canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba) override;
    void canvas_release(int id) override;

    /// Paints `list` (CSS px) into `buffer` (premultiplied RGBA8, `w`×`h` physical px, scale `dpr`).
    /// With `damage` (Renderer::damage, CSS px), only those areas are cleared and painted again; the
    /// rest of `buffer` is kept.
    void paint(const DisplayList& list, uint8_t* buffer, uint32_t w, uint32_t h, double dpr, const std::vector<Rect>* damage = nullptr);

    /// Drops the rasters of keyed groups, made again on demand.
    void trim();
    size_t cached_rasters() const { return rasters_.size(); }

private:
    struct Font {
        CTFontRef font = nullptr;
        FontMetrics metrics;
    };
    /// A canvas bitmap: premultiplied RGBA8, its context y-down in canvas px.
    struct Canvas {
        CGContextRef ctx = nullptr;
        int w = 0, h = 0;
    };
    CTLineRef make_line(const Font& f, const std::string& text, CGColorRef color);
    /// make_line through a bounded cache keyed by font, colour and text; the caller releases it.
    CTLineRef line(int font, const Font& f, const std::string& text, const Color* color);
    static constexpr size_t MAX_LINES = 2048;
    static constexpr size_t MAX_GRADIENTS = 256;
    std::unordered_map<std::string, CTLineRef> lines_;
    std::unordered_map<std::string, CGGradientRef> gradients_;
    void draw(CGContextRef ctx, std::span<const Command> list);
    /// Draws a keyed group (`inner`, between its GroupPush and GroupPop) from its raster, made first
    /// when missing. False when it cannot be rastered: the transform turns it, or it is too large.
    bool draw_raster(CGContextRef ctx, const Command& push, std::span<const Command> inner);
    // `inner` (between a FilterPush and its FilterPop) drawn on a bitmap of its own and filtered there.
    void draw_filtered(CGContextRef ctx, const Command& push, std::span<const Command> inner);
    // backdrop-filter: the bitmap's pixels under the command's rect, filtered, back inside it.
    void backdrop(CGContextRef ctx, const Command& c);
    // A raster depends on the scale and the fraction of a pixel it was drawn at, besides the key.
    using RasterKey = std::tuple<uint64_t, int64_t, int64_t, int64_t, int64_t>;
    struct Raster {
        CGImageRef image = nullptr;
        uint64_t used = 0;
        int64_t pixels = 0;
    };
    std::map<RasterKey, Raster> rasters_;
    int64_t raster_pixels_ = 0;
    uint64_t raster_clock_ = 0;
    CGColorRef cg_color(const Color& c);
    CGGradientRef gradient(const std::vector<Stop>& stops);
    void draw_path(CGContextRef ctx, const Path& p);
    void draw_shadow(CGContextRef ctx, const Command& c);
    void canvas_op(Canvas& target, const CanvasOp& op);
    void canvas_paint(CGContextRef ctx, const CanvasOp& op);
    /// An image of canvas `id`'s current pixels, or of a decoded image; the caller releases it.
    CGImageRef canvas_image(int id);

    CGColorSpaceRef rgb_;
    std::unordered_map<int, Font> fonts_;
    int next_font_ = 0;
    std::unordered_map<int, CGImageRef> images_;
    std::unordered_map<int, Canvas> canvases_;
};

} // namespace twlite
