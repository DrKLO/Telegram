#import <Cocoa/Cocoa.h>
#import <ImageIO/ImageIO.h>
#include <zlib.h>

#include "LHContainer.h"
#include "render_item.h"
#include "LHFilters.h"
#include "LHSimpleText.h"
#include "twidget_assets.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <list>
#include <memory>
#include <mutex>
#include <string>
#include <utility>

static inline void TWClipPath(CGContextRef ctx, bool evenOdd) {
    if (CGContextIsPathEmpty(ctx)) {
        CGContextClipToRect(ctx, CGRectZero);
        return;
    }
    if (evenOdd) {
        CGContextEOClip(ctx);
    } else {
        CGContextClip(ctx);
    }
}

namespace lh = litehtml;
using lh::operator""_px;

static inline CGFloat F(lh::pixel_t v) { return static_cast<CGFloat>(v.value()); }
static inline CGRect  R(const lh::position& p) { return CGRectMake(F(p.x), F(p.y), F(p.width), F(p.height)); }

static CGColorSpaceRef SRGB()
{
    static CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    return space;
}

static CGColorRef CreateColor(const lh::web_color& c)
{
    const CGFloat comps[4] = {c.red / 255.0, c.green / 255.0, c.blue / 255.0, c.alpha / 255.0};
    return CGColorCreate(SRGB(), comps);
}

namespace {

struct ColorCache
{
    uint32_t        keys[64]   = {};
    CGColorSpaceRef spaces[64] = {};
    CGColorRef      colors[64] = {};

    ~ColorCache()
    {
        for(int i = 0; i < 64; i++)
        {
            CGColorRelease(colors[i]);
            CGColorSpaceRelease(spaces[i]);
        }
    }

    CGColorRef get(const lh::web_color& c, CGColorSpaceRef space)
    {
        const uint32_t key  = uint32_t(c.red) << 24 | uint32_t(c.green) << 16 | uint32_t(c.blue) << 8 | uint32_t(c.alpha);
        const uint32_t slot = ((key ^ static_cast<uint32_t>(reinterpret_cast<uintptr_t>(space) >> 4)) * 2654435761u) >> 26;
        if(colors[slot] && keys[slot] == key && spaces[slot] == space)
            return colors[slot];
        CGColorRelease(colors[slot]);
        CGColorSpaceRelease(spaces[slot]);
        CGColorRef color = CreateColor(c);
        if(space)
        {
            if(CGColorRef matched = CGColorCreateCopyByMatchingToColorSpace(space, kCGRenderingIntentDefault, color, nullptr))
            {
                CGColorRelease(color);
                color = matched;
            }
        }
        colors[slot] = color;
        spaces[slot] = space ? CGColorSpaceRetain(space) : nullptr;
        keys[slot]   = key;
        return color;
    }
};

}

static CGColorRef ContextColor(CGContextRef ctx, const lh::web_color& c)
{
    static thread_local ColorCache cache;
    CGColorSpaceRef space = CGBitmapContextGetColorSpace(ctx);
    if(space && CGColorSpaceGetModel(space) != kCGColorSpaceModelRGB)
        space = nullptr;
    return cache.get(c, space);
}

static void SetFill(CGContextRef ctx, const lh::web_color& c)
{
    CGContextSetFillColorWithColor(ctx, ContextColor(ctx, c));
}

static void SetStroke(CGContextRef ctx, const lh::web_color& c)
{
    CGContextSetStrokeColorWithColor(ctx, ContextColor(ctx, c));
}

static void AddRoundedRect(CGMutablePathRef path, CGRect r, const lh::border_radiuses& rad)
{
    if(r.size.width <= 0 || r.size.height <= 0)
        return;
    const CGFloat k = 0.5522847498;
    const CGFloat hw = r.size.width / 2, hh = r.size.height / 2;
    auto clampx = [&](lh::pixel_t v) { return std::min<CGFloat>(std::max<CGFloat>(F(v), 0), hw); };
    auto clampy = [&](lh::pixel_t v) { return std::min<CGFloat>(std::max<CGFloat>(F(v), 0), hh); };
    CGFloat tlx = clampx(rad.top_left_x), tly = clampy(rad.top_left_y);
    CGFloat trx = clampx(rad.top_right_x), try_ = clampy(rad.top_right_y);
    CGFloat brx = clampx(rad.bottom_right_x), bry = clampy(rad.bottom_right_y);
    CGFloat blx = clampx(rad.bottom_left_x), bly = clampy(rad.bottom_left_y);
    CGFloat x0 = CGRectGetMinX(r), y0 = CGRectGetMinY(r), x1 = CGRectGetMaxX(r), y1 = CGRectGetMaxY(r);

    CGPathMoveToPoint(path, nullptr, x0 + tlx, y0);
    CGPathAddLineToPoint(path, nullptr, x1 - trx, y0);
    if(trx > 0 && try_ > 0)
        CGPathAddCurveToPoint(path, nullptr, x1 - trx + trx * k, y0, x1, y0 + try_ - try_ * k, x1, y0 + try_);
    CGPathAddLineToPoint(path, nullptr, x1, y1 - bry);
    if(brx > 0 && bry > 0)
        CGPathAddCurveToPoint(path, nullptr, x1, y1 - bry + bry * k, x1 - brx + brx * k, y1, x1 - brx, y1);
    CGPathAddLineToPoint(path, nullptr, x0 + blx, y1);
    if(blx > 0 && bly > 0)
        CGPathAddCurveToPoint(path, nullptr, x0 + blx - blx * k, y1, x0, y1 - bly + bly * k, x0, y1 - bly);
    CGPathAddLineToPoint(path, nullptr, x0, y0 + tly);
    if(tlx > 0 && tly > 0)
        CGPathAddCurveToPoint(path, nullptr, x0, y0 + tly - tly * k, x0 + tlx - tlx * k, y0, x0 + tlx, y0);
    CGPathCloseSubpath(path);
}

static lh::margins Uniform(CGFloat v)
{
    lh::margins m;
    m.left = m.right = m.top = m.bottom = static_cast<float>(v);
    return m;
}

static bool HasRadius(const lh::border_radiuses& r)
{
    return F(r.top_left_x) > 0 || F(r.top_right_x) > 0 || F(r.bottom_left_x) > 0 || F(r.bottom_right_x) > 0;
}

static CGFloat RoundedRectLength(CGRect r, const lh::border_radiuses& rad)
{
    if(r.size.width <= 0 || r.size.height <= 0)
        return 0;
    const CGFloat hw = r.size.width / 2, hh = r.size.height / 2;
    auto clampx = [&](lh::pixel_t v) { return std::min<CGFloat>(std::max<CGFloat>(F(v), 0), hw); };
    auto clampy = [&](lh::pixel_t v) { return std::min<CGFloat>(std::max<CGFloat>(F(v), 0), hh); };
    auto corner = [](CGFloat a, CGFloat b) {
        if(a <= 0 || b <= 0)
            return std::hypot(a, b);
        return M_PI / 4 * (3 * (a + b) - std::sqrt((3 * a + b) * (a + 3 * b)));
    };
    CGFloat xs[4] = {clampx(rad.top_left_x), clampx(rad.top_right_x), clampx(rad.bottom_right_x), clampx(rad.bottom_left_x)};
    CGFloat ys[4] = {clampy(rad.top_left_y), clampy(rad.top_right_y), clampy(rad.bottom_right_y), clampy(rad.bottom_left_y)};
    CGFloat length = 2 * (r.size.width + r.size.height);
    for(int i = 0; i < 4; i++)
        length += corner(xs[i], ys[i]) - xs[i] - ys[i];
    return length;
}

struct BorderDash
{
    bool    solid = true;
    bool    round = false;
    CGFloat intervals[2] = {0, 0};
};

static CGFloat BestDashGap(CGFloat length, CGFloat dash, CGFloat gap, bool closed)
{
    CGFloat available = closed ? length : length + gap;
    CGFloat fewer     = std::floor(available / (dash + gap));
    CGFloat more      = fewer + 1;
    CGFloat fewerGap  = (length - fewer * dash) / (closed ? fewer : fewer - 1);
    CGFloat moreGap   = (length - more * dash) / (closed ? more : more - 1);
    return moreGap <= 0 || std::fabs(fewerGap - gap) < std::fabs(moreGap - gap) ? fewerGap : moreGap;
}

static BorderDash BorderDashFor(CGFloat length, CGFloat width, bool dotted, bool closed)
{
    BorderDash out;
    if(width <= 0 || length <= 0)
        return out;
    if(!dotted || width <= 3)
    {
        CGFloat dash = width, gap = width;
        if(!dotted)
        {
            dash *= width >= 3 ? 2 : 3;
            gap *= width >= 3 ? 1 : 2;
        }
        if(length <= dash * 2)
            return out;
        CGFloat pair = closed ? dash + gap : 2 * dash + gap;
        out.solid    = false;
        if(length <= pair)
        {
            out.intervals[0] = dash * length / pair;
            out.intervals[1] = gap * length / pair;
            return out;
        }
        out.intervals[0] = dash;
        out.intervals[1] = dotted ? gap : BestDashGap(length, dash, gap, closed);
        return out;
    }
    out.solid = false;
    out.round = true;
    if(length < 2 * width)
    {
        out.intervals[1] = 2 * width;
        return out;
    }
    out.intervals[1] = BestDashGap(length, width, width, closed) + width - 0.01;
    if(!std::isfinite(out.intervals[1]))
        out.intervals[1] = length + 2 * width;
    return out;
}

static void ApplyBorderDash(CGContextRef ctx, const BorderDash& dash)
{
    CGContextSetLineCap(ctx, dash.round ? kCGLineCapRound : kCGLineCapButt);
    if(dash.solid)
        CGContextSetLineDash(ctx, 0, nullptr, 0);
    else
        CGContextSetLineDash(ctx, 0, dash.intervals, 2);
}

static bool InsideRoundedRect(CGRect r, const lh::border_radiuses& rad, CGRect b)
{
    if(!CGRectContainsRect(r, b))
        return false;
    const CGFloat hw = r.size.width / 2, hh = r.size.height / 2;
    auto clampx = [&](lh::pixel_t v) { return std::min<CGFloat>(std::max<CGFloat>(F(v), 0), hw); };
    auto clampy = [&](lh::pixel_t v) { return std::min<CGFloat>(std::max<CGFloat>(F(v), 0), hh); };
    CGFloat x0 = CGRectGetMinX(r), y0 = CGRectGetMinY(r), x1 = CGRectGetMaxX(r), y1 = CGRectGetMaxY(r);
    const CGRect corners[4] = {
        CGRectMake(x0, y0, clampx(rad.top_left_x), clampy(rad.top_left_y)),
        CGRectMake(x1 - clampx(rad.top_right_x), y0, clampx(rad.top_right_x), clampy(rad.top_right_y)),
        CGRectMake(x1 - clampx(rad.bottom_right_x), y1 - clampy(rad.bottom_right_y), clampx(rad.bottom_right_x), clampy(rad.bottom_right_y)),
        CGRectMake(x0, y1 - clampy(rad.bottom_left_y), clampx(rad.bottom_left_x), clampy(rad.bottom_left_y)),
    };
    for(const CGRect& corner : corners)
        if(corner.size.width > 0 && corner.size.height > 0 && CGRectIntersectsRect(corner, b))
            return false;
    return true;
}

static void ClipToRoundedRect(CGContextRef ctx, CGRect r, const lh::border_radiuses& rad)
{
    if(!HasRadius(rad))
    {
        CGContextClipToRect(ctx, r);
        return;
    }
    CGMutablePathRef p = CGPathCreateMutable();
    AddRoundedRect(p, r, rad);
    CGContextAddPath(ctx, p);
    TWClipPath(ctx, false);
    CGPathRelease(p);
}

namespace
{
struct ImageHandle
{
    CGImageRef  image = nullptr;
    CGSize      size  = CGSizeZero;
    std::string aspect;

    ImageHandle() = default;
    ImageHandle(CGImageRef i, CGSize s) : image(i ? CGImageRetain(i) : nullptr), size(s) {}
    ImageHandle(const ImageHandle& o) : ImageHandle(o.image, o.size) { aspect = o.aspect; }
    ImageHandle& operator=(const ImageHandle& o)
    {
        if(this != &o)
        {
            CGImageRef old = image;
            image          = o.image ? CGImageRetain(o.image) : nullptr;
            size           = o.size;
            aspect         = o.aspect;
            if(old)
                CGImageRelease(old);
        }
        return *this;
    }
    ~ImageHandle()
    {
        if(image)
            CGImageRelease(image);
    }
    explicit operator bool() const { return image != nullptr; }
};

struct ImageStore
{
    std::mutex                                   lock;
    std::unordered_map<std::string, ImageHandle> images;
    std::list<std::string>                       dataOrder;
    std::unordered_map<std::string, std::pair<std::list<std::string>::iterator, size_t>> dataMeta;
    size_t                                       dataBytes = 0;
};

ImageStore& Store()
{
    static ImageStore* store = new ImageStore();
    return *store;
}

const size_t kMaxDataImageBytes = 24 * 1024 * 1024;

CGImageRef CreateCGImageFromNSImage(NSImage* image, CGSize logical, CGFloat scale, NSColor* tint)
{
    if(!std::isfinite(logical.width) || !std::isfinite(logical.height) || logical.width <= 0 || logical.height <= 0)
        return nullptr;
    size_t pw = static_cast<size_t>(std::ceil(logical.width * scale));
    size_t ph = static_cast<size_t>(std::ceil(logical.height * scale));
    if(pw == 0 || ph == 0 || pw > 4096 || ph > 4096)
        return nullptr;
    CGContextRef ctx = CGBitmapContextCreate(nullptr, pw, ph, 8, 0, SRGB(),
                                             kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    if(!ctx)
        return nullptr;
    CGContextScaleCTM(ctx, scale, scale);
    NSGraphicsContext* gc = [NSGraphicsContext graphicsContextWithCGContext:ctx flipped:NO];
    [NSGraphicsContext saveGraphicsState];
    NSGraphicsContext.currentContext = gc;
    [image drawInRect:NSMakeRect(0, 0, logical.width, logical.height)
             fromRect:NSZeroRect
            operation:NSCompositingOperationSourceOver
             fraction:1.0];
    if(tint)
    {
        [tint setFill];
        NSRectFillUsingOperation(NSMakeRect(0, 0, logical.width, logical.height),
                                 NSCompositingOperationSourceAtop);
    }
    [NSGraphicsContext restoreGraphicsState];
    CGImageRef out = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    return out;
}

NSColor* ColorFromHex(NSString* hex)
{
    if(hex.length == 0)
        return nil;
    if([hex hasPrefix:@"#"])
        hex = [hex substringFromIndex:1];
    unsigned int v = 0;
    if(![[NSScanner scannerWithString:hex] scanHexInt:&v])
        return nil;
    if(hex.length == 8)
        return [NSColor colorWithSRGBRed:((v >> 24) & 0xff) / 255.0
                                   green:((v >> 16) & 0xff) / 255.0
                                    blue:((v >> 8) & 0xff) / 255.0
                                   alpha:(v & 0xff) / 255.0];
    return [NSColor colorWithSRGBRed:((v >> 16) & 0xff) / 255.0
                               green:((v >> 8) & 0xff) / 255.0
                                blue:(v & 0xff) / 255.0
                               alpha:1.0];
}

ImageHandle AdoptImage(CGImageRef image, CGSize size)
{
    ImageHandle out(image, size);
    if(image)
        CGImageRelease(image);
    return out;
}

std::string SVGRootAttribute(const std::string& tag, const std::string& name)
{
    std::string lower = tag;
    for(char& ch : lower)
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    for(size_t at = lower.find(name); at != std::string::npos; at = lower.find(name, at + 1))
    {
        if(at == 0 || !std::isspace(static_cast<unsigned char>(lower[at - 1])))
            continue;
        size_t i = at + name.size();
        while(i < lower.size() && std::isspace(static_cast<unsigned char>(lower[i])))
            i++;
        if(i >= lower.size() || lower[i] != '=')
            continue;
        i++;
        while(i < lower.size() && std::isspace(static_cast<unsigned char>(lower[i])))
            i++;
        if(i >= lower.size() || (lower[i] != '"' && lower[i] != '\''))
            continue;
        size_t end = lower.find(lower[i], i + 1);
        if(end == std::string::npos)
            return {};
        return tag.substr(i + 1, end - i - 1);
    }
    return {};
}

std::string SVGRootAspect(const std::string& markup)
{
    size_t open = std::string::npos;
    for(size_t at = markup.find('<'); at != std::string::npos; at = markup.find('<', at + 1))
    {
        if(at + 4 < markup.size() && std::tolower(static_cast<unsigned char>(markup[at + 1])) == 's' &&
           std::tolower(static_cast<unsigned char>(markup[at + 2])) == 'v' &&
           std::tolower(static_cast<unsigned char>(markup[at + 3])) == 'g' &&
           (std::isspace(static_cast<unsigned char>(markup[at + 4])) || markup[at + 4] == '>'))
        {
            open = at;
            break;
        }
    }
    if(open == std::string::npos)
        return {};
    size_t close = markup.find('>', open);
    if(close == std::string::npos)
        return {};
    const std::string tag = markup.substr(open + 4, close - open - 4);
    if(SVGRootAttribute(tag, "viewbox").empty())
        return {};
    std::string aspect = SVGRootAttribute(tag, "preserveaspectratio");
    std::string lower  = aspect;
    for(char& ch : lower)
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    if(lower.find("none") != std::string::npos)
        return {};
    return lower.empty() ? "xmidymid meet" : lower;
}

size_t PNGIntactRows(NSData* data)
{
    static const uint8_t signature[8] = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    const auto* bytes = static_cast<const uint8_t*>(data.bytes);
    const size_t length = data.length;
    if(length < 33 || std::memcmp(bytes, signature, 8) != 0)
        return SIZE_MAX;
    auto be32 = [&](size_t at) { return (uint32_t(bytes[at]) << 24) | (uint32_t(bytes[at + 1]) << 16) | (uint32_t(bytes[at + 2]) << 8) | bytes[at + 3]; };
    if(std::memcmp(bytes + 12, "IHDR", 4) != 0 || be32(8) < 13)
        return SIZE_MAX;
    const uint32_t width = be32(16), height = be32(20);
    const uint8_t  depth = bytes[24], type = bytes[25], interlace = bytes[28];
    const int      channels = type == 0 ? 1 : type == 2 ? 3 : type == 3 ? 1 : type == 4 ? 2 : type == 6 ? 4 : 0;
    if(!channels || interlace != 0 || width == 0 || height == 0)
        return SIZE_MAX;
    bool   suspect = true;
    size_t at      = 8;
    while(at + 12 <= length)
    {
        const size_t size = be32(at);
        if(size > length - at - 12)
            break;
        if(std::memcmp(bytes + at + 4, "IEND", 4) == 0)
        {
            suspect = false;
            break;
        }
        if(std::memcmp(bytes + at + 4, "IDAT", 4) == 0 && crc32(0, bytes + at + 4, static_cast<uInt>(size + 4)) != be32(at + 8 + size))
            break;
        at += size + 12;
    }
    if(!suspect)
        return SIZE_MAX;
    const uint64_t stride = (uint64_t(width) * channels * depth + 7) / 8 + 1;
    z_stream       z      = {};
    if(inflateInit(&z) != Z_OK)
        return SIZE_MAX;
    uint8_t  chunk[16384];
    uint64_t produced = 0, intact = 0;
    bool     broken = false;
    int      status = Z_OK;
    at = 8;
    while(!broken && intact < height && status == Z_OK && at + 12 <= length)
    {
        const size_t size = be32(at);
        if(size > length - at - 12 || std::memcmp(bytes + at + 4, "IEND", 4) == 0)
            break;
        if(std::memcmp(bytes + at + 4, "IDAT", 4) == 0)
        {
            z.next_in  = const_cast<Bytef*>(bytes + at + 8);
            z.avail_in = static_cast<uInt>(size);
            while(!broken && intact < height && status == Z_OK && z.avail_in > 0)
            {
                z.next_out  = chunk;
                z.avail_out = sizeof(chunk);
                status      = inflate(&z, Z_NO_FLUSH);
                const uint64_t end = produced + (sizeof(chunk) - z.avail_out);
                while(intact < height)
                {
                    const uint64_t row = intact * stride;
                    if(row >= end)
                        break;
                    if(row >= produced && chunk[row - produced] > 4)
                    {
                        broken = true;
                        break;
                    }
                    if(row + stride > end)
                        break;
                    intact++;
                }
                produced = end;
            }
        }
        at += size + 12;
    }
    inflateEnd(&z);
    return intact < height ? static_cast<size_t>(intact) : SIZE_MAX;
}

CGImageRef CreateImageKeepingRows(CGImageRef image, size_t keep)
{
    const size_t    w     = CGImageGetWidth(image), h = CGImageGetHeight(image);
    CGColorSpaceRef space = CGImageGetColorSpace(image);
    if(!space || CGColorSpaceGetModel(space) != kCGColorSpaceModelRGB)
        space = SRGB();
    CGContextRef ctx = CGBitmapContextCreate(nullptr, w, h, 8, 0, space, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    if(!ctx)
        return CGImageRetain(image);
    CGContextClipToRect(ctx, CGRectMake(0, CGFloat(h - keep), CGFloat(w), CGFloat(keep)));
    CGContextDrawImage(ctx, CGRectMake(0, 0, CGFloat(w), CGFloat(h)), image);
    CGImageRef out = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    return out ?: CGImageRetain(image);
}

ImageHandle LoadImageFromData(NSData* data, bool isSVG)
{
    if(!data)
        return {};
    if(!isSVG)
    {
        CGImageSourceRef src = CGImageSourceCreateWithData((__bridge CFDataRef)data, nullptr);
        if(src)
        {
            CGImageRef image = CGImageSourceCreateImageAtIndex(src, 0, nullptr);
            CFRelease(src);
            size_t intact = image ? PNGIntactRows(data) : SIZE_MAX;
            if(intact < CGImageGetHeight(image))
            {
                CGImageRef kept = CreateImageKeepingRows(image, intact);
                CGImageRelease(image);
                image = kept;
            }
            if(image)
                return AdoptImage(image, CGSizeMake(CGImageGetWidth(image), CGImageGetHeight(image)));
        }
        return {};
    }
    CGSize      size   = CGSizeZero;
    std::string markup(static_cast<const char*>(data.bytes), data.length);
    CGImageRef  image  = LHCreateSVGImage(markup, &size);
    ImageHandle handle = image ? AdoptImage(image, size) : ImageHandle();
    if(handle)
        handle.aspect = SVGRootAspect(markup);
    return handle;
}

ImageHandle LoadDataURI(const std::string& url)
{
    auto comma = url.find(',');
    if(comma == std::string::npos)
        return {};
    std::string meta    = url.substr(5, comma - 5);
    std::string payload = url.substr(comma + 1);
    bool        base64  = meta.find(";base64") != std::string::npos;
    bool        isSVG   = meta.find("svg") != std::string::npos;
    NSData*     data    = nil;
    NSString*   str     = [NSString stringWithUTF8String:payload.c_str()];
    if(!str)
        return {};
    if(base64)
        data = [[NSData alloc] initWithBase64EncodedString:str
                                                   options:NSDataBase64DecodingIgnoreUnknownCharacters];
    else
        data = [(str.stringByRemovingPercentEncoding ?: str) dataUsingEncoding:NSUTF8StringEncoding];
    return LoadImageFromData(data, isSVG);
}

ImageHandle LoadSymbol(const std::string& url)
{
    if(@available(macOS 11.0, *))
    {
        NSURLComponents* comps =
            [NSURLComponents componentsWithString:[@"sf://x/" stringByAppendingString:@(url.c_str() + 3)]];
        NSString*    name       = comps.path.lastPathComponent;
        CGFloat      size       = 20;
        NSColor*     tint       = [NSColor colorWithSRGBRed:0.25 green:0.25 blue:0.27 alpha:1];
        NSFontWeight weight     = NSFontWeightRegular;
        bool         multicolor = false;
        for(NSURLQueryItem* q in comps.queryItems)
        {
            if([q.name isEqualToString:@"size"])
                size = std::clamp(q.value.doubleValue, 1.0, 512.0);
            else if([q.name isEqualToString:@"color"])
                tint = ColorFromHex(q.value) ?: tint;
            else if([q.name isEqualToString:@"weight"])
                weight = [q.value isEqualToString:@"bold"]       ? NSFontWeightBold
                         : [q.value isEqualToString:@"semibold"] ? NSFontWeightSemibold
                         : [q.value isEqualToString:@"light"]    ? NSFontWeightLight
                                                                 : NSFontWeightRegular;
            else if([q.name isEqualToString:@"mode"])
                multicolor = [q.value isEqualToString:@"multicolor"];
        }
        if(name.length == 0)
            return {};
        NSImage* sym = [NSImage imageWithSystemSymbolName:name accessibilityDescription:nil];
        if(!sym)
            return {};
        NSImageSymbolConfiguration* cfg = [NSImageSymbolConfiguration configurationWithPointSize:size weight:weight];
        bool tinted = true;
        if(multicolor)
        {
            if(@available(macOS 12.0, *))
            {
                cfg    = [cfg configurationByApplyingConfiguration:[NSImageSymbolConfiguration configurationPreferringMulticolor]];
                tinted = false;
            }
        }
        sym = [sym imageWithSymbolConfiguration:cfg] ?: sym;
        return AdoptImage(CreateCGImageFromNSImage(sym, sym.size, 2.0, tinted ? tint : nil), sym.size);
    }
    return {};
}

std::atomic<bool> g_portableSymbols{getenv("LH_PORTABLE_SYMBOLS") != nullptr};

bool HasScheme(const std::string& url, const char* scheme)
{
    size_t n = std::strlen(scheme);
    if(url.size() < n)
        return false;
    for(size_t i = 0; i < n; i++)
        if(std::tolower(static_cast<unsigned char>(url[i])) != scheme[i])
            return false;
    return true;
}

ImageHandle LoadIcon(const std::string& url)
{
    auto icon = twa::parseURL(url);
    if(!icon)
        return {};
    CGSize     size  = CGSizeZero;
    CGImageRef image = LHCreateSVGImage(twa::svg(*icon), &size);
    return image ? AdoptImage(image, size) : ImageHandle();
}

NSImage* (^g_provider)(NSString* url) = nil;
std::mutex& g_providerLock = *new std::mutex;

ImageHandle LoadProvided(const std::string& url)
{
    NSImage* (^provider)(NSString* url) = nil;
    {
        std::lock_guard<std::mutex> guard(g_providerLock);
        provider = g_provider;
    }
    NSString* string = provider ? [NSString stringWithUTF8String:url.c_str()] : nil;
    NSImage*  image  = string ? provider(string) : nil;
    if(!image)
        return {};
    if(image.representations.count == 1)
    {
        NSImageRep* rep = image.representations.firstObject;
        if([rep isKindOfClass:[NSBitmapImageRep class]] || [NSStringFromClass([rep class]) isEqualToString:@"NSCGImageSnapshotRep"])
        {
            CGImageRef direct = [image CGImageForProposedRect:nullptr context:nil hints:nil];
            if(direct && CGImageGetWidth(direct) <= 4096 && CGImageGetHeight(direct) <= 4096)
                return AdoptImage(CGImageRetain(direct), image.size);
        }
    }
    return AdoptImage(CreateCGImageFromNSImage(image, image.size, 2.0, nil), image.size);
}

ImageHandle LookupImage(const std::string& url)
{
    if(url.empty())
        return {};
    bool        isData   = url.rfind("data:", 0) == 0;
    if(!isData && LHRenderingSVGImage())
        return {};
    bool        isIcon   = !isData && HasScheme(url, "icon:");
    bool        isSymbol = !isData && !isIcon && url.rfind("sf:", 0) == 0;
    bool        bounded  = isData || isIcon || isSymbol;
    ImageStore& store    = Store();
    {
        std::lock_guard<std::mutex> guard(store.lock);
        auto                        it = store.images.find(url);
        if(it != store.images.end())
        {
            if(bounded)
            {
                auto mit = store.dataMeta.find(url);
                if(mit != store.dataMeta.end())
                    store.dataOrder.splice(store.dataOrder.end(), store.dataOrder, mit->second.first);
            }
            return it->second;
        }
    }
    ImageHandle img;
    if(isData)
        img = LoadDataURI(url);
    else if(isIcon)
        img = LoadIcon(url);
    else if(isSymbol)
    {
        if(!g_portableSymbols.load(std::memory_order_relaxed))
            img = LoadSymbol(url);
        if(!img)
            img = LoadIcon(url);
    } else
    {
        img = LoadProvided(url);
        if(!img)
            return {};
    }

    std::lock_guard<std::mutex> guard(store.lock);
    if(bounded)
    {
        auto existing = store.images.find(url);
        if(existing != store.images.end())
        {
            auto mit = store.dataMeta.find(url);
            if(mit != store.dataMeta.end())
                store.dataOrder.splice(store.dataOrder.end(), store.dataOrder, mit->second.first);
            return existing->second;
        }
        size_t bytes = (img.image ? CGImageGetBytesPerRow(img.image) * CGImageGetHeight(img.image) : 16 * 1024) + url.size();
        while(store.dataBytes + bytes > kMaxDataImageBytes && !store.dataOrder.empty())
        {
            std::string victim = store.dataOrder.front();
            auto        vit    = store.dataMeta.find(victim);
            if(vit != store.dataMeta.end())
            {
                store.dataBytes -= vit->second.second;
                store.dataMeta.erase(vit);
            }
            store.images.erase(victim);
            store.dataOrder.pop_front();
        }
        store.dataOrder.push_back(url);
        store.dataMeta[url] = {std::prev(store.dataOrder.end()), bytes};
        store.dataBytes += bytes;
    }
    store.images[url] = img;
    return img;
}
}

void LHRegisterAppImage(NSString* name, NSImage* image)
{
    ImageHandle img = AdoptImage(CreateCGImageFromNSImage(image, image.size, 2.0, nil), image.size);
    std::string key = std::string("app:") + name.UTF8String;
    ImageStore& store = Store();
    std::lock_guard<std::mutex> guard(store.lock);
    store.images[key] = img;
}

CGImageRef LHCopyImageForURL(NSString* url)
{
    if(url.length == 0 || (![url hasPrefix:@"data:"] && ![url.lowercaseString hasPrefix:@"icon:"]))
        return nullptr;
    ImageHandle img = LookupImage(url.UTF8String ?: "");
    return img.image ? CGImageRetain(img.image) : nullptr;
}

CGSize LHImageSizeForURL(NSString* url)
{
    if(url.length == 0 || (![url hasPrefix:@"data:"] && ![url.lowercaseString hasPrefix:@"icon:"]))
        return CGSizeZero;
    ImageHandle img = LookupImage(url.UTF8String ?: "");
    return img ? img.size : CGSizeZero;
}

NSImage* (^LHImageProvider(void))(NSString* url)
{
    std::lock_guard<std::mutex> guard(g_providerLock);
    return g_provider;
}

void LHSetImageProvider(NSImage* (^provider)(NSString* url))
{
    std::lock_guard<std::mutex> guard(g_providerLock);
    g_provider = [provider copy];
}

void LHSetPortableSymbols(bool portable)
{
    if(g_portableSymbols.exchange(portable) == portable)
        return;
    ImageStore& store = Store();
    std::lock_guard<std::mutex> guard(store.lock);
    for(auto it = store.dataOrder.begin(); it != store.dataOrder.end();)
    {
        if(it->rfind("sf:", 0) != 0)
        {
            ++it;
            continue;
        }
        auto meta = store.dataMeta.find(*it);
        if(meta != store.dataMeta.end())
        {
            store.dataBytes -= meta->second.second;
            store.dataMeta.erase(meta);
        }
        store.images.erase(*it);
        it = store.dataOrder.erase(it);
    }
}

bool LHPortableSymbols(void)
{
    return g_portableSymbols.load(std::memory_order_relaxed);
}

void LHForgetImage(NSString* url)
{
    ImageStore& store = Store();
    std::lock_guard<std::mutex> guard(store.lock);
    store.images.erase(std::string(url.UTF8String));
}

static void DrawCGImage(CGContextRef ctx, CGImageRef img, CGRect r)
{
    CGContextSaveGState(ctx);
    CGContextTranslateCTM(ctx, r.origin.x, r.origin.y + r.size.height);
    CGContextScaleCTM(ctx, 1, -1);
    CGContextSetInterpolationQuality(ctx, kCGInterpolationHigh);
    CGContextDrawImage(ctx, CGRectMake(0, 0, r.size.width, r.size.height), img);
    CGContextRestoreGState(ctx);
}

namespace {

class RasterCache
{
  public:
    CGImageRef copy(const std::string& key)
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        auto                        it = m_entries.find(key);
        if(it == m_entries.end())
            return nullptr;
        m_order.splice(m_order.begin(), m_order, it->second.position);
        return CGImageRetain(it->second.image);
    }

    void put(const std::string& key, CGImageRef image, size_t bytes)
    {
        if(bytes > kBudget / 4)
            return;
        std::lock_guard<std::mutex> lock(m_mutex);
        if(m_entries.count(key))
            return;
        m_order.push_front(key);
        m_entries.emplace(key, Entry{CGImageRetain(image), bytes, m_order.begin()});
        m_bytes += bytes;
        while(m_bytes > kBudget && !m_order.empty())
        {
            auto victim = m_entries.find(m_order.back());
            m_bytes -= victim->second.bytes;
            CGImageRelease(victim->second.image);
            m_entries.erase(victim);
            m_order.pop_back();
        }
    }

    void clear()
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        for(auto& entry : m_entries)
            CGImageRelease(entry.second.image);
        m_entries.clear();
        m_order.clear();
        m_bytes = 0;
    }

    size_t bytes()
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        return m_bytes;
    }

