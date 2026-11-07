#import "LHDocument.h"
#import <AppKit/AppKit.h>
#import "LHGPUInternal.h"
#import "LHDocumentPainting.h"

#include "LHContainer.h"
#include "LHImmutableBitmap.h"
#include "LHSimpleText.h"
#include "lhx_local_paint_key.h"
#include "lhx_alpha_bounds.h"
#include "lhx_paint_exclusion_cache.h"
#include "lhx_paint_exclusion_rules.h"
#include "render_item.h"
#include "render_inline_context.h"
#include "html_tag.h"
#include "el_space.h"
#include "el_svg.h"
#include "el_image.h"
#include "el_before_after.h"
#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cstring>
#include <cmath>
#include <functional>
#include <limits>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <memory>
#include <mutex>

#include <set>
#include <vector>

namespace lh = litehtml;

static double NowMs()
{
    using namespace std::chrono;
    return duration<double, std::milli>(steady_clock::now().time_since_epoch()).count();
}

static const std::string &MasterCSS()
{
    static const std::string& css = *new std::string([] {
        std::string s     = lh::master_css;
        size_t      start = s.find("a:link");
        size_t      end   = start == std::string::npos ? start : s.find('}', start);
        if(end != std::string::npos)
            s.replace(start, end - start + 1, "a:link { cursor: pointer; }");
        s += R"CSS(
[hidden], template, datalist { display: none; }
details, summary { display: block; }
details > summary:first-of-type { display: list-item; list-style-type: none; }
[dir=ltr] { direction: ltr; }
[dir=rtl] { direction: rtl; }
q::before { content: open-quote; }
q::after { content: close-quote; }
summary::before { content: "▶︎"; display: inline-block; width: 1.25em; font-size: 0.9em; }
details[open] > summary::before { content: "▼︎"; }
details:not([open]) > :not(summary) { display: none; }
input, select, textarea, button { appearance: auto; font-family: system-ui; font-size: 11px; font-weight: 400; font-style: normal; color: rgba(2, 3, 1, 0.004); letter-spacing: normal; word-spacing: normal; line-height: normal; text-transform: none; text-indent: 0; text-shadow: none; text-align: left; }
input { box-sizing: content-box; padding: 1.01px 2.01px; border: 2px inset rgba(1, 2, 3, 0.004); background-color: rgba(3, 2, 1, 0.004); vertical-align: baseline; }
input[type="number"] { padding: 1.01px 2.02px 1.01px 2.01px; }
input[type="checkbox"], input[type="radio"] { box-sizing: border-box; width: 12px; height: 12px; margin: 3px 2px; padding: 0; border: 0; }
input[type="range"] { width: 129px; height: 16px; margin: 2px; padding: 0; border: 0; }
input[type="color"] { box-sizing: border-box; width: 44px; height: 23px; padding: 1px; border-width: 2px; }
input[type="button"], input[type="submit"], input[type="reset"], button { box-sizing: border-box; padding: 0.01px 6.01px 1.01px; border: 2px outset rgba(1, 2, 3, 0.004); background-color: rgba(3, 2, 1, 0.004); text-align: center; }
select { box-sizing: border-box; padding: 0; border: 1px solid rgba(1, 2, 3, 0.004); border-radius: 5px; background-color: rgba(3, 2, 1, 0.004); vertical-align: baseline; }
textarea { display: inline-block; box-sizing: content-box; width: 12.73em; height: auto; padding: 2px 5px; border: 1px solid rgba(1, 2, 3, 0.004); background-color: rgba(3, 2, 1, 0.004); white-space: pre-wrap; overflow-wrap: break-word; overflow: hidden; }
fieldset { display: block; margin: 0 2px; padding: 0.35em 0.75em 0.625em; border: 2px groove rgb(192, 192, 192); }
legend { display: table; padding: 0 2px; }
progress, meter { appearance: auto; display: inline-block; box-sizing: border-box; width: 140px; height: 14px; vertical-align: -0.2em; overflow: hidden; color: transparent; font-size: 0; }
meter { width: 70px; }
)CSS";
        return s;
    }());
    return css;
}

