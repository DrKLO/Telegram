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

#ifndef LOTModel_H
#define LOTModel_H

#include <algorithm>
#include <cmath>
#include <cstring>
#include <functional>
#include <memory>
#include <unordered_map>
#include <vector>
#include <map>
#include "varenaalloc.h"
#include "vbezier.h"
#include "vbrush.h"
#include "vinterpolator.h"
#include "vmatrix.h"
#include "vpath.h"
#include "vpoint.h"
#include "vrect.h"

V_USE_NAMESPACE

namespace rlottie {

namespace internal {

using Marker = std::tuple<std::string, int, int>;

using LayerInfo = Marker;

template <typename T>
inline T lerp(const T &start, const T &end, float t)
{
    return start + t * (end - start);
}

namespace model {

    enum class MatteType : uchar {
        None = 0, Alpha = 1, AlphaInv, Luma, LumaInv
    };

    enum class BlendMode : uchar {
        Normal = 0,
        Multiply = 1,
        Screen = 2,
        OverLay = 3
    };

    class Color {
    public:
        Color() = default;

        Color(float red, float green, float blue) : r(red), g(green), b(blue) {}

        VColor toColor(float a = 1) {
            float r1, g1, b1;
            getColorReplacement(colorMap, *this, r1, g1, b1);
            return VColor(uchar(255 * r1), uchar(255 * g1), uchar(255 * b1),
                          uchar(255 * a));
        }

        friend inline Color operator+(const Color &c1, const Color &c2);

        friend inline Color operator-(const Color &c1, const Color &c2);

        friend inline void
        getColorReplacement(std::map<int32_t, int32_t> *colorMap, const Color &c, float &r,
                            float &g, float &b);

    public:
        std::map<int32_t, int32_t> *colorMap{nullptr};
        float r{1};
        float g{1};
        float b{1};
    };

    inline void
    getColorReplacement(std::map<int32_t, int32_t> *colorMap, const Color &c, float &r, float &g,
                        float &b) {
        if (colorMap != nullptr && !colorMap->empty()) {
            int32_t rr = (int32_t) (c.r * 255);
            int32_t gg = (int32_t) (c.g * 255);
            int32_t bb = (int32_t) (c.b * 255);
            int32_t cc = (int32_t) (((bb & 0xff) << 16) | ((gg & 0xff) << 8) | (rr & 0xff));
            std::map<int32_t, int32_t>::iterator iter = colorMap->find(cc);
            if (iter != colorMap->end()) {
                cc = iter->second;
                r = ((cc) & 0xff) / 255.0f;
                g = ((cc >> 8) & 0xff) / 255.0f;
                b = ((cc >> 16) & 0xff) / 255.0f;
                return;
            }
        }
        r = c.r;
        g = c.g;
        b = c.b;
    }

inline Color operator-(const Color &c1, const Color &c2)
{
    float r1, g1, b1;
    float r2, g2, b2;
    getColorReplacement(c1.colorMap, c1, r1, g1, b1);
    getColorReplacement(c2.colorMap, c2, r2, g2, b2);
    return Color(r1 - r2, g1 - g2, b1 - b2);
}
inline Color operator+(const Color &c1, const Color &c2)
{
    float r1, g1, b1;
    float r2, g2, b2;
    getColorReplacement(c1.colorMap, c1, r1, g1, b1);
    getColorReplacement(c2.colorMap, c2, r2, g2, b2);
    return Color(r1 + r2, g1 + g2, b1 + b2);
}

inline const Color operator*(const Color &c, float m)
{
    float r1, g1, b1;
    getColorReplacement(c.colorMap, c, r1, g1, b1);
    return Color(r1 * m, g1 * m, b1 * m);
}

inline const Color operator*(float m, const Color &c)
{
    float r1, g1, b1;
    getColorReplacement(c.colorMap, c, r1, g1, b1);
    return Color(r1 * m, g1 * m, b1 * m);
}

struct PathData {
    std::vector<VPointF> mPoints;
    bool                 mClosed = false; /* "c" */
    void        reserve(size_t size) { mPoints.reserve(mPoints.size() + size); }
    static void lerp(const PathData &start, const PathData &end, float t,
                     VPath &result)
    {
        result.reset();
        // test for empty animation data.
        if (start.mPoints.empty() || end.mPoints.empty())
        {
            return;
        }
        auto size = std::min(start.mPoints.size(), end.mPoints.size());
        /* reserve exact memory requirement at once
         * ptSize = size + 1(size + close)
         * elmSize = size/3 cubic + 1 move + 1 close
         */
        result.reserve(size + 1, size / 3 + 2);
        result.moveTo(start.mPoints[0] +
                      t * (end.mPoints[0] - start.mPoints[0]));
        for (size_t i = 1; i < size; i += 3) {
            result.cubicTo(
                start.mPoints[i] + t * (end.mPoints[i] - start.mPoints[i]),
                start.mPoints[i + 1] +
                    t * (end.mPoints[i + 1] - start.mPoints[i + 1]),
                start.mPoints[i + 2] +
                    t * (end.mPoints[i + 2] - start.mPoints[i + 2]));
        }
        if (start.mClosed) result.close();
    }
    void toPath(VPath &path) const
    {
        path.reset();

        if (mPoints.empty()) return;

        auto size = mPoints.size();
        auto points = mPoints.data();
        /* reserve exact memory requirement at once
         * ptSize = size + 1(size + close)
         * elmSize = size/3 cubic + 1 move + 1 close
         */
        path.reserve(size + 1, size / 3 + 2);
        path.moveTo(points[0]);
        for (size_t i = 1; i < size; i += 3) {
            path.cubicTo(points[i], points[i + 1], points[i + 2]);
        }
        if (mClosed) path.close();
    }
};

template <typename T, typename Tag = void>
struct Value {
    T     start_;
    T     end_;
    T     at(float t) const { return lerp(start_, end_, t); }
    float angle(float) const { return 0; }
    void  cache() {}
};

struct Position;

template <typename T>
struct Value<T, Position> {
    T     start_;
    T     end_;
    T     inTangent_;
    T     outTangent_;
    float length_{0};
    bool  hasTangent_{false};