  private:
    struct Entry
    {
        CGImageRef                       image;
        size_t                           bytes;
        std::list<std::string>::iterator position;
    };
    static constexpr size_t                kBudget = 32 << 20;
    std::mutex                             m_mutex;
    std::unordered_map<std::string, Entry> m_entries;
    std::list<std::string>                 m_order;
    size_t                                 m_bytes = 0;
};

RasterCache& SharedRasterCache()
{
    static RasterCache* cache = new RasterCache();
    return *cache;
}

bool Integral(CGFloat v)
{
    return std::fabs(v - std::round(v)) < 0.001;
}

void AppendInteger(std::string& key, uint64_t v)
{
    key.append(reinterpret_cast<const char*>(&v), sizeof(v));
}

void AppendNumber(std::string& key, double v)
{
    key.append(reinterpret_cast<const char*>(&v), sizeof(v));
}

}

// Paints `paint` (which draws in the context's user space) for the area `r` into a cached bitmap of
// exactly r's device size and copies it 1:1. CoreGraphics dithers gradients and resamples images
// relative to the current clip, so drawing them straight into a partially redrawn tile gives
// slightly different pixels than a full redraw; a pixel-aligned copy does not. Returns false when the
// transform is not axis-aligned or r is not on device pixels, and the caller draws directly.
static const CGFloat kMaxTextShadowBlur = 200;
static const CGFloat kMaxTextShadowOffset = 400;
static const size_t kMaxBackdrops = 8;
static const size_t kMaxTextShadows = 8;

static bool DrawDownsampled(CGContextRef ctx, CGRect r, std::string key, CGFloat blur, const std::function<void(CGContextRef)>& paint)
{
    CGAffineTransform m = CGContextGetCTM(ctx);
    if(std::fabs(m.b) > 1e-9 || std::fabs(m.c) > 1e-9 || r.size.width <= 0 || r.size.height <= 0)
        return false;
    CGRect device = CGContextConvertRectToDeviceSpace(ctx, r);
    CGFloat sx = std::fabs(m.a), sy = std::fabs(m.d);
    CGFloat pixels = device.size.width * device.size.height;
    CGFloat k = std::min<CGFloat>(1, 24 / std::max<CGFloat>(blur * std::max(sx, sy), 1));
    if(k >= 0.999 || !(k > 0))
        return false;
    k = std::min<CGFloat>(k, std::sqrt((1 << 20) / std::max<CGFloat>(pixels, 1)));
    size_t pw = (size_t)std::max<double>(1, std::ceil(device.size.width * k)), ph = (size_t)std::max<double>(1, std::ceil(device.size.height * k));
    CGFloat kx = pw / std::max<CGFloat>(r.size.width, 1e-6), ky = ph / std::max<CGFloat>(r.size.height, 1e-6);
    AppendNumber(key, (double)pw);
    AppendNumber(key, (double)ph);
    AppendNumber(key, sx);
    AppendNumber(key, sy);
    AppendNumber(key, m.d < 0 ? -1 : 1);
    CGColorSpaceRef space = CGBitmapContextGetColorSpace(ctx);
    if(!space || CGColorSpaceGetModel(space) != kCGColorSpaceModelRGB)
        space = SRGB();
    AppendInteger(key, (uintptr_t)space);
    CGImageRef image = SharedRasterCache().copy(key);
    if(!image)
    {
        CGContextRef bitmap = CGBitmapContextCreate(nullptr, pw, ph, 8, 0, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
        if(!bitmap)
            return false;
        if(m.d < 0)
        {
            CGContextTranslateCTM(bitmap, 0, ph);
            CGContextScaleCTM(bitmap, kx, -ky);
        }
        else
            CGContextScaleCTM(bitmap, kx, ky);
        CGContextTranslateCTM(bitmap, -r.origin.x, -r.origin.y);
        CGContextSetShouldAntialias(bitmap, true);
        paint(bitmap);
        image = CGBitmapContextCreateImage(bitmap);
        CGContextRelease(bitmap);
        if(!image)
            return false;
        SharedRasterCache().put(key, image, pw * ph * 4);
    }
    CGContextSaveGState(ctx);
    CGContextSetInterpolationQuality(ctx, kCGInterpolationMedium);
    if(m.d < 0)
    {
        CGContextTranslateCTM(ctx, r.origin.x, r.origin.y + r.size.height);
        CGContextScaleCTM(ctx, 1, -1);
    }
    else
        CGContextTranslateCTM(ctx, r.origin.x, r.origin.y);
    CGContextDrawImage(ctx, CGRectMake(0, 0, r.size.width, r.size.height), image);
    CGContextRestoreGState(ctx);
    CGImageRelease(image);
    return true;
}

static bool DrawRasterized(CGContextRef ctx, CGRect r, std::string key, const std::function<void(CGContextRef)>& paint, bool store = true, size_t stretchX = 0, size_t stretchY = 0)
{
    static const bool disabled = getenv("LH_NO_RASTER_CACHE") != nullptr;
    if(disabled)
        return false;
    CGAffineTransform m = CGContextGetCTM(ctx);
    if(std::fabs(m.b) > 1e-9 || std::fabs(m.c) > 1e-9 || r.size.width <= 0 || r.size.height <= 0)
        return false;
    CGRect device = CGContextConvertRectToDeviceSpace(ctx, r);
    if(!Integral(device.origin.x) || !Integral(device.origin.y) || !Integral(device.size.width) || !Integral(device.size.height))
        return false;
    size_t pw = (size_t)std::llround(device.size.width), ph = (size_t)std::llround(device.size.height);
    if(pw == 0 || ph == 0 || pw * ph > (4 << 20))
        return false;
    if(!CGRectIntersectsRect(r, CGContextGetClipBoundingBox(ctx)))
        return true;
    CGFloat sx = std::fabs(m.a), sy = std::fabs(m.d);
    if(stretchX && (stretchX >= pw || pw - stretchX < 3))
        return false;
    if(stretchY && (stretchX || stretchY >= ph || ph - stretchY < 3))
        return false;
    pw -= stretchX;
    ph -= stretchY;
    AppendNumber(key, (double)pw);
    AppendNumber(key, (double)ph);
    AppendNumber(key, sx);
    AppendNumber(key, sy);
    AppendNumber(key, m.d < 0 ? -1 : 1);
    CGColorSpaceRef space = CGBitmapContextGetColorSpace(ctx);
    if(!space || CGColorSpaceGetModel(space) != kCGColorSpaceModelRGB)
        space = SRGB();
    AppendInteger(key, (uintptr_t)space);
    CGImageRef image = store ? SharedRasterCache().copy(key) : nullptr;
    if(!image)
    {
        CGContextRef bitmap = CGBitmapContextCreate(nullptr, pw, ph, 8, 0, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
        if(!bitmap)
            return false;
        if(m.d < 0)
        {
            CGContextTranslateCTM(bitmap, 0, ph);
            CGContextScaleCTM(bitmap, sx, -sy);
        }
        else
            CGContextScaleCTM(bitmap, sx, sy);
        CGContextTranslateCTM(bitmap, -r.origin.x, -r.origin.y);
        CGContextSetAllowsFontSmoothing(bitmap, false);
        CGContextSetShouldAntialias(bitmap, true);
        paint(bitmap);
        image = CGBitmapContextCreateImage(bitmap);
        CGContextRelease(bitmap);
        if(!image)
            return false;
        if(store)
            SharedRasterCache().put(key, image, pw * ph * 4);
    }
    CGContextSaveGState(ctx);
    CGContextSetInterpolationQuality(ctx, kCGInterpolationNone);
    if(m.d < 0)
    {
        CGContextTranslateCTM(ctx, r.origin.x, r.origin.y + r.size.height);
        CGContextScaleCTM(ctx, 1, -1);
    }
    else
        CGContextTranslateCTM(ctx, r.origin.x, r.origin.y);
    if(stretchX || stretchY)
    {
        size_t length = stretchX ? pw : ph;
        size_t leading = length / 2, trailing = length - leading - 1;
        std::array<CGRect, 3> slices = stretchX
            ? std::array<CGRect, 3>{CGRectMake(0, 0, leading, ph), CGRectMake(leading, 0, 1, ph), CGRectMake(leading + 1, 0, trailing, ph)}
            : std::array<CGRect, 3>{CGRectMake(0, 0, pw, leading), CGRectMake(0, leading, pw, 1), CGRectMake(0, leading + 1, pw, trailing)};
        std::array<CGImageRef, 3> parts = {};
        bool complete = true;
        for(size_t i = 0; i < parts.size(); i++)
        {
            parts[i] = CGImageCreateWithImageInRect(image, slices[i]);
            complete = complete && parts[i] != nullptr;
        }
        if(!complete)
        {
            for(auto part : parts)
                if(part)
                    CGImageRelease(part);
            CGContextRestoreGState(ctx);
            CGImageRelease(image);
            return false;
        }
        for(size_t i = 0; i < parts.size(); i++)
        {
            CGRect slice = slices[i];
            CGRect target;
            if(stretchX)
            {
                target = CGRectMake(slice.origin.x / sx, 0, slice.size.width / sx, r.size.height);
                if(i == 1)
                    target.size.width += stretchX / sx;
                else if(i == 2)
                    target.origin.x += stretchX / sx;
            }
            else
            {
                target = CGRectMake(0, (ph - CGRectGetMaxY(slice)) / sy, r.size.width, slice.size.height / sy);
                if(i == 1)
                    target.size.height += stretchY / sy;
                else if(i == 0)
                    target.origin.y += stretchY / sy;
            }
            CGContextDrawImage(ctx, target, parts[i]);
            CGImageRelease(parts[i]);
        }
    }
    else
        CGContextDrawImage(ctx, CGRectMake(0, 0, r.size.width, r.size.height), image);
    CGContextRestoreGState(ctx);
    CGImageRelease(image);
    return true;
}

static bool DrawAxisLinearGradient(CGContextRef ctx, CGGradientRef gradient, CGPoint start, CGPoint end)
{
    static const bool disabled = getenv("LH_NO_GRADIENT_STRIP") != nullptr;
    if(disabled || CGBitmapContextGetData(ctx) == nullptr)
        return false;
    CGAffineTransform m = CGContextGetCTM(ctx);
    if(std::fabs(m.b) > 1e-9 || std::fabs(m.c) > 1e-9)
        return false;
    CGPoint a = CGPointApplyAffineTransform(start, m), b = CGPointApplyAffineTransform(end, m);
    bool vertical = std::fabs(a.x - b.x) < 1e-6, horizontal = std::fabs(a.y - b.y) < 1e-6;
    if(vertical == horizontal)
        return false;
    CGRect device = CGRectIntegral(CGRectApplyAffineTransform(CGContextGetClipBoundingBox(ctx), m));
    device = CGRectIntersection(device, CGRectMake(0, 0, CGBitmapContextGetWidth(ctx), CGBitmapContextGetHeight(ctx)));
    if(CGRectIsEmpty(device) || device.size.width < 4 || device.size.height < 4)
        return false;
    size_t pw = (size_t)device.size.width, ph = (size_t)device.size.height;
    if(pw * ph > (4 << 20))
        return false;
    size_t w = vertical ? 1 : pw, h = vertical ? ph : 1;
    CGColorSpaceRef space = CGBitmapContextGetColorSpace(ctx);
    if(!space || CGColorSpaceGetModel(space) != kCGColorSpaceModelRGB)
        space = SRGB();
    const uint32_t info = kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little;
    CGContextRef strip = CGBitmapContextCreate(nullptr, w, h, 8, w * 4, space, info);
    if(!strip)
        return false;
    CGPoint origin = CGPointMake(vertical ? a.x - 0.5 : device.origin.x, vertical ? device.origin.y : a.y - 0.5);
    CGContextTranslateCTM(strip, -origin.x, -origin.y);
    CGContextDrawLinearGradient(strip, gradient, a, b, kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation);
    const uint32_t* line = static_cast<const uint32_t*>(CGBitmapContextGetData(strip));
    CGContextRef full = line ? CGBitmapContextCreate(nullptr, pw, ph, 8, 0, space, info) : nullptr;
    if(!full)
    {
        CGContextRelease(strip);
        return false;
    }
    auto* bytes = static_cast<uint8_t*>(CGBitmapContextGetData(full));
    size_t row = CGBitmapContextGetBytesPerRow(full);
    for(size_t y = 0; y < ph; y++)
    {
        if(vertical)
            memset_pattern4(bytes + y * row, line + y, pw * 4);
        else
            memcpy(bytes + y * row, line, pw * 4);
    }
    CGContextRelease(strip);
    CGImageRef image = CGBitmapContextCreateImage(full);
    CGContextRelease(full);
    if(!image)
        return false;
    CGContextSaveGState(ctx);
    CGContextConcatCTM(ctx, CGAffineTransformInvert(m));
    CGContextSetInterpolationQuality(ctx, kCGInterpolationNone);
    CGContextDrawImage(ctx, device, image);
    CGContextRestoreGState(ctx);
    CGImageRelease(image);
    return true;
}

namespace {

struct SharedFont
{
    CTFontRef       font  = nullptr;
    CFDictionaryRef attrs = nullptr;
    CGFloat         ascent = 0, descent = 0, x_height = 0;
    CGFloat         underline_pos = 0, underline_thickness = 1;
    CGFloat         kern = 0;
    CFNumberRef     spaceKern = nullptr;
    bool            colorGlyphs = false;
    std::mutex                                widthsMutex;
    std::unordered_map<std::string, CGFloat> widths;
    std::unordered_map<std::string, CGFloat> oldWidths;
    CFDictionaryRef drawAttrs = nullptr;
    std::mutex                                  linesMutex;
    std::unordered_map<std::string, CTLineRef> lines;
    std::unordered_map<std::string, CTLineRef> oldLines;
    std::unique_ptr<LHSimpleShaper>             simple;

    ~SharedFont()
    {
        for(auto& entry : lines)
            CFRelease(entry.second);
        for(auto& entry : oldLines)
            CFRelease(entry.second);
        if(drawAttrs)
            CFRelease(drawAttrs);
        if(spaceKern)
            CFRelease(spaceKern);
        if(attrs)
            CFRelease(attrs);
        if(font)
            CFRelease(font);
    }
};

std::mutex&                                                   gFontCacheMutex = *new std::mutex;
std::unordered_map<std::string, std::shared_ptr<SharedFont>>& gFontCache = *new std::unordered_map<std::string, std::shared_ptr<SharedFont>>;
constexpr size_t                                             kFontCacheLimit = 256;

}

struct LHTextShadow
{
    CGFloat       x = 0, y = 0, blur = 0;
    lh::web_color color = lh::web_color::transparent;
};

struct LHContainer::Font
{
    static uint64_t allocatePaintIdentity()
    {
        static std::atomic<uint64_t> next{1};
        uint64_t identity = next.load(std::memory_order_relaxed);
        while(identity && !next.compare_exchange_weak(identity, identity + 1, std::memory_order_relaxed))
        {
        }
        return identity;
    }

    const uint64_t paintIdentity = allocatePaintIdentity();
    std::shared_ptr<SharedFont> shared;
    CGFloat         shadow_x = 0, shadow_y = 0, shadow_blur = 0;
    lh::web_color   shadow_color = lh::web_color::transparent;
    std::vector<LHTextShadow> shadows;
    bool            underline = false, overline = false, strike = false;
    lh::web_color   deco_color = lh::web_color::current_color;
    int             deco_style = lh::text_decoration_style_solid;
    CGFloat         deco_thickness = 0;
    int             smoothing = lh::font_smoothing_auto;
};

static NSFontWeight NSWeightFromCSS(int w)
{
    static const NSFontWeight table[] = {NSFontWeightUltraLight, NSFontWeightThin, NSFontWeightLight,
                                         NSFontWeightRegular, NSFontWeightMedium, NSFontWeightSemibold,
                                         NSFontWeightBold, NSFontWeightHeavy, NSFontWeightBlack};
    int idx = std::clamp((w + 50) / 100, 1, 9) - 1;
    return table[idx];
}

static NSInteger ManagerWeightFromCSS(int w)
{
    static const NSInteger table[] = {2, 3, 4, 5, 6, 8, 9, 10, 11};
    return table[std::clamp((w + 50) / 100, 1, 9) - 1];
}

static bool HasWeightAxis(NSFont* font)
{
    static std::mutex&                            mutex = *new std::mutex;
    static std::unordered_map<std::string, bool>& known = *new std::unordered_map<std::string, bool>;
    const int                                    axis = 0x77676874;
    NSString*                                    name = CFBridgingRelease(CTFontCopyPostScriptName((__bridge CTFontRef)font));
    const std::string                            key  = name.UTF8String ?: "";
    if(!key.empty())
    {
        std::lock_guard<std::mutex> lock(mutex);
        auto                        it = known.find(key);
        if(it != known.end())
            return it->second;
    }
    NSArray* axes      = CFBridgingRelease(CTFontCopyVariationAxes((__bridge CTFontRef)font));
    bool     hasWeight = false;
    for(NSDictionary* item in axes)
        hasWeight = hasWeight || [item[(__bridge NSString*)kCTFontVariationAxisIdentifierKey] intValue] == axis;
    if(!key.empty())
    {
        std::lock_guard<std::mutex> lock(mutex);
        if(known.size() > 512)
            known.clear();
        known.emplace(key, hasWeight);
    }
    return hasWeight;
}

static NSFont* WithCSSWeight(NSFont* font, int cssWeight)
{
    if(!font)
        return font;
    const int axis = 0x77676874;
    if(!HasWeightAxis(font))
        return font;
    CTFontDescriptorRef varied = CTFontDescriptorCreateCopyWithVariation((__bridge CTFontDescriptorRef)font.fontDescriptor,
                                                                         (__bridge CFNumberRef) @(axis), std::clamp(cssWeight, 1, 1000));
    CTFontRef result = CTFontCreateWithFontDescriptor(varied, font.pointSize, nullptr);
    CFRelease(varied);
    return result ? (NSFont*)CFBridgingRelease(result) : font;
}

static NSFont* SystemFontWithDesign(CGFloat size, NSFontWeight weight, bool serif)
{
    NSFont* base = [NSFont systemFontOfSize:size weight:weight];
    if(@available(macOS 10.15, *))
    {
        NSFontDescriptor* d = [base.fontDescriptor fontDescriptorWithDesign:serif ? NSFontDescriptorSystemDesignSerif
                                                                                  : NSFontDescriptorSystemDesignRounded];
        return d ? ([NSFont fontWithDescriptor:d size:size] ?: base) : base;
    }
    if(serif)
        return [NSFont fontWithName:@"Times New Roman" size:size] ?: base;
    return base;
}

static NSFont* MonospacedFont(CGFloat size, NSFontWeight weight)
{
    if(@available(macOS 10.15, *))
        return [NSFont monospacedSystemFontOfSize:size weight:weight];
    NSFont* menlo = [NSFont fontWithName:weight >= NSFontWeightSemibold ? @"Menlo-Bold" : @"Menlo-Regular" size:size];
    return menlo ?: ([NSFont userFixedPitchFontOfSize:size] ?: [NSFont systemFontOfSize:size weight:weight]);
}

static NSFont* ResolveFont(const std::string& families, CGFloat size, int cssWeight, bool italic)
{
    NSFontWeight weight = NSWeightFromCSS(cssWeight);
    NSFont*      font   = nil;

    NSArray<NSString*>* tokens = [@(families.c_str()) componentsSeparatedByString:@","];
    NSCharacterSet*     trim   = [NSCharacterSet characterSetWithCharactersInString:@" \t\r\n\"'"];
    for(NSString* raw in tokens)
    {
        NSString* name  = [raw stringByTrimmingCharactersInSet:trim];
        NSString* lower = name.lowercaseString;
        if(name.length == 0)
            continue;

        if([@[@"-apple-system", @"system-ui", @"blinkmacsystemfont", @"ui-sans-serif",
              @"sf pro", @"sf pro text", @"sf pro display"] containsObject:lower])
            font = WithCSSWeight([NSFont systemFontOfSize:size weight:NSFontWeightRegular], cssWeight);
        else if([@[@"ui-monospace", @"sf mono"] containsObject:lower])
            font = WithCSSWeight(MonospacedFont(size, NSFontWeightRegular), cssWeight);
        else if([lower isEqualToString:@"ui-serif"])
            font = WithCSSWeight(SystemFontWithDesign(size, NSFontWeightRegular, true), cssWeight);
        else if([@[@"ui-rounded", @"sf pro rounded"] containsObject:lower])
            font = WithCSSWeight(SystemFontWithDesign(size, NSFontWeightRegular, false), cssWeight);
        else
        {
            NSString* family = [lower isEqualToString:@"serif"]        ? @"Times"
                               : [lower isEqualToString:@"sans-serif"] ? @"Helvetica"
                               : [lower isEqualToString:@"monospace"]  ? @"Courier"
                               : [lower isEqualToString:@"cursive"]    ? @"Apple Chancery"
                               : [lower isEqualToString:@"fantasy"]    ? @"Papyrus"
                                                                        : name;
            NSFontManager* fm = NSFontManager.sharedFontManager;
            if([fm availableMembersOfFontFamily:family].count)
            {
                NSFontTraitMask traits = (italic ? NSItalicFontMask : 0) | (cssWeight >= 600 ? NSBoldFontMask : 0);
                NSInteger       mw     = ManagerWeightFromCSS(cssWeight);
                font = [fm fontWithFamily:family traits:traits weight:mw size:size]
                           ?: [fm fontWithFamily:family traits:0 weight:mw size:size]
                           ?: [fm fontWithFamily:family traits:0 weight:5 size:size];
            }
        }
        if(font)
            break;
    }
    if(!font)
        font = WithCSSWeight([NSFont systemFontOfSize:size weight:NSFontWeightRegular], cssWeight);

    if(italic && !(font.fontDescriptor.symbolicTraits & NSFontDescriptorTraitItalic))
    {
        NSFontDescriptor* d =
            [font.fontDescriptor fontDescriptorWithSymbolicTraits:font.fontDescriptor.symbolicTraits |
                                                                  NSFontDescriptorTraitItalic];
        NSFont* it = d ? [NSFont fontWithDescriptor:d size:size] : nil;
        if(it && (it.fontDescriptor.symbolicTraits & NSFontDescriptorTraitItalic))
            font = HasWeightAxis(font) ? WithCSSWeight(it, cssWeight) : it;
        else
        {
            NSAffineTransform*      skew = [NSAffineTransform transform];
            NSAffineTransformStruct t    = {size, 0, size * 0.2, size, 0, 0};
            skew.transformStruct         = t;
            font = [NSFont fontWithDescriptor:font.fontDescriptor textTransform:skew] ?: font;
        }
    }
    return font;
}

NSFont* LHResolveFont(NSString* families, CGFloat size, int weight, bool italic)
{
    return ResolveFont(families.UTF8String ?: "", size, weight, italic);
}

size_t LHContainer::cached_font_count()
{
    std::lock_guard<std::mutex> lock(gFontCacheMutex);
    return gFontCache.size();
}

size_t LHContainer::raster_cache_bytes()
{
    return SharedRasterCache().bytes();
}

void LHContainer::purge_caches()
{
    SharedRasterCache().clear();
    std::vector<std::shared_ptr<SharedFont>> fonts;
    {
        std::lock_guard<std::mutex> lock(gFontCacheMutex);
        for(auto it = gFontCache.begin(); it != gFontCache.end();)
        {
            if(it->second.use_count() == 1)
                it = gFontCache.erase(it);
            else
            {
                fonts.push_back(it->second);
                ++it;
            }
        }
    }
    for(auto& font : fonts)
    {
        {
            std::lock_guard<std::mutex> lock(font->linesMutex);
            for(auto& entry : font->lines)
                CFRelease(entry.second);
            for(auto& entry : font->oldLines)
                CFRelease(entry.second);
            font->lines.clear();
            font->oldLines.clear();
        }
        if(font->simple)
            font->simple->clear();
        std::lock_guard<std::mutex> lock(font->widthsMutex);
        font->widths.clear();
        font->oldWidths.clear();
    }
}

namespace {

std::atomic<long> gLiveContainers{0};

void WatchMemoryPressure()
{
    static dispatch_source_t source = [] {
        dispatch_source_t made = dispatch_source_create(DISPATCH_SOURCE_TYPE_MEMORYPRESSURE, 0,
                                                        DISPATCH_MEMORYPRESSURE_WARN | DISPATCH_MEMORYPRESSURE_CRITICAL,
                                                        dispatch_get_global_queue(QOS_CLASS_UTILITY, 0));
        dispatch_source_set_event_handler(made, ^{
            LHContainer::purge_caches();
        });
        dispatch_resume(made);
        return made;
    }();
    (void)source;
}

}

LHContainer::LHContainer()
{
    WatchMemoryPressure();
    gLiveContainers.fetch_add(1);
}

LHContainer::~LHContainer()
{
    if(gLiveContainers.fetch_sub(1) == 1)
    {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC), dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
            if(gLiveContainers.load() == 0)
                LHContainer::purge_caches();
        });
    }
}

