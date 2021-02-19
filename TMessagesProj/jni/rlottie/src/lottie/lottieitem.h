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

#ifndef LOTTIEITEM_H
#define LOTTIEITEM_H

#include <memory>
#include <sstream>

#include "lottiekeypath.h"
#include "lottiefiltermodel.h"
#include "rlottie.h"
#include "rlottiecommon.h"
#include "varenaalloc.h"
#include "vdrawable.h"
#include "vmatrix.h"
#include "vpainter.h"
#include "vpath.h"
#include "vpathmesure.h"
#include "vpoint.h"

V_USE_NAMESPACE

namespace rlottie {

namespace internal {

template <class T>
class VSpan {
public:
    using reference = T &;
    using pointer = T *;
    using const_pointer = T const *;
    using const_reference = T const &;
    using index_type = size_t;

    using iterator = pointer;
    using const_iterator = const_pointer;

    VSpan() = default;
    VSpan(pointer data, index_type size) : _data(data), _size(size) {}

    constexpr pointer        data() const noexcept { return _data; }
    constexpr index_type     size() const noexcept { return _size; }
    constexpr bool           empty() const noexcept { return size() == 0; }
    constexpr iterator       begin() const noexcept { return data(); }
    constexpr iterator       end() const noexcept { return data() + size(); }
    constexpr const_iterator cbegin() const noexcept { return data(); }
    constexpr const_iterator cend() const noexcept { return data() + size(); }
    constexpr reference      operator[](index_type idx) const
    {
        return *(data() + idx);
    }

private:
    pointer    _data{nullptr};
    index_type _size{0};
};

namespace renderer {

using DrawableList = VSpan<VDrawable *>;

enum class DirtyFlagBit : uchar {
    None = 0x00,
    Matrix = 0x01,
    Alpha = 0x02,
    All = (Matrix | Alpha)
};
typedef vFlag<DirtyFlagBit> DirtyFlag;

class SurfaceCache {
public:
    SurfaceCache() { mCache.reserve(10); }

    VBitmap make_surface(
        size_t width, size_t height,
        VBitmap::Format format = VBitmap::Format::ARGB32_Premultiplied)
    {
        if (mCache.empty()) return {width, height, format};

        auto surface = mCache.back();
        surface.reset(width, height, format);

        mCache.pop_back();
        return surface;
    }

    void release_surface(VBitmap &surface) { mCache.push_back(surface); }

private:
    std::vector<VBitmap> mCache;
};

class Drawable final : public VDrawable {
public:
    void sync();

public:
    std::unique_ptr<LOTNode> mCNode{nullptr};

