// CSS filter functions (filter, backdrop-filter) for painters: the colour functions as matrices, how
// far a chain reaches past what it filters, and a CPU pass over a painter's own pixels for the ones that
// paint into memory (Qt, CoreGraphics). The Direct2D painter takes the matrices into its effects.
#pragma once

#include "display_list.h"

#include <cstddef>
#include <cstdint>

namespace twlite {

/// A colour function as Filter Effects 1 writes its shorthand equivalent: on straight-alpha colour,
/// out = rgb · in + offset, alpha times `alpha`. rgb is row-major, a row per output channel.
struct ColorMatrix {
    float rgb[9] = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    float offset = 0;
    float alpha = 1;
};
/// False for blur and drop-shadow, which are no colour matrices.
bool color_matrix(const Filter& f, ColorMatrix& out);

/// How far past its box a chain paints, in CSS px: blurs spread three standard deviations, a drop
/// shadow that and its offset.
struct FilterReach {
    float left = 0, top = 0, right = 0, bottom = 0;
};
FilterReach filter_reach(const Filters& filters);

/// Runs `filters` in order over premultiplied 8-bit pixels, in place. `bgra`: the bytes are B, G, R, A
/// (QImage's premultiplied ARGB32 on a little-endian machine) rather than R, G, B, A. `scale`: device px
/// per CSS px, for blur radii and shadow offsets.
void filter_pixels(uint8_t* pixels, int width, int height, size_t stride, bool bgra, const Filters& filters, float scale);

} // namespace twlite