static CTFontRef CreateFeatureFont(NSFont* base, const std::string& features)
{
    NSMutableArray* settings = [NSMutableArray array];
    for(size_t start = 0; start < features.size();)
    {
        size_t end = features.find(',', start);
        if(end == std::string::npos)
            end = features.size();
        const std::string entry = features.substr(start, end - start);
        const size_t      eq    = entry.find('=');
        if(eq == 4)
            [settings addObject:@{(__bridge NSString*)kCTFontOpenTypeFeatureTag: [[NSString alloc] initWithBytes:entry.data() length:4 encoding:NSASCIIStringEncoding],
                                  (__bridge NSString*)kCTFontOpenTypeFeatureValue: @(atoi(entry.c_str() + 5))}];
        start = end + 1;
    }
    CTFontDescriptorRef descriptor = CTFontDescriptorCreateWithAttributes(
        (__bridge CFDictionaryRef) @{(__bridge NSString*)kCTFontFeatureSettingsAttribute: settings});
    CTFontRef font = CTFontCreateCopyWithAttributes((__bridge CTFontRef)base, 0, nullptr, descriptor);
    CFRelease(descriptor);
    return font ? font : (CTFontRef)CFBridgingRetain(base);
}

static std::shared_ptr<SharedFont> AcquireSharedFont(const lh::font_description& descr, CGFloat size)
{
    bool        italic = descr.style == lh::font_style_italic;
    std::string key    = descr.family + "|" + std::to_string(size) + "|" + std::to_string(descr.weight) + "|" +
                      (italic ? "1" : "0");
    if(descr.letter_spacing != 0)
        key += "|" + std::to_string(descr.letter_spacing);
    if(descr.word_spacing != 0)
        key += "|w" + std::to_string(descr.word_spacing);
    if(!descr.features.empty())
        key += "|f" + descr.features;

    {
        std::lock_guard<std::mutex> lock(gFontCacheMutex);
        auto                        it = gFontCache.find(key);
        if(it != gFontCache.end())
            return it->second;
    }

    NSFont* nsfont = ResolveFont(descr.family, size, descr.weight, italic);

    auto sf            = std::make_shared<SharedFont>();
    sf->font           = descr.features.empty() ? (CTFontRef)CFBridgingRetain(nsfont) : CreateFeatureFont(nsfont, descr.features);
    sf->kern           = descr.letter_spacing;
    CFNumberRef kern   = CFNumberCreate(nullptr, kCFNumberCGFloatType, &sf->kern);
    const void* keys[] = {kCTFontAttributeName, kCTKernAttributeName};
    const void* vals[] = {sf->font, kern};
    sf->attrs = CFDictionaryCreate(nullptr, keys, vals, sf->kern != 0 ? 2 : 1, &kCFTypeDictionaryKeyCallBacks,
                                   &kCFTypeDictionaryValueCallBacks);
    const void* drawKeys[] = {kCTFontAttributeName, kCTForegroundColorFromContextAttributeName, kCTKernAttributeName};
    const void* drawVals[] = {sf->font, kCFBooleanTrue, kern};
    sf->drawAttrs = CFDictionaryCreate(nullptr, drawKeys, drawVals, sf->kern != 0 ? 3 : 2, &kCFTypeDictionaryKeyCallBacks,
                                       &kCFTypeDictionaryValueCallBacks);
    CFRelease(kern);
    if(descr.word_spacing != 0)
    {
        CGFloat spaceKern = sf->kern + descr.word_spacing;
        sf->spaceKern     = CFNumberCreate(nullptr, kCFNumberCGFloatType, &spaceKern);
    }
    sf->simple = std::make_unique<LHSimpleShaper>(sf->font, sf->drawAttrs, descr.word_spacing);
    sf->colorGlyphs = (CTFontGetSymbolicTraits(sf->font) & kCTFontTraitColorGlyphs) != 0;

    CGFloat ascent          = CTFontGetAscent(sf->font);
    CGFloat descent         = CTFontGetDescent(sf->font);
    CGFloat leading         = CTFontGetLeading(sf->font);
    {
        NSString* familyName = CFBridgingRelease(CTFontCopyFamilyName(sf->font));
        if([familyName caseInsensitiveCompare:@"Times"] == NSOrderedSame ||
           [familyName caseInsensitiveCompare:@"Helvetica"] == NSOrderedSame ||
           [familyName caseInsensitiveCompare:@"Courier"] == NSOrderedSame)
            ascent += std::floor((ascent + descent) * 0.15 + 0.5);
    }
    sf->ascent              = std::round(ascent);
    sf->descent             = std::round(descent) + std::round(leading);
    sf->x_height            = CTFontGetXHeight(sf->font);
    sf->underline_pos       = -CTFontGetUnderlinePosition(sf->font);
    sf->underline_thickness = std::max<CGFloat>(1.0, std::round(CTFontGetUnderlineThickness(sf->font)));

    std::lock_guard<std::mutex> lock(gFontCacheMutex);
    if(gFontCache.size() >= kFontCacheLimit)
    {
        for(auto it = gFontCache.begin(); it != gFontCache.end();)
            it = it->second.use_count() == 1 ? gFontCache.erase(it) : std::next(it);
    }
    return gFontCache.emplace(key, sf).first->second;
}

lh::uint_ptr LHContainer::create_font(const lh::font_description& descr, const lh::document* doc,
                                      lh::font_metrics* fm)
{
    CGFloat size = std::max<CGFloat>(F(descr.size), 1.0);

    auto* f   = new Font();
    f->shared = AcquireSharedFont(descr, size);

    f->underline  = (descr.decoration_line & lh::text_decoration_line_underline) != 0;
    f->overline   = (descr.decoration_line & lh::text_decoration_line_overline) != 0;
    f->strike     = (descr.decoration_line & lh::text_decoration_line_line_through) != 0;
    f->deco_color = descr.decoration_color;
    f->deco_style = descr.decoration_style;
    f->shadow_x     = std::clamp<CGFloat>(descr.shadow_x, -kMaxTextShadowOffset, kMaxTextShadowOffset);
    f->shadow_y     = std::clamp<CGFloat>(descr.shadow_y, -kMaxTextShadowOffset, kMaxTextShadowOffset);
    f->shadow_blur  = std::clamp<CGFloat>(descr.shadow_blur, 0, kMaxTextShadowBlur);
    f->shadow_color = descr.shadow_color;
    if(f->shadow_color.alpha != 0)
        f->shadows.push_back({f->shadow_x, f->shadow_y, f->shadow_blur, f->shadow_color});
    for(const auto& extra : descr.extra_shadows)
    {
        if(f->shadows.size() >= kMaxTextShadows)
            break;
        f->shadows.push_back({std::clamp<CGFloat>(extra.x, -kMaxTextShadowOffset, kMaxTextShadowOffset), std::clamp<CGFloat>(extra.y, -kMaxTextShadowOffset, kMaxTextShadowOffset),
                              std::clamp<CGFloat>(extra.blur, 0, kMaxTextShadowBlur), extra.color});
    }
    f->smoothing    = descr.smoothing;

    CGFloat height = f->shared->ascent + f->shared->descent;

    auto thickness = descr.decoration_thickness;
    if(!thickness.is_predefined() && doc)
    {
        lh::font_metrics tmp;
        tmp.font_size     = descr.size;
        tmp.height        = static_cast<float>(height);
        tmp.ascent        = static_cast<float>(f->shared->ascent);
        tmp.descent       = static_cast<float>(f->shared->descent);
        tmp.x_height      = static_cast<float>(f->shared->x_height);
        tmp.ch_width      = descr.size;
        f->deco_thickness = F(doc->to_pixels(thickness, tmp, descr.size));
    }

    if(fm)
    {
        fm->font_size   = descr.size;
        fm->height      = static_cast<float>(height);
        fm->ascent      = static_cast<float>(f->shared->ascent);
        fm->descent     = static_cast<float>(f->shared->descent);
        fm->x_height    = static_cast<float>(f->shared->x_height);
        fm->draw_spaces = descr.decoration_line != lh::text_decoration_line_none;
        fm->sub_shift   = static_cast<int>(descr.size.value()) / 5 + 1;
        fm->super_shift = static_cast<int>(descr.size.value()) / 3 + 1;
        fm->ch_width    = text_width("0", reinterpret_cast<lh::uint_ptr>(f));
    }
    return reinterpret_cast<lh::uint_ptr>(f);
}

void LHContainer::delete_font(lh::uint_ptr hFont)
{
    delete reinterpret_cast<Font*>(hFont);
}

CTFontRef LHContainer::native_font(lh::uint_ptr hFont)
{
    auto* font = reinterpret_cast<Font*>(hFont);
    return font ? font->shared->font : nullptr;
}

static CFAttributedStringRef CreateAttributed(const SharedFont& sf, CFStringRef str)
{
    if(!sf.spaceKern)
        return CFAttributedStringCreate(nullptr, str, sf.drawAttrs);
    const CFIndex                length = CFStringGetLength(str);
    CFMutableAttributedStringRef as     = CFAttributedStringCreateMutable(nullptr, 0);
    CFAttributedStringBeginEditing(as);
    CFAttributedStringReplaceString(as, CFRangeMake(0, 0), str);
    CFAttributedStringSetAttributes(as, CFRangeMake(0, length), sf.drawAttrs, false);
    CFStringInlineBuffer buffer;
    CFStringInitInlineBuffer(str, &buffer, CFRangeMake(0, length));
    for(CFIndex i = 0; i < length; i++)
    {
        const UniChar c = CFStringGetCharacterFromInlineBuffer(&buffer, i);
        if(c == ' ' || c == 0x00A0)
            CFAttributedStringSetAttribute(as, CFRangeMake(i, 1), kCTKernAttributeName, sf.spaceKern);
    }
    CFAttributedStringEndEditing(as);
    return as;
}

static CTLineRef CreateLine(const char* text, const SharedFont& sf)
{
    CFStringRef str = CFStringCreateWithCString(nullptr, text, kCFStringEncodingUTF8);
    if(!str)
        return nullptr;
    CFAttributedStringRef as   = CreateAttributed(sf, str);
    CTLineRef             line = CTLineCreateWithAttributedString(as);
    CFRelease(as);
    CFRelease(str);
    return line;
}

static CTLineRef CopyCachedLine(SharedFont& sf, const char* text)
{
    {
        std::lock_guard<std::mutex> lock(sf.linesMutex);
        auto                        it = sf.lines.find(text);
        if(it != sf.lines.end())
            return static_cast<CTLineRef>(CFRetain(it->second));
        auto old = sf.oldLines.find(text);
        if(old != sf.oldLines.end())
        {
            CTLineRef line = old->second;
            sf.lines.emplace(old->first, line);
            sf.oldLines.erase(old);
            return static_cast<CTLineRef>(CFRetain(line));
        }
    }
    return nullptr;
}

static void CacheLine(SharedFont& sf, const char* text, CTLineRef line)
{
    std::lock_guard<std::mutex> lock(sf.linesMutex);
    if(sf.lines.size() >= 2048)
    {
        for(auto& entry : sf.oldLines)
            CFRelease(entry.second);
        sf.oldLines.clear();
        sf.oldLines.swap(sf.lines);
    }
    if(sf.lines.emplace(text, line).second)
        CFRetain(line);
}

static CTLineRef CopyLine(SharedFont& sf, const char* text)
{
    if(CTLineRef line = CopyCachedLine(sf, text))
        return line;
    CTLineRef line = CreateLine(text, sf);
    if(line)
        CacheLine(sf, text, line);
    return line;
}

static std::atomic<size_t> gTextMeasures{0};

size_t LHContainer::text_measure_count()
{
    return gTextMeasures.load(std::memory_order_relaxed);
}

static bool CheckSimpleText()
{
    static const bool check = getenv("TW_SIMPLE_TEXT_CHECK") != nullptr;
    return check;
}

struct SimpleRun
{
    CGGlyph glyphs[LHSimpleShaper::kMaxLength];
    CGFloat xs[LHSimpleShaper::kMaxLength];
    CGFloat width = 0;
    size_t  count = 0;
};

static bool ShapeSimple(SharedFont& sf, const char* text, SimpleRun& run)
{
    if(!sf.simple)
        return false;
    run.count = strlen(text);
    return sf.simple->shape(text, run.count, run.glyphs, run.xs, &run.width);
}

namespace
{
    class LHTextBreaker final : public lh::text_breaker
    {
        CTTypesetterRef typesetter = nullptr;
        std::vector<std::pair<CFIndex, size_t>> boundaries;
        lh::pixel_t minimum = -1_px;
        LHContainer *container;
        lh::uint_ptr font;
        std::shared_ptr<SharedFont> sharedFont;
        std::string text;

        void prepare()
        {
            if(typesetter)
                return;
            CFStringRef string = CFStringCreateWithBytes(nullptr, reinterpret_cast<const UInt8*>(text.data()), text.size(), kCFStringEncodingUTF8, false);
            if(!string)
                return;
            CFAttributedStringRef attributed = CreateAttributed(*sharedFont, string);
            typesetter = CTTypesetterCreateWithAttributedString(attributed);
            CFRelease(attributed);
            CFRelease(string);
        }

      public:
        LHTextBreaker(LHContainer *container, const std::string& text, lh::uint_ptr font, std::shared_ptr<SharedFont> sharedFont) : container(container), font(font), sharedFont(std::move(sharedFont)), text(text)
        {
            if(std::all_of(text.begin(), text.end(), [](unsigned char c) { return c >= 0x20 && c < 0x7f; }))
            {
                boundaries.reserve(text.size() + 1);
                for(size_t index = 0; index <= text.size(); index++)
                    boundaries.emplace_back(index, index);
                return;
            }
            CFStringRef string = CFStringCreateWithBytes(nullptr, reinterpret_cast<const UInt8*>(text.data()), text.size(), kCFStringEncodingUTF8, false);
            if(!string)
                return;
            NSString *source = (__bridge NSString *)string;
            boundaries.emplace_back(0, 0);
            size_t bytes = 0;
            for(NSUInteger index = 0; index < source.length;)
            {
                NSRange range = [source rangeOfComposedCharacterSequenceAtIndex:index];
                CFIndex length = 0;
                CFStringGetBytes(string, CFRangeMake(range.location, range.length), kCFStringEncodingUTF8, 0, false, nullptr, 0, &length);
                bytes += length;
                index = NSMaxRange(range);
                boundaries.emplace_back(index, bytes);
            }
            CFRelease(string);
        }