    ~Drawable() noexcept
    {
        if (mCNode && mCNode->mGradient.stopPtr)
            free(mCNode->mGradient.stopPtr);
    }
};

struct CApiData {
    CApiData();
    LOTLayerNode                mLayer;
    std::vector<LOTMask>        mMasks;
    std::vector<LOTLayerNode *> mLayers;
    std::vector<LOTNode *>      mCNodeList;
};

class Clipper {
public:
    explicit Clipper(VSize size) : mSize(size) {}
    void update(const VMatrix &matrix);
    void preprocess(const VRect &clip);
    VRle rle(const VRle &mask);

public:
    VSize       mSize;
    VPath       mPath;
    VRle        mMaskedRle;
    VRasterizer mRasterizer;
    bool        mRasterRequest{false};
};

class Mask {
public:
    explicit Mask(model::Mask *data) : mData(data) {}
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag);
    model::Mask::Mode maskMode() const { return mData->mMode; }
    VRle              rle();
    void              preprocess(const VRect &clip);
    bool              inverted() const { return mData->mInv; }
public:
    model::Mask *mData{nullptr};
    VPath        mLocalPath;
    VPath        mFinalPath;
    VRasterizer  mRasterizer;
    float        mCombinedAlpha{0};
    bool         mRasterRequest{false};
};

/*
 * Handels mask property of a layer item
 */
class LayerMask {
public:
    explicit LayerMask(model::Layer *layerData);
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag);
    bool isStatic() const { return mStatic; }
    VRle maskRle(const VRect &clipRect);
    void preprocess(const VRect &clip);

public:
    std::vector<Mask> mMasks;
    VRle              mRle;
    bool              mStatic{true};
    bool              mDirty{true};
};

class Layer;

class Composition {
public:
    explicit Composition(std::shared_ptr<model::Composition> composition);
    bool  update(int frameNo, const VSize &size);
    VSize size() const { return mViewSize; }
    void  buildRenderTree();
    const LOTLayerNode *renderTree() const;
    bool                render(const rlottie::Surface &surface, bool clear);
    void                setValue(const std::string &keypath, LOTVariant &value);
    void resetCurrentFrame();

private:
    SurfaceCache                        mSurfaceCache;
    VBitmap                             mSurface;
    VMatrix                             mScaleMatrix;
    VSize                               mViewSize;
    std::shared_ptr<model::Composition> mModel;
    Layer *                             mRootLayer{nullptr};
    VArenaAlloc                         mAllocator{2048};
    int                                 mCurFrameNo;
    bool                                mKeepAspectRatio{true};
};

class Layer {
public:
    virtual ~Layer() = default;
    Layer &operator=(Layer &&) noexcept = delete;
    Layer(model::Layer *layerData);
    int          id() const { return mLayerData->id(); }
    int          parentId() const { return mLayerData->parentId(); }
    void         setParentLayer(Layer *parent) { mParentLayer = parent; }
    void         setComplexContent(bool value) { mComplexContent = value; }
    bool         complexContent() const { return mComplexContent; }
    virtual void update(int frameNo, const VMatrix &parentMatrix,
                        float parentAlpha);
    VMatrix      matrix(int frameNo) const;
    void         preprocess(const VRect &clip);
    virtual DrawableList renderList() { return {}; }
    virtual void         render(VPainter *painter, const VRle &mask,
                                const VRle &matteRle, SurfaceCache &cache);
    bool                 hasMatte()
    {
        if (mLayerData->mMatteType == model::MatteType::None) return false;
        return true;
    }
    model::MatteType matteType() const { return mLayerData->mMatteType; }
    bool             visible() const;
    virtual void     buildLayerNode();
    LOTLayerNode &   clayer() { return mCApiData->mLayer; }
    std::vector<LOTLayerNode *> &clayers() { return mCApiData->mLayers; }
    std::vector<LOTMask> &       cmasks() { return mCApiData->mMasks; }
    std::vector<LOTNode *> &     cnodes() { return mCApiData->mCNodeList; }
    const char *                 name() const { return mLayerData->name(); }
    virtual bool                 resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                                LOTVariant &value);

protected:
    virtual void   preprocessStage(const VRect &clip) = 0;
    virtual void   updateContent() = 0;
    inline VMatrix combinedMatrix() const { return mCombinedMatrix; }
    inline int     frameNo() const { return mFrameNo; }
    inline float   combinedAlpha() const { return mCombinedAlpha; }
    inline bool    isStatic() const { return mLayerData->isStatic(); }
    float opacity(int frameNo) const { return mLayerData->opacity(frameNo); }
    inline DirtyFlag flag() const { return mDirtyFlag; }
    bool             skipRendering() const
    {
        return (!visible() || vIsZero(combinedAlpha()));
    }

protected:
    std::unique_ptr<LayerMask> mLayerMask;
    model::Layer *             mLayerData{nullptr};
    Layer *                    mParentLayer{nullptr};
    VMatrix                    mCombinedMatrix;
    float                      mCombinedAlpha{0.0};
    int                        mFrameNo{-1};
    DirtyFlag                  mDirtyFlag{DirtyFlagBit::All};
    bool                       mComplexContent{false};
    std::unique_ptr<CApiData>  mCApiData;
};

class CompLayer final : public Layer {
public:
    explicit CompLayer(model::Layer *layerData, VArenaAlloc *allocator);

    void render(VPainter *painter, const VRle &mask, const VRle &matteRle,
                SurfaceCache &cache) final;
    void buildLayerNode() final;
    bool resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                        LOTVariant &value) override;

protected:
    void preprocessStage(const VRect &clip) final;
    void updateContent() final;

private:
    void renderHelper(VPainter *painter, const VRle &mask, const VRle &matteRle,
                      SurfaceCache &cache);
    void renderMatteLayer(VPainter *painter, const VRle &inheritMask,
                          const VRle &matteRle, Layer *layer, Layer *src,
                          SurfaceCache &cache);

private:
    std::vector<Layer *>     mLayers;
    std::unique_ptr<Clipper> mClipper;
};

class SolidLayer final : public Layer {
public:
    explicit SolidLayer(model::Layer *layerData);
    void         buildLayerNode() final;
    DrawableList renderList() final;

protected:
    void preprocessStage(const VRect &clip) final;
    void updateContent() final;

private:
    Drawable   mRenderNode;
    VPath      mPath;
    VDrawable *mDrawableList{nullptr};  // to work with the Span api
};

