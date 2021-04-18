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
#include "config.h"
#include "lottieitem.h"
#include "lottiemodel.h"
#include "rlottie.h"

#include <fstream>

using namespace rlottie;
using namespace rlottie::internal;

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
    void    init(std::shared_ptr<model::Composition> composition);
    bool    update(size_t frameNo, const VSize &size);
    VSize   size() const { return mModel->size(); }
    double  duration() const { return mModel->duration(); }
    double  frameRate() const { return mModel->frameRate(); }
    size_t  totalFrame() const { return mModel->totalFrame(); }
    size_t  frameAtPos(double pos) const { return mModel->frameAtPos(pos); }
    Surface render(size_t frameNo, const Surface &surface, bool clear);
    const LOTLayerNode * renderTree(size_t frameNo, const VSize &size);

    const LayerInfoList &layerInfoList() const
    {
        if (mLayerList.empty()) {
            mLayerList = mModel->layerInfoList();
        }
        return mLayerList;
    }
    const MarkerList &markers() const { return mModel->markers(); }
    void              setValue(const std::string &keypath, LOTVariant &&value);
    void              removeFilter(const std::string &keypath, Property prop);
    void resetCurrentFrame();

private:
    mutable LayerInfoList                  mLayerList;
    model::Composition *                   mModel;
    SharedRenderTask                       mTask;
    std::atomic<bool>                      mRenderInProgress;
    std::unique_ptr<renderer::Composition> mRenderer{nullptr};
};

void AnimationImpl::setValue(const std::string &keypath, LOTVariant &&value)
{
    if (keypath.empty()) return;
    mRenderer->setValue(keypath, value);
}

void AnimationImpl::resetCurrentFrame() {
    mRenderer->resetCurrentFrame();
}

const LOTLayerNode *AnimationImpl::renderTree(size_t frameNo, const VSize &size)
{
    if (update(frameNo, size)) {
        mRenderer->buildRenderTree();
    }
    return mRenderer->renderTree();
}

bool AnimationImpl::update(size_t frameNo, const VSize &size)
{
    frameNo += mModel->startFrame();

    if (frameNo > mModel->endFrame()) frameNo = mModel->endFrame();

    if (frameNo < mModel->startFrame()) frameNo = mModel->startFrame();

    return mRenderer->update(int(frameNo), size);
}

Surface AnimationImpl::render(size_t frameNo, const Surface &surface, bool clear)
{
    bool renderInProgress = mRenderInProgress.load();
    if (renderInProgress) {
        vCritical << "Already Rendering Scheduled for this Animation";
        return surface;
    }

    mRenderInProgress.store(true);
    update(
        frameNo,
        VSize(int(surface.drawRegionWidth()), int(surface.drawRegionHeight())));
    mRenderer->render(surface, clear);
    mRenderInProgress.store(false);

    return surface;
}

void AnimationImpl::init(std::shared_ptr<model::Composition> composition)
{
    mModel = composition.get();
    mRenderer = std::make_unique<renderer::Composition>(composition);
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
    const std::string &resourcePath)
{
    if (jsonData.empty()) {
        vWarning << "jason data is empty";
        return nullptr;
    }

    auto composition = model::loadFromData(std::move(jsonData), key,
                                           colorReplacement, resourcePath);
    if (composition) {
        auto animation = std::unique_ptr<Animation>(new Animation);
        animation->colorMap = colorReplacement;
        animation->d->init(std::move(composition));
        return animation;
    }
    if (colorReplacement != nullptr) {
        delete colorReplacement;
    }
    return nullptr;
}

std::unique_ptr<Animation> Animation::loadFromFile(const std::string &path, std::map<int32_t, int32_t> *colorReplacement)
{
    if (path.empty()) {
        vWarning << "File path is empty";
        return nullptr;
    }

    auto composition = model::loadFromFile(path, colorReplacement);
    if (composition) {
        auto animation = std::unique_ptr<Animation>(new Animation);
        animation->colorMap = colorReplacement;
        animation->d->init(std::move(composition));
        return animation;
    }
    if (colorReplacement != nullptr) {
        delete colorReplacement;
    }
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
    return d->renderTree(frameNo, VSize(int(width), int(height)));
}

void Animation::renderSync(size_t frameNo, Surface &surface, bool clear)
{
    d->render(frameNo, surface, clear);
}

const LayerInfoList &Animation::layers() const
{
    return d->layerInfoList();
}

const MarkerList &Animation::markers() const
{
    return d->markers();
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
#if defined(__ARM_NEON__) || defined(__ARM64_NEON__)
    set_log_level(LogLevel::OFF);
#else
    initialize(GuaranteedLogger(), "/tmp/", "rlottie", 1);
    set_log_level(LogLevel::INFO);
#endif
}

V_CONSTRUCTOR_FUNCTION(initLogging)
#endif
