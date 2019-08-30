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

#ifndef VDASHER_H
#define VDASHER_H
#include "vpath.h"

V_BEGIN_NAMESPACE

class VDasher {
public:
    VDasher(const float *dashArray, size_t size);
    VPath dashed(const VPath &path);

private:
    void moveTo(const VPointF &p);
    void lineTo(const VPointF &p);
    void cubicTo(const VPointF &cp1, const VPointF &cp2, const VPointF &e);
    void close();
    void addLine(const VPointF &p);
    void addCubic(const VPointF &cp1, const VPointF &cp2, const VPointF &e);
    void updateActiveSegment();

private:
    struct Dash {
        float length;
        float gap;
    };
    const VDasher::Dash *mDashArray;
    size_t               mArraySize{0};
    VPointF              mCurPt;
    size_t               mIndex{0}; /* index to the dash Array */
    float                mCurrentLength;
    bool                 mDiscard;
    float                mDashOffset{0};
    VPath                mResult;
    bool                 mStartNewSegment=true;
};

V_END_NAMESPACE

#endif  // VDASHER_H