class Group;

class ShapeLayer final : public Layer {
public:
    explicit ShapeLayer(model::Layer *layerData, VArenaAlloc *allocator);
    DrawableList renderList() final;
    void         buildLayerNode() final;
    bool         resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                                LOTVariant &value) override;

protected:
    void                     preprocessStage(const VRect &clip) final;
    void                     updateContent() final;
    std::vector<VDrawable *> mDrawableList;
    Group *                  mRoot{nullptr};
};

class NullLayer final : public Layer {
public:
    explicit NullLayer(model::Layer *layerData);

protected:
    void preprocessStage(const VRect &) final {}
    void updateContent() final;
};

class ImageLayer final : public Layer {
public:
    explicit ImageLayer(model::Layer *layerData);
    void         buildLayerNode() final;
    DrawableList renderList() final;

protected:
    void preprocessStage(const VRect &clip) final;
    void updateContent() final;

private:
    Drawable   mRenderNode;
    VTexture   mTexture;
    VPath      mPath;
    VDrawable *mDrawableList{nullptr};  // to work with the Span api
};

class Object {
public:
    enum class Type : uchar { Unknown, Group, Shape, Paint, Trim };
    virtual ~Object() = default;
    Object &     operator=(Object &&) noexcept = delete;
    virtual void update(int frameNo, const VMatrix &parentMatrix,
                        float parentAlpha, const DirtyFlag &flag) = 0;
    virtual void renderList(std::vector<VDrawable *> &) {}
    virtual bool resolveKeyPath(LOTKeyPath &, uint, LOTVariant &)
    {
        return false;
    }
    virtual Object::Type type() const { return Object::Type::Unknown; }
};

class Shape;
class Group : public Object {
public:
    Group() = default;
    explicit Group(model::Group *data, VArenaAlloc *allocator);
    void addChildren(model::Group *data, VArenaAlloc *allocator);
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag) override;
    void applyTrim();
    void processTrimItems(std::vector<Shape *> &list);
    void processPaintItems(std::vector<Shape *> &list);
    void renderList(std::vector<VDrawable *> &list) override;
    Object::Type   type() const final { return Object::Type::Group; }
    const VMatrix &matrix() const { return mMatrix; }
    const char *   name() const
    {
        static const char *TAG = "__";
        return mModel.hasModel() ? mModel.name() : TAG;
    }
    bool resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                        LOTVariant &value) override;

protected:
    std::vector<Object *> mContents;
    VMatrix               mMatrix;

private:
    model::Filter<model::Group> mModel;
};

class Shape : public Object {
public:
    Shape(bool staticPath) : mStaticPath(staticPath) {}
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag) final;
    Object::Type type() const final { return Object::Type::Shape; }
    bool         dirty() const { return mDirtyPath; }
    const VPath &localPath() const { return mTemp; }
    void         finalPath(VPath &result);
    void         updatePath(const VPath &path)
    {
        mTemp = path;
        mDirtyPath = true;
    }
    bool   staticPath() const { return mStaticPath; }
    void   setParent(Group *parent) { mParent = parent; }
    Group *parent() const { return mParent; }

protected:
    virtual void updatePath(VPath &path, int frameNo) = 0;
    virtual bool hasChanged(int prevFrame, int curFrame) = 0;

private:
    bool hasChanged(int frameNo)
    {
        int prevFrame = mFrameNo;
        mFrameNo = frameNo;
        if (prevFrame == -1) return true;
        if (mStaticPath || (prevFrame == frameNo)) return false;
        return hasChanged(prevFrame, frameNo);
    }
    Group *mParent{nullptr};
    VPath  mLocalPath;
    VPath  mTemp;
    int    mFrameNo{-1};
    bool   mDirtyPath{true};
    bool   mStaticPath;
};

class Rect final : public Shape {
public:
    explicit Rect(model::Rect *data);

protected:
    void         updatePath(VPath &path, int frameNo) final;
    model::Rect *mData{nullptr};

    bool hasChanged(int prevFrame, int curFrame) final
    {
        return (mData->mPos.changed(prevFrame, curFrame) ||
                mData->mSize.changed(prevFrame, curFrame) ||
                mData->roundnessChanged(prevFrame, curFrame));
    }
};