static lh::web_color LHControlAccent()
{
    if(@available(macOS 10.14, *))
    {
        NSColor *accent = [NSColor.controlAccentColor colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
        if(accent)
            return lh::web_color((lh::byte)std::lround(accent.redComponent * 255), (lh::byte)std::lround(accent.greenComponent * 255),
                                 (lh::byte)std::lround(accent.blueComponent * 255));
    }
    return lh::web_color(0, 122, 255);
}

static void IgnoreRedraw(const lh::position &) {}

static bool UsesPercentHeight(const lh::element::ptr &el)
{
    return el->css().get_height().units() == lh::css_units_percentage ||
           el->css().get_min_height().units() == lh::css_units_percentage;
}

static bool NeedsViewportHeight(const lh::document::ptr &doc)
{
    lh::position::vector fixed;
    doc->get_fixed_boxes(fixed);
    if(!fixed.empty())
        return true;
    auto root = doc->root();
    if(!root)
        return false;
    if(UsesPercentHeight(root))
        return true;
    for(const auto &child : root->children())
        if(child->tag() == lh::_body_ && UsesPercentHeight(child))
            return true;
    return false;
}

struct LHDeclarationsEntry
{
    NSDictionary *value;
    NSArray      *key;
    uint64_t      epoch;
    uint64_t      generation;
};

static bool LHExclusionStateMatches(NSSet<NSString *> *saved, NSString *savedAttribute, NSSet<NSString *> *values, NSString *attribute)
{
    return (saved == values || [saved isEqualToSet:values]) &&
           (savedAttribute == attribute || [savedAttribute isEqualToString:attribute]);
}

static bool LHPaintExclusionCacheEnabled()
{
    static const bool enabled = !getenv("TW_PAINT_EXCLUSION_CACHE") || strcmp(getenv("TW_PAINT_EXCLUSION_CACHE"), "0") != 0;
    return enabled;
}

static bool SubtreeNeedsBackdrop(const lh::element *root)
{
    if(!root->css().get_backdrop_filter().empty())
        return true;
    for(const auto &child : root->children())
        if(SubtreeNeedsBackdrop(child.get()))
            return true;
    return false;
}

static void IncludeScrollSubtree(lh::document &document, const lh::element *root)
{
    for(auto it = document.skip_scroll_content.begin(); it != document.skip_scroll_content.end();)
    {
        auto owner = *it;
        while(owner && owner != root)
            owner = owner->parent().get();
        if(owner == root)
            it = document.skip_scroll_content.erase(it);
        else
            ++it;
    }
}

static bool ParseKeyInteger(const char *text, int64_t &out)
{
    const char *p = text;
    bool negative = false;
    if(*p == '-' || *p == '+')
        negative = *p++ == '-';
    if(!*p)
        return false;
    uint64_t magnitude = 0;
    const uint64_t limit = negative ? uint64_t(INT64_MAX) + 1 : uint64_t(INT64_MAX);
    for(; *p; p++)
    {
        if(*p < '0' || *p > '9')
            return false;
        const uint64_t digit = uint64_t(*p - '0');
        if(magnitude > (limit - digit) / 10)
            return false;
        magnitude = magnitude * 10 + digit;
    }
    out = negative ? int64_t(0 - magnitude) : int64_t(magnitude);
    return true;
}

static void CollectPositionedZ(const std::shared_ptr<lh::render_item> &item, std::set<int> &z)
{
    for(const auto &child : item->children())
    {
        if(child->src_el()->is_positioned())
            z.insert(child->src_el()->css().get_z_index());
        else
            CollectPositionedZ(child, z);
    }
}

static void DrawAsStackingContext(const std::shared_ptr<lh::render_item> &ri, lh::uint_ptr hdc, lh::pixel_t x, lh::pixel_t y)
{
    std::set<int> z;
    CollectPositionedZ(ri, z);
    for(int index : z)
        if(index < 0)
            ri->draw_children(hdc, x, y, nullptr, lh::draw_positioned, index);
    ri->draw_children(hdc, x, y, nullptr, lh::draw_block, 0);
    ri->draw_children(hdc, x, y, nullptr, lh::draw_floats, 0);
    ri->draw_children(hdc, x, y, nullptr, lh::draw_inlines, 0);
    for(int index : z)
        if(index >= 0)
            ri->draw_children(hdc, x, y, nullptr, lh::draw_positioned, index);
}

static bool CopyPixels(CGImageRef image, CGContextRef ctx)
{
    size_t width = CGImageGetWidth(image), height = CGImageGetHeight(image);
    if(CGImageGetBitsPerPixel(image) != 32 || CGImageGetBitmapInfo(image) != CGBitmapContextGetBitmapInfo(ctx) ||
       CGBitmapContextGetWidth(ctx) != width || CGBitmapContextGetHeight(ctx) != height)
        return false;
    CGDataProviderRef provider = CGImageGetDataProvider(image);
    CFDataRef         data     = provider ? CGDataProviderCopyData(provider) : nullptr;
    if(!data)
        return false;
    const uint8_t *src       = CFDataGetBytePtr(data);
    uint8_t       *dst       = static_cast<uint8_t *>(CGBitmapContextGetData(ctx));
    size_t         srcStride = CGImageGetBytesPerRow(image), dstStride = CGBitmapContextGetBytesPerRow(ctx);
    bool           ok        = dst && src && (size_t)CFDataGetLength(data) >= srcStride * height;
    if(ok)
        for(size_t y = 0; y < height; y++)
            memcpy(dst + y * dstStride, src + y * srcStride, width * 4);
    CFRelease(data);
    return ok;
}

@interface LHTextFragment ()
- (instancetype)initWithText:(NSString *)text font:(CTFontRef)font frame:(CGRect)frame;
@end

@implementation LHTextFragment
{
    CTFontRef _font;
    CTLineRef _line;
}

- (instancetype)initWithText:(NSString *)text font:(CTFontRef)font frame:(CGRect)frame
{
    if((self = [super init]))
    {
        _text  = [text copy];
        _font  = (CTFontRef)CFRetain(font);
        _frame = frame;
    }
    return self;
}

- (void)dealloc
{
    if(_line)
        CFRelease(_line);
    CFRelease(_font);
}

- (CTLineRef)line
{
    if(!_line)
    {
        NSDictionary *attributes = @{(__bridge NSString *)kCTFontAttributeName: (__bridge id)_font};
        NSAttributedString *string = [[NSAttributedString alloc] initWithString:_text attributes:attributes];
        _line = CTLineCreateWithAttributedString((__bridge CFAttributedStringRef)string);
    }
    return _line;
}

- (NSUInteger)characterIndexForX:(CGFloat)x
{
    CFIndex index = CTLineGetStringIndexForPosition([self line], CGPointMake(x - _frame.origin.x, 0));
    if(index == kCFNotFound || index < 0)
        return 0;
    return MIN((NSUInteger)index, _text.length);
}

- (CGFloat)xForCharacterIndex:(NSUInteger)index
{
    index = MIN(index, _text.length);
    return _frame.origin.x + CTLineGetOffsetForStringIndex([self line], (CFIndex)index, nullptr);
}

@end

@interface LHDisplayList ()
- (instancetype)initWithOps:(std::vector<LHDrawOp>)ops;
@end

static uint64_t OpKey(const LHDrawOp &op)
{
    uint64_t     key       = op.hash;
    const double values[4] = {op.bounds.origin.x, op.bounds.origin.y, op.bounds.size.width, op.bounds.size.height};
    const auto  *bytes     = reinterpret_cast<const unsigned char *>(values);
    for(size_t i = 0; i < sizeof(values); i++)
        key = (key ^ bytes[i]) * 0x100000001b3ULL;
    return key;
}

static std::vector<std::pair<uint64_t, size_t>> OpIdentities(const std::vector<LHDrawOp> &ops)
{
    std::unordered_map<uint64_t, size_t>      seen;
    std::vector<std::pair<uint64_t, size_t>> result;
    result.reserve(ops.size());
    for(const auto &op : ops)
    {
        uint64_t key = OpKey(op);
        result.emplace_back(key, seen[key]++);
    }
    return result;
}

struct OpIdentityHash
{
    size_t operator()(const std::pair<uint64_t, size_t> &v) const { return std::hash<uint64_t>()(v.first ^ (v.second * 0x9e3779b97f4a7c15ULL)); }
};

using OpIdentitySet = std::unordered_set<std::pair<uint64_t, size_t>, OpIdentityHash>;

@implementation LHSVGShape
{
@public
    CGPathRef  _path;
    CGColorRef _fillColor;
    CGColorRef _strokeColor;
    NSArray   *_gradientColors;
    NSArray   *_gradientLocations;
    CGPoint    _gradientStart, _gradientEnd;
    BOOL       _evenOdd;
    CGFloat    _lineWidth, _miterLimit;
    int        _lineCap, _lineJoin;
}
- (CGPathRef)path { return _path; }
- (CGColorRef)fillColor { return _fillColor; }
- (CGColorRef)strokeColor { return _strokeColor; }
- (NSArray *)gradientColors { return _gradientColors; }
- (NSArray<NSNumber *> *)gradientLocations { return _gradientLocations; }
- (CGPoint)gradientStart { return _gradientStart; }
- (CGPoint)gradientEnd { return _gradientEnd; }
- (BOOL)evenOdd { return _evenOdd; }
- (CGFloat)lineWidth { return _lineWidth; }
- (CGFloat)miterLimit { return _miterLimit; }
- (int)lineCap { return _lineCap; }
- (int)lineJoin { return _lineJoin; }
- (void)dealloc
{
    CGPathRelease(_path);
    CGColorRelease(_fillColor);
    CGColorRelease(_strokeColor);
}
@end

@implementation LHSVGShapes
{
@public
    CGRect              _frame;
    NSArray<LHSVGShape *> *_shapes;
}
- (CGRect)frame { return _frame; }
- (NSArray<LHSVGShape *> *)shapes { return _shapes; }
@end

@implementation LHBackgroundTile
{
@public
    CGImageRef _image;
    CGRect     _tile;
    CGRect     _area;
    CGRect     _clip;
    CGFloat    _radii[8];
}
- (CGImageRef)image { return _image; }
- (CGRect)tile { return _tile; }
- (CGRect)area { return _area; }
- (CGRect)clip { return _clip; }
- (CGFloat)radius:(NSInteger)index { return index >= 0 && index < 8 ? _radii[index] : 0; }
- (void)dealloc { CGImageRelease(_image); }
@end

@implementation LHDisplayList
{
    std::vector<LHDrawOp>                    _ops;
    std::vector<LHGPUOp>                     _gpuOps;
    BOOL                                     _hasGPU;
    BOOL                                     _gpuUnsupported;
    std::once_flag                           _identitiesOnce;
    std::vector<std::pair<uint64_t, size_t>> _identities;
    OpIdentitySet                            _identitySet;
}

- (const std::vector<std::pair<uint64_t, size_t>> &)identities
{
    std::call_once(_identitiesOnce, [self] {
        _identities = OpIdentities(_ops);
        _identitySet.reserve(_identities.size());
        _identitySet.insert(_identities.begin(), _identities.end());
    });
    return _identities;
}

- (const OpIdentitySet &)identitySet
{
    [self identities];
    return _identitySet;
}

- (void)setGPUOps:(std::vector<LHGPUOp>)ops unsupported:(BOOL)unsupported
{
    _gpuOps         = std::move(ops);
    _hasGPU         = YES;
    _gpuUnsupported = unsupported;
}

- (const std::vector<LHGPUOp> *)gpuOps
{
    return _hasGPU ? &_gpuOps : nullptr;
}

- (BOOL)gpuUnsupported
{
    return _gpuUnsupported;
}

- (BOOL)gpuSupported
{
    return _hasGPU && !_gpuUnsupported;
}

- (instancetype)initWithOps:(std::vector<LHDrawOp>)ops
{
    if((self = [super init]))
        _ops = std::move(ops);
    return self;
}

- (NSArray<NSValue *> *)changedRectsSince:(LHDisplayList *)previous
{
    const std::vector<LHDrawOp> &a = previous->_ops;
    const std::vector<LHDrawOp> &b = _ops;
    NSMutableArray<NSValue *>   *rects = [NSMutableArray array];
    auto add = [&](const LHDrawOp &op) {
        if(!CGRectIsNull(op.bounds) && !CGRectIsEmpty(op.bounds))
            [rects addObject:[NSValue valueWithRect:NSRectFromCGRect(op.bounds)]];
    };
    const auto &ida = [previous identities], &idb = [self identities];
    const auto &inA = [previous identitySet], &inB = [self identitySet];
    std::vector<std::pair<uint64_t, size_t>> commonA, commonB;
    std::vector<size_t>                      goneA, newB;
    for(size_t i = 0; i < a.size(); i++)
    {
        if(inB.count(ida[i]))
            commonA.push_back(ida[i]);
        else
            goneA.push_back(i);
    }
    for(size_t i = 0; i < b.size(); i++)
    {
        if(inA.count(idb[i]))
            commonB.push_back(idb[i]);
        else
            newB.push_back(i);
    }
    std::unordered_map<uint64_t, std::pair<int, int>> stretched;
    for(size_t i : goneA)
        if(a[i].stretch)
            stretched[a[i].stretch].first++;
    for(size_t i : newB)
        if(b[i].stretch)
            stretched[b[i].stretch].second++;
    std::unordered_map<uint64_t, size_t> pairedA;
    for(size_t i : goneA)
    {
        auto it = a[i].stretch ? stretched.find(a[i].stretch) : stretched.end();
        if(it != stretched.end() && it->second.first == 1 && it->second.second == 1)
            pairedA[a[i].stretch] = i;
        else
            add(a[i]);
    }
    for(size_t i : newB)
    {
        auto it = b[i].stretch ? pairedA.find(b[i].stretch) : pairedA.end();
        if(it == pairedA.end())
        {
            add(b[i]);
            continue;
        }
        const CGRect &ra = a[it->second].bounds, &rb = b[i].bounds;
        if(CGRectIsNull(ra) || CGRectIsNull(rb) || std::fabs(CGRectGetMinX(ra) - CGRectGetMinX(rb)) > 0.01 ||
           std::fabs(CGRectGetMinY(ra) - CGRectGetMinY(rb)) > 0.01 || std::fabs(CGRectGetHeight(ra) - CGRectGetHeight(rb)) > 0.01)
        {
            add(a[it->second]);
            add(b[i]);
            continue;
        }
        const CGFloat lo = std::max(CGRectGetMinX(ra), std::min(CGRectGetMaxX(ra), CGRectGetMaxX(rb)) - b[i].edge - 2);
        const CGFloat hi = std::max(CGRectGetMaxX(ra), CGRectGetMaxX(rb));
        if(hi > lo)
            [rects addObject:[NSValue valueWithRect:NSMakeRect(lo, CGRectGetMinY(rb), hi - lo, CGRectGetHeight(rb))]];
    }
    if(commonA != commonB)
    {
        [rects removeAllObjects];
        for(const auto &op : a)
            add(op);
        for(const auto &op : b)
            add(op);
    }
    for(const auto *list : {&a, &b})
    {
        for(const auto &op : *list)
        {
            if(!op.backdrop || CGRectIsNull(op.bounds))
                continue;
            for(NSUInteger i = 0, n = rects.count; i < n; i++)
            {
                if(CGRectIntersectsRect(rects[i].rectValue, op.bounds))
                {
                    add(op);
                    break;
                }
            }
        }
    }
    return rects;
}

static std::unordered_multimap<uint64_t, CGRect> OpShapes(const std::vector<LHDrawOp> &ops)
{
    std::unordered_multimap<uint64_t, CGRect> shapes;
    shapes.reserve(ops.size());
    for(const auto &op : ops)
        shapes.emplace(op.shape, op.bounds);
    return shapes;
}

static bool HasMovedCopy(const std::unordered_multimap<uint64_t, CGRect> &shapes, const LHDrawOp &op, CGFloat dy)
{
    if(CGRectIsNull(op.bounds))
        return false;
    auto range = shapes.equal_range(op.shape);
    for(auto it = range.first; it != range.second; ++it)
    {
        const CGRect &r = it->second;
        if(!CGRectIsNull(r) && std::fabs(r.origin.x - op.bounds.origin.x) < 0.05 && std::fabs(r.origin.y - op.bounds.origin.y - dy) <= 1
           && std::fabs(r.size.width - op.bounds.size.width) < 0.05 && std::fabs(r.size.height - op.bounds.size.height) < 0.05)
            return true;
    }
    return false;
}

static CGRect OpInk(const LHDrawOp &op)
{
    CGRect ink = CGRectInset(op.bounds, 1, 1);
    return CGRectIsNull(ink) ? op.bounds : ink;
}

static bool MovedDistance(const std::vector<LHDrawOp> &ops, const std::unordered_multimap<uint64_t, CGRect> &previous, CGFloat estimate, CGFloat top, CGFloat *distance)
{
    std::unordered_map<int64_t, size_t> votes;
    for(const auto &op : ops)
    {
        if(CGRectIsNull(op.bounds) || CGRectGetMinY(op.bounds) < top - 1)
            continue;
        auto range = previous.equal_range(op.shape);
        for(auto it = range.first; it != range.second; ++it)
        {
            const CGRect &r = it->second;
            if(CGRectIsNull(r) || std::fabs(r.origin.x - op.bounds.origin.x) >= 0.05)
                continue;
            const CGFloat dy = op.bounds.origin.y - r.origin.y;
            if(std::fabs(dy - estimate) < 1.5)
                votes[static_cast<int64_t>(std::llround(dy * 64))]++;
        }
    }
    if(votes.empty())
        return false;
    auto best = std::max_element(votes.begin(), votes.end(), [](const auto &l, const auto &r) { return l.second < r.second; });
    *distance = static_cast<CGFloat>(best->first) / 64;
    return true;
}

static CGFloat ChangedBottom(const std::vector<LHDrawOp> &ops, const std::unordered_multimap<uint64_t, CGRect> &other, CGFloat dy, CGFloat top, CGFloat offset)
{
    std::vector<CGRect> moved, changed;
    for(const auto &op : ops)
    {
        if(CGRectIsNull(op.bounds) || CGRectIsEmpty(op.bounds))
            continue;
        const CGRect ink = OpInk(op);
        if(CGRectGetMinY(ink) < top - 1)
            continue;
        (HasMovedCopy(other, op, dy) ? moved : changed).push_back(ink);
    }
    CGFloat bottom = -CGFLOAT_MAX;
    for(const auto &ink : changed)
    {
        const CGPoint center = CGPointMake(CGRectGetMidX(ink), CGRectGetMidY(ink));
        if(std::any_of(moved.begin(), moved.end(), [&](const CGRect &box) { return CGRectContainsPoint(box, center); }))
            continue;
        bottom = std::max(bottom, CGRectGetMaxY(ink) + offset);
    }
    return bottom;
}

- (CGFloat)unchangedBottomSince:(LHDisplayList *)previous previousHeight:(CGFloat)previousHeight height:(CGFloat)height below:(CGFloat)top
{
    const std::vector<LHDrawOp> &a   = previous->_ops;
    const std::vector<LHDrawOp> &b   = _ops;
    const auto                   inA = OpShapes(a);
    const auto                   inB = OpShapes(b);
    CGFloat                      delta;
    if(!MovedDistance(b, inA, height - previousHeight, top, &delta))
        return 0;
    const CGFloat start = std::max({top, ChangedBottom(b, inA, -delta, top, 0), ChangedBottom(a, inB, delta, top, delta)});
    return std::max<CGFloat>(0, height - start);
}

@end

struct PixelSnap
{
    float saved;
    explicit PixelSnap(CGFloat scale) : saved(lh::pixel_snap_scale())
    {
        lh::pixel_snap_scale() = scale >= 1 && scale <= 16 && std::isfinite(scale) ? static_cast<float>(scale) : 1.0f;
    }
    explicit PixelSnap(CGContextRef ctx) : saved(lh::pixel_snap_scale())
    {
        const CGAffineTransform m = CGContextGetCTM(ctx);
        const CGFloat           k = std::hypot(m.a, m.b);
        lh::pixel_snap_scale()    = k >= 1 && k <= 16 && std::isfinite(k) ? static_cast<float>(k) : 1.0f;
    }
    ~PixelSnap() { lh::pixel_snap_scale() = saved; }
};

static bool LHScrollTextBatchingSafe(const std::shared_ptr<lh::render_item>& root)
{
    if(getenv("LH_NO_SCROLL_TEXT_BATCH"))
        return false;
    lh::render_item* pending[512];
    size_t count = 1, visited = 0;
    pending[0] = root.get();
    while(count)
    {
        lh::render_item* item = pending[--count];
        if(++visited > 512 || !item->css().get_text_shadows().empty())
            return false;
        for(const auto& child : item->children())
        {
            if(count == 512)
                return false;
            pending[count++] = child.get();
        }
    }
    return true;
}

static bool LHPaintTraceUncached()
{
    static const bool uncached = getenv("LH_NO_TRACE_CACHE") != nullptr;
    return uncached;
}

static uint64_t LHPackSize(CGSize size)
{
    const float w = static_cast<float>(size.width), h = static_cast<float>(size.height);
    uint32_t    lo, hi;
    std::memcpy(&lo, &w, sizeof(lo));
    std::memcpy(&hi, &h, sizeof(hi));
    return static_cast<uint64_t>(lo) | (static_cast<uint64_t>(hi) << 32);
}

static bool DependsOnViewport(const std::string& text)
{
    const size_t count = text.size();
    if(strcasestr(text.c_str(), "@media"))
        return true;
    for(size_t i = 1; i + 1 < count; i++)
    {
        const char v = text[i];
        if(v != 'v' && v != 'V')
            continue;
        size_t number = i - 1;
        const char prefix = static_cast<char>(std::tolower(static_cast<unsigned char>(text[number])));
        if((prefix == 's' || prefix == 'l' || prefix == 'd') && number > 0)
            number--;
        const unsigned char digit = static_cast<unsigned char>(text[number]);
        if(!std::isdigit(digit) && digit != '.')
            continue;
        size_t end = i + 1;
        const char unit = static_cast<char>(std::tolower(static_cast<unsigned char>(text[end])));
        if(unit == 'w' || unit == 'h')
            end += 1;
        else if(count - end >= 3 && (strncasecmp(text.c_str() + end, "min", 3) == 0 || strncasecmp(text.c_str() + end, "max", 3) == 0))
            end += 3;
        else
            continue;
        if(end >= count || !std::isalpha(static_cast<unsigned char>(text[end])))
            return true;
    }
    return false;
}

static CGRect BorderBox(const lh::render_item *ri);
static CGRect ClippedBorderBox(const lh::render_item *ri);

template <typename F>
static bool VisitLineBoxes(lh::render_item *ri, F &&line)
{
    if(ri->src_el()->css().get_display() != lh::display_inline)
        return false;
    const lh::position p   = ri->get_placement();
    const CGFloat      dx  = p.x.value() - ri->pos().x.value();
    const CGFloat      dy  = p.y.value() - ri->pos().y.value();
    const CGFloat      top = ri->margin_top().value();
    const CGFloat      height = top + ri->margin_bottom().value();
    bool               any = false;
    ri->for_inline_boxes([&](const lh::position &b, bool first, bool last) {
        const CGFloat left  = first ? ri->margin_left().value() : 0;
        const CGFloat right = last ? ri->margin_right().value() : 0;
        const CGRect  box   = CGRectMake(dx + b.x.value() + left, dy + b.y.value() + top, std::max<CGFloat>(0, b.width.value() - left - right), std::max<CGFloat>(0, b.height.value() - height));
        if(box.size.width > 0 || box.size.height > 0)
        {
            line(box);
            any = true;
        }
        return true;
    });
    return any;
}

static CGAffineTransform VisualTransform(const lh::render_item *ri)
{
    lh::transform_matrix matrix;
    for(; ri; ri = ri->parent().get())
        matrix = matrix.then(ri->get_visual_transform());
    return CGAffineTransformMake(matrix.a, matrix.b, matrix.c, matrix.d, matrix.tx, matrix.ty);
}

struct LHCoverEntry
{
    const lh::render_item *item;
    CGRect                 box;
    int                    pre;
    int                    post;
    int                    owner;
    bool                   eligible;
};

struct LHCoverNode
{
    int value;
    int parent;
    int pre;
    int post;
};

struct LHCoverVisit
{
    int  pre;
    int  post;
    int  owner;
    bool transparent;
};

@implementation LHDocument
{
    std::mutex                   _lock;
    std::unique_ptr<LHContainer> _container;
    std::string                  _htmlSource;
    std::vector<const lh::render_item *> _paintTrace;
    BOOL                         _paintTraceValid;
    NSSet                       *_ignoredKey;
    std::unordered_set<std::string> _ignoredValues;
    int                          _backdropDepth;
    NSString                    *_html;
    lh::document::ptr            _doc;
    CGFloat                      _lastMaxWidth;
    CGFloat                      _preferredWidth;
    CGSize                       _size;
    CGSize                       _viewportSize;
    BOOL                         _dependsOnViewport;
    std::atomic<uint64_t>        _sizeMirror;
    BOOL                         _paintOnly;
    BOOL                         _pointerRestyled;
    NSString                    *_cursor;
    CGColorSpaceRef              _colorSpace;
    std::mutex                   _spaceLock;
    BOOL                         _noSubtreeCulling;
    BOOL                         _controlsActive;
    std::string                  _master;
    BOOL                         _updatable;
    BOOL                         _consumed;
    BOOL                         _consumedCulling;
    BOOL                         _reusedPrevious;
    BOOL                         _inheritedPointerRestyle;
    CGFloat                      _consumedMaxWidth;
    NSString                    *_exclusionAttribute;
    NSSet<NSString *>           *_exclusions;
    NSSet<NSString *>           *_textExclusions;
    NSString                    *_textExclusionAttribute;
    NSSet<NSString *>           *_layerExclusions;
    NSString                    *_layerAttribute;
    NSSet<NSString *>           *_backgroundExclusions;
    NSString                    *_backgroundAttribute;
    NSInteger                    _restoreMismatches;
    int                          _snapshotDepth;
    NSSet<NSString *>           *_scrollExclusions;
    std::unordered_map<std::string, std::weak_ptr<lh::render_item>> _scrollItems;
    std::unordered_map<std::string, std::weak_ptr<lh::render_item>> _itemIndex;
    std::string                  _itemIndexAttribute;
    BOOL                         _itemIndexValid;
    NSString                    *_scrollAttribute;
    lhx::PaintExclusionRules     _paintExclusionRules;
    BOOL                         _paintExclusionRulesValid;
    lhx::PaintExclusionCache<lh::element> _paintExclusionCache;
    uint64_t                     _exclusionStyleEpoch;
    uint64_t                     _exclusionLayoutEpoch;
    uint64_t                     _exclusionTreeSerial;
    uint64_t                     _exclusionCacheHits;
    uint64_t                     _exclusionCacheMisses;
    BOOL                         _exclusionCacheDisabled;
    std::vector<LHDeclarationsEntry> _declarations;
    uint64_t                     _paintTraceGeneration;
    std::vector<LHCoverEntry>    _coverEntries;
    std::vector<LHCoverNode>     _coverNodes;
    std::vector<std::string>     _coverValues;
    std::unordered_map<std::string, int> _coverValueIds;
    std::string                  _coverAttribute;
    uint64_t                     _coverGeneration;
    uint64_t                     _coverStyleEpoch;
    uint64_t                     _coverLayoutEpoch;
    lh::position                 _coverViewport;
    BOOL                         _coverValid;
    BOOL                         _coverUsable;
    BOOL                         _coverBoxesValid;
    BOOL                         _coverDisabled;
    std::vector<uint8_t>         _coverIgnored;
    NSSet                       *_coverIgnoredKey;
}

- (BOOL)ensurePaintTraceLocked
{
    if(_paintTraceValid && !LHPaintTraceUncached())
        return YES;
    _paintTrace.clear();
    _paintTrace.reserve(512);
    CGContextRef ctx = [self createContextWithWidth:1 height:1];
    if(!ctx)
        return NO;
    auto saved = _doc->skip_paint;
    auto savedScroll = _doc->skip_scroll_content;
    auto savedBackgrounds = _doc->skip_background_images;
    [self resolveExclusionsLocked];
    lh::g_paint_trace = &_paintTrace;
    _doc->skip_paint.clear();
    _doc->skip_scroll_content.clear();
    _backdropDepth++;
    _doc->draw(reinterpret_cast<lh::uint_ptr>(ctx), 0, 0, nullptr);
    _backdropDepth--;
    lh::g_paint_trace = nullptr;
    _doc->skip_paint = std::move(saved);
    _doc->skip_scroll_content = std::move(savedScroll);
    _doc->skip_background_images = std::move(savedBackgrounds);
    CGContextRelease(ctx);
    _paintTraceValid = YES;
    _paintTraceGeneration++;
    return YES;
}

- (void)setObscuringCacheDisabled:(BOOL)disabled
{
    std::lock_guard<std::mutex> guard(_lock);
    _coverDisabled = disabled;
}

- (BOOL)ensureCoverLocked:(const char *)key
{
    if(_coverDisabled || !_doc || !_doc->root_render())
        return NO;
    if(!_coverValid || _coverGeneration != _paintTraceGeneration || _coverStyleEpoch != _doc->style_epoch() || _coverAttribute != key)
    {
        _coverValid      = NO;
        _coverUsable     = NO;
        _coverBoxesValid = NO;
        _coverIgnoredKey = nil;
        _coverEntries.clear();
        _coverNodes.clear();
        _coverValues.clear();
        _coverValueIds.clear();
        std::unordered_map<const lh::render_item *, size_t> visits;
        std::vector<LHCoverVisit> infos;
        visits.reserve(_paintTrace.size() * 2);
        infos.reserve(_paintTrace.size() * 2);
        int  counter    = 0;
        bool consistent = true;
        std::function<void(lh::render_item *, int, bool)> visit = [&](lh::render_item *node, int owner, bool transparent) {
            transparent    = transparent || node->src_el()->css().get_opacity() <= 0;
            const int pre  = counter++;
            int       mine = -1;
            if(const char *v = node->src_el()->get_attr(key))
            {
                auto inserted = _coverValueIds.emplace(v, static_cast<int>(_coverValues.size()));
                if(inserted.second)
                    _coverValues.emplace_back(v);
                _coverNodes.push_back({inserted.first->second, owner, pre, 0});
                mine  = static_cast<int>(_coverNodes.size()) - 1;
                owner = mine;
            }
            const size_t slot = infos.size();
            if(!visits.emplace(node, slot).second)
                consistent = false;
            infos.push_back({pre, 0, owner, transparent});
            for(auto &child : node->children())
            {
                if(!child || child->parent().get() != node)
                {
                    consistent = false;
                    continue;
                }
                visit(child.get(), owner, transparent);
            }
            const int post   = counter++;
            infos[slot].post = post;
            if(mine >= 0)
                _coverNodes[mine].post = post;
        };
        auto root = _doc->root_render();
        if(root->parent())
            consistent = false;
        visit(root.get(), -1, false);
        _coverEntries.reserve(_paintTrace.size());
        for(const lh::render_item *item : _paintTrace)
        {
            auto found = visits.find(item);
            if(found == visits.end())
            {
                consistent = false;
                break;
            }
            const LHCoverVisit &info = infos[found->second];
            const bool eligible = !info.transparent && item->src_el()->css().get_visibility() == lh::visibility_visible && !dynamic_cast<const lh::el_space *>(item->src_el().get());
            _coverEntries.push_back({item, CGRectNull, info.pre, info.post, info.owner, eligible});
        }
        if(!consistent)
        {
            _coverEntries.clear();
            _coverNodes.clear();
            _coverValues.clear();
            _coverValueIds.clear();
        }
        _coverGeneration  = _paintTraceGeneration;
        _coverStyleEpoch  = _doc->style_epoch();
        _coverAttribute   = key;
        _coverValid       = YES;
        _coverUsable      = consistent;
    }
    if(!_coverUsable)
        return NO;
    lh::position viewport;
    _container->get_viewport(viewport);
    if(!_coverBoxesValid || _coverLayoutEpoch != _doc->layout_epoch() || !(viewport == _coverViewport))
    {
        for(LHCoverEntry &entry : _coverEntries)
            entry.box = ClippedBorderBox(entry.item);
        _coverLayoutEpoch = _doc->layout_epoch();
        _coverViewport    = viewport;
        _coverBoxesValid  = YES;
    }
    return YES;
}

- (CGImageRef)backdropLockedForOwner:(const void *)owner rect:(CGRect)rect scaleX:(CGFloat)sx scaleY:(CGFloat)sy flipped:(BOOL)flipped CF_RETURNS_RETAINED
{
    if(_backdropDepth > 0 || !_doc || !_doc->root_render() || !owner)
        return nullptr;
    if(![self ensurePaintTraceLocked])
        return nullptr;
    auto at = std::find_if(_paintTrace.begin(), _paintTrace.end(), [owner](const lh::render_item *item) { return item->src_el().get() == owner; });
    if(at == _paintTrace.end())
        return nullptr;
    const size_t pw = (size_t)std::llround(rect.size.width * sx), ph = (size_t)std::llround(rect.size.height * sy);
    if(pw == 0 || ph == 0 || pw * ph > (16u << 20))
        return nullptr;
    CGContextRef ctx = [self createContextWithWidth:pw height:ph];
    if(!ctx)
        return nullptr;
    if(flipped)
    {
        CGContextTranslateCTM(ctx, 0, ph);
        CGContextScaleCTM(ctx, sx, -sy);
    } else
        CGContextScaleCTM(ctx, sx, sy);
    CGContextTranslateCTM(ctx, -rect.origin.x, -rect.origin.y);
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    auto saved = _doc->skip_paint;
    for(auto it = at; it != _paintTrace.end(); ++it)
        _doc->skip_paint.insert((*it)->src_el().get());
    _backdropDepth++;
    _container->m_backdrop_source_depth++;
    lh::position clip(static_cast<float>(rect.origin.x), static_cast<float>(rect.origin.y), static_cast<float>(rect.size.width), static_cast<float>(rect.size.height));
    PixelSnap snap(ctx);
    _doc->draw(reinterpret_cast<lh::uint_ptr>(ctx), 0, 0, &clip, false);
    _container->m_backdrop_source_depth--;
    _backdropDepth--;
    _doc->skip_paint = saved;
    CGImageRef image = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    return image;
}

- (void)bindContainerLocked
{
    __weak LHDocument *weakSelf = self;
    _container->on_anchor = [weakSelf](const std::string &url) {
        NSString *u = @(url.c_str());
        dispatch_async(dispatch_get_main_queue(), ^{
            LHDocument *s = weakSelf;
            if(s.anchorHandler && u)
                s.anchorHandler(u);
        });
    };
    _container->on_action = [weakSelf](const std::string &action, const std::string &value) {
        NSString *a = @(action.c_str()), *v = value.empty() ? nil : @(value.c_str());
        dispatch_async(dispatch_get_main_queue(), ^{
            LHDocument *s = weakSelf;
            if(s.actionHandler && a)
                s.actionHandler(a, v);
        });
    };
    LHDocument *__unsafe_unretained unsafeSelf = self;
    _container->backdrop_source = [unsafeSelf](const void *owner, CGRect rect, CGFloat sx, CGFloat sy, bool flipped) -> CGImageRef {
        return [unsafeSelf backdropLockedForOwner:owner rect:rect scaleX:sx scaleY:sy flipped:flipped];
    };
    _container->on_cursor = [unsafeSelf](const std::string &cursor) {
        unsafeSelf->_cursor = @(cursor.c_str()) ?: @"auto";
    };
}

static std::string MasterWithCSS(NSString *css)
{
    std::string master = MasterCSS();
    master += "\n";
    master += css.UTF8String ?: "";
    return master;
}

- (instancetype)initWithHTML:(NSString *)html css:(NSString *)css
{
    if((self = [super init]))
    {
        _html         = [html copy];
        _lastMaxWidth = -1;
        _cursor       = @"auto";
        _container    = std::make_unique<LHContainer>();
        [self bindContainerLocked];

        double t0 = NowMs();
        _doc = lh::document::createFromString(lh::estring(html.UTF8String ?: ""), _container.get(), MasterWithCSS(css));
        _parseMilliseconds = NowMs() - t0;
    }
    return self;
}

- (instancetype)initWithHTML:(NSString *)html css:(NSString *)css previous:(LHDocument *)previous
{
    return [self initWithHTML:html css:css previous:previous viewportSize:previous ? previous.viewportSize : CGSizeZero];
}

- (instancetype)initWithHTML:(NSString *)html css:(NSString *)css previous:(LHDocument *)previous viewportSize:(CGSize)viewportSize
{
    if((self = [super init]))
    {
        _html         = [html copy];
        _lastMaxWidth = -1;
        _cursor       = @"auto";
        _updatable    = YES;
        _viewportSize = viewportSize;
        _master       = MasterWithCSS(css);

        double            t0   = NowMs();
        const std::string body = html.UTF8String ?: "";
        _dependsOnViewport = DependsOnViewport(body) || DependsOnViewport(css.UTF8String ?: "");
        if(previous)
        {
            std::lock_guard<std::mutex> guard(previous->_lock);
            if(previous->_doc && previous->_updatable && previous->_master == _master && (CGSizeEqualToSize(previous->_viewportSize, _viewportSize) || (!previous->_dependsOnViewport && !_dependsOnViewport)) &&
               previous->_doc->update_from_string(body, nullptr))
            {
                _doc       = std::move(previous->_doc);
                _container = std::move(previous->_container);
                _cursor    = previous->_cursor;
                [self takeDeclarationsLocked:previous];
                previous->_consumed         = YES;
                previous->_consumedMaxWidth = previous->_lastMaxWidth;
                previous->_consumedCulling  = _container->clip_culling();
                _reusedPrevious             = YES;
                _inheritedPointerRestyle    = previous->_pointerRestyled;
                if(_doc->last_patch_paint_only() && previous->_lastMaxWidth > 0 && !previous->_pointerRestyled)
                {
                    _lastMaxWidth = previous->_lastMaxWidth;
                    _size         = previous->_size;
                    _sizeMirror.store(LHPackSize(_size), std::memory_order_relaxed);
                    _paintOnly    = YES;
                }
            }
        }
        if(!_doc)
        {
            _container = std::make_unique<LHContainer>();
            _container->set_viewport(lh::position(0, 0, static_cast<float>(_viewportSize.width), static_cast<float>(_viewportSize.height)));
            _doc       = lh::document::createUpdatableFromString(body, _container.get(), _master);
        }
        [self bindContainerLocked];
        _parseMilliseconds = NowMs() - t0;
    }
    return self;
}

- (nullable instancetype)initWithPrevious:(LHDocument *)previous styles:(NSDictionary<NSString *, NSString *> *)styles attribute:(NSString *)attribute css:(NSString *)css
{
    if(!(self = [super init]))
        return nil;
    _lastMaxWidth = -1;
    _cursor       = @"auto";
    _updatable    = YES;
    _master       = MasterWithCSS(css);
    double t0     = NowMs();
    std::lock_guard<std::mutex> guard(previous->_lock);
    if(!previous->_doc || !previous->_updatable || previous->_master != _master || !previous->_doc->root() || styles.count == 0)
        return nil;
    std::unordered_map<std::string, std::string> wanted;
    for(NSString *key in styles)
        wanted.emplace(key.UTF8String ?: "", styles[key].UTF8String ?: "");
    std::vector<std::pair<lh::element::ptr, std::string>> patches;
    const char *name = attribute.UTF8String;
    std::function<void(const lh::element::ptr &)> visit = [&](const lh::element::ptr &el) {
        if(patches.size() == wanted.size())
            return;
        if(const char *v = el->get_attr(name))
        {
            auto it = wanted.find(v);
            if(it != wanted.end())
                patches.emplace_back(el, it->second);
        }
        for(const auto &child : el->children())
            visit(child);
    };
    visit(previous->_doc->root());
    if(patches.size() != wanted.size() || !previous->_doc->update_styles(patches, nullptr))
        return nil;
    if(![self adoptPatchedLocked:previous])
        return nil;
    _parseMilliseconds = NowMs() - t0;
    return self;
}

- (nullable instancetype)initWithPrevious:(LHDocument *)previous changes:(NSData *)changes attribute:(NSString *)attribute css:(NSString *)css
{
    if(!(self = [super init]))
        return nil;
    _lastMaxWidth = -1;
    _cursor       = @"auto";
    _updatable    = YES;
    _master       = MasterWithCSS(css);
    double t0     = NowMs();
    std::lock_guard<std::mutex> guard(previous->_lock);
    if(!previous->_doc || !previous->_updatable || previous->_master != _master || !previous->_doc->root() || changes.length == 0)
        return nil;
    const char *bytes = static_cast<const char *>(changes.bytes);
    const char *limit = bytes + changes.length;
    if(limit[-1] != 0)
        return nil;
    struct entry
    {
        std::string_view name, value;
    };
    std::unordered_map<std::string_view, std::vector<entry>> wanted;
    for(const char *p = bytes; p < limit;)
    {
        std::string_view parts[3];
        for(auto &part : parts)
        {
            if(p >= limit)
                return nil;
            part = std::string_view(p);
            p += part.size() + 1;
        }
        wanted[parts[0]].push_back({parts[1], parts[2]});
    }
    std::vector<lh::document::hole_change> patches;
    size_t matched = 0;
    const char *name = attribute.UTF8String;
    std::vector<lh::element *> pending{previous->_doc->root().get()};
    while(!pending.empty() && matched < wanted.size())
    {
        lh::element *el = pending.back();
        pending.pop_back();
        if(const char *v = el->get_attr(name))
        {
            auto it = wanted.find(std::string_view(v));
            if(it != wanted.end() && !it->second.empty())
            {
                matched++;
                const lh::element::ptr owner = el->shared_from_this();
                for(const auto &item : it->second)
                {
                    lh::document::hole_change change;
                    change.owner = owner;
                    change.text  = item.name.empty();
                    change.name  = std::string(item.name);
                    change.value = std::string(item.value);
                    patches.push_back(std::move(change));
                }
                it->second.clear();
            }
        }
        const auto &children = el->children();
        for(auto child = children.rbegin(); child != children.rend(); ++child)
            pending.push_back(child->get());
    }
    if(matched != wanted.size() || !previous->_doc->update_holes(patches, nullptr))
        return nil;
    if(![self adoptPatchedLocked:previous])
        return nil;
    _parseMilliseconds = NowMs() - t0;
    return self;
}

- (BOOL)adoptPatchedLocked:(LHDocument *)previous
{
    std::string html;
    if(!previous->_doc->template_html(html))
    {
        previous->_updatable = NO;
        previous->_doc.reset();
        previous->_container.reset();
        previous->_consumed         = YES;
        previous->_consumedMaxWidth = previous->_lastMaxWidth;
        return NO;
    }
    _htmlSource                 = std::move(html);
    _doc                        = std::move(previous->_doc);
    _container                  = std::move(previous->_container);
    _cursor                     = previous->_cursor;
    _viewportSize               = previous->_viewportSize;
    [self takeDeclarationsLocked:previous];
    previous->_consumed         = YES;
    previous->_consumedMaxWidth = previous->_lastMaxWidth;
    previous->_consumedCulling  = _container->clip_culling();
    _reusedPrevious             = YES;
    _inheritedPointerRestyle    = previous->_pointerRestyled;
    if(_doc->last_patch_paint_only() && previous->_lastMaxWidth > 0 && !previous->_pointerRestyled)
    {
        _lastMaxWidth = previous->_lastMaxWidth;
        _size         = previous->_size;
        _sizeMirror.store(LHPackSize(_size), std::memory_order_relaxed);
        _paintOnly    = YES;
    }
    [self bindContainerLocked];
    return YES;
}

+ (NSUInteger)cachedFontCount
{
    return LHContainer::cached_font_count();
}

+ (NSFont *)fontForFamilies:(NSString *)families size:(CGFloat)size weight:(NSInteger)weight italic:(BOOL)italic
{
    return LHResolveFont(families, size, (int)weight, italic);
}

+ (NSUInteger)rasterCacheBytes
{
    return LHContainer::raster_cache_bytes();
}

+ (BOOL)simpleTextEnabled
{
    return LHSimpleShaper::enabled();
}

+ (void)setSimpleTextEnabled:(BOOL)enabled
{
    LHSimpleShaper::set_enabled(enabled);
}

+ (NSDictionary<NSString*, NSNumber*>*)simpleTextStats
{
    const auto stats = LHSimpleShaper::stats();
    return @{@"hits": @(stats.hits), @"misses": @(stats.misses), @"learned": @(stats.learned), @"rejected": @(stats.rejected)};
}

+ (NSDictionary<NSString*, NSNumber*>*)layoutCacheStats
{
    return @{@"hits": @(lh::document::s_layout_hits.load()), @"misses": @(lh::document::s_layout_misses.load()),
             @"mismatches": @(lh::document::s_layout_mismatches.load())};
}

+ (NSUInteger)textMeasureCount
{
    return LHContainer::text_measure_count();
}

+ (void)purgeCaches
{
    LHContainer::purge_caches();
}

- (NSString *)html
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!_html)
        _html = [[NSString alloc] initWithBytes:_htmlSource.data() length:_htmlSource.size() encoding:NSUTF8StringEncoding] ?: @"";
    return _html;
}

