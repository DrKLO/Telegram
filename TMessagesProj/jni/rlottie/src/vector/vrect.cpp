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

#include "vrect.h"
#include <algorithm>

VRect VRect::operator&(const VRect &r) const
{
    if (empty()) return VRect();

    int l1 = x1;
    int r1 = x1;
    if (x2 - x1 + 1 < 0)
        l1 = x2;
    else
        r1 = x2;

    int l2 = r.x1;
    int r2 = r.x1;
    if (r.x2 - r.x1 + 1 < 0)
        l2 = r.x2;
    else
        r2 = r.x2;

    if (l1 > r2 || l2 > r1) return VRect();

    int t1 = y1;
    int b1 = y1;
    if (y2 - y1 + 1 < 0)
        t1 = y2;
    else
        b1 = y2;

    int t2 = r.y1;
    int b2 = r.y1;
    if (r.y2 - r.y1 + 1 < 0)
        t2 = r.y2;
    else
        b2 = r.y2;

    if (t1 > b2 || t2 > b1) return VRect();

    VRect tmp;
    tmp.x1 = std::max(l1, l2);
    tmp.x2 = std::min(r1, r2);
    tmp.y1 = std::max(t1, t2);
    tmp.y2 = std::min(b1, b2);
    return tmp;
}
