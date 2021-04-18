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

#include "lottiemodel.h"
#include <cassert>
#include <iterator>
#include <stack>
#include "vimageloader.h"
#include "vline.h"

using namespace rlottie::internal;

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
    void visitChildren(model::Group *obj)
    {
        for (auto i = obj->mChildren.rbegin(); i != obj->mChildren.rend();
             ++i) {
            auto child = (*i);
            if (child->type() == model::Object::Type::Repeater) {
                model::Repeater *repeater =
                    static_cast<model::Repeater *>(child);
                // check if this repeater is already processed
                // can happen if the layer is an asset and referenced by
                // multiple layer.
                if (repeater->processed()) continue;

                repeater->markProcessed();

                auto content = repeater->content();
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
            }
            visit(child);
        }
    }

    void visit(model::Object *obj)
    {
        switch (obj->type()) {
        case model::Object::Type::Group:
        case model::Object::Type::Layer: {
            visitChildren(static_cast<model::Group *>(obj));
            break;
        }
        default:
            break;
        }
    }
};

class LottieUpdateStatVisitor {
    model::Composition::Stats *stat;

public:
    explicit LottieUpdateStatVisitor(model::Composition::Stats *s) : stat(s) {}
    void visitChildren(model::Group *obj)
    {
        for (const auto &child : obj->mChildren) {
            if (child) visit(child);
        }
    }
    void visitLayer(model::Layer *layer)
    {
        switch (layer->mLayerType) {
        case model::Layer::Type::Precomp:
            stat->precompLayerCount++;
            break;
        case model::Layer::Type::Null:
            stat->nullLayerCount++;
            break;
        case model::Layer::Type::Shape:
            stat->shapeLayerCount++;
            break;
        case model::Layer::Type::Solid:
            stat->solidLayerCount++;
            break;
        case model::Layer::Type::Image:
            stat->imageLayerCount++;
            break;
        default:
            break;
        }
        visitChildren(layer);
    }
    void visit(model::Object *obj)
    {
        switch (obj->type()) {
        case model::Object::Type::Layer: {
            visitLayer(static_cast<model::Layer *>(obj));
            break;
        }
        case model::Object::Type::Repeater: {
            visitChildren(static_cast<model::Repeater *>(obj)->content());
            break;
        }
        case model::Object::Type::Group: {
            visitChildren(static_cast<model::Group *>(obj));
            break;
        }
        default:
            break;
        }
    }
};

void model::Composition::processRepeaterObjects()
{
    LottieRepeaterProcesser visitor;
    visitor.visit(mRootLayer);
}

void model::Composition::updateStats()
{
    LottieUpdateStatVisitor visitor(&mStats);
    visitor.visit(mRootLayer);
}

VMatrix model::Repeater::Transform::matrix(int frameNo, float multiplier) const
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

VMatrix model::Transform::Data::matrix(int frameNo, bool autoOrient) const
{
    VMatrix m;
    VPointF position;
    if (mExtra && mExtra->mSeparate) {
        position.setX(mExtra->mSeparateX.value(frameNo));
        position.setY(mExtra->mSeparateY.value(frameNo));
    } else {
        position = mPosition.value(frameNo);
    }

    float angle = autoOrient ? mPosition.angle(frameNo) : 0;
    if (mExtra && mExtra->m3DData) {
        m.translate(position)
            .rotate(mExtra->m3DRz.value(frameNo) + angle)
            .rotate(mExtra->m3DRy.value(frameNo), VMatrix::Axis::Y)
            .rotate(mExtra->m3DRx.value(frameNo), VMatrix::Axis::X)
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

void model::Dash::getDashInfo(int frameNo, std::vector<float> &result) const
{
    result.clear();

    if (mData.empty()) return;

    if (result.capacity() < mData.size()) result.reserve(mData.size() + 1);

    for (const auto &elm : mData) result.push_back(elm.value(frameNo));

    // if the size is even then we are missing last
    // gap information which is same as the last dash value
    // copy it from the last dash value.
    // NOTE: last value is the offset and last-1 is the last dash value.
    auto size = result.size();
    if ((size % 2) == 0) {
        // copy offset value to end.
        result.push_back(result.back());
        // copy dash value to gap.
        result[size - 1] = result[size - 2];
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
void model::Gradient::populate(VGradientStops &stops, int frameNo)
{
    model::Gradient::Data gradData = mGradient.value(frameNo);
    auto                  size = gradData.mGradient.size();
    float *               ptr = gradData.mGradient.data();
    int                   colorPoints = mColorPoints;
    if (colorPoints == -1) {  // for legacy bodymovin (ref: lottie-android)
        colorPoints = int(size / 4);
    }
    auto   opacityArraySize = size - colorPoints * 4;
    float *opacityPtr = ptr + (colorPoints * 4);
    stops.clear();
    size_t j = 0;
    for (int i = 0; i < colorPoints; i++) {
        float        colorStop = ptr[0];
        model::Color color = model::Color(ptr[1], ptr[2], ptr[3]);
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

void model::Gradient::update(std::unique_ptr<VGradient> &grad, int frameNo)
{
    bool init = false;
    if (!grad) {
        if (mGradientType == 1)
            grad = std::make_unique<VGradient>(VGradient::Type::Linear);
        else
            grad = std::make_unique<VGradient>(VGradient::Type::Radial);
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
        float                  startAngle = VLine(start, end).angle();
        float                  highlightAngle = mHighlightAngle.value(frameNo);
        static constexpr float K_PI = 3.1415926f;
        float angle = (startAngle + highlightAngle) * (K_PI / 180.0f);
        grad->radial.fx =
            grad->radial.cx + std::cos(angle) * progress * grad->radial.cradius;
        grad->radial.fy =
            grad->radial.cy + std::sin(angle) * progress * grad->radial.cradius;
        // Lottie dosen't have any focal radius concept.
        grad->radial.fradius = 0;
    }
}

void model::Asset::loadImageData(std::string data)
{
    if (!data.empty())
        mBitmap = VImageLoader::instance().load(data.c_str(), data.length());
}

void model::Asset::loadImagePath(std::string path)
{
    if (!path.empty()) mBitmap = VImageLoader::instance().load(path.c_str());
}

std::vector<LayerInfo> model::Composition::layerInfoList() const
{
    if (!mRootLayer || mRootLayer->mChildren.empty()) return {};

    std::vector<LayerInfo> result;

    result.reserve(mRootLayer->mChildren.size());

    for (auto it : mRootLayer->mChildren) {
        auto layer = static_cast<model::Layer *>(it);
        result.emplace_back(layer->name(), layer->mInFrame, layer->mOutFrame);
    }

    return result;
}
