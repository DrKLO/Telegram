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

#ifndef VINTERPOLATOR_H
#define VINTERPOLATOR_H

#include "vpoint.h"

V_BEGIN_NAMESPACE

class VInterpolator {
public:
    VInterpolator()
    { /* caller must call Init later */
    }

    VInterpolator(float aX1, float aY1, float aX2, float aY2)
    {
        init(aX1, aY1, aX2, aY2);
    }

    VInterpolator(VPointF pt1, VPointF pt2)
    {
        init(pt1.x(), pt1.y(), pt2.x(), pt2.y());
    }

    void init(float aX1, float aY1, float aX2, float aY2);

    float value(float aX) const;

    void GetSplineDerivativeValues(float aX, float& aDX, float& aDY) const;

private:
    void CalcSampleValues();

    /**
     * Returns x(t) given t, x1, and x2, or y(t) given t, y1, and y2.
     */
    static float CalcBezier(float aT, float aA1, float aA2);

    /**
     * Returns dx/dt given t, x1, and x2, or dy/dt given t, y1, and y2.
     */
    static float GetSlope(float aT, float aA1, float aA2);

    float GetTForX(float aX) const;

    float NewtonRaphsonIterate(float aX, float aGuessT) const;

    float BinarySubdivide(float aX, float aA, float aB) const;

    static float A(float aA1, float aA2) { return 1.0 - 3.0 * aA2 + 3.0 * aA1; }

    static float B(float aA1, float aA2) { return 3.0 * aA2 - 6.0 * aA1; }

    static float C(float aA1) { return 3.0 * aA1; }

    float mX1;
    float mY1;
    float mX2;
    float mY2;
    enum { kSplineTableSize = 11 };
    float              mSampleValues[kSplineTableSize];
    static const float kSampleStepSize;
};

V_END_NAMESPACE

#endif  // VINTERPOLATOR_H
