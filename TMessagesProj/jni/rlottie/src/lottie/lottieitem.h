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

#ifndef LOTTIEITEM_H
#define LOTTIEITEM_H

#include<sstream>
#include<memory>

#include"lottieproxymodel.h"
#include"vmatrix.h"
#include"vpath.h"
#include"vpoint.h"
#include"vpathmesure.h"
#include"rlottiecommon.h"
#include"rlottie.h"
#include"vpainter.h"
#include"vdrawable.h"
#include"lottiekeypath.h"

V_USE_NAMESPACE

enum class DirtyFlagBit : uchar
{
   None   = 0x00,
   Matrix = 0x01,
   Alpha  = 0x02,
   All    = (Matrix | Alpha)
};

class LOTLayerItem;
class LOTMaskItem;
class VDrawable;

class LOTDrawable : public VDrawable
{
public:
    void sync();
public:
    std::unique_ptr<LOTNode>  mCNode{nullptr};

    ~LOTDrawable() {
        if (mCNode && mCNode->mGradient.stopPtr)
          free(mCNode->mGradient.stopPtr);
    }
};

class LOTCompItem
{
public:
   explicit LOTCompItem(LOTModel *model);
   static std::unique_ptr<LOTLayerItem> createLayerItem(LOTLayerData *layerData);
   bool update(int frameNo);
   void resize(const VSize &size);
   VSize size() const;
   void buildRenderTree();
   const LOTLayerNode * renderTree()const;
   bool render(const rlottie::Surface &surface, bool clear);
   void setValue(const std::string &keypath, LOTVariant &value);
   void resetCurrentFrame();
private:
   VMatrix                                    mScaleMatrix;
   VSize                                      mViewSize;
   LOTCompositionData                         *mCompData;
   std::unique_ptr<LOTLayerItem>               mRootLayer;
   bool                                        mUpdateViewBox;
   int                                         mCurFrameNo;
   std::vector<LOTNode *>                      mRenderList;
   std::vector<VDrawable *>                    mDrawableList;
};

class LOTLayerMaskItem;

class LOTClipperItem
{
public:
    explicit LOTClipperItem(VSize size): mSize(size){}
    void update(const VMatrix &matrix);
    VRle rle();
public:
    VSize                    mSize;
    VPath                    mPath;
    VRasterizer              mRasterizer;
};

typedef vFlag<DirtyFlagBit> DirtyFlag;

class LOTLayerItem
{
public:
   virtual ~LOTLayerItem() = default;
   LOTLayerItem& operator=(LOTLayerItem&&) noexcept = delete;
   LOTLayerItem(LOTLayerData *layerData);
   int id() const {return mLayerData->id();}
   int parentId() const {return mLayerData->parentId();}
   LOTLayerItem *resolvedParentLayer() const {return mParentLayer;}
   void setParentLayer(LOTLayerItem *parent){mParentLayer = parent;}
   void setComplexContent(bool value) { mComplexContent = value;}
   bool complexContent() const {return mComplexContent;}
   virtual void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha);
   VMatrix matrix(int frameNo) const;
   virtual void renderList(std::vector<VDrawable *> &){}
   virtual void render(VPainter *painter, const VRle &mask, const VRle &matteRle);
   bool hasMatte() { if (mLayerData->mMatteType == MatteType::None) return false; return true; }
   MatteType matteType() const { return mLayerData->mMatteType;}
   bool visible() const;
   virtual void buildLayerNode();
   LOTLayerNode * layerNode() const {return mLayerCNode.get();}
   const std::string & name() const {return mLayerData->name();}
   virtual bool resolveKeyPath(LOTKeyPath &keyPath, uint depth, LOTVariant &value);
   VBitmap& bitmap() {return mRenderBuffer;}
protected:
   virtual void updateContent() = 0;
   inline VMatrix combinedMatrix() const {return mCombinedMatrix;}
   inline int frameNo() const {return mFrameNo;}
   inline float combinedAlpha() const {return mCombinedAlpha;}
   inline bool isStatic() const {return mLayerData->isStatic();}
   float opacity(int frameNo) const {return mLayerData->opacity(frameNo);}
   inline DirtyFlag flag() const {return mDirtyFlag;}
