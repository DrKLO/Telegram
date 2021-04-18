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

#ifndef VDRAWHELPER_H
#define VDRAWHELPER_H

#include <memory>
#include <array>
#include "assert.h"
#include "vbitmap.h"
#include "vbrush.h"
#include "vrect.h"
#include "vrle.h"

V_USE_NAMESPACE

struct VSpanData;
struct Operator;

struct RenderFunc
{
    using Color = void (*)(uint32_t *dest, int length, uint32_t color, uint32_t alpha);
    using Src   = void (*)(uint32_t *dest, int length, const uint32_t *src, uint32_t alpha);
    enum class Type {
        Invalid,
        Color,
        Src,
    };
    RenderFunc() = default;
    RenderFunc(Type t, Color f):type_(t), color_(f){assert(t == Type::Color);}
    RenderFunc(Type t, Src f):type_(t), src_(f){ assert(t == Type::Src);}

    Type   type_{Type::Invalid};
    union {
        Color color_;
        Src   src_;
    };
};

class RenderFuncTable
{
public:
    RenderFuncTable();
    RenderFunc::Color color(BlendMode mode) const
    {
        return colorTable[uint32_t(mode)].color_;
    }
    RenderFunc::Src   src(BlendMode mode) const
    {
        return srcTable[uint32_t(mode)].src_;
    }
private:
    void neon();
    void sse();
    void updateColor(BlendMode mode, RenderFunc::Color f)
    {
        colorTable[uint32_t(mode)] = {RenderFunc::Type::Color, f};
    }
    void updateSrc(BlendMode mode, RenderFunc::Src f)
    {
        srcTable[uint32_t(mode)] = {RenderFunc::Type::Src, f};
    }
private:
    std::array<RenderFunc, uint32_t(BlendMode::Last)> colorTable;
    std::array<RenderFunc, uint32_t(BlendMode::Last)> srcTable;
};

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
    BlendMode                mode;
    SourceFetchProc          srcFetch;
    RenderFunc::Color        funcSolid;
    RenderFunc::Src          func;
    union {
        LinearGradientValues linear;
        RadialGradientValues radial;
    };
};

class VRasterBuffer {
public:
    VBitmap::Format prepare(const VBitmap *image);
    void            clear();

    void resetBuffer(int val = 0);

    inline uchar *scanLine(int y)
    {
        assert(y >= 0);
        assert(size_t(y) < mHeight);
        return mBuffer + y * mBytesPerLine;
    }
    uint32_t *pixelRef(int x, int y) const
    {
        return (uint32_t *)(mBuffer + y * mBytesPerLine + x * mBytesPerPixel);
    }

    size_t          width() const { return mWidth; }
    size_t          height() const { return mHeight; }
    size_t          bytesPerLine() const { return mBytesPerLine; }
    size_t          bytesPerPixel() const { return mBytesPerPixel; }
    VBitmap::Format format() const { return mFormat; }

private:
    VBitmap::Format mFormat{VBitmap::Format::ARGB32_Premultiplied};
    size_t          mWidth{0};
    size_t          mHeight{0};
    size_t          mBytesPerLine{0};
    size_t          mBytesPerPixel{0};
    mutable uchar * mBuffer{nullptr};
};

struct VGradientData {
    VGradient::Spread mSpread;
    struct Linear {
        float x1, y1, x2, y2;
    };
    struct Radial {
        float cx, cy, fx, fy, cradius, fradius;
    };
    union {
        Linear linear;
        Radial radial;
    };
    const uint32_t *mColorTable;
    bool            mColorTableAlpha;
};

struct VTextureData : public VRasterBuffer {
    uint32_t pixel(int x, int y) const { return *pixelRef(x, y); };
    uchar    alpha() const { return mAlpha; }
    void     setAlpha(uchar alpha) { mAlpha = alpha; }
    void     setClip(const VRect &clip);
    // clip rect
    int   left;
    int   right;
    int   top;
    int   bottom;
    bool  hasAlpha;
    uchar mAlpha;
};

struct VColorTable {
    uint32_t buffer32[VGradient::colorTableSize];
    bool     alpha{true};
};

struct VSpanData {
    enum class Type { None, Solid, LinearGradient, RadialGradient, Texture };

    void updateSpanFunc();
    void init(VRasterBuffer *image);
    void setup(const VBrush &brush, BlendMode mode = BlendMode::SrcOver,
               int alpha = 255);
    void setupMatrix(const VMatrix &matrix);

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
        return mRasterBuffer->pixelRef(x + mOffset.x(), y + mOffset.y());
    }
    void initTexture(const VBitmap *image, int alpha, const VRect &sourceRect);
    const VTextureData &texture() const { return mTexture; }

    BlendMode                          mBlendMode{BlendMode::SrcOver};
    VRasterBuffer *                    mRasterBuffer;
    ProcessRleSpan                     mBlendFunc;
    ProcessRleSpan                     mUnclippedBlendFunc;
    VSpanData::Type                    mType;
    std::shared_ptr<const VColorTable> mColorTable{nullptr};
    VPoint                             mOffset;  // offset to the subsurface
    VSize                              mDrawableSize;  // suburface size
    uint32_t                           mSolid;
    VGradientData                      mGradient;
    VTextureData                       mTexture;

    float m11, m12, m13, m21, m22, m23, m33, dx, dy;  // inverse xform matrix
    bool  fast_matrix{true};
    VMatrix::MatrixType transformType{VMatrix::MatrixType::None};
};

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

static inline uint32_t interpolate_pixel(uint x, uint a, uint y, uint b)
{
    uint t = (x & 0xff00ff) * a + (y & 0xff00ff) * b;
    t >>= 8;
    t &= 0xff00ff;
    x = ((x >> 8) & 0xff00ff) * a + ((y >> 8) & 0xff00ff) * b;
    x &= 0xff00ff00;
    x |= t;
    return x;
}

#endif  // QDRAWHELPER_P_H
