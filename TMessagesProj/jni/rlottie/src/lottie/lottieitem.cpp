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

#include "lottieitem.h"
#include <algorithm>
#include <cmath>
#include <iterator>
#include "lottiekeypath.h"
#include "vbitmap.h"
#include "vdasher.h"
#include "vpainter.h"
#include "vraster.h"

/* Lottie Layer Rules
 * 1. time stretch is pre calculated and applied to all the properties of the
 * lottilayer model and all its children
 * 2. The frame property could be reversed using,time-reverse layer property in
 * AE. which means (start frame > endFrame) 3.
 */

static bool transformProp(rlottie::Property prop)
{
    switch (prop) {
    case rlottie::Property::TrAnchor:
    case rlottie::Property::TrScale:
    case rlottie::Property::TrOpacity:
    case rlottie::Property::TrPosition:
    case rlottie::Property::TrRotation:
        return true;
    default:
        return false;
    }
}
static bool fillProp(rlottie::Property prop)
{
    switch (prop) {
    case rlottie::Property::Color:
    case rlottie::Property::FillOpacity:
        return true;
    default:
        return false;
    }
}

static bool strokeProp(rlottie::Property prop)
{
    switch (prop) {
    case rlottie::Property::Color:
    case rlottie::Property::StrokeOpacity:
    case rlottie::Property::StrokeWidth:
        return true;
    default:
        return false;
    }
}

LOTCompItem::LOTCompItem(LOTModel *model)
    : mUpdateViewBox(false), mCurFrameNo(-1)
{
    mCompData = model->mRoot.get();
    mRootLayer = createLayerItem(mCompData->mRootLayer.get());
    mRootLayer->setComplexContent(false);
    mViewSize = mCompData->size();
}

static bool isGoodParentLayer(LOTLayerItem *parent, LOTLayerItem *child) {
    do {
        if (parent == child) {
            return false;
        }
        parent = parent->resolvedParentLayer();
    } while (parent);
    return true;
}

void LOTCompItem::setValue(const std::string &keypath, LOTVariant &value)
{
    LOTKeyPath key(keypath);
    mRootLayer->resolveKeyPath(key, 0, value);
    mCurFrameNo = -1;
}

void LOTCompItem::resetCurrentFrame()
{
    mCurFrameNo = -1;
}

std::unique_ptr<LOTLayerItem> LOTCompItem::createLayerItem(
    LOTLayerData *layerData)
{
    switch (layerData->mLayerType) {
    case LayerType::Precomp: {
        return std::make_unique<LOTCompLayerItem>(layerData);
    }
    case LayerType::Solid: {
        return std::make_unique<LOTSolidLayerItem>(layerData);
    }
    case LayerType::Shape: {
        return std::make_unique<LOTShapeLayerItem>(layerData);
    }
    case LayerType::Null: {
        return std::make_unique<LOTNullLayerItem>(layerData);
    }
    case LayerType::Image: {
        return std::make_unique<LOTImageLayerItem>(layerData);
    }
    default:
        return nullptr;
        break;
    }
}

void LOTCompItem::resize(const VSize &size)
{
    if (mViewSize == size) return;
    mViewSize = size;
    mUpdateViewBox = true;
}

VSize LOTCompItem::size() const
{
    return mViewSize;
}

bool LOTCompItem::update(int frameNo)
{
    // check if cached frame is same as requested frame.
    if (!mUpdateViewBox && (mCurFrameNo == frameNo)) return false;

    /*
     * if viewbox dosen't scale exactly to the viewport
     * we scale the viewbox keeping AspectRatioPreserved and then align the
     * viewbox to the viewport using AlignCenter rule.
     */
    VSize viewPort = mViewSize;
    VSize viewBox = mCompData->size();

    float sx = float(viewPort.width()) / viewBox.width();
    float sy = float(viewPort.height()) / viewBox.height();
    float scale = fmin(sx, sy);
    float tx = (viewPort.width() - viewBox.width() * scale) * 0.5;
    float ty = (viewPort.height() - viewBox.height() * scale) * 0.5;

    VMatrix m;
    m.translate(tx, ty).scale(scale, scale);
    mRootLayer->update(frameNo, m, 1.0);

    mCurFrameNo = frameNo;
    mUpdateViewBox = false;
    return true;
}

void LOTCompItem::buildRenderTree()
{
    mRootLayer->buildLayerNode();
}

const LOTLayerNode *LOTCompItem::renderTree() const
{
    return mRootLayer->layerNode();
}

bool LOTCompItem::render(const rlottie::Surface &surface, bool clear)
{
    VBitmap bitmap(reinterpret_cast<uchar *>(surface.buffer()), surface.width(),
                   surface.height(), surface.bytesPerLine(),
                   VBitmap::Format::ARGB32);

    /* schedule all preprocess task for this frame at once.
     */
    mDrawableList.clear();
    mRootLayer->renderList(mDrawableList);
    VRect clip(0, 0, surface.drawRegionWidth(), surface.drawRegionHeight());
    for (auto &e : mDrawableList) {
        e->preprocess(clip);
    }

    VPainter painter(&bitmap, clear);
    // set sub surface area for drawing.
    painter.setDrawRegion(
        VRect(surface.drawRegionPosX(), surface.drawRegionPosY(),
              surface.drawRegionWidth(), surface.drawRegionHeight()));
    mRootLayer->render(&painter, {}, {});

    return true;
}

void LOTMaskItem::update(int frameNo, const VMatrix &            parentMatrix,
                         float /*parentAlpha*/, const DirtyFlag &flag)
{
    if (flag.testFlag(DirtyFlagBit::None) && mData->isStatic()) return;

    if (mData->mShape.isStatic()) {
        if (mLocalPath.empty()) {
            mData->mShape.value(frameNo).toPath(mLocalPath);
        }
    } else {
        mData->mShape.value(frameNo).toPath(mLocalPath);
    }
    /* mask item dosen't inherit opacity */
    mCombinedAlpha = mData->opacity(frameNo);

    mFinalPath.clone(mLocalPath);
    mFinalPath.transform(parentMatrix);

    mRasterizer.rasterize(mFinalPath);
    mRasterRequest = true;
}

VRle LOTMaskItem::rle()
{
    if (mRasterRequest) {
        mRasterRequest = false;
        if (!vCompare(mCombinedAlpha, 1.0f))
            mRasterizer.rle() *= (mCombinedAlpha * 255);
        if (mData->mInv) mRasterizer.rle().invert();
    }
    return mRasterizer.rle();
}

