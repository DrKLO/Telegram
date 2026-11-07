#include "LHRenderList.h"

#include <cmath>

namespace lhx
{
    bool rasterizeGlyph(CTFontRef font, CGGlyph glyph, CGPoint device, CGFloat scale, int maxSize, AlphaBitmap& out)
    {
        out = AlphaBitmap();
        CGRect bounds;
        CTFontGetBoundingRectsForGlyphs(font, kCTFontOrientationHorizontal, &glyph, &bounds, 1);
        if(CGRectIsNull(bounds) || CGRectIsEmpty(bounds))
            return true;
        const CGFloat ix = std::floor(device.x), iy = std::floor(device.y);
        const CGFloat fx = device.x - ix, fy = device.y - iy;
        const int     x0 = int(std::floor(fx + bounds.origin.x * scale)) - 2;
        const int     y0 = int(std::floor(fy - CGRectGetMaxY(bounds) * scale)) - 2;
        const int     x1 = int(std::ceil(fx + CGRectGetMaxX(bounds) * scale)) + 2;
        const int     y1 = int(std::ceil(fy - bounds.origin.y * scale)) + 2;
        const int     w = x1 - x0, h = y1 - y0;
        if(w <= 0 || h <= 0 || w > maxSize || h > maxSize)
            return false;
        out.pixels.assign(size_t(w) * h, 0);
        CGContextRef ctx = CGBitmapContextCreate(out.pixels.data(), w, h, 8, w, nullptr, kCGImageAlphaOnly);
        if(!ctx)
            return false;
        CGContextTranslateCTM(ctx, 0, h);
        CGContextScaleCTM(ctx, scale, -scale);
        CGContextTranslateCTM(ctx, -(ix + x0) / scale, -(iy + y0) / scale);
        CGContextSetAllowsFontSmoothing(ctx, false);
        CGContextSetShouldAntialias(ctx, true);
        CGContextSetTextMatrix(ctx, CGAffineTransformMakeScale(1, -1));
        CGContextSetTextPosition(ctx, device.x / scale, device.y / scale);
        CGPoint zero = CGPointZero;
        CTFontDrawGlyphs(font, &glyph, &zero, 1, ctx);
        CGContextRelease(ctx);
        out.x      = x0;
        out.y      = y0;
        out.width  = w;
        out.height = h;
        out.empty  = false;
        return true;
    }

    bool rasterizeMask(CGRect box, CGFloat scale, int maxSize, const std::function<void(CGContextRef ctx)>& draw, AlphaBitmap& out)
    {
        out = AlphaBitmap();
        const int w = int(box.size.width), h = int(box.size.height);
        if(w <= 0 || h <= 0 || w > maxSize || h > maxSize)
            return false;
        out.pixels.assign(size_t(w) * h, 0);
        CGContextRef ctx = CGBitmapContextCreate(out.pixels.data(), w, h, 8, w, nullptr, kCGImageAlphaOnly);
        if(!ctx)
            return false;
        CGContextTranslateCTM(ctx, 0, h);
        CGContextScaleCTM(ctx, scale, -scale);
        CGContextTranslateCTM(ctx, -box.origin.x / scale, -box.origin.y / scale);
        CGContextSetShouldAntialias(ctx, true);
        draw(ctx);
        CGContextRelease(ctx);
        out.width  = w;
        out.height = h;
        out.empty  = false;
        return true;
    }
}