- (BOOL)reusedPrevious
{
    return _reusedPrevious;
}

- (BOOL)reusedLayout
{
    return _paintOnly;
}

- (NSArray<NSValue *> *)layoutDirtyRects
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!_reusedPrevious || _paintOnly || !_doc || _inheritedPointerRestyle)
        return nil;
    std::vector<lh::position> regions;
    if(!_doc->layout_dirty_regions(regions))
        return nil;
    NSMutableArray<NSValue *> *rects = [NSMutableArray arrayWithCapacity:regions.size()];
    for(const auto &region : regions)
        [rects addObject:[NSValue valueWithRect:NSMakeRect(region.x.value(), region.y.value(), region.width.value(), region.height.value())]];
    return rects;
}

- (NSArray<NSValue *> *)patchDirtyRects
{
    std::lock_guard<std::mutex> guard(_lock);
    std::vector<lh::element::ptr> regions;
    if(!_paintOnly || !_doc || !_doc->root_render() || _doc->has_backdrop || !_doc->last_patch_regions(regions))
        return nil;
    if(regions.empty())
        return @[];
    const lh::render_item *root = _doc->root_render().get();
    std::unordered_map<const lh::element *, std::shared_ptr<lh::render_item>> found;
    for(const auto &region : regions)
    {
        if(found.count(region.get()))
            continue;
        std::shared_ptr<lh::render_item> single;
        int                              count = 0;
        region->for_each_render_item([&](const std::shared_ptr<lh::render_item> &ri) {
            auto top = ri;
            while(auto up = top->parent())
                top = up;
            if(top.get() != root)
                return;
            count++;
            single = ri;
        });
        if(count != 1)
            return nil;
        found.emplace(region.get(), single);
    }
    NSMutableArray<NSValue *> *rects = [NSMutableArray arrayWithCapacity:found.size()];
    for(const auto &entry : found)
    {
        const auto &ri = entry.second;
        if(!ri)
            return nil;
        const auto el       = ri->src_el();
        const bool text     = el->is_text();
        const bool replaced = std::dynamic_pointer_cast<lh::el_svg>(el) || std::dynamic_pointer_cast<lh::el_image>(el);
        if(!text && el->is_inline() && !el->is_inline_box() && !replaced)
            return nil;
        for(auto p = ri; p; p = p->parent())
        {
            const auto &css = p->src_el()->css();
            if(css.is_paint_group() || (css.get_position() != lh::element_position_static && css.get_position() != lh::element_position_relative))
                return nil;
        }
        if(!text && (!el->css().get_box_shadows().empty() || !el->css().get_text_shadows().empty() ||
                     (el->css().get_outline().style != lh::border_style_none && el->css().get_outline().style != lh::border_style_hidden)))
            return nil;
        CGRect box = BorderBox(ri);
        if(text)
        {
            const auto parent = el->parent();
            if(!parent || !parent->css().get_text_shadows().empty())
                return nil;
            const CGFloat slack = std::max<CGFloat>(4, std::ceil(box.size.height) / 2 + 1) + 1;
            box                 = CGRectInset(box, -slack, -slack);
        }
        if(CGRectIsEmpty(box))
            continue;
        [rects addObject:[NSValue valueWithRect:NSRectFromCGRect(CGRectInset(box, -1, -1))]];
    }
    return rects;
}

- (void)takeDeclarationsLocked:(LHDocument *)previous
{
    _declarations = std::move(previous->_declarations);
    previous->_declarations.clear();
}

- (void)restoreLocked
{
    if(!_consumed || _doc)
        return;
    _consumed     = NO;
    _paintExclusionCache.clear();
    _declarations.clear();
    _paintTraceValid = NO;
    _itemIndexValid  = NO;
    _container = std::make_unique<LHContainer>();
    _container->set_viewport(lh::position(0, 0, static_cast<float>(_viewportSize.width), static_cast<float>(_viewportSize.height)));
    _container->set_clip_culling(_consumedCulling);
    _container->set_controls_active(_controlsActive, LHControlAccent());
    [self bindContainerLocked];
    _doc          = lh::document::createUpdatableFromString(_html ? std::string(_html.UTF8String ?: "") : _htmlSource, _container.get(), _master);
    _lastMaxWidth = -1;
    if(_consumedMaxWidth > 0)
        [self layoutLockedWithMaxWidth:_consumedMaxWidth];
}

- (void)dealloc
{
    _doc.reset();
    _container.reset();
    if(_colorSpace)
        CGColorSpaceRelease(_colorSpace);
}

- (CGColorSpaceRef)colorSpace
{
    std::lock_guard<std::mutex> guard(_spaceLock);
    return _colorSpace;
}

- (CGColorSpaceRef)copyDrawingSpace CF_RETURNS_RETAINED
{
    std::lock_guard<std::mutex> guard(_spaceLock);
    return _colorSpace ? CGColorSpaceRetain(_colorSpace) : CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
}

- (void)setColorSpace:(CGColorSpaceRef)colorSpace
{
    std::lock_guard<std::mutex> guard(_spaceLock);
    if(colorSpace == _colorSpace)
        return;
    if(_colorSpace)
        CGColorSpaceRelease(_colorSpace);
    _colorSpace = colorSpace ? CGColorSpaceRetain(colorSpace) : nullptr;
}

- (CGSize)viewportSize
{
    return _viewportSize;
}

- (BOOL)dependsOnViewport
{
    return _dependsOnViewport;
}

- (CGSize)size
{
    if(_lock.try_lock())
    {
        CGSize size = _size;
        _lock.unlock();
        return size;
    }
    const uint64_t packed = _sizeMirror.load(std::memory_order_relaxed);
    float          w, h;
    const uint32_t lo = static_cast<uint32_t>(packed), hi = static_cast<uint32_t>(packed >> 32);
    std::memcpy(&w, &lo, sizeof(w));
    std::memcpy(&h, &hi, sizeof(h));
    return CGSizeMake(w, h);
}

- (BOOL)clipCulling
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    return _container->clip_culling();
}

- (void)setClipCulling:(BOOL)clipCulling
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    _container->set_clip_culling(clipCulling);
}

- (BOOL)controlsActive
{
    std::lock_guard<std::mutex> guard(_lock);
    return _controlsActive;
}

- (void)setControlsActive:(BOOL)controlsActive
{
    std::lock_guard<std::mutex> guard(_lock);
    _controlsActive = controlsActive;
    if(_container)
        _container->set_controls_active(controlsActive, LHControlAccent());
}

- (BOOL)subtreeCulling
{
    std::lock_guard<std::mutex> guard(_lock);
    return !_noSubtreeCulling;
}

- (void)setSubtreeCulling:(BOOL)subtreeCulling
{
    std::lock_guard<std::mutex> guard(_lock);
    _noSubtreeCulling = !subtreeCulling;
}

- (NSString *)cursor
{
    std::lock_guard<std::mutex> guard(_lock);
    return _cursor;
}

- (void)fitViewportHeightLockedWithWidth:(CGFloat)width
{
    _paintTraceValid = NO;
    _itemIndexValid  = NO;
    if(_viewportSize.height > 0 || !NeedsViewportHeight(_doc))
        return;
    _container->set_viewport(lh::position(0, 0, static_cast<float>(width), static_cast<float>(std::ceil(_doc->height().value()))));
    _doc->render(static_cast<float>(width));
}

- (CGSize)layoutLockedWithMaxWidth:(CGFloat)maxWidth
{
    _paintTraceValid = NO;
    _itemIndexValid  = NO;
    [self restoreLocked];
    maxWidth = std::max<CGFloat>(1, std::floor(maxWidth));
    if(!_doc)
        return CGSizeZero;
    if(std::fabs(maxWidth - _lastMaxWidth) < 0.5)
        return _size;
    if(_preferredWidth > 0.5 && _preferredWidth <= maxWidth + 0.5)
    {
        double  t0 = NowMs();
        CGFloat vw = std::min<CGFloat>(_preferredWidth, maxWidth);
        _container->set_viewport(lh::position(0, 0, static_cast<float>(vw), static_cast<float>(_viewportSize.height)));
        _doc->render(static_cast<float>(vw));
        CGFloat extent = std::ceil(_doc->width().value());
        if(vw >= maxWidth - 0.5 || extent <= vw + 0.5)
        {
            [self fitViewportHeightLockedWithWidth:vw];
            _lastMaxWidth       = maxWidth;
            _layoutMilliseconds = NowMs() - t0;
            _size = CGSizeMake(std::max(std::ceil(vw), extent), std::ceil(_doc->height().value()));
            _sizeMirror.store(LHPackSize(_size), std::memory_order_relaxed);
            return _size;
        }
    }
    _lastMaxWidth = maxWidth;

    double t0 = NowMs();
    _container->set_viewport(lh::position(0, 0, static_cast<float>(maxWidth), static_cast<float>(_viewportSize.height)));
    lh::pixel_t natural = _doc->render(static_cast<float>(maxWidth));
    CGFloat     width   = maxWidth;
    if(natural.value() > 0 && natural.value() < maxWidth)
    {
        width = std::min<CGFloat>(maxWidth, std::ceil(natural.value() + 0.01));
        _container->set_viewport(lh::position(0, 0, static_cast<float>(width), static_cast<float>(_viewportSize.height)));
        _doc->render(static_cast<float>(width));
    }
    CGFloat extent = std::ceil(_doc->width().value());
    for(int pass = 0; pass < 2 && extent > width + 0.5 && width < maxWidth; pass++)
    {
        width = std::min<CGFloat>(maxWidth, extent);
        _container->set_viewport(lh::position(0, 0, static_cast<float>(width), static_cast<float>(_viewportSize.height)));
        _doc->render(static_cast<float>(width));
        extent = std::ceil(_doc->width().value());
    }
    for(int pass = 0; pass < 3 && extent > maxWidth + 0.5; pass++)
    {
        CGFloat target = std::floor(width - (extent - maxWidth));
        if(target < maxWidth / 2)
            break;
        _container->set_viewport(lh::position(0, 0, static_cast<float>(target), static_cast<float>(_viewportSize.height)));
        _doc->render(static_cast<float>(target));
        CGFloat narrowed = std::ceil(_doc->width().value());
        if(narrowed >= extent - 0.5)
        {
            _container->set_viewport(lh::position(0, 0, static_cast<float>(width), static_cast<float>(_viewportSize.height)));
            _doc->render(static_cast<float>(width));
            break;
        }
        width  = target;
        extent = narrowed;
    }
    [self fitViewportHeightLockedWithWidth:width];
    _layoutMilliseconds = NowMs() - t0;
    _size               = CGSizeMake(std::max(std::ceil(width), extent), std::ceil(_doc->height().value()));
    _sizeMirror.store(LHPackSize(_size), std::memory_order_relaxed);
    return _size;
}

- (CGSize)layoutWithMaxWidth:(CGFloat)maxWidth
{
    std::lock_guard<std::mutex> guard(_lock);
    return [self layoutLockedWithMaxWidth:maxWidth];
}

- (CGSize)layoutWithMaxWidth:(CGFloat)maxWidth preferredWidth:(CGFloat)preferredWidth
{
    std::lock_guard<std::mutex> guard(_lock);
    _preferredWidth = preferredWidth;
    CGSize size     = [self layoutLockedWithMaxWidth:maxWidth];
    _preferredWidth = 0;
    return size;
}

- (void)resolveExclusionsLocked
{
    [self resolveExclusionsLockedInRoot:_doc->root()];
}

- (void)setPaintExclusionCacheDisabled:(BOOL)disabled
{
    std::lock_guard<std::mutex> guard(_lock);
    _exclusionCacheDisabled = disabled;
    _paintExclusionCache.clear();
}

- (NSDictionary<NSString *, NSNumber *> *)paintExclusionCacheStats
{
    std::lock_guard<std::mutex> guard(_lock);
    return @{@"hits": @(_exclusionCacheHits), @"misses": @(_exclusionCacheMisses),
             @"entries": @(_paintExclusionCache.size()), @"pointers": @(_paintExclusionCache.pointerCount())};
}

- (void)resolveExclusionsLockedInRoot:(const lh::element::ptr &)requestedRoot
{
    if(_exclusionCacheDisabled || !LHPaintExclusionCacheEnabled() || requestedRoot != _doc->root())
    {
        [self rebuildExclusionsLockedInRoot:requestedRoot];
        _exclusionCacheMisses++;
        return;
    }
    const uint64_t style = _doc->style_epoch(), layout = _doc->layout_epoch(), tree = _doc->m_render_tree_serial;
    if(style != _exclusionStyleEpoch || layout != _exclusionLayoutEpoch || tree != _exclusionTreeSerial)
    {
        _paintExclusionCache.clear();
        _exclusionStyleEpoch = style;
        _exclusionLayoutEpoch = layout;
        _exclusionTreeSerial = tree;
    }
    const bool snapshot = _snapshotDepth != 0;
    if(const auto *sets = _paintExclusionCache.find(requestedRoot.get(), snapshot))
    {
        _doc->skip_paint = sets->paint;
        _doc->skip_scroll_content = sets->scroll;
        _doc->skip_background_images = sets->background;
        _exclusionCacheHits++;
        return;
    }
    [self rebuildExclusionsLockedInRoot:requestedRoot];
    _exclusionCacheMisses++;
    _paintExclusionCache.store(requestedRoot.get(), snapshot,
        _doc->skip_paint, _doc->skip_scroll_content, _doc->skip_background_images);
}

- (void)rebuildExclusionsLockedInRoot:(const lh::element::ptr &)requestedRoot
{
    const auto root = requestedRoot && requestedRoot != _doc->root() && _doc->has_backdrop && SubtreeNeedsBackdrop(requestedRoot.get()) ? _doc->root() : requestedRoot;
    _doc->skip_paint.clear();
    _doc->skip_scroll_content.clear();
    _doc->skip_background_images.clear();
    for(NSString *value in _textExclusions)
    {
        auto item = [self renderItemLockedWithAttribute:_textExclusionAttribute value:value];
        if(item)
        {
            for(const auto &child : item->src_el()->children())
            {
                if(child->is_text())
                    _doc->skip_paint.insert(child.get());
            }
        }
    }
    if(!_paintExclusionRulesValid)
    {
        _paintExclusionRules.clear();
        auto add = [&](NSSet<NSString *> *values, NSString *attribute, uint8_t bits) {
            const char *name = attribute.UTF8String;
            if(!name)
                return;
            for(NSString *value in values)
            {
                const char *encoded = value.UTF8String;
                if(encoded)
                    _paintExclusionRules.add(name, encoded, bits);
            }
        };
        add(_exclusions, _exclusionAttribute, lhx::PaintExclusionRules::paint);
        add(_layerExclusions, _layerAttribute, lhx::PaintExclusionRules::layer);
        add(_backgroundExclusions, _backgroundAttribute, lhx::PaintExclusionRules::background);
        add(_scrollExclusions, _scrollAttribute, lhx::PaintExclusionRules::scroll);
        _paintExclusionRulesValid = YES;
    }
    const uint8_t active = lhx::PaintExclusionRules::paint | lhx::PaintExclusionRules::scroll |
        (_snapshotDepth == 0 ? lhx::PaintExclusionRules::layer | lhx::PaintExclusionRules::background : 0);
    if(!root || !_paintExclusionRules.active(active))
        return;
    std::function<void(const lh::element::ptr &)> visit = [&](const lh::element::ptr &el) {
        const uint8_t matched = _paintExclusionRules.match([&](const char *attribute) { return el->get_attr(attribute); }, active);
        if(matched & (lhx::PaintExclusionRules::paint | lhx::PaintExclusionRules::layer))
            _doc->skip_paint.insert(el.get());
        if(matched & lhx::PaintExclusionRules::scroll)
            _doc->skip_scroll_content.insert(el.get());
        if(matched & lhx::PaintExclusionRules::background)
            _doc->skip_background_images.insert(el.get());
        for(const auto &child : el->children())
            visit(child);
    };
    visit(root);
}