void LOTLayerItem::buildLayerNode()
{
    if (!mLayerCNode) {
        mLayerCNode = std::make_unique<LOTLayerNode>();
        mLayerCNode->mMaskList.ptr = nullptr;
        mLayerCNode->mMaskList.size = 0;
        mLayerCNode->mLayerList.ptr = nullptr;
        mLayerCNode->mLayerList.size = 0;
        mLayerCNode->mNodeList.ptr = nullptr;
        mLayerCNode->mNodeList.size = 0;
        mLayerCNode->mMatte = MatteNone;
        mLayerCNode->mVisible = 0;
        mLayerCNode->mAlpha = 255;
        mLayerCNode->mClipPath.ptPtr = nullptr;
        mLayerCNode->mClipPath.elmPtr = nullptr;
        mLayerCNode->mClipPath.ptCount = 0;
        mLayerCNode->mClipPath.elmCount = 0;
        mLayerCNode->name = name().c_str();
    }
    if (complexContent()) mLayerCNode->mAlpha = combinedAlpha() * 255;
    mLayerCNode->mVisible = visible();
    // update matte
    if (hasMatte()) {
        switch (mLayerData->mMatteType) {
        case MatteType::Alpha:
            mLayerCNode->mMatte = MatteAlpha;
            break;
        case MatteType::AlphaInv:
            mLayerCNode->mMatte = MatteAlphaInv;
            break;
        case MatteType::Luma:
            mLayerCNode->mMatte = MatteLuma;
            break;
        case MatteType::LumaInv:
            mLayerCNode->mMatte = MatteLumaInv;
            break;
        default:
            mLayerCNode->mMatte = MatteNone;
            break;
        }
    }
    if (mLayerMask) {
        mMasksCNode.clear();
        mMasksCNode.resize(mLayerMask->mMasks.size());
        size_t i = 0;
        for (const auto &mask : mLayerMask->mMasks) {
            LOTMask *                          cNode = &mMasksCNode[i++];
            const std::vector<VPath::Element> &elm = mask.mFinalPath.elements();
            const std::vector<VPointF> &       pts = mask.mFinalPath.points();
            const float *ptPtr = reinterpret_cast<const float *>(pts.data());
            const char * elmPtr = reinterpret_cast<const char *>(elm.data());
            cNode->mPath.ptPtr = ptPtr;
            cNode->mPath.ptCount = pts.size();
            cNode->mPath.elmPtr = elmPtr;
            cNode->mPath.elmCount = elm.size();
            cNode->mAlpha = mask.mCombinedAlpha * 255;
            switch (mask.maskMode()) {
            case LOTMaskData::Mode::Add:
                cNode->mMode = MaskAdd;
                break;
            case LOTMaskData::Mode::Substarct:
                cNode->mMode = MaskSubstract;
                break;
            case LOTMaskData::Mode::Intersect:
                cNode->mMode = MaskIntersect;
                break;
            case LOTMaskData::Mode::Difference:
                cNode->mMode = MaskDifference;
                break;
            default:
                cNode->mMode = MaskAdd;
                break;
            }
        }
        mLayerCNode->mMaskList.ptr = mMasksCNode.data();
        mLayerCNode->mMaskList.size = mMasksCNode.size();
    }
}

void LOTLayerItem::render(VPainter *painter, const VRle &inheritMask,
                          const VRle &matteRle)
{
    mDrawableList.clear();
    renderList(mDrawableList);

    VRle mask;
    if (mLayerMask) {
        mask = mLayerMask->maskRle(painter->clipBoundingRect());
        if (!inheritMask.empty()) mask = mask & inheritMask;
        // if resulting mask is empty then return.
        if (mask.empty()) return;
    } else {
        mask = inheritMask;
    }

    for (auto &i : mDrawableList) {
        painter->setBrush(i->mBrush);
        VRle rle = i->rle();
        if (matteRle.empty()) {
            if (mask.empty()) {
                // no mask no matte
                painter->drawRle(VPoint(), rle);
            } else {
                // only mask
                painter->drawRle(rle, mask);
            }

        } else {
            if (!mask.empty()) rle = rle & mask;

            if (rle.empty()) continue;
            if (matteType() == MatteType::AlphaInv) {
                rle = rle - matteRle;
                painter->drawRle(VPoint(), rle);
            } else {
                // render with matteRle as clip.
                painter->drawRle(rle, matteRle);
            }
        }
    }
}

LOTLayerMaskItem::LOTLayerMaskItem(LOTLayerData *layerData)
{
    if (!layerData->mExtra) return;

    mMasks.reserve(layerData->mExtra->mMasks.size());

    for (auto &i : layerData->mExtra->mMasks) {
        mMasks.emplace_back(i.get());
        mStatic &= i->isStatic();
    }
}

void LOTLayerMaskItem::update(int frameNo, const VMatrix &parentMatrix,
                              float parentAlpha, const DirtyFlag &flag)
{
    if (flag.testFlag(DirtyFlagBit::None) && isStatic()) return;

    for (auto &i : mMasks) {
        i.update(frameNo, parentMatrix, parentAlpha, flag);
    }
    mDirty = true;
}

VRle LOTLayerMaskItem::maskRle(const VRect &clipRect)
{
    if (!mDirty) return mRle;

    VRle rle;
    for (auto &i : mMasks) {
        switch (i.maskMode()) {
        case LOTMaskData::Mode::Add: {
            rle = rle + i.rle();
            break;
        }
        case LOTMaskData::Mode::Substarct: {
            if (rle.empty() && !clipRect.empty()) rle = VRle::toRle(clipRect);
            rle = rle - i.rle();
            break;
        }
        case LOTMaskData::Mode::Intersect: {
            if (rle.empty() && !clipRect.empty()) rle = VRle::toRle(clipRect);
            rle = rle & i.rle();
            break;
        }
        case LOTMaskData::Mode::Difference: {
            rle = rle ^ i.rle();
            break;
        }
        default:
            break;
        }
    }

    if (!rle.empty() && !rle.unique()) {
        mRle.clone(rle);
    } else {
        mRle = rle;
    }
    mDirty = false;
    return mRle;
}

LOTLayerItem::LOTLayerItem(LOTLayerData *layerData) : mLayerData(layerData)
{
    if (mLayerData->mHasMask)
        mLayerMask = std::make_unique<LOTLayerMaskItem>(mLayerData);
}

bool LOTLayerItem::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                  LOTVariant &value)
{
    if (!keyPath.matches(name(), depth)) {
        return false;
    }

    if (!keyPath.skip(name())) {
        if (keyPath.fullyResolvesTo(name(), depth) && transformProp(value.property())) {
            mDirtyFlag = DirtyFlagBit::All;
        }
    }
    return true;
}

bool LOTShapeLayerItem::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                       LOTVariant &value)
{
    if (LOTLayerItem::resolveKeyPath(keyPath, depth, value)) {
        if (keyPath.propagate(name(), depth)) {
            uint newDepth = keyPath.nextDepth(name(), depth);
            if (mRoot->resolveKeyPath(keyPath, newDepth, value)) {
                mDirtyFlag = DirtyFlagBit::All;
            }
        }
        return true;
    }
    return false;
}

bool LOTCompLayerItem::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                      LOTVariant &value)
{
    if (LOTLayerItem::resolveKeyPath(keyPath, depth, value)) {
        if (keyPath.propagate(name(), depth)) {
            uint newDepth = keyPath.nextDepth(name(), depth);
            for (const auto &layer : mLayers) {
                if (layer->resolveKeyPath(keyPath, newDepth, value)) {
                    mDirtyFlag = DirtyFlagBit::All;
                }
            }
        }
        return true;
    }
    return false;
}

