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

#include "vpathmesure.h"
#include <limits>
#include "vbezier.h"
#include "vdasher.h"

V_BEGIN_NAMESPACE

/*
 * start and end value must be normalized to [0 - 1]
 * Path mesure trims the path from [start --> end]
 * if start > end it treates as a loop and trims as two segment
 *  [0-->end] and [start --> 1]
 */
VPath VPathMesure::trim(const VPath &path)
{
    if (vCompare(mStart, mEnd)) return VPath();

    if ((vCompare(mStart, 0.0f) && (vCompare(mEnd, 1.0f))) ||
        (vCompare(mStart, 1.0f) && (vCompare(mEnd, 0.0f))))
        return path;

    float length = path.length();

    if (mStart < mEnd) {
        float array[4] = {
            0.0f, length * mStart,  // 1st segment
            (mEnd - mStart) * length,
            std::numeric_limits<float>::max(),  // 2nd segment
        };
        VDasher dasher(array, 4);
        dasher.dashed(path, mScratchObject);
        return mScratchObject;
    } else {
        float array[4] = {
            length * mEnd, (mStart - mEnd) * length,  // 1st segment
            (1 - mStart) * length,
            std::numeric_limits<float>::max(),  // 2nd segment
        };
        VDasher dasher(array, 4);
        dasher.dashed(path, mScratchObject);
        return mScratchObject;
    }
}

V_END_NAMESPACE