protected:
   std::vector<LOTMask>                        mMasksCNode;
   std::unique_ptr<LOTLayerNode>               mLayerCNode;
   std::vector<VDrawable *>                    mDrawableList;
   std::unique_ptr<LOTLayerMaskItem>           mLayerMask;
   LOTLayerData                               *mLayerData{nullptr};
   LOTLayerItem                               *mParentLayer{nullptr};
   VMatrix                                     mCombinedMatrix;
   VBitmap                                     mRenderBuffer;
   float                                       mCombinedAlpha{0.0};
   int                                         mFrameNo{-1};
   DirtyFlag                                   mDirtyFlag{DirtyFlagBit::All};
   bool                                        mComplexContent{false};
};

class LOTCompLayerItem: public LOTLayerItem
{
public:
   explicit LOTCompLayerItem(LOTLayerData *layerData);
   void renderList(std::vector<VDrawable *> &list)final;
   void render(VPainter *painter, const VRle &mask, const VRle &matteRle) final;
   void buildLayerNode() final;
   bool resolveKeyPath(LOTKeyPath &keyPath, uint depth, LOTVariant &value) override;
protected:
   void updateContent() final;
private:
    void renderHelper(VPainter *painter, const VRle &mask, const VRle &matteRle);
    void renderMatteLayer(VPainter *painter, const VRle &inheritMask, const VRle &matteRle,
                          LOTLayerItem *layer, LOTLayerItem *src);
private:
   std::vector<LOTLayerNode *>                  mLayersCNode;
   std::vector<std::unique_ptr<LOTLayerItem>>   mLayers;
   std::unique_ptr<LOTClipperItem>              mClipper;
};

class LOTSolidLayerItem: public LOTLayerItem
{
public:
   explicit LOTSolidLayerItem(LOTLayerData *layerData);
   void buildLayerNode() final;
protected:
   void updateContent() final;
   void renderList(std::vector<VDrawable *> &list) final;
private:
   std::vector<LOTNode *>       mCNodeList;
   LOTDrawable                  mRenderNode;
};

class LOTContentItem;
class LOTContentGroupItem;
class LOTShapeLayerItem: public LOTLayerItem
{
public:
   explicit LOTShapeLayerItem(LOTLayerData *layerData);
   static std::unique_ptr<LOTContentItem> createContentItem(LOTData *contentData);
   void renderList(std::vector<VDrawable *> &list)final;
   void buildLayerNode() final;
   bool resolveKeyPath(LOTKeyPath &keyPath, uint depth, LOTVariant &value) override;
protected:
   void updateContent() final;
   std::vector<LOTNode *>               mCNodeList;
   std::unique_ptr<LOTContentGroupItem> mRoot;
};

class LOTNullLayerItem: public LOTLayerItem
{
public:
   explicit LOTNullLayerItem(LOTLayerData *layerData);
protected:
   void updateContent() final;
};

class LOTImageLayerItem: public LOTLayerItem
{
public:
   explicit LOTImageLayerItem(LOTLayerData *layerData);
   void buildLayerNode() final;
protected:
   void updateContent() final;
   void renderList(std::vector<VDrawable *> &list) final;
private:
   std::vector<LOTNode *>       mCNodeList;
   LOTDrawable                  mRenderNode;
};

class LOTMaskItem
{
public:
    explicit LOTMaskItem(LOTMaskData *data): mData(data){}
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag);
    LOTMaskData::Mode maskMode() const { return mData->mMode;}
    VRle rle();
public:
    LOTMaskData             *mData;
    float                    mCombinedAlpha{0};
    VMatrix                  mCombinedMatrix;
    VPath                    mLocalPath;
    VPath                    mFinalPath;
    VRasterizer              mRasterizer;
    bool                     mRasterRequest{false};
};

/*
 * Handels mask property of a layer item
 */
class LOTLayerMaskItem
{
public:
    explicit LOTLayerMaskItem(LOTLayerData *layerData);
    void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag);
    bool isStatic() const {return mStatic;}
    VRle maskRle(const VRect &clipRect);
public:
    std::vector<LOTMaskItem>   mMasks;
    VRle                       mRle;
    bool                       mStatic{true};
    bool                       mDirty{true};
};

class LOTPathDataItem;
class LOTPaintDataItem;
class LOTTrimItem;

enum class ContentType
{
    Unknown,
    Group,
    Path,
    Paint,
    Trim
};