void LOTLayerItem::update(int frameNumber, const VMatrix &parentMatrix,
                          float parentAlpha)
{
    mFrameNo = frameNumber;
    // 1. check if the layer is part of the current frame
    if (!visible()) return;

    float alpha = parentAlpha * opacity(frameNo());
    if (vIsZero(alpha)) {
        mCombinedAlpha = 0;
        return;
    }

    // 2. calculate the parent matrix and alpha
    VMatrix m = matrix(frameNo());
    m *= parentMatrix;

    // 3. update the dirty flag based on the change
    if (!mCombinedMatrix.fuzzyCompare(m)) {
        mDirtyFlag |= DirtyFlagBit::Matrix;
    }
    if (!vCompare(mCombinedAlpha, alpha)) {
        mDirtyFlag |= DirtyFlagBit::Alpha;
    }
    mCombinedMatrix = m;
    mCombinedAlpha = alpha;

    // 4. update the mask
    if (mLayerMask) {
        mLayerMask->update(frameNo(), m, alpha, mDirtyFlag);
    }

    // 5. if no parent property change and layer is static then nothing to do.
    if (!mLayerData->precompLayer() && flag().testFlag(DirtyFlagBit::None) &&
        isStatic())
        return;

    // 6. update the content of the layer
    updateContent();

    // 7. reset the dirty flag
    mDirtyFlag = DirtyFlagBit::None;
}

VMatrix LOTLayerItem::matrix(int frameNo) const
{
    return mParentLayer
               ? (mLayerData->matrix(frameNo) * mParentLayer->matrix(frameNo))
               : mLayerData->matrix(frameNo);
}

bool LOTLayerItem::visible() const
{
    if (frameNo() >= mLayerData->inFrame() &&
        frameNo() < mLayerData->outFrame())
        return true;
    else
        return false;
}

LOTCompLayerItem::LOTCompLayerItem(LOTLayerData *layerModel)
    : LOTLayerItem(layerModel)
{
    // 1. create layer item
    for (auto &i : mLayerData->mChildren) {
        if (i->type() != LOTData::Type::Layer) {
            continue;
        }
        LOTLayerData *layerModel = static_cast<LOTLayerData *>(i.get());
        auto          layerItem = LOTCompItem::createLayerItem(layerModel);
        if (layerItem) mLayers.push_back(std::move(layerItem));
    }

    // 2. update parent layer
    for (const auto &layer : mLayers) {
        int id = layer->parentId();
        if (id >= 0) {
            auto search =
                std::find_if(mLayers.begin(), mLayers.end(),
                             [id](const auto &val) { return val->id() == id; });
            if (search != mLayers.end() &&
                isGoodParentLayer((*search).get(), layer.get())) {
                layer->setParentLayer((*search).get());
            }
        }
    }

    // 3. keep the layer in back-to-front order.
    // as lottie model keeps the data in front-toback-order.
    std::reverse(mLayers.begin(), mLayers.end());

    // 4. check if its a nested composition
    if (!layerModel->layerSize().empty()) {
        mClipper = std::make_unique<LOTClipperItem>(layerModel->layerSize());
    }

    if (mLayers.size() > 1) setComplexContent(true);
}

void LOTCompLayerItem::buildLayerNode()
{
    LOTLayerItem::buildLayerNode();
    if (mClipper) {
        const std::vector<VPath::Element> &elm = mClipper->mPath.elements();
        const std::vector<VPointF> &       pts = mClipper->mPath.points();
        const float *ptPtr = reinterpret_cast<const float *>(pts.data());
        const char * elmPtr = reinterpret_cast<const char *>(elm.data());
        layerNode()->mClipPath.ptPtr = ptPtr;
        layerNode()->mClipPath.elmPtr = elmPtr;
        layerNode()->mClipPath.ptCount = 2 * pts.size();
        layerNode()->mClipPath.elmCount = elm.size();
    }
    if (mLayers.size() != mLayersCNode.size()) {
        for (const auto &layer : mLayers) {
            layer->buildLayerNode();
            mLayersCNode.push_back(layer->layerNode());
        }
        layerNode()->mLayerList.ptr = mLayersCNode.data();
        layerNode()->mLayerList.size = mLayersCNode.size();
    } else {
        for (const auto &layer : mLayers) {
            layer->buildLayerNode();
        }
    }
}

void LOTCompLayerItem::render(VPainter *painter, const VRle &inheritMask,
                              const VRle &matteRle)
{
    if (vIsZero(combinedAlpha())) return;

    if (vCompare(combinedAlpha(), 1.0)) {
        renderHelper(painter, inheritMask, matteRle);
    } else {
        if (complexContent()) {
            VSize    size = painter->clipBoundingRect().size();
            VPainter srcPainter;
            VBitmap  srcBitmap(size.width(), size.height(),
                              VBitmap::Format::ARGB32);
            srcPainter.begin(&srcBitmap, true);
            renderHelper(&srcPainter, inheritMask, matteRle);
            srcPainter.end();
            painter->drawBitmap(VPoint(), srcBitmap, combinedAlpha() * 255);
        } else {
            renderHelper(painter, inheritMask, matteRle);
        }
    }
}

void LOTCompLayerItem::renderHelper(VPainter *painter, const VRle &inheritMask,
                                    const VRle &matteRle)
{
    VRle mask;
    if (mLayerMask) {
        mask = mLayerMask->maskRle(painter->clipBoundingRect());
        if (!inheritMask.empty()) mask = mask & inheritMask;
        // if resulting mask is empty then return.
        if (mask.empty()) return;
    } else {
        mask = inheritMask;
    }

    if (mClipper) {
        if (mask.empty()) {
            mask = mClipper->rle();
        } else {
            mask = mClipper->rle() & mask;
        }
    }

    LOTLayerItem *matte = nullptr;
    for (const auto &layer : mLayers) {
        if (layer->hasMatte()) {
            matte = layer.get();
        } else {
            if (layer->visible()) {
                if (matte) {
                    if (matte->visible())
                        renderMatteLayer(painter, mask, matteRle, matte,
                                         layer.get());
                } else {
                    layer->render(painter, mask, matteRle);
                }
            }
            matte = nullptr;
        }
    }
}

void LOTCompLayerItem::renderMatteLayer(VPainter *painter, const VRle &mask,
                                        const VRle &  matteRle,
                                        LOTLayerItem *layer, LOTLayerItem *src)
{
    VSize size = painter->clipBoundingRect().size();
    // Decide if we can use fast matte.
    // 1. draw src layer to matte buffer
    VPainter srcPainter;
    src->bitmap().reset(size.width(), size.height(),
                        VBitmap::Format::ARGB32);
    srcPainter.begin(&src->bitmap(), true);
    src->render(&srcPainter, mask, matteRle);
    srcPainter.end();

    // 2. draw layer to layer buffer
    VPainter layerPainter;
    layer->bitmap().reset(size.width(), size.height(),
                          VBitmap::Format::ARGB32);
    layerPainter.begin(&layer->bitmap(), true);
    layer->render(&layerPainter, mask, matteRle);

    // 2.1update composition mode
    switch (layer->matteType()) {
    case MatteType::Alpha:
    case MatteType::Luma: {
        layerPainter.setCompositionMode(
            VPainter::CompositionMode::CompModeDestIn);
        break;
    }
    case MatteType::AlphaInv:
    case MatteType::LumaInv: {
        layerPainter.setCompositionMode(
            VPainter::CompositionMode::CompModeDestOut);
        break;
    }
    default:
        break;
    }

    // 2.2 update srcBuffer if the matte is luma type
    if (layer->matteType() == MatteType::Luma ||
        layer->matteType() == MatteType::LumaInv) {
        src->bitmap().updateLuma();
    }

    // 2.3 draw src buffer as mask
    layerPainter.drawBitmap(VPoint(), src->bitmap());
    layerPainter.end();
    // 3. draw the result buffer into painter
    painter->drawBitmap(VPoint(), layer->bitmap());
}

