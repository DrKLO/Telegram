/*
 * Implements LottieItem functions needed
 * to support renderTree() api.
 * Moving all those implementation to its own
 * file make clear separation as well easy of
 * maintenance.
 */

#include "lottieitem.h"
#include "vdasher.h"

using namespace rlottie::internal;

renderer::CApiData::CApiData()
{
    mLayer.mMaskList.ptr = nullptr;
    mLayer.mMaskList.size = 0;
    mLayer.mLayerList.ptr = nullptr;
    mLayer.mLayerList.size = 0;
    mLayer.mNodeList.ptr = nullptr;
    mLayer.mNodeList.size = 0;
    mLayer.mMatte = MatteNone;
    mLayer.mVisible = 0;
    mLayer.mAlpha = 255;
    mLayer.mClipPath.ptPtr = nullptr;
    mLayer.mClipPath.elmPtr = nullptr;
    mLayer.mClipPath.ptCount = 0;
    mLayer.mClipPath.elmCount = 0;
    mLayer.keypath = nullptr;
}

void renderer::Composition::buildRenderTree()
{
    mRootLayer->buildLayerNode();
}

const LOTLayerNode *renderer::Composition::renderTree() const
{
    return &mRootLayer->clayer();
}

void renderer::CompLayer::buildLayerNode()
{
    renderer::Layer::buildLayerNode();
    if (mClipper) {
        const auto &elm = mClipper->mPath.elements();
        const auto &pts = mClipper->mPath.points();
        auto        ptPtr = reinterpret_cast<const float *>(pts.data());
        auto        elmPtr = reinterpret_cast<const char *>(elm.data());
        clayer().mClipPath.ptPtr = ptPtr;
        clayer().mClipPath.elmPtr = elmPtr;
        clayer().mClipPath.ptCount = 2 * pts.size();
        clayer().mClipPath.elmCount = elm.size();
    }
    if (mLayers.size() != clayers().size()) {
        for (const auto &layer : mLayers) {
            layer->buildLayerNode();
            clayers().push_back(&layer->clayer());
        }
        clayer().mLayerList.ptr = clayers().data();
        clayer().mLayerList.size = clayers().size();
    } else {
        for (const auto &layer : mLayers) {
            layer->buildLayerNode();
        }
    }
}

void renderer::ShapeLayer::buildLayerNode()
{
    renderer::Layer::buildLayerNode();

    auto renderlist = renderList();

    cnodes().clear();
    for (auto &i : renderlist) {
        auto lotDrawable = static_cast<renderer::Drawable *>(i);
        lotDrawable->sync();
        cnodes().push_back(lotDrawable->mCNode.get());
    }
    clayer().mNodeList.ptr = cnodes().data();
    clayer().mNodeList.size = cnodes().size();
}

void renderer::Layer::buildLayerNode()
{
    if (!mCApiData) {
        mCApiData = std::make_unique<renderer::CApiData>();
        clayer().keypath = name();
    }
    if (complexContent()) clayer().mAlpha = uchar(combinedAlpha() * 255.f);
    clayer().mVisible = visible();
    // update matte
    if (hasMatte()) {
        switch (mLayerData->mMatteType) {
        case model::MatteType::Alpha:
            clayer().mMatte = MatteAlpha;
            break;
        case model::MatteType::AlphaInv:
            clayer().mMatte = MatteAlphaInv;
            break;
        case model::MatteType::Luma:
            clayer().mMatte = MatteLuma;
            break;
        case model::MatteType::LumaInv:
            clayer().mMatte = MatteLumaInv;
            break;
        default:
            clayer().mMatte = MatteNone;
            break;
        }
    }
    if (mLayerMask) {
        cmasks().clear();
        cmasks().resize(mLayerMask->mMasks.size());
        size_t i = 0;
        for (const auto &mask : mLayerMask->mMasks) {
            auto &      cNode = cmasks()[i++];
            const auto &elm = mask.mFinalPath.elements();
            const auto &pts = mask.mFinalPath.points();
            auto        ptPtr = reinterpret_cast<const float *>(pts.data());
            auto        elmPtr = reinterpret_cast<const char *>(elm.data());
            cNode.mPath.ptPtr = ptPtr;
            cNode.mPath.ptCount = pts.size();
            cNode.mPath.elmPtr = elmPtr;
            cNode.mPath.elmCount = elm.size();
            cNode.mAlpha = uchar(mask.mCombinedAlpha * 255.0f);
            switch (mask.maskMode()) {
            case model::Mask::Mode::Add:
                cNode.mMode = MaskAdd;
                break;
            case model::Mask::Mode::Substarct:
                cNode.mMode = MaskSubstract;
                break;
            case model::Mask::Mode::Intersect:
                cNode.mMode = MaskIntersect;
                break;
            case model::Mask::Mode::Difference:
                cNode.mMode = MaskDifference;
                break;
            default:
                cNode.mMode = MaskAdd;
                break;
            }
        }
        clayer().mMaskList.ptr = cmasks().data();
        clayer().mMaskList.size = cmasks().size();
    }
}

