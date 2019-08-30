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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301 USA
 */

/****************************************************************************
**
** Copyright (C) 2009 Nokia Corporation and/or its subsidiary(-ies).
** All rights reserved.
** Contact: Nokia Corporation (qt-info@nokia.com)
**
** This file is part of the QtGui module of the Qt Toolkit.
**
** $QT_BEGIN_LICENSE:LGPL$
** No Commercial Usage
** This file contains pre-release code and may not be distributed.
** You may use this file in accordance with the terms and conditions
** contained in the Technology Preview License Agreement accompanying
** this package.
**
** GNU Lesser General Public License Usage
** Alternatively, this file may be used under the terms of the GNU Lesser
** General Public License version 2.1 as published by the Free Software
** Foundation and appearing in the file LICENSE.LGPL included in the
** packaging of this file.  Please review the following information to
** ensure the GNU Lesser General Public License version 2.1 requirements
** will be met: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.
**
** In addition, as a special exception, Nokia gives you certain additional
** rights.  These rights are described in the Nokia Qt LGPL Exception
** version 1.1, included in the file LGPL_EXCEPTION.txt in this package.
**
** If you have questions regarding the use of this file, please contact
** Nokia at qt-info@nokia.com.
**
** $QT_END_LICENSE$
**
****************************************************************************/

#include "vdrawhelper.h"
#include <algorithm>
#include <climits>
#include <cstring>
#include <mutex>
#include <unordered_map>

class VGradientCache {
public:
    struct CacheInfo : public VColorTable {
        inline CacheInfo(VGradientStops s) : stops(std::move(s)) {}
        VGradientStops stops;
    };
    using VCacheData = std::shared_ptr<const CacheInfo>;
    using VCacheKey = int64_t;
    using VGradientColorTableHash =
        std::unordered_multimap<VCacheKey, VCacheData>;

    bool generateGradientColorTable(const VGradientStops &stops, float alpha,
                                    uint32_t *colorTable, int size);
    VCacheData getBuffer(const VGradient &gradient)
    {
        VCacheKey             hash_val = 0;
        VCacheData            info;
        const VGradientStops &stops = gradient.mStops;
        for (uint i = 0; i < stops.size() && i <= 2; i++)
            hash_val += (stops[i].second.premulARGB() * gradient.alpha());

        {
            std::lock_guard<std::mutex> guard(mMutex);

            size_t count = mCache.count(hash_val);
            if (!count) {
                // key is not present in the hash
                info = addCacheElement(hash_val, gradient);
            } else if (count == 1) {
                auto search = mCache.find(hash_val);
                if (search->second->stops == stops) {
                    info = search->second;
                } else {
                    // didn't find an exact match
                    info = addCacheElement(hash_val, gradient);
                }
            } else {
                // we have a multiple data with same key
                auto range = mCache.equal_range(hash_val);
                for (auto it = range.first; it != range.second; ++it) {
                    if (it->second->stops == stops) {
                        info = it->second;
                        break;
                    }
                }
                if (!info) {
                    // didn't find an exact match
                    info = addCacheElement(hash_val, gradient);
                }
            }
        }
        return info;
    }

protected:
    uint       maxCacheSize() const { return 60; }
    VCacheData addCacheElement(VCacheKey hash_val, const VGradient &gradient)
    {
        if (mCache.size() == maxCacheSize()) {
            uint count = maxCacheSize() / 10;
            while (count--) {
                mCache.erase(mCache.begin());
            }
        }
        auto cache_entry = std::make_shared<CacheInfo>(gradient.mStops);
        cache_entry->alpha = generateGradientColorTable(
            gradient.mStops, gradient.alpha(), cache_entry->buffer32,
            VGradient::colorTableSize);
        mCache.insert(std::make_pair(hash_val, cache_entry));
        return cache_entry;
    }

    VGradientColorTableHash mCache;
    std::mutex              mMutex;
};

