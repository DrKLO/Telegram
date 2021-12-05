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

#include "vbrush.h"

V_BEGIN_NAMESPACE

VGradient::VGradient(VGradient::Type type)
    : mType(type),
      mSpread(VGradient::Spread::Pad),
      mMode(VGradient::Mode::Absolute)
{
}

void VGradient::setStops(const VGradientStops &stops)
{
    mStops = stops;
}

VLinearGradient::VLinearGradient(const VPointF &start, const VPointF &stop)
    : VGradient(VGradient::Type::Linear)
{
    linear.x1 = start.x();
    linear.y1 = start.y();
    linear.x1 = stop.x();
    linear.y1 = stop.y();
}

VLinearGradient::VLinearGradient(float xStart, float yStart, float xStop,
                                 float yStop)
    : VGradient(VGradient::Type::Linear)
{
    linear.x1 = xStart;
    linear.y1 = yStart;
    linear.x1 = xStop;
    linear.y1 = yStop;
}

VRadialGradient::VRadialGradient(const VPointF &center, float cradius,
                                 const VPointF &focalPoint, float fradius)
    : VGradient(VGradient::Type::Radial)
{
    radial.cx = center.x();
    radial.cy = center.y();
    radial.fx = focalPoint.x();
    radial.fy = focalPoint.y();
    radial.cradius = cradius;
    radial.fradius = fradius;
}

VRadialGradient::VRadialGradient(float cx, float cy, float cradius, float fx,
                                 float fy, float fradius)
    : VGradient(VGradient::Type::Radial)
{
    radial.cx = cx;
    radial.cy = cy;
    radial.fx = fx;
    radial.fy = fy;
    radial.cradius = cradius;
    radial.fradius = fradius;
}

VBrush::VBrush(const VColor &color) : mType(VBrush::Type::Solid), mColor(color)
{
}

VBrush::VBrush(int r, int g, int b, int a)
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

VBrush::VBrush(const VBitmap &texture)
{
    if (!texture.valid()) return;

    mType = VBrush::Type::Texture;
    mTexture = texture;
}

void VBrush::setMatrix(const VMatrix &m)
{
    mMatrix = m;
}

V_END_NAMESPACE
