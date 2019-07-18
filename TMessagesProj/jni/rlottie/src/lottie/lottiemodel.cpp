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

#include "lottiemodel.h"
#include <cassert>
#include <iterator>
#include <stack>
#include "vimageloader.h"
#include "vline.h"

/*
 * We process the iterator objects in the children list
 * by iterating from back to front. when we find a repeater object
 * we remove the objects from satrt till repeater object and then place
 * under a new shape group object which we add it as children to the repeater
 * object.
 * Then we visit the childrens of the newly created shape group object to
 * process the remaining repeater object(when children list contains more than
 * one repeater).
 *
 */
class LottieRepeaterProcesser {
public:
    void visitChildren(LOTGroupData *obj)
    {
        for (auto i = obj->mChildren.rbegin(); i != obj->mChildren.rend();
             ++i) {
            auto child = (*i).get();
            if (child->type() == LOTData::Type::Repeater) {
                LOTRepeaterData *repeater =
                    static_cast<LOTRepeaterData *>(child);
                // check if this repeater is already processed
                // can happen if the layer is an asset and referenced by
                // multiple layer.
                if (repeater->content()) continue;

                repeater->setContent(std::make_shared<LOTShapeGroupData>());
                LOTShapeGroupData *content = repeater->content();
                // 1. increment the reverse iterator to point to the
                //   object before the repeater
                ++i;
                // 2. move all the children till repater to the group
                std::move(obj->mChildren.begin(), i.base(),
                          back_inserter(content->mChildren));
                // 3. erase the objects from the original children list
                obj->mChildren.erase(obj->mChildren.begin(), i.base());

                // 5. visit newly created group to process remaining repeater
                // object.
                visitChildren(content);
                // 6. exit the loop as the current iterators are invalid
                break;
            } else {
                visit(child);
            }
        }
    }

    void visit(LOTData *obj)
    {
        switch (obj->mType) {
        case LOTData::Type::Repeater:
        case LOTData::Type::ShapeGroup:
        case LOTData::Type::Layer: {
            visitChildren(static_cast<LOTGroupData *>(obj));
            break;
        }
        default:
            break;
        }
    }
};

void LOTCompositionData::processRepeaterObjects()
{
    LottieRepeaterProcesser visitor;
    visitor.visit(mRootLayer.get());
}

VMatrix LOTRepeaterTransform::matrix(int frameNo, float multiplier) const
{
    VPointF scale = mScale.value(frameNo) / 100.f;
    scale.setX(std::pow(scale.x(), multiplier));
    scale.setY(std::pow(scale.y(), multiplier));
    VMatrix m;
    m.translate(mPosition.value(frameNo) * multiplier)
        .translate(mAnchor.value(frameNo))
        .scale(scale)
        .rotate(mRotation.value(frameNo) * multiplier)
        .translate(-mAnchor.value(frameNo));

    return m;
}

VMatrix TransformData::matrix(int frameNo, bool autoOrient) const
{
    VMatrix m;
    VPointF position;
    if (mSeparate) {
        position.setX(mX.value(frameNo));
        position.setY(mY.value(frameNo));
    } else {
        position = mPosition.value(frameNo);
    }

    float angle = autoOrient ? mPosition.angle(frameNo) : 0;
    if (m3D) {
        m.translate(position)
            .rotate(m3D->mRz.value(frameNo) + angle)
            .rotate(m3D->mRy.value(frameNo), VMatrix::Axis::Y)
            .rotate(m3D->mRx.value(frameNo), VMatrix::Axis::X)
            .scale(mScale.value(frameNo) / 100.f)
            .translate(-mAnchor.value(frameNo));
    } else {
        m.translate(position)
            .rotate(mRotation.value(frameNo) + angle)
            .scale(mScale.value(frameNo) / 100.f)
            .translate(-mAnchor.value(frameNo));
    }
    return m;
}

int LOTStrokeData::getDashInfo(int frameNo, float *array) const
{
    if (!mDash.mDashCount) return 0;
    // odd case
    if (mDash.mDashCount % 2) {
        for (int i = 0; i < mDash.mDashCount; i++) {
            array[i] = mDash.mDashArray[i].value(frameNo);
        }
        return mDash.mDashCount;
    } else {  // even case when last gap info is not provided.
        int i;
        for (i = 0; i < mDash.mDashCount - 1; i++) {
            array[i] = mDash.mDashArray[i].value(frameNo);
        }
        array[i] = array[i - 1];
        array[i + 1] = mDash.mDashArray[i].value(frameNo);
        return mDash.mDashCount + 1;
    }
}

int LOTGStrokeData::getDashInfo(int frameNo, float *array) const
{
    if (!mDash.mDashCount) return 0;
    // odd case
    if (mDash.mDashCount % 2) {
        for (int i = 0; i < mDash.mDashCount; i++) {
            array[i] = mDash.mDashArray[i].value(frameNo);
        }
        return mDash.mDashCount;
    } else {  // even case when last gap info is not provided.
        int i;
        for (i = 0; i < mDash.mDashCount - 1; i++) {
            array[i] = mDash.mDashArray[i].value(frameNo);
        }
        array[i] = array[i - 1];
        array[i + 1] = mDash.mDashArray[i].value(frameNo);
        return mDash.mDashCount + 1;
    }
}

