#include "d2d_backend.h"
#include "filters.h"
#include "png.h"

#include <d2d1effects.h>
#include <dwrite_1.h>

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>

namespace twlite
{
    namespace
    {
        // A layout's glyph runs as outlines, each at its baseline origin, for strokeText.
        struct GlyphOutlines : winrt::implements<GlyphOutlines, IDWriteTextRenderer>
        {
            explicit GlyphOutlines(ID2D1Factory* factory) : m_factory(factory) {}

            std::vector<winrt::com_ptr<ID2D1Geometry>> runs;

            HRESULT __stdcall IsPixelSnappingDisabled(void*, BOOL* disabled) noexcept override
            {
                *disabled = TRUE;
                return S_OK;
            }
            HRESULT __stdcall GetCurrentTransform(void*, DWRITE_MATRIX* transform) noexcept override
            {
                *transform = DWRITE_MATRIX{ 1, 0, 0, 1, 0, 0 };
                return S_OK;
            }
            HRESULT __stdcall GetPixelsPerDip(void*, FLOAT* pixels) noexcept override
            {
                *pixels = 1;
                return S_OK;
            }
            HRESULT __stdcall DrawGlyphRun(void*, FLOAT x, FLOAT y, DWRITE_MEASURING_MODE, DWRITE_GLYPH_RUN const* run, DWRITE_GLYPH_RUN_DESCRIPTION const*, IUnknown*) noexcept override
            {
                winrt::com_ptr<ID2D1PathGeometry> path;
                winrt::com_ptr<ID2D1GeometrySink> sink;
                if (FAILED(m_factory->CreatePathGeometry(path.put())) || FAILED(path->Open(sink.put())))
                {
                    return S_OK;
                }
                HRESULT hr = run->fontFace->GetGlyphRunOutline(run->fontEmSize, run->glyphIndices, run->glyphAdvances, run->glyphOffsets, run->glyphCount,
                    run->isSideways, run->bidiLevel % 2, sink.get());
                if (FAILED(hr) || FAILED(sink->Close()))
                {
                    return S_OK;
                }
                winrt::com_ptr<ID2D1TransformedGeometry> placed;
                if (SUCCEEDED(m_factory->CreateTransformedGeometry(path.get(), D2D1::Matrix3x2F::Translation(x, y), placed.put())))
                {
                    runs.push_back(placed);
                }
                return S_OK;
            }
            HRESULT __stdcall DrawUnderline(void*, FLOAT, FLOAT, DWRITE_UNDERLINE const*, IUnknown*) noexcept override { return S_OK; }
            HRESULT __stdcall DrawStrikethrough(void*, FLOAT, FLOAT, DWRITE_STRIKETHROUGH const*, IUnknown*) noexcept override { return S_OK; }
            HRESULT __stdcall DrawInlineObject(void*, FLOAT, FLOAT, IDWriteInlineObject*, BOOL, BOOL, IUnknown*) noexcept override { return S_OK; }

        private:
            ID2D1Factory* m_factory;
        };

        std::wstring widen(const std::string& s)
        {
            if (s.empty())
            {
                return {};
            }

            int length = MultiByteToWideChar(CP_UTF8, 0, s.data(), int(s.size()), nullptr, 0);
            std::wstring out(size_t(length), L'\0');
            MultiByteToWideChar(CP_UTF8, 0, s.data(), int(s.size()), out.data(), length);
            return out;
        }

        std::string lower(std::string s)
        {
            for (auto& c : s) c = char(std::tolower((unsigned char)c));
            return s;
        }

        std::string trim(std::string s)
        {
            auto ws = [](char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '"' || c == '\''; };
            while (!s.empty() && ws(s.front())) s.erase(s.begin());
            while (!s.empty() && ws(s.back())) s.pop_back();
            return s;
        }

        std::vector<uint8_t> base64_decode(const std::string& in)
        {
            static int T[256];
            static bool init = false;
            if (!init)
            {
                for (int& t : T) t = -1;
                const char* a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
                for (int i = 0; i < 64; i++) T[(unsigned char)a[i]] = i;
                T[(unsigned char)'-'] = 62;
                T[(unsigned char)'_'] = 63;
                init = true;
            }

            std::vector<uint8_t> out;
            out.reserve(in.size() * 3 / 4);
            int val = 0, bits = -8;
            for (unsigned char c : in)
            {
                if (T[c] == -1) continue;
                val = (val << 6) + T[c];
                bits += 6;
                if (bits >= 0)
                {
                    out.push_back(uint8_t((val >> bits) & 0xFF));
                    bits -= 8;
                }
            }
            return out;
        }

        std::string percent_decode(const std::string& in)
        {
            std::string out;
            for (size_t i = 0; i < in.size(); i++)
            {
                if (in[i] == '%' && i + 2 < in.size())
                {
                    out.push_back(char(std::strtol(in.substr(i + 1, 2).c_str(), nullptr, 16)));
                    i += 2;
                }
                else
                {
                    out.push_back(in[i]);
                }
            }
            return out;
        }

        D2D1_COLOR_F color(const Color& c)
        {
            return D2D1::ColorF(c.r / 255.0f, c.g / 255.0f, c.b / 255.0f, c.a / 255.0f);
        }

        D2D1_RECT_F rect(const Rect& r)
        {
            return D2D1::RectF(r.x, r.y, r.x + r.w, r.y + r.h);
        }

        D2D1_COLOR_F lerp(const Color& a, const Color& b, float t)
        {
            auto mix = [t](uint8_t x, uint8_t y) { return (x + (y - x) * t) / 255.0f; };
            return D2D1::ColorF(mix(a.r, b.r), mix(a.g, b.g), mix(a.b, b.b), mix(a.a, b.a));
        }
    }

    D2DBackend::D2DBackend(ID2D1Device* device, IDWriteFactory* dwrite, IWICImagingFactory* wic)
        : m_device(device)
        , m_dwrite(dwrite)
        , m_wic(wic)
    {
        winrt::check_hresult(m_dwrite->GetSystemFontCollection(m_fonts.put()));
        m_device->GetFactory(m_factory.put());
    }

