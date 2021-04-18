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

#ifndef VPAINTER_H
#define VPAINTER_H

#include "vbrush.h"
#include "vpoint.h"
#include "vrle.h"
#include "vdrawhelper.h"

V_BEGIN_NAMESPACE

class VBitmap;
class VPainter {
public:
    VPainter() = default;
    explicit     VPainter(VBitmap *buffer, bool clear);
    bool  begin(VBitmap *buffer, bool clear);
    void  end();
    void  setDrawRegion(const VRect &region); // sub surface rendering area.
    void  setBrush(const VBrush &brush);
    void  setBlendMode(BlendMode mode);
    void  drawRle(const VPoint &pos, const VRle &rle);
    void  drawRle(const VRle &rle, const VRle &clip);
    VRect clipBoundingRect() const;

    void  drawBitmap(const VPoint &point, const VBitmap &bitmap, const VRect &source, uint8_t const_alpha = 255);
    void  drawBitmap(const VRect &target, const VBitmap &bitmap, const VRect &source, uint8_t const_alpha = 255);
    void  drawBitmap(const VPoint &point, const VBitmap &bitmap, uint8_t const_alpha = 255);
    void  drawBitmap(const VRect &rect, const VBitmap &bitmap, uint8_t const_alpha = 255);
private:
    void drawBitmapUntransform(const VRect &target, const VBitmap &bitmap,
                               const VRect &source, uint8_t const_alpha);
    VRasterBuffer mBuffer;
    VSpanData     mSpanData;
};

V_END_NAMESPACE

#endif  // VPAINTER_H