    void cache()
    {
        if (hasTangent_) {
            inTangent_ = end_ + inTangent_;
            outTangent_ = start_ + outTangent_;
            length_ = VBezier::fromPoints(start_, outTangent_, inTangent_, end_)
                          .length();
            if (vIsZero(length_)) {
                // this segment has zero length.
                // so disable expensive path computaion.
                hasTangent_ = false;
            }
        }
    }

    T at(float t) const
    {
        if (hasTangent_) {
            /*
             * position along the path calcualated
             * using bezier at progress length (t * bezlen)
             */
            VBezier b =
                VBezier::fromPoints(start_, outTangent_, inTangent_, end_);
            return b.pointAt(b.tAtLength(t * length_, length_));
        }
        return lerp(start_, end_, t);
    }

    float angle(float t) const
    {
        if (hasTangent_) {
            VBezier b =
                VBezier::fromPoints(start_, outTangent_, inTangent_, end_);
            return b.angleAt(b.tAtLength(t * length_, length_));
        }
        return 0;
    }
};

template <typename T, typename Tag>
class KeyFrames {
public:
    struct Frame {
        float progress(int frameNo) const
        {
            return interpolator_ ? interpolator_->value((frameNo - start_) /
                                                        (end_ - start_))
                                 : 0;
        }
        T     value(int frameNo) const { return value_.at(progress(frameNo)); }
        float angle(int frameNo) const
        {
            return value_.angle(progress(frameNo));
        }

        float          start_{0};
        float          end_{0};
        VInterpolator *interpolator_{nullptr};
        Value<T, Tag>  value_;
    };

    T value(int frameNo) const
    {
        if (frames_.front().start_ >= frameNo)
            return frames_.front().value_.start_;
        if (frames_.back().end_ <= frameNo) return frames_.back().value_.end_;

        for (const auto &keyFrame : frames_) {
            if (frameNo >= keyFrame.start_ && frameNo < keyFrame.end_)
                return keyFrame.value(frameNo);
        }
        return {};
    }

    float angle(int frameNo) const
    {
        if ((frames_.front().start_ >= frameNo) ||
            (frames_.back().end_ <= frameNo))
            return 0;

        for (const auto &frame : frames_) {
            if (frameNo >= frame.start_ && frameNo < frame.end_)
                return frame.angle(frameNo);
        }
        return 0;
    }

    bool changed(int prevFrame, int curFrame) const
    {
        auto first = frames_.front().start_;
        auto last = frames_.back().end_;

        return !((first > prevFrame && first > curFrame) ||
                 (last < prevFrame && last < curFrame));
    }
    void cache()
    {
        for (auto &e : frames_) e.value_.cache();
    }

public:
    std::vector<Frame> frames_;
};

template <typename T, typename Tag = void>
class Property {
public:
    using Animation = KeyFrames<T, Tag>;