/**
 * Both the color stops and opacity stops are in the same array.
 * There are {@link #colorPoints} colors sequentially as:
 * [
 *     ...,
 *     position,
 *     red,
 *     green,
 *     blue,
 *     ...
 * ]
 *
 * The remainder of the array is the opacity stops sequentially as:
 * [
 *     ...,
 *     position,
 *     opacity,
 *     ...
 * ]
 */
void LOTGradient::populate(VGradientStops &stops, int frameNo)
{
    LottieGradient gradData = mGradient.value(frameNo);
    int            size = gradData.mGradient.size();
    float *        ptr = gradData.mGradient.data();
    int            colorPoints = mColorPoints;
    if (colorPoints == -1) {  // for legacy bodymovin (ref: lottie-android)
        colorPoints = size / 4;
    }
    int    opacityArraySize = size - colorPoints * 4;
    float *opacityPtr = ptr + (colorPoints * 4);
    stops.clear();
    int j = 0;
    for (int i = 0; i < colorPoints; i++) {
        float       colorStop = ptr[0];
        LottieColor color = LottieColor(ptr[1], ptr[2], ptr[3]);
        if (opacityArraySize) {
            if (j == opacityArraySize) {
                // already reached the end
                float stop1 = opacityPtr[j - 4];
                float op1 = opacityPtr[j - 3];
                float stop2 = opacityPtr[j - 2];
                float op2 = opacityPtr[j - 1];
                if (colorStop > stop2) {
                    stops.push_back(
                        std::make_pair(colorStop, color.toColor(op2)));
                } else {
                    float progress = (colorStop - stop1) / (stop2 - stop1);
                    float opacity = op1 + progress * (op2 - op1);
                    stops.push_back(
                        std::make_pair(colorStop, color.toColor(opacity)));
                }
                continue;
            }
            for (; j < opacityArraySize; j += 2) {
                float opacityStop = opacityPtr[j];
                if (opacityStop < colorStop) {
                    // add a color using opacity stop
                    stops.push_back(std::make_pair(
                        opacityStop, color.toColor(opacityPtr[j + 1])));
                    continue;
                }
                // add a color using color stop
                if (j == 0) {
                    stops.push_back(std::make_pair(
                        colorStop, color.toColor(opacityPtr[j + 1])));
                } else {
                    float progress = (colorStop - opacityPtr[j - 2]) /
                                     (opacityPtr[j] - opacityPtr[j - 2]);
                    float opacity =
                        opacityPtr[j - 1] +
                        progress * (opacityPtr[j + 1] - opacityPtr[j - 1]);
                    stops.push_back(
                        std::make_pair(colorStop, color.toColor(opacity)));
                }
                j += 2;
                break;
            }
        } else {
            stops.push_back(std::make_pair(colorStop, color.toColor()));
        }
        ptr += 4;
    }
}

void LOTGradient::update(std::unique_ptr<VGradient> &grad, int frameNo)
{
    bool init = false;
    if (!grad) {
        if (mGradientType == 1)
            grad = std::make_unique<VLinearGradient>(0, 0, 0, 0);
        else
            grad = std::make_unique<VRadialGradient>(0, 0, 0, 0, 0, 0);
        grad->mSpread = VGradient::Spread::Pad;
        init = true;
    }

    if (!mGradient.isStatic() || init) {
        populate(grad->mStops, frameNo);
    }

    if (mGradientType == 1) {  // linear gradient
        VPointF start = mStartPoint.value(frameNo);
        VPointF end = mEndPoint.value(frameNo);
        grad->linear.x1 = start.x();
        grad->linear.y1 = start.y();
        grad->linear.x2 = end.x();
        grad->linear.y2 = end.y();
    } else {  // radial gradient
        VPointF start = mStartPoint.value(frameNo);
        VPointF end = mEndPoint.value(frameNo);
        grad->radial.cx = start.x();
        grad->radial.cy = start.y();
        grad->radial.cradius =
            VLine::length(start.x(), start.y(), end.x(), end.y());
        /*
         * Focal point is the point lives in highlight length distance from
         * center along the line (start, end)  and rotated by highlight angle.
         * below calculation first finds the quadrant(angle) on which the point
         * lives by applying inverse slope formula then adds the rotation angle
         * to find the final angle. then point is retrived using circle equation
         * of center, angle and distance.
         */
        float progress = mHighlightLength.value(frameNo) / 100.0f;
        if (vCompare(progress, 1.0f)) progress = 0.99f;
        float startAngle = VLine(start, end).angle();
        float highlightAngle = mHighlightAngle.value(frameNo);
        float angle = ((startAngle + highlightAngle) * M_PI) / 180.0f;
        grad->radial.fx =
            grad->radial.cx + std::cos(angle) * progress * grad->radial.cradius;
        grad->radial.fy =
            grad->radial.cy + std::sin(angle) * progress * grad->radial.cradius;
        // Lottie dosen't have any focal radius concept.
        grad->radial.fradius = 0;
    }
}

void LOTAsset::loadImageData(std::string data)
{
    if (!data.empty())
        mBitmap = VImageLoader::instance().load(data.c_str(), data.length());
}

void LOTAsset::loadImagePath(std::string path)
{
    if (!path.empty()) mBitmap = VImageLoader::instance().load(path.c_str());
}