    std::wstring D2DBackend::resolve_family(const std::string& families)
    {
        size_t start = 0;
        while (start <= families.size())
        {
            size_t comma = families.find(',', start);
            std::string family = twlite::trim(families.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
            start = comma == std::string::npos ? families.size() + 1 : comma + 1;
            if (family.empty())
            {
                continue;
            }

            std::string l = lower(family);
            if (l == "-apple-system" || l == "blinkmacsystemfont" || l == "system-ui" || l == "ui-sans-serif" || l == "roboto" || l == "inter")
            {
                return L"Segoe UI";
            }
            // As in every Windows browser: Arial keeps Helvetica's metrics, which widgets are measured with.
            if (l == "sans-serif" || l == "helvetica" || l == "helvetica neue") return L"Arial";
            if (l == "serif" || l == "ui-serif") return L"Times New Roman";
            if (l == "monospace" || l == "ui-monospace" || l == "menlo" || l == "sf mono") return L"Consolas";
            if (l == "cursive") return L"Segoe Script";
            if (l == "fantasy") return L"Impact";
            if (l == "emoji" || l == "apple color emoji") return L"Segoe UI Emoji";

            std::wstring name = widen(family);
            UINT32 index;
            BOOL exists = FALSE;
            if (SUCCEEDED(m_fonts->FindFamilyName(name.c_str(), &index, &exists)) && exists)
            {
                return name;
            }
        }

        return L"Segoe UI";
    }

    int D2DBackend::create_font(const std::string& families, float size, int weight, bool italic, const std::string& features, FontMetrics& out)
    {
        FaceKey key{ families, size, weight, italic, features };
        auto it = m_faces.find(key);
        if (it == m_faces.end())
        {
            std::wstring family = resolve_family(families);
            auto dwriteWeight = DWRITE_FONT_WEIGHT(std::clamp(weight, 1, 999));
            auto style = italic ? DWRITE_FONT_STYLE_ITALIC : DWRITE_FONT_STYLE_NORMAL;

            auto face = std::make_shared<Face>();
            if (FAILED(m_dwrite->CreateTextFormat(family.c_str(), m_fonts.get(), dwriteWeight, style, DWRITE_FONT_STRETCH_NORMAL, size, L"en-us", face->format.put())))
            {
                return 0;
            }

            face->format->SetWordWrapping(DWRITE_WORD_WRAPPING_NO_WRAP);

            for (size_t start = 0; start < features.size();)
            {
                size_t end = std::min(features.find(',', start), features.size());
                std::string_view entry(features.data() + start, end - start);
                start = end + 1;
                if (entry.size() < 6 || entry[4] != '=')
                {
                    continue;
                }
                if (!face->typography && FAILED(m_dwrite->CreateTypography(face->typography.put())))
                {
                    break;
                }
                auto tag = DWRITE_FONT_FEATURE_TAG(uint32_t(uint8_t(entry[0])) | uint32_t(uint8_t(entry[1])) << 8 | uint32_t(uint8_t(entry[2])) << 16 | uint32_t(uint8_t(entry[3])) << 24);
                face->typography->AddFontFeature({ tag, UINT32(std::strtoul(std::string(entry.substr(5)).c_str(), nullptr, 10)) });
            }

            UINT32 index;
            BOOL exists = FALSE;
            winrt::com_ptr<IDWriteFontFamily> fontFamily;
            winrt::com_ptr<IDWriteFont> font;
            if (SUCCEEDED(m_fonts->FindFamilyName(family.c_str(), &index, &exists)) && exists
                && SUCCEEDED(m_fonts->GetFontFamily(index, fontFamily.put()))
                && SUCCEEDED(fontFamily->GetFirstMatchingFont(dwriteWeight, DWRITE_FONT_STRETCH_NORMAL, style, font.put())))
            {
                DWRITE_FONT_METRICS m;
                font->GetMetrics(&m);
                float scale = size / m.designUnitsPerEm;
                face->metrics.ascent = m.ascent * scale;
                face->metrics.descent = m.descent * scale;
                face->metrics.height = (m.ascent + m.descent + m.lineGap) * scale;
                face->metrics.x_height = m.xHeight * scale;

                // sTypoAscender and sTypoDescender, big-endian at 68 and 70 in the OS/2 table.
                winrt::com_ptr<IDWriteFontFace> fontFace;
                const void* table = nullptr;
                UINT32 length = 0;
                void* context = nullptr;
                BOOL found = FALSE;
                if (SUCCEEDED(font->CreateFontFace(fontFace.put()))
                    && SUCCEEDED(fontFace->TryGetFontTable(DWRITE_MAKE_OPENTYPE_TAG('O', 'S', '/', '2'), &table, &length, &context, &found)) && found)
                {
                    auto bytes = static_cast<const uint8_t*>(table);
                    if (length >= 72)
                    {
                        float typoAscent = float(int16_t(bytes[68] << 8 | bytes[69]));
                        float typoDescent = -float(int16_t(bytes[70] << 8 | bytes[71]));
                        set_em_box(face->metrics, typoAscent, typoDescent, size);
                    }
                    fontFace->ReleaseFontTable(context);
                }
            }
            else
            {
                face->metrics.ascent = size * 0.8f;
                face->metrics.descent = size * 0.2f;
                face->metrics.height = size * 1.2f;
                face->metrics.x_height = size * 0.5f;
            }

            if (auto zero = layout(*face, "0"))
            {
                face->metrics.zero_width = zero->width;
            }

            it = m_faces.emplace(key, std::move(face)).first;
        }

        int handle = ++m_nextFont;
        m_handles[handle] = it->second;
        out = it->second->metrics;
        return handle;
    }

    void D2DBackend::delete_font(int font)
    {
        m_handles.erase(font);
    }

    const D2DBackend::Layout* D2DBackend::layout(Face& face, const std::string& text)
    {
        auto it = face.layouts.find(text);
        if (it != face.layouts.end())
        {
            return &it->second;
        }

        // Words repeat across rebuilds, but a widget that types a counter would grow this forever.
        if (face.layouts.size() > 4096)
        {
            face.layouts.clear();
        }

        std::wstring wide = widen(text);
        Layout entry;
        if (FAILED(m_dwrite->CreateTextLayout(wide.c_str(), UINT32(wide.size()), face.format.get(), 1e6f, 1e6f, entry.layout.put())))
        {
            return nullptr;
        }

        if (face.typography)
        {
            entry.layout->SetTypography(face.typography.get(), { 0, UINT32(wide.size()) });
        }

        DWRITE_TEXT_METRICS metrics;
        entry.layout->GetMetrics(&metrics);
        entry.width = metrics.widthIncludingTrailingWhitespace;

        DWRITE_LINE_METRICS line;
        UINT32 lines = 0;
        entry.baseline = SUCCEEDED(entry.layout->GetLineMetrics(&line, 1, &lines)) && lines > 0 ? line.baseline : face.metrics.ascent;

        return &face.layouts.emplace(text, std::move(entry)).first->second;
    }

    float D2DBackend::text_width(int font, const std::string& text)
    {
        auto it = m_handles.find(font);
        if (it == m_handles.end())
        {
            return 0;
        }

        auto entry = layout(*it->second, text);
        return entry ? entry->width : 0;
    }

    bool D2DBackend::load_image(int id, const std::string& url, float& w, float& h)
    {
        // data:[<mime>][;base64],<payload>
        size_t comma = url.find(',');
        if (url.rfind("data:", 0) != 0 || comma == std::string::npos)
        {
            return false;
        }

        std::string meta = url.substr(5, comma - 5);
        std::string payload = url.substr(comma + 1);

        // No SVG rasteriser here, as on Apple (litehtml has none either).
        if (meta.find("svg") != std::string::npos)
        {
            return false;
        }

        std::vector<uint8_t> bytes;
        if (meta.find(";base64") != std::string::npos)
        {
            bytes = base64_decode(payload);
        }
        else
        {
            std::string s = percent_decode(payload);
            bytes.assign(s.begin(), s.end());
        }

        if (bytes.empty())
        {
            return false;
        }

        // Decoded to an IWICBitmap now: the stream reads `bytes`, which do not outlive this call.
        winrt::com_ptr<IWICStream> stream;
        winrt::com_ptr<IWICBitmapDecoder> decoder;
        winrt::com_ptr<IWICBitmapFrameDecode> frame;
        winrt::com_ptr<IWICFormatConverter> converter;
        winrt::com_ptr<IWICBitmap> bitmap;
        if (FAILED(m_wic->CreateStream(stream.put()))
            || FAILED(stream->InitializeFromMemory(bytes.data(), DWORD(bytes.size())))
            || FAILED(m_wic->CreateDecoderFromStream(stream.get(), nullptr, WICDecodeMetadataCacheOnDemand, decoder.put()))
            || FAILED(decoder->GetFrame(0, frame.put()))
            || FAILED(m_wic->CreateFormatConverter(converter.put()))
            || FAILED(converter->Initialize(frame.get(), GUID_WICPixelFormat32bppPBGRA, WICBitmapDitherTypeNone, nullptr, 0, WICBitmapPaletteTypeMedianCut))
            || FAILED(m_wic->CreateBitmapFromSource(converter.get(), WICBitmapCacheOnLoad, bitmap.put())))
        {
            return false;
        }

        UINT width, height;
        bitmap->GetSize(&width, &height);
        w = float(width);
        h = float(height);

        // WIC carries on through a broken stream; Chromium leaves what follows the damage transparent.
        size_t intact = png_intact_rows(bytes.data(), bytes.size());
        if (intact < height)
        {
            WICRect rows{ 0, INT(intact), INT(width), INT(height - intact) };
            winrt::com_ptr<IWICBitmapLock> lock;
            UINT stride = 0, size = 0;
            BYTE* pixels = nullptr;
            if (SUCCEEDED(bitmap->Lock(&rows, WICBitmapLockWrite, lock.put())) && SUCCEEDED(lock->GetStride(&stride)) && SUCCEEDED(lock->GetDataPointer(&size, &pixels)))
            {
                for (UINT y = 0; y < UINT(rows.Height); y++)
                {
                    std::memset(pixels + size_t(y) * stride, 0, size_t(width) * 4);
                }
            }
        }

        m_images[id] = Image{ bitmap, nullptr };
        return true;
    }

    winrt::com_ptr<ID2D1Geometry> D2DBackend::rounded(const Rect& r, const Radii& q)
    {
        winrt::com_ptr<ID2D1PathGeometry> path;
        winrt::com_ptr<ID2D1GeometrySink> sink;
        winrt::check_hresult(m_factory->CreatePathGeometry(path.put()));
        winrt::check_hresult(path->Open(sink.put()));

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

        auto arc = [&](float x, float y, float rx, float ry)
        {
            if (rx > 0 && ry > 0)
            {
                sink->AddArc(D2D1::ArcSegment(D2D1::Point2F(x, y), D2D1::SizeF(rx, ry), 0, D2D1_SWEEP_DIRECTION_CLOCKWISE, D2D1_ARC_SIZE_SMALL));
            }
            else
            {
                sink->AddLine(D2D1::Point2F(x, y));
            }
        };

        sink->BeginFigure(D2D1::Point2F(x0 + tlx, y0), D2D1_FIGURE_BEGIN_FILLED);
        sink->AddLine(D2D1::Point2F(x1 - trx, y0));
        arc(x1, y0 + try_, trx, try_);
        sink->AddLine(D2D1::Point2F(x1, y1 - bry));
        arc(x1 - brx, y1, brx, bry);
        sink->AddLine(D2D1::Point2F(x0 + blx, y1));
        arc(x0, y1 - bly, blx, bly);
        sink->AddLine(D2D1::Point2F(x0, y0 + tly));
        arc(x0 + tlx, y0, tlx, tly);
        sink->EndFigure(D2D1_FIGURE_END_CLOSED);
        winrt::check_hresult(sink->Close());

        return path;
    }

    winrt::com_ptr<ID2D1GradientStopCollection> D2DBackend::stops(const std::vector<Stop>& list)
    {
        std::vector<D2D1_GRADIENT_STOP> converted;
        converted.reserve(list.size());
        for (const Stop& s : list)
        {
            converted.push_back({ std::clamp(s.offset, 0.0f, 1.0f), color(s.color) });
        }

        winrt::com_ptr<ID2D1GradientStopCollection> collection;
        if (converted.empty() || FAILED(m_ctx->CreateGradientStopCollection(converted.data(), UINT32(converted.size()), D2D1_GAMMA_2_2, D2D1_EXTEND_MODE_CLAMP, collection.put())))
        {
            return nullptr;
        }

        return collection;
    }

    // Direct2D has no conic gradient: wedges around the centre, each a flat colour from the stops.
    void D2DBackend::conic(const Command& c)
    {
        if (c.stops.empty())
        {
            return;
        }

        auto at = [&](float t)
        {
            if (t <= c.stops.front().offset) return color(c.stops.front().color);
            for (size_t i = 1; i < c.stops.size(); i++)
            {
                const Stop& a = c.stops[i - 1];
                const Stop& b = c.stops[i];
                if (t <= b.offset)
                {
                    float span = b.offset - a.offset;
                    return lerp(a.color, b.color, span > 0 ? (t - a.offset) / span : 1);
                }
            }
            return color(c.stops.back().color);
        };

        float radius = std::hypot(c.rect.w, c.rect.h) * 2;
        constexpr int kWedges = 120;
        constexpr float kPi = 3.14159265f;

        // CSS: degrees clockwise from the top; y points down here, so clockwise is +angle from -y.
        float start = (c.bx - 90.0f) * kPi / 180.0f;
        float step = 2 * kPi / kWedges;

        for (int i = 0; i < kWedges; i++)
        {
            // Half a step of overlap, or the antialiased seams show as spokes.
            float a0 = start + i * step;
            float a1 = a0 + step * 1.5f;

            winrt::com_ptr<ID2D1PathGeometry> path;
            winrt::com_ptr<ID2D1GeometrySink> sink;
            if (FAILED(m_factory->CreatePathGeometry(path.put())) || FAILED(path->Open(sink.put())))
            {
                return;
            }

            sink->BeginFigure(D2D1::Point2F(c.ax, c.ay), D2D1_FIGURE_BEGIN_FILLED);
            sink->AddLine(D2D1::Point2F(c.ax + radius * std::cos(a0), c.ay + radius * std::sin(a0)));
            sink->AddLine(D2D1::Point2F(c.ax + radius * std::cos(a1), c.ay + radius * std::sin(a1)));
            sink->EndFigure(D2D1_FIGURE_END_CLOSED);
            sink->Close();

            m_brush->SetColor(at((i + 0.5f) / kWedges));
            m_ctx->FillGeometry(path.get(), m_brush.get());
        }
    }

    winrt::com_ptr<ID2D1Geometry> D2DBackend::exclude(ID2D1Geometry* outer, ID2D1Geometry* inner)
    {
        winrt::com_ptr<ID2D1PathGeometry> path;
        winrt::com_ptr<ID2D1GeometrySink> sink;
        winrt::check_hresult(m_factory->CreatePathGeometry(path.put()));
        winrt::check_hresult(path->Open(sink.put()));
        winrt::check_hresult(outer->CombineWithGeometry(inner, D2D1_COMBINE_MODE_EXCLUDE, nullptr, sink.get()));
        winrt::check_hresult(sink->Close());
        return path;
    }

    winrt::com_ptr<ID2D1PathGeometry> D2DBackend::geometry(const std::vector<Path::Op>& ops, const std::vector<float>& points, bool evenodd)
    {
        winrt::com_ptr<ID2D1PathGeometry> geometry;
        winrt::com_ptr<ID2D1GeometrySink> sink;
        if (FAILED(m_factory->CreatePathGeometry(geometry.put())) || FAILED(geometry->Open(sink.put())))
        {
            return nullptr;
        }

        sink->SetFillMode(evenodd ? D2D1_FILL_MODE_ALTERNATE : D2D1_FILL_MODE_WINDING);

        const float* v = points.data();
        size_t n = points.size();
        size_t i = 0;
        bool open = false;
        for (auto op : ops)
        {
            size_t need = op == Path::Cubic ? 6 : op == Path::Quad ? 4 : op == Path::Close ? 0 : 2;
            if (i + need > n) break;
            switch (op)
            {
            case Path::Move:
                if (open) sink->EndFigure(D2D1_FIGURE_END_OPEN);
                sink->BeginFigure(D2D1::Point2F(v[i], v[i + 1]), D2D1_FIGURE_BEGIN_FILLED);
                open = true;
                break;
            case Path::Line:
                if (open) sink->AddLine(D2D1::Point2F(v[i], v[i + 1]));
                break;
            case Path::Quad:
                if (open) sink->AddQuadraticBezier(D2D1::QuadraticBezierSegment(D2D1::Point2F(v[i], v[i + 1]), D2D1::Point2F(v[i + 2], v[i + 3])));
                break;
            case Path::Cubic:
                if (open) sink->AddBezier(D2D1::BezierSegment(D2D1::Point2F(v[i], v[i + 1]), D2D1::Point2F(v[i + 2], v[i + 3]), D2D1::Point2F(v[i + 4], v[i + 5])));
                break;
            case Path::Close:
                if (open) sink->EndFigure(D2D1_FIGURE_END_CLOSED);
                open = false;
                break;
            }
            i += need;
        }

        if (open) sink->EndFigure(D2D1_FIGURE_END_OPEN);
        if (FAILED(sink->Close())) return nullptr;
        return geometry;
    }

    void D2DBackend::path(const Path& p)
    {
        auto geometry = this->geometry(p.ops, p.points, p.evenodd);
        if (!geometry) return;

        if (p.fill && p.gradient != 0 && !p.stops.empty())
        {
            std::vector<Stop> stopList = p.stops;
            if (auto collection = stops(stopList))
            {
                winrt::com_ptr<ID2D1Brush> brush;
                if (p.gradient == 1)
                {
                    winrt::com_ptr<ID2D1LinearGradientBrush> linear;
                    m_ctx->CreateLinearGradientBrush(D2D1::LinearGradientBrushProperties(D2D1::Point2F(p.x1, p.y1), D2D1::Point2F(p.x2, p.y2)), collection.get(), linear.put());
                    brush = linear;
                }
                else
                {
                    winrt::com_ptr<ID2D1RadialGradientBrush> radial;
                    m_ctx->CreateRadialGradientBrush(D2D1::RadialGradientBrushProperties(D2D1::Point2F(p.x1, p.y1), D2D1::Point2F(p.fx - p.x1, p.fy - p.y1), p.r, p.r), collection.get(), radial.put());
                    brush = radial;
                }

                if (brush) m_ctx->FillGeometry(geometry.get(), brush.get());
            }
        }
        else if (p.fill && p.fill_color.a > 0)
        {
            m_brush->SetColor(color(p.fill_color));
            m_ctx->FillGeometry(geometry.get(), m_brush.get());
        }

        if (p.stroke && p.stroke_color.a > 0 && p.stroke_width > 0)
        {
            D2D1_STROKE_STYLE_PROPERTIES props{};
            auto cap = p.line_cap == 1 ? D2D1_CAP_STYLE_ROUND : p.line_cap == 2 ? D2D1_CAP_STYLE_SQUARE : D2D1_CAP_STYLE_FLAT;
            props.startCap = props.endCap = props.dashCap = cap;
            // CoreGraphics and SVG fall back to a bevel past the miter limit.
            props.lineJoin = p.line_join == 1 ? D2D1_LINE_JOIN_ROUND : p.line_join == 2 ? D2D1_LINE_JOIN_BEVEL : D2D1_LINE_JOIN_MITER_OR_BEVEL;
            props.miterLimit = p.miter_limit;
            props.dashStyle = D2D1_DASH_STYLE_SOLID;

            // Direct2D measures dashes in stroke widths, SVG in user units.
            std::vector<float> dashes;
            bool dashed = false;
            for (float d : p.dashes)
            {
                dashes.push_back(d / p.stroke_width);
                dashed = dashed || d > 0;
            }

            if (dashed)
            {
                props.dashStyle = D2D1_DASH_STYLE_CUSTOM;
                props.dashOffset = p.dash_offset / p.stroke_width;
            }

            winrt::com_ptr<ID2D1StrokeStyle> style;
            m_factory->CreateStrokeStyle(props, dashed ? dashes.data() : nullptr, dashed ? UINT32(dashes.size()) : 0, style.put());

            m_brush->SetColor(color(p.stroke_color));
            m_ctx->DrawGeometry(geometry.get(), m_brush.get(), p.stroke_width, style.get());
        }
    }

    // CSS box-shadow: the shape blurred with sigma = blur / 2, outside the border box (or inside the
    // padding box when inset). Drawn the way twidget's LHContainer::draw_box_shadow does on CoreGraphics.
    void D2DBackend::shadow(const Command& c)
    {
        if (c.rect.empty())
        {
            return;
        }

        float dx = c.ax, dy = c.ay, blur = std::max(c.bx, 0.0f), spread = c.by;
        float reach = blur + std::fabs(spread) + std::max(std::fabs(dx), std::fabs(dy)) + 2;

        auto inflate = [](const Rect& r, float by) { return Rect{ r.x - by, r.y - by, r.w + by * 2, r.h + by * 2 }; };
        auto grow = [](const Radii& q, float by)
        {
            auto g = [by](float v) { return std::max(v + by, 0.0f); };
            return Radii{ g(q.tlx), g(q.tly), g(q.trx), g(q.try_), g(q.brx), g(q.bry), g(q.blx), g(q.bly) };
        };

        Rect shape = inflate(c.rect, c.inset ? -spread : spread);
        Radii shapeRadii = c.radii.any() ? grow(c.radii, c.inset ? -spread : spread) : c.radii;
        auto box = rounded(c.rect, c.radii);

        winrt::com_ptr<ID2D1Geometry> clip, fill;
        if (c.inset)
        {
            clip = box;
            winrt::com_ptr<ID2D1RectangleGeometry> frame;
            winrt::check_hresult(m_factory->CreateRectangleGeometry(rect(inflate(c.rect, reach * 2)), frame.put()));
            fill = shape.empty() ? winrt::com_ptr<ID2D1Geometry>(frame) : exclude(frame.get(), rounded(shape, shapeRadii).get());
        }
        else
        {
            winrt::com_ptr<ID2D1RectangleGeometry> ink;
            winrt::check_hresult(m_factory->CreateRectangleGeometry(rect(inflate(c.rect, reach + 1)), ink.put()));
            clip = exclude(ink.get(), box.get());
            if (shape.empty()) return;
            fill = rounded(shape, shapeRadii);
        }

        // Recorded on a second context: the main one is mid-draw on the composition surface.
        if (!m_recorder)
        {
            winrt::com_ptr<ID2D1Device> device;
            m_ctx->GetDevice(device.put());
            winrt::check_hresult(device->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, m_recorder.put()));
        }

        winrt::com_ptr<ID2D1CommandList> recorded;
        winrt::check_hresult(m_recorder->CreateCommandList(recorded.put()));
        m_recorder->SetTarget(recorded.get());
        m_recorder->BeginDraw();
        m_recorder->SetTransform(D2D1::Matrix3x2F::Translation(dx, dy));
        m_brush->SetColor(color(c.color));
        m_recorder->FillGeometry(fill.get(), m_brush.get());
        HRESULT hr = m_recorder->EndDraw();
        m_recorder->SetTarget(nullptr);
        if (FAILED(hr) || FAILED(recorded->Close()))
        {
            return;
        }

        if (!m_blur)
        {
            winrt::check_hresult(m_ctx->CreateEffect(CLSID_D2D1GaussianBlur, m_blur.put()));
        }

        m_blur->SetInput(0, recorded.get());
        m_blur->SetValue(D2D1_GAUSSIANBLUR_PROP_STANDARD_DEVIATION, blur / 2);

        m_ctx->PushLayer(D2D1::LayerParameters(D2D1::InfiniteRect(), clip.get()), nullptr);
        m_ctx->DrawImage(m_blur.get());
        m_ctx->PopLayer();
    }

