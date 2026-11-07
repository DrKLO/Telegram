// What a browser shows of a broken PNG. Decoders differ on a cut or corrupt stream: Chromium keeps the
// rows decoded before the damage and leaves the rest transparent, where others fail the whole image or
// fill the rest with whatever their inflater made of it. The backends decode as they do and then clear
// the rows this finds.
#pragma once

#include <cstddef>
#include <cstdint>

namespace twlite {

/// How many rows of the PNG in `data` come before the damage, when it is damaged: it lacks its IEND,
/// or an IDAT chunk fails its CRC. The rows then stop at an unknown filter type or where the image data
/// runs out. SIZE_MAX when nothing is damaged, or `data` is no non-interlaced PNG.
size_t png_intact_rows(const uint8_t* data, size_t size);

/// The size a PNG, GIF, JPEG or WebP header in `data` declares, read before decoding: what a decoder
/// would allocate for a small file. False for anything else, or a header cut short.
bool image_dimensions(const uint8_t* data, size_t size, uint32_t& w, uint32_t& h);

} // namespace twlite
