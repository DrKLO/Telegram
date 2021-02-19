/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <cstring>
#include "vdrawhelper.h"

/*
result = s
dest = s * ca + d * cia
*/
static void color_Source(uint32_t *dest, int length, uint32_t color,
                         uint32_t alpha)
{
    int ialpha, i;

    if (alpha == 255) {
        memfill32(dest, color, length);
    } else {
        ialpha = 255 - alpha;
        color = BYTE_MUL(color, alpha);
        for (i = 0; i < length; ++i)
            dest[i] = color + BYTE_MUL(dest[i], ialpha);
    }
}

/*
  r = s + d * sia
  dest = r * ca + d * cia
       =  (s + d * sia) * ca + d * cia
       = s * ca + d * (sia * ca + cia)
       = s * ca + d * (1 - sa*ca)
       = s' + d ( 1 - s'a)
*/
static void color_SourceOver(uint32_t *dest, int length, uint32_t color,
                             uint32_t alpha)
{
    int ialpha, i;

    if (alpha != 255) color = BYTE_MUL(color, alpha);
    ialpha = 255 - vAlpha(color);
    for (i = 0; i < length; ++i) dest[i] = color + BYTE_MUL(dest[i], ialpha);
}

/*
  result = d * sa
  dest = d * sa * ca + d * cia
       = d * (sa * ca + cia)
*/
static void color_DestinationIn(uint *dest, int length, uint color,
                                uint alpha)
{
    uint a = vAlpha(color);
    if (alpha != 255) {
        a = BYTE_MUL(a, alpha) + 255 - alpha;
    }
    for (int i = 0; i < length; ++i) {
        dest[i] = BYTE_MUL(dest[i], a);
    }
}

/*
  result = d * sia
  dest = d * sia * ca + d * cia
       = d * (sia * ca + cia)
*/
static void color_DestinationOut(uint *dest, int length, uint color,
                                 uint alpha)
{
    uint a = vAlpha(~color);
    if (alpha != 255) a = BYTE_MUL(a, alpha) + 255 - alpha;
    for (int i = 0; i < length; ++i) {
        dest[i] = BYTE_MUL(dest[i], a);
    }
}

static void src_Source(uint32_t *dest, int length, const uint32_t *src,
                       uint32_t alpha)
{
    if (alpha == 255) {
        memcpy(dest, src, size_t(length) * sizeof(uint));
    } else {
        uint ialpha = 255 - alpha;
        for (int i = 0; i < length; ++i) {
            dest[i] =
                interpolate_pixel(src[i], alpha, dest[i], ialpha);
        }
    }
}

/* s' = s * ca
 * d' = s' + d (1 - s'a)
 */
static void src_SourceOver(uint32_t *dest, int length, const uint32_t *src,
                           uint32_t alpha)
{
    uint s, sia;

    if (alpha == 255) {
        for (int i = 0; i < length; ++i) {
            s = src[i];
            if (s >= 0xff000000)
                dest[i] = s;
            else if (s != 0) {
                sia = vAlpha(~s);
                dest[i] = s + BYTE_MUL(dest[i], sia);
            }
        }
    } else {
        /* source' = source * const_alpha
         * dest = source' + dest ( 1- source'a)
         */
        for (int i = 0; i < length; ++i) {
            s = BYTE_MUL(src[i], alpha);
            sia = vAlpha(~s);
            dest[i] = s + BYTE_MUL(dest[i], sia);
        }
    }
}

static void src_DestinationIn(uint *dest, int length, const uint *src,
                              uint alpha)
{
    if (alpha == 255) {
        for (int i = 0; i < length; ++i) {
            dest[i] = BYTE_MUL(dest[i], vAlpha(src[i]));
        }
    } else {
        uint cia = 255 - alpha;
        for (int i = 0; i < length; ++i) {
            uint a = BYTE_MUL(vAlpha(src[i]), alpha) + cia;
            dest[i] = BYTE_MUL(dest[i], a);
        }
    }
}

static void src_DestinationOut(uint *dest, int length, const uint *src,
                               uint alpha)
{
    if (alpha == 255) {
        for (int i = 0; i < length; ++i) {
            dest[i] = BYTE_MUL(dest[i], vAlpha(~src[i]));
        }
    } else {
        uint cia = 255 - alpha;
        for (int i = 0; i < length; ++i) {
            uint sia = BYTE_MUL(vAlpha(~src[i]), alpha) + cia;
            dest[i] = BYTE_MUL(dest[i], sia);
        }
    }
}

RenderFuncTable::RenderFuncTable()
{
    updateColor(BlendMode::Src, color_Source);
    updateColor(BlendMode::SrcOver, color_SourceOver);
    updateColor(BlendMode::DestIn, color_DestinationIn);
    updateColor(BlendMode::DestOut, color_DestinationOut);

    updateSrc(BlendMode::Src, src_Source);
    updateSrc(BlendMode::SrcOver, src_SourceOver);
    updateSrc(BlendMode::DestIn, src_DestinationIn);
    updateSrc(BlendMode::DestOut, src_DestinationOut);

#if defined(__ARM_NEON__) || defined(__ARM64_NEON__)
    neon();
#endif
#if defined(__SSE2__)
    sse();
#endif
}
