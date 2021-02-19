/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "lottieitem.h"
#include <algorithm>
#include <cmath>
#include <iterator>
#include "lottiekeypath.h"
#include "vbitmap.h"
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
    case rlottie::Property::FillColor:
    case rlottie::Property::FillOpacity:
        return true;
    default:
        return false;
    }
}

static bool strokeProp(rlottie::Property prop)
{
    switch (prop) {
    case rlottie::Property::StrokeColor:
    case rlottie::Property::StrokeOpacity:
    case rlottie::Property::StrokeWidth:
        return true;
    default:
        return false;
    }
}

static renderer::Layer *createLayerItem(model::Layer *layerData,
                                        VArenaAlloc * allocator)
{
    switch (layerData->mLayerType) {
    case model::Layer::Type::Precomp: {
        return allocator->make<renderer::CompLayer>(layerData, allocator);
    }
    case model::Layer::Type::Solid: {
        return allocator->make<renderer::SolidLayer>(layerData);
    }
    case model::Layer::Type::Shape: {
        return allocator->make<renderer::ShapeLayer>(layerData, allocator);
    }
    case model::Layer::Type::Null: {
        return allocator->make<renderer::NullLayer>(layerData);
    }
    case model::Layer::Type::Image: {
        return allocator->make<renderer::ImageLayer>(layerData);
    }
    default:
        return nullptr;
        break;
    }
}

void renderer::Composition::resetCurrentFrame()
{
    mCurFrameNo = -1;
}

renderer::Composition::Composition(std::shared_ptr<model::Composition> model)
    : mCurFrameNo(-1)
{
    mModel = std::move(model);
    mRootLayer = createLayerItem(mModel->mRootLayer, &mAllocator);
    mRootLayer->setComplexContent(false);
    mViewSize = mModel->size();
}

void renderer::Composition::setValue(const std::string &keypath,
                                     LOTVariant &       value)
{
    LOTKeyPath key(keypath);
    mRootLayer->resolveKeyPath(key, 0, value);
}

bool renderer::Composition::update(int frameNo, const VSize &size)
{
    // check if cached frame is same as requested frame.
    if ((mViewSize == size) && (mCurFrameNo == frameNo))
        return false;

    mViewSize = size;
    mCurFrameNo = frameNo;

    /*
     * if viewbox dosen't scale exactly to the viewport
     * we scale the viewbox keeping AspectRatioPreserved and then align the
     * viewbox to the viewport using AlignCenter rule.
     */
    VMatrix m;
    VSize   viewPort = mViewSize;
    VSize   viewBox = mModel->size();
    float   sx = float(viewPort.width()) / viewBox.width();
    float   sy = float(viewPort.height()) / viewBox.height();
    if (mKeepAspectRatio) {
        float scale = std::min(sx, sy);
        float tx = (viewPort.width() - viewBox.width() * scale) * 0.5f;
        float ty = (viewPort.height() - viewBox.height() * scale) * 0.5f;
        m.translate(tx, ty).scale(scale, scale);
    } else {
        m.scale(sx, sy);
    }
    mRootLayer->update(frameNo, m, 1.0);
    return true;
}

bool renderer::Composition::render(const rlottie::Surface &surface, bool clear)
{
    mSurface.reset(reinterpret_cast<uchar *>(surface.buffer()),
                   uint(surface.width()), uint(surface.height()),
                   uint(surface.bytesPerLine()),
                   VBitmap::Format::ARGB32_Premultiplied);

    /* schedule all preprocess task for this frame at once.
     */
    VRect clip(0, 0, int(surface.drawRegionWidth()),
               int(surface.drawRegionHeight()));
    mRootLayer->preprocess(clip);

    VPainter painter(&mSurface, clear);
    // set sub surface area for drawing.
    painter.setDrawRegion(
        VRect(int(surface.drawRegionPosX()), int(surface.drawRegionPosY()),
              int(surface.drawRegionWidth()), int(surface.drawRegionHeight())));
    mRootLayer->render(&painter, {}, {}, mSurfaceCache);
    painter.end();
    return true;
}

void renderer::Mask::update(int frameNo, const VMatrix &parentMatrix,
                            float /*parentAlpha*/, const DirtyFlag &flag)
{
    bool dirtyPath = false;

    if (flag.testFlag(DirtyFlagBit::None) && mData->isStatic()) return;

    if (mData->mShape.isStatic()) {
        if (mLocalPath.empty()) {
            dirtyPath = true;
            mData->mShape.value(frameNo, mLocalPath);
        }
    } else {
        dirtyPath = true;
        mData->mShape.value(frameNo, mLocalPath);
    }
    /* mask item dosen't inherit opacity */
    mCombinedAlpha = mData->opacity(frameNo);

    if ( flag.testFlag(DirtyFlagBit::Matrix) || dirtyPath ) {
        mFinalPath.clone(mLocalPath);
        mFinalPath.transform(parentMatrix);
        mRasterRequest = true;
    }
}