void renderer::SolidLayer::buildLayerNode()
{
    renderer::Layer::buildLayerNode();

    auto renderlist = renderList();

    cnodes().clear();
    for (auto &i : renderlist) {
        auto lotDrawable = static_cast<renderer::Drawable *>(i);
        lotDrawable->sync();
        cnodes().push_back(lotDrawable->mCNode.get());
    }
    clayer().mNodeList.ptr = cnodes().data();
    clayer().mNodeList.size = cnodes().size();
}

void renderer::ImageLayer::buildLayerNode()
{
    renderer::Layer::buildLayerNode();

    auto renderlist = renderList();

    cnodes().clear();
    for (auto &i : renderlist) {
        auto lotDrawable = static_cast<renderer::Drawable *>(i);
        lotDrawable->sync();

        lotDrawable->mCNode->mImageInfo.data =
            lotDrawable->mBrush.mTexture->mBitmap.data();
        lotDrawable->mCNode->mImageInfo.width =
            int(lotDrawable->mBrush.mTexture->mBitmap.width());
        lotDrawable->mCNode->mImageInfo.height =
            int(lotDrawable->mBrush.mTexture->mBitmap.height());

        lotDrawable->mCNode->mImageInfo.mMatrix.m11 = combinedMatrix().m_11();
        lotDrawable->mCNode->mImageInfo.mMatrix.m12 = combinedMatrix().m_12();
        lotDrawable->mCNode->mImageInfo.mMatrix.m13 = combinedMatrix().m_13();

        lotDrawable->mCNode->mImageInfo.mMatrix.m21 = combinedMatrix().m_21();
        lotDrawable->mCNode->mImageInfo.mMatrix.m22 = combinedMatrix().m_22();
        lotDrawable->mCNode->mImageInfo.mMatrix.m23 = combinedMatrix().m_23();

        lotDrawable->mCNode->mImageInfo.mMatrix.m31 = combinedMatrix().m_tx();
        lotDrawable->mCNode->mImageInfo.mMatrix.m32 = combinedMatrix().m_ty();
        lotDrawable->mCNode->mImageInfo.mMatrix.m33 = combinedMatrix().m_33();

        // Alpha calculation already combined.
        lotDrawable->mCNode->mImageInfo.mAlpha =
            uchar(lotDrawable->mBrush.mTexture->mAlpha);

        cnodes().push_back(lotDrawable->mCNode.get());
    }
    clayer().mNodeList.ptr = cnodes().data();
    clayer().mNodeList.size = cnodes().size();
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
        ptr->a = uchar(i.second.alpha() * grad->alpha());
        ptr->r = i.second.red();
        ptr->g = i.second.green();
        ptr->b = i.second.blue();
        ptr++;
    }
}

void renderer::Drawable::sync()
{
    if (!mCNode) {
        mCNode = std::make_unique<LOTNode>();
        mCNode->mGradient.stopPtr = nullptr;
        mCNode->mGradient.stopCount = 0;
    }

    mCNode->mFlag = ChangeFlagNone;
    if (mFlag & DirtyState::None) return;

    if (mFlag & DirtyState::Path) {
        applyDashOp();
        const std::vector<VPath::Element> &elm = mPath.elements();
        const std::vector<VPointF> &       pts = mPath.points();
        const float *ptPtr = reinterpret_cast<const float *>(pts.data());
        const char * elmPtr = reinterpret_cast<const char *>(elm.data());
        mCNode->mPath.elmPtr = elmPtr;
        mCNode->mPath.elmCount = elm.size();
        mCNode->mPath.ptPtr = ptPtr;
        mCNode->mPath.ptCount = 2 * pts.size();
        mCNode->mFlag |= ChangeFlagPath;
        mCNode->keypath = name();
    }

    if (mStrokeInfo) {
        mCNode->mStroke.width = mStrokeInfo->width;
        mCNode->mStroke.miterLimit = mStrokeInfo->miterLimit;
        mCNode->mStroke.enable = 1;

        switch (mStrokeInfo->cap) {
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

        switch (mStrokeInfo->join) {
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

        float scale = mBrush.mGradient->mMatrix.scale();
        mCNode->mGradient.cradius = mBrush.mGradient->radial.cradius * scale;
        mCNode->mGradient.fradius = mBrush.mGradient->radial.fradius * scale;
        updateGStops(mCNode.get(), mBrush.mGradient);
        break;
    }
    default:
        break;
    }
}