    Property() { construct(impl_.value_, {}); }
    explicit Property(T value) { construct(impl_.value_, std::move(value)); }

    const Animation &animation() const { return *(impl_.animation_.get()); }
    const T &        value() const { return impl_.value_; }

    Animation &animation()
    {
        if (isValue_) {
            destroy();
            construct(impl_.animation_, std::make_unique<Animation>());
            isValue_ = false;
        }
        return *(impl_.animation_.get());
    }

    T &value()
    {
        assert(isValue_);
        return impl_.value_;
    }

    Property(Property &&other) noexcept
    {
        if (!other.isValue_) {
            construct(impl_.animation_, std::move(other.impl_.animation_));
            isValue_ = false;
        } else {
            construct(impl_.value_, std::move(other.impl_.value_));
            isValue_ = true;
        }
    }
    // delete special member functions
    Property(const Property &) = delete;
    Property &operator=(const Property &) = delete;
    Property &operator=(Property &&) = delete;

    ~Property() { destroy(); }

    bool isStatic() const { return isValue_; }

    T value(int frameNo) const
    {
        return isStatic() ? value() : animation().value(frameNo);
    }

    // special function only for type T=PathData
    template <typename forT = PathData>
    auto value(int frameNo, VPath &path) const ->
        typename std::enable_if_t<std::is_same<T, forT>::value, void>
    {
        if (isStatic()) {
            value().toPath(path);
        } else {
            const auto &vec = animation().frames_;
            if (vec.front().start_ >= frameNo)
                return vec.front().value_.start_.toPath(path);
            if (vec.back().end_ <= frameNo)
                return vec.back().value_.end_.toPath(path);

            for (const auto &keyFrame : vec) {
                if (frameNo >= keyFrame.start_ && frameNo < keyFrame.end_) {
                    T::lerp(keyFrame.value_.start_, keyFrame.value_.end_,
                            keyFrame.progress(frameNo), path);
                }
            }
        }
    }

    float angle(int frameNo) const
    {
        return isStatic() ? 0 : animation().angle(frameNo);
    }

    bool changed(int prevFrame, int curFrame) const
    {
        return isStatic() ? false : animation().changed(prevFrame, curFrame);
    }
    void cache()
    {
        if (!isStatic()) animation().cache();
    }

private:
    template <typename Tp>
    void construct(Tp &member, Tp &&val)
    {
        new (&member) Tp(std::move(val));
    }

    void destroy()
    {
        if (isValue_) {
            impl_.value_.~T();
        } else {
            using std::unique_ptr;
            impl_.animation_.~unique_ptr<Animation>();
        }
    }
    union details {
        std::unique_ptr<Animation> animation_;
        T                          value_;
        details(){};
        details(const details &) = delete;
        details(details &&) = delete;
        details &operator=(details &&) = delete;
        details &operator=(const details &) = delete;
        ~details() noexcept {};
    } impl_;
    bool isValue_{true};
};

class Path;
struct PathData;
struct Dash {
    std::vector<Property<float>> mData;
    bool                         empty() const { return mData.empty(); }
    size_t                       size() const { return mData.size(); }
    bool                         isStatic() const
    {
        for (const auto &elm : mData)
            if (!elm.isStatic()) return false;
        return true;
    }
    void getDashInfo(int frameNo, std::vector<float> &result) const;
};

class Mask {
public:
    enum class Mode { None, Add, Substarct, Intersect, Difference };
    float opacity(int frameNo) const
    {
        return mOpacity.value(frameNo) / 100.0f;
    }
    bool isStatic() const { return mIsStatic; }

public:
    Property<PathData> mShape;
    Property<float>    mOpacity{100};
    bool               mInv{false};
    bool               mIsStatic{true};
    Mask::Mode         mMode;
};

class Object {
public:
    enum class Type : unsigned char {
        Composition = 1,
        Layer,
        Group,
        Transform,
        Fill,
        Stroke,
        GFill,
        GStroke,
        Rect,
        Ellipse,
        Path,
        Polystar,
        Trim,
        Repeater,
        RoundedCorner
    };

    explicit Object(Object::Type type) : mPtr(nullptr)
    {
        mData._type = type;
        mData._static = true;
        mData._shortString = true;
        mData._hidden = false;
    }
    ~Object() noexcept
    {
        if (!shortString() && mPtr) free(mPtr);
    }
    Object(const Object &) = delete;
    Object &operator=(const Object &) = delete;