VRle renderer::Mask::rle()
{
    if (!vCompare(mCombinedAlpha, 1.0f)) {
        VRle obj = mRasterizer.rle();
        obj *= uchar(mCombinedAlpha * 255);
        return obj;
    } else {
        return mRasterizer.rle();
    }
}

void renderer::Mask::preprocess(const VRect &clip)
{
    if (mRasterRequest)
        mRasterizer.rasterize(mFinalPath, FillRule::Winding, clip);
}

void renderer::Layer::render(VPainter *painter, const VRle &inheritMask,
                             const VRle &matteRle, SurfaceCache &)
{
    auto renderlist = renderList();

    if (renderlist.empty()) return;

    VRle mask;
    if (mLayerMask) {
        mask = mLayerMask->maskRle(painter->clipBoundingRect());
        if (!inheritMask.empty()) mask = mask & inheritMask;
        // if resulting mask is empty then return.
        if (mask.empty()) return;
    } else {
        mask = inheritMask;
    }

    for (auto &i : renderlist) {
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
            if (matteType() == model::MatteType::AlphaInv) {
                rle = rle - matteRle;
                painter->drawRle(VPoint(), rle);
            } else {
                // render with matteRle as clip.
                painter->drawRle(rle, matteRle);
            }
        }
    }
}

void renderer::LayerMask::preprocess(const VRect &clip)
{
    for (auto &i : mMasks) {
        i.preprocess(clip);
    }
}

renderer::LayerMask::LayerMask(model::Layer *layerData)
{
    if (!layerData->mExtra) return;

    mMasks.reserve(layerData->mExtra->mMasks.size());

    for (auto &i : layerData->mExtra->mMasks) {
        mMasks.emplace_back(i);
        mStatic &= i->isStatic();
    }
}

void renderer::LayerMask::update(int frameNo, const VMatrix &parentMatrix,
                                 float parentAlpha, const DirtyFlag &flag)
{
    if (flag.testFlag(DirtyFlagBit::None) && isStatic()) return;

    for (auto &i : mMasks) {
        i.update(frameNo, parentMatrix, parentAlpha, flag);
    }
    mDirty = true;
}

