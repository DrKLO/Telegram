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
#include "vraster.h"
#include <climits>
#include <cstring>
#include <memory>
#include "config.h"
#include "v_ft_raster.h"
#include "v_ft_stroker.h"
#include "vdebug.h"
#include "vmatrix.h"
#include "vpath.h"
#include "vrle.h"

V_BEGIN_NAMESPACE

template <typename T>
class dyn_array {
public:
    explicit dyn_array(size_t size)
        : mCapacity(size), mData(std::make_unique<T[]>(mCapacity))
    {
    }
    void reserve(size_t size)
    {
        if (mCapacity > size) return;
        mCapacity = size;
        mData = std::make_unique<T[]>(mCapacity);
    }
    [[nodiscard]] T *        data() const { return mData.get(); }
    dyn_array &operator=(dyn_array &&) noexcept = delete;

private:
    size_t               mCapacity{0};
    std::unique_ptr<T[]> mData{nullptr};
};

struct FTOutline {
public:
    void reset();
    void grow(size_t, size_t);
    void convert(const VPath &path);
    void convert(CapStyle, JoinStyle, float, float);
    void moveTo(const VPointF &pt);
    void lineTo(const VPointF &pt);
    void cubicTo(const VPointF &cp1, const VPointF &cp2, const VPointF &ep);
    void close();
    void end();
    static SW_FT_Pos TO_FT_COORD(float x)
    {
        return SW_FT_Pos(x * 64);
    }  // to freetype 26.6 coordinate.
    SW_FT_Outline           ft;
    bool                    closed{false};
    SW_FT_Stroker_LineCap   ftCap;
    SW_FT_Stroker_LineJoin  ftJoin;
    SW_FT_Fixed             ftWidth;
    SW_FT_Fixed             ftMiterLimit;
    dyn_array<SW_FT_Vector> mPointMemory{100};
    dyn_array<char>         mTagMemory{100};
    dyn_array<short>        mContourMemory{10};
    dyn_array<char>         mContourFlagMemory{10};
};

void FTOutline::reset()
{
    ft.n_points = ft.n_contours = 0;
    ft.flags = 0x0;
}

void FTOutline::grow(size_t points, size_t segments)
{
    reset();
    mPointMemory.reserve(points + segments);
    mTagMemory.reserve(points + segments);
    mContourMemory.reserve(segments);
    mContourFlagMemory.reserve(segments);

    ft.points = mPointMemory.data();
    ft.tags = mTagMemory.data();
    ft.contours = mContourMemory.data();
    ft.contours_flag = mContourFlagMemory.data();
}

void FTOutline::convert(const VPath &path)
{
    const std::vector<VPath::Element> &elements = path.elements();
    const std::vector<VPointF> &       points = path.points();

    grow(points.size(), path.segments());

    size_t index = 0;
    for (auto element : elements) {
        switch (element) {
        case VPath::Element::MoveTo:
            moveTo(points[index]);
            index++;
            break;
        case VPath::Element::LineTo:
            lineTo(points[index]);
            index++;
            break;
        case VPath::Element::CubicTo:
            cubicTo(points[index], points[index + 1], points[index + 2]);
            index = index + 3;
            break;
        case VPath::Element::Close:
            close();
            break;
        }
    }
    end();
}

void FTOutline::convert(CapStyle cap, JoinStyle join, float width,
                        float miterLimit)
{
    // map strokeWidth to freetype. It uses as the radius of the pen not the
    // diameter
    width = width / 2.0f;
    // convert to freetype co-ordinate
    // IMP: stroker takes radius in 26.6 co-ordinate
    ftWidth = SW_FT_Fixed(width * (1 << 6));
    // IMP: stroker takes meterlimit in 16.16 co-ordinate
    ftMiterLimit = SW_FT_Fixed(miterLimit * (1 << 16));

    // map to freetype capstyle
    switch (cap) {
    case CapStyle::Square:
        ftCap = SW_FT_STROKER_LINECAP_SQUARE;
        break;
    case CapStyle::Round:
        ftCap = SW_FT_STROKER_LINECAP_ROUND;
        break;
    default:
        ftCap = SW_FT_STROKER_LINECAP_BUTT;
        break;
    }
    switch (join) {
    case JoinStyle::Bevel:
        ftJoin = SW_FT_STROKER_LINEJOIN_BEVEL;
        break;
    case JoinStyle::Round:
        ftJoin = SW_FT_STROKER_LINEJOIN_ROUND;
        break;
    default:
        ftJoin = SW_FT_STROKER_LINEJOIN_MITER_FIXED;
        break;
    }
}