    void         setStatic(bool value) { mData._static = value; }
    bool         isStatic() const { return mData._static; }
    bool         hidden() const { return mData._hidden; }
    void         setHidden(bool value) { mData._hidden = value; }
    void         setType(Object::Type type) { mData._type = type; }
    Object::Type type() const { return mData._type; }
    void         setName(const char *name)
    {
        if (name) {
            auto len = strlen(name);
            if (len < maxShortStringLength) {
                setShortString(true);
                strncpy(mData._buffer, name, len + 1);
            } else {
                setShortString(false);
                mPtr = strdup(name);
            }
        }
    }
    const char *name() const { return shortString() ? mData._buffer : mPtr; }

private:
    static constexpr unsigned char maxShortStringLength = 14;
    void setShortString(bool value) { mData._shortString = value; }
    bool shortString() const { return mData._shortString; }
    struct Data {
        char         _buffer[maxShortStringLength];
        Object::Type _type;
        bool         _static : 1;
        bool         _hidden : 1;
        bool         _shortString : 1;
    };
    union {
        Data  mData;
        char *mPtr{nullptr};
    };
};

struct Asset {
    enum class Type : unsigned char { Precomp, Image, Char };
    bool                  isStatic() const { return mStatic; }
    void                  setStatic(bool value) { mStatic = value; }
    VBitmap               bitmap() const { return mBitmap; }
    void                  loadImageData(std::string data);
    void                  loadImagePath(std::string Path);
    Type                  mAssetType{Type::Precomp};
    bool                  mStatic{true};
    std::string           mRefId;  // ref id
    std::vector<Object *> mLayers;
    // image asset data
    int     mWidth{0};
    int     mHeight{0};
    VBitmap mBitmap;
};

class Layer;

class Composition : public Object {
public:
    Composition() : Object(Object::Type::Composition) {}
    std::vector<LayerInfo>     layerInfoList() const;
    const std::vector<Marker> &markers() const { return mMarkers; }
    double                     duration() const
    {
        return frameDuration() / frameRate();  // in second
    }
    size_t frameAtPos(double pos) const
    {
        if (pos < 0) pos = 0;
        if (pos > 1) pos = 1;
        return size_t(round(pos * frameDuration()));
    }
    long frameAtTime(double timeInSec) const
    {
        return long(frameAtPos(timeInSec / duration()));
    }
    size_t totalFrame() const { return mEndFrame - mStartFrame; }
    long   frameDuration() const { return mEndFrame - mStartFrame - 1; }
    float  frameRate() const { return mFrameRate; }
    size_t startFrame() const { return mStartFrame; }
    size_t endFrame() const { return mEndFrame; }
    VSize  size() const { return mSize; }
    void   processRepeaterObjects();
    void   updateStats();

public:
    struct Stats {
        uint16_t precompLayerCount{0};
        uint16_t solidLayerCount{0};
        uint16_t shapeLayerCount{0};
        uint16_t imageLayerCount{0};
        uint16_t nullLayerCount{0};
    };

public:
    std::string                              mVersion;
    VSize                                    mSize;
    long                                     mStartFrame{0};
    long                                     mEndFrame{0};
    float                                    mFrameRate{60};
    BlendMode                                mBlendMode{BlendMode::Normal};
    Layer *                                  mRootLayer{nullptr};
    std::unordered_map<std::string, Asset *> mAssets;

    std::vector<Marker> mMarkers;
    VArenaAlloc         mArenaAlloc{2048};
    Stats               mStats;
};

class Transform : public Object {
public:
    struct Data {
        struct Extra {
            Property<float> m3DRx{0};
            Property<float> m3DRy{0};
            Property<float> m3DRz{0};
            Property<float> mSeparateX{0};
            Property<float> mSeparateY{0};
            bool            mSeparate{false};
            bool            m3DData{false};
        };
        VMatrix matrix(int frameNo, bool autoOrient = false) const;
        float   opacity(int frameNo) const
        {
            return mOpacity.value(frameNo) / 100.0f;
        }
        void createExtraData()
        {
            if (!mExtra) mExtra = std::make_unique<Extra>();
        }
        Property<float>             mRotation{0};       /* "r" */
        Property<VPointF>           mScale{{100, 100}}; /* "s" */
        Property<VPointF, Position> mPosition;          /* "p" */
        Property<VPointF>           mAnchor;            /* "a" */
        Property<float>             mOpacity{100};      /* "o" */
        std::unique_ptr<Extra>      mExtra;
    };

