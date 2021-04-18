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

#ifndef VDRAWABLE_H
#define VDRAWABLE_H
#include <future>
#include <cstring>
#include "vbrush.h"
#include "vpath.h"
#include "vrle.h"
#include "vraster.h"

class VDrawable {
public:
    enum class DirtyState : unsigned char {
        None = 1<<0,
        Path = 1<<1,
        Stroke = 1<<2,
        Brush = 1<<3,
        All = (Path | Stroke | Brush)
    };

    enum class Type : unsigned char{
        Fill,
        Stroke,
        StrokeWithDash
    };

    explicit VDrawable(VDrawable::Type type = Type::Fill);
    void setType(VDrawable::Type type);
    ~VDrawable() noexcept;

    typedef vFlag<DirtyState> DirtyFlag;
    void setPath(const VPath &path);
    void setFillRule(FillRule rule) { mFillRule = rule; }
    void setBrush(const VBrush &brush) { mBrush = brush; }
    void setStrokeInfo(CapStyle cap, JoinStyle join, float miterLimit,
                       float strokeWidth);
    void setDashInfo(std::vector<float> &dashInfo);
    void preprocess(const VRect &clip);
    void applyDashOp();
    VRle rle();
    void setName(const char *name)
    {
        mName = name;
    }
    const char* name() const { return mName; }

public:
    struct StrokeInfo {
        float              width{0.0};
        float              miterLimit{10};
        CapStyle           cap{CapStyle::Flat};
        JoinStyle          join{JoinStyle::Bevel};
    };

    struct StrokeWithDashInfo : public StrokeInfo{
        std::vector<float> mDash;
    };

public:
    VPath                    mPath;
    VBrush                   mBrush;
    VRasterizer              mRasterizer;
    StrokeInfo              *mStrokeInfo{nullptr};

    DirtyFlag                mFlag{DirtyState::All};
    FillRule                 mFillRule{FillRule::Winding};
    VDrawable::Type          mType{Type::Fill};

    const char              *mName{nullptr};
};

#endif  // VDRAWABLE_H
