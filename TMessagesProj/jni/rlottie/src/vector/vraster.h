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

#ifndef VRASTER_H
#define VRASTER_H
#include <future>
#include "vglobal.h"
#include "vrect.h"

V_BEGIN_NAMESPACE

class VPath;
class VRle;

class VRasterizer
{
public:
    void rasterize(VPath path, FillRule fillRule = FillRule::Winding, const VRect &clip = VRect());
    void rasterize(VPath path, CapStyle cap, JoinStyle join, float width,
                   float miterLimit, const VRect &clip = VRect());
    VRle rle();
private:
    struct VRasterizerImpl;
    void init();
    void updateRequest();
    std::shared_ptr<VRasterizerImpl> d{nullptr};
};

V_END_NAMESPACE

#endif  // VRASTER_H
