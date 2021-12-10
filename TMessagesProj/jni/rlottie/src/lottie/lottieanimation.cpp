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
#include "config.h"
#include "lottieitem.h"
#include "lottieloader.h"
#include "lottiemodel.h"
#include "rlottie.h"

#include <fstream>

using namespace rlottie;

struct RenderTask {
    RenderTask() { receiver = sender.get_future(); }
    std::promise<Surface> sender;
    std::future<Surface>  receiver;
    AnimationImpl *       playerImpl{nullptr};
    size_t                frameNo{0};
    Surface               surface;
};
using SharedRenderTask = std::shared_ptr<RenderTask>;

class AnimationImpl {
public:
    void    init(const std::shared_ptr<LOTModel> &model);
    bool    update(size_t frameNo, const VSize &size);
    VSize   size() const { return mCompItem->size(); }
    double  duration() const { return mModel->duration(); }
    double  frameRate() const { return mModel->frameRate(); }
    size_t  totalFrame() const { return mModel->totalFrame(); }
    size_t  frameAtPos(double pos) const { return mModel->frameAtPos(pos); }
    Surface render(size_t frameNo, const Surface &surface, bool clear);
    const LOTLayerNode * renderTree(size_t frameNo, const VSize &size);

    const LayerInfoList &layerInfoList() const
    {
        return mModel->layerInfoList();
    }
    void setValue(const std::string &keypath, LOTVariant &&value);
    void removeFilter(const std::string &keypath, Property prop);
    void resetCurrentFrame();

private:
    std::string                  mFilePath;
    std::shared_ptr<LOTModel>    mModel;
    std::unique_ptr<LOTCompItem> mCompItem;
    SharedRenderTask             mTask;
    std::atomic<bool>            mRenderInProgress{false};
};

void AnimationImpl::setValue(const std::string &keypath, LOTVariant &&value)
{
    if (keypath.empty()) return;
    mCompItem->setValue(keypath, value);
}

void AnimationImpl::resetCurrentFrame() {
    mCompItem->resetCurrentFrame();
}

const LOTLayerNode *AnimationImpl::renderTree(size_t frameNo, const VSize &size)
{
    if (update(frameNo, size)) {
        mCompItem->buildRenderTree();
    }
    return mCompItem->renderTree();
}

bool AnimationImpl::update(size_t frameNo, const VSize &size)
{
    frameNo += mModel->startFrame();

    if (frameNo > mModel->endFrame()) frameNo = mModel->endFrame();

    if (frameNo < mModel->startFrame()) frameNo = mModel->startFrame();

    mCompItem->resize(size);
    return mCompItem->update(frameNo);
}

Surface AnimationImpl::render(size_t frameNo, const Surface &surface, bool clear)
{
    bool renderInProgress = mRenderInProgress.load();
    if (renderInProgress) {
        vCritical << "Already Rendering Scheduled for this Animation";
        return surface;
    }

    mRenderInProgress.store(true);
    update(frameNo,
           VSize(surface.drawRegionWidth(), surface.drawRegionHeight()));
    mCompItem->render(surface, clear);
    mRenderInProgress.store(false);

    return surface;
}

void AnimationImpl::init(const std::shared_ptr<LOTModel> &model)
{
    mModel = model;
    mCompItem = std::make_unique<LOTCompItem>(mModel.get());
    mRenderInProgress = false;
}

/**
 * \breif Brief abput the Api.
 * Description about the setFilePath Api
 * @param path  add the details
 */
std::unique_ptr<Animation> Animation::loadFromData(
    std::string jsonData, const std::string &key,
    std::map<int32_t, int32_t> *colorReplacement,
    FitzModifier fitzModifier,
    const std::string &resourcePath)
{
    if (jsonData.empty()) {
        vWarning << "jason data is empty";
        return nullptr;
    }

    LottieLoader loader;
    if (loader.loadFromData(std::move(jsonData), key,
                            colorReplacement,
                            (resourcePath.empty() ? " " : resourcePath), fitzModifier)) {
        auto animation = std::unique_ptr<Animation>(new Animation);
        animation->colorMap = colorReplacement;
        animation->d->init(loader.model());
        return animation;
    }
    delete colorReplacement;
    return nullptr;
}