VRle renderer::LayerMask::maskRle(const VRect &clipRect)
{
    if (!mDirty) return mRle;

    VRle rle;
    for (auto &e : mMasks) {
        const auto cur = [&]() {
            if (e.inverted())
                return clipRect - e.rle();
            else
                return e.rle();
        }();

        switch (e.maskMode()) {
        case model::Mask::Mode::Add: {
            rle = rle + cur;
            break;
        }
        case model::Mask::Mode::Substarct: {
            if (rle.empty() && !clipRect.empty())
                rle = clipRect - cur;
            else
                rle = rle - cur;
            break;
        }
        case model::Mask::Mode::Intersect: {
            if (rle.empty() && !clipRect.empty())
                rle = clipRect & cur;
            else
                rle = rle & cur;
            break;
        }
        case model::Mask::Mode::Difference: {
            rle = rle ^ cur;
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

renderer::Layer::Layer(model::Layer *layerData) : mLayerData(layerData)
{
    if (mLayerData->mHasMask)
        mLayerMask = std::make_unique<renderer::LayerMask>(mLayerData);
}

bool renderer::Layer::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                     LOTVariant &value)
{
    if (!keyPath.matches(name(), depth)) {
        return false;
    }

    if (!keyPath.skip(name())) {
        if (keyPath.fullyResolvesTo(name(), depth) &&
            transformProp(value.property())) {
            //@TODO handle propery update.
        }
    }
    return true;
}

bool renderer::ShapeLayer::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                          LOTVariant &value)
{
    if (renderer::Layer::resolveKeyPath(keyPath, depth, value)) {
        if (keyPath.propagate(name(), depth)) {
            uint newDepth = keyPath.nextDepth(name(), depth);
            mRoot->resolveKeyPath(keyPath, newDepth, value);
        }
        return true;
    }
    return false;
}

bool renderer::CompLayer::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                         LOTVariant &value)
{
    if (renderer::Layer::resolveKeyPath(keyPath, depth, value)) {
        if (keyPath.propagate(name(), depth)) {
            uint newDepth = keyPath.nextDepth(name(), depth);
            for (const auto &layer : mLayers) {
                layer->resolveKeyPath(keyPath, newDepth, value);
            }
        }
        return true;
    }
    return false;
}

void renderer::Layer::update(int frameNumber, const VMatrix &parentMatrix,
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
    if (mCombinedMatrix != m) {
        mDirtyFlag |= DirtyFlagBit::Matrix;
        mCombinedMatrix = m;
    }

    if (!vCompare(mCombinedAlpha, alpha)) {
        mDirtyFlag |= DirtyFlagBit::Alpha;
        mCombinedAlpha = alpha;
    }

    // 4. update the mask
    if (mLayerMask) {
        mLayerMask->update(frameNo(), mCombinedMatrix, mCombinedAlpha,
                           mDirtyFlag);
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

VMatrix renderer::Layer::matrix(int frameNo) const
{
    return mParentLayer
               ? (mLayerData->matrix(frameNo) * mParentLayer->matrix(frameNo))
               : mLayerData->matrix(frameNo);
}

bool renderer::Layer::visible() const
{
    return (frameNo() >= mLayerData->inFrame() &&
            frameNo() < mLayerData->outFrame());
}

void renderer::Layer::preprocess(const VRect &clip)
{
    // layer dosen't contribute to the frame
    if (skipRendering()) return;

    // preprocess layer masks
    if (mLayerMask) mLayerMask->preprocess(clip);

    preprocessStage(clip);
}

renderer::CompLayer::CompLayer(model::Layer *layerModel, VArenaAlloc *allocator)
    : renderer::Layer(layerModel)
{
    if (!mLayerData->mChildren.empty())
        mLayers.reserve(mLayerData->mChildren.size());

    // 1. keep the layer in back-to-front order.
    // as lottie model keeps the data in front-toback-order.
    for (auto it = mLayerData->mChildren.crbegin();
         it != mLayerData->mChildren.rend(); ++it) {
        auto model = static_cast<model::Layer *>(*it);
        auto item = createLayerItem(model, allocator);
        if (item) mLayers.push_back(item);
    }

    // 2. update parent layer
    for (const auto &layer : mLayers) {
        int id = layer->parentId();
        if (id >= 0) {
            auto search =
                std::find_if(mLayers.begin(), mLayers.end(),
                             [id](const auto &val) { return val->id() == id; });
            if (search != mLayers.end()) layer->setParentLayer(*search);
        }
    }

    // 4. check if its a nested composition
    if (!layerModel->layerSize().empty()) {
        mClipper = std::make_unique<renderer::Clipper>(layerModel->layerSize());
    }

    if (mLayers.size() > 1) setComplexContent(true);
}

void renderer::CompLayer::render(VPainter *painter, const VRle &inheritMask,
                                 const VRle &matteRle, SurfaceCache &cache)
{
    if (vIsZero(combinedAlpha())) return;

    if (vCompare(combinedAlpha(), 1.0)) {
        renderHelper(painter, inheritMask, matteRle, cache);
    } else {
        if (complexContent()) {
            VSize    size = painter->clipBoundingRect().size();
            VPainter srcPainter;
            VBitmap srcBitmap = cache.make_surface(size.width(), size.height());
            srcPainter.begin(&srcBitmap, true);
            renderHelper(&srcPainter, inheritMask, matteRle, cache);
            srcPainter.end();
            painter->drawBitmap(VPoint(), srcBitmap,
                                uchar(combinedAlpha() * 255.0f));
            cache.release_surface(srcBitmap);
        } else {
            renderHelper(painter, inheritMask, matteRle, cache);
        }
    }
}

void renderer::CompLayer::renderHelper(VPainter *    painter,
                                       const VRle &  inheritMask,
                                       const VRle &  matteRle,
                                       SurfaceCache &cache)
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
        mask = mClipper->rle(mask);
        if (mask.empty()) return;
    }

    renderer::Layer *matte = nullptr;
    for (const auto &layer : mLayers) {
        if (layer->hasMatte()) {
            matte = layer;
        } else {
            if (layer->visible()) {
                if (matte) {
                    if (matte->visible())
                        renderMatteLayer(painter, mask, matteRle, matte, layer,
                                         cache);
                } else {
                    layer->render(painter, mask, matteRle, cache);
                }
            }
            matte = nullptr;
        }
    }
}

void renderer::CompLayer::renderMatteLayer(VPainter *painter, const VRle &mask,
                                           const VRle &     matteRle,
                                           renderer::Layer *layer,
                                           renderer::Layer *src,
                                           SurfaceCache &   cache)
{
    VSize size = painter->clipBoundingRect().size();
    // Decide if we can use fast matte.
    // 1. draw src layer to matte buffer
    VPainter srcPainter;
    VBitmap  srcBitmap = cache.make_surface(size.width(), size.height());
    srcPainter.begin(&srcBitmap, true);
    src->render(&srcPainter, mask, matteRle, cache);
    srcPainter.end();

    // 2. draw layer to layer buffer
    VPainter layerPainter;
    VBitmap  layerBitmap = cache.make_surface(size.width(), size.height());
    layerPainter.begin(&layerBitmap, true);
    layer->render(&layerPainter, mask, matteRle, cache);

    // 2.1update composition mode
    switch (layer->matteType()) {
    case model::MatteType::Alpha:
    case model::MatteType::Luma: {
        layerPainter.setBlendMode(BlendMode::DestIn);
        break;
    }
    case model::MatteType::AlphaInv:
    case model::MatteType::LumaInv: {
        layerPainter.setBlendMode(BlendMode::DestOut);
        break;
    }
    default:
        break;
    }

    // 2.2 update srcBuffer if the matte is luma type
    if (layer->matteType() == model::MatteType::Luma ||
        layer->matteType() == model::MatteType::LumaInv) {
        srcBitmap.updateLuma();
    }

    // 2.3 draw src buffer as mask
    layerPainter.drawBitmap(VPoint(), srcBitmap);
    layerPainter.end();
    // 3. draw the result buffer into painter
    painter->drawBitmap(VPoint(), layerBitmap);

    cache.release_surface(srcBitmap);
    cache.release_surface(layerBitmap);
}

void renderer::Clipper::update(const VMatrix &matrix)
{
    mPath.reset();
    mPath.addRect(VRectF(0, 0, mSize.width(), mSize.height()));
    mPath.transform(matrix);
    mRasterRequest = true;
}

void renderer::Clipper::preprocess(const VRect &clip)
{
    if (mRasterRequest) mRasterizer.rasterize(mPath, FillRule::Winding, clip);

    mRasterRequest = false;
}

VRle renderer::Clipper::rle(const VRle &mask)
{
    if (mask.empty()) return mRasterizer.rle();

    mMaskedRle.clone(mask);
    mMaskedRle &= mRasterizer.rle();
    return mMaskedRle;
}

void renderer::CompLayer::updateContent()
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

void renderer::CompLayer::preprocessStage(const VRect &clip)
{
    // if layer has clipper
    if (mClipper) mClipper->preprocess(clip);

    renderer::Layer *matte = nullptr;
    for (const auto &layer : mLayers) {
        if (layer->hasMatte()) {
            matte = layer;
        } else {
            if (layer->visible()) {
                if (matte) {
                    if (matte->visible()) {
                        layer->preprocess(clip);
                        matte->preprocess(clip);
                    }
                } else {
                    layer->preprocess(clip);
                }
            }
            matte = nullptr;
        }
    }
}

renderer::SolidLayer::SolidLayer(model::Layer *layerData)
    : renderer::Layer(layerData)
{
    mDrawableList = &mRenderNode;
}

void renderer::SolidLayer::updateContent()
{
    if (flag() & DirtyFlagBit::Matrix) {
        mPath.reset();
        mPath.addRect(VRectF(0, 0, mLayerData->layerSize().width(),
                            mLayerData->layerSize().height()));
        mPath.transform(combinedMatrix());
        mRenderNode.mFlag |= VDrawable::DirtyState::Path;
        mRenderNode.mPath = mPath;
    }
    if (flag() & DirtyFlagBit::Alpha) {
        model::Color color = mLayerData->solidColor();
        VBrush       brush(color.toColor(combinedAlpha()));
        mRenderNode.setBrush(brush);
        mRenderNode.mFlag |= VDrawable::DirtyState::Brush;
    }
}

void renderer::SolidLayer::preprocessStage(const VRect &clip)
{
    mRenderNode.preprocess(clip);
}

renderer::DrawableList renderer::SolidLayer::renderList()
{
    if (skipRendering()) return {};

    return {&mDrawableList, 1};
}

renderer::ImageLayer::ImageLayer(model::Layer *layerData)
    : renderer::Layer(layerData)
{
    mDrawableList = &mRenderNode;

    if (!mLayerData->asset()) return;

    mTexture.mBitmap = mLayerData->asset()->bitmap();
    VBrush brush(&mTexture);
    mRenderNode.setBrush(brush);
}

void renderer::ImageLayer::updateContent()
{
    if (!mLayerData->asset()) return;

    if (flag() & DirtyFlagBit::Matrix) {
        mPath.reset();
        mPath.addRect(VRectF(0, 0, mLayerData->asset()->mWidth,
                            mLayerData->asset()->mHeight));
        mPath.transform(combinedMatrix());
        mRenderNode.mFlag |= VDrawable::DirtyState::Path;
        mRenderNode.mPath = mPath;
        mTexture.mMatrix = combinedMatrix();
    }

    if (flag() & DirtyFlagBit::Alpha) {
        mTexture.mAlpha = int(combinedAlpha() * 255);
    }
}

void renderer::ImageLayer::preprocessStage(const VRect &clip)
{
    mRenderNode.preprocess(clip);
}

renderer::DrawableList renderer::ImageLayer::renderList()
{
    if (skipRendering()) return {};

    return {&mDrawableList, 1};
}

renderer::NullLayer::NullLayer(model::Layer *layerData)
    : renderer::Layer(layerData)
{
}
void renderer::NullLayer::updateContent() {}

static renderer::Object *createContentItem(model::Object *contentData,
                                           VArenaAlloc *  allocator)
{
    switch (contentData->type()) {
    case model::Object::Type::Group: {
        return allocator->make<renderer::Group>(
            static_cast<model::Group *>(contentData), allocator);
    }
    case model::Object::Type::Rect: {
        return allocator->make<renderer::Rect>(
            static_cast<model::Rect *>(contentData));
    }
    case model::Object::Type::Ellipse: {
        return allocator->make<renderer::Ellipse>(
            static_cast<model::Ellipse *>(contentData));
    }
    case model::Object::Type::Path: {
        return allocator->make<renderer::Path>(
            static_cast<model::Path *>(contentData));
    }
    case model::Object::Type::Polystar: {
        return allocator->make<renderer::Polystar>(
            static_cast<model::Polystar *>(contentData));
    }
    case model::Object::Type::Fill: {
        return allocator->make<renderer::Fill>(
            static_cast<model::Fill *>(contentData));
    }
    case model::Object::Type::GFill: {
        return allocator->make<renderer::GradientFill>(
            static_cast<model::GradientFill *>(contentData));
    }
    case model::Object::Type::Stroke: {
        return allocator->make<renderer::Stroke>(
            static_cast<model::Stroke *>(contentData));
    }
    case model::Object::Type::GStroke: {
        return allocator->make<renderer::GradientStroke>(
            static_cast<model::GradientStroke *>(contentData));
    }
    case model::Object::Type::Repeater: {
        return allocator->make<renderer::Repeater>(
            static_cast<model::Repeater *>(contentData), allocator);
    }
    case model::Object::Type::Trim: {
        return allocator->make<renderer::Trim>(
            static_cast<model::Trim *>(contentData));
    }
    default:
        return nullptr;
        break;
    }
}

renderer::ShapeLayer::ShapeLayer(model::Layer *layerData,
                                 VArenaAlloc * allocator)
    : renderer::Layer(layerData),
      mRoot(allocator->make<renderer::Group>(nullptr, allocator))
{
    mRoot->addChildren(layerData, allocator);

    std::vector<renderer::Shape *> list;
    mRoot->processPaintItems(list);

    if (layerData->hasPathOperator()) {
        list.clear();
        mRoot->processTrimItems(list);
    }
}

void renderer::ShapeLayer::updateContent()
{
    mRoot->update(frameNo(), combinedMatrix(), combinedAlpha(), flag());

    if (mLayerData->hasPathOperator()) {
        mRoot->applyTrim();
    }
}

void renderer::ShapeLayer::preprocessStage(const VRect &clip)
{
    mDrawableList.clear();
    mRoot->renderList(mDrawableList);

    for (auto &drawable : mDrawableList) drawable->preprocess(clip);
}

renderer::DrawableList renderer::ShapeLayer::renderList()
{
    if (skipRendering()) return {};

    mDrawableList.clear();
    mRoot->renderList(mDrawableList);

    if (mDrawableList.empty()) return {};

    return {mDrawableList.data(), mDrawableList.size()};
}

bool renderer::Group::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                     LOTVariant &value)
{
    if (!keyPath.skip(name())) {
        if (!keyPath.matches(mModel.name(), depth)) {
            return false;
        }

        if (!keyPath.skip(mModel.name())) {
            if (keyPath.fullyResolvesTo(mModel.name(), depth) &&
                transformProp(value.property())) {
                mModel.filter()->addValue(value);
            }
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

bool renderer::Fill::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                    LOTVariant &value)
{
    if (!keyPath.matches(mModel.name(), depth)) {
        return false;
    }

    if (keyPath.fullyResolvesTo(mModel.name(), depth) &&
        fillProp(value.property())) {
        mModel.filter()->addValue(value);
        return true;
    }
    return false;
}

bool renderer::Stroke::resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                      LOTVariant &value)
{
    if (!keyPath.matches(mModel.name(), depth)) {
        return false;
    }

    if (keyPath.fullyResolvesTo(mModel.name(), depth) &&
        strokeProp(value.property())) {
        mModel.filter()->addValue(value);
        return true;
    }
    return false;
}

renderer::Group::Group(model::Group *data, VArenaAlloc *allocator)
    : mModel(data)
{
    addChildren(data, allocator);
}

void renderer::Group::addChildren(model::Group *data, VArenaAlloc *allocator)
{
    if (!data) return;

    if (!data->mChildren.empty()) mContents.reserve(data->mChildren.size());

    // keep the content in back-to-front order.
    // as lottie model keeps it in front-to-back order.
    for (auto it = data->mChildren.crbegin(); it != data->mChildren.rend();
         ++it) {
        auto content = createContentItem(*it, allocator);
        if (content) {
            mContents.push_back(content);
        }
    }
}

void renderer::Group::update(int frameNo, const VMatrix &parentMatrix,
                             float parentAlpha, const DirtyFlag &flag)
{
    DirtyFlag newFlag = flag;
    float     alpha;

    if (mModel.hasModel() && mModel.transform()) {
        VMatrix m = mModel.matrix(frameNo);

        m *= parentMatrix;
        if (!(flag & DirtyFlagBit::Matrix) && !mModel.transform()->isStatic() &&
            (m != mMatrix)) {
            newFlag |= DirtyFlagBit::Matrix;
        }

        mMatrix = m;

        alpha = parentAlpha * mModel.transform()->opacity(frameNo);
        if (!vCompare(alpha, parentAlpha)) {
            newFlag |= DirtyFlagBit::Alpha;
        }
    } else {
        mMatrix = parentMatrix;
        alpha = parentAlpha;
    }

    for (const auto &content : mContents) {
        content->update(frameNo, matrix(), alpha, newFlag);
    }
}

void renderer::Group::applyTrim()
{
    for (auto i = mContents.rbegin(); i != mContents.rend(); ++i) {
        auto content = (*i);
        switch (content->type()) {
        case renderer::Object::Type::Trim: {
            static_cast<renderer::Trim *>(content)->update();
            break;
        }
        case renderer::Object::Type::Group: {
            static_cast<renderer::Group *>(content)->applyTrim();
            break;
        }
        default:
            break;
        }
    }
}

void renderer::Group::renderList(std::vector<VDrawable *> &list)
{
    for (const auto &content : mContents) {
        content->renderList(list);
    }
}

void renderer::Group::processPaintItems(std::vector<renderer::Shape *> &list)
{
    size_t curOpCount = list.size();
    for (auto i = mContents.rbegin(); i != mContents.rend(); ++i) {
        auto content = (*i);
        switch (content->type()) {
        case renderer::Object::Type::Shape: {
            auto pathItem = static_cast<renderer::Shape *>(content);
            pathItem->setParent(this);
            list.push_back(pathItem);
            break;
        }
        case renderer::Object::Type::Paint: {
            static_cast<renderer::Paint *>(content)->addPathItems(list,
                                                                  curOpCount);
            break;
        }
        case renderer::Object::Type::Group: {
            static_cast<renderer::Group *>(content)->processPaintItems(list);
            break;
        }
        default:
            break;
        }
    }
}

void renderer::Group::processTrimItems(std::vector<renderer::Shape *> &list)
{
    size_t curOpCount = list.size();
    for (auto i = mContents.rbegin(); i != mContents.rend(); ++i) {
        auto content = (*i);

        switch (content->type()) {
        case renderer::Object::Type::Shape: {
            list.push_back(static_cast<renderer::Shape *>(content));
            break;
        }
        case renderer::Object::Type::Trim: {
            static_cast<renderer::Trim *>(content)->addPathItems(list,
                                                                 curOpCount);
            break;
        }
        case renderer::Object::Type::Group: {
            static_cast<renderer::Group *>(content)->processTrimItems(list);
            break;
        }
        default:
            break;
        }
    }
}

/*
 * renderer::Shape uses 2 path objects for path object reuse.
 * mLocalPath -  keeps track of the local path of the item before
 * applying path operation and transformation.
 * mTemp - keeps a referece to the mLocalPath and can be updated by the
 *          path operation objects(trim, merge path),
 * We update the DirtyPath flag if the path needs to be updated again
 * beacuse of local path or matrix or some path operation has changed which
 * affects the final path.
 * The PaintObject queries the dirty flag to check if it needs to compute the
 * final path again and calls finalPath() api to do the same.
 * finalPath() api passes a result Object so that we keep only one copy of
 * the path object in the paintItem (for memory efficiency).
 *   NOTE: As path objects are COW objects we have to be
 * carefull about the refcount so that we don't generate deep copy while
 * modifying the path objects.
 */
void renderer::Shape::update(int              frameNo, const VMatrix &, float,
                             const DirtyFlag &flag)
{
    mDirtyPath = false;

    // 1. update the local path if needed
    if (hasChanged(frameNo)) {
        // loose the reference to mLocalPath if any
        // from the last frame update.
        mTemp = VPath();

        updatePath(mLocalPath, frameNo);
        mDirtyPath = true;
    }
    // 2. keep a reference path in temp in case there is some
    // path operation like trim which will update the path.
    // we don't want to update the local path.
    mTemp = mLocalPath;

    // 3. mark the path dirty if matrix has changed.
    if (flag & DirtyFlagBit::Matrix) {
        mDirtyPath = true;
    }
}

void renderer::Shape::finalPath(VPath &result)
{
    result.addPath(mTemp, static_cast<renderer::Group *>(parent())->matrix());
}

renderer::Rect::Rect(model::Rect *data)
    : renderer::Shape(data->isStatic()), mData(data)
{
}

void renderer::Rect::updatePath(VPath &path, int frameNo)
{
    VPointF pos = mData->mPos.value(frameNo);
    VPointF size = mData->mSize.value(frameNo);
    float   roundness = mData->roundness(frameNo);
    VRectF  r(pos.x() - size.x() / 2, pos.y() - size.y() / 2, size.x(),
             size.y());

    path.reset();
    path.addRoundRect(r, roundness, mData->direction());
}

renderer::Ellipse::Ellipse(model::Ellipse *data)
    : renderer::Shape(data->isStatic()), mData(data)
{
}

void renderer::Ellipse::updatePath(VPath &path, int frameNo)
{
    VPointF pos = mData->mPos.value(frameNo);
    VPointF size = mData->mSize.value(frameNo);
    VRectF  r(pos.x() - size.x() / 2, pos.y() - size.y() / 2, size.x(),
             size.y());

    path.reset();
    path.addOval(r, mData->direction());
}

renderer::Path::Path(model::Path *data)
    : renderer::Shape(data->isStatic()), mData(data)
{
}

void renderer::Path::updatePath(VPath &path, int frameNo)
{
    mData->mShape.value(frameNo, path);
}

renderer::Polystar::Polystar(model::Polystar *data)
    : renderer::Shape(data->isStatic()), mData(data)
{
}

void renderer::Polystar::updatePath(VPath &path, int frameNo)
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

    if (mData->mPolyType == model::Polystar::PolyType::Star) {
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
renderer::Paint::Paint(bool staticContent) : mStaticContent(staticContent) {}

void renderer::Paint::update(int frameNo, const VMatrix &parentMatrix,
                             float parentAlpha, const DirtyFlag & /*flag*/)
{
    mRenderNodeUpdate = true;
    mContentToRender = updateContent(frameNo, parentMatrix, parentAlpha);
}

void renderer::Paint::updateRenderNode()
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
        for (const auto &i : mPathItems) {
            i->finalPath(mPath);
        }
        mDrawable.setPath(mPath);
    } else {
        if (mDrawable.mFlag & VDrawable::DirtyState::Path)
            mDrawable.mPath = mPath;
    }
}

