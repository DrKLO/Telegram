// What the platform provides to the litehtml container: fonts with metrics,
// text measurement and image decoding. Everything else the container needs
// (painting) goes through the DisplayList. One implementation per platform:
// CoreText and CoreGraphics, Android's text and Canvas over JNI, DirectWrite
// and Direct2D. All calls on the renderer's thread.
#pragma once

#include "canvas_ops.h"

#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

namespace twlite {

struct FontMetrics {
    float ascent = 0;      // baseline → top of the line box
    float descent = 0;     // baseline → bottom
    float height = 0;      // line height the font asks for (ascent + descent + leading)
    float x_height = 0;
    float zero_width = 0;  // advance of "0" (the `ch` unit)
    // The em box canvas text is placed on: the font's typographic ascent and descent (OS/2 table)
    // scaled to add up to its size, as Chromium normalises them. 0 and 0 when the backend cannot read
    // them; the core then normalises ascent and descent instead.
    float em_ascent = 0, em_descent = 0;
};

/// Sets `m`'s em box from an ascent and a descent (any unit, the descent positive) for a font `size` px:
/// the ascent rounded to 1/64 px, the descent the rest. False, leaving it, when they make no box.
inline bool set_em_box(FontMetrics& m, float ascent, float descent, float size) {
    float height = ascent + descent;
    if (ascent < 0 || descent < 0 || height <= 0 || size <= 0) return false;
    m.em_ascent = std::round(ascent * size / height * 64) / 64;
    m.em_descent = size - m.em_ascent;
    return true;
}

class Backend {
public:
    virtual ~Backend() = default;
    /// `families`: the CSS `font-family` list as written (quotes kept, comma separated); the
    /// backend resolves generics (`-apple-system`, `system-ui`, `sans-serif`, …) and falls back
    /// to its default face. `features`: OpenType features to turn on or off, as `tag=value` pairs
    /// joined by commas (`tnum=1,zero=1`), from font-variant-numeric and font-feature-settings; empty
    /// for the font's defaults. Returns a handle > 0, or 0 when no font at all could be made.
    /// Documents and canvases never share a handle: a canvas's fonts (fillText, strokeText,
    /// measureText) are made for it alone, with empty `features`, and deleted when it drops them.
    virtual int create_font(const std::string& families, float size_px, int weight, bool italic, const std::string& features, FontMetrics& out) = 0;
    virtual void delete_font(int font) = 0;
    /// Advance width of `text` (UTF-8) in CSS px.
    virtual float text_width(int font, const std::string& text) = 0;
    /// Decodes a `data:` image (anything else is refused by the container before this) and keeps it
    /// under `id` for the painter; `w`/`h` get the intrinsic size in CSS px. False when undecodable.
    virtual bool load_image(int id, const std::string& url, float& w, float& h) = 0;

    // ── 2D canvas: optional. Without it a <canvas> stays transparent and reads back as zeros. ──
    /// Runs `ops` on the w × h px bitmap of canvas `id`, created on the first call; a Reset op
    /// reallocates it. The painter draws it wherever a display list Image names `canvas = id`.
    virtual void canvas_run(int /*id*/, int /*w*/, int /*h*/, const std::vector<CanvasOp>& /*ops*/) {}
    /// Straight-alpha RGBA of a region; pixels outside the bitmap are transparent. False when unsupported.
    virtual bool canvas_read(int /*id*/, int /*x*/, int /*y*/, int /*w*/, int /*h*/, std::vector<uint8_t>& /*rgba*/) { return false; }
    virtual void canvas_release(int /*id*/) {}
};

} // namespace twlite