class LOTContentItem
{
public:
   virtual ~LOTContentItem() = default;
   LOTContentItem& operator=(LOTContentItem&&) noexcept = delete;
   LOTContentItem(ContentType type=ContentType::Unknown):mType(type) {}
   virtual void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag) = 0;
   virtual void renderList(std::vector<VDrawable *> &){}
   void setParent(LOTContentItem *parent) {mParent = parent;}
   LOTContentItem *parent() const {return mParent;}
   virtual bool resolveKeyPath(LOTKeyPath &, uint, LOTVariant &) {return false;}
   ContentType type() const {return mType;}
private:
   ContentType     mType{ContentType::Unknown};
   LOTContentItem *mParent{nullptr};
};

class LOTContentGroupItem: public LOTContentItem
{
public:
   explicit LOTContentGroupItem(LOTGroupData *data=nullptr);
   void addChildren(LOTGroupData *data);
   void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag) override;
   void applyTrim();
   void processTrimItems(std::vector<LOTPathDataItem *> &list);
   void processPaintItems(std::vector<LOTPathDataItem *> &list);
   void renderList(std::vector<VDrawable *> &list) override;
   const VMatrix & matrix() const { return mMatrix;}
   const std::string & name() const
   {
       static const std::string TAG = "__";
       return mData ? mData->name() : TAG;
   }
   bool resolveKeyPath(LOTKeyPath &keyPath, uint depth, LOTVariant &value) override;
protected:
   LOTGroupData                                  *mData{nullptr};
   std::vector<std::unique_ptr<LOTContentItem>>   mContents;
   VMatrix                                        mMatrix;
};

class LOTPathDataItem : public LOTContentItem
{
public:
   LOTPathDataItem(bool staticPath): LOTContentItem(ContentType::Path), mStaticPath(staticPath){}
   void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag) final;
   bool dirty() const {return mPathChanged;}
   const VPath &localPath() const {return mTemp;}
   const VPath &finalPath();
   void updatePath(const VPath &path) {mTemp = path; mPathChanged = true; mNeedUpdate = true;}
   bool staticPath() const { return mStaticPath; }
protected:
   virtual void updatePath(VPath& path, int frameNo) = 0;
   virtual bool hasChanged(int prevFrame, int curFrame) = 0;
private:
   bool hasChanged(int frameNo) {
       int prevFrame = mFrameNo;
       mFrameNo = frameNo;
       if (prevFrame == -1) return true;
       if (mStaticPath ||
           (prevFrame == frameNo)) return false;
       return hasChanged(prevFrame, frameNo);
   }
   VPath                                   mLocalPath;
   VPath                                   mTemp;
   VPath                                   mFinalPath;
   int                                     mFrameNo{-1};
   bool                                    mPathChanged{true};
   bool                                    mNeedUpdate{true};
   bool                                    mStaticPath;
};

class LOTRectItem: public LOTPathDataItem
{
public:
   explicit LOTRectItem(LOTRectData *data);
protected:
   void updatePath(VPath& path, int frameNo) final;
   LOTRectData           *mData;

   bool hasChanged(int prevFrame, int curFrame) final {
       return (mData->mPos.changed(prevFrame, curFrame) ||
               mData->mSize.changed(prevFrame, curFrame) ||
               mData->mRound.changed(prevFrame, curFrame)) ? true : false;
   }
};

class LOTEllipseItem: public LOTPathDataItem
{
public:
   explicit LOTEllipseItem(LOTEllipseData *data);
private:
   void updatePath(VPath& path, int frameNo) final;
   LOTEllipseData           *mData;
   bool hasChanged(int prevFrame, int curFrame) final {
       return (mData->mPos.changed(prevFrame, curFrame) ||
               mData->mSize.changed(prevFrame, curFrame)) ? true : false;
   }
};

class LOTShapeItem: public LOTPathDataItem
{
public:
   explicit LOTShapeItem(LOTShapeData *data);
private:
   void updatePath(VPath& path, int frameNo) final;
   LOTShapeData             *mData;
   bool hasChanged(int prevFrame, int curFrame) final {
       return mData->mShape.changed(prevFrame, curFrame);
   }
};

class LOTPolystarItem: public LOTPathDataItem
{
public:
   explicit LOTPolystarItem(LOTPolystarData *data);
private:
   void updatePath(VPath& path, int frameNo) final;
   LOTPolystarData             *mData;