- (void)setScrollContentExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!LHExclusionStateMatches(_scrollExclusions, _scrollAttribute, values, attribute))
    {
        _paintExclusionCache.clear();
        _paintExclusionRulesValid = NO;
    }
    _scrollExclusions = [values copy];
    _scrollAttribute  = [attribute copy];
}

- (NSDictionary<NSString *, NSDictionary<NSString *, id> *> *)scrollersByAttribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    NSMutableDictionary *result = [NSMutableDictionary dictionary];
    if(!_doc || !_doc->root_render())
        return result;
    const char *key = attribute.UTF8String;
    std::function<bool(const std::shared_ptr<lh::render_item>&)> hasSticky = [&](const std::shared_ptr<lh::render_item>& item) {
        if(item->css().get_position() == lh::element_position_sticky) return true;
        for(const auto& child : item->children())
            if(hasSticky(child)) return true;
        return false;
    };
    std::function<void(const std::shared_ptr<lh::render_item> &)> visit = [&](const std::shared_ptr<lh::render_item> &ri) {
        const lh::scroll_view *view = ri->get_scroll_view();
        const char            *v    = ri->src_el()->get_attr(key);
        if(view && v && ri->is_visible() && ri->src_el()->css().get_display() != lh::display_inline)
        {
            lh::position       placement = ri->get_placement();
            placement                   += ri->get_paddings();
            const auto        &css       = ri->src_el()->css();
            lh::position       border    = placement;
            border += ri->get_borders();
            lh::border_radiuses radius = css.get_borders().radius.calc_percents(border.width, border.height);
            radius -= ri->get_borders();
            const lh::size &content = view->get_scroll_size();
            CGRect hitViewport = CGRectMake(placement.x.value(), placement.y.value(), placement.width.value(), placement.height.value());
            bool escaped = css.get_position() == lh::element_position_fixed;
            for(auto parent = ri->parent(); parent; parent = parent->parent())
            {
                if(!parent->css().get_transform().empty() || parent->css().contains(lh::contain_layout | lh::contain_paint))
                    escaped = false;
                const bool viewportClip = escaped || !parent->parent();
                escaped = escaped || parent->css().get_position() == lh::element_position_fixed;
                if(parent->css().clips_contents() && !viewportClip)
                {
                    lh::position clip = parent->get_placement();
                    clip += parent->get_paddings();
                    hitViewport = CGRectIntersection(hitViewport, CGRectMake(clip.x.value(), clip.y.value(), clip.width.value(), clip.height.value()));
                }
            }
            if(CGRectIsNull(hitViewport))
                hitViewport = CGRectZero;
            result[@(v)] = @{
                @"wheel": @(css.get_overflow_x() == lh::overflow_auto || css.get_overflow_x() == lh::overflow_scroll || css.get_overflow_y() == lh::overflow_auto || css.get_overflow_y() == lh::overflow_scroll || ri->src_el()->tag() == lh::_textarea_),
                @"wheelX": @(css.get_overflow_x() == lh::overflow_auto || css.get_overflow_x() == lh::overflow_scroll || ri->src_el()->tag() == lh::_textarea_),
                @"wheelY": @(css.get_overflow_y() == lh::overflow_auto || css.get_overflow_y() == lh::overflow_scroll || ri->src_el()->tag() == lh::_textarea_),
                @"hitViewport": [NSValue valueWithRect:hitViewport],
                @"viewport": [NSValue valueWithRect:NSMakeRect(placement.x.value(), placement.y.value(), placement.width.value(), placement.height.value())],
                @"content": [NSValue valueWithSize:NSMakeSize(content.width.value(), content.height.value())],
                @"sticky": @(hasSticky(ri)),
                @"offset": [NSValue valueWithPoint:NSMakePoint(ri->get_scroll_left().value(), ri->get_scroll_top().value())],
                @"radii": @[@(radius.top_left_x.value()), @(radius.top_left_y.value()), @(radius.top_right_x.value()), @(radius.top_right_y.value()),
                            @(radius.bottom_right_x.value()), @(radius.bottom_right_y.value()), @(radius.bottom_left_x.value()), @(radius.bottom_left_y.value())],
            };
        }
        for(const auto &child : ri->children())
            visit(child);
    };
    visit(_doc->root_render());
    return result;
}

- (nullable NSDictionary<NSString *, NSNumber *> *)offsetMetricsWithAttribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return nil;
    auto origin = [](std::shared_ptr<lh::render_item> item) {
        CGPoint point = CGPointZero;
        for(; item; item = item->parent())
        {
            const auto sticky = item->sticky_offset();
            point.x += item->pos().x.value() + sticky.x.value();
            point.y += item->pos().y.value() + sticky.y.value();
            if(item->css().get_position() == lh::element_position_fixed)
                break;
        }
        return point;
    };
    CGPoint point = origin(ri);
    point.x -= ri->padding_left().value() + ri->border_left().value();
    point.y -= ri->padding_top().value() + ri->border_top().value();
    NSInteger parentId = 0;
    if(ri->css().get_position() != lh::element_position_fixed && ri->src_el()->tag() != lh::_body_ && ri->src_el()->tag() != lh::_html_)
    {
        for(auto parent = ri->parent(); parent; parent = parent->parent())
        {
            const auto& css = parent->css();
            const auto tag = parent->src_el()->tag();
            const bool positioned = css.get_position() != lh::element_position_static || !css.get_transform().empty() || css.contains(lh::contain_layout | lh::contain_paint);
            const bool table = ri->css().get_position() == lh::element_position_static && (tag == lh::_table_ || tag == lh::_td_ || tag == lh::_th_);
            if(!positioned && !table && tag != lh::_body_)
                continue;
            if(const char *value = parent->src_el()->get_attr(attribute.UTF8String))
                parentId = [@(value) integerValue];
            if(positioned || table)
            {
                const CGPoint parentOrigin = origin(parent);
                point.x -= parentOrigin.x - parent->padding_left().value();
                point.y -= parentOrigin.y - parent->padding_top().value();
            }
            break;
        }
    }
    NSMutableDictionary<NSString *, NSNumber *> *metrics = [@{
        @"offsetLeft": @(std::round(point.x)),
        @"offsetTop": @(std::round(point.y)),
        @"offsetParent": @(parentId),
    } mutableCopy];
    CGRect lines = CGRectNull;
    if(VisitLineBoxes(ri.get(), [&](CGRect line) { lines = CGRectUnion(lines, line); }))
    {
        const lh::position placement = ri->get_placement();
        metrics[@"offsetLeft"] = @(std::round(point.x + ri->padding_left().value() + ri->border_left().value() + lines.origin.x - placement.x.value()));
        metrics[@"offsetTop"] = @(std::round(point.y + ri->padding_top().value() + ri->border_top().value() + lines.origin.y - placement.y.value()));
        metrics[@"offsetWidth"] = @(std::round(lines.size.width));
        metrics[@"offsetHeight"] = @(std::round(lines.size.height));
    } else if(!ri->src_el()->is_inline() || ri->src_el()->is_inline_box() || ri->src_el()->is_replaced())
    {
        metrics[@"offsetWidth"] = @(std::round(ri->pos().width.value() + ri->get_paddings().width().value() + ri->get_borders().width().value()));
        metrics[@"offsetHeight"] = @(std::round(ri->pos().height.value() + ri->get_paddings().height().value() + ri->get_borders().height().value()));
    }
    return metrics;
}

- (NSArray<NSValue *> *)lineBoxesWithAttribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    NSMutableArray<NSValue *> *result = [NSMutableArray array];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return result;
    const CGAffineTransform transform = VisualTransform(ri.get());
    VisitLineBoxes(ri.get(), [&](CGRect line) { [result addObject:[NSValue valueWithRect:NSRectFromCGRect(CGRectApplyAffineTransform(line, transform))]]; });
    return result;
}

- (nullable NSDictionary<NSString *, NSNumber *> *)textareaMetricsWithAttribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri || ri->src_el()->tag() != lh::_textarea_ || !ri->is_visible())
        return nil;
    auto text = std::dynamic_pointer_cast<lh::render_item_inline_context>(ri);
    if(!text)
        return nil;
    const auto& padding = ri->get_paddings();
    const auto& borders = ri->get_borders();
    const auto content = text->text_content_size();
    const float width = ri->pos().width.value() + padding.width().value();
    const float height = ri->pos().height.value() + padding.height().value();
    return @{
        @"clientWidth": @(std::round(width)),
        @"clientHeight": @(std::round(height)),
        @"clientLeft": @(std::round(borders.left.value())),
        @"clientTop": @(std::round(borders.top.value())),
        @"scrollWidth": @(std::round(std::max(width, content.width.value() + padding.width().value()))),
        @"scrollHeight": @(std::round(std::max(height, content.height.value() + padding.height().value()))),
    };
}

- (CGPoint)setScrollOffset:(CGPoint)offset attribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    std::string cacheKey = std::string(attribute.UTF8String) + "\x01" + value.UTF8String;
    std::shared_ptr<lh::render_item> ri;
    if(auto it = _scrollItems.find(cacheKey); it != _scrollItems.end())
    {
        ri = it->second.lock();
        if(ri)
        {
            auto top = ri;
            while(auto up = top->parent())
                top = up;
            const char *v = ri->src_el()->get_attr(attribute.UTF8String);
            if(top != _doc->root_render() || !v || strcmp(v, value.UTF8String) != 0)
                ri = nullptr;
        }
    }
    if(!ri)
    {
        ri = [self renderItemLockedWithAttribute:attribute value:value];
        if(ri)
            _scrollItems[cacheKey] = ri;
    }
    if(!ri || !ri->get_scroll_view())
        return CGPointZero;
    ri->scroll_to(lh::pixel_t(static_cast<float>(offset.x)), lh::pixel_t(static_cast<float>(offset.y)));
    _coverBoxesValid = NO;
    return CGPointMake(ri->get_scroll_left().value(), ri->get_scroll_top().value());
}

- (CGImageRef)copyScrollContentWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect)rect scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    return [self copyScrollContentLockedWithAttribute:attribute value:value rect:rect scale:scale];
}

- (CGImageRef)copyScrollContentIfCurrentWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect)rect scale:(CGFloat)scale shouldContinue:(BOOL (NS_NOESCAPE ^)(void))shouldContinue
{
    std::lock_guard<std::mutex> guard(_lock);
    if(_consumed || !_doc || !shouldContinue())
        return nullptr;
    return [self copyScrollContentLockedWithAttribute:attribute value:value rect:rect scale:scale];
}

- (CGImageRef)copyScrollContentLockedWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect)rect scale:(CGFloat)scale
{
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri || !ri->get_scroll_view())
        return nullptr;
    const size_t pw = (size_t)std::ceil(rect.size.width * scale), ph = (size_t)std::ceil(rect.size.height * scale);
    if(pw == 0 || ph == 0 || pw > 8192 || ph > 8192)
        return nullptr;
    LHImmutableBitmap bitmap([self createContextWithWidth:pw height:ph]);
    CGContextRef ctx = bitmap.context();
    if(!ctx)
        return nullptr;
    const lh::position placement = ri->get_placement();
    const lh::pixel_t  x         = placement.x - ri->pos().x;
    const lh::pixel_t  y         = placement.y - ri->pos().y;
    lh::position viewport = placement;
    viewport += ri->get_paddings();
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, scale, -scale);
    CGContextTranslateCTM(ctx, -(viewport.x.value() + rect.origin.x), -(viewport.y.value() + rect.origin.y));
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    _snapshotDepth++;
    [self resolveExclusionsLockedInRoot:ri->src_el()];
    _snapshotDepth--;
    _doc->skip_scroll_content.erase(ri->src_el().get());
    const lh::pixel_t left = ri->get_scroll_left(), top = ri->get_scroll_top();
    ri->scroll_to(0, 0);
    const lh::element *savedUnclipped = _doc->unclipped_scroll;
    _doc->unclipped_scroll            = ri->src_el().get();
    const bool savedCull              = _doc->culls_paint();
    _doc->set_culls_paint(true);
    lh::position clip(static_cast<float>(viewport.x.value() + rect.origin.x), static_cast<float>(viewport.y.value() + rect.origin.y), static_cast<float>(rect.size.width), static_cast<float>(rect.size.height));
    {
        PixelSnap snap(ctx);
        std::set<int> z;
        CollectPositionedZ(ri, z);
        auto hdc = reinterpret_cast<lh::uint_ptr>(ctx);
        const bool batchText = LHScrollTextBatchingSafe(ri);
        if(batchText)
            _container->begin_text_batching(ctx);
        for(int index : z)
            if(index < 0)
                ri->draw_children(hdc, x, y, &clip, lh::draw_positioned, index);
        ri->draw_children(hdc, x, y, &clip, lh::draw_block, 0);
        ri->draw_children(hdc, x, y, &clip, lh::draw_floats, 0);
        ri->draw_children(hdc, x, y, &clip, lh::draw_inlines, 0);
        for(int index : z)
            if(index >= 0)
                ri->draw_children(hdc, x, y, &clip, lh::draw_positioned, index);
        if(batchText)
            _container->end_text_batching();
    }
    _doc->set_culls_paint(savedCull);
    _doc->unclipped_scroll = savedUnclipped;
    ri->scroll_to(left, top);
    _coverBoxesValid = NO;
    return bitmap.takeImage(getenv("LH_NO_IMMUTABLE_SCROLL_BITMAP") == nullptr);
}

- (void)setBackgroundImageExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!LHExclusionStateMatches(_backgroundExclusions, _backgroundAttribute, values, attribute))
    {
        _paintExclusionCache.clear();
        _paintExclusionRulesValid = NO;
    }
    _backgroundExclusions = [values copy];
    _backgroundAttribute  = [attribute copy];
}

- (NSInteger)restoreMismatches
{
    return _restoreMismatches;
}

- (NSSet<NSString *> *)layerExclusions
{
    std::lock_guard<std::mutex> guard(_lock);
    return _layerExclusions ?: [NSSet set];
}

- (void)setLayerExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!LHExclusionStateMatches(_layerExclusions, _layerAttribute, values, attribute))
    {
        _paintExclusionCache.clear();
        _paintExclusionRulesValid = NO;
    }
    _layerExclusions = [values copy];
    _layerAttribute  = [attribute copy];
}

- (void)setPaintExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!LHExclusionStateMatches(_exclusions, _exclusionAttribute, values, attribute))
    {
        _paintExclusionCache.clear();
        _paintExclusionRulesValid = NO;
    }
    _exclusions         = [values copy];
    _exclusionAttribute = [attribute copy];
}

- (void)setTextPaintExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    if(!LHExclusionStateMatches(_textExclusions, _textExclusionAttribute, values, attribute))
        _paintExclusionCache.clear();
    _textExclusions = [values copy];
    _textExclusionAttribute = [attribute copy];
    _paintTraceValid = NO;
}

- (std::shared_ptr<lh::render_item>)renderItemLockedWithAttribute:(NSString *)attribute value:(NSString *)value
{
    if(!_doc || !_doc->root_render())
        return nullptr;
    const char                      *key    = attribute.UTF8String;
    const std::string                wanted = value.UTF8String;
    if(!_itemIndexValid || _itemIndexAttribute != key)
    {
        _itemIndex.clear();
        _itemIndexAttribute = key;
        std::vector<std::shared_ptr<lh::render_item>> pending{_doc->root_render()};
        while(!pending.empty())
        {
            std::shared_ptr<lh::render_item> ri = std::move(pending.back());
            pending.pop_back();
            if(const char *v = ri->src_el()->get_attr(key))
                _itemIndex.emplace(v, ri);
            const auto &children = ri->children();
            for(auto child = children.rbegin(); child != children.rend(); ++child)
                pending.push_back(*child);
        }
        _itemIndexValid = YES;
    }
    auto hit = _itemIndex.find(wanted);
    if(hit == _itemIndex.end())
        return nullptr;
    if(auto ri = hit->second.lock())
    {
        const char *v = ri->src_el()->get_attr(key);
        if(v && wanted == v)
            return ri;
    }
    _itemIndexValid = NO;
    std::shared_ptr<lh::render_item> found;
    std::function<void(const std::shared_ptr<lh::render_item> &)> visit = [&](const std::shared_ptr<lh::render_item> &ri) {
        if(found)
            return;
        const char *v = ri->src_el()->get_attr(key);
        if(v && wanted == v)
        {
            found = ri;
            return;
        }
        for(const auto &child : ri->children())
            visit(child);
    };
    visit(_doc->root_render());
    return found;
}

static CGRect BorderBox(const lh::render_item *ri)
{
    const lh::position p      = ri->get_placement();
    const CGFloat      left   = ri->padding_left().value() + ri->border_left().value();
    const CGFloat      top    = ri->padding_top().value() + ri->border_top().value();
    const CGFloat      right  = ri->padding_right().value() + ri->border_right().value();
    const CGFloat      bottom = ri->padding_bottom().value() + ri->border_bottom().value();
    return CGRectMake(p.x.value() - left, p.y.value() - top, p.width.value() + left + right, p.height.value() + top + bottom);
}

static CGRect BorderBox(const std::shared_ptr<lh::render_item> &ri)
{
    return BorderBox(ri.get());
}

static CGRect ClippedBorderBox(const lh::render_item *ri)
{
    CGRect box = BorderBox(ri);
    bool escaped = ri->src_el()->css().get_position() == lh::element_position_fixed;
    for(auto p = ri->parent(); p && !CGRectIsNull(box); p = p->parent())
    {
        if(escaped || !p->parent())
            break;
        escaped = p->src_el()->css().get_position() == lh::element_position_fixed;
        if(!p->src_el()->css().clips_contents())
            continue;
        CGRect border = BorderBox(p);
        CGFloat bl = p->border_left().value(), bt = p->border_top().value(), br = p->border_right().value(), bb = p->border_bottom().value();
        box = CGRectIntersection(box, CGRectMake(border.origin.x + bl, border.origin.y + bt, border.size.width - bl - br, border.size.height - bt - bb));
    }
    return CGRectIsNull(box) ? CGRectZero : box;
}

static CGRect TightInk(CGContextRef ctx, CGRect ink, CGRect box, CGFloat scale)
{
    static const bool disabled = getenv("LH_NO_TIGHT_INK") != nullptr;
    if(disabled)
        return ink;
    CGFloat step = 0;
    for(CGFloat k : {1.0, 2.0, 4.0, 8.0})
    {
        if(std::fabs(k * scale - std::round(k * scale)) < 1e-6)
        {
            step = k;
            break;
        }
    }
    const uint8_t* data = static_cast<const uint8_t*>(CGBitmapContextGetData(ctx));
    const size_t   pw = CGBitmapContextGetWidth(ctx), ph = CGBitmapContextGetHeight(ctx), row = CGBitmapContextGetBytesPerRow(ctx);
    if(step == 0 || !data || CGBitmapContextGetBitsPerPixel(ctx) != 32 || (CGBitmapContextGetBitmapInfo(ctx) & kCGBitmapAlphaInfoMask) != kCGImageAlphaPremultipliedFirst || (CGBitmapContextGetBitmapInfo(ctx) & kCGBitmapByteOrderMask) != kCGBitmapByteOrder32Little)
        return ink;
    lhx::Alpha8Bounds bounds;
    if(ph != 0 && row > std::numeric_limits<size_t>::max() / ph)
        return ink;
    if(!lhx::alpha8Bounds<4, 3>(data, row * ph, pw, ph, row, bounds))
        return ink;
    CGRect painted = bounds.empty() ? CGRectNull : CGRectMake(ink.origin.x + bounds.left / scale, ink.origin.y + bounds.top / scale, (bounds.right - bounds.left) / scale, (bounds.bottom - bounds.top) / scale);
    CGRect wanted = CGRectIntersection(CGRectUnion(painted, box), ink);
    if(CGRectIsNull(wanted) || CGRectIsEmpty(wanted))
        return ink;
    CGFloat x0 = ink.origin.x + std::floor((CGRectGetMinX(wanted) - ink.origin.x) / step) * step;
    CGFloat y0 = ink.origin.y + std::floor((CGRectGetMinY(wanted) - ink.origin.y) / step) * step;
    CGFloat x1 = std::min(CGRectGetMaxX(ink), ink.origin.x + std::ceil((CGRectGetMaxX(wanted) - ink.origin.x) / step) * step);
    CGFloat y1 = std::min(CGRectGetMaxY(ink), ink.origin.y + std::ceil((CGRectGetMaxY(wanted) - ink.origin.y) / step) * step);
    return CGRectMake(x0, y0, x1 - x0, y1 - y0);
}