    // ───────────────────────── canvas ─────────────────────────

    winrt::com_ptr<ID2D1Brush> D2DBackend::canvas_brush(ID2D1DeviceContext* ctx, const CanvasPaint& paint, float alpha)
    {
        winrt::com_ptr<ID2D1Brush> brush;
        if (paint.kind == CanvasPaint::Solid || paint.kind == CanvasPaint::Conic || paint.stops.empty())
        {
            // Direct2D has no conic brush: a conic gradient paints its first stop.
            Color c = paint.kind == CanvasPaint::Solid || paint.stops.empty() ? paint.color : paint.stops.front().color;
            winrt::com_ptr<ID2D1SolidColorBrush> solid;
            ctx->CreateSolidColorBrush(color(c), D2D1::BrushProperties(alpha), solid.put());
            return solid;
        }

        std::vector<D2D1_GRADIENT_STOP> converted;
        float r0 = paint.args[2], r1 = paint.args[5];
        for (const Stop& s : paint.stops)
        {
            float offset = std::clamp(s.offset, 0.0f, 1.0f);
            // Direct2D's radial gradient starts at radius 0: an inner circle squeezes the stops outwards.
            if (paint.kind == CanvasPaint::Radial && r1 > 0 && r0 > 0 && r0 < r1) offset = r0 / r1 + offset * (1 - r0 / r1);
            converted.push_back({ offset, color(s.color) });
        }

        winrt::com_ptr<ID2D1GradientStopCollection> collection;
        if (FAILED(ctx->CreateGradientStopCollection(converted.data(), UINT32(converted.size()), D2D1_GAMMA_2_2, D2D1_EXTEND_MODE_CLAMP, collection.put())))
        {
            return nullptr;
        }

        if (paint.kind == CanvasPaint::Linear)
        {
            winrt::com_ptr<ID2D1LinearGradientBrush> linear;
            ctx->CreateLinearGradientBrush(D2D1::LinearGradientBrushProperties(D2D1::Point2F(paint.args[0], paint.args[1]), D2D1::Point2F(paint.args[2], paint.args[3])),
                D2D1::BrushProperties(alpha), collection.get(), linear.put());
            brush = linear;
        }
        else
        {
            float x0 = paint.args[0], y0 = paint.args[1], x1 = paint.args[3], y1 = paint.args[4];
            winrt::com_ptr<ID2D1RadialGradientBrush> radial;
            ctx->CreateRadialGradientBrush(D2D1::RadialGradientBrushProperties(D2D1::Point2F(x1, y1), D2D1::Point2F(x0 - x1, y0 - y1), std::max(r1, 0.01f), std::max(r1, 0.01f)),
                D2D1::BrushProperties(alpha), collection.get(), radial.put());
            brush = radial;
        }

        return brush;
    }

