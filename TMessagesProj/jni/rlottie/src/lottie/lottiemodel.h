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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#ifndef LOTModel_H
#define LOTModel_H

#include<vector>
#include<memory>
#include<unordered_map>
#include <algorithm>
#include"vpoint.h"
#include"vrect.h"
#include"vinterpolator.h"
#include"vmatrix.h"
#include"vbezier.h"
#include"vbrush.h"
#include"vpath.h"

V_USE_NAMESPACE

class LOTCompositionData;
class LOTLayerData;
class LOTTransformData;
class LOTShapeGroupData;
class LOTShapeData;
class LOTRectData;
class LOTEllipseData;
class LOTTrimData;
class LOTRepeaterData;
class LOTFillData;
class LOTStrokeData;
class LOTGroupData;
class LOTGFillData;
class LOTGStrokeData;
class LottieShapeData;
class LOTPolystarData;
class LOTMaskData;

enum class MatteType
{
    None = 0,
    Alpha = 1,
    AlphaInv,
    Luma,
    LumaInv
};

enum class LayerType {
    Precomp = 0,
    Solid = 1,
    Image = 2,
    Null = 3,
    Shape = 4,
    Text = 5
};

class LottieColor
{
public:
    LottieColor() = default;
    LottieColor(float red, float green , float blue):r(red), g(green),b(blue){}
    VColor toColor(float a=1){ return VColor((255 * r), (255 * g), (255 * b), (255 * a));}
    friend inline LottieColor operator+(const LottieColor &c1, const LottieColor &c2);
    friend inline LottieColor operator-(const LottieColor &c1, const LottieColor &c2);
public:
    float r{1};
    float g{1};
    float b{1};
};

inline LottieColor operator-(const LottieColor &c1, const LottieColor &c2)
{
    return LottieColor(c1.r - c2.r, c1.g - c2.g, c1.b - c2.b);
}
inline LottieColor operator+(const LottieColor &c1, const LottieColor &c2)
{
    return LottieColor(c1.r + c2.r, c1.g + c2.g, c1.b + c2.b);
}

inline const LottieColor operator*(const LottieColor &c, float m)
{ return LottieColor(c.r*m, c.g*m, c.b*m); }

inline const LottieColor operator*(float m, const LottieColor &c)
{ return LottieColor(c.r*m, c.g*m, c.b*m); }

class LottieShapeData
{
public:
    void reserve(int size) {
        mPoints.reserve(mPoints.size() + size);
    }
    void toPath(VPath& path) {
        path.reset();

        if (mPoints.empty()) return;

        int size = mPoints.size();
        const VPointF *points = mPoints.data();
        /* reserve exact memory requirement at once
         * ptSize = size + 1(size + close)
         * elmSize = size/3 cubic + 1 move + 1 close
         */
        path.reserve(size + 1 , size/3 + 2);
        path.moveTo(points[0]);
        for (int i = 1 ; i < size; i+=3) {
           path.cubicTo(points[i], points[i+1], points[i+2]);
        }
        if (mClosed)
          path.close();
    }
public:
    std::vector<VPointF>    mPoints;
    bool                     mClosed = false;   /* "c" */
};



template<typename T>
inline T lerp(const T& start, const T& end, float t)
{
    return start + t * (end - start);
}

inline LottieShapeData lerp(const LottieShapeData& start, const LottieShapeData& end, float t)
{
    // Usal case both start and end path has same size
    // In case its different then truncate the larger path and do the interpolation.
    LottieShapeData result;
    auto size = std::min(start.mPoints.size(), end.mPoints.size());
    result.reserve(size);
    for (unsigned int i = 0 ; i < size; i++) {
       result.mPoints.push_back(start.mPoints[i] + t * (end.mPoints[i] - start.mPoints[i]));
    }
   return result;
}

template <typename T>
struct LOTKeyFrameValue
{
    T mStartValue;
    T mEndValue;
    T value(float t) const {
        return lerp(mStartValue, mEndValue, t);
    }
    float angle(float ) const { return 0;}
};

