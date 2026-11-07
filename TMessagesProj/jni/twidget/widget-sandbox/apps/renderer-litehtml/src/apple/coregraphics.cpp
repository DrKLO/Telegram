#include "coregraphics.h"

#include <ImageIO/ImageIO.h>

#include <algorithm>
#include <cmath>
#include <vector>

namespace twlite {

namespace {

struct CFHolder {
    CFTypeRef ref;
    explicit CFHolder(CFTypeRef r) : ref(r) {}
    ~CFHolder() { if (ref) CFRelease(ref); }
    CFHolder(const CFHolder&) = delete;
    CFHolder& operator=(const CFHolder&) = delete;
};

CFStringRef cfstr(const std::string& s) {
    return CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)s.data(), CFIndex(s.size()), kCFStringEncodingUTF8, false);
}

std::string lower(std::string s) {
    for (auto& c : s) c = char(std::tolower((unsigned char)c));
    return s;
}

std::string trim(std::string s) {
    auto ws = [](char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '"' || c == '\''; };
    while (!s.empty() && ws(s.front())) s.erase(s.begin());
    while (!s.empty() && ws(s.back())) s.pop_back();
    return s;
}

/// CSS weight → CoreText weight trait (-1…1; 400 = 0).
float ct_weight(int w) {
    static const std::pair<int, float> table[] = {{100, -0.8f}, {200, -0.6f}, {300, -0.4f}, {400, 0.0f}, {500, 0.23f}, {600, 0.3f}, {700, 0.4f}, {800, 0.56f}, {900, 0.62f}};
    float best = 0;
    int dist = 1000;
    for (auto [k, v] : table) if (std::abs(k - w) < dist) { dist = std::abs(k - w); best = v; }
    return best;
}

CTFontDescriptorRef family_descriptor(const std::string& family) {
    CFHolder name(cfstr(family));
    const void* keys[] = {kCTFontFamilyNameAttribute};
    const void* values[] = {name.ref};
    CFHolder attrs(CFDictionaryCreate(kCFAllocatorDefault, keys, values, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));
    CFHolder desc(CTFontDescriptorCreateWithAttributes((CFDictionaryRef)attrs.ref));
    // nullptr when no installed family has this name (CTFontCreateWithName would silently fall back)
    return CTFontDescriptorCreateMatchingFontDescriptor((CTFontDescriptorRef)desc.ref, nullptr);
}

std::vector<unsigned char> base64_decode(const std::string& in) {
    static int T[256];
    static bool init = false;
    if (!init) {
        for (int& t : T) t = -1;
        const char* a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < 64; i++) T[(unsigned char)a[i]] = i;
        T[(unsigned char)'-'] = 62;
        T[(unsigned char)'_'] = 63;
        init = true;
    }
    std::vector<unsigned char> out;
    out.reserve(in.size() * 3 / 4);
    int val = 0, bits = -8;
    for (unsigned char c : in) {
        if (T[c] == -1) continue;
        val = (val << 6) + T[c];
        bits += 6;
        if (bits >= 0) {
            out.push_back((unsigned char)((val >> bits) & 0xFF));
            bits -= 8;
        }
    }
    return out;
}

/// Percent-decoding for non-base64 data: URLs.
std::string percent_decode(const std::string& in) {
    std::string out;
    for (size_t i = 0; i < in.size(); i++) {
        if (in[i] == '%' && i + 2 < in.size()) {
            out.push_back(char(std::strtol(in.substr(i + 1, 2).c_str(), nullptr, 16)));
            i += 2;
        } else out.push_back(in[i]);
    }
    return out;
}

CGPathRef rounded_path(const Rect& r, const Radii& q) {
    CGMutablePathRef p = CGPathCreateMutable();
    if (!q.any()) {
        CGPathAddRect(p, nullptr, CGRectMake(r.x, r.y, r.w, r.h));
        return p;
    }
    // circular corners of the smaller radius (CGPathAddArcToPoint has no ellipses), clamped to the box
    auto rad = [&](float a, float b) { return std::min({a, b, r.w / 2, r.h / 2}); };
    float tl = rad(q.tlx, q.tly), tr = rad(q.trx, q.try_), br = rad(q.brx, q.bry), bl = rad(q.blx, q.bly);
    float x0 = r.x, y0 = r.y, x1 = r.x + r.w, y1 = r.y + r.h;
    CGPathMoveToPoint(p, nullptr, x0 + tl, y0);
    CGPathAddLineToPoint(p, nullptr, x1 - tr, y0);
    if (tr > 0) CGPathAddArcToPoint(p, nullptr, x1, y0, x1, y0 + tr, tr);
    CGPathAddLineToPoint(p, nullptr, x1, y1 - br);
    if (br > 0) CGPathAddArcToPoint(p, nullptr, x1, y1, x1 - br, y1, br);
    CGPathAddLineToPoint(p, nullptr, x0 + bl, y1);
    if (bl > 0) CGPathAddArcToPoint(p, nullptr, x0, y1, x0, y1 - bl, bl);
    CGPathAddLineToPoint(p, nullptr, x0, y0 + tl);
    if (tl > 0) CGPathAddArcToPoint(p, nullptr, x0, y0, x0 + tl, y0, tl);
    CGPathCloseSubpath(p);
    return p;
}

} // namespace