bool VGradientCache::generateGradientColorTable(const VGradientStops &stops,
                                                float                 opacity,
                                                uint32_t *colorTable, int size)
{
    int                  dist, idist, pos = 0, i;
    bool                 alpha = false;
    int                  stopCount = stops.size();
    const VGradientStop *curr, *next, *start;
    uint32_t             curColor, nextColor;
    float                delta, t, incr, fpos;

    if (!vCompare(opacity, 1.0f)) alpha = true;

    start = stops.data();
    curr = start;
    if (!curr->second.isOpaque()) alpha = true;
    curColor = curr->second.premulARGB(opacity);
    incr = 1.0 / (float)size;
    fpos = 1.5 * incr;

    colorTable[pos++] = curColor;

    while (fpos <= curr->first) {
        colorTable[pos] = colorTable[pos - 1];
        pos++;
        fpos += incr;
    }

    for (i = 0; i < stopCount - 1; ++i) {
        curr = (start + i);
        next = (start + i + 1);
        delta = 1 / (next->first - curr->first);
        if (!next->second.isOpaque()) alpha = true;
        nextColor = next->second.premulARGB(opacity);
        while (fpos < next->first && pos < size) {
            t = (fpos - curr->first) * delta;
            dist = (int)(255 * t);
            idist = 255 - dist;
            colorTable[pos] =
                INTERPOLATE_PIXEL_255(curColor, idist, nextColor, dist);
            ++pos;
            fpos += incr;
        }
        curColor = nextColor;
    }

    for (; pos < size; ++pos) colorTable[pos] = curColor;

    // Make sure the last color stop is represented at the end of the table
    colorTable[size - 1] = curColor;
    return alpha;
}

static VGradientCache VGradientCacheInstance;

void VRasterBuffer::clear()
{
    memset(mBuffer, 0, mHeight * mBytesPerLine);
}

VBitmap::Format VRasterBuffer::prepare(VBitmap *image)
{
    mBuffer = image->data();
    mWidth = image->width();
    mHeight = image->height();
    mBytesPerPixel = 4;
    mBytesPerLine = image->stride();

    mFormat = image->format();
    return mFormat;
}

void VSpanData::init(VRasterBuffer *image)
{
    mRasterBuffer = image;
    setDrawRegion(VRect(0, 0, image->width(), image->height()));
    mType = VSpanData::Type::None;
    mBlendFunc = nullptr;
    mUnclippedBlendFunc = nullptr;
}

extern CompositionFunction             COMP_functionForMode_C[];
extern CompositionFunctionSolid        COMP_functionForModeSolid_C[];
static const CompositionFunction *     functionForMode = COMP_functionForMode_C;
static const CompositionFunctionSolid *functionForModeSolid =
    COMP_functionForModeSolid_C;

/*
 *  Gradient Draw routines
 *
 */

#define FIXPT_BITS 8
#define FIXPT_SIZE (1 << FIXPT_BITS)
static inline void getLinearGradientValues(LinearGradientValues *v,
                                           const VSpanData *     data)
{
    const VGradientData *grad = &data->mGradient;
    v->dx = grad->linear.x2 - grad->linear.x1;
    v->dy = grad->linear.y2 - grad->linear.y1;
    v->l = v->dx * v->dx + v->dy * v->dy;
    v->off = 0;
    if (v->l != 0) {
        v->dx /= v->l;
        v->dy /= v->l;
        v->off = -v->dx * grad->linear.x1 - v->dy * grad->linear.y1;
    }
}

static inline void getRadialGradientValues(RadialGradientValues *v,
                                           const VSpanData *     data)
{
    const VGradientData &gradient = data->mGradient;
    v->dx = gradient.radial.cx - gradient.radial.fx;
    v->dy = gradient.radial.cy - gradient.radial.fy;

    v->dr = gradient.radial.cradius - gradient.radial.fradius;
    v->sqrfr = gradient.radial.fradius * gradient.radial.fradius;

    v->a = v->dr * v->dr - v->dx * v->dx - v->dy * v->dy;
    v->inv2a = 1 / (2 * v->a);

    v->extended = !vIsZero(gradient.radial.fradius) || v->a <= 0;
}

static inline int gradientClamp(const VGradientData *grad, int ipos)
{
    int limit;

    if (grad->mSpread == VGradient::Spread::Repeat) {
        ipos = ipos % VGradient::colorTableSize;
        ipos = ipos < 0 ? VGradient::colorTableSize + ipos : ipos;
    } else if (grad->mSpread == VGradient::Spread::Reflect) {
        limit = VGradient::colorTableSize * 2;
        ipos = ipos % limit;
        ipos = ipos < 0 ? limit + ipos : ipos;
        ipos = ipos >= VGradient::colorTableSize ? limit - 1 - ipos : ipos;
    } else {
        if (ipos < 0)
            ipos = 0;
        else if (ipos >= VGradient::colorTableSize)
            ipos = VGradient::colorTableSize - 1;
    }
    return ipos;
}

