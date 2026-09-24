#ifndef TGCALLS_V2WASM_CORE_BASE64_H
#define TGCALLS_V2WASM_CORE_BASE64_H

// Header-only base64 (RFC 4648, with padding). WASM discipline: std only.
// Shared by the core (packet / binary dc payload encoding) and CallCoreHost.

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace tgcalls {
namespace v2wasm {

inline std::string base64Encode(const uint8_t *data, size_t len) {
    static const char kAlphabet[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string result;
    result.reserve(((len + 2) / 3) * 4);
    size_t i = 0;
    while (i + 3 <= len) {
        const uint32_t v = (uint32_t(data[i]) << 16) | (uint32_t(data[i + 1]) << 8) | uint32_t(data[i + 2]);
        result.push_back(kAlphabet[(v >> 18) & 63]);
        result.push_back(kAlphabet[(v >> 12) & 63]);
        result.push_back(kAlphabet[(v >> 6) & 63]);
        result.push_back(kAlphabet[v & 63]);
        i += 3;
    }
    if (i + 1 == len) {
        const uint32_t v = uint32_t(data[i]) << 16;
        result.push_back(kAlphabet[(v >> 18) & 63]);
        result.push_back(kAlphabet[(v >> 12) & 63]);
        result.push_back('=');
        result.push_back('=');
    } else if (i + 2 == len) {
        const uint32_t v = (uint32_t(data[i]) << 16) | (uint32_t(data[i + 1]) << 8);
        result.push_back(kAlphabet[(v >> 18) & 63]);
        result.push_back(kAlphabet[(v >> 12) & 63]);
        result.push_back(kAlphabet[(v >> 6) & 63]);
        result.push_back('=');
    }
    return result;
}

inline std::string base64Encode(std::vector<uint8_t> const &data) {
    return base64Encode(data.data(), data.size());
}

inline std::optional<std::vector<uint8_t>> base64Decode(std::string const &text) {
    const auto valueOf = [](char c) -> int {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    };
    if (text.size() % 4 != 0) {
        return std::nullopt;
    }
    std::vector<uint8_t> result;
    result.reserve((text.size() / 4) * 3);
    for (size_t i = 0; i < text.size(); i += 4) {
        int values[4] = {0, 0, 0, 0};
        int padding = 0;
        for (int j = 0; j < 4; j++) {
            const char c = text[i + j];
            if (c == '=') {
                // '=' allowed only at positions 2/3 of the final group.
                if (i + 4 != text.size() || j < 2) {
                    return std::nullopt;
                }
                padding++;
            } else {
                if (padding > 0) {
                    return std::nullopt;
                }
                values[j] = valueOf(c);
                if (values[j] < 0) {
                    return std::nullopt;
                }
            }
        }
        const uint32_t v = (uint32_t(values[0]) << 18) | (uint32_t(values[1]) << 12) | (uint32_t(values[2]) << 6) | uint32_t(values[3]);
        result.push_back(uint8_t((v >> 16) & 0xff));
        if (padding < 2) {
            result.push_back(uint8_t((v >> 8) & 0xff));
        }
        if (padding < 1) {
            result.push_back(uint8_t(v & 0xff));
        }
    }
    return result;
}

} // namespace v2wasm
} // namespace tgcalls

#endif
