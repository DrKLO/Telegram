#include "qt_backend.h"
#include "filters.h"
#include "png.h"

#include <QFontDatabase>
#include <QFontInfo>
#include <QFontMetricsF>
#include <QLinearGradient>
#include <QPainter>
#include <QRadialGradient>
#include <QRawFont>
#include <QSysInfo>
#include <QConicalGradient>

#include <algorithm>
#include <cstring>
#include <cmath>
#include <optional>

namespace twlite {

namespace {

constexpr float kPi = 3.14159265f;
/// Decoded images, as the Apple renderer bounds them: a side, and all of them together. Over either the
/// image is refused (an error for the widget) before it is decoded; nothing evicts one a document holds.
constexpr int MAX_IMAGE_SIDE = 4096;
constexpr int64_t MAX_IMAGE_BYTES = int64_t(24) << 20;
/// Every canvas's bitmap together, as the core bounds one renderer's (Canvas::MAX_PIXELS).
constexpr int64_t MAX_CANVAS_PIXELS = int64_t(4096) * 4096;
/// Measured widths kept per face: longer text is measured each time.
constexpr size_t MAX_WIDTH_KEY = 256;

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

QColor qcolor(const Color& c) { return QColor(c.r, c.g, c.b, c.a); }
QRectF qrect(const Rect& r) { return QRectF(r.x, r.y, r.w, r.h); }
QTransform qmatrix(const float m[6]) { return QTransform(m[0], m[1], m[2], m[3], m[4], m[5]); }

QPainterPath rounded(const Rect& r, const Radii& q) {
    QPainterPath path;
    if (!q.any()) {
        path.addRect(qrect(r));
        return path;
    }

    // CSS: when adjacent radii exceed a side, all of them shrink by the same factor.
    float f = 1;
    auto limit = [&](float side, float a, float b) { if (a + b > side && a + b > 0) f = std::min(f, side / (a + b)); };
    limit(r.w, q.tlx, q.trx);
    limit(r.w, q.blx, q.brx);
    limit(r.h, q.tly, q.bly);
    limit(r.h, q.try_, q.bry);

    float tlx = q.tlx * f, tly = q.tly * f, trx = q.trx * f, try_ = q.try_ * f;
    float brx = q.brx * f, bry = q.bry * f, blx = q.blx * f, bly = q.bly * f;
    float x0 = r.x, y0 = r.y, x1 = r.x + r.w, y1 = r.y + r.h;

    // A quarter ellipse from the current point to (ex, ey) around the corner (cx, cy).
    constexpr float k = 0.5522847498f;
    auto corner = [&](float sx, float sy, float cx, float cy, float ex, float ey) {
        if (sx == ex && sy == ey) return;
        path.cubicTo(sx + (cx - sx) * k, sy + (cy - sy) * k, ex + (cx - ex) * k, ey + (cy - ey) * k, ex, ey);
    };

    path.moveTo(x0 + tlx, y0);
    path.lineTo(x1 - trx, y0);
    corner(x1 - trx, y0, x1, y0, x1, y0 + try_);
    path.lineTo(x1, y1 - bry);
    corner(x1, y1 - bry, x1, y1, x1 - brx, y1);
    path.lineTo(x0 + blx, y1);
    corner(x0 + blx, y1, x0, y1, x0, y1 - bly);
    path.lineTo(x0, y0 + tly);
    corner(x0, y0 + tly, x0, y0, x0 + tlx, y0);
    path.closeSubpath();
    return path;
}

QPainterPath geometry(const std::vector<Path::Op>& ops, const std::vector<float>& points, bool evenodd) {
    QPainterPath path;
    path.setFillRule(evenodd ? Qt::OddEvenFill : Qt::WindingFill);
    const float* v = points.data();
    size_t n = points.size(), i = 0;
    bool open = false;
    for (auto op : ops) {
        size_t need = op == Path::Cubic ? 6 : op == Path::Quad ? 4 : op == Path::Close ? 0 : 2;
        if (i + need > n) break;
        switch (op) {
        case Path::Move:
            path.moveTo(v[i], v[i + 1]);
            open = true;
            break;
        case Path::Line:
            if (open) path.lineTo(v[i], v[i + 1]);
            break;
        case Path::Quad:
            if (open) path.quadTo(v[i], v[i + 1], v[i + 2], v[i + 3]);
            break;
        case Path::Cubic:
            if (open) path.cubicTo(v[i], v[i + 1], v[i + 2], v[i + 3], v[i + 4], v[i + 5]);
            break;
        case Path::Close:
            if (open) path.closeSubpath();
            open = false;
            break;
        }
        i += need;
    }
    return path;
}

QGradientStops gradient_stops(const std::vector<Stop>& list, bool reversed = false) {
    QGradientStops out;
    out.reserve(qsizetype(list.size()));
    for (const Stop& s : list) out.push_back({std::clamp(double(s.offset), 0.0, 1.0), qcolor(s.color)});
    if (reversed) {
        std::reverse(out.begin(), out.end());
        for (auto& s : out) s.first = 1 - s.first;
    }
    // Qt wants the positions in order; a hard stop keeps its order at the same position.
    for (qsizetype i = 1; i < out.size(); i++) out[i].first = std::max(out[i].first, out[i - 1].first);
    return out;
}

QPen pen(const QBrush& brush, float width, int cap, int join, float miter, const std::vector<float>& dashes, float dash_offset) {
    QPen p(brush, width, Qt::SolidLine, cap == 1 ? Qt::RoundCap : cap == 2 ? Qt::SquareCap : Qt::FlatCap,
           join == 1 ? Qt::RoundJoin : join == 2 ? Qt::BevelJoin : Qt::SvgMiterJoin);
    // Qt measures the miter from the join point in widths, SVG and the canvas tip to tip: half as long.
    p.setMiterLimit(miter / 2);
    bool dashed = false;
    for (float d : dashes) dashed = dashed || d > 0;
    if (dashed && width > 0) {
        // In pen widths, an even count of positive entries; an odd list repeats, as in SVG.
        QList<qreal> pattern;
        for (int pass = 0; pass < (dashes.size() % 2 ? 2 : 1); pass++)
            for (float d : dashes) pattern.push_back(std::max(d / width, 1e-3f));
        p.setDashPattern(pattern);
        p.setDashOffset(dash_offset / width);
    }
    return p;
}

void hints(QPainter& p) {
    p.setRenderHints(QPainter::Antialiasing | QPainter::SmoothPixmapTransform | QPainter::TextAntialiasing);
}

/// An antialiased coverage image of `path` (in the image's px) over a w × h area.
QImage coverage(const QPainterPath& path, int w, int h) {
    QImage mask(w, h, QImage::Format_Alpha8);
    mask.fill(0);
    QPainter p(&mask);
    p.setRenderHint(QPainter::Antialiasing);
    p.fillPath(path, Qt::black);
    return mask;
}

/// Keeps `image` only where `mask` covers it (DestinationIn over the whole image: a path fill would
/// leave what it does not touch).
void keep(QImage& image, const QImage& mask) {
    QPainter p(&image);
    p.setCompositionMode(QPainter::CompositionMode_DestinationIn);
    p.drawImage(0, 0, mask);
}

/// A Gaussian of `sigma` px over an alpha image, as three box blurs; outside the image is transparent.
void blur_alpha(QImage& a8, float sigma) {
    if (sigma < 0.5f) return;
    int w = a8.width(), h = a8.height();
    constexpr int passes = 3;
    float ideal = std::sqrt(12 * sigma * sigma / passes + 1);
    int wl = int(std::floor(ideal));
    if (wl % 2 == 0) wl--;
    int wu = wl + 2;
    int m = int(std::round((12 * sigma * sigma - passes * wl * wl - 4 * passes * wl - 3 * passes) / (-4.0f * wl - 4)));

    std::vector<int> line(size_t(std::max(w, h)));
    auto box = [&](uint8_t* base, int count, int stride, int r) {
        int span = 2 * r + 1;
        for (int i = 0; i < count; i++) line[size_t(i)] = base[size_t(i) * stride];
        int sum = 0;
        for (int i = 0; i <= r && i < count; i++) sum += line[size_t(i)];
        for (int i = 0; i < count; i++) {
            base[size_t(i) * stride] = uint8_t((sum + span / 2) / span);
            int add = i + r + 1, drop = i - r;
            if (add < count) sum += line[size_t(add)];
            if (drop >= 0) sum -= line[size_t(drop)];
        }
    };

    for (int pass = 0; pass < passes; pass++) {
        int r = ((pass < m ? wl : wu) - 1) / 2;
        if (r <= 0) continue;
        for (int y = 0; y < h; y++) box(a8.scanLine(y), w, 1, r);
        uint8_t* bits = a8.bits();
        for (int x = 0; x < w; x++) box(bits + x, h, int(a8.bytesPerLine()), r);
    }
}

/// `alpha` tinted with `color`, as premultiplied ARGB.
QImage tint(const QImage& alpha, const QColor& color) {
    QImage out = QtBackend::make_image(alpha.width(), alpha.height());
    out.fill(color);
    keep(out, alpha);
    return out;
}

// ── The non-separable blend modes of Compositing and Blending Level 1, which QPainter lacks ──

struct Rgb { float r, g, b; };
float lum(Rgb c) { return 0.3f * c.r + 0.59f * c.g + 0.11f * c.b; }
Rgb clip_color(Rgb c) {
    float l = lum(c), n = std::min({c.r, c.g, c.b}), x = std::max({c.r, c.g, c.b});
    auto f = [&](float v) {
        if (n < 0) v = l + (v - l) * l / (l - n);
        if (x > 1) v = l + (v - l) * (1 - l) / (x - l);
        return v;
    };
    return {f(c.r), f(c.g), f(c.b)};
}
Rgb set_lum(Rgb c, float l) {
    float d = l - lum(c);
    return clip_color({c.r + d, c.g + d, c.b + d});
}
float sat(Rgb c) { return std::max({c.r, c.g, c.b}) - std::min({c.r, c.g, c.b}); }
Rgb set_sat(Rgb c, float s) {
    float* v[3] = {&c.r, &c.g, &c.b};
    std::sort(v, v + 3, [](float* a, float* b) { return *a < *b; });
    if (*v[2] > *v[0]) {
        *v[1] = (*v[1] - *v[0]) * s / (*v[2] - *v[0]);
        *v[2] = s;
    } else {
        *v[1] = *v[2] = 0;
    }
    *v[0] = 0;
    return c;
}

void blend_nonseparable(QImage& dst, const QImage& src, Composite mode) {
    for (int y = 0; y < dst.height(); y++) {
        auto* d = reinterpret_cast<QRgb*>(dst.scanLine(y));
        auto* s = reinterpret_cast<const QRgb*>(src.constScanLine(y));
        for (int x = 0; x < dst.width(); x++) {
            float as = qAlpha(s[x]) / 255.0f, ab = qAlpha(d[x]) / 255.0f;
            if (as <= 0) continue;
            auto straight = [](QRgb p, float a) { return Rgb{qRed(p) / 255.0f / a, qGreen(p) / 255.0f / a, qBlue(p) / 255.0f / a}; };
            Rgb cs = straight(s[x], as);
            Rgb cb = ab > 0 ? straight(d[x], ab) : Rgb{0, 0, 0};
            Rgb b;
            switch (mode) {
            case Composite::Hue: b = set_lum(set_sat(cs, sat(cb)), lum(cb)); break;
            case Composite::Saturation: b = set_lum(set_sat(cb, sat(cs)), lum(cb)); break;
            case Composite::Color: b = set_lum(cs, lum(cb)); break;
            default: b = set_lum(cb, lum(cs)); break;
            }
            // Cs' = (1 - ab) Cs + ab B(Cb, Cs), then source-over, premultiplied.
            float ao = as + ab * (1 - as);
            auto channel = [&](float s1, float b1, float bb) {
                float mixed = (1 - ab) * s1 + ab * bb;
                return int(std::lround(std::clamp(as * mixed + (1 - as) * ab * b1, 0.0f, 1.0f) * 255));
            };
            d[x] = qRgba(channel(cs.r, cb.r, b.r), channel(cs.g, cb.g, b.g), channel(cs.b, cb.b, b.b), int(std::lround(ao * 255)));
        }
    }
}

bool qt_mode(Composite c, QPainter::CompositionMode& out) {
    static const QPainter::CompositionMode modes[] = {
        QPainter::CompositionMode_SourceOver, QPainter::CompositionMode_SourceIn, QPainter::CompositionMode_SourceOut,
        QPainter::CompositionMode_SourceAtop, QPainter::CompositionMode_DestinationOver, QPainter::CompositionMode_DestinationIn,
        QPainter::CompositionMode_DestinationOut, QPainter::CompositionMode_DestinationAtop, QPainter::CompositionMode_Plus,
        QPainter::CompositionMode_Source, QPainter::CompositionMode_Xor, QPainter::CompositionMode_Multiply,
        QPainter::CompositionMode_Screen, QPainter::CompositionMode_Overlay, QPainter::CompositionMode_Darken,
        QPainter::CompositionMode_Lighten, QPainter::CompositionMode_ColorDodge, QPainter::CompositionMode_ColorBurn,
        QPainter::CompositionMode_HardLight, QPainter::CompositionMode_SoftLight, QPainter::CompositionMode_Difference,
        QPainter::CompositionMode_Exclusion,
    };
    size_t i = size_t(c);
    if (i >= std::size(modes)) return false;
    out = modes[i];
    return true;
}

/// Composites `src` (premultiplied, the canvas's size) onto `dst` with `mode`, only where `clip` covers.
void composite(QImage& dst, const QImage& src, Composite mode, const QImage* clip) {
    QPainter::CompositionMode qmode;
    bool native = qt_mode(mode, qmode);
    auto apply = [&](QImage& onto, const QImage& from) {
        if (native) {
            QPainter p(&onto);
            p.setCompositionMode(qmode);
            p.drawImage(0, 0, from);
        } else {
            blend_nonseparable(onto, from, mode);
        }
    };

    if (!clip) {
        apply(dst, src);
        return;
    }
    if (mode == Composite::SourceOver) {
        // Source-over of a source masked by the coverage is the clipped result already.
        QImage masked = src;
        keep(masked, *clip);
        apply(dst, masked);
        return;
    }
    // Other modes change the destination where the source is transparent too: blend the composited
    // result into the destination by the clip's coverage.
    QImage result = dst;
    apply(result, src);
    keep(result, *clip);
    QPainter p(&dst);
    p.setCompositionMode(QPainter::CompositionMode_DestinationOut);
    p.drawImage(0, 0, *clip);
    p.setCompositionMode(QPainter::CompositionMode_Plus);
    p.drawImage(0, 0, result);
}

QBrush canvas_brush(const CanvasPaint& paint) {
    if (paint.kind == CanvasPaint::Solid || paint.stops.empty()) return QBrush(qcolor(paint.color));
    const float* a = paint.args;
    switch (paint.kind) {
    case CanvasPaint::Linear: {
        QLinearGradient g(a[0], a[1], a[2], a[3]);
        g.setStops(gradient_stops(paint.stops));
        return QBrush(g);
    }
    case CanvasPaint::Radial: {
        // The canvas's two-circle gradient is Qt's extended radial gradient.
        QRadialGradient g(QPointF(a[3], a[4]), std::max(a[5], 0.0f), QPointF(a[0], a[1]), std::max(a[2], 0.0f));
        g.setStops(gradient_stops(paint.stops));
        return QBrush(g);
    }
    default: {
        // The canvas turns clockwise from its start angle, Qt counter-clockwise from its own: the stops run backwards.
        QConicalGradient g(QPointF(a[1], a[2]), -a[0] * 180 / kPi);
        g.setStops(gradient_stops(paint.stops, true));
        return QBrush(g);
    }
    }
}

} // namespace

QImage QtBackend::make_image(int w, int h) {
    QImage image(std::max(w, 1), std::max(h, 1), QImage::Format_ARGB32_Premultiplied);
    // 96 dpi, the CSS reference: point sizes below are px × 0.75 on every image this backend draws.
    image.setDotsPerMeterX(3780);
    image.setDotsPerMeterY(3780);
    image.fill(Qt::transparent);
    return image;
}

QtBackend::QtBackend() : reference_(make_image(1, 1)) {}

QStringList QtBackend::resolve_families(const std::string& families) {
    auto hinted = [](QFont::StyleHint hint) {
        QFont f;
        f.setStyleHint(hint);
        return f.defaultFamily();
    };

    QStringList out;
    size_t start = 0;
    while (start <= families.size()) {
        size_t comma = families.find(',', start);
        std::string family = twlite::trim(families.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
        start = comma == std::string::npos ? families.size() + 1 : comma + 1;
        if (family.empty()) continue;

        std::string l = lower(family);
        if (l == "-apple-system" || l == "blinkmacsystemfont" || l == "system-ui" || l == "ui-sans-serif" || l == "sans-serif" || l == "roboto" ||
            l == "inter" || l == "helvetica" || l == "helvetica neue") {
            out.push_back(QFontDatabase::systemFont(QFontDatabase::GeneralFont).family());
        } else if (l == "serif" || l == "ui-serif") {
            out.push_back(hinted(QFont::Serif));
        } else if (l == "monospace" || l == "ui-monospace" || l == "menlo" || l == "sf mono") {
            // Qt's fixed font is Courier New on Windows, far wider than what the other platforms pick.
            QString mono = QFontDatabase::systemFont(QFontDatabase::FixedFont).family();
            for (const char* name : {"SF Mono", "Menlo", "Consolas", "Cascadia Mono", "DejaVu Sans Mono"})
                if (QFontDatabase::hasFamily(name)) {
                    mono = name;
                    break;
                }
            out.push_back(mono);
        } else if (l == "cursive") {
            out.push_back(hinted(QFont::Cursive));
        } else if (l == "fantasy") {
            out.push_back(hinted(QFont::Fantasy));
        } else if (l == "emoji" || l == "apple color emoji") {
            for (const char* name : {"Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji"})
                if (QFontDatabase::hasFamily(name)) out.push_back(name);
        } else {
            QString name = QString::fromStdString(family);
            if (QFontDatabase::hasFamily(name)) out.push_back(name);
        }
    }
    if (out.isEmpty()) out.push_back(QFontDatabase::systemFont(QFontDatabase::GeneralFont).family());
    return out;
}

int QtBackend::create_font(const std::string& families, float size, int weight, bool italic, const std::string& features, FontMetrics& out) {
    FaceKey key{families, size, weight, italic, features};
    auto it = faces_.find(key);
    if (it == faces_.end()) {
        auto face = std::make_shared<Face>();
        face->font.setFamilies(resolve_families(families));
        face->font.setPointSizeF(std::max(size, 0.01f) * 0.75);
        face->font.setWeight(QFont::Weight(std::clamp(weight, 1, 1000)));
        face->font.setItalic(italic);
        // Unhinted outlines scale linearly, so widths measured here hold at any device pixel ratio.
        face->font.setHintingPreference(QFont::PreferNoHinting);
#if QT_VERSION >= QT_VERSION_CHECK(6, 7, 0)
        for (const QString& entry : QString::fromStdString(features).split(u',', Qt::SkipEmptyParts)) {
            auto tag = QFont::Tag::fromString(entry.left(4));
            if (entry.size() > 5 && entry[4] == u'=' && tag) face->font.setFeature(*tag, entry.mid(5).toUInt());
        }
#endif

        QFontMetricsF m(face->font, &reference_);
        face->metrics.ascent = float(m.ascent());
        face->metrics.descent = float(m.descent());
        face->metrics.height = float(m.lineSpacing());
        face->metrics.x_height = float(m.xHeight());
        face->metrics.zero_width = float(m.horizontalAdvance(QStringLiteral("0")));
        // sTypoAscender and sTypoDescender, big-endian at 68 and 70 in the OS/2 table.
        QByteArray os2 = QRawFont::fromFont(face->font).fontTable("OS/2");
        if (os2.size() >= 72) {
            auto at = [&](int i) { return float(int16_t(uint8_t(os2[i]) << 8 | uint8_t(os2[i + 1]))); };
            set_em_box(face->metrics, at(68), -at(70), size);
        }
        it = faces_.emplace(key, std::move(face)).first;
    }

    int handle = ++next_font_;
    handles_[handle] = it->second;
    out = it->second->metrics;
    return handle;
}

void QtBackend::delete_font(int font) { handles_.erase(font); }

QtBackend::Face* QtBackend::face(int font) {
    auto it = handles_.find(font);
    return it == handles_.end() ? nullptr : it->second.get();
}

float QtBackend::text_width(int font, const std::string& text) {
    Face* f = face(font);
    if (!f) return 0;
    auto it = f->widths.find(text);
    if (it != f->widths.end()) return it->second;
    float w = float(QFontMetricsF(f->font, &reference_).horizontalAdvance(QString::fromUtf8(text.data(), qsizetype(text.size()))));
    // Words repeat across rebuilds, but a widget that types a counter would grow this forever.
    if (text.size() > MAX_WIDTH_KEY) return w;
    if (f->widths.size() > 4096) f->widths.clear();
    f->widths.emplace(text, w);
    return w;
}

bool QtBackend::load_image(int id, const std::string& url, float& w, float& h) {
    // data:[<mime>][;base64],<payload>
    size_t comma = url.find(',');
    if (url.rfind("data:", 0) != 0 || comma == std::string::npos) return false;
    std::string meta = lower(url.substr(5, comma - 5));
    // No SVG rasteriser on the other platforms, so none here either.
    if (meta.find("svg") != std::string::npos) return false;

    QByteArray payload = QByteArray::fromStdString(url.substr(comma + 1));
    QByteArray bytes = meta.find(";base64") != std::string::npos ? QByteArray::fromBase64(payload) : QByteArray::fromPercentEncoding(payload);
    if (bytes.isEmpty()) return false;
    // The size from the header, before a decoder allocates what a small file can claim.
    uint32_t declared_w = 0, declared_h = 0;
    if (!image_dimensions(reinterpret_cast<const uint8_t*>(bytes.constData()), size_t(bytes.size()), declared_w, declared_h)) return false;
    if (declared_w == 0 || declared_h == 0 || declared_w > uint32_t(MAX_IMAGE_SIDE) || declared_h > uint32_t(MAX_IMAGE_SIDE)) return false;
    if (image_bytes_ + int64_t(declared_w) * declared_h * 4 > MAX_IMAGE_BYTES) return false;
    QImage image;
    if (!image.loadFromData(bytes) || image.width() > MAX_IMAGE_SIDE || image.height() > MAX_IMAGE_SIDE) return false;
    image_bytes_ += int64_t(image.width()) * image.height() * 4;
    QImage& kept = images_[id] = image.convertToFormat(QImage::Format_ARGB32_Premultiplied);
    // Qt's decoder carries on through a broken stream; Chromium leaves what follows the damage transparent.
    size_t intact = png_intact_rows(reinterpret_cast<const uint8_t*>(bytes.constData()), size_t(bytes.size()));
    for (int y = int(std::min<size_t>(intact, size_t(kept.height()))); y < kept.height(); y++) std::memset(kept.scanLine(y), 0, size_t(kept.bytesPerLine()));
    w = float(image.width());
    h = float(image.height());
    return true;
}

// ───────────────────────── canvas ─────────────────────────

void QtBackend::canvas_content(QPainter& p, const CanvasOp& op, const QImage* self) {
    QTransform m = qmatrix(op.matrix);
    hints(p);
    switch (op.kind) {
    case CanvasOp::Fill:
    case CanvasOp::Stroke: {
        bool invertible = false;
        QTransform inverse = m.inverted(&invertible);
        if (!invertible) return;
        // The path is in canvas px; drawn in user space under the transform, the gradient and the
        // stroke width are where the canvas spec puts them.
        QPainterPath user = inverse.map(geometry(op.path.ops, op.path.points, op.evenodd));
        p.setTransform(m);
        if (op.kind == CanvasOp::Fill) p.fillPath(user, canvas_brush(op.paint));
        else p.strokePath(user, pen(canvas_brush(op.paint), op.line_width, op.line_cap, op.line_join, op.miter_limit, op.dashes, op.dash_offset));
        return;
    }
    case CanvasOp::Text: {
        Face* f = face(op.font);
        if (!f) return;
        QString text = QString::fromUtf8(op.text.data(), qsizetype(op.text.size()));
        p.setTransform(QTransform::fromScale(op.scale_x, 1) * QTransform::fromTranslate(op.x, op.y) * m);
        if (op.stroke_text) {
            QPainterPath outline;
            outline.addText(op.text_dx, 0, f->font, text);
            p.strokePath(outline, pen(canvas_brush(op.paint), op.line_width, op.line_cap, op.line_join, op.miter_limit, op.dashes, op.dash_offset));
        } else {
            p.setFont(f->font);
            p.setPen(QPen(canvas_brush(op.paint), 0));
            p.drawText(QPointF(op.text_dx, 0), text);
        }
        return;
    }
    case CanvasOp::Image: {
        const QImage* image = nullptr;
        if (op.canvas) {
            auto it = canvases_.find(op.canvas);
            if (it != canvases_.end()) image = self && &it->second == self ? nullptr : &it->second;
        } else {
            auto it = images_.find(op.image);
            if (it != images_.end()) image = &it->second;
        }
        if (!image) return;
        p.setTransform(m);
        p.setRenderHint(QPainter::SmoothPixmapTransform, op.smoothing);
        QRectF source = op.sw < 0 ? QRectF(image->rect()) : QRectF(op.sx, op.sy, op.sw, op.sh);
        p.drawImage(QRectF(op.dx, op.dy, op.dw, op.dh), *image, source);
        return;
    }
    default:
        return;
    }
}

void QtBackend::canvas_op(QImage& target, const CanvasOp& op, int id) {
    int w = target.width(), h = target.height();

    if (op.kind == CanvasOp::Reset) {
        target.fill(Qt::transparent);
        return;
    }

    if (op.kind == CanvasOp::PutImage) {
        // Raw pixels, outside drawing: straight RGBA to premultiplied, clipped to the bitmap.
        int ox = int(op.dx), oy = int(op.dy);
        int x0 = std::max(0, ox), y0 = std::max(0, oy), x1 = std::min(w, ox + op.w), y1 = std::min(h, oy + op.h);
        for (int y = y0; y < y1; y++) {
            const uint8_t* src = op.rgba->data() + (size_t(y - oy) * op.w + (x0 - ox)) * 4;
            auto* dst = reinterpret_cast<QRgb*>(target.scanLine(y));
            for (int x = x0; x < x1; x++, src += 4) dst[x] = qPremultiply(qRgba(src[0], src[1], src[2], src[3]));
        }
        return;
    }

    std::optional<QImage> clip;
    QPainterPath clip_path;
    if (op.clips) {
        bool first = true;
        for (const CanvasClip& c : *op.clips) {
            QPainterPath path = geometry(c.path.ops, c.path.points, c.evenodd);
            clip_path = first ? path : clip_path.intersected(path);
            first = false;
        }
        if (!first) clip = coverage(clip_path, w, h);
    }

    if (op.kind == CanvasOp::Clear) {
        // Clearing writes transparent pixels: the area meets the clip as geometry, antialiased once.
        QPainterPath area = geometry(op.path.ops, op.path.points, false);
        if (clip) area = area.intersected(clip_path);
        QPainter p(&target);
        p.setRenderHint(QPainter::Antialiasing);
        p.setCompositionMode(QPainter::CompositionMode_Clear);
        p.fillPath(area, Qt::black);
        return;
    }

    // An image drawn onto its own canvas reads a copy taken before the draw.
    std::optional<QImage> snapshot;
    if (op.kind == CanvasOp::Image && op.canvas == id) snapshot = target.copy();

    // The operation is drawn on its own first: alpha, the shadow and the mode apply to it as a whole.
    QImage content = make_image(w, h);
    {
        QPainter p(&content);
        if (snapshot) {
            hints(p);
            p.setTransform(qmatrix(op.matrix));
            p.setRenderHint(QPainter::SmoothPixmapTransform, op.smoothing);
            QRectF source = op.sw < 0 ? QRectF(snapshot->rect()) : QRectF(op.sx, op.sy, op.sw, op.sh);
            p.drawImage(QRectF(op.dx, op.dy, op.dw, op.dh), *snapshot, source);
        } else {
            canvas_content(p, op, &target);
        }
    }

    // globalAlpha scales the source before it is composited, whatever the mode; QPainter's opacity
    // would instead blend the composited result back over the destination.
    auto faded = [&](const QImage& image) {
        if (op.alpha >= 1) return image;
        QImage out = make_image(w, h);
        QPainter p(&out);
        p.setOpacity(std::max(op.alpha, 0.0f));
        p.drawImage(0, 0, image);
        return out;
    };

    if (op.has_shadow()) {
        // The shadow is cast in canvas px whatever the transform, and composited before the content.
        QImage alpha = content.convertToFormat(QImage::Format_Alpha8);
        blur_alpha(alpha, std::max(op.shadow_blur / 2, 0.0f));
        QImage shadow = make_image(w, h);
        {
            QPainter p(&shadow);
            p.drawImage(QPointF(op.shadow_x, op.shadow_y), tint(alpha, qcolor(op.shadow_color)));
        }
        composite(target, faded(shadow), op.composite, clip ? &*clip : nullptr);
    }
    composite(target, faded(content), op.composite, clip ? &*clip : nullptr);
}

void QtBackend::canvas_run(int id, int w, int h, const std::vector<CanvasOp>& ops) {
    if (w <= 0 || h <= 0) {
        canvases_.erase(id);
        return;
    }
    int64_t others = 0;
    for (const auto& [other, bitmap] : canvases_)
        if (other != id) others += int64_t(bitmap.width()) * bitmap.height();
    if (others + int64_t(w) * h > MAX_CANVAS_PIXELS) {
        canvases_.erase(id);
        return;
    }
    QImage& target = canvases_[id];
    if (target.width() != w || target.height() != h || target.isNull()) target = make_image(w, h);
    for (const CanvasOp& op : ops) canvas_op(target, op, id);
}

bool QtBackend::canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba) {
    rgba.assign(size_t(std::max(w, 0)) * std::max(h, 0) * 4, 0);
    auto it = canvases_.find(id);
    if (it == canvases_.end() || w <= 0 || h <= 0) return true;
    const QImage& canvas = it->second;
    int x0 = std::max(0, x), y0 = std::max(0, y), x1 = std::min(canvas.width(), x + w), y1 = std::min(canvas.height(), y + h);
    for (int row = y0; row < y1; row++) {
        auto* src = reinterpret_cast<const QRgb*>(canvas.constScanLine(row));
        uint8_t* dst = rgba.data() + (size_t(row - y) * w + (x0 - x)) * 4;
        for (int col = x0; col < x1; col++, dst += 4) {
            // getImageData is straight alpha.
            QRgb c = qUnpremultiply(src[col]);
            dst[0] = uint8_t(qRed(c));
            dst[1] = uint8_t(qGreen(c));
            dst[2] = uint8_t(qBlue(c));
            dst[3] = uint8_t(qAlpha(c));
        }
    }
    return true;
}

void QtBackend::canvas_release(int id) { canvases_.erase(id); }

// ───────────────────────── display list ─────────────────────────

void QtBackend::paint(const DisplayList& list, QImage& target, const QTransform& transform, const std::vector<Rect>* damage) {
    if (!damage) {
        paint_area(list, target, transform, nullptr);
        return;
    }
    // Damage in device px, whole pixels around it. Each area is its own pass under a plain rectangle
    // clip: a clip region of several rectangles makes the raster engine scale antialiased coverage,
    // and the edges would come out a shade off a whole repaint.
    QRegion region;
    for (const Rect& r : *damage) region += transform.mapRect(QRectF(r.x, r.y, r.w, r.h)).toAlignedRect().adjusted(-1, -1, 1, 1);
    region &= target.rect();
    if (region.isEmpty()) return;
    if (region.rectCount() > 4) {
        QRect all = region.boundingRect();
        paint_area(list, target, transform, &all);
        return;
    }
    for (const QRect& r : region) paint_area(list, target, transform, &r);
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
}

void QtBackend::trim() {
    rasters_.clear();
    raster_pixels_ = 0;
}

bool QtBackend::draw_raster(QPainter& p, QPoint origin, const QTransform& device, const Command& push, std::span<const Command> inner) {
    if (device.type() > QTransform::TxScale) return false;
    QRect bounds = device.mapRect(QRectF(push.rect.x, push.rect.y, push.rect.w, push.rect.h)).toAlignedRect();
    if (bounds.isEmpty() || int64_t(bounds.width()) * bounds.height() > MAX_RASTER_PIXELS / 4) return false;
    QTransform to_raster = device * QTransform::fromTranslate(-bounds.x(), -bounds.y());
    auto q = [](qreal v) { return int64_t(std::llround(v * 1024)); };
    RasterKey key{push.key, q(to_raster.m11()), q(to_raster.m22()), q(to_raster.dx()), q(to_raster.dy())};
    auto it = rasters_.find(key);
    if (it == rasters_.end()) {
        int64_t pixels = int64_t(bounds.width()) * bounds.height();
        while (!rasters_.empty() && raster_pixels_ + pixels > MAX_RASTER_PIXELS) {
            auto oldest = rasters_.begin();
            for (auto i = rasters_.begin(); i != rasters_.end(); ++i)
                if (i->second.used < oldest->second.used) oldest = i;
            raster_pixels_ -= int64_t(oldest->second.image.width()) * oldest->second.image.height();
            rasters_.erase(oldest);
        }
        Raster raster;
        raster.image = make_image(bounds.width(), bounds.height());
        paint_area(inner, raster.image, to_raster, nullptr);
        raster_pixels_ += pixels;
        it = rasters_.emplace(key, std::move(raster)).first;
    }
    it->second.used = ++raster_clock_;
    p.save();
    p.resetTransform();
    p.drawImage(bounds.topLeft() - origin, it->second.image);
    p.restore();
    return true;
}

void QtBackend::paint_area(std::span<const Command> list, QImage& target, const QTransform& transform, const QRect* area_only) {
    QPainter root(&target);
    hints(root);
    QRect limit = target.rect();
    if (area_only) {
        root.setClipRect(*area_only);
        root.setCompositionMode(QPainter::CompositionMode_Source);
        root.fillRect(*area_only, Qt::transparent);
        root.setCompositionMode(QPainter::CompositionMode_SourceOver);
        limit = *area_only;
    }
    root.setTransform(transform);

    // The raster engine clips without antialiasing, so a rounded or path clip and an opacity group
    // each draw into a layer, masked by an antialiased coverage and composited on the way out. Layers
    // are in the target's px; `origin` places one there.
    struct Layer {
        QImage image;
        std::unique_ptr<QPainter> painter;
        QPoint origin;
        QImage mask;
        qreal opacity = 1;
        const Filters* filters = nullptr;  // a filter layer: run over its pixels when it closes
        float scale = 1;                   // device px per CSS px, for the filters' lengths
    };
    struct Entry {
        QPainter* saved = nullptr;
        std::unique_ptr<Layer> layer;
        bool hidden = false;
    };
    std::vector<Entry> stack;
    std::vector<Layer*> layers;
    int hidden = 0;

    auto current = [&]() -> QPainter& { return layers.empty() ? root : *layers.back()->painter; };
    auto origin = [&] { return layers.empty() ? QPoint() : layers.back()->origin; };
    // Layers sit where a whole paint puts them, whatever the damage: glyphs are placed on the device's
    // pixel grid, and a layer at another origin would draw its text a pixel off. A damaged area only
    // clips what a layer draws.
    auto area = [&] { return layers.empty() ? target.rect() : QRect(layers.back()->origin, layers.back()->image.size()); };
    // User space → target px.
    auto device = [&] { return current().transform() * QTransform::fromTranslate(origin().x(), origin().y()); };

    auto push_layer = [&](Entry& e, QRect bounds, const QPainterPath* mask, qreal opacity) {
        bounds &= area();
        if (bounds.isEmpty()) {
            e.hidden = true;
            hidden++;
            return;
        }
        auto layer = std::make_unique<Layer>();
        layer->image = make_image(bounds.width(), bounds.height());
        layer->origin = bounds.topLeft();
        layer->opacity = opacity;
        if (mask) layer->mask = coverage(mask->translated(-bounds.topLeft()), bounds.width(), bounds.height());
        QTransform to_layer = device() * QTransform::fromTranslate(-bounds.x(), -bounds.y());
        layer->painter = std::make_unique<QPainter>(&layer->image);
        hints(*layer->painter);
        if (area_only) layer->painter->setClipRect(limit.translated(-bounds.topLeft()));
        layer->painter->setTransform(to_layer);
        layers.push_back(layer.get());
        e.layer = std::move(layer);
    };

    auto pop = [&] {
        if (stack.empty()) return;
        Entry e = std::move(stack.back());
        stack.pop_back();
        if (e.layer) {
            layers.pop_back();
            Layer& l = *e.layer;
            l.painter->end();
            if (l.filters) filter_pixels(l.image.bits(), l.image.width(), l.image.height(), size_t(l.image.bytesPerLine()), QSysInfo::ByteOrder == QSysInfo::LittleEndian, *l.filters, l.scale);
            if (!l.mask.isNull()) keep(l.image, l.mask);
            QPainter& under = current();
            under.save();
            under.resetTransform();
            under.setOpacity(under.opacity() * l.opacity);
            under.drawImage(l.origin - origin(), l.image);
            under.restore();
        }
        if (e.saved) e.saved->restore();
        if (e.hidden) hidden--;
    };

    auto push_clip = [&](const Rect& r, const Radii& q) {
        Entry e;
        if (!q.any() && device().isAffine() && device().type() <= QTransform::TxScale) {
            e.saved = &current();
            e.saved->save();
            e.saved->setClipRect(qrect(r), Qt::IntersectClip);
        } else {
            QPainterPath shape = device().map(rounded(r, q));
            push_layer(e, shape.boundingRect().toAlignedRect(), &shape, 1);
        }
        stack.push_back(std::move(e));
    };

    for (size_t i = 0; i < list.size(); i++) {
        const Command& c = list[i];
        QPainter& p = current();
        switch (c.kind) {
        case Cmd::ClipPush:
            if (c.path) {
                Entry e;
                QPainterPath shape = device().map(geometry(c.path->ops, c.path->points, c.path->evenodd));
                push_layer(e, shape.boundingRect().toAlignedRect(), &shape, 1);
                stack.push_back(std::move(e));
            } else {
                push_clip(c.rect, c.radii);
            }
            continue;
        case Cmd::ClipPop:
        case Cmd::GroupPop:
        case Cmd::FilterPop:
            pop();
            continue;
        case Cmd::FilterPush: {
            // What the element paints, on a layer as wide as the filters spread, filtered as it closes.
            Entry e;
            if (c.filter && !c.filter->empty() && hidden == 0) {
                FilterReach reach = filter_reach(*c.filter);
                QRectF box(c.rect.x - reach.left, c.rect.y - reach.top, c.rect.w + reach.left + reach.right, c.rect.h + reach.top + reach.bottom);
                QTransform d = device();
                push_layer(e, d.mapRect(box).toAlignedRect(), nullptr, 1);
                if (e.layer) {
                    e.layer->filters = c.filter.get();
                    e.layer->scale = float(std::sqrt(std::fabs(d.determinant())));
                }
            }
            stack.push_back(std::move(e));
            continue;
        }
        case Cmd::GroupPush: {
            if (c.key && !c.hidden && c.opacity >= 1 && hidden == 0) {
                size_t end = group_end(list, i);
                if (end < list.size() && draw_raster(p, origin(), qmatrix(c.matrix) * device(), c, list.subspan(i + 1, end - i - 1))) {
                    i = end;
                    continue;
                }
            }
            Entry e;
            e.saved = &p;
            p.save();
            p.setTransform(qmatrix(c.matrix), true);
            if (c.hidden) {
                e.hidden = true;
                hidden++;
            } else if (c.opacity < 1) {
                // An opacity group is composited once, so overlapping children do not darken.
                push_layer(e, area(), nullptr, std::max(c.opacity, 0.0f));
            }
            stack.push_back(std::move(e));
            continue;
        }
        default:
            break;
        }
        if (hidden > 0) continue;

        switch (c.kind) {
        case Cmd::Path:
            if (!c.path) break;
            {
                const Path& path = *c.path;
                QPainterPath shape = geometry(path.ops, path.points, path.evenodd);
                if (path.fill && path.gradient != 0 && !path.stops.empty()) {
                    if (path.gradient == 1) {
                        QLinearGradient g(path.x1, path.y1, path.x2, path.y2);
                        g.setStops(gradient_stops(path.stops));
                        p.fillPath(shape, g);
                    } else {
                        QRadialGradient g(QPointF(path.x1, path.y1), path.r, QPointF(path.fx, path.fy));
                        g.setStops(gradient_stops(path.stops));
                        p.fillPath(shape, g);
                    }
                } else if (path.fill && path.fill_color.a > 0) {
                    p.fillPath(shape, qcolor(path.fill_color));
                }
                if (path.stroke && path.stroke_width > 0) {
                    QBrush brush;
                    if (path.stroke_gradient == 1 && path.stroke_stops.size() >= 2) {
                        QLinearGradient g(path.sg[0], path.sg[1], path.sg[2], path.sg[3]);
                        g.setStops(gradient_stops(path.stroke_stops));
                        brush = QBrush(g);
                    } else if (path.stroke_gradient == 2 && path.stroke_stops.size() >= 2) {
                        QRadialGradient g(QPointF(path.sg[0], path.sg[1]), path.sg[4], QPointF(path.sg[5], path.sg[6]));
                        g.setStops(gradient_stops(path.stroke_stops));
                        brush = QBrush(g);
                    } else if (path.stroke_color.a > 0) {
                        brush = QBrush(qcolor(path.stroke_color));
                    }
                    if (brush.style() != Qt::NoBrush)
                        p.strokePath(shape, pen(brush, path.stroke_width, path.line_cap, path.line_join, path.miter_limit, path.dashes, path.dash_offset));
                }
            }
            break;
        case Cmd::Shadow: {
            // CSS box-shadow: the shape blurred with sigma = blur / 2, outside the border box (or inside the
            // padding box when inset), rastered in user space at the device scale and drawn transformed.
            if (c.rect.empty() || c.color.a == 0) break;
            float dx = c.ax, dy = c.ay, blur = std::max(c.bx, 0.0f), spread = c.by;
            float reach = blur * 1.5f + std::fabs(spread) + 2 + (c.inset ? std::fabs(dx) + std::fabs(dy) : 0);
            QRectF ink = qrect(c.rect).adjusted(-reach, -reach, reach, reach);
            QTransform d = device();
            float scale = std::max(1.0f, float(std::sqrt(std::fabs(d.determinant()))));
            int w = int(std::ceil(ink.width() * scale)), h = int(std::ceil(ink.height() * scale));
            if (w <= 0 || h <= 0 || int64_t(w) * h > (int64_t(16) << 20)) break;

            auto inflate = [](const Rect& r, float by) { return Rect{r.x - by, r.y - by, r.w + by * 2, r.h + by * 2}; };
            auto grow = [](const Radii& q, float by) {
                auto g = [by](float v) { return std::max(v + by, 0.0f); };
                return Radii{g(q.tlx), g(q.tly), g(q.trx), g(q.try_), g(q.brx), g(q.bry), g(q.blx), g(q.bly)};
            };
            Rect shape = inflate(c.rect, c.inset ? -spread : spread);
            Radii shape_radii = c.radii.any() ? grow(c.radii, c.inset ? -spread : spread) : c.radii;
            QTransform to_image = QTransform::fromTranslate(-ink.x(), -ink.y()) * QTransform::fromScale(scale, scale);

            QPainterPath fill;
            if (c.inset) {
                fill.addRect(ink);
                if (!shape.empty()) fill = fill.subtracted(rounded(shape, shape_radii));
            } else if (!shape.empty()) {
                fill = rounded(shape, shape_radii);
            }
            QImage alpha = coverage(to_image.map(fill), w, h);
            blur_alpha(alpha, blur / 2 * scale);
            QImage image = tint(alpha, qcolor(c.color));

            // The shadow shows outside the border box, or inside it when inset; the image lands at the offset.
            QPainterPath box = (QTransform::fromTranslate(-dx, -dy) * to_image).map(rounded(c.rect, c.radii));
            QImage box_mask = coverage(box, w, h);
            if (c.inset) {
                keep(image, box_mask);
            } else {
                QPainter cut(&image);
                cut.setCompositionMode(QPainter::CompositionMode_DestinationOut);
                cut.drawImage(0, 0, box_mask);
            }
            p.drawImage(ink.translated(dx, dy), image);
            break;
        }
        case Cmd::Backdrop: {
            // What is painted under the box so far, filtered, back inside it. A blur reads around the box.
            auto* image = dynamic_cast<QImage*>(p.device());
            if (!image || !c.filter || c.filter->empty() || c.rect.empty()) break;
            QTransform d = device();
            if (d.type() > QTransform::TxScale) break;
            float spread = 0;
            for (const Filter& f : *c.filter)
                if (f.kind == Filter::Blur) spread += 3 * f.amount;
            QRect from = d.mapRect(QRectF(c.rect.x - spread, c.rect.y - spread, c.rect.w + 2 * spread, c.rect.h + 2 * spread)).toAlignedRect();
            from = from.translated(-origin()) & image->rect();
            if (from.isEmpty()) break;
            QImage copy = image->copy(from).convertToFormat(QImage::Format_ARGB32_Premultiplied);
            filter_pixels(copy.bits(), copy.width(), copy.height(), size_t(copy.bytesPerLine()), QSysInfo::ByteOrder == QSysInfo::LittleEndian, *c.filter,
                          float(std::sqrt(std::fabs(d.determinant()))));
            p.save();
            p.setClipPath(rounded(c.rect, c.radii), Qt::IntersectClip);
            p.resetTransform();
            p.setCompositionMode(QPainter::CompositionMode_Source);
            p.drawImage(from.topLeft(), copy);
            p.restore();
            break;
        }
        case Cmd::FillRect:
            if (c.has_clip) {
                p.save();
                p.setClipRect(qrect(c.clip), Qt::IntersectClip);
            }
            p.fillPath(rounded(c.rect, c.radii), qcolor(c.color));
            if (c.has_clip) p.restore();
            break;
        case Cmd::StrokeRect:
            p.strokePath(rounded(c.rect, c.radii), QPen(qcolor(c.color), c.width, Qt::SolidLine, Qt::FlatCap, Qt::MiterJoin));
            break;
        case Cmd::FillEllipse: {
            QPainterPath e;
            e.addEllipse(qrect(c.rect));
            p.fillPath(e, qcolor(c.color));
            break;
        }
        case Cmd::StrokeEllipse: {
            QPainterPath e;
            e.addEllipse(qrect(c.rect).adjusted(c.width / 2, c.width / 2, -c.width / 2, -c.width / 2));
            p.strokePath(e, QPen(qcolor(c.color), c.width));
            break;
        }
        case Cmd::Text: {
            Face* f = face(c.font);
            if (!f) break;
            p.setFont(f->font);
            p.setPen(qcolor(c.color));
            p.drawText(QPointF(c.x, c.y), QString::fromUtf8(c.text.data(), qsizetype(c.text.size())));
            break;
        }
        case Cmd::Image: {
            if (c.rect.empty()) break;
            const QImage* image = nullptr;
            if (c.canvas) {
                auto it = canvases_.find(c.canvas);
                if (it != canvases_.end()) image = &it->second;
            } else {
                auto it = images_.find(c.image);
                if (it != images_.end()) image = &it->second;
            }
            if (!image) break;
            if (c.has_clip) push_clip(c.clip, {});
            // The border box with its radii is the clip the layer asks for; the clip rect above bounds it.
            if (c.radii.any()) push_clip(c.has_clip ? c.clip : c.rect, c.radii);
            if (hidden == 0) current().drawImage(qrect(c.rect), *image);
            if (c.radii.any()) pop();
            if (c.has_clip) pop();
            break;
        }
        case Cmd::Linear:
        case Cmd::Radial:
        case Cmd::Conic: {
            if (c.stops.empty()) break;
            if (c.has_clip) {
                p.save();
                p.setClipRect(qrect(c.clip), Qt::IntersectClip);
            }
            QBrush brush;
            if (c.kind == Cmd::Linear) {
                QLinearGradient g(c.ax, c.ay, c.bx, c.by);
                g.setStops(gradient_stops(c.stops));
                brush = QBrush(g);
            } else if (c.kind == Cmd::Radial) {
                float rx = std::max(c.bx, 0.01f), ry = std::max(c.by, 0.01f);
                QRadialGradient g(QPointF(c.ax, c.ay), rx);
                g.setStops(gradient_stops(c.stops));
                brush = QBrush(g);
                brush.setTransform(QTransform::fromTranslate(-c.ax, -c.ay) * QTransform::fromScale(1, ry / rx) * QTransform::fromTranslate(c.ax, c.ay));
            } else {
                // CSS turns clockwise from the top, Qt counter-clockwise from the right: the stops run backwards.
                QConicalGradient g(QPointF(c.ax, c.ay), 90 - c.bx);
                g.setStops(gradient_stops(c.stops, true));
                brush = QBrush(g);
            }
            p.fillPath(rounded(c.rect, c.radii), brush);
            if (c.has_clip) p.restore();
            break;
        }
        default:
            break;
        }
    }

    while (!stack.empty()) pop();
}

} // namespace twlite