static uint32_t gradientPixelFixed(const VGradientData *grad, int fixed_pos)
{
    int ipos = (fixed_pos + (FIXPT_SIZE / 2)) >> FIXPT_BITS;

    return grad->mColorTable[gradientClamp(grad, ipos)];
}

static inline uint32_t gradientPixel(const VGradientData *grad, float pos)
{
    int ipos = (int)(pos * (VGradient::colorTableSize - 1) + (float)(0.5));

    return grad->mColorTable[gradientClamp(grad, ipos)];
}

void fetch_linear_gradient(uint32_t *buffer, const Operator *op,
                           const VSpanData *data, int y, int x, int length)
{
    float                t, inc;
    const VGradientData *gradient = &data->mGradient;

    bool  affine = true;
    float rx = 0, ry = 0;
    if (op->linear.l == 0) {
        t = inc = 0;
    } else {
        rx = data->m21 * (y + float(0.5)) + data->m11 * (x + float(0.5)) +
             data->dx;
        ry = data->m22 * (y + float(0.5)) + data->m12 * (x + float(0.5)) +
             data->dy;
        t = op->linear.dx * rx + op->linear.dy * ry + op->linear.off;
        inc = op->linear.dx * data->m11 + op->linear.dy * data->m12;
        affine = !data->m13 && !data->m23;

        if (affine) {
            t *= (VGradient::colorTableSize - 1);
            inc *= (VGradient::colorTableSize - 1);
        }
    }

    const uint32_t *end = buffer + length;
    if (affine) {
        if (inc > float(-1e-5) && inc < float(1e-5)) {
            memfill32(buffer, gradientPixelFixed(gradient, int(t * FIXPT_SIZE)),
                      length);
        } else {
            if (t + inc * length < float(INT_MAX >> (FIXPT_BITS + 1)) &&
                t + inc * length > float(INT_MIN >> (FIXPT_BITS + 1))) {
                // we can use fixed point math
                int t_fixed = int(t * FIXPT_SIZE);
                int inc_fixed = int(inc * FIXPT_SIZE);
                while (buffer < end) {
                    *buffer = gradientPixelFixed(gradient, t_fixed);
                    t_fixed += inc_fixed;
                    ++buffer;
                }
            } else {
                // we have to fall back to float math
                while (buffer < end) {
                    *buffer =
                        gradientPixel(gradient, t / VGradient::colorTableSize);
                    t += inc;
                    ++buffer;
                }
            }
        }
    } else {  // fall back to float math here as well
        float rw = data->m23 * (y + float(0.5)) + data->m13 * (x + float(0.5)) +
                   data->m33;
        while (buffer < end) {
            float x = rx / rw;
            float y = ry / rw;
            t = (op->linear.dx * x + op->linear.dy * y) + op->linear.off;

            *buffer = gradientPixel(gradient, t);
            rx += data->m11;
            ry += data->m12;
            rw += data->m13;
            if (!rw) {
                rw += data->m13;
            }
            ++buffer;
        }
    }
}

static inline float radialDeterminant(float a, float b, float c)
{
    return (b * b) - (4 * a * c);
}

static void fetch(uint32_t *buffer, uint32_t *end, const Operator *op,
                  const VSpanData *data, float det, float delta_det,
                  float delta_delta_det, float b, float delta_b)
{
    if (op->radial.extended) {
        while (buffer < end) {
            uint32_t result = 0;
            if (det >= 0) {
                float w = std::sqrt(det) - b;
                if (data->mGradient.radial.fradius + op->radial.dr * w >= 0)
                    result = gradientPixel(&data->mGradient, w);
            }

            *buffer = result;

            det += delta_det;
            delta_det += delta_delta_det;
            b += delta_b;

            ++buffer;
        }
    } else {
        while (buffer < end) {
            *buffer++ = gradientPixel(&data->mGradient, std::sqrt(det) - b);

            det += delta_det;
            delta_det += delta_delta_det;
            b += delta_b;
        }
    }
}