std::unique_ptr<Animation> Animation::loadFromFile(const std::string &path, std::map<int32_t, int32_t> *colorReplacement, FitzModifier fitzModifier)
{
    if (path.empty()) {
        vWarning << "File path is empty";
        return nullptr;
    }

    LottieLoader loader;
    if (loader.load(path, colorReplacement, fitzModifier)) {
        auto animation = std::unique_ptr<Animation>(new Animation);
        animation->colorMap = colorReplacement;
        animation->d->init(loader.model());
        return animation;
    }
    delete colorReplacement;
    return nullptr;
}

void Animation::size(size_t &width, size_t &height) const
{
    VSize sz = d->size();

    width = sz.width();
    height = sz.height();
}

double Animation::duration() const
{
    return d->duration();
}

double Animation::frameRate() const
{
    return d->frameRate();
}

size_t Animation::totalFrame() const
{
    return d->totalFrame();
}

size_t Animation::frameAtPos(double pos)
{
    return d->frameAtPos(pos);
}

const LOTLayerNode *Animation::renderTree(size_t frameNo, size_t width,
                                          size_t height) const
{
    return d->renderTree(frameNo, VSize(width, height));
}

void Animation::renderSync(size_t frameNo, Surface &surface, bool clear)
{
    d->render(frameNo, surface, clear);
}

const LayerInfoList &Animation::layers() const
{
    return d->layerInfoList();
}

void Animation::setValue(Color_Type, Property prop, const std::string &keypath,
                         Color value)
{
    d->setValue(keypath,
                LOTVariant(prop, [value](const FrameInfo &) { return value; }));
}

void Animation::setValue(Float_Type, Property prop, const std::string &keypath,
                         float value)
{
    d->setValue(keypath,
                LOTVariant(prop, [value](const FrameInfo &) { return value; }));
}

void Animation::setValue(Size_Type, Property prop, const std::string &keypath,
                         Size value)
{
    d->setValue(keypath,
                LOTVariant(prop, [value](const FrameInfo &) { return value; }));
}

void Animation::setValue(Point_Type, Property prop, const std::string &keypath,
                         Point value)
{
    d->setValue(keypath,
                LOTVariant(prop, [value](const FrameInfo &) { return value; }));
}

void Animation::setValue(Color_Type, Property prop, const std::string &keypath,
                         std::function<Color(const FrameInfo &)> &&value)
{
    d->setValue(keypath, LOTVariant(prop, value));
}

void Animation::setValue(Float_Type, Property prop, const std::string &keypath,
                         std::function<float(const FrameInfo &)> &&value)
{
    d->setValue(keypath, LOTVariant(prop, value));
}

void Animation::setValue(Size_Type, Property prop, const std::string &keypath,
                         std::function<Size(const FrameInfo &)> &&value)
{
    d->setValue(keypath, LOTVariant(prop, value));
}

void Animation::setValue(Point_Type, Property prop, const std::string &keypath,
                         std::function<Point(const FrameInfo &)> &&value)
{
    d->setValue(keypath, LOTVariant(prop, value));
}

Animation::Animation() : d(std::make_unique<AnimationImpl>()) {}

/*
 * this is only to supress build fail
 * because unique_ptr expects the destructor in the same translation unit.
 */
Animation::~Animation() {
    if (colorMap != nullptr) {
        delete colorMap;
        colorMap = nullptr;
    }
}

void Animation::resetCurrentFrame() {
    d->resetCurrentFrame();
}

Surface::Surface(uint32_t *buffer, size_t width, size_t height,
                 size_t bytesPerLine)
    : mBuffer(buffer),
      mWidth(width),
      mHeight(height),
      mBytesPerLine(bytesPerLine)
{
    mDrawArea.w = mWidth;
    mDrawArea.h = mHeight;
}

void Surface::setDrawRegion(size_t x, size_t y, size_t width, size_t height)
{
    if ((x + width > mWidth) || (y + height > mHeight)) return;

    mDrawArea.x = x;
    mDrawArea.y = y;
    mDrawArea.w = width;
    mDrawArea.h = height;
}

#ifdef LOTTIE_LOGGING_SUPPORT
void initLogging()
{
#if defined(__ARM_NEON__)
    set_log_level(LogLevel::OFF);
#else
    initialize(GuaranteedLogger(), "/tmp/", "rlottie", 1);
    set_log_level(LogLevel::INFO);
#endif
}

V_CONSTRUCTOR_FUNCTION(initLogging)
#endif