static CGRect LHElementRasterBounds(const std::shared_ptr<lh::render_item>& root, bool bareShadows)
{
    const CGRect box = BorderBox(root);
    const auto placement = root->get_placement();
    lh::position redraw(static_cast<float>(box.origin.x), static_cast<float>(box.origin.y), static_cast<float>(box.size.width), static_cast<float>(box.size.height));
    root->get_redraw_box(redraw, placement.x - root->pos().x, placement.y - root->pos().y);
    CGFloat slack = 4;
    size_t visited = 0;
    std::vector<std::shared_ptr<lh::render_item>> pending{root};
    while(!pending.empty())
    {
        auto item = std::move(pending.back());
        pending.pop_back();
        const auto &css = item->css();
        const auto el = item->src_el();
        if(++visited > 512 || !css.get_filter().empty() || !css.get_backdrop_filter().empty() ||
           !css.get_clip_path().empty() || (item != root && !css.get_transform().empty()) ||
           (css.get_outline().style != lh::border_style_none && css.get_outline().style != lh::border_style_hidden) ||
           (!el->is_text() && std::string_view(el->get_tagName()) != "div" && std::string_view(el->get_tagName()) != "span"))
        {
            slack = std::max<CGFloat>(slack, 48);
            break;
        }
        if(el->is_text())
            slack = std::max(slack, std::ceil(BorderBox(item).size.height) / 2 + 2);
        auto shadows = [&](const lh::box_shadow_vector &values) {
            for(const auto &shadow : values)
            {
                if(shadow.inset)
                    continue;
                const CGFloat x = shadow.x.calc_percent(0).value(), y = shadow.y.calc_percent(0).value();
                const CGFloat blur = shadow.blur.calc_percent(0).value(), spread = shadow.spread.calc_percent(0).value();
                slack = std::max(slack, blur * 2 + std::fabs(spread) + std::max(std::fabs(x), std::fabs(y)) + 4);
            }
        };
        if(item != root || !bareShadows)
            shadows(css.get_box_shadows());
        shadows(css.get_text_shadows());
        for(const auto &child : item->children())
            pending.push_back(child);
    }
    return CGRectIntegral(CGRectInset(CGRectUnion(box, CGRectMake(redraw.x.value(), redraw.y.value(), redraw.width.value(), redraw.height.value())), -slack, -slack));
}

- (CGRect)rasterBoundsOfElementWithAttribute:(NSString *)attribute value:(NSString *)value bareShadows:(BOOL)bareShadows
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    return ri ? LHElementRasterBounds(ri, bareShadows) : CGRectNull;
}

- (CGImageRef)copyImageOfElementWithAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale inkBounds:(CGRect *)inkOut borderBox:(CGRect *)boxOut
{
    return [self copyImageOfElementWithAttribute:attribute value:value scale:scale inkBounds:inkOut borderBox:boxOut radii:nullptr];
}

- (CGImageRef)copyImageOfElementWithAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale inkBounds:(CGRect *)inkOut borderBox:(CGRect *)boxOut radii:(CGFloat *)radiiOut
{
    return [self copyImageOfElementWithAttribute:attribute value:value scale:scale inkBounds:inkOut borderBox:boxOut radii:radiiOut bareShadows:NO];
}

- (CGImageRef)copyImageOfElementWithAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale inkBounds:(CGRect *)inkOut borderBox:(CGRect *)boxOut radii:(CGFloat *)radiiOut bareShadows:(BOOL)bareShadows
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return nullptr;
    const CGRect       box       = BorderBox(ri);
    const lh::position placement = ri->get_placement();
    const lh::pixel_t  x         = placement.x - ri->pos().x;
    const lh::pixel_t  y         = placement.y - ri->pos().y;
    CGRect ink = LHElementRasterBounds(ri, bareShadows);
    const size_t pw = (size_t)std::ceil(ink.size.width * scale), ph = (size_t)std::ceil(ink.size.height * scale);
    if(pw == 0 || ph == 0 || pw > 8192 || ph > 8192 || pw * ph > 16u * 1024u * 1024u)
        return nullptr;
    CGContextRef ctx = [self createContextWithWidth:pw height:ph];
    if(!ctx)
        return nullptr;
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, scale, -scale);
    CGContextTranslateCTM(ctx, -ink.origin.x, -ink.origin.y);
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    auto savedPaint = _doc->skip_paint;
    auto savedScroll = _doc->skip_scroll_content;
    auto savedBackgrounds = _doc->skip_background_images;
    _snapshotDepth++;
    [self resolveExclusionsLockedInRoot:ri->src_el()];
    _snapshotDepth--;
    _doc->skip_paint.erase(ri->src_el().get());
    IncludeScrollSubtree(*_doc, ri->src_el().get());
    PixelSnap snap(ctx);
    _doc->bare_effects = ri->src_el().get();
    _doc->bare_shadows = bareShadows;
    ri->src_el()->draw(reinterpret_cast<lh::uint_ptr>(ctx), x, y, nullptr, ri);
    if(ri->is_visible())
        DrawAsStackingContext(ri, reinterpret_cast<lh::uint_ptr>(ctx), x, y);
    _doc->bare_effects = nullptr;
    _doc->bare_shadows = false;
    _doc->skip_paint = std::move(savedPaint);
    _doc->skip_scroll_content = std::move(savedScroll);
    _doc->skip_background_images = std::move(savedBackgrounds);
    CGRect tight = TightInk(ctx, ink, box, scale);
    CGImageRef image = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    if(image && !CGRectEqualToRect(tight, ink))
    {
        CGRect pixels = CGRectMake(std::round((tight.origin.x - ink.origin.x) * scale), std::round((tight.origin.y - ink.origin.y) * scale), std::round(tight.size.width * scale), std::round(tight.size.height * scale));
        pixels = CGRectIntersection(pixels, CGRectMake(0, 0, pw, ph));
        CGImageRef cropped = CGRectIsEmpty(pixels) ? nullptr : CGImageCreateWithImageInRect(image, pixels);
        if(cropped)
        {
            CGImageRelease(image);
            image = cropped;
            ink   = tight;
        }
    }
    if(inkOut)
        *inkOut = ink;
    if(boxOut)
        *boxOut = box;
    if(radiiOut)
    {
        auto r = ri->src_el()->css().get_borders().radius.calc_percents(lh::pixel_t(static_cast<float>(box.size.width)), lh::pixel_t(static_cast<float>(box.size.height)));
        r.fix_values(lh::pixel_t(static_cast<float>(box.size.width)), lh::pixel_t(static_cast<float>(box.size.height)));
        const CGFloat values[8] = {r.top_left_x.value(), r.top_left_y.value(), r.top_right_x.value(), r.top_right_y.value(), r.bottom_right_x.value(), r.bottom_right_y.value(), r.bottom_left_x.value(), r.bottom_left_y.value()};
        for(int i = 0; i < 8; i++)
            radiiOut[i] = std::max<CGFloat>(0, values[i]);
    }
    return image;
}

- (NSData *)localPaintKeyForAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri || !std::isfinite(scale) || scale < 1 || scale > 16)
        return nil;
    static const std::unordered_set<std::string> tags{"div", "span", "p", "section", "article", "header", "footer", "main", "aside", "nav", "b", "strong", "i", "em", "small", "label", "code", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "br"};
    std::vector<std::shared_ptr<lh::render_item>> pending{ri};
    size_t count = 0;
    while(!pending.empty())
    {
        auto item = std::move(pending.back());
        pending.pop_back();
        if(++count > 512)
            return nil;
        const auto &css = item->css();
        if(!css.get_filter().empty() || !css.get_backdrop_filter().empty() || !css.get_clip_path().empty() ||
           css.get_position() == lh::element_position_fixed || css.get_position() == lh::element_position_sticky ||
           (item != ri && css.is_paint_group()))
            return nil;
        if(!dynamic_cast<lh::el_text *>(item->src_el().get()) && !tags.count(item->src_el()->get_tagName()))
            return nil;
        for(const auto &child : item->children())
            pending.push_back(child);
    }
    const CGRect box = BorderBox(ri);
    lhx::LocalPaintKey key(box.origin.x, box.origin.y, scale);
    auto savedPaint = _doc->skip_paint;
    auto savedScroll = _doc->skip_scroll_content;
    auto savedBackgrounds = _doc->skip_background_images;
    _snapshotDepth++;
    [self resolveExclusionsLockedInRoot:ri->src_el()];
    _snapshotDepth--;
    _doc->skip_paint.erase(ri->src_el().get());
    IncludeScrollSubtree(*_doc, ri->src_el().get());
    const lh::position placement = ri->get_placement();
    const lh::pixel_t x = placement.x - ri->pos().x;
    const lh::pixel_t y = placement.y - ri->pos().y;
    _container->set_painter(&key);
    _doc->bare_effects = ri->src_el().get();
    {
        PixelSnap snap(scale);
        ri->src_el()->draw(lh::uint_ptr(0), x, y, nullptr, ri);
        if(ri->is_visible())
            DrawAsStackingContext(ri, lh::uint_ptr(0), x, y);
    }
    _doc->bare_effects = nullptr;
    _container->set_painter(nullptr);
    _doc->skip_paint = std::move(savedPaint);
    _doc->skip_scroll_content = std::move(savedScroll);
    _doc->skip_background_images = std::move(savedBackgrounds);
    if(!key.valid())
        return nil;
    return [NSData dataWithBytes:key.bytes().data() length:key.bytes().size()];
}

- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value isolatedInRect:(CGRect)rect
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri || (ri->src_el()->is_inline() && !ri->src_el()->is_inline_box()))
        return NO;
    std::unordered_set<const lh::render_item *> ancestors;
    for(auto p = ri->parent(); p; p = p->parent())
    {
        ancestors.insert(p.get());
        if(p->src_el()->css().clips_contents() || p->src_el()->css().is_paint_group())
            return NO;
    }
    bool isolated = true;
    std::function<void(const std::shared_ptr<lh::render_item> &)> visit = [&](const std::shared_ptr<lh::render_item> &item) {
        if(!isolated || item == ri)
            return;
        if(!ancestors.count(item.get()) && !std::dynamic_pointer_cast<lh::el_space>(item->src_el()))
        {
            CGRect box = BorderBox(item);
            if(box.size.width > 0 && box.size.height > 0 && CGRectIntersectsRect(box, rect))
            {
                isolated = false;
                return;
            }
        }
        for(const auto &child : item->children())
            visit(child);
    };
    visit(_doc->root_render());
    return isolated;
}

- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect
{
    return [self isElementWithAttribute:attribute value:value unobscuredInRect:rect ignoring:[NSSet set]];
}

- (BOOL)backgroundColorIsLocalWithAttribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    auto tag = ri ? dynamic_cast<lh::html_tag *>(ri->src_el().get()) : nullptr;
    if(!tag || !tag->background_color_is_local())
        return NO;
    switch(tag->tag())
    {
    case lh::_input_:
    case lh::_select_:
    case lh::_textarea_:
    case lh::_button_:
    case lh::_meter_:
    case lh::_progress_:
    case lh::_fieldset_:
        return NO;
    default:
        break;
    }
    std::vector<const lh::element *> pending{tag};
    while(!pending.empty())
    {
        const lh::element *el = pending.back();
        pending.pop_back();
        if(!el->css().get_backdrop_filter().empty())
            return NO;
        for(const auto& child : el->children())
            pending.push_back(child.get());
    }
    return YES;
}

- (NSDictionary<NSString *, NSNumber *> *)paintOrderOfAttribute:(NSString *)attribute values:(NSSet<NSString *> *)values
{
    return [self paintOrderOfAttribute:attribute values:values includingScrollContent:NO];
}

- (NSDictionary<NSString *, NSNumber *> *)paintOrderOfAttribute:(NSString *)attribute values:(NSSet<NSString *> *)values includingScrollContent:(BOOL)includingScrollContent
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    NSMutableDictionary<NSString *, NSNumber *> *result = [NSMutableDictionary dictionary];
    if(!_doc || !_doc->root_render() || values.count == 0)
        return result;
    if(includingScrollContent)
    {
        if(![self ensurePaintTraceLocked])
            return result;
        const char *key = attribute.UTF8String;
        for(size_t index = 0; index < _paintTrace.size(); index++)
        {
            const char *v = _paintTrace[index]->src_el()->get_attr(key);
            if(!v)
                continue;
            NSString *string = @(v);
            if([values containsObject:string] && !result[string])
                result[string] = @(index);
        }
        return result;
    }
    std::vector<const lh::render_item *> trace;
    trace.reserve(512);
    CGContextRef ctx = [self createContextWithWidth:1 height:1];
    if(!ctx)
        return result;
    [self resolveExclusionsLocked];
    auto saved        = _doc->skip_paint;
    _doc->skip_paint.clear();
    lh::g_paint_trace = &trace;
    _doc->draw(reinterpret_cast<lh::uint_ptr>(ctx), 0, 0, nullptr);
    lh::g_paint_trace = nullptr;
    _doc->skip_paint  = saved;
    CGContextRelease(ctx);
    const char *key = attribute.UTF8String;
    for(size_t index = 0; index < trace.size(); index++)
    {
        const char *v = trace[index]->src_el()->get_attr(key);
        if(!v)
            continue;
        NSString *string = @(v);
        if([values containsObject:string] && !result[string])
            result[string] = @(index);
    }
    return result;
}

- (BOOL)clipOfElementWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect *)rect radii:(CGFloat *)radii
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return NO;
    std::shared_ptr<lh::render_item> clip;
    std::vector<std::shared_ptr<lh::render_item>> outer;
    bool escaped = ri->src_el()->css().get_position() == lh::element_position_fixed;
    for(auto p = ri->parent(); p; p = p->parent())
    {
        const bool viewportClip = escaped || !p->parent();
        escaped = escaped || p->src_el()->css().get_position() == lh::element_position_fixed;
        if(p->src_el()->css().clips_contents() && !viewportClip)
        {
            if(clip)
                outer.push_back(p);
            else
                clip = p;
        }
    }
    if(!clip)
        return NO;
    auto paddingBox = [](const std::shared_ptr<lh::render_item> &item) {
        CGRect border = BorderBox(item);
        CGFloat bl = item->border_left().value(), bt = item->border_top().value(), br = item->border_right().value(), bb = item->border_bottom().value();
        return CGRectMake(border.origin.x + bl, border.origin.y + bt, border.size.width - bl - br, border.size.height - bt - bb);
    };
    for(const auto &p : outer)
    {
        CGRect border = BorderBox(p);
        const auto &radius = p->src_el()->css().get_borders().radius.calc_percents(lh::pixel_t(static_cast<float>(border.size.width)), lh::pixel_t(static_cast<float>(border.size.height)));
        CGFloat corner = std::max({radius.top_left_x.value(), radius.top_left_y.value(), radius.top_right_x.value(), radius.top_right_y.value(),
                                   radius.bottom_left_x.value(), radius.bottom_left_y.value(), radius.bottom_right_x.value(), radius.bottom_right_y.value()});
        if(!CGRectContainsRect(CGRectInset(paddingBox(p), corner, corner), paddingBox(clip)))
            return NO;
    }
    CGRect border = BorderBox(clip);
    CGFloat bl = clip->border_left().value(), bt = clip->border_top().value(), br = clip->border_right().value(), bb = clip->border_bottom().value();
    *rect = CGRectMake(border.origin.x + bl, border.origin.y + bt, border.size.width - bl - br, border.size.height - bt - bb);
    const auto r = clip->src_el()->css().get_borders().radius.calc_percents(lh::pixel_t(static_cast<float>(border.size.width)), lh::pixel_t(static_cast<float>(border.size.height)));
    CGFloat values[8] = {r.top_left_x.value() - bl, r.top_left_y.value() - bt, r.top_right_x.value() - br, r.top_right_y.value() - bt,
                         r.bottom_right_x.value() - br, r.bottom_right_y.value() - bb, r.bottom_left_x.value() - bl, r.bottom_left_y.value() - bb};
    for(int i = 0; i < 8; i++)
        radii[i] = std::max<CGFloat>(0, values[i]);
    return YES;
}

- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring
{
    return [self isElementWithAttribute:attribute value:value unobscuredInRect:rect ignoring:ignoring clipped:NO];
}

- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring clipped:(BOOL)clipped
{
    NSArray<NSString *> *obscuring = [self elementsObscuringAttribute:attribute value:value inRect:rect ignoring:ignoring clipped:clipped];
    return obscuring != nil && obscuring.count == 0;
}

- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect ignoringSet:(NSObject *)ignoring clipped:(BOOL)clipped
{
    NSArray<NSString *> *obscuring = [self elementsObscuringAttribute:attribute value:value inRect:rect ignoringSet:ignoring clipped:clipped];
    return obscuring != nil && obscuring.count == 0;
}

- (nullable NSArray<NSString *> *)elementsObscuringAttribute:(NSString *)attribute value:(NSString *)value inRect:(CGRect)rect ignoringSet:(NSObject *)ignoring clipped:(BOOL)clipped
{
    return [self elementsObscuringAttribute:attribute value:value inRect:rect ignoring:[ignoring isKindOfClass:[NSSet class]] ? (NSSet<NSString *> *)ignoring : [NSSet set] clipped:clipped];
}

- (nullable NSArray<NSString *> *)elementsObscuringAttribute:(NSString *)attribute value:(NSString *)value inRect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring clipped:(BOOL)clipped
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri || (ri->src_el()->is_inline() && !ri->src_el()->is_inline_box()) || CGRectIsNull(rect) || CGRectIsEmpty(rect))
        return nil;
    for(auto p = ri; p; p = p->parent())
        if(!p->css().get_clip_path().empty() || p->css().get_position() == lh::element_position_sticky) return nil;
    bool escaped = ri->src_el()->css().get_position() == lh::element_position_fixed;
    for(auto p = ri->parent(); p; p = p->parent())
    {
        if(p->src_el()->css().is_paint_group())
            return nil;
        const bool viewportClip = escaped || !p->parent();
        escaped = escaped || p->src_el()->css().get_position() == lh::element_position_fixed;
        if(p->src_el()->css().clips_contents() && !clipped && !viewportClip)
        {
            CGRect border = BorderBox(p);
            CGFloat bl = p->border_left().value(), bt = p->border_top().value(), br = p->border_right().value(), bb = p->border_bottom().value();
            CGRect inner  = CGRectMake(border.origin.x + bl, border.origin.y + bt, border.size.width - bl - br, border.size.height - bt - bb);
            const auto &radius = p->src_el()->css().get_borders().radius.calc_percents(lh::pixel_t(static_cast<float>(border.size.width)), lh::pixel_t(static_cast<float>(border.size.height)));
            CGFloat corner = std::max({radius.top_left_x.value(), radius.top_left_y.value(), radius.top_right_x.value(), radius.top_right_y.value(),
                                       radius.bottom_left_x.value(), radius.bottom_left_y.value(), radius.bottom_right_x.value(), radius.bottom_right_y.value()});
            CGRect safe = CGRectInset(inner, corner, corner);
            if(!CGRectContainsRect(safe, rect))
                return nil;
        }
    }
    if(![self ensurePaintTraceLocked])
        return nil;
    const char *key = attribute.UTF8String;
    if([self ensureCoverLocked:key])
        return [self coveringLockedWithItem:ri.get() rect:rect ignoring:ignoring];
    const std::vector<const lh::render_item *> &trace = _paintTrace;
    NSMutableOrderedSet<NSString *> *found = [NSMutableOrderedSet orderedSet];
    std::unordered_set<std::string> local;
    const std::unordered_set<std::string> *ignoredRef = nullptr;
    auto self_it = std::find(trace.begin(), trace.end(), ri.get());
    if(self_it == trace.end())
        return nil;
    for(auto it = std::next(self_it); it != trace.end(); ++it)
    {
        const lh::render_item *item = *it;
        CGRect box = ClippedBorderBox(item);
        if(!(box.size.width > 0 && box.size.height > 0 && CGRectIntersectsRect(box, rect)))
            continue;
        bool descendant = false;
        for(auto p = item->parent(); p; p = p->parent())
        {
            if(p.get() == ri.get())
            {
                descendant = true;
                break;
            }
        }
        if(descendant || std::dynamic_pointer_cast<lh::el_space>(item->src_el()))
            continue;
        if(item->src_el()->css().get_visibility() != lh::visibility_visible)
            continue;
        bool transparent = false;
        for(const lh::render_item *walk = item; walk && !transparent; walk = walk->parent().get())
            transparent = walk->src_el()->css().get_opacity() <= 0;
        if(transparent)
            continue;
        if(!ignoredRef)
        {
            const bool reusable = ![ignoring isKindOfClass:[NSMutableSet class]];
            if(reusable && ignoring == _ignoredKey)
                ignoredRef = &_ignoredValues;
            else
            {
                std::unordered_set<std::string> &target = reusable ? _ignoredValues : local;
                target.clear();
                for(NSString *value in ignoring)
                    target.insert(value.UTF8String ?: "");
                if(reusable)
                    _ignoredKey = ignoring;
                ignoredRef = &target;
            }
        }
        const std::unordered_set<std::string> &ignored = *ignoredRef;
        if(!ignored.empty())
        {
            bool skipped = false;
            for(const lh::render_item *walk = item; walk && !skipped; walk = walk->parent().get())
            {
                const char *v = walk->src_el()->get_attr(key);
                if(v && ignored.count(v))
                    skipped = true;
            }
            if(skipped)
                continue;
        }
        {
            NSString *owner = nil;
            for(const lh::render_item *walk = item; walk; walk = walk->parent().get())
            {
                bool ancestorOfSelf = false;
                for(auto p = ri->parent(); p; p = p->parent())
                {
                    if(p.get() == walk)
                    {
                        ancestorOfSelf = true;
                        break;
                    }
                }
                if(ancestorOfSelf)
                    break;
                if(const char *v = walk->src_el()->get_attr(key))
                {
                    owner = @(v);
                    break;
                }
            }
            if(!owner)
                return nil;
            [found addObject:owner];
            if(found.count > 8)
                return nil;
        }
    }
    return found.array;
}