void renderer::Paint::renderList(std::vector<VDrawable *> &list)
{
    if (mRenderNodeUpdate) {
        updateRenderNode();
        mRenderNodeUpdate = false;
    }

    // Q: Why we even update the final path if we don't have content
    // to render ?
    // Ans: We update the render nodes because we will loose the
    // dirty path information at end of this frame.
    // so if we return early without updating the final path.
    // in the subsequent frame when we have content to render but
    // we may not able to update our final path properly as we
    // don't know what paths got changed in between.
    if (mContentToRender) list.push_back(&mDrawable);
}

void renderer::Paint::addPathItems(std::vector<renderer::Shape *> &list,
                                   size_t                          startOffset)
{
    std::copy(list.begin() + startOffset, list.end(),
              back_inserter(mPathItems));
}

renderer::Fill::Fill(model::Fill *data)
    : renderer::Paint(data->isStatic()), mModel(data)
{
    mDrawable.setName(mModel.name());
}

bool renderer::Fill::updateContent(int frameNo, const VMatrix &, float alpha)
{
    auto combinedAlpha = alpha * mModel.opacity(frameNo);
    auto color = mModel.color(frameNo).toColor(combinedAlpha);

    VBrush brush(color);
    mDrawable.setBrush(brush);
    mDrawable.setFillRule(mModel.fillRule());

    return !color.isTransparent();
}

