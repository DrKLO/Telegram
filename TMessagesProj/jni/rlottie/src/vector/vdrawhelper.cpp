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

#include "vdrawhelper.h"
#include <algorithm>
#include <climits>
#include <cstring>
#include <mutex>
#include <unordered_map>
#include <array>

static RenderFuncTable RenderTable;

void VTextureData::setClip(const VRect &clip)
{
    left = clip.left();
    top = clip.top();
    right = std::min(clip.right(), int(width())) - 1;
    bottom = std::min(clip.bottom(), int(height())) - 1;
}

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
            hash_val +=
                VCacheKey(stops[i].second.premulARGB() * gradient.alpha());

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

    static VGradientCache &instance()
    {
        static VGradientCache CACHE;
        return CACHE;
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

private:
    VGradientCache() = default;

    VGradientColorTableHash mCache;
    std::mutex              mMutex;
};

bool VGradientCache::generateGradientColorTable(const VGradientStops &stops,
                                                float                 opacity,
                                                uint32_t *colorTable, int size)
{
    int                  dist, idist, pos = 0;
    size_t               i;
    bool                 alpha = false;
    size_t               stopCount = stops.size();
    const VGradientStop *curr, *next, *start;
    uint32_t             curColor, nextColor;
    float                delta, t, incr, fpos;

    if (!vCompare(opacity, 1.0f)) alpha = true;

    start = stops.data();
    curr = start;
    if (!curr->second.isOpaque()) alpha = true;
    curColor = curr->second.premulARGB(opacity);
    incr = 1.0f / (float)size;
    fpos = 1.5f * incr;

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
                interpolate_pixel(curColor, idist, nextColor, dist);
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

void VRasterBuffer::clear()
{
    memset(mBuffer, 0, mHeight * mBytesPerLine);
}

VBitmap::Format VRasterBuffer::prepare(const VBitmap *image)
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
    setDrawRegion(VRect(0, 0, int(image->width()), int(image->height())));
    mType = VSpanData::Type::None;
    mBlendFunc = nullptr;
    mUnclippedBlendFunc = nullptr;
}

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
            float xt = rx / rw;
            float yt = ry / rw;
            t = (op->linear.dx * xt + op->linear.dy * yt) + op->linear.off;

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

static inline Operator getOperator(const VSpanData *data)
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

    op.mode = data->mBlendMode;
    if (op.mode == BlendMode::SrcOver && solidSource) op.mode = BlendMode::Src;

    op.funcSolid = RenderTable.color(op.mode);
    op.func = RenderTable.src(op.mode);

    return op;
}

static void blend_color(size_t size, const VRle::Span *array, void *userData)
{
    VSpanData *data = (VSpanData *)(userData);
    Operator   op = getOperator(data);
    const uint color = data->mSolid;

    for (size_t i = 0 ; i < size; ++i) {
        const auto &span = array[i];
        op.funcSolid(data->buffer(span.x, span.y), span.len, color, span.coverage);
    }
}

// Signature of Process Object
//  void Pocess(uint* scratchBuffer, size_t x, size_t y, uchar cov)
template <class Process>
static inline void process_in_chunk(const VRle::Span *array, size_t size,
                                    Process process)
{
    std::array<uint, 2048> buf;
    for (size_t i = 0; i < size; i++) {
        const auto &span = array[i];
        size_t      len = span.len;
        auto        x = span.x;
        while (len) {
            auto l = std::min(len, buf.size());
            process(buf.data(), x, span.y, l, span.coverage);
            x += l;
            len -= l;
        }
    }
}

static void blend_gradient(size_t size, const VRle::Span *array,
                           void *userData)
{
    VSpanData *data = (VSpanData *)(userData);
    Operator   op = getOperator(data);

    if (!op.srcFetch) return;

    process_in_chunk(
        array, size,
        [&](uint *scratch, size_t x, size_t y, size_t len, uchar cov) {
            op.srcFetch(scratch, &op, data, (int)y, (int)x, (int)len);
            op.func(data->buffer((int)x, (int)y), (int)len, scratch, cov);
        });
}

template <class T>
constexpr const T &clamp(const T &v, const T &lo, const T &hi)
{
    return v < lo ? lo : hi < v ? hi : v;
}

