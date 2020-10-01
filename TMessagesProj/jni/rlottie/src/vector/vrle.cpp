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

#include "vrle.h"
#include <vrect.h>
#include <algorithm>
#include <array>
#include <cstdlib>
#include <vector>
#include "vdebug.h"
#include "vglobal.h"
#include "vregion.h"

V_BEGIN_NAMESPACE

enum class Operation { Add, Xor };

struct VRleHelper {
    size_t      alloc;
    size_t      size;
    VRle::Span *spans;
};
static void rleIntersectWithRle(VRleHelper *, int, int, VRleHelper *,
                                VRleHelper *);
static void rleIntersectWithRect(const VRect &, VRleHelper *, VRleHelper *);
static void rleOpGeneric(VRleHelper *, VRleHelper *, VRleHelper *,
                         Operation op);
static void rleSubstractWithRle(VRleHelper *, VRleHelper *, VRleHelper *);

static inline uchar divBy255(int x)
{
    return (x + (x >> 8) + 0x80) >> 8;
}

inline static void copyArrayToVector(const VRle::Span *span, size_t count,
                                     std::vector<VRle::Span> &v)
{
    // make sure enough memory available
    if (v.capacity() < v.size() + count) v.reserve(v.size() + count);
    std::copy(span, span + count, back_inserter(v));
}

void VRle::VRleData::addSpan(const VRle::Span *span, size_t count)
{
    copyArrayToVector(span, count, mSpans);
    mBboxDirty = true;
}

VRect VRle::VRleData::bbox() const
{
    updateBbox();
    return mBbox;
}

void VRle::VRleData::setBbox(const VRect &bbox) const
{
    mBboxDirty = false;
    mBbox = bbox;
}

void VRle::VRleData::reset()
{
    mSpans.clear();
    mBbox = VRect();
    mOffset = VPoint();
    mBboxDirty = false;
}

void VRle::VRleData::clone(const VRle::VRleData &o)
{
    *this = o;
}

void VRle::VRleData::translate(const VPoint &p)
{
    // take care of last offset if applied
    mOffset = p - mOffset;
    int x = mOffset.x();
    int y = mOffset.y();
    for (auto &i : mSpans) {
        i.x = i.x + x;
        i.y = i.y + y;
    }
    updateBbox();
    mBbox.translate(mOffset.x(), mOffset.y());
}

void VRle::VRleData::addRect(const VRect &rect)
{
    int x = rect.left();
    int y = rect.top();
    int width = rect.width();
    int height = rect.height();

    mSpans.reserve(size_t(height));

    VRle::Span span;
    for (int i = 0; i < height; i++) {
        span.x = x;
        span.y = y + i;
        span.len = width;
        span.coverage = 255;
        mSpans.push_back(span);
    }
    updateBbox();
}

void VRle::VRleData::updateBbox() const
{
    if (!mBboxDirty) return;

    mBboxDirty = false;

    int               l = std::numeric_limits<int>::max();
    const VRle::Span *span = mSpans.data();

    mBbox = VRect();
    size_t sz = mSpans.size();
    if (sz) {
        int t = span[0].y;
        int b = span[sz - 1].y;
        int r = 0;
        for (size_t i = 0; i < sz; i++) {
            if (span[i].x < l) l = span[i].x;
            if (span[i].x + span[i].len > r) r = span[i].x + span[i].len;
        }
        mBbox = VRect(l, t, r - l, b - t + 1);
    }
}

void VRle::VRleData::invert()
{
    for (auto &i : mSpans) {
        i.coverage = 255 - i.coverage;
    }
}

void VRle::VRleData::operator*=(int alpha)
{
    alpha &= 0xff;
    for (auto &i : mSpans) {
        i.coverage = divBy255(i.coverage * alpha);
    }
}

void VRle::VRleData::opIntersect(const VRect &r, VRle::VRleSpanCb cb,
                                 void *userData) const
{
    if (empty()) return;

    if (r.contains(bbox())) {
        cb(mSpans.size(), mSpans.data(), userData);
        return;
    }

    VRect                       clip = r;
    VRleHelper                  tresult, tmp_obj;
    std::array<VRle::Span, 256> array;

    // setup the tresult object
    tresult.size = array.size();
    tresult.alloc = array.size();
    tresult.spans = array.data();

    // setup tmp object
    tmp_obj.size = mSpans.size();
    tmp_obj.spans = const_cast<VRle::Span *>(mSpans.data());

    // run till all the spans are processed
    while (tmp_obj.size) {
        rleIntersectWithRect(clip, &tmp_obj, &tresult);
        if (tresult.size) {
            cb(tresult.size, tresult.spans, userData);
        }
        tresult.size = 0;
    }
}

