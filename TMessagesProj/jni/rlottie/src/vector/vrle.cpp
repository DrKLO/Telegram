/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in
 all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "vrle.h"
#include <vrect.h>
#include <algorithm>
#include <array>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <vector>
#include "vdebug.h"
#include "vglobal.h"

V_BEGIN_NAMESPACE

using Result = std::array<VRle::Span, 255>;
using rle_view = VRle::View;
static size_t _opGeneric(rle_view &a, rle_view &b, Result &result,
                         VRle::Data::Op op);
static size_t _opIntersect(const VRect &, rle_view &, Result &);
static size_t _opIntersect(rle_view &, rle_view &, Result &);

static inline uchar divBy255(int x)
{
    return (x + (x >> 8) + 0x80) >> 8;
}

inline static void copy(const VRle::Span *span, size_t count,
                        std::vector<VRle::Span> &v)
{
    // make sure enough memory available
    if (v.capacity() < v.size() + count) v.reserve(v.size() + count);
    std::copy(span, span + count, back_inserter(v));
}

void VRle::Data::addSpan(const VRle::Span *span, size_t count)
{
    copy(span, count, mSpans);
    mBboxDirty = true;
}

VRect VRle::Data::bbox() const
{
    updateBbox();
    return mBbox;
}

void VRle::Data::setBbox(const VRect &bbox) const
{
    mBboxDirty = false;
    mBbox = bbox;
}

void VRle::Data::reset()
{
    mSpans.clear();
    mBbox = VRect();
    mOffset = VPoint();
    mBboxDirty = false;
}

void VRle::Data::clone(const VRle::Data &o)
{
    *this = o;
}

void VRle::Data::translate(const VPoint &p)
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

void VRle::Data::addRect(const VRect &rect)
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
    mBbox = rect;
}

void VRle::Data::updateBbox() const
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

void VRle::Data::operator*=(uchar alpha)
{
    for (auto &i : mSpans) {
        i.coverage = divBy255(i.coverage * alpha);
    }
}

void VRle::Data::opIntersect(const VRect &r, VRle::VRleSpanCb cb,
                             void *userData) const
{
    if (empty()) return;

    if (r.contains(bbox())) {
        cb(mSpans.size(), mSpans.data(), userData);
        return;
    }

    auto   obj = view();
    Result result;
    // run till all the spans are processed
    while (obj.size()) {
        auto count = _opIntersect(r, obj, result);
        if (count) cb(count, result.data(), userData);
    }
}

// res = a - b;
void VRle::Data::opSubstract(const VRle::Data &aObj, const VRle::Data &bObj)
{
    // if two rle are disjoint
    if (!aObj.bbox().intersects(bObj.bbox())) {
        mSpans = aObj.mSpans;
    } else {
        auto a = aObj.view();
        auto b = bObj.view();

        auto aPtr = a.data();
        auto aEnd = a.data() + a.size();
        auto bPtr = b.data();
        auto bEnd = b.data() + b.size();

        // 1. forward a till it intersects with b
        while ((aPtr != aEnd) && (aPtr->y < bPtr->y)) aPtr++;
        auto count = aPtr - a.data();
        if (count) copy(a.data(), count, mSpans);

        // 2. forward b till it intersects with a
        if (aPtr != aEnd)
            while ((bPtr != bEnd) && (bPtr->y < aPtr->y)) bPtr++;

        // update a and b object
        a = {aPtr, size_t(aEnd - aPtr)};
        b = {bPtr, size_t(bEnd - bPtr)};

        // 3. calculate the intersect region
        Result result;

        // run till all the spans are processed
        while (a.size() && b.size()) {
            auto count = _opGeneric(a, b, result, Op::Substract);
            if (count) copy(result.data(), count, mSpans);
        }

        // 4. copy the rest of a
        if (a.size()) copy(a.data(), a.size(), mSpans);
    }

    mBboxDirty = true;
}

