#include "png.h"

#include <array>
#include <cstring>
#include <memory>
#include <utility>
#include <vector>

namespace twlite {
namespace {

uint32_t crc32(const uint8_t* p, size_t n) {
    static const std::array<uint32_t, 256> table = [] {
        std::array<uint32_t, 256> t{};
        for (uint32_t i = 0; i < 256; i++) {
            uint32_t c = i;
            for (int k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
            t[i] = c;
        }
        return t;
    }();
    uint32_t c = 0xFFFFFFFFu;
    for (size_t i = 0; i < n; i++) c = table[(c ^ p[i]) & 0xFF] ^ (c >> 8);
    return c ^ 0xFFFFFFFFu;
}

/// Counts whole rows as the inflater makes them, keeping only the 32 KB its back references reach.
struct Rows {
    uint64_t stride = 0, height = 0, produced = 0, intact = 0;
    uint8_t window[32768] = {};
    /// False once the image is complete or a row starts with an unknown filter type.
    bool put(uint8_t b) {
        if (produced == intact * stride && b > 4) return false;
        window[produced & 32767] = b;
        produced++;
        if (produced == (intact + 1) * stride) intact++;
        return intact < height;
    }
};

struct Bits {
    const uint8_t* p;
    size_t n, at = 0;
    uint32_t buf = 0;
    int count = 0;
    bool over = false;
    uint32_t get(int k) {
        while (count < k) {
            if (at >= n) {
                over = true;
                return 0;
            }
            buf |= uint32_t(p[at++]) << count;
            count += 8;
        }
        uint32_t v = buf & ((1u << k) - 1);
        buf >>= k;
        count -= k;
        return v;
    }
};

/// A canonical Huffman code: how many codes of each length, and the symbols in code order.
struct Tree {
    uint16_t counts[16] = {};
    uint16_t symbols[320] = {};
    bool build(const uint8_t* lengths, int n) {
        uint16_t offsets[16] = {};
        for (int i = 0; i < n; i++) counts[lengths[i]]++;
        counts[0] = 0;
        for (int i = 1; i < 16; i++) offsets[i] = uint16_t(offsets[i - 1] + counts[i - 1]);
        for (int i = 0; i < n; i++)
            if (lengths[i]) symbols[offsets[lengths[i]]++] = uint16_t(i);
        return true;
    }
    int decode(Bits& bits) const {
        int code = 0, first = 0, index = 0;
        for (int len = 1; len < 16; len++) {
            code |= int(bits.get(1));
            if (bits.over) return -1;
            int count = counts[len];
            if (code - first < count) return symbols[index + code - first];
            index += count;
            first = (first + count) << 1;
            code <<= 1;
        }
        return -1;
    }
};

const uint16_t LENGTH_BASE[29] = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
const uint8_t LENGTH_EXTRA[29] = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
const uint16_t DIST_BASE[30] = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577};
const uint8_t DIST_EXTRA[30] = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};

/// False when the rows stop: the image is complete, a row is broken, or the data is.
bool inflate_block(Bits& bits, const Tree& lit, const Tree& dist, Rows& rows) {
    for (;;) {
        int sym = lit.decode(bits);
        if (sym < 0) return false;
        if (sym < 256) {
            if (!rows.put(uint8_t(sym))) return false;
            continue;
        }
        if (sym == 256) return true;
        sym -= 257;
        if (sym >= 29) return false;
        uint32_t length = LENGTH_BASE[sym] + bits.get(LENGTH_EXTRA[sym]);
        int d = dist.decode(bits);
        if (d < 0 || d >= 30) return false;
        uint64_t back = DIST_BASE[d] + bits.get(DIST_EXTRA[d]);
        if (bits.over || back > rows.produced) return false;
        for (uint32_t i = 0; i < length; i++)
            if (!rows.put(rows.window[(rows.produced - back) & 32767])) return false;
    }
}

/// Inflates a zlib stream into `rows`, as far as it goes.
void inflate(const std::vector<uint8_t>& data, Rows& rows) {
    if (data.size() < 2 || (data[0] & 0x0F) != 8 || ((data[0] << 8) | data[1]) % 31 != 0 || (data[1] & 0x20)) return;
    Bits bits{data.data() + 2, data.size() - 2};
    for (bool last = false; !last;) {
        last = bits.get(1) != 0;
        uint32_t type = bits.get(2);
        if (bits.over) return;
        if (type == 0) {
            bits.buf = 0;
            bits.count = 0;
            if (bits.at + 4 > bits.n) return;
            uint32_t len = bits.p[bits.at] | bits.p[bits.at + 1] << 8;
            bits.at += 4;
            for (uint32_t i = 0; i < len; i++) {
                if (bits.at >= bits.n || !rows.put(bits.p[bits.at++])) return;
            }
        } else if (type == 1) {
            static const std::pair<Tree, Tree> fixed = [] {
                uint8_t lengths[320];
                for (int i = 0; i < 288; i++) lengths[i] = i < 144 ? 8 : i < 256 ? 9 : i < 280 ? 7 : 8;
                std::pair<Tree, Tree> t;
                t.first.build(lengths, 288);
                for (int i = 0; i < 30; i++) lengths[i] = 5;
                t.second.build(lengths, 30);
                return t;
            }();
            if (!inflate_block(bits, fixed.first, fixed.second, rows)) return;
        } else if (type == 2) {
            uint32_t hlit = bits.get(5) + 257, hdist = bits.get(5) + 1, hclen = bits.get(4) + 4;
            static const uint8_t order[19] = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
            uint8_t code_lengths[19] = {};
            for (uint32_t i = 0; i < hclen; i++) code_lengths[order[i]] = uint8_t(bits.get(3));
            if (bits.over || hlit > 286 || hdist > 30) return;
            Tree codes;
            codes.build(code_lengths, 19);
            uint8_t lengths[320] = {};
            for (uint32_t i = 0; i < hlit + hdist;) {
                int sym = codes.decode(bits);
                if (sym < 0) return;
                if (sym < 16) {
                    lengths[i++] = uint8_t(sym);
                    continue;
                }
                uint8_t repeat = 0;
                uint32_t times;
                if (sym == 16) {
                    if (i == 0) return;
                    repeat = lengths[i - 1];
                    times = 3 + bits.get(2);
                } else if (sym == 17) times = 3 + bits.get(3);
                else times = 11 + bits.get(7);
                if (bits.over || i + times > hlit + hdist) return;
                while (times--) lengths[i++] = repeat;
            }
            Tree lit, dist;
            lit.build(lengths, int(hlit));
            dist.build(lengths + hlit, int(hdist));
            if (!inflate_block(bits, lit, dist, rows)) return;
        } else {
            return;
        }
    }
}

} // namespace

