/* -*- Mode: C++; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 2 -*- */
/* vim: set ts=8 sts=2 et sw=2 tw=80: */
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "vinterpolator.h"
#include <cmath>

V_BEGIN_NAMESPACE

#define NEWTON_ITERATIONS 4
#define NEWTON_MIN_SLOPE 0.02
#define SUBDIVISION_PRECISION 0.0000001
#define SUBDIVISION_MAX_ITERATIONS 10

const float VInterpolator::kSampleStepSize =
    1.0f / float(VInterpolator::kSplineTableSize - 1);

void VInterpolator::init(float aX1, float aY1, float aX2, float aY2)
{
    mX1 = aX1;
    mY1 = aY1;
    mX2 = aX2;
    mY2 = aY2;

    if (mX1 != mY1 || mX2 != mY2) CalcSampleValues();
}

/*static*/ float VInterpolator::CalcBezier(float aT, float aA1, float aA2)
{
    // use Horner's scheme to evaluate the Bezier polynomial
    return ((A(aA1, aA2) * aT + B(aA1, aA2)) * aT + C(aA1)) * aT;
}

void VInterpolator::CalcSampleValues()
{
    for (int i = 0; i < kSplineTableSize; ++i) {
        mSampleValues[i] = CalcBezier(float(i) * kSampleStepSize, mX1, mX2);
    }
}

float VInterpolator::GetSlope(float aT, float aA1, float aA2)
{
    return 3.0f * A(aA1, aA2) * aT * aT + 2.0f * B(aA1, aA2) * aT + C(aA1);
}

float VInterpolator::value(float aX) const
{
    if (mX1 == mY1 && mX2 == mY2) return aX;

    return CalcBezier(GetTForX(aX), mY1, mY2);
}

float VInterpolator::GetTForX(float aX) const
{
    // Find interval where t lies
    float              intervalStart = 0.0;
    const float*       currentSample = &mSampleValues[1];
    const float* const lastSample = &mSampleValues[kSplineTableSize - 1];
    for (; currentSample != lastSample && *currentSample <= aX;
         ++currentSample) {
        intervalStart += kSampleStepSize;
    }
    --currentSample;  // t now lies between *currentSample and *currentSample+1

    // Interpolate to provide an initial guess for t
    float dist =
        (aX - *currentSample) / (*(currentSample + 1) - *currentSample);
    float guessForT = intervalStart + dist * kSampleStepSize;

    // Check the slope to see what strategy to use. If the slope is too small
    // Newton-Raphson iteration won't converge on a root so we use bisection
    // instead.
    float initialSlope = GetSlope(guessForT, mX1, mX2);
    if (initialSlope >= NEWTON_MIN_SLOPE) {
        return NewtonRaphsonIterate(aX, guessForT);
    } else if (initialSlope == 0.0) {
        return guessForT;
    } else {
        return BinarySubdivide(aX, intervalStart,
                               intervalStart + kSampleStepSize);
    }
}

float VInterpolator::NewtonRaphsonIterate(float aX, float aGuessT) const
{
    // Refine guess with Newton-Raphson iteration
    for (int i = 0; i < NEWTON_ITERATIONS; ++i) {
        // We're trying to find where f(t) = aX,
        // so we're actually looking for a root for: CalcBezier(t) - aX
        float currentX = CalcBezier(aGuessT, mX1, mX2) - aX;
        float currentSlope = GetSlope(aGuessT, mX1, mX2);

        if (currentSlope == 0.0) return aGuessT;

        aGuessT -= currentX / currentSlope;
    }

    return aGuessT;
}

float VInterpolator::BinarySubdivide(float aX, float aA, float aB) const
{
    float currentX;
    float currentT;
    int   i = 0;

    do {
        currentT = aA + (aB - aA) / 2.0f;
        currentX = CalcBezier(currentT, mX1, mX2) - aX;

        if (currentX > 0.0) {
            aB = currentT;
        } else {
            aA = currentT;
        }
    } while (fabs(currentX) > SUBDIVISION_PRECISION &&
             ++i < SUBDIVISION_MAX_ITERATIONS);

    return currentT;
}

V_END_NAMESPACE
