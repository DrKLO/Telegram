#pragma once

#ifdef __cplusplus
#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include <cstdint>
#include <functional>
#include <vector>

namespace lhx
{
    struct RenderOp
    {
        enum Kind : uint8_t
        {
            Bitmap,
            Fill,
            Mask,
        };
        Kind     kind;
        CGRect   device;
        uint64_t key;
        uint8_t  color[4];
    };

    struct AtlasSlot
    {
        CGRect   device;
        uint16_t u, v;
        bool     empty;
    };

    struct RenderRecorder
    {
        CGFloat               scale = 2;
        CGColorSpaceRef       space = nullptr;
        std::vector<RenderOp> ops;
        bool                  unsupported = false;

        std::function<bool(uint64_t key)>                                  cached;
        std::function<CGContextRef(uint64_t key, CGRect device)>           allocate;
        std::function<void(uint64_t key, CGContextRef ctx, CGRect device)> store;
        std::function<bool(CTFontRef font, CGGlyph glyph, CGPoint device, AtlasSlot& slot)> glyph;
        std::function<bool(uint64_t key, CGRect box, const std::function<void(CGContextRef ctx)>& draw, AtlasSlot& slot)> mask;
    };

    struct AlphaBitmap
    {
        int                  x = 0, y = 0, width = 0, height = 0;
        std::vector<uint8_t> pixels;
        bool                 empty = true;
    };

    bool rasterizeGlyph(CTFontRef font, CGGlyph glyph, CGPoint device, CGFloat scale, int maxSize, AlphaBitmap& out);
    bool rasterizeMask(CGRect box, CGFloat scale, int maxSize, const std::function<void(CGContextRef ctx)>& draw, AlphaBitmap& out);
}
#endif