void LOTClipperItem::update(const VMatrix &matrix)
{
    mPath.reset();
    mPath.addRect(VRectF(0, 0, mSize.width(), mSize.height()));
    mPath.transform(matrix);
    mRasterizer.rasterize(mPath);
}

VRle LOTClipperItem::rle()
{
    return mRasterizer.rle();
}

void LOTCompLayerItem::updateContent()
{
    if (mClipper && flag().testFlag(DirtyFlagBit::Matrix)) {
        mClipper->update(combinedMatrix());
    }
    int   mappedFrame = mLayerData->timeRemap(frameNo());
    float alpha = combinedAlpha();
    if (complexContent()) alpha = 1;
    for (const auto &layer : mLayers) {
        layer->update(mappedFrame, combinedMatrix(), alpha);
    }
}

void LOTCompLayerItem::renderList(std::vector<VDrawable *> &list)
{
    if (!visible() || vIsZero(combinedAlpha())) return;

    LOTLayerItem *matte = nullptr;
    for (const auto &layer : mLayers) {
        if (layer->hasMatte()) {
            matte = layer.get();
        } else {
            if (layer->visible()) {
                if (matte) {
                    if (matte->visible()) {
                        layer->renderList(list);
                        matte->renderList(list);
                    }
                } else {
                    layer->renderList(list);
                }
            }
            matte = nullptr;
        }
    }
}

LOTSolidLayerItem::LOTSolidLayerItem(LOTLayerData *layerData)
    : LOTLayerItem(layerData)
{
}

void LOTSolidLayerItem::updateContent()
{
    if (flag() & DirtyFlagBit::Matrix) {
        VPath path;
        path.addRect(
                VRectF(0, 0,
                       mLayerData->layerSize().width(),
                       mLayerData->layerSize().height()));
        path.transform(combinedMatrix());
        mRenderNode.mFlag |= VDrawable::DirtyState::Path;
        mRenderNode.mPath = path;
    }
    if (flag() & DirtyFlagBit::Alpha) {
        LottieColor color = mLayerData->solidColor();
        VBrush      brush(color.toColor(combinedAlpha()));
        mRenderNode.setBrush(brush);
        mRenderNode.mFlag |= VDrawable::DirtyState::Brush;
    }
}

void LOTSolidLayerItem::buildLayerNode()
{
    LOTLayerItem::buildLayerNode();

    mDrawableList.clear();
    renderList(mDrawableList);

    mCNodeList.clear();
    for (auto &i : mDrawableList) {
        LOTDrawable *lotDrawable = static_cast<LOTDrawable *>(i);
        lotDrawable->sync();
        mCNodeList.push_back(lotDrawable->mCNode.get());
    }
    layerNode()->mNodeList.ptr = mCNodeList.data();
    layerNode()->mNodeList.size = mCNodeList.size();
}

void LOTSolidLayerItem::renderList(std::vector<VDrawable *> &list)
{
    if (!visible() || vIsZero(combinedAlpha())) return;

    list.push_back(&mRenderNode);
}

LOTImageLayerItem::LOTImageLayerItem(LOTLayerData *layerData)
    : LOTLayerItem(layerData)
{
    if (!mLayerData->asset()) return;

    VBrush brush(mLayerData->asset()->bitmap());
    mRenderNode.setBrush(brush);
}

void LOTImageLayerItem::updateContent()
{
    if (!mLayerData->asset()) return;

    if (flag() & DirtyFlagBit::Matrix) {
        VPath path;
        path.addRect(VRectF(0, 0, mLayerData->asset()->mWidth,
                            mLayerData->asset()->mHeight));
        path.transform(combinedMatrix());
        mRenderNode.mFlag |= VDrawable::DirtyState::Path;
        mRenderNode.mPath = path;
        mRenderNode.mBrush.setMatrix(combinedMatrix());
    }

    if (flag() & DirtyFlagBit::Alpha) {
        //@TODO handle alpha with the image.
    }
}

void LOTImageLayerItem::renderList(std::vector<VDrawable *> &list)
{
    if (!visible() || vIsZero(combinedAlpha())) return;

    list.push_back(&mRenderNode);
}

void LOTImageLayerItem::buildLayerNode()
{
    LOTLayerItem::buildLayerNode();

    mDrawableList.clear();
    renderList(mDrawableList);

    mCNodeList.clear();
    for (auto &i : mDrawableList) {
        LOTDrawable *lotDrawable = static_cast<LOTDrawable *>(i);
        lotDrawable->sync();

        lotDrawable->mCNode->mImageInfo.data =
            lotDrawable->mBrush.mTexture.data();
        lotDrawable->mCNode->mImageInfo.width =
            lotDrawable->mBrush.mTexture.width();
        lotDrawable->mCNode->mImageInfo.height =
            lotDrawable->mBrush.mTexture.height();

        lotDrawable->mCNode->mImageInfo.mMatrix.m11 = combinedMatrix().m_11();
        lotDrawable->mCNode->mImageInfo.mMatrix.m12 = combinedMatrix().m_12();
        lotDrawable->mCNode->mImageInfo.mMatrix.m13 = combinedMatrix().m_13();

        lotDrawable->mCNode->mImageInfo.mMatrix.m21 = combinedMatrix().m_21();
        lotDrawable->mCNode->mImageInfo.mMatrix.m22 = combinedMatrix().m_22();
        lotDrawable->mCNode->mImageInfo.mMatrix.m23 = combinedMatrix().m_23();

        lotDrawable->mCNode->mImageInfo.mMatrix.m31 = combinedMatrix().m_tx();
        lotDrawable->mCNode->mImageInfo.mMatrix.m32 = combinedMatrix().m_ty();
        lotDrawable->mCNode->mImageInfo.mMatrix.m33 = combinedMatrix().m_33();

        mCNodeList.push_back(lotDrawable->mCNode.get());
    }
    layerNode()->mNodeList.ptr = mCNodeList.data();
    layerNode()->mNodeList.size = mCNodeList.size();
}

LOTNullLayerItem::LOTNullLayerItem(LOTLayerData *layerData)
    : LOTLayerItem(layerData)
{
}
void LOTNullLayerItem::updateContent() {}