void VRle::Data::opGeneric(const VRle::Data &aObj, const VRle::Data &bObj,
                           Op op)
{
    // This routine assumes, obj1(span_y) < obj2(span_y).

    auto a = aObj.view();
    auto b = bObj.view();

    // reserve some space for the result vector.
    mSpans.reserve(a.size() + b.size());

    // if two rle are disjoint
    if (!aObj.bbox().intersects(bObj.bbox())) {
        if (a.data()[0].y < b.data()[0].y) {
            copy(a.data(), a.size(), mSpans);
            copy(b.data(), b.size(), mSpans);
        } else {
            copy(b.data(), b.size(), mSpans);
            copy(a.data(), a.size(), mSpans);
        }
    } else {
        auto aPtr = a.data();
        auto aEnd = a.data() + a.size();
        auto bPtr = b.data();
        auto bEnd = b.data() + b.size();

        // 1. forward a till it intersects with b
        while ((aPtr != aEnd) && (aPtr->y < bPtr->y)) aPtr++;

        auto count = aPtr - a.data();
        if (count) copy(a.data(), count, mSpans);

        // 2. forward b till it intersects with a
        if (aPtr != aEnd)
            while ((bPtr != bEnd) && (bPtr->y < aPtr->y)) bPtr++;

        count = bPtr - b.data();
        if (count) copy(b.data(), count, mSpans);

        // update a and b object
        a = {aPtr, size_t(aEnd - aPtr)};
        b = {bPtr, size_t(bEnd - bPtr)};

        // 3. calculate the intersect region
        Result result;

        // run till all the spans are processed
        while (a.size() && b.size()) {
            auto count = _opGeneric(a, b, result, op);
            if (count) copy(result.data(), count, mSpans);
        }
        // 3. copy the rest
        if (b.size()) copy(b.data(), b.size(), mSpans);
        if (a.size()) copy(a.data(), a.size(), mSpans);
    }

    mBboxDirty = true;
}

static inline V_ALWAYS_INLINE void _opIntersectPrepare(VRle::View &a,
                                                       VRle::View &b)
{
    auto aPtr = a.data();
    auto aEnd = a.data() + a.size();
    auto bPtr = b.data();
    auto bEnd = b.data() + b.size();

    // 1. advance a till it intersects with b
    while ((aPtr != aEnd) && (aPtr->y < bPtr->y)) aPtr++;

    // 2. advance b till it intersects with a
    if (aPtr != aEnd)
        while ((bPtr != bEnd) && (bPtr->y < aPtr->y)) bPtr++;

    // update a and b object
    a = {aPtr, size_t(aEnd - aPtr)};
    b = {bPtr, size_t(bEnd - bPtr)};
}

void VRle::Data::opIntersect(VRle::View a, VRle::View b)
{
    _opIntersectPrepare(a, b);
    Result result;
    while (a.size()) {
        auto count = _opIntersect(a, b, result);
        if (count) copy(result.data(), count, mSpans);
    }

    updateBbox();
}

static void _opIntersect(rle_view a, rle_view b, VRle::VRleSpanCb cb,
                         void *userData)
{
    if (!cb) return;

    _opIntersectPrepare(a, b);
    Result result;
    while (a.size()) {
        auto count = _opIntersect(a, b, result);
        if (count) cb(count, result.data(), userData);
    }
}

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

