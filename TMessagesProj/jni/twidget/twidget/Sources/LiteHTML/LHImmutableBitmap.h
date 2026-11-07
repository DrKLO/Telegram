#pragma once

#include <CoreGraphics/CoreGraphics.h>
#include <atomic>
#include <cstddef>
#include <limits>
#include <utility>

class LHImmutableBitmap
{
    struct SnapshotDefaults
    {
        bool valid = false;
        bool interpolate = false;
        CGColorRenderingIntent intent = kCGRenderingIntentDefault;
    };

    CGContextRef m_context = nullptr;
    inline static std::atomic<size_t> s_retainedBytes{0};
    inline static std::atomic<size_t> s_retainedContexts{0};

    static const SnapshotDefaults& snapshotDefaults()
    {
        static const SnapshotDefaults defaults = [] {
            SnapshotDefaults value;
            CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
            CGContextRef context = space ? CGBitmapContextCreate(nullptr, 1, 1, 8, 0, space,
                kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little) : nullptr;
            CGImageRef image = context ? CGBitmapContextCreateImage(context) : nullptr;
            if(image)
            {
                value.valid = true;
                value.interpolate = CGImageGetShouldInterpolate(image);
                value.intent = CGImageGetRenderingIntent(image);
                CGImageRelease(image);
            }
            if(context)
                CGContextRelease(context);
            if(space)
                CGColorSpaceRelease(space);
            return value;
        }();
        return defaults;
    }

    static void releaseData(void* owner, const void*, size_t bytes)
    {
        CGContextRelease(static_cast<CGContextRef>(owner));
        s_retainedBytes.fetch_sub(bytes, std::memory_order_relaxed);
        s_retainedContexts.fetch_sub(1, std::memory_order_relaxed);
    }

public:
    explicit LHImmutableBitmap(CGContextRef ownedContext) : m_context(ownedContext) {}

    LHImmutableBitmap(size_t width, size_t height, CGColorSpaceRef space, size_t bytesPerRow = 0)
    {
        if(!space || width == 0 || height == 0 || width > std::numeric_limits<size_t>::max() / 4 ||
           (bytesPerRow && bytesPerRow < width * 4) ||
           height > std::numeric_limits<size_t>::max() / (bytesPerRow ? bytesPerRow : width * 4))
            return;
        m_context = CGBitmapContextCreate(nullptr, width, height, 8, bytesPerRow, space,
            kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    }

    ~LHImmutableBitmap()
    {
        if(m_context)
            CGContextRelease(m_context);
    }

    LHImmutableBitmap(const LHImmutableBitmap&) = delete;
    LHImmutableBitmap& operator=(const LHImmutableBitmap&) = delete;
    LHImmutableBitmap(LHImmutableBitmap&& other) noexcept : m_context(std::exchange(other.m_context, nullptr)) {}
    LHImmutableBitmap& operator=(LHImmutableBitmap&&) = delete;

    CGContextRef context() const { return m_context; }
    static size_t retainedBytes() { return s_retainedBytes.load(std::memory_order_relaxed); }
    static size_t retainedContexts() { return s_retainedContexts.load(std::memory_order_relaxed); }

    CGImageRef takeImage(bool immutable = true)
    {
        CGContextRef context = std::exchange(m_context, nullptr);
        if(!context)
            return nullptr;
        const auto& defaults = snapshotDefaults();
        const size_t width = CGBitmapContextGetWidth(context), height = CGBitmapContextGetHeight(context);
        const size_t stride = CGBitmapContextGetBytesPerRow(context);
        const size_t componentBits = CGBitmapContextGetBitsPerComponent(context);
        const size_t pixelBits = CGBitmapContextGetBitsPerPixel(context);
        CGColorSpaceRef space = CGBitmapContextGetColorSpace(context);
        const CGBitmapInfo info = CGBitmapContextGetBitmapInfo(context);
        void* bytes = CGBitmapContextGetData(context);
        if(!immutable || !defaults.valid || !bytes || !space || componentBits != 8 || pixelBits != 32 ||
           (info & kCGBitmapAlphaInfoMask) != kCGImageAlphaPremultipliedFirst ||
           (info & kCGBitmapByteOrderMask) != kCGBitmapByteOrder32Little ||
           width == 0 || height == 0 || stride > std::numeric_limits<size_t>::max() / height)
        {
            CGImageRef image = CGBitmapContextCreateImage(context);
            CGContextRelease(context);
            return image;
        }
        CGContextFlush(context);
        CGDataProviderRef provider = CGDataProviderCreateWithData(context, bytes, stride * height, releaseData);
        if(!provider)
        {
            CGImageRef image = CGBitmapContextCreateImage(context);
            CGContextRelease(context);
            return image;
        }
        s_retainedBytes.fetch_add(stride * height, std::memory_order_relaxed);
        s_retainedContexts.fetch_add(1, std::memory_order_relaxed);
        CGImageRef image = CGImageCreate(width, height, componentBits, pixelBits, stride, space, info,
            provider, nullptr, defaults.interpolate, defaults.intent);
        CGDataProviderRelease(provider);
        return image;
    }
};
