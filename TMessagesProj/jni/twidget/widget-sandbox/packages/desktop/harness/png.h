// Minimal PNG writer for the harness: RGBA8 (premultiplied in, straight out),
// uncompressed deflate (stored blocks), no dependency. Files are large but
// every viewer opens them; the gallery scripts re-encode anyway.
#pragma once

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

namespace twidget::png {

inline uint32_t crc32(const uint8_t* data, size_t len, uint32_t crc = 0) {
    static uint32_t table[256];
    static bool init = false;
    if (!init) {
        for (uint32_t n = 0; n < 256; n++) {
            uint32_t c = n;
            for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
            table[n] = c;
        }
        init = true;
    }
    crc = ~crc;
    for (size_t i = 0; i < len; i++) crc = table[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    return ~crc;
}

inline void be32(std::vector<uint8_t>& out, uint32_t v) {
    out.push_back(uint8_t(v >> 24)); out.push_back(uint8_t(v >> 16)); out.push_back(uint8_t(v >> 8)); out.push_back(uint8_t(v));
}

inline void chunk(std::vector<uint8_t>& out, const char* type, const std::vector<uint8_t>& data) {
    be32(out, uint32_t(data.size()));
    size_t start = out.size();
    out.insert(out.end(), type, type + 4);
    out.insert(out.end(), data.begin(), data.end());
    be32(out, crc32(out.data() + start, out.size() - start));
}

/// zlib stream of stored (uncompressed) deflate blocks.
inline std::vector<uint8_t> zlibStored(const std::vector<uint8_t>& raw) {
    std::vector<uint8_t> z;
    z.reserve(raw.size() + raw.size() / 65535 * 5 + 16);
    z.push_back(0x78); z.push_back(0x01);
    size_t pos = 0;
    do {
        size_t n = std::min<size_t>(65535, raw.size() - pos);
        bool last = pos + n == raw.size();
        z.push_back(last ? 1 : 0);
        z.push_back(uint8_t(n)); z.push_back(uint8_t(n >> 8));
        z.push_back(uint8_t(~n)); z.push_back(uint8_t(~n >> 8));
        z.insert(z.end(), raw.begin() + long(pos), raw.begin() + long(pos + n));
        pos += n;
    } while (pos < raw.size());
    uint32_t a = 1, b = 0;
    for (uint8_t c : raw) { a = (a + c) % 65521; b = (b + a) % 65521; }
    be32(z, (b << 16) | a);
    return z;
}

/// Writes `width × height` premultiplied RGBA8 pixels as a PNG; false on I/O failure.
inline bool write(const std::string& path, uint32_t width, uint32_t height, const std::vector<uint8_t>& premultiplied) {
    if (premultiplied.size() < size_t(width) * height * 4) return false;
    std::vector<uint8_t> raw;
    raw.reserve((size_t(width) * 4 + 1) * height);
    for (uint32_t y = 0; y < height; y++) {
        raw.push_back(0); // filter: none
        const uint8_t* row = premultiplied.data() + size_t(y) * width * 4;
        for (uint32_t x = 0; x < width; x++) {
            uint8_t a = row[x * 4 + 3];
            for (int c = 0; c < 3; c++) {
                unsigned v = row[x * 4 + c];
                raw.push_back(uint8_t(a == 0 ? 0 : std::min<unsigned>(255, (v * 255 + a / 2) / a)));
            }
            raw.push_back(a);
        }
    }
    std::vector<uint8_t> out = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    std::vector<uint8_t> ihdr;
    be32(ihdr, width); be32(ihdr, height);
    ihdr.push_back(8); ihdr.push_back(6); ihdr.push_back(0); ihdr.push_back(0); ihdr.push_back(0); // 8-bit RGBA
    chunk(out, "IHDR", ihdr);
    chunk(out, "IDAT", zlibStored(raw));
    chunk(out, "IEND", {});
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) return false;
    bool ok = std::fwrite(out.data(), 1, out.size(), f) == out.size();
    std::fclose(f);
    return ok;
}

} // namespace twidget::png