// res = a - b;
void VRle::VRleData::opSubstract(const VRle::VRleData &a,
                                 const VRle::VRleData &b)
{
    // if two rle are disjoint
    if (!a.bbox().intersects(b.bbox())) {
        mSpans = a.mSpans;
    } else {
        VRle::Span *      aPtr = const_cast<VRle::Span *>(a.mSpans.data());
        const VRle::Span *aEnd = a.mSpans.data() + a.mSpans.size();
        VRle::Span *      bPtr = const_cast<VRle::Span *>(b.mSpans.data());
        const VRle::Span *bEnd = b.mSpans.data() + b.mSpans.size();

        // 1. forward till both y intersect
        while ((aPtr != aEnd) && (aPtr->y < bPtr->y)) aPtr++;
        size_t sizeA = size_t(aPtr - a.mSpans.data());
        if (sizeA) copyArrayToVector(a.mSpans.data(), sizeA, mSpans);

        // 2. forward b till it intersect with a.
        while ((bPtr != bEnd) && (bPtr->y < aPtr->y)) bPtr++;
        size_t sizeB = size_t(bPtr - b.mSpans.data());

        // 2. calculate the intersect region
        VRleHelper                  tresult, aObj, bObj;
        std::array<VRle::Span, 256> array;

        // setup the tresult object
        tresult.size = array.size();
        tresult.alloc = array.size();
        tresult.spans = array.data();

        // setup a object
        aObj.size = a.mSpans.size() - sizeA;
        aObj.spans = aPtr;

        // setup b object
        bObj.size = b.mSpans.size() - sizeB;
        bObj.spans = bPtr;

        // run till all the spans are processed
        while (aObj.size && bObj.size) {
            rleSubstractWithRle(&aObj, &bObj, &tresult);
            if (tresult.size) {
                copyArrayToVector(tresult.spans, tresult.size, mSpans);
            }
            tresult.size = 0;
        }
        // 3. copy the rest of a
        if (aObj.size) copyArrayToVector(aObj.spans, aObj.size, mSpans);
    }

    mBboxDirty = true;
}

void VRle::VRleData::opGeneric(const VRle::VRleData &a, const VRle::VRleData &b,
                               OpCode code)
{
    // This routine assumes, obj1(span_y) < obj2(span_y).

    // reserve some space for the result vector.
    mSpans.reserve(a.mSpans.size() + b.mSpans.size());

    // if two rle are disjoint
    if (!a.bbox().intersects(b.bbox())) {
        if (a.mSpans[0].y < b.mSpans[0].y) {
            copyArrayToVector(a.mSpans.data(), a.mSpans.size(), mSpans);
            copyArrayToVector(b.mSpans.data(), b.mSpans.size(), mSpans);
        } else {
            copyArrayToVector(b.mSpans.data(), b.mSpans.size(), mSpans);
            copyArrayToVector(a.mSpans.data(), a.mSpans.size(), mSpans);
        }
    } else {
        VRle::Span *      aPtr = const_cast<VRle::Span *>(a.mSpans.data());
        const VRle::Span *aEnd = a.mSpans.data() + a.mSpans.size();
        VRle::Span *      bPtr = const_cast<VRle::Span *>(b.mSpans.data());
        const VRle::Span *bEnd = b.mSpans.data() + b.mSpans.size();

        // 1. forward a till it intersects with b
        while ((aPtr != aEnd) && (aPtr->y < bPtr->y)) aPtr++;
        size_t sizeA = size_t(aPtr - a.mSpans.data());
        if (sizeA) copyArrayToVector(a.mSpans.data(), sizeA, mSpans);

        // 2. forward b till it intersects with a
        while ((bPtr != bEnd) && (bPtr->y < aPtr->y)) bPtr++;
        size_t sizeB = size_t(bPtr - b.mSpans.data());
        if (sizeB) copyArrayToVector(b.mSpans.data(), sizeB, mSpans);

        // 3. calculate the intersect region
        VRleHelper                  tresult, aObj, bObj;
        std::array<VRle::Span, 256> array;

        // setup the tresult object
        tresult.size = array.size();
        tresult.alloc = array.size();
        tresult.spans = array.data();

        // setup a object
        aObj.size = a.mSpans.size() - sizeA;
        aObj.spans = aPtr;

        // setup b object
        bObj.size = b.mSpans.size() - sizeB;
        bObj.spans = bPtr;

        Operation op = Operation::Add;
        switch (code) {
        case OpCode::Add:
            op = Operation::Add;
            break;
        case OpCode::Xor:
            op = Operation::Xor;
            break;
        }
        // run till all the spans are processed
        while (aObj.size && bObj.size) {
            rleOpGeneric(&aObj, &bObj, &tresult, op);
            if (tresult.size) {
                copyArrayToVector(tresult.spans, tresult.size, mSpans);
            }
            tresult.size = 0;
        }
        // 3. copy the rest
        if (bObj.size) copyArrayToVector(bObj.spans, bObj.size, mSpans);
        if (aObj.size) copyArrayToVector(aObj.spans, aObj.size, mSpans);
    }

    // update result bounding box
    VRegion reg(a.bbox());
    reg += b.bbox();
    mBbox = reg.boundingRect();
    mBboxDirty = false;
}

