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

#ifndef VBRUSH_H
#define VBRUSH_H

#include <vector>
#include "vglobal.h"
#include "vmatrix.h"
#include "vpoint.h"
#include "vbitmap.h"

V_BEGIN_NAMESPACE

typedef std::pair<float, VColor>   VGradientStop;
typedef std::vector<VGradientStop> VGradientStops;
class VGradient {
public:
    enum class Mode { Absolute, Relative };
    enum class Spread { Pad, Repeat, Reflect };
    enum class Type { Linear, Radial };
    VGradient(VGradient::Type type);
    void setStops(const VGradientStops &stops);
    void setAlpha(float alpha) {mAlpha = alpha;}
    float alpha() const {return mAlpha;}
    VGradient() = default;

public:
    static constexpr int colorTableSize = 1024;
    VGradient::Type      mType;
    VGradient::Spread    mSpread;
    VGradient::Mode      mMode;
    VGradientStops       mStops;
    float                mAlpha{1.0};
    union {
        struct {
            float x1, y1, x2, y2;
        } linear;
        struct {
            float cx, cy, fx, fy, cradius, fradius;
        } radial;
    };
    VMatrix mMatrix;
};

class VLinearGradient : public VGradient {
public:
    VLinearGradient(const VPointF &start, const VPointF &stop);
    VLinearGradient(float xStart, float yStart, float xStop, float yStop);
};

class VRadialGradient : public VGradient {
public:
    VRadialGradient(const VPointF &center, float cradius,
                    const VPointF &focalPoint, float fradius);
    VRadialGradient(float cx, float cy, float cradius, float fx, float fy,
                    float fradius);
};

class VBrush {
public:
    enum class Type { NoBrush, Solid, LinearGradient, RadialGradient, Texture };
    VBrush() = default;
    VBrush(const VColor &color);
    VBrush(const VGradient *gradient);
    VBrush(int r, int g, int b, int a);
    VBrush(const VBitmap &texture);
    inline VBrush::Type type() const { return mType; }
    void setMatrix(const VMatrix &m);
public:
    VBrush::Type     mType{Type::NoBrush};
    VColor           mColor;
    const VGradient *mGradient{nullptr};
    VBitmap          mTexture;
    VMatrix          mMatrix;
};

V_END_NAMESPACE

#endif  // VBRUSH_H
