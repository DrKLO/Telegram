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

#ifndef VDRAWABLE_H
#define VDRAWABLE_H
#include <future>
#include "vbrush.h"
#include "vpath.h"
#include "vrle.h"
#include "vraster.h"

class VDrawable {
public:
    enum class DirtyState {
        None = 0x00000000,
        Path = 0x00000001,
        Stroke = 0x00000010,
        Brush = 0x00000100,
        All = (None | Path | Stroke | Brush)
    };
    enum class Type : unsigned char{
        Fill,
        Stroke,
    };
    typedef vFlag<DirtyState> DirtyFlag;
    void setPath(const VPath &path);
    void setFillRule(FillRule rule) { mFillRule = rule; }
    void setBrush(const VBrush &brush) { mBrush = brush; }
    void setStrokeInfo(CapStyle cap, JoinStyle join, float meterLimit,
                       float strokeWidth);
    void setDashInfo(float *array, uint size);
    void preprocess(const VRect &clip);
    VRle rle();

public:
    struct StrokeInfo {
        std::vector<float> mDash;
        float              width{0.0};
        float              meterLimit{10};
        bool               enable{false};
        CapStyle           cap{CapStyle::Flat};
        JoinStyle          join{JoinStyle::Bevel};
    };
    VRasterizer       mRasterizer;
    VBrush            mBrush;
    VPath             mPath;
    StrokeInfo        mStroke;
    DirtyFlag         mFlag{DirtyState::All};
    FillRule          mFillRule{FillRule::Winding};
    VDrawable::Type   mType{Type::Fill};
};

#endif  // VDRAWABLE_H