    Transform() : Object(Object::Type::Transform) {}
    void set(Transform::Data *data, bool staticFlag)
    {
        setStatic(staticFlag);
        if (isStatic()) {
            new (&impl.mStaticData)
                StaticData(data->matrix(0), data->opacity(0));
        } else {
            impl.mData = data;
        }
    }
    VMatrix matrix(int frameNo, bool autoOrient = false) const
    {
        if (isStatic()) return impl.mStaticData.mMatrix;
        return impl.mData->matrix(frameNo, autoOrient);
    }
    float opacity(int frameNo) const
    {
        if (isStatic()) return impl.mStaticData.mOpacity;
        return impl.mData->opacity(frameNo);
    }
    Transform(const Transform &) = delete;
    Transform(Transform &&) = delete;
    Transform &operator=(Transform &) = delete;
    Transform &operator=(Transform &&) = delete;
    ~Transform() noexcept { destroy(); }

private:
    void destroy()
    {
        if (isStatic()) {
            impl.mStaticData.~StaticData();
        }
    }
    struct StaticData {
        StaticData(VMatrix &&m, float opacity)
            : mOpacity(opacity), mMatrix(std::move(m))
        {
        }
        float   mOpacity;
        VMatrix mMatrix;
    };
    union details {
        Data *     mData{nullptr};
        StaticData mStaticData;
        details(){};
        details(const details &) = delete;
        details(details &&) = delete;
        details &operator=(details &&) = delete;
        details &operator=(const details &) = delete;
        ~details() noexcept {};
    } impl;
};

class Group : public Object {
public:
    Group() : Object(Object::Type::Group) {}
    explicit Group(Object::Type type) : Object(type) {}

public:
    std::vector<Object *> mChildren;
    Transform *           mTransform{nullptr};
};

class Layer : public Group {
public:
    enum class Type : uchar {
        Precomp = 0,
        Solid = 1,
        Image = 2,
        Null = 3,
        Shape = 4,
        Text = 5
    };
    Layer() : Group(Object::Type::Layer) {}
    bool    hasRoundedCorner() const noexcept { return mHasRoundedCorner; }
    bool    hasPathOperator() const noexcept { return mHasPathOperator; }
    bool    hasGradient() const noexcept { return mHasGradient; }
    bool    hasMask() const noexcept { return mHasMask; }
    bool    hasRepeater() const noexcept { return mHasRepeater; }
    int     id() const noexcept { return mId; }
    int     parentId() const noexcept { return mParentId; }
    bool    hasParent() const noexcept { return mParentId != -1; }
    int     inFrame() const noexcept { return mInFrame; }
    int     outFrame() const noexcept { return mOutFrame; }
    int     startFrame() const noexcept { return mStartFrame; }
    Color   solidColor() const noexcept { return mExtra->mSolidColor; }
    bool    autoOrient() const noexcept { return mAutoOrient; }
    int     timeRemap(int frameNo) const;
    VSize   layerSize() const { return mLayerSize; }
    bool    precompLayer() const { return mLayerType == Type::Precomp; }
    VMatrix matrix(int frameNo) const
    {
        return mTransform ? mTransform->matrix(frameNo, autoOrient())
                          : VMatrix{};
    }
    float opacity(int frameNo) const
    {
        return mTransform ? mTransform->opacity(frameNo) : 1.0f;
    }
    Asset *asset() const
    {
        return (mExtra && mExtra->mAsset) ? mExtra->mAsset : nullptr;
    }
    struct Extra {
        Color               mSolidColor;
        std::string         mPreCompRefId;
        Property<float>     mTimeRemap; /* "tm" */
        Composition *       mCompRef{nullptr};
        Asset *             mAsset{nullptr};
        std::vector<Mask *> mMasks;
    };

