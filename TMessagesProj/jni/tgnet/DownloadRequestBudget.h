#pragma once

#include <cstdint>
#include <map>
#include <utility>

// Rebuilt from running requests on each scheduling pass, so cancellation,
// retries and DC migration cannot leak reservations.
class DownloadRequestBudget {
public:
    explicit DownloadRequestBudget(bool premium) : limit((premium ? 16u : 2u) * 1024 * 1024) {
    }

    void account(uint32_t dc, bool small, uint32_t bytes) {
        used[{dc, small}] += bytes;
    }

    bool acquire(uint32_t dc, bool small, uint32_t bytes) {
        if (bytes == 0) {
            return true; // Control requests must not wait behind file data.
        }
        auto &current = used[{dc, small}];
        if (bytes > limit || current > limit - bytes) {
            return false;
        }
        current += bytes;
        return true;
    }

private:
    const uint32_t limit;
    std::map<std::pair<uint32_t, bool>, uint64_t> used;
};
