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

#include "vbezier.h"
#include <cmath>
#include "vline.h"

V_BEGIN_NAMESPACE

VBezier VBezier::fromPoints(const VPointF &p1, const VPointF &p2,
                            const VPointF &p3, const VPointF &p4)
{
    VBezier b;
    b.x1 = p1.x();
    b.y1 = p1.y();
    b.x2 = p2.x();
    b.y2 = p2.y();
    b.x3 = p3.x();
    b.y3 = p3.y();
    b.x4 = p4.x();
    b.y4 = p4.y();
    return b;
}

float VBezier::length() const
{
    VBezier left, right; /* bez poly splits */
    float   len = 0.0;   /* arc length */
    float   chord;       /* chord length */
    float   length;

    len = len + VLine::length(x1, y1, x2, y2);
    len = len + VLine::length(x2, y2, x3, y3);
    len = len + VLine::length(x3, y3, x4, y4);

    chord = VLine::length(x1, y1, x4, y4);

    if ((len - chord) > 0.01) {
        split(&left, &right);    /* split in two */
        length = left.length() + /* try left side */
                 right.length(); /* try right side */

        return length;
    }

    return len;
}

VBezier VBezier::onInterval(float t0, float t1) const
{
    if (t0 == 0 && t1 == 1) return *this;

    VBezier bezier = *this;

    VBezier result;
    bezier.parameterSplitLeft(t0, &result);
    float trueT = (t1 - t0) / (1 - t0);
    bezier.parameterSplitLeft(trueT, &result);

    return result;
}

float VBezier::tAtLength(float l) const
{
    float       len = length();
    float       t = 1.0;
    const float error = 0.01;
    if (l > len || vCompare(l, len)) return t;

    t *= 0.5;

    float lastBigger = 1.0;
    while (1) {
        VBezier right = *this;
        VBezier left;
        right.parameterSplitLeft(t, &left);
        float lLen = left.length();
        if (fabs(lLen - l) < error) break;

        if (lLen < l) {
            t += (lastBigger - t) * 0.5;
        } else {
            lastBigger = t;
            t -= t * 0.5;
        }
    }
    return t;
}

void VBezier::splitAtLength(float len, VBezier *left, VBezier *right)
{
    float t;

    *right = *this;
    t = right->tAtLength(len);
    right->parameterSplitLeft(t, left);
}

VPointF VBezier::derivative(float t) const
{
    // p'(t) = 3 * (-(1-2t+t^2) * p0 + (1 - 4 * t + 3 * t^2) * p1 + (2 * t - 3 *
    // t^2) * p2 + t^2 * p3)

    float m_t = 1. - t;

    float d = t * t;
    float a = -m_t * m_t;
    float b = 1 - 4 * t + 3 * d;
    float c = 2 * t - 3 * d;

    return 3 * VPointF(a * x1 + b * x2 + c * x3 + d * x4,
                       a * y1 + b * y2 + c * y3 + d * y4);
}

float VBezier::angleAt(float t) const
{
    if (t < 0 || t > 1) {
        return 0;
    }
    return VLine({}, derivative(t)).angle();
}

V_END_NAMESPACE
