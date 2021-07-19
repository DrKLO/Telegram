/* 
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#ifndef VDRAWHELPER_H
#define VDRAWHELPER_H

#include <cstring>
#include <memory>
#include "assert.h"
#include "vbitmap.h"
#include "vbrush.h"
#include "vpainter.h"
#include "vrect.h"
#include "vrle.h"

V_USE_NAMESPACE

struct VSpanData;
struct Operator;

typedef void (*CompositionFunctionSolid)(uint32_t *dest, int length,
                                         uint32_t color, uint32_t const_alpha);
typedef void (*CompositionFunction)(uint32_t *dest, const uint32_t *src,
                                    int length, uint32_t const_alpha);
typedef void (*SourceFetchProc)(uint32_t *buffer, const Operator *o,
                                const VSpanData *data, int y, int x,
                                int length);
typedef void (*ProcessRleSpan)(size_t count, const VRle::Span *spans,
                               void *userData);

extern void memfill32(uint32_t *dest, uint32_t value, int count);

struct LinearGradientValues {
    float dx;
    float dy;
    float l;
    float off;
};

struct RadialGradientValues {
    float dx;
    float dy;
    float dr;
    float sqrfr;
    float a;
    float inv2a;
    bool  extended;
};

struct Operator {
    VPainter::CompositionMode mode;
    SourceFetchProc           srcFetch;
    CompositionFunctionSolid  funcSolid;
    CompositionFunction       func;
    union {
        LinearGradientValues linear;
        RadialGradientValues radial;
    };
};

class VRasterBuffer {
public:
    VBitmap::Format prepare(VBitmap *image);
    void            clear();

    void resetBuffer(int val = 0);

    inline uchar *scanLine(int y)
    {
        assert(y >= 0);
        assert(y < mHeight);
        return mBuffer + y * mBytesPerLine;
    }

    int width() const { return mWidth; }
    int height() const { return mHeight; }
    int bytesPerLine() const { return mBytesPerLine; }
    int bytesPerPixel() const { return mBytesPerPixel; }

    VBitmap::Format           mFormat{VBitmap::Format::ARGB32};
private:
    int    mWidth{0};
    int    mHeight{0};
    int    mBytesPerLine{0};
    int    mBytesPerPixel{0};
    uchar *mBuffer{nullptr};
};

struct VGradientData {
    VGradient::Spread mSpread;
    union {
        struct {
            float x1, y1, x2, y2;
        } linear;
        struct {
            float cx, cy, fx, fy, cradius, fradius;
        } radial;
    };
    const uint32_t *mColorTable;
    bool            mColorTableAlpha;
};

struct VBitmapData
{
    const uchar *imageData;
    const uchar *scanLine(int y) const { return imageData + y*bytesPerLine; }

    int width;
    int height;
    // clip rect
    int x1;
    int y1;
    int x2;
    int y2;
    uint bytesPerLine;
    VBitmap::Format format;
    bool hasAlpha;
    enum Type {
        Plain,
        Tiled
    };
    Type type;
    int const_alpha;
};

struct VColorTable
{
    uint32_t buffer32[VGradient::colorTableSize];
    bool     alpha{true};
};

struct VSpanData {
    enum class Type { None, Solid, LinearGradient, RadialGradient, Texture };

    void  updateSpanFunc();
    void  init(VRasterBuffer *image);
    void  setup(const VBrush &            brush,
                VPainter::CompositionMode mode = VPainter::CompModeSrcOver,
                int                       alpha = 255);
    void  setupMatrix(const VMatrix &matrix);

    VRect clipRect() const
    {
        return VRect(0, 0, mDrawableSize.width(), mDrawableSize.height());
    }

    void setDrawRegion(const VRect &region)
    {
        mOffset = VPoint(region.left(), region.top());
        mDrawableSize = VSize(region.width(), region.height());
    }

    uint *buffer(int x, int y) const
    {
        return (uint *)(mRasterBuffer->scanLine(y + mOffset.y())) + x + mOffset.x();
    }
    void initTexture(const VBitmap *image, int alpha, VBitmapData::Type type, const VRect &sourceRect);

    VPainter::CompositionMode            mCompositionMode{VPainter::CompositionMode::CompModeSrcOver};
    VRasterBuffer *                      mRasterBuffer;
    ProcessRleSpan                       mBlendFunc;
    ProcessRleSpan                       mUnclippedBlendFunc;
    VSpanData::Type                      mType;
    std::shared_ptr<const VColorTable>   mColorTable{nullptr};
    VPoint                               mOffset; // offset to the subsurface
    VSize                                mDrawableSize;// suburface size
    union {
        uint32_t      mSolid;
        VGradientData mGradient;
        VBitmapData   mBitmap;
    };
    float m11, m12, m13, m21, m22, m23, m33, dx, dy;  // inverse xform matrix
    bool fast_matrix{true};
    VMatrix::MatrixType   transformType{VMatrix::MatrixType::None};
};

void        vInitDrawhelperFunctions();
extern void vInitBlendFunctions();

#define BYTE_MUL(c, a)                                  \
    ((((((c) >> 8) & 0x00ff00ff) * (a)) & 0xff00ff00) + \
     (((((c)&0x00ff00ff) * (a)) >> 8) & 0x00ff00ff))

inline constexpr int vRed(uint32_t c)
{
    return ((c >> 16) & 0xff);
}

inline constexpr int vGreen(uint32_t c)
{
    return ((c >> 8) & 0xff);
}

inline constexpr int vBlue(uint32_t c)
{
    return (c & 0xff);
}

inline constexpr int vAlpha(uint32_t c)
{
    return c >> 24;
}

static inline uint INTERPOLATE_PIXEL_255(uint x, uint a, uint y, uint b)
{
    uint t = (x & 0xff00ff) * a + (y & 0xff00ff) * b;
    t >>= 8;
    t &= 0xff00ff;
    x = ((x >> 8) & 0xff00ff) * a + ((y >> 8) & 0xff00ff) * b;
    x &= 0xff00ff00;
    x |= t;
    return x;
}

#define LOOP_ALIGNED_U1_A4(DEST, LENGTH, UOP, A4OP) \
    {                                               \
        while ((uintptr_t)DEST & 0xF && LENGTH)     \
            UOP                                     \
                                                    \
                while (LENGTH)                      \
            {                                       \
                switch (LENGTH) {                   \
                case 3:                             \
                case 2:                             \
                case 1:                             \
                    UOP break;                      \
                default:                            \
                    A4OP break;                     \
                }                                   \
            }                                       \
    }

#endif  // QDRAWHELPER_P_H