- (nullable NSArray<NSString *> *)coveringLockedWithItem:(const lh::render_item *)ri rect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring
{
    const LHCoverEntry *begin = _coverEntries.data(), *end = begin + _coverEntries.size();
    const LHCoverEntry *self_it = begin;
    while(self_it != end && self_it->item != ri)
        ++self_it;
    if(self_it == end)
        return nil;
    const int rpre = self_it->pre, rpost = self_it->post;
    NSMutableOrderedSet<NSString *> *found = nil;
    std::vector<uint8_t> local;
    const std::vector<uint8_t> *marks = nullptr;
    bool anyIgnored = false;
    for(const LHCoverEntry *entry = self_it + 1; entry != end; ++entry)
    {
        const CGRect box = entry->box;
        if(!(box.size.width > 0 && box.size.height > 0 && CGRectIntersectsRect(box, rect)))
            continue;
        if(entry->pre > rpre && entry->post < rpost)
            continue;
        if(!entry->eligible)
            continue;
        if(!marks)
        {
            const bool reusable = ![ignoring isKindOfClass:[NSMutableSet class]];
            std::vector<uint8_t> &target = reusable ? _coverIgnored : local;
            if(!(reusable && ignoring == _coverIgnoredKey))
            {
                target.assign(_coverValues.size(), 0);
                for(NSString *value in ignoring)
                {
                    auto id = _coverValueIds.find(value.UTF8String ?: "");
                    if(id != _coverValueIds.end())
                        target[id->second] = 1;
                }
                if(reusable)
                    _coverIgnoredKey = ignoring;
            }
            marks      = &target;
            anyIgnored = std::find(target.begin(), target.end(), 1) != target.end();
        }
        if(anyIgnored)
        {
            bool skipped = false;
            for(int node = entry->owner; node >= 0 && !skipped; node = _coverNodes[node].parent)
                skipped = (*marks)[_coverNodes[node].value] != 0;
            if(skipped)
                continue;
        }
        const int owner = entry->owner;
        if(owner < 0)
            return nil;
        const LHCoverNode &node = _coverNodes[owner];
        if(node.pre < rpre && rpost < node.post)
            return nil;
        if(!found)
            found = [NSMutableOrderedSet orderedSet];
        [found addObject:@(_coverValues[node.value].c_str())];
        if(found.count > 8)
            return nil;
    }
    return found ? found.array : @[];
}

- (void)drawLockedInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip
{
    [self restoreLocked];
    if(!_doc)
        return;
    [self resolveExclusionsLocked];
    CGContextSaveGState(ctx);
    CGContextTranslateCTM(ctx, origin.x, origin.y);
    lh::position c(static_cast<float>(clip.origin.x), static_cast<float>(clip.origin.y),
                   static_cast<float>(clip.size.width), static_cast<float>(clip.size.height));
    PixelSnap  snap(ctx);
    static const bool disabled = getenv("LH_NO_SUBTREE_CULL") != nullptr;
    const bool sourceFilters = _container->begin_source_filters();
    _container->begin_text_batching(ctx);
    _doc->draw(reinterpret_cast<lh::uint_ptr>(ctx), 0, 0, &c, !_noSubtreeCulling && !disabled && _container->rasterizes());
    _container->end_text_batching();
    if(sourceFilters)
        _container->end_source_filters();
    CGContextRestoreGState(ctx);
}

- (void)drawInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip
{
    std::lock_guard<std::mutex> guard(_lock);
    [self drawLockedInContext:ctx origin:origin clip:clip];
}

- (void)drawInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip baseTransform:(CGAffineTransform)baseTransform
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc)
        return;
    _container->m_shadow_base_context = ctx;
    _container->m_shadow_base_inverse = CGAffineTransformInvert(baseTransform);
    [self drawLockedInContext:ctx origin:origin clip:clip];
    _container->m_shadow_base_context = nullptr;
    _container->m_shadow_base_inverse = CGAffineTransformIdentity;
}

- (BOOL)drawIfCurrentInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip
{
    std::lock_guard<std::mutex> guard(_lock);
    if(_consumed || !_doc)
        return NO;
    [self drawLockedInContext:ctx origin:origin clip:clip];
    return YES;
}

- (CGImageRef)copyImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale base:(CGImageRef)base dirty:(CGRect)dirty
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    size_t pw = (size_t)std::ceil(rect.size.width * scale);
    size_t ph = (size_t)std::ceil(rect.size.height * scale);
    if(!_doc || pw == 0 || ph == 0)
        return nullptr;
    if(CGImageGetWidth(base) != pw || CGImageGetHeight(base) != ph)
        return [self copyImageLockedWithRect:rect scale:scale];
    CGRect local = CGRectIntersection(CGRectOffset(dirty, -rect.origin.x, -rect.origin.y),
                                      CGRectMake(0, 0, rect.size.width, rect.size.height));
    if(CGRectIsNull(local) || CGRectIsEmpty(local))
        return CGImageRetain(base);
    CGFloat x0 = std::floor(CGRectGetMinX(local) * scale) / scale, y0 = std::floor(CGRectGetMinY(local) * scale) / scale;
    CGFloat x1 = std::ceil(CGRectGetMaxX(local) * scale) / scale, y1 = std::ceil(CGRectGetMaxY(local) * scale) / scale;
    CGRect  aligned = CGRectMake(x0, y0, x1 - x0, y1 - y0);

    CGContextRef ctx = [self createContextWithWidth:pw height:ph];
    if(!ctx)
        return nullptr;
    if(!CopyPixels(base, ctx))
    {
        CGContextRelease(ctx);
        return [self copyImageLockedWithRect:rect scale:scale];
    }
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, scale, -scale);
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    CGContextClipToRect(ctx, aligned);
    CGContextClearRect(ctx, aligned);
    [self drawLockedInContext:ctx origin:CGPointMake(-rect.origin.x, -rect.origin.y) clip:rect];
    CGImageRef image = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    return image;
}

- (CGContextRef)createContextWithWidth:(size_t)pw height:(size_t)ph CF_RETURNS_RETAINED
{
    CGColorSpaceRef space = [self copyDrawingSpace];
    CGContextRef    ctx   = CGBitmapContextCreate(nullptr, pw, ph, 8, 0, space,
                                                  kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    return ctx;
}

- (nullable NSString *)attribute:(NSString *)name atPoint:(CGPoint)p
{
    return [self attribute:name atPoint:p withinAttribute:nil value:nil];
}

- (nullable NSString *)attribute:(NSString *)name atPoint:(CGPoint)p withinAttribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || !_doc->root_render())
        return nil;
    auto root = attribute && value ? [self renderItemLockedWithAttribute:attribute value:value] : _doc->root_render();
    if(!root)
        return nil;
    lh::pixel_t x = static_cast<float>(p.x), y = static_cast<float>(p.y);
    if(attribute && value)
    {
        const auto placement = root->get_placement();
        const auto sticky = root->sticky_offset();
        x -= placement.x - root->pos().x - sticky.x;
        y -= placement.y - root->pos().y - sticky.y;
    }
    const char*       key = name.UTF8String;
    static const lh::string_id pointerEvents = lh::raw_declaration_id(lh::_id("pointer-events"));
    const auto hittable = [](const std::shared_ptr<lh::render_item>& item) -> bool {
        for(lh::element::ptr el = item->src_el(); el; el = el->parent())
        {
            auto tag = std::dynamic_pointer_cast<lh::html_tag>(el);
            if(!tag)
                continue;
            const lh::css_token_vector* tokens = tag->get_token_property(pointerEvents);
            if(!tokens)
                continue;
            std::string value = lh::get_repr(*tokens, 0, -1, true);
            if(value.empty())
                continue;
            for(auto& c : value)
                c = static_cast<char>(tolower(static_cast<unsigned char>(c)));
            return value != "none";
        }
        return true;
    };
    lh::element::ptr hit = root->get_element_by_point(x, y, static_cast<float>(p.x), static_cast<float>(p.y), hittable);
    if(auto svg = std::dynamic_pointer_cast<lh::el_svg>(hit))
    {
        if(lh::element::ptr inner = svg->element_at(svg->get_placement(), static_cast<float>(p.x), static_cast<float>(p.y)))
            hit = inner;
    }
    for(lh::element::ptr el = hit; el; el = el->parent())
    {
        if(const char* value = el->get_attr(key))
            return @(value);
    }
    return nil;
}

- (NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *)declarations:(NSArray<NSString *> *)names byAttribute:(NSString *)attribute
{
    return [self declarations:names byAttribute:attribute requiringAny:@[]];
}

- (NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *)declarations:(NSArray<NSString *> *)names byAttribute:(NSString *)attribute requiringAny:(NSArray<NSString *> *)required
{
    uint64_t generation = 0;
    return [self declarations:names byAttribute:attribute requiringAny:required changedSince:0 generation:&generation];
}

- (NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *)declarations:(NSArray<NSString *> *)names byAttribute:(NSString *)attribute requiringAny:(NSArray<NSString *> *)required changedSince:(uint64_t)known generation:(uint64_t *)generation
{
    static std::atomic<uint64_t> generations{0};
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    *generation = 0;
    NSMutableDictionary *result = [NSMutableDictionary dictionary];
    if(!_doc || !_doc->root())
        return result;
    NSArray *cacheKey = @[names, attribute, required];
    const bool caching = !getenv("LH_NO_DECLARATION_CACHE");
    for(size_t i = 0; caching && i < _declarations.size(); i++)
    {
        if(_declarations[i].epoch != _doc->style_epoch() || ![_declarations[i].key isEqualToArray:cacheKey])
            continue;
        if(i > 0)
            std::rotate(_declarations.begin(), _declarations.begin() + i, _declarations.begin() + i + 1);
        *generation = _declarations[0].generation;
        return known != 0 && known == *generation ? nil : _declarations[0].value;
    }
    std::vector<std::pair<lh::string_id, NSString *>> keys;
    for(NSString *name in names)
    {
        const lh::string_id raw = lh::raw_declaration_id(lh::_id(name.UTF8String ?: ""));
        if(raw != lh::empty_id)
            keys.emplace_back(raw, name);
    }
    std::vector<lh::string_id> gates;
    for(NSString *name in required)
    {
        const lh::string_id raw = lh::raw_declaration_id(lh::_id(name.UTF8String ?: ""));
        if(raw != lh::empty_id)
            gates.push_back(raw);
    }
    if(required.count > 0 && gates.empty())
        return result;
    const char *key = attribute.UTF8String;
    std::vector<const lh::element *> pending{_doc->root().get()};
    while(!pending.empty())
    {
        const lh::element *el = pending.back();
        pending.pop_back();
        if(el->css().get_display() == lh::display_none)
            continue;
        if(auto tag = dynamic_cast<const lh::html_tag *>(el))
        {
            bool open = gates.empty();
            for(size_t i = 0; !open && i < gates.size(); i++)
            {
                const lh::css_token_vector *tokens = tag->get_token_property(gates[i]);
                open = tokens && !lh::get_repr(*tokens, 0, -1, true).empty();
            }
            const char *value = open ? tag->get_attr(key) : nullptr;
            if(value)
            {
                NSMutableDictionary<NSString *, NSString *> *found = nil;
                for(const auto &entry : keys)
                {
                    const lh::css_token_vector *tokens = tag->get_token_property(entry.first);
                    if(!tokens)
                        continue;
                    std::string raw = lh::get_repr(*tokens, 0, -1, true);
                    if(raw.empty())
                        continue;
                    if(!found)
                        found = [NSMutableDictionary dictionary];
                    found[entry.second] = @(raw.c_str());
                }
                if(found)
                    result[@(value)] = found;
            }
        }
        const auto &children = el->children();
        for(auto it = children.rbegin(); it != children.rend(); ++it)
            pending.push_back(it->get());
    }
    LHDeclarationsEntry entry{[result copy], cacheKey, _doc->style_epoch(), ++generations};
    _declarations.insert(_declarations.begin(), entry);
    if(_declarations.size() > 4)
        _declarations.resize(4);
    *generation = entry.generation;
    return entry.value;
}

- (nullable NSDictionary<NSString *, NSNumber *> *)svgGeometry:(NSString *)method at:(double)distance attribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || !_doc->root())
        return nil;
    const char       *key    = attribute.UTF8String;
    const std::string wanted = value.UTF8String;
    std::function<lh::element::ptr(const lh::element::ptr &, int)> search = [&](const lh::element::ptr &e, int depth) -> lh::element::ptr {
        const char *v = e->get_attr(key);
        if(v && wanted == v)
            return e;
        if(depth > 512)
            return nullptr;
        for(const auto &child : e->children())
            if(auto hit = search(child, depth + 1))
                return hit;
        return nullptr;
    };
    lh::element::ptr el = search(_doc->root(), 0);
    lh::position     text;
    if(el && ([method isEqualToString:@"getComputedTextLength"] || [method isEqualToString:@"getBBox"]) && lh::svg_text_box(el, _container.get(), text))
    {
        if([method isEqualToString:@"getComputedTextLength"])
            return @{@"length" : @(text.width.value())};
        return @{@"x" : @(text.x.value()), @"y" : @(text.y.value()), @"width" : @(text.width.value()), @"height" : @(text.height.value())};
    }
    lh::vector_path  path;
    if(!el || !lh::svg_geometry(el, path))
        return nil;
    if([method isEqualToString:@"getTotalLength"])
        return @{@"length" : @(lh::svg_path_length(path, -1, nullptr, nullptr))};
    if([method isEqualToString:@"getPointAtLength"])
    {
        float x = 0, y = 0;
        lh::svg_path_length(path, std::isfinite(distance) ? std::max(0.0, distance) : 0, &x, &y);
        return @{@"x" : @(x), @"y" : @(y)};
    }
    if([method isEqualToString:@"getBBox"])
    {
        float minX = INFINITY, minY = INFINITY, maxX = -INFINITY, maxY = -INFINITY;
        const int steps = 512;
        float     total = lh::svg_path_length(path, -1, nullptr, nullptr);
        for(int i = 0; i <= steps; i++)
        {
            float x = 0, y = 0;
            lh::svg_path_length(path, total * i / steps, &x, &y);
            minX = std::min(minX, x);
            minY = std::min(minY, y);
            maxX = std::max(maxX, x);
            maxY = std::max(maxY, y);
        }
        if(!std::isfinite(minX))
            return nil;
        return @{@"x" : @(minX), @"y" : @(minY), @"width" : @(maxX - minX), @"height" : @(maxY - minY)};
    }
    return nil;
}

- (CGRect)svgBoxWithAttribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || !_doc->root())
        return CGRectNull;
    const char       *key    = attribute.UTF8String;
    const std::string wanted = value.UTF8String;
    std::function<lh::element::ptr(const lh::element::ptr &, int)> search = [&](const lh::element::ptr &e, int depth) -> lh::element::ptr {
        const char *v = e->get_attr(key);
        if(v && wanted == v)
            return e;
        if(depth > 512)
            return nullptr;
        for(const auto &child : e->children())
            if(auto hit = search(child, depth + 1))
                return hit;
        return nullptr;
    };
    lh::element::ptr     el = search(_doc->root(), 0);
    lh::transform_matrix ctm;
    if(!el || el->get_render_item() || !lh::svg_ctm(el, ctm))
        return CGRectNull;
    CGRect          box = CGRectNull;
    lh::vector_path path;
    lh::position    text;
    if(lh::svg_geometry(el, path))
    {
        const int steps = 512;
        const float total = lh::svg_path_length(path, -1, nullptr, nullptr);
        for(int i = 0; i <= steps; i++)
        {
            float x = 0, y = 0;
            lh::svg_path_length(path, total * i / steps, &x, &y);
            box = CGRectUnion(box, CGRectMake(x, y, 0, 0));
        }
    } else if(lh::svg_text_box(el, _container.get(), text))
    {
        box = CGRectMake(text.x.value(), text.y.value(), text.width.value(), text.height.value());
    }
    if(CGRectIsNull(box) || !std::isfinite(box.origin.x))
        return CGRectNull;
    box = CGRectApplyAffineTransform(box, CGAffineTransformMake(ctm.a, ctm.b, ctm.c, ctm.d, ctm.tx, ctm.ty));
    for(lh::element::ptr p = el->parent(); p; p = p->parent())
    {
        if(auto ri = p->get_render_item())
            return CGRectApplyAffineTransform(box, VisualTransform(ri.get()));
    }
    return box;
}

- (nullable NSFont *)fontWithAttribute:(NSString *)attribute value:(NSString *)value lineHeight:(CGFloat *)lineHeight baseline:(CGFloat *)baseline
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    *lineHeight = ri ? ri->css().line_height().computed_value.value() : 0;
    *baseline = 0;
    if(ri)
    {
        const auto& metrics = ri->css().get_font_metrics();
        *baseline = metrics.ascent.value() + std::trunc((*lineHeight - metrics.height.value()) / 2);
    }
    return ri ? (__bridge NSFont *)LHContainer::native_font(ri->css().get_font()) : nil;
}

- (NSDictionary<NSString *, NSString *> *)computedStyleForAttribute:(NSString *)attribute value:(NSString *)value
{
    return [self computedStyleForAttribute:attribute value:value pseudo:nil];
}