static size_t _opIntersect(rle_view &obj, rle_view &clip, Result &result)
{
    auto out = result.data();
    auto available = result.max_size();
    auto spans = obj.data();
    auto end = obj.data() + obj.size();
    auto clipSpans = clip.data();
    auto clipEnd = clip.data() + clip.size();
    int  sx1, sx2, cx1, cx2, x, len;

    while (available && spans < end) {
        if (clipSpans >= clipEnd) {
            spans = end;
            break;
        }
        if (clipSpans->y > spans->y) {
            ++spans;
            continue;
        }
        if (spans->y != clipSpans->y) {
            ++clipSpans;
            continue;
        }
        // assert(spans->y == (clipSpans->y + clip_offset_y));
        sx1 = spans->x;
        sx2 = sx1 + spans->len;
        cx1 = clipSpans->x;
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

    // update the obj view yet to be processed
    obj = {spans, size_t(end - spans)};

    // update the clip view yet to be processed
    clip = {clipSpans, size_t(clipEnd - clipSpans)};

    return result.max_size() - available;
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
static size_t _opIntersect(const VRect &clip, rle_view &obj, Result &result)
{
    auto out = result.data();
    auto available = result.max_size();
    auto ptr = obj.data();
    auto end = obj.data() + obj.size();

    const auto minx = clip.left();
    const auto miny = clip.top();
    const auto maxx = clip.right() - 1;
    const auto maxy = clip.bottom() - 1;

    while (available && ptr < end) {
        const auto &span = *ptr;
        if (span.y > maxy) {
            ptr = end;  // update spans so that we can breakout
            break;
        }
        if (span.y < miny || span.x > maxx || span.x + span.len <= minx) {
            ++ptr;
            continue;
        }
        if (span.x < minx) {
            out->len = std::min(span.len - (minx - span.x), maxx - minx + 1);
            out->x = minx;
        } else {
            out->x = span.x;
            out->len = std::min(span.len, ushort(maxx - span.x + 1));
        }
        if (out->len != 0) {
            out->y = span.y;
            out->coverage = span.coverage;
            ++out;
            --available;
        }
        ++ptr;
    }

    // update the span list that yet to be processed
    obj = {ptr, size_t(end - ptr)};

    return result.max_size() - available;
}

static void blitXor(VRle::Span *spans, int count, uchar *buffer, int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
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

static void blitDestinationOut(VRle::Span *spans, int count, uchar *buffer,
                               int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        uchar *ptr = buffer + x;
        while (l--) {
            *ptr = divBy255((255 - spans->coverage) * (*ptr));
            ptr++;
        }
        spans++;
    }
}

static void blitSrcOver(VRle::Span *spans, int count, uchar *buffer,
                        int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        uchar *ptr = buffer + x;
        while (l--) {
            *ptr = spans->coverage + divBy255((255 - spans->coverage) * (*ptr));
            ptr++;
        }
        spans++;
    }
}

void blitSrc(VRle::Span *spans, int count, uchar *buffer, int offsetX)
{
    while (count--) {
        int    x = spans->x + offsetX;
        int    l = spans->len;
        uchar *ptr = buffer + x;
        while (l--) {
            *ptr = std::max(spans->coverage, *ptr);
            ptr++;
        }
        spans++;
    }
}

size_t bufferToRle(uchar *buffer, int size, int offsetX, int y, VRle::Span *out)
{
    size_t count = 0;
    uchar  value = buffer[0];
    int    curIndex = 0;

    // size = offsetX < 0 ? size + offsetX : size;
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

struct SpanMerger {
    explicit SpanMerger(VRle::Data::Op op)
    {
        switch (op) {
        case VRle::Data::Op::Add:
            _blitter = &blitSrcOver;
            break;
        case VRle::Data::Op::Xor:
            _blitter = &blitXor;
            break;
        case VRle::Data::Op::Substract:
            _blitter = &blitDestinationOut;
            break;
        }
    }
    using blitter = void (*)(VRle::Span *, int, uchar *, int);
    blitter                     _blitter;
    std::array<VRle::Span, 256> _result;
    std::array<uchar, 1024>     _buffer;
    VRle::Span *                _aStart{nullptr};
    VRle::Span *                _bStart{nullptr};

    void revert(VRle::Span *&aPtr, VRle::Span *&bPtr)
    {
        aPtr = _aStart;
        bPtr = _bStart;
    }
    VRle::Span *data() { return _result.data(); }
    size_t merge(VRle::Span *&aPtr, const VRle::Span *aEnd, VRle::Span *&bPtr,
                 const VRle::Span *bEnd);
};

size_t SpanMerger::merge(VRle::Span *&aPtr, const VRle::Span *aEnd,
                         VRle::Span *&bPtr, const VRle::Span *bEnd)
{
    assert(aPtr->y == bPtr->y);

    _aStart = aPtr;
    _bStart = bPtr;
    int lb = std::min(aPtr->x, bPtr->x);
    int y = aPtr->y;

    while (aPtr < aEnd && aPtr->y == y) aPtr++;
    while (bPtr < bEnd && bPtr->y == y) bPtr++;

    int ub = std::max((aPtr - 1)->x + (aPtr - 1)->len,
                      (bPtr - 1)->x + (bPtr - 1)->len);
    int length = (lb < 0) ? ub + lb : ub - lb;

    if (length <= 0 || size_t(length) >= _buffer.max_size()) {
        // can't handle merge . skip
        return 0;
    }

    // clear buffer
    memset(_buffer.data(), 0, length);

    // blit a to buffer
    blitSrc(_aStart, aPtr - _aStart, _buffer.data(), -lb);

    // blit b to buffer
    _blitter(_bStart, bPtr - _bStart, _buffer.data(), -lb);

    // convert buffer to span
    return bufferToRle(_buffer.data(), length, lb, y, _result.data());
}

static size_t _opGeneric(rle_view &a, rle_view &b, Result &result,
                         VRle::Data::Op op)
{
    SpanMerger merger{op};

    auto   out = result.data();
    size_t available = result.max_size();
    auto   aPtr = a.data();
    auto   aEnd = a.data() + a.size();
    auto   bPtr = b.data();
    auto   bEnd = b.data() + b.size();

    // only logic change for substract operation.
    const bool keep = op != (VRle::Data::Op::Substract);

    while (available && aPtr < aEnd && bPtr < bEnd) {
        if (aPtr->y < bPtr->y) {
            *out++ = *aPtr++;
            available--;
        } else if (bPtr->y < aPtr->y) {
            if (keep) {
                *out++ = *bPtr;
                available--;
            }
            bPtr++;
        } else {  // same y
            auto count = merger.merge(aPtr, aEnd, bPtr, bEnd);
            if (available >= count) {
                if (count) {
                    memcpy(out, merger.data(), count * sizeof(VRle::Span));
                    out += count;
                    available -= count;
                }
            } else {
                // not enough space try next time.
                merger.revert(aPtr, bPtr);
                break;
            }
        }
    }
    // update the span list that yet to be processed
    a = {aPtr, size_t(aEnd - aPtr)};
    b = {bPtr, size_t(bEnd - bPtr)};

    return result.max_size() - available;
}

/*
 * this api makes use of thread_local temporary
 * buffer to avoid creating intermediate temporary rle buffer
 * the scratch buffer object will grow its size on demand
 * so that future call won't need any more memory allocation.
 * this function is thread safe as it uses thread_local variable
 * which is unique per thread.
 */
static vthread_local VRle::Data Scratch_Object;

VRle VRle::opGeneric(const VRle &o, Data::Op op) const
{
    if (empty()) return o;
    if (o.empty()) return *this;

    Scratch_Object.reset();
    Scratch_Object.opGeneric(d.read(), o.d.read(), op);

    VRle result;
    result.d.write() = Scratch_Object;

    return result;
}

VRle VRle::operator-(const VRle &o) const
{
    if (empty()) return {};
    if (o.empty()) return *this;

    Scratch_Object.reset();
    Scratch_Object.opSubstract(d.read(), o.d.read());

    VRle result;
    result.d.write() = Scratch_Object;

    return result;
}

VRle VRle::operator&(const VRle &o) const
{
    if (empty() || o.empty()) return {};

    Scratch_Object.reset();
    Scratch_Object.opIntersect(d.read().view(), o.d.read().view());

    VRle result;
    result.d.write() = Scratch_Object;

    return result;
}

void VRle::operator&=(const VRle &o)
{
    if (empty()) return;
    if (o.empty()) {
        reset();
        return;
    }
    Scratch_Object.reset();
    Scratch_Object.opIntersect(d.read().view(), o.d.read().view());
    d.write() = Scratch_Object;
}

VRle operator-(const VRect &rect, const VRle &o)
{
    if (rect.empty()) return {};

    Scratch_Object.reset();
    Scratch_Object.addRect(rect);

    VRle result;
    result.d.write().opSubstract(Scratch_Object, o.d.read());

    return result;
}

VRle operator&(const VRect &rect, const VRle &o)
{
    if (rect.empty() || o.empty()) return {};

    Scratch_Object.reset();
    Scratch_Object.addRect(rect);

    VRle result;
    result.d.write().opIntersect(Scratch_Object.view(), o.d.read().view());

    return result;
}

void VRle::intersect(const VRle &clip, VRleSpanCb cb, void *userData) const
{
    if (empty() || clip.empty()) return;

    _opIntersect(d.read().view(), clip.d.read().view(), cb, userData);
}

V_END_NAMESPACE
