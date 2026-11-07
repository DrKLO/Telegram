#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>

namespace lhx
{

struct Alpha8Bounds
{
    size_t left = 0;
    size_t top = 0;
    size_t right = 0;
    size_t bottom = 0;

    bool empty() const { return left >= right || top >= bottom; }
};

namespace alpha_bounds_detail
{

inline uint64_t word(const uint8_t* bytes)
{
    uint64_t value;
    std::memcpy(&value, bytes, sizeof(value));
    return value;
}

inline bool blockHasAlpha(const uint8_t* bytes, uint64_t mask)
{
    const uint64_t combined = word(bytes) | word(bytes + 8) | word(bytes + 16) | word(bytes + 24) |
                              word(bytes + 32) | word(bytes + 40) | word(bytes + 48) | word(bytes + 56);
    return (combined & mask) != 0;
}

template<size_t PixelStride, size_t AlphaOffset>
uint64_t mask()
{
    uint8_t bytes[8] = {};
    for(size_t offset = AlphaOffset; offset < sizeof(bytes); offset += PixelStride)
        bytes[offset] = 255;
    return word(bytes);
}

}

template<size_t PixelStride = 1, size_t AlphaOffset = 0>
bool alpha8Bounds(const uint8_t* data, size_t byteCount, size_t width, size_t height, size_t rowBytes,
                  Alpha8Bounds& bounds)
{
    static_assert(PixelStride == 1 || PixelStride == 4);
    static_assert(AlphaOffset < PixelStride);
    bounds = {};
    if(width == 0 || height == 0)
        return true;
    constexpr size_t limit = std::numeric_limits<size_t>::max();
    if(!data || width > limit / PixelStride)
        return false;
    const size_t usedRowBytes = width * PixelStride;
    if(rowBytes < usedRowBytes || height - 1 > (limit - usedRowBytes) / rowBytes)
        return false;
    const size_t requiredBytes = (height - 1) * rowBytes + usedRowBytes;
    if(requiredBytes > byteCount)
        return false;
    bounds = {width, height, 0, 0};
    constexpr size_t blockPixels = 64 / PixelStride;
    const uint64_t mask = alpha_bounds_detail::mask<PixelStride, AlphaOffset>();
    for(size_t y = 0; y < height; ++y)
    {
        const uint8_t* row = data + y * rowBytes;
        size_t first = 0;
        while(width - first >= blockPixels &&
              !alpha_bounds_detail::blockHasAlpha(row + first * PixelStride, mask))
            first += blockPixels;
        while(first < width && row[first * PixelStride + AlphaOffset] == 0)
            ++first;
        if(first == width)
            continue;
        size_t last = width;
        while(last - first >= blockPixels &&
              !alpha_bounds_detail::blockHasAlpha(row + (last - blockPixels) * PixelStride, mask))
            last -= blockPixels;
        while(last > first + 1 && row[(last - 1) * PixelStride + AlphaOffset] == 0)
            --last;
        bounds.left = std::min(bounds.left, first);
        bounds.right = std::max(bounds.right, last);
        bounds.top = std::min(bounds.top, y);
        bounds.bottom = y + 1;
    }
    return true;
}

}