renderer::GradientFill::GradientFill(model::GradientFill *data)
    : renderer::Paint(data->isStatic()), mData(data)
{
    mDrawable.setName(mData->name());
}

bool renderer::GradientFill::updateContent(int frameNo, const VMatrix &matrix,
                                           float alpha)
{
    float combinedAlpha = alpha * mData->opacity(frameNo);

    mData->update(mGradient, frameNo);
    mGradient->setAlpha(combinedAlpha);
    mGradient->mMatrix = matrix;
    mDrawable.setBrush(VBrush(mGradient.get()));
    mDrawable.setFillRule(mData->fillRule());

    return !vIsZero(combinedAlpha);
}

renderer::Stroke::Stroke(model::Stroke *data)
    : renderer::Paint(data->isStatic()), mModel(data)
{
    mDrawable.setName(mModel.name());
    if (mModel.hasDashInfo()) {
        mDrawable.setType(VDrawable::Type::StrokeWithDash);
    } else {
        mDrawable.setType(VDrawable::Type::Stroke);
    }
}

static vthread_local std::vector<float> Dash_Vector;

bool renderer::Stroke::updateContent(int frameNo, const VMatrix &matrix,
                                     float alpha)
{
    auto combinedAlpha = alpha * mModel.opacity(frameNo);
    auto color = mModel.color(frameNo).toColor(combinedAlpha);

    VBrush brush(color);
    mDrawable.setBrush(brush);
    float scale = matrix.scale();
    mDrawable.setStrokeInfo(mModel.capStyle(), mModel.joinStyle(),
                            mModel.miterLimit(),
                            mModel.strokeWidth(frameNo) * scale);

    if (mModel.hasDashInfo()) {
        Dash_Vector.clear();
        mModel.getDashInfo(frameNo, Dash_Vector);
        if (!Dash_Vector.empty()) {
            for (auto &elm : Dash_Vector) elm *= scale;
            mDrawable.setDashInfo(Dash_Vector);
        }
    }

    return !color.isTransparent();
}

