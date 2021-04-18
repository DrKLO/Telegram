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

#include "vbrush.h"

V_BEGIN_NAMESPACE

VGradient::VGradient(VGradient::Type type)
    : mType(type)
{
    if (mType == Type::Linear)
        linear.x1 = linear.y1 = linear.x2 = linear.y2 = 0.0f;
    else
        radial.cx = radial.cy = radial.fx =
        radial.fy = radial.cradius = radial.fradius = 0.0f;
}

void VGradient::setStops(const VGradientStops &stops)
{
    mStops = stops;
}

VBrush::VBrush(const VColor &color) : mType(VBrush::Type::Solid), mColor(color)
{
}

VBrush::VBrush(uchar r, uchar g, uchar b, uchar a)
    : mType(VBrush::Type::Solid), mColor(r, g, b, a)

{
}

VBrush::VBrush(const VGradient *gradient)
{
    if (!gradient) return;

    mGradient = gradient;

    if (gradient->mType == VGradient::Type::Linear) {
        mType = VBrush::Type::LinearGradient;
    } else if (gradient->mType == VGradient::Type::Radial) {
        mType = VBrush::Type::RadialGradient;
    }
}

VBrush::VBrush(const VTexture *texture):mType(VBrush::Type::Texture), mTexture(texture)
{
}

V_END_NAMESPACE