static void rle_cb(size_t count, const VRle::Span *spans, void *userData)
{
    auto vector = static_cast<std::vector<VRle::Span> *>(userData);
    copyArrayToVector(spans, count, *vector);
}

void opIntersectHelper(const VRle::VRleData &obj1, const VRle::VRleData &obj2,
                       VRle::VRleSpanCb cb, void *userData)
{
    VRleHelper                  result, source, clip;
    std::array<VRle::Span, 256> array;

    // setup the tresult object
    result.size = array.size();
    result.alloc = array.size();
    result.spans = array.data();

    // setup tmp object
    source.size = obj1.mSpans.size();
    source.spans = const_cast<VRle::Span *>(obj1.mSpans.data());

    // setup tmp clip object
    clip.size = obj2.mSpans.size();
    clip.spans = const_cast<VRle::Span *>(obj2.mSpans.data());

    // run till all the spans are processed
    while (source.size) {
        rleIntersectWithRle(&clip, 0, 0, &source, &result);
        if (result.size) {
            cb(result.size, result.spans, userData);
        }
        result.size = 0;
    }
}

void VRle::VRleData::opIntersect(const VRle::VRleData &obj1,
                                 const VRle::VRleData &obj2)
{
    opIntersectHelper(obj1, obj2, rle_cb, &mSpans);
    updateBbox();
}

#define VMIN(a, b) ((a) < (b) ? (a) : (b))
#define VMAX(a, b) ((a) > (b) ? (a) : (b))

/*
 * This function will clip a rle list with another rle object
 * tmp_clip  : The rle list that will be use to clip the rle
 * tmp_obj   : holds the list of spans that has to be clipped
 * result    : will hold the result after the processing
 * NOTE: if the algorithm runs out of the result buffer list
 *       it will stop and update the tmp_obj with the span list
 *       that are yet to be processed as well as the tpm_clip object
 *       with the unprocessed clip spans.
 */
static void rleIntersectWithRle(VRleHelper *tmp_clip, int clip_offset_x,
                                int clip_offset_y, VRleHelper *tmp_obj,
                                VRleHelper *result)
{
    VRle::Span *out = result->spans;
    int         available = result->alloc;
    VRle::Span *spans = tmp_obj->spans;
    VRle::Span *end = tmp_obj->spans + tmp_obj->size;
    VRle::Span *clipSpans = tmp_clip->spans;
    VRle::Span *clipEnd = tmp_clip->spans + tmp_clip->size;
    int         sx1, sx2, cx1, cx2, x, len;

    while (available && spans < end) {
        if (clipSpans >= clipEnd) {
            spans = end;
            break;
        }
        if ((clipSpans->y + clip_offset_y) > spans->y) {
            ++spans;
            continue;
        }
        if (spans->y != (clipSpans->y + clip_offset_y)) {
            ++clipSpans;
            continue;
        }
        // assert(spans->y == (clipSpans->y + clip_offset_y));
        sx1 = spans->x;
        sx2 = sx1 + spans->len;
        cx1 = (clipSpans->x + clip_offset_x);
        cx2 = cx1 + clipSpans->len;

        if (cx1 < sx1 && cx2 < sx1) {
            ++clipSpans;
            continue;
        } else if (sx1 < cx1 && sx2 < cx1) {
            ++spans;
            continue;
        }
        x = std::max(sx1, cx1);
        len = std::min(sx2, cx2) - x;
        if (len) {
            out->x = std::max(sx1, cx1);
            out->len = (std::min(sx2, cx2) - out->x);
            out->y = spans->y;
            out->coverage = divBy255(spans->coverage * clipSpans->coverage);
            ++out;
            --available;
        }
        if (sx2 < cx2) {
            ++spans;
        } else {
            ++clipSpans;
        }
    }

    // update the span list that yet to be processed
    tmp_obj->spans = spans;
    tmp_obj->size = end - spans;

    // update the clip list that yet to be processed
    tmp_clip->spans = clipSpans;
    tmp_clip->size = clipEnd - clipSpans;

    // update the result
    result->size = result->alloc - available;
}