class Ellipse final : public Shape {
public:
    explicit Ellipse(model::Ellipse *data);

private:
    void            updatePath(VPath &path, int frameNo) final;
    model::Ellipse *mData{nullptr};
    bool            hasChanged(int prevFrame, int curFrame) final
    {
        return (mData->mPos.changed(prevFrame, curFrame) ||
                mData->mSize.changed(prevFrame, curFrame));
    }
};

class Path final : public Shape {
public:
    explicit Path(model::Path *data);

private:
    void         updatePath(VPath &path, int frameNo) final;
    model::Path *mData{nullptr};
    bool         hasChanged(int prevFrame, int curFrame) final
    {
        return mData->mShape.changed(prevFrame, curFrame);
    }
};

class Polystar final : public Shape {
public:
    explicit Polystar(model::Polystar *data);

private:
    void             updatePath(VPath &path, int frameNo) final;
    model::Polystar *mData{nullptr};

    bool hasChanged(int prevFrame, int curFrame) final
    {
        return (mData->mPos.changed(prevFrame, curFrame) ||
                mData->mPointCount.changed(prevFrame, curFrame) ||
                mData->mInnerRadius.changed(prevFrame, curFrame) ||
                mData->mOuterRadius.changed(prevFrame, curFrame) ||
                mData->mInnerRoundness.changed(prevFrame, curFrame) ||
                mData->mOuterRoundness.changed(prevFrame, curFrame) ||
                mData->mRotation.changed(prevFrame, curFrame));
    }
};

class Paint : public Object {
public:
    Paint(bool staticContent);
    void addPathItems(std::vector<Shape *> &list, size_t startOffset);
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag) override;
    void renderList(std::vector<VDrawable *> &list) final;
    Object::Type type() const final { return Object::Type::Paint; }

protected:
    virtual bool updateContent(int frameNo, const VMatrix &matrix,
                               float alpha) = 0;

private:
    void updateRenderNode();

protected:
    std::vector<Shape *> mPathItems;
    Drawable             mDrawable;
    VPath                mPath;
    DirtyFlag            mFlag;
    bool                 mStaticContent;
    bool                 mRenderNodeUpdate{true};
    bool                 mContentToRender{true};
};

class Fill final : public Paint {
public:
    explicit Fill(model::Fill *data);

protected:
    bool updateContent(int frameNo, const VMatrix &matrix, float alpha) final;
    bool resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                        LOTVariant &value) final;

private:
    model::Filter<model::Fill> mModel;
};

class GradientFill final : public Paint {
public:
    explicit GradientFill(model::GradientFill *data);

protected:
    bool updateContent(int frameNo, const VMatrix &matrix, float alpha) final;

private:
    model::GradientFill *      mData{nullptr};
    std::unique_ptr<VGradient> mGradient;
};

class Stroke : public Paint {
public:
    explicit Stroke(model::Stroke *data);

protected:
    bool updateContent(int frameNo, const VMatrix &matrix, float alpha) final;
    bool resolveKeyPath(LOTKeyPath &keyPath, uint depth,
                        LOTVariant &value) final;

private:
    model::Filter<model::Stroke> mModel;
};

class GradientStroke final : public Paint {
public:
    explicit GradientStroke(model::GradientStroke *data);

protected:
    bool updateContent(int frameNo, const VMatrix &matrix, float alpha) final;

private:
    model::GradientStroke *    mData{nullptr};
    std::unique_ptr<VGradient> mGradient;
};

class Trim final : public Object {
public:
    explicit Trim(model::Trim *data) : mData(data) {}
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag) final;
    Object::Type type() const final { return Object::Type::Trim; }
    void         update();
    void         addPathItems(std::vector<Shape *> &list, size_t startOffset);

private:
    bool pathDirty() const
    {
        for (auto &i : mPathItems) {
            if (i->dirty()) return true;
        }
        return false;
    }
    struct Cache {
        int                  mFrameNo{-1};
        model::Trim::Segment mSegment{};
    };
    Cache                mCache;
    std::vector<Shape *> mPathItems;
    model::Trim *        mData{nullptr};
    VPathMesure          mPathMesure;
    bool                 mDirty{true};
};

class Repeater final : public Group {
public:
    explicit Repeater(model::Repeater *data, VArenaAlloc *allocator);
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha,
                const DirtyFlag &flag) final;
    void renderList(std::vector<VDrawable *> &list) final;

private:
    model::Repeater *mRepeaterData{nullptr};
    bool             mHidden{false};
    int              mCopies{0};
};

}  // namespace renderer

}  // namespace internal

}  // namespace rlottie

#endif  // LOTTIEITEM_H