    Layer::Extra *extra()
    {
        if (!mExtra) mExtra = std::make_unique<Layer::Extra>();
        return mExtra.get();
    }

public:
    MatteType mMatteType{MatteType::None};
    Type      mLayerType{Layer::Type::Null};
    BlendMode mBlendMode{BlendMode::Normal};
    bool      mHasRoundedCorner{false};
    bool      mHasPathOperator{false};
    bool      mHasMask{false};
    bool      mHasRepeater{false};
    bool      mHasGradient{false};
    bool      mAutoOrient{false};
    VSize     mLayerSize;
    int       mParentId{-1};  // Lottie the id of the parent in the composition
    int       mId{-1};        // Lottie the group id  used for parenting.
    float     mTimeStreatch{1.0f};
    int       mInFrame{0};
    int       mOutFrame{0};
    int       mStartFrame{0};
    std::unique_ptr<Extra> mExtra{nullptr};
};

/**
 * TimeRemap has the value in time domain(in sec)
 * To get the proper mapping first we get the mapped time at the current frame
 * Number then we need to convert mapped time to frame number using the
 * composition time line Ex: at frame 10 the mappend time is 0.5(500 ms) which
 * will be convert to frame number 30 if the frame rate is 60. or will result to
 * frame number 15 if the frame rate is 30.
 */
inline int Layer::timeRemap(int frameNo) const
{
    /*
     * only consider startFrame() when there is no timeRemap.
     * when a layer has timeremap bodymovin updates the startFrame()
     * of all child layer so we don't have to take care of it.
     */
    if (!mExtra || mExtra->mTimeRemap.isStatic())
        frameNo = frameNo - startFrame();
    else
        frameNo =
            mExtra->mCompRef->frameAtTime(mExtra->mTimeRemap.value(frameNo));
    /* Apply time streatch if it has any.
     * Time streatch is just a factor by which the animation will speedup or
     * slow down with respect to the overal animation. Time streach factor is
     * already applied to the layers inFrame and outFrame.
     * @TODO need to find out if timestreatch also affects the in and out frame
     * of the child layers or not. */
    return int(frameNo / mTimeStreatch);
}

class Stroke : public Object {
public:
    Stroke() : Object(Object::Type::Stroke) {}
    Color color(int frameNo) const { return mColor.value(frameNo); }
    float opacity(int frameNo) const
    {
        return mOpacity.value(frameNo) / 100.0f;
    }
    float     strokeWidth(int frameNo) const { return mWidth.value(frameNo); }
    CapStyle  capStyle() const { return mCapStyle; }
    JoinStyle joinStyle() const { return mJoinStyle; }
    float     miterLimit() const { return mMiterLimit; }
    bool      hasDashInfo() const { return !mDash.empty(); }
    void      getDashInfo(int frameNo, std::vector<float> &result) const
    {
        return mDash.getDashInfo(frameNo, result);
    }

public:
    Property<Color> mColor;                       /* "c" */
    Property<float> mOpacity{100};                /* "o" */
    Property<float> mWidth{0};                    /* "w" */
    CapStyle        mCapStyle{CapStyle::Flat};    /* "lc" */
    JoinStyle       mJoinStyle{JoinStyle::Miter}; /* "lj" */
    float           mMiterLimit{0};               /* "ml" */
    Dash            mDash;
    bool            mEnabled{true}; /* "fillEnabled" */
};

class Gradient : public Object {
public:
    class Data {
    public:
        friend inline Gradient::Data operator+(const Gradient::Data &g1,
                                               const Gradient::Data &g2);
        friend inline Gradient::Data operator-(const Gradient::Data &g1,
                                               const Gradient::Data &g2);
        friend inline Gradient::Data operator*(float                 m,
                                               const Gradient::Data &g);

