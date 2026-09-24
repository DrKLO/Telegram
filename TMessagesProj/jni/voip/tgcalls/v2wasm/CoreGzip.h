#ifndef TGCALLS_V2WASM_CORE_GZIP_H
#define TGCALLS_V2WASM_CORE_GZIP_H

// gzip-format compress/decompress for signaling V2 bodies, backed by the
// HOST compression service (tgcalls_host_deflate/tgcalls_host_inflate in
// CallCoreABI.h — the host's zlib, so compressed bytes match stock exactly).
// Mechanism only: whether/what to compress stays core policy. Semantics
// mirror utils/gzip.{h,cpp}:
//  - coreGzipData: gzip-wrapped deflate (magic 1f 8b), max compression
//  - coreGunzipData: accepts gzip (1f 8b) and zlib (78 9c) framing, enforces
//    sizeLimit (zip-bomb bound), nullopt on failure
//  - coreIsGzip: same magic check as utils/gzip.h isGzip
// WASM discipline: std + the ABI header only.

#include <cstdint>
#include <optional>
#include <vector>

namespace tgcalls {
namespace v2wasm {

bool coreIsGzip(std::vector<uint8_t> const &data);
std::optional<std::vector<uint8_t>> coreGzipData(std::vector<uint8_t> const &data);
std::optional<std::vector<uint8_t>> coreGunzipData(std::vector<uint8_t> const &data, size_t sizeLimit);

} // namespace v2wasm
} // namespace tgcalls

#endif