void fetch_radial_gradient(uint32_t *buffer, const Operator *op,
                           const VSpanData *data, int y, int x, int length)
{
    // avoid division by zero
    if (vIsZero(op->radial.a)) {
        memfill32(buffer, 0, length);
        return;
    }

    float rx =
        data->m21 * (y + float(0.5)) + data->dx + data->m11 * (x + float(0.5));
    float ry =
        data->m22 * (y + float(0.5)) + data->dy + data->m12 * (x + float(0.5));
    bool affine = !data->m13 && !data->m23;

    uint32_t *end = buffer + length;
    if (affine) {
        rx -= data->mGradient.radial.fx;
        ry -= data->mGradient.radial.fy;

        float inv_a = 1 / float(2 * op->radial.a);

        const float delta_rx = data->m11;
        const float delta_ry = data->m12;

        float b = 2 * (op->radial.dr * data->mGradient.radial.fradius +
                       rx * op->radial.dx + ry * op->radial.dy);
        float delta_b =
            2 * (delta_rx * op->radial.dx + delta_ry * op->radial.dy);
        const float b_delta_b = 2 * b * delta_b;
        const float delta_b_delta_b = 2 * delta_b * delta_b;

        const float bb = b * b;
        const float delta_bb = delta_b * delta_b;

        b *= inv_a;
        delta_b *= inv_a;

        const float rxrxryry = rx * rx + ry * ry;
        const float delta_rxrxryry = delta_rx * delta_rx + delta_ry * delta_ry;
        const float rx_plus_ry = 2 * (rx * delta_rx + ry * delta_ry);
        const float delta_rx_plus_ry = 2 * delta_rxrxryry;

        inv_a *= inv_a;

        float det =
            (bb - 4 * op->radial.a * (op->radial.sqrfr - rxrxryry)) * inv_a;
        float delta_det = (b_delta_b + delta_bb +
                           4 * op->radial.a * (rx_plus_ry + delta_rxrxryry)) *
                          inv_a;
        const float delta_delta_det =
            (delta_b_delta_b + 4 * op->radial.a * delta_rx_plus_ry) * inv_a;

        fetch(buffer, end, op, data, det, delta_det, delta_delta_det, b,
              delta_b);
    } else {
        float rw = data->m23 * (y + float(0.5)) + data->m33 +
                   data->m13 * (x + float(0.5));

        while (buffer < end) {
            if (rw == 0) {
                *buffer = 0;
            } else {
                float invRw = 1 / rw;
                float gx = rx * invRw - data->mGradient.radial.fx;
                float gy = ry * invRw - data->mGradient.radial.fy;
                float b = 2 * (op->radial.dr * data->mGradient.radial.fradius +
                               gx * op->radial.dx + gy * op->radial.dy);
                float det = radialDeterminant(
                    op->radial.a, b, op->radial.sqrfr - (gx * gx + gy * gy));

                uint32_t result = 0;
                if (det >= 0) {
                    float detSqrt = std::sqrt(det);

                    float s0 = (-b - detSqrt) * op->radial.inv2a;
                    float s1 = (-b + detSqrt) * op->radial.inv2a;

                    float s = vMax(s0, s1);

                    if (data->mGradient.radial.fradius + op->radial.dr * s >= 0)
                        result = gradientPixel(&data->mGradient, s);
                }

                *buffer = result;
            }

            rx += data->m11;
            ry += data->m12;
            rw += data->m13;

            ++buffer;
        }
    }
}

static inline Operator getOperator(const VSpanData *data, const VRle::Span *,
                                   size_t)
{
    Operator op;
    bool     solidSource = false;

    switch (data->mType) {
    case VSpanData::Type::Solid:
        solidSource = (vAlpha(data->mSolid) == 255);
        op.srcFetch = nullptr;
        break;
    case VSpanData::Type::LinearGradient:
        solidSource = false;
        getLinearGradientValues(&op.linear, data);
        op.srcFetch = &fetch_linear_gradient;
        break;
    case VSpanData::Type::RadialGradient:
        solidSource = false;
        getRadialGradientValues(&op.radial, data);
        op.srcFetch = &fetch_radial_gradient;
        break;
    default:
        op.srcFetch = nullptr;
        break;
    }

    op.mode = data->mCompositionMode;
    if (op.mode == VPainter::CompModeSrcOver && solidSource)
        op.mode = VPainter::CompModeSrc;

    op.funcSolid = functionForModeSolid[op.mode];
    op.func = functionForMode[op.mode];

    return op;
}

