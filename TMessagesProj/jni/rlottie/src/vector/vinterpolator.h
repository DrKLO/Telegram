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

    static float A(float aA1, float aA2) { return 1.0f - 3.0f * aA2 + 3.0f * aA1; }

    static float B(float aA1, float aA2) { return 3.0f * aA2 - 6.0f * aA1; }

    static float C(float aA1) { return 3.0f * aA1; }

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