/*
 * This function will clip a rle list with a given rect
 * clip      : The clip rect that will be use to clip the rle
 * tmp_obj   : holds the list of spans that has to be clipped
 * result    : will hold the result after the processing
 * NOTE: if the algorithm runs out of the result buffer list
 *       it will stop and update the tmp_obj with the span list
 *       that are yet to be processed
 */
static void rleIntersectWithRect(const VRect &clip, VRleHelper *tmp_obj,
                                 VRleHelper *result)
{
    VRle::Span *out = result->spans;
    int         available = result->alloc;
    VRle::Span *spans = tmp_obj->spans;
    VRle::Span *end = tmp_obj->spans + tmp_obj->size;
    short       minx, miny, maxx, maxy;

    minx = clip.left();
    miny = clip.top();
    maxx = clip.right() - 1;
    maxy = clip.bottom() - 1;

    while (available && spans < end) {
        if (spans->y > maxy) {
            spans = end;  // update spans so that we can breakout
            break;
        }
        if (spans->y < miny || spans->x > maxx ||
            spans->x + spans->len <= minx) {
            ++spans;
            continue;
        }
        if (spans->x < minx) {
            out->len = VMIN(spans->len - (minx - spans->x), maxx - minx + 1);
            out->x = minx;
        } else {
            out->x = spans->x;
            out->len = VMIN(spans->len, (maxx - spans->x + 1));
        }
        if (out->len != 0) {
            out->y = spans->y;
            out->coverage = spans->coverage;
            ++out;
            --available;
        }
        ++spans;
    }

    // update the span list that yet to be processed
    tmp_obj->spans = spans;
    tmp_obj->size = end - spans;

    // update the result
    result->size = result->alloc - available;
}

void blitXor(VRle::Span *spans, int count, uchar *buffer, int len, int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        if (x + l >= len) {
            return;
        }
        uchar *ptr = buffer + x;
        while (l--) {
            int da = *ptr;
            *ptr = divBy255((255 - spans->coverage) * (da) +
                            spans->coverage * (255 - da));
            ptr++;
        }
        spans++;
    }
}

void blitDestinationOut(VRle::Span *spans, int count, uchar *buffer, int len, int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        if (x + l >= len) {
            return;
        }
        uchar *ptr = buffer + x;
        while (l--) {
            *ptr = divBy255((255 - spans->coverage) * (*ptr));
            ptr++;
        }
        spans++;
    }
}

void blitSrcOver(VRle::Span *spans, int count, uchar *buffer, int len, int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        if (x + l >= len) {
            return;
        }
        uchar *ptr = buffer + x;
        while (l--) {
            *ptr = spans->coverage + divBy255((255 - spans->coverage) * (*ptr));
            ptr++;
        }
        spans++;
    }
}

void blit(VRle::Span *spans, int count, uchar *buffer, int len, int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        if (x + l < 0 || x + l > len) {
            return;
        }
        uchar *ptr = buffer + x;
        while (l--) {
            *ptr = std::max(spans->coverage, *ptr);
            ptr++;
        }
        spans++;
    }
}

size_t bufferToRle(uchar *buffer, int len, int size, int offsetX, int y, VRle::Span *out)
{
    size_t count = 0;
    uchar  value = buffer[0];
    int    curIndex = 0;

    size = offsetX < 0 ? size + offsetX : size;
    if (size > len) {
        return count;
    }
    for (int i = 0; i < size; i++) {
        uchar curValue = buffer[0];
        if (value != curValue) {
            if (value) {
                out->y = y;
                out->x = offsetX + curIndex;
                out->len = i - curIndex;
                out->coverage = value;
                out++;
                count++;
            }
            curIndex = i;
            value = curValue;
        }
        buffer++;
    }
    if (value) {
        out->y = y;
        out->x = offsetX + curIndex;
        out->len = size - curIndex;
        out->coverage = value;
        count++;
    }
    return count;
}