static void blendColorARGB(size_t count, const VRle::Span *spans,
                           void *userData)
{
    VSpanData *data = (VSpanData *)(userData);
    Operator   op = getOperator(data, spans, count);
    const uint color = data->mSolid;

    if (op.mode == VPainter::CompModeSrc) {
        // inline for performance
        while (count--) {
            uint *target = data->buffer(spans->x, spans->y);
            if (spans->coverage == 255) {
                memfill32(target, color, spans->len);
            } else {
                uint c = BYTE_MUL(color, spans->coverage);
                int  ialpha = 255 - spans->coverage;
                for (int i = 0; i < spans->len; ++i)
                    target[i] = c + BYTE_MUL(target[i], ialpha);
            }
            ++spans;
        }
        return;
    }

    while (count--) {
        uint *target = data->buffer(spans->x, spans->y);
        op.funcSolid(target, spans->len, color, spans->coverage);
        ++spans;
    }
}

#define BLEND_GRADIENT_BUFFER_SIZE 2048
static void blendGradientARGB(size_t count, const VRle::Span *spans,
                              void *userData)
{
    VSpanData *data = (VSpanData *)(userData);
    Operator   op = getOperator(data, spans, count);

    unsigned int buffer[BLEND_GRADIENT_BUFFER_SIZE];

    if (!op.srcFetch) return;

    while (count--) {
        uint *target = data->buffer(spans->x, spans->y);
        int   length = spans->len;
        while (length) {
            int l = std::min(length, BLEND_GRADIENT_BUFFER_SIZE);
            op.srcFetch(buffer, &op, data, spans->y, spans->x, l);
            op.func(target, buffer, l, spans->coverage);
            target += l;
            length -= l;
        }
        ++spans;
    }
}

template <class T>
constexpr const T &clamp(const T &v, const T &lo, const T &hi)
{
    return v < lo ? lo : hi < v ? hi : v;
}

static const int buffer_size = 1024;
static const int fixed_scale = 1 << 16;
static void      blend_transformed_argb(size_t count, const VRle::Span *spans,
                                        void *userData)
{
    VSpanData *data = reinterpret_cast<VSpanData *>(userData);
    if (data->mBitmap.format != VBitmap::Format::ARGB32_Premultiplied &&
        data->mBitmap.format != VBitmap::Format::ARGB32) {
        //@TODO other formats not yet handled.
        return;
    }

    Operator op = getOperator(data, spans, count);
    uint     buffer[buffer_size];

    const int image_x1 = data->mBitmap.x1;
    const int image_y1 = data->mBitmap.y1;
    const int image_x2 = data->mBitmap.x2 - 1;
    const int image_y2 = data->mBitmap.y2 - 1;

    if (data->fast_matrix) {
        // The increment pr x in the scanline
        int fdx = (int)(data->m11 * fixed_scale);
        int fdy = (int)(data->m12 * fixed_scale);

        while (count--) {
            uint *target = data->buffer(spans->x, spans->y);

            const float cx = spans->x + float(0.5);
            const float cy = spans->y + float(0.5);

            int x =
                int((data->m21 * cy + data->m11 * cx + data->dx) * fixed_scale);
            int y =
                int((data->m22 * cy + data->m12 * cx + data->dy) * fixed_scale);

            int       length = spans->len;
            const int coverage =
                (spans->coverage * data->mBitmap.const_alpha) >> 8;
            while (length) {
                int         l = std::min(length, buffer_size);
                const uint *end = buffer + l;
                uint *      b = buffer;
                while (b < end) {
                    int px = clamp(x >> 16, image_x1, image_x2);
                    int py = clamp(y >> 16, image_y1, image_y2);
                    *b = reinterpret_cast<const uint *>(
                        data->mBitmap.scanLine(py))[px];

                    x += fdx;
                    y += fdy;
                    ++b;
                }
                op.func(target, buffer, l, coverage);
                target += l;
                length -= l;
            }
            ++spans;
        }
    } else {
        const float fdx = data->m11;
        const float fdy = data->m12;
        const float fdw = data->m13;
        while (count--) {
            uint *target = data->buffer(spans->x, spans->y);

            const float cx = spans->x + float(0.5);
            const float cy = spans->y + float(0.5);

            float x = data->m21 * cy + data->m11 * cx + data->dx;
            float y = data->m22 * cy + data->m12 * cx + data->dy;
            float w = data->m23 * cy + data->m13 * cx + data->m33;

            int       length = spans->len;
            const int coverage =
                (spans->coverage * data->mBitmap.const_alpha) >> 8;
            while (length) {
                int         l = std::min(length, buffer_size);
                const uint *end = buffer + l;
                uint *      b = buffer;
                while (b < end) {
                    const float iw = w == 0 ? 1 : 1 / w;
                    const float tx = x * iw;
                    const float ty = y * iw;
                    const int   px =
                        clamp(int(tx) - (tx < 0), image_x1, image_x2);
                    const int py =
                        clamp(int(ty) - (ty < 0), image_y1, image_y2);

                    *b = reinterpret_cast<const uint *>(
                        data->mBitmap.scanLine(py))[px];
                    x += fdx;
                    y += fdy;
                    w += fdw;

                    ++b;
                }
                op.func(target, buffer, l, coverage);
                target += l;
                length -= l;
            }
            ++spans;
        }
    }
}

