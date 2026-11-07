#include "filters.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

namespace twlite {

bool color_matrix(const Filter& f, ColorMatrix& out) {
    out = ColorMatrix{};
    const float a = f.amount;
    switch (f.kind) {
    case Filter::Brightness:
        out.rgb[0] = out.rgb[4] = out.rgb[8] = a;
        return true;
    case Filter::Contrast:
        out.rgb[0] = out.rgb[4] = out.rgb[8] = a;
        out.offset = (1 - a) / 2;
        return true;
    case Filter::Invert: {
        const float k = std::min(a, 1.0f);
        out.rgb[0] = out.rgb[4] = out.rgb[8] = 1 - 2 * k;
        out.offset = k;
        return true;
    }
    case Filter::Opacity:
        out.alpha = std::clamp(a, 0.0f, 1.0f);
        return true;
    case Filter::Grayscale:
    case Filter::Sepia: {
        const float s = 1 - std::min(a, 1.0f);
        const float grey[9] = {0.2126f + 0.7874f * s, 0.7152f - 0.7152f * s, 0.0722f - 0.0722f * s,
                               0.2126f - 0.2126f * s, 0.7152f + 0.2848f * s, 0.0722f - 0.0722f * s,
                               0.2126f - 0.2126f * s, 0.7152f - 0.7152f * s, 0.0722f + 0.9278f * s};
        const float sepia[9] = {0.393f + 0.607f * s, 0.769f - 0.769f * s, 0.189f - 0.189f * s,
                                0.349f - 0.349f * s, 0.686f + 0.314f * s, 0.168f - 0.168f * s,
                                0.272f - 0.272f * s, 0.534f - 0.534f * s, 0.131f + 0.869f * s};
        std::copy_n(f.kind == Filter::Grayscale ? grey : sepia, 9, out.rgb);
        return true;
    }
    case Filter::Saturate: {
        const float s = a;
        const float m[9] = {0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s,
                            0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s,
                            0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s};
        std::copy_n(m, 9, out.rgb);
        return true;
    }
    case Filter::HueRotate: {
        const float r = a * 3.14159265f / 180, c = std::cos(r), s = std::sin(r);
        const float m[9] = {0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f,
                            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f,
                            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f};
        std::copy_n(m, 9, out.rgb);
        return true;
    }
    default:
        return false;
    }
}

FilterReach filter_reach(const Filters& filters) {
    FilterReach r;
    for (const Filter& f : filters) {
        if (f.kind == Filter::Blur) {
            float d = 3 * std::max(f.amount, 0.0f);
            r.left += d; r.top += d; r.right += d; r.bottom += d;
        } else if (f.kind == Filter::DropShadow) {
            // The shadow's blur is half the radius as a standard deviation, as the Direct2D painter has it.
            float d = 1.5f * std::max(f.amount, 0.0f);
            r.left = std::max(r.left, d - f.x); r.right = std::max(r.right, d + f.x);
            r.top = std::max(r.top, d - f.y); r.bottom = std::max(r.bottom, d + f.y);
        }
    }
    return r;
}

namespace {

/// One box blur pass of radius `r` along rows (`horizontal`) or columns, over 4 interleaved channels.
void box_pass(std::vector<float>& src, std::vector<float>& dst, int w, int h, int r, bool horizontal) {
    const int lines = horizontal ? h : w, length = horizontal ? w : h;
    const float scale = 1.0f / float(2 * r + 1);
    for (int line = 0; line < lines; line++) {
        auto at = [&](int i) -> size_t {
            i = std::clamp(i, -1, length);
            if (i < 0 || i >= length) return SIZE_MAX;
            return (horizontal ? size_t(line) * w + i : size_t(i) * w + line) * 4;
        };
        float sum[4] = {0, 0, 0, 0};
        // Outside the image is transparent: the sum starts with the pixels that reach position 0.
        for (int i = -r; i <= r; i++)
            if (size_t k = at(i); k != SIZE_MAX)
                for (int c = 0; c < 4; c++) sum[c] += src[k + c];
        for (int i = 0; i < length; i++) {
            size_t out = at(i);
            for (int c = 0; c < 4; c++) dst[out + c] = sum[c] * scale;
            if (size_t k = at(i - r); k != SIZE_MAX)
                for (int c = 0; c < 4; c++) sum[c] -= src[k + c];
            if (size_t k = at(i + r + 1); k != SIZE_MAX)
                for (int c = 0; c < 4; c++) sum[c] += src[k + c];
        }
    }
}

/// A Gaussian of standard deviation `sigma` px as three box blurs (the box sizes SVG's feGaussianBlur gives).
void gaussian(std::vector<float>& px, int w, int h, float sigma) {
    if (sigma < 0.5f) return;
    int d = int(std::floor(sigma * 3 * std::sqrt(2 * 3.14159265f) / 4 + 0.5f));
    int r = std::max(1, d / 2);
    std::vector<float> tmp(px.size());
    for (int pass = 0; pass < 3; pass++) {
        box_pass(px, tmp, w, h, r, true);
        box_pass(tmp, px, w, h, r, false);
    }
}

} // namespace

void filter_pixels(uint8_t* pixels, int w, int h, size_t stride, bool bgra, const Filters& filters, float scale) {
    if (w <= 0 || h <= 0 || filters.empty()) return;
    // Premultiplied R, G, B, A in floats, whatever the bytes' order.
    const int R = bgra ? 2 : 0, B = bgra ? 0 : 2;
    std::vector<float> px(size_t(w) * h * 4);
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            const uint8_t* s = pixels + size_t(y) * stride + size_t(x) * 4;
            float* d = &px[(size_t(y) * w + x) * 4];
            d[0] = s[R] / 255.0f; d[1] = s[1] / 255.0f; d[2] = s[B] / 255.0f; d[3] = s[3] / 255.0f;
        }
    for (const Filter& f : filters) {
        ColorMatrix m;
        if (color_matrix(f, m)) {
            for (size_t i = 0; i < px.size(); i += 4) {
                float a = px[i + 3];
                if (a <= 0) continue;
                float c[3] = {px[i] / a, px[i + 1] / a, px[i + 2] / a};
                float na = std::clamp(a * m.alpha, 0.0f, 1.0f);
                for (int o = 0; o < 3; o++) {
                    float v = m.rgb[o * 3] * c[0] + m.rgb[o * 3 + 1] * c[1] + m.rgb[o * 3 + 2] * c[2] + m.offset;
                    px[i + o] = std::clamp(v, 0.0f, 1.0f) * na;
                }
                px[i + 3] = na;
            }
        } else if (f.kind == Filter::Blur) {
            gaussian(px, w, h, f.amount * scale);
        } else if (f.kind == Filter::DropShadow) {
            // The alpha in the shadow's colour, blurred and moved, under the image.
            std::vector<float> shadow(px.size(), 0.0f);
            const int dx = int(std::lround(f.x * scale)), dy = int(std::lround(f.y * scale));
            const float color[4] = {f.color.r / 255.0f, f.color.g / 255.0f, f.color.b / 255.0f, f.color.a / 255.0f};
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int sx = x - dx, sy = y - dy;
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                    float a = px[(size_t(sy) * w + sx) * 4 + 3] * color[3];
                    float* d = &shadow[(size_t(y) * w + x) * 4];
                    d[0] = color[0] * a; d[1] = color[1] * a; d[2] = color[2] * a; d[3] = a;
                }
            gaussian(shadow, w, h, f.amount * scale / 2);
            for (size_t i = 0; i < px.size(); i += 4) {
                float under = 1 - px[i + 3];
                for (int c = 0; c < 4; c++) px[i + c] += shadow[i + c] * under;
            }
        }
    }
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            uint8_t* d = pixels + size_t(y) * stride + size_t(x) * 4;
            const float* s = &px[(size_t(y) * w + x) * 4];
            auto byte = [](float v) { return uint8_t(std::lround(std::clamp(v, 0.0f, 1.0f) * 255)); };
            d[R] = byte(s[0]); d[1] = byte(s[1]); d[B] = byte(s[2]); d[3] = byte(s[3]);
        }
}

} // namespace twlite