void FTOutline::moveTo(const VPointF &pt)
{
    assert(ft.n_points <= SHRT_MAX - 1);

    ft.points[ft.n_points].x = TO_FT_COORD(pt.x());
    ft.points[ft.n_points].y = TO_FT_COORD(pt.y());
    ft.tags[ft.n_points] = SW_FT_CURVE_TAG_ON;
    if (ft.n_points) {
        ft.contours[ft.n_contours] = ft.n_points - 1;
        ft.n_contours++;
    }
    // mark the current contour as open
    // will be updated if ther is a close tag at the end.
    ft.contours_flag[ft.n_contours] = 1;

    ft.n_points++;
}

void FTOutline::lineTo(const VPointF &pt)
{
    assert(ft.n_points <= SHRT_MAX - 1);

    ft.points[ft.n_points].x = TO_FT_COORD(pt.x());
    ft.points[ft.n_points].y = TO_FT_COORD(pt.y());
    ft.tags[ft.n_points] = SW_FT_CURVE_TAG_ON;
    ft.n_points++;
}

void FTOutline::cubicTo(const VPointF &cp1, const VPointF &cp2,
                        const VPointF &ep)
{
    assert(ft.n_points <= SHRT_MAX - 3);

    ft.points[ft.n_points].x = TO_FT_COORD(cp1.x());
    ft.points[ft.n_points].y = TO_FT_COORD(cp1.y());
    ft.tags[ft.n_points] = SW_FT_CURVE_TAG_CUBIC;
    ft.n_points++;

    ft.points[ft.n_points].x = TO_FT_COORD(cp2.x());
    ft.points[ft.n_points].y = TO_FT_COORD(cp2.y());
    ft.tags[ft.n_points] = SW_FT_CURVE_TAG_CUBIC;
    ft.n_points++;

    ft.points[ft.n_points].x = TO_FT_COORD(ep.x());
    ft.points[ft.n_points].y = TO_FT_COORD(ep.y());
    ft.tags[ft.n_points] = SW_FT_CURVE_TAG_ON;
    ft.n_points++;
}
void FTOutline::close()
{
    assert(ft.n_points <= SHRT_MAX - 1);

    // mark the contour as a close path.
    ft.contours_flag[ft.n_contours] = 0;

    int index;
    if (ft.n_contours) {
        index = ft.contours[ft.n_contours - 1] + 1;
    } else {
        index = 0;
    }

    // make sure atleast 1 point exists in the segment.
    if (ft.n_points == index) {
        closed = false;
        return;
    }

    ft.points[ft.n_points].x = ft.points[index].x;
    ft.points[ft.n_points].y = ft.points[index].y;
    ft.tags[ft.n_points] = SW_FT_CURVE_TAG_ON;
    ft.n_points++;
}

void FTOutline::end()
{
    assert(ft.n_contours <= SHRT_MAX - 1);

    if (ft.n_points) {
        ft.contours[ft.n_contours] = ft.n_points - 1;
        ft.n_contours++;
    }
}

static void rleGenerationCb(int count, const SW_FT_Span *spans, void *user)
{
    VRle *rle = static_cast<VRle *>(user);
    auto *rleSpan = reinterpret_cast<const VRle::Span *>(spans);
    rle->addSpan(rleSpan, count);
}

static void bboxCb(int x, int y, int w, int h, void *user)
{
    VRle *rle = static_cast<VRle *>(user);
    rle->setBoundingRect({x, y, w, h});
}

class SharedRle {
public:
    SharedRle() = default;
    VRle &unsafe() { return _rle; }
    void  notify()
    {
        {
            std::lock_guard<std::mutex> lock(_mutex);
            _ready = true;
        }
        _cv.notify_one();
    }
    void wait()
    {
        if (!_pending) return;

        {
            std::unique_lock<std::mutex> lock(_mutex);
            while (!_ready) _cv.wait(lock);
        }

        _pending = false;
    }

    VRle &get()
    {
        wait();
        return _rle;
    }

    void reset()
    {
        wait();
        _ready = false;
        _pending = true;
    }

private:
    VRle                    _rle;
    std::mutex              _mutex;
    std::condition_variable _cv;
    bool                    _ready{true};
    bool                    _pending{false};
};