CGBackend::CGBackend() : rgb_(CGColorSpaceCreateDeviceRGB()) {}

CGBackend::~CGBackend() {
    for (auto& [_, f] : fonts_) if (f.font) CFRelease(f.font);
    for (auto& [_, i] : images_) if (i) CGImageRelease(i);
    CGColorSpaceRelease(rgb_);
}

int CGBackend::create_font(const std::string& families, float size, int weight, bool italic, FontMetrics& out) {
    CTFontRef base = nullptr;
    std::string list = families;
    size_t start = 0;
    while (!base && start <= list.size()) {
        size_t comma = list.find(',', start);
        std::string fam = trim(list.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
        start = comma == std::string::npos ? list.size() + 1 : comma + 1;
        if (fam.empty()) continue;
        std::string l = lower(fam);
        const char* named = nullptr;
        if (l == "-apple-system" || l == "blinkmacsystemfont" || l == "system-ui" || l == "ui-sans-serif" || l == "sans-serif" || l == "segoe ui" || l == "roboto" || l == "inter") {
            base = CTFontCreateUIFontForLanguage(kCTFontUIFontSystem, size, nullptr);
        } else if (l == "serif" || l == "ui-serif") named = "Times New Roman";
        else if (l == "monospace" || l == "ui-monospace") named = "Menlo";
        else if (l == "cursive") named = "Snell Roundhand";
        else if (l == "fantasy") named = "Papyrus";
        else if (l == "emoji") named = "Apple Color Emoji";
        if (!base) {
            CTFontDescriptorRef d = family_descriptor(named ? named : fam);
            if (d) {
                base = CTFontCreateWithFontDescriptor(d, size, nullptr);
                CFRelease(d);
            }
        }
    }
    if (!base) base = CTFontCreateUIFontForLanguage(kCTFontUIFontSystem, size, nullptr);
    if (!base) return 0;
    // weight through the traits attribute (variable system font and family faces alike), italic as a symbolic trait
    CTFontRef font = base;
    if (weight != 400) {
        CFHolder weightNum(CFNumberCreate(kCFAllocatorDefault, kCFNumberFloatType, &(const float&)ct_weight(weight)));
        const void* tkeys[] = {kCTFontWeightTrait};
        const void* tvals[] = {weightNum.ref};
        CFHolder traits(CFDictionaryCreate(kCFAllocatorDefault, tkeys, tvals, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));
        const void* akeys[] = {kCTFontTraitsAttribute};
        const void* avals[] = {traits.ref};
        CFHolder attrs(CFDictionaryCreate(kCFAllocatorDefault, akeys, avals, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));
        CFHolder desc(CTFontDescriptorCreateWithAttributes((CFDictionaryRef)attrs.ref));
        if (CTFontRef heavier = CTFontCreateCopyWithAttributes(base, size, nullptr, (CTFontDescriptorRef)desc.ref)) {
            CFRelease(font);
            font = heavier;
        }
    }
    if (italic) {
        if (CTFontRef it = CTFontCreateCopyWithSymbolicTraits(font, size, nullptr, kCTFontTraitItalic, kCTFontTraitItalic)) {
            CFRelease(font);
            font = it;
        }
    }
    Font f;
    f.font = font;
    f.metrics.ascent = float(CTFontGetAscent(font));
    f.metrics.descent = float(CTFontGetDescent(font));
    f.metrics.height = f.metrics.ascent + f.metrics.descent + float(CTFontGetLeading(font));
    f.metrics.x_height = float(CTFontGetXHeight(font));
    int id = ++next_font_;
    fonts_[id] = f;
    f.metrics.zero_width = text_width(id, "0");
    fonts_[id].metrics = f.metrics;
    out = f.metrics;
    return id;
}

void CGBackend::delete_font(int font) {
    auto it = fonts_.find(font);
    if (it == fonts_.end()) return;
    if (it->second.font) CFRelease(it->second.font);
    fonts_.erase(it);
}

CTLineRef CGBackend::make_line(const Font& f, const std::string& text, CGColorRef color) {
    CFHolder str(cfstr(text));
    if (!str.ref) return nullptr;
    const void* keys[] = {kCTFontAttributeName, kCTForegroundColorAttributeName};
    const void* values[] = {f.font, color};
    CFHolder attrs(CFDictionaryCreate(kCFAllocatorDefault, keys, values, color ? 2 : 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));
    CFHolder attributed(CFAttributedStringCreate(kCFAllocatorDefault, (CFStringRef)str.ref, (CFDictionaryRef)attrs.ref));
    return CTLineCreateWithAttributedString((CFAttributedStringRef)attributed.ref);
}

float CGBackend::text_width(int font, const std::string& text) {
    auto it = fonts_.find(font);
    if (it == fonts_.end()) return 0;
    CTLineRef line = make_line(it->second, text, nullptr);
    if (!line) return 0;
    double w = CTLineGetTypographicBounds(line, nullptr, nullptr, nullptr);
    CFRelease(line);
    return float(w);
}

bool CGBackend::load_image(int id, const std::string& url, float& w, float& h) {
    // data:[<mime>][;base64],<payload>
    size_t comma = url.find(',');
    if (url.rfind("data:", 0) != 0 || comma == std::string::npos) return false;
    std::string meta = url.substr(5, comma - 5);
    std::string payload = url.substr(comma + 1);
    if (meta.find("svg") != std::string::npos) return false;  // no SVG rasteriser here (litehtml has none either)
    std::vector<unsigned char> bytes;
    if (meta.find(";base64") != std::string::npos) bytes = base64_decode(payload);
    else { std::string s = percent_decode(payload); bytes.assign(s.begin(), s.end()); }
    if (bytes.empty()) return false;
    CFHolder data(CFDataCreate(kCFAllocatorDefault, bytes.data(), CFIndex(bytes.size())));
    CFHolder source(CGImageSourceCreateWithData((CFDataRef)data.ref, nullptr));
    if (!source.ref) return false;
    CGImageRef img = CGImageSourceCreateImageAtIndex((CGImageSourceRef)source.ref, 0, nullptr);
    if (!img) return false;
    auto old = images_.find(id);
    if (old != images_.end() && old->second) CGImageRelease(old->second);
    images_[id] = img;
    w = float(CGImageGetWidth(img));
    h = float(CGImageGetHeight(img));
    return true;
}

CGColorRef CGBackend::cg_color(const Color& c) {
    CGFloat comps[] = {c.r / 255.0, c.g / 255.0, c.b / 255.0, c.a / 255.0};
    return CGColorCreate(rgb_, comps);
}

void CGBackend::paint(const DisplayList& list, uint8_t* buffer, uint32_t w, uint32_t h, double dpr) {
    if (!buffer || w == 0 || h == 0) return;
    std::fill(buffer, buffer + size_t(w) * h * 4, 0);
    CGContextRef ctx = CGBitmapContextCreate(buffer, w, h, 8, size_t(w) * 4, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    if (!ctx) return;
    // CSS px, y down
    CGContextTranslateCTM(ctx, 0, h);
    CGContextScaleCTM(ctx, dpr, -dpr);
    CGContextSetTextMatrix(ctx, CGAffineTransformMakeScale(1, -1));
    CGContextSetShouldAntialias(ctx, true);
    CGContextSetShouldSmoothFonts(ctx, false);
    CGContextSetInterpolationQuality(ctx, kCGInterpolationHigh);

    int clip_depth = 0;
    auto clip_to = [&](const Rect& r, const Radii& q) {
        CGPathRef p = rounded_path(r, q);
        CGContextAddPath(ctx, p);
        CGContextClip(ctx);
        CGPathRelease(p);
    };
    auto gradient = [&](const std::vector<Stop>& stops) -> CGGradientRef {
        if (stops.empty()) return nullptr;
        std::vector<CGFloat> comps, locs;
        for (const Stop& s : stops) {
            comps.insert(comps.end(), {s.color.r / 255.0, s.color.g / 255.0, s.color.b / 255.0, s.color.a / 255.0});
            locs.push_back(std::clamp(s.offset, 0.0f, 1.0f));
        }
        return CGGradientCreateWithColorComponents(rgb_, comps.data(), locs.data(), stops.size());
    };

    for (const Command& c : list) {
        switch (c.kind) {
        case Cmd::ClipPush:
            CGContextSaveGState(ctx);
            clip_depth++;
            clip_to(c.rect, c.radii);
            break;
        case Cmd::ClipPop:
            if (clip_depth > 0) {
                CGContextRestoreGState(ctx);
                clip_depth--;
            }
            break;
        case Cmd::FillRect: {
            CGContextSaveGState(ctx);
            if (c.has_clip) CGContextClipToRect(ctx, CGRectMake(c.clip.x, c.clip.y, c.clip.w, c.clip.h));
            CGColorRef col = cg_color(c.color);
            CGContextSetFillColorWithColor(ctx, col);
            CGPathRef p = rounded_path(c.rect, c.radii);
            CGContextAddPath(ctx, p);
            CGContextFillPath(ctx);
            CGPathRelease(p);
            CGColorRelease(col);
            CGContextRestoreGState(ctx);
            break;
        }
        case Cmd::StrokeRect: {
            CGColorRef col = cg_color(c.color);
            CGContextSetStrokeColorWithColor(ctx, col);
            CGContextSetLineWidth(ctx, c.width);
            CGPathRef p = rounded_path(c.rect, c.radii);
            CGContextAddPath(ctx, p);
            CGContextStrokePath(ctx);
            CGPathRelease(p);
            CGColorRelease(col);
            break;
        }
        case Cmd::FillEllipse:
        case Cmd::StrokeEllipse: {
            CGColorRef col = cg_color(c.color);
            CGRect r = CGRectMake(c.rect.x, c.rect.y, c.rect.w, c.rect.h);
            if (c.kind == Cmd::FillEllipse) {
                CGContextSetFillColorWithColor(ctx, col);
                CGContextFillEllipseInRect(ctx, r);
            } else {
                CGContextSetStrokeColorWithColor(ctx, col);
                CGContextSetLineWidth(ctx, c.width);
                CGContextStrokeEllipseInRect(ctx, CGRectInset(r, c.width / 2, c.width / 2));
            }
            CGColorRelease(col);
            break;
        }
        case Cmd::Text: {
            auto it = fonts_.find(c.font);
            if (it == fonts_.end()) break;
            CGColorRef col = cg_color(c.color);
            CTLineRef line = make_line(it->second, c.text, col);
            if (line) {
                CGContextSetTextPosition(ctx, c.x, c.y);
                CTLineDraw(line, ctx);
                CFRelease(line);
            }
            CGColorRelease(col);
            break;
        }
        case Cmd::Image: {
            auto it = images_.find(c.image);
            if (it == images_.end() || !it->second || c.rect.empty()) break;
            CGContextSaveGState(ctx);
            if (c.has_clip) CGContextClipToRect(ctx, CGRectMake(c.clip.x, c.clip.y, c.clip.w, c.clip.h));
            if (c.radii.any()) {
                // the border box with its radii is the clip the layer asks for; the clip rect above bounds it
                Rect box = c.has_clip ? c.clip : c.rect;
                clip_to(box, c.radii);
            }
            // CGContextDrawImage draws y-up: flip around the destination
            CGContextTranslateCTM(ctx, 0, c.rect.y + c.rect.h);
            CGContextScaleCTM(ctx, 1, -1);
            CGContextDrawImage(ctx, CGRectMake(c.rect.x, 0, c.rect.w, c.rect.h), it->second);
            CGContextRestoreGState(ctx);
            break;
        }
        case Cmd::Linear:
        case Cmd::Radial:
        case Cmd::Conic: {
            CGGradientRef g = gradient(c.stops);
            if (!g) break;
            CGContextSaveGState(ctx);
            if (c.has_clip) CGContextClipToRect(ctx, CGRectMake(c.clip.x, c.clip.y, c.clip.w, c.clip.h));
            clip_to(c.rect, c.radii);
            if (c.kind == Cmd::Linear) {
                CGContextDrawLinearGradient(ctx, g, CGPointMake(c.ax, c.ay), CGPointMake(c.bx, c.by), kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation);
            } else if (c.kind == Cmd::Radial) {
                float rx = std::max(c.bx, 0.01f), ry = std::max(c.by, 0.01f);
                CGContextTranslateCTM(ctx, c.ax, c.ay);
                CGContextScaleCTM(ctx, 1, ry / rx);
                CGContextDrawRadialGradient(ctx, g, CGPointZero, 0, CGPointZero, rx, kCGGradientDrawsAfterEndLocation);
            } else {
                // CSS: degrees clockwise from the top; CG (in this flipped CTM): radians clockwise from +x
                CGContextDrawConicGradient(ctx, g, CGPointMake(c.ax, c.ay), CGFloat((c.bx - 90.0) * M_PI / 180.0));
            }
            CGContextRestoreGState(ctx);
            CGGradientRelease(g);
            break;
        }
        }
    }
    while (clip_depth-- > 0) CGContextRestoreGState(ctx);
    CGContextFlush(ctx);
    CGContextRelease(ctx);
}

} // namespace twlite