renderer::GradientStroke::GradientStroke(model::GradientStroke *data)
    : renderer::Paint(data->isStatic()), mData(data)
{
    mDrawable.setName(mData->name());
    if (mData->hasDashInfo()) {
        mDrawable.setType(VDrawable::Type::StrokeWithDash);
    } else {
        mDrawable.setType(VDrawable::Type::Stroke);
    }
}

bool renderer::GradientStroke::updateContent(int frameNo, const VMatrix &matrix,
                                             float alpha)
{
    float combinedAlpha = alpha * mData->opacity(frameNo);

    mData->update(mGradient, frameNo);
    mGradient->setAlpha(combinedAlpha);
    mGradient->mMatrix = matrix;
    auto scale = mGradient->mMatrix.scale();
    mDrawable.setBrush(VBrush(mGradient.get()));
    mDrawable.setStrokeInfo(mData->capStyle(), mData->joinStyle(),
                            mData->miterLimit(), mData->width(frameNo) * scale);

    if (mData->hasDashInfo()) {
        Dash_Vector.clear();
        mData->getDashInfo(frameNo, Dash_Vector);
        if (!Dash_Vector.empty()) {
            for (auto &elm : Dash_Vector) elm *= scale;
            mDrawable.setDashInfo(Dash_Vector);
        }
    }

    return !vIsZero(combinedAlpha);
}

