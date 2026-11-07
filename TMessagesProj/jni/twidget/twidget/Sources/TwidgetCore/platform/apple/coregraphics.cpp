#include "coregraphics.h"
#include "filters.h"
#include "png.h"

#include <ImageIO/ImageIO.h>

#include <algorithm>
#include <iterator>
#include <cmath>
#include <cstdlib>
#include <vector>

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

CGPathRef vector_path(const Path& path) {
    CGMutablePathRef p = CGPathCreateMutable();
    const float* v = path.points.data();
    size_t n = path.points.size(), i = 0;
    for (auto op : path.ops) {
        size_t need = op == Path::Cubic ? 6 : op == Path::Quad ? 4 : op == Path::Close ? 0 : 2;
        if (i + need > n) break;
        switch (op) {
        case Path::Move: CGPathMoveToPoint(p, nullptr, v[i], v[i + 1]); break;
        case Path::Line: CGPathAddLineToPoint(p, nullptr, v[i], v[i + 1]); break;
        case Path::Quad: CGPathAddQuadCurveToPoint(p, nullptr, v[i], v[i + 1], v[i + 2], v[i + 3]); break;
        case Path::Cubic: CGPathAddCurveToPoint(p, nullptr, v[i], v[i + 1], v[i + 2], v[i + 3], v[i + 4], v[i + 5]); break;
        case Path::Close: CGPathCloseSubpath(p); break;
        }
        i += need;
    }
    return p;
}

} // namespace

CGBackend::CGBackend() : rgb_(CGColorSpaceCreateDeviceRGB()) {}

CGBackend::~CGBackend() {
    trim();
    for (auto& [k, l] : lines_) CFRelease(l);
    for (auto& [k, g] : gradients_) CGGradientRelease(g);
    for (auto& [_, f] : fonts_) if (f.font) CFRelease(f.font);
    for (auto& [_, i] : images_) if (i) CGImageRelease(i);
    for (auto& [_, c] : canvases_) if (c.ctx) CGContextRelease(c.ctx);
    CGColorSpaceRelease(rgb_);
}