template <>
struct LOTKeyFrameValue<VPointF>
{
    VPointF mStartValue;
    VPointF mEndValue;
    VPointF mInTangent;
    VPointF mOutTangent;
    bool    mPathKeyFrame = false;

    VPointF value(float t) const {
        if (mPathKeyFrame) {
            /*
             * position along the path calcualated
             * using bezier at progress length (t * bezlen)
             */
            VBezier b = VBezier::fromPoints(mStartValue, mStartValue + mOutTangent,
                                       mEndValue + mInTangent, mEndValue);
            return b.pointAt(b.tAtLength(t * b.length()));

        } else {
            return lerp(mStartValue, mEndValue, t);
        }
    }

    float angle(float t) const {
        if (mPathKeyFrame) {
            VBezier b = VBezier::fromPoints(mStartValue, mStartValue + mOutTangent,
                                       mEndValue + mInTangent, mEndValue);
            return b.angleAt(b.tAtLength(t * b.length()));
        }
        return 0;
    }
};


template<typename T>
class LOTKeyFrame
{
public:
    float progress(int frameNo) const {
        return mInterpolator ? mInterpolator->value((frameNo - mStartFrame) / (mEndFrame - mStartFrame)) : 0;
    }
    T value(int frameNo) const {
        return mValue.value(progress(frameNo));
    }
    float angle(int frameNo) const {
        return mValue.angle(progress(frameNo));
    }

public:
    float                 mStartFrame{0};
    float                 mEndFrame{0};
    std::shared_ptr<VInterpolator> mInterpolator;
    LOTKeyFrameValue<T>  mValue;
};

template<typename T>
class LOTAnimInfo
{
public:
    T value(int frameNo) const {
        if (mKeyFrames.front().mStartFrame >= frameNo)
            return mKeyFrames.front().mValue.mStartValue;
        if(mKeyFrames.back().mEndFrame <= frameNo)
            return mKeyFrames.back().mValue.mEndValue;

        for(const auto &keyFrame : mKeyFrames) {
            if (frameNo >= keyFrame.mStartFrame && frameNo < keyFrame.mEndFrame)
                return keyFrame.value(frameNo);
        }
        return T();
    }

    float angle(int frameNo) const {
        if ((mKeyFrames.front().mStartFrame >= frameNo) ||
            (mKeyFrames.back().mEndFrame <= frameNo) )
            return 0;

        for(const auto &keyFrame : mKeyFrames) {
            if (frameNo >= keyFrame.mStartFrame && frameNo < keyFrame.mEndFrame)
                return keyFrame.angle(frameNo);
        }
        return 0;
    }

    bool changed(int prevFrame, int curFrame) const {
        int first = mKeyFrames.front().mStartFrame;
        int last = mKeyFrames.back().mEndFrame;

        if ((first > prevFrame  && first > curFrame) ||
            (last < prevFrame  && last < curFrame)) {
            return false;
        }

        return true;
    }

public:
    std::vector<LOTKeyFrame<T>>    mKeyFrames;
};

template<typename T>
class LOTAnimatable
{
public:
    LOTAnimatable() { construct(impl.mValue, {}); }
    explicit LOTAnimatable(T value) { construct(impl.mValue, std::move(value)); }

    const LOTAnimInfo<T>& animation() const {return *(impl.mAnimInfo.get());}
    const T& value() const {return impl.mValue;}

    LOTAnimInfo<T>& animation()
    {
        if (mStatic) {
            destroy();
            construct(impl.mAnimInfo, std::make_unique<LOTAnimInfo<T>>());
            mStatic = false;
        }
        return *(impl.mAnimInfo.get());
    }

    T& value()
    {
        assert(mStatic);
        return impl.mValue;
    }