LOTShapeLayerItem::LOTShapeLayerItem(LOTLayerData *layerData)
    : LOTLayerItem(layerData),
      mRoot(std::make_unique<LOTContentGroupItem>(nullptr))
{
    mRoot->addChildren(layerData);

    std::vector<LOTPathDataItem *> list;
    mRoot->processPaintItems(list);

    if (layerData->hasPathOperator()) {
        list.clear();
        mRoot->processTrimItems(list);
    }
}

std::unique_ptr<LOTContentItem> LOTShapeLayerItem::createContentItem(
    LOTData *contentData)
{
    switch (contentData->type()) {
    case LOTData::Type::ShapeGroup: {
        return std::make_unique<LOTContentGroupItem>(
            static_cast<LOTGroupData *>(contentData));
    }
    case LOTData::Type::Rect: {
        return std::make_unique<LOTRectItem>(
            static_cast<LOTRectData *>(contentData));
    }
    case LOTData::Type::Ellipse: {
        return std::make_unique<LOTEllipseItem>(
            static_cast<LOTEllipseData *>(contentData));
    }
    case LOTData::Type::Shape: {
        return std::make_unique<LOTShapeItem>(
            static_cast<LOTShapeData *>(contentData));
    }
    case LOTData::Type::Polystar: {
        return std::make_unique<LOTPolystarItem>(
            static_cast<LOTPolystarData *>(contentData));
    }
    case LOTData::Type::Fill: {
        return std::make_unique<LOTFillItem>(
            static_cast<LOTFillData *>(contentData));
    }
    case LOTData::Type::GFill: {
        return std::make_unique<LOTGFillItem>(
            static_cast<LOTGFillData *>(contentData));
    }
    case LOTData::Type::Stroke: {
        return std::make_unique<LOTStrokeItem>(
            static_cast<LOTStrokeData *>(contentData));
    }
    case LOTData::Type::GStroke: {
        return std::make_unique<LOTGStrokeItem>(
            static_cast<LOTGStrokeData *>(contentData));
    }
    case LOTData::Type::Repeater: {
        return std::make_unique<LOTRepeaterItem>(
            static_cast<LOTRepeaterData *>(contentData));
    }
    case LOTData::Type::Trim: {
        return std::make_unique<LOTTrimItem>(
            static_cast<LOTTrimData *>(contentData));
    }
    default:
        return nullptr;
        break;
    }
}

void LOTShapeLayerItem::updateContent()
{
    mRoot->update(frameNo(), combinedMatrix(), combinedAlpha(), flag());

    if (mLayerData->hasPathOperator()) {
        mRoot->applyTrim();
    }
}

void LOTShapeLayerItem::buildLayerNode()
{
    LOTLayerItem::buildLayerNode();

    mDrawableList.clear();
    renderList(mDrawableList);

    mCNodeList.clear();
    for (auto &i : mDrawableList) {
        LOTDrawable *lotDrawable = static_cast<LOTDrawable *>(i);
        lotDrawable->sync();
        mCNodeList.push_back(lotDrawable->mCNode.get());
    }
    layerNode()->mNodeList.ptr = mCNodeList.data();
    layerNode()->mNodeList.size = mCNodeList.size();
}

void LOTShapeLayerItem::renderList(std::vector<VDrawable *> &list)
{
    if (!visible() || vIsZero(combinedAlpha())) return;
    mRoot->renderList(list);
}

bool LOTContentGroupItem::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                         LOTVariant &value)
{
    if (!keyPath.matches(name(), depth)) {
        return false;
    }

    if (!keyPath.skip(name())) {
        if (keyPath.fullyResolvesTo(name(), depth) &&
            transformProp(value.property())) {
            //@TODO handle property update
        }
    }

    if (keyPath.propagate(name(), depth)) {
        uint newDepth = keyPath.nextDepth(name(), depth);
        for (auto &child : mContents) {
            child->resolveKeyPath(keyPath, newDepth, value);
        }
    }
    return true;
}

bool LOTFillItem::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                 LOTVariant &value)
{
    if (!keyPath.matches(mModel.name(), depth)) {
        return false;
    }

    if (keyPath.fullyResolvesTo(mModel.name(), depth) &&
        fillProp(value.property())) {
        mModel.filter().addValue(value);
        return true;
    }
    return false;
}

bool LOTStrokeItem::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                   LOTVariant &value)
{
    if (!keyPath.matches(mModel.name(), depth)) {
        return false;
    }

    if (keyPath.fullyResolvesTo(mModel.name(), depth) &&
        strokeProp(value.property())) {
        mModel.filter().addValue(value);
        return true;
    }
    return false;
}

LOTContentGroupItem::LOTContentGroupItem(LOTGroupData *data)
    : LOTContentItem(ContentType::Group), mData(data)
{
    addChildren(mData);
}

void LOTContentGroupItem::addChildren(LOTGroupData *data)
{
    if (!data) return;

    for (auto &i : data->mChildren) {
        auto content = LOTShapeLayerItem::createContentItem(i.get());
        if (content) {
            content->setParent(this);
            mContents.push_back(std::move(content));
        }
    }

    // keep the content in back-to-front order.
    std::reverse(mContents.begin(), mContents.end());
}

void LOTContentGroupItem::update(int frameNo, const VMatrix &parentMatrix,
                                 float parentAlpha, const DirtyFlag &flag)
{
    VMatrix   m = parentMatrix;
    float     alpha = parentAlpha;
    DirtyFlag newFlag = flag;

    if (mData && mData->mTransform) {
        // update the matrix and the flag
        if ((flag & DirtyFlagBit::Matrix) || !mData->mTransform->isStatic()) {
            newFlag |= DirtyFlagBit::Matrix;
        }
        m = mData->mTransform->matrix(frameNo);
        m *= parentMatrix;
        alpha *= mData->mTransform->opacity(frameNo);

        if (!vCompare(alpha, parentAlpha)) {
            newFlag |= DirtyFlagBit::Alpha;
        }
    }

    mMatrix = m;

    for (const auto &content : mContents) {
        content->update(frameNo, m, alpha, newFlag);
    }
}

void LOTContentGroupItem::applyTrim()
{
    for (auto i = mContents.rbegin(); i != mContents.rend(); ++i) {
        auto content = (*i).get();
        switch (content->type()) {
        case ContentType::Trim: {
            static_cast<LOTTrimItem *>(content)->update();
            break;
        }
        case ContentType::Group: {
            static_cast<LOTContentGroupItem *>(content)->applyTrim();
            break;
        }
        default:
            break;
        }
    }
}

void LOTContentGroupItem::renderList(std::vector<VDrawable *> &list)
{
    for (const auto &content : mContents) {
        content->renderList(list);
    }
}

void LOTContentGroupItem::processPaintItems(
    std::vector<LOTPathDataItem *> &list)
{
    int curOpCount = list.size();
    for (auto i = mContents.rbegin(); i != mContents.rend(); ++i) {
        auto content = (*i).get();
        switch (content->type()) {
        case ContentType::Path: {
            list.push_back(static_cast<LOTPathDataItem *>(content));
            break;
        }
        case ContentType::Paint: {
            static_cast<LOTPaintDataItem *>(content)->addPathItems(list,
                                                                   curOpCount);
            break;
        }
        case ContentType::Group: {
            static_cast<LOTContentGroupItem *>(content)->processPaintItems(
                list);
            break;
        }
        default:
            break;
        }
    }
}