int CGBackend::create_font(const std::string& families, float size, int weight, bool italic, const std::string& features, FontMetrics& out) {
    CTFontRef base = nullptr;
    std::string list = families;
    size_t start = 0;
    while (!base && start <= list.size()) {
        size_t comma = list.find(',', start);
        std::string fam = twlite::trim(list.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
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
    if (!features.empty()) {
        CFHolder settings(CFArrayCreateMutable(kCFAllocatorDefault, 0, &kCFTypeArrayCallBacks));
        for (size_t start = 0; start < features.size();) {
            size_t end = std::min(features.find(',', start), features.size());
            std::string entry = features.substr(start, end - start);
            start = end + 1;
            if (entry.size() < 6 || entry[4] != '=') continue;
            int value = std::atoi(entry.c_str() + 5);
            CFHolder tag(cfstr(entry.substr(0, 4)));
            CFHolder number(CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &value));
            const void* keys[] = {kCTFontOpenTypeFeatureTag, kCTFontOpenTypeFeatureValue};
            const void* vals[] = {tag.ref, number.ref};
            CFHolder setting(CFDictionaryCreate(kCFAllocatorDefault, keys, vals, 2, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));
            CFArrayAppendValue((CFMutableArrayRef)settings.ref, setting.ref);
        }
        const void* akeys[] = {kCTFontFeatureSettingsAttribute};
        const void* avals[] = {settings.ref};
        CFHolder attrs(CFDictionaryCreate(kCFAllocatorDefault, akeys, avals, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));
        CFHolder desc(CTFontDescriptorCreateWithAttributes((CFDictionaryRef)attrs.ref));
        if (CTFontRef featured = CTFontCreateCopyWithAttributes(font, size, nullptr, (CTFontDescriptorRef)desc.ref)) {
            CFRelease(font);
            font = featured;
        }
    }
    Font f;
    f.font = font;
    f.metrics.ascent = float(CTFontGetAscent(font));
    f.metrics.descent = float(CTFontGetDescent(font));
    f.metrics.height = f.metrics.ascent + f.metrics.descent + float(CTFontGetLeading(font));
    f.metrics.x_height = float(CTFontGetXHeight(font));
    // sTypoAscender and sTypoDescender, big-endian at 68 and 70 in the OS/2 table.
    if (CFDataRef os2 = CTFontCopyTable(font, kCTFontTableOS2, kCTFontTableOptionNoOptions)) {
        if (CFDataGetLength(os2) >= 72) {
            const uint8_t* b = CFDataGetBytePtr(os2);
            set_em_box(f.metrics, float(int16_t(b[68] << 8 | b[69])), -float(int16_t(b[70] << 8 | b[71])), size);
        }
        CFRelease(os2);
    }
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

CTLineRef CGBackend::line(int font, const Font& f, const std::string& text, const Color* color) {
    // A widget draws the same words in the same colours every frame: a typeset line is kept per font,
    // colour and text, and handed out retained, as make_line's are.
    std::string key = std::to_string(font);
    key.push_back('\x1f');
    if (color) key.append(reinterpret_cast<const char*>(color), sizeof(Color));
    key.push_back('\x1f');
    key += text;
    if (auto it = lines_.find(key); it != lines_.end()) return (CTLineRef)CFRetain(it->second);
    if (lines_.size() >= MAX_LINES) {
        for (auto& [k, l] : lines_) CFRelease(l);
        lines_.clear();
    }
    CGColorRef col = color ? cg_color(*color) : nullptr;
    CTLineRef made = make_line(f, text, col);
    if (col) CGColorRelease(col);
    if (!made) return nullptr;
    lines_.emplace(std::move(key), made);
    return (CTLineRef)CFRetain(made);
}

float CGBackend::text_width(int font, const std::string& text) {
    auto it = fonts_.find(font);
    if (it == fonts_.end()) return 0;
    CTLineRef line = this->line(font, it->second, text, nullptr);
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
    // ImageIO carries on through a broken stream; Chromium leaves what follows the damage transparent.
    size_t intact = png_intact_rows(bytes.data(), bytes.size());
    size_t iw = CGImageGetWidth(img), ih = CGImageGetHeight(img);
    if (intact < ih) {
        if (CGContextRef ctx = CGBitmapContextCreate(nullptr, iw, ih, 8, 0, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big)) {
            // The context's origin is its bottom left: the kept rows are the top ones.
            CGContextClipToRect(ctx, CGRectMake(0, CGFloat(ih - intact), CGFloat(iw), CGFloat(intact)));
            CGContextDrawImage(ctx, CGRectMake(0, 0, CGFloat(iw), CGFloat(ih)), img);
            if (CGImageRef kept = CGBitmapContextCreateImage(ctx)) {
                CGImageRelease(img);
                img = kept;
            }
            CGContextRelease(ctx);
        }
    }
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

CGGradientRef CGBackend::gradient(const std::vector<Stop>& stops) {
    if (stops.empty()) return nullptr;
    // Compiled once per set of stops, handed out retained.
    std::string key(reinterpret_cast<const char*>(stops.data()), stops.size() * sizeof(Stop));
    if (auto it = gradients_.find(key); it != gradients_.end()) return CGGradientRetain(it->second);
    if (gradients_.size() >= MAX_GRADIENTS) {
        for (auto& [k, g] : gradients_) CGGradientRelease(g);
        gradients_.clear();
    }
    std::vector<CGFloat> comps, locs;
    for (const Stop& s : stops) {
        comps.insert(comps.end(), {s.color.r / 255.0, s.color.g / 255.0, s.color.b / 255.0, s.color.a / 255.0});
        locs.push_back(std::clamp(s.offset, 0.0f, 1.0f));
    }
    CGGradientRef made = CGGradientCreateWithColorComponents(rgb_, comps.data(), locs.data(), stops.size());
    if (!made) return nullptr;
    gradients_.emplace(std::move(key), made);
    return CGGradientRetain(made);
}

namespace {

void set_line_style(CGContextRef ctx, float width, int cap, int join, float miter, const std::vector<float>& dashes, float offset) {
    CGContextSetLineWidth(ctx, width);
    CGContextSetLineCap(ctx, cap == 1 ? kCGLineCapRound : cap == 2 ? kCGLineCapSquare : kCGLineCapButt);
    CGContextSetLineJoin(ctx, join == 1 ? kCGLineJoinRound : join == 2 ? kCGLineJoinBevel : kCGLineJoinMiter);
    CGContextSetMiterLimit(ctx, miter);
    bool dashed = false;
    for (float d : dashes) dashed = dashed || d > 0;
    if (dashed) {
        std::vector<CGFloat> lengths(dashes.begin(), dashes.end());
        CGContextSetLineDash(ctx, offset, lengths.data(), lengths.size());
    } else {
        CGContextSetLineDash(ctx, 0, nullptr, 0);
    }
}

CGBlendMode blend_mode(Composite c) {
    static const CGBlendMode modes[] = {
        kCGBlendModeNormal, kCGBlendModeSourceIn, kCGBlendModeSourceOut, kCGBlendModeSourceAtop,
        kCGBlendModeDestinationOver, kCGBlendModeDestinationIn, kCGBlendModeDestinationOut, kCGBlendModeDestinationAtop,
        kCGBlendModePlusLighter, kCGBlendModeCopy, kCGBlendModeXOR, kCGBlendModeMultiply, kCGBlendModeScreen,
        kCGBlendModeOverlay, kCGBlendModeDarken, kCGBlendModeLighten, kCGBlendModeColorDodge, kCGBlendModeColorBurn,
        kCGBlendModeHardLight, kCGBlendModeSoftLight, kCGBlendModeDifference, kCGBlendModeExclusion, kCGBlendModeHue,
        kCGBlendModeSaturation, kCGBlendModeColor, kCGBlendModeLuminosity,
    };
    size_t i = size_t(c);
    return i < std::size(modes) ? modes[i] : kCGBlendModeNormal;
}

} // namespace

void CGBackend::draw_path(CGContextRef ctx, const Path& p) {
    CGPathRef shape = vector_path(p);
    if (p.fill && p.gradient != 0 && !p.stops.empty()) {
        if (CGGradientRef g = gradient(p.stops)) {
            CGContextSaveGState(ctx);
            CGContextAddPath(ctx, shape);
            if (p.evenodd) TWClipPath(ctx, true);
            else TWClipPath(ctx, false);
            const auto extend = kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation;
            if (p.gradient == 1) CGContextDrawLinearGradient(ctx, g, CGPointMake(p.x1, p.y1), CGPointMake(p.x2, p.y2), extend);
            else CGContextDrawRadialGradient(ctx, g, CGPointMake(p.fx, p.fy), 0, CGPointMake(p.x1, p.y1), p.r, extend);
            CGContextRestoreGState(ctx);
            CGGradientRelease(g);
        }
    } else if (p.fill && p.fill_color.a > 0) {
        CGColorRef col = cg_color(p.fill_color);
        CGContextSetFillColorWithColor(ctx, col);
        CGContextAddPath(ctx, shape);
        if (p.evenodd) CGContextEOFillPath(ctx);
        else CGContextFillPath(ctx);
        CGColorRelease(col);
    }
    if (p.stroke && p.stroke_color.a > 0 && p.stroke_width > 0) {
        CGContextSaveGState(ctx);
        set_line_style(ctx, p.stroke_width, p.line_cap, p.line_join, p.miter_limit, p.dashes, p.dash_offset);
        CGColorRef col = cg_color(p.stroke_color);
        CGContextSetStrokeColorWithColor(ctx, col);
        CGContextAddPath(ctx, shape);
        CGContextStrokePath(ctx);
        CGColorRelease(col);
        CGContextRestoreGState(ctx);
    }
    CGPathRelease(shape);
}

// CSS box-shadow as LHContainer::paint_box_shadow draws it: the shape is filled far outside the clip and
// only its shadow, offset back, lands next to the box.
void CGBackend::draw_shadow(CGContextRef ctx, const Command& c) {
    if (c.rect.empty() || c.color.a == 0) return;
    float dx = c.ax, dy = c.ay, blur = std::max(c.bx, 0.0f), spread = c.by;
    float reach = blur + std::fabs(spread) + std::max(std::fabs(dx), std::fabs(dy)) + 2;
    CGRect r = CGRectMake(c.rect.x, c.rect.y, c.rect.w, c.rect.h);
    CGRect ink = c.inset ? r : CGRectInset(r, -reach, -reach);
    auto grow = [](const Radii& q, float by) {
        auto g = [by](float v) { return std::max(v + by, 0.0f); };
        return Radii{g(q.tlx), g(q.tly), g(q.trx), g(q.try_), g(q.brx), g(q.bry), g(q.blx), g(q.bly)};
    };
    Rect shape = c.inset ? Rect{c.rect.x + spread, c.rect.y + spread, c.rect.w - spread * 2, c.rect.h - spread * 2}
                         : Rect{c.rect.x - spread, c.rect.y - spread, c.rect.w + spread * 2, c.rect.h + spread * 2};
    Radii shape_radii = c.inset ? grow(c.radii, -spread) : c.radii.any() ? grow(c.radii, spread) : c.radii;

    CGContextSaveGState(ctx);
    float far = float(CGRectGetWidth(ink)) + reach * 4 + 1000;
    CGMutablePathRef path = CGPathCreateMutable();
    CGMutablePathRef clip = CGPathCreateMutable();
    CGPathRef box = rounded_path(c.rect, c.radii);
    if (c.inset) {
        CGContextAddPath(ctx, box);
        TWClipPath(ctx, false);
        CGRect frame = CGRectInset(r, -reach * 2, -reach * 2);
        CGPathAddRect(path, nullptr, CGRectOffset(frame, far, 0));
        if (!shape.empty()) {
            CGPathRef inner = rounded_path(Rect{shape.x + far, shape.y, shape.w, shape.h}, shape_radii);
            CGPathAddPath(path, nullptr, inner);
            CGPathRelease(inner);
        }
    } else {
        CGPathAddRect(clip, nullptr, CGRectInset(ink, -1, -1));
        CGPathAddPath(clip, nullptr, box);
        CGContextAddPath(ctx, clip);
        TWClipPath(ctx, true);
        if (!shape.empty()) {
            CGPathRef outer = rounded_path(Rect{shape.x + far, shape.y, shape.w, shape.h}, shape_radii);
            CGPathAddPath(path, nullptr, outer);
            CGPathRelease(outer);
        }
    }
    // Shadow offsets and blur are in device space, whatever the CTM.
    CGAffineTransform ctm = CGContextGetCTM(ctx);
    CGSize offset = CGSizeApplyAffineTransform(CGSizeMake(dx - far, dy), ctm);
    CGFloat scale = std::sqrt(std::fabs(ctm.a * ctm.d - ctm.b * ctm.c));
    CGColorRef col = cg_color(c.color);
    CGContextSetShadowWithColor(ctx, offset, blur * scale, col);
    CGColorRelease(col);
    CGContextSetRGBFillColor(ctx, 0, 0, 0, 1);
    CGContextAddPath(ctx, path);
    if (c.inset) CGContextEOFillPath(ctx);
    else CGContextFillPath(ctx);
    CGPathRelease(box);
    CGPathRelease(clip);
    CGPathRelease(path);
    CGContextRestoreGState(ctx);
}

CGImageRef CGBackend::canvas_image(int id) {
    auto it = canvases_.find(id);
    if (it == canvases_.end() || !it->second.ctx) return nullptr;
    // A copy-on-write snapshot: drawing a canvas onto itself reads the pixels from before the draw.
    return CGBitmapContextCreateImage(it->second.ctx);
}

void CGBackend::paint(const DisplayList& list, uint8_t* buffer, uint32_t w, uint32_t h, double dpr, const std::vector<Rect>* damage) {
    if (!buffer || w == 0 || h == 0) return;
    // The damaged areas in whole device pixels, a pixel around each for antialiasing.
    std::vector<CGRect> areas;
    if (damage) {
        for (const Rect& r : *damage) {
            double x0 = std::max(0.0, std::floor(r.x * dpr) - 1), y0 = std::max(0.0, std::floor(r.y * dpr) - 1);
            double x1 = std::min(double(w), std::ceil((r.x + r.w) * dpr) + 1), y1 = std::min(double(h), std::ceil((r.y + r.h) * dpr) + 1);
            if (x1 > x0 && y1 > y0) areas.push_back(CGRectMake(x0, y0, x1 - x0, y1 - y0));
        }
        if (areas.empty()) return;
        for (const CGRect& a : areas)
            for (size_t y = size_t(a.origin.y); y < size_t(a.origin.y + a.size.height); y++)
                std::fill(buffer + (y * w + size_t(a.origin.x)) * 4, buffer + (y * w + size_t(a.origin.x + a.size.width)) * 4, 0);
    } else {
        std::fill(buffer, buffer + size_t(w) * h * 4, 0);
    }
    CGContextRef ctx = CGBitmapContextCreate(buffer, w, h, 8, size_t(w) * 4, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    if (!ctx) return;
    if (!areas.empty()) {
        // Device px with y up, as the bitmap context starts.
        std::vector<CGRect> flipped;
        for (const CGRect& a : areas) flipped.push_back(CGRectMake(a.origin.x, h - a.origin.y - a.size.height, a.size.width, a.size.height));
        CGContextClipToRects(ctx, flipped.data(), flipped.size());
    }
    // CSS px, y down
    CGContextTranslateCTM(ctx, 0, h);
    CGContextScaleCTM(ctx, dpr, -dpr);
    CGContextSetTextMatrix(ctx, CGAffineTransformMakeScale(1, -1));
    CGContextSetShouldAntialias(ctx, true);
    CGContextSetShouldSmoothFonts(ctx, false);
    CGContextSetInterpolationQuality(ctx, kCGInterpolationHigh);
    draw(ctx, list);
    CGContextFlush(ctx);
    CGContextRelease(ctx);
}

namespace {
constexpr int64_t MAX_RASTER_PIXELS = int64_t(16) << 20;

/// The index of the GroupPop closing the group opened at `at`, or list.size().
size_t group_end(std::span<const Command> list, size_t at) {
    int depth = 0;
    for (size_t i = at; i < list.size(); i++) {
        switch (list[i].kind) {
        case Cmd::GroupPush: case Cmd::ClipPush: depth++; break;
        case Cmd::GroupPop: case Cmd::ClipPop:
            if (--depth == 0) return i;
            break;
        default: break;
        }
    }
    return list.size();
}

/// The index of the FilterPop closing the filter opened at `at`, or list.size().
size_t filter_end(std::span<const Command> list, size_t at) {
    int depth = 0;
    for (size_t i = at; i < list.size(); i++) {
        if (list[i].kind == Cmd::FilterPush) depth++;
        else if (list[i].kind == Cmd::FilterPop && --depth == 0) return i;
    }
    return list.size();
}
} // namespace

void CGBackend::draw_filtered(CGContextRef ctx, const Command& push, std::span<const Command> inner) {
    // User space → device px, y up; a filter under a rotation draws unfiltered.
    CGAffineTransform device = CGContextGetCTM(ctx);
    if (!push.filter || push.filter->empty() || device.b != 0 || device.c != 0 || !(device.a > 0) || device.d == 0) {
        draw(ctx, inner);
        return;
    }
    FilterReach reach = filter_reach(*push.filter);
    CGRect box = CGRectMake(push.rect.x - reach.left, push.rect.y - reach.top, push.rect.w + reach.left + reach.right, push.rect.h + reach.top + reach.bottom);
    CGRect bounds = CGRectIntegral(CGRectApplyAffineTransform(box, device));
    size_t w = size_t(bounds.size.width), h = size_t(bounds.size.height);
    if (CGRectIsEmpty(bounds) || int64_t(w) * int64_t(h) > MAX_RASTER_PIXELS / 4) {
        draw(ctx, inner);
        return;
    }
    CGContextRef layer = CGBitmapContextCreate(nullptr, w, h, 8, 0, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    if (!layer) {
        draw(ctx, inner);
        return;
    }
    CGContextConcatCTM(layer, CGAffineTransformConcat(device, CGAffineTransformMakeTranslation(-bounds.origin.x, -bounds.origin.y)));
    CGContextSetTextMatrix(layer, CGAffineTransformMakeScale(1, -1));
    CGContextSetShouldAntialias(layer, true);
    CGContextSetShouldSmoothFonts(layer, false);
    CGContextSetInterpolationQuality(layer, kCGInterpolationHigh);
    draw(layer, inner);
    CGContextFlush(layer);
    // The bitmap's first row is its top, as CSS has it: offsets go the right way.
    filter_pixels(static_cast<uint8_t*>(CGBitmapContextGetData(layer)), int(w), int(h), CGBitmapContextGetBytesPerRow(layer), false, *push.filter, float(device.a));
    CGImageRef image = CGBitmapContextCreateImage(layer);
    CGContextRelease(layer);
    if (!image) return;
    CGContextSaveGState(ctx);
    CGContextConcatCTM(ctx, CGAffineTransformInvert(CGContextGetCTM(ctx)));
    CGContextDrawImage(ctx, bounds, image);
    CGContextRestoreGState(ctx);
    CGImageRelease(image);
}

void CGBackend::backdrop(CGContextRef ctx, const Command& c) {
    // Only the bitmap the paint draws into has pixels to read; a transparency layer reads as what is under it.
    auto* base = static_cast<uint8_t*>(CGBitmapContextGetData(ctx));
    CGAffineTransform device = CGContextGetCTM(ctx);
    if (!base || !c.filter || c.filter->empty() || c.rect.empty() || device.b != 0 || device.c != 0 || !(device.a > 0) || device.d == 0) return;
    float spread = 0;
    for (const Filter& f : *c.filter)
        if (f.kind == Filter::Blur) spread += 3 * f.amount;
    const size_t bw = CGBitmapContextGetWidth(ctx), bh = CGBitmapContextGetHeight(ctx), stride = CGBitmapContextGetBytesPerRow(ctx);
    CGRect from = CGRectIntersection(CGRectIntegral(CGRectApplyAffineTransform(
                                         CGRectMake(c.rect.x - spread, c.rect.y - spread, c.rect.w + 2 * spread, c.rect.h + 2 * spread), device)),
                                     CGRectMake(0, 0, CGFloat(bw), CGFloat(bh)));
    if (CGRectIsEmpty(from)) return;
    const size_t x0 = size_t(from.origin.x), w = size_t(from.size.width), h = size_t(from.size.height);
    // Device y is up; memory rows go down from the top.
    const size_t top = bh - size_t(from.origin.y) - h;
    std::vector<uint8_t> copy(w * h * 4);
    for (size_t y = 0; y < h; y++) std::memcpy(&copy[y * w * 4], base + (top + y) * stride + x0 * 4, w * 4);
    filter_pixels(copy.data(), int(w), int(h), w * 4, false, *c.filter, float(device.a));
    CFHolder data(CFDataCreate(kCFAllocatorDefault, copy.data(), CFIndex(copy.size())));
    CGDataProviderRef provider = CGDataProviderCreateWithCFData((CFDataRef)data.ref);
    CGImageRef image = CGImageCreate(w, h, 8, 32, w * 4, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big, provider, nullptr, false, kCGRenderingIntentDefault);
    CGDataProviderRelease(provider);
    if (!image) return;
    CGContextSaveGState(ctx);
    CGPathRef shape = rounded_path(c.rect, c.radii);
    CGContextAddPath(ctx, shape);
    CGContextClip(ctx);
    CGPathRelease(shape);
    CGContextConcatCTM(ctx, CGAffineTransformInvert(CGContextGetCTM(ctx)));
    CGContextSetBlendMode(ctx, kCGBlendModeCopy);
    CGContextDrawImage(ctx, from, image);
    CGContextRestoreGState(ctx);
    CGImageRelease(image);
}

void CGBackend::trim() {
    for (auto& [key, raster] : rasters_) CGImageRelease(raster.image);
    rasters_.clear();
    raster_pixels_ = 0;
}

bool CGBackend::draw_raster(CGContextRef ctx, const Command& push, std::span<const Command> inner) {
    // User space → device px, y up.
    CGAffineTransform device = CGAffineTransformConcat(
        CGAffineTransformMake(push.matrix[0], push.matrix[1], push.matrix[2], push.matrix[3], push.matrix[4], push.matrix[5]), CGContextGetCTM(ctx));
    if (device.b != 0 || device.c != 0 || !(device.a > 0) || device.d == 0) return false;
    CGRect bounds = CGRectIntegral(CGRectApplyAffineTransform(CGRectMake(push.rect.x, push.rect.y, push.rect.w, push.rect.h), device));
    if (CGRectIsEmpty(bounds)) return false;
    size_t w = size_t(bounds.size.width), h = size_t(bounds.size.height);
    int64_t pixels = int64_t(w) * int64_t(h);
    if (pixels > MAX_RASTER_PIXELS / 4) return false;
    CGAffineTransform to_raster = CGAffineTransformConcat(device, CGAffineTransformMakeTranslation(-bounds.origin.x, -bounds.origin.y));
    auto q = [](CGFloat v) { return int64_t(std::llround(double(v) * 1024)); };
    RasterKey key{push.key, q(to_raster.a), q(to_raster.d), q(to_raster.tx), q(to_raster.ty)};
    auto it = rasters_.find(key);
    if (it == rasters_.end()) {
        while (!rasters_.empty() && raster_pixels_ + pixels > MAX_RASTER_PIXELS) {
            auto oldest = rasters_.begin();
            for (auto i = rasters_.begin(); i != rasters_.end(); ++i)
                if (i->second.used < oldest->second.used) oldest = i;
            raster_pixels_ -= oldest->second.pixels;
            CGImageRelease(oldest->second.image);
            rasters_.erase(oldest);
        }
        CGContextRef raster = CGBitmapContextCreate(nullptr, w, h, 8, 0, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        if (!raster) return false;
        CGContextConcatCTM(raster, to_raster);
        CGContextSetTextMatrix(raster, CGAffineTransformMakeScale(1, -1));
        CGContextSetShouldAntialias(raster, true);
        CGContextSetShouldSmoothFonts(raster, false);
        CGContextSetInterpolationQuality(raster, kCGInterpolationHigh);
        draw(raster, inner);
        CGImageRef image = CGBitmapContextCreateImage(raster);
        CGContextRelease(raster);
        if (!image) return false;
        raster_pixels_ += pixels;
        it = rasters_.emplace(key, Raster{image, 0, pixels}).first;
    }
    it->second.used = ++raster_clock_;
    // Device px, copied as they are.
    CGContextSaveGState(ctx);
    CGContextConcatCTM(ctx, CGAffineTransformInvert(CGContextGetCTM(ctx)));
    CGContextSetInterpolationQuality(ctx, kCGInterpolationNone);
    CGContextDrawImage(ctx, bounds, it->second.image);
    CGContextRestoreGState(ctx);
    return true;
}

void CGBackend::draw(CGContextRef ctx, std::span<const Command> list) {
    // Clips and groups nest together in paint order, so one stack unwinds both; a group with opacity
    // is a transparency layer.
    std::vector<bool> stack;
    auto pop = [&] {
        if (stack.empty()) return;
        if (stack.back()) CGContextEndTransparencyLayer(ctx);
        CGContextRestoreGState(ctx);
        stack.pop_back();
    };
    auto clip_to = [&](const Rect& r, const Radii& q) {
        CGPathRef p = rounded_path(r, q);
        CGContextAddPath(ctx, p);
        TWClipPath(ctx, false);
        CGPathRelease(p);
    };

    for (size_t i = 0; i < list.size(); i++) {
        const Command& c = list[i];
        switch (c.kind) {
        case Cmd::ClipPush:
            CGContextSaveGState(ctx);
            stack.push_back(false);
            if (c.path) {
                CGPathRef p = vector_path(*c.path);
                CGContextAddPath(ctx, p);
                if (c.path->evenodd) TWClipPath(ctx, true);
                else TWClipPath(ctx, false);
                CGPathRelease(p);
            } else {
                clip_to(c.rect, c.radii);
            }
            break;
        case Cmd::ClipPop:
        case Cmd::GroupPop:
            pop();
            break;
        case Cmd::GroupPush: {
            if (c.key && !c.hidden && c.opacity >= 1) {
                size_t end = group_end(list, i);
                if (end < list.size() && draw_raster(ctx, c, list.subspan(i + 1, end - i - 1))) {
                    i = end;
                    break;
                }
            }
            CGContextSaveGState(ctx);
            CGContextConcatCTM(ctx, CGAffineTransformMake(c.matrix[0], c.matrix[1], c.matrix[2], c.matrix[3], c.matrix[4], c.matrix[5]));
            bool layer = false;
            if (c.hidden) {
                CGContextClipToRect(ctx, CGRectZero);
            } else if (c.opacity < 1) {
                CGContextSetAlpha(ctx, std::max(c.opacity, 0.0f));
                CGContextBeginTransparencyLayer(ctx, nullptr);
                layer = true;
            }
            stack.push_back(layer);
            break;
        }
        case Cmd::FilterPush: {
            size_t end = filter_end(list, i);
            draw_filtered(ctx, c, list.subspan(i + 1, std::min(end, list.size()) - i - 1));
            i = end;
            break;
        }
        case Cmd::FilterPop:
            break;
        case Cmd::Backdrop:
            backdrop(ctx, c);
            break;
        case Cmd::Path:
            if (c.path) draw_path(ctx, *c.path);
            break;
        case Cmd::Shadow:
            draw_shadow(ctx, c);
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
            CTLineRef line = this->line(c.font, it->second, c.text, &c.color);
            if (line) {
                CGContextSetTextPosition(ctx, c.x, c.y);
                CTLineDraw(line, ctx);
                CFRelease(line);
            }
            break;
        }
        case Cmd::Image: {
            CGImageRef image = nullptr;
            if (c.canvas) {
                image = canvas_image(c.canvas);
            } else {
                auto it = images_.find(c.image);
                if (it != images_.end() && it->second) image = CGImageRetain(it->second);
            }
            if (!image || c.rect.empty()) {
                if (image) CGImageRelease(image);
                break;
            }
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
            CGContextDrawImage(ctx, CGRectMake(c.rect.x, 0, c.rect.w, c.rect.h), image);
            CGContextRestoreGState(ctx);
            CGImageRelease(image);
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
    while (!stack.empty()) pop();
}

// ───────────────────────── canvas ─────────────────────────

void CGBackend::canvas_run(int id, int w, int h, const std::vector<CanvasOp>& ops) {
    Canvas& target = canvases_[id];
    if (w <= 0 || h <= 0) {
        canvas_release(id);
        return;
    }
    if (!target.ctx || target.w != w || target.h != h) {
        if (target.ctx) CGContextRelease(target.ctx);
        // No buffer: CoreGraphics allocates it zeroed, which is the transparent bitmap a new canvas starts as.
        target.ctx = CGBitmapContextCreate(nullptr, size_t(w), size_t(h), 8, 0, rgb_, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        target.w = target.ctx ? w : 0;
        target.h = target.ctx ? h : 0;
        if (!target.ctx) return;
        CGContextTranslateCTM(target.ctx, 0, h);
        CGContextScaleCTM(target.ctx, 1, -1);
        CGContextSetTextMatrix(target.ctx, CGAffineTransformMakeScale(1, -1));
        CGContextSetShouldAntialias(target.ctx, true);
    }
    for (const CanvasOp& op : ops) canvas_op(target, op);
}

void CGBackend::canvas_op(Canvas& target, const CanvasOp& op) {
    CGContextRef ctx = target.ctx;
    if (op.kind == CanvasOp::Reset) {
        CGContextClearRect(ctx, CGRectMake(0, 0, target.w, target.h));
        return;
    }
    if (op.kind == CanvasOp::PutImage) {
        // Raw pixels, outside drawing: straight RGBA to premultiplied, clipped to the bitmap.
        auto* pixels = static_cast<uint8_t*>(CGBitmapContextGetData(ctx));
        size_t stride = CGBitmapContextGetBytesPerRow(ctx);
        int x0 = std::max(0, int(op.dx)), y0 = std::max(0, int(op.dy));
        int x1 = std::min(target.w, int(op.dx) + op.w), y1 = std::min(target.h, int(op.dy) + op.h);
        if (!pixels || !op.rgba || x1 <= x0 || y1 <= y0) return;
        for (int y = y0; y < y1; y++) {
            const uint8_t* src = op.rgba->data() + (size_t(y - int(op.dy)) * op.w + (x0 - int(op.dx))) * 4;
            uint8_t* dst = pixels + size_t(y) * stride + size_t(x0) * 4;
            for (int x = x0; x < x1; x++, src += 4, dst += 4) {
                uint32_t a = src[3];
                dst[0] = uint8_t(src[0] * a / 255);
                dst[1] = uint8_t(src[1] * a / 255);
                dst[2] = uint8_t(src[2] * a / 255);
                dst[3] = uint8_t(a);
            }
        }
        return;
    }
    CGContextSaveGState(ctx);
    if (op.clips) {
        for (const CanvasClip& clip : *op.clips) {
            Path p;
            p.ops = clip.path.ops;
            p.points = clip.path.points;
            CGPathRef shape = vector_path(p);
            CGContextAddPath(ctx, shape);
            if (clip.evenodd) TWClipPath(ctx, true);
            else TWClipPath(ctx, false);
            CGPathRelease(shape);
        }
    }
    if (op.kind == CanvasOp::Clear) {
        Path p;
        p.ops = op.path.ops;
        p.points = op.path.points;
        CGPathRef shape = vector_path(p);
        CGContextSetBlendMode(ctx, kCGBlendModeClear);
        CGContextAddPath(ctx, shape);
        CGContextFillPath(ctx);
        CGPathRelease(shape);
        CGContextRestoreGState(ctx);
        return;
    }
    CGContextSetAlpha(ctx, std::clamp(op.alpha, 0.0f, 1.0f));
    CGContextSetBlendMode(ctx, blend_mode(op.composite));
    if (op.has_shadow()) {
        // The bitmap's base space is y-up and unscaled: the offset is in canvas px with y flipped.
        CGColorRef col = cg_color(op.shadow_color);
        CGContextSetShadowWithColor(ctx, CGSizeMake(op.shadow_x, -op.shadow_y), std::max(op.shadow_blur, 0.0f), col);
        CGColorRelease(col);
        // The shadow is cast by the whole operation, not by each primitive inside it.
        CGContextBeginTransparencyLayer(ctx, nullptr);
        canvas_paint(ctx, op);
        CGContextEndTransparencyLayer(ctx);
    } else {
        canvas_paint(ctx, op);
    }
    CGContextRestoreGState(ctx);
}

// One operation's content, under the clip and compositing canvas_op has set.
void CGBackend::canvas_paint(CGContextRef ctx, const CanvasOp& op) {
    CGAffineTransform m = CGAffineTransformMake(op.matrix[0], op.matrix[1], op.matrix[2], op.matrix[3], op.matrix[4], op.matrix[5]);
    // Fills a shape (canvas px, already on the context's path or clip) with the paint, whose geometry is in user space.
    auto paint_area = [&] {
        const CanvasPaint& paint = op.paint;
        if (paint.kind == CanvasPaint::Solid || paint.stops.empty()) {
            Color c = paint.kind == CanvasPaint::Solid || paint.stops.empty() ? paint.color : paint.stops.front().color;
            CGColorRef col = cg_color(c);
            CGContextSetFillColorWithColor(ctx, col);
            CGContextFillRect(ctx, CGRectInfinite);
            CGColorRelease(col);
            return;
        }
        CGGradientRef g = gradient(paint.stops);
        if (!g) return;
        CGContextConcatCTM(ctx, m);
        const auto extend = kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation;
        const float* a = paint.args;
        if (paint.kind == CanvasPaint::Linear) CGContextDrawLinearGradient(ctx, g, CGPointMake(a[0], a[1]), CGPointMake(a[2], a[3]), extend);
        else if (paint.kind == CanvasPaint::Radial) CGContextDrawRadialGradient(ctx, g, CGPointMake(a[0], a[1]), a[2], CGPointMake(a[3], a[4]), a[5], extend);
        else CGContextDrawConicGradient(ctx, g, CGPointMake(a[1], a[2]), a[0]);
        CGGradientRelease(g);
    };

    switch (op.kind) {
    case CanvasOp::Fill: {
        Path p;
        p.ops = op.path.ops;
        p.points = op.path.points;
        CGPathRef shape = vector_path(p);
        CGContextSaveGState(ctx);
        CGContextAddPath(ctx, shape);
        if (op.evenodd) TWClipPath(ctx, true);
        else TWClipPath(ctx, false);
        paint_area();
        CGContextRestoreGState(ctx);
        CGPathRelease(shape);
        return;
    }
    case CanvasOp::Stroke: {
        // The path is in canvas px but the line style in user space: stroke it back in user space.
        if (std::fabs(m.a * m.d - m.b * m.c) < 1e-12) return;
        CGAffineTransform inverse = CGAffineTransformInvert(m);
        Path p;
        p.ops = op.path.ops;
        p.points = op.path.points;
        CGPathRef device = vector_path(p);
        CGPathRef user = CGPathCreateCopyByTransformingPath(device, &inverse);
        CGPathRelease(device);
        if (!user) return;
        CGContextSaveGState(ctx);
        CGContextConcatCTM(ctx, m);
        set_line_style(ctx, op.line_width, op.line_cap, op.line_join, op.miter_limit, op.dashes, op.dash_offset);
        CGContextAddPath(ctx, user);
        CGContextReplacePathWithStrokedPath(ctx);
        TWClipPath(ctx, false);
        CGContextConcatCTM(ctx, inverse);
        paint_area();
        CGContextRestoreGState(ctx);
        CGPathRelease(user);
        return;
    }
    case CanvasOp::Text: {
        auto it = fonts_.find(op.font);
        if (it == fonts_.end() || op.scale_x == 0 || std::fabs(m.a * m.d - m.b * m.c) < 1e-12) return;
        CTLineRef line = this->line(op.font, it->second, op.text, nullptr);
        if (!line) return;
        CGContextSaveGState(ctx);
        CGContextConcatCTM(ctx, m);
        CGContextTranslateCTM(ctx, op.x, op.y);
        CGContextScaleCTM(ctx, op.scale_x, 1);
        CGContextSetTextPosition(ctx, op.text_dx, 0);
        if (op.stroke_text) {
            set_line_style(ctx, op.line_width, op.line_cap, op.line_join, op.miter_limit, op.dashes, op.dash_offset);
            CGContextSetTextDrawingMode(ctx, kCGTextStrokeClip);
        } else {
            CGContextSetTextDrawingMode(ctx, kCGTextClip);
        }
        CTLineDraw(line, ctx);
        CFRelease(line);
        // The glyphs are now the clip: the paint fills them, in canvas px.
        CGContextScaleCTM(ctx, 1 / op.scale_x, 1);
        CGContextTranslateCTM(ctx, -op.x, -op.y);
        CGContextConcatCTM(ctx, CGAffineTransformInvert(m));
        paint_area();
        CGContextRestoreGState(ctx);
        return;
    }
    case CanvasOp::Image: {
        CGImageRef image = nullptr;
        if (op.canvas) {
            image = canvas_image(op.canvas);
        } else {
            auto it = images_.find(op.image);
            if (it != images_.end() && it->second) image = CGImageRetain(it->second);
        }
        if (!image) return;
        if (op.sw >= 0) {
            CGImageRef part = CGImageCreateWithImageInRect(image, CGRectMake(op.sx, op.sy, op.sw, op.sh));
            CGImageRelease(image);
            image = part;
            if (!image) return;
        }
        CGContextSaveGState(ctx);
        CGContextConcatCTM(ctx, m);
        CGContextSetInterpolationQuality(ctx, op.smoothing ? kCGInterpolationHigh : kCGInterpolationNone);
        // CGContextDrawImage draws y-up: flip around the destination
        CGContextTranslateCTM(ctx, 0, op.dy + op.dh);
        CGContextScaleCTM(ctx, 1, -1);
        CGContextDrawImage(ctx, CGRectMake(op.dx, 0, op.dw, op.dh), image);
        CGContextRestoreGState(ctx);
        CGImageRelease(image);
        return;
    }
    default:
        return;
    }
}

bool CGBackend::canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba) {
    rgba.assign(size_t(std::max(w, 0)) * std::max(h, 0) * 4, 0);
    auto it = canvases_.find(id);
    if (it == canvases_.end() || !it->second.ctx || w <= 0 || h <= 0) return true;
    const Canvas& canvas = it->second;
    const auto* pixels = static_cast<const uint8_t*>(CGBitmapContextGetData(canvas.ctx));
    size_t stride = CGBitmapContextGetBytesPerRow(canvas.ctx);
    int x0 = std::max(0, x), y0 = std::max(0, y), x1 = std::min(canvas.w, x + w), y1 = std::min(canvas.h, y + h);
    if (!pixels || x1 <= x0 || y1 <= y0) return true;
    for (int row = y0; row < y1; row++) {
        const uint8_t* src = pixels + size_t(row) * stride + size_t(x0) * 4;
        uint8_t* dst = rgba.data() + (size_t(row - y) * w + (x0 - x)) * 4;
        for (int col = x0; col < x1; col++, src += 4, dst += 4) {
            uint32_t a = src[3];
            // getImageData is straight alpha.
            dst[0] = a ? uint8_t(std::min<uint32_t>(255, src[0] * 255 / a)) : 0;
            dst[1] = a ? uint8_t(std::min<uint32_t>(255, src[1] * 255 / a)) : 0;
            dst[2] = a ? uint8_t(std::min<uint32_t>(255, src[2] * 255 / a)) : 0;
            dst[3] = uint8_t(a);
        }
    }
    return true;
}

void CGBackend::canvas_release(int id) {
    auto it = canvases_.find(id);
    if (it == canvases_.end()) return;
    if (it->second.ctx) CGContextRelease(it->second.ctx);
    canvases_.erase(it);
}

} // namespace twlite
