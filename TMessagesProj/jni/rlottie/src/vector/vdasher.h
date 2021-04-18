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

#ifndef VDASHER_H
#define VDASHER_H
#include "vpath.h"

V_BEGIN_NAMESPACE

class VDasher {
public:
    VDasher(const float *dashArray, size_t size);
    VPath dashed(const VPath &path);
    void dashed(const VPath &path, VPath &result);

private:
    void moveTo(const VPointF &p);
    void lineTo(const VPointF &p);
    void cubicTo(const VPointF &cp1, const VPointF &cp2, const VPointF &e);
    void close();
    void addLine(const VPointF &p);
    void addCubic(const VPointF &cp1, const VPointF &cp2, const VPointF &e);
    void updateActiveSegment();

private:
    void dashHelper(const VPath &path, VPath &result);
    struct Dash {
        float length;
        float gap;
    };
    const VDasher::Dash *mDashArray;
    size_t               mArraySize{0};
    VPointF              mCurPt;
    size_t               mIndex{0}; /* index to the dash Array */
    float                mCurrentLength;
    float                mDashOffset{0};
    VPath               *mResult{nullptr};
    bool                 mDiscard{false};
    bool                 mStartNewSegment{true};
    bool                 mNoLength{true};
    bool                 mNoGap{true};
};

V_END_NAMESPACE

#endif  // VDASHER_H
