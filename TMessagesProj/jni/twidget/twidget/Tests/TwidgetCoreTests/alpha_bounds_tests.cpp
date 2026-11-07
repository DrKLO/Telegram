#include "../../Sources/LiteHTML/core/lhx_alpha_bounds.h"

#include <cstdio>
#include <vector>

namespace
{

size_t checks = 0;
size_t failures = 0;

void check(bool condition, const char* name)
{
    ++checks;
    if(!condition)
    {
        if(failures < 20)
            std::printf("FAIL %s\n", name);
        ++failures;
    }
}

lhx::Alpha8Bounds reference(const uint8_t* data, size_t width, size_t height, size_t rowBytes,
                            size_t pixelStride, size_t alphaOffset)
{
    lhx::Alpha8Bounds result{width, height, 0, 0};
    for(size_t y = 0; y < height; ++y)
    {
        for(size_t x = 0; x < width; ++x)
        {
            if(data[y * rowBytes + x * pixelStride + alphaOffset] != 0)
            {
                result.left = std::min(result.left, x);
                result.right = std::max(result.right, x + 1);
                result.top = std::min(result.top, y);
                result.bottom = std::max(result.bottom, y + 1);
            }
        }
    }
    return result;
}

bool equal(const lhx::Alpha8Bounds& left, const lhx::Alpha8Bounds& right)
{
    if(left.empty() || right.empty())
        return left.empty() && right.empty();
    return left.left == right.left && left.right == right.right && left.top == right.top && left.bottom == right.bottom;
}

template<size_t PixelStride, size_t AlphaOffset>
void layouts()
{
    uint32_t random = 1729;
    for(size_t width : {size_t(0), size_t(1), size_t(2), size_t(3), size_t(7), size_t(15), size_t(16), size_t(17),
                        size_t(31), size_t(32), size_t(33), size_t(63), size_t(64), size_t(65), size_t(127), size_t(129)})
    {
        for(size_t height : {size_t(0), size_t(1), size_t(2), size_t(7)})
        {
            for(size_t prefix : {size_t(0), size_t(1), size_t(3), size_t(7)})
            {
                for(size_t padding : {size_t(0), size_t(1), size_t(5), size_t(63)})
                {
                    const size_t rowBytes = width * PixelStride + padding;
                    const size_t byteCount = width && height ? (height - 1) * rowBytes + width * PixelStride : 0;
                    std::vector<uint8_t> storage(prefix + byteCount + 1, 255);
                    uint8_t* data = storage.data() + prefix;
                    for(int pattern = 0; pattern < 5; ++pattern)
                    {
                        for(size_t y = 0; y < height; ++y)
                        {
                            for(size_t x = 0; x < width; ++x)
                            {
                                random = random * 1664525u + 1013904223u;
                                uint8_t alpha = 0;
                                if(pattern == 1)
                                    alpha = 255;
                                else if(pattern == 2 && y == height - 1 && x == width - 1)
                                    alpha = 1;
                                else if(pattern == 3 && (random >> 24) < 32)
                                    alpha = static_cast<uint8_t>((random >> 8) | 1);
                                else if(pattern == 4 && y % 2 == 0 && x == width / 2)
                                    alpha = 128;
                                data[y * rowBytes + x * PixelStride + AlphaOffset] = alpha;
                            }
                        }
                        lhx::Alpha8Bounds bounds;
                        const bool valid = lhx::alpha8Bounds<PixelStride, AlphaOffset>(data, byteCount, width, height, rowBytes, bounds);
                        check(valid && equal(bounds, reference(data, width, height, rowBytes, PixelStride, AlphaOffset)),
                              "chunk scan matches scalar for padded and unaligned layouts");
                    }
                }
            }
        }
    }
}

template<size_t PixelStride, size_t AlphaOffset>
void boundaries()
{
    for(size_t prefix = 0; prefix < 8; ++prefix)
    {
        for(size_t width = 1; width <= 139; ++width)
        {
            std::vector<uint8_t> storage(prefix + width * PixelStride, 0);
            uint8_t* data = storage.data() + prefix;
            for(size_t x = 0; x < width; ++x)
            {
                data[x * PixelStride + AlphaOffset] = 1;
                lhx::Alpha8Bounds bounds;
                const bool valid = lhx::alpha8Bounds<PixelStride, AlphaOffset>(data, width * PixelStride, width, 1, width * PixelStride, bounds);
                check(valid && bounds.left == x && bounds.right == x + 1 && bounds.top == 0 && bounds.bottom == 1,
                      "one nonzero alpha at every chunk boundary and tail");
                data[x * PixelStride + AlphaOffset] = 0;
            }
        }
    }
}

void validation()
{
    const uint8_t pixel[] = {255, 255, 255, 1};
    lhx::Alpha8Bounds bounds;
    check(!lhx::alpha8Bounds<4, 3>(nullptr, 4, 1, 1, 4, bounds) && bounds.empty(), "null nonempty buffer rejected");
    check(lhx::alpha8Bounds<4, 3>(nullptr, 0, 0, 1, 0, bounds) && bounds.empty(), "zero width accepted");
    check(lhx::alpha8Bounds<4, 3>(nullptr, 0, 1, 0, 0, bounds) && bounds.empty(), "zero height accepted");
    check(!lhx::alpha8Bounds<4, 3>(pixel, 3, 1, 1, 4, bounds), "short buffer rejected");
    check(!lhx::alpha8Bounds<4, 3>(pixel, 4, 1, 1, 3, bounds), "short row rejected");
    const size_t limit = std::numeric_limits<size_t>::max();
    check(!lhx::alpha8Bounds<4, 3>(pixel, limit, limit / 4 + 1, 1, limit, bounds), "pixel width overflow rejected");
    check(!lhx::alpha8Bounds<4, 3>(pixel, limit, 1, limit, 4, bounds), "row multiplication overflow rejected");
    check(!lhx::alpha8Bounds<4, 3>(pixel, limit, 1, 2, limit, bounds), "last row addition overflow rejected");
    check(lhx::alpha8Bounds<4, 3>(pixel, sizeof(pixel), 1, 1, 1000, bounds) && bounds.right == 1,
          "last row padding need not exist");
    const uint8_t transparent[] = {255, 255, 255, 0};
    check(lhx::alpha8Bounds<4, 3>(transparent, sizeof(transparent), 1, 1, 4, bounds) && bounds.empty(),
          "nonzero RGB does not imply nonzero alpha");
}

}

int main()
{
    layouts<1, 0>();
    layouts<4, 0>();
    layouts<4, 1>();
    layouts<4, 2>();
    layouts<4, 3>();
    boundaries<1, 0>();
    boundaries<4, 3>();
    validation();
    std::printf("%zu checks, %zu failures\n", checks, failures);
    return failures ? 1 : 0;
}
