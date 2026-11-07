// What the platform provides to the litehtml container: fonts with metrics,
// text measurement and image decoding. Everything else the container needs
// (painting) goes through the DisplayList. One implementation per platform:
// CoreText in src/apple/coregraphics.cpp, JNI callbacks into
// LitehtmlBackend.kt in src/android/jni.cpp. All calls on the main thread.
#pragma once

#include "canvas_ops.h"

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
};

class Backend {
public:
    virtual ~Backend() = default;
    /// `families`: the CSS `font-family` list as written (quotes kept, comma separated); the
    /// backend resolves generics (`-apple-system`, `system-ui`, `sans-serif`, …) and falls back
    /// to its default face. Returns a handle > 0, or 0 when no font at all could be made.
    virtual int create_font(const std::string& families, float size_px, int weight, bool italic, FontMetrics& out) = 0;
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