void LOTContentGroupItem::processTrimItems(std::vector<LOTPathDataItem *> &list)
{
    int curOpCount = list.size();
    for (auto i = mContents.rbegin(); i != mContents.rend(); ++i) {
        auto content = (*i).get();

        switch (content->type()) {
        case ContentType::Path: {
            list.push_back(static_cast<LOTPathDataItem *>(content));
            break;
        }
        case ContentType::Trim: {
            static_cast<LOTTrimItem *>(content)->addPathItems(list, curOpCount);
            break;
        }
        case ContentType::Group: {
            static_cast<LOTContentGroupItem *>(content)->processTrimItems(list);
            break;
        }
        default:
            break;
        }
    }
}

/*
 * LOTPathDataItem uses 3 path objects for path object reuse.
 * mLocalPath -  keeps track of the local path of the item before
 * applying path operation and transformation.
 * mTemp - keeps a referece to the mLocalPath and can be updated by the
 *          path operation objects(trim, merge path),
 *  mFinalPath - it takes a deep copy of the intermediate path(mTemp) each time
 *  when the path is dirty(so if path changes every frame we don't realloc just
 * copy to the final path). NOTE: As path objects are COW objects we have to be
 * carefull about the refcount so that we don't generate deep copy while
 * modifying the path objects.
 */
void LOTPathDataItem::update(int              frameNo, const VMatrix &, float,
                             const DirtyFlag &flag)
{
    mPathChanged = false;

    // 1. update the local path if needed
    if (hasChanged(frameNo)) {
        // loose the reference to mLocalPath if any
        // from the last frame update.
        mTemp = VPath();

        updatePath(mLocalPath, frameNo);
        mPathChanged = true;
        mNeedUpdate = true;
    }
    // 2. keep a reference path in temp in case there is some
    // path operation like trim which will update the path.
    // we don't want to update the local path.
    mTemp = mLocalPath;

    // 3. compute the final path with parentMatrix
    if ((flag & DirtyFlagBit::Matrix) || mPathChanged) {
        mPathChanged = true;
    }
}

const VPath &LOTPathDataItem::finalPath()
{
    if (mPathChanged || mNeedUpdate) {
        mFinalPath.clone(mTemp);
        mFinalPath.transform(
            static_cast<LOTContentGroupItem *>(parent())->matrix());
        mNeedUpdate = false;
    }
    return mFinalPath;
}
LOTRectItem::LOTRectItem(LOTRectData *data)
    : LOTPathDataItem(data->isStatic()), mData(data)
{
}

void LOTRectItem::updatePath(VPath &path, int frameNo)
{
    VPointF pos = mData->mPos.value(frameNo);
    VPointF size = mData->mSize.value(frameNo);
    float   roundness = mData->mRound.value(frameNo);
    VRectF  r(pos.x() - size.x() / 2, pos.y() - size.y() / 2, size.x(),
             size.y());

    path.reset();
    path.addRoundRect(r, roundness, mData->direction());
}

LOTEllipseItem::LOTEllipseItem(LOTEllipseData *data)
    : LOTPathDataItem(data->isStatic()), mData(data)
{
}

void LOTEllipseItem::updatePath(VPath &path, int frameNo)
{
    VPointF pos = mData->mPos.value(frameNo);
    VPointF size = mData->mSize.value(frameNo);
    VRectF  r(pos.x() - size.x() / 2, pos.y() - size.y() / 2, size.x(),
             size.y());

    path.reset();
    path.addOval(r, mData->direction());
}

LOTShapeItem::LOTShapeItem(LOTShapeData *data)
    : LOTPathDataItem(data->isStatic()), mData(data)
{
}

void LOTShapeItem::updatePath(VPath &path, int frameNo)
{
    mData->mShape.value(frameNo).toPath(path);
}

LOTPolystarItem::LOTPolystarItem(LOTPolystarData *data)
    : LOTPathDataItem(data->isStatic()), mData(data)
{
}

void LOTPolystarItem::updatePath(VPath &path, int frameNo)
{
    VPointF pos = mData->mPos.value(frameNo);
    float   points = mData->mPointCount.value(frameNo);
    float   innerRadius = mData->mInnerRadius.value(frameNo);
    float   outerRadius = mData->mOuterRadius.value(frameNo);
    float   innerRoundness = mData->mInnerRoundness.value(frameNo);
    float   outerRoundness = mData->mOuterRoundness.value(frameNo);
    float   rotation = mData->mRotation.value(frameNo);

    path.reset();
    VMatrix m;

    if (mData->mType == LOTPolystarData::PolyType::Star) {
        path.addPolystar(points, innerRadius, outerRadius, innerRoundness,
                         outerRoundness, 0.0, 0.0, 0.0, mData->direction());
    } else {
        path.addPolygon(points, outerRadius, outerRoundness, 0.0, 0.0, 0.0,
                        mData->direction());
    }

    m.translate(pos.x(), pos.y()).rotate(rotation);
    m.rotate(rotation);
    path.transform(m);
}

/*
 * PaintData Node handling
 *
 */
LOTPaintDataItem::LOTPaintDataItem(bool staticContent)
    : LOTContentItem(ContentType::Paint), mStaticContent(staticContent)
{
}

void LOTPaintDataItem::update(int   frameNo, const VMatrix & /*parentMatrix*/,
                              float parentAlpha, const DirtyFlag &flag)
{
    mRenderNodeUpdate = true;
    mParentAlpha = parentAlpha;
    mFlag = flag;
    mFrameNo = frameNo;

    updateContent(frameNo);
}

void LOTPaintDataItem::updateRenderNode()
{
    bool dirty = false;
    for (auto &i : mPathItems) {
        if (i->dirty()) {
            dirty = true;
            break;
        }
    }

    if (dirty) {
        mPath.reset();

        for (auto &i : mPathItems) {
            mPath.addPath(i->finalPath());
        }
        mDrawable.setPath(mPath);
    } else {
        if (mDrawable.mFlag & VDrawable::DirtyState::Path)
            mDrawable.mPath = mPath;
    }
}

void LOTPaintDataItem::renderList(std::vector<VDrawable *> &list)
{
    if (mRenderNodeUpdate) {
        updateRenderNode();
        LOTPaintDataItem::updateRenderNode();
        mRenderNodeUpdate = false;
    }
    list.push_back(&mDrawable);
}

void LOTPaintDataItem::addPathItems(std::vector<LOTPathDataItem *> &list,
                                    int                             startOffset)
{
    std::copy(list.begin() + startOffset, list.end(),
              back_inserter(mPathItems));
}

LOTFillItem::LOTFillItem(LOTFillData *data)
    : LOTPaintDataItem(data->isStatic()), mModel(data)
{
}

void LOTFillItem::updateContent(int frameNo)
{
    mColor = mModel.color(frameNo).toColor(mModel.opacity(frameNo));
}

