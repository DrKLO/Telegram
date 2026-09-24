// Native implementation of the host compression service declared in
// CallCoreABI.h (tgcalls_host_deflate / tgcalls_host_inflate): a pure gzip
// codec over utils/gzip (the same zlib the stock implementations use, so
// compressed bytes match stock exactly). Stateless mechanism only — the
// core owns the policy (whether/what to compress, size limits via outCap).
//
// Linked into the native library for both backends: the native core calls
// these symbols directly; WamrCoreBackend routes the module's
// env.host_deflate/env.host_inflate imports here via its trampolines.
// NEVER compiled into the wasm modules (there the same C symbols ARE the
// imports).

#include <cstring>
#include <vector>

#include "v2wasm/CallCoreABI.h"

#include "utils/gzip.h"

extern "C" int32_t tgcalls_host_deflate(const uint8_t *in, size_t inLen, uint8_t *out, size_t outCap) {
    if (!in || !out || inLen == 0) {
        return -1;
    }
    const auto compressed = tgcalls::gzipData(std::vector<uint8_t>(in, in + inLen));
    if (!compressed || compressed->size() > outCap) {
        return -1;
    }
    memcpy(out, compressed->data(), compressed->size());
    return (int32_t)compressed->size();
}

extern "C" int32_t tgcalls_host_inflate(const uint8_t *in, size_t inLen, uint8_t *out, size_t outCap) {
    if (!in || !out || inLen == 0) {
        return -1;
    }
    // outCap is the caller-chosen size limit (zip-bomb bound) — enforced by
    // gunzipData during inflation, re-checked before the copy.
    const auto decompressed = tgcalls::gunzipData(std::vector<uint8_t>(in, in + inLen), outCap);
    if (!decompressed || decompressed->size() > outCap) {
        return -1;
    }
    memcpy(out, decompressed->data(), decompressed->size());
    return (int32_t)decompressed->size();
}