void renderer::Trim::update(int frameNo, const VMatrix & /*parentMatrix*/,
                            float /*parentAlpha*/, const DirtyFlag & /*flag*/)
{
    mDirty = false;

    if (mCache.mFrameNo == frameNo) return;

    model::Trim::Segment segment = mData->segment(frameNo);

    if (!(vCompare(mCache.mSegment.start, segment.start) &&
          vCompare(mCache.mSegment.end, segment.end))) {
        mDirty = true;
        mCache.mSegment = segment;
    }
    mCache.mFrameNo = frameNo;
}

void renderer::Trim::update()
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

    if (mData->type() == model::Trim::TrimType::Simultaneously) {
        for (auto &i : mPathItems) {
            mPathMesure.setRange(mCache.mSegment.start, mCache.mSegment.end);
            i->updatePath(mPathMesure.trim(i->localPath()));
        }
    } else {  // model::Trim::TrimType::Individually
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
                    mPathMesure.setRange(local_start, local_end);
                    i->updatePath(mPathMesure.trim(i->localPath()));
                    curLen += len;
                }
            }
        }
    }
}

void renderer::Trim::addPathItems(std::vector<renderer::Shape *> &list,
                                  size_t                          startOffset)
{
    std::copy(list.begin() + startOffset, list.end(),
              back_inserter(mPathItems));
}

renderer::Repeater::Repeater(model::Repeater *data, VArenaAlloc *allocator)
    : mRepeaterData(data)
{
    assert(mRepeaterData->content());

    mCopies = mRepeaterData->maxCopies();

    for (int i = 0; i < mCopies; i++) {
        auto content = allocator->make<renderer::Group>(
            mRepeaterData->content(), allocator);
        // content->setParent(this);
        mContents.push_back(content);
    }
}

void renderer::Repeater::update(int frameNo, const VMatrix &parentMatrix,
                                float parentAlpha, const DirtyFlag &flag)
{
    DirtyFlag newFlag = flag;

    float copies = mRepeaterData->copies(frameNo);
    int   visibleCopies = int(copies);

    if (visibleCopies == 0) {
        mHidden = true;
        return;
    }

    mHidden = false;

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

void renderer::Repeater::renderList(std::vector<VDrawable *> &list)
{
    if (mHidden) return;
    return renderer::Group::renderList(list);
}