    public:
        std::vector<float> mGradient;
    };
    explicit Gradient(Object::Type type) : Object(type) {}
    inline float opacity(int frameNo) const
    {
        return mOpacity.value(frameNo) / 100.0f;
    }
    void update(std::unique_ptr<VGradient> &grad, int frameNo);

private:
    void populate(VGradientStops &stops, int frameNo);

public:
    int                      mGradientType{1};    /* "t" Linear=1 , Radial = 2*/
    Property<VPointF>        mStartPoint;         /* "s" */
    Property<VPointF>        mEndPoint;           /* "e" */
    Property<float>          mHighlightLength{0}; /* "h" */
    Property<float>          mHighlightAngle{0};  /* "a" */
    Property<float>          mOpacity{100};       /* "o" */
    Property<Gradient::Data> mGradient;           /* "g" */
    int                      mColorPoints{-1};
    bool                     mEnabled{true}; /* "fillEnabled" */
};

class GradientStroke : public Gradient {
public:
    GradientStroke() : Gradient(Object::Type::GStroke) {}
    float     width(int frameNo) const { return mWidth.value(frameNo); }
    CapStyle  capStyle() const { return mCapStyle; }
    JoinStyle joinStyle() const { return mJoinStyle; }
    float     miterLimit() const { return mMiterLimit; }
    bool      hasDashInfo() const { return !mDash.empty(); }
    void      getDashInfo(int frameNo, std::vector<float> &result) const
    {
        return mDash.getDashInfo(frameNo, result);
    }

public:
    Property<float> mWidth;                       /* "w" */
    CapStyle        mCapStyle{CapStyle::Flat};    /* "lc" */
    JoinStyle       mJoinStyle{JoinStyle::Miter}; /* "lj" */
    float           mMiterLimit{0};               /* "ml" */
    Dash            mDash;
};

class GradientFill : public Gradient {
public:
    GradientFill() : Gradient(Object::Type::GFill) {}
    FillRule fillRule() const { return mFillRule; }

public:
    FillRule mFillRule{FillRule::Winding}; /* "r" */
};

class Fill : public Object {
public:
    Fill() : Object(Object::Type::Fill) {}
    Color color(int frameNo) const { return mColor.value(frameNo); }
    float opacity(int frameNo) const
    {
        return mOpacity.value(frameNo) / 100.0f;
    }
    FillRule fillRule() const { return mFillRule; }

public:
    FillRule        mFillRule{FillRule::Winding}; /* "r" */
    bool            mEnabled{true};               /* "fillEnabled" */
    Property<Color> mColor;                       /* "c" */
    Property<float> mOpacity{100};                /* "o" */
};

class Shape : public Object {
public:
    explicit Shape(Object::Type type) : Object(type) {}
    VPath::Direction direction()
    {
        return (mDirection == 3) ? VPath::Direction::CCW : VPath::Direction::CW;
    }

public:
    int mDirection{1};
};

class Path : public Shape {
public:
    Path() : Shape(Object::Type::Path) {}

public:
    Property<PathData> mShape;
};

class RoundedCorner : public Object {
public:
    RoundedCorner() : Object(Object::Type::RoundedCorner) {}
    float radius(int frameNo) const { return mRadius.value(frameNo);}
public:
    Property<float>   mRadius{0};
};

class Rect : public Shape {
public:
    Rect() : Shape(Object::Type::Rect) {}
    float roundness(int frameNo)
    {
        return mRoundedCorner ? mRoundedCorner->radius(frameNo) :
                                mRound.value(frameNo);
    }