    // delete special member functions
    LOTAnimatable(const LOTAnimatable &) = delete;
    LOTAnimatable(LOTAnimatable &&) = delete;
    LOTAnimatable& operator=(const LOTAnimatable&) = delete;
    LOTAnimatable& operator=(LOTAnimatable&&) = delete;

    ~LOTAnimatable() {destroy();}

    bool isStatic() const {return mStatic;}

    T value(int frameNo) const {
        return isStatic() ? value() : animation().value(frameNo);
    }

    float angle(int frameNo) const {
        return isStatic() ? 0 : animation().angle(frameNo);
    }

    bool changed(int prevFrame, int curFrame) const {
        return isStatic() ? false : animation().changed(prevFrame, curFrame);
    }
private:
    template <typename Tp>
    void construct(Tp& member, Tp&& val)
    {
        new (&member) Tp(std::move(val));
    }

    void destroy() {
        if (mStatic) {
            impl.mValue.~T();
        } else {
            using std::unique_ptr;
            impl.mAnimInfo.~unique_ptr<LOTAnimInfo<T>>();
        }
    }
    union details {
        std::unique_ptr<LOTAnimInfo<T>>   mAnimInfo;
        T                                 mValue;
        details(){}
        ~details(){}
    }impl;
    bool                                 mStatic{true};
};

enum class LottieBlendMode
{
    Normal = 0,
    Multiply = 1,
    Screen = 2,
    OverLay = 3
};

class LOTDataVisitor;
class LOTData
{
public:
    enum class Type :short {
        Composition = 1,
        Layer,
        ShapeGroup,
        Transform,
        Fill,
        Stroke,
        GFill,
        GStroke,
        Rect,
        Ellipse,
        Shape,
        Polystar,
        Trim,
        Repeater
    };
    explicit LOTData(LOTData::Type  type): mType(type){}
    inline LOTData::Type type() const {return mType;}
    bool isStatic() const{return mStatic;}
    void setStatic(bool value) {mStatic = value;}
    bool hidden() const {return mHidden;}
    const std::string& name() const{ return mName;}
public:
    std::string               mName;
    bool                      mStatic{true};
    bool                      mHidden{false};
    LOTData::Type             mType;
};

class LOTGroupData: public LOTData
{
public:
    explicit LOTGroupData(LOTData::Type  type):LOTData(type){}
public:
    std::vector<std::shared_ptr<LOTData>>  mChildren;
    std::shared_ptr<LOTTransformData>      mTransform;
};

class LOTShapeGroupData : public LOTGroupData
{
public:
    LOTShapeGroupData():LOTGroupData(LOTData::Type::ShapeGroup){}
};

class LOTLayerData;
struct LOTAsset
{
    enum class Type : unsigned char{
        Precomp,
        Image,
        Char
    };
    bool isStatic() const {return mStatic;}
    void setStatic(bool value) {mStatic = value;}
    VBitmap  bitmap() const {return mBitmap;}
    void loadImageData(std::string data);
    void loadImagePath(std::string Path);
    Type                                      mAssetType{Type::Precomp};
    bool                                      mStatic{true};
    std::string                               mRefId; // ref id
    std::vector<std::shared_ptr<LOTData>>     mLayers;
    // image asset data
    int                                       mWidth{0};
    int                                       mHeight{0};
    VBitmap                                   mBitmap;
};

struct LOT3DData
{
    LOTAnimatable<float>     mRx{0};
    LOTAnimatable<float>     mRy{0};
    LOTAnimatable<float>     mRz{0};
};

struct TransformData
{
    VMatrix matrix(int frameNo, bool autoOrient = false) const;
    float opacity(int frameNo) const { return mOpacity.value(frameNo)/100.0f; }
    bool isStatic() const { return mStatic;}
    std::unique_ptr<LOT3DData>    m3D;
    LOTAnimatable<float>          mRotation{0};  /* "r" */
    LOTAnimatable<VPointF>        mScale{{100, 100}};     /* "s" */
    LOTAnimatable<VPointF>        mPosition;  /* "p" */
    LOTAnimatable<float>          mX{0};
    LOTAnimatable<float>          mY{0};
    LOTAnimatable<VPointF>        mAnchor;    /* "a" */
    LOTAnimatable<float>          mOpacity{100};   /* "o" */
    bool                          mSeparate{false};
    bool                          mStatic{false};
};