static void blend_untransformed_argb(size_t count, const VRle::Span *spans,
                                     void *userData)
{
    VSpanData *data = reinterpret_cast<VSpanData *>(userData);
    if (data->mBitmap.format != VBitmap::Format::ARGB32_Premultiplied &&
        data->mBitmap.format != VBitmap::Format::ARGB32) {
        //@TODO other formats not yet handled.
        return;
    }

    Operator op = getOperator(data, spans, count);

    const int image_width = data->mBitmap.width;
    const int image_height = data->mBitmap.height;

    int xoff = data->dx;
    int yoff = data->dy;

    while (count--) {
        int x = spans->x;
        int length = spans->len;
        int sx = xoff + x;
        int sy = yoff + spans->y;
        if (sy >= 0 && sy < image_height && sx < image_width) {
            if (sx < 0) {
                x -= sx;
                length += sx;
                sx = 0;
            }
            if (sx + length > image_width) length = image_width - sx;
            if (length > 0) {
                const int coverage =
                    (spans->coverage * data->mBitmap.const_alpha) >> 8;
                const uint *src = (const uint *)data->mBitmap.scanLine(sy) + sx;
                uint *      dest = data->buffer(x, spans->y);
                op.func(dest, src, length, coverage);
            }
        }
        ++spans;
    }
}

void VSpanData::setup(const VBrush &brush, VPainter::CompositionMode /*mode*/,
                      int /*alpha*/)
{
    transformType = VMatrix::MatrixType::None;

    switch (brush.type()) {
    case VBrush::Type::NoBrush:
        mType = VSpanData::Type::None;
        break;
    case VBrush::Type::Solid:
        mType = VSpanData::Type::Solid;
        mSolid = brush.mColor.premulARGB();
        break;
    case VBrush::Type::LinearGradient: {
        mType = VSpanData::Type::LinearGradient;
        mColorTable = VGradientCacheInstance.getBuffer(*brush.mGradient);
        mGradient.mColorTable = mColorTable->buffer32;
        mGradient.mColorTableAlpha = mColorTable->alpha;
        mGradient.linear.x1 = brush.mGradient->linear.x1;
        mGradient.linear.y1 = brush.mGradient->linear.y1;
        mGradient.linear.x2 = brush.mGradient->linear.x2;
        mGradient.linear.y2 = brush.mGradient->linear.y2;
        mGradient.mSpread = brush.mGradient->mSpread;
        setupMatrix(brush.mGradient->mMatrix);
        break;
    }
    case VBrush::Type::RadialGradient: {
        mType = VSpanData::Type::RadialGradient;
        mColorTable = VGradientCacheInstance.getBuffer(*brush.mGradient);
        mGradient.mColorTable = mColorTable->buffer32;
        mGradient.mColorTableAlpha = mColorTable->alpha;
        mGradient.radial.cx = brush.mGradient->radial.cx;
        mGradient.radial.cy = brush.mGradient->radial.cy;
        mGradient.radial.fx = brush.mGradient->radial.fx;
        mGradient.radial.fy = brush.mGradient->radial.fy;
        mGradient.radial.cradius = brush.mGradient->radial.cradius;
        mGradient.radial.fradius = brush.mGradient->radial.fradius;
        mGradient.mSpread = brush.mGradient->mSpread;
        setupMatrix(brush.mGradient->mMatrix);
        break;
    }
    case VBrush::Type::Texture: {
        mType = VSpanData::Type::Texture;
        initTexture(
            &brush.mTexture, 255, VBitmapData::Plain,
            VRect(0, 0, brush.mTexture.width(), brush.mTexture.height()));
        setupMatrix(brush.mMatrix);
        break;
    }
    default:
        break;
    }
    updateSpanFunc();
}