- (NSDictionary<NSString *, NSString *> *)computedStyleForAttribute:(NSString *)attribute value:(NSString *)value pseudo:(nullable NSString *)pseudo
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || !_doc->root_render())
        return @{};
    const char                          *key    = attribute.UTF8String;
    const std::string                    wanted = value.UTF8String;
    std::shared_ptr<lh::render_item>     found;
    std::function<void(const std::shared_ptr<lh::render_item> &)> visit = [&](const std::shared_ptr<lh::render_item> &ri) {
        if(found)
            return;
        const char *v = ri->src_el()->get_attr(key);
        if(v && wanted == v)
        {
            found = ri;
            return;
        }
        for(const auto &child : ri->children())
            visit(child);
    };
    visit(_doc->root_render());
    lh::element::ptr el = found ? found->src_el() : nullptr;
    if(!el)
    {
        std::function<lh::element::ptr(const lh::element::ptr &)> search = [&](const lh::element::ptr &e) -> lh::element::ptr {
            const char *v = e->get_attr(key);
            if(v && wanted == v)
                return e;
            for(const auto &child : e->children())
                if(auto hit = search(child))
                    return hit;
            return nullptr;
        };
        el = _doc->root() ? search(_doc->root()) : nullptr;
    }
    if(!el)
        return @{};
    const bool before = [pseudo isEqualToString:@"::before"] || [pseudo isEqualToString:@":before"];
    const bool after  = [pseudo isEqualToString:@"::after"] || [pseudo isEqualToString:@":after"];
    if(before || after)
    {
        for(const auto &child : el->children())
        {
            if(before ? std::dynamic_pointer_cast<lh::el_before>(child) != nullptr : std::dynamic_pointer_cast<lh::el_after>(child) != nullptr)
            {
                el = child;
                break;
            }
        }
    }
    const lh::css_properties &css = el->css();
    auto tag = std::dynamic_pointer_cast<lh::html_tag>(el);
    NSMutableDictionary<NSString *, NSString *> *out = [NSMutableDictionary dictionary];
    auto px = [](double v) { return [NSString stringWithFormat:@"%gpx", v]; };
    const double fontSize = css.get_font_size().value();
    auto length = [&](const lh::css_length &l, NSString *predefined = @"none") -> NSString * {
        if(l.is_predefined())
            return predefined;
        switch(l.units())
        {
        case lh::css_units_percentage:
            return [NSString stringWithFormat:@"%g%%", l.val()];
        case lh::css_units_em:
            return px(l.val() * fontSize);
        case lh::css_units_rem:
            return px(l.val() * 16);
        case lh::css_units_pt:
            return px(l.val() * 4 / 3);
        case lh::css_units_none:
        case lh::css_units_px:
        case lh::css_units_calc:
            return px(l.val());
        default:
            return [NSString stringWithFormat:@"%g%s", l.val(), std::string(lh::css_units_strings[l.units()]).c_str()];
        }
    };
    auto raw = [&](const char *name, NSString *fallback) -> NSString * {
        std::string v = tag ? tag->raw_declaration(name) : std::string();
        const size_t first = v.find_first_not_of(" \t\n\r\f");
        if(first == std::string::npos)
            return fallback;
        v = v.substr(first, v.find_last_not_of(" \t\n\r\f") - first + 1);
        return @(v.c_str());
    };
    auto shorthand = [](NSString *top, NSString *right, NSString *bottom, NSString *left) -> NSString * {
        if([left isEqualToString:right])
        {
            if([top isEqualToString:bottom])
                return [top isEqualToString:right] ? top : [NSString stringWithFormat:@"%@ %@", top, right];
            return [NSString stringWithFormat:@"%@ %@ %@", top, right, bottom];
        }
        return [NSString stringWithFormat:@"%@ %@ %@ %@", top, right, bottom, left];
    };
    auto name = [](std::string_view s) { return @(std::string(s).c_str()); };
    if(found)
    {
        out[@"padding-top"]         = px(found->padding_top().value());
        out[@"padding-right"]       = px(found->padding_right().value());
        out[@"padding-bottom"]      = px(found->padding_bottom().value());
        out[@"padding-left"]        = px(found->padding_left().value());
        out[@"border-top-width"]    = px(found->border_top().value());
        out[@"border-right-width"]  = px(found->border_right().value());
        out[@"border-bottom-width"] = px(found->border_bottom().value());
        out[@"border-left-width"]   = px(found->border_left().value());
        out[@"margin-top"]          = px(found->margin_top().value());
        out[@"margin-right"]        = px(found->margin_right().value());
        out[@"margin-bottom"]       = px(found->margin_bottom().value());
        out[@"margin-left"]         = px(found->margin_left().value());
        const lh::position p        = found->get_placement();
        out[@"width"]               = px(p.width.value());
        out[@"height"]              = px(p.height.value());
    } else
    {
        for(NSString *name in @[@"padding-top", @"padding-right", @"padding-bottom", @"padding-left", @"border-top-width", @"border-right-width",
                                @"border-bottom-width", @"border-left-width", @"margin-top", @"margin-right", @"margin-bottom", @"margin-left", @"width", @"height"])
            out[name] = @"0px";
    }
    out[@"max-width"]   = length(css.get_max_width());
    out[@"max-height"]  = length(css.get_max_height());
    out[@"box-sizing"]  = css.get_box_sizing() == lh::box_sizing_border_box ? @"border-box" : @"content-box";
    out[@"display"]     = @(std::string(lh::style_display_strings[css.get_display()]).c_str());
    out[@"position"]    = @(std::string(lh::element_position_strings[css.get_position()]).c_str());
    out[@"font-size"]   = px(css.get_font_size().value());
    const lh::css_length &lineHeight = css.line_height().css_value;
    if(lineHeight.is_predefined())
        out[@"line-height"] = @"normal";
    else if(lineHeight.units() == lh::css_units_none)
        out[@"line-height"] = px(lineHeight.val() * css.get_font_size().value());
    else if(lineHeight.units() == lh::css_units_percentage)
        out[@"line-height"] = px(lineHeight.val() * css.get_font_size().value() / 100);
    else
        out[@"line-height"] = px(css.line_height().computed_value.value());
    out[@"font-weight"] = [NSString stringWithFormat:@"%d", (int)css.font_weight().val()];
    out[@"font-family"] = @(css.font_family().c_str());
    out[@"font-style"]  = css.font_style_value() == lh::font_style_italic ? @"italic" : @"normal";
    auto rgb = [](const lh::web_color &c) {
        return c.alpha == 255 ? [NSString stringWithFormat:@"rgb(%d, %d, %d)", c.red, c.green, c.blue]
                              : [NSString stringWithFormat:@"rgba(%d, %d, %d, %g)", c.red, c.green, c.blue, c.alpha / 255.0];
    };
    out[@"color"]            = rgb(css.get_color());
    out[@"background-color"] = rgb(css.get_bg().m_color);
    out[@"opacity"]     = [NSString stringWithFormat:@"%g", css.get_opacity()];
    out[@"text-align"]  = @(std::string(lh::text_align_strings[css.get_text_align()]).c_str());
    out[@"visibility"]  = css.get_visibility() == lh::visibility_visible ? @"visible" : @"hidden";
    out[@"direction"]   = name(lh::direction_strings[css.get_direction()]);
    out[@"white-space"] = name(lh::white_space_strings[css.get_white_space()]);
    NSString *overflowX = name(lh::overflow_strings[css.get_overflow_x()]), *overflowY = name(lh::overflow_strings[css.get_overflow_y()]);
    out[@"overflow-x"]  = overflowX;
    out[@"overflow-y"]  = overflowY;
    out[@"overflow"]    = [overflowX isEqualToString:overflowY] ? overflowX : [NSString stringWithFormat:@"%@ %@", overflowX, overflowY];
    NSArray<NSString *> *edges = @[@"top", @"right", @"bottom", @"left"];
    for(NSArray<NSString *> *box in @[@[@"margin", @""], @[@"padding", @""], @[@"border", @"-width"]])
    {
        NSMutableArray<NSString *> *values = [NSMutableArray array];
        for(NSString *edge in edges)
            [values addObject:out[[NSString stringWithFormat:@"%@-%@%@", box[0], edge, box[1]]]];
        out[[box componentsJoinedByString:@""]] = shorthand(values[0], values[1], values[2], values[3]);
    }
    const lh::css_borders &borders = css.get_borders();
    const lh::css_border  *sides[4] = {&borders.top, &borders.right, &borders.bottom, &borders.left};
    NSMutableArray<NSString *> *styles = [NSMutableArray array], *colors = [NSMutableArray array];
    for(int i = 0; i < 4; i++)
    {
        [styles addObject:name(lh::border_style_strings[sides[i]->style])];
        [colors addObject:rgb(sides[i]->color)];
        out[[NSString stringWithFormat:@"border-%@-style", edges[i]]] = styles[i];
        out[[NSString stringWithFormat:@"border-%@-color", edges[i]]] = colors[i];
    }
    out[@"border-style"] = shorthand(styles[0], styles[1], styles[2], styles[3]);
    out[@"border-color"] = shorthand(colors[0], colors[1], colors[2], colors[3]);
    const lh::css_border &outline  = css.get_outline();
    const bool            outlined = outline.style != lh::border_style_none && outline.style != lh::border_style_hidden;
    out[@"outline-style"]  = name(lh::border_style_strings[outline.style]);
    out[@"outline-width"]  = outlined ? length(outline.width, @"3px") : @"0px";
    out[@"outline-color"]  = rgb(outline.color.alpha ? outline.color : css.get_color());
    out[@"outline-offset"] = px(css.get_outline_offset().value());
    const int decoration = css.get_text_decoration_line();
    NSMutableArray<NSString *> *lines = [NSMutableArray array];
    if(decoration & lh::text_decoration_line_underline)
        [lines addObject:@"underline"];
    if(decoration & lh::text_decoration_line_overline)
        [lines addObject:@"overline"];
    if(decoration & lh::text_decoration_line_line_through)
        [lines addObject:@"line-through"];
    NSString *decorationLine  = lines.count ? [lines componentsJoinedByString:@" "] : @"none";
    NSString *decorationStyle = name(lh::style_text_decoration_style_strings[css.get_text_decoration_style()]);
    NSString *decorationColor = rgb(css.get_text_decoration_color().alpha ? css.get_text_decoration_color() : css.get_color());
    out[@"text-decoration-line"]  = decorationLine;
    out[@"text-decoration-style"] = decorationStyle;
    out[@"text-decoration-color"] = decorationColor;
    out[@"text-decoration"]       = [NSString stringWithFormat:@"%@ %@ %@", decorationLine, decorationStyle, decorationColor];
    out[@"text-indent"]           = length(css.get_text_indent(), @"0px");
    NSString *userSelect          = name(lh::user_select_strings[css.get_user_select()]);
    out[@"user-select"]           = userSelect;
    out[@"-webkit-user-select"]   = userSelect;
    out[@"vertical-align"]        = name(lh::vertical_align_strings[css.get_vertical_align()]);
    out[@"border-collapse"]       = name(lh::border_collapse_strings[css.get_border_collapse()]);
    out[@"list-style-type"]       = name(lh::list_style_type_strings[css.get_list_style_type()]);
    out[@"list-style-position"]   = name(lh::list_style_position_strings[css.get_list_style_position()]);
    out[@"list-style-image"]      = css.get_list_style_image().empty() ? @"none" : [NSString stringWithFormat:@"url(\"%s\")", css.get_list_style_image().c_str()];
    const lh::background &bg = css.get_bg();
    NSMutableArray<NSString *> *images = [NSMutableArray array], *sizes = [NSMutableArray array], *positions = [NSMutableArray array],
                               *repeats = [NSMutableArray array];
    for(size_t i = 0; i < bg.m_image.size(); i++)
    {
        const lh::image &image = bg.m_image[i];
        if(image.type == lh::image::type_url)
            [images addObject:[NSString stringWithFormat:@"url(\"%s\")", image.url.c_str()]];
        else if(image.type == lh::image::type_gradient)
            [images addObject:[NSString stringWithFormat:@"%s(...)", lh::_s(image.m_gradient.m_type).c_str()]];
        else
            [images addObject:@"none"];
        if(i < bg.m_size.size())
        {
            const auto &size = bg.m_size[i];
            NSString   *w = length(size.width, @"auto"), *h = length(size.height, @"auto");
            if(size.width.is_predefined() && size.width.predef() != lh::background_size_auto)
                [sizes addObject:name(lh::background_size_strings[size.width.predef()])];
            else
                [sizes addObject:[w isEqualToString:@"auto"] && [h isEqualToString:@"auto"] ? w : [NSString stringWithFormat:@"%@ %@", w, h]];
        }
        if(i < bg.m_position_x.size() && i < bg.m_position_y.size())
            [positions addObject:[NSString stringWithFormat:@"%@ %@", length(bg.m_position_x[i], @"0%"), length(bg.m_position_y[i], @"0%")]];
        if(i < bg.m_repeat.size())
            [repeats addObject:name(lh::background_repeat_strings[bg.m_repeat[i]])];
    }
    out[@"background-image"]    = images.count ? [images componentsJoinedByString:@", "] : @"none";
    out[@"background-size"]     = sizes.count ? [sizes componentsJoinedByString:@", "] : @"auto";
    out[@"background-position"] = positions.count ? [positions componentsJoinedByString:@", "] : @"0% 0%";
    out[@"background-repeat"]   = repeats.count ? [repeats componentsJoinedByString:@", "] : @"repeat";
    out[@"cursor"]              = css.get_cursor().empty() ? @"auto" : @(css.get_cursor().c_str());
    out[@"touch-action"]        = raw("touch-action", @"auto");
    out[@"filter"]              = raw("filter", @"none");
    out[@"backdrop-filter"]     = raw("backdrop-filter", @"none");
    lh::web_color paint;
    out[@"fill"]                = lh::svg_paint_color(el, true, paint, _container.get()) ? rgb(paint) : @"none";
    out[@"stroke"]              = lh::svg_paint_color(el, false, paint, _container.get()) ? rgb(paint) : @"none";
    out[@"quotes"]              = @"auto";
    for(lh::element::ptr e = el; e; e = e->parent())
    {
        auto quoted = std::dynamic_pointer_cast<lh::html_tag>(e);
        std::string v = quoted ? quoted->raw_declaration("quotes") : std::string();
        lh::trim(v);
        if(v.empty() || v == "inherit" || v == "unset")
            continue;
        out[@"quotes"] = @(v.c_str());
        break;
    }
    return out;
}

static std::string LHCustomPropertyText(const lh::css_token_vector &tokens)
{
    std::string             out;
    const lh::css_token *previous = nullptr;
    for(const auto &tok : tokens)
    {
        if(tok.type == lh::WHITESPACE)
            continue;
        if(previous && tok.type != lh::COMMA && tok.type != '/' && previous->type != '/')
            out += ' ';
        if(tok.type == lh::CV_FUNCTION)
            out += tok.name() + "(" + LHCustomPropertyText(tok.value) + ")";
        else if(tok.type == lh::ROUND_BLOCK)
            out += "(" + LHCustomPropertyText(tok.value) + ")";
        else if(tok.type == lh::SQUARE_BLOCK)
            out += "[" + LHCustomPropertyText(tok.value) + "]";
        else if(tok.type == lh::CURLY_BLOCK)
            out += "{" + LHCustomPropertyText(tok.value) + "}";
        else
            out += tok.get_repr(false);
        previous = &tok;
    }
    return out;
}

- (nullable NSString *)customProperty:(NSString *)property attribute:(NSString *)attribute value:(NSString *)value
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || !_doc->root())
        return nil;
    const char       *key    = attribute.UTF8String;
    const std::string wanted = value.UTF8String;
    std::function<lh::element::ptr(const lh::element::ptr &)> search = [&](const lh::element::ptr &e) -> lh::element::ptr {
        const char *v = e->get_attr(key);
        if(v && wanted == v)
            return e;
        for(const auto &child : e->children())
            if(auto hit = search(child))
                return hit;
        return nullptr;
    };
    auto                   tag = std::dynamic_pointer_cast<lh::html_tag>(search(_doc->root()));
    lh::css_token_vector tokens;
    if(!tag || !tag->get_custom_property(lh::_id(property.UTF8String ?: ""), tokens))
        return nil;
    return @(LHCustomPropertyText(tokens).c_str());
}

- (void)visitBoundsLocked:(NSString *)name transformed:(BOOL)transformed lineBoxes:(BOOL)lineBoxes visitor:(const std::function<void(const char *, CGRect)> &)visitor
{
    if(!_doc || !_doc->root_render())
        return;
    const char *key = name.UTF8String;
    std::function<void(const std::shared_ptr<lh::render_item> &, const lh::transform_matrix&)> visit = [&](const std::shared_ptr<lh::render_item> &ri, const lh::transform_matrix& ancestors) {
        const auto matrix = transformed ? ri->get_visual_transform().then(ancestors) : lh::transform_matrix{};
        if(const char *value = ri->src_el()->get_attr(key))
        {
            const CGAffineTransform affine = CGAffineTransformMake(matrix.a, matrix.b, matrix.c, matrix.d, matrix.tx, matrix.ty);
            CGRect box = CGRectNull;
            if(!lineBoxes || !VisitLineBoxes(ri.get(), [&](CGRect line) { box = CGRectUnion(box, transformed ? CGRectApplyAffineTransform(line, affine) : line); }))
            {
                const lh::position p      = ri->get_placement();
                const CGFloat      left   = ri->padding_left().value() + ri->border_left().value();
                const CGFloat      top    = ri->padding_top().value() + ri->border_top().value();
                const CGFloat      right  = ri->padding_right().value() + ri->border_right().value();
                const CGFloat      bottom = ri->padding_bottom().value() + ri->border_bottom().value();
                box = CGRectMake(p.x.value() - left, p.y.value() - top, p.width.value() + left + right, p.height.value() + top + bottom);
                if(transformed)
                {
                    box = CGRectApplyAffineTransform(box, affine);
                }
            }
            visitor(value, box);
        }
        for(const auto &child : ri->children())
            visit(child, matrix);
    };
    visit(_doc->root_render(), {});
}

- (NSDictionary<NSString *, NSValue *> *)boundsWithAttribute:(NSString *)name transformed:(BOOL)transformed
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    NSMutableDictionary<NSString *, NSValue *> *result = [NSMutableDictionary dictionary];
    std::unordered_map<std::string, CGRect> boxes;
    [self visitBoundsLocked:name transformed:transformed lineBoxes:NO visitor:[&](const char *value, CGRect box) {
        auto it = boxes.find(value);
        if(it == boxes.end())
            boxes.emplace(value, box);
        else
            it->second = CGRectUnion(it->second, box);
    }];
    for(const auto &entry : boxes)
        result[@(entry.first.c_str())] = [NSValue valueWithRect:NSRectFromCGRect(entry.second)];
    return result;
}

- (NSData *)keyedBoundsByAttribute:(NSString *)name transformed:(BOOL)transformed
{
    return [self keyedBoundsByAttribute:name transformed:transformed lineBoxes:NO];
}

- (NSData *)keyedBoundsByAttribute:(NSString *)name transformed:(BOOL)transformed lineBoxes:(BOOL)lineBoxes
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    std::vector<LHKeyedRect> boxes;
    std::unordered_map<int64_t, size_t> slots;
    [self visitBoundsLocked:name transformed:transformed lineBoxes:lineBoxes visitor:[&](const char *value, CGRect box) {
        int64_t key = 0;
        if(!ParseKeyInteger(value, key))
            return;
        auto [it, inserted] = slots.emplace(key, boxes.size());
        if(inserted)
            boxes.push_back({(NSInteger)key, box});
        else
            boxes[it->second].rect = CGRectUnion(boxes[it->second].rect, box);
    }];
    return [NSData dataWithBytes:boxes.data() length:boxes.size() * sizeof(LHKeyedRect)];
}

- (NSDictionary<NSString *, NSValue *> *)boundsByAttribute:(NSString *)name
{
    return [self boundsWithAttribute:name transformed:NO];
}

- (NSDictionary<NSString *, NSValue *> *)visualBoundsByAttribute:(NSString *)name
{
    return [self boundsWithAttribute:name transformed:YES];
}

- (NSString *)layoutSignature
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    std::string out;
    if(!_doc || !_doc->root_render())
        return @"";
    char line[256];
    std::function<void(const std::shared_ptr<lh::render_item> &, int)> visit = [&](const std::shared_ptr<lh::render_item> &ri, int depth) {
        const auto         el = ri->src_el();
        const lh::position p  = ri->get_placement();
        if(p.width.value() == 0 && p.height.value() == 0)
        {
            for(const auto &child : ri->children())
                visit(child, depth + 1);
            return;
        }
        if(el->is_text())
        {
            std::string text;
            el->get_text(text);
            out += "#" + text;
        } else
            out += el->get_tagName();
        if(ri->skip())
        {
            out += " skipped\n";
            for(const auto &child : ri->children())
                visit(child, depth + 1);
            return;
        }
        snprintf(line, sizeof(line), " %d %.3f %.3f %.3f %.3f m%.3f,%.3f,%.3f,%.3f p%.3f,%.3f,%.3f,%.3f b%.3f,%.3f,%.3f,%.3f %d\n", depth,
                 p.x.value(), p.y.value(), p.width.value(), p.height.value(), ri->margin_left().value(), ri->margin_top().value(),
                 ri->margin_right().value(), ri->margin_bottom().value(), ri->padding_left().value(), ri->padding_top().value(),
                 ri->padding_right().value(), ri->padding_bottom().value(), ri->border_left().value(), ri->border_top().value(),
                 ri->border_right().value(), ri->border_bottom().value(), ri->skip() ? 1 : 0);
        out += line;
        for(const auto &child : ri->children())
            visit(child, depth + 1);
    };
    visit(_doc->root_render(), 0);
    return [[NSString alloc] initWithBytes:out.data() length:out.size() encoding:NSUTF8StringEncoding] ?: @"";
}

- (NSDictionary<NSString *, NSString *> *)parentsByAttribute:(NSString *)name
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    NSMutableDictionary<NSString *, NSString *> *result = [NSMutableDictionary dictionary];
    if(!_doc || !_doc->root())
        return result;
    const char *key = name.UTF8String;
    std::function<void(const lh::element::ptr &, const char *)> visit = [&](const lh::element::ptr &el, const char *parent) {
        const char *value = el->get_attr(key);
        if(value && parent)
            result[@(value)] = @(parent);
        for(const auto &child : el->children())
            visit(child, value ? value : parent);
    };
    visit(_doc->root(), nullptr);
    return result;
}

- (CGRect)boundsOfElementsMatching:(NSString *)selector
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || !_doc->root() || !_doc->root_render())
        return CGRectNull;
    std::unordered_set<const lh::element *> wanted;
    for(const auto &el : _doc->root()->select_all(std::string(selector.UTF8String)))
        wanted.insert(el.get());
    CGRect result = CGRectNull;
    if(wanted.empty())
        return result;
    std::function<void(const std::shared_ptr<lh::render_item> &)> visit = [&](const std::shared_ptr<lh::render_item> &ri) {
        if(wanted.count(ri->src_el().get()))
        {
            const lh::position p      = ri->get_placement();
            const CGFloat      left   = ri->padding_left().value() + ri->border_left().value();
            const CGFloat      top    = ri->padding_top().value() + ri->border_top().value();
            const CGFloat      right  = ri->padding_right().value() + ri->border_right().value();
            const CGFloat      bottom = ri->padding_bottom().value() + ri->border_bottom().value();
            const CGRect       box    = CGRectMake(p.x.value() - left, p.y.value() - top, p.width.value() + left + right, p.height.value() + top + bottom);
            if(box.size.width > 0 && box.size.height > 0)
                result = CGRectUnion(result, box);
        }
        for(const auto &child : ri->children())
            visit(child);
    };
    visit(_doc->root_render());
    return result;
}

- (void)recordLockedSize:(CGSize)size ops:(std::vector<LHDrawOp> &)ops recorder:(lhx::RenderRecorder &)recorder
{
    CGContextRef ctx = _doc ? [self createContextWithWidth:1 height:1] : nullptr;
    if(!ctx)
        return;
    _container->set_draw_ops(&ops);
    _container->set_gpu_recorder(&recorder);
    [self drawLockedInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, size.width, std::max<CGFloat>(size.height, 1))];
    _container->set_gpu_recorder(nullptr);
    _container->set_draw_ops(nullptr);
    CGContextRelease(ctx);
}

+ (long)sourceFilterCacheHits
{
    return LHContainer::s_source_filter_hits.load(std::memory_order_relaxed);
}

+ (void)setSourceFilterCacheEnabled:(BOOL)enabled
{
    LHContainer::s_source_filter_cache.store(enabled, std::memory_order_relaxed);
}

+ (void)setSVGGroupCacheEnabled:(BOOL)enabled
{
    lh::svg_set_group_cache_enabled(enabled);
}

+ (long)shadowLineBatches
{
    return LHContainer::s_shadow_line_batches.load(std::memory_order_relaxed);
}

+ (void)setExactGroupBoundsEnabled:(BOOL)enabled
{
    LHContainer::s_exact_group_bounds.store(enabled, std::memory_order_relaxed);
}

+ (void)setTextBatchingEnabled:(BOOL)enabled
{
    LHContainer::s_text_batching.store(enabled, std::memory_order_relaxed);
}

