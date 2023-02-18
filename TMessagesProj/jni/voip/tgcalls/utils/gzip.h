#ifndef TGCALLS_UTILS_GZIP_H
#define TGCALLS_UTILS_GZIP_H

#include <absl/types/optional.h>
#include <cstdint>
#include <vector>

namespace tgcalls {

bool isGzip(std::vector<uint8_t> const &data);
absl::optional<std::vector<uint8_t>> gzipData(std::vector<uint8_t> const &data);
absl::optional<std::vector<uint8_t>> gunzipData(std::vector<uint8_t> const &data, size_t sizeLimit);

}

#endif // TGCALLS_UTILS_GZIP_H