    // Draws one canvas operation's content (no clip, shadow or compositing) into `ctx`, whose
    // transform it sets itself. `self` is the target's own bitmap, which an Image must not read.
    void D2DBackend::canvas_content(ID2D1DeviceContext* ctx, const CanvasOp& op, ID2D1Bitmap* self)
    {
        auto m = D2D1::Matrix3x2F(op.matrix[0], op.matrix[1], op.matrix[2], op.matrix[3], op.matrix[4], op.matrix[5]);
        auto inverse = m;
        bool invertible = inverse.Invert();

        switch (op.kind)
        {
        case CanvasOp::Fill:
        case CanvasOp::Stroke:
        {
            auto device = geometry(op.path.ops, op.path.points, op.evenodd);
            auto brush = canvas_brush(ctx, op.paint, op.alpha);
            if (!device || !brush || !invertible) return;

            // The path is in canvas px; drawing it in user space under the transform keeps the
            // gradient and the stroke width where the canvas spec puts them.
            winrt::com_ptr<ID2D1TransformedGeometry> user;
            if (FAILED(m_factory->CreateTransformedGeometry(device.get(), inverse, user.put()))) return;
            ctx->SetTransform(m);
            if (op.kind == CanvasOp::Fill)
            {
                ctx->FillGeometry(user.get(), brush.get());
                return;
            }

            D2D1_STROKE_STYLE_PROPERTIES props{};
            auto cap = op.line_cap == 1 ? D2D1_CAP_STYLE_ROUND : op.line_cap == 2 ? D2D1_CAP_STYLE_SQUARE : D2D1_CAP_STYLE_FLAT;
            props.startCap = props.endCap = props.dashCap = cap;
            props.lineJoin = op.line_join == 1 ? D2D1_LINE_JOIN_ROUND : op.line_join == 2 ? D2D1_LINE_JOIN_BEVEL : D2D1_LINE_JOIN_MITER_OR_BEVEL;
            props.miterLimit = op.miter_limit;
            props.dashStyle = D2D1_DASH_STYLE_SOLID;
            std::vector<float> dashes;
            bool dashed = false;
            for (float d : op.dashes)
            {
                dashes.push_back(d / op.line_width);
                dashed = dashed || d > 0;
            }
            if (dashed)
            {
                props.dashStyle = D2D1_DASH_STYLE_CUSTOM;
                props.dashOffset = op.dash_offset / op.line_width;
            }
            winrt::com_ptr<ID2D1StrokeStyle> style;
            m_factory->CreateStrokeStyle(props, dashed ? dashes.data() : nullptr, dashed ? UINT32(dashes.size()) : 0, style.put());
            ctx->DrawGeometry(user.get(), brush.get(), op.line_width, style.get());
            return;
        }
        case CanvasOp::Text:
        {
            auto it = m_handles.find(op.font);
            if (it == m_handles.end()) return;
            auto entry = layout(*it->second, op.text);
            auto brush = canvas_brush(ctx, op.paint, op.alpha);
            if (!entry || !brush) return;
            ctx->SetTransform(D2D1::Matrix3x2F::Scale(op.scale_x, 1) * D2D1::Matrix3x2F::Translation(op.x, op.y) * m);
            if (!op.stroke_text)
            {
                ctx->DrawTextLayout(D2D1::Point2F(op.text_dx, -entry->baseline), entry->layout.get(), brush.get(), D2D1_DRAW_TEXT_OPTIONS_ENABLE_COLOR_FONT);
                return;
            }

            auto outlines = winrt::make_self<GlyphOutlines>(m_factory.get());
            entry->layout->Draw(nullptr, outlines.get(), op.text_dx, -entry->baseline);
            D2D1_STROKE_STYLE_PROPERTIES props{};
            auto cap = op.line_cap == 1 ? D2D1_CAP_STYLE_ROUND : op.line_cap == 2 ? D2D1_CAP_STYLE_SQUARE : D2D1_CAP_STYLE_FLAT;
            props.startCap = props.endCap = props.dashCap = cap;
            props.lineJoin = op.line_join == 1 ? D2D1_LINE_JOIN_ROUND : op.line_join == 2 ? D2D1_LINE_JOIN_BEVEL : D2D1_LINE_JOIN_MITER_OR_BEVEL;
            props.miterLimit = op.miter_limit;
            props.dashStyle = D2D1_DASH_STYLE_SOLID;
            std::vector<float> dashes;
            bool dashed = false;
            for (float d : op.dashes)
            {
                dashes.push_back(d / op.line_width);
                dashed = dashed || d > 0;
            }
            if (dashed)
            {
                props.dashStyle = D2D1_DASH_STYLE_CUSTOM;
                props.dashOffset = op.dash_offset / op.line_width;
            }
            winrt::com_ptr<ID2D1StrokeStyle> style;
            m_factory->CreateStrokeStyle(props, dashed ? dashes.data() : nullptr, dashed ? UINT32(dashes.size()) : 0, style.put());
            for (const auto& run : outlines->runs)
            {
                ctx->DrawGeometry(run.get(), brush.get(), op.line_width, style.get());
            }
            return;
        }
        case CanvasOp::Image:
        {
            ID2D1Bitmap* bitmap = nullptr;
            if (op.canvas)
            {
                auto it = m_canvases.find(op.canvas);
                if (it != m_canvases.end()) bitmap = it->second.bitmap.get();
            }
            else
            {
                auto it = m_images.find(op.image);
                if (it != m_images.end())
                {
                    if (!it->second.bitmap) ctx->CreateBitmapFromWicBitmap(it->second.source.get(), nullptr, it->second.bitmap.put());
                    bitmap = it->second.bitmap.get();
                }
            }
            if (!bitmap || bitmap == self) return;
            ctx->SetTransform(m);
            D2D1_RECT_F source = op.sw < 0 ? D2D1::RectF(0, 0, float(bitmap->GetPixelSize().width), float(bitmap->GetPixelSize().height))
                                           : D2D1::RectF(op.sx, op.sy, op.sx + op.sw, op.sy + op.sh);
            ctx->DrawBitmap(bitmap, D2D1::RectF(op.dx, op.dy, op.dx + op.dw, op.dy + op.dh), op.alpha,
                op.smoothing ? D2D1_INTERPOLATION_MODE_HIGH_QUALITY_CUBIC : D2D1_INTERPOLATION_MODE_NEAREST_NEIGHBOR, &source);
            return;
        }
        default:
            return;
        }
    }

