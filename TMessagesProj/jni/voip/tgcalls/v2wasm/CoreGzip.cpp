#include "v2wasm/CoreGzip.h"

#include "v2wasm/CallCoreABI.h"

namespace tgcalls {
namespace v2wasm {

bool coreIsGzip(std::vector<uint8_t> const &data) {
    if (data.size() < 2) {
        return false;
    }
    return (data[0] == 0x1f && data[1] == 0x8b) || (data[0] == 0x78 && data[1] == 0x9c);
}

std::optional<std::vector<uint8_t>> coreGzipData(std::vector<uint8_t> const &data) {
    if (data.empty()) {
        return std::nullopt;
    }
    // Deflate worst-case expansion (~0.1% + constants) + gzip container.
    const size_t outCap = data.size() + data.size() / 1000 + 128;
    std::vector<uint8_t> output(outCap);
    const int32_t written = tgcalls_host_deflate(data.data(), data.size(), output.data(), outCap);
    if (written < 0) {
        return std::nullopt;
    }
    output.resize((size_t)written);
    return output;
}

std::optional<std::vector<uint8_t>> coreGunzipData(std::vector<uint8_t> const &data, size_t sizeLimit) {
    if (data.empty()) {
        return std::nullopt;
    }
    // outCap doubles as the host-enforced inflate size limit (zip-bomb bound).
    std::vector<uint8_t> output(sizeLimit);
    const int32_t written = tgcalls_host_inflate(data.data(), data.size(), output.data(), sizeLimit);
    if (written < 0) {
        return std::nullopt;
    }
    output.resize((size_t)written);
    output.shrink_to_fit();
    return output;
}

} // namespace v2wasm
} // namespace tgcalls