class LOTTransformData : public LOTData
{
public:
    LOTTransformData():LOTData(LOTData::Type::Transform){}
    void set(std::unique_ptr<TransformData> data)
    {
        setStatic(data->isStatic());
        if (isStatic()) {
            new (&impl.mStaticData) static_data(data->matrix(0), data->opacity(0));
        } else {
            new (&impl.mData) std::unique_ptr<TransformData>(std::move(data));
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

    LOTTransformData& operator=(LOTTransformData&&) = delete;
    ~LOTTransformData() {destroy();}

private:
    void destroy() {
        if (isStatic()) {
            impl.mStaticData.~static_data();
        } else {
            using std::unique_ptr;
            impl.mData.~unique_ptr<TransformData>();
        }
    }
    struct static_data {
       static_data(VMatrix &&m, float opacity):
           mOpacity(opacity), mMatrix(std::move(m)){}
       float    mOpacity;
       VMatrix  mMatrix;
    };
    union details {
        std::unique_ptr<TransformData>   mData;
        static_data                      mStaticData;
        details(){}
        ~details(){}
    }impl;
};

class LOTLayerData : public LOTGroupData
{
public:
    LOTLayerData():LOTGroupData(LOTData::Type::Layer){}
    bool hasPathOperator() const noexcept {return mHasPathOperator;}
    bool hasGradient() const noexcept {return mHasGradient;}
    bool hasMask() const noexcept {return mHasMask;}
    bool hasRepeater() const noexcept {return mHasRepeater;}
    int id() const noexcept{ return mId;}
    int parentId() const noexcept{ return mParentId;}
    int inFrame() const noexcept{return mInFrame;}
    int outFrame() const noexcept{return mOutFrame;}
    int startFrame() const noexcept{return mStartFrame;}
    int solidWidth() const noexcept{return mSolidLayer.mWidth;}
    int solidHeight() const noexcept{return mSolidLayer.mHeight;}
    LottieColor solidColor() const noexcept{return mSolidLayer.mColor;}
    bool autoOrient() const noexcept{return mAutoOrient;}
    int timeRemap(int frameNo) const;
    VSize layerSize() const {return mLayerSize;}
    bool precompLayer() const {return mLayerType == LayerType::Precomp;}
    VMatrix matrix(int frameNo) const
    {
        return mTransform ? mTransform->matrix(frameNo, autoOrient()) : VMatrix{};
    }
    float opacity(int frameNo) const
    {
        return mTransform ? mTransform->opacity(frameNo) : 1.0f;
    }
public:
    struct SolidLayer {
        int            mWidth{0};
        int            mHeight{0};
        LottieColor    mColor;
    };

    MatteType            mMatteType{MatteType::None};
    LayerType            mLayerType{LayerType::Null}; //lottie layer type  (solid/shape/precomp)
    int                  mParentId{-1}; // Lottie the id of the parent in the composition
    int                  mId{-1};  // Lottie the group id  used for parenting.
    long                 mInFrame{0};
    long                 mOutFrame{0};
    long                 mStartFrame{0};
    VSize                mLayerSize;
    LottieBlendMode      mBlendMode{LottieBlendMode::Normal};
    float                mTimeStreatch{1.0f};
    std::string          mPreCompRefId;
    LOTAnimatable<float> mTimeRemap;  /* "tm" */
    SolidLayer           mSolidLayer;
    bool                 mHasPathOperator{false};
    bool                 mHasMask{false};
    bool                 mHasRepeater{false};
    bool                 mHasGradient{false};
    bool                 mAutoOrient{false};
    std::vector<std::shared_ptr<LOTMaskData>>  mMasks;
    LOTCompositionData   *mCompRef{nullptr};
    std::shared_ptr<LOTAsset> mAsset;
};

using LayerInfo = std::tuple<std::string, int , int>;

class LOTCompositionData : public LOTData
{
public:
    LOTCompositionData():LOTData(LOTData::Type::Composition){}
    const std::vector<LayerInfo> &layerInfoList() const { return  mLayerInfoList;}
    double duration() const {
        return frameDuration() / frameRate(); // in second
    }
    size_t frameAtPos(double pos) const {
        if (pos < 0) pos = 0;
        if (pos > 1) pos = 1;
        return pos * frameDuration();
    }
    long frameAtTime(double timeInSec) const {
        return frameAtPos(timeInSec / duration());
    }
    size_t totalFrame() const {return mEndFrame - mStartFrame;}
    long frameDuration() const {return mEndFrame - mStartFrame -1;}
    float frameRate() const {return mFrameRate;}
    long startFrame() const {return mStartFrame;}
    long endFrame() const {return mEndFrame;}
    VSize size() const {return mSize;}
    void processRepeaterObjects();
public:
    std::string          mVersion;
    VSize                mSize;
    long                 mStartFrame{0};
    long                 mEndFrame{0};
    float                mFrameRate{60};
    LottieBlendMode      mBlendMode{LottieBlendMode::Normal};
    std::shared_ptr<LOTLayerData> mRootLayer;
    std::unordered_map<std::string,
                       std::shared_ptr<LOTAsset>>    mAssets;

    std::vector<LayerInfo>  mLayerInfoList;

};

/**
 * TimeRemap has the value in time domain(in sec)
 * To get the proper mapping first we get the mapped time at the current frame Number
 * then we need to convert mapped time to frame number using the composition time line
 * Ex: at frame 10 the mappend time is 0.5(500 ms) which will be convert to frame number
 * 30 if the frame rate is 60. or will result to frame number 15 if the frame rate is 30.
 */
inline int LOTLayerData::timeRemap(int frameNo) const
{
    /*
     * only consider startFrame() when there is no timeRemap.
     * when a layer has timeremap bodymovin updates the startFrame()
     * of all child layer so we don't have to take care of it.
     */
    frameNo = mTimeRemap.isStatic() ? frameNo - startFrame():
              mCompRef->frameAtTime(mTimeRemap.value(frameNo));
    /* Apply time streatch if it has any.
     * Time streatch is just a factor by which the animation will speedup or slow
     * down with respect to the overal animation.
     * Time streach factor is already applied to the layers inFrame and outFrame.
     * @TODO need to find out if timestreatch also affects the in and out frame of the
     * child layers or not. */
    return frameNo / mTimeStreatch;
}

class LOTFillData : public LOTData
{
public:
    LOTFillData():LOTData(LOTData::Type::Fill){}
    LottieColor color(int frameNo) const {return mColor.value(frameNo);}
    float opacity(int frameNo) const {return mOpacity.value(frameNo)/100.0f;}
    FillRule fillRule() const {return mFillRule;}
public:
    FillRule                       mFillRule{FillRule::Winding}; /* "r" */
    LOTAnimatable<LottieColor>     mColor;   /* "c" */
    LOTAnimatable<float>           mOpacity{100};  /* "o" */
    bool                           mEnabled{true}; /* "fillEnabled" */
};

struct LOTDashProperty
{
    LOTAnimatable<float>     mDashArray[5]; /* "d" "g" "o"*/
    int                      mDashCount{0};
    bool                     mStatic{true};
};

class LOTStrokeData : public LOTData
{
public:
    LOTStrokeData():LOTData(LOTData::Type::Stroke){}
    LottieColor color(int frameNo) const {return mColor.value(frameNo);}
    float opacity(int frameNo) const {return mOpacity.value(frameNo)/100.0f;}
    float strokeWidth(int frameNo) const {return mWidth.value(frameNo);}
    CapStyle capStyle() const {return mCapStyle;}
    JoinStyle joinStyle() const {return mJoinStyle;}
    float meterLimit() const{return mMeterLimit;}
    bool hasDashInfo() const { return !(mDash.mDashCount == 0);}
    int getDashInfo(int frameNo, float *array) const;
public:
    LOTAnimatable<LottieColor>        mColor;      /* "c" */
    LOTAnimatable<float>              mOpacity{100};    /* "o" */
    LOTAnimatable<float>              mWidth{0};      /* "w" */
    CapStyle                          mCapStyle{CapStyle::Flat};   /* "lc" */
    JoinStyle                         mJoinStyle{JoinStyle::Miter};  /* "lj" */
    float                             mMeterLimit{0}; /* "ml" */
    LOTDashProperty                   mDash;
    bool                              mEnabled{true}; /* "fillEnabled" */
};

class LottieGradient
{
public:
    friend inline LottieGradient operator+(const LottieGradient &g1, const LottieGradient &g2);
    friend inline LottieGradient operator-(const LottieGradient &g1, const LottieGradient &g2);
    friend inline LottieGradient operator*(float m, const LottieGradient &g);
public:
    std::vector<float>    mGradient;
};

inline LottieGradient operator+(const LottieGradient &g1, const LottieGradient &g2)
{
    if (g1.mGradient.size() != g2.mGradient.size())
        return g1;

    LottieGradient newG;
    newG.mGradient = g1.mGradient;

    auto g2It = g2.mGradient.begin();
    for(auto &i : newG.mGradient) {
        i = i + *g2It;
        g2It++;
    }

    return newG;
}

inline LottieGradient operator-(const LottieGradient &g1, const LottieGradient &g2)
{
    if (g1.mGradient.size() != g2.mGradient.size())
        return g1;
    LottieGradient newG;
    newG.mGradient = g1.mGradient;

    auto g2It = g2.mGradient.begin();
    for(auto &i : newG.mGradient) {
        i = i - *g2It;
        g2It++;
    }

    return newG;
}

inline LottieGradient operator*(float m, const LottieGradient &g)
{
    LottieGradient newG;
    newG.mGradient = g.mGradient;

    for(auto &i : newG.mGradient) {
        i = i * m;
    }
    return newG;
}



class LOTGradient : public LOTData
{
public:
    explicit LOTGradient(LOTData::Type  type):LOTData(type){}
    inline float opacity(int frameNo) const {return mOpacity.value(frameNo)/100.0;}
    void update(std::unique_ptr<VGradient> &grad, int frameNo);

private:
    void populate(VGradientStops &stops, int frameNo);
public:
    int                                 mGradientType{1};        /* "t" Linear=1 , Radial = 2*/
    LOTAnimatable<VPointF>              mStartPoint;          /* "s" */
    LOTAnimatable<VPointF>              mEndPoint;            /* "e" */
    LOTAnimatable<float>                mHighlightLength{0};     /* "h" */
    LOTAnimatable<float>                mHighlightAngle{0};      /* "a" */
    LOTAnimatable<float>                mOpacity{100};             /* "o" */
    LOTAnimatable<LottieGradient>       mGradient;            /* "g" */
    int                                 mColorPoints{-1};
    bool                                mEnabled{true};      /* "fillEnabled" */
};

class LOTGFillData : public LOTGradient
{
public:
    LOTGFillData():LOTGradient(LOTData::Type::GFill){}
    FillRule fillRule() const {return mFillRule;}
public:
    FillRule                       mFillRule{FillRule::Winding}; /* "r" */
};

class LOTGStrokeData : public LOTGradient
{
public:
    LOTGStrokeData():LOTGradient(LOTData::Type::GStroke){}
    float width(int frameNo) const {return mWidth.value(frameNo);}
    CapStyle capStyle() const {return mCapStyle;}
    JoinStyle joinStyle() const {return mJoinStyle;}
    float meterLimit() const{return mMeterLimit;}
    bool hasDashInfo() const { return !(mDash.mDashCount == 0);}
    int getDashInfo(int frameNo, float *array) const;
public:
    LOTAnimatable<float>           mWidth;       /* "w" */
    CapStyle                       mCapStyle{CapStyle::Flat};    /* "lc" */
    JoinStyle                      mJoinStyle{JoinStyle::Miter};   /* "lj" */
    float                          mMeterLimit{0};  /* "ml" */
    LOTDashProperty                mDash;
};

class LOTPath : public LOTData
{
public:
    explicit LOTPath(LOTData::Type  type):LOTData(type){}
    VPath::Direction direction() { if (mDirection == 3) return VPath::Direction::CCW;
                                   else return VPath::Direction::CW;}
public:
    int                                    mDirection{1};
};

class LOTShapeData : public LOTPath
{
public:
    LOTShapeData():LOTPath(LOTData::Type::Shape){}
    void process();
public:
    LOTAnimatable<LottieShapeData>    mShape;
};

class LOTMaskData
{
public:
    enum class Mode {
      None,
      Add,
      Substarct,
      Intersect,
      Difference
    };
    float opacity(int frameNo) const {return mOpacity.value(frameNo)/100.0f;}
    bool isStatic() const {return mIsStatic;}
public:
    LOTAnimatable<LottieShapeData>    mShape;
    LOTAnimatable<float>              mOpacity{100};
    bool                              mInv{false};
    bool                              mIsStatic{true};
    LOTMaskData::Mode                 mMode;
};

class LOTRectData : public LOTPath
{
public:
    LOTRectData():LOTPath(LOTData::Type::Rect){}
public:
    LOTAnimatable<VPointF>    mPos;
    LOTAnimatable<VPointF>    mSize;
    LOTAnimatable<float>      mRound{0};
};

class LOTEllipseData : public LOTPath
{
public:
    LOTEllipseData():LOTPath(LOTData::Type::Ellipse){}
public:
    LOTAnimatable<VPointF>   mPos;
    LOTAnimatable<VPointF>   mSize;
};

class LOTPolystarData : public LOTPath
{
public:
    enum class PolyType {
        Star = 1,
        Polygon = 2
    };
    LOTPolystarData():LOTPath(LOTData::Type::Polystar){}
public:
    LOTPolystarData::PolyType     mType{PolyType::Polygon};
    LOTAnimatable<VPointF>        mPos;
    LOTAnimatable<float>          mPointCount{0};
    LOTAnimatable<float>          mInnerRadius{0};
    LOTAnimatable<float>          mOuterRadius{0};
    LOTAnimatable<float>          mInnerRoundness{0};
    LOTAnimatable<float>          mOuterRoundness{0};
    LOTAnimatable<float>          mRotation{0};
};

class LOTTrimData : public LOTData
{
public:
    struct Segment {
        float start{0};
        float end{0};
        Segment() {}
        Segment(float s, float e):start(s), end(e) {}
    };
    enum class TrimType {
        Simultaneously,
        Individually
    };
    LOTTrimData():LOTData(LOTData::Type::Trim){}
    /*
     * if start > end vector trims the path as a loop ( 2 segment)
     * if start < end vector trims the path without loop ( 1 segment).
     * if no offset then there is no loop.
     */
    Segment segment(int frameNo) const {
        float start = mStart.value(frameNo)/100.0f;
        float end = mEnd.value(frameNo)/100.0f;
        float offset = fmod(mOffset.value(frameNo), 360.0f)/ 360.0f;

        float diff = fabs(start - end);
        if (vCompare(diff, 0.0f)) return Segment(0, 0);
        if (vCompare(diff, 1.0f)) return Segment(0, 1);

        if (offset > 0) {
            start += offset;
            end += offset;
            if (start <= 1 && end <=1) {
                return noloop(start, end);
            } else if (start > 1 && end > 1) {
                return noloop(start - 1, end - 1);
            } else {
                if (start > 1) return loop(start - 1 , end);
                else return loop(start , end - 1);
            }
        } else {
            start += offset;
            end   += offset;
            if (start >= 0 && end >= 0) {
                return noloop(start, end);
            } else if (start < 0 && end < 0) {
                return noloop(1 + start, 1 + end);
            } else {
                if (start < 0) return loop(1 + start, end);
                else return loop(start , 1 + end);
            }
        }
    }
    LOTTrimData::TrimType type() const {return mTrimType;}
private:
    Segment noloop(float start, float end) const{
        assert(start >= 0);
        assert(end >= 0);
        Segment s;
        s.start = std::min(start, end);
        s.end = std::max(start, end);
        return s;
    }
    Segment loop(float start, float end) const{
        assert(start >= 0);
        assert(end >= 0);
        Segment s;
        s.start = std::max(start, end);
        s.end = std::min(start, end);
        return s;
    }
public:
    LOTAnimatable<float>             mStart{0};
    LOTAnimatable<float>             mEnd{0};
    LOTAnimatable<float>             mOffset{0};
    LOTTrimData::TrimType            mTrimType{TrimType::Simultaneously};
};

class LOTRepeaterTransform
{
public:
    VMatrix matrix(int frameNo, float multiplier) const;
    float startOpacity(int frameNo) const { return mStartOpacity.value(frameNo)/100;}
    float endOpacity(int frameNo) const { return mEndOpacity.value(frameNo)/100;}
    bool isStatic() const
    {
        return mRotation.isStatic() &&
               mScale.isStatic() &&
               mPosition.isStatic() &&
               mAnchor.isStatic() &&
               mStartOpacity.isStatic() &&
               mEndOpacity.isStatic();
    }
public:
    LOTAnimatable<float>          mRotation{0};  /* "r" */
    LOTAnimatable<VPointF>        mScale{{100, 100}};     /* "s" */
    LOTAnimatable<VPointF>        mPosition;  /* "p" */
    LOTAnimatable<VPointF>        mAnchor;    /* "a" */
    LOTAnimatable<float>          mStartOpacity{100}; /* "so" */
    LOTAnimatable<float>          mEndOpacity{100};   /* "eo" */
};

class LOTRepeaterData : public LOTData
{
public:
    LOTRepeaterData():LOTData(LOTData::Type::Repeater){}
    LOTShapeGroupData *content() const { return mContent ? mContent.get() : nullptr; }
    void setContent(std::shared_ptr<LOTShapeGroupData> content) {mContent = std::move(content);}
    int maxCopies() const { return int(mMaxCopies);}
    float copies(int frameNo) const {return mCopies.value(frameNo);}
    float offset(int frameNo) const {return mOffset.value(frameNo);}
public:
    std::shared_ptr<LOTShapeGroupData>      mContent{nullptr};
    LOTRepeaterTransform                    mTransform;
    LOTAnimatable<float>                    mCopies{0};
    LOTAnimatable<float>                    mOffset{0};
    float                                   mMaxCopies{0.0};
};

class LOTModel
{
public:
   bool  isStatic() const {return mRoot->isStatic();}
   double duration() const {return mRoot->duration();}
   size_t totalFrame() const {return mRoot->totalFrame();}
   size_t frameDuration() const {return mRoot->frameDuration();}
   size_t frameRate() const {return mRoot->frameRate();}
   size_t startFrame() const {return mRoot->startFrame();}
   size_t endFrame() const {return mRoot->endFrame();}
   size_t frameAtPos(double pos) const {return mRoot->frameAtPos(pos);}
   const std::vector<LayerInfo> &layerInfoList() const { return mRoot->layerInfoList();}
public:
    std::shared_ptr<LOTCompositionData> mRoot;
};

#endif // LOTModel_H