        ~LHTextBreaker() override
        {
            if(typesetter)
                CFRelease(typesetter);
        }

        size_t fit(size_t start, lh::pixel_t width) override
        {
            if(boundaries.size() < 2)
                return start;
            auto first = std::lower_bound(boundaries.begin(), boundaries.end(), start, [](const auto& point, size_t byte) { return point.second < byte; });
            if(first == boundaries.end() || first + 1 == boundaries.end())
                return start;
            if(width <= 0_px)
                return (first + 1)->second;
            prepare();
            if(!typesetter)
                return start;
            CFIndex end = first->first + CTTypesetterSuggestClusterBreak(typesetter, first->first, std::max(0.f, width.value()));
            auto last = std::upper_bound(first, boundaries.end(), end, [](CFIndex index, const auto& point) { return index < point.first; });
            if(last != first)
                --last;
            if(last == first)
                ++last;
            return last->second;
        }

        lh::pixel_t minimum_width() override
        {
            if(minimum >= 0_px)
                return minimum;
            minimum = 0_px;
            for(size_t index = 1; index < boundaries.size(); index++)
            {
                const auto cluster = text.substr(boundaries[index - 1].second, boundaries[index].second - boundaries[index - 1].second);
                minimum = std::max(minimum, container->text_width(cluster.c_str(), font));
            }
            return minimum;
        }
    };
}

std::shared_ptr<lh::text_breaker> LHContainer::create_text_breaker(const std::string& text, lh::uint_ptr font)
{
    auto* f = reinterpret_cast<Font*>(font);
    return f && f->shared ? std::make_shared<LHTextBreaker>(this, text, font, f->shared) : nullptr;
}

lh::pixel_t LHContainer::text_width(const char* text, lh::uint_ptr hFont)
{
    auto* f = reinterpret_cast<Font*>(hFont);
    if(!f || !f->shared || !text || !*text)
        return 0_px;
    gTextMeasures.fetch_add(1, std::memory_order_relaxed);
    auto& sf = *f->shared;
    {
        std::lock_guard<std::mutex> lock(sf.widthsMutex);
        auto                        it = sf.widths.find(text);
        if(it != sf.widths.end())
            return static_cast<float>(it->second);
        auto old = sf.oldWidths.find(text);
        if(old != sf.oldWidths.end())
        {
            const CGFloat w = old->second;
            sf.widths.emplace(old->first, w);
            sf.oldWidths.erase(old);
            return static_cast<float>(w);
        }
    }
    SimpleRun simple;
    if(ShapeSimple(sf, text, simple))
    {
        if(CheckSimpleText())
        {
            CTLineRef line = CreateLine(text, sf);
            CGFloat   w    = line ? CTLineGetTypographicBounds(line, nullptr, nullptr, nullptr) : 0;
            if(line)
                CFRelease(line);
            if(std::fabs(w - simple.width) > 1e-4)
                fprintf(stderr, "SIMPLETEXT width mismatch \"%s\" %.6f vs %.6f\n", text, simple.width, w);
        }
        return static_cast<float>(simple.width);
    }

    CGFloat   w    = 0;
    CTLineRef line = CreateLine(text, sf);
    if(line)
    {
        w = CTLineGetTypographicBounds(line, nullptr, nullptr, nullptr);
        if(sf.simple)
            sf.simple->learn(text, strlen(text), line);
        CacheLine(sf, text, line);
        CFRelease(line);
    }
    std::lock_guard<std::mutex> lock(sf.widthsMutex);
    if(sf.widths.size() >= 4096)
    {
        sf.oldWidths.clear();
        sf.oldWidths.swap(sf.widths);
    }
    sf.widths.emplace(text, w);
    return static_cast<float>(w);
}

static void DrawDecoration(CGContextRef ctx, CGFloat x, CGFloat y, CGFloat w, CGFloat t, int style)
{
    switch(style)
    {
    case lh::text_decoration_style_double:
        CGContextFillRect(ctx, CGRectMake(x, y - t / 2, w, t));
        CGContextFillRect(ctx, CGRectMake(x, y + t * 1.5, w, t));
        break;
    case lh::text_decoration_style_dotted:
    case lh::text_decoration_style_dashed:
        {
            CGFloat dots[]   = {t, t * 1.5};
            CGFloat dashes[] = {t * 3, t * 2};
            CGContextSetLineWidth(ctx, t);
            CGContextSetLineDash(ctx, 0, style == lh::text_decoration_style_dotted ? dots : dashes, 2);
            CGContextMoveToPoint(ctx, x, y);
            CGContextAddLineToPoint(ctx, x + w, y);
            CGContextStrokePath(ctx);
            CGContextSetLineDash(ctx, 0, nullptr, 0);
        }
        break;
    case lh::text_decoration_style_wavy:
        {
            CGFloat amp = std::max<CGFloat>(t, 1.0) * 1.2, wl = amp * 4;
            CGContextSetLineWidth(ctx, t);
            CGContextMoveToPoint(ctx, x, y);
            for(CGFloat cx = x; cx < x + w; cx += wl)
            {
                CGContextAddCurveToPoint(ctx, cx + wl / 4, y - amp, cx + wl / 4, y - amp, cx + wl / 2, y);
                CGContextAddCurveToPoint(ctx, cx + wl * 3 / 4, y + amp, cx + wl * 3 / 4, y + amp, cx + wl, y);
            }
            CGContextStrokePath(ctx);
        }
        break;
    default:
        CGContextFillRect(ctx, CGRectMake(x, y - t / 2, w, t));
        break;
    }
}

struct LHContainer::OpHash
{
    uint64_t value  = 0xcbf29ce484222325ULL;
    uint64_t shape  = 0xcbf29ce484222325ULL;
    bool     placed = false;
    double   anchor = 0;
    uint64_t stretch = 0;
    double   edge    = 0;

    void stretch_number(double v) { mix(stretch, &v, sizeof(v)); }

    static void mix(uint64_t& hash, const void* data, size_t length)
    {
        auto   p = static_cast<const unsigned char*>(data);
        size_t i = 0;
        for(; i + 8 <= length; i += 8)
        {
            uint64_t word;
            memcpy(&word, p + i, sizeof(word));
            hash  = (hash ^ word) * 0x100000001b3ULL;
            hash ^= hash >> 32;
        }
        for(; i < length; i++)
            hash = (hash ^ p[i]) * 0x100000001b3ULL;
    }
    void bytes(const void* data, size_t length)
    {
        mix(value, data, length);
        mix(shape, data, length);
    }
    void number(double v) { bytes(&v, sizeof(v)); }
    void integer(int64_t v) { bytes(&v, sizeof(v)); }
    void text(const char* s, size_t length) { integer(static_cast<int64_t>(length)); bytes(s, length); }
    void text(const std::string& s) { text(s.data(), s.size()); }
    void vertical(double y)
    {
        mix(value, &y, sizeof(y));
        if(!placed)
        {
            placed = true;
            anchor = y;
        }
        const double offset = std::round((y - anchor) * 64) / 64;
        mix(shape, &offset, sizeof(offset));
    }
    void box(const lh::position& p)
    {
        number(F(p.x));
        vertical(F(p.y));
        number(F(p.width));
        number(F(p.height));
    }
    void clip(const lh::position& p, const lh::border_radiuses& r, CGRect drawn)
    {
        const double radii[8] = {F(r.top_left_x), F(r.top_left_y), F(r.top_right_x), F(r.top_right_y),
                                 F(r.bottom_right_x), F(r.bottom_right_y), F(r.bottom_left_x), F(r.bottom_left_y)};
        for(double v : {F(p.x), F(p.y), F(p.width), F(p.height)})
            mix(value, &v, sizeof(v));
        mix(value, radii, sizeof(radii));
        if(InsideRoundedRect(R(p), r, drawn))
            return;
        const CGRect part = CGRectIntersection(R(p), drawn);
        const double cut[4] = {std::round(part.origin.x * 64) / 64, std::round((part.origin.y - anchor) * 64) / 64,
                               std::round(part.size.width * 64) / 64, std::round(part.size.height * 64) / 64};
        mix(shape, cut, sizeof(cut));
        mix(shape, radii, sizeof(radii));
    }
    void color(const lh::web_color& c)
    {
        const unsigned char v[5] = {c.red, c.green, c.blue, c.alpha, c.is_current_color};
        bytes(v, sizeof(v));
    }
    void radius(const lh::border_radiuses& r)
    {
        for(lh::pixel_t v : {r.top_left_x, r.top_left_y, r.top_right_x, r.top_right_y, r.bottom_right_x,
                             r.bottom_right_y, r.bottom_left_x, r.bottom_left_y})
            number(F(v));
    }
    void layer(const lh::background_layer& l)
    {
        box(l.border_box);
        radius(l.border_radius);
        box(l.clip_box);
        box(l.origin_box);
        integer(l.attachment);
        integer(l.repeat);
        integer(l.is_root);
    }
    void gradient(const lh::background_layer::gradient_base& g)
    {
        integer(static_cast<int64_t>(g.color_points.size()));
        for(const auto& point : g.color_points)
        {
            number(point.offset);
            color(point.color);
        }
        integer(g.color_space);
        integer(g.hue_interpolation);
    }
    void border(const lh::border& b)
    {
        number(F(b.width));
        integer(b.style);
        color(b.color);
    }
};

bool LHContainer::clips_needed(CGRect bounds) const
{
    CGRect            ink    = CGRectInset(bounds, -1, -1);
    CGAffineTransform matrix = group_matrix();
    for(const auto& c : m_clips)
    {
        if(c.path || !CGAffineTransformEqualToTransform(c.matrix, matrix) || !m_clip_culling || !InsideRoundedRect(R(c.box), c.radius, ink))
            return true;
    }
    return false;
}

std::atomic<long> LHContainer::s_shadow_line_batches{0};

std::atomic<long> LHContainer::s_source_filter_hits{0};
std::atomic<bool> LHContainer::s_source_filter_cache{getenv("LH_NO_SOURCE_FILTER_CACHE") == nullptr};

bool LHContainer::begin_source_filters()
{
    if(m_source_filter_scope)
        return false;
    m_source_filter_scope = true;
    return true;
}

void LHContainer::end_source_filters()
{
    for(auto& entry : m_source_filters)
        CGImageRelease(entry.second.image);
    m_source_filters.clear();
    m_source_filter_scope = false;
}

std::atomic<bool> LHContainer::s_exact_group_bounds{getenv("LH_NO_EXACT_GROUP_BOUNDS") == nullptr};

std::atomic<bool> LHContainer::s_text_batching{getenv("LH_NO_TEXT_BATCH") == nullptr};

CGAffineTransform LHContainer::shadow_transform(CGContextRef ctx) const
{
    CGAffineTransform transform = CGContextGetCTM(ctx);
    if(ctx == m_shadow_base_context)
        transform = CGAffineTransformConcat(transform, m_shadow_base_inverse);
    return transform;
}

void LHContainer::begin_text_batching(CGContextRef ctx)
{
    flush_text();
    m_batch_ctx = s_text_batching.load(std::memory_order_relaxed) ? ctx : nullptr;
}

void LHContainer::end_text_batching()
{
    flush_text();
    m_batch_ctx = nullptr;
}

static bool SameColor(const lh::web_color& a, const lh::web_color& b)
{
    return a.red == b.red && a.green == b.green && a.blue == b.blue && a.alpha == b.alpha && a.is_current_color == b.is_current_color;
}

void LHContainer::flush_shadow_batch()
{
    auto& batch = m_shadow_batch;
    if(batch.runs.empty())
        return;
    s_shadow_line_batches.fetch_add(1, std::memory_order_relaxed);
    auto*        f   = reinterpret_cast<Font*>(const_cast<void*>(batch.font));
    CGContextRef ctx = batch.ctx;
    CGFloat      reach = 0;
    for(const auto& shadow : f->shadows)
        reach = std::max<CGFloat>(reach, shadow.blur * 1.5 + std::max(std::fabs(shadow.x), std::fabs(shadow.y)));
    const CGRect ink = CGRectInset(batch.box, -(reach + 4), -(reach + 4));
    auto glyphsAt = [&](CGContextRef target, const ShadowRun& run, CGFloat dx) {
        SimpleRun simple;
        if(ShapeSimple(*f->shared, run.text.c_str(), simple))
        {
            CGPoint points[LHSimpleShaper::kMaxLength];
            for(size_t i = 0; i < simple.count; i++)
                points[i] = CGPointMake(simple.xs[i], 0);
            CGContextSetTextPosition(target, run.x + dx, run.baseline);
            CTFontDrawGlyphs(f->shared->font, simple.glyphs, points, simple.count, target);
            return;
        }
        CTLineRef line = CopyLine(*f->shared, run.text.c_str());
        if(!line)
            return;
        CGContextSetTextPosition(target, run.x + dx, run.baseline);
        CTLineDraw(line, target);
        CFRelease(line);
    };
    CGContextSaveGState(ctx);
    CGContextSetAllowsFontSmoothing(ctx, true);
    CGContextSetShouldSmoothFonts(ctx, f->smoothing == lh::font_smoothing_auto || f->smoothing == lh::font_smoothing_subpixel_antialiased);
    if(f->smoothing == lh::font_smoothing_none)
        CGContextSetShouldAntialias(ctx, false);
    CGAffineTransform ctm   = CGContextGetCTM(ctx);
    CGFloat           scale = std::sqrt(std::fabs(ctm.a * ctm.d - ctm.b * ctm.c));
    for(auto it = f->shadows.rbegin(); it != f->shadows.rend(); ++it)
    {
        const LHTextShadow& shadow = *it;
        if(shadow.color.alpha == 0)
            continue;
        auto paint = [&](CGContextRef target) {
            CGFloat           far = ink.size.width + reach * 4 + 1000;
            CGAffineTransform m   = shadow_transform(target);
            CGFloat           ms  = std::sqrt(std::fabs(m.a * m.d - m.b * m.c));
            CGColorRef        sc  = CreateColor(shadow.color);
            CGContextSaveGState(target);
            CGContextSetShadowWithColor(target, CGSizeApplyAffineTransform(CGSizeMake(shadow.x - far, shadow.y), m), shadow.blur * ms, sc);
            CGColorRelease(sc);
            CGContextSetRGBFillColor(target, 0, 0, 0, 1);
            CGContextSetTextMatrix(target, CGAffineTransformMakeScale(1, -1));
            for(const auto& run : batch.runs)
                glyphsAt(target, run, far);
            CGContextRestoreGState(target);
        };
        if(shadow.blur * scale > 24)
        {
            std::string key = "tshadowline:";
            for(const auto& run : batch.runs)
            {
                key.append(run.text);
                key.push_back('\x1f');
                AppendNumber(key, run.x - ink.origin.x);
                AppendNumber(key, run.baseline - ink.origin.y);
            }
            AppendInteger(key, reinterpret_cast<intptr_t>(f->shared.get()));
            for(CGFloat v : {shadow.x, shadow.y, shadow.blur, ink.size.width, ink.size.height})
                AppendNumber(key, v);
            AppendInteger(key, (shadow.color.red << 24) | (shadow.color.green << 16) | (shadow.color.blue << 8) | shadow.color.alpha);
            if(DrawDownsampled(ctx, ink, key, shadow.blur, paint))
                continue;
        }
        paint(ctx);
    }
    SetFill(ctx, batch.color);
    CGContextSetTextMatrix(ctx, CGAffineTransformMakeScale(1, -1));
    for(const auto& run : batch.runs)
        glyphsAt(ctx, run, 0);
    CGContextRestoreGState(ctx);
    batch.runs.clear();
    batch.box = CGRectNull;
}

void LHContainer::flush_text()
{
    flush_shadow_batch();
    for(auto& batch : m_text_batches)
    {
        if(batch.glyphs.empty())
            continue;
        CGContextRef ctx = batch.ctx;
        CGContextSaveGState(ctx);
        SetFill(ctx, batch.color);
        CGContextSetAllowsFontSmoothing(ctx, true);
        CGContextSetShouldSmoothFonts(ctx, batch.smoothing == lh::font_smoothing_auto || batch.smoothing == lh::font_smoothing_subpixel_antialiased);
        if(batch.smoothing == lh::font_smoothing_none)
            CGContextSetShouldAntialias(ctx, false);
        CGContextSetTextMatrix(ctx, CGAffineTransformMakeScale(1, -1));
        CGContextSetTextPosition(ctx, 0, 0);
        CTFontDrawGlyphs(batch.font, batch.glyphs.data(), batch.points.data(), batch.glyphs.size(), ctx);
        CGContextRestoreGState(ctx);
    }
    m_text_batches.clear();
}

void LHContainer::draw_text(lh::uint_ptr hdc, const char* text, lh::uint_ptr hFont, lh::web_color color,
                            const lh::position& pos)
{
    if(lh::g_paint_trace)
        return;
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        auto* font = reinterpret_cast<Font*>(hFont);
        if(!font || !text || !*text)
            return;
        lhx::TextStyle style;
        style.nativeFont          = font->shared->font;
        style.paintIdentity       = font->paintIdentity;
        style.ascent              = float(font->shared->ascent);
        style.descent             = float(font->shared->descent);
        style.underline           = font->underline;
        style.overline            = font->overline;
        style.strike              = font->strike;
        style.decorationColor     = font->deco_color;
        style.decorationStyle     = font->deco_style;
        style.decorationThickness = float(font->deco_thickness);
        style.shadowColor         = font->shadow_color;
        style.shadowX             = float(font->shadow_x);
        style.shadowY             = float(font->shadow_y);
        style.shadowBlur          = float(font->shadow_blur);
        style.hasShadows          = !font->shadows.empty();
        style.smoothing           = font->smoothing;
        m_painter->text(text, style, color, pos);
        return;
    }
    auto* f   = reinterpret_cast<Font*>(hFont);
    auto  ctx = reinterpret_cast<CGContextRef>(hdc);
    if(!f || !ctx || !text || !*text)
        return;
    if(m_runs)
    {
        if(m_hidden_depth == 0)
            m_runs->push_back({text, f->shared->font, CGRectApplyAffineTransform(R(pos), group_matrix())});
        return;
    }
    CGRect  box   = R(pos);
    CGFloat slack = std::max<CGFloat>(4, box.size.height / 2);
    CGFloat shadowReach = 0;
    for(const auto& shadow : f->shadows)
        shadowReach = std::max(shadowReach, shadow.blur * 2 + std::max(std::fabs(shadow.x), std::fabs(shadow.y)));
    slack += shadowReach;
    CGRect  ink   = CGRectInset(box, -slack, -slack);
    if(m_ops)
    {
        OpHash hash;
        hash.integer(1);
        hash.text(text, strlen(text));
        hash.integer(reinterpret_cast<intptr_t>(f->shared.get()));
        hash.integer(f->underline | f->overline << 1 | f->strike << 2);
        hash.color(f->deco_color);
        hash.integer(f->deco_style);
        hash.number(f->deco_thickness);
        hash.color(f->shadow_color);
        hash.number(f->shadow_x);
        hash.number(f->shadow_y);
        hash.number(f->shadow_blur);
        for(size_t i = f->shadow_color.alpha != 0 ? 1 : 0; i < f->shadows.size(); i++)
        {
            hash.color(f->shadows[i].color);
            for(CGFloat v : {f->shadows[i].x, f->shadows[i].y, f->shadows[i].blur})
                hash.number(v);
        }
        hash.integer(f->smoothing);
        hash.color(color);
        hash.box(pos);
        record_op(hash, ink, !record_glyphs(text, f, color, pos, ink));
        if(!m_capture)
            return;
        ctx = m_capture;
    }

    if(ctx == m_batch_ctx && !m_capture && !f->shadows.empty() && !f->underline && !f->overline && !f->strike && !clips_needed(ink))
    {
        if(!m_text_batches.empty())
            flush_text();
        const CGFloat baseline = F(pos.y) + f->shared->ascent;
        auto&         batch    = m_shadow_batch;
        if(!batch.runs.empty() && (batch.ctx != ctx || batch.font != f || !SameColor(batch.color, color) || std::fabs(batch.baseline - baseline) > 0.5 || batch.runs.size() >= 256))
            flush_shadow_batch();
        if(batch.runs.empty())
        {
            batch.ctx      = ctx;
            batch.font     = f;
            batch.color    = color;
            batch.baseline = baseline;
            batch.box      = CGRectNull;
        }
        batch.runs.push_back({text, F(pos.x), baseline});
        batch.box = CGRectUnion(batch.box, box);
        return;
    }
    SimpleRun simple;
    const bool simpleRun = ShapeSimple(*f->shared, text, simple);
    if(simpleRun && ctx == m_batch_ctx && !m_capture && f->shadows.empty() && !f->underline && !f->overline && !f->strike && !clips_needed(ink))
    {
        if(!m_shadow_batch.runs.empty())
            flush_shadow_batch();
        const CGRect glyphBox = CGRectInset(CGRectMake(F(pos.x), F(pos.y), std::max<CGFloat>(simple.width, 0), box.size.height), -2, -2);
        TextBatch* target = nullptr;
        bool       overlap = false;
        for(auto& batch : m_text_batches)
        {
            if(batch.ctx == ctx && batch.font == f->shared->font && batch.smoothing == f->smoothing && batch.color.red == color.red &&
               batch.color.green == color.green && batch.color.blue == color.blue && batch.color.alpha == color.alpha &&
               batch.color.is_current_color == color.is_current_color)
            {
                target = &batch;
                continue;
            }
            for(const auto& other : batch.inks)
            {
                if(CGRectIntersectsRect(other, glyphBox))
                {
                    overlap = true;
                    break;
                }
            }
            if(overlap)
                break;
        }
        if(overlap || (!target && m_text_batches.size() >= 8))
        {
            flush_text();
            target = nullptr;
        }
        if(!target)
        {
            m_text_batches.emplace_back();
            target            = &m_text_batches.back();
            target->ctx       = ctx;
            target->font      = f->shared->font;
            target->smoothing = f->smoothing;
            target->color     = color;
        }
        auto& batch = *target;
        batch.inks.push_back(glyphBox);
        CGFloat x        = F(pos.x);
        CGFloat baseline = F(pos.y) + f->shared->ascent;
        for(size_t i = 0; i < simple.count; i++)
        {
            batch.glyphs.push_back(simple.glyphs[i]);
            batch.points.push_back(CGPointMake(x + simple.xs[i], -baseline));
        }
        return;
    }
    flush_text();
    CGContextSaveGState(ctx);
    apply_clips(ctx, ink);

    CTLineRef  line      = simpleRun ? nullptr : CopyLine(*f->shared, text);

    if(simpleRun || line)
    {
        SetFill(ctx, color);
        CGContextSetAllowsFontSmoothing(ctx, true);
        CGContextSetShouldSmoothFonts(ctx, f->smoothing == lh::font_smoothing_auto ||
                                               f->smoothing == lh::font_smoothing_subpixel_antialiased);
        if(f->smoothing == lh::font_smoothing_none)
            CGContextSetShouldAntialias(ctx, false);
        CGFloat x        = F(pos.x);
        CGFloat baseline = F(pos.y) + f->shared->ascent;
        auto drawGlyphs = [&](CGContextRef target) {
            if(simpleRun)
            {
                CGPoint points[LHSimpleShaper::kMaxLength];
                for(size_t i = 0; i < simple.count; i++)
                    points[i] = CGPointMake(simple.xs[i], 0);
                CTFontDrawGlyphs(f->shared->font, simple.glyphs, points, simple.count, target);
            }
            else
                CTLineDraw(line, target);
        };
        auto paintShadow = [&](const LHTextShadow& shadow) {
            if(shadow.color.alpha == 0)
                return;
            auto paint = [&](CGContextRef target) {
                CGFloat           far = ink.size.width + slack * 4 + 1000;
                CGAffineTransform m   = shadow_transform(target);
                CGFloat           ms  = std::sqrt(std::fabs(m.a * m.d - m.b * m.c));
                CGColorRef        sc  = CreateColor(shadow.color);
                CGContextSaveGState(target);
                CGContextSetShadowWithColor(target, CGSizeApplyAffineTransform(CGSizeMake(shadow.x - far, shadow.y), m), shadow.blur * ms, sc);
                CGColorRelease(sc);
                CGContextSetRGBFillColor(target, 0, 0, 0, 1);
                CGContextSetTextMatrix(target, CGAffineTransformMakeScale(1, -1));
                CGContextSetTextPosition(target, x + far, baseline);
                drawGlyphs(target);
                CGContextRestoreGState(target);
            };
            CGAffineTransform ctm   = CGContextGetCTM(ctx);
            CGFloat           scale = std::sqrt(std::fabs(ctm.a * ctm.d - ctm.b * ctm.c));
            if(!m_capture && shadow.blur * scale > 24)
            {
                std::string key = "tshadow:";
                key.append(text);
                AppendInteger(key, reinterpret_cast<intptr_t>(f->shared.get()));
                for(CGFloat v : {shadow.x, shadow.y, shadow.blur, slack, box.size.width, box.size.height, x - ink.origin.x, baseline - ink.origin.y})
                    AppendNumber(key, v);
                AppendInteger(key, (shadow.color.red << 24) | (shadow.color.green << 16) | (shadow.color.blue << 8) | shadow.color.alpha);
                AppendInteger(key, f->smoothing);
                if(DrawDownsampled(ctx, ink, key, shadow.blur, paint))
                    return;
            }
            paint(ctx);
        };
        for(auto it = f->shadows.rbegin(); it != f->shadows.rend(); ++it)
            paintShadow(*it);
        SetFill(ctx, color);
        CGContextSetTextMatrix(ctx, CGAffineTransformMakeScale(1, -1));
        CGContextSetTextPosition(ctx, x, baseline);
        drawGlyphs(ctx);
        CGContextSetShadowWithColor(ctx, CGSizeZero, 0, nullptr);

        if(f->underline || f->overline || f->strike)
        {
            CGFloat       w  = simpleRun ? simple.width : CTLineGetTypographicBounds(line, nullptr, nullptr, nullptr);
            CGFloat       t  = f->deco_thickness > 0 ? f->deco_thickness : f->shared->underline_thickness;
            lh::web_color dc = f->deco_color.is_current_color ? color : f->deco_color;
            SetFill(ctx, dc);
            SetStroke(ctx, dc);
            if(f->underline)
                DrawDecoration(ctx, x, std::round(baseline + std::max<CGFloat>(f->shared->underline_pos, 1.0) + t / 2),
                               w, t, f->deco_style);
            if(f->strike)
                DrawDecoration(ctx, x, std::round(baseline - f->shared->x_height / 2), w, t, f->deco_style);
            if(f->overline)
                DrawDecoration(ctx, x, std::round(baseline - f->shared->ascent + t / 2), w, t, f->deco_style);
        }
        if(line)
            CFRelease(line);
    }
    CGContextRestoreGState(ctx);
}