   bool hasChanged(int prevFrame, int curFrame) final {
       return (mData->mPos.changed(prevFrame, curFrame) ||
               mData->mPointCount.changed(prevFrame, curFrame) ||
               mData->mInnerRadius.changed(prevFrame, curFrame) ||
               mData->mOuterRadius.changed(prevFrame, curFrame) ||
               mData->mInnerRoundness.changed(prevFrame, curFrame) ||
               mData->mOuterRoundness.changed(prevFrame, curFrame) ||
               mData->mRotation.changed(prevFrame, curFrame)) ? true : false;
   }
};



class LOTPaintDataItem : public LOTContentItem
{
public:
   LOTPaintDataItem(bool staticContent);
   void addPathItems(std::vector<LOTPathDataItem *> &list, int startOffset);
   void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag) override;
   void renderList(std::vector<VDrawable *> &list) final;
protected:
   virtual void updateContent(int frameNo) = 0;
   virtual void updateRenderNode();
   inline float parentAlpha() const {return mParentAlpha;}
protected:
   std::vector<LOTPathDataItem *>   mPathItems;
   LOTDrawable                      mDrawable;
   VPath                            mPath;
   float                            mParentAlpha{1.0f};
   int                              mFrameNo{-1};
   DirtyFlag                        mFlag;
   bool                             mStaticContent;
   bool                             mRenderNodeUpdate{true};
};

class LOTFillItem : public LOTPaintDataItem
{
public:
   explicit LOTFillItem(LOTFillData *data);
protected:
   void updateContent(int frameNo) final;
   void updateRenderNode() final;
   bool resolveKeyPath(LOTKeyPath &keyPath, uint depth, LOTVariant &value) final;
private:
   LOTProxyModel<LOTFillData> mModel;
   VColor                     mColor;
};

class LOTGFillItem : public LOTPaintDataItem
{
public:
   explicit LOTGFillItem(LOTGFillData *data);
protected:
   void updateContent(int frameNo) final;
   void updateRenderNode() final;
private:
   LOTGFillData                 *mData;
   std::unique_ptr<VGradient>    mGradient;
   float                         mAlpha{1.0};
   FillRule                      mFillRule{FillRule::Winding};
};

class LOTStrokeItem : public LOTPaintDataItem
{
public:
   explicit LOTStrokeItem(LOTStrokeData *data);
protected:
   void updateContent(int frameNo) final;
   void updateRenderNode() final;
   bool resolveKeyPath(LOTKeyPath &keyPath, uint depth, LOTVariant &value) final;
private:
   LOTProxyModel<LOTStrokeData> mModel;
   VColor                       mColor;
   float                        mWidth{0};
   float                        mDashArray[6];
   int                          mDashArraySize{0};
};

class LOTGStrokeItem : public LOTPaintDataItem
{
public:
   explicit LOTGStrokeItem(LOTGStrokeData *data);
protected:
   void updateContent(int frameNo) final;
   void updateRenderNode() final;
private:
   LOTGStrokeData               *mData;
   std::unique_ptr<VGradient>    mGradient;
   CapStyle                      mCap{CapStyle::Flat};
   JoinStyle                     mJoin{JoinStyle::Miter};
   float                         mMiterLimit{0};
   VColor                        mColor;
   float                         mAlpha{1.0};
   float                         mWidth{0};
   float                         mDashArray[6];
   int                           mDashArraySize{0};
};


// Trim Item

class LOTTrimItem : public LOTContentItem
{
public:
   LOTTrimItem(LOTTrimData *data);
   void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag) final;
   void update();
   void addPathItems(std::vector<LOTPathDataItem *> &list, int startOffset);
private:
   bool pathDirty() const {
       for (auto &i : mPathItems) {
           if (i->dirty())
               return true;
       }
       return false;
   }
   struct Cache {
        int                     mFrameNo{-1};
        LOTTrimData::Segment    mSegment{};
   };
   Cache                            mCache;
   std::vector<LOTPathDataItem *>   mPathItems;
   LOTTrimData                     *mData;
   bool                             mDirty{true};
};

class LOTRepeaterItem : public LOTContentGroupItem
{
public:
   explicit LOTRepeaterItem(LOTRepeaterData *data);
   void update(int frameNo, const VMatrix &parentMatrix, float parentAlpha, const DirtyFlag &flag) final;
   void renderList(std::vector<VDrawable *> &list) final;
private:
   LOTRepeaterData             *mRepeaterData;
   bool                         mHidden{false};
   int                          mCopies{0};
};


#endif // LOTTIEITEM_H