static void rleOpGeneric(VRleHelper *a, VRleHelper *b, VRleHelper *result,
                         Operation op)
{
    std::array<VRle::Span, 256> temp;
    VRle::Span *                out = result->spans;
    size_t                      available = result->alloc;
    VRle::Span *                aPtr = a->spans;
    VRle::Span *                aEnd = a->spans + a->size;
    VRle::Span *                bPtr = b->spans;
    VRle::Span *                bEnd = b->spans + b->size;

    while (available && aPtr < aEnd && bPtr < bEnd) {
        if (aPtr->y < bPtr->y) {
            *out++ = *aPtr++;
            available--;
        } else if (bPtr->y < aPtr->y) {
            *out++ = *bPtr++;
            available--;
        } else {  // same y
            VRle::Span *aStart = aPtr;
            VRle::Span *bStart = bPtr;

            int y = aPtr->y;

            while (aPtr < aEnd && aPtr->y == y) aPtr++;
            while (bPtr < bEnd && bPtr->y == y) bPtr++;

            int aLength = (aPtr - 1)->x + (aPtr - 1)->len;
            int bLength = (bPtr - 1)->x + (bPtr - 1)->len;
            int offset = std::min(aStart->x, bStart->x);

            std::array<uchar, 1024> array = {{0}};
            blit(aStart, (aPtr - aStart), array.data(), 1024, -offset);
            if (op == Operation::Add)
                blitSrcOver(bStart, (bPtr - bStart), array.data(), 1024, -offset);
            else if (op == Operation::Xor)
                blitXor(bStart, (bPtr - bStart), array.data(), 1024, -offset);
            VRle::Span *tResult = temp.data();
            size_t size = bufferToRle(array.data(), 1024, std::max(aLength, bLength),
                                      offset, y, tResult);
            if (available >= size) {
                while (size--) {
                    *out++ = *tResult++;
                    available--;
                }
            } else {
                aPtr = aStart;
                bPtr = bStart;
                break;
            }
        }
    }
    // update the span list that yet to be processed
    a->spans = aPtr;
    a->size = aEnd - aPtr;

    // update the clip list that yet to be processed
    b->spans = bPtr;
    b->size = bEnd - bPtr;

    // update the result
    result->size = result->alloc - available;
}

static void rleSubstractWithRle(VRleHelper *a, VRleHelper *b,
                                VRleHelper *result)
{
    std::array<VRle::Span, 256> temp;
    VRle::Span *                out = result->spans;
    size_t                      available = result->alloc;
    VRle::Span *                aPtr = a->spans;
    VRle::Span *                aEnd = a->spans + a->size;
    VRle::Span *                bPtr = b->spans;
    VRle::Span *                bEnd = b->spans + b->size;

    while (available && aPtr < aEnd && bPtr < bEnd) {
        if (aPtr->y < bPtr->y) {
            *out++ = *aPtr++;
            available--;
        } else if (bPtr->y < aPtr->y) {
            bPtr++;
        } else {  // same y
            VRle::Span *aStart = aPtr;
            VRle::Span *bStart = bPtr;

            int y = aPtr->y;

            while (aPtr < aEnd && aPtr->y == y) aPtr++;
            while (bPtr < bEnd && bPtr->y == y) bPtr++;

            int aLength = (aPtr - 1)->x + (aPtr - 1)->len;
            int bLength = (bPtr - 1)->x + (bPtr - 1)->len;
            int offset = std::min(aStart->x, bStart->x);

            std::array<uchar, 1024> array = {{0}};
            blit(aStart, (aPtr - aStart), array.data(), 1024, -offset);
            blitDestinationOut(bStart, (bPtr - bStart), array.data(), 1024, -offset);
            VRle::Span *tResult = temp.data();
            size_t size = bufferToRle(array.data(), 1024, std::max(aLength, bLength),
                                      offset, y, tResult);
            if (available >= size) {
                while (size--) {
                    *out++ = *tResult++;
                    available--;
                }
            } else {
                aPtr = aStart;
                bPtr = bStart;
                break;
            }
        }
    }
    // update the span list that yet to be processed
    a->spans = aPtr;
    a->size = size_t(aEnd - aPtr);

    // update the clip list that yet to be processed
    b->spans = bPtr;
    b->size = size_t(bEnd - bPtr);

    // update the result
    result->size = result->alloc - available;
}

VRle VRle::toRle(const VRect &rect)
{
    if (rect.empty()) return VRle();

    VRle result;
    result.d.write().addRect(rect);
    return result;
}

V_END_NAMESPACE
