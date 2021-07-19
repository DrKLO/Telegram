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
        return dasher.dashed(path);
    } else {
        float array[4] = {
            length * mEnd, (mStart - mEnd) * length,  // 1st segment
            (1 - mStart) * length,
            std::numeric_limits<float>::max(),  // 2nd segment
        };
        VDasher dasher(array, 4);
        return dasher.dashed(path);
    }
}

V_END_NAMESPACE