lh::pixel_t LHContainer::pt_to_px(float pt) const
{
    return pt * 96.0f / 72.0f;
}

lh::pixel_t LHContainer::get_default_font_size() const
{
    return m_default_font_size;
}

const char* LHContainer::get_default_font_name() const
{
    return "-apple-system";
}

void LHContainer::transform_text(std::string& text, lh::text_transform tt)
{
    NSString* s = @(text.c_str());
    if(!s)
        return;
    switch(tt)
    {
    case lh::text_transform_uppercase:
        s = s.uppercaseString;
        break;
    case lh::text_transform_lowercase:
        s = s.lowercaseString;
        break;
    case lh::text_transform_capitalize:
        if(s.length)
        {
            NSRange first = [s rangeOfComposedCharacterSequenceAtIndex:0];
            s = [[s substringWithRange:first].uppercaseString stringByAppendingString:[s substringFromIndex:NSMaxRange(first)]];
        }
        break;
    default:
        return;
    }
    text = s.UTF8String;
}

static CGPathRef CreateVectorPath(const lh::vector_path& path);

void LHContainer::set_clip_path(const lh::vector_path& path)
{
    flush_text();
    if(m_painter) m_painter->pushClipPath(path);
    CGPathRef cgpath = CreateVectorPath(path);
    CGRect box = CGPathGetPathBoundingBox(cgpath);
    CGPathRelease(cgpath);
    if(CGRectIsNull(box)) box = CGRectZero;
    lh::position bounds(lh::pixel_t(static_cast<float>(box.origin.x)), lh::pixel_t(static_cast<float>(box.origin.y)),
                        lh::pixel_t(static_cast<float>(box.size.width)), lh::pixel_t(static_cast<float>(box.size.height)));
    m_clips.push_back({bounds, {}, group_matrix(), std::make_shared<lh::vector_path>(path)});
}

bool LHContainer::point_in_clip_path(const lh::vector_path& path, lh::pixel_t x, lh::pixel_t y)
{
    CGPathRef cgpath = CreateVectorPath(path);
    bool inside = CGPathContainsPoint(cgpath, nullptr, CGPointMake(x.value(), y.value()), path.evenodd);
    CGPathRelease(cgpath);
    return inside;
}

void LHContainer::set_clip(const lh::position& pos, const lh::border_radiuses& bdr_radius)
{
    flush_text();
    if(m_painter)
        m_painter->pushClip(pos, bdr_radius);
    m_clips.push_back({pos, bdr_radius, group_matrix()});
}

void LHContainer::suspend_clips()
{
    flush_text();
    if(m_painter)
    {
        for(size_t i = 0; i < m_clips.size(); i++)
            m_painter->popClip();
    }
    m_suspended_clips.push_back(std::move(m_clips));
    m_clips.clear();
}

void LHContainer::resume_clips()
{
    flush_text();
    if(m_suspended_clips.empty())
        return;
    if(m_painter)
    {
        for(size_t i = 0; i < m_clips.size(); i++)
            m_painter->popClip();
    }
    m_clips = std::move(m_suspended_clips.back());
    m_suspended_clips.pop_back();
    if(m_painter)
    {
        for(const auto& c : m_clips)
        {
            if(c.path) m_painter->pushClipPath(*c.path);
            else m_painter->pushClip(c.box, c.radius);
        }
    }
}

void LHContainer::del_clip()
{
    flush_text();
    if(m_painter)
        m_painter->popClip();
    if(!m_clips.empty())
        m_clips.pop_back();
}

void LHContainer::flush_capture()
{
    if(!m_capture)
        return;
    if(m_gpu && m_gpu->store)
        m_gpu->store(m_capture_key, m_capture, m_capture_device);
    CGContextRelease(m_capture);
    m_capture = nullptr;
}

const uint8_t* LHContainer::device_color(const lh::web_color& color)
{
    if(m_device_colors_space != m_gpu->space)
    {
        m_device_colors.clear();
        m_device_colors_space = m_gpu->space;
    }
    const uint32_t key = color.red | color.green << 8 | color.blue << 16 | static_cast<uint32_t>(color.alpha) << 24;
    auto           it  = m_device_colors.find(key);
    if(it != m_device_colors.end())
        return it->second.data();
    std::array<uint8_t, 4> pixel = {0, 0, 0, 0};
    CGContextRef ctx = CGBitmapContextCreate(pixel.data(), 1, 1, 8, 4, m_gpu->space,
                                             kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    if(ctx)
    {
        SetFill(ctx, color);
        CGContextFillRect(ctx, CGRectMake(0, 0, 1, 1));
        CGContextRelease(ctx);
    }
    return m_device_colors.emplace(key, pixel).first->second.data();
}

void LHContainer::push_mask(const LHGlyphSlot& slot, CGRect device, const uint8_t* rgba)
{
    if(slot.empty)
        return;
    LHGPUOp op;
    op.kind   = LHGPUOp::Mask;
    op.device = device;
    op.key    = static_cast<uint64_t>(slot.u) | static_cast<uint64_t>(slot.v) << 16 |
             static_cast<uint64_t>(slot.device.size.width) << 32 | static_cast<uint64_t>(slot.device.size.height) << 48;
    memcpy(op.color, rgba, 4);
    m_gpu->ops.push_back(op);
}

bool LHContainer::record_rounded(CGRect rect, const lh::border_radiuses& radius, const lh::web_color& color)
{
    if(!m_gpu->mask)
        return false;
    const CGFloat s  = m_gpu->scale;
    const CGFloat x0 = rect.origin.x * s, y0 = rect.origin.y * s, x1 = CGRectGetMaxX(rect) * s, y1 = CGRectGetMaxY(rect) * s;
    const CGFloat r[8] = {F(radius.top_left_x) * s, F(radius.top_left_y) * s, F(radius.top_right_x) * s, F(radius.top_right_y) * s,
                          F(radius.bottom_right_x) * s, F(radius.bottom_right_y) * s, F(radius.bottom_left_x) * s, F(radius.bottom_left_y) * s};
    for(CGFloat v : r)
    {
        if(v <= 0)
            return false;
    }
    if(r[0] + r[2] > x1 - x0 || r[6] + r[4] > x1 - x0 || r[1] + r[7] > y1 - y0 || r[3] + r[5] > y1 - y0)
        return false;
    const CGRect tl = CGRectMake(std::floor(x0), std::floor(y0), std::ceil(x0 + r[0]) - std::floor(x0), std::ceil(y0 + r[1]) - std::floor(y0));
    const CGRect tr = CGRectMake(std::floor(x1 - r[2]), std::floor(y0), std::ceil(x1) - std::floor(x1 - r[2]), std::ceil(y0 + r[3]) - std::floor(y0));
    const CGRect br = CGRectMake(std::floor(x1 - r[4]), std::floor(y1 - r[5]), std::ceil(x1) - std::floor(x1 - r[4]), std::ceil(y1) - std::floor(y1 - r[5]));
    const CGRect bl = CGRectMake(std::floor(x0), std::floor(y1 - r[7]), std::ceil(x0 + r[6]) - std::floor(x0), std::ceil(y1) - std::floor(y1 - r[7]));
    if(CGRectGetMaxY(tl) != CGRectGetMaxY(tr) || CGRectGetMinY(bl) != CGRectGetMinY(br) || CGRectGetMaxX(tl) > CGRectGetMinX(tr) ||
       CGRectGetMaxX(bl) > CGRectGetMinX(br) || CGRectGetMaxY(tl) > CGRectGetMinY(bl))
        return false;
    const uint8_t* rgba = device_color(color);
    auto           mask = [&](int part, CGRect box, CGRect canonical, const CGFloat* radii) -> bool {
        uint64_t key = 0xcbf29ce484222325ULL;
        double   v[12] = {double(part), canonical.origin.x - box.origin.x, canonical.origin.y - box.origin.y,
                          CGRectGetMaxX(canonical) - box.origin.x, CGRectGetMaxY(canonical) - box.origin.y, box.size.width, box.size.height,
                          radii[0], radii[1], radii[2], radii[3], s};
        OpHash::mix(key, v, sizeof(v));
        lh::border_radiuses rad;
        rad.top_left_x     = float(radii[0] / s);
        rad.top_left_y     = float(radii[1] / s);
        rad.top_right_x    = float(radii[2] / s);
        rad.top_right_y    = float(radii[3] / s);
        rad.bottom_right_x = float(radii[4] / s);
        rad.bottom_right_y = float(radii[5] / s);
        rad.bottom_left_x  = float(radii[6] / s);
        rad.bottom_left_y  = float(radii[7] / s);
        const CGRect doc   = CGRectMake(canonical.origin.x / s, canonical.origin.y / s, canonical.size.width / s, canonical.size.height / s);
        LHGlyphSlot  slot;
        bool ok = m_gpu->mask(key, box, [&](CGContextRef ctx) {
            ClipToRoundedRect(ctx, doc, rad);
            CGContextClipToRect(ctx, doc);
            CGContextFillRect(ctx, doc);
        }, slot);
        if(!ok)
            return false;
        push_mask(slot, box, rgba);
        return true;
    };
    flush_capture();
    const size_t mark = m_gpu->ops.size();
    const CGFloat pad = 8;
    const CGFloat none[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    CGFloat       c0[8] = {r[0], r[1], 0, 0, 0, 0, 0, 0}, c1[8] = {0, 0, r[2], r[3], 0, 0, 0, 0};
    CGFloat       c2[8] = {0, 0, 0, 0, r[4], r[5], 0, 0}, c3[8] = {0, 0, 0, 0, 0, 0, r[6], r[7]};
    auto span = [&](CGFloat box, CGFloat radius) { return std::max(box + pad, radius * 2 + pad); };
    const CGFloat w0 = span(CGRectGetMaxX(tl) - x0, r[0]), h0 = span(CGRectGetMaxY(tl) - y0, r[1]);
    const CGFloat w1 = span(x1 - tr.origin.x, r[2]), h1 = span(CGRectGetMaxY(tr) - y0, r[3]);
    const CGFloat w2 = span(x1 - br.origin.x, r[4]), h2 = span(y1 - br.origin.y, r[5]);
    const CGFloat w3 = span(CGRectGetMaxX(bl) - x0, r[6]), h3 = span(y1 - bl.origin.y, r[7]);
    bool ok = mask(0, tl, CGRectMake(x0, y0, w0, h0), c0) &&
              mask(1, tr, CGRectMake(x1 - w1, y0, w1, h1), c1) &&
              mask(2, br, CGRectMake(x1 - w2, y1 - h2, w2, h2), c2) &&
              mask(3, bl, CGRectMake(x0, y1 - h3, w3, h3), c3);
    const CGFloat ix0 = std::ceil(x0), iy0 = std::ceil(y0), ix1 = std::floor(x1), iy1 = std::floor(y1);
    auto edge = [&](int part, CGRect strip, CGRect canonical) -> bool {
        if(strip.size.width <= 0 || strip.size.height <= 0)
            return true;
        uint64_t key = 0xcbf29ce484222325ULL;
        double   v[6] = {double(part + 10), canonical.origin.x - strip.origin.x, canonical.origin.y - strip.origin.y, CGRectGetMaxX(canonical) - strip.origin.x,
                         CGRectGetMaxY(canonical) - strip.origin.y, s};
        OpHash::mix(key, v, sizeof(v));
        const CGRect doc = CGRectMake(canonical.origin.x / s, canonical.origin.y / s, canonical.size.width / s, canonical.size.height / s);
        LHGlyphSlot  slot;
        if(!m_gpu->mask(key, CGRectMake(strip.origin.x, strip.origin.y, 1, 1), [&](CGContextRef ctx) {
               lh::border_radiuses zero;
               ClipToRoundedRect(ctx, doc, zero);
               CGContextClipToRect(ctx, doc);
               CGContextFillRect(ctx, doc);
           }, slot))
            return false;
        push_mask(slot, strip, rgba);
        return true;
    };
    if(ok && y0 != iy0)
        ok = edge(0, CGRectMake(CGRectGetMaxX(tl), std::floor(y0), CGRectGetMinX(tr) - CGRectGetMaxX(tl), 1),
                  CGRectMake(CGRectGetMaxX(tl) - pad, y0, pad * 2 + 1, iy0 + pad - y0));
    if(ok && y1 != iy1)
        ok = edge(1, CGRectMake(CGRectGetMaxX(bl), iy1, CGRectGetMinX(br) - CGRectGetMaxX(bl), 1),
                  CGRectMake(CGRectGetMaxX(bl) - pad, iy1 - pad, pad * 2 + 1, y1 - iy1 + pad));
    if(ok && x0 != ix0)
        ok = edge(2, CGRectMake(std::floor(x0), CGRectGetMaxY(tl), 1, CGRectGetMinY(bl) - CGRectGetMaxY(tl)),
                  CGRectMake(x0, CGRectGetMaxY(tl) - pad, ix0 + pad - x0, pad * 2 + 1));
    if(ok && x1 != ix1)
        ok = edge(3, CGRectMake(ix1, CGRectGetMaxY(tl), 1, CGRectGetMinY(bl) - CGRectGetMaxY(tl)),
                  CGRectMake(ix1 - pad, CGRectGetMaxY(tl) - pad, x1 - ix1 + pad, pad * 2 + 1));
    (void)none;
    if(!ok)
    {
        m_gpu->ops.resize(mark);
        return false;
    }
    auto slab = [&](CGFloat ax, CGFloat ay, CGFloat bx, CGFloat by) {
        if(bx <= ax || by <= ay)
            return;
        LHGPUOp op;
        op.kind   = LHGPUOp::Fill;
        op.device = CGRectMake(ax, ay, bx - ax, by - ay);
        op.key    = 0;
        memcpy(op.color, rgba, 4);
        m_gpu->ops.push_back(op);
    };
    slab(CGRectGetMaxX(tl), iy0, CGRectGetMinX(tr), CGRectGetMaxY(tl));
    slab(ix0, CGRectGetMaxY(tl), ix1, CGRectGetMinY(bl));
    slab(CGRectGetMaxX(bl), CGRectGetMinY(bl), CGRectGetMinX(br), iy1);
    return true;
}

bool LHContainer::record_solid(const lh::background_layer& layer, const lh::web_color& color)
{
    if(m_gpu && m_hidden_depth == 0 && m_groups.empty() && color.alpha != 0 && HasRadius(layer.border_radius) &&
       CGRectEqualToRect(R(layer.clip_box), R(layer.border_box)))
    {
        const CGRect rect = R(layer.border_box);
        bool         inside = true;
        for(const auto& c : m_clips)
        {
            if(c.path || !CGAffineTransformIsIdentity(c.matrix) || !InsideRoundedRect(R(c.box), c.radius, CGRectInset(rect, -1, -1)))
                inside = false;
        }
        if(inside && record_rounded(rect, layer.border_radius, color))
            return true;
    }
    if(!m_gpu || m_hidden_depth > 0 || HasRadius(layer.border_radius) || !m_groups.empty() || color.alpha == 0)
        return false;
    CGRect rect = CGRectIntersection(R(layer.clip_box), R(layer.border_box));
    for(const auto& c : m_clips)
    {
        if(c.path || !CGAffineTransformIsIdentity(c.matrix))
            return false;
        if(HasRadius(c.radius) && !InsideRoundedRect(R(c.box), c.radius, CGRectInset(rect, -1, -1)))
            return false;
        rect = CGRectIntersection(rect, R(c.box));
    }
    flush_capture();
    if(CGRectIsNull(rect) || CGRectIsEmpty(rect))
        return true;
    const CGFloat s = m_gpu->scale;
    LHGPUOp       op;
    op.kind   = LHGPUOp::Fill;
    op.device = CGRectMake(rect.origin.x * s, rect.origin.y * s, rect.size.width * s, rect.size.height * s);
    op.key    = 0;
    memcpy(op.color, device_color(color), 4);
    m_gpu->ops.push_back(op);
    return true;
}

bool LHContainer::record_glyphs(const char* text, Font* f, const lh::web_color& color, const lh::position& pos, CGRect ink)
{
    if(!m_gpu || !m_gpu->glyph || m_hidden_depth > 0 || !m_groups.empty() || !f->shadows.empty() || f->underline ||
       f->overline || f->strike || color.alpha == 0)
        return false;
    for(const auto& c : m_clips)
    {
        if(c.path || !CGAffineTransformIsIdentity(c.matrix) || !InsideRoundedRect(R(c.box), c.radius, ink))
            return false;
    }
    SimpleRun simple;
    if(ShapeSimple(*f->shared, text, simple))
    {
        if(f->shared->colorGlyphs)
            return false;
        flush_capture();
        const size_t   mark     = m_gpu->ops.size();
        const CGFloat  s        = m_gpu->scale;
        const CGFloat  x        = F(pos.x);
        const CGFloat  baseline = F(pos.y) + f->shared->ascent;
        const uint8_t* rgba     = device_color(color);
        for(size_t g = 0; g < simple.count; g++)
        {
            CGPoint     device = CGPointMake((x + simple.xs[g]) * s, baseline * s);
            LHGlyphSlot slot;
            if(!m_gpu->glyph(f->shared->font, simple.glyphs[g], device, slot))
            {
                m_gpu->ops.resize(mark);
                return false;
            }
            if(slot.empty)
                continue;
            LHGPUOp op;
            op.kind   = LHGPUOp::Mask;
            op.device = slot.device;
            op.key    = static_cast<uint64_t>(slot.u) | static_cast<uint64_t>(slot.v) << 16;
            memcpy(op.color, rgba, 4);
            m_gpu->ops.push_back(op);
        }
        return true;
    }
    CTLineRef line = CopyLine(*f->shared, text);
    if(!line)
        return false;
    CFArrayRef runs  = CTLineGetGlyphRuns(line);
    CFIndex    count = CFArrayGetCount(runs);
    for(CFIndex i = 0; i < count; i++)
    {
        auto      run  = static_cast<CTRunRef>(CFArrayGetValueAtIndex(runs, i));
        CTFontRef font = static_cast<CTFontRef>(CFDictionaryGetValue(CTRunGetAttributes(run), kCTFontAttributeName));
        if(!font || (CTFontGetSymbolicTraits(font) & kCTFontTraitColorGlyphs))
        {
            CFRelease(line);
            return false;
        }
    }
    flush_capture();
    const size_t  mark     = m_gpu->ops.size();
    const CGFloat s        = m_gpu->scale;
    const CGFloat x        = F(pos.x);
    const CGFloat baseline = F(pos.y) + f->shared->ascent;
    const uint8_t* rgba    = device_color(color);
    std::vector<CGGlyph> glyphs;
    std::vector<CGPoint> points;
    for(CFIndex i = 0; i < count; i++)
    {
        auto      run  = static_cast<CTRunRef>(CFArrayGetValueAtIndex(runs, i));
        CTFontRef font = static_cast<CTFontRef>(CFDictionaryGetValue(CTRunGetAttributes(run), kCTFontAttributeName));
        CFIndex   n    = CTRunGetGlyphCount(run);
        glyphs.resize(n);
        points.resize(n);
        CTRunGetGlyphs(run, CFRangeMake(0, 0), glyphs.data());
        CTRunGetPositions(run, CFRangeMake(0, 0), points.data());
        for(CFIndex g = 0; g < n; g++)
        {
            CGPoint     device = CGPointMake((x + points[g].x) * s, (baseline - points[g].y) * s);
            LHGlyphSlot slot;
            if(!m_gpu->glyph(font, glyphs[g], device, slot))
            {
                m_gpu->ops.resize(mark);
                CFRelease(line);
                return false;
            }
            if(slot.empty)
                continue;
            LHGPUOp op;
            op.kind   = LHGPUOp::Mask;
            op.device = slot.device;
            op.key    = static_cast<uint64_t>(slot.u) | static_cast<uint64_t>(slot.v) << 16;
            memcpy(op.color, rgba, 4);
            m_gpu->ops.push_back(op);
        }
    }
    CFRelease(line);
    return true;
}

void LHContainer::record_op(OpHash& hash, CGRect ink, bool capture)
{
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_hidden_depth > 0)
        return;
    if(m_gpu)
        flush_capture();
    auto HashMatrix = [](OpHash& hash, const CGAffineTransform& m) {
        if(CGAffineTransformIsIdentity(m))
            return;
        for(CGFloat v : {m.a, m.b, m.c, m.d, m.tx, m.ty})
            hash.number(v);
    };
    for(const auto& effect : m_effects)
    {
        ink = CGRectInset(ink, -effect.reach, -effect.reach);
        hash.integer(static_cast<int64_t>(effect.hash));
        hash.stretch = 0;
    }
    CGAffineTransform matrix = group_matrix();
    CGRect            bounds = CGAffineTransformIsIdentity(matrix) ? ink : CGRectApplyAffineTransform(ink, matrix);
    const CGRect      drawn  = bounds;
    for(const auto& c : m_clips)
    {
        if(hash.stretch)
        {
            for(double v : {F(c.box.x), F(c.box.y), F(c.box.width), F(c.box.height)})
                hash.stretch_number(v);
            const auto& r = c.radius;
            for(lh::pixel_t v : {r.top_left_x, r.top_left_y, r.top_right_x, r.top_right_y, r.bottom_right_x, r.bottom_right_y, r.bottom_left_x, r.bottom_left_y})
            {
                hash.stretch_number(F(v));
                if(F(v) != 0)
                    hash.stretch = 0;
            }
            if(c.path || !CGAffineTransformIsIdentity(c.matrix))
                hash.stretch = 0;
        }
        if(c.path)
        {
            hash.stretch = 0;
            hash.integer(c.path->evenodd);
            for(auto op : c.path->ops) hash.integer(op);
            for(float v : c.path->points) hash.number(v);
        }
        hash.clip(c.box, c.radius, drawn);
        HashMatrix(hash, c.matrix);
        bounds = CGRectIntersection(bounds, CGAffineTransformIsIdentity(c.matrix) ? R(c.box)
                                                                                 : CGRectApplyAffineTransform(R(c.box), c.matrix));
    }
    for(const auto& g : m_groups)
    {
        hash.number(g.opacity);
        if(hash.stretch)
            hash.stretch_number(g.opacity);
    }
    HashMatrix(hash, matrix);
    if(!CGAffineTransformIsIdentity(matrix))
        hash.stretch = 0;
    m_ops->push_back({CGRectIsNull(bounds) ? CGRectNull : CGRectInset(bounds, -1, -1), hash.value, hash.shape, false, hash.stretch, static_cast<CGFloat>(hash.edge)});
    if(!m_gpu || !capture || CGRectIsNull(bounds) || CGRectIsEmpty(bounds))
        return;
    for(const auto& g : m_groups)
    {
        if(g.opacity < 1)
            m_gpu->unsupported = true;
    }
    const CGFloat s      = m_gpu->scale;
    const CGRect  device = CGRectIntegral(CGRectMake(bounds.origin.x * s, bounds.origin.y * s, bounds.size.width * s, bounds.size.height * s));
    if(device.size.width <= 0 || device.size.height <= 0 || device.size.width > 8192 || device.size.height > 8192)
        return;
    uint64_t key = hash.value;
    for(double v : {double(s), double(device.origin.x), double(device.origin.y), double(device.size.width), double(device.size.height)})
        OpHash::mix(key, &v, sizeof(v));
    LHGPUOp op;
    op.kind   = LHGPUOp::Bitmap;
    op.device = device;
    op.key    = key;
    m_gpu->ops.push_back(op);
    if(m_gpu->cached && m_gpu->cached(key))
        return;
    const size_t w   = static_cast<size_t>(device.size.width);
    const size_t h   = static_cast<size_t>(device.size.height);
    CGContextRef ctx = m_gpu->allocate ? m_gpu->allocate(key, device)
                                       : CGBitmapContextCreate(nullptr, w, h, 8, 0, m_gpu->space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    if(!ctx)
        return;
    CGContextTranslateCTM(ctx, 0, h);
    CGContextScaleCTM(ctx, s, -s);
    CGContextTranslateCTM(ctx, -device.origin.x / s, -device.origin.y / s);
    CGContextConcatCTM(ctx, group_matrix());
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    m_capture        = ctx;
    m_capture_key    = key;
    m_capture_device = device;
}

void LHContainer::apply_clips(CGContextRef ctx, CGRect bounds) const
{
    CGRect            ink    = CGRectInset(bounds, -1, -1);
    CGAffineTransform matrix = group_matrix();
    if(!m_clips.empty() && !(std::fabs(matrix.a * matrix.d - matrix.b * matrix.c) > 1e-12))
    {
        CGContextClipToRect(ctx, CGRectZero);
        return;
    }
    for(const auto& c : m_clips)
    {
        CGRect box = R(c.box);
        if(c.path)
        {
            CGAffineTransform t = CGAffineTransformConcat(c.matrix, CGAffineTransformInvert(matrix));
            CGPathRef path = CreateVectorPath(*c.path);
            CGPathRef transformed = CGPathCreateCopyByTransformingPath(path, &t);
            CGContextAddPath(ctx, transformed);
            if(c.path->evenodd) TWClipPath(ctx, true);
            else TWClipPath(ctx, false);
            CGPathRelease(transformed);
            CGPathRelease(path);
            continue;
        }
        if(CGAffineTransformEqualToTransform(c.matrix, matrix))
        {
            if(m_clip_culling && InsideRoundedRect(box, c.radius, ink))
                continue;
            ClipToRoundedRect(ctx, box, c.radius);
            continue;
        }
        CGAffineTransform t = CGAffineTransformConcat(c.matrix, CGAffineTransformInvert(matrix));
        if(!(std::fabs(t.a * t.d - t.b * t.c) > 1e-12))
        {
            CGContextClipToRect(ctx, CGRectZero);
            return;
        }
        if(m_clip_culling && !CGRectIsNull(bounds) && !CGRectIsInfinite(bounds) && std::fabs(t.a * t.d - t.b * t.c) > 1e-9)
        {
            CGRect local = CGRectInset(CGRectApplyAffineTransform(bounds, CGAffineTransformInvert(t)), -1, -1);
            if(std::isfinite(local.origin.x) && std::isfinite(local.origin.y) && std::isfinite(local.size.width) && std::isfinite(local.size.height) &&
               InsideRoundedRect(box, c.radius, local))
                continue;
        }
        CGContextConcatCTM(ctx, t);
        ClipToRoundedRect(ctx, box, c.radius);
        CGContextConcatCTM(ctx, CGAffineTransformInvert(t));
    }
}

void LHContainer::clip_background_layer(CGContextRef ctx, const lh::background_layer& layer) const
{
    ClipToRoundedRect(ctx, R(layer.border_box), layer.border_radius);
    CGContextClipToRect(ctx, R(layer.clip_box));
}

void LHContainer::for_each_tile(const lh::background_layer& layer, CGSize tile,
                                const std::function<void(CGRect)>& fn) const
{
    CGRect origin = R(layer.origin_box);
    CGRect clip   = R(layer.clip_box);
    if(tile.width < 1 || tile.height < 1)
        return;
    bool rx = layer.repeat == lh::background_repeat_repeat || layer.repeat == lh::background_repeat_repeat_x;
    bool ry = layer.repeat == lh::background_repeat_repeat || layer.repeat == lh::background_repeat_repeat_y;

    CGFloat startX = origin.origin.x, startY = origin.origin.y;
    CGFloat endX = startX + tile.width, endY = startY + tile.height;
    if(rx)
    {
        startX -= std::ceil((startX - CGRectGetMinX(clip)) / tile.width) * tile.width;
        endX = CGRectGetMaxX(clip);
    }
    if(ry)
    {
        startY -= std::ceil((startY - CGRectGetMinY(clip)) / tile.height) * tile.height;
        endY = CGRectGetMaxY(clip);
    }
    int guard = 0;
    for(CGFloat y = startY; y < endY && guard < 10000; y += tile.height)
        for(CGFloat x = startX; x < endX && guard < 10000; x += tile.width, ++guard)
            fn(CGRectMake(x, y, tile.width, tile.height));
}

void LHContainer::draw_solid_fill(lh::uint_ptr hdc, const lh::background_layer& layer, const lh::web_color& color)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->solidFill(layer, color);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(2);
        hash.layer(layer);
        hash.color(color);
        static const bool edgeBands = getenv("LH_NO_EDGE_BANDS") == nullptr;
        const auto&       corners   = layer.border_radius;
        const bool        square    = F(corners.top_left_x) == 0 && F(corners.top_left_y) == 0 && F(corners.top_right_x) == 0 && F(corners.top_right_y) == 0 &&
                                      F(corners.bottom_right_x) == 0 && F(corners.bottom_right_y) == 0 && F(corners.bottom_left_x) == 0 && F(corners.bottom_left_y) == 0;
        if(edgeBands && square)
        {
            hash.stretch = 0x84222325cbf29ce4ULL;
            for(const lh::position* p : {&layer.border_box, &layer.clip_box, &layer.origin_box})
            {
                hash.stretch_number(F(p->x));
                hash.stretch_number(F(p->y));
                hash.stretch_number(F(p->height));
            }
            const auto& r = layer.border_radius;
            for(lh::pixel_t v : {r.top_left_x, r.top_left_y, r.top_right_x, r.top_right_y, r.bottom_right_x, r.bottom_right_y, r.bottom_left_x, r.bottom_left_y})
                hash.stretch_number(F(v));
            const unsigned char c[5] = {color.red, color.green, color.blue, color.alpha, color.is_current_color};
            OpHash::mix(hash.stretch, c, sizeof(c));
            hash.stretch_number(layer.is_root ? 1 : 0);
            hash.edge = std::max(F(r.top_right_x), F(r.bottom_right_x));
        }
        record_op(hash, R(layer.clip_box), !record_solid(layer, color));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(color.alpha == 0)
        return;
    CGContextSaveGState(ctx);
    apply_clips(ctx, R(layer.clip_box));
    clip_background_layer(ctx, layer);
    SetFill(ctx, color);
    CGContextFillRect(ctx, R(layer.clip_box));
    CGContextRestoreGState(ctx);
}

static CGGradientRef CreateGradient(const std::vector<lh::background_layer::color_point>& points)
{
    if(points.empty())
        return nullptr;
    std::vector<CGFloat> comps, locs;
    CGFloat              prev = 0;
    for(const auto& p : points)
    {
        comps.insert(comps.end(), {p.color.red / 255.0, p.color.green / 255.0, p.color.blue / 255.0,
                                   p.color.alpha / 255.0});
        prev = std::max(prev, std::clamp<CGFloat>(p.offset, 0, 1));
        locs.push_back(prev);
    }
    if(points.size() == 1)
    {
        comps.insert(comps.end(), comps.begin(), comps.begin() + 4);
        locs = {0, 1};
    }
    return CGGradientCreateWithColorComponents(SRGB(), comps.data(), locs.data(), locs.size());
}

static void ConicColorAt(const std::vector<lh::background_layer::color_point>& points, CGFloat t, CGFloat out[4])
{
    auto set = [&](const lh::web_color& a, const lh::web_color& b, CGFloat k) {
        out[0] = (a.red + (b.red - a.red) * k) / 255.0;
        out[1] = (a.green + (b.green - a.green) * k) / 255.0;
        out[2] = (a.blue + (b.blue - a.blue) * k) / 255.0;
        out[3] = (a.alpha + (b.alpha - a.alpha) * k) / 255.0;
    };
    CGFloat prevOffset = std::clamp<CGFloat>(points.front().offset, 0, 1);
    if(t <= prevOffset)
    {
        set(points.front().color, points.front().color, 0);
        return;
    }
    for(size_t i = 1; i < points.size(); i++)
    {
        CGFloat offset = std::max(prevOffset, std::clamp<CGFloat>(points[i].offset, 0, 1));
        if(t <= offset)
        {
            CGFloat span = offset - prevOffset;
            set(points[i - 1].color, points[i].color, span > 0 ? (t - prevOffset) / span : 1);
            return;
        }
        prevOffset = offset;
    }
    set(points.back().color, points.back().color, 0);
}

static void DrawConicWedges(CGContextRef ctx, const std::vector<lh::background_layer::color_point>& points, CGPoint c,
                            CGFloat start, CGFloat radius)
{
    const int steps = 180;
    for(int i = 0; i < steps; i++)
    {
        CGFloat a0 = start + 2 * M_PI * i / steps;
        CGFloat a1 = start + 2 * M_PI * (i + 1) / steps + 0.01;
        CGFloat color[4];
        ConicColorAt(points, (i + 0.5) / steps, color);
        CGContextSetRGBFillColor(ctx, color[0], color[1], color[2], color[3]);
        CGContextBeginPath(ctx);
        CGContextMoveToPoint(ctx, c.x, c.y);
        CGContextAddLineToPoint(ctx, c.x + radius * std::cos(a0), c.y + radius * std::sin(a0));
        CGContextAddLineToPoint(ctx, c.x + radius * std::cos(a1), c.y + radius * std::sin(a1));
        CGContextClosePath(ctx);
        CGContextFillPath(ctx);
    }
}

static void AppendColorPoints(std::string& key, const std::vector<lh::background_layer::color_point>& points)
{
    for(const auto& p : points)
    {
        AppendNumber(key, p.offset);
        AppendInteger(key, p.color.red | p.color.green << 8 | p.color.blue << 16 | (uint32_t)p.color.alpha << 24);
    }
    key.push_back('|');
}

void LHContainer::draw_linear_gradient(lh::uint_ptr hdc, const lh::background_layer& layer,
                                       const lh::background_layer::linear_gradient& gradient)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->linearGradient(layer, gradient);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(3);
        hash.layer(layer);
        hash.gradient(gradient);
        hash.number(gradient.start.x);
        hash.number(gradient.start.y);
        hash.number(gradient.end.x);
        hash.number(gradient.end.y);
        record_op(hash, R(layer.clip_box));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto          ctx = reinterpret_cast<CGContextRef>(hdc);
    if(gradient.color_points.empty())
        return;
    CGGradientRef g = nullptr;
    CGContextSaveGState(ctx);
    apply_clips(ctx, R(layer.clip_box));
    clip_background_layer(ctx, layer);
    CGRect origin = R(layer.origin_box);
    std::string key = "L|";
    AppendColorPoints(key, gradient.color_points);
    AppendNumber(key, gradient.start.x - origin.origin.x);
    AppendNumber(key, gradient.start.y - origin.origin.y);
    AppendNumber(key, gradient.end.x - origin.origin.x);
    AppendNumber(key, gradient.end.y - origin.origin.y);
    for_each_tile(layer, origin.size, [&](CGRect tile) {
        auto paint = [&](CGContextRef c) {
            if(!g)
                g = CreateGradient(gradient.color_points);
            if(!g)
                return;
            CGContextSaveGState(c);
            CGContextClipToRect(c, tile);
            CGContextTranslateCTM(c, tile.origin.x - origin.origin.x, tile.origin.y - origin.origin.y);
            CGContextDrawLinearGradient(c, g, CGPointMake(gradient.start.x, gradient.start.y),
                                        CGPointMake(gradient.end.x, gradient.end.y),
                                        kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation);
            CGContextRestoreGState(c);
        };
        if(!DrawRasterized(ctx, tile, key, paint))
            paint(ctx);
    });
    CGContextRestoreGState(ctx);
    if(g)
        CGGradientRelease(g);
}