struct VRleTask {
    SharedRle mRle;
    VPath     mPath;
    float     mStrokeWidth{};
    float     mMiterLimit{};
    VRect     mClip;
    FillRule  mFillRule;
    CapStyle  mCap;
    JoinStyle mJoin;
    bool      mGenerateStroke{};

    VRle &rle() { return mRle.get(); }

    void update(VPath path, FillRule fillRule, const VRect &clip)
    {
        mRle.reset();
        mPath = std::move(path);
        mFillRule = fillRule;
        mClip = clip;
        mGenerateStroke = false;
    }

    void update(VPath path, CapStyle cap, JoinStyle join, float width,
                float miterLimit, const VRect &clip)
    {
        mRle.reset();
        mPath = std::move(path);
        mCap = cap;
        mJoin = join;
        mStrokeWidth = width;
        mMiterLimit = miterLimit;
        mClip = clip;
        mGenerateStroke = true;
    }
    void render(FTOutline &outRef)
    {
        SW_FT_Raster_Params params;

        mRle.unsafe().reset();

        params.flags = SW_FT_RASTER_FLAG_DIRECT | SW_FT_RASTER_FLAG_AA;
        params.gray_spans = &rleGenerationCb;
        params.bbox_cb = &bboxCb;
        params.user = &mRle.unsafe();
        params.source = &outRef.ft;

        if (!mClip.empty()) {
            params.flags |= SW_FT_RASTER_FLAG_CLIP;

            params.clip_box.xMin = mClip.left();
            params.clip_box.yMin = mClip.top();
            params.clip_box.xMax = mClip.right();
            params.clip_box.yMax = mClip.bottom();
        }
        // compute rle
        sw_ft_grays_raster.raster_render(nullptr, &params);
    }

    void operator()(FTOutline &outRef, SW_FT_Stroker &stroker)
    {
        if (mPath.points().size() > SHRT_MAX ||
            mPath.points().size() + mPath.segments() > SHRT_MAX) {
            return;
        }

        if (mGenerateStroke) {  // Stroke Task
            outRef.convert(mPath);
            outRef.convert(mCap, mJoin, mStrokeWidth, mMiterLimit);

            uint points, contors;

            SW_FT_Stroker_Set(stroker, outRef.ftWidth, outRef.ftCap,
                              outRef.ftJoin, outRef.ftMiterLimit);
            SW_FT_Stroker_ParseOutline(stroker, &outRef.ft);
            SW_FT_Stroker_GetCounts(stroker, &points, &contors);

            outRef.grow(points, contors);

            SW_FT_Stroker_Export(stroker, &outRef.ft);

        } else {  // Fill Task
            outRef.convert(mPath);
            int fillRuleFlag = SW_FT_OUTLINE_NONE;
            switch (mFillRule) {
            case FillRule::EvenOdd:
                fillRuleFlag = SW_FT_OUTLINE_EVEN_ODD_FILL;
                break;
            default:
                fillRuleFlag = SW_FT_OUTLINE_NONE;
                break;
            }
            outRef.ft.flags = fillRuleFlag;
        }

        render(outRef);

        mPath = VPath();

        mRle.notify();
    }
};

struct VRasterizer::VRasterizerImpl {
    VRleTask mTask;
    FTOutline     outlineRef;
    SW_FT_Stroker stroker;

    VRasterizerImpl() {
        SW_FT_Stroker_New(&stroker);
    }

    ~VRasterizerImpl() {
        SW_FT_Stroker_Done(stroker);
    }

    VRle &    rle() { return mTask.rle(); }
    VRleTask &task() { return mTask; }
};

VRle VRasterizer::rle()
{
    if (!d) return VRle();
    return d->rle();
}

void VRasterizer::init()
{
    if (!d) d = std::make_shared<VRasterizerImpl>();
}

void VRasterizer::updateRequest()
{
    d->task()(d->outlineRef, d->stroker);
}

void VRasterizer::rasterize(VPath path, FillRule fillRule, const VRect &clip)
{
    init();
    if (path.empty()) {
        d->rle().reset();
        return;
    }
    d->task().update(std::move(path), fillRule, clip);
    updateRequest();
}

void VRasterizer::rasterize(VPath path, CapStyle cap, JoinStyle join,
                            float width, float miterLimit, const VRect &clip)
{
    init();
    if (path.empty() || vIsZero(width)) {
        d->rle().reset();
        return;
    }
    d->task().update(std::move(path), cap, join, width, miterLimit, clip);
    updateRequest();
}

V_END_NAMESPACE
