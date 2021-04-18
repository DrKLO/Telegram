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
