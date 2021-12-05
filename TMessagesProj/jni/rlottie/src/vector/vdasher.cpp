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

#include "vbezier.h"

#include <cmath>

#include "vdasher.h"
#include "vline.h"

V_BEGIN_NAMESPACE

VDasher::VDasher(const float *dashArray, size_t size)
{
    mDashArray = reinterpret_cast<const VDasher::Dash *>(dashArray);
    mArraySize = size / 2;
    if (size % 2) mDashOffset = dashArray[size - 1];
    mIndex = 0;
    mCurrentLength = 0;
    mDiscard = false;
}

void VDasher::moveTo(const VPointF &p)
{
    mDiscard = false;
    mStartNewSegment = true;
    mCurPt = p;
    mIndex = 0;

    if (!vCompare(mDashOffset, 0.0f)) {
        float totalLength = 0.0;
        for (size_t i = 0; i < mArraySize; i++) {
            totalLength = mDashArray[i].length + mDashArray[i].gap;
        }
        float normalizeLen = std::fmod(mDashOffset, totalLength);
        if (normalizeLen < 0.0f) {
            normalizeLen = totalLength + normalizeLen;
        }
        // now the length is less than total length and +ve
        // findout the current dash index , dashlength and gap.
        for (size_t i = 0; i < mArraySize; i++) {
            if (normalizeLen < mDashArray[i].length) {
                mIndex = i;
                mCurrentLength = mDashArray[i].length - normalizeLen;
                mDiscard = false;
                break;
            }
            normalizeLen -= mDashArray[i].length;
            if (normalizeLen < mDashArray[i].gap) {
                mIndex = i;
                mCurrentLength = mDashArray[i].gap - normalizeLen;
                mDiscard = true;
                break;
            }
            normalizeLen -= mDashArray[i].gap;
        }
    } else {
        mCurrentLength = mDashArray[mIndex].length;
    }
    if (vIsZero(mCurrentLength)) updateActiveSegment();
}

void VDasher::addLine(const VPointF &p)
{
    if (mDiscard) return;

    if (mStartNewSegment) {
        mResult.moveTo(mCurPt);
        mStartNewSegment = false;
    }
    mResult.lineTo(p);
}

void VDasher::updateActiveSegment()
{
    mStartNewSegment = true;

    if (mDiscard) {
        mDiscard = false;
        mIndex = (mIndex + 1) % mArraySize;
        mCurrentLength = mDashArray[mIndex].length;
    } else {
        mDiscard = true;
        mCurrentLength = mDashArray[mIndex].gap;
    }
    if (vIsZero(mCurrentLength)) updateActiveSegment();
}

void VDasher::lineTo(const VPointF &p)
{
    VLine left, right;
    VLine line(mCurPt, p);
    float length = line.length();

    if (length <= mCurrentLength) {
        mCurrentLength -= length;
        addLine(p);
    } else {
        while (length > mCurrentLength) {
            length -= mCurrentLength;
            line.splitAtLength(mCurrentLength, left, right);

            addLine(left.p2());
            updateActiveSegment();

            line = right;
            mCurPt = line.p1();
        }
        // handle remainder
        if (length > 1.0f) {
            mCurrentLength -= length;
            addLine(line.p2());
        }
    }

    if (mCurrentLength < 1.0f) updateActiveSegment();

    mCurPt = p;
}

void VDasher::addCubic(const VPointF &cp1, const VPointF &cp2, const VPointF &e)
{
    if (mDiscard) return;

    if (mStartNewSegment) {
        mResult.moveTo(mCurPt);
        mStartNewSegment = false;
    }
    mResult.cubicTo(cp1, cp2, e);
}

void VDasher::cubicTo(const VPointF &cp1, const VPointF &cp2, const VPointF &e)
{
    VBezier left, right;
    VBezier b = VBezier::fromPoints(mCurPt, cp1, cp2, e);
    float   bezLen = b.length();

    if (bezLen <= mCurrentLength) {
        mCurrentLength -= bezLen;
        addCubic(cp1, cp2, e);
    } else {
        while (bezLen > mCurrentLength) {
            bezLen -= mCurrentLength;
            b.splitAtLength(mCurrentLength, &left, &right);

            addCubic(left.pt2(), left.pt3(), left.pt4());
            updateActiveSegment();

            b = right;
            mCurPt = b.pt1();
        }
        // handle remainder
        if (bezLen > 1.0f) {
            mCurrentLength -= bezLen;
            addCubic(b.pt2(), b.pt3(), b.pt4());
        }
    }

    if (mCurrentLength < 1.0f) updateActiveSegment();

    mCurPt = e;
}

VPath VDasher::dashed(const VPath &path)
{
    if (path.empty()) return VPath();

    mResult = {};
    mResult.reserve(path.points().size(), path.elements().size());
    mIndex = 0;
    const std::vector<VPath::Element> &elms = path.elements();
    const std::vector<VPointF> &       pts = path.points();
    const VPointF *                    ptPtr = pts.data();

    for (auto &i : elms) {
        switch (i) {
        case VPath::Element::MoveTo: {
            moveTo(*ptPtr++);
            break;
        }
        case VPath::Element::LineTo: {
            lineTo(*ptPtr++);
            break;
        }
        case VPath::Element::CubicTo: {
            cubicTo(*ptPtr, *(ptPtr + 1), *(ptPtr + 2));
            ptPtr += 3;
            break;
        }
        case VPath::Element::Close: {
            // The point is already joined to start point in VPath
            // no need to do anything here.
            break;
        }
        }
    }
    if (mResult.points().size() > SHRT_MAX) {
        mResult.reset();
    }
    return std::move(mResult);
}

V_END_NAMESPACE
