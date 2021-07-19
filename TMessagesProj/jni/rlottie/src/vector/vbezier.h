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

#ifndef VBEZIER_H
#define VBEZIER_H

#include <vpoint.h>

V_BEGIN_NAMESPACE

class VBezier {
public:
    VBezier() = default;
    VPointF     pointAt(float t) const;
    float       angleAt(float t) const;
    VBezier     onInterval(float t0, float t1) const;
    float       length() const;
    static void coefficients(float t, float &a, float &b, float &c, float &d);
    static VBezier fromPoints(const VPointF &start, const VPointF &cp1,
                              const VPointF &cp2, const VPointF &end);
    inline void    parameterSplitLeft(float t, VBezier *left);
    inline void    split(VBezier *firstHalf, VBezier *secondHalf) const;
    float          tAtLength(float len) const;
    void           splitAtLength(float len, VBezier *left, VBezier *right);
    VPointF        pt1() const { return {x1, y1}; }
    VPointF        pt2() const { return {x2, y2}; }
    VPointF        pt3() const { return {x3, y3}; }
    VPointF        pt4() const { return {x4, y4}; }

private:
    VPointF derivative(float t) const;
    float   x1, y1, x2, y2, x3, y3, x4, y4;
};

inline void VBezier::coefficients(float t, float &a, float &b, float &c,
                                  float &d)
{
    float m_t = 1. - t;
    b = m_t * m_t;
    c = t * t;
    d = c * t;
    a = b * m_t;
    b *= 3. * t;
    c *= 3. * m_t;
}

inline VPointF VBezier::pointAt(float t) const
{
    // numerically more stable:
    float x, y;

    float m_t = 1. - t;
    {
        float a = x1 * m_t + x2 * t;
        float b = x2 * m_t + x3 * t;
        float c = x3 * m_t + x4 * t;
        a = a * m_t + b * t;
        b = b * m_t + c * t;
        x = a * m_t + b * t;
    }
    {
        float a = y1 * m_t + y2 * t;
        float b = y2 * m_t + y3 * t;
        float c = y3 * m_t + y4 * t;
        a = a * m_t + b * t;
        b = b * m_t + c * t;
        y = a * m_t + b * t;
    }
    return {x, y};
}

inline void VBezier::parameterSplitLeft(float t, VBezier *left)
{
    left->x1 = x1;
    left->y1 = y1;

    left->x2 = x1 + t * (x2 - x1);
    left->y2 = y1 + t * (y2 - y1);

    left->x3 = x2 + t * (x3 - x2);  // temporary holding spot
    left->y3 = y2 + t * (y3 - y2);  // temporary holding spot

    x3 = x3 + t * (x4 - x3);
    y3 = y3 + t * (y4 - y3);

    x2 = left->x3 + t * (x3 - left->x3);
    y2 = left->y3 + t * (y3 - left->y3);

    left->x3 = left->x2 + t * (left->x3 - left->x2);
    left->y3 = left->y2 + t * (left->y3 - left->y2);

    left->x4 = x1 = left->x3 + t * (x2 - left->x3);
    left->y4 = y1 = left->y3 + t * (y2 - left->y3);
}

inline void VBezier::split(VBezier *firstHalf, VBezier *secondHalf) const
{
    float c = (x2 + x3) * .5;
    firstHalf->x2 = (x1 + x2) * .5;
    secondHalf->x3 = (x3 + x4) * .5;
    firstHalf->x1 = x1;
    secondHalf->x4 = x4;
    firstHalf->x3 = (firstHalf->x2 + c) * .5;
    secondHalf->x2 = (secondHalf->x3 + c) * .5;
    firstHalf->x4 = secondHalf->x1 = (firstHalf->x3 + secondHalf->x2) * .5;

    c = (y2 + y3) / 2;
    firstHalf->y2 = (y1 + y2) * .5;
    secondHalf->y3 = (y3 + y4) * .5;
    firstHalf->y1 = y1;
    secondHalf->y4 = y4;
    firstHalf->y3 = (firstHalf->y2 + c) * .5;
    secondHalf->y2 = (secondHalf->y3 + c) * .5;
    firstHalf->y4 = secondHalf->y1 = (firstHalf->y3 + secondHalf->y2) * .5;
}

V_END_NAMESPACE

#endif  // VBEZIER_H