    bool roundnessChanged(int prevFrame, int curFrame)
    {
        return mRoundedCorner ? mRoundedCorner->mRadius.changed(prevFrame, curFrame) :
                        mRound.changed(prevFrame, curFrame);
    }
public:
    RoundedCorner*    mRoundedCorner{nullptr};
    Property<VPointF> mPos;
    Property<VPointF> mSize;
    Property<float>   mRound{0};
};

class Ellipse : public Shape {
public:
    Ellipse() : Shape(Object::Type::Ellipse) {}

public:
    Property<VPointF> mPos;
    Property<VPointF> mSize;
};

class Polystar : public Shape {
public:
    enum class PolyType { Star = 1, Polygon = 2 };
    Polystar() : Shape(Object::Type::Polystar) {}

public:
    Polystar::PolyType mPolyType{PolyType::Polygon};
    Property<VPointF>  mPos;
    Property<float>    mPointCount{0};
    Property<float>    mInnerRadius{0};
    Property<float>    mOuterRadius{0};
    Property<float>    mInnerRoundness{0};
    Property<float>    mOuterRoundness{0};
    Property<float>    mRotation{0};
};

class Repeater : public Object {
public:
    struct Transform {
        VMatrix matrix(int frameNo, float multiplier) const;
        float   startOpacity(int frameNo) const
        {
            return mStartOpacity.value(frameNo) / 100;
        }
        float endOpacity(int frameNo) const
        {
            return mEndOpacity.value(frameNo) / 100;
        }
        bool isStatic() const
        {
            return mRotation.isStatic() && mScale.isStatic() &&
                   mPosition.isStatic() && mAnchor.isStatic() &&
                   mStartOpacity.isStatic() && mEndOpacity.isStatic();
        }
        Property<float>   mRotation{0};       /* "r" */
        Property<VPointF> mScale{{100, 100}}; /* "s" */
        Property<VPointF> mPosition;          /* "p" */
        Property<VPointF> mAnchor;            /* "a" */
        Property<float>   mStartOpacity{100}; /* "so" */
        Property<float>   mEndOpacity{100};   /* "eo" */
    };
    Repeater() : Object(Object::Type::Repeater) {}
    Group *content() const { return mContent ? mContent : nullptr; }
    void   setContent(Group *content) { mContent = content; }
    int    maxCopies() const { return int(mMaxCopies); }
    float  copies(int frameNo) const { return mCopies.value(frameNo); }
    float  offset(int frameNo) const { return mOffset.value(frameNo); }
    bool   processed() const { return mProcessed; }
    void   markProcessed() { mProcessed = true; }

public:
    Group *         mContent{nullptr};
    Transform       mTransform;
    Property<float> mCopies{0};
    Property<float> mOffset{0};
    float           mMaxCopies{0.0};
    bool            mProcessed{false};
};

class Trim : public Object {
public:
    struct Segment {
        float start{0};
        float end{0};
        Segment() = default;
        explicit Segment(float s, float e) : start(s), end(e) {}
    };
    enum class TrimType { Simultaneously, Individually };
    Trim() : Object(Object::Type::Trim) {}
    /*
     * if start > end vector trims the path as a loop ( 2 segment)
     * if start < end vector trims the path without loop ( 1 segment).
     * if no offset then there is no loop.
     */
    Segment segment(int frameNo) const
    {
        float start = mStart.value(frameNo) / 100.0f;
        float end = mEnd.value(frameNo) / 100.0f;
        float offset = std::fmod(mOffset.value(frameNo), 360.0f) / 360.0f;

        float diff = std::abs(start - end);
        if (vCompare(diff, 0.0f)) return Segment(0, 0);
        if (vCompare(diff, 1.0f)) return Segment(0, 1);

        if (offset > 0) {
            start += offset;
            end += offset;
            if (start <= 1 && end <= 1) {
                return noloop(start, end);
            } else if (start > 1 && end > 1) {
                return noloop(start - 1, end - 1);
            } else {
                return (start > 1) ? loop(start - 1, end)
                                   : loop(start, end - 1);
            }
        } else {
            start += offset;
            end += offset;
            if (start >= 0 && end >= 0) {
                return noloop(start, end);
            } else if (start < 0 && end < 0) {
                return noloop(1 + start, 1 + end);
            } else {
                return (start < 0) ? loop(1 + start, end)
                                   : loop(start, 1 + end);
            }
        }
    }
    Trim::TrimType type() const { return mTrimType; }

private:
    Segment noloop(float start, float end) const
    {
        assert(start >= 0);
        assert(end >= 0);
        Segment s;
        s.start = std::min(start, end);
        s.end = std::max(start, end);
        return s;
    }
    Segment loop(float start, float end) const
    {
        assert(start >= 0);
        assert(end >= 0);
        Segment s;
        s.start = std::max(start, end);
        s.end = std::min(start, end);
        return s;
    }

public:
    Property<float> mStart{0};
    Property<float> mEnd{0};
    Property<float> mOffset{0};
    Trim::TrimType  mTrimType{TrimType::Simultaneously};
};

inline Gradient::Data operator+(const Gradient::Data &g1,
                                const Gradient::Data &g2)
{
    if (g1.mGradient.size() != g2.mGradient.size()) return g1;

    Gradient::Data newG;
    newG.mGradient = g1.mGradient;

    auto g2It = g2.mGradient.begin();
    for (auto &i : newG.mGradient) {
        i = i + *g2It;
        g2It++;
    }

    return newG;
}

inline Gradient::Data operator-(const Gradient::Data &g1,
                                const Gradient::Data &g2)
{
    if (g1.mGradient.size() != g2.mGradient.size()) return g1;
    Gradient::Data newG;
    newG.mGradient = g1.mGradient;

    auto g2It = g2.mGradient.begin();
    for (auto &i : newG.mGradient) {
        i = i - *g2It;
        g2It++;
    }

    return newG;
}

inline Gradient::Data operator*(float m, const Gradient::Data &g)
{
    Gradient::Data newG;
    newG.mGradient = g.mGradient;

    for (auto &i : newG.mGradient) {
        i = i * m;
    }
    return newG;
}

using ColorFilter = std::function<void(float &, float &, float &)>;

std::shared_ptr<model::Composition> loadFromFile(const std::string &filePath, std::map<int32_t, int32_t> *colorReplacement);

std::shared_ptr<model::Composition> loadFromData(std::string &&jsonData, const std::string &key, std::map<int32_t, int32_t> *colorReplacement, const std::string &resourcePath);

std::shared_ptr<model::Composition> parse(char *str, std::string dir_path,
                                          std::map<int32_t, int32_t> *colorReplacement);

}  // namespace model

}  // namespace internal

}  // namespace rlottie

#endif  // LOTModel_H