- (nullable CGImageRef)copyImageWithBackgroundPositionX:(double)number percent:(BOOL)percent attribute:(NSString *)attribute value:(NSString *)value maxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale 
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return nullptr;
    auto* bg = const_cast<lh::background*>(ri->src_el()->get_background());
    if(!bg || bg->m_image.size() != 1)
        return nullptr;
    CGImageRef before = [self copyImageLockedWithRect:rect scale:scale];
    const lh::length_vector saved = bg->m_position_x;
    bg->m_position_x = {lh::css_length(static_cast<float>(number), percent ? lh::css_units_percentage : lh::css_units_px)};
    CGImageRef image = [self copyImageLockedWithRect:rect scale:scale];
    bg->m_position_x = saved;
    CGImageRef after = [self copyImageLockedWithRect:rect scale:scale];
    {
        BOOL same = before && after;
        if(same)
        {
            NSData *a = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(before)));
            NSData *b = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(after)));
            const size_t rowA = CGImageGetBytesPerRow(before), rowB = CGImageGetBytesPerRow(after);
            const size_t w = CGImageGetWidth(before) * 4, h = CGImageGetHeight(before);
            same = CGImageGetWidth(before) == CGImageGetWidth(after) && h == CGImageGetHeight(after) && a.length >= rowA * h && b.length >= rowB * h;
            for(size_t y = 0; same && y < h; y++)
                same = memcmp((const uint8_t *)a.bytes + y * rowA, (const uint8_t *)b.bytes + y * rowB, w) == 0;
        }
        if(!same)
            _restoreMismatches++;
    }
    CGImageRelease(before);
    CGImageRelease(after);
    return image;
}

- (LHBackgroundTile *)backgroundTileWithAttribute:(NSString *)attribute value:(NSString *)value maxWidth:(CGFloat)maxWidth scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return nil;
    auto el = ri->src_el();
    const lh::background* bg = el->get_background();
    if(!bg || el->is_root() || bg->m_image.size() != 1 || ri->src_el()->is_inline())
        return nil;
    const auto type = bg->get_layer_type(0);
    if(type != lh::background::type_linear_gradient && type != lh::background::type_radial_gradient && type != lh::background::type_conic_gradient)
        return nil;
    if(!bg->m_repeat.empty() && bg->m_repeat[0] != lh::background_repeat_no_repeat)
        return nil;
    if(bg->m_repeat.empty())
        return nil;
    if(!bg->m_origin.empty() && bg->m_origin[0] != lh::background_box_padding)
        return nil;
    if(!bg->m_attachment.empty() && bg->m_attachment[0] != lh::background_attachment_scroll)
        return nil;
    if(!el->children().empty() || el->css().is_paint_group())
        return nil;
    lh::position content = ri->get_placement();
    lh::background_layer layer;
    if(!bg->get_layer(0, content, el.get(), ri, layer))
        return nil;
    const CGRect clipRect = CGRectMake(layer.clip_box.x.value(), layer.clip_box.y.value(), layer.clip_box.width.value(), layer.clip_box.height.value());
    for(auto p = ri->parent(); p; p = p->parent())
    {
        const auto& css = p->src_el()->css();
        if(css.is_paint_group() || css.get_position() == lh::element_position_fixed)
            return nil;
        if(css.clips_contents())
        {
            CGRect border = BorderBox(p);
            CGFloat bl = p->border_left().value(), bt = p->border_top().value(), br = p->border_right().value(), bb = p->border_bottom().value();
            CGRect inner  = CGRectMake(border.origin.x + bl, border.origin.y + bt, border.size.width - bl - br, border.size.height - bt - bb);
            const auto& radius = css.get_borders().radius.calc_percents(lh::pixel_t(static_cast<float>(border.size.width)), lh::pixel_t(static_cast<float>(border.size.height)));
            CGFloat corner = std::max({radius.top_left_x.value(), radius.top_left_y.value(), radius.top_right_x.value(), radius.top_right_y.value(),
                                       radius.bottom_left_x.value(), radius.bottom_left_y.value(), radius.bottom_right_x.value(), radius.bottom_right_y.value()});
            if(!CGRectContainsRect(CGRectInset(inner, corner, corner), clipRect))
                return nil;
        }
    }
    lh::position padding = content;
    padding += ri->get_paddings();
    const CGFloat tw = layer.origin_box.width.value(), th = layer.origin_box.height.value();
    if(tw <= 0 || th <= 0 || scale < 0)
        return nil;
    if(scale == 0)
    {
        LHBackgroundTile *geometry = [LHBackgroundTile new];
        geometry->_tile = CGRectMake(layer.origin_box.x.value(), layer.origin_box.y.value(), tw, th);
        lh::position area = content;
        area += ri->get_paddings();
        geometry->_area = CGRectMake(area.x.value(), area.y.value(), area.width.value(), area.height.value());
        geometry->_clip = clipRect;
        const auto& r = layer.border_radius;
        const lh::pixel_t radii[8] = {r.top_left_x, r.top_left_y, r.top_right_x, r.top_right_y, r.bottom_right_x, r.bottom_right_y, r.bottom_left_x, r.bottom_left_y};
        for(int i = 0; i < 8; i++)
            geometry->_radii[i] = radii[i].value();
        return geometry;
    }
    const size_t pw = (size_t)std::ceil(tw * scale), ph = (size_t)std::ceil(th * scale);
    if(pw == 0 || ph == 0 || pw * ph > (4u << 20))
        return nil;
    CGContextRef ctx = [self createContextWithWidth:pw height:ph];
    if(!ctx)
        return nil;
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, scale, -scale);
    CGContextTranslateCTM(ctx, -layer.origin_box.x.value(), -layer.origin_box.y.value());
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    lh::background_layer tile = layer;
    tile.clip_box      = layer.origin_box;
    tile.border_box    = layer.origin_box;
    tile.border_radius = lh::border_radiuses();
    bg->draw_layer(reinterpret_cast<lh::uint_ptr>(ctx), 0, tile, _doc->container());
    CGImageRef image = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    if(!image)
        return nil;
    LHBackgroundTile *result = [LHBackgroundTile new];
    result->_image = image;
    result->_tile  = CGRectMake(layer.origin_box.x.value(), layer.origin_box.y.value(), tw, th);
    result->_area  = CGRectMake(padding.x.value(), padding.y.value(), padding.width.value(), padding.height.value());
    result->_clip  = CGRectMake(layer.clip_box.x.value(), layer.clip_box.y.value(), layer.clip_box.width.value(), layer.clip_box.height.value());
    const auto& r = layer.border_radius;
    const lh::pixel_t radii[8] = {r.top_left_x, r.top_left_y, r.top_right_x, r.top_right_y, r.bottom_right_x, r.bottom_right_y, r.bottom_left_x, r.bottom_left_y};
    for(int i = 0; i < 8; i++)
        result->_radii[i] = radii[i].value();
    return result;
}

- (LHSVGShapes *)svgShapesWithAttribute:(NSString *)attribute value:(NSString *)value maxWidth:(CGFloat)maxWidth
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    auto ri = [self renderItemLockedWithAttribute:attribute value:value];
    if(!ri)
        return nil;
    auto svg = std::dynamic_pointer_cast<lh::el_svg>(ri->src_el());
    if(!svg)
        return nil;
    lh::position pos = ri->get_placement();
    std::vector<LHDrawOp>    ops;
    std::vector<LHShapeData> shapes;
    _container->set_draw_ops(&ops);
    _container->set_shape_capture(&shapes);
    svg->lh::html_tag::draw(0, pos.x - ri->pos().x, pos.y - ri->pos().y, nullptr, ri);
    bool painted = !_container->shape_capture_failed() && svg->paint_shapes(0, pos);
    bool failed  = _container->shape_capture_failed();
    _container->set_shape_capture(nullptr);
    _container->set_draw_ops(nullptr);
    if(!painted || failed || !ops.empty())
    {
        for(auto& shape : shapes)
            CGPathRelease(shape.path);
        return nil;
    }
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    NSMutableArray<LHSVGShape *> *list = [NSMutableArray arrayWithCapacity:shapes.size()];
    for(auto& data : shapes)
    {
        LHSVGShape *shape   = [LHSVGShape new];
        shape->_path        = data.path;
        shape->_evenOdd     = data.evenOdd;
        shape->_fillColor   = data.fill ? CGColorCreate(space, data.fillColor) : nullptr;
        shape->_strokeColor = data.stroke ? CGColorCreate(space, data.strokeColor) : nullptr;
        shape->_lineWidth   = data.lineWidth;
        shape->_lineCap     = data.cap;
        shape->_lineJoin    = data.join;
        shape->_miterLimit  = data.miter;
        if(data.gradient == 1)
        {
            NSMutableArray *colors = [NSMutableArray array];
            NSMutableArray *locations = [NSMutableArray array];
            for(const auto& stop : data.stops)
            {
                CGColorRef color = CGColorCreate(space, stop.second.data());
                [colors addObject:(__bridge_transfer id)color];
                [locations addObject:@(stop.first)];
            }
            shape->_gradientColors    = colors;
            shape->_gradientLocations = locations;
            shape->_gradientStart     = data.start;
            shape->_gradientEnd       = data.end;
        }
        [list addObject:shape];
    }
    CGColorSpaceRelease(space);
    LHSVGShapes *result = [LHSVGShapes new];
    result->_frame  = CGRectMake(pos.x.value(), pos.y.value(), pos.width.value(), pos.height.value());
    result->_shapes = list;
    return result;
}

- (LHDisplayList *)displayListWithMaxWidth:(CGFloat)maxWidth gpu:(LHGPURenderer *)renderer scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    CGSize                size = [self layoutLockedWithMaxWidth:maxWidth];
    std::vector<LHDrawOp> ops;
    LHGPURecorder         recorder;
    CGColorSpaceRef       space = [self copyDrawingSpace];
    [renderer beginRecordingWithScale:scale space:space recorder:&recorder];
    [self recordLockedSize:size ops:ops recorder:recorder];
    CGColorSpaceRelease(space);
    LHDisplayList *list = [[LHDisplayList alloc] initWithOps:std::move(ops)];
    [list setGPUOps:std::move(recorder.ops) unsupported:recorder.unsupported];
    return list;
}

- (LHDisplayList *)displayListWithMaxWidth:(CGFloat)maxWidth
{
    return [self displayListWithMaxWidth:maxWidth scale:1];
}

- (LHDisplayList *)displayListWithMaxWidth:(CGFloat)maxWidth scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    CGSize                size = [self layoutLockedWithMaxWidth:maxWidth];
    std::vector<LHDrawOp> ops;
    CGContextRef          ctx = _doc ? [self createContextWithWidth:1 height:1] : nullptr;
    if(ctx)
    {
        if(scale > 0 && std::isfinite(scale))
            CGContextScaleCTM(ctx, scale, scale);
        _container->set_draw_ops(&ops);
        [self drawLockedInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, size.width, std::max<CGFloat>(size.height, 1))];
        _container->set_draw_ops(nullptr);
        CGContextRelease(ctx);
    }
    return [[LHDisplayList alloc] initWithOps:std::move(ops)];
}

- (CGImageRef)copyImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    return [self copyImageLockedWithRect:rect scale:scale];
}

- (CGImageRef)copyImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale excluding:(NSSet<NSString *> *)values attribute:(NSString *)attribute
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    NSSet<NSString *> *savedValues    = _exclusions;
    NSString          *savedAttribute = _exclusionAttribute;
    NSMutableSet<NSString *> *merged  = [NSMutableSet setWithSet:values];
    if(savedValues.count > 0 && [savedAttribute isEqualToString:attribute])
        [merged unionSet:savedValues];
    _paintExclusionCache.clear();
    _paintExclusionRulesValid = NO;
    _exclusions         = merged;
    _exclusionAttribute = [attribute copy];
    CGImageRef image    = [self copyImageLockedWithRect:rect scale:scale];
    _paintExclusionCache.clear();
    _paintExclusionRulesValid = NO;
    _exclusions         = savedValues;
    _exclusionAttribute = savedAttribute;
    return image;
}

- (CGImageRef)copyTileImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale
{
    std::lock_guard<std::mutex> guard(_lock);
    [self layoutLockedWithMaxWidth:maxWidth];
    _snapshotDepth--;
    CGImageRef image = [self copyImageLockedWithRect:rect scale:scale];
    _snapshotDepth++;
    return image;
}

- (CGImageRef)copyImageLockedWithRect:(CGRect)rect scale:(CGFloat)scale CF_RETURNS_RETAINED
{
    size_t pw = (size_t)std::ceil(rect.size.width * scale);
    size_t ph = (size_t)std::ceil(rect.size.height * scale);
    if(!_doc || pw == 0 || ph == 0)
        return nullptr;
    CGContextRef ctx = [self createContextWithWidth:pw height:ph];
    if(!ctx)
        return nullptr;
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, scale, -scale);
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    _snapshotDepth++;
    [self drawLockedInContext:ctx origin:CGPointMake(-rect.origin.x, -rect.origin.y) clip:rect];
    _snapshotDepth--;
    CGImageRef image = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    return image;
}

static bool LHUserSelectable(lh::element::ptr el)
{
    for(; el; el = el->parent())
    {
        if(!std::dynamic_pointer_cast<lh::html_tag>(el))
            continue;
        const lh::user_select value = el->css().get_user_select();
        if(value != lh::user_select_auto)
            return value == lh::user_select_text;
    }
    return false;
}

- (NSArray<LHTextFragment *> *)selectableTextFragments
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    if(!_doc || _lastMaxWidth <= 0)
        return @[];
    CGSize size = [self layoutLockedWithMaxWidth:_lastMaxWidth];
    auto   root = _doc->root_render();
    if(!root)
        return @[];
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef    ctx   = CGBitmapContextCreate(nullptr, 1, 1, 8, 0, space,
                                                  kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    if(!ctx)
        return @[];
    std::vector<LHTextRun> runs;
    _container->set_text_runs(&runs);
    [self drawLockedInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, size.width, std::max<CGFloat>(size.height, 1))];
    _container->set_text_runs(nullptr);
    CGContextRelease(ctx);

    const auto any = [](const std::shared_ptr<lh::render_item> &) { return true; };
    NSMutableArray<LHTextFragment *> *fragments = [NSMutableArray array];
    for(const auto &run : runs)
    {
        NSString *text = @(run.text.c_str());
        if(text.length == 0 || !run.font)
            continue;
        const float x = static_cast<float>(CGRectGetMidX(run.frame)), y = static_cast<float>(CGRectGetMidY(run.frame));
        if(!LHUserSelectable(root->get_element_by_point(x, y, x, y, any)))
            continue;
        [fragments addObject:[[LHTextFragment alloc] initWithText:text font:run.font frame:run.frame]];
    }
    return fragments;
}

- (NSArray<LHTextFragment *> *)textFragmentsWithMaxWidth:(CGFloat)maxWidth
{
    std::lock_guard<std::mutex> guard(_lock);
    CGSize size = [self layoutLockedWithMaxWidth:maxWidth];
    if(!_doc)
        return @[];
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef    ctx   = CGBitmapContextCreate(nullptr, 1, 1, 8, 0, space,
                                                  kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    if(!ctx)
        return @[];
    std::vector<LHTextRun> runs;
    _container->set_text_runs(&runs);
    [self drawLockedInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, size.width, std::max<CGFloat>(size.height, 1))];
    _container->set_text_runs(nullptr);
    CGContextRelease(ctx);

    NSMutableArray<LHTextFragment *> *fragments = [NSMutableArray arrayWithCapacity:runs.size()];
    for(const auto &run : runs)
    {
        NSString *text = @(run.text.c_str());
        if(text.length == 0 || !run.font)
            continue;
        [fragments addObject:[[LHTextFragment alloc] initWithText:text font:run.font frame:run.frame]];
    }
    return fragments;
}

- (BOOL)mouseMoved:(CGPoint)p
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    float x = static_cast<float>(p.x), y = static_cast<float>(p.y);
    _paintTraceValid = NO;
    _itemIndexValid = NO;
    const BOOL changed = _doc && _doc->on_mouse_over(x, y, x, y, IgnoreRedraw);
    _pointerRestyled = _pointerRestyled || changed;
    return changed;
}

- (BOOL)mouseDown:(CGPoint)p
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    float x = static_cast<float>(p.x), y = static_cast<float>(p.y);
    _paintTraceValid = NO;
    _itemIndexValid = NO;
    const BOOL changed = _doc && _doc->on_lbutton_down(x, y, x, y, IgnoreRedraw);
    _pointerRestyled = _pointerRestyled || changed;
    return changed;
}

- (BOOL)mouseUp:(CGPoint)p
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    float x = static_cast<float>(p.x), y = static_cast<float>(p.y);
    _paintTraceValid = NO;
    _itemIndexValid = NO;
    const BOOL changed = _doc && _doc->on_lbutton_up(x, y, x, y, IgnoreRedraw);
    _pointerRestyled = _pointerRestyled || changed;
    return changed;
}

- (BOOL)mouseExited
{
    std::lock_guard<std::mutex> guard(_lock);
    [self restoreLocked];
    _paintTraceValid = NO;
    _itemIndexValid = NO;
    const BOOL changed = _doc && _doc->on_mouse_leave(IgnoreRedraw);
    _pointerRestyled = _pointerRestyled || changed;
    return changed;
}

- (NSDictionary<NSString *, NSNumber *> *)sanitizerCounts
{
    std::lock_guard<std::mutex> guard(_lock);
    NSMutableDictionary<NSString *, NSNumber *> *counts = [NSMutableDictionary dictionary];
    if(_container)
    {
        for(const auto &entry : _container->sanitizer_report())
            counts[@(entry.first.c_str())] = @(entry.second.first);
    }
    return counts;
}

- (NSArray<NSString *> *)sanitizerDetails
{
    std::lock_guard<std::mutex> guard(_lock);
    NSMutableArray<NSString *> *details = [NSMutableArray array];
    if(_container)
    {
        for(const auto &entry : _container->sanitizer_report())
        {
            for(const auto &detail : entry.second.second)
                [details addObject:[NSString stringWithFormat:@"%s: %s", entry.first.c_str(), detail.c_str()]];
        }
    }
    return details;
}

@end

@implementation LHDocument (Painting)

- (CGSize)paintWithMaxWidth:(CGFloat)maxWidth clip:(CGRect)clip painter:(lhx::Painter &)painter
{
    std::lock_guard<std::mutex> guard(_lock);
    CGSize       size = [self layoutLockedWithMaxWidth:maxWidth];
    CGContextRef ctx  = _doc ? [self createContextWithWidth:1 height:1] : nullptr;
    if(ctx)
    {
        _container->set_painter(&painter);
        [self drawLockedInContext:ctx origin:CGPointZero clip:clip];
        _container->set_painter(nullptr);
        CGContextRelease(ctx);
    }
    return size;
}

- (CGSize)recordWithMaxWidth:(CGFloat)maxWidth recorder:(lhx::RenderRecorder &)recorder
{
    std::lock_guard<std::mutex> guard(_lock);
    CGSize                size  = [self layoutLockedWithMaxWidth:maxWidth];
    CGColorSpaceRef       given = recorder.space;
    std::vector<LHDrawOp> ops;
    if(!given)
        recorder.space = [self copyDrawingSpace];
    [self recordLockedSize:size ops:ops recorder:recorder];
    if(!given)
    {
        CGColorSpaceRelease(recorder.space);
        recorder.space = nullptr;
    }
    return size;
}

@end

static std::string::size_type FindCaseless(const std::string &text, const char *needle, bool last)
{
    const size_t n = std::strlen(needle);
    if(text.size() < n)
        return std::string::npos;
    auto matches = [&](size_t at) {
        for(size_t i = 0; i < n; i++)
            if(std::tolower(static_cast<unsigned char>(text[at + i])) != needle[i])
                return false;
        return true;
    };
    if(last)
    {
        for(size_t at = text.size() - n + 1; at-- > 0;)
            if(matches(at))
                return at;
        return std::string::npos;
    }
    for(size_t at = 0; at + n <= text.size(); at++)
        if(matches(at))
            return at;
    return std::string::npos;
}

static thread_local int SVGImageDepth = 0;

bool LHRenderingSVGImage(void)
{
    return SVGImageDepth > 0;
}

CGImageRef LHCreateSVGImage(const std::string &markup, CGSize *size)
{
    int         &depth      = SVGImageDepth;
    const size_t kMaxMarkup = 2 * 1024 * 1024;
    if(depth > 0 || markup.size() > kMaxMarkup)
        return nullptr;
    size_t open  = FindCaseless(markup, "<svg", false);
    size_t close = FindCaseless(markup, "</svg>", true);
    if(open == std::string::npos || close == std::string::npos || close < open)
        return nullptr;
    std::string body = "<body style=\"margin: 0; color: #000000\">" + markup.substr(open, close + 6 - open) + "</body>";
    NSString   *html = [[NSString alloc] initWithBytes:body.data() length:body.size() encoding:NSUTF8StringEncoding];
    if(!html)
        return nullptr;
    depth++;
    CGImageRef image = nullptr;
    @autoreleasepool
    {
        LHDocument *doc = [[LHDocument alloc] initWithHTML:html css:@"body > :not(svg) { display: none !important; }"];
        [doc layoutWithMaxWidth:300 preferredWidth:300];
        CGRect box = [doc boundsOfElementsMatching:@"body > svg"];
        if(!CGRectIsNull(box) && box.size.width > 0 && box.size.height > 0 && box.size.width <= 16384 && box.size.height <= 16384)
        {
            CGFloat scale = std::min<CGFloat>(2, std::min(4096 / box.size.width, 4096 / box.size.height));
            image         = [doc copyImageWithMaxWidth:300 rect:box scale:scale];
            if(image && size)
                *size = box.size;
        }
    }
    depth--;
    return image;
}