size_t png_intact_rows(const uint8_t* bytes, size_t length) {
    static const uint8_t signature[8] = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    if (!bytes || length < 33 || std::memcmp(bytes, signature, 8) != 0) return SIZE_MAX;
    auto be32 = [&](size_t at) { return uint32_t(bytes[at]) << 24 | uint32_t(bytes[at + 1]) << 16 | uint32_t(bytes[at + 2]) << 8 | bytes[at + 3]; };
    if (std::memcmp(bytes + 12, "IHDR", 4) != 0 || be32(8) < 13) return SIZE_MAX;
    const uint32_t width = be32(16), height = be32(20);
    const uint8_t depth = bytes[24], type = bytes[25], interlace = bytes[28];
    const int channels = type == 0 ? 1 : type == 2 ? 3 : type == 3 ? 1 : type == 4 ? 2 : type == 6 ? 4 : 0;
    if (!channels || interlace != 0 || width == 0 || height == 0) return SIZE_MAX;

    bool damaged = true;
    for (size_t at = 8; at + 12 <= length;) {
        const size_t size = be32(at);
        if (size > length - at - 12) break;
        if (std::memcmp(bytes + at + 4, "IEND", 4) == 0) {
            damaged = false;
            break;
        }
        if (std::memcmp(bytes + at + 4, "IDAT", 4) == 0 && crc32(bytes + at + 4, size + 4) != be32(at + 8 + size)) break;
        at += size + 12;
    }
    if (!damaged) return SIZE_MAX;

    // Every whole IDAT chunk, the damaged one too: its rows count until they go wrong.
    std::vector<uint8_t> data;
    for (size_t at = 8; at + 12 <= length;) {
        const size_t size = be32(at);
        if (size > length - at - 12 || std::memcmp(bytes + at + 4, "IEND", 4) == 0) break;
        if (std::memcmp(bytes + at + 4, "IDAT", 4) == 0) data.insert(data.end(), bytes + at + 8, bytes + at + 8 + size);
        at += size + 12;
    }
    auto rows = std::make_unique<Rows>();
    rows->stride = (uint64_t(width) * channels * depth + 7) / 8 + 1;
    rows->height = height;
    inflate(data, *rows);
    return rows->intact < height ? size_t(rows->intact) : SIZE_MAX;
}

bool image_dimensions(const uint8_t* b, size_t n, uint32_t& w, uint32_t& h) {
    auto be16 = [&](size_t at) { return uint32_t(b[at]) << 8 | b[at + 1]; };
    auto le16 = [&](size_t at) { return uint32_t(b[at]) | uint32_t(b[at + 1]) << 8; };
    auto be32 = [&](size_t at) { return be16(at) << 16 | be16(at + 2); };
    auto le24 = [&](size_t at) { return le16(at) | uint32_t(b[at + 2]) << 16; };
    if (!b) return false;
    static const uint8_t png[8] = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    if (n >= 24 && std::memcmp(b, png, 8) == 0 && std::memcmp(b + 12, "IHDR", 4) == 0) {
        w = be32(16);
        h = be32(20);
        return true;
    }
    if (n >= 10 && (std::memcmp(b, "GIF87a", 6) == 0 || std::memcmp(b, "GIF89a", 6) == 0)) {
        w = le16(6);
        h = le16(8);
        return true;
    }
    if (n >= 4 && b[0] == 0xFF && b[1] == 0xD8) {
        // The first start-of-frame marker, past the segments before it.
        for (size_t i = 2; i + 3 < n;) {
            if (b[i] != 0xFF) return false;
            uint8_t marker = b[i + 1];
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                i += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) return false;
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (i + 9 > n) return false;
                h = be16(i + 5);
                w = be16(i + 7);
                return true;
            }
            i += 2 + be16(i + 2);
        }
        return false;
    }
    if (n >= 30 && std::memcmp(b, "RIFF", 4) == 0 && std::memcmp(b + 8, "WEBP", 4) == 0) {
        if (std::memcmp(b + 12, "VP8 ", 4) == 0) {
            w = le16(26) & 0x3FFF;
            h = le16(28) & 0x3FFF;
            return true;
        }
        if (std::memcmp(b + 12, "VP8L", 4) == 0 && b[20] == 0x2F) {
            uint32_t bits = uint32_t(b[21]) | uint32_t(b[22]) << 8 | uint32_t(b[23]) << 16 | uint32_t(b[24]) << 24;
            w = (bits & 0x3FFF) + 1;
            h = ((bits >> 14) & 0x3FFF) + 1;
            return true;
        }
        if (std::memcmp(b + 12, "VP8X", 4) == 0) {
            w = le24(24) + 1;
            h = le24(27) + 1;
            return true;
        }
    }
    return false;
}

} // namespace twlite