void LOTFillItem::updateRenderNode()
{
    VColor color = mColor;

    color.setAlpha(color.a * parentAlpha());
    VBrush brush(color);
    mDrawable.setBrush(brush);
    mDrawable.setFillRule(mModel.fillRule());
}

LOTGFillItem::LOTGFillItem(LOTGFillData *data)
    : LOTPaintDataItem(data->isStatic()), mData(data)
{
}

void LOTGFillItem::updateContent(int frameNo)
{
    mAlpha = mData->opacity(frameNo);
    mData->update(mGradient, frameNo);
    mGradient->mMatrix = static_cast<LOTContentGroupItem *>(parent())->matrix();
    mFillRule = mData->fillRule();
}

void LOTGFillItem::updateRenderNode()
{
    mGradient->setAlpha(mAlpha * parentAlpha());
    mDrawable.setBrush(VBrush(mGradient.get()));
    mDrawable.setFillRule(mFillRule);
}

LOTStrokeItem::LOTStrokeItem(LOTStrokeData *data)
    : LOTPaintDataItem(data->isStatic()), mModel(data)
{
    mDashArraySize = 0;
}

void LOTStrokeItem::updateContent(int frameNo)
{
    mColor = mModel.color(frameNo).toColor(mModel.opacity(frameNo));
    mWidth = mModel.strokeWidth(frameNo);
    if (mModel.hasDashInfo()) {
        mDashArraySize = mModel.getDashInfo(frameNo, mDashArray);
    }
}

static float getScale(const VMatrix &matrix)
{
    constexpr float SQRT_2 = 1.41421;
    VPointF         p1(0, 0);
    VPointF         p2(SQRT_2, SQRT_2);
    p1 = matrix.map(p1);
    p2 = matrix.map(p2);
    VPointF final = p2 - p1;

    return std::sqrt(final.x() * final.x() + final.y() * final.y()) / 2.0;
}

void LOTStrokeItem::updateRenderNode()
{
    VColor color = mColor;

    color.setAlpha(color.a * parentAlpha());
    VBrush brush(color);
    mDrawable.setBrush(brush);
    float scale =
        getScale(static_cast<LOTContentGroupItem *>(parent())->matrix());
    mDrawable.setStrokeInfo(mModel.capStyle(), mModel.joinStyle(),
                            mModel.meterLimit(), mWidth * scale);
    if (mDashArraySize) {
        for (int i = 0; i < mDashArraySize; i++) mDashArray[i] *= scale;

        /* AE draw the dash even if dash value is 0 */
        if (vCompare(mDashArray[0], 0.0f)) mDashArray[0] = 0.1;

        mDrawable.setDashInfo(mDashArray, mDashArraySize);
    }
}

LOTGStrokeItem::LOTGStrokeItem(LOTGStrokeData *data)
    : LOTPaintDataItem(data->isStatic()), mData(data)
{
    mDashArraySize = 0;
}

void LOTGStrokeItem::updateContent(int frameNo)
{
    mAlpha = mData->opacity(frameNo);
    mData->update(mGradient, frameNo);
    mGradient->mMatrix = static_cast<LOTContentGroupItem *>(parent())->matrix();
    mCap = mData->capStyle();
    mJoin = mData->joinStyle();
    mMiterLimit = mData->meterLimit();
    mWidth = mData->width(frameNo);
    if (mData->hasDashInfo()) {
        mDashArraySize = mData->getDashInfo(frameNo, mDashArray);
    }
}

void LOTGStrokeItem::updateRenderNode()
{
    float scale = getScale(mGradient->mMatrix);
    mGradient->setAlpha(mAlpha * parentAlpha());
    mDrawable.setBrush(VBrush(mGradient.get()));
    mDrawable.setStrokeInfo(mCap, mJoin, mMiterLimit, mWidth * scale);
    if (mDashArraySize) {
        for (int i = 0; i < mDashArraySize; i++) mDashArray[i] *= scale;
        mDrawable.setDashInfo(mDashArray, mDashArraySize);
    }
}

LOTTrimItem::LOTTrimItem(LOTTrimData *data)
    : LOTContentItem(ContentType::Trim), mData(data)
{
}

void LOTTrimItem::update(int frameNo, const VMatrix & /*parentMatrix*/,
                         float /*parentAlpha*/, const DirtyFlag & /*flag*/)
{
    mDirty = false;

    if (mCache.mFrameNo == frameNo) return;

    LOTTrimData::Segment segment = mData->segment(frameNo);

    if (!(vCompare(mCache.mSegment.start, segment.start) &&
          vCompare(mCache.mSegment.end, segment.end))) {
        mDirty = true;
        mCache.mSegment = segment;
    }
    mCache.mFrameNo = frameNo;
}

void LOTTrimItem::update()
{
    // when both path and trim are not dirty
    if (!(mDirty || pathDirty())) return;

    if (vCompare(mCache.mSegment.start, mCache.mSegment.end)) {
        for (auto &i : mPathItems) {
            i->updatePath(VPath());
        }
        return;
    }

    if (vCompare(std::fabs(mCache.mSegment.start - mCache.mSegment.end), 1)) {
        for (auto &i : mPathItems) {
            i->updatePath(i->localPath());
        }
        return;
    }

    if (mData->type() == LOTTrimData::TrimType::Simultaneously) {
        for (auto &i : mPathItems) {
            VPathMesure pm;
            pm.setStart(mCache.mSegment.start);
            pm.setEnd(mCache.mSegment.end);
            i->updatePath(pm.trim(i->localPath()));
        }
    } else {  // LOTTrimData::TrimType::Individually
        float totalLength = 0.0;
        for (auto &i : mPathItems) {
            totalLength += i->localPath().length();
        }
        float start = totalLength * mCache.mSegment.start;
        float end = totalLength * mCache.mSegment.end;

        if (start < end) {
            float curLen = 0.0;
            for (auto &i : mPathItems) {
                if (curLen > end) {
                    // update with empty path.
                    i->updatePath(VPath());
                    continue;
                }
                float len = i->localPath().length();

                if (curLen < start && curLen + len < start) {
                    curLen += len;
                    // update with empty path.
                    i->updatePath(VPath());
                    continue;
                } else if (start <= curLen && end >= curLen + len) {
                    // inside segment
                    curLen += len;
                    continue;
                } else {
                    float local_start = start > curLen ? start - curLen : 0;
                    local_start /= len;
                    float local_end = curLen + len < end ? len : end - curLen;
                    local_end /= len;
                    VPathMesure pm;
                    pm.setStart(local_start);
                    pm.setEnd(local_end);
                    VPath p = pm.trim(i->localPath());
                    i->updatePath(p);
                    curLen += len;
                }
            }
        }
    }
}

void LOTTrimItem::addPathItems(std::vector<LOTPathDataItem *> &list,
                               int                             startOffset)
{
    std::copy(list.begin() + startOffset, list.end(),
              back_inserter(mPathItems));
}

LOTRepeaterItem::LOTRepeaterItem(LOTRepeaterData *data) : mRepeaterData(data)
{
    assert(mRepeaterData->content());

    mCopies = mRepeaterData->maxCopies();

    for (int i = 0; i < mCopies; i++) {
        auto content =
            std::make_unique<LOTContentGroupItem>(mRepeaterData->content());
        content->setParent(this);
        mContents.push_back(std::move(content));
    }
}

