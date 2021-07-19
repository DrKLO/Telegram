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

#ifndef VLINE_H
#define VLINE_H

#include "vglobal.h"
#include "vpoint.h"

V_BEGIN_NAMESPACE

class VLine {
public:
    VLine() = default;
    VLine(float x1, float y1, float x2, float y2)
        : mX1(x1), mY1(y1), mX2(x2), mY2(y2)
    {
    }
    VLine(const VPointF &p1, const VPointF &p2)
        : mX1(p1.x()), mY1(p1.y()), mX2(p2.x()), mY2(p2.y())
    {
    }
    float   length() const { return length(mX1, mY1, mX2, mY2);}
    void    splitAtLength(float length, VLine &left, VLine &right) const;
    VPointF p1() const { return {mX1, mY1}; }
    VPointF p2() const { return {mX2, mY2}; }
    float angle() const;
    static float length(float x1, float y1, float x2, float y2);

private:
    float mX1{0};
    float mY1{0};
    float mX2{0};
    float mY2{0};
};

inline float VLine::angle() const
{
    const float dx = mX2 - mX1;
    const float dy = mY2 - mY1;

    const float theta = std::atan2(dy, dx) * 180.0 / M_PI;
    return theta;
}

// approximate sqrt(x*x + y*y) using alpha max plus beta min algorithm.
// With alpha = 1, beta = 3/8, giving results with the largest error less
// than 7% compared to the exact value.
inline float VLine::length(float x1, float y1, float x2, float y2)
{
    float x = x2 - x1;
    float y = y2 - y1;

    x = x < 0 ? -x : x;
    y = y < 0 ? -y : y;

    return (x > y ? x + 0.375 * y : y + 0.375 * x);
}

inline void VLine::splitAtLength(float lengthAt, VLine &left, VLine &right) const
{
    float  len = length();
    float dx = ((mX2 - mX1) / len) * lengthAt;
    float dy = ((mY2 - mY1) / len) * lengthAt;

    left.mX1 = mX1;
    left.mY1 = mY1;
    left.mX2 = left.mX1 + dx;
    left.mY2 = left.mY1 + dy;

    right.mX1 = left.mX2;
    right.mY1 = left.mY2;
    right.mX2 = mX2;
    right.mY2 = mY2;
}

#endif //VLINE_H