static constexpr inline uchar alpha_mul(uchar a, uchar b)
{
    return ((a * b) >> 8);
}

static void blend_image_xform(size_t size, const VRle::Span *array,
                              void *userData)
{
    const auto  data = reinterpret_cast<const VSpanData *>(userData);
    const auto &src = data->texture();

    if (src.format() != VBitmap::Format::ARGB32_Premultiplied &&
        src.format() != VBitmap::Format::ARGB32) {
        //@TODO other formats not yet handled.
        return;
    }

    Operator op = getOperator(data);

    process_in_chunk(
        array, size,
        [&](uint *scratch, size_t x, size_t y, size_t len, uchar cov) {
            const auto  coverage = (cov * src.alpha()) >> 8;
            const float xfactor = y * data->m21 + data->dx + data->m11;
            const float yfactor = y * data->m22 + data->dy + data->m12;
            for (size_t i = 0; i < len; i++) {
                const float fx = (x + i) * data->m11 + xfactor;
                const float fy = (x + i) * data->m12 + yfactor;
                const int   px = clamp(int(fx), src.left, src.right);
                const int   py = clamp(int(fy), src.top, src.bottom);
                scratch[i] = src.pixel(px, py);
            }
            op.func(data->buffer((int)x, (int)y), (int)len, scratch, coverage);
        });
}

static void blend_image(size_t size, const VRle::Span *array, void *userData)
{
    const auto  data = reinterpret_cast<const VSpanData *>(userData);
    const auto &src = data->texture();

    if (src.format() != VBitmap::Format::ARGB32_Premultiplied &&
        src.format() != VBitmap::Format::ARGB32) {
        //@TODO other formats not yet handled.
        return;
    }

    Operator op = getOperator(data);

    for (size_t i = 0; i < size; i++) {
        const auto &span = array[i];
        int         x = span.x;
        int         length = span.len;
        int         sx = x + int(data->dx);
        int         sy = span.y + int(data->dy);

        // notyhing to copy.
        if (sy < 0 || sy >= int(src.height()) || sx >= int(src.width()) ||
            (sx + length) <= 0)
            continue;

        // intersecting left edge of image
        if (sx < 0) {
            x -= sx;
            length += sx;
            sx = 0;
        }
        // intersecting right edge of image
        if (sx + length > int(src.width())) length = (int)src.width() - sx;

        op.func(data->buffer(x, span.y), length, src.pixelRef(sx, sy),
                alpha_mul(span.coverage, src.alpha()));
    }
}

void VSpanData::setup(const VBrush &brush, BlendMode /*mode*/, int /*alpha*/)
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
        mColorTable = VGradientCache::instance().getBuffer(*brush.mGradient);
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
        mColorTable = VGradientCache::instance().getBuffer(*brush.mGradient);
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
        initTexture(&brush.mTexture->mBitmap, brush.mTexture->mAlpha,
                    brush.mTexture->mBitmap.rect());
        setupMatrix(brush.mTexture->mMatrix);
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
                            const VRect &sourceRect)
{
    mType = VSpanData::Type::Texture;
    mTexture.prepare(bitmap);
    mTexture.setClip(sourceRect);
    mTexture.setAlpha(alpha);
    updateSpanFunc();
}

void VSpanData::updateSpanFunc()
{
    switch (mType) {
    case VSpanData::Type::None:
        mUnclippedBlendFunc = nullptr;
        break;
    case VSpanData::Type::Solid:
        mUnclippedBlendFunc = &blend_color;
        break;
    case VSpanData::Type::LinearGradient:
    case VSpanData::Type::RadialGradient: {
        mUnclippedBlendFunc = &blend_gradient;
        break;
    }
    case VSpanData::Type::Texture: {
        //@TODO update proper image function.
        if (transformType <= VMatrix::MatrixType::Translate) {
            mUnclippedBlendFunc = &blend_image;
        } else {
            mUnclippedBlendFunc = &blend_image_xform;
        }
        break;
    }
    }
}

#if !defined(__SSE2__) && !defined(__ARM_NEON__) && !defined(__ARM64_NEON__)
void memfill32(uint32_t *dest, uint32_t value, int length)
{
    // let compiler do the auto vectorization.
    for (int i = 0 ; i < length; i++) {
        *dest++ = value;
    }
}
#endif