void LHContainer::draw_radial_gradient(lh::uint_ptr hdc, const lh::background_layer& layer,
                                       const lh::background_layer::radial_gradient& gradient)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->radialGradient(layer, gradient);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(4);
        hash.layer(layer);
        hash.gradient(gradient);
        hash.number(gradient.position.x);
        hash.number(gradient.position.y);
        hash.number(gradient.radius.x);
        hash.number(gradient.radius.y);
        record_op(hash, R(layer.clip_box));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(gradient.radius.x <= 0 || gradient.radius.y <= 0 || gradient.color_points.empty())
        return;
    CGGradientRef g = nullptr;
    CGContextSaveGState(ctx);
    apply_clips(ctx, R(layer.clip_box));
    clip_background_layer(ctx, layer);
    CGRect  origin = R(layer.origin_box);
    CGPoint c      = CGPointMake(gradient.position.x, gradient.position.y);
    std::string key = "R|";
    AppendColorPoints(key, gradient.color_points);
    AppendNumber(key, c.x - origin.origin.x);
    AppendNumber(key, c.y - origin.origin.y);
    AppendNumber(key, gradient.radius.x);
    AppendNumber(key, gradient.radius.y);
    for_each_tile(layer, origin.size, [&](CGRect tile) {
        auto paint = [&](CGContextRef cg) {
            if(!g)
                g = CreateGradient(gradient.color_points);
            if(!g)
                return;
            CGContextSaveGState(cg);
            CGContextClipToRect(cg, tile);
            CGContextTranslateCTM(cg, tile.origin.x - origin.origin.x, tile.origin.y - origin.origin.y);
            CGContextTranslateCTM(cg, c.x, c.y);
            CGContextScaleCTM(cg, 1, gradient.radius.y / gradient.radius.x);
            CGContextTranslateCTM(cg, -c.x, -c.y);
            CGContextDrawRadialGradient(cg, g, c, 0, c, gradient.radius.x,
                                        kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation);
            CGContextRestoreGState(cg);
        };
        if(!DrawRasterized(ctx, tile, key, paint))
            paint(ctx);
    });
    CGContextRestoreGState(ctx);
    if(g)
        CGGradientRelease(g);
}

void LHContainer::draw_conic_gradient(lh::uint_ptr hdc, const lh::background_layer& layer,
                                      const lh::background_layer::conic_gradient& gradient)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->conicGradient(layer, gradient);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(5);
        hash.layer(layer);
        hash.gradient(gradient);
        hash.number(gradient.position.x);
        hash.number(gradient.position.y);
        hash.number(gradient.angle);
        hash.number(gradient.radius);
        record_op(hash, R(layer.clip_box));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(gradient.color_points.empty())
        return;
    CGContextSaveGState(ctx);
    apply_clips(ctx, R(layer.clip_box));
    clip_background_layer(ctx, layer);
    CGRect  origin = R(layer.origin_box);
    CGPoint c      = CGPointMake(gradient.position.x, gradient.position.y);
    CGFloat start  = (gradient.angle - 90.0) * M_PI / 180.0;
    CGFloat radius = std::hypot(origin.size.width, origin.size.height) + 2;
    std::string key = "C|";
    AppendColorPoints(key, gradient.color_points);
    AppendNumber(key, c.x - origin.origin.x);
    AppendNumber(key, c.y - origin.origin.y);
    AppendNumber(key, start);
    AppendNumber(key, radius);
    for_each_tile(layer, origin.size, [&](CGRect tile) {
        auto paint = [&](CGContextRef cg) {
            CGContextSaveGState(cg);
            CGContextClipToRect(cg, tile);
            CGContextTranslateCTM(cg, tile.origin.x - origin.origin.x, tile.origin.y - origin.origin.y);
            if(@available(macOS 14.0, *))
            {
                CGGradientRef g = CreateGradient(gradient.color_points);
                if(g)
                {
                    CGContextDrawConicGradient(cg, g, c, start);
                    CGGradientRelease(g);
                }
            }
            else
                DrawConicWedges(cg, gradient.color_points, c, start, radius);
            CGContextRestoreGState(cg);
        };
        if(!DrawRasterized(ctx, tile, key, paint))
            paint(ctx);
    });
    CGContextRestoreGState(ctx);
}

void LHContainer::load_image(const char* src, const char* baseurl, bool redraw_on_ready)
{
    if(src)
        LookupImage(src);
}

void LHContainer::get_image_size(const char* src, const char* baseurl, lh::size& sz)
{
    ImageHandle img = src ? LookupImage(src) : ImageHandle();
    sz.width        = img ? static_cast<float>(img.size.width) : 0.0f;
    sz.height       = img ? static_cast<float>(img.size.height) : 0.0f;
}

std::string LHContainer::get_image_aspect(const char* src)
{
    return src ? LookupImage(src).aspect : std::string();
}

void LHContainer::draw_image(lh::uint_ptr hdc, const lh::background_layer& layer, const std::string& url,
                             const std::string& base_url)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->image(layer, url);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(6);
        hash.layer(layer);
        hash.text(url);
        record_op(hash, R(layer.clip_box));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(url.empty() || layer.clip_box.width == 0_px || layer.clip_box.height == 0_px)
        return;
    ImageHandle img = LookupImage(url);
    if(!img)
        return;
    CGContextSaveGState(ctx);
    apply_clips(ctx, R(layer.clip_box));
    clip_background_layer(ctx, layer);
    CGRect origin = R(layer.origin_box);
    std::string key = "I|";
    AppendInteger(key, std::hash<std::string>()(url));
    AppendInteger(key, url.size());
    AppendInteger(key, (uintptr_t)img.image);
    AppendInteger(key, CGImageGetWidth(img.image));
    AppendInteger(key, CGImageGetHeight(img.image));
    const bool changesEveryFrame = url.compare(0, 9, "twcanvas:") == 0;
    for_each_tile(layer, origin.size, [&](CGRect tile) {
        if(changesEveryFrame || !DrawRasterized(ctx, tile, key, [&](CGContextRef c) { DrawCGImage(c, img.image, tile); }))
            DrawCGImage(ctx, img.image, tile);
    });
    CGContextRestoreGState(ctx);
}

void LHContainer::draw_list_marker(lh::uint_ptr hdc, const lh::list_marker& marker)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->listMarker(marker);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(7);
        hash.text(marker.image);
        hash.integer(marker.marker_type);
        hash.color(marker.color);
        hash.box(marker.pos);
        hash.integer(marker.index);
        record_op(hash, R(marker.pos));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto   ctx = reinterpret_cast<CGContextRef>(hdc);
    CGRect r   = R(marker.pos);
    CGContextSaveGState(ctx);
    apply_clips(ctx, r);
    if(!marker.image.empty())
    {
        ImageHandle img = LookupImage(marker.image);
        if(img)
            DrawCGImage(ctx, img.image, r);
    }
    else
    {
        switch(marker.marker_type)
        {
        case lh::list_style_type_circle:
            SetStroke(ctx, marker.color);
            CGContextSetLineWidth(ctx, 1);
            CGContextStrokeEllipseInRect(ctx, CGRectInset(r, 0.5, 0.5));
            break;
        case lh::list_style_type_disc:
            SetFill(ctx, marker.color);
            CGContextFillEllipseInRect(ctx, r);
            break;
        case lh::list_style_type_square:
            SetFill(ctx, marker.color);
            CGContextFillRect(ctx, r);
            break;
        default:
            break;
        }
    }
    CGContextRestoreGState(ctx);
}

static lh::web_color ShadeBorder(lh::web_color color, bool topLeft, bool inset)
{
    auto distance = [](const lh::web_color& c, int v) {
        return (c.red - v) * (c.red - v) + (c.green - v) * (c.green - v) + (c.blue - v) * (c.blue - v);
    };
    const float scale = std::nextafter(256.0f, 0.0f);
    const float r = color.red / 255.0f, g = color.green / 255.0f, b = color.blue / 255.0f;
    const float v = std::max({r, g, b});
    auto        scaled = [&](float m) {
        return lh::web_color(static_cast<lh::byte>(std::clamp(static_cast<int>(m * r * scale), 0, 255)),
                             static_cast<lh::byte>(std::clamp(static_cast<int>(m * g * scale), 0, 255)),
                             static_cast<lh::byte>(std::clamp(static_cast<int>(m * b * scale), 0, 255)), color.alpha);
    };
    if(topLeft == inset)
    {
        if(distance(color, 0) <= 3 * 0x20 * 0x20)
            return color;
        if(color.red == 255 && color.green == 255 && color.blue == 255)
            return lh::web_color(0xAB, 0xAB, 0xAB, color.alpha);
        return scaled(std::max(0.0f, (v - 0.33f) / v));
    }
    if(distance(color, 255) <= 3 * 0x14 * 0x14)
        return color;
    if(v == 0)
        return lh::web_color(0x54, 0x54, 0x54, color.alpha);
    return scaled(std::min(1.0f, v + 0.33f) / v);
}