    void D2DBackend::canvas_op(const CanvasOp& op, int id)
    {
        CanvasBitmap& target = m_canvases[id];
        ID2D1DeviceContext* ctx = m_canvasCtx.get();

        if (op.kind == CanvasOp::Reset)
        {
            ctx->Clear(D2D1::ColorF(0, 0));
            return;
        }

        if (op.kind == CanvasOp::PutImage)
        {
            // Raw pixels, outside drawing: straight RGBA to premultiplied BGRA, clipped to the bitmap.
            int x0 = std::max(0, int(op.dx)), y0 = std::max(0, int(op.dy));
            int x1 = std::min(target.w, int(op.dx) + op.w), y1 = std::min(target.h, int(op.dy) + op.h);
            if (x1 <= x0 || y1 <= y0) return;
            std::vector<uint8_t> bgra(size_t(x1 - x0) * (y1 - y0) * 4);
            for (int y = y0; y < y1; y++)
            {
                const uint8_t* src = op.rgba->data() + (size_t(y - int(op.dy)) * op.w + (x0 - int(op.dx))) * 4;
                uint8_t* dst = bgra.data() + size_t(y - y0) * (x1 - x0) * 4;
                for (int x = x0; x < x1; x++, src += 4, dst += 4)
                {
                    uint32_t a = src[3];
                    dst[0] = uint8_t(src[2] * a / 255);
                    dst[1] = uint8_t(src[1] * a / 255);
                    dst[2] = uint8_t(src[0] * a / 255);
                    dst[3] = uint8_t(a);
                }
            }
            ctx->EndDraw();
            D2D1_RECT_U rect{ UINT32(x0), UINT32(y0), UINT32(x1), UINT32(y1) };
            target.bitmap->CopyFromMemory(&rect, bgra.data(), UINT32((x1 - x0) * 4));
            ctx->BeginDraw();
            return;
        }

        // An image drawn onto its own canvas reads a copy taken before the draw.
        winrt::com_ptr<ID2D1Bitmap1> snapshot;
        CanvasOp own = op;
        if (op.kind == CanvasOp::Image && op.canvas == id)
        {
            ctx->EndDraw();
            D2D1_BITMAP_PROPERTIES1 props = D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_NONE, D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED));
            if (SUCCEEDED(ctx->CreateBitmap(D2D1::SizeU(target.w, target.h), nullptr, 0, props, snapshot.put())))
            {
                snapshot->CopyFromBitmap(nullptr, target.bitmap.get(), nullptr);
            }
            ctx->BeginDraw();
            if (!snapshot) return;
            m_canvases[-id] = CanvasBitmap{ snapshot, target.w, target.h };
            own.canvas = -id;
        }

        // Clips: each one a geometry layer in canvas px.
        std::vector<winrt::com_ptr<ID2D1PathGeometry>> clips;
        if (op.clips)
        {
            for (const CanvasClip& clip : *op.clips)
            {
                auto g = geometry(clip.path.ops, clip.path.points, clip.evenodd);
                if (!g) return;
                clips.push_back(std::move(g));
            }
        }

        ctx->SetTransform(D2D1::Matrix3x2F::Identity());
        if (op.kind == CanvasOp::Clear)
        {
            // Clearing writes transparent pixels: a copy blend, clipped by intersecting the
            // geometries rather than through layers, which would composite the transparency over.
            winrt::com_ptr<ID2D1Geometry> area = geometry(op.path.ops, op.path.points, false);
            for (auto& clip : clips)
            {
                winrt::com_ptr<ID2D1PathGeometry> both;
                winrt::com_ptr<ID2D1GeometrySink> sink;
                if (!area || FAILED(m_factory->CreatePathGeometry(both.put())) || FAILED(both->Open(sink.put()))) return;
                area->CombineWithGeometry(clip.get(), D2D1_COMBINE_MODE_INTERSECT, nullptr, sink.get());
                sink->Close();
                area = both;
            }
            if (!area) return;
            m_canvasBrush->SetColor(D2D1::ColorF(0, 0));
            ctx->SetPrimitiveBlend(D2D1_PRIMITIVE_BLEND_COPY);
            ctx->FillGeometry(area.get(), m_canvasBrush.get());
            ctx->SetPrimitiveBlend(D2D1_PRIMITIVE_BLEND_SOURCE_OVER);
            return;
        }

        for (auto& clip : clips)
        {
            ctx->PushLayer(D2D1::LayerParameters1(D2D1::InfiniteRect(), clip.get()), nullptr);
        }

        static const D2D1_COMPOSITE_MODE modes[] = {
            D2D1_COMPOSITE_MODE_SOURCE_OVER, D2D1_COMPOSITE_MODE_SOURCE_IN, D2D1_COMPOSITE_MODE_SOURCE_OUT, D2D1_COMPOSITE_MODE_SOURCE_ATOP,
            D2D1_COMPOSITE_MODE_DESTINATION_OVER, D2D1_COMPOSITE_MODE_DESTINATION_IN, D2D1_COMPOSITE_MODE_DESTINATION_OUT, D2D1_COMPOSITE_MODE_DESTINATION_ATOP,
            D2D1_COMPOSITE_MODE_PLUS, D2D1_COMPOSITE_MODE_SOURCE_COPY, D2D1_COMPOSITE_MODE_XOR,
        };
        // Blend modes (multiply … luminosity) need the destination as an effect input; they draw as source-over.
        size_t index = size_t(op.composite);
        D2D1_COMPOSITE_MODE mode = index < std::size(modes) ? modes[index] : D2D1_COMPOSITE_MODE_SOURCE_OVER;
        bool shadow = op.has_shadow();

        if (mode == D2D1_COMPOSITE_MODE_SOURCE_OVER && !shadow)
        {
            canvas_content(ctx, own, target.bitmap.get());
        }
        else
        {
            winrt::com_ptr<ID2D1CommandList> recorded;
            if (SUCCEEDED(m_canvasRecorder->CreateCommandList(recorded.put())))
            {
                m_canvasRecorder->SetTarget(recorded.get());
                m_canvasRecorder->BeginDraw();
                canvas_content(m_canvasRecorder.get(), own, target.bitmap.get());
                m_canvasRecorder->SetTransform(D2D1::Matrix3x2F::Identity());
                HRESULT hr = m_canvasRecorder->EndDraw();
                m_canvasRecorder->SetTarget(nullptr);
                if (SUCCEEDED(hr) && SUCCEEDED(recorded->Close()))
                {
                    ctx->SetTransform(D2D1::Matrix3x2F::Identity());
                    if (shadow)
                    {
                        // The shadow is offset in canvas px, whatever the transform.
                        winrt::com_ptr<ID2D1Effect> effect;
                        if (SUCCEEDED(ctx->CreateEffect(CLSID_D2D1Shadow, effect.put())))
                        {
                            effect->SetInput(0, recorded.get());
                            effect->SetValue(D2D1_SHADOW_PROP_BLUR_STANDARD_DEVIATION, std::max(op.shadow_blur / 2, 0.0f));
                            effect->SetValue(D2D1_SHADOW_PROP_COLOR, D2D1::Vector4F(op.shadow_color.r / 255.0f, op.shadow_color.g / 255.0f, op.shadow_color.b / 255.0f, op.shadow_color.a / 255.0f));
                            D2D1_POINT_2F offset = D2D1::Point2F(op.shadow_x, op.shadow_y);
                            ctx->DrawImage(effect.get(), &offset, nullptr, D2D1_INTERPOLATION_MODE_LINEAR, mode);
                        }
                    }
                    ctx->DrawImage(recorded.get(), nullptr, nullptr, D2D1_INTERPOLATION_MODE_LINEAR, mode);
                }
            }
        }

        for (size_t i = 0; i < clips.size(); i++)
        {
            ctx->PopLayer();
        }

        if (snapshot)
        {
            m_canvases.erase(-id);
        }
    }

    void D2DBackend::canvas_run(int id, int w, int h, const std::vector<CanvasOp>& ops)
    {
        if (!m_canvasCtx)
        {
            winrt::check_hresult(m_device->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, m_canvasCtx.put()));
            winrt::check_hresult(m_device->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, m_canvasRecorder.put()));
            winrt::check_hresult(m_canvasCtx->CreateSolidColorBrush(D2D1::ColorF(0, 0), m_canvasBrush.put()));
            // Canvas pixels are device pixels: no DPI scaling on these contexts.
            m_canvasCtx->SetDpi(96, 96);
            m_canvasRecorder->SetDpi(96, 96);
        }

        CanvasBitmap& target = m_canvases[id];
        if (w <= 0 || h <= 0)
        {
            target = {};
            return;
        }

        bool fresh = !target.bitmap || target.w != w || target.h != h;
        if (fresh)
        {
            D2D1_BITMAP_PROPERTIES1 props = D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET, D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED));
            target = {};
            if (FAILED(m_canvasCtx->CreateBitmap(D2D1::SizeU(w, h), nullptr, 0, props, target.bitmap.put()))) return;
            target.w = w;
            target.h = h;
        }

        m_canvasCtx->SetTarget(target.bitmap.get());
        m_canvasCtx->BeginDraw();
        if (fresh) m_canvasCtx->Clear(D2D1::ColorF(0, 0));
        for (const CanvasOp& op : ops)
        {
            canvas_op(op, id);
        }
        m_canvasCtx->SetTransform(D2D1::Matrix3x2F::Identity());
        m_canvasCtx->EndDraw();
        m_canvasCtx->SetTarget(nullptr);
    }

    bool D2DBackend::canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba)
    {
        rgba.assign(size_t(w) * h * 4, 0);
        auto it = m_canvases.find(id);
        if (it == m_canvases.end() || !it->second.bitmap || w <= 0 || h <= 0) return true;
        const CanvasBitmap& canvas = it->second;
        int x0 = std::max(0, x), y0 = std::max(0, y), x1 = std::min(canvas.w, x + w), y1 = std::min(canvas.h, y + h);
        if (x1 <= x0 || y1 <= y0) return true;

        winrt::com_ptr<ID2D1Bitmap1> readable;
        D2D1_BITMAP_PROPERTIES1 props = D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_CPU_READ | D2D1_BITMAP_OPTIONS_CANNOT_DRAW, D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED));
        if (FAILED(m_canvasCtx->CreateBitmap(D2D1::SizeU(x1 - x0, y1 - y0), nullptr, 0, props, readable.put()))) return false;
        D2D1_POINT_2U origin{ 0, 0 };
        D2D1_RECT_U source{ UINT32(x0), UINT32(y0), UINT32(x1), UINT32(y1) };
        if (FAILED(readable->CopyFromBitmap(&origin, canvas.bitmap.get(), &source))) return false;

        D2D1_MAPPED_RECT mapped;
        if (FAILED(readable->Map(D2D1_MAP_OPTIONS_READ, &mapped))) return false;
        for (int row = y0; row < y1; row++)
        {
            const uint8_t* src = mapped.bits + size_t(row - y0) * mapped.pitch;
            uint8_t* dst = rgba.data() + (size_t(row - y) * w + (x0 - x)) * 4;
            for (int col = x0; col < x1; col++, src += 4, dst += 4)
            {
                uint32_t a = src[3];
                // getImageData is straight alpha.
                dst[0] = a ? uint8_t(std::min<uint32_t>(255, src[2] * 255 / a)) : 0;
                dst[1] = a ? uint8_t(std::min<uint32_t>(255, src[1] * 255 / a)) : 0;
                dst[2] = a ? uint8_t(std::min<uint32_t>(255, src[0] * 255 / a)) : 0;
                dst[3] = uint8_t(a);
            }
        }
        readable->Unmap();
        return true;
    }

    void D2DBackend::canvas_release(int id)
    {
        m_canvases.erase(id);
    }

    namespace
    {
        constexpr int64_t MAX_RASTER_PIXELS = int64_t(16) << 20;

        // The index of the GroupPop closing the group opened at `at`, or list.size().
        size_t group_end(std::span<const Command> list, size_t at)
        {
            int depth = 0;
            for (size_t i = at; i < list.size(); i++)
            {
                switch (list[i].kind)
                {
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

    void D2DBackend::trim()
    {
        m_rasters.clear();
        m_rasterPixels = 0;
    }

    bool D2DBackend::draw_raster(ID2D1DeviceContext* ctx, const Command& push, std::span<const Command> inner)
    {
        D2D1_MATRIX_3X2_F outer;
        ctx->GetTransform(&outer);
        float dpiX, dpiY;
        ctx->GetDpi(&dpiX, &dpiY);
        // User space → device pixels.
        auto device = D2D1::Matrix3x2F(push.matrix[0], push.matrix[1], push.matrix[2], push.matrix[3], push.matrix[4], push.matrix[5]) *
                      D2D1::Matrix3x2F::ReinterpretBaseType(&outer)[0] * D2D1::Matrix3x2F::Scale(dpiX / 96, dpiY / 96);
        if (device._12 != 0 || device._21 != 0 || !(device._11 > 0) || !(device._22 > 0)) return false;
        float x0 = std::floor(push.rect.x * device._11 + device._31), y0 = std::floor(push.rect.y * device._22 + device._32);
        float x1 = std::ceil((push.rect.x + push.rect.w) * device._11 + device._31), y1 = std::ceil((push.rect.y + push.rect.h) * device._22 + device._32);
        if (!(x1 > x0) || !(y1 > y0)) return false;
        int64_t pixels = int64_t(x1 - x0) * int64_t(y1 - y0);
        if (pixels > MAX_RASTER_PIXELS / 4) return false;
        auto to_raster = device * D2D1::Matrix3x2F::Translation(-x0, -y0);
        auto q = [](float v) { return int64_t(std::llround(double(v) * 1024)); };
        RasterKey key{push.key, q(to_raster._11), q(to_raster._22), q(to_raster._31), q(to_raster._32)};
        auto it = m_rasters.find(key);
        if (it == m_rasters.end())
        {
            while (!m_rasters.empty() && m_rasterPixels + pixels > MAX_RASTER_PIXELS)
            {
                auto oldest = m_rasters.begin();
                for (auto i = m_rasters.begin(); i != m_rasters.end(); ++i)
                    if (i->second.used < oldest->second.used) oldest = i;
                m_rasterPixels -= oldest->second.pixels;
                m_rasters.erase(oldest);
            }
            if (!m_rasterCtx)
            {
                winrt::check_hresult(m_device->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, m_rasterCtx.put()));
            }
            Raster raster;
            raster.pixels = pixels;
            auto properties = D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET, D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED));
            if (FAILED(m_rasterCtx->CreateBitmap(D2D1::SizeU(UINT32(x1 - x0), UINT32(y1 - y0)), nullptr, 0, properties, raster.bitmap.put()))) return false;
            m_rasterCtx->SetTarget(raster.bitmap.get());
            m_rasterCtx->SetDpi(96, 96);
            m_rasterCtx->BeginDraw();
            m_rasterCtx->Clear(D2D1::ColorF(0, 0));
            m_rasterCtx->SetTransform(to_raster);
            // The raster paints with this backend's state, which the paint around it gets back after.
            ID2D1DeviceContext* saved = m_ctx;
            auto blur = m_blur;
            m_rastering = true;
            paint_span(inner, m_rasterCtx.get());
            m_rastering = false;
            m_ctx = saved;
            m_blur = blur;
            HRESULT hr = m_rasterCtx->EndDraw();
            m_rasterCtx->SetTarget(nullptr);
            if (FAILED(hr)) return false;
            m_rasterPixels += pixels;
            it = m_rasters.emplace(key, std::move(raster)).first;
        }
        it->second.used = ++m_rasterClock;
        // Device pixels, copied as they are.
        ctx->SetTransform(D2D1::Matrix3x2F::Scale(96 / dpiX, 96 / dpiY));
        ctx->DrawBitmap(it->second.bitmap.get(), D2D1::RectF(x0, y0, x1, y1), 1.0f, D2D1_INTERPOLATION_MODE_NEAREST_NEIGHBOR);
        ctx->SetTransform(outer);
        return true;
    }

    winrt::com_ptr<ID2D1Image> D2DBackend::filtered(ID2D1DeviceContext* ctx, ID2D1Image* input, const Filters& steps, float scale)
    {
        // Blur is a Gaussian whose standard deviation is the radius; the colour functions are color_matrix's.
        auto matrix = [](const float rgb[9], float offset, float alpha) {
            D2D1_MATRIX_5X4_F m{};
            for (int out = 0; out < 3; out++)
                for (int in = 0; in < 3; in++) m.m[in][out] = rgb[out * 3 + in];
            m.m[3][3] = alpha;
            m.m[4][0] = m.m[4][1] = m.m[4][2] = offset;
            return m;
        };
        winrt::com_ptr<ID2D1Image> current;
        current.copy_from(input);
        for (const Filter& f : steps)
        {
            winrt::com_ptr<ID2D1Effect> effect;
            if (f.kind == Filter::Blur)
            {
                if (f.amount <= 0 || FAILED(ctx->CreateEffect(CLSID_D2D1GaussianBlur, effect.put()))) continue;
                effect->SetValue(D2D1_GAUSSIANBLUR_PROP_STANDARD_DEVIATION, f.amount * scale);
                effect->SetValue(D2D1_GAUSSIANBLUR_PROP_BORDER_MODE, D2D1_BORDER_MODE_SOFT);
            }
            else if (f.kind == Filter::DropShadow)
            {
                winrt::com_ptr<ID2D1Effect> shadow, moved, over;
                if (FAILED(ctx->CreateEffect(CLSID_D2D1Shadow, shadow.put())) || FAILED(ctx->CreateEffect(CLSID_D2D12DAffineTransform, moved.put())) ||
                    FAILED(ctx->CreateEffect(CLSID_D2D1Composite, over.put())))
                    continue;
                shadow->SetInput(0, current.get());
                shadow->SetValue(D2D1_SHADOW_PROP_BLUR_STANDARD_DEVIATION, f.amount * scale / 2);
                shadow->SetValue(D2D1_SHADOW_PROP_COLOR, D2D1::Vector4F(f.color.r / 255.0f, f.color.g / 255.0f, f.color.b / 255.0f, f.color.a / 255.0f));
                moved->SetInputEffect(0, shadow.get());
                moved->SetValue(D2D1_2DAFFINETRANSFORM_PROP_TRANSFORM_MATRIX, D2D1::Matrix3x2F::Translation(f.x * scale, f.y * scale));
                // The shadow under, the image over it.
                over->SetInputEffect(0, moved.get());
                over->SetInput(1, current.get());
                current = nullptr;
                over->GetOutput(current.put());
                continue;
            }
            else
            {
                ColorMatrix cm;
                if (!color_matrix(f, cm)) continue;
                if (FAILED(ctx->CreateEffect(CLSID_D2D1ColorMatrix, effect.put()))) continue;
                effect->SetValue(D2D1_COLORMATRIX_PROP_COLOR_MATRIX, matrix(cm.rgb, cm.offset, cm.alpha));
                effect->SetValue(D2D1_COLORMATRIX_PROP_ALPHA_MODE, D2D1_COLORMATRIX_ALPHA_MODE_STRAIGHT);
                effect->SetValue(D2D1_COLORMATRIX_PROP_CLAMP_OUTPUT, TRUE);
            }
            effect->SetInput(0, current.get());
            current = nullptr;
            effect->GetOutput(current.put());
        }
        return current;
    }

    bool D2DBackend::draw_filtered(ID2D1DeviceContext* ctx, const Command& push, std::span<const Command> inner)
    {
        if (!push.filter || push.filter->empty()) return false;
        // Recorded on a context of its own, in the space the commands are in: the one drawing is mid-draw,
        // and a filter inside this one records on another.
        winrt::com_ptr<ID2D1Device> device;
        ctx->GetDevice(device.put());
        winrt::com_ptr<ID2D1DeviceContext> recorder;
        winrt::com_ptr<ID2D1CommandList> recorded;
        if (FAILED(device->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, recorder.put())) || FAILED(recorder->CreateCommandList(recorded.put())))
            return false;
        recorder->SetTarget(recorded.get());
        recorder->BeginDraw();
        ID2D1DeviceContext* saved = m_ctx;
        auto blur = m_blur;
        const bool rastering = m_rastering;
        m_rastering = true;
        paint_span(inner, recorder.get());
        m_rastering = rastering;
        m_ctx = saved;
        m_blur = blur;
        HRESULT hr = recorder->EndDraw();
        recorder->SetTarget(nullptr);
        if (FAILED(hr) || FAILED(recorded->Close())) return false;
        auto image = filtered(ctx, recorded.get(), *push.filter, 1);
        if (!image) return false;
        ctx->DrawImage(image.get());
        return true;
    }

    void D2DBackend::backdrop(ID2D1DeviceContext* ctx, const Command& c)
    {
        if (!c.filter || c.filter->empty() || c.rect.empty()) return;
        // Only a bitmap has pixels to read; a recording (a filter, a shadow) has none yet.
        winrt::com_ptr<ID2D1Image> target;
        ctx->GetTarget(target.put());
        auto bitmap = target.try_as<ID2D1Bitmap1>();
        if (!bitmap) return;
        D2D1_MATRIX_3X2_F outer;
        ctx->GetTransform(&outer);
        float dpiX, dpiY;
        ctx->GetDpi(&dpiX, &dpiY);
        auto device = D2D1::Matrix3x2F::ReinterpretBaseType(&outer)[0] * D2D1::Matrix3x2F::Scale(dpiX / 96, dpiY / 96);
        if (device._12 != 0 || device._21 != 0 || !(device._11 > 0) || !(device._22 > 0)) return;
        // Wide enough that a blur near the edge reads what is around the box, as browsers do.
        float reach = 0;
        for (const Filter& f : *c.filter)
            if (f.kind == Filter::Blur) reach += 3 * f.amount;
        const D2D1_SIZE_U size = bitmap->GetPixelSize();
        float x0 = std::max(0.0f, std::floor((c.rect.x - reach) * device._11 + device._31));
        float y0 = std::max(0.0f, std::floor((c.rect.y - reach) * device._22 + device._32));
        float x1 = std::min(float(size.width), std::ceil((c.rect.x + c.rect.w + reach) * device._11 + device._31));
        float y1 = std::min(float(size.height), std::ceil((c.rect.y + c.rect.h + reach) * device._22 + device._32));
        if (!(x1 > x0) || !(y1 > y0) || int64_t(x1 - x0) * int64_t(y1 - y0) > MAX_RASTER_PIXELS / 4) return;
        winrt::com_ptr<ID2D1Bitmap1> copy;
        auto properties = D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_NONE, bitmap->GetPixelFormat());
        if (FAILED(ctx->CreateBitmap(D2D1::SizeU(UINT32(x1 - x0), UINT32(y1 - y0)), nullptr, 0, properties, copy.put()))) return;
        ctx->Flush();
        D2D1_POINT_2U at{0, 0};
        D2D1_RECT_U from{UINT32(x0), UINT32(y0), UINT32(x1), UINT32(y1)};
        if (FAILED(copy->CopyFromBitmap(&at, bitmap.get(), &from))) return;
        auto image = filtered(ctx, copy.get(), *c.filter, device._11);
        if (!image) return;
        // Back inside the box, its corners too; the copy is in device pixels.
        winrt::com_ptr<ID2D1Geometry> shape = c.radii.any() ? rounded(c.rect, c.radii) : nullptr;
        if (shape) ctx->PushLayer(D2D1::LayerParameters(D2D1::InfiniteRect(), shape.get()), nullptr);
        else ctx->PushAxisAlignedClip(rect(c.rect), D2D1_ANTIALIAS_MODE_PER_PRIMITIVE);
        ctx->SetTransform(D2D1::Matrix3x2F::Scale(96 / dpiX, 96 / dpiY));
        ctx->DrawImage(image.get(), D2D1::Point2F(x0, y0));
        ctx->SetTransform(outer);
        if (shape) ctx->PopLayer();
        else ctx->PopAxisAlignedClip();
    }

    void D2DBackend::paint(const DisplayList& list, ID2D1DeviceContext* ctx)
    {
        paint_span(list, ctx);
    }

    void D2DBackend::paint_span(std::span<const Command> list, ID2D1DeviceContext* ctx)
    {
        m_ctx = ctx;
        if (!m_factory)
        {
            ctx->GetFactory(m_factory.put());
        }

        // The device is the process's one, so the brush and the bitmaps outlive a paint.
        if (!m_brush)
        {
            winrt::check_hresult(ctx->CreateSolidColorBrush(D2D1::ColorF(0, 0), m_brush.put()));
        }

        // Effects belong to the context they were made on, and BeginDraw can hand out a different one each time.
        m_blur = nullptr;

        // Clips and groups nest together in paint order, so one stack unwinds both. A rounded clip is a
        // layer, a plain one an axis-aligned clip; a group restores the transform it replaced.
        struct Entry
        {
            bool layer = false;
            bool axis = false;
            bool group = false;
            D2D1_MATRIX_3X2_F transform{};
        };
        std::vector<Entry> stack;
        auto push = [&](const Rect& r, const Radii& q)
        {
            Entry e;
            if (q.any())
            {
                auto geometry = rounded(r, q);
                ctx->PushLayer(D2D1::LayerParameters(D2D1::InfiniteRect(), geometry.get()), nullptr);
                e.layer = true;
            }
            else
            {
                ctx->PushAxisAlignedClip(rect(r), D2D1_ANTIALIAS_MODE_PER_PRIMITIVE);
                e.axis = true;
            }
            stack.push_back(e);
        };
        auto pop = [&]
        {
            if (stack.empty()) return;
            Entry e = stack.back();
            stack.pop_back();
            if (e.layer) ctx->PopLayer();
            if (e.axis) ctx->PopAxisAlignedClip();
            if (e.group) ctx->SetTransform(&e.transform);
        };
        auto group = [&](const Command& c)
        {
            Entry e;
            e.group = true;
            ctx->GetTransform(&e.transform);
            auto own = D2D1::Matrix3x2F(c.matrix[0], c.matrix[1], c.matrix[2], c.matrix[3], c.matrix[4], c.matrix[5]);
            ctx->SetTransform(own * D2D1::Matrix3x2F::ReinterpretBaseType(&e.transform)[0]);
            if (c.hidden)
            {
                ctx->PushAxisAlignedClip(D2D1::RectF(0, 0, 0, 0), D2D1_ANTIALIAS_MODE_ALIASED);
                e.axis = true;
            }
            else if (c.opacity < 1)
            {
                ctx->PushLayer(D2D1::LayerParameters(D2D1::InfiniteRect(), nullptr, D2D1_ANTIALIAS_MODE_PER_PRIMITIVE, D2D1::IdentityMatrix(), c.opacity), nullptr);
                e.layer = true;
            }
            stack.push_back(e);
        };

        for (size_t i = 0; i < list.size(); i++)
        {
            const Command& c = list[i];
            switch (c.kind)
            {
            case Cmd::ClipPush:
                if (c.path)
                {
                    Entry e;
                    auto shape = geometry(c.path->ops, c.path->points, c.path->evenodd);
                    ctx->PushLayer(D2D1::LayerParameters(D2D1::InfiniteRect(), shape.get()), nullptr);
                    e.layer = true;
                    stack.push_back(e);
                }
                else
                {
                    push(c.rect, c.radii);
                }
                break;
            case Cmd::ClipPop:
            case Cmd::GroupPop:
                pop();
                break;
            case Cmd::GroupPush:
                if (c.key && !c.hidden && c.opacity >= 1 && !m_rastering)
                {
                    size_t end = group_end(list, i);
                    if (end < list.size() && draw_raster(ctx, c, list.subspan(i + 1, end - i - 1)))
                    {
                        i = end;
                        break;
                    }
                }
                group(c);
                break;
            case Cmd::Path:
                if (c.path) path(*c.path);
                break;
            case Cmd::Shadow:
                shadow(c);
                break;
            case Cmd::FilterPush:
            {
                size_t end = i + 1;
                for (int depth = 1; end < list.size(); end++)
                {
                    if (list[end].kind == Cmd::FilterPush) depth++;
                    else if (list[end].kind == Cmd::FilterPop && --depth == 0) break;
                }
                if (end < list.size() && draw_filtered(ctx, c, list.subspan(i + 1, end - i - 1))) i = end;
                break;
            }
            case Cmd::FilterPop:
                break;
            case Cmd::Backdrop:
                backdrop(ctx, c);
                break;
            case Cmd::FillRect:
                if (c.has_clip) ctx->PushAxisAlignedClip(rect(c.clip), D2D1_ANTIALIAS_MODE_PER_PRIMITIVE);
                m_brush->SetColor(color(c.color));
                if (c.radii.any()) ctx->FillGeometry(rounded(c.rect, c.radii).get(), m_brush.get());
                else ctx->FillRectangle(rect(c.rect), m_brush.get());
                if (c.has_clip) ctx->PopAxisAlignedClip();
                break;
            case Cmd::StrokeRect:
                m_brush->SetColor(color(c.color));
                if (c.radii.any()) ctx->DrawGeometry(rounded(c.rect, c.radii).get(), m_brush.get(), c.width);
                else ctx->DrawRectangle(rect(c.rect), m_brush.get(), c.width);
                break;
            case Cmd::FillEllipse:
                m_brush->SetColor(color(c.color));
                ctx->FillEllipse(D2D1::Ellipse(D2D1::Point2F(c.rect.x + c.rect.w / 2, c.rect.y + c.rect.h / 2), c.rect.w / 2, c.rect.h / 2), m_brush.get());
                break;
            case Cmd::StrokeEllipse:
                m_brush->SetColor(color(c.color));
                ctx->DrawEllipse(D2D1::Ellipse(D2D1::Point2F(c.rect.x + c.rect.w / 2, c.rect.y + c.rect.h / 2), (c.rect.w - c.width) / 2, (c.rect.h - c.width) / 2), m_brush.get(), c.width);
                break;
            case Cmd::Text:
            {
                auto it = m_handles.find(c.font);
                if (it == m_handles.end()) break;
                auto entry = layout(*it->second, c.text);
                if (!entry) break;
                m_brush->SetColor(color(c.color));
                ctx->DrawTextLayout(D2D1::Point2F(c.x, c.y - entry->baseline), entry->layout.get(), m_brush.get(), D2D1_DRAW_TEXT_OPTIONS_ENABLE_COLOR_FONT);
                break;
            }
            case Cmd::Image:
            {
                if (c.rect.empty()) break;
                ID2D1Bitmap* bitmap = nullptr;
                if (c.canvas)
                {
                    auto it = m_canvases.find(c.canvas);
                    if (it == m_canvases.end() || !it->second.bitmap) break;
                    bitmap = it->second.bitmap.get();
                }
                else
                {
                    auto it = m_images.find(c.image);
                    if (it == m_images.end()) break;
                    Image& image = it->second;
                    if (!image.bitmap && FAILED(ctx->CreateBitmapFromWicBitmap(image.source.get(), nullptr, image.bitmap.put()))) break;
                    bitmap = image.bitmap.get();
                }
                if (c.has_clip) push(c.clip, {});
                // The border box with its radii is the clip the layer asks for; the clip rect above bounds it.
                if (c.radii.any()) push(c.has_clip ? c.clip : c.rect, c.radii);
                ctx->DrawBitmap(bitmap, rect(c.rect), 1.0f, D2D1_INTERPOLATION_MODE_HIGH_QUALITY_CUBIC);
                if (c.radii.any()) pop();
                if (c.has_clip) pop();
                break;
            }
            case Cmd::Linear:
            case Cmd::Radial:
            case Cmd::Conic:
            {
                if (c.has_clip) push(c.clip, {});
                if (c.kind == Cmd::Conic)
                {
                    push(c.rect, c.radii);
                    conic(c);
                    pop();
                }
                else if (auto collection = stops(c.stops))
                {
                    winrt::com_ptr<ID2D1Brush> brush;
                    if (c.kind == Cmd::Linear)
                    {
                        winrt::com_ptr<ID2D1LinearGradientBrush> linear;
                        ctx->CreateLinearGradientBrush(D2D1::LinearGradientBrushProperties(D2D1::Point2F(c.ax, c.ay), D2D1::Point2F(c.bx, c.by)), collection.get(), linear.put());
                        brush = linear;
                    }
                    else
                    {
                        winrt::com_ptr<ID2D1RadialGradientBrush> radial;
                        ctx->CreateRadialGradientBrush(D2D1::RadialGradientBrushProperties(D2D1::Point2F(c.ax, c.ay), D2D1::Point2F(), std::max(c.bx, 0.01f), std::max(c.by, 0.01f)), collection.get(), radial.put());
                        brush = radial;
                    }

                    if (brush)
                    {
                        if (c.radii.any()) ctx->FillGeometry(rounded(c.rect, c.radii).get(), brush.get());
                        else ctx->FillRectangle(rect(c.rect), brush.get());
                    }
                }
                if (c.has_clip) pop();
                break;
            }
            }
        }

        while (!stack.empty())
        {
            pop();
        }

        m_ctx = nullptr;
    }
}
