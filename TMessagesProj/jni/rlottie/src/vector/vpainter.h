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

#ifndef VPAINTER_H
#define VPAINTER_H

#include "vbrush.h"
#include "vpoint.h"
#include "vrle.h"

V_BEGIN_NAMESPACE

class VBitmap;
class VPainterImpl;
class VPainter {
public:
    enum CompositionMode {
        CompModeSrc,
        CompModeSrcOver,
        CompModeDestIn,
        CompModeDestOut
    };
    ~VPainter();
    VPainter();
    VPainter(VBitmap *buffer, bool clear);
    bool  begin(VBitmap *buffer, bool clear);
    void  end();
    void  setDrawRegion(const VRect &region); // sub surface rendering area.
    void  setBrush(const VBrush &brush);
    void  setCompositionMode(CompositionMode mode);
    void  drawRle(const VPoint &pos, const VRle &rle);
    void  drawRle(const VRle &rle, const VRle &clip);
    VRect clipBoundingRect() const;

    void  drawBitmap(const VPoint &point, const VBitmap &bitmap, const VRect &source, uint8_t const_alpha = 255);
    void  drawBitmap(const VRect &target, const VBitmap &bitmap, const VRect &source, uint8_t const_alpha = 255);
    void  drawBitmap(const VPoint &point, const VBitmap &bitmap, uint8_t const_alpha = 255);
    void  drawBitmap(const VRect &rect, const VBitmap &bitmap, uint8_t const_alpha = 255);
private:
    VPainterImpl *mImpl;
};

V_END_NAMESPACE

#endif  // VPAINTER_H