void LHContainer::draw_borders(lh::uint_ptr hdc, const lh::borders& borders, const lh::position& draw_pos, bool root)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->borders(borders, draw_pos, root);
        return;
    }
    if(m_runs)
        return;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(8);
        hash.border(borders.left);
        hash.border(borders.top);
        hash.border(borders.right);
        hash.border(borders.bottom);
        hash.radius(borders.radius);
        hash.box(draw_pos);
        hash.integer(root);
        record_op(hash, R(draw_pos));
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto    ctx     = reinterpret_cast<CGContextRef>(hdc);
    auto    visible = [](const lh::border& b) { return b.width > 0_px && b.style > lh::border_style_hidden; };
    CGFloat L       = visible(borders.left) ? F(borders.left.width) : 0;
    CGFloat T       = visible(borders.top) ? F(borders.top.width) : 0;
    CGFloat Rr      = visible(borders.right) ? F(borders.right.width) : 0;
    CGFloat B       = visible(borders.bottom) ? F(borders.bottom.width) : 0;
    if(L == 0 && T == 0 && Rr == 0 && B == 0)
        return;

    CGRect outer = R(draw_pos);
    CGRect inner = CGRectMake(outer.origin.x + L, outer.origin.y + T, outer.size.width - L - Rr,
                              outer.size.height - T - B);
    const lh::border_radiuses& orad = borders.radius;
    lh::border_radiuses        irad = orad;
    irad.top_left_x     = std::max(0.0f, orad.top_left_x.value() - (float)L);
    irad.top_left_y     = std::max(0.0f, orad.top_left_y.value() - (float)T);
    irad.top_right_x    = std::max(0.0f, orad.top_right_x.value() - (float)Rr);
    irad.top_right_y    = std::max(0.0f, orad.top_right_y.value() - (float)T);
    irad.bottom_right_x = std::max(0.0f, orad.bottom_right_x.value() - (float)Rr);
    irad.bottom_right_y = std::max(0.0f, orad.bottom_right_y.value() - (float)B);
    irad.bottom_left_x  = std::max(0.0f, orad.bottom_left_x.value() - (float)L);
    irad.bottom_left_y  = std::max(0.0f, orad.bottom_left_y.value() - (float)B);

    CGMutablePathRef ring = CGPathCreateMutable();
    AddRoundedRect(ring, outer, orad);
    if(inner.size.width > 0 && inner.size.height > 0)
        AddRoundedRect(ring, inner, irad);

    CGContextSaveGState(ctx);
    apply_clips(ctx, outer);

    const lh::border* sides[4] = {&borders.top, &borders.right, &borders.bottom, &borders.left};
    bool              uniform  = true;
    for(int i = 1; i < 4; i++)
        uniform = uniform && sides[i]->width == sides[0]->width && sides[i]->color == sides[0]->color &&
                  sides[i]->style == sides[0]->style && visible(*sides[i]) == visible(*sides[0]);
    lh::border_style style = borders.top.style;

    if(uniform && (style == lh::border_style_dotted || style == lh::border_style_dashed) && HasRadius(orad))
    {
        CGFloat w = T;
        CGContextAddPath(ctx, ring);
        TWClipPath(ctx, true);
        lh::border_radiuses mrad = orad;
        mrad -= Uniform(w / 2);
        CGRect           midRect = CGRectInset(outer, w / 2, w / 2);
        CGMutablePathRef mid     = CGPathCreateMutable();
        AddRoundedRect(mid, midRect, mrad);
        BorderDash dash = BorderDashFor(std::floor(RoundedRectLength(midRect, mrad)), w, style == lh::border_style_dotted, true);
        SetStroke(ctx, borders.top.color);
        CGContextSetLineWidth(ctx, dash.round ? w : w * 2.2);
        ApplyBorderDash(ctx, dash);
        CGContextAddPath(ctx, mid);
        CGContextStrokePath(ctx);
        CGPathRelease(mid);
    }
    else if(uniform && style == lh::border_style_double && T >= 3)
    {
        CGFloat          t3 = T / 3;
        CGMutablePathRef p  = CGPathCreateMutable();
        AddRoundedRect(p, outer, orad);
        lh::border_radiuses r1 = orad;
        r1 -= Uniform(t3);
        AddRoundedRect(p, CGRectInset(outer, t3, t3), r1);
        lh::border_radiuses r2 = orad;
        r2 -= Uniform(t3 * 2);
        AddRoundedRect(p, CGRectInset(outer, t3 * 2, t3 * 2), r2);
        if(inner.size.width > 0 && inner.size.height > 0)
            AddRoundedRect(p, inner, irad);
        SetFill(ctx, borders.top.color);
        CGContextAddPath(ctx, p);
        CGContextEOFillPath(ctx);
        CGPathRelease(p);
    }
    else if(uniform && (style == lh::border_style_solid || style == lh::border_style_double))
    {
        SetFill(ctx, borders.top.color);
        CGContextAddPath(ctx, ring);
        CGContextEOFillPath(ctx);
    }
    else
    {
        CGContextAddPath(ctx, ring);
        TWClipPath(ctx, true);
        auto drawSides = [&](CGRect o, CGRect i, int pass) {
            CGFloat ox0 = CGRectGetMinX(o), oy0 = CGRectGetMinY(o), ox1 = CGRectGetMaxX(o), oy1 = CGRectGetMaxY(o);
            CGFloat ix0 = CGRectGetMinX(i), iy0 = CGRectGetMinY(i), ix1 = CGRectGetMaxX(i), iy1 = CGRectGetMaxY(i);
            CGFloat px0 = ix0, py0 = iy0, px1 = ix1, py1 = iy1;
            if(HasRadius(orad))
            {
                CGFloat horizontal = (ix0 - ox0) + (ox1 - ix1);
                CGFloat vertical = (iy0 - oy0) + (oy1 - iy1);
                CGFloat extent = std::min(horizontal > 0 ? (ox1 - ox0) / horizontal : CGFLOAT_MAX,
                                          vertical > 0 ? (oy1 - oy0) / vertical : CGFLOAT_MAX);
                px0 = ox0 + (ix0 - ox0) * extent;
                py0 = oy0 + (iy0 - oy0) * extent;
                px1 = ox1 - (ox1 - ix1) * extent;
                py1 = oy1 - (oy1 - iy1) * extent;
            }
            struct Side
            {
                const lh::border* b;
                CGFloat           w;
                CGPoint           poly[4];
                CGPoint           a, z;
                bool              topLeft;
            } list[4] = {
                {&borders.top, iy0 - oy0, {{ox0, oy0}, {ox1, oy0}, {px1, py0}, {px0, py0}}, {ox0, (oy0 + iy0) / 2}, {ox1, (oy0 + iy0) / 2}, true},
                {&borders.right, ox1 - ix1, {{ox1, oy0}, {ox1, oy1}, {px1, py1}, {px1, py0}}, {(ox1 + ix1) / 2, oy0}, {(ox1 + ix1) / 2, oy1}, false},
                {&borders.bottom, oy1 - iy1, {{ox1, oy1}, {ox0, oy1}, {px0, py1}, {px1, py1}}, {ox0, (oy1 + iy1) / 2}, {ox1, (oy1 + iy1) / 2}, false},
                {&borders.left, ix0 - ox0, {{ox0, oy1}, {ox0, oy0}, {px0, py0}, {px0, py1}}, {(ox0 + ix0) / 2, oy0}, {(ox0 + ix0) / 2, oy1}, true},
            };
            for(auto& s : list)
            {
                if(s.w <= 0)
                    continue;
                lh::web_color c = s.b->color;
                switch(s.b->style)
                {
                case lh::border_style_inset:
                case lh::border_style_outset:
                    if(pass == 0)
                        c = ShadeBorder(c, s.topLeft, s.b->style == lh::border_style_inset);
                    break;
                case lh::border_style_groove:
                case lh::border_style_ridge:
                    c = ShadeBorder(c, s.topLeft, (s.b->style == lh::border_style_groove) == (pass != 2));
                    break;
                default:
                    if(pass == 2)
                        continue;
                    break;
                }
                if(pass == 2 && s.b->style != lh::border_style_groove && s.b->style != lh::border_style_ridge)
                    continue;
                CGContextSaveGState(ctx);
                CGContextBeginPath(ctx);
                CGContextAddLines(ctx, s.poly, 4);
                CGContextClosePath(ctx);
                if(s.b->style == lh::border_style_dotted || s.b->style == lh::border_style_dashed)
                {
                    TWClipPath(ctx, false);
                    bool dotted = s.b->style == lh::border_style_dotted;
                    SetStroke(ctx, c);
                    if(HasRadius(orad))
                    {
                        CGRect              midRect = CGRectMake(ox0 + L / 2, oy0 + T / 2, ox1 - ox0 - (L + Rr) / 2, oy1 - oy0 - (T + B) / 2);
                        lh::border_radiuses mrad    = orad;
                        mrad.top_left_x     = std::max(0.0f, orad.top_left_x.value() - (float)L / 2);
                        mrad.top_left_y     = std::max(0.0f, orad.top_left_y.value() - (float)T / 2);
                        mrad.top_right_x    = std::max(0.0f, orad.top_right_x.value() - (float)Rr / 2);
                        mrad.top_right_y    = std::max(0.0f, orad.top_right_y.value() - (float)T / 2);
                        mrad.bottom_right_x = std::max(0.0f, orad.bottom_right_x.value() - (float)Rr / 2);
                        mrad.bottom_right_y = std::max(0.0f, orad.bottom_right_y.value() - (float)B / 2);
                        mrad.bottom_left_x  = std::max(0.0f, orad.bottom_left_x.value() - (float)L / 2);
                        mrad.bottom_left_y  = std::max(0.0f, orad.bottom_left_y.value() - (float)B / 2);
                        BorderDash dash = BorderDashFor(std::floor(RoundedRectLength(midRect, mrad)), s.w, dotted, true);
                        CGFloat    widest = s.b == &borders.top || s.b == &borders.bottom ? std::max({s.w, L, Rr}) : std::max({s.w, T, B});
                        CGContextSetLineWidth(ctx, dash.round ? s.w : widest * 2.2);
                        ApplyBorderDash(ctx, dash);
                        CGMutablePathRef mid = CGPathCreateMutable();
                        AddRoundedRect(mid, midRect, mrad);
                        CGContextAddPath(ctx, mid);
                        CGPathRelease(mid);
                    }
                    else
                    {
                        CGFloat    length = std::round(std::hypot(s.z.x - s.a.x, s.z.y - s.a.y));
                        BorderDash dash   = BorderDashFor(length, s.w, dotted, false);
                        CGPoint    a = s.a, z = s.z;
                        if(dash.round && length > 0)
                        {
                            CGFloat inset = s.w / 2 / length;
                            CGFloat dx = (s.z.x - s.a.x) * inset, dy = (s.z.y - s.a.y) * inset;
                            a = CGPointMake(s.a.x + dx, s.a.y + dy);
                            z = CGPointMake(s.z.x - dx, s.z.y - dy);
                        }
                        CGContextSetLineWidth(ctx, s.w);
                        ApplyBorderDash(ctx, dash);
                        CGContextMoveToPoint(ctx, a.x, a.y);
                        CGContextAddLineToPoint(ctx, z.x, z.y);
                    }
                    CGContextStrokePath(ctx);
                }
                else
                {
                    SetFill(ctx, c);
                    if(HasRadius(orad))
                        CGContextSetShouldAntialias(ctx, false);
                    CGContextFillPath(ctx);
                }
                CGContextRestoreGState(ctx);
            }
        };
        drawSides(outer, inner, 0);
        bool grooved = false;
        for(const auto* side : sides)
            grooved = grooved || side->style == lh::border_style_groove || side->style == lh::border_style_ridge;
        if(grooved)
        {
            CGRect mid = CGRectMake(outer.origin.x + L / 2, outer.origin.y + T / 2, outer.size.width - (L + Rr) / 2,
                                    outer.size.height - (T + B) / 2);
            lh::border_radiuses mrad = orad;
            mrad.top_left_x     = std::max(0.0f, orad.top_left_x.value() - (float)L / 2);
            mrad.top_left_y     = std::max(0.0f, orad.top_left_y.value() - (float)T / 2);
            mrad.top_right_x    = std::max(0.0f, orad.top_right_x.value() - (float)Rr / 2);
            mrad.top_right_y    = std::max(0.0f, orad.top_right_y.value() - (float)T / 2);
            mrad.bottom_right_x = std::max(0.0f, orad.bottom_right_x.value() - (float)Rr / 2);
            mrad.bottom_right_y = std::max(0.0f, orad.bottom_right_y.value() - (float)B / 2);
            mrad.bottom_left_x  = std::max(0.0f, orad.bottom_left_x.value() - (float)L / 2);
            mrad.bottom_left_y  = std::max(0.0f, orad.bottom_left_y.value() - (float)B / 2);
            CGMutablePathRef midPath = CGPathCreateMutable();
            AddRoundedRect(midPath, mid, mrad);
            CGContextAddPath(ctx, midPath);
            TWClipPath(ctx, false);
            CGPathRelease(midPath);
            drawSides(mid, inner, 2);
        }
    }
    CGContextRestoreGState(ctx);
    CGPathRelease(ring);
}

bool LHContainer::draw_cached(lh::uint_ptr hdc, const lh::position& box, uint64_t key, const std::function<void(lh::uint_ptr)>& paint)
{
    if(lh::g_paint_trace)
        return false;
    flush_text();
    if(m_hidden_depth > 0 && !m_painter)
        return true;
    if(m_painter || m_runs || m_capture)
        return false;
    CGRect r = R(box);
    if(r.size.width <= 0 || r.size.height <= 0)
        return false;
    if(m_ops)
    {
        OpHash hash;
        hash.integer(77);
        hash.integer(static_cast<intptr_t>(key));
        hash.box(box);
        record_op(hash, r);
        return true;
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(!ctx)
        return false;
    CGRect device   = CGRectIntegral(CGContextConvertRectToDeviceSpace(ctx, r));
    CGRect integral = CGContextConvertRectToUserSpace(ctx, device);
    std::string cacheKey = "svg:";
    AppendInteger(cacheKey, static_cast<uintptr_t>(key));
    AppendNumber(cacheKey, integral.origin.x - r.origin.x);
    AppendNumber(cacheKey, integral.origin.y - r.origin.y);
    return DrawRasterized(ctx, integral, cacheKey, [&](CGContextRef c) { paint(reinterpret_cast<lh::uint_ptr>(c)); });
}

void LHContainer::draw_isolated(lh::uint_ptr hdc, const lh::position& box, const std::function<void(lh::uint_ptr)>& paint)
{
    if(lh::g_paint_trace)
    {
        paint(hdc);
        return;
    }
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_hidden_depth > 0 && !m_painter)
        return;
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(m_painter || m_runs || m_ops || !ctx)
    {
        paint(hdc);
        return;
    }
    CGRect integral = CGContextConvertRectToUserSpace(ctx, CGRectIntegral(CGContextConvertRectToDeviceSpace(ctx, R(box))));
    if(DrawRasterized(ctx, integral, std::string(), [&](CGContextRef c) { paint(reinterpret_cast<lh::uint_ptr>(c)); }, false))
        return;
    CGContextSaveGState(ctx);
    CGContextClipToRect(ctx, integral);
    CGContextBeginTransparencyLayer(ctx, nullptr);
    paint(hdc);
    CGContextEndTransparencyLayer(ctx);
    CGContextRestoreGState(ctx);
}

void LHContainer::draw_box_shadow(lh::uint_ptr hdc, const lh::position& box, const lh::border_radiuses& radius,
                                  const lh::shadow_paint& shadow)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter)
    {
        m_painter->boxShadow(box, radius, shadow);
        return;
    }
    if(m_runs || shadow.color.alpha == 0)
        return;
    CGFloat dx = F(shadow.x), dy = F(shadow.y), blur = F(shadow.blur), spread = F(shadow.spread);
    CGRect  r     = R(box);
    CGFloat reach = blur + std::fabs(spread) + std::max(std::fabs(dx), std::fabs(dy)) + 2;
    CGRect  ink   = shadow.inset ? r : CGRectInset(r, -reach, -reach);
    if(m_ops)
    {
        OpHash hash;
        hash.integer(9);
        hash.box(box);
        hash.radius(radius);
        for(CGFloat v : {dx, dy, blur, spread})
            hash.number(v);
        hash.color(shadow.color);
        hash.integer(shadow.inset);
        record_op(hash, ink);
        if(!m_capture)
            return;
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(!ctx || r.size.width <= 0 || r.size.height <= 0)
        return;
    if(!m_capture)
    {
        CGRect device = CGRectIntegral(CGContextConvertRectToDeviceSpace(ctx, ink));
        CGRect aligned = CGContextConvertRectToUserSpace(ctx, device);
        std::string key = "shadow:";
        CGFloat phaseX = std::round((r.origin.x - aligned.origin.x) * 1e9) / 1e9;
        CGFloat phaseY = std::round((r.origin.y - aligned.origin.y) * 1e9) / 1e9;
        for(CGFloat v : {phaseX, phaseY, dx, dy, blur, spread})
            AppendNumber(key, v);
        for(auto v : {radius.top_left_x, radius.top_left_y, radius.top_right_x, radius.top_right_y, radius.bottom_right_x, radius.bottom_right_y, radius.bottom_left_x, radius.bottom_left_y})
            AppendNumber(key, F(v));
        AppendInteger(key, (shadow.color.red << 24) | (shadow.color.green << 16) | (shadow.color.blue << 8) | shadow.color.alpha);
        AppendInteger(key, shadow.inset);
        CGContextSaveGState(ctx);
        apply_clips(ctx, ink);
        lh::shadow_paint copy = shadow;
        CGRect compact = r;
        size_t stretchX = 0, stretchY = 0;
        CGAffineTransform transform = CGContextGetCTM(ctx);
        CGFloat scaleX = std::fabs(transform.a), scaleY = std::fabs(transform.d);
        if(!getenv("LH_NO_SHADOW_SLICES") && !shadow.inset && spread >= 0 && blur > 0 && scaleX > 0 && scaleY > 0 &&
           std::fabs(transform.b) < 1e-9 && std::fabs(transform.c) < 1e-9 && device.size.width * device.size.height <= (4 << 20))
        {
            CGFloat cornerX = std::max({F(radius.top_left_x), F(radius.top_right_x), F(radius.bottom_left_x), F(radius.bottom_right_x)});
            CGFloat cornerY = std::max({F(radius.top_left_y), F(radius.top_right_y), F(radius.bottom_left_y), F(radius.bottom_right_y)});
            CGFloat capX = std::ceil((reach + cornerX + blur * 2 + std::fabs(spread) + std::fabs(dx) + 4) * scaleX);
            CGFloat capY = std::ceil((reach + cornerY + blur * 2 + std::fabs(spread) + std::fabs(dy) + 4) * scaleY);
            if(capX >= 1 && device.size.width > capX * 2 + 1)
                stretchX = (size_t)(device.size.width - capX * 2 - 1);
            if(capY >= 1 && device.size.height > capY * 2 + 1)
                stretchY = (size_t)(device.size.height - capY * 2 - 1);
            if(stretchY * device.size.width > stretchX * device.size.height)
                stretchX = 0;
            else
                stretchY = 0;
            compact.size.width -= stretchX / scaleX;
            compact.size.height -= stretchY / scaleY;
        }
        AppendInteger(key, stretchX > 0);
        AppendInteger(key, stretchY > 0);
        std::string rasterKey = key;
        const CGSize keySize = getenv("LH_NO_SHADOW_RESIZE_CACHE") ? r.size : compact.size;
        AppendNumber(rasterKey, keySize.width);
        AppendNumber(rasterKey, keySize.height);
        bool drawn = DrawRasterized(ctx, aligned, rasterKey, [&](CGContextRef bitmap) { paint_box_shadow(bitmap, compact, radius, copy); }, true, stretchX, stretchY);
        if(!drawn)
        {
            CGContextSaveGState(ctx);
            CGMutablePathRef clip = CGPathCreateMutable();
            if(!shadow.inset)
                CGPathAddRect(clip, nullptr, CGRectInset(ink, -1, -1));
            AddRoundedRect(clip, r, radius);
            CGContextAddPath(ctx, clip);
            if(shadow.inset)
                TWClipPath(ctx, false);
            else
                TWClipPath(ctx, true);
            CGPathRelease(clip);
            AppendNumber(key, r.size.width);
            AppendNumber(key, r.size.height);
            drawn = DrawDownsampled(ctx, aligned, key + "|down", blur, [&](CGContextRef bitmap) { paint_box_shadow(bitmap, r, radius, copy); });
            CGContextRestoreGState(ctx);
        }
        CGContextRestoreGState(ctx);
        if(drawn)
            return;
    }
    CGContextSaveGState(ctx);
    apply_clips(ctx, ink);
    paint_box_shadow(ctx, r, radius, shadow);
    CGContextRestoreGState(ctx);
}

void LHContainer::paint_box_shadow(CGContextRef ctx, CGRect r, const lh::border_radiuses& radius, const lh::shadow_paint& shadow)
{
    CGFloat dx = F(shadow.x), dy = F(shadow.y), blur = F(shadow.blur), spread = F(shadow.spread);
    CGFloat reach = blur + std::fabs(spread) + std::max(std::fabs(dx), std::fabs(dy)) + 2;
    CGRect  ink   = shadow.inset ? r : CGRectInset(r, -reach, -reach);
    lh::border_radiuses shape_radius = radius;
    CGRect              shape        = r;
    if(shadow.inset)
    {
        shape = CGRectInset(r, spread, spread);
        shape_radius -= Uniform(spread);
    } else
    {
        shape = CGRectInset(r, -spread, -spread);
        if(HasRadius(radius))
            shape_radius += Uniform(spread);
    }

    CGContextSaveGState(ctx);
    CGFloat far = CGRectGetWidth(ink) + reach * 4 + 1000;
    CGMutablePathRef path = CGPathCreateMutable();
    if(shadow.inset)
    {
        CGMutablePathRef clip = CGPathCreateMutable();
        AddRoundedRect(clip, r, radius);
        CGContextAddPath(ctx, clip);
        TWClipPath(ctx, false);
        CGPathRelease(clip);
        CGRect frame = CGRectInset(r, -reach * 2, -reach * 2);
        CGPathAddRect(path, nullptr, CGRectOffset(frame, far, 0));
        if(shape.size.width > 0 && shape.size.height > 0)
            AddRoundedRect(path, CGRectOffset(shape, far, 0), shape_radius);
    } else
    {
        CGMutablePathRef clip = CGPathCreateMutable();
        CGPathAddRect(clip, nullptr, CGRectInset(ink, -1, -1));
        AddRoundedRect(clip, r, radius);
        CGContextAddPath(ctx, clip);
        TWClipPath(ctx, true);
        CGPathRelease(clip);
        AddRoundedRect(path, CGRectOffset(shape, far, 0), shape_radius);
    }
    CGAffineTransform ctm    = shadow_transform(ctx);
    CGSize            offset = CGSizeApplyAffineTransform(CGSizeMake(dx - far, dy), ctm);
    CGFloat           scale  = std::sqrt(std::fabs(ctm.a * ctm.d - ctm.b * ctm.c));
    CGColorRef        color  = CreateColor(shadow.color);
    CGContextSetShadowWithColor(ctx, offset, blur * scale, color);
    CGColorRelease(color);
    CGContextSetRGBFillColor(ctx, 0, 0, 0, 1);
    CGContextAddPath(ctx, path);
    if(shadow.inset)
        CGContextEOFillPath(ctx);
    else
        CGContextFillPath(ctx);
    CGPathRelease(path);
    CGContextRestoreGState(ctx);
}

static CGPathRef CreateVectorPath(const lh::vector_path& path)
{
    CGMutablePathRef out = CGPathCreateMutable();
    const float*     p   = path.points.data();
    size_t           n   = path.points.size();
    size_t           i   = 0;
    bool             open = false;
    for(auto op : path.ops)
    {
        size_t need = op == lh::vector_path::cubic ? 6 : op == lh::vector_path::quad ? 4 : op == lh::vector_path::close ? 0 : 2;
        if(i + need > n)
            break;
        switch(op)
        {
        case lh::vector_path::move:
            CGPathMoveToPoint(out, nullptr, p[i], p[i + 1]);
            open = true;
            break;
        case lh::vector_path::line:
            if(open)
                CGPathAddLineToPoint(out, nullptr, p[i], p[i + 1]);
            break;
        case lh::vector_path::quad:
            if(open)
                CGPathAddQuadCurveToPoint(out, nullptr, p[i], p[i + 1], p[i + 2], p[i + 3]);
            break;
        case lh::vector_path::cubic:
            if(open)
                CGPathAddCurveToPoint(out, nullptr, p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5]);
            break;
        case lh::vector_path::close:
            if(open)
                CGPathCloseSubpath(out);
            break;
        }
        i += need;
    }
    return out;
}

