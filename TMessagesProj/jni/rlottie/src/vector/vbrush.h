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

#ifndef VBRUSH_H
#define VBRUSH_H

#include <vector>
#include "vglobal.h"
#include "vmatrix.h"
#include "vpoint.h"
#include "vbitmap.h"

V_BEGIN_NAMESPACE

using VGradientStop = std::pair<float, VColor>;
using VGradientStops = std::vector<VGradientStop>;
class VGradient {
public:
    enum class Mode { Absolute, Relative };
    enum class Spread { Pad, Repeat, Reflect };
    enum class Type { Linear, Radial };
    explicit VGradient(VGradient::Type type);
    void setStops(const VGradientStops &stops);
    void setAlpha(float alpha) {mAlpha = alpha;}
    float alpha() const {return mAlpha;}

public:
    static constexpr int colorTableSize = 1024;
    VGradient::Type      mType{Type::Linear};
    VGradient::Spread    mSpread{Spread::Pad};
    VGradient::Mode      mMode{Mode::Absolute};
    VGradientStops       mStops;
    float                mAlpha{1.0};
    struct Linear{
        float x1{0}, y1{0}, x2{0}, y2{0};
    };
    struct Radial{
        float cx{0}, cy{0}, fx{0}, fy{0}, cradius{0}, fradius{0};
    };
    union {
        Linear linear;
        Radial radial;
    };
    VMatrix mMatrix;
};

struct VTexture {
    VBitmap  mBitmap;
    VMatrix  mMatrix;
    int      mAlpha{255};
};

class VBrush {
public:
    enum class Type { NoBrush, Solid, LinearGradient, RadialGradient, Texture };
    VBrush():mType(Type::NoBrush),mColor(){};
    explicit VBrush(const VColor &color);
    explicit VBrush(const VGradient *gradient);
    explicit VBrush(uchar r, uchar g, uchar b, uchar a);
    explicit VBrush(const VTexture *texture);
    inline VBrush::Type type() const { return mType; }
public:
    VBrush::Type     mType{Type::NoBrush};
    union {
        VColor           mColor{};
        const VGradient *mGradient;
        const VTexture  *mTexture;
    };
};

V_END_NAMESPACE

#endif  // VBRUSH_H