void LOTRepeaterItem::update(int frameNo, const VMatrix &parentMatrix,
                             float parentAlpha, const DirtyFlag &flag)
{
    DirtyFlag newFlag = flag;

    float copies = mRepeaterData->copies(frameNo);
    int   visibleCopies = int(copies);

    if (visibleCopies == 0) {
        mHidden = true;
        return;
    } else {
        mHidden = false;
    }

    if (!mRepeaterData->isStatic()) newFlag |= DirtyFlagBit::Matrix;

    float offset = mRepeaterData->offset(frameNo);
    float startOpacity = mRepeaterData->mTransform.startOpacity(frameNo);
    float endOpacity = mRepeaterData->mTransform.endOpacity(frameNo);

    newFlag |= DirtyFlagBit::Alpha;

    for (int i = 0; i < mCopies; ++i) {
        float newAlpha =
            parentAlpha * lerp(startOpacity, endOpacity, i / copies);

        // hide rest of the copies , @TODO find a better solution.
        if (i >= visibleCopies) newAlpha = 0;

        VMatrix result = mRepeaterData->mTransform.matrix(frameNo, i + offset) *
                         parentMatrix;
        mContents[i]->update(frameNo, result, newAlpha, newFlag);
    }
}

void LOTRepeaterItem::renderList(std::vector<VDrawable *> &list)
{
    if (mHidden) return;
    return LOTContentGroupItem::renderList(list);
}

static void updateGStops(LOTNode *n, const VGradient *grad)
{
    if (grad->mStops.size() != n->mGradient.stopCount) {
        if (n->mGradient.stopCount) free(n->mGradient.stopPtr);
        n->mGradient.stopCount = grad->mStops.size();
        n->mGradient.stopPtr = (LOTGradientStop *)malloc(
            n->mGradient.stopCount * sizeof(LOTGradientStop));
    }

    LOTGradientStop *ptr = n->mGradient.stopPtr;
    for (const auto &i : grad->mStops) {
        ptr->pos = i.first;
        ptr->a = i.second.alpha() * grad->alpha();
        ptr->r = i.second.red();
        ptr->g = i.second.green();
        ptr->b = i.second.blue();
        ptr++;
    }
}

void LOTDrawable::sync()
{
    if (!mCNode) {
        mCNode = std::make_unique<LOTNode>();
        mCNode->mGradient.stopPtr = nullptr;
        mCNode->mGradient.stopCount = 0;
    }

    mCNode->mFlag = ChangeFlagNone;
    if (mFlag & DirtyState::None) return;

    if (mFlag & DirtyState::Path) {
        if (mStroke.mDash.size()) {
            VDasher dasher(mStroke.mDash.data(), mStroke.mDash.size());
            mPath = dasher.dashed(mPath);
        }
        const std::vector<VPath::Element> &elm = mPath.elements();
        const std::vector<VPointF> &       pts = mPath.points();
        const float *ptPtr = reinterpret_cast<const float *>(pts.data());
        const char * elmPtr = reinterpret_cast<const char *>(elm.data());
        mCNode->mPath.elmPtr = elmPtr;
        mCNode->mPath.elmCount = elm.size();
        mCNode->mPath.ptPtr = ptPtr;
        mCNode->mPath.ptCount = 2 * pts.size();
        mCNode->mFlag |= ChangeFlagPath;
    }

    if (mStroke.enable) {
        mCNode->mStroke.width = mStroke.width;
        mCNode->mStroke.meterLimit = mStroke.meterLimit;
        mCNode->mStroke.enable = 1;

        switch (mStroke.cap) {
        case CapStyle::Flat:
            mCNode->mStroke.cap = LOTCapStyle::CapFlat;
            break;
        case CapStyle::Square:
            mCNode->mStroke.cap = LOTCapStyle::CapSquare;
            break;
        case CapStyle::Round:
            mCNode->mStroke.cap = LOTCapStyle::CapRound;
            break;
        }

        switch (mStroke.join) {
        case JoinStyle::Miter:
            mCNode->mStroke.join = LOTJoinStyle::JoinMiter;
            break;
        case JoinStyle::Bevel:
            mCNode->mStroke.join = LOTJoinStyle::JoinBevel;
            break;
        case JoinStyle::Round:
            mCNode->mStroke.join = LOTJoinStyle::JoinRound;
            break;
        default:
            mCNode->mStroke.join = LOTJoinStyle::JoinMiter;
            break;
        }
    } else {
        mCNode->mStroke.enable = 0;
    }

    switch (mFillRule) {
    case FillRule::EvenOdd:
        mCNode->mFillRule = LOTFillRule::FillEvenOdd;
        break;
    default:
        mCNode->mFillRule = LOTFillRule::FillWinding;
        break;
    }

    switch (mBrush.type()) {
    case VBrush::Type::Solid:
        mCNode->mBrushType = LOTBrushType::BrushSolid;
        mCNode->mColor.r = mBrush.mColor.r;
        mCNode->mColor.g = mBrush.mColor.g;
        mCNode->mColor.b = mBrush.mColor.b;
        mCNode->mColor.a = mBrush.mColor.a;
        break;
    case VBrush::Type::LinearGradient: {
        mCNode->mBrushType = LOTBrushType::BrushGradient;
        mCNode->mGradient.type = LOTGradientType::GradientLinear;
        VPointF s = mBrush.mGradient->mMatrix.map(
            {mBrush.mGradient->linear.x1, mBrush.mGradient->linear.y1});
        VPointF e = mBrush.mGradient->mMatrix.map(
            {mBrush.mGradient->linear.x2, mBrush.mGradient->linear.y2});
        mCNode->mGradient.start.x = s.x();
        mCNode->mGradient.start.y = s.y();
        mCNode->mGradient.end.x = e.x();
        mCNode->mGradient.end.y = e.y();
        updateGStops(mCNode.get(), mBrush.mGradient);
        break;
    }
    case VBrush::Type::RadialGradient: {
        mCNode->mBrushType = LOTBrushType::BrushGradient;
        mCNode->mGradient.type = LOTGradientType::GradientRadial;
        VPointF c = mBrush.mGradient->mMatrix.map(
            {mBrush.mGradient->radial.cx, mBrush.mGradient->radial.cy});
        VPointF f = mBrush.mGradient->mMatrix.map(
            {mBrush.mGradient->radial.fx, mBrush.mGradient->radial.fy});
        mCNode->mGradient.center.x = c.x();
        mCNode->mGradient.center.y = c.y();
        mCNode->mGradient.focal.x = f.x();
        mCNode->mGradient.focal.y = f.y();

        float scale = getScale(mBrush.mGradient->mMatrix);
        mCNode->mGradient.cradius = mBrush.mGradient->radial.cradius * scale;
        mCNode->mGradient.fradius = mBrush.mGradient->radial.fradius * scale;
        updateGStops(mCNode.get(), mBrush.mGradient);
        break;
    }
    default:
        break;
    }
}