void LHContainer::capture_shape(const lh::vector_path& path)
{
    if(!path.fill && !path.stroke)
        return;
    CGAffineTransform m = group_matrix();
    bool dashed = false;
    for(float v : path.dashes)
        dashed = dashed || v > 0;
    bool faded = false;
    for(const auto& g : m_groups)
        faded = faded || g.opacity < 1;
    if(dashed || faded || !m_clips.empty() || !m_effects.empty() || m_hidden_depth > 0 || std::fabs(m.b) > 1e-9 || std::fabs(m.c) > 1e-9 ||
       std::fabs(std::fabs(m.a) - std::fabs(m.d)) > 1e-6 || (path.fill && path.fill_gradient.type == 2) || (path.stroke && path.stroke_gradient.type != 0))
    {
        m_shapes_bad = true;
        return;
    }
    CGPathRef local = CreateVectorPath(path);
    if(!local)
        return;
    LHShapeData shape;
    shape.path    = CGPathCreateCopyByTransformingPath(local, &m);
    CGPathRelease(local);
    shape.evenOdd = path.evenodd;
    auto rgba = [](const lh::web_color& c, CGFloat out[4]) {
        out[0] = c.red / 255.0;
        out[1] = c.green / 255.0;
        out[2] = c.blue / 255.0;
        out[3] = c.alpha / 255.0;
    };
    if(path.fill)
    {
        shape.fill = true;
        rgba(path.fill_color, shape.fillColor);
        if(path.fill_gradient.type == 1)
        {
            const auto& g  = path.fill_gradient;
            shape.gradient = 1;
            shape.start    = CGPointApplyAffineTransform(CGPointMake(g.x1, g.y1), m);
            shape.end      = CGPointApplyAffineTransform(CGPointMake(g.x2, g.y2), m);
            for(const auto& stop : g.stops)
            {
                std::array<CGFloat, 4> color;
                rgba(stop.second, color.data());
                shape.stops.emplace_back(stop.first, color);
            }
        }
    }
    if(path.stroke && path.stroke_color.alpha > 0 && path.stroke_width > 0)
    {
        shape.stroke    = true;
        rgba(path.stroke_color, shape.strokeColor);
        shape.lineWidth = path.stroke_width * std::fabs(m.a);
        shape.cap       = path.line_cap;
        shape.join      = path.line_join;
        shape.miter     = path.miter_limit;
    }
    m_shapes->push_back(std::move(shape));
}

static void EvaluateSVGGradient(void* info, const CGFloat* input, CGFloat* output)
{
    const auto& stops = static_cast<const lh::svg_gradient*>(info)->stops;
    size_t index = 0;
    while(index + 1 < stops.size() && stops[index + 1].first <= input[0]) index++;
    const auto& left = stops[index];
    const auto& right = stops[std::min(index + 1, stops.size() - 1)];
    CGFloat span = right.first - left.first;
    CGFloat t = span > 0 ? std::clamp<CGFloat>((input[0] - left.first) / span, 0, 1) : 0;
    CGFloat a = left.second.alpha / 255.0 * (1 - t), b = right.second.alpha / 255.0 * t;
    CGFloat alpha = a + b;
    output[0] = alpha > 0 ? (left.second.red * a + right.second.red * b) / (255 * alpha) : 0;
    output[1] = alpha > 0 ? (left.second.green * a + right.second.green * b) / (255 * alpha) : 0;
    output[2] = alpha > 0 ? (left.second.blue * a + right.second.blue * b) / (255 * alpha) : 0;
    output[3] = alpha;
}

static void ReleaseSVGGradient(void* info)
{
    delete static_cast<lh::svg_gradient*>(info);
}

void LHContainer::draw_path(lh::uint_ptr hdc, const lh::vector_path& path)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_painter)
    {
        m_painter->path(path);
        return;
    }
    if(m_shapes)
    {
        capture_shape(path);
        return;
    }
    if(m_runs || (!path.fill && !path.stroke))
        return;
    CGPathRef cgpath = CreateVectorPath(path);
    CGRect    box    = CGPathGetPathBoundingBox(cgpath);
    if(CGRectIsNull(box))
    {
        CGPathRelease(cgpath);
        return;
    }
    CGFloat reach = path.stroke ? path.stroke_width * std::max<CGFloat>(path.miter_limit, 1) / 2 + 1 : 1;
    CGRect  ink   = CGRectInset(box, -reach, -reach);
    if(m_ops)
    {
        OpHash hash;
        hash.integer(10);
        for(auto op : path.ops)
            hash.integer(op);
        for(float v : path.points)
            hash.number(v);
        hash.integer(path.fill);
        hash.color(path.fill_color);
        hash.integer(path.evenodd);
        hash.integer(path.stroke);
        hash.color(path.stroke_color);
        hash.number(path.stroke_width);
        hash.integer(path.line_cap | path.line_join << 4);
        hash.number(path.miter_limit);
        for(float v : path.dashes)
            hash.number(v);
        hash.number(path.dash_offset);
        for(const auto* gradient : {&path.fill_gradient, &path.stroke_gradient})
        {
            const auto& g = *gradient;
            hash.integer(g.type);
            if(g.type == 0) continue;
            for(float v : {g.x1, g.y1, g.x2, g.y2, g.r, g.fx, g.fy})
                hash.number(v);
            for(const auto& stop : g.stops)
            {
                hash.number(stop.first);
                hash.color(stop.second);
            }
        }
        record_op(hash, ink);
        if(!m_capture)
        {
            CGPathRelease(cgpath);
            return;
        }
        hdc = reinterpret_cast<lh::uint_ptr>(m_capture);
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    if(!ctx)
    {
        CGPathRelease(cgpath);
        return;
    }
    CGContextSaveGState(ctx);
    apply_clips(ctx, ink);
    auto draw_gradient = [&](const lh::svg_gradient& g) {
        bool premultiplied = false;
        for(size_t i = 1; i < g.stops.size(); i++)
        {
            const auto& a = g.stops[i - 1].second;
            const auto& b = g.stops[i].second;
            premultiplied = premultiplied || (a.alpha != b.alpha && (a.red != b.red || a.green != b.green || a.blue != b.blue));
        }
        if(premultiplied)
        {
            const CGFloat domain[] = {0, 1}, range[] = {0, 1, 0, 1, 0, 1, 0, 1};
            const CGFunctionCallbacks callbacks = {0, EvaluateSVGGradient, ReleaseSVGGradient};
            auto info = std::make_unique<lh::svg_gradient>(g);
            CGFunctionRef function = CGFunctionCreate(info.get(), 1, domain, 4, range, &callbacks);
            if(!function) return;
            info.release();
            CGShadingRef shading = g.type == 1
                ? CGShadingCreateAxial(SRGB(), CGPointMake(g.x1, g.y1), CGPointMake(g.x2, g.y2), function, true, true)
                : CGShadingCreateRadial(SRGB(), CGPointMake(g.fx, g.fy), 0, CGPointMake(g.x1, g.y1), g.r, function, true, true);
            CGFunctionRelease(function);
            if(shading)
            {
                CGContextDrawShading(ctx, shading);
                CGShadingRelease(shading);
            }
            return;
        }
        std::vector<CGColorRef> colors;
        std::vector<CGFloat>    locations;
        for(const auto& stop : g.stops)
        {
            colors.push_back(CreateColor(stop.second));
            locations.push_back(stop.first);
        }
        CFArrayRef  array    = CFArrayCreate(nullptr, (const void**)colors.data(), colors.size(), &kCFTypeArrayCallBacks);
        CGGradientRef gradient = CGGradientCreateWithColors(SRGB(), array, locations.data());
        CFRelease(array);
        for(CGColorRef color : colors)
            CGColorRelease(color);
        if(gradient)
        {
            const CGGradientDrawingOptions options = kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation;
            if(g.type == 1)
            {
                if(!DrawAxisLinearGradient(ctx, gradient, CGPointMake(g.x1, g.y1), CGPointMake(g.x2, g.y2)))
                    CGContextDrawLinearGradient(ctx, gradient, CGPointMake(g.x1, g.y1), CGPointMake(g.x2, g.y2), options);
            }
            else
                CGContextDrawRadialGradient(ctx, gradient, CGPointMake(g.fx, g.fy), 0, CGPointMake(g.x1, g.y1), g.r, options);
            CGGradientRelease(gradient);
        }
    };
    if(path.fill && path.fill_gradient.type != 0)
    {
        CGContextSaveGState(ctx);
        CGContextAddPath(ctx, cgpath);
        if(path.evenodd)
            TWClipPath(ctx, true);
        else
            TWClipPath(ctx, false);
        draw_gradient(path.fill_gradient);
        CGContextRestoreGState(ctx);
    } else if(path.fill && path.fill_color.alpha > 0)
    {
        SetFill(ctx, path.fill_color);
        CGContextAddPath(ctx, cgpath);
        if(path.evenodd)
            CGContextEOFillPath(ctx);
        else
            CGContextFillPath(ctx);
    }
    if(path.stroke && (path.stroke_gradient.type != 0 || path.stroke_color.alpha > 0))
    {
        SetStroke(ctx, path.stroke_color);
        CGContextSetLineWidth(ctx, path.stroke_width);
        CGContextSetLineCap(ctx, path.line_cap == 1 ? kCGLineCapRound : path.line_cap == 2 ? kCGLineCapSquare : kCGLineCapButt);
        CGContextSetLineJoin(ctx, path.line_join == 1 ? kCGLineJoinRound : path.line_join == 2 ? kCGLineJoinBevel : kCGLineJoinMiter);
        CGContextSetMiterLimit(ctx, path.miter_limit);
        bool dashed = false;
        for(float v : path.dashes)
            dashed = dashed || v > 0;
        if(dashed)
        {
            std::vector<CGFloat> lengths(path.dashes.begin(), path.dashes.end());
            CGContextSetLineDash(ctx, path.dash_offset, lengths.data(), lengths.size());
        }
        CGContextAddPath(ctx, cgpath);
        if(path.stroke_gradient.type != 0)
        {
            CGContextReplacePathWithStrokedPath(ctx);
            TWClipPath(ctx, false);
            draw_gradient(path.stroke_gradient);
        } else
        {
            CGContextStrokePath(ctx);
        }
    }
    CGContextRestoreGState(ctx);
    CGPathRelease(cgpath);
}

void LHContainer::begin_group(lh::uint_ptr hdc, const lh::paint_group& group)
{
    flush_text();
    if(m_painter)
    {
        m_painter->beginGroup(group);
        return;
    }
    const auto&       m   = group.matrix;
    CGAffineTransform own = CGAffineTransformMake(m.a, m.b, m.c, m.d, m.tx, m.ty);
    Group             g;
    g.matrix  = CGAffineTransformConcat(own, group_matrix());
    g.opacity = group.opacity;
    g.hidden  = group.backface_hidden && (g.matrix.a * g.matrix.d - g.matrix.b * g.matrix.c) < 0;
    g.raster  = !m_ops && !m_runs && hdc;
    g.layer   = false;
    if(g.hidden)
        m_hidden_depth++;
    if(g.raster)
    {
        auto ctx = reinterpret_cast<CGContextRef>(hdc);
        CGContextSaveGState(ctx);
        if(g.hidden)
            CGContextClipToRect(ctx, CGRectZero);
        CGContextConcatCTM(ctx, own);
        if(group.opacity < 1 && !g.hidden && m_hidden_depth == 0)
        {
            CGContextSetAlpha(ctx, group.opacity);
            const CGFloat margin    = group.exact_bounds && s_exact_group_bounds.load(std::memory_order_relaxed) ? 2 : 64;
            CGRect        layerRect = CGRectIntersection(CGRectInset(R(group.bounds), -margin, -margin), CGContextGetClipBoundingBox(ctx));
            CGContextBeginTransparencyLayerWithRect(ctx, CGRectIsNull(layerRect) ? CGRectZero : layerRect, nullptr);
            g.layer = true;
        }
    }
    m_groups.push_back(g);
}

void LHContainer::end_group(lh::uint_ptr hdc)
{
    flush_text();
    if(m_painter)
    {
        m_painter->endGroup();
        return;
    }
    if(m_groups.empty())
        return;
    Group g = m_groups.back();
    m_groups.pop_back();
    if(g.raster)
    {
        auto ctx = reinterpret_cast<CGContextRef>(hdc);
        if(g.layer)
            CGContextEndTransparencyLayer(ctx);
        CGContextRestoreGState(ctx);
    }
    if(g.hidden)
        m_hidden_depth--;
}

static uint64_t FilterHash(const lh::filter_vector& steps, uint64_t seed)
{
    uint64_t hash = seed;
    auto     mix  = [&](double v) {
        uint64_t bits;
        memcpy(&bits, &v, sizeof(bits));
        hash = (hash ^ bits) * 0x100000001b3ULL;
        hash ^= hash >> 29;
    };
    for(const auto& fn : steps)
    {
        mix(fn.kind);
        mix(fn.amount);
        mix(fn.x.val());
        mix(fn.y.val());
        mix(fn.radius.val());
        mix(fn.color.red | (fn.color.green << 8) | (fn.color.blue << 16) | (fn.color.alpha << 24));
    }
    return hash;
}

struct FilterTarget
{
    CGRect  user;
    size_t  pw, ph;
    CGFloat sx, sy;
    bool    flipped;
    bool    reduced = false;
};

static void ReduceForBlur(FilterTarget& t, const lh::filter_vector& steps)
{
    CGFloat radius = 0;
    for(const auto& fn : steps)
    {
        if(fn.kind == lh::filter_function::blur)
            radius = std::max<CGFloat>(radius, fn.radius.val());
    }
    const CGFloat device = radius * std::max(t.sx, t.sy);
    const CGFloat limit  = 4;
    if(device <= limit)
        return;
    const CGFloat k = std::max<CGFloat>(limit / device, 1.0 / 32);
    t.sx *= k;
    t.sy *= k;
    t.pw      = (size_t)std::max<double>(1, std::ceil(t.user.size.width * t.sx));
    t.ph      = (size_t)std::max<double>(1, std::ceil(t.user.size.height * t.sy));
    t.reduced = true;
}

static bool FilterTargetFor(CGContextRef ctx, CGRect r, FilterTarget& out)
{
    CGAffineTransform m = CGContextGetCTM(ctx);
    if(std::fabs(m.b) > 1e-6 || std::fabs(m.c) > 1e-6 || r.size.width <= 0 || r.size.height <= 0)
        return false;
    CGRect device = CGRectIntegral(CGContextConvertRectToDeviceSpace(ctx, r));
    out.user      = CGContextConvertRectToUserSpace(ctx, device);
    out.pw        = (size_t)std::llround(device.size.width);
    out.ph        = (size_t)std::llround(device.size.height);
    out.sx        = std::fabs(m.a);
    out.sy        = std::fabs(m.d);
    out.flipped   = m.d < 0;
    return out.pw > 0 && out.ph > 0 && out.pw <= 8192 && out.ph <= 8192 && out.pw * out.ph <= (16u << 20);
}

static CGContextRef CreateFilterBitmap(const FilterTarget& t, CGColorSpaceRef space)
{
    CGContextRef bitmap = CGBitmapContextCreate(nullptr, t.pw, t.ph, 8, 0, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    if(!bitmap)
        return nullptr;
    if(t.flipped)
    {
        CGContextTranslateCTM(bitmap, 0, t.ph);
        CGContextScaleCTM(bitmap, t.sx, -t.sy);
    } else
        CGContextScaleCTM(bitmap, t.sx, t.sy);
    CGContextTranslateCTM(bitmap, -t.user.origin.x, -t.user.origin.y);
    CGContextSetAllowsFontSmoothing(bitmap, false);
    CGContextSetShouldAntialias(bitmap, true);
    return bitmap;
}

static void DrawFilterResult(CGContextRef ctx, const FilterTarget& t, CGImageRef image)
{
    CGContextSaveGState(ctx);
    CGContextSetInterpolationQuality(ctx, t.reduced ? kCGInterpolationMedium : kCGInterpolationNone);
    if(t.flipped)
    {
        CGContextTranslateCTM(ctx, t.user.origin.x, t.user.origin.y + t.user.size.height);
        CGContextScaleCTM(ctx, 1, -1);
    } else
        CGContextTranslateCTM(ctx, t.user.origin.x, t.user.origin.y);
    CGContextDrawImage(ctx, CGRectMake(0, 0, t.user.size.width, t.user.size.height), image);
    CGContextRestoreGState(ctx);
}

static CGColorSpaceRef FilterSpace(CGContextRef ctx)
{
    CGColorSpaceRef space = CGBitmapContextGetColorSpace(ctx);
    if(!space || CGColorSpaceGetModel(space) != kCGColorSpaceModelRGB)
        space = SRGB();
    return space;
}

void LHContainer::draw_filtered(lh::uint_ptr hdc, const lh::filter_paint& filter, const std::function<void(lh::uint_ptr)>& paint)
{
    if(lh::g_paint_trace)
    {
        paint(hdc);
        return;
    }
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter || m_runs || !filter.steps || filter.steps->empty())
    {
        paint(hdc);
        return;
    }
    if(m_ops)
    {
        if(m_gpu)
            m_gpu->unsupported = true;
        m_effects.push_back({lh::filter_reach(*filter.steps), FilterHash(*filter.steps, 0x51)});
        paint(hdc);
        m_effects.pop_back();
        return;
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    FilterTarget target;
    if(!ctx || m_hidden_depth > 0 || !FilterTargetFor(ctx, R(filter.bounds), target))
    {
        paint(hdc);
        return;
    }
    ReduceForBlur(target, *filter.steps);
    CGColorSpaceRef space  = FilterSpace(ctx);
    if(m_backdrop_source_depth > 0 && m_source_filter_scope && s_source_filter_cache.load(std::memory_order_relaxed))
    {
        const CGFloat need   = std::max<CGFloat>(std::max(target.sx, target.sy), 1.0 / 64);
        const CGFloat bucket = std::exp2(std::ceil(std::log2(need)));
        const CGRect  bounds = R(filter.bounds);
        std::string   key(1, 's');
        AppendInteger(key, reinterpret_cast<intptr_t>(filter.owner));
        AppendInteger(key, static_cast<int64_t>(FilterHash(*filter.steps, 0x54)));
        AppendInteger(key, reinterpret_cast<intptr_t>(space));
        AppendInteger(key, target.flipped);
        for(CGFloat v : {bucket, bounds.origin.x, bounds.origin.y, bounds.size.width, bounds.size.height})
            AppendNumber(key, v);
        auto found = m_source_filters.find(key);
        if(found == m_source_filters.end())
        {
            FilterTarget canonical;
            canonical.user    = bounds;
            canonical.sx      = bucket;
            canonical.sy      = bucket;
            canonical.pw      = (size_t)std::max<double>(1, std::ceil(bounds.size.width * bucket));
            canonical.ph      = (size_t)std::max<double>(1, std::ceil(bounds.size.height * bucket));
            canonical.flipped = target.flipped;
            canonical.reduced = true;
            CGImageRef filtered = nullptr;
            if(canonical.pw <= 8192 && canonical.ph <= 8192 && canonical.pw * canonical.ph <= (16u << 20))
            {
                if(CGContextRef local = CreateFilterBitmap(canonical, space))
                {
                    auto saved = m_clips;
                    m_clips.clear();
                    paint(reinterpret_cast<lh::uint_ptr>(local));
                    m_clips = saved;
                    CGImageRef source = CGBitmapContextCreateImage(local);
                    CGContextRelease(local);
                    filtered = LHCreateFilteredImage(source, *filter.steps, bucket, canonical.flipped, false, space);
                    CGImageRelease(source);
                }
            }
            found = m_source_filters.emplace(key, SourceFilter{filtered, canonical.user, bucket, canonical.flipped}).first;
        }
        else
            s_source_filter_hits.fetch_add(1, std::memory_order_relaxed);
        if(found->second.image)
        {
            FilterTarget cached;
            cached.user    = found->second.user;
            cached.sx      = found->second.scale;
            cached.sy      = found->second.scale;
            cached.pw      = CGImageGetWidth(found->second.image);
            cached.ph      = CGImageGetHeight(found->second.image);
            cached.flipped = found->second.flipped;
            cached.reduced = true;
            CGContextSaveGState(ctx);
            apply_clips(ctx, cached.user);
            DrawFilterResult(ctx, cached, found->second.image);
            CGContextRestoreGState(ctx);
            return;
        }
    }
    CGContextRef    bitmap = CreateFilterBitmap(target, space);
    if(!bitmap)
    {
        paint(hdc);
        return;
    }
    auto saved = m_clips;
    m_clips.clear();
    paint(reinterpret_cast<lh::uint_ptr>(bitmap));
    m_clips = saved;
    CGImageRef source = CGBitmapContextCreateImage(bitmap);
    CGContextRelease(bitmap);
    CGImageRef filtered = LHCreateFilteredImage(source, *filter.steps, std::max(target.sx, target.sy), target.flipped, false, space);
    CGImageRelease(source);
    if(filtered)
    {
        CGContextSaveGState(ctx);
        apply_clips(ctx, target.user);
        DrawFilterResult(ctx, target, filtered);
        CGContextRestoreGState(ctx);
        CGImageRelease(filtered);
    }
}

bool LHContainer::backdrop_allowed(const void* owner)
{
    std::lock_guard<std::mutex> guard(m_backdrop_lock);
    if(m_backdrop_owners.count(owner))
        return true;
    if(m_backdrop_owners.size() >= kMaxBackdrops)
        return false;
    m_backdrop_owners.insert(owner);
    return true;
}

void LHContainer::draw_backdrop(lh::uint_ptr hdc, const lh::filter_paint& filter)
{
    if(lh::g_paint_trace)
        return;
    flush_text();
    if(m_shapes)
    {
        m_shapes_bad = true;
        return;
    }
    if(m_painter || m_runs || !filter.steps || filter.steps->empty() || !backdrop_allowed(filter.owner))
        return;
    CGRect box = R(filter.box);
    if(m_ops)
    {
        if(m_gpu)
            m_gpu->unsupported = true;
        OpHash hash;
        hash.integer(91);
        hash.box(filter.box);
        hash.radius(filter.radius);
        hash.integer(static_cast<int64_t>(FilterHash(*filter.steps, 0x52)));
        record_op(hash, box, false);
        if(!m_ops->empty())
            m_ops->back().backdrop = true;
        return;
    }
    auto ctx = reinterpret_cast<CGContextRef>(hdc);
    FilterTarget target;
    if(!ctx || m_hidden_depth > 0 || !backdrop_source || !FilterTargetFor(ctx, box, target))
        return;
    ReduceForBlur(target, *filter.steps);
    const CGAffineTransform placed = group_matrix();
    if(std::fabs(placed.a - 1) > 1e-6 || std::fabs(placed.d - 1) > 1e-6 || std::fabs(placed.b) > 1e-6 || std::fabs(placed.c) > 1e-6)
        return;
    CGImageRef source = backdrop_source(filter.owner, CGRectOffset(target.user, placed.tx, placed.ty), target.sx, target.sy, target.flipped);
    if(!source)
        return;
    CGColorSpaceRef space    = FilterSpace(ctx);
    CGImageRef      filtered = LHCreateFilteredImage(source, *filter.steps, std::max(target.sx, target.sy), target.flipped, true, space);
    CGImageRelease(source);
    if(!filtered)
        return;
    CGContextSaveGState(ctx);
    apply_clips(ctx, box);
    CGMutablePathRef clip = CGPathCreateMutable();
    AddRoundedRect(clip, box, filter.radius);
    CGContextAddPath(ctx, clip);
    TWClipPath(ctx, false);
    CGPathRelease(clip);
    CGContextSetBlendMode(ctx, kCGBlendModeCopy);
    DrawFilterResult(ctx, target, filtered);
    CGContextRestoreGState(ctx);
    CGImageRelease(filtered);
}

void LHContainer::on_anchor_click(const char* url, const lh::element::ptr& el)
{
    if(on_element_click(el))
        return;
    if(on_anchor && url)
        on_anchor(url);
}

bool LHContainer::on_element_click(const lh::element::ptr& el)
{
    const char* action = el && on_action ? el->get_attr("data-action") : nullptr;
    if(!action)
        return false;
    on_action(action, el->get_attr("data-value", ""));
    return true;
}

void LHContainer::set_cursor(const char* cursor)
{
    if(on_cursor)
        on_cursor(cursor ? cursor : "auto");
}

void LHContainer::get_viewport(lh::position& viewport) const
{
    viewport = m_viewport;
}

void LHContainer::get_media_features(lh::media_features& media) const
{
    media.type          = lh::media_type_screen;
    media.width         = m_viewport.width;
    media.height        = m_viewport.height;
    media.device_width  = 1440;
    media.device_height = 900;
    media.color         = 8;
    media.monochrome    = 0;
    media.color_index   = 256;
    media.resolution    = 96;
}

void LHContainer::get_language(std::string& language, std::string& culture) const
{
    language = "en";
    culture  = "";
}