void VSpanData::setupMatrix(const VMatrix &matrix)
{
    VMatrix inv = matrix.inverted();
    m11 = inv.m11;
    m12 = inv.m12;
    m13 = inv.m13;
    m21 = inv.m21;
    m22 = inv.m22;
    m23 = inv.m23;
    m33 = inv.m33;
    dx = inv.mtx;
    dy = inv.mty;
    transformType = inv.type();

    const bool  affine = inv.isAffine();
    const float f1 = m11 * m11 + m21 * m21;
    const float f2 = m12 * m12 + m22 * m22;
    fast_matrix = affine && f1 < 1e4 && f2 < 1e4 && f1 > (1.0 / 65536) &&
                  f2 > (1.0 / 65536) && fabs(dx) < 1e4 && fabs(dy) < 1e4;
}

void VSpanData::initTexture(const VBitmap *bitmap, int alpha,
                            VBitmapData::Type type, const VRect &sourceRect)
{
    mType = VSpanData::Type::Texture;

    mBitmap.imageData = bitmap->data();
    mBitmap.width = bitmap->width();
    mBitmap.height = bitmap->height();
    mBitmap.bytesPerLine = bitmap->stride();
    mBitmap.format = bitmap->format();
    mBitmap.x1 = sourceRect.x();
    mBitmap.y1 = sourceRect.y();
    mBitmap.x2 = std::min(mBitmap.x1 + sourceRect.width(), mBitmap.width);
    mBitmap.y2 = std::min(mBitmap.y1 + sourceRect.height(), mBitmap.height);

    mBitmap.const_alpha = alpha;
    mBitmap.type = type;

    updateSpanFunc();
}

void VSpanData::updateSpanFunc()
{
    switch (mType) {
    case VSpanData::Type::None:
        mUnclippedBlendFunc = nullptr;
        break;
    case VSpanData::Type::Solid:
        mUnclippedBlendFunc = &blendColorARGB;
        break;
    case VSpanData::Type::LinearGradient:
    case VSpanData::Type::RadialGradient: {
        mUnclippedBlendFunc = &blendGradientARGB;
        break;
    }
    case VSpanData::Type::Texture: {
        //@TODO update proper image function.
        if (transformType <= VMatrix::MatrixType::Translate) {
            mUnclippedBlendFunc = &blend_untransformed_argb;
        } else {
            mUnclippedBlendFunc = &blend_transformed_argb;
        }
        break;
    }
    }
}

#if !defined(__ARM_NEON__) && !defined(__ARM64_NEON__)

void memfill32(uint32_t *dest, uint32_t value, int length)
{
    int n;

    if (length <= 0) return;

    // Cute hack to align future memcopy operation
    // and do unroll the loop a bit. Not sure it is
    // the most efficient, but will do for now.
    n = (length + 7) / 8;
    switch (length & 0x07) {
    case 0:
        do {
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 7:
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 6:
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 5:
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 4:
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 3:
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 2:
            *dest++ = value;
            VECTOR_FALLTHROUGH;
        case 1:
            *dest++ = value;
        } while (--n > 0);
    }
}
#endif

void vInitDrawhelperFunctions()
{
    vInitBlendFunctions();

#if defined(__ARM_NEON__) || defined(__ARM64_NEON__)
    // update fast path for NEON
    extern void comp_func_solid_SourceOver_neon(
        uint32_t * dest, int length, uint32_t color, uint32_t const_alpha);

    COMP_functionForModeSolid_C[VPainter::CompModeSrcOver] =
        comp_func_solid_SourceOver_neon;
#endif
}

V_CONSTRUCTOR_FUNCTION(vInitDrawhelperFunctions)
