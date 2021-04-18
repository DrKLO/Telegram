/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef VRLE_H
#define VRLE_H

#include <vector>
#include "vcowptr.h"
#include "vglobal.h"
#include "vpoint.h"
#include "vrect.h"

V_BEGIN_NAMESPACE

class VRle {
public:
    struct Span {
        short  x{0};
        short  y{0};
        ushort len{0};
        uchar  coverage{0};
    };
    using VRleSpanCb = void (*)(size_t count, const VRle::Span *spans,
                                void *userData);
    bool  empty() const { return d->empty(); }
    VRect boundingRect() const { return d->bbox(); }
    void  setBoundingRect(const VRect &bbox) { d->setBbox(bbox); }
    void  addSpan(const VRle::Span *span, size_t count)
    {
        d.write().addSpan(span, count);
    }

    void reset() { d.write().reset(); }
    void translate(const VPoint &p) { d.write().translate(p); }

    void operator*=(uchar alpha) { d.write() *= alpha; }

    void intersect(const VRect &r, VRleSpanCb cb, void *userData) const;
    void intersect(const VRle &rle, VRleSpanCb cb, void *userData) const;

    void operator&=(const VRle &o);
    VRle operator&(const VRle &o) const;
    VRle operator-(const VRle &o) const;
    VRle operator+(const VRle &o) const { return opGeneric(o, Data::Op::Add); }
    VRle operator^(const VRle &o) const { return opGeneric(o, Data::Op::Xor); }

    friend VRle operator-(const VRect &rect, const VRle &o);
    friend VRle operator&(const VRect &rect, const VRle &o);

    bool   unique() const { return d.unique(); }
    size_t refCount() const { return d.refCount(); }
    void   clone(const VRle &o) { d.write().clone(o.d.read()); }

public:
    struct View {
        Span * _data;
        size_t _size;
        View(const Span *data, size_t sz) : _data((Span *)data), _size(sz) {}
        Span * data() { return _data; }
        size_t size() { return _size; }
    };
    struct Data {
        enum class Op { Add, Xor, Substract };
        VRle::View view() const
        {
            return VRle::View(mSpans.data(), mSpans.size());
        }
        bool  empty() const { return mSpans.empty(); }
        void  addSpan(const VRle::Span *span, size_t count);
        void  updateBbox() const;
        VRect bbox() const;
        void  setBbox(const VRect &bbox) const;
        void  reset();
        void  translate(const VPoint &p);
        void  operator*=(uchar alpha);
        void  opGeneric(const VRle::Data &, const VRle::Data &, Op code);
        void  opSubstract(const VRle::Data &, const VRle::Data &);
        void  opIntersect(VRle::View a, VRle::View b);
        void  opIntersect(const VRect &, VRle::VRleSpanCb, void *) const;
        void  addRect(const VRect &rect);
        void  clone(const VRle::Data &);

        std::vector<VRle::Span> mSpans;
        VPoint                  mOffset;
        mutable VRect           mBbox;
        mutable bool            mBboxDirty = true;
    };

private:
    VRle opGeneric(const VRle &o, Data::Op opcode) const;

    vcow_ptr<Data> d;
};

inline void VRle::intersect(const VRect &r, VRleSpanCb cb, void *userData) const
{
    d->opIntersect(r, cb, userData);
}

V_END_NAMESPACE

#endif  // VRLE_H
