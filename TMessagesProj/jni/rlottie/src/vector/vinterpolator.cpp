/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is the Mozilla SMIL module.
 *
 * The Initial Developer of the Original Code is Brian Birtles.
 * Portions created by the Initial Developer are Copyright (C) 2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Brian Birtles <birtles@gmail.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

#include "vinterpolator.h"
#include <cmath>

V_BEGIN_NAMESPACE

#define NEWTON_ITERATIONS 4
#define NEWTON_MIN_SLOPE 0.02
#define SUBDIVISION_PRECISION 0.0000001
#define SUBDIVISION_MAX_ITERATIONS 10

const float VInterpolator::kSampleStepSize =
    1.0 / float(VInterpolator::kSplineTableSize - 1);

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
    return 3.0 * A(aA1, aA2) * aT * aT + 2.0 * B(aA1, aA2) * aT + C(aA1);
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
        currentT = aA + (aB - aA) / 2.0;
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
